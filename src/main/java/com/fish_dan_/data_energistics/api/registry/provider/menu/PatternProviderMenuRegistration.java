package com.fish_dan_.data_energistics.api.registry.provider.menu;

import com.fish_dan_.data_energistics.common.menu.patternprovider.registration.PatternProviderMenuAccess;
import com.fish_dan_.data_energistics.common.menu.patternprovider.registration.RedstoneTuningMenuBinding;

import appeng.menu.AEBaseMenu;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.function.Function;

/**
 * Registers one factory for a native menu class during common plugin registration. The closest registered superclass
 * wins. AE2's menu factory invokes it after construction on both logical sides, before initial slot synchronization.
 * The factory may append slots and client actions and returns a menu-local control, or null for an unsupported host.
 * It must not retain menus globally; exceptions propagate to the menu opening boundary.
 *
 * @param menuType native menu class, unique within the registration snapshot
 * @param factory  non-null factory called once for each menu built through AE2's MenuTypeBuilder
 */
@NullMarked
public record PatternProviderMenuRegistration<M extends AEBaseMenu>(
                                                                    Class<M> menuType,
                                                                    Function<M, @Nullable PatternProviderRedstoneMenu> factory) {

    /**
     * Attaches the shared slot, state synchronization and action directly from the end of a native menu constructor.
     * Use this once, on both logical sides, from an optional compatibility wrapper after ModList.isLoaded succeeds.
     * Inputs are non-null and menu-local. This is an alternative to registering a factory for this menu hierarchy;
     * combining both paths would append the extension twice. Exceptions propagate to the native opening boundary.
     */
    public static void attachRedstoneTuning(AEBaseMenu menu, RedstoneTuningMenuHost host) {
        ((PatternProviderMenuAccess) menu).dataEnergistics$attachProviderRedstoneMenu(
                RedstoneTuningMenuBinding.create(menu, host));
    }

    /**
     * Attaches once at the end of a native menu constructor using its built-in redstone host capability.
     * Both logical sides must call this optional-wrapper entry; unsupported hosts receive no extension.
     */
    public static void attachRedstoneTuning(AEBaseMenu menu) {
        var host = RedstoneTuningMenuBinding.nativeHost(menu);
        if (host != null) {
            attachRedstoneTuning(menu, host);
        }
    }

    /**
     * Creates a registration for hosts already exposing DE's built-in redstone capability. The callback resolves
     * that capability only after native menu construction and declines unsupported hosts.
     */
    public static <M extends AEBaseMenu> PatternProviderMenuRegistration<M> redstoneTuning(Class<M> menuType) {
        return redstoneTuning(menuType, RedstoneTuningMenuBinding::nativeHost);
    }

    /**
     * Encapsulates the upgrade slot, DataSlot and client action around callbacks for a native host. Call this during
     * plugin registration; hostSource runs once per menu on its logical thread and may return null to decline.
     * Callback failures propagate to the existing menu opening boundary. Native menu and host classes need no DE API.
     */
    public static <M extends AEBaseMenu> PatternProviderMenuRegistration<M> redstoneTuning(
                                                                                           Class<M> menuType, Function<M, @Nullable RedstoneTuningMenuHost> hostSource) {
        return new PatternProviderMenuRegistration<>(menuType, menu -> {
            var host = hostSource.apply(menu);
            return host == null ? null : RedstoneTuningMenuBinding.create(menu, host);
        });
    }
}
