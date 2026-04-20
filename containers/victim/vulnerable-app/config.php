<?php
/**
 * Database Configuration — Hardcoded Credentials (T1552)
 * INTENTIONALLY VULNERABLE: For educational/lab use only.
 *
 * Detection Rule: DET-related (Credential Exposure)
 * MITRE ATT&CK: T1552 — Unsecured Credentials
 */

$db_host = 'localhost';
$db_user = 'root';
$db_pass = 'rootpass';  // NOTE: actual MySQL uses skip-grant-tables (no auth)
$db_name = 'victim_db';

// API keys (intentionally exposed)
$api_key    = 'sk-live-4f3c2b1a0d9e8f7g6h5i4j3k2l1m0n';
$api_secret = 'secret_xK9mN3pQ7rS1tU5vW8xY2zA4bC6dE0f';

// Internal service endpoints
$internal_api   = 'http://localhost:8080/api/internal';
$backup_server  = '192.168.1.50';
$backup_ssh_key = '/root/.ssh/backup_key';

function getDbConnection() {
    global $db_host, $db_user, $db_pass, $db_name;
    $conn = new mysqli($db_host, $db_user, $db_pass, $db_name);
    if ($conn->connect_error) {
        die("DB connection failed");
    }
    return $conn;
}
?>
