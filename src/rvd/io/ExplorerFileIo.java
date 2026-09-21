package rvd.io;

import rvd.model.ExplorerInstance;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** UTF-8 JSON on disk; body is whatever {@link ExplorerJsonCodec} produces. */
public final class ExplorerFileIo {
    private ExplorerFileIo() {
    }

    public static void save(Path path, ExplorerInstance instance) throws IOException {
        Files.writeString(path, ExplorerJsonCodec.encode(instance), StandardCharsets.UTF_8);
    }

    public static ExplorerInstance load(Path path) throws IOException, ExplorerJsonException {
        String json = Files.readString(path, StandardCharsets.UTF_8);
        return ExplorerJsonCodec.decode(json);
    }

    /** Bundled startup instance, packaged next to this class. */
    public static ExplorerInstance loadDefault() throws IOException, ExplorerJsonException {
        try (InputStream in = ExplorerFileIo.class.getResourceAsStream("default-instance.json")) {
            if (in == null) {
                throw new IOException("Missing default instance");
            }
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return ExplorerJsonCodec.decode(json);
        }
    }
}
