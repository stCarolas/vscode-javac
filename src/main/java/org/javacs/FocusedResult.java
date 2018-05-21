package org.javacs;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreePath;
import java.util.Optional;

class FocusedResult {
    final CompilationUnitTree compilationUnit;
    final Optional<TreePath> cursor;
    final JavacTask task;
    final ClassPathIndex classPath;

    FocusedResult(
            CompilationUnitTree compilationUnit, Optional<TreePath> cursor, JavacTask task, ClassPathIndex classPath) {
        this.compilationUnit = compilationUnit;
        this.cursor = cursor;
        this.task = task;
        this.classPath = classPath;
    }
}
