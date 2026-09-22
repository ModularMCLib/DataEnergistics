package com.fish_dan_.data_energistics.menu.patternencoding;

import com.fish_dan_.data_energistics.api.crafting.packaged.PackagedPatternInputCompletion;
import com.fish_dan_.data_energistics.common.entrypoint.DataEnergisticsEntrypointLoader;

import appeng.api.stacks.GenericStack;
import appeng.helpers.IPatternTerminalMenuHost;
import appeng.menu.me.items.PatternEncodingTermMenu;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import it.unimi.dsi.fastutil.objects.ObjectList;

/** Applies registered recipe input completion to server-owned ghost slots. */
public final class PackagedPatternInputTransfer {

    private PackagedPatternInputTransfer() {}

    public static void complete(PatternEncodingTermMenu menu, ResourceLocation type, ResourceLocation recipeId) {
        if (!(menu.getPlayer().level() instanceof ServerLevel level) || !(menu.getHost() instanceof IPatternTerminalMenuHost host)) return;
        var inventory = host.getLogic().getEncodedInputInv();
        ObjectList<GenericStack> inputs = new ObjectArrayList<>();
        for (int slot = 0; slot < inventory.size(); slot++) if (inventory.getStack(slot) != null) inputs.add(inventory.getStack(slot));
        boolean completed = false;
        for (var adapter : DataEnergisticsEntrypointLoader.snapshot().packagedCrafting().forType(type)) {
            if (!(adapter instanceof PackagedPatternInputCompletion completion)) continue;
            var result = completion.completePatternInputs(level, recipeId, new ObjectImmutableList<>(inputs));
            if (result == null || result.size() > inventory.size()) return;
            if (result.stream().anyMatch(stack -> stack.amount() <= 0)) throw new IllegalStateException("Invalid completed pattern input amount");
            inputs = result;
            completed = true;
        }
        if (!completed) return;
        for (int slot = 0; slot < inventory.size(); slot++) inventory.setStack(slot, slot < inputs.size() ? inputs.get(slot) : null);
        menu.broadcastChanges();
    }
}
