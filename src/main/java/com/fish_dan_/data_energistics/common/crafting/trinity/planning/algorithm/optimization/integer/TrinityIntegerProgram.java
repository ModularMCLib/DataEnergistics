package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.optimization.integer;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityPlanningControl;

import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import org.jspecify.annotations.Nullable;

import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.Arrays;

/**
 * Request-local sparse integer linear program. All variables are non-negative integers.
 * Build and solve on the owning planning thread; the returned point owns its exact values.
 */
public final class TrinityIntegerProgram {

    public static final int DEFAULT_STATE_LIMIT = 65_536;

    private static final BigInteger ZERO = BigInteger.ZERO;
    private static final BigInteger ONE = BigInteger.ONE;
    private static final int PROPAGATION_PASSES = 4;
    private static final int OPEN_DOMAIN_ATTEMPTS = 4;

    private final ObjectArrayList<Variable> variables = new ObjectArrayList<>();
    private final Object2ObjectLinkedOpenHashMap<String, Constraint> constraints = new Object2ObjectLinkedOpenHashMap<>();

    public Variable addVariable() {
        Variable variable = new Variable(this.variables.size());
        this.variables.add(variable);
        return variable;
    }

    public Constraint addConstraint(String name) {
        Constraint constraint = new Constraint();
        if (this.constraints.putIfAbsent(name, constraint) != null) {
            throw new IllegalArgumentException("Duplicate Trinity integer constraint " + name);
        }
        return constraint;
    }

    public Constraint getConstraint(String name) {
        return this.constraints.get(name);
    }

    public Variable getVariable(int index) {
        return this.variables.get(index);
    }

    public int indexOf(Variable variable) {
        return variable.index;
    }

    public TrinityIntegerProgram copy() {
        TrinityIntegerProgram copy = new TrinityIntegerProgram();
        for (Variable variable : this.variables) {
            Variable cloned = copy.addVariable().lower(variable.lower).weight(variable.weight);
            if (variable.upper != null) cloned.upper(variable.upper);
        }
        this.constraints.forEach((name, constraint) -> {
            Constraint cloned = copy.addConstraint(name);
            cloned.terms.putAll(constraint.terms);
            cloned.lower = constraint.lower;
            cloned.upper = constraint.upper;
            cloned.weight = constraint.weight;
        });
        return copy;
    }

    public Solution minimize(int stateLimit, TrinityPlanningControl control) {
        if (stateLimit <= 0) throw new IllegalArgumentException("Trinity integer search needs a positive work limit");
        if (control.cancellationRequested()) return Solution.stopped(Status.CANCELLED, 0);
        if (control.deadlineExceeded()) return Solution.stopped(Status.TIMEOUT, 0);
        BigInteger[] objective = new BigInteger[this.variables.size()];
        BigInteger[] lower = new BigInteger[this.variables.size()];
        @Nullable
        BigInteger[] upper = new BigInteger[this.variables.size()];
        BigInteger extent = ONE;
        for (Variable variable : this.variables) {
            objective[variable.index] = variable.weight;
            lower[variable.index] = variable.lower;
            upper[variable.index] = variable.upper;
            extent = extent.max(variable.lower);
            if (variable.upper != null && variable.lower.compareTo(variable.upper) > 0) {
                return Solution.stopped(Status.INFEASIBLE, 0);
            }
        }

        ObjectArrayList<Row> rows = new ObjectArrayList<>();
        for (Constraint constraint : this.constraints.values()) {
            if (constraint.lower != null) {
                rows.add(Row.of(constraint, constraint.lower, false));
                extent = extent.max(constraint.lower.abs());
            }
            if (constraint.upper != null) {
                rows.add(Row.of(constraint, constraint.upper.negate(), true));
                extent = extent.max(constraint.upper.abs());
            }
            if (constraint.weight.signum() != 0) {
                constraint.terms.forEach((index, coefficient) -> objective[index] = objective[index]
                        .add(coefficient.multiply(constraint.weight)));
            }
        }

        Search search = new Search(rows, objective, control, stateLimit);
        Box domain = new Box(lower, upper);
        if (!search.tighten(domain)) return search.result(Status.INFEASIBLE);
        Status stopped = search.stopStatus();
        if (stopped != null) return search.result(stopped);
        if (Arrays.stream(upper).noneMatch(bound -> bound == null)) {
            return search.result(search.traverse(domain));
        }

        BigInteger objectiveLower = domain.objectiveLowerBound(objective);
        // A compact window can find a witness. Its boundary never proves global infeasibility or optimality.
        BigInteger ceiling = extent.multiply(BigInteger.valueOf(Math.max(1L, lower.length)));
        for (int attempt = 0; attempt < OPEN_DOMAIN_ATTEMPTS; attempt++) {
            BigInteger[] bounded = new BigInteger[upper.length];
            for (int index = 0; index < bounded.length; index++) {
                bounded[index] = upper[index] == null ? ceiling.max(lower[index]) : upper[index];
            }
            Status status = search.traverse(new Box(lower.clone(), bounded));
            if (status == Status.OPTIMAL) {
                return search.result(search.incumbentValue.equals(objectiveLower) || search.provesOptimal(domain) ?
                        Status.OPTIMAL : Status.FEASIBLE);
            }
            if (status != Status.INFEASIBLE) return search.result(status);
            ceiling = ceiling.shiftLeft(2);
        }
        return search.result(Status.LIMIT);
    }

    public static final class Variable {

        private final int index;
        private BigInteger lower = ZERO;
        private @Nullable BigInteger upper;
        private BigInteger weight = ZERO;

        private Variable(int index) {
            this.index = index;
        }

        public Variable lower(BigInteger value) {
            if (value.signum() < 0) throw new IllegalArgumentException("Integer model variables must be non-negative");
            this.lower = value;
            return this;
        }

        public Variable upper(BigInteger value) {
            this.upper = value;
            return this;
        }

        public Variable level(BigInteger value) {
            return lower(value).upper(value);
        }

        public Variable weight(BigInteger value) {
            this.weight = value;
            return this;
        }
    }

    public static final class Constraint {

        private final Int2ObjectLinkedOpenHashMap<BigInteger> terms = new Int2ObjectLinkedOpenHashMap<>();
        private @Nullable BigInteger lower;
        private @Nullable BigInteger upper;
        private BigInteger weight = ZERO;

        public Constraint set(Variable variable, BigInteger coefficient) {
            if (coefficient.signum() == 0) this.terms.remove(variable.index);
            else this.terms.put(variable.index, coefficient);
            return this;
        }

        public Constraint lower(BigInteger value) {
            this.lower = value;
            return this;
        }

        public Constraint upper(BigInteger value) {
            this.upper = value;
            return this;
        }

        public Constraint level(BigInteger value) {
            return lower(value).upper(value);
        }

        public Constraint weight(BigInteger value) {
            this.weight = value;
            return this;
        }
    }

    public enum Status {

        OPTIMAL,
        FEASIBLE,
        INFEASIBLE,
        LIMIT,
        TIMEOUT,
        CANCELLED
    }

    public record Solution(Status status, ObjectList<BigInteger> values, int states) {

        private static Solution stopped(Status status, int states) {
            return new Solution(status, ObjectList.of(), states);
        }

        public boolean feasible() {
            return this.status == Status.FEASIBLE || this.status == Status.OPTIMAL;
        }

        public boolean optimal() {
            return this.status == Status.OPTIMAL;
        }
    }

    private record Row(int[] indexes, BigInteger[] coefficients, BigInteger minimum) {

        private static Row of(Constraint constraint, BigInteger minimum, boolean negate) {
            int[] indexes = constraint.terms.keySet().toIntArray();
            BigInteger[] coefficients = constraint.terms.values().toArray(BigInteger[]::new);
            if (negate) {
                for (int index = 0; index < coefficients.length; index++) {
                    coefficients[index] = coefficients[index].negate();
                }
            }
            BigInteger divisor = ZERO;
            for (BigInteger coefficient : coefficients) divisor = divisor.gcd(coefficient.abs());
            if (divisor.compareTo(ONE) > 0) {
                for (int index = 0; index < coefficients.length; index++) {
                    coefficients[index] = coefficients[index].divide(divisor);
                }
                minimum = ceilDivide(minimum, divisor);
            }
            return new Row(indexes, coefficients, minimum);
        }

        private BigInteger evaluate(BigInteger[] point) {
            BigInteger value = ZERO;
            for (int term = 0; term < this.indexes.length; term++) {
                value = value.add(this.coefficients[term].multiply(point[this.indexes[term]]));
            }
            return value;
        }

        private static BigInteger ceilDivide(BigInteger numerator, BigInteger denominator) {
            BigInteger[] division = numerator.divideAndRemainder(denominator);
            return division[1].signum() > 0 ? division[0].add(ONE) : division[0];
        }
    }

    private record Box(BigInteger[] lower, @Nullable BigInteger[] upper) {

        private Box copy() {
            return new Box(this.lower.clone(), this.upper.clone());
        }

        private BigInteger preferred(int index, BigInteger[] objective) {
            return objective[index].signum() < 0 ? this.upper[index] : this.lower[index];
        }

        private @Nullable BigInteger objectiveLowerBound(BigInteger[] objective) {
            BigInteger bound = ZERO;
            for (int index = 0; index < objective.length; index++) {
                BigInteger endpoint = objective[index].signum() >= 0 ? this.lower[index] : this.upper[index];
                if (endpoint == null) return null;
                bound = bound.add(endpoint.multiply(objective[index]));
            }
            return bound;
        }
    }

    private static final class Search {

        private final ObjectList<Row> rows;
        private final BigInteger[] objective;
        private final TrinityPlanningControl control;
        private final int stateLimit;
        private int states;
        private @Nullable BigInteger[] incumbent;
        private @Nullable BigInteger incumbentValue;

        private Search(ObjectList<Row> rows, BigInteger[] objective, TrinityPlanningControl control, int stateLimit) {
            this.rows = rows;
            this.objective = objective;
            this.control = control;
            this.stateLimit = stateLimit;
        }

        private @Nullable Status stopStatus() {
            if (this.control.cancellationRequested()) return Status.CANCELLED;
            if (this.control.deadlineExceeded()) return this.incumbent == null ? Status.TIMEOUT : Status.FEASIBLE;
            if (this.states >= this.stateLimit) return this.incumbent == null ? Status.LIMIT : Status.FEASIBLE;
            return null;
        }

        // The traversal partitions finite boxes. Open bounds are used only for propagation and proof outside it.
        private Status traverse(Box root) {
            ArrayDeque<Box> pending = new ArrayDeque<>();
            pending.push(root);
            while (!pending.isEmpty()) {
                Status stopped = stopStatus();
                if (stopped != null) return stopped;
                this.states++;

                Box box = pending.pop();
                if (!tighten(box)) continue;
                if (this.control.cancellationRequested()) return Status.CANCELLED;
                if (this.control.deadlineExceeded()) return this.incumbent == null ? Status.TIMEOUT : Status.FEASIBLE;
                BigInteger[] point = new BigInteger[this.objective.length];
                BigInteger value = ZERO;
                for (int index = 0; index < point.length; index++) {
                    point[index] = box.preferred(index, this.objective);
                    value = value.add(point[index].multiply(this.objective[index]));
                }
                if (this.incumbentValue != null && value.compareTo(this.incumbentValue) >= 0) continue;

                Row violated = null;
                BigInteger deficit = ZERO;
                for (Row row : this.rows) {
                    deficit = row.minimum.subtract(row.evaluate(point));
                    if (deficit.signum() > 0) {
                        violated = row;
                        break;
                    }
                }
                if (violated == null) {
                    this.incumbent = point;
                    this.incumbentValue = value;
                    if (value.signum() == 0 && Arrays.stream(this.objective).allMatch(weight -> weight.signum() >= 0)) {
                        return Status.OPTIMAL;
                    }
                    continue;
                }

                split(pending, box, point, violated, deficit);
            }
            if (this.incumbent == null) return Status.INFEASIBLE;
            return Status.OPTIMAL;
        }

        private boolean tighten(Box box) {
            // An unproductive feedback loop can tighten a range by one per pass.
            // Bound propagation work before partitioning the remaining exact domain.
            for (int pass = 0; pass < PROPAGATION_PASSES; pass++) {
                boolean changed = false;
                for (Row row : this.rows) {
                    if (this.control.cancellationRequested() || this.control.deadlineExceeded()) return true;
                    var propagation = propagate(box, row);
                    if (propagation == Propagation.CONTRADICTION) return false;
                    changed |= propagation == Propagation.TIGHTENED;
                }
                if (!changed) break;
            }
            return true;
        }

        private boolean provesOptimal(Box domain) {
            if (this.control.cancellationRequested() || this.control.deadlineExceeded()) return false;
            var improvement = new Constraint();
            for (int index = 0; index < this.objective.length; index++) {
                BigInteger weight = this.objective[index];
                if (weight.signum() != 0) improvement.terms.put(index, weight);
            }
            Row better = Row.of(improvement, this.incumbentValue.subtract(ONE).negate(), true);
            Box probe = domain.copy();
            for (int pass = 0; pass < PROPAGATION_PASSES; pass++) {
                Propagation tightened = propagate(probe, better);
                if (tightened == Propagation.CONTRADICTION) return true;
                if (tightened == Propagation.UNCHANGED) break;
                if (!tighten(probe)) return true;
            }
            return false;
        }

        private static Propagation propagate(Box box, Row row) {
            BigInteger maximum = ZERO;
            int openTerm = -1;
            for (int term = 0; term < row.indexes.length; term++) {
                int index = row.indexes[term];
                BigInteger coefficient = row.coefficients[term];
                BigInteger endpoint = coefficient.signum() > 0 ? box.upper[index] : box.lower[index];
                if (endpoint == null) {
                    if (openTerm >= 0) return Propagation.UNCHANGED;
                    openTerm = term;
                } else {
                    maximum = maximum.add(endpoint.multiply(coefficient));
                }
            }
            if (openTerm >= 0) {
                int index = row.indexes[openTerm];
                BigInteger bound = Row.ceilDivide(row.minimum.subtract(maximum), row.coefficients[openTerm]);
                if (bound.compareTo(box.lower[index]) <= 0) return Propagation.UNCHANGED;
                box.lower[index] = bound;
                return Propagation.TIGHTENED;
            }

            BigInteger slack = maximum.subtract(row.minimum);
            if (slack.signum() < 0) return Propagation.CONTRADICTION;
            boolean changed = false;
            for (int term = 0; term < row.indexes.length; term++) {
                int index = row.indexes[term];
                BigInteger coefficient = row.coefficients[term];
                BigInteger distance = slack.divide(coefficient.abs());
                if (coefficient.signum() > 0) {
                    BigInteger bound = box.upper[index].subtract(distance);
                    if (bound.compareTo(box.lower[index]) > 0) {
                        box.lower[index] = bound;
                        changed = true;
                    }
                } else {
                    BigInteger bound = box.lower[index].add(distance);
                    BigInteger upper = box.upper[index];
                    if (upper == null || bound.compareTo(upper) < 0) {
                        box.upper[index] = bound;
                        changed = true;
                    }
                }
            }
            return changed ? Propagation.TIGHTENED : Propagation.UNCHANGED;
        }

        private static void split(ArrayDeque<Box> pending, Box box, BigInteger[] point, Row row, BigInteger deficit) {
            int chosen = -1;
            BigInteger effect = ZERO;
            BigInteger gain = ZERO;
            for (int term = 0; term < row.indexes.length; term++) {
                int index = row.indexes[term];
                BigInteger coefficient = row.coefficients[term];
                BigInteger endpoint = coefficient.signum() > 0 ? box.upper[index] : box.lower[index];
                BigInteger improvement = endpoint.subtract(point[index]).multiply(coefficient);
                if (improvement.compareTo(gain) > 0) {
                    chosen = index;
                    gain = improvement;
                    effect = coefficient;
                }
            }
            if (chosen < 0) return;

            BigInteger magnitude = effect.abs();
            BigInteger step = deficit.subtract(ONE).divide(magnitude).add(ONE);
            Box deferred = box.copy();
            if (effect.signum() > 0) {
                BigInteger cut = point[chosen].add(step).min(box.upper[chosen]);
                deferred.upper[chosen] = cut.subtract(ONE);
                box.lower[chosen] = cut;
            } else {
                BigInteger cut = point[chosen].subtract(step).max(box.lower[chosen]);
                deferred.lower[chosen] = cut.add(ONE);
                box.upper[chosen] = cut;
            }
            pending.push(deferred);
            pending.push(box);
        }

        private Solution result(Status status) {
            if (this.control.cancellationRequested()) return Solution.stopped(Status.CANCELLED, this.states);
            if (this.incumbent == null) return Solution.stopped(status, this.states);
            return new Solution(status, ObjectList.of(this.incumbent.clone()), this.states);
        }
    }

    private enum Propagation {

        UNCHANGED,
        TIGHTENED,
        CONTRADICTION
    }
}
