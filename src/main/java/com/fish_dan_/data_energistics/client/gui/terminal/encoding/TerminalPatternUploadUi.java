package com.fish_dan_.data_energistics.client.gui.terminal.encoding;

import com.fish_dan_.data_energistics.client.gui.patternencoding.upload.PatternUploadActions;
import com.fish_dan_.data_energistics.client.gui.patternencoding.upload.PatternUploadUi;
import com.fish_dan_.data_energistics.client.preferences.PatternEncodingClientPreferencesAccess;
import com.fish_dan_.data_energistics.common.menu.patternencoding.PatternEncodingPreviewMenu.SyncedPatternProviderList;
import com.fish_dan_.data_energistics.common.menu.patternprovider.PatternProviderClickStatistic;
import com.fish_dan_.data_energistics.common.network.packets.terminal.TerminalPatternProviderCodec;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;

import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.jspecify.annotations.NullMarked;

import java.util.Arrays;
import java.util.Comparator;
import java.util.function.BiConsumer;

/** Adapts the player session's upload snapshots and history to the shared upload view. */
@NullMarked
public final class TerminalPatternUploadUi implements PatternUploadActions {

    private final RegistryAccess registries;
    private final BiConsumer<String, CompoundTag> request;
    private final PatternUploadUi view;
    private SyncedPatternProviderList providers = SyncedPatternProviderList.EMPTY;
    private byte[] previous = new byte[0];
    private ItemStack encoded = ItemStack.EMPTY;
    private boolean connected;
    private CompoundTag sentHistory = new CompoundTag();

    public TerminalPatternUploadUi(UIElement root, RegistryAccess registries, BiConsumer<String, CompoundTag> request) {
        this.registries = registries;
        this.request = request;
        view = new PatternUploadUi(root, this);
    }

    public void apply(CompoundTag value) {
        var patterns = value.getCompound("work").getList("patterns", Tag.TAG_COMPOUND);
        encoded = patterns.isEmpty() ? ItemStack.EMPTY : ItemStack.parseOptional(registries, patterns.getCompound(0));
        boolean newConnection = value.getBoolean("connected");
        byte[] data = value.getByteArray("upload_providers");
        boolean changed = connected != newConnection || !Arrays.equals(previous, data);
        connected = newConnection;
        if (changed) {
            previous = data;
            providers = TerminalPatternProviderCodec.decode(data, registries);
            if (!connected) sentHistory = new CompoundTag();
            syncHistory();
        }
        view.apply(providers, encoded.isEmpty() ? null : new GenericStack(AEItemKey.of(encoded), encoded.getCount()), connected);
    }

    public void tick() {
        view.tick();
    }

    public boolean cancelEditing() {
        return view.cancelEditing();
    }

    public boolean providerShortcut(double x, double y, boolean rename) {
        return view.providerShortcut(x, y, rename);
    }

    public void resize(int width, int height) {
        view.resize(width, height);
    }

    public void fit(int width, int height) {
        view.fit(width, height);
    }

    public int width() {
        return view.width();
    }

    public int height() {
        return view.height();
    }

    @Override
    public void upload(long group, long leaf) {
        if (encoded.isEmpty()) return;
        CompoundTag data = target(group, leaf);
        data.put("encoded", AEItemKey.of(encoded).toTagGeneric(registries));
        request.accept("upload_pattern", data);
    }

    @Override
    public void rename(long group, long leaf, String name) {
        CompoundTag data = target(group, leaf);
        data.putString("name", name);
        request.accept("upload_rename", data);
    }

    @Override
    public void open(long group, long leaf) {
        request.accept("upload_open", target(group, leaf));
    }

    private void syncHistory() {
        var context = providers.rankingContext();
        if (!connected || context == null) return;
        var digests = new ObjectOpenHashSet<String>();
        for (var group : providers.providers()) for (var leaf : group.leaves()) digests.add(leaf.providerDigest());
        var statistics = new ObjectArrayList<>(PatternEncodingClientPreferencesAccess.get().statistics(context, digests));
        statistics.sort(Comparator.comparing(PatternProviderClickStatistic::providerDigest));
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

    private static CompoundTag target(long group, long leaf) {
        CompoundTag data = new CompoundTag();
        data.putLong("group", group);
        if (leaf != 0) data.putLong("leaf", leaf);
        return data;
    }
}
