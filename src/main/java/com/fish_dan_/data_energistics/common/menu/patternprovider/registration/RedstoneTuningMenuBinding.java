package com.fish_dan_.data_energistics.common.menu.patternprovider.registration;

import com.fish_dan_.data_energistics.api.registry.provider.menu.PatternProviderRedstoneMenu;
import com.fish_dan_.data_energistics.api.registry.provider.menu.RedstoneTuningMenuHost;
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
    private final RedstoneTuningMenuHost host;
    private int clientState;

    private RedstoneTuningMenuBinding(AEBaseMenu menu, RedstoneTuningMenuHost host) {
        this.menu = menu;
        this.access = (PatternProviderMenuAccess) menu;
        this.host = host;
        access.dataEnergistics$addProviderSlot(new RestrictedInputSlot(
                RestrictedInputSlot.PlacableItemType.UPGRADES,
                host.upgrades(), 0), SlotSemantics.UPGRADE);
        access.dataEnergistics$addProviderDataSlot(this);
        access.dataEnergistics$registerProviderAction(SET_MODE, Integer.class, this::applyMode);
    }

    /** Creates a binding from a registered host wrapper after native construction. */
    public static PatternProviderRedstoneMenu create(AEBaseMenu menu, RedstoneTuningMenuHost host) {
        return new RedstoneTuningMenuBinding(menu, host);
    }

    /** Resolves the built-in host capability once; custom integrations may supply their own callback wrapper. */
    public static @Nullable RedstoneTuningMenuHost nativeHost(AEBaseMenu menu) {
        if (menu.getTarget() instanceof PatternProviderHostAccessor host) {
            return new RedstoneTuningMenuHost(host.dataEnergistics$getRedstoneTuningUpgrades(),
                    host::dataEnergistics$hasRedstoneTuningCard,
                    () -> host.dataEnergistics$getRedstoneTuningMode().ordinal(),
                    ordinal -> host.dataEnergistics$setRedstoneTuningMode(MODES[ordinal]));
        }
        return null;
    }

    @Override
    public int get() {
        return menu.isClientSide() ? clientState :
                (host.mode().getAsInt() << 1) | (host.hasCard().getAsBoolean() ? 1 : 0);
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
        host.setMode().accept(ordinal);
    }
}
