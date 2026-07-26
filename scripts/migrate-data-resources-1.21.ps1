param(
  [string]$DataRoot = "src/generated/resources/data/tconstruct"
)

$ErrorActionPreference = "Stop"
$workspace = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$mantleWorkspace = [IO.Path]::GetFullPath((Join-Path $workspace "../Mantle-1.21.1"))
if (-not (Test-Path -LiteralPath $mantleWorkspace -PathType Container)) {
  throw "Mantle project not found: $mantleWorkspace"
}
$data = (Resolve-Path (Join-Path $workspace $DataRoot)).Path
$namespaceRoot = Split-Path -Parent $data

if (-not $data.StartsWith($workspace, [StringComparison]::OrdinalIgnoreCase)) {
  throw "Data migration path must stay inside the workspace"
}

function Has-Property($Value, [string]$Name) {
  return $null -ne $Value -and $null -ne $Value.PSObject.Properties[$Name]
}

function Set-Property($Value, [string]$Name, $PropertyValue) {
  if (Has-Property $Value $Name) {
    $Value.$Name = $PropertyValue
  } else {
    $Value | Add-Member -MemberType NoteProperty -Name $Name -Value $PropertyValue
  }
}

function Move-Property($Value, [string]$OldName, [string]$NewName) {
  if (Has-Property $Value $OldName) {
    $propertyValue = $Value.$OldName
    $Value.PSObject.Properties.Remove($OldName)
    Set-Property $Value $NewName $propertyValue
  }
}

function Convert-AdvancementNode($Node) {
  if ($null -eq $Node -or $Node -is [string] -or $Node.GetType().IsPrimitive) {
    return
  }
  if ($Node -is [Array]) {
    foreach ($child in $Node) {
      Convert-AdvancementNode $child
    }
    return
  }

  # Advancement icons became ItemStack.CODEC values in 1.21.
  if (Has-Property $Node "icon") {
    $icon = $Node.icon
    Move-Property $icon "item" "id"
    if (Has-Property $icon "nbt") {
      $components = [pscustomobject][ordered]@{
        "minecraft:custom_data" = [string]$icon.nbt
      }
      $icon.PSObject.Properties.Remove("nbt")
      Set-Property $icon "components" $components
    }
  }

  # ItemPredicate removed its top-level NBT predicate in favor of item
  # sub-predicates. All generated advancement objects with both fields are
  # item predicates (entity predicates do not have an "items" field).
  if ((Has-Property $Node "items") -and (Has-Property $Node "nbt")) {
    $predicates = [pscustomobject][ordered]@{
      "minecraft:custom_data" = [string]$Node.nbt
    }
    $Node.PSObject.Properties.Remove("nbt")
    Set-Property $Node "predicates" $predicates
  }

  foreach ($property in @($Node.PSObject.Properties)) {
    Convert-AdvancementNode $property.Value
  }
}

function Convert-LootNode($Node) {
  if ($null -eq $Node -or $Node -is [string] -or $Node.GetType().IsPrimitive) {
    return
  }
  if ($Node -is [Array]) {
    foreach ($child in $Node) {
      Convert-LootNode $child
    }
    return
  }

  if (Has-Property $Node "function") {
    if ($Node.function -eq "minecraft:copy_nbt") {
      $Node.function = "minecraft:copy_custom_data"
    } elseif ($Node.function -eq "minecraft:looting_enchant") {
      $Node.function = "minecraft:enchanted_count_increase"
      Set-Property $Node "enchantment" "minecraft:looting"
    }
  }

  if ((Has-Property $Node "condition") -and $Node.condition -eq "forge:can_tool_perform_action") {
    $Node.condition = "neoforge:can_item_perform_ability"
    Move-Property $Node "action" "ability"
  }

  # The old ItemPredicate enchantment list became the enchantments item
  # sub-predicate. The entry field also changed from singular to plural.
  if (Has-Property $Node "enchantments") {
    $enchantments = $Node.enchantments
    foreach ($entry in @($enchantments)) {
      Move-Property $entry "enchantment" "enchantments"
    }
    $predicates = [pscustomobject][ordered]@{
      "minecraft:enchantments" = $enchantments
    }
    $Node.PSObject.Properties.Remove("enchantments")
    Set-Property $Node "predicates" $predicates
  }

  foreach ($property in @($Node.PSObject.Properties)) {
    Convert-LootNode $property.Value
  }
}

function Convert-BookIngredientNode($Node) {
  if ($null -eq $Node -or $Node -is [string] -or $Node.GetType().IsPrimitive) {
    return
  }
  if ($Node -is [Array]) {
    foreach ($child in $Node) {
      Convert-BookIngredientNode $child
    }
    return
  }

  # Forge's NBT ingredient was replaced by NeoForge's component ingredient.
  # Tinkers' legacy tool data remains custom data, while the vanilla Damage
  # field became the dedicated minecraft:damage component in 1.21.
  if ((Has-Property $Node "type") -and $Node.type -eq "forge:nbt") {
    $components = [ordered]@{}
    $customData = [ordered]@{}
    if (Has-Property $Node "nbt") {
      foreach ($property in @($Node.nbt.PSObject.Properties)) {
        if ($property.Name -eq "Damage") {
          $components["minecraft:damage"] = $property.Value
        } else {
          $customData[$property.Name] = $property.Value
        }
      }
    }
    if ($customData.Count -gt 0) {
      $components["minecraft:custom_data"] = [pscustomobject]$customData
    }

    $Node.type = "neoforge:components"
    Move-Property $Node "item" "items"
    $Node.PSObject.Properties.Remove("nbt")
    Set-Property $Node "components" ([pscustomobject]$components)
    $script:bookIngredientCount++
  }

  foreach ($property in @($Node.PSObject.Properties)) {
    Convert-BookIngredientNode $property.Value
  }
}

function Write-Json($Value, [string]$Target) {
  $parent = Split-Path -Parent $Target
  [IO.Directory]::CreateDirectory($parent) | Out-Null
  $text = $Value | ConvertTo-Json -Depth 100
  [IO.File]::WriteAllText($Target, $text + "`r`n", [Text.UTF8Encoding]::new($false))
}

$advancementSource = Join-Path $data "advancements"
$advancementDestination = Join-Path $data "advancement"
$advancementCount = 0
foreach ($file in Get-ChildItem -LiteralPath $advancementSource -File -Recurse) {
  $relative = $file.FullName.Substring($advancementSource.Length).TrimStart([char]92, [char]47)
  $advancement = [IO.File]::ReadAllText($file.FullName) | ConvertFrom-Json

  # Forge's old conditional advancement builder emitted a one-element wrapper.
  # NeoForge 1.21 places conditions alongside the advancement fields.
  if (Has-Property $advancement "advancements") {
    $entries = @($advancement.advancements)
    if ($entries.Count -eq 0 -or @($entries | Where-Object { -not (Has-Property $_ "advancement") }).Count -gt 0) {
      throw "Unsupported conditional advancement wrapper: $($file.FullName)"
    }

    # ConditionalAdvancement's fallback form can contain multiple entries with
    # the exact same payload (for example, mod-loaded followed by true). Such a
    # resource is unconditional regardless of which entry matches, so keep the
    # shared advancement without inventing a condition-expression format.
    $payloads = @($entries | ForEach-Object { $_.advancement | ConvertTo-Json -Depth 100 -Compress })
    if (@($payloads | Sort-Object -Unique).Count -ne 1) {
      throw "Conditional advancement alternatives have different payloads: $($file.FullName)"
    }

    $entry = $entries[0]
    $advancement = $entry.advancement
    if ($entries.Count -eq 1 -and (Has-Property $entry "conditions")) {
      Set-Property $advancement "neoforge:conditions" $entry.conditions
    }
  }

  Convert-AdvancementNode $advancement
  Write-Json $advancement (Join-Path $advancementDestination $relative)
  $advancementCount++
}

$lootSource = Join-Path $data "loot_tables"
$lootDestination = Join-Path $data "loot_table"
$lootCount = 0
foreach ($file in Get-ChildItem -LiteralPath $lootSource -File -Recurse) {
  $relative = $file.FullName.Substring($lootSource.Length).TrimStart([char]92, [char]47)
  $loot = [IO.File]::ReadAllText($file.FullName) | ConvertFrom-Json
  Convert-LootNode $loot
  Write-Json $loot (Join-Path $lootDestination $relative)
  $lootCount++
}

# Global loot modifiers embed vanilla loot functions and conditions, so they
# require the same 1.21 conversion as ordinary loot tables.
$lootModifierSource = Join-Path $data "loot_modifiers"
$lootModifierCount = 0
if (Test-Path -LiteralPath $lootModifierSource) {
  foreach ($file in Get-ChildItem -LiteralPath $lootModifierSource -Filter "*.json" -File -Recurse) {
    $lootModifier = [IO.File]::ReadAllText($file.FullName) | ConvertFrom-Json
    Convert-LootNode $lootModifier
    Write-Json $lootModifier $file.FullName
    $lootModifierCount++
  }
}

# NeoForge owns the global loot modifier index. The namespace changed from
# Forge's data/forge location, while the index payload itself stayed the same.
$legacyGlobalLootModifiers = Join-Path $namespaceRoot "forge/loot_modifiers/global_loot_modifiers.json"
$neoForgeGlobalLootModifiers = Join-Path $namespaceRoot "neoforge/loot_modifiers/global_loot_modifiers.json"
$globalLootModifierCount = 0
if (Test-Path -LiteralPath $legacyGlobalLootModifiers) {
  $globalLootModifiers = [IO.File]::ReadAllText($legacyGlobalLootModifiers) | ConvertFrom-Json
  Write-Json $globalLootModifiers $neoForgeGlobalLootModifiers
  $globalLootModifierCount = @($globalLootModifiers.entries).Count
}

# Mantle is stored as a sibling Gradle project. Its block and fluid tag registry folders
# also changed from plural to singular in 1.21.
$mantleGeneratedResources = Join-Path $mantleWorkspace "src/generated/resources"
$mantleTagCount = 0
foreach ($tagType in @(@("blocks", "block"), @("fluids", "fluid"))) {
  $source = Join-Path $mantleGeneratedResources "data/mantle/tags/$($tagType[0])"
  $destination = Join-Path $mantleGeneratedResources "data/mantle/tags/$($tagType[1])"
  if (Test-Path -LiteralPath $source) {
    foreach ($file in Get-ChildItem -LiteralPath $source -Filter "*.json" -File -Recurse) {
      $relative = $file.FullName.Substring($source.Length).TrimStart([char]92, [char]47)
      $target = Join-Path $destination $relative
      [IO.Directory]::CreateDirectory((Split-Path -Parent $target)) | Out-Null
      [IO.File]::WriteAllText($target, [IO.File]::ReadAllText($file.FullName), [Text.UTF8Encoding]::new($false))
      $mantleTagCount++
    }
  }
}

# Mantle books embed ingredients in client assets. Convert only files that
# contain the removed Forge NBT ingredient; some unrelated community
# translations contain legacy malformed JSON and are intentionally untouched.
$bookRoot = Join-Path $workspace "src/main/resources/assets/tconstruct/book"
$bookIngredientCount = 0
$bookIngredientFileCount = 0
if (Test-Path -LiteralPath $bookRoot) {
  foreach ($file in Get-ChildItem -LiteralPath $bookRoot -Filter "*.json" -File -Recurse) {
    $original = [IO.File]::ReadAllText($file.FullName)
    if (-not $original.Contains('"forge:nbt"')) {
      continue
    }
    $book = $original | ConvertFrom-Json
    $before = $bookIngredientCount
    Convert-BookIngredientNode $book
    if ($bookIngredientCount -gt $before) {
      Write-Json $book $file.FullName
      $bookIngredientFileCount++
    }
  }
}

# Migrate public convention-tag references in all packaged JSON resources, not
# just server data. Keep serializer/type IDs such as neoforge:difference intact
# by only changing #tag references and values of properties named "tag".
$resourceRoots = @(
  (Join-Path $workspace "src/main/resources"),
  (Join-Path $workspace "src/generated/resources"),
  (Join-Path $mantleWorkspace "src/main/resources"),
  $mantleGeneratedResources
) | Where-Object { Test-Path -LiteralPath $_ }

$tagReferenceFiles = 0
$tagReferenceCount = 0
foreach ($resourceRoot in $resourceRoots) {
  foreach ($file in Get-ChildItem -LiteralPath $resourceRoot -Filter "*.json" -File -Recurse) {
    $fullName = [IO.Path]::GetFullPath($file.FullName)
    $original = [IO.File]::ReadAllText($fullName)
    $hashTagMatches = [regex]::Matches($original, '#(?:forge|neoforge):')
    $tagPropertyMatches = [regex]::Matches($original, '("tag"\s*:\s*")(?:forge|neoforge):')
    if ($hashTagMatches.Count -eq 0 -and $tagPropertyMatches.Count -eq 0) {
      continue
    }

    $migrated = [regex]::Replace($original, '#(?:forge|neoforge):', '#c:')
    $migrated = [regex]::Replace($migrated, '("tag"\s*:\s*")(?:forge|neoforge):', '${1}c:')
    if ($migrated -ne $original) {
      [IO.File]::WriteAllText($fullName, $migrated, [Text.UTF8Encoding]::new($false))
      $tagReferenceFiles++
      $tagReferenceCount += $hashTagMatches.Count + $tagPropertyMatches.Count
    }
  }
}

Write-Output "Migrated advancements: $advancementCount"
Write-Output "Migrated loot tables: $lootCount"
Write-Output "Migrated loot modifiers: $lootModifierCount"
Write-Output "Migrated global loot modifiers: $globalLootModifierCount"
Write-Output "Migrated Mantle tags: $mantleTagCount"
Write-Output "Migrated book NBT ingredients: $bookIngredientCount in $bookIngredientFileCount files"
Write-Output "Migrated public tag references: $tagReferenceCount in $tagReferenceFiles files"
