#!/bin/bash
# ==============================================================================
# SCRIPT DE DIAGNÓSTICO Y DESPLIEGUE SEGURO (ALTA INGENIERÍA)
# ==============================================================================

# Colores para la consola
GREEN='\033[0;32m'
CYAN='\033[0;36m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

echo -e "${CYAN}=== INICIANDO DIAGNÓSTICO DE ALTA PRECISIÓN ===${NC}"

# 1. Verificar estado de Git
echo -e "\n${CYAN}[1/5] Actualizando repositorio Git...${NC}"
git fetch origin main
git reset --hard origin/main

# 2. Verificar tamaño de los archivos en el Host
echo -e "\n${CYAN}[2/5] Verificando archivos en el Host...${NC}"
if [ -f "apk/zarate.apk" ]; then
    SIZE_HOST=$(ls -lh apk/zarate.apk | awk '{print $5}')
    echo -e "${GREEN}✔ apk/zarate.apk existe en el Host. Tamaño: $SIZE_HOST${NC}"
else
    echo -e "${RED}✖ apk/zarate.apk NO existe en el Host!${NC}"
fi

# Eliminar el APK residual en descargas para evitar colisiones/fallbacks de caché
if [ -f "downloads/zarate-player.apk" ]; then
    echo -e "${YELLOW}⚠ Detectado zarate-player.apk en la carpeta downloads/ del host (Tamaño: $(ls -lh downloads/zarate-player.apk | awk '{print $5}')).${NC}"
    echo -e "${YELLOW}Eliminando archivo residual para forzar el uso del APK nuevo de la app...${NC}"
    rm -f downloads/zarate-player.apk
fi

# 3. Reconstruir con Docker forzando sin caché la copia de archivos
echo -e "\n${CYAN}[3/5] Reconstruyendo contenedor de Docker con --no-cache...${NC}"
docker compose build --no-cache
docker compose down --remove-orphans
docker compose up -d

# 4. Verificar el archivo dentro del contenedor en ejecución
echo -e "\n${CYAN}[4/5] Verificando archivo dentro del contenedor Docker...${NC}"
sleep 3
CONTAINER_NAME="nexus-downloader-service"

if docker exec $CONTAINER_NAME ls -la /app/apk/zarate.apk >/dev/null 2>&1; then
    SIZE_CONTAINER=$(docker exec $CONTAINER_NAME ls -lh /app/apk/zarate.apk | awk '{print $5}')
    echo -e "${GREEN}✔ apk/zarate.apk verificado dentro del contenedor. Tamaño final: $SIZE_CONTAINER${NC}"
else
    echo -e "${RED}✖ El archivo no existe dentro de /app/apk/zarate.apk en el contenedor!${NC}"
fi

# 5. Limpieza de Docker residual
echo -e "\n${CYAN}[5/5] Limpiando recursos antiguos de Docker...${NC}"
docker image prune -f

echo -e "\n${GREEN}=== PROCESO DE ALTA INGENIERÍA FINALIZADO ===${NC}"
echo -e "${GREEN}Por favor, limpia el caché de tu navegador (Ctrl + F5) o descarga usando una pestaña de incógnito para verificar.${NC}\n"
