import subprocess
import time
import os
import sys
import socket

PORT = 8000

def is_port_in_use(port):
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
        return s.connect_ex(('localhost', port)) == 0

def main():
    current_dir = os.path.dirname(os.path.abspath(__file__))
    server_script = os.path.join(current_dir, "server.py")
    
    server_process = None
    
    # 1. Start the server if it's not already running
    if not is_port_in_use(PORT):
        print("Iniciando el servidor local del descargador...")
        server_process = subprocess.Popen([sys.executable, server_script], cwd=current_dir)
        
        # Wait for the server to spin up and bind the port
        for _ in range(30):
            if is_port_in_use(PORT):
                break
            time.sleep(0.1)
    else:
        print("El servidor local ya se encuentra en ejecución.")

    gui_script = os.path.join(current_dir, "gui.py")
    print("Abriendo la interfaz del futuro en una ventana nativa de escritorio...")
    
    try:
        # Run the PyQt6 window and block execution until the user closes it
        subprocess.run([sys.executable, gui_script], cwd=current_dir)
    except Exception as e:
        print(f"Error al abrir la interfaz nativa PyQt6: {e}")
        # Standard browser open fallback in case PyQt6 fails
        import webbrowser
        webbrowser.open(f"http://localhost:{PORT}/")
        time.sleep(5) # Let it load
        
    # 3. Clean up the server process when the application window is closed
    if server_process:
        print("Cerrando el servidor local...")
        server_process.terminate()
        try:
            server_process.wait(timeout=3)
        except subprocess.TimeoutExpired:
            server_process.kill()
            
    print("Nexus Downloader cerrado.")

if __name__ == "__main__":
    main()
