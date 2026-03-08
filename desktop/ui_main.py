"""
ui_main.py — Main window for YPDlp YouTube Downloader
Dark modern UI built with PyQt6
"""
import os
from PyQt6.QtWidgets import (
    QMainWindow, QWidget, QVBoxLayout, QHBoxLayout,
    QLabel, QLineEdit, QPushButton, QComboBox, QProgressBar,
    QScrollArea, QFrame, QFileDialog, QMessageBox,
    QStatusBar, QSizePolicy, QApplication
)
from PyQt6.QtCore import Qt, QSize, QTimer
from PyQt6.QtGui import QPixmap, QFont, QIcon, QColor, QPalette, QCursor

from downloader import (
    DownloadWorker, InfoWorker,
    QUALITY_MAP, AUDIO_FORMATS, FORMAT_MAP
)
from thumbnail_loader import ThumbnailLoader
from ui_settings import SettingsDialog, load_settings


# ─────────────────────────────────────────────
#  Dark palette
# ─────────────────────────────────────────────
STYLE = """
QMainWindow, QWidget {
    background-color: #0f0f0f;
    color: #e8e8e8;
    font-family: 'Segoe UI', sans-serif;
}
QLabel { color: #e8e8e8; }
QLineEdit {
    background: #1e1e1e;
    border: 1.5px solid #333;
    border-radius: 8px;
    color: #fff;
    padding: 8px 12px;
    font-size: 14px;
}
QLineEdit:focus { border-color: #ff0000; }
QComboBox {
    background: #1e1e1e;
    border: 1.5px solid #333;
    border-radius: 8px;
    color: #e8e8e8;
    padding: 6px 10px;
    font-size: 13px;
    min-width: 110px;
}
QComboBox::drop-down { border: none; width: 28px; }
QComboBox::down-arrow { image: none; border-left: 5px solid transparent;
    border-right: 5px solid transparent;
    border-top: 6px solid #aaa; margin-right: 8px; }
QComboBox QAbstractItemView {
    background: #1e1e1e; color: #e8e8e8;
    border: 1px solid #444; selection-background-color: #ff0000; }
QPushButton {
    background: #ff0000;
    color: #fff;
    border: none;
    border-radius: 8px;
    padding: 9px 22px;
    font-size: 13px;
    font-weight: bold;
}
QPushButton:hover  { background: #cc0000; }
QPushButton:pressed{ background: #990000; }
QPushButton#btnGray {
    background: #2a2a2a; color: #ccc; font-weight: normal;
}
QPushButton#btnGray:hover { background: #3a3a3a; }
QPushButton#btnSmall {
    background: #2a2a2a; color: #e8e8e8;
    padding: 6px 14px; font-size: 12px; font-weight: normal;
}
QPushButton#btnSmall:hover { background: #3a3a3a; }
QPushButton#btnCancel {
    background: #3a1010; color: #ff6666;
    padding: 4px 10px; font-size: 11px; font-weight: normal;
    border-radius: 6px;
}
QPushButton#btnCancel:hover { background: #550000; }
QProgressBar {
    background: #1e1e1e;
    border: none;
    border-radius: 4px;
    height: 6px;
    text-align: center;
}
QProgressBar::chunk {
    background: qlineargradient(x1:0,y1:0,x2:1,y2:0,
        stop:0 #ff0000, stop:1 #ff6666);
    border-radius: 4px;
}
QScrollArea { border: none; background: transparent; }
QScrollBar:vertical {
    background: #1a1a1a; width: 8px; border-radius: 4px;
}
QScrollBar::handle:vertical {
    background: #444; border-radius: 4px; min-height: 30px;
}
QScrollBar::add-line:vertical, QScrollBar::sub-line:vertical { height: 0; }
QFrame#card {
    background: #181818;
    border-radius: 12px;
    border: 1px solid #282828;
}
QFrame#divider { background: #282828; }
QStatusBar { background: #0a0a0a; color: #888; font-size: 12px; }
"""


# ─────────────────────────────────────────────
#  Download Queue Card Widget
# ─────────────────────────────────────────────
class QueueCard(QFrame):
    def __init__(self, url, title, fmt, quality, thumbnail_url, parent=None):
        super().__init__(parent)
        self.setObjectName("card")
        self.url           = url
        self.title         = title
        self.fmt           = fmt
        self.quality       = quality
        self.thumbnail_url = thumbnail_url
        self.worker        = None
        self._build_ui()

    def _build_ui(self):
        self.setFixedHeight(110)
        root = QHBoxLayout(self)
        root.setContentsMargins(12, 10, 12, 10)
        root.setSpacing(14)

        # Thumbnail
        self.thumb_lbl = QLabel()
        self.thumb_lbl.setFixedSize(142, 80)
        self.thumb_lbl.setAlignment(Qt.AlignmentFlag.AlignCenter)
        self.thumb_lbl.setStyleSheet(
            "background:#0a0a0a; border-radius:8px; color:#555;"
        )
        self.thumb_lbl.setText("🎬")
        root.addWidget(self.thumb_lbl)

        # Info column
        col = QVBoxLayout()
        col.setSpacing(4)

        self.title_lbl = QLabel(self.title)
        self.title_lbl.setFont(QFont("Segoe UI", 12, QFont.Weight.DemiBold))
        self.title_lbl.setWordWrap(True)
        self.title_lbl.setMaximumHeight(40)
        col.addWidget(self.title_lbl)

        badge_row = QHBoxLayout()
        badge_row.setSpacing(8)
        for txt in [self.fmt, self.quality]:
            b = QLabel(txt)
            b.setStyleSheet(
                "background:#ff0000; color:#fff; border-radius:4px;"
                "padding:2px 7px; font-size:11px; font-weight:bold;"
            )
            badge_row.addWidget(b)
        badge_row.addStretch()
        col.addLayout(badge_row)

        # Progress
        self.prog_bar = QProgressBar()
        self.prog_bar.setFixedHeight(6)
        self.prog_bar.setValue(0)
        self.prog_bar.setTextVisible(False)
        col.addWidget(self.prog_bar)

        # Status row
        status_row = QHBoxLayout()
        self.status_lbl = QLabel("Queued")
        self.status_lbl.setStyleSheet("color:#888; font-size:11px;")
        status_row.addWidget(self.status_lbl)

        self.speed_lbl = QLabel("")
        self.speed_lbl.setStyleSheet("color:#aaa; font-size:11px;")
        status_row.addStretch()
        status_row.addWidget(self.speed_lbl)

        self.cancel_btn = QPushButton("✕ Cancel")
        self.cancel_btn.setObjectName("btnCancel")
        self.cancel_btn.setFixedWidth(72)
        self.cancel_btn.clicked.connect(self._cancel)
        status_row.addWidget(self.cancel_btn)

        col.addLayout(status_row)
        root.addLayout(col)

    def set_thumbnail(self, pix: QPixmap):
        self.thumb_lbl.setPixmap(
            pix.scaled(142, 80, Qt.AspectRatioMode.KeepAspectRatio,
                       Qt.TransformationMode.SmoothTransformation)
        )

    def update_progress(self, pct: int, speed: str, eta: str):
        self.prog_bar.setValue(pct)
        self.status_lbl.setText(f"{pct}%  •  ETA {eta}")
        self.speed_lbl.setText(speed)

    def set_done(self, success: bool, msg: str):
        self.prog_bar.setValue(100 if success else self.prog_bar.value())
        self.status_lbl.setText("✔ Done" if success else f"✘ {msg}")
        self.status_lbl.setStyleSheet(
            f"color:{'#44bb44' if success else '#ff4444'}; font-size:11px;"
        )
        self.speed_lbl.setText("")
        self.cancel_btn.setEnabled(False)

    def _cancel(self):
        if self.worker and self.worker.isRunning():
            self.worker.cancel()
        self.set_done(False, "Cancelled")


# ─────────────────────────────────────────────
#  Main Window
# ─────────────────────────────────────────────
class MainWindow(QMainWindow):
    def __init__(self):
        super().__init__()
        self.setWindowTitle("YPDlp — YouTube Downloader")
        self.resize(900, 700)
        self.setMinimumSize(720, 560)
        self.setStyleSheet(STYLE)

        self._settings    = load_settings()
        self._info_worker = None
        self._thumb_worker = None
        self._current_info = None
        self._queue_cards  = []

        self._build_ui()
        self._status_bar.showMessage("Ready. Paste a YouTube URL above to get started.")

    # ─────────────────────────────────────────
    #  Build UI
    # ─────────────────────────────────────────
    def _build_ui(self):
        central = QWidget()
        self.setCentralWidget(central)
        root = QVBoxLayout(central)
        root.setContentsMargins(0, 0, 0, 0)
        root.setSpacing(0)

        root.addWidget(self._make_header())

        # Main content (URL + info + queue)
        content = QWidget()
        content.setStyleSheet("background:#0f0f0f;")
        cv = QVBoxLayout(content)
        cv.setContentsMargins(28, 20, 28, 10)
        cv.setSpacing(18)

        cv.addWidget(self._make_url_bar())
        cv.addWidget(self._make_info_panel())
        cv.addWidget(self._make_format_bar())
        cv.addWidget(self._make_queue_section())

        root.addWidget(content)

        # Status bar
        self._status_bar = QStatusBar()
        self.setStatusBar(self._status_bar)

    # ─── Header ───────────────────────────────
    def _make_header(self):
        hdr = QWidget()
        hdr.setFixedHeight(56)
        hdr.setStyleSheet("background:#ff0000;")
        hl = QHBoxLayout(hdr)
        hl.setContentsMargins(20, 0, 20, 0)

        logo = QLabel("▶  YPDlp")
        logo.setFont(QFont("Segoe UI", 16, QFont.Weight.Bold))
        logo.setStyleSheet("color:white; letter-spacing:1px;")
        hl.addWidget(logo)
        hl.addStretch()

        sub = QLabel("4K · MKV · MP3 · All Formats")
        sub.setStyleSheet("color:rgba(255,255,255,0.75); font-size:12px;")
        hl.addWidget(sub)
        hl.addSpacing(20)

        self._settings_btn = QPushButton("⚙  Settings")
        self._settings_btn.setObjectName("btnGray")
        self._settings_btn.setStyleSheet(
            "background:rgba(255,255,255,0.15); color:white; border-radius:8px;"
            "padding:6px 16px; font-size:12px; border:none;"
        )
        self._settings_btn.clicked.connect(self._open_settings)
        hl.addWidget(self._settings_btn)
        return hdr

    # ─── URL Bar ──────────────────────────────
    def _make_url_bar(self):
        frame = QFrame()
        frame.setObjectName("card")
        hl = QHBoxLayout(frame)
        hl.setContentsMargins(14, 10, 14, 10)
        hl.setSpacing(10)

        self._url_edit = QLineEdit()
        self._url_edit.setPlaceholderText(
            "🔗  Paste YouTube / video URL here…"
        )
        self._url_edit.returnPressed.connect(self._fetch_info)
        hl.addWidget(self._url_edit)

        self._fetch_btn = QPushButton("  Fetch Info")
        self._fetch_btn.setFixedWidth(120)
        self._fetch_btn.clicked.connect(self._fetch_info)
        hl.addWidget(self._fetch_btn)

        self._paste_btn = QPushButton("📋 Paste")
        self._paste_btn.setObjectName("btnSmall")
        self._paste_btn.setFixedWidth(80)
        self._paste_btn.clicked.connect(self._paste_url)
        hl.addWidget(self._paste_btn)
        return frame

    # ─── Info Panel ───────────────────────────
    def _make_info_panel(self):
        frame = QFrame()
        frame.setObjectName("card")
        hl = QHBoxLayout(frame)
        hl.setContentsMargins(14, 12, 14, 12)
        hl.setSpacing(16)

        self._thumb_lbl = QLabel()
        self._thumb_lbl.setFixedSize(240, 135)
        self._thumb_lbl.setAlignment(Qt.AlignmentFlag.AlignCenter)
        self._thumb_lbl.setStyleSheet(
            "background:#0a0a0a; border-radius:10px; color:#444; font-size:30px;"
        )
        self._thumb_lbl.setText("🎬")
        hl.addWidget(self._thumb_lbl)

        info_col = QVBoxLayout()
        info_col.setSpacing(6)

        self._title_lbl = QLabel("No video loaded")
        self._title_lbl.setFont(QFont("Segoe UI", 14, QFont.Weight.Bold))
        self._title_lbl.setWordWrap(True)
        info_col.addWidget(self._title_lbl)

        self._channel_lbl = QLabel("")
        self._channel_lbl.setStyleSheet("color:#aaa; font-size:12px;")
        info_col.addWidget(self._channel_lbl)

        self._duration_lbl = QLabel("")
        self._duration_lbl.setStyleSheet("color:#888; font-size:12px;")
        info_col.addWidget(self._duration_lbl)

        self._views_lbl = QLabel("")
        self._views_lbl.setStyleSheet("color:#666; font-size:11px;")
        info_col.addWidget(self._views_lbl)

        info_col.addStretch()
        hl.addLayout(info_col)
        return frame

    # ─── Format / Quality / Download Bar ──────
    def _make_format_bar(self):
        frame = QFrame()
        frame.setObjectName("card")
        hl = QHBoxLayout(frame)
        hl.setContentsMargins(14, 10, 14, 10)
        hl.setSpacing(12)

        hl.addWidget(QLabel("Type:"))
        self._type_combo = QComboBox()
        self._type_combo.addItems(["Video", "Audio Only"])
        self._type_combo.currentTextChanged.connect(self._on_type_changed)
        hl.addWidget(self._type_combo)

        hl.addWidget(QLabel("Quality:"))
        self._qual_combo = QComboBox()
        self._qual_combo.addItems(list(QUALITY_MAP.keys()))
        # default
        idx = self._qual_combo.findText(self._settings["default_quality"])
        if idx >= 0:
            self._qual_combo.setCurrentIndex(idx)
        hl.addWidget(self._qual_combo)

        hl.addWidget(QLabel("Format:"))
        self._fmt_combo = QComboBox()
        hl.addWidget(self._fmt_combo)

        hl.addStretch()

        # Output folder
        self._folder_lbl = QLabel(self._short_path(self._settings["output_dir"]))
        self._folder_lbl.setStyleSheet("color:#888; font-size:11px;")
        hl.addWidget(self._folder_lbl)

        self._folder_btn = QPushButton("📁")
        self._folder_btn.setObjectName("btnSmall")
        self._folder_btn.setFixedWidth(38)
        self._folder_btn.setToolTip("Change output folder")
        self._folder_btn.clicked.connect(self._pick_folder)
        hl.addWidget(self._folder_btn)

        self._add_btn = QPushButton("＋  Add to Queue")
        self._add_btn.setFixedWidth(148)
        self._add_btn.clicked.connect(self._add_to_queue)
        hl.addWidget(self._add_btn)

        self._on_type_changed("Video")  # populate format combo
        return frame

    # ─── Queue Section ────────────────────────
    def _make_queue_section(self):
        vbox = QVBoxLayout()
        vbox.setSpacing(10)

        hdr = QHBoxLayout()
        q_lbl = QLabel("Download Queue")
        q_lbl.setFont(QFont("Segoe UI", 13, QFont.Weight.DemiBold))
        hdr.addWidget(q_lbl)
        hdr.addStretch()
        clear_btn = QPushButton("Clear Done")
        clear_btn.setObjectName("btnSmall")
        clear_btn.clicked.connect(self._clear_done)
        hdr.addWidget(clear_btn)
        vbox.addLayout(hdr)

        self._scroll_area = QScrollArea()
        self._scroll_area.setWidgetResizable(True)
        self._scroll_area.setHorizontalScrollBarPolicy(
            Qt.ScrollBarPolicy.ScrollBarAlwaysOff)

        self._queue_container = QWidget()
        self._queue_container.setStyleSheet("background:transparent;")
        self._queue_layout = QVBoxLayout(self._queue_container)
        self._queue_layout.setContentsMargins(0, 0, 0, 0)
        self._queue_layout.setSpacing(8)
        self._queue_layout.addStretch()

        self._scroll_area.setWidget(self._queue_container)

        # Placeholder
        self._empty_lbl = QLabel("Queue is empty. Paste a URL and add items above.")
        self._empty_lbl.setAlignment(Qt.AlignmentFlag.AlignCenter)
        self._empty_lbl.setStyleSheet("color:#444; font-size:13px; margin:40px;")
        self._queue_layout.insertWidget(0, self._empty_lbl)

        wrapper = QWidget()
        wl = QVBoxLayout(wrapper)
        wl.setContentsMargins(0, 0, 0, 0)
        wl.addLayout(vbox)
        wl.addWidget(self._scroll_area)
        return wrapper

    # ─────────────────────────────────────────
    #  Slots / Logic
    # ─────────────────────────────────────────
    def _paste_url(self):
        clip = QApplication.clipboard().text().strip()
        if clip:
            self._url_edit.setText(clip)
            self._fetch_info()

    def _on_type_changed(self, t):
        self._fmt_combo.clear()
        if t == "Audio Only":
            self._fmt_combo.addItems(["MP3", "M4A", "FLAC", "WAV", "OGG", "OPUS"])
            self._qual_combo.setEnabled(False)
        else:
            self._fmt_combo.addItems(["MP4", "MKV", "WEBM", "AVI"])
            self._qual_combo.setEnabled(True)

        # Restore user default if matches
        dfmt = self._settings["default_format"]
        idx = self._fmt_combo.findText(dfmt)
        if idx >= 0:
            self._fmt_combo.setCurrentIndex(idx)

    def _fetch_info(self):
        url = self._url_edit.text().strip()
        if not url:
            return
        self._fetch_btn.setEnabled(False)
        self._fetch_btn.setText("Fetching…")
        self._title_lbl.setText("Loading…")
        self._channel_lbl.setText("")
        self._duration_lbl.setText("")
        self._views_lbl.setText("")
        self._thumb_lbl.setText("⏳")
        self._current_info = None

        self._info_worker = InfoWorker(url, self)
        self._info_worker.result.connect(self._on_info_ready)
        self._info_worker.error.connect(self._on_info_error)
        self._info_worker.start()

    def _on_info_ready(self, info: dict):
        self._current_info = info
        title    = info.get("title", "Unknown Title")
        channel  = info.get("uploader", info.get("channel", ""))
        duration = info.get("duration", 0)
        views    = info.get("view_count", 0)
        thumb_url= info.get("thumbnail", "")

        self._title_lbl.setText(title)
        self._channel_lbl.setText(f"📺  {channel}")
        mins, secs = divmod(int(duration or 0), 60)
        hrs,  mins = divmod(mins, 60)
        dur_str = f"{hrs:02d}:{mins:02d}:{secs:02d}" if hrs else f"{mins:02d}:{secs:02d}"
        self._duration_lbl.setText(f"⏱️  {dur_str}")
        if views:
            self._views_lbl.setText(f"👁️  {views:,} views")

        self._fetch_btn.setEnabled(True)
        self._fetch_btn.setText("  Fetch Info")
        self._status_bar.showMessage("Video info loaded. Choose format and click Add to Queue.")

        if thumb_url:
            self._thumb_lbl.setText("⏳")
            self._thumb_worker = ThumbnailLoader(thumb_url, self)
            self._thumb_worker.loaded.connect(
                lambda pix: self._thumb_lbl.setPixmap(
                    pix.scaled(240, 135, Qt.AspectRatioMode.KeepAspectRatio,
                               Qt.TransformationMode.SmoothTransformation)
                )
            )
            self._thumb_worker.failed.connect(lambda: self._thumb_lbl.setText("🎬"))
            self._thumb_worker.start()

    def _on_info_error(self, err: str):
        self._fetch_btn.setEnabled(True)
        self._fetch_btn.setText("  Fetch Info")
        self._title_lbl.setText("Failed to load video.")
        self._status_bar.showMessage(f"Error: {err}")
        QMessageBox.warning(self, "Fetch Error",
                            f"Could not fetch video info:\n\n{err}")

    def _add_to_queue(self):
        if self._current_info is None:
            QMessageBox.information(self, "No Video",
                                    "Please fetch a video URL first.")
            return

        url      = self._url_edit.text().strip()
        title    = self._current_info.get("title", "Unknown")
        fmt      = self._fmt_combo.currentText()
        quality  = self._qual_combo.currentText()
        thumb    = self._current_info.get("thumbnail", "")

        card = QueueCard(url, title, fmt, quality, thumb, self)
        self._queue_cards.append(card)

        # Insert before the stretch
        self._queue_layout.insertWidget(
            self._queue_layout.count() - 1, card
        )
        self._empty_lbl.setVisible(False)

        # Load thumbnail for card
        if thumb:
            tw = ThumbnailLoader(thumb, card)
            tw.loaded.connect(card.set_thumbnail)
            tw.start()

        self._start_download(card)
        self._status_bar.showMessage(f"Added to queue: {title}")

    def _start_download(self, card: QueueCard):
        settings = self._settings
        worker = DownloadWorker(
            url             = card.url,
            output_dir      = settings["output_dir"],
            container       = card.fmt,
            quality         = card.quality,
            ffmpeg_location = settings["ffmpeg_path"],
            parent          = card,
        )
        card.worker = worker
        worker.progress.connect(lambda p, s, e: card.update_progress(p, s, e))
        worker.status.connect(lambda msg: card.status_lbl.setText(msg))
        worker.finished.connect(lambda ok, msg: self._on_download_done(card, ok, msg))
        worker.start()
        card.status_lbl.setText("Downloading…")
        card.status_lbl.setStyleSheet("color:#ffaa00; font-size:11px;")

    def _on_download_done(self, card: QueueCard, success: bool, msg: str):
        card.set_done(success, msg)
        done = sum(1 for c in self._queue_cards
                   if not (c.worker and c.worker.isRunning()))
        total = len(self._queue_cards)
        self._status_bar.showMessage(
            f"{'✔' if success else '✘'} {card.title[:40]} — {msg}  "
            f"({done}/{total} finished)"
        )

    def _clear_done(self):
        to_remove = [c for c in self._queue_cards
                     if not (c.worker and c.worker.isRunning())]
        for c in to_remove:
            self._queue_layout.removeWidget(c)
            c.deleteLater()
            self._queue_cards.remove(c)
        if not self._queue_cards:
            self._empty_lbl.setVisible(True)

    def _pick_folder(self):
        d = QFileDialog.getExistingDirectory(
            self, "Select Output Folder", self._settings["output_dir"]
        )
        if d:
            self._settings["output_dir"] = d
            self._folder_lbl.setText(self._short_path(d))

    def _open_settings(self):
        dlg = SettingsDialog(self)
        if dlg.exec():
            self._settings = load_settings()
            self._folder_lbl.setText(self._short_path(self._settings["output_dir"]))

    @staticmethod
    def _short_path(p: str) -> str:
        home = os.path.expanduser("~")
        if p.startswith(home):
            return "~" + p[len(home):]
        return p if len(p) < 42 else "…" + p[-39:]
