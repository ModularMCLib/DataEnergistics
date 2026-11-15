package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.optimization;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnostic;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnosticCode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityAlgorithmResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.diagnostic.TrinityDiagnosticField;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.diagnostic.TrinityDiagnosticReason;
import com.fish_dan_.data_energistics.utils.FastUtilCollections;

import net.minecraft.network.chat.Component;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.ojalgo.type.context.NumberContext;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

/**
 * Canonicalizes only values accepted by the active solver's integrality contract before exact conservation checks.
 * <p>
 * Solver-contract conversion whose candidates remain subject to exact {@link BigInteger} model verification.
 */
@NullMarked
public final class TrinityIntegerResultVerifier {

    /**
     * @return stateless exact verifier
     */
    public static TrinityIntegerResultVerifier create() {
        return new TrinityIntegerResultVerifier();
    }

    /**
     * @param values               raw ojAlgo decimal values
     * @param integralityTolerance the same tolerance used by the integer solver that produced the values
     * @return exact integers or {@code MIP_INEXACT_RESULT}
     */
    public TrinityAlgorithmResult<ObjectList<BigInteger>> verify(
                                                                 ObjectList<@Nullable BigDecimal> values,
                                                                 NumberContext integralityTolerance) {
        ObjectArrayList<BigInteger> integers = new ObjectArrayList<>(values.size());
        for (int index = 0; index < values.size(); index++) {
            BigDecimal value = values.get(index);
            if (value != null) {
                BigDecimal nearest = value.setScale(0, RoundingMode.HALF_EVEN);
                BigDecimal displacement = value.subtract(nearest).abs();
                if (integralityTolerance.isZero(displacement.doubleValue())) {
                    integers.add(nearest.toBigIntegerExact());
                    continue;
                }
            }
            return TrinityAlgorithmResult.failure(new TrinityPlanningDiagnostic(
                    TrinityPlanningDiagnosticCode.MIP_INEXACT_RESULT,
                    Component.translatable("gui.data_energistics.trinity_planning.diagnostic.inexact_result"),
                    FastUtilCollections.mapOf(
                            TrinityDiagnosticField.INDEX, index,
                            TrinityDiagnosticField.VALUE, value == null ? TrinityDiagnosticReason.MISSING_SOLVER_VALUE : value)));
        }
        return TrinityAlgorithmResult.success(FastUtilCollections.immutableList(integers));
    }
}
