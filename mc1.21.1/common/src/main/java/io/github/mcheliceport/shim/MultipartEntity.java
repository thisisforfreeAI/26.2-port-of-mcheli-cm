package io.github.mcheliceport.shim;

/**
 * An entity made of {@link PartEntity parts}: NeoForge's {@code isMultipartEntity/getParts}, under
 * other names so they do not clash with NeoForge's own methods on that loader.
 */
public interface MultipartEntity {
    boolean hasParts();

    PartEntity<?>[] parts();
}
