package com.fish_dan_.data_energistics.common.gui.plane;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.api.gui.style.DEStylesheets;
import com.fish_dan_.data_energistics.api.gui.style.SharedBackpackStyles;
import com.fish_dan_.data_energistics.client.gui.plane.AreaPlanePreview;
import com.fish_dan_.data_energistics.common.blockentity.plane.AreaPlaneBlockEntity;
import com.fish_dan_.data_energistics.common.gui.multiblock.preview.scene.StructurePreviewSceneElement;
import com.fish_dan_.data_energistics.common.plane.configuration.PlanePlacementMode;
import com.fish_dan_.data_energistics.common.plane.model.AreaPlaneVariant;

import com.lowdragmc.lowdraglib2.gui.sync.SyncValue;
import com.lowdragmc.lowdraglib2.gui.sync.rpc.RPCEmitter;
import com.lowdragmc.lowdraglib2.gui.sync.rpc.RPCEventBuilder;
import com.lowdragmc.lowdraglib2.gui.texture.ColorBorderTexture;
import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Scroller;
import com.lowdragmc.lowdraglib2.gui.ui.elements.inventory.InventorySlots;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import dev.vfyjxf.taffy.style.TaffyPosition;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.objects.ObjectList;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;

/** Formation edits spatial cells; annihilation shares one filter list across its complete working volume. */
public final class AreaPlaneUi {

    private static final int TEXT = 0xFF343548;
    private final AreaPlaneBlockEntity host;
    private final Player player;
    private final boolean client;
    private final boolean formation;
    private final UIElement root = new UIElement();
    private final UIElement editor = new UIElement();
    private final Label position = new Label();
    private final Label material = new Label();
    private final Scroller.Horizontal layerScroller = new Scroller.Horizontal();
    private final PlaneGhostSlot ghost;
    private final Button mode = new Button();
    private final ItemStack[] filters;
    private final PlaneGhostSlot[] filterSlots;
    private final IntSet itemModes = new IntOpenHashSet();
    private final @Nullable PlaneScenePreview preview;
    private final RPCEmitter setFilter;
    private final RPCEmitter setMode;
    private int selected = -1;
    private int layer;

    private AreaPlaneUi(AreaPlaneBlockEntity host, Player player) {
        this.host = host;
        this.player = player;
        client = player.level().isClientSide();
        formation = host.layout().variant().getOperation() == AreaPlaneVariant.Operation.FORMATION;
        filters = new ItemStack[host.getConfiguration().getSlots()];
        Arrays.fill(filters, ItemStack.EMPTY);
        filterSlots = new PlaneGhostSlot[formation ? 0 : filters.length];
        ghost = new PlaneGhostSlot(player);
        root.setId("area_plane");
        root.addClass("terminal-popup");
        setFilter = root.addRPCEvent(RPCEventBuilder.simple(Integer.class, ItemStack.class, (index, stack) -> {
            if (!client && accepts(index)) host.getConfiguration().setFilter(index, stack);
        }));
        setMode = root.addRPCEvent(RPCEventBuilder.simple(Integer.class, Integer.class, (index, value) -> {
            if (!client && formation && accepts(index) && value >= 0 && value < PlanePlacementMode.values().length) {
                host.getConfiguration().setMode(index, PlanePlacementMode.values()[value]);
            }
        }));
        if (formation) {
            StructurePreviewSceneElement scene = createFormationEditor();
            preview = client ? new AreaPlanePreview(scene, host.layout().variant(), this::select) : null;
            if (preview != null) preview.refresh(filters, itemModes);
            updateLayer();
        } else {
            createFilterEditor();
            preview = null;
        }
    }

    private StructurePreviewSceneElement createFormationEditor() {
        root.layout(layout -> layout.width(178).height(232));
        label(root, host.getBlockState().getBlock().getName(), 8, 2, 162, 18)
                .textStyle(style -> style.fontSize(8).textAlignVertical(Vertical.CENTER));
        UIElement frame = at(new UIElement(), 6, 20, 166, 208);
        frame.style(style -> style.backgroundTexture(new ColorBorderTexture(-1, 0xFFF2F2F2)));
        root.addChild(frame);
        UIElement viewport = at(new UIElement(), 2, 2, 162, 100);
        viewport.setId("area_plane_viewport");
        // Draw the shared outline before the children so it cannot cover the slider thumb.
        viewport.style(style -> style.backgroundTexture(GuiTextureGroup.of(
                new ColorRectTexture(0xFF9A9FB4), new ColorBorderTexture(-1, 0xFFF2F2F2))));
        frame.addChild(viewport);
        StructurePreviewSceneElement scene = new StructurePreviewSceneElement();
        scene.setId("area_plane_scene");
        // The scene and its layer rail share one outline, with room reserved below the framebuffer.
        viewport.addChild(at(scene, 1, 1, 160, 92));
        createLayerScroller(viewport);
        createPositionEditor(frame);
        addInventory(8, 148);
        return scene;
    }

    private void createLayerScroller(UIElement viewport) {
        int lastLayer = host.layout().variant().getWidth() - 1;
        layerScroller.setId("area_plane_layer_scroller");
        layerScroller.headButton.setDisplay(false);
        layerScroller.tailButton.setDisplay(false);
        layerScroller.layout(layout -> layout.gapRow(0).gapColumn(0));
        // The multiblock preview's framed rail is baked into the bottom of its structure-view texture.
        layerScroller.scrollContainer.style(style -> style.backgroundTexture(
                SpriteTexture.of(Data_Energistics.id("textures/guis/autobuild/structure_view.png"))
                        .setSprite(22, 128, 152, 5).setBorder(1)));
        // Match the multiblock preview's four-pixel thumb and one-layer wheel steps.
        layerScroller.scrollerStyle(style -> style.scrollBarSize(4f / 162 * 100).scrollDelta(1f / lastLayer));
        layerScroller.scrollBar.buttonStyle(style -> style
                .baseTexture(SpriteTexture.of(Data_Energistics.id("textures/guis/small_scroller.png")))
                .hoverTexture(SpriteTexture.of(Data_Energistics.id("textures/guis/small_highlighted.png")))
                .pressedTexture(SpriteTexture.of(Data_Energistics.id("textures/guis/small_scroller_disabled.png"))));
        layerScroller.setOnValueChanged(value -> {
            int next = Math.round(value * lastLayer);
            if (client && next != layer) changeLayer(next);
        });
        viewport.addChild(at(layerScroller, 0, 95, 162, 5));
    }

    private void createPositionEditor(UIElement frame) {
        UIElement configuration = at(new UIElement(), 2, 104, 162, 22);
        configuration.setId("area_plane_configuration");
        configuration.style(style -> style.backgroundTexture(new ColorRectTexture(0xFF9A9FB4))
                .overlayTexture(new ColorBorderTexture(-1, 0xFFF2F2F2)));
        frame.addChild(configuration);
        editor.setId("area_plane_position_editor");
        configuration.addChild(at(editor, 0, 0, 162, 22));
        ghost.setId("area_plane_filter");
        SharedBackpackStyles.standalone(ghost);
        ghost.style(style -> style.overlayTexture(new ColorBorderTexture(-1, 0xFFF2F2F2)));
        editor.addChild(at(ghost, 2, 2, 18, 18));
        editor.addChildren(at(position, 27, 2, 86, 8), at(material, 27, 12, 86, 8));
        styleLabel(position);
        styleLabel(material);
        position.textStyle(style -> style.fontSize(6).textAlignVertical(Vertical.CENTER));
        material.textStyle(style -> style.fontSize(6).textAlignVertical(Vertical.CENTER));
        mode.setId("area_plane_placement_mode");
        SpriteTexture normal = modeTexture("normal");
        SpriteTexture disabled = modeTexture("disabled");
        mode.buttonStyle(style -> style.baseTexture(IGuiTexture.dynamic(() -> mode.isActive() ? normal : disabled))
                .hoverTexture(modeTexture("hovered")).pressedTexture(modeTexture("hovered")));
        mode.textStyle(style -> style.adaptiveWidth(false).adaptiveHeight(false).fontSize(6)
                .textWrap(TextWrap.HOVER_ROLL).textColor(TEXT).textShadow(false));
        mode.text.layout(layout -> layout.widthPercent(100).heightPercent(100).marginAll(0));
        mode.setOnClick(event -> {
            if (client && selected >= 0) {
                boolean asItem = !itemModes.contains(selected);
                applyMode(selected, asItem);
                setMode.send(selected, (asItem ? PlanePlacementMode.ITEM : PlanePlacementMode.BLOCK).ordinal());
            }
        });
        editor.addChild(at(mode, 119, 3, 40, 16));
        clearEditor();
        ghost.registerValueListener(stack -> {
            if (client && selected >= 0) setFilter.send(selected, stack.copyWithCount(1));
        });
    }

    private void createFilterEditor() {
        int rows = filters.length / 9;
        int inventoryY = 42 + rows * 18;
        shell(178, inventoryY + 84);
        int range = host.layout().variant().getWidth();
        root.style(style -> style.tooltips(text("range", range, range, range)));
        addInventory(8, inventoryY);
        for (int slot = 0; slot < filterSlots.length; slot++) {
            int index = slot;
            PlaneGhostSlot filter = new PlaneGhostSlot(player);
            filter.setId("area_plane_filter_" + slot);
            filter.style(style -> style.tooltips(text("annihilation_filter_hint"), text("filter_hint")));
            filter.registerValueListener(stack -> {
                if (client) setFilter.send(index, stack.copyWithCount(1));
            });
            filterSlots[slot] = filter;
            root.addChild(at(filter, 8 + slot % 9 * 18, 24 + slot / 9 * 18, 18, 18));
        }
        SharedBackpackStyles.grid(filterSlots, 9);
        label(root, text("annihilation_filter_hint"), 8, 28 + rows * 18, 162, 14)
                .textStyle(style -> style.fontSize(6));
    }

    private void shell(int width, int height) {
        root.layout(layout -> layout.width(width).height(height));
        int headerTop = 2;
        int headerHeight = 24 - headerTop;
        label(root, host.getBlockState().getBlock().getName(), 8, headerTop, width - 32, headerHeight)
                .textStyle(style -> style.fontSize(8).textAlignVertical(Vertical.CENTER));
        Button close = new Button();
        close.addClass("terminal-control");
        close.setText(Component.empty());
        close.style(style -> style.tooltips(Component.translatable("gui.close")));
        UIElement closeIcon = at(new UIElement(), 3, 2, 8, 9);
        closeIcon.setAllowHitTest(false);
        closeIcon.style(style -> style.backgroundTexture(SpriteTexture.of(Data_Energistics.id("textures/guis/close.png")).setSprite(4, 4, 8, 9)));
        close.addChild(closeIcon);
        close.setOnServerClick(event -> {
            if (event.button == 0) player.closeContainer();
        });
        root.addChild(at(close, width - 22, headerTop + (headerHeight - 14) / 2, 14, 14));
    }

    private void addInventory(int x, int y) {
        InventorySlots inventory = SharedBackpackStyles.createInventory();
        inventory.setId("area_plane_inventory");
        root.addChild(at(inventory, x, y, 162, 77));
    }

    public static ModularUI create(AreaPlaneBlockEntity host, Player player) {
        AreaPlaneUi view = new AreaPlaneUi(host, player);
        ModularUI ui = ModularUI.of(UI.of(view.root, ObjectList.of(
                DEStylesheets.PLAYER_TERMINAL.get(), DEStylesheets.SHARED_BACKPACK.get())), player);
        for (int index = 0; index < view.filters.length; index++) {
            int slot = index;
            var filter = new SyncValue<>("plane_filter_" + slot, ItemStack.class, ItemStack.EMPTY);
            filter.setToSync(!view.client);
            filter.setAcceptSync(view.client);
            filter.addListener(stack -> {
                if (view.client) view.applyFilter(slot, stack);
            });
            if (!view.client) filter.setValueProvider(() -> host.getConfiguration().filterStack(slot));
            ui.syncManager.registerSyncValue(filter);
            if (view.formation) {
                var mode = new SyncValue<>("plane_item_mode_" + slot, Boolean.class, false);
                mode.setToSync(!view.client);
                mode.setAcceptSync(view.client);
                mode.addListener(value -> {
                    if (view.client) view.applyMode(slot, value);
                });
                if (!view.client) mode.setValueProvider(() -> host.getConfiguration().mode(slot) == PlanePlacementMode.ITEM);
                ui.syncManager.registerSyncValue(mode);
            }
        }
        return ui;
    }

    private boolean accepts(int slot) {
        BlockPos pos = host.getBlockPos();
        return slot >= 0 && slot < filters.length && player.level().getBlockEntity(pos) == host &&
                player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64 &&
                player.level().mayInteract(player, pos);
    }

    private void select(int slot) {
        selected = slot;
        ghost.setAllowHitTest(true);
        mode.setActive(true);
        updateEditor();
    }

    private void changeLayer(int value) {
        layer = value;
        clearEditor();
        updateLayer();
    }

    private void clearEditor() {
        selected = -1;
        ghost.setValue(ItemStack.EMPTY, false);
        ghost.setAllowHitTest(false);
        position.setValue(Component.empty());
        material.setValue(Component.empty());
        mode.setText(text("mode_block"));
        mode.setActive(false);
    }

    private void updateLayer() {
        layerScroller.setNormalizedValue((float) layer / (host.layout().variant().getWidth() - 1), false);
        if (preview != null) preview.showLayer(layer);
    }

    private void applyFilter(int slot, ItemStack stack) {
        filters[slot] = stack;
        if (formation) {
            if (slot == selected) updateEditor();
            if (preview != null) preview.refresh(filters, itemModes);
        } else {
            filterSlots[slot].setValue(stack, false);
        }
    }

    private void applyMode(int slot, boolean asItem) {
        if (asItem) itemModes.add(slot);
        else itemModes.remove(slot);
        if (slot == selected) updateEditor();
        if (preview != null) preview.refresh(filters, itemModes);
    }

    private void updateEditor() {
        ItemStack stack = filters[selected];
        ghost.setValue(stack, false);
        BlockPos target = host.layout().targetPosition(host.getBlockPos(), selected);
        position.setValue(text("position", target.getX(), target.getY(), target.getZ()));
        material.setValue(stack.isEmpty() ? text("disabled") : stack.getHoverName());
        boolean asItem = itemModes.contains(selected);
        mode.setText(text(asItem ? "mode_item" : "mode_block"));
    }

    private static SpriteTexture modeTexture(String state) {
        return SpriteTexture.of(Data_Energistics.id("textures/guis/states/top_surface/button/dialog_action/" + state + ".png")).setBorder(2);
    }

    private static Component text(String key, Object... arguments) {
        return Component.translatable("ui.data_energistics.area_plane." + key, arguments);
    }

    private static Label label(UIElement parent, Component text, int x, int y, int width, int height) {
        Label label = new Label();
        label.setValue(text);
        label.setAllowHitTest(false);
        styleLabel(label);
        parent.addChild(at(label, x, y, width, height));
        return label;
    }

    private static void styleLabel(Label label) {
        label.textStyle(style -> style.adaptiveWidth(false).adaptiveHeight(false).fontSize(7)
                .textWrap(TextWrap.HOVER_ROLL).textColor(TEXT).textShadow(false));
    }

    private static <T extends UIElement> T at(T element, int x, int y, int width, int height) {
        element.layout(layout -> layout.positionType(TaffyPosition.ABSOLUTE).left(x).top(y).width(width).height(height));
        return element;
    }
}
