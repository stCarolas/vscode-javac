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
    private Cache cache;

    private void report(Diagnostic<? extends JavaFileObject> diags) {
        LOG.warning(diags.getMessage(null));
    }

    private JavacTask singleFileTask(URI file, String contents) {
        return (JavacTask)
                compiler.getTask(
                        null,
                        fileManager,
                        JavaPresentationCompiler.this::report,
                        Arrays.asList("-proc:none", "-g"),
                        Collections.emptyList(),
                        Collections.singletonList(new StringFileObject(contents, file)));
    }

    class Cache {
        final String contents;
        final URI file;
        final CompilationUnitTree parsed;
        final Element analyzed;
        final JavacTask task;
        final long focusStart, focusEnd;

        Cache(URI file, String contents, int line, int character) {
            if (line != -1) {
                Pruner p = new Pruner(file, contents);
                p.prune(line, character);
                this.contents = p.contents();
                this.focusStart = p.focusStart();
                this.focusEnd = p.focusEnd();
            } else {
                this.contents = contents;
                this.focusStart = 0;
                this.focusEnd = contents.length();
            }
            this.file = file;
            this.task = singleFileTask(file, contents);
            try {
                this.parsed = task.parse().iterator().next();
                this.analyzed = task.analyze().iterator().next();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        boolean focusIncludes(int line, int character) {
            long p = parsed.getLineMap().getPosition(line, character);
            return focusStart <= p && p <= focusEnd;
        }
    }

    private void recompile(URI file, String contents, int line, int character) {
        if (cache == null
                || !cache.file.equals(file)
                || !cache.contents.equals(contents)
                || !cache.focusIncludes(line, character)) {
            cache = new Cache(file, contents, line, character);
        }
        // TODO recover from small changes, recompile on large changes
    }

    private boolean leq(long beforeLine, long beforeColumn, long afterLine, long afterColumn) {
        if (beforeLine < afterLine) return true;
        else if (beforeLine == afterLine) return beforeColumn <= afterColumn;
        else return false;
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

    /** Find the smallest tree that includes the cursor */
    private TreePath path(URI file, String contents, int line, int character) {
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
                    if (leq(startLine, startColumn, line, character)
                            && leq(line, character, endLine, endColumn)) {
                        if (width.applyAsInt(leaf) <= width.applyAsInt(found.value))
                            found.value = leaf;
                    }
                });
        if (found.value == null)
            throw new RuntimeException(
                    String.format("No TreePath to %s %d:%d", file, line, character));
        else return trees.getPath(cache.parsed, found.value);
    }

    /** Find the scope at a point. Exposed for testing. */
    Scope scope(URI file, String contents, int line, int character) {
        recompile(file, contents, line, character);

        Trees trees = Trees.instance(cache.task);
        TreePath path = path(file, contents, line, character);
        return trees.getScope(path);
    }

    /** Find all members of `expr`, which can be any expression */
    public List<? extends Element> members(URI file, String contents, int line, int character) {
        return TODO();
    }

    /** Find the smallest element that includes the cursor */
    public Element element(URI file, String contents, int line, int character) {
        recompile(file, contents, line, character);

        Trees trees = Trees.instance(cache.task);
        TreePath path = path(file, contents, line, character);
        return trees.getElement(path);
    }
}
