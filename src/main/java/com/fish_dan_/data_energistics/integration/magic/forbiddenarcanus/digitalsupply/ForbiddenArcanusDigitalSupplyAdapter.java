package com.fish_dan_.data_energistics.integration.magic.forbiddenarcanus.digitalsupply;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.ae2.key.BloodKey;
import com.fish_dan_.data_energistics.ae2.key.ExperienceKey;
import com.fish_dan_.data_energistics.api.registry.digitalsupply.DigitalSupplyInterfaceAdapter;
import com.fish_dan_.data_energistics.api.registry.digitalsupply.DigitalSupplyInterfaceTarget;
import com.fish_dan_.data_energistics.api.registry.digitalsupply.DigitalSupplyResourceDefinition;
import com.fish_dan_.data_energistics.api.registry.digitalsupply.DigitalSupplyTransferContext;
import com.fish_dan_.data_energistics.api.registry.digitalsupply.DigitalSupplyTransferDirection;
import com.fish_dan_.data_energistics.api.registry.digitalsupply.DigitalSupplyUnitConversion;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import com.stal111.forbidden_arcanus.common.block.HephaestusForgeBlock;
import com.stal111.forbidden_arcanus.common.block.entity.ArcaneCrystalObeliskBlockEntity;
import com.stal111.forbidden_arcanus.common.block.entity.forge.HephaestusForgeBlockEntity;
import com.stal111.forbidden_arcanus.common.block.entity.forge.essence.EssenceType;
import com.stal111.forbidden_arcanus.core.init.ModBlockEntities;
import com.stal111.forbidden_arcanus.core.init.ModItems;
import com.stal111.forbidden_arcanus.core.init.other.ModPOITypes;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import org.jspecify.annotations.Nullable;

import java.util.EnumSet;
import java.util.WeakHashMap;

/** Transfers forge essence and mirrors valid obelisk production through public two-phase digital-supply transactions. */
public final class ForbiddenArcanusDigitalSupplyAdapter implements DigitalSupplyInterfaceAdapter {

    private static final long RATE_LIMIT = 256L;
    private static final AEKey AUREAL_KEY = ForbiddenArcanusEssenceKey.of(EssenceType.AUREAL);
    private static final Object2LongMap<ObeliskProductionKey> CLAIMED_PRODUCTION = new Object2LongOpenHashMap<>();
    private final ObjectList<DigitalSupplyResourceDefinition> resources = createResources();
    private final WeakHashMap<DigitalSupplyInterfaceTarget, LongList> discoveredForges = new WeakHashMap<>();
    private final WeakHashMap<DigitalSupplyInterfaceTarget, LongList> discoveredObelisks = new WeakHashMap<>();

    @Override
    public ResourceLocation id() {
        return Data_Energistics.id("forbidden_arcanus_digital_supply");
    }

    @Override
    public ObjectList<DigitalSupplyResourceDefinition> resources() {
        return resources;
    }

    @Override
    public boolean supports(DigitalSupplyInterfaceTarget target) {
        return !target.level().isClientSide();
    }

    @Override
    public void discover(DigitalSupplyInterfaceTarget target) {
        if (target.level().getGameTime() % 20L != 0L && this.discoveredForges.containsKey(target) && this.discoveredObelisks.containsKey(target)) return;
        for (DigitalSupplyResourceDefinition resource : resources) target.setPresence(resource.key(), false);
        LongList forges = nearbyForges(target);
        LongList obelisks = nearbyObelisks(target);
        this.discoveredForges.put(target, forges);
        this.discoveredObelisks.put(target, obelisks);
        boolean forgePresent = !forges.isEmpty();
        boolean aurealPresent = forgePresent || !obelisks.isEmpty();
        for (DigitalSupplyResourceDefinition resource : resources) {
            boolean present = resource.key().equals(AUREAL_KEY) ? aurealPresent : forgePresent;
            target.setPresence(resource.key(), present);
        }
        if (forgePresent || !obelisks.isEmpty()) target.refreshState();
    }

    @Override
    public void tick(DigitalSupplyInterfaceTarget target, DigitalSupplyTransferContext transfer) {
        LongList positions = cachedForges(target);
        for (int index = 0; index < positions.size(); index++) {
            long packedPosition = positions.getLong(index);
            BlockPos position = BlockPos.of(packedPosition);
            HephaestusForgeBlockEntity forge = forgeAt(target, position);
            if (forge == null) continue;
            for (EssenceType type : EssenceType.values()) {
                AEKey key = keyFor(type);
                transfer.networkToTarget(key, RATE_LIMIT, (amount, simulate) -> change(forge, type, amount, simulate));
            }
        }
        transferObeliskProduction(target, transfer, this.discoveredObelisks.get(target));
    }

    @Override
    public int acceptItem(DigitalSupplyInterfaceTarget target, ItemStack stack, boolean simulate) {
        if (stack.isEmpty() || !isSoulItem(stack)) {
            return 0;
        }
        MEStorage storage = target.networkStorage();
        if (storage == null) {
            return 0;
        }
        long accepted = storage.insert(keyFor(EssenceType.SOULS), stack.getCount(),
                simulate ? Actionable.SIMULATE : Actionable.MODULATE, IActionSource.empty());
        if (accepted < 0L || accepted > stack.getCount()) {
            throw new IllegalStateException("Forbidden Arcanus soul input returned an invalid insertion amount");
        }
        return Math.toIntExact(accepted);
    }

    private static long change(HephaestusForgeBlockEntity forge, EssenceType type, long amount, boolean simulate) {
        if (amount <= 0 || amount > RATE_LIMIT) return 0;
        if (forge.getRitualManager().isRitualActive()) return 0;
        var manager = forge.getEssenceManager();
        int current = manager.getEssence(type);
        int maximum = maximumEssence(forge, type);
        long accepted = Math.clamp((long) maximum - current, 0L, amount);
        if (simulate || accepted == 0L) return accepted;
        manager.increaseEssence(type, Math.toIntExact(accepted));
        return Math.max(0, manager.getEssence(type) - current);
    }

    private static int maximumEssence(HephaestusForgeBlockEntity forge, EssenceType type) {
        if (forge.getBlockState().getBlock() instanceof HephaestusForgeBlock block) {
            return block.getLevel().getMaxAmount(type);
        }
        return forge.getEssenceManager().getEssence(type);
    }

    private static @Nullable HephaestusForgeBlockEntity forgeAt(DigitalSupplyInterfaceTarget target, BlockPos position) {
        BlockEntity entity = target.level().getBlockEntity(position);
        return entity instanceof HephaestusForgeBlockEntity forge ? forge : null;
    }

    private static LongList nearbyForges(DigitalSupplyInterfaceTarget target) {
        LongSet positions = new LongOpenHashSet();
        BlockPos center = target.position();
        BlockPos min = center.offset(-7, -7, -7);
        BlockPos max = center.offset(7, 7, 7);
        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int y = min.getY(); y <= max.getY(); y++) {
                for (int z = min.getZ(); z <= max.getZ(); z++) {
                    BlockPos position = new BlockPos(x, y, z);
                    if (target.level().isLoaded(position) && forgeAt(target, position) != null) positions.add(position.asLong());
                }
            }
        }
        return new LongArrayList(positions);
    }

    private static LongList nearbyObelisks(DigitalSupplyInterfaceTarget target) {
        LongSet positions = new LongOpenHashSet();
        BlockPos center = target.position();
        BlockPos min = center.offset(-7, -7, -7);
        BlockPos max = center.offset(7, 7, 7);
        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int y = min.getY(); y <= max.getY(); y++) {
                for (int z = min.getZ(); z <= max.getZ(); z++) {
                    BlockPos position = new BlockPos(x, y, z);
                    if (target.level().isLoaded(position) && target.level().getBlockEntity(position) instanceof ArcaneCrystalObeliskBlockEntity) {
                        positions.add(position.asLong());
                    }
                }
            }
        }
        return new LongArrayList(positions);
    }

    private LongList cachedForges(DigitalSupplyInterfaceTarget target) {
        LongList positions = this.discoveredForges.get(target);
        if (positions == null) {
            positions = nearbyForges(target);
            this.discoveredForges.put(target, positions);
        }
        return positions;
    }

    /** Mirrors the native production amount for every valid nearby obelisk without modifying the native obelisk. */
    private static void transferObeliskProduction(DigitalSupplyInterfaceTarget target,
                                                  DigitalSupplyTransferContext transfer, LongList positions) {
        if (positions == null || positions.isEmpty() || target.level().getGameTime() % 100L != 0L || !(target.level() instanceof ServerLevel level)) {
            return;
        }
        pruneClaims(level.getGameTime() - 200L);
        for (int index = 0; index < positions.size(); index++) {
            BlockPos position = BlockPos.of(positions.getLong(index));
            if (!hasObeliskProduction(level, position)) {
                continue;
            }
            ObeliskProductionKey production = new ObeliskProductionKey(
                    level.dimension().location(), position.asLong(), level.getGameTime());
            synchronized (CLAIMED_PRODUCTION) {
                if (CLAIMED_PRODUCTION.containsKey(production)) {
                    continue;
                }
            }
            long simulated = transfer.simulateNetworkInsert(AUREAL_KEY, 1L);
            if (simulated < 1L) {
                continue;
            }
            long inserted = transfer.commitNetworkInsert(AUREAL_KEY, 1L);
            if (inserted == 1L) {
                synchronized (CLAIMED_PRODUCTION) {
                    CLAIMED_PRODUCTION.put(production, 1L);
                }
            } else if (inserted > 0L) {
                long restored = transfer.commitNetworkExtract(AUREAL_KEY, inserted);
                if (restored != inserted) {
                    throw new IllegalStateException("Unable to roll back a partial obelisk AUREAL insertion");
                }
            }
        }
    }

    private static boolean hasObeliskProduction(ServerLevel level, BlockPos position) {
        return level.getPoiManager()
                .getInRange(holder -> holder.value() == ModPOITypes.HEPHAESTUS_FORGE.get(),
                        position, 4, PoiManager.Occupancy.ANY)
                .map(PoiRecord::getPos)
                .anyMatch(forgePosition -> level.getBlockEntity(forgePosition, ModBlockEntities.HEPHAESTUS_FORGE.get()).isPresent());
    }

    private static void pruneClaims(long oldestGameTime) {
        synchronized (CLAIMED_PRODUCTION) {
            CLAIMED_PRODUCTION.object2LongEntrySet().removeIf(obeliskProductionKeyEntry -> obeliskProductionKeyEntry.getKey().gameTime() < oldestGameTime);
        }
    }

    private record ObeliskProductionKey(ResourceLocation dimension, long position, long gameTime) {}

    private static ObjectList<DigitalSupplyResourceDefinition> createResources() {
        var result = new ObjectArrayList<DigitalSupplyResourceDefinition>();
        for (ForbiddenArcanusEssenceKey key : ForbiddenArcanusEssenceKey.all()) {
            EnumSet<DigitalSupplyTransferDirection> directions = EnumSet.of(DigitalSupplyTransferDirection.NETWORK_TO_TARGET);
            if (key.getEssenceType() == EssenceType.AUREAL) {
                directions.add(DigitalSupplyTransferDirection.TARGET_TO_NETWORK);
            }
            result.add(new DigitalSupplyResourceDefinition(key.getId(), key, key.getDisplayName(),
                    DigitalSupplyUnitConversion.IDENTITY, false, directions));
        }
        return result;
    }

    private static AEKey keyFor(EssenceType type) {
        return switch (type) {
            case BLOOD -> BloodKey.INSTANCE;
            case EXPERIENCE -> ExperienceKey.INSTANCE;
            default -> ForbiddenArcanusEssenceKey.of(type);
        };
    }

    private static boolean isSoulItem(ItemStack stack) {
        return stack.is(ModItems.SOUL.get()) || stack.is(ModItems.CORRUPT_SOUL.get());
    }
}
