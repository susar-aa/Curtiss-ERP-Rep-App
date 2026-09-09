import os

file_path = r"c:\xampp\htdocs\CURTISS\Curtiss-ERP\app\Controllers\RepDashboardController.php"
if os.path.exists(file_path):
    with open(file_path, "r", encoding="utf-8", errors="ignore") as f:
        lines = f.readlines()
    start = 580
    for idx in range(start, min(start + 50, len(lines))):
        print(f"{idx+1}: {lines[idx].rstrip()}")
else:
    print("File not found")
