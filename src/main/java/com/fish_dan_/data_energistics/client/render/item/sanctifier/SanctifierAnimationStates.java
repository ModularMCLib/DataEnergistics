package com.fish_dan_.data_energistics.client.render.item.sanctifier;

import com.fish_dan_.data_energistics.item.powered.PoweredEnergyItem;
import com.fish_dan_.data_energistics.registry.DEItems;

import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import org.jspecify.annotations.Nullable;

import java.util.EnumMap;
import java.util.WeakHashMap;

/**
 * Client-thread hand state; values do not retain their weak entity keys or an old world.
 */
public final class SanctifierAnimationStates {

    private static final WeakHashMap<LivingEntity, EnumMap<InteractionHand, HandAnimation>> HANDS = new WeakHashMap<>();

    private SanctifierAnimationStates() {}

    /** Main-hand selection drives drawing independently of the item's energy. */
    static float draw(LivingEntity entity, float partialTick) {
        HandAnimation tracked = animation(entity, InteractionHand.MAIN_HAND);
        return tracked == null ? 0 : tracked.animation().draw(partialTick);
    }

    /** The old stack remains visual-only until sheathing finishes; inventory selection is never delayed. */
    static ItemStack renderStack(LivingEntity entity) {
        HandAnimation tracked = animation(entity, InteractionHand.MAIN_HAND);
        return tracked == null ? ItemStack.EMPTY : tracked.stack();
    }

    static boolean isSheathing(LivingEntity entity) {
        return !isSanctifier(entity.getMainHandItem()) && !renderStack(entity).isEmpty();
    }

    static boolean usesSupportHand(LivingEntity entity, float partialTick) {
        return entity.getOffhandItem().isEmpty() && SanctifierRig.support(draw(entity, partialTick)) > 0;
    }

    /**
     * Samples a held item's animation without advancing it; called by the item model's hand pass.
     */
    static SanctifierAnimation.Pose pose(LivingEntity entity, InteractionHand hand, float partialTick) {
        HandAnimation tracked = animation(entity, hand);
        boolean held = isSanctifier(entity.getItemInHand(hand));
        float swing = held && entity.swingingArm == hand ? entity.getAttackAnim(partialTick) : 0;
        float charge = held && entity.isUsingItem() && entity.getUsedItemHand() == hand ? Math.clamp((entity.getTicksUsingItem() + partialTick) / 10.0F, 0, 1) : 0;
        return SanctifierAnimation.sample(tracked == null ? 0 : tracked.animation().draw(partialTick), swing, charge,
                (entity.tickCount % 1200) + partialTick);
    }

    /**
     * Observes selection once per tick. A paused world holds its exact pose.
     */
    public static void tick(Minecraft minecraft) {
        if (minecraft.level == null) {
            HANDS.clear();
            return;
        }
        HANDS.keySet().removeIf(entity -> !entity.isAlive() || entity.isSpectator() || entity.isRemoved() || entity.level() != minecraft.level);
        if (minecraft.isPaused()) return;
        for (var player : minecraft.level.players()) {
            for (InteractionHand hand : InteractionHand.values()) {
                if (player.isAlive() && !player.isSpectator() && isSanctifier(player.getItemInHand(hand))) animation(player, hand);
            }
        }
        for (var entry : HANDS.entrySet()) {
            LivingEntity entity = entry.getKey();
            entry.getValue().entrySet().removeIf(handEntry -> {
                InteractionHand hand = handEntry.getKey();
                ItemStack stack = entity.getItemInHand(hand);
                HandAnimation tracked = handEntry.getValue();
                boolean held = isSanctifier(stack);
                // A thrown or dropped weapon is no longer available to put back in its sheath.
                if (!held && entity == minecraft.player && minecraft.player.getInventory().items.stream().noneMatch(SanctifierAnimationStates::isSanctifier)) return true;
                if (held && !ItemStack.matches(stack, tracked.stack())) {
                    handEntry.setValue(new HandAnimation(stack.copy(), tracked.animation()));
                }
                tracked.animation().tick(held);
                return !held && tracked.animation().isSheathed();
            });
        }
        HANDS.values().removeIf(EnumMap::isEmpty);
    }

    static boolean isPowered(ItemStack stack) {
        return stack.getItem() instanceof PoweredEnergyItem powered && powered.getAECurrentPower(stack) > 0;
    }

    private static boolean isSanctifier(ItemStack stack) {
        return stack.is(DEItems.DATA_SANCTIFIER.get());
    }

    private static @Nullable HandAnimation animation(LivingEntity entity, InteractionHand hand) {
        if (!entity.isAlive() || entity.isSpectator()) return null;
        var hands = HANDS.get(entity);
        HandAnimation tracked = hands == null ? null : hands.get(hand);
        if (tracked == null && isSanctifier(entity.getItemInHand(hand))) {
            if (hands == null) {
                hands = new EnumMap<>(InteractionHand.class);
                HANDS.put(entity, hands);
            }
            tracked = new HandAnimation(entity.getItemInHand(hand).copy(), new SanctifierAnimation());
            hands.put(hand, tracked);
        }
        return tracked;
    }

    private record HandAnimation(ItemStack stack, SanctifierAnimation animation) {}
}
