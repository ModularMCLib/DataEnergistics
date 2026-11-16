package com.fish_dan_.data_energistics.api.registry.provider.client;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.WidgetContainer;

import net.minecraft.client.gui.components.Button;

import org.jspecify.annotations.NullMarked;

import java.util.function.Consumer;

/**
 * Client-thread access to a fully constructed native screen, supplied once before its first layout. All members are
 * non-null and belong to this screen; factories may retain them only for the screen's lifetime.
 *
 * @param screen           native screen whose constructor has completed
 * @param widgets          AE2 widgets, including those installed by native subclasses
 * @param addToolbarButton appends a non-null button before AE2 populates its toolbar
 */
@NullMarked
public record PatternProviderScreenContext(
                                           AEBaseScreen<?> screen, WidgetContainer widgets, Consumer<Button> addToolbarButton) {}
