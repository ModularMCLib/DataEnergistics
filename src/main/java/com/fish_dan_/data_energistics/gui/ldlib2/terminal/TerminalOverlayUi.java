package com.fish_dan_.data_energistics.gui.ldlib2.terminal;

import com.fish_dan_.data_energistics.client.terminal.TerminalClientPreferences;
import com.fish_dan_.data_energistics.client.terminal.TerminalViewportLayout;
import com.fish_dan_.data_energistics.client.terminal.storage.TerminalStorageIndex;
import com.fish_dan_.data_energistics.common.anvil.DigitalAnvilUpgrade;
import com.fish_dan_.data_energistics.common.terminal.player.workstation.TerminalWorkspace;
import com.fish_dan_.data_energistics.gui.ldlib2.terminal.encoding.TerminalPatternUploadUi;
import com.fish_dan_.data_energistics.integration.viewer.xei.transfer.PatternProviderRecipeTypeNames;

import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollDisplay;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollerMode;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Scroller;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyTypes;
import appeng.api.stacks.GenericStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.StonecutterRecipe;

import dev.vfyjxf.taffy.style.TaffyPosition;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.Reference2BooleanOpenHashMap;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;
import java.util.Objects;
import java.util.function.BiConsumer;

/**
 * Binds server snapshots to the Java/LSS composition; native slots and cursor stay with the host.
 */
@NullMarked
public final class TerminalOverlayUi {

    public final UI ui = TerminalUiLayout.create();
    private final TerminalClientPreferences preferences;
    private final RegistryAccess registries;
    private final BiConsumer<String, CompoundTag> request;
    private final UIElement window = element(ui.rootElement, "window");
    private final Object2ObjectMap<String, UIElement> workspaces = new Object2ObjectLinkedOpenHashMap<>();
    private final Object2ObjectMap<String, UIElement> popups = new Object2ObjectLinkedOpenHashMap<>();
    private final ObjectList<String> popupStack = new ObjectArrayList<>();
    private final ObjectList<TerminalResourceCell> storage = new ObjectArrayList<>();
    private final TerminalStorageIndex storageIndex = new TerminalStorageIndex();
    private ObjectList<TerminalStorageIndex.Entry> boundStorage = ObjectImmutableList.of();
    private boolean boundConnected;
    private int boundCellCount = -1;
    private final ObjectList<TerminalResourceCell> inputs = new ObjectArrayList<>();
    private final ObjectList<TerminalResourceCell> outputs = new ObjectArrayList<>();
    private final ObjectList<TerminalResourceCell> encodedPatterns = new ObjectArrayList<>();
    private final TerminalPatternUploadUi uploadUi;
    private boolean pendingEncode;
    private final ObjectList<TerminalResourceCell> stonecuttingRecipes = new ObjectArrayList<>();
    private final ObjectList<TerminalResourceCell> pickup = new ObjectArrayList<>();
    private final ObjectList<TerminalResourceCell> insert = new ObjectArrayList<>();
    private final ObjectList<String> types = new ObjectArrayList<>();
    private final Reference2BooleanOpenHashMap<UIElement> modalHitStates = new Reference2BooleanOpenHashMap<>();
    private final Scroller.Vertical scrollbar = TerminalUiLayout.require(window, "storage_scroll", Scroller.Vertical.class);
    private final Scroller.Vertical processingScrollbar;
    private final Scroller.Vertical stonecuttingScrollbar;
    private TerminalViewportLayout viewport = TerminalViewportLayout.compute(320, 240, 0.025F, 0.033333335F, 150, 168, true);
    private CompoundTag snapshot = new CompoundTag();
    private String workspace = "crafting";
    private boolean encoding;
    private String sort = "name";
    private String filter = "all";
    private boolean descending;
    private int typeIndex;
    private int offset;
    private @Nullable AEKey selected;
    private @Nullable GhostTarget quantityTarget;
    private CompoundTag previousMagnet = new CompoundTag();
    private long[] previousFrequencies = new long[0];
    private long previousFrequency = Long.MIN_VALUE;
    private ObjectList<TerminalViewportLayout.Cell> positionedStorage = ObjectImmutableList.of();
    private int screenWidth;
    private int screenHeight;
    private boolean adjusting;
    private boolean resizing;
    private double dragStartX;
    private double dragStartY;
    private int startX;
    private int startY;
    private int startWidth;
    private int startHeight;
    private boolean geometryDirty = true;
    private boolean visible;
    private boolean visibilitySent;
    private boolean entryPressed;
    private boolean entryDragging;
    private int processingScrollRow;
    private int stonecuttingScrollRow;

    public TerminalOverlayUi(TerminalClientPreferences preferences, BiConsumer<String, CompoundTag> request, Runnable openLargeTerminal) {
        this.preferences = preferences;
        this.request = request;
        visible = preferences.open;
        registries = Objects.requireNonNull(Minecraft.getInstance().level).registryAccess();
        types.add("all");
        for (var type : AEKeyTypes.getAll()) types.add(type.getId().toString());
        field(window, "search").textFieldStyle(style -> style.placeholder(text("search")));
        field(window, "search").style(style -> style.tooltips(text("search_syntax")));
        window.setVisible(visible);
        element(ui.rootElement, "entry").setVisible(!visible);
        Button entry = TerminalUiLayout.require(ui.rootElement, "entry", Button.class);
        entry.style(style -> style.tooltips(text("entry_gestures")));
        entry.setOnClick(event -> {
            if (event.button == 0) visible(true);
            else if (event.button == 1) openLargeTerminal.run();
        });
        Button drag = TerminalUiLayout.require(window, "drag", Button.class);
        drag.style(style -> style.tooltips(text("drag")));
        drag.setOnClick(event -> {
            if (event.button == 1) visible(false);
        });
        tooltip(window, "resize", text("resize"));
        field(window, "search").setTextResponder(value -> view(true));
        scrollbar.setRange(0, 1);
        scrollbar.setOnValueChanged(value -> {
            int next = Math.round(value * viewport.maximumFirstRow(storageIndex.projected().size())) * viewport.columns();
            if (next != offset) {
                offset = next;
                view(false);
            }
        });
        element(window, "storage").addEventListener(UIEvents.MOUSE_WHEEL, event -> {
            scrollbar.scrollByWheel(event.deltaY);
            event.stopPropagation();
        });
        button(window, "sort", () -> {
            sort = switch (sort) {
                case "name" -> "amount";
                case "amount" -> "mod";
                default -> "name";
            };
            view(true);
        });
        button(window, "direction", () -> {
            descending = !descending;
            view(true);
        });
        button(window, "display", () -> {
            filter = switch (filter) {
                case "all" -> "stored";
                case "stored" -> "craftable";
                default -> "all";
            };
            view(true);
        });
        button(window, "type", () -> {
            typeIndex = (typeIndex + 1) % types.size();
            view(true);
        });
        button(window, "network", () -> open("network"));
        button(window, "magnet", () -> {
            if (snapshot.getBoolean("magnet_unlocked")) open("magnet");
        });
        button(window, "tasks", () -> networkAction("cpu_list", new CompoundTag()));
        button(window, "insert", () -> networkAction("insert", new CompoundTag()));
        for (TerminalWorkspace mode : TerminalWorkspace.values()) {
            UIElement root = TerminalUiLayout.page(mode);
            workspaces.put(mode.id(), root);
            element(window, "workspaces").addChild(root);
            Button tab = TerminalUiLayout.require(window, "tab_" + mode.id(), Button.class);
            tab.style(style -> style.tooltips(text("page_gestures", text("workspace." + mode.id()))));
            tab.setOnClick(event -> {
                preferences.folded = event.button == 1;
                if (event.button == 0 && !workspace.equals(mode.id())) request.accept("workspace", string("id", mode.id()));
                geometry(screenWidth, screenHeight);
                preferences.save();
            });
            if (mode.supportsEncoding()) {
                button(root, "encoding", () -> request.accept("toggle_encoding", new CompoundTag()));
                if (mode == TerminalWorkspace.CRAFTING) {
                    button(root, "substitutes", () -> request.accept("substitutes", new CompoundTag()));
                    button(root, "fluid_substitutes", () -> request.accept("fluid_substitutes", new CompoundTag()));
                } else if (mode == TerminalWorkspace.SMITHING) {
                    button(root, "substitutes", () -> request.accept("substitutes", new CompoundTag()));
                } else if (mode == TerminalWorkspace.PROCESSING) {
                    button(root, "encoding_info", () -> open("encoding_info"));
                    button(root, "transfer_info", () -> open("transfer_info"));
                    button(root, "transfer_processing", () -> {
                        if (encoding) {
                            pendingEncode = true;
                            request.accept("result", new CompoundTag());
                        } else networkAction("transfer_processing", new CompoundTag());
                    });
                }
            } else {
                field(root, "name").setTextRegexValidator(".{0,50}").setTextResponder(value -> request.accept("name", string("value", value)));
                button(root, "boost_down", () -> boost(-1));
                button(root, "boost_up", () -> boost(1));
            }
        }
        for (String name : new String[] { "network", "magnet", "filters", "quantity", "encoding_info", "transfer_info", "upload" }) {
            UIElement root = TerminalUiLayout.popup(name);
            popups.put(name, root);
            root.setVisible(false);
            ui.rootElement.addChild(root);
            label(root, "popup_title").setText(text(name));
            button(root, "back", this::closePopup);
            root.select(".terminal-list", ScrollerView.class).forEach(list -> list.scrollerStyle(style -> style.mode(ScrollerMode.VERTICAL).horizontalScrollDisplay(ScrollDisplay.NEVER)
                    .verticalScrollDisplay(ScrollDisplay.AUTO)));
        }
        uploadUi = new TerminalPatternUploadUi(popups.get("upload"), registries, this::networkAction);
        UIElement encodingOptions = popups.get("encoding_info");
        button(encodingOptions, "record_type", () -> request.accept("record_type", new CompoundTag()));
        processingScrollbar = TerminalUiLayout.require(workspaces.get("processing"), "pattern_scroll", Scroller.Vertical.class);
        configureProcessingScrollbar();
        for (String host : new String[] { "inputs", "outputs" })
            element(workspaces.get("processing"), host).addEventListener(UIEvents.MOUSE_WHEEL, event -> {
                scrollProcessing(event.deltaY > 0 ? -1 : 1);
                event.stopPropagation();
            });
        stonecuttingScrollbar = TerminalUiLayout.require(workspaces.get("stonecutting"), "recipe_scroll", Scroller.Vertical.class);
        configureStonecuttingScrollbar();
        element(workspaces.get("stonecutting"), "recipes").addEventListener(UIEvents.MOUSE_WHEEL, event -> {
            scrollStonecutting(event.deltaY > 0 ? -1 : 1);
            event.stopPropagation();
        });
        UIElement magnet = popups.get("magnet");
        for (String mode : new String[] { "OFF", "INVENTORY", "NETWORK", "PICKUP_NETWORK" }) {
            String id = "mode_" + mode.toLowerCase(Locale.ROOT);
            button(magnet, id, () -> request.accept("magnet_mode", string("value", mode)));
        }
        button(magnet, "restock", () -> request.accept("magnet_restock", new CompoundTag()));
        button(magnet, "filters", () -> open("filters"));
        UIElement filters = popups.get("filters");
        for (String action : new String[] { "pickup_whitelist", "insert_whitelist", "filter_copy_up", "filter_copy_down", "filter_swap" }) {
            button(filters, action, () -> request.accept(action, new CompoundTag()));
        }
        createCells(element(filters, "pickup"), pickup, 27, 9, (index, event) -> ghost("pickup", index, event.button));
        createCells(element(filters, "insert"), insert, 27, 9, (index, event) -> ghost("insert", index, event.button));
        for (TerminalResourceCell cell : pickup) cell.configurable(true);
        for (TerminalResourceCell cell : insert) cell.configurable(true);
        UIElement quantity = popups.get("quantity");
        field(quantity, "amount").setNumbersOnlyLong(1, Long.MAX_VALUE).setText("1", false);
        button(quantity, "minus", () -> amount(-1));
        button(quantity, "plus", () -> amount(1));
        button(quantity, "apply_quantity", this::applyQuantity);
        refreshWorkCells();
    }

    private void configureProcessingScrollbar() {
        int maximumRow = 27 - 3;
        processingScrollbar.setRange(0, 1);
        processingScrollbar.setScrollBarSize(100F * 15 / 54);
        processingScrollbar.scrollerStyle(style -> style.scrollDelta(1F / maximumRow));
        processingScrollbar.setOnValueChanged(value -> setProcessingScrollRow(Math.round(value * maximumRow), false));
        setProcessingScrollRow(processingScrollRow, true);
    }

    private void configureStonecuttingScrollbar() {
        stonecuttingScrollbar.setRange(0, 1);
        stonecuttingScrollbar.setOnValueChanged(value -> {
            int maximumRow = maximumStonecuttingScrollRow();
            setStonecuttingScrollRow(Math.round(value * maximumRow), false);
            refreshStonecuttingRecipes();
        });
    }

    private void scrollProcessing(int rows) {
        setProcessingScrollRow(processingScrollRow + rows, true);
    }

    private void setProcessingScrollRow(int row, boolean updateScrollbar) {
        int maximumRow = 27 - 3;
        processingScrollRow = Math.clamp(row, 0, maximumRow);
        if (updateScrollbar) processingScrollbar.setNormalizedValue((float) processingScrollRow / maximumRow, false);
        for (int index = 0; index < inputs.size(); index++) {
            int slotRow = index / 3;
            TerminalUiLayout.at(inputs.get(index), index % 3 * 18, (slotRow - processingScrollRow) * 18, 18, 18);
            inputs.get(index).setVisible(slotRow >= processingScrollRow && slotRow < processingScrollRow + 3);
        }
        for (int index = 0; index < outputs.size(); index++) {
            TerminalUiLayout.at(outputs.get(index), 0, (index - processingScrollRow) * 18, 18, 18);
            outputs.get(index).setVisible(index >= processingScrollRow && index < processingScrollRow + 3);
        }
    }

    private void scrollStonecutting(int rows) {
        setStonecuttingScrollRow(stonecuttingScrollRow + rows, true);
        refreshStonecuttingRecipes();
    }

    private void setStonecuttingScrollRow(int row, boolean updateScrollbar) {
        int maximumRow = maximumStonecuttingScrollRow();
        stonecuttingScrollRow = Math.clamp(row, 0, maximumRow);
        stonecuttingScrollbar.setActive(maximumRow > 0);
        stateClass(stonecuttingScrollbar, "inactive-scroll", maximumRow == 0);
        stonecuttingScrollbar.setScrollBarSize(1500F / 48);
        stonecuttingScrollbar.scrollerStyle(style -> style.scrollDelta(maximumRow == 0 ? 1F : 1F / maximumRow));
        if (updateScrollbar) stonecuttingScrollbar.setNormalizedValue(maximumRow == 0 ? 0 : (float) stonecuttingScrollRow / maximumRow, false);
        stonecuttingScrollbar.selfAndAllChildren().forEach(element -> element.setAllowHitTest(maximumRow > 0));
    }

    private int maximumStonecuttingScrollRow() {
        int totalRows = (snapshot.getInt("recipe_count") + 3) / 4;
        return Math.max(0, totalRows - 2);
    }

    private void boost(int direction) {
        int step = UIElement.isCtrlDown() ? 100 : UIElement.isShiftDown() ? 10 : 1;
        request.accept("boost", number("value", Math.clamp(snapshot.getCompound("work").getInt("boost") + (long) direction * step, 0, 254)));
    }

    private void amount(int direction) {
        TextField field = field(popups.get("quantity"), "amount");
        long current = quantityAmount();
        long step = UIElement.isCtrlDown() ? 100 : UIElement.isShiftDown() ? 10 : 1;
        field.setText(Long.toString(direction > 0 ? Math.min(Long.MAX_VALUE - step, current) + step : Math.max(1, current - step)), false);
    }

    private void applyQuantity() {
        if (selected == null || quantityTarget == null) return;
        drop(quantityTarget, new GenericStack(selected, quantityAmount()));
        closePopup();
    }

    private long quantityAmount() {
        String value = field(popups.get("quantity"), "amount").getValue();
        return value.isEmpty() ? 1 : Long.parseLong(value);
    }

    public void view(boolean reset) {
        ObjectList<TerminalStorageIndex.Entry> previous = storageIndex.projected();
        AEKey anchor = !reset && offset < previous.size() ? previous.get(offset).key() : null;
        storageIndex.project(field(window, "search").getValue(), sort, descending, filter, types.get(typeIndex));
        ObjectList<TerminalStorageIndex.Entry> projected = storageIndex.projected();
        if (reset) offset = 0;
        else if (previous != projected && anchor != null) {
            for (int index = 0; index < projected.size(); index++) {
                if (projected.get(index).key().equals(anchor)) {
                    offset = index;
                    break;
                }
            }
        }
        offset = viewport.clampOffset(offset, projected.size());
        fillStorage();
        updateScrollbar();
        Component page = Component.literal((projected.isEmpty() ? 0 : offset + 1) + "–" + Math.min(offset + storage.size(), projected.size()) + "/" + projected.size());
        tooltip(window, "search", text("search_syntax").copy().append("\n").append(storageIndex.pending() ? text("storage_loading", storageIndex.loaded(), storageIndex.total()) : page));
        tooltip(window, "sort", text("sort." + sort));
        tooltip(window, "direction", text(descending ? "descending" : "ascending"));
        tooltip(window, "display", text("display." + filter));
        tooltip(window, "type", text("type", types.get(typeIndex)));
        stateClass(element(window, "sort"), "sort-amount", sort.equals("amount"));
        stateClass(element(window, "direction"), "descending", descending);
        stateClass(element(window, "display"), "stored-only", filter.equals("stored"));
        stateClass(element(window, "display"), "craftable-only", filter.equals("craftable"));
    }

    public void apply(CompoundTag value) {
        boolean rebuild = !workspace.equals(value.getString("workspace")) || encoding != value.getBoolean("encoding");
        snapshot = value.copy();
        workspace = value.getString("workspace");
        encoding = value.getBoolean("encoding");
        if (rebuild) refreshWorkCells();
        for (var entry : workspaces.object2ObjectEntrySet()) {
            entry.getValue().setVisible(entry.getKey().equals(workspace));
            UIElement tab = element(window, "tab_" + entry.getKey());
            stateClass(tab, "selected", !preferences.folded && entry.getKey().equals(workspace));
        }
        CompoundTag work = value.getCompound("work");
        boolean ghostInputs = encoding || workspace.equals("processing");
        setCells(inputs, work.getList(ghostInputs ? "ghost_inputs" : "items", Tag.TAG_COMPOUND), ghostInputs);
        setCells(outputs, work.getList("ghost_outputs", Tag.TAG_COMPOUND), true);
        if (!encodedPatterns.isEmpty()) encodedPatterns.getFirst().entry(physical(work.getList("patterns", Tag.TAG_COMPOUND).getCompound(1)), false);
        UIElement active = workspaces.get(workspace);
        if (workspace.equals("stonecutting")) {
            int selected = work.getInt("recipe");
            if (selected < stonecuttingScrollRow * 4) setStonecuttingScrollRow(selected / 4, true);
            else if (selected >= (stonecuttingScrollRow + 2) * 4) setStonecuttingScrollRow(selected / 4 - 1, true);
            refreshStonecuttingRecipes();
        } else if (!workspace.equals("processing")) {
            TerminalResourceCell result = (TerminalResourceCell) element(active, "result").getChildren().getFirst();
            result.entry(physical(value.getCompound("result")), false);
            if (workspace.equals("anvil")) {
                if (!field(active, "name").isFocused()) field(active, "name").setText(work.getString("name"), false);
                label(active, "boost").setText(Component.literal("+" + work.getInt("boost")));
                boolean levels = DigitalAnvilUpgrade.UNRESTRICTED_LEVELS.enabled(value.getInt("anvil_upgrades"));
                element(active, "boost_down").setActive(levels && work.getInt("boost") > 0);
                element(active, "boost_up").setActive(levels && work.getInt("boost") < 254);
                result.style(style -> style.tooltips(text("anvil_cost", value.getLong("anvil_energy"), value.getLong("anvil_levels"))));
            } else result.setActive(value.getBoolean("connected"));
        }
        if (!workspace.equals("anvil")) {
            tooltip(active, "encoding", text(encoding ? "encoding_on" : "encoding_off"));
            stateClass(element(active, "encoding"), "actual-mode", !encoding);
            boolean substitutes = value.getBoolean("substitutes");
            boolean fluidSubstitutes = value.getBoolean("fluid_substitutes");
            if (workspace.equals("crafting") || workspace.equals("smithing")) {
                stateClass(element(active, "substitutes"), "option-enabled", substitutes);
                tooltip(active, "substitutes", text(substitutes ? "substitutes_on" : "substitutes_off"));
            }
            if (workspace.equals("crafting")) {
                stateClass(element(active, "fluid_substitutes"), "option-enabled", fluidSubstitutes);
                tooltip(active, "fluid_substitutes", text(fluidSubstitutes ? "fluid_substitutes_on" : "fluid_substitutes_off"));
            }
            if (workspace.equals("processing")) {
                boolean recordType = value.getBoolean("record_recipe_type");
                stateClass(element(popups.get("encoding_info"), "record_type"), "option-enabled", recordType);
                label(popups.get("encoding_info"), "record_type_text").setText(Component.translatable(
                        "button.data_energistics.pattern_encoding_recipe_type_toggle." + (recordType ? "enabled" : "disabled")));
                label(popups.get("encoding_info"), "encoding_details")
                        .setText(text("encoding_details", text("workspace." + workspace), value.getLong("blank_patterns")));
                CompoundTag context = work.getCompound("context");
                String type = context.getString("type");
                Component transfer = text("transfer_details", type.isEmpty() ? text("context_none") :
                        PatternProviderRecipeTypeNames.resolveDisplayName(ResourceLocation.parse(type)),
                        context.getString("recipe").isEmpty() ? "—" : context.getString("recipe"));
                label(popups.get("transfer_info"), "transfer_details").setText(transfer);
                tooltip(active, "transfer_info", transfer);
                tooltip(active, "encoding_info", text("encoding_info"));
            }
        }
        if (workspace.equals("processing")) {
            element(active, "transfer_processing").setVisible(!encoding);
            element(active, "transfer_processing").setActive(value.getBoolean("connected") && value.getBoolean("transfer_supported"));
            tooltip(active, "transfer_processing", text(!value.getBoolean("transfer_supported") ? "message.unsupported_container" :
                    !value.getBoolean("connected") ? "message.offline" : "transfer_processing"));
        }
        storageIndex.apply(value, registries);
        view(false);
        stateClass(element(window, "network"), "offline", !value.getBoolean("connected"));
        tooltip(window, "network", text("reason." + value.getString("reason")));
        if (!value.getString("message").isEmpty())
            tooltip(window, "network", text("message." + value.getString("message")));
        element(window, "insert").setActive(value.getBoolean("connected"));
        element(window, "tasks").setActive(value.getBoolean("connected"));
        element(window, "magnet").setActive(value.getBoolean("magnet_unlocked"));
        label(popups.get("network"), "connection_status").setText(text("reason." + value.getString("reason")));
        refreshNetwork();
        refreshMagnet();
        uploadUi.apply(value);
        if (pendingEncode && value.getString("action").equals("result")) {
            pendingEncode = false;
            if (value.getString("message").equals("encoded") && encoding) open("upload");
        }
        if (!visibilitySent) {
            visibilitySent = true;
            if (!visible) request.accept("visible", booleanValue("value", false));
        }
    }

    private void refreshWorkCells() {
        inputs.clear();
        outputs.clear();
        encodedPatterns.clear();
        UIElement root = workspaces.get(workspace);
        TerminalWorkspace mode = TerminalWorkspace.byId(workspace);
        int inputCount = mode == TerminalWorkspace.PROCESSING ? mode.patternInputSlots() : mode.inputSlots();
        createCells(element(root, "inputs"), inputs, inputCount, mode == TerminalWorkspace.ANVIL ? 2 : 3,
                (index, event) -> {
                    if (encoding || mode == TerminalWorkspace.PROCESSING) ghost("inputs", index, event.button);
                    else slot("inputs", index, event.button);
                });
        for (TerminalResourceCell cell : inputs) cell.addClass("embedded-cell");
        if (mode == TerminalWorkspace.ANVIL) TerminalUiLayout.at(inputs.get(1), 36, 0, 18, 18);
        createCells(element(root, "outputs"), outputs,
                mode == TerminalWorkspace.PROCESSING && encoding ? mode.patternOutputSlots() : 0, 1,
                (index, event) -> ghost("outputs", index, event.button));
        for (TerminalResourceCell cell : inputs) cell.configurable(encoding || mode == TerminalWorkspace.PROCESSING);
        for (TerminalResourceCell cell : outputs) cell.configurable(true);
        for (TerminalResourceCell cell : outputs) cell.addClass("embedded-cell");
        if (mode.supportsEncoding()) {
            createCells(element(root, "encoded"), encodedPatterns, encoding ? 1 : 0, 1, (index, event) -> {
                if (event.button == 0 && (encodedPatterns.getFirst().stack() == null || UIElement.isCtrlDown())) {
                    pendingEncode = true;
                    request.accept("result", new CompoundTag());
                } else slot("encoded", 1, event.button);
            });
            for (TerminalResourceCell cell : encodedPatterns) cell.actionHint(text("encoded_gestures"));
            if (mode != TerminalWorkspace.PROCESSING)
                for (TerminalResourceCell cell : encodedPatterns) cell.addClass("embedded-cell");
            element(root, "encoded").setVisible(encoding);
            if (mode == TerminalWorkspace.CRAFTING || mode == TerminalWorkspace.SMITHING)
                element(root, "substitutes").setVisible(encoding);
            if (mode == TerminalWorkspace.CRAFTING) element(root, "fluid_substitutes").setVisible(encoding);
            if (mode == TerminalWorkspace.PROCESSING) {
                element(root, "encoding_info").setVisible(encoding);
                element(root, "transfer_info").setVisible(encoding);
                element(root, "transfer_processing").setVisible(!encoding);
            }
        }
        if (mode != TerminalWorkspace.STONECUTTING && mode != TerminalWorkspace.PROCESSING) {
            ObjectList<TerminalResourceCell> result = new ObjectArrayList<>();
            createCells(element(root, "result"), result, 1, 1, (index, event) -> {
                if (event.button != 0) return;
                CompoundTag data = new CompoundTag();
                data.putBoolean("shift", UIElement.isShiftDown());
                pendingEncode = encoding;
                request.accept("result", data);
            });
            if (mode != TerminalWorkspace.PROCESSING) result.getFirst().addClass("embedded-cell");
            result.getFirst().actionHint(encoding ? text("encode_action") : null);
            element(root, "result").setVisible(mode != TerminalWorkspace.PROCESSING || encoding);
        }
        if (mode == TerminalWorkspace.STONECUTTING) {
            createCells(element(root, "recipes"), stonecuttingRecipes, 8, 4, (index, event) -> {
                if (event.button == 0) {
                    int selected = stonecuttingScrollRow * 4 + index;
                    if (selected < snapshot.getInt("recipe_count")) request.accept("recipe", number("index", selected));
                }
            });
            setStonecuttingScrollRow(stonecuttingScrollRow, true);
        }
        if (mode == TerminalWorkspace.PROCESSING) setProcessingScrollRow(processingScrollRow, true);
    }

    private void refreshStonecuttingRecipes() {
        var level = Objects.requireNonNull(Minecraft.getInstance().level);
        GenericStack ingredient = inputs.getFirst().stack();
        ItemStack input = ingredient != null && ingredient.what() instanceof AEItemKey key ? key.toStack(1) : ItemStack.EMPTY;
        SingleRecipeInput recipeInput = new SingleRecipeInput(input);
        ObjectList<RecipeHolder<StonecutterRecipe>> recipes = new ObjectArrayList<>(level.getRecipeManager().getRecipesFor(RecipeType.STONECUTTING, recipeInput, level));
        recipes.sort(Comparator.comparing(recipe -> recipe.id().toString()));
        int selected = snapshot.getCompound("work").getInt("recipe");
        setStonecuttingScrollRow(stonecuttingScrollRow, true);
        int first = stonecuttingScrollRow * 4;
        for (int index = 0; index < stonecuttingRecipes.size(); index++) {
            TerminalResourceCell cell = stonecuttingRecipes.get(index);
            ItemStack output = first + index < recipes.size() ? recipes.get(first + index).value().assemble(recipeInput, registries) : ItemStack.EMPTY;
            cell.entry(output.isEmpty() ? null : new GenericStack(AEItemKey.of(output), output.getCount()), false);
            cell.setActive(!output.isEmpty());
            int row = index / 4;
            TerminalUiLayout.at(cell, index % 4 * 18, row * 18, 18, 18);
            cell.setVisible(true);
            stateClass(cell, "selected-recipe", first + index == selected && !output.isEmpty());
        }
    }

    private void slot(String inventory, int index, int button) {
        CompoundTag data = string("inventory", inventory);
        data.putInt("index", index);
        data.putInt("button", button);
        data.putBoolean("shift", UIElement.isShiftDown());
        request.accept("slot", data);
    }

    private void ghost(String inventory, int index, int button) {
        ObjectList<TerminalResourceCell> cells = switch (inventory) {
            case "pickup" -> pickup;
            case "insert" -> insert;
            case "outputs" -> outputs;
            default -> inputs;
        };
        GenericStack entry = cells.get(index).stack();
        if (button == 0 && UIElement.isShiftDown() && entry != null) {
            selected = entry.what();
            quantityTarget = new GhostTarget(rect(cells.get(index)), inventory, index);
            field(popups.get("quantity"), "amount").setText(Long.toString(entry.amount()), false);
            open("quantity");
            return;
        }
        CompoundTag data = string("inventory", inventory);
        data.putInt("index", index);
        data.putInt("button", button);
        data.putLong("amount", 1);
        request.accept("ghost", data);
    }

    private void refreshNetwork() {
        long currentFrequency = snapshot.contains("frequency") ? snapshot.getLong("frequency") : Long.MIN_VALUE;
        long[] frequencies = snapshot.getLongArray("frequencies");
        if (currentFrequency == previousFrequency && Arrays.equals(frequencies, previousFrequencies)) return;
        previousFrequency = currentFrequency;
        previousFrequencies = frequencies.clone();
        UIElement list = listHost(popups.get("network"), "frequencies");
        list.clearAllChildren();
        for (long frequency : snapshot.getLongArray("frequencies")) {
            Button button = new TerminalActionButton();
            button.addClass("frequency-row");
            button.addClass("terminal-control");
            button.layout(layout -> layout.widthPercent(100).height(14).flexShrink(0));
            button.textStyle(style -> style.adaptiveWidth(false).adaptiveHeight(false));
            button.text.layout(layout -> layout.widthPercent(100).heightPercent(100).marginAll(0));
            button.setText(Component.literal((snapshot.contains("frequency") && snapshot.getLong("frequency") == frequency ? "● " : "○ ") + frequency));
            button.setOnClick(event -> {
                CompoundTag data = new CompoundTag();
                data.putLong("value", frequency);
                request.accept("frequency", data);
            });
            list.addChild(button);
        }
    }

    private void refreshMagnet() {
        CompoundTag magnet = snapshot.getCompound("magnet");
        if (magnet.equals(previousMagnet)) return;
        previousMagnet = magnet.copy();
        UIElement root = popups.get("magnet");
        for (String mode : new String[] { "OFF", "INVENTORY", "NETWORK", "PICKUP_NETWORK" }) {
            Button control = TerminalUiLayout.require(root, "mode_" + mode.toLowerCase(Locale.ROOT), Button.class);
            control.setText(Component.literal(mode.equals(magnet.getString("mode")) ? "● " : "○ ").append(text("magnet_mode." + mode)));
        }
        TerminalUiLayout.require(root, "restock", Button.class).setText(text(magnet.getBoolean("restock") ? "restock_on" : "restock_off"));
        setCells(pickup, magnet.getList("pickup", Tag.TAG_COMPOUND), true);
        setCells(insert, magnet.getList("insert", Tag.TAG_COMPOUND), true);
        UIElement filters = popups.get("filters");
        TerminalUiLayout.require(filters, "pickup_whitelist", Button.class).setText(text("pickup_" + (magnet.getBoolean("pickup_whitelist") ? "whitelist" : "blacklist")));
        TerminalUiLayout.require(filters, "insert_whitelist", Button.class).setText(text("insert_" + (magnet.getBoolean("insert_whitelist") ? "whitelist" : "blacklist")));
    }

    public void geometry(int width, int height) {
        screenWidth = width;
        screenHeight = height;
        viewport = TerminalViewportLayout.compute(width, height, preferences.xPercent, preferences.yPercent,
                preferences.width, preferences.height, preferences.folded);
        preferences.xPercent = width <= viewport.width() ? 0 : (float) viewport.x() / (width - viewport.width());
        preferences.yPercent = height <= viewport.height() ? 0 : (float) viewport.y() / (height - viewport.height());
        preferences.width = viewport.width();
        preferences.height = viewport.height();
        window.layout(style -> style.leftPercent(width == 0 ? 0 : viewport.x() * 100F / width)
                .topPercent(height == 0 ? 0 : viewport.y() * 100F / height).width(viewport.width()).height(viewport.height()));
        TerminalUiLayout.resize(window, viewport);
        element(ui.rootElement, "entry").layout(style -> style.positionType(TaffyPosition.ABSOLUTE)
                .leftPercent(width == 0 ? 0 : viewport.x() * 100F / width)
                .topPercent(height == 0 ? 0 : viewport.y() * 100F / height).width(10).height(10).paddingAll(0));
        element(window, "workspaces").setVisible(!preferences.folded);
        for (TerminalWorkspace mode : TerminalWorkspace.values())
            stateClass(element(window, "tab_" + mode.id()), "selected", !preferences.folded && workspace.equals(mode.id()));
        int cellCount = viewport.cellCount();
        boolean gridChanged = !positionedStorage.equals(viewport.cells());
        if (storage.size() != cellCount) {
            createCells(element(window, "storage"), storage, cellCount, viewport.columns(), (index, event) -> {
                TerminalResourceCell cell = storage.get(index);
                if (cell.stack() == null) return;
                if (event.button == 2 || cell.craftable() && (UIElement.isCtrlDown() || cell.stack().amount() == 0)) {
                    networkAction("craft_request", key(cell.stack().what()));
                } else {
                    CompoundTag data = key(cell.stack().what());
                    data.putInt("button", event.button);
                    data.putBoolean("shift", UIElement.isShiftDown());
                    networkAction("storage", data);
                }
            });
            for (TerminalResourceCell cell : storage) cell.storageCell(true);
            boundCellCount = -1;
            fillStorage();
        }
        if (gridChanged) {
            for (int index = 0; index < storage.size(); index++) {
                TerminalViewportLayout.Cell cell = viewport.cells().get(index);
                TerminalUiLayout.at(storage.get(index), cell.column() * TerminalViewportLayout.CELL_SIZE,
                        cell.row() * TerminalViewportLayout.CELL_SIZE, TerminalViewportLayout.CELL_SIZE, TerminalViewportLayout.CELL_SIZE);
            }
            positionedStorage = viewport.cells();
        }
        for (var entry : popups.object2ObjectEntrySet()) {
            UIElement popup = entry.getValue();
            int popupX = Math.clamp(viewport.x() + viewport.width() + 3, 0, Math.max(0, width - 190));
            int popupY = Math.clamp(viewport.y(), 0, Math.max(0, height - TerminalUiLayout.popupHeight(entry.getKey())));
            popup.layout(style -> style.leftPercent(width == 0 ? 0 : popupX * 100F / width)
                    .topPercent(height == 0 ? 0 : popupY * 100F / height));
        }
        if (gridChanged && snapshot.contains("enabled")) {
            offset = viewport.clampOffset(offset, storageIndex.projected().size());
            view(false);
        }
        if (modal()) captureWindow();
        updateScrollbar();
        geometryDirty = true;
    }

    public boolean beginDrag(double x, double y, int button) {
        if (button != 0 || !popupStack.isEmpty()) return false;
        if (!visible && over(element(ui.rootElement, "entry"), x, y)) {
            entryPressed = true;
            entryDragging = false;
            dragStartX = x;
            dragStartY = y;
            startX = viewport.x();
            startY = viewport.y();
            return true;
        }
        if (!visible) return false;
        resizing = over(element(window, "resize"), x, y);
        adjusting = resizing || over(element(window, "drag"), x, y);
        if (!adjusting) return false;
        dragStartX = x;
        dragStartY = y;
        startX = viewport.x();
        startY = viewport.y();
        startWidth = preferences.width;
        startHeight = preferences.height;
        return true;
    }

    public boolean drag(double x, double y) {
        if (entryPressed) {
            if (!entryDragging && Math.hypot(x - dragStartX, y - dragStartY) >= 3) {
                entryDragging = true;
                adjusting = true;
                resizing = false;
            }
            if (entryDragging) {
                moveWindow(startX + (int) (x - dragStartX), startY + (int) (y - dragStartY));
                return true;
            }
            return false;
        }
        if (!adjusting) return false;
        if (resizing) {
            preferences.width = startWidth + (int) (x - dragStartX);
            preferences.height = startHeight + (int) (y - dragStartY);
        } else {
            moveWindow(startX + (int) (x - dragStartX), startY + (int) (y - dragStartY));
        }
        geometry(screenWidth, screenHeight);
        return true;
    }

    public boolean endDrag() {
        if (entryPressed) {
            entryPressed = false;
            if (!entryDragging) {
                visible(true);
                return true;
            }
            entryDragging = false;
        }
        if (!adjusting) return false;
        adjusting = false;
        preferences.save();
        return true;
    }

    public boolean adjusting() {
        return adjusting;
    }

    public boolean takeGeometryDirty() {
        boolean dirty = geometryDirty;
        geometryDirty = false;
        return dirty;
    }

    public boolean contains(double x, double y) {
        return visible ? over(element(window, "panel"), x, y) || sidebarContains(x, y) || over(element(window, "tabs"), x, y) ||
                (!popupStack.isEmpty() && over(popups.get(popupStack.getLast()), x, y)) : over(element(ui.rootElement, "entry"), x, y);
    }

    private boolean sidebarContains(double x, double y) {
        return x >= viewport.x() && x < viewport.x() + 15 && y >= viewport.y() && y < viewport.y() + 105;
    }

    public boolean modal() {
        return !popupStack.isEmpty();
    }

    public @Nullable TerminalWorkspace encodingWorkspace() {
        return visible && !preferences.folded && encoding && !modal() ? TerminalWorkspace.byId(workspace) : null;
    }

    public boolean inputAllowed(double x, double y) {
        return modal() ? over(popups.get(popupStack.getLast()), x, y) : contains(x, y);
    }

    private void captureWindow() {
        window.selfAndAllChildren().forEach(element -> {
            if (element instanceof TerminalActionButton button) button.resetInteraction();
            if (!modalHitStates.containsKey(element)) modalHitStates.put(element, element.isAllowHitTest());
            element.setAllowHitTest(false);
        });
    }

    private void releaseWindow() {
        for (var entry : modalHitStates.reference2BooleanEntrySet())
            entry.getKey().setAllowHitTest(entry.getBooleanValue());
        modalHitStates.clear();
    }

    private void updateScrollbar() {
        int maximumRow = viewport.maximumFirstRow(storageIndex.projected().size());
        scrollbar.setNormalizedValue(maximumRow == 0 ? 0 : (float) (offset / viewport.columns()) / maximumRow, false);
        scrollbar.scrollerStyle(style -> style.scrollDelta(maximumRow == 0 ? 1 : 1F / maximumRow)
                .scrollBarSize(1500F / viewport.scrollHeight()));
        scrollbar.scrollBar.setActive(maximumRow > 0);
        stateClass(scrollbar, "inactive-scroll", maximumRow == 0);
        scrollbar.selfAndAllChildren().forEach(element -> {
            boolean interactive = maximumRow > 0 && !element.hasClass("scrollbar-decoration");
            if (modal()) modalHitStates.put(element, interactive);
            element.setAllowHitTest(interactive);
        });
        if (modal()) captureWindow();
    }

    private void fillStorage() {
        ObjectList<TerminalStorageIndex.Entry> entries = storageIndex.projected();
        int end = Math.min(entries.size(), offset + storage.size());
        ObjectList<TerminalStorageIndex.Entry> next = new ObjectArrayList<>();
        for (int index = Math.min(offset, end); index < end; index++) next.add(entries.get(index));
        boolean connected = snapshot.getBoolean("connected");
        boolean force = boundCellCount != storage.size();
        if (!force && boundConnected == connected && next.equals(boundStorage)) return;
        for (int index = 0; index < storage.size(); index++) {
            TerminalStorageIndex.Entry entry = index < next.size() ? next.get(index) : null;
            TerminalStorageIndex.Entry previous = index < boundStorage.size() ? boundStorage.get(index) : null;
            if (force || !Objects.equals(entry, previous))
                storage.get(index).entry(entry == null ? null : entry.stack(), entry != null && entry.craftable());
            if (force || boundConnected != connected) storage.get(index).setActive(connected);
        }
        boundStorage = new ObjectImmutableList<>(next);
        boundConnected = connected;
        boundCellCount = storage.size();
    }

    public ObjectList<Rect2i> rectangles() {
        ObjectList<Rect2i> rectangles = new ObjectArrayList<>();
        if (!visible) {
            rectangles.add(rect(element(ui.rootElement, "entry")));
            return rectangles;
        }
        rectangles.add(new Rect2i(viewport.x() + 12, viewport.y(), viewport.width() - TerminalViewportLayout.TAB_WIDTH - 12, viewport.height()));
        rectangles.add(new Rect2i(viewport.x(), viewport.y(), 15, 105));
        rectangles.add(new Rect2i(viewport.x() + viewport.width() - TerminalViewportLayout.TAB_WIDTH,
                viewport.y() + viewport.height() - 72, TerminalViewportLayout.TAB_WIDTH, 70));
        if (!popupStack.isEmpty()) rectangles.add(rect(popups.get(popupStack.getLast())));
        return rectangles;
    }

    /**
     * Only visible configuration cells accept dragged viewer ingredients; physical input cells never do.
     */
    public ObjectList<GhostTarget> ghostTargets() {
        ObjectList<GhostTarget> targets = new ObjectArrayList<>();
        if (!visible) return targets;
        if (!popupStack.isEmpty()) {
            if (popupStack.getLast().equals("filters")) {
                collectTargets(targets, pickup, "pickup");
                collectTargets(targets, insert, "insert");
            }
        } else if (!preferences.folded) {
            if (encoding || workspace.equals("processing")) collectTargets(targets, inputs, "inputs");
            if (encoding && workspace.equals("processing")) collectTargets(targets, outputs, "outputs");
        }
        return targets;
    }

    private static void collectTargets(ObjectList<GhostTarget> targets, ObjectList<TerminalResourceCell> cells, String inventory) {
        for (int index = 0; index < cells.size(); index++)
            if (cells.get(index).isVisible()) targets.add(new GhostTarget(rect(cells.get(index)), inventory, index));
    }

    public void drop(GhostTarget target, GenericStack stack) {
        CompoundTag data = string("inventory", target.inventory());
        data.putInt("index", target.index());
        data.putInt("button", 0);
        data.putLong("amount", Math.max(1, stack.amount()));
        data.put("stack", GenericStack.writeTag(registries, new GenericStack(stack.what(), Math.max(1, stack.amount()))));
        request.accept("ghost", data);
    }

    public record GhostTarget(Rect2i area, String inventory, int index) {}

    public void open(String name) {
        Objects.requireNonNull(window.getModularUI(), "Terminal popups require an attached UI").clearFocus();
        captureWindow();
        if (!popupStack.isEmpty()) popups.get(popupStack.getLast()).setVisible(false);
        popupStack.add(name);
        popups.get(name).setVisible(true);
        if (name.equals("quantity") && selected != null) {
            ObjectList<TerminalResourceCell> cells = new ObjectArrayList<>();
            createCells(element(popups.get(name), "selected"), cells, 1, 1, (index, event) -> {});
            cells.getFirst().entry(new GenericStack(selected, 1), false);
        }
        geometryDirty = true;
    }

    public boolean closePopup() {
        if (popupStack.isEmpty()) return false;
        popups.get(popupStack.removeLast()).setVisible(false);
        if (!popupStack.isEmpty()) popups.get(popupStack.getLast()).setVisible(true);
        else releaseWindow();
        geometryDirty = true;
        return true;
    }

    public void visible(boolean value) {
        visible = value;
        preferences.open = value;
        preferences.save();
        window.setVisible(value);
        element(ui.rootElement, "entry").setVisible(!value);
        if (!value) {
            window.selfAndAllChildren().forEach(element -> {
                if (element instanceof TerminalActionButton button) button.resetInteraction();
            });
            Objects.requireNonNull(window.getModularUI()).clearFocus();
            for (UIElement popup : popups.values()) popup.setVisible(false);
            popupStack.clear();
            releaseWindow();
        }
        CompoundTag data = new CompoundTag();
        data.putBoolean("value", value);
        request.accept("visible", data);
        geometryDirty = true;
    }

    private void moveWindow(int x, int y) {
        int availableX = Math.max(0, screenWidth - viewport.width());
        int availableY = Math.max(0, screenHeight - viewport.height());
        preferences.xPercent = availableX == 0 ? 0 : (float) Math.clamp(x, 0, availableX) / availableX;
        preferences.yPercent = availableY == 0 ? 0 : (float) Math.clamp(y, 0, availableY) / availableY;
        geometry(screenWidth, screenHeight);
    }

    private void networkAction(String action, CompoundTag data) {
        if (snapshot.getBoolean("connected")) request.accept(action, data);
    }

    private static void createCells(UIElement host, ObjectList<TerminalResourceCell> cells, int count, int columns,
                                    BiConsumer<Integer, UIEvent> click) {
        host.clearAllChildren();
        cells.clear();
        for (int index = 0; index < count; index++) {
            int slot = index;
            TerminalResourceCell cell = new TerminalResourceCell();
            TerminalUiLayout.at(cell, slot % columns * 18, slot / columns * 18, 18, 18);
            cell.onResourceClick(event -> click.accept(slot, event));
            cells.add(cell);
            host.addChild(cell);
        }
    }

    private static void setCells(ObjectList<TerminalResourceCell> cells, ListTag values, boolean ghosts) {
        for (int index = 0; index < cells.size(); index++) {
            CompoundTag value = index < values.size() ? values.getCompound(index) : new CompoundTag();
            cells.get(index).entry(ghosts ? generic(value) : physical(value), false);
        }
    }

    private static @Nullable GenericStack generic(CompoundTag tag) {
        var level = Minecraft.getInstance().level;
        return level == null || tag.isEmpty() ? null : GenericStack.readTag(level.registryAccess(), tag);
    }

    private static @Nullable GenericStack physical(CompoundTag tag) {
        var level = Minecraft.getInstance().level;
        if (level == null || tag.isEmpty()) return null;
        ItemStack stack = ItemStack.parseOptional(level.registryAccess(), tag);
        return stack.isEmpty() ? null : new GenericStack(AEItemKey.of(stack), stack.getCount());
    }

    private CompoundTag key(AEKey key) {
        CompoundTag tag = new CompoundTag();
        tag.put("key", key.toTagGeneric(registries));
        return tag;
    }

    private static CompoundTag string(String key, String value) {
        CompoundTag data = new CompoundTag();
        data.putString(key, value);
        return data;
    }

    private static CompoundTag number(String key, int value) {
        CompoundTag data = new CompoundTag();
        data.putInt(key, value);
        return data;
    }

    private static CompoundTag booleanValue(String key, boolean value) {
        CompoundTag data = new CompoundTag();
        data.putBoolean(key, value);
        return data;
    }

    private static UIElement element(UIElement root, String id) {
        return TerminalUiLayout.require(root, id, UIElement.class);
    }

    private static Label label(UIElement root, String id) {
        return TerminalUiLayout.require(root, id, Label.class);
    }

    private static TextField field(UIElement root, String id) {
        return TerminalUiLayout.require(root, id, TextField.class);
    }

    private static void button(UIElement root, String id, Runnable action) {
        Button button = TerminalUiLayout.require(root, id, Button.class);
        if (!button.hasClass("terminal-icon")) button.setText(text(id));
        button.style(style -> style.tooltips(text(id)));
        button.setOnClick(event -> {
            if (event.button == 0) action.run();
        });
    }

    private static void tooltip(UIElement root, String id, Component value) {
        element(root, id).style(style -> style.tooltips(value));
    }

    private static void stateClass(UIElement element, String name, boolean value) {
        if (value) {
            if (!element.hasClass(name)) element.addClass(name);
        } else element.removeClass(name);
    }

    private static UIElement listHost(UIElement root, String id) {
        return TerminalUiLayout.require(root, id, ScrollerView.class).viewContainer;
    }

    private static Component text(String key, Object... args) {
        return Component.translatable("gui.data_energistics.player_terminal." + key, args);
    }

    private static Rect2i rect(UIElement element) {
        return new Rect2i(Math.round(element.getPositionX()), Math.round(element.getPositionY()), Math.round(element.getSizeWidth()), Math.round(element.getSizeHeight()));
    }

    public static boolean over(UIElement element, double x, double y) {
        return element.isVisible() && x >= element.getPositionX() && y >= element.getPositionY() && x < element.getPositionX() + element.getSizeWidth() && y < element.getPositionY() + element.getSizeHeight();
    }
}
