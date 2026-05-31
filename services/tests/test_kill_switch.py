"""Tests for the KillSwitch dual-trigger mechanism."""
from pathlib import Path

import pytest
from services.risk.kill_switch import KillSwitch


class TestKillSwitchFileDrop:
    def test_inactive_by_default(self, temp_flag_dir):
        ks = KillSwitch(flag_dir=temp_flag_dir)
        assert not ks.is_active()

    def test_stop_flag_activates(self, temp_flag_dir):
        ks = KillSwitch(flag_dir=temp_flag_dir)
        Path(temp_flag_dir, "STOP.flag").touch()
        assert ks.is_active()

    def test_resume_flag_clears(self, temp_flag_dir):
        ks = KillSwitch(flag_dir=temp_flag_dir)
        Path(temp_flag_dir, "STOP.flag").touch()
        assert ks.is_active()
        ks.resume()
        assert not ks.is_active()

    def test_trigger_creates_flag(self, temp_flag_dir):
        ks = KillSwitch(flag_dir=temp_flag_dir)
        assert not Path(temp_flag_dir, "STOP.flag").exists()
        ks.trigger()
        assert Path(temp_flag_dir, "STOP.flag").exists()
        assert ks.is_active()

    def test_health_check_returns_true(self, temp_flag_dir):
        ks = KillSwitch(flag_dir=temp_flag_dir)
        result = ks.health_check()
        assert result

    def test_health_check_does_not_leave_flags(self, temp_flag_dir):
        ks = KillSwitch(flag_dir=temp_flag_dir)
        ks.health_check()
        assert not Path(temp_flag_dir, "STOP.flag").exists()

    def test_resume_when_no_flag_is_safe(self, temp_flag_dir):
        ks = KillSwitch(flag_dir=temp_flag_dir)
        ks.resume()
        assert not ks.is_active()
