package com.fish_dan_.data_energistics.client.gui.plane;

import com.fish_dan_.data_energistics.common.block.plane.AreaPlaneBlock;
import com.fish_dan_.data_energistics.common.block.plane.AreaPlaneSegmentBlock;
import com.fish_dan_.data_energistics.common.data.DEBlocks;
import com.fish_dan_.data_energistics.common.gui.multiblock.preview.scene.StructurePreviewSceneElement;
import com.fish_dan_.data_energistics.common.gui.plane.PlaneScenePreview;
import com.fish_dan_.data_energistics.common.plane.model.AreaPlaneVariant;
import com.fish_dan_.data_energistics.common.plane.model.PlaneLayout;

import com.lowdragmc.lowdraglib2.gui.ui.elements.Scene;
import com.lowdragmc.lowdraglib2.math.Size;
import com.lowdragmc.lowdraglib2.utils.data.BlockInfo;
import com.lowdragmc.lowdraglib2.utils.virtuallevel.TrackedDummyWorld;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.vfyjxf.taffy.style.TaffyPosition;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.joml.Vector3f;

import java.util.Arrays;
import java.util.function.IntConsumer;

/** One synthetic world per UI; selecting a spatial cell only opens its single shared editor. */
@OnlyIn(Dist.CLIENT)
public final class LdlibAreaPlanePreview implements PlaneScenePreview {

    private final TargetScene scene = new TargetScene();
    private final TrackedDummyWorld world = new TrackedDummyWorld();
    private final PlaneLayout layout;
    private ItemStack[] filters;
    private IntSet itemModes = new IntOpenHashSet();
    private int layer;

    public LdlibAreaPlanePreview(StructurePreviewSceneElement shell, AreaPlaneVariant variant, IntConsumer selection) {
        layout = new PlaneLayout(variant, Direction.NORTH);
        filters = new ItemStack[variant.getWorkingCellCount()];
        Arrays.fill(filters, ItemStack.EMPTY);
        Object2IntMap<BlockPos> selectable = new Object2IntOpenHashMap<>();
        selectable.defaultReturnValue(-1);
        var blocks = new Object2ObjectLinkedOpenHashMap<BlockPos, BlockInfo>();
        var bodyPositions = new ObjectArrayList<BlockPos>();
        for (int slot = 0; slot < variant.getFaceCellCount(); slot++) {
            BlockPos body = layout.bodyPosition(BlockPos.ZERO, slot);
            BlockState state;
            if (body.equals(BlockPos.ZERO)) {
                state = switch (variant) {
                    case LARGE_FORMATION -> DEBlocks.LARGE_FORMATION_PLANE.get().defaultBlockState();
                    case EXTREMELY_LARGE_FORMATION -> DEBlocks.EXTREMELY_LARGE_FORMATION_PLANE.get().defaultBlockState();
                    case LARGE_ANNIHILATION -> DEBlocks.LARGE_ANNIHILATION_PLANE.get().defaultBlockState();
                    case EXTREMELY_LARGE_ANNIHILATION -> DEBlocks.EXTREMELY_LARGE_ANNIHILATION_PLANE.get().defaultBlockState();
                };
            } else {
                state = DEBlocks.AREA_PLANE_SEGMENT.get().defaultBlockState().setValue(AreaPlaneBlock.FACING, Direction.NORTH)
                        .setValue(AreaPlaneSegmentBlock.SEGMENT, layout.segmentIndex(slot));
            }
            if (state.getBlock() instanceof AreaPlaneBlock) state = state.setValue(AreaPlaneBlock.ACTIVE, true);
            blocks.put(body, new BlockInfo(state, false));
            bodyPositions.add(body);
        }
        for (int slot = 0; slot < variant.getWorkingCellCount(); slot++) {
            selectable.put(layout.targetPosition(BlockPos.ZERO, slot), slot);
        }
        world.addBlocks(blocks);
        scene.layout(style -> style.positionType(TaffyPosition.ABSOLUTE).left(0).top(0).widthPercent(100).heightPercent(100));
        scene.markAsInternal();
        shell.attachClientScene(scene);
        scene.setOnSelected((position, face) -> {
            int slot = selectable.getInt(position);
            if (slot >= 0) selection.accept(slot);
        });
        scene.createScene(world, true, Size.of(582, 390)).setTickWorld(false).setDraggable(true).setScalable(true)
                .setRenderSelect(true).setRenderFacing(true).setShowHoverBlockTips(false).useCacheBuffer();
        // Only working cells enter Scene's hit-test core. The plate is a separate, non-selectable render group.
        scene.getRenderer().addRenderedBlocks(bodyPositions, null);
        scene.setCenter(new Vector3f(0.5f, 0.5f, -variant.getWidth() / 2f));
        scene.setZoom(variant.getWidth() * 2f);
        scene.setAfterWorldRender(view -> drawWorkingBounds());
        refreshLayer();
    }

    @Override
    public void refresh(ItemStack[] filters, IntSet itemModes) {
        this.filters = filters;
        this.itemModes = itemModes;
        refreshLayer();
    }

    @Override
    public void showLayer(int layer) {
        this.layer = layer;
        scene.clearSelection();
        refreshLayer();
    }

    private void refreshLayer() {
        var rendered = new ObjectArrayList<BlockPos>(layout.variant().getFaceCellCount());
        for (int slot = 0; slot < filters.length; slot++) {
            BlockPos target = layout.targetPosition(BlockPos.ZERO, slot);
            if (slot / layout.variant().getFaceCellCount() != layer) {
                world.addBlock(target, new BlockInfo(Blocks.AIR.defaultBlockState(), false));
                continue;
            }
            ItemStack filter = filters[slot];
            BlockState state = !filter.isEmpty() && filter.getItem() instanceof BlockItem item && !itemModes.contains(slot) ?
                    item.getBlock().defaultBlockState() : placeholder(slot);
            world.addBlock(target, new BlockInfo(state, false));
            rendered.add(target);
        }
        scene.setRenderedCore(rendered, null, false);
    }

    private void drawWorkingBounds() {
        int width = layout.variant().getWidth();
        int radius = width / 2;
        var buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        LevelRenderer.renderLineBox(new PoseStack(), buffers.getBuffer(RenderType.lines()),
                new AABB(-radius, -radius, -width, radius + 1, radius + 1, 0), 0.6f, 0.85f, 1f, 1f);
        buffers.endBatch(RenderType.lines());
    }

    private static BlockState placeholder(int slot) {
        return (slot % 2 == 0 ? Blocks.LIGHT_BLUE_STAINED_GLASS : Blocks.CYAN_STAINED_GLASS).defaultBlockState();
    }

    private static final class TargetScene extends Scene {

        private void clearSelection() {
            lastSelectedPosFace = null;
            lastHoverPosFace = null;
            lastClickPosFace = null;
        }
    }
}
