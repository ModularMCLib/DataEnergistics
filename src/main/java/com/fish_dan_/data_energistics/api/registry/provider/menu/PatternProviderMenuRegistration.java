package com.fish_dan_.data_energistics.api.registry.provider.menu;

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
                                                                    Function<M, @Nullable PatternProviderRedstoneMenu> factory) {}
