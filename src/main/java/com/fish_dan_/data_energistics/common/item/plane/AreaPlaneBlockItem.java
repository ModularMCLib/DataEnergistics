package com.fish_dan_.data_energistics.common.item.plane;

import com.fish_dan_.data_energistics.common.block.plane.AreaPlaneBlock;
import com.fish_dan_.data_energistics.common.plane.model.AreaPlaneVariant;
import com.fish_dan_.data_energistics.common.plane.structure.AreaPlaneStructure;

import appeng.core.localization.GuiText;
import appeng.core.localization.Tooltips;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

public final class AreaPlaneBlockItem extends BlockItem {

    private final boolean annihilation;

    public AreaPlaneBlockItem(AreaPlaneBlock block, Properties properties) {
        super(block, properties);
        annihilation = block.getVariant().getOperation() == AreaPlaneVariant.Operation.ANNIHILATION;
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return annihilation || super.isEnchantable(stack);
    }

    @Override
    public int getEnchantmentValue(ItemStack stack) {
        return annihilation ? 10 : super.getEnchantmentValue(stack);
    }

    @Override
    public boolean isBookEnchantable(ItemStack stack, ItemStack book) {
        return annihilation || super.isBookEnchantable(stack, book);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, context, lines, flag);
        if (!annihilation) return;
        ItemEnchantments enchantments = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        lines.add(Tooltips.of(enchantments.isEmpty() ? GuiText.CanBeEnchanted : GuiText.IncreasedEnergyUseFromEnchants));
    }

    /** Mirrors AE2's ordinary and Silk Touch creative variants for annihilation planes. */
    public void addToMainCreativeTab(CreativeModeTab.ItemDisplayParameters parameters, CreativeModeTab.Output output) {
        output.accept(this);
        if (!annihilation) return;
        var registry = parameters.holders().lookupOrThrow(Registries.ENCHANTMENT);
        var enchantments = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        enchantments.set(registry.getOrThrow(Enchantments.SILK_TOUCH), 1);
        ItemStack silkTouch = new ItemStack(this);
        silkTouch.set(DataComponents.ENCHANTMENTS, enchantments.toImmutable());
        output.accept(silkTouch);
    }

    @Override
    protected boolean placeBlock(BlockPlaceContext context, BlockState state) {
        return AreaPlaneStructure.place(context, state, () -> super.placeBlock(context, state));
    }
}
