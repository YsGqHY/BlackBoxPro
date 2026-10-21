package com.blackboxpro.common.action

object ActionCatalog {

    private val definitions = linkedMapOf<String, ActionDefinition>()

    init {
        // Movement
        register("player_move", "x", "y", "z", "speed", "timeout")
        register("player_move_look", "x", "y", "z", "pitch", "speed", "timeout")
        register("player_look", "yaw", "pitch", "onGround")
        register("player_on_ground", "onGround")
        register("confirm_teleportation", "teleportId")
        register("move_vehicle", "x", "y", "z", "yaw", "pitch", "onGround")
        register("paddle_boat", "leftPaddling", "rightPaddling")
        register("player_input", "forward", "backward", "left", "right", "jump", "sneak", "sprint")

        // Block interaction
        register("dig_start", "x", "y", "z", "face", "sequence")
        register("dig_cancel", "x", "y", "z", "face", "sequence")
        register("dig_finish", "x", "y", "z", "face", "sequence")
        register("place_block", "x", "y", "z", "face", "hand", "cursorX", "cursorY", "cursorZ", "insideBlock", "sequence")
        register("use_item", "hand")

        // Entity interaction
        register("attack_entity", "entityId", "sneaking")
        register("interact_entity", "entityId", "hand", "sneaking")
        register("interact_entity_at", "entityId", "targetX", "targetY", "targetZ", "hand", "sneaking")
        register("swing_arm", "hand")
        register("left_click", "hand")

        // Container / GUI
        register("click_slot", "windowId", "stateId", "slot", "button", "mode")
        register("click_button", "windowId", "buttonId")
        register("close_container", "windowId")
        register("set_carried_item", "slot")
        register("creative_set_slot", "slot")
        register("pick_item", "x", "y", "z", "includeData")
        register("pick_entity", "entityId", "includeData")
        register("pick_item_from_block", "x", "y", "z", "includeData")
        register("pick_item_from_entity", "entityId", "includeData")
        register("bundle_selected_slot", "slotId", "selectedIndex")
        register("slot_state_change", "windowId", "slotId", "state")
        register("hover_slot", "windowId", "slot", "durationTicks")

        // Player state
        register("sneak_start")
        register("sneak_stop")
        register("sprint_start")
        register("sprint_stop")
        register("leave_bed")
        register("horse_jump_start", "jumpBoost", "entityId")
        register("horse_jump_stop", "entityId")
        register("open_horse_inventory", "entityId")
        register("elytra_start", "entityId")
        register("drop_item", "sequence")
        register("drop_item_stack", "sequence")
        register("finish_using", "sequence")
        register("swap_hands", "sequence")
        register("perform_respawn")
        register("spectator_teleport", "targetUuid")
        register("jump")

        // Chat / commands
        register("chat_message", "message")
        register("chat_command", "command")
        register("click_chat_text", "match", "index", "execute")

        // Client
        register("client_information", "locale", "viewDistance", "chatMode", "chatColors", "skinParts", "mainHand", "textFiltering", "allowServerListings")
        register("player_abilities", "flying")
        register("resource_pack_response", "uuid", "result")
        register("screenshot", "testId", "prefix", "playerName")
        // 参数以客户端 execute(params, commandId) 为准：
        //   windowId/slot 必填（requireInt，缺失即 Invalid params）
        //   testId 默认 "default"、prefix 默认 "tooltip"（params.get(...) ?: 默认值）
        // 注意：playerName 不是入参——客户端由 RuntimeScreenshotBridge.currentPlayerName() 自行获取
        register("screenshot_tooltip", "windowId", "slot", "testId", "prefix")
        register("connect_to_server", "ip", "port")
        register("close_screen")
        register("open_inventory")
        register("create_world", "worldName", "gameMode", "difficulty", "allowCommands", "generateStructures", "bonusChest", "seed")
        register("join_world", "worldName")
        register("leave_world")

        // Advanced
        register("edit_book", "slot", "pages", "title")
        register("sign_book", "slot", "title", "pages")
        register("update_sign", "x", "y", "z", "isFrontText", "lines")
        register("update_command_block", "x", "y", "z", "command", "mode", "trackOutput", "conditional", "alwaysActive")
        register("update_command_block_minecart", "entityId", "command", "trackOutput")
        register("update_structure_block", "x", "y", "z", "action", "mode", "name", "offsetX", "offsetY", "offsetZ", "sizeX", "sizeY", "sizeZ", "mirror", "rotation", "metadata", "integrity", "seed", "flags")
        register("update_jigsaw_block", "x", "y", "z", "name", "target", "pool", "finalState", "jointType", "selectionPriority", "placementPriority")
        register("select_recipe", "windowId", "recipeIndex", "makeAll")
        register("recipe_book_toggle", "category", "open", "filtering")
        register("recipe_book_seen", "recipeIndex")
        register("query_entity_nbt", "transactionId", "entityId")
        register("query_block_nbt", "transactionId", "x", "y", "z")
        register("set_beacon_effect", "primaryEffect", "secondaryEffect")
        register("rename_item", "name")
        register("select_trade", "selectedSlot")
        register("lock_difficulty", "locked")
        register("advancement_tab", "action", "tabId")

        // Debug
        register("custom_payload", "channel", "data")
        register("tab_complete", "transactionId", "text")
        register("keep_alive", "id")
        register("pong", "parameter")
        register("debug_sample_subscription", "type")
        register("chunk_batch_received", "desiredChunksPerTick")

        // Composite
        register("look_at", "x", "y", "z")
        register("look_at_entity", "entityId")
        register("break_block", "x", "y", "z")
        register("place_block_at", "x", "y", "z", "face", "hand")
        register("attack", "entityId")
        register("use", "hand", "slot")
        register("open_container", "x", "y", "z", "hand")
        register("container_transfer", "windowId", "stateId", "slot")
        register("drop_inventory", "slot")
        register("pathfind_to", "x", "y", "z", "speed")
        register("batch")
        register("wait", "ticks")
        register("respawn")
        register("craft_recipe", "windowId", "recipeIndex", "makeAll")

        // Query
        register("query_held_item", "hand")
        register("query_inventory_slot", "slot")
        register("query_chat_history", "count", "filter", "since")
        register("query_nearby_entities", "radius", "type", "limit")
        register("query_container_state")
        register("query_player_state")
        register("query_container_slots", "windowId", "slots")
        register("query_active_effects")
        register("query_block_state", "x", "y", "z")
        register("query_world_state")
        register("query_tab_list", "limit")
        register("query_scoreboard", "objective")
        register("query_screen_state")
        register("query_boss_bar")
        register("query_tooltip_state", "advanced")
        register("query_chat_style", "match", "index")
        register("query_slot_tooltip", "slot", "advanced")

        // Navigation
        register("look_at_block", "x", "y", "z", "face")
        register("navigate_to", "x", "y", "z", "speed", "timeout", "allowJump")
    }

    fun getActionIds(): List<String> = definitions.keys.toList()

    fun getParams(actionId: String): List<String>? = definitions[actionId]?.params

    fun contains(actionId: String): Boolean = actionId in definitions

    fun size(): Int = definitions.size

    fun getDefinitions(): List<ActionDefinition> = definitions.values.toList()

    private fun register(actionId: String, vararg params: String) {
        definitions[actionId] = ActionDefinition(actionId, params.toList())
    }
}
