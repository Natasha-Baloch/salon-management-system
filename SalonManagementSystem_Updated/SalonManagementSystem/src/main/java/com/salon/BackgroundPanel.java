package com.salon;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.util.Random;

/**
 * A JPanel that paints a colorful, "salon glam" backdrop instead of a flat
 * color: a soft diagonal pink/purple/gold gradient, glowing blurred color
 * blobs, and a scattering of salon-themed sparkle/flower/scissors glyphs.
 * Everything is drawn with Java2D (no external image files needed), using a
 * fixed random seed so the pattern is stable across repaints/resizes instead
 * of flickering.
 */
public class BackgroundPanel extends JPanel {

    private static final String[] GLYPHS = {"✂", "✨", "💇", "💅", "🌸", "💄", "🌺", "✦"};
    private final long seed;

    public BackgroundPanel(LayoutManager layout, long seed) {
        super(layout);
        this.seed = seed;
        setOpaque(true);
    }

    public BackgroundPanel(LayoutManager layout) {
        this(layout, 42L);
    }

    @Override
    protected void paintComponent(Graphics g) {
        int w = getWidth();
        int h = getHeight();
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Base diagonal gradient: soft blush -> lavender -> gold blush
        GradientPaint base = new GradientPaint(
                0, 0, new Color(255, 235, 244),
                w, h, new Color(238, 222, 245));
        g2.setPaint(base);
        g2.fillRect(0, 0, w, h);

        GradientPaint sheen = new GradientPaint(
                0, h, new Color(255, 244, 214, 90),
                w, 0, new Color(255, 210, 235, 90));
        g2.setPaint(sheen);
        g2.fillRect(0, 0, w, h);

        // Soft glowing color blobs, scattered but deterministic
        Random r = new Random(seed);
        Color[] blobColors = {
                new Color(244, 143, 177, 70),   // pink
                new Color(186, 104, 200, 60),   // purple
                new Color(255, 213, 128, 60),   // gold
                new Color(129, 212, 250, 45),   // sky
                new Color(255, 138, 178, 55)    // rose
        };
        int blobCount = Math.max(6, (w * h) / 90000);
        for (int i = 0; i < blobCount; i++) {
            int size = 120 + r.nextInt(260);
            int x = r.nextInt(Math.max(1, w)) - size / 3;
            int y = r.nextInt(Math.max(1, h)) - size / 3;
            g2.setColor(blobColors[r.nextInt(blobColors.length)]);
            g2.fill(new Ellipse2D.Float(x, y, size, size));
        }

        // Scattered faint salon glyphs (scissors, sparkles, flowers, etc.)
        Random gr = new Random(seed + 1);
        g2.setFont(new Font("SansSerif", Font.PLAIN, 26));
        int glyphCount = Math.max(10, (w * h) / 26000);
        for (int i = 0; i < glyphCount; i++) {
            String glyph = GLYPHS[gr.nextInt(GLYPHS.length)];
            int size = 16 + gr.nextInt(30);
            int x = gr.nextInt(Math.max(1, w));
            int y = gr.nextInt(Math.max(1, h));
            g2.setFont(new Font("SansSerif", Font.PLAIN, size));
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.10f));
            g2.drawString(glyph, x, y);
        }

        g2.dispose();
        super.paintComponent(g);
    }
}
