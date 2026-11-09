package com.salon;

import javax.swing.*;
import java.awt.*;
import java.awt.font.TextAttribute;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WelcomePanel extends VideoPanel {
    public WelcomePanel(Runnable onStart) {
        super(new GridBagLayout());
        JPanel col = new JPanel();
        col.setOpaque(false);
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));

        JLabel brand = new Glow("✿  BEAUTY BLOOM SALON");
        brand.setFont(pick(Font.BOLD, 17, 0.22f, "Segoe UI Semibold", "Segoe UI", "Trebuchet MS", "SansSerif"));
        brand.setForeground(GOLD);
        JLabel h1 = new Glow("Welcome to Your");
        JLabel h2 = new Glow("Beauty Destination");
        h1.setFont(pick(Font.BOLD, 54, 0f, "Palatino Linotype", "Georgia", "Serif"));
        h1.setForeground(Color.WHITE);
        h2.setFont(pick(Font.BOLD | Font.ITALIC, 60, 0f, "Palatino Linotype", "Georgia", "Serif"));
        h2.setForeground(GOLD);
        JLabel sub = new Glow("Experience beauty, elegance and professional care.");
        sub.setFont(pick(Font.ITALIC, 23, 0.03f, "Palatino Linotype", "Georgia", "Serif"));
        sub.setForeground(Color.WHITE);
        JButton start = Theme.button("GET STARTED  →", Theme.ROSE_GOLD);
        start.setFont(new Font("SansSerif", Font.BOLD, 16));
        start.setBorder(BorderFactory.createEmptyBorder(15, 44, 15, 44));
        start.addActionListener(e -> onStart.run());
        JLabel foot = new Glow("Clients sign in with their email  •  Staff and the Owner use their username and password");
        foot.setFont(pick(Font.BOLD, 14, 0.04f, "Segoe UI Semibold", "Segoe UI", "Trebuchet MS", "SansSerif"));
        foot.setForeground(Color.WHITE);

        for (JComponent c : new JComponent[]{brand, h1, h2, sub, start, foot}) c.setAlignmentX(Component.LEFT_ALIGNMENT);
        col.add(brand); col.add(Box.createVerticalStrut(22));
        col.add(h1); col.add(h2); col.add(Box.createVerticalStrut(18));
        col.add(sub); col.add(Box.createVerticalStrut(36));
        col.add(start); col.add(Box.createVerticalStrut(26));
        col.add(foot);

        GridBagConstraints g = new GridBagConstraints();
        g.anchor = GridBagConstraints.WEST;
        g.weightx = 1;
        g.weighty = 1;
        g.insets = new Insets(0, 90, 0, 0);
        add(col, g);

        JButton sound = new JButton("🔊 Sound on");
        sound.setFont(new Font("SansSerif", Font.BOLD, 12));
        sound.setForeground(Color.WHITE);
        sound.setContentAreaFilled(false);
        sound.setFocusPainted(false);
        sound.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Color.WHITE, 1, true), BorderFactory.createEmptyBorder(6, 12, 6, 12)));
        sound.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        sound.addActionListener(e -> { setMuted(!isMuted()); sound.setText(isMuted() ? "🔇 Sound off" : "🔊 Sound on"); });
        GridBagConstraints sg = new GridBagConstraints();
        sg.gridx = 1; sg.gridy = 0;
        sg.anchor = GridBagConstraints.SOUTHEAST;
        sg.insets = new Insets(0, 0, 24, 28);
        add(sound, sg);
    }

    private static final Color GOLD = new Color(255, 218, 196);   // soft rose-gold highlight

    /** First installed font from the list (falls back to the last name), with optional letter spacing. */
    private static Font pick(int style, int size, float tracking, String... names) {
        List<String> have = Arrays.asList(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames());
        String use = names[names.length - 1];
        for (String n : names) if (have.contains(n)) { use = n; break; }
        Font f = new Font(use, style, size);
        return tracking == 0f ? f : spaced(f, tracking);
    }

    private static Font spaced(Font f, float tracking) {
        Map<TextAttribute, Object> m = new HashMap<>();
        m.put(TextAttribute.TRACKING, tracking);
        return f.deriveFont(m);
    }

    /** Label with a soft dark shadow so the text stays sharp and readable over the bright video. */
    private static final class Glow extends JLabel {
        private static final java.awt.font.FontRenderContext FRC = new java.awt.font.FontRenderContext(null, true, true);

        Glow(String text) {
            super(text);
            setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 6));
        }

        private java.awt.font.TextLayout layout(java.awt.font.FontRenderContext frc) {
            return new java.awt.font.TextLayout(getText(), getFont(), frc);
        }

        @Override public Dimension getPreferredSize() {
            java.awt.font.TextLayout tl = layout(FRC);
            Insets in = getInsets();
            return new Dimension((int) Math.ceil(tl.getAdvance()) + in.left + in.right + 2,
                    (int) Math.ceil(tl.getAscent() + tl.getDescent() + tl.getLeading()) + in.top + in.bottom);
        }

        @Override public Dimension getMinimumSize() { return getPreferredSize(); }

        @Override public Dimension getMaximumSize() { return getPreferredSize(); }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            java.awt.font.TextLayout tl = layout(g2.getFontRenderContext());
            Insets in = getInsets();
            float x = in.left, y = in.top + tl.getAscent();
            int[][] off = {{3, 3, 40}, {2, 2, 70}, {1, 1, 110}, {0, 2, 50}, {2, 0, 50}};
            for (int[] o : off) {
                g2.setColor(new Color(20, 5, 15, o[2]));
                tl.draw(g2, x + o[0], y + o[1]);
            }
            g2.setColor(getForeground());
            tl.draw(g2, x, y);
            g2.dispose();
        }
    }
}
