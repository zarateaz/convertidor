import os
import sys

# Configure Qt WebEngine resource paths before importing PyQt6 modules.
# We search both the local user site-packages directory and the system-wide
# directory, preferring the user-local site-packages (since pip install --user
# installs there and Qt WebEngine cannot find it automatically).
major, minor = sys.version_info[:2]
user_site = os.path.expanduser(f"~/.local/lib/python{major}.{minor}/site-packages")
local_pyqt_dir = os.path.join(user_site, "PyQt6")

resources_dir = os.path.join(local_pyqt_dir, "Qt6", "resources")
locales_dir = os.path.join(local_pyqt_dir, "Qt6", "translations", "qtwebengine_locales")

if os.path.exists(resources_dir):
    os.environ["QTWEBENGINE_RESOURCES_PATH"] = resources_dir
else:
    try:
        import PyQt6
        sys_pyqt_dir = os.path.dirname(PyQt6.__file__)
        sys_resources = os.path.join(sys_pyqt_dir, "Qt6", "resources")
        if os.path.exists(sys_resources):
            os.environ["QTWEBENGINE_RESOURCES_PATH"] = sys_resources
    except ImportError:
        pass

if os.path.exists(locales_dir):
    os.environ["QTWEBENGINE_LOCALES_PATH"] = locales_dir
else:
    try:
        import PyQt6
        sys_pyqt_dir = os.path.dirname(PyQt6.__file__)
        sys_locales = os.path.join(sys_pyqt_dir, "Qt6", "translations", "qtwebengine_locales")
        if os.path.exists(sys_locales):
            os.environ["QTWEBENGINE_LOCALES_PATH"] = sys_locales
    except ImportError:
        pass

os.environ["QTWEBENGINE_DISABLE_SANDBOX"] = "1"

from PyQt6.QtWidgets import QApplication, QMainWindow
from PyQt6.QtWebEngineWidgets import QWebEngineView
from PyQt6.QtCore import QUrl

def main():
    app = QApplication(sys.argv)
    window = QMainWindow()
    window.setWindowTitle("NEXUS - Descargador de Medios del Futuro")
    window.resize(1024, 768)
    
    web_view = QWebEngineView()
    web_view.load(QUrl("http://localhost:8000/"))
    window.setCentralWidget(web_view)
    
    window.show()
    sys.exit(app.exec())

if __name__ == "__main__":
    main()
