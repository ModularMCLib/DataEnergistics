package com.fish_dan_.data_energistics.core.mixins.dev.ftb.mods.ftbchunks.client.gui;

import com.fish_dan_.data_energistics.core.mixin.condition.RequireMods;
import com.fish_dan_.data_energistics.integration.map.ftbchunks.client.FtbChunksOrbitalMapBridge;

import dev.ftb.mods.ftbchunks.client.gui.LargeMapScreen;
import dev.ftb.mods.ftbchunks.client.gui.RegionMapPanel;
import org.jspecify.annotations.NullMarked;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Exposes the owning large-map screen without duplicating FTB Chunks coordinate conversion. */
@RequireMods("ftbchunks")
@Mixin(value = RegionMapPanel.class, remap = false)
@NullMarked
public interface RegionMapPanelAccessor extends FtbChunksOrbitalMapBridge.Access {

    @Accessor("largeMap")
    LargeMapScreen dataEnergistics$getLargeMap();
}
