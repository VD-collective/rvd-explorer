package rvd.model;

/** Persistable explorer content: site geometry plus view settings (not camera). */
public record ExplorerInstance(
        ExplorerSnapshot snapshot,
        ExplorerViewSettings view
) {
}
