param(
  [string]$SourceRoot = "src/generated/resources/data/forge/tags",
  [string]$DestinationRoot = "src/generated/resources/data/c/tags"
)

$ErrorActionPreference = "Stop"
$workspace = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$source = (Resolve-Path (Join-Path $workspace $SourceRoot)).Path
$destination = [IO.Path]::GetFullPath((Join-Path $workspace $DestinationRoot))

if (-not $source.StartsWith($workspace, [StringComparison]::OrdinalIgnoreCase) -or
    -not $destination.StartsWith($workspace, [StringComparison]::OrdinalIgnoreCase)) {
  throw "Tag migration paths must stay inside the workspace"
}

[IO.Directory]::CreateDirectory($destination) | Out-Null
$migrated = 0

foreach ($file in Get-ChildItem -LiteralPath $source -File -Recurse) {
  $relative = $file.FullName.Substring($source.Length).TrimStart([char]92, [char]47)
  $text = [IO.File]::ReadAllText($file.FullName)

  # References to other shared tags move with the definitions. References to
  # TConstruct and Minecraft tags keep their original namespaces.
  $text = $text.Replace('#forge:', '#c:').Replace('#neoforge:', '#c:')

  $target = Join-Path $destination $relative
  [IO.Directory]::CreateDirectory((Split-Path -Parent $target)) | Out-Null
  [IO.File]::WriteAllText($target, $text, [Text.UTF8Encoding]::new($false))
  $migrated++
}

Write-Output "Migrated common tags: $migrated"
