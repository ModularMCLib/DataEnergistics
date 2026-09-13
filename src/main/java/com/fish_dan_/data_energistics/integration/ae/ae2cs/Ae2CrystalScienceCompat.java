package com.fish_dan_.data_energistics.integration.ae.ae2cs;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.MEStorage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import io.github.lounode.ae2cs.api.util.GenericStackInvHelper;
import io.github.lounode.ae2cs.common.me.crafting.EncodedResonatingPattern;
import io.github.lounode.ae2cs.common.me.crafting.ResonatingPatternDetails;
import it.unimi.dsi.fastutil.objects.ObjectList;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/** Direct AE2CS calls used by the Adaptive provider when AE2CS is loaded. */
public final class Ae2CrystalScienceCompat {

    private Ae2CrystalScienceCompat() {}

    public static boolean isResonatingPattern(IPatternDetails patternDetails) {
        return patternDetails instanceof ResonatingPatternDetails;
    }

    public static @Nullable MEStorage getAdjacentMeStorage(
                                                           Level level,
                                                           BlockPos position,
                                                           @Nullable BlockEntity blockEntity,
                                                           Direction side) {
        return GenericStackInvHelper.getAdjacentMeStorage(level, position, blockEntity, side);
    }

    public static List<GenericStack> getSparseInputs(IPatternDetails patternDetails) {
        return patternDetails instanceof ResonatingPatternDetails resonating ? resonating.getSparseInputs() : ObjectList.of();
    }

    public static @Nullable ResolvedTarget resolveTarget(IPatternDetails patternDetails, int sparseIndex) {
        if (!(patternDetails instanceof ResonatingPatternDetails resonating)) {
            return null;
        }
        Optional<EncodedResonatingPattern.Target> target = resonating.getTargetForSparseInputIndex(sparseIndex);
        return target.map(value -> new ResolvedTarget(value.pos(), value.face())).orElse(null);
    }

    public record ResolvedTarget(GlobalPos position, Direction face) {}
}
