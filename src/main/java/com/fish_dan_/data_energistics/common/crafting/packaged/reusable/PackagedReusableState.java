package com.fish_dan_.data_energistics.common.crafting.packaged.reusable;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.api.crafting.dispatch.CountedCraftingAdmission;
import com.fish_dan_.data_energistics.api.crafting.dispatch.CountedCraftingTarget;
import com.fish_dan_.data_energistics.api.crafting.packaged.PackagedMachineAdapter;
import com.fish_dan_.data_energistics.api.crafting.reusable.ReusableInputRule;
import com.fish_dan_.data_energistics.api.crafting.reusable.dispatch.ReusableCraftingAdmission;
import com.fish_dan_.data_energistics.api.crafting.reusable.dispatch.ReusableCraftingCustodyCensus;
import com.fish_dan_.data_energistics.api.crafting.reusable.dispatch.ReusableCraftingProviderAdapter;
import com.fish_dan_.data_energistics.api.crafting.reusable.dispatch.ReusableCraftingRequest;
import com.fish_dan_.data_energistics.api.crafting.reusable.dispatch.ReusableCraftingRequest.Target;
import com.fish_dan_.data_energistics.api.crafting.reusable.dispatch.ReusableCraftingSessionView;
import com.fish_dan_.data_energistics.api.crafting.reusable.dispatch.ReusableCraftingSessionView.AppendReceipt;
import com.fish_dan_.data_energistics.api.registry.connector.ConnectorLink;
import com.fish_dan_.data_energistics.common.crafting.packaged.execution.PackagedOperationState;
import com.fish_dan_.data_energistics.common.crafting.packaged.recipe.PackagedOutputMatching;
import com.fish_dan_.data_energistics.common.crafting.pattern.EncodedPatternRecipeReference;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternIdentity;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.custody.ReusableCustodyAggregation;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.endpoint.PersistentReusableCraftingEndpoint;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.endpoint.PersistentReusableCraftingEndpoint.Binding;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.endpoint.PersistentReusableCraftingEndpoint.Host;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.endpoint.PersistentReusableCraftingEndpoint.NativeResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.endpoint.ReusableCraftingEndpointNbtCodec;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.session.ReusableInputSession.Identity;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.session.ReusableInputSession.Operation;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.session.ReusableInputSession.ToolOutcome;
import com.fish_dan_.data_energistics.common.entrypoint.DataEnergisticsEntrypointLoader;
import com.fish_dan_.data_energistics.common.trinity.pattern.TrinityPatternPublicationSignature;
import com.fish_dan_.data_energistics.world.packaged.PackagedMachineClaims;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import org.jspecify.annotations.Nullable;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Persistent custody for asynchronous native machines. Live query adapters are never saved or retained. */
public final class PackagedReusableState {

    public static final ResourceLocation MODE = Data_Energistics.id("packaged_reusable");
    private final UUID providerId;
    private final Object2ObjectLinkedOpenHashMap<String, Entry> entries = new Object2ObjectLinkedOpenHashMap<>();
    private final Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> outputs = new Object2ObjectLinkedOpenHashMap<>();
    private final ReusableCustodyAggregation custody = new ReusableCustodyAggregation();
    private boolean frozen;

    public PackagedReusableState() {
        this(UUID.randomUUID());
    }

    private PackagedReusableState(UUID providerId) {
        this.providerId = providerId;
    }

    public boolean hasWork() {
        return !frozen && (!outputs.isEmpty() || entries.values().stream().anyMatch(entry -> entry.endpoint.hasResidentSession()));
    }

    public int pendingOperations() {
        int count = 0;
        for (Entry entry : entries.values()) if (entry.endpoint.hasResidentSession()) count++;
        return count;
    }

    /** Includes settled custody history, which must survive dismantling and later CPU receipt reconciliation. */
    public boolean hasState() {
        return !entries.isEmpty() || !outputs.isEmpty();
    }

    public void freeze() {
        frozen = true;
    }

    public ReusableCraftingProviderAdapter adapter(ServerLevel level, ObjectList<ConnectorLink> links,
                                                   Predicate<IPatternDetails> available, Runnable changed, Consumer<IPatternDetails> success) {
        return new Access(level, links, available, changed, success);
    }

    public boolean tick(ServerLevel level, MEStorage returns, IActionSource source) {
        if (frozen) return false;
        boolean[] dirty = { false };
        var access = new Access(level, ObjectList.of(), pattern -> true, () -> dirty[0] = true, pattern -> dirty[0] = true);
        for (Entry entry : entries.values()) {
            entry.endpoint.tick(level.getGameTime(), 1, access.host(entry));
        }
        var iterator = outputs.object2ObjectEntrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            long offered = entry.getValue().min(BigInteger.valueOf(Long.MAX_VALUE)).longValueExact();
            long accepted = returns.insert(entry.getKey(), offered, Actionable.MODULATE, source);
            if (accepted < 0 || accepted > offered) throw new IllegalStateException("Invalid packaged reusable return insertion");
            if (accepted == 0) continue;
            var remaining = entry.getValue().subtract(BigInteger.valueOf(accepted));
            if (remaining.signum() == 0) iterator.remove();
            else entry.setValue(remaining);
            dirty[0] = true;
        }
        return dirty[0];
    }

    private String target(BlockPos position) {
        return "packaged:" + providerId + "/machine:" + position.asLong();
    }

    private @Nullable Entry locate(UUID session) {
        if (frozen) return null;
        for (Entry entry : entries.values()) if (entry.endpoint.query(session).isPresent()) return entry;
        return null;
    }

    private @Nullable Entry findTarget(String identity) {
        return entries.getOrDefault(identity, null);
    }

    private final class Access implements ReusableCraftingProviderAdapter {

        private final ServerLevel level;
        private final ObjectList<ConnectorLink> links;
        private final Predicate<IPatternDetails> available;
        private final Runnable changed;
        private final Consumer<IPatternDetails> success;

        private Access(ServerLevel level, ObjectList<ConnectorLink> links, Predicate<IPatternDetails> available, Runnable changed, Consumer<IPatternDetails> success) {
            this.level = level;
            this.links = links;
            this.available = available;
            this.changed = changed;
            this.success = success;
        }

        @Override
        public @Nullable CountedCraftingAdmission prepareBatch(IPatternDetails pattern, KeyCounter[] prototype, long count) {
            return null; // This view only extends reusable dispatch; ordinary dispatch remains with its provider.
        }

        @Override
        public ObjectList<Target> reusableTargetsFast(IPatternDetails pattern, IActionSource source, ServerLevel queryLevel) {
            if (frozen || queryLevel != level || !available.test(pattern)) return ObjectList.of();
            var reference = EncodedPatternRecipeReference.get(pattern.getDefinition().getReadOnlyStack());
            if (reference == null) return ObjectList.of();
            var result = new ObjectArrayList<Target>();
            for (ConnectorLink link : links) {
                if (!link.mode().supportsInput() || !level.isLoaded(link.position())) continue;
                for (var machine : DataEnergisticsEntrypointLoader.snapshot().packagedCrafting().forType(reference.recipeTypeId())) {
                    if (!machine.supportsReusableInputs() || !machine.recognizes(level, link.position())) continue;
                    String identity = target(link.position());
                    var candidate = new Target(identity, CountedCraftingTarget.route(identity), Optional.of(MODE));
                    if (!result.contains(candidate)) result.add(candidate);
                }
            }
            return ObjectLists.unmodifiable(result);
        }

        @Override
        public @Nullable ReusableCraftingAdmission prepareReusable(ReusableCraftingRequest request) {
            if (frozen || request.level() != level || !request.target().mode().equals(Optional.of(MODE)) ||
                    !available.test(request.pattern()))
                return null;
            for (var input : request.inputsFast()) {
                if (input.tool().isPresent() && input.tool().orElseThrow().rule().kind() != ReusableInputRule.Kind.UNCHANGED) return null;
            }
            String identity = request.target().persistentIdentity();
            if (links.stream().noneMatch(link -> link.mode().supportsInput() && target(link.position()).equals(identity))) return null;
            Entry existing = findTarget(identity);
            Entry candidate = existing;
            if (candidate == null) {
                var reference = EncodedPatternRecipeReference.get(request.pattern().getDefinition().getReadOnlyStack());
                if (reference == null) return null;
                for (ConnectorLink link : links) {
                    if (!link.mode().supportsInput() || !target(link.position()).equals(identity)) continue;
                    for (var machine : DataEnergisticsEntrypointLoader.snapshot().packagedCrafting().forType(reference.recipeTypeId())) {
                        if (machine.supportsReusableInputs() && machine.recognizes(level, link.position())) {
                            candidate = new Entry(link.position(), link.side(), machine.id(), new PersistentReusableCraftingEndpoint(identity));
                            break;
                        }
                    }
                    if (candidate != null) break;
                }
            }
            if (candidate == null) return null;
            Entry selected = candidate;
            var prepared = selected.endpoint.prepare(request, level.getGameTime(), host(selected));
            if (prepared == null) return null;
            return new ReusableCraftingAdmission() {

                @Override
                public long count() {
                    return prepared.count();
                }

                @Override
                public ObjectList<ReusableCraftingRequest.SlotStack> physicalInputsFast() {
                    return prepared.physicalInputsFast();
                }

                @Override
                public boolean replay() {
                    return prepared.replay();
                }

                @Override
                public boolean hasTransferredInputOwnership() {
                    return prepared.hasTransferredInputOwnership();
                }

                @Override
                public boolean commit(KeyCounter[] delivery) {
                    if (frozen || entries.get(identity) != existing) return false;
                    try {
                        boolean accepted = prepared.commit(delivery);
                        if (accepted && !prepared.replay()) success.accept(request.pattern());
                        return accepted;
                    } finally {
                        if (prepared.hasTransferredInputOwnership()) {
                            entries.put(identity, selected);
                            changed.run();
                        }
                    }
                }
            };
        }

        @Override
        public Optional<ReusableCraftingSessionView> reusableSession(UUID sessionId) {
            Entry entry = locate(sessionId);
            return entry == null ? Optional.empty() : entry.endpoint.query(sessionId);
        }

        @Override
        public ReusableCraftingCustodyCensus reusableCustody(String cpuOwner) {
            var sources = new ObjectArrayList<ReusableCraftingCustodyCensus>();
            if (!frozen) for (Entry entry : entries.values()) sources.add(entry.endpoint.reusableCustody(cpuOwner));
            return custody.census(cpuOwner, !frozen, sources);
        }

        @Override
        public Optional<AppendReceipt> reusableReceipt(UUID sessionId, long sequence) {
            Entry entry = locate(sessionId);
            return entry == null ? Optional.empty() : entry.endpoint.receipt(sessionId, sequence);
        }

        @Override
        public void closeReusableSession(UUID sessionId) {
            Entry entry = locate(sessionId);
            if (entry != null) entry.endpoint.close(sessionId, host(entry));
        }

        @Override
        public boolean requestReusableYield(ReusableCraftingRequest contender) {
            Entry entry = findTarget(contender.target().persistentIdentity());
            return !frozen && entry != null && contender.level() == level &&
                    available.test(contender.pattern()) && entry.endpoint.requestYield(contender, level.getGameTime(), host(entry));
        }

        @Override
        public boolean settleReusableSession(UUID sessionId, ReturnReceiver receiver) {
            Entry entry = locate(sessionId);
            return entry != null && entry.endpoint.settle(sessionId, receiver, host(entry));
        }

        private Host host(Entry entry) {
            return new Host() {

                @Override
                public boolean isAvailable(Binding binding) {
                    if (frozen || !level.isLoaded(entry.position) || !binding.identity().target().equals(entry.endpoint.targetIdentity())) return false;
                    var machine = machine(entry);
                    IPatternDetails pattern = PatternDetailsHelper.decodePattern(binding.identity().pattern(), level);
                    if (machine == null || !machine.supportsReusableInputs() || pattern == null || !available.test(pattern) ||
                            !machine.recognizes(level, entry.position) || binding.recipeId().isEmpty())
                        return false;
                    if (!binding.publicationIdentity().equals(TrinityPatternIdentity.capture(
                            TrinityPatternPublicationSignature.capture(pattern), level.registryAccess())))
                        return false;
                    if (!PackagedMachineClaims.get(level).available(entry.position)) return false;
                    var prototype = new KeyCounter[binding.inputSlots()];
                    for (int i = 0; i < prototype.length; i++) prototype[i] = new KeyCounter();
                    for (var material : binding.consumed()) prototype[material.slot()].add(material.stack().what(), material.stack().amount());
                    for (var tool : binding.tools()) prototype[tool.slot()].add(tool.rule().initialKey(), tool.heldAmount());
                    return machine.prepare(level, entry.position, entry.face, ResourceLocation.parse(binding.recipeId().orElseThrow()),
                            pattern, prototype) != null;
                }

                @Override
                public boolean asynchronous() {
                    return true;
                }

                @Override
                public NativeResult execute(Binding binding, Operation operation) {
                    return poll(binding, operation);
                }

                @Override
                public NativeResult poll(Binding binding, Operation operation) {
                    if (frozen || !level.isLoaded(entry.position)) return NativeResult.inFlight();
                    PackagedMachineAdapter machine = machine(entry);
                    if (machine == null) return NativeResult.inFlight();
                    NativeWork active = entry.work;
                    boolean sameOperation = active != null && binding.identity().sessionId().equals(active.session) && operation.id() == active.operation;
                    if (!sameOperation) {
                        if (active != null && !active.machine.completed()) throw new IllegalStateException("Unfinished packaged reusable operation replaced");
                        var pattern = PatternDetailsHelper.decodePattern(binding.identity().pattern(), level);
                        if (pattern == null) return NativeResult.inFlight();
                        var recipeId = ResourceLocation.parse(binding.recipeId().orElseThrow());
                        var inputs = new KeyCounter[binding.inputSlots()];
                        for (int i = 0; i < inputs.length; i++) inputs[i] = new KeyCounter();
                        for (var material : operation.consumed()) inputs[material.slot()].add(material.stack().what(), material.stack().amount());
                        for (var tool : operation.tools()) inputs[tool.slot()].add(tool.stack().what(), tool.stack().amount());
                        var claims = PackagedMachineClaims.get(level);
                        if (!claims.available(entry.position)) return NativeResult.inFlight();
                        var preparation = machine.prepare(level, entry.position, entry.face, recipeId, pattern, inputs);
                        if (preparation == null) return NativeResult.inFlight();
                        var occupied = machine.occupiedPositions(level, entry.position, preparation);
                        if (occupied.stream().anyMatch(position -> !level.isLoaded(position))) return NativeResult.inFlight();
                        PackagedOutputMatching.save(pattern, preparation, level.registryAccess());
                        var work = new PackagedOperationState(entry.adapter, recipeId, entry.position, entry.face, preparation, inputs, occupied);
                        if (!claims.acquireAll(occupied, work.id())) return NativeResult.inFlight();
                        active = new NativeWork(binding.identity().sessionId(), operation.id(), operation.appendSequence(), work, false);
                        entry.work = active;
                        changed.run();
                    }
                    if (active.sequence != operation.appendSequence()) throw new IllegalStateException("Native reusable append identity changed");
                    var work = active.machine;
                    var claims = PackagedMachineClaims.get(level);
                    if (!work.completed()) {
                        if (claims.structureRemoved(work.id())) {
                            throw new IllegalStateException("Reusable machine removed while holding native inputs");
                        }
                        if (!claims.acquireAll(work.occupiedPositions(), work.id())) return NativeResult.inFlight();
                        if (work.advance(level, machine)) changed.run();
                        if (!work.completed()) return NativeResult.inFlight();
                    }
                    NativeResult result = completedResult(active, operation);
                    if (!active.released) {
                        if (claims.structureRemoved(work.id())) claims.acknowledgeRemoval(work.id());
                        else claims.releaseAll(work.occupiedPositions(), work.id());
                        active.released = true;
                        changed.run();
                    }
                    return result;
                }

                @Override
                public Optional<NativeResult> completedCheckpoint(Binding binding, Operation operation) {
                    NativeWork work = entry.work;
                    if (work == null || !work.released || !work.machine.completed() ||
                            !work.session.equals(binding.identity().sessionId()) || work.operation != operation.id() ||
                            work.sequence != operation.appendSequence())
                        return Optional.empty();
                    return Optional.of(completedResult(work, operation));
                }

                @Override
                public void acceptOutputs(Identity identity, List<GenericStack> produced) {
                    for (GenericStack stack : produced) outputs.merge(stack.what(), BigInteger.valueOf(stack.amount()), BigInteger::add);
                }

                @Override
                public void persistChanges() {
                    changed.run();
                }
            };
        }
    }

    private static @Nullable PackagedMachineAdapter machine(Entry entry) {
        return DataEnergisticsEntrypointLoader.snapshot().packagedCrafting().adapter(entry.adapter);
    }

    private static NativeResult completedResult(NativeWork work, Operation operation) {
        var actual = new KeyCounter();
        for (var stack : work.machine.collectedOutputs()) actual.add(stack.what(), stack.amount());
        var tools = new ObjectArrayList<ToolOutcome>();
        for (var tool : operation.tools()) {
            GenericStack stack = tool.stack();
            if (actual.get(stack.what()) < stack.amount()) throw new IllegalStateException("Native machine did not return the held reusable input");
            actual.add(stack.what(), -stack.amount());
            tools.add(new ToolOutcome(tool.slot(), ObjectList.of(stack), ObjectList.of()));
        }
        var produced = new ObjectArrayList<GenericStack>();
        for (var stack : actual) if (stack.getLongValue() > 0) produced.add(new GenericStack(stack.getKey(), stack.getLongValue()));
        return new NativeResult(true, tools, produced, Optional.empty());
    }

    public CompoundTag save(HolderLookup.Provider registries) {
        var tag = new CompoundTag();
        tag.putUUID("provider", providerId);
        var machines = new ListTag();
        for (Entry entry : entries.values()) {
            var saved = new CompoundTag();
            saved.putLong("position", entry.position.asLong());
            saved.putString("face", entry.face.getName());
            saved.putString("adapter", entry.adapter.toString());
            saved.put("endpoint", ReusableCraftingEndpointNbtCodec.encode(entry.endpoint, registries));
            if (entry.work != null) {
                saved.putUUID("session", entry.work.session);
                saved.putLong("operation", entry.work.operation);
                saved.putLong("sequence", entry.work.sequence);
                saved.putBoolean("released", entry.work.released);
                saved.put("machine", entry.work.machine.save(registries));
            }
            machines.add(saved);
        }
        tag.put("machines", machines);
        var pending = new ListTag();
        outputs.forEach((key, amount) -> {
            var saved = new CompoundTag();
            saved.put("key", key.toTagGeneric(registries));
            saved.putString("amount", amount.toString());
            pending.add(saved);
        });
        tag.put("outputs", pending);
        return tag;
    }

    public static PackagedReusableState load(CompoundTag tag, HolderLookup.Provider registries) {
        var state = new PackagedReusableState(tag.getUUID("provider"));
        var machines = tag.getList("machines", Tag.TAG_COMPOUND);
        for (int i = 0; i < machines.size(); i++) {
            var saved = machines.getCompound(i);
            var face = Direction.byName(saved.getString("face"));
            if (face == null) throw new IllegalArgumentException("Invalid reusable machine face");
            var endpoint = ReusableCraftingEndpointNbtCodec.decode(saved.getCompound("endpoint"), registries);
            var entry = new Entry(BlockPos.of(saved.getLong("position")), face, ResourceLocation.parse(saved.getString("adapter")), endpoint);
            if (!endpoint.targetIdentity().equals(state.target(entry.position)) || state.entries.putIfAbsent(endpoint.targetIdentity(), entry) != null)
                throw new IllegalArgumentException("Invalid reusable machine target identity");
            if (saved.contains("machine", Tag.TAG_COMPOUND)) {
                var work = new NativeWork(saved.getUUID("session"), saved.getLong("operation"), saved.getLong("sequence"),
                        PackagedOperationState.load(saved.getCompound("machine"), registries), saved.getBoolean("released"));
                if (work.operation < 0 || work.sequence < 0 || endpoint.query(work.session).isEmpty() ||
                        endpoint.receipt(work.session, work.sequence).isEmpty() || !work.machine.position().equals(entry.position) ||
                        !work.machine.adapterId().equals(entry.adapter) || work.released && !work.machine.completed())
                    throw new IllegalArgumentException("Invalid reusable native operation evidence");
                entry.work = work;
            }
        }
        var pending = tag.getList("outputs", Tag.TAG_COMPOUND);
        for (int i = 0; i < pending.size(); i++) {
            var saved = pending.getCompound(i);
            var key = AEKey.fromTagGeneric(registries, saved.getCompound("key"));
            var amount = new BigInteger(saved.getString("amount"));
            if (key == null || amount.signum() <= 0 || state.outputs.putIfAbsent(key, amount) != null)
                throw new IllegalArgumentException("Invalid reusable pending output");
        }
        return state;
    }

    private static final class Entry {

        final BlockPos position;
        final Direction face;
        final ResourceLocation adapter;
        final PersistentReusableCraftingEndpoint endpoint;
        @Nullable
        NativeWork work;

        Entry(BlockPos position, Direction face, ResourceLocation adapter, PersistentReusableCraftingEndpoint endpoint) {
            this.position = position.immutable();
            this.face = face;
            this.adapter = adapter;
            this.endpoint = endpoint;
        }
    }

    /** Identity and physical evidence are constructed together, including across provider recovery. */
    private static final class NativeWork {

        final UUID session;
        final long operation;
        final long sequence;
        final PackagedOperationState machine;
        boolean released;

        NativeWork(UUID session, long operation, long sequence, PackagedOperationState machine, boolean released) {
            this.session = session;
            this.operation = operation;
            this.sequence = sequence;
            this.machine = machine;
            this.released = released;
        }
    }
}
