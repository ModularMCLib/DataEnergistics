package com.fish_dan_.data_energistics.common.weapon.platform.network;

import com.fish_dan_.data_energistics.client.weapon.platform.DomainClientState;
import com.fish_dan_.data_energistics.common.weapon.platform.domain.DomainVisualSnapshot;
import com.fish_dan_.data_energistics.common.weapon.platform.domain.StasisDomain;

import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.networking.rpc.RPCPacketDistributor;
import com.lowdragmc.lowdraglib2.networking.rpc.RPCPacketHandler;
import com.lowdragmc.lowdraglib2.syncdata.rpc.RPCSender;
import com.lowdragmc.lowdraglib2.utils.ByteBufUtil;

import net.minecraft.server.MinecraftServer;

import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;

import java.util.UUID;

public final class DomainRpc {

    private static final String SNAPSHOT = "data_energistics:stasis_domains";

    public static void register() {
        RPCPacketDistributor.registerPacket(SNAPSHOT, new RPCPacketHandler() {

            @Override
            public byte[] args2Bytes(Object... args) {
                return ByteBufUtil.writeCustomData(buffer -> DomainVisualSnapshot.STREAM_CODEC.encode(buffer, (DomainVisualSnapshot) args[0]), Platform.getFrozenRegistry());
            }

            @Override
            public Object[] bytes2Args(byte[] bytes) {
                Object[] args = new Object[1];
                ByteBufUtil.readCustomData(bytes, buffer -> args[0] = DomainVisualSnapshot.STREAM_CODEC.decode(buffer), Platform.getFrozenRegistry());
                return args;
            }

            @Override
            public void handler(RPCSender sender, Object... args) {
                if (sender.isServer()) DomainClientState.accept((DomainVisualSnapshot) args[0]);
            }
        });
    }

    public static void push(MinecraftServer server, ObjectList<StasisDomain> domains) {
        var exemptions = new ObjectOpenHashSet<UUID>();
        for (var player : server.getPlayerList().getPlayers()) if (player.isCreative() || player.isSpectator() || player.hasPermissions(2)) exemptions.add(player.getUUID());
        var snapshot = new DomainVisualSnapshot(domains, exemptions);
        for (var player : server.getPlayerList().getPlayers()) RPCPacketDistributor.rpcToPlayer(player, SNAPSHOT, snapshot);
    }
}
