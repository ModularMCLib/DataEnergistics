package com.fish_dan_.data_energistics.client.screen.patternencoding;

import com.fish_dan_.data_energistics.client.preferences.PatternEncodingPreferencesClient;
import com.fish_dan_.data_energistics.client.screen.Ae2NativeSlotHighlight;
import com.fish_dan_.data_energistics.client.widget.PatternRecipeTypeToggleButton;
import com.fish_dan_.data_energistics.integration.viewer.xei.transfer.PatternProviderRecipeTypeNames;
import com.fish_dan_.data_energistics.menu.patternencoding.BlankPatternProxyMenu;
import com.fish_dan_.data_energistics.menu.patternencoding.PatternEncodingPreferenceMenu;
import com.fish_dan_.data_energistics.menu.patternencoding.PatternEncodingSourceAware;

import appeng.api.stacks.AEItemKey;
import appeng.client.Point;
import appeng.client.gui.Icon;
import appeng.client.gui.StackWithBounds;
import appeng.client.gui.me.common.StackSizeRenderer;
import appeng.client.gui.me.items.PatternEncodingTermScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.style.WidgetStyle;
import appeng.core.definitions.AEItems;
import appeng.helpers.InventoryAction;
import appeng.menu.SlotSemantics;
import appeng.menu.me.common.GridInventoryEntry;
import appeng.menu.me.items.PatternEncodingTermMenu;
import appeng.parts.encoding.EncodingMode;
import appeng.util.ReadableNumberConverter;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import com.mojang.blaze3d.vertex.PoseStack;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import org.jspecify.annotations.Nullable;

/** Native terminal behavior with the shared LDLib2 upload window. */
public class PatternEncodingPreviewScreen<T extends PatternEncodingTermMenu> extends PatternEncodingTermScreen<T>
                                         implements Ae2NativeSlotHighlight, PatternEncodingPreviewLayerScreen {

    private static final Component ENCODE_BUTTON_HINT = Component.translatable("screen.data_energistics.pattern_writer_preview.encode_button_hint");
    private final PatternEncodingUploadPanel uploadPanel;
    private @Nullable AbstractWidget encodePatternWidget;
    private @Nullable Component originalEncodePatternMessage;
    private @Nullable PatternRecipeTypeToggleButton recipeTypeToggleButton;
    private float partialTick;

    public PatternEncodingPreviewScreen(T menu, Inventory playerInventory, Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);
        uploadPanel = new PatternEncodingUploadPanel(this, menu);
    }

    @Override
    public void init() {
        super.init();
        PatternEncodingPreferencesClient.initializeMenu(menu);
        encodePatternWidget = widgets.widgets.get("encodePattern");
        if (originalEncodePatternMessage == null && encodePatternWidget != null) originalEncodePatternMessage = encodePatternWidget.getMessage();
        initRecipeTypeToggleButton();
        updateRecipeTypeToggleButton();
        applyEncodeButtonHint();
        ObjectList<Rect2i> occupied = new ObjectArrayList<>(super.getExclusionZones());
        occupied.add(new Rect2i(leftPos, topPos, imageWidth, imageHeight));
        uploadPanel.init(encodeButtonBounds(), occupied);
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        updateRecipeTypeToggleButton();
        applyEncodeButtonHint();
        uploadPanel.update();
    }

    @Override
    public void containerTick() {
        super.containerTick();
        uploadPanel.tick();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (uploadPanel.mouseClicked(mouseX, mouseY, button)) return true;
        if (encodeButtonBounds().contains((int) mouseX, (int) mouseY) && uploadPanel.uploadEnabled()) {
            if (button == 0) {
                if (hasShiftDown()) {
                    uploadPanel.close();
                    return true;
                }
                uploadPanel.open();
                super.mouseClicked(mouseX, mouseY, button);
                return true;
            }
            if (button == 1) {
                if (hasShiftDown()) uploadPanel.close();
                else {
                    uploadPanel.open();
                    menu.encode();
                }
                return true;
            }
        }
        if (handleBlankPatternSlotClick(mouseX, mouseY, button)) return true;
        if (Minecraft.getInstance().options.keyPickItem.matchesMouse(button) && triggerBlankPatternAutoCraft(mouseX, mouseY)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return uploadPanel.keyPressed(keyCode, scanCode, modifiers) || super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        return uploadPanel.charTyped(codePoint, modifiers) || super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return uploadPanel.mouseReleased(mouseX, mouseY, button) || super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return uploadPanel.mouseDragged(mouseX, mouseY, button, dragX, dragY) || super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return uploadPanel.mouseScrolled(mouseX, mouseY, scrollX, scrollY) || super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.partialTick = partialTick;
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void renderPreviewForeground(GuiGraphics graphics, int mouseX, int mouseY) {
        if (isPreviewLayerAt(mouseX, mouseY)) hoveredSlot = null;
        uploadPanel.render(graphics, mouseX, mouseY, partialTick, leftPos, topPos);
    }

    @Override
    protected boolean isHovering(Slot slot, double mouseX, double mouseY) {
        return !isPreviewLayerAt(mouseX, mouseY) && super.isHovering(slot, mouseX, mouseY);
    }

    @Override
    protected boolean hasClickedOutside(double mouseX, double mouseY, int guiLeft, int guiTop, int button) {
        return !isPreviewLayerAt(mouseX, mouseY) && super.hasClickedOutside(mouseX, mouseY, guiLeft, guiTop, button);
    }

    @Override
    public @Nullable StackWithBounds getStackUnderMouse(double mouseX, double mouseY) {
        return isPreviewLayerAt(mouseX, mouseY) ? null : super.getStackUnderMouse(mouseX, mouseY);
    }

    @Override
    public void renderSlot(GuiGraphics guiGraphics, Slot slot) {
        if (this.menu.getSlotSemantic(slot) != SlotSemantics.BLANK_PATTERN ||
                networkBackedBlankPatternMenu() == null) {
            super.renderSlot(guiGraphics, slot);
            return;
        }

        GridInventoryEntry blankPatternEntry = findBlankPatternEntry();
        long networkStored = blankPatternEntry != null ? blankPatternEntry.getStoredAmount() : 0;
        boolean networkCraftable = blankPatternEntry != null && (blankPatternEntry.isCraftable() || blankPatternEntry.getRequestableAmount() > 0);
        int localBlankPatternCount = AEItems.BLANK_PATTERN.is(slot.getItem()) ? slot.getItem().getCount() : 0;
        long displayedCount = networkStored > 0 ? networkStored : localBlankPatternCount;
        boolean hasBlankPatterns = displayedCount > 0;

        if (slot.getItem().isEmpty() && !hasBlankPatterns) {
            Icon.BACKGROUND_ENCODED_PATTERN.getBlitter()
                    .dest(slot.x, slot.y)
                    .blit(guiGraphics);
        } else {
            ItemStack displayStack = slot.getItem().isEmpty() ? AEItems.BLANK_PATTERN.stack() : slot.getItem().copyWithCount(1);
            guiGraphics.renderItem(displayStack, slot.x, slot.y);
            guiGraphics.renderItemDecorations(this.font, displayStack, slot.x, slot.y, "");
        }

        if (displayedCount > 0) {
            StackSizeRenderer.renderSizeLabel(guiGraphics, this.font, slot.x, slot.y,
                    ReadableNumberConverter.format(displayedCount, 4));
        }

        PoseStack poseStack = guiGraphics.pose();
        if (networkCraftable) {
            poseStack.pushPose();
            poseStack.translate(0.0F, 0.0F, 100.0F);
            StackSizeRenderer.renderSizeLabel(guiGraphics, this.font, (float) (slot.x - 11), (float) (slot.y - 11),
                    "+", false);
            poseStack.popPose();
        }
    }

    @Override
    public ObjectList<Rect2i> getExclusionZones() {
        ObjectList<Rect2i> zones = new ObjectArrayList<>(super.getExclusionZones());
        zones.addAll(uploadPanel.exclusions());
        return zones;
    }

    @Override
    public boolean shouldSuppressUnderlyingTooltip(int mouseX, int mouseY) {
        return uploadPanel.suppressTooltip(mouseX, mouseY);
    }

    @Override
    public boolean isPreviewLayerAt(double mouseX, double mouseY) {
        return uploadPanel.contains(mouseX, mouseY);
    }

    @Override
    public void onClose() {
        uploadPanel.close();
        super.onClose();
    }

    @Override
    public void removed() {
        uploadPanel.close();
        super.removed();
    }

    protected void applyEncodeButtonHint() {
        if (encodePatternWidget != null) encodePatternWidget.setMessage(getEncodeButtonHint());
    }

    protected Component getEncodeButtonHint() {
        return uploadPanel.uploadEnabled() ? ENCODE_BUTTON_HINT : originalEncodePatternMessage == null ? ENCODE_BUTTON_HINT : originalEncodePatternMessage;
    }

    private Rect2i encodeButtonBounds() {
        if (encodePatternWidget != null && encodePatternWidget.visible)
            return new Rect2i(encodePatternWidget.getX(), encodePatternWidget.getY(), encodePatternWidget.getWidth(), encodePatternWidget.getHeight());
        WidgetStyle widget = getStyle().getWidget("encodePattern");
        Point point = widget.resolve(new Rect2i(leftPos, topPos, imageWidth, imageHeight));
        return new Rect2i(point.getX(), point.getY(), widget.getWidth() > 0 ? widget.getWidth() : 16, widget.getHeight() > 0 ? widget.getHeight() : 16);
    }

    private void initRecipeTypeToggleButton() {
        if (!(this.menu instanceof PatternEncodingSourceAware sourceAware)) {
            return;
        }

        this.recipeTypeToggleButton = new PatternRecipeTypeToggleButton(
                enabled -> PatternEncodingPreferencesClient.setPatternSourceEnabled(this.menu, enabled));
        this.recipeTypeToggleButton.setState(sourceAware.data_energistics$isPatternSourceEnabled());
        this.addRenderableWidget(this.recipeTypeToggleButton);
    }

    private void updateRecipeTypeToggleButton() {
        if (this.recipeTypeToggleButton == null || !(this.menu instanceof PatternEncodingSourceAware sourceAware)) {
            return;
        }

        this.recipeTypeToggleButton.setState(sourceAware.data_energistics$isPatternSourceEnabled());
        var recipeContext = ((PatternEncodingPreferenceMenu) this.menu).data_energistics$getPreferenceSession().rankingContext();
        if (recipeContext != null) {
            this.recipeTypeToggleButton.setDetailLine(Component.translatable(
                    "button.data_energistics.pattern_encoding_recipe_type_toggle.detail",
                    PatternProviderRecipeTypeNames.resolveDisplayName(recipeContext.recipeTypeId())));
        } else {
            this.recipeTypeToggleButton.setDetailLine(Component.translatable(
                    "button.data_energistics.pattern_encoding_recipe_type_toggle.detail.none"));
        }
        boolean visible = this.menu.getMode() == EncodingMode.PROCESSING;
        this.recipeTypeToggleButton.visible = visible;
        this.recipeTypeToggleButton.active = visible;
        WidgetStyle clearButtonStyle = this.getStyle().getWidget("processingClearPattern");
        Point clearButtonPosition = clearButtonStyle.resolve(new Rect2i(this.leftPos, this.topPos, this.imageWidth, this.imageHeight));
        this.recipeTypeToggleButton.setX(clearButtonPosition.getX());
        this.recipeTypeToggleButton.setY(clearButtonPosition.getY() + 10);
    }

    private boolean triggerBlankPatternAutoCraft(double mouseX, double mouseY) {
        if (networkBackedBlankPatternMenu() == null) {
            return false;
        }

        Slot slot = this.hoveredSlot;
        if (slot == null || this.menu.getSlotSemantic(slot) != SlotSemantics.BLANK_PATTERN) {
            return false;
        }
        if (isMouseOutsideSlot(slot, mouseX, mouseY)) {
            return false;
        }

        GridInventoryEntry blankPatternEntry = findBlankPatternEntry();
        if (blankPatternEntry == null || !blankPatternEntry.isCraftable()) {
            return false;
        }

        this.menu.handleInteraction(blankPatternEntry.getSerial(), InventoryAction.AUTO_CRAFT);
        return true;
    }

    private boolean handleBlankPatternSlotClick(double mouseX, double mouseY, int button) {
        BlankPatternProxyMenu blankPatternProxyMenu = networkBackedBlankPatternMenu();
        if (blankPatternProxyMenu == null) {
            return false;
        }

        Slot slot = this.hoveredSlot;
        if (slot == null || this.menu.getSlotSemantic(slot) != SlotSemantics.BLANK_PATTERN || isMouseOutsideSlot(slot, mouseX, mouseY)) {
            return false;
        }

        if (button == 0) {
            if (this.menu.getCarried().isEmpty()) {
                blankPatternProxyMenu.data_energistics$pickupBlankPatterns(false);
            } else if (AEItems.BLANK_PATTERN.is(this.menu.getCarried())) {
                blankPatternProxyMenu.data_energistics$depositCarriedBlankPatterns(false);
            } else {
                return false;
            }
            return true;
        }

        if (button == 1) {
            if (this.menu.getCarried().isEmpty()) {
                blankPatternProxyMenu.data_energistics$pickupBlankPatterns(true);
            } else if (AEItems.BLANK_PATTERN.is(this.menu.getCarried())) {
                blankPatternProxyMenu.data_energistics$depositCarriedBlankPatterns(true);
            } else {
                return false;
            }
            return true;
        }

        return false;
    }

    private @Nullable BlankPatternProxyMenu networkBackedBlankPatternMenu() {
        if (this.menu instanceof BlankPatternProxyMenu blankPatternProxyMenu &&
                blankPatternProxyMenu.data_energistics$usesNetworkBackedBlankPatternSlot()) {
            return blankPatternProxyMenu;
        }
        return null;
    }

    private @Nullable GridInventoryEntry findBlankPatternEntry() {
        AEItemKey blankPatternKey = AEItemKey.of(AEItems.BLANK_PATTERN);
        if (blankPatternKey == null) {
            return null;
        }

        GridInventoryEntry fallback = null;
        for (GridInventoryEntry entry : this.repo.getAllEntries()) {
            if (!blankPatternKey.equals(entry.getWhat())) {
                continue;
            }
            if (entry.isMeaningful()) {
                return entry;
            }
            if (fallback == null) {
                fallback = entry;
            }
        }

        return fallback;
    }

    private boolean isMouseOutsideSlot(Slot slot, double mouseX, double mouseY) {
        return mouseX < this.leftPos + slot.x || mouseX >= this.leftPos + slot.x + 16 ||
                mouseY < this.topPos + slot.y || mouseY >= this.topPos + slot.y + 16;
    }
}
