-- ============================================================================
--  BEAUTY BLOOM SALON  -  database migration (safe to run on a NEW or EXISTING salon_db)
--  * Never deletes data. Re-running it is harmless.
--  * Adds: users, activity_logs, bookings.assigned_staff_id / created_by, payments.received_by,
--    foreign keys, indexes and a UNIQUE index that prevents duplicate payments.
--  * Client workflow: notifications, feedback (reviews with optional photo), services.image, bookings.notes,
--    booking statuses REQUESTED / CONFIRMED. Old "Receptionist" accounts become disabled staff accounts.
--  * The sample clients, bookings, payments and reviews are added by the app itself on first start.
--  Run in MySQL Workbench: open this file and click the lightning bolt (Execute All).
-- ============================================================================
CREATE DATABASE IF NOT EXISTS salon_db CHARACTER SET utf8mb4;
USE salon_db;

CREATE TABLE IF NOT EXISTS clients (
    id INT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    phone VARCHAR(30) NOT NULL,
    email VARCHAR(100),
    notes VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS services (
    id INT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL UNIQUE,
    category VARCHAR(60),
    duration_minutes INT NOT NULL,
    price DECIMAL(10,2) NOT NULL
);
CREATE TABLE IF NOT EXISTS users (
    id INT PRIMARY KEY AUTO_INCREMENT,
    full_name VARCHAR(100) NOT NULL,
    username VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL,
    is_active TINYINT(1) NOT NULL DEFAULT 1,
    must_change_password TINYINT(1) NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_users_role CHECK (role IN ('ADMIN','RECEPTIONIST','STAFF'))
);
CREATE TABLE IF NOT EXISTS bookings (
    id INT PRIMARY KEY AUTO_INCREMENT,
    client_id INT NOT NULL,
    service_id INT NOT NULL,
    assigned_staff_id INT NULL,
    created_by INT NULL,
    booking_date DATE NOT NULL,
    booking_time TIME NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'BOOKED',
    total_amount DECIMAL(10,2) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (client_id) REFERENCES clients(id),
    FOREIGN KEY (service_id) REFERENCES services(id)
);
CREATE TABLE IF NOT EXISTS payments (
    id INT PRIMARY KEY AUTO_INCREMENT,
    booking_id INT NOT NULL,
    client_id INT NOT NULL,
    amount DECIMAL(10,2) NOT NULL,
    method VARCHAR(30) NOT NULL,
    paid_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    received_by INT NULL,
    FOREIGN KEY (booking_id) REFERENCES bookings(id),
    FOREIGN KEY (client_id) REFERENCES clients(id)
);
CREATE TABLE IF NOT EXISTS activity_logs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id INT NULL,
    action VARCHAR(50) NOT NULL,
    details VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- helper procedures (dropped again at the end)
DROP PROCEDURE IF EXISTS sp_add_column;
DROP PROCEDURE IF EXISTS sp_add_index;
DROP PROCEDURE IF EXISTS sp_add_fk;
DELIMITER //
CREATE PROCEDURE sp_add_column(t VARCHAR(64), c VARCHAR(64), def VARCHAR(200))
BEGIN
  IF (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name=t AND column_name=c) = 0 THEN
    SET @s = CONCAT('ALTER TABLE ', t, ' ADD COLUMN ', c, ' ', def);
    PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;
  END IF;
END//
CREATE PROCEDURE sp_add_index(t VARCHAR(64), i VARCHAR(64), c VARCHAR(64), uq INT)
BEGIN
  IF (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name=t AND index_name=i) = 0 THEN
    SET @s = CONCAT('CREATE ', IF(uq=1,'UNIQUE ',''), 'INDEX ', i, ' ON ', t, '(', c, ')');
    PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;
  END IF;
END//
CREATE PROCEDURE sp_add_fk(t VARCHAR(64), f VARCHAR(64), c VARCHAR(64))
BEGIN
  IF (SELECT COUNT(*) FROM information_schema.table_constraints WHERE table_schema=DATABASE() AND table_name=t AND constraint_name=f) = 0 THEN
    SET @s = CONCAT('ALTER TABLE ', t, ' ADD CONSTRAINT ', f, ' FOREIGN KEY (', c, ') REFERENCES users(id) ON DELETE SET NULL');
    PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;
  END IF;
END//
DELIMITER ;

-- upgrade older installs
CALL sp_add_column('bookings', 'assigned_staff_id', 'INT NULL');
CALL sp_add_column('bookings', 'created_by', 'INT NULL');
CALL sp_add_column('payments', 'received_by', 'INT NULL');
CALL sp_add_column('payments', 'status', "VARCHAR(20) NOT NULL DEFAULT 'CONFIRMED'");   -- PENDING until staff confirms
CALL sp_add_column('payments', 'confirmed_at', 'TIMESTAMP NULL');
CALL sp_add_index('clients', 'idx_clients_phone', 'phone', 0);
CALL sp_add_index('bookings', 'idx_bookings_status', 'status', 0);
CALL sp_add_index('bookings', 'idx_bookings_client', 'client_id', 0);
CALL sp_add_index('bookings', 'idx_bookings_date', 'booking_date', 0);
CALL sp_add_index('bookings', 'idx_bookings_staff', 'assigned_staff_id', 0);
CALL sp_add_index('payments', 'idx_payments_paid_at', 'paid_at', 0);
CALL sp_add_index('activity_logs', 'idx_logs_created', 'created_at', 0);
CALL sp_add_index('activity_logs', 'idx_logs_user', 'user_id', 0);
CALL sp_add_fk('bookings', 'fk_bookings_staff', 'assigned_staff_id');
CALL sp_add_fk('bookings', 'fk_bookings_creator', 'created_by');
CALL sp_add_fk('payments', 'fk_payments_receiver', 'received_by');
CALL sp_add_fk('activity_logs', 'fk_logs_user', 'user_id');
-- one payment per booking (fails only if old duplicate payments exist: fix those rows first)
CALL sp_add_index('payments', 'uq_payments_booking', 'booking_id', 1);

-- ---------- client workflow tables ----------
CREATE TABLE IF NOT EXISTS notifications (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id INT NULL,
    client_id INT NULL,
    message VARCHAR(400) NOT NULL,
    is_read TINYINT(1) NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (client_id) REFERENCES clients(id) ON DELETE CASCADE
);
CREATE TABLE IF NOT EXISTS feedback (
    id INT PRIMARY KEY AUTO_INCREMENT,
    booking_id INT NOT NULL UNIQUE,
    client_id INT NOT NULL,
    rating TINYINT NOT NULL,
    comment VARCHAR(600),
    photo MEDIUMBLOB NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (booking_id) REFERENCES bookings(id),
    FOREIGN KEY (client_id) REFERENCES clients(id)
);
CREATE TABLE IF NOT EXISTS app_meta (k VARCHAR(50) PRIMARY KEY, v VARCHAR(100));
CALL sp_add_column('services', 'image', 'MEDIUMBLOB NULL');
CALL sp_add_column('bookings', 'notes', 'VARCHAR(300) NULL');
CALL sp_add_index('notifications', 'idx_notes_user', 'user_id', 0);
CALL sp_add_index('notifications', 'idx_notes_client', 'client_id', 0);
UPDATE users SET role='STAFF', is_active=0 WHERE role='RECEPTIONIST';

DROP PROCEDURE sp_add_column;
DROP PROCEDURE sp_add_index;
DROP PROCEDURE sp_add_fk;

-- default services (existing ones are kept)
INSERT IGNORE INTO services(name, category, duration_minutes, price) VALUES
('Hair Cut','Hair',45,800),('Hair Styling','Hair',60,1200),('Hair Color','Hair',120,3500),
('Facial Glow','Skin',60,1800),('Manicure','Nails',45,1000),('Pedicure','Nails',60,1300),
('Party Ready','Special',120,4500),('Wedding Ready','Special',240,10000),('Bridal Package','Special',300,15000),
('Makeup','Makeup',90,4000),('Spa Relax','Spa',90,2500);

-- demo accounts (passwords are salted PBKDF2 hashes; each user must change the password at first login)
--   admin / Admin@123    staff / Staff@123    ayesha / Ayesha@123    sara / Sara@123
--   Clients have no password: they sign in with their email (e.g. sana.malik@example.com).
INSERT INTO users(full_name, username, password_hash, role, is_active, must_change_password)
SELECT 'Salon Owner','admin','pbkdf2$210000$o+29NHDk2rLyYn4U+Qwv0g==$bTQmawkHls/4rXbCiprrlMGDq6hFQcxRjG8Oxyh/h3M=','ADMIN',1,1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM users WHERE username='admin');
INSERT INTO users(full_name, username, password_hash, role, is_active, must_change_password)
SELECT 'Senior Stylist','staff','pbkdf2$210000$hzBGKUSyhns4lZVBxWvPtg==$bD2uy7gvLC6enCNF3JTUAsLCWGOG69sqVX/748TS+5M=','STAFF',1,1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM users WHERE username='staff');
INSERT INTO users(full_name, username, password_hash, role, is_active, must_change_password)
SELECT 'Ayesha Khan','ayesha','pbkdf2$210000$ix4XW93uQ3xFMpxJNMuAQQ==$DT5Gq9yzZ199ZN7/FskWCK+46epK2vL1W4xgcWLTOzY=','STAFF',1,1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM users WHERE username='ayesha');
INSERT INTO users(full_name, username, password_hash, role, is_active, must_change_password)
SELECT 'Sara Ahmed','sara','pbkdf2$210000$GiLu/pHRzhV/65QphGAX6A==$ymejjWj14BuxDWyUGmpLc+s8hr9WpB9IIOFLYX3DLAU=','STAFF',1,1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM users WHERE username='sara');
