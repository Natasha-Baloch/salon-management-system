package com.salon;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/** Small shared UI helpers: dialogs and a refresh registry so every open page reloads after a change. */
public final class Ui {
    private static final List<Runnable> REFRESHERS = new ArrayList<>();

    private Ui() {}

    public static void register(Runnable r) { REFRESHERS.add(r); }
    public static void clearRefreshers() { REFRESHERS.clear(); }
    public static void refreshAll() { for (Runnable r : new ArrayList<>(REFRESHERS)) r.run(); }

    public static void warn(Component p, String msg) { JOptionPane.showMessageDialog(p, msg, "Notice", JOptionPane.WARNING_MESSAGE); }
    public static void info(Component p, String msg) { JOptionPane.showMessageDialog(p, msg, "Beauty Bloom Salon", JOptionPane.INFORMATION_MESSAGE); }

    public static void error(Component p, Exception e) {
        e.printStackTrace();   // technical details for the console
        if (e instanceof SecurityException || e instanceof IllegalArgumentException || e instanceof IllegalStateException) {
            JOptionPane.showMessageDialog(p, e.getMessage(), e instanceof SecurityException ? "Access Denied" : "Please check", JOptionPane.WARNING_MESSAGE);
        } else {
            JOptionPane.showMessageDialog(p, "Database error:\n" + e.getMessage() + "\n\nCheck MySQL is running and db.properties is correct.",
                    "Database Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    public static JLabel label(String t) {
        JLabel l = new JLabel(t);
        l.setFont(Theme.LABEL);
        l.setForeground(Theme.TEXT);
        return l;
    }

    public static JTextField field(String text) {
        JTextField f = new JTextField(text, 22);
        Theme.styleField(f);
        return f;
    }

    // ---------- friendly date / time pickers ----------
    public static JSpinner dateSpinner(java.time.LocalDate d) {
        JSpinner sp = new JSpinner(new SpinnerDateModel(java.sql.Date.valueOf(d), null, null, java.util.Calendar.DAY_OF_MONTH));
        sp.setEditor(new JSpinner.DateEditor(sp, "yyyy-MM-dd"));
        return styledSpinner(sp);
    }

    public static java.time.LocalDate dateOf(JSpinner sp) {
        return ((java.util.Date) sp.getValue()).toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate();
    }

    /** Half-hour slots from 09:00 to 21:00. The given time is snapped to the nearest slot. */
    public static JSpinner timeSpinner(java.time.LocalTime preferred) {
        List<String> slots = new ArrayList<>();
        for (int m = 9 * 60; m <= 21 * 60; m += 30) slots.add(String.format("%02d:%02d", m / 60, m % 60));
        int want = preferred.getHour() * 60 + preferred.getMinute(), best = 0, diff = Integer.MAX_VALUE;
        for (int i = 0; i < slots.size(); i++) {
            int m = Integer.parseInt(slots.get(i).substring(0, 2)) * 60 + Integer.parseInt(slots.get(i).substring(3));
            if (Math.abs(m - want) < diff) { diff = Math.abs(m - want); best = i; }
        }
        JSpinner sp = new JSpinner(new SpinnerListModel(slots));
        sp.setValue(slots.get(best));
        return styledSpinner(sp);
    }

    public static java.time.LocalTime timeOf(JSpinner sp) { return java.time.LocalTime.parse((String) sp.getValue()); }

    private static JSpinner styledSpinner(JSpinner sp) {
        sp.setFont(Theme.BODY);
        sp.setPreferredSize(new Dimension(200, 32));
        if (sp.getEditor() instanceof JSpinner.DefaultEditor e) { e.getTextField().setFont(Theme.BODY); e.getTextField().setEditable(false); e.getTextField().setBackground(Color.WHITE); }
        return sp;
    }

    public static JPasswordField password() {
        JPasswordField f = new JPasswordField(22);
        Theme.styleField(f);
        return f;
    }

    /** Shows a labelled form in a dialog. Returns true when OK was pressed. */
    public static boolean form(Component parent, String title, String[] labels, JComponent[] inputs) {
        JPanel p = new JPanel(new GridLayout(labels.length, 2, 8, 8));
        p.setBackground(Color.WHITE);
        for (int i = 0; i < labels.length; i++) { p.add(label(labels[i])); p.add(inputs[i]); }
        return JOptionPane.showConfirmDialog(parent, p, title, JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION;
    }

    /** Change-password dialog. Returns true when the password was changed. */
    public static boolean changePassword(Component parent, boolean forced) {
        while (true) {
            JPasswordField oldPw = password(), n1 = password(), n2 = password();
            String head = forced ? "For security, please set a new password before continuing.\n(8+ characters, letters and numbers)" : "Choose a new password (8+ characters, letters and numbers).";
            JPanel p = new JPanel(new GridLayout(0, 1, 6, 6));
            p.add(new JLabel("<html>" + head.replace("\n", "<br>") + "</html>"));
            p.add(label("Current password")); p.add(oldPw);
            p.add(label("New password")); p.add(n1);
            p.add(label("Confirm new password")); p.add(n2);
            int r = JOptionPane.showConfirmDialog(parent, p, "Change Password", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (r != JOptionPane.OK_OPTION) return false;
            try {
                if (!new String(n1.getPassword()).equals(new String(n2.getPassword()))) throw new IllegalArgumentException("The new passwords do not match.");
                Salon.changePassword(new String(oldPw.getPassword()), new String(n1.getPassword()));
                info(parent, "Your password was changed successfully.");
                return true;
            } catch (Exception e) { error(parent, e); }
        }
    }
}
