package rvd.model;

import xyz.marsavic.geometry.Vector;

public record ExplorerSnapshot(
        double rotate,
        int n,
        Vector[] points,
        double[] angles,
        boolean[] enabled
) {
    public ExplorerSnapshot {
        points = points.clone();
        angles = angles.clone();
        enabled = enabled.clone();
    }

    @Override
    public Vector[] points() {
        return points.clone();
    }

    @Override
    public double[] angles() {
        return angles.clone();
    }

    @Override
    public boolean[] enabled() {
        return enabled.clone();
    }
}
