package com.fish_dan_.data_energistics.integration.magic.neovitae.digitalsupply;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.ae2.key.BloodKey;
import com.fish_dan_.data_energistics.ae2.key.DigitalBiologicalResources;
import com.fish_dan_.data_energistics.api.registry.connector.ConnectorLink;
import com.fish_dan_.data_energistics.api.registry.worldenergy.DigitalSupplyInterfaceAdapter;
import com.fish_dan_.data_energistics.api.registry.worldenergy.DigitalSupplyInterfaceTarget;
import com.fish_dan_.data_energistics.api.registry.worldenergy.WorldEnergyResourceDefinition;
import com.fish_dan_.data_energistics.api.registry.worldenergy.WorldEnergyTransferContext;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import com.breakinblocks.neovitae.api.altar.IAraVitae;
import com.breakinblocks.neovitae.api.capability.NVCapabilities;
import com.breakinblocks.neovitae.common.fluid.NVFluids;
import it.unimi.dsi.fastutil.objects.ObjectList;

/** Supplies the NeoVitae Ara Vitae blood tank from the shared Data Energistics blood key. */
public final class NeoVitaeDigitalSupplyAdapter implements DigitalSupplyInterfaceAdapter {

    private static final ResourceLocation ADAPTER_ID = Data_Energistics.id("neovitae_digital_supply");
    private static final long RATE_LIMIT = 256L;
    private static final ObjectList<WorldEnergyResourceDefinition> RESOURCES = ObjectList.of(DigitalBiologicalResources.BLOOD);

    @Override
    public ResourceLocation id() {
        return ADAPTER_ID;
    }

    @Override
    public ObjectList<WorldEnergyResourceDefinition> resources() {
        return RESOURCES;
    }

    @Override
    public int priority() {
        return 80;
    }

    @Override
    public boolean supports(DigitalSupplyInterfaceTarget target) {
        for (ConnectorLink link : target.links().bindings()) {
            if (target.links().isOnline(link) && araVitaeAt(target, link) != null) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void discover(DigitalSupplyInterfaceTarget target) {
        boolean present = false;
        for (ConnectorLink link : target.links().bindings()) {
            if (target.links().isOnline(link) && araVitaeAt(target, link) != null) {
                present = true;
                break;
            }
        }
        target.setPresence(BloodKey.INSTANCE, present);
        target.refreshState();
    }

    @Override
    public void tick(DigitalSupplyInterfaceTarget target, WorldEnergyTransferContext transfer) {
        for (ConnectorLink link : target.links().bindings()) {
            if (!target.links().isOnline(link)) {
                continue;
            }
            IAraVitae altar = araVitaeAt(target, link);
            if (altar == null) {
                continue;
            }
            IFluidHandler fluidHandler = altar.getFluidHandler();
            if (fluidHandler == null) {
                continue;
            }
            if (link.mode().supportsInput()) {
                transfer.networkToWorld(BloodKey.INSTANCE, RATE_LIMIT,
                        (amount, simulate) -> fill(fluidHandler, amount, simulate));
            }
            if (link.mode().supportsPull()) {
                transfer.worldToNetwork(BloodKey.INSTANCE, RATE_LIMIT,
                        (amount, simulate) -> drain(fluidHandler, amount, simulate));
            }
        }
    }

    private static IAraVitae araVitaeAt(DigitalSupplyInterfaceTarget target, ConnectorLink link) {
        return target.level().getCapability(NVCapabilities.ARA_VITAE, link.position(), link.side());
    }

    private static long fill(IFluidHandler handler, long amount, boolean simulate) {
        int bounded = (int) Math.min(amount, RATE_LIMIT);
        if (bounded <= 0) {
            return 0;
        }
        FluidStack blood = new FluidStack(NVFluids.ESSENTIA_VITAE_SOURCE.get(), bounded);
        return handler.fill(blood, simulate ? IFluidHandler.FluidAction.SIMULATE : IFluidHandler.FluidAction.EXECUTE);
    }

    private static long drain(IFluidHandler handler, long amount, boolean simulate) {
        int bounded = (int) Math.min(amount, RATE_LIMIT);
        if (bounded <= 0) {
            return 0;
        }
        FluidStack drained = handler.drain(
                new FluidStack(NVFluids.ESSENTIA_VITAE_SOURCE.get(), bounded),
                simulate ? IFluidHandler.FluidAction.SIMULATE : IFluidHandler.FluidAction.EXECUTE);
        return drained.getFluid() == NVFluids.ESSENTIA_VITAE_SOURCE.get() ? drained.getAmount() : 0;
    }
}
