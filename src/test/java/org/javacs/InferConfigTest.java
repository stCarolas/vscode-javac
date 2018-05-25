package org.javacs;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import org.junit.Test;

public class InferConfigTest {
    private Path workspaceRoot = Paths.get("src/test/test-project/workspace");
    private Path mavenHome = Paths.get("src/test/test-project/home/.m2");
    private Artifact externalArtifact = new Artifact("com.external", "external-library", "1.2");
    private InferConfig infer = new InferConfig(workspaceRoot, mavenHome);

    @Test
    public void mavenClassPath() {
        Optional<Path> found = infer.findMavenJar(externalArtifact, false);
        assertTrue(found.isPresent());
        assertThat(
                found.get(),
                equalTo(mavenHome.resolve("repository/com/external/external-library/1.2/external-library-1.2.jar")));
        // v1.1 should be ignored
    }

    @Test
    public void mavenDocPath() {
        Optional<Path> found = infer.findMavenJar(externalArtifact, true);
        assertTrue(found.isPresent());
        assertThat(
                found.get(),
                equalTo(
                        mavenHome.resolve(
                                "repository/com/external/external-library/1.2/external-library-1.2-sources.jar")));
        // v1.1 should be ignored
    }

    @Test
    public void dependencyList() {
        assertThat(InferConfig.dependencyList(Paths.get("pom.xml")), hasItem(new Artifact("com.sun", "tools", "1.8")));
    }
}
