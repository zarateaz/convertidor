document.addEventListener("DOMContentLoaded", () => {
    // State Variables
    let activeUrl = "";
    let activeTitle = "";
    let selectedType = "video"; // "video" or "audio"
    let selectedFormatId = "";
    let isPlaylistActive = false;
    let pollingInterval = null;
    let eventSource = null;
    let lastLogLength = 0;

    // DOM Elements
    const youtubeUrlInput = document.getElementById("youtubeUrl");
    const savePathInput = document.getElementById("savePath");
    const btnRestorePath = document.getElementById("btnRestorePath");
    const btnScan = document.getElementById("btnScan");
    const scanError = document.getElementById("scanError");

    const mediaDetailsPanel = document.getElementById("mediaDetailsPanel");
    const videoThumbnail = document.getElementById("videoThumbnail");
    const videoDuration = document.getElementById("videoDuration");
    const videoTitle = document.getElementById("videoTitle");
    const videoUploader = document.getElementById("videoUploader");

    const tabVideo = document.getElementById("tabVideo");
    const tabAudio = document.getElementById("tabAudio");
    const videoContent = document.getElementById("videoContent");
    const audioContent = document.getElementById("audioContent");

    const videoFormatGrid = document.getElementById("videoFormatGrid");
    const audioFormatGrid = document.getElementById("audioFormatGrid");

    const btnDownload = document.getElementById("btnDownload");
    const progressPanel = document.getElementById("progressPanel");
    const downloadingTitle = document.getElementById("downloadingTitle");
    const btnCancel = document.getElementById("btnCancel");

    const progressPercent = document.getElementById("progressPercent");
    const progressSpeed = document.getElementById("progressSpeed");
    const progressEta = document.getElementById("progressEta");
    const progressBarFill = document.getElementById("progressBarFill");
    const terminalLog = document.getElementById("terminalLog");

    // Load saved path from local storage or set default from backend
    const savedPath = localStorage.getItem("nexus_save_path");
    if (savedPath) {
        savePathInput.value = savedPath;
    } else {
        fetch("/api/config")
            .then(res => res.json())
            .then(data => {
                if (data.default_path) {
                    savePathInput.value = data.default_path;
                    localStorage.setItem("nexus_save_path", data.default_path);
                } else {
                    savePathInput.value = "~/Downloads";
                }
            })
            .catch(() => {
                savePathInput.value = "~/Downloads";
            });
    }

    // Save path on edit
    savePathInput.addEventListener("change", () => {
        const path = savePathInput.value.trim();
        if (path) {
            localStorage.setItem("nexus_save_path", path);
        }
    });

    // Update folder notice reactively as path is typed
    savePathInput.addEventListener("input", () => {
        const folderNotice = document.getElementById("folderNotice");
        if (isPlaylistActive && folderNotice.style.display !== "none") {
            const savePath = savePathInput.value.trim() || "~/Downloads";
            folderNotice.textContent = `Se guardará en: ${savePath}/${activeTitle}/`;
        }
    });

    // Restore default path
    btnRestorePath.addEventListener("click", () => {
        fetch("/api/config")
            .then(res => res.json())
            .then(data => {
                if (data.default_path) {
                    savePathInput.value = data.default_path;
                    localStorage.setItem("nexus_save_path", data.default_path);
                } else {
                    savePathInput.value = "~/Downloads";
                    localStorage.setItem("nexus_save_path", "~/Downloads");
                }
            })
            .catch(() => {
                savePathInput.value = "~/Downloads";
                localStorage.setItem("nexus_save_path", "~/Downloads");
            });
    });

    // Cookies Management
    const btnToggleCookies = document.getElementById("btnToggleCookies");
    const cookiesContainer = document.getElementById("cookiesContainer");
    const cookiesTextarea = document.getElementById("cookiesTextarea");
    const btnSaveCookies = document.getElementById("btnSaveCookies");
    const cookiesStatus = document.getElementById("cookiesStatus");

    btnToggleCookies.addEventListener("click", () => {
        if (cookiesContainer.style.display === "none") {
            cookiesContainer.style.display = "flex";
            btnToggleCookies.textContent = "OCULTAR PANEL";
            loadCookiesStatus();
        } else {
            cookiesContainer.style.display = "none";
            updateCookiesButtonStatus();
        }
    });

    async function loadCookiesStatus() {
        try {
            cookiesStatus.textContent = "Consultando VPS...";
            const res = await fetch("/api/cookies");
            const data = await res.json();
            if (data.exists) {
                cookiesStatus.textContent = `Estado: ACTIVO (${data.youtube_cookies} de YouTube, ${data.total_cookies} en total)`;
                cookiesStatus.style.color = "var(--neon-cyan)";
            } else {
                cookiesStatus.textContent = "Estado: NO DETECTADO (Bypass inactivo)";
                cookiesStatus.style.color = "#ff4444";
            }
        } catch (err) {
            cookiesStatus.textContent = "Error al consultar estado de cookies.";
            cookiesStatus.style.color = "#ff4444";
        }
    }

    function updateCookiesButtonStatus() {
        fetch("/api/cookies")
            .then(res => res.json())
            .then(data => {
                if (data.exists) {
                    btnToggleCookies.textContent = `EDITAR COOKIES (${data.youtube_cookies})`;
                } else {
                    btnToggleCookies.textContent = "CONFIGURAR COOKIES";
                }
            })
            .catch(() => {});
    }

    btnSaveCookies.addEventListener("click", async () => {
        const text = cookiesTextarea.value.trim();
        if (!text) {
            alert("Por favor, ingresa el texto de las cookies.");
            return;
        }

        btnSaveCookies.disabled = true;
        btnSaveCookies.textContent = "GUARDANDO EN VPS...";
        cookiesStatus.textContent = "Escribiendo archivo de forma segura...";
        cookiesStatus.style.color = "var(--neon-cyan)";

        try {
            const response = await fetch("/api/cookies", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ cookies: text })
            });
            const data = await response.json();
            if (data.success) {
                cookiesStatus.textContent = "¡Cookies guardadas y activadas con éxito!";
                cookiesStatus.style.color = "#39ff14";
                cookiesTextarea.value = "";
                setTimeout(() => {
                    cookiesContainer.style.display = "none";
                    updateCookiesButtonStatus();
                }, 1500);
            } else {
                cookiesStatus.textContent = "Error: " + (data.error || "No se pudo guardar.");
                cookiesStatus.style.color = "#ff4444";
            }
        } catch (err) {
            cookiesStatus.textContent = "Error de conexión al guardar cookies.";
            cookiesStatus.style.color = "#ff4444";
        } finally {
            btnSaveCookies.disabled = false;
            btnSaveCookies.textContent = "GUARDAR COOKIES EN EL VPS";
        }
    });

    // Initial check
    updateCookiesButtonStatus();


    // Toggle between tabs (Video vs Audio)
    tabVideo.addEventListener("click", () => {
        selectedType = "video";
        tabVideo.classList.add("active");
        tabAudio.classList.remove("active");
        videoContent.style.display = "block";
        audioContent.style.display = "none";
        
        // Select first video format card
        const firstCard = videoFormatGrid.querySelector(".format-card");
        if (firstCard) {
            selectFormatCard(firstCard);
        }
    });

    tabAudio.addEventListener("click", () => {
        selectedType = "audio";
        tabAudio.classList.add("active");
        tabVideo.classList.remove("active");
        audioContent.style.display = "block";
        videoContent.style.display = "none";
        
        // Select active audio card
        const activeCard = audioFormatGrid.querySelector(".format-card.active") || audioFormatGrid.querySelector(".format-card");
        if (activeCard) {
            selectFormatCard(activeCard);
        }
    });

    // Helper to select a format card
    function selectFormatCard(cardElement) {
        const grid = cardElement.parentElement;
        grid.querySelectorAll(".format-card").forEach(c => c.classList.remove("active"));
        cardElement.classList.add("active");
        selectedFormatId = cardElement.dataset.format;
    }

    // Bind event listeners for static audio cards
    audioFormatGrid.querySelectorAll(".format-card").forEach(card => {
        card.addEventListener("click", () => {
            if (selectedType === "audio") {
                selectFormatCard(card);
            }
        });
    });

    // SCAN URL FOR INFO
    btnScan.addEventListener("click", async () => {
        const url = youtubeUrlInput.value.trim();
        if (!url) {
            showScanError("Por favor, introduce un enlace válido de YouTube, TikTok, Instagram o Facebook.");
            return;
        }

        // Reset UI state
        clearScanError();
        setScanLoadingState(true);
        mediaDetailsPanel.style.display = "none";

        try {
            const response = await fetch("/api/info", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ url: url })
            });
            const data = await response.json();

            if (data.success) {
                activeUrl = url;
                activeTitle = data.title;
                isPlaylistActive = !!data.is_playlist;
                
                // Toggle playlist badge and subfolder notice
                const playlistBadge = document.getElementById("playlistBadge");
                const folderNotice = document.getElementById("folderNotice");
                if (isPlaylistActive) {
                    playlistBadge.style.display = "inline-block";
                    folderNotice.style.display = "block";
                    const savePath = savePathInput.value.trim() || "/home/zarate";
                    folderNotice.textContent = `Se guardará en: ${savePath}/${data.title}/`;
                } else {
                    playlistBadge.style.display = "none";
                    folderNotice.style.display = "none";
                }
                
                // Populate metadata
                videoThumbnail.src = data.thumbnail || "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?w=500";
                videoDuration.textContent = data.duration;
                videoTitle.textContent = data.title;
                videoUploader.textContent = data.uploader;

                // Populate Video formats
                populateVideoFormats(data.video_options);
                
                // Show content panels
                mediaDetailsPanel.style.display = "block";
                
                // Default tab and format selection
                if (selectedType === "video") {
                    tabVideo.click();
                } else {
                    tabAudio.click();
                }
                
                // Smooth scroll to details
                mediaDetailsPanel.scrollIntoView({ behavior: "smooth" });
            } else {
                showScanError("Error del servidor: " + (data.error || "No se pudo extraer información del video."));
            }
        } catch (err) {
            showScanError("Error de conexión: No se pudo comunicar con el backend local.");
            console.error(err);
        } finally {
            setScanLoadingState(false);
        }
    });

    let scanMsgInterval = null;

    function setScanLoadingState(isLoading) {
        const messages = [
            "CONTACTANDO SERVIDOR...",
            "EXTRAYENDO METADATOS...",
            "ANALIZANDO FORMATOS...",
            "PROCESANDO DATOS...",
            "PREPARANDO INTERFAZ...",
        ];
        let msgIndex = 0;
        if (isLoading) {
            btnScan.disabled = true;
            btnScan.querySelector(".btn-text").textContent = messages[0];
            btnScan.querySelector(".btn-scanner").classList.add("scanning");
            scanMsgInterval = setInterval(() => {
                msgIndex = (msgIndex + 1) % messages.length;
                if (btnScan.querySelector(".btn-text")) {
                    btnScan.querySelector(".btn-text").textContent = messages[msgIndex];
                }
            }, 1200);
        } else {
            if (scanMsgInterval) clearInterval(scanMsgInterval);
            btnScan.disabled = false;
            btnScan.querySelector(".btn-text").textContent = "ESCANEAR";
            btnScan.querySelector(".btn-scanner").classList.remove("scanning");
        }
    }

    function showScanError(msg) {
        scanError.textContent = msg;
        youtubeUrlInput.classList.add("error");
    }

    function clearScanError() {
        scanError.textContent = "";
        youtubeUrlInput.classList.remove("error");
    }

    function populateVideoFormats(options) {
        videoFormatGrid.innerHTML = "";
        
        if (!options || options.length === 0) {
            videoFormatGrid.innerHTML = `<div class="info-msg">No se encontraron pistas de video adaptables para descargar.</div>`;
            return;
        }

        options.forEach((opt, index) => {
            const card = document.createElement("div");
            card.className = "format-card" + (index === 0 ? " active" : "");
            card.dataset.format = opt.format_id;
            card.dataset.type = "video";
            
            card.innerHTML = `
                <div class="format-res">${opt.label}</div>
                <div class="format-ext">${opt.ext.toUpperCase()} • ${opt.fps} FPS</div>
                <div class="format-size">${opt.size_mb ? `~${opt.size_mb} MB` : 'Tamaño desconocido'}</div>
                <div class="format-quality">Video + Audio Combinados</div>
            `;

            card.addEventListener("click", () => {
                if (selectedType === "video") {
                    selectFormatCard(card);
                }
            });

            videoFormatGrid.appendChild(card);
        });

        // Set default format id to the first card format
        if (options[0]) {
            selectedFormatId = options[0].format_id;
        }
    }

    // DOWNLOAD TRIGGER
    btnDownload.addEventListener("click", async () => {
        if (!activeUrl || !selectedFormatId) {
            alert("Por favor, selecciona un video e indica la calidad primero.");
            return;
        }

        const savePath = savePathInput.value.trim();
        if (!savePath) {
            alert("Por favor, define una ruta de guardado válida.");
            return;
        }

        // Prepare request params
        const payload = {
            url: activeUrl,
            format_id: selectedFormatId,
            type: selectedType,
            save_path: savePath,
            title: activeTitle,
            is_playlist: isPlaylistActive
        };

        btnDownload.disabled = true;
        btnDownload.textContent = "CONECTANDO AL FLUJO...";

        try {
            const response = await fetch("/api/download", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(payload)
            });
            const data = await response.json();

            if (data.success) {
                // Show progress panel
                progressPanel.style.display = "block";
                progressPanel.scrollIntoView({ behavior: "smooth" });
                
                // Reset metrics
                progressPercent.textContent = "0%";
                progressSpeed.textContent = "0 B/s";
                progressEta.textContent = "--:--";
                progressBarFill.style.width = "0%";
                terminalLog.innerHTML = `<div class="log-info">Estableciendo túnel de descarga...</div>`;
                lastLogLength = 0;

                // Start progress tracking loop (SSE with fallback)
                startProgressTracking();
            } else {
                alert("Error al iniciar descarga: " + data.error);
                btnDownload.disabled = false;
                btnDownload.textContent = "INICIAR TRANSMISIÓN Y DESCARGA";
            }
        } catch (err) {
            alert("Error de red al intentar descargar.");
            btnDownload.disabled = false;
            btnDownload.textContent = "INICIAR TRANSMISIÓN Y DESCARGA";
            console.error(err);
        }
    });

    // CANCEL TRIGGER
    btnCancel.addEventListener("click", async () => {
        if (confirm("¿Estás seguro de que deseas abortar el proceso de descarga actual?")) {
            try {
                const response = await fetch("/api/cancel", { method: "POST" });
                const data = await response.json();
                if (!data.success) {
                    alert(data.error);
                }
            } catch (err) {
                console.error("Error al cancelar:", err);
            }
        }
    });

    // PROGRESS STREAM / POLLING FALLBACK
    function startProgressTracking() {
        // Clear any existing polling/streaming
        if (pollingInterval) {
            clearInterval(pollingInterval);
            pollingInterval = null;
        }
        if (eventSource) {
            eventSource.close();
            eventSource = null;
        }

        // Try SSE first
        if (window.EventSource) {
            eventSource = new EventSource("/api/progress/stream");
            
            eventSource.onmessage = (event) => {
                try {
                    const data = JSON.parse(event.data);
                    updateProgressUI(data);
                    
                    if (!data.active) {
                        if (eventSource) {
                            eventSource.close();
                            eventSource = null;
                        }
                    }
                } catch (err) {
                    console.error("Error parsing stream data:", err);
                }
            };

            eventSource.onerror = (err) => {
                console.warn("SSE encountered an error, falling back to HTTP Polling...", err);
                if (eventSource) {
                    eventSource.close();
                    eventSource = null;
                }
                startPollingProgressFallback();
            };
        } else {
            startPollingProgressFallback();
        }
    }

    function startPollingProgressFallback() {
        if (pollingInterval) clearInterval(pollingInterval);
        
        pollingInterval = setInterval(async () => {
            try {
                const response = await fetch("/api/progress");
                const data = await response.json();
                updateProgressUI(data);

                if (!data.active) {
                    clearInterval(pollingInterval);
                    pollingInterval = null;
                }
            } catch (err) {
                console.error("Error in fallback progress polling:", err);
            }
        }, 500);
    }

    function updateProgressUI(data) {
        // Update metrics
        progressPercent.textContent = data.percent;
        progressSpeed.textContent = data.speed;
        progressEta.textContent = data.eta;
        
        // Parse percentage for progress bar fill
        let cleanPercent = parseFloat(data.percent.replace("%", "").trim()) || 0;
        progressBarFill.style.width = `${cleanPercent}%`;

        // Update title status
        downloadingTitle.textContent = `Descargando: "${data.title}" (${data.status})`;

        // Render Logs
        renderTerminalLogs(data.logs, data.status);

        // Check if finished
        if (!data.active) {
            btnDownload.disabled = false;
            btnDownload.textContent = "INICIAR TRANSMISIÓN Y DESCARGA";

            if (data.status === "Completed") {
                downloadingTitle.textContent = "DESCARGA COMPLETADA CON ÉXITO";
            } else if (data.status === "Cancelled") {
                downloadingTitle.textContent = "DESCARGA CANCELADA";
            } else if (data.status === "Failed") {
                downloadingTitle.textContent = "LA DESCARGA HA FALLADO";
            }
        }
    }

    function renderTerminalLogs(logs, status) {
        if (!logs) return;
        
        // Only re-render if new log lines have arrived to prevent heavy DOM updates
        if (logs.length === lastLogLength) return;
        lastLogLength = logs.length;

        terminalLog.innerHTML = "";
        
        logs.forEach(line => {
            // Ignore custom progress markers in log window to avoid bloating
            if (line.startsWith("[PROGRESS]")) return;

            const div = document.createElement("div");
            
            // Stylize log lines
            if (line.includes("Error:") || line.includes("ERROR:") || line.includes("failed")) {
                div.className = "log-error";
            } else if (line.includes("completada") || line.includes("éxito") || line.includes("100%")) {
                div.className = "log-success";
            } else if (line.startsWith("[download]")) {
                div.className = "log-progress";
            } else {
                div.className = "log-info";
            }

            div.textContent = line;
            terminalLog.appendChild(div);
        });

        // Auto Scroll to Bottom of Terminal
        terminalLog.scrollTop = terminalLog.scrollHeight;
    }
});
