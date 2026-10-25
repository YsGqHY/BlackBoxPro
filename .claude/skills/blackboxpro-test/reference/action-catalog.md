# BlackBoxPro Action 完整目录

按功能分类，覆盖**各客户端 `ActionRegistry` 真正注册过的全部 Action**。

> **本文不写死任何计数。** 计数必然随分支漂移（本文历史版本曾在正文写死过一个总数，而那个数字在当时就已经过期），因此所有数字一律由源码动态复算，不落在正文里。
>
> **唯一真相来源（按优先级）**
> 1. 各客户端注册表 `mod/<MC版本>/<加载器>/src/main/kotlin/**/dispatcher/ActionRegistry.kt` —— 决定**该端能否真正执行**这个 Action。
> 2. `common/src/main/kotlin/com/blackboxpro/common/action/ActionCatalog.kt` —— 共享契约，**只声明 `id` + 参数名**，没有类型 / 必填 / 默认值。
> 3. 各 Action 实现类 `.../action/**/<Name>Action.kt` —— 类型、必填、默认值与运行时行为的唯一依据。
>
> **复算与校验（无需构建、无需网络）**
> ```powershell
> python .claude/skills/validate_blackboxpro_skills.py   # 退出码 0 = 本文与源码一致
> ```
> `.github/workflows/validate-skills.yml` 在每次 push / PR 上跑同一条命令；改动注册表后不同步本文，CI 直接红。
>
> 只想看"当前客户端实际有多少个 Action"：读 mod 的 `POST /status` 的 `actions` 字段（它返回的是运行时注册表大小），不要相信任何写死的数字。

## 版本支持说明

| 标记 | 含义（判定依据 = 客户端注册表：`1.12.2/forge`、`1.21.1/fabric`、`1.21.1/neoforge`、`1.21.11/fabric`、`1.21.11/neoforge`） |
|------|------|
| ✅ | 上表全部注册表都注册 |
| 🔶 | 1.12.2 **无**；1.21.1 与 1.21.11 都有 |
| 🟠 | 1.21.1 **无**；1.12.2 与 1.21.11 都有 |
| ⚠️ | 客户端已注册，但 `ActionCatalog.kt` **未收录** ⇒ 参数表只能取自实现类，且**不会被 `run_test scope=full` 覆盖** |

- 参数标记：**粗体** = 必填，普通 = 可选

### 只在部分端可用的 Action（非 🔶 的那些）

| Action ID | 可用 | 不可用 |
|-----------|------|--------|
| `left_click` | 1.12.2 forge、1.21.11 fabric + neoforge | 1.21.1 fabric + neoforge |
| `query_tooltip_state` | 1.12.2 forge、1.21.11 fabric + neoforge | 1.21.1 fabric + neoforge |
| `screenshot_tooltip` | 所有客户端注册表（⚠️ 未进 `ActionCatalog.kt`） | — |

> 上面三张表由 `validate_blackboxpro_skills.py` 与注册表 diff 逐项校验（见文末 `bbp-action-matrix` 块）。

---

## 1. 移动与位置 ✅

| Action ID | 参数 | 类型 | 默认值 | 说明 |
|-----------|------|------|--------|------|
| `player_move` | **x**, **y**, **z** | Double | — | 目标坐标 |
| | speed | Double | 1.0 | 移动速度（0.1~2.0，>1.0 冲刺） |
| | timeout | Int | 200 | 超时 tick 数 |
| `player_move_look` | **x**, **y**, **z** | Double | — | 目标坐标 |
| | **pitch** | Double | — | 俯仰角（-90~90） |
| | speed | Double | 1.0 | 移动速度（0.1~2.0） |
| | timeout | Int | 200 | 超时 tick 数 |
| `player_look` | **yaw** | Double | — | 水平旋转角 |
| | **pitch** | Double | — | 俯仰角（-90~90） |
| | onGround | Boolean | true | 是否在地面 |
| `player_on_ground` | **onGround** | Boolean | — | 是否在地面 |
| `confirm_teleportation` | **teleportId** | Int | — | 传送确认 ID |
| `move_vehicle` | **x**, **y**, **z** | Double | — | 载具坐标 |
| | **yaw**, **pitch** | Double | — | 载具旋转角 |
| | onGround | Boolean | true | 是否在地面 |
| `paddle_boat` | **leftPaddling** | Boolean | — | 左桨划动 |
| | **rightPaddling** | Boolean | — | 右桨划动 |
| `player_input` | forward, backward, left, right | Boolean | false | 方向输入 |
| | jump, sneak, sprint | Boolean | false | 动作输入（1.12.2 无 sprint） |

## 2. 方块交互 ✅

| Action ID | 参数 | 类型 | 默认值 | 说明 |
|-----------|------|------|--------|------|
| `dig_start` | **x**, **y**, **z** | Int | — | 方块坐标 |
| | **face** | String | — | 方块面（up/down/north/south/east/west） |
| | sequence | Int | 0 | 序列号（1.12.2 忽略） |
| `dig_cancel` | （同 dig_start） | | | |
| `dig_finish` | （同 dig_start） | | | |
| `place_block` | **x**, **y**, **z** | Int | — | 方块坐标 |
| | **face** | String | — | 放置面 |
| | hand | String | "main_hand" | 主手/副手 |
| | cursorX, cursorY, cursorZ | Double | 0.5 | 光标偏移（0~1） |
| | insideBlock | Boolean | false | 是否在方块内部 |
| | sequence | Int | 0 | 序列号（1.12.2 忽略） |
| `use_item` | hand | String | "main_hand" | 主手/副手（模拟右键使用物品） |

## 3. 实体交互 ✅

> 版本：`left_click` 为 🟠（1.21.1 fabric + neoforge 未注册）；其余 ✅。

| Action ID | 参数 | 类型 | 默认值 | 说明 |
|-----------|------|------|--------|------|
| `attack_entity` | **entityId** | Int | — | 目标实体 ID |
| | sneaking | Boolean | false | 是否潜行（1.12.2 忽略） |
| `interact_entity` | **entityId** | Int | — | 目标实体 ID |
| | hand | String | "main_hand" | 主手/副手 |
| | sneaking | Boolean | false | 是否潜行（1.12.2 忽略） |
| `interact_entity_at` | **entityId** | Int | — | 目标实体 ID |
| | **targetX**, **targetY**, **targetZ** | Double | — | 交互点（实体局部坐标） |
| | hand | String | "main_hand" | 主手/副手 |
| | sneaking | Boolean | false | 是否潜行（1.12.2 忽略） |
| `swing_arm` | hand | String | "main_hand" | 挥动手臂 |
| `left_click` | hand | String | "main_hand" | 模拟左键点击（发送 START+ABORT_DESTROY_BLOCK + swing） |

## 4. 容器/GUI

| Action ID | 版本 | 参数 | 类型 | 默认值 | 说明 |
|-----------|------|------|------|--------|------|
| `click_slot` | ✅ | **windowId** | Int | — | 容器窗口 ID |
| | | **stateId** | Int | — | 容器状态 ID（1.12.2 自动获取事务 ID，此参数忽略） |
| | | **slot** | Int | — | 槽位索引 |
| | | **button** | Int | — | 鼠标按钮（0 左/1 右） |
| | | **mode** | Int | — | 点击模式：0=PICKUP, 1=QUICK_MOVE, 2=SWAP, 3=CLONE, 4=THROW, 5=QUICK_CRAFT, 6=PICKUP_ALL |
| `click_button` | ✅ | **windowId**, **buttonId** | Int | — | 容器按钮点击 |
| `close_container` | ✅ | windowId | Int | 当前容器 | 关闭容器（同时重置客户端容器状态） |
| `set_carried_item` | ✅ | **slot** | Int | — | 切换快捷栏（0~8），同步客户端本地状态 |
| `creative_set_slot` | ✅ | **slot** | Int | — | 创造模式设置槽位（当前仅支持清空） |
| `pick_item` | ✅ | **x**, **y**, **z** | Int | — | 拾取方块物品 |
| | | includeData | Boolean | false | 是否包含 NBT |
| `pick_entity` | 🔶 | **entityId** | Int | — | 拾取实体物品 |
| | | includeData | Boolean | false | 是否包含 NBT |
| `pick_item_from_block` | 🔶 | **x**, **y**, **z** | Int | — | 从方块拾取物品 |
| | | includeData | Boolean | false | |
| `pick_item_from_entity` | 🔶 | **entityId** | Int | — | 从实体拾取物品 |
| | | includeData | Boolean | false | |
| `bundle_selected_slot` | 🔶 | **slotId**, **selectedIndex** | Int | — | 收纳袋槽位选择 |
| `slot_state_change` | 🔶 | **windowId**, **slotId** | Int | — | Crafter 槽位状态变更 |
| | | **state** | Boolean | — | 新状态 |
| `hover_slot` | ✅ | **windowId**, **slot** | Int | — | 模拟鼠标悬停槽位 |
| | | durationTicks | Int | 0 | 悬停持续 tick |

## 5. 玩家状态 ✅

| Action ID | 参数 | 类型 | 默认值 | 说明 |
|-----------|------|------|--------|------|
| `sneak_start` | （无） | | | 开始潜行（1.21.11 设置本地 input 状态，1.12.2 发送 EntityAction 包） |
| `sneak_stop` | （无） | | | 停止潜行 |
| `sprint_start` | （无） | | | 开始冲刺（同步 player.isSprinting + 发包） |
| `sprint_stop` | （无） | | | 停止冲刺 |
| `jump` | （无） | | | 跳跃（需在地面，通过物理引擎处理） |
| `drop_item` | sequence | Int | 0 | 丢弃当前手持物品 1 个 |
| `drop_item_stack` | sequence | Int | 0 | 丢弃当前手持物品整组 |
| `finish_using` | sequence | Int | 0 | 释放正在使用的物品（如弓箭、盾牌） |
| `swap_hands` | sequence | Int | 0 | 交换主副手物品 |
| `perform_respawn` | （无） | | | 发送重生包 |
| `spectator_teleport` | **targetUuid** | String | — | 旁观者传送到目标玩家 |
| `leave_bed` | entityId | Int | 当前玩家 | 离开床 |
| `horse_jump_start` | jumpBoost | Int | 100 | 马匹跳跃开始 |
| | entityId | Int | 当前玩家 | 实体 ID |
| `horse_jump_stop` | entityId | Int | 当前玩家 | 马匹跳跃停止 |
| `open_horse_inventory` | entityId | Int | 当前玩家 | 打开马匹背包 |
| `elytra_start` | entityId | Int | 当前玩家 | 开始鞘翅飞行 |

## 6. 聊天命令 ✅

| Action ID | 参数 | 类型 | 默认值 | 说明 |
|-----------|------|------|--------|------|
| `chat_message` | **message** | String | — | 发送聊天消息（≤256 字符，1.19.1+ 自动处理签名） |
| `chat_command` | **command** | String | — | 执行命令（自动去除 `/` 前缀，≤32767 字符） |
| `click_chat_text` | **match** | String | — | 要匹配的聊天文本 |
| | index | Int | 0 | 聊天历史索引（0=最新） |
| | execute | Boolean | true | 是否执行 ClickEvent |

## 7. 客户端设置 ✅

| Action ID | 参数 | 类型 | 默认值 | 说明 |
|-----------|------|------|--------|------|
| `client_information` | locale | String | "en_us" | 语言 |
| | viewDistance | Int | 12 | 视距 |
| | chatMode | Int | 0 | 聊天模式（0 全部/1 系统/2 隐藏） |
| | chatColors | Boolean | true | 聊天颜色 |
| | skinParts | Int | 127 | 皮肤部件位掩码 |
| | mainHand | Int | 1 | 主手（0 左/1 右） |
| | textFiltering | Boolean | false | 文本过滤（1.12.2 忽略） |
| | allowServerListings | Boolean | true | 允许服务器列表（1.12.2 忽略） |
| `player_abilities` | **flying** | Boolean | — | 切换飞行状态（需 mayfly 权限） |
| `resource_pack_response` | **uuid** | String | — | 资源包 UUID（1.12.2 忽略） |
| | **result** | String | — | 状态：accepted/declined/successfully_loaded/failed_download/failed_reload/discarded/invalid_url/downloaded |
| `screenshot` | testId | String | "default" | 测试 ID（子目录名） |
| | prefix | String | null | 文件名前缀 |
| | playerName | String | 当前玩家名 | 玩家名（目录名） |
| `screenshot_tooltip` ⚠️ | **windowId** | Int | — | 容器窗口 ID（需已打开容器界面） |
| | **slot** | Int | — | 槽位索引（空槽 ⇒ `failure: Slot N is empty, no tooltip to render`） |
| | testId | String | "default" | 测试 ID（子目录名） |
| | prefix | String | "tooltip" | 文件名前缀 |
| `connect_to_server` | **ip** | String | — | 服务器 IP |
| | port | Int | 25565 | 服务器端口 |
| `close_screen` | （无） | | | 关闭当前屏幕 |
| `open_inventory` | （无） | | | 打开玩家自己的背包界面（等价于按 E）；不依赖世界里任何方块/实体，是验自家插件 GUI 时最稳的容器入口 |
| `create_world` | **worldName** | String | — | 世界名称 |
| | gameMode | String | "survival" | 游戏模式（survival/creative/hardcore） |
| | difficulty | String | "normal" | 难度（peaceful/easy/normal/hard） |
| | allowCommands | Boolean | creative 时 true | 允许作弊 |
| | generateStructures | Boolean | true | 生成结构 |
| | bonusChest | Boolean | false | 奖励箱 |
| | seed | String | 随机 | 世界种子 |
| `join_world` | **worldName** | String | — | 世界名称（匹配 levelId 或 displayName） |
| `leave_world` | （无） | | | 返回标题画面（异步等待） |

## 8. 进阶交互

| Action ID | 版本 | 参数 | 类型 | 默认值 | 说明 |
|-----------|------|------|------|--------|------|
| `edit_book` | ✅ | **slot** | Int | — | 书本槽位 |
| | | **pages** | JsonArray | — | 页面内容（≤200 页，每页 ≤32767 字符） |
| | | title | String | null | 书名（传入则同时签名） |
| `sign_book` | ✅ | **slot** | Int | — | 书本槽位 |
| | | **title** | String | — | 书名（≤128 字符） |
| | | **pages** | JsonArray | — | 页面内容 |
| `update_sign` | ✅ | **x**, **y**, **z** | Int | — | 告示牌坐标 |
| | | **lines** | JsonArray | — | 4 行文本（不足补空） |
| | | isFrontText | Boolean | true | 正面文本（1.12.2 忽略） |
| `update_command_block` | ✅ | **x**, **y**, **z** | Int | — | 命令方块坐标 |
| | | **command** | String | — | 命令内容 |
| | | mode | Int | 2 | 模式（0 序列/1 自动/2 红石） |
| | | trackOutput | Boolean | true | 追踪输出 |
| | | conditional | Boolean | false | 条件模式 |
| | | alwaysActive | Boolean | false | 始终活跃 |
| `update_command_block_minecart` | ✅ | **entityId** | Int | — | 矿车实体 ID |
| | | **command** | String | — | 命令内容 |
| | | trackOutput | Boolean | true | 追踪输出 |
| `update_structure_block` | ✅ | **x**, **y**, **z** | Int | — | 结构方块坐标 |
| | | **action** | Int | — | 操作（0 更新/1 保存/2 加载/3 扫描） |
| | | **mode** | String | — | 模式（save/load/corner/data） |
| | | **name** | String | — | 结构名称 |
| | | offsetX/Y/Z | Int | 0 | 偏移 |
| | | sizeX/Y/Z | Int | 0 | 尺寸 |
| | | mirror | String | "none" | 镜像（none/left_right/front_back） |
| | | rotation | String | "none" | 旋转（none/clockwise_90/clockwise_180/counterclockwise_90） |
| | | metadata | String | "" | 元数据 |
| | | integrity | Float | 1.0 | 完整度（0~1） |
| | | seed | Long | 0 | 随机种子 |
| | | flags | Int | 0 | 标志位（0x01 忽略实体/0x02 显示空气/0x04 显示边界/0x08 严格） |
| `update_jigsaw_block` | 🔶 | **x**, **y**, **z** | Int | — | 拼图方块坐标 |
| | | **name**, **target**, **pool** | String | — | ResourceLocation |
| | | finalState | String | "" | 最终状态 |
| | | jointType | String | "rollable" | 连接类型 |
| | | selectionPriority, placementPriority | Int | 0 | 优先级 |
| `select_recipe` | ✅ | **windowId** | Int | — | 容器窗口 ID |
| | | **recipeIndex** | Int | — | 配方索引（1.12.2 用 recipeId: String） |
| | | makeAll | Boolean | false | 批量制作 |
| `recipe_book_toggle` | ✅ | **category** | String | — | 类别（crafting/furnace/blast_furnace/smoker） |
| | | **open**, **filtering** | Boolean | — | 打开/过滤 |
| `recipe_book_seen` | ✅ | **recipeIndex** | Int | — | 配方索引（1.12.2 用 recipeId: String） |
| `query_block_nbt` | 🔶 | **transactionId** | Int | — | 事务 ID |
| | | **x**, **y**, **z** | Int | — | 方块坐标 |
| `query_entity_nbt` | 🔶 | **transactionId**, **entityId** | Int | — | 事务 ID + 实体 ID |
| `set_beacon_effect` | ✅ | primaryEffect | Int | -1 | 主效果 ID（-1 无） |
| | | secondaryEffect | Int | -1 | 副效果 ID（-1 无） |
| `rename_item` | ✅ | **name** | String | — | 新名称（≤50 字符） |
| `select_trade` | ✅ | **selectedSlot** | Int | — | 交易槽位（≥0） |
| `lock_difficulty` | ✅ | **locked** | Boolean | — | 锁定难度（1.12.2 无效） |
| `advancement_tab` | ✅ | **action** | String | — | 操作（open/close） |
| | | tabId | String | — | 进度标签 ID（action=open 时必填） |

## 9. 调试

| Action ID | 版本 | 参数 | 类型 | 默认值 | 说明 |
|-----------|------|------|------|--------|------|
| `keep_alive` | ✅ | **id** | Long | — | KeepAlive ID |
| `pong` | 🔶 | **parameter** | Int | — | Pong 参数 |
| `tab_complete` | ✅ | **transactionId** | Int | — | 事务 ID（1.12.2 忽略） |
| | | **text** | String | — | 补全文本 |
| `custom_payload` | ✅ | **channel** | String | — | 通道标识（ResourceLocation 格式） |
| | | **data** | String | — | Base64 编码数据（≤32768 字节） |
| `debug_sample_subscription` | 🔶 | **type** | String | — | 调试订阅类型 |
| `chunk_batch_received` | 🔶 | desiredChunksPerTick | Float | 7.0 | 每 tick 期望区块数 |

## 10. 复合行为 ✅

复合行为内部调用其他基础 Action，下表仅列出自身直接读取的参数。

| Action ID | 参数 | 类型 | 默认值 | 说明 |
|-----------|------|------|--------|------|
| `look_at` | **x**, **y**, **z** | Double | — | 看向世界坐标（自动计算 yaw/pitch） |
| `look_at_entity` | **entityId** | Int | — | 看向实体眼睛位置 |
| `look_at_block` | **x**, **y**, **z** | Int | — | 看向方块面中心 |
| | face | String | "top" | 目标面 |
| `break_block` | **x**, **y**, **z** | Int | — | 挖掘方块（look_at → dig_start → wait → dig_finish） |
| `place_block_at` | **x**, **y**, **z** | Int | — | 在指定位置放置方块（look_at → place_block） |
| | face | String | "top" | 放置面 |
| | hand | String | "main_hand" | 主手/副手 |
| `attack` | **entityId** | Int | — | 攻击实体（look_at_entity → attack_entity → swing_arm） |
| `use` | hand | String | "main_hand" | 使用物品 |
| | slot | Int | -1 | 快捷栏槽位（≥0 时先切换） |
| `open_container` | **x**, **y**, **z** | Int | — | 打开容器（look_at → place_block 右键交互） |
| | hand | String | "main_hand" | 主手/副手 |
| `container_transfer` | **windowId** | Int | — | Shift-click 转移物品 |
| | **stateId** | Int | — | 容器状态 ID（1.12.2 忽略） |
| | **slot** | Int | — | 目标槽位 |
| `drop_inventory` | **slot** | Int | — | 丢弃快捷栏物品（set_carried_item → drop_item） |
| `pathfind_to` | **x**, **y**, **z** | Double | — | 直线传送式移动（绕过物理引擎，推荐用 navigate_to） |
| | speed | Double | 1.0 | 速度倍率 |
| `navigate_to` | **x**, **y**, **z** | Double | — | A* 寻路 + InjectedInput 物理引擎驱动 |
| | speed | Double | 1.0 | 移动速度（0.1~2.0） |
| | timeout | Int | 配置默认值 | 超时 tick 数 |
| | allowJump | Boolean | true | 是否允许跳跃 |
| `batch` | **actions** | JsonArray | — | 子行为数组，每项 `{action, params}`，不允许嵌套 |
| `wait` | ticks | Int | 0 | 等待 tick 数（仅在 batch 内有效） |
| `respawn` | （无） | | | 委托 perform_respawn |
| `craft_recipe` | **windowId** | Int | — | 容器窗口 ID |
| | **recipeIndex** | Int | — | 配方索引（1.12.2 用 recipeId: String） |
| | makeAll | Boolean | false | 批量制作 |

## 11. 查询行为 ✅

> 版本：`query_tooltip_state` 为 🟠（1.21.1 fabric + neoforge 未注册）；其余 ✅。

查询行为不发送网络包，仅读取客户端本地状态并返回 JSON data。

| Action ID | 参数 | 类型 | 默认值 | 返回 data 关键字段 |
|-----------|------|------|--------|-------------------|
| `query_player_state` | （无） | | | x, y, z, yaw, pitch, health, maxHealth, food, saturation, gameMode, onGround, sneaking, sprinting, flying, dead, selectedSlot, experienceLevel, experienceProgress, absorption, armorValue, airSupply, maxAirSupply, isSwimming, isUsingItem, isFallFlying, fallDistance, vehicleId, dimension, biome, mainHandItem |
| `query_block_state` | **x**, **y**, **z** | Int | — | blockId, isAir, properties, lightLevel, blockLight, skyLight, biome, hardness |
| `query_world_state` | （无） | | | timeOfDay, worldTime, raining, thundering, dimension, hasSkyLight, hasCeiling, difficulty, seaLevel |
| `query_tab_list` | limit | Int | 100 | players[{name, uuid, latency, gameMode, displayName, team}], count, total |
| `query_scoreboard` | objective | String | null | objectives[], sidebar{}, queriedScores[], playerTeam{} |
| `query_screen_state` | （无） | | | open, screenClass, isContainer, windowId, slotCount, title, screenType, tooltipVisible |
| `query_boss_bar` | （无） | | | bossBars[{name, percent, color, style, darkenSky, playMusic, createFog}], count |
| `query_container_state` | （无） | | | open, windowId, slotCount, stateId, type, title, carriedItem |
| `query_container_slots` | windowId | Int | 当前容器 | slots[{slot, itemId, count, ...}] |
| | slots | JsonArray | 全部 | 指定槽位索引数组 |
| `query_active_effects` | （无） | | | effects[{id, amplifier, duration, ambient, visible}] |
| `query_held_item` | hand | String | "main_hand" | itemId, count, components, nbt |
| `query_inventory_slot` | **slot** | Int | — | 同 query_held_item |
| `query_nearby_entities` | radius | Double | 16.0 | entities[{entityId, type, x, y, z, distance, name, uuid}] |
| | type | String | null | 过滤实体类型 |
| | limit | Int | 50 | 返回数量上限 |
| `query_chat_history` | count | Int | 10 | messages[{timestamp, raw, plain, type}], total |
| | filter | String | null | 文本过滤 |
| | since | Long | null | 时间戳过滤 |
| `query_tooltip_state` | advanced | Boolean | false | tooltipVisible, tooltipLines[], slotIndex, itemId |
| `query_chat_style` | **match** | String | — | 匹配文本的 Style 信息（color, bold, clickEvent 等） |
| | index | Int | 0 | 聊天历史索引 |
| `query_slot_tooltip` | **slot** | Int | — | tooltip 行列表 |
| | advanced | Boolean | false | 是否显示高级信息 |

## 12. 导航 ✅

| Action ID | 参数 | 类型 | 默认值 | 说明 |
|-----------|------|------|--------|------|
| `look_at_block` | **x**, **y**, **z** | Int | — | 看向方块面中心 |
| | face | String | "top" | 目标面 |
| `navigate_to` | **x**, **y**, **z** | Double | — | A* 寻路移动 |
| | speed | Double | 1.0 | 移动速度（0.1~2.0） |
| | timeout | Int | 配置默认值 | 超时 tick 数 |
| | allowJump | Boolean | true | 是否允许跳跃 |

> `look_at_block` 和 `navigate_to` 同时出现在复合行为分类中，此处为 ActionCatalog 的 Navigation 分组引用。

---

## 1.12.2 版本差异速查

以下 Action 仅 1.21.11 与 1.21.1 支持，1.12.2 不可用（清单由 CI 与注册表 diff 逐项校验）：

`pong`, `debug_sample_subscription`, `chunk_batch_received`, `pick_entity`, `pick_item_from_block`, `pick_item_from_entity`, `bundle_selected_slot`, `slot_state_change`, `update_jigsaw_block`, `query_entity_nbt`, `query_block_nbt`

以下参数在 1.12.2 端有差异：
- `click_slot`: stateId 忽略，事务 ID 和 clickedItem 自动获取
- `select_recipe` / `recipe_book_seen` / `craft_recipe`: 用 `recipeId`（String）替代 `recipeIndex`（Int）
- `player_input`: 无 sprint 参数
- `attack_entity` / `interact_entity` / `interact_entity_at`: sneaking 参数忽略
- `resource_pack_response`: uuid 参数忽略，仅支持 4 种状态
- `lock_difficulty`: Action 存在但无实际效果（1.12.2 无此协议）

---

## 1.21.1 版本差异速查

以下 Action 仅 1.21.11（Fabric + NeoForge）与 1.12.2（Forge）注册，**1.21.1 的 fabric 与 neoforge 都没有**：

`left_click`, `query_tooltip_state`

在 1.21.1 客户端上发这两个 action 会拿到"未注册 action"类失败 —— **这是版本能力差，不是你插件的缺陷**。

---

## 机器可读矩阵（CI 校验用；语义由脚本解析，勿手改格式）

<!-- bbp-action-matrix:begin
# 由 .claude/skills/validate_blackboxpro_skills.py 解析，并与源码逐项比对。
# 改动任何 ActionRegistry.kt 后必须同步本块，否则 CI 红。
# catalog-missing = 客户端注册了、但 ActionCatalog.kt 未收录的 id（union - catalog）
# key / unsupported = 该客户端注册表相对"全体客户端并集"缺少的 id（union - registry）
catalog-missing=screenshot_tooltip
key=1.12.2-forge
unsupported=bundle_selected_slot,chunk_batch_received,debug_sample_subscription,pick_entity,pick_item_from_block,pick_item_from_entity,pong,query_block_nbt,query_entity_nbt,slot_state_change,update_jigsaw_block
key=1.21.1-fabric
unsupported=left_click,query_tooltip_state
key=1.21.1-neoforge
unsupported=left_click,query_tooltip_state
key=1.21.11-fabric
unsupported=
key=1.21.11-neoforge
unsupported=
bbp-action-matrix:end -->
