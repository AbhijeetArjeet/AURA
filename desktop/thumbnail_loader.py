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
            headers = {
                "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
            }
            resp = requests.get(self.url, headers=headers, timeout=12)
            resp.raise_for_status()
            img = Image.open(BytesIO(resp.content)).convert("RGB")
            img = img.resize((320, 180), Image.LANCZOS)
            data = img.tobytes("raw", "RGB")
            qimg = QImage(data, img.width, img.height, img.width * 3, QImage.Format.Format_RGB888)
            pix = QPixmap.fromImage(qimg.copy())
            self.loaded.emit(pix)
        except Exception:
            self.failed.emit()
