package com.fish_dan_.data_energistics.core.mixins.normal.appeng.menu;

import com.fish_dan_.data_energistics.api.registry.provider.menu.PatternProviderRedstoneMenu;
import com.fish_dan_.data_energistics.common.item.depot.DigitalStorageDepotBlockItem;
import com.fish_dan_.data_energistics.common.menu.patternencoding.BlankPatternProxyMenu;
import com.fish_dan_.data_energistics.common.menu.patternprovider.registration.PatternProviderMenuAccess;
import com.fish_dan_.data_energistics.common.menu.patternprovider.registration.PatternProviderMenus;
import com.fish_dan_.data_energistics.config.DataEnergisticsConfiguration;
import com.fish_dan_.data_energistics.integration.ae.ae2.menu.ContainerSlotInteraction;

import appeng.api.behaviors.ContainerItemStrategies;
import appeng.api.stacks.GenericStack;
import appeng.helpers.InventoryAction;
import appeng.menu.AEBaseMenu;
import appeng.menu.SlotSemantic;
import appeng.menu.SlotSemantics;
import appeng.menu.me.items.PatternEncodingTermMenu;
import appeng.menu.slot.FakeSlot;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Consumer;

@Mixin(AEBaseMenu.class)
@NullMarked
public abstract class AEBaseMenuMixin extends AbstractContainerMenu implements PatternProviderMenuAccess {

    @Unique
    private @Nullable PatternProviderRedstoneMenu dataEnergistics$providerRedstoneMenu;

    protected AEBaseMenuMixin(@Nullable MenuType<?> menuType, int containerId) {
        super(menuType, containerId);
    }

    @Shadow
    protected abstract Slot addSlot(Slot slot, SlotSemantic semantic);

    @Shadow
    protected abstract <T> void registerClientAction(String name, Class<T> argClass, Consumer<T> handler);

    @Shadow
    protected abstract <T> void sendClientAction(String action, T arg);

    @Override
    public void dataEnergistics$initializeProviderMenu() {
        dataEnergistics$providerRedstoneMenu = PatternProviderMenus.create((AEBaseMenu) (Object) this);
    }

    @Override
    public @Nullable PatternProviderRedstoneMenu dataEnergistics$providerRedstoneMenu() {
        return dataEnergistics$providerRedstoneMenu;
    }

    @Override
    public void dataEnergistics$addProviderSlot(Slot slot, SlotSemantic semantic) {
        addSlot(slot, semantic);
    }

    @Override
    public void dataEnergistics$addProviderDataSlot(DataSlot slot) {
        addDataSlot(slot);
    }

    @Override
    public <T> void dataEnergistics$registerProviderAction(String name, Class<T> argumentType, Consumer<T> action) {
        registerClientAction(name, argumentType, action);
    }

    @Override
    public <T> void dataEnergistics$sendProviderAction(String name, T argument) {
        sendClientAction(name, argument);
    }

    @Shadow
    public abstract Player getPlayer();

    @Inject(method = "isValidForSlot", at = @At("HEAD"), cancellable = true)
    private void dataEnergistics$preventBlankPatternSlotInsertion(Slot s, ItemStack i,
                                                                  CallbackInfoReturnable<Boolean> cir) {
        var menu = (AEBaseMenu) (Object) this;
        if (DataEnergisticsConfiguration.INSTANCE.integration.ae2.patternEncoding.blankPatternSource.usesNetworkStorage() &&
                menu instanceof PatternEncodingTermMenu patternEncodingTermMenu &&
                patternEncodingTermMenu instanceof BlankPatternProxyMenu &&
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
    private void dataEnergistics$quickMoveContainerContent(Player player, int idx,
                                                           CallbackInfoReturnable<ItemStack> cir) {
        if (ContainerSlotInteraction.tryQuickMove((AEBaseMenu) (Object) this, idx, player)) {
            cir.setReturnValue(ItemStack.EMPTY);
        }
    }

    @Inject(method = "handleFakeSlotAction", at = @At("HEAD"), cancellable = true)
    private void dataEnergistics$markDigitalStorageDepotFakeSlot(FakeSlot fakeSlot, InventoryAction action,
                                                                 CallbackInfo ci) {
        if (action != InventoryAction.EMPTY_ITEM && action != InventoryAction.EMPTY_ENTIRE_ITEM) {
            return;
        }

        ItemStack carried = getCarried();
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
