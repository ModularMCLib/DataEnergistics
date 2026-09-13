package com.fish_dan_.data_energistics.mixin.core.menu.sanctum;

import com.fish_dan_.data_energistics.ae2.sanctum.DataSanctumInterfaceInventory;
import com.fish_dan_.data_energistics.ae2.sanctum.DataSanctumLargeInterfaceHost;
import com.fish_dan_.data_energistics.api.registry.connector.ConnectorPolicy;
import com.fish_dan_.data_energistics.menu.sanctum.SetStockAmountMenuAccess;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.helpers.InterfaceLogicHost;
import appeng.menu.AEBaseMenu;
import appeng.menu.implementations.SetStockAmountMenu;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds a per-configuration-slot unlimited flag to AE2's stock amount menu. */
@Mixin(SetStockAmountMenu.class)
public abstract class SetStockAmountMenuMixin extends AEBaseMenu implements SetStockAmountMenuAccess {

    @Shadow
    @Final
    private InterfaceLogicHost host;
    @Shadow
    private int slot;

    @Unique
    private boolean dataEnergistics$unlimited;
    @Unique
    private boolean dataEnergistics$stateInitialized;
    @Unique
    private int dataEnergistics$policy = ConnectorPolicy.ROUND_ROBIN.ordinal();
    @Unique
    private long dataEnergistics$initialAmount;

    protected SetStockAmountMenuMixin(MenuType<?> menuType, int id, Inventory inventory, Object host) {
        super(menuType, id, inventory, host);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void dataEnergistics$registerUnlimitedAction(int id, Inventory inventory, InterfaceLogicHost host, CallbackInfo ci) {
        this.registerClientAction("data_energistics_set_unlimited", Boolean.class, this::dataEnergistics$setUnlimited);
        this.registerClientAction("data_energistics_set_policy", Integer.class, this::dataEnergistics$setPolicy);
        this.registerClientAction("data_energistics_confirm_long", Long.class, this::dataEnergistics$confirmLong);
    }

    @Inject(method = "setWhatToStock", at = @At("RETURN"))
    private void dataEnergistics$loadUnlimitedState(int slot, AEKey key, int initialAmount, CallbackInfo ci) {
        if (host instanceof DataSanctumLargeInterfaceHost largeHost && largeHost.getConfig() instanceof DataSanctumInterfaceInventory config) {
            dataEnergistics$unlimited = config.isUnlimitedSlot(slot);
            dataEnergistics$policy = config.getSlotPolicy(slot).ordinal();
            dataEnergistics$initialAmount = config.getFiniteAmount(slot);
            dataEnergistics$stateInitialized = true;
        }
    }

    @Inject(method = "confirm", at = @At("HEAD"), cancellable = true)
    private void dataEnergistics$confirmUnlimited(int amount, CallbackInfo ci) {
        if (dataEnergistics$unlimited && !isClientSide() && host instanceof DataSanctumLargeInterfaceHost largeHost && largeHost.getConfig() instanceof DataSanctumInterfaceInventory config) {
            config.setUnlimitedSlot(slot, true);
            host.returnToMainMenu(getPlayer(), (SetStockAmountMenu) (Object) this);
            ci.cancel();
        }
    }

    @Override
    public boolean dataEnergistics$isUnlimited() {
        dataEnergistics$initializeStateFromConfig();
        return dataEnergistics$unlimited;
    }

    @Unique
    private void dataEnergistics$initializeStateFromConfig() {
        if (!dataEnergistics$stateInitialized && host instanceof DataSanctumLargeInterfaceHost largeHost && largeHost.getConfig() instanceof DataSanctumInterfaceInventory config) {
            dataEnergistics$unlimited = config.isUnlimitedSlot(slot);
            dataEnergistics$policy = config.getSlotPolicy(slot).ordinal();
            dataEnergistics$initialAmount = config.getFiniteAmount(slot);
            dataEnergistics$stateInitialized = true;
        }
    }

    @Override
    public int dataEnergistics$getSlot() {
        return slot;
    }

    @Override
    public void dataEnergistics$setUnlimited(boolean enabled) {
        if (isClientSide()) {
            sendClientAction("data_energistics_set_unlimited", enabled);
            dataEnergistics$unlimited = enabled;
            return;
        }
        if (host instanceof DataSanctumLargeInterfaceHost) {
            dataEnergistics$unlimited = enabled;
            dataEnergistics$stateInitialized = true;
            broadcastChanges();
        }
    }

    @Override
    public int dataEnergistics$getPolicy() {
        dataEnergistics$initializeStateFromConfig();
        return dataEnergistics$policy;
    }

    @Override
    public void dataEnergistics$setPolicy(int ordinal) {
        if (ordinal < 0 || ordinal >= ConnectorPolicy.values().length) {
            return;
        }
        if (isClientSide()) {
            sendClientAction("data_energistics_set_policy", ordinal);
            dataEnergistics$policy = ordinal;
            return;
        }
        if (host instanceof DataSanctumLargeInterfaceHost largeHost && largeHost.getConfig() instanceof DataSanctumInterfaceInventory config) {
            config.setSlotPolicy(slot, ConnectorPolicy.values()[ordinal]);
            dataEnergistics$policy = ordinal;
            broadcastChanges();
        }
    }

    @Override
    public long dataEnergistics$getInitialAmount() {
        dataEnergistics$initializeStateFromConfig();
        return dataEnergistics$initialAmount;
    }

    @Override
    public long dataEnergistics$getFiniteAmount() {
        if (host instanceof DataSanctumLargeInterfaceHost largeHost && largeHost.getConfig() instanceof DataSanctumInterfaceInventory config) {
            return config.getFiniteAmount(slot);
        }
        return 1L;
    }

    @Override
    public void dataEnergistics$confirmLong(long amount) {
        if (isClientSide()) {
            sendClientAction("data_energistics_confirm_long", amount);
            return;
        }
        if (host instanceof DataSanctumLargeInterfaceHost largeHost && largeHost.getConfig() instanceof DataSanctumInterfaceInventory config && config.getKey(slot) != null) {
            if (dataEnergistics$unlimited) {
                config.setUnlimitedSlot(slot, true);
            } else if (amount > 0) {
                config.setUnlimitedSlot(slot, false);
                config.setStack(slot, new GenericStack(config.getKey(slot), amount));
            } else {
                config.setUnlimitedSlot(slot, false);
                config.setStack(slot, null);
            }
            host.returnToMainMenu(getPlayer(), (SetStockAmountMenu) (Object) this);
        }
    }
}
