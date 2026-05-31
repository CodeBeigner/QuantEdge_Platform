"""Abstract LLM provider interface."""
from __future__ import annotations

from abc import ABC, abstractmethod
from dataclasses import dataclass, field
from typing import List, Optional


@dataclass
class LLMMessage:
    role: str  # system, user, assistant
    content: str


@dataclass
class LLMResponse:
    content: str
    model: str
    input_tokens: int = 0
    output_tokens: int = 0
    cost_usd: float = 0.0
    finish_reason: str = "stop"


class LLMProvider(ABC):
    @abstractmethod
    def chat(
        self,
        messages: List[LLMMessage],
        temperature: Optional[float] = None,
        max_tokens: Optional[int] = None,
    ) -> LLMResponse:
        """Send messages to the LLM and return response."""
        ...

    @abstractmethod
    def get_name(self) -> str:
        """Return provider name."""
        ...

    @abstractmethod
    def estimate_tokens(self, text: str) -> int:
        """Estimate token count for a text string."""
        ...
