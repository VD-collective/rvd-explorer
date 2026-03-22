package rvd.core;

import xyz.marsavic.geometry.Figure;
import xyz.marsavic.geometry.Polygon;
import xyz.marsavic.geometry.Ray;
import xyz.marsavic.geometry.Vector;

public class DiagramPreparation {

    @FunctionalInterface
    public interface DominanceProvider {
        Figure create(int i0, int i1, double[] effectiveAngles);
    }

    public record PreparedData(Polygon polygon, Ray[] rays, Figure[][] dominanceRegion) {}

    public static PreparedData prepare(
            Vector[] points,
            double[] effectiveAngles,
            int n,
            double rotate,
            boolean polygonMode,
            DominanceProvider dominanceProvider
    ) {
        Polygon polygon = polygonMode ? Polygon.of(points, n) : null;

        Ray[] rays = new Ray[n];
        for (int i = 0; i < n; i++) {
            rays[i] = Ray.pa(points[i], effectiveAngles[i] + rotate);
        }

        Figure[][] dominanceRegion = new Figure[n][n];
        for (int i0 = 0; i0 < n; i0++) {
            for (int i1 = 0; i1 < n; i1++) {
                dominanceRegion[i0][i1] = dominanceProvider.create(i0, i1, effectiveAngles);
            }
        }

        return new PreparedData(polygon, rays, dominanceRegion);
    }
}
