package com.fish_dan_.data_energistics.integration.magic.occultism.packaged;

import com.fish_dan_.data_energistics.api.crafting.packaged.PackagedMachineOperation;

import appeng.api.stacks.AEItemKey;

import net.minecraft.nbt.Tag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import com.klikli_dev.occultism.common.blockentity.GoldenSacrificialBowlBlockEntity;
import com.klikli_dev.occultism.common.ritual.Ritual;
import com.mojang.authlib.GameProfile;

import java.math.BigInteger;
import java.util.UUID;

/**
 * Runs actual player actions on the server thread; the native ritual listeners alone fulfill its conditions.
 */
final class RitualNativeActions {

    private static final GameProfile PROFILE = new GameProfile(UUID.fromString("b3dd8b88-1eef-4db9-b3a8-7e027d87ca69"), "[DE Ritual]");

    private RitualNativeActions() {}

    static boolean advance(PackagedMachineOperation operation, GoldenSacrificialBowlBlockEntity bowl) {
        var active = bowl.getCurrentRitualRecipe();
        if (active == null || !bowl.ritualActive) return false;
        if (!active.id().equals(operation.recipeId()))
            throw new IllegalStateException("Another Occultism ritual replaced the owned operation");
        var recipe = active.value();
        var player = FakePlayerFactory.get(operation.level(), PROFILE);
        player.setPos(operation.position().getX() + 0.5, operation.position().getY() + 1, operation.position().getZ() + 0.5);
        if (!bowl.sacrificeFulfilled()) {
            var candidates = operation.level().getEntitiesOfClass(LivingEntity.class,
                    new AABB(operation.position()).inflate(Ritual.SACRIFICE_DETECTION_RANGE),
                    entity -> entity.isAlive() && !(entity instanceof Player) &&
                            entity.blockPosition().distSqr(operation.position()) <= Ritual.SACRIFICE_DETECTION_RANGE_SQUARE &&
                            recipe.getRitual().isValidSacrifice(entity));
            if (candidates.isEmpty()) return false;
            // A real player-attributed death posts the native event; cancelled damage leaves the ritual waiting.
            var target = candidates.getFirst();
            target.hurt(operation.level().damageSources().playerAttack(player), Float.MAX_VALUE);
            return true;
        }
        if (bowl.itemUseFulfilled()) return false;
        var progress = operation.progress();
        if (!progress.contains("use_item", Tag.TAG_COMPOUND))
            throw new IllegalStateException("Ritual has no owned item for its required action");
        ItemStack planned = ItemStack.parse(operation.level().registryAccess(), progress.getCompound("use_item")).orElseThrow();
        ItemStack held;
        if (progress.getBoolean("use_taken")) {
            held = ItemStack.parseOptional(operation.level().registryAccess(), progress.getCompound("use_held"));
            if (held.isEmpty())
                throw new IllegalStateException("Required ritual item was consumed without fulfilling its action");
        } else {
            var key = AEItemKey.of(planned);
            if (operation.available(key).compareTo(BigInteger.valueOf(planned.getCount())) < 0)
                throw new IllegalStateException("Missing owned ritual action item");
            held = planned.copy();
            operation.delivered(key, held.getCount());
            progress.putBoolean("use_taken", true);
            progress.put("use_held", held.save(operation.level().registryAccess()));
            operation.changed();
        }
        if (!player.getInventory().isEmpty())
            throw new IllegalStateException("Ritual automation player inventory is not empty");
        player.setItemInHand(InteractionHand.MAIN_HAND, held);
        try {
            var event = new PlayerInteractEvent.RightClickItem(player, InteractionHand.MAIN_HAND);
            NeoForge.EVENT_BUS.post(event);
            if (!event.isCanceled())
                player.gameMode.useItem(player, operation.level(), held, InteractionHand.MAIN_HAND);
        } finally {
            player.stopUsingItem();
            ItemStack remaining = player.getMainHandItem().copy();
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            progress.put("use_held", remaining.saveOptional(operation.level().registryAccess()));
            for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                ItemStack extra = player.getInventory().removeItemNoUpdate(slot);
                if (!extra.isEmpty()) operation.returned(AEItemKey.of(extra), extra.getCount());
            }
            operation.changed();
        }
        return true;
    }

    static void returnHeld(PackagedMachineOperation operation) {
        var progress = operation.progress();
        ItemStack held = ItemStack.parseOptional(operation.level().registryAccess(), progress.getCompound("use_held"));
        if (!progress.getBoolean("use_taken") && progress.contains("use_item", Tag.TAG_COMPOUND)) {
            // A real player may have fulfilled the native event before automation used its reserved item.
            held = ItemStack.parse(operation.level().registryAccess(), progress.getCompound("use_item")).orElseThrow();
            operation.delivered(AEItemKey.of(held), held.getCount());
        }
        if (!held.isEmpty()) operation.returned(AEItemKey.of(held), held.getCount());
        progress.remove("use_held");
        progress.remove("use_taken");
        operation.changed();
    }
}
