package com.fish_dan_.data_energistics.common.terminal.player.encoding;

import com.fish_dan_.data_energistics.common.terminal.player.connection.TerminalConnection;
import com.fish_dan_.data_energistics.common.terminal.player.persistence.TerminalPlayerState;
import com.fish_dan_.data_energistics.common.terminal.player.storage.TerminalGhostInventory;
import com.fish_dan_.data_energistics.common.terminal.player.storage.TerminalNetworkStorage;
import com.fish_dan_.data_energistics.common.terminal.player.workstation.TerminalWorkspace;
import com.fish_dan_.data_energistics.common.terminal.player.workstation.TerminalWorkspaceState;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.StorageHelper;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;

import it.unimi.dsi.fastutil.objects.Object2LongLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;

/** Validates a complete viewer transfer before replacing any of the active page's ghost configuration. */
@NullMarked
public final class TerminalRecipeTransfer {

    private TerminalRecipeTransfer() {}

    /** Sends marked item keys to inventory when requested; the network destination only removes their marks. */
    public static String clear(ServerPlayer player, TerminalPlayerState state, @Nullable TerminalConnection connection, boolean toNetwork) {
        if (!state.workspace().supportsEncoding()) throw new IllegalArgumentException("The active page is not a recipe encoder");
        TerminalWorkspace page = state.workspace();
        TerminalWorkspaceState work = state.work(page);
        if (toNetwork) {
            CompoundTag data = new CompoundTag();
            data.putString("workspace", page.id());
            return apply(player, state, data, connection, true);
        }

        ObjectList<Extraction> extractions = new ObjectArrayList<>();
        String failure = planMarkedInputs(player, page, work, connection, extractions);
        if (!failure.isEmpty()) return failure;

        CompoundTag data = new CompoundTag();
        data.putString("workspace", page.id());
        failure = apply(player, state, data, connection, false);
        if (!failure.isEmpty()) return failure;
        if (!extractions.isEmpty()) {
            if (connection == null) throw new IllegalStateException("Planned ME extraction lost its connection");
            extractMarkedInputs(player, connection, extractions);
        }
        return "";
    }

    private static String planMarkedInputs(ServerPlayer player, TerminalWorkspace page, TerminalWorkspaceState work,
                                           @Nullable TerminalConnection connection, ObjectList<Extraction> extractions) {
        Object2LongMap<AEItemKey> requested = new Object2LongLinkedOpenHashMap<>();
        for (int index = 0; index < work.patternInputs.size(); index++) {
            GenericStack stack = work.patternInputs.get(index);
            if (stack == null || !(stack.what() instanceof AEItemKey key)) continue;
            long previous = requested.getLong(key);
            if (Long.MAX_VALUE - previous < stack.amount()) return "amount_overflow";
            requested.put(key, previous + stack.amount());
        }
        if (requested.isEmpty()) return "";
        if (connection == null) return "offline";

        SimpleContainer capacity = new SimpleContainer(player.getInventory().items.size());
        for (int index = 0; index < capacity.getContainerSize(); index++)
            capacity.setItem(index, player.getInventory().items.get(index).copy());
        if (page != TerminalWorkspace.PROCESSING) {
            for (int index = 0; index < work.inputs.getContainerSize(); index++) {
                ItemStack input = work.inputs.getItem(index);
                if (!input.isEmpty() && !capacity.addItem(input.copy()).isEmpty()) return "inventory_full";
            }
        }

        for (Object2LongMap.Entry<AEItemKey> entry : requested.object2LongEntrySet()) {
            long available = connection.storage().extract(entry.getKey(), entry.getLongValue(), Actionable.SIMULATE,
                    connection.source());
            TerminalNetworkStorage.checkAmount(available, entry.getLongValue());
            if (available == 0) continue;
            if (!simulateInsert(capacity, entry.getKey(), available)) return "inventory_full";
            extractions.add(new Extraction(entry.getKey(), available));
        }
        return "";
    }

    private static boolean simulateInsert(SimpleContainer inventory, AEItemKey key, long amount) {
        long remaining = amount;
        while (remaining > 0) {
            int count = (int) Math.min(remaining, key.getMaxStackSize());
            if (!inventory.addItem(key.toStack(count)).isEmpty()) return false;
            remaining -= count;
        }
        return true;
    }

    private static void extractMarkedInputs(ServerPlayer player, TerminalConnection connection,
                                            ObjectList<Extraction> extractions) {
        for (Extraction extraction : extractions) {
            long amount = StorageHelper.poweredExtraction(connection.grid().getEnergyService(), connection.storage(),
                    extraction.key(), extraction.amount(), connection.source());
            TerminalNetworkStorage.checkAmount(amount, extraction.amount());
            long remaining = amount;
            while (remaining > 0) {
                int count = (int) Math.min(remaining, extraction.key().getMaxStackSize());
                ItemStack stack = extraction.key().toStack(count);
                if (!player.getInventory().add(stack) && !stack.isEmpty())
                    throw new IllegalStateException("Reserved terminal extraction capacity was lost");
                remaining -= count;
            }
        }
    }

    private record Extraction(AEItemKey key, long amount) {}

    public static String apply(ServerPlayer player, TerminalPlayerState state, CompoundTag data,
                               @Nullable TerminalConnection connection, boolean toNetwork) {
        TerminalWorkspace page = TerminalWorkspace.byId(data.getString("workspace"));
        if (!page.supportsEncoding()) throw new IllegalArgumentException("Recipe transfer requires an encoder");
        TerminalWorkspaceState work = state.work(page);
        TerminalGhostInventory inputs = new TerminalGhostInventory(page.patternInputSlots(), () -> {});
        TerminalGhostInventory outputs = new TerminalGhostInventory(page.patternOutputSlots(), () -> {});
        inputs.load(data.getList("inputs", Tag.TAG_COMPOUND), player.registryAccess());
        outputs.load(data.getList("outputs", Tag.TAG_COMPOUND), player.registryAccess());
        TerminalPatternContext context = TerminalPatternContext.load(data.getCompound("context"));
        int selectedRecipe = 0;
        if (page != TerminalWorkspace.PROCESSING) {
            for (int index = 0; index < inputs.size(); index++) {
                GenericStack stack = inputs.get(index);
                if (stack != null) {
                    if (!(stack.what() instanceof AEItemKey)) throw new IllegalArgumentException("Vanilla encoders require item ingredients");
                    inputs.set(index, new GenericStack(stack.what(), 1));
                }
            }
        }
        if (page == TerminalWorkspace.STONECUTTING && context.recipeId() != null) {
            GenericStack input = inputs.get(0);
            if (input == null) throw new IllegalArgumentException("Stonecutting transfer has no input");
            var recipes = new ObjectArrayList<>(player.serverLevel().getRecipeManager().getRecipesFor(RecipeType.STONECUTTING,
                    new SingleRecipeInput(((AEItemKey) input.what()).toStack(1)), player.level()));
            recipes.sort(Comparator.comparing(recipe -> recipe.id().toString()));
            selectedRecipe = -1;
            for (int index = 0; index < recipes.size(); index++) if (recipes.get(index).id().equals(context.recipeId())) selectedRecipe = index;
            if (selectedRecipe < 0) throw new IllegalArgumentException("Transferred stonecutting recipe does not match its input");
        }
        if (page != TerminalWorkspace.PROCESSING && toNetwork && !work.inputs.isEmpty()) {
            if (connection == null) return "offline";
            if (!TerminalNetworkStorage.insertInventory(connection, work.inputs)) return "network_full";
        }
        if (page != TerminalWorkspace.PROCESSING && !work.inputs.isEmpty()) {
            SimpleContainer capacity = new SimpleContainer(player.getInventory().items.size());
            for (int index = 0; index < capacity.getContainerSize(); index++)
                capacity.setItem(index, player.getInventory().items.get(index).copy());
            for (int index = 0; index < work.inputs.getContainerSize(); index++)
                if (!capacity.addItem(work.inputs.getItem(index).copy()).isEmpty()) return "inventory_full";
            for (int index = 0; index < work.inputs.getContainerSize(); index++) {
                ItemStack returned = work.inputs.removeItemNoUpdate(index);
                player.getInventory().add(returned);
                if (!returned.isEmpty()) throw new IllegalStateException("Recipe transfer lost reserved return capacity");
            }
        }
        work.patternInputs.copyFrom(inputs);
        work.patternOutputs.copyFrom(outputs);
        work.matching.clear();
        work.context(context);
        work.recipeIndex(selectedRecipe);
        state.workspace(page);
        return "";
    }
}
