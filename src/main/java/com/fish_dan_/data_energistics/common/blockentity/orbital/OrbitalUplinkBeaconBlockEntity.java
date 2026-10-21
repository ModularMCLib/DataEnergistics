package com.fish_dan_.data_energistics.common.blockentity.orbital;

import com.fish_dan_.data_energistics.common.data.DEBlockEntities;
import com.fish_dan_.data_energistics.common.data.DEBlocks;
import com.fish_dan_.data_energistics.common.orbital.endpoint.OrbitalEndpointKind;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * AE-connected uplink endpoint that can bind only to a weapon already owned by its placing player.
 */
public final class OrbitalUplinkBeaconBlockEntity extends OrbitalEndpointBlockEntity {

    public OrbitalUplinkBeaconBlockEntity(BlockPos pos, BlockState state) {
        super(
                DEBlockEntities.ORBITAL_UPLINK_BEACON_BLOCK_ENTITY.get(),
                pos,
                state,
                DEBlocks.ORBITAL_UPLINK_BEACON.get());
    }

    @Override
    public OrbitalEndpointKind endpointKind() {
        return OrbitalEndpointKind.UPLINK_BEACON;
    }
}
