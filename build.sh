#!/usr/bin/env bash
# Builds a signed release APK inside Docker. Output: dist/ThumbEdge-<version>.apk
# The Android SDK and Gradle caches live in the named volumes android-sdk and gradle-cache.
set -euo pipefail
cd "$(dirname "$0")"
NAME=thumbedge-build
docker rm -f "$NAME" >/dev/null 2>&1 || true
tar --exclude=./build --exclude=./app/build --exclude=./.gradle --exclude=./dist --exclude=./.git -cf - . |
docker run -i --name "$NAME" -v android-sdk:/sdk -v gradle-cache:/home/gradle/.gradle -e ANDROID_HOME=/sdk \
  gradle:8.14.3-jdk21 bash -c '
set -e
mkdir -p /work && tar -xf - -C /work && cd /work
echo "sdk.dir=/sdk" > local.properties
if [ ! -f keystore.properties ]; then
  PW=$(head -c 18 /dev/urandom | base64 | tr -dc A-Za-z0-9)
  keytool -genkeypair -keystore thumbedge-release.jks -alias thumbedge -keyalg RSA -keysize 3072 -validity 36500 \
    -storepass "$PW" -keypass "$PW" -dname "CN=Thumb Edge" >/dev/null 2>&1
  printf "storeFile=thumbedge-release.jks\nstorePassword=%s\nkeyAlias=thumbedge\nkeyPassword=%s\n" "$PW" "$PW" > keystore.properties
fi
[ -f gradlew ] || gradle wrapper --gradle-version 8.14.3 -q
gradle assembleRelease --no-daemon --console=plain -q 2>&1 | grep -vE "^w: |warning:" || true
ls app/build/outputs/apk/release/
'
mkdir -p dist
docker cp "$NAME":/work/app/build/outputs/apk/release/. dist/release >/dev/null 2>&1 || true
for f in keystore.properties thumbedge-release.jks gradlew gradlew.bat gradle; do
  [ -e "$f" ] || docker cp "$NAME":/work/$f ./ >/dev/null 2>&1 || true
done
docker rm "$NAME" >/dev/null
VER=$(grep -oP 'versionName = "\K[^"]+' app/build.gradle.kts)
if [ -f dist/release/app-release.apk ]; then
  cp dist/release/app-release.apk "dist/ThumbEdge-$VER.apk"
  echo "APK: $(pwd)/dist/ThumbEdge-$VER.apk"
else
  echo "BUILD FAILED"; exit 1
fi
