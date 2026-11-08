package com.fish_dan_.data_energistics.client.hud.weapon_platform.weapon;

import com.fish_dan_.data_energistics.client.gui.GenericStackDisplayHelper;
import com.fish_dan_.data_energistics.common.weapon.platform.model.GunAmmunition;
import com.fish_dan_.data_energistics.common.weapon.platform.model.GunForm;
import com.fish_dan_.data_energistics.common.weapon.platform.protocol.GunSnapshot;
import com.fish_dan_.data_energistics.common.weapon.platform.protocol.PlatformNetworkSnapshot;
import com.fish_dan_.data_energistics.common.weapon.platform.ui.GunHeatBar;
import com.fish_dan_.data_energistics.config.client.hud.HudModuleStyle;

import com.lowdragmc.lowdraglib2.gui.texture.ItemStackTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;

import net.minecraft.network.chat.Component;

import lombok.Getter;

import java.util.Locale;

/** One occupied platform slot; drag ownership and ordering belong to the surrounding columns. */
public final class TacticalWeaponRow extends UIElement {

    @Getter
    private final ItemStackTexture model = new ItemStackTexture();
    private final GunHeatBar heat = new GunHeatBar();
    private final Label name = new Label();
    private final Label ammunition = new Label();

    public TacticalWeaponRow() {
        addClass("platform-hud-weapon-row");
        UIElement preview = new UIElement();
        preview.style(style -> style.backgroundTexture(model));
        preview.setAllowHitTest(false);
        preview.addClass("platform-hud-weapon-icon");
        heat.addClass("platform-hud-weapon-heat");
        name.addClasses("platform-hud-label", "platform-hud-weapon-name");
        name.setAllowHitTest(false);
        ammunition.addClasses("platform-hud-label", "platform-hud-weapon-ammunition");
        ammunition.setAllowHitTest(false);
        addChildren(preview, heat, name, ammunition);
    }

    public void refresh(GunSnapshot gun, PlatformNetworkSnapshot network, HudModuleStyle fields, int textColor) {
        model.setItems(gun.item());
        heat.setDisplay(fields.shows(HudModuleStyle.HEAT));
        heat.setHeat(gun.heat(), gun.overheated());
        var title = Component.literal((gun.slot() + 1) + " ");
        if (fields.shows(HudModuleStyle.NAME)) title.append(gun.item().getHoverName());
        name.setValue(title);
        name.textStyle(style -> style.textColor(textColor).textShadow(true));
        ammunition.textStyle(style -> style.textColor(textColor).textShadow(true));
        boolean showAmmunition = fields.shows(HudModuleStyle.AMMUNITION);
        ammunition.setDisplay(showAmmunition);
        if (showAmmunition) {
            GunForm form = GunForm.of(gun.item());
            GunAmmunition ammo = GunAmmunition.values()[gun.actualAmmunition()];
            long shots = (long) (network.stored() / form.energy);
            if (ammo.key != null) {
                long special = network.ammunition().getLong(ammo.ordinal() - 1) / ammo.amount;
                if (special == 0 && form != GunForm.MEDICAL) ammo = GunAmmunition.AE;
                else shots = Math.min(shots, special);
            }
            ammunition.setValue(Component.translatable("hud.data_energistics.weapon_platform.ammunition." + ammo.name().toLowerCase(Locale.ROOT))
                    .append(" ").append(GenericStackDisplayHelper.formatCompactAmount(shots)));
        }
    }
}
