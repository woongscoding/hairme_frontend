$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
Write-Host "JAVA_HOME set to: $env:JAVA_HOME"
Set-Location "C:\Users\pcw99\AndroidStudioProjects\hairme_frontend"
Write-Host "Current directory: $(Get-Location)"
Write-Host "Starting bundleRelease..."
& .\gradlew.bat bundleRelease
Write-Host "Build finished with exit code: $LASTEXITCODE"
