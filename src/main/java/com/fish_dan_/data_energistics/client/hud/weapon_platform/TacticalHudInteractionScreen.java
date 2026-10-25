package com.fish_dan_.data_energistics.client.hud.weapon_platform;

import com.fish_dan_.data_energistics.client.registry.DEKeyMappings;
import com.fish_dan_.data_energistics.client.weapon.platform.PlatformClientState;

import com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import org.lwjgl.glfw.GLFW;

/** Native LDLib2 screen owns focus and cursor capture while the same HUD widgets accept input. */
public final class TacticalHudInteractionScreen extends ModularUIScreen {

    public TacticalHudInteractionScreen() {
        super(TacticalHudLayer.interactiveUi(), Component.translatable("hud.data_energistics.weapon_platform.interaction"));
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key == GLFW.GLFW_KEY_ESCAPE || DEKeyMappings.INTERACT_TACTICAL_HUD.matches(key, scanCode)) {
            onClose();
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public void tick() {
        if (!PlatformClientState.snapshot.hud() || Minecraft.getInstance().options.hideGui) onClose();
        else super.tick();
    }
}
