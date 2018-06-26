package org.javacs;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import java.util.Collections;
import java.util.Map;
import org.junit.Test;

public class ParserFixImportsTest {
    ClassPathIndex classPath = new ClassPathIndex(Collections.emptySet());
    ExistingImports emptyImports = new ExistingImports(Collections.emptySet(), Collections.emptySet());

    @Test
    public void findJavaUtilList() {
        Map<String, String> resolved = Parser.resolveSymbols(Collections.singleton("List"), emptyImports, classPath);
        assertThat(resolved, hasEntry("List", "java.util.List"));
    }

    @Test
    public void findExistingImports() {
        ExistingImports find = Parser.existingImports(Collections.singleton(JavaCompilerServiceTest.resourcesDir()));
        assertThat(find.classes, hasItem("java.util.List"));
        assertThat(find.packages, hasItem("java.util"));
    }
}
