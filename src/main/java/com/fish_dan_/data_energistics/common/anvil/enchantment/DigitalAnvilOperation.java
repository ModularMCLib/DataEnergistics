package com.fish_dan_.data_energistics.common.anvil.enchantment;

import com.fish_dan_.data_energistics.common.anvil.payment.DigitalAnvilCost;

import net.minecraft.world.item.ItemStack;

/** A server-calculated preview. Only the menu that calculated it may consume its inputs. */
public record DigitalAnvilOperation(ItemStack output, int rightConsumed, boolean preserveLeft,
                                    int levelBoost, DigitalAnvilCost cost) {

    public static final DigitalAnvilOperation EMPTY = new DigitalAnvilOperation(ItemStack.EMPTY, 0, false, 0, DigitalAnvilCost.ZERO);
}
