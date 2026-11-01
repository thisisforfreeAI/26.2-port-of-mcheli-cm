package io.github.mcheliceport.content;

/**
 * A parsed content definition.
 *
 * <p>Ported from upstream {@code com.norwood.mcheli.helper.info.IContentData}. Upstream's
 * {@code onPostReload} hook reattached items and models after {@code /mcheli reconfig}; the port
 * keeps content free of registry objects and models, so the hook is not needed.
 */
public interface ContentData {

    /**
     * Checks the definition and fills in derived values. The content loader calls this exactly once
     * per parsed file, whichever parser produced it.
     *
     * @return false if the definition is invalid but still usable
     * @throws Exception if the definition cannot be used at all
     */
    boolean validate() throws Exception;

    ContentLocation getLocation();

    /** Path of the source file inside its addon pack, with forward slashes. */
    String getContentPath();
}
