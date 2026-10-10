package com.fish_dan_.data_energistics.mixin.magic.astral;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.blockentity.digitalsupply.DigitalSupplyInterfaceBlockEntity;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.storage.MEStorage;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import hellfirepvp.astralsorcery.common.lib.FluidsAS;
import hellfirepvp.astralsorcery.common.tile.TileChalice;
import hellfirepvp.astralsorcery.common.util.RayTraceUtil;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Lets an altar draw Liquid Starlight from a nearby interface without making it a real Chalice tile. */
@Mixin(value = TileChalice.LiquidDrawInstance.class, remap = false)
public abstract class AstralAltarLiquidDrawMixin {

    @Unique
    private static final int dataEnergistics$SCAN_RADIUS = 16;

    @Unique
    private final LongSet dataEnergistics$digitalSupplyInterfaces = new LongLinkedOpenHashSet();

    @Inject(method = "update", at = @At("TAIL"))
    private void dataEnergistics$discoverDigitalSupply(Level level, BlockPos pos, FluidStack search, CallbackInfo callback) {
        this.dataEnergistics$digitalSupplyInterfaces.clear();
        if (!dataEnergistics$isLiquidStarlight(search)) {
            return;
        }
        for (int x = -dataEnergistics$SCAN_RADIUS; x <= dataEnergistics$SCAN_RADIUS; x++) {
            for (int y = -dataEnergistics$SCAN_RADIUS; y <= dataEnergistics$SCAN_RADIUS; y++) {
                for (int z = -dataEnergistics$SCAN_RADIUS; z <= dataEnergistics$SCAN_RADIUS; z++) {
                    BlockPos position = pos.offset(x, y, z);
                    if (position.equals(pos) || !level.isLoaded(position) || RayTraceUtil.clip(level, TileChalice.getChaliceCenter(pos), Vec3.atCenterOf(position)).getType() == HitResult.Type.BLOCK) {
                        continue;
                    }
                    if (level.getBlockEntity(position) instanceof DigitalSupplyInterfaceBlockEntity target && dataEnergistics$canExtract(target, search.getAmount())) {
                        this.dataEnergistics$digitalSupplyInterfaces.add(position.asLong());
                    }
                }
            }
        }
    }

    @Inject(method = "consumeLiquid", at = @At("HEAD"), cancellable = true)
    private void dataEnergistics$consumeDigitalSupply(Level level, BlockPos pos, FluidStack search,
                                                      boolean simulate, CallbackInfoReturnable<Boolean> callback) {
        if (!dataEnergistics$isLiquidStarlight(search)) {
            return;
        }
        boolean completed = dataEnergistics$consumeCombined(level, pos, search, simulate);
        callback.setReturnValue(completed);
        callback.cancel();
    }

    /**
     * Executes one atomic draw across Astral's real chalices and all discovered DSI network sources.
     * DSI sources remain a LiquidDraw capability only; they are never represented as TileChalice instances,
     * so they cannot participate in Astral's two-chalice interaction recipes.
     */
    @Unique
    private boolean dataEnergistics$consumeCombined(Level level, BlockPos origin, FluidStack search, boolean simulate) {
        AEFluidKey key = AEFluidKey.of(search);
        if (key == null || search.getAmount() <= 0) {
            return false;
        }

        ObjectList<dataEnergistics$ChaliceSource> chaliceSources = new ObjectArrayList<>();
        int remaining = search.getAmount();
        for (BlockPos position : TileChalice.findNearbyChalices(level, origin, dataEnergistics$SCAN_RADIUS)) {
            if (!(level.getBlockEntity(position) instanceof TileChalice chalice)) {
                continue;
            }
            FluidStack available = chalice.getTankView().drain(search, IFluidHandler.FluidAction.SIMULATE);
            int accepted = Math.min(remaining, available.getAmount());
            if (accepted <= 0) {
                continue;
            }
            chaliceSources.add(new dataEnergistics$ChaliceSource(chalice, accepted));
            remaining -= accepted;
            if (remaining == 0) {
                break;
            }
        }

        ObjectList<dataEnergistics$InterfaceSource> interfaceSources = new ObjectArrayList<>();
        LongIterator positions = this.dataEnergistics$digitalSupplyInterfaces.iterator();
        while (positions.hasNext() && remaining > 0) {
            BlockPos position = BlockPos.of(positions.nextLong());
            if (!level.isLoaded(position) || !(level.getBlockEntity(position) instanceof DigitalSupplyInterfaceBlockEntity target)) {
                continue;
            }
            MEStorage storage = target.networkStorage();
            if (storage == null) {
                continue;
            }
            long available = storage.extract(key, remaining, Actionable.SIMULATE, IActionSource.empty());
            if (available <= 0L) {
                continue;
            }
            int accepted = Math.toIntExact(Math.min(remaining, available));
            interfaceSources.add(new dataEnergistics$InterfaceSource(target, accepted));
            remaining -= accepted;
        }

        if (remaining > 0) {
            return false;
        }
        if (simulate) {
            return true;
        }

        ObjectList<dataEnergistics$ChaliceCommit> committedChalices = new ObjectArrayList<>();
        ObjectList<dataEnergistics$InterfaceCommit> committedInterfaces = new ObjectArrayList<>();
        for (dataEnergistics$ChaliceSource source : chaliceSources) {
            FluidStack drained = source.chalice().getTankView().drain(
                    search.copyWithAmount(source.amount()), IFluidHandler.FluidAction.EXECUTE);
            if (drained.getAmount() != source.amount()) {
                if (!drained.isEmpty()) {
                    int restored = source.chalice().getTankView().fill(drained, IFluidHandler.FluidAction.EXECUTE);
                    if (restored < drained.getAmount()) {
                        Data_Energistics.LOGGER.error(
                                "Liquid Starlight chalice preflight rollback lost {} units at {}",
                                drained.getAmount() - restored,
                                source.chalice().getBlockPos());
                    }
                }
                dataEnergistics$rollback(committedChalices, committedInterfaces, key);
                return false;
            }
            committedChalices.add(new dataEnergistics$ChaliceCommit(source.chalice(), drained.copy()));
        }
        for (dataEnergistics$InterfaceSource source : interfaceSources) {
            MEStorage storage = source.target().networkStorage();
            long extracted = storage == null ? 0L : storage.extract(key, source.amount(), Actionable.MODULATE, IActionSource.empty());
            if (extracted != source.amount()) {
                if (extracted > 0L && storage != null) {
                    long restored = storage.insert(key, extracted, Actionable.MODULATE, IActionSource.empty());
                    if (restored < extracted) {
                        Data_Energistics.LOGGER.error(
                                "Liquid Starlight draw rollback lost {} units at {}",
                                extracted - restored,
                                source.target().position());
                    }
                }
                dataEnergistics$rollback(committedChalices, committedInterfaces, key);
                return false;
            }
            committedInterfaces.add(new dataEnergistics$InterfaceCommit(source.target(), extracted));
        }
        return true;
    }

    @Unique
    private static void dataEnergistics$rollback(ObjectList<dataEnergistics$ChaliceCommit> chalices,
                                                 ObjectList<dataEnergistics$InterfaceCommit> interfaces,
                                                 AEFluidKey key) {
        for (dataEnergistics$InterfaceCommit committed : interfaces) {
            MEStorage storage = committed.target().networkStorage();
            if (storage == null) {
                Data_Energistics.LOGGER.error(
                        "Liquid Starlight draw rollback lost {} units because the interface network went offline at {}",
                        committed.amount(),
                        committed.target().position());
                continue;
            }
            long restored = storage.insert(key, committed.amount(), Actionable.MODULATE, IActionSource.empty());
            if (restored < committed.amount()) {
                Data_Energistics.LOGGER.error(
                        "Liquid Starlight draw rollback lost {} units at {}",
                        committed.amount() - restored,
                        committed.target().position());
            }
        }
        for (dataEnergistics$ChaliceCommit committed : chalices) {
            int restored = committed.chalice().getTankView().fill(committed.stack(), IFluidHandler.FluidAction.EXECUTE);
            if (restored < committed.stack().getAmount()) {
                Data_Energistics.LOGGER.error(
                        "Liquid Starlight chalice rollback lost {} units at {}",
                        committed.stack().getAmount() - restored,
                        committed.chalice().getBlockPos());
            }
        }
    }

    @Unique
    private static boolean dataEnergistics$isLiquidStarlight(FluidStack stack) {
        return !stack.isEmpty() && stack.getFluid() == FluidsAS.LIQUID_STARLIGHT.getSource().get();
    }

    @Unique
    private static boolean dataEnergistics$canExtract(DigitalSupplyInterfaceBlockEntity target, int amount) {
        if (amount <= 0) {
            return false;
        }
        MEStorage storage = target.networkStorage();
        if (storage == null) {
            return false;
        }
        long available = storage.extract(AEFluidKey.of(FluidsAS.LIQUID_STARLIGHT.getSource().get()), amount,
                Actionable.SIMULATE, IActionSource.empty());
        return available > 0L;
    }

    @Unique
    private record dataEnergistics$ChaliceSource(TileChalice chalice, int amount) {}

    @Unique
    private record dataEnergistics$InterfaceSource(DigitalSupplyInterfaceBlockEntity target, int amount) {}

    @Unique
    private record dataEnergistics$ChaliceCommit(TileChalice chalice, FluidStack stack) {}

    @Unique
    private record dataEnergistics$InterfaceCommit(DigitalSupplyInterfaceBlockEntity target, long amount) {}
}
