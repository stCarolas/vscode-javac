package org.javacs;

import com.sun.source.tree.*;
import com.sun.source.util.*;
import com.sun.tools.javac.api.JavacTool;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.*;
import javax.tools.*;
import org.eclipse.lsp4j.*;

class Parser {

    private static final JavacTool compiler = JavacTool.create(); // TODO switch to java 9 mechanism
    private static final StandardJavaFileManager fileManager =
            compiler.getStandardFileManager(__ -> {}, null, Charset.defaultCharset());

    private static JavacTask parseTask(JavaFileObject file) {
        return compiler.getTask(
                null,
                fileManager,
                err -> LOG.warning(err.getMessage(Locale.getDefault())),
                Collections.emptyList(),
                null,
                Collections.singletonList(file));
    }

    private static JavacTask parseTask(Path source) {
        JavaFileObject file =
                fileManager.getJavaFileObjectsFromFiles(Collections.singleton(source.toFile())).iterator().next();
        return parseTask(file);
    }

    static CompilationUnitTree parse(JavaFileObject file) {
        try {
            return parseTask(file).parse().iterator().next();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    static CompilationUnitTree parse(Path source) {
        try {
            return parseTask(source).parse().iterator().next();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Check if `candidate` contains all the characters of `find`, in-order, case-insensitive Matches can be
     * discontinuous if the letters of `find` match the first letters of words in `candidate` For example, fb matches
     * FooBar, but it doesn't match Foobar (exposed for testing)
     */
    static boolean matchesTitleCase(CharSequence candidate, String find) {
        int i = 0;
        for (char f : find.toCharArray()) {
            // If we have reached the end of candidate without matching all of find, fail
            if (i >= candidate.length()) return false;
            // If the next character in candidate matches, advance i
            else if (Character.toLowerCase(f) == Character.toLowerCase(candidate.charAt(i))) i++;
            else {
                // Find the start of the next word
                while (i < candidate.length()) {
                    char c = candidate.charAt(i);
                    boolean isStartOfWord = Character.isUpperCase(c);
                    boolean isMatch = Character.toLowerCase(f) == Character.toLowerCase(c);
                    if (isStartOfWord && isMatch) {
                        i++;
                        break;
                    } else i++;
                }
                if (i == candidate.length()) return false;
            }
        }
        return true;
    }

    private static final Pattern WORD = Pattern.compile("\\b\\w+\\b");

    static boolean containsWordMatching(Path java, String query) {
        try {
            for (String line : Files.readAllLines(java)) {
                Matcher pattern = WORD.matcher(line);
                while (pattern.find()) {
                    String word = pattern.group(0);
                    if (matchesTitleCase(word, query)) return true;
                }
            }
            return false;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static Stream<TreePath> findSymbolsMatching(CompilationUnitTree parse, String query) {
        class Find extends TreePathScanner<Void, Void> {
            List<TreePath> found = new ArrayList<>();

            void accept(TreePath path) {
                Tree node = path.getLeaf();
                if (node instanceof ClassTree) {
                    ClassTree c = (ClassTree) node;
                    if (matchesTitleCase(c.getSimpleName(), query)) found.add(path);
                } else if (node instanceof MethodTree) {
                    MethodTree m = (MethodTree) node;
                    if (matchesTitleCase(m.getName(), query)) found.add(path);
                } else if (node instanceof VariableTree) {
                    VariableTree v = (VariableTree) node;
                    if (matchesTitleCase(v.getName(), query)) found.add(path);
                }
            }

            @Override
            public Void visitClass(ClassTree node, Void nothing) {
                super.visitClass(node, nothing);
                accept(getCurrentPath());
                for (Tree t : node.getMembers()) {
                    TreePath child = new TreePath(getCurrentPath(), t);
                    accept(child);
                }
                return null;
            }

            List<TreePath> run() {
                scan(parse, null);
                return found;
            }
        }
        return new Find().run().stream();
    }

    /** Search `dir` for .java files containing important symbols matching `query` */
    static Stream<TreePath> findSymbols(Path dir, String query) {
        PathMatcher match = FileSystems.getDefault().getPathMatcher("glob:*.java");

        try {
            return Files.walk(dir)
                    .filter(java -> match.matches(java.getFileName()))
                    .filter(java -> containsWordMatching(java, query))
                    .flatMap(java -> findSymbolsMatching(parse(java), query));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    static List<TreePath> documentSymbols(Path java, String content) {
        CompilationUnitTree parse = parse(new StringFileObject(content, java.toUri()));
        return findSymbolsMatching(parse, "").collect(Collectors.toList());
    }

    static Location location(TreePath p) {
        // This is very questionable, will this Trees object actually work?
        JavacTask task = parseTask(p.getCompilationUnit().getSourceFile());
        Trees trees = Trees.instance(task);
        SourcePositions pos = trees.getSourcePositions();
        CompilationUnitTree cu = p.getCompilationUnit();
        LineMap lines = cu.getLineMap();
        long start = pos.getStartPosition(cu, p.getLeaf()), end = pos.getEndPosition(cu, p.getLeaf());
        int startLine = (int) lines.getLineNumber(start) - 1, startCol = (int) lines.getColumnNumber(start) - 1;
        int endLine = (int) lines.getLineNumber(end) - 1, endCol = (int) lines.getColumnNumber(end) - 1;
        URI dUri = cu.getSourceFile().toUri();
        return new Location(
                dUri.toString(), new Range(new Position(startLine, startCol), new Position(endLine, endCol)));
    }

    private static SymbolKind asSymbolKind(Tree.Kind k) {
        switch (k) {
            case ANNOTATION_TYPE:
            case CLASS:
                return SymbolKind.Class;
            case ENUM:
                return SymbolKind.Enum;
            case INTERFACE:
                return SymbolKind.Interface;
            case METHOD:
                return SymbolKind.Method;
            case TYPE_PARAMETER:
                return SymbolKind.TypeParameter;
            case VARIABLE:
                // This method is used for symbol-search functionality,
                // where we only return fields, not local variables
                return SymbolKind.Field;
            default:
                return null;
        }
    }

    private static String containerName(TreePath path) {
        TreePath parent = path.getParentPath();
        while (parent != null) {
            Tree t = parent.getLeaf();
            if (t instanceof ClassTree) {
                ClassTree c = (ClassTree) t;
                return c.getSimpleName().toString();
            } else if (t instanceof CompilationUnitTree) {
                CompilationUnitTree c = (CompilationUnitTree) t;
                return c.getPackageName().toString();
            } else {
                parent = parent.getParentPath();
            }
        }
        return null;
    }

    private static String symbolName(Tree t) {
        if (t instanceof ClassTree) {
            ClassTree c = (ClassTree) t;
            return c.getSimpleName().toString();
        } else if (t instanceof MethodTree) {
            MethodTree m = (MethodTree) t;
            return m.getName().toString();
        } else if (t instanceof VariableTree) {
            VariableTree v = (VariableTree) t;
            return v.getName().toString();
        } else {
            LOG.warning("Don't know how to create SymbolInformation from " + t);
            return "???";
        }
    }

    static SymbolInformation asSymbolInformation(TreePath path) {
        SymbolInformation i = new SymbolInformation();
        Tree t = path.getLeaf();
        i.setKind(asSymbolKind(t.getKind()));
        i.setName(symbolName(t));
        i.setContainerName(containerName(path));
        i.setLocation(Parser.location(path));
        return i;
    }

    private static final Logger LOG = Logger.getLogger("main");
}
