package org.javacs;

import com.sun.source.tree.*;
import com.sun.source.util.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.*;
import java.util.regex.*;
import java.util.stream.*;

class FixImports {
    final CompilationUnitTree parsed;
    final SourcePositions sourcePositions;
    final Set<String> fixedImports;

    FixImports(CompilationUnitTree parsed, SourcePositions sourcePositions, Set<String> fixedImports) {
        this.parsed = parsed;
        this.sourcePositions = sourcePositions;
        this.fixedImports = fixedImports;
    }
}
