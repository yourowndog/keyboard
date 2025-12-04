import os
import sys
import time
import subprocess

# --- DEPENDENCY CHECK ---
try:
    import pexpect
    import google.generativeai as genai
    from colorama import Fore, Style, init
except ImportError:
    print("Missing dependencies! Run: pip install pexpect google-generativeai colorama")
    sys.exit(1)

# --- CONFIGURATION ---
BRAIN_MODEL_NAME = "gemini-3-pro-preview" 
HANDS_MODEL_NAME = "gemini-2.5-flash"
CLI_COMMAND = f"gemini chat --model {HANDS_MODEL_NAME}"

init(autoreset=True)

# --- AUTHENTICATION ---
print(f"{Fore.YELLOW}>>> CHECKING CREDENTIALS...")
PAID_KEY = os.environ.get("GEMINI_PAID_API_KEY")
FREE_KEY = os.environ.get("GEMINI_FREE_API_KEY")

# Failsafe: Ask user if keys are missing
if not PAID_KEY:
    PAID_KEY = input(f"{Fore.YELLOW}Paste PAID Key: ").strip()
if not FREE_KEY:
    FREE_KEY = input(f"{Fore.YELLOW}Paste FREE Key: ").strip()

if not PAID_KEY or not FREE_KEY:
    print(f"{Fore.RED}CRITICAL: Keys missing. Aborting.")
    sys.exit(1)

genai.configure(api_key=PAID_KEY)

def start_rig():
    print(f"\n{Fore.YELLOW}{Style.BRIGHT}>>> BOOTING HYBRID RIG v9.1 (Unmuted) <<<")
    print(f"{Fore.YELLOW}Brain: {BRAIN_MODEL_NAME}")
    print(f"{Fore.YELLOW}Hands: {HANDS_MODEL_NAME}")
    
    # --- 0. MISSION INPUT ---
    print(f"\n{Fore.WHITE}What is the objective for this session?")
    mission = input(f"{Fore.GREEN}>> MISSION: {Fore.RESET}")
    
    if not mission.strip():
        print("No mission provided. Aborting.")
        return

    print(f"\n{Fore.YELLOW}Initializing Brain with Mission: {mission}...")

    # --- 1. SETUP THE BRAIN ---
    try:
        brain = genai.GenerativeModel(
            model_name=BRAIN_MODEL_NAME,
            system_instruction=(
                f"You are the MASTERMIND. You control a CLI agent called 'HANDS'.\n"
                f"CURRENT MISSION: {mission}\n"
                "Your goal: Complete the mission by issuing shell commands to HANDS.\n"
                "INPUT: You will receive the last output from the HANDS terminal.\n"
                "OUTPUT: You must output ONLY the raw command you want typed into the terminal.\n"
                "RULES:\n"
                "1. If you need to think, put it in a comment like `# Thinking: checking directory...`\n"
                "2. NO markdown blocks (no ```bash). Just the command.\n"
                "3. If you want to write a file, use: `cat <<EOF > filename.py` ... `EOF`"
            )
        )
        chat_session_brain = brain.start_chat(history=[])
    except Exception as e:
        print(f"{Fore.RED}Brain Init Failed: {e}")
        return

    # --- 2. SPAWN THE HANDS (PEXPECT) ---
    print(f"{Fore.YELLOW}Spawning Hands (CLI)...")
    cli_env = os.environ.copy()
    cli_env["GEMINI_API_KEY"] = FREE_KEY
    
    try:
        hands = pexpect.spawn(CLI_COMMAND, env=cli_env, encoding='utf-8', timeout=300)
        hands.logfile_read = sys.stdout.buffer # Ghost Mode
        hands.expect(['>', 'Type a message', '\u279c', r'\$'])
        print(f"\n{Fore.GREEN}>> HANDS ONLINE.")

    except Exception as e:
        print(f"{Fore.RED}Spawn Error: {e}")
        return

    # --- 3. THE LOOP ---
    cli_output = "Session Started. Ready for orders."
    
    while True:
        try:
            # A. BRAIN THINKS
            prompt = (
                f"MISSION: {mission}\n"
                f"LAST OUTPUT FROM HANDS:\n{cli_output}\n\n"
                "YOUR COMMAND (Raw text only):"
            )
            
            print(f"\n{Fore.CYAN}{Style.BRIGHT}[BRAIN] ", end="", flush=True)
            
            full_brain_response = ""
            response_stream = chat_session_brain.send_message(prompt, stream=True)
            
            # --- THE FIX: We print the stream as it comes in ---
            for chunk in response_stream:
                if chunk.text:
                    print(Fore.CYAN + chunk.text, end="", flush=True) # <--- VISIBILITY RESTORED
                    full_brain_response += chunk.text
            
            # Add a newline after the brain finishes thinking
            print()

            # Clean up response 
            cmd_to_send = full_brain_response.replace("```bash", "").replace("```", "").strip()

            if not cmd_to_send:
                print(f"{Fore.YELLOW}... (Brain sent empty command)")
                time.sleep(1)
                continue

            # B. HANDS ACT
            hands.sendline(cmd_to_send)
            
            try:
                hands.expect(['>', 'Type a message', '\u279c', r'\$'], timeout=120)
            except pexpect.TIMEOUT:
                print(f"{Fore.RED}\n[System]: Timeout. Passing buffer to Brain.")
            
            cli_output = hands.before

        except KeyboardInterrupt:
            print(f"\n{Fore.RED}Rig Shutdown.")
            hands.close()
            sys.exit()
        except Exception as e:
            print(f"{Fore.RED}Loop Error: {e}")
            time.sleep(2)

if __name__ == "__main__":
    start_rig()