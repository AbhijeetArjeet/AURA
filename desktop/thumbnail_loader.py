"""
thumbnail_loader.py — async thumbnail fetch + conversion to QPixmap
"""
import requests
from io import BytesIO
from PIL import Image
from PyQt6.QtGui import QPixmap, QImage
from PyQt6.QtCore import QThread, pyqtSignal


class ThumbnailLoader(QThread):
    """Download a thumbnail URL and emit a QPixmap."""
    loaded = pyqtSignal(QPixmap)
    failed = pyqtSignal()

    def __init__(self, url: str, parent=None):
        super().__init__(parent)
        self.url = url

    def run(self):
        try:
            resp = requests.get(self.url, timeout=10)
            resp.raise_for_status()
            img = Image.open(BytesIO(resp.content)).convert("RGB")
            img = img.resize((320, 180), Image.LANCZOS)
            data = img.tobytes("raw", "RGB")
            qimg = QImage(data, img.width, img.height, QImage.Format.Format_RGB888)
            self.loaded.emit(QPixmap.fromImage(qimg))
        except Exception:
            self.failed.emit()
