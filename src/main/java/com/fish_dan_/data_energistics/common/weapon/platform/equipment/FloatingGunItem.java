package com.fish_dan_.data_energistics.common.weapon.platform.equipment;

import com.fish_dan_.data_energistics.common.weapon.platform.model.GunForm;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

public final class FloatingGunItem extends Item {

    public FloatingGunItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("gun_form.data_energistics." + GunForm.of(stack).id));
    }
}
