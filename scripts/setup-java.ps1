[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"

function Test-Jdk21 {
  param([string]$JavaRoot)

  if ([string]::IsNullOrWhiteSpace($JavaRoot)) {
    return $false
  }

  $normalizedRoot = [IO.Path]::GetFullPath($JavaRoot.Trim('"'))
  $java = Join-Path $normalizedRoot "bin/java.exe"
  $javac = Join-Path $normalizedRoot "bin/javac.exe"
  if (-not (Test-Path -LiteralPath $java -PathType Leaf) -or
      -not (Test-Path -LiteralPath $javac -PathType Leaf)) {
    return $false
  }

  $versionOutput = & $java --version | Out-String
  $isJava21 = $versionOutput -match '(?m)^(?:openjdk|java)\s+21(?:\.|\s)' -or
              $versionOutput -match 'version "21(?:\.|\")'
  return $LASTEXITCODE -eq 0 -and $isJava21
}

function Add-Candidate {
  param(
    [System.Collections.Generic.List[string]]$Candidates,
    [string]$Path
  )

  if (-not [string]::IsNullOrWhiteSpace($Path)) {
    $normalized = [IO.Path]::GetFullPath($Path.Trim('"'))
    if (-not $Candidates.Contains($normalized)) {
      $Candidates.Add($normalized)
    }
  }
}

$candidates = [System.Collections.Generic.List[string]]::new()
Add-Candidate $candidates $env:JAVA_HOME
Add-Candidate $candidates ([Environment]::GetEnvironmentVariable("JAVA_HOME", "User"))
Add-Candidate $candidates ([Environment]::GetEnvironmentVariable("JAVA_HOME", "Machine"))

$roaming = [Environment]::GetFolderPath("ApplicationData")
Add-Candidate $candidates (Join-Path $roaming ".minecraft/runtime/java-runtime-delta")

$runtimeRoot = Join-Path $roaming ".minecraft/runtime"
if (Test-Path -LiteralPath $runtimeRoot -PathType Container) {
  Get-ChildItem -LiteralPath $runtimeRoot -Recurse -Filter javac.exe -File -ErrorAction SilentlyContinue |
    ForEach-Object {
      Add-Candidate $candidates (Split-Path -Parent (Split-Path -Parent $_.FullName))
    }
}

foreach ($root in @(
  "$env:ProgramFiles\Java",
  "$env:ProgramFiles\Microsoft",
  "$env:ProgramFiles\Eclipse Adoptium",
  "$env:ProgramFiles\Zulu",
  "$env:LOCALAPPDATA\Programs\Eclipse Adoptium",
  "$env:USERPROFILE\.jdks",
  "$env:USERPROFILE\.gradle\jdks"
)) {
  if (Test-Path -LiteralPath $root -PathType Container) {
    Get-ChildItem -LiteralPath $root -Recurse -Filter javac.exe -File -ErrorAction SilentlyContinue |
      ForEach-Object {
        Add-Candidate $candidates (Split-Path -Parent (Split-Path -Parent $_.FullName))
      }
  }
}

$javaRoot = $candidates | Where-Object { Test-Jdk21 $_ } | Select-Object -First 1
if (-not $javaRoot) {
  throw "No JDK 21 installation containing both java.exe and javac.exe was found."
}

[Environment]::SetEnvironmentVariable("JAVA_HOME", $javaRoot, "User")

$userPath = [Environment]::GetEnvironmentVariable("Path", "User")
$javaPathToken = "%JAVA_HOME%\bin"
$pathEntries = @($userPath -split ';' | Where-Object {
  -not [string]::IsNullOrWhiteSpace($_) -and
  $_.TrimEnd('\') -ine $javaPathToken.TrimEnd('\')
})
$newUserPath = (@($javaPathToken) + $pathEntries) -join ';'
[Environment]::SetEnvironmentVariable("Path", $newUserPath, "User")

$env:JAVA_HOME = $javaRoot
$env:Path = "$javaRoot\bin;$env:Path"

Write-Output "JAVA_HOME=$javaRoot"
& (Join-Path $javaRoot "bin/java.exe") --version
& (Join-Path $javaRoot "bin/javac.exe") -version
Write-Output "Configured JAVA_HOME and user Path. Open a new terminal for other processes to inherit them."
