package com.blackboxpro.plugin.command.testframework

import com.blackboxpro.common.action.ActionCatalog
import com.blackboxpro.common.action.ActionDefinition
import com.blackboxpro.common.protocol.ResponseMessage
import com.blackboxpro.common.runtime.screenshot.RuntimeScreenshotBridge
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.util.Locale
import java.util.concurrent.CompletableFuture

object BlackBoxTestCatalog {

    private val unsupportedOn1122 = setOf(
        "bundle_selected_slot",
        "chunk_batch_received",
        "debug_sample_subscription",
        "pick_entity",
        "pick_item_from_block",
        "pick_item_from_entity",
        "pong",
        "query_block_nbt",
        "query_entity_nbt",
        "slot_state_change",
        "update_jigsaw_block"
    )

    private val pendingFixtureActions = setOf(
        "confirm_teleportation",
        "move_vehicle",
        "paddle_boat",
        "dig_start",
        "dig_cancel",
        "dig_finish",
        "place_block",
        "use_item",
        "attack_entity",
        "interact_entity",
        "interact_entity_at",
        "click_slot",
        "click_button",
        "close_container",
        "creative_set_slot",
        "pick_item",
        "pick_entity",
        "pick_item_from_block",
        "pick_item_from_entity",
        "bundle_selected_slot",
        "slot_state_change",
        "hover_slot",
        "query_slot_tooltip",
        "query_tooltip_state",
        "screenshot_tooltip",
        "leave_bed",
        "horse_jump_start",
        "horse_jump_stop",
        "open_horse_inventory",
        "elytra_start",
        "drop_item",
        "drop_item_stack",
        "finish_using",
        "perform_respawn",
        "spectator_teleport",
        "edit_book",
        "sign_book",
        "update_sign",
        "update_command_block",
        "update_command_block_minecart",
        "update_structure_block",
        "update_jigsaw_block",
        "select_recipe",
        "recipe_book_toggle",
        "recipe_book_seen",
        "query_entity_nbt",
        "query_block_nbt",
        "set_beacon_effect",
        "rename_item",
        "select_trade",
        "connect_to_server",
        "close_screen",
        "create_world",
        "join_world",
        "leave_world",
        "custom_payload",
        "tab_complete",
        "debug_sample_subscription",
        "look_at_entity",
        "break_block",
        "place_block_at",
        "attack",
        "use",
        "open_container",
        "container_transfer",
        "drop_inventory",
        "pathfind_to",
        "respawn",
        "craft_recipe",
        "look_at_block",
        "navigate_to",
        // keep_alive 发送硬编码 id=1L，若服务端期望的 keepAlive id 不同则踢出玩家，导致后续测试全部失败
        "keep_alive"
    )

    private val longRunningActions = setOf(
        "player_move",
        "player_move_look",
        "pathfind_to",
        "navigate_to",
        "break_block",
        "screenshot",
        // D2：screenshot_tooltip 现在走"负对照 + 主帧"两个相位（各等 ≥1 渲染帧），
        // 超时余量是真实的 flake 来源，故与 screenshot 同档
        "screenshot_tooltip"
    )

    /**
     * D2（设计 P）主帧**断言判据**的下限。判据 E2 原文：
     * `|R−B| ≤ 4 ∧ G ≤ R−8 ∧ R ≤ 40`（`audit\d2-pixel-fixtures.py` 对 `audit\` 下 12 张真实帧独立复算）。
     *
     * 实测分离度：5 个"有 tooltip"帧为 **1292~2504**；7 个"无 tooltip"帧（含全部负对照）**恒为 0**
     * ⇒ 阈值 300 的余量 = 4.31×。**阈值的唯一出处是 `RuntimeScreenshotBridge.TOOLTIP_BG_ASSERT_THRESHOLD`**
     * （下面这行只引用它，不要再写字面量；离线自检引用同一个常量）。
     *
     * ⚠️ 不要用严格判据 A（`R,B∈[8,30] ∧ |R−B|≤4 ∧ G≤6`）+ 128 去断言：A 的 `G ≤ 6` 把真正的
     * vanilla tooltip 背景（`[27,12,27]` G=12、`[23,8,23]` G=8）排除在外，只抓到较暗的一小部分
     * ⇒ 计数随 tooltip 底下的背景变化，在更亮的面板上可掉到 0 而 tooltip 画得好好的（**假阴性**）。
     * A 只作诊断字段（`tooltipBgStrict` / `controlTooltipBgStrict`）随响应回传，不参与判定。
     */
    private val d2MainThresholdLoose = RuntimeScreenshotBridge.TOOLTIP_BG_ASSERT_THRESHOLD

    fun getCategories(): List<String> =
        ActionCatalog.getDefinitions().map { categoryOf(it.id) }.distinct().sorted()

    fun findAction(actionId: String): BlackBoxActionTestCase? =
        ActionCatalog.getDefinitions().firstOrNull { it.id == actionId }?.let(::buildCase)

    fun cases(profile: BlackBoxTestProfile, category: String? = null): List<BlackBoxActionTestCase> {
        val all = when (profile) {
            BlackBoxTestProfile.SMOKE -> smokeCases()
            BlackBoxTestProfile.FULL -> ActionCatalog.getDefinitions().map(::buildCase)
        }
        return if (category == null) all else all.filter { it.category.equals(category, ignoreCase = true) }
    }

    fun smokeCases(): List<BlackBoxActionTestCase> {
        val smokeIds = listOf(
            "player_on_ground",
            "player_move",
            "player_move_look",
            "player_look",
            "sneak_start",
            "sneak_stop",
            "sprint_start",
            "sprint_stop",
            "jump",
            "swap_hands",
            "swing_arm",
            "chat_message",
            "chat_command",
            "set_carried_item",
            "query_player_state",
            "query_held_item",
            "query_nearby_entities",
            "query_chat_history",
            "query_active_effects",
            "wait",
            "batch",
            "screenshot"
        )
        return smokeIds.mapNotNull(::findAction)
    }

    private fun buildCase(definition: ActionDefinition): BlackBoxActionTestCase {
        val actionId = definition.id
        val supportedProfiles = BlackBoxLoaderProfile.entries.filterTo(linkedSetOf()) { profile ->
            profile != BlackBoxLoaderProfile.MC_1122 || actionId !in unsupportedOn1122
        }

        return BlackBoxActionTestCase(
            actionId = actionId,
            displayName = prettify(actionId),
            category = categoryOf(actionId),
            supportedProfiles = supportedProfiles,
            timeoutMs = if (actionId in longRunningActions) 20000L else 5000L,
            prepare = { ctx ->
                when {
                    ctx.loaderProfile !in supportedProfiles ->
                        CompletableFuture.completedFuture(BlackBoxPrepareResult("当前版本不支持该 action"))
                    actionId in pendingFixtureActions ->
                        prepareFixture(actionId, ctx)
                    else -> CompletableFuture.completedFuture(BlackBoxPrepareResult())
                }
            },
            execute = { ctx ->
                val params = defaultParams(actionId, ctx)
                when {
                    params.has("__needEntityId") -> {
                        // 先查询附近实体，取第一个 entityId
                        ctx.sendAction("query_nearby_entities", JsonObject().apply {
                            addProperty("radius", 8.0)
                            addProperty("limit", 5)
                        }).thenCompose { queryResp ->
                            val entityId = queryResp.data
                                ?.takeIf { it.has("entities") }
                                ?.getAsJsonArray("entities")
                                ?.takeIf { it.size() > 0 }
                                ?.get(0)?.asJsonObject
                                ?.get("entityId")?.asInt
                            if (entityId == null) {
                                CompletableFuture.completedFuture(
                                    ResponseMessage(ctx.testId, "failure", "No entities found nearby for $actionId", null)
                                )
                            } else {
                                val actionParams = JsonObject().apply {
                                    addProperty("entityId", entityId)
                                    if (actionId == "interact_entity_at") {
                                        val entity = queryResp.data
                                            ?.getAsJsonArray("entities")?.get(0)?.asJsonObject
                                        addProperty("targetX", entity?.get("x")?.asDouble ?: 0.0)
                                        addProperty("targetY", entity?.get("y")?.asDouble ?: 1.0)
                                        addProperty("targetZ", entity?.get("z")?.asDouble ?: 0.0)
                                        addProperty("hand", "main_hand")
                                    }
                                }
                                ctx.sendAction(actionId, actionParams, timeoutMs = 5000L)
                            }
                        }
                    }
                    params.has("__needWindowId") -> {
                        // 先查询容器状态，取当前 windowId 和 stateId
                        ctx.sendAction("query_container_state", JsonObject()).thenCompose { queryResp ->
                            val data = queryResp.data
                            val windowId = data?.get("windowId")?.asInt ?: 0
                            val stateId = data?.get("stateId")?.asInt ?: 0
                            val isOpen = data?.get("open")?.asBoolean ?: false
                            if (!isOpen) {
                                CompletableFuture.completedFuture(
                                    ResponseMessage(ctx.testId, "failure", "No container open for $actionId", null)
                                )
                            } else {
                                val actionParams = JsonObject().apply {
                                    addProperty("windowId", windowId)
                                    addProperty("stateId", stateId)
                                    when (actionId) {
                                        "click_slot" -> {
                                            // 三端统一参数格式
                                            addProperty("slot", 0)
                                            addProperty("button", 0)
                                            addProperty("mode", 0)
                                        }
                                        "close_container" -> { /* windowId 已加入 */ }
                                        "hover_slot" -> {
                                            addProperty("slot", 0)
                                            addProperty("durationTicks", 10)
                                        }
                                        "slot_state_change" -> {
                                            addProperty("slotId", 0)
                                            addProperty("state", false)
                                        }
                                        "drop_inventory" -> {
                                            addProperty("slot", 0)
                                            addProperty("dropStack", false)
                                        }
                                        "screenshot_tooltip" -> {
                                            // slot0 已由 openChestInventory 放入 STONE（非空才能取到 tooltip 文本行）
                                            addProperty("slot", 0)
                                            addProperty("testId", "catalog_screenshot_tooltip")
                                            addProperty("prefix", "screenshot_tooltip")
                                        }
                                    }
                                }
                                // D2：按集合取超时，screenshot_tooltip 走两相位需要更长余量
                                ctx.sendAction(
                                    actionId,
                                    actionParams,
                                    timeoutMs = if (actionId in longRunningActions) 20000L else 5000L
                                )
                            }
                        }
                    }
                    else -> {
                        val timeout = when (actionId) {
                            "player_move", "player_move_look" -> 15000L
                            "screenshot" -> 20000L
                            "break_block", "navigate_to", "pathfind_to" -> 20000L
                            else -> 5000L
                        }
                        ctx.sendAction(actionId, params, timeoutMs = timeout)
                    }
                }
            },
            verify = { _, response -> verify(actionId, response) }
        )
    }

    /**
     * 为有夹具支撑的 pendingFixtureActions 设置前置条件。
     * 有夹具 → 返回 BlackBoxPrepareResult()（放行）
     * 无夹具 → 返回跳过理由
     */
    private fun prepareFixture(actionId: String, ctx: BlackBoxTestContext): CompletableFuture<BlackBoxPrepareResult> {
        val fm = ctx.fixtureManager
        return when (actionId) {

            // ===== FX-ITEM：背包有物品 =====
            // resetBaseline 已在 slot0 放 STONE，drop_item/drop_item_stack 直接可用
            "drop_item", "drop_item_stack" ->
                CompletableFuture.completedFuture(BlackBoxPrepareResult())

            // finish_using 需要手持食物并开始使用，过于复杂，暂跳过
            "finish_using" ->
                CompletableFuture.completedFuture(BlackBoxPrepareResult("需要手持食物并进入使用状态"))

            // pick_item 需要光标指向物品实体，暂跳过
            "pick_item" ->
                CompletableFuture.completedFuture(BlackBoxPrepareResult("需要世界中存在物品实体"))

            // edit_book/sign_book 在 1.21.11 导致后续断线，待排查（书写状态 GUI 与 reset 冲突）
            "edit_book", "sign_book" ->
                CompletableFuture.completedFuture(BlackBoxPrepareResult("书操作后 reset 导致断线，待排查"))

            // ===== FX-BLOCK：前方有方块 =====
            "dig_start", "dig_cancel", "dig_finish",
            "place_block", "use_item",
            "break_block", "place_block_at", "look_at_block" -> {
                ctx.mainThread {
                    // 在玩家正前方 2 格放一个石头（dx=0, dy=0, dz=2）
                    fm.ensureBlock(0, 0, 2, "STONE")
                    null
                }.thenApply { BlackBoxPrepareResult() }
            }

            // ===== FX-ENTITY：攻击盔甲架在 Paper 1.21.11 触发断线，待进一步排查 =====
            // 可能是 Paper 1.21.11 对 invulnerable/Peaceful 模式下的攻击有更严格的校验
            "attack_entity", "interact_entity", "interact_entity_at",
            "attack", "use", "look_at_entity" ->
                CompletableFuture.completedFuture(BlackBoxPrepareResult("FX-ENTITY 待排查：攻击盔甲架导致客户端断线"))

            // ===== FX-GUI：需要打开容器 =====
            // ===== FX-GUI：打开箱子容器 =====
            "click_slot", "close_container" -> {
                ctx.mainThread {
                    fm.openChestInventory()
                }.thenCompose {
                    ctx.delay(2000L)  // 等待客户端区块加载并收到 open_window 包（网络延迟充裕）
                }.thenApply { BlackBoxPrepareResult() }
            }
            // drop_inventory 不需要容器，只需背包有物品（resetBaseline 已放 STONE）
            "drop_inventory" ->
                CompletableFuture.completedFuture(BlackBoxPrepareResult())
            // screenshot_tooltip 需同时满足：容器已打开 + 目标槽位非空 + 已完成至少一帧渲染。
            // 先开箱（BlackBoxFixtureManager.openChestInventory 会在 slot0 放 STONE，满足"非空"），
            // 再等客户端收到 open_window 包；否则客户端 hover 阶段就会失败。
            // ⚠️ 仍依赖客户端窗口可交互：窗口最小化时 GLFW 报 0×0，moveCursor 的 coerceIn 会抛异常。
            "screenshot_tooltip" -> {
                ctx.mainThread {
                    fm.openChestInventory()
                }.thenCompose {
                    ctx.delay(2000L)  // 与 click_slot 同口径：等待区块加载 + open_window 包到达
                }.thenApply { BlackBoxPrepareResult() }
            }
            // 以下需要特殊 GUI（铁砧/信标/村民等），暂跳过
            "click_button", "open_container", "container_transfer",
            "rename_item", "select_trade", "set_beacon_effect", "craft_recipe" ->
                CompletableFuture.completedFuture(BlackBoxPrepareResult("需要特殊容器 GUI（铁砧/信标/村民等）"))

            // ===== 需要特殊游戏状态 =====
            "leave_bed" ->
                CompletableFuture.completedFuture(BlackBoxPrepareResult("需要玩家处于睡眠状态"))
            "horse_jump_start", "horse_jump_stop", "open_horse_inventory" ->
                CompletableFuture.completedFuture(BlackBoxPrepareResult("需要骑乘马匹"))
            "elytra_start" ->
                CompletableFuture.completedFuture(BlackBoxPrepareResult("需要穿戴鞘翅且处于下落状态"))
            "perform_respawn" ->
                CompletableFuture.completedFuture(BlackBoxPrepareResult("需要玩家处于死亡状态"))
            "spectator_teleport" ->
                CompletableFuture.completedFuture(BlackBoxPrepareResult("需要旁观者模式"))
            "move_vehicle", "paddle_boat" ->
                CompletableFuture.completedFuture(BlackBoxPrepareResult("需要骑乘载具"))
            "confirm_teleportation" ->
                CompletableFuture.completedFuture(BlackBoxPrepareResult("需要服务端发送 TP 确认包"))

            // ===== 需要特殊方块/服务端配置 =====
            "update_sign" -> {
                ctx.mainThread {
                    fm.ensureBlock(0, 0, 2, "OAK_SIGN", "SIGN")
                    null
                }.thenApply { BlackBoxPrepareResult() }
            }
            "update_command_block", "update_command_block_minecart",
            "update_structure_block", "update_jigsaw_block" ->
                CompletableFuture.completedFuture(BlackBoxPrepareResult("需要命令方块/结构方块且 op 权限"))
            "select_recipe", "recipe_book_toggle", "recipe_book_seen" ->
                CompletableFuture.completedFuture(BlackBoxPrepareResult("需要配方书开启状态"))
            "query_entity_nbt", "query_block_nbt" ->
                CompletableFuture.completedFuture(BlackBoxPrepareResult("1.21.11 不支持该 action"))
            "select_trade" ->
                CompletableFuture.completedFuture(BlackBoxPrepareResult("需要打开村民交易界面"))

            // ===== 调试类：协议格式复杂，暂跳过 =====
            "custom_payload" ->
                CompletableFuture.completedFuture(BlackBoxPrepareResult("需要指定通道和数据"))
            "tab_complete" ->
                CompletableFuture.completedFuture(BlackBoxPrepareResult("需要 tab 补全上下文"))
            "debug_sample_subscription" ->
                CompletableFuture.completedFuture(BlackBoxPrepareResult("需要服务端调试采样支持"))

            // ===== 客户端会话 / 世界管理类 =====
            "connect_to_server" ->
                CompletableFuture.completedFuture(BlackBoxPrepareResult("该 action 用于启动前连接流程，不纳入 run_test 全量回放"))
            "close_screen" ->
                CompletableFuture.completedFuture(BlackBoxPrepareResult("需要显式打开 GUI；当前 run_test 默认场景不覆盖"))
            "create_world", "join_world", "leave_world" ->
                CompletableFuture.completedFuture(BlackBoxPrepareResult("该 action 属于客户端会话管理，当前 run_test 服务端联调链路不覆盖"))

            // ===== keep_alive：会踢人 =====
            "keep_alive" ->
                CompletableFuture.completedFuture(BlackBoxPrepareResult("发送硬编码 id 可能导致踢出玩家"))

            // 其他未分类
            else -> CompletableFuture.completedFuture(BlackBoxPrepareResult("该 action 需要专用夹具，当前框架已建模但夹具尚未补齐"))
        }
    }

    private fun verify(actionId: String, response: com.blackboxpro.common.protocol.ResponseMessage): String? {
        if (!response.isSuccess) {
            return response.message ?: response.status
        }
        val data = response.data
        return when (actionId) {
            "screenshot" -> if (data?.has("filePath") == true) null else "截图响应缺少 filePath"
            "query_player_state",
            "query_held_item",
            "query_inventory_slot",
            "query_chat_history",
            "query_nearby_entities",
            "query_container_state",
            "query_container_slots",
            "query_active_effects",
            "query_block_state",
            "query_world_state",
            "query_tab_list",
            "query_scoreboard",
            "query_screen_state",
            "query_boss_bar" -> if (data != null && data.size() > 0) null else "查询响应没有返回数据"
            // ---- D2（设计 P）：tooltip 是否**真的**在截图里的成对判据 ----
            // 为什么必须成对：`HandledScreen.render` 自身会写 focusedSlot 并可能走 vanilla 的悬停
            // tooltip 路径，所以"主帧像素 > 0"这一个条件会被 vanilla 骗过（零回归保护价值）；
            // 负对照帧（同光标、未注入绘制）必须**一个 tooltip 像素都没有**，否则本次比较不可信。
            "screenshot_tooltip" -> {
                val d = data
                if (d == null || d.get("tooltipPixelProbe")?.asString != RuntimeScreenshotBridge.PROBE_OK) {
                    // 该版本未实现像素探针（字段缺失或 "unsupported"）：退化为 status==success。
                    // 已知代价：此退化**只能**从结果 JSON 里"字段是否存在"看出，
                    // run_test 摘要（passed/failed 计数）上看不出强弱，PR 文本须写明。
                    null
                } else {
                    val controlLoose = d.get("controlTooltipBgLoose")?.asInt ?: -1
                    val mainLoose = d.get("tooltipBgLoose")?.asInt ?: -1
                    // 严格判据 A 只作诊断（对背景敏感、会假阴性，见 d2MainThresholdLoose 的注释）
                    val diag = "strict(diag)=${d.get("tooltipBgStrict")?.asInt ?: -1}" +
                        "/${d.get("controlTooltipBgStrict")?.asInt ?: -1}"
                    when {
                        controlLoose != 0 ->
                            "tooltip 像素负对照被污染：controlTooltipBgLoose=$controlLoose（期望 0）" +
                                "，本次比较不可信（main=$mainLoose, $diag）"
                        mainLoose < d2MainThresholdLoose ->
                            "tooltip 不在截图中：tooltipBgLoose=$mainLoose < $d2MainThresholdLoose" +
                                "（control=$controlLoose, $diag）"
                        else -> null
                    }
                }
            }
            else -> null
        }
    }

    private fun defaultParams(actionId: String, ctx: BlackBoxTestContext): JsonObject = when (actionId) {
        "player_move" -> JsonObject().apply {
            val target = ctx.fixtureManager.relative(2.0, 0.0, 0.0)
            addProperty("x", target.x)
            addProperty("y", target.y)
            addProperty("z", target.z)
            addProperty("speed", 1.0)
            addProperty("timeout", 80)
        }
        "player_move_look" -> JsonObject().apply {
            val target = ctx.fixtureManager.relative(-2.0, 0.0, 0.0)
            addProperty("x", target.x)
            addProperty("y", target.y)
            addProperty("z", target.z)
            addProperty("pitch", 0.0f)
            addProperty("speed", 1.0)
            addProperty("timeout", 80)
        }
        "player_look" -> JsonObject().apply {
            addProperty("yaw", 90.0f)
            addProperty("pitch", -15.0f)
            addProperty("onGround", true)
        }
        "player_on_ground" -> JsonObject().apply { addProperty("onGround", true) }
        "player_input" -> JsonObject().apply {
            addProperty("forward", true)
            addProperty("backward", false)
            addProperty("left", false)
            addProperty("right", false)
            addProperty("jump", false)
            addProperty("sneak", false)
            addProperty("sprint", false)
        }
        "sneak_start", "sneak_stop", "sprint_start", "sprint_stop", "jump", "swap_hands", "leave_bed", "drop_item", "drop_item_stack", "perform_respawn" -> JsonObject()
        "chat_message" -> JsonObject().apply { addProperty("message", "[BlackBoxPro] full action test") }
        "chat_command" -> JsonObject().apply { addProperty("command", "me BlackBoxPro full test") }
        "set_carried_item", "creative_set_slot" -> JsonObject().apply { addProperty("slot", 0) }
        "client_information" -> JsonObject().apply {
            addProperty("locale", "zh_cn")
            addProperty("viewDistance", 8)
            addProperty("chatMode", 0)
            addProperty("chatColors", true)
            addProperty("skinParts", 127)
            addProperty("mainHand", 1)
            addProperty("textFiltering", false)
            addProperty("allowServerListings", true)
        }
        "player_abilities" -> JsonObject().apply { addProperty("flying", false) }
        "resource_pack_response" -> JsonObject().apply {
            addProperty("uuid", "00000000-0000-0000-0000-000000000000")
            addProperty("result", "accepted")
        }
        "connect_to_server" -> JsonObject().apply {
            addProperty("ip", "127.0.0.1")
            addProperty("port", 25565)
        }
        "close_screen" -> JsonObject()
        "create_world" -> JsonObject().apply {
            addProperty("worldName", "blackbox_test_world")
            addProperty("gameMode", "creative")
            addProperty("allowCommands", true)
            addProperty("generateStructures", true)
            addProperty("bonusChest", false)
        }
        "join_world" -> JsonObject().apply {
            addProperty("worldName", "blackbox_test_world")
        }
        "keep_alive" -> JsonObject().apply { addProperty("id", 1L) }
        "pong" -> JsonObject().apply { addProperty("parameter", 0) }
        "chunk_batch_received" -> JsonObject()
        "look_at" -> JsonObject().apply {
            val target = ctx.fixtureManager.relative(0.0, 1.0, 4.0)
            addProperty("x", target.x)
            addProperty("y", target.y)
            addProperty("z", target.z)
        }
        "wait" -> JsonObject().apply { addProperty("ticks", 10) }
        "batch" -> JsonObject().apply {
            add("actions", JsonArray().apply {
                add(JsonObject().apply { addProperty("action", "sneak_start"); add("params", JsonObject()) })
                add(JsonObject().apply { addProperty("action", "wait"); add("params", JsonObject().apply { addProperty("ticks", 5) }) })
                add(JsonObject().apply { addProperty("action", "sneak_stop"); add("params", JsonObject()) })
            })
        }
        "query_held_item" -> JsonObject().apply { addProperty("hand", "main_hand") }
        "query_inventory_slot" -> JsonObject().apply { addProperty("slot", 0) }
        "query_chat_history" -> JsonObject().apply { addProperty("count", 5) }
        "query_nearby_entities" -> JsonObject().apply {
            addProperty("radius", 16.0)
            addProperty("limit", 20)
        }
        "query_container_slots" -> JsonObject().apply { addProperty("windowId", 0) }
        "query_block_state" -> JsonObject().apply {
            val target = ctx.fixtureManager.block(0, 0, 0)
            addProperty("x", target.blockX)
            addProperty("y", target.blockY)
            addProperty("z", target.blockZ)
        }
        "query_tab_list" -> JsonObject().apply { addProperty("limit", 10) }
        "query_scoreboard" -> JsonObject()
        "query_slot_tooltip" -> JsonObject().apply { addProperty("slot", 0) }
        "screenshot" -> JsonObject().apply {
            addProperty("testId", ctx.testId)
            addProperty("prefix", "catalog_${actionId}")
            addProperty("playerName", ctx.player.name)
        }
        "lock_difficulty" -> JsonObject().apply { addProperty("locked", false) }
        "advancement_tab" -> JsonObject().apply {
            addProperty("action", "close")
        }
        // FX-ITEM：book 类（prepare 时已移到 slot0）
        "edit_book" -> JsonObject().apply {
            addProperty("slot", 0)
            add("pages", JsonArray().apply { add("BlackBox Test Page") })
            addProperty("title", "")
            addProperty("signing", false)
        }
        "sign_book" -> JsonObject().apply {
            addProperty("slot", 0)
            add("pages", JsonArray().apply { add("BlackBox Test Page") })
            addProperty("title", "BlackBox Test")
            addProperty("signing", true)
        }
        // FX-BLOCK 类
        "dig_start", "dig_cancel", "dig_finish" -> JsonObject().apply {
            val target = ctx.fixtureManager.block(0, 0, 2)
            addProperty("x", target.blockX)
            addProperty("y", target.blockY)
            addProperty("z", target.blockZ)
            addProperty("face", "south")
        }
        "place_block" -> JsonObject().apply {
            // 在前方方块的北面放方块（石头在 slot0）
            val target = ctx.fixtureManager.block(0, 0, 2)
            addProperty("x", target.blockX)
            addProperty("y", target.blockY)
            addProperty("z", target.blockZ)
            addProperty("face", "north")
            addProperty("hand", "main_hand")
        }
        "use_item" -> JsonObject().apply {
            addProperty("hand", "main_hand")
        }
        "break_block" -> JsonObject().apply {
            val target = ctx.fixtureManager.block(0, 0, 2)
            addProperty("x", target.blockX)
            addProperty("y", target.blockY)
            addProperty("z", target.blockZ)
        }
        "place_block_at" -> JsonObject().apply {
            val target = ctx.fixtureManager.block(1, 0, 2)
            addProperty("x", target.blockX)
            addProperty("y", target.blockY)
            addProperty("z", target.blockZ)
            addProperty("materialName", "STONE")
        }
        "look_at_block" -> JsonObject().apply {
            val target = ctx.fixtureManager.block(0, 0, 2)
            addProperty("x", target.blockX)
            addProperty("y", target.blockY)
            addProperty("z", target.blockZ)
        }
        "update_sign" -> JsonObject().apply {
            val target = ctx.fixtureManager.block(0, 0, 2)
            addProperty("x", target.blockX)
            addProperty("y", target.blockY)
            addProperty("z", target.blockZ)
            add("lines", JsonArray().apply {
                add("BlackBox"); add("Test"); add(""); add("")
            })
        }
        // FX-GUI：需要动态 windowId，execute 时先 query_container_state
        "click_slot", "close_container" -> JsonObject().apply {
            addProperty("__needWindowId", true)
        }
        // screenshot_tooltip：容器界面截图，windowId/slot/testId/prefix 在 __needWindowId 分支内装配
        "screenshot_tooltip" -> JsonObject().apply {
            addProperty("__needWindowId", true)
        }
        // drop_inventory：从背包指定 slot 丢一个
        "drop_inventory" -> JsonObject().apply {
            addProperty("slot", 0)  // slot0 有 STONE（resetBaseline 放的）
        }
        // FX-ENTITY 类：需要动态 entityId，execute 时先 query_nearby_entities 取第一个实体
        "attack_entity", "interact_entity", "interact_entity_at",
        "attack", "use", "look_at_entity" -> JsonObject().apply {
            addProperty("__needEntityId", true)
        }
        else -> JsonObject()
    }

    private fun categoryOf(actionId: String): String = when {
        actionId.startsWith("query_") -> "query"
        actionId in setOf("look_at", "look_at_entity", "look_at_block", "pathfind_to", "navigate_to", "break_block", "place_block_at", "attack", "use", "open_container", "container_transfer", "drop_inventory", "wait", "batch", "respawn", "craft_recipe") -> "composite"
        actionId in setOf("chat_message", "chat_command") -> "chat"
        actionId in setOf("client_information", "player_abilities", "resource_pack_response", "screenshot", "connect_to_server", "close_screen", "create_world", "join_world", "leave_world") -> "client"
        actionId in setOf("custom_payload", "tab_complete", "keep_alive", "pong", "debug_sample_subscription", "chunk_batch_received") -> "debug"
        actionId in setOf("player_move", "player_move_look", "player_look", "player_on_ground", "confirm_teleportation", "move_vehicle", "paddle_boat", "player_input") -> "movement"
        actionId in setOf("dig_start", "dig_cancel", "dig_finish", "place_block", "use_item") -> "block"
        actionId in setOf("attack_entity", "interact_entity", "interact_entity_at", "swing_arm") -> "entity"
        actionId in setOf("click_slot", "click_button", "close_container", "set_carried_item", "creative_set_slot", "pick_item", "pick_entity", "pick_item_from_block", "pick_item_from_entity", "bundle_selected_slot", "slot_state_change") -> "container"
        actionId in setOf("sneak_start", "sneak_stop", "sprint_start", "sprint_stop", "leave_bed", "horse_jump_start", "horse_jump_stop", "open_horse_inventory", "elytra_start", "drop_item", "drop_item_stack", "finish_using", "swap_hands", "perform_respawn", "spectator_teleport", "jump") -> "player"
        actionId in setOf("edit_book", "sign_book", "update_sign", "update_command_block", "update_command_block_minecart", "update_structure_block", "update_jigsaw_block", "select_recipe", "recipe_book_toggle", "recipe_book_seen", "query_entity_nbt", "query_block_nbt", "set_beacon_effect", "rename_item", "select_trade", "lock_difficulty", "advancement_tab") -> "advanced"
        else -> "misc"
    }

    private fun prettify(actionId: String): String =
        actionId.split('_').joinToString(" ") { part ->
            part.replaceFirstChar { ch -> if (ch.isLowerCase()) ch.titlecase(Locale.getDefault()) else ch.toString() }
        }
}
