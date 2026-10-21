package com.fish_dan_.data_energistics.integration.viewer.emi.ingredient;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.data.DEAE2Keys;
import com.fish_dan_.data_energistics.integration.viewer.xei.ingredient.DataResourceKey;

import appeng.api.integrations.emi.EmiStackConverter;
import appeng.api.stacks.GenericStack;
import appeng.items.misc.WrappedGenericStack;

import dev.emi.emi.api.stack.EmiStack;
import org.jspecify.annotations.Nullable;

/**
 * Converts Data Energistics-owned viewer identities and preserves optional integration keys through AE2's wrapped
 * generic-stack tooltip path.
 */
public final class DataResourceEmiStackConverter implements EmiStackConverter {

    public static final DataResourceEmiStackConverter INSTANCE = new DataResourceEmiStackConverter();

    private DataResourceEmiStackConverter() {}

    @Override
    public Class<?> getKeyType() {
        return DataResourceKey.class;
    }

    @Override
    public @Nullable EmiStack toEmiStack(GenericStack stack) {
        if (stack.amount() < 0L) {
            String message = "Cannot convert a negative AE2 stack amount to EMI: " + stack.amount();
            Data_Energistics.LOGGER.error(message);
            throw new IllegalArgumentException(message);
        }
        long amount = stack.amount() == 0L ? 1L : stack.amount();
        DataResourceKey key = DataResourceKey.fromAeKey(stack.what());
        if (key == null) {
            if (!DEAE2Keys.isCustomKey(stack.what())) {
                return null;
            }
            return EmiStack.of(WrappedGenericStack.wrap(new GenericStack(stack.what(), amount)));
        }
        return new DataResourceEmiStack(key, amount);
    }

    @Override
    public @Nullable GenericStack toGenericStack(EmiStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        DataResourceKey key = stack.getKeyOfType(DataResourceKey.class);
        if (key != null) {
            return new GenericStack(key.aeKey(), stack.getAmount());
        }
        if (stack.getItemStack().isEmpty()) {
            return null;
        }
        GenericStack wrapped = GenericStack.unwrapItemStack(stack.getItemStack());
        if (wrapped == null || !DEAE2Keys.isCustomKey(wrapped.what())) {
            return null;
        }
        return wrapped;
    }
}
