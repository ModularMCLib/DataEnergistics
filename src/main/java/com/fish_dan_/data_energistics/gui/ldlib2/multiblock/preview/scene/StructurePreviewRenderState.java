package com.fish_dan_.data_energistics.gui.ldlib2.multiblock.preview.scene;

import com.fish_dan_.data_energistics.common.multiblock.preview.model.PreviewCandidate;
import com.fish_dan_.data_energistics.common.multiblock.preview.model.PreviewCellSnapshot;
import com.fish_dan_.data_energistics.common.multiblock.preview.model.PreviewLayerSnapshot;
import com.fish_dan_.data_energistics.common.multiblock.preview.model.PreviewViewState;
import com.fish_dan_.data_energistics.common.multiblock.preview.projection.StructurePreviewSnapshot;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMaps;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.longs.LongLists;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;

/**
 * Immutable common-side mapping from a structure snapshot to exact render states and one visible logical slice.
 *
 * @param blockStates  every selected concrete block in stable snapshot order
 * @param renderedCore concrete positions included by the current logical-layer view
 */
public record StructurePreviewRenderState(Long2ObjectMap<BlockState> blockStates,
                                          LongList renderedCore) {

    /**
     * Copies positions and states so a client renderer never observes caller mutation.
     */
    public StructurePreviewRenderState {
        if (blockStates == null || renderedCore == null) {
            throw new IllegalArgumentException("Structure preview render state arguments cannot be null");
        }
        Long2ObjectLinkedOpenHashMap<BlockState> copiedStates = new Long2ObjectLinkedOpenHashMap<>();
        for (Long2ObjectMap.Entry<BlockState> entry : blockStates.long2ObjectEntrySet()) {
            BlockState state = entry.getValue();
            if (state == null || state.isAir()) {
                throw new IllegalArgumentException("Structure preview render blocks require concrete positions and states");
            }
            if (copiedStates.put(entry.getLongKey(), state) != null) {
                throw new IllegalArgumentException("Structure preview render blocks contain duplicate position " + BlockPos.of(entry.getLongKey()));
            }
        }

        LongSet visiblePositions = new LongOpenHashSet();
        LongArrayList copiedCore = new LongArrayList(renderedCore.size());
        for (long position : renderedCore) {
            if (!copiedStates.containsKey(position)) {
                throw new IllegalArgumentException(
                        "Structure preview rendered core position has no concrete state: " + BlockPos.of(position));
            }
            if (!visiblePositions.add(position)) {
                throw new IllegalArgumentException(
                        "Structure preview rendered core contains duplicate position " + BlockPos.of(position));
            }
            copiedCore.add(position);
        }
        blockStates = Long2ObjectMaps.unmodifiable(copiedStates);
        renderedCore = LongLists.unmodifiable(copiedCore);
    }

    /**
     * Resolves selected candidates without applying an axis assumption to logical preview layers.
     *
     * @param snapshot  complete projected structure
     * @param viewState all layers or one logical layer
     * @return detached exact block map and filtered rendered core
     */
    public static StructurePreviewRenderState from(StructurePreviewSnapshot snapshot, PreviewViewState viewState) {
        if (snapshot == null || viewState == null) {
            throw new IllegalArgumentException("Structure preview render projection arguments cannot be null");
        }

        Long2ObjectLinkedOpenHashMap<BlockState> blockStates = new Long2ObjectLinkedOpenHashMap<>();
        LongSet occupiedPositions = new LongOpenHashSet();
        for (PreviewCellSnapshot cell : snapshot.cells()) {
            long position = cell.relativePosition().asLong();
            if (!occupiedPositions.add(position)) {
                throw new IllegalArgumentException(
                        "Structure preview snapshot contains duplicate position " + BlockPos.of(position));
            }
            PreviewCandidate candidate = cell.predicate().selectedCandidate().orElse(null);
            if (candidate == null || candidate.state().isEmpty()) {
                continue;
            }
            BlockState state = candidate.state().orElseThrow();
            if (state.isAir()) {
                throw new IllegalArgumentException(
                        "Structure preview concrete candidate resolves to air at " + BlockPos.of(position));
            }
            blockStates.put(position, state);
        }

        LongArrayList renderedCore = new LongArrayList();
        for (PreviewLayerSnapshot layer : snapshot.visibleLayers(viewState)) {
            for (PreviewCellSnapshot cell : layer.cells()) {
                long position = cell.relativePosition().asLong();
                if (blockStates.containsKey(position)) renderedCore.add(position);
            }
        }
        return new StructurePreviewRenderState(blockStates, renderedCore);
    }
}
