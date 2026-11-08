package com.fish_dan_.data_energistics.core.mixins.normal.net.minecraft.world.inventory;

import com.fish_dan_.data_energistics.common.menu.patternencoding.PatternEncodingPreferenceMenu;
import com.fish_dan_.data_energistics.common.menu.patternencoding.PatternEncodingPreferenceSession;
import com.fish_dan_.data_energistics.common.weapon.platform.equipment.protection.EquipmentMutation;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.jspecify.annotations.NullMarked;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerMenu.class)
@NullMarked
public abstract class AbstractContainerMenuMixin {

    @WrapMethod(method = "clicked")
    private void dataEnergistics$equipmentAction(int slot, int button, ClickType type, Player player, Operation<Void> original) {
        try (var ignored = new EquipmentMutation(player)) {
            original.call(slot, button, type, player);
        }
    }

    @Inject(method = "removed", at = @At("HEAD"))
    private void dataEnergistics$clearPatternPreferenceSession(Player player, CallbackInfo ci) {
        AbstractContainerMenu removedMenu = (AbstractContainerMenu) (Object) this;
        boolean temporaryClientScreenChange = player.level().isClientSide() && player.containerMenu == removedMenu;
        if (this instanceof PatternEncodingPreferenceMenu && !temporaryClientScreenChange) {
            PatternEncodingPreferenceSession.clearForMenu(this);
        }
    }
}
