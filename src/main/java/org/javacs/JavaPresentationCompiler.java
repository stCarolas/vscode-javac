package org.javacs;

import com.sun.source.tree.*;
import com.sun.source.util.*;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.ToIntFunction;
import java.util.logging.*;
import javax.lang.model.*;
import javax.lang.model.element.*;
import javax.lang.model.type.*;
import javax.tools.*;

public class JavaPresentationCompiler {
    private static final Logger LOG = Logger.getLogger("main");

    private final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    private final StandardJavaFileManager fileManager =
            compiler.getStandardFileManager(this::report, null, Charset.defaultCharset());
    /**
     * Cache a pre-compiled version of a single source file. We will always try to recompile
     * incrementally using this file. When the user switches files or makes so many edits that we
     * can't re-use this cache, we will recompute it.
     */
    private Cache cache;

    private void recompileIfChanged(URI file, String contents) {
        if (cache == null || !cache.contents.equals(contents)) {
            cache = new Cache(file, contents);
        }
    }

    /** Find all members of `identifer`, which can be a qualified identifier like Foo.bar */
    public List<? extends Element> members(URI file, String contents, int line, String identifier) {
        return TODO();
    }

    /** Find the element at a point */
    public Optional<Element> element(URI file, String contents, int line, int character) {
        recompileIfChanged(file, contents);

        // Search for the smallest element that encompasses line:column
        Trees trees = Trees.instance(cache.task);
        SourcePositions pos = trees.getSourcePositions();
        LineMap lines = cache.parsed.getLineMap();
        ToIntFunction<Tree> width =
                t -> {
                    if (t == null) return Integer.MAX_VALUE;
                    long start = pos.getStartPosition(cache.parsed, t),
                            end = pos.getEndPosition(cache.parsed, t);
                    if (start == -1 || end == -1) return Integer.MAX_VALUE;
                    return (int) (end - start);
                };
        Ref<Tree> found = new Ref<>();
        scan(
                cache.parsed,
                leaf -> {
                    long start = pos.getStartPosition(cache.parsed, leaf),
                            end = pos.getEndPosition(cache.parsed, leaf);
                    // If element has no position, give up
                    if (start == -1 || end == -1) return;
                    long startLine = lines.getLineNumber(start),
                            startColumn = lines.getColumnNumber(start),
                            endLine = lines.getLineNumber(end),
                            endColumn = lines.getColumnNumber(end);
                    // Don't return multi-line elements
                    if (startLine != endLine) return;
                    if (startLine == line && startColumn <= character && character <= endColumn) {
                        if (width.applyAsInt(leaf) <= width.applyAsInt(found.value))
                            found.value = leaf;
                    }
                });

        if (found.value != null) {
            TreePath path = trees.getPath(cache.parsed, found.value);
            Element el = trees.getElement(path);
            return Optional.of(el);
        } else return Optional.empty();
    }

    private static <T> T TODO() {
        throw new UnsupportedOperationException("TODO");
    }

    private void scan(CompilationUnitTree root, Consumer<Tree> forEach) {
        new TreeScanner<Void, Void>() {
            @Override
            public Void scan(Tree tree, Void nothing) {
                if (tree != null) forEach.accept(tree);
                return super.scan(tree, nothing);
            }
        }.scan(root, null);
    }

    private void report(Diagnostic<? extends JavaFileObject> diags) {
        LOG.warning(diags.getMessage(null));
    }

    class Cache {
        final String contents;
        final URI file;
        final CompilationUnitTree parsed;
        final Element analyzed;
        final JavacTask task;

        Cache(URI file, String contents) {
            this.contents = contents;
            this.file = file;
            this.task =
                    (JavacTask)
                            compiler.getTask(
                                    null,
                                    fileManager,
                                    JavaPresentationCompiler.this::report,
                                    Arrays.asList("-proc:none", "-g"),
                                    Collections.emptyList(),
                                    Collections.singletonList(
                                            new StringFileObject(contents, file)));
            try {
                this.parsed = task.parse().iterator().next();
                this.analyzed = task.analyze().iterator().next();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
