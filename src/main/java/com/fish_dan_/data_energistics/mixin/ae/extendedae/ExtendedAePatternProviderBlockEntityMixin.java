package com.fish_dan_.data_energistics.mixin.ae.extendedae;

import com.fish_dan_.data_energistics.accessor.patternprovider.NativePatternDispatchHost;

import com.glodblock.github.extendedae.common.tileentities.TileExPatternProvider;
import org.spongepowered.asm.mixin.Mixin;

/** Keeps the native ExtendedAE block provider on its conservative pushPattern route. */
@Mixin(TileExPatternProvider.class)
public abstract class ExtendedAePatternProviderBlockEntityMixin implements NativePatternDispatchHost {}
