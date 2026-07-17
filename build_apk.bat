@echo off
set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr
echo Starting build...
call gradlew.bat assembleRelease > build_output.txt 2>&1
echo Build completed. Exit code: %ERRORLEVEL%
type build_output.txt
