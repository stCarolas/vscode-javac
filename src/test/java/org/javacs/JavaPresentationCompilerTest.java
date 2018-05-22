package org.javacs;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import com.sun.source.tree.*;
import com.sun.source.util.*;
import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.logging.*;
import java.util.stream.Collectors;
import javax.lang.model.*;
import javax.lang.model.element.*;
import javax.lang.model.type.*;
import javax.tools.*;
import org.junit.Test;

public class JavaPresentationCompilerTest {
    private static final Logger LOG = Logger.getLogger("main");

    private JavaPresentationCompiler compiler =
            new JavaPresentationCompiler(Collections.singleton(resourcesDir()), Collections.emptySet());

    private static Path resourcesDir() {
        try {
            return Paths.get(JavaPresentationCompilerTest.class.getResource("/HelloWorld.java").toURI()).getParent();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    private String contents(String resourceFile) {
        try (InputStream in = JavaPresentationCompilerTest.class.getResourceAsStream(resourceFile)) {
            return new BufferedReader(new InputStreamReader(in)).lines().collect(Collectors.joining("\n"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void element() {
        Element found = compiler.element(URI.create("/HelloWorld.java"), contents("/HelloWorld.java"), 3, 24);

        assertThat(found.getSimpleName(), hasToString(containsString("println")));
    }

    private List<String> localElements(Scope s) {
        List<String> result = new ArrayList<>();
        for (Element e : s.getLocalElements()) {
            result.add(e.getSimpleName().toString());
        }
        return result;
    }

    @Test
    public void pruneMethods() {
        Pruner pruner = new Pruner(URI.create("/PruneMethods.java"), contents("/PruneMethods.java"));
        pruner.prune(6, 19);
        String expected = contents("/PruneMethods_erased.java");
        assertThat(pruner.contents(), equalToIgnoringWhiteSpace(expected));
    }

    @Test
    public void pruneToEndOfBlock() {
        Pruner pruner = new Pruner(URI.create("/PruneToEndOfBlock.java"), contents("/PruneToEndOfBlock.java"));
        pruner.prune(4, 18);
        String expected = contents("/PruneToEndOfBlock_erased.java");
        assertThat(pruner.contents(), equalToIgnoringWhiteSpace(expected));
    }

    @Test
    public void identifiers() {
        List<Element> found =
                compiler.scopeMembers(
                        URI.create("/CompleteIdentifiers.java"), contents("/CompleteIdentifiers.java"), 13, 21);
        List<String> names = found.stream().map(e -> e.getSimpleName().toString()).collect(Collectors.toList());
        assertThat(names, hasItem("completeLocal"));
        assertThat(names, hasItem("completeParam"));
        assertThat(names, hasItem("super"));
        assertThat(names, hasItem("this"));
        assertThat(names, hasItem("completeOtherMethod"));
        assertThat(names, hasItem("completeInnerField"));
        assertThat(names, hasItem("completeOuterField"));
        assertThat(names, hasItem("completeOuterStatic"));
        assertThat(names, hasItem("CompleteIdentifiers"));
    }

    @Test
    public void completeIdentifiers() {
        List<Element> found =
                compiler.completions(
                        URI.create("/CompleteIdentifiers.java"), contents("/CompleteIdentifiers.java"), 13, 21);
        List<String> names = found.stream().map(e -> e.getSimpleName().toString()).collect(Collectors.toList());
        assertThat(names, hasItem("completeLocal"));
        assertThat(names, hasItem("completeParam"));
        assertThat(names, hasItem("super"));
        assertThat(names, hasItem("this"));
        assertThat(names, hasItem("completeOtherMethod"));
        assertThat(names, hasItem("completeInnerField"));
        assertThat(names, hasItem("completeOuterField"));
        assertThat(names, hasItem("completeOuterStatic"));
        assertThat(names, hasItem("CompleteIdentifiers"));
    }

    @Test
    public void members() {
        List<Element> found =
                compiler.members(URI.create("/CompleteMembers.java"), contents("/CompleteMembers.java"), 3, 14);
        List<String> names = found.stream().map(e -> e.getSimpleName().toString()).collect(Collectors.toList());
        assertThat(names, hasItem("subMethod"));
        assertThat(names, hasItem("superMethod"));
        assertThat(names, hasItem("equals"));
    }

    @Test
    public void completeMembers() {
        List<Element> found =
                compiler.completions(URI.create("/CompleteMembers.java"), contents("/CompleteMembers.java"), 3, 15);
        List<String> names = found.stream().map(e -> e.getSimpleName().toString()).collect(Collectors.toList());
        assertThat(names, hasItem("subMethod"));
        assertThat(names, hasItem("superMethod"));
        assertThat(names, hasItem("equals"));
    }

    @Test
    public void gotoDefinition() {
        Optional<TreePath> def =
                compiler.definition(URI.create("/GotoDefinition.java"), contents("/GotoDefinition.java"), 3, 12);
        assertTrue(def.isPresent());

        TreePath t = def.get();
        CompilationUnitTree unit = t.getCompilationUnit();
        assertThat(unit.getSourceFile().getName(), endsWith("GotoDefinition.java"));

        Trees trees = compiler.trees();
        SourcePositions pos = trees.getSourcePositions();
        LineMap lines = unit.getLineMap();
        long start = pos.getStartPosition(unit, t.getLeaf());
        long line = lines.getLineNumber(start);
        assertThat(line, equalTo(6L));
    }

    @Test
    public void references() {
        List<TreePath> refs =
                compiler.references(URI.create("/GotoDefinition.java"), contents("/GotoDefinition.java"), 6, 13);
        boolean found = false;
        for (TreePath t : refs) {
            CompilationUnitTree unit = t.getCompilationUnit();
            String name = unit.getSourceFile().getName();
            Trees trees = compiler.trees();
            SourcePositions pos = trees.getSourcePositions();
            LineMap lines = unit.getLineMap();
            long start = pos.getStartPosition(unit, t.getLeaf());
            long line = lines.getLineNumber(start);
            if (name.endsWith("GotoDefinition.java") && line == 3) found = true;
        }

        if (!found) fail(String.format("No GotoDefinition.java line 3 in %s", refs));
    }

    @Test
    public void overloads() {
        List<ExecutableElement> found =
                compiler.overloads(URI.create("/Overloads.java"), contents("/Overloads.java"), 3, 15);

        assertThat(found, containsInAnyOrder(hasToString("print(int)"), hasToString("print(java.lang.String)")));
    }
}
