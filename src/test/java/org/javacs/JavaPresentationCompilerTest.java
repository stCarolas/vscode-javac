package org.javacs;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import com.sun.source.tree.*;
import com.sun.source.util.*;
import java.net.URI;
import java.util.*;
import java.util.logging.*;
import javax.lang.model.*;
import javax.lang.model.element.*;
import javax.lang.model.type.*;
import javax.tools.*;
import org.junit.Test;

public class JavaPresentationCompilerTest {
    private static final Logger LOG = Logger.getLogger("main");

    private JavaPresentationCompiler compiler = new JavaPresentationCompiler();

    private String helloWorld =
            "public class HelloWorld {\n"
                    + "  public static void main(String[] args) {\n"
                    + "    System.out.println(\"Hello world!\");\n"
                    + "  }\n"
                    + "}";

    @Test
    public void element() {
        Element found = compiler.element(URI.create("/HelloWorld.java"), helloWorld, 3, 18);

        assertThat(found.getSimpleName(), hasToString(containsString("println")));
    }

    private String buildUpScope =
            "class BuildUpScope {\n"
                    + "  void main() {\n"
                    + "    int a = 1;\n"
                    + "    int b = 2;\n"
                    + "    int c = 3;\n"
                    + "  }\n"
                    + "  void otherMethod() { }\n"
                    + "}\n";

    @Test
    public void buildUpScope() {
        Scope a = compiler.scope(URI.create("/BuildUpScope.java"), buildUpScope, 3, 12);
        assertThat(localElements(a), containsInAnyOrder("super", "this", "a"));
        Scope b = compiler.scope(URI.create("/BuildUpScope.java"), buildUpScope, 4, 12);
        assertThat(localElements(b), containsInAnyOrder("super", "this", "a", "b"));
        Scope c = compiler.scope(URI.create("/BuildUpScope.java"), buildUpScope, 5, 12);
        assertThat(localElements(c), containsInAnyOrder("super", "this", "a", "b", "c"));
    }

    private List<String> localElements(Scope s) {
        List<String> result = new ArrayList<>();
        for (Element e : s.getLocalElements()) {
            result.add(e.getSimpleName().toString());
        }
        return result;
    }
}
