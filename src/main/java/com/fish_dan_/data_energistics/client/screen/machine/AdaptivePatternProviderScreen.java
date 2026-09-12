package com.fish_dan_.data_energistics.client.screen.machine;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.api.registry.adaptive.AdaptivePatternProviderToolbarAction;
import com.fish_dan_.data_energistics.api.registry.adaptive.client.AdaptivePatternProviderToolbarButton;
import com.fish_dan_.data_energistics.api.registry.adaptive.client.AdaptivePatternProviderToolbarContext;
import com.fish_dan_.data_energistics.client.gui.DataEnergisticsIcon;
import com.fish_dan_.data_energistics.client.registry.adaptive.AdaptivePatternProviderToolbarFactories;
import com.fish_dan_.data_energistics.menu.patternprovider.AdaptivePatternProviderMenu;

import appeng.api.client.AEKeyRendering;
import appeng.api.config.LockCraftingMode;
import appeng.api.stacks.AmountFormat;
import appeng.api.stacks.GenericStack;
import appeng.api.upgrades.Upgrades;
import appeng.client.Point;
import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.ICompositeWidget;
import appeng.client.gui.Icon;
import appeng.client.gui.Tooltip;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.ToolboxPanel;
import appeng.client.gui.widgets.UpgradesPanel;
import appeng.core.localization.GuiText;
import appeng.core.localization.InGameTooltip;
import appeng.menu.SlotSemantics;
import appeng.menu.slot.AppEngSlot;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import lombok.Getter;
import lombok.Setter;
import org.jspecify.annotations.Nullable;

import java.util.List;

public class AdaptivePatternProviderScreen extends AEBaseScreen<AdaptivePatternProviderMenu> {

    private static final int HIDDEN_SLOT_COORD = -9999;

    private final Object2ObjectLinkedOpenHashMap<ResourceLocation, AdaptivePatternProviderToolbarButton> toolbarButtons = new Object2ObjectLinkedOpenHashMap<>();
    private ObjectList<AdaptivePatternProviderToolbarAction> toolbarActions = ObjectList.of();
    private final ObjectOpenHashSet<ResourceLocation> declaredToolbarActions = new ObjectOpenHashSet<>();
    private final AdaptivePatternProviderLockReason lockReason;
    private final ObjectList<Slot> duplicateUpgradeSlots;
    private final ObjectList<Slot> duplicateToolboxSlots;

    public AdaptivePatternProviderScreen(AdaptivePatternProviderMenu menu, Inventory playerInventory, Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);

        this.widgets.addOpenPriorityButton();
        this.lockReason = new AdaptivePatternProviderLockReason(this);
        this.widgets.add("lockReason", this.lockReason);

        var upgradeSlots = splitUniqueSlots(menu.getSlots(SlotSemantics.UPGRADE));
        var toolboxSlots = splitUniqueSlots(menu.getSlots(SlotSemantics.TOOLBOX));
        this.duplicateUpgradeSlots = upgradeSlots.duplicates();
        this.duplicateToolboxSlots = toolboxSlots.duplicates();

        installOrReplaceCompositeWidget("upgrades", new UpgradesPanel(upgradeSlots.unique(), this::getCompatibleUpgrades));
        if (menu.getToolbox().isPresent() && !hasWidget("toolbox")) {
            this.widgets.add("toolbox", new ToolboxPanel(style, menu.getToolbox().getName()));
        }

        var context = new AdaptivePatternProviderToolbarContext(menu, this::isHandlingRightClick);
        for (var entry : AdaptivePatternProviderToolbarFactories.entries()) {
            var binding = entry.factory().apply(context);
            this.toolbarButtons.put(entry.actionId(), binding);
            this.addToLeftToolbar(binding.button());
        }
        synchronizeToolbar();
    }

    @Override
    protected void init() {
        super.init();
        hideDuplicatedAuxiliarySlots();
    }

    @Override
    protected void updateBeforeRender() {
        synchronizeToolbar();
        super.updateBeforeRender();

        this.lockReason.setVisible(this.menu.getLockCraftingMode() != LockCraftingMode.NONE);

        this.setTextContent("dialog_title",
                Component.translatable("block.data_energistics.adaptive_pattern_provider"));
        this.setTextContent("page_info", Component.translatable(
                "screen.data_energistics.page",
                this.menu.totalPages <= 0 ? 1 : this.menu.pageIndex + 1,
                Math.max(1, this.menu.totalPages)));
    }

    private void synchronizeToolbar() {
        var actions = this.menu.getRegisteredToolbarActions();
        if (actions != this.toolbarActions) {
            this.declaredToolbarActions.clear();
            for (var action : actions) {
                if (!this.toolbarButtons.containsKey(action.actionId())) {
                    throw new IllegalStateException("No client factory registered for adaptive toolbar action " + action.actionId());
                }
                this.declaredToolbarActions.add(action.actionId());
            }
            this.toolbarActions = actions;
        }
        for (var entry : this.toolbarButtons.object2ObjectEntrySet()) {
            entry.getValue().synchronize(this.declaredToolbarActions.contains(entry.getKey()));
        }
    }

    @Override
    public void renderSlot(GuiGraphics guiGraphics, Slot slot) {
        var semantic = this.menu.getSlotSemantic(slot);
        if (slot.isActive() && slot.getItem().isEmpty() && semantic == AdaptivePatternProviderMenu.PAGE_PATTERN) {
            Icon.BACKGROUND_BLANK_PATTERN.getBlitter()
                    .dest(slot.x, slot.y)
                    .blit(guiGraphics);
        } else if (slot.isActive() && semantic == AdaptivePatternProviderMenu.PROVIDER_INPUT && slot.getItem().isEmpty()) {
            DataEnergisticsIcon.getBlitter("BACKGROUND_BLOCK")
                    .dest(slot.x, slot.y)
                    .blit(guiGraphics);
        }
        super.renderSlot(guiGraphics, slot);
    }

    private List<Component> getCompatibleUpgrades() {
        ObjectArrayList<Component> list = new ObjectArrayList<>();
        list.add(GuiText.CompatibleUpgrades.text());
        list.addAll(Upgrades.getTooltipLinesForMachine(this.menu.getUpgrades().getUpgradableItem()));
        return list;
    }

    private void hideDuplicatedAuxiliarySlots() {
        hideSlots(this.duplicateUpgradeSlots);
        hideSlots(this.duplicateToolboxSlots);
    }

    private static void hideSlots(List<Slot> slots) {
        for (var slot : slots) {
            if (slot instanceof AppEngSlot appEngSlot) {
                appEngSlot.setActive(false);
                appEngSlot.setSlotEnabled(false);
            } else {
                String message = "Could not hide duplicate adaptive pattern provider slot: " + slot.getClass().getName();
                Data_Energistics.LOGGER.error(message);
                throw new IllegalStateException(message);
            }
            setSlotPosition(slot, HIDDEN_SLOT_COORD, HIDDEN_SLOT_COORD);
        }
    }

    private void installOrReplaceCompositeWidget(String id, ICompositeWidget widget) {
        this.widgets.compositeWidgets.put(id, widget);
    }

    private boolean hasWidget(String id) {
        return this.widgets.widgets.containsKey(id) || this.widgets.compositeWidgets.containsKey(id);
    }

    private static SlotBuckets splitUniqueSlots(List<Slot> slots) {
        Object2ObjectLinkedOpenHashMap<String, Slot> uniqueByBackingSlot = new Object2ObjectLinkedOpenHashMap<>();
        ObjectArrayList<Slot> duplicates = new ObjectArrayList<>();
        for (var slot : slots) {
            String key = System.identityHashCode(slot.container) + ":" + slot.getContainerSlot();
            if (uniqueByBackingSlot.putIfAbsent(key, slot) != null) {
                duplicates.add(slot);
            }
        }
        return new SlotBuckets(ObjectLists.unmodifiable(new ObjectArrayList<>(uniqueByBackingSlot.values())),
                ObjectLists.unmodifiable(duplicates));
    }

    private static void setSlotPosition(Slot slot, int x, int y) {
        slot.x = x;
        slot.y = y;
    }

    private record SlotBuckets(ObjectList<Slot> unique, ObjectList<Slot> duplicates) {}

    private static final class AdaptivePatternProviderLockReason implements ICompositeWidget {

        private final AdaptivePatternProviderScreen screen;
        @Setter
        @Getter
        private boolean visible;
        private int x;
        private int y;

        private AdaptivePatternProviderLockReason(AdaptivePatternProviderScreen screen) {
            this.screen = screen;
        }

        public void setPosition(Point position) {
            this.x = position.getX();
            this.y = position.getY();
        }

        public void setSize(int width, int height) {}

        public Rect2i getBounds() {
            return new Rect2i(this.x, this.y, 126, 16);
        }

        public void drawForegroundLayer(GuiGraphics guiGraphics, Rect2i bounds, Point mouse) {
            Icon icon;
            Component lockStatusText;
            if (this.screen.menu.getCraftingLockedReason() == LockCraftingMode.NONE) {
                icon = Icon.UNLOCKED;
                lockStatusText = GuiText.CraftingLockIsUnlocked.text()
                        .setStyle(Style.EMPTY.withColor(Mth.color(0.49019608F, 0.6627451F, 0.8235294F)));
            } else {
                icon = Icon.LOCKED;
                lockStatusText = GuiText.CraftingLockIsLocked.text()
                        .setStyle(Style.EMPTY.withColor(Mth.color(0.75686276F, 0.25882354F, 0.29411766F)));
            }

            icon.getBlitter().dest(this.x, this.y).blit(guiGraphics);
            guiGraphics.drawString(Minecraft.getInstance().font, lockStatusText, this.x + 15, this.y + 5, -1, false);
        }

        public @Nullable Tooltip getTooltip(int mouseX, int mouseY) {
            MutableComponent tooltip = switch (this.screen.menu.getCraftingLockedReason()) {
                case NONE -> null;
                case LOCK_UNTIL_PULSE -> InGameTooltip.CraftingLockedUntilPulse.text();
                case LOCK_WHILE_HIGH -> InGameTooltip.CraftingLockedByRedstoneSignal.text();
                case LOCK_WHILE_LOW -> InGameTooltip.CraftingLockedByLackOfRedstoneSignal.text();
                case LOCK_UNTIL_RESULT -> {
                    GenericStack stack = this.screen.menu.getUnlockStack();
                    Component stackName;
                    Component stackAmount;
                    if (stack != null) {
                        stackName = AEKeyRendering.getDisplayName(stack.what());
                        stackAmount = Component.literal(stack.what().formatAmount(stack.amount(), AmountFormat.FULL));
                    } else {
                        stackName = Component.literal("ERROR");
                        stackAmount = Component.literal("ERROR");
                    }
                    yield InGameTooltip.CraftingLockedUntilResult.text(stackName, stackAmount);
                }
            };
            return tooltip != null ? new Tooltip(tooltip) : null;
        }
    }
}
