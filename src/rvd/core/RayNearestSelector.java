package rvd.core;

import xyz.marsavic.geometry.Ray;
import xyz.marsavic.geometry.Vector;
import xyz.marsavic.utils.performance.ApproximateNumeric;

public class RayNearestSelector {

    public record Result(int bestIndex, double angle) {}

    private static final Vector ALMOST_FULL_TURN = Vector.xy(Double.MAX_VALUE, -Double.MIN_VALUE);

    public static Result select(
            Vector p,
            Ray[] rays,
            boolean[] enabled,
            int[] visibleVertices,
            boolean line,
            boolean unoriented
    ) {
        int bestK = -4;
        Vector bestR = ALMOST_FULL_TURN;

        for (int k : visibleVertices) {
            if (enabled[k]) {
                Vector o = p.sub(rays[k].p());
                Vector e = rays[k].d();
                Vector r = Vector.xy(e.dot(o), e.cross(o));

                if (r.y() < 0 && -1e-9 < r.y()) {
                    r = Vector.xy(r.x(), 0);
                }

                if (r.angleBefore(bestR)) {
                    bestR = r;
                    bestK = k;
                }
                if (line) {
                    r = r.inverse();
                    if (r.angleBefore(bestR)) {
                        bestR = r;
                        bestK = k;
                    }
                }
                if (unoriented) {
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
