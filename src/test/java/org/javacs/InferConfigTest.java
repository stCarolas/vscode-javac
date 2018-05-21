package org.javacs;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class InferConfigTest {
    private Path workspaceRoot = Paths.get("src/test/test-project/workspace");
    private Path mavenHome = Paths.get("src/test/test-project/home/.m2");
    private Path gradleHome = Paths.get("src/test/test-project/home/.gradle");
    private Artifact externalArtifact = new Artifact("com.external", "external-library", "1.2");
    private List<Artifact> externalDependencies = ImmutableList.of(externalArtifact);
    private InferConfig both =
            new InferConfig(workspaceRoot, externalDependencies, Collections.emptyList(), mavenHome, gradleHome);
    private InferConfig gradle =
            new InferConfig(
                    workspaceRoot, externalDependencies, Collections.emptyList(), Paths.get("nowhere"), gradleHome);
    private InferConfig onlyPomXml =
            new InferConfig(
                    Paths.get("src/test/test-project/only-pom-xml"),
                    Collections.emptyList(),
                    Collections.emptyList(),
                    mavenHome,
                    Paths.get("nowhere"));
    private Path libraryJar = Paths.get("lib/library.jar");
    private InferConfig settingsClassPath =
            new InferConfig(
                    workspaceRoot, Collections.emptyList(), ImmutableList.of(libraryJar), mavenHome, gradleHome);

    @Test
    public void mavenClassPath() {
        assertThat(
                both.buildClassPath(),
                contains(mavenHome.resolve("repository/com/external/external-library/1.2/external-library-1.2.jar")));
        // v1.1 should be ignored
    }

    @Test
    public void gradleClasspath() {
        assertThat(
                gradle.buildClassPath(),
                contains(
                        gradleHome.resolve(
                                "caches/modules-2/files-2.1/com.external/external-library/1.2/xxx/external-library-1.2.jar")));
        // v1.1 should be ignored
    }

    @Test
    public void mavenDocPath() {
        assertThat(
                both.buildDocPath(),
                contains(
                        mavenHome.resolve(
                                "repository/com/external/external-library/1.2/external-library-1.2-sources.jar")));
        // v1.1 should be ignored
    }

    @Test
    public void gradleDocPath() {
        assertThat(
                gradle.buildDocPath(),
                contains(
                        gradleHome.resolve(
                                "caches/modules-2/files-2.1/com.external/external-library/1.2/yyy/external-library-1.2-sources.jar")));
        // v1.1 should be ignored
    }

    @Test
    public void dependencyList() {
        assertThat(InferConfig.dependencyList(Paths.get("pom.xml")), hasItem(new Artifact("com.sun", "tools", "1.8")));
    }

    @Test
    public void onlyPomXmlClassPath() {
        assertThat(
                onlyPomXml.buildClassPath(),
                contains(mavenHome.resolve("repository/com/external/external-library/1.2/external-library-1.2.jar")));
    }

    @Test
    public void onlyPomXmlDocPath() {
        assertThat(
                onlyPomXml.buildDocPath(),
                contains(
                        mavenHome.resolve(
                                "repository/com/external/external-library/1.2/external-library-1.2-sources.jar")));
    }

    @Test
    public void settingsClassPath() {
        assertThat(settingsClassPath.buildClassPath(), contains(workspaceRoot.resolve(libraryJar)));
    }
}
