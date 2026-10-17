package com.fish_dan_.data_energistics.blockentity.machine.anvil;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.anvil.payment.DigitalAnvilPayment;
import com.fish_dan_.data_energistics.registry.DEBlockEntities;
import com.fish_dan_.data_energistics.registry.DEDataComponents;

import appeng.api.networking.GridFlags;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.security.IActionSource;
import appeng.api.orientation.BlockOrientation;
import appeng.api.stacks.GenericStack;
import appeng.api.util.AECableType;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import appeng.util.SettingsFrom;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;

@NullMarked
public final class MeDigitalAnvilBlockEntity extends AENetworkedBlockEntity {

    private final DigitalAnvilPayment payment = new DigitalAnvilPayment();
    private boolean faulted;
    private boolean falling;

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

    /** Called on the server thread immediately before removal, transferring state without also dropping its pending refunds. */
    public CompoundTag saveForFalling(HolderLookup.Provider registries) {
        CompoundTag data = saveWithoutMetadata(registries);
        falling = true;
        return data;
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

    /** Vanilla stores item components on placement; AE2 must copy research back only when dismantling. */
    @Override
    public void exportSettings(SettingsFrom mode, DataComponentMap.Builder builder, @Nullable Player player) {
        super.exportSettings(mode, builder, player);
        if (mode == SettingsFrom.DISMANTLE_ITEM) {
            builder.set(DEDataComponents.RESEARCH_UPGRADES.get(), components().get(DEDataComponents.RESEARCH_UPGRADES.get()));
            builder.set(DEDataComponents.RESEARCH_EXCLUSIONS.get(), components().get(DEDataComponents.RESEARCH_EXCLUSIONS.get()));
            builder.set(DEDataComponents.RESEARCH_INSTANT.get(), components().get(DEDataComponents.RESEARCH_INSTANT.get()));
        }
    }

    @Override
    public void addAdditionalDrops(Level level, BlockPos pos, List<ItemStack> drops) {
        if (falling) return;
        super.addAdditionalDrops(level, pos, drops);
        for (var entry : payment.resourceRefunds().object2LongEntrySet()) {
            drops.add(GenericStack.wrapInItemStack(entry.getKey(), entry.getLongValue()));
        }
    }
}
