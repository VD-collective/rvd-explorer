package rvd.core;

import xyz.marsavic.geometry.Ray;
import xyz.marsavic.geometry.Vector;

public class NearestCellClassifier {

    public record Result(int index, int visibleCount, double angle) {}

    public static Result classify(
            Vector p,
            Ray[] rays,
            boolean[] enabled,
            int[] visibleVertices,
            boolean includeInverseRay,
            boolean includeMirroredRay,
            double stopAngleThreshold
    ) {
        RayNearestSelector.Result nearest = RayNearestSelector.select(
                p,
                rays,
                enabled,
                visibleVertices,
                includeInverseRay,
                includeMirroredRay
        );

        int bestK = nearest.bestIndex();
        double angle = nearest.angle();
        if (bestK >= 0 && angle > stopAngleThreshold) {
            bestK = -2;
        }

        return new Result(bestK, visibleVertices.length, angle);
    }
}
