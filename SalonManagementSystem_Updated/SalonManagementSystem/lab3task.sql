CREATE DATABASE lab3task;

CREATE TABLE Task1_1NF (
    Item VARCHAR(50),
    Color VARCHAR(30),
    Price DECIMAL(10, 2),
    Tax DECIMAL(10, 2),
    PRIMARY KEY (Item, Color)
);
INSERT INTO Task1_1NF (Item, Color, Price, Tax) VALUES
('T-shirt', 'Red', 12.00, 0.60),
('T-shirt', 'Blue', 12.00, 0.60),
('Polo', 'Red', 12.00, 0.60),
('Polo', 'Yellow', 12.00, 0.60),
('Sweat Shirt', 'Blue', 25.00, 1.25),
('Sweat Shirt', 'Black', 25.00, 1.25);


-- ------------------------------------------------------------
-- 2NF (Second Normal Form)
-- Eliminating partial dependency: Price and Tax depend only on 'Item', not 'Color'.
-- Decoupled into Item details and Item-Color availability.
-- ------------------------------------------------------------
CREATE TABLE Task1_2NF_Item (
    Item VARCHAR(50) PRIMARY KEY,
    Price DECIMAL(10, 2),
    Tax DECIMAL(10, 2)
);

CREATE TABLE Task1_2NF_ItemColor (
    Item VARCHAR(50),
    Color VARCHAR(30),
    PRIMARY KEY (Item, Color),
    FOREIGN KEY (Item) REFERENCES Task1_2NF_Item(Item)
);

INSERT INTO Task1_2NF_Item (Item, Price, Tax) VALUES
('T-shirt', 12.00, 0.60),
('Polo', 12.00, 0.60),
('Sweat Shirt', 25.00, 1.25);

INSERT INTO Task1_2NF_ItemColor (Item, Color) VALUES
('T-shirt', 'Red'),
('T-shirt', 'Blue'),
('Polo', 'Red'),
('Polo', 'Yellow'),
('Sweat Shirt', 'Blue'),
('Sweat Shirt', 'Black');


-- ------------------------------------------------------------
-- 3NF (Third Normal Form)
-- Eliminating transitive dependency: Tax depends on Price (Price -> Tax).
-- ------------------------------------------------------------
CREATE TABLE Task1_3NF_Tax (
    Price DECIMAL(10, 2) PRIMARY KEY,
    Tax DECIMAL(10, 2)
);

CREATE TABLE Task1_3NF_Item (
    Item VARCHAR(50) PRIMARY KEY,
    Price DECIMAL(10, 2),
    FOREIGN KEY (Price) REFERENCES Task1_3NF_Tax(Price)
);

CREATE TABLE Task1_3NF_ItemColor (
    Item VARCHAR(50),
    Color VARCHAR(30),
    PRIMARY KEY (Item, Color),
    FOREIGN KEY (Item) REFERENCES Task1_3NF_Item(Item)
);

INSERT INTO Task1_3NF_Tax (Price, Tax) VALUES
(12.00, 0.60),
(25.00, 1.25);

INSERT INTO Task1_3NF_Item (Item, Price) VALUES
('T-shirt', 12.00),
('Polo', 12.00),
('Sweat Shirt', 25.00);

INSERT INTO Task1_3NF_ItemColor (Item, Color) VALUES
('T-shirt', 'Red'),
('T-shirt', 'Blue'),
('Polo', 'Red'),
('Polo', 'Yellow'),
('Sweat Shirt', 'Blue'),
('Sweat Shirt', 'Black');


-- ============================================================
-- TASK 2: Normalization (1NF, 2NF, 3NF)
-- Original Attributes: Supplier, Name, City_ID, City, Part, Quantity
-- Dependencies: Supplier -> Name, Supplier -> City_ID, City_ID -> City, (Supplier, Part) -> Quantity
-- ============================================================

-- ------------------------------------------------------------
-- 1NF (First Normal Form)
-- All values are already atomic in the source dataset.
-- Primary Key: (Supplier, Part)
-- ------------------------------------------------------------
CREATE TABLE Task2_1NF (
    Supplier VARCHAR(10),
    Name VARCHAR(50),
    City_ID INT,
    City VARCHAR(50),
    Part VARCHAR(10),
    Quantity INT,
    PRIMARY KEY (Supplier, Part)
);

INSERT INTO Task2_1NF (Supplier, Name, City_ID, City, Part, Quantity) VALUES
('S1', 'Ali', 10, 'Paris', 'P3', 257),
('S1', 'Ali', 10, 'Paris', 'P1', 500),
('S1', 'Ali', 10, 'Paris', 'P4', 125),
('S2', 'James', 12, 'London', 'P3', 200),
('S7', 'Robert', 10, 'Paris', 'P4', 342);


-- ------------------------------------------------------------
-- 2NF (Second Normal Form)
-- Eliminating partial dependency: Name, City_ID, and City depend only on 'Supplier', not 'Part'.
-- ------------------------------------------------------------
CREATE TABLE Task2_2NF_Supplier (
    Supplier VARCHAR(10) PRIMARY KEY,
    Name VARCHAR(50),
    City_ID INT,
    City VARCHAR(50)
);

CREATE TABLE Task2_2NF_Shipment (
    Supplier VARCHAR(10),
    Part VARCHAR(10),
    Quantity INT,
    PRIMARY KEY (Supplier, Part),
    FOREIGN KEY (Supplier) REFERENCES Task2_2NF_Supplier(Supplier)
);

INSERT INTO Task2_2NF_Supplier (Supplier, Name, City_ID, City) VALUES
('S1', 'Ali', 10, 'Paris'),
('S2', 'James', 12, 'London'),
('S7', 'Robert', 10, 'Paris');

INSERT INTO Task2_2NF_Shipment (Supplier, Part, Quantity) VALUES
('S1', 'P3', 257),
('S1', 'P1', 500),
('S1', 'P4', 125),
('S2', 'P3', 200),
('S7', 'P4', 342);


-- ------------------------------------------------------------
-- 3NF (Third Normal Form)
-- Eliminating transitive dependency: City depends on City_ID (City_ID -> City).
-- ------------------------------------------------------------
CREATE TABLE Task2_3NF_City (
    City_ID INT PRIMARY KEY,
    City VARCHAR(50)
);

CREATE TABLE Task2_3NF_Supplier (
    Supplier VARCHAR(10) PRIMARY KEY,
    Name VARCHAR(50),
    City_ID INT,
    FOREIGN KEY (City_ID) REFERENCES Task2_3NF_City(City_ID)
);

CREATE TABLE Task2_3NF_Shipment (
    Supplier VARCHAR(10),
    Part VARCHAR(10),
    Quantity INT,
    PRIMARY KEY (Supplier, Part),
    FOREIGN KEY (Supplier) REFERENCES Task2_3NF_Supplier(Supplier)
);

INSERT INTO Task2_3NF_City (City_ID, City) VALUES
(10, 'Paris'),
(12, 'London');

INSERT INTO Task2_3NF_Supplier (Supplier, Name, City_ID) VALUES
('S1', 'Ali', 10),
('S2', 'James', 12),
('S7', 'Robert', 10);

INSERT INTO Task2_3NF_Shipment (Supplier, Part, Quantity) VALUES
('S1', 'P3', 257),
('S1', 'P1', 500),
('S1', 'P4', 125),
('S2', 'P3', 200),
('S7', 'P4', 342);