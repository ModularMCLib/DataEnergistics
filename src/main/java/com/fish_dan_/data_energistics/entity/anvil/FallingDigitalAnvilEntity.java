package com.fish_dan_.data_energistics.entity.anvil;

import com.fish_dan_.data_energistics.blockentity.machine.anvil.MeDigitalAnvilBlockEntity;
import com.fish_dan_.data_energistics.common.data.DEBlocks;
import com.fish_dan_.data_energistics.common.data.DEEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/** Vanilla falling physics with the machine's data retained on landing and on failed placement. */
@NullMarked
public final class FallingDigitalAnvilEntity extends FallingBlockEntity {

    public FallingDigitalAnvilEntity(EntityType<? extends FallingDigitalAnvilEntity> type, Level level) {
        super(type, level);
    }

    public static FallingDigitalAnvilEntity fall(ServerLevel level, BlockPos pos, BlockState state, MeDigitalAnvilBlockEntity host) {
        FallingDigitalAnvilEntity falling = new FallingDigitalAnvilEntity(DEEntities.FALLING_DIGITAL_ANVIL.get(), level);
        // Vanilla exposes the carried block state through its NBT loading API, without a protected setter.
        CompoundTag data = new CompoundTag();
        data.put("BlockState", NbtUtils.writeBlockState(state));
        data.putBoolean("DropItem", true);
        falling.readAdditionalSaveData(data);
        falling.setHurtsEntities(2, 40);
        falling.blocksBuilding = true;
        falling.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        falling.xo = falling.getX();
        falling.yo = falling.getY();
        falling.zo = falling.getZ();
        falling.setStartPos(pos);
        falling.blockData = host.saveForFalling(level.registryAccess());
        level.setBlock(pos, state.getFluidState().createLegacyBlock(), Block.UPDATE_ALL);
        level.addFreshEntity(falling);
        return falling;
    }

    @Override
    public @Nullable ItemEntity spawnAtLocation(ItemStack stack, float offsetY) {
        if (!stack.is(DEBlocks.ME_DIGITAL_ANVIL.asItem()) || blockData == null || !(level() instanceof ServerLevel level)) {
            return super.spawnAtLocation(stack, offsetY);
        }
        // A failed landing or a fall timeout must export the same research and refunds as dismantling.
        MeDigitalAnvilBlockEntity host = new MeDigitalAnvilBlockEntity(blockPosition(), getBlockState());
        host.setLevel(level);
        host.loadWithComponents(blockData, level.registryAccess());
        var drops = Block.getDrops(getBlockState(), level, blockPosition(), host);
        host.addAdditionalDrops(level, blockPosition(), drops);
        ItemEntity first = null;
        for (ItemStack drop : drops) {
            ItemEntity spawned = super.spawnAtLocation(drop, offsetY);
            if (first == null) first = spawned;
        }
        return first;
    }
}
