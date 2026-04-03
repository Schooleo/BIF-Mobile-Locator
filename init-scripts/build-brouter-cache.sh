#!/bin/bash
# ==============================================================================
# build-brouter-cache.sh — Build BRouter rd5 cache + zip archive
# Usage:
#   bash init-scripts/build-brouter-cache.sh
# Optional env:
#   OSM_PBF_URL=https://download.geofabrik.de/asia/vietnam-latest.osm.pbf
#   OSM_PBF_FILE=/path/to/map-data/vietnam-latest.osm.pbf
#   BROUTER_CACHE_DIR=/path/to/map-data/brouter-cache
#   BROUTER_CACHE_ARCHIVE=/path/to/map-data/city-map-brouter-cache.zip
#   BROUTER_VERSION=v1.7.8
# ==============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
DATA_DIR="$ROOT_DIR/map-data"

OSM_PBF_URL="${OSM_PBF_URL:-https://download.geofabrik.de/asia/vietnam-latest.osm.pbf}"
OSM_PBF_FILE="${OSM_PBF_FILE:-$DATA_DIR/$(basename "$OSM_PBF_URL")}"
BROUTER_CACHE_DIR="${BROUTER_CACHE_DIR:-$DATA_DIR/brouter-cache}"
BROUTER_CACHE_ARCHIVE="${BROUTER_CACHE_ARCHIVE:-$DATA_DIR/city-map-brouter-cache.zip}"
BROUTER_VERSION="${BROUTER_VERSION:-v1.7.8}"

DOCKER_BIN=""
if command -v docker.exe >/dev/null 2>&1; then
    DOCKER_BIN="docker.exe"
elif command -v docker >/dev/null 2>&1; then
    DOCKER_BIN="docker"
else
    echo "❌ Docker is required but was not found in PATH."
    exit 1
fi

if ! "$DOCKER_BIN" version --format '{{.Server.Version}}' >/dev/null 2>&1; then
    echo "Docker CLI is installed but the daemon is not reachable."
    echo "Start Docker Desktop (or another compatible daemon) and retry."
    exit 1
fi

mkdir -p "$DATA_DIR"

case "$OSM_PBF_FILE" in
    "$DATA_DIR"/*)
        OSM_PBF_REL="${OSM_PBF_FILE#$DATA_DIR/}"
        ;;
    *)
        echo "❌ OSM_PBF_FILE must be under $DATA_DIR"
        exit 1
        ;;
esac

case "$BROUTER_CACHE_DIR" in
    "$DATA_DIR"/*)
        BROUTER_CACHE_REL="${BROUTER_CACHE_DIR#$DATA_DIR/}"
        ;;
    *)
        echo "❌ BROUTER_CACHE_DIR must be under $DATA_DIR"
        exit 1
        ;;
esac

case "$BROUTER_CACHE_ARCHIVE" in
    "$DATA_DIR"/*)
        BROUTER_CACHE_ARCHIVE_REL="${BROUTER_CACHE_ARCHIVE#$DATA_DIR/}"
        ;;
    *)
        echo "❌ BROUTER_CACHE_ARCHIVE must be under $DATA_DIR"
        exit 1
        ;;
esac

BUILD_TMP_DIR="$(mktemp -d 2>/dev/null || mktemp -d -t broutercache)"
trap 'rm -rf "$BUILD_TMP_DIR" 2>/dev/null || true' EXIT

WORK_MOUNT_SRC="$BUILD_TMP_DIR"
DATA_MOUNT_SRC="$DATA_DIR"
if [[ "$DOCKER_BIN" == "docker.exe" ]] && command -v wslpath >/dev/null 2>&1; then
    WORK_MOUNT_SRC="$(wslpath -w "$BUILD_TMP_DIR")"
    DATA_MOUNT_SRC="$(wslpath -w "$DATA_DIR")"
fi

echo "🚧 Building BRouter cache from: $OSM_PBF_URL"
"$DOCKER_BIN" run --rm \
    -v "$WORK_MOUNT_SRC:/work" \
    -v "$DATA_MOUNT_SRC:/data" \
    -w /work \
    eclipse-temurin:17-jdk-jammy \
    bash -lc "
        set -euo pipefail
        export DEBIAN_FRONTEND=noninteractive
        apt-get update -yqq >/dev/null
        apt-get install -yqq git curl zip unzip ca-certificates findutils >/dev/null

        mkdir -p /data/$(dirname "$OSM_PBF_REL")
        mkdir -p /data/$BROUTER_CACHE_REL/profiles2 /data/$BROUTER_CACHE_REL/segments4

        if [ ! -f /data/$OSM_PBF_REL ]; then
            echo '⬇️ Downloading OSM extract...'
            curl -L --fail --retry 3 --output /data/$OSM_PBF_REL '$OSM_PBF_URL'
        fi

        rm -rf /work/brouter-src /work/brouter-build
        git clone --depth 1 --branch '$BROUTER_VERSION' https://github.com/abrensch/brouter.git /work/brouter-src >/dev/null 2>&1
        mkdir -p /work/brouter-build/empty-srtm

        cd /work/brouter-src
        cat >/work/brouter-src/print-runtime-classpath.init.gradle <<'GRADLE'
allprojects {
    afterEvaluate { project ->
        if (project.path == ':brouter-map-creator') {
            project.tasks.register('printRuntimeClasspath') {
                doLast {
                    println '__BROUTER_RUNTIME_CP__=' + project.sourceSets.main.runtimeClasspath.asPath
                }
            }
        }
    }
}
GRADLE

        ./gradlew --no-daemon -x test \
            :brouter-codec:jar \
            :brouter-util:jar \
            :brouter-expressions:jar \
            :brouter-mapaccess:jar \
            :brouter-core:jar \
            :brouter-map-creator:jar >/dev/null

        sh misc/scripts/generate_profile_variants.sh

        CP=\"\$(./gradlew --no-daemon -q --console=plain -I /work/brouter-src/print-runtime-classpath.init.gradle :brouter-map-creator:printRuntimeClasspath | sed -n 's/^__BROUTER_RUNTIME_CP__=//p' | tail -n 1)\"
        if [ -z \"\$CP\" ]; then
            echo '❌ Failed to resolve BRouter map-creator runtime classpath.'
            exit 1
        fi

        WORKDIR=/work/brouter-build/work
        rm -rf \"\$WORKDIR\"
        mkdir -p \"\$WORKDIR\"/nodetiles \"\$WORKDIR\"/waytiles \"\$WORKDIR\"/nodes55 \"\$WORKDIR\"/waytiles55 \"\$WORKDIR\"/unodes55 \"\$WORKDIR\"/segments

        java -Xmx4g -cp \"\$CP\" btools.mapcreator.OsmFastCutter \
            misc/profiles2/lookups.dat \
            \"\$WORKDIR\"/nodetiles \
            \"\$WORKDIR\"/waytiles \
            \"\$WORKDIR\"/nodes55 \
            \"\$WORKDIR\"/waytiles55 \
            \"\$WORKDIR\"/bordernids.dat \
            \"\$WORKDIR\"/relations.dat \
            \"\$WORKDIR\"/restrictions.dat \
            misc/profiles2/all.brf \
            misc/profiles2/trekking.brf \
            misc/profiles2/softaccess.brf \
            /data/$OSM_PBF_REL

        java -Xmx4g -cp \"\$CP\" btools.mapcreator.PosUnifier \
            \"\$WORKDIR\"/nodes55 \
            \"\$WORKDIR\"/unodes55 \
            \"\$WORKDIR\"/bordernids.dat \
            \"\$WORKDIR\"/bordernodes.dat \
            /work/brouter-build/empty-srtm

        java -Xmx4g -DskipEncodingCheck=true -cp \"\$CP\" btools.mapcreator.WayLinker \
            \"\$WORKDIR\"/unodes55 \
            \"\$WORKDIR\"/waytiles55 \
            \"\$WORKDIR\"/bordernodes.dat \
            \"\$WORKDIR\"/restrictions.dat \
            misc/profiles2/lookups.dat \
            misc/profiles2/all.brf \
            \"\$WORKDIR\"/segments \
            rd5

        rm -rf /data/$BROUTER_CACHE_REL
        mkdir -p /data/$BROUTER_CACHE_REL/profiles2 /data/$BROUTER_CACHE_REL/segments4

        cp misc/profiles2/lookups.dat /data/$BROUTER_CACHE_REL/profiles2/
        cp misc/profiles2/car-fast.brf /data/$BROUTER_CACHE_REL/profiles2/
        cp misc/profiles2/fastbike.brf /data/$BROUTER_CACHE_REL/profiles2/bicycle.brf
        cp misc/profiles2/hiking-mountain.brf /data/$BROUTER_CACHE_REL/profiles2/foot.brf
        find \"\$WORKDIR\"/segments -name '*.rd5' -exec cp {} /data/$BROUTER_CACHE_REL/segments4/ \;

        cd /data
        rm -f $BROUTER_CACHE_ARCHIVE_REL
        zip -qr $BROUTER_CACHE_ARCHIVE_REL $BROUTER_CACHE_REL
    "

if ! find "$BROUTER_CACHE_DIR/segments4" -maxdepth 1 -name '*.rd5' | grep -q .; then
    echo "❌ BRouter cache build did not produce any .rd5 tiles."
    exit 1
fi

for profile in car-fast.brf bicycle.brf foot.brf; do
    if [[ ! -f "$BROUTER_CACHE_DIR/profiles2/$profile" ]]; then
        echo "❌ Missing profile in packaged cache: $profile"
        exit 1
    fi
done

if [[ ! -f "$BROUTER_CACHE_DIR/profiles2/lookups.dat" ]]; then
    echo "❌ Missing BRouter lookups.dat file in packaged cache."
    exit 1
fi

if [[ ! -f "$BROUTER_CACHE_ARCHIVE" ]]; then
    echo "❌ Archive build failed: $BROUTER_CACHE_ARCHIVE not found"
    exit 1
fi

echo "✅ BRouter cache build complete"
echo "   cache:   $BROUTER_CACHE_DIR"
echo "   archive: $BROUTER_CACHE_ARCHIVE"
