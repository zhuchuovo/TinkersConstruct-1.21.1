// ============================================================
// 匠魂3 + KubeJS 配方冲突修复脚本
// 适用环境: Minecraft 1.21.1 / NeoForge / Tinkers' Construct 3.12.0-port / KubeJS 7
//
// 冲突根因:
//   1. KubeJS 的 .replaceInput/.replaceOutput 基于原版配方序列化
//      (getIngredients() + toJson/fromJson 往返) 工作。匠魂配方由 Mantle
//      RecordLoadable 自定义序列化, 字段是 inputs / input / cast / tools,
//      不是原版的 key.X 结构; 且 SizedIngredient 带有 count 字段,
//      往返重解析时会丢失或损坏这些字段, 导致 replaceInput 静默失效
//      或配方加载报错。
//   2. KubeJS 的 .remove 只按配方 ID 删除单个文件。一个强化(modifier)
//      实际包含多个配方: 升级配方 + salvage 配方(甚至增量配方), 只删其一,
//      其余配方仍会在加载时向 ModifierRecipeLookup 等静态缓存注入数据,
//      表现为"删除后仍在工作台/JEI 中出现"。
//
// 修复策略:
//   - 移除: 按 ID 正则成对移除升级配方 + salvage 配方
//   - 替换: 不依赖 .replaceInput, 用 JSON 深遍历直接改字段
//     (兼容 KubeJS 7 的 Gson JsonObject/JsonArray, 也兼容普通 JS 对象)
//   - 顺序: 先移除, 再替换, 最后新增
// ============================================================

ServerEvents.recipes(event => {
  const TC = 'tconstruct'

  // ---------- 1. 按 ID 正则移除配方 ----------
  // 注: 匠魂 modifier 配方的 result 是修饰符而非物品, 所以
  //     event.remove({ output: 'tconstruct:haste' }) 永远匹配不到!

  // 移除单个强化(modifier)的全部相关配方: 升级 + salvage (+ 变体)
  // 用法: tconstructRemoveModifier('haste')
  function tconstructRemoveModifier(modifierId) {
    // 升级/能力/防御/无槽位/工作台/兼容等主配方
    event.remove({
      id: new RegExp(`^${TC}:tools/modifiers/(upgrade|ability|defense|slotless|worktable|compat)/${modifierId}(_.+)?$`)
    })
    // 配套 salvage 配方(独立 ID 路径, 必须一并删除, 否则残留缓存)
    event.remove({
      id: new RegExp(`^${TC}:tools/modifiers/salvage/${modifierId}(_.+)?$`)
    })
    // 增量配方与晶球配方通常与升级配方同名或带后缀, 上面两条已覆盖;
    // 若存在 tools/modifiers 根目录下的同名配方(如 tasty.json), 再删一次:
    event.remove({
      id: new RegExp(`^${TC}:tools/modifiers/${modifierId}$`)
    })
  }

  // ---------- 2. JSON 深遍历替换输入 ----------
  // 匠魂配方字段:
  //   tconstruct:modifier             -> "inputs": [{"item"/"tag", "count"?: N}, ...]
  //   tconstruct:incremental_modifier -> "input": {"item"/"tag"} (单数)
  //   tconstruct:modifier_repair      -> "ingredient": {"item"/"tag"}
  //   tconstruct:extract_modifier     -> "inputs": [...]
  //   tconstruct:remove_modifier      -> "inputs": [{"ingredient": [...], ...}]
  //   tconstruct:toggle_interaction   -> "inputs": [...]
  //   tconstruct:casting_table/basin  -> "cast": {...}, "fluid": {...}
  // 本函数递归遍历上述全部结构(含嵌套 ingredient 与 count)。

  // 替换 JSON 中的 item/tag 字符串, 返回是否发生修改
  function replaceJsonIngredient(json, from, to, mode = 'item') {
    let changed = false

    function walk(node) {
      if (node === null || node === undefined) return false
      let localChanged = false

      // Gson JsonObject: 有 entrySet() 方法
      if (typeof node.entrySet === 'function') {
        const keys = []
        node.entrySet().forEach(entry => keys.push(entry.key))
        for (const key of keys) {
          const value = node.get(key)
          if (key === mode && typeof value.getAsString === 'function' && value.getAsString() === from) {
            node.addProperty(key, to)
            localChanged = true
          } else if (value !== null && typeof value === 'object') {
            if (walk(value)) localChanged = true
          }
        }
        return localChanged
      }

      // Gson JsonArray: 有 size()/get(i) 方法
      if (typeof node.size === 'function' && typeof node.get === 'function') {
        for (let i = 0; i < node.size(); i++) {
          if (walk(node.get(i))) localChanged = true
        }
        return localChanged
      }

      // 普通 JS 数组
      if (Array.isArray(node)) {
        for (const child of node) {
          if (walk(child)) localChanged = true
        }
        return localChanged
      }

      // 普通 JS 对象
      if (typeof node === 'object') {
        for (const [key, value] of Object.entries(node)) {
          if (key === mode && typeof value === 'string' && value === from) {
            node[key] = to
            localChanged = true
          } else if (typeof value === 'object' && value !== null) {
            if (walk(value)) localChanged = true
          }
        }
        return localChanged
      }

      return localChanged
    }

    changed = walk(json)
    return changed
  }

  // 对指定类型的匠魂配方批量替换输入物品
  // 用法: tconstructReplaceInput({ type: 'tconstruct:modifier' }, 'minecraft:gold_ingot', 'minecraft:netherite_ingot')
  function tconstructReplaceInput(filter, fromItem, toItem) {
    event.forEachRecipe(filter, recipe => {
      if (recipe.json && replaceJsonIngredient(recipe.json, fromItem, toItem)) {
        console.info(`[TConstructFix] 替换输入 ${fromItem} -> ${toItem}: ${recipe.getId()}`)
      }
    })
  }

  // 对指定类型的匠魂配方批量替换流体标签 (如 c:molten_gold -> c:molten_netherite)
  function tconstructReplaceFluidTag(filter, fromTag, toTag) {
    event.forEachRecipe(filter, recipe => {
      if (recipe.json && replaceJsonIngredient(recipe.json, fromTag, toTag, 'tag')) {
        console.info(`[TConstructFix] 替换流体 ${fromTag} -> ${toTag}: ${recipe.getId()}`)
      }
    })
  }

  // ---------- 3. 使用示例(按需取消注释) ----------

  // 示例 A: 移除某强化及其 salvage 配方
  // tconstructRemoveModifier('haste')

  // 示例 B: 替换某类型配方中的输入物品(不触发 replaceInput 的序列化问题)
  // tconstructReplaceInput({ type: `${TC}:modifier` }, 'minecraft:gold_ingot', 'minecraft:netherite_ingot')
  // tconstructReplaceInput({ type: `${TC}:incremental_modifier` }, 'minecraft:gold_ingot', 'minecraft:netherite_ingot')

  // 示例 C: 替换铸造/熔炼配方中的流体标签
  // tconstructReplaceFluidTag({ type: `${TC}:casting_table` }, 'c:molten_gold', 'c:molten_netherite')

  // ---------- 4. 完全替换一个配方(最稳妥, 不依赖任何 JSON 可变性) ----------
  // 先删旧配方, 再 event.custom 写入完整 JSON(字段必须与上方结构完全一致)
  // event.remove({ id: `${TC}:tools/modifiers/upgrade/smelting` })
  // event.custom({
  //   type: `${TC}:modifier`,
  //   allow_crystal: true,
  //   inputs: [
  //     { item: 'minecraft:soul_campfire' }   // 原为 campfire
  //   ],
  //   level: { max: 4 },
  //   result: `${TC}:smelting`,
  //   slots: { upgrades: 1 },
  //   tools: [
  //     { tag: `${TC}:modifiable/interactable` },
  //     { tag: `${TC}:modifiable/armor/worn` }
  //   ]
  // })
})
