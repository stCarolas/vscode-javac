package org.javacs;

import com.sun.source.tree.CompilationUnitTree;
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
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.TextDocumentService;

class JavaTextDocumentService implements TextDocumentService {
    private final JavaLanguageServer server;
    private final Map<URI, VersionedContent> activeDocuments = new HashMap<>();

    JavaTextDocumentService(JavaLanguageServer server) {
        this.server = server;
    }

    @Override
    public CompletableFuture<Either<List<CompletionItem>, CompletionList>> completion(CompletionParams position) {
        URI uri = URI.create(position.getTextDocument().getUri());
        String content = contents(uri).content;
        int line = position.getPosition().getLine() + 1;
        int column = position.getPosition().getCharacter() + 1;
        List<CompletionItem> result = new ArrayList<>();
        for (Completion c : server.compiler.completions(uri, content, line, column)) {
            CompletionItem i = new CompletionItem();
            if (c.element != null) {
                i.setLabel(c.element.getSimpleName().toString());
                i.setDetail(c.element.toString());
            } else if (c.packagePart != null) {
                i.setLabel(c.packagePart.name);
                i.setDetail(c.packagePart.fullName);
            } else if (c.classSymbol != null) {
                i.setLabel("class");
                i.setDetail(c.classSymbol.toString());
            } else throw new RuntimeException(c + " is not valid");

            result.add(i);
        }
        return CompletableFuture.completedFuture(Either.forRight(new CompletionList(false, result)));
    }

    @Override
    public CompletableFuture<CompletionItem> resolveCompletionItem(CompletionItem unresolved) {
        return CompletableFuture.completedFuture(unresolved); // TODO
    }

    @Override
    public CompletableFuture<Hover> hover(TextDocumentPositionParams position) {
        URI uri = URI.create(position.getTextDocument().getUri());
        String content = contents(uri).content;
        int line = position.getPosition().getLine() + 1;
        int column = position.getPosition().getCharacter() + 1;
        Element e = server.compiler.element(uri, content, line, column);
        if (e != null) {
            MarkedString hover = new MarkedString("java", e.toString());
            Hover result = new Hover(Collections.singletonList(Either.forRight(hover)));
            return CompletableFuture.completedFuture(result);
        } else return CompletableFuture.completedFuture(new Hover(Collections.emptyList()));
    }

    private SignatureInformation asSignatureInformation(ExecutableElement e) {
        SignatureInformation i = new SignatureInformation();
        List<ParameterInformation> ps = new ArrayList<>();
        StringJoiner args = new StringJoiner(", ");
        for (VariableElement v : e.getParameters()) {
            ParameterInformation p = new ParameterInformation();
            String label = v.getSimpleName().toString();
            // TODO use type when name is not available
            args.add(label);
            p.setLabel(label);
            ps.add(p);
        }
        String name = e.getSimpleName().toString();
        if (name.equals("<init>")) name = e.getEnclosingElement().getSimpleName().toString();
        i.setLabel(name + "(" + args + ")");
        i.setParameters(ps);
        return i;
    }

    private SignatureHelp asSignatureHelp(MethodInvocation invoke) {
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
        return null; // TODO
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
        } else server.updateConfig(uri);
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
