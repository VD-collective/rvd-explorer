package rvd.io;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import rvd.DiagramType;
import rvd.model.ExplorerInstance;
import rvd.model.ExplorerSnapshot;
import rvd.model.ExplorerViewSettings;
import xyz.marsavic.geometry.Vector;

import java.util.ArrayList;
import java.util.List;

/** Human-readable instance file: version, sites, and selected view settings. */
public final class ExplorerJsonCodec {
    public static final String CURRENT_VERSION = "1";
    public static final int MAX_SITES = 64;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private ExplorerJsonCodec() {
    }

    public static String encode(ExplorerInstance instance) {
        return GSON.toJson(toDto(instance));
    }

    public static ExplorerInstance decode(String json) throws ExplorerJsonException {
        InstanceFileV1 dto;
        try {
            dto = GSON.fromJson(json, InstanceFileV1.class);
        } catch (Exception e) {
            throw new ExplorerJsonException("Invalid JSON", e);
        }
        if (dto == null) {
            throw new ExplorerJsonException("Empty instance file");
        }
        return fromDto(dto);
    }

    static InstanceFileV1 toDto(ExplorerInstance instance) {
        ExplorerSnapshot snapshot = instance.snapshot();
        ExplorerViewSettings view = instance.view();

        List<SiteJson> sites = new ArrayList<>();
        Vector[] points = snapshot.points();
        double[] angles = snapshot.angles();
        boolean[] enabled = snapshot.enabled();
        for (int k = 0; k < snapshot.n(); k++) {
            SiteJson site = new SiteJson();
            site.x = points[k].x();
            site.y = points[k].y();
            site.angle = angles[k];
            site.enabled = enabled[k];
            sites.add(site);
        }

        InstanceFileV1 root = new InstanceFileV1();
        root.version = CURRENT_VERSION;
        root.rotate = snapshot.rotate();
        root.n = snapshot.n();
        root.sites = sites;
        root.diagramType = view.diagramType().name();
        root.polygonMode = view.polygonMode();
        root.brocardIllumination = view.brocardIllumination();
        root.showPolygonExterior = view.showPolygonExterior();
        root.showVisibilityCells = view.showVisibilityCells();
        root.stopAngle1 = view.stopAngle1();
        root.stopAngle2 = view.stopAngle2();
        return root;
    }

    static ExplorerInstance fromDto(InstanceFileV1 dto) throws ExplorerJsonException {
        if (dto.version == null || dto.version.isEmpty()) {
            throw new ExplorerJsonException("version is required");
        }
        if (!CURRENT_VERSION.equals(dto.version)) {
            throw new ExplorerJsonException("Unsupported version: " + dto.version);
        }
        if (dto.sites == null) {
            throw new ExplorerJsonException("sites is required");
        }
        if (dto.n < 1 || dto.n > MAX_SITES) {
            throw new ExplorerJsonException("n must be between 1 and " + MAX_SITES);
        }
        if (dto.sites.size() != dto.n) {
            throw new ExplorerJsonException("sites length must equal n");
        }

        Vector[] points = new Vector[dto.n];
        double[] angles = new double[dto.n];
        boolean[] enabled = new boolean[dto.n];
        for (int k = 0; k < dto.n; k++) {
            SiteJson site = dto.sites.get(k);
            if (site == null) {
                throw new ExplorerJsonException("sites[" + k + "] is required");
            }
            if (site.x == null || site.y == null || site.angle == null || site.enabled == null) {
                throw new ExplorerJsonException("sites[" + k + "] requires x, y, angle, enabled");
            }
            points[k] = Vector.xy(site.x, site.y);
            angles[k] = site.angle;
            enabled[k] = site.enabled;
        }

        DiagramType diagramType = parseDiagramType(dto.diagramType);

        ExplorerSnapshot snapshot = new ExplorerSnapshot(dto.rotate, dto.n, points, angles, enabled);
        ExplorerViewSettings view = new ExplorerViewSettings(
                diagramType,
                dto.polygonMode,
                dto.brocardIllumination,
                dto.showPolygonExterior,
                dto.showVisibilityCells,
                dto.stopAngle1,
                dto.stopAngle2
        );
        return new ExplorerInstance(snapshot, view);
    }

    private static DiagramType parseDiagramType(String name) throws ExplorerJsonException {
        if (name == null || name.isEmpty()) {
            throw new ExplorerJsonException("diagramType is required");
        }
        try {
            return DiagramType.valueOf(name);
        } catch (IllegalArgumentException e) {
            throw new ExplorerJsonException("Unknown diagramType: " + name);
        }
    }

    static final class InstanceFileV1 {
        String version;
        double rotate;
        int n;
        List<SiteJson> sites;
        String diagramType;
        boolean polygonMode;
        boolean brocardIllumination;
        boolean showPolygonExterior;
        boolean showVisibilityCells;
        double stopAngle1;
        double stopAngle2;
    }

    static final class SiteJson {
        Double x;
        Double y;
        Double angle;
        Boolean enabled;
    }
}
