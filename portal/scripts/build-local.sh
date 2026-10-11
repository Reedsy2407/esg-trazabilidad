#!/usr/bin/env bash
# Construye el portal en local con el mismo builder Docker y los mismos
# argumentos que el job "build" de .github/workflows/portal.yml. Deja el zip del
# sitio y report.json en portal/artifacts/. Requiere Docker (en Windows, Rancher
# Desktop en modo dockerd).
#
# Uso (funciona desde cualquier carpeta):
#   bash portal/scripts/build-local.sh     # desde la raíz del repo
#   bash scripts/build-local.sh            # desde portal/
set -euo pipefail

# Debe coincidir con DOCKER_VERSION en portal.yml.
BUILDER_VERSION="2026.09.0357"
IMAGE="registry.jetbrains.team/p/writerside/builder/writerside-builder:${BUILDER_VERSION}"

if ! docker info >/dev/null 2>&1; then
  echo "Docker no responde. En Windows, abre Rancher Desktop y reintenta." >&2
  exit 1
fi

PORTAL_DIR="$(cd "$(dirname "$0")/.." && pwd)"
# En Git Bash de Windows, Docker necesita la ruta en formato C:/...
if command -v cygpath >/dev/null 2>&1; then
  MOUNT_SRC="$(cygpath -m "$PORTAL_DIR")"
else
  MOUNT_SRC="$PORTAL_DIR"
fi

# El contenedor corre como root: en Linux, lo que escribe queda con dueño root.
# Por eso la limpieza se hace dentro del contenedor y al final se devuelve la
# propiedad al usuario que llamó (en Windows el chown no tiene efecto).
# -lang es-ES: la interfaz del sitio (buscador, navegación) sale en español.
# MSYS_NO_PATHCONV evita que Git Bash reescriba /opt/... como ruta de Windows.
MSYS_NO_PATHCONV=1 docker run --rm \
  -e HOST_UID="$(id -u)" -e HOST_GID="$(id -g)" \
  -v "${MOUNT_SRC}:/opt/sources" "$IMAGE" /bin/bash -c '
  rm -rf /opt/sources/artifacts
  export DISPLAY=:99
  Xvfb :99 &
  git config --global --add safe.directory /opt/sources
  /opt/builder/bin/idea.sh helpbuilderinspect -source-dir /opt/sources \
    -product Writerside/esg -lang es-ES --runner other -output-dir /opt/sources/artifacts/
  status=$?
  chown -R "$HOST_UID:$HOST_GID" /opt/sources/artifacts /opt/sources/.idea 2>/dev/null || true
  exit $status
'

ls -la "$PORTAL_DIR/artifacts"
