package com.salon;

import java.sql.*;

public final class DB {
    private DB() {}

    private static String name() { return Config.get("db.name", "salon_db"); }
    private static String params() { return "?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true"; }

    private static String url() {
        String custom = Config.get("db.url", "");
        if (!custom.isEmpty()) return custom;
        return "jdbc:mysql://" + Config.get("db.host", "localhost") + ":" + Config.get("db.port", "3306") + "/" + name() + params();
    }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(url(), Config.get("db.user", "root"), Config.password());
    }

    /** Creates / upgrades the schema without deleting data, then seeds demo accounts if there are no users. */
    public static void initialize() throws SQLException {
        if (Config.get("db.url", "").isEmpty()) {
            String serverUrl = "jdbc:mysql://" + Config.get("db.host", "localhost") + ":" + Config.get("db.port", "3306") + "/" + params();
            try (Connection c = DriverManager.getConnection(serverUrl, Config.get("db.user", "root"), Config.password());
                 Statement s = c.createStatement()) {
                s.executeUpdate("CREATE DATABASE IF NOT EXISTS `" + name().replaceAll("[^A-Za-z0-9_]", "") + "` CHARACTER SET utf8mb4");
            }
        }
        try (Connection c = getConnection(); Statement s = c.createStatement()) {
            // --- original tables (kept exactly; created only on fresh installs) ---
            s.executeUpdate("CREATE TABLE IF NOT EXISTS clients (id INT PRIMARY KEY AUTO_INCREMENT, name VARCHAR(100) NOT NULL, "
                    + "phone VARCHAR(30) NOT NULL, email VARCHAR(100), notes VARCHAR(500), created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS services (id INT PRIMARY KEY AUTO_INCREMENT, name VARCHAR(100) NOT NULL UNIQUE, "
                    + "category VARCHAR(60), duration_minutes INT NOT NULL, price DECIMAL(10,2) NOT NULL)");
            // --- new tables ---
            s.executeUpdate("CREATE TABLE IF NOT EXISTS users (id INT PRIMARY KEY AUTO_INCREMENT, full_name VARCHAR(100) NOT NULL, "
                    + "username VARCHAR(50) NOT NULL UNIQUE, password_hash VARCHAR(255) NOT NULL, role VARCHAR(20) NOT NULL, "
                    + "is_active TINYINT(1) NOT NULL DEFAULT 1, must_change_password TINYINT(1) NOT NULL DEFAULT 0, "
                    + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, CONSTRAINT chk_users_role CHECK (role IN ('ADMIN','RECEPTIONIST','STAFF')))");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS bookings (id INT PRIMARY KEY AUTO_INCREMENT, client_id INT NOT NULL, service_id INT NOT NULL, "
                    + "assigned_staff_id INT NULL, created_by INT NULL, booking_date DATE NOT NULL, booking_time TIME NOT NULL, "
                    + "status VARCHAR(20) NOT NULL DEFAULT 'BOOKED', total_amount DECIMAL(10,2) NOT NULL, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
                    + "FOREIGN KEY (client_id) REFERENCES clients(id), FOREIGN KEY (service_id) REFERENCES services(id))");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS payments (id INT PRIMARY KEY AUTO_INCREMENT, booking_id INT NOT NULL, client_id INT NOT NULL, "
                    + "amount DECIMAL(10,2) NOT NULL, method VARCHAR(30) NOT NULL, paid_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, received_by INT NULL, "
                    + "FOREIGN KEY (booking_id) REFERENCES bookings(id), FOREIGN KEY (client_id) REFERENCES clients(id))");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS activity_logs (id BIGINT PRIMARY KEY AUTO_INCREMENT, user_id INT NULL, "
                    + "action VARCHAR(50) NOT NULL, details VARCHAR(500), created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");

            s.executeUpdate("CREATE TABLE IF NOT EXISTS notifications (id BIGINT PRIMARY KEY AUTO_INCREMENT, user_id INT NULL, client_id INT NULL, "
                    + "message VARCHAR(400) NOT NULL, is_read TINYINT(1) NOT NULL DEFAULT 0, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
                    + "FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE, FOREIGN KEY (client_id) REFERENCES clients(id) ON DELETE CASCADE)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS feedback (id INT PRIMARY KEY AUTO_INCREMENT, booking_id INT NOT NULL UNIQUE, client_id INT NOT NULL, "
                    + "rating TINYINT NOT NULL, comment VARCHAR(600), photo MEDIUMBLOB NULL, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
                    + "FOREIGN KEY (booking_id) REFERENCES bookings(id), FOREIGN KEY (client_id) REFERENCES clients(id))");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS app_meta (k VARCHAR(50) PRIMARY KEY, v VARCHAR(100))");

            // --- upgrade older installs (adds missing columns / keys; never deletes data) ---
            addColumn(c, "services", "image", "MEDIUMBLOB NULL");
            addColumn(c, "bookings", "notes", "VARCHAR(300) NULL");
            addIndex(c, "notifications", "idx_notes_user", "user_id");
            addIndex(c, "notifications", "idx_notes_client", "client_id");
            // The Receptionist role was replaced by Clients (email sign-in). Old receptionist accounts are kept but disabled.
            ddl(c, "UPDATE users SET role='STAFF', is_active=0 WHERE role='RECEPTIONIST'");
            addColumn(c, "bookings", "assigned_staff_id", "INT NULL");
            addColumn(c, "bookings", "created_by", "INT NULL");
            addColumn(c, "payments", "received_by", "INT NULL");
            addColumn(c, "payments", "status", "VARCHAR(20) NOT NULL DEFAULT 'CONFIRMED'");   // PENDING until the staff member confirms
            addColumn(c, "payments", "confirmed_at", "TIMESTAMP NULL");
            addIndex(c, "clients", "idx_clients_phone", "phone");
            addIndex(c, "bookings", "idx_bookings_status", "status");
            addIndex(c, "bookings", "idx_bookings_client", "client_id");
            addIndex(c, "bookings", "idx_bookings_date", "booking_date");
            addIndex(c, "bookings", "idx_bookings_staff", "assigned_staff_id");
            addIndex(c, "payments", "idx_payments_paid_at", "paid_at");
            addIndex(c, "activity_logs", "idx_logs_created", "created_at");
            addIndex(c, "activity_logs", "idx_logs_user", "user_id");
            addFk(c, "bookings", "fk_bookings_staff", "assigned_staff_id", "users");
            addFk(c, "bookings", "fk_bookings_creator", "created_by", "users");
            addFk(c, "payments", "fk_payments_receiver", "received_by", "users");
            addFk(c, "activity_logs", "fk_logs_user", "user_id", "users");
            addUniquePaymentPerBooking(c);
            seedServices(c);
            seedUsers(c);
            seedDemoData(c);
        }
    }

    private static boolean exists(Connection c, String sql, String... args) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) ps.setString(i + 1, args[i]);
            try (ResultSet r = ps.executeQuery()) { r.next(); return r.getInt(1) > 0; }
        }
    }

    private static void ddl(Connection c, String sql) throws SQLException {
        try (Statement s = c.createStatement()) { s.executeUpdate(sql); }
    }

    private static void addColumn(Connection c, String t, String col, String def) throws SQLException {
        if (!exists(c, "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name=? AND column_name=?", t, col))
            ddl(c, "ALTER TABLE " + t + " ADD COLUMN " + col + " " + def);
    }

    private static void addIndex(Connection c, String t, String idx, String col) throws SQLException {
        if (!exists(c, "SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name=? AND index_name=?", t, idx))
            ddl(c, "CREATE INDEX " + idx + " ON " + t + "(" + col + ")");
    }

    private static void addFk(Connection c, String t, String fk, String col, String ref) throws SQLException {
        if (!exists(c, "SELECT COUNT(*) FROM information_schema.table_constraints WHERE table_schema=DATABASE() AND table_name=? AND constraint_name=?", t, fk))
            ddl(c, "ALTER TABLE " + t + " ADD CONSTRAINT " + fk + " FOREIGN KEY (" + col + ") REFERENCES " + ref + "(id) ON DELETE SET NULL");
    }

    /** Blocks duplicate payments at the database level (skipped with a warning if old duplicate rows already exist). */
    private static void addUniquePaymentPerBooking(Connection c) throws SQLException {
        if (exists(c, "SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name=? AND index_name=?", "payments", "uq_payments_booking"))
            return;
        if (exists(c, "SELECT COUNT(*) FROM (SELECT booking_id FROM payments GROUP BY booking_id HAVING COUNT(*)>1) d")) {
            System.err.println("WARNING: duplicate payments exist for some bookings; unique index uq_payments_booking was not created.");
            return;
        }
        ddl(c, "CREATE UNIQUE INDEX uq_payments_booking ON payments(booking_id)");
    }

    private static void seedServices(Connection c) throws SQLException {
        Object[][] services = {
            {"Hair Cut", "Hair", 45, 800}, {"Hair Styling", "Hair", 60, 1200}, {"Hair Color", "Hair", 120, 3500},
            {"Facial Glow", "Skin", 60, 1800}, {"Manicure", "Nails", 45, 1000}, {"Pedicure", "Nails", 60, 1300},
            {"Party Ready", "Special", 120, 4500}, {"Wedding Ready", "Special", 240, 10000},
            {"Bridal Package", "Special", 300, 15000}, {"Makeup", "Makeup", 90, 4000}, {"Spa Relax", "Spa", 90, 2500}
        };
        try (PreparedStatement ps = c.prepareStatement("INSERT IGNORE INTO services(name,category,duration_minutes,price) VALUES (?,?,?,?)")) {
            for (Object[] x : services) {
                ps.setString(1, (String) x[0]); ps.setString(2, (String) x[1]);
                ps.setInt(3, (Integer) x[2]); ps.setDouble(4, (Integer) x[3]);
                ps.addBatch();
            }
            ps.executeBatch();
        }
        attachServiceImages(c);
    }

    /** Built-in pictures (src/main/resources/images/services) for the 11 standard services. Only fills services that have no picture yet,
     *  so a picture the owner uploaded later is never overwritten. */
    private static void attachServiceImages(Connection c) throws SQLException {
        String[][] pics = {
            {"Hair Color", "hair-color"}, {"Hair Cut", "hair-cut"}, {"Hair Styling", "hair-styling"}, {"Makeup", "makeup"},
            {"Manicure", "manicure"}, {"Pedicure", "pedicure"}, {"Facial Glow", "facial-glow"}, {"Spa Relax", "spa-relax"},
            {"Bridal Package", "bridal-package"}, {"Party Ready", "party-ready"}, {"Wedding Ready", "wedding-ready"}
        };
        try (PreparedStatement ps = c.prepareStatement("UPDATE services SET image=? WHERE name=? AND image IS NULL")) {
            for (String[] p : pics) {
                try (java.io.InputStream in = DB.class.getResourceAsStream("/images/services/" + p[1] + ".jpg")) {
                    if (in == null) continue;
                    ps.setBytes(1, in.readAllBytes());
                    ps.setString(2, p[0]);
                    ps.executeUpdate();
                } catch (java.io.IOException e) { System.err.println("Could not load picture for " + p[0] + ": " + e.getMessage()); }
            }
        }
    }

    /** First run only: the owner (admin) and three staff accounts (clients sign in with just an email). Passwords are hashed; users must change them at first login. */
    private static final String[][] DEMO = {
        {"Salon Owner", "admin", "Admin@123", "ADMIN"},
        {"Senior Stylist", "staff", "Staff@123", "STAFF"},
        {"Ayesha Khan", "ayesha", "Ayesha@123", "STAFF"},
        {"Sara Ahmed", "sara", "Sara@123", "STAFF"}
    };

    /**
     * Keeps the documented demo logins working:
     *  - a missing demo account is created;
     *  - a demo account whose password was never changed (must_change_password=1) gets the documented password;
     *  - with --reset-demo (or env SALON_RESET_DEMO=1) all four demo accounts are reset, enabled and flagged for a new password.
     * Accounts whose owner already chose their own password are left alone otherwise.
     */
    private static void seedUsers(Connection c) throws SQLException {
        boolean force = "1".equals(System.getenv("SALON_RESET_DEMO")) || System.getProperty("salon.resetDemo") != null;
        for (String[] d : DEMO) {
            String hash = null; boolean mustChange = false;
            try (PreparedStatement ps = c.prepareStatement("SELECT password_hash,must_change_password FROM users WHERE username=?")) {
                ps.setString(1, d[1]);
                try (ResultSet r = ps.executeQuery()) { if (r.next()) { hash = r.getString(1); mustChange = r.getBoolean(2); } }
            }
            if (hash == null) {
                try (PreparedStatement ps = c.prepareStatement("INSERT INTO users(full_name,username,password_hash,role,is_active,must_change_password) VALUES (?,?,?,?,1,1)")) {
                    ps.setString(1, d[0]); ps.setString(2, d[1]); ps.setString(3, Passwords.hash(d[2])); ps.setString(4, d[3]);
                    ps.executeUpdate();
                }
            } else if ((force || mustChange) && !Passwords.verify(d[2], hash)) {
                try (PreparedStatement ps = c.prepareStatement("UPDATE users SET password_hash=?,must_change_password=1,is_active=1 WHERE username=?")) {
                    ps.setString(1, Passwords.hash(d[2])); ps.setString(2, d[1]);
                    ps.executeUpdate();
                }
            } else if (force) {
                ddl(c, "UPDATE users SET must_change_password=1,is_active=1 WHERE username='" + d[1] + "'");
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    //  Sample data so every screen has something to show on the first run (skipped on existing installs)
    // ---------------------------------------------------------------------------------------------
    private static int insertKey(Connection c, String sql, Object... p) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            for (int i = 0; i < p.length; i++) {
                Object v = p[i];
                if (v instanceof java.time.LocalDate d) v = java.sql.Date.valueOf(d);
                else if (v instanceof java.time.LocalTime t) v = java.sql.Time.valueOf(t);
                else if (v instanceof java.time.LocalDateTime dt) v = Timestamp.valueOf(dt);
                ps.setObject(i + 1, v);
            }
            ps.executeUpdate();
            try (ResultSet k = ps.getGeneratedKeys()) { k.next(); return k.getInt(1); }
        }
    }

    private static java.util.List<Integer> ids(Connection c, String sql) throws SQLException {
        java.util.List<Integer> l = new java.util.ArrayList<>();
        try (Statement st = c.createStatement(); ResultSet r = st.executeQuery(sql)) { while (r.next()) l.add(r.getInt(1)); }
        return l;
    }

    private static void note(Connection c, Integer userId, Integer clientId, String msg, boolean read) throws SQLException {
        insertKey(c, "INSERT INTO notifications(user_id,client_id,message,is_read) VALUES(?,?,?,?)", userId, clientId, msg, read ? 1 : 0);
    }

    private static void seedDemoData(Connection c) throws SQLException {
        if (exists(c, "SELECT COUNT(*) FROM app_meta WHERE k='demo_seeded'")) return;
        boolean fresh = !exists(c, "SELECT COUNT(*) FROM clients") && !exists(c, "SELECT COUNT(*) FROM bookings");
        ddl(c, "INSERT INTO app_meta(k,v) VALUES('demo_seeded','1')");
        if (!fresh) return;                       // never touch an existing salon's real data
        java.util.List<Integer> staff = ids(c, "SELECT id FROM users WHERE role='STAFF' AND is_active=1 ORDER BY id");
        java.util.List<Integer> admins = ids(c, "SELECT id FROM users WHERE role='ADMIN' AND is_active=1 ORDER BY id");
        if (staff.isEmpty()) return;

        String[][] people = {
            {"Sana Malik", "0300-1112233", "sana.malik@example.com", "Prefers afternoon appointments"},
            {"Hira Ali", "0321-4455667", "hira.ali@example.com", ""},
            {"Zara Khan", "0333-7788990", "zara.khan@example.com", "Sensitive skin"},
            {"Maria Shah", "0345-2233445", "maria.shah@example.com", ""}
        };
        int[] cid = new int[people.length];
        for (int i = 0; i < people.length; i++)
            cid[i] = insertKey(c, "INSERT INTO clients(name,phone,email,notes) VALUES(?,?,?,?)", people[i][0], people[i][1], people[i][2], people[i][3]);

        java.util.Map<String, Object[]> svc = new java.util.HashMap<>();
        try (Statement st = c.createStatement(); ResultSet r = st.executeQuery("SELECT id,name,price FROM services")) {
            while (r.next()) svc.put(r.getString(2), new Object[]{r.getInt(1), r.getDouble(3)});
        }
        java.time.LocalDate today = java.time.LocalDate.now();
        // client index, service, days ago, time, payment method, rating, comment
        Object[][] done = {
            {0, "Hair Cut", 26, "11:00", "Card", 5, "Loved my haircut! Very professional and friendly staff."},
            {1, "Facial Glow", 20, "14:00", "JazzCash / Easypaisa", 5, "My skin feels amazing. The salon is spotless and so relaxing."},
            {2, "Manicure", 15, "16:00", "Cash", 4, "Beautiful nail art. A short wait, but totally worth it."},
            {3, "Bridal Package", 11, "10:00", "Bank Transfer", 5, "Perfect bridal look. Thank you for making my day so special!"},
            {0, "Hair Color", 6, "13:00", "Card", 4, "Gorgeous colour. Would love to see even more shades."},
            {1, "Makeup", 2, "17:00", "JazzCash / Easypaisa", 5, "Party makeup stayed flawless all night long."},
            {2, "Spa Relax", 1, "15:00", "Card", 4, "Very calming. The massage was just right."}
        };
        for (int i = 0; i < done.length; i++) {
            Object[] x = done[i];
            Object[] sv = svc.get((String) x[1]);
            if (sv == null) continue;
            int st = staff.get(i % staff.size());
            java.time.LocalDate d = today.minusDays((Integer) x[2]);
            java.time.LocalTime t = java.time.LocalTime.parse((String) x[3]);
            int bid = insertKey(c, "INSERT INTO bookings(client_id,service_id,assigned_staff_id,created_by,booking_date,booking_time,status,total_amount) VALUES(?,?,?,NULL,?,?,'COMPLETED',?)",
                    cid[(Integer) x[0]], sv[0], st, d, t, sv[1]);
            insertKey(c, "INSERT INTO payments(booking_id,client_id,amount,method,paid_at,received_by) VALUES(?,?,?,?,?,NULL)",
                    bid, cid[(Integer) x[0]], sv[1], x[4], java.time.LocalDateTime.of(d, t).minusMinutes(5));
            insertKey(c, "INSERT INTO feedback(booking_id,client_id,rating,comment,created_at) VALUES(?,?,?,?,?)",
                    bid, cid[(Integer) x[0]], x[5], x[6], java.time.LocalDateTime.of(d, t).plusHours(3));
            if (i >= done.length - 3) {   // the newest ones are still unread in the inboxes
                for (Integer a : admins) note(c, a, null, "💳 Payment received: Rs. " + String.format("%,.0f", (Double) sv[1]) + " from " + people[(Integer) x[0]][0] + " for " + x[1] + " via " + x[4] + ".", false);
                note(c, st, null, "⭐ New feedback " + x[5] + "/5 from " + people[(Integer) x[0]][0] + " on " + x[1] + ": \"" + x[6] + "\"", false);
            }
        }
        // 1) a confirmed booking waiting for the client to pay
        Object[] ped = svc.get("Pedicure");
        if (ped != null) {
            int st = staff.get(0);
            int bid = insertKey(c, "INSERT INTO bookings(client_id,service_id,assigned_staff_id,created_by,booking_date,booking_time,status,total_amount) VALUES(?,?,?,NULL,?,?,'CONFIRMED',?)",
                    cid[0], ped[0], st, today.plusDays(2), java.time.LocalTime.of(15, 0), ped[1]);
            note(c, null, cid[0], "✅ Your booking #" + bid + " for Pedicure is CONFIRMED on " + today.plusDays(2) + " at 15:00. Total: Rs. " + String.format("%,.0f", (Double) ped[1]) + ". Open My Bookings & Payments and tap PAY NOW.", false);
        }
        // 2) requests waiting for staff to set a time and confirm
        Object[] sty = svc.get("Hair Styling"), par = svc.get("Party Ready");
        if (sty != null) {
            int bid = insertKey(c, "INSERT INTO bookings(client_id,service_id,assigned_staff_id,created_by,booking_date,booking_time,status,total_amount,notes) VALUES(?,?,NULL,NULL,?,?,'REQUESTED',?,?)",
                    cid[1], sty[0], today.plusDays(3), java.time.LocalTime.of(12, 0), sty[1], "Wedding guest look, please keep it natural");
            for (Integer st : staff) note(c, st, null, "📥 New booking request #" + bid + " from " + people[1][0] + ": Hair Styling on " + today.plusDays(3) + " around 12:00. Open Requests to set the time and confirm.", false);
        }
        if (par != null) {
            int st = staff.get(Math.min(1, staff.size() - 1));
            int bid = insertKey(c, "INSERT INTO bookings(client_id,service_id,assigned_staff_id,created_by,booking_date,booking_time,status,total_amount) VALUES(?,?,?,NULL,?,?,'REQUESTED',?)",
                    cid[3], par[0], st, today.plusDays(4), java.time.LocalTime.of(18, 0), par[1]);
            note(c, st, null, "📥 New booking request #" + bid + " from " + people[3][0] + ": Party Ready on " + today.plusDays(4) + " around 18:00. Open Requests to set the time and confirm.", false);
        }
    }
}
