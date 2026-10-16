package com.fish_dan_.data_energistics.gui.ldlib2.anvil;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.anvil.DigitalAnvilUpgrade;
import com.fish_dan_.data_energistics.gui.ldlib2.style.SharedBackpackStyles;
import com.fish_dan_.data_energistics.gui.ldlib2.style.StateTextureButton;
import com.fish_dan_.data_energistics.menu.machine.anvil.MeDigitalAnvilMenu;

import com.lowdragmc.lowdraglib2.gui.holder.IModularUIHolderMenu;
import com.lowdragmc.lowdraglib2.gui.texture.ColorBorderTexture;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.elements.inventory.InventorySlots;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.style.Stylesheet;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import dev.vfyjxf.taffy.style.TaffyPosition;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;

/** Compact native LDLib2 composition based on the supplied anvil layout and shared player inventory. */
public final class MeDigitalAnvilUi {

    private static final int WIDTH = 178;
    private static final int HEIGHT = 180;
    private static final int TEXT = 0xFF343548;
    private final MeDigitalAnvilMenu menu;
    private final UIElement root = new UIElement();
    private final TextField name = new TextField();
    private final Label status = label(Component.empty());
    private final UIElement errorOverlay = new UIElement();
    private final UIElement icon = new UIElement();
    private final Button up;
    private final Button down;
    private ItemStack previousLeft = ItemStack.EMPTY;
    private CompoundTag previousSnapshot = new CompoundTag();

    private MeDigitalAnvilUi(MeDigitalAnvilMenu menu) {
        this.menu = menu;
        root.setId("me_digital_anvil");
        root.layout(layout -> layout.width(WIDTH).height(HEIGHT));
        root.style(style -> style.backgroundTexture(SpriteTexture.of(Data_Energistics.id("textures/guis/digital_panel.png"))));
        at(icon, 17, 8, 30, 30);
        icon.style(style -> style.backgroundTexture(texture("top_surface/hammer")));
        root.addChild(icon);
        UIElement workArea = at(new UIElement(), 50, 25, 108, 58);
        workArea.style(style -> style.backgroundTexture(texture("background/small_anvil")));
        root.addChild(workArea);
        name.setAnyString();
        name.setTextValidator(value -> value.length() <= 50);
        at(name, 3, 5, 96, 10);
        name.layout(layout -> layout.paddingAll(1));
        name.style(style -> style.backgroundTexture(texture("small_top_surface/small_text_box_0")));
        name.textFieldStyle(style -> style.fontSize(8).textColor(TEXT).textShadow(false)
                .focusOverlay(new ColorBorderTexture(-1, 0xFF343548)));
        name.setTextResponder(value -> {
            if (!menu.isServerSide() && value.length() <= 50) menu.request("rename", value);
        });
        workArea.addChild(name);
        ItemSlot left = new ItemSlot(menu.leftSlot());
        ItemSlot right = new ItemSlot(menu.rightSlot());
        ItemSlot output = new ItemSlot(menu.outputSlot());
        for (ItemSlot slot : new ItemSlot[] { left, right, output }) {
            slot.style(style -> style.backgroundTexture(IGuiTexture.EMPTY));
        }
        at(left, 3, 20, 18, 18);
        at(right, 39, 20, 18, 18);
        at(output, 78, 20, 18, 18);
        workArea.addChildren(left, right, output);
        at(errorOverlay, 60, 21, 16, 16);
        errorOverlay.style(style -> style.zIndex(1).backgroundTexture(texture("top_surface/prohibit")));
        errorOverlay.setAllowHitTest(false);
        errorOverlay.setVisible(false);
        workArea.addChild(errorOverlay);
        up = step("up", () -> menu.request("boost_up", ""));
        down = step("down", () -> menu.request("boost_down", ""));
        root.addChildren(at(up, 159, 46, 10, 10), at(down, 159, 58, 10, 10));
        at(status, 3, 43, 96, 10);
        status.style(style -> style.backgroundTexture(texture("small_top_surface/small_text_box_0")));
        status.textStyle(style -> style.fontSize(8).textWrap(TextWrap.HOVER_ROLL));
        status.setAllowHitTest(true);
        workArea.addChild(status);
        InventorySlots inventory = new InventorySlots();
        inventory.setId("me_digital_anvil_inventory");
        SharedBackpackStyles.inventory(inventory);
        root.addChild(at(inventory, 8, 95, 162, 76));
        root.addEventListener(UIEvents.TICK, event -> {
            if (!menu.isServerSide()) refresh();
        });
    }

    public static ModularUI mount(MeDigitalAnvilMenu menu) {
        IModularUIHolderMenu holder = (IModularUIHolderMenu) (Object) menu;
        if (holder.getModularUI() != null || !menu.slots.isEmpty()) throw new IllegalStateException("Digital anvil UI is already mounted");
        MeDigitalAnvilUi composition = new MeDigitalAnvilUi(menu);
        ObjectList<Stylesheet> styles = new ObjectArrayList<>();
        styles.add(SharedBackpackStyles.stylesheet());
        ModularUI ui = ModularUI.of(UI.of(composition.root, styles), menu.getPlayer());
        holder.setModularUI(ui);
        if (menu.slots.size() != 39 || menu.slots.get(0) != menu.leftSlot() ||
                menu.slots.get(1) != menu.rightSlot() || menu.slots.get(2) != menu.outputSlot()) {
            throw new IllegalStateException("Digital anvil requires three native machine slots and 36 player slots");
        }
        for (int index = 3; index < menu.slots.size(); index++) {
            Slot slot = menu.slots.get(index);
            int expected = index < 30 ? index + 6 : index - 30;
            if (slot.container != menu.getPlayer().getInventory() || slot.getContainerSlot() != expected || holder.getItemSlot(slot) == null) {
                throw new IllegalStateException("Digital anvil player slot mapping diverged at " + index);
            }
        }
        return ui;
    }

    private void refresh() {
        ItemStack left = menu.leftSlot().getItem();
        if (!ItemStack.matches(previousLeft, left)) {
            name.setText(left.isEmpty() ? "" : left.getHoverName().getString(), false);
            previousLeft = left.copy();
        }
        CompoundTag data = menu.snapshot();
        // Missing inputs and unavailable network resources are not an invalid item combination.
        errorOverlay.setVisible(!left.isEmpty() && menu.rightSlot().hasItem() && !menu.outputSlot().hasItem() &&
                !data.getBoolean("valid") && data.getBoolean("online") && !data.getBoolean("faulted") &&
                !data.getBoolean("refund_pending"));
        if (data.equals(previousSnapshot)) return;
        previousSnapshot = data.copy();
        boolean levels = DigitalAnvilUpgrade.UNRESTRICTED_LEVELS.enabled(data.getInt("upgrades"));
        up.setActive(levels && data.getInt("boost") < 254);
        down.setActive(levels && data.getInt("boost") > 0);
        up.style(style -> style.tooltips(text(levels ? "boost_up" : "levels_locked", data.getInt("boost"))));
        down.style(style -> style.tooltips(text(levels ? "boost_down" : "levels_locked", data.getInt("boost"))));
        String state = data.getBoolean("faulted") ? "faulted" : !data.getBoolean("online") ? "offline" :
                data.getBoolean("refund_pending") ? "refund_pending" : !data.getBoolean("valid") ? "invalid" :
                        !data.getBoolean("ready") ? "insufficient" : "ready";
        status.setText(data.getBoolean("valid") ? text("cost", data.getLong("energy"), data.getLong("levels")) : text(state));
        ObjectList<Component> costs = new ObjectArrayList<>();
        costs.add(text(state));
        costs.add(text("energy", data.getLong("energy")));
        costs.add(text("experience", data.getLong("experience"), data.getLong("levels")));
        costs.add(text("data_flow", data.getLong("data_flow")));
        costs.add(text("stellar_flux", data.getLong("stellar_flux")));
        costs.add(text("echo", data.getLong("echo")));
        costs.add(text("boost", data.getInt("effective_boost")));
        status.style(style -> style.tooltips(costs.toArray(Component[]::new)));
        ObjectList<Component> research = new ObjectArrayList<>();
        research.add(Component.translatable("block.data_energistics.me_digital_anvil"));
        research.add(Component.translatable("block.data_energistics.me_digital_anvil.description"));
        for (DigitalAnvilUpgrade upgrade : DigitalAnvilUpgrade.values()) {
            research.add(text(upgrade.enabled(data.getInt("upgrades")) ? "research_unlocked" : "research_locked",
                    Component.translatable(upgrade.translationKey())));
        }
        icon.style(style -> style.tooltips(research.toArray(Component[]::new)));
    }

    private static Button step(String direction, Runnable action) {
        StateTextureButton button = new StateTextureButton();
        button.setArrow(direction.equals("up") ? StateTextureButton.Arrow.UP : StateTextureButton.Arrow.DOWN);
        button.setOnClick(event -> action.run());
        return button;
    }

    private static IGuiTexture texture(String path) {
        return SpriteTexture.of(Data_Energistics.id("textures/guis/states/" + path + ".png"));
    }

    private static Label label(Component text) {
        Label label = new Label();
        label.setText(text);
        label.textStyle(style -> style.fontSize(12).textColor(TEXT).textShadow(false).adaptiveWidth(false)
                .adaptiveHeight(false).textAlignHorizontal(Horizontal.CENTER).textAlignVertical(Vertical.CENTER));
        label.setAllowHitTest(false);
        return label;
    }

    private static Component text(String key, Object... arguments) {
        return Component.translatable("gui.data_energistics.me_digital_anvil." + key, arguments);
    }

    private static <T extends UIElement> T at(T element, float x, float y, float width, float height) {
        element.layout(layout -> layout.positionType(TaffyPosition.ABSOLUTE).left(x).top(y).width(width).height(height));
        return element;
    }
}
