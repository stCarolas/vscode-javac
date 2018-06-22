package org.javacs;

import static org.junit.Assert.assertEquals;

import java.util.StringJoiner;
import java.util.concurrent.ExecutionException;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentPositionParams;
import org.junit.Ignore;
import org.junit.Test;

public class SymbolUnderCursorTest {

    @Test
    public void classDeclaration() {
        assertEquals(
                "org.javacs.example.SymbolUnderCursor", symbolAt("/org/javacs/example/SymbolUnderCursor.java", 3, 22));
    }

    @Test
    public void fieldDeclaration() {
        assertEquals("field", symbolAt("/org/javacs/example/SymbolUnderCursor.java", 4, 22));
    }

    @Test
    public void methodDeclaration() {
        assertEquals("method(java.lang.String)", symbolAt("/org/javacs/example/SymbolUnderCursor.java", 6, 22));
    }

    @Test
    public void methodParameterDeclaration() {
        assertEquals("methodParameter", symbolAt("/org/javacs/example/SymbolUnderCursor.java", 6, 36));
    }

    @Test
    public void localVariableDeclaration() {
        assertEquals("localVariable", symbolAt("/org/javacs/example/SymbolUnderCursor.java", 7, 22));
    }

    @Test
    public void constructorParameterDeclaration() {
        assertEquals("constructorParameter", symbolAt("/org/javacs/example/SymbolUnderCursor.java", 17, 46));
    }

    @Test
    public void classIdentifier() {
        assertEquals(
                "org.javacs.example.SymbolUnderCursor", symbolAt("/org/javacs/example/SymbolUnderCursor.java", 12, 23));
    }

    @Test
    public void fieldIdentifier() {
        assertEquals("field", symbolAt("/org/javacs/example/SymbolUnderCursor.java", 9, 27));
    }

    @Test
    public void methodIdentifier() {
        assertEquals("method(java.lang.String)", symbolAt("/org/javacs/example/SymbolUnderCursor.java", 12, 12));
    }

    @Test
    public void methodSelect() {
        assertEquals("method(java.lang.String)", symbolAt("/org/javacs/example/SymbolUnderCursor.java", 13, 17));
    }

    @Ignore // tree.sym is null
    @Test
    public void methodReference() {
        assertEquals("method", symbolAt("/org/javacs/example/SymbolUnderCursor.java", 14, 46));
    }

    @Test
    public void methodParameterReference() {
        assertEquals("methodParameter", symbolAt("/org/javacs/example/SymbolUnderCursor.java", 10, 32));
    }

    @Test
    public void localVariableReference() {
        assertEquals("localVariable", symbolAt("/org/javacs/example/SymbolUnderCursor.java", 10, 16));
    }

    // Re-using the language server makes these tests go a lot faster, but it will potentially produce surprising output
    // if things go wrong
    private static final JavaLanguageServer server = LanguageServerFixture.getJavaLanguageServer();

    private String symbolAt(String file, int line, int character) {
        TextDocumentPositionParams pos =
                new TextDocumentPositionParams(
                        new TextDocumentIdentifier(FindResource.uri(file).toString()),
                        new Position(line - 1, character - 1));
        StringJoiner result = new StringJoiner("\n");
        try {
            server.getTextDocumentService()
                    .hover(pos)
                    .get()
                    .getContents()
                    .getLeft()
                    .forEach(hover -> result.add(hover.getRight().getValue()));
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
        return result.toString();
    }
}
