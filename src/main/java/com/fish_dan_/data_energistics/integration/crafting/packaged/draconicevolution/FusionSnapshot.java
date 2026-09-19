package com.fish_dan_.data_energistics.integration.crafting.packaged.draconicevolution;

import net.minecraft.world.item.ItemStack;

import com.brandon3055.brandonscore.api.TechLevel;
import com.brandon3055.draconicevolution.api.crafting.IFusionInjector;
import com.brandon3055.draconicevolution.api.crafting.IFusionInventory;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.List;

/** Detached recipe input used only for pure recipe validation and output assembly. */
final class FusionSnapshot implements IFusionInventory {

    private ItemStack catalyst;
    private ItemStack output = ItemStack.EMPTY;
    private final List<IFusionInjector> injectors;
    private final TechLevel minimumTier;

    private FusionSnapshot(ItemStack catalyst, List<IFusionInjector> injectors, TechLevel minimumTier) {
        this.catalyst = catalyst.copy();
        this.injectors = injectors;
        this.minimumTier = minimumTier;
    }

    static FusionSnapshot inventory(ItemStack catalyst,
                                    List<FusionRecipePlan.PlannedIngredient> ingredients,
                                    TechLevel minimumTier) {
        var injectors = new ObjectArrayList<IFusionInjector>();
        for (var ingredient : ingredients) {
            injectors.add(new SnapshotInjector(ingredient.input(), minimumTier));
        }
        return new FusionSnapshot(catalyst, injectors, minimumTier);
    }

    @Override
    public ItemStack getCatalystStack() {
        return this.catalyst;
    }

    @Override
    public ItemStack getOutputStack() {
        return this.output;
    }

    @Override
    public void setCatalystStack(ItemStack stack) {
        this.catalyst = stack.copy();
    }

    @Override
    public void setOutputStack(ItemStack stack) {
        this.output = stack.copy();
    }

    @Override
    public List<IFusionInjector> getInjectors() {
        return this.injectors;
    }

    @Override
    public TechLevel getMinimumTier() {
        return this.minimumTier;
    }

    @Override
    public ItemStack getItem(int index) {
        if (index <= 0) return this.catalyst;
        int injector = index - 1;
        return injector < this.injectors.size() ? this.injectors.get(injector).getInjectorStack() : ItemStack.EMPTY;
    }

    @Override
    public int size() {
        return this.injectors.size() + 1;
    }

    private static final class SnapshotInjector implements IFusionInjector {

        private ItemStack stack;
        private final TechLevel tier;
        private long energy;
        private long energyRequirement;

        private SnapshotInjector(ItemStack stack, TechLevel tier) {
            this.stack = stack.copy();
            this.tier = tier;
        }

        @Override
        public TechLevel getInjectorTier() {
            return this.tier;
        }

        @Override
        public ItemStack getInjectorStack() {
            return this.stack;
        }

        @Override
        public void setInjectorStack(ItemStack stack) {
            this.stack = stack.copy();
        }

        @Override
        public long getInjectorEnergy() {
            return this.energy;
        }

        @Override
        public void setInjectorEnergy(long energy) {
            this.energy = energy;
        }

        @Override
        public void setEnergyRequirement(long maxEnergy, long chargeRate) {
            this.energyRequirement = maxEnergy;
        }

        @Override
        public long getEnergyRequirement() {
            return this.energyRequirement;
        }

        @Override
        public boolean validate() {
            return true;
        }
    }
}
