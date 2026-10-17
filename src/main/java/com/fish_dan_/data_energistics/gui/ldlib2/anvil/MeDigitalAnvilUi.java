package com.fish_dan_.data_energistics.gui.ldlib2.anvil;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.anvil.DigitalAnvilUpgrade;
import com.fish_dan_.data_energistics.gui.ldlib2.style.SharedBackpackStyles;
import com.fish_dan_.data_energistics.gui.ldlib2.style.StateTextureButton;
import com.fish_dan_.data_energistics.menu.machine.anvil.MeDigitalAnvilMenu;

import com.lowdragmc.lowdraglib2.gui.holder.IModularUIHolderMenu;
import com.lowdragmc.lowdraglib2.gui.texture.ColorBorderTexture;
import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.gui.texture.GuiTextureGroup;
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
    private static final int OUTLINE = 0xFFF2F2F2;
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
        at(icon, 18, 9, 32, 32);
        icon.style(style -> style.backgroundTexture(texture("top_surface/hammer")));
        root.addChild(icon);
        name.setAnyString();
        name.setTextValidator(value -> value.length() <= 50);
        at(name, 60, 22, 110, 16);
        name.layout(layout -> layout.paddingAll(3));
        name.style(style -> style.backgroundTexture(texture("top_surface/text_box_0")));
        name.textFieldStyle(style -> style.fontSize(8).textColor(TEXT).textShadow(false)
                .focusOverlay(new ColorBorderTexture(-1, 0xFF343548)));
        name.setTextResponder(value -> {
            if (!menu.isServerSide() && value.length() <= 50) menu.request("rename", value);
        });
        root.addChild(name);
        root.addChildren(framedSlot(menu.leftSlot(), 25), framedSlot(menu.rightSlot(), 74), framedSlot(menu.outputSlot(), 132));
        UIElement addition = at(new UIElement(), 54, 51, 13, 13);
        addition.style(style -> style.backgroundTexture(texture("background/addition")));
        UIElement progress = at(new UIElement(), 103, 49, 22, 16);
        progress.style(style -> style.backgroundTexture(texture("background/progress")));
        root.addChildren(addition, progress);
        at(errorOverlay, 104, 50, 16, 16);
        errorOverlay.style(style -> style.zIndex(1).backgroundTexture(texture("top_surface/prohibit")));
        errorOverlay.setAllowHitTest(false);
        errorOverlay.setVisible(false);
        root.addChild(errorOverlay);
        up = step("up", () -> menu.request("boost_up", ""));
        down = step("down", () -> menu.request("boost_down", ""));
        root.addChildren(at(up, 159, 46, 10, 10), at(down, 159, 58, 10, 10));
        UIElement statusFrame = at(new UIElement(), 25, 72, 129, 14);
        statusFrame.style(style -> style.backgroundTexture(new ColorBorderTexture(-1, OUTLINE)));
        UIElement statusSurface = at(new UIElement(), 2, 2, 125, 10);
        statusSurface.style(style -> style.backgroundTexture(GuiTextureGroup.of(
                new ColorRectTexture(0xFF9A9FB4), new ColorBorderTexture(-1, OUTLINE))));
        UIElement statusShadow = at(new UIElement(), 1, 1, 123, 2);
        statusShadow.style(style -> style.backgroundTexture(new ColorRectTexture(0xFF696D88)));
        at(status, 1, 1, 123, 8);
        status.layout(layout -> layout.paddingAll(0));
        status.style(style -> style.backgroundTexture(IGuiTexture.EMPTY));
        status.textStyle(style -> style.fontSize(8).textWrap(TextWrap.HOVER_ROLL));
        status.setAllowHitTest(true);
        statusSurface.addChildren(statusShadow, status);
        statusFrame.addChild(statusSurface);
        root.addChild(statusFrame);
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
        button.setActive(false);
        button.setOnClick(event -> action.run());
        return button;
    }

    private static UIElement framedSlot(Slot machineSlot, int x) {
        UIElement frame = at(new UIElement(), x, 46, 22, 22);
        frame.style(style -> style.backgroundTexture(new ColorBorderTexture(-1, OUTLINE)));
        UIElement rim = at(new UIElement(), 2, 2, 18, 18);
        rim.style(style -> style.backgroundTexture(new ColorBorderTexture(-1, OUTLINE)));
        ItemSlot slot = at(new ItemSlot(machineSlot), 1, 1, 16, 16);
        slot.layout(layout -> layout.paddingAll(0));
        slot.style(style -> style.backgroundTexture(SpriteTexture.of(Data_Energistics.id("textures/guis/inventory_slot.png"))));
        rim.addChild(slot);
        frame.addChild(rim);
        return frame;
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
