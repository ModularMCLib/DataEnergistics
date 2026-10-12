package com.fish_dan_.data_energistics.ae2.digitalsupply;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import lombok.RequiredArgsConstructor;

/** Insertion-only fluid capability with a virtual tank and no local fluid inventory. */
@RequiredArgsConstructor
public final class DigitalSupplyExternalFluidHandler implements IFluidHandler {

    private final DigitalSupplyExternalInput input;

    @Override
    public int getTanks() {
        return 1;
    }

    @Override
    public FluidStack getFluidInTank(int tank) {
        validateTank(tank);
        return FluidStack.EMPTY;
    }

    @Override
    public int getTankCapacity(int tank) {
        validateTank(tank);
        return FluidType.BUCKET_VOLUME;
    }

    @Override
    public boolean isFluidValid(int tank, FluidStack stack) {
        validateTank(tank);
        return this.input.acceptFluid(stack, true) > 0;
    }

    @Override
    public int fill(FluidStack stack, FluidAction action) {
        return this.input.acceptFluid(stack, action.simulate());
    }

    @Override
    public FluidStack drain(FluidStack stack, FluidAction action) {
        return FluidStack.EMPTY;
    }

    @Override
    public FluidStack drain(int amount, FluidAction action) {
        return FluidStack.EMPTY;
    }

    private static void validateTank(int tank) {
        if (tank != 0) {
            throw new IndexOutOfBoundsException("Invalid Digital Supply input tank: " + tank);
        }
    }
}
