# BlackBoxPro HTTP API Reference

所有响应都是 UTF-8 JSON，`Content-Type: application/json; charset=utf-8`，**HTTP 状态码恒为 200** —— 成功/失败一律看响应体里的 `status` 字段，不要看状态码。

## 端点总表

| 端点 | Plugin (38080) | Mod (38081) | 说明 |
|------|:--------------:|:-----------:|------|
| `POST /execute` | ✅ | ✅ | 执行 Action。Plugin 侧会转发给 Mod（`dual` 模式） |
| `/status` | ✅ | ✅ | 就绪探测。两个进程的**响应字段不同**，见下 |
| `/actions` | ❌ | ❌ | **不存在**。`HttpEndpoints.ACTIONS` 常量在代码里有，但从未 `createContext` 注册 ⇒ 去查它只会拿到 404。要清单请读 `reference/action-catalog.md` |

> Plugin 侧只对 `/execute`、`/status` 建了 context；对 `/execute` 之外的 method 会返回 `failure: Method not allowed, use POST`。

## POST /execute

### 请求

```json
{
  "id": "unique-request-id",
  "action": "action_id",
  "params": { }
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|:----:|------|
| `id` | string | 是 | 请求唯一标识，原样回传。**同一客户端同一时刻只发一个请求，且 id 必须唯一**（Mod 用 id 做响应路由，撞 id 会串线） |
| `action` | string | 是 | Action ID（清单见 `reference/action-catalog.md`） |
| `params` | object | 否 | Action 参数，缺省 `{}` |

请求体上限 1 MB。

### 响应

```json
{ "id": "unique-request-id", "status": "success", "message": "Action completed", "data": { } }
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | string | 与请求 id 一致 |
| `status` | string | `"success"` 或 `"failure"` |
| `message` | string? | 人类可读描述；失败原因主要靠它 |
| `data` | object? | 查询类 action 的结果（`query_*`、`screenshot` 等） |

### 两个只在 Plugin 侧（38080）生效的 action

| action | params | 行为 |
|--------|--------|------|
| `run_test` | `player`（**必填**）、`scope`（可选，默认 `full`） | 跑框架自测，阻塞到结束 |
| `stop_server` | 无 | 让服务端执行 `/stop`。**能停服，不要对生产服发** |

#### `run_test` 细则（这是最容易误读的地方）

- **`params.player` 必填**，必须是在线玩家名。缺了直接返回 `failure: Missing param: player`；名字不在线返回 `failure: Player not online: <name>`。
- **`scope` 只有 `smoke` 与 `full` 两种取值**（`BlackBoxTestProfile` 只有 `SMOKE` / `FULL`）；`ExecuteHandler` 的判定是 `"smoke" -> runSmoke`，**其余一律 `runFull`**。写 `scope=all` 不会报错，只会静默按 `full` 跑。
- `smoke` = `BlackBoxTestCatalog.smokeCases()` 里硬编码的一小撮基础链路（移动/潜行/聊天/查询，条数不写死，直接看返回的 `total`）；`full` = `ActionCatalog.getDefinitions().map(::buildCase)`，即 **每个进 `ActionCatalog` 的 action 一条**（所以它**不覆盖** `ActionCatalog` 未收录的 action，例如 `screenshot_tooltip`）。
- 服务端侧硬超时 1800 秒（30 分钟），超时返回 `failure: Test timed out after 30 minutes`。
- 成功时 `data` 形状（下面数值取真实样本，**不是固定值**）：

```json
{
  "passed": 21, "failed": 1, "skipped": 0, "total": 22, "totalMs": 8379,
  "results": [
    { "action": "player_on_ground", "status": "passed", "message": "success" },
    { "action": "jump", "status": "failed", "message": "Player is not on ground" },
    { "action": "query_tooltip_state", "status": "skipped", "message": "需要显式打开 GUI；当前 run_test 默认场景不覆盖" }
  ]
}
```

`results[].status` ∈ `passed` / `failed` / `skipped`。

> ★ **`passed` / `failed` 说的是"框架 action 在本端能否真正生效"，不是"我的插件通过了测试"。**
> `run_test` 的用例表在 `BlackBoxTestCatalog` 里硬编码为 ActionCatalog 自测，不是可扩展的插件测试套件；
> 要验自己的插件必须由宿主 Agent 自编排 `/execute` 序列（`chat_command` 开界面 → `query_container_slots` / `screenshot_tooltip` / `query_chat_style` → `close_container`）。判别方法见下文「结果判定」一节。

## /status

### Plugin (38080) —— 探测"插件是否起来了"

```json
{ "version": "2.2.4", "mode": "dual", "httpPort": 38080, "modHttpAddress": "http://localhost:38081", "ready": true }
```

| 字段 | 说明 |
|------|------|
| `version` | 插件版本（来自 `BlackBoxPro.VERSION`） |
| `mode` | `dual` 或 `server_only`（来自 `config.yml` 的 `test-mode`） |
| `httpPort` | 插件 HTTP 端口 |
| `modHttpAddress` | plugin 转发目标 |
| `ready` | **恒为 `true`**，只表示 HTTP 起来了 —— 不代表 Mod 在线 |

### Mod (38081) —— 探测"客户端是否已进世界 + 本端注册表多大"

```json
{ "status": "running", "version": "unknown", "platform": "fabric", "httpPort": 38081, "actions": 116, "ready": true }
```

| 字段 | 说明 |
|------|------|
| `status` | 恒为 `running` |
| `version` | Mod 版本；本地 dev 构建下可能解析成 `"unknown"` |
| `platform` | `fabric` / `neoforge` / `forge` |
| `httpPort` | Mod HTTP 端口（默认 38081，见下"单实例"） |
| `actions` | **运行时注册表大小** —— 想要"这个客户端到底有几个 action"就读这里，别信文档里的数字 |
| `ready` | `client.player != null && client.world != null`，即**已进入世界** |

> ⚠️ **端口 38081 实际上是固定值**：`RuntimeNetworkConfig.httpPort` 默认 38081，而 `RuntimeBlackBoxConfig.update()` 在当前分支**没有任何调用方**（没有配置文件读取路径）⇒ **同一时刻只允许一个客户端实例**，第二个会端口冲突。

## 结果判定 / "环境未就绪 ≠ 被测程序失败"

| 观察到 | 归因 | 处理 |
|--------|------|------|
| `failure: Missing param: player` | **调用方写错了**（漏传必填参数） | 补 `params.player` 再发 |
| `failure: Player not online: X` | **环境未就绪**（玩家没连上/名字不对） | 先用 Mod 的 `query_tab_list` 取真实玩家名，见下 |
| `failure: server_only mode: no client actions available` | **配置错**：`config.yml` 的 `test-mode` 不是 `dual` | 改 `test-mode: dual` 并重启服务端 |
| `failure: Cannot coerce value to an empty range: maximum -1.0 is less than minimum 0.0.` | **环境未就绪**：客户端窗口被**最小化**了（窗口尺寸变 0 ⇒ 光标坐标计算区间为空） | 恢复窗口，见下；不要记成缺陷 |
| `status: failure, message: Timeout after 10000ms` | **环境未就绪或耗时过长**（截图/tooltip 偶发超过 `response-timeout-ms`） | 调大 `config.yml` 的 `response-timeout-ms`；确认窗口可见 |
| `results[]` 里 `status: skipped` + 一句原因 | **夹具缺失**，不是失败 | 按 message 补夹具或忽略 |
| `results[]` 里 `status: failed`，message 是 `Player is not on ground` / `No container screen open` | **环境未就绪**（玩家姿势/界面状态不对） | 先修正环境（落地、开界面）再重跑 |
| 端口连不上 | 进程没起来 | 分别探测 38080 / 38081 的 `/status` |
| `connected=false` / `query_screen_state.data.open` 表明还在主菜单 | **未连服务端** | 用 `connect_to_server`，或人工在客户端连 |

> 记住这条总原则：**HTTP 能通、`status: success`、`results[].status == passed` 是三个不同层次的事实。**
> 只有"环境先就绪"才谈得上"被测程序失败"。

## 最小示例（默认端口 38080 / 38081，可用变量覆盖）

### bash / curl

```bash
PLUGIN_PORT=${PLUGIN_PORT:-38080}
MOD_PORT=${MOD_PORT:-38081}

# 1) 双端就绪探测
curl -s --max-time 5 "http://127.0.0.1:${PLUGIN_PORT}/status"
curl -s --max-time 5 "http://127.0.0.1:${MOD_PORT}/status"

# 2) 取真实玩家名（不要硬编码）
curl -s --max-time 8 -X POST "http://127.0.0.1:${MOD_PORT}/execute" \
  -H "Content-Type: application/json" \
  -d '{"id":"tab-1","action":"query_tab_list","params":{"limit":100}}'

# 3) 打开自己的背包并读槽位
curl -s --max-time 8 -X POST "http://127.0.0.1:${MOD_PORT}/execute" \
  -H "Content-Type: application/json" -d '{"id":"inv-1","action":"open_inventory","params":{}}'
curl -s --max-time 8 -X POST "http://127.0.0.1:${MOD_PORT}/execute" \
  -H "Content-Type: application/json" -d '{"id":"inv-2","action":"query_container_slots","params":{}}'

# 4) 关掉界面
curl -s --max-time 8 -X POST "http://127.0.0.1:${MOD_PORT}/execute" \
  -H "Content-Type: application/json" -d '{"id":"inv-3","action":"close_container","params":{}}'

# 5) 链路体检（注意 scope 只有 smoke/full）
curl -s --max-time 120 -X POST "http://127.0.0.1:${PLUGIN_PORT}/execute" \
  -H "Content-Type: application/json" \
  -d '{"id":"rt-1","action":"run_test","params":{"player":"<PLAYER>","scope":"smoke"}}'
```

### node（**结果落盘必须用它或显式 UTF-8，见下**）

```js
// bbp-call.mjs —— node bbp-call.mjs <port> <action> '<json params>'
import { writeFileSync } from 'node:fs';

const [port, action, paramsRaw = '{}'] = process.argv.slice(2);
const body = { id: `c-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`, action, params: JSON.parse(paramsRaw) };

const res = await fetch(`http://127.0.0.1:${port}/execute`, {
  method: 'POST',
  headers: { 'content-type': 'application/json' },
  body: JSON.stringify(body),
  signal: AbortSignal.timeout(180_000),
});
const text = await res.text();                    // 不要用 res.json()：我们要自己存原始字节
const json = JSON.parse(text);                    // 解析失败 ⇒ 上游不是 UTF-8
console.log(JSON.stringify(json, null, 2));
// 显式 UTF-8、无 BOM，且写完立刻复验可解析
writeFileSync('result.json', text, { encoding: 'utf8' });
JSON.parse(await (await import('node:fs/promises')).readFile('result.json', 'utf8'));
```

### PowerShell

```powershell
$PluginPort = if ($env:PLUGIN_PORT) { $env:PLUGIN_PORT } else { 38080 }
$ModPort    = if ($env:MOD_PORT)    { $env:MOD_PORT }    else { 38081 }

function Invoke-Bbp([int]$Port, [string]$Action, [hashtable]$Params = @{}) {
    $body = @{ id = "ps-$([guid]::NewGuid())"; action = $Action; params = $Params } | ConvertTo-Json -Compress -Depth 10
    # 关键：显式 UTF-8 字节体，避免中文被按 GBK 编码发出去
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($body)
    $r = Invoke-WebRequest -Uri "http://127.0.0.1:$Port/execute" -Method Post `
         -ContentType 'application/json; charset=utf-8' -Body $bytes -TimeoutSec 180
    [System.Text.Encoding]::UTF8.GetString($r.RawContentStream.ToArray())   # 关键：自己按 UTF-8 解码
}

Invoke-Bbp $ModPort   'query_tab_list' @{ limit = 100 }
Invoke-Bbp $ModPort   'open_inventory'
Invoke-Bbp $ModPort   'query_container_slots'
Invoke-Bbp $ModPort   'close_container'
Invoke-Bbp $PluginPort 'run_test' @{ player = '<PLAYER>'; scope = 'smoke' }
```

> **落盘编码是真实踩过的坑**：Windows PowerShell 5.1 的 `>` / `Out-File` / `Set-Content` 默认用 ANSI(936) 或 UTF-16LE，结果文件开头会出现 `FF FE` BOM，node 按 UTF-8 读会直接 `Unexpected token` 报错。
> 写结果一律 `[System.IO.File]::WriteAllText($p, $text, (New-Object System.Text.UTF8Encoding($false)))`，并立刻用 node 复验能 `JSON.parse`。

## 安全提醒

Plugin 的 HTTP 服务是 `HttpServer.create(InetSocketAddress(port), 0)` —— **绑定通配地址（0.0.0.0），默认没有鉴权**，而 `/execute` 里的 `chat_command` 可以发**任意服务端命令**、`stop_server` 可以直接停服。
⇒ 只在本机隔离测试服上开，只允许 `127.0.0.1` 访问。加固方向（改代码才生效，本文件不代做）：把绑定地址从 `InetSocketAddress(port)` 换成 `InetSocketAddress(InetAddress.getLoopbackAddress(), port)`（`plugin/.../http/BlackBoxHttpServer.kt`、`mod/.../http/ModHttpServer.kt` 各一处），并给 `/execute` 加 token 校验。

## 端口速查

| 组件 | 默认端口 | 归属 |
|------|:--------:|------|
| Plugin HTTP（`/execute`、`/status`） | 38080 | BlackBoxPro |
| Mod HTTP（`/execute`、`/status`，**实际固定**） | 38081 | BlackBoxPro |
| Minecraft 服务端游戏端口 | 25565 | BlackBoxPro 约定；测试 harness 实例不得占用 |
