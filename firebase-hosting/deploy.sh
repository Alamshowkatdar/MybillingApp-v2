#!/bin/sh
set -eu

if [ "$#" -lt 1 ]; then
    echo "Usage: $0 FIREBASE_PROJECT_ID [APK_PATH]" >&2
    exit 2
fi

PROJECT_ID=$1
APK_PATH=${2:-app/build/outputs/apk/release/app-release.apk}
DESTINATION=firebase-hosting/public/factorybill.apk
FIREBASE_CLI=$(command -v firebase || true)

if [ -z "$FIREBASE_CLI" ] && [ -x "$HOME/.local/bin/firebase" ]; then
    FIREBASE_CLI="$HOME/.local/bin/firebase"
fi

if [ ! -f "$APK_PATH" ]; then
    echo "APK not found: $APK_PATH" >&2
    echo "Build a signed release with ./gradlew :app:assembleRelease first." >&2
    exit 1
fi

if [ -z "$FIREBASE_CLI" ]; then
    echo "Firebase CLI is required. Install it with: npm install --global firebase-tools" >&2
    exit 1
fi

cp "$APK_PATH" "$DESTINATION"
"$FIREBASE_CLI" deploy --only hosting --project "$PROJECT_ID" --config firebase.json
