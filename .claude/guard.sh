#!/bin/bash
# Guardrail: Block file operations outside the QuantEdge_Platform directory
# This runs as a PreToolUse hook for Edit, Write, and Bash tools.

PROJECT_DIR="/Users/abhinavunmesh/Desktop/QuantEdge_Platform"
INPUT=$(cat)

TOOL_NAME=$(echo "$INPUT" | jq -r '.tool_name // empty')

case "$TOOL_NAME" in
  Edit|Write)
    FILE_PATH=$(echo "$INPUT" | jq -r '.tool_input.file_path // empty')
    if [ -n "$FILE_PATH" ]; then
      # Resolve to absolute path and check prefix
      RESOLVED=$(python3 -c "import os; print(os.path.realpath('$FILE_PATH'))")
      if [[ "$RESOLVED" != "$PROJECT_DIR"* ]]; then
        echo '{"decision":"block","reason":"GUARDRAIL: Cannot write/edit files outside /Users/abhinavunmesh/Desktop/QuantEdge_Platform/. Requested path: '"$FILE_PATH"'"}'
        exit 0
      fi
    fi
    ;;
  Bash)
    COMMAND=$(echo "$INPUT" | jq -r '.tool_input.command // empty')
    # Block commands that write outside the project dir
    # Check for obvious redirections or file operations targeting outside paths
    # Allow safe read-only commands (git status, version checks, etc.)
    # Block rm/mv/cp/tee/> that target paths outside the project
    for PATTERN in \
      "rm .*/" \
      "mv .*/" \
      "cp .*/" \
      "> /" \
      ">> /" \
      "tee /"; do
      if echo "$COMMAND" | grep -qE "$PATTERN"; then
        # Check if the target path is outside the project
        # Extract paths that start with / and check them
        PATHS=$(echo "$COMMAND" | grep -oE '/[^ "]+' | head -20)
        for P in $PATHS; do
          RESOLVED=$(python3 -c "import os; print(os.path.realpath('$P'))" 2>/dev/null)
          if [ -n "$RESOLVED" ] && [[ "$RESOLVED" != "$PROJECT_DIR"* ]] && [[ "$RESOLVED" != "/usr/"* ]] && [[ "$RESOLVED" != "/opt/"* ]] && [[ "$RESOLVED" != "/tmp/"* ]]; then
            echo '{"decision":"block","reason":"GUARDRAIL: Bash command appears to modify files outside the project directory. Blocked path: '"$P"'"}'
            exit 0
          fi
        done
      fi
    done
    ;;
esac

# Allow by default
echo '{"decision":"allow"}'
