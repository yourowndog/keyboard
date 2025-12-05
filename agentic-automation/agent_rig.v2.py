import os
import subprocess
import sys
import datetime
from google import genai
from google.genai import types

# --- CONFIGURATION ---
api_key = os.getenv("GEMINI_API_KEY")
if not api_key:
    print("❌ ERROR: GEMINI_API_KEY not found. Please export it first.")
    sys.exit(1)

# Initialize the NEW Client (v1.0+)
client = genai.Client(api_key=api_key)

# --- THE FLIGHT RECORDER ---
def log_event(text):
    timestamp = datetime.datetime.now().strftime("%H:%M:%S")
    with open("mission_log.txt", "a", encoding="utf-8") as f:
        f.write(f"[{timestamp}] {text}\n")

# --- THE BRAIN (SYSTEM PROMPT) ---
SYSTEM_INSTRUCTION = """
### IDENTITY: GEMINI 3.0 AUDITOR
You are the BRAIN. You control the HANDS (Gemini CLI).
You are running in an AUTONOMOUS LOOP.

PROTOCOL:
1. READ the Mission.
2. THINK deeply (Chain of Thought).
3. COMMAND the hands to read files: `HANDS: cat filename`
4. COMMAND the hands to fix code.
5. VERIFY every fix.

TRIGGER:
To run a command, start a line with:
HANDS: <command>

TERMINATION:
When finished, output:
### FINAL REPORT
"""

# --- START CHAT (GEMINI 3.0 CONFIG) ---
# We use the new 'types' structure for Thinking Config
try:
    chat = client.chats.create(
        model="gemini-3-pro-preview", #
        config=types.GenerateContentConfig(
            system_instruction=SYSTEM_INSTRUCTION,
            temperature=0.7,
            thinking_config=types.ThinkingConfig(
                include_thoughts=True, # Logs the hidden reasoning!
                thinking_level="HIGH"  # Forces deep reasoning
            )
        )
    )
except Exception as e:
    print(f"❌ INIT ERROR: {e}")
    sys.exit(1)

# --- THE HANDS ---
def call_hands(command_text):
    print(f"\n⚡ [HANDS] Running: {command_text}")
    log_event(f"[HANDS REQUEST] {command_text}")
    try:
        # We still use the Flash model for the grunt work
        result = subprocess.run(
            f"gemini chat --model flash '{command_text}'",
            shell=True,
            capture_output=True,
            text=True,
            timeout=120
        )
        output = (result.stdout + result.stderr).strip()
        if not output: output = "[DONE]"
        return output
    except Exception as e:
        return f"[SYSTEM ERROR] {e}"

# --- THE LOOP ---
def main():
    print("--- OMNIBOARD RIG (GEMINI 3.0 NEW SDK) ---")
    mission = input("📋 PASTE MISSION MANIFEST:\n")
    if not mission: return

    log_event(f"--- NEW SESSION: {mission} ---")
    next_message = f"MISSION START: {mission}"

    while True:
        print("\n🧠 [BRAIN] Thinking...")
        try:
            # Send message using the new client structure
            response = chat.send_message(next_message)
            brain_text = response.text
            
            # Print the text (The "Thoughts" are hidden in metadata, 
            # but the response contains the final decision)
            print(f"🗣️ [BRAIN SAYS]:\n{brain_text}\n")
            log_event(f"[BRAIN] {brain_text}")

            if "### FINAL REPORT" in brain_text:
                if input("👉 Approve? (Y/N): ").lower() == 'y': break
                next_message = input("⌨️ [YOU]: ")
                continue

            # Parse Commands
            executed = False
            for line in brain_text.split('\n'):
                if line.strip().startswith("HANDS:"):
                    cmd = line.split("HANDS:", 1)[1].strip()
                    hands_output = call_hands(cmd)
                    print(f"📄 [RESULT]: {hands_output[:200]}...")
                    log_event(f"[HANDS] {hands_output}")
                    next_message = f"HANDS OUTPUT:\n{hands_output}"
                    executed = True
                    break
            
            if not executed:
                print("⚠️ Brain waiting...")
                next_message = input("⌨️ [YOU]: ")

        except KeyboardInterrupt:
            break
        except Exception as e:
            print(f"❌ ERROR: {e}")
            break

if __name__ == "__main__":
    main()
