package org.javacs;

import com.sun.tools.javac.api.ClientCodeWrapper;
import com.sun.tools.javac.util.DiagnosticSource;
import com.sun.tools.javac.util.JCDiagnostic;
import java.util.Locale;
import java.util.Optional;
import java.util.logging.Logger;
import javax.tools.JavaFileObject;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;

class Lints {

    static Optional<Diagnostic> convert(javax.tools.Diagnostic<? extends JavaFileObject> error) {
        if (error.getStartPosition() != javax.tools.Diagnostic.NOPOS) {
            Range range = position(error);
            Diagnostic diagnostic = new Diagnostic();
            DiagnosticSeverity severity = severity(error.getKind());

            diagnostic.setSeverity(severity);
            diagnostic.setRange(range);
            diagnostic.setCode(error.getCode());
            diagnostic.setMessage(error.getMessage(null));

            return Optional.of(diagnostic);
        } else {
            LOG.warning("Skipped " + error.getMessage(Locale.getDefault()));

            return Optional.empty();
        }
    }

    private static DiagnosticSeverity severity(javax.tools.Diagnostic.Kind kind) {
        switch (kind) {
            case ERROR:
                return DiagnosticSeverity.Error;
            case WARNING:
            case MANDATORY_WARNING:
                return DiagnosticSeverity.Warning;
            case NOTE:
            case OTHER:
            default:
                return DiagnosticSeverity.Information;
        }
    }

    private static Range position(javax.tools.Diagnostic<? extends JavaFileObject> error) {
        if (error instanceof ClientCodeWrapper.DiagnosticSourceUnwrapper)
            error = ((ClientCodeWrapper.DiagnosticSourceUnwrapper) error).d;

        JCDiagnostic diagnostic = (JCDiagnostic) error;
        DiagnosticSource source = diagnostic.getDiagnosticSource();
        long start = error.getStartPosition(), end = error.getEndPosition();

        if (end == start) end = start + 1;

        return new Range(
                new Position(source.getLineNumber((int) start) - 1, source.getColumnNumber((int) start, true) - 1),
                new Position(source.getLineNumber((int) end) - 1, source.getColumnNumber((int) end, true) - 1));
    }

    private static final Logger LOG = Logger.getLogger("main");
}
