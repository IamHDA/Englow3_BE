[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$pipelineRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$repoRoot = [IO.Path]::GetFullPath((Join-Path $pipelineRoot '..'))
$images = [IO.Path]::GetFullPath((Join-Path $pipelineRoot 'output\media\images'))
$listening = [IO.Path]::GetFullPath((Join-Path $pipelineRoot 'output\media\audio\toeic\listening'))
$speaking = [IO.Path]::GetFullPath((Join-Path $pipelineRoot 'output\media\audio\toeic\speaking'))
$flashcards = [IO.Path]::GetFullPath((Join-Path $pipelineRoot 'output\media\audio\flashcards'))
$shadowing = [IO.Path]::GetFullPath((Join-Path $pipelineRoot 'output\media\audio\shadowing'))

foreach ($path in @($images, $listening, $speaking, $flashcards, $shadowing)) {
    if (-not $path.StartsWith($pipelineRoot) -or -not (Test-Path -LiteralPath $path)) {
        throw "Missing or unsafe media directory: $path"
    }
}

Push-Location $repoRoot
try {
    docker compose up -d minio
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to start MinIO (docker exit code $LASTEXITCODE)"
    }
    docker compose run --rm --no-deps --entrypoint /bin/sh `
        --volume "${images}:/media-images:ro" `
        --volume "${listening}:/media-listening:ro" `
        --volume "${speaking}:/media-speaking:ro" `
        --volume "${flashcards}:/media-flashcards:ro" `
        --volume "${shadowing}:/media-shadowing:ro" `
        minio-init -c @'
set -eu
until mc alias set local http://minio:9000 minioadmin minioadmin; do sleep 1; done
mc mb --ignore-existing local/images
mc mb --ignore-existing local/audio
mc anonymous set download local/images
mc anonymous set download local/audio
mc mirror --overwrite /media-images local/images
mc mirror --overwrite /media-listening local/images/toeic/listening/audio
mc mirror --overwrite /media-speaking local/images/toeic/speaking-writing/speaking/audio
mc mirror --overwrite /media-flashcards local/audio/flashcards
mc mirror --overwrite /media-shadowing local/audio/shadowing
mc stat local/images/toeic/listening/part1/warehouse_boxes.jpg >/dev/null
mc find local/audio/flashcards --name '*.mp3' | head -n 1 | grep -q .
mc find local/audio/shadowing --name '*.mp3' | head -n 1 | grep -q .
echo "Media upload complete: http://localhost:9000/images/toeic/ and http://localhost:9000/audio/"
'@
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to upload or verify media (docker exit code $LASTEXITCODE)"
    }
}
finally {
    Pop-Location
}
