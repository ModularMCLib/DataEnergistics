package com.fish_dan_.data_energistics.common.research.progression;

import com.fish_dan_.data_energistics.api.research.ResearchInput;
import com.fish_dan_.data_energistics.common.research.persistence.ResearchSavedData;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;

import it.unimi.dsi.fastutil.objects.Object2LongLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongMap;

/** Multi-key payment journal; extracted amounts remain recoverable until the project is published. */
final class ResearchPayment {

    private ResearchPayment() {}

    static boolean withdraw(Iterable<ResearchInput> inputs, MEStorage storage, IActionSource source,
                            ResearchStationState state, ResearchSavedData data) {
        Object2LongMap<AEKey> costs = new Object2LongLinkedOpenHashMap<>();
        for (ResearchInput input : inputs) costs.mergeLong(input.key(), input.amount(), Math::addExact);
        for (var entry : costs.object2LongEntrySet()) {
            if (storage.extract(entry.getKey(), entry.getLongValue(), Actionable.SIMULATE, source) != entry.getLongValue()) return false;
        }
        for (var entry : costs.object2LongEntrySet()) {
            long extracted = storage.extract(entry.getKey(), entry.getLongValue(), Actionable.MODULATE, source);
            if (extracted > 0) {
                state.refunds.put(entry.getKey(), extracted);
                data.setDirty();
            }
            if (extracted != entry.getLongValue()) {
                refund(storage, source, state, data);
                return false;
            }
        }
        return true;
    }

    static void refund(MEStorage storage, IActionSource source, ResearchStationState state, ResearchSavedData data) {
        var iterator = state.refunds.object2LongEntrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            long returned = storage.insert(entry.getKey(), entry.getLongValue(), Actionable.MODULATE, source);
            if (returned == 0) continue;
            if (returned == entry.getLongValue()) iterator.remove();
            else entry.setValue(entry.getLongValue() - returned);
            data.setDirty();
        }
    }
}
