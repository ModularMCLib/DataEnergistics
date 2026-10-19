package com.fish_dan_.data_energistics.common.terminal.player.workstation;

import com.fish_dan_.data_energistics.api.research.ResearchAccess;
import com.fish_dan_.data_energistics.common.anvil.DigitalAnvilUpgrade;
import com.fish_dan_.data_energistics.common.anvil.enchantment.DigitalAnvilCalculation;
import com.fish_dan_.data_energistics.common.anvil.enchantment.DigitalAnvilOperation;
import com.fish_dan_.data_energistics.common.terminal.player.connection.TerminalConnection;
import com.fish_dan_.data_energistics.common.terminal.player.persistence.TerminalPlayerState;
import com.fish_dan_.data_energistics.common.terminal.player.storage.TerminalNetworkStorage;
import com.fish_dan_.data_energistics.common.terminal.player.transfer.TerminalIngredientSupply;
import com.fish_dan_.data_energistics.common.terminal.player.workstation.crafting.TerminalCraftingRecipes;
import com.fish_dan_.data_energistics.data.research.PlayerTerminalResearch;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;

import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.event.EventHooks;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;

/**
 * Vanilla recipe execution and the project's digital anvil, independent of quantum connectivity except payment.
 */
public final class TerminalWorkstation {

    private TerminalWorkstation() {}

    public static ItemStack preview(ServerPlayer player, TerminalPlayerState state) {
        return preview(player, state, true);
    }

    private static ItemStack preview(ServerPlayer player, TerminalPlayerState state, boolean configured) {
        ItemStack result = switch (state.workspace()) {
            case CRAFTING -> TerminalCraftingRecipes.resolve(player, state.work(), configured).output(player, state.work());
            case PROCESSING -> ItemStack.EMPTY;
            case SMITHING -> {
                SmithingRecipeInput input = smithingInput(state.work(), configured);
                var recipe = player.serverLevel().getRecipeManager().getRecipeFor(RecipeType.SMITHING, input, player.level());
                yield recipe.map(value -> value.value().assemble(input, player.registryAccess())).orElse(ItemStack.EMPTY);
            }
            case STONECUTTING -> {
                var recipes = stonecuttingRecipes(player, state.work(), configured);
                yield recipes.isEmpty() ? ItemStack.EMPTY : recipes.get(Math.min(state.work().recipeIndex(), recipes.size() - 1)).value()
                        .assemble(new SingleRecipeInput(input(state.work(), 0, configured)), player.registryAccess());
            }
            case ANVIL -> anvil(player, state.work()).output().copy();
        };
        return result.isItemEnabled(player.level().enabledFeatures()) ? result : ItemStack.EMPTY;
    }

    public static TerminalIngredientSupply.Result take(ServerPlayer player, TerminalPlayerState state, @Nullable TerminalConnection connection, boolean shift) {
        int completed = 0;
        for (int repeat = 0; repeat < (shift ? 64 : 1); repeat++) {
            ItemStack product = preview(player, state);
            if (product.isEmpty()) break;
            if (!fits(player, product, shift)) return result(completed == 0 ? "inventory_full" : "crafted");
            if (state.workspace() == TerminalWorkspace.ANVIL) {
                if (connection == null) return result("offline");
                if (state.anvilPayment().pending()) return result("refund_pending");
                var operation = anvil(player, state.work());
                state.paymentFrequency(connection.frequency());
                if (!state.anvilPayment().pay(connection.grid(), connection.source(), operation.cost(), state::changed))
                    return result("insufficient");
                if (!operation.preserveLeft()) state.work().inputs.setItem(0, ItemStack.EMPTY);
                state.work().inputs.removeItem(1, operation.rightConsumed());
                deliver(player, product, shift);
                player.serverLevel().levelEvent(1030, player.blockPosition(), 0);
            } else {
                rememberInputs(state);
                var supply = TerminalIngredientSupply.supplyFromNetwork(player, state, connection, materialTargets(state));
                if (!supply.message().isEmpty()) return supply;
                product = preview(player, state, false);
                if (product.isEmpty()) return result("invalid_recipe");
                if (!fits(player, product, shift)) return result(completed == 0 ? "inventory_full" : "crafted");
                if (state.workspace() == TerminalWorkspace.CRAFTING) {
                    consumeCrafting(player, state.work(), product, shift);
                    state.changed();
                    completed++;
                    continue;
                }
                ObjectList<ItemStack> relevant = new ObjectArrayList<>();
                for (int index = 0; index < state.work().inputs.getContainerSize(); index++)
                    relevant.add(state.work().inputs.getItem(index).copy());
                RecipeHolder<?> recipe = state.workspace() == TerminalWorkspace.SMITHING ?
                        player.serverLevel().getRecipeManager().getRecipeFor(RecipeType.SMITHING, smithingInput(state.work(), false), player.level()).orElseThrow() :
                        stonecuttingRecipes(player, state.work(), false).get(Math.min(state.work().recipeIndex(), stonecuttingRecipes(player, state.work(), false).size() - 1));
                for (int index = 0; index < state.work().inputs.getContainerSize(); index++)
                    state.work().inputs.removeItem(index, 1);
                deliver(player, product, shift);
                product.onCraftedBy(player.level(), player, product.getCount());
                player.triggerRecipeCrafted(recipe, relevant);
                if (!recipe.value().isSpecial()) player.awardRecipes(ObjectList.of(recipe));
                if (repeat == 0) {
                    if (state.workspace() == TerminalWorkspace.SMITHING)
                        player.serverLevel().levelEvent(1044, player.blockPosition(), 0);
                    else
                        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.UI_STONECUTTER_TAKE_RESULT, SoundSource.PLAYERS, 1, 1);
                }
            }
            state.changed();
            completed++;
        }
        return result(completed == 0 ? "invalid_recipe" : "crafted");
    }

    private static TerminalIngredientSupply.Result result(String message) {
        return new TerminalIngredientSupply.Result(message, ObjectList.of());
    }

    private static void rememberInputs(TerminalPlayerState state) {
        for (int index = 0; index < state.work().inputs.getContainerSize(); index++) {
            ItemStack stack = state.work().inputs.getItem(index);
            if (!stack.isEmpty()) state.work().patternInputs.set(index, new GenericStack(AEItemKey.of(stack), 1));
        }
    }

    public static ObjectList<TerminalIngredientSupply.Target> materialTargets(TerminalPlayerState state) {
        var targets = new ObjectArrayList<TerminalIngredientSupply.Target>();
        for (int index = 0; index < state.work().inputs.getContainerSize(); index++) {
            ItemStack ingredient = input(state.work(), index, true);
            targets.add(new TerminalIngredientSupply.Target(new Slot(state.work().inputs, index, 0, 0),
                    AEItemKey.of(ingredient), ingredient.isEmpty() ? 0 : 1));
        }
        return targets;
    }

    private static void consumeCrafting(ServerPlayer player, TerminalWorkspaceState work, ItemStack product, boolean shift) {
        var positioned = craftingInput(work, false);
        var input = positioned.input();
        var recipe = TerminalCraftingRecipes.resolve(player, work, false).selected(work).orElseThrow();
        NonNullList<ItemStack> remainders;
        CommonHooks.setCraftingPlayer(player);
        try {
            remainders = recipe.value().getRemainingItems(input);
        } finally {
            CommonHooks.setCraftingPlayer(null);
        }
        var eventInventory = new SimpleContainer(9);
        ObjectList<ItemStack> overflow = new ObjectArrayList<>();
        ObjectList<ItemStack> relevant = new ObjectArrayList<>();
        for (int index = 0; index < 9; index++) {
            ItemStack item = work.inputs.getItem(index).copy();
            eventInventory.setItem(index, item);
            relevant.add(item.copy());
        }
        for (int y = 0; y < input.height(); y++) {
            for (int x = 0; x < input.width(); x++) {
                int index = positioned.left() + x + (positioned.top() + y) * 3;
                work.inputs.removeItem(index, 1);
                ItemStack remainder = remainders.get(x + y * input.width()).copy();
                if (remainder.isEmpty()) continue;
                ItemStack existing = work.inputs.getItem(index);
                if (existing.isEmpty()) work.inputs.setItem(index, remainder);
                else if (ItemStack.isSameItemSameComponents(existing, remainder) && existing.getCount() + remainder.getCount() <= existing.getMaxStackSize()) {
                    existing.grow(remainder.getCount());
                    work.inputs.setChanged();
                } else overflow.add(remainder);
            }
        }
        // Reserve the preflighted output before a container remainder can fill the player's last free slot.
        deliver(player, product, shift);
        for (ItemStack remainder : overflow) player.getInventory().placeItemBackInInventory(remainder);
        product.onCraftedBy(player.level(), player, product.getCount());
        player.triggerRecipeCrafted(recipe, relevant);
        if (!recipe.value().isSpecial()) player.awardRecipes(ObjectList.of(recipe));
        EventHooks.firePlayerCraftingEvent(player, product, eventInventory);
    }

    public static boolean fits(ServerPlayer player, ItemStack product, boolean shift) {
        if (shift) return TerminalNetworkStorage.inventoryCapacity(player, product) >= product.getCount();
        ItemStack cursor = player.containerMenu.getCarried();
        return cursor.isEmpty() || ItemStack.isSameItemSameComponents(cursor, product) && cursor.getCount() + product.getCount() <= cursor.getMaxStackSize();
    }

    private static void deliver(ServerPlayer player, ItemStack product, boolean shift) {
        if (shift) {
            ItemStack output = product.copy();
            player.getInventory().add(output);
            if (!output.isEmpty()) player.getInventory().placeItemBackInInventory(output);
        } else {
            ItemStack cursor = player.containerMenu.getCarried();
            if (cursor.isEmpty()) player.containerMenu.setCarried(product.copy());
            else cursor.grow(product.getCount());
        }
    }

    public static CraftingInput.Positioned craftingInput(TerminalWorkspaceState work, boolean ghosts) {
        ObjectList<ItemStack> items = new ObjectArrayList<>();
        for (int index = 0; index < 9; index++) items.add(input(work, index, ghosts));
        return CraftingInput.ofPositioned(3, 3, items);
    }

    public static SmithingRecipeInput smithingInput(TerminalWorkspaceState work, boolean ghosts) {
        return new SmithingRecipeInput(input(work, 0, ghosts), input(work, 1, ghosts), input(work, 2, ghosts));
    }

    public static ItemStack input(TerminalWorkspaceState work, int index, boolean ghosts) {
        ItemStack physical = work.inputs.getItem(index);
        if (!ghosts || !physical.isEmpty()) return physical;
        var ghost = work.patternInputs.get(index);
        return ghost != null && ghost.what() instanceof AEItemKey key ? key.toStack(1) : ItemStack.EMPTY;
    }

    public static ObjectList<RecipeHolder<StonecutterRecipe>> stonecuttingRecipes(ServerPlayer player, TerminalWorkspaceState work, boolean ghosts) {
        var input = new SingleRecipeInput(input(work, 0, ghosts));
        ObjectList<RecipeHolder<StonecutterRecipe>> recipes = new ObjectArrayList<>(player.serverLevel().getRecipeManager().getRecipesFor(RecipeType.STONECUTTING, input, player.level()));
        recipes.sort(Comparator.comparing(value -> value.id().toString()));
        return recipes;
    }

    public static int upgrades(ServerPlayer player) {
        int upgrades = 0;
        for (DigitalAnvilUpgrade upgrade : DigitalAnvilUpgrade.values()) {
            if (ResearchAccess.hasPlayerUpgrade(player, PlayerTerminalResearch.anvilUpgradeId(upgrade)))
                upgrades |= upgrade.mask();
        }
        return upgrades;
    }

    public static DigitalAnvilOperation anvil(ServerPlayer player, TerminalWorkspaceState work) {
        return DigitalAnvilCalculation.calculate(work.inputs.getItem(0), work.inputs.getItem(1), work.name(), upgrades(player), work.boost());
    }

    public static void snapshot(ServerPlayer player, TerminalPlayerState state, CompoundTag tag) {
        if (state.workspace() == TerminalWorkspace.CRAFTING)
            TerminalCraftingRecipes.resolve(player, state.work(), true).snapshot(player, state.work(), tag);
        else tag.put("result", preview(player, state).saveOptional(player.registryAccess()));
        tag.putInt("recipe_count", state.workspace() == TerminalWorkspace.STONECUTTING ? stonecuttingRecipes(player, state.work(), true).size() : 0);
        tag.putInt("anvil_upgrades", upgrades(player));
        if (state.workspace() == TerminalWorkspace.ANVIL) {
            var cost = anvil(player, state.work()).cost();
            tag.putLong("anvil_energy", cost.energy());
            tag.putLong("anvil_levels", cost.experienceLevels());
        }
    }
}
