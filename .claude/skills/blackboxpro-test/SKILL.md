---
name: blackboxpro-test
description: 部署并测试 BlackBoxPro 黑盒测试环境。两种模式：客户端自测（单人世界 HTTP 直达 mod:38081）和服务端联调（plugin:38080 转发 mod:38081）。覆盖构建、部署、环境检测、测试、截图视觉分析；启动前必须检查并释放 25565/38080/38081 的对应旧实例端口占用，且禁止由 AI 代启动客户端或服务端。
---

BlackBoxPro 自动化测试部署技能。根据用户指定的版本和模式，执行构建→部署→环境检测→测试→清理流程；**不负责代启动任何长驻客户端/服务端进程**。

## 参考资源（Level 3 按需加载）

本技能的 `reference/` 目录包含以下资源，**不要预加载**，在需要时用 Read 工具按需读取：

| 文件 | 用途 | 何时读取 |
|------|------|---------|
| `reference/action-catalog.md` | Action 的 ID、参数、分类快照（**正文不写死计数**；真值由 `validate_blackboxpro_skills.py` 从源码动态复算，本目录已有 CI 校验） | 用户问"有哪些 action"、需要查参数、按分类筛选时 |
| `reference/http-api.md` | HTTP 端点格式（请求/响应 JSON 结构） | 需要确认 API 调用格式时 |
| `reference/register-action.md` | 注册第三方自定义 Action 的完整流程 | 用户要添加新 action、扩展测试能力时 |

> `reference/action-catalog.md` 只是便于检索的快照；若与实际代码不一致，以 `common/src/main/kotlin/com/blackboxpro/common/action/ActionCatalog.kt` 和对应 loader 的 `ActionRegistry.kt` 为准，尤其注意 `connect_to_server`、`close_screen` 这类容易遗漏的会话类 action。

示例场景：
- 用户："查询类的 action 有哪些？" → Read `reference/action-catalog.md`，定位"查询行为"章节
- 用户："screenshot 需要什么参数？" → Read `reference/action-catalog.md`，搜索 `screenshot`
- 用户："请求格式是什么？" → Read `reference/http-api.md`
- 用户："我需要一个 xxx action" → 先查 `reference/action-catalog.md`，找不到再查 `ActionCatalog.kt` / `ActionRegistry.kt`，仍不存在再引导注册自定义 action（见下方"找不到 Action 的处理流程"）

### 找不到 Action 的处理流程

当用户需要某个 action 但不确定是否存在时，按以下决策树处理：

```
用户描述需求
    │
    ├─ Step 1：查 reference/action-catalog.md
    │   ├─ 找到匹配 → 直接使用，告知 action ID 和参数
    │   └─ 未找到 → Step 1.5
    │
    ├─ Step 1.5：查代码事实源
    │   ├─ Read common/.../ActionCatalog.kt
    │   ├─ 必要时再查对应 loader 的 ActionRegistry.kt
    │   ├─ 找到匹配 → 直接使用，顺便指出 reference 快照已滞后
    │   └─ 仍未找到 → Step 2
    │
    ├─ Step 2：模糊匹配（功能相近的 action）
    │   ├─ 有相近替代方案 → 告知用户，说明差异
    │   └─ 无替代 → Step 3
    │
    └─ Step 3：引导注册自定义 Action（Read register-action.md）
```

#### Step 3：注册自定义 Action 流程

Read `reference/register-action.md` 获取完整 API，然后按以下步骤引导用户：

**① 实现 ActionExecutor**

在用户 mod 中创建 action 类（`com.blackboxpro.common.runtime.action.ActionExecutor`）：

```kotlin
class MyAction : ActionExecutor {
    override fun execute(params: JsonObject): ActionResult {
        val value = params.getStringOrNull("key")
            ?: return ActionResult.fail("Missing: key")
        // 业务逻辑...
        return ActionResult.ok(message = "done")
    }
}
```

**② 声明对 BBP 的前置依赖**

| 平台 | 配置文件 | 写法 |
|------|----------|------|
| Fabric | `fabric.mod.json` | `"depends": { "blackboxpro": "*" }` |
| NeoForge | `mods.toml` | `[[dependencies.yourmod]]` + `modId = "blackboxpro"` |
| Forge 1.12.2 | `@Mod` 注解 | `dependencies = "required-after:blackboxpro"` |

**③ 在 mod 初始化时注册**

```kotlin
// Fabric
ActionRegistry.registerExternal("mymod_my_action", MyAction())

// NeoForge
ActionRegistry.registerExternal("mymod_my_action", MyAction())

// Forge 1.12.2（在 FMLInitializationEvent 中）
ActionRegistry.registerExternal("mymod_my_action", MyAction())
```

> action ID 建议加 mod 前缀（如 `mymod_`）避免与 BBP 内置 action 冲突。

**④ 验证注册成功**

重启客户端后调用 `/status` 确认 mod 已加载，然后直接 HTTP 调用测试：

```bash
curl -s --max-time 8 -X POST http://localhost:38081/execute \
  -H "Content-Type: application/json" \
  -d '{"id":"test1","action":"mymod_my_action","params":{"key":"value"}}'
```

**注意事项**：
- 主线程操作需调度：Fabric `MinecraftClient.getInstance().execute {}` / NeoForge `Minecraft.getInstance().execute {}`
- 长耗时操作使用异步模式（`ActionResult.async()` + `RuntimeResponseSender`），详见 `register-action.md`
- 自定义 action 不参与 `run_test` 全量测试，只能通过 HTTP 手动调用

## 通用规则

### 非阻塞规则

**禁止由 AI 调起任何长驻/阻塞型进程。** 包括但不限于：
- 启动 Minecraft 客户端（`runClient`、启动器、PCL、外部 launcher、对应 Terminal/Skill）
- 启动 Paper/Spigot 服务端（`java -jar ... nogui`、对应 Terminal/Skill）
- 任何会持续占用终端并导致后续步骤悬挂等待的命令
- 但允许使用 NarraFork 对话中启动的 Terminal 并自动执行命令

执行边界：
- **允许**：构建、复制产物、编辑配置、有限时 `curl` 请求、读取已有日志/状态、停止旧实例、轮询短时就绪状态
- **禁止**：使用 `Bash run_in_background: true` 启动客户端/服务端，或调用任何用于启动 client/server 的 Skill / Terminal
- 若测试所需环境未运行：**立即停止自动流程**，输出配置中的启动命令、工作目录、`JAVA_HOME` 与就绪判定方式，要求用户在外部终端或已有会话手动启动；检测到 `:38080` / `:38081` 就绪后再继续

### 轮询规则

**禁止任何单次等待超过 8 秒。** 所有需要等待的场景必须使用轮询循环：

```
每 N 秒检查一次，最多 M 次，超过则判定失败
```

各场景的轮询参数：

| 场景 | 间隔 | 最大次数 | 总上限 | 判定成功标志 |
|------|------|---------|--------|-------------|
| HTTP 就绪 (:38081) | 3s | 40 | 120s | `curl -sf localhost:38081/status` 返回 0 |
| HTTP 就绪 (:38080) | 3s | 40 | 120s | `curl -sf localhost:38080/status` 返回 0 |
| 服务端启动 | 3s | 40 | 120s | 日志包含 `Done` |
| 世界创建/加入 | 已由 curl 阻塞等待响应，无需轮询（curl 自带 --max-time 90） | | | |
| 客户端连接服务器 | 3s | 20 | 60s | 服务端日志包含 `joined the game` |

轮询实现：使用 Bash `for` 循环 + `sleep`，例如：
```bash
for i in $(seq 1 40); do
  curl -sf http://localhost:38081/status > /dev/null 2>&1 && break
  [ $i -eq 40 ] && echo "FAIL: Mod HTTP not ready after 120s" && exit 1
  sleep 3
done
```

### 环境准备规则

**切换测试环境前必须先做端口预检查，发现对应旧实例仍在运行时先停止，再继续。**

- 客户端相关：检查 `38081`；若占用，先停止旧的 Mod / 客户端进程，确认端口释放后再继续。
- 服务端相关：检查 `25565` 与 `38080`；若占用，优先对旧服务端执行 `stop_server`（`38080` 可访问时），再处理残留服务端进程，确认两个端口都释放后再继续。
- 两个 MC 版本共用这些端口，**禁止并行保留两个版本实例**；发现旧版本残留时必须先清理。

#### 旧实例清理状态机（统一流程）

1. **识别归属**
   - 先判定旧实例是：HTTP 可达的 BBP 实例、当前会话自己创建的后台任务/terminal，还是用户手动启动的外部进程。
   - 只允许接管当前会话明确创建的 task / terminal；**禁止**向未知用户终端盲发 `Ctrl-C`、`y` 或其他输入。
2. **优雅停止**
   - 服务端：若 `curl -sf http://localhost:38080/status` 成功，先调用 `stop_server`。
   - 客户端：若 `curl -sf http://localhost:38081/status` 成功，只做会话级清理（如 `leave_world` 回主菜单）；**不负责代替用户关闭整个客户端进程**。
   - 若存在当前会话自己创建的后台任务 / terminal，先发送一次中断，再立即读取 buffer；若出现确认提示，先处理提示，再继续，不要连续盲发多次 `Ctrl-C`。
3. **退出确认**
   - 每次停止动作后都必须重新检查：
     - `:38080` / `:38081` 的 `/status` 是否已不可访问
     - `25565` / `38080` / `38081` 对应端口是否已释放
     - 对于 owned task / terminal，进程是否已结束或终端是否已回到 shell prompt
   - 任一条件未满足，都**不能**开始下一次启动或部署。
4. **升级处理**
   - 若 HTTP 已不可达但端口仍被占用，视为残留 Java / Gradle / Minecraft 进程。
   - 若不是当前会话 owned task / terminal，停止自动流程，明确要求用户关闭对应外部终端/启动器；必要时再处理占用该端口的残留进程。
5. **重启闸门**
   - 只有在端口确认释放后，才允许部署新产物、提示用户重新启动，或进入下一阶段测试。

**本技能不负责启动客户端/服务端。** 当需要用户手动启动时，必须输出：
- 启动命令
- 工作目录
- `JAVA_HOME`（若有）
- 就绪判定方式（如 `curl -sf http://localhost:38081/status`、日志 `Done`、`joined the game`）

### 工程/配置约定（非常重要）

- **本技能运行目录 = 接入/使用 BBP 的项目仓库根目录**（即你当前执行技能的项目）。
- **配置文件也属于接入项目**：每次执行前必须先读取 `./.claude/config/blackboxpro-env.json`（不是 BBP 仓库里的配置）。

### 定位或拉取 BBP 仓库（bbp.* → BBP_ROOT）

先根据配置计算 `BBP_ROOT`（BlackBoxPro 仓库根目录）：

| 配置项 | 说明 | 示例 |
|--------|------|------|
| `bbp.method` | `local`（本机路径）或 `git`（自动克隆） | `local` |
| `bbp.localPath` | BBP 仓库根目录（仅 local） | `D:/repos/BlackBoxPro` |
| `bbp.git.url` | BBP git 地址（仅 git） | `https://github.com/<org>/BlackBoxPro.git` |
| `bbp.git.ref` | 分支/Tag/Commit（仅 git，可空=默认分支） | `main` |
| `bbp.git.cloneDir` | 克隆目录（仅 git，相对“接入项目”根目录） | `.bbp/BlackBoxPro` |

解析规则：
1. `local`：`BBP_ROOT = bbp.localPath`
2. `git`：若 `cloneDir` 不存在则先 `git clone`，再（可选）checkout `ref`；`BBP_ROOT = <cloneDir>`
3. 校验：`BBP_ROOT` 下必须存在 `gradlew` 或 `gradlew.bat`

示例脚本（git 模式，在“接入项目根目录”执行）：
```bash
CLONE_DIR="<bbp.git.cloneDir>"
[ -d "$CLONE_DIR/.git" ] || git clone "<bbp.git.url>" "$CLONE_DIR"
[ -z "<bbp.git.ref>" ] || (cd "$CLONE_DIR" && git checkout "<bbp.git.ref>")
BBP_ROOT="$CLONE_DIR"
```

### 运行参数配置（versions.*）

若关键字段为空，暂停流程，用 AskUserQuestion 逐项询问后回填配置文件。

**方案 A 必填项**：`client.launchCommand`
**方案 B 额外必填**：`server.directory` + `server.jar`

| 配置项 | 说明 | 示例 |
|--------|------|------|
| `client.launchMethod` | `runClient`（Gradle）或 `launcher`（外部启动器） | `runClient` |
| `client.launchCommand` | 启动客户端的完整命令 | `./gradlew --no-daemon :<version>:fabric:runClient` |
| `client.launchCwd` | 工作目录（相对 `BBP_ROOT`；空=`BBP_ROOT`） | `mod` |
| `client.javaHome` | JAVA_HOME（空=系统默认） | `C:/Program Files/Java/jdk-21` |
| `client.playerName` | 游戏内玩家名 | `Player` |
| `server.directory` | 服务端安装目录（绝对路径或相对接入项目根） | `D:/mc-server/paper-<version>` |
| `server.jar` | 服务端 JAR 文件名 | `paper-<version>-latest.jar` |
| `server.javaPath` | 服务端 java 路径（空=`java`） | `java` |
| `server.jvmArgs` | JVM 参数 | `-Xms4G -Xmx4G -XX:+UseG1GC` |
| `build.javaHome` | 构建用 JAVA_HOME（空=系统默认） | 同 client.javaHome |

### 版本解析规则（必须动态）

- `resolvedVersion`：**用户显式指定** → `config.activeVersion` → `versions.*` 中唯一已配置版本 → 扫描 `<BBP_ROOT>/mod/settings.gradle.kts` 得到的唯一候选；若仍有多个候选才询问用户。
- `referenceVersion`：**仅作为验收基准**，优先 `config.referenceVersion`；若未配置可默认 `1.21.11`，但**绝不能**把它当作当前运行版本。
- `resolvedLoader`：用户显式指定 > `config.versions[resolvedVersion].client.loader`（若有）> 从 `client.launchCommand` 推断（`fabric` / `neoforge` / `forge`）。
- 所有命令、产物路径、run 目录、截图目录都必须基于 `resolvedVersion + resolvedLoader` 展开，禁止把 `1.21.11` 当成隐式默认值。

## 决策树

```
用户请求测试
    │
    ├─ 指定了版本？
    │   ├─ 是 → resolvedVersion = 用户指定
    │   └─ 否 → 按“版本解析规则”动态解析
    │
    ├─ 指定了模式？
    │   ├─ 是 → 使用指定模式
    │   └─ 否 → 默认方案 A（客户端自测）
    │
    ├─ 读取配置 → 缺失则询问 → 回填
    │
    └─ 用 resolvedVersion / resolvedLoader 展开命令、路径、报告
```

## 两种部署模式

| 模式 | 名称 | 描述 | HTTP 端口 |
|------|------|------|-----------|
| A | 客户端自测 | 仅 Mod，单人世界，无需服务端 | 直达 `:38081` |
| B | 服务端联调 | Plugin + Mod，多人服务器 | Plugin `:38080` 转发 Mod `:38081` |

## 产物路径（相对 BBP_ROOT）

| 产物 | 路径 |
|------|------|
| 1.21.x Fabric Mod | `<BBP_ROOT>/mod/<resolvedVersion>/fabric/build/libs/BlackBoxPro-fabric-<resolvedVersion>-*.jar` |
| 1.21.x NeoForge Mod | `<BBP_ROOT>/mod/<resolvedVersion>/neoforge/build/libs/BlackBoxPro-neoforge-<resolvedVersion>-*.jar` |
| 1.12.2 Forge Mod | `<BBP_ROOT>/mod/1.12.2/forge/build/libs/BlackBoxPro-forge-1.12.2-*.jar` |
| Plugin | `<BBP_ROOT>/plugin/build/libs/BlackBoxPro-Plugin-*.jar` |

> `resolvedVersion` 只能来自上面的动态解析流程；不要根据旧文档假设成 `1.21.11`。

---

## 方案 A：客户端自测流程

### A1. 构建 Mod

按 `resolvedVersion` / `resolvedLoader` 精确构建，避免因为示例写死旧版本而误建错误模块：
```bash
# 在 BBP_ROOT 下执行
# 1.21.x Fabric
./mod/gradlew -p "<BBP_ROOT>/mod" --no-daemon :<resolvedVersion>:fabric:build

# 1.21.x NeoForge
./mod/gradlew -p "<BBP_ROOT>/mod" --no-daemon :<resolvedVersion>:neoforge:build

# 1.12.2 Forge
./gradlew forge1122_build --no-daemon
```

- 仅执行与当前目标版本/loader 对应的命令。
- 只有用户明确要求全量构建时，才使用 `./gradlew buildAll --no-daemon`。
- 构建失败则停止流程，向用户报告错误。

### A2. 客户端预启动检查（不代启动）

启动前先按“环境准备规则”检查并释放 `38081`；若旧 Mod / 客户端仍在运行，优先停止旧实例并确认端口释放。

先检查：
```bash
curl -sf http://localhost:38081/status
```

- 若已就绪：直接进入 A3
- 若未就绪：**不要执行启动命令**。从配置文件读取以下信息并原样输出给用户，由用户在外部终端手动启动客户端：

```
launchCommand = config.versions[resolvedVersion].client.launchCommand
launchCwd = config.versions[resolvedVersion].client.launchCwd  （空则用 BBP_ROOT）
javaHome = config.versions[resolvedVersion].client.javaHome     （非空则设 JAVA_HOME）
```

建议输出格式：
```text
请在外部终端手动启动客户端：
- 工作目录: <BBP_ROOT>/<launchCwd>
- JAVA_HOME: <javaHome 或 系统默认>
- 命令: <launchCommand>
- 就绪判定: curl -sf http://localhost:38081/status
```

> 不得使用 `run_in_background`、Terminal 技能或其他方式代替用户启动长驻客户端进程。

### A3. 检测 HTTP 就绪

仅在用户确认已手动启动客户端，或 `:38081` 已开始就绪时，才执行以下轮询。每 3 秒检查一次 `localhost:38081/status`，最多 40 次（120 秒）：

```bash
for i in $(seq 1 40); do
  curl -sf http://localhost:38081/status > /dev/null 2>&1 && echo "Mod HTTP ready" && break
  if [ $i -eq 40 ]; then echo "FAIL: Mod HTTP :38081 not ready after 120s"; exit 1; fi
  sleep 3
done
```

失败时：报告 `:38081` 未就绪，并要求用户检查外部终端中的客户端日志；不要尝试代为重启客户端。

### A3.5. 界面检测与初始化

HTTP 就绪后，**立即查询当前界面**，根据结果决定下一步：

```bash
curl -s --max-time 8 -X POST http://localhost:38081/execute \
  -H "Content-Type: application/json" \
  -d '{"id":"qs_init","action":"query_screen_state"}'
```

解析 `data.screenClass`（或 `data.type`），按以下逻辑处理：

#### 场景 1：首次启动界面（语言选择 / 初始化向导）

**判断标志**：screenClass 包含 `LanguageSelectScreen`、`InitialScreen`、`AccessibilityOnboardingScreen` 等，或 options.txt 不存在于运行目录。

**处理流程**：

1. **覆盖 options.txt**：将模板文件复制到客户端运行目录，跳过初始化向导：
   ```bash
   # 运行目录（按 resolvedVersion / resolvedLoader）：
   # 1.21.x Fabric:   <BBP_ROOT>/mod/<resolvedVersion>/fabric/run/
   # 1.21.x NeoForge: <BBP_ROOT>/mod/<resolvedVersion>/neoforge/run/
   # 1.12.2 Forge:    <BBP_ROOT>/mod/1.12.2/forge/run/
   #
   # 模板来源（优先级）：
   # 1. config.versions[resolvedVersion].client.optionsTemplate（若配置了绝对/相对路径）
   # 2. <BBP_ROOT>/mod/options-default.txt（BBP 内置默认模板）
   cp "<optionsTemplate>" "<runDir>/options.txt"
   ```

2. **关闭当前界面**（让客户端回到主菜单）：
   ```bash
   curl -s --max-time 8 -X POST http://localhost:38081/execute \
     -H "Content-Type: application/json" \
     -d '{"id":"cs_init","action":"close_screen"}'
   ```

3. **等待并重新查询**：等待 3s 后再次 `query_screen_state`，确认已进入主菜单（`TitleScreen` / `MainMenuScreen`）。若仍不在主菜单则截图记录并报告。

#### 场景 2：主菜单 / 标题界面

**判断标志**：screenClass 包含 `TitleScreen`、`MainMenuScreen`、`PauseScreen` 等，或 `data.type` 为 `"title"` / `"main_menu"`。

- **方案 A** → 继续 A4（创建/加入世界）
- **方案 B** → 继续 B6（连接服务器）

#### 场景 3：已在世界中（`data.open == false` 或 screenClass 为空/游戏内 HUD）

玩家已在某个世界或服务器中（可能是上次测试未正常退出）：

1. 统一执行 `leave_world` 退出当前世界（方案 A / B 都使用它；当前没有独立 `disconnect` action）
2. 等待 2s 后再次 `query_screen_state` 确认回到主菜单
3. 然后继续正常流程

#### 场景 4：其他未知界面

截图确认当前状态：

```bash
curl -s --max-time 8 -X POST http://localhost:38081/execute \
  -H "Content-Type: application/json" \
  -d '{"id":"ss_diag","action":"screenshot","params":{"testId":"init-diag","prefix":"screen"}}'
```

读取截图文件进行视觉分析，向用户报告当前界面类型后再决定是否继续。

### A4. 进入世界

**前提**：已通过 A3.5 确认当前处于主菜单。

自动决策逻辑（不需要用户选择）：

1. 先尝试 `create_world`：
```bash
curl -s --max-time 90 -X POST http://localhost:38081/execute \
  -H "Content-Type: application/json" \
  -d '{"id":"cw1","action":"create_world","params":{"worldName":"blackbox_test","gameMode":"creative","allowCommands":true}}'
```

2. 若响应包含 `"World already exists"`，自动 fallback 到 `join_world`：
```bash
curl -s --max-time 90 -X POST http://localhost:38081/execute \
  -H "Content-Type: application/json" \
  -d '{"id":"jw1","action":"join_world","params":{"worldName":"blackbox_test"}}'
```

3. 确认响应 `status: "success"` + `state: "in_world"` 后继续。

### A5. 执行测试

方案 A 无 Plugin，不能用 `run_test`。逐个执行 action 并汇总结果。

**推荐测试集**（按优先级）：

```bash
# 1. 基础查询（验证链路通畅）
curl -s --max-time 8 -X POST http://localhost:38081/execute \
  -H "Content-Type: application/json" \
  -d '{"id":"t1","action":"query_player_state"}'

# 2. 截图（验证渲染正常）
curl -s --max-time 8 -X POST http://localhost:38081/execute \
  -H "Content-Type: application/json" \
  -d '{"id":"t2","action":"screenshot","params":{"testId":"self-test","prefix":"verify"}}'

# 3. 其他 action（按需）
# 使用 batch 批量执行多个简单 action：
curl -s --max-time 8 -X POST http://localhost:38081/execute \
  -H "Content-Type: application/json" \
  -d '{"id":"t3","action":"batch","params":{"actions":[
    {"action":"sneak_start","params":{}},
    {"action":"wait","params":{"ticks":10}},
    {"action":"sneak_stop","params":{}},
    {"action":"query_player_state","params":{}}
  ]}}'
```

**结果收集**：每个响应记录 `{action, status, message}`。

### A6. 截图视觉分析

从 screenshot 响应中提取 `data.filePath`，用 Read 工具读取 PNG 进行视觉验证。

截图路径：
- 1.21.x：`mod/<resolvedVersion>/<resolvedLoader>/run/screenshots/blackboxpro/`
- 1.12.2：`mod/1.12.2/forge/run/screenshots/blackboxpro/`

### A7. 清理

```bash
# 1. 离开世界
curl -s --max-time 8 -X POST http://localhost:38081/execute \
  -H "Content-Type: application/json" \
  -d '{"id":"lw1","action":"leave_world"}'

# 2. 本技能不主动关闭客户端
# 如需结束，请提示用户手动关闭外部终端/启动器中的客户端进程
```

### A8. 输出报告

```
=== BlackBoxPro 客户端自测报告 ===
版本: <resolvedVersion>
模式: A（客户端自测）
世界: blackbox_test (created/joined)

测试结果:
  query_player_state: success
  screenshot: success (filePath: ...)
  batch(sneak): success
  ...

通过: N / 总计: M
截图: <filePath>（已视觉分析）
```

---

## 方案 B：服务端联调流程

### B1. 构建

```bash
# 先构建目标版本对应的 Mod
# 1.21.x
./mod/gradlew -p "<BBP_ROOT>/mod" --no-daemon :<resolvedVersion>:<resolvedLoader>:build

# 1.12.2
./gradlew forge1122_build --no-daemon

# 再构建 Plugin
./gradlew plugin_build --no-daemon

# 仅当用户明确要求全量构建时，才使用：
# ./gradlew buildAll --no-daemon
```

### B2. 部署 Plugin

从配置文件读取 `server.pluginDir`（或 `server.directory + "/plugins"`）：

```bash
cp <BBP_ROOT>/plugin/build/libs/BlackBoxPro-Plugin-*.jar <server.directory>/plugins/
```

> Plugin jar 被服务端占用时无法覆盖，必须先停服再部署再启服。

### B3. 服务端预启动检查（不代启动）

启动前先按“环境准备规则”检查并释放 `25565` 与 `38080`；若 `38080/status` 可访问，先调用 `stop_server`，若端口仍占用，再停止旧服务端进程，确认释放后再继续。

先检查：
```bash
curl -sf http://localhost:38080/status
```

- 若已就绪：直接进入 B4
- 若未就绪：**不要执行 `java -jar ... nogui`**。从配置读取 `server.directory`、`server.javaPath`、`server.jvmArgs`、`server.jar`，原样输出给用户，由用户在外部终端手动启动服务端

建议输出格式：
```text
请在外部终端手动启动服务端：
- 工作目录: <server.directory>
- 命令: <server.javaPath> <server.jvmArgs> -jar <server.jar> nogui
- 就绪判定 1: 日志出现 Done
- 就绪判定 2: curl -sf http://localhost:38080/status
```

> 不得使用 `run_in_background`、Terminal 技能或其他方式代替用户启动长驻服务端进程。

### B4. 客户端预启动检查（不代启动）

启动前先按“环境准备规则”检查并释放 `38081`；若旧 Mod / 客户端仍在运行，必须先停止旧实例并确认端口释放。

同方案 A2：若 `:38081` 未就绪，只能输出手动启动命令给用户，禁止代为启动。

### B5. 轮询等待双端 HTTP 就绪

仅在用户确认服务端与客户端已手动启动，或两个端口已进入启动过程时，先轮询 `:38080`（Plugin），再轮询 `:38081`（Mod），各 3s × 40 次。

### B5.5. 界面检测与初始化

同方案 A3.5，轮询就绪后立即查询界面状态：

```bash
curl -s --max-time 8 -X POST http://localhost:38081/execute \
  -H "Content-Type: application/json" \
  -d '{"id":"qs_init","action":"query_screen_state"}'
```

按 A3.5 的场景 1-4 处理（首次启动覆盖 options.txt、已在世界中则先退出），确认处于主菜单后继续 B6。

### B6. 连接服务器

**前提**：已通过 B5.5 确认当前处于主菜单。

```bash
curl -s --max-time 8 -X POST http://localhost:38081/execute \
  -H "Content-Type: application/json" \
  -d '{"id":"c1","action":"connect_to_server","params":{"ip":"127.0.0.1","port":25565}}'
```

**连接成功**：轮询服务端日志包含 `joined the game`，每 3s 检查一次，最多 20 次（60s）。

**连接失败处理**：若响应 `status != "success"` 或超时：

1. **立即查询界面状态**：
   ```bash
   curl -s --max-time 8 -X POST http://localhost:38081/execute \
     -H "Content-Type: application/json" \
     -d '{"id":"qs_fail","action":"query_screen_state"}'
   ```

2. **根据返回的 screenClass 判断**：

   | 界面类型 | 说明 | 处理 |
   |----------|------|------|
   | `DisconnectedScreen` / `ConnectScreen` | 连接被拒或认证失败 | 截图记录错误信息，报告给用户，停止流程 |
   | `TitleScreen` / `MainMenuScreen` | 连接未建立，仍在主菜单 | 检查服务端日志（online-mode / 端口），报告原因 |
   | 游戏内 HUD（open=false） | 连接实际已成功但响应异常 | 继续流程，跳过错误 |
   | 其他未知界面 | 状态不明 | 截图 + 视觉分析，向用户报告 |

3. **截图兜底**（query_screen_state 无法确认时）：
   ```bash
   curl -s --max-time 8 -X POST http://localhost:38081/execute \
     -H "Content-Type: application/json" \
     -d '{"id":"ss_fail","action":"screenshot","params":{"testId":"connect-fail","prefix":"diag"}}'
   ```
   读取截图进行视觉分析，向用户说明当前状态后再决定是否重试。

4. **不自动重试连接**：确认原因后报告用户，由用户决定是否继续。

### B7. 执行全量测试

通过 Plugin HTTP 执行（阻塞到完成，约 40-60s）：

```bash
curl -s --max-time 120 -X POST http://localhost:38080/execute \
  -H "Content-Type: application/json" \
  -d '{"id":"run-1","action":"run_test","params":{"player":"<playerName>","scope":"full"}}'
```

### B8. 清理

```bash
# 默认不主动停止用户手动启动的服务端/客户端
# 若用户明确要求收尾，可先停服务端：
curl -s --max-time 8 -X POST http://localhost:38080/execute \
  -H "Content-Type: application/json" \
  -d '{"id":"stop","action":"stop_server"}'

# 客户端仍由用户手动关闭
```

### B9. 输出报告

```
=== BlackBoxPro 服务端联调报告 ===
版本: <resolvedVersion>
模式: B（服务端联调）
玩家: <playerName>

测试结果:
  passed: <passed>
  failed: <failed>
  skipped: <skipped>

通过标准: failed == 0
判定: PASS
```

---

## 结果判定标准

| 指标 | PASS 条件 |
|------|----------|
| 方案 A | 所有执行的 action 响应 `status: "success"` |
| 方案 B | `run_test` 返回 `failed == 0`（**前提：只当作框架链路体检，见下方警告**） |

> ⚠️ **`run_test` 的 `passed/failed` 描述的是"框架 action 在本端能否真正生效"，不等于"用户的插件通过了测试"。**
>
> - `run_test` 的用例表在 `BlackBoxTestCatalog` 里**硬编码为 `ActionCatalog` 自测**（`FULL` = `ActionCatalog.getDefinitions().map(::buildCase)`），既不是可扩展的插件测试套件，也不覆盖 `ActionCatalog` 未收录的 action（例如 `screenshot_tooltip`）。
> - 基线自带环境性失败（实测 `jump` → `Player is not on ground`），所以 `failed == 0` 在本机不是稳定可达的门槛；把 `failed` 当插件缺陷会误判。
> - 验用户自己的插件必须由宿主 Agent **自编排一串 `/execute`**：`chat_command` 开界面 → `query_container_slots` / `screenshot_tooltip` / `query_chat_style` 断言 → `close_container`。`run_test` 只当链路体检。
>
> 「环境未就绪 ≠ 被测程序失败」判别表见 `reference/http-api.md`。

---

## 故障排查

| 症状 | 原因 | 解决方案 |
|------|------|---------|
| `:38081` 轮询超时 | Mod 未加载或崩溃 | 要求用户检查外部终端中的客户端日志，确认 Mod 是否成功加载 |
| `:38080` 轮询超时 | Plugin 未启用 | 要求用户检查外部终端中的服务端日志，确认 Plugin 是否成功加载 |
| `Already in a world` | 未先 `leave_world` | 先执行 `leave_world`，确认回到主菜单后再继续 |
| `38081` 仍占用但 `/status` 不通 | 旧 Gradle / Minecraft 进程未真正退出 | 按“旧实例清理状态机”确认进程结束、终端回到 prompt、端口释放后再继续 |
| 多次 `Ctrl-C` 后仍卡住 | 终端停在确认提示或 Gradle 未退出 | 先读取 buffer 确认提示内容，再处理确认输入；不要盲目重复中断 |
| `World already exists` | 同名世界 | 自动 fallback 到 `join_world` |
| `World not found` | 世界不存在 | 使用 `create_world` |
| 截图全黑 | 窗口遮挡或最小化 | 确保客户端窗口可见 |
| `Connection refused` 连接服务器 | 服务端未启动或 online-mode | 检查服务端状态，确认 online-mode=false |

## 端口速查

| 组件 | 端口 | 用途 |
|------|------|------|
| Plugin HTTP | 38080 | 测试脚本指令入口（方案 B） |
| Mod HTTP | 38081 | Mod 直达（方案 A）/ Plugin 转发目标（方案 B） |
| Minecraft Server | 25565 | 游戏连接 |

## 注意事项

- 两个 MC 版本不要同时启动（都占用 25565 和 38081）
- 每次启动前必须检查并释放 `25565` / `38080` / `38081` 的旧实例占用，确认端口空闲后再继续
- 修改 Plugin 后必须停服 → 部署 → 重启
- 修改 Mod 后需重启客户端
- 所有控制通过 HTTP，禁止使用 RCON
