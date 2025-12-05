import os
import time
import sys
import pexpect
import google.generativeai as genai
from colorama import Fore, Style, init

# --- CONFIGURATION ---

# 1. THE BRAIN (Your Paid API Key)
# We use the "Thinking" model. As of Nov 2025, this is likely 'gemini-3-pro-preview' or 'gemini-exp-1114'
API_KEY = "YOUR_PAID_KEY_HERE"  # <--- PASTE YOUR KEY HERE
BRAIN_MODEL_NAME = "gemini-3-pro-preview" # Or 'gemini-exp-1114'

# 2. THE HANDS (Your Subscription CLI)
# We force the CLI to use Flash for speed. 
# It runs in a persistent process using pexpect.
CLI_COMMAND = "gemini chat --model gemini-2.0-flash"

# ---------------------

init(autoreset=True)
genai.configure(api_key=API_KEY)

def stream_print(prefix, color, text):
    """Pretty print helper"""
    print(f"{color}{Style.BRIGHT}\n[{prefix}]: {Style.RESET_ALL}{color}{text}{Style.RESET_ALL}")

def start_rig():
    print(f"{Fore.YELLOW}Initializing Hybrid Rig...")
    print(f"{Fore.YELLOW}Brain: {BRAIN_MODEL_NAME} (Paid API)")
    print(f"{Fore.YELLOW}Hands: Gemini CLI (Subscription/Free)")

    # --- 1. SETUP THE BRAIN ---
    # We give the brain a persona. It is NOT the executor. It is the Commander.
    brain = genai.GenerativeModel(
        model_name=BRAIN_MODEL_NAME,
        system_instruction=(
            "You are the MASTERMIND. You are running on an Android Galaxy S25 Ultra in Termux.\n"
            "You have a subordinate agent called 'HANDS' (a Gemini CLI instance).\n"
            "Your job is to plan tasks and issue text commands to HANDS.\n"
            "HANDS will report back with output. You analyze it and give the next instruction.\n"
            "To give a command, output ONLY the text you want typed into the CLI.\n"
            "If you need to wait or think, just output the reasoning."
        )
    )
    chat_session_brain = brain.start_chat(history=[])

    # --- 2. SPAWN THE HANDS (PEXPECT) ---
    print(f"{Fore.YELLOW}Spawning CLI subprocess...")
    try:
        # Spawn the CLI. encoding='utf-8' is crucial for Python 3.
        hands = pexpect.spawn(CLI_COMMAND, encoding='utf-8', timeout=300)
        
        # Expect the CLI prompt. Usually '>' or 'Type a message' or user@machine
        # We accept a few common variations to be safe.
        hands.expect(['>', 'Type a message', '\$']) 
        print(f"{Fore.GREEN}>> HANDS ONLINE.")
    except Exception as e:
        print(f"{Fore.RED}CRITICAL ERROR: Could not spawn Gemini CLI. Is it installed?")
        print(e)
        return

    # --- 3. THE LOOP ---
    # Initial state
    cli_output = "Session Started. I am ready for instructions."
    
    while True:
        try:
            # A. BRAIN THINKS
            # We send the last output from the CLI to the Brain
            prompt = f"REPORT FROM HANDS:\n{cli_output}\n\nINSTRUCTION:"
            
            # Stream the Brain's response so you see it thinking
            print(f"{Fore.CYAN}{Style.BRIGHT}\n[BRAIN ({BRAIN_MODEL_NAME})]: ", end="", flush=True)
            
            full_brain_response = ""
            response_stream = chat_session_brain.send_message(prompt, stream=True)
            
            for chunk in response_stream:
                print(Fore.CYAN + chunk.text, end="", flush=True)
                full_brain_response += chunk.text
            print() # Newline

            # Clean the response (remove any markdown code blocks if it accidentally adds them)
            cmd_to_send = full_brain_response.replace("```bash", "").replace("```", "").strip()

            # B. HANDS ACT
            # Send the Brain's text into the CLI process
            print(f"{Fore.GREEN}{Style.BRIGHT}\n[HANDS (CLI)]: Processing...", end="")
            
            hands.sendline(cmd_to_send)
            
            # Wait for the prompt to reappear (indicating the command finished)
            # This is the blocking part where the CLI does the work.
            hands.expect(['>', 'Type a message', '\$'])
            
            # Get the output *before* the prompt match
            raw_output = hands.before
            
            # Strip the command itself (echo) from the output so we don't feed it back endlessly
            cli_output = raw_output.replace(cmd_to_send, "").strip()
            
            # Print a preview of what happened
            print(f"{Fore.GREEN}{cli_output[:500]}...") # Print first 500 chars to avoid spamming
            
            # Optional: Safety Sleep (remove if you want max speed)
            time.sleep(1)

        except KeyboardInterrupt:
            print(f"\n{Fore.RED}Rig Shutdown initiated.")
            hands.close()
            sys.exit()
        except pexpect.TIMEOUT:
            print(f"{Fore.RED}Timeout waiting for CLI. It might be stuck or waiting for input.")
            # We loop back and tell the brain it timed out
            cli_output = "[SYSTEM ERROR] The CLI timed out. It might be stuck."
            continue

if __name__ == "__main__":
    start_rig()
