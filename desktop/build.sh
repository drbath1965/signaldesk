#!/bin/sh
# Builds SignalDesk-<version>.jar with any JDK 17 or newer. No other dependencies.
# The version comes from the VERSION file next to this script.
set -e
cd "$(dirname "$0")"
VERSION=$(tr -d ' \r\n' < VERSION)
rm -rf out && mkdir out
javac --release 17 -d out $(find src -name '*.java')
cp -r res/fonts out/
printf 'Main-Class: com.signaldesk.App\nImplementation-Title: Signal Desk\nImplementation-Version: %s\n' "$VERSION" > manifest.txt
rm -f SignalDesk-*.jar SignalDesk.jar
jar cfm "SignalDesk-$VERSION.jar" manifest.txt -C out .
echo "Built SignalDesk-$VERSION.jar"
