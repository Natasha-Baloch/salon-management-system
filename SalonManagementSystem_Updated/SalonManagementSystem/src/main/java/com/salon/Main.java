package com.salon;

import javax.swing.*;
import javax.swing.plaf.metal.MetalLookAndFeel;

public class Main {
    public static void main(String[] args) {
        for (String a : args) if ("--reset-demo".equals(a)) System.setProperty("salon.resetDemo", "1");
        try {
            // Metal always honours custom colours, so the rose theme renders the same on every OS.
            UIManager.setLookAndFeel(new MetalLookAndFeel());
        } catch (Exception ignored) {
        }
        UIManager.put("TabbedPane.selected", Theme.LAVENDER);
        UIManager.put("TabbedPane.contentAreaColor", Theme.BG);
        UIManager.put("TabbedPane.light", Theme.BORDER);
        UIManager.put("TabbedPane.highlight", Theme.BORDER);
        UIManager.put("TabbedPane.shadow", Theme.BORDER);
        UIManager.put("TabbedPane.darkShadow", Theme.BORDER);
        UIManager.put("TabbedPane.focus", Theme.ROSE_GOLD);
        UIManager.put("TabbedPane.tabInsets", new java.awt.Insets(9, 16, 9, 16));
        UIManager.put("OptionPane.background", java.awt.Color.WHITE);
        UIManager.put("Panel.background", java.awt.Color.WHITE);
        try {
            DB.initialize();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null,
                    "Beauty Bloom Salon could not connect to MySQL.\n\n" + e.getMessage() +
                    "\n\nCheck that MySQL is running and that db.properties (host, port, user, password)\n" +
                    "or the SALON_DB_* environment variables are correct. See README.txt.",
                    "Database Connection Error", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
        SwingUtilities.invokeLater(() -> new AppFrame().setVisible(true));
    }
}
