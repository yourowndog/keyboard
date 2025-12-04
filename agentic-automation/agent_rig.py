import os
import subprocess
import sys
import datetime
import google.generativeai as genai
from google.generativeai.types import HarmCategory, HarmBlockThreshold

# --- CONFIGURATION ---
# 1. Get key from environment
api_key = os.getenv("GEMINI_API_KEY")
if not api_key:
    print("❌ ERROR: GEMINI_API_KEY not found. Please run: export GEMINI_API_KEY='your_key'")
    sys.exit(1)

# 2. Configure the API
genai.configure(api_key=api_key)

# --- THE FLIGHT RECORDER (LOGGING) ---
def log_event(text):
    """Saves the drama to a text file so you can read it later."""
    timestamp = datetime.datetime.now().strftime("%H:%M:%S")
    formatted_line = f"[{timestamp}] {text}\n"
    # Print to screen
    # (We don't print here because the main loop handles screen output, 
    # this is just for the permanent file record)
    with open("mission_log.txt", "a", encoding="utf-8") as f:
        f.write(formatted_line)

# --- THE BRAIN (MAXIMUM INSTRUCTION) ---
SYSTEM_INSTRUCTION = """
### IDENTITY & MISSION
You are the **GEMINI 3.0 BRAIN**, the Chief Auditor and Intelligence Engine for the OmniBoard Project.
You are paired with a "Coding Agent" (Gemini Flash CLI) known as "THE HANDS."
You are running inside an **AUTONOMOUS PYTHON RUNTIME** on the User's local machine (Termux).

### YOUR CONTEXT
* **The Project:** OmniBoard (Android Keyboard with 'snygg' theming).
* **The User:** The Commander. They provide the "Mission Manifest." You report to them.
* **The Hands:** Fast but dumb. They read files and write code. They make mistakes. You must watch them like a hawk.

### YOUR PROTOCOL (THE LOOP)
1.  **ANALYZE:** Read the Mission Manifest. Formulate a plan.
2.  **INVESTIGATE:** You are BLIND. You cannot see files unless you order the Hands to `cat` them.
    * *Command:* "HANDS: cat app/src/main/kotlin/LayoutManager.kt"
3.  **REASON:** Use your "High Thinking" capacity. Look for legacy code, 'snygg' violations, or logic bugs.
4.  **EXECUTE:** Order the Hands to apply fixes using `sed`, `printf`, or the `codebase_analyzer`.
    * *Command:* "HANDS: printf 'val newKey = ...' > temp_fix.kt"
5.  **VERIFY:** Never trust the Hands. After a fix, order them to `cat` the file again to prove it changed.

### COMMAND SYNTAX (STRICT)
To control the runtime, you must start a new line with exactly:
`HANDS: <your_command_here>`

### TERMINATION
Only when the Mission is 100% complete and verified, output:
`### FINAL REPORT`
(Followed by a summary of what you changed).
"""

# 3. Initialize the Brain (Gemini 3.0 Pro with Deep Thinking)
model = genai.GenerativeModel(
    model_name="gemini-3-pro-preview", #
    system_instruction=SYSTEM_INSTRUCTION,
    safety_settings={HarmCategory.HARM_CATEGORY_HATE_SPEECH: HarmBlockThreshold.BLOCK_NONE},
    generation_config=genai.types.GenerationConfig(
        thinking_level="high", # Enables Chain-of-Thought reasoning
        temperature=0.7
    )
)

chat = model.start_chat(history=[])

# --- THE HANDS (SUBPROCESS) ---
def call_hands(command_text):
    print(f"\n⚡ [HANDS] Running: {command_text}")
    log_event(f"[HANDS REQUEST] {command_text}")
    
    try:
        # Using 'flash' model for the heavy lifting (cost efficiency)
        result = subprocess.run(
            f"gemini chat --model flash '{command_text}'",
            shell=True,
            capture_output=True,
            text=True,
            timeout=120
        )
        output = (result.stdout + result.stderr).strip()
        if not output:
            output = "[HANDS]: Task completed (No output returned)."
        
        return output
    except Exception as e:
        return f"[SYSTEM ERROR]: {str(e)}"

# --- THE AUTONOMOUS LOOP ---
def main():
    print("--- OMNIBOARD AGENT RIG (GEMINI 3.0 POWERED) ---")
    print("--- Type 'Ctrl+C' to emergency stop ---")
    
    mission = input("📋 PASTE MISSION MANIFEST:\n")
    if not mission: return

    # Start the log
    log_event(f"--- NEW SESSION: {mission} ---")
    
    # Seed the conversation
    next_message = f"MISSION START: {mission}"
    
    while True:
        print("\n🧠 [BRAIN] Thinking (Gemini 3.0)...")
        try:
            # 1. Send context to Brain
            response = chat.send_message(next_message)
            brain_text = response.text
            
            print(f"🗣️ [BRAIN SAYS]:\n{brain_text}\n")
            log_event(f"[BRAIN] {brain_text}")
            
            # 2. Check for Completion
            if "### FINAL REPORT" in brain_text:
                print("🛑 [MISSION COMPLETE] The Brain is awaiting approval.")
                if input("👉 Type 'Y' to save & exit, or Enter to debate: ").lower() == 'y':
                    log_event("[STATUS] Mission Approved by User.")
                    break
                else:
                    next_message = input("⌨️ [YOU]: ")
                    log_event(f"[USER INTERVENTION] {next_message}")
                    continue

            # 3. PARSE & EXECUTE (The "Hands" Loop)
            lines = brain_text.split('\n')
            executed = False
            
            for line in lines:
                if line.strip().startswith("HANDS:"):
                    # Extract command
                    cmd = line.split("HANDS:", 1)[1].strip()
                    
                    # RUN IT
                    hands_output = call_hands(cmd)
                    print(f"📄 [RESULT]:\n{hands_output[:500]}... (truncated)")
                    log_event(f"[HANDS OUTPUT] {hands_output}")
                    
                    # FEEDBACK LOOP
                    next_message = f"HANDS OUTPUT:\n{hands_output}"
                    executed = True
                    break 
            
            if not executed:
                # If Brain didn't command, it's talking to you.
                print("⚠️ [SYSTEM] Brain is waiting for input.")
                next_message = input("⌨️ [YOU]: ")
                log_event(f"[USER INPUT] {next_message}")

        except KeyboardInterrupt:
            print("\n🛑 MANUAL OVERRIDE.")
            break
        except Exception as e:
            print(f"❌ CRITICAL ERROR: {e}")
            log_event(f"[ERROR] {e}")
            break

if __name__ == "__main__":
    main()
