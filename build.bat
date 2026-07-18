@echo off
set JAVA_HOME=C:\Program Files\Java\jdk-17
.\gradlew.bat build -x test --no-daemon
pause
