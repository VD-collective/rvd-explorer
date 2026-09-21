package rvd.io;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import rvd.DiagramType;
import rvd.model.ExplorerInstance;
import rvd.model.ExplorerSnapshot;
import rvd.model.ExplorerState;
import rvd.model.ExplorerViewSettings;
import xyz.marsavic.geometry.Vector;

import java.util.ArrayList;
import java.util.List;

/**
 * Human-readable instance file. {@code version} and each site's {@code x}, {@code y}, and {@code angle}
 * are required. Other fields fall back to the explorer gadget defaults when absent.
 * Encoding still writes every field.
 */
public final class ExplorerJsonCodec {
    public static final String CURRENT_VERSION = "1";

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
        int n = dto.n == null ? dto.sites.size() : dto.n;
        if (n < 1 || n > ExplorerState.MAX_N) {
            throw new ExplorerJsonException("n must be between 1 and " + ExplorerState.MAX_N);
        }
        if (dto.sites.size() != n) {
            throw new ExplorerJsonException("sites length must equal n");
        }
        double rotate = finiteOrDefault("rotate", dto.rotate, 0.0);

        Vector[] points = new Vector[n];
        double[] angles = new double[n];
        boolean[] enabled = new boolean[n];
        for (int k = 0; k < n; k++) {
            SiteJson site = dto.sites.get(k);
            if (site == null) {
                throw new ExplorerJsonException("sites[" + k + "] is required");
            }
            if (site.x == null || site.y == null || site.angle == null) {
                throw new ExplorerJsonException("sites[" + k + "] requires x, y, angle");
            }
            points[k] = Vector.xy(
                    requireFinite("sites[" + k + "].x", site.x),
                    requireFinite("sites[" + k + "].y", site.y)
            );
            angles[k] = requireFinite("sites[" + k + "].angle", site.angle);
            enabled[k] = site.enabled == null || site.enabled;
        }

        DiagramType diagramType = parseDiagramType(dto.diagramType);
        boolean polygonMode = booleanOrDefault(dto.polygonMode, true);
        boolean brocardIllumination = booleanOrDefault(dto.brocardIllumination, true);
        boolean showPolygonExterior = booleanOrDefault(dto.showPolygonExterior, false);
        boolean showVisibilityCells = booleanOrDefault(dto.showVisibilityCells, false);
        double stopAngle1 = finiteOrDefault("stopAngle1", dto.stopAngle1, 1.0);
        double stopAngle2 = finiteOrDefault("stopAngle2", dto.stopAngle2, 1.0);

        ExplorerSnapshot snapshot = new ExplorerSnapshot(rotate, n, points, angles, enabled);
        ExplorerViewSettings view = new ExplorerViewSettings(
                diagramType,
                polygonMode,
                brocardIllumination,
                showPolygonExterior,
                showVisibilityCells,
                stopAngle1,
                stopAngle2
        );
        return new ExplorerInstance(snapshot, view);
    }

    private static boolean booleanOrDefault(Boolean value, boolean fallback) {
        return value == null ? fallback : value;
    }

    private static double finiteOrDefault(String field, Double value, double fallback) throws ExplorerJsonException {
        if (value == null) {
            return fallback;
        }
        return requireFinite(field, value);
    }

    private static double requireFinite(String field, Double value) throws ExplorerJsonException {
        if (value == null) {
            throw new ExplorerJsonException(field + " is required");
        }
        if (!Double.isFinite(value)) {
            throw new ExplorerJsonException(field + " must be finite");
        }
        return value;
    }

    private static DiagramType parseDiagramType(String name) throws ExplorerJsonException {
        if (name == null || name.isEmpty()) {
            return DiagramType.RVD_RAYS_ORIENTED;
        }
        try {
            return DiagramType.valueOf(name);
        } catch (IllegalArgumentException e) {
            throw new ExplorerJsonException("Unknown diagramType: " + name);
        }
    }

    static final class InstanceFileV1 {
        String version;
        Double rotate;
        Integer n;
        List<SiteJson> sites;
        String diagramType;
        Boolean polygonMode;
        Boolean brocardIllumination;
        Boolean showPolygonExterior;
        Boolean showVisibilityCells;
        Double stopAngle1;
        Double stopAngle2;
    }

    static final class SiteJson {
        Double x;
        Double y;
        Double angle;
        Boolean enabled;
    }
}
