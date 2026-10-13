package com.fish_dan_.data_energistics.common.multiblock.json.matching;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.block.storage.CompartmentBlock;
import com.fish_dan_.data_energistics.common.compartment.CompartmentType;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.modularmc.mdl.api.multiblock.MultiblockState;
import com.modularmc.mdl.api.multiblock.PatternCandidate;
import com.modularmc.mdl.api.multiblock.PatternDiagnostic;
import com.modularmc.mdl.api.multiblock.structurepredicate.StructurePredicate;
import com.modularmc.mdl.api.multiblock.structurepredicate.StructurePredicateTypes;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import it.unimi.dsi.fastutil.objects.ObjectSets;

/**
 * Predicate wrapper that allows selected normal structure blocks to be replaced by declared compartment roles.
 */
public record JsonMultiBlockReplaceableCompartmentPredicate(ObjectSet<CompartmentType> compartmentTypes,
                                                            StructurePredicate delegate)
        implements StructurePredicate {

    public static final ResourceLocation TYPE = ResourceLocation.fromNamespaceAndPath(
            Data_Energistics.MODID,
            "replaceable_compartment");
    private static final String COMPARTMENTS_PROPERTY = "compartments";
    private static final String PREDICATE_PROPERTY = "predicate";
    private static boolean registered;

    public JsonMultiBlockReplaceableCompartmentPredicate {
        if (compartmentTypes.isEmpty()) {
            throw new IllegalArgumentException("Replaceable compartment predicate requires at least one compartment type");
        }
        compartmentTypes = ObjectSets.unmodifiable(new ObjectLinkedOpenHashSet<>(compartmentTypes));
    }

    public static synchronized void registerType() {
        if (registered) {
            return;
        }
        StructurePredicateTypes.register(TYPE, JsonMultiBlockReplaceableCompartmentPredicate::fromJson);
        registered = true;
    }

    public static JsonMultiBlockReplaceableCompartmentPredicate fromJson(JsonObject object) {
        return new JsonMultiBlockReplaceableCompartmentPredicate(
                readCompartmentTypes(object),
                StructurePredicateTypes.decode(readRequiredObject(object, PREDICATE_PROPERTY)));
    }

    @Override
    public ResourceLocation type() {
        return TYPE;
    }

    @Override
    public boolean test(MultiblockState state, boolean mutateCount) {
        if (state.getBlockState().getBlock() instanceof CompartmentBlock block) {
            CompartmentType actualType = block.compartmentType();
            if (!this.compartmentTypes.contains(actualType)) {
                state.setDiagnostic(PatternDiagnostic.of(
                        "replaceable_compartment_mismatch",
                        "Replaceable structure slot did not accept this compartment role",
                        state.getPos(),
                        expected()));
                return false;
            }
            JsonMultiBlockCompartmentPredicate.recordMatchedCompartment(
                    state.getMatchContext(),
                    state.getPos(),
                    actualType);
            return true;
        }
        return this.delegate.test(state, mutateCount);
    }

    @Override
    public boolean checkGlobalMinimum(MultiblockState state) {
        return this.delegate.checkGlobalMinimum(state);
    }

    @Override
    public boolean checkLayerMinimum(MultiblockState state) {
        return this.delegate.checkLayerMinimum(state);
    }

    @Override
    public ObjectList<Block> blockCandidates() {
        ObjectArrayList<Block> candidates = new ObjectArrayList<>(this.delegate.blockCandidates());
        for (CompartmentType type : this.compartmentTypes) {
            candidates.add(JsonMultiBlockCompartmentPredicate.blockFor(type));
        }
        return new ObjectImmutableList<>(candidates);
    }

    @Override
    public ObjectList<BlockState> blockStateCandidates() {
        ObjectArrayList<BlockState> candidates = new ObjectArrayList<>(this.delegate.blockStateCandidates());
        for (CompartmentType type : this.compartmentTypes) {
            candidates.add(JsonMultiBlockCompartmentPredicate.blockFor(type).defaultBlockState());
        }
        return new ObjectImmutableList<>(candidates);
    }

    @Override
    public ObjectList<ItemStack> placementCandidates() {
        ObjectArrayList<ItemStack> candidates = new ObjectArrayList<>();
        for (CompartmentType type : this.compartmentTypes) {
            ItemStack stack = JsonMultiBlockCompartmentPredicate.blockFor(type).asItem().getDefaultInstance();
            if (!stack.isEmpty()) {
                candidates.add(stack);
            }
        }
        candidates.addAll(this.delegate.placementCandidates());
        return new ObjectImmutableList<>(candidates);
    }

    @Override
    public ObjectList<PatternCandidate> patternCandidates() {
        ObjectArrayList<PatternCandidate> candidates = new ObjectArrayList<>();
        for (CompartmentType type : this.compartmentTypes) {
            Block block = JsonMultiBlockCompartmentPredicate.blockFor(type);
            ItemStack stack = block.asItem().getDefaultInstance();
            if (stack.isEmpty()) {
                throw new IllegalStateException("Replaceable compartment does not expose a placement item: " + type);
            }
            candidates.add(new PatternCandidate(block.defaultBlockState(), stack));
        }
        candidates.addAll(this.delegate.patternCandidates());
        return new ObjectImmutableList<>(candidates);
    }

    private ObjectList<String> expected() {
        ObjectArrayList<String> values = new ObjectArrayList<>();
        for (CompartmentType type : this.compartmentTypes) {
            values.add(type.id());
        }
        return new ObjectImmutableList<>(values);
    }

    private static ObjectSet<CompartmentType> readCompartmentTypes(JsonObject object) {
        JsonElement compartmentsElement = object.get(COMPARTMENTS_PROPERTY);
        if (compartmentsElement == null || !compartmentsElement.isJsonArray()) {
            throw new IllegalArgumentException("Replaceable compartment predicate requires array property '" +
                    COMPARTMENTS_PROPERTY + "'");
        }
        JsonArray compartments = compartmentsElement.getAsJsonArray();
        ObjectLinkedOpenHashSet<CompartmentType> types = new ObjectLinkedOpenHashSet<>();
        for (JsonElement compartmentElement : compartments) {
            if (!compartmentElement.isJsonPrimitive() || !compartmentElement.getAsJsonPrimitive().isString()) {
                throw new IllegalArgumentException("Replaceable compartment type entries must be strings");
            }
            String typeId = compartmentElement.getAsString();
            CompartmentType type = CompartmentType.byId(typeId)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown replaceable compartment type: " + typeId));
            types.add(type);
        }
        return types;
    }

    private static JsonObject readRequiredObject(JsonObject object, String property) {
        if (!object.has(property) || !object.get(property).isJsonObject()) {
            throw new IllegalArgumentException("Replaceable compartment predicate requires object property '" + property + "'");
        }
        return object.get(property).getAsJsonObject();
    }
}
