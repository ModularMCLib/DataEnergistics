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

import hellfirepvp.astralsorcery.common.lib.FluidsAS;
import hellfirepvp.astralsorcery.common.tile.TileChalice;
import hellfirepvp.astralsorcery.common.util.RayTraceUtil;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
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
    private void dataEnergistics$discoverDigitalSupply(Level level, BlockPos origin, FluidStack search, CallbackInfo callback) {
        this.dataEnergistics$digitalSupplyInterfaces.clear();
        if (!dataEnergistics$isLiquidStarlight(search)) {
            return;
        }
        for (int x = -dataEnergistics$SCAN_RADIUS; x <= dataEnergistics$SCAN_RADIUS; x++) {
            for (int y = -dataEnergistics$SCAN_RADIUS; y <= dataEnergistics$SCAN_RADIUS; y++) {
                for (int z = -dataEnergistics$SCAN_RADIUS; z <= dataEnergistics$SCAN_RADIUS; z++) {
                    BlockPos position = origin.offset(x, y, z);
                    if (position.equals(origin) || !level.isLoaded(position) || RayTraceUtil.clip(level, TileChalice.getChaliceCenter(origin), Vec3.atCenterOf(position)).getType() == HitResult.Type.BLOCK) {
                        continue;
                    }
                    if (level.getBlockEntity(position) instanceof DigitalSupplyInterfaceBlockEntity target && dataEnergistics$canExtract(target, search.getAmount())) {
                        this.dataEnergistics$digitalSupplyInterfaces.add(position.asLong());
                    }
                }
            }
        }
    }

    @Inject(method = "consumeLiquid", at = @At("RETURN"), cancellable = true)
    private void dataEnergistics$consumeDigitalSupply(Level level, BlockPos origin, FluidStack search,
                                                      boolean simulate, CallbackInfoReturnable<Boolean> callback) {
        if (callback.getReturnValue() || !dataEnergistics$isLiquidStarlight(search)) {
            return;
        }
        AEFluidKey key = AEFluidKey.of(search);
        if (key == null) {
            return;
        }
        LongIterator positions = this.dataEnergistics$digitalSupplyInterfaces.iterator();
        while (positions.hasNext()) {
            BlockPos position = BlockPos.of(positions.nextLong());
            if (!level.isLoaded(position)) {
                continue;
            }
            if (!(level.getBlockEntity(position) instanceof DigitalSupplyInterfaceBlockEntity target)) {
                continue;
            }
            MEStorage storage = target.networkStorage();
            if (storage == null) {
                continue;
            }
            long requested = search.getAmount();
            long available = storage.extract(key, requested, Actionable.SIMULATE, IActionSource.empty());
            if (available < requested) {
                continue;
            }
            if (simulate) {
                callback.setReturnValue(true);
                return;
            }
            long extracted = storage.extract(key, requested, Actionable.MODULATE, IActionSource.empty());
            if (extracted >= requested) {
                callback.setReturnValue(true);
                return;
            }
            if (extracted > 0L) {
                long restored = storage.insert(key, extracted, Actionable.MODULATE, IActionSource.empty());
                if (restored < extracted) {
                    Data_Energistics.LOGGER.error(
                            "Liquid Starlight draw rollback lost {} units at {}",
                            extracted - restored,
                            position);
                }
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
        return storage.extract(AEFluidKey.of(FluidsAS.LIQUID_STARLIGHT.getSource().get()), amount,
                Actionable.SIMULATE, IActionSource.empty()) >= amount;
    }
}
