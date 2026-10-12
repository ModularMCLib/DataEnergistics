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

import de.ellpeck.naturesaura.api.NaturesAuraAPI;
import de.ellpeck.naturesaura.api.aura.chunk.IAuraChunk;
import de.ellpeck.naturesaura.api.aura.type.IAuraType;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;

import java.util.EnumSet;

/** Transfers Nature's Aura using the public IAuraChunk API with threshold hysteresis. */
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
        for (ConnectorLink link : target.links().bindings()) {
            if (!target.links().isOnline(link)) continue;
            IAuraChunk chunk = auraAt(target, link.position());
            if (chunk == null) continue;
            NaturesAuraKey key = NaturesAuraKey.of(chunk.getType());
            int currentAura = IAuraChunk.getAuraInArea(target.level(), link.position(), 0);
            if (currentAura > RECLAIM_THRESHOLD && link.mode().supportsPull()) {
                long excess = (long) currentAura - RECLAIM_THRESHOLD;
                transfer.targetToNetwork(key, excess,
                        DigitalSupplyTransferContext.NativeTransfer.reversible(
                                (amount, simulate) -> drainAboveThreshold(target, chunk, link.position(), amount, simulate),
                                amount -> chunk.storeAura(link.position(), Math.toIntExact(amount), false, false)));
            } else if (currentAura < RELEASE_THRESHOLD && link.mode().supportsInput()) {
                long deficit = (long) RECLAIM_THRESHOLD - currentAura;
                transfer.networkToTarget(key, deficit,
                        (amount, simulate) -> chunk.storeAura(link.position(), Math.toIntExact(amount), false, simulate));
            }
        }
    }

    private static long drainAboveThreshold(DigitalSupplyInterfaceTarget target, IAuraChunk chunk,
                                            BlockPos position, long offered, boolean simulate) {
        int currentAura = IAuraChunk.getAuraInArea(target.level(), position, 0);
        long allowed = Math.clamp((long) currentAura - RECLAIM_THRESHOLD, 0L, offered);
        return allowed <= 0L ? 0L : chunk.drainAura(position, Math.toIntExact(allowed), false, simulate);
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
