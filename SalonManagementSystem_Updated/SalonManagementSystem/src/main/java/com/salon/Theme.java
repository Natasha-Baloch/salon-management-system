package com.salon;

import javax.swing.*;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.util.Arrays;

/** Dusty pink / blush / rose-gold / white / lavender palette. */
public final class Theme {
    public static final Color ROSE_GOLD = new Color(183, 110, 121);
    public static final Color ROSE_GOLD_LIGHT = new Color(226, 170, 160);
    public static final Color PURPLE = ROSE_GOLD;                      // main accent
    public static final Color PURPLE_DARK = new Color(122, 56, 82);    // deep rose (headers)
    public static final Color DARK = PURPLE_DARK;
    public static final Color PINK = new Color(214, 132, 160);         // dusty pink
    public static final Color MAGENTA = new Color(196, 112, 142);
    public static final Color LAVENDER = new Color(234, 224, 246);
    public static final Color GREEN = new Color(63, 148, 110);
    public static final Color ORANGE = new Color(214, 140, 70);
    public static final Color RED = new Color(190, 65, 85);
    public static final Color BG = new Color(253, 242, 246);           // soft blush
    public static final Color TEXT = new Color(60, 30, 45);
    public static final Color MUTED = new Color(128, 88, 106);
    public static final Color BORDER = new Color(236, 190, 208);
    public static final Color TABLE_HEADER = new Color(168, 92, 116);

    private static final String SERIF = Arrays.asList(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames())
            .contains("Georgia") ? "Georgia" : "Serif";
    public static final Font TITLE = serif(Font.BOLD, 26);
    public static final Font H2 = serif(Font.BOLD, 19);
    public static final Font BODY = new Font("SansSerif", Font.PLAIN, 14);
    public static final Font BOLD = new Font("SansSerif", Font.BOLD, 14);
    public static final Font LABEL = new Font("SansSerif", Font.BOLD, 14);

    private Theme() {}

    public static Font serif(int style, int size) { return new Font(SERIF, style, size); }

    public static JButton button(String text, Color color) { return new Btn(text, color); }

    public static void styleField(JComponent c) {
        c.setFont(BODY);
        c.setForeground(TEXT);
        c.setBackground(Color.WHITE);
        c.setOpaque(true);
        c.setBorder(BorderFactory.createCompoundBorder(new LineBorder(BORDER, 2, true), BorderFactory.createEmptyBorder(6, 9, 6, 9)));
    }

    public static void styleCombo(JComboBox<?> combo) {
        combo.setFont(BODY);
        combo.setForeground(TEXT);
        combo.setBackground(Color.WHITE);
        combo.setBorder(new LineBorder(BORDER, 2, true));
        combo.setRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> l, Object v, int i, boolean sel, boolean f) {
                JLabel x = (JLabel) super.getListCellRendererComponent(l, v, i, sel, f);
                x.setFont(BODY); x.setForeground(TEXT);
                x.setBackground(sel ? LAVENDER : Color.WHITE);
                x.setBorder(BorderFactory.createEmptyBorder(5, 8, 5, 8));
                return x;
            }
        });
    }

    /** Rounded pill button with hover effect. */
    public static final class Btn extends JButton {
        private final Color base;
        private boolean hover;

        Btn(String text, Color base) {
            super(text);
            this.base = base;
            setFont(BOLD);
            setForeground(Color.WHITE);
            setFocusPainted(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(11, 20, 11, 20));
            setCursor(new Cursor(Cursor.HAND_CURSOR));
            addMouseListener(new java.awt.event.MouseAdapter() {
                @Override public void mouseEntered(java.awt.event.MouseEvent e) { hover = true; repaint(); }
                @Override public void mouseExited(java.awt.event.MouseEvent e) { hover = false; repaint(); }
            });
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color c = !isEnabled() ? new Color(205, 195, 200) : hover ? base.darker() : base;
            g2.setPaint(new GradientPaint(0, 0, c.brighter(), 0, getHeight(), c));
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
            g2.dispose();
            super.paintComponent(g);
        }
    }
}
