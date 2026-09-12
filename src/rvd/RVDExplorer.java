package rvd;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import rvd.core.DiagramPreparation;
import rvd.core.DominanceRegionFactory;
import rvd.core.DiskCellSelector;
import rvd.core.NearestCellClassifier;
import rvd.core.PolygonVisibility;
import rvd.io.ExplorerFileIo;
import rvd.io.ExplorerJsonCodec;
import rvd.io.ExplorerJsonException;
import rvd.model.ExplorerInstance;
import rvd.model.ExplorerState;
import rvd.model.ExplorerViewSettings;
import rvd.render.BrocardTracker;
import rvd.render.DiagramFrameCoordinator;
import rvd.render.HelpOverlayDrawer;
import rvd.render.OverlayDrawer;
import rvd.render.RasterDiagramRenderer;
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

import java.io.File;
import java.io.IOException;


public class RVDExplorer implements Drawing {
	public static final Vector sizeInitial = Vector.xy(800, 800);
	public static final Vector gridCellD = Vector.xy(16, 16);

	private final int maxN = 64;

	/** Former Base64 datastring default, converted to JSON sites + current view defaults. */
private static final String DEFAULT_INSTANCE_JSON = """
		{
		  "version": "1",
		  "rotate": 0.0,
  "n": 7,
  "sites": [
    {
      "x": 1.0,
      "y": 0.0,
      "angle": 0.5957614938397598,
      "enabled": true
    },
    {
      "x": 3.0,
      "y": 5.196152422706632,
      "angle": 0.5957614938397598,
      "enabled": true
    },
    {
      "x": -3.0,
      "y": 5.196152422706632,
      "angle": 0.8333333333333333,
      "enabled": true
    },
    {
      "x": -6.0,
      "y": 0.0,
      "angle": 0.0,
      "enabled": true
    },
    {
      "x": -3.0,
      "y": -5.196152422706632,
      "angle": 0.1666666666666667,
      "enabled": true
    },
    {
      "x": 3.0,
      "y": -5.196152422706632,
      "angle": 0.3333333333333333,
      "enabled": true
    },
    {
      "x": 6.0,
      "y": 0.0,
      "angle": 0.5833333333333333,
      "enabled": true
    }
  ],
		  "diagramType": "RVD_RAYS_ORIENTED",
		  "polygonMode": true,
		  "brocardIllumination": false,
		  "showPolygonExterior": false,
		  "showVisibilityCells": false,
		  "stopAngle1": 1.0,
		  "stopAngle2": 1.0
		}
		""";

	@GadgetBoolean
	@Properties(name = "Help (h)")
	boolean showHelp = false;

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
	boolean showShading = false;

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
	@Properties(name = "Snap to grid (g)")
	boolean snapToGrid = false;

	@GadgetColorPicker
	@Properties(name = "Background color")
	Color colorBackground = Color.gray(0.2);

	@GadgetBoolean
	@Properties(name = "Polygon mode (y)")
	boolean polygonMode = true;

	@GadgetBoolean
	@Properties(name = "Edge-aligned rays (a)")
	boolean brocardIllumination = true;

	@GadgetBoolean
	@Properties(name = "Show polygon exterior (x)")
	boolean showPolygonExterior = false;

	@GadgetBoolean
	@Properties(name = "Show visibility cells (v)")
	boolean showVisibilityCells = false;

	@GadgetBoolean
	@Properties(name = "Show visibility cells depth")
	boolean visibilityCellsShadingCount = false;

	double[] hues = new double[maxN];
	int kSelected = -1;

	private Polygon polygon;

	RVDColor rvdColorBackground;

	private final BrocardTracker brocardTracker = new BrocardTracker();
	private final DiagramFrameCoordinator diagramFrameCoordinator = new DiagramFrameCoordinator();
	private final RasterDiagramRenderer rasterDiagramRenderer = new RasterDiagramRenderer();


	CameraSimple camera = new CameraSimple(F_R_R.cutoff01(t -> F_R_R.power(t, 8)));
	double pixelWidth;

	// After FileChooser, Control can stay pressed in InputState; ignore it for camera until released.
	private boolean ignoreControlModifierForCamera;
	private Alert activeErrorAlert = null;
	private String activeErrorDialogKey = null;


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

		try {
			applyInstance(ExplorerJsonCodec.decode(DEFAULT_INSTANCE_JSON));
		} catch (ExplorerJsonException e) {
			throw new IllegalStateException("Default instance JSON is invalid", e);
		}
	}


	private final double strokeWidth = 3;
	private final double rPoint = 6.0;

	private double hue(int k) {
		return 360 * k * Numeric.PHI;
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
		if (ia.i == -3) return RVDColor.BLACK;
		if (ia.i == -4) return rvdColorBackground;
		double b = showShading ? 0.9 - 0.6 * ia.a : 1.0;
		if (visibilityCellsShadingCount) b *= (double) ia.nVisible / state.n;
		return showColor ? colorsDiagram[ia.i].mul(b) : new RVDColor(b);
	}

	private record PointResult(int i, int nVisible, double a) {
		// i = -1    In the domain, but on the skeleton
		// i = -2    In the domain, but inside the aperture
		// i = -3    Out of the domain, but on the skeleton
		// i = -4    Out of the domain
	}

	private static final double rEdge = 1.6;
	private static final int nNeighbors = 6;

	private PointResult findNearest(Vector p, Ray[] rays) {
		PointResult iaCenter = findNearest_(p, rays);

		if (showDiagramSkeleton) {
			for (int k = 0; k < nNeighbors; k++) {
				Vector q = Vector.polar(rEdge, (double) k / nNeighbors).add(p);
				PointResult iaNeighbor = findNearest_(q, rays);
				if (iaNeighbor.i != iaCenter.i) {
					return new PointResult((iaCenter.i == -4) ? -3 : -1, iaCenter.nVisible, iaCenter.a);
				}
			}
		}

		return iaCenter;
	}

	private PointResult findNearest_(Vector p, Ray[] rays) {
		int[] vis = PolygonVisibility.visibleVertices(
				p,
				state.points,
				state.n,
				polygon,
				polygonMode,
				showPolygonExterior
		);

		NearestCellClassifier.Result nearest = NearestCellClassifier.classify(
				p,
				rays,
				state.enabled,
				vis,
				diagram == DiagramType.RVD_LINES,
				diagram == DiagramType.RVD_RAYS_UNORIENTED,
				stopAngle1 * stopAngle2
		);

		return new PointResult(nearest.index(), nearest.visibleCount(), nearest.angle());
	}


	private PointResult findDDCell(Vector p, Figure[][] dominances) {
		int k = DiskCellSelector.select(p, dominances, state.enabled, state.n);
		return new PointResult(k, 0, 0.0);
	}


	Figure dominanceFor(int i0, int i1) {
		return dominanceFor(i0, i1, state.angles);
	}


	Figure dominanceFor(int i0, int i1, double[] angles) {
		return DominanceRegionFactory.create(
				state.points[i0],
				state.points[i1],
				angles[i0],
				angles[i1]
		);
	}


	private void resetBrocardSearch() {
		brocardTracker.reset();
	}

	private double[] computeEffectiveAngles() {
		double[] effectiveAngles = new double[state.n];
		if (polygonMode && brocardIllumination) {
			for (int i = 0; i < state.n; i++) {
				effectiveAngles[i] = state.points[(i + 1) % state.n].sub(state.points[i]).angle();
			}
		} else {
			System.arraycopy(state.angles, 0, effectiveAngles, 0, state.n);
		}
		return effectiveAngles;
	}

	private PointResult classifyPoint(Vector p, Figure[][] dominanceRegion, Ray[] rays) {
		return (diagram == DiagramType.DISK_DIAGRAM)
				? findDDCell(p, dominanceRegion)
				: findNearest(p, rays);
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
		if (sizeX == 0 || sizeY == 0) {
			return null;
		}

		double[] effectiveAngles = computeEffectiveAngles();
		DiagramPreparation.PreparedData prepared = DiagramPreparation.prepare(
				state.points,
				effectiveAngles,
				state.n,
				state.rotate,
				polygonMode,
				(i0, i1, angles) -> dominanceFor(i0, i1, angles)
		);
		polygon = prepared.polygon();
		Ray[] rays = prepared.rays();
		Figure[][] dominanceRegion = prepared.dominanceRegion();

		resetBrocardSearch();
		return rasterDiagramRenderer.render(
				tFromPixels,
				bImage,
				p -> {
					PointResult ia = classifyPoint(p, dominanceRegion, rays);
					return new RasterDiagramRenderer.Classification(ia.i, ia.nVisible, ia.a);
				},
				classification -> colorDiagram(new PointResult(
						classification.index(),
						classification.visibleCount(),
						classification.angle()
				)),
				(classification, p) -> brocardTracker.observe(classification.index(), classification.angle(), p)
		);
	}



	private void updateDrawInvalidationState(View view) {
		diagramFrameCoordinator.updateInvalidationState(view);
	}

	private void syncFrameState(View view) {
		diagramFrameCoordinator.syncFrameState(view);
	}

	private ExplorerInstance captureInstance() {
		return new ExplorerInstance(state.snapshot(), new ExplorerViewSettings(
				diagram,
				polygonMode,
				brocardIllumination,
				showPolygonExterior,
				showVisibilityCells,
				stopAngle1,
				stopAngle2
		));
	}

	private void applyInstance(ExplorerInstance instance) {
		state.applySnapshot(instance.snapshot());
		ExplorerViewSettings view = instance.view();
		diagram = view.diagramType();
		polygonMode = view.polygonMode();
		brocardIllumination = view.brocardIllumination();
		showPolygonExterior = view.showPolygonExterior();
		showVisibilityCells = view.showVisibilityCells();
		stopAngle1 = view.stopAngle1();
		stopAngle2 = view.stopAngle2();
		diagramFrameCoordinator.markDirty();
	}

	private void drawVisibleLayers(View view) {
		double[] effectiveAngles = computeEffectiveAngles();
		OverlayDrawer.Context overlayContext = new OverlayDrawer.Context(
				state,
				effectiveAngles,
				hues,
				kSelected,
				polygon,
				polygonMode,
				showDiagramSkeleton,
				pixelWidth,
				strokeWidth,
				rPoint
		);

		if (showDiagram        ) diagramFrameCoordinator.drawDiagram(view, this::makeImage);
		if (polygonMode        ) OverlayDrawer.drawPolygon(view, overlayContext);
		if (showVisibilityCells) OverlayDrawer.drawVisibilityCells(view, overlayContext);
		if (showCircles        ) OverlayDrawer.drawCircles(view, overlayContext, (i0, i1) -> dominanceFor(i0, i1, effectiveAngles));
		if (showRays           ) OverlayDrawer.drawRays(view, overlayContext);
		if (showPoints         ) OverlayDrawer.drawPoints(view, overlayContext);
		if (showBrocardPoint   ) OverlayDrawer.drawBrocardPoint(view, brocardTracker.point(), overlayContext);
		if (showHelp           ) HelpOverlayDrawer.draw(view);
	}


	@Override
	public void valuesChanged() {
		diagramFrameCoordinator.markDirty();
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
			diagramFrameCoordinator.markDirty();
		}

		if (inputState.mouseButtonPressed(3) && kSelected >= 0 && !(polygonMode && brocardIllumination)) {
			Vector d = p.sub(this.state.points[kSelected]);
			this.state.angles[kSelected] = d.angle() - this.state.rotate;
			diagramFrameCoordinator.markDirty();
		}
	}

	private void applySiteKeys(InputEvent event, Vector pointerWorld) {
		if (event.isKeyPress(KeyCode.E)) {
			int k = nearestK(pointerWorld, mouseReach * pixelWidth);
			if (k >= 0) {
				this.state.enabled[k] ^= true;
				diagramFrameCoordinator.markDirty();
			}
		}

		if (event.isKeyPress(KeyCode.N)) {
			for (int i = 0; i < this.state.n; i++) {
				this.state.points[i] = Vector.polar(i % 2 == 0 ? 350 : 150, 1.0 * i / this.state.n);
			}
			diagramFrameCoordinator.markDirty();
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
		if (event.isKeyPress(KeyCode.D)) { showDiagram              ^= true; if (showDiagram) diagramFrameCoordinator.markDirty(); }
		if (event.isKeyPress(KeyCode.L)) { showColor                ^= true; diagramFrameCoordinator.markDirty(); }
		if (event.isKeyPress(KeyCode.S)) { showShading              ^= true; diagramFrameCoordinator.markDirty(); }
		if (event.isKeyPress(KeyCode.Y)) { polygonMode              ^= true; diagramFrameCoordinator.markDirty(); }
		if (event.isKeyPress(KeyCode.A)) { brocardIllumination      ^= true; diagramFrameCoordinator.markDirty(); }
		if (event.isKeyPress(KeyCode.X)) { showPolygonExterior      ^= true; diagramFrameCoordinator.markDirty(); }
	}

	private void applyDiagramModeKeys(InputEvent event) {
		if (event.isKeyPress(KeyCode.F2)) { diagram = DiagramType.RVD_RAYS_ORIENTED  ; diagramFrameCoordinator.markDirty(); }
		if (event.isKeyPress(KeyCode.F3)) { diagram = DiagramType.RVD_LINES          ; diagramFrameCoordinator.markDirty(); }
		if (event.isKeyPress(KeyCode.F4)) { diagram = DiagramType.RVD_RAYS_UNORIENTED; diagramFrameCoordinator.markDirty(); }
		if (event.isKeyPress(KeyCode.F5)) { diagram = DiagramType.DISK_DIAGRAM       ; diagramFrameCoordinator.markDirty(); }
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
		if (ignoreControlModifierForCamera
				&& (!state.keyPressed(KeyCode.CONTROL) || event.isKeyRelease(KeyCode.CONTROL))) {
			ignoreControlModifierForCamera = false;
		}

		if (event.isKeyPress(KeyCode.S) && state.keyPressed(KeyCode.CONTROL)) {
			saveInstanceToFile();
			return;
		}
		if (event.isKeyPress(KeyCode.O) && state.keyPressed(KeyCode.CONTROL)) {
			loadInstanceFromFile();
			return;
		}

		boolean controlForCamera = state.keyPressed(KeyCode.CONTROL) && !ignoreControlModifierForCamera;
		if (controlForCamera && !event.isKey()) {
			camera.receiveEvent(view, event, state, pointerWorld, pointerViewBase);
			return;
		}
		handleEditorInput(event, state, pointerWorld);
	}

	/** FileChooser needs a window; any showing JavaFX window is enough. */
	private static Window firstShowingWindow() {
		for (Window w : Window.getWindows()) {
			if (w.isShowing()) {
				return w;
			}
		}
		return null;
	}

	private void afterNativeFileDialog() {
		ignoreControlModifierForCamera = true;
		Window w = firstShowingWindow();
		if (w != null) {
			Platform.runLater(w::requestFocus);
		}
	}

	private void showErrorDialog(String header, String message) {
		Platform.runLater(() -> {
			String dialogKey = header + "\n" + message;
			if (activeErrorAlert != null && activeErrorAlert.isShowing()) {
				if (dialogKey.equals(activeErrorDialogKey)) {
					focusActiveErrorAlert();
					return;
				}
				activeErrorAlert.setHeaderText(header);
				activeErrorAlert.setContentText(message);
				activeErrorDialogKey = dialogKey;
				focusActiveErrorAlert();
				return;
			}

			Alert alert = new Alert(Alert.AlertType.ERROR);
			alert.setTitle("RVD Explorer");
			alert.setHeaderText(header);
			alert.setContentText(message);
			Window owner = firstShowingWindow();
			if (owner != null) {
				alert.initOwner(owner);
			}
			alert.setOnHidden(event -> {
				if (activeErrorAlert == alert) {
					activeErrorAlert = null;
					activeErrorDialogKey = null;
				}
			});
			activeErrorAlert = alert;
			activeErrorDialogKey = dialogKey;
			alert.show();
		});
	}

	private void focusActiveErrorAlert() {
		if (activeErrorAlert == null) {
			return;
		}
		Window window = activeErrorAlert.getDialogPane().getScene() == null
				? null
				: activeErrorAlert.getDialogPane().getScene().getWindow();
		if (window != null) {
			window.requestFocus();
		}
	}

	private void saveInstanceToFile() {
		FileChooser chooser = new FileChooser();
		chooser.setTitle("Save instance");
		chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON instance", "*.json"));
		File file = chooser.showSaveDialog(firstShowingWindow());
		afterNativeFileDialog();
		if (file == null) {
			return;
		}
		if (!file.getName().toLowerCase().endsWith(".json")) {
			file = new File(file.getParentFile(), file.getName() + ".json");
		}
		try {
			ExplorerFileIo.save(file.toPath(), captureInstance());
		} catch (IOException e) {
			showErrorDialog("Save failed", e.getMessage());
			System.err.println("Save failed: " + e.getMessage());
		}
	}

	private void loadInstanceFromFile() {
		FileChooser chooser = new FileChooser();
		chooser.setTitle("Load instance");
		chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON instance", "*.json"));
		File file = chooser.showOpenDialog(firstShowingWindow());
		afterNativeFileDialog();
		if (file == null) {
			return;
		}
		try {
			applyInstance(ExplorerFileIo.load(file.toPath()));
			kSelected = -1;
		} catch (ExplorerJsonException e) {
			showErrorDialog("Load failed", e.getMessage());
			System.err.println("Load failed: " + e.getMessage());
		} catch (IOException e) {
			showErrorDialog("Load failed", e.getMessage());
			System.err.println("Load failed: " + e.getMessage());
		}
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
