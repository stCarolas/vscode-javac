package org.javacs;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;

import java.net.URI;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.logging.Logger;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentPositionParams;
import org.junit.Ignore;
import org.junit.Test;

public class GotoTest {
    private static final Logger LOG = Logger.getLogger("main");
    private static final String file = "/org/javacs/example/Goto.java";
    private static final URI uri = FindResource.uri(file),
            other = FindResource.uri("/org/javacs/example/GotoOther.java");
    private static final String defaultConstructorFile = "/org/javacs/example/GotoDefaultConstructor.java";
    private static final URI defaultConstructorUri = FindResource.uri(defaultConstructorFile);

    @Test
    public void localVariable() {
        List<? extends Location> suggestions = doGoto(file, 9, 8);

        assertThat(suggestions, contains(location(uri, 4, 8, 4, 21)));
    }

    @Test
    public void defaultConstructor() {
        List<? extends Location> suggestions = doGoto(defaultConstructorFile, 4, 45);

        assertThat(suggestions, contains(location(defaultConstructorUri, 2, 0, 6, 1)));
    }

    @Test
    public void constructor() {
        List<? extends Location> suggestions = doGoto(file, 10, 20);

        assertThat(suggestions, contains(location(uri, 2, 0, 47, 1)));
    }

    @Test
    public void className() {
        List<? extends Location> suggestions = doGoto(file, 15, 8);

        assertThat(suggestions, contains(location(uri, 2, 0, 47, 1)));
    }

    @Test
    public void staticField() {
        List<? extends Location> suggestions = doGoto(file, 12, 21);

        assertThat(suggestions, contains(location(uri, 35, 4, 35, 37)));
    }

    @Test
    public void field() {
        List<? extends Location> suggestions = doGoto(file, 13, 21);

        assertThat(suggestions, contains(location(uri, 36, 4, 36, 24)));
    }

    @Test
    public void staticMethod() {
        List<? extends Location> suggestions = doGoto(file, 15, 13);

        assertThat(suggestions, contains(location(uri, 37, 4, 39, 5)));
    }

    @Test
    public void method() {
        List<? extends Location> suggestions = doGoto(file, 16, 13);

        assertThat(suggestions, contains(location(uri, 40, 4, 42, 5)));
    }

    @Test
    public void staticMethodReference() {
        List<? extends Location> suggestions = doGoto(file, 18, 26);

        assertThat(suggestions, contains(location(uri, 37, 4, 39, 5)));
    }

    @Test
    public void methodReference() {
        List<? extends Location> suggestions = doGoto(file, 19, 26);

        assertThat(suggestions, contains(location(uri, 40, 4, 42, 5)));
    }

    @Test
    public void otherStaticMethod() {
        List<? extends Location> suggestions = doGoto(file, 28, 24);

        assertThat(suggestions, contains(hasProperty("uri", equalTo(other.toString()))));
    }

    @Test
    public void otherMethod() {
        List<? extends Location> suggestions = doGoto(file, 29, 17);

        assertThat(suggestions, contains(hasProperty("uri", equalTo(other.toString()))));
    }

    @Test
    public void otherCompiledFile() {
        List<? extends Location> suggestions = doGoto(file, 28, 24);

        assertThat(suggestions, contains(hasProperty("uri", equalTo(other.toString()))));
    }

    @Test
    @Ignore // TODO
    public void typeParam() {
        List<? extends Location> suggestions = doGoto(file, 45, 11);

        assertThat(suggestions, contains(location(uri, 2, 18, 2, 23)));
    }

    @Test
    public void gotoEnum() {
        String file = "/org/javacs/example/GotoEnum.java";

        assertThat(doGoto(file, 5, 30), not(empty()));
        assertThat(doGoto(file, 5, 35), not(empty()));
    }

    private Location location(URI uri, int startRow, int startColumn, int endRow, int endColumn) {
        Position start = new Position();
        start.setLine(startRow);
        start.setCharacter(startColumn);

        Position end = new Position();
        end.setLine(endRow);
        end.setCharacter(endColumn);

        Range range = new Range();
        range.setStart(start);
        range.setEnd(end);

        Location location = new Location();
        location.setUri(uri.toString());
        location.setRange(range);

        return location;
    }

    private static final JavaLanguageServer server = LanguageServerFixture.getJavaLanguageServer();

    private List<? extends Location> doGoto(String file, int row, int column) {
        TextDocumentIdentifier document = new TextDocumentIdentifier();

        document.setUri(FindResource.uri(file).toString());

        Position position = new Position();

        position.setLine(row);
        position.setCharacter(column);

        TextDocumentPositionParams p = new TextDocumentPositionParams();

        p.setTextDocument(document);
        p.setPosition(position);

        try {
            return server.getTextDocumentService().definition(p).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }
}
