package com.fish_dan_.data_energistics.mixin.ae.ae2.client.crafting;

import com.fish_dan_.data_energistics.client.crafting.NumberEntryWidgetValidationRegistry;

import appeng.client.gui.widgets.ConfirmableTextField;
import appeng.client.gui.widgets.NumberEntryWidget;
import appeng.client.gui.widgets.ValidationIcon;

import net.minecraft.network.chat.Component;

import it.unimi.dsi.fastutil.objects.ObjectList;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * Replaces the amount widget's integer-only visual validation with the parser used by the crafting screen.
 */
@Mixin(NumberEntryWidget.class)
public abstract class NumberEntryWidgetValidationMixin {

    @Shadow
    @Final
    private ConfirmableTextField textField;

    @Shadow
    @Final
    private int normalTextColor;

    @Shadow
    @Final
    private int errorTextColor;

    @Shadow
    @Nullable
    private ValidationIcon validationIcon;

    @Inject(method = "validate", at = @At("RETURN"))
    private void dataEnergistics$validateExpression(CallbackInfo ci) {
        if (!NumberEntryWidgetValidationRegistry.isEnabled((NumberEntryWidget) (Object) this)) {
            return;
        }
        dataEnergistics$applyExpressionValidation();
    }

    @Unique
    private void dataEnergistics$applyExpressionValidation() {
        var parsed = NumberEntryWidgetValidationRegistry.parse((NumberEntryWidget) (Object) this, this.textField.getValue());
        ObjectList<Component> messages = ObjectList.of();
        if (parsed.isPresent()) {
            this.textField.setTextColor(this.normalTextColor);
        } else {
            this.textField.setTextColor(this.errorTextColor);
            messages = ObjectList.of(Component.translatable(
                    NumberEntryWidgetValidationRegistry.isStockAmount((NumberEntryWidget) (Object) this) ?
                            "gui.data_energistics.data_sanctum_interface.invalid_amount" :
                            "gui.data_energistics.crafting.amount.invalid_expression"));
        }
        this.textField.setTooltipMessage(messages);
        if (this.validationIcon != null) {
            this.validationIcon.setValid(parsed.isPresent());
            this.validationIcon.setTooltip(messages);
        }
    }

    @Inject(method = "getLongValue", at = @At("HEAD"), cancellable = true)
    private void dataEnergistics$readStockExpression(CallbackInfoReturnable<OptionalLong> cir) {
        NumberEntryWidget widget = (NumberEntryWidget) (Object) this;
        if (NumberEntryWidgetValidationRegistry.isEnabled(widget)) {
            cir.setReturnValue(NumberEntryWidgetValidationRegistry.parse(widget, textField.getValue()));
        }
    }

    @Inject(method = "getValueInternal", at = @At("HEAD"), cancellable = true)
    private void dataEnergistics$stockExpressionForButtons(CallbackInfoReturnable<Optional<BigDecimal>> cir) {
        NumberEntryWidget widget = (NumberEntryWidget) (Object) this;
        if (NumberEntryWidgetValidationRegistry.isEnabled(widget)) {
            var parsed = NumberEntryWidgetValidationRegistry.parse(widget, textField.getValue());
            cir.setReturnValue(parsed.isPresent() ? Optional.of(BigDecimal.valueOf(parsed.getAsLong()).divide(BigDecimal.valueOf(widget.getType().amountPerUnit()), MathContext.DECIMAL128)) : Optional.empty());
        }
    }
}
