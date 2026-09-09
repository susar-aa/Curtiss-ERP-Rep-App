import os

file_path = r"c:\xampp\htdocs\CURTISS\Curtiss-ERP\app\Views\rep-tracking\index.php"
if os.path.exists(file_path):
    with open(file_path, "r", encoding="utf-8", errors="ignore") as f:
        lines = f.readlines()
    for idx, line in enumerate(lines):
        if "data-sales=" in line.lower() or "route_data_" in line.lower():
            print(f"Line {idx+1}: {line.strip()}")
            start = max(0, idx - 5)
            end = min(idx + 10, len(lines))
            for i in range(start, end):
                print(f"  {i+1}: {lines[i].rstrip()}")
            break
else:
    print("File not found")
