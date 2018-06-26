package org.javacs;

import com.google.common.collect.ImmutableList;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.eclipse.lsp4j.services.WorkspaceService;

class JavaLanguageServer implements LanguageServer {
    private static final Logger LOG = Logger.getLogger("main");

    Path workspaceRoot;
    LanguageClient client;
    JavaCompilerService compiler;
    final JavaTextDocumentService textDocuments = new JavaTextDocumentService(this);
    final JavaWorkspaceService workspace = new JavaWorkspaceService(this);

    private static DiagnosticSeverity severity(javax.tools.Diagnostic.Kind kind) {
        switch (kind) {
            case ERROR:
                return DiagnosticSeverity.Error;
            case WARNING:
            case MANDATORY_WARNING:
                return DiagnosticSeverity.Warning;
            case NOTE:
            case OTHER:
            default:
                return DiagnosticSeverity.Information;
        }
    }

    private static Position position(String content, long offset) {
        int line = 0, column = 0;
        for (int i = 0; i < offset; i++) {
            if (content.charAt(i) == '\n') {
                line++;
                column = 0;
            } else column++;
        }
        return new Position(line, column);
    }

    void publishDiagnostics(Collection<URI> files, List<Diagnostic<? extends JavaFileObject>> javaDiagnostics) {
        for (URI f : files) {
            List<org.eclipse.lsp4j.Diagnostic> ds = new ArrayList<>();
            for (Diagnostic<? extends JavaFileObject> j : javaDiagnostics) {
                URI uri = j.getSource().toUri();
                if (uri.equals(f)) {
                    String content = textDocuments.contents(uri).content;
                    Position start = position(content, j.getStartPosition()),
                            end = position(content, j.getEndPosition());
                    DiagnosticSeverity sev = severity(j.getKind());
                    org.eclipse.lsp4j.Diagnostic d = new org.eclipse.lsp4j.Diagnostic();
                    d.setSeverity(sev);
                    d.setRange(new Range(start, end));
                    d.setCode(j.getCode());
                    d.setMessage(j.getMessage(null));
                    ds.add(d);
                }
            }
            client.publishDiagnostics(new PublishDiagnosticsParams(f.toString(), ds));
        }
    }

    void lint(Collection<URI> uris) {
        LOG.info("Lint " + uris.stream().map(uri -> uri.toString()).collect(Collectors.joining(", ")));
        List<Path> paths = uris.stream().map(uri -> Paths.get(uri)).collect(Collectors.toList());
        publishDiagnostics(uris, compiler.lint(paths));
    }

    private JavaCompilerService createCompiler() {
        Objects.requireNonNull(workspaceRoot, "Can't create compiler because workspaceRoot has not been initialized");
        InferConfig infer = new InferConfig(workspaceRoot);
        return new JavaCompilerService(
                InferSourcePath.sourcePath(workspaceRoot), infer.classPath(), infer.buildDocPath());
    }

    @Override
    public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
        this.workspaceRoot = Paths.get(URI.create(params.getRootUri()));
        this.compiler = createCompiler();

        InitializeResult result = new InitializeResult();
        ServerCapabilities c = new ServerCapabilities();

        c.setTextDocumentSync(TextDocumentSyncKind.Incremental);
        c.setDefinitionProvider(true);
        c.setCompletionProvider(new CompletionOptions(true, ImmutableList.of(".")));
        c.setHoverProvider(true);
        c.setWorkspaceSymbolProvider(true);
        c.setReferencesProvider(true);
        c.setDocumentSymbolProvider(true);
        c.setSignatureHelpProvider(new SignatureHelpOptions(ImmutableList.of("(", ",")));
        c.setDocumentFormattingProvider(true);

        result.setCapabilities(c);

        return CompletableFuture.completedFuture(result);
    }

    @Override
    public CompletableFuture<Object> shutdown() {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void exit() {}

    @Override
    public TextDocumentService getTextDocumentService() {
        return textDocuments;
    }

    @Override
    public WorkspaceService getWorkspaceService() {
        return workspace;
    }

    void installClient(LanguageClient client) {
        this.client = client;
    }
}
