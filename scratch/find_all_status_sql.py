import os

root_dir = r"c:\xampp\htdocs\CURTISS\Curtiss-ERP\app"
for root, dirs, files in os.walk(root_dir):
    for file in files:
        if file.endswith(".php"):
            path = os.path.join(root, file)
            with open(path, "r", encoding="utf-8", errors="ignore") as f:
                lines = f.readlines()
            for idx, line in enumerate(lines):
                if "rep_daily_routes" in line.lower() and "set status =" in line.lower():
                    print(f"File: {path}, Line {idx+1}: {line.strip()}")
                elif "rep_daily_routes" in line.lower() and "status = :" in line.lower():
                    print(f"File: {path}, Line {idx+1}: {line.strip()}")
