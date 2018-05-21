package org.javacs;

import com.sun.source.tree.*;
import com.sun.source.util.*;
import java.util.Optional;

class FindCursor {

    public static Optional<TreePath> find(JavacTask task, CompilationUnitTree source, int line, int column) {
        SourcePositions sourcePositions = Trees.instance(task).getSourcePositions();
        long offset = source.getLineMap().getPosition(line, column);

        class Finished extends RuntimeException {
            final TreePath found;

            Finished(TreePath of) {
                found = of;
            }
        }

        class Search extends TreePathScanner<Void, Void> {
            @Override
            public Void scan(Tree leaf, Void nothing) {
                if (containsCursor(leaf)) {
                    super.scan(leaf, nothing);

                    throw new Finished(new TreePath(getCurrentPath(), leaf));
                } else return null;
            }

            boolean containsCursor(Tree leaf) {
                long start = sourcePositions.getStartPosition(source, leaf);
                long end = sourcePositions.getEndPosition(source, leaf);

                return start <= offset && offset <= end;
            }

            @Override
            public Void visitErroneous(ErroneousTree node, Void nothing) {
                return super.scan(node.getErrorTrees(), nothing);
            }
        }

        try {
            new Search().scan(source, null);

            return Optional.empty();
        } catch (Finished found) {
            return Optional.of(found.found);
        }
    }
}
