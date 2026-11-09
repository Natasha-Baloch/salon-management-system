package com.salon;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import javax.sound.sampled.*;
import java.io.BufferedInputStream;
import java.io.InputStream;

/**
 * Front-page background: the salon video, played as a looping frame sequence (resources /video/intro/f0001.jpg ...)
 * together with its sound (resources /video/intro/audio.wav, looped in sync). No extra libraries needed. It fills the
 * panel "cover" style, and the video and music pause automatically while the page is not visible.
 * If the frames are missing it falls back to the still salon picture.
 */
public class VideoPanel extends ImagePanel {
    private static final String DIR = "/video/intro/";
    private static final int FPS = 20;

    private volatile BufferedImage frame;
    private volatile boolean running = true;
    private volatile boolean muted = false;
    private Clip clip;
    private float mx = 0.5f, my = 0.5f, sx = 0.5f, sy = 0.5f;   // target and smoothed mouse position (0..1)

    public VideoPanel(LayoutManager lm) {
        super(lm, true);
        int count = frameCount();
        if (count <= 0) return;
        MouseAdapter m = new MouseAdapter() {
            @Override public void mouseMoved(MouseEvent e) { mx = e.getX() / (float) Math.max(1, getWidth()); my = e.getY() / (float) Math.max(1, getHeight()); }
        };
        addMouseMotionListener(m);
        Thread t = new Thread(() -> play(count), "welcome-video");
        t.setDaemon(true);
        t.start();
    }

    private static int frameCount() {
        try (InputStream in = VideoPanel.class.getResourceAsStream(DIR + "count.txt")) {
            return in == null ? 0 : Integer.parseInt(new String(in.readAllBytes()).trim());
        } catch (Exception e) { return 0; }
    }

    private void play(int count) {
        int i = 1;
        long next = System.nanoTime();
        boolean audioOn = false;
        while (running) {
            try {
                boolean show = isShowing();
                if (show && !audioOn) { i = 1; startAudio(); audioOn = true; }
                else if (!show && audioOn) { stopAudio(); audioOn = false; }
                if (audioOn && clip != null) {
                    if (muted) { if (clip.isRunning()) clip.stop(); }
                    else if (!clip.isRunning()) { clip.setFramePosition((int) (((i - 1) / (double) FPS * clip.getFormat().getFrameRate()) % Math.max(1, clip.getFrameLength()))); clip.loop(Clip.LOOP_CONTINUOUSLY); }
                }
                if (show) {
                    try (InputStream in = VideoPanel.class.getResourceAsStream(String.format("%sf%04d.jpg", DIR, i))) {
                        if (in != null) frame = ImageIO.read(in);
                    }
                    SwingUtilities.invokeLater(this::repaint);
                    i = i % count + 1;
                }
                next += 1_000_000_000L / FPS;
                long wait = (next - System.nanoTime()) / 1_000_000L;
                if (wait > 0) Thread.sleep(wait); else next = System.nanoTime();
                if (!isShowing()) Thread.sleep(250);
            } catch (InterruptedException e) { return; }
            catch (Exception ignored) { }
        }
    }

    private void startAudio() {
        try {
            if (clip == null) {
                InputStream raw = VideoPanel.class.getResourceAsStream(DIR + "audio.wav");
                if (raw == null) return;
                try (AudioInputStream ais = AudioSystem.getAudioInputStream(new BufferedInputStream(raw))) {
                    Clip c = AudioSystem.getClip();
                    c.open(ais);
                    clip = c;
                }
            }
            clip.setFramePosition(0);
            if (!muted) clip.loop(Clip.LOOP_CONTINUOUSLY);
        } catch (Exception ignored) { clip = null; }   // no sound device: the video simply plays silently
    }

    private void stopAudio() { if (clip != null) clip.stop(); }

    public boolean isMuted() { return muted; }

    public void setMuted(boolean m) { muted = m; }

    @Override public void removeNotify() {
        running = false;
        if (clip != null) { clip.stop(); clip.close(); }
        super.removeNotify();
    }

    @Override protected void paintComponent(Graphics g) {
        BufferedImage f = frame;
        if (f == null) { super.paintComponent(g); return; }
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        int w = getWidth(), h = getHeight();
        // cover-fit with a little zoom so the mouse drift has room to move
        int sh = f.getHeight();
        double zoom = 1.07;
        double s = Math.max((double) w / f.getWidth(), (double) h / sh) * zoom;
        int iw = (int) Math.ceil(f.getWidth() * s), ih = (int) Math.ceil(sh * s);
        sx += (mx - sx) * 0.08f; sy += (my - sy) * 0.08f;
        int x = (int) ((w - iw) * (0.5 + (sx - 0.5) * 0.9)), y = (int) ((h - ih) * (0.5 + (sy - 0.5) * 0.9));
        g2.drawImage(f, x, y, x + iw, y + ih, 0, 0, f.getWidth(), sh, null);
        // dark rose fade on the left (light, keeps the video clear) + soft vignette
        g2.setPaint(new GradientPaint(0, 0, new Color(40, 14, 28, 150), w * 0.62f, 0, new Color(40, 14, 28, 0)));
        g2.fillRect(0, 0, w, h);
        g2.setPaint(new GradientPaint(0, h * 0.7f, new Color(0, 0, 0, 0), 0, h, new Color(40, 10, 25, 60)));
        g2.fillRect(0, 0, w, h);
        g2.dispose();
        if (Math.abs(mx - sx) > 0.002 || Math.abs(my - sy) > 0.002) repaint();
    }
}
