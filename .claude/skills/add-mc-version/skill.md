---
name: add-mc-version
description: 为 BlackBoxPro 新增一个 Minecraft 版本的测试支持。自动完成 mod 代码生成、环境搭建、构建验证，并在用户已手动启动的环境上执行测试与验收；禁止由 AI 代启动客户端或服务端。验收标准：全量测试 failed=0 且 passed ≥ 参考版本；启动前必须检查并释放 25565/38080/38081 的对应旧实例端口占用。
---

# BlackBoxPro 新版本接入技能

## 用法

```
/add-mc-version <mc_version> [loader=fabric|neoforge|both] [ref_version=1.21.11]
```

示例：
- `/add-mc-version 1.21.4` → 接入 1.21.4 Fabric（默认）
- `/add-mc-version 1.21.4 loader=both ref_version=1.21.1` → 以 1.21.1 为参考模板，同时接入 Fabric + NeoForge

### 参数语义（必须区分）

- `mc_version`：**要新增的目标版本**。
- `ref_version`：**复制模板与验收基准版本**，默认可用 `1.21.11`，但它不是当前目标版本；若仓库实际更适合从 `1.21.1` 演进，必须显式传 `ref_version=1.21.1`。
- `loader`：目标接入的 loader；若为 `both`，代码生成与 settings 注册都要同时覆盖 Fabric + NeoForge。

---

## 前置：定位 BBP 仓库（BBP_ROOT）

本技能可以在“接入 BBP 的项目目录”中运行，配置文件应放在当前项目的 `.claude/config/blackboxpro-env.json`（而不是写死在 BBP 仓库里）。

需要先得到 `BBP_ROOT`（BlackBoxPro 仓库根目录），两种方式任选其一：

- **本机已有 BBP**：在配置中填写 `bbp.method=local` + `bbp.localPath`（指向 BlackBoxPro 根目录）
- **自动克隆 BBP**：在配置中填写 `bbp.method=git` + `bbp.git.url` + `bbp.git.cloneDir`（相对当前项目根目录）。若目录不存在则先 `git clone`，再继续流程

校验：`$BBP_ROOT/gradlew` 或 `$BBP_ROOT/gradlew.bat` 必须存在。

示例脚本（git 模式，在“接入项目根目录”执行）：
```bash
CLONE_DIR="<bbp.git.cloneDir>"
[ -d "$CLONE_DIR/.git" ] || git clone "<bbp.git.url>" "$CLONE_DIR"
[ -z "<bbp.git.ref>" ] || (cd "$CLONE_DIR" && git checkout "<bbp.git.ref>")
BBP_ROOT="$CLONE_DIR"
```

---

## 验收标准

**通过条件（必须同时满足）：**
1. `failed == 0`（无失败用例）
2. `passed >= ref_passed - 2`（通过数不低于参考版本，允许 ±2 容差）
3. 服务端正常启动（日志出现 `Done`）
4. 客户端 mod 正常加载（日志出现 `All actions registered`）
5. 连接服务器成功（服务端日志出现 `joined the game`）

**不通过时的处理原则：**
- 编译错误 → 分析 API 差异，修复代码后重试（最多 3 次）
- 测试失败 → 判断是版本不支持（加豁免列表）还是 bug（修复重试）
- 环境问题 → 报告具体错误，给出操作指引

---

## 非阻塞规则

**禁止由 AI 调起任何长驻/阻塞型进程。** 包括但不限于：
- `./gradlew :{mc_version}:<loader>:runClient`（工作目录为 `${BBP_ROOT}/mod`）
- 各类 Minecraft 启动器 / PCL / 外部 launcher
- `java -jar <server.jar> nogui`
- 任何会持续占用终端并让后续步骤长期等待的命令

执行边界：
- **允许**：代码生成、构建、下载服务端、部署插件、编辑配置、有限时 HTTP 调用、短时轮询就绪状态
- **禁止**：使用 Bash background、Terminal 技能或其他方式代替用户启动客户端/服务端
- 若环境未就绪：必须输出精确的手动启动命令、工作目录、`JAVA_HOME`/`javaPath`、就绪判定，然后暂停自动流程，等待用户在外部终端完成启动

## 执行流程

### 阶段 0：前置检查

1. 检查 `38080` 是否已有旧 Plugin HTTP：
   - 若 `curl -sf http://localhost:38080/status` 成功，先调用 `stop_server` 优雅停服
   - 再确认 `38080` 已释放；未释放则继续停止残留服务端进程
2. 检查 `25565` 是否被旧服务端占用：
   - 若占用，停止对应服务端进程 / 终端
   - 确认 `25565` 已释放后再继续
3. 检查 `38081` 是否被旧客户端 / Mod HTTP 占用：
   - 若 `curl -sf http://localhost:38081/status` 成功，说明旧客户端测试实例仍在运行，必须先停止
   - 若仅端口占用但 HTTP 不通，也要停止残留客户端进程 / 终端
   - 确认 `38081` 已释放后再继续
4. 确认 `mod/{mc_version}` 是否已存在（存在则跳过代码生成）

#### 旧实例清理状态机（统一流程）

1. **识别归属**
   - 先判定旧实例是：HTTP 可达的 BBP 实例、当前会话自己创建的后台任务/terminal，还是用户手动启动的外部进程。
   - 只允许接管当前会话明确创建的 task / terminal；**禁止**向未知用户终端盲发 `Ctrl-C`、`y` 或其他输入。
2. **优雅停止**
   - 服务端：若 `38080/status` 可访问，先走 `stop_server`。
   - 客户端：若 `38081/status` 可访问，只允许做会话级清理与确认，不代替用户关闭整个客户端。
   - 若存在当前会话自己创建的后台任务 / terminal，先发一次中断，再立即读取输出；若出现确认提示，先处理提示再继续，不要连续盲发多次 `Ctrl-C`。
3. **退出确认**
   - 每次停止动作后都必须重新确认：`25565` / `38080` / `38081` 已释放，HTTP `/status` 已不可访问，owned task / terminal 已结束或回到 prompt。
4. **升级处理**
   - 若 HTTP 已不可达但端口仍被占用，视为残留 Java / Gradle / Minecraft 进程。
   - 若不是当前会话 owned task / terminal，停止自动流程，明确要求用户关闭对应外部终端/启动器。
5. **重启闸门**
   - 只有在端口确认释放后，才允许部署新产物、提示用户重启，或进入下一阶段。

> 规则：后续每次启动服务端或客户端前，若发现对应端口仍被旧实例占用，必须先停掉旧实例，禁止直接叠加启动。

---

### 阶段 1：代码生成（mod 侧）

**1.1 查询版本号**

```bash
MC_VER="{mc_version}"

# Yarn mappings
YARN=$(curl -s "https://meta.fabricmc.net/v2/versions/yarn/${MC_VER}" | python3 -c "
import json,sys; d=json.load(sys.stdin); print(d[0]['version'] if d else 'NOT_FOUND')")

# Fabric loader
LOADER=$(curl -s "https://meta.fabricmc.net/v2/versions/loader/${MC_VER}" | python3 -c "
import json,sys; d=json.load(sys.stdin); print(d[0]['loader']['version'] if d else 'NOT_FOUND')")

# Fabric API
FABRIC_API=$(curl -s "https://api.modrinth.com/v2/project/fabric-api/version?game_versions=%5B%22${MC_VER}%22%5D&loaders=%5B%22fabric%22%5D" | python3 -c "
import json,sys; d=json.load(sys.stdin); print(d[0]['version_number'] if d else 'NOT_FOUND')")

echo "yarn=$YARN loader=$LOADER fabric_api=$FABRIC_API"
```

**1.2 复制目录并替换版本号**

```bash
cd "${BBP_ROOT}/mod"
cp -r {ref_version} {mc_version}

# 修改 gradle.properties
# 替换字段：minecraft_version, yarn_mappings, loader_version, fabric_version, neoforge_version
```

修改 `mod/{mc_version}/gradle.properties`（关键字段）：
```properties
minecraft_version={mc_version}
yarn_mappings={YARN}
loader_version={LOADER}
fabric_version={FABRIC_API}+{mc_version}
```

**1.3 更新 mod 元数据**

- `fabric/src/main/resources/fabric.mod.json`：更新 `minecraft` 依赖版本范围为 `>={mc_version}`
- `neoforge/src/main/resources/META-INF/mods.toml`：更新版本范围

**1.4 注册到 mod/settings.gradle.kts**

按 loader 动态追加：
```kotlin
include("{mc_version}:runtime")
include("{mc_version}:fabric")      // loader=fabric|both 时需要
include("{mc_version}:neoforge")    // loader=neoforge|both 时需要

project(":{mc_version}").projectDir = file("{mc_version}")
project(":{mc_version}:runtime").projectDir = file("{mc_version}/runtime")
project(":{mc_version}:fabric").projectDir = file("{mc_version}/fabric")      // 按需添加
project(":{mc_version}:neoforge").projectDir = file("{mc_version}/neoforge")  // 按需添加
```

---

### 阶段 2：环境搭建

**2.1 下载 Paper 服务端**

```bash
MC_VER="{mc_version}"
# 由用户配置/输入一个服务端根目录（不要写死在技能里）
SERVER_BASE_DIR="{SERVER_BASE_DIR}"
SERVER_DIR="${SERVER_BASE_DIR}/paper-${MC_VER}"

BUILD=$(curl -s "https://api.papermc.io/v2/projects/paper/versions/${MC_VER}/builds" | \
  python3 -c "import json,sys; d=json.load(sys.stdin); print(d['builds'][-1]['build'] if d.get('builds') else 'NOT_FOUND')")

JAR=$(curl -s "https://api.papermc.io/v2/projects/paper/versions/${MC_VER}/builds/${BUILD}" | \
  python3 -c "import json,sys; d=json.load(sys.stdin); print(d['downloads']['application']['name'])")

powershell -NoProfile -Command "New-Item -ItemType Directory -Force '${SERVER_DIR}' | Out-Null"
curl -o "${SERVER_DIR}/${JAR}" "https://api.papermc.io/v2/projects/paper/versions/${MC_VER}/builds/${BUILD}/downloads/${JAR}"
echo "eula=true" > "${SERVER_DIR}/eula.txt"
```

**2.2 首次启动生成配置（用户手动执行），然后关闭在线验证**

若 `server.properties` 尚不存在，**AI 不得执行首次启动命令**。必须先输出以下信息给用户，由用户在外部终端手动完成首次启动并在看到 `Done` 后自行关闭：

```text
请在外部终端完成服务端首次启动：
- 工作目录: ${SERVER_DIR}
- 命令: "/c/Program Files/Java/jdk-21/bin/java.exe" -Xms2G -Xmx2G -jar {jar} nogui
- 完成标志: 生成 server.properties 且日志出现 Done
```

用户完成后，再继续关闭在线验证：

```bash
powershell -NoProfile -Command "
(Get-Content '${SERVER_DIR}/server.properties') -replace 'online-mode=true','online-mode=false' | Set-Content '${SERVER_DIR}/server.properties'"
powershell -NoProfile -Command "
(Get-Content '${SERVER_DIR}/server.properties') -replace 'enforce-secure-profile=true','enforce-secure-profile=false' | Set-Content '${SERVER_DIR}/server.properties'"
# enforce-secure-profile: 1.19.1+ 新增，开发账号必须关闭，否则离线玩家被拒连接
```

**2.3 部署 plugin**

```bash
powershell -NoProfile -Command "
Copy-Item "${BBP_ROOT}\\plugin\\build\\libs\\BlackBoxPro-Plugin-*.jar" "${SERVER_DIR}\\plugins\\" -Force"
```

---

### 阶段 3：构建验证

```bash
cd "${BBP_ROOT}/mod"
JAVA_HOME="/c/Program Files/Java/jdk-21" ./gradlew :{mc_version}:{selected_loader}:build --no-daemon 2>&1
```

> `selected_loader` 必须来自用户传入的 `loader`：
> - `fabric` → `fabric`
> - `neoforge` → `neoforge`
> - `both` → 分别执行 Fabric 与 NeoForge 两次构建验证

**编译错误处理规则：**

| 错误特征 | 处理方式 |
|---|---|
| `Unresolved reference: XxxScreen/XxxPacket` | 查 Yarn 新映射，更新 import 或类名 |
| `None of the following candidates` | 方法签名变化，查新版源码调整参数 |
| `type mismatch: actual 'T?' expected 'T'` | nullable 变化，加 `?: fallback` |
| `Could not resolve…fabric-api` | 更新 fabric_version 为正确版本 |
| daemon 内存/锁文件错误 | `./gradlew --stop` 后重试 |

最多修复重试 3 次，否则输出报告等待人工介入。

---

### 阶段 4：测试执行

**Step 1：确认服务端已手动启动**

启动前再次确认 `25565` / `38080` 未被旧实例占用；若占用，重复阶段 0 的停服清理。

若 `curl -sf http://localhost:38080/status` 失败，**不要执行服务端启动命令**。必须输出以下信息给用户，由用户在外部终端手动启动：

```text
请在外部终端启动服务端：
- 工作目录: ${SERVER_DIR}
- 命令: "/c/Program Files/Java/jdk-21/bin/java.exe" -Xms2G -Xmx4G -XX:+UseG1GC -jar {jar} nogui
- 就绪判定 1: 日志出现 Done
- 就绪判定 2: curl -sf http://localhost:38080/status
```

**Step 2：确认客户端已手动启动**

启动前再次确认 `38081` 未被旧实例占用；若占用，重复阶段 0 的停客户端清理。

若 `curl -sf http://localhost:38081/status` 失败，**不要执行客户端启动命令**。必须输出以下信息给用户，由用户在外部终端手动启动：

```text
请在外部终端启动客户端：
- 工作目录: ${BBP_ROOT}/mod
- JAVA_HOME: /c/Program Files/Java/jdk-21
- 命令: ./gradlew :{mc_version}:{selected_loader}:runClient --no-daemon
- 就绪判定: curl -sf http://localhost:38081/status
```

> `selected_loader` 同构建阶段；若 `loader=both`，先选择一个 loader 完成联调验收，再对另一侧重复执行一轮。

待 `:38081` 就绪后，记录日志中 `Setting user: PlayerXXX` 的玩家名。

**Step 3：连接服务器**
```bash
curl -s --max-time 35 -X POST http://localhost:38081/execute \
  -H "Content-Type: application/json" \
  -d '{"id":"c1","action":"connect_to_server","params":{"ip":"127.0.0.1","port":25565}}'
```
等待服务端日志出现 `joined the game`。
若出现 `DisconnectedScreen`，检查 online-mode 并重连。

**Step 4：运行全量测试**
```bash
curl -s -X POST http://localhost:38080/execute \
  -H "Content-Type: application/json" \
  -d "{\"id\":\"run\",\"action\":\"run_test\",\"params\":{\"player\":\"{PLAYER}\",\"scope\":\"full\"}}" \
  -o result_{mc_version}.json
```
阻塞等待完成（约 40-60s）。

**Step 5：收尾**
```bash
# 默认不主动关闭用户手动启动的服务端/客户端
# 若用户明确要求回收环境，可停服务端：
curl -s -X POST http://localhost:38080/execute -d '{"id":"stop","action":"stop_server"}'
# 客户端仍由用户手动关闭
```

---

### 阶段 5：验收判断

```python
import json

r = json.load(open(f"result_{mc_version}.json", encoding="utf-8"))["data"]
passed, failed, skipped = r["passed"], r["failed"], r["skipped"]
ref_passed = <ref_passed>  # 来自 ref_version={ref_version} 的历史基准，不要写死到脚本里

print(f"结果：{passed}/{failed}/{skipped}")

if failed == 0 and passed >= ref_passed - 2:
    print("✅ 验收通过！")
else:
    print("❌ 验收未通过，失败分析：")
    for c in r["results"]:
        if c["status"] == "failed":
            msg = c.get("message", "")
            a = c["action"]
            if "Not connected" in msg or "Player not available" in msg:
                print(f"  [断线] {a} → 参考已有断线处理（add to skip list）")
            elif "Invalid params" in msg or "Missing required field" in msg:
                print(f"  [参数] {a}: {msg} → 在 BlackBoxTestCatalog 增加版本分支")
            elif "不支持" in msg or "not supported" in msg.lower():
                print(f"  [版本] {a} → 加入 unsupportedOn 豁免列表")
            else:
                print(f"  [未知] {a}: {msg}")
```

**自动修复策略：**
1. 参数格式变化 → 在 `BlackBoxTestCatalog` execute lambda 的 `when(actionId)` 中，参照 1.12.2 分支增加新版本分支
2. 版本不支持的 action → 在 `BlackBoxTestProfile.kt` 增加 `unsupportedOnXxx` 集合，在 `buildCase` 的 `prepare` 中判断
3. 断线问题 → 加入 prepareFixture 的"待排查"跳过分支
4. 修复后重新构建测试，回到阶段 3 循环（最多 3 次）

---

### 阶段 6：收尾

**更新 blackbox-runner/SKILL.md 版本速查表**

在版本环境速查章节添加：
```markdown
### {mc_version}

| 项目 | 值 |
|------|-----|
| Java（Gradle/运行时） | `C:\Program Files\Java\jdk-21` |
| 服务端目录 | `{SERVER_BASE_DIR}/paper-{mc_version}` |
| 服务端 JAR | `{jar_name}` |
| 客户端手动启动 | `cd mod && JAVA_HOME="C:/Program Files/Java/jdk-21" ./gradlew :{mc_version}:{selected_loader}:runClient`（仅展示给用户，不由 AI 执行） |
| 玩家名 | `{player_name}`（runClient 开发模式随机） |
| 最新测试结果 | `{passed}/{failed}/{skipped}` |
```

**更新本 skill 的版本接入记录表（末尾）**

**Git commit**
```bash
git add mod/{mc_version}/ .claude/skills/ plugin/
git commit -m "feat: 新增 {mc_version} Fabric 测试支持，通过率 {passed}/{total}"
git push
```

---

## 常见问题

### Q: Paper 没有该版本

检查：`curl -s "https://api.papermc.io/v2/projects/paper" | python3 -c "import json,sys; print(json.load(sys.stdin)['versions'])"`

若无 Paper 则考虑用 Folia 或等待 Paper 支持。

### Q: Fabric API 找不到（返回 NOT_FOUND）

该版本 Fabric API 可能尚未发布或版本号格式不同，手动去 https://modrinth.com/mod/fabric-api/versions 查找。

### Q: NeoForge 版本号格式

- 1.21.11 → `21.11.x`
- 1.21.1 → `21.1.x`
- 规律：`{MC_MAJOR}.{MC_MINOR}.x`

### Q: 玩家名动态获取

连接后查 tab 列表：
curl -X POST http://localhost:38081/execute -H Content-Type:application/json -d action=query_tab_list

---

## 版本接入记录

| MC 版本 | 加载器 | 通过/失败/跳过 | 接入日期 | 备注 |
|---|---|---|---|---|
| 1.12.2 | Forge | 52/0/54 | 2026-03-22 | 基线版本 |
| 1.21.11 | Fabric | 54/0/52 | 2026-03-22 | 参考基准示例，可被 `ref_version` 覆盖 |
| 1.21.4 | Fabric | 56/2/57 | 2026-10-16 | 净增失败数=0：两个失败项 `click_chat_text`、`query_chat_style` 与 1.21.11 基线同名同因（测试目录缺 `match` 参数），属已知测试目录缺陷，非版本适配问题 |

> 上表每行是**其接入日期当时测试目录规模**的快照，因此各行的通过/失败/跳过之和并不一致：前两行合计 106 项，为当时目录规模；截至 2026-10-16（1.21.4 接入）目录为 115 项。新增版本验收时应以与参考版本**同期同目录**的重跑结果对标，不要直接与旧行数字相减。

（本技能不应包含任何本机绝对路径；请通过 bbp.localPath 或 bbp.git.cloneDir 指定 BBP_ROOT）
