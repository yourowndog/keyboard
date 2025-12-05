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
    print("Missing dependencies! Run this:")
    print("pip install pexpect google-generativeai colorama")
    sys.exit(1)

# --- CONFIGURATION ---

# 1. THE BRAIN (State-of-the-Art Reasoning)
# We use the new 3.0 Preview for maximum planning capability.
BRAIN_MODEL_NAME = "gemini-3-pro-preview"

# 2. THE HANDS (High Speed Executor)
# We use 2.5 Flash via CLI to leverage your Subscription/High-Rate-Limit.
HANDS_MODEL_NAME = "gemini-2.5-flash" 
CLI_COMMAND = f"gemini chat --model {HANDS_MODEL_NAME}"

# 3. AUTHENTICATION
# Pulls directly from your ~/.bashrc export
API_KEY = os.environ.get("GEMINI_API_KEY")

if not API_KEY:
    print(f"{Fore.RED}ERROR: GEMINI_API_KEY not found in environment variables.")
    print("Make sure you added 'export GEMINI_API_KEY=...' to your .bashrc")
    sys.exit(1)

# ---------------------

init(autoreset=True)
genai.configure(api_key=API_KEY)

def stream_print(prefix, color, text):
    print(f"{color}{Style.BRIGHT}\n[{prefix}]: {Style.RESET_ALL}{color}{text}{Style.RESET_ALL}")

def start_rig():
    print(f"{Fore.YELLOW}{Style.BRIGHT}>>> BOOTING HYBRID RIG v3.0 <<<")
    print(f"{Fore.YELLOW}Brain: {BRAIN_MODEL_NAME} (Paid/Pro)")
    print(f"{Fore.YELLOW}Hands: {HANDS_MODEL_NAME} (CLI/Sub)")

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
    try:
        # Spawn the CLI.
        hands = pexpect.spawn(CLI_COMMAND, encoding='utf-8', timeout=300)
        
        # CRITICAL FIX: Raw strings for regex to prevent SyntaxWarning/Errors
        # We look for common prompt indicators: '>', '$', or 'Type a message'
        hands.expect(['>', r'\$', 'Type a message']) 
        print(f"{Fore.GREEN}>> HANDS ONLINE.")
    except Exception as e:
        print(f"{Fore.RED}CRITICAL ERROR: Could not spawn Gemini CLI. Is it installed?")
        print(e)
        return

    # --- 3. THE LOOP ---
    cli_output = "Session Started. I am ready for instructions."
    
    while True:
        try:
            # A. BRAIN THINKS
            prompt = f"REPORT FROM HANDS:\n{cli_output}\n\nINSTRUCTION:"
            
            print(f"{Fore.CYAN}{Style.BRIGHT}\n[BRAIN]: ", end="", flush=True)
            
            full_brain_response = ""
            # Stream the response
            response_stream = chat_session_brain.send_message(prompt, stream=True)
            
            for chunk in response_stream:
                print(Fore.CYAN + chunk.text, end="", flush=True)
                full_brain_response += chunk.text
            print() 

            # Cleanup: Remove code blocks if the Brain tried to be fancy
            cmd_to_send = full_brain_response.replace("```bash", "").replace("```", "").strip()

            # B. HANDS ACT
            if cmd_to_send.upper() == "WAIT":
                print(f"{Fore.YELLOW}[System]: Brain requested wait.")
                time.sleep(5)
                continue

            print(f"{Fore.GREEN}{Style.BRIGHT}\n[HANDS]: Processing...", end="")
            
            hands.sendline(cmd_to_send)
            
            # Wait for the prompt to return (Blocking)
            hands.expect(['>', r'\$', 'Type a message'])
            
            raw_output = hands.before
            # Filter out the echoed command so we don't read it back
            cli_output = raw_output.replace(cmd_to_send, "").strip()
            
            print(f"{Fore.GREEN}{cli_output[:500]}...") # Preview output
            
            # Small safety buffer
            time.sleep(0.5)

        except KeyboardInterrupt:
            print(f"\n{Fore.RED}Rig Shutdown initiated.")
            hands.close()
            sys.exit()
        except pexpect.TIMEOUT:
            print(f"{Fore.RED}Timeout waiting for CLI response.")
            cli_output = "[SYSTEM ERROR] The CLI timed out. It might be stuck."
            continue
        except Exception as e:
            print(f"{Fore.RED}Loop Error: {e}")
            time.sleep(2)

if __name__ == "__main__":
    start_rig()
