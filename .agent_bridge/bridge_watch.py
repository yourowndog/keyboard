import time
import os
import subprocess
try:
    from watchdog.observers import Observer
    from watchdog.events import FileSystemEventHandler
except ImportError:
    print("Error: watchdog package not found. Please run 'pip install watchdog'.")
    exit(1)

class BridgeHandler(FileSystemEventHandler):
    def on_modified(self, event):
        # We only care about markdown files changing in the bridge directory
        if event.src_path.endswith(".md") and not event.is_directory:
            filename = os.path.basename(event.src_path)
            # Prevent triggering on temporary editor files or hidden files
            if not filename.startswith("."):
                print(f"
[!] ALERT: Handover or consultation detected from Agent in {filename}")
                try:
                    # Trigger an Android notification via Termux API
                    subprocess.run(['termux-notification', '--content', f'Agent Bridge Activity: {filename}'], check=False)
                except FileNotFoundError:
                    # Termux API not installed or accessible
                    print("  (termux-notification not found, skipping Android push notification)")

if __name__ == "__main__":
    path = os.path.dirname(os.path.abspath(__file__))
    print(f"Starting Agent Bridge Watcher on {path}...")
    event_handler = BridgeHandler()
    observer = Observer()
    observer.schedule(event_handler, path, recursive=False)
    observer.start()
    try:
        while True:
            time.sleep(1)
    except KeyboardInterrupt:
        print("
Stopping watcher...")
        observer.stop()
    observer.join()
