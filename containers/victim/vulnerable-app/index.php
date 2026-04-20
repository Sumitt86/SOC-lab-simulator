<?php
/**
 * Vulnerable Login Page — SQL Injection (T1190)
 * INTENTIONALLY VULNERABLE: For educational/lab use only.
 * 
 * Detection Rule: DET-001 (SQL Injection Pattern)
 * MITRE ATT&CK: T1190 — Exploit Public-Facing Application
 */

$db_host = '127.0.0.1';  // Use TCP, not unix socket
$db_user = 'root';
$db_pass = '';  // VULNERABLE: skip-grant-tables, no password needed
$db_name = 'victim_db';

$conn = @new mysqli($db_host, $db_user, $db_pass, $db_name, 3306);
if ($conn->connect_error) {
    die("DB connection failed: " . $conn->connect_error);
}

$message = '';
$authenticated = false;
$user_data = null;

if (isset($_GET['username']) || isset($_POST['username'])) {
    $username = isset($_GET['username']) ? $_GET['username'] : $_POST['username'];
    $password = isset($_GET['password']) ? $_GET['password'] : (isset($_POST['password']) ? $_POST['password'] : '');

    // VULNERABLE: Direct string interpolation — no prepared statements
    $query = "SELECT * FROM users WHERE username='$username' AND password='$password'";

    $result = $conn->query($query);
    if ($result && $result->num_rows > 0) {
        $authenticated = true;
        $user_data = $result->fetch_assoc();
        $message = "Welcome, " . $user_data['username'] . "!";

        // Log successful auth (will be picked up by monitoring)
        error_log("[AUTH] Successful login for user: $username from " . $_SERVER['REMOTE_ADDR']);
    } else {
        $message = "Invalid credentials.";
        error_log("[AUTH] Failed login attempt for user: $username from " . $_SERVER['REMOTE_ADDR']);
    }
}
?>
<!DOCTYPE html>
<html>
<head><title>Internal Portal — Login</title></head>
<body>
<h1>Internal Portal</h1>
<?php if ($authenticated): ?>
    <p style="color:green;"><?php echo $message; ?></p>
    <p>Role: <?php echo htmlspecialchars($user_data['role']); ?></p>
    <p>Email: <?php echo htmlspecialchars($user_data['email']); ?></p>
    <?php if ($user_data['role'] === 'admin'): ?>
        <p><a href="/admin.php">Go to Admin Panel</a></p>
    <?php endif; ?>
<?php else: ?>
    <?php if ($message): ?>
        <p style="color:red;"><?php echo $message; ?></p>
    <?php endif; ?>
    <form method="POST" action="/index.php">
        <label>Username: <input type="text" name="username"></label><br><br>
        <label>Password: <input type="password" name="password"></label><br><br>
        <button type="submit">Login</button>
    </form>
<?php endif; ?>
</body>
</html>
<?php $conn->close(); ?>
