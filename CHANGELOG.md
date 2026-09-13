# 更新日志

本文件记录本移植项目相对上一个版本的改动；完整历史见 git 提交记录。

## 3.12.8

同步上游 [SlimeKnights/TinkersConstruct](https://github.com/SlimeKnights/TinkersConstruct/) 1.20.1 分支最新内容（含 3.12.0 Slimesuit Update 之后的开发提交）。

### 新增材料

- **鹦鹉螺（nautilus）**：壳材料，由鹦鹉螺壳在部件台制作，特性「螺壳肠胃」免疫中毒 I、饥饿 II 与反胃 I（每级纹饰提升可免疫的最高等级）。
- **犄角（horn）**：肋骨笼材料，由山羊角在部件台制作，特性「冲撞攻击」在冲刺时空手攻击每级额外造成 4 点伤害，并在命中时播放对应山羊角号声；8 种山羊角各自对应一个材质变体。
- **奶酪（cheese）**：鞋带材料，由奶酪锭或奶酪块在部件台制作，特性「咸香诱人」。
- **毒液（venom）**：史莱姆套装材料，由蜘蛛眼或发酵蜘蛛眼制作、也可用毒液浇筑，特性「魔法防护」（由末影珍珠移交）。
- **天空黏液皮 / 末影黏液皮（skyslimeskin / enderslimeskin）**：从黏液藤蔓中拆分出的独立材料，分别在皮革上浇筑天空史莱姆与末影史莱姆获得。

### 特性与模块

- 新增强化：`shell_gut`（螺壳肠胃）、`airborn`（凌空护体）、`ram_attack`（冲撞攻击）、`savory`（咸香诱人）、`scrumptious`（美味可口）。
- 重构可食用系统：新增 `edible_effect` 钩子与 `edible_representative_item`、`edible_consume_durability`、`edible_cure_effects`、`edible_cure_random_effect`、`edible_remove_effect` 五个模块；`eat_duration`、`edible_counter_chance` 改为工具属性，生铁「美味」改用新格式。
- 新增 `melee_instrument` 模块与自定义材料原料 `tconstruct:instrument`，用于按山羊角乐器类型匹配部件材料。
- 支持在强化 JSON 中通过 `tconstruct:edible` 标记工具可食用。

### 平衡与同步

- 蜂蜜史莱姆套装补全史莱姆属性与「美味可口」特性。
- 末影珍珠史莱姆特性改为「末影闪避」（25%/级概率传送走攻击者），等级由 2 级提升至 3 级。
- 黏液藤蔓移除装甲鳞片（maille）用途，末影黏液藤蔓护甲特性改为「末影闪避」，与黏液皮拆分保持一致。
- 同步生铁、黏土、末影珍珠的百科文案；新增内容的英文与简体中文语言条目。

### 贴图

- 按上游调色板生成新材料的部件贴图（肋骨笼、壳、鞋带、史莱姆套装、护甲鳞片、皮甲、弓弦、钓线、修理包等），共 216 张。

### 说明

- 青铜、镍铬合金（nicrosil）等**兼容合金**在上游与本节移植版中均为条件加载：需要实例中存在 `c:ingots/tin`、`c:ingots/nickel`、`c:ingots/chromium` 等标签（即安装了提供这些金属的模组）或开启 `forceIntegrationMaterials` 配置，单装匠魂时不会出现，这属于上游既定行为。
