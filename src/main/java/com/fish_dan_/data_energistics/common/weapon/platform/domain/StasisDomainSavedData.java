package com.fish_dan_.data_energistics.common.weapon.platform.domain;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.data.DEAttachments;
import com.fish_dan_.data_energistics.common.data.DEDataComponents;
import com.fish_dan_.data_energistics.common.research.item.ItemResearchUpgrades;
import com.fish_dan_.data_energistics.common.terminal.player.connection.TerminalQuantumAccess;
import com.fish_dan_.data_energistics.common.weapon.platform.equipment.EquippedWeaponPlatform;
import com.fish_dan_.data_energistics.common.weapon.platform.model.GunAmmunition;
import com.fish_dan_.data_energistics.common.weapon.platform.model.PlatformAbility;
import com.fish_dan_.data_energistics.common.weapon.platform.network.DomainRpc;
import com.fish_dan_.data_energistics.common.weapon.platform.network.NetworkShotPayment;
import com.fish_dan_.data_energistics.common.weapon.platform.storage.PlatformAbilitySettings;
import com.fish_dan_.data_energistics.common.weapon.platform.storage.WeaponPlatformInventory;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.ticks.LevelChunkTicks;
import net.minecraft.world.ticks.LevelTicks;
import net.minecraft.world.ticks.ScheduledTick;
import net.minecraft.world.ticks.TickPriority;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/** World-owned domain clock, delayed impacts and paused native updates. No chunk tickets are issued. */
public final class StasisDomainSavedData extends SavedData {

    public static final ResourceLocation EXPANSION = Data_Energistics.id("biochip/specialized/weapon_platform/domain_expansion");
    private static final String NAME = Data_Energistics.MODID + "_stasis_domains";
    private static final Reference2ObjectOpenHashMap<MinecraftServer, StasisDomainSavedData> SERVERS = new Reference2ObjectOpenHashMap<>();
    private static final Reference2ObjectOpenHashMap<LevelTicks<?>, ServerLevel> TICK_LEVELS = new Reference2ObjectOpenHashMap<>();
    private static final Codec<StasisDomainSavedData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            StasisDomain.CODEC.listOf().fieldOf("domains").forGetter(data -> data.domains),
            DeferredImpact.CODEC.listOf().fieldOf("impacts").forGetter(data -> new ObjectArrayList<>(data.impacts)),
            DomainChunkState.CODEC.listOf().fieldOf("chunks").forGetter(data -> data.chunks),
            DeferredBlockEvent.CODEC.listOf().fieldOf("block_events").forGetter(data -> data.blockEvents),
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.LONG).fieldOf("cooldowns").forGetter(data -> data.cooldowns)).apply(instance, (domains, impacts, chunks, blockEvents, cooldowns) -> {
                var data = new StasisDomainSavedData();
                data.domains.addAll(domains);
                data.impacts.addAll(impacts);
                data.chunks.addAll(chunks);
                data.blockEvents.addAll(blockEvents);
                data.cooldowns.putAll(cooldowns);
                data.reindex();
                return data;
            }));
    private static final Factory<StasisDomainSavedData> FACTORY = new Factory<>(StasisDomainSavedData::new, StasisDomainSavedData::load);
    private final ObjectArrayList<StasisDomain> domains = new ObjectArrayList<>();
    private final ReferenceLinkedOpenHashSet<DeferredImpact> impacts = new ReferenceLinkedOpenHashSet<>();
    private @Nullable ObjectList<DeferredImpact> replaySnapshot;
    private final ObjectArrayList<DomainChunkState> chunks = new ObjectArrayList<>();
    private final ObjectArrayList<DeferredBlockEvent> blockEvents = new ObjectArrayList<>();
    private final Object2LongOpenHashMap<UUID> cooldowns = new Object2LongOpenHashMap<>();
    private final Object2ObjectOpenHashMap<ResourceKey<Level>, Long2ObjectOpenHashMap<ObjectList<StasisDomain>>> index = new Object2ObjectOpenHashMap<>();
    private final ReferenceOpenHashSet<LevelChunk> capturedChunks = new ReferenceOpenHashSet<>();
    private int replaying;
    private boolean visualsDirty = true;

    public static StasisDomainSavedData get(MinecraftServer server) {
        var value = SERVERS.get(server);
        if (value == null) {
            value = server.overworld().getDataStorage().computeIfAbsent(FACTORY, NAME);
            SERVERS.put(server, value);
        }
        return value;
    }

    public static void release(MinecraftServer server) {
        SERVERS.remove(server);
        TICK_LEVELS.values().removeIf(level -> level.getServer() == server);
    }

    public static <T> boolean pause(LevelTicks<T> ticks, ScheduledTick<T> tick) {
        var level = TICK_LEVELS.get(ticks);
        return level != null && get(level.getServer()).pause(level, tick);
    }

    public ObjectList<StasisDomain> domains() {
        return domains;
    }

    public @Nullable ObjectList<StasisDomain> at(ResourceKey<Level> level, long chunk) {
        var world = index.get(level);
        return world == null ? null : world.get(chunk);
    }

    public boolean frozen(Level level, long chunk) {
        return at(level.dimension(), chunk) != null;
    }

    public int cooldown(MinecraftServer server, UUID owner) {
        return (int) Math.max(0, cooldowns.getLong(owner) - server.overworld().getGameTime());
    }

    public int remaining(MinecraftServer server, UUID owner) {
        for (var domain : domains) if (domain.owner().equals(owner)) return (int) Math.max(0, domain.expires() - server.overworld().getGameTime());
        return 0;
    }

    public void toggle(ServerPlayer player) {
        for (var domain : domains) if (domain.owner().equals(player.getUUID())) {
            end(player.serverLevel().getServer(), domain);
            return;
        }
        var server = player.serverLevel().getServer();
        var platform = EquippedWeaponPlatform.find(player);
        if (platform.isEmpty() || cooldown(server, player.getUUID()) > 0) return;
        var chip = new WeaponPlatformInventory(platform).getStackInSlot(WeaponPlatformInventory.CHIP_SLOT);
        var research = ItemResearchUpgrades.of(chip).ids();
        if (!research.contains(PlatformAbility.DOMAIN.research)) return;
        var connection = TerminalQuantumAccess.resolve(player);
        if (connection == null) return;
        var settings = platform.getOrDefault(DEDataComponents.PLATFORM_ABILITIES, PlatformAbilitySettings.DEFAULT);
        int size = settings.domainChunks() == 5 && research.contains(EXPANSION) ? 5 : 3;
        var domain = new StasisDomain(player.getUUID(), player.level().dimension(), player.chunkPosition().x, player.chunkPosition().z, player.getY(), size, server.overworld().getGameTime() + 1200);
        NetworkShotPayment.pay(platform, connection, 64800, GunAmmunition.DATA_FLOW, 1000, true, ammo -> {
            domains.add(domain);
            reindex();
            captureChunks(server);
            visualsDirty = true;
            setDirty();
        });
    }

    public void tick(MinecraftServer server) {
        for (var level : server.getAllLevels()) {
            TICK_LEVELS.put(level.getBlockTicks(), level);
            TICK_LEVELS.put(level.getFluidTicks(), level);
        }
        long now = server.overworld().getGameTime();
        if (cooldowns.object2LongEntrySet().removeIf(entry -> entry.getLongValue() <= now)) setDirty();
        for (var domain : new ObjectArrayList<>(domains)) {
            var player = server.getPlayerList().getPlayer(domain.owner());
            if (player == null || !player.isAlive() || player.level().dimension() != domain.dimension() || EquippedWeaponPlatform.find(player).isEmpty() || now >= domain.expires()) {
                end(server, domain);
                continue;
            }
            var platform = EquippedWeaponPlatform.find(player);
            if (!ItemResearchUpgrades.of(new WeaponPlatformInventory(platform).getStackInSlot(WeaponPlatformInventory.CHIP_SLOT)).ids().contains(PlatformAbility.DOMAIN.research)) {
                end(server, domain);
                continue;
            }
            var connection = TerminalQuantumAccess.resolve(player);
            if (connection == null || NetworkShotPayment.pay(platform, connection, 64800, GunAmmunition.DATA_FLOW, 1000, true, ammo -> setDirty()) == null) end(server, domain);
        }
        captureChunks(server);
        resumeChunks(server);
        resumeBlockEvents(server);
        replay(server);
        if (visualsDirty || now % 20 == 0) {
            DomainRpc.push(server, domains);
            visualsDirty = false;
        }
    }

    public void endOwner(MinecraftServer server, UUID owner) {
        for (var domain : new ObjectArrayList<>(domains)) if (domain.owner().equals(owner)) end(server, domain);
    }

    private void end(MinecraftServer server, StasisDomain domain) {
        domains.remove(domain);
        cooldowns.put(domain.owner(), server.overworld().getGameTime() + 4000);
        reindex();
        capturedChunks.removeIf(chunk -> !frozen(chunk.getLevel(), chunk.getPos().toLong()));
        visualsDirty = true;
        setDirty();
    }

    public void defer(Entity target, DeferredImpact.Payload payload) {
        impacts.add(new DeferredImpact(target.getUUID(), target.getData(DEAttachments.DOMAIN_LIFE), target.level().dimension(), payload));
        replaySnapshot = null;
        setDirty();
    }

    public boolean replaying() {
        return replaying > 0;
    }

    private void replay(MinecraftServer server) {
        ObjectList<DeferredImpact> pending = replaySnapshot;
        if (pending == null) replaySnapshot = pending = new ObjectArrayList<>(impacts);
        var waiting = new ObjectOpenHashSet<UUID>();
        for (var impact : pending) {
            // Death and weapon callbacks may discard later entries or enqueue new impacts during this pass.
            if (!impacts.contains(impact)) continue;
            var level = server.getLevel(impact.dimension());
            Entity target = level == null ? null : level.getEntity(impact.subject());
            if (target != null && (!target.isAlive() || !target.getData(DEAttachments.DOMAIN_LIFE).equals(impact.life()))) {
                impacts.remove(impact);
                replaySnapshot = null;
                setDirty();
                continue;
            }
            if (target == null || DomainStasis.frozen(target) || waiting.contains(impact.subject()) || !impact.payload().ready(level)) {
                waiting.add(impact.subject());
                continue;
            }
            impacts.remove(impact);
            replaySnapshot = null;
            setDirty();
            try {
                impact.payload().apply(target, level);
            } catch (RuntimeException failure) {
                Data_Energistics.LOGGER.error("Deferred domain impact failed for {} in {}", impact.subject(), impact.dimension().location(), failure);
            }
        }
    }

    public void discard(UUID subject) {
        if (impacts.removeIf(impact -> impact.subject().equals(subject))) {
            replaySnapshot = null;
            setDirty();
        }
    }

    public boolean binaryReserved(Entity target) {
        for (var impact : impacts) if (impact.subject().equals(target.getUUID()) && impact.payload() instanceof DeferredImpact.Binary) return true;
        return false;
    }

    public boolean pauseBlockEvent(ServerLevel level, BlockPos pos, Block block, int action, int parameter) {
        if (!frozen(level, ChunkPos.asLong(pos))) return false;
        var event = new DeferredBlockEvent(level.dimension(), pos.immutable(), BuiltInRegistries.BLOCK.getKey(block), action, parameter);
        if (!blockEvents.contains(event)) {
            blockEvents.add(event);
            setDirty();
        }
        return true;
    }

    private void resumeBlockEvents(MinecraftServer server) {
        var iterator = blockEvents.iterator();
        while (iterator.hasNext()) {
            var event = iterator.next();
            var level = server.getLevel(event.dimension());
            if (level == null || frozen(level, ChunkPos.asLong(event.position())) || !level.getChunkSource().hasChunk(event.position().getX() >> 4, event.position().getZ() >> 4)) continue;
            level.blockEvent(event.position(), BuiltInRegistries.BLOCK.get(event.block()), event.action(), event.parameter());
            iterator.remove();
            setDirty();
        }
    }

    public <T> boolean pause(ServerLevel level, ScheduledTick<T> tick) {
        if (replaying() || !frozen(level, ChunkPos.asLong(tick.pos()))) return false;
        boolean fluid = tick.type() instanceof Fluid;
        ResourceLocation type = fluid ? BuiltInRegistries.FLUID.getKey((Fluid) tick.type()) : BuiltInRegistries.BLOCK.getKey((Block) tick.type());
        DomainChunkState state = chunkState(level, ChunkPos.asLong(tick.pos()));
        for (var existing : state.ticks()) if (existing.fluid() == fluid && existing.position().equals(tick.pos()) && existing.type().equals(type)) return true;
        state.ticks().add(new FrozenChunkTicks(tick.pos(), type, fluid, Math.max(0, tick.triggerTick() - level.getGameTime()), tick.priority().getValue(), tick.subTickOrder()));
        setDirty();
        return true;
    }

    private DomainChunkState chunkState(ServerLevel level, long chunk) {
        for (var state : chunks) if (state.dimension() == level.dimension() && state.chunk() == chunk) return state;
        var state = new DomainChunkState(level.dimension(), chunk, new ObjectArrayList<>());
        chunks.add(state);
        return state;
    }

    private void captureChunks(MinecraftServer server) {
        for (var entry : index.object2ObjectEntrySet()) {
            var level = server.getLevel(entry.getKey());
            if (level == null) continue;
            for (long key : entry.getValue().keySet()) {
                var pos = new ChunkPos(key);
                var access = level.getChunkSource().getChunk(pos.x, pos.z, ChunkStatus.FULL, false);
                if (!(access instanceof LevelChunk chunk) || !capturedChunks.add(chunk)) continue;
                var blockTicks = (LevelChunkTicks<Block>) chunk.getBlockTicks();
                var fluidTicks = (LevelChunkTicks<Fluid>) chunk.getFluidTicks();
                blockTicks.getAll().forEach(tick -> pause(level, tick));
                fluidTicks.getAll().forEach(tick -> pause(level, tick));
                blockTicks.removeIf(tick -> true);
                fluidTicks.removeIf(tick -> true);
            }
        }
    }

    public void unload(LevelChunk chunk) {
        capturedChunks.remove(chunk);
    }

    private void resumeChunks(MinecraftServer server) {
        var iterator = chunks.iterator();
        while (iterator.hasNext()) {
            var state = iterator.next();
            var level = server.getLevel(state.dimension());
            if (level == null || frozen(level, state.chunk())) continue;
            var pos = new ChunkPos(state.chunk());
            if (!level.getChunkSource().hasChunk(pos.x, pos.z)) continue;
            replaying++;
            try {
                for (var tick : state.ticks()) {
                    long at = level.getGameTime() + tick.delay();
                    var priority = TickPriority.byValue(tick.priority());
                    if (tick.fluid()) level.getFluidTicks().schedule(new ScheduledTick<>(BuiltInRegistries.FLUID.get(tick.type()), tick.position(), at, priority, tick.order()));
                    else level.getBlockTicks().schedule(new ScheduledTick<>(BuiltInRegistries.BLOCK.get(tick.type()), tick.position(), at, priority, tick.order()));
                }
            } finally {
                replaying--;
            }
            iterator.remove();
            setDirty();
        }
    }

    private void reindex() {
        index.clear();
        for (var domain : domains) {
            var world = index.computeIfAbsent(domain.dimension(), ignored -> new Long2ObjectOpenHashMap<>());
            for (int x = domain.x() - domain.size() / 2; x <= domain.x() + domain.size() / 2; x++) for (int z = domain.z() - domain.size() / 2; z <= domain.z() + domain.size() / 2; z++) world.computeIfAbsent(ChunkPos.asLong(x, z), ignored -> new ObjectArrayList<>()).add(domain);
        }
    }

    private static StasisDomainSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        return CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        return (CompoundTag) CODEC.encodeStart(NbtOps.INSTANCE, this).getOrThrow();
    }
}
