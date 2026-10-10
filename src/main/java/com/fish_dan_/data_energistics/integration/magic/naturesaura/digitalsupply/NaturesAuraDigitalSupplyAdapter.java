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

    /** Nature's Aura's native spread moves ceil(aura * 0.72) / 6 units; use five times that fixed rate. */
    private static final long RATE_LIMIT = (long) Math.ceil(IAuraChunk.DEFAULT_AURA * 0.72D) / 6L * 5L;
    private static final int RELEASE_THRESHOLD = IAuraChunk.DEFAULT_AURA * 3 / 10;
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
            int aura = IAuraChunk.getAuraInArea(target.level(), link.position(), 0);
            if (aura > IAuraChunk.DEFAULT_AURA && link.mode().supportsPull()) {
                long available = Math.min(RATE_LIMIT, (long) aura - IAuraChunk.DEFAULT_AURA);
                transfer.targetToNetwork(key, available, DigitalSupplyTransferContext.NativeTransfer.reversible(
                        (amount, simulate) -> chunk.drainAura(link.position(), Math.toIntExact(amount), simulate, false),
                        amount -> chunk.storeAura(link.position(), Math.toIntExact(amount), false, false)));
            } else if (aura <= RELEASE_THRESHOLD && link.mode().supportsInput()) {
                transfer.networkToTarget(key, RATE_LIMIT, (amount, simulate) -> chunk.storeAura(link.position(), Math.toIntExact(amount), simulate, false));
            }
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
