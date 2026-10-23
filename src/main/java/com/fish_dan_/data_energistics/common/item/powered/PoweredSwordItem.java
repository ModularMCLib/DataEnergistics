package com.fish_dan_.data_energistics.common.item.powered;

import com.fish_dan_.data_energistics.common.data.DEDataComponents;
import com.fish_dan_.data_energistics.common.data.DEItems;
import com.fish_dan_.data_energistics.common.effect.RadixRedundancyEffectLogic;
import com.fish_dan_.data_energistics.common.entity.projectile.OrbitingLightSaberEntity;
import com.fish_dan_.data_energistics.common.item.powered.lightsaber.LightSaberOrbitEnergy;
import com.fish_dan_.data_energistics.common.item.powered.sanctifier.SanctifierBladeRecovery;
import com.fish_dan_.data_energistics.common.item.powered.sanctifier.SanctifierCombat;
import com.fish_dan_.data_energistics.integration.ae.ae2.key.DataFlowKey;

import appeng.api.config.Actionable;
import appeng.api.ids.AEComponents;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.StorageCells;
import appeng.api.util.AEColor;
import appeng.items.tools.powered.ColorApplicatorItem;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.ItemAbility;

import java.util.List;
import java.util.Optional;

public class PoweredSwordItem extends AbstractPoweredTieredItem implements ConditionalDataFlowCellItem {

    private static final long SWORD_RADIX_REDUNDANCY_DATA_FLOW_COST = 20L;

    public PoweredSwordItem(Tier tier, Properties properties) {
        this(tier, properties, createDefaultSwordTool());
    }

    public PoweredSwordItem(Tier tier, Properties properties, Tool toolComponent) {
        super(tier, properties, toolComponent);
    }

    public static Tool createDefaultSwordTool() {
        return SwordItem.createToolProperties();
    }

    public static ItemAttributeModifiers createAttributes(Tier tier, float attackDamage, float attackSpeed) {
        return SwordItem.createAttributes(tier, attackDamage, attackSpeed);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flags) {
        super.appendHoverText(stack, context, lines, flags);
        appendConditionalCellInformationToTooltip(stack, lines);
        lines.add(Component.translatable("tooltip.data_energistics.tool.research"));
        if (stack.is(DEItems.DATA_LIGHT_SABER.get())) lines.add(Component.translatable("tooltip.data_energistics.light_saber.skill"));
        if (stack.is(DEItems.DATA_SANCTIFIER.get())) lines.add(Component.translatable("tooltip.data_energistics.sanctifier.skill"));
    }

    @Override
    public Optional<TooltipComponent> getTooltipImage(ItemStack stack) {
        return getConditionalCellTooltipImage(stack);
    }

    @Override
    public boolean canPerformAction(ItemStack stack, ItemAbility ability) {
        return ItemAbilities.DEFAULT_SWORD_ACTIONS.contains(ability);
    }

    @Override
    public boolean canAttackBlock(BlockState state, Level level, BlockPos pos, Player player) {
        return !player.isCreative();
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.NONE;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return stack.is(DEItems.DATA_SANCTIFIER.get()) ? 72000 : 0;
    }

    @Override
    public boolean useOnRelease(ItemStack stack) {
        return stack.is(DEItems.DATA_SANCTIFIER.get());
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (!hasSufficientEnergy(stack)) return false;
        if (!attacker.level().isClientSide) {
            tryApplyRadixRedundancy(stack, target, attacker);
            consumeActionEnergy(stack);
        }
        return true;
    }

    @Override
    public boolean overrideOtherStackedOnMe(ItemStack stack, ItemStack carriedStack, Slot slot,
                                            ClickAction action, Player player, SlotAccess carriedAccess) {
        if (action != ClickAction.SECONDARY || !LightSaberColorData.isColorableLightSaber(stack)) return false;
        DyeColor color = LightSaberColorData.getColorFromIngredient(carriedStack);
        if (color == null || color == LightSaberColorData.getStoredColor(stack)) return false;
        slot.set(LightSaberColorData.withColor(stack, color));
        if (player.getAbilities().instabuild) return true;
        if (carriedStack.getItem() instanceof ColorApplicatorItem applicator) {
            AEColor aeColor = applicator.getActiveColor(carriedStack);
            if (aeColor != null && aeColor != AEColor.TRANSPARENT && aeColor.dye != null) {
                ItemStack updated = carriedStack.copy();
                applicator.consumeColor(updated, aeColor, false);
                carriedAccess.set(updated);
            }
            return true;
        }
        if (carriedStack.getItem() instanceof DyeItem) {
            ItemStack updated = carriedStack.copy();
            updated.shrink(1);
            carriedAccess.set(updated);
            return true;
        }
        return false;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (stack.is(DEItems.DATA_LIGHT_SABER.get())) {
            if (level.isClientSide) return InteractionResultHolder.success(stack);
            ServerPlayer serverPlayer = (ServerPlayer) player;
            LightSaberOrbitEnergy.Charge active = LightSaberOrbitEnergy.find(stack);
            if (active == null) active = LightSaberOrbitEnergy.get(serverPlayer.server).findOwner(player.getUUID());
            double power = getAECurrentPower(stack);
            long cooldownEnd = stack.getOrDefault(DEDataComponents.LIGHT_SABER_COOLDOWN_END.get(), 0L);
            if (active == null && (power <= 0 || cooldownEnd > serverPlayer.server.overworld().getGameTime())) {
                return InteractionResultHolder.fail(stack);
            }
            OrbitingLightSaberEntity.toggle(serverPlayer, stack);
            return InteractionResultHolder.consume(stack);
        }
        if (stack.is(DEItems.DATA_SANCTIFIER.get())) {
            if (getAECurrentPower(stack) < 12) return InteractionResultHolder.fail(stack);
            boolean available = player instanceof ServerPlayer serverPlayer ? SanctifierBladeRecovery.get(serverPlayer.server).canCharge(serverPlayer, stack) : !stack.has(DEDataComponents.SANCTIFIER_BLADE.get());
            if (!available) {
                if (!level.isClientSide) player.displayClientMessage(Component.translatable("message.data_energistics.sanctifier_blade_missing"), true);
                return InteractionResultHolder.fail(stack);
            }
            if (player instanceof ServerPlayer serverPlayer && !SanctifierCombat.startCharge(serverPlayer, stack)) return InteractionResultHolder.fail(stack);
            player.startUsingItem(hand);
            return InteractionResultHolder.consume(stack);
        }
        return InteractionResultHolder.pass(stack);
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remaining) {
        if (stack.is(DEItems.DATA_SANCTIFIER.get()) && entity instanceof ServerPlayer player) {
            SanctifierCombat.chargeTick(player, stack);
        }
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int remaining) {
        if (stack.is(DEItems.DATA_SANCTIFIER.get()) && entity instanceof ServerPlayer player) SanctifierCombat.release(player, stack, getUseDuration(stack, entity) - remaining);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        super.inventoryTick(stack, level, entity, slot, selected);
        if (!level.isClientSide && stack.is(DEItems.DATA_LIGHT_SABER.get())) {
            double power = getAECurrentPower(stack);
            if (LightSaberOrbitEnergy.find(stack) != null) stack.set(AEComponents.STORED_ENERGY, power);
        }
        if (!level.isClientSide && stack.is(DEItems.DATA_SANCTIFIER.get()) && level.getServer() != null) SanctifierBladeRecovery.get(level.getServer()).synchronize(stack);
    }

    @Override
    public boolean hasDataFlowCellSupport(ItemStack stack) {
        return stack.is(DEItems.DATA_CRYSTAL_SWORD.get()) && ConditionalDataFlowCellItem.super.hasDataFlowCellSupport(stack);
    }

    public static double getPanelAttackDamage(ItemStack stack) {
        final double[] added = { 0 };
        final double[] multipliedBase = { 0 };
        final double[] multipliedTotal = { 1 };
        stack.forEachModifier(EquipmentSlot.MAINHAND, (Holder<Attribute> attribute, AttributeModifier modifier) -> {
            if (!attribute.equals(Attributes.ATTACK_DAMAGE)) return;
            if (modifier.operation() == Operation.ADD_VALUE) added[0] += modifier.amount();
            else if (modifier.operation() == Operation.ADD_MULTIPLIED_BASE) multipliedBase[0] += modifier.amount();
            else if (modifier.operation() == Operation.ADD_MULTIPLIED_TOTAL) multipliedTotal[0] *= 1 + modifier.amount();
        });
        return Math.max(0, (1 + added[0]) * (1 + multipliedBase[0]) * multipliedTotal[0]);
    }

    private void tryApplyRadixRedundancy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (!(attacker instanceof Player player) || player.getAttackStrengthScale(0.5F) < 1) return;
        if (stack.is(DEItems.DATA_CRYSTAL_SWORD.get()) && PoweredToolResearch.has(stack, PoweredToolResearch.CRYSTAL_SWORD)) {
            var inventory = StorageCells.getCellInventory(stack, null);
            if (inventory == null || inventory.extract(DataFlowKey.of(), SWORD_RADIX_REDUNDANCY_DATA_FLOW_COST,
                    Actionable.MODULATE, IActionSource.empty()) < SWORD_RADIX_REDUNDANCY_DATA_FLOW_COST)
                return;
            RadixRedundancyEffectLogic.applyOrBurst(target, 20, attacker, (float) getPanelAttackDamage(stack));
        }
        if (stack.is(DEItems.DATA_LIGHT_SABER.get()) && PoweredToolResearch.has(stack, "digitization")) {
            RadixRedundancyEffectLogic.applyOrBurst(target, 20, attacker, (float) getPanelAttackDamage(stack));
        }
    }
}
