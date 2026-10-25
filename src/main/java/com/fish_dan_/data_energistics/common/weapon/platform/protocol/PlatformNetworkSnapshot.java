package com.fish_dan_.data_energistics.common.weapon.platform.protocol;

import com.fish_dan_.data_energistics.common.terminal.player.connection.TerminalConnection;
import com.fish_dan_.data_energistics.common.weapon.platform.model.GunAmmunition;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.longs.LongLists;

/** Read-only AE telemetry; resource estimates never authorize or pay for a shot. */
public record PlatformNetworkSnapshot(long frequency, double stored, double capacity, double input, double usage, LongList ammunition) {

    public static final PlatformNetworkSnapshot EMPTY = new PlatformNetworkSnapshot(0, 0, 0, 0, 0, LongList.of(0, 0, 0, 0));
    public static final Codec<PlatformNetworkSnapshot> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.fieldOf("frequency").forGetter(PlatformNetworkSnapshot::frequency),
            Codec.doubleRange(0, Double.MAX_VALUE).fieldOf("stored").forGetter(PlatformNetworkSnapshot::stored),
            Codec.doubleRange(0, Double.MAX_VALUE).fieldOf("capacity").forGetter(PlatformNetworkSnapshot::capacity),
            Codec.doubleRange(0, Double.MAX_VALUE).fieldOf("input").forGetter(PlatformNetworkSnapshot::input),
            Codec.doubleRange(0, Double.MAX_VALUE).fieldOf("usage").forGetter(PlatformNetworkSnapshot::usage),
            Codec.LONG.validate(amount -> amount >= 0 ? DataResult.success(amount) : DataResult.error(() -> "Negative network ammunition"))
                    .listOf(4, 4).<LongList>xmap(LongArrayList::new, list -> list).fieldOf("ammunition").forGetter(PlatformNetworkSnapshot::ammunition))
            .apply(instance, PlatformNetworkSnapshot::new));

    public static PlatformNetworkSnapshot capture(TerminalConnection connection) {
        var energy = connection.grid().getEnergyService();
        var contents = connection.grid().getStorageService().getCachedInventory();
        var amounts = new LongArrayList(4);
        for (GunAmmunition effect : GunAmmunition.values()) if (effect.key != null) amounts.add(contents.get(effect.key));
        return new PlatformNetworkSnapshot(connection.frequency(), energy.getStoredPower(), energy.getMaxStoredPower(),
                energy.getAvgPowerInjection(), energy.getAvgPowerUsage(), LongLists.unmodifiable(amounts));
    }
}
