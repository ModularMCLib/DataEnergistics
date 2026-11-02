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

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import dev.vfyjxf.taffy.style.TaffyPosition;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.function.IntConsumer;

/** One synthetic world per UI; selecting a spatial cell only opens its single shared editor. */
@OnlyIn(Dist.CLIENT)
public final class LdlibAreaPlanePreview implements PlaneScenePreview {

    private final Scene scene = new Scene();
    private final TrackedDummyWorld world = new TrackedDummyWorld();
    private final PlaneLayout layout;

    public LdlibAreaPlanePreview(StructurePreviewSceneElement shell, AreaPlaneVariant variant, IntConsumer selection) {
        layout = new PlaneLayout(variant, Direction.NORTH);
        Object2IntMap<BlockPos> selectable = new Object2IntOpenHashMap<>();
        selectable.defaultReturnValue(-1);
        var blocks = new Object2ObjectLinkedOpenHashMap<BlockPos, BlockInfo>();
        var rendered = new ObjectArrayList<BlockPos>();
        for (int slot = 0; slot < variant.getSlotCount(); slot++) {
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
            blocks.put(body, new BlockInfo(state, false));
            blocks.put(layout.targetPosition(BlockPos.ZERO, slot), new BlockInfo(placeholder(slot), false));
            selectable.put(body, slot);
            selectable.put(layout.targetPosition(BlockPos.ZERO, slot), slot);
            rendered.add(body);
            rendered.add(layout.targetPosition(BlockPos.ZERO, slot));
        }
        world.addBlocks(blocks);
        scene.layout(style -> style.positionType(TaffyPosition.ABSOLUTE).left(0).top(0).widthPercent(100).heightPercent(100));
        scene.markAsInternal();
        shell.attachClientScene(scene);
        scene.setOnSelected((position, face) -> {
            int slot = selectable.getInt(position);
            if (slot >= 0) selection.accept(slot);
        });
        scene.createScene(world, true, Size.of(556, 340)).setTickWorld(false).setDraggable(true).setScalable(true)
                .setRenderSelect(true).setRenderFacing(true).setShowHoverBlockTips(false).useCacheBuffer();
        scene.setRenderedCore(rendered, null, true);
    }

    @Override
    public void refresh(ItemStack[] filters, int entityModes) {
        for (int slot = 0; slot < filters.length; slot++) {
            ItemStack filter = filters[slot];
            BlockState state = !filter.isEmpty() && filter.getItem() instanceof BlockItem item && (entityModes & 1 << slot) == 0 ?
                    item.getBlock().defaultBlockState() : placeholder(slot);
            world.addBlock(layout.targetPosition(BlockPos.ZERO, slot), new BlockInfo(state, false));
        }
        scene.needCompileCache();
    }

    private static BlockState placeholder(int slot) {
        return (slot % 2 == 0 ? Blocks.LIGHT_BLUE_STAINED_GLASS : Blocks.CYAN_STAINED_GLASS).defaultBlockState();
    }
}
