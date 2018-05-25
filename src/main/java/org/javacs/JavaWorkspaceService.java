package org.javacs;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.services.WorkspaceService;

class JavaWorkspaceService implements WorkspaceService {
    private static final Logger LOG = Logger.getLogger("main");

    private final JavaLanguageServer server;

    JavaWorkspaceService(JavaLanguageServer server) {
        this.server = server;
    }

    @Override
    public CompletableFuture<Object> executeCommand(ExecuteCommandParams params) {
        return null;
    }

    @Override
    public CompletableFuture<List<? extends SymbolInformation>> symbol(WorkspaceSymbolParams params) {
        return null; // TODO
    }

    @Override
    public void didChangeConfiguration(DidChangeConfigurationParams change) {}

    @Override
    public void didChangeWatchedFiles(DidChangeWatchedFilesParams params) {}
}
