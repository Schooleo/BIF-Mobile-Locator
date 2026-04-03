#!/bin/bash
# ==============================================================
# init-city-map.sh — Build city-level OSRM graph from user location
# Usage:
#   LAT=10.7769 LON=106.7009 [RADIUS_KM=20] bash init-scripts/init-city-map.sh
# ============================================================== 
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
DATA_DIR="$ROOT_DIR/map-data"

LAT="${LAT:-}"
LON="${LON:-}"
RADIUS_KM="${RADIUS_KM:-20}"
VIETNAM_PBF_URL="${VIETNAM_PBF_URL:-https://download.geofabrik.de/asia/vietnam-latest.osm.pbf}"
VIETNAM_PBF_FILE="$DATA_DIR/vietnam-latest.osm.pbf"
CITY_PBF_FILE="$DATA_DIR/merged.osm.pbf"

if [[ -z "$LAT" || -z "$LON" ]]; then
    echo "Usage: LAT=<latitude> LON=<longitude> [RADIUS_KM=20] bash init-scripts/init-city-map.sh"
    exit 1
fi

mkdir -p "$DATA_DIR"

echo "📍 Requested location: lat=$LAT lon=$LON radius=${RADIUS_KM}km"

BBOX=$(docker run --rm \
    -e LAT="$LAT" \
    -e LON="$LON" \
    -e RADIUS_KM="$RADIUS_KM" \
    python:3.11-slim \
    python - <<'PY'
import math
import os
import sys

lat = float(os.environ["LAT"])
lon = float(os.environ["LON"])
radius_km = float(os.environ["RADIUS_KM"])

VN_MIN_LAT = 8.56
VN_MAX_LAT = 23.39
VN_MIN_LON = 102.14
VN_MAX_LON = 109.46

if not (VN_MIN_LAT <= lat <= VN_MAX_LAT and VN_MIN_LON <= lon <= VN_MAX_LON):
    print("OUTSIDE_VIETNAM")
    sys.exit(0)

lat_delta = radius_km / 111.32
cos_lat = math.cos(math.radians(lat))
lon_delta = radius_km / (111.32 * max(abs(cos_lat), 0.1))

south = max(VN_MIN_LAT, lat - lat_delta)
north = min(VN_MAX_LAT, lat + lat_delta)
west = max(VN_MIN_LON, lon - lon_delta)
east = min(VN_MAX_LON, lon + lon_delta)

print(f"{west:.6f},{south:.6f},{east:.6f},{north:.6f}")
PY
)

if [[ "$BBOX" == "OUTSIDE_VIETNAM" ]]; then
    echo "❌ Not supported outside of Vietnam."
    exit 1
fi

echo "🧭 Extract bbox: $BBOX"

if [[ ! -f "$VIETNAM_PBF_FILE" ]]; then
    echo "📥 Downloading Vietnam base map..."
    docker run --rm \
        -v "$DATA_DIR:/data" \
        python:3.11-slim \
        bash -c "
            apt-get update -yqq && apt-get install -yqq wget > /dev/null 2>&1 &&
            wget -q --show-progress -O /data/vietnam-latest.osm.pbf '$VIETNAM_PBF_URL'
        "
fi

echo "🗺️  Extracting city-level map around current location..."
docker run --rm \
    -v "$DATA_DIR:/data" \
    python:3.11-slim \
    bash -c "
        apt-get update -yqq && apt-get install -yqq osmium-tool > /dev/null 2>&1 &&
        osmium extract --bbox $BBOX /data/vietnam-latest.osm.pbf -o /data/merged.osm.pbf --overwrite
    "

echo "🚗 Rebuilding OSRM graph for extracted city map..."
docker run --rm \
    -v "$DATA_DIR:/data" \
    osrm/osrm-backend:latest \
    bash -c "
        rm -f /data/merged.osrm* &&
        osrm-extract -p /opt/car.lua /data/merged.osm.pbf &&
        osrm-partition /data/merged.osrm &&
        osrm-customize /data/merged.osrm
    "

echo "🔄 Restarting OSRM service..."
docker compose -f "$ROOT_DIR/docker-compose.yml" --profile osrm up -d osrm

echo "✅ City-level map is ready for routing."
