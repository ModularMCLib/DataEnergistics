package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.radix.codec;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnostic;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnosticCode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityAlgorithmResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.radix.model.TrinityRadixModelLimitException;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.optimization.TrinityIntegerResultVerifier;
import com.fish_dan_.data_energistics.util.FastUtilCollections;

import net.minecraft.network.chat.Component;

import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import org.ojalgo.optimisation.ExpressionsBasedModel;
import org.ojalgo.optimisation.Optimisation;
import org.ojalgo.optimisation.Variable;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.IdentityHashMap;

/**
 * Decodes ojAlgo digit/carry candidates and replays every radix column with BigInteger before publication.
 */
public final class TrinityRadixResultDecoder {

    private final TrinityIntegerResultVerifier integerVerifier;

    public TrinityRadixResultDecoder(TrinityIntegerResultVerifier integerVerifier) {
        if (integerVerifier == null) {
            throw new IllegalArgumentException("A Trinity radix decoder requires an integer verifier");
        }
        this.integerVerifier = integerVerifier;
    }

    public TrinityAlgorithmResult<Object2ObjectMap<Variable, BigInteger>> decode(
                                                                                 ExpressionsBasedModel model,
                                                                                 ObjectList<Variable> variables,
                                                                                 ObjectList<TrinityRadixColumnEquation> equations,
                                                                                 Optimisation.Result result) {
        ObjectArrayList<BigDecimal> rawValues = new ObjectArrayList<>(variables.size());
        if (model.countVariables() != variables.size()) {
            throw new IllegalArgumentException("A Trinity radix result must preserve the encoded variable order");
        }
        for (int index = 0; index < variables.size(); index++) {
            if (!variables.get(index).getName().equals(model.getVariable(index).getName())) {
                throw new IllegalArgumentException("A Trinity radix result changed the encoded variable identity");
            }
            rawValues.add(result.get(index));
        }
        TrinityAlgorithmResult<ObjectList<BigInteger>> verified = this.integerVerifier.verify(
                rawValues,
                model.options.integer().getIntegralityTolerance());
        if (!verified.successful()) {
            return TrinityAlgorithmResult.failure(verified.diagnostic());
        }
        IdentityHashMap<Variable, BigInteger> values = new IdentityHashMap<>();
        for (int index = 0; index < variables.size(); index++) {
            BigInteger value = verified.value().get(index);
            Variable variable = variables.get(index);
            Variable solvedVariable = model.getVariable(index);
            BigInteger lower = decimalInteger(solvedVariable.getLowerLimit());
            BigInteger upper = decimalInteger(solvedVariable.getUpperLimit());
            if (value.compareTo(lower) < 0 || value.compareTo(upper) > 0) {
                return inexact("radix_variable_bound", variable.getName());
            }
            values.put(variable, value);
        }
        for (int index = 0; index < equations.size(); index++) {
            TrinityRadixColumnEquation equation = equations.get(index);
            BigInteger left = equation.terms().entrySet().stream()
                    .map(entry -> entry.getValue().multiply(values.get(entry.getKey())))
                    .reduce(BigInteger.ZERO, BigInteger::add);
            if (!left.equals(equation.rightHandSide())) {
                return inexact("radix_carry_column", Integer.toString(index));
            }
        }
        return TrinityAlgorithmResult.success(FastUtilCollections.immutableMap(values));
    }

    private static BigInteger decimalInteger(BigDecimal value) {
        if (value == null) {
            throw new TrinityRadixModelLimitException(FastUtilCollections.mapOf("reason", "unbounded_radix_variable"));
        }
        return value.toBigIntegerExact();
    }

    private static <T> TrinityAlgorithmResult<T> inexact(String constraint, String value) {
        return TrinityAlgorithmResult.failure(new TrinityPlanningDiagnostic(
                TrinityPlanningDiagnosticCode.MIP_INEXACT_RESULT,
                Component.translatable("gui.data_energistics.trinity_planning.diagnostic.inexact_result"),
                FastUtilCollections.mapOf("constraint", constraint, "value", value)));
    }
}
