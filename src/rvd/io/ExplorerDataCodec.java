package rvd.io;

import rvd.model.ExplorerSnapshot;
import xyz.marsavic.geometry.Vector;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Base64;

public class ExplorerDataCodec {

    public String encode(ExplorerSnapshot snapshot) {
        try (
                ByteArrayOutputStream outB = new ByteArrayOutputStream();
                ObjectOutputStream out = new ObjectOutputStream(outB)
        ) {
            out.writeDouble(snapshot.rotate());
            out.writeInt(snapshot.n());

            Vector[] points = snapshot.points();
            double[] angles = snapshot.angles();
            boolean[] enabled = snapshot.enabled();

            for (int k = 0; k < snapshot.n(); k++) {
                out.writeDouble(points[k].x());
                out.writeDouble(points[k].y());
                out.writeDouble(angles[k]);
                out.writeBoolean(enabled[k]);
            }
            out.flush();

            return Base64.getEncoder().encodeToString(outB.toByteArray());
        } catch (Exception e) {
            return null;
        }
    }

    public ExplorerSnapshot decode(String data) {
        try (
                ByteArrayInputStream inB = new ByteArrayInputStream(Base64.getDecoder().decode(data));
                ObjectInputStream in = new ObjectInputStream(inB)
        ) {
            double rotate = in.readDouble();
            int n = in.readInt();
            Vector[] points = new Vector[n];
            double[] angles = new double[n];
            boolean[] enabled = new boolean[n];

            for (int k = 0; k < n; k++) {
                double x = in.readDouble();
                double y = in.readDouble();
                points[k] = Vector.xy(x, y);
                angles[k] = in.readDouble();
                enabled[k] = in.readBoolean();
            }

            return new ExplorerSnapshot(rotate, n, points, angles, enabled);
        } catch (Exception e) {
            return null;
        }
    }
}
