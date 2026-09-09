import os

file_path = r"c:\xampp\htdocs\CURTISS\Curtiss-ERP\app\Views\rep-tracking\index.php"
if os.path.exists(file_path):
    with open(file_path, "r", encoding="utf-8", errors="ignore") as f:
        lines = f.readlines()
    for idx, line in enumerate(lines):
        if "onclick=" in line.lower() and "advanceroutestatus" in line.lower():
            print(f"Line {idx+1}: {line.strip()}")
        elif "onclick=" in line.lower() and "status" in line.lower():
            print(f"Line {idx+1}: {line.strip()}")
else:
    print("File not found")
