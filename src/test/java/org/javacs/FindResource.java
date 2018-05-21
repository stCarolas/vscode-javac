package org.javacs;

import java.io.File;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Find java sources in test-project/workspace/src */
public class FindResource {
    public static URI uri(String resourcePath) {
        if (resourcePath.startsWith("/")) resourcePath = resourcePath.substring(1);

        Path path = Paths.get("./src/test/test-project/workspace/src").resolve(resourcePath).normalize();
        File file = path.toFile();

        if (!file.exists()) throw new RuntimeException(file + " does not exist");

        return file.toURI();
    }
}
