package com.fish_dan_.data_energistics.core.mixins.appeng.client.gui;

import appeng.client.gui.WidgetContainer;

import net.minecraft.client.gui.components.AbstractWidget;

import org.jspecify.annotations.NullMarked;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(WidgetContainer.class)
@NullMarked
public interface WidgetContainerAccessor {

    @Accessor("widgets")
    Map<String, AbstractWidget> dataEnergistics$getWidgets();
}
