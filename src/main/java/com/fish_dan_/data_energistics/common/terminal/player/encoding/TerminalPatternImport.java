package com.fish_dan_.data_energistics.common.terminal.player.encoding;

import com.fish_dan_.data_energistics.common.crafting.pattern.EncodedPatternRecipeReference;
import com.fish_dan_.data_energistics.common.crafting.pattern.matching.EncodedPatternMatching;
import com.fish_dan_.data_energistics.common.terminal.player.connection.TerminalConnection;
import com.fish_dan_.data_energistics.common.terminal.player.persistence.TerminalPlayerState;
import com.fish_dan_.data_energistics.common.terminal.player.workstation.TerminalWorkspace;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.helpers.IPatternTerminalLogicHost;
import appeng.parts.encoding.PatternEncodingLogic;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/** Uses AE's pattern inventory callback to restore the same sparse recipe and substitution settings as its terminal. */
@NullMarked
public final class TerminalPatternImport implements IPatternTerminalLogicHost {

    private final ServerPlayer player;
    private final TerminalPlayerState state;
    private final PatternEncodingLogic logic = new PatternEncodingLogic(this);

    private TerminalPatternImport(ServerPlayer player, TerminalPlayerState state) {
        this.player = player;
        this.state = state;
    }

    public static String apply(ServerPlayer player, TerminalPlayerState state, ItemStack pattern,
                               @Nullable TerminalConnection connection, boolean toNetwork) {
        var reader = new TerminalPatternImport(player, state);
        reader.logic.getEncodedPatternInv().setItemDirect(0, pattern.copyWithCount(1));
        TerminalWorkspace page = switch (reader.logic.getMode()) {
            case CRAFTING -> TerminalWorkspace.CRAFTING;
            case PROCESSING -> TerminalWorkspace.PROCESSING;
            case SMITHING_TABLE -> TerminalWorkspace.SMITHING;
            case STONECUTTING -> TerminalWorkspace.STONECUTTING;
        };
        if (page != state.workspace() && !state.work(page).patterns.isEmpty()) return "inventory_full";
        CompoundTag data = new CompoundTag();
        data.putString("workspace", page.id());
        ListTag inputs = new ListTag();
        for (int index = 0; index < page.patternInputSlots(); index++)
            inputs.add(GenericStack.writeTag(player.registryAccess(), reader.logic.getEncodedInputInv().getStack(index)));
        ListTag outputs = new ListTag();
        for (int index = 0; index < page.patternOutputSlots(); index++)
            outputs.add(GenericStack.writeTag(player.registryAccess(), reader.logic.getEncodedOutputInv().getStack(index)));
        data.put("inputs", inputs);
        data.put("outputs", outputs);
        var reference = EncodedPatternRecipeReference.get(pattern);
        data.put("context", new TerminalPatternContext(reference == null ? null : reference.recipeTypeId(),
                reader.logic.getStonecuttingRecipeId() != null ? reader.logic.getStonecuttingRecipeId() :
                        EncodedPatternRecipeReference.getProcessingRecipeId(pattern))
                .save());
        String result = TerminalRecipeTransfer.apply(player, state, data, connection, toNetwork);
        if (!result.isEmpty()) return result;
        state.work().substitutes(reader.logic.isSubstitution());
        state.work().fluidSubstitutes(reader.logic.isFluidSubstitution());
        var matching = new CompoundTag();
        AEItemKey key = AEItemKey.of(pattern);
        matching.putString("inputs", EncodedPatternMatching.modes(key));
        matching.putString("outputs", EncodedPatternMatching.modes(key, true));
        state.work().matching.load(matching);
        state.changed();
        return "";
    }

    @Override
    public PatternEncodingLogic getLogic() {
        return logic;
    }

    @Override
    public Level getLevel() {
        return player.level();
    }

    @Override
    public void markForSave() {
        state.changed();
    }
}
