package com.fish_dan_.data_energistics.integration.crafting.packaged.arsnouveau;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.api.crafting.packaged.PackagedMachineAdapter;
import com.fish_dan_.data_energistics.api.crafting.packaged.PackagedMachineOperation;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import com.hollingsworth.arsnouveau.common.block.ArcaneCore;
import com.hollingsworth.arsnouveau.common.block.tile.ArcanePedestalTile;
import com.hollingsworth.arsnouveau.common.block.tile.EnchantingApparatusTile;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.jspecify.annotations.Nullable;

import java.math.BigInteger;
import java.util.List;

/** Owns only the selected empty pedestals and the center; the actual Ars tile produces and consumes everything. */
final class ArsPedestalAdapter implements PackagedMachineAdapter {

    private final ArsMachineKind kind;

    ArsPedestalAdapter(ArsMachineKind kind) {
        this.kind = kind;
    }

    @Override
    public ResourceLocation id() {
        return Data_Energistics.id("ars_nouveau_" + this.kind.path);
    }

    @Override
    public ObjectSet<ResourceLocation> recipeTypes() {
        return this.kind.categories;
    }

    @Override
    public boolean recognizes(ServerLevel level, BlockPos position) {
        return level.isLoaded(position) && this.kind.accepts(level.getBlockEntity(position));
    }

    @Override
    public @Nullable CompoundTag prepare(ServerLevel level, BlockPos position, Direction face,
                                         ResourceLocation recipeId, IPatternDetails pattern, KeyCounter[] inputs) {
        Layout layout = layout(level, position);
        if (layout == null || !layout.centerEmpty()) return null;
        var availablePedestals = new ObjectArrayList<ArcanePedestalTile>();
        var installed = new ObjectArrayList<ItemStack>();
        var catalysts = new ListTag();
        for (var pedestal : layout.pedestals()) {
            if (pedestal.isEmpty()) availablePedestals.add(pedestal);
            else {
                if (!this.kind.retained(pedestal.getStack())) return null;
                installed.add(pedestal.getStack().copy());
                var catalyst = new CompoundTag();
                catalyst.putLong("position", pedestal.getBlockPos().asLong());
                catalyst.put("input", pedestal.getStack().save(level.registryAccess()));
                catalysts.add(catalyst);
            }
        }
        var holder = level.getRecipeManager().byKey(recipeId);
        if (holder.isEmpty() || !this.kind.accepts(holder.get().value())) return null;
        // Trying possible center positions is sufficient: Ars matches the remaining pedestal items without order.
        int remainingAttempts = 4096;
        for (int size = 1; size <= availablePedestals.size() + 1; size++) {
            ObjectList<ItemStack> units = ArsInputAssignment.units(inputs, size);
            if (units == null) continue;
            long cycles = ArsInputAssignment.cycles(inputs, size);
            for (int centerIndex = 0; centerIndex < units.size(); centerIndex++) {
                if (--remainingAttempts < 0) return null;
                ItemStack center = units.get(centerIndex);
                var pedestalInputs = new ObjectArrayList<>(units);
                pedestalInputs.remove(centerIndex);
                var recipePedestals = new ObjectArrayList<>(installed);
                recipePedestals.addAll(pedestalInputs);
                ItemStack result = this.kind.validate(level, layout.tile(), recipeId, holder.get().value(), center, recipePedestals);
                if (result == null || result.isEmpty()) continue;
                var remaining = new ObjectArrayList<ItemStack>();
                for (ItemStack stack : pedestalInputs) remaining.add(this.kind.remainder(stack));
                if (!ArsInputAssignment.outputsMatch(pattern, result, remaining, cycles)) continue;
                var progress = new CompoundTag();
                progress.put("center", center.save(level.registryAccess()));
                progress.put("result", result.save(level.registryAccess()));
                progress.putLong("cycles", cycles);
                var slots = new ListTag();
                for (int index = 0; index < pedestalInputs.size(); index++) {
                    var slot = new CompoundTag();
                    slot.putLong("position", availablePedestals.get(index).getBlockPos().asLong());
                    slot.put("input", pedestalInputs.get(index).save(level.registryAccess()));
                    slot.put("remaining", remaining.get(index).saveOptional(level.registryAccess()));
                    slots.add(slot);
                }
                progress.put("pedestals", slots);
                progress.put("catalysts", catalysts);
                return progress;
            }
        }
        return null;
    }

    @Override
    public ObjectList<BlockPos> occupiedPositions(ServerLevel level, BlockPos position, CompoundTag preparation) {
        var positions = new ObjectArrayList<BlockPos>();
        positions.add(position);
        for (var name : ObjectList.of("pedestals", "catalysts")) {
            var slots = preparation.getList(name, Tag.TAG_COMPOUND);
            for (int index = 0; index < slots.size(); index++) positions.add(BlockPos.of(slots.getCompound(index).getLong("position")));
        }
        return positions;
    }

    @Override
    public boolean advance(PackagedMachineOperation operation) {
        Layout layout = layout(operation.level(), operation.position());
        if (layout == null) return false;
        CompoundTag progress = operation.progress();
        if (progress.getLong("cycles") <= 0) throw new IllegalArgumentException("Invalid Ars cycle count");
        ListTag slots = progress.getList("pedestals", Tag.TAG_COMPOUND);
        if (slots.size() > layout.pedestals().size()) return false;
        ObjectList<ArcanePedestalTile> selected = new ObjectArrayList<>();
        for (int index = 0; index < slots.size(); index++) {
            BlockPos target = BlockPos.of(slots.getCompound(index).getLong("position"));
            var found = layout.pedestals().stream().filter(pedestal -> pedestal.getBlockPos().equals(target)).findFirst();
            if (found.isEmpty() || selected.contains(found.get())) return false;
            selected.add(found.get());
        }
        var installed = new ObjectArrayList<ItemStack>();
        var installedTiles = new ObjectArrayList<ArcanePedestalTile>();
        var catalysts = progress.getList("catalysts", Tag.TAG_COMPOUND);
        for (int index = 0; index < catalysts.size(); index++) {
            var slot = catalysts.getCompound(index);
            BlockPos position = BlockPos.of(slot.getLong("position"));
            var found = layout.pedestals().stream().filter(tile -> tile.getBlockPos().equals(position)).findFirst();
            if (found.isEmpty() || selected.contains(found.get()) || installedTiles.contains(found.get())) return false;
            ItemStack expected = read(operation, slot, "input");
            if (!ItemStack.matches(expected, found.get().getStack()) || !this.kind.retained(expected)) return false;
            installedTiles.add(found.get());
            installed.add(expected);
        }
        for (ArcanePedestalTile tile : layout.pedestals()) {
            if (!selected.contains(tile) && !installedTiles.contains(tile) && !tile.isEmpty()) return false;
        }
        ItemStack result = read(operation, progress, "result");
        if (progress.getBoolean("delivered")) return collect(operation, layout, selected, slots, result);
        if (!layout.centerEmpty() || selected.stream().anyMatch(tile -> !tile.isEmpty())) return false;
        ItemStack center = read(operation, progress, "center");
        var pedestalInputs = new ObjectArrayList<ItemStack>();
        var required = new KeyCounter();
        required.add(AEItemKey.of(center), center.getCount());
        for (int index = 0; index < slots.size(); index++) {
            ItemStack input = read(operation, slots.getCompound(index), "input");
            if (input.getCount() != 1) throw new IllegalArgumentException("Invalid persisted Ars pedestal quantity");
            pedestalInputs.add(input);
            required.add(AEItemKey.of(input), input.getCount());
        }
        if (center.getCount() != 1) throw new IllegalArgumentException("Invalid persisted Ars center quantity");
        for (var entry : required) {
            if (operation.available(entry.getKey()).compareTo(BigInteger.valueOf(entry.getLongValue())) < 0) {
                throw new IllegalStateException("Ars dispatch exceeds owned ingredients");
            }
        }
        var holder = operation.level().getRecipeManager().byKey(operation.recipeId());
        var recipePedestals = new ObjectArrayList<>(installed);
        recipePedestals.addAll(pedestalInputs);
        ItemStack currentResult = holder.isEmpty() ? null : this.kind.validate(operation.level(), layout.tile(),
                operation.recipeId(), holder.get().value(), center, recipePedestals);
        if (currentResult == null) return false;
        if (!ItemStack.matches(result, currentResult)) throw new IllegalStateException("Ars recipe output changed after preparation");
        for (int index = 0; index < slots.size(); index++) {
            ItemStack expected = ItemStack.parseOptional(operation.level().registryAccess(), slots.getCompound(index).getCompound("remaining"));
            if (!ItemStack.matches(expected, this.kind.remainder(pedestalInputs.get(index)))) {
                throw new IllegalStateException("Ars pedestal remainder changed after preparation");
            }
        }
        for (int index = 0; index < slots.size(); index++) {
            ItemStack input = pedestalInputs.get(index);
            selected.get(index).setItem(0, input.copy());
            operation.delivered(AEItemKey.of(input), input.getCount());
        }
        // Apparatus.setItem starts its own source-consuming craft; imbuement starts naturally on its next tick.
        layout.inventory().setItem(0, center.copy());
        operation.delivered(AEItemKey.of(center), center.getCount());
        if (layout.tile() instanceof EnchantingApparatusTile apparatus && !apparatus.isCrafting) {
            throw new IllegalStateException("Ars apparatus refused the validated craft");
        }
        progress.putBoolean("delivered", true);
        operation.changed();
        return true;
    }

    private static boolean collect(PackagedMachineOperation operation, Layout layout,
                                   ObjectList<ArcanePedestalTile> selected, ListTag slots, ItemStack result) {
        if (layout.tile() instanceof EnchantingApparatusTile apparatus && apparatus.isCrafting) return false;
        ItemStack actual = layout.inventory().getItem(0);
        if (!ItemStack.matches(actual, result)) return false;
        for (int index = 0; index < selected.size(); index++) {
            ItemStack expected = ItemStack.parseOptional(operation.level().registryAccess(), slots.getCompound(index).getCompound("remaining"));
            if (!ItemStack.matches(expected, selected.get(index).getStack())) {
                throw new IllegalStateException("Ars pedestal contents changed outside this operation");
            }
        }
        harvest(operation, layout.inventory());
        for (ArcanePedestalTile pedestal : selected) harvest(operation, pedestal);
        long cycles = operation.progress().getLong("cycles") - 1;
        operation.progress().putLong("cycles", cycles);
        operation.progress().putBoolean("delivered", false);
        operation.changed();
        if (cycles == 0) operation.complete();
        return true;
    }

    private static void harvest(PackagedMachineOperation operation, Container container) {
        ItemStack current = container.getItem(0);
        if (current.isEmpty()) return;
        int amount = current.getCount();
        ItemStack extracted = container.removeItem(0, amount);
        if (!extracted.isEmpty()) operation.returned(AEItemKey.of(extracted), extracted.getCount());
        if (extracted.getCount() != amount) throw new IllegalStateException("Ars output extraction was incomplete");
    }

    private @Nullable Layout layout(ServerLevel level, BlockPos position) {
        if (!recognizes(level, position)) return null;
        int radius = this.kind.loadedRadius;
        // Ars discovery APIs do not check loaded chunks themselves, including the source search around an apparatus.
        for (int x = (position.getX() - radius) >> 4; x <= (position.getX() + radius) >> 4; x++) {
            for (int z = (position.getZ() - radius) >> 4; z <= (position.getZ() + radius) >> 4; z++) {
                if (!level.hasChunk(x, z)) return null;
            }
        }
        BlockEntity tile = level.getBlockEntity(position);
        if (tile instanceof EnchantingApparatusTile apparatus) {
            Direction facing = apparatus.getBlockState().getValue(BlockStateProperties.FACING);
            var core = level.getBlockState(position.relative(facing.getOpposite()));
            if (!(core.getBlock() instanceof ArcaneCore) ||
                    core.getValue(BlockStateProperties.FACING).getAxis() != facing.getAxis())
                return null;
        }
        var pedestals = new ObjectArrayList<ArcanePedestalTile>();
        for (BlockPos pedestalPosition : this.kind.positions(tile)) {
            if (!(level.getBlockEntity(pedestalPosition) instanceof ArcanePedestalTile pedestal)) return null;
            pedestals.add(pedestal);
        }
        return new Layout(tile, (Container) tile, pedestals);
    }

    private static ItemStack read(PackagedMachineOperation operation, CompoundTag tag, String key) {
        return ItemStack.parse(operation.level().registryAccess(), tag.getCompound(key))
                .orElseThrow(() -> new IllegalArgumentException("Invalid Ars persisted " + key));
    }

    private record Layout(BlockEntity tile, Container inventory, List<ArcanePedestalTile> pedestals) {

        boolean centerEmpty() {
            return this.inventory.isEmpty() && (!(this.tile instanceof EnchantingApparatusTile apparatus) || !apparatus.isCrafting);
        }
    }
}
