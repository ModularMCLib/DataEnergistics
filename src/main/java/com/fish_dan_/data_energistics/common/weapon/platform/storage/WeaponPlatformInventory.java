package com.fish_dan_.data_energistics.common.weapon.platform.storage;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.data.DEDataComponents;
import com.fish_dan_.data_energistics.common.data.DEItems;
import com.fish_dan_.data_energistics.common.research.item.ItemResearchUpgrades;
import com.fish_dan_.data_energistics.common.weapon.platform.model.BiochipResearch;
import com.fish_dan_.data_energistics.common.weapon.platform.model.GunForm;

import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.neoforged.neoforge.items.ItemStackHandler;

/** One stack-owned inventory. Construct a fresh view after external component synchronization. */
public final class WeaponPlatformInventory extends ItemStackHandler {

    public static final int CHIP_SLOT = 8;
    public static final TagKey<Item> AERIAL_WEAPONS = TagKey.create(Registries.ITEM, Data_Energistics.id("platform/detached_aerial_weapons"));
    public static final TagKey<Item> AERIAL_HEALING = TagKey.create(Registries.ITEM, Data_Energistics.id("platform/aerial_healing"));
    private final ItemStack platform;

    public WeaponPlatformInventory(ItemStack platform) {
        super(9);
        this.platform = platform;
        var contents = platform.getOrDefault(DEDataComponents.PLATFORM_CONTENTS, ItemContainerContents.EMPTY);
        if (contents.getSlots() > 9) throw new IllegalArgumentException("Invalid weapon platform inventory size");
        contents.copyInto(stacks);
    }

    public int capacity() {
        return BiochipResearch.capacity(stacks.get(CHIP_SLOT));
    }

    public boolean permits(ItemStack gun, ItemStack chip) {
        if (!gun.is(DEItems.DIGITAL_FLOATING_GUN)) return false;
        GunForm form = GunForm.of(gun);
        return gun.is(form == GunForm.MEDICAL ? AERIAL_HEALING : AERIAL_WEAPONS) && (form == GunForm.NORMAL || ItemResearchUpgrades.of(chip).ids().contains(BiochipResearch.PLATFORM));
    }

    public boolean canReplaceChip(ItemStack chip) {
        int capacity = BiochipResearch.capacity(chip);
        for (int slot = 0; slot < CHIP_SLOT; slot++) {
            ItemStack gun = stacks.get(slot);
            if (!gun.isEmpty() && (slot >= capacity || !permits(gun, chip))) return false;
        }
        return true;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        if (slot == CHIP_SLOT) return stack.is(DEItems.COMPLEXIFIED_BIOCHIPS) && canReplaceChip(stack);
        return slot < capacity() && permits(stack, stacks.get(CHIP_SLOT));
    }

    @Override
    public int getSlotLimit(int slot) {
        return 1;
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (slot == CHIP_SLOT && !canReplaceChip(ItemStack.EMPTY)) return ItemStack.EMPTY;
        return super.extractItem(slot, amount, simulate);
    }

    @Override
    protected void onContentsChanged(int slot) {
        platform.set(DEDataComponents.PLATFORM_CONTENTS, ItemContainerContents.fromItems(stacks));
    }
}
