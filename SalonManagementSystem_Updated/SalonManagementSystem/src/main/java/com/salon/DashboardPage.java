package com.salon;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** Role dashboard: banner + stat cards + optional revenue chart + table. All numbers come from MySQL. */
public class DashboardPage extends BackgroundPanel {
    private final JPanel cardsRow = new JPanel();
    private final ChartPanel chart = new ChartPanel();
    private final JLabel tableTitle = new JLabel();
    private final DefaultTableModel model = new DefaultTableModel() { @Override public boolean isCellEditable(int r, int c) { return false; } };
    private final RoundedPanel chartCard = new RoundedPanel(new BorderLayout(5, 5), 20);
    private final JPanel lower = new JPanel(new GridLayout(1, 2, 14, 14));
    private final RoundedPanel tableCard = new RoundedPanel(new BorderLayout(5, 8), 20);

    public DashboardPage(Session.User u) {
        super(new BorderLayout(14, 14), 7);
        setBorder(new EmptyBorder(14, 14, 14, 14));

        RoundedPanel banner = new RoundedPanel(new BorderLayout(), 24) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setPaint(new GradientPaint(0, 0, Theme.PURPLE_DARK, getWidth(), 0, Theme.ROSE_GOLD));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 24, 24);
                g2.dispose();
            }
        };
        JLabel hello = new JLabel("<html><div style='padding:6px 10px'><span style='font-size:22px;color:white'>Welcome back, " + esc(u.fullName()) +
                "</span><br><span style='color:#ffe4ec'>" + u.role().label + " dashboard  •  " +
                LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy")) + "</span></div></html>");
        hello.setFont(Theme.serif(Font.BOLD, 14));
        banner.add(hello);

        cardsRow.setOpaque(false);
        cardsRow.setPreferredSize(new Dimension(100, 110));

        chartCard.setBackground(Color.WHITE);
        JLabel ct = new JLabel("Revenue — Last 7 Days");
        ct.setFont(Theme.H2); ct.setForeground(Theme.PURPLE_DARK);
        chartCard.add(ct, BorderLayout.NORTH);
        chartCard.add(chart, BorderLayout.CENTER);

        tableCard.setBackground(Color.WHITE);
        tableTitle.setFont(Theme.H2); tableTitle.setForeground(Theme.PURPLE_DARK);
        JTable t = new JTable(model);
        TablePage.style(t);
        tableCard.add(tableTitle, BorderLayout.NORTH);
        JScrollPane sp = new JScrollPane(t);
        sp.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        tableCard.add(sp, BorderLayout.CENTER);
        lower.setOpaque(false);

        JPanel north = new JPanel(new BorderLayout(14, 14));
        north.setOpaque(false);
        north.add(banner, BorderLayout.NORTH);
        north.add(cardsRow, BorderLayout.CENTER);
        add(north, BorderLayout.NORTH);
        add(lower, BorderLayout.CENTER);
        Ui.register(this::refresh);
        refresh();
    }

    private static String esc(String s) { return s.replace("&", "&amp;").replace("<", "&lt;"); }

    public final void refresh() {
        try {
            Salon.Dash d = Salon.dashboard();
            cardsRow.removeAll();
            cardsRow.setLayout(new GridLayout(1, d.cards().size(), 12, 12));
            Color[] accent = {Theme.PURPLE_DARK, Theme.ROSE_GOLD, new Color(0, 121, 107), new Color(150, 90, 170), new Color(199, 21, 133)};
            int i = 0;
            for (var e : d.cards().entrySet()) cardsRow.add(statCard(e.getKey(), e.getValue(), accent[i++ % accent.length]));
            cardsRow.revalidate(); cardsRow.repaint();

            tableTitle.setText(d.tableTitle());
            model.setDataVector(d.rows().toArray(new Object[0][]), d.cols());
            lower.removeAll();
            if (d.chartValues() != null) { chart.setData(d.chartValues(), d.chartLabels()); lower.add(chartCard); }
            lower.add(tableCard);
            lower.revalidate(); lower.repaint();
        } catch (Exception e) { Ui.error(this, e); }
    }

    private JPanel statCard(String title, String value, Color accent) {
        RoundedPanel c = new RoundedPanel(new GridLayout(2, 1), 20);
        c.setBackground(Color.WHITE);
        JLabel t = new JLabel(title);
        t.setFont(new Font("SansSerif", Font.BOLD, 11));
        t.setForeground(Theme.MUTED);
        JLabel v = new JLabel(value);
        v.setFont(Theme.serif(Font.BOLD, 28));
        v.setForeground(accent);
        c.add(t); c.add(v);
        return c;
    }
}
