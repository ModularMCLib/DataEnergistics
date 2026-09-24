package com.fish_dan_.data_energistics.mixin.ae.ae2.menu.base;

import com.fish_dan_.data_energistics.ae2.menu.ContainerSlotInteraction;
import com.fish_dan_.data_energistics.item.depot.DigitalStorageDepotBlockItem;
import com.fish_dan_.data_energistics.menu.patternencoding.BlankPatternProxyMenu;

import appeng.api.behaviors.ContainerItemStrategies;
import appeng.api.stacks.GenericStack;
import appeng.helpers.InventoryAction;
import appeng.menu.AEBaseMenu;
import appeng.menu.SlotSemantics;
import appeng.menu.me.items.PatternEncodingTermMenu;
import appeng.menu.slot.FakeSlot;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AEBaseMenu.class)
public abstract class AEBaseMenuMixin {

    @Shadow
    public abstract Player getPlayer();

    @Inject(method = "isValidForSlot", at = @At("HEAD"), cancellable = true)
    private void dataEnergistics$preventBlankPatternSlotInsertion(Slot s, ItemStack i,
                                                                  CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof BlankPatternProxyMenu proxyMenu &&
                proxyMenu.data_energistics$usesNetworkBackedBlankPatternSlot() &&
                (Object) this instanceof PatternEncodingTermMenu patternEncodingTermMenu &&
                patternEncodingTermMenu.getSlotSemantic(s) == SlotSemantics.BLANK_PATTERN &&
                !s.hasItem()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "clicked", at = @At("HEAD"), cancellable = true)
    private void dataEnergistics$transferContainerContent(int slotId, int button, ClickType clickType, Player player,
                                                          CallbackInfo ci) {
        if (ContainerSlotInteraction.tryClicked((AEBaseMenu) (Object) this, slotId, button, clickType, player)) {
            ci.cancel();
        }
    }

    @Inject(method = "quickMoveStack", at = @At("HEAD"), cancellable = true)
    private void dataEnergistics$quickMoveContainerContent(Player player, int slotIndex,
                                                           CallbackInfoReturnable<ItemStack> cir) {
        if (ContainerSlotInteraction.tryQuickMove((AEBaseMenu) (Object) this, slotIndex, player)) {
            cir.setReturnValue(ItemStack.EMPTY);
        }
    }

    @Inject(method = "handleFakeSlotAction", at = @At("HEAD"), cancellable = true)
    private void dataEnergistics$markDigitalStorageDepotFakeSlot(FakeSlot fakeSlot, InventoryAction action,
                                                                 CallbackInfo ci) {
        if (action != InventoryAction.EMPTY_ITEM && action != InventoryAction.EMPTY_ENTIRE_ITEM) {
            return;
        }

        ItemStack carried = ((AbstractContainerMenu) (Object) this).getCarried();
        var emptyingAction = ContainerItemStrategies.getEmptyingAction(carried);
        GenericStack markedStack = emptyingAction == null ? null : new GenericStack(emptyingAction.what(), emptyingAction.maxAmount());
        if (markedStack == null && DigitalStorageDepotBlockItem.isDepotStack(carried)) {
            markedStack = DigitalStorageDepotBlockItem.getSelectedMarkedStack(
                    carried,
                    this.getPlayer().level().registryAccess());
        }
        if (markedStack == null || markedStack.what() == null || markedStack.amount() <= 0) {
            return;
        }

        ItemStack filter = GenericStack.wrapInItemStack(markedStack.what(), markedStack.amount());
        if (!fakeSlot.canSetFilterTo(filter)) {
            return;
        }

        fakeSlot.set(filter);
        ci.cancel();
    }
}
