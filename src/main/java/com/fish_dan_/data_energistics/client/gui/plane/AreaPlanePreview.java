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
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.vfyjxf.taffy.style.TaffyPosition;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.joml.Vector3f;

import java.util.Arrays;
import java.util.function.IntConsumer;
import java.util.function.Predicate;

/** One synthetic world per UI; selecting a spatial cell only opens its single shared editor. */
@OnlyIn(Dist.CLIENT)
public final class AreaPlanePreview implements PlaneScenePreview {

    private final TargetScene scene = new TargetScene();
    private final ObjectSet<BlockPos> editablePositions = new ObjectOpenHashSet<>();
    private final PreviewWorld world = new PreviewWorld(editablePositions::contains);
    private final ObjectArrayList<BlockPos> configuredPositions = new ObjectArrayList<>();
    private final Int2ObjectMap<PreviewItemEntity> itemPreviews = new Int2ObjectOpenHashMap<>();
    private final PlaneLayout layout;
    private ItemStack[] filters;
    private IntSet itemModes = new IntOpenHashSet();
    private int layer;

    public AreaPlanePreview(StructurePreviewSceneElement shell, AreaPlaneVariant variant, IntConsumer selection) {
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
        scene.createScene(world, true, Size.of(480, 276)).setTickWorld(false).setDraggable(true).setScalable(true)
                .setRenderSelect(true).setRenderFacing(true).setShowHoverBlockTips(false).useCacheBuffer();
        // Rendering keeps the complete configuration; PreviewWorld limits ray picking to the editable layer.
        world.setBlockFilter(position -> true);
        scene.getRenderer().addRenderedBlocks(bodyPositions, null);
        scene.setCenter(new Vector3f(0.5f, 0.5f, -variant.getWidth() / 2f));
        scene.setZoom(variant.getWidth() * 2f);
        scene.setAfterWorldRender(view -> drawWorkingBounds());
        refreshPreview();
    }

    @Override
    public void refresh(ItemStack[] filters, IntSet itemModes) {
        this.filters = filters;
        this.itemModes = itemModes;
        refreshPreview();
    }

    @Override
    public void showLayer(int layer) {
        this.layer = layer;
        scene.clearSelection();
        refreshPreview();
    }

    private void refreshPreview() {
        var renderer = scene.getRenderer();
        renderer.removeRenderedBlocks(configuredPositions);
        configuredPositions.clear();
        editablePositions.clear();
        for (int slot = 0; slot < filters.length; slot++) {
            BlockPos target = layout.targetPosition(BlockPos.ZERO, slot);
            boolean editable = slot / layout.variant().getFaceCellCount() == layer;
            ItemStack filter = filters[slot];
            if (!editable && filter.isEmpty()) {
                world.removeBlock(target);
                itemPreviews.remove(slot);
                continue;
            }
            BlockState state;
            if (!filter.isEmpty() && itemModes.contains(slot)) {
                itemPreviews.computeIfAbsent(slot, index -> new PreviewItemEntity(world, target)).setItem(filter.copyWithCount(1));
                // The invisible full cube keeps the whole cell selectable around the small item entity.
                state = Blocks.BARRIER.defaultBlockState();
            } else {
                itemPreviews.remove(slot);
                state = !filter.isEmpty() && filter.getItem() instanceof BlockItem item ? item.getBlock().defaultBlockState() : placeholder(slot);
            }
            world.addBlock(target, new BlockInfo(state, false));
            if (editable) editablePositions.add(target);
            else configuredPositions.add(target);
        }
        renderer.addRenderedBlocks(configuredPositions, null);
        scene.setRenderedCore(editablePositions, null, false);
    }

    private void drawWorkingBounds() {
        int width = layout.variant().getWidth();
        int radius = width / 2;
        var buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        LevelRenderer.renderLineBox(new PoseStack(), buffers.getBuffer(RenderType.lines()),
                new AABB(-radius, -radius, -width, radius + 1, radius + 1, 0), 0.6f, 0.85f, 1f, 1f);
        for (int slot : itemPreviews.keySet()) {
            LevelRenderer.renderLineBox(new PoseStack(), buffers.getBuffer(RenderType.lines()),
                    new AABB(layout.targetPosition(BlockPos.ZERO, slot)), 0.6f, 0.85f, 1f, 0.5f);
        }
        buffers.endBatch(RenderType.lines());
    }

    private static BlockState placeholder(int slot) {
        return (slot % 2 == 0 ? Blocks.LIGHT_BLUE_STAINED_GLASS : Blocks.CYAN_STAINED_GLASS).defaultBlockState();
    }

    /** A filtered proxy supplies ray intersections while the render world keeps every configured layer. */
    private static final class PreviewWorld extends TrackedDummyWorld {

        private final TrackedDummyWorld selectionWorld = new TrackedDummyWorld(this);

        private PreviewWorld(Predicate<BlockPos> editable) {
            selectionWorld.setBlockFilter(editable);
        }

        @Override
        public BlockHitResult clip(ClipContext context) {
            return selectionWorld.clip(context);
        }
    }

    private final class TargetScene extends Scene {

        @Override
        public void screenTick() {
            super.screenTick();
            // Advance only the display animation; preview items never run physics or item behaviour.
            for (PreviewItemEntity item : itemPreviews.values()) item.tickCount++;
        }

        @Override
        protected void renderBeforeBatchEnd(MultiBufferSource buffers, float partialTicks) {
            var dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
            PoseStack pose = new PoseStack();
            for (PreviewItemEntity item : itemPreviews.values()) {
                dispatcher.render(item, item.getX(), item.getY(), item.getZ(), 0, partialTicks, pose, buffers, LightTexture.FULL_BRIGHT);
            }
        }

        @Override
        protected void onRemoved() {
            super.onRemoved();
            itemPreviews.clear();
        }

        private void clearSelection() {
            lastSelectedPosFace = null;
            lastHoverPosFace = null;
            lastClickPosFace = null;
        }
    }

    /** Render-only entities stay outside both the real level and the dummy world's entity lifecycle. */
    private static final class PreviewItemEntity extends ItemEntity {

        private PreviewItemEntity(TrackedDummyWorld world, BlockPos target) {
            super(world, target.getX() + 0.5, target.getY() + 0.375, target.getZ() + 0.75, ItemStack.EMPTY, 0, 0, 0);
        }

        @Override
        public int getAge() {
            return tickCount;
        }
    }
}
