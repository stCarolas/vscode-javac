package org.javacs;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Test;

public class ParserTest {
    @Test
    public void testMatchesTitleCase() {
        assertTrue(Parser.matchesTitleCase("FooBar", "fb"));
        assertTrue(Parser.matchesTitleCase("FooBar", "fob"));
        assertTrue(Parser.matchesTitleCase("AnyPrefixFooBar", "fb"));
        assertTrue(Parser.matchesTitleCase("AutocompleteBetweenLines", "ABetweenLines"));
        assertTrue(Parser.matchesTitleCase("UPPERFooBar", "fb"));
        assertFalse(Parser.matchesTitleCase("Foobar", "fb"));
    }

    @Test
    public void findAutocompleteBetweenLines() {
        Path file =
                LanguageServerFixture.DEFAULT_WORKSPACE_ROOT.resolve(
                        Paths.get("src", "org", "javacs", "example", "AutocompleteBetweenLines.java"));
        assertTrue(Parser.containsWordMatching(file, "ABetweenLines"));
    }
}
