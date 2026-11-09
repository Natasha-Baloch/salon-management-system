package com.salon;

import com.salon.Role.Perm;
import com.salon.Session.User;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

/** All database operations. Every method checks the logged-in user's permission first and logs important actions. */
public final class Salon {
    private Salon() {}

    public static final String ACTIVE = "(b.status IN ('REQUESTED','CONFIRMED','BOOKED','READY','IN_PROGRESS') OR (b.status='COMPLETED' AND p.id IS NULL))";

    public record Dash(LinkedHashMap<String, String> cards, List<Double> chartValues, List<String> chartLabels,
                       String tableTitle, String[] cols, List<Object[]> rows) {}

    // ---------- low-level helpers ----------
    private static void bind(PreparedStatement ps, Object... p) throws SQLException {
        for (int i = 0; i < p.length; i++) {
            Object v = p[i];
            if (v instanceof LocalDate d) v = java.sql.Date.valueOf(d);
            else if (v instanceof LocalTime t) v = java.sql.Time.valueOf(t);
            ps.setObject(i + 1, v);
        }
    }

    static List<Object[]> query(String sql, Object... p) throws SQLException {
        try (Connection c = DB.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, p);
            try (ResultSet r = ps.executeQuery()) {
                int n = r.getMetaData().getColumnCount();
                List<Object[]> out = new ArrayList<>();
                while (r.next()) {
                    Object[] row = new Object[n];
                    for (int i = 0; i < n; i++) row[i] = r.getObject(i + 1);
                    out.add(row);
                }
                return out;
            }
        }
    }

    private static double num(String sql, Object... p) throws SQLException {
        List<Object[]> r = query(sql, p);
        Object v = r.isEmpty() ? null : r.get(0)[0];
        return v == null ? 0 : ((Number) v).doubleValue();
    }

    private static void update(Connection c, String sql, Object... p) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) { bind(ps, p); ps.executeUpdate(); }
    }

    private static void log(Connection c, Integer userId, String action, String details) {
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO activity_logs(user_id,action,details) VALUES(?,?,?)")) {
            ps.setObject(1, userId);
            ps.setString(2, action);
            ps.setString(3, details == null ? null : (details.length() > 480 ? details.substring(0, 480) : details));
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Could not write activity log: " + e.getMessage());
        }
    }

    private static void log(Connection c, String action, String details) {
        User u = Session.user();
        if (u != null && u.role() == Role.CLIENT) log(c, null, action, "[client " + u.fullName() + "] " + (details == null ? "" : details));
        else log(c, u == null ? null : u.id(), action, details);
    }

    public static final String[] PAY_METHODS = {"Card", "JazzCash / Easypaisa", "Bank Transfer", "Cash"};

    private static String money(double v) { return "Rs. " + String.format("%,.0f", v); }

    // ---------- authentication ----------
    public static User login(String username, char[] password) throws SQLException {
        username = username == null ? "" : username.trim().toLowerCase();
        String pw = new String(password);
        try (Connection c = DB.getConnection()) {
            Object[] u;
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT id,full_name,password_hash,role,is_active,must_change_password FROM users WHERE username=?")) {
                ps.setString(1, username);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) { log(c, null, "LOGIN_FAILED", "Unknown username: " + username); throw new IllegalStateException("Invalid username or password."); }
                    u = new Object[]{rs.getInt(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getBoolean(5), rs.getBoolean(6)};
                }
            }
            int id = (Integer) u[0];
            if (!Passwords.verify(pw, (String) u[2])) { log(c, id, "LOGIN_FAILED", "Wrong password for " + username); throw new IllegalStateException("Invalid username or password."); }
            if (!(Boolean) u[4]) { log(c, id, "LOGIN_BLOCKED", "Disabled account: " + username); throw new IllegalStateException("This account has been disabled. Please contact the administrator."); }
            User user = new User(id, (String) u[1], username, Role.valueOf((String) u[3]), (Boolean) u[5]);
            Session.start(user);
            log(c, "LOGIN", username + " signed in as " + user.role());
            return user;
        }
    }

    public static void logout() {
        User u = Session.user();
        if (u != null) {
            try (Connection c = DB.getConnection()) { log(c, "LOGOUT", u.username() + " signed out"); } catch (SQLException ignored) { }
        }
        Session.end();
    }

    public static void changePassword(String oldPw, String newPw) throws SQLException {
        User u = Session.user();
        if (u == null) throw new SecurityException("You are not logged in.");
        String err = Passwords.policyError(newPw);
        if (err != null) throw new IllegalArgumentException(err);
        if (newPw.equals(oldPw)) throw new IllegalArgumentException("The new password must be different from the current one.");
        try (Connection c = DB.getConnection()) {
            List<Object[]> r = query("SELECT password_hash FROM users WHERE id=?", u.id());
            if (r.isEmpty() || !Passwords.verify(oldPw, (String) r.get(0)[0])) throw new IllegalArgumentException("Current password is incorrect.");
            update(c, "UPDATE users SET password_hash=?, must_change_password=0 WHERE id=?", Passwords.hash(newPw), u.id());
            log(c, "PASSWORD_CHANGED", u.username() + " changed their password");
        }
        Session.start(new User(u.id(), u.fullName(), u.username(), u.role(), false));
    }

    // ---------- clients ----------
    public static List<Object[]> clients() throws SQLException {
        Session.require(Perm.VIEW_CLIENTS);
        boolean money = Session.user().role() == Role.ADMIN;
        return query("SELECT c.id,c.name,c.phone,COALESCE(c.email,''),COALESCE(c.notes,''),COUNT(DISTINCT b.id)," +
                (money ? "COALESCE(SUM(p.amount),0)" : "'—'") + ",DATE_FORMAT(c.created_at,'%Y-%m-%d') FROM clients c " +
                "LEFT JOIN bookings b ON b.client_id=c.id AND b.status<>'CANCELLED' LEFT JOIN payments p ON p.client_id=c.id AND p.status='CONFIRMED' " +
                "GROUP BY c.id,c.name,c.phone,c.email,c.notes,c.created_at ORDER BY c.name");
    }

    private static void checkClient(String name, String phone) {
        if (name == null || name.trim().isEmpty() || phone == null || phone.trim().isEmpty())
            throw new IllegalArgumentException("Client name and phone are required.");
        if (!phone.trim().matches("[0-9+\\-() ]{7,20}")) throw new IllegalArgumentException("Enter a valid phone number (digits, +, -, spaces; 7-20 characters).");
    }

    public static void registerClient(String name, String phone, String email, String notes) throws SQLException {
        Session.require(Perm.REGISTER_CLIENT);
        checkClient(name, phone);
        if (email != null && !email.isBlank() && !email.trim().matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) throw new IllegalArgumentException("Enter a valid email address.");
        try (Connection c = DB.getConnection()) {
            if (!query("SELECT id FROM clients WHERE phone=?", phone.trim()).isEmpty()) throw new IllegalArgumentException("A client with this phone number already exists.");
            update(c, "INSERT INTO clients(name,phone,email,notes) VALUES(?,?,?,?)", name.trim(), phone.trim(), email.trim(), notes.trim());
            log(c, "CLIENT_REGISTERED", name.trim() + " (" + phone.trim() + ")");
        }
    }

    // ---------- services ----------
    public static List<Object[]> services() throws SQLException {
        Session.require(Perm.VIEW_SERVICES);
        return query("SELECT s.id,s.name,s.category,s.duration_minutes,s.price,COUNT(b.id),IF(MAX(s.image IS NULL)=1,'—','Yes') FROM services s " +
                "LEFT JOIN bookings b ON s.id=b.service_id GROUP BY s.id,s.name,s.category,s.duration_minutes,s.price ORDER BY s.category,s.name");
    }

    public record ServiceItem(int id, String name, int duration, double price) {
        @Override public String toString() { return name + "  •  Rs. " + String.format("%.0f", price) + "  •  " + duration + " min"; }
    }

    public static List<ServiceItem> serviceItems() throws SQLException {
        Session.require(Perm.VIEW_SERVICES);
        List<ServiceItem> l = new ArrayList<>();
        for (Object[] r : query("SELECT id,name,duration_minutes,price FROM services ORDER BY name"))
            l.add(new ServiceItem((Integer) r[0], (String) r[1], (Integer) r[2], ((Number) r[3]).doubleValue()));
        return l;
    }

    public static void saveService(Integer id, String name, String category, String duration, String price) throws SQLException {
        Session.require(Perm.MANAGE_SERVICES);
        if (name.isBlank()) throw new IllegalArgumentException("Service name is required.");
        int d; double p;
        try { d = Integer.parseInt(duration.trim()); p = Double.parseDouble(price.trim()); }
        catch (NumberFormatException e) { throw new IllegalArgumentException("Duration and price must be numbers."); }
        if (d <= 0 || p < 0) throw new IllegalArgumentException("Duration must be above 0 and price cannot be negative.");
        try (Connection c = DB.getConnection()) {
            if (id == null) update(c, "INSERT INTO services(name,category,duration_minutes,price) VALUES(?,?,?,?)", name.trim(), category.trim(), d, p);
            else update(c, "UPDATE services SET name=?,category=?,duration_minutes=?,price=? WHERE id=?", name.trim(), category.trim(), d, p, id);
            log(c, id == null ? "SERVICE_ADDED" : "SERVICE_UPDATED", name.trim() + " @ " + money(p));
        } catch (SQLIntegrityConstraintViolationException e) {
            throw new IllegalArgumentException("A service with this name already exists.");
        }
    }

    public static void deleteService(int id, String name) throws SQLException {
        Session.require(Perm.MANAGE_SERVICES);
        try (Connection c = DB.getConnection()) {
            update(c, "DELETE FROM services WHERE id=?", id);
            log(c, "SERVICE_DELETED", name);
        } catch (SQLException e) {
            if ("23000".equals(e.getSQLState())) throw new IllegalArgumentException("Can't delete \"" + name + "\" — it already has bookings linked to it. Keep it for records.");
            throw e;
        }
    }

    // ---------- staff / bookings ----------
    public record Staff(int id, String name) {
        @Override public String toString() { return name; }
    }

    public static List<Staff> staffList() throws SQLException {
        Session.require(Perm.VIEW_BOOKINGS);
        List<Staff> l = new ArrayList<>();
        for (Object[] r : query("SELECT id,full_name FROM users WHERE role='STAFF' AND is_active=1 ORDER BY full_name")) l.add(new Staff((Integer) r[0], (String) r[1]));
        return l;
    }

    public static List<Object[]> bookings(boolean activeOnly) throws SQLException {
        Session.require(Perm.VIEW_BOOKINGS);
        return query("SELECT b.id,c.name,c.phone,s.name,b.booking_date,TIME_FORMAT(b.booking_time,'%H:%i'),COALESCE(u.full_name,'Unassigned')," +
                "b.status,b.total_amount,CASE WHEN b.status='CANCELLED' THEN '—' WHEN p.id IS NULL THEN 'UNPAID' WHEN p.status='PENDING' THEN 'PENDING' ELSE 'PAID' END " +
                "FROM bookings b JOIN clients c ON b.client_id=c.id JOIN services s ON b.service_id=s.id " +
                "LEFT JOIN users u ON u.id=b.assigned_staff_id LEFT JOIN payments p ON p.booking_id=b.id " +
                (activeOnly ? "WHERE " + ACTIVE + " " : "") + "ORDER BY b.booking_date" + (activeOnly ? "" : " DESC") + ",b.booking_time,b.id");
    }

    private static void checkSlot(Connection c, Integer staffId, LocalDate d, LocalTime t, Integer ignoreBooking) throws SQLException {
        if (staffId == null) return;
        if (num("SELECT COUNT(*) FROM users WHERE id=? AND role='STAFF' AND is_active=1", staffId) == 0)
            throw new IllegalArgumentException("The selected staff member is not an active staff account.");
        if (num("SELECT COUNT(*) FROM bookings WHERE assigned_staff_id=? AND booking_date=? AND booking_time=? AND status IN ('CONFIRMED','BOOKED','READY','IN_PROGRESS') AND id<>?",
                staffId, d, t, ignoreBooking == null ? -1 : ignoreBooking) > 0)
            throw new IllegalArgumentException("That staff member already has an appointment at this date and time.");
    }

    public static void createBooking(String name, String phone, String email, ServiceItem s, LocalDate d, LocalTime t, Integer staffId) throws SQLException {
        User u = Session.require(Perm.CREATE_BOOKING);
        checkClient(name, phone);
        if (s == null) throw new IllegalArgumentException("Please choose a service.");
        if (d.isBefore(LocalDate.now())) throw new IllegalArgumentException("The booking date cannot be in the past.");
        try (Connection c = DB.getConnection()) {
            c.setAutoCommit(false);
            try {
                checkSlot(c, staffId, d, t, null);
                int clientId;
                List<Object[]> ex = query("SELECT id FROM clients WHERE phone=?", phone.trim());
                if (!ex.isEmpty()) {
                    clientId = (Integer) ex.get(0)[0];
                    update(c, "UPDATE clients SET name=?, email=? WHERE id=?", name.trim(), email.trim(), clientId);
                } else {
                    try (PreparedStatement ins = c.prepareStatement("INSERT INTO clients(name,phone,email) VALUES(?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
                        ins.setString(1, name.trim()); ins.setString(2, phone.trim()); ins.setString(3, email.trim());
                        ins.executeUpdate();
                        try (ResultSet k = ins.getGeneratedKeys()) { k.next(); clientId = k.getInt(1); }
                    }
                }
                update(c, "INSERT INTO bookings(client_id,service_id,assigned_staff_id,created_by,booking_date,booking_time,status,total_amount) VALUES(?,?,?,?,?,?,'BOOKED',?)",
                        clientId, s.id(), staffId, u.id(), d, t, s.price());
                log(c, "BOOKING_CREATED", name.trim() + " — " + s.name() + " on " + d + " " + t);
                c.commit();
            } catch (Exception e) { c.rollback(); throw e; }
        }
    }

    /** Receptionist/admin edit: reschedule, reassign staff, set BOOKED/READY. Paid or cancelled bookings are locked. */
    public static void updateBooking(int id, LocalDate d, LocalTime t, Integer staffId, String status) throws SQLException {
        Session.require(Perm.EDIT_BOOKING);
        if (!List.of("BOOKED", "READY", "CONFIRMED").contains(status)) throw new IllegalArgumentException("Status must be BOOKED, READY or CONFIRMED here.");
        try (Connection c = DB.getConnection()) {
            c.setAutoCommit(false);
            try {
                List<Object[]> r = query("SELECT status,client_id FROM bookings WHERE id=?", id);
                if (r.isEmpty()) throw new IllegalArgumentException("Booking not found.");
                if (!List.of("REQUESTED", "CONFIRMED", "BOOKED", "READY", "IN_PROGRESS").contains((String) r.get(0)[0])) throw new IllegalArgumentException("Completed or cancelled bookings cannot be edited.");
                if ("CONFIRMED".equals(status) && staffId == null) throw new IllegalArgumentException("Choose a staff member before confirming a booking.");
                checkSlot(c, staffId, d, t, id);
                String keep = "IN_PROGRESS".equals(r.get(0)[0]) ? "IN_PROGRESS" : status;
                update(c, "UPDATE bookings SET booking_date=?,booking_time=?,assigned_staff_id=?,status=? WHERE id=?", d, t, staffId, keep, id);
                log(c, "BOOKING_UPDATED", "Booking #" + id + " -> " + d + " " + t + ", staff " + staffId + ", " + keep);
                if ("CONFIRMED".equals(keep))
                    addNote(c, null, (Integer) r.get(0)[1], "✅ Your booking #" + id + " is confirmed for " + d + " at " + t + ". Please open My Bookings & Payments to pay.");
                c.commit();
            } catch (Exception e) { c.rollback(); throw e; }
        }
    }

    public static void cancelBooking(int id) throws SQLException {
        Session.require(Perm.EDIT_BOOKING);
        List<Object[]> info = query("SELECT client_id FROM bookings WHERE id=?", id);
        try (Connection c = DB.getConnection(); PreparedStatement ps = c.prepareStatement(
                "UPDATE bookings SET status='CANCELLED' WHERE id=? AND status IN ('REQUESTED','CONFIRMED','BOOKED','READY','IN_PROGRESS')")) {
            ps.setInt(1, id);
            if (ps.executeUpdate() == 0) throw new IllegalArgumentException("This booking is no longer active.");
            log(c, "BOOKING_CANCELLED", "Booking #" + id);
            if (!info.isEmpty()) addNote(c, null, (Integer) info.get(0)[0], "❌ Your booking #" + id + " was cancelled by the salon. Please request another time.");
        }
    }

    // ---------- payments ----------
    public static List<Object[]> payableBookings() throws SQLException {
        Session.require(Perm.PROCESS_PAYMENT);
        return query("SELECT b.id,c.name,s.name,b.total_amount FROM bookings b JOIN clients c ON c.id=b.client_id JOIN services s ON s.id=b.service_id " +
                "LEFT JOIN payments p ON p.booking_id=b.id WHERE p.id IS NULL AND b.status IN ('CONFIRMED','BOOKED','READY','IN_PROGRESS','COMPLETED') ORDER BY b.booking_date,b.booking_time");
    }

    /** Transactional "Complete & Pay": locks the booking row, refuses duplicate payments, amount comes from the database. */
    public static double completeAndPay(int bookingId, String method) throws SQLException {
        User u = Session.require(Perm.PROCESS_PAYMENT);
        try (Connection c = DB.getConnection()) {
            c.setAutoCommit(false);
            try {
                int clientId; double amount; String status;
                try (PreparedStatement ps = c.prepareStatement("SELECT client_id,total_amount,status FROM bookings WHERE id=? FOR UPDATE")) {
                    ps.setInt(1, bookingId);
                    try (ResultSet r = ps.executeQuery()) {
                        if (!r.next()) throw new IllegalArgumentException("Booking not found.");
                        clientId = r.getInt(1); amount = r.getDouble(2); status = r.getString(3);
                    }
                }
                if ("CANCELLED".equals(status)) throw new IllegalArgumentException("A cancelled booking cannot be paid.");
                if ("REQUESTED".equals(status)) throw new IllegalArgumentException("This is still a client request. Confirm it (Edit → CONFIRMED with a staff member) or let staff confirm it first.");
                try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM payments WHERE booking_id=?")) {
                    ps.setInt(1, bookingId);
                    try (ResultSet r = ps.executeQuery()) { r.next(); if (r.getInt(1) > 0) throw new IllegalArgumentException("This booking has already been paid."); }
                }
                update(c, "INSERT INTO payments(booking_id,client_id,amount,method,received_by) VALUES(?,?,?,?,?)", bookingId, clientId, amount, method, u.id());
                update(c, "UPDATE bookings SET status='COMPLETED' WHERE id=?", bookingId);
                log(c, "PAYMENT_RECEIVED", "Booking #" + bookingId + " " + money(amount) + " via " + method);
                addNote(c, null, clientId, "💳 We received your payment of " + money(amount) + " for booking #" + bookingId + ". Your service is complete — we would love your feedback (Reviews tab).");
                c.commit();
                return amount;
            } catch (SQLIntegrityConstraintViolationException e) {
                c.rollback();
                throw new IllegalArgumentException("This booking has already been paid.");
            } catch (Exception e) { c.rollback(); throw e; }
        }
    }

    public static List<Object[]> payments() throws SQLException {
        Session.require(Perm.VIEW_PAYMENTS);
        return query("SELECT p.id,c.name,c.phone,s.name,p.amount,p.method,DATE_FORMAT(p.paid_at,'%Y-%m-%d %H:%i'),p.status,COALESCE(u.full_name,'Waiting for staff') " +
                "FROM payments p JOIN clients c ON p.client_id=c.id JOIN bookings b ON p.booking_id=b.id JOIN services s ON b.service_id=s.id " +
                "LEFT JOIN users u ON u.id=p.received_by ORDER BY p.id DESC");
    }

    public static String todayPaymentsText() throws SQLException {
        User u = Session.require(Perm.VIEW_PAYMENTS);
        return money(u.role() == Role.ADMIN ? num("SELECT COALESCE(SUM(amount),0) FROM payments WHERE status='CONFIRMED' AND DATE(paid_at)=CURDATE()")
                : num("SELECT COALESCE(SUM(amount),0) FROM payments WHERE status='CONFIRMED' AND DATE(paid_at)=CURDATE() AND received_by=?", u.id()));
    }

    // ---------- staff workspace (own work only, no financial data) ----------
    public static List<Object[]> myAppointments(boolean history) throws SQLException {
        User u = Session.require(Perm.VIEW_OWN_WORK);
        return query("SELECT b.id,b.booking_date,TIME_FORMAT(b.booking_time,'%H:%i'),c.name,c.phone,COALESCE(NULLIF(b.notes,''),c.notes,''),s.name,s.duration_minutes,b.status," +
                "CASE WHEN b.status='CANCELLED' THEN '—' WHEN p.id IS NULL THEN 'UNPAID' WHEN p.status='PENDING' THEN 'PENDING' ELSE 'PAID' END " +
                "FROM bookings b JOIN clients c ON c.id=b.client_id JOIN services s ON s.id=b.service_id LEFT JOIN payments p ON p.booking_id=b.id WHERE b.assigned_staff_id=? AND " +
                (history ? "b.status IN ('COMPLETED','CANCELLED') ORDER BY b.booking_date DESC,b.booking_time DESC"
                         : "b.status IN ('CONFIRMED','BOOKED','READY','IN_PROGRESS') ORDER BY b.booking_date,b.booking_time"), u.id());
    }

    public static void setProgress(int bookingId, String newStatus) throws SQLException {
        User u = Session.require(Perm.UPDATE_OWN_PROGRESS);
        try (Connection c = DB.getConnection()) {
            c.setAutoCommit(false);
            try {
                String status; int staff, clientId;
                try (PreparedStatement ps = c.prepareStatement("SELECT status,COALESCE(assigned_staff_id,0),client_id FROM bookings WHERE id=? FOR UPDATE")) {
                    ps.setInt(1, bookingId);
                    try (ResultSet r = ps.executeQuery()) {
                        if (!r.next()) throw new IllegalArgumentException("Appointment not found.");
                        status = r.getString(1); staff = r.getInt(2); clientId = r.getInt(3);
                    }
                }
                if (staff != u.id()) throw new SecurityException("This appointment is not assigned to you.");
                boolean ok = ("IN_PROGRESS".equals(newStatus) && List.of("CONFIRMED", "BOOKED", "READY").contains(status))
                        || ("COMPLETED".equals(newStatus) && "IN_PROGRESS".equals(status));
                if (!ok) throw new IllegalArgumentException("Cannot change status from " + status + " to " + newStatus + ".");
                update(c, "UPDATE bookings SET status=? WHERE id=?", newStatus, bookingId);
                noteAdmins(c, ("IN_PROGRESS".equals(newStatus) ? "💆 " + u.fullName() + " started" : "✨ " + u.fullName() + " completed") + " the service for " + clientName(clientId) + " (booking #" + bookingId + ").");
                log(c, "SERVICE_" + newStatus, "Booking #" + bookingId + " by " + u.username());
                if ("IN_PROGRESS".equals(newStatus)) addNote(c, null, clientId, "💆 Your service (booking #" + bookingId + ") has started with " + u.fullName() + ".");
                else addNote(c, null, clientId, "✨ Your service (booking #" + bookingId + ") is completed. We would love your feedback — open the Reviews tab and tap WRITE FEEDBACK.");
                c.commit();
            } catch (Exception e) { c.rollback(); throw e; }
        }
    }

    // ---------- dashboards ----------
    private static LinkedHashMap<String, String> cards(String... kv) {
        LinkedHashMap<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put(kv[i], kv[i + 1]);
        return m;
    }

    private static String n(double v) { return String.valueOf((long) v); }

    public static Dash dashboard() throws SQLException {
        User u = Session.user();
        if (u == null) throw new SecurityException("You are not logged in.");
        switch (u.role()) {
            case ADMIN: {
                List<Double> vals = new ArrayList<>(); List<String> labs = new ArrayList<>();
                for (int i = 6; i >= 0; i--) {
                    LocalDate d = LocalDate.now().minusDays(i);
                    labs.add(d.getDayOfWeek().toString().substring(0, 3));
                    vals.add(num("SELECT COALESCE(SUM(amount),0) FROM payments WHERE status='CONFIRMED' AND DATE(paid_at)=?", d));
                }
                double avg = num("SELECT COALESCE(AVG(rating),0) FROM feedback");
                return new Dash(cards(
                        "TOTAL CLIENTS", n(num("SELECT COUNT(*) FROM clients")),
                        "TOTAL BOOKINGS", n(num("SELECT COUNT(*) FROM bookings")),
                        "TODAY'S APPOINTMENTS", n(num("SELECT COUNT(*) FROM bookings WHERE booking_date=CURDATE() AND status NOT IN ('CANCELLED','REQUESTED')")),
                        "PENDING REQUESTS", n(num("SELECT COUNT(*) FROM bookings WHERE status='REQUESTED'")),
                        "PAYMENTS TO CONFIRM", n(num("SELECT COUNT(*) FROM payments WHERE status='PENDING'")),
                        "TOTAL REVENUE", money(num("SELECT COALESCE(SUM(amount),0) FROM payments WHERE status='CONFIRMED'"))),
                        vals, labs, "Recent Payments", new String[]{"ID", "Client", "Service", "Amount (Rs.)", "Method", "Paid At", "Status"},
                        query("SELECT p.id,c.name,s.name,p.amount,p.method,DATE_FORMAT(p.paid_at,'%Y-%m-%d %H:%i'),p.status FROM payments p " +
                                "JOIN clients c ON c.id=p.client_id JOIN bookings b ON b.id=p.booking_id JOIN services s ON s.id=b.service_id ORDER BY p.id DESC LIMIT 8"));
            }
            case CLIENT:
                return new Dash(cards(
                        "UPCOMING VISITS", n(num("SELECT COUNT(*) FROM bookings WHERE client_id=? AND status IN ('CONFIRMED','BOOKED','READY','IN_PROGRESS') AND booking_date>=CURDATE()", u.id())),
                        "AWAITING STAFF", n(num("SELECT COUNT(*) FROM bookings WHERE client_id=? AND status='REQUESTED'", u.id())),
                        "READY TO PAY", n(num("SELECT COUNT(*) FROM bookings b LEFT JOIN payments p ON p.booking_id=b.id WHERE b.client_id=? AND b.status IN ('CONFIRMED','IN_PROGRESS','COMPLETED') AND p.id IS NULL", u.id())),
                        "SERVICES COMPLETED", n(num("SELECT COUNT(*) FROM bookings WHERE client_id=? AND status='COMPLETED'", u.id())),
                        "TOTAL PAID", money(num("SELECT COALESCE(SUM(amount),0) FROM payments WHERE status='CONFIRMED' AND client_id=?", u.id()))),
                        null, null, "My Appointments", new String[]{"ID", "Date", "Time", "Service", "Staff", "Status", "Payment"},
                        query("SELECT b.id,b.booking_date,TIME_FORMAT(b.booking_time,'%H:%i'),s.name,COALESCE(u.full_name,'Any available'),b.status," +
                                "CASE WHEN b.status IN ('CANCELLED','REQUESTED') THEN '—' WHEN p.id IS NULL THEN 'UNPAID' WHEN p.status='PENDING' THEN 'PENDING' ELSE 'PAID' END FROM bookings b " +
                                "JOIN services s ON s.id=b.service_id LEFT JOIN users u ON u.id=b.assigned_staff_id LEFT JOIN payments p ON p.booking_id=b.id " +
                                "WHERE b.client_id=? AND b.status NOT IN ('CANCELLED') ORDER BY (b.status='COMPLETED'),b.booking_date,b.booking_time LIMIT 8", u.id()));
            default:
                return new Dash(cards(
                        "NEW REQUESTS", n(num("SELECT COUNT(*) FROM bookings WHERE status='REQUESTED' AND (assigned_staff_id=? OR assigned_staff_id IS NULL)", u.id())),
                        "MY ACTIVE APPOINTMENTS", n(num("SELECT COUNT(*) FROM bookings WHERE assigned_staff_id=? AND status IN ('CONFIRMED','BOOKED','READY','IN_PROGRESS')", u.id())),
                        "TODAY", n(num("SELECT COUNT(*) FROM bookings WHERE assigned_staff_id=? AND booking_date=CURDATE() AND status NOT IN ('CANCELLED','REQUESTED')", u.id())),
                        "IN PROGRESS", n(num("SELECT COUNT(*) FROM bookings WHERE assigned_staff_id=? AND status='IN_PROGRESS'", u.id())),
                        "COMPLETED", n(num("SELECT COUNT(*) FROM bookings WHERE assigned_staff_id=? AND status='COMPLETED'", u.id())),
                        "PAYMENTS TO CONFIRM", n(num("SELECT COUNT(*) FROM payments p JOIN bookings b ON b.id=p.booking_id WHERE p.status='PENDING' AND b.assigned_staff_id=?", u.id()))),
                        null, null, "Today's Work Schedule", new String[]{"ID", "Time", "Client", "Service", "Duration (min)", "Status", "Payment"},
                        query("SELECT b.id,TIME_FORMAT(b.booking_time,'%H:%i'),c.name,s.name,s.duration_minutes,b.status," +
                                "CASE WHEN p.id IS NULL THEN 'UNPAID' WHEN p.status='PENDING' THEN 'PENDING' ELSE 'PAID' END FROM bookings b " +
                                "JOIN clients c ON c.id=b.client_id JOIN services s ON s.id=b.service_id LEFT JOIN payments p ON p.booking_id=b.id WHERE b.assigned_staff_id=? AND b.booking_date=CURDATE() " +
                                "AND b.status NOT IN ('CANCELLED','REQUESTED') ORDER BY b.booking_time", u.id()));
        }
    }

    // ---------- reports (admin) ----------
    public static List<Object[]> report(String which) throws SQLException {
        Session.require(Perm.VIEW_REPORTS);
        switch (which) {
            case "service": return query("SELECT s.name,COUNT(DISTINCT b.id),COUNT(p.id),COALESCE(SUM(p.amount),0) FROM services s " +
                    "LEFT JOIN bookings b ON b.service_id=s.id LEFT JOIN payments p ON p.booking_id=b.id AND p.status='CONFIRMED' GROUP BY s.id,s.name ORDER BY 4 DESC,1");
            case "method": return query("SELECT method,COUNT(*),SUM(amount) FROM payments WHERE status='CONFIRMED' GROUP BY method ORDER BY 3 DESC");
            case "month": return query("SELECT DATE_FORMAT(paid_at,'%Y-%m'),COUNT(*),SUM(amount) FROM payments WHERE status='CONFIRMED' GROUP BY 1 ORDER BY 1 DESC LIMIT 12");
            case "staff": return query("SELECT u.full_name,COALESCE(SUM(b.status='COMPLETED'),0),COALESCE(SUM(b.status='IN_PROGRESS'),0),COALESCE(SUM(b.status IN ('CONFIRMED','BOOKED','READY')),0),COALESCE(SUM(p.amount),0) " +
                    "FROM users u LEFT JOIN bookings b ON b.assigned_staff_id=u.id LEFT JOIN payments p ON p.booking_id=b.id AND p.status='CONFIRMED' WHERE u.role='STAFF' GROUP BY u.id,u.full_name ORDER BY 2 DESC");
            default: return query("SELECT l.id,DATE_FORMAT(l.created_at,'%Y-%m-%d %H:%i:%s'),COALESCE(u.full_name,'(unknown)'),l.action,COALESCE(l.details,'') " +
                    "FROM activity_logs l LEFT JOIN users u ON u.id=l.user_id ORDER BY l.id DESC LIMIT 500");
        }
    }

    // ---------- user management (admin) ----------
    public static List<Object[]> users() throws SQLException {
        Session.require(Perm.MANAGE_USERS);
        return query("SELECT id,full_name,username,role,CASE WHEN is_active=1 THEN 'ACTIVE' ELSE 'DISABLED' END,DATE_FORMAT(created_at,'%Y-%m-%d'),IF(must_change_password=1,'Yes','No') FROM users ORDER BY role,full_name");
    }

    public static void createUser(String fullName, String username, Role role, String tempPassword) throws SQLException {
        Session.require(Perm.MANAGE_USERS);
        if (fullName.isBlank()) throw new IllegalArgumentException("Full name is required.");
        if (role != Role.ADMIN && role != Role.STAFF) throw new IllegalArgumentException("Choose Administrator or Salon Staff. Clients sign in with their email.");
        if (!username.matches("[A-Za-z0-9_.]{3,30}")) throw new IllegalArgumentException("Username: 3-30 letters, numbers, dot or underscore.");
        String err = Passwords.policyError(tempPassword);
        if (err != null) throw new IllegalArgumentException(err);
        try (Connection c = DB.getConnection()) {
            update(c, "INSERT INTO users(full_name,username,password_hash,role,is_active,must_change_password) VALUES(?,?,?,?,1,1)",
                    fullName.trim(), username.trim().toLowerCase(), Passwords.hash(tempPassword), role.name());
            log(c, "USER_CREATED", username + " (" + role + ")");
        } catch (SQLIntegrityConstraintViolationException e) {
            throw new IllegalArgumentException("That username is already taken.");
        }
    }

    public static void updateUser(int id, String fullName, Role role, boolean active) throws SQLException {
        User me = Session.require(Perm.MANAGE_USERS);
        if (fullName.isBlank()) throw new IllegalArgumentException("Full name is required.");
        if (id == me.id() && (!active || role != Role.ADMIN)) throw new IllegalArgumentException("You cannot disable or demote your own account.");
        try (Connection c = DB.getConnection()) {
            c.setAutoCommit(false);
            try {
                if ((role != Role.ADMIN || !active) && num("SELECT COUNT(*) FROM users WHERE role='ADMIN' AND is_active=1 AND id<>?", id) == 0)
                    throw new IllegalArgumentException("At least one active administrator is required.");
                update(c, "UPDATE users SET full_name=?,role=?,is_active=? WHERE id=?", fullName.trim(), role.name(), active ? 1 : 0, id);
                log(c, "USER_UPDATED", "User #" + id + " -> " + fullName + ", " + role + ", " + (active ? "active" : "disabled"));
                c.commit();
            } catch (Exception e) { c.rollback(); throw e; }
        }
    }

    public static void resetPassword(int id, String tempPassword) throws SQLException {
        Session.require(Perm.MANAGE_USERS);
        String err = Passwords.policyError(tempPassword);
        if (err != null) throw new IllegalArgumentException(err);
        try (Connection c = DB.getConnection()) {
            update(c, "UPDATE users SET password_hash=?,must_change_password=1 WHERE id=?", Passwords.hash(tempPassword), id);
            log(c, "PASSWORD_RESET", "User #" + id);
        }
    }

    public static Object[] userRow(int id) throws SQLException {
        Session.require(Perm.MANAGE_USERS);
        return query("SELECT full_name,role,is_active FROM users WHERE id=?", id).get(0);
    }

    // =====================================================================================
    //  NOTIFICATIONS
    // =====================================================================================
    /** Saves a notification for a staff/admin user (userId) or for a client (clientId). Never throws. */
    private static void addNote(Connection c, Integer userId, Integer clientId, String msg) {
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO notifications(user_id,client_id,message) VALUES(?,?,?)")) {
            ps.setObject(1, userId);
            ps.setObject(2, clientId);
            ps.setString(3, msg.length() > 390 ? msg.substring(0, 390) : msg);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Could not save notification: " + e.getMessage());
        }
    }

    /** One staff member, or every active staff member when staffId is null. */
    private static void noteStaff(Connection c, Integer staffId, String msg) throws SQLException {
        if (staffId != null) { addNote(c, staffId, null, msg); return; }
        for (Object[] r : query("SELECT id FROM users WHERE role='STAFF' AND is_active=1")) addNote(c, (Integer) r[0], null, msg);
    }

    private static void noteAdmins(Connection c, String msg) throws SQLException {
        for (Object[] r : query("SELECT id FROM users WHERE role='ADMIN' AND is_active=1")) addNote(c, (Integer) r[0], null, msg);
    }

    private static String clientName(int clientId) throws SQLException {
        List<Object[]> r = query("SELECT name FROM clients WHERE id=?", clientId);
        return r.isEmpty() ? "a client" : String.valueOf(r.get(0)[0]);
    }

    private static String staffName(Integer id) throws SQLException {
        if (id == null) return "any available staff";
        List<Object[]> r = query("SELECT full_name FROM users WHERE id=?", id);
        return r.isEmpty() ? "staff" : String.valueOf(r.get(0)[0]);
    }

    private static String ownerColumn(User u) { return u.role() == Role.CLIENT ? "client_id" : "user_id"; }

    public static List<Object[]> notifications() throws SQLException {
        User u = Session.user();
        if (u == null) throw new SecurityException("You are not logged in.");
        return query("SELECT id,DATE_FORMAT(created_at,'%Y-%m-%d %H:%i'),message,IF(is_read=1,'read','NEW') FROM notifications WHERE " + ownerColumn(u) +
                "=? ORDER BY id DESC LIMIT 200", u.id());
    }

    public static int unreadCount() throws SQLException {
        User u = Session.user();
        if (u == null) return 0;
        return (int) num("SELECT COUNT(*) FROM notifications WHERE " + ownerColumn(u) + "=? AND is_read=0", u.id());
    }

    public static void markAllRead() throws SQLException {
        User u = Session.user();
        if (u == null) throw new SecurityException("You are not logged in.");
        try (Connection c = DB.getConnection()) { update(c, "UPDATE notifications SET is_read=1 WHERE " + ownerColumn(u) + "=? AND is_read=0", u.id()); }
    }

    // =====================================================================================
    //  CLIENT: sign in with email only
    // =====================================================================================
    /** Thrown when an unknown email signs in: the screen then asks for name and phone to create the account. */
    public static class NewClientException extends Exception {
        public NewClientException() { super("New client"); }
    }

    public static User clientLogin(String email, String name, String phone) throws SQLException, NewClientException {
        String em = email == null ? "" : email.trim();
        if (!em.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) throw new IllegalArgumentException("Please enter a valid email address.");
        try (Connection c = DB.getConnection()) {
            int id; String full;
            List<Object[]> r = query("SELECT id,name FROM clients WHERE LOWER(email)=LOWER(?) ORDER BY id LIMIT 1", em);
            if (!r.isEmpty()) {
                id = (Integer) r.get(0)[0]; full = (String) r.get(0)[1];
            } else {
                if (name == null || phone == null) throw new NewClientException();
                checkClient(name, phone);
                List<Object[]> byPhone = query("SELECT id,name,COALESCE(email,'') FROM clients WHERE phone=?", phone.trim());
                if (!byPhone.isEmpty()) {
                    if (!((String) byPhone.get(0)[2]).isBlank()) throw new IllegalArgumentException("This phone number is already registered with a different email address.");
                    id = (Integer) byPhone.get(0)[0]; full = (String) byPhone.get(0)[1];
                    update(c, "UPDATE clients SET email=? WHERE id=?", em, id);
                } else {
                    try (PreparedStatement ins = c.prepareStatement("INSERT INTO clients(name,phone,email) VALUES(?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
                        ins.setString(1, name.trim()); ins.setString(2, phone.trim()); ins.setString(3, em);
                        ins.executeUpdate();
                        try (ResultSet k = ins.getGeneratedKeys()) { k.next(); id = k.getInt(1); }
                    }
                    full = name.trim();
                }
            }
            User u = new User(id, full, em, Role.CLIENT, false);
            Session.start(u);
            log(c, "CLIENT_LOGIN", em + (r.isEmpty() ? " (new account)" : ""));
            return u;
        }
    }

    // =====================================================================================
    //  CLIENT: services, booking requests, payment, feedback
    // =====================================================================================
    public record ServiceCard(int id, String name, String category, int duration, double price, byte[] image) {}

    public static List<ServiceCard> clientServices() throws SQLException {
        Session.require(Perm.CLIENT_BROWSE);
        List<ServiceCard> l = new ArrayList<>();
        for (Object[] r : query("SELECT id,name,COALESCE(category,''),duration_minutes,price,image FROM services ORDER BY category,name"))
            l.add(new ServiceCard((Integer) r[0], (String) r[1], (String) r[2], (Integer) r[3], ((Number) r[4]).doubleValue(), (byte[]) r[5]));
        return l;
    }

    public static List<Staff> staffChoices() throws SQLException {
        Session.require(Perm.CLIENT_BOOK);
        List<Staff> l = new ArrayList<>();
        for (Object[] r : query("SELECT id,full_name FROM users WHERE role='STAFF' AND is_active=1 ORDER BY full_name")) l.add(new Staff((Integer) r[0], (String) r[1]));
        return l;
    }

    /** Step 1 of the workflow: the client asks for a service; the request goes to the chosen staff member (or all staff). */
    public static void requestBooking(ServiceItem s, Integer staffId, LocalDate d, LocalTime t, String note) throws SQLException {
        User u = Session.require(Perm.CLIENT_BOOK);
        if (s == null) throw new IllegalArgumentException("Please choose a service.");
        if (d == null || t == null) throw new IllegalArgumentException("Please choose a date and time.");
        if (d.isBefore(LocalDate.now()) || (d.equals(LocalDate.now()) && t.isBefore(LocalTime.now())))
            throw new IllegalArgumentException("Please choose a date and time in the future.");
        String nt = note == null ? "" : note.trim();
        if (nt.length() > 250) throw new IllegalArgumentException("Your note is too long (maximum 250 characters).");
        try (Connection c = DB.getConnection()) {
            c.setAutoCommit(false);
            try {
                List<Object[]> svc = query("SELECT name,price FROM services WHERE id=?", s.id());
                if (svc.isEmpty()) throw new IllegalArgumentException("That service is no longer available.");
                double price = ((Number) svc.get(0)[1]).doubleValue();
                if (staffId != null && num("SELECT COUNT(*) FROM users WHERE id=? AND role='STAFF' AND is_active=1", staffId) == 0)
                    throw new IllegalArgumentException("The selected staff member is not available.");
                if (num("SELECT COUNT(*) FROM bookings WHERE client_id=? AND service_id=? AND booking_date=? AND status IN ('REQUESTED','CONFIRMED')", u.id(), s.id(), d) > 0)
                    throw new IllegalArgumentException("You already have a request for this service on that day.");
                int id;
                try (PreparedStatement ps = c.prepareStatement("INSERT INTO bookings(client_id,service_id,assigned_staff_id,created_by,booking_date,booking_time,status,total_amount,notes) " +
                        "VALUES(?,?,?,NULL,?,?,'REQUESTED',?,?)", Statement.RETURN_GENERATED_KEYS)) {
                    bind(ps, u.id(), s.id(), staffId, d, t, price, nt);
                    ps.executeUpdate();
                    try (ResultSet k = ps.getGeneratedKeys()) { k.next(); id = k.getInt(1); }
                }
                noteStaff(c, staffId, "📥 New booking request #" + id + " from " + u.fullName() + ": " + svc.get(0)[0] + " on " + d + " around " + t +
                        (nt.isEmpty() ? "" : " — \"" + nt + "\"") + ". Open Requests to set the time and confirm.");
                noteAdmins(c, "📥 " + u.fullName() + " requested " + svc.get(0)[0] + " with " + staffName(staffId) + " on " + d + " around " + t + " (request #" + id + "). Waiting for staff to confirm.");
                log(c, "BOOKING_REQUESTED", "Request #" + id + " — " + svc.get(0)[0] + " on " + d + " " + t);
                c.commit();
            } catch (Exception e) { c.rollback(); throw e; }
        }
    }

    public static List<Object[]> myBookings() throws SQLException {
        User u = Session.require(Perm.CLIENT_BOOK);
        return query("SELECT b.id,s.name,b.booking_date,TIME_FORMAT(b.booking_time,'%H:%i'),COALESCE(u.full_name,'Any available'),b.status,b.total_amount," +
                "CASE WHEN b.status IN ('CANCELLED','REQUESTED') THEN '—' WHEN p.id IS NULL THEN 'UNPAID' WHEN p.status='PENDING' THEN 'PENDING' ELSE 'PAID' END," +
                "CASE WHEN f.id IS NOT NULL THEN 'Given' WHEN b.status='COMPLETED' THEN 'Pending' ELSE '—' END " +
                "FROM bookings b JOIN services s ON s.id=b.service_id LEFT JOIN users u ON u.id=b.assigned_staff_id " +
                "LEFT JOIN payments p ON p.booking_id=b.id LEFT JOIN feedback f ON f.booking_id=b.id " +
                "WHERE b.client_id=? ORDER BY b.booking_date DESC,b.booking_time DESC,b.id DESC", u.id());
    }

    public static String myDueText() throws SQLException {
        User u = Session.require(Perm.CLIENT_PAY);
        return "To pay: " + money(num("SELECT COALESCE(SUM(b.total_amount),0) FROM bookings b LEFT JOIN payments p ON p.booking_id=b.id " +
                "WHERE b.client_id=? AND b.status IN ('CONFIRMED','IN_PROGRESS','COMPLETED') AND p.id IS NULL", u.id()));
    }

    /** Step 4: the client pays after confirmation. The payment goes to the owner (admin) and the staff member is told. */
    public static double payMyBooking(int bookingId, String method) throws SQLException {
        User u = Session.require(Perm.CLIENT_PAY);
        if (!Arrays.asList(PAY_METHODS).contains(method)) throw new IllegalArgumentException("Please choose a payment method.");
        try (Connection c = DB.getConnection()) {
            c.setAutoCommit(false);
            try {
                int owner; double amount; String status; Integer staff; LocalDate d; LocalTime t; int serviceId;
                try (PreparedStatement ps = c.prepareStatement("SELECT client_id,total_amount,status,assigned_staff_id,booking_date,booking_time,service_id FROM bookings WHERE id=? FOR UPDATE")) {
                    ps.setInt(1, bookingId);
                    try (ResultSet r = ps.executeQuery()) {
                        if (!r.next()) throw new IllegalArgumentException("Booking not found.");
                        owner = r.getInt(1); amount = r.getDouble(2); status = r.getString(3);
                        staff = (Integer) r.getObject(4); d = r.getDate(5).toLocalDate(); t = r.getTime(6).toLocalTime(); serviceId = r.getInt(7);
                    }
                }
                if (owner != u.id()) throw new SecurityException("This booking belongs to another client.");
                if ("REQUESTED".equals(status)) throw new IllegalArgumentException("Please wait — the salon has not confirmed this booking yet. You can pay once it is confirmed.");
                if ("CANCELLED".equals(status)) throw new IllegalArgumentException("A cancelled booking cannot be paid.");
                if (num("SELECT COUNT(*) FROM payments WHERE booking_id=? AND status='PENDING'", bookingId) > 0) throw new IllegalArgumentException("You already paid — this booking has already been paid and is waiting for the staff member to confirm it.");
                if (num("SELECT COUNT(*) FROM payments WHERE booking_id=?", bookingId) > 0) throw new IllegalArgumentException("This booking has already been paid.");
                String svc = String.valueOf(query("SELECT name FROM services WHERE id=?", serviceId).get(0)[0]);
                update(c, "INSERT INTO payments(booking_id,client_id,amount,method,received_by,status) VALUES(?,?,?,?,NULL,'PENDING')", bookingId, u.id(), amount, method);
                noteAdmins(c, "💳 " + u.fullName() + " paid " + money(amount) + " for " + svc + " (booking #" + bookingId + ") via " + method + ". Waiting for " + staffName(staff) + " to confirm the payment.");
                if (staff != null) addNote(c, staff, null, "💳 " + u.fullName() + " says they paid " + money(amount) + " via " + method + " for " + svc + " (booking #" + bookingId + ", " + d + " " + t + "). Open Confirm Payments to confirm you received it.");
                addNote(c, null, u.id(), "🧾 Your payment of " + money(amount) + " for " + svc + " (booking #" + bookingId + ") was sent. You will get a message as soon as " + staffName(staff) + " confirms it.");
                log(c, "PAYMENT_SUBMITTED", "Booking #" + bookingId + " " + money(amount) + " via " + method + " (waiting for staff confirmation)");
                c.commit();
                return amount;
            } catch (SQLIntegrityConstraintViolationException e) {
                c.rollback();
                throw new IllegalArgumentException("This booking has already been paid.");
            } catch (Exception e) { c.rollback(); throw e; }
        }
    }

    public static void cancelMyBooking(int bookingId) throws SQLException {
        User u = Session.require(Perm.CLIENT_BOOK);
        try (Connection c = DB.getConnection()) {
            c.setAutoCommit(false);
            try {
                int owner; String status; Integer staff;
                try (PreparedStatement ps = c.prepareStatement("SELECT client_id,status,assigned_staff_id FROM bookings WHERE id=? FOR UPDATE")) {
                    ps.setInt(1, bookingId);
                    try (ResultSet r = ps.executeQuery()) {
                        if (!r.next()) throw new IllegalArgumentException("Booking not found.");
                        owner = r.getInt(1); status = r.getString(2); staff = (Integer) r.getObject(3);
                    }
                }
                if (owner != u.id()) throw new SecurityException("This booking belongs to another client.");
                boolean paid = num("SELECT COUNT(*) FROM payments WHERE booking_id=?", bookingId) > 0;
                if (!("REQUESTED".equals(status) || ("CONFIRMED".equals(status) && !paid)))
                    throw new IllegalArgumentException("Only requests or unpaid confirmed bookings can be cancelled. Please contact the salon for anything else.");
                update(c, "UPDATE bookings SET status='CANCELLED' WHERE id=?", bookingId);
                noteStaff(c, staff, "❌ " + u.fullName() + " cancelled booking #" + bookingId + ".");
                noteAdmins(c, "❌ " + u.fullName() + " cancelled booking #" + bookingId + ".");
                log(c, "BOOKING_CANCELLED", "Booking #" + bookingId + " cancelled by client");
                c.commit();
            } catch (Exception e) { c.rollback(); throw e; }
        }
    }

    /** Completed services of this client that still have no feedback: id, service, date. */
    public static List<Object[]> feedbackEligible() throws SQLException {
        User u = Session.require(Perm.CLIENT_FEEDBACK);
        return query("SELECT b.id,s.name,b.booking_date FROM bookings b JOIN services s ON s.id=b.service_id LEFT JOIN feedback f ON f.booking_id=b.id " +
                "WHERE b.client_id=? AND b.status='COMPLETED' AND f.id IS NULL ORDER BY b.booking_date DESC,b.id DESC", u.id());
    }

    public static void submitFeedback(int bookingId, int rating, String comment, byte[] photo) throws SQLException {
        User u = Session.require(Perm.CLIENT_FEEDBACK);
        if (rating < 1 || rating > 5) throw new IllegalArgumentException("Please choose a rating from 1 to 5 stars.");
        String cm = comment == null ? "" : comment.trim();
        if (cm.length() > 500) throw new IllegalArgumentException("Your comment is too long (maximum 500 characters).");
        if (photo != null && photo.length > 3 * 1024 * 1024) throw new IllegalArgumentException("That photo is too large.");
        try (Connection c = DB.getConnection()) {
            c.setAutoCommit(false);
            try {
                int owner; String status; Integer staff; int serviceId;
                try (PreparedStatement ps = c.prepareStatement("SELECT client_id,status,assigned_staff_id,service_id FROM bookings WHERE id=? FOR UPDATE")) {
                    ps.setInt(1, bookingId);
                    try (ResultSet r = ps.executeQuery()) {
                        if (!r.next()) throw new IllegalArgumentException("Booking not found.");
                        owner = r.getInt(1); status = r.getString(2); staff = (Integer) r.getObject(3); serviceId = r.getInt(4);
                    }
                }
                if (owner != u.id()) throw new SecurityException("This booking belongs to another client.");
                if (!"COMPLETED".equals(status)) throw new IllegalArgumentException("You can give feedback after your service is completed.");
                if (num("SELECT COUNT(*) FROM feedback WHERE booking_id=?", bookingId) > 0) throw new IllegalArgumentException("You already gave feedback for this booking.");
                try (PreparedStatement ps = c.prepareStatement("INSERT INTO feedback(booking_id,client_id,rating,comment,photo) VALUES(?,?,?,?,?)")) {
                    ps.setInt(1, bookingId); ps.setInt(2, u.id()); ps.setInt(3, rating); ps.setString(4, cm);
                    if (photo == null) ps.setNull(5, Types.BLOB); else ps.setBytes(5, photo);
                    ps.executeUpdate();
                }
                String svc = String.valueOf(query("SELECT name FROM services WHERE id=?", serviceId).get(0)[0]);
                String msg = "⭐ New feedback " + rating + "/5 from " + u.fullName() + " on " + svc + (cm.isEmpty() ? "." : ": \"" + cm + "\"");
                noteAdmins(c, msg);
                if (staff != null) addNote(c, staff, null, msg);
                log(c, "FEEDBACK_GIVEN", "Booking #" + bookingId + " rated " + rating + "/5");
                c.commit();
            } catch (SQLIntegrityConstraintViolationException e) {
                c.rollback();
                throw new IllegalArgumentException("You already gave feedback for this booking.");
            } catch (Exception e) { c.rollback(); throw e; }
        }
    }

    // =====================================================================================
    //  REVIEWS (clients see everyone's, the owner sees all, staff see feedback about their own work)
    // =====================================================================================
    public record Review(int id, String client, String service, String staff, int rating, String comment, byte[] photo, String date) {}

    public static List<Review> reviews() throws SQLException {
        User u = Session.require(Perm.VIEW_REVIEWS);
        String sql = "SELECT f.id,c.name,s.name,COALESCE(st.full_name,''),f.rating,COALESCE(f.comment,''),f.photo,DATE_FORMAT(f.created_at,'%d %b %Y') " +
                "FROM feedback f JOIN bookings b ON b.id=f.booking_id JOIN clients c ON c.id=f.client_id JOIN services s ON s.id=b.service_id " +
                "LEFT JOIN users st ON st.id=b.assigned_staff_id " + (u.role() == Role.STAFF ? "WHERE b.assigned_staff_id=? " : "WHERE ?=? ") +
                "ORDER BY f.id DESC LIMIT 200";
        List<Review> l = new ArrayList<>();
        for (Object[] r : u.role() == Role.STAFF ? query(sql, u.id()) : query(sql, 1, 1)) {
            String who = (String) r[1];
            if (u.role() == Role.CLIENT) {   // other clients only see "Sana M."
                String[] parts = who.trim().split("\\s+");
                who = parts.length > 1 ? parts[0] + " " + parts[parts.length - 1].charAt(0) + "." : parts[0];
            }
            l.add(new Review((Integer) r[0], who, (String) r[2], (String) r[3], ((Number) r[4]).intValue(), (String) r[5], (byte[]) r[6], (String) r[7]));
        }
        return l;
    }

    // =====================================================================================
    //  STAFF: requests -> set time -> confirm
    // =====================================================================================
    public static List<Object[]> requests() throws SQLException {
        User u = Session.require(Perm.HANDLE_REQUESTS);
        return query("SELECT b.id,c.name,c.phone,s.name,s.duration_minutes,b.booking_date,TIME_FORMAT(b.booking_time,'%H:%i'),COALESCE(b.notes,'')," +
                "CASE WHEN b.assigned_staff_id IS NULL THEN 'Any staff' ELSE 'You' END FROM bookings b JOIN clients c ON c.id=b.client_id " +
                "JOIN services s ON s.id=b.service_id WHERE b.status='REQUESTED' AND (b.assigned_staff_id=? OR b.assigned_staff_id IS NULL) " +
                "ORDER BY b.booking_date,b.booking_time,b.id", u.id());
    }

    /** Step 2: staff sets the final date/time and confirms. The client is notified. */
    public static void confirmRequest(int id, LocalDate d, LocalTime t) throws SQLException {
        User u = Session.require(Perm.HANDLE_REQUESTS);
        if (d == null || t == null) throw new IllegalArgumentException("Please choose a date and time.");
        if (d.isBefore(LocalDate.now())) throw new IllegalArgumentException("The date cannot be in the past.");
        try (Connection c = DB.getConnection()) {
            c.setAutoCommit(false);
            try {
                int clientId, serviceId; Integer assigned; String status; double amount;
                try (PreparedStatement ps = c.prepareStatement("SELECT client_id,assigned_staff_id,status,total_amount,service_id FROM bookings WHERE id=? FOR UPDATE")) {
                    ps.setInt(1, id);
                    try (ResultSet r = ps.executeQuery()) {
                        if (!r.next()) throw new IllegalArgumentException("Request not found.");
                        clientId = r.getInt(1); assigned = (Integer) r.getObject(2); status = r.getString(3); amount = r.getDouble(4); serviceId = r.getInt(5);
                    }
                }
                if (!"REQUESTED".equals(status)) throw new IllegalArgumentException("This request was already handled.");
                if (assigned != null && assigned != u.id()) throw new SecurityException("This request was sent to another staff member.");
                checkSlot(c, u.id(), d, t, id);
                update(c, "UPDATE bookings SET assigned_staff_id=?,booking_date=?,booking_time=?,status='CONFIRMED' WHERE id=?", u.id(), d, t, id);
                String svc = String.valueOf(query("SELECT name FROM services WHERE id=?", serviceId).get(0)[0]);
                addNote(c, null, clientId, "✅ Your booking #" + id + " for " + svc + " is CONFIRMED with " + u.fullName() + " on " + d + " at " + t +
                        ". Total: " + money(amount) + ". Open My Bookings & Payments and tap PAY NOW.");
                noteAdmins(c, "✅ " + u.fullName() + " confirmed booking #" + id + " (" + svc + ") for " + clientName(clientId) + " on " + d + " at " + t + ". Waiting for the client to pay.");
                log(c, "BOOKING_CONFIRMED", "Request #" + id + " confirmed for " + d + " " + t);
                c.commit();
            } catch (Exception e) { c.rollback(); throw e; }
        }
    }

    public static void declineRequest(int id, String reason) throws SQLException {
        User u = Session.require(Perm.HANDLE_REQUESTS);
        try (Connection c = DB.getConnection()) {
            c.setAutoCommit(false);
            try {
                int clientId, serviceId; Integer assigned; String status;
                try (PreparedStatement ps = c.prepareStatement("SELECT client_id,assigned_staff_id,status,service_id FROM bookings WHERE id=? FOR UPDATE")) {
                    ps.setInt(1, id);
                    try (ResultSet r = ps.executeQuery()) {
                        if (!r.next()) throw new IllegalArgumentException("Request not found.");
                        clientId = r.getInt(1); assigned = (Integer) r.getObject(2); status = r.getString(3); serviceId = r.getInt(4);
                    }
                }
                if (!"REQUESTED".equals(status)) throw new IllegalArgumentException("This request was already handled.");
                if (assigned != null && assigned != u.id()) throw new SecurityException("This request was sent to another staff member.");
                update(c, "UPDATE bookings SET status='CANCELLED' WHERE id=?", id);
                String svc = String.valueOf(query("SELECT name FROM services WHERE id=?", serviceId).get(0)[0]);
                String why = reason == null || reason.isBlank() ? "" : " Reason: " + reason.trim();
                addNote(c, null, clientId, "😔 Sorry, " + u.fullName() + " could not take your request #" + id + " for " + svc + "." + why + " Please request another time or staff member.");
                noteAdmins(c, "✖ " + u.fullName() + " declined request #" + id + " (" + svc + ") from " + clientName(clientId) + "." + why);
                log(c, "REQUEST_DECLINED", "Request #" + id + " declined by " + u.username());
                c.commit();
            } catch (Exception e) { c.rollback(); throw e; }
        }
    }

    // =====================================================================================
    //  STAFF: confirm that the client's payment really arrived
    // =====================================================================================
    public static List<Object[]> pendingPayments() throws SQLException {
        User u = Session.require(Perm.HANDLE_REQUESTS);
        return query("SELECT p.id,c.name,s.name,b.booking_date,p.amount,p.method,DATE_FORMAT(p.paid_at,'%Y-%m-%d %H:%i'),b.status FROM payments p " +
                "JOIN bookings b ON b.id=p.booking_id JOIN clients c ON c.id=p.client_id JOIN services s ON s.id=b.service_id " +
                "WHERE p.status='PENDING' AND b.assigned_staff_id=? ORDER BY p.id", u.id());
    }

    private record PendingPay(int bookingId, int clientId, double amount, String method, String status, Integer staff) {}

    private static PendingPay lockPending(Connection c, int paymentId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT p.booking_id,p.client_id,p.amount,p.method,p.status,b.assigned_staff_id FROM payments p " +
                "JOIN bookings b ON b.id=p.booking_id WHERE p.id=? FOR UPDATE")) {
            ps.setInt(1, paymentId);
            try (ResultSet r = ps.executeQuery()) {
                if (!r.next()) throw new IllegalArgumentException("Payment not found.");
                return new PendingPay(r.getInt(1), r.getInt(2), r.getDouble(3), r.getString(4), r.getString(5), (Integer) r.getObject(6));
            }
        }
    }

    public static void confirmPayment(int paymentId) throws SQLException {
        User u = Session.require(Perm.HANDLE_REQUESTS);
        try (Connection c = DB.getConnection()) {
            c.setAutoCommit(false);
            try {
                PendingPay p = lockPending(c, paymentId);
                if (p.staff() == null || p.staff() != u.id()) throw new SecurityException("This payment belongs to another staff member's booking.");
                if (!"PENDING".equals(p.status())) throw new IllegalArgumentException("This payment was already handled.");
                update(c, "UPDATE payments SET status='CONFIRMED',received_by=?,confirmed_at=NOW() WHERE id=?", u.id(), paymentId);
                addNote(c, null, p.clientId(), "✅ " + u.fullName() + " confirmed your payment of " + money(p.amount()) + " (booking #" + p.bookingId() + "). Thank you!");
                noteAdmins(c, "💰 " + u.fullName() + " confirmed the payment of " + money(p.amount()) + " from " + clientName(p.clientId()) + " (booking #" + p.bookingId() + ", " + p.method() + ").");
                log(c, "PAYMENT_CONFIRMED", "Payment #" + paymentId + " booking #" + p.bookingId() + " " + money(p.amount()) + " confirmed by " + u.username());
                c.commit();
            } catch (Exception e) { c.rollback(); throw e; }
        }
    }

    /** The money did not arrive: the pending payment is removed so the client can pay again. */
    public static void rejectPayment(int paymentId, String reason) throws SQLException {
        User u = Session.require(Perm.HANDLE_REQUESTS);
        try (Connection c = DB.getConnection()) {
            c.setAutoCommit(false);
            try {
                PendingPay p = lockPending(c, paymentId);
                if (p.staff() == null || p.staff() != u.id()) throw new SecurityException("This payment belongs to another staff member's booking.");
                if (!"PENDING".equals(p.status())) throw new IllegalArgumentException("This payment was already handled.");
                update(c, "DELETE FROM payments WHERE id=?", paymentId);
                String why = reason == null || reason.isBlank() ? "" : " Reason: " + reason.trim();
                addNote(c, null, p.clientId(), "⚠ " + u.fullName() + " could not find your payment of " + money(p.amount()) + " (booking #" + p.bookingId() + ")." + why + " Please pay again from My Bookings & Payments.");
                noteAdmins(c, "⚠ " + u.fullName() + " rejected the payment of " + money(p.amount()) + " from " + clientName(p.clientId()) + " (booking #" + p.bookingId() + ")." + why);
                log(c, "PAYMENT_REJECTED", "Payment #" + paymentId + " booking #" + p.bookingId() + " rejected by " + u.username());
                c.commit();
            } catch (Exception e) { c.rollback(); throw e; }
        }
    }

    // =====================================================================================
    //  ADMIN: service photos
    // =====================================================================================
    public static void setServiceImage(int serviceId, byte[] image) throws SQLException {
        Session.require(Perm.MANAGE_SERVICES);
        try (Connection c = DB.getConnection(); PreparedStatement ps = c.prepareStatement("UPDATE services SET image=? WHERE id=?")) {
            if (image == null) ps.setNull(1, Types.BLOB); else ps.setBytes(1, image);
            ps.setInt(2, serviceId);
            ps.executeUpdate();
            log(c, "SERVICE_PHOTO", "Service #" + serviceId + (image == null ? " photo removed" : " photo updated"));
        }
    }
}
