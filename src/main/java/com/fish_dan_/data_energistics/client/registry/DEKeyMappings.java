package com.fish_dan_.data_energistics.client.registry;

import com.fish_dan_.data_energistics.util.input.KeyChordMapping;

import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;

import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

public final class DEKeyMappings {

    public static final String KEY_CATEGORY = "key.categories.data_energistics";
    public static final KeyChordMapping TOGGLE_PLATFORM_DOMAIN = new KeyChordMapping("key.data_energistics.toggle_platform_domain", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, KEY_CATEGORY);
    public static final KeyChordMapping TOGGLE_PLATFORM_PROSPECTING = new KeyChordMapping("key.data_energistics.toggle_platform_prospecting", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, KEY_CATEGORY);
    public static final KeyChordMapping INTERACT_TACTICAL_HUD = new KeyChordMapping("key.data_energistics.interact_tactical_hud", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J, KEY_CATEGORY);
    public static final KeyChordMapping OPEN_WEAPON_PLATFORM = new KeyChordMapping("key.data_energistics.open_weapon_platform", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, KEY_CATEGORY);
    public static final KeyChordMapping LOCK_WEAPON_PLATFORM_TARGET = new KeyChordMapping("key.data_energistics.lock_weapon_platform_target", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, KEY_CATEGORY);
    public static final KeyChordMapping RALLY_WEAPON_PLATFORM = new KeyChordMapping("key.data_energistics.rally_weapon_platform", KeyConflictContext.IN_GAME, KeyModifier.ALT,
            InputConstants.Type.MOUSE, GLFW.GLFW_MOUSE_BUTTON_LEFT, KEY_CATEGORY);
    public static final KeyChordMapping OPEN_PATTERN_PROVIDER = new KeyChordMapping(
            "key.data_energistics.open_pattern_provider",
            InputConstants.Type.MOUSE,
            GLFW.GLFW_MOUSE_BUTTON_MIDDLE,
            KEY_CATEGORY);
    public static final KeyChordMapping RENAME_PATTERN_PROVIDER = new KeyChordMapping(
            "key.data_energistics.rename_pattern_provider",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_M,
            KEY_CATEGORY);
    public static final KeyChordMapping TOGGLE_DIGITAL_STORAGE_DEPOT_BUCKET_MODE = new KeyChordMapping(
            "key.data_energistics.toggle_digital_storage_depot_bucket_mode",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_Z,
            KEY_CATEGORY);
    public static final KeyChordMapping OPEN_ORBITAL_CONTROL = new KeyChordMapping(
            "key.data_energistics.open_orbital_control",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_O,
            KEY_CATEGORY);
    public static final KeyChordMapping TOGGLE_ORBITAL_CONTROL_HUD = new KeyChordMapping(
            "key.data_energistics.toggle_orbital_control_hud",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_H,
            KEY_CATEGORY);
    public static final KeyChordMapping MOVE_PLAYER_TERMINAL_TO_MOUSE = new KeyChordMapping(
            "key.data_energistics.move_player_terminal_to_mouse",
            KeyConflictContext.GUI,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_UNKNOWN,
            KEY_CATEGORY);
    public static final KeyChordMapping PREVIOUS_PAGE = new KeyChordMapping(
            "key.data_energistics.previous_page",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_LEFT_BRACKET,
            KEY_CATEGORY);
    public static final KeyChordMapping NEXT_PAGE = new KeyChordMapping(
            "key.data_energistics.next_page",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_RIGHT_BRACKET,
            KEY_CATEGORY);
    public static final KeyChordMapping TOGGLE_CROSSBOW_RAIL = new KeyChordMapping(
            "key.data_energistics.toggle_crossbow_rail",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_V,
            KEY_CATEGORY);
    public static final KeyChordMapping TOGGLE_CROSSBOW_ARMS = new KeyChordMapping(
            "key.data_energistics.toggle_crossbow_arms",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_B,
            KEY_CATEGORY);

    private DEKeyMappings() {}
}
