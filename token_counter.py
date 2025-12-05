
import os

def is_binary(filepath):
    """Check if a file is binary by trying to read it as text."""
    try:
        with open(filepath, 'r', encoding='utf-8') as f:
            f.read(1024)  # Read a chunk to test
        return False
    except (UnicodeDecodeError, PermissionError):
        return True

def count_tokens():
    """
    Recursively scans the current directory, counts characters in specified
    code files, and estimates the token count.
    """
    total_files = 0
    total_chars = 0
    
    ignored_dirs = {'.git', 'node_modules', 'build', '.gradle', 'schemas', 'assets'}
    allowed_extensions = {'.kt', '.kts', '.xml', '.py', '.java', '.gradle'}

    for root, dirs, files in os.walk('.'):
        # Filter out ignored directories
        dirs[:] = [d for d in dirs if d not in ignored_dirs]
        
        for filename in files:
            if any(filename.endswith(ext) for ext in allowed_extensions):
                filepath = os.path.join(root, filename)
                
                if is_binary(filepath):
                    continue
                
                try:
                    with open(filepath, 'r', encoding='utf-8') as f:
                        content = f.read()
                        total_chars += len(content)
                        total_files += 1
                except Exception:
                    # Ignore files that can't be read
                    pass

    approx_tokens = total_chars // 4
    
    print(f"Total Files: {total_files}, Approx Tokens: {approx_tokens}")

if __name__ == "__main__":
    count_tokens()
