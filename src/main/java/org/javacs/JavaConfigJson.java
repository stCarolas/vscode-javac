package org.javacs;

import java.nio.file.Path;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;

public class JavaConfigJson {
    public Set<Path> sourcePath, classPath = Collections.emptySet(), docPath = Collections.emptySet();
    public Optional<Path> classPathFile = Optional.empty(), docPathFile = Optional.empty();
    public Path outputDirectory;
}
