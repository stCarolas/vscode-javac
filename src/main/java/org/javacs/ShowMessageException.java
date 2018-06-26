package org.javacs;

import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;

class ShowMessageException extends RuntimeException {
    private final MessageParams message;

    ShowMessageException(MessageParams message, Exception cause) {
        super(message.getMessage(), cause);

        this.message = message;
    }

    static ShowMessageException error(String message, Exception cause) {
        return create(MessageType.Error, message, cause);
    }

    static ShowMessageException warning(String message, Exception cause) {
        return create(MessageType.Warning, message, cause);
    }

    private static ShowMessageException create(MessageType type, String message, Exception cause) {
        MessageParams m = new MessageParams(type, message);

        return new ShowMessageException(m, cause);
    }
}
