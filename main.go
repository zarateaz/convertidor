package main

import (
	"bufio"
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"log"
	"math"
	"net/http"
	"net/url"
	"os"
	"os/exec"
	"path/filepath"
	"regexp"
	"strconv"
	"strings"
	"sync"
	"time"
)

var PORT = 8000

// Mobile and desktop User-Agents for rotation
var userAgents = []string{
	"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
	"Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36",
	"Mozilla/5.0 (iPhone; CPU iPhone OS 17_2 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.2 Mobile/15E148 Safari/604.1",
	"Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
	"Mozilla/5.0 (iPad; CPU OS 17_2 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Mobile/15E148",
}

func getRandomUserAgent() string {
	return userAgents[time.Now().UnixNano()%int64(len(userAgents))]
}

func getDefaultSavePath() string {
	home, err := os.UserHomeDir()
	if err != nil {
		return "/tmp"
	}
	path := filepath.Join(home, "Downloads")
	os.MkdirAll(path, 0755)
	return path
}

// ─────────────────────────── Cache de metadatos ───────────────────────────
type infoCache struct {
	mu    sync.Mutex
	items map[string]infoCacheItem
}

type infoCacheItem struct {
	data      map[string]interface{}
	cachedAt  time.Time
}

var cache = &infoCache{items: make(map[string]infoCacheItem)}

func (c *infoCache) get(key string) (map[string]interface{}, bool) {
	c.mu.Lock()
	defer c.mu.Unlock()
	if item, ok := c.items[key]; ok {
		if time.Since(item.cachedAt) < 30*time.Minute {
			return item.data, true
		}
		delete(c.items, key)
	}
	return nil, false
}

func (c *infoCache) set(key string, data map[string]interface{}) {
	c.mu.Lock()
	defer c.mu.Unlock()
	if len(c.items) >= 20 {
		for k := range c.items {
			delete(c.items, k)
			break
		}
	}
	c.items[key] = infoCacheItem{data: data, cachedAt: time.Now()}
}

// ─────────────────────────── Sesión de descarga ───────────────────────────
type DownloadSession struct {
	mu       sync.Mutex
	active   bool
	percent  string
	speed    string
	eta      string
	status   string // Idle, Fetching, Downloading, Merging, Completed, Cancelled, Failed
	logs     []string
	title    string
	savePath string
	cancelFn context.CancelFunc
}

var session = DownloadSession{
	percent:  "0%",
	speed:    "0 B/s",
	eta:      "--:--",
	status:   "Idle",
	logs:     []string{},
	savePath: getDefaultSavePath(),
}

type DownloadRequest struct {
	URL        string `json:"url"`
	FormatID   string `json:"format_id"`
	Type       string `json:"type"`
	SavePath   string `json:"save_path"`
	Title      string `json:"title"`
	IsPlaylist bool   `json:"is_playlist"`
}

func runCommand(name string, args ...string) ([]byte, error) {
	cmd := exec.Command(name, args...)
	var stdout, stderr bytes.Buffer
	cmd.Stdout = &stdout
	cmd.Stderr = &stderr
	err := cmd.Run()
	if err != nil {
		return nil, fmt.Errorf("error running %s: %w, stderr: %s", name, err, stderr.String())
	}
	return stdout.Bytes(), nil
}

func getString(m map[string]interface{}, key string, def string) string {
	if v, ok := m[key]; ok {
		if s, ok := v.(string); ok {
			return s
		}
	}
	return def
}

// Helper formatting functions
func formatSize(bytes float64) string {
	units := []string{"B", "KB", "MB", "GB", "TB"}
	i := 0
	for bytes >= 1024 && i < len(units)-1 {
		bytes /= 1024
		i++
	}
	return fmt.Sprintf("%.1f %s", bytes, units[i])
}

func formatSpeed(bytesPerSec float64) string {
	return formatSize(bytesPerSec) + "/s"
}

func formatETA(seconds int) string {
	if seconds < 0 {
		return "--:--"
	}
	if seconds >= 3600 {
		h := seconds / 3600
		m := (seconds % 3600) / 60
		s := seconds % 60
		return fmt.Sprintf("%02d:%02d:%02d", h, m, s)
	}
	m := seconds / 60
	s := seconds % 60
	return fmt.Sprintf("%02d:%02d", m, s)
}

// ─────────────────────────── Motores de Descarga ───────────────────────────

// TikTok Motor (No Watermark API)
func runTikTokMotor(ctx context.Context, urlStr, savePath, title string) error {
	session.mu.Lock()
	session.status = "Fetching"
	session.logs = append(session.logs, "Iniciando Motor TikTok (Go): Consultando API sin marca de agua...")
	session.mu.Unlock()

	apiURL := fmt.Sprintf("https://www.tikwm.com/api/?url=%s", url.QueryEscape(urlStr))

	req, err := http.NewRequestWithContext(ctx, "GET", apiURL, nil)
	if err != nil {
		return err
	}
	req.Header.Set("User-Agent", getRandomUserAgent())
	req.Header.Set("Accept", "application/json")

	client := &http.Client{Timeout: 15 * time.Second}
	resp, err := client.Do(req)
	if err != nil {
		session.mu.Lock()
		session.logs = append(session.logs, fmt.Sprintf("Aviso API TikWM fallida: %v. Intentando fallback de parseo HTML...", err))
		session.mu.Unlock()
	}

	var playURL string
	videoTitle := title
	if videoTitle == "" {
		videoTitle = "tiktok_video"
	}

	if err == nil && resp.StatusCode == 200 {
		defer resp.Body.Close()
		var resJSON map[string]interface{}
		if err := json.NewDecoder(resp.Body).Decode(&resJSON); err == nil {
			if code, ok := resJSON["code"].(float64); ok && code == 0 {
				if data, ok := resJSON["data"].(map[string]interface{}); ok {
					if hd, ok := data["hdplay"].(string); ok && hd != "" {
						playURL = hd
					} else if pl, ok := data["play"].(string); ok && pl != "" {
						playURL = pl
					}
					if t, ok := data["title"].(string); ok && t != "" {
						videoTitle = t
					}
				}
			}
		}
	}

	// Fallback HTML Parse
	if playURL == "" {
		reqHTML, err := http.NewRequestWithContext(ctx, "GET", urlStr, nil)
		if err == nil {
			reqHTML.Header.Set("User-Agent", getRandomUserAgent())
			respHTML, err := client.Do(reqHTML)
			if err == nil {
				defer respHTML.Body.Close()
				htmlBytes, err := io.ReadAll(respHTML.Body)
				if err == nil {
					htmlStr := string(htmlBytes)
					
					// Match __UNIVERSAL_DATA_FOR_REHYDRATION__
					re := regexp.MustCompile(`<script id="__UNIVERSAL_DATA_FOR_REHYDRATION__" type="application/json">(.*?)</script>`)
					match := re.FindStringSubmatch(htmlStr)
					if len(match) > 1 {
						var jsData map[string]interface{}
						if json.Unmarshal([]byte(match[1]), &jsData) == nil {
							if scope, ok := jsData["__DEFAULT_SCOPE__"].(map[string]interface{}); ok {
								if detail, ok := scope["webapp.video-detail"].(map[string]interface{}); ok {
									if item, ok := detail["itemInfo"].(map[string]interface{}); ok {
										if structData, ok := item["itemStruct"].(map[string]interface{}); ok {
											if video, ok := structData["video"].(map[string]interface{}); ok {
												playURL = getString(video, "playAddr", "")
											}
											if desc, ok := structData["desc"].(string); ok && desc != "" {
												videoTitle = desc
											}
										}
									}
								}
							}
						}
					}
				}
			}
		}
	}

	if playURL == "" {
		return fmt.Errorf("no se pudo resolver la URL del video desde el CDN de TikTok")
	}

	safeTitle := ""
	for _, r := range videoTitle {
		if (r >= 'a' && r <= 'z') || (r >= 'A' && r <= 'Z') || (r >= '0' && r <= '9') || strings.ContainsRune(" .-_()", r) {
			safeTitle += string(r)
		}
	}
	safeTitle = strings.TrimSpace(safeTitle)
	if len(safeTitle) > 50 {
		safeTitle = safeTitle[:50]
	}
	if safeTitle == "" {
		safeTitle = "tiktok_video"
	}
	filename := safeTitle + ".mp4"
	filepathDest := filepath.Join(savePath, filename)

	session.mu.Lock()
	session.logs = append(session.logs, fmt.Sprintf("Descargando video TikTok sin marca de agua: %s", filename))
	session.status = "Downloading"
	session.mu.Unlock()

	return downloadStreamSequentially(ctx, playURL, filepathDest)
}

func downloadStreamSequentially(ctx context.Context, urlStr, filepathDest string) error {
	req, err := http.NewRequestWithContext(ctx, "GET", urlStr, nil)
	if err != nil {
		return err
	}
	req.Header.Set("User-Agent", getRandomUserAgent())

	client := &http.Client{}
	resp, err := client.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()

	if resp.StatusCode != 200 {
		return fmt.Errorf("error HTTP al descargar: %s", resp.Status)
	}

	totalSize, _ := strconv.ParseInt(resp.Header.Get("Content-Length"), 10, 64)
	out, err := os.Create(filepathDest)
	if err != nil {
		return err
	}
	defer out.Close()

	buffer := make([]byte, 128*1024)
	var downloaded int64
	startTime := time.Now()

	for {
		select {
		case <-ctx.Done():
			session.mu.Lock()
			session.status = "Cancelled"
			session.mu.Unlock()
			return ctx.Err()
		default:
		}

		n, err := resp.Body.Read(buffer)
		if n > 0 {
			_, werr := out.Write(buffer[:n])
			if werr != nil {
				return werr
			}
			downloaded += int64(n)

			elapsed := time.Since(startTime).Seconds()
			speed := float64(downloaded) / elapsed
			percent := "0%"
			if totalSize > 0 {
				percent = fmt.Sprintf("%d%%", int(downloaded*100/totalSize))
			}
			etaStr := "--:--"
			if speed > 0 && totalSize > 0 {
				etaStr = formatETA(int(float64(totalSize-downloaded) / speed))
			}

			session.mu.Lock()
			session.percent = percent
			session.speed = formatSpeed(speed)
			session.eta = etaStr
			session.mu.Unlock()
		}
		if err == io.EOF {
			break
		}
		if err != nil {
			return err
		}
	}

	session.mu.Lock()
	session.status = "Completed"
	session.percent = "100%"
	session.speed = "0 B/s"
	session.eta = "00:00"
	session.logs = append(session.logs, "¡Descarga de TikTok finalizada con éxito!")
	session.mu.Unlock()

	return nil
}

// Large File Segmented Concurrency Engine
type segmentTracker struct {
	mu              sync.Mutex
	downloadedBytes int64
	errorMsg        string
	startTime       time.Time
}

func runSegmentedMotor(ctx context.Context, urlStr, savePath string) error {
	session.mu.Lock()
	session.status = "Fetching"
	session.logs = append(session.logs, "Iniciando Motor de Descarga Segmentada Paralela...")
	session.mu.Unlock()

	// HEAD call
	client := &http.Client{Timeout: 10 * time.Second}
	req, err := http.NewRequestWithContext(ctx, "HEAD", urlStr, nil)
	if err != nil {
		return err
	}
	resp, err := client.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()

	totalSize, _ := strconv.ParseInt(resp.Header.Get("Content-Length"), 10, 64)
	acceptRanges := strings.ToLower(resp.Header.Get("Accept-Ranges"))
	supportsRanges := acceptRanges == "bytes"

	u, err := url.Parse(urlStr)
	if err != nil {
		return err
	}
	filename := filepath.Base(u.Path)
	if filename == "" || filename == "." || filename == "/" {
		filename = "descarga_directa"
	}
	filename, _ = url.QueryUnescape(filename)
	filepathDest := filepath.Join(savePath, filename)

	session.mu.Lock()
	session.logs = append(session.logs, fmt.Sprintf("Archivo de salida: %s", filepathDest))
	session.logs = append(session.logs, fmt.Sprintf("Tamaño total: %s", formatSize(float64(totalSize))))
	session.mu.Unlock()

	if totalSize <= 0 || !supportsRanges {
		session.mu.Lock()
		session.logs = append(session.logs, "El servidor no admite descargas por rangos. Usando descarga secuencial...")
		session.mu.Unlock()
		return downloadStreamSequentially(ctx, urlStr, filepathDest)
	}

	// 10MB chunk size
	var chunkSize int64 = 10 * 1024 * 1024
	type byteRange struct {
		start int64
		end   int64
	}
	var ranges []byteRange
	var start int64
	for start < totalSize {
		end := start + chunkSize - 1
		if end >= totalSize {
			end = totalSize - 1
		}
		ranges = append(ranges, byteRange{start: start, end: end})
		start += chunkSize
	}

	numSegments := len(ranges)
	session.mu.Lock()
	session.logs = append(session.logs, fmt.Sprintf("Dividiendo en %d segmentos para descarga concurrente...", numSegments))
	session.status = "Downloading"
	session.mu.Unlock()

	// Preallocate file space
	out, err := os.Create(filepathDest)
	if err != nil {
		return err
	}
	defer out.Close()
	if err := out.Truncate(totalSize); err != nil {
		return err
	}

	tracker := &segmentTracker{
		startTime: time.Now(),
	}

	// Sidecar metrics updater
	stopChan := make(chan struct{})
	go func() {
		ticker := time.NewTicker(200 * time.Millisecond)
		defer ticker.Stop()
		for {
			select {
			case <-stopChan:
				return
			case <-ticker.C:
				tracker.mu.Lock()
				dl := tracker.downloadedBytes
				errStr := tracker.errorMsg
				tracker.mu.Unlock()

				elapsed := time.Since(tracker.startTime).Seconds()
				speed := float64(dl) / elapsed
				percent := fmt.Sprintf("%d%%", int(dl*100/totalSize))
				etaStr := "--:--"
				if speed > 0 {
					etaStr = formatETA(int(float64(totalSize-dl) / speed))
				}

				session.mu.Lock()
				session.percent = percent
				session.speed = formatSpeed(speed)
				session.eta = etaStr
				if errStr != "" {
					session.logs = append(session.logs, fmt.Sprintf("[Segmento] Error: %s", errStr))
				}
				session.mu.Unlock()
			}
		}
	}()

	// Concurrent worker execution
	var wg sync.WaitGroup
	semaphore := make(chan struct{}, 8) // Limit concurrent range connections

	for i, r := range ranges {
		wg.Add(1)
		semaphore <- struct{}{}
		go func(partNum int, br byteRange) {
			defer wg.Done()
			defer func() { <-semaphore }()

			var downloadErr error
			for attempt := 0; attempt < 3; attempt++ {
				select {
				case <-ctx.Done():
					return
				default:
				}

				downloadErr = downloadSegmentPart(ctx, urlStr, br.start, br.end, out, tracker)
				if downloadErr == nil {
					return
				}
				time.Sleep(1 * time.Second)
			}

			if downloadErr != nil {
				tracker.mu.Lock()
				tracker.errorMsg = fmt.Sprintf("Error bloque %d: %v", partNum, downloadErr)
				tracker.mu.Unlock()
			}
		}(i, r)
	}

	wg.Wait()
	close(stopChan)

	select {
	case <-ctx.Done():
		session.mu.Lock()
		session.status = "Cancelled"
		session.mu.Unlock()
		return ctx.Err()
	default:
	}

	tracker.mu.Lock()
	hasError := tracker.errorMsg != ""
	tracker.mu.Unlock()

	if hasError {
		return fmt.Errorf("la descarga de segmentos falló: %s", tracker.errorMsg)
	}

	fi, err := os.Stat(filepathDest)
	if err != nil {
		return err
	}
	if fi.Size() != totalSize {
		return fmt.Errorf("fallo de integridad: tamaño descargado %d difiere de real %d", fi.Size(), totalSize)
	}

	session.mu.Lock()
	session.status = "Completed"
	session.percent = "100%"
	session.speed = "0 B/s"
	session.eta = "00:00"
	session.logs = append(session.logs, "¡Descarga segmentada en paralelo finalizada exitosamente!")
	session.mu.Unlock()

	return nil
}

func downloadSegmentPart(ctx context.Context, urlStr string, start, end int64, file *os.File, tracker *segmentTracker) error {
	req, err := http.NewRequestWithContext(ctx, "GET", urlStr, nil)
	if err != nil {
		return err
	}
	req.Header.Set("Range", fmt.Sprintf("bytes=%d-%d", start, end))
	req.Header.Set("User-Agent", getRandomUserAgent())

	client := &http.Client{}
	resp, err := client.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()

	if resp.StatusCode != 206 && resp.StatusCode != 200 {
		return fmt.Errorf("status HTTP inesperado: %s", resp.Status)
	}

	buffer := make([]byte, 128*1024)
	offset := start
	for {
		select {
		case <-ctx.Done():
			return ctx.Err()
		default:
		}

		n, err := resp.Body.Read(buffer)
		if n > 0 {
			_, werr := file.WriteAt(buffer[:n], offset)
			if werr != nil {
				return werr
			}
			offset += int64(n)

			tracker.mu.Lock()
			tracker.downloadedBytes += int64(n)
			tracker.mu.Unlock()
		}
		if err == io.EOF {
			break
		}
		if err != nil {
			return err
		}
	}
	return nil
}

// Universal Engine (yt-dlp wrapper with high-bitrate conversion options)
func runUniversalMotor(ctx context.Context, req DownloadRequest, formatSpec, outputTemplate string, postArgs []string) error {
	session.mu.Lock()
	session.status = "Downloading"
	session.mu.Unlock()

	fSpec := formatSpec
	if fSpec != "" && !strings.Contains(fSpec, "+") && !strings.Contains(fSpec, "best") && !strings.Contains(fSpec, "audio") && !strings.Contains(fSpec, "direct") {
		fSpec = fSpec + "+bestaudio/best"
	}

	cmdArgs := []string{
		"--newline",
		"--progress-template", "download:[PROGRESS] %(progress.downloaded_bytes)s/%(progress.total_bytes_estimate)s | %(progress.speed)s | %(progress.eta)s | %(progress._percent_str)s | %(progress._speed_str)s | %(progress._eta_str)s",
		"-f", fSpec,
		"-o", outputTemplate,
	}

	if _, err := os.Stat("/app/cookies.txt"); err == nil {
		cmdArgs = append([]string{"--cookies", "/app/cookies.txt"}, cmdArgs...)
	}

	if req.Type == "audio" {
		cmdArgs = append(cmdArgs, "-x", "--audio-format", "mp3", "--audio-quality", "320k")
	} else {
		cmdArgs = append(cmdArgs, postArgs...)
	}
	cmdArgs = append(cmdArgs, req.URL)

	cmd := exec.CommandContext(ctx, "yt-dlp", cmdArgs...)
	stdout, err := cmd.StdoutPipe()
	if err != nil {
		return err
	}
	cmd.Stderr = cmd.Stdout

	if err := cmd.Start(); err != nil {
		return err
	}

	scanner := bufio.NewScanner(stdout)
	for scanner.Scan() {
		line := scanner.Text()
		lineStr := strings.TrimSpace(line)
		if lineStr == "" {
			continue
		}

		session.mu.Lock()
		session.logs = append(session.logs, lineStr)
		if len(session.logs) > 300 {
			session.logs = session.logs[1:]
		}

		if strings.HasPrefix(lineStr, "[PROGRESS]") {
			progressData := strings.TrimPrefix(lineStr, "[PROGRESS]")
			parts := strings.Split(progressData, "|")
			if len(parts) >= 6 {
				session.percent = strings.TrimSpace(parts[3])
				session.speed = strings.TrimSpace(parts[4])
				session.eta = strings.TrimSpace(parts[5])
			}
		} else if strings.Contains(lineStr, "[Merger]") || strings.Contains(lineStr, "[ffmpeg]") || strings.Contains(lineStr, "Merging") || strings.Contains(lineStr, "Extracting audio") {
			session.status = "Merging"
		} else if strings.Contains(lineStr, "Destination:") {
			idx := strings.Index(lineStr, "Destination:")
			dest := strings.TrimSpace(lineStr[idx+len("Destination:"):])
			session.logs = append(session.logs, fmt.Sprintf("Archivo de destino: %s", dest))
		}
		session.mu.Unlock()
	}

	exitErr := cmd.Wait()

	session.mu.Lock()
	defer session.mu.Unlock()

	if session.status == "Cancelled" {
		return nil
	}

	if exitErr == nil {
		session.status = "Completed"
		session.percent = "100%"
		session.speed = "0 B/s"
		session.eta = "00:00"
		session.logs = append(session.logs, "¡Descarga de yt-dlp completada con éxito!")
	} else {
		session.status = "Failed"
		session.logs = append(session.logs, fmt.Sprintf("Error: Proceso terminado con error: %v", exitErr))
	}
	return exitErr
}

// ─────────────────────────── Manejador de Coordinación ───────────────────────────
func runDownload(req DownloadRequest, cancel context.CancelFunc) {
	ctx := context.Background()
	var ctxCancel context.Context
	
	session.mu.Lock()
	session.cancelFn = cancel
	session.mu.Unlock()
	
	// Create context linked to request cancellation
	ctxCancel, _ = context.WithCancel(ctx)
	defer cancel()

	var postArgs []string
	var formatSpec string
	var outputTemplate string
	var downloadSavePath string

	isTikTok := strings.Contains(req.URL, "tiktok.com")
	isYouTube := strings.Contains(req.URL, "youtube.com") || strings.Contains(req.URL, "youtu.be")

	// Determine save path
	if req.SavePath == "" {
		req.SavePath = getDefaultSavePath()
	} else if strings.HasPrefix(req.SavePath, "~") {
		home, _ := os.UserHomeDir()
		req.SavePath = filepath.Join(home, strings.TrimPrefix(req.SavePath, "~"))
	}

	if req.IsPlaylist {
		safeFolder := cleanPlaylistTitle(req.Title)
		playlistDir := filepath.Join(req.SavePath, safeFolder)

		if err := os.MkdirAll(playlistDir, 0755); err != nil {
			session.mu.Lock()
			session.status = "Failed"
			session.logs = append(session.logs, fmt.Sprintf("Error creando carpeta playlist: %v", err))
			session.active = false
			session.mu.Unlock()
			return
		}

		outputTemplate = filepath.Join(playlistDir, "%(playlist_index)02d - %(title)s.%(ext)s")
		postArgs = append(postArgs, "--yes-playlist")
		downloadSavePath = playlistDir
	} else {
		if err := os.MkdirAll(req.SavePath, 0755); err != nil {
			session.mu.Lock()
			session.status = "Failed"
			session.logs = append(session.logs, fmt.Sprintf("Error creando directorio de guardado: %v", err))
			session.active = false
			session.mu.Unlock()
			return
		}

		outputTemplate = filepath.Join(req.SavePath, "%(title)s.%(ext)s")
		postArgs = append(postArgs, "--no-playlist")
		downloadSavePath = req.SavePath
	}

	session.mu.Lock()
	session.savePath = downloadSavePath
	session.mu.Unlock()

	// Check if direct file download
	isDirectFile := false
	cleanURL := strings.Split(req.URL, "?")[0]
	largeFileExts := []string{".iso", ".zip", ".tar.gz", ".tgz", ".tar", ".dmg", ".exe", ".rar", ".7z", ".pkg", ".deb"}
	for _, ext := range largeFileExts {
		if strings.HasSuffix(strings.ToLower(cleanURL), ext) {
			isDirectFile = true
			break
		}
	}

	if !isDirectFile && !isTikTok {
		// Run HEAD request
		headReq, err := http.NewRequest("HEAD", req.URL, nil)
		if err == nil {
			headReq.Header.Set("User-Agent", getRandomUserAgent())
			client := &http.Client{Timeout: 5 * time.Second}
			headResp, err := client.Do(headReq)
			if err == nil {
				defer headResp.Body.Close()
				contentType := strings.ToLower(headResp.Header.Get("Content-Type"))
				contentLength, _ := strconv.ParseInt(headResp.Header.Get("Content-Length"), 10, 64)
				acceptRanges := strings.ToLower(headResp.Header.Get("Accept-Ranges"))
				if contentLength > 20*1024*1024 && (acceptRanges == "bytes" || strings.Contains(contentType, "octet-stream")) {
					isDirectFile = true
				}
			}
		}
	}

	var runErr error
	if isTikTok {
		runErr = runTikTokMotor(ctxCancel, req.URL, downloadSavePath, req.Title)
	} else if isDirectFile {
		runErr = runSegmentedMotor(ctxCancel, req.URL, downloadSavePath)
	} else {
		// yt-dlp option configs
		if req.Type == "audio" {
			if req.FormatID == "best_audio_native" {
				formatSpec = "bestaudio/best"
			} else if strings.HasPrefix(req.FormatID, "mp3_") {
				quality := strings.TrimPrefix(req.FormatID, "mp3_") + "k"
				formatSpec = "bestaudio/best"
				postArgs = append(postArgs, "-x", "--audio-format", "mp3", "--audio-quality", quality)
			} else {
				formatSpec = req.FormatID
			}
		} else {
			if isYouTube && req.FormatID != "best" {
				formatSpec = fmt.Sprintf("%s+bestaudio/best", req.FormatID)
			} else {
				formatSpec = req.FormatID
			}
		}
		runErr = runUniversalMotor(ctxCancel, req, formatSpec, outputTemplate, postArgs)
	}

	session.mu.Lock()
	session.active = false
	if runErr != nil && session.status != "Cancelled" {
		session.status = "Failed"
		session.logs = append(session.logs, fmt.Sprintf("Error crítico en descarga: %v", runErr))
	}
	session.mu.Unlock()
}

func getVideoInfo(urlStr string) (map[string]interface{}, error) {
	if cached, ok := cache.get(urlStr); ok {
		return cached, nil
	}

	if strings.Contains(urlStr, "tiktok.com") {
		result := map[string]interface{}{
			"success":     true,
			"is_playlist": false,
			"title":       "Video de TikTok",
			"uploader":    "TikTok Creator",
			"thumbnail":   "https://images.unsplash.com/photo-1611162617213-7d7a39e9b1d7?w=500",
			"duration":    "N/A",
			"video_options": []map[string]interface{}{
				{"format_id": "best", "height": 1080, "label": "Máxima resolución disponible", "ext": "mp4", "fps": 30, "size_mb": 0},
			},
		}
		cache.set(urlStr, result)
		return result, nil
	}

	cleanURL := strings.Split(urlStr, "?")[0]
	largeFileExts := []string{".iso", ".zip", ".tar.gz", ".tgz", ".tar", ".dmg", ".exe", ".rar", ".7z", ".pkg", ".deb"}
	isDirectFile := false
	for _, ext := range largeFileExts {
		if strings.HasSuffix(strings.ToLower(cleanURL), ext) {
			isDirectFile = true
			break
		}
	}

	if isDirectFile {
		u, err := url.Parse(urlStr)
		filename := "Archivo Directo"
		if err == nil {
			filename = filepath.Base(u.Path)
			filename, _ = url.QueryUnescape(filename)
		}
		
		sizeMB := 0.0
		headReq, err := http.NewRequest("HEAD", urlStr, nil)
		if err == nil {
			headReq.Header.Set("User-Agent", getRandomUserAgent())
			client := &http.Client{Timeout: 5 * time.Second}
			headResp, err := client.Do(headReq)
			if err == nil {
				defer headResp.Body.Close()
				contentLength, _ := strconv.ParseInt(headResp.Header.Get("Content-Length"), 10, 64)
				if contentLength > 0 {
					sizeMB = math.Round((float64(contentLength)/(1024*1024))*10) / 10
				}
			}
		}

		result := map[string]interface{}{
			"success":     true,
			"is_playlist": false,
			"title":       filename,
			"uploader":    "Enlace Directo",
			"thumbnail":   "https://images.unsplash.com/photo-1544383835-bda2bc66a55d?w=500",
			"duration":    "Archivo Directo",
			"video_options": []map[string]interface{}{
				{"format_id": "direct", "height": 0, "label": "Descarga Directa Segmentada", "ext": filepath.Ext(filename), "fps": 0, "size_mb": sizeMB},
			},
		}
		cache.set(urlStr, result)
		return result, nil
	}

	isPlaylistLink := strings.Contains(urlStr, "list=") || strings.Contains(urlStr, "playlist")

	var out []byte
	var err error

	if isPlaylistLink {
		args := []string{"--flat-playlist", "-J", "--no-call-home", "--no-warnings", "--socket-timeout", "15"}
		if _, errStat := os.Stat("/app/cookies.txt"); errStat == nil {
			args = append([]string{"--cookies", "/app/cookies.txt"}, args...)
		}
		args = append(args, urlStr)
		out, err = runCommand("yt-dlp", args...)
		if err == nil {
			var data map[string]interface{}
			if err := json.Unmarshal(out, &data); err == nil {
				isPlaylist := false
				if t, ok := data["_type"].(string); ok && t == "playlist" {
					isPlaylist = true
				} else if _, ok := data["entries"]; ok {
					isPlaylist = true
				}

				if isPlaylist {
					title := getString(data, "title", "Mix de YouTube")
					uploader := getString(data, "uploader", "Lista de reproducción")

					var entries []interface{}
					if ent, ok := data["entries"].([]interface{}); ok {
						entries = ent
					}
					entriesCount := len(entries)

					thumbnail := ""
					if entriesCount > 0 {
						if firstEntry, ok := entries[0].(map[string]interface{}); ok {
							if thumb, ok := firstEntry["thumbnail"].(string); ok && thumb != "" {
								thumbnail = thumb
							} else if id, ok := firstEntry["id"].(string); ok && id != "" {
								thumbnail = fmt.Sprintf("https://i.ytimg.com/vi/%s/hqdefault.jpg", id)
							}
						}
					}

					videoOptions := []map[string]interface{}{
						{"format_id": "bestvideo+bestaudio/best", "height": 4320, "label": "Mejor calidad disponible", "ext": "mp4", "fps": 30, "size_mb": 0},
						{"format_id": "bestvideo[height<=1080]+bestaudio/best[height<=1080]", "height": 1080, "label": "Full HD (1080p)", "ext": "mp4", "fps": 30, "size_mb": 0},
						{"format_id": "bestvideo[height<=720]+bestaudio/best[height<=720]", "height": 720, "label": "HD (720p)", "ext": "mp4", "fps": 30, "size_mb": 0},
						{"format_id": "bestvideo[height<=480]+bestaudio/best[height<=480]", "height": 480, "label": "480p", "ext": "mp4", "fps": 30, "size_mb": 0},
						{"format_id": "bestvideo[height<=360]+bestaudio/best[height<=360]", "height": 360, "label": "360p", "ext": "mp4", "fps": 30, "size_mb": 0},
					}

					result := map[string]interface{}{
						"success":       true,
						"is_playlist":   true,
						"title":         title,
						"uploader":      uploader,
						"thumbnail":     thumbnail,
						"duration":      fmt.Sprintf("%d videos", entriesCount),
						"video_options": videoOptions,
					}
					cache.set(urlStr, result)
					return result, nil
				}
			}
		}
	}

	ytdlpArgs := []string{
		"-J",
		"-f", "all",
		"--no-playlist",
		"--no-call-home",
		"--no-warnings",
		"--extractor-args", "youtube:player_client=android,web",
		"--socket-timeout", "15",
	}
	if _, errStat := os.Stat("/app/cookies.txt"); errStat == nil {
		ytdlpArgs = append([]string{"--cookies", "/app/cookies.txt"}, ytdlpArgs...)
	}

	ytdlpArgs = append(ytdlpArgs, urlStr)
	out, err = runCommand("yt-dlp", ytdlpArgs...)
	if err != nil {
		return nil, err
	}

	var data map[string]interface{}
	if err := json.Unmarshal(out, &data); err != nil {
		return nil, err
	}

	title := getString(data, "title", "Video sin título")
	uploader := getString(data, "uploader", "Canal/Usuario")
	thumbnail := getString(data, "thumbnail", "")

	durationStr := "Desconocida"
	if durVal, ok := data["duration"]; ok {
		var duration float64
		switch v := durVal.(type) {
		case float64:
			duration = v
		case int:
			duration = float64(v)
		}
		if duration > 0 {
			durSec := int(duration)
			hours := durSec / 3600
			minutes := (durSec % 3600) / 60
			seconds := durSec % 60
			if hours > 0 {
				durationStr = fmt.Sprintf("%02d:%02d:%02d", hours, minutes, seconds)
			} else {
				durationStr = fmt.Sprintf("%02d:%02d", minutes, seconds)
			}
		}
	}

	var formats []interface{}
	if fList, ok := data["formats"].([]interface{}); ok {
		formats = fList
	}

	type FormatItem struct {
		FormatID string  `json:"format_id"`
		Ext      string  `json:"ext"`
		Height   int     `json:"height"`
		FPS      float64 `json:"fps"`
		Size     float64 `json:"size"`
		Bitrate  float64 `json:"bitrate"`
	}

	var bestAudioSize float64
	for _, fVal := range formats {
		if fmtMap, ok := fVal.(map[string]interface{}); ok {
			acodec, _ := fmtMap["acodec"].(string)
			vcodec, _ := fmtMap["vcodec"].(string)
			hasAudio := acodec != "" && acodec != "none"
			hasVideo := vcodec != "" && vcodec != "none"
			if hasAudio && !hasVideo {
				var size float64
				if sVal, ok := fmtMap["filesize"].(float64); ok {
					size = sVal
				} else if sVal, ok := fmtMap["filesize_approx"].(float64); ok {
					size = sVal
				}
				if size > bestAudioSize {
					bestAudioSize = size
				}
			}
		}
	}

	videoMap := make(map[int]FormatItem)
	for _, fVal := range formats {
		if fmtMap, ok := fVal.(map[string]interface{}); ok {
			vcodec, _ := fmtMap["vcodec"].(string)
			acodec, _ := fmtMap["acodec"].(string)
			hasVideo := vcodec != "" && vcodec != "none"

			var height int
			if hVal, ok := fmtMap["height"].(float64); ok {
				height = int(hVal)
			}

			if hasVideo && height > 0 {
				var size float64
				if sVal, ok := fmtMap["filesize"].(float64); ok {
					size = sVal
				} else if sVal, ok := fmtMap["filesize_approx"].(float64); ok {
					size = sVal
				}

				hasAudio := acodec != "" && acodec != "none"
				if !hasAudio && bestAudioSize > 0 {
					size += bestAudioSize
				}

				fps := 30.0
				if fpsVal, ok := fmtMap["fps"].(float64); ok {
					fps = fpsVal
				}
				ext, _ := fmtMap["ext"].(string)
				if ext == "" {
					ext = "mp4"
				}
				bitrate := 0.0
				if tbrVal, ok := fmtMap["tbr"].(float64); ok {
					bitrate = tbrVal
				}
				fmtID, _ := fmtMap["format_id"].(string)

				existing, exists := videoMap[height]
				if !exists || bitrate > existing.Bitrate {
					videoMap[height] = FormatItem{
						FormatID: fmtID,
						Ext:      ext,
						Height:   height,
						FPS:      fps,
						Size:     size,
						Bitrate:  bitrate,
					}
				}
			}
		}
	}

	qualityTiers := []struct {
		height int
		label  string
	}{
		{4320, "8K Ultra HD"},
		{2160, "4K Ultra HD"},
		{1440, "2K Quad HD"},
		{1080, "Full HD"},
		{720, "HD"},
		{480, "480p"},
		{360, "360p"},
		{240, "240p"},
	}

	var videoOptions []map[string]interface{}
	for _, tier := range qualityTiers {
		h := tier.height
		v, exists := videoMap[h]
		sizeMB := 0.0
		fps := 30
		if exists && v.Size > 0 {
			sizeMB = math.Round((v.Size/(1024*1024))*10) / 10
			fps = int(v.FPS)
		}
		// Always show standard tiers regardless of detected formats.
		// If YouTube blocked format access (expired cookies), we still offer
		// quality options and let yt-dlp resolve them at download time.
		formatSpec := fmt.Sprintf("bestvideo[height=%d]+bestaudio/bestvideo[height<=%d]+bestaudio/best[height<=%d]/best", h, h, h)
		label := fmt.Sprintf("%dp (%s)", h, tier.label)
		videoOptions = append(videoOptions, map[string]interface{}{
			"format_id": formatSpec,
			"height":    h,
			"label":     label,
			"ext":       "mp4",
			"fps":       fps,
			"size_mb":   sizeMB,
		})
	}

	if len(videoOptions) == 0 {
		videoOptions = append(videoOptions, map[string]interface{}{
			"format_id": "bestvideo+bestaudio/best",
			"height":    0,
			"label":     "Mejor calidad disponible",
			"ext":       "mp4",
			"fps":       30,
			"size_mb":   0,
		})
	}

	result := map[string]interface{}{
		"success":       true,
		"is_playlist":   false,
		"title":         title,
		"uploader":      uploader,
		"thumbnail":     thumbnail,
		"duration":      durationStr,
		"video_options": videoOptions,
	}
	cache.set(urlStr, result)
	return result, nil
}

func cleanPlaylistTitle(title string) string {
	var sb strings.Builder
	for _, r := range title {
		if (r >= 'a' && r <= 'z') || (r >= 'A' && r <= 'Z') || (r >= '0' && r <= '9') || strings.ContainsRune(" .-_()", r) {
			sb.WriteRune(r)
		}
	}
	res := strings.TrimSpace(sb.String())
	if res == "" {
		res = "Playlist"
	}
	return res
}

func handleInfo(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Error(w, "Method Not Allowed", http.StatusMethodNotAllowed)
		return
	}

	var req struct {
		URL string `json:"url"`
	}
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, "Bad Request", http.StatusBadRequest)
		return
	}

	if req.URL == "" {
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusBadRequest)
		json.NewEncoder(w).Encode(map[string]interface{}{"success": false, "error": "URL vacía"})
		return
	}

	info, err := getVideoInfo(req.URL)
	w.Header().Set("Content-Type", "application/json")
	if err != nil {
		json.NewEncoder(w).Encode(map[string]interface{}{"success": false, "error": err.Error()})
		return
	}

	json.NewEncoder(w).Encode(info)
}

func handleDownload(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Error(w, "Method Not Allowed", http.StatusMethodNotAllowed)
		return
	}

	var req DownloadRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, "Bad Request", http.StatusBadRequest)
		return
	}

	if req.URL == "" {
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusBadRequest)
		json.NewEncoder(w).Encode(map[string]interface{}{"success": false, "error": "URL requerida"})
		return
	}

	session.mu.Lock()
	if session.active {
		session.mu.Unlock()
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusBadRequest)
		json.NewEncoder(w).Encode(map[string]interface{}{"success": false, "error": "Ya hay una descarga activa en progreso."})
		return
	}

	_, cancel := context.WithCancel(context.Background())

	session.active = true
	session.status = "Downloading"
	session.percent = "0%"
	session.speed = "0 B/s"
	session.eta = "--:--"
	session.logs = []string{"Iniciando descarga...", fmt.Sprintf("Ruta de guardado: %s", req.SavePath)}
	session.title = req.Title
	session.savePath = req.SavePath
	session.cancelFn = cancel
	session.mu.Unlock()

	go runDownload(req, cancel)

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]interface{}{"success": true, "message": "Descarga iniciada en segundo plano."})
}

func handleCancel(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Error(w, "Method Not Allowed", http.StatusMethodNotAllowed)
		return
	}

	session.mu.Lock()
	defer session.mu.Unlock()

	if session.active && session.cancelFn != nil {
		session.status = "Cancelled"
		session.logs = append(session.logs, "La descarga fue cancelada por el usuario.")
		session.cancelFn()
		session.active = false
		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(map[string]interface{}{"success": true, "message": "Descarga cancelada."})
	} else {
		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(map[string]interface{}{"success": false, "error": "No hay descargas activas para cancelar."})
	}
}

func handleProgress(w http.ResponseWriter, r *http.Request) {
	session.mu.Lock()
	defer session.mu.Unlock()

	data := map[string]interface{}{
		"active":    session.active,
		"status":    session.status,
		"percent":   session.percent,
		"speed":     session.speed,
		"eta":       session.eta,
		"logs":      session.logs,
		"title":     session.title,
		"save_path": session.savePath,
	}

	w.Header().Set("Content-Type", "application/json")
	w.Header().Set("Access-Control-Allow-Origin", "*")
	json.NewEncoder(w).Encode(data)
}

func handleProgressStream(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "text/event-stream")
	w.Header().Set("Cache-Control", "no-cache")
	w.Header().Set("Connection", "keep-alive")
	w.Header().Set("Access-Control-Allow-Origin", "*")

	flusher, ok := w.(http.Flusher)
	if !ok {
		http.Error(w, "Streaming unsupported", http.StatusInternalServerError)
		return
	}

	for {
		session.mu.Lock()
		data := map[string]interface{}{
			"active":    session.active,
			"status":    session.status,
			"percent":   session.percent,
			"speed":     session.speed,
			"eta":       session.eta,
			"logs":      session.logs,
			"title":     session.title,
			"save_path": session.savePath,
		}
		active := session.active
		session.mu.Unlock()

		payload, err := json.Marshal(data)
		if err == nil {
			fmt.Fprintf(w, "data: %s\n\n", payload)
			flusher.Flush()
		}

		if !active {
			break
		}

		time.Sleep(150 * time.Millisecond)
	}
}

func handleConfig(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "application/json")
	w.Header().Set("Access-Control-Allow-Origin", "*")
	json.NewEncoder(w).Encode(map[string]interface{}{
		"default_path": getDefaultSavePath(),
	})
}

func startGUI() {
	fmt.Println("Abriendo la interfaz del futuro en una ventana nativa de escritorio...")
	cmd := exec.Command("python3", "gui.py")
	err := cmd.Run()
	if err != nil {
		fmt.Printf("Error al iniciar la interfaz nativa PyQt6: %v. Abriendo navegador por defecto...\n", err)
		exec.Command("xdg-open", fmt.Sprintf("http://localhost:%d/", PORT)).Run()
	}
}

func main() {
	if portStr := os.Getenv("PORT"); portStr != "" {
		if p, err := strconv.Atoi(portStr); err == nil {
			PORT = p
		}
	}
	// Register static handlers
	http.HandleFunc("/", func(w http.ResponseWriter, r *http.Request) {
		path := r.URL.Path
		if path == "/" {
			http.ServeFile(w, r, "index.html")
			return
		}
		if path == "/style.css" {
			http.ServeFile(w, r, "style.css")
			return
		}
		if path == "/app.js" {
			http.ServeFile(w, r, "app.js")
			return
		}
		http.NotFound(w, r)
	})

	// Register API handlers
	http.HandleFunc("/api/info", handleInfo)
	http.HandleFunc("/api/download", handleDownload)
	http.HandleFunc("/api/cancel", handleCancel)
	http.HandleFunc("/api/progress", handleProgress)
	http.HandleFunc("/api/progress/stream", handleProgressStream)
	http.HandleFunc("/api/config", handleConfig)

	server := &http.Server{Addr: fmt.Sprintf("0.0.0.0:%d", PORT)}

	go func() {
		fmt.Printf("Iniciando el servidor de la interfaz del futuro en http://localhost:%d...\n", PORT)
		if err := server.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			log.Fatalf("Error del servidor: %v", err)
		}
	}()

	// Wait briefly for port binding
	time.Sleep(200 * time.Millisecond)

	// Launch PyQt6 GUI Wrapper
	startGUI()

	// Shut down server after browser closes
	fmt.Println("Apagando el servidor local...")
	ctx, cancel := context.WithTimeout(context.Background(), 3*time.Second)
	defer cancel()
	server.Shutdown(ctx)
	fmt.Println("Nexus Downloader cerrado.")
}
