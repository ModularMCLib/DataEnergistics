package com.fish_dan_.data_energistics.common.multiblock.json.matching;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.compartment.CompartmentBindingHandle;
import com.fish_dan_.data_energistics.common.compartment.CompartmentHost;
import com.fish_dan_.data_energistics.common.compartment.CompartmentPart;
import com.fish_dan_.data_energistics.common.compartment.CompartmentType;
import com.fish_dan_.data_energistics.common.multiblock.vertical.VerticalMultiBlockPos;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;

import com.modularmc.mdl.api.multiblock.PatternDiagnostic;
import com.modularmc.mdl.api.multiblock.StructureMatchResult;
import com.modularmc.mdl.api.multiblock.StructureWorldView;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceMap;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import org.apache.logging.log4j.Logger;
import org.jspecify.annotations.Nullable;

/**
 * Default reusable binder for JSON-declared multiblock compartments.
 */
public final class JsonDeclaredCompartmentBinder implements JsonMultiBlockCompartmentBinder {

    private static final Logger LOGGER = Data_Energistics.LOGGER;
    /**
     * Runtime-only identities retained until this binder releases the matching structure.
     */
    private final Reference2ReferenceMap<CompartmentHost, Object2ObjectMap<String, Reference2ReferenceMap<CompartmentPart, CompartmentBindingHandle>>> bindingHandles = new Reference2ReferenceOpenHashMap<>();

    @Nullable
    @Override
    public PatternDiagnostic validate(StructureWorldView world,
                                      StructureMatchResult result,
                                      Long2ObjectMap<CompartmentType> declaredCompartments) {
        for (Long2ObjectMap.Entry<CompartmentType> entry : declaredCompartments.long2ObjectEntrySet()) {
            BlockPos position = BlockPos.of(entry.getLongKey());
            PatternDiagnostic diagnostic = validateDeclaredPart(world, position, entry.getValue());
            if (diagnostic != null) {
                return diagnostic;
            }
        }
        for (BlockPos pos : result.positions()) {
            if (declaredCompartments.containsKey(pos.asLong())) {
                continue;
            }
            BlockEntity blockEntity = world.getBlockEntity(pos);
            if (blockEntity instanceof CompartmentPart part) {
                String message = "Structure matched undeclared compartment " + part.compartmentType().id() +
                        " at " + pos;
                LOGGER.warn(message);
                return PatternDiagnostic.of("compartment_part_undeclared", message, pos, ObjectList.of());
            }
        }
        return null;
    }

    @Override
    public void bind(StructureWorldView world,
                     String structureName,
                     CompartmentHost host,
                     Long2ObjectMap<CompartmentType> declaredCompartments) {
        for (Long2ObjectMap.Entry<CompartmentType> entry : declaredCompartments.long2ObjectEntrySet()) {
            bindDeclaredPart(world, structureName, host, BlockPos.of(entry.getLongKey()), entry.getValue());
        }
    }

    @Override
    public void ensureBound(StructureWorldView world,
                            String structureName,
                            CompartmentHost host,
                            Long2ObjectMap<CompartmentType> declaredCompartments) {
        Long2ObjectMap<CompartmentPart> currentDeclaredParts = new Long2ObjectLinkedOpenHashMap<>();
        for (Long2ObjectMap.Entry<CompartmentType> entry : declaredCompartments.long2ObjectEntrySet()) {
            currentDeclaredParts.put(entry.getLongKey(), requireDeclaredPart(world, BlockPos.of(entry.getLongKey()), entry.getValue()));
        }

        ReferenceOpenHashSet<CompartmentPart> registeredParts = new ReferenceOpenHashSet<>();
        for (CompartmentPart registeredPart : new ObjectArrayList<>(host.compartmentHost$getCompartments(structureName))) {
            registeredParts.add(registeredPart);
        }
        Object2ObjectMap<String, Reference2ReferenceMap<CompartmentPart, CompartmentBindingHandle>> hostBindings = this.bindingHandles.get(host);
        if (hostBindings != null) {
            Reference2ReferenceMap<CompartmentPart, CompartmentBindingHandle> structureBindings = hostBindings.get(structureName);
            if (structureBindings != null) {
                for (CompartmentPart registeredPart : new ObjectArrayList<>(structureBindings.keySet())) {
                    registeredParts.add(registeredPart);
                }
            }
        }
        for (CompartmentPart registeredPart : registeredParts) {
            CompartmentPart currentPart = currentDeclaredParts.get(toLong(registeredPart.compartmentPos()));
            if (registeredPart != currentPart) {
                unbindPart(structureName, host, registeredPart);
            }
        }

        for (CompartmentPart currentPart : currentDeclaredParts.values()) {
            if (!host.compartmentHost$getCompartments(structureName).contains(currentPart) ||
                    currentPart.compartmentHost() != host ||
                    !structureName.equals(currentPart.compartmentStructureName()) ||
                    currentPart.compartment$requiresBindingRetry(structureName, host)) {
                currentPart.compartment$bindToHost(structureName, host);
                rememberBinding(structureName, host, currentPart);
            } else {
                rememberBinding(structureName, host, currentPart);
            }
        }
    }

    @Override
    public void unbind(String structureName, CompartmentHost host) {
        ReferenceOpenHashSet<CompartmentPart> partsToUnbind = new ReferenceOpenHashSet<>();
        for (CompartmentPart part : new ObjectArrayList<>(host.compartmentHost$getCompartments(structureName))) {
            partsToUnbind.add(part);
        }
        Object2ObjectMap<String, Reference2ReferenceMap<CompartmentPart, CompartmentBindingHandle>> hostBindings = this.bindingHandles.get(host);
        if (hostBindings != null) {
            Reference2ReferenceMap<CompartmentPart, CompartmentBindingHandle> structureBindings = hostBindings.get(structureName);
            if (structureBindings != null) {
                for (CompartmentPart part : new ObjectArrayList<>(structureBindings.keySet())) {
                    partsToUnbind.add(part);
                }
            }
        }
        for (CompartmentPart part : partsToUnbind) {
            unbindPart(structureName, host, part);
        }
    }

    @Nullable
    private static PatternDiagnostic validateDeclaredPart(StructureWorldView world,
                                                          BlockPos pos,
                                                          CompartmentType declaredType) {
        BlockEntity blockEntity = world.getBlockEntity(pos);
        if (!(blockEntity instanceof CompartmentPart part)) {
            String message = "JSON multiblock declared a compartment at " + pos +
                    " but no compartment part exists";
            LOGGER.warn(message);
            return PatternDiagnostic.of("compartment_part_missing", message, pos, ObjectList.of(declaredType.id()));
        }
        if (part.compartmentType() != declaredType) {
            String message = "JSON multiblock declared compartment type " + declaredType.id() + " at " + pos +
                    " but found " + part.compartmentType().id();
            LOGGER.warn(message);
            return PatternDiagnostic.of("compartment_part_mismatch", message, pos, ObjectList.of(declaredType.id()));
        }
        return null;
    }

    private void bindDeclaredPart(StructureWorldView world,
                                  String structureName,
                                  CompartmentHost host,
                                  BlockPos pos,
                                  CompartmentType declaredType) {
        CompartmentPart part = requireDeclaredPart(world, pos, declaredType);
        part.compartment$bindToHost(structureName, host);
        rememberBinding(structureName, host, part);
    }

    private void rememberBinding(String structureName, CompartmentHost host, CompartmentPart part) {
        if (!part.isCompartmentBound() || part.compartmentHost() != host ||
                !structureName.equals(part.compartmentStructureName())) {
            return;
        }
        CompartmentBindingHandle bindingHandle = part.compartment$bindingHandle();
        if (bindingHandle == null) {
            discardBinding(structureName, host, part);
            return;
        }
        this.bindingHandles.computeIfAbsent(host, ignored -> new Object2ObjectLinkedOpenHashMap<>())
                .computeIfAbsent(structureName, ignored -> new Reference2ReferenceOpenHashMap<>())
                .put(part, bindingHandle);
    }

    private void unbindPart(String structureName, CompartmentHost host, CompartmentPart part) {
        CompartmentBindingHandle bindingHandle = bindingForUnbind(structureName, host, part);
        if (bindingHandle != null) {
            part.compartment$unbindFromHost(bindingHandle);
            rememberBinding(structureName, host, part);
            discardBinding(structureName, host, part, bindingHandle);
            return;
        }
        part.compartment$unbindFromHost(structureName, host);
    }

    @Nullable
    private CompartmentBindingHandle bindingForUnbind(String structureName, CompartmentHost host, CompartmentPart part) {
        Object2ObjectMap<String, Reference2ReferenceMap<CompartmentPart, CompartmentBindingHandle>> hostBindings = this.bindingHandles.get(host);
        if (hostBindings == null) {
            return null;
        }
        Reference2ReferenceMap<CompartmentPart, CompartmentBindingHandle> structureBindings = hostBindings.get(structureName);
        if (structureBindings == null) {
            return null;
        }
        return structureBindings.get(part);
    }

    private void discardBinding(String structureName, CompartmentHost host, CompartmentPart part) {
        discardBinding(structureName, host, part, null);
    }

    private void discardBinding(String structureName,
                                CompartmentHost host,
                                CompartmentPart part,
                                @Nullable CompartmentBindingHandle expectedBindingHandle) {
        Object2ObjectMap<String, Reference2ReferenceMap<CompartmentPart, CompartmentBindingHandle>> hostBindings = this.bindingHandles.get(host);
        if (hostBindings == null) {
            return;
        }
        Reference2ReferenceMap<CompartmentPart, CompartmentBindingHandle> structureBindings = hostBindings.get(structureName);
        if (structureBindings == null) {
            return;
        }
        if (expectedBindingHandle != null && structureBindings.get(part) != expectedBindingHandle) {
            return;
        }
        structureBindings.remove(part);
        discardEmptyBindings(host, structureName, hostBindings, structureBindings);
    }

    private void discardEmptyBindings(CompartmentHost host,
                                      String structureName,
                                      Object2ObjectMap<String, Reference2ReferenceMap<CompartmentPart, CompartmentBindingHandle>> hostBindings,
                                      Reference2ReferenceMap<CompartmentPart, CompartmentBindingHandle> structureBindings) {
        if (!structureBindings.isEmpty()) {
            return;
        }
        hostBindings.remove(structureName);
        if (hostBindings.isEmpty()) {
            this.bindingHandles.remove(host);
        }
    }

    private static CompartmentPart requireDeclaredPart(StructureWorldView world,
                                                       BlockPos pos,
                                                       CompartmentType declaredType) {
        BlockEntity blockEntity = world.getBlockEntity(pos);
        if (!(blockEntity instanceof CompartmentPart part)) {
            throw failDeclaredPart("JSON multiblock declared a compartment at " + pos +
                    " but no compartment part exists");
        }
        if (part.compartmentType() != declaredType) {
            throw failDeclaredPart("JSON multiblock declared compartment type " + declaredType.id() + " at " + pos +
                    " but found " + part.compartmentType().id());
        }
        return part;
    }

    private static IllegalStateException failDeclaredPart(String message) {
        LOGGER.error(message);
        return new IllegalStateException(message);
    }

    private static BlockPos toBlockPos(VerticalMultiBlockPos pos) {
        return new BlockPos(pos.x(), pos.y(), pos.z());
    }

    private static long toLong(VerticalMultiBlockPos pos) {
        return toBlockPos(pos).asLong();
    }
}
