package com.fish_dan_.data_energistics.common.weapon.platform.ui.configuration;

import com.fish_dan_.data_energistics.common.weapon.platform.protocol.GunSnapshot;
import com.fish_dan_.data_energistics.common.weapon.platform.ui.WeaponPlatformStyle;

import com.lowdragmc.lowdraglib2.gui.texture.ItemStackTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;

import net.minecraft.world.item.ItemStack;

import org.jspecify.annotations.Nullable;

/** Default weapon configuration component. Subtypes add their own controls while reusing the item preview. */
public class WeaponConfigurationPanel extends UIElement {

    private final ItemStackTexture model = new ItemStackTexture();

    public WeaponConfigurationPanel() {
        layout(style -> style.width(126).height(82));
        UIElement preview = new UIElement();
        preview.style(style -> style.backgroundTexture(model));
        WeaponPlatformStyle.place(preview, 48, 26, 30, 30);
        addChild(preview);
    }

    /** Menu-thread update; absent selection clears the preview. No inventory or server state is changed here. */
    public void bind(@Nullable GunSnapshot gun) {
        model.setItems(gun == null ? ItemStack.EMPTY : gun.item());
    }
}
