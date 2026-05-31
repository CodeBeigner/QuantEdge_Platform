"""Kill switch — dual trigger: file-drop (local) + manual (API/cloud)."""
from __future__ import annotations

import logging
from pathlib import Path

_log = logging.getLogger(__name__)


class KillSwitch:
    def __init__(self, flag_dir: str = "./flags"):
        self.flag_dir = Path(flag_dir)
        self.flag_dir.mkdir(parents=True, exist_ok=True)
        self.stop_flag = self.flag_dir / "STOP.flag"
        self.resume_flag = self.flag_dir / "RESUME.flag"
        self._active = False

    def is_active(self) -> bool:
        if self.stop_flag.exists():
            self._active = True
        return self._active

    def trigger(self) -> None:
        self.stop_flag.touch()
        self._active = True
        _log.critical("KILL SWITCH TRIGGERED — all order generation halted")

    def resume(self) -> None:
        if self.stop_flag.exists():
            self.stop_flag.unlink()
        self._active = False
        self.resume_flag.touch()
        _log.info("Kill switch resumed — order generation re-enabled")
        if self.resume_flag.exists():
            self.resume_flag.unlink()

    def health_check(self) -> bool:
        test_flag = self.flag_dir / ".health_check_test"
        try:
            test_flag.touch()
            if not test_flag.exists():
                return False
            test_flag.unlink()
            return True
        except OSError:
            return False
