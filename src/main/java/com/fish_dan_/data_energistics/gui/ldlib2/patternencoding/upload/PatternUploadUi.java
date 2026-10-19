package com.fish_dan_.data_energistics.gui.ldlib2.patternencoding.upload;

import com.fish_dan_.data_energistics.common.crafting.pattern.EncodedPatternRecipeReference;
import com.fish_dan_.data_energistics.gui.ldlib2.terminal.TerminalActionButton;
import com.fish_dan_.data_energistics.gui.ldlib2.terminal.TerminalResourceCell;
import com.fish_dan_.data_energistics.gui.ldlib2.terminal.TerminalUiLayout;
import com.fish_dan_.data_energistics.gui.ldlib2.terminal.text.TerminalSearchField;
import com.fish_dan_.data_energistics.gui.ldlib2.terminal.text.TerminalTooltipLines;
import com.fish_dan_.data_energistics.integration.viewer.xei.transfer.PatternProviderRecipeTypeNames;
import com.fish_dan_.data_energistics.menu.patternencoding.PatternEncodingPreviewMenu.SyncedPatternProvider;
import com.fish_dan_.data_energistics.menu.patternencoding.PatternEncodingPreviewMenu.SyncedPatternProviderLeaf;
import com.fish_dan_.data_energistics.menu.patternencoding.PatternEncodingPreviewMenu.SyncedPatternProviderList;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Scroller;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/** Shared LDLib2 upload view. Menus and the player terminal supply data and retain their own transactions. */
@NullMarked
public final class PatternUploadUi {

    private static final int ROW_HEIGHT = 20;
    public static final int MIN_WIDTH = 100;
    public static final int MIN_HEIGHT = 140;
    private final UIElement root;
    private final UIElement list = new UIElement();
    private final Button resize = TerminalUiLayout.icon("resize", "terminal-resize", "icon-resize", 0, 0, 4, 4);
    private final PatternUploadActions actions;
    private final UIElement viewport = new UIElement();
    private final Scroller.Vertical scrollbar = TerminalUiLayout.verticalScrollbar(new Scroller.Vertical());
    private final TextField search = new TerminalSearchField();
    private final TerminalResourceCell encoded = new TerminalResourceCell();
    private final Label status = new Label();
    private final ObjectList<ProviderRow> rows = new ObjectArrayList<>();
    private SyncedPatternProviderList providers = SyncedPatternProviderList.EMPTY;
    private PatternProviderSearchContext searchContext = PatternProviderSearchContext.resolve(null);
    private @Nullable GenericStack pattern;
    private @Nullable PatternProviderNameField editing;
    private long expandedGroup;
    private long selectedGroup;
    private long selectedLeaf;
    private boolean connected;
    private boolean rebuildPending;
    private boolean viewportPending;
    private int width;
    private int height;
    private int rowWidth;
    private int listHeight;

    public PatternUploadUi(UIElement root, PatternUploadActions actions) {
        this.root = root;
        this.actions = actions;
        root.addClass("terminal-upload-popup");
        search.addClass("terminal-field");
        search.addClass("terminal-search-field");
        search.setText("", false);
        search.textFieldStyle(style -> style.placeholder(text("search")));
        search.setTextResponder(value -> rebuildPending = true);
        search.addEventListener(UIEvents.MOUSE_DOWN, event -> {
            if (event.button == 1) {
                search.setText("");
                search.focus();
                event.stopPropagation();
            }
        });
        scrollbar.scrollContainer.addChildAt(TerminalUiLayout.scrollbarRail("upload_rail"), 0);
        scrollbar.setOnValueChanged(value -> viewportPending = true);
        list.addClass("terminal-upload-list");
        viewport.setOverflowVisible(false);
        list.addChildren(viewport, scrollbar);
        list.addEventListener(UIEvents.MOUSE_WHEEL, event -> {
            cancelEditing();
            scrollbar.scrollByWheel(event.deltaY);
            event.stopPropagation();
        });
        status.addClass("terminal-label");
        status.setText(text("message.offline"));
        status.textStyle(style -> style.adaptiveWidth(false).adaptiveHeight(false));
        status.setAllowHitTest(false);
        encoded.encodedPattern();
        encoded.addClass("terminal-upload-pattern");
        encoded.setAllowHitTest(false);
        resize.style(style -> style.tooltips(text("resize")));
        root.addChildren(TerminalUiLayout.at(encoded, 5, 19, 18, 18), search, status, list, resize);
        resize(MIN_WIDTH, MIN_HEIGHT);
    }

    /** Resizes only the list viewport and field widths; row and thumb pixels retain their native size. */
    public void resize(int width, int height) {
        if (this.width == width && this.height == height) return;
        cancelEditing();
        this.width = width;
        this.height = height;
        rowWidth = width - 20;
        listHeight = height - 50;
        root.layout(layout -> layout.width(width).height(height));
        TerminalUiLayout.at(TerminalUiLayout.require(root, "popup_title", Label.class), 7, 5, width - 31, 10);
        TerminalUiLayout.at(TerminalUiLayout.require(root, "close", UIElement.class), width - 17, 3, 14, 14);
        boolean showHint = width >= 160;
        int searchWidth = showHint ? width - 92 : width - 33;
        status.setDisplay(showHint);
        TerminalUiLayout.at(search, 28, 22, searchWidth, 14);
        TerminalUiLayout.at(status, searchWidth + 31, 20, 56, 18);
        TerminalUiLayout.at(list, 5, 42, rowWidth + 10, listHeight);
        TerminalUiLayout.at(viewport, 0, 0, rowWidth, listHeight);
        TerminalUiLayout.at(scrollbar, rowWidth + 4, 0, 6, listHeight);
        TerminalUiLayout.at(resize, width - 6, height - 6, 4, 4);
        updateScrollRange();
        rebuildViewport();
    }

    public void fit(int availableWidth, int availableHeight) {
        resize(Math.clamp(width, MIN_WIDTH, Math.max(MIN_WIDTH, availableWidth)), Math.clamp(height, MIN_HEIGHT, Math.max(MIN_HEIGHT, availableHeight)));
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public void apply(SyncedPatternProviderList providers, @Nullable GenericStack pattern, boolean connected) {
        if (!Objects.equals(this.pattern, pattern) || this.connected != connected) {
            this.pattern = pattern;
            encoded.entry(pattern, false);
            status.setText(text(!connected ? "message.offline" : pattern == null ? "upload_empty" : "upload_hint"));
        }
        var reference = pattern != null && pattern.what() instanceof AEItemKey itemKey ? EncodedPatternRecipeReference.get(itemKey.getReadOnlyStack()) : null;
        boolean contextChanged = !searchContext.current(reference);
        if (contextChanged) searchContext = PatternProviderSearchContext.resolve(reference);
        boolean changed = this.providers != providers &&
                (!this.providers.providers().equals(providers.providers()) || !Objects.equals(this.providers.rankingContext(), providers.rankingContext())) ||
                this.connected != connected || contextChanged;
        this.providers = providers;
        this.connected = connected;
        if (changed) rebuildPending = true;
    }

    /** Lets a live provider refresh wait for the active name edit instead of replacing its focused field. */
    public void tick() {
        if (editing != null && editing.isFocused()) return;
        if (rebuildPending) rebuild();
        if (viewportPending) rebuildViewport();
    }

    public boolean cancelEditing() {
        if (editing == null || !editing.isFocused()) return false;
        editing.blur();
        return true;
    }

    private void rebuild() {
        rebuildPending = false;
        rows.clear();
        var ordered = PatternProviderDisplayOrder.order(providers, search.getValue(), searchContext,
                id -> BuiltInRegistries.ITEM.get(id).getDescription().getString(), PatternProviderRecipeTypeNames::resolve);
        for (SyncedPatternProvider group : ordered) {
            rows.add(new ProviderRow(group, -1));
            if (expandedGroup == group.id()) {
                for (int index = 0; index < group.leaves().size(); index++) rows.add(new ProviderRow(group, index));
            }
        }
        updateScrollRange();
    }

    private void updateScrollRange() {
        int maximum = Math.max(0, rows.size() * ROW_HEIGHT - listHeight);
        scrollbar.setRange(0, maximum);
        scrollbar.setValue(Math.min(scrollbar.getValue(), maximum));
        scrollbar.scrollerStyle(style -> style.scrollDelta(maximum == 0 ? 1 : (float) ROW_HEIGHT / maximum));
        scrollbar.setActive(maximum > 0);
        if (maximum == 0) scrollbar.addClass("inactive-scroll");
        else scrollbar.removeClass("inactive-scroll");
        viewportPending = true;
    }

    /** Keeps the LDLib2 element tree bounded even when the network publishes thousands of providers. */
    private void rebuildViewport() {
        viewportPending = false;
        editing = null;
        viewport.clearAllChildren();
        int offset = Math.round(scrollbar.getValue());
        int first = offset / ROW_HEIGHT;
        int visibleRows = (listHeight + ROW_HEIGHT - 1) / ROW_HEIGHT + 1;
        for (int index = first; index < Math.min(rows.size(), first + visibleRows); index++) {
            ProviderRow entry = rows.get(index);
            SyncedPatternProvider group = entry.group();
            int top = index * ROW_HEIGHT - offset;
            if (entry.leafIndex() < 0) {
                row(group, 0, group.displayName(), group.iconItemId(), group.usedPatternSlotCount(), group.patternSlotCount(), group.renameable(),
                        group.leaves().stream().anyMatch(SyncedPatternProviderLeaf::openable), null, top);
            } else {
                var leaf = group.leaves().get(entry.leafIndex());
                var location = leaf.location();
                Component detail = location.blockPos() == null ? null : Component.literal(location.dimensionId() + " " + location.blockPos().toShortString());
                row(group, leaf.id(), leaf.displayName(), leaf.iconItemId(), leaf.usedPatternSlotCount(), leaf.patternSlotCount(), leaf.renameable(), leaf.openable(), detail, top);
            }
        }
    }

    private void row(SyncedPatternProvider group, long leaf, Component name, ResourceLocation iconId, int used, int capacity, boolean renameable,
                     boolean openable, @Nullable Component detail, int top) {
        TerminalActionButton button = new TerminalActionButton();
        button.addClass("terminal-upload-button");
        button.addClass("terminal-upload-row");
        if (selectedGroup == group.id() && selectedLeaf == leaf) button.addClass("selected");
        button.noText();
        TerminalUiLayout.at(button, 0, top, rowWidth, ROW_HEIGHT);
        TerminalResourceCell icon = new TerminalResourceCell();
        icon.addClass("embedded-cell");
        ItemStack stack = BuiltInRegistries.ITEM.get(iconId).getDefaultInstance();
        icon.entry(stack.isEmpty() ? null : new GenericStack(AEItemKey.of(stack), 1), false);
        icon.setAllowHitTest(false);
        PatternProviderNameField field = new PatternProviderNameField(name.getString(), renameable,
                value -> actions.rename(group.id(), leaf, value));
        field.addEventListener(UIEvents.MOUSE_DOWN, event -> {
            if (event.button == 2 && renameable) editing = field;
        }, true);
        Label count = new Label();
        count.addClass("terminal-label");
        count.addClass("terminal-upload-count");
        count.addClass(used * 100L < capacity * 70L ? "capacity-available" : used * 100L < capacity * 90L ? "capacity-warning" : "capacity-critical");
        String capacityText = used + "/" + capacity;
        count.setText(Component.literal(capacityText));
        count.textStyle(style -> style.adaptiveWidth(false).adaptiveHeight(false));
        count.setAllowHitTest(false);
        int iconLeft = leaf == 0 ? 2 : 9;
        int nameLeft = iconLeft + 21;
        var font = Minecraft.getInstance().font;
        int countWidth = Math.min(rowWidth - nameLeft - 8, (int) Math.ceil(font.width(capacityText) * 7F / font.lineHeight));
        int countLeft = rowWidth - countWidth - 4;
        button.addChildren(TerminalUiLayout.at(icon, iconLeft, 1, 18, 18),
                TerminalUiLayout.at(field, nameLeft, 3, countLeft - nameLeft - 3, 14),
                TerminalUiLayout.at(count, countLeft, 1, countWidth, 18));
        var tooltip = new ObjectArrayList<Component>();
        tooltip.add(name);
        if (detail != null) tooltip.add(detail);
        tooltip.add(text("upload_row_gestures"));
        if (!openable) tooltip.add(Component.translatable("screen.data_energistics.pattern_writer_preview.leaf_open_unavailable"));
        button.style(style -> style.tooltips(TerminalTooltipLines.split(tooltip).toArray(Component[]::new)));
        button.setOnClick(event -> {
            selectedGroup = group.id();
            selectedLeaf = leaf;
            viewportPending = true;
            if (event.button == 1) {
                expandedGroup = expandedGroup == group.id() ? 0 : group.id();
                rebuildPending = true;
            } else if (event.button == 0 && connected) actions.upload(group.id(), leaf);
        });
        button.addEventListener(UIEvents.MOUSE_DOWN, event -> {
            if (event.button == 2 && connected && openable) {
                actions.open(group.id(), leaf);
                event.stopPropagation();
            }
        });
        viewport.addChild(button);
    }

    private record ProviderRow(SyncedPatternProvider group, int leafIndex) {}

    private static Component text(String key) {
        return Component.translatable("gui.data_energistics.player_terminal." + key);
    }
}
