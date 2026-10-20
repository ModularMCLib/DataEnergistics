package com.fish_dan_.data_energistics.mixin.ae.ae2.client.patternencoding;

import com.fish_dan_.data_energistics.ae2.DEAE2Keys;
import com.fish_dan_.data_energistics.client.preferences.PatternEncodingPreferencesClient;
import com.fish_dan_.data_energistics.client.screen.Ae2NativeSlotHighlight;
import com.fish_dan_.data_energistics.client.screen.GenericStackLookupScreen;
import com.fish_dan_.data_energistics.client.screen.patternencoding.NetworkBlankPatternSlot;
import com.fish_dan_.data_energistics.client.screen.patternencoding.PatternEncodingScreenControls;
import com.fish_dan_.data_energistics.client.screen.patternencoding.PatternEncodingUploadClient;
import com.fish_dan_.data_energistics.client.screen.patternencoding.ProcessingPatternAmountContext;
import com.fish_dan_.data_energistics.client.widget.PatternRecipeTypeToggleButton;
import com.fish_dan_.data_energistics.integration.viewer.xei.transfer.PatternProviderRecipeTypeNames;
import com.fish_dan_.data_energistics.menu.patternencoding.PatternEncodingPreferenceMenu;
import com.fish_dan_.data_energistics.menu.patternencoding.PatternEncodingSourceAware;

import appeng.api.stacks.GenericStack;
import appeng.client.Point;
import appeng.client.gui.StackWithBounds;
import appeng.client.gui.me.common.MEStorageScreen;
import appeng.client.gui.me.items.PatternEncodingTermScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.menu.me.items.PatternEncodingTermMenu;
import appeng.parts.encoding.EncodingMode;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Extends the real AE encoding screen without replacing it or its wireless subclasses. */
@Mixin(PatternEncodingTermScreen.class)
public abstract class PatternEncodingTermScreenMixin extends MEStorageScreen<PatternEncodingTermMenu>
                                                     implements ProcessingPatternAmountContext, PatternEncodingScreenControls, Ae2NativeSlotHighlight, GenericStackLookupScreen {

    @Unique
    private static final Component DATA_ENERGISTICS_ENCODE_HINT = Component.translatable("screen.data_energistics.pattern_writer_preview.encode_button_hint");
    @Unique
    private @Nullable AbstractWidget dataEnergistics$encodeWidget;
    @Unique
    private @Nullable Component dataEnergistics$originalEncodeMessage;
    @Unique
    private @Nullable PatternRecipeTypeToggleButton dataEnergistics$sourceButton;

    @Unique
    private int dataEnergistics$processingInputAmountTarget = -1;
    @Unique
    private int dataEnergistics$processingOutputAmountTarget = -1;

    protected PatternEncodingTermScreenMixin(PatternEncodingTermMenu menu,
                                             Inventory playerInventory,
                                             Component title,
                                             ScreenStyle style) {
        super(menu, playerInventory, title, style);
    }

    @Override
    public void data_energistics$initializeEncodingControls() {
        this.dataEnergistics$encodeWidget = widgets.widgets.get("encodePattern");
        if (this.dataEnergistics$originalEncodeMessage == null && this.dataEnergistics$encodeWidget != null)
            this.dataEnergistics$originalEncodeMessage = this.dataEnergistics$encodeWidget.getMessage();
        ObjectList<Rect2i> occupied = new ObjectArrayList<>(super.getExclusionZones());
        occupied.add(new Rect2i(leftPos, topPos, imageWidth, imageHeight));
        PatternEncodingUploadClient.attach((PatternEncodingTermScreen<?>) (Object) this, dataEnergistics$encodeBounds(), occupied);
        this.dataEnergistics$sourceButton = new PatternRecipeTypeToggleButton(enabled -> PatternEncodingPreferencesClient.setPatternSourceEnabled(menu, enabled));
        addRenderableWidget(this.dataEnergistics$sourceButton);
        dataEnergistics$updateControls();
    }

    @Inject(method = "updateBeforeRender", at = @At("TAIL"))
    private void dataEnergistics$updateUploadControls(CallbackInfo ci) {
        dataEnergistics$updateControls();
    }

    @Inject(method = "renderSlot", at = @At("HEAD"), cancellable = true)
    private void dataEnergistics$renderNetworkBlankPatterns(GuiGraphics guiGraphics, Slot s, CallbackInfo ci) {
        if (NetworkBlankPatternSlot.render(menu, s, repo.getAllEntries(), font, guiGraphics)) ci.cancel();
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void dataEnergistics$captureProcessingAmountTarget(double xCoord,
                                                               double yCoord,
                                                               int btn,
                                                               CallbackInfoReturnable<Boolean> cir) {
        this.dataEnergistics$processingInputAmountTarget = -1;
        this.dataEnergistics$processingOutputAmountTarget = -1;
        if (dataEnergistics$encodeBounds().contains((int) xCoord, (int) yCoord) &&
                PatternEncodingUploadClient.encodeClicked(menu, btn)) {
            cir.setReturnValue(true);
            return;
        }

        Slot clicked = null;
        for (Slot candidate : this.menu.slots) {
            if (candidate.isActive() && this.isHovering(candidate, xCoord, yCoord)) {
                clicked = candidate;
                break;
            }
        }
        if (NetworkBlankPatternSlot.click(menu, clicked, repo.getAllEntries(), btn)) {
            cir.setReturnValue(true);
            return;
        }
        if (!Minecraft.getInstance().options.keyPickItem.matchesMouse(btn) || this.menu.getMode() != EncodingMode.PROCESSING) return;
        var inputs = this.menu.getProcessingInputSlots();
        for (int index = 0; index < inputs.length; index++) {
            if (clicked == inputs[index]) {
                this.dataEnergistics$processingInputAmountTarget = index;
                return;
            }
        }
        var outputs = this.menu.getProcessingOutputSlots();
        for (int index = 0; index < outputs.length; index++) {
            if (clicked == outputs[index]) {
                this.dataEnergistics$processingOutputAmountTarget = index;
                return;
            }
        }
    }

    @Override
    protected boolean isHovering(Slot slot, double x, double y) {
        return !PatternEncodingUploadClient.contains(this, x, y) && super.isHovering(slot, x, y);
    }

    @Override
    protected boolean hasClickedOutside(double x, double y, int guiLeft, int guiTop, int button) {
        return !PatternEncodingUploadClient.contains(this, x, y) && super.hasClickedOutside(x, y, guiLeft, guiTop, button);
    }

    @Override
    public @Nullable StackWithBounds getStackUnderMouse(double x, double y) {
        return PatternEncodingUploadClient.contains(this, x, y) ? null : super.getStackUnderMouse(x, y);
    }

    @Override
    public ObjectList<Rect2i> getExclusionZones() {
        ObjectList<Rect2i> zones = new ObjectArrayList<>(super.getExclusionZones());
        zones.addAll(PatternEncodingUploadClient.exclusions(this));
        return zones;
    }

    @Override
    public @Nullable StackWithBounds dataEnergistics$getGenericStackUnderMouse(double x, double y) {
        if (PatternEncodingUploadClient.contains(this, x, y) || menu.getMode() != EncodingMode.PROCESSING || hoveredSlot == null) return null;
        for (var slot : menu.getProcessingInputSlots()) {
            if (slot != hoveredSlot || !slot.isActive()) continue;
            GenericStack stack = GenericStack.fromItemStack(slot.getItem());
            if (stack != null && DEAE2Keys.isCustomKey(stack.what()) && stack.amount() > 0)
                return new StackWithBounds(stack, new Rect2i(leftPos + slot.x, topPos + slot.y, 16, 16));
        }
        return null;
    }

    @Unique
    private Rect2i dataEnergistics$encodeBounds() {
        if (this.dataEnergistics$encodeWidget != null && this.dataEnergistics$encodeWidget.visible) {
            var widget = this.dataEnergistics$encodeWidget;
            return new Rect2i(widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight());
        }
        var style = getStyle().getWidget("encodePattern");
        Point point = style.resolve(new Rect2i(leftPos, topPos, imageWidth, imageHeight));
        return new Rect2i(point.getX(), point.getY(), style.getWidth() > 0 ? style.getWidth() : 16, style.getHeight() > 0 ? style.getHeight() : 16);
    }

    @Unique
    private void dataEnergistics$updateControls() {
        if (this.dataEnergistics$encodeWidget != null) {
            Component original = this.dataEnergistics$originalEncodeMessage;
            this.dataEnergistics$encodeWidget.setMessage(PatternEncodingUploadClient.enabled(menu) || original == null ? DATA_ENERGISTICS_ENCODE_HINT : original);
        }
        if (this.dataEnergistics$sourceButton == null) return;
        this.dataEnergistics$sourceButton.setState(((PatternEncodingSourceAware) menu).data_energistics$isPatternSourceEnabled());
        var context = ((PatternEncodingPreferenceMenu) menu).data_energistics$getPreferenceSession().rankingContext();
        this.dataEnergistics$sourceButton.setDetailLine(context == null ? Component.translatable("button.data_energistics.pattern_encoding_recipe_type_toggle.detail.none") :
                Component.translatable("button.data_energistics.pattern_encoding_recipe_type_toggle.detail", PatternProviderRecipeTypeNames.resolveDisplayName(context.recipeTypeId())));
        boolean visible = menu.getMode() == EncodingMode.PROCESSING;
        this.dataEnergistics$sourceButton.visible = visible;
        this.dataEnergistics$sourceButton.active = visible;
        Point clear = getStyle().getWidget("processingClearPattern").resolve(new Rect2i(leftPos, topPos, imageWidth, imageHeight));
        this.dataEnergistics$sourceButton.setX(clear.getX());
        this.dataEnergistics$sourceButton.setY(clear.getY() + 10);
    }

    @Override
    public int data_energistics$getProcessingInputAmountTarget() {
        return this.dataEnergistics$processingInputAmountTarget;
    }

    @Override
    public int data_energistics$getProcessingOutputAmountTarget() {
        return this.dataEnergistics$processingOutputAmountTarget;
    }
}
