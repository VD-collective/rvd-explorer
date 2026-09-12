package rvd.render;

import javafx.scene.image.Image;
import xyz.marsavic.drawingfx.drawing.View;
import xyz.marsavic.geometry.Box;
import xyz.marsavic.geometry.Transformation;

public class DiagramFrameCoordinator {

    @FunctionalInterface
    public interface ImageProducer {
        Image produce(Transformation tFromPixels, Box bImage);
    }

    private Box lastNativeBox = Box.ZERO;
    private Transformation lastTransformation = Transformation.IDENTITY;
    private boolean diagramChanged = true;
    private Image imgDiagram;

    public void markDirty() {
        diagramChanged = true;
    }

    public void updateInvalidationState(View view) {
        diagramChanged |= !lastNativeBox.equals(view.nativeBox());
        diagramChanged |= !lastTransformation.equals(view.transformation());
    }

    public void drawDiagram(View view, ImageProducer imageProducer) {
        Box b = view.nativeBox().positive();
        Transformation t = view.transformation();

        if (diagramChanged) {
            imgDiagram = imageProducer.produce(t.inverse(), b);
        }

        view.setTransformation(Transformation.IDENTITY);
        view.drawImage(b, imgDiagram);
        view.setTransformation(t);
    }

    public void syncFrameState(View view) {
        lastNativeBox = view.nativeBox();
        lastTransformation = view.transformation();
        diagramChanged = false;
    }
}
