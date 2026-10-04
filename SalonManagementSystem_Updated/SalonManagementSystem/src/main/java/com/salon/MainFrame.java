package com.salon;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

public class MainFrame extends JFrame {
    private final JTabbedPane tabs = new JTabbedPane();
    private final JLabel revenueLabel = new JLabel("Rs. 0");
    private final JLabel todayRevenueLabel = new JLabel("Rs. 0");
    private final JLabel bookingsLabel = new JLabel("0");
    private final JLabel clientsLabel = new JLabel("0");
    private final JLabel pendingLabel = new JLabel("0");
    private final ChartPanel chart = new ChartPanel();

    private JTable bookingTable, paymentTable, clientTable, serviceTable;
    private DefaultTableModel bookingModel, paymentModel, clientModel, serviceModel;
    private JLabel paymentTodayLabel;

    public MainFrame() {
        setTitle("✨ Glow & Grace — Salon Management System");
        setSize(1280, 820);
        setMinimumSize(new Dimension(1050, 700));
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        getContentPane().setBackground(Theme.BG);

        tabs.setFont(new Font("SansSerif", Font.BOLD, 14));
        tabs.setBackground(Color.WHITE);
        tabs.setForeground(Theme.TEXT);
        tabs.setBorder(BorderFactory.createMatteBorder(0, 0, 2, 0, Theme.PINK));

        tabs.addTab("🏠  Dashboard", dashboard());
        tabs.addTab("📅  Book Client", bookingPage());
        tabs.addTab("👩  Clients", clientsPage());
        tabs.addTab("💇  Services", servicesPage());
        tabs.addTab("💳  Payments", paymentsPage());

        JPanel header = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics gr) {
                Graphics2D g2 = (Graphics2D) gr.create();
                g2.setPaint(new GradientPaint(0, 0, Theme.PURPLE_DARK, getWidth(), getHeight(), Theme.MAGENTA));
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.dispose();
                super.paintComponent(gr);
            }
        };
        header.setOpaque(false);
        header.setBorder(new EmptyBorder(13, 22, 13, 22));

        JLabel brand = new JLabel("✦ GLOW & GRACE  |  SALON MANAGEMENT");
        brand.setForeground(Color.WHITE);
        brand.setFont(new Font("SansSerif", Font.BOLD, 24));

        JLabel sub = new JLabel("Book  •  Style  •  Complete  •  Pay  •  Grow");
        sub.setForeground(Color.WHITE);
        sub.setFont(new Font("SansSerif", Font.BOLD, 14));

        header.add(brand, BorderLayout.WEST);
        header.add(sub, BorderLayout.EAST);

        add(header, BorderLayout.NORTH);
        add(tabs, BorderLayout.CENTER);
        refreshAll();
    }

    private JPanel dashboard() {
        JPanel p = base();
        p.setLayout(new BorderLayout(14, 14));

        RoundedPanel welcome = new RoundedPanel(new BorderLayout(), 22);
        welcome.setBackground(Theme.PURPLE_DARK);
        JLabel w = new JLabel("<html><div style='padding:8px'><font color='white' size='6'>Welcome to Glow & Grace ✨</font><br><font color='white' size='4'>Your salon command center — bookings, clients, services and payments in one place.</font></div></html>");
        welcome.add(w, BorderLayout.CENTER);

        JPanel cards = new JPanel(new GridLayout(1, 4, 12, 12));
        cards.setOpaque(false);
        cards.add(statCard("💰", "TOTAL REVENUE", revenueLabel, Theme.VALUE_REVENUE));
        cards.add(statCard("📅", "ACTIVE BOOKINGS", bookingsLabel, Theme.VALUE_BOOKINGS));
        cards.add(statCard("👩", "TOTAL CLIENTS", clientsLabel, Theme.VALUE_CLIENTS));
        cards.add(statCard("⏳", "WAITING / BOOKED", pendingLabel, Theme.VALUE_PENDING));

        RoundedPanel graphCard = new RoundedPanel(new BorderLayout(5, 5), 20);
        graphCard.setBackground(Color.WHITE);
        JLabel gt = new JLabel("📊 Revenue — Last 7 Days");
        gt.setFont(Theme.H2);
        gt.setForeground(Theme.TEXT);
        graphCard.add(gt, BorderLayout.NORTH);
        graphCard.add(chart, BorderLayout.CENTER);

        p.add(welcome, BorderLayout.NORTH);
        p.add(cards, BorderLayout.CENTER);
        p.add(graphCard, BorderLayout.SOUTH);
        return p;
    }

    private JPanel statCard(String icon, String title, JLabel value, Color accent) {
        RoundedPanel c = new RoundedPanel(new BorderLayout(10, 2), 20);
        c.setBackground(Color.WHITE);
        JLabel i = new JLabel(icon);
        i.setFont(new Font("SansSerif", Font.PLAIN, 28));
        JLabel t = new JLabel(title);
        t.setFont(new Font("SansSerif", Font.BOLD, 11));
        t.setForeground(Theme.MUTED);
        value.setFont(new Font("SansSerif", Font.BOLD, 23));
        value.setForeground(accent);
        c.add(i, BorderLayout.WEST);
        JPanel x = new JPanel(new GridLayout(2, 1));
        x.setOpaque(false);
        x.add(t);
        x.add(value);
        c.add(x, BorderLayout.CENTER);
        return c;
    }

    private JPanel bookingPage() {
        JPanel p = base();
        p.setLayout(new BorderLayout(12, 12));

        RoundedPanel form = new RoundedPanel(new GridBagLayout(), 20);
        form.setBackground(Color.WHITE);
        GridBagConstraints g = gbcCompact();

        JTextField name = fieldCompact();
        JTextField phone = fieldCompact();
        JTextField email = fieldCompact();
        JTextField date = fieldCompact();
        JTextField time = fieldCompact();
        JComboBox<ServiceItem> service = new JComboBox<>();
        Theme.styleCombo(service);
        service.setPreferredSize(new Dimension(330, 32));
        date.setText(LocalDate.now().toString());
        time.setText("10:00");

        loadServices(service);
        JLabel price = new JLabel("Price: Rs. 0");
        price.setFont(new Font("SansSerif", Font.BOLD, 15));
        price.setForeground(Theme.PINK);

        service.addActionListener(e -> {
            ServiceItem s = (ServiceItem) service.getSelectedItem();
            if (s != null) price.setText("Price: Rs. " + String.format("%.0f", s.price) + "  •  " + s.duration + " min");
        });

        JLabel title = new JLabel("✨ NEW CLIENT BOOKING");
        title.setFont(new Font("SansSerif", Font.BOLD, 18));
        title.setForeground(Theme.PURPLE);
        g.gridx = 0; g.gridy = 0; g.gridwidth = 2; form.add(title, g);
        g.gridwidth = 1;

        addField(form, g, 1, "Client Name *", name);
        addField(form, g, 2, "Phone *", phone);
        addField(form, g, 3, "Email", email);
        addField(form, g, 4, "Service *", service);
        addField(form, g, 5, "Date (YYYY-MM-DD) *", date);
        addField(form, g, 6, "Time (HH:MM) *", time);

        g.gridx = 1; g.gridy = 7; form.add(price, g);
        JButton book = new JButton("✨  BOOK CLIENT");
        Theme.styleButton(book, Theme.PINK);
        g.gridy = 8; form.add(book, g);

        book.addActionListener(e -> saveBooking(name, phone, email, date, time, service));

        RoundedPanel help = new RoundedPanel(new BorderLayout(8, 8), 20);
        help.setBackground(new Color(255, 228, 240));
        JLabel helpText = new JLabel("<html><div style='padding:6px'><font color='#96305f' size='4'><b>💇 Services</b></font><br><br><font color='#3c1e2d' size='3'>Hair Cut • Hair Styling • Hair Color<br>Facial Glow • Manicure • Pedicure<br>Party Ready • Wedding Ready<br>Bridal Package • Makeup • Spa Relax<br><br><b>Booking flow:</b><br>1. Enter client details<br>2. Choose service + time<br>3. Click BOOK CLIENT<br>4. Client gets ready<br>5. Select booking → Complete & Pay <b>or</b> Cancel<br>6. On payment: client + service stay saved in Clients/Payments, booking leaves Active list</font></div></html>");
        help.add(helpText, BorderLayout.CENTER);

        JPanel top = new JPanel(new BorderLayout(12, 12));
        top.setOpaque(false);
        top.add(form, BorderLayout.WEST);
        top.add(help, BorderLayout.CENTER);
        // Fixed, generous-but-compact height: comfortably fits the title,
        // all 6 fields, price line and the BOOK CLIENT button (previously
        // this either clipped the button when too short, or ballooned and
        // pushed the Active Bookings table off-screen when left unbounded).
        // Width is ignored by BorderLayout's NORTH slot, so only height matters.
        top.setPreferredSize(new Dimension(100, 440));

        bookingModel = model("ID", "Client", "Phone", "Service", "Date", "Time", "Status", "Amount");
        bookingTable = table(bookingModel);
        JScrollPane scroll = new JScrollPane(bookingTable);
        scroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER, 2));

        JLabel activeTitle = new JLabel("📋 ACTIVE BOOKINGS — select a client when they are ready");
        activeTitle.setFont(new Font("SansSerif", Font.BOLD, 17));
        activeTitle.setForeground(Theme.TEXT);

        JButton complete = new JButton("✅  DONE — COMPLETE & PAY");
        Theme.styleButton(complete, Theme.GREEN);
        complete.addActionListener(e -> completeSelectedBooking());

        JButton cancel = new JButton("❌  CANCEL BOOKING");
        Theme.styleButton(cancel, Theme.RED);
        cancel.addActionListener(e -> cancelSelectedBooking());

        JPanel actionButtons = new JPanel(new GridLayout(1, 2, 10, 0));
        actionButtons.setOpaque(false);
        actionButtons.add(complete);
        actionButtons.add(cancel);

        JPanel bottom = new JPanel(new BorderLayout(8, 8));
        bottom.setOpaque(false);
        bottom.add(activeTitle, BorderLayout.NORTH);
        bottom.add(scroll, BorderLayout.CENTER);
        bottom.add(actionButtons, BorderLayout.SOUTH);

        p.add(top, BorderLayout.NORTH);
        p.add(bottom, BorderLayout.CENTER);
        return p;
    }

    private void saveBooking(JTextField name, JTextField phone, JTextField email,
                             JTextField date, JTextField time, JComboBox<ServiceItem> service) {
        try {
            String n = name.getText().trim();
            String ph = phone.getText().trim();
            if (n.isEmpty() || ph.isEmpty() || service.getSelectedItem() == null) {
                throw new IllegalArgumentException("Client name, phone and service are required.");
            }
            LocalDate d = LocalDate.parse(date.getText().trim());
            LocalTime t = LocalTime.parse(time.getText().trim());
            ServiceItem s = (ServiceItem) service.getSelectedItem();

            try (Connection c = DB.getConnection()) {
                c.setAutoCommit(false);
                try {
                    int clientId;
                    try (PreparedStatement ps = c.prepareStatement("SELECT id FROM clients WHERE phone=?")) {
                        ps.setString(1, ph);
                        try (ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) {
                                clientId = rs.getInt(1);
                                try (PreparedStatement update = c.prepareStatement("UPDATE clients SET name=?, email=? WHERE id=?")) {
                                    update.setString(1, n);
                                    update.setString(2, email.getText().trim());
                                    update.setInt(3, clientId);
                                    update.executeUpdate();
                                }
                            } else {
                                try (PreparedStatement ins = c.prepareStatement(
                                        "INSERT INTO clients(name,phone,email) VALUES(?,?,?)",
                                        Statement.RETURN_GENERATED_KEYS)) {
                                    ins.setString(1, n);
                                    ins.setString(2, ph);
                                    ins.setString(3, email.getText().trim());
                                    ins.executeUpdate();
                                    try (ResultSet keys = ins.getGeneratedKeys()) {
                                        if (!keys.next()) throw new SQLException("Could not create client.");
                                        clientId = keys.getInt(1);
                                    }
                                }
                            }
                        }
                    }

                    String q = "INSERT INTO bookings(client_id,service_id,booking_date,booking_time,status,total_amount) VALUES(?,?,?,?,?,?)";
                    try (PreparedStatement ps = c.prepareStatement(q)) {
                        ps.setInt(1, clientId);
                        ps.setInt(2, s.id);
                        ps.setDate(3, Date.valueOf(d));
                        ps.setTime(4, Time.valueOf(t));
                        ps.setString(5, "BOOKED");
                        ps.setDouble(6, s.price);
                        ps.executeUpdate();
                    }
                    c.commit();
                } catch (Exception ex) {
                    c.rollback();
                    throw ex;
                }
            }

            JOptionPane.showMessageDialog(this,
                    "✨ Booking saved successfully!\n\nClient: " + n +
                            "\nService: " + s.name +
                            "\nDate: " + d + "\nTime: " + t,
                    "Booking Saved", JOptionPane.INFORMATION_MESSAGE);
            name.setText("");
            phone.setText("");
            email.setText("");
            date.setText(LocalDate.now().toString());
            time.setText("10:00");
            refreshAll();
            tabs.setSelectedIndex(1);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Booking could not be saved.\n\n" + ex.getMessage(),
                    "Booking Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private JPanel clientsPage() {
        JPanel p = base();
        clientModel = model("Booking ID", "Client", "Phone", "Email", "Service", "Date", "Time", "Amount (Rs.)", "Status");
        clientTable = table(clientModel);

        JPanel top = new JPanel(new BorderLayout(10, 8));
        top.setOpaque(false);
        top.add(sectionTitle("👩 CLIENTS — READY TO COMPLETE",
                "Select a client below, then choose COMPLETE & PAY or CANCEL BOOKING. Completed/cancelled clients leave this list."),
                BorderLayout.CENTER);

        JButton complete = new JButton("✅  COMPLETE & PAY");
        Theme.styleButton(complete, Theme.GREEN);
        complete.addActionListener(e -> completeSelectedClientBooking());

        JButton cancel = new JButton("❌  CANCEL BOOKING");
        Theme.styleButton(cancel, Theme.RED);
        cancel.addActionListener(e -> cancelSelectedClientBooking());

        JPanel actions = new JPanel(new GridLayout(1, 2, 10, 0));
        actions.setOpaque(false);
        actions.add(complete);
        actions.add(cancel);
        top.add(actions, BorderLayout.SOUTH);

        p.add(top, BorderLayout.NORTH);
        p.add(new JScrollPane(clientTable), BorderLayout.CENTER);
        return p;
    }

    private JPanel servicesPage() {
        JPanel p = base();
        serviceModel = model("ID", "Service", "Category", "Duration (min)", "Price (Rs.)", "CLIENTS BOOKED");
        serviceTable = table(serviceModel);

        RoundedPanel top = new RoundedPanel(new BorderLayout(10, 5), 18);
        top.setBackground(Color.WHITE);
        JLabel title = new JLabel("💇 SERVICE MENU + CLIENT COUNTER");
        title.setFont(Theme.H2);
        title.setForeground(Theme.PURPLE);
        JLabel note = new JLabel("New services start at 0. Every new booking increases that service's client count by 1.");
        note.setFont(Theme.BODY);
        note.setForeground(Theme.TEXT);
        JPanel left = new JPanel(new GridLayout(2, 1));
        left.setOpaque(false);
        left.add(title); left.add(note);
        top.add(left, BorderLayout.CENTER);

        JButton add = new JButton("➕ ADD SERVICE");
        Theme.styleButton(add, Theme.PURPLE);
        add.addActionListener(e -> addService());

        JButton delete = new JButton("🗑️ DELETE SERVICE");
        Theme.styleButton(delete, Theme.RED);
        delete.addActionListener(e -> deleteSelectedService());

        JPanel serviceButtons = new JPanel(new GridLayout(1, 2, 10, 0));
        serviceButtons.setOpaque(false);
        serviceButtons.add(add);
        serviceButtons.add(delete);
        top.add(serviceButtons, BorderLayout.EAST);

        p.add(top, BorderLayout.NORTH);
        p.add(new JScrollPane(serviceTable), BorderLayout.CENTER);
        return p;
    }

    private JPanel paymentsPage() {
        JPanel p = base();
        paymentModel = model("Payment ID", "Client", "Phone", "Service", "Amount (Rs.)", "Method", "Paid At");
        paymentTable = table(paymentModel);

        RoundedPanel top = new RoundedPanel(new BorderLayout(12, 8), 18);
        top.setBackground(Color.WHITE);
        JLabel info = new JLabel("💳 TODAY'S PAYMENT");
        info.setFont(Theme.H2);
        info.setForeground(Theme.PURPLE);
        paymentTodayLabel = new JLabel("Rs. 0");
        paymentTodayLabel.setFont(new Font("SansSerif", Font.BOLD, 25));
        paymentTodayLabel.setForeground(Theme.GREEN);

        JPanel amount = new JPanel(new GridLayout(2, 1));
        amount.setOpaque(false);
        amount.add(info); amount.add(paymentTodayLabel);
        top.add(amount, BorderLayout.WEST);

        JLabel desc = new JLabel("Every completed booking's payment lands here automatically, with the customer and service it was for.");
        desc.setForeground(Theme.TEXT);
        desc.setFont(Theme.BODY);
        top.add(desc, BorderLayout.CENTER);

        JButton refresh = new JButton("↻ REFRESH");
        Theme.styleButton(refresh, Theme.MAGENTA);
        refresh.addActionListener(e -> refreshAll());
        top.add(refresh, BorderLayout.EAST);

        p.add(top, BorderLayout.NORTH);
        p.add(new JScrollPane(paymentTable), BorderLayout.CENTER);
        return p;
    }

    private JPanel sectionTitle(String title, String subtitle) {
        JPanel box = new JPanel(new GridLayout(2, 1));
        box.setOpaque(false);
        JLabel a = new JLabel(title);
        a.setFont(Theme.H2);
        a.setForeground(Theme.PURPLE);
        JLabel b = new JLabel(subtitle);
        b.setFont(Theme.BODY);
        b.setForeground(Theme.TEXT);
        box.add(a); box.add(b);
        return box;
    }

    private JTextField field() {
        JTextField f = new JTextField();
        Theme.styleField(f);
        f.setPreferredSize(new Dimension(330, 40));
        return f;
    }

    private JTextField fieldCompact() {
        JTextField f = new JTextField();
        Theme.styleField(f);
        f.setPreferredSize(new Dimension(330, 32));
        return f;
    }

    private int bgSeed = 100;

    private JPanel base() {
        // A fresh colorful "salon glam" background per tab (different seed
        // so each page's blob/sparkle pattern looks a little different).
        BackgroundPanel p = new BackgroundPanel(new BorderLayout(12, 12), bgSeed);
        bgSeed += 17;
        p.setBorder(new EmptyBorder(14, 14, 14, 14));
        return p;
    }

    private void refreshAll() {
        loadBookings();
        loadClients();
        loadServicesTable();
        loadPayments();
        loadDashboard();
    }

    private void loadBookings() {
        if (bookingModel == null) return;
        bookingModel.setRowCount(0);
        String q = """
            SELECT b.id,c.name,c.phone,s.name,b.booking_date,b.booking_time,b.status,b.total_amount
            FROM bookings b
            JOIN clients c ON b.client_id=c.id
            JOIN services s ON b.service_id=s.id
            WHERE b.status IN ('BOOKED','READY')
            ORDER BY b.booking_date,b.booking_time
            """;
        try (Connection c = DB.getConnection(); PreparedStatement ps = c.prepareStatement(q); ResultSet r = ps.executeQuery()) {
            while (r.next()) {
                bookingModel.addRow(new Object[]{
                        r.getInt(1), r.getString(2), r.getString(3), r.getString(4),
                        r.getDate(5), r.getTime(6), r.getString(7), r.getDouble(8)
                });
            }
        } catch (SQLException e) { dbError(e); }
    }

    private void completeSelectedBooking() {
        int row = bookingTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Please select a client from Active Bookings first.");
            return;
        }
        int id = (int) bookingModel.getValueAt(row, 0);
        double amount = ((Number) bookingModel.getValueAt(row, 7)).doubleValue();
        String[] methods = {"Cash", "Card", "Bank Transfer", "JazzCash / Easypaisa"};
        String method = (String) JOptionPane.showInputDialog(this, "Choose payment method:", "Complete & Pay",
                JOptionPane.QUESTION_MESSAGE, null, methods, methods[0]);
        if (method == null) return;

        try (Connection c = DB.getConnection()) {
            c.setAutoCommit(false);
            try {
                int clientId;
                try (PreparedStatement ps = c.prepareStatement("SELECT client_id FROM bookings WHERE id=?")) {
                    ps.setInt(1, id);
                    try (ResultSet r = ps.executeQuery()) {
                        if (!r.next()) throw new SQLException("Booking not found.");
                        clientId = r.getInt(1);
                    }
                }
                try (PreparedStatement ps = c.prepareStatement("INSERT INTO payments(booking_id,client_id,amount,method) VALUES(?,?,?,?)")) {
                    ps.setInt(1, id); ps.setInt(2, clientId); ps.setDouble(3, amount); ps.setString(4, method); ps.executeUpdate();
                }
                try (PreparedStatement ps = c.prepareStatement("UPDATE bookings SET status='COMPLETED' WHERE id=?")) {
                    ps.setInt(1, id); ps.executeUpdate();
                }
                c.commit();
                JOptionPane.showMessageDialog(this,
                        "✨ Client completed!\nPayment: Rs. " + String.format("%.0f", amount) +
                                "\nAdded to salon account.\nBooking removed from active bookings.",
                        "Payment Saved", JOptionPane.INFORMATION_MESSAGE);
                refreshAll();
            } catch (Exception ex) {
                c.rollback();
                throw ex;
            }
        } catch (Exception e) { dbError(new SQLException(e.getMessage())); }
    }

    private void cancelSelectedBooking() {
        int row = bookingTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Please select a client from Active Bookings first.");
            return;
        }
        int id = (int) bookingModel.getValueAt(row, 0);
        String clientName = String.valueOf(bookingModel.getValueAt(row, 1));
        int confirm = JOptionPane.showConfirmDialog(this,
                "Cancel the booking for \"" + clientName + "\"?\nThis cannot be undone.",
                "Cancel Booking", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) return;

        try (Connection c = DB.getConnection(); PreparedStatement ps = c.prepareStatement("UPDATE bookings SET status='CANCELLED' WHERE id=?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
            JOptionPane.showMessageDialog(this, "Booking for \"" + clientName + "\" was cancelled.",
                    "Booking Cancelled", JOptionPane.INFORMATION_MESSAGE);
            refreshAll();
        } catch (SQLException e) { dbError(e); }
    }

    private void loadClients() {
        if (clientModel == null) return;
        clientModel.setRowCount(0);
        String q = """
            SELECT b.id,c.name,c.phone,c.email,s.name,b.booking_date,b.booking_time,b.total_amount,b.status
            FROM bookings b
            JOIN clients c ON b.client_id=c.id
            JOIN services s ON b.service_id=s.id
            WHERE b.status IN ('BOOKED','READY')
            ORDER BY b.booking_date,b.booking_time,b.id
            """;
        try (Connection c = DB.getConnection(); PreparedStatement ps = c.prepareStatement(q); ResultSet r = ps.executeQuery()) {
            while (r.next()) clientModel.addRow(new Object[]{
                    r.getInt(1), r.getString(2), r.getString(3), r.getString(4),
                    r.getString(5), r.getDate(6), r.getTime(7), r.getDouble(8), r.getString(9)
            });
        } catch (SQLException e) { dbError(e); }
    }

    private void completeSelectedClientBooking() {
        int row = clientTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this,
                    "Please select a client from the Clients tab first.",
                    "Select Client", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int bookingId = ((Number) clientTable.getValueAt(row, 0)).intValue();
        double amount = ((Number) clientTable.getValueAt(row, 7)).doubleValue();
        String clientName = String.valueOf(clientTable.getValueAt(row, 1));
        String serviceName = String.valueOf(clientTable.getValueAt(row, 4));

        String[] methods = {"Cash", "Card", "Bank Transfer", "JazzCash / Easypaisa"};
        String method = (String) JOptionPane.showInputDialog(this,
                "Payment for " + clientName + " — " + serviceName +
                        "\nAmount: Rs. " + String.format("%.0f", amount) +
                        "\n\nChoose payment method:",
                "Complete Client & Pay", JOptionPane.QUESTION_MESSAGE, null, methods, methods[0]);
        if (method == null) return;

        try (Connection c = DB.getConnection()) {
            c.setAutoCommit(false);
            try {
                int clientId;
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT client_id FROM bookings WHERE id=? AND status IN ('BOOKED','READY')")) {
                    ps.setInt(1, bookingId);
                    try (ResultSet r = ps.executeQuery()) {
                        if (!r.next()) throw new SQLException("This booking is no longer active.");
                        clientId = r.getInt(1);
                    }
                }

                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO payments(booking_id,client_id,amount,method) VALUES(?,?,?,?)")) {
                    ps.setInt(1, bookingId);
                    ps.setInt(2, clientId);
                    ps.setDouble(3, amount);
                    ps.setString(4, method);
                    ps.executeUpdate();
                }

                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE bookings SET status='COMPLETED' WHERE id=?")) {
                    ps.setInt(1, bookingId);
                    ps.executeUpdate();
                }

                c.commit();
                JOptionPane.showMessageDialog(this,
                        "✨ Payment completed successfully!\n\nClient: " + clientName +
                                "\nService: " + serviceName +
                                "\nPayment: Rs. " + String.format("%.0f", amount) +
                                "\nMethod: " + method +
                                "\n\nThe client has been removed from the active Clients list and the payment is now saved in Payments.",
                        "Payment Saved", JOptionPane.INFORMATION_MESSAGE);
                refreshAll();
            } catch (Exception ex) {
                c.rollback();
                throw ex;
            }
        } catch (SQLException e) {
            dbError(e);
        }
    }

    private void cancelSelectedClientBooking() {
        int row = clientTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this,
                    "Please select a client from the Clients tab first.",
                    "Select Client", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int bookingId = ((Number) clientTable.getValueAt(row, 0)).intValue();
        String clientName = String.valueOf(clientTable.getValueAt(row, 1));
        String serviceName = String.valueOf(clientTable.getValueAt(row, 4));

        int confirm = JOptionPane.showConfirmDialog(this,
                "Cancel the booking for \"" + clientName + "\"?\nService: " + serviceName +
                        "\n\nNo payment will be added.",
                "Cancel Booking", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) return;

        try (Connection c = DB.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE bookings SET status='CANCELLED' WHERE id=? AND status IN ('BOOKED','READY')")) {
            ps.setInt(1, bookingId);
            int updated = ps.executeUpdate();
            if (updated == 0) {
                throw new SQLException("This booking is no longer active.");
            }

            JOptionPane.showMessageDialog(this,
                    "Booking for \"" + clientName + "\" was cancelled.\nThe client has been removed from the active Clients list.",
                    "Booking Cancelled", JOptionPane.INFORMATION_MESSAGE);
            refreshAll();
        } catch (SQLException e) {
            dbError(e);
        }
    }

    private void loadServicesTable() {
        if (serviceModel == null) return;
        serviceModel.setRowCount(0);
        String q = """
            SELECT s.id,s.name,s.category,s.duration_minutes,s.price,COUNT(b.id) AS booked_clients
            FROM services s
            LEFT JOIN bookings b ON s.id=b.service_id
            GROUP BY s.id,s.name,s.category,s.duration_minutes,s.price
            ORDER BY s.category,s.name
            """;
        try (Connection c = DB.getConnection(); PreparedStatement ps = c.prepareStatement(q); ResultSet r = ps.executeQuery()) {
            while (r.next()) serviceModel.addRow(new Object[]{r.getInt(1), r.getString(2), r.getString(3), r.getInt(4), r.getDouble(5), r.getInt(6)});
        } catch (SQLException e) { dbError(e); }
    }

    private void loadPayments() {
        if (paymentModel == null) return;
        paymentModel.setRowCount(0);
        String q = """
            SELECT p.id, c.name, c.phone, s.name, p.amount, p.method, p.paid_at
            FROM payments p
            JOIN clients c ON p.client_id = c.id
            JOIN bookings b ON p.booking_id = b.id
            JOIN services s ON b.service_id = s.id
            ORDER BY p.id DESC
            """;
        try (Connection c = DB.getConnection(); PreparedStatement ps = c.prepareStatement(q); ResultSet r = ps.executeQuery()) {
            while (r.next()) paymentModel.addRow(new Object[]{
                    r.getInt(1), r.getString(2), r.getString(3), r.getString(4), r.getDouble(5), r.getString(6), r.getTimestamp(7)
            });
        } catch (SQLException e) { dbError(e); }

        if (paymentTodayLabel != null) {
            try (Connection c = DB.getConnection(); PreparedStatement ps = c.prepareStatement("SELECT COALESCE(SUM(amount),0) FROM payments WHERE DATE(paid_at)=CURDATE()")) {
                try (ResultSet r = ps.executeQuery()) { r.next(); paymentTodayLabel.setText("Rs. " + String.format("%.0f", r.getDouble(1))); }
            } catch (SQLException e) { dbError(e); }
        }
    }

    private void loadDashboard() {
        try (Connection c = DB.getConnection()) {
            revenueLabel.setText("Rs. " + String.format("%.0f", scalar(c, "SELECT COALESCE(SUM(amount),0) FROM payments")));
            todayRevenueLabel.setText("Rs. " + String.format("%.0f", scalar(c, "SELECT COALESCE(SUM(amount),0) FROM payments WHERE DATE(paid_at)=CURDATE()")));
            bookingsLabel.setText(String.valueOf((long) scalar(c, "SELECT COUNT(*) FROM bookings WHERE status IN ('BOOKED','READY')")));
            clientsLabel.setText(String.valueOf((long) scalar(c, "SELECT COUNT(*) FROM clients")));
            pendingLabel.setText(String.valueOf((long) scalar(c, "SELECT COUNT(*) FROM bookings WHERE status='BOOKED'")));

            List<Double> vals = new ArrayList<>();
            List<String> labs = new ArrayList<>();
            for (int i = 6; i >= 0; i--) {
                LocalDate d = LocalDate.now().minusDays(i);
                labs.add(d.getDayOfWeek().toString().substring(0, 1));
                try (PreparedStatement ps = c.prepareStatement("SELECT COALESCE(SUM(amount),0) FROM payments WHERE DATE(paid_at)=?")) {
                    ps.setDate(1, Date.valueOf(d));
                    try (ResultSet r = ps.executeQuery()) { r.next(); vals.add(r.getDouble(1)); }
                }
            }
            chart.setData(vals, labs);
        } catch (SQLException e) { dbError(e); }
    }

    private double scalar(Connection c, String q) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(q); ResultSet r = ps.executeQuery()) { r.next(); return r.getDouble(1); }
    }

    private void loadServices(JComboBox<ServiceItem> box) {
        box.removeAllItems();
        try (Connection c = DB.getConnection(); PreparedStatement ps = c.prepareStatement("SELECT id,name,duration_minutes,price FROM services ORDER BY name"); ResultSet r = ps.executeQuery()) {
            while (r.next()) box.addItem(new ServiceItem(r.getInt(1), r.getString(2), r.getInt(3), r.getDouble(4)));
        } catch (SQLException e) { dbError(e); }
    }

    private void addService() {
        JTextField name = field();
        JTextField category = field();
        JTextField duration = field(); duration.setText("60");
        JTextField price = field(); price.setText("1000");
        JPanel p = new JPanel(new GridLayout(4, 2, 8, 8));
        p.setBackground(Color.WHITE);
        p.add(label("Service:")); p.add(name);
        p.add(label("Category:")); p.add(category);
        p.add(label("Duration (minutes):")); p.add(duration);
        p.add(label("Price (Rs.):")); p.add(price);
        if (JOptionPane.showConfirmDialog(this, p, "Add New Service", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
        try (Connection c = DB.getConnection(); PreparedStatement ps = c.prepareStatement("INSERT INTO services(name,category,duration_minutes,price) VALUES(?,?,?,?)")) {
            ps.setString(1, name.getText().trim());
            ps.setString(2, category.getText().trim());
            ps.setInt(3, Integer.parseInt(duration.getText().trim()));
            ps.setDouble(4, Double.parseDouble(price.getText().trim()));
            ps.executeUpdate();
            loadServicesTable();
            JOptionPane.showMessageDialog(this, "Service added ✨ Its client count starts at 0.");
        } catch (Exception e) { dbError(new SQLException(e.getMessage())); }
    }

    private void deleteSelectedService() {
        int row = serviceTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Please select a service from the table first.");
            return;
        }
        int id = (int) serviceModel.getValueAt(row, 0);
        String name = String.valueOf(serviceModel.getValueAt(row, 1));
        int confirm = JOptionPane.showConfirmDialog(this,
                "Delete the service \"" + name + "\"?\nThis cannot be undone.",
                "Delete Service", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) return;

        try (Connection c = DB.getConnection(); PreparedStatement ps = c.prepareStatement("DELETE FROM services WHERE id=?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
            loadServicesTable();
            JOptionPane.showMessageDialog(this, "Service \"" + name + "\" deleted.",
                    "Service Deleted", JOptionPane.INFORMATION_MESSAGE);
        } catch (SQLException e) {
            if ("23000".equals(e.getSQLState())) {
                JOptionPane.showMessageDialog(this,
                        "Can't delete \"" + name + "\" — it already has bookings linked to it.\n" +
                                "Cancel or complete those bookings first, or keep the service for records.",
                        "Service In Use", JOptionPane.WARNING_MESSAGE);
            } else {
                dbError(e);
            }
        }
    }

    private JLabel label(String text) {
        JLabel l = new JLabel(text);
        l.setFont(Theme.LABEL); l.setForeground(Theme.TEXT);
        return l;
    }

    private DefaultTableModel model(String... cols) {
        return new DefaultTableModel(cols, 0) { @Override public boolean isCellEditable(int r, int c) { return false; } };
    }

    private JTable table(DefaultTableModel m) {
        JTable t = new JTable(m);
        t.setRowHeight(34);
        t.setFont(Theme.BODY);
        t.setForeground(Theme.TEXT);
        t.setBackground(Color.WHITE);
        t.setSelectionBackground(new Color(225, 207, 236));
        t.setSelectionForeground(Theme.TEXT);
        t.setGridColor(new Color(220, 205, 225));
        t.setShowGrid(true);
        t.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 13));
        t.getTableHeader().setUI(new javax.swing.plaf.basic.BasicTableHeaderUI());
        t.getTableHeader().setBackground(Theme.TABLE_HEADER);
        t.getTableHeader().setForeground(Color.WHITE);
        t.getTableHeader().setOpaque(true);
        t.getTableHeader().setPreferredSize(new Dimension(0, 38));
        return t;
    }

    private GridBagConstraints gbc() {
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(5, 8, 5, 8);
        g.fill = GridBagConstraints.HORIZONTAL;
        g.weightx = 1;
        return g;
    }

    private GridBagConstraints gbcCompact() {
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(3, 8, 3, 8);
        g.fill = GridBagConstraints.HORIZONTAL;
        g.weightx = 1;
        return g;
    }

    private void addField(JPanel p, GridBagConstraints g, int row, String label, JComponent comp) {
        g.gridx = 0; g.gridy = row; g.weightx = 0.3;
        p.add(label(label), g);
        g.gridx = 1; g.weightx = 1;
        p.add(comp, g);
    }

    private void dbError(SQLException e) {
        JOptionPane.showMessageDialog(this,
                "Database error:\n" + e.getMessage() + "\n\nCheck MySQL is running and DB.java credentials are correct.",
                "Database Error", JOptionPane.ERROR_MESSAGE);
    }

    private static class ServiceItem {
        int id, duration; double price; String name;
        ServiceItem(int id, String name, int duration, double price) { this.id = id; this.name = name; this.duration = duration; this.price = price; }
        @Override public String toString() { return name + "  •  Rs. " + String.format("%.0f", price) + "  •  " + duration + " min"; }
    }
}
