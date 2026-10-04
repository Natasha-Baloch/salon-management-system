package com.salon;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.plaf.metal.MetalLookAndFeel;

public class Main {
    public static void main(String[] args) {
        try {
            // Metal is used deliberately instead of the system L&F. On Linux
            // (GTK) and some other native look-and-feels, JButton and
            // JTableHeader are painted natively and silently ignore custom
            // setBackground()/setForeground() calls — that's what was making
            // buttons and column headers show up blank. Metal always honors
            // custom colors, so the pink theme renders correctly everywhere.
            UIManager.setLookAndFeel(new MetalLookAndFeel());
        } catch (Exception ignored) {}
        DB.initialize();
        SwingUtilities.invokeLater(() -> new MainFrame().setVisible(true));
    }
}
