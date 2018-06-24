package org.javacs;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.logging.Logger;
import java.util.stream.*;
import javax.tools.*;

class InferSourcePath {

    static Stream<Path> allJavaFiles(Path dir) {
        PathMatcher match = FileSystems.getDefault().getPathMatcher("glob:*.java");

        try {
            return Files.walk(dir).filter(java -> match.matches(java.getFileName()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    static Set<Path> sourcePath(Path workspaceRoot) {
        class SourcePaths implements Consumer<Path> {
            int certaintyThreshold = 10;
            Map<Path, Integer> sourceRoots = new HashMap<>();

            boolean alreadyKnown(Path java) {
                for (Path root : sourceRoots.keySet()) {
                    if (java.startsWith(root) && sourceRoots.get(root) > certaintyThreshold) return true;
                }
                return false;
            }

            Optional<Path> infer(Path java) {
                String packageName = Objects.toString(Parser.parse(java).getPackageName(), "");
                String packagePath = packageName.replace('.', File.separatorChar);
                Path dir = java.getParent();
                if (!dir.endsWith(packagePath)) {
                    LOG.warning("Java source file " + java + " is not in " + packagePath);
                    return Optional.empty();
                } else {
                    int up = Paths.get(packagePath).getNameCount();
                    Path truncate = dir;
                    for (int i = 0; i < up; i++) truncate = truncate.getParent();
                    return Optional.of(truncate);
                }
            }

            @Override
            public void accept(Path java) {
                if (!alreadyKnown(java)) {
                    infer(java)
                            .ifPresent(
                                    root -> {
                                        int count = sourceRoots.getOrDefault(root, 0);
                                        sourceRoots.put(root, count + 1);
                                    });
                }
            }
        }
        SourcePaths checker = new SourcePaths();
        allJavaFiles(workspaceRoot).forEach(checker);
        return checker.sourceRoots.keySet();
    }

    private static final Logger LOG = Logger.getLogger("main");
}
