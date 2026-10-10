package com.fish_dan_.data_energistics.integration.magic.astral;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.api.registry.digitalsupply.DigitalSupplyInterfaceAdapter;
import com.fish_dan_.data_energistics.api.registry.digitalsupply.DigitalSupplyInterfaceTarget;
import com.fish_dan_.data_energistics.api.registry.digitalsupply.DigitalSupplyResourceDefinition;
import com.fish_dan_.data_energistics.api.registry.digitalsupply.DigitalSupplyTransferContext;
import com.fish_dan_.data_energistics.api.registry.digitalsupply.DigitalSupplyTransferDirection;
import com.fish_dan_.data_energistics.api.registry.digitalsupply.DigitalSupplyUnitConversion;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.storage.MEStorage;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;

import hellfirepvp.astralsorcery.common.constellation.BaseConstellation;
import hellfirepvp.astralsorcery.common.lib.FluidsAS;
import hellfirepvp.astralsorcery.common.lib.LumenAS;
import hellfirepvp.astralsorcery.common.lib.RegistriesAS;
import hellfirepvp.astralsorcery.common.linking.LinkContainer;
import hellfirepvp.astralsorcery.common.lumen.Lumen;
import hellfirepvp.astralsorcery.common.lumen.transfer.LumenNetworkHelper;
import hellfirepvp.astralsorcery.common.lumen.transfer.LumenNode;
import hellfirepvp.astralsorcery.common.starlight.StarlightNetworkLevelHelper;
import hellfirepvp.astralsorcery.common.starlight.api.TransmissionNode;
import hellfirepvp.astralsorcery.common.starlight.api.TransmissionReceiverNode;
import hellfirepvp.astralsorcery.common.starlight.transmission.StarlightTransmissionPacket;
import hellfirepvp.astralsorcery.common.tile.TileAltar;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.EnumSet;
import java.util.Optional;

/** Astral Sorcery integration for lumen, constellation brightness and liquid starlight. */
@NullMarked
public final class AstralSorceryDigitalSupplyAdapter implements DigitalSupplyInterfaceAdapter {

    private static final ResourceLocation ID = Data_Energistics.id("astral_sorcery");
    private static final ResourceLocation STARLIGHT_ID = Data_Energistics.id("astral/liquid_starlight");
    private static final long TICK_LIMIT = 1_000L;
    private static final long CONSTELLATION_AE_PER_BRIGHTNESS = 1_000L;
    private static final ObjectLinkedOpenHashSet<DigitalSupplyTransmissionReceiverNode> REGISTERED_NODES = new ObjectLinkedOpenHashSet<>();
    private final ObjectList<DigitalSupplyResourceDefinition> resourceCatalog;

    public AstralSorceryDigitalSupplyAdapter() {
        this.resourceCatalog = createResources();
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public boolean supports(DigitalSupplyInterfaceTarget target) {
        return !target.level().isClientSide();
    }

    @Override
    public ObjectList<DigitalSupplyResourceDefinition> resources() {
        return this.resourceCatalog;
    }

    private static ObjectList<DigitalSupplyResourceDefinition> createResources() {
        ObjectArrayList<DigitalSupplyResourceDefinition> result = new ObjectArrayList<>();
        for (var entry : RegistriesAS.REGISTRY_LUMEN.entrySet()) {
            ResourceLocation registryId = RegistriesAS.REGISTRY_LUMEN.getKey(entry.getValue());
            if (registryId == null || entry.getValue() == null || entry.getValue() == LumenAS.NONE.get() || entry.getValue() == LumenAS.PRISMATIC.get()) {
                continue;
            }
            AstralSorceryKey key = AstralSorceryKey.lumen(registryId, entry.getValue());
            result.add(new DigitalSupplyResourceDefinition(key.getId(), key, entry.getValue().getName(),
                    DigitalSupplyUnitConversion.IDENTITY, false,
                    EnumSet.of(DigitalSupplyTransferDirection.NETWORK_TO_TARGET,
                            DigitalSupplyTransferDirection.TARGET_TO_NETWORK)));
        }
        for (var entry : RegistriesAS.REGISTRY_CONSTELLATIONS.entrySet()) {
            ResourceLocation registryId = RegistriesAS.REGISTRY_CONSTELLATIONS.getKey(entry.getValue());
            if (registryId == null || entry.getValue() == null || isNoneConstellation(registryId)) {
                continue;
            }
            AstralSorceryKey key = AstralSorceryKey.constellation(registryId, entry.getValue());
            result.add(new DigitalSupplyResourceDefinition(key.getId(), key, entry.getValue().getName(),
                    DigitalSupplyUnitConversion.IDENTITY, false,
                    EnumSet.of(DigitalSupplyTransferDirection.NETWORK_TO_TARGET,
                            DigitalSupplyTransferDirection.TARGET_TO_NETWORK)));
        }
        if (FluidsAS.LIQUID_STARLIGHT.getSource().isBound()) {
            AEFluidKey key = AEFluidKey.of(FluidsAS.LIQUID_STARLIGHT.getSource().get());
            result.add(new DigitalSupplyResourceDefinition(STARLIGHT_ID, key,
                    FluidsAS.LIQUID_STARLIGHT.getFluidType().getDescription(),
                    DigitalSupplyUnitConversion.IDENTITY, false,
                    EnumSet.of(DigitalSupplyTransferDirection.NETWORK_TO_TARGET,
                            DigitalSupplyTransferDirection.TARGET_TO_NETWORK)));
        }
        return ObjectList.of(result.toArray(DigitalSupplyResourceDefinition[]::new));
    }

    private static boolean isNoneConstellation(ResourceLocation registryId) {
        return registryId.getPath().equals("none");
    }

    /** Receives native Lens output as fixed-point brightness through the optional Astral transmission node. */
    public static void receiveTransmission(AstralDigitalSupplyReceiver receiver, StarlightTransmissionPacket packet) {
        if (packet.constellation() == null || !Float.isFinite(packet.amount()) || packet.amount() <= 0.0F) {
            return;
        }
        ResourceLocation registryId = RegistriesAS.REGISTRY_CONSTELLATIONS.getKey(packet.constellation());
        if (registryId == null || isNoneConstellation(registryId)) {
            return;
        }
        double scaledAmount = packet.amount() * (double) CONSTELLATION_AE_PER_BRIGHTNESS;
        if (!Double.isFinite(scaledAmount) || scaledAmount < 1.0D || scaledAmount > Long.MAX_VALUE) {
            return;
        }
        AstralSorceryKey key = AstralSorceryKey.constellation(registryId, packet.constellation());
        MEStorage storage = receiver.data_energistics$networkStorage();
        if (storage == null) {
            return;
        }
        long aeAmount = (long) Math.floor(scaledAmount);
        long simulated = storage.insert(key, aeAmount, Actionable.SIMULATE, IActionSource.empty());
        if (simulated < 0L || simulated > aeAmount) {
            throw new IllegalStateException("Astral constellation storage returned an invalid simulated insertion amount");
        }
        if (simulated > 0L) {
            long inserted = storage.insert(key, simulated, Actionable.MODULATE, IActionSource.empty());
            if (inserted < 0L || inserted > simulated) {
                throw new IllegalStateException("Astral constellation storage returned an invalid insertion amount");
            }
        }
    }

    /** Accepts Liquid Starlight from an external fluid capability into the ordinary AE network. */
    @Override
    public int acceptFluid(DigitalSupplyInterfaceTarget target, FluidStack stack, boolean simulate) {
        if (stack.isEmpty() || !stack.is(FluidsAS.LIQUID_STARLIGHT.getSource().get())) {
            return 0;
        }
        MEStorage storage = target.networkStorage();
        if (storage == null) {
            return 0;
        }
        AEFluidKey key = AEFluidKey.of(stack);
        if (key == null) {
            return 0;
        }
        long accepted = storage.insert(key, stack.getAmount(),
                simulate ? Actionable.SIMULATE : Actionable.MODULATE, IActionSource.empty());
        if (accepted < 0L || accepted > stack.getAmount()) {
            throw new IllegalStateException("Astral Liquid Starlight input returned an invalid insertion amount");
        }
        return Math.toIntExact(accepted);
    }

    /** Sends packets for DSI nodes linked by Astral's own linking tool after Astral's native source pass. */
    public static void transmitRegisteredInterfaces(ServerLevel level) {
        if (REGISTERED_NODES.isEmpty()) {
            return;
        }
        ObjectList<DigitalSupplyTransmissionReceiverNode> snapshot = new ObjectArrayList<>(REGISTERED_NODES);
        StarlightNetworkLevelHelper helper = StarlightNetworkLevelHelper.get(level);
        for (DigitalSupplyTransmissionReceiverNode node : snapshot) {
            if (!level.isLoaded(node.getNodePos())) {
                continue;
            }
            if (!(level.getBlockEntity(node.getNodePos()) instanceof AstralDigitalSupplyReceiver receiver)) {
                continue;
            }
            LinkContainer links = receiver.data_energistics$astralLinkContainer();
            Optional<StarlightTransmissionPacket> packet = produceTransmission(
                    receiver.data_energistics$networkStorage(), level, links);
            if (packet.isEmpty()) {
                continue;
            }
            StarlightTransmissionPacket output = packet.get().withMultiplier(node.getTransmissionLossMultiplier());
            for (var connection : links.getLinkedTo()) {
                TransmissionNode linked = helper.getNode(connection.getTo()).orElse(null);
                if (linked instanceof TransmissionReceiverNode && acceptsConstellation(level, connection.getTo(), output.constellation())) {
                    ((TransmissionReceiverNode) linked).receiveStarlight(level, output);
                }
            }
        }
    }

    /**
     * Produces a packet only for constellation targets explicitly linked by Astral's own linking tool.
     * The interface's connector links and the automatic 15x15x15 scan are intentionally not consulted here.
     */
    static Optional<StarlightTransmissionPacket> produceTransmission(@Nullable MEStorage storage,
                                                                     ServerLevel level,
                                                                     LinkContainer links) {
        ObjectLinkedOpenHashSet<BaseConstellation> requested = new ObjectLinkedOpenHashSet<>();
        LongList receiverPositions = new LongArrayList();
        StarlightNetworkLevelHelper helper = StarlightNetworkLevelHelper.get(level);
        for (var connection : links.getLinkedTo()) {
            if (helper.getNode(connection.getTo()).orElse(null) instanceof TransmissionReceiverNode) {
                addRequestedConstellations(level, connection.getTo(), requested);
                receiverPositions.add(connection.getTo().asLong());
            }
        }
        if (requested.isEmpty()) {
            return Optional.empty();
        }
        if (storage == null) {
            return Optional.empty();
        }
        ObjectList<BaseConstellation> ordered = new ObjectArrayList<>(requested);
        int start = (int) Math.floorMod(level.getGameTime() / 20L, (long) ordered.size());
        for (int offset = 0; offset < ordered.size(); offset++) {
            BaseConstellation constellation = ordered.get((start + offset) % ordered.size());
            AstralSorceryKey key = keyFor(constellation);
            if (key == null || storage.extract(key, 1L, Actionable.SIMULATE, IActionSource.empty()) <= 0L) {
                continue;
            }
            int matchingReceivers = 0;
            for (int index = 0; index < receiverPositions.size(); index++) {
                BlockPos position = BlockPos.of(receiverPositions.getLong(index));
                if (acceptsConstellation(level, position, constellation)) {
                    matchingReceivers++;
                }
            }
            if (matchingReceivers == 0) {
                continue;
            }
            long requestedAmount = TICK_LIMIT * (long) matchingReceivers;
            long simulated = storage.extract(key, requestedAmount, Actionable.SIMULATE, IActionSource.empty());
            if (simulated <= 0L) {
                continue;
            }
            long extracted = storage.extract(key, simulated, Actionable.MODULATE, IActionSource.empty());
            if (extracted <= 0L) {
                continue;
            }
            float brightness = extracted / ((float) CONSTELLATION_AE_PER_BRIGHTNESS * matchingReceivers);
            if (brightness > 0.0F) {
                return Optional.of(new StarlightTransmissionPacket(constellation, brightness));
            }
        }
        return Optional.empty();
    }

    private static boolean acceptsConstellation(Level level, BlockPos position, BaseConstellation constellation) {
        ObjectLinkedOpenHashSet<BaseConstellation> requested = new ObjectLinkedOpenHashSet<>();
        addRequestedConstellations(level, position, requested);
        return requested.contains(constellation);
    }

    @Override
    public void discover(DigitalSupplyInterfaceTarget target) {
        MEStorage storage = target.networkStorage();
        for (DigitalSupplyResourceDefinition definition : resources()) {
            if (definition.key() instanceof AstralSorceryKey key && key.getKind() == AstralSorceryKey.Kind.CONSTELLATION) {
                // Constellation brightness is the real AE quantity; legacy presence markers are removed.
                target.setPresence(key, false);
                continue;
            }
            boolean present = storage != null && storage.extract(definition.key(), 1L, Actionable.SIMULATE, IActionSource.empty()) > 0L;
            target.setPresence(definition.key(), present);
        }
        target.refreshState();
    }

    @Override
    public void updateLinks(DigitalSupplyInterfaceTarget target) {
        target.links().removeOfflineLinks();
    }

    @Override
    public void tick(DigitalSupplyInterfaceTarget target, DigitalSupplyTransferContext transfer) {
        this.ensureLumenNode(target);
        // Lumen uses Astral's native LumenNode and capability; Liquid Starlight uses the altar-specific discovery.
    }

    @Override
    public void detach(DigitalSupplyInterfaceTarget target) {
        if (target.level() instanceof ServerLevel level) {
            LumenNetworkHelper.getNode(level, target.position()).ifPresent(node -> {
                if (node.getConnectionType() == LumenNode.ConnectionType.SOURCE) {
                    LumenNetworkHelper.removeNode(level, node);
                }
            });
        }
    }

    public static void registerTransmissionNode(DigitalSupplyTransmissionReceiverNode node) {
        REGISTERED_NODES.add(node);
    }

    public static void unregisterTransmissionNode(DigitalSupplyTransmissionReceiverNode node) {
        REGISTERED_NODES.remove(node);
    }

    private void ensureLumenNode(DigitalSupplyInterfaceTarget target) {
        if (!(target.level() instanceof ServerLevel level)) {
            return;
        }
        LumenNode node = LumenNetworkHelper.getNode(level, target.position()).orElse(null);
        if (node == null) {
            node = LumenNetworkHelper.createNode(
                    level, target.position(), Vec3.atCenterOf(target.position()), LumenNode.ConnectionType.SOURCE)
                    .orElse(null);
        }
        if (node == null || node.getConnectionType() != LumenNode.ConnectionType.SOURCE) {
            return;
        }
        ObjectLinkedOpenHashSet<Lumen> provided = new ObjectLinkedOpenHashSet<>();
        MEStorage storage = target.networkStorage();
        if (storage != null) {
            for (var entry : RegistriesAS.REGISTRY_LUMEN.entrySet()) {
                Lumen lumen = entry.getValue();
                if (lumen == null || lumen == LumenAS.NONE.get() || lumen == LumenAS.PRISMATIC.get()) {
                    continue;
                }
                ResourceLocation id = RegistriesAS.REGISTRY_LUMEN.getKey(lumen);
                if (id != null && storage.extract(AstralSorceryKey.lumen(id, lumen), 1L,
                        Actionable.SIMULATE, IActionSource.empty()) > 0L) {
                    provided.add(lumen);
                }
            }
        }
        LumenNetworkHelper.setNodeProvidedLumenTypes(level, node, provided);
    }

    private static void addRequestedConstellations(Level level, BlockPos position,
                                                   ObjectSet<BaseConstellation> output) {
        BlockEntity blockEntity = level.getBlockEntity(position);
        if (!(blockEntity instanceof TileAltar altar)) {
            return;
        }
        altar.getTileData().getActiveRecipe()
                .flatMap(active -> active.getRecipe(level))
                .ifPresent(recipe -> output.addAll(recipe.getRequiredStarlight()));
    }

    private static @Nullable AstralSorceryKey keyFor(BaseConstellation constellation) {
        ResourceLocation registryId = RegistriesAS.REGISTRY_CONSTELLATIONS.getKey(constellation);
        return registryId == null || isNoneConstellation(registryId) ? null : AstralSorceryKey.constellation(registryId, constellation);
    }
}
