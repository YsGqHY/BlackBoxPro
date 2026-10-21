# BlackBoxPro

[![Build & Release](https://github.com/YsGqHY/BlackBoxPro/actions/workflows/release.yml/badge.svg)](https://github.com/YsGqHY/BlackBoxPro/actions/workflows/release.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

Minecraft 自动化黑盒测试框架。服务端插件通过 Plugin Message Channel 向客户端 Mod 下发 JSON 指令，Mod 在客户端模拟真实玩家行为（移动、交互、GUI 操作、截图、查询等），并回传结果，用于对服务端插件逻辑进行自动化功能测试。

## 特性

- 112 个 Action（1.21.x），105 个 Action（1.12.2），覆盖 Minecraft 全部 Serverbound 协议包 + 查询 + 复合行为
- 六端同步支持：Fabric 1.21.11 / NeoForge 1.21.11 / Fabric 1.21.4 / Fabric 1.21.1 / NeoForge 1.21.1 / Forge 1.12.2
- 双通道通讯：Plugin Message Channel（服务端→客户端）+ HTTP API（外部工具直连 Mod）
- 物理引擎驱动的移动系统（InjectedInput），支持碰撞检测与 A* 寻路
- 截图系统：普通截图 + Tooltip 渲染截图（`screenshot_tooltip`），支持帧缓冲捕获
- 完整的查询系统：玩家状态、方块、世界、容器、记分板、Boss Bar、聊天历史等 17 种查询
- GitHub Actions CI/CD 自动构建与发布

## 架构

```text
┌─────────────────────┐     blackbox:command      ┌──────────────────────┐
│   Bukkit Server     │ ────────────────────────▶ │  Fabric / NeoForge   │
│   (plugin 模块)     │                           │  / Forge 客户端 Mod  │
│                     │ ◀──────────────────────── │                      │
│                     │     blackbox:response     │                      │
└─────────────────────┘                           └──────────────────────┘

外部工具 ──── HTTP POST ────▶ Mod (:38081) 或 Plugin (:38080)
```

## 支持的 Minecraft 版本

| Minecraft | Mod Loader | JVM |
|-----------|------------|-----|
| 1.21.11   | Fabric / NeoForge | 21 |
| 1.21.4    | Fabric | 21 |
| 1.21.1    | Fabric / NeoForge | 21 |
| 1.12.2    | Forge | 8 |

## 模块

| 模块 | 角色 | 框架 |
|------|------|------|
| `common` | 无 MC 依赖的共享协议层 | Kotlin + Gson |
| `mod:1.21.11:runtime` | 1.21.11 公共运行时核心 | NeoForm + Kotlin |
| `mod:1.21.11:fabric` | Fabric 1.21.11 平台实现 | Fabric API + fabric-language-kotlin |
| `mod:1.21.11:neoforge` | NeoForge 1.21.11 平台实现 | NeoForge 21.11.x + KotlinForForge |
| `mod:1.21.4:runtime` | 1.21.4 共享运行时核心（无 MC 依赖） | Kotlin + SLF4J |
| `mod:1.21.4:fabric` | Fabric 1.21.4 平台实现 | Fabric API + fabric-language-kotlin |
| `mod:1.21.1:runtime` | 1.21.1 公共运行时核心 | NeoForm + Kotlin |
| `mod:1.21.1:fabric` | Fabric 1.21.1 平台实现 | Fabric API + fabric-language-kotlin |
| `mod:1.21.1:neoforge` | NeoForge 1.21.1 平台实现 | NeoForge 21.1.x + KotlinForForge |
| `mod/1.12.2` | Forge 1.12.2 客户端 Mod（独立构建根） | Forge 1.12.2 + Kotlin 1.9.25 |
| `plugin` | 服务端插件 | Paper/Spigot + TabooLib 6.2 |

## 仓库结构

```text
BlackBoxPro/
├── common/                    # 共享协议层
├── plugin/                    # 服务端插件（TabooLib）
├── mod/                       # 客户端多版本工程
│   ├── 1.21.11/
│   │   ├── runtime/           # 公共运行时核心（桥接 + NeoForge MC 实现）
│   │   ├── fabric/            # Fabric wrapper + Mixin
│   │   └── neoforge/          # NeoForge wrapper
│   ├── 1.21.4/
│   │   ├── runtime/
│   │   └── fabric/
│   ├── 1.21.1/
│   │   ├── runtime/
│   │   ├── fabric/
│   │   └── neoforge/
│   └── 1.12.2/
│       ├── runtime/
│       └── forge/
├── docs/
│   ├── design/                # 需求 / 开发设计文档
│   ├── testing/               # 测试计划 / 用例说明
│   └── reports/               # 测试报告 / 汇总
├── .github/workflows/         # CI/CD（Build & Release）
├── build.gradle.kts           # 根聚合入口
└── settings.gradle.kts
```

## 支持的行为

1.21.x 端共 112 个 Action，1.12.2 端共 105 个（为最大兼容子集）。

| 分类 | 示例 | 1.21.x | 1.12.2 |
|------|------|:------:|:------:|
| 移动与位置 | `player_move`, `player_look`, `navigate_to` | 8 | 8 |
| 方块交互 | `dig_start`, `place_block`, `use_item` | 5 | 5 |
| 实体交互 | `attack_entity`, `interact_entity`, `left_click` | 5 | 5 |
| 容器 / GUI | `click_slot`, `hover_slot`, `close_container` | 11 | 7 |
| 玩家状态 | `sneak_start`, `drop_item`, `jump`, `elytra_start` | 15 | 16 |
| 聊天命令 | `chat_message`, `chat_command`, `click_chat_text` | 3 | 3 |
| 客户端设置 | `screenshot`, `screenshot_tooltip`, `connect_to_server` | 10 | 11 |
| 进阶交互 | `edit_book`, `update_sign`, `select_trade` | 17 | 14 |
| 调试 | `keep_alive`, `pong`, `custom_payload` | 6 | 3 |
| 复合行为 | `pathfind_to`, `break_block`, `batch`, `craft_recipe` | 15 | 16 |
| 查询 | `query_player_state`, `query_container_slots`, `query_tooltip_state` | 17 | 17 |

## 通讯协议

### Plugin Message Channel

消息格式：JSON over Plugin Message Channel（VarInt length + UTF-8 bytes）。

指令（Server → Client）：
```json
{
  "id": "uuid",
  "action": "screenshot",
  "params": { "testId": "shop_gui_test", "prefix": "after_warp" },
  "delay": 0
}
```

响应（Client → Server）：
```json
{
  "id": "uuid",
  "status": "success",
  "message": "Screenshot saved: 001_after_warp.png",
  "data": { "filePath": "screenshots/blackboxpro/Steve/shop_gui_test/001_after_warp.png" }
}
```

### HTTP API

Mod 内置 HTTP 服务器（端口 38081），Plugin 内置 HTTP 服务器（端口 38080），支持外部工具直接调用。

```bash
# 查询玩家状态
curl -s -X POST http://localhost:38081/execute \
  -H "Content-Type: application/json" \
  -d '{"id":"q1","action":"query_player_state","params":{}}'

# 截图（带 tooltip 渲染）
curl -s -X POST http://localhost:38081/execute \
  -H "Content-Type: application/json" \
  -d '{"id":"st1","action":"screenshot_tooltip","params":{"slot":36,"windowId":0}}'

# 服务状态检查
curl -sf http://localhost:38081/status
```

## 构建

在仓库根目录执行：

```bash
# 全量构建并收集产物到根 build/libs
./gradlew buildAll

# 仅构建 1.21.x mod（runtime + fabric + neoforge）
./gradlew mod_buildAll

# 仅构建服务端插件
./gradlew plugin_build

# 仅构建 Forge 1.12.2
./gradlew forge1122_build
```

### 产物路径

| 产物 | 路径 |
|------|------|
| Fabric 1.21.11 | `mod/1.21.11/fabric/build/libs/BlackBoxPro-fabric-1.21.11-*.jar` |
| NeoForge 1.21.11 | `mod/1.21.11/neoforge/build/libs/BlackBoxPro-neoforge-1.21.11-*.jar` |
| Fabric 1.21.4 | `mod/1.21.4/fabric/build/libs/BlackBoxPro-fabric-1.21.4-*.jar` |
| Fabric 1.21.1 | `mod/1.21.1/fabric/build/libs/BlackBoxPro-fabric-1.21.1-*.jar` |
| NeoForge 1.21.1 | `mod/1.21.1/neoforge/build/libs/BlackBoxPro-neoforge-1.21.1-*.jar` |
| Forge 1.12.2 | `mod/1.12.2/build/libs/BlackBoxPro-forge-1.12.2-*.jar` |
| 服务端插件 | `plugin/build/libs/BlackBoxPro-Plugin-*.jar` |
| 聚合收集 | `build/libs/` |

## 技术栈

- Kotlin（JVM 21 / JVM 8）
- Gradle Kotlin DSL，多模块聚合构建
- Fabric API / NeoForge / Forge 1.12.2
- TabooLib 6.2（服务端插件）
- Gson
- Mixin（1.21.11 Fabric 端 tooltip 渲染注入）

## 许可证

[MIT](LICENSE)
