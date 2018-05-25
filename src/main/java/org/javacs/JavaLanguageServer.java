package org.javacs;

import com.google.common.collect.ImmutableList;
import java.net.URI;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.eclipse.lsp4j.services.WorkspaceService;

class JavaLanguageServer implements LanguageServer {
    private static final Logger LOG = Logger.getLogger("main");

    LanguageClient client;
    JavaCompilerService compiler;
    JavaTextDocumentService textDocuments = new JavaTextDocumentService(this);
    JavaWorkspaceService workspace = new JavaWorkspaceService(this);

    void publishDiagnostics(URI file, List<Diagnostic<? extends JavaFileObject>> javaDiagnostics) {
        // TODO
    }

    @Override
    public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
        this.compiler = new JavaCompilerService(Collections.emptySet(), Collections.emptySet());
        this.textDocuments = new JavaTextDocumentService(this);
        this.workspace = new JavaWorkspaceService(this);

        InitializeResult result = new InitializeResult();
        ServerCapabilities c = new ServerCapabilities();

        c.setTextDocumentSync(TextDocumentSyncKind.Incremental);
        c.setDefinitionProvider(true);
        c.setCompletionProvider(new CompletionOptions(true, ImmutableList.of(".")));
        c.setHoverProvider(true);
        c.setWorkspaceSymbolProvider(true);
        c.setReferencesProvider(true);
        c.setDocumentSymbolProvider(true);
        c.setCodeActionProvider(true);
        c.setSignatureHelpProvider(new SignatureHelpOptions(ImmutableList.of("(", ",")));

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
