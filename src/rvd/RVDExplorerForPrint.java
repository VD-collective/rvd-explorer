package rvd;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.IntBuffer;
import java.util.Base64;
import java.util.stream.IntStream;

import javafx.scene.image.Image;
import javafx.scene.image.PixelBuffer;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import xyz.marsavic.drawingfx.application.DrawingApplication;
import xyz.marsavic.drawingfx.application.Options;
import xyz.marsavic.drawingfx.drawing.Drawing;
import xyz.marsavic.drawingfx.drawing.DrawingUtils;
import xyz.marsavic.drawingfx.drawing.View;
import xyz.marsavic.drawingfx.gadgets.annotations.GadgetAnimation;
import xyz.marsavic.drawingfx.gadgets.annotations.GadgetBoolean;
import xyz.marsavic.drawingfx.gadgets.annotations.GadgetInteger;
import xyz.marsavic.drawingfx.gadgets.annotations.GadgetString;
import xyz.marsavic.drawingfx.gadgets.annotations.Properties;
import xyz.marsavic.drawingfx.utils.camera.CameraSimple;
import xyz.marsavic.functions.F_R_R;
import xyz.marsavic.geometry.Box;
import xyz.marsavic.geometry.Circle;
import xyz.marsavic.geometry.Figure;
import xyz.marsavic.geometry.HalfPlane;
import xyz.marsavic.geometry.Ray;
import xyz.marsavic.geometry.Transformation;
import xyz.marsavic.geometry.Vector;
import xyz.marsavic.input.InputEvent;
import xyz.marsavic.input.InputState;
import xyz.marsavic.input.KeyCode;
import xyz.marsavic.random.sampling.Sampler;
import xyz.marsavic.utils.Hash;
import xyz.marsavic.utils.Numeric;
import xyz.marsavic.utils.performance.ApproximateNumeric;


public class RVDExplorerForPrint implements Drawing {
	public static final double scale = 1.0;
	
	//	public static final Vector sizeInitial = Vector.xy(1200, 720).mul(scale);
	public static final Vector sizeInitial = Vector.xy(720 , 720).mul(scale);

//	public static final Vector sizeInitial = Vector.xy(800, 480).mul(scale);
//	public static final Vector sizeInitial = Vector.xy(480, 480).mul(scale);
	
	public static final Vector gridCellD = Vector.xy(10, 10);
	
	private final int maxN = 64;
	
	@GadgetString
	String dataString = "";
	
	@GadgetBoolean
	boolean showHelp = false;
	
	boolean snapToGrid = false;
	boolean showDiagram = true;
	boolean showPoints = true;
	boolean showRays = true;
	boolean showColor = true;
	boolean showCircles = false;
	boolean shading = false;
	boolean polygon = false;
	boolean leftSideOnly = false;
	
	DiagramType diagram = DiagramType.RVD_RAYS_ORIENTED;
	
	
	@GadgetAnimation(p = 0, q = 1, loop = true, speed = 0.05, start = false)
	double rotate = 0.0;
	
	@Properties(name = "Number of sites")
	@GadgetInteger(min = 1, max = maxN)
	int n = 3;
	
	Vector[] points = new Vector[maxN];
	double[] angles = new double[maxN];
	boolean[] enabled = new boolean[maxN];
	int kSelected = -1;
	
	
	CameraSimple camera = new CameraSimple(F_R_R.cutoff01(t -> F_R_R.power(t, 8)));
	double pixelWidth;
	
	
	
	private String dataAsString() {
		try (
				ByteArrayOutputStream outB = new ByteArrayOutputStream();
				ObjectOutputStream out = new ObjectOutputStream(outB);
		) {
			out.writeDouble(rotate);
			out.writeInt(n);
			for (int k = 0; k < n; k++) {
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
	
	
	private void stringToData(String data) {
		try (
				ByteArrayInputStream inB = new ByteArrayInputStream(Base64.getDecoder().decode(data));
				ObjectInputStream in = new ObjectInputStream(inB);
		) {
			rotate = in.readDouble();
			n      = in.readInt();
			for (int k = 0; k < n; k++) {
				double x = in.readDouble();
				double y = in.readDouble();
				points[k] = Vector.xy(x, y);
				angles[k] = in.readDouble();
				enabled[k] = in.readBoolean();
			}
		} catch (Exception e) {
			// I should really do nothing here.
		}
	}
	
	
	{
		Sampler sampler = new Sampler(new Hash(0x5C727CC650E510C7L));
		
		Box box = Box.cr(sizeInitial.div(2));
		for (int k = 0; k < maxN; k++) {
			points[k] = sampler.randomInBox(box.scaleFromCenter(2.0/3));
//			points[k] = sampler.randomGaussian(box.r().min() / 2);
			angles[k] = sampler.uniform();
			enabled[k] = true;
		}
	}
	
	
	private double strokeWidth = 3 * scale;
	private double rPoint = 8 * scale;
	
	private double hue(int k) {
		return 360 * k * Numeric.PHI;
	}
	
	private Color colorStroke(int k, boolean enabled, boolean selected) {
		return Color.hsb(
				hue(k),
				selected ? 0.2 : 0.8,
				selected ? 1.0 : 0.0,
				enabled ? 1.0 : 0.2
		);
	}
	
	private Color colorFill(int k) {
		return Color.hsb(hue(k), 0.7, 1);
	}
	
	
	RVDColor[] colorsDiagram = new RVDColor[maxN];
	{
		for (int k = 0; k < maxN; k++) {
			colorsDiagram[k]= new RVDColor(Color.hsb(hue(k), 0.3, 1.0));
		}
	}
	
	
	private RVDColor colorDiagram(IndexAngle ia) {
		if (ia.i == -1) return RVDColor.WHITE;
		double b = shading ? 0.9 - 0.6 * ia.a : 0.9;
		return showColor ? colorsDiagram[ia.i].mul(b) : new RVDColor(b);
	}
	
	
	private static class IndexAngle {
		int i;
		double a;
		
		public IndexAngle(int i, double a) {
			this.i = i;
			this.a = a;
		}
	}
	
	//	final Vector almostFullTurn = Vector.polar(Math.nextDown(1.0));
	final Vector almostFullTurn = Vector.xy(Double.MAX_VALUE, -Double.MIN_VALUE);
	
	private IndexAngle findNearest(Vector p, Ray[] rays) {
		int bestK = -1;
		Vector bestR = almostFullTurn;
		
		for (int k = 0; k < n; k++) {
			if (enabled[k]) {
				Vector o = p.sub(rays[k].p());
				Vector e = rays[k].d();
				Vector r = Vector.xy(e.dot(o), e.cross(o));
				
				if (leftSideOnly && (r.y() < 0)) {
					bestK = -1;
					break;
				}
				
				if (r.angleBefore(bestR)) {
					bestR = r;
					bestK = k;
				}
				if (diagram == DiagramType.RVD_LINES) {
					r = r.inverse();
					if (r.angleBefore(bestR)) {
						bestR = r;
						bestK = k;
					}
				}
				if (diagram == DiagramType.RVD_RAYS_UNORIENTED) {
					Vector r2 = Vector.xy(r.x(), -r.y());
					if (r2.angleBefore(bestR)) {
						bestR = r2;
						bestK = k;
					}
				}
			}
		}
		
		return new IndexAngle(bestK, ApproximateNumeric.angle(bestR));
	}
	
	
	private IndexAngle findDDCell(Vector p, Figure[][] dominances) {
		int k = 0;
		while (!enabled[k]) {
			k++;
		}
		int i = k + 1;
		
		while ((k < n) && (i < k + n)) {
			final int j = i%n;
			if (enabled[j] && dominances[j][k].contains(p)) {
				k = i;
			}
			i++;
		}
		
		return new IndexAngle(k < n ? k : -1, 0);
//		return new IndexAngle(dominances[0][1].contains(p) ? 0 : -1, 0);
	}
	
	
	Figure dominanceFor(int i0, int i1) {
		Vector p0 = points[i0];
		Vector p1 = points[i1];
		double phi = angles[i1] - angles[i0];
		
		if (Numeric.mod(phi, 0.5) != 0) {
			Vector d = p1.sub(p0).div(2);
			Vector c = p0.add(d.asBase(1, Numeric.tanT(0.25 - phi)));
			double r = Math.copySign(c.to(p0).length(), 0.5 - Numeric.mod(phi, 1));
			return Circle.cr(c, r);
		} else {
			return phi == 0 ? HalfPlane.pq(p0, p1) : HalfPlane.pq(p1, p0);
		}
	}


//	Figure dominanceFor(int i0, int i1) {
//		Vector p0 = points[i0];
//		Vector p1 = points[i1];
//		double phi = angles[i1] - angles[i0];
//
////		if (Numeric.mod(phi, 1) < 0.5) {
//			Figure c = dominanceDiskFor(i0, i1);
//			Figure r0 = HalfPlane.pq(Vector.ZERO, p0);
//			Figure r1 = HalfPlane.pq(p1, Vector.ZERO);
//			Figure l = HalfPlane.pq	(p0, p1);
//
//			return new SunRegion(c, r0, r1, l);
//
//	}
	
	
	private static final int nBits = 3;
	private static final int nValues = 1 << nBits;
	
	int[] pixels;
	int sizeYp = 0, sizeXp = 0;
	byte[][] bestI;
	float[][] bestA;
	
	/**
	 * @param tFromPixels  A transformation from the pixel space to the working space
	 * @param bImage  An integer box in the pixel space used to make the resulting image
	 * @return
	 */
	private Image makeImage(Transformation tFromPixels, Box bImage) {
		Vector diag = bImage.d().abs();
		int sizeX = diag.xInt();
		int sizeY = diag.yInt();
		
		if (sizeX == 0 || sizeY == 0) {
			return null;
		}
		
		if (polygon) {
			for (int i = 0; i < n; i++) {
				angles[i] = points[(i+1)%n].sub(points[i]).angle();
			}
		}
		
		Ray[] rays = new Ray[n];
		for (int i = 0; i < n; i++) {
			rays[i] = Ray.pa(points[i], angles[i] + rotate);
		}
		
		Figure[][] dominanceRegion = new Figure[n][n];
		for (int i0 = 0; i0 < n; i0++) {
			for (int i1 = 0; i1 < n; i1++) {
				dominanceRegion[i0][i1] = dominanceFor(i0, i1);
			}
		}
		
		
		if ((sizeYp < sizeY) || (sizeXp < sizeX)) {
			sizeYp = sizeY;
			sizeXp = sizeX;
			bestI = new byte[sizeY][sizeX];
			bestA = new float[sizeY][sizeX];
			pixels = new int[sizeY * sizeX];
		}
		
		
		// Finding nearest
		
		IntStream.range(0, sizeY).parallel().forEach(y -> {
			for (int x = 0; x < sizeX; x++) {
				Vector cPixel = Vector.xy(x + 0.5, y + 0.5);
				Vector p = tFromPixels.applyTo(cPixel);
				IndexAngle rp = (diagram == DiagramType.DISK_DIAGRAM) ? findDDCell(p, dominanceRegion) : findNearest(p, rays);
				bestI[y][x] = (byte) rp.i;
				bestA[y][x] = (float) rp.a;
			}
		});
		
		
		// Antialiasing
		
		IntStream.range(0, sizeY).parallel().forEach(y -> {
			for (int x = 0; x < sizeX; x++) {
				int bI = bestI[y][x];
				
				boolean shouldAntialias = false;
				if ((x > 0      ) && (bI != bestI[y][x-1])) shouldAntialias = true;
				if ((x < sizeX-1) && (bI != bestI[y][x+1])) shouldAntialias = true;
				if ((y > 0      ) && (bI != bestI[y-1][x])) shouldAntialias = true;
				if ((y < sizeY-1) && (bI != bestI[y+1][x])) shouldAntialias = true;
				
				RVDColor color;
				
				if (shouldAntialias) {
					RVDColor sum = new RVDColor();
					
					for (int idx = 0; idx < nValues; idx++) {
						int idy = Integer.reverse(idx) >>> (32 - nBits); // No need to optimize
						
						Vector cPixel = Vector.xy(x + (idx + 0.5) / nValues, y + (idy + 0.5) / nValues);
						Vector p = tFromPixels.applyTo(cPixel);
						IndexAngle best = diagram == DiagramType.DISK_DIAGRAM ? findDDCell(p, dominanceRegion) : findNearest(p, rays);
						
						sum = sum.add(colorDiagram(best));
					}
					
					color = sum.mul(1.0 / nValues);
				} else {
					color = colorDiagram(new IndexAngle(bI, bestA[y][x]));
				}
				
				pixels[y * sizeX + x] = color.code();
			}
		});
		
		
		PixelFormat<IntBuffer> pixelFormat = PixelFormat.getIntArgbPreInstance();
		IntBuffer buffer = IntBuffer.wrap(pixels);
		
		PixelBuffer<IntBuffer> pixelBuffer = new PixelBuffer<>(sizeX, sizeY, buffer, pixelFormat);
		pixelBuffer.updateBuffer(pb -> {
			return null;
		});
		
		return new WritableImage(pixelBuffer);
	}
	
	
	
	// Constructs an image covering box b (given in the current view space), and draws it.
//	private void drawDiagram(View view, Box b) {
//		Transformation t = view.transformation();
//		Transformation tInv = t.inverse();
//		Box bNativeBounding = Box.bounding(t.applyTo(b.corners()));
//		Box bNativeBoundingInt = Box.UNIT.gridIndices(bNativeBounding);
//
//		Image img = makeImage(tInv, bNativeBoundingInt);
//		view.setTransformation(Transformation.translation(bNativeBoundingInt.p().inversed()));
//		view.drawImage(bNativeBoundingInt, img);
//		view.setTransformation(t);
//	}
	
	
	Image imgDiagram;
	
	// Constructs an image covering visible area and draws it.
	private void drawDiagram(View view, boolean shouldRedraw) {
		Box b = view.nativeBox().positive();
		Transformation t = view.transformation();
		
		if (shouldRedraw) {
			imgDiagram = makeImage(t.inverse(), b);
		}
		
		view.setTransformation(Transformation.IDENTITY);
		view.drawImage(b, imgDiagram);
		view.setTransformation(t);
	}
	
	
	private void drawRays(View view) {
		view.setLineWidth(strokeWidth * pixelWidth);
		
		for (int k = 0; k < n; k++) {
			Ray ray = Ray.pd(points[k], Vector.polar(angles[k] + rotate));
			view.setStroke(colorStroke(k, enabled[k], k == kSelected));
			view.strokeRay(ray);
		}
	}
	
	
	private void drawPoints(View view) {
		view.setLineWidth(strokeWidth * pixelWidth);
		
		for (int k = 0; k < n; k++) {
			view.setFill(colorFill(k));
			view.fillCircleCentered(points[k], rPoint * pixelWidth);
			
			view.setStroke(colorStroke(k, enabled[k], k == kSelected));
			view.strokeCircleCentered(points[k], rPoint * pixelWidth);
		}
	}
	
	
	private void stroke(View view, Figure f) {
		if (f instanceof Circle   ) view.strokeCircle   ((Circle   ) f);
		if (f instanceof HalfPlane) view.strokeHalfPlane((HalfPlane) f);
	}
	
	
	private void drawCircles(View view) {
		view.setLineWidth(pixelWidth);
		
		if (kSelected == -1) {
			view.setStroke(Color.gray(1, 0.25));
			for (int i0 = 0; i0 < n; i0++) {
				if (enabled[i0]) {
					for (int i1 = 0; i1 < i0; i1++) {
						if (enabled[i1]) {
							stroke(view, dominanceFor(i0, i1));
						}
					}
				}
			}
		} else {
			if (enabled[kSelected]) {
				for (int i = 0; i < n; i++) {
					if (i != kSelected && enabled[i]) {
						view.setStroke(colorStroke(i, true, false));
						stroke(view, dominanceFor(i, kSelected));
					}
				}
			}
		}
	}
	
	
	private void showHelp(View view) {
		DrawingUtils.drawInfoText(view,
				"Gadgets:",
				"    data            - The encoding of the configuration (save/load = copy/paste)",
				"    rotate          - Rotate all rays by this angle",
				"    n               - The number of rays",
				"",
				"Controls:",
				"    Mouse left      - Select a ray; Move the initial point of the selected ray",
				"    Mouse right     - Set the angle of the selected ray",
				"    Mouse wheel     - Toggle if the selected ray is enabled",
				"    Y               - Toggle polygon mode",
				"    F2              - Show RVD for rays",
				"    F3              - Show RVD for lines",
				"    F4              - Show Disk Diagram",
				"    F5              - Show RVD for rays (bidirectional)",
				"    F8              - Toggle grid",
				"    G               - Toggle snap to grid",
				"    D               - Toggle show diagram",
				"    M               - Toggle show only the area on the left of all active rays",
				"    C               - Toggle show circles",
				"    R               - Toggle show rays",
				"    P               - Toggle show points",
				"    S               - Toggle shading",
				"    L               - Toggle color",
				"    H               - Toggle show help",
				"    Ctrl            - Control the view:",
				"      + Mouse left      - Pan",
				"      + Mouse wheel     - Zoom",
				"      + Mouse right     - Reset",
				"",
				"App author:",
				"    Marko Savić (marsavic@gmail.com)"
		);
	}
	
	
	String lastDataString = "";
	Box lastNativeBox = Box.ZERO;
	Transformation lastTransformation = Transformation.IDENTITY;
	
	boolean diagramChanged = true;
	
	
	@Override
	public void valuesChanged() {
		diagramChanged = true;
	}
	
	
	@Override
	public void draw(View view) {
		view.addTransformation(camera.getTransformation().scale(scale));
		
		if (!dataString.equals(lastDataString)) {
			stringToData(dataString);
		} else {
			diagramChanged |= !lastNativeBox.equals(view.nativeBox());
			diagramChanged |= !lastTransformation.equals(view.transformation());
		}
		
		pixelWidth = 1.0 / view.transformation().getScale();
		
		DrawingUtils.clear(view, Color.gray(0.9));
		
		if (showDiagram) drawDiagram(view, diagramChanged);
		if (showCircles) drawCircles(view);
		if (showRays   ) drawRays(view);
		if (showPoints ) drawPoints(view);
		if (showHelp   ) showHelp(view);
		
		lastDataString = dataString = dataAsString();
		lastNativeBox = view.nativeBox();
		lastTransformation = view.transformation();
		diagramChanged = false;
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
	double mouseReach = 10;
	double draggingMinDistance = 1;
	
	
	@Override
	public void receiveEvent(View view, InputEvent event, InputState state, Vector pointerWorld, Vector pointerViewBase) {
		if (state.keyPressed(KeyCode.CONTROL)) {
			camera.receiveEvent(view, event, state, pointerWorld, pointerViewBase);
			return;
		}
		
		Vector p = snapToGrid ? pointerWorld.round(gridCellD) : pointerWorld;
		
		if (!dragging) {
			int k = nearestK(pointerWorld, mouseReach * pixelWidth);
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
			if (draggingStartPoint != null && pointerWorld.distanceTo(draggingStartPoint) > draggingMinDistance * pixelWidth) {
				dragging = true;
			}
		} else {
			dragging = false;
		}
		
		if (dragging && kSelected >= 0) {
			points[kSelected] = p;
			diagramChanged = true;
		}
		
		if (state.mouseButtonPressed(3) && kSelected >= 0) {
			Vector d = p.sub(points[kSelected]);
			angles[kSelected] = d.angle() - rotate;
			diagramChanged = true;
		}
		
		if (event.isMouseWheel() && kSelected >= 0) {
			enabled[kSelected] ^= true;
			diagramChanged = true;
		}
		
		if (event.isKeyPress(KeyCode.G))   snapToGrid               ^= true;
		if (event.isKeyPress(KeyCode.C))   showCircles              ^= true;
		if (event.isKeyPress(KeyCode.R))   showRays                 ^= true;
		if (event.isKeyPress(KeyCode.P))   showPoints               ^= true;
		if (event.isKeyPress(KeyCode.H))   showHelp                 ^= true;
		if (event.isKeyPress(KeyCode.D)) { showDiagram              ^= true; diagramChanged |= showDiagram; }
		if (event.isKeyPress(KeyCode.L)) { showColor                ^= true; diagramChanged = true; }
		if (event.isKeyPress(KeyCode.S)) { shading                  ^= true; diagramChanged = true; }
		if (event.isKeyPress(KeyCode.Y)) { polygon                  ^= true; diagramChanged = true; }
		if (event.isKeyPress(KeyCode.M)) { leftSideOnly             ^= true; diagramChanged = true; }
		
		if (event.isKeyPress(KeyCode.F2)) { diagram = DiagramType.RVD_RAYS_ORIENTED; diagramChanged = true; }
		if (event.isKeyPress(KeyCode.F3)) { diagram = DiagramType.RVD_LINES              ; diagramChanged = true; }
		if (event.isKeyPress(KeyCode.F4)) { diagram = DiagramType.DISK_DIAGRAM; diagramChanged = true; }
		if (event.isKeyPress(KeyCode.F5)) { diagram = DiagramType.RVD_RAYS_UNORIENTED; diagramChanged = true; }
	}
	
	
	
	
	public static void main(String[] args) {
		Options options = new Options();
		options.windowTitle = "RVD Explorer";
		options.drawingSize = RVDExplorerForPrint.sizeInitial;
		options.gridSubdivision = 10;
		options.gridInterval = gridCellD.x() * options.gridSubdivision;
		options.gridColor = Color.gray(1, 0.125);
//		options.gadgetsPosition = GadgetsPosition.TOP;
		
		DrawingApplication.launch(options);

//		RenderingApplication.launch("c:/animations", 1920, 1080, 60);
	}
	
}