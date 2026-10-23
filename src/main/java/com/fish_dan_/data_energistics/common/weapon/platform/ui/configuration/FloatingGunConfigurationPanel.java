package com.fish_dan_.data_energistics.common.weapon.platform.ui.configuration;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.research.item.ItemResearchUpgrades;
import com.fish_dan_.data_energistics.common.weapon.platform.model.BiochipResearch;
import com.fish_dan_.data_energistics.common.weapon.platform.model.GunAmmunition;
import com.fish_dan_.data_energistics.common.weapon.platform.model.GunForm;
import com.fish_dan_.data_energistics.common.weapon.platform.protocol.GunSnapshot;
import com.fish_dan_.data_energistics.common.weapon.platform.ui.WeaponPlatformStyle;

import com.lowdragmc.lowdraglib2.gui.texture.ColorBorderTexture;
import com.lowdragmc.lowdraglib2.gui.texture.ItemStackTexture;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;

import net.minecraft.network.chat.Component;

import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.function.IntConsumer;

/** Floating-gun-specific circuit layout. Other weapon bindings choose their own component and texture. */
public final class FloatingGunConfigurationPanel extends WeaponConfigurationPanel {

    private final Button[] effects = new Button[4];
    private final UIElement medical = new UIElement();

    public FloatingGunConfigurationPanel(IntConsumer select) {
        UIElement circuit = new UIElement();
        circuit.style(style -> style.backgroundTexture(SpriteTexture.of(Data_Energistics.id("textures/guis/weapon_platform/special_configuration.png"))));
        WeaponPlatformStyle.place(circuit, 31, 9, 64, 64);
        addChildAt(circuit, 0);
        for (int index = 0; index < effects.length; index++) {
            int id = index + 1;
            GunAmmunition effect = GunAmmunition.values()[id];
            Button button = effects[index] = new Button();
            button.noText();
            button.style(style -> style.tooltips(Component.translatable("gui.data_energistics.weapon_platform.ammunition." + effect.name().toLowerCase(Locale.ROOT))));
            button.buttonStyle(style -> style.baseTexture(WeaponPlatformStyle.surface(WeaponPlatformStyle.SECTION))
                    .hoverTexture(WeaponPlatformStyle.surface(WeaponPlatformStyle.SURFACE)));
            UIElement icon = new UIElement();
            icon.setAllowHitTest(false);
            icon.style(style -> style.backgroundTexture(new ItemStackTexture(effect.key.wrapForDisplayOrFilter())));
            WeaponPlatformStyle.place(icon, 1, 1, 16, 16);
            button.addChild(icon);
            button.setOnClick(event -> select.accept(id));
            WeaponPlatformStyle.place(button, 26 + index % 2 * 54, 5 + index / 2 * 56, 18, 18);
            addChild(button);
        }
        medical.style(style -> style.backgroundTexture(new ItemStackTexture(GunAmmunition.BINARY.key.wrapForDisplayOrFilter()))
                .tooltips(Component.translatable("gui.data_energistics.weapon_platform.medical_ammunition")));
        WeaponPlatformStyle.place(medical, 26, 5, 18, 18);
        addChild(medical);
    }

    @Override
    public void bind(@Nullable GunSnapshot gun) {
        super.bind(gun);
        boolean healing = gun != null && GunForm.of(gun.item()) == GunForm.MEDICAL;
        boolean expanded = gun != null && ItemResearchUpgrades.of(gun.item()).ids().contains(BiochipResearch.AMMUNITION);
        for (int index = 0; index < effects.length; index++) {
            effects[index].setDisplay(gun != null && !healing && (index < 2 || expanded));
            int border = gun != null && gun.settings().ammunition().ordinal() == index + 1 ? WeaponPlatformStyle.EDGE : WeaponPlatformStyle.MUTED;
            effects[index].style(style -> style.overlayTexture(new ColorBorderTexture(-1, border)));
        }
        medical.setDisplay(healing);
    }
}
