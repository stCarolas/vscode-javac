package org.javacs;

import com.sun.source.util.JavacTask;
import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;
import java.util.Optional;
import java.util.stream.Stream;
import javax.lang.model.element.Element;
import org.eclipse.lsp4j.Location;

public class References {

    public static Stream<Location> findReferences(FocusedResult compiled, FindSymbols find) {
        return compiled.cursor
                .map(cursor -> new References(compiled.task, find).doFindReferences(cursor))
                .orElseGet(Stream::empty);
    }

    public static Optional<Location> gotoDefinition(FocusedResult compiled, FindSymbols find) {
        return compiled.cursor.flatMap(cursor -> new References(compiled.task, find).doGotoDefinition(cursor));
    }

    private final JavacTask task;
    private final FindSymbols find;

    private References(JavacTask task, FindSymbols find) {
        this.task = task;
        this.find = find;
    }

    private Stream<Location> doFindReferences(TreePath cursor) {
        Trees trees = Trees.instance(task);
        Element symbol = trees.getElement(cursor);

        if (symbol == null) return Stream.empty();
        else return find.references(symbol);
    }

    private Optional<Location> doGotoDefinition(TreePath cursor) {
        CursorContext context = CursorContext.from(cursor);

        if (context == CursorContext.NewClass) cursor = context.find(cursor);

        Trees trees = Trees.instance(task);
        Element symbol = trees.getElement(cursor);

        return find.find(symbol);
    }
}
