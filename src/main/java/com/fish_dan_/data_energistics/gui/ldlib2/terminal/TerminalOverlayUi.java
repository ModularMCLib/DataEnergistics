package com.fish_dan_.data_energistics.gui.ldlib2.terminal;

import com.fish_dan_.data_energistics.client.terminal.TerminalClientPreferences;
import com.fish_dan_.data_energistics.client.terminal.TerminalViewportLayout;
import com.fish_dan_.data_energistics.client.terminal.storage.TerminalStorageIndex;
import com.fish_dan_.data_energistics.common.anvil.DigitalAnvilUpgrade;
import com.fish_dan_.data_energistics.common.terminal.player.workstation.TerminalWorkspace;

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
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import dev.vfyjxf.taffy.style.TaffyPosition;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import it.unimi.dsi.fastutil.objects.Reference2BooleanOpenHashMap;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.function.BiConsumer;

/**
 * Binds server snapshots to authored XML trees; native container slots and cursor stay with the host.
 */
@NullMarked
public final class TerminalOverlayUi {

    public final UI ui = TerminalXmlLayouts.load("main");
    private final TerminalClientPreferences preferences;
    private final HolderLookup.Provider registries;
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
    private final ObjectList<TerminalResourceCell> patterns = new ObjectArrayList<>();
    private final ObjectList<TerminalResourceCell> pickup = new ObjectArrayList<>();
    private final ObjectList<TerminalResourceCell> insert = new ObjectArrayList<>();
    private final ObjectList<String> types = new ObjectArrayList<>();
    private final Reference2BooleanOpenHashMap<UIElement> modalHitStates = new Reference2BooleanOpenHashMap<>();
    private final Scroller.Vertical scrollbar = TerminalXmlLayouts.require(window, "storage_scroll", Scroller.Vertical.class);
    private TerminalViewportLayout viewport = TerminalViewportLayout.compute(320, 240, 8, 8, 148, 198, true);
    private CompoundTag snapshot = new CompoundTag();
    private String workspace = "crafting";
    private boolean encoding;
    private String sort = "name";
    private String filter = "all";
    private boolean descending;
    private int typeIndex;
    private int offset;
    private int cpu = -1;
    private @Nullable AEKey selected;
    private @Nullable GhostTarget quantityTarget;
    private CompoundTag previousCraft = new CompoundTag();
    private CompoundTag previousMagnet = new CompoundTag();
    private long[] previousFrequencies = new long[0];
    private long previousFrequency = Long.MIN_VALUE;
    private int gridColumns;
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
    private boolean visible = true;

    public TerminalOverlayUi(TerminalClientPreferences preferences, BiConsumer<String, CompoundTag> request) {
        this.preferences = preferences;
        this.request = request;
        registries = Objects.requireNonNull(Minecraft.getInstance().level).registryAccess();
        types.add("all");
        for (var type : AEKeyTypes.getAll()) types.add(type.getId().toString());
        label(window, "title").setText(text("title"));
        field(window, "search").textFieldStyle(style -> style.placeholder(text("search")));
        field(window, "search").style(style -> style.tooltips(text("search_syntax")));
        element(ui.rootElement, "entry").setVisible(false);
        button(ui.rootElement, "entry", () -> visible(true));
        button(window, "close", () -> visible(false));
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
        button(window, "page_previous", () -> {
            offset = Math.max(0, offset - storage.size());
            view(false);
        });
        button(window, "page_next", () -> {
            if (offset + storage.size() < storageIndex.projected().size()) {
                offset += storage.size();
                view(false);
            }
        });
        button(window, "fold", () -> {
            preferences.folded = !preferences.folded;
            geometry(screenWidth, screenHeight);
            preferences.save();
        });
        for (TerminalWorkspace mode : TerminalWorkspace.values()) {
            UIElement root = TerminalXmlLayouts.load(mode.id()).rootElement;
            workspaces.put(mode.id(), root);
            element(window, "workspaces").addChild(root);
            label(root, "workspace_title").setText(text("workspace." + mode.id()));
            button(window, "tab_" + mode.id(), () -> request.accept("workspace", string("id", mode.id())));
            button(root, "recipe_previous", () -> request.accept("recipe", number("index", Math.max(0, snapshot.getCompound("work").getInt("recipe") - 1))));
            button(root, "recipe_next", () -> request.accept("recipe", number("index", Math.min(Math.max(0, snapshot.getInt("recipe_count") - 1), snapshot.getCompound("work").getInt("recipe") + 1))));
            if (mode.supportsEncoding()) {
                button(root, "encoding", () -> request.accept("toggle_encoding", new CompoundTag()));
                button(root, "substitutes", () -> request.accept("substitutes", new CompoundTag()));
                button(root, "fluid_substitutes", () -> request.accept("fluid_substitutes", new CompoundTag()));
            } else {
                field(root, "name").setTextRegexValidator(".{0,50}").setTextResponder(value -> request.accept("name", string("value", value)));
                button(root, "boost_down", () -> boost(-1));
                button(root, "boost_up", () -> boost(1));
            }
            if (mode == TerminalWorkspace.PROCESSING)
                button(root, "transfer_processing", () -> networkAction("transfer_processing", new CompoundTag()));
        }
        for (String name : new String[]{"network", "magnet", "filters", "quantity", "confirm"}) {
            UIElement root = TerminalXmlLayouts.load(name).rootElement;
            popups.put(name, root);
            root.setVisible(false);
            ui.rootElement.addChild(root);
            label(root, "popup_title").setText(text(name));
            button(root, "back", this::closePopup);
            root.select(".terminal-list", ScrollerView.class).forEach(list -> {
                list.scrollerStyle(style -> style.mode(ScrollerMode.VERTICAL).horizontalScrollDisplay(ScrollDisplay.NEVER)
                        .verticalScrollDisplay(ScrollDisplay.AUTO));
            });
        }
        UIElement magnet = popups.get("magnet");
        for (String mode : new String[]{"OFF", "INVENTORY", "NETWORK", "PICKUP_NETWORK"}) {
            String id = "mode_" + mode.toLowerCase(Locale.ROOT);
            button(magnet, id, () -> request.accept("magnet_mode", string("value", mode)));
        }
        button(magnet, "restock", () -> request.accept("magnet_restock", new CompoundTag()));
        button(magnet, "filters", () -> open("filters"));
        UIElement filters = popups.get("filters");
        for (String action : new String[]{"pickup_whitelist", "insert_whitelist", "filter_copy_up", "filter_copy_down", "filter_swap"}) {
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
        button(quantity, "plan", this::plan);
        UIElement confirm = popups.get("confirm");
        button(confirm, "cancel", () -> request.accept("craft_cancel", new CompoundTag()));
        button(confirm, "start", () -> networkAction("craft_start", number("cpu", cpu)));
        button(confirm, "cpu", this::nextCpu);
        refreshWorkCells();
    }

    private void boost(int direction) {
        int step = UIElement.isCtrlDown() ? 100 : UIElement.isShiftDown() ? 10 : 1;
        request.accept("boost", number("value", Math.clamp(snapshot.getCompound("work").getInt("boost") + direction * step, 0, 254)));
    }

    private void amount(int direction) {
        TextField field = field(popups.get("quantity"), "amount");
        long current = quantityAmount();
        long step = UIElement.isCtrlDown() ? 100 : UIElement.isShiftDown() ? 10 : 1;
        field.setText(Long.toString(direction > 0 ? Math.min(Long.MAX_VALUE - step, current) + step : Math.max(1, current - step)), false);
    }

    private void plan() {
        if (selected == null) return;
        if (quantityTarget != null) {
            drop(quantityTarget, new GenericStack(selected, quantityAmount()));
            closePopup();
            return;
        }
        CompoundTag data = key(selected);
        data.putLong("amount", quantityAmount());
        networkAction("craft_plan", data);
        open("confirm");
    }

    private long quantityAmount() {
        String value = field(popups.get("quantity"), "amount").getValue();
        return value.isEmpty() ? 1 : Long.parseLong(value);
    }

    private void nextCpu() {
        ListTag cpus = snapshot.getCompound("craft").getList("cpus", Tag.TAG_COMPOUND);
        int next = -1;
        for (int index = 0; index < cpus.size(); index++) {
            int id = cpus.getCompound(index).getInt("id");
            if (cpu == -1 || next == -2) {
                next = id;
                break;
            }
            if (id == cpu) next = -2;
        }
        cpu = next < 0 ? -1 : next;
        refreshCraft();
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
        label(window, "page").setText(storageIndex.pending() ? text("storage_loading", storageIndex.loaded(), storageIndex.total()) : page);
        tooltip(window, "page", storageIndex.pending() ? Component.empty().append(text("storage_loading", storageIndex.loaded(), storageIndex.total())).append(" ").append(page) : page);
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
            if (entry.getKey().equals(workspace)) tab.addClass("selected");
            else tab.removeClass("selected");
        }
        CompoundTag work = value.getCompound("work");
        boolean ghostInputs = encoding || workspace.equals("processing");
        setCells(inputs, work.getList(ghostInputs ? "ghost_inputs" : "items", Tag.TAG_COMPOUND), ghostInputs);
        setCells(outputs, work.getList("ghost_outputs", Tag.TAG_COMPOUND), true);
        setCells(patterns, work.getList("patterns", Tag.TAG_COMPOUND), false);
        UIElement active = workspaces.get(workspace);
        TerminalResourceCell result = (TerminalResourceCell) element(active, "result").getChildren().getFirst();
        result.entry(physical(value.getCompound("result")), false);
        label(active, "recipe_index").setText(Component.literal((work.getInt("recipe") + 1) + "/" + Math.max(1, value.getInt("recipe_count"))));
        if (workspace.equals("anvil")) {
            if (!field(active, "name").isFocused()) field(active, "name").setText(work.getString("name"), false);
            label(active, "boost").setText(Component.literal("+" + work.getInt("boost")));
            boolean levels = DigitalAnvilUpgrade.UNRESTRICTED_LEVELS.enabled(value.getInt("anvil_upgrades"));
            element(active, "boost_down").setActive(levels && work.getInt("boost") > 0);
            element(active, "boost_up").setActive(levels && work.getInt("boost") < 254);
            result.style(style -> style.tooltips(text("anvil_cost", value.getLong("anvil_energy"), value.getLong("anvil_levels"))));
        }
        boolean recipes = workspace.equals("stonecutting") && value.getInt("recipe_count") > 1;
        element(active, "recipe_previous").setVisible(recipes);
        element(active, "recipe_next").setVisible(recipes);
        element(active, "recipe_index").setVisible(recipes);
        result.setActive(!workspace.equals("anvil") || value.getBoolean("connected"));
        if (!workspace.equals("anvil")) {
            tooltip(active, "encoding", text(encoding ? "encoding_on" : "encoding_off"));
            stateClass(element(active, "encoding"), "actual-mode", !encoding);
            tooltip(active, "substitutes", text(value.getBoolean("substitutes") ? "substitutes_on" : "substitutes_off"));
            tooltip(active, "fluid_substitutes", text(value.getBoolean("fluid_substitutes") ? "fluid_substitutes_on" : "fluid_substitutes_off"));
        }
        if (workspace.equals("processing")) {
            element(active, "transfer_processing").setVisible(!encoding);
            element(active, "transfer_processing").setActive(value.getBoolean("connected") && value.getBoolean("transfer_supported"));
            tooltip(active, "transfer_processing", text(!value.getBoolean("transfer_supported") ? "message.unsupported_container" :
                    !value.getBoolean("connected") ? "message.offline" : "transfer_processing"));
        }
        storageIndex.apply(value, registries);
        view(false);
        label(window, "title").setText(text(value.getString("reason").equals("fault") ? "reason.fault" : "title"));
        label(window, "status").setText(Component.literal(value.getString("reason").equals("fault") ? "!" : value.getBoolean("connected") ? "●" : "○"));
        tooltip(window, "status", text("reason." + value.getString("reason")));
        if (!value.getString("message").isEmpty())
            tooltip(window, "status", text("message." + value.getString("message")));
        element(window, "insert").setActive(value.getBoolean("connected"));
        element(window, "tasks").setActive(value.getBoolean("connected"));
        element(window, "magnet").setActive(value.getBoolean("magnet_unlocked"));
        label(popups.get("network"), "connection_status").setText(text("reason." + value.getString("reason")));
        refreshNetwork();
        refreshMagnet();
        refreshCraft();
    }

    private void refreshWorkCells() {
        inputs.clear();
        outputs.clear();
        patterns.clear();
        UIElement root = workspaces.get(workspace);
        TerminalWorkspace mode = TerminalWorkspace.byId(workspace);
        createCells(element(root, "inputs"), inputs, mode.inputSlots(), mode == TerminalWorkspace.ANVIL ? 2 : 3,
                (index, event) -> {
                    if (encoding || mode == TerminalWorkspace.PROCESSING) ghost("inputs", index, event.button);
                    else slot("inputs", index, event.button);
                });
        createCells(element(root, "outputs"), outputs, mode == TerminalWorkspace.PROCESSING && encoding ? 3 : 0, 1,
                (index, event) -> ghost("outputs", index, event.button));
        for (TerminalResourceCell cell : inputs) cell.configurable(encoding || mode == TerminalWorkspace.PROCESSING);
        for (TerminalResourceCell cell : outputs) cell.configurable(true);
        if (mode.supportsEncoding()) {
            createCells(element(root, "patterns"), patterns, encoding ? 2 : 0, 2, (index, event) -> slot("patterns", index, event.button));
        }
        ObjectList<TerminalResourceCell> result = new ObjectArrayList<>();
        createCells(element(root, "result"), result, 1, 1, (index, event) -> {
            CompoundTag data = new CompoundTag();
            data.putBoolean("shift", UIElement.isShiftDown());
            request.accept("result", data);
        });
        element(root, "result").setVisible(mode != TerminalWorkspace.PROCESSING || encoding);
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
            Button button = new Button();
            button.addClass("frequency-row");
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
        for (String mode : new String[]{"OFF", "INVENTORY", "NETWORK", "PICKUP_NETWORK"}) {
            Button control = TerminalXmlLayouts.require(root, "mode_" + mode.toLowerCase(Locale.ROOT), Button.class);
            control.setText(Component.literal(mode.equals(magnet.getString("mode")) ? "● " : "○ ").append(text("magnet_mode." + mode)));
        }
        TerminalXmlLayouts.require(root, "restock", Button.class).setText(text(magnet.getBoolean("restock") ? "restock_on" : "restock_off"));
        setCells(pickup, magnet.getList("pickup", Tag.TAG_COMPOUND), true);
        setCells(insert, magnet.getList("insert", Tag.TAG_COMPOUND), true);
        UIElement filters = popups.get("filters");
        TerminalXmlLayouts.require(filters, "pickup_whitelist", Button.class).setText(text("pickup_" + (magnet.getBoolean("pickup_whitelist") ? "whitelist" : "blacklist")));
        TerminalXmlLayouts.require(filters, "insert_whitelist", Button.class).setText(text("insert_" + (magnet.getBoolean("insert_whitelist") ? "whitelist" : "blacklist")));
    }

    private void refreshCraft() {
        CompoundTag craft = snapshot.getCompound("craft");
        UIElement confirm = popups.get("confirm");
        TerminalXmlLayouts.require(confirm, "cpu", Button.class).setText(text("cpu_selection", cpu < 0 ? text("automatic") : cpu));
        element(confirm, "start").setActive(snapshot.getBoolean("connected") && craft.getString("status").equals("ready"));
        if (craft.equals(previousCraft)) return;
        previousCraft = craft.copy();
        label(confirm, "craft_status").setText(text("craft_status", text("craft." + craft.getString("status")), craft.getLong("bytes"), craft.getString("message")));
        costRows(listHost(confirm, "used"), craft.getList("used", Tag.TAG_COMPOUND));
        costRows(listHost(confirm, "missing"), craft.getList("missing", Tag.TAG_COMPOUND));
    }

    private void costRows(UIElement host, ListTag entries) {
        host.clearAllChildren();
        for (int index = 0; index < entries.size(); index++) {
            GenericStack stack = generic(entries.getCompound(index));
            if (stack == null) continue;
            Label row = new Label();
            row.addClass("cost-row");
            row.setText(Component.literal(stack.amount() + " × ").append(stack.what().getDisplayName()));
            host.addChild(row);
        }
    }

    public void geometry(int width, int height) {
        screenWidth = width;
        screenHeight = height;
        viewport = TerminalViewportLayout.compute(width, height, preferences.x, preferences.y,
                preferences.width, preferences.height, preferences.folded);
        preferences.x = viewport.x();
        preferences.y = viewport.y();
        preferences.width = viewport.width();
        preferences.height = viewport.height();
        window.layout(style -> style.left(viewport.x()).top(viewport.y()).width(viewport.width()).height(viewport.height()));
        element(window, "storage").layout(style -> style.width(viewport.storageWidth()).height(viewport.storageHeight()));
        scrollbar.layout(style -> style.left(TerminalViewportLayout.STORAGE_LEFT + viewport.storageWidth() + 2).height(viewport.storageHeight()));
        element(window, "workspaces").setVisible(!preferences.folded);
        element(window, "tabs").setVisible(!preferences.folded);
        int footer = viewport.workspaceHeight();
        element(window, "page_previous").layout(style -> style.bottom(footer + 15));
        element(window, "page_next").layout(style -> style.bottom(footer + 15));
        element(window, "page").layout(style -> style.bottom(footer + 15));
        element(window, "fold").layout(style -> style.bottom(footer + 3));
        TerminalXmlLayouts.require(window, "fold", Button.class).setText(text(preferences.folded ? "expand" : "collapse"));
        int cellCount = viewport.cellCount();
        boolean gridChanged = storage.size() != cellCount || gridColumns != viewport.columns();
        if (gridChanged) {
            gridColumns = viewport.columns();
            createCells(element(window, "storage"), storage, cellCount, viewport.columns(), (index, event) -> {
                TerminalResourceCell cell = storage.get(index);
                if (cell.stack() == null) return;
                if (cell.craftable() && (UIElement.isCtrlDown() || cell.stack().amount() == 0)) {
                    selected = cell.stack().what();
                    quantityTarget = null;
                    field(popups.get("quantity"), "amount").setText("1", false);
                    open("quantity");
                } else {
                    CompoundTag data = key(cell.stack().what());
                    data.putInt("button", event.button);
                    data.putBoolean("shift", UIElement.isShiftDown());
                    data.putBoolean("craft", false);
                    networkAction("storage", data);
                }
            });
            for (TerminalResourceCell cell : storage) cell.storageCell(true);
            boundCellCount = -1;
            fillStorage();
        }
        for (UIElement popup : popups.values()) {
            popup.layout(style -> style.left(Math.clamp(preferences.x + viewport.width() + 3, 0, Math.max(0, width - 190)))
                    .top(Math.clamp(preferences.y, 0, Math.max(0, height - 232))));
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
        resizing = over(element(window, "resize"), x, y);
        adjusting = resizing || over(element(window, "drag"), x, y);
        if (!adjusting) return false;
        dragStartX = x;
        dragStartY = y;
        startX = preferences.x;
        startY = preferences.y;
        startWidth = preferences.width;
        startHeight = preferences.height;
        return true;
    }

    public boolean drag(double x, double y) {
        if (!adjusting) return false;
        if (resizing) {
            preferences.width = startWidth + (int) (x - dragStartX);
            preferences.height = startHeight + (int) (y - dragStartY);
        } else {
            preferences.x = startX + (int) (x - dragStartX);
            preferences.y = startY + (int) (y - dragStartY);
        }
        geometry(screenWidth, screenHeight);
        return true;
    }

    public boolean endDrag() {
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
        return visible ? over(window, x, y) || (!popupStack.isEmpty() && over(popups.get(popupStack.getLast()), x, y)) : over(element(ui.rootElement, "entry"), x, y);
    }

    public boolean modal() {
        return !popupStack.isEmpty();
    }

    public boolean inputAllowed(double x, double y) {
        return modal() ? over(popups.get(popupStack.getLast()), x, y) : contains(x, y);
    }

    private void captureWindow() {
        window.selfAndAllChildren().forEach(element -> {
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
                .scrollBarSize(maximumRow == 0 ? 100 : Math.max(5, 100F * viewport.rows() / (viewport.rows() + maximumRow))));
        scrollbar.selfAndAllChildren().forEach(element -> {
            if (modal()) modalHitStates.put(element, maximumRow > 0);
            element.setAllowHitTest(maximumRow > 0);
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
        rectangles.add(new Rect2i(viewport.x(), viewport.y(), viewport.width(), viewport.height()));
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
            targets.add(new GhostTarget(rect(cells.get(index)), inventory, index));
    }

    public void drop(GhostTarget target, GenericStack stack) {
        CompoundTag data = string("inventory", target.inventory());
        data.putInt("index", target.index());
        data.putInt("button", 0);
        data.putLong("amount", Math.max(1, stack.amount()));
        data.put("stack", GenericStack.writeTag(registries, new GenericStack(stack.what(), Math.max(1, stack.amount()))));
        request.accept("ghost", data);
    }

    public record GhostTarget(Rect2i area, String inventory, int index) {
    }

    public void open(String name) {
        Objects.requireNonNull(window.getModularUI(), "Terminal popups require an attached UI").clearFocus();
        captureWindow();
        if (!popupStack.isEmpty()) popups.get(popupStack.getLast()).setVisible(false);
        popupStack.add(name);
        popups.get(name).setVisible(true);
        if (name.equals("quantity") && selected != null) {
            ObjectList<TerminalResourceCell> cells = new ObjectArrayList<>();
            createCells(element(popups.get(name), "selected"), cells, 1, 1, (index, event) -> {
            });
            cells.getFirst().entry(new GenericStack(selected, 1), false);
            element(popups.get(name), "plan").setActive(quantityTarget != null || snapshot.getBoolean("connected"));
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
        window.setVisible(value);
        element(ui.rootElement, "entry").setVisible(!value);
        if (!value) {
            for (UIElement popup : popups.values()) popup.setVisible(false);
            popupStack.clear();
            releaseWindow();
        }
        CompoundTag data = new CompoundTag();
        data.putBoolean("value", value);
        request.accept("visible", data);
        geometryDirty = true;
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
            cell.layout(style -> style.positionType(TaffyPosition.ABSOLUTE).left(slot % columns * 18).top(slot / columns * 18));
            cell.setOnClick(event -> {
                if (event.button == 0 || event.button == 1) click.accept(slot, event);
            });
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

    private static UIElement element(UIElement root, String id) {
        return TerminalXmlLayouts.require(root, id, UIElement.class);
    }

    private static Label label(UIElement root, String id) {
        return TerminalXmlLayouts.require(root, id, Label.class);
    }

    private static TextField field(UIElement root, String id) {
        return TerminalXmlLayouts.require(root, id, TextField.class);
    }

    private static void button(UIElement root, String id, Runnable action) {
        Button button = TerminalXmlLayouts.require(root, id, Button.class);
        button.setText(switch (id) {
            case "sort", "direction", "display", "type", "network", "magnet", "tasks", "insert", "encoding", "drag",
                 "close", "resize", "entry", "filters", "tab_crafting", "tab_processing", "tab_smithing",
                 "tab_stonecutting", "tab_anvil" -> Component.empty();
            default -> text(id);
        });
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
        return TerminalXmlLayouts.require(root, id, ScrollerView.class).viewContainer;
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
