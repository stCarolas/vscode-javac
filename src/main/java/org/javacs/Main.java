package org.javacs;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JSR310Module;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ServiceLoader;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.tools.JavaCompiler;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageClient;

public class Main {
    public static final ObjectMapper JSON =
            new ObjectMapper()
                    .registerModule(new Jdk8Module())
                    .registerModule(new JSR310Module())
                    .registerModule(pathAsJson())
                    .configure(JsonGenerator.Feature.AUTO_CLOSE_TARGET, false)
                    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                    .configure(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES, false);

    private static final Logger LOG = Logger.getLogger("main");

    public static void setRootFormat() {
        Logger root = Logger.getLogger("");

        for (Handler h : root.getHandlers()) h.setFormatter(new LogFormat());
    }

    private static SimpleModule pathAsJson() {
        SimpleModule m = new SimpleModule();

        m.addSerializer(
                Path.class,
                new JsonSerializer<Path>() {
                    @Override
                    public void serialize(Path path, JsonGenerator gen, SerializerProvider serializerProvider)
                            throws IOException, JsonProcessingException {
                        gen.writeString(path.toString());
                    }
                });

        m.addDeserializer(
                Path.class,
                new JsonDeserializer<Path>() {
                    @Override
                    public Path deserialize(JsonParser parse, DeserializationContext deserializationContext)
                            throws IOException, JsonProcessingException {
                        return Paths.get(parse.getText());
                    }
                });

        return m;
    }

    public static void main(String[] args) {
        try {
            // TODO remove when it is stable again
            Logger.getLogger("").addHandler(new FileHandler("javacs.%u.log", false));
            ClassLoader langTools = LangTools.createLangToolsClassLoader();
            Class<?> main = Class.forName("org.javacs.Main", true, langTools);
            Method run = main.getMethod("run");
            run.invoke(null);
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Failed", e);
        }
    }

    public static ClassLoader checkJavacClassLoader() {
        return ServiceLoader.load(JavaCompiler.class).iterator().next().getClass().getClassLoader();
    }

    public static void run() {
        assert checkJavacClassLoader() instanceof ChildFirstClassLoader;
        setRootFormat();

        try {
            JavaLanguageServer server = new JavaLanguageServer();
            ExecutorService threads = Executors.newSingleThreadExecutor(runnable -> new Thread(runnable, "client"));
            Launcher<LanguageClient> launcher =
                    LSPLauncher.createServerLauncher(
                            server, System.in, System.out, threads, messageConsumer -> messageConsumer);

            server.installClient(launcher.getRemoteProxy());
            launcher.startListening();
            LOG.info(String.format("java.version is %s", System.getProperty("java.version")));
        } catch (Throwable t) {
            LOG.log(Level.SEVERE, t.getMessage(), t);

            System.exit(1);
        }
    }
}
