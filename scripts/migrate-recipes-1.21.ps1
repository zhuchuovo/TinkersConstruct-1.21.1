param(
  [string]$SourceRoot = "src/generated/resources/data/tconstruct/recipes",
  [string]$DestinationRoot = "src/generated/resources/data/tconstruct/recipe",
  [string[]]$IncludeTopLevel = @("common", "compat", "smeltery", "tables", "tools"),
  [string[]]$IncludePathPrefix = @(),
  [switch]$Clean
)

$ErrorActionPreference = "Stop"
$workspace = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$source = (Resolve-Path (Join-Path $workspace $SourceRoot)).Path
$destination = [IO.Path]::GetFullPath((Join-Path $workspace $DestinationRoot))

if (-not $source.StartsWith($workspace, [StringComparison]::OrdinalIgnoreCase) -or
    -not $destination.StartsWith($workspace, [StringComparison]::OrdinalIgnoreCase)) {
  throw "Recipe migration paths must stay inside the workspace"
}
if ($Clean -and (Test-Path -LiteralPath $destination)) {
  $expectedDestination = [IO.Path]::GetFullPath((Join-Path $workspace "src/generated/resources/data/tconstruct/recipe"))
  if (-not $destination.Equals($expectedDestination, [StringComparison]::OrdinalIgnoreCase)) {
    throw "Refusing to clean a nonstandard destination: $destination"
  }
  Remove-Item -LiteralPath $destination -Recurse
}
[IO.Directory]::CreateDirectory($destination) | Out-Null

$resultBlock = [regex]::new(
  '(?ms)(^  "result"\s*:\s*\{\r?\n)(?<body>.*?)(\r?\n^  \})(?<comma>,?)',
  [Text.RegularExpressions.RegexOptions]::Multiline -bor [Text.RegularExpressions.RegexOptions]::Singleline)
$resultItem = [regex]::new('"item"\s*:', [Text.RegularExpressions.RegexOptions]::None)
$resultString = [regex]::new('(?m)^  "result"\s*:\s*"(?<id>[^"]+)"(?<comma>,?)$')
$noContainerSimple = [regex]::new(
  '(?ms)\{\r?\n(?<typeIndent>[ \t]*)"type"\s*:\s*"tconstruct:no_container",\r?\n(?<contentIndent>[ \t]*)"(?<kind>item|tag)"\s*:\s*"(?<value>[^"]+)"\r?\n(?<closeIndent>[ \t]*)\}')
$noContainerList = [regex]::new(
  '(?ms)\{\r?\n(?<typeIndent>[ \t]*)"type"\s*:\s*"tconstruct:no_container",\r?\n(?<contentIndent>[ \t]*)"match"\s*:\s*\[\r?\n(?<children>.*?)\r?\n[ \t]*\]\r?\n(?<closeIndent>[ \t]*)\}')
$anonymousIngredientList = [regex]::new(
  '(?ms)(?<indent>^[ \t]*)\[\r?\n(?<children>.*?)\r?\n\k<indent>\](?<comma>,?)',
  [Text.RegularExpressions.RegexOptions]::Multiline -bor [Text.RegularExpressions.RegexOptions]::Singleline)

$migrated = 0
$conditional = 0
$legacyNbt = 0
$outsideSelectedGroups = 0

foreach ($file in Get-ChildItem -LiteralPath $source -File -Recurse) {
  $relative = $file.FullName.Substring($source.Length).TrimStart([char]92, [char]47)
  $normalizedRelative = $relative.Replace([char]92, [char]47)
  $text = [IO.File]::ReadAllText($file.FullName)

  $topLevel = $relative.Split([char]92, [char]47)[0]
  $selected = $IncludeTopLevel -contains $topLevel
  if (-not $selected) {
    foreach ($rawPrefix in $IncludePathPrefix) {
      $prefix = $rawPrefix.Replace([char]92, [char]47).Trim([char]47)
      if ($prefix.Length -gt 0 -and
          ($normalizedRelative.Equals($prefix, [StringComparison]::OrdinalIgnoreCase) -or
           $normalizedRelative.StartsWith($prefix + '/', [StringComparison]::OrdinalIgnoreCase))) {
        $selected = $true
        break
      }
    }
  }
  if (-not $selected) {
    $outsideSelectedGroups++
    continue
  }

  # Forge 1.20 represented prioritized alternatives as one conditional recipe.
  # NeoForge 1.21 removed that serializer; these require a separate semantic
  # conversion and intentionally remain in the ignored legacy directory.
  if ($text -match '"type"\s*:\s*"neoforge:conditional"') {
    $conditional++
    continue
  }

  # These two retextured anvil recipes need their display-name NBT converted
  # to data components. Keep them isolated until that conversion is explicit.
  if ($text -match '(?ms)^  "result"\s*:\s*\{.*?"nbt"\s*:') {
    $legacyNbt++
    continue
  }

  $typeMatch = [regex]::Match($text, '(?m)^  "type"\s*:\s*"(?<type>[^"]+)"')
  if (-not $typeMatch.Success) {
    throw "Recipe has no root type: $($file.FullName)"
  }
  $type = $typeMatch.Groups['type'].Value

  # Recipe conditions moved from the Forge-era key to ConditionalOps' key.
  $text = [regex]::Replace($text, '(?m)^  "conditions"\s*:', '  "neoforge:conditions":')

  # Minecraft 1.21 uses ItemStack.CODEC for vanilla recipe results. Ingredient
  # objects still use the existing {"item": ...} syntax, so only touch the
  # root result object. Mantle's retextured crafting recipes also store a real
  # ItemStack result and follow the same schema.
  $usesVanillaItemStackResult = $type.StartsWith('minecraft:') -or
    $type -eq 'mantle:crafting_shaped_retextured' -or
    $type -eq 'tconstruct:crafting_shaped_materials' -or
    $type -eq 'tconstruct:crafting_shapeless_materials'
  if ($usesVanillaItemStackResult) {
    if ($resultString.IsMatch($text)) {
      $text = $resultString.Replace($text, {
        param($match)
        $id = $match.Groups['id'].Value
        $comma = $match.Groups['comma'].Value
        return "  `"result`": {`r`n    `"id`": `"$id`"`r`n  }$comma"
      }, 1)
    } else {
      $text = $resultBlock.Replace($text, {
        param($match)
        $body = $resultItem.Replace($match.Groups['body'].Value, '"id":', 1)
        return $match.Groups[1].Value + $body + $match.Groups[2].Value + $match.Groups['comma'].Value
      }, 1)
    }
  }

  # The vanilla item was renamed in 1.21.
  $text = $text.Replace('minecraft:scute', 'minecraft:turtle_scute')

  # NeoForge 1.21 standardized common tags under the c namespace. In addition
  # to ingredient/result "tag" fields, Mantle's tag-combination conditions
  # store tag IDs as bare strings in "match" arrays and "ignore" fields. Move
  # every quoted legacy namespace reference, then restore NeoForge serializer
  # IDs in "type" fields (ingredient and condition types are not tags).
  $text = $text.Replace('"forge:', '"c:').Replace('"neoforge:', '"c:')
  $text = $text.Replace('"c:conditions"', '"neoforge:conditions"')
  $text = [regex]::Replace($text, '("type"\s*:\s*")c:', '${1}neoforge:')

  # Custom ingredients use MapCodec fields in 1.21. Wrap the old inline
  # item/tag form, and represent the old ingredient arrays explicitly with
  # NeoForge's compound ingredient.
  $text = $noContainerSimple.Replace($text, {
    param($match)
    $typeIndent = $match.Groups['typeIndent'].Value
    $contentIndent = $match.Groups['contentIndent'].Value
    $closeIndent = $match.Groups['closeIndent'].Value
    $kind = $match.Groups['kind'].Value
    $value = $match.Groups['value'].Value
    return "{`r`n" +
      $typeIndent + '"type": "tconstruct:no_container",' + "`r`n" +
      $contentIndent + '"match": {' + "`r`n" +
      $contentIndent + '  "' + $kind + '": "' + $value + '"' + "`r`n" +
      $contentIndent + '}' + "`r`n" +
      $closeIndent + '}'
  })
  $text = $noContainerList.Replace($text, {
    param($match)
    $typeIndent = $match.Groups['typeIndent'].Value
    $contentIndent = $match.Groups['contentIndent'].Value
    $closeIndent = $match.Groups['closeIndent'].Value
    $children = $match.Groups['children'].Value
    return "{`r`n" +
      $typeIndent + '"type": "tconstruct:no_container",' + "`r`n" +
      $contentIndent + '"match": {' + "`r`n" +
      $contentIndent + '  "type": "neoforge:compound",' + "`r`n" +
      $contentIndent + '  "children": [' + "`r`n" +
      $children + "`r`n" +
      $contentIndent + '  ]' + "`r`n" +
      $contentIndent + '}' + "`r`n" +
      $closeIndent + '}'
  })

  # Forge's ingredient codec accepted an anonymous JSON array as an OR/compound
  # ingredient. NeoForge 1.21 still accepts that shorthand at a recipe field,
  # but not when the array is nested as a child of intersection/difference.
  # Generated modifier recipes use this anonymous nested form, so give those
  # lists an explicit compound ingredient type. Property-backed arrays such as
  # "inputs": [...] do not match because their opening bracket is not alone.
  $text = $anonymousIngredientList.Replace($text, {
    param($match)
    $indent = $match.Groups['indent'].Value
    $children = [regex]::Replace($match.Groups['children'].Value, '(?m)^', '  ')
    $comma = $match.Groups['comma'].Value
    return $indent + '{' + "`r`n" +
      $indent + '  "type": "neoforge:compound",' + "`r`n" +
      $indent + '  "children": [' + "`r`n" +
      $children + "`r`n" +
      $indent + '  ]' + "`r`n" +
      $indent + '}' + $comma
  })

  $target = Join-Path $destination $relative
  $parent = Split-Path -Parent $target
  [IO.Directory]::CreateDirectory($parent) | Out-Null
  [IO.File]::WriteAllText($target, $text, [Text.UTF8Encoding]::new($false))
  $migrated++
}

Write-Output "Migrated recipes: $migrated"
Write-Output "Deferred conditional recipes: $conditional"
Write-Output "Deferred NBT-result recipes: $legacyNbt"
Write-Output "Outside selected groups: $outsideSelectedGroups"
