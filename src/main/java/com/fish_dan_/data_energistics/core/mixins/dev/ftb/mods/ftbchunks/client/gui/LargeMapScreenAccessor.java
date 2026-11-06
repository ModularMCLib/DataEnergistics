package com.fish_dan_.data_energistics.core.mixins.dev.ftb.mods.ftbchunks.client.gui;

import com.fish_dan_.data_energistics.core.mixin.condition.RequireMods;
import com.fish_dan_.data_energistics.integration.map.ftbchunks.client.FtbChunksOrbitalMapBridge;

import dev.ftb.mods.ftbchunks.client.gui.LargeMapScreen;
import dev.ftb.mods.ftbchunks.client.gui.RegionMapPanel;
import org.jspecify.annotations.NullMarked;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Accesses only the large map's own cursor panel and drag sentinel. */
@RequireMods("ftbchunks")
@Mixin(value = LargeMapScreen.class, remap = false)
@NullMarked
public interface LargeMapScreenAccessor extends FtbChunksOrbitalMapBridge.Access {

    @Accessor("regionPanel")
    RegionMapPanel dataEnergistics$getRegionPanel();

    @Accessor("grabbed")
    int dataEnergistics$getGrabbed();
}
