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

# 1. BRAIN (PAID)
# The "Thinking" model released Nov 2025. 
# This uses your 'GEMINI_PAID_API_KEY'
BRAIN_MODEL_NAME = "gemini-3-pro-preview" 

# 2. HANDS (FREE)
# 'gemini-1.5-flash' is RETIRED as of mid-2025. We MUST use 2.5.
# This uses your 'GEMINI_FREE_API_KEY'
HANDS_MODEL_NAME = "gemini-2.5-flash"
CLI_COMMAND = f"gemini chat --model {HANDS_MODEL_NAME}"

# --- AUTHENTICATION ---
PAID_KEY = os.environ.get("GEMINI_PAID_API_KEY")
FREE_KEY = os.environ.get("GEMINI_FREE_API_KEY")

if not PAID_KEY or not FREE_KEY:
    print(f"{Fore.RED}ERROR: Missing API Keys in environment.")
    print("Please source your .bashrc or restart your termux session.")
    print(f"Paid Key Present: {bool(PAID_KEY)}")
    print(f"Free Key Present: {bool(FREE_KEY)}")
    sys.exit(1)

init(autoreset=True)

# Configure the BRAIN to use the PAID key
genai.configure(api_key=PAID_KEY)

def start_rig():
    print(f"{Fore.YELLOW}{Style.BRIGHT}>>> BOOTING HYBRID RIG v4.0 (Dual-Key Edition) <<<")
    print(f"{Fore.YELLOW}Brain: {BRAIN_MODEL_NAME} (Paid Key)")
    print(f"{Fore.YELLOW}Hands: {HANDS_MODEL_NAME} (Free Key)")

    # --- 1. SETUP THE BRAIN ---
    print(f"{Fore.YELLOW}Initializing Brain...")
    try:
        brain = genai.GenerativeModel(
            model_name=BRAIN_MODEL_NAME,
            system_instruction=(
                "You are the MASTERMIND. You are running on an Android Galaxy S25 Ultra in Termux.\n"
                "You have a subordinate agent called 'HANDS' (a Gemini CLI instance).\n"
                "Your job is to plan coding tasks, debug errors, and issue text commands to HANDS.\n"
                "HANDS will report back with output. You analyze it and give the next instruction.\n"
                "To give a command, output ONLY the text you want typed into the CLI.\n"
                "If you need to wait or think, just output the reasoning.\n"
                "Assume the user has already whitelisted shell commands."
            )
        )
        chat_session_brain = brain.start_chat(history=[])
    except Exception as e:
        print(f"{Fore.RED}Brain Init Failed: {e}")
        return

    # --- 2. SPAWN THE HANDS (PEXPECT) ---
    print(f"{Fore.YELLOW}Spawning Hands (CLI)...")
    
    # CRITICAL: Create a custom environment for the subprocess
    # We force the subprocess to use the FREE key by setting GEMINI_API_KEY 
    # inside its specific environment sandbox.
    cli_env = os.environ.copy()
    cli_env["GEMINI_API_KEY"] = FREE_KEY
    
    try:
        # Spawn CLI with the custom environment
        hands = pexpect.spawn(CLI_COMMAND, env=cli_env, encoding='utf-8', timeout=300)
        
        # Expect: '>', '$', or 'Type a message'. Using raw strings for regex safety.
        index = hands.expect(['>', r'\$', 'Type a message', '\u279c', pexpect.EOF])
        
        if index == 4: # EOF
            print(f"{Fore.RED}CRITICAL: CLI exited immediately.")
            print(f"Output: {hands.before}")
            return
            
        print(f"{Fore.GREEN}>> HANDS ONLINE.")

    except Exception as e:
        print(f"{Fore.RED}Spawn Error: {e}")
        return

    # --- 3. THE LOOP ---
    cli_output = "Session Started. Ready for orders."
    
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

            # Cleanup command
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
            
            # Wait for prompt
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
        except Exception as e:
            print(f"{Fore.RED}Loop Error: {e}")
            time.sleep(2)

if __name__ == "__main__":
    start_rig()
