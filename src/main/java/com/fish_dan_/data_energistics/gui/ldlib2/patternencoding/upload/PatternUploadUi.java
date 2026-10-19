package com.fish_dan_.data_energistics.gui.ldlib2.patternencoding.upload;

import com.fish_dan_.data_energistics.common.crafting.pattern.EncodedPatternRecipeReference;
import com.fish_dan_.data_energistics.gui.ldlib2.terminal.TerminalActionButton;
import com.fish_dan_.data_energistics.gui.ldlib2.terminal.TerminalResourceCell;
import com.fish_dan_.data_energistics.gui.ldlib2.terminal.TerminalUiLayout;
import com.fish_dan_.data_energistics.gui.ldlib2.terminal.text.TerminalTooltipLines;
import com.fish_dan_.data_energistics.integration.viewer.xei.transfer.PatternProviderRecipeTypeNames;
import com.fish_dan_.data_energistics.menu.patternencoding.PatternEncodingPreviewMenu.SyncedPatternProvider;
import com.fish_dan_.data_energistics.menu.patternencoding.PatternEncodingPreviewMenu.SyncedPatternProviderList;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollDisplay;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollerMode;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
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
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/** Shared LDLib2 upload view. Menus and the player terminal supply data and retain their own transactions. */
@NullMarked
public final class PatternUploadUi {

    private final PatternUploadActions actions;
    private final ScrollerView list = new ScrollerView();
    private final TextField search = new TextField();
    private final TerminalResourceCell encoded = new TerminalResourceCell();
    private final Label status = new Label();
    private SyncedPatternProviderList providers = SyncedPatternProviderList.EMPTY;
    private PatternProviderSearchContext searchContext = PatternProviderSearchContext.resolve(null);
    private @Nullable PatternProviderNameField editing;
    private long expandedGroup;
    private long selectedGroup;
    private long selectedLeaf;
    private boolean connected;
    private boolean rebuildPending;

    public PatternUploadUi(UIElement root, PatternUploadActions actions) {
        this.actions = actions;
        root.addClass("terminal-upload-popup");
        search.addClass("terminal-field");
        search.addClass("terminal-search-field");
        search.setText("", false);
        search.textFieldStyle(style -> style.placeholder(text("search")));
        search.setTextResponder(value -> rebuild());
        TerminalUiLayout.verticalScrollbar(list.verticalScroller);
        list.verticalScroller.scrollContainer.addChildAt(TerminalUiLayout.scrollbarRail("upload_rail"), 0);
        TerminalUiLayout.silentScrollbar(list.horizontalScroller);
        list.addClass("terminal-upload-list");
        list.scrollerStyle(style -> style.mode(ScrollerMode.VERTICAL).horizontalScrollDisplay(ScrollDisplay.NEVER));
        list.viewPort.layout(layout -> layout.paddingAll(0));
        list.viewContainer.layout(layout -> layout.widthPercent(100).paddingAll(0).gapAll(0));
        status.addClass("terminal-label");
        status.textStyle(style -> style.adaptiveWidth(false).adaptiveHeight(false));
        status.setAllowHitTest(false);
        encoded.encodedPattern();
        encoded.addClass("terminal-upload-pattern");
        encoded.setAllowHitTest(false);
        root.addChildren(TerminalUiLayout.at(encoded, 5, 19, 18, 18), TerminalUiLayout.at(search, 28, 22, 90, 14),
                TerminalUiLayout.at(status, 121, 20, 64, 18), TerminalUiLayout.at(list, 5, 42, 180, 138));
    }

    public void apply(SyncedPatternProviderList providers, @Nullable GenericStack pattern, boolean connected) {
        encoded.entry(pattern, false);
        status.setText(text(!connected ? "message.offline" : pattern == null ? "upload_empty" : "upload_hint"));
        var reference = pattern != null && pattern.what() instanceof AEItemKey itemKey ? EncodedPatternRecipeReference.get(itemKey.getReadOnlyStack()) : null;
        boolean contextChanged = !searchContext.current(reference);
        if (contextChanged) searchContext = PatternProviderSearchContext.resolve(reference);
        boolean changed = this.providers != providers || this.connected != connected || contextChanged;
        this.providers = providers;
        this.connected = connected;
        if (changed) rebuild();
    }

    /** Lets a live provider refresh wait for the active name edit instead of replacing its focused field. */
    public void tick() {
        if (rebuildPending && (editing == null || !editing.isFocused())) rebuild();
    }

    public boolean cancelEditing() {
        if (editing == null || !editing.isFocused()) return false;
        editing.blur();
        return true;
    }

    private void rebuild() {
        if (editing != null && editing.isFocused()) {
            rebuildPending = true;
            return;
        }
        editing = null;
        rebuildPending = false;
        list.viewContainer.clearAllChildren();
        var ordered = PatternProviderDisplayOrder.order(providers, search.getValue(), searchContext,
                id -> BuiltInRegistries.ITEM.get(id).getDescription().getString(), PatternProviderRecipeTypeNames::resolve);
        for (SyncedPatternProvider group : ordered) {
            row(group, 0, group.displayName(), group.iconItemId(), group.usedPatternSlotCount(), group.patternSlotCount(), group.renameable(),
                    group.leaves().stream().anyMatch(leaf -> leaf.openable()), null);
            if (expandedGroup == group.id()) {
                for (var leaf : group.leaves()) {
                    var location = leaf.location();
                    Component detail = location.blockPos() == null ? null : Component.literal(location.dimensionId() + " " + location.blockPos().toShortString());
                    row(group, leaf.id(), leaf.displayName(), leaf.iconItemId(), leaf.usedPatternSlotCount(), leaf.patternSlotCount(), leaf.renameable(), leaf.openable(), detail);
                }
            }
        }
    }

    private void row(SyncedPatternProvider group, long leaf, Component name, ResourceLocation iconId, int used, int capacity, boolean renameable,
                     boolean openable, @Nullable Component detail) {
        TerminalActionButton button = new TerminalActionButton();
        button.addClass("terminal-upload-button");
        button.addClass("terminal-upload-row");
        if (selectedGroup == group.id() && selectedLeaf == leaf) button.addClass("selected");
        button.noText();
        button.layout(layout -> layout.widthPercent(100).height(20).paddingAll(1).paddingLeft(leaf == 0 ? 2 : 9).paddingRight(4)
                .gapColumn(3).flexDirection(FlexDirection.ROW).alignItems(AlignItems.CENTER).flexShrink(0));
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
            if (event.button == 1) {
                expandedGroup = expandedGroup == group.id() ? 0 : group.id();
                rebuild();
            } else if (event.button == 0 && connected) actions.upload(group.id(), leaf);
        });
        button.addEventListener(UIEvents.MOUSE_DOWN, event -> {
            if (event.button == 2 && connected && openable) {
                actions.open(group.id(), leaf);
                event.stopPropagation();
            }
        });
        list.viewContainer.addChild(button);
    }

    private static Component text(String key) {
        return Component.translatable("gui.data_energistics.player_terminal." + key);
    }
}
