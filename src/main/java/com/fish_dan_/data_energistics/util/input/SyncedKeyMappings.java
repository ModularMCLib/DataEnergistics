package com.fish_dan_.data_energistics.util.input;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.minecraft.client.Minecraft;
import net.neoforged.fml.ModLoader;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.common.NeoForge;

import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

/** Built-in bindings; registration and callbacks follow GTM's synchronized input lifecycle. */
public final class SyncedKeyMappings {

    // MC keymappings
    public static final SyncedKeyMapping VANILLA_JUMP = SyncedKeyMapping.createFromMC(() -> () -> Minecraft.getInstance().options.keyJump);
    public static final SyncedKeyMapping VANILLA_SNEAK = SyncedKeyMapping.createFromMC(() -> () -> Minecraft.getInstance().options.keyShift);
    public static final SyncedKeyMapping VANILLA_FORWARD = SyncedKeyMapping.createFromMC(() -> () -> Minecraft.getInstance().options.keyUp);
    public static final SyncedKeyMapping VANILLA_BACKWARD = SyncedKeyMapping.createFromMC(() -> () -> Minecraft.getInstance().options.keyDown);
    public static final SyncedKeyMapping VANILLA_LEFT = SyncedKeyMapping.createFromMC(() -> () -> Minecraft.getInstance().options.keyLeft);
    public static final SyncedKeyMapping VANILLA_RIGHT = SyncedKeyMapping.createFromMC(() -> () -> Minecraft.getInstance().options.keyRight);

    public static final SyncedKeyMapping DOMAIN = SyncedKeyMapping.createConfigurable("keybind.data_energistics.domain", KeyConflictContext.IN_GAME, GLFW.GLFW_KEY_UNKNOWN).migrateFrom("key.data_energistics.toggle_platform_domain");
    public static final SyncedKeyMapping PROSPECTING = SyncedKeyMapping.createConfigurable("keybind.data_energistics.prospecting", KeyConflictContext.IN_GAME, GLFW.GLFW_KEY_UNKNOWN).migrateFrom("key.data_energistics.toggle_platform_prospecting");
    public static final SyncedKeyMapping TACTICAL_HUD = SyncedKeyMapping.createConfigurable("keybind.data_energistics.tactical_hud", KeyConflictContext.UNIVERSAL, InputConstants.KEY_J).migrateFrom("key.data_energistics.interact_tactical_hud");
    public static final SyncedKeyMapping PLATFORM = SyncedKeyMapping.createConfigurable("keybind.data_energistics.platform", KeyConflictContext.IN_GAME, InputConstants.KEY_K).migrateFrom("key.data_energistics.open_weapon_platform");
    public static final SyncedKeyMapping TARGET_LOCK = SyncedKeyMapping.createConfigurable("keybind.data_energistics.target_lock", KeyConflictContext.IN_GAME, InputConstants.KEY_R).migrateFrom("key.data_energistics.lock_weapon_platform_target");
    public static final SyncedKeyMapping RALLY = SyncedKeyMapping.createMouse("keybind.data_energistics.rally", KeyConflictContext.IN_GAME, KeyChordModifiers.ALT, GLFW.GLFW_MOUSE_BUTTON_LEFT).migrateFrom("key.data_energistics.rally_weapon_platform");
    public static final SyncedKeyMapping PROVIDER_RENAME = SyncedKeyMapping.createMouse("keybind.data_energistics.provider_rename", KeyConflictContext.GUI, 0, GLFW.GLFW_MOUSE_BUTTON_MIDDLE).migrateFrom("key.data_energistics.rename_pattern_provider");
    public static final SyncedKeyMapping PROVIDER = SyncedKeyMapping.createMouse("keybind.data_energistics.provider", KeyConflictContext.GUI, 0, GLFW.GLFW_MOUSE_BUTTON_MIDDLE).migrateFrom("key.data_energistics.open_pattern_provider");
    public static final SyncedKeyMapping DEPOT_BUCKET = SyncedKeyMapping.createConfigurable("keybind.data_energistics.depot_bucket", KeyConflictContext.IN_GAME, InputConstants.KEY_Z).migrateFrom("key.data_energistics.toggle_digital_storage_depot_bucket_mode");
    public static final SyncedKeyMapping ORBITAL = SyncedKeyMapping.createConfigurable("keybind.data_energistics.orbital", KeyConflictContext.IN_GAME, InputConstants.KEY_O).migrateFrom("key.data_energistics.open_orbital_control");
    public static final SyncedKeyMapping ORBITAL_HUD = SyncedKeyMapping.createConfigurable("keybind.data_energistics.orbital_hud", KeyConflictContext.IN_GAME, InputConstants.KEY_H).migrateFrom("key.data_energistics.toggle_orbital_control_hud");
    public static final SyncedKeyMapping TERMINAL_MOVE = SyncedKeyMapping.createConfigurable("keybind.data_energistics.terminal_move", KeyConflictContext.GUI, GLFW.GLFW_KEY_UNKNOWN).migrateFrom("key.data_energistics.move_player_terminal_to_mouse");
    public static final SyncedKeyMapping PAGE_PREV = SyncedKeyMapping.createConfigurable("keybind.data_energistics.page_prev", KeyConflictContext.GUI, InputConstants.KEY_LBRACKET).migrateFrom("key.data_energistics.previous_page");
    public static final SyncedKeyMapping PAGE_NEXT = SyncedKeyMapping.createConfigurable("keybind.data_energistics.page_next", KeyConflictContext.GUI, InputConstants.KEY_RBRACKET).migrateFrom("key.data_energistics.next_page");
    public static final SyncedKeyMapping CROSSBOW_RAIL = SyncedKeyMapping.createConfigurable("keybind.data_energistics.crossbow_rail", KeyConflictContext.UNIVERSAL, InputConstants.KEY_V).migrateFrom("key.data_energistics.toggle_crossbow_rail");
    public static final SyncedKeyMapping CROSSBOW_ARMS = SyncedKeyMapping.createConfigurable("keybind.data_energistics.crossbow_arms", KeyConflictContext.UNIVERSAL, InputConstants.KEY_B).migrateFrom("key.data_energistics.toggle_crossbow_arms");

    public static void init() {
        if (Data_Energistics.isClientSide()) {
            NeoForge.EVENT_BUS.register(SyncedKeyMapping.class);
        }
        ModLoader.postEvent(new SyncedKeyMappingEvent());
    }

    private SyncedKeyMappings() {}
}
