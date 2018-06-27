package org.javacs;

import com.google.gson.JsonPrimitive;
import com.sun.javadoc.MethodDoc;
import com.sun.javadoc.ParamTag;
import com.sun.javadoc.Parameter;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ImportTree;
import com.sun.source.tree.LineMap;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.CodeLens;
import org.eclipse.lsp4j.CodeLensParams;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.DocumentFormattingParams;
import org.eclipse.lsp4j.DocumentHighlight;
import org.eclipse.lsp4j.DocumentOnTypeFormattingParams;
import org.eclipse.lsp4j.DocumentRangeFormattingParams;
import org.eclipse.lsp4j.DocumentSymbolParams;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.MarkedString;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.MarkupKind;
import org.eclipse.lsp4j.ParameterInformation;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.ReferenceParams;
import org.eclipse.lsp4j.RenameParams;
import org.eclipse.lsp4j.SignatureHelp;
import org.eclipse.lsp4j.SignatureInformation;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.TextDocumentPositionParams;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.TextDocumentService;

class JavaTextDocumentService implements TextDocumentService {
    private final JavaLanguageServer server;
    private final Map<URI, VersionedContent> activeDocuments = new HashMap<>();

    JavaTextDocumentService(JavaLanguageServer server) {
        this.server = server;
    }

    private CompletionItemKind completionItemKind(Element e) {
        switch (e.getKind()) {
            case ANNOTATION_TYPE:
                return CompletionItemKind.Interface;
            case CLASS:
                return CompletionItemKind.Class;
            case CONSTRUCTOR:
                return CompletionItemKind.Constructor;
            case ENUM:
                return CompletionItemKind.Enum;
            case ENUM_CONSTANT:
                return CompletionItemKind.EnumMember;
            case EXCEPTION_PARAMETER:
                return CompletionItemKind.Variable;
            case FIELD:
                return CompletionItemKind.Field;
            case STATIC_INIT:
            case INSTANCE_INIT:
                return CompletionItemKind.Function;
            case INTERFACE:
                return CompletionItemKind.Interface;
            case LOCAL_VARIABLE:
                return CompletionItemKind.Variable;
            case METHOD:
                return CompletionItemKind.Method;
            case PACKAGE:
                return CompletionItemKind.Module;
            case PARAMETER:
                return CompletionItemKind.Variable;
            case RESOURCE_VARIABLE:
                return CompletionItemKind.Variable;
            case TYPE_PARAMETER:
                return CompletionItemKind.TypeParameter;
            case OTHER:
            default:
                return null;
        }
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
                i.setKind(completionItemKind(c.element));
                // Detailed name will be resolved later, using docs to fill in method names
                if (!(c.element instanceof ExecutableElement)) i.setDetail(c.element.toString());
            } else if (c.packagePart != null) {
                i.setLabel(c.packagePart.name);
                i.setKind(CompletionItemKind.Module);
                i.setDetail(c.packagePart.fullName);
            } else if (c.classSymbol != null) {
                i.setLabel("class");
                i.setKind(CompletionItemKind.Keyword);
                i.setDetail(c.classSymbol.toString());
            } else if (c.notImportedClass != null) {
                i.setLabel(c.notImportedClass.getSimpleName());
                i.setKind(CompletionItemKind.Class);
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
                // TODO check accessibility
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
                .definition(uri, line, column, f -> contents(f).content)
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

    private List<TextEdit> fixImports(URI java) {
        String contents = server.textDocuments.contents(java).content;
        FixImports fix = server.compiler.fixImports(java, contents);
        // TODO if imports already match fixed-imports, return empty list
        // TODO preserve comments and other details of existing imports
        List<TextEdit> edits = new ArrayList<>();
        // Delete all existing imports
        for (ImportTree i : fix.parsed.getImports()) {
            if (!i.isStatic()) {
                long offset = fix.sourcePositions.getStartPosition(fix.parsed, i);
                int line = (int) fix.parsed.getLineMap().getLineNumber(offset) - 1;
                TextEdit delete = new TextEdit(new Range(new Position(line, 0), new Position(line + 1, 0)), "");
                edits.add(delete);
            }
        }
        if (fix.fixedImports.isEmpty()) return edits;
        // Find a place to insert the new imports
        long insertLine = -1;
        StringBuilder insertText = new StringBuilder();
        // If there are imports, use the start of the first import as the insert position
        for (ImportTree i : fix.parsed.getImports()) {
            if (!i.isStatic() && insertLine == -1) {
                long offset = fix.sourcePositions.getStartPosition(fix.parsed, i);
                insertLine = fix.parsed.getLineMap().getLineNumber(offset) - 1;
            }
        }
        // If there are no imports, insert after the package declaration
        if (insertLine == -1 && fix.parsed.getPackageName() != null) {
            long offset = fix.sourcePositions.getEndPosition(fix.parsed, fix.parsed.getPackageName());
            insertLine = fix.parsed.getLineMap().getLineNumber(offset);
            insertText.append("\n");
        }
        // If there are no imports and no package, insert at the top of the file
        if (insertLine == -1) {
            insertLine = 0;
        }
        // Insert each import
        fix.fixedImports
                .stream()
                .sorted()
                .forEach(
                        i -> {
                            insertText.append("import ").append(i).append(";\n");
                        });
        Position insertPosition = new Position((int) insertLine, 0);
        TextEdit insert = new TextEdit(new Range(insertPosition, insertPosition), insertText.toString());
        edits.add(insert);
        return edits;
    }

    @Override
    public CompletableFuture<List<? extends TextEdit>> formatting(DocumentFormattingParams params) {
        URI uri = URI.create(params.getTextDocument().getUri());
        return CompletableFuture.completedFuture(fixImports(uri));
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

    Set<URI> activeDocuments() {
        return activeDocuments.keySet();
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
