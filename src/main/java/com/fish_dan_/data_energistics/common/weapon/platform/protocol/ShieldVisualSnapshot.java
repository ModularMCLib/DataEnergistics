package com.fish_dan_.data_energistics.common.weapon.platform.protocol;

import com.fish_dan_.data_energistics.common.data.DEAttachments;
import com.fish_dan_.data_energistics.common.data.DEDataComponents;
import com.fish_dan_.data_energistics.common.weapon.platform.equipment.EquippedWeaponPlatform;
import com.fish_dan_.data_energistics.common.weapon.platform.shield.ShieldAppearance;
import com.fish_dan_.data_energistics.common.weapon.platform.shield.ShieldImpact;
import com.fish_dan_.data_energistics.common.weapon.platform.shield.ShieldState;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectLists;

import java.util.UUID;

/** Tracking players receive the equipment owner's appearance, charge and recent local impacts together. */
public record ShieldVisualSnapshot(int entity, UUID player, ShieldState state, ShieldAppearance appearance, ObjectList<ShieldImpact> impacts) {

    public static final Codec<ShieldVisualSnapshot> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("entity").forGetter(ShieldVisualSnapshot::entity),
            UUIDUtil.CODEC.fieldOf("player").forGetter(ShieldVisualSnapshot::player),
            ShieldState.CODEC.fieldOf("state").forGetter(ShieldVisualSnapshot::state),
            ShieldAppearance.CODEC.fieldOf("appearance").forGetter(ShieldVisualSnapshot::appearance),
            ShieldImpact.CODEC.listOf(0, 8).<ObjectList<ShieldImpact>>xmap(ObjectArrayList::new, values -> values).fieldOf("impacts").forGetter(ShieldVisualSnapshot::impacts)).apply(instance, ShieldVisualSnapshot::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, ShieldVisualSnapshot> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC);

    public static ShieldVisualSnapshot capture(ServerPlayer player) {
        var platform = EquippedWeaponPlatform.find(player);
        var impacts = player.getData(DEAttachments.SHIELD_HEALTH_WRITES).impacts;
        long now = player.level().getGameTime();
        impacts.removeIf(impact -> now - impact.at() >= 30);
        return new ShieldVisualSnapshot(player.getId(), player.getUUID(), platform.getOrDefault(DEDataComponents.PLATFORM_SHIELD, ShieldState.EMPTY),
                platform.getOrDefault(DEDataComponents.PLATFORM_SHIELD_APPEARANCE, ShieldAppearance.DEFAULT), ObjectLists.unmodifiable(new ObjectArrayList<>(impacts)));
    }
}
