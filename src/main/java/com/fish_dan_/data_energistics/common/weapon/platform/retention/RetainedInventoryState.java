package com.fish_dan_.data_energistics.common.weapon.platform.retention;

import net.minecraft.world.item.ItemStack;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectLists;

/** The death decision survives logout; extra menu items wait here until the respawned inventory has room. */
public record RetainedInventoryState(boolean retained, ObjectList<ItemStack> pending) {

    public static final RetainedInventoryState EMPTY = new RetainedInventoryState(false, ObjectList.of());
    private static final Codec<ObjectList<ItemStack>> ITEMS_CODEC = ItemStack.CODEC.listOf().xmap(ObjectArrayList::new, values -> values);
    public static final Codec<RetainedInventoryState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.fieldOf("retained").forGetter(RetainedInventoryState::retained),
            ITEMS_CODEC.fieldOf("pending").forGetter(RetainedInventoryState::pending)).apply(instance, RetainedInventoryState::new));

    public RetainedInventoryState {
        pending = ObjectLists.unmodifiable(new ObjectArrayList<>(pending));
    }
}
