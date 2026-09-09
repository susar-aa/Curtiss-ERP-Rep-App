import os

file_path = r"c:\xampp\htdocs\CURTISS\Curtiss-ERP\app\Views\rep-tracking\index.php"
if os.path.exists(file_path):
    with open(file_path, "r", encoding="utf-8", errors="ignore") as f:
        content = f.read()
    # Find all occurrences of advanceRouteStatus
    start = 0
    while True:
        pos = content.lower().find("advanceroutestatus", start)
        if pos == -1:
            break
        print(f"Found match at position {pos}")
        # print around it
        start_pos = max(0, pos - 100)
        end_pos = min(len(content), pos + 100)
        print(f"Context: {content[start_pos:end_pos]}")
        start = pos + len("advanceroutestatus")
else:
    print("File not found")
