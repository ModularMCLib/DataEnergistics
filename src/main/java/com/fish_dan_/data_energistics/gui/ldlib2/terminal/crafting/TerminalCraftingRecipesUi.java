package com.fish_dan_.data_energistics.gui.ldlib2.terminal.crafting;

import com.fish_dan_.data_energistics.gui.ldlib2.terminal.TerminalResourceCell;
import com.fish_dan_.data_energistics.gui.ldlib2.terminal.TerminalUiLayout;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Scroller;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;

import appeng.api.stacks.GenericStack;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import org.jspecify.annotations.NullMarked;

import java.util.function.BiConsumer;

/** A fixed-size vertical recipe picker; scrolling only rebinds its three visible result cells. */
@NullMarked
public final class TerminalCraftingRecipesUi {

    private static final int ROWS = 3;
    private static final int CELL_SIZE = 18;
    private static final int HEIGHT = ROWS * CELL_SIZE;
    private final UIElement root;
    private final Scroller.Vertical scrollbar;
    private final HolderLookup.Provider registries;
    private final ObjectList<TerminalResourceCell> cells = new ObjectArrayList<>();
    private final ObjectList<Choice> choices = new ObjectArrayList<>();
    private ListTag previousRecipes = new ListTag();
    private String selected = "";
    private int first;

    public TerminalCraftingRecipesUi(UIElement root, HolderLookup.Provider registries, BiConsumer<String, CompoundTag> request) {
        this.root = root;
        this.registries = registries;
        UIElement list = TerminalUiLayout.require(root, "crafting_recipes", UIElement.class);
        for (int index = 0; index < ROWS; index++) {
            int row = index;
            TerminalResourceCell cell = TerminalUiLayout.at(new TerminalResourceCell(), 0, index * CELL_SIZE, CELL_SIZE, CELL_SIZE);
            cell.addClass("terminal-crafting-choice");
            cell.onResourceClick(event -> {
                if (event.button == 0 && first + row < choices.size()) {
                    CompoundTag data = new CompoundTag();
                    data.putString("id", choices.get(first + row).id());
                    request.accept("crafting_recipe", data);
                }
            });
            cells.add(cell);
            list.addChild(cell);
        }
        scrollbar = TerminalUiLayout.require(root, "crafting_recipe_scroll", Scroller.Vertical.class);
        scrollbar.setRange(0, 1);
        scrollbar.setScrollBarSize(1500F / HEIGHT);
        scrollbar.setOnValueChanged(value -> scroll(Math.round(value * maximumFirst()), false));
        root.addEventListener(UIEvents.MOUSE_WHEEL, event -> {
            scroll(first + (event.deltaY > 0 ? -1 : 1), true);
            event.stopPropagation();
        });
        root.setVisible(false);
    }

    public static UIElement create() {
        UIElement root = TerminalUiLayout.at(new UIElement(), 98, 1, 25, HEIGHT);
        root.setId("crafting_choices");
        UIElement list = TerminalUiLayout.at(new UIElement(), 0, 0, CELL_SIZE, HEIGHT);
        list.setId("crafting_recipes");
        list.setOverflowVisible(false);
        root.addChildren(list, TerminalUiLayout.at(TerminalUiLayout.recipeScrollbar("crafting_recipe_scroll"), 19, 0, 6, HEIGHT));
        return root;
    }

    public void apply(CompoundTag snapshot) {
        ListTag entries = snapshot.getList("crafting_recipes", Tag.TAG_COMPOUND);
        boolean changed = !entries.equals(previousRecipes);
        if (changed) {
            previousRecipes = entries.copy();
            choices.clear();
            for (int index = 0; index < entries.size(); index++) {
                CompoundTag entry = entries.getCompound(index);
                choices.add(new Choice(entry.getString("id"), ItemStack.parseOptional(registries, entry.getCompound("output"))));
            }
        }
        String selection = snapshot.getString("crafting_recipe");
        if (changed || !selected.equals(selection)) {
            selected = selection;
            for (int index = 0; index < choices.size(); index++) {
                if (!choices.get(index).id().equals(selected)) continue;
                if (index < first) first = index;
                else if (index >= first + ROWS) first = index - ROWS + 1;
                break;
            }
        }
        root.setVisible(choices.size() > 1);
        scroll(first, true);
    }

    private int maximumFirst() {
        return Math.max(0, choices.size() - ROWS);
    }

    private void scroll(int row, boolean updateScrollbar) {
        int maximum = maximumFirst();
        first = Math.clamp(row, 0, maximum);
        scrollbar.setActive(maximum > 0);
        if (maximum == 0) scrollbar.addClass("inactive-scroll");
        else scrollbar.removeClass("inactive-scroll");
        scrollbar.scrollerStyle(style -> style.scrollDelta(maximum == 0 ? 1F : 1F / maximum));
        if (updateScrollbar) scrollbar.setNormalizedValue(maximum == 0 ? 0 : (float) first / maximum, false);
        for (int index = 0; index < cells.size(); index++) {
            TerminalResourceCell cell = cells.get(index);
            boolean present = first + index < choices.size();
            cell.setVisible(present);
            cell.setActive(present);
            cell.entry(present ? GenericStack.fromItemStack(choices.get(first + index).output()) : null, false);
            if (present && choices.get(first + index).id().equals(selected)) cell.addClass("selected-recipe");
            else cell.removeClass("selected-recipe");
        }
    }

    private record Choice(String id, ItemStack output) {}
}
