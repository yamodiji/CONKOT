# Download Gradle 8.4 Wrapper JAR
$url = "https://github.com/gradle/gradle/raw/v8.4.0/gradle/wrapper/gradle-wrapper.jar"
$output = "gradle/wrapper/gradle-wrapper.jar"

Write-Host "Downloading Gradle wrapper JAR..."
try {
    Invoke-WebRequest -Uri $url -OutFile $output -UseBasicParsing
    Write-Host "✅ Successfully downloaded gradle-wrapper.jar"
} catch {
    Write-Host "❌ Failed to download from GitHub, trying services.gradle.org..."
    $fallbackUrl = "https://raw.githubusercontent.com/gradle/gradle/v8.4.0/gradle/wrapper/gradle-wrapper.jar"
    Invoke-WebRequest -Uri $fallbackUrl -OutFile $output -UseBasicParsing
    Write-Host "✅ Successfully downloaded gradle-wrapper.jar from fallback"
}

# Verify file was created
if (Test-Path $output) {
    $size = (Get-Item $output).Length
    Write-Host "File size: $size bytes"
} else {
    Write-Host "❌ Failed to create gradle-wrapper.jar"
} 