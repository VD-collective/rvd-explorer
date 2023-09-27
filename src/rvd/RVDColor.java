package rvd;

import javafx.scene.paint.Color;

public class RVDColor {
	public static RVDColor BLACK = new RVDColor(0.0);
	public static RVDColor GRAY  = new RVDColor(0.2);
	public static RVDColor WHITE = new RVDColor(1.0);
	
	private final int r, g, b;
	
	
	
	public RVDColor(int r, int g, int b) {
		this.r = r;
		this.g = g;
		this.b = b;
	}
	
	
	public RVDColor() {
		this.r = 0;
		this.g = 0;
		this.b = 0;
	}
	
	
	public RVDColor(double r, double g, double b) {
		this.r = (int) (255 * r);
		this.g = (int) (255 * g);
		this.b = (int) (255 * b);
	}
	
	
	public RVDColor(double gray) {
		this(gray, gray, gray);
	}
	
	public RVDColor(Color c) {
		this(c.getRed(), c.getGreen(), c.getBlue());
	}
	
	
	public RVDColor add(RVDColor o) {
		return new RVDColor(r + o.r, g + o.g, b + o.b);
	}
	
	public RVDColor mul(double c) {
		return new RVDColor(
				(int) (r * c),
				(int) (g * c),
				(int) (b * c)
		);
	}
	
	
	public int code() {
		return
				(0xFF000000) |
						(   r << 16) |
						(   g <<  8) |
						(   b      );
	}
	
}