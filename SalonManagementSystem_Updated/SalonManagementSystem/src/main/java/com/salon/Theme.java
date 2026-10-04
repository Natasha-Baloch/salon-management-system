package com.salon;

import java.awt.*;
import javax.swing.*;
import javax.swing.border.LineBorder;

public final class Theme {
    // Baby-pink palette. Any color meant to sit behind text (headers, buttons,
    // the top bar, the welcome banner) is dark/saturated enough that white text
    // on top of it stays bright and easy to read.
    public static final Color PURPLE = new Color(214, 92, 155);        // deeper baby pink, used like the old "purple" accent
    public static final Color PURPLE_DARK = new Color(150, 45, 95);    // dark rose — header / banner background
    public static final Color DARK = PURPLE_DARK;
    public static final Color PINK = new Color(244, 143, 177);         // core baby pink accent
    public static final Color MAGENTA = new Color(219, 112, 147);      // pale violet red
    public static final Color TEAL = new Color(60, 160, 165);
    public static final Color GREEN = new Color(46, 160, 100);
    public static final Color ORANGE = new Color(230, 140, 60);
    public static final Color RED = new Color(210, 60, 80);
    public static final Color BG = new Color(255, 240, 246);           // soft baby pink app background
    public static final Color CARD = Color.WHITE;
    public static final Color TEXT = new Color(60, 30, 45);            // dark rose-brown, readable on white cards
    public static final Color MUTED = new Color(140, 90, 110);
    public static final Color FIELD_BG = new Color(255, 255, 255);
    public static final Color BORDER = new Color(244, 178, 202);
    public static final Color TABLE_HEADER = new Color(219, 112, 147); // baby pink header, white text on top
    public static final Color TEXT_ON_DARK = Color.WHITE;              // bright white text for colored/dark surfaces

    // Strong, high-contrast colors specifically for numbers/values shown on
    // white cards (e.g. dashboard stats). The lighter theme accents above
    // look nice as buttons/backgrounds but are too pale to read as text on
    // white, so these darker versions are used wherever a value needs to
    // stay clearly visible.
    public static final Color VALUE_REVENUE = new Color(199, 21, 133);
    public static final Color VALUE_BOOKINGS = new Color(120, 40, 75);
    public static final Color VALUE_CLIENTS = new Color(0, 121, 107);
    public static final Color VALUE_PENDING = new Color(191, 87, 0);

    public static final Font TITLE = new Font("SansSerif", Font.BOLD, 26);
    public static final Font H2 = new Font("SansSerif", Font.BOLD, 19);
    public static final Font BODY = new Font("SansSerif", Font.PLAIN, 14);
    public static final Font BOLD = new Font("SansSerif", Font.BOLD, 14);
    public static final Font LABEL = new Font("SansSerif", Font.BOLD, 14);

    private Theme() {}

    public static void styleButton(JButton b, Color color) {
        b.setUI(new javax.swing.plaf.basic.BasicButtonUI());
        b.setBackground(color);
        b.setForeground(Color.WHITE);
        b.setFont(BOLD);
        b.setFocusPainted(false);
        b.setBorder(BorderFactory.createEmptyBorder(11, 18, 11, 18));
        b.setCursor(new Cursor(Cursor.HAND_CURSOR));
        b.setOpaque(true);
    }

    public static void styleField(JComponent c) {
        c.setFont(BODY);
        c.setForeground(TEXT);
        c.setBackground(FIELD_BG);
        c.setOpaque(true);
        c.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(BORDER, 2, true),
                BorderFactory.createEmptyBorder(6, 9, 6, 9)
        ));
    }

    public static void styleCombo(JComboBox<?> combo) {
        combo.setFont(BODY);
        combo.setForeground(TEXT);
        combo.setBackground(Color.WHITE);
        combo.setOpaque(true);
        combo.setBorder(new LineBorder(BORDER, 2, true));
        combo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                    boolean isSelected, boolean cellHasFocus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                label.setFont(BODY);
                label.setForeground(TEXT);
                label.setBackground(isSelected ? new Color(235, 215, 242) : Color.WHITE);
                label.setBorder(BorderFactory.createEmptyBorder(5, 8, 5, 8));
                return label;
            }
        });
    }
}
