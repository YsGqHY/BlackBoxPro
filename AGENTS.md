# AGENTS.md — 给自动化代理的入口

> 本文件面向**所有** AI 编码代理（Claude Code / Copilot / Cursor / Codex / 自研 agent 等）。
> 目标：让你在**不猜**的前提下驱动 BlackBoxPro 做客户端侧黑盒测试。

## 0.-1 关于命令示例的 shell（很重要）

本文的命令示例分两类，**不要混用**：

- 标了 `powershell` 的 = **Windows PowerShell 5.1 可直接跑**（本机 `pwsh` 实际就是 PS 5.1）。
- 标了 `bash` / 无标记的裸命令（`curl ...`、`grep ...`、`sed ...`）= **Unix 风格**。
  Windows 上**没有** `bash`/`grep`/`sed`/`wc`；而且 **PS 5.1 里 `curl` 是 `Invoke-WebRequest` 的别名**，
  写 `curl -s http://...` 会直接报 `Missing mandatory parameter: Uri`。

**⇒ 在 Windows 上请只用 `powershell` 块，或把裸命令换成下面这些等价物**：

| Unix 写法 | PS 5.1 等价 |
|---|---|
| `curl -s -X POST ...` | `Invoke-RestMethod -Method Post -Uri ... -Body $b -ContentType 'application/json'` |
| `grep -c foo file` | `(Select-String -Path file -Pattern foo).Count` |
| `wc -l file` | `(Get-Content file).Count`（中文文件用 `[IO.File]::ReadAllText($p,[Text.Encoding]::UTF8) -split "\`n"`）|
| `sed 's/a/b/'` | `-replace 'a','b'` |
| `export VAR=x` | `$env:VAR='x'` |

---

## 0. 30 秒判断：这是不是你要的工具？

**是**，如果你要验证的是**真实客户端能看到/能做到什么**：
- GUI / 容器 / 槽位内容 / tooltip / 聊天样式 / 记分板 / bossbar / tablist 是否出现、内容对不对
- 渲染结果（截图）是否正常
- 输入模拟：移动、点击、挖掘、放置、交互、按键、命令补全
- 客户端侧状态查询（NBT、玩家状态、路径移动）

**不是**，如果你要验证的是：
- 服务端插件业务逻辑（经济/权限/数据库）—— 用单元测试或 RCON
- 服务端日志内容 —— 用 RCON
- 多个客户端并发交互 —— 需要 bot（如 headlessmc）
- 真实玩家协议层行为 —— 需要专门协议库

---

## 1. ⚠️ 最重要的一条：不要误读 `run_test` 的结果
```
run_test 的 passed/failed  描述的是  框架自身 action 在当前环境是否可用
                           NOT        你的插件是否通过了测试
```

三条机制性理由：

1. **触发路径不同**：你插件的命令/界面不会被 `run_test` 自动触达。
2. **断言对象不同**：`run_test` 的用例表在 `BlackBoxTestCatalog` 里**硬编码为 ActionCatalog 自测**，不是可扩展的插件测试套件。
3. **语义错位**：它测的是"客户端能不能做到某个动作"，不是"你的业务逻辑对不对"。

**把它当作"我的插件通过了测试"，是假阳性。**

### 正确做法：自己编排 action 序列

```
# 例：验证 /myplugin gui 打开的界面是否正确
POST /execute {"action":"chat_command","params":{"command":"/myplugin gui"}}
POST /execute {"action":"query_screen_state"}      # ← 必须验证，见下方警告
POST /execute {"action":"query_container_slots"}   # 注意：需要确认已 open_inventory
POST /execute {"action":"screenshot","params":{"testId":"myplugin-gui"}}
POST /execute {"action":"close_container"}
```

`run_test` 只当**链路体检**：确认框架与你的环境握手正常。

#### ⚠️ `chat_command` 也会假成功（同一个陷阱换了个动作）

`chat_command` 返回 `{"status":"success","message":"Sent command: ..."}` **只表示"命令被发出去了"**，
**它不知道命令是否执行成功**。源码里 `ChatCommandAction` 只是 `sendChatCommand(...)` 然后立刻返回 ok，
**没有任何服务端回执**。所以：

| 情况 | `chat_command` 返回 |
|---|---|
| 命令不存在 | `success` |
| 你没权限（非 OP） | `success` |
| GUI 其实没打开 | `success` |
| 命令真的生效了 | `success`（**与上面三种无法区分**） |

**⇒ 绝不能用 `chat_command` 的 `success` 作为"GUI 已打开 / 命令已生效"的证据。**
必须用**后续查询动作**来证明结果：

```
chat_command "/myplugin gui"
query_screen_state            → screenClass 必须是你的 GUI 类名，screenType 不是 "none"
query_container_slots         → windowId ≥ 0 且 slots 里有预期物品
```

> 实测佐证（本机 1.21.4 联机）：`chat_command "/give @p minecraft:diamond_sword"` 返回 `success`，
> 但玩家**不是 OP**，服务端日志里**没有任何命令执行记录**，背包仍为空。若只看 `success` 就会得出
> "物品给了、GUI 有东西"的错误结论。

---

## 2. 最小上手路径

### 2.-1 先搞清楚 `<BBP_ROOT>` 是哪个目录

`<BBP_ROOT>` = **含 `settings.gradle.kts` 与 `common/`、`plugin/`、`mod/` 三个子目录的那个根**。
本机可能有多个 worktree（`git worktree list` 会列出全部），**必须用 `plugin/build/libs/` 里刚构建出的 jar 所属的那个根**，否则你拷的服务端插件版本会与客户端 Mod 不匹配。

```powershell
# 确认当前所在的根是否正确（应输出 3 行目录）
Test-Path settings.gradle.kts, common, plugin, mod
```

### 2.0 你的被测插件怎么放（这是文档以前缺的一节）

BlackBoxPro 只提供**观测与驱动能力**，它**不知道**你的插件。要让"验证我的插件"成立，必须：

1. **构建你的插件** → 得到 `YourPlugin.jar`；
2. 把 **`YourPlugin.jar` 和 `BlackBoxPro-Plugin-*.jar` 一起**放进**同一个**测试服的 `plugins/` 目录；
3. **重启测试服**（插件不做热重载）；
4. 让客户端连**这个**测试服（而不是单机世界），否则你的插件命令根本不存在。

```powershell
# 一个测试服里同时放两个插件
Copy-Item .\build\libs\YourPlugin.jar                        <SERVER_DIR>\plugins\
Copy-Item <BBP_ROOT>\plugin\build\libs\BlackBoxPro-Plugin-*.jar <SERVER_DIR>\plugins\
Remove-Item <SERVER_DIR>\plugins\.paper-remapped -Recurse -Force -ErrorAction SilentlyContinue
# 重启服务端后，插件命令才会注册
```

**验收判据（字段级，别只看 success）**：

| 你要验证的 | 唯一可信的证据 |
|---|---|
| GUI 打开了 | `query_screen_state` → `screenClass` / `screenType` / `title` 符合预期 |
| GUI 里有东西 | `query_container_slots` → `slots` 对象的键数与非空项符合预期 |
| tooltip 正确 | `screenshot_tooltip` → `tooltipPixelProbe`、`tooltipDiffHit` 等判据字段 |
| 聊天样式正确 | `query_chat_style` → 返回的样式字段 |
| 插件在服务端生效了 | **服务端日志**（`logs/latest.log`）或 RCON —— BlackBoxPro 看不到服务端业务逻辑 |

### 2.1 常见动作的**真实**必填参数（照抄，别猜）

下表由源码逐个核对（`params.requireXxx("...")`）。**参数名写错会得到 `Invalid params: Missing required field: xxx`**：

| action | 必填参数 | 可选 | 备注 |
|---|---|---|---|
| `connect_to_server` | **`ip`**（如 `127.0.0.1:25565`）| — | 注意是 `ip`，**不是** `address`/`host` |
| `create_world` | `worldName` | — | 已在世界时会失败：`Already in a world` |
| `join_world` | `worldName` | — | 复用已有世界，比 `create_world` 稳 |
| `open_inventory` | — | — | |
| `query_container_slots` | — | — | 返回 `windowId` + `slots`（**对象**，键=槽位号） |
| `screenshot_tooltip` | **`windowId`** + **`slot`** | `testId`、`prefix` | 槽位为空会失败：`Slot N is empty` |
| `screenshot` | — | `testId`、`prefix`、`playerName` | **`name` 会被静默忽略** |
| `hover_slot` | 见 `action-catalog.md` | | |
| `query_chat_style` | `match` | | 缺了报 `Missing required field: match` |
| `run_test` | `player` | `scope`（`smoke`\|`full`） | `player` 必须**在线玩家名** |

### 2.2 拓扑

```
你的 Agent ──HTTP 38080──> Paper 测试服务端（BlackBoxPro 插件 + 你的被测插件）
                                   ▲
                                   │ 游戏协议
                                   ▼
                           Fabric 客户端（BlackBoxPro Mod，HTTP 38081，硬编码）
```

### 2.3 四条硬约束（违反会得到**误导性结果**）

| 约束 | 违反后果 |
|---|---|
| **窗口不可最小化** | "移动光标"类 action 会在数毫秒内失败，且失败文案不可读（`Cannot coerce value to an empty range: maximum -1.0 is less than minimum 0.0`） |
| **`pauseOnLostFocus: false`** | 默认 `true`，失焦会弹暂停菜单 ⇒ 玩家物理冻结 ⇒ `jump`/`click_slot`/`close_container` 连锁失败 |
| **同一时刻只能一个客户端** | 38081 硬编码且 `RuntimeBlackBoxConfig.update()` 在 `dev-2.0` 零调用方 ⇒ 两个客户端互相顶掉 |
| **收口 HTTP 到回环** | 7 处 `HttpServer.create(InetSocketAddress(port),0)` **默认绑 `0.0.0.0`**、**零鉴权** ⇒ 对局域网敞开 |

### 2.4 调用约定

- 端点：`POST /status`、`POST /execute`。**`/actions` 不存在**（常量未注册，访问得 404）。
- `run_test` **必传 `params.player`**（在线玩家名，先用客户端 `query_tab_list` 取）；`scope` 只有 `smoke` / `full`。
- **结果 JSON 必须用 node 解析**：PowerShell 5.1 的 `Get-Content` 默认 GBK、`ConvertFrom-Json` 行为与 node 不同。
- **写 JSON 不要用 `>` 重定向**（PS 5.1 会写 UTF-16LE）：
  ```powershell
  [System.IO.File]::WriteAllText($p, $s, (New-Object System.Text.UTF8Encoding($false)))
  ```
- **请求串行化**：HTTP server 单线程处理，并发会排队甚至超时。
- **`actions` 字段 = 运行时注册表大小**（唯一不写死的计数来源）；文档里的数字**都不要信**。

---

## 3. 环境未就绪 ≠ 被测程序失败

把下列情况归为**环境问题**，不要记成缺陷：

| 现象 | 归属 | 处置 |
|---|---|---|
| 端口连不上 | 环境 | 等就绪 / 检查进程 |
| `ready: false` | 环境 | 客户端还在加载 |
| `Player not online` | 环境 | 先 `join_world` / `connect_to_server` |
| `Missing param: player` | 调用错误 | 补 `params.player` |
| 窗口最小化相关报错 | 环境 | 恢复窗口 |
| `create_world` 超时（10s） | 环境 | 改用 `join_world {worldName}` 复用已有世界 |
| 首次启动卡在下载 assets | 环境 | 等，或预置 `run/downloads` |
| **`AccessibilityOnboardingScreen`** | **环境（首次运行）** | 首次启动会停在"Welcome to Minecraft!"无障碍引导页 ⇒ `create_world` 必超时。先 `close_screen` 关掉它 |
| **`leave_world` 超时但界面已在 `TitleScreen`** | **已知缺陷** | 客户端与玩家世界状态可能不同步；此时 `connect_to_server` 会报 `Already in a world`。先 `close_container`，再重试 |
| **`chat_command` 返回 success 但没生效** | **假阳性** | 见 §1 的警告 —— 必须用后续查询验证 |

### 3.1 取 `run_test` 的 `player` 参数：不要用 `players[0]`

`query_tab_list` 返回的 `players` **按 latency 升序排序**，`players[0]` 通常是**别人**（或延迟最低的某个玩家），
拿它去喂 `run_test` 会得到 `Player not online`，而这条失败**很容易被误判成"环境问题"**。

```javascript
// ✅ 正确：按名字精确匹配
const tab = await call(MOD_PORT, 'query_tab_list');
const me = process.env.BBP_PLAYER;
const hit = (tab?.data?.players ?? []).find(p => p.name === me);
if (!hit) { console.error('玩家不在线：' + me); process.exit(2); }
await call(PLUGIN_PORT, 'run_test', { player: hit.name, scope: 'smoke' });
```

---

## 4. 安全（不可协商）

这是**本机开发工具**，不是生产服务：

- HTTP **明文 + 零鉴权**；`chat_command` 能发**任意服务端命令**（等级继承该账号）、`stop_server` 一行停服、`query_chat_history` 读**全部聊天**、`screenshot` 把画面**落盘**。
- **只 bind 回环**，并**从另一台机器验证确实超时**（只验本机通不算）。
- **只在隔离测试服上用**；不要接主力世界/生产服。
- 不要暴露到公网（端口转发 / frp / 云安全组）。

---

## 5. 详细文档在哪

| 需要什么 | 读这个 |
|---|---|
| 完整操作流程（技能主文档） | `.claude/skills/blackboxpro-test/SKILL.md` |
| 全部 action 与参数 | `.claude/skills/blackboxpro-test/reference/action-catalog.md` |
| HTTP 端点、字段、错误语义 | `.claude/skills/blackboxpro-test/reference/http-api.md` |
| 新增一个 action | `.claude/skills/blackboxpro-test/reference/register-action.md` |
| 接入新 MC 版本 | `.claude/skills/add-mc-version/skill.md` |

**不要预加载全部文档**：先读本文件，再按需读取上面某一份。

---

## 6. 本仓库的约定

- 默认分支 `dev-2.0`；`mod/1.21.4` **只存在于 `feat/1.21.4-fabric` 分支**（PR #3）。
- 文档漂移由 CI 拦：`.github/workflows/validate-skills.yml` 跑 `.claude/skills/validate_blackboxpro_skills.py`。
  - 它**现场解析源码**得出 action 计数，因此**文档里不要写死任何 action 数字** —— 写了就会红。
  - 本地手动跑：`python .claude/skills/validate_blackboxpro_skills.py`（退出码 0 = 通过）。
