package com.fish_dan_.data_energistics.common.crafting.trinity.planning.diagnostic;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnostic;

import appeng.api.stacks.GenericStack;

import java.util.concurrent.atomic.AtomicLong;

/** Writes terminal planning evidence without requiring developer logging or an ojAlgo invocation. */
public final class TrinityPlanningFailureLog {

    private static final AtomicLong REPORT_SEQUENCE = new AtomicLong();

    private TrinityPlanningFailureLog() {}

    /**
     * Records one newly constructed terminal diagnostic, never a menu refresh or intermediate search rejection.
     * May run on the server or a planning worker; immutable evidence is logged with a process-local report ID
     * so concurrent requests remain distinguishable. Amounts retain their full decimal representation.
     *
     * @param output           original requested item or fluid and amount
     * @param diagnostic       final reason and any exact material or cycle evidence, without inventing missing evidence
     * @param calculationNanos elapsed planning time, or zero when planning stopped before calculation
     */
    public static void write(GenericStack output, TrinityPlanningDiagnostic diagnostic, long calculationNanos) {
        long report = REPORT_SEQUENCE.incrementAndGet();
        Data_Energistics.LOGGER.info(
                "Trinity planning failure report={} target={} requestedAmount={} reason={} message={} metadata={} calculationNanos={}",
                report, output.what(), output.amount(), diagnostic.code(), diagnostic.message().getString(),
                diagnostic.metadata(), calculationNanos);

        diagnostic.inputShortage().ifPresent(shortage -> Data_Energistics.LOGGER.info(
                "Trinity planning shortage report={} key={} required={} available={} missing={}",
                report, shortage.key(), shortage.required(), shortage.available(), shortage.missing()));

        diagnostic.partialPlan().ifPresent(partial -> {
            Data_Energistics.LOGGER.info(
                    "Trinity planning partial report={} usedKinds={} emittedKinds={} demandKinds={} exactShortageKinds={} selectedVariants={}",
                    report, partial.usedItems().size(), partial.emittedItems().size(), partial.missingItems().size(),
                    partial.inputRequirements().size(), partial.selectedFirings().size());
            partial.inputRequirements().forEach((key, requirement) -> Data_Energistics.LOGGER.info(
                    "Trinity planning shortage report={} key={} required={} available={} missing={}",
                    report, key, requirement.required(), requirement.available(), requirement.missing()));
            partial.missingItems().forEach((key, amount) -> {
                if (!partial.inputRequirements().containsKey(key)) {
                    Data_Energistics.LOGGER.info(
                            "Trinity planning unresolved report={} key={} amount={}", report, key, amount);
                }
            });
            partial.usedItems().forEach((key, amount) -> Data_Energistics.LOGGER.info(
                    "Trinity planning reserved report={} key={} amount={}", report, key, amount));
        });

        for (TrinityCycleDiagnosticEvidence cycle : diagnostic.cycleEvidence()) {
            Data_Energistics.LOGGER.info(
                    "Trinity planning cycle report={} component={} repetitions={} quality={} seedKinds={} inputKinds={}",
                    report, cycle.componentIndex(), cycle.repetitions(), cycle.quality(),
                    cycle.minimumSeed().size(), cycle.initialInputs().size());
            cycle.minimumSeed().forEach((key, amount) -> Data_Energistics.LOGGER.info(
                    "Trinity planning cycle seed report={} component={} key={} minimum={} plannedInitial={}",
                    report, cycle.componentIndex(), key, amount, cycle.initialInputs().get(key)));
        }
        Data_Energistics.LOGGER.info("Trinity planning failure end report={}", report);
    }
}
