#!/bin/bash
ME=$1
TO=$2
MSG_FILE=$3

if [ "$#" -ne 3 ]; then
    echo "Usage: ./pass.sh [my_name] [target_name] [message_file.md]"
    exit 1
fi

BRIDGE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOG_FILE="$BRIDGE_DIR/conference.log"
TURN_FILE="$BRIDGE_DIR/turn.txt"
REPLY_FILE="$BRIDGE_DIR/latest_message.md"

# Append to human log
echo -e "\n========================================" >> "$LOG_FILE"
echo "From: $ME | To: $TO | $(date)" >> "$LOG_FILE"
echo "========================================" >> "$LOG_FILE"
cat "$MSG_FILE" >> "$LOG_FILE"

# Stage the message for the other agent
cp "$MSG_FILE" "$REPLY_FILE"

# Change the turn
echo "$TO" > "$TURN_FILE"

# Wait for our turn to come back
echo "Handed over to $TO. Waiting for reply..." >&2
while true; do
    CURRENT=$(cat "$TURN_FILE" 2>/dev/null)
    if [ "$CURRENT" == "$ME" ]; then
        break
    fi
    # Wait for the file to change to save CPU
    inotifywait -qq -e modify "$TURN_FILE" 2>/dev/null || sleep 2
done

# Output ONLY the latest reply so the LLM doesn't read the whole history
echo "=== MESSAGE FROM $TO ==="
cat "$REPLY_FILE"
