package com.fish_dan_.data_energistics.integration.technology.extendedcrafting.packaged;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.api.crafting.packaged.PackagedMachineAdapter;
import com.fish_dan_.data_energistics.api.crafting.packaged.PackagedMachineOperation;
import com.fish_dan_.data_energistics.common.crafting.packaged.recipe.PackagedCraftingGrid;
import com.fish_dan_.data_energistics.common.crafting.packaged.recipe.PackagedIngredientAssignment;
import com.fish_dan_.data_energistics.common.crafting.packaged.recipe.PackagedOutputMatching;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

import com.blakebr0.cucumber.inventory.BaseItemStackHandler;
import com.blakebr0.extendedcrafting.api.TableCraftingInput;
import com.blakebr0.extendedcrafting.api.crafting.ITableRecipe;
import com.blakebr0.extendedcrafting.container.slot.TableOutputSlot;
import com.blakebr0.extendedcrafting.crafting.recipe.ShapedTableRecipe;
import com.blakebr0.extendedcrafting.init.ModRecipeTypes;
import com.blakebr0.extendedcrafting.tileentity.AdvancedTableTileEntity;
import com.blakebr0.extendedcrafting.tileentity.BasicTableTileEntity;
import com.blakebr0.extendedcrafting.tileentity.EliteTableTileEntity;
import com.blakebr0.extendedcrafting.tileentity.UltimateTableTileEntity;
import com.mojang.authlib.GameProfile;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.jspecify.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/** Runs Extended Crafting's four table tiers through their actual menu result slot. */
public final class TableCrafterAdapter implements PackagedMachineAdapter {

    private final int tier;
    private final ResourceLocation id;

    public TableCrafterAdapter(int tier) {
        this.tier = tier;
        this.id = Data_Energistics.id("extended_crafting_table_" + tier);
    }

    @Override
    public ResourceLocation id() {
        return this.id;
    }

    @Override
    public ObjectSet<ResourceLocation> recipeTypes() {
        return ObjectSet.of(ResourceLocation.fromNamespaceAndPath("extendedcrafting", "table"),
                ResourceLocation.fromNamespaceAndPath("extendedcrafting", "basic_crafting"),
                ResourceLocation.fromNamespaceAndPath("extendedcrafting", "advanced_crafting"),
                ResourceLocation.fromNamespaceAndPath("extendedcrafting", "elite_crafting"),
                ResourceLocation.fromNamespaceAndPath("extendedcrafting", "ultimate_crafting"));
    }

    @Override
    public ObjectSet<ResourceLocation> workstationItemIds() {
        String workstation = switch (this.tier) {
            case 1 -> "basic_table";
            case 2 -> "advanced_table";
            case 3 -> "elite_table";
            case 4 -> "ultimate_table";
            default -> throw new IllegalStateException("Unsupported Extended Crafting table tier: " + this.tier);
        };
        return ObjectSet.of(ResourceLocation.fromNamespaceAndPath("extendedcrafting", workstation));
    }

    @Override
    public boolean recognizes(ServerLevel level, BlockPos position) {
        return table(level, position) != null;
    }

    @Override
    public @Nullable CompoundTag prepare(ServerLevel level, BlockPos position, Direction face,
                                         ResourceLocation recipeId, IPatternDetails pattern, KeyCounter[] inputs) {
        Table table = table(level, position);
        if (table == null || !empty(table.inventory())) return null;
        // Extended Crafting's result slot is a single native transaction. Do
        // not loop over that slot to simulate a bulk craft.
        var holder = level.getRecipeManager().byKey(recipeId);
        if (holder.isEmpty() || !(holder.get().value() instanceof ITableRecipe recipe) ||
                recipe.getType() != ModRecipeTypes.TABLE.get() || recipe.getTier() > this.tier)
            return null;
        int width = recipe instanceof ShapedTableRecipe shaped ?
                shaped.getWidth() : table.width();
        ObjectList<ItemStack> grid = PackagedCraftingGrid.assign(
                new ObjectArrayList<>(recipe.getIngredients()), width, table.width(), inputs);
        if (grid == null) return null;
        TableCraftingInput tableInput = TableCraftingInput.of(table.width(), table.width(), grid, this.tier);
        if (!recipe.matches(tableInput, level)) return null;
        var chosen = level.getRecipeManager().getRecipeFor(ModRecipeTypes.TABLE.get(), tableInput, level);
        if (chosen.isEmpty() || !chosen.get().id().equals(holder.get().id())) return null;
        ItemStack result = recipe.assemble(tableInput, level.registryAccess());
        var nativeRemaining = recipe.getRemainingItems(tableInput);
        var remaining = new ObjectArrayList<ItemStack>(table.width() * table.width());
        for (int i = 0; i < table.width() * table.width(); i++) remaining.add(ItemStack.EMPTY);
        if (nativeRemaining.size() != tableInput.size()) return null;
        for (int y = 0; y < tableInput.height(); y++) for (int x = 0; x < tableInput.width(); x++) {
            remaining.set((y + tableInput.top()) * table.width() + x + tableInput.left(),
                    nativeRemaining.get(y * tableInput.width() + x));
        }
        var expected = new ObjectArrayList<ItemStack>();
        expected.add(result);
        expected.addAll(remaining);
        if (result.isEmpty() || !PackagedIngredientAssignment.outputsMatch(pattern, expected)) return null;
        var progress = new CompoundTag();
        progress.putInt("width", table.width());
        progress.put("inputs", saveStacks(grid, level.registryAccess()));
        progress.put("remaining", saveStacks(remaining, level.registryAccess()));
        progress.put("result", result.save(level.registryAccess()));
        return progress;
    }

    @Override
    public long batchCapacity(ServerLevel level, BlockPos position, Direction face, ResourceLocation recipeId,
                              IPatternDetails pattern, KeyCounter[] prototype, long requestedCount) {
        Table table = table(level, position);
        if (table == null || !empty(table.inventory()) || requestedCount <= 0) return 0;
        var holder = level.getRecipeManager().byKey(recipeId);
        if (holder.isEmpty() || !(holder.get().value() instanceof ITableRecipe recipe) ||
                recipe.getType() != ModRecipeTypes.TABLE.get() || recipe.getTier() > this.tier)
            return 0;
        int width = recipe instanceof ShapedTableRecipe shaped ? shaped.getWidth() : table.width();
        var grid = PackagedCraftingGrid.assign(new ObjectArrayList<>(recipe.getIngredients()), width, table.width(), prototype);
        if (grid == null) return 0;
        var nativeInput = TableCraftingInput.of(table.width(), table.width(), grid, this.tier);
        if (!recipe.matches(nativeInput, level)) return 0;
        var chosen = level.getRecipeManager().getRecipeFor(ModRecipeTypes.TABLE.get(), nativeInput, level);
        if (chosen.isEmpty() || !chosen.get().id().equals(recipeId)) return 0;
        ItemStack result = recipe.assemble(nativeInput, level.registryAccess());
        if (result.isEmpty()) return 0;
        // Return 1 for recipes with containers/remainders. Returning zero
        // makes otherwise valid single crafts impossible to dispatch.
        return 1;
    }

    @Override
    public boolean advance(PackagedMachineOperation operation) {
        Table table = table(operation.level(), operation.position());
        if (table == null) return false;
        CompoundTag progress = operation.progress();
        int width = progress.getInt("width");
        if (width != table.width()) throw new IllegalArgumentException("Extended Crafting table tier changed");
        ListTag encodedInputs = progress.getList("inputs", Tag.TAG_COMPOUND);
        ListTag encodedRemaining = progress.getList("remaining", Tag.TAG_COMPOUND);
        if (encodedInputs.size() != width * width || encodedRemaining.size() != width * width) {
            throw new IllegalArgumentException("Invalid persisted Extended Crafting table grid");
        }
        var inputs = readStacks(operation, encodedInputs);
        var remaining = readStacks(operation, encodedRemaining);
        ItemStack result = ItemStack.parse(operation.level().registryAccess(), progress.getCompound("result"))
                .orElseThrow(() -> new IllegalArgumentException("Missing Extended Crafting table output"));
        boolean delivered = progress.getBoolean("delivered");
        for (int slot = 0; slot < inputs.size(); slot++) {
            ItemStack actual = table.inventory().getStackInSlot(slot);
            if (delivered) {
                if (!actual.isEmpty() && (!ItemStack.isSameItemSameComponents(actual, inputs.get(slot)) ||
                        actual.getCount() > inputs.get(slot).getCount()))
                    throw new IllegalStateException("Extended Crafting table input changed outside this operation");
            } else if (!actual.isEmpty()) {
                return false;
            }
        }
        if (delivered) return collect(operation, table, result, remaining);
        for (int slot = 0; slot < inputs.size(); slot++) {
            ItemStack input = inputs.get(slot);
            if (!input.isEmpty() && !table.inventory().insertItem(slot, input.copy(), true).isEmpty()) return false;
        }
        for (int slot = 0; slot < inputs.size(); slot++) {
            ItemStack input = inputs.get(slot);
            if (input.isEmpty()) continue;
            ItemStack rejected = table.inventory().insertItem(slot, input.copy(), false);
            if (!rejected.isEmpty()) throw new IllegalStateException("Extended Crafting table input insertion changed");
            operation.delivered(AEItemKey.of(input), input.getCount());
        }
        progress.putBoolean("delivered", true);
        operation.changed();
        return true;
    }

    private boolean collect(PackagedMachineOperation operation, Table table, ItemStack result,
                            ObjectList<ItemStack> remaining) {
        MenuContext context = createMenu(table, operation);
        Slot slot = context.menu().getSlot(0);
        if (!(slot instanceof TableOutputSlot outputSlot)) throw new IllegalStateException("Missing table result slot");
        ItemStack actual = outputSlot.getItem().copy();
        if (actual.isEmpty()) return false;
        if (!PackagedOutputMatching.matches(operation, result.copyWithCount(actual.getCount()), actual))
            throw new IllegalStateException("Unexpected Extended Crafting output");
        ItemStack taken = outputSlot.remove(actual.getCount());
        if (!ItemStack.matches(actual, taken)) throw new IllegalStateException("Extended Crafting result extraction changed");
        outputSlot.onTake(context.player(), taken);
        actual = taken;
        operation.returned(AEItemKey.of(actual), actual.getCount());
        for (int index = 0; index < remaining.size(); index++) {
            if (!ItemStack.matches(remaining.get(index), table.inventory().getStackInSlot(index))) {
                throw new IllegalStateException("Extended Crafting table remainder changed after result transfer");
            }
            ItemStack returned = table.inventory().getStackInSlot(index).copy();
            if (!returned.isEmpty()) {
                table.inventory().setStackInSlot(index, ItemStack.EMPTY);
                operation.returned(AEItemKey.of(returned), returned.getCount());
            }
        }
        operation.complete();
        return true;
    }

    private static MenuContext createMenu(Table table, PackagedMachineOperation operation) {
        var fake = FakePlayerFactory.get(operation.level(), new GameProfile(
                UUID.nameUUIDFromBytes(operation.id().toString().getBytes(StandardCharsets.UTF_8)),
                "data_energistics_packaged"));
        return new MenuContext(table.tile().createMenu(0, fake.getInventory(), fake), fake);
    }

    private static ListTag saveStacks(List<? extends ItemStack> stacks, HolderLookup.Provider registries) {
        var encoded = new ListTag();
        for (ItemStack stack : stacks) encoded.add(stack.saveOptional(registries));
        return encoded;
    }

    private static ObjectList<ItemStack> readStacks(PackagedMachineOperation operation, ListTag encoded) {
        var stacks = new ObjectArrayList<ItemStack>(encoded.size());
        for (int index = 0; index < encoded.size(); index++) {
            stacks.add(ItemStack.parseOptional(operation.level().registryAccess(), encoded.getCompound(index)));
        }
        return stacks;
    }

    private static boolean empty(BaseItemStackHandler inventory) {
        for (int slot = 0; slot < inventory.getSlots(); slot++) if (!inventory.getStackInSlot(slot).isEmpty()) return false;
        return true;
    }

    private @Nullable Table table(ServerLevel level, BlockPos position) {
        if (!level.isLoaded(position)) return null;
        var tile = level.getBlockEntity(position);
        if (this.tier == 1 && tile instanceof BasicTableTileEntity basic) return new Table(basic, basic.getInventory(), 3);
        if (this.tier == 2 && tile instanceof AdvancedTableTileEntity advanced) return new Table(advanced, advanced.getInventory(), 5);
        if (this.tier == 3 && tile instanceof EliteTableTileEntity elite) return new Table(elite, elite.getInventory(), 7);
        if (this.tier == 4 && tile instanceof UltimateTableTileEntity ultimate) return new Table(ultimate, ultimate.getInventory(), 9);
        return null;
    }

    private record Table(MenuProvider tile,
                         BaseItemStackHandler inventory, int width) {}

    private record MenuContext(AbstractContainerMenu menu, Player player) {}
}
