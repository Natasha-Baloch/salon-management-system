package com.salon;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;

/** Picking pictures from the computer (file browser), shrinking them for the database, and drawing thumbnails. */
public final class ImageUtil {
    private static final Color[][] PALETTE = {
        {new Color(244, 194, 214), new Color(214, 132, 160)}, {new Color(234, 224, 246), new Color(183, 150, 214)},
        {new Color(255, 218, 200), new Color(226, 150, 130)}, {new Color(252, 214, 228), new Color(196, 112, 142)},
        {new Color(226, 232, 250), new Color(150, 160, 214)}, {new Color(250, 226, 214), new Color(183, 110, 121)}
    };

    private ImageUtil() {}

    /** Opens the computer's file browser. Returns JPEG bytes (scaled to fit maxW x maxH) or null if cancelled. */
    public static byte[] chooseImage(Component parent, int maxW, int maxH) throws IOException {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Choose a picture");
        fc.setFileFilter(new FileNameExtensionFilter("Pictures (png, jpg, jpeg, gif, bmp)", "png", "jpg", "jpeg", "gif", "bmp"));
        if (fc.showOpenDialog(parent) != JFileChooser.APPROVE_OPTION) return null;
        File f = fc.getSelectedFile();
        if (f.length() > 20L * 1024 * 1024) throw new IllegalArgumentException("That picture is too large (maximum 20 MB).");
        BufferedImage src = ImageIO.read(f);
        if (src == null) throw new IllegalArgumentException("That file is not a picture the app can read. Please choose a PNG, JPG, GIF or BMP.");
        double s = Math.min(1.0, Math.min((double) maxW / src.getWidth(), (double) maxH / src.getHeight()));
        int w = Math.max(1, (int) Math.round(src.getWidth() * s)), h = Math.max(1, (int) Math.round(src.getHeight() * s));
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(out, "jpg", bos);
        return bos.toByteArray();
    }

    public static BufferedImage decode(byte[] data) {
        if (data == null || data.length == 0) return null;
        try { return ImageIO.read(new ByteArrayInputStream(data)); } catch (IOException e) { return null; }
    }

    /** Picture (or a pretty placeholder when there is none) cropped to exactly w x h. */
    public static ImageIcon icon(byte[] data, String title, String category, int w, int h) {
        BufferedImage img = decode(data);
        if (img == null) return new ImageIcon(placeholder(title, category, w, h));
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        double s = Math.max((double) w / img.getWidth(), (double) h / img.getHeight());
        int iw = (int) Math.ceil(img.getWidth() * s), ih = (int) Math.ceil(img.getHeight() * s);
        g.drawImage(img, (w - iw) / 2, (h - ih) / 2, iw, ih, null);
        g.dispose();
        return new ImageIcon(out);
    }

    public static BufferedImage placeholder(String title, String category, int w, int h) {
        Color[] c = PALETTE[Math.abs((category == null ? "" : category).hashCode()) % PALETTE.length];
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setPaint(new GradientPaint(0, 0, c[0], w, h, c[1]));
        g.fillRect(0, 0, w, h);
        g.setColor(new Color(255, 255, 255, 70));
        g.fillOval(-h / 4, -h / 3, h, h);
        g.fillOval(w - h / 2, h / 2, h, h);
        g.setColor(new Color(255, 255, 255, 235));
        String initial = title == null || title.isBlank() ? "✿" : title.trim().substring(0, 1).toUpperCase();
        g.setFont(Theme.serif(Font.BOLD, Math.max(24, h / 2)));
        FontMetrics fm = g.getFontMetrics();
        g.drawString(initial, (w - fm.stringWidth(initial)) / 2, h / 2 + fm.getAscent() / 3);
        if (category != null && !category.isBlank()) {
            g.setFont(new Font("SansSerif", Font.BOLD, Math.max(10, h / 12)));
            fm = g.getFontMetrics();
            String cat = category.toUpperCase();
            g.drawString(cat, (w - fm.stringWidth(cat)) / 2, h - Math.max(10, h / 10));
        }
        g.dispose();
        return out;
    }
}
