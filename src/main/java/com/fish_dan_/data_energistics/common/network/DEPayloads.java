package com.fish_dan_.data_energistics.common.network;

import com.fish_dan_.data_energistics.common.network.packets.action.CannonChargePayload;
import com.fish_dan_.data_energistics.common.network.packets.action.ChromaticGlowPayload;
import com.fish_dan_.data_energistics.common.network.packets.action.ConnectorClipboardPayload;
import com.fish_dan_.data_energistics.common.network.packets.action.ConnectorScrollPayload;
import com.fish_dan_.data_energistics.common.network.packets.action.DataTeleportAnchorKnifeTeleportPayload;
import com.fish_dan_.data_energistics.common.network.packets.action.DigitalStorageDepotBucketModePayload;
import com.fish_dan_.data_energistics.common.network.packets.action.DigitalStorageDepotScrollPayload;
import com.fish_dan_.data_energistics.common.network.packets.action.MatterConvergingCrossbowModePayload;
import com.fish_dan_.data_energistics.common.network.packets.action.MeVacuumLaunchPayload;
import com.fish_dan_.data_energistics.common.network.packets.action.RailChainPayload;
import com.fish_dan_.data_energistics.common.network.packets.anvil.DigitalAnvilActionPayload;
import com.fish_dan_.data_energistics.common.network.packets.anvil.DigitalAnvilSnapshotPayload;
import com.fish_dan_.data_energistics.common.network.packets.crafting.tree.action.CraftingPlanTreeActionPayload;
import com.fish_dan_.data_energistics.common.network.packets.crafting.tree.protocol.CraftingPlanGraphPayload;
import com.fish_dan_.data_energistics.common.network.packets.input.KeyStatePayload;
import com.fish_dan_.data_energistics.common.network.packets.meteorite.DataMeteoriteCompassRequestPayload;
import com.fish_dan_.data_energistics.common.network.packets.meteorite.DataMeteoriteCompassResponsePayload;
import com.fish_dan_.data_energistics.common.network.packets.orbital.control.OrbitalControlConsoleOpenPayload;
import com.fish_dan_.data_energistics.common.network.packets.orbital.control.OrbitalControlHudSnapshotPayload;
import com.fish_dan_.data_energistics.common.network.packets.orbital.control.OrbitalControlOpenPayload;
import com.fish_dan_.data_energistics.common.network.packets.orbital.map.OrbitalTacticalMapRequestPayload;
import com.fish_dan_.data_energistics.common.network.packets.orbital.map.OrbitalTacticalMapResponsePayload;
import com.fish_dan_.data_energistics.common.network.packets.orbital.projection.OrbitalProjectionVisualsPayload;
import com.fish_dan_.data_energistics.common.network.packets.orbital.visual.OrbitalAttackVisualsPayload;
import com.fish_dan_.data_energistics.common.network.packets.patternencoding.MultiblockPatternTransferPayload;
import com.fish_dan_.data_energistics.common.network.packets.patternencoding.PatternEncodingPreferencesAckPayload;
import com.fish_dan_.data_energistics.common.network.packets.patternencoding.PatternEncodingPreferencesSyncPayload;
import com.fish_dan_.data_energistics.common.network.packets.patternencoding.PatternUploadSucceededPayload;
import com.fish_dan_.data_energistics.common.network.packets.research.ResearchActionPayload;
import com.fish_dan_.data_energistics.common.network.packets.research.ResearchMaterialFavoritesPayload;
import com.fish_dan_.data_energistics.common.network.packets.research.ResearchSnapshotPayload;
import com.fish_dan_.data_energistics.common.network.packets.sanctifier.SanctifierCarryPayload;
import com.fish_dan_.data_energistics.common.network.packets.terminal.TerminalNavigationPayload;
import com.fish_dan_.data_energistics.common.network.packets.terminal.TerminalPickBlockPayload;
import com.fish_dan_.data_energistics.common.network.packets.terminal.TerminalPickBlockStatePayload;
import com.fish_dan_.data_energistics.common.network.packets.terminal.TerminalRequestPayload;
import com.fish_dan_.data_energistics.common.network.packets.terminal.TerminalRestockStatePayload;
import com.fish_dan_.data_energistics.common.network.packets.terminal.TerminalSnapshotPayload;
import com.fish_dan_.data_energistics.common.network.packets.tower.DataDistributionTowerTargetsPayload;
import com.fish_dan_.data_energistics.common.network.packets.trinity.TrinityHostedActionResponsePayload;
import com.fish_dan_.data_energistics.common.network.packets.trinity.TrinityHostedAutoBuildPayload;
import com.fish_dan_.data_energistics.common.network.packets.trinity.TrinityHostedPatternMigrationPayload;
import com.fish_dan_.data_energistics.common.network.packets.trinity.TrinityHostedPatternQuickMovePayload;
import com.fish_dan_.data_energistics.common.network.packets.trinity.TrinityHostedPatternSlotPayload;
import com.fish_dan_.data_energistics.common.network.packets.trinity.TrinityHostedPriorityPayload;
import com.fish_dan_.data_energistics.common.network.packets.trinity.TrinityOpenCpuStatusPayload;
import com.fish_dan_.data_energistics.common.network.packets.trinity.TrinityRefundPatternsPayload;
import com.fish_dan_.data_energistics.common.network.packets.trinity.TrinityRefundRetainedItemsPayload;
import com.fish_dan_.data_energistics.common.network.packets.trinity.crafting.progress.TrinityCraftConfirmPlanningProgressPayload;
import com.fish_dan_.data_energistics.common.network.packets.trinity.crafting.protocol.TrinityCraftConfirmCyclePayload;
import com.fish_dan_.data_energistics.common.network.packets.trinity.crafting.protocol.TrinityCraftingStatusPayload;
import com.fish_dan_.data_energistics.common.network.packets.ui.HostUiRequestPayload;
import com.fish_dan_.data_energistics.common.network.packets.ui.HostUiResponsePayload;
import com.fish_dan_.data_energistics.common.network.packets.ui.UniversalTerminalCyclePayload;
import com.fish_dan_.data_energistics.common.network.packets.ui.UniversalTerminalSelectPayload;
import com.fish_dan_.data_energistics.common.network.packets.ui.UniversalTerminalStateSyncPayload;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class DEPayloads {

    private DEPayloads() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("26.10.27");
        registrar.playToServer(KeyStatePayload.TYPE, KeyStatePayload.STREAM_CODEC, KeyStatePayload::handle);
        registrar.playToClient(SanctifierCarryPayload.TYPE, SanctifierCarryPayload.STREAM_CODEC, SanctifierCarryPayload::handle);
        registrar.playToServer(TerminalRequestPayload.TYPE, TerminalRequestPayload.STREAM_CODEC, TerminalRequestPayload::handle);
        registrar.playToServer(TerminalPickBlockPayload.TYPE, TerminalPickBlockPayload.STREAM_CODEC, TerminalPickBlockPayload::handle);
        registrar.playToClient(TerminalPickBlockStatePayload.TYPE, TerminalPickBlockStatePayload.STREAM_CODEC, TerminalPickBlockStatePayload::handle);
        registrar.playToClient(TerminalRestockStatePayload.TYPE, TerminalRestockStatePayload.STREAM_CODEC, TerminalRestockStatePayload::handle);
        registrar.playToClient(TerminalSnapshotPayload.TYPE, TerminalSnapshotPayload.STREAM_CODEC, TerminalSnapshotPayload::handle);
        registrar.playToClient(TerminalNavigationPayload.TYPE, TerminalNavigationPayload.STREAM_CODEC, TerminalNavigationPayload::handle);
        registrar.playToServer(DigitalAnvilActionPayload.TYPE, DigitalAnvilActionPayload.STREAM_CODEC, DigitalAnvilActionPayload::handle);
        registrar.playToClient(DigitalAnvilSnapshotPayload.TYPE, DigitalAnvilSnapshotPayload.STREAM_CODEC, DigitalAnvilSnapshotPayload::handle);
        registrar.playToServer(ResearchActionPayload.TYPE, ResearchActionPayload.STREAM_CODEC, ResearchActionPayload::handle);
        registrar.playToClient(ResearchSnapshotPayload.TYPE, ResearchSnapshotPayload.STREAM_CODEC, ResearchSnapshotPayload::handle);
        registrar.playToClient(ResearchMaterialFavoritesPayload.TYPE, ResearchMaterialFavoritesPayload.STREAM_CODEC, ResearchMaterialFavoritesPayload::handle);
        registrar.playToServer(CannonChargePayload.TYPE, CannonChargePayload.STREAM_CODEC, CannonChargePayload::handle);
        registrar.playToClient(RailChainPayload.TYPE, RailChainPayload.STREAM_CODEC, RailChainPayload::handle);
        registrar.playToClient(ChromaticGlowPayload.TYPE, ChromaticGlowPayload.STREAM_CODEC, ChromaticGlowPayload::handle);
        registrar.playToClient(TrinityCraftingStatusPayload.TYPE, TrinityCraftingStatusPayload.STREAM_CODEC, TrinityCraftingStatusPayload::handle);
        registrar.playToClient(CraftingPlanGraphPayload.TYPE, CraftingPlanGraphPayload.STREAM_CODEC, CraftingPlanGraphPayload::handle);
        registrar.playToServer(CraftingPlanTreeActionPayload.TYPE, CraftingPlanTreeActionPayload.STREAM_CODEC, CraftingPlanTreeActionPayload::handle);
        registrar.playToServer(
                PatternEncodingPreferencesSyncPayload.TYPE,
                PatternEncodingPreferencesSyncPayload.STREAM_CODEC,
                PatternEncodingPreferencesSyncPayload::handle);
        registrar.playToClient(
                PatternEncodingPreferencesAckPayload.TYPE,
                PatternEncodingPreferencesAckPayload.STREAM_CODEC,
                PatternEncodingPreferencesAckPayload::handle);
        registrar.playToClient(
                PatternUploadSucceededPayload.TYPE,
                PatternUploadSucceededPayload.STREAM_CODEC,
                PatternUploadSucceededPayload::handle);
        registrar.playToClient(
                DataDistributionTowerTargetsPayload.TYPE,
                DataDistributionTowerTargetsPayload.STREAM_CODEC,
                DataDistributionTowerTargetsPayload::handle);
        registrar.playToClient(
                TrinityCraftConfirmCyclePayload.TYPE,
                TrinityCraftConfirmCyclePayload.STREAM_CODEC,
                TrinityCraftConfirmCyclePayload::handle);
        registrar.playToClient(
                TrinityCraftConfirmPlanningProgressPayload.TYPE,
                TrinityCraftConfirmPlanningProgressPayload.STREAM_CODEC,
                TrinityCraftConfirmPlanningProgressPayload::handle);
        registrar.playToClient(
                UniversalTerminalStateSyncPayload.TYPE,
                UniversalTerminalStateSyncPayload.STREAM_CODEC,
                UniversalTerminalStateSyncPayload::handle);
        registrar.playToServer(
                UniversalTerminalCyclePayload.TYPE,
                UniversalTerminalCyclePayload.STREAM_CODEC,
                UniversalTerminalCyclePayload::handle);
        registrar.playToServer(
                UniversalTerminalSelectPayload.TYPE,
                UniversalTerminalSelectPayload.STREAM_CODEC,
                UniversalTerminalSelectPayload::handle);
        registrar.playToServer(
                DataTeleportAnchorKnifeTeleportPayload.TYPE,
                DataTeleportAnchorKnifeTeleportPayload.STREAM_CODEC,
                DataTeleportAnchorKnifeTeleportPayload::handle);
        registrar.playToServer(
                MeVacuumLaunchPayload.TYPE,
                MeVacuumLaunchPayload.STREAM_CODEC,
                MeVacuumLaunchPayload::handle);
        registrar.playToServer(
                MatterConvergingCrossbowModePayload.TYPE,
                MatterConvergingCrossbowModePayload.STREAM_CODEC,
                MatterConvergingCrossbowModePayload::handle);
        registrar.playToServer(
                DigitalStorageDepotScrollPayload.TYPE,
                DigitalStorageDepotScrollPayload.STREAM_CODEC,
                DigitalStorageDepotScrollPayload::handle);
        registrar.playToServer(
                ConnectorScrollPayload.TYPE,
                ConnectorScrollPayload.STREAM_CODEC,
                ConnectorScrollPayload::handle);
        registrar.playToServer(
                ConnectorClipboardPayload.TYPE,
                ConnectorClipboardPayload.STREAM_CODEC,
                ConnectorClipboardPayload::handle);
        registrar.playToServer(
                DigitalStorageDepotBucketModePayload.TYPE,
                DigitalStorageDepotBucketModePayload.STREAM_CODEC,
                DigitalStorageDepotBucketModePayload::handle);
        registrar.playToServer(
                HostUiRequestPayload.TYPE,
                HostUiRequestPayload.STREAM_CODEC,
                HostUiRequestPayload::handle);
        registrar.playToServer(
                MultiblockPatternTransferPayload.TYPE,
                MultiblockPatternTransferPayload.STREAM_CODEC,
                MultiblockPatternTransferPayload::handle);
        registrar.playToClient(
                HostUiResponsePayload.TYPE,
                HostUiResponsePayload.STREAM_CODEC,
                HostUiResponsePayload::handle);
        registrar.playToServer(
                TrinityHostedAutoBuildPayload.TYPE,
                TrinityHostedAutoBuildPayload.STREAM_CODEC,
                TrinityHostedAutoBuildPayload::handle);
        registrar.playToServer(
                TrinityHostedPriorityPayload.TYPE,
                TrinityHostedPriorityPayload.STREAM_CODEC,
                TrinityHostedPriorityPayload::handle);
        registrar.playToServer(
                TrinityHostedPatternSlotPayload.TYPE,
                TrinityHostedPatternSlotPayload.STREAM_CODEC,
                TrinityHostedPatternSlotPayload::handle);
        registrar.playToServer(
                TrinityHostedPatternQuickMovePayload.TYPE,
                TrinityHostedPatternQuickMovePayload.STREAM_CODEC,
                TrinityHostedPatternQuickMovePayload::handle);
        registrar.playToServer(
                TrinityHostedPatternMigrationPayload.TYPE,
                TrinityHostedPatternMigrationPayload.STREAM_CODEC,
                TrinityHostedPatternMigrationPayload::handle);
        registrar.playToServer(
                TrinityRefundPatternsPayload.TYPE,
                TrinityRefundPatternsPayload.STREAM_CODEC,
                TrinityRefundPatternsPayload::handle);
        registrar.playToServer(
                TrinityRefundRetainedItemsPayload.TYPE,
                TrinityRefundRetainedItemsPayload.STREAM_CODEC,
                TrinityRefundRetainedItemsPayload::handle);
        registrar.playToServer(
                TrinityOpenCpuStatusPayload.TYPE,
                TrinityOpenCpuStatusPayload.STREAM_CODEC,
                TrinityOpenCpuStatusPayload::handle);
        registrar.playToClient(
                TrinityHostedActionResponsePayload.TYPE,
                TrinityHostedActionResponsePayload.STREAM_CODEC,
                TrinityHostedActionResponsePayload::handle);
        registrar.playToServer(
                DataMeteoriteCompassRequestPayload.TYPE,
                DataMeteoriteCompassRequestPayload.STREAM_CODEC,
                DataMeteoriteCompassRequestPayload::handle);
        registrar.playToClient(
                DataMeteoriteCompassResponsePayload.TYPE,
                DataMeteoriteCompassResponsePayload.STREAM_CODEC,
                DataMeteoriteCompassResponsePayload::handle);
        registrar.playToClient(
                OrbitalControlHudSnapshotPayload.TYPE,
                OrbitalControlHudSnapshotPayload.STREAM_CODEC,
                OrbitalControlHudSnapshotPayload::handle);
        registrar.playToServer(
                OrbitalControlOpenPayload.TYPE,
                OrbitalControlOpenPayload.STREAM_CODEC,
                OrbitalControlOpenPayload::handle);
        registrar.playToServer(
                OrbitalControlConsoleOpenPayload.TYPE,
                OrbitalControlConsoleOpenPayload.STREAM_CODEC,
                OrbitalControlConsoleOpenPayload::handle);
        registrar.playToServer(
                OrbitalTacticalMapRequestPayload.TYPE,
                OrbitalTacticalMapRequestPayload.STREAM_CODEC,
                OrbitalTacticalMapRequestPayload::handle);
        registrar.playToClient(
                OrbitalTacticalMapResponsePayload.TYPE,
                OrbitalTacticalMapResponsePayload.STREAM_CODEC,
                OrbitalTacticalMapResponsePayload::handle);
        registrar.playToClient(
                OrbitalAttackVisualsPayload.TYPE,
                OrbitalAttackVisualsPayload.STREAM_CODEC,
                OrbitalAttackVisualsPayload::handle);
        registrar.playToClient(
                OrbitalProjectionVisualsPayload.TYPE,
                OrbitalProjectionVisualsPayload.STREAM_CODEC,
                OrbitalProjectionVisualsPayload::handle);
    }
}
