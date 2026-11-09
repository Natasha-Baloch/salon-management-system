package com.salon;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;

/** Reusable page: title + toolbar + table that reloads itself from the database. Column 0 is the record id. */
public class TablePage extends BackgroundPanel {
    public interface Loader { List<Object[]> load() throws Exception; }
    public interface RowAction { void run(int id, Object[] row) throws Exception; }

    public final DefaultTableModel model;
    public final JTable table;
    private final Loader loader;
    private final JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
    private final JLabel info = new JLabel(" ");
    private Loader infoLoader;

    public TablePage(String title, String subtitle, String[] cols, Loader loader) {
        super(new BorderLayout(12, 12), 100 + title.hashCode() % 50);
        this.loader = loader;
        setBorder(new EmptyBorder(14, 14, 14, 14));
        model = new DefaultTableModel(cols, 0) { @Override public boolean isCellEditable(int r, int c) { return false; } };
        table = new JTable(model);
        style(table);

        RoundedPanel head = new RoundedPanel(new BorderLayout(10, 6), 20);
        head.setBackground(Color.WHITE);
        JLabel t = new JLabel(title);
        t.setFont(Theme.H2);
        t.setForeground(Theme.PURPLE_DARK);
        JLabel st = new JLabel(subtitle);
        st.setFont(Theme.BODY);
        st.setForeground(Theme.MUTED);
        info.setFont(Theme.serif(Font.BOLD, 22));
        info.setForeground(Theme.GREEN);
        JPanel text = new JPanel(new GridLayout(2, 1));
        text.setOpaque(false);
        text.add(t); text.add(st);
        toolbar.setOpaque(false);
        head.add(text, BorderLayout.WEST);
        head.add(info, BorderLayout.CENTER);
        head.add(toolbar, BorderLayout.EAST);
        info.setHorizontalAlignment(SwingConstants.CENTER);
        add(head, BorderLayout.NORTH);
        JScrollPane sp = new JScrollPane(table);
        sp.setBorder(BorderFactory.createLineBorder(Theme.BORDER, 2));
        sp.getViewport().setBackground(Color.WHITE);
        add(sp, BorderLayout.CENTER);
        Ui.register(this::refresh);
    }

    public void setInfo(Loader l) { this.infoLoader = l; }

    public void setColumnWidth(int col, int width) { table.getColumnModel().getColumn(col).setPreferredWidth(width); }

    public void addToolbar(JComponent c) { toolbar.add(c); toolbar.revalidate(); }

    public JButton addAction(String text, Color color, boolean needsRow, RowAction action) {
        JButton b = Theme.button(text, color);
        b.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (needsRow && row < 0) { Ui.warn(this, "Please select a row in the table first."); return; }
            Object[] data = null;
            int id = -1;
            if (row >= 0) {
                data = new Object[model.getColumnCount()];
                for (int i = 0; i < data.length; i++) data[i] = model.getValueAt(row, i);
                if (data[0] instanceof Number n) id = n.intValue();
            }
            try {
                action.run(id, data);
                Ui.refreshAll();
            } catch (Exception ex) { Ui.error(this, ex); }
        });
        toolbar.add(b);
        toolbar.revalidate();
        return b;
    }

    public void refresh() {
        try {
            List<Object[]> rows = loader.load();
            model.setRowCount(0);
            for (Object[] r : rows) model.addRow(r);
            if (infoLoader != null) { List<Object[]> i = infoLoader.load(); info.setText(String.valueOf(i.get(0)[0])); }
        } catch (Exception e) { Ui.error(this, e); }
    }

    static void style(JTable t) {
        t.setRowHeight(34);
        t.setFont(Theme.BODY);
        t.setForeground(Theme.TEXT);
        t.setBackground(Color.WHITE);
        t.setSelectionBackground(Theme.LAVENDER);
        t.setSelectionForeground(Theme.TEXT);
        t.setGridColor(new Color(242, 222, 230));
        t.setIntercellSpacing(new Dimension(1, 1));
        t.setFillsViewportHeight(true);
        t.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 13));
        t.getTableHeader().setUI(new javax.swing.plaf.basic.BasicTableHeaderUI());
        t.getTableHeader().setBackground(Theme.TABLE_HEADER);
        t.getTableHeader().setForeground(Color.WHITE);
        t.getTableHeader().setOpaque(true);
        t.getTableHeader().setPreferredSize(new Dimension(0, 38));
        t.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable tb, Object v, boolean sel, boolean foc, int r, int c) {
                JLabel l = (JLabel) super.getTableCellRendererComponent(tb, v, sel, foc, r, c);
                l.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
                String s = String.valueOf(v);
                Color col = Theme.TEXT;
                switch (s) {
                    case "COMPLETED", "PAID", "ACTIVE" -> col = Theme.GREEN;
                    case "IN_PROGRESS" -> col = new Color(150, 90, 170);
                    case "CANCELLED", "UNPAID", "DISABLED", "REJECTED" -> col = Theme.RED;
                    case "BOOKED", "READY", "REQUESTED", "Pending", "PENDING" -> col = Theme.ORANGE;
                    case "CONFIRMED", "Given" -> col = new Color(0, 121, 107);
                    case "NEW" -> col = Theme.MAGENTA;
                    default -> { }
                }
                l.setToolTipText(s.length() > 40 ? s : null);
                l.setForeground(col);
                l.setFont(col == Theme.TEXT ? Theme.BODY : Theme.BOLD);
                return l;
            }
        });
    }
}
