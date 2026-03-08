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


SETTINGS_ORG  = "YPDlp"
SETTINGS_APP  = "YouTubeDownloader"

DEFAULTS = {
    "output_dir":      os.path.expanduser("~/Downloads"),
    "ffmpeg_path":     r"C:\Users\hp\Downloads\ypdlp\ffmpeg\bin",
    "default_quality": "1080p",
    "default_format":  "MP4",
    "max_concurrent":  2,
}


def load_settings() -> dict:
    s = QSettings(SETTINGS_ORG, SETTINGS_APP)
    result = {}
    for k, v in DEFAULTS.items():
        stored = s.value(k, v)
        # QSettings returns strings; convert int fields
        if k == "max_concurrent":
            result[k] = int(stored)
        else:
            result[k] = stored
    return result


def save_settings(data: dict):
    s = QSettings(SETTINGS_ORG, SETTINGS_APP)
    for k, v in data.items():
        s.setValue(k, v)


class SettingsDialog(QDialog):
    def __init__(self, parent=None):
        super().__init__(parent)
        self.setWindowTitle("Settings")
        self.setMinimumWidth(520)
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
        grp_ff = QGroupBox("FFmpeg (required for conversion)")
        fl_ff  = QFormLayout(grp_ff)
        fl_ff.setSpacing(8)

        self.ff_edit = QLineEdit()
        self.ff_edit.setPlaceholderText("Leave blank if ffmpeg is in PATH")
        self.ff_btn  = QPushButton("Browse…")
        self.ff_btn.setFixedWidth(90)
        self.ff_btn.clicked.connect(self._pick_ffmpeg)

        row_ff = QHBoxLayout()
        row_ff.addWidget(self.ff_edit)
        row_ff.addWidget(self.ff_btn)
        fl_ff.addRow("FFmpeg Exe:", row_ff)
        root.addWidget(grp_ff)

        # ── Defaults ───────────────────────────
        grp_def = QGroupBox("Download Defaults")
        fl_def  = QFormLayout(grp_def)
        fl_def.setSpacing(8)

        self.qual_combo = QComboBox()
        self.qual_combo.addItems(["Best","4K (2160p)","2K (1440p)","1080p","720p","480p","360p"])
        fl_def.addRow("Default Quality:", self.qual_combo)

        self.fmt_combo = QComboBox()
        self.fmt_combo.addItems(["MP4","MKV","WEBM","AVI","MP3","M4A","FLAC","WAV","OGG","OPUS"])
        fl_def.addRow("Default Format:", self.fmt_combo)

        self.concurrent_spin = QSpinBox()
        self.concurrent_spin.setRange(1, 5)
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
        self.out_edit.setText(self._data["output_dir"])
        self.ff_edit.setText(self._data["ffmpeg_path"])
        idx = self.qual_combo.findText(self._data["default_quality"])
        if idx >= 0:
            self.qual_combo.setCurrentIndex(idx)
        idx = self.fmt_combo.findText(self._data["default_format"])
        if idx >= 0:
            self.fmt_combo.setCurrentIndex(idx)
        self.concurrent_spin.setValue(self._data["max_concurrent"])

    def _pick_output(self):
        d = QFileDialog.getExistingDirectory(self, "Select Output Folder",
                                             self.out_edit.text())
        if d:
            self.out_edit.setText(d)

    def _pick_ffmpeg(self):
        f, _ = QFileDialog.getOpenFileName(self, "Select FFmpeg Executable",
                                            "", "Executables (*.exe);;All Files (*)")
        if f:
            self.ff_edit.setText(f)

    def _save_and_accept(self):
        data = {
            "output_dir":      self.out_edit.text(),
            "ffmpeg_path":     self.ff_edit.text(),
            "default_quality": self.qual_combo.currentText(),
            "default_format":  self.fmt_combo.currentText(),
            "max_concurrent":  self.concurrent_spin.value(),
        }
        save_settings(data)
        self.accept()

    def get_settings(self) -> dict:
        return load_settings()
