package rvd.io;

import rvd.model.ExplorerInstance;

import java.io.IOException;
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
}
