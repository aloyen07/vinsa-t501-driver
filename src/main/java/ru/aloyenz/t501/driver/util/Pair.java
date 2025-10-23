package ru.aloyenz.t501.driver.util;

import java.util.Objects;

public record Pair<F, S>(F first, S second) {

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Pair<?, ?>(Object first1, Object second1))) return false;
        return Objects.equals(first, first1) && Objects.equals(second, second1);
    }

    @Override
    public int hashCode() {
        return Objects.hash(first, second);
    }
}
