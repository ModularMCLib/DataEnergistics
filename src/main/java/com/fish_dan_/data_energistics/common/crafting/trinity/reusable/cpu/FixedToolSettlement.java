package com.fish_dan_.data_energistics.common.crafting.trinity.reusable.cpu;

import com.fish_dan_.data_energistics.api.crafting.reusable.ReusableInputRule;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.rules.FixedToolIdentity;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectAVLTreeMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;

import java.math.BigInteger;

/** Checks returned real states against delivered units and completed uses, without synthesizing state-chain items. */
final class FixedToolSettlement {

    private final ReusableInputRule rule;
    private final Object2ObjectMap<AEItemKey, BigInteger> delivered = new Object2ObjectOpenHashMap<>();
    private final Int2ObjectOpenHashMap<BigInteger> operationsBySlot = new Int2ObjectOpenHashMap<>();
    private BigInteger consumedUses = BigInteger.ZERO;

    FixedToolSettlement(ReusableInputRule rule) {
        this.rule = rule;
    }

    void deliver(AEItemKey key, long amount) {
        if (!FixedToolIdentity.matches(rule, key)) throw new IllegalStateException("Delivered tool violates its durability contract");
        delivered.merge(key, BigInteger.valueOf(amount), BigInteger::add);
    }

    void completed(int slot, BigInteger held, long operations) {
        var count = BigInteger.valueOf(operations);
        operationsBySlot.merge(slot, count, BigInteger::add);
        consumedUses = consumedUses.add(held.multiply(count));
    }

    BigInteger verify(Object2ObjectMap<AEKey, BigInteger> remaining) {
        var returned = new Object2ObjectOpenHashMap<AEItemKey, BigInteger>();
        var iterator = remaining.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (entry.getKey() instanceof AEItemKey item && FixedToolIdentity.matches(rule, item)) {
                returned.put(item, entry.getValue());
                iterator.remove();
            }
        }
        boolean bounded = rule.kind() == ReusableInputRule.Kind.BOUNDED_DAMAGE;
        var initial = groups(delivered);
        var actual = groups(returned);
        BigInteger maximumOperations = BigInteger.ZERO;
        for (BigInteger count : operationsBySlot.values()) maximumOperations = maximumOperations.max(count);
        BigInteger exhausted = BigInteger.ZERO;
        BigInteger spent = BigInteger.ZERO;
        for (var entry : initial.int2ObjectEntrySet()) {
            var expected = entry.getValue();
            var observed = actual.computeIfAbsent(entry.getIntKey(), ignored -> new Long2ObjectAVLTreeMap<>());
            actual.remove(entry.getIntKey());
            BigInteger missing = count(expected).subtract(count(observed));
            if (missing.signum() < 0) throw new IllegalStateException("Executor returned additional tool units");
            if (missing.signum() > 0) observed.put(0L, missing);
            spent = spent.add(spentCapacity(expected, observed, maximumOperations, bounded));
            exhausted = exhausted.add(missing);
        }
        if (!actual.isEmpty()) throw new IllegalStateException("Executor changed undeclared tool damage coordinates");
        int wear = spent.compareTo(consumedUses);
        if (wear > 0 || !bounded && wear != 0) {
            throw new IllegalStateException("Returned tool durability does not match completed operations");
        }
        return exhausted;
    }

    private Int2ObjectOpenHashMap<Long2ObjectAVLTreeMap<BigInteger>> groups(Object2ObjectMap<AEItemKey, BigInteger> tools) {
        var result = new Int2ObjectOpenHashMap<Long2ObjectAVLTreeMap<BigInteger>>();
        boolean bounded = rule.kind() == ReusableInputRule.Kind.BOUNDED_DAMAGE;
        tools.forEach((key, count) -> result.computeIfAbsent(bounded ? 0 : key.getReadOnlyStack().getDamageValue() % rule.damagePerUse(),
                ignored -> new Long2ObjectAVLTreeMap<>()).merge(rule.guaranteedUses(key), count, BigInteger::add));
        return result;
    }

    private static BigInteger count(Long2ObjectAVLTreeMap<BigInteger> amounts) {
        BigInteger result = BigInteger.ZERO;
        for (BigInteger amount : amounts.values()) result = result.add(amount);
        return result;
    }

    private static BigInteger spentCapacity(Long2ObjectAVLTreeMap<BigInteger> before, Long2ObjectAVLTreeMap<BigInteger> after,
                                            BigInteger maximumOperations, boolean bounded) {
        var initial = buckets(before);
        var actual = buckets(after);
        BigInteger spent = BigInteger.ZERO;
        int left = 0, right = 0;
        BigInteger leftCount = initial.getFirst().count();
        BigInteger rightCount = actual.getFirst().count();
        while (left < initial.size() && right < actual.size()) {
            long delta = initial.get(left).uses() - actual.get(right).uses();
            if (!bounded && delta < 0 || BigInteger.valueOf(delta).compareTo(maximumOperations) > 0) {
                throw new IllegalStateException("Executor returned a tool outside its wear contract");
            }
            BigInteger matched = leftCount.min(rightCount);
            if (delta > 0) spent = spent.add(matched.multiply(BigInteger.valueOf(delta)));
            leftCount = leftCount.subtract(matched);
            rightCount = rightCount.subtract(matched);
            if (leftCount.signum() == 0 && ++left < initial.size()) leftCount = initial.get(left).count();
            if (rightCount.signum() == 0 && ++right < actual.size()) rightCount = actual.get(right).count();
        }
        return spent;
    }

    private static ObjectList<Bucket> buckets(Long2ObjectAVLTreeMap<BigInteger> source) {
        var result = new ObjectArrayList<Bucket>();
        source.long2ObjectEntrySet().forEach(entry -> result.add(new Bucket(entry.getLongKey(), entry.getValue())));
        return result;
    }

    private record Bucket(long uses, BigInteger count) {}
}
