package com.fish_dan_.data_energistics.common.combat.erasure.strike;

import net.minecraft.core.UUIDUtil;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSets;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Server-thread journal owned by one attack, including its subsequent payload work. UUID deduplication lasts only
 * as long as that attack: a respawned object cannot be hit twice by it, while later independent attacks remain valid.
 * Persistent owners mark their SavedData dirty before handing this mutable journal to a worker. Synchronous
 * weapon executions may use a transient journal, which is released once their strike returns.
 */
public final class ErasureStrike {

    private static final Codec<ErasureOutcome> OUTCOME_CODEC = Codec.STRING.comapFlatMap(
            ErasureStrike::decodeOutcome, ErasureOutcome::name);
    private static final Codec<Attempt> ATTEMPT_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("execution").forGetter(Attempt::executionId),
            OUTCOME_CODEC.fieldOf("outcome").forGetter(Attempt::outcome)).apply(instance, Attempt::new));
    private static final Codec<Object2ObjectMap<UUID, Attempt>> ATTEMPTS_CODEC = Codec.unboundedMap(UUIDUtil.STRING_CODEC, ATTEMPT_CODEC)
            .xmap(Object2ObjectOpenHashMap::new, map -> map);
    private static final Codec<ObjectList<UUID>> UUID_LIST_CODEC = Codec.list(UUIDUtil.CODEC)
            .xmap(ObjectArrayList::new, values -> values);
    public static final Codec<Persistence> PERSISTENCE_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("strike_id").forGetter(Persistence::strikeId),
            UUIDUtil.CODEC.optionalFieldOf("initiator").forGetter(Persistence::initiatorId),
            UUID_LIST_CODEC.fieldOf("exemptions").forGetter(Persistence::exemptions),
            ATTEMPTS_CODEC.fieldOf("attempts").forGetter(Persistence::attempts),
            UUID_LIST_CODEC.fieldOf("encounters").forGetter(Persistence::encounters),
            Codec.BOOL.fieldOf("bypasses_protection").forGetter(Persistence::bypassesProtection),
            Codec.BOOL.fieldOf("usable").forGetter(Persistence::usable))
            .apply(instance, Persistence::new));

    private final UUID strikeId;
    private final @Nullable UUID initiatorId;
    private final Set<UUID> exemptions;
    private final boolean bypassesProtection;
    private final Object2ObjectOpenHashMap<UUID, Attempt> attempts = new Object2ObjectOpenHashMap<>();
    private final ObjectOpenHashSet<UUID> encounters = new ObjectOpenHashSet<>();
    private boolean usable = true;

    public ErasureStrike(UUID strikeId, @Nullable UUID initiatorId, Set<UUID> exemptions) {
        this(strikeId, initiatorId, exemptions, false);
    }

    public ErasureStrike(UUID strikeId, @Nullable UUID initiatorId, Set<UUID> exemptions, boolean bypassesProtection) {
        this.strikeId = strikeId;
        this.initiatorId = initiatorId;
        this.exemptions = ObjectSets.unmodifiable(new ObjectOpenHashSet<>(exemptions));
        this.bypassesProtection = bypassesProtection;
    }

    /** Orbital strikes own the terminal life transition even while platform defenses are active. */
    public boolean bypassesProtection() {
        return bypassesProtection;
    }

    public UUID strikeId() {
        return this.strikeId;
    }

    public @Nullable UUID initiatorId() {
        return this.initiatorId;
    }

    public boolean exempts(UUID subject) {
        return this.exemptions.contains(subject);
    }

    /** Claims one subject before callbacks run, preventing both reentrancy and post-respawn repeat hits. */
    public @Nullable UUID begin(UUID subject) {
        if (!this.usable) {
            throw new IllegalStateException("An unreadable erasure journal cannot replay entity work");
        }
        if (this.attempts.containsKey(subject)) {
            return null;
        }
        UUID execution = UUID.randomUUID();
        this.attempts.put(subject, new Attempt(execution, ErasureOutcome.IN_PROGRESS));
        return execution;
    }

    public void complete(UUID subject, UUID execution, ErasureOutcome outcome) {
        if (!this.attempts.containsKey(subject)) {
            throw new IllegalStateException("Erasure completion has no claimed subject");
        }
        Attempt previous = this.attempts.get(subject);
        if (!previous.executionId().equals(execution) || previous.outcome() != ErasureOutcome.IN_PROGRESS || outcome == ErasureOutcome.IN_PROGRESS) {
            throw new IllegalStateException("Erasure result does not match the claimed execution");
        }
        this.attempts.put(subject, new Attempt(execution, outcome));
    }

    public Optional<Attempt> result(UUID subject) {
        return this.attempts.containsKey(subject) ? Optional.of(this.attempts.get(subject)) : Optional.empty();
    }

    public boolean usable() {
        return this.usable;
    }

    /** Uses the encounter's persistent identity, separately from the current guardian entity UUID. */
    public boolean claimEncounter(UUID encounterId) {
        if (!this.usable) {
            throw new IllegalStateException("An unreadable erasure journal cannot replay encounter work");
        }
        return this.encounters.add(encounterId);
    }

    /** Keeps a corrupt attack diagnosable and refundable without guessing which subjects it already terminated. */
    public static ErasureStrike quarantined(UUID strikeId, Set<UUID> exemptions) {
        ErasureStrike strike = new ErasureStrike(strikeId, null, exemptions);
        strike.usable = false;
        return strike;
    }

    public Persistence snapshot() {
        ObjectArrayList<UUID> persistedExemptions = new ObjectArrayList<>(this.exemptions);
        persistedExemptions.sort(UUID::compareTo);
        ObjectArrayList<UUID> persistedEncounters = new ObjectArrayList<>(this.encounters);
        persistedEncounters.sort(UUID::compareTo);
        return new Persistence(this.strikeId, Optional.ofNullable(this.initiatorId), persistedExemptions,
                new Object2ObjectOpenHashMap<>(this.attempts), persistedEncounters, this.bypassesProtection, this.usable);
    }

    public static ErasureStrike restore(Persistence state) {
        ObjectOpenHashSet<UUID> exemptions = new ObjectOpenHashSet<>(state.exemptions());
        if (exemptions.size() != state.exemptions().size()) throw new IllegalArgumentException("Duplicate erasure exemption identity");
        ErasureStrike strike = new ErasureStrike(state.strikeId(), state.initiatorId().orElse(null), exemptions, state.bypassesProtection());
        strike.attempts.putAll(state.attempts());
        for (UUID encounter : state.encounters()) {
            if (!strike.encounters.add(encounter)) throw new IllegalArgumentException("Duplicate erasure encounter identity");
        }
        strike.usable = state.usable();
        strike.attempts.replaceAll((subject, attempt) -> attempt.outcome() == ErasureOutcome.IN_PROGRESS ? new Attempt(attempt.executionId(), ErasureOutcome.PARTIAL_FAILURE) : attempt);
        return strike;
    }

    private static DataResult<ErasureOutcome> decodeOutcome(String value) {
        for (ErasureOutcome outcome : ErasureOutcome.values()) {
            if (outcome.name().equals(value)) return DataResult.success(outcome);
        }
        return DataResult.error(() -> "Unknown erasure outcome: " + value);
    }

    public record Attempt(UUID executionId, ErasureOutcome outcome) {}

    public record Persistence(UUID strikeId, Optional<UUID> initiatorId, ObjectList<UUID> exemptions,
                              Object2ObjectMap<UUID, Attempt> attempts, ObjectList<UUID> encounters, boolean bypassesProtection, boolean usable) {}
}
