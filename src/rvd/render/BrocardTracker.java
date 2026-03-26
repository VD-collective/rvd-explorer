package rvd.render;

import xyz.marsavic.geometry.Vector;

public class BrocardTracker {

    private Vector point;
    private double angle;

    public void reset() {
        angle = 0.0;
        point = null;
    }

    public void observe(int index, double candidateAngle, Vector candidatePoint) {
        // i = -1    In the domain, but on the skeleton
        // i = -2    In the domain, but inside the aperture
        // i = -3    Out of the domain, but on the skeleton
        // i = -4    Out of the domain
        if (index >= -2 && candidateAngle > angle) {
            angle = candidateAngle;
            point = candidatePoint;
        }
    }

    public Vector point() {
        return point;
    }

    public double angle() {
        return angle;
    }
}
