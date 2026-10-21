package com.fish_dan_.data_energistics.common.terminal.player.encoding;

import com.fish_dan_.data_energistics.common.crafting.pattern.EncodedPatternRecipeReference;
import com.fish_dan_.data_energistics.common.menu.patternencoding.PatternEncodingPreferenceSession;
import com.fish_dan_.data_energistics.common.menu.patternencoding.PatternEncodingPreviewMenu.SyncedPatternProviderList;
import com.fish_dan_.data_energistics.common.menu.patternencoding.PatternEncodingRankingContext;
import com.fish_dan_.data_energistics.common.menu.patternencoding.PatternUploadRecorder;
import com.fish_dan_.data_energistics.common.menu.patternprovider.PatternProviderSyncHelper;
import com.fish_dan_.data_energistics.common.menu.patternprovider.PatternProviderSyncTracker;
import com.fish_dan_.data_energistics.common.network.packets.patternencoding.PatternUploadSource;
import com.fish_dan_.data_energistics.common.network.packets.terminal.TerminalPatternProviderCodec;
import com.fish_dan_.data_energistics.common.terminal.player.connection.TerminalConnection;
import com.fish_dan_.data_energistics.common.terminal.player.persistence.TerminalPlayerState;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.IGrid;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.core.definitions.AEItems;
import appeng.helpers.patternprovider.PatternContainer;
import appeng.parts.encoding.EncodingMode;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StringUtil;
import net.minecraft.world.item.ItemStack;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.Reference2LongMap;
import it.unimi.dsi.fastutil.objects.Reference2LongOpenHashMap;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/** Session-scoped adapter for the mod's shared provider discovery, upload transactions and history. */
@NullMarked
public final class TerminalPatternUpload {

    private final Reference2LongMap<PatternContainer> ids = new Reference2LongOpenHashMap<>();
    private final Long2ObjectMap<ObjectList<PatternContainer>> targets = new Long2ObjectOpenHashMap<>();
    private final PatternProviderSyncTracker tracker = new PatternProviderSyncTracker();
    private final PatternEncodingPreferenceSession preferences = PatternEncodingPreferenceSession.forMenu(this);
    private @Nullable IGrid grid;
    private long nextId;
    private SyncedPatternProviderList providers = SyncedPatternProviderList.EMPTY;
    private byte[] encodedProviders = new byte[0];

    public void refresh(ServerPlayer player, TerminalPlayerState state, @Nullable TerminalConnection connection, boolean force) {
        IGrid nextGrid = connection == null ? null : connection.grid();
        if (grid != nextGrid) {
            ids.clear();
            targets.clear();
            tracker.clear();
            grid = nextGrid;
        }
        if (nextGrid == null || !state.workspace().supportsEncoding()) {
            providers = SyncedPatternProviderList.EMPTY;
            encodedProviders = new byte[0];
            tracker.clear();
            return;
        }
        AEItemKey definition = AEItemKey.of(state.work().patterns.getItem(0));
        var reference = definition == null ? null : EncodedPatternRecipeReference.get(definition.getReadOnlyStack());
        PatternEncodingRankingContext context = reference == null ? null : PatternEncodingRankingContext.of(reference.recipeTypeId());
        preferences.setRecipeContext(context, definition == null ? null : EncodedPatternRecipeReference.getProcessingRecipeId(definition.getReadOnlyStack()));
        var publication = PatternProviderSyncTracker.capturePublicationVersion(nextGrid);
        long tick = player.server.getTickCount();
        if (!force && !tracker.needsRefresh(publication, tick, context, definition)) return;
        providers = PatternProviderSyncHelper.collectSyncedPatternProviders(player, definition, nextGrid, ids, targets,
                () -> ++nextId, context, preferences.leafCounts());
        encodedProviders = TerminalPatternProviderCodec.encode(providers, player.registryAccess());
        tracker.refreshed(publication, tick, context, definition);
    }

    public void snapshot(CompoundTag snapshot) {
        snapshot.putByteArray("upload_providers", encodedProviders);
    }

    public String action(ServerPlayer player, TerminalPlayerState state, TerminalConnection connection, String action, CompoundTag request) {
        refresh(player, state, connection, true);
        if (!state.workspace().supportsEncoding()) return "invalid_recipe";
        if (action.equals("upload_refresh")) return "";
        if (action.equals("upload_history")) {
            var context = providers.rankingContext();
            if (context == null || !context.recipeTypeId().toString().equals(request.getString("type"))) return "";
            var counts = new Object2LongOpenHashMap<>(preferences.leafCounts());
            var visible = new ObjectOpenHashSet<String>();
            for (var row : providers.providers()) for (var leaf : row.leaves()) visible.add(leaf.providerDigest());
            var history = request.getList("history", Tag.TAG_COMPOUND);
            if (history.size() > 512) throw new IllegalArgumentException("Upload history batch is too large");
            for (Tag raw : history) {
                CompoundTag entry = (CompoundTag) raw;
                String digest = entry.getString("digest");
                long count = entry.getLong("count");
                if (!visible.contains(digest) || count < 0) throw new IllegalArgumentException("Invalid terminal provider history");
                counts.put(digest, Math.max(counts.getLong(digest), count));
            }
            preferences.replaceLeafCounts(counts);
            refresh(player, state, connection, true);
            return "";
        }
        long group = request.getLong("group");
        ObjectList<PatternContainer> selected;
        if (request.contains("leaf")) {
            PatternContainer leaf = PatternProviderSyncHelper.findProviderLeafById(ids, targets, group, request.getLong("leaf"));
            selected = leaf == null ? null : ObjectList.of(leaf);
        } else selected = PatternProviderSyncHelper.findProvidersById(targets, group);
        if (selected == null || selected.isEmpty()) return "upload_unavailable";
        if (action.equals("upload_rename")) {
            String name = StringUtil.filterText(request.getString("name"));
            if (name.length() > 50) throw new IllegalArgumentException("Provider name is too long");
            PatternProviderSyncHelper.renamePatternProviders(selected, name);
            refresh(player, state, connection, true);
            return "";
        }
        if (!action.equals("upload_pattern")) throw new IllegalArgumentException("Unknown upload action " + action);
        ItemStack encoded = state.work().patterns.getItem(0);
        AEKey expected = AEKey.fromTagGeneric(player.registryAccess(), request.getCompound("encoded"));
        if (!PatternDetailsHelper.isEncodedPattern(encoded) || !AEItemKey.of(encoded).equals(expected)) return "upload_unavailable";
        var preferred = providers.providers().stream().filter(row -> row.id() == group).findFirst().orElseThrow().preferredWorkstationId();
        var context = new PatternProviderSyncHelper.PatternUploadContext(player,
                switch (state.workspace()) {
                    case CRAFTING -> EncodingMode.CRAFTING;
                    case PROCESSING -> EncodingMode.PROCESSING;
                    case SMITHING -> EncodingMode.SMITHING_TABLE;
                    case STONECUTTING -> EncodingMode.STONECUTTING;
                    case ANVIL -> throw new IllegalStateException("The anvil cannot upload a pattern");
                }, preferences.rankingContext(), preferred);
        var result = PatternProviderSyncHelper.transferEncodedPatternToProvidersChecked(selected, encoded, context);
        if (result.hasWarning()) player.sendSystemMessage(result.warningMessageOrThrow());
        if (result.rejected()) {
            player.sendSystemMessage(result.rejectionMessageOrThrow());
            return "upload_unavailable";
        }
        if (result.duplicateFound()) {
            state.work().patterns.setItem(0, AEItems.BLANK_PATTERN.stack(encoded.getCount()));
            player.sendSystemMessage(Component.translatable("message.data_energistics.pattern_provider.duplicate_cleared"));
        } else if (result.transferred()) {
            state.work().patterns.setItem(0, result.remainder());
            PatternUploadRecorder.record(player, preferences, preferences.rankingContext(), result.committedTarget(), PatternUploadSource.DATA_ENERGISTICS);
        }
        refresh(player, state, connection, true);
        return result.transferred() ? "uploaded" : "upload_unavailable";
    }

    /** Resolves only IDs from the current authorized network snapshot before opening an existing provider menu. */
    public ObjectList<PatternContainer> openTargets(ServerPlayer player, TerminalPlayerState state, TerminalConnection connection, CompoundTag request) {
        if (!state.workspace().supportsEncoding()) return ObjectList.of();
        refresh(player, state, connection, true);
        long group = request.getLong("group");
        if (request.contains("leaf")) {
            PatternContainer leaf = PatternProviderSyncHelper.findProviderLeafById(ids, targets, group, request.getLong("leaf"));
            return leaf == null ? ObjectList.of() : ObjectList.of(leaf);
        }
        ObjectList<PatternContainer> selected = PatternProviderSyncHelper.findProvidersById(targets, group);
        return selected == null ? ObjectList.of() : selected;
    }

    public void close() {
        PatternEncodingPreferenceSession.clearForMenu(this);
        ids.clear();
        targets.clear();
    }
}
