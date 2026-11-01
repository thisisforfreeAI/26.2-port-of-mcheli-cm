package io.github.mcheliceport.shim;

/** An entity made of {@link PartEntity parts}; NeoForge's {@code isMultipartEntity/getParts}. */
public interface MultipartEntity {

    boolean isMultipartEntity();

    PartEntity<?>[] getParts();
}
