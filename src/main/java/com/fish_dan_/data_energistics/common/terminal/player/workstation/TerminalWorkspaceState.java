package com.fish_dan_.data_energistics.common.terminal.player.workstation;

import com.fish_dan_.data_energistics.common.terminal.player.encoding.TerminalPatternContext;
import com.fish_dan_.data_energistics.common.terminal.player.storage.TerminalGhostInventory;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;

/**
 * Owns the separate physical and configuration inventories for one player's workspace.
 */
public final class TerminalWorkspaceState {

    public final SimpleContainer inputs;
    public final SimpleContainer patterns = new SimpleContainer(1);
    public final TerminalGhostInventory patternInputs;
    public final TerminalGhostInventory patternOutputs;
    private final Runnable changed;
    private boolean substitutes;
    private boolean fluidSubstitutes;
    private int recipeIndex;
    private String name = "";
    private int boost;
    private TerminalPatternContext context = TerminalPatternContext.EMPTY;
    private boolean recordRecipeType = true;

    public TerminalPatternContext context() {
        return context;
    }

    public void context(TerminalPatternContext value) {
        context = value;
        changed.run();
    }

    public boolean recordRecipeType() {
        return recordRecipeType;
    }

    public void recordRecipeType(boolean value) {
        recordRecipeType = value;
        changed.run();
    }

    public TerminalWorkspaceState(TerminalWorkspace workspace, Runnable changed) {
        this.changed = changed;
        inputs = new SimpleContainer(workspace.inputSlots());
        patternInputs = new TerminalGhostInventory(workspace.patternInputSlots(), changed);
        patternOutputs = new TerminalGhostInventory(workspace.patternOutputSlots(), changed);
        inputs.addListener(inventory -> changed.run());
        patterns.addListener(inventory -> changed.run());
    }

    public boolean substitutes() {
        return substitutes;
    }

    public boolean fluidSubstitutes() {
        return fluidSubstitutes;
    }

    public int recipeIndex() {
        return recipeIndex;
    }

    public String name() {
        return name;
    }

    public int boost() {
        return boost;
    }

    public void substitutes(boolean value) {
        substitutes = value;
        changed.run();
    }

    public void fluidSubstitutes(boolean value) {
        fluidSubstitutes = value;
        changed.run();
    }

    public void recipeIndex(int value) {
        if (value < 0) throw new IllegalArgumentException("Negative recipe index");
        recipeIndex = value;
        changed.run();
    }

    public void name(String value) {
        if (value.length() > 32767)
            throw new IllegalArgumentException("Anvil item name exceeds the saved string limit");
        name = value;
        changed.run();
    }

    public void boost(int value) {
        if (value < 0 || value > 254) throw new IllegalArgumentException("Invalid anvil level boost");
        boost = value;
        changed.run();
    }

    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.put("items", saveSlots(inputs, registries));
        tag.put("patterns", saveSlots(patterns, registries));
        tag.put("ghost_inputs", patternInputs.save(registries));
        tag.put("ghost_outputs", patternOutputs.save(registries));
        tag.putBoolean("substitutes", substitutes);
        tag.putBoolean("fluid_substitutes", fluidSubstitutes);
        tag.putInt("recipe", recipeIndex);
        tag.putString("name", name);
        tag.putInt("boost", boost);
        tag.put("context", context.save());
        tag.putBoolean("record_recipe_type", recordRecipeType);
        return tag;
    }

    public void load(CompoundTag tag, HolderLookup.Provider registries) {
        loadSlots(inputs, tag.getList("items", Tag.TAG_COMPOUND), registries);
        loadSlots(patterns, tag.getList("patterns", Tag.TAG_COMPOUND), registries);
        patternInputs.load(tag.getList("ghost_inputs", Tag.TAG_COMPOUND), registries);
        patternOutputs.load(tag.getList("ghost_outputs", Tag.TAG_COMPOUND), registries);
        substitutes = tag.getBoolean("substitutes");
        fluidSubstitutes = tag.getBoolean("fluid_substitutes");
        recipeIndex = tag.getInt("recipe");
        name = tag.getString("name");
        boost = tag.getInt("boost");
        context = TerminalPatternContext.load(tag.getCompound("context"));
        recordRecipeType = !tag.contains("record_recipe_type") || tag.getBoolean("record_recipe_type");
        if (recipeIndex < 0 || name.length() > 32767 || boost < 0 || boost > 254) {
            throw new IllegalArgumentException("Invalid saved workspace settings");
        }
    }

    private static ListTag saveSlots(SimpleContainer inventory, HolderLookup.Provider registries) {
        ListTag slots = new ListTag();
        for (int index = 0; index < inventory.getContainerSize(); index++)
            slots.add(inventory.getItem(index).saveOptional(registries));
        return slots;
    }

    private static void loadSlots(SimpleContainer inventory, ListTag slots, HolderLookup.Provider registries) {
        if (slots.size() != inventory.getContainerSize())
            throw new IllegalArgumentException("Unexpected physical workspace inventory size");
        for (int index = 0; index < slots.size(); index++) {
            CompoundTag raw = slots.getCompound(index);
            ItemStack stack = raw.isEmpty() ? ItemStack.EMPTY : ItemStack.parse(registries, raw).orElseThrow(() -> new IllegalArgumentException("Invalid saved workspace item"));
            if (stack.getCount() > stack.getMaxStackSize())
                throw new IllegalArgumentException("Oversized saved workspace item");
            inventory.setItem(index, stack);
        }
    }
}
