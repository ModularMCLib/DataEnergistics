package com.fish_dan_.data_energistics.common.crafting.trinity.planning.progress;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.neoforge.network.codec.NeoForgeStreamCodecs;

/**
 * Stable coarse-grained lifecycle boundaries of one Trinity initial planning request.
 *
 * <p>
 * These values describe real planner boundaries, rather than weighted portions of a synthetic global percentage.
 * The client must inspect the accompanying {@link TrinityPlanningProgressMeasure} before drawing any percentage.
 * </p>
 */
public enum TrinityPlanningProgressPhase {

    QUEUED("gui.data_energistics.craft_confirm.progress.phase.queued", false),
    CAPTURING_INPUT("gui.data_energistics.craft_confirm.progress.phase.capturing_input", false),
    DELEGATED_TO_AE2("gui.data_energistics.craft_confirm.progress.phase.delegated_to_ae2", true),
    REACHABLE_SUBGRAPH("gui.data_energistics.craft_confirm.progress.phase.reachable_subgraph", false),
    EXPANDING_PATTERNS("gui.data_energistics.craft_confirm.progress.phase.expanding_patterns", false),
    COMPACTING_VARIANTS("gui.data_energistics.craft_confirm.progress.phase.compacting_variants", false),
    ANALYZING_TOPOLOGY("gui.data_energistics.craft_confirm.progress.phase.analyzing_topology", false),
    BUILDING_STRUCTURAL_PROOFS("gui.data_energistics.craft_confirm.progress.phase.building_structural_proofs", false),
    LOADING_ROUTE_HINTS("gui.data_energistics.craft_confirm.progress.phase.loading_route_hints", false),
    PROJECTING_REQUEST("gui.data_energistics.craft_confirm.progress.phase.projecting_request", false),
    SOLVING_BOUNDED("gui.data_energistics.craft_confirm.progress.phase.solving_bounded", false),
    SOLVING_FALLBACK("gui.data_energistics.craft_confirm.progress.phase.solving_fallback", false),
    ASSEMBLING_PLAN("gui.data_energistics.craft_confirm.progress.phase.assembling_plan", false),
    VALIDATING_CPU_CAPACITY("gui.data_energistics.craft_confirm.progress.phase.validating_cpu_capacity", false),
    AWAITING_MENU_RESULT("gui.data_energistics.craft_confirm.progress.phase.awaiting_menu_result", false),
    WAITING_FOR_SHARED_RESULT("gui.data_energistics.craft_confirm.progress.phase.waiting_for_shared_result", false),
    READY("gui.data_energistics.craft_confirm.progress.phase.ready", true),
    DIAGNOSTIC("gui.data_energistics.craft_confirm.progress.phase.diagnostic", true),
    CANCELLED("gui.data_energistics.craft_confirm.progress.phase.cancelled", true);

    public static final StreamCodec<FriendlyByteBuf, TrinityPlanningProgressPhase> STREAM_CODEC = NeoForgeStreamCodecs.enumCodec(TrinityPlanningProgressPhase.class);

    public final String translationKey;
    public final boolean terminal;

    TrinityPlanningProgressPhase(String translationKey, boolean terminal) {
        this.translationKey = translationKey;
        this.terminal = terminal;
    }
}
