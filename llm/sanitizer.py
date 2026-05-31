"""Prompt injection prevention — sanitize user/external content before LLM calls."""
from __future__ import annotations

import re


DANGEROUS_PATTERNS = [
    r"ignore\s+(all\s+)?(previous|above|prior)\s+instructions?",
    r"you\s+are\s+now\s+(a|an)\s",
    r"system\s*(prompt|message|instruction):",
    r"<\|im_start\|>",
    r"<\|im_end\|>",
    r"\[INST\]",
    r"\[/INST\]",
    r"new\s+(role|persona|identity):",
]


def sanitize_content(text: str) -> str:
    """Remove or escape prompt injection patterns from user-supplied text."""
    sanitized = text
    for pattern in DANGEROUS_PATTERNS:
        sanitized = re.sub(pattern, "[filtered]", sanitized, flags=re.IGNORECASE)
    if len(sanitized) > 32000:
        sanitized = sanitized[:32000] + "\n[truncated]"
    return sanitized


def safe_system_prompt(prompt: str) -> str:
    """Ensure system prompt doesn't contain dangerous patterns."""
    return sanitize_content(prompt)


def safe_user_message(content: str) -> str:
    """Wrap user content with sanitization markers."""
    return f"[sanitized_user_input]\n{sanitize_content(content)}\n[/sanitized_user_input]"
