package com.salon;

import javax.swing.*;
import java.awt.*;

/** Two ways in: clients sign in with just their email; staff and the owner use username + password. */
public class LoginPanel extends ImagePanel {
    private final JTextField email = Ui.field("");
    private final JTextField user = Ui.field("");
    private final JPasswordField pass = Ui.password();
    private final JLabel clientError = new JLabel(" ");
    private final JLabel staffError = new JLabel(" ");
    private final CardLayout modes = new CardLayout();
    private final JPanel body = new JPanel(modes);
    private final JToggleButton clientTab = tab("I'm a Client");
    private final JToggleButton staffTab = tab("Staff / Owner");
    private final java.util.function.Consumer<Session.User> onSuccess;

    public LoginPanel(java.util.function.Consumer<Session.User> onSuccess, Runnable onBack) {
        super(new GridBagLayout(), false);
        this.onSuccess = onSuccess;
        RoundedPanel card = new RoundedPanel(new GridBagLayout(), 30);
        card.setBackground(new Color(255, 255, 255, 244));
        card.setBorder(BorderFactory.createEmptyBorder(28, 40, 24, 40));
        GridBagConstraints g = new GridBagConstraints();
        g.gridx = 0; g.fill = GridBagConstraints.HORIZONTAL; g.weightx = 1; g.insets = new Insets(4, 0, 4, 0);

        JLabel brand = new JLabel("✿  BEAUTY BLOOM SALON", SwingConstants.CENTER);
        brand.setFont(new Font("SansSerif", Font.BOLD, 13));
        brand.setForeground(Theme.ROSE_GOLD);
        JLabel title = new JLabel("Welcome", SwingConstants.CENTER);
        title.setFont(Theme.serif(Font.BOLD, 32));
        title.setForeground(Theme.PURPLE_DARK);

        ButtonGroup bg = new ButtonGroup();
        JPanel tabs = new JPanel(new GridLayout(1, 2, 8, 0));
        tabs.setOpaque(false);
        for (JToggleButton b : new JToggleButton[]{clientTab, staffTab}) {
            bg.add(b);
            b.setFont(Theme.BOLD);
            b.setFocusPainted(false);
            b.setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));
            b.setCursor(new Cursor(Cursor.HAND_CURSOR));
            b.addItemListener(e -> styleTabs());
            tabs.add(b);
        }
        clientTab.addActionListener(e -> show("client"));
        staffTab.addActionListener(e -> show("staff"));

        body.setOpaque(false);
        body.add(clientForm(), "client");
        body.add(staffForm(), "staff");

        JButton back = new JButton("← Back to welcome");
        back.setContentAreaFilled(false); back.setBorderPainted(false); back.setFocusPainted(false);
        back.setForeground(Theme.MUTED); back.setFont(Theme.BODY); back.setCursor(new Cursor(Cursor.HAND_CURSOR));
        back.addActionListener(e -> onBack.run());

        int y = 0;
        g.gridy = y++; card.add(brand, g);
        g.gridy = y++; card.add(title, g);
        g.gridy = y++; g.insets = new Insets(6, 0, 10, 0); card.add(tabs, g);
        g.insets = new Insets(4, 0, 4, 0);
        g.gridy = y++; card.add(body, g);
        g.gridy = y; card.add(back, g);
        add(card);
        clientTab.setSelected(true);
        styleTabs();
    }

    /** Pill-shaped tab that paints its own colours (the default look ignores them). */
    private static JToggleButton tab(String text) {
        return new JToggleButton(text) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(isSelected() ? Theme.ROSE_GOLD : Theme.LAVENDER);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
                g2.dispose();
                super.paintComponent(g);
            }
        };
    }

    private void styleTabs() {
        for (JToggleButton b : new JToggleButton[]{clientTab, staffTab}) {
            b.setContentAreaFilled(false);
            b.setOpaque(false);
            b.setForeground(b.isSelected() ? Color.WHITE : Theme.TEXT);
            b.repaint();
        }
    }

    private void show(String name) { modes.show(body, name); (name.equals("client") ? email : user).requestFocusInWindow(); }

    private JPanel clientForm() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setOpaque(false);
        GridBagConstraints g = new GridBagConstraints();
        g.gridx = 0; g.fill = GridBagConstraints.HORIZONTAL; g.weightx = 1; g.insets = new Insets(4, 0, 4, 0);
        email.setPreferredSize(new Dimension(340, 40));
        clientError.setForeground(Theme.RED);
        clientError.setFont(Theme.BOLD);
        clientError.setHorizontalAlignment(SwingConstants.CENTER);
        JLabel hint = new JLabel("<html><div style='text-align:center;color:#80586a'>No password needed — just your email.<br>First visit? We'll ask for your name and phone.</div></html>", SwingConstants.CENTER);
        hint.setFont(Theme.BODY);
        JLabel demo = new JLabel("Try the demo client:  sana.malik@example.com", SwingConstants.CENTER);
        demo.setFont(new Font("SansSerif", Font.ITALIC, 12));
        demo.setForeground(Theme.MUTED);
        JButton go = Theme.button("SIGN IN WITH EMAIL", Theme.ROSE_GOLD);
        go.addActionListener(e -> clientSignIn());
        email.addActionListener(e -> clientSignIn());
        int y = 0;
        g.gridy = y++; p.add(hint, g);
        g.gridy = y++; p.add(Ui.label("Your email"), g);
        g.gridy = y++; p.add(email, g);
        g.gridy = y++; p.add(clientError, g);
        g.gridy = y++; g.insets = new Insets(6, 0, 6, 0); p.add(go, g);
        g.gridy = y; p.add(demo, g);
        return p;
    }

    private JPanel staffForm() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setOpaque(false);
        GridBagConstraints g = new GridBagConstraints();
        g.gridx = 0; g.fill = GridBagConstraints.HORIZONTAL; g.weightx = 1; g.insets = new Insets(4, 0, 2, 0);
        user.setPreferredSize(new Dimension(340, 40));
        pass.setPreferredSize(new Dimension(340, 40));
        JCheckBox show = new JCheckBox("Show password");
        show.setOpaque(false);
        show.setFont(Theme.BODY);
        show.setForeground(Theme.MUTED);
        char echo = pass.getEchoChar();
        show.addActionListener(e -> pass.setEchoChar(show.isSelected() ? (char) 0 : echo));
        staffError.setForeground(Theme.RED);
        staffError.setFont(Theme.BOLD);
        staffError.setHorizontalAlignment(SwingConstants.CENTER);
        JButton login = Theme.button("SIGN IN", Theme.ROSE_GOLD);
        login.addActionListener(e -> staffSignIn());
        pass.addActionListener(e -> staffSignIn());
        user.addActionListener(e -> pass.requestFocusInWindow());
        int y = 0;
        g.gridy = y++; p.add(Ui.label("Username"), g);
        g.gridy = y++; p.add(user, g);
        g.gridy = y++; p.add(Ui.label("Password"), g);
        g.gridy = y++; p.add(pass, g);
        g.gridy = y++; p.add(show, g);
        g.gridy = y++; p.add(staffError, g);
        g.gridy = y; g.insets = new Insets(6, 0, 6, 0); p.add(login, g);
        return p;
    }

    private void clientSignIn() {
        String em = email.getText().trim();
        if (em.isEmpty()) { clientError.setText("Please enter your email address."); return; }
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        try {
            Session.User s;
            try {
                s = Salon.clientLogin(em, null, null);
            } catch (Salon.NewClientException nce) {
                JTextField name = Ui.field(""), phone = Ui.field("");
                if (!Ui.form(this, "Welcome! Create your client account", new String[]{"Your name *", "Phone *"}, new JComponent[]{name, phone})) {
                    clientError.setText(" ");
                    return;
                }
                s = Salon.clientLogin(em, name.getText(), phone.getText());
            }
            clientError.setText(" ");
            onSuccess.accept(s);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            clientError.setText(ex.getMessage());
        } catch (Salon.NewClientException ex) {
            clientError.setText("Could not create the account.");
        } catch (Exception ex) {
            clientError.setText("Cannot reach the database. Check MySQL and db.properties.");
        } finally { setCursor(Cursor.getDefaultCursor()); }
    }

    private void staffSignIn() {
        String u = user.getText().trim();
        char[] p = pass.getPassword();
        if (u.isEmpty() || p.length == 0) { staffError.setText("Please enter your username and password."); return; }
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        try {
            Session.User s = Salon.login(u, p);
            staffError.setText(" ");
            pass.setText("");
            onSuccess.accept(s);
        } catch (IllegalStateException ex) {
            staffError.setText(ex.getMessage());
            pass.setText("");
        } catch (Exception ex) {
            staffError.setText("Cannot reach the database. Check MySQL and db.properties.");
        } finally { setCursor(Cursor.getDefaultCursor()); }
    }

    public void reset() {
        email.setText(""); user.setText(""); pass.setText("");
        clientError.setText(" "); staffError.setText(" ");
        clientTab.setSelected(true); styleTabs(); modes.show(body, "client");
        email.requestFocusInWindow();
    }
}
