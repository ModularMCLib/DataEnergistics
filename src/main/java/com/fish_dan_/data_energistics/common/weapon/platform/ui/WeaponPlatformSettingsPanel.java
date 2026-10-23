package com.fish_dan_.data_energistics.common.weapon.platform.ui;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.api.gui.style.SharedBackpackStyles;
import com.fish_dan_.data_energistics.client.weapon.platform.PlatformClientState;
import com.fish_dan_.data_energistics.common.weapon.platform.model.BiochipResearch;
import com.fish_dan_.data_energistics.common.weapon.platform.network.PlatformRpc;
import com.fish_dan_.data_energistics.common.weapon.platform.protocol.PlatformSnapshot;
import com.fish_dan_.data_energistics.common.weapon.platform.storage.WeaponPlatformInventory;

import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.networking.rpc.RPCPacketDistributor;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.ItemHandlerCopySlot;

import static com.fish_dan_.data_energistics.common.weapon.platform.ui.WeaponPlatformStyle.*;

/** Platform-wide options share one compact, inset column; weapon-specific options remain in the bound component. */
final class WeaponPlatformSettingsPanel extends UIElement {

    private final Label capacity = new Label();
    private final Label cooling = new Label();
    private final Label hud = new Label();
    private final Button appearance = new Button();
    private final WeaponPlatformSwitch[] targets = new WeaponPlatformSwitch[5];
    private final WeaponPlatformSwitch protection;
    private final boolean client;

    WeaponPlatformSettingsPanel(Player player, IItemHandlerModifiable inventory) {
        client = player.level().isClientSide;
        panel(this, 0, 20, 73, 170, SECTION);
        panel(this, 0, 0, 73, 22, SURFACE);
        Label title = label(new Label(), Component.translatable("gui.data_energistics.weapon_platform.platform_configuration"), 4, 3, 46, 16, 7);
        title.textStyle(style -> style.textAlignHorizontal(Horizontal.CENTER));
        ItemSlot chip = new ItemSlot(new ItemHandlerCopySlot(inventory, WeaponPlatformInventory.CHIP_SLOT, 0, 0));
        SharedBackpackStyles.standalone(chip);
        place(chip, 54, 2, 18, 18);
        chip.style(style -> style.tooltips(Component.translatable("gui.data_energistics.weapon_platform.chip")));
        addChild(chip);

        label(capacity, Component.empty(), 8, 28, 57, 9, 6);
        label(cooling, Component.empty(), 8, 39, 57, 9, 6);
        label(hud, Component.empty(), 8, 50, 35, 11, 6);
        appearance.setText(Component.translatable("gui.data_energistics.weapon_platform.display_settings"));
        appearance.text.textStyle(style -> style.fontSize(6).textColor(TEXT).textShadow(false));
        appearance.buttonStyle(style -> style
                .baseTexture(SpriteTexture.of(Data_Energistics.id("textures/guis/states/small_top_surface/small_button_0.png")).setSprite(0, 0, 10, 10).setBorder(2))
                .hoverTexture(SpriteTexture.of(Data_Energistics.id("textures/guis/states/small_top_surface/small_button_1.png")).setSprite(0, 0, 10, 10).setBorder(2))
                .pressedTexture(SpriteTexture.of(Data_Energistics.id("textures/guis/states/small_top_surface/small_button_2.png")).setSprite(0, 0, 10, 10).setBorder(2)));
        appearance.style(style -> style.tooltips(Component.translatable("hud.data_energistics.weapon_platform.appearance")));
        appearance.setOnClick(event -> { if (client) PlatformClientState.openAppearance(); });
        place(appearance, 46, 50, 19, 11);
        addChild(appearance);

        String[] categories = { "players", "hostile", "friendly", "neutral", "other" };
        for (int index = 0; index < targets.length; index++) {
            targets[index] = setting("targets." + categories[index], 70 + index * 12, PlatformRpc.TARGETS, 1 << index);
        }
        protection = setting("protection", 136, PlatformRpc.PROTECTION, 0);
    }

    void bind(PlatformSnapshot value) {
        capacity.setValue(Component.translatable("gui.data_energistics.weapon_platform.capacity", value.capacity()));
        cooling.setValue(Component.translatable("gui.data_energistics.weapon_platform.cooling", BiochipResearch.cooling(value.chip())));
        hud.setValue(Component.translatable("gui.data_energistics.weapon_platform.hud", value.hud() ? "✓" : "—"));
        appearance.setActive(value.hud());
        for (int index = 0; index < targets.length; index++) targets[index].setOn((value.targetMask() & 1 << index) != 0, false);
        protection.setOn(value.friendlyProtection(), false);
    }

    private WeaponPlatformSwitch setting(String key, int top, int operation, int argument) {
        Button row = new Button();
        row.noText();
        row.buttonStyle(style -> style.baseTexture(IGuiTexture.EMPTY).hoverTexture(IGuiTexture.EMPTY).pressedTexture(IGuiTexture.EMPTY));
        place(row, 8, top, 57, 11);
        WeaponPlatformSwitch toggle = new WeaponPlatformSwitch();
        place(toggle, 0, 2, 13, 7);
        toggle.setOnSwitchChanged(on -> { if (client) RPCPacketDistributor.rpcToServer(PlatformRpc.REQUEST, operation, 0, argument); });
        row.setOnClick(event -> toggle.setOn(!toggle.isOn(), true));
        Component text = Component.translatable("gui.data_energistics.weapon_platform." + key);
        row.style(style -> style.tooltips(text));
        Label label = new Label();
        label.setValue(text);
        label.setAllowHitTest(false);
        label.textStyle(style -> style.fontSize(6).textColor(TEXT).textShadow(false).adaptiveWidth(false).adaptiveHeight(false).textAlignVertical(Vertical.CENTER));
        place(label, 17, 0, 40, 11);
        row.addChildren(toggle, label);
        addChild(row);
        return toggle;
    }

    private Label label(Label label, Component text, int x, int y, int width, int height, int size) {
        label.setValue(text);
        label.setAllowHitTest(false);
        label.textStyle(style -> style.fontSize(size).textColor(TEXT).textShadow(false).adaptiveWidth(false).adaptiveHeight(false).textAlignVertical(Vertical.CENTER));
        place(label, x, y, width, height);
        addChild(label);
        return label;
    }
}
