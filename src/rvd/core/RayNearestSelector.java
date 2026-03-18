package rvd.core;

import xyz.marsavic.geometry.Ray;
import xyz.marsavic.geometry.Vector;
import xyz.marsavic.utils.performance.ApproximateNumeric;

public class RayNearestSelector {

    public record Result(int bestIndex, double angle) {}

    private static final Vector ALMOST_FULL_TURN = Vector.xy(Double.MAX_VALUE, -Double.MIN_VALUE);

    public Result select(
            Vector p,
            Ray[] rays,
            boolean[] enabled,
            int[] visibleVertices,
            boolean includeInverseRay,
            boolean includeMirroredRay
    ) {
        int bestK = -3;
        Vector bestR = ALMOST_FULL_TURN;

        for (int k : visibleVertices) {
            if (enabled[k]) {
                Vector o = p.sub(rays[k].p());
                Vector e = rays[k].d();
                Vector r = Vector.xy(e.dot(o), e.cross(o));

                if (r.angleBefore(bestR)) {
                    bestR = r;
                    bestK = k;
                }
                if (includeInverseRay) {
                    r = r.inverse();
                    if (r.angleBefore(bestR)) {
                        bestR = r;
                        bestK = k;
                    }
                }
                if (includeMirroredRay) {
                    Vector r2 = Vector.xy(r.x(), -r.y());
                    if (r2.angleBefore(bestR)) {
                        bestR = r2;
                        bestK = k;
                    }
                }
            }
        }

        return new Result(bestK, ApproximateNumeric.angle(bestR));
    }
}
