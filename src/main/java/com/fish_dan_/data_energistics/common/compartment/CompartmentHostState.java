package com.fish_dan_.data_energistics.common.compartment;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Reusable runtime state for multiblock controllers that accept compartment parts.
 */
public final class CompartmentHostState {

    private final Map<String, List<CompartmentPart>> compartments = new Object2ObjectLinkedOpenHashMap<>();

    public void addCompartment(String structureName, CompartmentPart part) {
        List<CompartmentPart> parts = this.compartments.computeIfAbsent(structureName, ignored -> new ObjectArrayList<>());
        if (!parts.contains(part)) {
            parts.add(part);
        }
    }

    public void removeCompartment(String structureName, CompartmentPart part) {
        List<CompartmentPart> parts = this.compartments.get(structureName);
        if (parts == null) {
            return;
        }
        parts.remove(part);
        if (parts.isEmpty()) {
            this.compartments.remove(structureName);
        }
    }

    public Collection<CompartmentPart> compartments(String structureName) {
        List<CompartmentPart> parts = this.compartments.get(structureName);
        return parts == null ? List.of() : List.copyOf(parts);
    }

    public void clear(String structureName) {
        this.compartments.remove(structureName);
    }

    public void clearAll() {
        this.compartments.clear();
    }
}
