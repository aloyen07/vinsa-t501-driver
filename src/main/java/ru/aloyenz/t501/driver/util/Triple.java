package ru.aloyenz.t501.driver.util;

import java.util.Objects;

public record Triple<F, S, T>(F first, S second, T third) {

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Triple<?, ?, ?>(Object first1, Object second1, Object third1))) return false;
        return Objects.equals(first, first1) && Objects.equals(third, third1) && Objects.equals(second, second1);
    }

    @Override
    public int hashCode() {
        return Objects.hash(first, second, third);
    }
}
