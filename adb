#!/bin/bash
REAL_ADB=$ANDROID_HOME/platform-tools/adb

exec $REAL_ADB "$@"