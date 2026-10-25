package com.fish_dan_.data_energistics.data.research;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.api.research.RegisterResearchNodesEvent;
import com.fish_dan_.data_energistics.api.research.ResearchInput;
import com.fish_dan_.data_energistics.api.research.ResearchNode;
import com.fish_dan_.data_energistics.api.research.ResearchScope;
import com.fish_dan_.data_energistics.api.research.ResearchTarget;
import com.fish_dan_.data_energistics.common.data.DEItems;
import com.fish_dan_.data_energistics.common.weapon.platform.domain.StasisDomainSavedData;
import com.fish_dan_.data_energistics.common.weapon.platform.model.GunForm;
import com.fish_dan_.data_energistics.common.weapon.platform.model.PlatformAbility;
import com.fish_dan_.data_energistics.common.weapon.platform.model.PlatformChipUpgrades;
import com.fish_dan_.data_energistics.common.weapon.platform.shield.ShieldUpgrades;
import com.fish_dan_.data_energistics.integration.ae.ae2.key.DataKey;

import appeng.api.stacks.AEItemKey;

import net.minecraft.resources.ResourceLocation;

public final class WeaponPlatformResearch {

    private WeaponPlatformResearch() {}

    public static void register(RegisterResearchNodesEvent event) {
        ResourceLocation chip = Data_Energistics.id("complexified_biochips");
        event.register(node(PlatformChipUpgrades.ROOT, chip, true).icon(Data_Energistics.id("digital_weapon_platform")).build());
        for (ResourceLocation id : new ResourceLocation[] { PlatformChipUpgrades.CAPACITY_I, PlatformChipUpgrades.COOLING_I, PlatformChipUpgrades.HUD }) {
            event.register(node(id, chip, false).requiredPrerequisite(PlatformChipUpgrades.ROOT).build());
        }
        event.register(node(PlatformChipUpgrades.CAPACITY_II, chip, true).requiredPrerequisite(PlatformChipUpgrades.CAPACITY_I).build());
        event.register(node(PlatformChipUpgrades.COOLING_II, chip, true).requiredPrerequisite(PlatformChipUpgrades.COOLING_I).build());
        event.register(node(PlatformChipUpgrades.PLATFORM, chip, true).requiredPrerequisite(PlatformChipUpgrades.CAPACITY_II).build());
        event.register(node(PlatformChipUpgrades.REBOOT, chip, true).requiredPrerequisite(PlatformChipUpgrades.ROOT).build());
        for (PlatformAbility ability : PlatformAbility.values()) event.register(node(ability.research, chip, true)
                .requiredPrerequisite(ability == PlatformAbility.MOVEMENT_PROTECTION ? PlatformAbility.FLIGHT.research : PlatformChipUpgrades.ROOT).build());
        event.register(node(StasisDomainSavedData.EXPANSION, chip, true).requiredPrerequisite(PlatformAbility.DOMAIN.research).build());
        for (var line : ShieldUpgrades.LINES) {
            for (int index = 0; index < line.size(); index++) {
                ResourceLocation id = line.get(index);
                int multiplier = index + 1;
                String name = "research.data_energistics." + id.getPath().replace('/', '.');
                var builder = ResearchNode.builder(id, ResearchScope.ITEM_UPGRADE, name, name + ".description", 400 * multiplier, 4000 * multiplier)
                        .target(ResearchTarget.item(chip)).icon(Data_Energistics.id("digital_weapon_platform"))
                        .input(new ResearchInput(DataKey.of(), 32L * multiplier))
                        .input(new ResearchInput(AEItemKey.of(DEItems.DATA_PROCESSOR.get()), 16L * multiplier))
                        .requiredPrerequisite(index == 0 ? PlatformAbility.SHIELD.research : line.get(index - 1));
                event.register(builder.build());
            }
        }
        ResourceLocation gun = Data_Energistics.id("digital_floating_gun");
        for (GunForm form : GunForm.values()) {
            if (form == GunForm.NORMAL) continue;
            var builder = node(form.researchId, gun, true);
            for (GunForm other : GunForm.values()) {
                if (other != GunForm.NORMAL && other != form) builder.mutuallyExclusive(other.researchId);
            }
            event.register(builder.build());
        }
    }

    private static ResearchNode.Builder node(ResourceLocation id, ResourceLocation target, boolean advanced) {
        String name = "research.data_energistics." + id.getPath().replace('/', '.');
        return ResearchNode.builder(id, ResearchScope.ITEM_UPGRADE, name, name + ".description", advanced ? 400 : 200, advanced ? 4000 : 2000)
                .target(ResearchTarget.item(target)).icon(Data_Energistics.id("digital_floating_gun"))
                .input(new ResearchInput(DataKey.of(), advanced ? 32 : 16))
                .input(new ResearchInput(AEItemKey.of(DEItems.DATA_PROCESSOR.get()), advanced ? 16 : 8));
    }
}
