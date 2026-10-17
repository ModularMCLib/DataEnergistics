package com.fish_dan_.data_energistics.data.research;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.ae2.key.DataKey;
import com.fish_dan_.data_energistics.ae2.key.EchoKey;
import com.fish_dan_.data_energistics.ae2.key.StellarFluxKey;
import com.fish_dan_.data_energistics.api.research.RegisterResearchNodesEvent;
import com.fish_dan_.data_energistics.api.research.ResearchInput;
import com.fish_dan_.data_energistics.api.research.ResearchNode;
import com.fish_dan_.data_energistics.api.research.ResearchScope;
import com.fish_dan_.data_energistics.api.research.ResearchTarget;
import com.fish_dan_.data_energistics.common.anvil.DigitalAnvilUpgrade;
import com.fish_dan_.data_energistics.registry.DEItems;

import appeng.api.stacks.AEItemKey;
import appeng.core.definitions.AEItems;

/** Independent, datapack-overridable upgrades owned by each ME digital anvil item. */
public final class MeDigitalAnvilResearch {

    private MeDigitalAnvilResearch() {}

    public static void register(RegisterResearchNodesEvent event) {
        event.register(node(DigitalAnvilUpgrade.FIXED_PENALTY, 200, 2000, 16)
                .input(new ResearchInput(AEItemKey.of(DEItems.DATA_PROCESSOR.get()), 8))
                .input(new ResearchInput(AEItemKey.of(DEItems.DIGISIDIAN_MEMORIZE_INGOT.get()), 4)).build());
        event.register(node(DigitalAnvilUpgrade.UNRESTRICTED_TYPES, 300, 3000, 32)
                .input(new ResearchInput(AEItemKey.of(DEItems.DATA_PROCESSOR.get()), 16))
                .input(new ResearchInput(AEItemKey.of(DEItems.RESONANCE_CRYSTAL.get()), 8)).build());
        event.register(node(DigitalAnvilUpgrade.UNRESTRICTED_LEVELS, 400, 4000, 48)
                .input(new ResearchInput(AEItemKey.of(DEItems.RESONANCE_DIGITALIZATION_CORE.get()), 1))
                .input(new ResearchInput(StellarFluxKey.of(), 8)).build());
        event.register(node(DigitalAnvilUpgrade.COPY_ENCHANTMENTS, 500, 5000, 64)
                .input(new ResearchInput(AEItemKey.of(AEItems.QUANTUM_ENTANGLED_SINGULARITY), 2))
                .input(new ResearchInput(AEItemKey.of(DEItems.DATA_PROCESSOR.get()), 16))
                .input(new ResearchInput(EchoKey.of(), 16)).build());
    }

    private static ResearchNode.Builder node(DigitalAnvilUpgrade upgrade, int ticks, double energy, long data) {
        return ResearchNode.builder(upgrade.researchId(), ResearchScope.ITEM_UPGRADE, upgrade.translationKey(),
                upgrade.translationKey() + ".description", ticks, energy)
                .icon(Data_Energistics.id("me_digital_anvil"))
                .target(ResearchTarget.item(Data_Energistics.id("me_digital_anvil")))
                .input(new ResearchInput(DataKey.of(), data));
    }
}
