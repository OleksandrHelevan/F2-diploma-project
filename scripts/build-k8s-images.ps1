# DVO-L4-3C8B52
param(
    [Parameter()]
    [ValidatePattern('^[0-9]+\.[0-9]+\.[0-9]+$')]
    [string]$Tag = '1.0.0'
)

$ErrorActionPreference = 'Stop'
$images = @(
    @{ Name = 'bricklayers/web-app'; Path = 'src/web-app' },
    @{ Name = 'bricklayers/user-service'; Path = 'src/web-api/user-service' },
    @{ Name = 'bricklayers/project-service'; Path = 'src/web-api/market-service' },
    @{ Name = 'bricklayers/chat-service'; Path = 'src/web-api/chat-service' }
)

foreach ($image in $images) {
    $taggedImage = "$($image.Name):$Tag"
    docker build --tag $taggedImage $image.Path
    if ($LASTEXITCODE -ne 0) {
        throw "Docker build failed for $taggedImage"
    }
    minikube image load $taggedImage
    if ($LASTEXITCODE -ne 0) {
        throw "Minikube image load failed for $taggedImage"
    }
}
