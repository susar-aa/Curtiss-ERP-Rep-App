import os

file_path = r"c:\xampp\htdocs\CURTISS\Curtiss-ERP\app\Views\rep-tracking\index.php"
if os.path.exists(file_path):
    with open(file_path, "r", encoding="utf-8", errors="ignore") as f:
        lines = f.readlines()
    for idx, line in enumerate(lines):
        line_lower = line.lower()
        if "advanceroutestatus" in line_lower:
            print(f"Line {idx+1}: {line.strip()}")
        elif "api_update_route_status" in line_lower:
            print(f"Line {idx+1}: {line.strip()}")
else:
    print("File not found")
