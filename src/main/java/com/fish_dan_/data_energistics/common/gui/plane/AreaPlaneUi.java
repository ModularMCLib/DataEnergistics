package com.fish_dan_.data_energistics.common.gui.plane;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.api.gui.style.DEStylesheets;
import com.fish_dan_.data_energistics.api.gui.style.SharedBackpackStyles;
import com.fish_dan_.data_energistics.api.gui.style.StateTextureButton;
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
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Toggle;
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
    private final Label status = new Label();
    private final Label depth = new Label();
    private final Label selectionHint = new Label();
    private final StateTextureButton previousLayer = new StateTextureButton();
    private final StateTextureButton nextLayer = new StateTextureButton();
    private final PlaneGhostSlot ghost;
    private final Toggle mode = new Toggle();
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
        shell(380, 250);
        StructurePreviewSceneElement scene = new StructurePreviewSceneElement();
        scene.setId("area_plane_scene");
        scene.style(style -> style.backgroundTexture(GuiTextureGroup.of(
                new ColorRectTexture(0xFF696D88), new ColorBorderTexture(-1, 0xFFF2F2F2))));
        root.addChild(at(scene, 8, 24, 194, 130));
        createPositionEditor();
        createFormationInformation();
        addInventory(210, 162);
        return scene;
    }

    private void createFormationInformation() {
        UIElement information = at(new UIElement(), 8, 162, 194, 80);
        information.setId("area_plane_information");
        root.addChild(information);
        previousLayer.setArrow(StateTextureButton.Arrow.LEFT);
        nextLayer.setArrow(StateTextureButton.Arrow.RIGHT);
        previousLayer.setOnClick(event -> {
            if (client && event.button == 0 && layer > 0) changeLayer(layer - 1);
        });
        nextLayer.setOnClick(event -> {
            if (client && event.button == 0 && layer + 1 < host.layout().variant().getWidth()) changeLayer(layer + 1);
        });
        information.addChildren(at(previousLayer, 0, 0, 10, 10), at(nextLayer, 184, 0, 10, 10));
        information.addChild(at(depth, 16, 0, 162, 10));
        styleLabel(depth);
        information.addChild(at(status, 0, 16, 194, 8));
        int range = host.layout().variant().getWidth();
        label(information, text("range", range, range, range), 0, 27, 194, 8).textStyle(style -> style.fontSize(6));
        information.addChild(at(position, 0, 38, 194, 14));
        styleLabel(position);
        position.textStyle(style -> style.fontSize(6).textWrap(TextWrap.WRAP));
        position.setDisplay(false);
        selectionHint.setValue(text("select_hint"));
        selectionHint.setAllowHitTest(false);
        styleLabel(selectionHint);
        selectionHint.textStyle(style -> style.fontSize(6).textWrap(TextWrap.WRAP));
        information.addChild(at(selectionHint, 0, 38, 194, 14));
        label(information, text("scene_hint"), 0, 54, 194, 8).textStyle(style -> style.fontSize(6));
        label(information, text("filter_hint"), 0, 65, 194, 15).textStyle(style -> style.fontSize(6).textWrap(TextWrap.WRAP));
    }

    private void createPositionEditor() {
        UIElement configuration = at(new UIElement(), 210, 24, 162, 130);
        configuration.setId("area_plane_configuration");
        configuration.style(style -> style.backgroundTexture(GuiTextureGroup.of(
                new ColorRectTexture(0xFF9A9FB4), new ColorBorderTexture(-1, 0xFFF2F2F2))));
        root.addChild(configuration);
        editor.setId("area_plane_position_editor");
        configuration.addChild(at(editor, 0, 0, 162, 130));
        ghost.setId("area_plane_filter");
        SharedBackpackStyles.standalone(ghost);
        editor.addChild(at(ghost, 12, 20, 18, 18));
        editor.addChild(at(material, 40, 18, 110, 26));
        styleLabel(material);
        material.textStyle(style -> style.textWrap(TextWrap.WRAP));
        mode.setId("area_plane_placement_mode");
        SpriteTexture on = switchTexture("on");
        SpriteTexture onHovered = switchTexture("on_hovered");
        SpriteTexture off = switchTexture("off");
        SpriteTexture offHovered = switchTexture("off_hovered");
        mode.toggleStyle(style -> style.baseTexture(IGuiTexture.EMPTY).hoverTexture(IGuiTexture.EMPTY)
                .markTexture(IGuiTexture.dynamic(() -> mode.toggleButton.isSelfOrChildHover() ? onHovered : on))
                .unmarkTexture(IGuiTexture.dynamic(() -> mode.toggleButton.isSelfOrChildHover() ? offHovered : off)));
        mode.layout(layout -> layout.paddingAll(0));
        mode.toggleButton.layout(layout -> layout.width(22).height(12).setAspectRatio(22f / 12f));
        mode.markIcon.setAllowHitTest(false);
        mode.toggleLabel.layout(layout -> layout.marginLeft(6));
        styleLabel(mode.toggleLabel);
        mode.setOnToggleChanged(asItem -> {
            if (client && selected >= 0) {
                applyMode(selected, asItem);
                setMode.send(selected, (asItem ? PlanePlacementMode.ITEM : PlanePlacementMode.BLOCK).ordinal());
            }
        });
        editor.addChild(at(mode, 12, 62, 138, 14));
        editor.setDisplay(false);
        ghost.registerValueListener(stack -> {
            if (client && selected >= 0) setFilter.send(selected, stack.copyWithCount(1));
        });
    }

    private void createFilterEditor() {
        int rows = (filters.length + 8) / 9;
        int inventoryY = 54 + rows * 18;
        shell(178, inventoryY + 84);
        root.addChild(at(status, 8, inventoryY - 12, 162, 8));
        int range = host.layout().variant().getWidth();
        root.style(style -> style.tooltips(text("range", range, range, range)));
        addInventory(8, inventoryY);
        for (int slot = 0; slot < filterSlots.length; slot++) {
            int index = slot;
            PlaneGhostSlot filter = new PlaneGhostSlot(player);
            filter.setId("area_plane_filter_" + slot);
            SharedBackpackStyles.standalone(filter);
            filter.style(style -> style.tooltips(text("annihilation_filter_hint"), text("filter_hint")));
            filter.registerValueListener(stack -> {
                if (client) setFilter.send(index, stack.copyWithCount(1));
            });
            filterSlots[slot] = filter;
            root.addChild(at(filter, 8 + slot % 9 * 18, 24 + slot / 9 * 18, 18, 18));
        }
        label(root, text("annihilation_filter_hint"), 8, 28 + rows * 18, 162, 14)
                .textStyle(style -> style.fontSize(6));
    }

    private void shell(int width, int height) {
        root.layout(layout -> layout.width(width).height(height));
        label(root, host.getBlockState().getBlock().getName(), 8, 6, width - 32, 12)
                .textStyle(style -> style.fontSize(8));
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
        root.addChild(at(close, width - 22, 5, 14, 14));
        status.setValue(text("offline"));
        styleLabel(status);
        status.textStyle(style -> style.fontSize(6).textWrap(TextWrap.HOVER_ROLL));
        status.setAllowHitTest(true);
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
        var state = new SyncValue<>("plane_status", Integer.class, 0);
        state.setToSync(!view.client);
        state.setAcceptSync(view.client);
        state.addListener(value -> {
            Component message = text(switch (value) {
                case 1 -> "online";
                case 2 -> "faulted";
                default -> "offline";
            });
            view.status.setValue(message);
            view.status.style(style -> style.tooltips(message));
        });
        if (!view.client) state.setValueProvider(() -> host.isFaulted() ? 2 : host.getMainNode().isOnline() ? 1 : 0);
        ui.syncManager.registerSyncValue(state);
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
        BlockPos target = host.layout().targetPosition(host.getBlockPos(), slot);
        position.setValue(text("position", target.getX(), target.getY(), target.getZ()));
        position.setDisplay(true);
        selectionHint.setDisplay(false);
        editor.setDisplay(true);
        updateEditor();
    }

    private void changeLayer(int value) {
        layer = value;
        selected = -1;
        editor.setDisplay(false);
        position.setDisplay(false);
        selectionHint.setDisplay(true);
        updateLayer();
    }

    private void updateLayer() {
        depth.setValue(text("depth", layer + 1, host.layout().variant().getWidth()));
        previousLayer.setActive(layer > 0);
        nextLayer.setActive(layer + 1 < host.layout().variant().getWidth());
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
        material.setValue(stack.isEmpty() ? text("disabled") : stack.getHoverName());
        boolean asItem = itemModes.contains(selected);
        mode.setOn(asItem, false);
        mode.setText(text(asItem ? "mode_item" : "mode_block"));
    }

    private static SpriteTexture switchTexture(String state) {
        return SpriteTexture.of(Data_Energistics.id("textures/guis/states/top_surface/toggle/horizontal/" + state + ".png"));
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
