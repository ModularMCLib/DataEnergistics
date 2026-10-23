package com.fish_dan_.data_energistics.common.weapon.platform.ui.configuration;

import com.fish_dan_.data_energistics.common.research.item.ItemResearchUpgrades;
import com.fish_dan_.data_energistics.common.weapon.platform.model.BiochipResearch;
import com.fish_dan_.data_energistics.common.weapon.platform.model.GunAmmunition;
import com.fish_dan_.data_energistics.common.weapon.platform.model.GunForm;
import com.fish_dan_.data_energistics.common.weapon.platform.protocol.GunSnapshot;

import com.lowdragmc.lowdraglib2.gui.texture.ItemStackTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;

import net.minecraft.network.chat.Component;

import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.function.IntConsumer;

/** Floating-gun-specific circuit layout. Other weapon bindings choose their own component and texture. */
public final class FloatingGunConfigurationPanel extends WeaponConfigurationPanel {

    private final Button[] effects = new Button[4];
    private final UIElement[] effectFrames = new UIElement[4];
    private final UIElement medical = new UIElement();

    public FloatingGunConfigurationPanel(IntConsumer select) {
        addClass("platform-floating-gun-configuration");
        UIElement circuitLayer = new UIElement();
        circuitLayer.addClass("platform-gun-circuit-layer");
        circuitLayer.setAllowHitTest(false);
        for (String part : new String[] { "outer", "inner" }) {
            UIElement frame = new UIElement();
            frame.addClass("platform-gun-frame");
            frame.addClass("platform-gun-frame-" + part);
            frame.setAllowHitTest(false);
            circuitLayer.addChild(frame);
        }
        UIElement circuit = new UIElement();
        circuit.addClass("platform-gun-circuit");
        circuit.setAllowHitTest(false);
        circuitLayer.addChild(circuit);
        UIElement previewFrame = new UIElement();
        previewFrame.addClass("platform-gun-preview-frame");
        previewFrame.setAllowHitTest(false);
        circuitLayer.addChild(previewFrame);
        addChildAt(circuitLayer, 0);
        for (int index = 0; index < effects.length; index++) {
            int id = index + 1;
            GunAmmunition effect = GunAmmunition.values()[id];
            UIElement frame = effectFrames[index] = new UIElement();
            frame.addClass("platform-ammunition-frame");
            frame.addClass(index % 2 == 0 ? "platform-ammunition-frame-left" : "platform-ammunition-frame-right");
            frame.addClass(index < 2 ? "platform-ammunition-frame-top" : "platform-ammunition-frame-bottom");
            frame.setAllowHitTest(false);
            for (String axis : new String[] { "horizontal", "vertical" }) {
                UIElement joint = new UIElement();
                joint.addClass("platform-ammunition-joint");
                joint.addClass("platform-ammunition-joint-" + axis);
                joint.setAllowHitTest(false);
                frame.addChild(joint);
            }
            circuitLayer.addChild(frame);
            Button button = effects[index] = new Button();
            button.noText();
            button.style(style -> style.tooltips(Component.translatable("gui.data_energistics.weapon_platform.ammunition." + effect.name().toLowerCase(Locale.ROOT))));
            button.addClass("platform-ammunition");
            button.addClass(index % 2 == 0 ? "platform-ammunition-left" : "platform-ammunition-right");
            button.addClass(index < 2 ? "platform-ammunition-top" : "platform-ammunition-bottom");
            UIElement icon = new UIElement();
            icon.setAllowHitTest(false);
            icon.style(style -> style.backgroundTexture(new ItemStackTexture(effect.key.wrapForDisplayOrFilter())));
            icon.addClass("platform-ammunition-icon");
            button.addChild(icon);
            button.setOnClick(event -> select.accept(id));
            addChild(button);
        }
        medical.style(style -> style.backgroundTexture(new ItemStackTexture(GunAmmunition.BINARY.key.wrapForDisplayOrFilter()))
                .tooltips(Component.translatable("gui.data_energistics.weapon_platform.medical_ammunition")));
        medical.addClass("platform-medical-ammunition");
        addChild(medical);
    }

    @Override
    public void bind(@Nullable GunSnapshot gun) {
        super.bind(gun);
        boolean healing = gun != null && GunForm.of(gun.item()) == GunForm.MEDICAL;
        boolean expanded = gun != null && ItemResearchUpgrades.of(gun.item()).ids().contains(BiochipResearch.AMMUNITION);
        for (int index = 0; index < effects.length; index++) {
            boolean available = gun != null && !healing && (index < 2 || expanded);
            effects[index].setDisplay(available);
            effectFrames[index].setDisplay(available || healing && index == 0);
            if (gun != null && gun.settings().ammunition().ordinal() == index + 1) effects[index].addClass("selected");
            else effects[index].removeClass("selected");
        }
        medical.setDisplay(healing);
    }
}
