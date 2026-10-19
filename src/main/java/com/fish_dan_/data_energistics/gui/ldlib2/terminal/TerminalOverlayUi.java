package com.fish_dan_.data_energistics.gui.ldlib2.terminal;

import com.fish_dan_.data_energistics.client.terminal.TerminalClientPreferences;
import com.fish_dan_.data_energistics.client.terminal.TerminalViewportLayout;
import com.fish_dan_.data_energistics.client.terminal.storage.TerminalStorageIndex;
import com.fish_dan_.data_energistics.common.anvil.DigitalAnvilUpgrade;
import com.fish_dan_.data_energistics.common.terminal.player.workstation.TerminalWorkspace;
import com.fish_dan_.data_energistics.gui.ldlib2.terminal.amount.TerminalAmountUi;
import com.fish_dan_.data_energistics.gui.ldlib2.terminal.encoding.TerminalBlankPatternCell;
import com.fish_dan_.data_energistics.gui.ldlib2.terminal.encoding.TerminalPatternUploadUi;
import com.fish_dan_.data_energistics.gui.ldlib2.terminal.encoding.TerminalProcessingControlsUi;
import com.fish_dan_.data_energistics.gui.ldlib2.terminal.magnet.TerminalMagnetUi;
import com.fish_dan_.data_energistics.gui.ldlib2.terminal.storage.TerminalKeyTypeUi;
import com.fish_dan_.data_energistics.gui.ldlib2.terminal.storage.TerminalPinnedResourcesUi;
import com.fish_dan_.data_energistics.gui.ldlib2.terminal.text.TerminalTooltipLines;
import com.fish_dan_.data_energistics.gui.ldlib2.terminal.window.TerminalWindowStack;
import com.fish_dan_.data_energistics.integration.viewer.xei.transfer.PatternProviderRecipeTypeNames;

import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Scroller;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypes;
import appeng.api.stacks.GenericStack;
import appeng.api.util.KeyTypeSelection;
import appeng.core.definitions.AEItems;
import appeng.core.localization.ButtonToolTips;

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
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.Objects;
import java.util.function.BiConsumer;

/**
 * Binds server snapshots to the Java/LSS composition; native slots and cursor stay with the host.
 */
@NullMarked
public final class TerminalOverlayUi {

    private static final int SEARCH_TOOLTIP_WIDTH = 260;

    public final UI ui = TerminalUiLayout.create();
    private final TerminalClientPreferences preferences;
    private final RegistryAccess registries;
    private final BiConsumer<String, CompoundTag> request;
    private final UIElement window = element(ui.rootElement, "window");
    private final Object2ObjectMap<String, UIElement> workspaces = new Object2ObjectLinkedOpenHashMap<>();
    private final TerminalWindowStack subWindows = new TerminalWindowStack(ui.rootElement);
    private final ObjectList<TerminalResourceCell> storage = new ObjectArrayList<>();
    private final TerminalStorageIndex storageIndex = new TerminalStorageIndex();
    private final TerminalPinnedResourcesUi pinnedUi;
    private ObjectList<TerminalStorageIndex.Entry> boundStorage = ObjectImmutableList.of();
    private boolean boundConnected;
    private int boundCellCount = -1;
    private final ObjectList<TerminalResourceCell> inputs = new ObjectArrayList<>();
    private final ObjectList<TerminalResourceCell> outputs = new ObjectArrayList<>();
    private final ObjectList<TerminalResourceCell> encodedPatterns = new ObjectArrayList<>();
    private @Nullable TerminalBlankPatternCell blankPattern;
    private final TerminalPatternUploadUi uploadUi;
    private final TerminalProcessingControlsUi processingUi;
    private final TerminalAmountUi amountUi;
    private final TerminalKeyTypeUi keyTypeUi;
    private final TerminalMagnetUi magnetUi;
    private final KeyTypeSelection keyTypeSelection;
    private ObjectSet<AEKeyType> enabledKeyTypes;
    private boolean pendingEncode;
    private final ObjectList<TerminalResourceCell> stonecuttingRecipes = new ObjectArrayList<>();
    private final ObjectList<TerminalResourceCell> pickup = new ObjectArrayList<>();
    private final ObjectList<TerminalResourceCell> insert = new ObjectArrayList<>();
    private final Scroller.Vertical scrollbar = TerminalUiLayout.require(window, "storage_scroll", Scroller.Vertical.class);
    private final Scroller.Vertical processingScrollbar;
    private final Scroller.Vertical stonecuttingScrollbar;
    private TerminalViewportLayout viewport = TerminalViewportLayout.compute(320, 240, 0.025F, 0.033333335F, TerminalViewportLayout.MIN_WIDTH, 168, true, true,
            TerminalViewportLayout.OPERATION_WIDTH + TerminalViewportLayout.ENCODING_WIDTH, 0, 0);
    private CompoundTag snapshot = new CompoundTag();
    private String workspace = "crafting";
    private String sort = "name";
    private String filter = "all";
    private boolean descending;
    private int offset;
    private @Nullable CompoundTag previousMagnet;
    private boolean previousMagnetUnlocked;
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
        pinnedUi = new TerminalPinnedResourcesUi(element(window, "pinned_resources"), registries, request, this::storageClick);
        keyTypeSelection = new KeyTypeSelection(this::keyTypesChanged, ignored -> true);
        ObjectList<AEKeyType> enabledTypes = new ObjectArrayList<>();
        for (var type : AEKeyTypes.getAll())
            if (!preferences.disabledKeyTypes.contains(type.getId().toString())) enabledTypes.add(type);
        if (!enabledTypes.isEmpty()) keyTypeSelection.setEnabledSet(enabledTypes);
        enabledKeyTypes = new ObjectOpenHashSet<>(keyTypeSelection.enabledSet());
        field(window, "search").textFieldStyle(style -> style.placeholder(text("search")));
        field(window, "search").style(style -> style.tooltips(TerminalTooltipLines.wrap(SEARCH_TOOLTIP_WIDTH, text("search"))));
        window.setVisible(visible);
        element(ui.rootElement, "entry").setVisible(!visible);
        Button entry = TerminalUiLayout.require(ui.rootElement, "entry", Button.class);
        entry.style(style -> style.tooltips(TerminalTooltipLines.split(text("entry_gestures"))));
        entry.setOnClick(event -> {
            if (event.button == 0) visible(true);
            else if (event.button == 1) openLargeTerminal.run();
        });
        Button drag = TerminalUiLayout.require(window, "drag", Button.class);
        drag.style(style -> style.tooltips(TerminalTooltipLines.split(text("drag"))));
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
        element(window, "pinned_resources").addEventListener(UIEvents.MOUSE_WHEEL, event -> {
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
        button(window, "type", () -> open("key_types"));
        TerminalUiLayout.require(window, "pins", Button.class).setOnClick(event -> {
            if (UIElement.isShiftDown() && pinnedUi.changeRows(event.button, viewport)) {
                geometry(screenWidth, screenHeight);
                view(false);
            }
        });
        button(window, "magnet", () -> open("magnet"));
        button(window, "tasks", () -> networkAction("cpu_list", new CompoundTag()));
        TerminalUiLayout.require(window, "destination", Button.class).setOnClick(event -> {
            preferences.shiftClickToNetwork = !preferences.shiftClickToNetwork;
            preferences.save();
            refreshDestination();
        });
        refreshDestination();
        for (TerminalWorkspace mode : TerminalWorkspace.values()) {
            UIElement root = TerminalUiLayout.page(mode);
            workspaces.put(mode.id(), root);
            element(window, "workspaces").addChild(root);
            Button tab = TerminalUiLayout.require(window, "tab_" + mode.id(), Button.class);
            tab.style(style -> style.tooltips(TerminalTooltipLines.split(text("page_gestures", text("workspace." + mode.id())))));
            tab.setOnClick(event -> {
                preferences.folded = event.button == 1;
                if (event.button == 0 && !workspace.equals(mode.id())) request.accept("workspace", string("id", mode.id()));
                geometry(screenWidth, screenHeight);
                preferences.save();
            });
            if (mode.supportsEncoding()) {
                if (mode != TerminalWorkspace.STONECUTTING) {
                    TerminalUiLayout.require(root, "clear", Button.class).setOnClick(event -> {
                        if (event.button == 0) request.accept("clear", booleanValue("to_network", preferences.shiftClickToNetwork));
                    });
                    element(root, "clear").style(style -> style.tooltips(TerminalTooltipLines.split(ButtonToolTips.Clear.text(), ButtonToolTips.ClearSettings.text())));
                }
                button(root, "encoding", () -> {
                    pendingEncode = true;
                    request.accept("encode", new CompoundTag());
                });
                if (mode == TerminalWorkspace.CRAFTING) {
                    button(root, "substitutes", () -> request.accept("substitutes", new CompoundTag()));
                    button(root, "fluid_substitutes", () -> request.accept("fluid_substitutes", new CompoundTag()));
                } else if (mode == TerminalWorkspace.SMITHING) {
                    button(root, "substitutes", () -> request.accept("substitutes", new CompoundTag()));
                } else if (mode == TerminalWorkspace.PROCESSING) {
                    button(root, "record_type", () -> request.accept("record_type", new CompoundTag()));
                }
            } else {
                field(root, "name").setTextRegexValidator(".{0,50}").setTextResponder(value -> request.accept("name", string("value", value)));
                button(root, "boost_down", () -> boost(-1));
                button(root, "boost_up", () -> boost(1));
            }
        }
        processingUi = new TerminalProcessingControlsUi(element(workspaces.get("processing"), "processing_amounts"), request);
        for (String name : new String[] { "magnet", "filters", "quantity", "upload", "key_types" }) {
            UIElement root = TerminalUiLayout.popup(name);
            subWindows.add(name, root);
            label(root, "popup_title").setText(text(name));
            element(root, "close").style(style -> style.tooltips(TerminalTooltipLines.split(text("close"), text("hold_to_drag"))));
        }
        uploadUi = new TerminalPatternUploadUi(subWindows.root("upload"), registries, this::networkAction);
        keyTypeUi = new TerminalKeyTypeUi(subWindows.root("key_types"), keyTypeSelection);
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
        UIElement filters = subWindows.root("filters");
        magnetUi = new TerminalMagnetUi(subWindows.root("magnet"), filters, request, () -> open("filters", "magnet"));
        createCells(element(filters, "pickup"), pickup, 27, 9, (index, event) -> ghost("pickup", index, event.button));
        createCells(element(filters, "insert"), insert, 27, 9, (index, event) -> ghost("insert", index, event.button));
        amountUi = new TerminalAmountUi(subWindows.root("quantity"), registries, request, () -> closePopup("quantity"));
        subWindows.extraHitArea("quantity", amountUi::contains);
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
        stonecuttingScrollbar.setScrollBarSize(1500F / 46);
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

    private void keyTypesChanged() {
        enabledKeyTypes = new ObjectOpenHashSet<>(keyTypeSelection.enabledSet());
        preferences.disabledKeyTypes.clear();
        for (var type : AEKeyTypes.getAll())
            if (!enabledKeyTypes.contains(type)) preferences.disabledKeyTypes.add(type.getId().toString());
        preferences.save();
        view(false);
    }

    public void view(boolean reset) {
        ObjectList<TerminalStorageIndex.Entry> previous = storageIndex.projected();
        AEKey anchor = !reset && offset < previous.size() ? previous.get(offset).key() : null;
        pinnedUi.bind(storageIndex, snapshot.getBoolean("connected"));
        storageIndex.project(field(window, "search").getValue(), sort, descending, filter, enabledKeyTypes, pinnedUi.visibleKeys());
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
        Component searchTooltip = storageIndex.pending() ? text("storage_loading", storageIndex.loaded(), storageIndex.total()) : text("search");
        field(window, "search").style(style -> style.tooltips(TerminalTooltipLines.wrap(SEARCH_TOOLTIP_WIDTH, searchTooltip)));
        tooltip(window, "sort", text("sort." + sort));
        tooltip(window, "direction", text(descending ? "descending" : "ascending"));
        tooltip(window, "display", text("display." + filter));
        Component typeDescription = enabledKeyTypes.size() == AEKeyTypes.getAll().size() ? text("type.all") :
                enabledKeyTypes.size() == 1 ? enabledKeyTypes.iterator().next().getDescription() : text("type.selected", enabledKeyTypes.size());
        tooltip(window, "type", text("type", typeDescription));
        tooltip(window, "pins", text("pins", pinnedUi.rows()));
        tooltip(window, "tasks", text("tasks").copy().append("\n").append(connectionStatus()));
        stateClass(element(window, "sort"), "sort-amount", sort.equals("amount"));
        stateClass(element(window, "direction"), "descending", descending);
        stateClass(element(window, "display"), "stored-only", filter.equals("stored"));
        stateClass(element(window, "display"), "craftable-only", filter.equals("craftable"));
    }

    public void apply(CompoundTag value) {
        boolean rebuild = !workspace.equals(value.getString("workspace"));
        snapshot = value.copy();
        workspace = value.getString("workspace");
        if (rebuild) {
            refreshWorkCells();
            geometry(screenWidth, screenHeight);
        }
        for (var entry : workspaces.object2ObjectEntrySet()) {
            entry.getValue().setVisible(entry.getKey().equals(workspace));
            UIElement tab = element(window, "tab_" + entry.getKey());
            stateClass(tab, "selected", !preferences.folded && entry.getKey().equals(workspace));
        }
        CompoundTag work = value.getCompound("work");
        ListTag realInputs = work.getList("items", Tag.TAG_COMPOUND);
        ListTag configuredInputs = work.getList("ghost_inputs", Tag.TAG_COMPOUND);
        for (int index = 0; index < inputs.size(); index++) {
            GenericStack actual = physical(realInputs.getCompound(index));
            inputs.get(index).entry(actual != null ? actual : generic(configuredInputs.getCompound(index)), false);
            inputs.get(index).configurable(!workspace.equals("anvil") && actual == null);
        }
        setCells(outputs, work.getList("ghost_outputs", Tag.TAG_COMPOUND), true);
        if (!encodedPatterns.isEmpty()) encodedPatterns.getFirst().entry(physical(work.getList("patterns", Tag.TAG_COMPOUND).getCompound(0)), false);
        if (blankPattern != null) {
            blankPattern.entry(value.getLong("blank_patterns"), value.getBoolean("blank_patterns_craftable"));
            blankPattern.setActive(value.getBoolean("connected"));
        }
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
                label(active, "anvil_status").setText(Component.translatable("gui.data_energistics.me_digital_anvil." +
                        (result.stack() == null ? "invalid" : "cost"), value.getLong("anvil_energy"), value.getLong("anvil_levels")));
                element(active, "anvil_status").style(style -> style.tooltips(TerminalTooltipLines.split(
                        text("anvil_cost", value.getLong("anvil_energy"), value.getLong("anvil_levels")),
                        Component.translatable("gui.data_energistics.me_digital_anvil.boost", work.getInt("boost")))));
                boolean levels = DigitalAnvilUpgrade.UNRESTRICTED_LEVELS.enabled(value.getInt("anvil_upgrades"));
                element(active, "boost_down").setActive(levels && work.getInt("boost") > 0);
                element(active, "boost_up").setActive(levels && work.getInt("boost") < 254);
                result.style(style -> style.tooltips(TerminalTooltipLines.split(text("anvil_cost", value.getLong("anvil_energy"), value.getLong("anvil_levels")))));
            }
            result.setActive(!workspace.equals("anvil") || value.getBoolean("connected"));
        }
        if (!workspace.equals("anvil")) {
            tooltip(active, "encoding", text("encode_action"));
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
                stateClass(element(active, "record_type"), "option-enabled", recordType);
                CompoundTag context = work.getCompound("context");
                String type = context.getString("type");
                Component recipeType = type.isEmpty() ? text("context_none") :
                        PatternProviderRecipeTypeNames.resolveDisplayName(ResourceLocation.parse(type));
                Component transfer = text("transfer_details", recipeType,
                        context.getString("recipe").isEmpty() ? "—" : context.getString("recipe"));
                element(active, "record_type").style(style -> style.tooltips(TerminalTooltipLines.split(
                        Component.translatable("button.data_energistics.pattern_encoding_recipe_type_toggle." + (recordType ? "enabled" : "disabled")),
                        transfer)));
            }
        }
        if (!workspace.equals("anvil") && !workspace.equals("stonecutting")) {
            var clearTooltip = new ObjectArrayList<Component>(ObjectImmutableList.of(ButtonToolTips.Clear.text(), ButtonToolTips.ClearSettings.text()));
            if (value.getString("action").equals("clear")) {
                String message = value.getString("message");
                if (!message.isEmpty()) clearTooltip.add(text("message." + message));
                else if (workspace.equals("processing")) setProcessingScrollRow(0, true);
            }
            element(active, "clear").style(style -> style.tooltips(TerminalTooltipLines.split(clearTooltip).toArray(Component[]::new)));
        }
        storageIndex.apply(value, registries);
        boolean pinsChanged = pinnedUi.apply(value.getCompound("pins"));
        if (pinnedUi.refreshAutomatic(storageIndex) || pinsChanged) geometry(screenWidth, screenHeight);
        view(false);
        element(window, "insert").setActive(value.getBoolean("connected"));
        element(window, "tasks").setActive(value.getBoolean("connected"));
        refreshMagnet();
        uploadUi.apply(value);
        processingUi.apply(value);
        amountUi.apply(value);
        if (pendingEncode && value.getString("action").equals("encode")) {
            pendingEncode = false;
            if (value.getString("message").equals("encoded")) open("upload");
        }
        if (!visibilitySent) {
            visibilitySent = true;
            if (!visible) request.accept("visible", booleanValue("value", false));
        }
    }

    public void tick() {
        if (pinnedUi.refreshAutomatic(storageIndex)) {
            geometry(screenWidth, screenHeight);
            view(false);
        }
    }

    public void removed() {
        pinnedUi.removed();
    }

    private void refreshWorkCells() {
        inputs.clear();
        outputs.clear();
        encodedPatterns.clear();
        blankPattern = null;
        UIElement root = workspaces.get(workspace);
        TerminalWorkspace mode = TerminalWorkspace.byId(workspace);
        int inputCount = mode == TerminalWorkspace.PROCESSING ? mode.patternInputSlots() : mode.inputSlots();
        createCells(element(root, "inputs"), inputs, inputCount, mode == TerminalWorkspace.ANVIL ? 2 : 3,
                (index, event) -> {
                    boolean actual = !snapshot.getCompound("work").getList("items", Tag.TAG_COMPOUND).getCompound(index).isEmpty();
                    if (mode == TerminalWorkspace.PROCESSING || mode.supportsEncoding() && !actual &&
                            Objects.requireNonNull(Minecraft.getInstance().player).containerMenu.getCarried().isEmpty())
                        ghost("inputs", index, event.button);
                    else slot("inputs", index, event.button);
                });
        for (TerminalResourceCell cell : inputs) cell.addClass("embedded-cell");
        if (mode == TerminalWorkspace.ANVIL) TerminalUiLayout.at(inputs.get(1), 36, 0, 18, 18);
        createCells(element(root, "outputs"), outputs,
                mode == TerminalWorkspace.PROCESSING ? mode.patternOutputSlots() : 0, 1,
                (index, event) -> ghost("outputs", index, event.button));
        for (TerminalResourceCell cell : inputs) cell.configurable(mode.supportsEncoding());
        for (TerminalResourceCell cell : outputs) cell.configurable(true);
        for (TerminalResourceCell cell : outputs) cell.addClass("embedded-cell");
        if (mode.supportsEncoding()) {
            UIElement blanks = element(root, "blank_pattern");
            blanks.clearAllChildren();
            blankPattern = TerminalUiLayout.at(new TerminalBlankPatternCell(this::blankPatternClick), 0, 0, 18, 18);
            blanks.addChild(blankPattern);
            createCells(element(root, "encoded"), encodedPatterns, 1, 1, (index, event) -> slot("encoded", 0, event.button));
            for (TerminalResourceCell cell : encodedPatterns) cell.actionHint(text("encoded_gestures"));
        }
        if (mode != TerminalWorkspace.STONECUTTING && mode != TerminalWorkspace.PROCESSING) {
            ObjectList<TerminalResourceCell> result = new ObjectArrayList<>();
            createCells(element(root, "result"), result, 1, 1, (index, event) -> {
                if (event.button != 0) return;
                CompoundTag data = new CompoundTag();
                data.putBoolean("shift", UIElement.isShiftDown());
                request.accept("result", data);
            });
            if (mode != TerminalWorkspace.PROCESSING) result.getFirst().addClass("embedded-cell");
        }
        if (mode == TerminalWorkspace.STONECUTTING) {
            createCells(element(root, "recipes"), stonecuttingRecipes, 8, 4, (index, event) -> {
                if (event.button == 0) {
                    int selected = stonecuttingScrollRow * 4 + index;
                    if (selected < snapshot.getInt("recipe_count")) request.accept("cut", number("index", selected));
                } else if (event.button == 1) {
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
            cell.actionHint(text("stonecutting_gestures"));
            int row = index / 4;
            TerminalUiLayout.at(cell, index % 4 * 18, row * 18, 18, 18);
            cell.setVisible(true);
            stateClass(cell, "selected-recipe", first + index == selected && !output.isEmpty());
        }
    }

    private void blankPatternClick(UIEvent event) {
        if (event.button == 2 || event.button == 0 && UIElement.isCtrlDown()) {
            if (snapshot.getBoolean("blank_patterns_craftable"))
                networkAction("craft_request", key(AEItemKey.of(AEItems.BLANK_PATTERN)));
        } else networkAction("blank_pattern", number("button", event.button));
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
        boolean processing = workspace.equals("processing") && (inventory.equals("inputs") || inventory.equals("outputs"));
        if (entry != null && (processing || inventory.equals("pickup") || inventory.equals("insert")) &&
                (button == 2 && processing || button == 0 && UIElement.isShiftDown())) {
            amountUi.edit(inventory, index, entry, processing, snapshot.getCompound("work"));
            open("quantity", processing ? null : "filters");
            return;
        }
        if (button == 2) return;
        CompoundTag data = string("inventory", inventory);
        data.putInt("index", index);
        data.putInt("button", button);
        data.putLong("amount", 1);
        request.accept("ghost", data);
    }

    private void refreshMagnet() {
        CompoundTag magnet = snapshot.getCompound("magnet");
        boolean unlocked = snapshot.getBoolean("magnet_unlocked");
        if (magnet.equals(previousMagnet) && unlocked == previousMagnetUnlocked) return;
        previousMagnet = magnet.copy();
        previousMagnetUnlocked = unlocked;
        UIElement root = subWindows.root("magnet");
        Component title = unlocked ? text("magnet") : text("magnet").copy().append(" · ").append(text("reason.locked"));
        label(root, "popup_title").setText(title);
        tooltip(window, "magnet", title);
        magnetUi.refresh(magnet, unlocked);
        setCells(pickup, magnet.getList("pickup", Tag.TAG_COMPOUND), true);
        setCells(insert, magnet.getList("insert", Tag.TAG_COMPOUND), true);
        for (TerminalResourceCell cell : pickup) {
            cell.setActive(unlocked);
            cell.configurable(unlocked);
        }
        for (TerminalResourceCell cell : insert) {
            cell.setActive(unlocked);
            cell.configurable(unlocked);
        }
    }

    public void geometry(int width, int height) {
        screenWidth = width;
        screenHeight = height;
        int operationWidth = TerminalUiLayout.operationWidth(TerminalWorkspace.byId(workspace));
        viewport = adjusting && resizing ?
                viewport.resize(width, height, preferences.width, preferences.height, preferences.folded, operationWidth, pinnedUi.rows(), pinnedUi.automaticCount()) :
                TerminalViewportLayout.compute(width, height, preferences.xPercent, preferences.yPercent,
                        preferences.width, preferences.height, visible, preferences.folded, operationWidth, pinnedUi.rows(), pinnedUi.automaticCount());
        int positionWidth = visible ? viewport.width() : TerminalViewportLayout.ENTRY_SIZE;
        int positionHeight = visible ? viewport.height() : TerminalViewportLayout.ENTRY_SIZE;
        preferences.xPercent = width <= positionWidth ? 0 : (float) viewport.x() / (width - positionWidth);
        preferences.yPercent = height <= positionHeight ? 0 : (float) viewport.y() / (height - positionHeight);
        preferences.width = viewport.width();
        preferences.height = viewport.height();
        window.layout(style -> style.leftPercent(width == 0 ? 0 : viewport.x() * 100F / width)
                .topPercent(height == 0 ? 0 : viewport.y() * 100F / height).width(viewport.width()).height(viewport.height()));
        TerminalUiLayout.resize(window, viewport);
        pinnedUi.layout(viewport);
        element(ui.rootElement, "entry").layout(style -> style.positionType(TaffyPosition.ABSOLUTE)
                .leftPercent(width == 0 ? 0 : viewport.x() * 100F / width)
                .topPercent(height == 0 ? 0 : viewport.y() * 100F / height)
                .width(TerminalViewportLayout.ENTRY_SIZE).height(TerminalViewportLayout.ENTRY_SIZE).paddingAll(0));
        element(window, "workspaces").setVisible(!preferences.folded);
        for (TerminalWorkspace mode : TerminalWorkspace.values())
            stateClass(element(window, "tab_" + mode.id()), "selected", !preferences.folded && workspace.equals(mode.id()));
        int cellCount = viewport.cellCount();
        boolean gridChanged = !positionedStorage.equals(viewport.cells());
        if (storage.size() != cellCount) {
            createCells(element(window, "storage"), storage, cellCount, viewport.columns(), (index, event) -> storageClick(storage.get(index), event));
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
        keyTypeUi.resize(width, height);
        for (String name : subWindows.names()) {
            boolean keyTypesPopup = name.equals("key_types");
            int popupWidth = keyTypesPopup ? keyTypeUi.width() : TerminalUiLayout.popupWidth(name);
            int popupHeight = keyTypesPopup ? keyTypeUi.height() : TerminalUiLayout.popupHeight(name);
            subWindows.place(name, width, height,
                    new Rect2i(viewport.x() + viewport.width() + 3, viewport.y(), popupWidth, popupHeight),
                    name.equals("quantity") ? 12 : 0, 0);
        }
        if (gridChanged && snapshot.contains("enabled")) {
            offset = viewport.clampOffset(offset, storageIndex.projected().size());
            view(false);
        }
        updateScrollbar();
        geometryDirty = true;
    }

    public boolean beginDrag(double x, double y, int button) {
        if (subWindows.contains(x, y)) return subWindows.beginDrag(x, y, button);
        if (button != 0) return false;
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
        if (subWindows.drag(x, y)) {
            geometryDirty = true;
            return true;
        }
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
        if (subWindows.endDrag()) {
            geometryDirty = true;
            return true;
        }
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
        return adjusting || subWindows.dragging();
    }

    /** Moves the shared terminal origin to the cursor on the client thread, preserving its open state and size. */
    public void moveToMouse(double x, double y) {
        entryPressed = false;
        entryDragging = false;
        adjusting = false;
        resizing = false;
        subWindows.cancelDrag();
        moveWindow((int) Math.round(x) - TerminalViewportLayout.ENTRY_SIZE / 2,
                (int) Math.round(y) - TerminalViewportLayout.ENTRY_SIZE / 2);
        preferences.save();
    }

    public boolean takeGeometryDirty() {
        boolean dirty = geometryDirty;
        geometryDirty = false;
        return dirty;
    }

    public boolean contains(double x, double y) {
        return visible ? over(element(window, "panel"), x, y) || over(element(window, "sidebar"), x, y) || over(element(window, "tabs"), x, y) ||
                subWindows.contains(x, y) : over(element(ui.rootElement, "entry"), x, y);
    }

    public boolean quickMoveToNetwork() {
        return visible && preferences.shiftClickToNetwork && snapshot.getBoolean("connected");
    }

    private void refreshDestination() {
        stateClass(element(window, "destination"), "option-enabled", preferences.shiftClickToNetwork);
        tooltip(window, "destination", text(preferences.shiftClickToNetwork ? "to_network" : "to_inventory"));
    }

    public @Nullable TerminalWorkspace encodingWorkspace() {
        TerminalWorkspace page = TerminalWorkspace.byId(workspace);
        return visible && !preferences.folded && page.supportsEncoding() ? page : null;
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
            element.setAllowHitTest(interactive);
        });
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
        rectangles.add(rect(element(window, "sidebar")));
        rectangles.add(new Rect2i(viewport.x() + viewport.width() - TerminalViewportLayout.TAB_WIDTH,
                viewport.y() + viewport.height() - 72, TerminalViewportLayout.TAB_WIDTH, 70));
        rectangles.addAll(subWindows.rectangles());
        amountUi.extraRectangles(rectangles);
        return rectangles;
    }

    /**
     * Visible recipe cells accept viewer templates; the server returns any real input before replacing it.
     */
    public ObjectList<GhostTarget> ghostTargets() {
        ObjectList<GhostTarget> targets = new ObjectArrayList<>();
        if (!visible) return targets;
        if (subWindows.root("filters").isVisible()) {
            collectTargets(targets, pickup, "pickup");
            collectTargets(targets, insert, "insert");
        }
        collectTargets(targets, pinnedUi.manualCells(), "pins");
        if (!preferences.folded) {
            if (!workspace.equals("anvil")) collectTargets(targets, inputs, "inputs");
            if (workspace.equals("processing")) collectTargets(targets, outputs, "outputs");
        }
        targets.removeIf(target -> subWindows.covers(target.area(), target.inventory().equals("pickup") || target.inventory().equals("insert") ? "filters" : null));
        return targets;
    }

    private static void collectTargets(ObjectList<GhostTarget> targets, ObjectList<TerminalResourceCell> cells, String inventory) {
        for (int index = 0; index < cells.size(); index++)
            if (cells.get(index).isVisible() && cells.get(index).isActive()) targets.add(new GhostTarget(rect(cells.get(index)), inventory, index));
    }

    public void drop(GhostTarget target, GenericStack stack) {
        CompoundTag data = string("inventory", target.inventory());
        data.putInt("index", target.index());
        data.putInt("button", 0);
        data.putLong("amount", Math.max(1, stack.amount()));
        data.put("stack", GenericStack.writeTag(registries, new GenericStack(stack.what(), Math.max(1, stack.amount()))));
        request.accept(target.inventory().equals("pins") ? "pin" : "ghost", data);
    }

    public record GhostTarget(Rect2i area, String inventory, int index) {}

    public void open(String name) {
        open(name, null);
    }

    private void open(String name, @Nullable String parent) {
        subWindows.open(name, parent);
        geometryDirty = true;
    }

    public boolean closePopup() {
        if (!subWindows.closeTop()) return false;
        geometryDirty = true;
        return true;
    }

    private void closePopup(String name) {
        subWindows.close(name);
        geometryDirty = true;
    }

    public void visible(boolean value) {
        visible = value;
        preferences.open = value;
        window.setVisible(value);
        element(ui.rootElement, "entry").setVisible(!value);
        if (!value) {
            window.selfAndAllChildren().forEach(element -> {
                if (element instanceof TerminalActionButton button) button.resetInteraction();
            });
            Objects.requireNonNull(window.getModularUI()).clearFocus();
            subWindows.closeAll();
        }
        moveWindow(viewport.x(), viewport.y());
        preferences.save();
        CompoundTag data = new CompoundTag();
        data.putBoolean("value", value);
        request.accept("visible", data);
        geometryDirty = true;
    }

    private void moveWindow(int x, int y) {
        int availableX = Math.max(0, screenWidth - (visible ? viewport.width() : TerminalViewportLayout.ENTRY_SIZE));
        int availableY = Math.max(0, screenHeight - (visible ? viewport.height() : TerminalViewportLayout.ENTRY_SIZE));
        preferences.xPercent = availableX == 0 ? 0 : (float) Math.clamp(x, 0, availableX) / availableX;
        preferences.yPercent = availableY == 0 ? 0 : (float) Math.clamp(y, 0, availableY) / availableY;
        geometry(screenWidth, screenHeight);
    }

    private void networkAction(String action, CompoundTag data) {
        if (snapshot.getBoolean("connected")) request.accept(action, data);
    }

    private Component connectionStatus() {
        Component status = text("reason." + snapshot.getString("reason"));
        String message = snapshot.getString("message");
        return message.isEmpty() ? status : status.copy().append("\n").append(text("message." + message));
    }

    private void storageClick(TerminalResourceCell cell, UIEvent event) {
        GenericStack stack = cell.stack();
        if (stack == null) return;
        if (event.button == 2 || cell.craftable() && (UIElement.isCtrlDown() || stack.amount() == 0)) {
            networkAction("craft_request", key(stack.what()));
        } else {
            CompoundTag data = key(stack.what());
            data.putInt("button", event.button);
            data.putBoolean("shift", UIElement.isShiftDown());
            networkAction("storage", data);
        }
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
        button.style(style -> style.tooltips(TerminalTooltipLines.split(text(id))));
        button.setOnClick(event -> {
            if (event.button == 0) action.run();
        });
    }

    private static void tooltip(UIElement root, String id, Component value) {
        element(root, id).style(style -> style.tooltips(TerminalTooltipLines.split(value)));
    }

    private static void stateClass(UIElement element, String name, boolean value) {
        if (value) {
            if (!element.hasClass(name)) element.addClass(name);
        } else element.removeClass(name);
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
