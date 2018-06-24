package org.javacs;

import com.google.gson.JsonPrimitive;
import com.sun.javadoc.*;
import com.sun.source.tree.*;
import com.sun.source.util.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javax.lang.model.element.*;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.TextDocumentService;

class JavaTextDocumentService implements TextDocumentService {
    private final JavaLanguageServer server;
    private final Map<URI, VersionedContent> activeDocuments = new HashMap<>();

    JavaTextDocumentService(JavaLanguageServer server) {
        this.server = server;
    }

    /** Cache of completions from the last call to `completion` */
    private final Map<String, Completion> lastCompletions = new HashMap<>();

    @Override
    public CompletableFuture<Either<List<CompletionItem>, CompletionList>> completion(CompletionParams position) {
        URI uri = URI.create(position.getTextDocument().getUri());
        String content = contents(uri).content;
        int line = position.getPosition().getLine() + 1;
        int column = position.getPosition().getCharacter() + 1;
        List<CompletionItem> result = new ArrayList<>();
        lastCompletions.clear();
        CompletionResult completions = server.compiler.completions(uri, content, line, column, 50);
        for (Completion c : completions.items) {
            CompletionItem i = new CompletionItem();
            String id = UUID.randomUUID().toString();
            i.setData(id);
            lastCompletions.put(id, c);
            if (c.element != null) {
                i.setLabel(c.element.getSimpleName().toString());
                // Detailed name will be resolved later, using docs to fill in method names
            } else if (c.packagePart != null) {
                i.setLabel(c.packagePart.name);
                i.setDetail(c.packagePart.fullName);
            } else if (c.classSymbol != null) {
                i.setLabel("class");
                i.setDetail(c.classSymbol.toString());
            } else if (c.notImportedClass != null) {
                i.setLabel(c.notImportedClass.getSimpleName());
                i.setDetail(c.notImportedClass.getName());
            } else throw new RuntimeException(c + " is not valid");

            result.add(i);
        }
        return CompletableFuture.completedFuture(Either.forRight(new CompletionList(completions.isIncomplete, result)));
    }

    private String resolveDocDetail(MethodDoc doc) {
        StringJoiner args = new StringJoiner(", ");
        for (Parameter p : doc.parameters()) {
            args.add(p.name());
        }
        return String.format("%s(%s)", doc.name(), args);
    }

    private String resolveDefaultDetail(ExecutableElement method) {
        StringJoiner args = new StringJoiner(", ");
        boolean missingParamNames =
                method.getParameters().stream().allMatch(p -> p.getSimpleName().toString().matches("arg\\d+"));
        for (VariableElement p : method.getParameters()) {
            if (missingParamNames) args.add(p.asType().toString());
            else args.add(p.getSimpleName().toString());
        }
        return String.format("%s(%s)", method.getSimpleName(), args);
    }

    @Override
    public CompletableFuture<CompletionItem> resolveCompletionItem(CompletionItem unresolved) {
        JsonPrimitive idJson = (JsonPrimitive) unresolved.getData();
        String id = idJson.getAsString();
        Completion cached = lastCompletions.get(id);
        if (cached == null) {
            LOG.warning("CompletionItem " + id + " was not in the cache");
            return CompletableFuture.completedFuture(unresolved);
        }
        if (cached.element != null) {
            if (cached.element instanceof ExecutableElement) {
                ExecutableElement method = (ExecutableElement) cached.element;
                Optional<MethodDoc> doc = server.compiler.methodDoc(method);
                String detail = doc.map(this::resolveDocDetail).orElse(resolveDefaultDetail(method));
                unresolved.setDetail(detail);
                doc.flatMap(Javadocs::commentText)
                        .ifPresent(
                                html -> {
                                    String markdown = Javadocs.htmlToMarkdown(html);
                                    MarkupContent content = new MarkupContent();
                                    content.setKind(MarkupKind.MARKDOWN);
                                    content.setValue(markdown);
                                    unresolved.setDocumentation(content);
                                });
            } else if (cached.element instanceof TypeElement) {
                TypeElement type = (TypeElement) cached.element;
                server.compiler
                        .classDoc(type)
                        .ifPresent(
                                doc -> {
                                    String html = doc.commentText();
                                    String markdown = Javadocs.htmlToMarkdown(html);
                                    MarkupContent content = new MarkupContent();
                                    content.setKind(MarkupKind.MARKDOWN);
                                    content.setValue(markdown);
                                    unresolved.setDocumentation(content);
                                });
            } else {
                LOG.info("Don't know how to look up docs for element " + cached.element);
            }
            // TODO constructors, fields
        }
        return CompletableFuture.completedFuture(unresolved); // TODO
    }

    private String hoverTypeDeclaration(TypeElement t) {
        StringBuilder result = new StringBuilder();
        switch (t.getKind()) {
            case INTERFACE:
                result.append("interface");
                break;
            case CLASS:
                result.append("class");
                break;
            default:
                LOG.warning("Don't know what to call type element " + t);
                result.append("???");
        }
        result.append(" ").append(t.asType());
        if (!t.getSuperclass().toString().equals("java.lang.Object")) {
            result.append(" extends ").append(t.getSuperclass());
        }
        return result.toString();
    }

    private String hoverCode(Element e) {
        if (e instanceof ExecutableElement) {
            ExecutableElement m = (ExecutableElement) e;
            if (m.getSimpleName().contentEquals("<init>")) {
                return m.toString();
            } else {
                StringJoiner result = new StringJoiner(" ");
                if (m.getModifiers().contains(Modifier.STATIC)) result.add("static");
                result.add(m.getReturnType().toString());
                result.add(m.toString());
                return result.toString();
            }
        } else if (e instanceof VariableElement) {
            VariableElement v = (VariableElement) e;
            return v.asType() + " " + v;
        } else if (e instanceof TypeElement) {
            TypeElement t = (TypeElement) e;
            StringJoiner lines = new StringJoiner("\n");
            lines.add(hoverTypeDeclaration(t) + " {");
            for (Element member : t.getEnclosedElements()) {
                if (member instanceof ExecutableElement || member instanceof VariableElement) {
                    lines.add("  " + hoverCode(member) + ";");
                } else if (member instanceof TypeElement) {
                    lines.add("  " + hoverTypeDeclaration((TypeElement) member) + " { /* removed */ }");
                }
            }
            lines.add("}");
            return lines.toString();
        } else return e.toString();
    }

    private Optional<String> hoverDocs(Element e) {
        if (e instanceof ExecutableElement) {
            ExecutableElement m = (ExecutableElement) e;
            return server.compiler.methodDoc(m).flatMap(Javadocs::commentText).map(Javadocs::htmlToMarkdown);
        } else if (e instanceof TypeElement) {
            TypeElement t = (TypeElement) e;
            return server.compiler.classDoc(t).map(doc -> doc.commentText()).map(Javadocs::htmlToMarkdown);
        } else return Optional.empty();
    }

    @Override
    public CompletableFuture<Hover> hover(TextDocumentPositionParams position) {
        URI uri = URI.create(position.getTextDocument().getUri());
        String content = contents(uri).content;
        int line = position.getPosition().getLine() + 1;
        int column = position.getPosition().getCharacter() + 1;
        Element e = server.compiler.element(uri, content, line, column);
        if (e != null) {
            List<Either<String, MarkedString>> result = new ArrayList<>();
            result.add(Either.forRight(new MarkedString("java", hoverCode(e))));
            hoverDocs(e).ifPresent(doc -> result.add(Either.forLeft(doc)));
            return CompletableFuture.completedFuture(new Hover(result));
        } else return CompletableFuture.completedFuture(new Hover(Collections.emptyList()));
    }

    private List<ParameterInformation> signatureParamsFromDocs(MethodDoc doc) {
        List<ParameterInformation> ps = new ArrayList<>();
        Map<String, String> paramComments = new HashMap<>();
        for (ParamTag t : doc.paramTags()) {
            paramComments.put(t.parameterName(), t.parameterComment());
        }
        for (Parameter d : doc.parameters()) {
            ParameterInformation p = new ParameterInformation();
            p.setLabel(d.name());
            p.setDocumentation(paramComments.get(d.name()));
            ps.add(p);
        }
        return ps;
    }

    private List<ParameterInformation> signatureParamsFromMethod(ExecutableElement e) {
        boolean missingParamNames =
                e.getParameters().stream().allMatch(p -> p.getSimpleName().toString().matches("arg\\d+"));
        List<ParameterInformation> ps = new ArrayList<>();
        for (VariableElement v : e.getParameters()) {
            ParameterInformation p = new ParameterInformation();
            if (missingParamNames) p.setLabel(v.asType().toString());
            else p.setLabel(v.getSimpleName().toString());
            ps.add(p);
        }
        return ps;
    }

    private SignatureInformation asSignatureInformation(ExecutableElement e) {
        SignatureInformation i = new SignatureInformation();
        Optional<MethodDoc> doc = server.compiler.methodDoc(e);
        List<ParameterInformation> ps = doc.map(this::signatureParamsFromDocs).orElse(signatureParamsFromMethod(e));
        String args = ps.stream().map(p -> p.getLabel()).collect(Collectors.joining(", "));
        String name = e.getSimpleName().toString();
        if (name.equals("<init>")) name = e.getEnclosingElement().getSimpleName().toString();
        i.setLabel(name + "(" + args + ")");
        i.setParameters(ps);
        return i;
    }

    private SignatureHelp asSignatureHelp(MethodInvocation invoke) {
        // TODO use docs to get parameter names
        List<SignatureInformation> sigs = new ArrayList<>();
        for (ExecutableElement e : invoke.overloads) {
            sigs.add(asSignatureInformation(e));
        }
        int activeSig = invoke.activeMethod.map(invoke.overloads::indexOf).orElse(0);
        return new SignatureHelp(sigs, activeSig, invoke.activeParameter);
    }

    @Override
    public CompletableFuture<SignatureHelp> signatureHelp(TextDocumentPositionParams position) {
        URI uri = URI.create(position.getTextDocument().getUri());
        String content = contents(uri).content;
        int line = position.getPosition().getLine() + 1;
        int column = position.getPosition().getCharacter() + 1;
        SignatureHelp help =
                server.compiler
                        .methodInvocation(uri, content, line, column)
                        .map(this::asSignatureHelp)
                        .orElse(new SignatureHelp());
        return CompletableFuture.completedFuture(help);
    }

    private Location location(TreePath p) {
        Trees trees = server.compiler.trees();
        SourcePositions pos = trees.getSourcePositions();
        CompilationUnitTree cu = p.getCompilationUnit();
        LineMap lines = cu.getLineMap();
        long start = pos.getStartPosition(cu, p.getLeaf()), end = pos.getEndPosition(cu, p.getLeaf());
        int startLine = (int) lines.getLineNumber(start) - 1, startCol = (int) lines.getColumnNumber(start) - 1;
        int endLine = (int) lines.getLineNumber(end) - 1, endCol = (int) lines.getColumnNumber(end) - 1;
        URI dUri = cu.getSourceFile().toUri();
        return new Location(
                dUri.toString(), new Range(new Position(startLine, startCol), new Position(endLine, endCol)));
    }

    @Override
    public CompletableFuture<List<? extends Location>> definition(TextDocumentPositionParams position) {
        URI uri = URI.create(position.getTextDocument().getUri());
        String content = contents(uri).content;
        int line = position.getPosition().getLine() + 1;
        int column = position.getPosition().getCharacter() + 1;
        List<Location> result = new ArrayList<>();
        server.compiler
                .definition(uri, content, line, column)
                .ifPresent(
                        d -> {
                            result.add(location(d));
                        });
        return CompletableFuture.completedFuture(result);
    }

    @Override
    public CompletableFuture<List<? extends Location>> references(ReferenceParams position) {
        URI uri = URI.create(position.getTextDocument().getUri());
        String content = contents(uri).content;
        int line = position.getPosition().getLine() + 1;
        int column = position.getPosition().getCharacter() + 1;
        List<Location> result = new ArrayList<>();
        for (TreePath r : server.compiler.references(uri, content, line, column)) {
            result.add(location(r));
        }
        return CompletableFuture.completedFuture(result);
    }

    @Override
    public CompletableFuture<List<? extends DocumentHighlight>> documentHighlight(TextDocumentPositionParams position) {
        return null;
    }

    @Override
    public CompletableFuture<List<? extends SymbolInformation>> documentSymbol(DocumentSymbolParams params) {
        URI uri = URI.create(params.getTextDocument().getUri());
        String content = contents(uri).content;
        List<SymbolInformation> result =
                Parser.documentSymbols(Paths.get(uri), content)
                        .stream()
                        .map(Parser::asSymbolInformation)
                        .collect(Collectors.toList());
        return CompletableFuture.completedFuture(result);
    }

    @Override
    public CompletableFuture<List<? extends Command>> codeAction(CodeActionParams params) {
        return null;
    }

    @Override
    public CompletableFuture<List<? extends CodeLens>> codeLens(CodeLensParams params) {
        return null;
    }

    @Override
    public CompletableFuture<CodeLens> resolveCodeLens(CodeLens unresolved) {
        return null;
    }

    @Override
    public CompletableFuture<List<? extends TextEdit>> formatting(DocumentFormattingParams params) {
        return null;
    }

    @Override
    public CompletableFuture<List<? extends TextEdit>> rangeFormatting(DocumentRangeFormattingParams params) {
        return null;
    }

    @Override
    public CompletableFuture<List<? extends TextEdit>> onTypeFormatting(DocumentOnTypeFormattingParams params) {
        return null;
    }

    @Override
    public CompletableFuture<WorkspaceEdit> rename(RenameParams params) {
        return null;
    }

    private boolean isJava(URI uri) {
        return uri.getPath().endsWith(".java");
    }

    @Override
    public void didOpen(DidOpenTextDocumentParams params) {
        TextDocumentItem document = params.getTextDocument();
        URI uri = URI.create(document.getUri());
        if (isJava(uri)) {
            activeDocuments.put(uri, new VersionedContent(document.getText(), document.getVersion()));
            server.lint(Collections.singleton(uri));
        }
    }

    @Override
    public void didChange(DidChangeTextDocumentParams params) {
        VersionedTextDocumentIdentifier document = params.getTextDocument();
        URI uri = URI.create(document.getUri());
        if (isJava(uri)) {
            VersionedContent existing = activeDocuments.get(uri);
            String newText = existing.content;

            if (document.getVersion() > existing.version) {
                for (TextDocumentContentChangeEvent change : params.getContentChanges()) {
                    if (change.getRange() == null)
                        activeDocuments.put(uri, new VersionedContent(change.getText(), document.getVersion()));
                    else newText = patch(newText, change);
                }

                activeDocuments.put(uri, new VersionedContent(newText, document.getVersion()));
            } else LOG.warning("Ignored change with version " + document.getVersion() + " <= " + existing.version);
        }
    }

    private String patch(String sourceText, TextDocumentContentChangeEvent change) {
        try {
            Range range = change.getRange();
            BufferedReader reader = new BufferedReader(new StringReader(sourceText));
            StringWriter writer = new StringWriter();

            // Skip unchanged lines
            int line = 0;

            while (line < range.getStart().getLine()) {
                writer.write(reader.readLine() + '\n');
                line++;
            }

            // Skip unchanged chars
            for (int character = 0; character < range.getStart().getCharacter(); character++)
                writer.write(reader.read());

            // Write replacement text
            writer.write(change.getText());

            // Skip replaced text
            reader.skip(change.getRangeLength());

            // Write remaining text
            while (true) {
                int next = reader.read();

                if (next == -1) return writer.toString();
                else writer.write(next);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void didClose(DidCloseTextDocumentParams params) {
        TextDocumentIdentifier document = params.getTextDocument();
        URI uri = URI.create(document.getUri());
        if (isJava(uri)) {
            // Remove from source cache
            activeDocuments.remove(uri);

            // Clear diagnostics
            server.publishDiagnostics(Collections.singletonList(uri), Collections.emptyList());
        }
    }

    @Override
    public void didSave(DidSaveTextDocumentParams params) {
        URI uri = URI.create(params.getTextDocument().getUri());
        if (isJava(uri)) {
            // Re-lint all active documents
            server.lint(activeDocuments.keySet());
            // TODO update config when java file implies a new source root
        }
        // TODO update config when pom.xml changes
    }

    VersionedContent contents(URI openFile) {
        if (activeDocuments.containsKey(openFile)) {
            return activeDocuments.get(openFile);
        } else {
            try {
                String content = Files.readAllLines(Paths.get(openFile)).stream().collect(Collectors.joining("\n"));
                return new VersionedContent(content, -1);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static final Logger LOG = Logger.getLogger("main");
}
