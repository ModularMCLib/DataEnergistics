package com.fish_dan_.data_energistics.integration.magic.astral;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.api.registry.connector.ConnectorLink;
import com.fish_dan_.data_energistics.api.registry.worldenergy.DigitalSupplyInterfaceAdapter;
import com.fish_dan_.data_energistics.api.registry.worldenergy.DigitalSupplyInterfaceTarget;
import com.fish_dan_.data_energistics.api.registry.worldenergy.WorldEnergyResourceDefinition;
import com.fish_dan_.data_energistics.api.registry.worldenergy.WorldEnergyTransferContext;
import com.fish_dan_.data_energistics.api.registry.worldenergy.WorldEnergyTransferDirection;
import com.fish_dan_.data_energistics.api.registry.worldenergy.WorldEnergyUnitConversion;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEKey;

import hellfirepvp.astralsorcery.common.constellation.BaseConstellation;
import hellfirepvp.astralsorcery.common.lib.FluidsAS;
import hellfirepvp.astralsorcery.common.lib.LumenAS;
import hellfirepvp.astralsorcery.common.lib.RegistriesAS;
import hellfirepvp.astralsorcery.common.lumen.ILumenHandler;
import hellfirepvp.astralsorcery.common.lumen.Lumen;
import hellfirepvp.astralsorcery.common.lumen.LumenStack;
import hellfirepvp.astralsorcery.common.lumen.transfer.LumenNetworkHelper;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import org.jspecify.annotations.NullMarked;

import java.util.EnumSet;

/** Public-API Astral Sorcery bridge for lumen, constellation markers and liquid starlight. */
@NullMarked
public final class AstralSorceryDigitalSupplyAdapter implements DigitalSupplyInterfaceAdapter {

    private static final ResourceLocation ID = Data_Energistics.id("astral_sorcery");
    private static final ResourceLocation STARLIGHT_ID = Data_Energistics.id("astral/liquid_starlight");
    private static final long TICK_LIMIT = 1_000L;
    private static final int NATIVE_LIMIT = 1_000;
    private static boolean constellationWarningLogged;

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public boolean supports(DigitalSupplyInterfaceTarget target) {
        for (ConnectorLink link : target.links().bindings()) {
            if (!target.links().isOnline(link)) continue;
            if (target.level().getCapability(ILumenHandler.BLOCK, link.position(), link.side()) != null
                    || target.level().getCapability(Capabilities.FluidHandler.BLOCK, link.position(), link.side()) != null
                    || LumenNetworkHelper.getNode(target.level(), link.position()).isPresent()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public ObjectList<WorldEnergyResourceDefinition> resources() {
        ObjectArrayList<WorldEnergyResourceDefinition> result = new ObjectArrayList<>();
        for (var entry : RegistriesAS.REGISTRY_LUMEN.entrySet()) {
            ResourceLocation registryId = RegistriesAS.REGISTRY_LUMEN.getKey(entry.getValue());
            if (registryId == null || entry.getValue() == null || entry.getValue() == LumenAS.NONE.get()
                    || entry.getValue() == LumenAS.PRISMATIC.get()) {
                continue;
            }
            AstralSorceryKey key = AstralSorceryKey.lumen(registryId, entry.getValue());
            result.add(new WorldEnergyResourceDefinition(key.getId(), key, entry.getValue().getName(),
                    WorldEnergyUnitConversion.IDENTITY, false,
                    EnumSet.of(WorldEnergyTransferDirection.NETWORK_TO_WORLD, WorldEnergyTransferDirection.WORLD_TO_NETWORK)));
        }
        for (var entry : RegistriesAS.REGISTRY_CONSTELLATIONS.entrySet()) {
            ResourceLocation registryId = RegistriesAS.REGISTRY_CONSTELLATIONS.getKey(entry.getValue());
            if (registryId == null || entry.getValue() == null) continue;
            AstralSorceryKey key = AstralSorceryKey.constellation(registryId, entry.getValue());
            result.add(new WorldEnergyResourceDefinition(key.getId(), key, entry.getValue().getName(),
                    WorldEnergyUnitConversion.IDENTITY, true,
                    EnumSet.of(WorldEnergyTransferDirection.NETWORK_TO_WORLD)));
        }
        if (FluidsAS.LIQUID_STARLIGHT.getSource().isBound()) {
            AEFluidKey key = AEFluidKey.of(FluidsAS.LIQUID_STARLIGHT.getSource().get());
            result.add(new WorldEnergyResourceDefinition(STARLIGHT_ID, key, FluidsAS.LIQUID_STARLIGHT.getFluidType().getDescription(),
                    WorldEnergyUnitConversion.IDENTITY, false,
                    EnumSet.of(WorldEnergyTransferDirection.NETWORK_TO_WORLD, WorldEnergyTransferDirection.WORLD_TO_NETWORK)));
        }
        return result;
    }

    @Override
    public void discover(DigitalSupplyInterfaceTarget target) {
        for (WorldEnergyResourceDefinition definition : resources()) {
            if (definition.presenceMarker()) {
                // A registry entry proves that a constellation type exists, not that this interface received it.
                // Native transmission callbacks are responsible for setting this marker.
                continue;
            }
            boolean present = false;
            for (ConnectorLink link : target.links().bindings()) {
                if (!target.links().isOnline(link)) continue;
                if (definition.key() instanceof AstralSorceryKey key && key.kind() == AstralSorceryKey.Kind.LUMEN) {
                    Lumen lumen = (Lumen) key.value();
                    ILumenHandler handler = target.level().getCapability(ILumenHandler.BLOCK, link.position(), link.side());
                    present |= handler != null && handler.getContainedLumen(lumen).map(stack -> stack.getAmount() > 0).orElse(false);
                    present |= LumenNetworkHelper.getNode(target.level(), link.position())
                            .map(node -> node.getProvidedLumenTypes().contains(lumen) || node.doesRelayLumenType(lumen)).orElse(false);
                } else if (definition.key() instanceof AEFluidKey fluid) {
                    IFluidHandler handler = target.level().getCapability(Capabilities.FluidHandler.BLOCK, link.position(), link.side());
                    present |= handler != null && handler.getTanks() > 0 && handler.getFluidInTank(0).is(fluid.getFluid());
                }
                if (present) break;
            }
            target.setPresence(definition.key(), present);
        }
        target.refreshState();
    }

    @Override
    public void updateLinks(DigitalSupplyInterfaceTarget target) {
        target.links().removeOfflineLinks();
    }

    @Override
    public void tick(DigitalSupplyInterfaceTarget target, WorldEnergyTransferContext transfer) {
        for (ConnectorLink link : target.links().bindings()) {
            if (!target.links().isOnline(link)) continue;
            for (WorldEnergyResourceDefinition definition : resources()) {
                if (definition.key() instanceof AstralSorceryKey key && key.kind() == AstralSorceryKey.Kind.LUMEN) {
                    transferLumen(target, transfer, link, definition, key);
                } else if (definition.key() instanceof AEFluidKey fluid) {
                    transferFluid(target, transfer, link, definition, fluid);
                } else if (definition.presenceMarker() && !constellationWarningLogged) {
                    constellationWarningLogged = true;
                    Data_Energistics.LOGGER.warn("Astral Sorcery constellation resources expose presence markers only; no public quantity transfer API is available");
                }
            }
        }
    }

    private static void transferLumen(DigitalSupplyInterfaceTarget target, WorldEnergyTransferContext transfer,
                                      ConnectorLink link, WorldEnergyResourceDefinition definition, AstralSorceryKey key) {
        ILumenHandler handler = target.level().getCapability(ILumenHandler.BLOCK, link.position(), link.side());
        if (handler == null) return;
        Lumen lumen = (Lumen) key.value();
        if (link.mode().supportsInput() && definition.allows(WorldEnergyTransferDirection.NETWORK_TO_WORLD)) {
            transfer.networkToWorld(key, TICK_LIMIT, (amount, simulate) -> {
                int nativeAmount = Math.min((int) Math.min(amount, NATIVE_LIMIT), NATIVE_LIMIT);
                return handler.fill(LumenStack.of(lumen, nativeAmount), simulate ? ILumenHandler.Action.SIMULATE : ILumenHandler.Action.EXECUTE);
            });
        }
        if (link.mode().supportsPull() && definition.allows(WorldEnergyTransferDirection.WORLD_TO_NETWORK)) {
            transfer.worldToNetwork(key, TICK_LIMIT, (amount, simulate) -> {
                int nativeAmount = Math.min((int) Math.min(amount, NATIVE_LIMIT), NATIVE_LIMIT);
                return handler.drain(lumen, nativeAmount, simulate ? ILumenHandler.Action.SIMULATE : ILumenHandler.Action.EXECUTE).getAmount();
            });
        }
    }

    private static void transferFluid(DigitalSupplyInterfaceTarget target, WorldEnergyTransferContext transfer,
                                      ConnectorLink link, WorldEnergyResourceDefinition definition, AEFluidKey key) {
        IFluidHandler handler = target.level().getCapability(Capabilities.FluidHandler.BLOCK, link.position(), link.side());
        if (handler == null) return;
        if (link.mode().supportsInput() && definition.allows(WorldEnergyTransferDirection.NETWORK_TO_WORLD)) {
            transfer.networkToWorld(key, TICK_LIMIT, (amount, simulate) -> handler.fill(new FluidStack(key.getFluid(), (int) Math.min(amount, NATIVE_LIMIT)),
                    simulate ? IFluidHandler.FluidAction.SIMULATE : IFluidHandler.FluidAction.EXECUTE));
        }
        if (link.mode().supportsPull() && definition.allows(WorldEnergyTransferDirection.WORLD_TO_NETWORK)) {
            transfer.worldToNetwork(key, TICK_LIMIT, (amount, simulate) -> handler.drain(new FluidStack(key.getFluid(), (int) Math.min(amount, NATIVE_LIMIT)),
                    simulate ? IFluidHandler.FluidAction.SIMULATE : IFluidHandler.FluidAction.EXECUTE).getAmount());
        }
    }

}
