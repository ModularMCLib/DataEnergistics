package com.fish_dan_.data_energistics.integration.magic.astral;

import com.fish_dan_.data_energistics.Data_Energistics;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.storage.MEStorage;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import hellfirepvp.astralsorcery.common.tile.TileChalice;
import hellfirepvp.astralsorcery.common.util.RayTraceUtil;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import org.jspecify.annotations.Nullable;

/**
 * Combines real chalices and AE networks for one native Astral altar liquid request.
 *
 * <p>Only packed source positions survive between calls. Simulations and commit plans use ordinary network
 * quantities; no liquid is persisted here. Instances belong to one native LiquidDrawInstance and run on the
 * logical server thread. This does not register a TileChalice or participate in liquid interaction recipes.</p>
 */
public final class AstralLiquidSupplyTransaction {

    /** Astral's native LiquidDraw range, independent of the interface's 15x15x15 ritual discovery. */
    private static final int NATIVE_RANGE = 16;

    private final LongSet chalices = new LongLinkedOpenHashSet();
    private final LongSet interfaces = new LongLinkedOpenHashSet();
    private boolean failed;

    /** Refreshes native liquid sources without loading missing chunks or reserving any resources. */
    public void discover(Level level, BlockPos origin, FluidStack search) {
        this.chalices.clear();
        this.interfaces.clear();
        if (search.isEmpty() || this.failed) {
            return;
        }
        for (BlockPos position : BlockPos.betweenClosed(
                origin.offset(-NATIVE_RANGE, -NATIVE_RANGE, -NATIVE_RANGE),
                origin.offset(NATIVE_RANGE, NATIVE_RANGE, NATIVE_RANGE))) {
            if (position.equals(origin) || !level.isLoaded(position)) {
                continue;
            }
            var entity = level.getBlockEntity(position);
            if (entity instanceof TileChalice && !level.hasNeighborSignal(position) && visible(level, origin, position)) {
                this.chalices.add(position.asLong());
            } else if (entity instanceof AstralDigitalSupplyReceiver && visible(level, origin, position)) {
                this.interfaces.add(position.asLong());
            }
        }
    }

    /** Returns whether this request has an interface source and should use the combined transaction. */
    public boolean hasInterfaces() {
        return !this.failed && !this.interfaces.isEmpty();
    }

    /**
     * Simulates every source before committing a complete request. Real chalices are used first, with each AE
     * grid counted once. A late mismatch restores every committed amount before returning false. An invalid
     * third-party result disables this draw instance and logs the origin and resource at this boundary.
     */
    public boolean consume(Level level, BlockPos origin, FluidStack search, boolean simulate) {
        if (search.isEmpty() || this.failed) {
            return false;
        }
        try {
            ObjectList<Draw> plan = plan(level, origin, search);
            return plan != null && (simulate || commit(plan, search));
        } catch (RuntimeException exception) {
            this.failed = true;
            Data_Energistics.LOGGER.error(
                    "Disabled Astral digital liquid supply at {} for {} after a transaction failure",
                    origin, AEFluidKey.of(search), exception);
            return false;
        }
    }

    private @Nullable ObjectList<Draw> plan(Level level, BlockPos origin, FluidStack search) {
        AEFluidKey key = AEFluidKey.of(search);
        if (key == null) {
            throw new IllegalArgumentException("Astral requested a liquid without an AE fluid identity");
        }
        ObjectList<Draw> plan = new ObjectArrayList<>();
        int remaining = search.getAmount();
        LongIterator positions = this.chalices.iterator();
        while (positions.hasNext() && remaining > 0) {
            BlockPos position = BlockPos.of(positions.nextLong());
            if (!level.isLoaded(position) || level.hasNeighborSignal(position)
                    || !(level.getBlockEntity(position) instanceof TileChalice chalice)
                    || !visible(level, origin, position)) {
                continue;
            }
            LiquidSource source = new ChaliceSource(chalice);
            int amount = checkedAmount(source.extract(search.copyWithAmount(remaining), true), remaining, source);
            if (amount > 0) {
                plan.add(new Draw(source, amount));
                remaining -= amount;
            }
        }
        ReferenceOpenHashSet<IGrid> grids = new ReferenceOpenHashSet<>();
        positions = this.interfaces.iterator();
        while (positions.hasNext() && remaining > 0) {
            BlockPos position = BlockPos.of(positions.nextLong());
            if (!level.isLoaded(position)
                    || !(level.getBlockEntity(position) instanceof AstralDigitalSupplyReceiver receiver)
                    || !visible(level, origin, position)) {
                continue;
            }
            IGrid grid = receiver.data_energistics$grid();
            MEStorage storage = receiver.data_energistics$networkStorage();
            if (grid == null || storage == null || !grids.add(grid)) {
                continue;
            }
            LiquidSource source = new NetworkSource(storage, key, receiver, grid, position.asLong());
            int amount = checkedAmount(source.extract(search.copyWithAmount(remaining), true), remaining, source);
            if (amount > 0) {
                plan.add(new Draw(source, amount));
                remaining -= amount;
            }
        }
        return remaining == 0 ? plan : null;
    }

    private static boolean commit(ObjectList<Draw> plan, FluidStack search) {
        ObjectList<Draw> committed = new ObjectArrayList<>();
        boolean complete = true;
        RuntimeException failure = null;
        try {
            for (Draw draw : plan) {
                int amount = checkedAmount(draw.source().extract(search.copyWithAmount(draw.amount()), false),
                        draw.amount(), draw.source());
                if (amount > 0) {
                    committed.add(new Draw(draw.source(), amount));
                }
                if (amount != draw.amount()) {
                    complete = false;
                    break;
                }
            }
        } catch (RuntimeException exception) {
            complete = false;
            failure = exception;
        }
        if (complete) {
            return true;
        }
        try {
            rollback(committed, search);
        } catch (RuntimeException exception) {
            if (failure == null) {
                failure = exception;
            } else {
                failure.addSuppressed(exception);
            }
        }
        if (failure != null) {
            throw failure;
        }
        return false;
    }

    private static void rollback(ObjectList<Draw> committed, FluidStack search) {
        RuntimeException failure = null;
        for (int index = committed.size() - 1; index >= 0; index--) {
            Draw draw = committed.get(index);
            try {
                int restored = checkedAmount(draw.source().restore(search.copyWithAmount(draw.amount())),
                        draw.amount(), draw.source());
                if (restored != draw.amount()) {
                    throw new IllegalStateException("Astral liquid rollback at " + BlockPos.of(draw.source().position())
                            + " could only restore " + restored + " of " + draw.amount());
                }
            } catch (RuntimeException exception) {
                if (failure == null) {
                    failure = exception;
                } else {
                    failure.addSuppressed(exception);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    private static boolean visible(Level level, BlockPos origin, BlockPos position) {
        // Both endpoint blocks are solid machines; only intervening collisions obstruct the native liquid draw.
        return RayTraceUtil.clip(level, TileChalice.getChaliceCenter(origin), Vec3.atCenterOf(position),
                ObjectSet.of(origin, position)).getType() != HitResult.Type.BLOCK;
    }

    private static int checkedAmount(long amount, int offered, LiquidSource source) {
        if (amount < 0L || amount > offered) {
            throw new IllegalStateException("Astral liquid source at " + BlockPos.of(source.position())
                    + " returned " + amount + " for " + offered);
        }
        return (int) amount;
    }

    /** Ephemeral reversible source used only on the server thread during one complete draw. */
    private interface LiquidSource {

        /** Extracts at most the requested fluid; simulation has no side effects. */
        long extract(FluidStack fluid, boolean simulate);

        /** Restores fluid extracted by this source when another source changes during commit. */
        long restore(FluidStack fluid);

        /** Packed native source position for failure diagnostics. */
        long position();
    }

    private record ChaliceSource(TileChalice chalice) implements LiquidSource {

        @Override
        public long extract(FluidStack fluid, boolean simulate) {
            if (this.chalice.isRemoved()) {
                return 0L;
            }
            FluidStack drained = this.chalice.getTankView().drain(fluid,
                    simulate ? IFluidHandler.FluidAction.SIMULATE : IFluidHandler.FluidAction.EXECUTE);
            if (!drained.isEmpty() && !FluidStack.isSameFluidSameComponents(fluid, drained)) {
                throw new IllegalStateException("Astral chalice returned a different fluid at " + this.chalice.getBlockPos());
            }
            return drained.getAmount();
        }

        @Override
        public long restore(FluidStack fluid) {
            return this.chalice.getTankView().fill(fluid, IFluidHandler.FluidAction.EXECUTE);
        }

        @Override
        public long position() {
            return this.chalice.getBlockPos().asLong();
        }
    }

    private record NetworkSource(MEStorage storage, AEFluidKey key, AstralDigitalSupplyReceiver receiver,
                                 IGrid grid, long position) implements LiquidSource {

        @Override
        public long extract(FluidStack fluid, boolean simulate) {
            if (this.receiver.data_energistics$grid() != this.grid) {
                return 0L;
            }
            return this.storage.extract(this.key, fluid.getAmount(),
                    simulate ? Actionable.SIMULATE : Actionable.MODULATE, IActionSource.empty());
        }

        @Override
        public long restore(FluidStack fluid) {
            return this.storage.insert(this.key, fluid.getAmount(), Actionable.MODULATE, IActionSource.empty());
        }
    }

    private record Draw(LiquidSource source, int amount) {}
}
