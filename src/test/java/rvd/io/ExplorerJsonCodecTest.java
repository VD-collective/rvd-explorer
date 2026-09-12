package rvd.io;

import org.junit.jupiter.api.Test;
import rvd.DiagramType;
import rvd.model.ExplorerInstance;
import rvd.model.ExplorerSnapshot;
import rvd.model.ExplorerViewSettings;
import xyz.marsavic.geometry.Vector;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExplorerJsonCodecTest {

    @Test
    void encodeDecodeRoundTripPreservesSitesAndViewSettings() throws Exception {
        ExplorerInstance source = sampleInstance();
        String json = ExplorerJsonCodec.encode(source);
        ExplorerInstance restored = ExplorerJsonCodec.decode(json);

        assertEquals(source.snapshot().rotate(), restored.snapshot().rotate(), 1e-12);
        assertEquals(source.snapshot().n(), restored.snapshot().n());
        for (int k = 0; k < source.snapshot().n(); k++) {
            assertEquals(source.snapshot().points()[k].x(), restored.snapshot().points()[k].x(), 1e-12);
            assertEquals(source.snapshot().points()[k].y(), restored.snapshot().points()[k].y(), 1e-12);
            assertEquals(source.snapshot().angles()[k], restored.snapshot().angles()[k], 1e-12);
            assertEquals(source.snapshot().enabled()[k], restored.snapshot().enabled()[k]);
        }

        ExplorerViewSettings expected = source.view();
        ExplorerViewSettings actual = restored.view();
        assertEquals(expected.diagramType(), actual.diagramType());
        assertEquals(expected.polygonMode(), actual.polygonMode());
        assertEquals(expected.brocardIllumination(), actual.brocardIllumination());
        assertEquals(expected.showPolygonExterior(), actual.showPolygonExterior());
        assertEquals(expected.showVisibilityCells(), actual.showVisibilityCells());
        assertEquals(expected.stopAngle1(), actual.stopAngle1(), 1e-12);
        assertEquals(expected.stopAngle2(), actual.stopAngle2(), 1e-12);

        assertFalse(json.contains("showDiagram"));
        assertFalse(json.contains("colorBackground"));
        assertFalse(json.contains("visibilityCellsShadingCount"));
    }

    @Test
    void fileIoRoundTrip() throws Exception {
        Path path = Files.createTempFile("rvd-instance", ".json");
        try {
            ExplorerInstance source = sampleInstance();
            ExplorerFileIo.save(path, source);
            ExplorerInstance restored = ExplorerFileIo.load(path);
            assertEquals(source.snapshot().n(), restored.snapshot().n());
            assertEquals(source.view().diagramType(), restored.view().diagramType());
            assertTrue(Files.readString(path).contains("\"version\": \"1\""));
        } finally {
            Files.deleteIfExists(path);
        }
    }

    @Test
    void rejectsWrongVersion() {
        String json = """
                {
                  "version": "99",
                  "rotate": 0.0,
                  "n": 1,
                  "sites": [{ "x": 0, "y": 0, "angle": 0, "enabled": true }],
                  "diagramType": "RVD_RAYS_ORIENTED"
                }
                """;
        ExplorerJsonException ex = assertThrows(ExplorerJsonException.class, () -> ExplorerJsonCodec.decode(json));
        assertTrue(ex.getMessage().contains("version"));
    }

    @Test
    void rejectsMissingSites() {
        String json = """
                {
                  "version": "1",
                  "rotate": 0.0,
                  "n": 1,
                  "diagramType": "RVD_RAYS_ORIENTED"
                }
                """;
        ExplorerJsonException ex = assertThrows(ExplorerJsonException.class, () -> ExplorerJsonCodec.decode(json));
        assertTrue(ex.getMessage().contains("sites"));
    }

    @Test
    void rejectsSitesLengthMismatch() {
        String json = """
                {
                  "version": "1",
                  "rotate": 0.0,
                  "n": 2,
                  "sites": [{ "x": 0, "y": 0, "angle": 0, "enabled": true }],
                  "diagramType": "RVD_RAYS_ORIENTED"
                }
                """;
        ExplorerJsonException ex = assertThrows(ExplorerJsonException.class, () -> ExplorerJsonCodec.decode(json));
        assertTrue(ex.getMessage().contains("sites length"));
    }

    @Test
    void rejectsInvalidN() {
        String json = """
                {
                  "version": "1",
                  "rotate": 0.0,
                  "n": 0,
                  "sites": [],
                  "diagramType": "RVD_RAYS_ORIENTED"
                }
                """;
        ExplorerJsonException ex = assertThrows(ExplorerJsonException.class, () -> ExplorerJsonCodec.decode(json));
        assertTrue(ex.getMessage().contains("n must be"));
    }

    @Test
    void rejectsUnknownDiagramType() {
        String json = """
                {
                  "version": "1",
                  "rotate": 0.0,
                  "n": 1,
                  "sites": [{ "x": 0, "y": 0, "angle": 0, "enabled": true }],
                  "diagramType": "NOT_A_DIAGRAM"
                }
                """;
        ExplorerJsonException ex = assertThrows(ExplorerJsonException.class, () -> ExplorerJsonCodec.decode(json));
        assertTrue(ex.getMessage().contains("diagramType"));
    }

    private static ExplorerInstance sampleInstance() {
        Vector[] points = { Vector.xy(10, -20), Vector.xy(3, 4) };
        double[] angles = { 0.25, 0.75 };
        boolean[] enabled = { true, false };
        ExplorerSnapshot snapshot = new ExplorerSnapshot(0.125, 2, points, angles, enabled);
        ExplorerViewSettings view = new ExplorerViewSettings(
                DiagramType.RVD_LINES,
                false,
                true,
                false,
                true,
                0.8,
                0.6
        );
        assertFalse(enabled[1]);
        return new ExplorerInstance(snapshot, view);
    }
}
