package com.fish_dan_.data_energistics.common.menu.patternprovider.registration;

import com.fish_dan_.data_energistics.api.registry.provider.menu.PatternProviderRedstoneMenu;
import com.fish_dan_.data_energistics.integration.ae.ae2.accessor.patternprovider.PatternProviderHostAccessor;
import com.fish_dan_.data_energistics.integration.ae.ae2.patternprovider.RedstoneTuningMode;

import appeng.menu.AEBaseMenu;
import appeng.menu.SlotSemantics;
import appeng.menu.slot.RestrictedInputSlot;

import net.minecraft.world.inventory.DataSlot;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/** One menu's upgrade slot, client action and compact vanilla synchronization. */
@NullMarked
public final class RedstoneTuningMenuBinding extends DataSlot implements PatternProviderRedstoneMenu {

    private static final String SET_MODE = "dataEnergistics$setRedstoneTuningMode";
    private static final RedstoneTuningMode[] MODES = RedstoneTuningMode.values();

    private final AEBaseMenu menu;
    private final PatternProviderMenuAccess access;
    private final PatternProviderHostAccessor host;
    private int clientState;

    private RedstoneTuningMenuBinding(AEBaseMenu menu, PatternProviderHostAccessor host) {
        this.menu = menu;
        this.access = (PatternProviderMenuAccess) menu;
        this.host = host;
        access.dataEnergistics$addProviderSlot(new RestrictedInputSlot(
                RestrictedInputSlot.PlacableItemType.UPGRADES,
                host.dataEnergistics$getRedstoneTuningUpgrades(), 0), SlotSemantics.UPGRADE);
        access.dataEnergistics$addProviderDataSlot(this);
        access.dataEnergistics$registerProviderAction(SET_MODE, Integer.class, this::applyMode);
    }

    /** Resolves the host capability once; third-party hosts without it do not receive an extra slot. */
    public static @Nullable PatternProviderRedstoneMenu create(AEBaseMenu menu) {
        return menu.getTarget() instanceof PatternProviderHostAccessor host ? new RedstoneTuningMenuBinding(menu, host) : null;
    }

    @Override
    public int get() {
        return menu.isClientSide() ? clientState :
                (host.dataEnergistics$getRedstoneTuningMode().ordinal() << 1) |
                        (host.dataEnergistics$hasRedstoneTuningCard() ? 1 : 0);
    }

    @Override
    public void set(int value) {
        clientState = value;
    }

    @Override
    public boolean hasRedstoneTuningCard() {
        return (get() & 1) != 0;
    }

    @Override
    public int getRedstoneTuningMode() {
        return get() >>> 1;
    }

    @Override
    public void setRedstoneTuningMode(int ordinal) {
        if (menu.isClientSide()) {
            access.dataEnergistics$sendProviderAction(SET_MODE, ordinal);
        } else {
            applyMode(ordinal);
        }
    }

    private void applyMode(@Nullable Integer ordinal) {
        if (ordinal == null || ordinal < 0 || ordinal >= MODES.length) {
            throw new IllegalArgumentException("Invalid redstone tuning mode: " + ordinal);
        }
        host.dataEnergistics$setRedstoneTuningMode(MODES[ordinal]);
    }
}
