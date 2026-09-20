package com.fish_dan_.data_energistics.common.crafting.trinity.execution.cpu;

import com.fish_dan_.data_energistics.api.crafting.matching.ItemMatchingRule;
import com.fish_dan_.data_energistics.common.crafting.dynamic.DynamicCraftingOutputResolutionException;
import com.fish_dan_.data_energistics.common.crafting.pattern.matching.EncodedPatternMatching;
import com.fish_dan_.data_energistics.common.crafting.trinity.serialization.TrinityBigIntegerEncoding;

import appeng.api.ids.AEComponents;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectSet;

import java.math.BigInteger;
import java.util.Optional;
import java.util.function.Function;

/**
 * Durable exact-template ledger for runtime outputs that may return a different item-component variant.
 */
final class DynamicCraftingOutputLedger {

    private static final String KEY_TAG = "planned_key";
    private static final String AMOUNT_TAG = "remaining";
    private static final String ROUTE_TAG = "route";
    private static final String SOURCE_TAG = "source";
    private static final String WAITING_TAG = "waiting";
    private static final String INPUT_ALIASES_TAG = "same_item_inputs";
    private static final String ACTUAL_KEY_TAG = "actual_key";

    private final ObjectArrayList<MutableEntry> entries = new ObjectArrayList<>();
    private final Object2ObjectLinkedOpenHashMap<AEItemKey, BigInteger> inputAliases = new Object2ObjectLinkedOpenHashMap<>();

    /**
     * Output ownership route selected at provider-commit time.
     */
    enum Route {
        INVENTORY,
        FINAL_OUTPUT
    }

    /**
     * One amount registered by a successful provider submission.
     *
     * @param plannedKey exact key whose waiting counter remains authoritative
     * @param amount     positive accepted amount
     * @param route      destination for the actual runtime key
     * @param source     adapter ID or request-local manual source
     */
    record Registration(AEItemKey plannedKey,
                        BigInteger amount,
                        Route route,
                        ResourceLocation source,
                        ItemMatchingRule rule,
                        AEItemKey templateKey) {

        Registration(AEItemKey plannedKey, BigInteger amount, Route route, ResourceLocation source, ItemMatchingRule rule) {
            this(plannedKey, amount, route, source, rule, plannedKey);
        }

        Registration {
            if (amount.signum() <= 0) {
                throw new IllegalArgumentException("A dynamic output registration must be positive");
            }
        }
    }

    /**
     * Immutable acceptance selected without mutating the ledger.
     *
     * @param plannedKey exact waiting key to deduct
     * @param amount     maximum accepted actual amount
     * @param route      actual-key destination
     * @param source     persisted semantic source
     */
    record Match(AEItemKey plannedKey,
                 long amount,
                 Route route,
                 ResourceLocation source,
                 ItemMatchingRule rule,
                 AEItemKey templateKey) {}

    /**
     * Whether a new push can coexist with all active same-item matching domains.
     */
    enum DispatchSafety {
        SAFE,
        CONFLICT
    }

    boolean isEmpty() {
        return this.entries.isEmpty();
    }

    /**
     * Rejects intrinsically ambiguous declarations and defers transient conflicts with already in-flight outputs.
     */
    DispatchSafety evaluate(Object2ObjectMap<AEKey, BigInteger> waitingFor,
                            ObjectList<GenericStack> expectedPhysicalOutputs,
                            ObjectList<Registration> registrations) {
        for (Registration incoming : registrations) {
            BigInteger expected = expectedPhysicalOutputs.stream().filter(stack -> stack.what().equals(incoming.plannedKey()))
                    .map(stack -> BigInteger.valueOf(stack.amount())).reduce(BigInteger.ZERO, BigInteger::add);
            BigInteger declared = registrations.stream().filter(value -> value.plannedKey().equals(incoming.plannedKey()))
                    .map(Registration::amount).reduce(BigInteger.ZERO, BigInteger::add);
            if (declared.compareTo(expected) > 0) throw new DynamicCraftingOutputResolutionException("Dynamic declaration exceeds physical output");
            for (Registration other : registrations) {
                if (incoming.rule().overlaps(incoming.templateKey(), other.rule(), other.templateKey()) &&
                        (!incoming.plannedKey().equals(other.plannedKey()) || incoming.route() != other.route())) {
                    throw new DynamicCraftingOutputResolutionException("One dispatch has overlapping output rules with different accounting routes");
                }
            }
            for (var active : entries) {
                if (incoming.rule().overlaps(incoming.templateKey(), active.rule, active.templateKey) &&
                        (!incoming.plannedKey().equals(active.plannedKey) || incoming.route() != active.route))
                    return DispatchSafety.CONFLICT;
            }
            for (var waiting : waitingFor.entrySet()) {
                if (waiting.getValue().signum() > 0 && waiting.getKey() instanceof AEItemKey key &&
                        !key.equals(incoming.plannedKey()) && incoming.rule().matches(incoming.templateKey(), key))
                    return DispatchSafety.CONFLICT;
            }
        }
        for (var active : entries) for (var output : expectedPhysicalOutputs) {
            if (output.what() instanceof AEItemKey key && !key.equals(active.plannedKey) && active.rule.matches(active.templateKey, key)) {
                return DispatchSafety.CONFLICT;
            }
        }
        return DispatchSafety.SAFE;
    }

    void register(ObjectList<Registration> registrations) {
        for (Registration registration : registrations) {
            MutableEntry existing = this.entries.stream()
                    .filter(entry -> entry.matches(registration))
                    .findFirst()
                    .orElse(null);
            if (existing == null) {
                this.entries.add(new MutableEntry(registration));
            } else {
                existing.remaining = existing.remaining.add(registration.amount());
            }
        }
        this.entries.sort((left, right) -> Integer.compare(left.rule.mode().ordinal(), right.rule.mode().ordinal()));
    }

    /**
     * Withdraws only uncompleted registrations identified by their frozen key, route and source.
     * Duplicate requests are summed before checking remaining amounts. Every lookup and amount check
     * completes before returning, so an invalid cancellation cannot partially consume another registration.
     * This does not remove actual input aliases or adjust the CPU's separate exact waiting counter.
     * The returned one-shot action must run in the same server callback without intervening ledger mutations.
     *
     * @param cancelledRegistrations positive cancelled amounts, not the original accepted totals
     * @throws IllegalStateException when an exact registration is absent or has insufficient remaining amount
     */
    Runnable prepareWithdrawal(ObjectList<Registration> cancelledRegistrations) {
        Object2ObjectLinkedOpenHashMap<MutableEntry, BigInteger> withdrawals = new Object2ObjectLinkedOpenHashMap<>();
        for (Registration registration : cancelledRegistrations) {
            MutableEntry existing = this.entries.stream()
                    .filter(entry -> entry.matches(registration))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Cancelled dynamic output registration is absent: " + registration));
            withdrawals.merge(existing, registration.amount(), BigInteger::add);
        }
        for (var withdrawal : withdrawals.object2ObjectEntrySet()) {
            if (withdrawal.getValue().compareTo(withdrawal.getKey().remaining) > 0) {
                throw new IllegalStateException("Cancelled dynamic output exceeds its uncompleted registration: " +
                        withdrawal.getKey().registration());
            }
            withdrawal.setValue(withdrawal.getKey().remaining.subtract(withdrawal.getValue()));
        }
        return new Runnable() {

            private boolean applied;

            @Override
            public void run() {
                if (applied) {
                    throw new IllegalStateException("A prepared dynamic withdrawal may only be applied once");
                }
                applied = true;
                withdrawals.object2ObjectEntrySet().forEach(entry -> entry.getKey().remaining = entry.getValue());
                removeEmpty();
            }
        };
    }

    /**
     * Finds a same-item entry after the exact waiting path has rejected the remaining actual stack.
     */
    Optional<Match> match(AEItemKey actualKey, long maximumAmount, Object2ObjectMap<AEKey, BigInteger> waitingFor) {
        if (maximumAmount <= 0L) {
            return Optional.empty();
        }
        for (MutableEntry entry : this.entries) {
            if (!entry.rule.matches(entry.templateKey, actualKey)) {
                continue;
            }
            BigInteger exactWaiting = waitingFor.getOrDefault(entry.plannedKey, BigInteger.ZERO);
            long amount = entry.remaining.min(exactWaiting).min(BigInteger.valueOf(maximumAmount)).longValueExact();
            if (amount > 0L) {
                return Optional.of(entry.match(amount));
            }
        }
        return Optional.empty();
    }

    /**
     * Deducts exact output receipts from a compatible dynamic allowance first, releasing its item domain promptly.
     */
    long exactAllowance(AEKey key, long requested, BigInteger waiting) {
        BigInteger forbidden = BigInteger.ZERO;
        for (var entry : entries) if (entry.plannedKey.equals(key) && !entry.rule.matches(entry.templateKey, key)) {
            forbidden = forbidden.add(entry.remaining);
        }
        return waiting.subtract(forbidden).max(BigInteger.ZERO).min(BigInteger.valueOf(requested)).longValueExact();
    }

    void consumeExact(AEKey exactKey, long amount, BigInteger stillWaiting) {
        var flexible = entries.stream().filter(entry -> entry.plannedKey.equals(exactKey))
                .map(entry -> entry.remaining).reduce(BigInteger.ZERO, BigInteger::add);
        long remaining = flexible.subtract(stillWaiting).max(BigInteger.ZERO).min(BigInteger.valueOf(amount)).longValueExact();
        for (MutableEntry entry : this.entries) {
            if (!entry.plannedKey.equals(exactKey) || !entry.rule.matches(entry.templateKey, exactKey) || remaining == 0L) {
                continue;
            }
            long consumed = entry.remaining.min(BigInteger.valueOf(remaining)).longValueExact();
            entry.remaining = entry.remaining.subtract(BigInteger.valueOf(consumed));
            remaining -= consumed;
        }
        removeEmpty();
    }

    void consume(Match match, long amount) {
        if (amount <= 0L || amount > match.amount()) {
            throw new IllegalArgumentException("A dynamic output receipt must consume a bounded positive amount");
        }
        for (MutableEntry entry : this.entries) {
            if (entry.plannedKey.equals(match.plannedKey()) &&
                    entry.route == match.route() &&
                    entry.source.equals(match.source()) && entry.rule.equals(match.rule()) && entry.templateKey.equals(match.templateKey())) {
                if (BigInteger.valueOf(amount).compareTo(entry.remaining) > 0) {
                    throw new IllegalStateException("Dynamic output ledger changed after acceptance simulation");
                }
                entry.remaining = entry.remaining.subtract(BigInteger.valueOf(amount));
                removeEmpty();
                return;
            }
        }
        throw new IllegalStateException("Dynamic output ledger lost its simulated acceptance entry");
    }

    /**
     * Marks an ordinary actual dynamic output as eligible for same-item input binding within this job only.
     */
    void recordInputAlias(AEItemKey actualKey, long amount) {
        if (amount <= 0L) {
            throw new IllegalArgumentException("A dynamic input alias must be a positive item amount");
        }
        this.inputAliases.merge(actualKey, BigInteger.valueOf(amount), BigInteger::add);
    }

    /**
     * Returns the owned same-item alternatives without requiring one variant to satisfy the whole input.
     */
    ObjectList<GenericStack> resolveInputs(AEItemKey definition, AEKey plannedKey, KeyCounter inventory) {
        if (!(plannedKey instanceof AEItemKey plannedItem)) {
            return ObjectList.of();
        }
        ObjectArrayList<GenericStack> alternatives = new ObjectArrayList<>();
        for (var alias : this.inputAliases.object2ObjectEntrySet()) {
            if (matchesAnyInput(definition, plannedItem, alias.getKey()) &&
                    !alias.getKey().equals(plannedKey)) {
                long available = alias.getValue().min(BigInteger.valueOf(inventory.get(alias.getKey()))).longValueExact();
                if (available > 0L) {
                    alternatives.add(new GenericStack(alias.getKey(), available));
                }
            }
        }
        return new ObjectImmutableList<>(alternatives);
    }

    private static boolean matchesAnyInput(AEItemKey definition, AEItemKey planned, AEItemKey actual) {
        var encoded = definition.get(AEComponents.ENCODED_PROCESSING_PATTERN);
        if (encoded == null) return planned.equals(actual);
        int dense = 0;
        for (var input : encoded.sparseInputs()) {
            if (input == null) continue;
            if (input.what().equals(planned) && EncodedPatternMatching.matchesInput(definition, dense, planned, actual)) return true;
            dense++;
        }
        return false;
    }

    boolean isInputAlias(AEKey key) {
        return key instanceof AEItemKey itemKey && this.inputAliases.containsKey(itemKey);
    }

    /**
     * Deducts only aliases that the accepted provider submission actually consumed.
     */
    void consumeInputAliases(KeyCounter consumedInputs) {
        for (var consumed : consumedInputs) {
            consumeInputAlias(consumed.getKey(), BigInteger.valueOf(consumed.getLongValue()));
        }
    }

    void consumeInputAlias(AEKey key, BigInteger consumed) {
        if (!(key instanceof AEItemKey itemKey)) {
            return;
        }
        BigInteger aliased = this.inputAliases.getOrDefault(itemKey, BigInteger.ZERO);
        BigInteger remaining = aliased.subtract(aliased.min(consumed));
        if (remaining.signum() == 0) {
            this.inputAliases.remove(itemKey);
        } else {
            this.inputAliases.put(itemKey, remaining);
        }
    }

    void validateInputAliases(Function<AEKey, BigInteger> inventory) {
        this.inputAliases.forEach((key, amount) -> {
            if (amount.signum() <= 0 || inventory.apply(key).compareTo(amount) < 0) {
                throw new IllegalArgumentException(
                        "Persisted same-item input ownership exceeds CPU inventory for " + key);
            }
        });
    }

    CompoundTag writeToTag(HolderLookup.Provider registries) {
        CompoundTag root = new CompoundTag();
        ListTag encoded = new ListTag();
        for (MutableEntry entry : this.entries) {
            CompoundTag tag = new CompoundTag();
            tag.put(KEY_TAG, entry.plannedKey.toTagGeneric(registries));
            tag.putByteArray(AMOUNT_TAG, TrinityBigIntegerEncoding.encode(entry.remaining, "dynamic output allowance"));
            tag.putString(ROUTE_TAG, entry.route.name());
            tag.putString(SOURCE_TAG, entry.source.toString());
            tag.put("rule", entry.rule.save());
            tag.put("template", entry.templateKey.toTagGeneric(registries));
            encoded.add(tag);
        }
        root.put(WAITING_TAG, encoded);
        ListTag aliases = new ListTag();
        this.inputAliases.forEach((key, amount) -> {
            CompoundTag tag = new CompoundTag();
            tag.put(ACTUAL_KEY_TAG, key.toTagGeneric(registries));
            tag.putByteArray(AMOUNT_TAG, TrinityBigIntegerEncoding.encode(amount, "dynamic input alias"));
            aliases.add(tag);
        });
        root.put(INPUT_ALIASES_TAG, aliases);
        return root;
    }

    static DynamicCraftingOutputLedger readFromTag(CompoundTag root,
                                                   HolderLookup.Provider registries) {
        if (!root.getAllKeys().equals(ObjectSet.of(WAITING_TAG, INPUT_ALIASES_TAG)) ||
                !root.contains(WAITING_TAG, Tag.TAG_LIST) ||
                !root.contains(INPUT_ALIASES_TAG, Tag.TAG_LIST)) {
            throw new IllegalArgumentException("Damaged dynamic crafting output ledger root");
        }
        DynamicCraftingOutputLedger ledger = new DynamicCraftingOutputLedger();
        ObjectArrayList<Registration> registrations = new ObjectArrayList<>();
        Tag rawWaiting = root.get(WAITING_TAG);
        if (!(rawWaiting instanceof ListTag encoded) ||
                (!encoded.isEmpty() && encoded.getElementType() != Tag.TAG_COMPOUND)) {
            throw new IllegalArgumentException("Damaged dynamic crafting output waiting list");
        }
        for (Tag value : encoded) {
            boolean legacy = value instanceof CompoundTag entry && entry.getAllKeys().equals(ObjectSet.of(KEY_TAG, AMOUNT_TAG, ROUTE_TAG, SOURCE_TAG));
            if (!(value instanceof CompoundTag tag) ||
                    !legacy && !tag.getAllKeys().equals(ObjectSet.of(KEY_TAG, AMOUNT_TAG, ROUTE_TAG, SOURCE_TAG, "rule", "template")) ||
                    !tag.contains(KEY_TAG, Tag.TAG_COMPOUND) ||
                    !tag.contains(ROUTE_TAG, Tag.TAG_STRING) ||
                    !tag.contains(SOURCE_TAG, Tag.TAG_STRING)) {
                throw new IllegalArgumentException("Damaged dynamic crafting output ledger entry");
            }
            AEKey decoded = AEKey.fromTagGeneric(registries, tag.getCompound(KEY_TAG));
            if (!(decoded instanceof AEItemKey itemKey)) {
                throw new IllegalArgumentException("Dynamic crafting output ledger requires item keys");
            }
            Route route;
            ResourceLocation source;
            try {
                route = Route.valueOf(tag.getString(ROUTE_TAG));
                source = ResourceLocation.parse(tag.getString(SOURCE_TAG));
            } catch (RuntimeException exception) {
                throw new IllegalArgumentException("Damaged dynamic crafting output ledger metadata", exception);
            }
            var template = legacy ? itemKey : AEKey.fromTagGeneric(registries, tag.getCompound("template"));
            if (!(template instanceof AEItemKey templateKey)) throw new IllegalArgumentException("Invalid dynamic output template");
            registrations.add(new Registration(itemKey, readAmount(tag), route, source, legacy ? ItemMatchingRule.ID : ItemMatchingRule.load(tag.getCompound("rule")), templateKey));
        }
        for (var first : registrations) for (var second : registrations) {
            if (first.rule().overlaps(first.templateKey(), second.rule(), second.templateKey()) &&
                    (!first.plannedKey().equals(second.plannedKey()) || first.route() != second.route())) {
                throw new IllegalArgumentException("Persisted dynamic output rules have ambiguous routes");
            }
        }
        ledger.register(registrations);
        if (ledger.entries.size() != registrations.size()) {
            throw new IllegalArgumentException("Persisted dynamic crafting output ledger contains duplicate entries");
        }
        Tag rawAliases = root.get(INPUT_ALIASES_TAG);
        if (!(rawAliases instanceof ListTag aliases) ||
                (!aliases.isEmpty() && aliases.getElementType() != Tag.TAG_COMPOUND)) {
            throw new IllegalArgumentException("Damaged same-item input alias list");
        }
        for (Tag value : aliases) {
            if (!(value instanceof CompoundTag tag) ||
                    !tag.getAllKeys().equals(ObjectSet.of(ACTUAL_KEY_TAG, AMOUNT_TAG)) ||
                    !tag.contains(ACTUAL_KEY_TAG, Tag.TAG_COMPOUND)) {
                throw new IllegalArgumentException("Damaged same-item input alias entry");
            }
            AEKey decoded = AEKey.fromTagGeneric(registries, tag.getCompound(ACTUAL_KEY_TAG));
            BigInteger amount = readAmount(tag);
            if (!(decoded instanceof AEItemKey itemKey) || amount.signum() <= 0 ||
                    ledger.inputAliases.putIfAbsent(itemKey, amount) != null) {
                throw new IllegalArgumentException("Same-item input aliases require unique positive item entries");
            }
        }
        return ledger;
    }

    private void removeEmpty() {
        this.entries.removeIf(entry -> entry.remaining.signum() == 0);
    }

    private static BigInteger readAmount(CompoundTag tag) {
        return TrinityBigIntegerEncoding.readTag(tag, AMOUNT_TAG, "dynamic output ledger amount");
    }

    private static final class MutableEntry {

        private final AEItemKey plannedKey;
        private final Route route;
        private final ResourceLocation source;
        private final ItemMatchingRule rule;
        private final AEItemKey templateKey;
        private BigInteger remaining;

        private MutableEntry(Registration registration) {
            this.plannedKey = registration.plannedKey();
            this.remaining = registration.amount();
            this.route = registration.route();
            this.source = registration.source();
            this.rule = registration.rule();
            this.templateKey = registration.templateKey();
        }

        private Registration registration() {
            return new Registration(this.plannedKey, this.remaining, this.route, this.source, this.rule, this.templateKey);
        }

        private boolean matches(Registration registration) {
            return this.plannedKey.equals(registration.plannedKey()) &&
                    this.route == registration.route() &&
                    this.source.equals(registration.source()) && this.rule.equals(registration.rule()) && this.templateKey.equals(registration.templateKey());
        }

        private Match match(long amount) {
            return new Match(this.plannedKey, amount, this.route, this.source, this.rule, this.templateKey);
        }
    }
}
