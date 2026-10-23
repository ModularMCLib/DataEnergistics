package com.fish_dan_.data_energistics.integration.technology.draconicevolution.orbital;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.combat.erasure.lifecycle.LivingDeathSettlement;
import com.fish_dan_.data_energistics.common.combat.erasure.strike.ErasureOutcome;
import com.fish_dan_.data_energistics.common.combat.erasure.strike.ErasureStrike;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.EventHooks;

import com.brandon3055.brandonscore.worldentity.WorldEntity;
import com.brandon3055.brandonscore.worldentity.WorldEntityHandler;
import com.brandon3055.draconicevolution.entity.guardian.DraconicGuardianEntity;
import com.brandon3055.draconicevolution.entity.guardian.GuardianFightManager;
import com.brandon3055.draconicevolution.init.DEContent;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/** Optional adapter for Draconic 3.1.4.633; the mod-presence gate must run before this class is resolved. */
public final class DraconicGuardianErasureAdapter {

    private DraconicGuardianErasureAdapter() {}

    public static boolean supports(Entity target) {
        return target instanceof DraconicGuardianEntity;
    }

    /** Terminates the exact UUID-linked encounter once, preserving the guardian kill entry point's own rewards. */
    public static ErasureOutcome execute(Entity target, ErasureStrike strike, UUID execution) {
        DraconicGuardianEntity guardian = (DraconicGuardianEntity) target;
        GuardianFightManager encounter = null;
        boolean committed = false;
        AABB rewardArea = null;
        ObjectOpenHashSet<UUID> previousRewards = new ObjectOpenHashSet<>();
        try {
            encounter = findEncounter(guardian);
            if (encounter != null) {
                if (encounter.isRemoved()) {
                    guardian.setRemoved(RemovalReason.DISCARDED);
                    return ErasureOutcome.GUARDIAN_ENCOUNTER_COMPLETED;
                }
                if (!strike.claimEncounter(encounter.getUniqueID())) {
                    return ErasureOutcome.ALREADY_HANDLED;
                }
                guardian.setFightManager(encounter);
                rewardArea = new AABB(encounter.getArenaOrigin().above(20)).inflate(1);
                for (ItemEntity reward : guardian.level().getEntitiesOfClass(ItemEntity.class, rewardArea, DraconicGuardianErasureAdapter::isGuardianHeart)) {
                    previousRewards.add(reward.getUUID());
                }
            }
            committed = true;
            // Encounter completion runs inside the same captured drop routine as ordinary loot.
            LivingDeathSettlement.complete(guardian, strike, execution);
            if (!guardian.isRemoved() || (encounter != null && !encounter.isRemoved())) {
                throw new IllegalStateException("Guardian kill returned without completing the encounter");
            }
            return ErasureOutcome.GUARDIAN_ENCOUNTER_COMPLETED;
        } catch (RuntimeException | LinkageError failure) {
            if (committed && encounter != null && !encounter.isRemoved()) {
                try {
                    ((GuardianEncounterCleanup) encounter).dataEnergistics$releaseErasedEncounter();
                } catch (RuntimeException | LinkageError cleanupFailure) {
                    failure.addSuppressed(cleanupFailure);
                }
            }
            Data_Energistics.LOGGER.error("Guardian erasure {} for strike {} and guardian {} ended {}; settlement is not replayed",
                    execution, strike.strikeId(), guardian.getUUID(), committed ? "partially" : "before commitment", failure);
            return committed ? ErasureOutcome.PARTIAL_FAILURE : ErasureOutcome.FAILED_BEFORE_COMMIT;
        } finally {
            if (committed && rewardArea != null) {
                // The same continuing beam/sphere must not immediately erase the reward its own encounter produced.
                for (ItemEntity reward : guardian.level().getEntitiesOfClass(ItemEntity.class, rewardArea, DraconicGuardianErasureAdapter::isGuardianHeart)) {
                    if (!previousRewards.contains(reward.getUUID())) {
                        UUID claim = strike.begin(reward.getUUID());
                        if (claim != null) {
                            strike.complete(reward.getUUID(), claim, ErasureOutcome.EXEMPT);
                        }
                    }
                }
            }
        }
    }

    /** Called once by the terminal drop routine while its item capture is active. */
    public static void completeEncounter(LivingEntity target, DamageSource source) {
        DraconicGuardianEntity guardian = (DraconicGuardianEntity) target;
        ServerLevel level = (ServerLevel) guardian.level();
        // kill() already calls guardianUpdate and processDragonDeath; neither is repeated here.
        guardian.kill();
        if (!guardian.wasExperienceConsumed() && level.getGameRules().getBoolean(GameRules.RULE_DOMOBLOOT)) {
            guardian.skipDropExperience();
            int experience = EventHooks.getExperienceDrop(guardian, source.getEntity() instanceof Player player ? player : null, 24_000);
            ExperienceOrb.award(level, guardian.position(), experience);
        }
    }

    private static boolean isGuardianHeart(ItemEntity item) {
        return item.getItem().is(DEContent.DRAGON_HEART.get()) && item.getPersistentData().getBoolean("guardian_heart");
    }

    private static @Nullable GuardianFightManager findEncounter(DraconicGuardianEntity guardian) {
        GuardianFightManager linked = guardian.getFightManager();
        if (linked != null) {
            if (linked.getLevel() != guardian.level() || !guardian.getUUID().equals(linked.getGuardianUniqueId())) {
                throw new IllegalStateException("Guardian points to another encounter's owner or dimension");
            }
            return linked;
        }
        GuardianFightManager found = null;
        for (WorldEntity worldEntity : WorldEntityHandler.getWorldEntities()) {
            if (worldEntity instanceof GuardianFightManager candidate && !candidate.isRemoved() && candidate.getLevel() == guardian.level() && guardian.getUUID().equals(candidate.getGuardianUniqueId())) {
                if (found != null) {
                    throw new IllegalStateException("Multiple encounters claim the same guardian UUID");
                }
                found = candidate;
            }
        }
        return found;
    }
}
