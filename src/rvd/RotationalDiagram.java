package rvd;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Base64;
import java.util.random.RandomGenerator;
import java.util.stream.IntStream;

import javafx.scene.image.Image;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import xyz.marsavic.drawingfx.application.DrawingApplication;
import xyz.marsavic.drawingfx.application.Options;
import xyz.marsavic.drawingfx.drawing.Drawing;
import xyz.marsavic.drawingfx.drawing.DrawingUtils;
import xyz.marsavic.drawingfx.drawing.View;
import xyz.marsavic.drawingfx.gadgets.annotations.GadgetBoolean;
import xyz.marsavic.drawingfx.gadgets.annotations.GadgetDouble;
import xyz.marsavic.drawingfx.gadgets.annotations.GadgetInteger;
import xyz.marsavic.drawingfx.gadgets.annotations.GadgetString;
import xyz.marsavic.geometry.Box;
import xyz.marsavic.geometry.Vector;
import xyz.marsavic.input.InputEvent;
import xyz.marsavic.input.InputState;
import xyz.marsavic.random.sampling.Sampler;
import xyz.marsavic.utils.Hash;
import xyz.marsavic.utils.Numeric;


public class RotationalDiagram implements Drawing {
	public static final Vector sizeInitial = Vector.xy(600, 600);
	final int maxN = 20;

	Vector gridCellD = Vector.xy(10, 10);
	
	@GadgetString
	String data = "";

	@GadgetBoolean
	boolean showHelp = false;
	
	@GadgetBoolean
	boolean snapToGrid = false;

	@GadgetBoolean
	boolean showDiagram = true;

	@GadgetBoolean
	boolean shading = true;
	
	@GadgetDouble(p = 0, q = 1)
	double offset = 0.0;
	
	@GadgetInteger(min = 1, max = maxN)
	int n = 3;
	
	
	
	Vector[] points = new Vector[maxN];
	double[] angles = new double[maxN];
	boolean[] active = new boolean[maxN];

	
	int kSelected = -1;

	Sampler sampler = new Sampler(new Hash(0xC68D25EBD134C8EDL));
	RandomGenerator rng = new Hash(0xF263ED177AFEF8A2L).rng();

	
	
	String dataLast = "";

	
	private void writeData() {
		try (
			ByteArrayOutputStream outB = new ByteArrayOutputStream();
			ObjectOutputStream out = new ObjectOutputStream(outB);
		) {
			out.writeDouble(offset);
			out.writeInt(n);
			for (int k = 0; k < n; k++) {
				out.writeDouble(points[k].x());
				out.writeDouble(points[k].y());
				out.writeDouble(angles[k]);
				out.writeBoolean(active[k]);
			}
			out.flush();
	
			dataLast = data;
			data = Base64.getEncoder().encodeToString(outB.toByteArray());
		} catch (Exception e) {
			// I should really do nothing here.
		}
	}
	
	
	private void readData() {
		if (data.equals(dataLast)) {
			return;
		}
		
		try (
			ByteArrayInputStream inB = new ByteArrayInputStream(Base64.getDecoder().decode(data));
			ObjectInputStream in = new ObjectInputStream(inB);
		) {
			offset = in.readDouble();
			n      = in.readInt();
			for (int k = 0; k < n; k++) {
				double x = in.readDouble();
				double y = in.readDouble();
				points[k] = Vector.xy(x, y);
				angles[k] = in.readDouble();
				active[k] = in.readBoolean();
			}
		} catch (Exception e) {
			// I should really do nothing here.
		}		
	}
	
	
	{
		Box box = Box.cr(Vector.ZERO, sizeInitial.div(2));
		for (int k = 0; k < maxN; k++) {
			points[k] = sampler.randomInBox(box);
			angles[k] = rng.nextDouble();
			active[k] = true;
		}
	}
	
	
	private double hue(int k) {
		return 360 * k * Numeric.PHI;
	}
	
	
	private Image diagram(Vector size) {
		int sizeX = (int) size.x();
		int sizeY = (int) size.y();

		WritableImage image = new WritableImage(sizeX, sizeY);
		PixelWriter pw = image.getPixelWriter();

		byte[] matrix = new byte[sizeX * sizeY * 3];
		
		IntStream.range(0, sizeY).parallel().forEach((i) -> {
			for (int j = 0; j < sizeX; j++) {
				int bestK = -1;
				double bestT = Double.POSITIVE_INFINITY;
				
				Vector p = Vector.xy(j, i).sub(size.div(2)).inversedY();
				
				for (int k = 0; k < n; k++) {
					if (active[k]) {
						Vector r = p.sub(points[k]);
						double t = Numeric.mod(r.angle() - angles[k] - offset, 1);
						if (t < bestT) {
							bestT = t;
							bestK = k;
						}
					}
				}
				
				double z = bestT;
				double b = shading ? 0.9 - 0.6*z : 0.9;
				
				Color c = Color.hsb(hue(bestK), 0.6, b);
				int o = (i * sizeX + j) * 3;
				matrix[o+0] = (byte) (0.5 + c.getRed  () * 255);
				matrix[o+1] = (byte) (0.5 + c.getGreen() * 255);
				matrix[o+2] = (byte) (0.5 + c.getBlue () * 255);
			}
		});
		
		pw.setPixels(0, 0, sizeX, sizeY, PixelFormat.getByteRgbInstance(), matrix, 0, 3 * sizeX);
		
		return image;
	}
	

	private void drawRays(View view, Vector size) {
		view.setLineWidth(1);
		
		for (int k = 0; k < n; k++) {
			view.setStroke(Color.gray(k == kSelected ? 1 : 0, active[k] ? 1 : 0.2));
			view.strokeLineSegment(points[k], points[k].add(Vector.polar(2*size.length(), angles[k] + offset)));
		}

		for (int k = 0; k < n; k++) {
			view.setFill(Color.hsb(hue(k), 0.8, 1));
			view.fillCircleCentered(points[k], 4);

			view.setStroke(Color.gray(k == kSelected ? 1 : 0, active[k] ? 1 : 0.2));
			view.strokeCircleCentered(points[k], 4);
		}
	}
	

	private void showHelp(View view) {
		DrawingUtils.drawInfoText(view,
				"Gadgets:",
				"    data        - The encoding of the configuration (save/load = copy/paste).",
				"    showDiagram - Hide the diagram to work with the configuration faster.",
				"    shading     - Points seen later by the rays are darker.",
				"    offset      - Rotate all rays by this angle.",
				"    n           - The number of rays.",
				"",
				"Controls:",
				"    Left click       - Select ray.", 
				"    Right click drag - Set the angle of the selected ray.", 
				"    Mouse wheel      - Toggle if the selected ray is active.",
				"    F8               - Toggle grid.",
				"",
				"App author:",
				"    Marko Savić (marsavic@gmail.com)"				
		);
	}
	

	@Override
	public void draw(View view) {
		readData();
		
		DrawingUtils.clear(view, Color.gray(0.2));
		
		Vector size = view.nativeBox().d();
		
		if (showDiagram) {
			view.drawImageCentered(Vector.ZERO, diagram(size));
		}

		drawRays(view, size);

		// OSD
		
		if (showHelp) {
			showHelp(view);
		}
		
		writeData();
	}


	private int nearestK(Vector p, double rLimit) {
		int bestK = -1;
		double bestD = Double.POSITIVE_INFINITY;
		
		for (int k = 0; k < n; k++) {
			double d = p.distanceTo(points[k]);
			if (d < bestD && d < rLimit) {
				bestK = k;
				bestD = d;
			}
		}
		
		return bestK;
	}
	
	
	// Input handling
	
	
	boolean dragging = false;
	Vector draggingStartPoint;
	double mouseReach = 8;
	double draggingMinDistance = 1;

	
	@Override
	public void receiveEvent(View view, InputEvent event, InputState state, Vector pointerWorld, Vector pointerViewBase) {
		Vector p = snapToGrid ? pointerWorld.round(gridCellD) : pointerWorld;

		if (!dragging) {
			int k = nearestK(pointerWorld, mouseReach);
			if (k >= 0) {
				if (event.isMouseButtonPress(1)) {
					kSelected = k;
					draggingStartPoint = pointerWorld;
				}
			} else {
				if (event.isMouseButtonPress(1)) {
					kSelected = -1;
				}
			}
		}

		if (state.mouseButtonPressed(1)) {
			if (draggingStartPoint != null && pointerWorld.distanceTo(draggingStartPoint) > draggingMinDistance) {
				dragging = true;
			}
		} else {
			dragging = false;
		}
		
		if (dragging && kSelected >= 0) {
			points[kSelected] = p;
		}
		
		if (state.mouseButtonPressed(3) && kSelected >= 0) {
			Vector d = p.sub(points[kSelected]);
			angles[kSelected] = d.angle() - offset;
		}
		
		if (event.isMouseWheel() && kSelected >= 0) {
			active[kSelected] ^= true;
		}
	}
	

	public static void main(String[] args) {
//		Options options = new Options();
		Options options = Options.redrawOnEvents();
		options.drawingSize = RotationalDiagram.sizeInitial;
		DrawingApplication.launch(options);
	}

}
