package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.model;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnostic;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnostic.SolverWork;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnosticCode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityAlgorithmResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityPlanningControl;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityPlanningMode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.bounds.TrinityCycleObjectiveBounds;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.bounds.TrinityExactCycleBalanceBounds;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.optimization.integer.TrinityIntegerProgram;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.optimization.integer.TrinityIntegerProgram.Constraint;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.optimization.integer.TrinityIntegerProgram.Solution;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.optimization.integer.TrinityIntegerProgram.Status;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.optimization.integer.TrinityIntegerProgram.Variable;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.diagnostic.TrinityDiagnosticField;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.diagnostic.TrinityDiagnosticPhase;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityPlanQuality;
import com.fish_dan_.data_energistics.utils.FastUtilCollections;

import appeng.api.stacks.AEKey;

import net.minecraft.network.chat.Component;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectSet;

import java.math.BigInteger;

/** Works directly on integral firing and reserve amounts before the executable prefix is checked. */
final class TrinityIntegerCycleFeasibilityModel implements TrinityCycleFeasibilityModel {

    private static final BigInteger ZERO = BigInteger.ZERO;
    private static final BigInteger ONE = BigInteger.ONE;
    private static final TrinityCycleObjectiveBounds OBJECTIVE_BOUNDS = TrinityCycleObjectiveBounds.create();

    @Override
    public TrinityAlgorithmResult<TrinityCycleFeasibilitySolution> solve(
                                                                         TrinityCycleFeasibilityRequest request,
                                                                         TrinityPlanningMode mode,
                                                                         TrinityPlanningControl control) {
        long started = System.nanoTime();
        SolveWork work = new SolveWork(started, control);
        if (control.cancellationRequested()) return failed(TrinityPlanningDiagnosticCode.CALCULATION_CANCELLED, work);
        if (control.deadlineExceeded()) return failed(TrinityPlanningDiagnosticCode.MIP_TIMEOUT, work);
        if (mode == TrinityPlanningMode.FIRST_FEASIBLE && !request.shortageDiagnostic()) {
            var sparse = TrinitySparseCycleFeasibility.solve(request, control);
            if (control.cancellationRequested()) return failed(TrinityPlanningDiagnosticCode.CALCULATION_CANCELLED, work);
            if (control.deadlineExceeded()) return failed(TrinityPlanningDiagnosticCode.MIP_TIMEOUT, work);
            if (sparse != null) return TrinityAlgorithmResult.success(sparse);
        }
        if (TrinityExactCycleBalanceBounds.contradictory(request)) {
            return failed(TrinityPlanningDiagnosticCode.MIP_NO_INTEGER_SOLUTION, work);
        }

        control.recordSolverModel();
        Model model = Model.capture(request);
        boolean optimize = mode == TrinityPlanningMode.OPTIMAL && !request.shortageDiagnostic();
        Solution point = optimize ? model.optimize(work) : work.minimize(model.program());
        if (!point.feasible()) {
            var code = switch (point.status()) {
                case INFEASIBLE -> TrinityPlanningDiagnosticCode.MIP_NO_INTEGER_SOLUTION;
                case CANCELLED -> TrinityPlanningDiagnosticCode.CALCULATION_CANCELLED;
                case TIMEOUT -> TrinityPlanningDiagnosticCode.MIP_TIMEOUT;
                case LIMIT -> TrinityPlanningDiagnosticCode.ORDER_SEARCH_LIMIT;
                case OPTIMAL, FEASIBLE -> throw new IllegalStateException("Integer program returned a missing witness");
            };
            return failed(code, work);
        }
        TrinityPlanQuality quality = optimize && point.optimal() ? TrinityPlanQuality.PROVED_OPTIMAL : TrinityPlanQuality.VERIFIED_FEASIBLE;
        return TrinityAlgorithmResult.success(model.decode(request, point, quality, work));
    }

    private static TrinityAlgorithmResult<TrinityCycleFeasibilitySolution> failed(
                                                                                  TrinityPlanningDiagnosticCode code, SolveWork work) {
        return TrinityAlgorithmResult.failure(new TrinityPlanningDiagnostic(code, Component.translatable(code.translationKey),
                FastUtilCollections.mapOf(TrinityDiagnosticField.PHASE, TrinityDiagnosticPhase.CYCLE_FEASIBILITY,
                        TrinityDiagnosticField.STATES, work.passes),
                new SolverWork(work.passes, work.passes, System.nanoTime() - work.started)));
    }

    private record Model(TrinityIntegerProgram program,
                         Object2ObjectMap<TrinityPatternVariant, Variable> firings,
                         Object2ObjectMap<AEKey, Variable> seed,
                         Object2ObjectMap<AEKey, Variable> external,
                         Constraint externalTotal,
                         Constraint seedTotal,
                         Constraint firingTotal) {

        private static Model capture(TrinityCycleFeasibilityRequest request) {
            TrinityIntegerProgram program = new TrinityIntegerProgram();
            Object2ObjectLinkedOpenHashMap<TrinityPatternVariant, Variable> firings = new Object2ObjectLinkedOpenHashMap<>();
            ObjectArrayList<Variable> firingAxes = new ObjectArrayList<>(request.variants().size());
            for (TrinityPatternVariant variant : request.variants()) {
                TrinityFiringBounds bounds = request.firingBounds().get(variant);
                Variable firing = program.addVariable().lower(bounds.lowerInclusive());
                bounds.upperInclusive().ifPresent(firing::upper);
                firings.put(variant, firing);
                firingAxes.add(firing);
            }
            var seed = reserves(program, request, request.internalKeys());
            var external = reserves(program, request, OBJECTIVE_BOUNDS.externalReserveKeys(request));
            ObjectLinkedOpenHashSet<AEKey> keys = new ObjectLinkedOpenHashSet<>(request.coefficientTemplate().touchedKeys());
            keys.addAll(request.demand().finalBalanceLowerBounds().keySet());

            int index = 0;
            for (AEKey key : keys) {
                Constraint balance = program.addConstraint("balance_" + index++)
                        .lower(request.demand().finalBalanceLowerBounds().getOrDefault(key, ZERO));
                addNet(request, firingAxes, key, balance);
                Variable reserve = request.internalKeys().contains(key) ? seed.get(key) : external.get(key);
                if (reserve != null) balance.set(reserve, ONE);
            }
            for (var entry : request.demand().requiredNetChangeLowerBounds().object2ObjectEntrySet()) {
                Constraint net = program.addConstraint("net_" + index++).lower(entry.getValue());
                addNet(request, firingAxes, entry.getKey(), net);
            }
            BigInteger externalMinimum = OBJECTIVE_BOUNDS.minimumFirstExternalInput(request);
            Constraint externalTotal = total(program, "external", external.values())
                    .lower(request.fixedExternalTotal().orElse(ZERO).max(externalMinimum));
            request.fixedExternalTotal().ifPresent(externalTotal::upper);
            Constraint seedTotal = total(program, "seed", seed.values())
                    .lower(request.seedLowerBound().max(OBJECTIVE_BOUNDS.minimumFirstInternalInput(request)));
            Constraint firingTotal = total(program, "firings", firings.values()).lower(request.firingLowerBound().max(ONE));
            return new Model(program, firings, seed, external, externalTotal, seedTotal, firingTotal);
        }

        private static Object2ObjectMap<AEKey, Variable> reserves(
                                                                  TrinityIntegerProgram program,
                                                                  TrinityCycleFeasibilityRequest request,
                                                                  ObjectSet<AEKey> keys) {
            Object2ObjectLinkedOpenHashMap<AEKey, Variable> reserves = new Object2ObjectLinkedOpenHashMap<>();
            for (AEKey key : keys) {
                Variable reserve = program.addVariable();
                if (!request.producibleInputs().contains(key) && !request.shortageDiagnostic()) {
                    reserve.upper(request.available().getOrDefault(key, ZERO));
                }
                reserves.put(key, reserve);
            }
            return reserves;
        }

        private static void addNet(TrinityCycleFeasibilityRequest request, ObjectList<Variable> firingAxes, AEKey key, Constraint row) {
            for (var coefficient : request.coefficientTemplate().coefficients(key)) {
                row.set(firingAxes.get(coefficient.variantIndex()), coefficient.value());
            }
        }

        private static Constraint total(TrinityIntegerProgram program, String name, Iterable<Variable> variables) {
            Constraint row = program.addConstraint(name);
            variables.forEach(variable -> row.set(variable, ONE));
            return row;
        }

        private Solution optimize(SolveWork work) {
            this.externalTotal.weight(ONE);
            Solution incumbent = work.minimize(this.program);
            if (!incumbent.optimal()) return incumbent;
            this.externalTotal.weight(ZERO).level(sum(this.external.values(), incumbent));

            this.seedTotal.weight(ONE);
            Solution candidate = work.minimize(this.program);
            if (!candidate.optimal()) return keepIncumbent(incumbent, candidate);
            incumbent = candidate;
            this.seedTotal.weight(ZERO).level(sum(this.seed.values(), incumbent));

            this.firingTotal.weight(ONE);
            candidate = work.minimize(this.program);
            if (!candidate.optimal()) return keepIncumbent(incumbent, candidate);
            incumbent = candidate;
            BigInteger remaining = sum(this.firings.values(), incumbent);
            this.firingTotal.weight(ZERO).level(remaining);

            for (Variable firing : this.firings.values()) {
                if (remaining.signum() == 0) break;
                firing.weight(ONE.negate());
                candidate = work.minimize(this.program);
                if (!candidate.optimal()) return keepIncumbent(incumbent, candidate);
                incumbent = candidate;
                BigInteger count = incumbent.values().get(this.program.indexOf(firing));
                firing.weight(ZERO).level(count);
                remaining = remaining.subtract(count);
            }
            return incumbent;
        }

        private static Solution keepIncumbent(Solution incumbent, Solution candidate) {
            return switch (candidate.status()) {
                case CANCELLED, FEASIBLE, OPTIMAL -> candidate;
                case LIMIT, TIMEOUT -> new Solution(Status.FEASIBLE, incumbent.values(), candidate.states());
                case INFEASIBLE -> throw new IllegalStateException("A locked Trinity objective excluded its exact witness");
            };
        }

        private BigInteger sum(Iterable<Variable> variables, Solution solution) {
            BigInteger result = ZERO;
            for (Variable variable : variables) result = result.add(solution.values().get(this.program.indexOf(variable)));
            return result;
        }

        private TrinityCycleFeasibilitySolution decode(
                                                       TrinityCycleFeasibilityRequest request, Solution solution,
                                                       TrinityPlanQuality quality, SolveWork work) {
            var initialSeed = amounts(this.seed, solution.values());
            var initialExternal = amounts(this.external, solution.values());
            var allocation = request.shortageDiagnostic() ? TrinityShortageInputAllocation.from(request, initialSeed, initialExternal) :
                    new TrinityShortageInputAllocation(FastUtilCollections.mapOf(), FastUtilCollections.mapOf());
            return new TrinityCycleFeasibilitySolution(amounts(this.firings, solution.values()), initialSeed, initialExternal,
                    work.passes, System.nanoTime() - work.started, false, quality,
                    allocation.actualInputs(), allocation.missingInputs(), request.shortageDiagnostic() ? work.passes : 0);
        }

        private <K> Object2ObjectMap<K, BigInteger> amounts(Object2ObjectMap<K, Variable> variables, ObjectList<BigInteger> values) {
            Object2ObjectLinkedOpenHashMap<K, BigInteger> result = new Object2ObjectLinkedOpenHashMap<>();
            variables.forEach((key, variable) -> {
                BigInteger value = values.get(this.program.indexOf(variable));
                if (value.signum() > 0) result.put(key, value);
            });
            return result;
        }
    }

    private static final class SolveWork {

        private final long started;
        private final TrinityPlanningControl control;
        private int passes;
        private int remainingStates = TrinityIntegerProgram.DEFAULT_STATE_LIMIT;

        private SolveWork(long started, TrinityPlanningControl control) {
            this.started = started;
            this.control = control;
        }

        private Solution minimize(TrinityIntegerProgram program) {
            if (this.control.cancellationRequested()) return new Solution(Status.CANCELLED, ObjectList.of(), 0);
            if (this.remainingStates == 0) return new Solution(Status.LIMIT, ObjectList.of(), 0);
            long start = System.nanoTime();
            Solution solution = program.minimize(this.remainingStates, this.control);
            this.remainingStates -= solution.states();
            this.passes++;
            this.control.recordSolverPass(System.nanoTime() - start);
            return solution;
        }
    }
}
