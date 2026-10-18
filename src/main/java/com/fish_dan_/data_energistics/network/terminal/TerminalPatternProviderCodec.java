package com.fish_dan_.data_energistics.network.terminal;

import com.fish_dan_.data_energistics.menu.patternencoding.PatternEncodingPreviewMenu.SyncedPatternProviderList;

import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.network.connection.ConnectionType;

import io.netty.buffer.Unpooled;
import org.jspecify.annotations.NullMarked;

/** Reuses the existing upload row wire format inside the independent terminal snapshot. */
@NullMarked
public final class TerminalPatternProviderCodec {

    private TerminalPatternProviderCodec() {}

    public static byte[] encode(SyncedPatternProviderList providers, RegistryAccess registries) {
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries, ConnectionType.OTHER);
        try {
            providers.writeToPacket(buffer);
            byte[] data = new byte[buffer.readableBytes()];
            buffer.readBytes(data);
            return data;
        } finally {
            buffer.release();
        }
    }

    public static SyncedPatternProviderList decode(byte[] data, RegistryAccess registries) {
        if (data.length == 0) return SyncedPatternProviderList.EMPTY;
        var buffer = new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(data), registries, ConnectionType.OTHER);
        try {
            SyncedPatternProviderList state = new SyncedPatternProviderList(buffer);
            if (buffer.isReadable()) throw new IllegalArgumentException("Trailing terminal upload data");
            return state;
        } finally {
            buffer.release();
        }
    }
}
