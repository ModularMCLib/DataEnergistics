package com.fish_dan_.data_energistics.api.registry.provider.client;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.WidgetContainer;
import appeng.menu.AEBaseMenu;

import net.minecraft.client.gui.components.Button;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Screen-local extension factory staged by a client plugin. The closest registered menu superclass wins. Creation
 * happens once on the client thread before first layout; resizing reuses the created widgets and update callback.
 *
 * @param menuType native menu class whose screens receive this extension, unique within the client snapshot
 * @param factory  non-null factory returning a per-frame update, or null when this screen needs no extension
 */
@NullMarked
public record PatternProviderScreenRegistration(
                                                Class<? extends AEBaseMenu> menuType, Function<PatternProviderScreenContext, @Nullable Runnable> factory) {

    /**
     * Initializes a matching, fully constructed screen before layout. Inputs are non-null and screen-local; exceptions
     * propagate to the screen opening boundary. A null result installs no frame callback.
     */
    public @Nullable Runnable initialize(AEBaseScreen<?> screen, WidgetContainer widgets, Consumer<Button> addToolbarButton) {
        return factory.apply(new PatternProviderScreenContext(screen, widgets, addToolbarButton));
    }
}
