package org.javacs;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertTrue;

import com.sun.source.util.*;
import java.io.IOException;
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
    public void element() throws IOException {
        Optional<Element> found =
                compiler.element(URI.create("/HelloWorld.java"), helloWorld, 3, 18);

        assertTrue(found.isPresent());

        LOG.info(found.get().toString());
    }
}
