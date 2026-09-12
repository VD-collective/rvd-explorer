package rvd.io;

/** Invalid file content, version, or schema (parse errors wrap the cause). */
public final class ExplorerJsonException extends Exception {
    public ExplorerJsonException(String message) {
        super(message);
    }

    public ExplorerJsonException(String message, Throwable cause) {
        super(message, cause);
    }
}
