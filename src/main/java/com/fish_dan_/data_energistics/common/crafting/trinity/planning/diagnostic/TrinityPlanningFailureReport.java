package com.fish_dan_.data_energistics.common.crafting.trinity.planning.diagnostic;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnostic;
import com.fish_dan_.data_energistics.configuration.schema.DataEnergisticsConfiguration;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;

import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Writes standalone terminal planning evidence beside the captured ojAlgo models.
 */
public final class TrinityPlanningFailureReport {

    private static final AtomicBoolean WRITE_FAILURE_REPORTED = new AtomicBoolean();
    private static final DateTimeFormatter FILE_TIMESTAMP = DateTimeFormatter
            .ofPattern("yyyy-MM-dd_HH-mm-ss_SSS")
            .withZone(ZoneId.systemDefault());

    private TrinityPlanningFailureReport() {}

    /**
     * Records one newly constructed terminal diagnostic, never a menu refresh or intermediate search rejection.
     * May run on the server or a planning worker; each report gets a unique UTF-8 file, including across restarts.
     * Amounts retain their full decimal representation. Successful writes do not emit main-log messages.
     * File-write errors are reported once per process without changing the planning result.
     *
     * @param output           original requested item or fluid and amount
     * @param diagnostic       final reason and any exact material or cycle evidence, without inventing missing evidence
     * @param calculationNanos elapsed planning time, or zero when planning stopped before calculation
     */
    public static void write(GenericStack output, TrinityPlanningDiagnostic diagnostic, long calculationNanos) {
        if (!DataEnergisticsConfiguration.INSTANCE.developer.trinityPlanningFailureReports) {
            return;
        }

        StringBuilder report = new StringBuilder()
                .append("timestamp=").append(Instant.now()).append('\n')
                .append("target=").append(output.what()).append('\n')
                .append("requestedAmount=").append(output.amount()).append('\n')
                .append("reason=").append(diagnostic.code()).append('\n')
                .append("message=").append(diagnostic.message().getString()).append('\n')
                .append("metadata=").append(diagnostic.metadata()).append('\n')
                .append("calculationNanos=").append(calculationNanos).append('\n');

        diagnostic.inputShortage().ifPresent(shortage -> appendShortage(
                report, shortage.key(), shortage.required(), shortage.available(), shortage.missing()));

        diagnostic.partialPlan().ifPresent(partial -> {
            report.append("partial usedKinds=").append(partial.usedItems().size())
                    .append(" emittedKinds=").append(partial.emittedItems().size())
                    .append(" demandKinds=").append(partial.missingItems().size())
                    .append(" exactShortageKinds=").append(partial.inputRequirements().size())
                    .append(" selectedVariants=").append(partial.selectedFirings().size()).append('\n');
            partial.inputRequirements().forEach((key, requirement) -> appendShortage(
                    report, key, requirement.required(), requirement.available(), requirement.missing()));
            partial.missingItems().forEach((key, amount) -> {
                if (!partial.inputRequirements().containsKey(key)) {
                    report.append("unresolved key=").append(key).append(" amount=").append(amount).append('\n');
                }
            });
            partial.usedItems().forEach((key, amount) -> report.append("reserved key=").append(key)
                    .append(" amount=").append(amount).append('\n'));
        });

        for (TrinityCycleDiagnosticEvidence cycle : diagnostic.cycleEvidence()) {
            report.append("cycle component=").append(cycle.componentIndex())
                    .append(" repetitions=").append(cycle.repetitions())
                    .append(" quality=").append(cycle.quality())
                    .append(" seedKinds=").append(cycle.minimumSeed().size())
                    .append(" inputKinds=").append(cycle.initialInputs().size()).append('\n');
            cycle.minimumSeed().forEach((key, amount) -> report.append("cycle_seed component=")
                    .append(cycle.componentIndex()).append(" key=").append(key)
                    .append(" minimum=").append(amount)
                    .append(" plannedInitial=").append(cycle.initialInputs().get(key)).append('\n'));
        }

        try {
            Path directory = FMLPaths.GAMEDIR.get().resolve("logs").resolve("data_energistics").resolve("trinity");
            Files.createDirectories(directory);
            writeTimestampedReport(directory, report.toString());
        } catch (IOException failure) {
            if (WRITE_FAILURE_REPORTED.compareAndSet(false, true)) {
                Data_Energistics.LOGGER.error("Could not write standalone Trinity planning failure report", failure);
            }
        }
    }

    private static void writeTimestampedReport(Path directory, String report) throws IOException {
        String timestamp = FILE_TIMESTAMP.format(Instant.now());
        for (int suffix = 0; suffix < 1000; suffix++) {
            String name = suffix == 0 ?
                    "planning-failure-" + timestamp + ".txt" :
                    "planning-failure-" + timestamp + "-" + suffix + ".txt";
            Path file = directory.resolve(name);
            try {
                Files.writeString(
                        file,
                        report,
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE_NEW,
                        StandardOpenOption.WRITE);
                return;
            } catch (FileAlreadyExistsException ignored) {
                // A same-millisecond collision is resolved with a short local suffix.
            }
        }
        throw new IOException("Could not allocate a timestamped Trinity planning report name");
    }

    private static void appendShortage(StringBuilder report, AEKey key, BigInteger required,
                                       BigInteger available, BigInteger missing) {
        report.append("shortage key=").append(key).append(" required=").append(required)
                .append(" available=").append(available).append(" missing=").append(missing).append('\n');
    }
}
