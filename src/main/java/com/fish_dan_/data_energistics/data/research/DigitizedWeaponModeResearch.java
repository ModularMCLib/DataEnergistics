package com.fish_dan_.data_energistics.data.research;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.api.research.RegisterResearchNodesEvent;
import com.fish_dan_.data_energistics.api.research.ResearchInput;
import com.fish_dan_.data_energistics.api.research.ResearchNode;
import com.fish_dan_.data_energistics.api.research.ResearchScope;
import com.fish_dan_.data_energistics.api.research.ResearchTarget;
import com.fish_dan_.data_energistics.common.data.DEItems;
import com.fish_dan_.data_energistics.common.item.powered.MatterConvergingCrossbowMode;
import com.fish_dan_.data_energistics.integration.ae.ae2.key.DataKey;

import appeng.api.stacks.AEItemKey;
import appeng.api.util.AEColor;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.core.definitions.AEParts;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;

import org.jspecify.annotations.NullMarked;

/** Registers the stack-bound research that enables each firing mode of the star shard. */
@NullMarked
public final class DigitizedWeaponModeResearch {

    private static final ResourceLocation TARGET = Data_Energistics.id("star_shard");
    private static final String NAME_PREFIX = "research.data_energistics.weapon_mode.";
    private static final int RESEARCH_TICKS = 200;
    private static final double AE_PER_TICK = 2_000;

    private DigitizedWeaponModeResearch() {}

    public static void register(RegisterResearchNodesEvent event) {
        ResearchNode.Builder grenade = node(MatterConvergingCrossbowMode.GRENADE)
                .icon(ResourceLocation.withDefaultNamespace("tnt"))
                .input(new ResearchInput(AEItemKey.of(AEItems.MATTER_CANNON), 1))
                .input(new ResearchInput(AEItemKey.of(Items.DROPPER), 4))
                .input(new ResearchInput(AEItemKey.of(DEItems.FLUIX_AGGREGATE_CRYSTAL_PROCESSOR.get()), 16))
                .input(new ResearchInput(AEItemKey.of(AEItems.ANNIHILATION_CORE), 8))
                .input(new ResearchInput(AEItemKey.of(AEItems.QUANTUM_ENTANGLED_SINGULARITY), 2))
                .input(new ResearchInput(DataKey.of(), 16));
        event.register(grenade.build());

        ResearchNode.Builder rail = node(MatterConvergingCrossbowMode.RAIL)
                .icon(ResourceLocation.withDefaultNamespace("lightning_rod"))
                .input(new ResearchInput(AEItemKey.of(DEItems.ME_VACUUM.get()), 1))
                .input(new ResearchInput(AEItemKey.of(DEItems.FLUIX_AGGREGATE_CRYSTAL_PROCESSOR.get()), 16))
                .input(new ResearchInput(AEItemKey.of(AEBlocks.CONDENSER), 2))
                .input(new ResearchInput(AEItemKey.of(DEItems.ME_BEAM_FORMER.get()), 3))
                .input(new ResearchInput(AEItemKey.of(DEItems.DATA_PROCESSOR.get()), 7))
                .input(new ResearchInput(DataKey.of(), 27));
        event.register(rail.build());

        ResearchNode.Builder crossbow = node(MatterConvergingCrossbowMode.CROSSBOW)
                .icon(ResourceLocation.withDefaultNamespace("crossbow"))
                .input(new ResearchInput(AEItemKey.of(AEItems.MATTER_CANNON), 1))
                .input(new ResearchInput(AEItemKey.of(Items.STRING), 17))
                .input(new ResearchInput(AEItemKey.of(DEItems.FLUIX_AGGREGATE_CRYSTAL_PROCESSOR.get()), 16))
                .input(new ResearchInput(AEItemKey.of(AEParts.COVERED_CABLE.item(AEColor.TRANSPARENT)), 6))
                .input(new ResearchInput(AEItemKey.of(AEParts.LEVEL_EMITTER), 1))
                .input(new ResearchInput(AEItemKey.of(DEItems.DATA_PROCESSOR.get()), 27))
                .input(new ResearchInput(AEItemKey.of(DEItems.DIGISIDIAN_MEMORIZE_INGOT.get()), 6))
                .input(new ResearchInput(DataKey.of(), 34));
        event.register(crossbow.build());
    }

    private static ResearchNode.Builder node(MatterConvergingCrossbowMode mode) {
        String name = NAME_PREFIX + mode.nameKey();
        return ResearchNode.builder(mode.researchId(), ResearchScope.ITEM_UPGRADE, name,
                name + ".description", RESEARCH_TICKS, AE_PER_TICK)
                .target(ResearchTarget.item(TARGET));
    }
}
