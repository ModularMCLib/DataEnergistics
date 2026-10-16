package com.fish_dan_.data_energistics.blockentity.machine.anvil;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.anvil.payment.DigitalAnvilPayment;
import com.fish_dan_.data_energistics.registry.DEBlockEntities;

import appeng.api.networking.GridFlags;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.security.IActionSource;
import appeng.api.orientation.BlockOrientation;
import appeng.api.stacks.GenericStack;
import appeng.api.util.AECableType;
import appeng.blockentity.grid.AENetworkedBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import org.jspecify.annotations.NullMarked;

import java.util.EnumSet;
import java.util.List;

@NullMarked
public final class MeDigitalAnvilBlockEntity extends AENetworkedBlockEntity {

    private final DigitalAnvilPayment payment = new DigitalAnvilPayment();
    private boolean faulted;

    public MeDigitalAnvilBlockEntity(BlockPos pos, BlockState state) {
        super(DEBlockEntities.ME_DIGITAL_ANVIL.get(), pos, state);
        getMainNode().setFlags(GridFlags.REQUIRE_CHANNEL).setIdlePowerUsage(0);
    }

    @Override
    public EnumSet<Direction> getGridConnectableSides(BlockOrientation orientation) {
        return EnumSet.of(Direction.DOWN);
    }

    @Override
    public AECableType getCableConnectionType(Direction side) {
        return AECableType.SMART;
    }

    @Override
    public void onMainNodeStateChanged(IGridNodeListener.State reason) {
        setChanged();
    }

    public DigitalAnvilPayment payment() {
        return payment;
    }

    public boolean faulted() {
        return faulted;
    }

    public void fault(RuntimeException error) {
        faulted = true;
        saveChanges();
        Data_Energistics.LOGGER.error("Stopped ME digital anvil at {} after a payment or operation failure", worldPosition, error);
    }

    public void serverTick() {
        var grid = getMainNode().getGrid();
        if (!faulted && payment.pending() && grid != null && getMainNode().isOnline()) {
            try {
                payment.refund(grid, IActionSource.ofMachine(this), this::saveChanges);
            } catch (RuntimeException error) {
                fault(error);
            }
        }
    }

    @Override
    public void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        payment.save(tag, registries);
        tag.putBoolean("anvil_faulted", faulted);
    }

    @Override
    public void loadTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadTag(tag, registries);
        payment.load(tag, registries);
        faulted = tag.getBoolean("anvil_faulted");
    }

    @Override
    public void addAdditionalDrops(Level level, BlockPos pos, List<ItemStack> drops) {
        super.addAdditionalDrops(level, pos, drops);
        for (var entry : payment.resourceRefunds().object2LongEntrySet()) {
            drops.add(GenericStack.wrapInItemStack(entry.getKey(), entry.getLongValue()));
        }
    }
}
