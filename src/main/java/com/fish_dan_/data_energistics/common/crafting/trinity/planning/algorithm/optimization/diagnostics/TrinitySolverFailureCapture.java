package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.optimization.diagnostics;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.neoforged.fml.loading.FMLPaths;

import org.ojalgo.optimisation.ExpressionsBasedModel;
import org.ojalgo.optimisation.Optimisation;
import org.ojalgo.optimisation.Variable;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

/** Captures the first failed solver input per process before presolve can mutate its constraints. */
public final class TrinitySolverFailureCapture {

    private static final AtomicBoolean CAPTURE_ATTEMPTED = new AtomicBoolean();
    private static final BigDecimal BRANCH_LOWER_SENTINEL = BigDecimal.valueOf(Integer.MIN_VALUE);
    private static final BigDecimal BRANCH_UPPER_SENTINEL = BigDecimal.valueOf(Integer.MAX_VALUE);

    private TrinitySolverFailureCapture() {}

    /**
     * Solves a request-owned model on its planning worker. Keeps a deep copy until the first failure is
     * captured, then releases it; successful calls perform no file IO. The original exception is rethrown
     * unchanged so the existing planning boundary retains its diagnostic semantics.
     *
     * @param model mutable model already configured for this invocation, never shared across workers
     * @param sense objective direction, also recorded separately because EBM does not encode it
     * @param phase internal solver stage used to identify the failing call in the log and metadata
     * @return the unmodified ojAlgo result
     */
    public static Optimisation.Result solve(ExpressionsBasedModel model, Optimisation.Sense sense, String phase) {
        return solve(model, sense, phase, false);
    }

    /**
     * Also captures a normally returned INFEASIBLE state when the caller needs the original model to
     * diagnose it. Enable only at a terminal diagnostic boundary, not for expected search probes.
     * Uses the same request ownership, exception propagation and process-wide capture limit as {@link #solve}.
     */
    public static Optimisation.Result solve(
                                            ExpressionsBasedModel model,
                                            Optimisation.Sense sense,
                                            String phase,
                                            boolean captureInfeasible) {
        if (CAPTURE_ATTEMPTED.get()) {
            return optimise(model, sense);
        }
        ExpressionsBasedModel original = model.copy();
        try {
            Optimisation.Result result = optimise(model, sense);
            if (captureInfeasible && result.getState() == Optimisation.State.INFEASIBLE &&
                    CAPTURE_ATTEMPTED.compareAndSet(false, true)) {
                capture(original, sense, phase, result.getState().name());
            }
            return result;
        } catch (StackOverflowError | RuntimeException failure) {
            if (CAPTURE_ATTEMPTED.compareAndSet(false, true)) {
                capture(original, sense, phase, failure.getClass().getName());
            }
            throw failure;
        } finally {
            original.dispose();
        }
    }

    private static Optimisation.Result optimise(ExpressionsBasedModel model, Optimisation.Sense sense) {
        return sense == Optimisation.Sense.MAX ? model.maximise() : model.minimise();
    }

    private static void capture(
                                ExpressionsBasedModel model,
                                Optimisation.Sense sense,
                                String phase,
                                String failure) {
        try {
            Path directory = FMLPaths.GAMEDIR.get().resolve("logs").resolve("data_energistics").resolve("trinity");
            Files.createDirectories(directory);
            Path modelFile = Files.createTempFile(directory, "solver-failure-", ".ebm");
            model.writeTo(modelFile);
            Path metadataFile = modelFile.resolveSibling(modelFile.getFileName() + ".txt");
            StringBuilder metadata = new StringBuilder()
                    .append("phase=").append(phase).append('\n')
                    .append("sense=").append(sense).append('\n')
                    .append("failure=").append(failure).append('\n')
                    .append("variables=").append(model.countVariables()).append('\n')
                    .append("expressions=").append(model.countExpressions()).append('\n')
                    .append("has_integer_variables=").append(model.isAnyVariableInteger()).append('\n')
                    .append("parallelism=").append(model.options.getParallelism()).append('\n')
                    .append("time_abort=").append(model.options.time_abort).append('\n')
                    .append("time_suffice=").append(model.options.time_suffice).append('\n')
                    .append("iterations_abort=").append(model.options.iterations_abort).append('\n')
                    .append("iterations_suffice=").append(model.options.iterations_suffice).append('\n')
                    .append("feasibility=").append(model.options.feasibility).append('\n')
                    .append("solution=").append(model.options.solution).append('\n')
                    .append("sparse=").append(model.options.sparse).append('\n')
                    .append("integrality=").append(model.options.integer().getIntegralityTolerance()).append('\n')
                    .append("gap=").append(model.options.integer().getGapTolerance()).append('\n');
            int unsafeDomains = 0;
            for (Variable variable : model.getVariables()) {
                if (!variable.isInteger()) continue;
                BigDecimal lower = variable.getLowerLimit();
                BigDecimal upper = variable.getUpperLimit();
                if (lower == null || upper == null || lower.compareTo(BRANCH_LOWER_SENTINEL) <= 0 ||
                        upper.compareTo(BRANCH_UPPER_SENTINEL) >= 0) {
                    unsafeDomains++;
                    metadata.append("integer_domain=").append(variable.getName())
                            .append(" lower=").append(lower).append(" upper=").append(upper).append('\n');
                }
            }
            metadata.append("unbounded_or_outside_int_domains=").append(unsafeDomains).append('\n');
            Files.writeString(metadataFile, metadata, StandardCharsets.UTF_8);
            Data_Energistics.LOGGER.error(
                    "Trinity solver failure captured phase={} sense={} failure={} variables={} expressions={} " +
                            "unboundedOrOutsideIntDomains={} model={} metadata={}",
                    phase, sense, failure, model.countVariables(), model.countExpressions(), unsafeDomains,
                    modelFile.toAbsolutePath(), metadataFile.toAbsolutePath());
        } catch (IOException | RuntimeException captureFailure) {
            Data_Energistics.LOGGER.error("Could not capture Trinity solver failure model for phase={}",
                    phase, captureFailure);
        }
    }
}
