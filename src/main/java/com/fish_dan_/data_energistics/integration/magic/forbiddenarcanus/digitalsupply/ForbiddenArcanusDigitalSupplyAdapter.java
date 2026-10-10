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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import com.stal111.forbidden_arcanus.common.block.HephaestusForgeBlock;
import com.stal111.forbidden_arcanus.common.block.entity.forge.HephaestusForgeBlockEntity;
import com.stal111.forbidden_arcanus.common.block.entity.forge.essence.EssenceType;
import com.stal111.forbidden_arcanus.core.init.ModItems;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;

import java.util.EnumSet;
import java.util.WeakHashMap;

/** Transfers Forbidden Arcanus forge essence through public two-phase digital-supply transactions. */
public final class ForbiddenArcanusDigitalSupplyAdapter implements DigitalSupplyInterfaceAdapter {

    private static final long RATE_LIMIT = 256L;
    private final ObjectList<DigitalSupplyResourceDefinition> resources = createResources();
    private final WeakHashMap<DigitalSupplyInterfaceTarget, LongList> discoveredTargets = new WeakHashMap<>();

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
        if (target.level().getGameTime() % 20L != 0L && this.discoveredTargets.containsKey(target)) return;
        for (DigitalSupplyResourceDefinition resource : resources) target.setPresence(resource.key(), false);
        boolean present = false;
        LongList nearby = nearbyForges(target);
        this.discoveredTargets.put(target, nearby);
        present = !nearby.isEmpty();
        for (DigitalSupplyResourceDefinition resource : resources) target.setPresence(resource.key(), present);
        if (present) target.refreshState();
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
        long accepted = Math.min(amount, Math.max(0L, (long) maximum - current));
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

    private static HephaestusForgeBlockEntity forgeAt(DigitalSupplyInterfaceTarget target, BlockPos position) {
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

    private LongList cachedForges(DigitalSupplyInterfaceTarget target) {
        LongList positions = this.discoveredTargets.get(target);
        if (positions == null) {
            positions = nearbyForges(target);
            this.discoveredTargets.put(target, positions);
        }
        return positions;
    }

    private static ObjectList<DigitalSupplyResourceDefinition> createResources() {
        var result = new ObjectArrayList<DigitalSupplyResourceDefinition>();
        for (ForbiddenArcanusEssenceKey key : ForbiddenArcanusEssenceKey.all()) {
            result.add(new DigitalSupplyResourceDefinition(key.getId(), key, key.getDisplayName(),
                    DigitalSupplyUnitConversion.IDENTITY, false,
                    EnumSet.of(DigitalSupplyTransferDirection.NETWORK_TO_TARGET, DigitalSupplyTransferDirection.TARGET_TO_NETWORK)));
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
