package com.fish_dan_.data_energistics.client.screen.sanctum;

import com.fish_dan_.data_energistics.api.registry.connector.ConnectorPolicy;
import com.fish_dan_.data_energistics.client.crafting.NumberEntryWidgetValidationRegistry;
import com.fish_dan_.data_energistics.client.screen.machine.DataSanctumLargeInterfaceScreen;
import com.fish_dan_.data_energistics.menu.sanctum.DataSanctumLargeInterfaceMenu;
import com.fish_dan_.data_energistics.mixin.ae.ae2.client.crafting.NumberEntryWidgetAccessor;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.client.gui.AESubScreen;
import appeng.client.gui.Icon;
import appeng.client.gui.NumberEntryType;
import appeng.client.gui.widgets.NumberEntryWidget;
import appeng.client.gui.widgets.TabButton;
import appeng.client.gui.widgets.ToggleButton;
import appeng.core.localization.GuiText;
import appeng.menu.SlotSemantics;
import appeng.menu.slot.InaccessibleSlot;
import appeng.util.inv.AppEngInternalInventory;

import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import org.jspecify.annotations.NullMarked;

import java.util.List;

/** Edits one page/slot/key tuple on the already synchronized parent menu. */
@NullMarked
public final class InterfaceSlotSettingsScreen extends AESubScreen<DataSanctumLargeInterfaceMenu, DataSanctumLargeInterfaceScreen> {

    private final int page;
    private final int slot;
    private final AEKey key;
    private final long finiteAmount;
    private final NumberEntryWidget amount;
    private final Button save;
    private ToggleButton unlimitedButton;
    private ToggleButton policyButton;
    private boolean unlimited;
    private ConnectorPolicy policy;

    public InterfaceSlotSettingsScreen(DataSanctumLargeInterfaceScreen parent, int slot) {
        super(parent, "/screens/set_stock_amount.json");
        this.page = menu.pageIndex;
        this.slot = slot;
        GenericStack configured = GenericStack.fromItemStack(menu.getConfigSlots().get(slot).getItem());
        if (configured == null) throw new IllegalArgumentException("Cannot edit an empty interface configuration");
        this.key = configured.what();
        this.finiteAmount = configured.amount();
        this.unlimited = menu.isUnlimitedConfigSlot(slot);
        this.policy = menu.getSlotPolicy(slot);
        this.amount = widgets.addNumberEntryWidget("amountToStock", NumberEntryType.of(key));
        NumberEntryWidgetValidationRegistry.enableStockAmount(this.amount);
        ((NumberEntryWidgetAccessor) this.amount).dataEnergistics$textField().setMaxLength(256);
        this.amount.setMinValue(0);
        this.amount.setMaxValue(Long.MAX_VALUE);
        this.amount.setLongValue(finiteAmount);
        this.amount.setHideValidationIcon(true);
        this.amount.setTextFieldStyle(style.getWidget("amountToStockInput"));
        this.amount.setOnConfirm(this::confirm);
        this.save = widgets.addButton("save", GuiText.Set.text(), this::confirm);
        widgets.add("back", new TabButton(Icon.BACK, menu.getHost().getMainMenuIcon().getHoverName(), b -> returnToParent()));
        var icon = new AppEngInternalInventory(1);
        icon.setItemDirect(0, key.wrapForDisplayOrFilter());
        addClientSideSlot(new InaccessibleSlot(icon, 0), SlotSemantics.MACHINE_OUTPUT);
        this.unlimitedButton = new ToggleButton(Icon.FILTER_ON_EXTRACT_ENABLED, Icon.FILTER_ON_EXTRACT_DISABLED,
                enabled -> {
                    this.unlimited = enabled;
                    this.unlimitedButton.setState(enabled);
                    // Switching a mapping does not replace the numeric text with an artificial maximum.
                    menu.configureSlot(page, slot, key, finiteAmount, unlimited, policy);
                });
        unlimitedButton.setTooltipOn(List.of(Component.translatable("gui.data_energistics.data_sanctum_interface.unlimited_pull.enabled")));
        unlimitedButton.setTooltipOff(List.of(Component.translatable("gui.data_energistics.data_sanctum_interface.unlimited_pull.disabled")));
        unlimitedButton.setState(unlimited);
        addToLeftToolbar(unlimitedButton);
        this.policyButton = new ToggleButton(Icon.PRIORITY, Icon.SCHEDULING_ROUND_ROBIN,
                priority -> {
                    this.policy = priority ? ConnectorPolicy.PRIORITY : ConnectorPolicy.ROUND_ROBIN;
                    this.policyButton.setState(priority);
                    menu.configureSlot(page, slot, key, finiteAmount, unlimited, policy);
                });
        policyButton.setTooltipOn(List.of(Component.translatable("button.data_energistics.data_sanctum_interface.connector_policy.priority")));
        policyButton.setTooltipOff(List.of(Component.translatable("button.data_energistics.data_sanctum_interface.connector_policy.round_robin")));
        policyButton.setState(policy == ConnectorPolicy.PRIORITY);
        addToLeftToolbar(policyButton);
    }

    private void confirm() {
        amount.getLongValue().ifPresent(value -> {
            menu.configureSlot(page, slot, key, value, unlimited, policy);
            returnToParent();
        });
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        this.save.active = amount.getLongValue().isPresent();
    }
}
