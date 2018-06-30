package org.javacs;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Set;

public class JavacConfig {
    public final Set<Path> classPath, workspaceClassPath, docPath;

    public JavacConfig(
        Set<Path> classPath, 
        Set<Path> workspaceClassPath, 
        Set<Path> docPath
    ) {
        this.classPath = classPath;
        this.workspaceClassPath = workspaceClassPath;
        this.docPath = docPath;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        JavacConfig that = (JavacConfig) o;
        return Objects.equals(classPath, that.classPath)
                && Objects.equals(workspaceClassPath, that.workspaceClassPath)
                && Objects.equals(docPath, that.docPath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(classPath, workspaceClassPath, docPath);
    }
}
