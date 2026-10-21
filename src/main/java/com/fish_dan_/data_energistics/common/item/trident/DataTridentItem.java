package com.fish_dan_.data_energistics.common.item.trident;

import com.fish_dan_.data_energistics.common.data.DEDataComponents;
import com.fish_dan_.data_energistics.common.item.powered.PoweredEnergyItem;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

public final class DataTridentItem extends TridentItem implements PoweredEnergyItem {

    public static final float DAMAGE = 17.0F;
    public static final float FLIGHT_SPEED_MULTIPLIER = 1.5F;

    public DataTridentItem(Properties properties) {
        super(properties.stacksTo(1)
                .rarity(Rarity.RARE)
                .attributes(createDataTridentAttributes())
                .component(DataComponents.TOOL, TridentItem.createToolProperties()));
    }

    private static ItemAttributeModifiers createDataTridentAttributes() {
        return TridentItem.createAttributes().withModifierAdded(Attributes.ATTACK_DAMAGE,
                new AttributeModifier(BASE_ATTACK_DAMAGE_ID, DAMAGE - 1.0D, AttributeModifier.Operation.ADD_VALUE),
                EquipmentSlotGroup.MAINHAND);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flags) {
        super.appendHoverText(stack, context, lines, flags);
        appendEnergyHoverText(stack, lines);
        lines.add(Component.translatable(isAttracting(stack) ? "item.data_energistics.data_trident.attract" : "item.data_energistics.data_trident.repel"));
    }

    public static boolean isAttracting(ItemStack stack) {
        return stack.getOrDefault(DEDataComponents.TRIDENT_ATTRACTION.get(), false);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown()) {
            if (!level.isClientSide) {
                stack.set(DEDataComponents.TRIDENT_ATTRACTION.get(), !isAttracting(stack));
                player.displayClientMessage(Component.translatable(isAttracting(stack) ? "item.data_energistics.data_trident.attract" : "item.data_energistics.data_trident.repel"), true);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        return hasSufficientEnergy(stack) ? super.use(level, player, hand) : InteractionResultHolder.fail(stack);
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int remaining) {
        if (!hasSufficientEnergy(stack)) return;
        int elapsed = getUseDuration(stack, entity) - remaining;
        if (!level.isClientSide && elapsed >= THROW_THRESHOLD_TIME) consumeActionEnergy(stack);
        // No MAX_DAMAGE component: vanilla loyalty/riptide/throw handling cannot consume durability.
        super.releaseUsing(stack, level, entity, remaining);
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (!hasSufficientEnergy(stack)) return false;
        if (!attacker.level().isClientSide) consumeActionEnergy(stack);
        return true;
    }

    @Override
    public boolean mineBlock(ItemStack stack, Level level, BlockState state, BlockPos pos, LivingEntity miner) {
        if (!hasSufficientEnergy(stack)) return false;
        if (!level.isClientSide) consumeActionEnergy(stack);
        return true;
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return getEnergyBarWidth(stack);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return getEnergyBarColor(stack);
    }

    @Override
    public boolean isValidRepairItem(ItemStack stack, ItemStack repair) {
        return false;
    }

    @Override
    public void shoot(Projectile projectile, double x, double y, double z, float velocity, float inaccuracy) {
        super.shoot(projectile, x, y, z, velocity * FLIGHT_SPEED_MULTIPLIER, inaccuracy);
    }
}
