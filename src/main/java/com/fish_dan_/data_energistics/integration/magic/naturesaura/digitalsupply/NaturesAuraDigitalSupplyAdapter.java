package com.fish_dan_.data_energistics.integration.magic.naturesaura.digitalsupply;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.api.registry.connector.ConnectorLink;
import com.fish_dan_.data_energistics.api.registry.digitalsupply.DigitalSupplyInterfaceAdapter;
import com.fish_dan_.data_energistics.api.registry.digitalsupply.DigitalSupplyInterfaceTarget;
import com.fish_dan_.data_energistics.api.registry.digitalsupply.DigitalSupplyResourceDefinition;
import com.fish_dan_.data_energistics.api.registry.digitalsupply.DigitalSupplyTransferContext;
import com.fish_dan_.data_energistics.api.registry.digitalsupply.DigitalSupplyTransferDirection;
import com.fish_dan_.data_energistics.api.registry.digitalsupply.DigitalSupplyUnitConversion;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import de.ellpeck.naturesaura.api.NaturesAuraAPI;
import de.ellpeck.naturesaura.api.aura.chunk.IAuraChunk;
import de.ellpeck.naturesaura.api.aura.type.IAuraType;
import de.ellpeck.naturesaura.chunk.AuraChunk;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;

import java.util.EnumSet;

/** Transfers Nature's Aura by per-chunk spot totals with threshold hysteresis. */
public final class NaturesAuraDigitalSupplyAdapter implements DigitalSupplyInterfaceAdapter {

    private static final int STANDARD_AURA = IAuraChunk.DEFAULT_AURA;
    private static final int RECLAIM_THRESHOLD = Math.toIntExact(STANDARD_AURA * 70L / 100L);
    private static final int RELEASE_THRESHOLD = Math.toIntExact(STANDARD_AURA * 45L / 100L);
    private final ObjectList<DigitalSupplyResourceDefinition> resources = createResources();

    @Override
    public ResourceLocation id() {
        return Data_Energistics.id("natures_aura_digital_supply");
    }

    @Override
    public ObjectList<DigitalSupplyResourceDefinition> resources() {
        return resources;
    }

    @Override
    public boolean supports(DigitalSupplyInterfaceTarget target) {
        for (ConnectorLink link : target.links().bindings()) {
            if (target.links().isOnline(link) && typeAt(target, link.position()) != null) return true;
        }
        return false;
    }

    @Override
    public void discover(DigitalSupplyInterfaceTarget target) {
        for (DigitalSupplyResourceDefinition resource : resources) target.setPresence(resource.key(), false);
        boolean present = false;
        for (ConnectorLink link : target.links().bindings()) {
            if (!target.links().isOnline(link)) continue;
            IAuraChunk chunk = auraAt(target, link.position());
            if (chunk != null && NaturesAuraAPI.AURA_TYPES.containsKey(chunk.getType().getName())) {
                target.setPresence(NaturesAuraKey.of(chunk.getType()), true);
                present = true;
            }
        }
        if (present) target.refreshState();
    }

    @Override
    public void tick(DigitalSupplyInterfaceTarget target, DigitalSupplyTransferContext transfer) {
        Long2ObjectLinkedOpenHashMap<ChunkAuraTransfer> chunks = new Long2ObjectLinkedOpenHashMap<>();
        for (ConnectorLink link : target.links().bindings()) {
            if (!target.links().isOnline(link)) continue;
            IAuraChunk auraChunk = auraAt(target, link.position());
            if (auraChunk == null) continue;
            if (!(auraChunk instanceof AuraChunk chunk)) {
                throw new IllegalStateException("Nature's Aura returned an unsupported chunk implementation: " + auraChunk.getClass().getName());
            }

            ChunkPos chunkPos = new ChunkPos(link.position());
            long chunkId = chunkPos.toLong();
            ChunkAuraTransfer chunkTransfer = chunks.get(chunkId);
            if (chunkTransfer == null) {
                int centerY = target.level().getMinBuildHeight() + (target.level().getHeight() - 1) / 2;
                BlockPos center = chunkPos.getMiddleBlockPosition(centerY);
                chunkTransfer = new ChunkAuraTransfer(chunk, center.asLong(), chunkRadius(target.level()), NaturesAuraKey.of(auraChunk.getType()));
                chunks.put(chunkId, chunkTransfer);
            }
            chunkTransfer.canReclaim |= link.mode().supportsPull();
            chunkTransfer.canRelease |= link.mode().supportsInput();
        }

        for (Long2ObjectMap.Entry<ChunkAuraTransfer> entry : chunks.long2ObjectEntrySet()) {
            ChunkAuraTransfer chunkTransfer = entry.getValue();
            BlockPos transferPosition = BlockPos.of(chunkTransfer.position);
            long currentAura = chunkAura(chunkTransfer.chunk, transferPosition, chunkTransfer.radius);
            if (currentAura > RECLAIM_THRESHOLD && chunkTransfer.canReclaim) {
                long excess = Math.min(currentAura - RECLAIM_THRESHOLD, Integer.MAX_VALUE);
                transfer.targetToNetwork(chunkTransfer.key, excess,
                        DigitalSupplyTransferContext.NativeTransfer.reversible(
                                (amount, simulate) -> drainAboveThreshold(chunkTransfer, amount, simulate),
                                amount -> chunkTransfer.chunk.storeAura(transferPosition, Math.toIntExact(amount), false, false)));
            } else if (currentAura < RELEASE_THRESHOLD && chunkTransfer.canRelease) {
                long deficit = Math.min(RECLAIM_THRESHOLD - currentAura, Integer.MAX_VALUE);
                transfer.networkToTarget(chunkTransfer.key, deficit,
                        (amount, simulate) -> chunkTransfer.chunk.storeAura(transferPosition, Math.toIntExact(amount), false, simulate));
            }
        }
    }

    private static long drainAboveThreshold(ChunkAuraTransfer chunkTransfer, long offered, boolean simulate) {
        BlockPos position = BlockPos.of(chunkTransfer.position);
        long currentAura = chunkAura(chunkTransfer.chunk, position, chunkTransfer.radius);
        long allowed = Math.clamp(currentAura - RECLAIM_THRESHOLD, 0L, offered);
        return allowed <= 0L ? 0L : chunkTransfer.chunk.drainAura(position, Math.toIntExact(allowed), false, simulate);
    }

    private static long chunkAura(AuraChunk chunk, BlockPos center, int radius) {
        // The public area query adds DEFAULT_AURA per query; aggregate this chunk's spots exactly once instead.
        long[] aura = {STANDARD_AURA};
        chunk.getSpots(center, radius, (position, amount) -> aura[0] += amount);
        return aura[0];
    }

    private static int chunkRadius(Level level) {
        double halfHeight = (level.getHeight() - 1) / 2.0D;
        return (int) Math.ceil(Math.sqrt(halfHeight * halfHeight + 128.0D));
    }

    private static final class ChunkAuraTransfer {

        private final AuraChunk chunk;
        private final long position;
        private final int radius;
        private final NaturesAuraKey key;
        private boolean canReclaim;
        private boolean canRelease;

        private ChunkAuraTransfer(AuraChunk chunk, long position, int radius, NaturesAuraKey key) {
            this.chunk = chunk;
            this.position = position;
            this.radius = radius;
            this.key = key;
        }
    }

    private static IAuraChunk auraAt(DigitalSupplyInterfaceTarget target, BlockPos position) {
        return IAuraChunk.getAuraChunk(target.level(), position);
    }

    private static IAuraType typeAt(DigitalSupplyInterfaceTarget target, BlockPos position) {
        IAuraChunk chunk = auraAt(target, position);
        return chunk == null ? null : chunk.getType();
    }

    private static ObjectList<DigitalSupplyResourceDefinition> createResources() {
        var result = new ObjectArrayList<DigitalSupplyResourceDefinition>();
        for (NaturesAuraKey key : NaturesAuraKey.all()) {
            result.add(new DigitalSupplyResourceDefinition(key.getId(), key, key.getDisplayName(), DigitalSupplyUnitConversion.IDENTITY,
                    false, EnumSet.of(DigitalSupplyTransferDirection.NETWORK_TO_TARGET, DigitalSupplyTransferDirection.TARGET_TO_NETWORK)));
        }
        return result;
    }
}
