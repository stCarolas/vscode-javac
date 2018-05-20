package org.javacs;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import com.sun.source.tree.*;
import com.sun.source.util.*;
import java.io.*;
import java.net.URI;
import java.util.*;
import java.util.logging.*;
import java.util.stream.Collectors;
import javax.lang.model.*;
import javax.lang.model.element.*;
import javax.lang.model.type.*;
import javax.tools.*;
import org.junit.Test;

public class JavaPresentationCompilerTest {
    private static final Logger LOG = Logger.getLogger("main");

    private JavaPresentationCompiler compiler = new JavaPresentationCompiler();

    private String contents(String resourceFile) {
        try (InputStream in =
                JavaPresentationCompilerTest.class.getResourceAsStream(resourceFile)) {
            return new BufferedReader(new InputStreamReader(in))
                    .lines()
                    .collect(Collectors.joining("\n"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void element() {
        Element found =
                compiler.element(
                        URI.create("/HelloWorld.java"), contents("/HelloWorld.java"), 3, 24);

        assertThat(found.getSimpleName(), hasToString(containsString("println")));
    }

    @Test
    public void buildUpScope() {
        String contents = contents("/BuildUpScope.java");
        Scope a = compiler.scope(URI.create("/BuildUpScope.java"), contents, 3, 17);
        assertThat(localElements(a), containsInAnyOrder("super", "this", "a"));
        Scope b = compiler.scope(URI.create("/BuildUpScope.java"), contents, 4, 17);
        assertThat(localElements(b), containsInAnyOrder("super", "this", "a", "b"));
        Scope c = compiler.scope(URI.create("/BuildUpScope.java"), contents, 5, 17);
        assertThat(localElements(c), containsInAnyOrder("super", "this", "a", "b", "c"));
    }

    private List<String> localElements(Scope s) {
        List<String> result = new ArrayList<>();
        for (Element e : s.getLocalElements()) {
            result.add(e.getSimpleName().toString());
        }
        return result;
    }

    @Test
    public void pruneMethods() {
        Pruner pruner =
                new Pruner(URI.create("/PruneMethods.java"), contents("/PruneMethods.java"));
        String pruned = pruner.prune(6, 17);
        String expected = contents("/PruneMethods_erased.java");
        assertThat(pruned, equalToIgnoringWhiteSpace(expected));
    }
}
