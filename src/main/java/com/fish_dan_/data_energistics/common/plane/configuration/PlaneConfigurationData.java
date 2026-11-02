package com.fish_dan_.data_energistics.common.plane.configuration;

import appeng.api.stacks.AEItemKey;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectLists;

/** Immutable, registry-aware component value shared by item persistence and block-entity saves. */
public record PlaneConfigurationData(ObjectList<Cell> cells, int entityModes) {

    public static final PlaneConfigurationData EMPTY = new PlaneConfigurationData(ObjectList.of(), 0);
    private static final Codec<ObjectList<Cell>> CELLS_CODEC = Cell.CODEC.listOf().validate(cells -> cells.size() <= 25 ?
            DataResult.success(cells) : DataResult.error(() -> "An area plane supports at most 25 configured positions"))
            .xmap(ObjectArrayList::new, cells -> cells);
    public static final Codec<PlaneConfigurationData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            CELLS_CODEC.optionalFieldOf("cells", ObjectList.of()).forGetter(PlaneConfigurationData::cells),
            Codec.intRange(0, (1 << 25) - 1).optionalFieldOf("entity_modes", 0).forGetter(PlaneConfigurationData::entityModes)).apply(instance, PlaneConfigurationData::new));

    public PlaneConfigurationData {
        cells = ObjectLists.unmodifiable(new ObjectArrayList<>(cells));
    }

    public record Cell(int slot, AEItemKey filter) {

        public static final Codec<Cell> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.intRange(0, 24).fieldOf("slot").forGetter(Cell::slot),
                AEItemKey.MAP_CODEC.forGetter(Cell::filter)).apply(instance, Cell::new));
    }
}
