@echo off
echo Starting build process...
set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
echo JAVA_HOME is set to: %JAVA_HOME%
cd /d C:\Users\pcw99\AndroidStudioProjects\hairme_frontend
echo Current directory: %CD%
echo Checking if gradlew.bat exists...
if exist gradlew.bat (
    echo gradlew.bat found!
    call gradlew.bat bundleRelease
    echo Build completed with exit code: %ERRORLEVEL%
) else (
    echo ERROR: gradlew.bat not found!
)
