import os
import sys
import time
import subprocess

# --- DEPENDENCY CHECK ---
try:
    import pexpect
    import google.generativeai as genai
    from colorama import Fore, Style, init
except ImportError as e:
    print("Missing dependencies! Run: pip install pexpect google-generativeai colorama")
    sys.exit(1)

# --- CONFIGURATION ---

# 1. THE BRAIN (Thinking Model)
# We use the new 3.0 Preview for reasoning.
BRAIN_MODEL_NAME = "gemini-3.0-pro-preview" # Check that this matches your API availability, or use 'gemini-1.5-pro-latest'

# 2. THE HANDS (Execution Model)
# CRITICAL FIX: Reverted to 1.5 Flash because 2.5 does not exist.
HANDS_MODEL_NAME = "gemini-1.5-flash" 
# NOTE: If this fails, change CLI_COMMAND to just "gemini chat" to use your default.
CLI_COMMAND = f"gemini chat --model {HANDS_MODEL_NAME}"

# 3. AUTH
API_KEY = os.environ.get("GEMINI_API_KEY")
if not API_KEY:
    print(f"{Fore.RED}ERROR: GEMINI_API_KEY not found. Export it in .bashrc first.")
    sys.exit(1)

# ---------------------

init(autoreset=True)
genai.configure(api_key=API_KEY)

def start_rig():
    print(f"{Fore.YELLOW}{Style.BRIGHT}>>> BOOTING HYBRID RIG v3.1 <<<")
    print(f"{Fore.YELLOW}Brain: {BRAIN_MODEL_NAME}")
    print(f"{Fore.YELLOW}Hands: {HANDS_MODEL_NAME} (via CLI)")

    # --- 1. SETUP THE BRAIN ---
    print(f"{Fore.YELLOW}Initializing Brain...")
    try:
        brain = genai.GenerativeModel(
            model_name=BRAIN_MODEL_NAME,
            system_instruction=(
                "You are the MASTERMIND. You are running on an Android Galaxy S25 Ultra in Termux.\n"
                "You have a subordinate agent called 'HANDS' (a Gemini CLI instance).\n"
                "Your job is to plan coding tasks and issue text commands to HANDS.\n"
                "HANDS will report back with output. Analyze it and give the next instruction.\n"
                "To give a command, output ONLY the text you want typed into the CLI.\n"
                "If you need to wait or think, just output the reasoning."
            )
        )
        chat_session_brain = brain.start_chat(history=[])
    except Exception as e:
        print(f"{Fore.RED}Brain Init Failed: {e}")
        return

    # --- 2. SPAWN THE HANDS (PEXPECT) ---
    print(f"{Fore.YELLOW}Spawning Hands (CLI)...")
    try:
        # Spawn the CLI
        hands = pexpect.spawn(CLI_COMMAND, encoding='utf-8', timeout=120)
        
        # We look for a variety of prompts. 
        # \u279c is the '➜' arrow often used in node CLIs.
        # r'\$' is a dollar sign.
        # '>' is standard.
        index = hands.expect(['>', r'\$', 'Type a message', '\u279c', pexpect.EOF])
        
        if index == 4: # EOF detected immediately
            print(f"{Fore.RED}CRITICAL: CLI exited immediately.")
            print(f"Output before death:\n{hands.before}")
            return
            
        print(f"{Fore.GREEN}>> HANDS ONLINE.")

    except Exception as e:
        print(f"{Fore.RED}Spawn Error: {e}")
        return

    # --- 3. THE LOOP ---
    cli_output = "Session Started. I am ready."
    
    while True:
        try:
            # A. BRAIN THINKS
            prompt = f"REPORT FROM HANDS:\n{cli_output}\n\nINSTRUCTION:"
            
            print(f"{Fore.CYAN}{Style.BRIGHT}\n[BRAIN]: ", end="", flush=True)
            
            full_brain_response = ""
            response_stream = chat_session_brain.send_message(prompt, stream=True)
            
            for chunk in response_stream:
                print(Fore.CYAN + chunk.text, end="", flush=True)
                full_brain_response += chunk.text
            print() 

            # Cleanup
            cmd_to_send = full_brain_response.replace("```bash", "").replace("```", "").strip()

            if not cmd_to_send:
                print(f"{Fore.YELLOW}Brain sent empty command. Waiting...")
                time.sleep(2)
                continue

            # B. HANDS ACT
            if "WAIT" in cmd_to_send.upper():
                print(f"{Fore.YELLOW}[System]: Brain requested wait.")
                time.sleep(5)
                continue

            print(f"{Fore.GREEN}{Style.BRIGHT}\n[HANDS]: Processing...", end="")
            
            hands.sendline(cmd_to_send)
            
            # Expect prompt or EOF
            index = hands.expect(['>', r'\$', 'Type a message', '\u279c', pexpect.EOF])
            
            if index == 4: # EOF
                print(f"\n{Fore.RED}CLI crashed/exited during execution!")
                print(f"Last words: {hands.before}")
                break

            raw_output = hands.before
            cli_output = raw_output.replace(cmd_to_send, "").strip()
            
            print(f"{Fore.GREEN}{cli_output[:500]}...") 
            time.sleep(0.5)

        except KeyboardInterrupt:
            print(f"\n{Fore.RED}Rig Shutdown.")
            hands.close()
            sys.exit()
        except pexpect.TIMEOUT:
            print(f"{Fore.RED}Timeout. CLI took too long.")
            cli_output = "[SYSTEM ERROR] Timeout."
            continue

if __name__ == "__main__":
    start_rig()
