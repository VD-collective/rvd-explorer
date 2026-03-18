package rvd.core;

import xyz.marsavic.geometry.Figure;
import xyz.marsavic.geometry.Polygon;
import xyz.marsavic.geometry.Ray;
import xyz.marsavic.geometry.Vector;

import java.util.function.BiFunction;

public class DiagramPreparation {

    public record PreparedData(Polygon polygon, Ray[] rays, Figure[][] dominanceRegion) {}

    public PreparedData prepare(
            Vector[] points,
            double[] angles,
            int n,
            double rotate,
            boolean polygonMode,
            BiFunction<Integer, Integer, Figure> dominanceProvider
    ) {
        if (polygonMode) {
            for (int i = 0; i < n; i++) {
                angles[i] = points[(i + 1) % n].sub(points[i]).angle();
            }
        }

        Polygon polygon = polygonMode ? Polygon.of(points, n) : null;

        Ray[] rays = new Ray[n];
        for (int i = 0; i < n; i++) {
            rays[i] = Ray.pa(points[i], angles[i] + rotate);
        }

        Figure[][] dominanceRegion = new Figure[n][n];
        for (int i0 = 0; i0 < n; i0++) {
            for (int i1 = 0; i1 < n; i1++) {
                dominanceRegion[i0][i1] = dominanceProvider.apply(i0, i1);
            }
        }

        return new PreparedData(polygon, rays, dominanceRegion);
    }
}
