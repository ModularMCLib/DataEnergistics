package com.fish_dan_.data_energistics.mixin.viewer.jei;

import com.fish_dan_.data_energistics.client.terminal.TerminalOverlayClient;

import net.minecraft.client.Minecraft;

import com.mojang.blaze3d.platform.InputConstants;
import mezz.jei.common.input.IInternalKeyMappings;
import mezz.jei.common.input.UserInput;
import mezz.jei.gui.input.CombinedRecipeFocusSource;
import mezz.jei.gui.input.IClickableIngredientInternal;
import org.jspecify.annotations.NullMarked;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.stream.Stream;

/** Only unclaimed inputs may look up recipes; foreground inventory clicks retain their existing meaning. */
@NullMarked
@Mixin(value = CombinedRecipeFocusSource.class, remap = false)
public abstract class PlayerTerminalInputMixin {

    @Inject(method = "getIngredientUnderMouse(Lmezz/jei/common/input/UserInput;Lmezz/jei/common/input/IInternalKeyMappings;)Ljava/util/stream/Stream;",
            at = @At("HEAD"),
            cancellable = true)
    private void dataEnergistics$keepTerminalMouseInput(UserInput input, IInternalKeyMappings keyBindings,
                                                        CallbackInfoReturnable<Stream<IClickableIngredientInternal<?>>> cir) {
        var screen = Minecraft.getInstance().screen;
        var key = input.getKey();
        if (screen != null && key.getType() == InputConstants.Type.MOUSE &&
                TerminalOverlayClient.ownsMouseButton(screen, input.getMouseX(), input.getMouseY(), key.getValue()))
            cir.setReturnValue(Stream.empty());
    }
}
