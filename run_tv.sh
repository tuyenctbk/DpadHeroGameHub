#!/bin/bash
# Set SDK Path if needed
export ANDROID_HOME="$HOME/Library/Android/sdk"
export PATH="$PATH:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator"

# Locate Android Studio bundled JDK if JAVA_HOME is not set
if [ -z "$JAVA_HOME" ]; then
    if [ -d "/Applications/Android Studio.app/Contents/jbr/Contents/Home" ]; then
        export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
    elif [ -d "/Applications/Android Studio.app/Contents/jre/Contents/Home" ]; then
        export JAVA_HOME="/Applications/Android Studio.app/Contents/jre/Contents/Home"
    fi
fi

echo "=== Updating Translation Resource Files ==="
python3 translate.py

echo "=== Checking Connected Devices ==="
adb devices

echo "=== Building and Installing Game Hub on emulator-5554 ==="
./gradlew installDebug

echo "=== Launching SplashActivity on emulator-5554 ==="
adb -s emulator-5554 shell am start -n com.tdpham.dpadarcade/com.tdpham.games.hub.SplashActivity
