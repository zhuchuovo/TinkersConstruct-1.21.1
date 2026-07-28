# Tinkers' Construct

本项目是 **Tinkers' Construct** 面向 **Minecraft 1.21.1 + NeoForge** 的 `3.11.2` 移植版，基于上游 `3.11.2` 版本开展适配工作。

## 项目信息

- Minecraft：`1.21.1`
- Mod Loader：`NeoForge`
- 移植版本：`3.11.2-port`
- 必需依赖：`Mantle 1.12.0-port`
- 状态：开发与适配中，部分内容可能尚未完成,匠魂百科还暂时没法用0.0

## 项目目录
```
TinkersConstruct-1.21.1/
```
Mantle-1.21.1
https://github.com/zhuchuovo/Mantle-1.21.1
## 构建

环境要求：JDK 21。进入 TinkersConstruct 项目目录执行：

```bash
gradlew build
```

构建后会分别生成两个独立模组文件：

```text
TinkersConstruct-1.21.1/build/libs/TinkersConstruct-*.jar
Mantle-1.21.1/build/libs/Mantle-*.jar
```

## 参考

- 上游项目：https://github.com/SlimeKnights/TinkersConstruct
- 上游文档：https://slimeknights.github.io/docs/