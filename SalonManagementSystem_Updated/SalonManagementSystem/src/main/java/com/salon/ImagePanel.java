package com.salon;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;

/** Paints the salon reference image "cover" style (fills the panel, keeps proportions) with a rose overlay. */
public class ImagePanel extends JPanel {
    private static BufferedImage image;
    private final boolean leftShade;

    public ImagePanel(LayoutManager lm, boolean leftShade) {
        super(lm);
        this.leftShade = leftShade;
        setOpaque(true);
        if (image == null) {
            try (var in = ImagePanel.class.getResourceAsStream("/images/salon.png")) {
                if (in != null) image = ImageIO.read(in);
            } catch (IOException ignored) { }
        }
    }

    @Override protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        int w = getWidth(), h = getHeight();
        if (image != null) {
            double s = Math.max((double) w / image.getWidth(), (double) h / image.getHeight());
            int iw = (int) Math.ceil(image.getWidth() * s), ih = (int) Math.ceil(image.getHeight() * s);
            g2.drawImage(image, (w - iw) / 2, (h - ih) / 2, iw, ih, null);
        } else {
            g2.setPaint(new GradientPaint(0, 0, Theme.BG, w, h, Theme.LAVENDER));
            g2.fillRect(0, 0, w, h);
        }
        if (leftShade) {   // dark rose fade on the left so text stays readable while the room stays visible on the right
            g2.setPaint(new GradientPaint(0, 0, new Color(96, 36, 64, 225), w * 0.75f, 0, new Color(96, 36, 64, 20)));
        } else {
            g2.setPaint(new Color(96, 36, 64, 150));
        }
        g2.fillRect(0, 0, w, h);
        g2.dispose();
    }
}
