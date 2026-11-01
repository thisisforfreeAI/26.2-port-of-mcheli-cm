package io.github.mcheliceport.content.addon;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

/**
 * Read access to the files of an addon pack, whether it is a folder or a zip or jar archive.
 *
 * <p>Replaces upstream's {@code ResourceLoader}, {@code FolderContentLoader} and
 * {@code FileContentLoader} file handling with one {@link Path}-based view.
 */
public final class PackFiles implements Closeable {

    private final Path root;
    private final @Nullable FileSystem archive;

    private PackFiles(Path root, @Nullable FileSystem archive) {
        this.root = root;
        this.archive = archive;
    }

    /** Opens a pack folder or archive. Close the result to release an archive. */
    public static PackFiles open(Path pack) throws IOException {
        if (Files.isDirectory(pack)) {
            return new PackFiles(pack, null);
        }
        FileSystem fs = FileSystems.newFileSystem(pack);
        return new PackFiles(fs.getPath("/"), fs);
    }

    public Path root() {
        return this.root;
    }

    /** The file at a pack-relative path ({@code a/b/c.yml}), or null if there is none. */
    public @Nullable Path resolve(String relativePath) {
        Path p = this.root;
        for (String part : relativePath.split("/")) {
            if (part.isEmpty() || part.equals(".") || part.equals("..")) {
                if (part.equals("..")) {
                    return null;
                }
                continue;
            }
            p = p.resolve(part);
        }
        return Files.isRegularFile(p) ? p : null;
    }

    /** Pack-relative paths of all files, with forward slashes, sorted. */
    public List<String> listFiles() throws IOException {
        List<String> list = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(this.root)) {
            stream.filter(Files::isRegularFile).forEach(p -> list.add(this.relativize(p)));
        }
        list.sort(null);
        return list;
    }

    private String relativize(Path p) {
        String s = this.root.relativize(p).toString().replace('\\', '/');
        while (s.startsWith("/")) {
            s = s.substring(1);
        }
        return s;
    }

    @Override
    public void close() throws IOException {
        if (this.archive != null) {
            this.archive.close();
        }
    }
}
