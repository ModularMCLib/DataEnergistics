package com.fish_dan_.data_energistics.integration.crafting.packaged.magic.occultism;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.Unbreakable;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;

import com.klikli_dev.occultism.common.ritual.CraftMinerSpiritRitual;
import com.klikli_dev.occultism.common.ritual.CraftRitual;
import com.klikli_dev.occultism.common.ritual.CraftWithSpiritNameRitual;
import com.klikli_dev.occultism.common.ritual.RepairRitual;
import com.klikli_dev.occultism.common.ritual.UnbreakableRitual;
import com.klikli_dev.occultism.common.ritual.UpgradeRitual;
import com.klikli_dev.occultism.crafting.recipe.RitualRecipe;
import com.klikli_dev.occultism.registry.OccultismDataComponents;
import com.klikli_dev.occultism.util.ItemNBTUtil;

import java.util.List;

/**
 * Side-effect-free admission preview; actual crafting and component creation remain in the native ritual.
 */
final class RitualItemResult {

    private RitualItemResult() {}

    static boolean supports(RitualRecipe recipe) {
        var ritual = recipe.getRitual();
        return ritual instanceof CraftRitual || ritual instanceof CraftWithSpiritNameRitual || ritual instanceof CraftMinerSpiritRitual ||
                ritual instanceof UpgradeRitual || ritual instanceof RepairRitual || ritual instanceof UnbreakableRitual;
    }

    static ItemStack preview(ServerLevel level, RitualRecipe recipe, ItemStack activation, List<ItemStack> ingredients) {
        var ritual = recipe.getRitual();
        ItemStack result = recipe.getResultItem(level.registryAccess()).copy();
        if (ritual instanceof RepairRitual || ritual instanceof UnbreakableRitual) {
            result = activation.copy();
            result.setDamageValue(0);
        }
        if (ritual instanceof UnbreakableRitual) {
            result.set(DataComponents.UNBREAKABLE, new Unbreakable(true));
            EnchantmentHelper.updateEnchantments(result, mutable -> mutable.removeIf(enchantment -> enchantment.is(Enchantments.UNBREAKING) || enchantment.is(Enchantments.MENDING)));
            result.set(DataComponents.RARITY, Rarity.EPIC);
            result.set(DataComponents.CUSTOM_NAME, Component.empty()
                    .append(ChatFormatting.OBFUSCATED + "nice   " + ChatFormatting.RESET)
                    .append(result.getHoverName())
                    .append(ChatFormatting.OBFUSCATED + "   ecin" + ChatFormatting.RESET));
        }
        if (ritual instanceof UpgradeRitual) {
            if (ingredients.isEmpty()) return ItemStack.EMPTY;
            Rarity rarity = result.getRarity();
            result.applyComponents(ingredients.getFirst().getComponents());
            result.set(DataComponents.RARITY, rarity);
            if (activation.has(OccultismDataComponents.SPIRIT_NAME))
                ItemNBTUtil.setBoundSpiritName(result, ItemNBTUtil.getBoundSpiritName(activation));
        }
        if (ritual instanceof CraftWithSpiritNameRitual || ritual instanceof CraftMinerSpiritRitual) {
            ItemNBTUtil.setBoundSpiritName(result, ItemNBTUtil.getBoundSpiritName(activation));
        }
        return result;
    }
}
