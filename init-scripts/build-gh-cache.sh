#!/bin/bash
# ==============================================================
# build-gh-cache.sh — Build GraphHopper gh-cache + zip archive
# Usage:
#   bash init-scripts/build-gh-cache.sh
# Optional env:
#   OSM_PBF_FILE=/path/to/map-data/merged.osm.pbf
#   GH_CACHE_DIR=/path/to/map-data/gh-cache
#   GH_CACHE_ARCHIVE=/path/to/map-data/city-map-gh-cache.zip
#   GRAPHHOPPER_VERSION=6.2
# ==============================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
DATA_DIR="$ROOT_DIR/map-data"

OSM_PBF_FILE="${OSM_PBF_FILE:-$DATA_DIR/merged.osm.pbf}"
GH_CACHE_DIR="${GH_CACHE_DIR:-$DATA_DIR/gh-cache}"
GH_CACHE_ARCHIVE="${GH_CACHE_ARCHIVE:-$DATA_DIR/city-map-gh-cache.zip}"
GRAPHHOPPER_VERSION="${GRAPHHOPPER_VERSION:-6.2}"
GH_WEB_JAR="graphhopper-web-${GRAPHHOPPER_VERSION}.jar"
GH_CONFIG_FILE="config-example.yml"

DOCKER_BIN=""
if command -v docker.exe >/dev/null 2>&1; then
    DOCKER_BIN="docker.exe"
elif command -v docker >/dev/null 2>&1; then
    DOCKER_BIN="docker"
else
    echo "❌ Docker is required but was not found in PATH."
    exit 1
fi

if [[ ! -f "$OSM_PBF_FILE" ]]; then
    echo "❌ Missing OSM source file: $OSM_PBF_FILE"
    echo "Run make init-city-map (or make init-map) first."
    exit 1
fi

case "$OSM_PBF_FILE" in
    "$DATA_DIR"/*)
        OSM_PBF_REL="${OSM_PBF_FILE#$DATA_DIR/}"
        ;;
    *)
        echo "❌ OSM_PBF_FILE must be under $DATA_DIR"
        exit 1
        ;;
esac

case "$GH_CACHE_DIR" in
    "$DATA_DIR"/*)
        GH_CACHE_REL="${GH_CACHE_DIR#$DATA_DIR/}"
        ;;
    *)
        echo "❌ GH_CACHE_DIR must be under $DATA_DIR"
        exit 1
        ;;
esac

case "$GH_CACHE_ARCHIVE" in
    "$DATA_DIR"/*)
        GH_CACHE_ARCHIVE_REL="${GH_CACHE_ARCHIVE#$DATA_DIR/}"
        ;;
    *)
        echo "❌ GH_CACHE_ARCHIVE must be under $DATA_DIR"
        exit 1
        ;;
esac

BUILD_TMP_DIR="$(mktemp -d 2>/dev/null || mktemp -d -t ghcache)"
trap 'rm -rf "$BUILD_TMP_DIR" 2>/dev/null || true' EXIT

WORK_MOUNT_SRC="$BUILD_TMP_DIR"
DATA_MOUNT_SRC="$DATA_DIR"
if [[ "$DOCKER_BIN" == "docker.exe" ]] && command -v wslpath >/dev/null 2>&1; then
    WORK_MOUNT_SRC="$(wslpath -w "$BUILD_TMP_DIR")"
    DATA_MOUNT_SRC="$(wslpath -w "$DATA_DIR")"
fi

mkdir -p "$DATA_DIR"

echo "🚧 Building GraphHopper gh-cache from: $OSM_PBF_FILE"
"$DOCKER_BIN" run --rm \
    -v "$WORK_MOUNT_SRC:/work" \
    -v "$DATA_MOUNT_SRC:/data" \
    -w /work \
    eclipse-temurin:17-jre-jammy \
    bash -lc "
        set -euo pipefail
        apt-get update -yqq >/dev/null
        apt-get install -yqq wget ca-certificates zip >/dev/null

        if [ ! -f /work/$GH_WEB_JAR ]; then
            wget -q -O /work/$GH_WEB_JAR \
                https://repo1.maven.org/maven2/com/graphhopper/graphhopper-web/$GRAPHHOPPER_VERSION/$GH_WEB_JAR
        fi

        if [ ! -f /work/$GH_CONFIG_FILE ]; then
            wget -q -O /work/$GH_CONFIG_FILE \
                https://raw.githubusercontent.com/graphhopper/graphhopper/$GRAPHHOPPER_VERSION/config-example.yml \
                || wget -q -O /work/$GH_CONFIG_FILE \
                https://raw.githubusercontent.com/graphhopper/graphhopper/${GRAPHHOPPER_VERSION%%.*}.x/config-example.yml
        fi

        rm -rf /data/$GH_CACHE_REL
        java -Xmx4g \
            -Ddw.graphhopper.datareader.file=/data/$OSM_PBF_REL \
            -Ddw.graphhopper.graph.location=/data/$GH_CACHE_REL \
            -jar /work/$GH_WEB_JAR import /work/$GH_CONFIG_FILE

        cd /data
        rm -f $GH_CACHE_ARCHIVE_REL
        zip -qr $GH_CACHE_ARCHIVE_REL $GH_CACHE_REL
    "

if [[ ! -f "$GH_CACHE_DIR/properties" ]]; then
    echo "❌ GraphHopper cache build did not produce expected properties file."
    exit 1
fi

if [[ ! -f "$GH_CACHE_ARCHIVE" ]]; then
    echo "❌ Archive build failed: $GH_CACHE_ARCHIVE not found"
    exit 1
fi

echo "✅ Graph cache build complete"
echo "   cache:   $GH_CACHE_DIR"
echo "   archive: $GH_CACHE_ARCHIVE"