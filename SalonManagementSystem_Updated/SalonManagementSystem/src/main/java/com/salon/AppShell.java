package com.salon;

import com.salon.Role.Perm;
import com.salon.Salon.ServiceItem;
import com.salon.Salon.Staff;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/** The logged-in workspace. Tabs are built from the user's role; every action is re-checked in Salon.* as well. */
public class AppShell extends JPanel {
    private static final String[] BOOKING_COLS = {"ID", "Client", "Phone", "Service", "Date", "Time", "Staff", "Status", "Amount (Rs.)", "Payment"};
    private static final String[] METHODS = {"Cash", "Card", "Bank Transfer", "JazzCash / Easypaisa"};
    private final Session.User user;
    private final JTabbedPane tabs = new JTabbedPane();
    private final JButton bell = Theme.button("🔔 0", new Color(255, 255, 255, 60));
    private int notifTab = -1;
    private int lastUnread = -1;
    private final javax.swing.Timer poll;

    public AppShell(Session.User user, Runnable onLogout) {
        super(new BorderLayout());
        this.user = user;
        setBackground(Theme.BG);
        tabs.setFont(new Font("SansSerif", Font.BOLD, 14));
        tabs.setBackground(Color.WHITE);
        tabs.setForeground(Theme.TEXT);
        tabs.setBorder(BorderFactory.createMatteBorder(0, 0, 2, 0, Theme.ROSE_GOLD_LIGHT));

        Role r = user.role();
        tabs.addTab("🏠  Dashboard", new DashboardPage(user));
        if (r == Role.STAFF) {
            tabs.addTab("📥  Requests", requestsPage());
            tabs.addTab("💳  Confirm Payments", confirmPaymentsPage());
            tabs.addTab("📋  My Appointments", myAppointments());
            tabs.addTab("🕘  My Work History", myHistory());
            tabs.addTab("⭐  My Feedback", new ReviewsPage("FEEDBACK ABOUT MY SERVICES", "What clients said about the services you performed.", null));
        } else if (r == Role.CLIENT) {
            tabs.addTab("💇  Services & Prices", new ServicesGallery(this::requestDialog));
            tabs.addTab("📋  My Bookings & Payments", clientBookingsPage());
            tabs.addTab("⭐  Reviews", new ReviewsPage("CLIENT REVIEWS", "See what other clients say, and share your own experience after your service.", () -> feedbackDialog(this, null)));
        } else {
            if (r.can(Perm.CREATE_BOOKING)) tabs.addTab("📅  Book Client", bookingPage());
            if (r.can(Perm.VIEW_CLIENTS)) tabs.addTab("👩  Clients", clientsPage());
            if (r.can(Perm.VIEW_BOOKINGS)) tabs.addTab("🗓  Bookings", bookingsPage());
            if (r.can(Perm.VIEW_SERVICES)) tabs.addTab("💇  Services", servicesPage());
            if (r.can(Perm.VIEW_PAYMENTS)) tabs.addTab("💳  Payments", paymentsPage());
            if (r.can(Perm.VIEW_REVIEWS)) tabs.addTab("⭐  Feedback", new ReviewsPage("CLIENT FEEDBACK", "Every review clients have shared about the salon.", null));
            if (r.can(Perm.VIEW_REPORTS)) tabs.addTab("📊  Reports", reportsPage());
            if (r.can(Perm.MANAGE_USERS)) tabs.addTab("👥  Users & Staff", usersPage());
        }
        notifTab = tabs.getTabCount();
        tabs.addTab("🔔  Notifications", notificationsPage());

        JPanel header = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setPaint(new GradientPaint(0, 0, Theme.PURPLE_DARK, getWidth(), 0, Theme.ROSE_GOLD));
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.dispose();
            }
        };
        header.setBorder(new EmptyBorder(10, 22, 10, 22));
        JLabel brand = new JLabel("✿  BEAUTY BLOOM SALON");
        brand.setForeground(Color.WHITE);
        brand.setFont(Theme.serif(Font.BOLD, 24));
        JLabel who = new JLabel(user.fullName() + "  •  " + r.label);
        who.setForeground(Color.WHITE);
        who.setFont(Theme.BOLD);
        JButton profile = Theme.button("Profile", new Color(255, 255, 255, 60));
        profile.addActionListener(e -> profile());
        JButton logout = Theme.button("Logout", Theme.PURPLE_DARK.darker());
        logout.addActionListener(e -> { if (JOptionPane.showConfirmDialog(this, "Log out now?", "Logout", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) onLogout.run(); });
        bell.addActionListener(e -> tabs.setSelectedIndex(notifTab));
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        right.setOpaque(false);
        right.add(who); right.add(bell); right.add(profile); right.add(logout);
        header.add(brand, BorderLayout.WEST);
        header.add(right, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);
        add(tabs, BorderLayout.CENTER);

        // keep the bell up to date; new notifications (from other people using the app) refresh every page
        Ui.register(() -> pollNotifications(false));
        Ui.refreshAll();                       // first load of every table
        pollNotifications(false);
        poll = new javax.swing.Timer(6000, e -> pollNotifications(true));
        poll.start();
        if (lastUnread > 0) tabs.setSelectedIndex(notifTab);   // show what is waiting right after sign-in
    }

    public void stop() { if (poll != null) poll.stop(); }

    private void pollNotifications(boolean refreshIfNew) {
        try {
            int n = Salon.unreadCount();
            bell.setText("🔔 " + n);
            if (notifTab >= 0) tabs.setTitleAt(notifTab, n > 0 ? "🔔  Notifications (" + n + ")" : "🔔  Notifications");
            boolean grew = lastUnread >= 0 && n > lastUnread;
            lastUnread = n;
            if (refreshIfNew && grew) { Toolkit.getDefaultToolkit().beep(); Ui.refreshAll(); }
        } catch (Exception ignored) { }
    }

    private TablePage notificationsPage() {
        TablePage p = new TablePage("NOTIFICATIONS", "Updates about your bookings, payments and feedback. New ones appear automatically.",
                new String[]{"ID", "Time", "Message", "Status"}, Salon::notifications);
        p.setColumnWidth(0, 50); p.setColumnWidth(1, 140); p.setColumnWidth(2, 800); p.setColumnWidth(3, 70);
        p.addAction("✔ MARK ALL AS READ", Theme.GREEN, false, (id, row) -> Salon.markAllRead());
        return p;
    }

    private void profile() {
        if (user.role() == Role.CLIENT) {
            try {
                List<Object[]> c = Salon.query("SELECT phone,DATE_FORMAT(created_at,'%Y-%m-%d') FROM clients WHERE id=?", user.id());
                Ui.info(this, "Name: " + user.fullName() + "\nEmail: " + user.username() + "\nPhone: " + (c.isEmpty() ? "-" : c.get(0)[0]) +
                        "\nClient since: " + (c.isEmpty() ? "-" : c.get(0)[1]));
            } catch (Exception e) { Ui.error(this, e); }
            return;
        }
        List<Object[]> r;
        try { r = Salon.query("SELECT DATE_FORMAT(created_at,'%Y-%m-%d') FROM users WHERE id=?", user.id()); } catch (Exception e) { Ui.error(this, e); return; }
        String msg = "Name: " + user.fullName() + "\nUsername: " + user.username() + "\nRole: " + user.role().label +
                "\nMember since: " + (r.isEmpty() ? "-" : r.get(0)[0]);
        int c = JOptionPane.showOptionDialog(this, msg, "My Profile", JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE, null,
                new String[]{"Change Password", "Close"}, "Close");
        if (c == 0) Ui.changePassword(this, false);
    }

    // ---------- booking form (existing flow + staff assignment) ----------
    private JPanel bookingPage() {
        BackgroundPanel p = new BackgroundPanel(new BorderLayout(12, 12), 31);
        p.setBorder(new EmptyBorder(14, 14, 14, 14));
        RoundedPanel form = new RoundedPanel(new GridBagLayout(), 20);
        form.setBackground(Color.WHITE);
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(3, 8, 3, 8); g.fill = GridBagConstraints.HORIZONTAL; g.weightx = 1;

        JTextField name = Ui.field(""), phone = Ui.field(""), email = Ui.field(""),
                date = Ui.field(LocalDate.now().toString()), time = Ui.field("10:00");
        JComboBox<ServiceItem> service = new JComboBox<>();
        JComboBox<Staff> staff = new JComboBox<>();
        Theme.styleCombo(service); Theme.styleCombo(staff);
        JLabel price = new JLabel(" ");
        price.setFont(new Font("SansSerif", Font.BOLD, 15));
        price.setForeground(Theme.ROSE_GOLD);
        Runnable loadCombos = () -> {
            try {
                service.removeAllItems();
                for (ServiceItem s : Salon.serviceItems()) service.addItem(s);
                staff.removeAllItems();
                staff.addItem(new Staff(0, "— Unassigned —"));
                for (Staff s : Salon.staffList()) staff.addItem(s);
            } catch (Exception e) { Ui.error(p, e); }
        };
        loadCombos.run();
        Ui.register(loadCombos);
        service.addActionListener(e -> {
            ServiceItem s = (ServiceItem) service.getSelectedItem();
            price.setText(s == null ? " " : "Price: Rs. " + String.format("%,.0f", s.price()) + "  •  " + s.duration() + " min");
        });
        service.setSelectedIndex(service.getItemCount() > 0 ? 0 : -1);

        JLabel title = new JLabel("✨ NEW CLIENT BOOKING");
        title.setFont(Theme.H2); title.setForeground(Theme.PURPLE_DARK);
        g.gridx = 0; g.gridy = 0; g.gridwidth = 2; form.add(title, g); g.gridwidth = 1;
        String[] labels = {"Client Name *", "Phone *", "Email", "Service *", "Assign Staff", "Date (YYYY-MM-DD) *", "Time (HH:MM) *"};
        JComponent[] comps = {name, phone, email, service, staff, date, time};
        for (int i = 0; i < labels.length; i++) {
            g.gridy = i + 1; g.gridx = 0; g.weightx = 0.3; form.add(Ui.label(labels[i]), g);
            g.gridx = 1; g.weightx = 1; comps[i].setPreferredSize(new Dimension(330, 32)); form.add(comps[i], g);
        }
        g.gridx = 1; g.gridy = 8; form.add(price, g);
        JButton book = Theme.button("✨  BOOK CLIENT", Theme.ROSE_GOLD);
        g.gridy = 9; form.add(book, g);
        book.addActionListener(e -> {
            try {
                Staff st = (Staff) staff.getSelectedItem();
                LocalDate d; LocalTime t;
                try { d = LocalDate.parse(date.getText().trim()); t = LocalTime.parse(time.getText().trim()); }
                catch (Exception ex) { throw new IllegalArgumentException("Use date format YYYY-MM-DD and time format HH:MM (24-hour)."); }
                Salon.createBooking(name.getText(), phone.getText(), email.getText(), (ServiceItem) service.getSelectedItem(), d, t,
                        st == null || st.id() == 0 ? null : st.id());
                Ui.info(p, "✨ Booking saved!\n\nClient: " + name.getText().trim() + "\nService: " + service.getSelectedItem() + "\nDate: " + d + "  Time: " + t);
                name.setText(""); phone.setText(""); email.setText("");
                date.setText(LocalDate.now().toString()); time.setText("10:00");
                Ui.refreshAll();
            } catch (Exception ex) { Ui.error(p, ex); }
        });

        RoundedPanel help = new RoundedPanel(new BorderLayout(), 20);
        help.setBackground(new Color(255, 236, 243));
        help.add(new JLabel("<html><div style='padding:8px;color:#3c1e2d'><b style='font-size:15px;color:#7a3852'>How booking works</b><br><br>" +
                "1. Enter client details (existing phone numbers are matched automatically)<br>2. Choose a service and, if you like, a staff member<br>" +
                "3. Click BOOK CLIENT — this is for walk-in clients (online clients request their own bookings)<br>4. Staff mark the service In&nbsp;Progress and Completed<br>" +
                "5. Select the booking below → COMPLETE &amp; PAY (or Cancel)</div></html>"));
        JPanel top = new JPanel(new BorderLayout(12, 12));
        top.setOpaque(false);
        top.add(form, BorderLayout.WEST);
        top.add(help, BorderLayout.CENTER);
        top.setPreferredSize(new Dimension(100, 430));
        p.add(top, BorderLayout.NORTH);
        p.add(bookingsTable("ACTIVE BOOKINGS", "Select a client when they are ready."), BorderLayout.CENTER);
        return p;
    }

    private TablePage bookingsPage() {
        return bookingsTable("BOOKINGS & SCHEDULE", "Every booking and its stage: requested → confirmed by staff → paid → confirmed → service done.");
    }

    private TablePage bookingsTable(String title, String subtitle) {
        boolean[] all = {false};
        TablePage p = new TablePage(title, subtitle, BOOKING_COLS, () -> Salon.bookings(!all[0]));
        JCheckBox chk = new JCheckBox("Show completed & cancelled");
        chk.setOpaque(false);
        chk.setFont(Theme.BODY);
        chk.addActionListener(e -> { all[0] = chk.isSelected(); p.refresh(); });
        p.addToolbar(chk);
        if (user.role().can(Perm.PROCESS_PAYMENT)) p.addAction("✅ COMPLETE & PAY", Theme.GREEN, true, (id, row) -> payBooking(p, id, row));
        if (user.role().can(Perm.EDIT_BOOKING)) {
            p.addAction("✏ EDIT", Theme.ROSE_GOLD, true, (id, row) -> editBooking(p, id, row));
            p.addAction("❌ CANCEL", Theme.RED, true, (id, row) -> {
                if (JOptionPane.showConfirmDialog(p, "Cancel the booking for \"" + row[1] + "\"?\nNo payment will be added.", "Cancel Booking",
                        JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION) Salon.cancelBooking(id);
            });
        }
        return p;
    }

    private void payBooking(Component parent, int id, Object[] row) throws Exception {
        if ("PAID".equals(row[9]) || "CANCELLED".equals(row[7])) { Ui.warn(parent, "This booking is already paid or cancelled."); return; }
        String method = (String) JOptionPane.showInputDialog(parent, "Payment for " + row[1] + " — " + row[3] + "\nAmount: Rs. " +
                String.format("%,.0f", ((Number) row[8]).doubleValue()) + "\n\nChoose payment method:", "Complete & Pay",
                JOptionPane.QUESTION_MESSAGE, null, METHODS, METHODS[0]);
        if (method == null) return;
        double amt = Salon.completeAndPay(id, method);
        Ui.info(parent, "✨ Payment saved!\n\nRs. " + String.format("%,.0f", amt) + " received via " + method + ".\nThe booking is now COMPLETED.");
    }

    private void editBooking(Component parent, int id, Object[] row) throws Exception {
        JTextField date = Ui.field(String.valueOf(row[4])), time = Ui.field(String.valueOf(row[5]));
        JComboBox<Staff> staff = new JComboBox<>();
        Theme.styleCombo(staff);
        staff.addItem(new Staff(0, "— Unassigned —"));
        for (Staff s : Salon.staffList()) { staff.addItem(s); if (s.name().equals(row[6])) staff.setSelectedItem(s); }
        JComboBox<String> status = new JComboBox<>(new String[]{"BOOKED", "READY", "CONFIRMED"});
        Theme.styleCombo(status);
        if ("READY".equals(row[7]) || "CONFIRMED".equals(row[7])) status.setSelectedItem(row[7]);
        if (!Ui.form(parent, "Edit Booking #" + id + " — " + row[1], new String[]{"Date (YYYY-MM-DD)", "Time (HH:MM)", "Assigned staff", "Status"},
                new JComponent[]{date, time, staff, status})) return;
        Staff st = (Staff) staff.getSelectedItem();
        LocalDate d; LocalTime t;
        try { d = LocalDate.parse(date.getText().trim()); t = LocalTime.parse(time.getText().trim()); }
        catch (Exception ex) { throw new IllegalArgumentException("Use date format YYYY-MM-DD and time format HH:MM (24-hour)."); }
        Salon.updateBooking(id, d, t, st == null || st.id() == 0 ? null : st.id(), (String) status.getSelectedItem());
    }

    // ---------- clients / services / payments ----------
    private TablePage clientsPage() {
        TablePage p = new TablePage("CLIENTS", "Every registered client and their visit history.",
                new String[]{"ID", "Name", "Phone", "Email", "Notes", "Visits", "Total Spent (Rs.)", "Client Since"}, Salon::clients);
        if (user.role().can(Perm.REGISTER_CLIENT)) p.addAction("➕ REGISTER CLIENT", Theme.ROSE_GOLD, false, (id, row) -> {
            JTextField n = Ui.field(""), ph = Ui.field(""), em = Ui.field(""), no = Ui.field("");
            if (Ui.form(p, "Register New Client", new String[]{"Name *", "Phone *", "Email", "Notes"}, new JComponent[]{n, ph, em, no}))
                Salon.registerClient(n.getText(), ph.getText(), em.getText(), no.getText());
        });
        return p;
    }

    private TablePage servicesPage() {
        TablePage p = new TablePage("SERVICE MENU", "Services, prices and how many clients booked each one.",
                new String[]{"ID", "Service", "Category", "Duration (min)", "Price (Rs.)", "Clients Booked", "Photo"}, Salon::services);
        if (user.role().can(Perm.MANAGE_SERVICES)) {
            p.addAction("➕ ADD", Theme.ROSE_GOLD, false, (id, row) -> serviceDialog(p, null, null));
            p.addAction("🖼 PHOTO", Theme.MAGENTA, true, (id, row) -> {
                byte[] img = ImageUtil.chooseImage(p, 800, 600);   // opens the computer's file browser
                if (img != null) { Salon.setServiceImage(id, img); Ui.info(p, "Photo saved for \"" + row[1] + "\". Clients will see it on the Services page."); }
            });
            p.addAction("✏ EDIT", Theme.PINK, true, (id, row) -> serviceDialog(p, id, row));
            p.addAction("🗑 DELETE", Theme.RED, true, (id, row) -> {
                if (JOptionPane.showConfirmDialog(p, "Delete the service \"" + row[1] + "\"?", "Delete Service", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION)
                    Salon.deleteService(id, String.valueOf(row[1]));
            });
        }
        return p;
    }

    private void serviceDialog(Component parent, Integer id, Object[] row) throws Exception {
        JTextField n = Ui.field(row == null ? "" : String.valueOf(row[1])), c = Ui.field(row == null ? "" : String.valueOf(row[2])),
                d = Ui.field(row == null ? "60" : String.valueOf(row[3])), pr = Ui.field(row == null ? "1000" : String.valueOf(((Number) row[4]).intValue()));
        if (Ui.form(parent, id == null ? "Add Service" : "Edit Service", new String[]{"Service", "Category", "Duration (minutes)", "Price (Rs.)"}, new JComponent[]{n, c, d, pr}))
            Salon.saveService(id, n.getText(), c.getText(), d.getText(), pr.getText());
    }

    private TablePage paymentsPage() {
        boolean admin = user.role() == Role.ADMIN;
        TablePage p = new TablePage("PAYMENTS", admin ? "Every client payment and whether the staff member has confirmed it. Revenue counts confirmed payments only." : "Payment history — today's total shows payments you received.",
                new String[]{"Payment ID", "Client", "Phone", "Service", "Amount (Rs.)", "Method", "Paid At", "Status", "Confirmed By"}, Salon::payments);
        p.setInfo(() -> List.<Object[]>of(new Object[]{"Today: " + Salon.todayPaymentsText()}));
        if (user.role().can(Perm.PROCESS_PAYMENT)) p.addAction("💳 COLLECT PAYMENT", Theme.GREEN, false, (id, row) -> {
            List<Object[]> open = Salon.payableBookings();
            if (open.isEmpty()) { Ui.info(p, "There are no bookings waiting for payment."); return; }
            JComboBox<String> pick = new JComboBox<>();
            for (Object[] o : open) pick.addItem("#" + o[0] + " — " + o[1] + " — " + o[2] + " — Rs. " + String.format("%,.0f", ((Number) o[3]).doubleValue()));
            Theme.styleCombo(pick);
            JComboBox<String> method = new JComboBox<>(METHODS);
            Theme.styleCombo(method);
            if (!Ui.form(p, "Collect Payment", new String[]{"Booking", "Method"}, new JComponent[]{pick, method})) return;
            double amt = Salon.completeAndPay(((Number) open.get(pick.getSelectedIndex())[0]).intValue(), (String) method.getSelectedItem());
            Ui.info(p, "✨ Payment of Rs. " + String.format("%,.0f", amt) + " saved.");
        });
        return p;
    }

    // ---------- admin: reports and users ----------
    private JComponent reportsPage() {
        JTabbedPane t = new JTabbedPane();
        t.setFont(Theme.BOLD);
        t.addTab("Revenue by Service", new TablePage("REVENUE BY SERVICE", "Bookings, paid bookings and revenue per service.", new String[]{"Service", "Bookings", "Paid", "Revenue (Rs.)"}, () -> Salon.report("service")));
        t.addTab("By Payment Method", new TablePage("PAYMENT METHODS", "Revenue split by payment method.", new String[]{"Method", "Payments", "Total (Rs.)"}, () -> Salon.report("method")));
        t.addTab("Monthly Revenue", new TablePage("MONTHLY REVENUE", "Last 12 months with payments.", new String[]{"Month", "Payments", "Revenue (Rs.)"}, () -> Salon.report("month")));
        t.addTab("Staff Performance", new TablePage("STAFF PERFORMANCE", "Work completed by each staff account.", new String[]{"Staff", "Completed", "In Progress", "Pending", "Revenue (Rs.)"}, () -> Salon.report("staff")));
        t.addTab("Activity Log", new TablePage("ACTIVITY LOG", "Latest 500 important actions (logins, bookings, payments, admin changes).", new String[]{"ID", "Time", "User", "Action", "Details"}, () -> Salon.report("log")));
        return t;
    }

    private TablePage usersPage() {
        TablePage p = new TablePage("USERS & STAFF", "Create, edit, enable/disable accounts and reset passwords.",
                new String[]{"ID", "Full Name", "Username", "Role", "Status", "Created", "Must Change Pw"}, Salon::users);
        p.addAction("➕ ADD", Theme.ROSE_GOLD, false, (id, row) -> {
            JTextField n = Ui.field(""), u = Ui.field("");
            JComboBox<Role> role = new JComboBox<>(Role.accountRoles());
            Theme.styleCombo(role);
            JPasswordField pw = Ui.password();
            if (Ui.form(p, "Add User", new String[]{"Full name", "Username", "Role", "Temporary password"}, new JComponent[]{n, u, role, pw})) {
                Salon.createUser(n.getText(), u.getText(), (Role) role.getSelectedItem(), new String(pw.getPassword()));
                Ui.info(p, "User created. They must change the temporary password at first login.");
            }
        });
        p.addAction("✏ EDIT", Theme.PINK, true, (id, row) -> {
            JTextField n = Ui.field(String.valueOf(row[1]));
            JComboBox<Role> role = new JComboBox<>(Role.accountRoles());
            Theme.styleCombo(role);
            role.setSelectedItem(Role.valueOf((String) row[3]));
            JCheckBox active = new JCheckBox("Account active", "ACTIVE".equals(row[4]));
            active.setBackground(Color.WHITE);
            if (Ui.form(p, "Edit " + row[2], new String[]{"Full name", "Role", "Status"}, new JComponent[]{n, role, active}))
                Salon.updateUser(id, n.getText(), (Role) role.getSelectedItem(), active.isSelected());
        });
        p.addAction("⛔ ENABLE / DISABLE", Theme.ORANGE, true, (id, row) ->
                Salon.updateUser(id, String.valueOf(row[1]), Role.valueOf((String) row[3]), !"ACTIVE".equals(row[4])));
        p.addAction("🔑 RESET PASSWORD", Theme.GREEN, true, (id, row) -> {
            JPasswordField pw = Ui.password();
            if (Ui.form(p, "Reset password for " + row[2], new String[]{"Temporary password"}, new JComponent[]{pw})) {
                Salon.resetPassword(id, new String(pw.getPassword()));
                Ui.info(p, "Password reset. The user must choose a new one at next login.");
            }
        });
        return p;
    }

    // ---------- client workspace ----------
    private void requestDialog(Salon.ServiceCard card) {
        try {
            JComboBox<Staff> staff = new JComboBox<>();
            Theme.styleCombo(staff);
            staff.addItem(new Staff(0, "— Any available staff —"));
            for (Staff st : Salon.staffChoices()) staff.addItem(st);
            JSpinner date = Ui.dateSpinner(LocalDate.now().plusDays(1)), time = Ui.timeSpinner(LocalTime.of(11, 0));
            JTextField note = Ui.field("");
            JLabel svc = new JLabel(card.name() + "  •  Rs. " + String.format("%,.0f", card.price()) + "  •  " + card.duration() + " min");
            svc.setFont(Theme.BOLD); svc.setForeground(Theme.ROSE_GOLD);
            if (!Ui.form(this, "Request a booking", new String[]{"Service", "Preferred staff", "Preferred date", "Preferred time", "Note (optional)"},
                    new JComponent[]{svc, staff, date, time, note})) return;
            Staff st = (Staff) staff.getSelectedItem();
            Salon.requestBooking(new ServiceItem(card.id(), card.name(), card.duration(), card.price()), st == null || st.id() == 0 ? null : st.id(),
                    Ui.dateOf(date), Ui.timeOf(time), note.getText());
            Ui.info(this, "✨ Request sent!\n\nOur staff will check your preferred time, set the final time and confirm.\nYou will get a notification here as soon as it is confirmed — then you can pay.");
            Ui.refreshAll();
        } catch (Exception e) { Ui.error(this, e); }
    }

    private TablePage clientBookingsPage() {
        TablePage p = new TablePage("MY BOOKINGS & PAYMENTS", "Request → staff confirms → you pay → staff confirms payment → service → your feedback.",
                new String[]{"ID", "Service", "Date", "Time", "Staff", "Status", "Amount (Rs.)", "Payment", "Feedback"}, Salon::myBookings);
        p.setInfo(() -> List.<Object[]>of(new Object[]{Salon.myDueText()}));
        p.addAction("💳 PAY NOW", Theme.GREEN, true, (id, row) -> {
            if ("REQUESTED".equals(row[5])) { Ui.warn(p, "This request is still waiting for our staff to confirm it. You can pay as soon as it is confirmed."); return; }
            if ("PENDING".equals(row[7])) { Ui.warn(p, "You already paid. Waiting for the staff member to confirm your payment."); return; }
            if ("PAID".equals(row[7]) || "CANCELLED".equals(row[5])) { Ui.warn(p, "This booking is already paid or cancelled."); return; }
            JComboBox<String> method = new JComboBox<>(Salon.PAY_METHODS);
            Theme.styleCombo(method);
            JLabel what = new JLabel(row[1] + "  •  " + row[2] + " " + row[3]);
            what.setFont(Theme.BOLD);
            JLabel amt = new JLabel("Rs. " + String.format("%,.0f", ((Number) row[6]).doubleValue()));
            amt.setFont(Theme.serif(Font.BOLD, 20)); amt.setForeground(Theme.ROSE_GOLD);
            if (!Ui.form(p, "Pay to Beauty Bloom Salon", new String[]{"Booking", "Amount", "Pay with"}, new JComponent[]{what, amt, method})) return;
            double paid = Salon.payMyBooking(id, (String) method.getSelectedItem());
            Ui.info(p, "✨ Payment submitted!\n\nRs. " + String.format("%,.0f", paid) + " was sent via " + method.getSelectedItem() +
                    ".\nYour stylist has been asked to confirm it — you will get a notification.\n\n(Demo payment — no real money is moved.)");
        });
        p.addAction("✍ FEEDBACK", Theme.PINK, true, (id, row) -> feedbackDialog(p, id));
        p.addAction("❌ CANCEL", Theme.RED, true, (id, row) -> {
            if (JOptionPane.showConfirmDialog(p, "Cancel your booking for \"" + row[1] + "\"?", "Cancel Booking", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION)
                Salon.cancelMyBooking(id);
        });
        return p;
    }

    /** Rating + comment + optional photo (picked from the computer). preselect = booking id, or null to choose. */
    private void feedbackDialog(Component parent, Integer preselect) {
        try {
            List<Object[]> open = Salon.feedbackEligible();
            if (preselect != null) open.removeIf(o -> ((Number) o[0]).intValue() != preselect);
            if (open.isEmpty()) {
                Ui.info(parent, preselect != null ? "Feedback can be given once the service is completed (and only once per booking)."
                        : "You have no completed services waiting for feedback yet.");
                return;
            }
            JComboBox<String> pick = new JComboBox<>();
            for (Object[] o : open) pick.addItem("#" + o[0] + " — " + o[1] + " (" + o[2] + ")");
            Theme.styleCombo(pick);
            JComboBox<String> rating = new JComboBox<>(new String[]{"5 ★★★★★  Excellent", "4 ★★★★☆  Very good", "3 ★★★☆☆  Good", "2 ★★☆☆☆  Fair", "1 ★☆☆☆☆  Poor"});
            Theme.styleCombo(rating);
            JTextArea comment = new JTextArea(4, 28);
            comment.setLineWrap(true); comment.setWrapStyleWord(true); comment.setFont(Theme.BODY);
            byte[][] photo = {null};
            JLabel photoName = new JLabel("No photo");
            photoName.setForeground(Theme.MUTED);
            JButton add = Theme.button("📷 Add photo…", Theme.MAGENTA);
            add.addActionListener(e -> {
                try {
                    byte[] img = ImageUtil.chooseImage(parent, 900, 700);
                    if (img != null) { photo[0] = img; photoName.setText("Photo added (" + img.length / 1024 + " KB)"); }
                } catch (Exception ex) { Ui.error(parent, ex); }
            });
            JPanel form = new JPanel(new GridBagLayout());
            form.setBackground(Color.WHITE);
            GridBagConstraints g = new GridBagConstraints();
            g.fill = GridBagConstraints.HORIZONTAL; g.insets = new Insets(4, 4, 4, 4); g.weightx = 1; g.gridx = 0;
            int y = 0;
            g.gridy = y++; form.add(Ui.label("Service"), g);
            g.gridy = y++; form.add(pick, g);
            g.gridy = y++; form.add(Ui.label("Your rating"), g);
            g.gridy = y++; form.add(rating, g);
            g.gridy = y++; form.add(Ui.label("Your comment"), g);
            g.gridy = y++; form.add(new JScrollPane(comment), g);
            JPanel photoRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
            photoRow.setOpaque(false);
            photoRow.add(add); photoRow.add(photoName);
            g.gridy = y; form.add(photoRow, g);
            if (JOptionPane.showConfirmDialog(parent, form, "Share your feedback", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
            int bookingId = ((Number) open.get(pick.getSelectedIndex())[0]).intValue();
            Salon.submitFeedback(bookingId, 5 - rating.getSelectedIndex(), comment.getText(), photo[0]);
            Ui.info(parent, "💖 Thank you! Your feedback was sent to the salon owner and your stylist, and it now appears in Reviews.");
            Ui.refreshAll();
        } catch (Exception e) { Ui.error(parent, e); }
    }

    // ---------- staff ----------
    private TablePage requestsPage() {
        TablePage p = new TablePage("NEW BOOKING REQUESTS", "Set the time that suits you and confirm — the client is notified and can then pay.",
                new String[]{"ID", "Client", "Phone", "Service", "Duration (min)", "Wanted Date", "Wanted Time", "Client Note", "Sent To"}, Salon::requests);
        p.addAction("✔ SET TIME & CONFIRM", Theme.GREEN, true, (id, row) -> {
            JSpinner d = Ui.dateSpinner(LocalDate.parse(String.valueOf(row[5]))), t = Ui.timeSpinner(LocalTime.parse(String.valueOf(row[6])));
            JLabel what = new JLabel(row[1] + " — " + row[3] + " (" + row[4] + " min)");
            what.setFont(Theme.BOLD);
            if (Ui.form(p, "Confirm request #" + id, new String[]{"Request", "Date", "Time"}, new JComponent[]{what, d, t})) {
                Salon.confirmRequest(id, Ui.dateOf(d), Ui.timeOf(t));
                Ui.info(p, "✅ Confirmed. " + row[1] + " has been notified and can now pay.");
            }
        });
        p.addAction("✖ DECLINE", Theme.RED, true, (id, row) -> {
            String why = JOptionPane.showInputDialog(p, "Reason for declining (shown to the client):", "Decline request", JOptionPane.QUESTION_MESSAGE);
            if (why != null) Salon.declineRequest(id, why);
        });
        return p;
    }

    private TablePage confirmPaymentsPage() {
        TablePage p = new TablePage("PAYMENTS TO CONFIRM", "Clients tell us they paid. Confirm once you have received the money — the client and the owner are notified.",
                new String[]{"ID", "Client", "Service", "Visit Date", "Amount (Rs.)", "Method", "Paid At", "Booking"}, Salon::pendingPayments);
        p.addAction("✔ CONFIRM PAYMENT", Theme.GREEN, true, (id, row) -> {
            if (JOptionPane.showConfirmDialog(p, "Confirm that you received Rs. " + String.format("%,.0f", ((Number) row[4]).doubleValue()) + " from " + row[1] + " (" + row[5] + ")?",
                    "Confirm Payment", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) Salon.confirmPayment(id);
        });
        p.addAction("✖ NOT RECEIVED", Theme.RED, true, (id, row) -> {
            String why = JOptionPane.showInputDialog(p, "Why is the payment rejected? (shown to the client):", "Payment not received", JOptionPane.QUESTION_MESSAGE);
            if (why != null) Salon.rejectPayment(id, why);
        });
        return p;
    }

    private static final String[] STAFF_COLS = {"ID", "Date", "Time", "Client", "Phone", "Client Notes", "Service", "Duration (min)", "Status", "Payment"};

    private TablePage myAppointments() {
        TablePage p = new TablePage("MY ASSIGNED APPOINTMENTS", "Only appointments assigned to your account are shown.", STAFF_COLS, () -> Salon.myAppointments(false));
        p.addAction("▶ START SERVICE", Theme.ROSE_GOLD, true, (id, row) -> Salon.setProgress(id, "IN_PROGRESS"));
        p.addAction("✔ MARK COMPLETED", Theme.GREEN, true, (id, row) -> {
            if (JOptionPane.showConfirmDialog(p, "Mark the service for " + row[3] + " as completed?", "Complete Service", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION)
                Salon.setProgress(id, "COMPLETED");
        });
        return p;
    }

    private TablePage myHistory() {
        return new TablePage("MY WORK HISTORY", "Your completed and cancelled appointments.", STAFF_COLS, () -> Salon.myAppointments(true));
    }
}
