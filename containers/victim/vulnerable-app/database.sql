-- Victim Database Initialization
-- INTENTIONALLY VULNERABLE: Weak passwords, sensitive data exposed

CREATE DATABASE IF NOT EXISTS victim_db;
USE victim_db;

-- Users table with weak/default credentials
CREATE TABLE IF NOT EXISTS users (
    id INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL,
    password VARCHAR(255) NOT NULL,
    email VARCHAR(100),
    role VARCHAR(20) DEFAULT 'user',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Insert default users with weak passwords (T1078 — Valid Accounts)
INSERT INTO users (username, password, email, role) VALUES
('admin',    'admin123',       'admin@minisoc.local',    'admin'),
('www-data', 'www-data',       'webmaster@minisoc.local','user'),
('operator', 'operator',       'ops@minisoc.local',      'user'),
('backup',   'backup2024',     'backup@minisoc.local',   'admin'),
('guest',    'guest',          'guest@minisoc.local',    'user');

-- Sensitive data table (exfiltration target)
CREATE TABLE IF NOT EXISTS sensitive_data (
    id INT AUTO_INCREMENT PRIMARY KEY,
    category VARCHAR(50),
    content TEXT,
    classification VARCHAR(20) DEFAULT 'CONFIDENTIAL'
);

INSERT INTO sensitive_data (category, content, classification) VALUES
('credentials', 'SSH root password: toor123!',                    'TOP_SECRET'),
('credentials', 'Database backup password: bkp_s3cur3_2024',     'TOP_SECRET'),
('network',     'Internal subnet: 10.0.50.0/24',                  'CONFIDENTIAL'),
('network',     'Firewall management IP: 10.0.50.1',              'CONFIDENTIAL'),
('api_keys',    'AWS Access Key: AKIAIOSFODNN7EXAMPLE',            'TOP_SECRET'),
('api_keys',    'AWS Secret Key: wJalrXUtnFEMI/K7MDENG/bPxRfiCY', 'TOP_SECRET'),
('config',      'VPN endpoint: vpn.minisoc.local:1194',           'RESTRICTED'),
('config',      'LDAP server: ldap://10.0.50.10:389',             'RESTRICTED');

-- Audit log table (for detection)
CREATE TABLE IF NOT EXISTS audit_log (
    id INT AUTO_INCREMENT PRIMARY KEY,
    event_type VARCHAR(50),
    details TEXT,
    source_ip VARCHAR(45),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
