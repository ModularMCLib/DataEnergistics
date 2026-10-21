package com.fish_dan_.data_energistics.integration.magic.goety.digitalsupply;

import com.fish_dan_.data_energistics.api.registry.digitalsupply.DigitalSupplyInterfaceTarget;
import com.fish_dan_.data_energistics.api.registry.digitalsupply.DigitalSupplyTransferContext;
import com.fish_dan_.data_energistics.integration.ae.ae2.key.ExperienceKey;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import com.Polarice3.Goety.common.ritual.EnchantItemRitual;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;

/** Supplies raw AE experience points to Goety ritual casters with an atomic multi-grid transaction. */
public final class GoetyExperienceSupply {

    private static final int SEARCH_RADIUS = 7;
    private static final long MAX_TRANSFER = Integer.MAX_VALUE;

    private GoetyExperienceSupply() {}

    /**
     * Ensures that a caster can pay every remaining native level debit for an enchant ritual.
     *
     * <p>
     * The target level is one above the remaining cost because Goety's native tick requires the caster to remain
     * above level one before it calls {@code giveExperienceLevels(-1)}.
     * </p>
     */
    public static boolean ensureExperience(ServerLevel level, BlockPos altarPosition,
                                           EnchantItemRitual ritual, ItemStack activation,
                                           Player caster, int experienceTaken) {
        int levelCost = Math.max(0, ritual.getLevelCost(activation));
        int remainingLevels = Math.max(0, levelCost - Math.max(0, experienceTaken));
        if (remainingLevels == 0) {
            return true;
        }
        long requiredPoints = rawExperienceToLevel(caster, remainingLevels + 1);
        if (requiredPoints <= 0L) {
            return true;
        }
        return transferExperience(level, altarPosition, caster, requiredPoints);
    }

    /** Returns the raw points needed to reach an absolute player level without changing the player. */
    public static long rawExperienceToLevel(Player player, int targetLevel) {
        if (targetLevel <= player.experienceLevel) {
            return 0L;
        }
        long points = (long) Math.ceil(
                Math.max(0.0D, (1.0D - player.experienceProgress) * player.getXpNeededForNextLevel()));
        int level = player.experienceLevel + 1;
        while (level < targetLevel) {
            points = Math.addExact(points, xpNeededForLevel(level));
            level++;
        }
        return points;
    }

    private static boolean transferExperience(ServerLevel level, BlockPos center, Player caster, long requested) {
        if (requested <= 0L || requested > MAX_TRANSFER) {
            return false;
        }
        ObjectList<Supply> supplies = findSupplies(level, center);
        if (supplies.isEmpty()) {
            return false;
        }

        long simulatedTotal = 0L;
        for (Supply supply : supplies) {
            long simulated = supply.context().simulateNetworkExtract(ExperienceKey.INSTANCE, requested);
            if (simulated < 0L || simulated > requested) {
                throw new IllegalStateException("Digital Supply Interface returned an invalid simulated experience amount");
            }
            supply.simulated(simulated);
            simulatedTotal = Math.addExact(simulatedTotal, simulated);
        }
        if (simulatedTotal < requested) {
            return false;
        }

        long remaining = requested;
        try {
            for (Supply supply : supplies) {
                long planned = Math.min(supply.simulated(), remaining);
                if (planned <= 0L) {
                    continue;
                }
                long extracted = supply.context().commitNetworkExtract(ExperienceKey.INSTANCE, planned);
                if (extracted < 0L || extracted > planned) {
                    throw new IllegalStateException("Digital Supply Interface returned an invalid experience amount");
                }
                supply.committed(extracted);
                if (extracted != planned) {
                    throw new IllegalStateException("Digital Supply Interface experience changed during an atomic commit");
                }
                remaining -= extracted;
                if (remaining == 0L) {
                    break;
                }
            }
            if (remaining != 0L) {
                throw new IllegalStateException("Digital Supply Interface experience commit was incomplete");
            }
            caster.giveExperiencePoints(Math.toIntExact(requested));
            return true;
        } catch (RuntimeException exception) {
            rollback(supplies, exception);
            return false;
        }
    }

    private static ObjectList<Supply> findSupplies(ServerLevel level, BlockPos center) {
        Reference2ObjectOpenHashMap<IGrid, Supply> byGrid = new Reference2ObjectOpenHashMap<>();
        BlockPos min = center.offset(-SEARCH_RADIUS, -SEARCH_RADIUS, -SEARCH_RADIUS);
        BlockPos max = center.offset(SEARCH_RADIUS, SEARCH_RADIUS, SEARCH_RADIUS);
        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int y = min.getY(); y <= max.getY(); y++) {
                for (int z = min.getZ(); z <= max.getZ(); z++) {
                    BlockPos position = new BlockPos(x, y, z);
                    if (!level.isLoaded(position)) {
                        continue;
                    }
                    BlockEntity blockEntity = level.getBlockEntity(position);
                    if (!(blockEntity instanceof DigitalSupplyInterfaceTarget target)) {
                        continue;
                    }
                    IGridNode node = target.gridNode();
                    if (node == null || !node.isActive() || node.getGrid() == null || target.networkStorage() == null) {
                        continue;
                    }
                    IGrid grid = node.getGrid();
                    byGrid.putIfAbsent(grid, new Supply(target.transferContext()));
                }
            }
        }
        return new ObjectArrayList<>(byGrid.values());
    }

    private static void rollback(ObjectList<Supply> supplies, RuntimeException cause) {
        RuntimeException failure = null;
        for (int index = supplies.size() - 1; index >= 0; index--) {
            Supply supply = supplies.get(index);
            if (supply.committed() <= 0L) {
                continue;
            }
            try {
                long restored = supply.context().commitNetworkInsert(ExperienceKey.INSTANCE, supply.committed());
                if (restored != supply.committed()) {
                    failure = new IllegalStateException("Failed to roll back committed Goety experience", cause);
                    break;
                }
            } catch (RuntimeException exception) {
                failure = new IllegalStateException("Failed to roll back committed Goety experience", exception);
                break;
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    private static int xpNeededForLevel(int level) {
        if (level >= 30) {
            return 112 + (level - 30) * 9;
        }
        if (level >= 15) {
            return 37 + (level - 15) * 5;
        }
        return 7 + level * 2;
    }

    private static final class Supply {

        private final DigitalSupplyTransferContext context;
        private long simulated;
        private long committed;

        private Supply(DigitalSupplyTransferContext context) {
            this.context = context;
        }

        private DigitalSupplyTransferContext context() {
            return this.context;
        }

        private long simulated() {
            return this.simulated;
        }

        private void simulated(long amount) {
            this.simulated = amount;
        }

        private long committed() {
            return this.committed;
        }

        private void committed(long amount) {
            this.committed = amount;
        }
    }
}
