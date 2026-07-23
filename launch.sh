#!/bin/bash
export PATH=$PATH:/Users/user/Library/Android/sdk/platform-tools:/Users/user/Library/Android/sdk/emulator
echo "=== Listing AVDs ==="
emulator -list-avds

echo "=== Starting Android TV AVD ==="
# Start emulator in background
emulator -avd Television_1080p -no-snapshot-load &
sleep 15

echo "=== Waiting for device to be ready ==="
adb wait-for-device

echo "=== Building and Installing Game Hub ==="
./gradlew installDebug

echo "=== Launching SplashActivity on TV AVD ==="
adb shell am start -n com.tdpham.games/com.tdpham.games.hub.SplashActivity
