package com.fish_dan_.data_energistics.data.research;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.api.research.RegisterResearchNodesEvent;
import com.fish_dan_.data_energistics.api.research.ResearchInput;
import com.fish_dan_.data_energistics.api.research.ResearchNode;
import com.fish_dan_.data_energistics.api.research.ResearchScope;
import com.fish_dan_.data_energistics.api.research.ResearchTarget;
import com.fish_dan_.data_energistics.common.data.DEItems;
import com.fish_dan_.data_energistics.common.item.powered.PoweredToolResearch;
import com.fish_dan_.data_energistics.integration.ae.ae2.key.DataKey;

import appeng.api.stacks.AEItemKey;
import appeng.core.definitions.AEItems;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ItemLike;

import org.jspecify.annotations.NullMarked;

@NullMarked
public final class DigitizedToolUpgradeResearch {

    private DigitizedToolUpgradeResearch() {}

    public static void register(RegisterResearchNodesEvent event) {
        tool(event, "data_crystal_sword", 16, 8, true, false, true);
        tool(event, "data_crystal_axe", 18, 10, true, true, true);
        tool(event, "data_crystal_pickaxe", 20, 12, true, true, true);
        tool(event, "data_crystal_hoe", 18, 10, true, true, true);
        tool(event, "data_crystal_shovel", 18, 10, true, true, true);
        tool(event, "data_crystal_cutting_knife", 16, 8, false, false, false);
        tool(event, "data_light_saber", 24, 16, true, false, true);
        tool(event, "data_sanctifier", 32, 20, true, false, true);
        tool(event, "data_trident", 24, 16, false, false, false);
        ability(event, PoweredToolResearch.CRYSTAL_SWORD, 16, 8, true);
        ability(event, PoweredToolResearch.CRYSTAL_AXE, 18, 10, true);
        ability(event, PoweredToolResearch.CRYSTAL_PICKAXE, 20, 12, true);
        ability(event, PoweredToolResearch.CRYSTAL_HOE, 18, 10, true);
        ability(event, PoweredToolResearch.CRYSTAL_SHOVEL, 18, 10, true);
        ability(event, PoweredToolResearch.CRYSTAL_CUTTING_KNIFE, 16, 8, false);
        ability(event, PoweredToolResearch.TRIDENT_FIELD, 24, 16, false);
        register(event, "data_light_saber", PoweredToolResearch.EMITTER, 24, 16, DEItems.CARD_SABER_ENERGY.get());
        register(event, "data_light_saber", "digitization", 24, 16, DEItems.CARD_SABER_ENERGY.get());
        register(event, "data_sanctifier", PoweredToolResearch.EMITTER, 32, 20, DEItems.CARD_SABER_ENERGY.get());
        levels(event, "data_light_saber", PoweredToolResearch.ORBIT, 4, 16, 8, DEItems.CARD_SABER_ENERGY.get());
    }

    private static void tool(RegisterResearchNodesEvent event, String item, long data, int processors,
                             boolean damage, boolean mining, boolean speed) {
        levels(event, item, PoweredToolResearch.ENERGY, 3, data, processors, AEItems.ENERGY_CARD);
        if (speed) levels(event, item, PoweredToolResearch.SPEED, 3, data, processors, AEItems.SPEED_CARD);
        if (damage) register(event, item, PoweredToolResearch.DAMAGE, data, processors, DEItems.CARD_SABER_ENERGY.get());
        if (mining) register(event, item, PoweredToolResearch.MINING_SPEED, data, processors, DEItems.CARD_SABER_ENERGY.get());
        if (!item.equals("data_light_saber") && !item.equals("data_sanctifier") && !item.equals("data_trident")) {
            register(event, item, PoweredToolResearch.STORAGE, data, processors, DEItems.CARD_SABER_ENERGY.get());
        }
    }

    private static void levels(RegisterResearchNodesEvent event, String item, String capability, int maximum,
                               long data, int processors, ItemLike card) {
        for (int level = 1; level <= maximum; level++) {
            ResearchNode.Builder node = node(item, capability + "/" + level, data * level, processors * level, card);
            if (level > 1) node.requiredPrerequisite(PoweredToolResearch.id(item, capability + "/" + (level - 1)));
            event.register(node.build());
        }
    }

    private static void ability(RegisterResearchNodesEvent event, ResourceLocation id, long data,
                                int processors, boolean needsStorage) {
        String[] parts = id.getPath().split("/");
        ResearchNode.Builder node = node(parts[1], parts[2], data, processors, DEItems.CARD_SABER_ENERGY.get());
        if (needsStorage) node.requiredPrerequisite(PoweredToolResearch.id(parts[1], PoweredToolResearch.STORAGE));
        event.register(node.build());
    }

    private static void register(RegisterResearchNodesEvent event, String item, String capability,
                                 long data, int processors, ItemLike card) {
        event.register(node(item, capability, data, processors, card).build());
    }

    private static ResearchNode.Builder node(String item, String capability, long data, int processors, ItemLike card) {
        ResourceLocation id = PoweredToolResearch.id(item, capability);
        String name = "research.data_energistics." + id.getPath().replace('/', '.');
        int level = capability.contains("/") ? Integer.parseInt(capability.substring(capability.lastIndexOf('/') + 1)) : 1;
        return ResearchNode.builder(id, ResearchScope.ITEM_UPGRADE, name, name + ".description", 200, 2000)
                .icon(Data_Energistics.id(item))
                .target(ResearchTarget.item(Data_Energistics.id(item)))
                .input(new ResearchInput(AEItemKey.of(card), level))
                .input(new ResearchInput(AEItemKey.of(DEItems.DATA_PROCESSOR.get()), processors))
                .input(new ResearchInput(DataKey.of(), data));
    }
}
