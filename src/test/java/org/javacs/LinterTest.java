package org.javacs;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Optional;
import java.util.logging.Logger;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import org.junit.Test;

public class LinterTest {
    private static final Logger LOG = Logger.getLogger("main");

    private static final JavacHolder compiler =
            JavacHolder.create(
                    Collections.singleton(Paths.get("src/test/test-project/workspace/src")), Collections.emptySet());

    @Test
    public void compile() throws IOException {
        URI file = FindResource.uri("/org/javacs/example/HelloWorld.java");
        DiagnosticCollector<JavaFileObject> errors =
                compiler.compileBatch(Collections.singletonMap(file, Optional.empty()));

        assertThat(errors.getDiagnostics(), empty());
    }

    @Test
    public void missingMethodBody() throws IOException {
        URI file = FindResource.uri("/org/javacs/example/MissingMethodBody.java");
        DiagnosticCollector<JavaFileObject> compile =
                compiler.compileBatch(Collections.singletonMap(file, Optional.empty()));

        assertThat(compile.getDiagnostics(), not(empty()));
    }

    @Test
    public void incompleteAssignment() throws IOException {
        URI file = FindResource.uri("/org/javacs/example/IncompleteAssignment.java");
        DiagnosticCollector<JavaFileObject> compile =
                compiler.compileBatch(Collections.singletonMap(file, Optional.empty()));

        assertThat(compile.getDiagnostics(), not(empty()));
    }

    @Test
    public void undefinedSymbol() throws IOException {
        URI file = FindResource.uri("/org/javacs/example/UndefinedSymbol.java");
        DiagnosticCollector<JavaFileObject> compile =
                compiler.compileBatch(Collections.singletonMap(file, Optional.empty()));

        assertThat(compile.getDiagnostics(), not(empty()));

        Diagnostic<? extends JavaFileObject> d = compile.getDiagnostics().get(0);

        // Error position should span entire 'foo' symbol
        assertThat(d.getLineNumber(), greaterThan(0L));
        assertThat(d.getStartPosition(), greaterThan(0L));
        assertThat(d.getEndPosition(), greaterThan(d.getStartPosition() + 1));
    }

    @Test(expected = IllegalArgumentException.class)
    public void notJava() {
        URI file = FindResource.uri("/org/javacs/example/NotJava.java.txt");
        DiagnosticCollector<JavaFileObject> compile =
                compiler.compileBatch(Collections.singletonMap(file, Optional.empty()));
    }

    @Test
    public void errorInDependency() {
        URI file = FindResource.uri("/org/javacs/example/ErrorInDependency.java");
        DiagnosticCollector<JavaFileObject> compile =
                compiler.compileBatch(Collections.singletonMap(file, Optional.empty()));

        assertThat(compile.getDiagnostics(), not(empty()));
    }

    @Test
    public void deprecationWarning() {
        URI file = FindResource.uri("/org/javacs/example/DeprecationWarning.java");
        DiagnosticCollector<JavaFileObject> compile =
                compiler.compileBatch(Collections.singletonMap(file, Optional.empty()));

        assertThat(compile.getDiagnostics(), not(empty()));
    }

    @Test
    public void parseError() {
        URI file = URI.create("/org/javacs/example/ArrowTry.java");
        String source =
                "package org.javacs.example;\n"
                        + "\n"
                        + "class Example {\n"
                        + "    private static <In, Out> Function<In, Stream<Out>> catchClasspathErrors(Function<In, Stream<Out>> f) {\n"
                        + "        return in -> try {\n"
                        + "            return f.apply(in);\n"
                        + "        } catch (Symbol.CompletionFailure failed) {\n"
                        + "            LOG.warning(failed.getMessage());\n"
                        + "            return Stream.empty();\n"
                        + "        };\n"
                        + "    }\n"
                        + "}";
        DiagnosticCollector<JavaFileObject> compile =
                compiler.compileBatch(Collections.singletonMap(file, Optional.of(source)));

        assertThat(compile.getDiagnostics(), not(empty()));
    }
}
