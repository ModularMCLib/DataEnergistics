package com.fish_dan_.data_energistics.client.screen.machine;

import com.fish_dan_.data_energistics.client.screen.sanctum.InterfaceSlotSettingsScreen;
import com.fish_dan_.data_energistics.client.widget.OutputSideActionButton;
import com.fish_dan_.data_energistics.menu.sanctum.DataSanctumLargeInterfaceMenu;

import appeng.api.config.FuzzyMode;
import appeng.api.config.Settings;
import appeng.api.stacks.AmountFormat;
import appeng.api.stacks.GenericStack;
import appeng.client.gui.Icon;
import appeng.client.gui.implementations.UpgradeableScreen;
import appeng.client.gui.me.common.StackSizeRenderer;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.IconButton;
import appeng.client.gui.widgets.ServerSettingToggleButton;
import appeng.client.gui.widgets.SettingToggleButton;
import appeng.client.gui.widgets.ToggleButton;
import appeng.core.definitions.AEItems;
import appeng.core.localization.ButtonToolTips;
import appeng.menu.slot.AppEngSlot;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import java.util.ArrayList;
import java.util.List;

public class DataSanctumLargeInterfaceScreen extends UpgradeableScreen<DataSanctumLargeInterfaceMenu> {

    private final SettingToggleButton<FuzzyMode> fuzzyMode;
    private final ToggleButton previousPageButton;
    private final ToggleButton nextPageButton;
    private final OutputSideActionButton activePullToggleButton;
    private final OutputSideActionButton activePullConfigButton;
    private final List<Button> amountButtons = new ArrayList<>();

    public DataSanctumLargeInterfaceScreen(DataSanctumLargeInterfaceMenu menu, Inventory playerInventory, Component title,
                                           ScreenStyle style) {
        super(menu, playerInventory, title, style);

        this.fuzzyMode = new ServerSettingToggleButton<>(Settings.FUZZY_MODE, FuzzyMode.IGNORE_ALL);
        addToLeftToolbar(this.fuzzyMode);

        widgets.addOpenPriorityButton();

        this.previousPageButton = new ToggleButton(
                Icon.BACK,
                Icon.BACK,
                Component.translatable("screen.data_energistics.page.previous"),
                Component.translatable("screen.data_energistics.page.previous"),
                ignored -> this.menu.sendSetPage(this.menu.pageIndex - 1));
        this.nextPageButton = new ToggleButton(
                Icon.ARROW_RIGHT,
                Icon.ARROW_RIGHT,
                Component.translatable("screen.data_energistics.page.next"),
                Component.translatable("screen.data_energistics.page.next"),
                ignored -> this.menu.sendSetPage(this.menu.pageIndex + 1));
        addToLeftToolbar(this.previousPageButton);
        addToLeftToolbar(this.nextPageButton);

        this.activePullToggleButton = new OutputSideActionButton(button -> toggleActivePull());
        this.addToLeftToolbar(this.activePullToggleButton);

        this.activePullConfigButton = new OutputSideActionButton(
                button -> openActivePullConfig(),
                "gui.data_energistics.set_active_pull_sides.open");
        this.addToLeftToolbar(this.activePullConfigButton);

        for (int i = 0; i < menu.getConfigSlots().size(); i++) {
            var button = new SetAmountButton(btn -> {
                int index = amountButtons.indexOf(btn);
                if (index >= 0 && index < this.menu.getConfigSlots().size()) {
                    this.switchToScreen(new InterfaceSlotSettingsScreen(this, index));
                }
            });
            button.setDisableBackground(true);
            button.setMessage(ButtonToolTips.InterfaceSetStockAmount.text());
            widgets.add("amtButton" + (i + 1), button);
            amountButtons.add(button);
        }
    }

    private void toggleActivePull() {
        if (this.menu.getHost() == null) {
            return;
        }

        List<Direction> activePullSides = this.menu.getActivePullSides();
        if (activePullSides.isEmpty()) {
            this.menu.sendSetActivePullSide(this.menu.getHost().getDefaultActivePullSide(), true);
            return;
        }

        for (Direction side : activePullSides) {
            this.menu.sendSetActivePullSide(side, false);
        }
    }

    private void openActivePullConfig() {
        if (this.menu.getHost() == null || !this.menu.getHost().hasActivePullSideSelection()) {
            return;
        }

        if (this.menu.getActivePullSides().isEmpty()) {
            return;
        }

        this.switchToScreen(new DataSanctumActivePullSideScreen(
                this,
                this.menu.getHost(),
                this.menu.getActivePullSides(),
                this.menu::sendSetActivePullSide));
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        this.fuzzyMode.set(menu.getFuzzyMode());
        this.fuzzyMode.setVisibility(menu.hasUpgrade(AEItems.FUZZY_CARD));

        boolean multiplePages = this.menu.totalPages > 1;
        this.previousPageButton.visible = multiplePages;
        this.nextPageButton.visible = multiplePages;
        this.previousPageButton.active = multiplePages && this.menu.pageIndex > 0;
        this.nextPageButton.active = multiplePages && this.menu.pageIndex + 1 < this.menu.totalPages;
        boolean activePullEnabled = !this.menu.getActivePullSides().isEmpty();
        this.activePullToggleButton.setIconName(activePullEnabled ? "POWER_UNIT_YES" : "POWER_UNIT_NO");
        this.activePullToggleButton.setMessageKey(activePullEnabled ? "gui.data_energistics.set_active_pull_sides.enable" : "gui.data_energistics.set_active_pull_sides.disable");
        this.activePullConfigButton.visible = activePullEnabled && this.menu.getHost() != null && this.menu.getHost().hasActivePullSideSelection();
        setTextContent("page_info", Component.translatable(
                "screen.data_energistics.page",
                this.menu.pageIndex + 1,
                this.menu.totalPages));

        var configSlots = this.menu.getConfigSlots();
        for (int i = 0; i < this.amountButtons.size(); i++) {
            this.amountButtons.get(i).visible = i < configSlots.size() && !configSlots.get(i).getItem().isEmpty();
        }
    }

    @Override
    public void renderSlot(GuiGraphics guiGraphics, Slot slot) {
        int configIndex = this.menu.getConfigSlots().indexOf(slot);
        boolean unlimited = configIndex >= 0 && this.menu.isUnlimitedConfigSlot(configIndex);
        var stack = GenericStack.unwrapItemStack(slot.getItem());
        if (slot instanceof AppEngSlot aeSlot && stack != null) {
            // Draw amounts after the model, instead of inside AE2's wrapped-item render hook.
            aeSlot.setHideAmount(true);
        }
        super.renderSlot(guiGraphics, slot);
        if (stack != null && (unlimited || stack.amount() > 0)) {
            guiGraphics.flush();
            guiGraphics.pose().pushPose();
            // Restore vanilla's slot offset; the renderer adds 200 for labels, below tooltips and carried items.
            guiGraphics.pose().translate(0, 0, 100);
            String label = unlimited ? "∞" : stack.what().formatAmount(stack.amount(), AmountFormat.SLOT);
            StackSizeRenderer.renderSizeLabel(guiGraphics, this.font, slot.x, slot.y, label, false);
            guiGraphics.pose().popPose();
        }
    }

    private static class SetAmountButton extends IconButton {

        private SetAmountButton(OnPress onPress) {
            super(onPress);
        }

        @Override
        protected Icon getIcon() {
            return Icon.COG;
        }
    }
}
