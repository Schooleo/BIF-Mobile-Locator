#!/bin/bash
# ==============================================================
# init-map-data.sh — Downloads map data & compiles OSRM graph
# Run via:  make init-maps
# ==============================================================
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
DATA_DIR="$ROOT_DIR/map-data"

# Load .env if present
if [ -f "$ROOT_DIR/.env" ]; then
    set -a
    # Support Windows CRLF in .env to avoid bash parse errors under WSL.
    source <(sed 's/\r$//' "$ROOT_DIR/.env")
    set +a
fi

URLS="${OSRM_MAP_URLS:-https://download.geofabrik.de/asia/vietnam-latest.osm.pbf}"
BBOX="${OVERTURE_BBOX:-102.14,8.56,109.46,23.39}"
OLLAMA_MODEL_NAME="${OLLAMA_MODEL:-llama3.1}"

mkdir -p "$DATA_DIR"

# ==============================================================
# Phase 1: Download Overture Places (via python container)
# ==============================================================
PLACES_FILE="$DATA_DIR/places.geojson"

if [ -f "$PLACES_FILE" ]; then
    echo "✅ Places GeoJSON already exists. Skipping Overture download."
else
    echo "🗺️  Downloading Places from Overture Maps (bbox: $BBOX)..."
    docker run --rm \
        -v "$DATA_DIR:/data" \
        python:3.11-slim \
        bash -c "
            pip install -q overturemaps &&
            overturemaps download --type place --bbox $BBOX -f geojson -o /data/places.geojson
        "
    echo "✅ Places download complete."
fi

echo "============================================="

# ==============================================================
# Phase 2: Download OSM PBF files (via python container)
# ==============================================================
COMBINED_PBF="$DATA_DIR/merged.osm.pbf"

if [ -f "$COMBINED_PBF" ] || [ -f "$DATA_DIR/merged.osrm" ]; then
    echo "✅ Raw OSM data already exists. Skipping download."
else
    echo "📥 Downloading OSM routing data..."

    IFS=',' read -ra URLS_ARRAY <<< "$URLS"
    WGET_CMDS=""
    FILENAMES=()

    for URL in "${URLS_ARRAY[@]}"; do
        FILENAME=$(basename "$URL")
        FILENAMES+=("$FILENAME")
        WGET_CMDS="$WGET_CMDS
            if [ ! -f /data/$FILENAME ]; then wget -q --show-progress -O /data/$FILENAME '$URL'; fi"
    done

    docker run --rm \
        -v "$DATA_DIR:/data" \
        python:3.11-slim \
        bash -c "
            apt-get update -yqq && apt-get install -yqq wget osmium-tool > /dev/null 2>&1
            $WGET_CMDS
        "

    # Merge if multiple PBFs, otherwise just copy
    if [ ${#FILENAMES[@]} -gt 1 ]; then
        echo "🗺️  Merging PBF maps..."
        MERGE_ARGS=""
        for F in "${FILENAMES[@]}"; do MERGE_ARGS="$MERGE_ARGS /data/$F"; done

        docker run --rm \
            -v "$DATA_DIR:/data" \
            python:3.11-slim \
            bash -c "
                apt-get update -yqq && apt-get install -yqq osmium-tool > /dev/null 2>&1 &&
                osmium merge $MERGE_ARGS -o /data/merged.osm.pbf --overwrite
            "
    else
        cp "$DATA_DIR/${FILENAMES[0]}" "$COMBINED_PBF" 2>/dev/null || true
    fi
    echo "✅ OSM download complete."
fi

echo "============================================="

# ==============================================================
# Phase 3: Compile OSRM routing graph (via osrm container)
# ==============================================================
GRAPH_FILE="$DATA_DIR/merged.osrm"

if [ -f "$GRAPH_FILE" ]; then
    echo "✅ OSRM routing graph already compiled. Skipping."
else
    echo "🚗 Compiling OSRM Routing Graph (this may take a while)..."
    docker run --rm \
        -v "$DATA_DIR:/data" \
        osrm/osrm-backend:latest \
        bash -c "
            osrm-extract -p /opt/car.lua /data/merged.osm.pbf &&
            osrm-partition /data/merged.osrm &&
            osrm-customize /data/merged.osrm
        "
    echo "✅ OSRM compilation complete."
fi

echo "============================================="
echo "🎉 All map data is ready!"
