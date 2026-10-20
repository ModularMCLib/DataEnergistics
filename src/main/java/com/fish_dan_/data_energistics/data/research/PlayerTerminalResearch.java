package com.fish_dan_.data_energistics.data.research;

import com.fish_dan_.data_energistics.ae2.key.DataKey;
import com.fish_dan_.data_energistics.ae2.key.EchoKey;
import com.fish_dan_.data_energistics.ae2.key.StellarFluxKey;
import com.fish_dan_.data_energistics.api.research.RegisterResearchNodesEvent;
import com.fish_dan_.data_energistics.api.research.ResearchInput;
import com.fish_dan_.data_energistics.api.research.ResearchNode;
import com.fish_dan_.data_energistics.api.research.ResearchScope;
import com.fish_dan_.data_energistics.common.anvil.DigitalAnvilUpgrade;
import com.fish_dan_.data_energistics.registry.DEItems;

import appeng.api.stacks.AEItemKey;
import appeng.core.definitions.AEItems;

import net.minecraft.resources.ResourceLocation;

import org.jspecify.annotations.NullMarked;

import static com.fish_dan_.data_energistics.Data_Energistics.id;

/** Player-owned terminal capabilities, independent of upgrades installed on individual digital anvils. */
@NullMarked
public final class PlayerTerminalResearch {

    public static final ResourceLocation TERMINAL = id("player_terminal");
    public static final ResourceLocation MAGNET = id("player_terminal/magnet");

    private PlayerTerminalResearch() {}

    /** Registers defaults after item and AE key registries are available during common setup. */
    public static void register(RegisterResearchNodesEvent event) {
        event.register(basicNode(TERMINAL, "research.data_energistics.player_terminal")
                .icon(ResourceLocation.withDefaultNamespace("crafting_table")).build());
        event.register(basicNode(MAGNET, "research.data_energistics.player_terminal.magnet")
                .icon(id("me_vacuum"))
                .requiredPrerequisite(TERMINAL).build());
        event.register(anvilNode(DigitalAnvilUpgrade.FIXED_PENALTY, 200, 2000, 16)
                .input(new ResearchInput(AEItemKey.of(DEItems.DATA_PROCESSOR.get()), 8))
                .input(new ResearchInput(AEItemKey.of(DEItems.DIGISIDIAN_MEMORIZE_INGOT.get()), 4)).build());
        event.register(anvilNode(DigitalAnvilUpgrade.UNRESTRICTED_TYPES, 300, 3000, 32)
                .input(new ResearchInput(AEItemKey.of(DEItems.DATA_PROCESSOR.get()), 16))
                .input(new ResearchInput(AEItemKey.of(DEItems.RESONANCE_CRYSTAL.get()), 8)).build());
        event.register(anvilNode(DigitalAnvilUpgrade.UNRESTRICTED_LEVELS, 400, 4000, 48)
                .input(new ResearchInput(AEItemKey.of(DEItems.RESONANCE_DIGITALIZATION_CORE.get()), 1))
                .input(new ResearchInput(StellarFluxKey.of(), 8)).build());
        event.register(anvilNode(DigitalAnvilUpgrade.COPY_ENCHANTMENTS, 500, 5000, 64)
                .input(new ResearchInput(AEItemKey.of(AEItems.QUANTUM_ENTANGLED_SINGULARITY), 2))
                .input(new ResearchInput(AEItemKey.of(DEItems.DATA_PROCESSOR.get()), 16))
                .input(new ResearchInput(EchoKey.of(), 16)).build());
    }

    /** Returns the player research ID; item research IDs remain owned by the anvil stack. */
    public static ResourceLocation anvilUpgradeId(DigitalAnvilUpgrade upgrade) {
        return id("player_terminal/anvil/" + switch (upgrade) {
            case FIXED_PENALTY -> "fixed_penalty";
            case UNRESTRICTED_TYPES -> "unrestricted_types";
            case UNRESTRICTED_LEVELS -> "unrestricted_levels";
            case COPY_ENCHANTMENTS -> "copy_enchantments";
        });
    }

    private static ResearchNode.Builder basicNode(ResourceLocation researchId, String name) {
        return ResearchNode.builder(researchId, ResearchScope.PLAYER_UPGRADE, name, name + ".description", 200, 2000)
                .input(new ResearchInput(DataKey.of(), 16))
                .input(new ResearchInput(AEItemKey.of(DEItems.DATA_PROCESSOR.get()), 8))
                .input(new ResearchInput(AEItemKey.of(DEItems.DIGISIDIAN_MEMORIZE_INGOT.get()), 4));
    }

    private static ResearchNode.Builder anvilNode(DigitalAnvilUpgrade upgrade, int ticks, double energy, long data) {
        ResourceLocation researchId = anvilUpgradeId(upgrade);
        String name = "research.data_energistics." + researchId.getPath().replace('/', '.');
        return ResearchNode.builder(researchId, ResearchScope.PLAYER_UPGRADE, name, name + ".description", ticks, energy)
                .icon(id("me_digital_anvil"))
                .requiredPrerequisite(TERMINAL)
                .input(new ResearchInput(DataKey.of(), data));
    }
}
