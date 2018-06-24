package org.javacs;

import com.google.common.base.Joiner;
import com.google.common.reflect.*;
import com.sun.source.tree.*;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.*;

class FixImports {

    private final ClassPathIndex classPath;

    FixImports(ClassPathIndex classPath) {
        this.classPath = classPath;
    }

    /** Find all unresolved symbols that start with an uppercase letter */
    Set<String> unresolvedSymbols(CompilationUnitTree tree) {
        // TODO
        return Collections.emptySet();
    }

    /** Find all already-imported symbols in all .java files in sourcePath */
    ExistingImports existingImports(List<Path> sourcePath) {
        // TODO
        return new ExistingImports(Collections.emptySet(), Collections.emptySet());
    }

    private Optional<String> resolveSymbol(String unresolved, ExistingImports imports) {
        // Try to disambiguate by looking for exact matches
        // For example, Foo is exactly matched by `import com.bar.Foo`
        // Foo is *not* exactly matched by `import com.bar.*`
        Set<String> candidates =
                imports.classes.stream().filter(c -> c.endsWith(unresolved)).collect(Collectors.toSet());
        if (candidates.size() > 1) {
            LOG.info(String.format("%s in ambiguous between %s", unresolved, Joiner.on(", ").join(candidates)));
            return Optional.empty();
        } else if (candidates.size() == 1) {
            return Optional.of(candidates.iterator().next());
        }

        // Try to disambiguate by looking at package names
        // Both normal imports like `import com.bar.Foo`, and star-imports like `import com.bar.*`,
        // are used to generate package names
        candidates =
                classPath
                        .topLevelClasses()
                        .filter(c -> c.getSimpleName().equals(unresolved))
                        .filter(c -> imports.packages.contains(c.getPackageName()))
                        .map(c -> c.getName())
                        .collect(Collectors.toSet());
        if (candidates.size() > 1) {
            LOG.info(String.format("%s in ambiguous between %s", unresolved, Joiner.on(", ").join(candidates)));
            return Optional.empty();
        } else if (candidates.size() == 1) {
            return Optional.of(candidates.iterator().next());
        }

        // Try to import from java classpath
        Comparator<ClassPath.ClassInfo> order =
                Comparator.comparing(
                        c -> {
                            String p = c.getPackageName();
                            if (p.startsWith("java.lang")) return 1;
                            else if (p.startsWith("java.util")) return 2;
                            else if (p.startsWith("java.io")) return 3;
                            else return 4;
                        });
        return classPath
                .topLevelClasses()
                .filter(c -> c.getPackageName().startsWith("java."))
                .filter(c -> c.getSimpleName().equals(unresolved))
                .sorted(order)
                .map(c -> c.getName())
                .findFirst();
    }

    Map<String, String> resolveSymbols(Set<String> unresolvedSymbols, ExistingImports imports) {
        Map<String, String> result = new HashMap<>();
        for (String s : unresolvedSymbols) {
            resolveSymbol(s, imports).ifPresent(resolved -> result.put(s, resolved));
        }
        return result;
    }

    static class ExistingImports {
        /** Fully-qualified names of classes that have been imported on the source path */
        final Set<String> classes;
        /** Package names from star-imports like `import java.util.*` */
        final Set<String> packages;

        ExistingImports(Set<String> classes, Set<String> packages) {
            this.classes = classes;
            this.packages = packages;
        }
    }

    private static final Logger LOG = Logger.getLogger("main");
}
