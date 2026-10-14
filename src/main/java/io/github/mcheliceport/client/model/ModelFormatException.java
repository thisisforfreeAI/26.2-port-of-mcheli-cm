package io.github.mcheliceport.client.model;

/** A model file that cannot be parsed. Ported from upstream {@code _ModelFormatException}. */
public class ModelFormatException extends Exception {

    public ModelFormatException(String message) {
        super(message);
    }

    public ModelFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
