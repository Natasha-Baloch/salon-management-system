CREATE DATABASE IF NOT EXISTS salon_db;
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
);

CREATE TABLE IF NOT EXISTS payments (
    id INT PRIMARY KEY AUTO_INCREMENT,
    booking_id INT NOT NULL,
    client_id INT NOT NULL,
    amount DECIMAL(10,2) NOT NULL,
    method VARCHAR(30) NOT NULL,
    paid_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (booking_id) REFERENCES bookings(id),
    FOREIGN KEY (client_id) REFERENCES clients(id)
);

CREATE INDEX idx_clients_phone ON clients(phone);
CREATE INDEX idx_bookings_status ON bookings(status);
CREATE INDEX idx_bookings_client ON bookings(client_id);
CREATE INDEX idx_payments_paid_at ON payments(paid_at);

INSERT IGNORE INTO services(name, category, duration_minutes, price) VALUES
('Hair Cut', 'Hair', 45, 800),
('Hair Styling', 'Hair', 60, 1200),
('Hair Color', 'Hair', 120, 3500),
('Facial Glow', 'Skin', 60, 1800),
('Manicure', 'Nails', 45, 1000),
('Pedicure', 'Nails', 60, 1300),
('Party Ready', 'Special', 120, 4500),
('Wedding Ready', 'Special', 240, 10000),
('Bridal Package', 'Special', 300, 15000),
('Makeup', 'Makeup', 90, 4000),
('Spa Relax', 'Spa', 90, 2500);
SELECT * FROM payments;