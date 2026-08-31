"""
ui_settings.py — Settings dialog (FFmpeg path, output folder, defaults)
"""
import os
from PyQt6.QtWidgets import (
    QDialog, QVBoxLayout, QHBoxLayout, QLabel, QLineEdit,
    QPushButton, QSpinBox, QComboBox, QFileDialog, QGroupBox,
    QFormLayout, QDialogButtonBox
)
from PyQt6.QtCore import QSettings, Qt

import ffmpeg_utils

SETTINGS_ORG  = "YPDlp"
SETTINGS_APP  = "YouTubeDownloader"

DEFAULTS = {
    "output_dir":      os.path.join(os.path.expanduser("~"), "Downloads"),
    "ffmpeg_path":     "",
    "default_quality": "1080p",
    "default_format":  "MP4",
    "max_concurrent":  2,
}


def load_settings() -> dict:
    s = QSettings(SETTINGS_ORG, SETTINGS_APP)
    result = {}
    for k, v in DEFAULTS.items():
        stored = s.value(k, v)
        if k == "max_concurrent":
            try:
                result[k] = int(stored)
            except (ValueError, TypeError):
                result[k] = 2
        else:
            result[k] = str(stored) if stored is not None else v
    return result


def save_settings(data: dict):
    s = QSettings(SETTINGS_ORG, SETTINGS_APP)
    for k, v in data.items():
        s.setValue(k, v)


class SettingsDialog(QDialog):
    def __init__(self, parent=None):
        super().__init__(parent)
        self.setWindowTitle("Settings — YPDlp")
        self.setMinimumWidth(540)
        self.setModal(True)
        self._data = load_settings()
        self._build_ui()
        self._populate()

    # ── UI ────────────────────────────────────────
    def _build_ui(self):
        root = QVBoxLayout(self)
        root.setSpacing(16)
        root.setContentsMargins(20, 20, 20, 20)

        # ── Output folder ──────────────────────
        grp_out = QGroupBox("Download Location")
        fl_out  = QFormLayout(grp_out)
        fl_out.setSpacing(8)

        self.out_edit = QLineEdit()
        self.out_edit.setPlaceholderText("Select folder…")
        self.out_btn  = QPushButton("Browse…")
        self.out_btn.setFixedWidth(90)
        self.out_btn.clicked.connect(self._pick_output)

        row_out = QHBoxLayout()
        row_out.addWidget(self.out_edit)
        row_out.addWidget(self.out_btn)
        fl_out.addRow("Output Folder:", row_out)
        root.addWidget(grp_out)

        # ── FFmpeg ─────────────────────────────
        grp_ff = QGroupBox("FFmpeg Configuration")
        fl_ff  = QFormLayout(grp_ff)
        fl_ff.setSpacing(8)

        self.ff_edit = QLineEdit()
        self.ff_edit.setPlaceholderText("Auto-detect (built-in / system PATH)")
        self.ff_btn  = QPushButton("Browse…")
        self.ff_btn.setFixedWidth(90)
        self.ff_btn.clicked.connect(self._pick_ffmpeg)

        self.ff_reset_btn = QPushButton("Auto")
        self.ff_reset_btn.setFixedWidth(60)
        self.ff_reset_btn.clicked.connect(lambda: self.ff_edit.setText(""))

        row_ff = QHBoxLayout()
        row_ff.addWidget(self.ff_edit)
        row_ff.addWidget(self.ff_btn)
        row_ff.addWidget(self.ff_reset_btn)
        fl_ff.addRow("Custom FFmpeg:", row_ff)

        # Status note
        detected = ffmpeg_utils.get_ffmpeg_path()
        status_text = f"✔ FFmpeg active: {detected}" if detected else "⚠️ FFmpeg will be auto-installed on download"
        self.ff_status = QLabel(status_text)
        self.ff_status.setStyleSheet("color: #44bb44;" if detected else "color: #ffaa00;")
        self.ff_status.setWordWrap(True)
        fl_ff.addRow("Status:", self.ff_status)

        root.addWidget(grp_ff)

        # ── Defaults ───────────────────────────
        grp_def = QGroupBox("Download Defaults")
        fl_def  = QFormLayout(grp_def)
        fl_def.setSpacing(8)

        self.qual_combo = QComboBox()
        self.qual_combo.addItems(["Best", "4K (2160p)", "2K (1440p)", "1080p", "720p", "480p", "360p"])
        fl_def.addRow("Default Quality:", self.qual_combo)

        self.fmt_combo = QComboBox()
        self.fmt_combo.addItems(["MP4", "MKV", "WEBM", "AVI", "MP3", "M4A", "FLAC", "WAV", "OGG", "OPUS"])
        fl_def.addRow("Default Format:", self.fmt_combo)

        self.concurrent_spin = QSpinBox()
        self.concurrent_spin.setRange(1, 10)
        fl_def.addRow("Max Concurrent Downloads:", self.concurrent_spin)

        root.addWidget(grp_def)

        # ── Buttons ────────────────────────────
        btns = QDialogButtonBox(
            QDialogButtonBox.StandardButton.Ok | QDialogButtonBox.StandardButton.Cancel
        )
        btns.accepted.connect(self._save_and_accept)
        btns.rejected.connect(self.reject)
        root.addWidget(btns)

    def _populate(self):
        self.out_edit.setText(self._data.get("output_dir", ""))
        self.ff_edit.setText(self._data.get("ffmpeg_path", ""))
        idx = self.qual_combo.findText(self._data.get("default_quality", "1080p"))
        if idx >= 0:
            self.qual_combo.setCurrentIndex(idx)
        idx = self.fmt_combo.findText(self._data.get("default_format", "MP4"))
        if idx >= 0:
            self.fmt_combo.setCurrentIndex(idx)
        self.concurrent_spin.setValue(self._data.get("max_concurrent", 2))

    def _pick_output(self):
        d = QFileDialog.getExistingDirectory(self, "Select Output Folder",
                                             self.out_edit.text() or os.path.expanduser("~"))
        if d:
            self.out_edit.setText(d)

    def _pick_ffmpeg(self):
        f, _ = QFileDialog.getOpenFileName(self, "Select FFmpeg Executable",
                                            "", "Executables (*.exe);;All Files (*)")
        if f:
            self.ff_edit.setText(f)

    def _save_and_accept(self):
        data = {
            "output_dir":      self.out_edit.text().strip(),
            "ffmpeg_path":     self.ff_edit.text().strip(),
            "default_quality": self.qual_combo.currentText(),
            "default_format":  self.fmt_combo.currentText(),
            "max_concurrent":  self.concurrent_spin.value(),
        }
        save_settings(data)
        self.accept()

    def get_settings(self) -> dict:
        return load_settings()
