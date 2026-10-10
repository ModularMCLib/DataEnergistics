package com.fish_dan_.data_energistics.blockentity.machine.mimetic;

import com.fish_dan_.data_energistics.api.production.rule.DataProductionRuleSet;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;

import org.jspecify.annotations.Nullable;

/**
 * Resolved production inputs for one installed data carrier.
 *
 * <p>
 * Plans are server-thread values. They remain valid only while the carrier slot and the published extractor-rule
 * snapshot are unchanged; the owning data mimetic field is responsible for invalidating them at those boundaries.
 * </p>
 */
public sealed interface MimeticCarrierPlan
                                           permits MimeticCarrierPlan.Empty, MimeticCarrierPlan.Biology, MimeticCarrierPlan.Ore, MimeticCarrierPlan.Crop {

    /** A slot without complete or resolvable recorded data. */
    enum Empty implements MimeticCarrierPlan {

        INSTANCE
    }

    /**
     * Resolved biology carrier data.
     *
     * @param entityId   recorded entity identity used by the sampled-loot cache
     * @param entityType entity type used for experience/drop simulation, or {@code null} when unavailable
     * @param rules      resolved output rules from the carrier's configuration snapshot
     */
    record Biology(
                   ResourceLocation entityId,
                   @Nullable EntityType<?> entityType,
                   DataProductionRuleSet rules)
            implements MimeticCarrierPlan {}

    /**
     * Resolved ore identity, rule snapshot and default loot for one roll.
     *
     * @param recordedId  recorded ore identity used to select output rules
     * @param rules       resolved output rules, including fluid and general AE resources
     * @param defaultLoot component-sensitive recorded-item fallback
     */
    record Ore(ResourceLocation recordedId, DataProductionRuleSet rules, MimeticGeneratedOutput defaultLoot)
            implements MimeticCarrierPlan {}

    /**
     * Resolved crop fallback chain for one roll.
     *
     * @param recordedId  recorded crop identity, or {@code null} for a loot-table-only carrier
     * @param rules       resolved output rules from the carrier's configuration snapshot
     * @param builtInLoot deterministic special crop output, checked before the default loot chain
     * @param lootTableId recorded loot table, or {@code null} when absent
     * @param sourceBlock recorded source block, or {@code null} when absent
     * @param fallback    recorded crop item fallback
     */
    record Crop(
                @Nullable ResourceLocation recordedId,
                DataProductionRuleSet rules,
                MimeticGeneratedOutput builtInLoot,
                @Nullable ResourceLocation lootTableId,
                @Nullable Block sourceBlock,
                MimeticGeneratedOutput fallback)
            implements MimeticCarrierPlan {}
}
