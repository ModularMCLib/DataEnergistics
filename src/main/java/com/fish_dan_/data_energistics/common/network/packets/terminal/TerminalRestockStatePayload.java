package com.fish_dan_.data_energistics.common.network.packets.terminal;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.client.terminal.magnet.TerminalRestockClient;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/** Exact component-sensitive ME amounts for the nine hotbar slots and offhand, independent of an open screen. */
public record TerminalRestockStatePayload(List<GenericStack> amounts) implements CustomPacketPayload {

    public static final Type<TerminalRestockStatePayload> TYPE = new Type<>(Data_Energistics.id("terminal_restock_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TerminalRestockStatePayload> STREAM_CODEC = StreamCodec.composite(
            GenericStack.STREAM_CODEC.apply(ByteBufCodecs.list(10)), TerminalRestockStatePayload::amounts, TerminalRestockStatePayload::new);

    public TerminalRestockStatePayload {
        amounts = List.copyOf(amounts);
        if (amounts.size() > 10 || amounts.stream().anyMatch(stack -> !(stack.what() instanceof AEItemKey) || stack.amount() < 0))
            throw new IllegalArgumentException("Invalid terminal restock snapshot");
    }

    @Override
    public Type<TerminalRestockStatePayload> type() {
        return TYPE;
    }

    public static void handle(TerminalRestockStatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> TerminalRestockClient.receive(payload.amounts()));
    }
}
