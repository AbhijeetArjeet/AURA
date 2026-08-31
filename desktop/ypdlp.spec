# -*- mode: python ; coding: utf-8 -*-
# YPDlp PyInstaller spec file
import os
import sys

datas = []
binaries = []

# Include imageio-ffmpeg binary if present
try:
    import imageio_ffmpeg
    ff_exe = imageio_ffmpeg.get_ffmpeg_exe()
    if ff_exe and os.path.isfile(ff_exe):
        binaries.append((ff_exe, '.'))
        binaries.append((ff_exe, 'bin'))
except Exception as e:
    print(f"Note: imageio_ffmpeg binary detection: {e}")

# Include local bin/ffmpeg.exe if present
local_ffmpeg = os.path.join(os.getcwd(), 'bin', 'ffmpeg.exe')
if os.path.isfile(local_ffmpeg):
    binaries.append((local_ffmpeg, '.'))
    binaries.append((local_ffmpeg, 'bin'))

# Include certifi certificates bundle
try:
    import certifi
    datas.append((certifi.where(), 'certifi'))
except Exception:
    pass

a = Analysis(
    ['main.py'],
    pathex=[],
    binaries=binaries,
    datas=datas,
    hiddenimports=[
        'PyQt6',
        'PyQt6.QtWidgets',
        'PyQt6.QtGui',
        'PyQt6.QtCore',
        'yt_dlp',
        'yt_dlp.extractor',
        'yt_dlp.downloader',
        'yt_dlp.postprocessor',
        'requests',
        'PIL',
        'PIL.Image',
        'imageio_ffmpeg',
        'certifi',
        'urllib3',
        'ffmpeg_utils',
    ],
    hookspath=[],
    hooksconfig={},
    runtime_hooks=[],
    excludes=[],
    noarchive=False,
)

pyz = PYZ(a.pure)

exe = EXE(
    pyz,
    a.scripts,
    [],
    exclude_binaries=True,
    name='YPDlp',
    debug=False,
    bootloader_ignore_signals=False,
    strip=False,
    upx=False,
    console=False,                # No console window
    disable_windowed_traceback=False,
    argv_emulation=False,
    target_arch=None,
    codesign_identity=None,
    entitlements_file=None,
)

coll = COLLECT(
    exe,
    a.binaries,
    a.datas,
    strip=False,
    upx=False,
    upx_exclude=[],
    name='YPDlp',
)
