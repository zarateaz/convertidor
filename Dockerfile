# ==========================================
# STAGE 1: Compilación de Binario Estático de Go
# ==========================================
FROM golang:1.21-alpine AS builder

# Configurar entorno de compilación
WORKDIR /build

# Copiar archivos necesarios para Go
COPY go.mod ./
# Copiar el código fuente
COPY main.go ./

# Compilar un binario de Go totalmente estático, optimizado para producción
# -ldflags="-s -w": remueve la tabla de símbolos y de depuración para reducir el tamaño al mínimo
RUN CGO_ENABLED=0 GOOS=linux go build -ldflags="-s -w" -o nexus-downloader main.go

# ==========================================
# STAGE 2: Entorno de Ejecución Headless Final
# ==========================================
FROM python:3.11-slim

# Evitar que Python escriba archivos .pyc en disco y forzar salida inmediata a logs
ENV PYTHONDONTWRITEBYTECODE=1
ENV PYTHONUNBUFFERED=1

WORKDIR /app

# Instalar dependencias del sistema indispensables para descargas y ffmpeg
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
    ffmpeg \
    curl \
    ca-certificates \
    nodejs && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

# Instalar dependencias de Python necesarias para el backend server.py
RUN pip install --no-cache-dir requests yt-dlp

# Crear grupo y usuario sin privilegios 'zarate' con UID/GID 1000
# Esto asegura consistencia absoluta con los permisos del usuario del host (Arch/Garuda)
RUN groupadd -g 1000 zarate && \
    useradd -u 1000 -g zarate -m -s /bin/bash zarate

# Copiar el binario estático de Go compilado en el Stage 1
COPY --from=builder /build/nexus-downloader /app/nexus-downloader

# Copiar el código del servidor backend de Python y los activos web estáticos
COPY server.py /app/server.py
COPY index.html /app/index.html
COPY style.css /app/style.css
COPY app.js /app/app.js

# Crear directorio de persistencia para las descargas
RUN mkdir -p /app/downloads

# Asignar la propiedad completa del directorio /app al usuario 'zarate'
RUN chown -R zarate:zarate /app

# Ejecutar el contenedor bajo el usuario de seguridad sin privilegios
USER zarate

# Declarar el volumen de persistencia para el almacenamiento de archivos del host
VOLUME ["/app/downloads"]

# Exponer el puerto del servidor HTTP backend
EXPOSE 8000

# Iniciar por defecto el servidor HTTP backend robusto headless
CMD ["python3", "server.py"]
