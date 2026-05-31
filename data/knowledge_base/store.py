"""Knowledge Base — structured store of trade learnings for feedback loop."""
from __future__ import annotations

import json
import os
from pathlib import Path
from typing import List, Optional


class KnowledgeBase:
    def __init__(self, base_path: Optional[str] = None):
        self.base_path = Path(base_path or os.getenv("KB_PATH", "data/knowledge_base"))
        self.base_path.mkdir(parents=True, exist_ok=True)
        self.lessons_path = self.base_path / "lessons.jsonl"
        self.patterns_path = self.base_path / "patterns.json"

    def add_lesson(self, asset: str, lesson: str, classification: str):
        entry = {
            "asset": asset,
            "lesson": lesson,
            "classification": classification,
            "timestamp": __import__("datetime").datetime.utcnow().isoformat(),
        }
        with open(self.lessons_path, "a") as f:
            f.write(json.dumps(entry) + "\n")

    def add_lessons(self, lessons: List[dict]):
        for l in lessons:
            self.add_lesson(l.get("asset", ""), l.get("lesson", ""), l.get("classification", ""))

    def get_lessons(self, asset: Optional[str] = None, limit: int = 50) -> List[dict]:
        if not self.lessons_path.exists():
            return []
        results = []
        with open(self.lessons_path, "r") as f:
            for line in f:
                line = line.strip()
                if line:
                    try:
                        entry = json.loads(line)
                        if asset is None or entry.get("asset") == asset:
                            results.append(entry)
                    except json.JSONDecodeError:
                        continue
        return results[-limit:]

    def save_patterns(self, patterns: dict):
        with open(self.patterns_path, "w") as f:
            json.dump(patterns, f, default=str, indent=2)

    def load_patterns(self) -> dict:
        if not self.patterns_path.exists():
            return {}
        with open(self.patterns_path, "r") as f:
            return json.load(f)
