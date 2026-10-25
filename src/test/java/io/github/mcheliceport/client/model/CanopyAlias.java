package io.github.mcheliceport.client.model;

import java.util.List;
import java.util.function.Function;

/**
 * Upstream's TXT parser adds each vehicle's first canopy twice, as {@code canopy0} and as
 * {@code canopy}, so a model may name it either way; the stock YAML kept both entries. The model
 * checks count the pair as present when the model has either one.
 */
final class CanopyAlias {
    private CanopyAlias() {
    }

    static boolean missing(Function<String, ? extends List<?>> part, String name) {
        if (!part.apply(name).isEmpty()) {
            return false;
        }
        return switch (name) {
            case "canopy" -> part.apply("canopy0").isEmpty();
            case "canopy0" -> part.apply("canopy").isEmpty();
            default -> true;
        };
    }
}
