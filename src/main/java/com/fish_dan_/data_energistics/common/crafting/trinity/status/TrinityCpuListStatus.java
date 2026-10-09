package com.fish_dan_.data_energistics.common.crafting.trinity.status;

import com.fish_dan_.data_energistics.common.crafting.trinity.execution.cpu.TrinityDataCoreCraftingRuntime;
import com.fish_dan_.data_energistics.common.crafting.trinity.execution.cpu.TrinityDataCoreVirtualCpu;
import com.fish_dan_.data_energistics.common.crafting.trinity.profile.TrinityDataCoreCpuContribution;
import com.fish_dan_.data_energistics.util.FastUtilCollections;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;

import java.util.Comparator;

/** Ordered immutable synchronization snapshot for the CPUs currently published by one Trinity structure. */
public record TrinityCpuListStatus(ObjectList<TrinityCpuStatus> cpus) {

    public static final TrinityCpuListStatus EMPTY = new TrinityCpuListStatus(ObjectList.of());
    public static final Codec<TrinityCpuListStatus> CODEC = TrinityCpuStatus.CODEC.listOf().xmap(
            statuses -> new TrinityCpuListStatus(new ObjectArrayList<>(statuses)),
            TrinityCpuListStatus::cpus);
    public static final StreamCodec<RegistryFriendlyByteBuf, TrinityCpuListStatus> STREAM_CODEC = StreamCodec.of(
            TrinityCpuListStatus::encode,
            TrinityCpuListStatus::decode);
    private static final int MAX_CPU_COUNT = TrinityDataCoreCpuContribution.MAX_PARTITION_COUNT + 1;

    public TrinityCpuListStatus {
        if (cpus == null) {
            throw new IllegalArgumentException("Trinity CPU status list is required");
        }
        if (cpus.size() > MAX_CPU_COUNT) {
            throw new IllegalArgumentException("Trinity CPU status list exceeds the maximum published CPU count");
        }
        ObjectList<TrinityCpuStatus> sorted = new ObjectArrayList<>(cpus);
        if (sorted.contains(null)) {
            throw new IllegalArgumentException("Trinity CPU status list must not contain null entries");
        }
        sorted.sort(Comparator.comparingInt(TrinityCpuStatus::number));
        for (int index = 0; index < sorted.size(); index++) {
            TrinityCpuStatus status = sorted.get(index);
            if (index > 0 && sorted.get(index - 1).number() == status.number()) {
                throw new IllegalArgumentException("Duplicate Trinity CPU number: " + status.number());
            }
        }
        cpus = FastUtilCollections.immutableList(sorted);
    }

    /** Captures the runtime's exact AE2-visible publication snapshot. */
    public static TrinityCpuListStatus from(TrinityDataCoreCraftingRuntime runtime) {
        if (runtime == null) {
            throw new IllegalArgumentException("Trinity crafting runtime is required");
        }
        return fromPublishedCpus(runtime.publishedCpus());
    }

    /** Captures a caller-owned snapshot returned by {@link TrinityDataCoreCraftingRuntime#publishedCpus()}. */
    public static TrinityCpuListStatus fromPublishedCpus(ObjectList<TrinityDataCoreVirtualCpu> publishedCpus) {
        if (publishedCpus == null) {
            throw new IllegalArgumentException("Published Trinity CPU list is required");
        }
        return new TrinityCpuListStatus(publishedCpus.stream().map(TrinityCpuStatus::from).collect(ObjectArrayList.toList()));
    }

    private static void encode(RegistryFriendlyByteBuf data, TrinityCpuListStatus status) {
        data.writeVarInt(status.cpus.size());
        for (TrinityCpuStatus cpu : status.cpus) {
            TrinityCpuStatus.STREAM_CODEC.encode(data, cpu);
        }
    }

    private static TrinityCpuListStatus decode(RegistryFriendlyByteBuf data) {
        int count = data.readVarInt();
        if (count < 0 || count > MAX_CPU_COUNT) {
            throw new IllegalArgumentException("Invalid synchronized Trinity CPU count: " + count);
        }
        ObjectList<TrinityCpuStatus> statuses = new ObjectArrayList<>(count);
        for (int index = 0; index < count; index++) {
            statuses.add(TrinityCpuStatus.STREAM_CODEC.decode(data));
        }
        return new TrinityCpuListStatus(statuses);
    }
}
