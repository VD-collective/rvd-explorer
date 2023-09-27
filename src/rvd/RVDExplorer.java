package rvd;

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
import xyz.marsavic.drawingfx.gadgets.annotations.*;
import xyz.marsavic.drawingfx.utils.camera.CameraSimple;
import xyz.marsavic.functions.F_R_R;
import xyz.marsavic.geometry.*;
import xyz.marsavic.input.InputEvent;
import xyz.marsavic.input.InputState;
import xyz.marsavic.input.KeyCode;
import xyz.marsavic.random.sampling.Sampler;
import xyz.marsavic.utils.Numeric;
import xyz.marsavic.utils.performance.ApproximateNumeric;
import xyz.marsavic.utils.performance.ArrayInts;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.IntBuffer;
import java.util.Base64;
import java.util.stream.IntStream;


enum DiagramType {
	RVD_RAYS, RVD_LINES, RVD_BIDIRECTIONAL_RAYS, DISK_DIAGRAM
}


public class RVDExplorer implements Drawing {
	public static final Vector sizeInitial = Vector.xy(800, 800);
	public static final Vector gridCellD = Vector.xy(16, 16);
	
	private final int maxN = 64;
	
	@GadgetBoolean
	@Properties(name = "Help (h)")
	boolean showHelp = false;
	
	@GadgetString
	String dataString = "";
	
	@GadgetAnimation(p = 0, q = 1, loop = true, speed = 0.05, start = false)
	double rotate = 0.0;
	
	@GadgetInteger(min = 1, max = maxN)
	@Properties(name = "Number of sites")
	int n = 7;
	
	
	@Properties(name = "Diagram type (F2-5)")
	@GadgetEnum(enumClass = DiagramType.class)
	DiagramType diagram = DiagramType.RVD_RAYS;
	
	@GadgetBoolean
	@Properties(name = "Polygon mode (y)")
	boolean polygonMode = true;
	
	@GadgetBoolean
	@Properties(name = "Show polygon exterior (x)")
	boolean showPolygonExterior = false;
	

	@GadgetBoolean
	@Properties(name = "Snap to grid (g)")
	boolean snapToGrid = false;
	
	@GadgetBoolean
	@Properties(name = "Diagram (d)")
	boolean showDiagram = true;
	
	@GadgetBoolean
	@Properties(name = "Points (p)")
	boolean showPoints = true;
	
	@GadgetBoolean
	@Properties(name = "Rays (r)")
	boolean showRays = false;
	
	@GadgetBoolean
	@Properties(name = "Circles (c)")
	boolean showCircles = false;
	
	@GadgetBoolean
	@Properties(name = "Color (l)")
	boolean showColor = true;
	
	@GadgetBoolean
	@Properties(name = "Shading (s)")
	boolean showShading = true;
	
	@GadgetBoolean
	@Properties(name = "Visibility cells (v)")
	boolean showVisibilityCells = true;

	@GadgetBoolean
	@Properties(name = "Visibility cell depth")
	boolean visibilityCellsShadingCount = true;
	
	
	
	
	
	Vector[] points = new Vector[maxN];
	double[] angles = new double[maxN];
	boolean[] enabled = new boolean[maxN];
	int kSelected = -1;
	
	private Polygon polygon;
	
	
	CameraSimple camera = new CameraSimple(F_R_R.cutoff01(t -> F_R_R.power(t, 8)));
	double pixelWidth;
	
	
	
	private String dataAsString() {
		try (
				ByteArrayOutputStream outB = new ByteArrayOutputStream();
				ObjectOutputStream out = new ObjectOutputStream(outB)
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
				ObjectInputStream in = new ObjectInputStream(inB)
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
		Sampler sampler = new Sampler();
		
		Box box = Box.cr(sizeInitial.div(2));
		for (int k = 0; k < maxN; k++) {
			points[k] = sampler.randomInBox(box.scaleFromCenter(2.0/3));
//			points[k] = sampler.randomGaussian(box.r().min() / 2);
			angles[k] = sampler.rng().nextDouble();
			enabled[k] = true;
		}
	}
	
	
	private final double strokeWidth = 3;
	private final double rPoint = 6.0;
	
	private double hue(int k) {
		return 360 * k * Numeric.PHI;
	}
	
	private Color colorStroke(int k, boolean enabled, boolean selected) {
		return Color.hsb(
				hue(k),
				selected ? 0.2 : 0.8,
				selected ? 1.0 : 0.6,
				enabled ? 1.0 : 0.4
		);
	}
	
	private Color colorFill(int k, boolean enabled) {
		return Color.hsb(
				hue(k),
				0.7,
				1.0,
				enabled ? 1.0 : 0.0
		);
	}
	
	
	RVDColor[] colorsDiagram = new RVDColor[maxN];
	{
		for (int k = 0; k < maxN; k++) {
			colorsDiagram[k]= new RVDColor(Color.hsb(hue(k), 0.6, 1.0));
		}
	}
	
	
	private RVDColor colorDiagram(IndexAngle ia) {
		if (ia.i == -1) return RVDColor.GRAY;
		double b = showShading ? 0.9 - 0.6 * ia.a : 0.9;
		return showColor ? colorsDiagram[ia.i].mul(b) : new RVDColor(b);
	}
	
	
	private boolean visibleThroughThePolygon(Vector p, int k) {
		LineSegment ab = LineSegment.pq(p, points[k]);
		
		if (!showPolygonExterior) {
			if (ab.d().angleBetween(polygon.e(k-1).d(), polygon.e(k).d().inverse())) {
				return false;
			}
		}
		
		for (int i = 0; i < n; i++) {
			if ((i == k) || ((i + 1) % n == k)) {
				continue;
			}
			LineSegment edge = LineSegment.pq(points[i], points[(i + 1) % n]);
			
			if (Geometry.intersecting(ab, edge)) {
				return false;
			}
		}
		return true;
	}
	
	
	private int[] visibleVertices(Vector p) {
		ArrayInts vs = new ArrayInts(n);
		for (int k = 0; k < n; k++) {
			if (!polygonMode || visibleThroughThePolygon(p, k)) {
				vs.add(k);
			}
		}
		return vs.toArray();
	}
	
	
	private record IndexAngle (int i, double a) {
	}
	
	//	final Vector almostFullTurn = Vector.polar(Math.nextDown(1.0));
	final Vector almostFullTurn = Vector.xy(Double.MAX_VALUE, -Double.MIN_VALUE);
	
	private IndexAngle findNearest(Vector p, Ray[] rays) {
		int bestK = -1;
		Vector bestR = almostFullTurn;
		
		int[] vis = visibleVertices(p);
		
		for (int k : vis) {
			if (enabled[k]) {
				Vector o = p.sub(rays[k].p());
				Vector e = rays[k].d();
				Vector r = Vector.xy(e.dot(o), e.cross(o));
				
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
				if (diagram == DiagramType.RVD_BIDIRECTIONAL_RAYS) {
					Vector r2 = Vector.xy(r.x(), -r.y());
					if (r2.angleBefore(bestR)) {
						bestR = r2;
						bestK = k;
					}
				}
			}
		}
		
		if (polygonMode) {
			double s = 1.0;
			if (visibilityCellsShadingCount  ) { s *= 1.0 * vis.length / n; }
			return new IndexAngle(bestK, 1 - s);
		} else {
			return new IndexAngle(bestK, ApproximateNumeric.angle(bestR));
		}
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


/*
	Figure dominanceFor(int i0, int i1) {
		Vector p0 = points[i0];
		Vector p1 = points[i1];
		double phi = angles[i1] - angles[i0];

//		if (Numeric.mod(phi, 1) < 0.5) {
			Figure c = dominanceDiskFor(i0, i1);
			Figure r0 = HalfPlane.pq(Vector.ZERO, p0);
			Figure r1 = HalfPlane.pq(p1, Vector.ZERO);
			Figure l = HalfPlane.pq	(p0, p1);

			return new SunRegion(c, r0, r1, l);

	}
*/
	
	
	private static final int nBits = 3;
	private static final int nValues = 1 << nBits;
	
	int[] pixels;
	int sizeYp = 0, sizeXp = 0;
	byte[][] bestI;
	float[][] bestA;
	
	/**
	 * @param tFromPixels  A transformation from the pixel space to the working space
	 * @param bImage  An integer box in the pixel space used to make the resulting image
	 * @return ...
	 */
	private Image makeImage(Transformation tFromPixels, Box bImage) {
		Vector diag = bImage.d().abs();
		int sizeX = diag.xInt();
		int sizeY = diag.yInt();
		
		if (sizeX == 0 || sizeY == 0) {
			return null;
		}
		
		if (polygonMode) {
			for (int i = 0; i < n; i++) {
				angles[i] = points[(i + 1) % n].sub(points[i]).angle();
			}

			polygon = Polygon.of(points, n);
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
		pixelBuffer.updateBuffer(pb -> null);
		
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
	

	private void drawPolygon(View view) {
		view.setLineWidth(strokeWidth * pixelWidth);
		view.setStroke(Color.BLACK);
		view.strokePolygon(polygon);
	}
	
	
	private void drawVisibilityCells(View view) {
		view.setLineWidth(strokeWidth * pixelWidth / 2);
		view.setStroke(Color.gray(0, 0.5));
		
		for (int iq = 0; iq < n; iq++) {
			Vector q = polygon.v(iq);
			Vector pq = polygon.e(iq-1).d();
			Vector qr = polygon.e(iq).d();
			if (pq.cross(qr) >= 0) {
				continue;
			}
			// reflex vertex
			
			for (int io = 0; io < n; io++) {
				if (io != iq) {
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
					
/*
					if (!oq.sameSide(pq, qr)) {
						double k = polygon.intersectionSideFirst(loq, 0);
						if ((k == iq) || (((k + 1) % polygon.size()) == iq)) {
							double t = polygon.intersectionTimeFirst(loq, 1);
							view.strokeLineSegment(loq.segment(Interval.pq(1, t)));
						}
					}
*/
				}
			}
		}
	}
	
	
	private void drawPoints(View view) {
		view.setLineWidth(strokeWidth * pixelWidth);
		
		for (int k = 0; k < n; k++) {
			view.setFill(colorFill(k, enabled[k]));
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
				"    E               - Toggle if the ray originating near the pointer is enabled",
				"    Y               - Toggle polygon mode",
				"    F2              - Show RVD for rays",
				"    F3              - Show RVD for lines",
				"    F4              - Show Disk Diagram",
				"    F5              - Show RVD for rays (bidirectional)",
				"    F8              - Toggle grid",
				"    G               - Toggle snap to grid",
				"    D               - Toggle show diagram",
				"    C               - Toggle show circles",
				"    R               - Toggle show rays",
				"    P               - Toggle show points",
				"    V               - Toggle show visibility cells",
				"    X               - Toggle show polygon exterior",
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
		view.addTransformation(camera.getTransformation());
		
		if (!dataString.equals(lastDataString)) {
			stringToData(dataString);
		} else {
			diagramChanged |= !lastNativeBox.equals(view.nativeBox());
			diagramChanged |= !lastTransformation.equals(view.transformation());
		}
		
		pixelWidth = 1.0 / view.transformation().getScale();
		
		DrawingUtils.clear(view, Color.gray(0.9));
		
		if (showDiagram        ) drawDiagram(view, diagramChanged);
		if (polygonMode        ) drawPolygon(view);
		if (showVisibilityCells) drawVisibilityCells(view);
		if (showCircles        ) drawCircles(view);
		if (showRays           ) drawRays(view);
		if (showPoints         ) drawPoints(view);
		if (showHelp           ) showHelp(view);
		
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
	double mouseReach = 12;
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
		
		if (event.isKeyPress(KeyCode.E)) {
			int k = nearestK(pointerWorld, mouseReach * pixelWidth);
			if (k >= 0) {
				enabled[k] ^= true;
				diagramChanged = true;
			}
		}
		
		if (event.isKeyPress(KeyCode.G))   snapToGrid               ^= true;
		if (event.isKeyPress(KeyCode.C))   showCircles              ^= true;
		if (event.isKeyPress(KeyCode.R))   showRays                 ^= true;
		if (event.isKeyPress(KeyCode.P))   showPoints               ^= true;
		if (event.isKeyPress(KeyCode.V))   showVisibilityCells      ^= true;
		if (event.isKeyPress(KeyCode.H))   showHelp                 ^= true;
		if (event.isKeyPress(KeyCode.D)) { showDiagram              ^= true; diagramChanged |= showDiagram; }
		if (event.isKeyPress(KeyCode.L)) { showColor                ^= true; diagramChanged = true; }
		if (event.isKeyPress(KeyCode.S)) { showShading              ^= true; diagramChanged = true; }
		if (event.isKeyPress(KeyCode.Y)) { polygonMode              ^= true; diagramChanged = true; }
		if (event.isKeyPress(KeyCode.X)) { showPolygonExterior      ^= true; diagramChanged = true; }
		
		if (event.isKeyPress(KeyCode.F2)) { diagram = DiagramType.RVD_RAYS               ; diagramChanged = true; }
		if (event.isKeyPress(KeyCode.F3)) { diagram = DiagramType.RVD_LINES              ; diagramChanged = true; }
		if (event.isKeyPress(KeyCode.F4)) { diagram = DiagramType.RVD_BIDIRECTIONAL_RAYS ; diagramChanged = true; }
		if (event.isKeyPress(KeyCode.F5)) { diagram = DiagramType.DISK_DIAGRAM           ; diagramChanged = true; }
	}
	
	
	
	
	public static void main(String[] args) {
		Options options = new Options();
		options.windowTitle = "RVD Explorer";
		options.drawingSize = RVDExplorer.sizeInitial;
		options.gridSubdivision = 8;
		options.gridInterval = gridCellD.x() * options.gridSubdivision;
		options.gridColor = Color.gray(1, 0.125);
		DrawingApplication.launch(options);

//		RenderingApplication.launch("c:/animations", 1920, 1080, 60);
	}
	
}
