#!/usr/bin/env sh
GRADLE_BIN="/c/Users/Rishit/.gradle/wrapper/dists/gradle-8.14.3-all/10utluxaxniiv4wxiphsi49nj/gradle-8.14.3/bin/gradle"
if [ -f "$GRADLE_BIN" ]; then
    exec "$GRADLE_BIN" "$@"
else
    exec gradle "$@"
fi
