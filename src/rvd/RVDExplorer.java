package rvd;

import javafx.scene.image.Image;
import javafx.scene.image.PixelBuffer;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.scene.shape.StrokeLineJoin;
import rvd.core.DiagramPreparation;
import rvd.core.DominanceRegionFactory;
import rvd.core.DiskCellSelector;
import rvd.core.PolygonVisibility;
import rvd.core.RayNearestSelector;
import rvd.io.ExplorerDataCodec;
import rvd.model.ExplorerSnapshot;
import rvd.model.ExplorerState;
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
import xyz.marsavic.utils.Hash;
import xyz.marsavic.utils.Numeric;

import java.nio.IntBuffer;
import java.util.stream.IntStream;


enum DiagramType {
	RVD_RAYS_ORIENTED, RVD_RAYS_UNORIENTED, RVD_LINES, DISK_DIAGRAM
}


public class RVDExplorer implements Drawing {
	public static final Vector sizeInitial = Vector.xy(800, 800);
	public static final Vector gridCellD = Vector.xy(16, 16);

	private final int maxN = 64;

	@GadgetBoolean
	@Properties(name = "Help (h)")
	boolean showHelp = false;

	@GadgetString
	@Properties(name = "Data String")
	String dataString = "rO0ABXoAAAHOAAAAAAAAAAAAAAASQE+AAAAAAADANgAAAAAAAD/RI++HH+QVAUA5AAAAAAAAQHPAAAAAAAA/4Pne7C5yLAHARQAAAAAAAEBy8AAAAAAAP+gyHxB0iNkBwD0AAAAAAADAQYAAAAAAAD/TRHIvdh0DAcBgYAAAAAAAQHEAAAAAAAA/4kplNrs9TwHAZ6AAAAAAAEBugAAAAAAAP+j+t2xMBQABwFyAAAAAAADAYOAAAAAAAD/XOrCUFp4CAcBuQAAAAAAAQCwAAAAAAAA/4oc8EYd5zgHAc6AAAAAAAMA5AAAAAAAAP+ru8xZW8P0BwGkgAAAAAADAaOAAAAAAAD/sTJsOBK9AAcBXQAAAAAAAwHJwAAAAAAA/dTFG2BgVNwFAYyAAAAAAAMBx8AAAAAAAP8WHD651nQIBQGkgAAAAAADAaUAAAAAAAD/Jt2jYeCQXAUBy4AAAAAAAQFzAAAAAAAA/2XICjs75hAFAcCAAAAAAAEBigAAAAAAAP+VYCjrljjYBQGNAAAAAAADAQIAAAAAAAD/MrdmdrBhQAUBpgAAAAAAAQHDwAAAAAAA/3G85j2AqXQFAYaAAAAAAAEByYAAAAAAAP+bEe81fa/AB";

	@RecurseGadgets
	final ExplorerState state = new ExplorerState(maxN);

	@GadgetDouble
	@Properties(name = "Max aperture")
	double stopAngle1 = 1;

	@GadgetDouble
	@Properties(name = "Current aperture (%)")
	double stopAngle2 = 1;


	@Properties(name = "Diagram type (F2-5)")
	@GadgetEnum(enumClass = DiagramType.class)
	DiagramType diagram = DiagramType.RVD_RAYS_ORIENTED;

	@GadgetBoolean
	@Properties(name = "Polygon mode (y)")
	boolean polygonMode = true;


	@GadgetBoolean
	@Properties(name = "Show diagram (d)")
	boolean showDiagram = true;

	@GadgetBoolean
	@Properties(name = "Show diagram skeleton (k)")
	boolean showDiagramSkeleton = false;

	@GadgetBoolean
	@Properties(name = "Show points of the maximum angle (b)")
	boolean showBrocardPoint = false;

	@GadgetBoolean
	@Properties(name = "Show distance shading (s)")
	boolean showShading = true;

	@GadgetBoolean
	@Properties(name = "Color regions (l)")
	boolean showColor = true;


	@GadgetBoolean
	@Properties(name = "Show sites (p)")
	boolean showPoints = true;

	@GadgetBoolean
	@Properties(name = "Show rays (r)")
	boolean showRays = false;

	@GadgetBoolean
	@Properties(name = "Show circles (c)")
	boolean showCircles = false;



	@GadgetBoolean
	@Properties(name = "Show polygon exterior (x)")
	boolean showPolygonExterior = false;

	@GadgetBoolean
	@Properties(name = "Show visibility cells (v)")
	boolean showVisibilityCells = false;

	@GadgetBoolean
	@Properties(name = "Show visibility cells depth")
	boolean visibilityCellsShadingCount = true;


	@GadgetColorPicker
	@Properties(name = "Background color")
	Color colorBackground = Color.gray(0.2);

	@GadgetBoolean
	@Properties(name = "Snap to grid (g)")
	boolean snapToGrid = false;





	double[] hues = new double[maxN];
	int kSelected = -1;

	private Polygon polygon;

	RVDColor rvdColorBackground;
	private final ExplorerDataCodec dataCodec = new ExplorerDataCodec();
	private final DominanceRegionFactory dominanceRegionFactory = new DominanceRegionFactory();
	private final DiskCellSelector diskCellSelector = new DiskCellSelector();
	private final PolygonVisibility polygonVisibility = new PolygonVisibility();
	private final RayNearestSelector rayNearestSelector = new RayNearestSelector();
	private final DiagramPreparation diagramPreparation = new DiagramPreparation();


	CameraSimple camera = new CameraSimple(F_R_R.cutoff01(t -> F_R_R.power(t, 8)));
	double pixelWidth;



	private String dataAsString() {
		return dataCodec.encode(state.snapshot());
	}


	private void stringToData(String data) {
		ExplorerSnapshot snapshot = dataCodec.decode(data);
		if (snapshot != null) {
			state.applySnapshot(snapshot);
		}
	}

	ExplorerState explorerState() {
		return state;
	}


	{
		Sampler sampler = new Sampler(new Hash(0x5C727CC650E510C7L));

		Box box = Box.cr(sizeInitial.div(2));
		for (int k = 0; k < maxN; k++) {
			state.points[k] = sampler.randomInBox(box.scaleFromCenter(2.0/3));
//			state.points[k] = sampler.randomGaussian(box.r().min() / 2);
			state.angles[k] = sampler.uniform();
			state.enabled[k] = true;
			hues[k] = 360 * k * Numeric.PHI;
		}
	}


	private final double strokeWidth = 3;
	private final double rPoint = 6.0;

	private double hue(int k) {
		return 360 * k * Numeric.PHI;
	}

	private Color colorStroke(int k, boolean enabled, boolean selected) {
		return Color.hsb(
				hues[k],
				selected ? 0.0 : 1.0,
				selected ? 1.0 : 0.0,
				enabled ? 1.0 : 0.4
		);
	}

	private Color colorStrokeRay(int k, boolean enabled, boolean selected) {
		return Color.hsb(
				hues[k],
				selected ? 0.2 : 0.8,
				selected ? 1.0 : 0.6,
				enabled ? 1.0 : 0.4
		);
	}

	private Color colorFill(int k, boolean enabled) {
		return Color.hsb(
				hues[k],
				0.7,
				1.0,
				enabled ? 1.0 : 0.0
		);
	}


	RVDColor[] colorsDiagram = new RVDColor[maxN];
	{
		for (int k = 0; k < maxN; k++) {
			colorsDiagram[k]= new RVDColor(Color.hsb(hues[k], 0.6, 1.0));
		}
	}


	private RVDColor colorDiagram(PointResult ia) {
		if (ia.i == -1) return RVDColor.BLACK;
		if (ia.i == -2) return rvdColorBackground;
		if (ia.i == -3) return rvdColorBackground;
		double b = showShading ? 0.9 - 0.6 * ia.a : 1.0;
		if (visibilityCellsShadingCount) b *= (double) ia.nVisible / state.n;
		return showColor ? colorsDiagram[ia.i].mul(b) : new RVDColor(b);
	}

	private record PointResult(int i, int nVisible, double a) {
		// i = -1    In the domain, but on the skeleton
		// i = -2    In the domain, but inside the aperture
		// i = -3    Out of the domain
	}

	private static final double rEdge = 1.6;
	private static final int nEdgeSamples = 6;

	private PointResult findNearest(Vector p, Ray[] rays) {
		PointResult iaCenter = findNearest_(p, rays);

		if (showDiagramSkeleton) {
			for (int k = 0; k < nEdgeSamples; k++) {
				Vector q = Vector.polar(rEdge, (double) k / nEdgeSamples).add(p);
				PointResult iaEdge = findNearest_(q, rays);
				if (iaEdge.i != iaCenter.i) {
					return new PointResult(-1, iaCenter.nVisible, iaCenter.a);
				}
			}
		}

		return iaCenter;
	}

	private PointResult findNearest_(Vector p, Ray[] rays) {
		int[] vis = polygonVisibility.visibleVertices(
				p,
				state.points,
				state.n,
				polygon,
				polygonMode,
				showPolygonExterior
		);

		RayNearestSelector.Result nearest = rayNearestSelector.select(
				p,
				rays,
				state.enabled,
				vis,
				diagram == DiagramType.RVD_LINES,
				diagram == DiagramType.RVD_RAYS_UNORIENTED
		);
		int bestK = nearest.bestIndex();
		double angle = nearest.angle();
		if (bestK >= 0) {
			if (angle > stopAngle1 * stopAngle2) {
				bestK = -2;
			}
		}

		return new PointResult(bestK, vis.length, angle);
	}


	private PointResult findDDCell(Vector p, Figure[][] dominances) {
		int k = diskCellSelector.select(p, dominances, state.enabled, state.n);
		return new PointResult(k, 0, 0.0);
//		return new IndexAngle(dominances[0][1].contains(p) ? 0 : -1, 0);
	}


	Figure dominanceFor(int i0, int i1) {
		return dominanceRegionFactory.create(
				state.points[i0],
				state.points[i1],
				state.angles[i0],
				state.angles[i1]
		);
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


	final Vector[] pBrocard = { null }; // terribly hacky and not thread-safe due to lack of time
	final double[] aBrocard = { 0.0 };  // same

	private static final int nBits = 3;
	private static final int nValues = 1 << nBits;

	int[] pixels;
	int sizeYp = 0, sizeXp = 0;
	byte[][] bestI;
	byte[][] bestDepth;
	float[][] bestA;

	private void ensureBuffers(int sizeX, int sizeY) {
		if ((sizeYp < sizeY) || (sizeXp < sizeX)) {
			sizeYp = sizeY;
			sizeXp = sizeX;
			bestI = new byte[sizeY][sizeX];
			bestDepth = new byte[sizeY][sizeX];
			bestA = new float[sizeY][sizeX];
			pixels = new int[sizeY * sizeX];
		}
	}

	private void resetBrocardSearch() {
		aBrocard[0] = 0.0;
		pBrocard[0] = null;
	}

	private PointResult classifyPoint(Vector p, Figure[][] dominanceRegion, Ray[] rays) {
		return (diagram == DiagramType.DISK_DIAGRAM)
				? findDDCell(p, dominanceRegion)
				: findNearest(p, rays);
	}

	private void updateBrocardIfBetter(PointResult ia, Vector p) {
		if (ia.i >= -2 && ia.a() > aBrocard[0]) {
			aBrocard[0] = ia.a();
			pBrocard[0] = p;
		}
	}

	private void computeNearestPass(
			Transformation tFromPixels,
			int sizeX,
			int sizeY,
			Figure[][] dominanceRegion,
			Ray[] rays
	) {
		IntStream.range(0, sizeY).parallel().forEach(y -> {
			for (int x = 0; x < sizeX; x++) {
				Vector cPixel = Vector.xy(x + 0.5, y + 0.5);
				Vector p = tFromPixels.applyTo(cPixel);
				PointResult ia = classifyPoint(p, dominanceRegion, rays);
				bestI[y][x] = (byte) ia.i;
				bestDepth[y][x] = (byte) ia.nVisible;
				bestA[y][x] = (float) ia.a;
				updateBrocardIfBetter(ia, p);
			}
		});
	}

	private void computeAntialiasPass(
			Transformation tFromPixels,
			int sizeX,
			int sizeY,
			Figure[][] dominanceRegion,
			Ray[] rays
	) {
		IntStream.range(0, sizeY).parallel().forEach(y -> {
			for (int x = 0; x < sizeX; x++) {
				int bI = bestI[y][x];

				boolean shouldAntialias = false;
				if ((x > 0      ) && (bI != bestI[y][x-1])) shouldAntialias = true;
				if ((x < sizeX-1) && (bI != bestI[y][x+1])) shouldAntialias = true;
				if ((y > 0      ) && (bI != bestI[y-1][x])) shouldAntialias = true;
				if ((y < sizeY-1) && (bI != bestI[y+1][x])) shouldAntialias = true;

				RVDColor color;

				if (!shouldAntialias) {
					color = colorDiagram(new PointResult(bI, bestDepth[y][x], bestA[y][x]));
				} else {
					RVDColor sum = new RVDColor();

					for (int idx = 0; idx < nValues; idx++) {
						int idy = Integer.reverse(idx) >>> (32 - nBits); // No need to optimize

						Vector cPixel = Vector.xy(x + (idx + 0.5) / nValues, y + (idy + 0.5) / nValues);
						Vector p = tFromPixels.applyTo(cPixel);
						PointResult ia = classifyPoint(p, dominanceRegion, rays);

						sum = sum.add(colorDiagram(ia));

						updateBrocardIfBetter(ia, p);
					}

					color = sum.mul(1.0 / nValues);
				}

				pixels[y * sizeX + x] = color.code();
			}
		});
	}

	private boolean isEmptyImage(int sizeX, int sizeY) {
		return sizeX == 0 || sizeY == 0;
	}

	private Image buildWritableImage(int sizeX, int sizeY) {
		PixelFormat<IntBuffer> pixelFormat = PixelFormat.getIntArgbPreInstance();
		IntBuffer buffer = IntBuffer.wrap(pixels);
		PixelBuffer<IntBuffer> pixelBuffer = new PixelBuffer<>(sizeX, sizeY, buffer, pixelFormat);
		pixelBuffer.updateBuffer(pb -> null);
		return new WritableImage(pixelBuffer);
	}

	/**
	 * @param tFromPixels  A transformation from the pixel space to the working space
	 * @param bImage  An integer box in the pixel space used to make the resulting image
	 * @return ...
	 */
	private Image makeImage(Transformation tFromPixels, Box bImage) {
		Vector diag = bImage.d().abs();
		int sizeX = diag.xInt();
		int sizeY = diag.yInt();

		if (isEmptyImage(sizeX, sizeY)) {
			return null;
		}

		DiagramPreparation.PreparedData prepared = diagramPreparation.prepare(
				state.points,
				state.angles,
				state.n,
				state.rotate,
				polygonMode,
				this::dominanceFor
		);
		polygon = prepared.polygon();
		Ray[] rays = prepared.rays();
		Figure[][] dominanceRegion = prepared.dominanceRegion();

		ensureBuffers(sizeX, sizeY);

		resetBrocardSearch();


		// Finding nearest
		computeNearestPass(tFromPixels, sizeX, sizeY, dominanceRegion, rays);

		// Antialiasing
		computeAntialiasPass(tFromPixels, sizeX, sizeY, dominanceRegion, rays);


		return buildWritableImage(sizeX, sizeY);
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

		for (int k = 0; k < state.n; k++) {
			Ray ray = Ray.pd(state.points[k], Vector.polar(state.angles[k] + state.rotate));
			view.setStroke(colorStrokeRay(k, state.enabled[k], k == kSelected));
			view.strokeRay(ray);
		}
	}


	private void drawBrocardPoint(View view) {
		if (pBrocard[0] == null) {
			return;
		}
/*
		System.out.println("RVDExplorer.drawBrocardPoint:581 " + pBrocard[0]);
		Ray[] rays = new Ray[state.n];
		for (int i = 0; i < state.n; i++) {
			rays[i] = Ray.pa(state.points[i], state.angles[i] + state.rotate);
		}
		findNearest(pBrocard[0], rays);
*/

//		view.setLineWidth(strokeWidth * pixelWidth);

		view.setFill(Color.WHITE);
		view.fillCircleCentered(pBrocard[0], rPoint * pixelWidth);
	}

	private void drawPolygon(View view) {
		if (showDiagramSkeleton) return;
		view.setLineWidth(strokeWidth * pixelWidth);
		view.setStroke(Color.BLACK);
		view.setLineJoin(StrokeLineJoin.ROUND);
		view.strokePolygon(polygon);
	}


	private void drawVisibilityCells(View view) {
		if (!polygonMode) return;

		view.setLineWidth(strokeWidth * pixelWidth / 2);
		view.setStroke(Color.gray(0, 0.5));

		for (int iq = 0; iq < state.n; iq++) {
			Vector q = polygon.v(iq);
			Vector pq = polygon.e(iq-1).d();
			Vector qr = polygon.e(iq).d();
			if (pq.cross(qr) >= 0) {
				continue;
			}
			// reflex vertex

			for (int io = 0; io < state.n; io++) {
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

		for (int k = 0; k < state.n; k++) {
			view.setFill(colorFill(k, state.enabled[k]));
			view.fillCircleCentered(state.points[k], rPoint * pixelWidth);

			view.setStroke(colorStroke(k, state.enabled[k], k == kSelected));
			view.strokeCircleCentered(state.points[k], rPoint * pixelWidth);
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
			for (int i0 = 0; i0 < state.n; i0++) {
				if (state.enabled[i0]) {
					for (int i1 = 0; i1 < i0; i1++) {
						if (state.enabled[i1]) {
							stroke(view, dominanceFor(i0, i1));
						}
					}
				}
			}
		} else {
			if (state.enabled[kSelected]) {
				for (int i = 0; i < state.n; i++) {
					if (i != kSelected && state.enabled[i]) {
						view.setStroke(colorStrokeRay(i, true, false));
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
				"    h               - Toggle show help",
				"    e               - Toggle if the ray originating near the pointer is enabled",
				"    F2              - Show RVD for oriented rays",
				"    F5              - Show RVD for unoriented rays",
				"    F3              - Show RVD for lines",
				"    F4              - Show Disk Diagram",
				"    y               - Toggle polygon mode",
				"    d               - Toggle show diagram",
				"    k               - Toggle show diagram skeleton",
				"    b               - Toggle show points of the maximum angle",
				"    s               - Toggle show distance shading",
				"    l               - Toggle color regions",
				"    p               - Toggle show sites",
				"    r               - Toggle show rays",
				"    c               - Toggle show circles",
				"    x               - Toggle show polygon exterior",
				"    v               - Toggle show visibility cells",
				"    F8              - Toggle grid",
				"    g               - Toggle snap to grid",
				"    Mouse left      - Select a ray; Move the initial point of the selected ray",
				"    Mouse right     - Set the angle of the selected ray",
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

	private void updateDrawInvalidationState(View view) {
		if (!dataString.equals(lastDataString)) {
			stringToData(dataString);
		} else {
			diagramChanged |= !lastNativeBox.equals(view.nativeBox());
			diagramChanged |= !lastTransformation.equals(view.transformation());
		}
	}

	private void syncFrameState(View view) {
		lastDataString = dataString = dataAsString();
		lastNativeBox = view.nativeBox();
		lastTransformation = view.transformation();
		diagramChanged = false;
	}

	private void drawVisibleLayers(View view) {
		if (showDiagram        ) drawDiagram(view, diagramChanged);
		if (showBrocardPoint   ) drawBrocardPoint(view);
		if (polygonMode        ) drawPolygon(view);
		if (showVisibilityCells) drawVisibilityCells(view);
		if (showCircles        ) drawCircles(view);
		if (showRays           ) drawRays(view);
		if (showPoints         ) drawPoints(view);
		if (showHelp           ) showHelp(view);
	}


	@Override
	public void valuesChanged() {
		diagramChanged = true;
	}




	@Override
	public void draw(View view) {
		view.addTransformation(camera.getTransformation());

		updateDrawInvalidationState(view);

		pixelWidth = 1.0 / view.transformation().getScale();
		rvdColorBackground = new RVDColor(colorBackground);

		DrawingUtils.clear(view, Color.gray(0.9));
		drawVisibleLayers(view);

		syncFrameState(view);
	}


	private int nearestK(Vector p, double rLimit) {
		int bestK = -1;
		double bestD = Double.POSITIVE_INFINITY;

		for (int k = 0; k < state.n; k++) {
			double d = p.distanceTo(state.points[k]);
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

	private void updateSelectionStart(InputEvent event, Vector pointerWorld) {
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
	}

	private void updateDraggingState(InputState inputState, Vector pointerWorld) {
		if (inputState.mouseButtonPressed(1)) {
			if (draggingStartPoint != null && pointerWorld.distanceTo(draggingStartPoint) > draggingMinDistance * pixelWidth) {
				dragging = true;
			}
		} else {
			dragging = false;
		}
	}

	private void applyPointerEdits(InputState inputState, Vector p) {
		if (dragging && kSelected >= 0) {
			this.state.points[kSelected] = p;
			diagramChanged = true;
		}

		if (inputState.mouseButtonPressed(3) && kSelected >= 0) {
			Vector d = p.sub(this.state.points[kSelected]);
			this.state.angles[kSelected] = d.angle() - this.state.rotate;
			diagramChanged = true;
		}
	}

	private void applySiteKeys(InputEvent event, Vector pointerWorld) {
		if (event.isKeyPress(KeyCode.E)) {
			int k = nearestK(pointerWorld, mouseReach * pixelWidth);
			if (k >= 0) {
				this.state.enabled[k] ^= true;
				diagramChanged = true;
			}
		}

		if (event.isKeyPress(KeyCode.N)) {
			for (int i = 0; i < this.state.n; i++) {
				this.state.points[i] = Vector.polar(i % 2 == 0 ? 350 : 150, 1.0 * i / this.state.n);
			}
			diagramChanged = true;
		}
	}

	private void applyToggleKeys(InputEvent event) {
		if (event.isKeyPress(KeyCode.G))   snapToGrid               ^= true;
		if (event.isKeyPress(KeyCode.C))   showCircles              ^= true;
		if (event.isKeyPress(KeyCode.R))   showRays                 ^= true;
		if (event.isKeyPress(KeyCode.P))   showPoints               ^= true;
		if (event.isKeyPress(KeyCode.K))   showDiagramSkeleton      ^= true;
		if (event.isKeyPress(KeyCode.B))   showBrocardPoint         ^= true;
		if (event.isKeyPress(KeyCode.V))   showVisibilityCells      ^= true;
		if (event.isKeyPress(KeyCode.H))   showHelp                 ^= true;
		if (event.isKeyPress(KeyCode.D)) { showDiagram              ^= true; diagramChanged |= showDiagram; }
		if (event.isKeyPress(KeyCode.L)) { showColor                ^= true; diagramChanged = true; }
		if (event.isKeyPress(KeyCode.S)) { showShading              ^= true; diagramChanged = true; }
		if (event.isKeyPress(KeyCode.Y)) { polygonMode              ^= true; diagramChanged = true; }
		if (event.isKeyPress(KeyCode.X)) { showPolygonExterior      ^= true; diagramChanged = true; }
	}

	private void applyDiagramModeKeys(InputEvent event) {
		if (event.isKeyPress(KeyCode.F2)) { diagram = DiagramType.RVD_RAYS_ORIENTED  ; diagramChanged = true; }
		if (event.isKeyPress(KeyCode.F3)) { diagram = DiagramType.RVD_LINES          ; diagramChanged = true; }
		if (event.isKeyPress(KeyCode.F4)) { diagram = DiagramType.RVD_RAYS_UNORIENTED; diagramChanged = true; }
		if (event.isKeyPress(KeyCode.F5)) { diagram = DiagramType.DISK_DIAGRAM       ; diagramChanged = true; }
	}

	private void handleEditorInput(InputEvent event, InputState inputState, Vector pointerWorld) {
		Vector p = snapToGrid ? pointerWorld.round(gridCellD) : pointerWorld;

		updateSelectionStart(event, pointerWorld);
		updateDraggingState(inputState, pointerWorld);
		applyPointerEdits(inputState, p);
		applySiteKeys(event, pointerWorld);
		applyToggleKeys(event);
		applyDiagramModeKeys(event);
	}


	@Override
	public void receiveEvent(View view, InputEvent event, InputState state, Vector pointerWorld, Vector pointerViewBase) {
		if (state.keyPressed(KeyCode.CONTROL)) {
			camera.receiveEvent(view, event, state, pointerWorld, pointerViewBase);
			return;
		}
		handleEditorInput(event, state, pointerWorld);
	}




	public static void main(String[] args) {
		Options options = new Options();
/*
		options.redrawOnPulse = false;
		options.redrawOnInput = true;
		options.redrawOnGadgetValueChange = true;
		options.redrawOnResize = true;
		options.redrawOnOSDChange = true;
*/
		options.windowTitle = "RVD Explorer";
		options.drawingSize = RVDExplorer.sizeInitial;
		options.gridSubdivision = 8;
		options.gridInterval = gridCellD.x() * options.gridSubdivision;
		options.gridColor = Color.gray(1, 0.125);
		DrawingApplication.launch(options);

//		RenderingApplication.launch("c:/animations", 1920, 1080, 60);
	}

}
