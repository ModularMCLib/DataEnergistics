package com.fish_dan_.data_energistics.api.crafting.matching;

import it.unimi.dsi.fastutil.ints.IntArrayFIFOQueue;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import org.jspecify.annotations.NullMarked;

import java.util.function.BiPredicate;

/**
 * Capacity assignment for overlapping resource domains, independent of stack sizes.
 */
@NullMarked
public final class ResourceCapacityMatching {

    private ResourceCapacityMatching() {}

    /**
     * Assigns every actual quantity through bounded rule domains and available expected quantities.
     * Arrays/predicates are non-null and read-only, quantities nonnegative; invalid quantities throw.
     * Predicates authorize expected-to-rule and rule-to-actual edges without side effects. Each invocation
     * owns its graph; worker use requires thread-safe predicates. False means insufficient compatible capacity.
     * Long-capacity augmenting paths avoid iterating once per item, including with overlapping tag sets.
     */
    public static boolean accepts(long[] expected, long[] rules, long[] actual,
                                  BiPredicate<Integer, Integer> expectedRule,
                                  BiPredicate<Integer, Integer> ruleActual) {
        int ruleStart = 1 + expected.length;
        int actualStart = ruleStart + 2 * rules.length;
        int sink = actualStart + actual.length;
        var graph = new ObjectArrayList<ObjectList<Edge>>(sink + 1);
        for (int i = 0; i <= sink; i++) graph.add(new ObjectArrayList<>());
        for (int i = 0; i < expected.length; i++) {
            add(graph, 0, i + 1, expected[i]);
            for (int r = 0; r < rules.length; r++)
                if (expectedRule.test(i, r))
                    add(graph, i + 1, ruleStart + 2 * r, Math.min(expected[i], rules[r]));
        }
        for (int r = 0; r < rules.length; r++) {
            add(graph, ruleStart + 2 * r, ruleStart + 2 * r + 1, rules[r]);
            for (int a = 0; a < actual.length; a++)
                if (ruleActual.test(r, a))
                    add(graph, ruleStart + 2 * r + 1, actualStart + a, Math.min(rules[r], actual[a]));
        }
        for (int a = 0; a < actual.length; a++) add(graph, actualStart + a, sink, actual[a]);
        while (true) {
            var parent = new Edge[sink + 1];
            var visited = new boolean[sink + 1];
            var queue = new IntArrayFIFOQueue();
            queue.enqueue(0);
            visited[0] = true;
            while (!queue.isEmpty() && !visited[sink]) {
                int node = queue.dequeueInt();
                for (Edge edge : graph.get(node))
                    if (edge.capacity > 0 && !visited[edge.to]) {
                        visited[edge.to] = true;
                        parent[edge.to] = edge;
                        queue.enqueue(edge.to);
                    }
            }
            if (!visited[sink]) break;
            long amount = Long.MAX_VALUE;
            for (int node = sink; node != 0; node = parent[node].from) amount = Math.min(amount, parent[node].capacity);
            for (int node = sink; node != 0; node = parent[node].from) {
                Edge edge = parent[node];
                edge.capacity -= amount;
                graph.get(edge.to).get(edge.reverse).capacity += amount;
            }
        }
        for (int a = 0; a < actual.length; a++)
            for (Edge edge : graph.get(actualStart + a)) if (edge.to == sink && edge.capacity != 0) return false;
        return true;
    }

    private static void add(ObjectList<ObjectList<Edge>> graph, int from, int to, long capacity) {
        if (capacity < 0) throw new IllegalArgumentException("Negative resource capacity");
        var forward = new Edge(from, to, graph.get(to).size(), capacity);
        var backward = new Edge(to, from, graph.get(from).size(), 0);
        graph.get(from).add(forward);
        graph.get(to).add(backward);
    }

    private static final class Edge {

        final int from;
        final int to;
        final int reverse;
        long capacity;

        Edge(int from, int to, int reverse, long capacity) {
            this.from = from;
            this.to = to;
            this.reverse = reverse;
            this.capacity = capacity;
        }
    }
}
