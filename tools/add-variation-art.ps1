$ErrorActionPreference = "Stop"
$cardsDir = "E:\GitHub\Rounds-MinecraftPlugin\rounds-core\src\main\resources\cards"
$files = Get-ChildItem $cardsDir -Recurse -Filter "*.yml"
$changed = 0
$variations = 0

foreach ($f in $files) {
    $text = [IO.File]::ReadAllText($f.FullName, [System.Text.Encoding]::UTF8)
    if ($text -notmatch '(?m)^\s*variations\s*:') { continue }
    $eol = if ($text -match "`r`n") { "`r`n" } else { "`n" }
    $lines = $text -split "`r?`n"
    $out = New-Object System.Collections.Generic.List[string]
    $inVar = $false
    $pendingArt = $null
    $id = 0
    foreach ($line in $lines) {
        if ($id -eq 0 -and $line -match '^id\s*:\s*(\d+)\s*$') { $id = [int]$Matches[1] }
        if ($line -match '^\s*variations\s*:') { $inVar = $true }
        if ($inVar -and $line -match '^\s*-\s+rarity\s*:\s*([A-Za-z]+)') {
            $currentRarity = $Matches[1].ToLower()
            $pendingArt = "card${id}-$currentRarity"
            $out.Add($line)
            continue
        }
        if ($pendingArt -ne $null) {
            if ($line -match '^\s+art\s*:') {
                $pendingArt = $null
            } else {
                $out.Add("    art: $pendingArt")
                $variations++
                $pendingArt = $null
            }
        }
        $out.Add($line)
    }
    if ($pendingArt -ne $null) {
        $out.Add("    art: $pendingArt")
        $variations++
    }
    $newText = [string]::Join($eol, $out)
    if ($newText -ne $text) {
        [IO.File]::WriteAllText($f.FullName, $newText, [System.Text.UTF8Encoding]::new($false))
        $changed++
    }
}
"Updated $changed files, added $variations variation art keys."
