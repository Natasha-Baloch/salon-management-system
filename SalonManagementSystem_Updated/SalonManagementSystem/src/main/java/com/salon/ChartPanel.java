package com.salon;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class ChartPanel extends JPanel {
    private List<Double> values = List.of(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
    private List<String> labels = List.of("M","T","W","T","F","S","S");

    public ChartPanel() {
        setPreferredSize(new Dimension(600, 230));
        setOpaque(false);
    }

    public void setData(List<Double> values, List<String> labels) {
        this.values = values;
        this.labels = labels;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int w = getWidth(), h = getHeight();
        int left = 45, bottom = 35, top = 20;
        double max = 1;
        for (double v : values) max = Math.max(max, v);

        g2.setColor(new Color(230, 225, 235));
        g2.drawLine(left, top, left, h-bottom);
        g2.drawLine(left, h-bottom, w-20, h-bottom);

        int n = Math.max(1, values.size());
        int chartW = w - left - 30;
        int barW = Math.max(20, chartW / n - 18);

        for (int i = 0; i < n; i++) {
            double value = i < values.size() ? values.get(i) : 0;
            int barH = (int)((h-top-bottom-10) * value / max);
            int x = left + 15 + i * (chartW / n);
            int y = h - bottom - barH;

            GradientPaint gp = new GradientPaint(x, y, Theme.PINK, x, h-bottom, Theme.PURPLE);
            g2.setPaint(gp);
            g2.fillRoundRect(x, y, barW, barH, 12, 12);

            g2.setColor(Theme.DARK);
            String label = i < labels.size() ? labels.get(i) : "";
            g2.drawString(label, x + barW/2 - 4, h - 12);
            g2.setFont(new Font("SansSerif", Font.PLAIN, 11));
            g2.drawString(String.format("%.0f", value), x + barW/2 - 6, Math.max(14, y-5));
        }
        g2.dispose();
    }
}
