package com.fish_dan_.data_energistics.common.crafting.trinity.reusable.session;

import com.fish_dan_.data_energistics.api.crafting.reusable.ReusableInputContext.Ownership;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.rules.ReusableInputRuleNbtCodec;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.session.ReusableInputSession.Append;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.session.ReusableInputSession.AppendSnapshot;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.session.ReusableInputSession.Identity;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.session.ReusableInputSession.Operation;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.session.ReusableInputSession.ReturnBatch;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.session.ReusableInputSession.SlotContract;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.session.ReusableInputSession.SlotInput;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.session.ReusableInputSession.Snapshot;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.session.ReusableInputSession.State;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.session.ReusableInputSession.ToolDelivery;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import it.unimi.dsi.fastutil.objects.ObjectList;

import java.util.Optional;

/** Complete current-format session escrow encoding. Malformed asset/progress relationships fail at load. */
public final class ReusableInputSessionNbtCodec {

    private ReusableInputSessionNbtCodec() {}

    public static CompoundTag encode(ReusableInputSession session, HolderLookup.Provider registries) {
        Snapshot snapshot = session.snapshot();
        CompoundTag tag = new CompoundTag();
        Identity identity = snapshot.identity();
        tag.putUUID("session_id", identity.sessionId());
        tag.putUUID("job_id", identity.jobId());
        tag.putString("cpu_owner", identity.cpuOwner());
        tag.putString("target", identity.target());
        tag.put("pattern", identity.pattern().toTagGeneric(registries));
        identity.mode().ifPresent(mode -> tag.putString("mode", mode));
        tag.putString("state", snapshot.state().name());
        ListTag contracts = new ListTag();
        for (SlotContract contract : snapshot.contracts()) {
            CompoundTag entry = new CompoundTag();
            entry.putInt("slot", contract.slot());
            entry.putLong("held", contract.heldAmount());
            entry.putString("ownership", contract.ownership().name());
            entry.put("rule", ReusableInputRuleNbtCodec.encode(contract.rule(), registries));
            contracts.add(entry);
        }
        tag.put("contracts", contracts);
        ListTag appends = new ListTag();
        for (AppendSnapshot append : snapshot.appends()) {
            CompoundTag entry = encodeAppend(append.request(), registries);
            entry.putLong("completed", append.completed());
            entry.putLong("cancelled", append.cancelled());
            entry.put("remaining", encodeAssets(append.remainingMaterials(), registries));
            appends.add(entry);
        }
        tag.put("appends", appends);
        tag.put("tools", encodeTools(snapshot.tools(), registries));
        if (snapshot.active() != null) {
            Operation active = snapshot.active();
            CompoundTag entry = new CompoundTag();
            entry.putLong("id", active.id());
            entry.putLong("append", active.appendSequence());
            entry.putLong("count", active.count());
            entry.put("consumed", encodeInputs(active.consumed(), registries));
            entry.put("tools", encodeTools(active.tools(), registries));
            tag.put("active", entry);
        }
        tag.put("outputs", encodeAssets(snapshot.outputs(), registries));
        tag.put("returns", encodeReturns(snapshot.returns(), registries));
        tag.put("acknowledged", encodeReturns(snapshot.acknowledged(), registries));
        tag.put("machine_released", encodeTools(snapshot.machineOwnedReleased(), registries));
        tag.putLong("next_operation", snapshot.nextOperation());
        tag.putLong("next_return", snapshot.nextReturn());
        tag.putLong("idle_since", snapshot.idleSince());
        tag.putLong("yield_requested_at", snapshot.yieldRequestedAt());
        tag.putLong("exhausted", snapshot.exhaustedTools());
        tag.putString("fault", snapshot.fault());
        return tag;
    }

    /** Restores all idempotency records and assets; interrupted native effects remain quarantined. */
    public static ReusableInputSession decode(CompoundTag tag, HolderLookup.Provider registries) {
        return decode(tag, registries, -1);
    }

    /** Decodes a validated durable native checkpoint; the active operation must match exactly. */
    public static ReusableInputSession decodeWithNativeCheckpoint(CompoundTag tag, HolderLookup.Provider registries,
                                                                  long operationId) {
        if (operationId < 0) throw new IllegalArgumentException("Invalid native checkpoint operation");
        return decode(tag, registries, operationId);
    }

    private static ReusableInputSession decode(CompoundTag tag, HolderLookup.Provider registries, long operationId) {
        Identity identity = new Identity(tag.getUUID("session_id"), tag.getUUID("job_id"), tag.getString("cpu_owner"),
                tag.getString("target"), item(tag, "pattern", registries),
                tag.contains("mode") ? Optional.of(tag.getString("mode")) : Optional.empty());
        ObjectArrayList<SlotContract> contracts = new ObjectArrayList<>();
        for (Tag encoded : tag.getList("contracts", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) encoded;
            contracts.add(new SlotContract(entry.getInt("slot"), entry.getLong("held"),
                    Ownership.valueOf(entry.getString("ownership")),
                    ReusableInputRuleNbtCodec.decode(entry.getCompound("rule"), registries)));
        }
        ObjectArrayList<AppendSnapshot> appends = new ObjectArrayList<>();
        for (Tag encoded : tag.getList("appends", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) encoded;
            appends.add(new AppendSnapshot(decodeAppend(entry, registries), entry.getLong("completed"),
                    entry.getLong("cancelled"), decodeAssets(entry, "remaining", registries)));
        }
        Operation active = null;
        if (tag.contains("active")) {
            CompoundTag entry = tag.getCompound("active");
            active = new Operation(entry.getLong("id"), entry.getLong("append"), entry.getLong("count"),
                    decodeInputs(entry, "consumed", registries), decodeTools(entry, "tools", registries));
        }
        Snapshot snapshot = new Snapshot(identity, contracts, State.valueOf(tag.getString("state")), appends,
                decodeTools(tag, "tools", registries), active, decodeAssets(tag, "outputs", registries),
                decodeReturns(tag, "returns", registries), decodeReturns(tag, "acknowledged", registries),
                decodeTools(tag, "machine_released", registries), tag.getLong("next_operation"), tag.getLong("next_return"),
                tag.getLong("idle_since"), tag.getLong("yield_requested_at"), tag.getLong("exhausted"), tag.getString("fault"));
        return operationId < 0 ? ReusableInputSession.restore(snapshot) :
                ReusableInputSession.restoreWithNativeCheckpoint(snapshot, operationId);
    }

    private static CompoundTag encodeAppend(Append append, HolderLookup.Provider registries) {
        CompoundTag entry = new CompoundTag();
        entry.putLong("sequence", append.sequence());
        entry.putLong("operations", append.operations());
        entry.put("consumed", encodeInputs(append.consumedPerOperation(), registries));
        entry.put("delivered_materials", encodeAssets(append.deliveredMaterials(), registries));
        entry.put("delivered_tools", encodeTools(append.deliveredTools(), registries));
        ListTag states = new ListTag();
        for (var state : append.operationStates().int2ObjectEntrySet()) {
            CompoundTag value = new CompoundTag();
            value.putInt("slot", state.getIntKey());
            value.put("state", state.getValue().toTagGeneric(registries));
            states.add(value);
        }
        entry.put("operation_states", states);
        return entry;
    }

    private static Append decodeAppend(CompoundTag tag, HolderLookup.Provider registries) {
        Int2ObjectMap<AEItemKey> states = new Int2ObjectLinkedOpenHashMap<>();
        for (Tag encoded : tag.getList("operation_states", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) encoded;
            if (states.putIfAbsent(entry.getInt("slot"), item(entry, "state", registries)) != null) {
                throw new IllegalArgumentException("Duplicate exact operation-state slot");
            }
        }
        return new Append(tag.getLong("sequence"), tag.getLong("operations"), decodeInputs(tag, "consumed", registries),
                decodeAssets(tag, "delivered_materials", registries), decodeTools(tag, "delivered_tools", registries), states);
    }

    private static ListTag encodeInputs(ObjectList<SlotInput> inputs, HolderLookup.Provider registries) {
        ListTag result = new ListTag();
        for (SlotInput input : inputs) {
            CompoundTag entry = new CompoundTag();
            entry.putInt("slot", input.slot());
            entry.put("stack", GenericStack.writeTag(registries, input.stack()));
            result.add(entry);
        }
        return result;
    }

    private static ObjectList<SlotInput> decodeInputs(CompoundTag tag, String field, HolderLookup.Provider registries) {
        ObjectArrayList<SlotInput> result = new ObjectArrayList<>();
        for (Tag encoded : tag.getList(field, Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) encoded;
            result.add(new SlotInput(entry.getInt("slot"), stack(entry, "stack", registries)));
        }
        return new ObjectImmutableList<>(result);
    }

    private static ListTag encodeTools(ObjectList<ToolDelivery> tools, HolderLookup.Provider registries) {
        ListTag result = new ListTag();
        for (ToolDelivery tool : tools) {
            CompoundTag entry = new CompoundTag();
            entry.putInt("slot", tool.slot());
            entry.put("stack", GenericStack.writeTag(registries, tool.stack()));
            result.add(entry);
        }
        return result;
    }

    private static ObjectList<ToolDelivery> decodeTools(CompoundTag tag, String field, HolderLookup.Provider registries) {
        ObjectArrayList<ToolDelivery> result = new ObjectArrayList<>();
        for (Tag encoded : tag.getList(field, Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) encoded;
            result.add(new ToolDelivery(entry.getInt("slot"), stack(entry, "stack", registries)));
        }
        return new ObjectImmutableList<>(result);
    }

    private static ListTag encodeReturns(ObjectList<ReturnBatch> batches, HolderLookup.Provider registries) {
        ListTag result = new ListTag();
        for (ReturnBatch batch : batches) {
            CompoundTag entry = new CompoundTag();
            entry.putLong("sequence", batch.sequence());
            entry.put("assets", encodeAssets(batch.assets(), registries));
            result.add(entry);
        }
        return result;
    }

    private static ObjectList<ReturnBatch> decodeReturns(CompoundTag tag, String field, HolderLookup.Provider registries) {
        ObjectArrayList<ReturnBatch> result = new ObjectArrayList<>();
        for (Tag encoded : tag.getList(field, Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) encoded;
            result.add(new ReturnBatch(entry.getLong("sequence"), decodeAssets(entry, "assets", registries)));
        }
        return new ObjectImmutableList<>(result);
    }

    private static ListTag encodeAssets(ObjectList<GenericStack> assets, HolderLookup.Provider registries) {
        ListTag result = new ListTag();
        assets.forEach(asset -> result.add(GenericStack.writeTag(registries, asset)));
        return result;
    }

    private static ObjectList<GenericStack> decodeAssets(CompoundTag tag, String field, HolderLookup.Provider registries) {
        ObjectArrayList<GenericStack> result = new ObjectArrayList<>();
        for (Tag encoded : tag.getList(field, Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) encoded;
            GenericStack stack = GenericStack.readTag(registries, entry);
            if (stack == null || stack.amount() <= 0) {
                throw new IllegalArgumentException("Invalid persisted session asset");
            }
            result.add(stack);
        }
        return new ObjectImmutableList<>(result);
    }

    private static GenericStack stack(CompoundTag tag, String field, HolderLookup.Provider registries) {
        GenericStack stack = GenericStack.readTag(registries, tag.getCompound(field));
        if (stack == null || stack.amount() <= 0) {
            throw new IllegalArgumentException("Invalid persisted session stack: " + field);
        }
        return stack;
    }

    private static AEItemKey item(CompoundTag tag, String field, HolderLookup.Provider registries) {
        if (!(AEKey.fromTagGeneric(registries, tag.getCompound(field)) instanceof AEItemKey item)) {
            throw new IllegalArgumentException("Invalid persisted session item: " + field);
        }
        return item;
    }
}
