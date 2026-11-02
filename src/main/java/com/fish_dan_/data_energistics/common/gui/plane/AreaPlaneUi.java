package com.fish_dan_.data_energistics.common.gui.plane;

import com.fish_dan_.data_energistics.api.gui.style.DEStylesheets;
import com.fish_dan_.data_energistics.api.gui.style.SharedBackpackStyles;
import com.fish_dan_.data_energistics.client.gui.plane.LdlibAreaPlanePreview;
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
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.data.Transform2D;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Selector;
import com.lowdragmc.lowdraglib2.gui.ui.elements.inventory.InventorySlots;
import com.lowdragmc.lowdraglib2.math.Size;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import dev.vfyjxf.taffy.style.TaffyPosition;
import it.unimi.dsi.fastutil.objects.ObjectList;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;

/** A single position editor follows selections in a spatial preview, rather than duplicating controls per cell. */
public final class AreaPlaneUi {

    private static final int WIDTH = 440;
    private static final int HEIGHT = 298;
    private static final int TEXT = 0xFF173641;
    private final AreaPlaneBlockEntity host;
    private final Player player;
    private final boolean client;
    private final UIElement root = new UIElement();
    private final UIElement canvas = new UIElement();
    private final UIElement editor = new UIElement();
    private final Label position = new Label();
    private final Label material = new Label();
    private final Label status = new Label();
    private final PlaneGhostSlot ghost;
    private final Selector<PlanePlacementMode> mode = new Selector<>();
    private final ItemStack[] filters;
    private final @Nullable PlaneScenePreview preview;
    private final RPCEmitter setFilter;
    private final RPCEmitter setMode;
    private int selected = -1;
    private int entityModes;

    private AreaPlaneUi(AreaPlaneBlockEntity host, Player player) {
        this.host = host;
        this.player = player;
        client = player.level().isClientSide();
        filters = new ItemStack[host.getConfiguration().getSlots()];
        Arrays.fill(filters, ItemStack.EMPTY);
        ghost = new PlaneGhostSlot(player);
        root.setId("area_plane");
        canvas.setId("area_plane_canvas");
        at(canvas, 0, 0, WIDTH, HEIGHT);
        canvas.style(style -> style.backgroundTexture(GuiTextureGroup.of(new ColorRectTexture(0xFFF3FAFC), new ColorBorderTexture(-1, 0xFF35AFC4))));
        root.addChild(canvas);
        label(canvas, host.getBlockState().getBlock().getName(), 10, 8, 380, 16);
        Button close = new Button();
        close.setText(Component.literal("×"));
        close.setOnServerClick(event -> player.closeContainer());
        canvas.addChild(at(close, WIDTH - 28, 6, 20, 18));

        StructurePreviewSceneElement scene = new StructurePreviewSceneElement();
        scene.setId("area_plane_scene");
        scene.style(style -> style.backgroundTexture(new ColorRectTexture(0xFF172831)));
        canvas.addChild(at(scene, 8, 30, 278, 170));
        label(canvas, Component.translatable("ui.data_energistics.area_plane.scene_hint"), 10, 203, 274, 14);

        editor.setId("area_plane_position_editor");
        editor.style(style -> style.backgroundTexture(GuiTextureGroup.of(new ColorRectTexture(0xFFFBFDFE), new ColorBorderTexture(-1, 0xFF97C5CE))));
        canvas.addChild(at(editor, 294, 30, 138, 170));
        editor.addChild(at(position, 6, 6, 126, 29));
        styleLabel(position);
        ghost.setId("area_plane_filter");
        SharedBackpackStyles.standalone(ghost);
        editor.addChild(at(ghost, 8, 41, 18, 18));
        editor.addChild(at(material, 32, 39, 100, 32));
        styleLabel(material);
        label(editor, Component.translatable("ui.data_energistics.area_plane.filter_hint"), 6, 76, 126, 28);
        mode.setId("area_plane_placement_mode");
        mode.setCandidates(ObjectList.of(PlanePlacementMode.values()));
        mode.setSelected(PlanePlacementMode.BLOCK, false);
        mode.setCandidateUIProvider(value -> {
            Label candidate = new Label();
            // LDLib2 requests a null display candidate while constructing the selector.
            candidate.setValue(value == null ? Component.empty() : Component.translatable(modeKey(value)));
            candidate.setAllowHitTest(false);
            candidate.layout(layout -> layout.height(18).widthPercent(100));
            styleLabel(candidate);
            return candidate;
        });
        mode.selectorStyle(style -> style.closeAfterSelect(true).maxItemCount(2).scrollerViewHeight(36));
        editor.addChild(at(mode, 6, 111, 126, 20));
        mode.setDisplay(host.layout().variant().getOperation() == AreaPlaneVariant.Operation.FORMATION);
        editor.setDisplay(false);
        label(canvas, Component.translatable("ui.data_energistics.area_plane.select_hint"), 298, 205, 132, 27);
        canvas.addChild(at(status, 8, 225, 170, 46));
        styleLabel(status);
        InventorySlots inventory = new InventorySlots();
        inventory.setId("area_plane_inventory");
        SharedBackpackStyles.inventory(inventory);
        canvas.addChild(at(inventory, 210, 216, 162, 76));

        setFilter = root.addRPCEvent(RPCEventBuilder.simple(Integer.class, ItemStack.class, (index, stack) -> {
            if (!client && accepts(index)) host.getConfiguration().setFilter(index, stack);
        }));
        setMode = root.addRPCEvent(RPCEventBuilder.simple(Integer.class, Integer.class, (index, value) -> {
            if (!client && accepts(index) && value >= 0 && value < PlanePlacementMode.values().length &&
                    host.layout().variant().getOperation() == AreaPlaneVariant.Operation.FORMATION) {
                host.getConfiguration().setMode(index, PlanePlacementMode.values()[value]);
            }
        }));
        ghost.registerValueListener(stack -> {
            if (client && selected >= 0) setFilter.send(selected, stack.copyWithCount(1));
        });
        mode.setOnValueChanged(value -> {
            if (client && selected >= 0) setMode.send(selected, value.ordinal());
        });
        preview = client ? new LdlibAreaPlanePreview(scene, host.layout().variant(), this::select) : null;
        if (preview != null) preview.refresh(filters, entityModes);
    }

    public static ModularUI create(AreaPlaneBlockEntity host, Player player) {
        AreaPlaneUi view = new AreaPlaneUi(host, player);
        ModularUI ui = ModularUI.of(UI.of(view.root, ObjectList.of(DEStylesheets.SHARED_BACKPACK.get()), view::resize), player);
        for (int index = 0; index < view.filters.length; index++) {
            int slot = index;
            var sync = new SyncValue<>("plane_filter_" + slot, ItemStack.class, ItemStack.EMPTY);
            sync.setToSync(!view.client);
            sync.setAcceptSync(view.client);
            sync.addListener(stack -> {
                if (view.client) view.applyFilter(slot, stack);
            });
            if (!view.client) sync.setValueProvider(() -> host.getConfiguration().filterStack(slot));
            ui.syncManager.registerSyncValue(sync);
        }
        var modes = new SyncValue<>("plane_entity_modes", Integer.class, 0);
        modes.setToSync(!view.client);
        modes.setAcceptSync(view.client);
        modes.addListener(value -> {
            if (view.client) view.applyModes(value);
        });
        if (!view.client) modes.setValueProvider(host.getConfiguration()::modeMask);
        ui.syncManager.registerSyncValue(modes);
        var state = new SyncValue<>("plane_status", Integer.class, 0);
        state.setToSync(!view.client);
        state.setAcceptSync(view.client);
        state.addListener(value -> view.status.setValue(Component.translatable("ui.data_energistics.area_plane." + switch (value) {
            case 1 -> "online";
            case 2 -> "faulted";
            default -> "offline";
        })));
        if (!view.client) state.setValueProvider(() -> host.isFaulted() ? 2 : host.getMainNode().isOnline() ? 1 : 0);
        ui.syncManager.registerSyncValue(state);
        view.status.setValue(Component.translatable("ui.data_energistics.area_plane.offline"));
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
        position.setValue(Component.translatable("ui.data_energistics.area_plane.position", target.getX(), target.getY(), target.getZ()));
        editor.setDisplay(true);
        updateEditor();
    }

    private void applyFilter(int slot, ItemStack stack) {
        filters[slot] = stack;
        if (slot == selected) updateEditor();
        if (preview != null) preview.refresh(filters, entityModes);
    }

    private void applyModes(int mask) {
        entityModes = mask;
        if (selected >= 0) updateEditor();
        if (preview != null) preview.refresh(filters, entityModes);
    }

    private void updateEditor() {
        ItemStack stack = filters[selected];
        ghost.setValue(stack, false);
        material.setValue(stack.isEmpty() ? Component.translatable("ui.data_energistics.area_plane.disabled") : stack.getHoverName());
        mode.setSelected((entityModes & 1 << selected) == 0 ? PlanePlacementMode.BLOCK : PlanePlacementMode.ITEM, false);
    }

    private Size resize(Size viewport) {
        float scale = Math.clamp(Math.min((viewport.getWidth() - 16f) / WIDTH, (viewport.getHeight() - 16f) / HEIGHT), 0.5f, 1f);
        int width = Math.round(WIDTH * scale);
        int height = Math.round(HEIGHT * scale);
        root.layout(layout -> layout.width(width).height(height));
        canvas.style(style -> style.transform2D(Transform2D.identity().pivot(0, 0).scale(scale)));
        return Size.of(width, height);
    }

    private static String modeKey(PlanePlacementMode mode) {
        return "ui.data_energistics.area_plane.mode_" + (mode == PlanePlacementMode.BLOCK ? "block" : "item");
    }

    private static void label(UIElement parent, Component text, int x, int y, int width, int height) {
        Label label = new Label();
        label.setValue(text);
        label.setAllowHitTest(false);
        styleLabel(label);
        parent.addChild(at(label, x, y, width, height));
    }

    private static void styleLabel(Label label) {
        label.textStyle(style -> style.adaptiveWidth(false).adaptiveHeight(false).fontSize(9)
                .textWrap(TextWrap.WRAP).textColor(TEXT).textShadow(false));
    }

    private static <T extends UIElement> T at(T element, int x, int y, int width, int height) {
        element.layout(layout -> layout.positionType(TaffyPosition.ABSOLUTE).left(x).top(y).width(width).height(height));
        return element;
    }
}
