package com.fish_dan_.data_energistics.ae2.digitalsupply;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.api.registry.digitalsupply.DigitalSupplyInterfaceAdapter;
import com.fish_dan_.data_energistics.api.registry.digitalsupply.DigitalSupplyInterfaceTarget;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import lombok.RequiredArgsConstructor;

/** Dispatches external inputs to the frozen adapter catalog without keeping an item or fluid buffer. */
@RequiredArgsConstructor
public final class DigitalSupplyExternalInput {

    private final DigitalSupplyInterfaceTarget target;
    private final ObjectList<DigitalSupplyInterfaceAdapter> adapters;
    private final ObjectSet<ResourceLocation> failedAdapters;

    public int acceptItem(ItemStack stack, boolean simulate) {
        if (stack.isEmpty() || !isOnline()) {
            return 0;
        }
        ItemStack remainder = stack.copy();
        for (DigitalSupplyInterfaceAdapter adapter : this.adapters) {
            if (this.failedAdapters.contains(adapter.id())) {
                continue;
            }
            try {
                int accepted = adapter.acceptItem(this.target, remainder.copy(), simulate);
                validateAccepted(accepted, remainder.getCount());
                remainder.shrink(accepted);
                if (remainder.isEmpty()) {
                    break;
                }
            } catch (RuntimeException exception) {
                disableAdapter(adapter, "item", stack, exception);
                break;
            }
        }
        return stack.getCount() - remainder.getCount();
    }

    public int acceptFluid(FluidStack stack, boolean simulate) {
        if (stack.isEmpty() || !isOnline()) {
            return 0;
        }
        FluidStack remainder = stack.copy();
        for (DigitalSupplyInterfaceAdapter adapter : this.adapters) {
            if (this.failedAdapters.contains(adapter.id())) {
                continue;
            }
            try {
                int accepted = adapter.acceptFluid(this.target, remainder.copy(), simulate);
                validateAccepted(accepted, remainder.getAmount());
                remainder.shrink(accepted);
                if (remainder.isEmpty()) {
                    break;
                }
            } catch (RuntimeException exception) {
                disableAdapter(adapter, "fluid", stack, exception);
                break;
            }
        }
        return stack.getAmount() - remainder.getAmount();
    }

    private boolean isOnline() {
        return this.target.networkStorage() != null && !this.target.level().isClientSide();
    }

    private static void validateAccepted(int accepted, int offered) {
        if (accepted < 0 || accepted > offered) {
            throw new IllegalStateException("Digital Supply input accepted " + accepted + " of " + offered);
        }
    }

    private void disableAdapter(DigitalSupplyInterfaceAdapter adapter, String kind, Object input,
                                RuntimeException exception) {
        this.failedAdapters.add(adapter.id());
        Data_Energistics.LOGGER.error(
                "Disabled Digital Supply Interface adapter {} at {} after external {} input {} failed",
                adapter.id(), this.target.position(), kind, input, exception);
    }
}
