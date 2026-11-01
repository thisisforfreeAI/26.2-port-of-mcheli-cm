package io.github.mcheliceport.content.weapon.hbm;

import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** HBM {@code ExplosionNT} settings. Upstream {@code NTSettingContainer}. */
public class NTSettingContainer {

    /** HBM {@code ExAttrib} names. */
    public final List<String> attributes = new ArrayList<>();
    public final int resolution;

    public NTSettingContainer(@Nullable List<String> attributes, int resolution) {
        if (attributes != null) {
            this.attributes.addAll(attributes);
        }
        this.resolution = resolution;
    }
}
