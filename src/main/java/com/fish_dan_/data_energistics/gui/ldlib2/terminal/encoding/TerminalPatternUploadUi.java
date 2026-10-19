package com.fish_dan_.data_energistics.gui.ldlib2.terminal.encoding;

import com.fish_dan_.data_energistics.client.preferences.PatternEncodingClientPreferencesAccess;
import com.fish_dan_.data_energistics.gui.ldlib2.terminal.TerminalActionButton;
import com.fish_dan_.data_energistics.gui.ldlib2.terminal.TerminalResourceCell;
import com.fish_dan_.data_energistics.gui.ldlib2.terminal.TerminalUiLayout;
import com.fish_dan_.data_energistics.gui.ldlib2.terminal.text.TerminalTooltipLines;
import com.fish_dan_.data_energistics.menu.patternencoding.PatternEncodingPreviewMenu.SyncedPatternProvider;
import com.fish_dan_.data_energistics.menu.patternencoding.PatternEncodingPreviewMenu.SyncedPatternProviderList;
import com.fish_dan_.data_energistics.network.terminal.TerminalPatternProviderCodec;
import com.fish_dan_.data_energistics.util.TextSearch;

import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.jspecify.annotations.NullMarked;

import java.util.Arrays;
import java.util.Comparator;
import java.util.function.BiConsumer;

/** LDLib2 presentation of the existing grouped provider upload data; it does not replace the host menu. */
@NullMarked
public final class TerminalPatternUploadUi {

    private final RegistryAccess registries;
    private final BiConsumer<String, CompoundTag> request;
    private final ScrollerView list = new ScrollerView();
    private final TextField search = new TextField();
    private final TextField rename = new TextField();
    private final TerminalResourceCell encoded = new TerminalResourceCell();
    private final Label status = new Label();
    private SyncedPatternProviderList providers = SyncedPatternProviderList.EMPTY;
    private byte[] previous = new byte[0];
    private CompoundTag snapshot = new CompoundTag();
    private long selectedGroup;
    private long selectedLeaf;
    private boolean connected;
    private CompoundTag sentHistory = new CompoundTag();

    public TerminalPatternUploadUi(UIElement root, RegistryAccess registries, BiConsumer<String, CompoundTag> request) {
        this.registries = registries;
        this.request = request;
        search.addClass("terminal-field");
        search.setText("", false);
        search.textFieldStyle(style -> style.placeholder(text("search")));
        search.setTextResponder(value -> rebuild());
        rename.addClass("terminal-field");
        rename.setText("", false);
        rename.setTextRegexValidator(".{0,50}");
        TerminalUiLayout.silentScrollbar(list.verticalScroller);
        TerminalUiLayout.silentScrollbar(list.horizontalScroller);
        list.viewPort.layout(layout -> layout.paddingAll(0));
        list.viewPort.style(style -> style.backgroundTexture(IGuiTexture.EMPTY));
        list.viewContainer.layout(layout -> layout.widthPercent(100).paddingAll(0));
        status.addClass("terminal-label");
        status.textStyle(style -> style.adaptiveWidth(false).adaptiveHeight(false));
        encoded.setAllowHitTest(false);
        root.addChildren(TerminalUiLayout.at(encoded, 5, 20, 18, 18), TerminalUiLayout.at(status, 28, 20, 157, 18),
                TerminalUiLayout.at(search, 5, 42, 180, 14), TerminalUiLayout.at(list, 5, 60, 180, 100),
                TerminalUiLayout.at(rename, 5, 166, 124, 14));
        TerminalActionButton renameButton = new TerminalActionButton();
        renameButton.addClass("terminal-control");
        renameButton.setText(text("upload_rename"));
        renameButton.setOnClick(event -> {
            if (event.button != 0 || selectedGroup == 0) return;
            CompoundTag data = target(selectedGroup, selectedLeaf);
            data.putString("name", rename.getValue());
            request.accept("upload_rename", data);
        });
        root.addChild(TerminalUiLayout.at(renameButton, 133, 166, 52, 14));
    }

    public void apply(CompoundTag value) {
        snapshot = value;
        var patterns = value.getCompound("work").getList("patterns", Tag.TAG_COMPOUND);
        ItemStack pattern = patterns.isEmpty() ? ItemStack.EMPTY : ItemStack.parseOptional(registries, patterns.getCompound(0));
        encoded.entry(pattern.isEmpty() ? null : new GenericStack(AEItemKey.of(pattern), pattern.getCount()), false);
        boolean newConnection = value.getBoolean("connected");
        byte[] data = value.getByteArray("upload_providers");
        boolean changed = connected != newConnection || !Arrays.equals(previous, data);
        connected = newConnection;
        status.setText(text(!connected ? "message.offline" : pattern.isEmpty() ? "upload_empty" : "upload_hint"));
        if (!changed) return;
        previous = data;
        providers = TerminalPatternProviderCodec.decode(data, registries);
        if (!connected) sentHistory = new CompoundTag();
        syncHistory();
        rebuild();
    }

    private void syncHistory() {
        var context = providers.rankingContext();
        if (!connected || context == null) return;
        var digests = new ObjectOpenHashSet<String>();
        for (var group : providers.providers()) for (var leaf : group.leaves()) digests.add(leaf.providerDigest());
        var statistics = new ObjectArrayList<>(PatternEncodingClientPreferencesAccess.get().statistics(context, digests));
        statistics.sort(Comparator.comparing(statistic -> statistic.providerDigest()));
        ListTag entries = new ListTag();
        for (var statistic : statistics) {
            CompoundTag entry = new CompoundTag();
            entry.putString("digest", statistic.providerDigest());
            entry.putLong("count", statistic.count());
            entries.add(entry);
        }
        CompoundTag history = new CompoundTag();
        history.putString("type", context.recipeTypeId().toString());
        history.put("history", entries);
        if (history.equals(sentHistory)) return;
        sentHistory = history;
        for (int first = 0; first < entries.size(); first += 512) {
            ListTag batch = new ListTag();
            for (int index = first; index < Math.min(first + 512, entries.size()); index++) batch.add(entries.get(index).copy());
            CompoundTag data = new CompoundTag();
            data.putString("type", context.recipeTypeId().toString());
            data.put("history", batch);
            request.accept("upload_history", data);
        }
    }

    private void rebuild() {
        list.viewContainer.clearAllChildren();
        String query = TextSearch.normalize(search.getValue());
        var ordered = new ObjectArrayList<>(providers.providers());
        ordered.sort(Comparator.comparing(SyncedPatternProvider::exactViewerMatch).reversed());
        for (SyncedPatternProvider group : ordered) {
            if (!TextSearch.matchesNormalized(group.displayName().getString(), query)) continue;
            row(group, 0, group.displayName(), group.iconItemId().toString(), group.usedPatternSlotCount(), group.patternSlotCount());
            if (selectedGroup == group.id()) {
                for (var leaf : group.leaves()) {
                    Component name = leaf.displayName().copy();
                    var location = leaf.location();
                    if (location.blockPos() != null) name = name.copy().append(" " + location.blockPos().toShortString());
                    row(group, leaf.id(), name, leaf.iconItemId().toString(), leaf.usedPatternSlotCount(), leaf.patternSlotCount());
                }
            }
        }
    }

    private void row(SyncedPatternProvider group, long leaf, Component name, String iconId, int used, int capacity) {
        TerminalActionButton button = new TerminalActionButton();
        button.addClass("terminal-control");
        button.noText();
        button.layout(layout -> layout.widthPercent(100).height(20).flexShrink(0));
        TerminalResourceCell icon = new TerminalResourceCell();
        ItemStack stack = BuiltInRegistries.ITEM.get(ResourceLocation.parse(iconId)).getDefaultInstance();
        icon.entry(stack.isEmpty() ? null : new GenericStack(AEItemKey.of(stack), 1), false);
        icon.setAllowHitTest(false);
        Label label = new Label();
        label.addClass("terminal-label");
        label.setText(name.copy().append(" " + used + "/" + capacity));
        label.textStyle(style -> style.adaptiveWidth(false).adaptiveHeight(false));
        label.setAllowHitTest(false);
        button.addChildren(TerminalUiLayout.at(icon, leaf == 0 ? 1 : 8, 1, 18, 18), TerminalUiLayout.at(label, leaf == 0 ? 21 : 28, 1, leaf == 0 ? 154 : 147, 18));
        button.style(style -> style.tooltips(TerminalTooltipLines.split(name, text("upload_row_gestures"))));
        button.setOnClick(event -> {
            selectedLeaf = leaf;
            if (event.button == 1) {
                selectedGroup = selectedGroup == group.id() && leaf == 0 ? 0 : group.id();
                if (!rename.isFocused()) rename.setText(name.getString(), false);
                rebuild();
            } else if (event.button == 0 && connected) {
                CompoundTag data = target(group.id(), leaf);
                var patterns = snapshot.getCompound("work").getList("patterns", Tag.TAG_COMPOUND);
                ItemStack pattern = patterns.isEmpty() ? ItemStack.EMPTY : ItemStack.parseOptional(registries, patterns.getCompound(0));
                if (pattern.isEmpty()) return;
                data.put("encoded", AEItemKey.of(pattern).toTagGeneric(registries));
                request.accept("upload_pattern", data);
            }
        });
        list.viewContainer.addChild(button);
    }

    private static CompoundTag target(long group, long leaf) {
        CompoundTag data = new CompoundTag();
        data.putLong("group", group);
        if (leaf != 0) data.putLong("leaf", leaf);
        return data;
    }

    private static Component text(String key) {
        return Component.translatable("gui.data_energistics.player_terminal." + key);
    }
}
