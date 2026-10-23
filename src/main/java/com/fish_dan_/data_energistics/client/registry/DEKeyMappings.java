package com.fish_dan_.data_energistics.client.registry;

import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.settings.KeyConflictContext;

import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

public final class DEKeyMappings {

    public static final String KEY_CATEGORY = "key.categories.data_energistics";
    public static final KeyMapping INTERACT_TACTICAL_HUD = new KeyMapping("key.data_energistics.interact_tactical_hud", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J, KEY_CATEGORY);
    public static final KeyMapping OPEN_WEAPON_PLATFORM = new KeyMapping("key.data_energistics.open_weapon_platform", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, KEY_CATEGORY);
    public static final KeyMapping LOCK_WEAPON_PLATFORM_TARGET = new KeyMapping("key.data_energistics.lock_weapon_platform_target", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, KEY_CATEGORY);
    public static final KeyMapping OPEN_PATTERN_PROVIDER = new KeyMapping(
            "key.data_energistics.open_pattern_provider",
            InputConstants.Type.MOUSE,
            GLFW.GLFW_MOUSE_BUTTON_MIDDLE,
            KEY_CATEGORY);
    public static final KeyMapping RENAME_PATTERN_PROVIDER = new KeyMapping(
            "key.data_energistics.rename_pattern_provider",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_M,
            KEY_CATEGORY);
    public static final KeyMapping TOGGLE_DIGITAL_STORAGE_DEPOT_BUCKET_MODE = new KeyMapping(
            "key.data_energistics.toggle_digital_storage_depot_bucket_mode",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_Z,
            KEY_CATEGORY);
    public static final KeyMapping OPEN_ORBITAL_CONTROL = new KeyMapping(
            "key.data_energistics.open_orbital_control",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_O,
            KEY_CATEGORY);
    public static final KeyMapping TOGGLE_ORBITAL_CONTROL_HUD = new KeyMapping(
            "key.data_energistics.toggle_orbital_control_hud",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_H,
            KEY_CATEGORY);
    public static final KeyMapping MOVE_PLAYER_TERMINAL_TO_MOUSE = new KeyMapping(
            "key.data_energistics.move_player_terminal_to_mouse",
            KeyConflictContext.GUI,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_UNKNOWN,
            KEY_CATEGORY);
    public static final KeyMapping PREVIOUS_PAGE = new KeyMapping(
            "key.data_energistics.previous_page",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_LEFT_BRACKET,
            KEY_CATEGORY);
    public static final KeyMapping NEXT_PAGE = new KeyMapping(
            "key.data_energistics.next_page",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_RIGHT_BRACKET,
            KEY_CATEGORY);
    public static final KeyMapping TOGGLE_CROSSBOW_RAIL = new KeyMapping(
            "key.data_energistics.toggle_crossbow_rail",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_V,
            KEY_CATEGORY);
    public static final KeyMapping TOGGLE_CROSSBOW_ARMS = new KeyMapping(
            "key.data_energistics.toggle_crossbow_arms",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_B,
            KEY_CATEGORY);

    private DEKeyMappings() {}
}
