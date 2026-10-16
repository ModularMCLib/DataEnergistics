package com.fish_dan_.data_energistics.common.anvil.enchantment;

import com.fish_dan_.data_energistics.common.anvil.DigitalAnvilUpgrade;
import com.fish_dan_.data_energistics.common.anvil.payment.DigitalAnvilCost;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/** Vanilla repair and enchantment rules with explicitly enabled technology exceptions. */
public final class DigitalAnvilCalculation {

    public static final int MAX_LEVEL = 255;

    private DigitalAnvilCalculation() {}

    public static DigitalAnvilOperation calculate(ItemStack left, ItemStack right, String name, int upgrades, int requestedBoost) {
        if (left.isEmpty() || left.getCount() != 1) return DigitalAnvilOperation.EMPTY;
        boolean unrestrictedTypes = DigitalAnvilUpgrade.UNRESTRICTED_TYPES.enabled(upgrades);
        boolean unrestrictedLevels = DigitalAnvilUpgrade.UNRESTRICTED_LEVELS.enabled(upgrades);
        boolean fixedPenalty = DigitalAnvilUpgrade.FIXED_PENALTY.enabled(upgrades);
        boolean copying = DigitalAnvilUpgrade.COPY_ENCHANTMENTS.enabled(upgrades) && right.is(Items.BOOK);
        ItemEnchantments original = EnchantmentHelper.getEnchantmentsForCrafting(left);
        if (copying && original.isEmpty()) return DigitalAnvilOperation.EMPTY;
        if (!copying && !unrestrictedTypes && !EnchantmentHelper.canStoreEnchantments(left)) return DigitalAnvilOperation.EMPTY;

        ItemStack output = copying ? new ItemStack(Items.ENCHANTED_BOOK) : left.copy();
        ItemEnchantments.Mutable enchantments = new ItemEnchantments.Mutable(original);
        long base = 0;
        int consumed = right.isEmpty() ? 0 : 1;
        int copiedCount = copying ? original.size() : 0;
        int copiedLevels = 0;
        boolean changed = copying;
        boolean renamed = false;
        if (copying) {
            for (var entry : original.entrySet()) {
                base += (long) Math.max(1, entry.getKey().value().getAnvilCost() / 2) * entry.getIntValue();
            }
        } else if (!right.isEmpty()) {
            boolean book = right.has(DataComponents.STORED_ENCHANTMENTS);
            if (output.isDamageableItem() && output.getItem().isValidRepairItem(left, right)) {
                int repaired = Math.min(output.getDamageValue(), output.getMaxDamage() / 4);
                if (repaired <= 0) return DigitalAnvilOperation.EMPTY;
                consumed = 0;
                while (repaired > 0 && consumed < right.getCount()) {
                    output.setDamageValue(output.getDamageValue() - repaired);
                    consumed++;
                    base++;
                    repaired = Math.min(output.getDamageValue(), output.getMaxDamage() / 4);
                }
                changed = true;
            } else {
                if (!book && (!left.is(right.getItem()) || !output.isDamageableItem())) return DigitalAnvilOperation.EMPTY;
                if (output.isDamageableItem() && !book) {
                    int remaining = left.getMaxDamage() - left.getDamageValue();
                    int sacrifice = right.getMaxDamage() - right.getDamageValue() + output.getMaxDamage() * 12 / 100;
                    int damage = Math.max(0, output.getMaxDamage() - remaining - sacrifice);
                    if (damage < output.getDamageValue()) {
                        output.setDamageValue(damage);
                        base += 2;
                        changed = true;
                    }
                }
                boolean accepted = false;
                boolean rejected = false;
                for (var entry : EnchantmentHelper.getEnchantmentsForCrafting(right).entrySet()) {
                    Holder<Enchantment> enchantment = entry.getKey();
                    int current = enchantments.getLevel(enchantment);
                    int incoming = entry.getIntValue();
                    int target = current == incoming ? incoming + 1 : Math.max(current, incoming);
                    boolean compatible = unrestrictedTypes || left.supportsEnchantment(enchantment);
                    if (!unrestrictedTypes) {
                        for (Holder<Enchantment> existing : enchantments.keySet()) {
                            if (!existing.equals(enchantment) && !Enchantment.areCompatible(enchantment, existing)) {
                                compatible = false;
                                base++;
                            }
                        }
                    }
                    if (!compatible) {
                        rejected = true;
                        continue;
                    }
                    target = Math.min(target, unrestrictedLevels ? MAX_LEVEL : enchantment.value().getMaxLevel());
                    enchantments.set(enchantment, target);
                    int weight = enchantment.value().getAnvilCost();
                    if (book) weight = Math.max(1, weight / 2);
                    base += (long) weight * target;
                    accepted = true;
                    changed = true;
                }
                if (rejected && !accepted) return DigitalAnvilOperation.EMPTY;
                if (book && !unrestrictedTypes && !output.isBookEnchantable(right)) return DigitalAnvilOperation.EMPTY;
            }
        }

        int maxLevel = 0;
        for (Holder<Enchantment> enchantment : enchantments.keySet()) maxLevel = Math.max(maxLevel, enchantments.getLevel(enchantment));
        int boost = unrestrictedLevels && maxLevel > 0 ? Math.min(requestedBoost, MAX_LEVEL - maxLevel) : 0;
        if (boost > 0) {
            for (Holder<Enchantment> enchantment : enchantments.keySet()) {
                if (!changed) base += (long) Math.max(1, enchantment.value().getAnvilCost() / 2) * enchantments.getLevel(enchantment);
                enchantments.set(enchantment, enchantments.getLevel(enchantment) + boost);
            }
            changed = true;
        }
        if (!StringUtil.isBlank(name)) {
            if (!name.equals(left.getHoverName().getString())) {
                output.set(DataComponents.CUSTOM_NAME, Component.literal(name));
                renamed = true;
            }
        } else if (output.has(DataComponents.CUSTOM_NAME)) {
            output.remove(DataComponents.CUSTOM_NAME);
            renamed = true;
        }
        if (!changed && !renamed) return DigitalAnvilOperation.EMPTY;
        if (renamed) base++;
        ItemEnchantments result = enchantments.toImmutable();
        EnchantmentHelper.setEnchantments(output, result);
        if (copying) for (var entry : result.entrySet()) copiedLevels = Math.addExact(copiedLevels, entry.getIntValue());
        int previous = Math.max(left.getOrDefault(DataComponents.REPAIR_COST, 0), right.getOrDefault(DataComponents.REPAIR_COST, 0));
        long increased = changed && !fixedPenalty ? (long) previous * 2 + 1 : previous;
        output.set(DataComponents.REPAIR_COST, (int) Math.min(increased, Integer.MAX_VALUE));
        double penalty = fixedPenalty ? 2.5 : (long) left.getOrDefault(DataComponents.REPAIR_COST, 0) + right.getOrDefault(DataComponents.REPAIR_COST, 0);
        try {
            return new DigitalAnvilOperation(output, consumed, copying, boost,
                    DigitalAnvilCost.calculate(base, penalty, unrestrictedTypes, boost, copiedCount, copiedLevels));
        } catch (ArithmeticException resourceOverflow) {
            // A legal item may carry a huge prior-work penalty; an unrepresentable quote cannot be offered.
            return DigitalAnvilOperation.EMPTY;
        }
    }
}
