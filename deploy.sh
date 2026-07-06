#!/bin/zsh

# ==============================================================================
# SCRIPT DE DESPLIEGUE AUTOMATIZADO PARA ECOBISTEMA GARUDA / ARCH LINUX (ZSH)
# ==============================================================================

# Configuración del servidor remoto VPS (Edita estos parámetros según corresponda)
VPS_USER="zarate"
VPS_HOST="187.127.20.171" # <-- Coloca aquí la IP o host de tu VPS
VPS_PATH="/home/zarate/nexus-downloader"
IMAGE_NAME="nexus-downloader"
TAG="latest"
TAR_FILE="nexus-downloader.tar.gz"

# Paleta de colores para consola Garuda
GREEN='\033[0;32m'
CYAN='\033[0;36m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BOLD='\033[1m'
NC='\033[0m' # No Color

# Mensaje de ayuda si falta configuración de IP
if [[ "$VPS_HOST" == "CAMBIA_POR_LA_IP_DE_TU_VPS" ]]; then
    echo -e "${RED}${BOLD}✖ ERROR:${NC} Debes abrir el script 'deploy.sh' y configurar la variable VPS_HOST con la IP de tu servidor."
    exit 1
fi

set -e # Terminar ejecución inmediatamente si ocurre cualquier error

# ------------------------------------------------------------------------------
# Paso 1: Construcción de la imagen local de Docker
# ------------------------------------------------------------------------------
echo -e "\n${CYAN}📦 [Paso 1/5] Construyendo la imagen de Docker localmente...${NC}"
docker build -t ${IMAGE_NAME}:${TAG} .
echo -e "${GREEN}✔ Imagen de Docker construida con éxito.${NC}"

# ------------------------------------------------------------------------------
# Paso 2: Exportación y compresión de la imagen
# ------------------------------------------------------------------------------
echo -e "\n${CYAN}🗜 [Paso 2/5] Exportando y comprimiendo imagen a '${TAR_FILE}'...${NC}"
if [[ -f "$TAR_FILE" ]]; then
    echo -e "${YELLOW}Aviso: Eliminando tarball residual previo...${NC}"
    rm -f "$TAR_FILE"
fi
docker save ${IMAGE_NAME}:${TAG} | gzip > "$TAR_FILE"
echo -e "${GREEN}✔ Imagen exportada correctamente. Tamaño: $(du -sh $TAR_FILE | cut -f1)${NC}"

# ------------------------------------------------------------------------------
# Paso 3: Transferencia de archivos al VPS (SCP)
# ------------------------------------------------------------------------------
echo -e "\n${CYAN}📤 [Paso 3/5] Transfiriendo archivos al VPS remoto (${VPS_HOST})...${NC}"
# Crear el directorio en el VPS si no existe antes de transferir
ssh ${VPS_USER}@${VPS_HOST} "mkdir -p ${VPS_PATH}"

# Transferir la imagen y el archivo docker-compose.yml
scp "$TAR_FILE" docker-compose.yml ${VPS_USER}@${VPS_HOST}:${VPS_PATH}/
echo -e "${GREEN}✔ Archivos transferidos exitosamente al VPS.${NC}"

# ------------------------------------------------------------------------------
# Paso 4: Despliegue en caliente en el VPS (SSH)
# ------------------------------------------------------------------------------
echo -e "\n${CYAN}⚙ [Paso 4/5] Conectando vía SSH para actualizar el servicio...${NC}"
ssh -T ${VPS_USER}@${VPS_HOST} << EOF
    set -e
    cd ${VPS_PATH}
    
    # 1. Crear el directorio de descargas con los permisos adecuados
    mkdir -p downloads
    
    echo "Cargando imagen Docker comprimida en el daemon..."
    docker load -i ${TAR_FILE}
    
    echo "Apagando el contenedor viejo (si existe)..."
    docker compose down --remove-orphans || true
    
    echo "Levantando la nueva versión del backend..."
    docker compose up -d
    
    echo "Limpiando la imagen comprimida para ahorrar espacio en disco..."
    rm -f ${TAR_FILE}
    
    echo "Limpiando imágenes de Docker huérfanas o sin etiqueta..."
    docker image prune -f
EOF
echo -e "${GREEN}✔ Contenedor desplegado y en ejecución silenciosa en el puerto 8000.${NC}"

# ------------------------------------------------------------------------------
# Paso 5: Limpieza local
# ------------------------------------------------------------------------------
echo -e "\n${CYAN}🧹 [Paso 5/5] Eliminando archivos locales temporales...${NC}"
rm -f "$TAR_FILE"
echo -e "${GREEN}✔ Limpieza de espacio local completada.${NC}"

echo -e "\n${GREEN}${BOLD}🎉 ¡Despliegue finalizado con éxito! El descargador está activo en http://${VPS_HOST}:8000/${NC}\n"
