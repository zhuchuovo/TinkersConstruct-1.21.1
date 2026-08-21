# Tinkers' Construct 1.21.1

这是一个面向 **Minecraft 1.21.1** 与 **NeoForge** 的 Tinkers' Construct（匠魂）移植项目。模组围绕“部件—材料—改造”体系扩展工具、武器和护甲的制作与成长，让玩家可以按用途组合部件、选择材料特性，并通过强化改造持续调整装备能力。

## 主要内容

- 使用工具装配台制作可自定义的工具、武器与护甲。
- 为部件选择不同材料，获得对应的耐久、挖掘、攻击与特殊属性。
- 通过强化槽和能力槽为装备添加改造效果，并可维修、替换部件或进一步升级。
- 提供熔炉、多方块冶炼炉、铸件与合金等冶炼流程，用于材料加工与装备制造。

## 项目信息

- Mod ID：`tconstruct`
- 游戏版本：`Minecraft 1.21.1`
- 模组加载器：`NeoForge`
- 当前模组版本：`3.12.6`
- Java 版本：`JDK 21`
- 必需依赖：`Mantle 1.12.5` 或兼容版本
- 可选依赖：`JEI`

> 项目仍在移植与适配阶段，部分功能、兼容性或游戏内文档可能尚未完全实现。

## 安装

1. 安装与 Minecraft `1.21.1` 匹配的 NeoForge。
2. 将本模组和 Mantle 放入游戏实例的 `mods` 文件夹。
3. 如需配方查询功能，可额外安装 JEI。

## 开发构建

请先安装 JDK 21，然后在项目根目录执行：

```powershell
.\gradlew.bat build
```

构建产物位于 `build/libs/`。本项目构建 Mantle 源码依赖时，也会同时生成 Mantle 的对应产物。

## 上游

本项目上游为：[https://github.com/SlimeKnights/TinkersConstruct/](https://github.com/SlimeKnights/TinkersConstruct/)

## 许可证

本项目遵循仓库内 [LICENSE](LICENSE) 文件所列的 MIT 许可证。
