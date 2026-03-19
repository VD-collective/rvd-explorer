package rvd.render;

import xyz.marsavic.drawingfx.drawing.DrawingUtils;
import xyz.marsavic.drawingfx.drawing.View;

public class HelpOverlayDrawer {

    public void draw(View view) {
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
}
