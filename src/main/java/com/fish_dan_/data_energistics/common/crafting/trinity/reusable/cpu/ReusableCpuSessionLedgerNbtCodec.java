package com.fish_dan_.data_energistics.common.crafting.trinity.reusable.cpu;

import com.fish_dan_.data_energistics.api.crafting.dispatch.CountedCraftingTarget;
import com.fish_dan_.data_energistics.api.crafting.dispatch.VirtualCraftingCompletion;
import com.fish_dan_.data_energistics.api.crafting.dispatch.VirtualCraftingCompletionMode;
import com.fish_dan_.data_energistics.api.crafting.matching.ItemMatchingRule;
import com.fish_dan_.data_energistics.api.crafting.reusable.dispatch.ReusableCraftingRequest.SlotStack;
import com.fish_dan_.data_energistics.api.crafting.reusable.dispatch.ReusableCraftingRequest.Target;
import com.fish_dan_.data_energistics.common.crafting.trinity.execution.state.TrinityPlanExecution.Work;
import com.fish_dan_.data_energistics.common.crafting.trinity.execution.state.persistence.TrinityBoundInputSnapshotCodec;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternIdentity;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.cpu.ReusableCpuSessionLedger.DynamicOutput;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.cpu.ReusableCpuSessionLedger.OutputContract;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.cpu.ReusableCpuSessionLedger.RemoteCustodyEvidence;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.cpu.ReusableCpuSessionLedger.SessionSnapshot;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.cpu.ReusableCpuSessionLedger.Snapshot;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.cpu.ReusableCpuSessionLedger.Submission;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.cpu.ReusableCpuSessionLedger.SubmissionEntry;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;

import java.util.Optional;
import java.util.UUID;

/** Persists CPU custody independently of the current job; receipt metadata is never decoded as physical stock. */
public final class ReusableCpuSessionLedgerNbtCodec {

    private ReusableCpuSessionLedgerNbtCodec() {}

    public static CompoundTag encode(ReusableCpuSessionLedger ledger, HolderLookup.Provider registries) {
        Snapshot snapshot = ledger.snapshot();
        CompoundTag tag = new CompoundTag();
        tag.putUUID("owner", snapshot.owner());
        ListTag replanning = new ListTag();
        for (UUID job : snapshot.replanningJobs()) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("job", job);
            replanning.add(entry);
        }
        tag.put("replanning_jobs", replanning);
        ListTag uncertain = new ListTag();
        for (UUID sessionId : snapshot.uncertainSessions()) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("session", sessionId);
            uncertain.add(entry);
        }
        tag.put("uncertain_sessions", uncertain);
        ListTag evidence = new ListTag();
        for (RemoteCustodyEvidence claim : snapshot.remoteEvidence()) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("session", claim.sessionId());
            entry.putUUID("job", claim.jobId());
            entry.putString("target", claim.targetIdentity());
            entry.putUUID("loaded_epoch", claim.loadedEpoch());
            entry.putLong("revision", claim.revision());
            entry.putLong("accepted", claim.accepted());
            entry.putBoolean("acknowledged", claim.settlementAcknowledged());
            entry.putString("reason", claim.reason());
            evidence.add(entry);
        }
        tag.put("remote_evidence", evidence);
        ListTag sessions = new ListTag();
        for (SessionSnapshot session : snapshot.sessions()) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("id", session.id());
            entry.putUUID("job", session.jobId());
            entry.putString("target", session.target().persistentIdentity());
            entry.putString("route", session.target().route().stableIdentity());
            session.target().route().machineIdentity().ifPresent(value -> entry.putString("machine", value));
            session.target().mode().ifPresent(value -> entry.putString("mode", value.toString()));
            entry.put("pattern", session.pattern().toTagGeneric(registries));
            entry.putString("definition", session.publication().definitionEncoding());
            entry.putString("publication", session.publication().publicationEncoding());
            entry.put("bindings", TrinityBoundInputSnapshotCodec.write(session.bindings(), registries));
            entry.putLong("next_sequence", session.nextSequence());
            entry.putBoolean("closing", session.closing());
            if (session.settlementFingerprint() != null) {
                entry.putString("settlement", session.settlementFingerprint());
            }
            ListTag submissions = new ListTag();
            for (SubmissionEntry saved : session.submissions()) {
                Submission submission = saved.submission();
                CompoundTag item = new CompoundTag();
                item.putLong("sequence", saved.sequence());
                item.put("work", writeWork(submission.work(), registries));
                item.putLong("count", submission.count());
                item.putLong("offer", submission.logicalOffer());
                item.putDouble("energy", submission.energy());
                item.put("products", writeAssets(submission.outputs().products(), registries));
                item.put("remainders", writeAssets(submission.outputs().remainders(), registries));
                ListTag dynamic = new ListTag();
                for (DynamicOutput output : submission.outputs().dynamic()) {
                    CompoundTag value = GenericStack.writeTag(registries, output.stack());
                    value.putBoolean("final", output.finalOutput());
                    value.putString("source", output.source().toString());
                    value.put("rule", output.rule().save());
                    value.put("template", output.templateKey().toTagGeneric(registries));
                    dynamic.add(value);
                }
                item.put("dynamic", dynamic);
                ListTag virtual = new ListTag();
                for (VirtualCraftingCompletion output : submission.outputs().virtual()) {
                    CompoundTag value = GenericStack.writeTag(registries, output.stack());
                    value.putString("mode", output.mode().name());
                    virtual.add(value);
                }
                item.put("virtual", virtual);
                item.putLong("completed", submission.completed());
                ListTag escrow = new ListTag();
                for (SlotStack asset : submission.physicalInputs()) {
                    CompoundTag value = GenericStack.writeTag(registries, asset.stack());
                    value.putInt("input_slot", asset.slot());
                    escrow.add(value);
                }
                item.put("physical_inputs", escrow);
                item.putBoolean("transferred", submission.transferred());
                item.putBoolean("accounted", submission.accounted());
                item.putBoolean("waiting_registered", submission.waitingRegistered());
                submissions.add(item);
            }
            entry.put("submissions", submissions);
            sessions.add(entry);
        }
        tag.put("sessions", sessions);
        return tag;
    }

    public static ReusableCpuSessionLedger decode(CompoundTag tag, HolderLookup.Provider registries) {
        UUID owner = tag.getUUID("owner");
        ObjectOpenHashSet<UUID> replanning = new ObjectOpenHashSet<>();
        ObjectOpenHashSet<UUID> uncertain = new ObjectOpenHashSet<>();
        ObjectList<RemoteCustodyEvidence> evidence = new ObjectArrayList<>();
        for (Tag value : tag.getList("remote_evidence", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) value;
            evidence.add(new RemoteCustodyEvidence(entry.getUUID("session"), entry.getUUID("job"), entry.getString("target"),
                    entry.getUUID("loaded_epoch"), entry.getLong("revision"), entry.getLong("accepted"), entry.getBoolean("acknowledged"),
                    entry.getString("reason")));
        }
        for (Tag value : tag.getList("replanning_jobs", Tag.TAG_COMPOUND)) {
            if (!replanning.add(((CompoundTag) value).getUUID("job"))) {
                throw new IllegalArgumentException("Duplicate reusable CPU recovery job");
            }
        }
        for (Tag value : tag.getList("uncertain_sessions", Tag.TAG_COMPOUND)) {
            if (!uncertain.add(((CompoundTag) value).getUUID("session"))) {
                throw new IllegalArgumentException("Duplicate quarantined reusable session");
            }
        }
        ObjectList<SessionSnapshot> sessions = new ObjectArrayList<>();
        for (Tag value : tag.getList("sessions", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) value;
            Target target = new Target(entry.getString("target"),
                    new CountedCraftingTarget(false, entry.getString("route"), entry.contains("machine") ?
                            Optional.of(entry.getString("machine")) : Optional.empty()),
                    entry.contains("mode") ? Optional.of(ResourceLocation.parse(entry.getString("mode"))) : Optional.empty());
            if (!(key(entry.getCompound("pattern"), registries) instanceof AEItemKey pattern)) {
                throw new IllegalArgumentException("Reusable CPU pattern must be an item key");
            }
            ObjectList<SubmissionEntry> submissions = new ObjectArrayList<>();
            for (Tag item : entry.getList("submissions", Tag.TAG_COMPOUND)) {
                CompoundTag stored = (CompoundTag) item;
                ObjectList<SlotStack> escrow = new ObjectArrayList<>();
                for (Tag asset : stored.getList("physical_inputs", Tag.TAG_COMPOUND)) {
                    CompoundTag owned = (CompoundTag) asset;
                    escrow.add(new SlotStack(owned.getInt("input_slot"), stack(owned, registries)));
                }
                ObjectList<DynamicOutput> dynamic = new ObjectArrayList<>();
                ObjectList<VirtualCraftingCompletion> virtual = new ObjectArrayList<>();
                for (Tag valueOutput : stored.getList("dynamic", Tag.TAG_COMPOUND)) {
                    CompoundTag output = (CompoundTag) valueOutput;
                    var stack = stack(output, registries);
                    var template = key(output.getCompound("template"), registries);
                    if (!(template instanceof AEItemKey templateKey)) throw new IllegalArgumentException("Invalid dynamic output custody template");
                    dynamic.add(new DynamicOutput(stack, output.getBoolean("final"), ResourceLocation.parse(output.getString("source")),
                            ItemMatchingRule.load(output.getCompound("rule")), templateKey));
                }
                for (Tag valueOutput : stored.getList("virtual", Tag.TAG_COMPOUND)) {
                    CompoundTag output = (CompoundTag) valueOutput;
                    virtual.add(new VirtualCraftingCompletion(stack(output, registries), VirtualCraftingCompletionMode.valueOf(output.getString("mode"))));
                }
                OutputContract outputs = new OutputContract(readAssets(stored.getList("products", Tag.TAG_COMPOUND), registries),
                        readAssets(stored.getList("remainders", Tag.TAG_COMPOUND), registries), dynamic, virtual);
                submissions.add(new SubmissionEntry(stored.getLong("sequence"), new Submission(
                        readWork(stored.getCompound("work"), registries), stored.getLong("count"), stored.getLong("offer"),
                        stored.getDouble("energy"), outputs, escrow,
                        stored.getBoolean("transferred"), stored.getBoolean("waiting_registered"),
                        stored.getBoolean("accounted"), stored.getLong("completed"))));
            }
            sessions.add(new SessionSnapshot(entry.getUUID("id"), entry.getUUID("job"), target, pattern,
                    new TrinityPatternIdentity(entry.getString("definition"), entry.getString("publication")),
                    new ObjectImmutableList<>(TrinityBoundInputSnapshotCodec.read(entry.getList("bindings", Tag.TAG_COMPOUND), registries)), submissions,
                    entry.getLong("next_sequence"), entry.getBoolean("closing"), entry.contains("settlement") ? entry.getString("settlement") : null));
        }
        return ReusableCpuSessionLedger.restore(new Snapshot(owner, sessions, replanning, uncertain, evidence));
    }

    private static CompoundTag writeWork(Work work, HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putLong("generation", work.generation());
        tag.putInt("stage", work.stageIndex());
        tag.putInt("firing", work.firingIndex());
        tag.putString("definition", work.patternIdentity().definitionEncoding());
        tag.putString("publication", work.patternIdentity().publicationEncoding());
        tag.put("output", work.primaryOutput().toTagGeneric(registries));
        tag.putInt("variant", work.plannedVariantOrdinal());
        tag.putLong("maximum", work.maximumLogicalFirings());
        tag.putBoolean("cycle", work.cycle());
        tag.put("bindings", TrinityBoundInputSnapshotCodec.write(work.exactBindings(), registries));
        return tag;
    }

    private static Work readWork(CompoundTag tag, HolderLookup.Provider registries) {
        return new Work(tag.getLong("generation"), tag.getInt("stage"), tag.getInt("firing"),
                new TrinityPatternIdentity(tag.getString("definition"), tag.getString("publication")),
                key(tag.getCompound("output"), registries), tag.getInt("variant"), tag.getLong("maximum"), tag.getBoolean("cycle"),
                new ObjectImmutableList<>(TrinityBoundInputSnapshotCodec.read(tag.getList("bindings", Tag.TAG_COMPOUND), registries)));
    }

    private static ListTag writeAssets(ObjectList<GenericStack> assets, HolderLookup.Provider registries) {
        ListTag tag = new ListTag();
        assets.forEach(asset -> tag.add(GenericStack.writeTag(registries, asset)));
        return tag;
    }

    private static ObjectList<GenericStack> readAssets(ListTag tag, HolderLookup.Provider registries) {
        ObjectList<GenericStack> result = new ObjectArrayList<>(tag.size());
        for (Tag value : tag) {
            result.add(stack((CompoundTag) value, registries));
        }
        return result;
    }

    private static GenericStack stack(CompoundTag tag, HolderLookup.Provider registries) {
        GenericStack stack = GenericStack.readTag(registries, tag);
        if (stack == null || stack.amount() <= 0L) {
            throw new IllegalArgumentException("Invalid reusable CPU asset");
        }
        return stack;
    }

    private static AEKey key(CompoundTag tag, HolderLookup.Provider registries) {
        AEKey key = AEKey.fromTagGeneric(registries, tag);
        if (key == null) {
            throw new IllegalArgumentException("Unknown reusable CPU key");
        }
        return key;
    }
}
