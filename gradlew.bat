@echo off
set JAVA_TOOL_OPTIONS=-Djava.net.preferIPv6Addresses=true -Djava.net.preferIPv4Stack=false
set GRADLE_BIN=C:\Users\Rishit\.gradle\wrapper\dists\gradle-8.14.3-all\10utluxaxniiv4wxiphsi49nj\gradle-8.14.3\bin\gradle.bat
if exist "%GRADLE_BIN%" (
    call "%GRADLE_BIN%" %*
) else (
    gradle %*
)
