---
name: Bug 报告
about: 报告 BlackBoxPro 的异常行为（构建 / 客户端 Mod / 服务端插件 / run_test 等）
title: "[Bug] "
---

## 环境

| 项 | 值 |
|----|----|
| Minecraft 版本 | |
| 客户端加载器 | Fabric / NeoForge / Forge |
| 服务端 | Paper / Spigot + 版本 |
| JDK | |
| 操作系统 | |
| BlackBoxPro 版本 / commit | |

## 复现步骤

1.
2.

```bash
# 你实际执行的命令（HTTP 请求 / gradle 任务）
```

## `/status` 原始输出

```text
插件  :38080/status →
客户端 :38081/status →
```

## 期望 vs 实际

- 期望：
- 实际：

## 原始输出 / 结果 JSON

<!--
涉及 run_test 请贴 passed/failed/skipped/total 四项，以及失败项的 action + message。
注意：结果必须显式 UTF-8 落盘并用解析器复验，否则容易贴成乱码坏 JSON。
-->

```text

```

## 已排除

- [ ] 客户端窗口保持可见、可交互（未最小化、未被遮挡）
- [ ] 同一时刻只有一个客户端实例（38081 硬编码，单例）
- [ ] `pauseOnLostFocus: false` 仍然生效
- [ ] 结果 JSON 是合法 JSON（用 `node`/解析器能读出来）
