import os

file_path = r"c:\xampp\htdocs\CURTISS\Curtiss-ERP\app\Views\rep-tracking\index.php"
if os.path.exists(file_path):
    with open(file_path, "r", encoding="utf-8", errors="ignore") as f:
        lines = f.readlines()
    found = False
    for idx, line in enumerate(lines):
        if "id=\"tabpanel-3\"" in line.lower() or "id='tabpanel-3'" in line.lower():
            print(f"Line {idx+1}: {line.strip()}")
            start = idx
            end = min(idx + 100, len(lines))
            for i in range(start, end):
                print(f"  {i+1}: {lines[i].rstrip()}")
            break
else:
    print("File not found")
