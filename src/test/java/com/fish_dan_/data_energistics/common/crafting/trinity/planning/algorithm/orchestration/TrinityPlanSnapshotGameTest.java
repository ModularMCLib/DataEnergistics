package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.orchestration;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.CraftingQuantityMode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnosticCode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityAlgorithmResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityPlanningControl;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.topology.TrinityCraftingTopology;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.topology.TrinityGraphTopologyAnalyzer;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.topology.TrinityPatternVariantExpander;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityCraftingGraphPattern;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityCraftingGraphSnapshot;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternIdentity;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.inventory.TrinityPlanningInventory;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityCraftingPlan;
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
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@GameTestHolder(Data_Energistics.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TrinityPlanSnapshotGameTest {

    private static final String SNAPSHOT_RESOURCE = "/fixtures/trinity/plansnapshot_tds.json";
    private static final String SEED_STOCK_RESOURCE = "/fixtures/trinity/plansnapshot_tds_seeds.json";
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
        TrinityPlanningInventory singleSeeds = initialInventory(reachable, analysis.value());
        TrinityPlanningInventory stockedSeeds = withCycleStock(singleSeeds, snapshot.stock(), snapshot.seedOverrides());

        helper.assertValueEqual(request.amount(), 1L, "TDS request size");
        helper.assertValueEqual(graph.patterns().size(), 5_319, "TDS exported patterns");
        helper.assertValueEqual(reachable.patterns().size(), 3_025, "TDS reachable patterns");
        int largestComponent = analysis.value().components().stream().mapToInt(component -> component.keys().size()).max().orElseThrow();
        helper.assertValueEqual(largestComponent, 559, "TDS largest strongly connected component");
        helper.assertValueEqual(singleSeeds.unlimitedKeys().size(), 1_082, "TDS unlimited leaf materials");
        helper.assertValueEqual(singleSeeds.finiteAmounts().size(), 588, "TDS finite cycle seeds");
        Data_Energistics.LOGGER.info(
                "Trinity TDS replay loaded {} patterns ({} reachable), {} unlimited leaf materials and {} cycle seed keys",
                graph.patterns().size(), reachable.patterns().size(), singleSeeds.unlimitedKeys().size(),
                singleSeeds.finiteAmounts().size());

        var result = planWithinBudget(helper, snapshot, stockedSeeds, "stocked cycles");
        if (!result.successful()) {
            helper.fail("TDS with stocked cycles must produce an executable plan, got " + result.diagnostic().code());
        }
        var plan = result.value();
        helper.assertValueEqual(plan.finalOutput(), request, "TDS plan must deliver the exact requested book");
        helper.assertTrue(!plan.cycleRepeatBlocks().isEmpty(), "TDS plan must execute at least one seeded cycle");
        plan.initialExpectedInputs().forEach((key, amount) -> helper.assertTrue(stockedSeeds.covers(key, amount),
                "TDS plan requires " + amount + " of " + key + " outside the starting inventory"));

        var undersupplied = planWithinBudget(helper, snapshot, singleSeeds, "one unit per seed");
        if (undersupplied.successful()) {
            helper.fail("TDS with one unit per seed cannot start the required conversions");
        }
        helper.assertTrue(INFEASIBLE_RESULTS.contains(undersupplied.diagnostic().code()),
                "TDS with one unit per seed must report infeasibility, got " + undersupplied.diagnostic().code());
        helper.succeed();
    }

    private static TrinityAlgorithmResult<TrinityCraftingPlan> planWithinBudget(
                                                                                GameTestHelper helper, Snapshot snapshot,
                                                                                TrinityPlanningInventory inventory, String scenario) {
        GenericStack request = snapshot.request();
        long start = System.nanoTime();
        var result = TrinityGraphPlanner.create().plan(
                snapshot.graph(), request.what(), BigInteger.valueOf(request.amount()), CraftingQuantityMode.NET_NEW, inventory,
                LIMITS, TrinityPlanningControl.create(() -> false, System::nanoTime, BUDGET_NANOS));
        long elapsedNanos = System.nanoTime() - start;
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(elapsedNanos);
        if (result.successful()) {
            var plan = result.value();
            Data_Energistics.LOGGER.info(
                    "Trinity TDS replay with {} planned {} patterns and {} cycle blocks in {} ms ({})",
                    scenario, plan.patternFirings().size(), plan.cycleRepeatBlocks().size(), elapsedMs, plan.statistics().quality());
        } else {
            var diagnostic = result.diagnostic();
            Data_Energistics.LOGGER.info("Trinity TDS replay with {} returned {} in {} ms ({})",
                    scenario, diagnostic.code(), elapsedMs, diagnostic.message().getString());
        }
        helper.assertTrue(elapsedNanos <= BUDGET_NANOS,
                "TDS planning with " + scenario + " must finish within 1 second, but took " + elapsedNanos + " ns");
        return result;
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

    private static TrinityPlanningInventory withCycleStock(
                                                           TrinityPlanningInventory singleSeeds,
                                                           Object2ObjectMap<AEKey, BigInteger> exportedStock,
                                                           Object2ObjectMap<AEKey, BigInteger> cycleStock) {
        var seeds = new Object2ObjectLinkedOpenHashMap<>(singleSeeds.finiteAmounts());
        seeds.replaceAll((key, amount) -> exportedStock.getOrDefault(key, amount));
        // The fixture sets finite stock for the executable replay, including a conversion that still needs to run.
        cycleStock.forEach((key, amount) -> {
            if (seeds.replace(key, amount) == null) {
                throw new IllegalArgumentException("TDS reserve must belong to a closed cycle: " + key);
            }
        });
        return new TrinityPlanningInventory(FastUtilCollections.immutableMap(seeds), singleSeeds.unlimitedKeys());
    }

    private static Snapshot readSnapshot(HolderLookup.Provider registries) {
        String override = System.getenv("DE_TRINITY_PLAN_SNAPSHOT");
        Path path = override == null ? null : Path.of(override);
        if (path != null && !path.isAbsolute()) {
            throw new IllegalArgumentException("DE_TRINITY_PLAN_SNAPSHOT requires an absolute path");
        }
        String source = path == null ? SNAPSHOT_RESOURCE : path.toString();
        try (var reader = new InputStreamReader(path == null ? openResource(SNAPSHOT_RESOURCE) : Files.newInputStream(path), StandardCharsets.UTF_8)) {
            Data_Energistics.LOGGER.info("Reading Trinity TDS snapshot from {}", source);
            JsonObject document = JsonParser.parseReader(reader).getAsJsonObject();
            if (GsonHelper.getAsBigDecimal(document, "formatVersion").intValueExact() != 1) {
                throw new IllegalArgumentException("Expected a version 1 plan snapshot at " + source);
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
            Object2ObjectOpenHashMap<AEKey, BigInteger> stock = new Object2ObjectOpenHashMap<>();
            for (var stockEntry : GsonHelper.getAsJsonArray(document, "stock")) {
                var row = stockEntry.getAsJsonObject();
                var key = keys.get(readLegacyItem(row));
                BigInteger count = GsonHelper.getAsBigDecimal(row, "count").toBigIntegerExact();
                if (key != null && count.signum() > 0) {
                    stock.merge(key, count, BigInteger::add);
                }
            }
            return new Snapshot(request, new TrinityCraftingGraphSnapshot(1L, patterns), stock, readSeedOverrides(keys));
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read the Trinity plan snapshot at " + source, exception);
        }
    }

    private static Object2ObjectMap<AEKey, BigInteger> readSeedOverrides(Object2ObjectMap<LegacyItem, AEItemKey> keys) throws IOException {
        try (var reader = new InputStreamReader(openResource(SEED_STOCK_RESOURCE), StandardCharsets.UTF_8)) {
            Object2ObjectOpenHashMap<AEKey, BigInteger> amounts = new Object2ObjectOpenHashMap<>();
            for (var entry : JsonParser.parseReader(reader).getAsJsonArray()) {
                var row = entry.getAsJsonObject();
                var key = keys.get(readLegacyItem(row));
                BigInteger amount = GsonHelper.getAsBigDecimal(row, "count").toBigIntegerExact();
                if (key == null || amount.signum() <= 0 || amounts.putIfAbsent(key, amount) != null) {
                    throw new IllegalArgumentException("Invalid TDS startup reserve " + row);
                }
            }
            return amounts;
        }
    }

    private static InputStream openResource(String path) {
        var resource = TrinityPlanSnapshotGameTest.class.getResourceAsStream(path);
        if (resource == null) {
            throw new IllegalStateException("Missing Trinity GameTest fixture " + path);
        }
        return resource;
    }

    private static GenericStack readStack(JsonObject row, Object2ObjectMap<LegacyItem, AEItemKey> keys) {
        String id = GsonHelper.getAsString(row, "id");
        long amount = GsonHelper.getAsBigDecimal(row, "count").longValueExact();
        if (id.isBlank() || amount <= 0L) {
            throw new IllegalArgumentException("Snapshot stacks require an item id and positive count: " + id + " x " + amount);
        }
        return new GenericStack(keys.computeIfAbsent(readLegacyItem(row), LegacyItem::toKey), amount);
    }

    private static LegacyItem readLegacyItem(JsonObject row) {
        return new LegacyItem(GsonHelper.getAsString(row, "id"), GsonHelper.getAsBigDecimal(row, "meta").intValueExact(),
                GsonHelper.getAsString(row, "nbt", null));
    }

    private static AEItemKey snapshotKey(CompoundTag identity) {
        ItemStack stack = new ItemStack(Items.PAPER);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(identity));
        return AEItemKey.of(stack);
    }

    private record Snapshot(GenericStack request, TrinityCraftingGraphSnapshot graph,
                            Object2ObjectMap<AEKey, BigInteger> stock, Object2ObjectMap<AEKey, BigInteger> seedOverrides) {}

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
