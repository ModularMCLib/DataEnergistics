package com.fish_dan_.data_energistics.common.menu.patternprovider.registration;

import com.fish_dan_.data_energistics.api.registry.provider.menu.PatternProviderMenuRegistration;
import com.fish_dan_.data_energistics.api.registry.provider.menu.PatternProviderRedstoneMenu;

import appeng.menu.AEBaseMenu;

import net.minecraft.world.inventory.AbstractContainerMenu;

import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMaps;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;

/** Native menu factories frozen by common setup and resolved only when a menu is opened. */
@NullMarked
public final class PatternProviderMenus {

    private static Reference2ObjectMap<Class<?>, @Nullable PatternProviderMenuRegistration<?>> registrations = Reference2ObjectMaps.emptyMap();

    private PatternProviderMenus() {}

    /** Publishes the validated declarations before any menu can open. */
    public static void install(List<PatternProviderMenuRegistration<?>> declarations) {
        Reference2ObjectMap<Class<?>, @Nullable PatternProviderMenuRegistration<?>> indexed = new Reference2ObjectOpenHashMap<>(declarations.size());
        for (var declaration : declarations) {
            indexed.put(declaration.menuType(), declaration);
        }
        registrations = Reference2ObjectMaps.unmodifiable(indexed);
    }

    /** Creates one control after native construction, or returns null for an unregistered menu or unsupported host. */
    public static @Nullable PatternProviderRedstoneMenu create(AEBaseMenu menu) {
        for (Class<?> type = menu.getClass(); type != AbstractContainerMenu.class; type = type.getSuperclass()) {
            var registration = registrations.get(type);
            if (registration != null) {
                return create(registration, menu);
            }
        }
        return null;
    }

    private static <M extends AEBaseMenu> @Nullable PatternProviderRedstoneMenu create(
                                                                                       PatternProviderMenuRegistration<M> registration, AEBaseMenu menu) {
        return registration.factory().apply(registration.menuType().cast(menu));
    }
}
