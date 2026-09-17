package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.topology;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityCraftingGraphPattern;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityCraftingGraphSnapshot;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternIdentity;
import com.fish_dan_.data_energistics.common.trinity.pattern.TrinityPatternPublicationSignature;
import com.fish_dan_.data_energistics.common.trinity.pattern.TrinityPatternPublicationSignature.Alternative;
import com.fish_dan_.data_energistics.common.trinity.pattern.TrinityPatternPublicationSignature.Input;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;

@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class TrinityGraphTopologyAnalyzerGameTest {

    private TrinityGraphTopologyAnalyzerGameTest() {}

    @TestHolder("trinity_condensation_sorts_successors_independently_of_recipe_order")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void sortsSuccessorsIndependentlyOfRecipeOrder(GameTestHelper helper) {
        AEItemKey a = AEItemKey.of(Items.COAL);
        AEItemKey b = AEItemKey.of(Items.IRON_INGOT);
        AEItemKey c = AEItemKey.of(Items.GOLD_INGOT);
        // Node order is A, B, C, but C encounters successor B before A. Repeated C -> B must stay unique.
        TrinityCraftingTopology topology = analyze(helper, ObjectList.of(
                new Edge(a, b), new Edge(c, b), new Edge(c, a), new Edge(c, b)));

        helper.assertValueEqual(topology.components().size(), 3, "Acyclic keys remain separate components");
        helper.assertValueEqual(topology.componentByKey().getInt(c), 2, "Fixture retains C as the last component");
        helper.assertValueEqual(topology.components().get(2).successorIndexes(), IntList.of(0, 1),
                "Successors are numerically sorted and deduplicated, regardless of recipe discovery order");
        helper.assertValueEqual(topology.topologicalOrder(), IntList.of(2, 0, 1),
                "Sorting adjacency does not replace the actual dependency order");
        helper.succeed();
    }

    @TestHolder("trinity_condensation_sorts_predecessors_after_merging_interleaved_cycle_nodes")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void sortsPredecessorsAfterMergingInterleavedCycleNodes(GameTestHelper helper) {
        AEItemKey a = AEItemKey.of(Items.COAL);
        AEItemKey b = AEItemKey.of(Items.IRON_INGOT);
        AEItemKey c = AEItemKey.of(Items.GOLD_INGOT);
        AEItemKey d = AEItemKey.of(Items.DIAMOND);
        // A and D form component 0. Iterating nodes A, B, C, D discovers C's predecessors as 1 then 0.
        TrinityCraftingTopology topology = analyze(helper, ObjectList.of(
                new Edge(a, b), new Edge(b, c), new Edge(a, d), new Edge(d, a),
                new Edge(d, c), new Edge(d, c)));

        helper.assertValueEqual(topology.components().size(), 3, "Only the A-D cycle is condensed");
        helper.assertValueEqual(topology.components().getFirst().keys(), ObjectList.of(a, d),
                "Interleaved cycle members retain their original key order");
        helper.assertTrue(topology.components().getFirst().cyclic(), "The internal feedback remains cyclic");
        helper.assertValueEqual(topology.components().get(2).predecessorIndexes(), IntList.of(0, 1),
                "Predecessors are sorted after component mapping and repeated edges stay unique");
        helper.assertValueEqual(topology.components().getFirst().successorIndexes(), IntList.of(1, 2),
                "Internal cycle edges do not become self-references in the condensation graph");
        helper.assertValueEqual(topology.topologicalOrder(), IntList.of(0, 1, 2),
                "The condensed cycle still precedes its dependent components");
        helper.succeed();
    }

    private static TrinityCraftingTopology analyze(GameTestHelper helper, ObjectList<Edge> edges) {
        ObjectList<TrinityCraftingGraphPattern> patterns = new ObjectArrayList<>();
        for (int index = 0; index < edges.size(); index++) {
            Edge edge = edges.get(index);
            TrinityPatternPublicationSignature publication = new TrinityPatternPublicationSignature(
                    AEItemKey.of(Items.CRAFTING_TABLE),
                    ObjectList.of(new Input(1L, ObjectList.of(new Alternative(new GenericStack(edge.input(), 1L), null)))),
                    ObjectList.of(new GenericStack(edge.output(), 1L)), false);
            patterns.add(new TrinityCraftingGraphPattern(new TrinityPatternIdentity("edge_" + index, "topology"), publication));
        }
        TrinityCraftingGraphSnapshot graph = new TrinityCraftingGraphSnapshot(1L, patterns);
        var expansion = TrinityPatternVariantExpander.create().expand(graph, 32);
        helper.assertTrue(expansion.successful(), "The complete fixture must expand into valid recipe transitions");
        var result = TrinityGraphTopologyAnalyzer.create().analyze(graph, expansion.value(), 32);
        helper.assertTrue(result.successful(), "Valid condensation edges must not reject topology construction");
        return result.value();
    }

    private record Edge(AEItemKey input, AEItemKey output) {}
}
