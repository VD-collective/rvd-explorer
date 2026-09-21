package rvd.model;

import rvd.DiagramType;

/** Persistable view settings. Display toggles (shading, rays, etc.) stay as-is on load. */
public record ExplorerViewSettings(
        DiagramType diagramType,
        boolean polygonMode,
        boolean brocardIllumination,
        boolean showPolygonExterior,
        boolean showVisibilityCells,
        double stopAngle1,
        double stopAngle2
) {
}
