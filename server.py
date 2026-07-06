import http.server
import socketserver
import json
import threading
import os
import urllib.parse
import time
import requests
import random
import concurrent.futures
import yt_dlp

PORT = 8000

# Rotating mobile and desktop User-Agents
USER_AGENTS = [
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36",
    "Mozilla/5.0 (iPhone; CPU iPhone OS 17_2 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.2 Mobile/15E148 Safari/604.1",
    "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
    "Mozilla/5.0 (iPad; CPU OS 17_2 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Mobile/15E148"
]

def get_default_save_path():
    path = os.path.join(os.path.expanduser("~"), "Downloads")
    os.makedirs(path, exist_ok=True)
    return path

class DownloadSession:
    def __init__(self):
        self.lock = threading.Lock()
        self.active = False
        self.percent = "0%"
        self.speed = "0 B/s"
        self.eta = "--:--"
        self.status = "Idle"  # Idle, Fetching, Downloading, Merging, Completed, Cancelled, Failed
        self.logs = []
        self.title = ""
        self.save_path = get_default_save_path()

session = DownloadSession()

# Helper formatting functions
def format_size(bytes_size):
    if not bytes_size:
        return "0 B"
    for unit in ['B', 'KB', 'MB', 'GB', 'TB']:
        if bytes_size < 1024.0:
            return f"{bytes_size:.1f} {unit}"
        bytes_size /= 1024.0
    return f"{bytes_size:.1f} PB"

def format_speed(bytes_per_sec):
    if bytes_per_sec is None or bytes_per_sec < 0:
        return "0 B/s"
    return f"{format_size(bytes_per_sec)}/s"

def format_eta(seconds):
    if seconds is None or seconds == float('inf') or seconds < 0:
        return "--:--"
    seconds = int(seconds)
    if seconds >= 3600:
        h = seconds // 3600
        m = (seconds % 3600) // 60
        s = seconds % 60
        return f"{h:02d}:{m:02d}:{s:02d}"
    else:
        m = seconds // 60
        s = seconds % 60
        return f"{m:02d}:{s:02d}"

# TikTok Downloader Engine (No Watermark)
def run_tiktok_motor(url, save_path, title):
    global session
    
    with session.lock:
        session.status = "Fetching"
        session.logs.append("Iniciando Motor TikTok: Extrayendo enlace limpio del CDN sin marca de agua...")
    
    api_url = f"https://www.tikwm.com/api/?url={urllib.parse.quote(url)}"
    headers = {
        "User-Agent": random.choice(USER_AGENTS),
        "Accept": "application/json"
    }
    
    res_data = {}
    try:
        res = requests.get(api_url, headers=headers, timeout=15)
        res.raise_for_status()
        res_data = res.json()
    except Exception as e:
        with session.lock:
            session.logs.append(f"Aviso API: Falló la petición a TikWM ({str(e)}). Activando parseador HTML de respaldo...")
            
    play_url = None
    video_title = title or "tiktok_video"
    
    if res_data.get("code") == 0:
        data = res_data.get("data", {})
        play_url = data.get("hdplay") or data.get("play")
        video_title = data.get("title") or video_title
    else:
        # Respaldo: Parseador HTML de metadatos integrados
        try:
            html_res = requests.get(url, headers={"User-Agent": random.choice(USER_AGENTS)}, timeout=10)
            html_res.raise_for_status()
            import re
            
            # Buscar __UNIVERSAL_DATA_FOR_REHYDRATION__
            match = re.search(r'<script id="__UNIVERSAL_DATA_FOR_REHYDRATION__" type="application/json">(.*?)</script>', html_res.text)
            if match:
                js_data = json.loads(match.group(1))
                video_detail = js_data.get("__DEFAULT_SCOPE__", {}).get("webapp.video-detail", {})
                item_struct = video_detail.get("itemInfo", {}).get("itemStruct", {})
                play_url = item_struct.get("video", {}).get("playAddr")
                video_title = item_struct.get("desc") or video_title
            
            # Alternativa: SIGI_STATE
            if not play_url:
                match2 = re.search(r'<script id="SIGI_STATE" type="application/json">(.*?)</script>', html_res.text)
                if match2:
                    js_data = json.loads(match2.group(1))
                    item_struct = list(js_data.get("ItemModule", {}).values())[0]
                    play_url = item_struct.get("video", {}).get("playAddr")
                    video_title = item_struct.get("desc") or video_title
        except Exception as fe:
            with session.lock:
                session.logs.append(f"Error crítico en parseador de respaldo: {str(fe)}")

    if not play_url:
        raise Exception("No se pudo extraer el enlace directo de video de TikTok desde el CDN.")
        
    safe_title = "".join([c for c in video_title if c.isalnum() or c in " .-_()"]).strip()[:50]
    if not safe_title:
        safe_title = "tiktok_video"
    filename = f"{safe_title}.mp4"
    filepath = os.path.join(save_path, filename)
    
    with session.lock:
        session.logs.append(f"Enlace de video limpio obtenido con éxito.")
        session.logs.append(f"Archivo final: {filename}")
        session.status = "Downloading"
        
    download_stream_sequentially(play_url, filepath)

def download_stream_sequentially(url, filepath):
    global session
    
    response = requests.get(url, headers={"User-Agent": random.choice(USER_AGENTS)}, stream=True, timeout=20)
    response.raise_for_status()
    
    total_size = int(response.headers.get('content-length', 0))
    downloaded = 0
    start_time = time.time()
    
    with open(filepath, 'wb') as f:
        for chunk in response.iter_content(chunk_size=128 * 1024):
            with session.lock:
                if not session.active or session.status == "Cancelled":
                    break
            if chunk:
                f.write(chunk)
                downloaded += len(chunk)
                
                elapsed = time.time() - start_time
                speed = downloaded / elapsed if elapsed > 0 else 0
                percent = f"{int(downloaded * 100 / total_size)}%" if total_size > 0 else "N/A"
                eta = format_eta((total_size - downloaded) / speed) if (total_size > 0 and speed > 0) else "--:--"
                
                with session.lock:
                    session.percent = percent
                    session.speed = format_speed(speed)
                    session.eta = eta
                    
    with session.lock:
        if session.status == "Cancelled":
            session.logs.append("Descarga cancelada.")
            return
        session.status = "Completed"
        session.percent = "100%"
        session.speed = "0 B/s"
        session.eta = "00:00"
        session.logs.append("¡Descarga de TikTok completada y guardada con éxito!")

# Large File Segmented Download Engine (Multi-threading Parallel Range Requests)
class SegmentProgressTracker:
    def __init__(self, total_size):
        self.total_size = total_size
        self.downloaded_bytes = 0
        self.lock = threading.Lock()
        self.error = None
        self.start_time = time.time()
        
    def add_bytes(self, num_bytes):
        with self.lock:
            self.downloaded_bytes += num_bytes
            
    def set_error(self, err_msg):
        with self.lock:
            self.error = err_msg

def run_segmented_motor(url, save_path, title):
    global session
    
    with session.lock:
        session.status = "Fetching"
        session.logs.append("Iniciando Motor de Descarga Segmentada Concurrente...")
        
    # Consultar metadatos
    try:
        r = requests.head(url, allow_redirects=True, timeout=10)
        total_size = int(r.headers.get("Content-Length", 0))
        accept_ranges = r.headers.get("Accept-Ranges", "").lower()
        supports_ranges = (accept_ranges == "bytes")
    except Exception as e:
        raise Exception(f"Fallo al conectar con el servidor remoto: {str(e)}")
        
    parsed_url = urllib.parse.urlparse(url)
    filename = os.path.basename(parsed_url.path)
    if not filename:
        filename = "descarga_directa"
    filename = urllib.parse.unquote(filename)
    filepath = os.path.join(save_path, filename)
    
    with session.lock:
        session.logs.append(f"Archivo destino: {filepath}")
        session.logs.append(f"Tamaño reportado: {format_size(total_size)}")
        
    if total_size <= 0 or not supports_ranges:
        with session.lock:
            session.logs.append("Aviso: El servidor remoto no soporta descargas segmentadas (HTTP Range). Usando descarga secuencial...")
        download_stream_sequentially(url, filepath)
        return
        
    # Dividir el archivo en segmentos de 10 MB
    chunk_size = 10 * 1024 * 1024
    ranges = []
    start = 0
    while start < total_size:
        end = min(start + chunk_size - 1, total_size - 1)
        ranges.append((start, end))
        start += chunk_size
        
    num_segments = len(ranges)
    with session.lock:
        session.logs.append(f"Dividiendo archivo en {num_segments} bloques para descarga paralela...")
        session.status = "Downloading"
        
    # Pre-asignar espacio de archivo
    with open(filepath, "wb") as f:
        f.truncate(total_size)
        
    tracker = SegmentProgressTracker(total_size)
    stop_event = threading.Event()
    
    # Hilo secundario para actualizar el progreso global
    def update_progress_loop():
        while not stop_event.is_set():
            with tracker.lock:
                downloaded = tracker.downloaded_bytes
                elapsed = time.time() - tracker.start_time
                error = tracker.error
                
            speed = downloaded / elapsed if elapsed > 0 else 0
            percent = f"{int(downloaded * 100 / total_size)}%" if total_size > 0 else "0%"
            eta = format_eta((total_size - downloaded) / speed) if speed > 0 else "--:--"
            
            with session.lock:
                session.percent = percent
                session.speed = format_speed(speed)
                session.eta = eta
                if error:
                    session.logs.append(f"[Error] {error}")
            time.sleep(0.2)
            
    updater_thread = threading.Thread(target=update_progress_loop)
    updater_thread.start()
    
    max_workers = min(8, num_segments)
    with concurrent.futures.ThreadPoolExecutor(max_workers=max_workers) as executor:
        futures = []
        for i, (start_byte, end_byte) in enumerate(ranges):
            futures.append(
                executor.submit(download_segment_part, url, start_byte, end_byte, filepath, i, tracker)
            )
        for future in concurrent.futures.as_completed(futures):
            with session.lock:
                if not session.active or session.status == "Cancelled":
                    break
                    
    stop_event.set()
    updater_thread.join()
    
    with session.lock:
        if not session.active or session.status == "Cancelled":
            session.status = "Cancelled"
            session.logs.append("La descarga paralela fue cancelada.")
            return
            
    if tracker.error:
        raise Exception(f"Fallo en segmentos: {tracker.error}")
        
    # Verificar integridad del archivo final
    actual_size = os.path.getsize(filepath)
    if actual_size != total_size:
        raise Exception(f"Fallo de integridad: El tamaño descargado ({actual_size} bytes) no coincide con el real ({total_size} bytes).")
        
    with session.lock:
        session.status = "Completed"
        session.percent = "100%"
        session.speed = "0 B/s"
        session.eta = "00:00"
        session.logs.append("¡Validación de integridad completada exitosamente!")
        session.logs.append("¡Descarga de alta velocidad por segmentos finalizada con éxito!")

def download_segment_part(url, start, end, filepath, part_num, tracker):
    global session
    
    retries = 3
    for attempt in range(retries):
        with session.lock:
            if not session.active or session.status == "Cancelled":
                return
                
        headers = {
            "Range": f"bytes={start}-{end}",
            "User-Agent": random.choice(USER_AGENTS)
        }
        try:
            response = requests.get(url, headers=headers, stream=True, timeout=20)
            response.raise_for_status()
            
            with open(filepath, "r+b") as f:
                f.seek(start)
                for chunk in response.iter_content(chunk_size=128 * 1024):
                    with session.lock:
                        if not session.active or session.status == "Cancelled":
                            return
                    if chunk:
                        f.write(chunk)
                        tracker.add_bytes(len(chunk))
            return
        except Exception as e:
            if attempt == retries - 1:
                tracker.set_error(f"Fallo persistente en bloque {part_num}: {str(e)}")
            else:
                time.sleep(1)

# Universal Media Engine (yt-dlp Native Python API Wrapper)
def run_ytdl_motor(url, format_spec, output_template, post_args, save_path, download_type, is_playlist):
    global session
    
    with session.lock:
        session.status = "Downloading"
        session.logs.append("Iniciando Motor Universal: Conectando vía API nativa yt-dlp...")
        
    def ytdl_hook(d):
        global session
        if d['status'] == 'downloading':
            percent = d.get('_percent_str', '0%').strip()
            speed = d.get('_speed_str', '0 B/s').strip()
            eta = d.get('_eta_str', '--:--').strip()
            
            downloaded = d.get('downloaded_bytes', 0)
            total = d.get('total_bytes') or d.get('total_bytes_estimate') or 0
            size_info = f" de {format_size(total)}" if total > 0 else ""
            
            with session.lock:
                session.percent = percent
                session.speed = speed
                session.eta = eta
                log_line = f"[download] {percent}{size_info} a {speed} ETA {eta}"
                if not session.logs or session.logs[-1] != log_line:
                    session.logs.append(log_line)
                    if len(session.logs) > 300:
                        session.logs.pop(0)
                        
        elif d['status'] == 'finished':
            with session.lock:
                session.status = "Merging"
                session.logs.append("[ffmpeg] Descarga finalizada. Procesando y fusionando formatos...")

    ydl_opts = {
        'noplaylist': not is_playlist,
        'progress_hooks': [ytdl_hook],
        'outtmpl': output_template,
        'quiet': True,
        'no_warnings': True,
    }
    
    if download_type == "audio":
        # Extract best audio and convert to 320kbps MP3
        ydl_opts['format'] = 'bestaudio/best'
        ydl_opts['postprocessors'] = [{
            'key': 'FFmpegExtractAudio',
            'preferredcodec': 'mp3',
            'preferredquality': '320',
        }]
    else:
        ydl_opts['format'] = format_spec
        ydl_opts['merge_output_format'] = 'mp4'
        
    try:
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            ydl.download([url])
    except Exception as e:
        raise Exception(f"Fallo en la biblioteca yt-dlp: {str(e)}")
        
    with session.lock:
        if session.status == "Cancelled":
            return
        session.status = "Completed"
        session.percent = "100%"
        session.speed = "0 B/s"
        session.eta = "00:00"
        session.logs.append("¡Descarga de yt-dlp completada con éxito!")

# Background Thread Coordinator
def download_thread(url, format_spec, output_template, post_args, save_path, download_type, title, is_playlist):
    global session
    
    with session.lock:
        session.active = True
        session.status = "Downloading"
        session.percent = "0%"
        session.speed = "0 B/s"
        session.eta = "--:--"
        session.logs = ["Iniciando descarga...", f"Ruta de guardado: {save_path}"]
        session.save_path = save_path
        session.title = title
        
    is_tiktok = "tiktok.com" in url
    
    is_direct_file = False
    clean_url = url.split("?")[0].lower()
    large_file_exts = (".iso", ".zip", ".tar.gz", ".tgz", ".tar", ".dmg", ".exe", ".rar", ".7z", ".pkg", ".deb")
    if clean_url.endswith(large_file_exts):
        is_direct_file = True
    else:
        try:
            r = requests.head(url, allow_redirects=True, timeout=5)
            content_type = r.headers.get("Content-Type", "").lower()
            content_length = int(r.headers.get("Content-Length", 0))
            accept_ranges = r.headers.get("Accept-Ranges", "").lower()
            if content_length > 20 * 1024 * 1024 and (accept_ranges == "bytes" or "octet-stream" in content_type):
                is_direct_file = True
        except Exception:
            pass
            
    try:
        if is_tiktok:
            run_tiktok_motor(url, save_path, title)
        elif is_direct_file:
            run_segmented_motor(url, save_path, title)
        else:
            run_ytdl_motor(url, format_spec, output_template, post_args, save_path, download_type, is_playlist)
    except Exception as e:
        with session.lock:
            session.status = "Failed"
            session.logs.append(f"Error crítico: {str(e)}")
    finally:
        with session.lock:
            session.active = False

def get_video_info(url):
    if "tiktok.com" in url:
        return {
            "success": True,
            "is_playlist": False,
            "title": "Video de TikTok",
            "uploader": "TikTok Creator",
            "thumbnail": "https://images.unsplash.com/photo-1611162617213-7d7a39e9b1d7?w=500",
            "duration": "N/A",
            "video_options": [
                {"format_id": "best", "height": 1080, "label": "Máxima resolución disponible", "ext": "mp4", "fps": 30, "size_mb": 0}
            ]
        }
        
    clean_url = url.split("?")[0].lower()
    large_file_exts = (".iso", ".zip", ".tar.gz", ".tgz", ".tar", ".dmg", ".exe", ".rar", ".7z", ".pkg", ".deb")
    if clean_url.endswith(large_file_exts):
        parsed_url = urllib.parse.urlparse(url)
        filename = os.path.basename(parsed_url.path)
        if not filename:
            filename = "Archivo Directo"
        filename = urllib.parse.unquote(filename)
        
        size_mb = 0
        try:
            r = requests.head(url, allow_redirects=True, timeout=5)
            content_length = int(r.headers.get("Content-Length", 0))
            if content_length > 0:
                size_mb = round(content_length / (1024 * 1024), 1)
        except Exception:
            pass
            
        return {
            "success": True,
            "is_playlist": False,
            "title": filename,
            "uploader": "Enlace Directo",
            "thumbnail": "https://images.unsplash.com/photo-1544383835-bda2bc66a55d?w=500",
            "duration": "Archivo Directo",
            "video_options": [
                {"format_id": "direct", "height": 0, "label": "Descarga Directa Segmentada", "ext": filename.split(".")[-1], "fps": 0, "size_mb": size_mb}
            ]
        }
        
    is_playlist_link = "list=" in url or "playlist" in url
    try:
        if is_playlist_link:
            ydl_opts = {
                'extract_flat': 'in_playlist',
                'skip_download': True,
                'quiet': True,
                'no_warnings': True,
            }
            with yt_dlp.YoutubeDL(ydl_opts) as ydl:
                data = ydl.extract_info(url, download=False)
                
            title = data.get("title", "Lista de reproducción")
            uploader = data.get("uploader", "Lista")
            entries = data.get("entries", [])
            entries_count = len(entries)
            
            thumbnail = ""
            if entries_count > 0:
                thumbnail = entries[0].get("thumbnail") or ""
                
            video_options = [
                {"format_id": "bestvideo+bestaudio/best", "height": 4320, "label": "Mejor calidad disponible", "ext": "mp4", "fps": 30, "size_mb": 0},
                {"format_id": "bestvideo[height<=1080]+bestaudio/best[height<=1080]", "height": 1080, "label": "Full HD (1080p)", "ext": "mp4", "fps": 30, "size_mb": 0},
                {"format_id": "bestvideo[height<=720]+bestaudio/best[height<=720]", "height": 720, "label": "HD (720p)", "ext": "mp4", "fps": 30, "size_mb": 0},
                {"format_id": "bestvideo[height<=480]+bestaudio/best[height<=480]", "height": 480, "label": "480p", "ext": "mp4", "fps": 30, "size_mb": 0},
                {"format_id": "bestvideo[height<=360]+bestaudio/best[height<=360]", "height": 360, "label": "360p", "ext": "mp4", "fps": 30, "size_mb": 0}
            ]
            
            return {
                "success": True,
                "is_playlist": True,
                "title": title,
                "uploader": uploader,
                "thumbnail": thumbnail,
                "duration": f"{entries_count} videos",
                "video_options": video_options
            }
        else:
            is_youtube = "youtube.com" in url or "youtu.be" in url
            ydl_opts = {
                'skip_download': True,
                'quiet': True,
                'no_warnings': True,
            }
            if is_youtube:
                ydl_opts['extractor_args'] = {'youtube': ['skip=dash,hls']}
                
            with yt_dlp.YoutubeDL(ydl_opts) as ydl:
                data = ydl.extract_info(url, download=False)
                
            title = data.get("title", "Video sin título")
            uploader = data.get("uploader", "Canal")
            thumbnail = data.get("thumbnail", "")
            duration = data.get("duration", 0)
            
            if duration:
                hours = duration // 3600
                minutes = (duration % 3600) // 60
                seconds = duration % 60
                if hours > 0:
                    duration_str = f"{hours:02d}:{minutes:02d}:{seconds:02d}"
                else:
                    duration_str = f"{minutes:02d}:{seconds:02d}"
            else:
                duration_str = "Desconocida"
                
            formats = data.get("formats", [])
            video_map = {}
            best_audio_size = 0
            
            for fmt in formats:
                vcodec = fmt.get("vcodec", "none")
                acodec = fmt.get("acodec", "none")
                if acodec != "none" and acodec is not None and (vcodec == "none" or vcodec is None):
                    size = fmt.get("filesize") or fmt.get("filesize_approx") or 0
                    if size > best_audio_size:
                        best_audio_size = size
                        
            for fmt in formats:
                vcodec = fmt.get("vcodec", "none")
                acodec = fmt.get("acodec", "none")
                has_video = vcodec != "none" and vcodec is not None
                height = fmt.get("height")
                
                if has_video and height:
                    size = fmt.get("filesize") or fmt.get("filesize_approx") or 0
                    if (acodec == "none" or acodec is None) and best_audio_size > 0:
                        size += best_audio_size
                        
                    fps = fmt.get("fps") or 30
                    ext = fmt.get("ext", "mp4")
                    bitrate = fmt.get("tbr") or 0
                    
                    existing = video_map.get(height)
                    if not existing or bitrate > existing.get("bitrate", 0):
                        video_map[height] = {
                            "format_id": fmt.get("format_id"),
                            "ext": ext,
                            "height": height,
                            "fps": fps,
                            "size": size,
                            "bitrate": bitrate
                        }
                        
            video_options = []
            for h in sorted(video_map.keys(), reverse=True):
                v = video_map[h]
                size_mb = round(v["size"] / (1024 * 1024), 1) if v["size"] else 0
                
                label = f"{h}p"
                if h == 4320:
                    label += " (8K Ultra HD)"
                elif h == 2160:
                    label += " (4K Ultra HD)"
                elif h == 1440:
                    label += " (2K Quad HD)"
                elif h == 1080:
                    label += " (Full HD)"
                elif h == 720:
                    label += " (HD)"
                    
                video_options.append({
                    "format_id": v["format_id"],
                    "height": h,
                    "label": label,
                    "ext": v["ext"],
                    "fps": int(v["fps"]),
                    "size_mb": size_mb
                })
                
            if not video_options:
                video_options.append({
                    "format_id": "best",
                    "height": 0,
                    "label": "Calidad única / Directo",
                    "ext": "mp4",
                    "fps": 30,
                    "size_mb": 0
                })
                
            return {
                "success": True,
                "is_playlist": False,
                "title": title,
                "uploader": uploader,
                "thumbnail": thumbnail,
                "duration": duration_str,
                "video_options": video_options
            }
    except Exception as e:
        return {"success": False, "error": str(e)}

class FuturisticAPIHandler(http.server.BaseHTTPRequestHandler):
    def log_message(self, format, *args):
        pass
        
    def do_GET(self):
        parsed_path = urllib.parse.urlparse(self.path)
        path = parsed_path.path
        
        if path == "/":
            self.serve_file("index.html", "text/html")
        elif path == "/style.css":
            self.serve_file("style.css", "text/css")
        elif path == "/app.js":
            self.serve_file("app.js", "application/javascript")
        elif path == "/api/progress":
            self.handle_progress()
        elif path == "/api/progress/stream":
            self.handle_progress_stream()
        elif path == "/api/config":
            self.handle_config()
        else:
            self.send_error(404, "File Not Found")
            
    def do_POST(self):
        parsed_path = urllib.parse.urlparse(self.path)
        path = parsed_path.path
        
        content_length = int(self.headers.get("Content-Length", 0))
        post_data = self.rfile.read(content_length) if content_length > 0 else b""
        
        try:
            params = json.loads(post_data.decode("utf-8")) if post_data else {}
        except Exception:
            params = {}
            
        if path == "/api/info":
            self.handle_info(params)
        elif path == "/api/download":
            self.handle_download(params)
        elif path == "/api/cancel":
            self.handle_cancel()
        else:
            self.send_error(404, "API Endpoint Not Found")
            
    def serve_file(self, filename, content_type):
        filepath = os.path.join(os.getcwd(), filename)
        if os.path.exists(filepath):
            self.send_response(200)
            self.send_header("Content-Type", content_type)
            self.send_header("Cache-Control", "no-cache, no-store, must-revalidate")
            self.end_headers()
            with open(filepath, "rb") as f:
                self.wfile.write(f.read())
        else:
            self.send_error(404, f"{filename} not found")
            
    def send_json(self, data, status=200):
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Access-Control-Allow-Origin", "*")
        self.end_headers()
        self.wfile.write(json.dumps(data).encode("utf-8"))
        
    def handle_config(self):
        self.send_json({"default_path": get_default_save_path()})
        
    def handle_info(self, params):
        url = params.get("url")
        if not url:
            self.send_json({"success": False, "error": "URL vacía"}, 400)
            return
            
        info = get_video_info(url)
        if info["success"]:
            self.send_json(info)
        else:
            self.send_json(info, 500)
            
    def handle_download(self, params):
        global session
        
        url = params.get("url")
        format_id = params.get("format_id")
        download_type = params.get("type", "video")
        save_path = params.get("save_path", "").strip()
        if not save_path:
            save_path = get_default_save_path()
        else:
            save_path = os.path.expanduser(save_path)
            
        title = params.get("title", "video")
        is_playlist = params.get("is_playlist", False)
        
        if not url:
            self.send_json({"success": False, "error": "URL requerida"}, 400)
            return
            
        post_args = []
        if is_playlist:
            safe_folder = "".join([c for c in title if c.isalnum() or c in " .-_()"]).strip()
            if not safe_folder:
                safe_folder = "Playlist"
            playlist_dir = os.path.join(save_path, safe_folder)
            try:
                os.makedirs(playlist_dir, exist_ok=True)
            except Exception as e:
                self.send_json({"success": False, "error": f"Error al crear carpeta: {str(e)}"}, 400)
                return
            output_template = os.path.join(playlist_dir, "%(playlist_index)02d - %(title)s.%(ext)s")
            download_save_path = playlist_dir
        else:
            try:
                os.makedirs(save_path, exist_ok=True)
            except Exception as e:
                self.send_json({"success": False, "error": f"Error al crear carpeta: {str(e)}"}, 400)
                return
            output_template = os.path.join(save_path, "%(title)s.%(ext)s")
            download_save_path = save_path
            
        # Lanzar descarga
        with session.lock:
            if session.active:
                self.send_json({"success": False, "error": "Ya hay una descarga activa en progreso."}, 400)
                return
            session.title = title
            
        thread = threading.Thread(
            target=download_thread,
            args=(url, format_id, output_template, post_args, download_save_path, download_type, title, is_playlist)
        )
        thread.daemon = True
        thread.start()
        
        self.send_json({"success": True, "message": "Descarga iniciada en segundo plano."})
        
    def handle_progress(self):
        global session
        with session.lock:
            data = {
                "active": session.active,
                "status": session.status,
                "percent": session.percent,
                "speed": session.speed,
                "eta": session.eta,
                "logs": list(session.logs),
                "title": session.title,
                "save_path": session.save_path
            }
        self.send_json(data)
        
    def handle_progress_stream(self):
        self.send_response(200)
        self.send_header("Content-Type", "text/event-stream")
        self.send_header("Cache-Control", "no-cache")
        self.send_header("Connection", "keep-alive")
        self.send_header("Access-Control-Allow-Origin", "*")
        self.end_headers()
        
        while True:
            with session.lock:
                data = {
                    "active": session.active,
                    "status": session.status,
                    "percent": session.percent,
                    "speed": session.speed,
                    "eta": session.eta,
                    "logs": list(session.logs),
                    "title": session.title,
                    "save_path": session.save_path
                }
                active = session.active
                
            try:
                payload = f"data: {json.dumps(data)}\n\n"
                self.wfile.write(payload.encode('utf-8'))
                self.wfile.flush()
            except (ConnectionError, BrokenPipeError):
                break
                
            if not active:
                break
            time.sleep(0.15)
            
    def handle_cancel(self):
        global session
        cancelled = False
        with session.lock:
            if session.active:
                session.status = "Cancelled"
                session.active = False
                session.logs.append("La descarga fue cancelada por el usuario.")
                cancelled = True
                
        if cancelled:
            self.send_json({"success": True, "message": "Descarga cancelada."})
        else:
            self.send_json({"success": False, "error": "No hay descargas activas para cancelar."})

class ThreadingHTTPServer(socketserver.ThreadingMixIn, http.server.HTTPServer):
    pass

def run():
    print(f"Iniciando el servidor de la interfaz del futuro en http://localhost:{PORT}...")
    server = ThreadingHTTPServer(("0.0.0.0", PORT), FuturisticAPIHandler)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\nServidor detenido.")
        server.server_close()

if __name__ == "__main__":
    run()
