package com.fish_dan_.data_energistics.common.menu.patternprovider.registration;

import com.fish_dan_.data_energistics.api.registry.provider.menu.PatternProviderRedstoneMenu;

import appeng.menu.SlotSemantic;

import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;

/**
 * Mixin bridge for AE2's protected menu extension points. The common menu factory or an explicit compatibility
 * wrapper initializes the attachment after native slots; all operations stay on the owning menu thread.
 */
@NullMarked
public interface PatternProviderMenuAccess {

    /** Installs the registered control once after native slots and actions have been constructed. */
    void dataEnergistics$initializeProviderMenu();

    /** Returns the menu-local control, or null when the native menu or host has no registered extension. */
    @Nullable
    PatternProviderRedstoneMenu dataEnergistics$providerRedstoneMenu();

    /** Stores the registered or explicitly attached control before initial menu synchronization. */
    void dataEnergistics$attachProviderRedstoneMenu(@Nullable PatternProviderRedstoneMenu control);

    /** Appends a non-null slot with its AE2 semantic before initial synchronization. */
    void dataEnergistics$addProviderSlot(Slot slot, SlotSemantic semantic);

    /** Appends a non-null data slot before initial synchronization; vanilla owns subsequent change detection. */
    void dataEnergistics$addProviderDataSlot(DataSlot slot);

    /** Registers a non-null, uniquely named client action before the menu is opened. AE2 rejects duplicate names. */
    <T> void dataEnergistics$registerProviderAction(String name, Class<T> argumentType, Consumer<T> action);

    /** Sends a previously registered action from the logical client. AE2 validates its argument and packet size. */
    <T> void dataEnergistics$sendProviderAction(String name, T argument);
}
