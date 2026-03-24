package rvd.render;

import javafx.scene.paint.Color;
import javafx.scene.shape.StrokeLineJoin;
import rvd.model.ExplorerState;
import xyz.marsavic.drawingfx.drawing.View;
import xyz.marsavic.geometry.Circle;
import xyz.marsavic.geometry.Figure;
import xyz.marsavic.geometry.HalfPlane;
import xyz.marsavic.geometry.Interval;
import xyz.marsavic.geometry.Line;
import xyz.marsavic.geometry.LineSegment;
import xyz.marsavic.geometry.Polygon;
import xyz.marsavic.geometry.Ray;
import xyz.marsavic.geometry.Vector;

import java.util.function.BiFunction;

public class OverlayDrawer {

    public record Context(
            ExplorerState state,
            double[] effectiveAngles,
            double[] hues,
            int selectedIndex,
            Polygon polygon,
            boolean polygonMode,
            boolean showDiagramSkeleton,
            double pixelWidth,
            double strokeWidth,
            double pointRadius
    ) {}

    public static void drawRays(View view, Context context) {
        ExplorerState state = context.state();
        double[] effectiveAngles = context.effectiveAngles();
        view.setLineWidth(context.strokeWidth() * context.pixelWidth());

        for (int k = 0; k < state.n; k++) {
            Ray ray = Ray.pd(state.points[k], Vector.polar(effectiveAngles[k] + state.rotate));
            view.setStroke(colorStrokeRay(context.hues(), k, state.enabled[k], k == context.selectedIndex()));
            view.strokeRay(ray);
        }
    }

    public static void drawBrocardPoint(View view, Vector brocardPoint, Context context) {
        if (brocardPoint == null) {
            return;
        }
        view.setFill(Color.WHITE);
        view.fillCircleCentered(brocardPoint, context.pointRadius() * context.pixelWidth());
    }

    public static void drawPolygon(View view, Context context) {
        Polygon polygon = context.polygon();
        if (context.showDiagramSkeleton() || polygon == null) {
            return;
        }
        view.setLineWidth(context.strokeWidth() * context.pixelWidth());
        view.setStroke(Color.BLACK);
        view.setLineJoin(StrokeLineJoin.ROUND);
        view.strokePolygon(polygon);
    }

    public static void drawVisibilityCells(View view, Context context) {
        Polygon polygon = context.polygon();
        if (!context.polygonMode() || polygon == null) {
            return;
        }

        view.setLineWidth(context.strokeWidth() * context.pixelWidth() / 2);
        view.setStroke(Color.gray(0, 0.5));

        for (int iq = 0; iq < context.state().n; iq++) {
            Vector q = polygon.v(iq);
            Vector pq = polygon.e(iq - 1).d();
            Vector qr = polygon.e(iq).d();
            if (pq.cross(qr) >= 0) {
                continue;
            }

            for (int io = 0; io < context.state().n; io++) {
                if (io == iq) {
                    continue;
                }
                Vector o = polygon.v(io);
                Line loq = Line.pq(o, q);
                Vector oq = loq.d();

                if (!oq.sameSide(pq, qr)) {
                    double t = polygon.intersectionTimeFirst(loq, 0.000001);
                    if (t >= 0.999999) {
                        t = polygon.intersectionTimeFirst(loq, 1.000001);
                        view.strokeLineSegment(loq.segment(Interval.pq(1, t)));
                    }
                }
            }
        }
    }

    public static void drawPoints(View view, Context context) {
        ExplorerState state = context.state();
        view.setLineWidth(context.strokeWidth() * context.pixelWidth());

        for (int k = 0; k < state.n; k++) {
            view.setFill(colorFill(context.hues(), k, state.enabled[k]));
            view.fillCircleCentered(state.points[k], context.pointRadius() * context.pixelWidth());

            view.setStroke(colorStroke(context.hues(), k, state.enabled[k], k == context.selectedIndex()));
            view.strokeCircleCentered(state.points[k], context.pointRadius() * context.pixelWidth());
        }
    }

    public static void drawCircles(
            View view,
            Context context,
            BiFunction<Integer, Integer, Figure> dominanceProvider
    ) {
        ExplorerState state = context.state();
        int kSelected = context.selectedIndex();
        view.setLineWidth(context.pixelWidth());

        if (kSelected == -1) {
            view.setStroke(Color.gray(1, 0.25));
            for (int i0 = 0; i0 < state.n; i0++) {
                if (state.enabled[i0]) {
                    for (int i1 = 0; i1 < i0; i1++) {
                        if (state.enabled[i1]) {
                            stroke(view, dominanceProvider.apply(i0, i1));
                        }
                    }
                }
            }
        } else if (state.enabled[kSelected]) {
            for (int i = 0; i < state.n; i++) {
                if (i != kSelected && state.enabled[i]) {
                    view.setStroke(colorStrokeRay(context.hues(), i, true, false));
                    stroke(view, dominanceProvider.apply(i, kSelected));
                }
            }
        }
    }

    private static void stroke(View view, Figure f) {
        if (f instanceof Circle) view.strokeCircle((Circle) f);
        if (f instanceof HalfPlane) view.strokeHalfPlane((HalfPlane) f);
    }

    private static Color colorStroke(double[] hues, int k, boolean enabled, boolean selected) {
        return Color.hsb(
                hues[k],
                selected ? 0.0 : 1.0,
                selected ? 1.0 : 0.0,
                enabled ? 1.0 : 0.4
        );
    }

    private static Color colorStrokeRay(double[] hues, int k, boolean enabled, boolean selected) {
        return Color.hsb(
                hues[k],
                selected ? 0.2 : 0.8,
                selected ? 1.0 : 0.6,
                enabled ? 1.0 : 0.4
        );
    }

    private static Color colorFill(double[] hues, int k, boolean enabled) {
        return Color.hsb(
                hues[k],
                0.7,
                1.0,
                enabled ? 1.0 : 0.0
        );
    }
}
