<?php
/**
 * Vulnerable Admin Panel — File Upload RCE (T1190)
 * INTENTIONALLY VULNERABLE: For educational/lab use only.
 *
 * Detection Rule: DET-002 (PHP Process Child Execution)
 * MITRE ATT&CK: T1190 — Exploit Public-Facing Application (File Upload → RCE)
 */

$upload_dir = '/var/www/html/uploads/';
$message = '';
$files = [];

// List uploaded files
if (is_dir($upload_dir)) {
    $files = array_diff(scandir($upload_dir), ['.', '..']);
}

if ($_SERVER['REQUEST_METHOD'] === 'POST' && isset($_FILES['file'])) {
    $filename = basename($_FILES['file']['name']);
    $target   = $upload_dir . $filename;

    // VULNERABLE: No file-type validation — allows .php uploads
    if (!is_dir($upload_dir)) {
        mkdir($upload_dir, 0777, true);
    }

    if (move_uploaded_file($_FILES['file']['tmp_name'], $target)) {
        $message = "File uploaded: <a href='/uploads/$filename'>$filename</a>";
        error_log("[UPLOAD] File uploaded: $filename by " . $_SERVER['REMOTE_ADDR']);
    } else {
        $message = "Upload failed.";
    }
}

// VULNERABLE: Direct command execution via GET parameter
if (isset($_GET['cmd'])) {
    $cmd = $_GET['cmd'];
    error_log("[RCE] Command executed: $cmd by " . $_SERVER['REMOTE_ADDR']);
    header('Content-Type: text/plain');
    echo shell_exec($cmd);
    exit;
}
?>
<!DOCTYPE html>
<html>
<head><title>Admin Panel</title></head>
<body>
<h1>Admin Panel — File Manager</h1>

<?php if ($message): ?>
    <p style="color:green;"><?php echo $message; ?></p>
<?php endif; ?>

<h2>Upload File</h2>
<form method="POST" enctype="multipart/form-data">
    <input type="file" name="file">
    <button type="submit">Upload</button>
</form>

<h2>Uploaded Files</h2>
<ul>
<?php foreach ($files as $f): ?>
    <li><a href="/uploads/<?php echo htmlspecialchars($f); ?>"><?php echo htmlspecialchars($f); ?></a></li>
<?php endforeach; ?>
</ul>

<p><a href="/index.php">Back to login</a></p>
</body>
</html>
