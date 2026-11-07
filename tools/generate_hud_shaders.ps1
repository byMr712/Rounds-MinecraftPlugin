# Generates tools/assets/hud_shaders from the official Minecraft client jars.
#
# Every output file is the vanilla text vertex shader of the matching version with:
#   1) all #moj_import / #include directives replaced by the inlined include source;
#   2) the HUD glyph positioning block inserted into main();
#   3) every "Position" reference in main() replaced by the adjusted "pos".
#
# Run from the repository root:  powershell -NoProfile -ExecutionPolicy Bypass -File tools\generate_hud_shaders.ps1
param(
    [string]$WorkDir = "$env:TEMP\rounds-hud-shaders"
)

$ErrorActionPreference = "Stop"
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$outRoot = Join-Path $repoRoot "tools\assets\hud_shaders"
New-Item -ItemType Directory -Force -Path $WorkDir | Out-Null
Add-Type -AssemblyName System.IO.Compression.FileSystem

# Minecraft version -> output family directory. The folder names match the
# pack-format ranges declared in pack.mcmeta by ResourcePackBuilder.
$families = @(
    @{ Ver = "1.20.4";  Src = "rendertype_text.vsh"; Out = "base" },
    @{ Ver = "1.20.6";  Src = "rendertype_text.vsh"; Out = "overlay_32_41" },
    @{ Ver = "1.21.4";  Src = "rendertype_text.vsh"; Out = "overlay_42_62" },
    @{ Ver = "1.21.8";  Src = "rendertype_text.vsh"; Out = "overlay_63_68" },
    @{ Ver = "1.21.10"; Src = "rendertype_text.vsh"; Out = "overlay_69_74" },
    @{ Ver = "1.21.11"; Src = "rendertype_text.vsh"; Out = "overlay_75_83" },
    @{ Ver = "26.1.2";  Src = "rendertype_text.vsh"; Out = "overlay_84_87" },
    @{ Ver = "26.2";    Src = "text.vsh";            Out = "overlay_88_96" },
    @{ Ver = "26.3";    Src = "text.vsh";            Out = "overlay_97_99" }
)

$manifestPath = Join-Path $WorkDir "version_manifest_v2.json"
if (-not (Test-Path $manifestPath)) {
    Invoke-WebRequest -Uri "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json" -OutFile $manifestPath -UseBasicParsing -TimeoutSec 60
}
$manifest = Get-Content $manifestPath -Raw | ConvertFrom-Json

$defines = @(
    "#define HEIGHT_BIT 13",
    "#define MAX_BIT 10",
    "#define ADD_OFFSET 4095",
    "#define DEFAULT_OFFSET 29"
)

$snippet = @(
    "    vec3 pos = Position;",
    "    vec2 ui = ceil(2.0 / vec2(ProjMat[0][0], -ProjMat[1][1]));",
    "",
    "    // HUD glyphs encode their target position in a huge ascent; decode it here.",
    "    if (ProjMat[3].x == -1.0 && pos.y >= ui.y) {",
    "        int bits = int(pos.y) >> HEIGHT_BIT;",
    "        if (((bits >> MAX_BIT) & 1) == 1) {",
    "            int id = bits - (1 << MAX_BIT);",
    "            // The boss bar centres its title, so move the origin to the left edge.",
    "            pos.x -= 0.5 * ui.x;",
    "            pos.y -= float((bits << HEIGHT_BIT) + ADD_OFFSET + DEFAULT_OFFSET);",
    "            float xGui = 0.0;",
    "            float yGui = 0.0;",
    "            float layer = 0.0;",
    "            switch (id) {",
    "                case 1:",
    "                    xGui = ui.x * 2.0 / 100.0;",
    "                    yGui = ui.y * 4.0 / 100.0;",
    "                    break;",
    "                case 2:",
    "                    xGui = ui.x * 2.0 / 100.0;",
    "                    yGui = ui.y * 4.0 / 100.0;",
    "                    layer = 1.0;",
    "                    break;",
    "            }",
    "            pos.x += xGui;",
    "            pos.y += yGui;",
    "            pos.z += layer;",
    "        }",
    "    }"
)

if (Test-Path $outRoot) { Remove-Item -Recurse -Force $outRoot }

foreach ($f in $families) {
    $verDir = Join-Path $WorkDir $f.Ver
    $coreDir = Join-Path $verDir "assets\minecraft\shaders\core"
    $incDir = Join-Path $verDir "assets\minecraft\shaders\include"
    if (-not (Test-Path (Join-Path $coreDir $f.Src))) {
        $entry = $manifest.versions | Where-Object { $_.id -eq $f.Ver } | Select-Object -First 1
        if (-not $entry) { throw "Minecraft version $($f.Ver) not found in the manifest" }
        $vj = Join-Path $WorkDir "$($f.Ver).json"
        Invoke-WebRequest -Uri $entry.url -OutFile $vj -UseBasicParsing -TimeoutSec 60
        $clientUrl = (Get-Content $vj -Raw | ConvertFrom-Json).downloads.client.url
        $jar = Join-Path $WorkDir "$($f.Ver)-client.jar"
        Write-Output "Downloading Minecraft $($f.Ver) client..."
        Invoke-WebRequest -Uri $clientUrl -OutFile $jar -UseBasicParsing -TimeoutSec 600
        $zip = [System.IO.Compression.ZipFile]::OpenRead($jar)
        $entries = $zip.Entries | Where-Object {
            $_.FullName -match '^assets/minecraft/shaders/(core/(rendertype_text|text)\.vsh|include/.*\.glsl)$'
        }
        foreach ($e in $entries) {
            $target = Join-Path $verDir ($e.FullName -replace "/", "\")
            New-Item -ItemType Directory -Force -Path (Split-Path $target) | Out-Null
            [System.IO.Compression.ZipFileExtensions]::ExtractToFile($e, $target, $true)
        }
        $zip.Dispose()
    }

    $srcPath = Join-Path $coreDir $f.Src
    $result = New-Object System.Collections.Generic.List[string]
    $inMain = $false
    foreach ($line in (Get-Content $srcPath -Encoding UTF8)) {
        if ($line -match '^\s*#(?:moj_import|include)\s+<([^>]+)>\s*$') {
            $incName = $Matches[1]
            if ($incName.StartsWith("minecraft:")) { $incName = $incName.Substring("minecraft:".Length) }
            $incPath = Join-Path $incDir $incName
            if (-not (Test-Path $incPath)) { throw "Missing include $incPath for $($f.Ver)" }
            foreach ($incLine in (Get-Content $incPath -Encoding UTF8)) {
                if ($incLine -match '^\s*#version\s') { continue }
                $result.Add($incLine)
            }
            continue
        }
        if (-not $inMain -and $line.Trim() -eq "void main() {") {
            foreach ($d in $defines) { $result.Add($d) }
            $result.Add("")
            $result.Add($line)
            foreach ($s in $snippet) { $result.Add($s) }
            $inMain = $true
            continue
        }
        if ($inMain) {
            $result.Add(($line -replace '\bPosition\b', 'pos'))
        } else {
            $result.Add($line)
        }
    }

    $dest = Join-Path $outRoot (Join-Path $f.Out ("assets\minecraft\shaders\core\" + $f.Src))
    New-Item -ItemType Directory -Force -Path (Split-Path $dest) | Out-Null
    [System.IO.File]::WriteAllText($dest, ($result -join "`n"), (New-Object System.Text.UTF8Encoding($false)))
    Write-Output "Generated $($f.Out)/$($f.Src) from Minecraft $($f.Ver)"
}
