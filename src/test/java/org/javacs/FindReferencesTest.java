package org.javacs;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import java.net.URI;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.logging.Logger;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.ReferenceParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.Test;

public class FindReferencesTest {
    private static final Logger LOG = Logger.getLogger("main");

    private static final JavaLanguageServer server = LanguageServerFixture.getJavaLanguageServer();

    protected List<? extends Location> items(String file, int row, int column) {
        URI uri = FindResource.uri(file);
        ReferenceParams params = new ReferenceParams();

        params.setTextDocument(new TextDocumentIdentifier(uri.toString()));
        params.setUri(uri.toString());
        params.setPosition(new Position(row - 1, column - 1));

        try {
            return server.getTextDocumentService().references(params).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void findAllReferences() {
        assertThat(items("/org/javacs/example/GotoOther.java", 6, 30), not(empty()));
    }
}
