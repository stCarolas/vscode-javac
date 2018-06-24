package org.javacs;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import java.nio.file.*;
import java.util.*;
import org.junit.Test;

public class FixImportsTest {
    FixImports fix = new FixImports(new ClassPathIndex(Collections.emptySet()));
    FixImports.ExistingImports emptyImports =
            new FixImports.ExistingImports(Collections.emptySet(), Collections.emptySet());

    @Test
    public void findJavaUtilList() {
        Map<String, String> resolved = fix.resolveSymbols(Collections.singleton("List"), emptyImports);
        assertThat(resolved, hasEntry("List", "java.util.List"));
    }

    @Test
    public void findExistingImports() {
        FixImports.ExistingImports find =
                fix.existingImports(Collections.singleton(JavaCompilerServiceTest.resourcesDir()));
        assertThat(find.classes, hasItem("java.util.List"));
        assertThat(find.packages, hasItem("java.util"));
    }
}
