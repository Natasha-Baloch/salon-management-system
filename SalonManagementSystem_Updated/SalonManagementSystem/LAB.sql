CREATE DATABASE DeNormalization;
USE DeNormalization;
-- Denormalized Task Table (integrates task_outcome directly)
CREATE TABLE denormalized_task (
    task_id INT PRIMARY KEY,
    client_id INT NOT NULL,
    start_time TIMESTAMP NOT NULL,
    end_time TIMESTAMP,
    user_assigned INT,
    
    -- Embedded Task Outcome details
    task_outcome_text VARCHAR(256),
    task_completed_successfully BOOLEAN
);

-- Denormalized Call Table (integrates call_outcome directly)
CREATE TABLE denormalized_call (
    call_id INT PRIMARY KEY,
    user_account_id INT NOT NULL,
    task_id INT NOT NULL,
    start_time TIMESTAMP NOT NULL,
    end_time TIMESTAMP,
    
    -- Embedded Call Outcome details
    call_outcome_text VARCHAR(256),
    call_completed_successfully BOOLEAN
);

-- Denormalized Meeting Table (integrates meeting_outcome directly)
CREATE TABLE denormalized_meeting (
    meeting_id INT PRIMARY KEY,
    user_account_id INT NOT NULL,
    task_id INT NOT NULL,
    start_time TIMESTAMP NOT NULL,
    end_time TIMESTAMP,
    
    -- Embedded Meeting Outcome details
    meeting_outcome_text VARCHAR(256),
    meeting_completed_successfully BOOLEAN
);

-- Denormalized Sales & Offering Data (combining product details into transactional records)
CREATE TABLE denormalized_product_activity (
    activity_id INT PRIMARY KEY,
    activity_type VARCHAR(50), -- 'SOLD' or 'OFFERED'
    task_id INT NOT NULL,
    product_id INT NOT NULL,
    product_name VARCHAR(256),
    unit VARCHAR(64),
    price_per_unit DECIMAL(8,2),
    units INT NOT NULL,
    total_amount DECIMAL(10,2) -- Pre-calculated derived field
);