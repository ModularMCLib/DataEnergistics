package com.fish_dan_.data_energistics.integration.magic.neovitae.digitalsupply;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.ae2.key.BloodKey;
import com.fish_dan_.data_energistics.api.registry.digitalsupply.DigitalSupplyInterfaceAdapter;
import com.fish_dan_.data_energistics.api.registry.digitalsupply.DigitalSupplyInterfaceTarget;
import com.fish_dan_.data_energistics.api.registry.digitalsupply.DigitalSupplyResourceDefinition;
import com.fish_dan_.data_energistics.api.registry.digitalsupply.DigitalSupplyTransferContext;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.MEStorage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import com.breakinblocks.neovitae.api.altar.IAraVitae;
import com.breakinblocks.neovitae.api.capability.NVCapabilities;
import com.breakinblocks.neovitae.common.fluid.NVFluids;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.ObjectList;

import java.util.WeakHashMap;

/** Supplies the NeoVitae Ara Vitae blood tank from the shared Data Energistics blood key. */
public final class NeoVitaeDigitalSupplyAdapter implements DigitalSupplyInterfaceAdapter {

    private static final ResourceLocation ADAPTER_ID = Data_Energistics.id("neovitae_digital_supply");
    private static final long RATE_LIMIT = 256L;
    private static final ObjectList<DigitalSupplyResourceDefinition> RESOURCES = ObjectList.of();
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
        return 80;
    }

    @Override
    public boolean supports(DigitalSupplyInterfaceTarget target) {
        return !target.level().isClientSide();
    }

    @Override
    public void discover(DigitalSupplyInterfaceTarget target) {
        if (target.level().getGameTime() % 20L != 0L && this.discoveredTargets.containsKey(target)) return;
        boolean present = false;
        LongList nearby = nearbyAltars(target);
        this.discoveredTargets.put(target, nearby);
        present = !nearby.isEmpty();
        target.setPresence(BloodKey.INSTANCE, present);
        target.refreshState();
    }

    @Override
    public void tick(DigitalSupplyInterfaceTarget target, DigitalSupplyTransferContext transfer) {
        LongList automatic = cachedAltars(target);
        for (int index = 0; index < automatic.size(); index++) {
            BlockPos position = BlockPos.of(automatic.getLong(index));
            IAraVitae altar = araVitaeAt(target, position);
            if (altar == null || altar.getFluidHandler() == null) continue;
            IFluidHandler fluidHandler = altar.getFluidHandler();
            transfer.networkToTarget(BloodKey.INSTANCE, RATE_LIMIT,
                    (amount, simulate) -> fill(fluidHandler, amount, simulate));
        }
    }

    @Override
    public int acceptFluid(DigitalSupplyInterfaceTarget target, FluidStack stack, boolean simulate) {
        if (stack.isEmpty() || !stack.is(NVFluids.ESSENTIA_VITAE_SOURCE.get())) {
            return 0;
        }
        MEStorage storage = target.networkStorage();
        if (storage == null) {
            return 0;
        }
        long accepted = storage.insert(BloodKey.INSTANCE, stack.getAmount(),
                simulate ? Actionable.SIMULATE : Actionable.MODULATE, IActionSource.empty());
        if (accepted < 0L || accepted > stack.getAmount()) {
            throw new IllegalStateException("NeoVitae blood input returned an invalid insertion amount");
        }
        return Math.toIntExact(accepted);
    }

    private static IAraVitae araVitaeAt(DigitalSupplyInterfaceTarget target, BlockPos position) {
        for (Direction side : Direction.values()) {
            IAraVitae altar = target.level().getCapability(NVCapabilities.ARA_VITAE, position, side);
            if (altar != null) return altar;
        }
        return target.level().getCapability(NVCapabilities.ARA_VITAE, position, null);
    }

    private static LongList nearbyAltars(DigitalSupplyInterfaceTarget target) {
        LongSet positions = new LongOpenHashSet();
        BlockPos center = target.position();
        BlockPos min = center.offset(-7, -7, -7);
        BlockPos max = center.offset(7, 7, 7);
        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int y = min.getY(); y <= max.getY(); y++) {
                for (int z = min.getZ(); z <= max.getZ(); z++) {
                    BlockPos position = new BlockPos(x, y, z);
                    if (target.level().isLoaded(position) && araVitaeAt(target, position) != null) positions.add(position.asLong());
                }
            }
        }
        return new LongArrayList(positions);
    }

    private LongList cachedAltars(DigitalSupplyInterfaceTarget target) {
        LongList positions = this.discoveredTargets.get(target);
        if (positions == null) {
            positions = nearbyAltars(target);
            this.discoveredTargets.put(target, positions);
        }
        return positions;
    }

    private static long fill(IFluidHandler handler, long amount, boolean simulate) {
        int bounded = (int) Math.min(amount, RATE_LIMIT);
        if (bounded <= 0) {
            return 0;
        }
        FluidStack blood = new FluidStack(NVFluids.ESSENTIA_VITAE_SOURCE.get(), bounded);
        return handler.fill(blood, simulate ? IFluidHandler.FluidAction.SIMULATE : IFluidHandler.FluidAction.EXECUTE);
    }
}
