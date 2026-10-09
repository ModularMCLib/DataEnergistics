package com.fish_dan_.data_energistics.common.crafting.trinity.reusable.endpoint;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternIdentity;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.endpoint.PersistentReusableCraftingEndpoint.Binding;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.endpoint.PersistentReusableCraftingEndpoint.EntrySnapshot;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.endpoint.PersistentReusableCraftingEndpoint.NativeResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.endpoint.PersistentReusableCraftingEndpoint.RecordedNativeResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.session.ReusableInputSession;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.session.ReusableInputSession.SlotInput;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.session.ReusableInputSession.ToolOutcome;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.session.ReusableInputSessionNbtCodec;

import appeng.api.stacks.GenericStack;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/** Native endpoint metadata plus complete session escrows; embedded directly in the owning core/provider state. */
public final class ReusableCraftingEndpointNbtCodec {

    private ReusableCraftingEndpointNbtCodec() {}

    public static CompoundTag encode(PersistentReusableCraftingEndpoint endpoint, HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putString("target", endpoint.targetIdentity());
        ListTag sessions = new ListTag();
        for (EntrySnapshot snapshot : endpoint.snapshot()) {
            CompoundTag entry = new CompoundTag();
            entry.put("session", ReusableInputSessionNbtCodec.encode(snapshot.session(), registries));
            entry.putInt("input_slots", snapshot.binding().inputSlots());
            entry.putString("publication_definition", snapshot.binding().publicationIdentity().definitionEncoding());
            entry.putString("publication_semantics", snapshot.binding().publicationIdentity().publicationEncoding());
            ListTag consumed = new ListTag();
            for (SlotInput input : snapshot.binding().consumed()) {
                CompoundTag material = new CompoundTag();
                material.putInt("slot", input.slot());
                material.put("stack", GenericStack.writeTag(registries, input.stack()));
                consumed.add(material);
            }
            entry.put("consumed", consumed);
            snapshot.binding().recipeId().ifPresent(recipe -> entry.putString("recipe", recipe));
            entry.putLong("revision", snapshot.revision());
            entry.putLong("not_before", snapshot.notBefore());
            entry.putBoolean("acknowledged", snapshot.settlementAcknowledged());
            entry.putString("failure", snapshot.failure());
            entry.put("native_result", encodeResult(snapshot.recordedResult(), registries));
            sessions.add(entry);
        }
        tag.put("sessions", sessions);
        return tag;
    }

    /** Decodes into detached state before the owner swaps it into its live core. */
    public static PersistentReusableCraftingEndpoint decode(CompoundTag tag, HolderLookup.Provider registries) {
        ObjectArrayList<EntrySnapshot> snapshots = new ObjectArrayList<>();
        for (Tag encoded : tag.getList("sessions", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) encoded;
            CompoundTag nativeResult = entry.getCompound("native_result");
            RecordedNativeResult recorded = decodeResult(nativeResult, registries);
            ReusableInputSession session = recorded != null && recorded.asynchronous() ?
                    ReusableInputSessionNbtCodec.decodeWithNativeCheckpoint(entry.getCompound("session"), registries, recorded.operationId()) :
                    ReusableInputSessionNbtCodec.decode(entry.getCompound("session"), registries);
            ObjectArrayList<SlotInput> consumed = new ObjectArrayList<>();
            for (Tag inputTag : entry.getList("consumed", Tag.TAG_COMPOUND)) {
                CompoundTag input = (CompoundTag) inputTag;
                GenericStack stack = GenericStack.readTag(registries, input.getCompound("stack"));
                if (stack == null) {
                    throw new IllegalArgumentException("Unknown persisted native material key");
                }
                consumed.add(new SlotInput(input.getInt("slot"), stack));
            }
            Optional<String> recipe = Optional.empty();
            if (entry.contains("recipe")) {
                recipe = Optional.of(ResourceLocation.parse(entry.getString("recipe")).toString());
            }
            TrinityPatternIdentity publication = new TrinityPatternIdentity(entry.getString("publication_definition"),
                    entry.getString("publication_semantics"));
            Binding binding = new Binding(session.identity(), publication, entry.getInt("input_slots"), consumed,
                    new ObjectImmutableList<>(session.slotContracts()), recipe);
            snapshots.add(new EntrySnapshot(binding, session, entry.getLong("revision"), entry.getLong("not_before"),
                    entry.getBoolean("acknowledged"), entry.getString("failure"), recorded));
        }
        return PersistentReusableCraftingEndpoint.restore(tag.getString("target"), snapshots);
    }

    private static CompoundTag encodeResult(@Nullable RecordedNativeResult recorded, HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        if (recorded == null) return tag;
        tag.putUUID("epoch", recorded.loadedEpoch());
        tag.putLong("operation", recorded.operationId());
        tag.putBoolean("asynchronous", recorded.asynchronous());
        NativeResult result = recorded.result();
        tag.putBoolean("executed", result.executed());
        tag.putBoolean("pending", result.pending());
        tag.putString("failure", result.failure().orElse(""));
        tag.put("outputs", stacks(result.outputs(), registries));
        ListTag tools = new ListTag();
        for (ToolOutcome outcome : result.tools()) {
            CompoundTag tool = new CompoundTag();
            tool.putInt("slot", outcome.slot());
            tool.put("successors", stacks(new ObjectImmutableList<>(outcome.successors()), registries));
            tool.put("byproducts", stacks(new ObjectImmutableList<>(outcome.byproducts()), registries));
            tools.add(tool);
        }
        tag.put("tools", tools);
        return tag;
    }

    private static @Nullable RecordedNativeResult decodeResult(CompoundTag tag, HolderLookup.Provider registries) {
        if (tag.isEmpty()) return null;
        if (!tag.hasUUID("epoch")) throw new IllegalArgumentException("Native result checkpoint has no loaded epoch");
        ObjectArrayList<ToolOutcome> tools = new ObjectArrayList<>();
        for (Tag encoded : tag.getList("tools", Tag.TAG_COMPOUND)) {
            CompoundTag tool = (CompoundTag) encoded;
            tools.add(new ToolOutcome(tool.getInt("slot"), readStacks(tool, "successors", registries), readStacks(tool, "byproducts", registries)));
        }
        String failure = tag.getString("failure");
        return new RecordedNativeResult(tag.getUUID("epoch"), tag.getLong("operation"), new NativeResult(tag.getBoolean("executed"), tools,
                readStacks(tag, "outputs", registries), failure.isEmpty() ? Optional.empty() : Optional.of(failure), tag.getBoolean("pending")),
                tag.getBoolean("asynchronous"));
    }

    private static ListTag stacks(ObjectList<GenericStack> stacks, HolderLookup.Provider registries) {
        ListTag result = new ListTag();
        for (GenericStack stack : stacks) result.add(GenericStack.writeTag(registries, stack));
        return result;
    }

    private static ObjectList<GenericStack> readStacks(CompoundTag tag, String field, HolderLookup.Provider registries) {
        ObjectArrayList<GenericStack> result = new ObjectArrayList<>();
        for (Tag encoded : tag.getList(field, Tag.TAG_COMPOUND)) {
            GenericStack stack = GenericStack.readTag(registries, (CompoundTag) encoded);
            if (stack == null || stack.amount() <= 0) throw new IllegalArgumentException("Invalid recorded native asset in " + field);
            result.add(stack);
        }
        return new ObjectImmutableList<>(result);
    }
}
