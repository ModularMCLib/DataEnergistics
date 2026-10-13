package com.fish_dan_.data_energistics.common.multiblock.json.matching;

import com.fish_dan_.data_energistics.block.storage.CompartmentBlock;
import com.fish_dan_.data_energistics.common.compartment.CompartmentType;
import com.fish_dan_.data_energistics.common.multiblock.json.definition.JsonMultiBlockDefinition;

import net.minecraft.world.level.block.state.BlockState;

import it.unimi.dsi.fastutil.objects.ObjectSet;
import it.unimi.dsi.fastutil.objects.ObjectSets;

import java.util.Optional;

/**
 * Validates block states for JSON symbols declared as compartment positions.
 */
public final class JsonMultiBlockCompartmentValidator {

    private JsonMultiBlockCompartmentValidator() {}

    /**
     * Returns the compartment type declared for a pattern symbol.
     */
    public static Optional<CompartmentType> declaredType(JsonMultiBlockDefinition definition, String symbol) {
        if (symbol.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(definition.compartmentTypes().get(symbol));
    }

    /**
     * Returns compartment roles that may replace a normal block symbol.
     */
    public static ObjectSet<CompartmentType> replaceableTypes(JsonMultiBlockDefinition definition, String symbol) {
        if (symbol.isBlank()) {
            return ObjectSets.emptySet();
        }
        return definition.replaceableCompartmentTypes().getOrDefault(symbol, ObjectSets.emptySet());
    }

    /**
     * Validates that a block state satisfies the compartment type declared for a pattern symbol.
     *
     * <p>
     * Symbols without compartment metadata are accepted. Declared symbols require an actual
     * {@link CompartmentBlock} with the exact matching role.
     */
    public static boolean matchesDeclaredType(JsonMultiBlockDefinition definition, String symbol, BlockState state) {
        Optional<CompartmentType> declared = declaredType(definition, symbol);
        if (declared.isEmpty()) {
            ObjectSet<CompartmentType> replaceable = replaceableTypes(definition, symbol);
            if (replaceable.isEmpty() || !(state.getBlock() instanceof CompartmentBlock block)) {
                return true;
            }
            return replaceable.contains(block.compartmentType());
        }
        if (!(state.getBlock() instanceof CompartmentBlock block)) {
            return false;
        }
        return block.compartmentType() == declared.orElseThrow();
    }
}
