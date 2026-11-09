package com.salon;

import javax.swing.*;
import java.awt.*;

/** Window: Welcome -> Login -> role-specific workspace (and back on logout). */
public class AppFrame extends JFrame {
    private final CardLayout cards = new CardLayout();
    private final JPanel root = new JPanel(cards);
    private final LoginPanel login;
    private AppShell shell;

    public AppFrame() {
        setTitle("Beauty Bloom Salon");
        setSize(1280, 820);
        setMinimumSize(new Dimension(1050, 700));
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        if (screen.width < 1400 || screen.height < 850) setExtendedState(JFrame.MAXIMIZED_BOTH);

        login = new LoginPanel(this::onLogin, () -> cards.show(root, "welcome"));
        root.add(new WelcomePanel(() -> { login.reset(); cards.show(root, "login"); }), "welcome");
        root.add(login, "login");
        setContentPane(root);
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosing(java.awt.event.WindowEvent e) { Salon.logout(); }
        });
    }

    private void onLogin(Session.User u) {
        if (u.mustChangePassword() && !Ui.changePassword(this, true)) {
            Salon.logout();                       // refused to change the password -> stay out
            login.reset();
            return;
        }
        Ui.clearRefreshers();
        if (shell != null) { shell.stop(); root.remove(shell); }
        shell = new AppShell(Session.user(), this::onLogout);
        root.add(shell, "app");
        cards.show(root, "app");
    }

    private void onLogout() {
        Salon.logout();
        Ui.clearRefreshers();
        if (shell != null) { shell.stop(); root.remove(shell); shell = null; }
        login.reset();
        cards.show(root, "login");
    }
}
