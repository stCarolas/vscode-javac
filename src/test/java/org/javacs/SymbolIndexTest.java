package org.javacs;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Optional;
import org.junit.Test;

public class SymbolIndexTest {
    private Path workspaceRoot = Paths.get("src/test/test-project/workspace");
    private SymbolIndex index = new SymbolIndex(workspaceRoot, () -> Collections.emptySet(), __ -> Optional.empty());

    @Test
    public void workspaceSourcePath() {
        assertThat(index.sourcePath(), contains(workspaceRoot.resolve("src").toAbsolutePath()));
    }
}
