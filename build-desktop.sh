#!/usr/bin/env bash
set -euo pipefail

# ─── build-desktop.sh ─────────────────────────────────────
# Builds the desktop app as a portable jpackage app-image.
# Output: dist/sandook/  (self-contained, no Java install needed)
#
# Requires:
#   - Node.js 18+  (for Next.js static export)
#   - JDK 17+      (for Maven + jpackage)
#   - Maven 3.9+   (for Spring Boot fat JAR)
#
# Usage:
#   ./build-desktop.sh              # full build
#   ./build-desktop.sh --skip-frontend  # skip frontend (if already built)
# ──────────────────────────────────────────────────────────

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
FRONTEND_DIR="$SCRIPT_DIR/frontend"
BACKEND_DIR="$SCRIPT_DIR/backend"
DIST_DIR="$SCRIPT_DIR/dist"
APP_NAME="Sandook"
APP_VERSION="${1:-1.0.0}"
SKIP_FRONTEND=false

for arg in "$@"; do
  case "$arg" in
    --skip-frontend) SKIP_FRONTEND=true ;;
  esac
done

echo "=== Sandook Desktop Build ==="
echo "App version: $APP_VERSION"
echo ""

# ─── 1. Frontend: static export ──────────────────────────
if [ "$SKIP_FRONTEND" = false ]; then
  echo "[1/4] Building frontend (static export)..."
  cd "$FRONTEND_DIR"
  npm ci --ignore-scripts 2>/dev/null || npm install --ignore-scripts
  NEXT_EXPORT=true npm run build
  echo "  ✓ Frontend built → frontend/out/"
  echo ""
else
  echo "[1/4] Skipping frontend build (--skip-frontend)"
  echo ""
fi

# ─── 2. Copy frontend into backend static resources ─────
echo "[2/4] Copying frontend to backend resources..."
TARGET_DIR="$BACKEND_DIR/src/main/resources/static"
rm -rf "$TARGET_DIR"
cp -r "$FRONTEND_DIR/out" "$TARGET_DIR"
echo "  ✓ Copied $(find "$TARGET_DIR" -type f | wc -l) files"
echo ""

# ─── 3. Build backend fat JAR ────────────────────────────
echo "[3/4] Building backend JAR (embedded profile)..."
cd "$BACKEND_DIR"
./mvnw -DskipTests package -Pembedded
echo "  ✓ JAR built → backend/target/sandook.jar"
echo ""

# ─── 4. jpackage app-image ──────────────────────────────
echo "[4/4] Creating jpackage app-image..."

JAR_PATH="$BACKEND_DIR/target/sandook.jar"

if [ ! -f "$JAR_PATH" ]; then
  echo "ERROR: JAR not found at $JAR_PATH" >&2
  exit 1
fi

rm -rf "$DIST_DIR"
mkdir -p "$DIST_DIR"

# Detect OS for jpackage output type
case "$(uname -s)" in
  MINGW*|MSYS*|CYGWIN*)
    JPKG_LAUNCHER="bin/${APP_NAME}.bat"
    ;;
  Darwin*)
    JPKG_LAUNCHER="bin/${APP_NAME}.command"
    ;;
  *)
    JPKG_LAUNCHER="bin/${APP_NAME}"
    ;;
esac

jpackage \
  --type app-image \
  --name "$APP_NAME" \
  --app-version "$APP_VERSION" \
  --dest "$DIST_DIR" \
  --input "$BACKEND_DIR/target" \
  --main-jar sandook.jar \
  --java-options "-Xmx512m -Dspring.profiles.active=embedded" \
  --description "Sandook — Cash box ledger" \
  --vendor "Sandook"

echo ""
echo "=== Build Complete ==="
echo "Output: $DIST_DIR/$APP_NAME/"
echo ""
echo "To run:"
echo "  $DIST_DIR/$APP_NAME/$JPKG_LAUNCHER"
echo ""
echo "The app starts on http://localhost:8081"
echo "Default admin: admin / admin123 (change on first login)"
