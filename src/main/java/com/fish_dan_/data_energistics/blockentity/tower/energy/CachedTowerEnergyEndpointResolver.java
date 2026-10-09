package com.fish_dan_.data_energistics.blockentity.tower.energy;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.blockentity.tower.energy.registry.TowerEnergyEndpointContext;
import com.fish_dan_.data_energistics.blockentity.tower.energy.registry.TowerEnergyEndpointIntegrationRegistry;
import com.fish_dan_.data_energistics.util.ThrowableIsolation;

import appeng.blockentity.networking.CableBusBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.energy.IEnergyStorage;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import org.jspecify.annotations.Nullable;

/**
 * Resolves side-sensitive tower FE endpoints while caching topology until invalidation and directions per game tick.
 */
public final class CachedTowerEnergyEndpointResolver implements TowerEnergyEndpointResolver {

    private final TowerEnergyEndpointResolverContext context;
    private final TowerEnergyEndpointIntegrationRegistry integrations;
    private final ObjectArrayList<TowerEnergyEndpoint> reusableEndpointFilter = new ObjectArrayList<>();
    private ObjectList<TowerEnergyEndpointCandidate> cachedTopologyEndpoints = ObjectList.of();
    private ObjectList<TowerEnergyEndpoint> cachedReceiveEnergyEndpoints = ObjectList.of();
    private ObjectList<TowerEnergyEndpoint> cachedExtractEnergyEndpoints = ObjectList.of();
    private boolean topologyResolutionValid;
    private long directionSnapshotTick = Long.MIN_VALUE;

    /**
     * Creates a resolver backed by one immutable endpoint integration registry.
     */
    public CachedTowerEnergyEndpointResolver(
                                             TowerEnergyEndpointResolverContext context,
                                             TowerEnergyEndpointIntegrationRegistry integrations) {
        this.context = context;
        this.integrations = integrations;
    }

    @Override
    @Nullable
    public IEnergyStorage getEnergyStorageAt(BlockPos pos, @Nullable Direction side) {
        Level level = this.context.level();
        if (level == null || !level.isLoaded(pos) || this.context.isTowerBlock(pos)) {
            return null;
        }

        return this.integrations.findEnergyStorage(level, pos, side);
    }

    @Override
    @Nullable
    public IEnergyStorage findAccessibleEnergyStorage(BlockPos pos, boolean forReceive) {
        ObjectList<TowerEnergyEndpoint> endpoints = findAccessibleEnergyEndpoints(pos, forReceive);
        return endpoints.isEmpty() ? null : endpoints.getFirst().storage();
    }

    @Override
    public ObjectList<TowerEnergyEndpoint> findAccessibleEnergyEndpoints(BlockPos pos, boolean forReceive) {
        return filterByDirection(resolveDirectionalEndpoints(resolveEndpointCandidates(pos)), forReceive);
    }

    @Override
    public ObjectList<TowerEnergyEndpoint> collectEnergyEndpoints(boolean forReceive, @Nullable BlockPos excludedPos) {
        return excludeEnergyEndpoint(getCachedResolvedEnergyEndpoints(forReceive), excludedPos);
    }

    @Override
    public ObjectList<TowerEnergyEndpoint> getCachedResolvedEnergyEndpoints(boolean forReceive) {
        Level level = this.context.level();
        if (level == null) {
            return ObjectList.of();
        }

        if (!this.topologyResolutionValid) {
            this.cachedTopologyEndpoints = resolveTopologyEndpoints();
            this.topologyResolutionValid = true;
        }

        long gameTime = level.getGameTime();
        if (this.directionSnapshotTick != gameTime) {
            ObjectList<TowerEnergyEndpoint> directionalEndpoints = resolveDirectionalEndpoints(this.cachedTopologyEndpoints);
            this.cachedReceiveEnergyEndpoints = filterByDirection(directionalEndpoints, true);
            this.cachedExtractEnergyEndpoints = filterByDirection(directionalEndpoints, false);
            this.directionSnapshotTick = gameTime;
        }
        return forReceive ? this.cachedReceiveEnergyEndpoints : this.cachedExtractEnergyEndpoints;
    }

    @Override
    @Nullable
    public BlockPos normalizeExtractExcludedPos(@Nullable BlockPos excludedPos) {
        return normalizeExcludedPos(excludedPos, false);
    }

    @Override
    @Nullable
    public BlockPos normalizeReceiveExcludedPos(@Nullable BlockPos excludedPos) {
        return normalizeExcludedPos(excludedPos, true);
    }

    @Override
    public boolean canReceiveEnergy(@Nullable IEnergyStorage storage) {
        return storage != null && canReceive(storage);
    }

    @Override
    public void invalidateResolvedCache() {
        this.cachedTopologyEndpoints = ObjectList.of();
        this.cachedReceiveEnergyEndpoints = ObjectList.of();
        this.cachedExtractEnergyEndpoints = ObjectList.of();
        this.topologyResolutionValid = false;
        this.directionSnapshotTick = Long.MIN_VALUE;
    }

    @Override
    public void clearReusableCache() {
        this.reusableEndpointFilter.clear();
    }

    private ObjectList<TowerEnergyEndpointCandidate> resolveTopologyEndpoints() {
        Object2ObjectLinkedOpenHashMap<TowerEnergyEndpointKey, TowerEnergyEndpointCandidate> endpoints = new Object2ObjectLinkedOpenHashMap<>();
        for (long packedPos : this.context.cachedEndpointPositions()) {
            BlockPos pos = BlockPos.of(packedPos);
            if (!this.context.targetAllowsFe(pos)) {
                continue;
            }

            boolean receiveExcluded = this.context.isDedicatedAeGridTarget(pos);
            for (TowerEnergyEndpointCandidate endpoint : resolveEndpointCandidates(pos)) {
                TowerEnergyEndpointCandidate candidate = endpoint.withReceiveExcluded(receiveExcluded);
                endpoints.merge(
                        new TowerEnergyEndpointKey(candidate.pos(), candidate.side()),
                        candidate,
                        TowerEnergyEndpointCandidate::mergeReceiveAccess);
            }
        }
        return new ObjectImmutableList<>(endpoints.values());
    }

    private ObjectList<TowerEnergyEndpointCandidate> resolveEndpointCandidates(BlockPos pos) {
        Level level = this.context.level();
        if (level == null || !level.isLoaded(pos)) {
            return ObjectList.of();
        }

        ObjectArrayList<TowerEnergyEndpointCandidate> endpoints = new ObjectArrayList<>();
        ReferenceOpenHashSet<IEnergyStorage> seenStorages = new ReferenceOpenHashSet<>();
        boolean collectAllSides = level.getBlockEntity(pos) instanceof CableBusBlockEntity;
        for (Direction direction : Direction.values()) {
            addEndpointCandidate(endpoints, seenStorages, pos, direction, collectAllSides);
        }
        if (endpoints.isEmpty()) {
            addEndpointCandidate(endpoints, seenStorages, pos, null, collectAllSides);
        }
        return new ObjectImmutableList<>(endpoints);
    }

    private void addEndpointCandidate(ObjectList<TowerEnergyEndpointCandidate> endpoints, ReferenceOpenHashSet<IEnergyStorage> seenStorages,
                                      BlockPos pos, @Nullable Direction side, boolean collectAllSides) {
        IEnergyStorage storage = getEnergyStorageAt(pos, side);
        if (storage != null && seenStorages.add(storage)) {
            endpoints.add(new TowerEnergyEndpointCandidate(pos.immutable(), side, storage, collectAllSides, false));
        }
    }

    private ObjectList<TowerEnergyEndpoint> resolveDirectionalEndpoints(ObjectList<TowerEnergyEndpointCandidate> candidates) {
        ObjectArrayList<TowerEnergyEndpoint> endpoints = new ObjectArrayList<>();
        LongSet selectedSources = new LongOpenHashSet();
        LongSet selectedSinks = new LongOpenHashSet();
        for (TowerEnergyEndpointCandidate candidate : candidates) {
            TowerEnergyDirection direction;
            try {
                direction = resolveTransferDirection(candidate);
            } catch (Throwable exception) {
                ThrowableIsolation.rethrowIfFatal(exception);
                Data_Energistics.LOGGER.error(
                        "Failed to resolve tower energy directions at {} side {} storage {}",
                        candidate.pos(), candidate.side(), candidate.storage().getClass().getName(), exception);
                continue;
            }
            if (direction == null) {
                continue;
            }

            boolean canUseSource = direction.allowsExtract();
            boolean canUseSink = direction.allowsReceive() && !candidate.receiveExcluded();
            TowerEnergyDirection usableDirection = TowerEnergyDirection.fromPermissions(canUseSource, canUseSink);
            if (usableDirection == null) {
                continue;
            }

            if (candidate.collectAllSides()) {
                endpoints.add(candidate.withDirection(usableDirection));
                continue;
            }

            long candidatePosition = candidate.pos().asLong();
            boolean selectSource = canUseSource && selectedSources.add(candidatePosition);
            boolean selectSink = canUseSink && selectedSinks.add(candidatePosition);
            TowerEnergyDirection selectedDirection = TowerEnergyDirection.fromPermissions(selectSource, selectSink);
            if (selectedDirection != null) {
                endpoints.add(candidate.withDirection(selectedDirection));
            }
        }
        return new ObjectImmutableList<>(endpoints);
    }

    @Nullable
    private TowerEnergyDirection resolveTransferDirection(TowerEnergyEndpointCandidate candidate) {
        Level level = this.context.level();
        if (level == null) {
            return null;
        }
        TowerEnergyEndpointContext endpointContext = new TowerEnergyEndpointContext(
                level, candidate.pos(), candidate.side(), candidate.storage());
        return this.integrations.resolve(endpointContext).direction(endpointContext);
    }

    private boolean canReceive(IEnergyStorage storage) {
        return storage.canReceive();
    }

    private ObjectList<TowerEnergyEndpoint> filterByDirection(ObjectList<TowerEnergyEndpoint> endpoints, boolean forReceive) {
        if (endpoints.isEmpty()) {
            return endpoints;
        }
        ObjectArrayList<TowerEnergyEndpoint> filtered = new ObjectArrayList<>(endpoints.size());
        for (TowerEnergyEndpoint endpoint : endpoints) {
            if (forReceive ? endpoint.direction().allowsReceive() : endpoint.direction().allowsExtract()) {
                filtered.add(endpoint);
            }
        }
        return new ObjectImmutableList<>(filtered);
    }

    private ObjectList<TowerEnergyEndpoint> excludeEnergyEndpoint(ObjectList<TowerEnergyEndpoint> endpoints,
                                                                  @Nullable BlockPos excludedPos) {
        if (excludedPos == null || endpoints.isEmpty()) {
            return endpoints;
        }

        this.reusableEndpointFilter.clear();
        for (TowerEnergyEndpoint endpoint : endpoints) {
            if (!excludedPos.equals(endpoint.pos())) {
                this.reusableEndpointFilter.add(endpoint);
            }
        }
        return new ObjectImmutableList<>(this.reusableEndpointFilter);
    }

    @Nullable
    private BlockPos normalizeExcludedPos(@Nullable BlockPos excludedPos, boolean forReceive) {
        BlockPos normalizedExcludedPos = excludedPos == null ? null : excludedPos.immutable();
        if (normalizedExcludedPos == null) {
            return null;
        }

        for (TowerEnergyEndpoint endpoint : getCachedResolvedEnergyEndpoints(forReceive)) {
            if (normalizedExcludedPos.equals(endpoint.pos())) {
                return normalizedExcludedPos;
            }
        }
        return null;
    }

    private record TowerEnergyEndpointCandidate(BlockPos pos, @Nullable Direction side, IEnergyStorage storage,
                                                boolean collectAllSides, boolean receiveExcluded) {

        private TowerEnergyEndpointCandidate withReceiveExcluded(boolean receiveExcluded) {
            return new TowerEnergyEndpointCandidate(
                    this.pos, this.side, this.storage, this.collectAllSides, receiveExcluded);
        }

        private TowerEnergyEndpointCandidate mergeReceiveAccess(TowerEnergyEndpointCandidate other) {
            return withReceiveExcluded(this.receiveExcluded && other.receiveExcluded);
        }

        private TowerEnergyEndpoint withDirection(TowerEnergyDirection direction) {
            return new TowerEnergyEndpoint(this.pos, this.side, this.storage, direction);
        }
    }

    private record TowerEnergyEndpointKey(BlockPos pos, @Nullable Direction side) {}
}
