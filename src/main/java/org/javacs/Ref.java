package org.javacs;

import java.util.Optional;

class Ref<T> {
    T value;

    Optional<T> option() {
        return Optional.ofNullable(value);
    }
}
