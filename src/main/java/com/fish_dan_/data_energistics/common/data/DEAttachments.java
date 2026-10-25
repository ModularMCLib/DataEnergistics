package com.fish_dan_.data_energistics.common.data;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.combat.erasure.lifecycle.ErasureState;
import com.fish_dan_.data_energistics.common.effect.ChromaticGlowState;
import com.fish_dan_.data_energistics.common.effect.WeaponBurnState;
import com.fish_dan_.data_energistics.common.effect.reboot.RadixRebootHistory;
import com.fish_dan_.data_energistics.common.effect.reboot.RadixRebootSnapshot;
import com.fish_dan_.data_energistics.common.entity.projectile.cannon.CannonPayloadSource;
import com.fish_dan_.data_energistics.common.item.powered.sanctifier.SanctifierAttachments;
import com.fish_dan_.data_energistics.common.item.powered.sanctifier.SanctifierCarryState;
import com.fish_dan_.data_energistics.common.weapon.platform.ability.PlatformMotionState;
import com.fish_dan_.data_energistics.common.weapon.platform.combat.GunEffectState;
import com.fish_dan_.data_energistics.common.weapon.platform.retention.RetainedInventoryState;
import com.fish_dan_.data_energistics.common.weapon.platform.shield.ShieldHealthWrites;

import net.minecraft.Util;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.level.GameType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import com.mojang.serialization.Codec;

import java.util.Optional;
import java.util.UUID;

/** Central registry for per-entity state; each value codec belongs to its owning domain model. */
public final class DEAttachments {

    private static final DeferredRegister<AttachmentType<?>> TYPES = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Data_Energistics.MODID);
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<UUID>> DOMAIN_LIFE = TYPES.register(
            "domain_life", () -> AttachmentType.builder(UUID::randomUUID).serialize(UUIDUtil.CODEC).build());
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<PlatformMotionState>> PLATFORM_MOTION = TYPES.register(
            "platform_motion", () -> AttachmentType.builder(PlatformMotionState::new).build());
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<ShieldHealthWrites>> SHIELD_HEALTH_WRITES = TYPES.register(
            "shield_health_writes", () -> AttachmentType.builder(ShieldHealthWrites::new).build());
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<RetainedInventoryState>> PLATFORM_RETAINED_INVENTORY = TYPES.register(
            "platform_retained_inventory", () -> AttachmentType.builder(() -> RetainedInventoryState.EMPTY).serialize(RetainedInventoryState.CODEC).build());

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<ErasureState>> ERASURE_LIFE = TYPES.register(
            "erasure_life", () -> AttachmentType.builder(ErasureState::new).build());
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Boolean>> RADIX_STASIS_ORIGINAL_NO_AI = TYPES.register(
            "radix_stasis_original_no_ai", () -> AttachmentType.builder(() -> false).serialize(Codec.BOOL).build());
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<RadixRebootHistory>> RADIX_REBOOT_HISTORY = TYPES.register(
            "radix_reboot_history", () -> AttachmentType.builder(RadixRebootHistory::new).serialize(RadixRebootHistory.CODEC).build());
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Optional<RadixRebootSnapshot>>> RADIX_REBOOT_ANCHOR = TYPES.register(
            "radix_reboot_anchor", () -> AttachmentType.builder(Optional::<RadixRebootSnapshot>empty)
                    .serialize(RadixRebootSnapshot.CODEC.optionalFieldOf("snapshot").codec()).build());
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<SanctifierAttachments.Charge>> SANCTIFIER_CHARGE = TYPES.register(
            "sanctifier_charge", () -> AttachmentType.builder(() -> new SanctifierAttachments.Charge(Util.NIL_UUID, 0))
                    .serialize(SanctifierAttachments.Charge.CODEC).build());
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<SanctifierAttachments.Rewards>> SANCTIFIER_REWARDS = TYPES.register(
            "sanctifier_rewards", () -> AttachmentType.builder(() -> new SanctifierAttachments.Rewards(0, 0))
                    .serialize(SanctifierAttachments.Rewards.CODEC).build());
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<SanctifierAttachments.Respawn>> SANCTIFIER_RESPAWN = TYPES.register(
            "sanctifier_respawn", () -> AttachmentType.builder(() -> new SanctifierAttachments.Respawn(0, GameType.SURVIVAL, false))
                    .serialize(SanctifierAttachments.Respawn.CODEC).copyOnDeath().build());
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<SanctifierCarryState>> SANCTIFIER_CARRY = TYPES.register(
            "sanctifier_carry", () -> AttachmentType.builder(() -> SanctifierCarryState.EMPTY).build());
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<WeaponBurnState>> WEAPON_BURN = TYPES.register(
            "weapon_burn", () -> AttachmentType.builder(() -> WeaponBurnState.EMPTY).serialize(WeaponBurnState.CODEC).build());
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<ChromaticGlowState>> CHROMATIC_GLOW = TYPES.register(
            "chromatic_glow", () -> AttachmentType.builder(() -> ChromaticGlowState.EMPTY).serialize(ChromaticGlowState.CODEC).build());
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<GunEffectState>> GUN_EFFECTS = TYPES.register(
            "gun_effects", () -> AttachmentType.builder(() -> GunEffectState.EMPTY).serialize(GunEffectState.CODEC).build());
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<CannonPayloadSource>> CANNON_PAYLOAD_SOURCE = TYPES.register(
            "cannon_payload_source", () -> AttachmentType.builder(() -> new CannonPayloadSource(Optional.empty()))
                    .serialize(CannonPayloadSource.CODEC).build());

    private DEAttachments() {}

    public static void register(IEventBus modBus) {
        TYPES.register(modBus);
    }
}
