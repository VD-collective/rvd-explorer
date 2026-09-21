package rvd.model;

import xyz.marsavic.drawingfx.gadgets.annotations.GadgetAnimation;
import xyz.marsavic.drawingfx.gadgets.annotations.GadgetInteger;
import xyz.marsavic.drawingfx.gadgets.annotations.Properties;
import xyz.marsavic.geometry.Vector;

public class ExplorerState {

    public static final int MAX_N = 64;

    @GadgetInteger(min = 1, max = MAX_N)
    @Properties(name = "Number of sites")
    public int n = 7;

    @GadgetAnimation(p = 0, q = 1, loop = true, speed = 0.05, start = false)
    @Properties(name = "Rotate rays")
    public double rotate = 0.0;

    public final Vector[] points;
    public final double[] angles;
    public final boolean[] enabled;

    public ExplorerState(int maxN) {
        points = new Vector[maxN];
        angles = new double[maxN];
        enabled = new boolean[maxN];
    }

    public ExplorerSnapshot snapshot() {
        Vector[] snapshotPoints = new Vector[n];
        double[] snapshotAngles = new double[n];
        boolean[] snapshotEnabled = new boolean[n];

        for (int k = 0; k < n; k++) {
            snapshotPoints[k] = points[k];
            snapshotAngles[k] = angles[k];
            snapshotEnabled[k] = enabled[k];
        }

        return new ExplorerSnapshot(rotate, n, snapshotPoints, snapshotAngles, snapshotEnabled);
    }

    public void applySnapshot(ExplorerSnapshot snapshot) {
        rotate = snapshot.rotate();
        n = snapshot.n();

        Vector[] snapshotPoints = snapshot.points();
        double[] snapshotAngles = snapshot.angles();
        boolean[] snapshotEnabled = snapshot.enabled();

        for (int k = 0; k < n; k++) {
            points[k] = snapshotPoints[k];
            angles[k] = snapshotAngles[k];
            enabled[k] = snapshotEnabled[k];
        }
    }
}
