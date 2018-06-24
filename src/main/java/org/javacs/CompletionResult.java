package org.javacs;

import java.util.List;

public class CompletionResult {
    public final List<Completion> items;
    public final boolean isIncomplete;

    public CompletionResult(List<Completion> items, boolean isIncomplete) {
        this.items = items;
        this.isIncomplete = isIncomplete;
    }
}
