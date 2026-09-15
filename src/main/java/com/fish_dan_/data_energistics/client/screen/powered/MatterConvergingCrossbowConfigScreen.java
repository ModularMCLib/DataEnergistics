package com.fish_dan_.data_energistics.client.screen.powered;

import com.fish_dan_.data_energistics.client.registry.DEKeyMappings;
import com.fish_dan_.data_energistics.item.powered.MatterConvergingCrossbowMode;
import com.fish_dan_.data_energistics.item.powered.cannon.presentation.DigitizedWeaponName;
import com.fish_dan_.data_energistics.menu.powered.MatterConvergingCrossbowConfigMenu;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.Icon;
import appeng.client.gui.style.PaletteColor;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.ToggleButton;
import appeng.client.gui.widgets.UpgradesPanel;
import appeng.menu.SlotSemantics;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** All item contents and the active mode come from AE slot synchronization and GuiSync. */
public final class MatterConvergingCrossbowConfigScreen extends AEBaseScreen<MatterConvergingCrossbowConfigMenu> {

    private final Map<MatterConvergingCrossbowMode, ToggleButton> modeButtons = new EnumMap<>(MatterConvergingCrossbowMode.class);
    private final int selectionColor;

    public MatterConvergingCrossbowConfigScreen(MatterConvergingCrossbowConfigMenu menu, Inventory inventory,
                                                Component title, ScreenStyle style) {
        super(menu, inventory, title, style);
        selectionColor = style.getColor(PaletteColor.SELECTION_COLOR).toARGB();
        widgets.add("upgrades", new UpgradesPanel(menu.getSlots(SlotSemantics.UPGRADE), menu.getHost()));
        for (MatterConvergingCrossbowMode mode : MatterConvergingCrossbowMode.values()) {
            ToggleButton button = new ToggleButton(Icon.AUTO_EXPORT_ON, Icon.AUTO_EXPORT_OFF, selected -> menu.sendSetMode(mode));
            button.setDisableBackground(true);
            Component name = DigitizedWeaponName.fullName(mode);
            button.setTooltipOn(List.of(name, Component.translatable("screen.data_energistics.cannon.hint.selected")));
            button.setTooltipOff(List.of(name, Component.translatable("screen.data_energistics.cannon.hint.select_mode")));
            widgets.add("mode_" + mode.nameKey(), button);
            modeButtons.put(mode, button);
        }
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        MatterConvergingCrossbowMode mode = MatterConvergingCrossbowMode.fromId(menu.activeMode);
        setFittedText("dialog_title", DigitizedWeaponName.fullName(mode), 160);
        for (MatterConvergingCrossbowMode row : MatterConvergingCrossbowMode.values()) {
            String rowId = "mode_label_" + row.nameKey();
            setFittedText(rowId + "_prefix", DigitizedWeaponName.prefix(), 34);
            setFittedText(rowId, DigitizedWeaponName.modeName(row), 34);
        }
        modeButtons.forEach((row, button) -> button.setState(row == mode));
    }

    private void setFittedText(String id, Component text, int width) {
        setTextContent(id, text);
        getStyle().getText().get(id).setScale(Math.min(1.0F, (float) width / Math.max(1, font.width(text))));
    }

    @Override
    public void drawBG(GuiGraphics graphics, int offsetX, int offsetY, int mouseX, int mouseY, float partialTicks) {
        super.drawBG(graphics, offsetX, offsetY, mouseX, mouseY, partialTicks);
        for (MatterConvergingCrossbowMode mode : MatterConvergingCrossbowMode.values()) {
            Slot cell = menu.cellSlot(mode);
            int top = offsetY + cell.y - 2;
            boolean selected = mode.id() == menu.activeMode;
            int color = selected ? (selectionColor & 0xFFFFFF) | 0x30000000 : 0x126A707A;
            graphics.fill(offsetX + 30, top, offsetX + 150, top + 20, color);
            if (selected) graphics.fill(offsetX + 27, top, offsetX + 29, top + 20, selectionColor);
        }
    }

    @Override
    public void renderSlot(GuiGraphics graphics, Slot slot) {
        super.renderSlot(graphics, slot);
        MatterConvergingCrossbowMode row = menu.rowOf(slot);
        if (row != null && row.id() != menu.activeMode) {
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 200);
            graphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, 0x486A707A);
            graphics.pose().popPose();
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 || button == 1) {
            double x = mouseX - leftPos;
            double y = mouseY - topPos;
            for (MatterConvergingCrossbowMode mode : MatterConvergingCrossbowMode.values()) {
                Slot ammo = menu.ammoSlot(mode);
                if (x >= ammo.x && x < ammo.x + 16 && y >= ammo.y && y < ammo.y + 16 && menu.getCarried().isEmpty()) {
                    if (mode.id() != menu.activeMode) menu.sendSetMode(mode);
                    else menu.sendCycleAmmo(mode, button == 1);
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (vertical != 0 && menu.getCarried().isEmpty()) {
            MatterConvergingCrossbowMode mode = MatterConvergingCrossbowMode.fromId(menu.activeMode);
            Slot ammo = menu.ammoSlot(mode);
            double x = mouseX - leftPos;
            double y = mouseY - topPos;
            if (x >= ammo.x && x < ammo.x + 16 && y >= ammo.y && y < ammo.y + 16) {
                menu.sendCycleAmmo(mode, vertical < 0);
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    protected List<Component> getTooltipFromContainerItem(ItemStack stack) {
        List<Component> lines = new ObjectArrayList<>(super.getTooltipFromContainerItem(stack));
        if (hoveredSlot != null) {
            MatterConvergingCrossbowMode row = menu.rowOf(hoveredSlot);
            if (row != null) {
                String hint = hoveredSlot == menu.cellSlot(row) ? "cell" : row.id() == menu.activeMode ? "cycle_ammo" : "select_mode";
                lines.add(Component.translatable("screen.data_energistics.cannon.hint." + hint));
                if (row.id() != menu.activeMode) lines.add(Component.translatable("screen.data_energistics.cannon.hint.inactive"));
            }
        }
        return lines;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        MatterConvergingCrossbowMode requested = null;
        if (DEKeyMappings.TOGGLE_CROSSBOW_RAIL.matches(keyCode, scanCode)) requested = MatterConvergingCrossbowMode.RAIL;
        else if (DEKeyMappings.TOGGLE_CROSSBOW_ARMS.matches(keyCode, scanCode)) requested = MatterConvergingCrossbowMode.CROSSBOW;
        if (requested != null) {
            menu.sendSetMode(requested.id() == menu.activeMode ? MatterConvergingCrossbowMode.GRENADE : requested);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
