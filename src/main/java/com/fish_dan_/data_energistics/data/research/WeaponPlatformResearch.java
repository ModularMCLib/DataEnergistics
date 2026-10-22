package com.fish_dan_.data_energistics.data.research;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.api.research.RegisterResearchNodesEvent;
import com.fish_dan_.data_energistics.api.research.ResearchInput;
import com.fish_dan_.data_energistics.api.research.ResearchNode;
import com.fish_dan_.data_energistics.api.research.ResearchScope;
import com.fish_dan_.data_energistics.api.research.ResearchTarget;
import com.fish_dan_.data_energistics.common.data.DEItems;
import com.fish_dan_.data_energistics.common.weapon.platform.model.BiochipResearch;
import com.fish_dan_.data_energistics.common.weapon.platform.model.GunForm;
import com.fish_dan_.data_energistics.integration.ae.ae2.key.DataKey;

import appeng.api.stacks.AEItemKey;

import net.minecraft.resources.ResourceLocation;

public final class WeaponPlatformResearch {
    private WeaponPlatformResearch() {}

    public static void register(RegisterResearchNodesEvent event) {
        ResourceLocation chip = Data_Energistics.id("complexified_biochips");
        for (ResourceLocation id : new ResourceLocation[] { BiochipResearch.CAPACITY_I, BiochipResearch.COOLING_I, BiochipResearch.HUD }) {
            event.register(node(id, chip, false).build());
        }
        event.register(node(BiochipResearch.CAPACITY_II, chip, true).requiredPrerequisite(BiochipResearch.CAPACITY_I).build());
        event.register(node(BiochipResearch.COOLING_II, chip, true).requiredPrerequisite(BiochipResearch.COOLING_I).build());
        event.register(node(BiochipResearch.PLATFORM, chip, true).build());
        ResourceLocation gun = Data_Energistics.id("digital_floating_gun");
        for (GunForm form : GunForm.values()) {
            if (form == GunForm.NORMAL) continue;
            var builder = node(form.researchId, gun, true);
            for (GunForm other : GunForm.values()) {
                if (other != GunForm.NORMAL && other != form) builder.mutuallyExclusive(other.researchId);
            }
            event.register(builder.build());
        }
        event.register(node(BiochipResearch.AMMUNITION, gun, true).build());
    }

    private static ResearchNode.Builder node(ResourceLocation id, ResourceLocation target, boolean advanced) {
        String name = "research.data_energistics." + id.getPath().replace('/', '.');
        return ResearchNode.builder(id, ResearchScope.ITEM_UPGRADE, name, name + ".description", advanced ? 400 : 200, advanced ? 4000 : 2000)
                .target(ResearchTarget.item(target)).icon(Data_Energistics.id("digital_floating_gun"))
                .input(new ResearchInput(DataKey.of(), advanced ? 32 : 16))
                .input(new ResearchInput(AEItemKey.of(DEItems.DATA_PROCESSOR.get()), advanced ? 16 : 8));
    }
}
