#!/bin/sh
# Builds SignalDesk.jar with any JDK 17 or newer. No other dependencies.
set -e
rm -rf out && mkdir out
javac --release 17 -d out $(find src -name '*.java')
cp -r res/fonts out/
printf 'Main-Class: com.signaldesk.App\nImplementation-Title: Signal Desk\nImplementation-Version: 1.0.0\n' > manifest.txt
jar cfm SignalDesk.jar manifest.txt -C out .
echo "Built SignalDesk.jar"
