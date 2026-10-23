package com.fish_dan_.data_energistics.client.weapon.platform;

import com.fish_dan_.data_energistics.common.weapon.platform.combat.GunFormation;
import com.fish_dan_.data_energistics.common.weapon.platform.equipment.EquippedWeaponPlatform;
import com.fish_dan_.data_energistics.common.weapon.platform.storage.WeaponPlatformInventory;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import com.mojang.math.Axis;

/** The item, configuration preview and hovering equipment all use the same baked OBJ geometry. */
public final class FloatingGunWorldRenderer {

    private FloatingGunWorldRenderer() {}

    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 camera = event.getCamera().getPosition();
        var poses = event.getPoseStack();
        var buffers = minecraft.renderBuffers().bufferSource();
        for (Player player : minecraft.level.players()) {
            if (!player.isAlive() || player.isSpectator() || player.position().distanceToSqr(camera) > 4096) continue;
            var platform = EquippedWeaponPlatform.find(player);
            if (platform.isEmpty()) continue;
            var inventory = new WeaponPlatformInventory(platform);
            for (int slot = 0; slot < inventory.capacity(); slot++) {
                var gun = inventory.getStackInSlot(slot);
                if (gun.isEmpty()) continue;
                Vec3 origin = player.getPosition(partial).add(GunFormation.offset(player, slot));
                origin = origin.add(0, Math.sin((player.tickCount + partial) * 0.08 + slot) * 0.035, 0);
                poses.pushPose();
                poses.translate(origin.x - camera.x, origin.y - camera.y, origin.z - camera.z);
                poses.mulPose(Axis.YP.rotationDegrees(180 - Mth.rotLerp(partial, player.yBodyRotO, player.yBodyRot)));
                minecraft.getItemRenderer().renderStatic(gun, ItemDisplayContext.NONE, LightTexture.FULL_BRIGHT,
                        OverlayTexture.NO_OVERLAY, poses, buffers, minecraft.level, player.getId() * 8 + slot);
                poses.popPose();
            }
        }
    }
}
