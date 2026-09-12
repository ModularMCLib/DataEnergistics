package com.fish_dan_.data_energistics.ae2.patternprovider.adaptive;

import com.fish_dan_.data_energistics.api.registry.adaptive.AdaptivePatternProviderDispatch;
import com.fish_dan_.data_energistics.api.registry.adaptive.AdaptivePatternProviderDispatchTarget;
import com.fish_dan_.data_energistics.api.registry.adaptive.AdaptivePatternProviderRegistration;
import com.fish_dan_.data_energistics.api.registry.adaptive.AdaptiveProviderConnectorBinding;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.helpers.patternprovider.PatternProviderReturnInventory;
import appeng.helpers.patternprovider.PatternProviderTarget;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Named generic runtime target used by adaptive registrations.
 *
 * <p>
 * This adapter contains no integration knowledge. It only forwards the
 * provider mechanics exposed by {@link AdaptivePatternProviderLogic} to the
 * public dispatch contract.
 * </p>
 */
final class AdaptivePatternProviderRuntimeTarget implements AdaptivePatternProviderDispatchTarget {

    private final AdaptivePatternProviderLogic logic;
    private final AdaptivePatternProviderRegistration registration;
    private @Nullable Object state;

    AdaptivePatternProviderRuntimeTarget(
                                         AdaptivePatternProviderLogic logic,
                                         AdaptivePatternProviderRegistration registration) {
        this.logic = logic;
        this.registration = registration;
    }

    @Override
    public ResourceLocation registrationId() {
        return this.registration.registrationId();
    }

    @Override
    public boolean isSelected() {
        return this.logic.adaptiveIsSelected(this.registration);
    }

    AdaptivePatternProviderDispatch dispatch() {
        return this.registration.dispatch();
    }

    @Override
    public boolean pushDefault(IPatternDetails patternDetails, KeyCounter[] inputHolder) {
        return this.logic.adaptivePushDefault(patternDetails, inputHolder);
    }

    @Override
    public boolean isBusy() {
        return this.logic.adaptiveIsBusy();
    }

    @Override
    public boolean isActive() {
        return this.logic.adaptiveIsActive();
    }

    @Override
    public boolean hasPattern(IPatternDetails patternDetails) {
        return this.logic.adaptiveHasPattern(patternDetails);
    }

    @Override
    public boolean isCraftingLocked() {
        return this.logic.adaptiveIsCraftingLocked();
    }

    @Override
    public @Nullable Level level() {
        return this.logic.adaptiveLevel();
    }

    @Override
    public BlockPos providerPos() {
        return this.logic.adaptiveProviderPos();
    }

    @Override
    public List<Direction> targetSides() {
        return this.logic.adaptiveTargetSides();
    }

    @Override
    public void patternSuccess(IPatternDetails patternDetails) {
        this.logic.adaptivePatternSuccess(patternDetails);
    }

    @Override
    public IActionSource actionSource() {
        return this.logic.adaptiveActionSource();
    }

    @Override
    public PatternProviderReturnInventory returnInventory() {
        return this.logic.adaptiveReturnInventory();
    }

    @Override
    public @Nullable MEStorage networkStorage() {
        return this.logic.adaptiveNetworkStorage();
    }

    @Override
    public @Nullable IEnergyService energyService() {
        return this.logic.adaptiveEnergyService();
    }

    @Override
    public boolean isBlocking() {
        return this.logic.adaptiveIsBlocking();
    }

    @Override
    public Set<AEKey> patternInputs() {
        return this.logic.adaptivePatternInputs();
    }

    @Override
    public @Nullable PatternProviderTarget externalTarget(Level level, BlockPos position, Direction side) {
        return this.logic.adaptiveExternalTarget(level, position, side);
    }

    @Override
    public @Nullable PatternProviderTarget resolvedTarget(GlobalPos target, Direction face, ServerLevel sourceLevel) {
        return this.logic.adaptiveResolvedTarget(target, face, sourceLevel);
    }

    @Override
    public boolean isBlocked(PatternProviderTarget target) {
        return this.logic.adaptiveIsBlocked(target);
    }

    @Override
    public int roundRobinIndex() {
        return this.logic.adaptiveRoundRobinIndex();
    }

    @Override
    public void advanceRoundRobin(int amount) {
        this.logic.adaptiveAdvanceRoundRobin(amount);
    }

    @Override
    public void saveChanges() {
        this.logic.adaptiveSaveChanges();
    }

    @Override
    public void alertDevice() {
        this.logic.adaptiveAlertDevice();
    }

    @Override
    public boolean isPullModeEnabled() {
        return this.logic.adaptiveIsPullModeEnabled();
    }

    @Override
    public boolean isFilteredImportEnabled() {
        return this.logic.adaptiveIsFilteredImportEnabled();
    }

    @Override
    public Set<AEKey> trackedCrafts() {
        return this.logic.getTrackedCrafts();
    }

    @Override
    public Set<AEKey> outputCache() {
        return this.logic.getOutputCache();
    }

    @Override
    public boolean hasAvailableNativeSlot(IPatternDetails patternDetails) {
        return this.logic.adaptiveHasAvailableNativeSlot(patternDetails);
    }

    @Override
    public int reusableWorkCount() {
        return this.logic.adaptiveReusableWorkCount();
    }

    @Override
    public void addReusableWork(int amount) {
        this.logic.adaptiveAddReusableWork(amount);
    }

    @Override
    public long pendingReusableOperations() {
        return this.logic.adaptivePendingReusableOperations();
    }

    @Override
    public boolean reusableHandoffPrepared() {
        return this.logic.adaptiveReusableHandoffPrepared();
    }

    @Override
    public int installedSpeedCardCount() {
        return this.logic.adaptiveInstalledSpeedCardCount();
    }

    @Override
    public List<AdaptiveProviderConnectorBinding> connectorBindings() {
        return this.logic.adaptiveConnectorBindings();
    }

    @Override
    public <T> T routeState(Class<T> stateType, Supplier<? extends T> factory) {
        if (this.state == null) {
            this.state = Objects.requireNonNull(factory.get(), "Route state factory returned null");
        }
        return stateType.cast(this.state);
    }

    @Override
    public void clearRouteState() {
        this.state = null;
    }

    @Override
    public boolean isPatternProviderAttachment(Level level, BlockPos position, @Nullable Direction side) {
        return AdaptivePatternProviderResolver.isPatternProviderAttachment(level, position, side);
    }
}
