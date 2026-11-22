package com.fish_dan_.data_energistics.common.item.powered.blade;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.data.DEAttachments;
import com.fish_dan_.data_energistics.common.entity.projectile.SanctifierBladeEntity;
import com.fish_dan_.data_energistics.common.item.powered.PoweredSwordItem;
import com.fish_dan_.data_energistics.common.network.packets.blade.BladeSwingPayload;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import org.jspecify.annotations.Nullable;

/** Per-player attack state, used only on the server thread. It expires after the combo window. */
@EventBusSubscriber(modid = Data_Energistics.MOD_ID)
public final class BladeAttack {

    private static final int COMBO_WINDOW_TICKS = 30;
    private @Nullable Pending pending;
    private @Nullable Entity heavyTarget;
    private @Nullable SanctifierBladeEntity execution;
    private BladeSwing lastSwing = BladeSwing.HEAVY;
    private ItemStack lastWeapon = ItemStack.EMPTY;
    private long started;

    public static void request(ServerPlayer player, boolean requestHeavy, int targetId) {
        ItemStack stack = player.getMainHandItem();
        BladeWeapon weapon = BladeWeapon.of(stack);
        if (weapon == null || player.isUsingItem() || !canAttack(player, stack)) return;

        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        BladeAttack attack = player.getData(DEAttachments.BLADE_ATTACK);
        if (attack.execution != null || (!attack.lastWeapon.isEmpty() && now < attack.started + attack.lastSwing.repeatTick())) return;

        // Vanilla uses this lookup to include separately targetable parts, such as the dragon's head.
        @SuppressWarnings("deprecation")
        Entity target = targetId < 0 ? null : level.getEntityOrPart(targetId);
        if (target == player || target instanceof ItemEntity || target instanceof ExperienceOrb) return;

        BladeSwing swing = attack.lastWeapon == stack && now - attack.started <= COMBO_WINDOW_TICKS ? attack.lastSwing.next() : BladeSwing.FOREHAND;
        if (requestHeavy && player.getAttackStrengthScale(0) >= 1 && ((PoweredSwordItem) stack.getItem()).hasSufficientEnergy(stack)) {
            swing = BladeSwing.HEAVY;
        }

        attack.lastWeapon = stack;
        attack.lastSwing = swing;
        attack.started = now;
        attack.pending = new Pending(level, target, now + swing.impactTick());
        player.resetLastActionTime();
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player,
                new BladeSwingPayload(player.getId(), player.getUUID(), weapon, swing));
    }

    /** The secondary's execution takes over the primary arm, even if the caster changes slots. */
    public static void beginExecution(ServerPlayer player, SanctifierBladeEntity blade) {
        BladeAttack attack = player.getData(DEAttachments.BLADE_ATTACK);
        attack.pending = null;
        attack.lastWeapon = ItemStack.EMPTY;
        attack.execution = blade;
    }

    @SubscribeEvent
    public static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        BladeAttack attack = player.getExistingDataOrNull(DEAttachments.BLADE_ATTACK.get());
        if (attack != null) attack.advance(player);
    }

    public static boolean isHeavyHit(ServerPlayer player, Entity target) {
        BladeAttack attack = player.getExistingDataOrNull(DEAttachments.BLADE_ATTACK.get());
        return attack != null && attack.heavyTarget == target;
    }

    private void advance(ServerPlayer player) {
        if (execution != null && execution.level() == player.level() && execution.isAlive() && execution.isExecutionActive()) return;
        long now = player.level().getGameTime();
        if (lastWeapon.isEmpty() || player.getMainHandItem() != lastWeapon || now - started > COMBO_WINDOW_TICKS) {
            player.removeData(DEAttachments.BLADE_ATTACK);
            return;
        }
        Pending strike = pending;
        if (strike == null) return;
        if (player.level() != strike.level() || player.isUsingItem()) {
            player.removeData(DEAttachments.BLADE_ATTACK);
            return;
        }
        if (now < strike.impact()) return;
        pending = null;
        if (!canAttack(player, lastWeapon)) return;
        Entity target = strike.target();
        if (target != null && target.isAlive() && strike.level().getWorldBorder().isWithinBounds(target.blockPosition()) && player.canInteractWithEntity(target.getBoundingBox(), 1.0) && player.hasLineOfSight(target)) {
            heavyTarget = lastSwing == BladeSwing.HEAVY ? target : null;
            try {
                player.attack(target);
            } finally {
                heavyTarget = null;
            }
        } else {
            player.resetAttackStrengthTicker();
        }
    }

    private static boolean canAttack(ServerPlayer player, ItemStack stack) {
        return player.isAlive() && !player.isSpectator() && player.containerMenu == player.inventoryMenu && stack.isItemEnabled(player.level().enabledFeatures());
    }

    private record Pending(ServerLevel level, @Nullable Entity target, long impact) {}
}
