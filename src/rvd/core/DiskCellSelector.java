package rvd.core;

import xyz.marsavic.geometry.Figure;
import xyz.marsavic.geometry.Vector;

public class DiskCellSelector {

    public int select(Vector p, Figure[][] dominances, boolean[] enabled, int n) {
        int k = 0;
        while (!enabled[k]) {
            k++;
        }
        int i = k + 1;

        while ((k < n) && (i < k + n)) {
            int j = i % n;
            if (enabled[j] && dominances[j][k].contains(p)) {
                k = i;
            }
            i++;
        }

        return k < n ? k : -1;
    }
}
