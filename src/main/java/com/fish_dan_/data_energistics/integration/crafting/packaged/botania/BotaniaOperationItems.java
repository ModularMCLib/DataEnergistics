package com.fish_dan_.data_energistics.integration.crafting.packaged.botania;

import com.fish_dan_.data_energistics.api.crafting.packaged.PackagedMachineOperation;
import com.fish_dan_.data_energistics.common.crafting.packaged.execution.PackagedEntityCapture;
import com.fish_dan_.data_energistics.common.crafting.packaged.recipe.PackagedOutputMatching;

import appeng.api.stacks.AEItemKey;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;

import java.util.UUID;

final class BotaniaOperationItems {

    private BotaniaOperationItems() {}

    static ObjectList<ItemStack> read(PackagedMachineOperation operation, String name) {
        var list = operation.progress().getList(name, Tag.TAG_COMPOUND);
        var stacks = new ObjectArrayList<ItemStack>();
        for (int index = 0; index < list.size(); index++) {
            stacks.add(ItemStack.parse(operation.level().registryAccess(), list.getCompound(index))
                    .orElseThrow(() -> new IllegalArgumentException("Invalid Botania persisted " + name)));
        }
        return stacks;
    }

    static ItemEntity spawn(PackagedMachineOperation operation, ItemStack stack, Vec3 position) {
        var item = new ItemEntity(operation.level(), position.x, position.y, position.z, stack.copy(), 0, 0, 0);
        item.setNoGravity(true);
        item.setUnlimitedLifetime();
        item.setPickUpDelay(32767);
        PackagedEntityCapture.run(operation.level(), operation.id(), () -> {
            if (!operation.level().addFreshEntity(item)) throw new IllegalStateException("Botania input entity was rejected by the world");
        });
        operation.delivered(AEItemKey.of(stack), stack.getCount());
        var ids = operation.progress().getList("entities", Tag.TAG_COMPOUND);
        var encoded = new CompoundTag();
        encoded.putUUID("id", item.getUUID());
        ids.add(encoded);
        operation.progress().put("entities", ids);
        operation.changed();
        return item;
    }

    static boolean collect(PackagedMachineOperation operation) {
        var inputIds = new ObjectOpenHashSet<UUID>();
        var ids = operation.progress().getList("entities", Tag.TAG_COMPOUND);
        for (int index = 0; index < ids.size(); index++) inputIds.add(ids.getCompound(index).getUUID("id"));
        var expected = read(operation, "outputs");
        int batch = operation.progress().contains("batch") ? operation.progress().getInt("batch") : 1;
        for (int index = 0; index < expected.size(); index++) {
            ItemStack stack = expected.get(index);
            expected.set(index, stack.copyWithCount(Math.multiplyExact(stack.getCount(), batch)));
        }
        var actual = new ObjectArrayList<ItemStack>();
        var harvested = operation.progress().getList("recovered", Tag.TAG_COMPOUND);
        for (int index = 0; index < harvested.size(); index++) {
            var stack = ItemStack.parse(operation.level().registryAccess(), harvested.getCompound(index)).orElseThrow();
            actual.add(stack);
        }
        if (PackagedOutputMatching.matches(operation, expected, actual)) return true;
        var drops = operation.level().getEntitiesOfClass(ItemEntity.class, new AABB(operation.position()).inflate(8),
                entity -> PackagedEntityCapture.ownedBy(entity, operation.id()) && !inputIds.contains(entity.getUUID()));
        for (var drop : drops) actual.add(drop.getItem());
        if (!PackagedOutputMatching.acceptsPartial(operation, expected, actual)) throw new IllegalStateException("Unexpected owned Botania output");
        boolean complete = PackagedOutputMatching.matches(operation, expected, actual);
        for (var drop : drops) {
            ItemStack stack = drop.getItem().copy();
            drop.discard();
            operation.returned(AEItemKey.of(stack), stack.getCount());
            harvested.add(stack.save(operation.level().registryAccess()));
        }
        if (!drops.isEmpty()) {
            operation.progress().put("recovered", harvested);
            operation.changed();
        }
        return complete;
    }

    static void recoveredInputContainer(PackagedMachineOperation operation, ItemEntity entity, ItemStack expected) {
        if (!PackagedOutputMatching.matches(operation, expected, entity.getItem())) throw new IllegalStateException("Unexpected Botania fluid container remainder");
        ItemStack actual = entity.getItem().copy();
        entity.discard();
        operation.returned(AEItemKey.of(actual), actual.getCount());
        ListTag recovered = operation.progress().getList("recovered", Tag.TAG_COMPOUND);
        recovered.add(actual.save(operation.level().registryAccess()));
        operation.progress().put("recovered", recovered);
        operation.changed();
    }
}
