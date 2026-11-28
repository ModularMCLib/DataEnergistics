package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.orchestration;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.CraftingQuantityMode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnosticCode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityPlanningControl;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.topology.TrinityCraftingTopology;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.topology.TrinityGraphTopologyAnalyzer;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.topology.TrinityPatternVariantExpander;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityCraftingGraphPattern;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityCraftingGraphSnapshot;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternIdentity;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.inventory.TrinityPlanningInventory;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.request.TrinityPlanningLimits;
import com.fish_dan_.data_energistics.common.trinity.pattern.TrinityPatternPublicationSignature;
import com.fish_dan_.data_energistics.common.trinity.pattern.TrinityPatternPublicationSignature.Alternative;
import com.fish_dan_.data_energistics.common.trinity.pattern.TrinityPatternPublicationSignature.Input;
import com.fish_dan_.data_energistics.utils.FastUtilCollections;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@GameTestHolder(Data_Energistics.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TrinityPlanSnapshotGameTest {

    private static final String SNAPSHOT_RESOURCE = "/fixtures/trinity/plansnapshot_tds.json";
    private static final TrinityPlanningLimits LIMITS = new TrinityPlanningLimits(1_024, 32_768, 5_000_000, 1_000);
    private static final long BUDGET_NANOS = TimeUnit.MILLISECONDS.toNanos(LIMITS.planningBudgetMs());
    private static final Set<TrinityPlanningDiagnosticCode> INFEASIBLE_RESULTS = Set.of(
            TrinityPlanningDiagnosticCode.INSUFFICIENT_INPUT,
            TrinityPlanningDiagnosticCode.NO_EXECUTABLE_ORDER,
            TrinityPlanningDiagnosticCode.MIP_NO_INTEGER_SOLUTION,
            TrinityPlanningDiagnosticCode.NO_PRODUCTIVE_CYCLE);

    private TrinityPlanSnapshotGameTest() {}

    @TestHolder("trinity_plan_snapshot_tds")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void replayTdsSnapshot(GameTestHelper helper) {
        Snapshot snapshot = readSnapshot(helper.getLevel().registryAccess());
        GenericStack request = snapshot.request();
        TrinityCraftingGraphSnapshot graph = snapshot.graph();
        TrinityCraftingGraphSnapshot reachable = graph.reachableSubgraph(request.what());

        var expansion = TrinityPatternVariantExpander.create().expand(reachable, LIMITS.maxBindingVariants());
        if (!expansion.successful()) {
            helper.fail("Could not expand TDS sample bindings: " + expansion.diagnostic().code());
        }
        var analysis = TrinityGraphTopologyAnalyzer.create().analyze(
                reachable, expansion.value(), LIMITS.maxSccKeys(), TrinityPlanningControl.unbounded());
        if (!analysis.successful()) {
            helper.fail("Could not analyze the TDS sample topology: " + analysis.diagnostic().code());
        }
        TrinityPlanningInventory inventory = initialInventory(reachable, analysis.value());

        helper.assertValueEqual(request.amount(), 1L, "TDS request size");
        helper.assertValueEqual(graph.patterns().size(), 5_319, "TDS exported patterns");
        helper.assertValueEqual(reachable.patterns().size(), 3_025, "TDS reachable patterns");
        helper.assertValueEqual(inventory.unlimitedKeys().size(), 1_082, "TDS unlimited leaf materials");
        helper.assertValueEqual(inventory.finiteAmounts().size(), 588, "TDS finite cycle seeds");
        Data_Energistics.LOGGER.info(
                "Trinity TDS replay started with {} patterns ({} reachable), {} unlimited leaf materials and {} single-unit seeds",
                graph.patterns().size(), reachable.patterns().size(), inventory.unlimitedKeys().size(), inventory.finiteAmounts().size());

        long start = System.nanoTime();
        var result = TrinityGraphPlanner.create().plan(
                graph, request.what(), BigInteger.valueOf(request.amount()), CraftingQuantityMode.NET_NEW, inventory,
                LIMITS, TrinityPlanningControl.create(() -> false, System::nanoTime, BUDGET_NANOS));
        long elapsedNanos = System.nanoTime() - start;
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(elapsedNanos);
        if (result.successful()) {
            helper.fail("TDS request unexpectedly produced an executable plan after " + elapsedMs + " ms");
        }
        var diagnostic = result.diagnostic();
        Data_Energistics.LOGGER.info("Trinity TDS replay returned {} after {} ms ({})",
                diagnostic.code(), elapsedMs, diagnostic.message().getString());
        helper.assertTrue(INFEASIBLE_RESULTS.contains(diagnostic.code()),
                "Expected an infeasibility diagnostic for the TDS request, got " + diagnostic.code() + " after " + elapsedMs + " ms");
        helper.assertTrue(elapsedNanos <= BUDGET_NANOS,
                "The full TDS plan must finish within 1 second, but took " + elapsedNanos + " ns");
        helper.succeed();
    }

    private static TrinityPlanningInventory initialInventory(TrinityCraftingGraphSnapshot reachable, TrinityCraftingTopology topology) {
        ObjectOpenHashSet<AEKey> leaves = new ObjectOpenHashSet<>(reachable.keys());
        leaves.removeAll(topology.variantsByOutputKey().keySet());

        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> seeds = new Object2ObjectLinkedOpenHashMap<>();
        for (var component : topology.components()) {
            if (component.cyclic()) {
                var producers = topology.variantsByOutputComponent().get(component.index());
                boolean hasEntry = producers.stream().anyMatch(producer -> producer.inputs().keySet().stream()
                        .noneMatch(key -> topology.componentByKey().getInt(key) == component.index()));
                if (!hasEntry) {
                    component.keys().forEach(key -> seeds.put(key, BigInteger.ONE));
                }
            }
        }
        return new TrinityPlanningInventory(FastUtilCollections.immutableMap(seeds), FastUtilCollections.immutableSet(leaves));
    }

    private static Snapshot readSnapshot(HolderLookup.Provider registries) {
        var resource = TrinityPlanSnapshotGameTest.class.getResourceAsStream(SNAPSHOT_RESOURCE);
        if (resource == null) {
            throw new IllegalStateException("Missing Trinity plan snapshot " + SNAPSHOT_RESOURCE);
        }
        try (var reader = new InputStreamReader(resource, StandardCharsets.UTF_8)) {
            JsonObject document = JsonParser.parseReader(reader).getAsJsonObject();
            if (GsonHelper.getAsBigDecimal(document, "formatVersion").intValueExact() != 1) {
                throw new IllegalArgumentException("Expected a version 1 plan snapshot at " + SNAPSHOT_RESOURCE);
            }

            Object2ObjectMap<LegacyItem, AEItemKey> keys = new Object2ObjectOpenHashMap<>();
            GenericStack request = readStack(GsonHelper.getAsJsonObject(document, "root"), keys);
            var exportedPatterns = GsonHelper.getAsJsonArray(document, "patterns");
            ObjectArrayList<TrinityCraftingGraphPattern> patterns = new ObjectArrayList<>(exportedPatterns.size());
            for (int index = 0; index < exportedPatterns.size(); index++) {
                JsonObject exportedPattern = exportedPatterns.get(index).getAsJsonObject();
                ObjectArrayList<Input> inputs = new ObjectArrayList<>();
                for (var row : GsonHelper.getAsJsonArray(exportedPattern, "inputs")) {
                    inputs.add(new Input(1L, ObjectList.of(new Alternative(readStack(row.getAsJsonObject(), keys), null))));
                }
                ObjectArrayList<GenericStack> outputs = new ObjectArrayList<>();
                for (var row : GsonHelper.getAsJsonArray(exportedPattern, "outputs")) {
                    outputs.add(readStack(row.getAsJsonObject(), keys));
                }

                CompoundTag definition = new CompoundTag();
                definition.putInt("pattern_index", index);
                definition.putBoolean("craftable", GsonHelper.getAsBoolean(exportedPattern, "craftable"));
                definition.putBoolean("can_substitute", GsonHelper.getAsBoolean(exportedPattern, "canSubstitute"));
                definition.putInt("priority", GsonHelper.getAsBigDecimal(exportedPattern, "priority").intValueExact());
                var publication = new TrinityPatternPublicationSignature(snapshotKey(definition), inputs, outputs, false);
                patterns.add(new TrinityCraftingGraphPattern(TrinityPatternIdentity.capture(publication, registries), publication));
            }
            return new Snapshot(request, new TrinityCraftingGraphSnapshot(1L, patterns));
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read the Trinity plan snapshot at " + SNAPSHOT_RESOURCE, exception);
        }
    }

    private static GenericStack readStack(JsonObject row, Object2ObjectMap<LegacyItem, AEItemKey> keys) {
        String id = GsonHelper.getAsString(row, "id");
        long amount = GsonHelper.getAsBigDecimal(row, "count").longValueExact();
        if (id.isBlank() || amount <= 0L) {
            throw new IllegalArgumentException("Snapshot stacks require an item id and positive count: " + id + " x " + amount);
        }
        LegacyItem item = new LegacyItem(id, GsonHelper.getAsBigDecimal(row, "meta").intValueExact(),
                GsonHelper.getAsString(row, "nbt", null));
        return new GenericStack(keys.computeIfAbsent(item, LegacyItem::toKey), amount);
    }

    private static AEItemKey snapshotKey(CompoundTag identity) {
        ItemStack stack = new ItemStack(Items.PAPER);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(identity));
        return AEItemKey.of(stack);
    }

    private record Snapshot(GenericStack request, TrinityCraftingGraphSnapshot graph) {}

    private record LegacyItem(String id, int metadata, @Nullable String nbt) {

        private AEItemKey toKey() {
            CompoundTag identity = new CompoundTag();
            identity.putString("legacy_id", this.id);
            identity.putInt("legacy_meta", this.metadata);
            if (this.nbt != null) {
                identity.putString("legacy_nbt", this.nbt);
            }
            return snapshotKey(identity);
        }
    }
}
