package com.fish_dan_.data_energistics.integration.magic.goety.digitalsupply;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.api.registry.digitalsupply.DigitalSupplyInterfaceAdapter;
import com.fish_dan_.data_energistics.api.registry.digitalsupply.DigitalSupplyInterfaceTarget;
import com.fish_dan_.data_energistics.api.registry.digitalsupply.DigitalSupplyResourceDefinition;
import com.fish_dan_.data_energistics.api.registry.digitalsupply.DigitalSupplyTransferContext;
import com.fish_dan_.data_energistics.api.registry.digitalsupply.DigitalSupplyTransferDirection;
import com.fish_dan_.data_energistics.api.registry.digitalsupply.DigitalSupplyUnitConversion;
import com.fish_dan_.data_energistics.common.world.packaged.PackagedMachineClaims;
import com.fish_dan_.data_energistics.integration.ae.ae2.key.ExperienceKey;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import com.Polarice3.Goety.api.items.magic.ITotem;
import com.Polarice3.Goety.common.blocks.entities.CursedCageBlockEntity;
import com.Polarice3.Goety.common.blocks.entities.DarkAltarBlockEntity;
import com.Polarice3.Goety.common.ritual.EnchantItemRitual;
import com.Polarice3.Goety.config.MainConfig;
import com.Polarice3.Goety.utils.SEHelper;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;

import java.util.EnumSet;
import java.util.WeakHashMap;

/**
 * Exposes Goety souls and ritual experience through public block-entity and player APIs.
 *
 * <p>
 * Experience is represented as raw experience points. Ritual level costs are converted against the
 * caster's current level and progress before a refill is requested.
 * </p>
 */
public final class GoetyDigitalSupplyAdapter implements DigitalSupplyInterfaceAdapter {

    private static final ResourceLocation ADAPTER_ID = Data_Energistics.id("goety_digital_supply");
    /** Limits one automatic refill transaction while preserving partial acceptance. */
    private static final long TICK_LIMIT = 256L;
    private static final ObjectList<DigitalSupplyResourceDefinition> RESOURCES = createResources();
    private final WeakHashMap<DigitalSupplyInterfaceTarget, LongList> discoveredTargets = new WeakHashMap<>();

    @Override
    public ResourceLocation id() {
        return ADAPTER_ID;
    }

    @Override
    public ObjectList<DigitalSupplyResourceDefinition> resources() {
        return RESOURCES;
    }

    @Override
    public int priority() {
        return 100;
    }

    @Override
    public boolean supports(DigitalSupplyInterfaceTarget target) {
        return !target.level().isClientSide();
    }

    @Override
    public void discover(DigitalSupplyInterfaceTarget target) {
        if (target.level().getGameTime() % 20L != 0L && this.discoveredTargets.containsKey(target)) {
            return;
        }
        boolean souls = false;
        boolean experience = false;
        LongList nearby = nearbyPositions(target);
        this.discoveredTargets.put(target, nearby);
        for (int index = 0; index < nearby.size(); index++) {
            BlockEntity entity = target.level().getBlockEntity(BlockPos.of(nearby.getLong(index)));
            if (entity == null) continue;
            if (entity instanceof CursedCageBlockEntity cage && soulEndpoint(cage)) {
                souls = true;
            }
            if (entity instanceof DarkAltarBlockEntity altar && experienceEndpoint(target.level(), altar)) {
                experience = true;
            }
        }
        target.setPresence(GoetySoulKey.INSTANCE, souls);
        target.setPresence(ExperienceKey.INSTANCE, experience);
        target.refreshState();
    }

    @Override
    public void tick(DigitalSupplyInterfaceTarget target, DigitalSupplyTransferContext transfer) {
        LongList positions = cachedPositions(target);
        LongSet visited = new LongOpenHashSet();
        for (int index = 0; index < positions.size(); index++) {
            long packedPosition = positions.getLong(index);
            if (!visited.add(packedPosition)) continue;
            BlockPos position = BlockPos.of(packedPosition);
            if (!target.level().isLoaded(position)) continue;
            BlockEntity entity = target.level().getBlockEntity(position);
            if (entity instanceof CursedCageBlockEntity cage) {
                transferSouls(cage, transfer);
            } else if (entity instanceof DarkAltarBlockEntity altar) {
                supplyExperience(target.level(), altar);
            }
        }
    }

    private static ObjectList<DigitalSupplyResourceDefinition> createResources() {
        ObjectArrayList<DigitalSupplyResourceDefinition> resources = new ObjectArrayList<>();
        resources.add(new DigitalSupplyResourceDefinition(
                GoetySoulKey.ID,
                GoetySoulKey.INSTANCE,
                GoetySoulKey.INSTANCE.getDisplayName(),
                DigitalSupplyUnitConversion.IDENTITY,
                true,
                EnumSet.of(DigitalSupplyTransferDirection.NETWORK_TO_TARGET)));
        return resources;
    }

    private static LongList nearbyPositions(DigitalSupplyInterfaceTarget target) {
        LongSet found = new LongOpenHashSet();
        BlockPos center = target.position();
        BlockPos min = center.offset(-7, -7, -7);
        BlockPos max = center.offset(7, 7, 7);
        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int y = min.getY(); y <= max.getY(); y++) {
                for (int z = min.getZ(); z <= max.getZ(); z++) {
                    BlockPos position = new BlockPos(x, y, z);
                    if (!target.level().isLoaded(position)) continue;
                    BlockEntity entity = target.level().getBlockEntity(position);
                    if (entity instanceof DarkAltarBlockEntity) {
                        found.add(position.asLong());
                        BlockPos cagePosition = position.below();
                        if (cagePosition.getY() >= min.getY() && target.level().isLoaded(cagePosition) && target.level().getBlockEntity(cagePosition) instanceof CursedCageBlockEntity) {
                            found.add(cagePosition.asLong());
                        }
                    }
                }
            }
        }
        return new LongArrayList(found);
    }

    private LongList cachedPositions(DigitalSupplyInterfaceTarget target) {
        LongList positions = this.discoveredTargets.get(target);
        if (positions == null) {
            positions = nearbyPositions(target);
            this.discoveredTargets.put(target, positions);
        }
        return positions;
    }

    private static boolean soulEndpoint(CursedCageBlockEntity cage) {
        return cage.getSouls() >= 0;
    }

    private static boolean experienceEndpoint(Level level, DarkAltarBlockEntity altar) {
        return altar.getCurrentRitualRecipe() != null && altar.castingPlayer != null && altar.castingPlayer.level() == level;
    }

    private static void transferSouls(CursedCageBlockEntity cage, DigitalSupplyTransferContext transfer) {
        if (!soulEndpoint(cage)) {
            return;
        }
        if (!(cage.getLevel().getBlockEntity(cage.getBlockPos().above()) instanceof DarkAltarBlockEntity altar) || !experienceEndpoint(cage.getLevel(), altar)) {
            return;
        }
        long required = altar.getCurrentRitualRecipe().getSoulCost();
        transfer.networkToTarget(GoetySoulKey.INSTANCE, TICK_LIMIT,
                (amount, simulate) -> changeSouls(cage, amount, required, simulate));
    }

    private static long changeSouls(CursedCageBlockEntity cage, long amount, long required, boolean simulate) {
        if (amount <= 0L) {
            return 0L;
        }
        long bounded = Math.min(amount, Integer.MAX_VALUE);
        Player owner = cage.getOwner();
        if (owner != null && SEHelper.getSEActive(owner)) {
            int current = Math.max(0, SEHelper.getSESouls(owner));
            int maximum = Math.min(MainConfig.MaxSouls.get(), Math.toIntExact(Math.min(Integer.MAX_VALUE, Math.max(required, current))));
            long accepted = Math.clamp((long) maximum - current, 0L, bounded);
            if (simulate || accepted == 0L) {
                return accepted;
            }
            if (!SEHelper.increaseSESouls(owner, (int) accepted)) {
                return 0L;
            }
            return Math.max(0L, SEHelper.getSESouls(owner) - current);
        }
        if (cage.getItem().getItem() instanceof ITotem totem) {
            int maximum = Math.max(0, totem.getMaxSouls());
            int current = Math.max(0, cage.getSouls());
            long accepted = Math.clamp(Math.min(required, maximum) - current, 0L, bounded);
            if (simulate || accepted == 0L) {
                return accepted;
            }
            ITotem.increaseSouls(cage.getItem(), (int) accepted);
            cage.setItem(cage.getItem());
            return Math.max(0L, (long) cage.getSouls() - current);
        }
        return 0L;
    }

    private static void supplyExperience(Level level, DarkAltarBlockEntity altar) {
        if (!(level instanceof ServerLevel serverLevel) || PackagedMachineClaims.get(serverLevel).owner(altar.getBlockPos()) != null || !experienceEndpoint(level, altar) || !(altar.getCurrentRitualRecipe().getRitual() instanceof EnchantItemRitual ritual) || altar.castingPlayer == null) {
            return;
        }
        GoetyExperienceSupply.ensureExperience(
                serverLevel,
                altar.getBlockPos(),
                ritual,
                altar.itemStackHandler.getStackInSlot(0),
                altar.castingPlayer,
                altar.experienceTaken);
    }
}
