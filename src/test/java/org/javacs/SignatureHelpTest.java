package org.javacs;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.SignatureHelp;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentPositionParams;
import org.junit.Test;

public class SignatureHelpTest {
    @Test
    public void signatureHelp() throws IOException {
        SignatureHelp help = doHelp("/org/javacs/example/SignatureHelp.java", 7, 36);

        assertThat(help.getSignatures(), hasSize(2));
    }

    @Test
    public void partlyFilledIn() throws IOException {
        SignatureHelp help = doHelp("/org/javacs/example/SignatureHelp.java", 8, 39);

        assertThat(help.getSignatures(), hasSize(2));
        assertThat(help.getActiveSignature(), equalTo(1));
        assertThat(help.getActiveParameter(), equalTo(1));
    }

    @Test
    public void constructor() throws IOException {
        SignatureHelp help = doHelp("/org/javacs/example/SignatureHelp.java", 9, 27);

        assertThat(help.getSignatures(), hasSize(1));
        assertThat(help.getSignatures().get(0).getLabel(), startsWith("SignatureHelp"));
    }

    @Test
    public void platformConstructor() throws IOException {
        SignatureHelp help = doHelp("/org/javacs/example/SignatureHelp.java", 10, 26);

        assertThat(help.getSignatures(), not(empty()));
        assertThat(help.getSignatures().get(0).getLabel(), startsWith("ArrayList"));
        // TODO
        // assertThat(help.getSignatures().get(0).getDocumentation(), not(nullValue()));
    }

    private static final JavaLanguageServer server = LanguageServerFixture.getJavaLanguageServer();

    private SignatureHelp doHelp(String file, int row, int column) throws IOException {
        TextDocumentIdentifier document = new TextDocumentIdentifier();

        document.setUri(FindResource.uri(file).toString());

        Position position = new Position();

        position.setLine(row - 1);
        position.setCharacter(column - 1);

        TextDocumentPositionParams p = new TextDocumentPositionParams();

        p.setTextDocument(document);
        p.setPosition(position);

        try {
            return server.getTextDocumentService().signatureHelp(p).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }
}
