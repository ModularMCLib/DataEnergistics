package com.fish_dan_.data_energistics.mixin.ae.ae2.client.crafting;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.util.FormattingUtils;

import appeng.client.gui.style.Blitter;
import appeng.client.gui.widgets.CPUSelectionList;
import appeng.client.gui.widgets.InfoBar;
import appeng.core.localization.Tooltips;
import appeng.menu.me.crafting.CraftingStatusMenu;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Slice;

/**
 * Keeps Trinity CPU rows and tooltips consistent across AE2's CPU list and compatible list-formatting mixins.
 *
 * <p>
 * The storage and co-processor wrappers use an earlier injector order than default-order compatibility wrappers. This
 * keeps them as the innermost operation wrappers so they can enforce Trinity formatting immediately before the real
 * {@link InfoBar} call. Row rendering and tooltips use AE2's current local CPU and coordinates instead of cached state.
 */
@Mixin(value = CPUSelectionList.class, priority = 2000)
public abstract class CPUSelectionListMixin {

    @Unique
    private static final String DATA_ENERGISTICS_TRINITY_CPU_NAME_KEY = "block.data_energistics.trinity_data_core";

    @Unique
    private static final ResourceLocation DATA_ENERGISTICS_CPU_IDLE_TEXTURE = Data_Energistics.id(
            "textures/guis/trinity_data_core/cpu_idle.png");

    @Unique
    private static final ResourceLocation DATA_ENERGISTICS_CPU_TASK_OVERLAY_TEXTURE = Data_Energistics.id(
            "textures/guis/trinity_data_core/cpu_task_overlay.png");

    @Unique
    private static final Blitter DATA_ENERGISTICS_CPU_IDLE = Blitter.texture(DATA_ENERGISTICS_CPU_IDLE_TEXTURE, 67, 22).src(0, 0, 67, 22);

    @Unique
    private static final Blitter DATA_ENERGISTICS_CPU_TASK_OVERLAY = Blitter.texture(DATA_ENERGISTICS_CPU_TASK_OVERLAY_TEXTURE, 67, 22).src(0, 0, 67, 22);

    @Shadow
    @Final
    private Blitter buttonBg;

    @WrapOperation(
                   method = "drawBackgroundLayer",
                   at = @At(
                            value = "INVOKE",
                            target = "Lappeng/client/gui/widgets/CPUSelectionList;getCpuName(Lappeng/menu/me/crafting/CraftingStatusMenu$CraftingCpuListEntry;)Lnet/minecraft/network/chat/Component;"))
    private Component dataEnergistics$drawTrinityCpuBackground(CPUSelectionList instance,
                                                               CraftingStatusMenu.CraftingCpuListEntry cpu,
                                                               Operation<Component> original,
                                                               @Local(argsOnly = true) GuiGraphics guiGraphics,
                                                               @Local(name = "x") int x,
                                                               @Local(name = "y") int y) {
        if (dataEnergistics$isTrinityCpu(cpu)) {
            DATA_ENERGISTICS_CPU_IDLE.copy()
                    .dest(
                            x,
                            y,
                            this.buttonBg.getSrcWidth(),
                            this.buttonBg.getSrcHeight())
                    .blit(guiGraphics);
        }
        return original.call(instance, cpu);
    }

    @WrapOperation(
                   method = "drawBackgroundLayer",
                   at = @At(
                            value = "INVOKE",
                            target = "Lappeng/client/gui/widgets/InfoBar;add(Ljava/lang/String;IFII)V",
                            ordinal = 0),
                   slice = @Slice(
                                  from = @At(
                                             value = "FIELD",
                                             target = "Lappeng/client/gui/Icon;S_STORAGE:Lappeng/client/gui/Icon;",
                                             opcode = Opcodes.GETSTATIC)),
                   require = 1,
                   order = 900)
    private void dataEnergistics$formatTrinityCpuStorage(InfoBar instance,
                                                         String text,
                                                         int color,
                                                         float scale,
                                                         int xPos,
                                                         int yPos,
                                                         Operation<Void> original,
                                                         @Local(name = "cpu") CraftingStatusMenu.CraftingCpuListEntry cpu) {
        if (dataEnergistics$isTrinityCpu(cpu)) {
            text = dataEnergistics$formatStorageAmount(cpu.storage()).getString();
        }
        original.call(instance, text, color, scale, xPos, yPos);
    }

    @WrapOperation(
                   method = "drawBackgroundLayer",
                   at = @At(
                            value = "INVOKE",
                            target = "Lappeng/client/gui/widgets/InfoBar;add(Ljava/lang/String;IFII)V"),
                   slice = @Slice(
                                  from = @At(
                                             value = "FIELD",
                                             target = "Lappeng/client/gui/Icon;S_PROCESSOR:Lappeng/client/gui/Icon;",
                                             opcode = Opcodes.GETSTATIC)),
                   require = 1,
                   order = 900)
    private void dataEnergistics$formatTrinityCpuCoProcessors(InfoBar instance,
                                                              String text,
                                                              int color,
                                                              float scale,
                                                              int xPos,
                                                              int yPos,
                                                              Operation<Void> original,
                                                              @Local(name = "cpu") CraftingStatusMenu.CraftingCpuListEntry cpu) {
        original.call(
                instance,
                dataEnergistics$isTrinityCpu(cpu) ? dataEnergistics$unlimited().getString() : text,
                color,
                scale,
                xPos,
                yPos);
    }

    @ModifyExpressionValue(
                           method = "getTooltip",
                           at = @At(
                                    value = "INVOKE",
                                    target = "Lappeng/core/localization/Tooltips;ofNumber(J)Lnet/minecraft/network/chat/MutableComponent;"))
    private MutableComponent dataEnergistics$formatTrinityCpuTooltipCoProcessors(MutableComponent number,
                                                                                 @Local(name = "cpu") CraftingStatusMenu.CraftingCpuListEntry cpu) {
        if (dataEnergistics$isTrinityCpu(cpu)) {
            return dataEnergistics$unlimited();
        }
        return number;
    }

    @ModifyExpressionValue(
                           method = "getTooltip",
                           at = @At(
                                    value = "INVOKE",
                                    target = "Lappeng/core/localization/Tooltips;ofBytes(J)Lnet/minecraft/network/chat/MutableComponent;"))
    private MutableComponent dataEnergistics$formatTrinityCpuTooltipStorage(MutableComponent number,
                                                                            @Local(name = "cpu") CraftingStatusMenu.CraftingCpuListEntry cpu) {
        if (dataEnergistics$isTrinityCpu(cpu)) {
            return dataEnergistics$formatStorageAmount(cpu.storage());
        }
        return number;
    }

    @WrapOperation(
                   method = "drawBackgroundLayer",
                   at = @At(
                            value = "INVOKE",
                            target = "Lappeng/client/gui/widgets/InfoBar;render(Lnet/minecraft/client/gui/GuiGraphics;II)V"))
    private void dataEnergistics$drawTrinityCpuTaskOverlay(InfoBar instance,
                                                           GuiGraphics guiGraphics,
                                                           int xPos,
                                                           int yPos,
                                                           Operation<Void> original,
                                                           @Local(name = "cpu") CraftingStatusMenu.CraftingCpuListEntry cpu,
                                                           @Local(name = "x") int x,
                                                           @Local(name = "y") int y) {
        original.call(instance, guiGraphics, xPos, yPos);
        if (dataEnergistics$isTrinityCpu(cpu) && cpu.currentJob() != null) {
            DATA_ENERGISTICS_CPU_TASK_OVERLAY.copy()
                    .dest(x, y, this.buttonBg.getSrcWidth(), this.buttonBg.getSrcHeight())
                    .blit(guiGraphics);
        }
    }

    @Unique
    private static boolean dataEnergistics$isTrinityCpu(CraftingStatusMenu.CraftingCpuListEntry cpu) {
        Component name = cpu.name();
        return name != null && dataEnergistics$containsTrinityNameKey(name);
    }

    @Unique
    private static MutableComponent dataEnergistics$unlimited() {
        return Component.translatable("gui.data_energistics.trinity.unlimited").withStyle(Tooltips.NUMBER_TEXT);
    }

    @Unique
    private static MutableComponent dataEnergistics$formatStorageAmount(long amount) {
        return amount == Long.MAX_VALUE ? dataEnergistics$unlimited() :
                Component.literal(FormattingUtils.format(amount)).withStyle(Tooltips.NUMBER_TEXT);
    }

    @Unique
    private static boolean dataEnergistics$containsTrinityNameKey(Component component) {
        if (component.getContents() instanceof TranslatableContents contents &&
                DATA_ENERGISTICS_TRINITY_CPU_NAME_KEY.equals(contents.getKey())) {
            return true;
        }
        for (Component sibling : component.getSiblings()) {
            if (dataEnergistics$containsTrinityNameKey(sibling)) {
                return true;
            }
        }
        return false;
    }
}
