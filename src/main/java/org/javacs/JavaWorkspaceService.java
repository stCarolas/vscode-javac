package org.javacs;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.eclipse.lsp4j.DidChangeConfigurationParams;
import org.eclipse.lsp4j.DidChangeWatchedFilesParams;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.WorkspaceSymbolParams;
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
        List<SymbolInformation> list =
                server.compiler
                        .findSymbols(params.getQuery())
                        .map(Parser::asSymbolInformation)
                        .limit(50)
                        .collect(Collectors.toList());
        return CompletableFuture.completedFuture(list);
    }

    @Override
    public void didChangeConfiguration(DidChangeConfigurationParams change) {}

    @Override
    public void didChangeWatchedFiles(DidChangeWatchedFilesParams params) {}
}
