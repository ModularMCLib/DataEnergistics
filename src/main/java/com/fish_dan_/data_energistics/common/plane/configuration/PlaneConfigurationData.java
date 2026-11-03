package com.fish_dan_.data_energistics.common.plane.configuration;

import appeng.api.stacks.AEItemKey;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntLists;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectLists;

/** Immutable, registry-aware component value shared by item persistence and block-entity saves. */
public record PlaneConfigurationData(ObjectList<Cell> cells, IntList itemModes) {

    public static final PlaneConfigurationData EMPTY = new PlaneConfigurationData(ObjectList.of(), IntList.of());
    private static final Codec<ObjectList<Cell>> CELLS_CODEC = Cell.CODEC.listOf().validate(cells -> cells.size() <= 125 ?
            DataResult.success(cells) : DataResult.error(() -> "An area plane supports at most 125 configured positions"))
            .xmap(ObjectArrayList::new, cells -> cells);
    private static final Codec<IntList> ITEM_MODES_CODEC = Codec.intRange(0, 124).listOf().validate(modes -> modes.size() <= 125 ?
            DataResult.success(modes) : DataResult.error(() -> "An area plane supports at most 125 placement modes"))
            .xmap(IntArrayList::new, modes -> modes);
    public static final Codec<PlaneConfigurationData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            CELLS_CODEC.optionalFieldOf("cells", ObjectList.of()).forGetter(PlaneConfigurationData::cells),
            ITEM_MODES_CODEC.optionalFieldOf("item_modes", IntList.of()).forGetter(PlaneConfigurationData::itemModes)).apply(instance, PlaneConfigurationData::new));

    public PlaneConfigurationData {
        cells = ObjectLists.unmodifiable(new ObjectArrayList<>(cells));
        itemModes = IntLists.unmodifiable(new IntArrayList(itemModes));
    }

    public record Cell(int slot, AEItemKey filter) {

        public static final Codec<Cell> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.intRange(0, 124).fieldOf("slot").forGetter(Cell::slot),
                AEItemKey.MAP_CODEC.forGetter(Cell::filter)).apply(instance, Cell::new));
    }
}
