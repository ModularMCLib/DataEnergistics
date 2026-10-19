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
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Scroller;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/** Shared LDLib2 upload view. Menus and the player terminal supply data and retain their own transactions. */
@NullMarked
public final class PatternUploadUi {

    private static final int ROW_HEIGHT = 20;
    private static final int LIST_HEIGHT = 138;
    private static final int ROW_WIDTH = TerminalUiLayout.popupWidth("upload") - 20;
    private static final int VISIBLE_ROWS = (LIST_HEIGHT + ROW_HEIGHT - 1) / ROW_HEIGHT + 1;
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

    public PatternUploadUi(UIElement root, PatternUploadActions actions) {
        this.actions = actions;
        UIElement list = new UIElement();
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
        list.addChildren(TerminalUiLayout.at(viewport, 0, 0, ROW_WIDTH, LIST_HEIGHT),
                TerminalUiLayout.at(scrollbar, ROW_WIDTH + 4, 0, 6, LIST_HEIGHT));
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
        int searchWidth = TerminalUiLayout.popupWidth("upload") - 92;
        root.addChildren(TerminalUiLayout.at(encoded, 5, 19, 18, 18), TerminalUiLayout.at(search, 28, 22, searchWidth, 14),
                TerminalUiLayout.at(status, searchWidth + 31, 20, 56, 18), TerminalUiLayout.at(list, 5, 42, ROW_WIDTH + 10, LIST_HEIGHT));
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
        int maximum = Math.max(0, rows.size() * ROW_HEIGHT - LIST_HEIGHT);
        scrollbar.setRange(0, maximum);
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
        for (int index = first; index < Math.min(rows.size(), first + VISIBLE_ROWS); index++) {
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
        button.layout(layout -> layout.widthPercent(100).height(20).paddingAll(1).paddingLeft(leaf == 0 ? 2 : 9).paddingRight(4)
                .gapColumn(3).flexDirection(FlexDirection.ROW).alignItems(AlignItems.CENTER).flexShrink(0));
        TerminalUiLayout.at(button, 0, top, ROW_WIDTH, ROW_HEIGHT);
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
        count.setText(Component.literal(used + "/" + capacity));
        count.layout(layout -> layout.height(18).flexShrink(0));
        count.textStyle(style -> style.adaptiveWidth(true).adaptiveHeight(false));
        count.setAllowHitTest(false);
        button.addChildren(icon, field, count);
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
