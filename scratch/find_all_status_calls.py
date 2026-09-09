import os

root_dir = r"c:\xampp\htdocs\CURTISS\Curtiss-ERP\app"
for root, dirs, files in os.walk(root_dir):
    for file in files:
        if file.endswith(".php"):
            path = os.path.join(root, file)
            with open(path, "r", encoding="utf-8", errors="ignore") as f:
                content = f.read()
            if "api_update_route_status" in content.lower():
                print(f"File: {path}")
