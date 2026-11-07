package com.fish_dan_.data_energistics.core.mixins.dev.ftb.mods.ftbchunks.client.gui;

import com.fish_dan_.data_energistics.integration.map.ftbchunks.client.FtbChunksOrbitalAdapter;
import com.fish_dan_.data_energistics.integration.map.ftbchunks.client.LargeMapScreenAccessor;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.ftb.mods.ftbchunks.client.gui.LargeMapScreen;
import dev.ftb.mods.ftbchunks.client.gui.RegionMapPanel;
import dev.ftb.mods.ftblibrary.icon.Icons;
import dev.ftb.mods.ftblibrary.ui.ContextMenu;
import dev.ftb.mods.ftblibrary.ui.ContextMenuItem;
import me.fallenbreath.conditionalmixin.api.annotation.Condition;
import me.fallenbreath.conditionalmixin.api.annotation.Restriction;
import org.jspecify.annotations.NullMarked;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

/** Appends one preview action while preserving FTB Chunks' existing waypoint menu construction and handling. */
@Restriction(require = @Condition("ftbchunks"))
@Mixin(value = LargeMapScreen.class, remap = false)
@NullMarked
public abstract class LargeMapScreenMixin implements LargeMapScreenAccessor {

    @Shadow
    @Final
    private RegionMapPanel regionPanel;

    @WrapOperation(
                   method = "mousePressed",
                   at = @At(
                            value = "INVOKE",
                            target = "Ldev/ftb/mods/ftbchunks/client/gui/LargeMapScreen;openContextMenu(Ljava/util/List;)Ldev/ftb/mods/ftblibrary/ui/ContextMenu;"),
                   require = 0)
    private ContextMenu dataEnergistics$appendOrbitalPreview(
                                                             LargeMapScreen screen,
                                                             List<ContextMenuItem> items,
                                                             Operation<ContextMenu> original) {
        if (FtbChunksOrbitalAdapter.INSTANCE.shouldOfferPreviewAction()) {
            BlockPos target = this.regionPanel.blockPos();
            items.add(new ContextMenuItem(
                    Component.translatable(
                            "screen.data_energistics.orbital_control_terminal.fire_control.map.preview"),
                    Icons.MAP,
                    ignoredButton -> FtbChunksOrbitalAdapter.INSTANCE.openRightClickPreview(
                            screen.currentDimension().location(),
                            target.getX(),
                            target.getZ())));
        }
        return original.call(screen, items);
    }

    @Accessor("grabbed")
    public abstract int dataEnergistics$getGrabbed();
}
