package com.fish_dan_.data_energistics.client.screen.ioport;

import com.fish_dan_.data_energistics.menu.ioport.DataIoPortMenu;
import com.fish_dan_.data_energistics.client.widget.ioport.DataIoPortUpgradePanel;

import appeng.api.config.FullnessMode;
import appeng.api.config.OperationMode;
import appeng.api.config.RedstoneMode;
import appeng.api.config.Settings;
import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.ServerSettingToggleButton;
import appeng.client.gui.widgets.ToolboxPanel;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.menu.SlotSemantics;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

/** Client screen for the data IO port. */
public final class DataIoPortScreen extends AEBaseScreen<DataIoPortMenu> {

    private final ServerSettingToggleButton<FullnessMode> fullnessMode;
    private final ServerSettingToggleButton<OperationMode> operationMode;
    private final ServerSettingToggleButton<RedstoneMode> redstoneMode;

    public DataIoPortScreen(DataIoPortMenu menu, Inventory playerInventory, Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);
        var upgrades = menu.getSlots(SlotSemantics.UPGRADE);
        widgets.add("upgrades", new DataIoPortUpgradePanel(new ObjectArrayList<>(upgrades), menu.getHost()));
        if (menu.getToolbox().isPresent()) {
            widgets.add("toolbox", new ToolboxPanel(style, menu.getToolbox().getName()));
        }
        fullnessMode = new ServerSettingToggleButton<>(Settings.FULLNESS_MODE, FullnessMode.EMPTY);
        operationMode = new ServerSettingToggleButton<>(Settings.OPERATION_MODE, OperationMode.EMPTY);
        redstoneMode = new ServerSettingToggleButton<>(Settings.REDSTONE_CONTROLLED, RedstoneMode.IGNORE);
        addToLeftToolbar(fullnessMode);
        addToLeftToolbar(redstoneMode);
        widgets.add("operationMode", operationMode);
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        fullnessMode.set(menu.fullnessMode);
        operationMode.set(menu.operationMode);
        redstoneMode.set(menu.getRedStoneMode());
        redstoneMode.setVisibility(menu.hasUpgrade(AEItems.REDSTONE_CARD));
    }

    @Override
    public void drawBG(GuiGraphics graphics, int offsetX, int offsetY, int mouseX, int mouseY, float partialTicks) {
        super.drawBG(graphics, offsetX, offsetY, mouseX, mouseY, partialTicks);
        drawItem(graphics, offsetX + 58, offsetY + 17, AEItems.ITEM_CELL_1K.stack());
        drawItem(graphics, offsetX + 102, offsetY + 17, AEBlocks.DRIVE.stack());
    }
}
