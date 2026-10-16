package com.blackboxpro.fabric.dispatcher

import com.blackboxpro.common.action.ActionCatalog
import com.blackboxpro.common.action.composite.BatchAction
import com.blackboxpro.fabric.action.ActionExecutor
import com.blackboxpro.fabric.action.movement.*
import com.blackboxpro.fabric.action.block.*
import com.blackboxpro.fabric.action.entity.*
import com.blackboxpro.fabric.action.container.*
import com.blackboxpro.fabric.action.player.*
import com.blackboxpro.fabric.action.chat.*
import com.blackboxpro.fabric.action.client.*
import com.blackboxpro.fabric.action.advanced.*
import com.blackboxpro.fabric.action.debug.*
import com.blackboxpro.fabric.action.composite.*
import com.blackboxpro.fabric.action.query.*
import com.blackboxpro.runtime.bindings.FabricBindings
import com.blackboxpro.runtime.bindings.SharedSlf4jLoggerSupplier
import com.blackboxpro.common.runtime.dispatcher.RuntimeActionRegistry

object ActionRegistry {

    private val registry = RuntimeActionRegistry(SharedSlf4jLoggerSupplier.getLogger("BlackBoxPro-Registry"))

    fun register(actionId: String, executor: ActionExecutor) {
        registry.register(actionId, executor)
    }

    fun find(actionId: String): ActionExecutor? = registry.find(actionId)

    fun size(): Int = registry.size()

    /**
     * 第三方 mod 注册自定义 Action 的公开 API。
     * 可在 BBP 初始化完成后调用，注册后立即生效。
     */
    fun registerExternal(actionId: String, executor: ActionExecutor) {
        registry.registerExternal(actionId, executor)
    }

    fun registerAll() {
        registry.ensureNotInitialized()

        // Bind common actions that need platform-specific dependencies
        val boundBatchAction = BatchAction().also { it.bind(FabricBindings) }

        // === 移动与位置 ===
        register("player_move", PlayerMoveAction())
        register("player_move_look", PlayerMoveLookAction())
        register("player_look", PlayerLookAction())
        register("player_on_ground", PlayerOnGroundAction())
        register("confirm_teleportation", ConfirmTeleportationAction())
        register("move_vehicle", MoveVehicleAction())
        register("paddle_boat", PaddleBoatAction())
        register("player_input", PlayerInputAction())

        // === 方块交互 ===
        register("dig_start", DigStartAction())
        register("dig_cancel", DigCancelAction())
        register("dig_finish", DigFinishAction())
        register("place_block", PlaceBlockAction())
        register("use_item", UseItemAction())

        // === 实体交互与战斗 ===
        register("attack_entity", AttackEntityAction())
        register("interact_entity", InteractEntityAction())
        register("interact_entity_at", InteractEntityAtAction())
        register("swing_arm", SwingArmAction())
        register("left_click", LeftClickAction())

        // === 容器/GUI 操作 ===
        register("click_slot", ClickSlotAction())
        register("click_button", ClickButtonAction())
        register("close_container", CloseContainerAction())
        register("set_carried_item", SetCarriedItemAction())
        register("creative_set_slot", CreativeSetSlotAction())
        register("pick_item", PickItemAction())
        register("pick_entity", PickEntityAction())
        register("pick_item_from_block", PickItemFromBlockAction())
        register("pick_item_from_entity", PickItemFromEntityAction())
        register("bundle_selected_slot", BundleSelectedSlotAction())
        register("slot_state_change", SlotStateChangeAction())
        register("hover_slot", HoverSlotAction())

        // === 玩家状态与动作 ===
        register("sneak_start", SneakStartAction())
        register("sneak_stop", SneakStopAction())
        register("leave_bed", LeaveBedAction())
        register("sprint_start", SprintStartAction())
        register("sprint_stop", SprintStopAction())
        register("horse_jump_start", HorseJumpStartAction())
        register("horse_jump_stop", HorseJumpStopAction())
        register("open_horse_inventory", OpenHorseInventoryAction())
        register("elytra_start", ElytraStartAction())
        register("drop_item", DropItemAction())
        register("drop_item_stack", DropItemStackAction())
        register("finish_using", FinishUsingAction())
        register("swap_hands", SwapHandsAction())
        register("perform_respawn", PerformRespawnAction())
        register("spectator_teleport", SpectatorTeleportAction())

        // === 聊天与命令 ===
        register("chat_message", ChatMessageAction())
        register("chat_command", ChatCommandAction())
        register("click_chat_text", ClickChatTextAction())

        // === 客户端设置与信息 ===
        register("client_information", ClientInformationAction())
        register("player_abilities", PlayerAbilitiesAction())
        register("resource_pack_response", ResourcePackResponseAction())
        register("screenshot", ScreenshotAction())
        register("screenshot_tooltip", ScreenshotTooltipAction())
        register("connect_to_server", ConnectToServerAction())
        register("create_world", CreateWorldAction())
        register("join_world", JoinWorldAction())
        register("leave_world", LeaveWorldAction())
        register("close_screen", CloseScreenAction())
        register("open_inventory", OpenInventoryAction())

        // === 进阶交互 ===
        register("edit_book", EditBookAction())
        register("sign_book", SignBookAction())
        register("update_sign", UpdateSignAction())
        register("update_command_block", UpdateCommandBlockAction())
        register("update_command_block_minecart", UpdateCommandBlockMinecartAction())
        register("update_structure_block", UpdateStructureBlockAction())
        register("update_jigsaw_block", UpdateJigsawBlockAction())
        register("select_recipe", SelectRecipeAction())
        register("recipe_book_toggle", RecipeBookToggleAction())
        register("query_entity_nbt", QueryEntityNbtAction())
        register("query_block_nbt", QueryBlockNbtAction())
        register("set_beacon_effect", SetBeaconEffectAction())
        register("rename_item", RenameItemAction())
        register("lock_difficulty", LockDifficultyAction())
        register("select_trade", SelectTradeAction())
        register("advancement_tab", AdvancementTabAction())
        register("recipe_book_seen", RecipeBookSeenAction())

        // === 调试与特殊操作 ===
        register("custom_payload", CustomPayloadAction())
        register("tab_complete", TabCompleteAction())
        register("keep_alive", KeepAliveAction())
        register("pong", PongAction())
        register("debug_sample_subscription", DebugSampleAction())
        register("chunk_batch_received", ChunkBatchReceivedAction())

        // === 复合行为 ===
        register("look_at", LookAtAction())
        register("look_at_entity", LookAtEntityAction())
        register("break_block", BreakBlockAction())
        register("place_block_at", PlaceBlockAtAction())
        register("attack", AttackAction())
        register("use", UseAction())
        register("open_container", OpenContainerAction())
        register("container_transfer", ContainerTransferAction())
        register("drop_inventory", DropInventoryAction())
        register("pathfind_to", PathfindToAction())
        register("batch", boundBatchAction)
        register("wait", WaitAction())
        register("respawn", RespawnAction())
        register("craft_recipe", CraftRecipeAction())

        // === 查询行为 ===
        register("query_held_item", QueryHeldItemAction())
        register("query_inventory_slot", QueryInventorySlotAction())
        register("query_chat_history", QueryChatHistoryAction())
        register("query_nearby_entities", QueryNearbyEntitiesAction())
        register("query_container_state", QueryContainerStateAction())
        register("query_player_state", QueryPlayerStateAction())
        register("query_container_slots", QueryContainerSlotsAction())
        register("query_active_effects", QueryActiveEffectsAction())
        register("query_block_state", QueryBlockStateAction())
        register("query_world_state", QueryWorldStateAction())
        register("query_tab_list", QueryTabListAction())
        register("query_scoreboard", QueryScoreboardAction())
        register("query_screen_state", QueryScreenStateAction())
        register("query_boss_bar", QueryBossBarAction())
        register("query_tooltip_state", QueryTooltipStateAction())
        register("query_chat_style", QueryChatStyleAction())
        register("query_slot_tooltip", QuerySlotTooltipAction())

        // === 玩家动作（新增）===
        register("jump", JumpAction())

        // === 导航与瞄准 ===
        register("look_at_block", LookAtBlockAction())
        register("navigate_to", NavigateToAction())

        registry.freezeAndValidate(ActionCatalog.getActionIds().toSet())
    }
}
