package com.fish_dan_.data_energistics.mixin.ae.ae2.menu.crafting;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.CraftingQuantityMode;
import com.fish_dan_.data_energistics.common.menu.crafting.TrinityCraftAmountMenuState;
import com.fish_dan_.data_energistics.common.menu.crafting.TrinityCraftConfirmMenuState;
import com.fish_dan_.data_energistics.configuration.DataEnergisticsConfiguration;
import com.fish_dan_.data_energistics.util.amount.AmountExpressionParser;

import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.stacks.AEKey;
import appeng.api.storage.ISubMenuHost;
import appeng.menu.AEBaseMenu;
import appeng.menu.MenuOpener;
import appeng.menu.guisync.GuiSync;
import appeng.menu.me.crafting.CraftAmountMenu;
import appeng.menu.me.crafting.CraftConfirmMenu;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.math.BigInteger;

/**
 * Adds request-scoped quantity semantics without changing AE2's original confirmation packet.
 */
@Mixin(CraftAmountMenu.class)
public abstract class CraftAmountMenuMixin extends AEBaseMenu implements TrinityCraftAmountMenuState {

    @Unique
    private static final String DATA_ENERGISTICS_ACTION_SET_QUANTITY_MODE = "data_energistics$set_quantity_mode";

    @Unique
    private static final String DATA_ENERGISTICS_ACTION_CONFIRM_EXACT = "data_energistics$confirm_exact";

    @Shadow
    @Nullable
    private AEKey whatToCraft;

    @GuiSync(791)
    @Unique
    public int dataEnergistics$quantityMode = DataEnergisticsConfiguration.INSTANCE.trinity.crafting.defaultQuantityMode.ordinal();

    @GuiSync(792)
    @Unique
    public String dataEnergistics$initialAmount = "1";

    protected CraftAmountMenuMixin(MenuType<?> menuType, int id, Inventory playerInventory, Object host) {
        super(menuType, id, playerInventory, host);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void dataEnergistics$registerQuantityModeAction(
                                                            int id,
                                                            Inventory ip,
                                                            ISubMenuHost host,
                                                            CallbackInfo ci) {
        this.registerClientAction(
                DATA_ENERGISTICS_ACTION_SET_QUANTITY_MODE,
                Integer.class,
                this::dataEnergistics$applyQuantityMode);
        this.registerClientAction(
                DATA_ENERGISTICS_ACTION_CONFIRM_EXACT,
                Confirmation.class,
                this::dataEnergistics$applyConfirmation);
    }

    @Inject(method = "setWhatToCraft", at = @At("RETURN"))
    private void dataEnergistics$rememberInitialAmount(AEKey whatToCraft, int initialAmount, CallbackInfo ci) {
        this.dataEnergistics$initialAmount = Integer.toString(Math.max(1, initialAmount));
    }

    @ModifyVariable(method = "confirm", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private boolean dataEnergistics$preserveFinalTotalAmount(boolean craftMissingAmount) {
        return data_energistics$quantityMode().appliesAe2MissingAmountAdjustment(craftMissingAmount);
    }

    @WrapOperation(
                   method = "confirm",
                   at = @At(
                            value = "INVOKE",
                            target = "Lappeng/menu/me/crafting/CraftConfirmMenu;planJob(Lappeng/api/stacks/AEKey;ILappeng/api/networking/crafting/CalculationStrategy;)Z"))
    private boolean dataEnergistics$transferQuantityMode(
                                                         CraftConfirmMenu confirmation,
                                                         AEKey what,
                                                         int amount,
                                                         CalculationStrategy strategy,
                                                         Operation<Boolean> original) {
        ((TrinityCraftConfirmMenuState) confirmation)
                .data_energistics$setQuantityMode(data_energistics$quantityMode());
        return original.call(confirmation, what, amount, strategy);
    }

    @Override
    public CraftingQuantityMode data_energistics$quantityMode() {
        return CraftingQuantityMode.fromOrdinal(this.dataEnergistics$quantityMode);
    }

    @Override
    public void data_energistics$setQuantityMode(CraftingQuantityMode quantityMode) {
        this.dataEnergistics$quantityMode = quantityMode.ordinal();
        if (this.isClientSide()) {
            this.sendClientAction(DATA_ENERGISTICS_ACTION_SET_QUANTITY_MODE, quantityMode.ordinal());
        } else {
            this.broadcastChanges();
        }
    }

    @Override
    public void data_energistics$confirm(String amountExpression, boolean craftMissingAmount, boolean autoStart) {
        Confirmation confirmation = new Confirmation(amountExpression, craftMissingAmount, autoStart);
        if (this.isClientSide()) {
            this.sendClientAction(DATA_ENERGISTICS_ACTION_CONFIRM_EXACT, confirmation);
        } else {
            dataEnergistics$applyConfirmation(confirmation);
        }
    }

    @Override
    public BigInteger data_energistics$initialAmount() {
        return new BigInteger(this.dataEnergistics$initialAmount);
    }

    @Override
    public void data_energistics$setInitialAmount(BigInteger amount) {
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("A crafting amount must be positive");
        }
        this.dataEnergistics$initialAmount = amount.toString();
        if (!this.isClientSide()) {
            this.broadcastChanges();
        }
    }

    @Unique
    private void dataEnergistics$applyQuantityMode(Integer ordinal) {
        if (ordinal < 0 || ordinal >= CraftingQuantityMode.values().length) {
            Data_Energistics.LOGGER.warn("Rejected invalid Trinity quantity mode client action {}", ordinal);
            return;
        }
        this.dataEnergistics$quantityMode = CraftingQuantityMode.fromOrdinal(ordinal).ordinal();
        this.broadcastChanges();
    }

    @Unique
    private void dataEnergistics$applyConfirmation(@Nullable Confirmation confirmation) {
        if (confirmation == null || confirmation.amountExpression() == null) {
            Data_Energistics.LOGGER.warn("Rejected crafting confirmation without an amount expression");
            return;
        }
        BigInteger requestedAmount = AmountExpressionParser
                .parseUnboundedPositiveInteger(confirmation.amountExpression())
                .orElse(null);
        if (requestedAmount == null || requestedAmount.signum() <= 0) {
            Data_Energistics.LOGGER.warn("Rejected invalid exact crafting confirmation {}", confirmation);
            return;
        }
        if (this.whatToCraft == null) {
            Data_Energistics.LOGGER.warn("Rejected a crafting confirmation before its requested key was synchronized");
            return;
        }

        boolean useExactTrinityPlanning = requestedAmount.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0;
        BigInteger amount = requestedAmount;
        boolean adjustMissing = data_energistics$quantityMode()
                .appliesAe2MissingAmountAdjustment(confirmation.craftMissingAmount());
        if (adjustMissing) {
            var actionHost = this.getActionHost();
            if (actionHost != null) {
                var node = actionHost.getActionableNode();
                if (node != null) {
                    long existingAmount = node.getGrid()
                            .getStorageService()
                            .getCachedInventory()
                            .get(this.whatToCraft);
                    amount = amount.subtract(BigInteger.valueOf(existingAmount).max(BigInteger.ZERO))
                            .max(BigInteger.ZERO);
                }
            }
        }

        var locator = this.getLocator();
        if (locator == null) {
            Data_Energistics.LOGGER.warn("Rejected a crafting confirmation because its menu locator is unavailable");
            return;
        }

        CraftAmountMenu self = (CraftAmountMenu) (Object) this;
        if (!(this.getPlayer() instanceof ServerPlayer player)) {
            Data_Energistics.LOGGER.error("Long-sized crafting confirmation reached a non-server player");
            return;
        }
        if (amount.signum() <= 0) {
            self.getHost().returnToMainMenu(player, self);
            return;
        }

        MenuOpener.open(CraftConfirmMenu.TYPE, player, locator);
        if (!(player.containerMenu instanceof CraftConfirmMenu confirmationMenu)) {
            Data_Energistics.LOGGER.error("Craft confirmation menu did not open for a long-sized request");
            return;
        }

        confirmationMenu.setAutoStart(confirmation.autoStart());
        TrinityCraftConfirmMenuState confirmationState = (TrinityCraftConfirmMenuState) confirmationMenu;
        confirmationState.data_energistics$setQuantityMode(data_energistics$quantityMode());
        boolean planned = useExactTrinityPlanning ?
                confirmationState.data_energistics$planJob(
                        this.whatToCraft,
                        amount,
                        CalculationStrategy.REPORT_MISSING_ITEMS) :
                confirmationState.data_energistics$planJob(
                        this.whatToCraft,
                        amount.longValueExact(),
                        CalculationStrategy.REPORT_MISSING_ITEMS);
        if (!planned) {
            confirmationMenu.setValidMenu(false);
            return;
        }
        confirmationMenu.broadcastChanges();
    }
}
