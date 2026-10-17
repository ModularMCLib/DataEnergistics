package com.fish_dan_.data_energistics.client.render.item.sanctifier;

import com.fish_dan_.data_energistics.item.powered.PoweredEnergyItem;
import com.fish_dan_.data_energistics.registry.DEItems;

import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.EnumMap;
import java.util.WeakHashMap;

/**
 * Client-thread hand state; values do not retain their weak entity keys or an old world.
 */
public final class SanctifierAnimationStates {

    private static final WeakHashMap<LivingEntity, EnumMap<InteractionHand, HandAnimation>> HANDS = new WeakHashMap<>();

    private SanctifierAnimationStates() {
    }

    /** Main-hand selection drives drawing independently of the item's energy. */
    static float draw(LivingEntity entity, float partialTick) {
        pose(entity, InteractionHand.MAIN_HAND, partialTick);
        return HANDS.get(entity).get(InteractionHand.MAIN_HAND).animation().draw(partialTick);
    }

    /**
     * Samples a held item's animation without advancing it; called by the item model's hand pass.
     */
    static SanctifierAnimation.Pose pose(LivingEntity entity, InteractionHand hand, float partialTick) {
        var hands = HANDS.computeIfAbsent(entity, ignored -> new EnumMap<>(InteractionHand.class));
        int slot = slot(entity, hand);
        HandAnimation tracked = hands.get(hand);
        if (tracked == null || tracked.slot() != slot) {
            tracked = new HandAnimation(slot, new SanctifierAnimation());
            hands.put(hand, tracked);
        }
        float swing = entity.swingingArm == hand ? entity.getAttackAnim(partialTick) : 0;
        float charge = entity.isUsingItem() && entity.getUsedItemHand() == hand ? Math.clamp((entity.getTicksUsingItem() + partialTick) / 10.0F, 0, 1) : 0;
        return SanctifierAnimation.sample(tracked.animation().draw(partialTick), swing, charge,
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
        HANDS.keySet().removeIf(entity -> entity.isRemoved() || entity.level() != minecraft.level);
        if (minecraft.isPaused()) return;
        for (var player : minecraft.level.players()) {
            for (InteractionHand hand : InteractionHand.values()) {
                if (isSanctifier(player.getItemInHand(hand))) pose(player, hand, 0);
            }
        }
        for (var entry : HANDS.entrySet()) {
            LivingEntity entity = entry.getKey();
            entry.getValue().entrySet().removeIf(handEntry -> {
                InteractionHand hand = handEntry.getKey();
                ItemStack stack = entity.getItemInHand(hand);
                if (!isSanctifier(stack)) return true;
                HandAnimation tracked = handEntry.getValue();
                int slot = slot(entity, hand);
                if (tracked.slot() != slot) {
                    tracked = new HandAnimation(slot, new SanctifierAnimation());
                    handEntry.setValue(tracked);
                }
                tracked.animation().tick(true);
                return false;
            });
        }
    }

    static boolean isPowered(ItemStack stack) {
        return stack.getItem() instanceof PoweredEnergyItem powered && powered.getAECurrentPower(stack) > 0;
    }

    private static boolean isSanctifier(ItemStack stack) {
        return stack.is(DEItems.DATA_SANCTIFIER.get());
    }

    private static int slot(LivingEntity entity, InteractionHand hand) {
        return hand == InteractionHand.MAIN_HAND && entity instanceof Player player ? player.getInventory().selected : -1;
    }

    private record HandAnimation(int slot, SanctifierAnimation animation) {
    }
}
