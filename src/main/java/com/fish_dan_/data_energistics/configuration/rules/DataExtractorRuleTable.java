package com.fish_dan_.data_energistics.configuration.rules;

import com.fish_dan_.data_energistics.api.production.rule.DataProductionRule;
import com.fish_dan_.data_energistics.api.production.rule.DataProductionRuleSet;
import com.fish_dan_.data_energistics.api.production.rule.OutputAmountMode;
import com.fish_dan_.data_energistics.api.production.rule.OutputFamily;
import com.fish_dan_.data_energistics.configuration.rules.schema.DataExtractorRulesConfiguration;

import appeng.api.stacks.AEItemKey;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import org.jspecify.annotations.Nullable;

public final class DataExtractorRuleTable {

    private DataExtractorRuleTable() {}

    /** Compiles and returns the current rule data from the Configuration-owned schema. */
    public static LoadedRules snapshot() {
        return DataExtractorRulesConfiguration.INSTANCE.rules();
    }

    public static boolean hasRuleForSlot(Slot slot, ItemStack stack) {
        return findRule(slot, stack) != null;
    }

    @Nullable
    public static ItemRule findRule(Slot slot, ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }

        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        for (ItemRule rule : snapshot().inputRules()) {
            if (rule.slot() == slot && rule.inputItemId().equals(itemId)) {
                return rule;
            }
        }
        return null;
    }

    public static ObjectList<ItemStack> getConfiguredOutputs(DataType dataType, ResourceLocation recordedId) {
        if (!snapshot().mimeticOutputs().isEmpty()) {
            ObjectArrayList<ItemStack> configured = new ObjectArrayList<>();
            for (MimeticOutputRule row : findMimeticOutputRules(dataType, recordedId)) {
                if (row.rule().family() == OutputFamily.LOOT && row.rule().amountMode() == OutputAmountMode.FIXED && row.rule().key() instanceof AEItemKey itemKey) {
                    long amount = row.rule().amount();
                    while (amount > 0) {
                        int count = (int) Math.min(Integer.MAX_VALUE, amount);
                        configured.add(itemKey.toStack(count));
                        amount -= count;
                    }
                }
            }
            return configured;
        }
        OutputRule rule = findOutputRule(dataType, recordedId);
        return rule == null ? ObjectArrayList.of() : rule.createStacks();
    }

    public static boolean containsConfiguredId(String[] configuredIds, ResourceLocation id) {
        for (String configuredId : configuredIds) {
            ResourceLocation parsed = ResourceLocation.tryParse(configuredId);
            if (id.equals(parsed)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Finds the first configured output rule for a recorded identity.
     *
     * @param dataType   carrier data type
     * @param recordedId recorded entity or item identity
     * @return configured rule, or {@code null} when configuration has no match
     */
    public static @Nullable OutputRule findOutputRule(DataType dataType, ResourceLocation recordedId) {
        for (OutputRule rule : snapshot().outputRules()) {
            if (rule.dataType() == dataType && rule.recordedId().equals(recordedId)) {
                return rule;
            }
        }
        return null;
    }

    /** Returns all valid mimetic rows in their declared order. */
    public static ObjectList<MimeticOutputRule> findMimeticOutputRules(DataType dataType, ResourceLocation recordedId) {
        ObjectArrayList<MimeticOutputRule> matches = new ObjectArrayList<>();
        for (MimeticOutputRule rule : snapshot().mimeticOutputs()) {
            if (rule.dataType() == dataType && rule.recordedId().equals(recordedId)) {
                matches.add(rule);
            }
        }
        return matches;
    }

    public static DataProductionRuleSet mimeticRuleSet(DataType dataType, ResourceLocation recordedId) {
        ObjectArrayList<DataProductionRule> rules = new ObjectArrayList<>();
        for (MimeticOutputRule row : findMimeticOutputRules(dataType, recordedId)) {
            rules.add(row.rule());
        }
        return new DataProductionRuleSet(rules);
    }

    /** Returns all weapon/target rows whose predicates match the current attack context. */
    public static ObjectList<ExtractorOutputRule> findExtractorOutputRules(
                                                                           ItemStack weapon,
                                                                           @Nullable ResourceLocation targetEntityId) {
        ObjectArrayList<ExtractorOutputRule> matches = new ObjectArrayList<>();
        for (ExtractorOutputRule rule : snapshot().extractorOutputs()) {
            if (rule.matches(weapon, targetEntityId)) {
                matches.add(rule);
            }
        }
        return matches;
    }

    public static DataProductionRuleSet extractorRuleSet(ItemStack weapon, @Nullable ResourceLocation targetEntityId) {
        ObjectArrayList<DataProductionRule> rules = new ObjectArrayList<>();
        for (ExtractorOutputRule row : findExtractorOutputRules(weapon, targetEntityId)) {
            rules.add(row.rule());
        }
        return new DataProductionRuleSet(rules);
    }

    public enum Slot {

        ORE,
        CROP
    }

    public enum DataType {

        MOB,
        ORE,
        CROP
    }

    public record ItemRule(
                           Slot slot,
                           DataType dataType,
                           ResourceLocation inputItemId,
                           ResourceLocation recordedItemId,
                           float progressPerItem,
                           float requiredAmount) {}

    public record OutputRule(
                             DataType dataType,
                             ResourceLocation recordedId,
                             ObjectList<ConfiguredStack> outputs) {

        public OutputRule {
            outputs = ObjectLists.unmodifiable(new ObjectArrayList<>(outputs));
        }

        public ObjectArrayList<ItemStack> createStacks() {
            ObjectArrayList<ItemStack> stacks = new ObjectArrayList<>();
            for (ConfiguredStack output : outputs) {
                var item = BuiltInRegistries.ITEM.getOptional(output.itemId()).orElse(Items.AIR);
                if (item != Items.AIR) {
                    stacks.add(new ItemStack(item, output.count()));
                }
            }
            return stacks;
        }
    }

    public record ConfiguredStack(ResourceLocation itemId, int count) {}

    public record MimeticOutputRule(DataType dataType, ResourceLocation recordedId, DataProductionRule rule) {

        public MimeticOutputRule {
            if (dataType == null || recordedId == null || rule == null) {
                throw new IllegalArgumentException("Mimetic output rule fields are required");
            }
        }
    }

    public record ExtractorOutputRule(
                                      ObjectList<ResourceLocation> weaponItems,
                                      ObjectList<net.minecraft.tags.TagKey<Item>> weaponTags,
                                      ObjectList<ResourceLocation> targetEntityIds,
                                      DataProductionRule rule) {

        public ExtractorOutputRule {
            weaponItems = ObjectLists.unmodifiable(new ObjectArrayList<>(weaponItems));
            weaponTags = ObjectLists.unmodifiable(new ObjectArrayList<>(weaponTags));
            targetEntityIds = ObjectLists.unmodifiable(new ObjectArrayList<>(targetEntityIds));
            if (rule == null || (weaponItems.isEmpty() && weaponTags.isEmpty())) {
                throw new IllegalArgumentException("Extractor rules require a weapon item or tag matcher");
            }
        }

        public boolean matches(ItemStack weapon, @Nullable ResourceLocation targetEntityId) {
            if (weapon.isEmpty()) {
                return false;
            }
            boolean itemMatch = weaponItems.isEmpty() || weaponItems.contains(BuiltInRegistries.ITEM.getKey(weapon.getItem()));
            boolean tagMatch = weaponTags.isEmpty() || weaponTags.stream().allMatch(weapon::is);
            boolean targetMatch = targetEntityIds.isEmpty() || targetEntityId != null && targetEntityIds.contains(targetEntityId);
            return itemMatch && tagMatch && targetMatch;
        }
    }
}
