# BlackBoxPro

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
![Language: Kotlin](https://img.shields.io/badge/Language-Kotlin-7F52FF.svg)
![Minecraft: 1.12.2 | 1.21.1 | 1.21.11](https://img.shields.io/badge/Minecraft-1.12.2%20%7C%201.21.1%20%7C%201.21.11-3C8527.svg)
![Mod Loaders: Fabric | NeoForge | Forge](https://img.shields.io/badge/Loaders-Fabric%20%7C%20NeoForge%20%7C%20Forge-1976D2.svg)

**一句话定位**：BlackBoxPro 是一个**客户端侧**的 Minecraft 自动化黑盒测试框架 —— 外部程序（人或 AI Agent）用 HTTP 下发 JSON 指令，客户端 Mod 在**真实运行的客户端里**模拟玩家行为并回传结果，用来回答"客户端能看到什么、能操作什么"。

两条链路：

```text
外部程序 ──HTTP POST──▶ 服务端插件 (:38080) ──Plugin Message / HTTP──▶ 客户端 Mod
     └──────────HTTP POST────────────────────────────────────────────▶ 客户端 Mod (:38081)

客户端 Mod 在真实客户端内执行：模拟输入 → 操作 GUI/容器 → 截图 → 查询状态 → 回传 JSON
```

---

## English Abstract

BlackBoxPro is a client-side black-box test framework for Minecraft. A server-side plugin, or any external tool, sends JSON commands over HTTP (plugin: `:38080`, client mod: `:38081`) or over a plugin-message channel; the client mod replays them inside a real Fabric / NeoForge / Forge client — simulating input, opening and clicking GUIs and containers, taking screenshots, and querying client-side state — then returns structured JSON results.

It is built for **verifying client-observable behaviour** (what a real client sees and can do), including GUI/tooltip/screenshot assertions, and for giving AI agents a machine-drivable harness for Minecraft client regression work. It is **not** a server-plugin functional test suite and **not** a headless bot: `run_test`'s `passed/failed/skipped` describes the availability of the framework's own actions in the current environment, not whether your plugin "passed its tests" (see the sections below). Supported targets: Minecraft 1.21.11 / 1.21.1 (Fabric + NeoForge), 1.12.2 (Forge), and 1.21.4 (Fabric) on the `feat/1.21.4-fabric` branch.

---

## 它能做什么 / 不能做什么

### 能：客户端侧事实（action 数量随树而变，**不要记固定值**）

> `ActionCatalog` 在 `dev-2.0` 上是 **115** 项；PR #6 把 `screenshot_tooltip` 纳入后是 **116**。
> 各端客户端注册表另加客户端专有 action：1.21.11=**116**、1.21.1=**114**、1.12.2=**105**。
> **想拿运行时真值就读 `POST /status` 的 `actions` 字段**（= 注册表大小），不要信本文的数字。

| 分类 | 示例 |
|------|------|
| 移动与位置 | `player_move`、`player_move_look`、`player_look`、`player_on_ground`、`navigate_to`、`pathfind_to` |
| 方块交互 | `dig_start` / `dig_cancel` / `dig_finish`、`place_block`、`use_item`、`break_block`、`look_at_block` |
| 实体交互 | `attack_entity`、`interact_entity`、`interact_entity_at`、`swing_arm`、`left_click` |
| 容器 / GUI | `click_slot`、`hover_slot`、`click_button`、`close_container`、`set_carried_item`、`open_inventory` |
| 玩家状态 | `sneak_start` / `sneak_stop`、`sprint_start` / `sprint_stop`、`jump`、`drop_item`、`elytra_start`、`swap_hands` |
| 聊天 / 命令 | `chat_message`、`chat_command`、`click_chat_text` |
| 客户端设置与会话 | `screenshot`、`screenshot_tooltip`、`connect_to_server`、`create_world`、`join_world`、`leave_world` |
| 进阶交互 | `edit_book`、`sign_book`、`update_sign`、`select_recipe`、`select_trade`、`rename_item`、`advancement_tab` |
| 调试 | `custom_payload`、`tab_complete`、`keep_alive`、`pong`、`chunk_batch_received` |
| 查询 | `query_player_state`、`query_container_slots`、`query_screen_state`、`query_tab_list`、`query_scoreboard`、`query_boss_bar`、`query_chat_history`、`query_tooltip_state`、`query_slot_tooltip` 等 |
| 复合行为 | `batch`、`wait`、`attack`、`use`、`open_container`、`container_transfer`、`drop_inventory`、`craft_recipe` |

复算（`dev-2.0`）：

```bash
grep -o 'register("[^"]*"' common/src/main/kotlin/com/blackboxpro/common/action/ActionCatalog.kt \
  | sed 's/register("//;s/"//' | sort -u | wc -l    # → 115
```

真正有杀伤力的是**视觉与状态断言**：

- `screenshot` —— 全帧落盘，可用像素级比对做视觉回归；
- `screenshot_tooltip` —— 在容器界面里悬停指定槽位、渲染 tooltip 后截图（可用于 tooltip 背景/边框像素断言）；
- `query_*` —— 直接读客户端内存里的状态（背包槽位、屏幕状态、tab 列表、记分板、bossbar、聊天历史、聊天样式、tooltip 状态）。

### 不能：明确不覆盖

| 不覆盖 | 说明 |
|--------|------|
| 服务端插件业务逻辑 | 经济、权限、数据库、自定义命令的**实现**不在框架视野内。框架只能以玩家身份发出 `/命令`，不能替你断言服务端内部状态 |
| 服务端日志内容 | 框架不解析服务端日志；需要日志断言请自行接 RCON 或读日志文件 |
| 多客户端交互 | 客户端 Mod 的 HTTP 端口是**硬编码单例**（38081），同一时刻只能有一个客户端实例 |
| 真实玩家协议层 | 它驱动的是真实客户端，不是自研协议 bot；压测/大规模并发不在设计目标内 |
| 可扩展的插件测试套件 | `run_test` 的用例表**硬编码**为"框架自身 action 的自测"，不是给插件作者写用例用的注册机制 |

### ⚠️ `run_test` 的 `passed / failed / skipped` 到底在说什么（最关键的一条）

> **它说的是"框架的 action 在本环境是否可用"，不是"你的插件通过测试"。**

- **`skipped` = 夹具缺口，不是失败。** 跳过项来自 `BlackBoxTestCatalog` 里硬编码的前置条件（需要马匹、床、旁观者模式、命令方块、村民交易、配方书、死亡状态……），与客户端能力无关。1.21.11 上恒为 **57** 项。
- **`passed` 大多是"自报成功"。** 115 项里实际执行 58 项，其中只有 **15 项**（`screenshot` + 14 个 `query_*`）在 `BlackBoxTestCatalog.verify()` 里做了超出"响应 `status=success`"的断言 —— 而且这 15 项也只断言"`data` 非空"。其余 43 项只要 action 返回 `success` 就算通过。
- **`failed` 常常是用例自己的问题**（见下文"验证状态"里的真实例子）。

⇒ **只装 BlackBoxPro**，`run_test scope=full` 只能当**链路体检 / 框架自测**。
⇒ **要验证你自己的插件**，必须在同一个测试服上**同时装被测插件 + BlackBoxPro 插件**，然后由外部程序编排一串 `/execute`，例如：

```text
chat_command "/yourgui open"                     # 用你的命令打开 GUI
  → query_container_state / query_container_slots # 断言界面与槽位
  → hover_slot + screenshot_tooltip               # 视觉断言
  → query_chat_style / query_tab_list             # 组件与样式断言
  → close_container
```

---

## 5 分钟上手

前置：JDK 21（1.12.2 端构建另需 JDK 8）、Gradle wrapper 已就位、一个**隔离的**测试服。

### 0. 构建

```bash
./gradlew buildAll          # 全量构建并收集产物
# 产物：plugin/build/libs/BlackBoxPro-Plugin-<version>.jar、各 mod jar，聚合在 build/libs/
```

### 1. 服务端：装插件

1. 把 `BlackBoxPro-Plugin-<version>.jar` 放进测试服的 `plugins/`；
2. 检查 `plugins/BlackBoxPro/config.yml`：

   ```yaml
   http-port: 38080
   test-mode: dual                                            # 插件把指令转发给 Mod
   mod-http-address: "http://localhost:38081"
   response-timeout-ms: 10000
   ```

3. 启动服务端。**Windows 控制台建议加** `-Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8`（日志与结果里有中文）；
4. 让一个玩家先**进服并保持在线** —— `run_test` 需要一个在线玩家名。

### 2. 客户端：起对应版本的 `runClient`

在 `mod/` 下用 Gradle 起开发客户端（例）：

```bash
cd mod
./gradlew :1.21.11:fabric:runClient      # 1.21.11 Fabric；同理 :1.21.11:neoforge / :1.21.1:fabric / :1.21.1:neoforge
```

1.12.2 是独立构建根：`cd mod/1.12.2 && ./gradlew forge:runClient`。
1.21.4 见"版本 × 加载器矩阵"（需要 `feat/1.21.4-fabric` 分支）。

客户端连上测试服，**保持窗口可见、可交互**（最小化或全屏遮挡会让截图变黑、光标类动作失败）。

> 注意：`runClient` 是**开发运行**，客户端 mod 来自 Gradle sourceSet，**不是**把 jar 丢进 `mods/`。

### 3. 就绪检查

```bash
curl -s http://127.0.0.1:38080/status     # 插件
curl -s http://127.0.0.1:38081/status     # 客户端 Mod；"ready": true 表示已进世界
```

### 4. 单个 action

```bash
curl -s -X POST http://127.0.0.1:38080/execute \
  -H "Content-Type: application/json" \
  -d '{"id":"q1","action":"query_player_state","params":{}}'
```

### 5. 跑全量自测（`run_test`，约 1 分钟）

```bash
curl -s -X POST http://127.0.0.1:38080/execute \
  -H "Content-Type: application/json" \
  -d '{"id":"full","action":"run_test","params":{"player":"<playerName>","scope":"full"}}' \
  -o full.json
```

```powershell
# PowerShell 等价写法（注意必须显式 UTF-8 **无 BOM** 落盘）
$body = @{ id='full'; action='run_test'; params=@{ player='<playerName>'; scope='full' } } | ConvertTo-Json -Depth 5
$json = (Invoke-WebRequest -Uri http://127.0.0.1:38080/execute -Method POST `
  -ContentType 'application/json' -Body $body -UseBasicParsing).Content
# ⚠️ 必须用 UTF8Encoding($false)。写成 [System.Text.Encoding]::UTF8 会带 BOM(EF BB BF)，
#    而 node 的 JSON.parse 见 BOM 直接抛 `Unexpected token '﻿'`。
[System.IO.File]::WriteAllText("$PWD\full.json", $json, (New-Object System.Text.UTF8Encoding($false)))
```

**结果必须用 JSON 解析器读**（落盘编码不对会得到坏 JSON）：

```bash
node -e "const d=JSON.parse(require('fs').readFileSync('full.json','utf8')).data;
console.log('total',d.total,'passed',d.passed,'failed',d.failed,'skipped',d.skipped);
d.results.filter(r=>r.status!=='passed'&&r.status!=='skipped').forEach(r=>console.log('FAIL',r.action,'::',r.message));"
```

---

## 支持的 Minecraft 版本与加载器矩阵

| Minecraft | 客户端加载器 | 服务端插件 | JVM | 客户端注册 action 数 | 在 `dev-2.0` 上 |
|-----------|--------------|-----------|-----|:---:|:---:|
| 1.21.11 | Fabric、NeoForge（+ runtime 公共核心） | 需要（Paper/Spigot） | 21 | 116 | ✅ |
| 1.21.1 | Fabric、NeoForge（+ runtime） | 需要 | 21 | 114 | ✅ |
| 1.12.2 | Forge（+ runtime，独立构建根） | 需要 | 8 | 105 | ✅ |
| 1.21.4 | Fabric（+ runtime），**无 NeoForge** | 需要 | 21 | 116（该分支上） | ❌ **不在 `dev-2.0`**：只在 PR #3（`feat/1.21.4-fabric`） |

关于 1.21.4 的两点重要提醒：

1. `dev-2.0` 上**不存在** `mod/1.21.4/` 目录（`git ls-tree -r dev-2.0 -- mod/1.21.4` 为空），需要用 PR #3 的分支才有；
2. 在 `dev-2.0` 上，插件的 `BlackBoxLoaderProfile.detect()` 只有"1.12 → 1.12.2，其余 → 1.21.11"两个分支，所以**一个 1.21.4 客户端会被按 1.21.11 的用例口径判定**。PR #3 才补上 `MC_1214`。

**客户端注册数 ≠ `run_test` 用例数。** `run_test` 的目录来自 `ActionCatalog`（当前 115 项），而各端 `ActionRegistry` 另注册了客户端专有 action。已核实的集合差：

| 版本 | 注册数 | 在 `ActionCatalog` 之外 | `ActionCatalog` 有但该端未注册 |
|------|:---:|------|------|
| 1.21.11 | 116 | `screenshot_tooltip` | —（空） |
| 1.21.1 | 114 | `screenshot_tooltip` | `left_click`、`query_tooltip_state` |
| 1.12.2 | 105 | `screenshot_tooltip` | 恰好是 `unsupportedOn1122` 的 11 项 |

---

## 端点速查

| 端点 | 插件 `:38080` | 客户端 Mod `:38081` | 说明 |
|------|:---:|:---:|------|
| `POST /execute` | ✅ | ✅ | 执行 action |
| `GET`/`POST /status` | ✅ | ✅ | 服务状态（handler 不校验方法） |
| `/actions` | ❌ | ❌ | **不存在**。`HttpEndpoints.ACTIONS` 只是未被注册的常量，两个 HTTP server 都只 `createContext` 了 `/execute` 与 `/status`。要 action 目录请读 `ActionCatalog.kt` |

### `POST /execute`

```json
// 请求
{ "id": "unique-id", "action": "action_id", "params": { } }

// 响应
{ "id": "unique-id", "status": "success", "message": "...", "data": { } }
```

- `status` 只有 `"success"` / `"failure"`；
- 插件在 `test-mode: dual` 下把**非 `run_test`** 的 action 转发到 `mod-http-address`（默认 `http://localhost:38081`），默认读超时 `response-timeout-ms + 2000` ms；
- `test-mode: server_only` 下插件直接返回 `failure: server_only mode: no client actions available`。

### `run_test` 专有规则

| 参数 | 说明 |
|------|------|
| `params.player` | **必填**，在线玩家名。缺失 → 立刻 `failure: Missing param: player`；玩家不在线 → `failure: Player not online: <name>` |
| `params.scope` | 只有 **`smoke`** / **`full`** 两个有效值（缺省 `full`；**任何其它字符串都会落到 `full`**，没有第三种 scope）。`smoke` = 22 个代表用例 |

- `run_test` **不转发给 Mod**，由插件本地在服务端侧驱动，最长 30 分钟超时；
- 响应的 `data` 结构：`{ passed, failed, skipped, total, totalMs, results[{ action, status, message, data? }] }`；
- 另有本地执行的 `stop_server`（直接向控制台发 `stop`，见"安全"）。

### `/status` 字段

插件（`:38080`）：`{ version, mode, httpPort, modHttpAddress, ready }` —— 这里的 `ready` **恒为 `true`**，只说明插件自己的 HTTP 起来了，**不代表客户端在位**。

客户端 Mod（`:38081`）：`{ status, version, platform, httpPort, actions, ready }` —— 这里的 `ready = 玩家已进世界 && 世界已加载`，**这才是"客户端可以驱动"的判据**。

---

## 已知限制与陷阱

1. **窗口必须保持可见、可交互。** 最小化或全屏遮挡会导致截图全黑、光标/槽位类动作失败。`run/options.txt` 里应保持 `pauseOnLostFocus:false`（**删掉 `run/` 目录会被重置**）。
2. **同时只能有一个客户端实例。** 客户端 Mod 端口来自 `RuntimeBlackBoxConfig.NetworkConfig.httpPort` 默认值 `38081`，全仓没有任何 `RuntimeBlackBoxConfig.update(...)` 调用者 ⇒ **不可配置**。第二个客户端只会 bind 失败。
3. **窗口焦点与输入模拟**依赖客户端的输入管线（`InjectedInput` / GLFW 光标位置），因此**不要用无头/离屏方式跑**。
4. **结果 JSON 必须用解析器读。** 结果里含中文与长字段；用错编码落盘会得到坏 JSON（本项目已经出现过 GBK 落盘的坏 JSON 先例）。落盘时显式 UTF-8，并让 `node`/JSON 解析器复验。
5. **`create_world` 容易踩两个坑：** (a) 经插件转发时默认 10 s 读超时对建图偏短，直连 Mod `:38081` 或调大 `response-timeout-ms` 更稳；(b) 世界已存在时返回 `World already exists: <name>` ⇒ 用 `join_world` fallback（`World not found` 则反过来用 `create_world`）。
6. **`chat_command` 与 `chat_message` 语义不同：** 前者把内容当 `/命令` **以玩家身份**发出（受玩家权限限制），后者是普通聊天消息。
7. **`click_chat_text` 需要匹配到带 `ClickEvent` 的组件**（例如 `/me` 产生的发送者名组件），普通聊天正文没有 `ClickEvent`；并且要显式传 `execute: false` 才不会被真的点下去。
8. **`keep_alive` 使用硬编码的 id，可能被服务端踢出**（`run_test` 因此把它列入跳过）。
9. **位置相关结果会漂移。** `player_move` 等 action 依赖玩家当前坐标，跨环境的 `message` 坐标不同属正常现象。
10. **`screenshot` 的玩家名可从返回值反解**（`data.filePath` 形如 `screenshots/blackboxpro/<Player>/<testId>/<prefix>.png`）。
11. **CI 目前只做构建与发布**，不跑测试、不跑 lint（见 `.github/workflows/release.yml`）—— 不要把它当作回归门禁。

---

## 安全

**这不是一个安全的对外服务，请只在隔离环境里用。**

- 两个 HTTP 服务都是**明文 HTTP 且没有任何鉴权**；
- 两者都以 `InetSocketAddress(port)` 嗅探全部网卡创建监听 ⇒ **默认绑定 `0.0.0.0`，不是仅 `127.0.0.1`**；
- `chat_command` 能以**玩家权限**执行任意服务端命令；
- `POST /execute` 的 `stop_server` 会**直接把服务端停掉**（`submit { Bukkit.dispatchCommand(console, "stop") }`）；
- 一个在线玩家或被允许连接的任何人都能通过这些端点驱动客户端并读回数据（截图、聊天历史、背包内容）。

**做法建议：**

1. 只在**隔离测试服**上开 `test-mode: dual`，**不要**复用正式服/正式端口；
2. 用防火墙或端口转发把 `38080` / `38081` 限制在 `127.0.0.1`，不要暴露到公网或办公网；
3. 测试服按最小暴露配置（白名单、必要的 `online-mode` 策略），不要给测试玩家超出测试需要的权限；
4. 用完就停：先 `stop_server`/关客户端，再确认端口释放。

---

## 验证状态（诚实的现状）

这一节只写可复算、可追溯的事实。

**1.21.11 参考基线** —— `run_test scope=full` 实测 `56 passed / 2 failed / 57 skipped / 115 total`（服务端日志：`完成: 56 通过, 2 失败, 57 跳过`，总耗时约 53 s）。这 2 个 failed 是 `click_chat_text` 与 `query_chat_style`，报错均为 `Invalid params: Missing required field: match` —— 即 `run_test` 用例没给它们传必填参数 `match`，**属用例侧缺陷，不是客户端能力缺失**。

**1.21.4** —— 需要 PR #3（`feat/1.21.4-fabric`；`dev-2.0` 上没有 `mod/1.21.4/`）。在该分支上、并修好 `pauseOnLostFocus` 后，实测**同样**为 `56 passed / 2 failed / 57 skipped / 115 total`。

**`116 = 59 passed / 0 failed / 57 skipped` 的前提，必须说清楚：**

> 需要三样东西同时在位：#3 提供 1.21.4 端；#4 给两个用例补上 `match` 参数（2 failed → 0）；#6 把 `screenshot_tooltip` 登记进 `ActionCatalog`（total 115 → 116，该 action 通过 ⇒ passed 57 → 59）。
>
> **✅ 已可由单一分支静态复现**：`integrate/1214-catalog`（`c16ca2f`）一条分支即齐备——
> ```bash
> git log --oneline integrate/1214-catalog           # c16ca2f → df610f0(match) → 52ea421(1.21.4 端)
> git show integrate/1214-catalog:common/src/main/kotlin/com/blackboxpro/common/action/ActionCatalog.kt | grep -c 'register("'   # → 116
> git ls-tree -r --name-only integrate/1214-catalog | grep -c '^mod/1\.21\.4/'                                                  # → 161
> ```
> 另需 `BlackBoxTestProfile` 含 `MC_1214`、`BlackBoxTestCatalog` 含 `match` 修复（该分支两者皆有）。
>
> ⚠️ 以上是**静态同构**证据（分支内容齐备）；`59/0/57/116` 这个**运行结果**本身仍需在实机上重跑才能确认。
> 历史上它先在本地未提交工作树上取得，现在该状态已固化为 `integrate/1214-catalog` 分支，可直接检出复现。

**57 项 `skipped` 的来历**（可逐项复算）：`BlackBoxTestCatalog` 的 `pendingFixtureActions` 共 **71** 项；实际 `skipped` 的 57 项**全部**落在这 71 项之内（57/57，无遗漏、无例外），未被跳过的是下列 **14** 项——它们的前置夹具已经就绪：

```
break_block, click_slot, close_container, dig_cancel, dig_finish, dig_start,
drop_inventory, drop_item, drop_item_stack, look_at_block, place_block,
place_block_at, update_sign, use_item
```

⇒ **71 − 14 = 57**，与 `data.skipped` 实测值吻合。复算方法：读结果 JSON 的 `data.results`（每项 `{action,status,message}`，注意跳过数在 `data.skipped` 是**标量**、明细在 `results`），取其 `status=="skipped"` 的 `action` 集合，与 `pendingFixtureActions` 求差。跳过原因是**夹具缺口**，与客户端能力无关。

> ⚠️ **口径陷阱（两个算式都得 57，但来源不同）**：
> - 上面这组 `71 / 14` 是 **`dev-2.0` 口径**（`total=115` 的那次跑）。
> - 而 `59/0/57` 那次的 `total=116`（多了 `screenshot_tooltip`）⇒ 对应的是 **`72 − 15 = 57`**（`pendingFixtureActions` 多一项、就绪项也多一项）。
> - 两个减法**数值相同纯属巧合**。引用时请连同**树/分支**一起说，别把 71 和 116 混在一句里。

**只有 58/115 项真正执行过，且断言强度有限**：实际执行 = `56 passed + 2 failed` = 58 项；其中 15 项（`screenshot` + 14 个 `query_*`）额外断言"返回的 `data` 非空"，其余 43 项只校验"响应 `status=success`"。因此**不要**把这套计数当作"客户端侧功能全覆盖"的证据。

> **口径提醒（分子分母必须同源）**：`58/115` 这组数字来自 **`dev-2.0` 口径**（`total=115`）。
> 在 **PR #6 之上**（`total=116`）实测是 `59 passed / 0 failed / 57 skipped`，即 **`59/116`**。
> 混用两组会得到"58/116"这种不存在的组合 —— 引用时请连同**树/分支**一起说。

**未验证项**：1.21.1 与 1.12.2 端在本次工作中**没有实机跑过全量 `run_test`**（矩阵中的注册数来自源码静态统计，非运行时验证）；1.21.11/1.21.4 的加载器矩阵中，NeoForge 侧同样没有实机验证记录。

---

## 构建与产物

根 `build.gradle.kts` 里真实存在的任务如下（**没有 `mod_buildAll` 这个任务**）：

```bash
./gradlew buildAll              # 全量：common + 1.21.11 + 1.21.1 + 1.12.2 + 服务端插件，并收集 jar 到根 build/libs
./gradlew mod2111_build         # 仅 1.21.11 客户端（:1.21.11:runtime/fabric/neoforge）
./gradlew mod1211_build         # 仅 1.21.1 客户端（runtime + fabric；neoforge 只编译 classes/processResources）
./gradlew mod1211_pack_neoforge # 把 1.21.1 NeoForge 手动打包成 jar（依赖 mod1211_build）
./gradlew plugin_build          # 仅服务端插件（独立项目 plugin/）
./gradlew forge1122_build       # 仅 Forge 1.12.2（独立构建根 mod/1.12.2）
./gradlew common_build          # 仅 common jar
./gradlew cleanAll              # 清理所有模块的 build 目录
```

| 产物 | 路径 |
|------|------|
| Fabric 1.21.11 | `mod/1.21.11/fabric/build/libs/BlackBoxPro-fabric-1.21.11-*.jar` |
| NeoForge 1.21.11 | `mod/1.21.11/neoforge/build/libs/BlackBoxPro-neoforge-1.21.11-*.jar` |
| Fabric 1.21.1 | `mod/1.21.1/fabric/build/libs/BlackBoxPro-fabric-1.21.1-*.jar` |
| NeoForge 1.21.1 | `mod/1.21.1/neoforge/build/libs/BlackBoxPro-neoforge-1.21.1-*.jar` |
| Forge 1.12.2 | `mod/1.12.2/build/libs/BlackBoxPro-forge-1.12.2-*.jar` |
| 服务端插件 | `plugin/build/libs/BlackBoxPro-Plugin-*.jar` |
| 聚合收集 | `build/libs/` |

`.github/workflows/release.yml` 在 push 到 `main` / `dev-2.0` 时构建并创建 Release（`dev-2.0` 上打 `-dev` 预发布 tag）；它不运行测试。

## 仓库结构

```text
BlackBoxPro/
├── common/                    # 无 MC 依赖的共享协议层（ActionCatalog / HTTP / 运行时契约）
├── plugin/                    # 服务端插件（TabooLib；HTTP :38080、测试运行器、夹具管理）
├── mod/
│   ├── 1.21.11/               # runtime（公共核心）+ fabric + neoforge
│   ├── 1.21.1/                # runtime + fabric + neoforge
│   └── 1.12.2/                # 独立构建根：runtime + forge
├── docs/                      # design / testing / reports（设计、测试计划、历史报告）
├── .claude/                   # AI Agent 接入：skills/ + commands/（纳入版本控制）
├── .github/workflows/         # Build & Release
├── build.gradle.kts           # 根聚合入口（buildAll 等）
└── settings.gradle.kts
```

## 技术栈

- Kotlin（JVM 21 / JVM 8），Gradle Kotlin DSL 多模块聚合
- Fabric API / NeoForge / Forge 1.12.2；TabooLib 6.2（服务端插件）；Gson
- 客户端物理输入模拟（`InjectedInput`）、A* 寻路、Mixin（tooltip 渲染路径）
- HTTP 侧使用 JDK 内置 `com.sun.net.httpserver`

## 给 AI Agent / 自动化使用

仓库内置了给 Agent 用的技能包（`.claude/` 下 `skills/`、`commands/` 是显式纳入版本控制的）：

| 路径 | 内容 |
|------|------|
| `.claude/skills/blackboxpro-test/SKILL.md` | 部署 + 测试主流程（客户端自测 / 服务端联调两种模式） |
| `.claude/skills/blackboxpro-test/reference/action-catalog.md` | 逐 action 参数表 |
| `.claude/skills/blackboxpro-test/reference/http-api.md` | HTTP 端点与响应契约 |
| `.claude/skills/blackboxpro-test/reference/register-action.md` | 如何新增一个 action |
| `.claude/skills/add-mc-version/skill.md` | 新增一个 Minecraft 版本的完整流程 |
| `.claude/skills/validate_blackboxpro_skills.py` | 技能文档一致性校验脚本（**当前 CI 未运行它**） |

注意：`action-catalog.md` 里的计数与 `ActionCatalog` 之间存在已记录的漂移，请以源码或 `/status` 的 `actions` 字段为准。

## 贡献

没有 `CONTRIBUTING.md`，沿用现状约定：

1. 从 `dev-2.0` 切分支（`feat/*`、`fix/*`），PR 目标也指向 `dev-2.0`；
2. 行为改动请附**可复算的证据**：命令、原始输出、以及涉及 `run_test` 时的 `passed/failed/skipped/total` 四项数字；
3. **不要只贴 `run_test` 的数字就声称"插件通过测试"**（语义见上文）；断言强度不足的地方请明确标注；
4. 改 `ActionCatalog` / 各端 `ActionRegistry` 时，同步更新 `.claude/skills/blackboxpro-test/reference/action-catalog.md` 与计数；
5. 新增 Minecraft 版本请走 `.claude/skills/add-mc-version/skill.md` 的流程。

## 许可证

[MIT](LICENSE)
