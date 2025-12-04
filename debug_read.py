import os

filepath = "app/src/main/assets/ime/dict/frequency_dictionary_en.txt"
print(f"Size: {os.path.getsize(filepath)}")
try:
    with open(filepath, 'r', encoding='utf-8') as f:
        lines = 0
        for i, line in enumerate(f):
            lines += 1
            if i < 5:
                print(f"Line {i}: {repr(line)}")
        print(f"Total lines: {lines}")
except Exception as e:
    print(f"Error: {e}")
