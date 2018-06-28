package org.javacs;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.overzealous.remark.Options;
import com.overzealous.remark.Remark;
import com.sun.javadoc.ClassDoc;
import com.sun.javadoc.MethodDoc;
import com.sun.javadoc.Parameter;
import com.sun.javadoc.RootDoc;
import com.sun.source.util.JavacTask;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.BreakIterator;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.tools.*;

// This class must be public so DocletInvoker can call it
public class Javadocs {

    /** Cache for performance reasons */
    private final StandardJavaFileManager actualFileManager;

    /** Empty file manager we pass to javadoc to prevent it from roaming all over the place */
    private final StandardJavaFileManager emptyFileManager =
            ServiceLoader.load(JavaCompiler.class).iterator().next().getStandardFileManager(__ -> {}, null, null);

    /** All the classes we have indexed so far */
    private final Map<String, IndexedDoc> topLevelClasses = new ConcurrentHashMap<>();

    private static class IndexedDoc {
        final RootDoc doc;
        final Instant updated;

        IndexedDoc(RootDoc doc, Instant updated) {
            this.doc = doc;
            this.updated = updated;
        }
    }

    private final JavacTask task;

    Javadocs(Set<Path> sourcePath, Set<Path> docPath) {
        this.actualFileManager = createFileManager(allSourcePaths(sourcePath, docPath));
        this.task =
                (JavacTask)
                        ServiceLoader.load(JavaCompiler.class)
                                .iterator()
                                .next()
                                .getTask(null, emptyFileManager, __ -> {}, null, null, null);
    }

    @SafeVarargs
    private static Set<File> allSourcePaths(Set<Path>... userSourcePath) {
        Set<File> allSourcePaths = new HashSet<>();

        // Add userSourcePath
        for (Set<Path> eachPath : userSourcePath) {
            for (Path each : eachPath) allSourcePaths.add(each.toFile());
        }

        // Add src.zip from JDK
        findSrcZip().ifPresent(allSourcePaths::add);

        return allSourcePaths;
    }

    private static StandardJavaFileManager createFileManager(Set<File> allSourcePaths) {
        StandardJavaFileManager actualFileManager =
                ServiceLoader.load(JavaCompiler.class).iterator().next().getStandardFileManager(__ -> {}, null, null);

        try {
            actualFileManager.setLocation(StandardLocation.SOURCE_PATH, allSourcePaths);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return actualFileManager;
    }

    private static final Pattern HTML_TAG = Pattern.compile("<(\\w+)>");

    private static boolean isHtml(String text) {
        Matcher tags = HTML_TAG.matcher(text);
        while (tags.find()) {
            String tag = tags.group(1);
            String close = String.format("</%s>", tag);
            int findClose = text.indexOf(close, tags.end());
            if (findClose != -1) return true;
        }
        return false;
    }

    /** If `commentText` looks like HTML, convert it to markdown */
    static String htmlToMarkdown(String commentText) {
        if (isHtml(commentText)) {
            Options options = new Options();

            options.tables = Options.Tables.CONVERT_TO_CODE_BLOCK;
            options.hardwraps = true;
            options.inlineLinks = true;
            options.autoLinks = true;
            options.reverseHtmlSmartPunctuation = true;

            return new Remark(options).convertFragment(commentText);
        } else return commentText;
    }

    /** Get docstring for method, using inherited method if necessary */
    static Optional<String> commentText(MethodDoc doc) {
        // TODO search interfaces as well

        while (doc != null && Strings.isNullOrEmpty(doc.commentText())) doc = doc.overriddenMethod();

        if (doc == null || Strings.isNullOrEmpty(doc.commentText())) return Optional.empty();
        else return Optional.of(doc.commentText());
    }

    // Figure out if elements and docs refer to the same thing, by computing String keys

    private String elementParamKey(VariableElement param) {
        return task.getTypes().erasure(param.asType()).toString();
    }

    private String elementMethodKey(ExecutableElement method) {
        TypeElement classElement = (TypeElement) method.getEnclosingElement();
        String params = method.getParameters().stream().map(this::elementParamKey).collect(Collectors.joining(","));
        return String.format("%s#%s(%s)", classElement.getQualifiedName(), method.getSimpleName(), params);
    }

    private String docParamKey(Parameter doc) {
        return doc.type().qualifiedTypeName() + doc.type().dimension();
    }

    private String docMethodKey(MethodDoc doc) {
        String params = Arrays.stream(doc.parameters()).map(this::docParamKey).collect(Collectors.joining(","));
        return String.format("%s#%s(%s)", doc.containingClass().qualifiedName(), doc.name(), params);
    }

    Optional<MethodDoc> methodDoc(ExecutableElement method) {
        String key = elementMethodKey(method);
        TypeElement classElement = (TypeElement) method.getEnclosingElement();
        String className = classElement.getQualifiedName().toString();
        RootDoc rootDoc = index(className);
        ClassDoc classDoc = rootDoc.classNamed(className);
        if (classDoc == null) {
            LOG.warning("No docs for class " + className);
            return Optional.empty();
        }
        for (MethodDoc each : classDoc.methods(false)) {
            if (docMethodKey(each).equals(key)) return Optional.of(each);
        }
        return Optional.empty();
    }

    Optional<ClassDoc> classDoc(TypeElement c) {
        String className = c.getQualifiedName().toString();
        RootDoc index = index(className);
        return Optional.ofNullable(index.classNamed(className));
    }

    /** Get or compute the javadoc for `className` */
    RootDoc index(String className) {
        if (needsUpdate(className)) topLevelClasses.put(className, doIndex(className));

        return topLevelClasses.get(className).doc;
    }

    private boolean needsUpdate(String className) {
        if (!topLevelClasses.containsKey(className)) return true;

        IndexedDoc indexed = topLevelClasses.get(className);

        try {
            JavaFileObject source =
                    actualFileManager.getJavaFileForInput(
                            StandardLocation.SOURCE_PATH, className, JavaFileObject.Kind.SOURCE);

            if (source == null) return true;

            Instant modified = Instant.ofEpochMilli(source.getLastModified());

            return indexed.updated.isBefore(modified);
        } catch (IOException e) {
            throw new RuntimeException();
        }
    }

    /** Read all the Javadoc for `className` */
    private IndexedDoc doIndex(String className) {
        try {
            JavaFileObject source =
                    actualFileManager.getJavaFileForInput(
                            StandardLocation.SOURCE_PATH, className, JavaFileObject.Kind.SOURCE);

            if (source == null) {
                LOG.warning("No source file for " + className);

                return new IndexedDoc(EmptyRootDoc.INSTANCE, Instant.EPOCH);
            }

            LOG.info("Found " + source.toUri() + " for " + className);

            DocumentationTool.DocumentationTask task =
                    ToolProvider.getSystemDocumentationTool()
                            .getTask(null, emptyFileManager, __ -> {}, Javadocs.class, null, ImmutableList.of(source));

            task.call();

            return new IndexedDoc(
                    getSneakyReturn().orElse(EmptyRootDoc.INSTANCE), Instant.ofEpochMilli(source.getLastModified()));
        } catch (IOException e) {
            throw new RuntimeException();
        }
    }

    private Optional<RootDoc> getSneakyReturn() {
        RootDoc result = sneakyReturn.get();
        sneakyReturn.remove();

        if (result == null) {
            LOG.warning("index did not return a RootDoc");

            return Optional.empty();
        } else return Optional.of(result);
    }

    /** start(RootDoc) uses this to return its result to doIndex(...) */
    private static ThreadLocal<RootDoc> sneakyReturn = new ThreadLocal<>();

    /**
     * Called by the javadoc tool
     *
     * <p>{@link com.sun.javadoc.Doclet}
     */
    public static boolean start(RootDoc root) {
        sneakyReturn.set(root);

        return true;
    }

    /** Find the copy of src.zip that comes with the system-installed JDK */
    static Optional<File> findSrcZip() {
        Path path = Paths.get(System.getProperty("java.home"));

        if (path.endsWith("jre")) path = path.getParent();

        path = path.resolve("src.zip");

        while (Files.isSymbolicLink(path)) {
            try {
                path = path.getParent().resolve(Files.readSymbolicLink(path));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        if (Files.exists(path)) return Optional.of(path.toFile());
        else {
            LOG.severe(String.format("Could not find %s", path));

            return Optional.empty();
        }
    }

    /**
     * Get the first sentence of a doc-comment.
     *
     * <p>In general, VS Code does a good job of only displaying the beginning of a doc-comment where appropriate. But
     * if VS Code is displaying too much and you want to only show the first sentence, use this.
     */
    public static String firstSentence(String doc) {
        BreakIterator breaks = BreakIterator.getSentenceInstance();

        breaks.setText(doc.replace('\n', ' '));

        int start = breaks.first(), end = breaks.next();

        if (start == -1 || end == -1) return doc;

        return doc.substring(start, end).trim();
    }

    private static final Logger LOG = Logger.getLogger("main");
}
