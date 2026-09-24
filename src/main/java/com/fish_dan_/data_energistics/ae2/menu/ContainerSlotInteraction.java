package com.fish_dan_.data_energistics.ae2.menu;

import com.fish_dan_.data_energistics.Data_Energistics;

import appeng.api.behaviors.ContainerItemContext;
import appeng.api.behaviors.ContainerItemStrategies;
import appeng.api.config.Actionable;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.helpers.externalstorage.GenericStackInv;
import appeng.menu.AEBaseMenu;
import appeng.menu.slot.AppEngSlot;
import appeng.menu.slot.FakeSlot;
import appeng.util.ConfigMenuInventory;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jspecify.annotations.Nullable;

import java.util.function.ToIntFunction;

/**
 * Transfers the content of registered container items through generic AE2 storage slots.
 *
 * <p>
 * AE2's explicit container actions already use {@link ContainerItemStrategies}, but ordinary menu clicks and
 * quick-move actions normally pass through {@link ConfigMenuInventory#convertToSuitableStack(ItemStack)}. This class
 * keeps those paths consistent without placing a container item in a generic-stack slot.
 * </p>
 */
public final class ContainerSlotInteraction {

    private static final int MAX_CONTAINER_TRANSFER_ITERATIONS = 64;

    private ContainerSlotInteraction() {}

    /**
     * Handles a normal click on a storage slot backed by a generic-stack inventory.
     *
     * @return {@code true} only when content was transferred and the original click must be cancelled
     */
    public static boolean tryClicked(AEBaseMenu menu, int slotId, int button, ClickType clickType, Player player) {
        if (clickType != ClickType.PICKUP || menu.isClientSide() || slotId < 0 || slotId >= menu.slots.size()) {
            return false;
        }

        Slot slot = menu.slots.get(slotId);
        if (menu.isPlayerSideSlot(slot) || !(slot instanceof AppEngSlot appEngSlot) || appEngSlot instanceof FakeSlot || !(appEngSlot.getInventory() instanceof ConfigMenuInventory configInventory)) {
            return false;
        }

        return tryClicked(menu, appEngSlot, configInventory.getDelegate(), appEngSlot.getContainerSlot(), button, player);
    }

    /**
     * Handles a normal click when a menu exposes a logical slot backed by a different physical generic-stack slot.
     * This is used by paged menus whose visible slot always has container index zero.
     */
    public static boolean tryClicked(AEBaseMenu menu, AppEngSlot slot, GenericStackInv inventory, int inventorySlot,
                                     int button, Player player) {
        if (menu.isClientSide() || menu.isPlayerSideSlot(slot) || slot instanceof FakeSlot || !slot.isActive() || inventory.getMode() != GenericStackInv.Mode.STORAGE) {
            return false;
        }

        Target target = target(inventory, inventorySlot);
        if (target == null || menu.getCarried().isEmpty()) {
            return false;
        }

        long transferred;
        if (button == 0) {
            GenericStack current = target.stack();
            ContainerItemContext fillingContext = current == null || !isTransferKey(current.what()) ? null : ContainerItemStrategies.findCarriedContextForKey(current.what(), player, menu);
            transferred = fillingContext == null ? 0 : transferIntoContainer(target, fillingContext, player, false);
        } else {
            ContainerItemContext context = ContainerItemStrategies.findCarriedContext(null, player, menu);
            if (context == null) {
                return false;
            }

            GenericStack contained = context.getExtractableContent();
            transferred = contained == null || contained.what() == null || contained.amount() <= 0 ? 0 : transferIntoSlot(target, context, contained.what(), contained.amount(), player, false);
        }

        if (transferred <= 0) {
            return false;
        }

        menu.broadcastChanges();
        return true;
    }

    /**
     * Handles shift-clicking an item from the player's inventory into the first compatible generic-stack slot.
     * Existing matching keys are considered before empty slots.
     */
    public static boolean tryQuickMove(AEBaseMenu menu, int slotIndex, Player player) {
        return tryQuickMove(menu, slotIndex, player, AppEngSlot::getContainerSlot);
    }

    /**
     * Handles shift-clicking with a menu-specific physical index resolver for paged storage slots.
     */
    public static boolean tryQuickMove(AEBaseMenu menu, int slotIndex, Player player,
                                       ToIntFunction<AppEngSlot> inventorySlotResolver) {
        if (menu.isClientSide() || slotIndex < 0 || slotIndex >= menu.slots.size()) {
            return false;
        }

        Slot source = menu.slots.get(slotIndex);
        if (!menu.isPlayerSideSlot(source) || source instanceof FakeSlot) {
            return false;
        }

        ItemStack sourceStack = source.getItem();
        if (sourceStack.isEmpty()) {
            return false;
        }

        ContainerItemContext context = ContainerItemStrategies.findOwnedItemContext(null, player, sourceStack);
        if (context == null) {
            return false;
        }

        GenericStack contained = context.getExtractableContent();
        ObjectArrayList<Target> matchingTargets = new ObjectArrayList<>();
        ObjectArrayList<Target> emptyTargets = new ObjectArrayList<>();
        ObjectArrayList<Target> filledTargets = new ObjectArrayList<>();
        collectTargets(menu, source, contained, inventorySlotResolver, matchingTargets, emptyTargets, filledTargets);

        long transferred = 0;
        if (contained != null && contained.what() != null && contained.amount() > 0) {
            for (Target target : matchingTargets) {
                transferred = transferIntoSlot(target, context, contained.what(), contained.amount(), player, true);
                if (transferred > 0) {
                    break;
                }
            }
            if (transferred <= 0) {
                for (Target target : emptyTargets) {
                    transferred = transferIntoSlot(target, context, contained.what(), contained.amount(), player, true);
                    if (transferred > 0) {
                        break;
                    }
                }
            }
        } else {
            for (Target target : filledTargets) {
                GenericStack current = target.stack();
                ContainerItemContext fillingContext = current == null || !isTransferKey(current.what()) ? null : ContainerItemStrategies.findOwnedItemContext(current.what().getType(), player, sourceStack);
                transferred = fillingContext == null ? 0 : transferIntoContainer(target, fillingContext, player, true);
                if (transferred > 0) {
                    break;
                }
            }
        }

        if (transferred <= 0) {
            return false;
        }

        source.setChanged();
        menu.broadcastChanges();
        return true;
    }

    private static void collectTargets(AEBaseMenu menu, Slot source, @Nullable GenericStack contained,
                                       ToIntFunction<AppEngSlot> inventorySlotResolver, ObjectArrayList<Target> matchingTargets,
                                       ObjectArrayList<Target> emptyTargets, ObjectArrayList<Target> filledTargets) {
        for (Slot candidate : menu.slots) {
            if (candidate == source || menu.isPlayerSideSlot(candidate)) {
                continue;
            }

            Target target = target(candidate, inventorySlotResolver);
            if (target == null) {
                continue;
            }

            GenericStack current = target.stack();
            if (contained == null || contained.what() == null || contained.amount() <= 0) {
                if (current != null && isTransferKey(current.what())) {
                    filledTargets.add(target);
                }
            } else if (current == null) {
                emptyTargets.add(target);
            } else if (current.what().equals(contained.what())) {
                matchingTargets.add(target);
            }
        }
    }

    private static long transferIntoSlot(Target target, ContainerItemContext context, AEKey what, long available,
                                         Player player, boolean transferAll) {
        if (!isTransferKey(what) || !target.accepts(what)) {
            return 0;
        }

        long total = 0;
        int iterations = transferAll ? MAX_CONTAINER_TRANSFER_ITERATIONS : 1;
        while (iterations-- > 0) {
            long requested = transferAll ? Long.MAX_VALUE : what.getAmountPerUnit();
            long sourceAvailable = context.extract(what, Math.min(requested, available), Actionable.SIMULATE);
            if (sourceAvailable <= 0) {
                break;
            }

            long targetAvailable = target.insert(what, sourceAvailable, Actionable.SIMULATE);
            long amount = Math.min(sourceAvailable, targetAvailable);
            if (amount <= 0) {
                break;
            }

            long extracted = context.extract(what, amount, Actionable.MODULATE);
            if (extracted != amount) {
                restoreContainer(context, what, extracted);
                logSimulationMismatch("emptying", target, what, amount, extracted);
                break;
            }

            long inserted = target.insert(what, extracted, Actionable.MODULATE);
            if (inserted != extracted) {
                long accepted = Math.min(extracted, Math.max(0, inserted));
                restoreContainer(context, what, extracted - accepted);
                logSimulationMismatch("emptying", target, what, extracted, inserted);
                total += accepted;
                break;
            }

            total += inserted;
            if (!transferAll) {
                break;
            }
            available = Long.MAX_VALUE;
        }

        if (total > 0) {
            context.playEmptySound(player, what);
        }
        return total;
    }

    private static long transferIntoContainer(Target target, ContainerItemContext context, Player player,
                                              boolean transferAll) {
        GenericStack current = target.stack();
        if (current == null || !isTransferKey(current.what())) {
            return 0;
        }

        AEKey what = current.what();
        if (!ContainerItemStrategies.isKeySupported(what) || !target.accepts(what)) {
            return 0;
        }

        long total = 0;
        int iterations = transferAll ? MAX_CONTAINER_TRANSFER_ITERATIONS : 1;
        while (iterations-- > 0) {
            long requested = transferAll ? Long.MAX_VALUE : what.getAmountPerUnit();
            long sourceAvailable = target.extract(what, requested, Actionable.SIMULATE);
            if (sourceAvailable <= 0) {
                break;
            }

            long containerAvailable = context.insert(what, sourceAvailable, Actionable.SIMULATE);
            long amount = Math.min(sourceAvailable, containerAvailable);
            if (amount <= 0) {
                break;
            }

            long extracted = target.extract(what, amount, Actionable.MODULATE);
            if (extracted != amount) {
                restoreSlot(target, what, extracted);
                logSimulationMismatch("filling", target, what, amount, extracted);
                break;
            }

            long inserted = context.insert(what, extracted, Actionable.MODULATE);
            if (inserted != extracted) {
                long accepted = Math.min(extracted, Math.max(0, inserted));
                restoreSlot(target, what, extracted - accepted);
                logSimulationMismatch("filling", target, what, extracted, inserted);
                total += accepted;
                break;
            }

            total += inserted;
            if (!transferAll) {
                break;
            }
        }

        if (total > 0) {
            context.playFillSound(player, what);
        }
        return total;
    }

    private static void restoreContainer(ContainerItemContext context, AEKey what, long amount) {
        if (amount <= 0) {
            return;
        }
        long restored = context.insert(what, amount, Actionable.MODULATE);
        if (restored != amount) {
            Data_Energistics.LOGGER.error(
                    "Container transfer could not restore {} of {} after a simulation mismatch", amount - restored, what);
        }
    }

    private static void restoreSlot(Target target, AEKey what, long amount) {
        if (amount <= 0) {
            return;
        }
        long restored = target.insert(what, amount, Actionable.MODULATE);
        if (restored != amount) {
            Data_Energistics.LOGGER.error(
                    "Container transfer could not restore {} of {} to slot {} after a simulation mismatch",
                    amount - restored,
                    what,
                    target.slot());
        }
    }

    private static void logSimulationMismatch(String direction, Target target, AEKey what, long simulated,
                                              long actual) {
        Data_Energistics.LOGGER.warn(
                "Container transfer {} simulation mismatch for {} in generic slot {}: simulated {}, actual {}",
                direction,
                what,
                target.slot(),
                simulated,
                actual);
    }

    private static boolean isTransferKey(@Nullable AEKey key) {
        return key != null && !(key instanceof AEItemKey);
    }

    private static @Nullable Target target(Slot slot) {
        return target(slot, AppEngSlot::getContainerSlot);
    }

    private static @Nullable Target target(Slot slot, ToIntFunction<AppEngSlot> inventorySlotResolver) {
        if (!(slot instanceof AppEngSlot appEngSlot) || appEngSlot instanceof FakeSlot || !appEngSlot.isActive() || !(appEngSlot.getInventory() instanceof ConfigMenuInventory configInventory)) {
            return null;
        }

        GenericStackInv inventory = configInventory.getDelegate();
        if (inventory.getMode() != GenericStackInv.Mode.STORAGE) {
            return null;
        }
        return target(inventory, inventorySlotResolver.applyAsInt(appEngSlot));
    }

    private static @Nullable Target target(GenericStackInv inventory, int inventorySlot) {
        if (inventorySlot < 0 || inventorySlot >= inventory.size()) {
            return null;
        }
        return new Target(inventory, inventorySlot);
    }

    private record Target(GenericStackInv inventory, int slot) {

        private @Nullable GenericStack stack() {
            return inventory.getStack(slot);
        }

        private boolean accepts(AEKey what) {
            return inventory.isAllowedIn(slot, what);
        }

        private long insert(AEKey what, long amount, Actionable mode) {
            return inventory.insert(slot, what, amount, mode);
        }

        private long extract(AEKey what, long amount, Actionable mode) {
            return inventory.extract(slot, what, amount, mode);
        }
    }
}
