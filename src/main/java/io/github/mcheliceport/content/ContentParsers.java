package io.github.mcheliceport.content;

import io.github.mcheliceport.content.parser.ContentParser;
import io.github.mcheliceport.content.parser.txt.TxtParser;
import io.github.mcheliceport.content.parser.yaml.YamlParser;
import java.util.Locale;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Content parsers by file extension: {@code .yml} (YAML, the current format) and {@code .txt}
 * (the legacy format). Ported from upstream {@code com.norwood.mcheli.helper.info.ContentParsers}.
 */
public final class ContentParsers {

    private static final Map<String, ContentParser> PARSERS = Map.of(
            "txt", TxtParser.INSTANCE,
            "yml", YamlParser.INSTANCE);

    private ContentParsers() {
    }

    /** The parser for a file extension (with or without the dot, any case), or null. */
    public static @Nullable ContentParser get(@Nullable String extension) {
        if (extension == null) {
            return null;
        }
        String ext = extension.startsWith(".") ? extension.substring(1) : extension;
        return PARSERS.get(ext.toLowerCase(Locale.ROOT));
    }
}
