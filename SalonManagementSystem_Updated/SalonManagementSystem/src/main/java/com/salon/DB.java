package com.salon;

import java.sql.*;

public final class DB {
    public static final String DB_URL =
            "jdbc:mysql://localhost:3306/salon_db?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true";
    public static final String DB_USER = "root";
    public static final String DB_PASSWORD = "Natasha@123"; // Put your MySQL password here.

    private DB() {}

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
    }

    public static void initialize() {
        String serverUrl =
                "jdbc:mysql://localhost:3306/?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true";
        try (Connection c = DriverManager.getConnection(serverUrl, DB_USER, DB_PASSWORD);
             Statement s = c.createStatement()) {
            s.executeUpdate("CREATE DATABASE IF NOT EXISTS salon_db");
        } catch (SQLException e) {
            System.err.println("Could not create database: " + e.getMessage());
            return;
        }

        String[] sql = {
            """
            CREATE TABLE IF NOT EXISTS clients (
                id INT PRIMARY KEY AUTO_INCREMENT,
                name VARCHAR(100) NOT NULL,
                phone VARCHAR(30) NOT NULL,
                email VARCHAR(100),
                notes VARCHAR(500),
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS services (
                id INT PRIMARY KEY AUTO_INCREMENT,
                name VARCHAR(100) NOT NULL UNIQUE,
                category VARCHAR(60),
                duration_minutes INT NOT NULL,
                price DECIMAL(10,2) NOT NULL
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS bookings (
                id INT PRIMARY KEY AUTO_INCREMENT,
                client_id INT NOT NULL,
                service_id INT NOT NULL,
                booking_date DATE NOT NULL,
                booking_time TIME NOT NULL,
                status VARCHAR(20) NOT NULL DEFAULT 'BOOKED',
                total_amount DECIMAL(10,2) NOT NULL,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY (client_id) REFERENCES clients(id),
                FOREIGN KEY (service_id) REFERENCES services(id)
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS payments (
                id INT PRIMARY KEY AUTO_INCREMENT,
                booking_id INT NOT NULL,
                client_id INT NOT NULL,
                amount DECIMAL(10,2) NOT NULL,
                method VARCHAR(30) NOT NULL,
                paid_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY (booking_id) REFERENCES bookings(id),
                FOREIGN KEY (client_id) REFERENCES clients(id)
            )
            """
        };

        try (Connection c = getConnection(); Statement s = c.createStatement()) {
            for (String q : sql) s.executeUpdate(q);
            seedServices(c);
            addIndexIfMissing(c, "clients", "idx_clients_phone", "phone");
            addIndexIfMissing(c, "bookings", "idx_bookings_status", "status");
            addIndexIfMissing(c, "bookings", "idx_bookings_client", "client_id");
            addIndexIfMissing(c, "payments", "idx_payments_paid_at", "paid_at");
        } catch (SQLException e) {
            System.err.println("Database initialization failed: " + e.getMessage());
        }
    }

    // Indexes speed up the lookups the app does on every screen refresh
    // (finding a client by phone, filtering active bookings, summing today's
    // payments) without needing a full table scan as the salon's data grows.
    private static void addIndexIfMissing(Connection c, String table, String indexName, String column) {
        try (PreparedStatement check = c.prepareStatement(
                "SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name=? AND index_name=?")) {
            check.setString(1, table);
            check.setString(2, indexName);
            try (ResultSet r = check.executeQuery()) {
                r.next();
                if (r.getInt(1) == 0) {
                    try (Statement s = c.createStatement()) {
                        s.executeUpdate("CREATE INDEX " + indexName + " ON " + table + "(" + column + ")");
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Could not create index " + indexName + ": " + e.getMessage());
        }
    }

    private static void seedServices(Connection c) throws SQLException {
        String q = """
            INSERT IGNORE INTO services(name, category, duration_minutes, price)
            VALUES (?, ?, ?, ?)
            """;
        Object[][] services = {
            {"Hair Cut", "Hair", 45, 800},
            {"Hair Styling", "Hair", 60, 1200},
            {"Hair Color", "Hair", 120, 3500},
            {"Facial Glow", "Skin", 60, 1800},
            {"Manicure", "Nails", 45, 1000},
            {"Pedicure", "Nails", 60, 1300},
            {"Party Ready", "Special", 120, 4500},
            {"Wedding Ready", "Special", 240, 10000},
            {"Bridal Package", "Special", 300, 15000},
            {"Makeup", "Makeup", 90, 4000},
            {"Spa Relax", "Spa", 90, 2500}
        };
        try (PreparedStatement ps = c.prepareStatement(q)) {
            for (Object[] x : services) {
                ps.setString(1, (String)x[0]);
                ps.setString(2, (String)x[1]);
                ps.setInt(3, (Integer)x[2]);
                ps.setDouble(4, (Integer)x[3]);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }
}
