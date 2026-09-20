package com.fish_dan_.data_energistics.ae2.patternprovider.packaged;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.ae2.patternprovider.adaptive.AdaptivePatternProviderLogic;
import com.fish_dan_.data_energistics.blockentity.patternprovider.AdaptivePatternProviderBlockEntity;
import com.fish_dan_.data_energistics.blockentity.patternprovider.DigitalPackagedPatternProviderBlockEntity;
import com.fish_dan_.data_energistics.common.crafting.packaged.execution.PackagedDispatchState;
import com.fish_dan_.data_energistics.common.crafting.packaged.execution.PackagedOperationState;
import com.fish_dan_.data_energistics.common.crafting.packaged.recipe.PackagedRecipeCatalog;
import com.fish_dan_.data_energistics.item.patternprovider.PackagedRecoveryItem;
import com.fish_dan_.data_energistics.registry.DEBlocks;
import com.fish_dan_.data_energistics.registry.DEItems;
import com.fish_dan_.data_energistics.world.packaged.PackagedMachineClaims;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;

import java.math.BigInteger;

@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class PackagedRecoveryGameTest {

    private PackagedRecoveryGameTest() {}

    @TestHolder("adaptive_capacity_cards_initialize_without_reentry_and_keep_runtime_expansion")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void capacityCardsStillExpandAfterSafeInitialization(GameTestHelper helper) {
        var local = new BlockPos(2, 1, 2);
        helper.setBlock(local, DEBlocks.ADAPTIVE_PATTERN_PROVIDER.get());
        var host = (AdaptivePatternProviderBlockEntity) helper.getBlockEntity(local);
        helper.assertValueEqual(host.getProviderSlotLimit(), 4, "An empty adaptive provider must initialize with four slots");
        for (int slot = 0; slot < 3; slot++) {
            host.getUpgrades().setItemDirect(slot, AEItems.CAPACITY_CARD.stack());
            helper.assertValueEqual(host.getProviderSlotLimit(), 4 + 4 * (slot + 1), "Each installed capacity card must still add four provider slots");
        }
        host.getUpgrades().setItemDirect(2, ItemStack.EMPTY);
        helper.assertValueEqual(host.getProviderSlotLimit(), 12, "Removing a capacity card must refresh the live capacity");
        helper.succeed();
    }

    @TestHolder("packaged_actual_block_destruction_preserves_eighteen_long_slots_and_redeems_once")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void brokenProviderRecoversLongSlotsThroughActualItemUse(GameTestHelper helper) {
        var local = new BlockPos(2, 1, 2);
        helper.setBlock(local, DEBlocks.DIGITAL_PACKAGED_PATTERN_PROVIDER.get());
        var provider = (DigitalPackagedPatternProviderBlockEntity) helper.getBlockEntity(local);
        var key = AEItemKey.of(Items.IRON_INGOT);
        for (int slot = 0; slot < 18; slot++) provider.getLogic().getReturnInv().insert(slot, key, Long.MAX_VALUE, Actionable.MODULATE);
        BlockPos origin = helper.absolutePos(local);
        helper.getLevel().destroyBlock(origin, true);
        var receipts = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(origin).inflate(1),
                entity -> entity.getItem().is(DEItems.PACKAGED_RECOVERY.get()));
        helper.assertValueEqual(receipts.size(), 1, "Actual removal must create one recovery receipt");
        ItemStack receipt = receipts.getFirst().getItem().copy();
        ItemStack duplicate = receipt.copy();
        receipts.getFirst().discard();
        helper.assertTrue(helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(origin).inflate(1),
                entity -> entity.getItem().is(Items.IRON_INGOT)).isEmpty(), "Escrowed long resources must not also become truncated item drops");
        helper.setBlock(local, DEBlocks.DIGITAL_PACKAGED_PATTERN_PROVIDER.get());
        var replacement = (DigitalPackagedPatternProviderBlockEntity) helper.getBlockEntity(local);
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, receipt);
        var use = new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(origin.getCenter(), Direction.UP, origin, false));
        helper.assertValueEqual(receipt.getItem().useOn(use), InteractionResult.CONSUME, "Using the receipt on the original host position must restore it");
        helper.assertTrue(receipt.isEmpty(), "Successful physical redemption must consume the receipt");
        for (int slot = 0; slot < 18; slot++) helper.assertValueEqual(replacement.getLogic().getReturnInv().getAmount(slot), Long.MAX_VALUE, "Every return slot must preserve all long units");
        replacement.getLogic().getReturnInv().clear();
        helper.assertTrue(!((DigitalPackagedPatternProviderLogic) replacement.getLogic()).restoreRecoveryItem(duplicate), "Even an empty destination cannot redeem a copied receipt twice");
        helper.succeed();
    }

    @TestHolder("packaged_recovery_freezes_source_preserves_big_inputs_and_rejects_wrong_position")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void persistedHandoffRetainsBigInputsAndCannotRunTwice(GameTestHelper helper) {
        var key = AEItemKey.of(Items.DIAMOND);
        var first = new KeyCounter();
        first.add(key, Long.MAX_VALUE);
        var second = new KeyCounter();
        second.add(key, Long.MAX_VALUE);
        var target = helper.absolutePos(new BlockPos(3, 1, 3));
        var operation = new PackagedOperationState(Data_Energistics.id("recovery_test"), Data_Energistics.id("recovery_test"),
                target, Direction.UP, new CompoundTag(), new KeyCounter[] { first, second });
        var state = state(helper, operation);
        var returns = new PackagedReturnInventory(() -> {});
        var origin = helper.absolutePos(new BlockPos(1, 1, 1));
        var receipt = state.prepareRecoveryDrop(helper.getLevel(), origin, "standalone", returns);
        var nether = helper.getLevel().getServer().getLevel(Level.NETHER);
        helper.assertTrue(nether != null && PackagedRecoveryItem.receipt(nether, origin, receipt) == null,
                "A copied receipt must not move tasks into another dimension");
        var saved = new CompoundTag();
        state.save(saved, helper.getLevel().registryAccess());
        var frozen = PackagedDispatchState.load(saved, helper.getLevel().registryAccess());
        helper.assertTrue(!frozen.hasWork(), "A persisted source handoff must remain frozen");
        var replacement = new PackagedDispatchState();
        helper.assertTrue(!replacement.restoreRecovery(helper.getLevel(), origin.above(), "standalone", receipt, returns), "Changing host coordinates must not consume the receipt");
        helper.assertTrue(!replacement.restoreRecovery(helper.getLevel(), origin, "adaptive", receipt, returns), "Changing host kind must not consume the receipt");
        helper.assertTrue(replacement.restoreRecovery(helper.getLevel(), origin, "standalone", receipt, returns), "The intended destination must restore the intact receipt");
        var restored = new CompoundTag();
        replacement.save(restored, helper.getLevel().registryAccess());
        var restoredOperation = PackagedOperationState.load(restored.getList("operations", Tag.TAG_COMPOUND).getCompound(0), helper.getLevel().registryAccess());
        helper.assertValueEqual(restoredOperation.available(key), BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.TWO), "Recovery must not narrow BigInteger input ownership");
        helper.assertTrue(!frozen.restoreRecovery(helper.getLevel(), origin, "standalone", receipt.copy(), returns), "The original frozen source cannot duplicate already restored assets");
        helper.succeed();
    }

    @TestHolder("packaged_recovered_finished_operation_waits_for_return_space_then_releases_machine")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void recoveredOutputBackpressureKeepsClaimUntilAllAmountsSettle(GameTestHelper helper) {
        var key = AEItemKey.of(Items.IRON_INGOT);
        var target = helper.absolutePos(new BlockPos(3, 1, 3));
        var operation = new PackagedOperationState(Data_Energistics.id("completed_recovery_test"), Data_Energistics.id("recovery_test"),
                target, Direction.UP, new CompoundTag(), new KeyCounter[0]);
        operation.returned(key, Long.MAX_VALUE);
        operation.returned(key, Long.MAX_VALUE);
        operation.complete();
        var claims = PackagedMachineClaims.get(helper.getLevel());
        helper.assertTrue(claims.acquire(target, operation.id()), "The completed task still owns its machine until output settlement");
        var state = state(helper, operation);
        var buffer = new PackagedReturnInventory(() -> {});
        var origin = helper.absolutePos(new BlockPos(1, 1, 1));
        var receipt = state.prepareRecoveryDrop(helper.getLevel(), origin, "standalone", buffer);
        var restored = new PackagedDispatchState();
        helper.assertTrue(restored.restoreRecovery(helper.getLevel(), origin, "standalone", receipt, buffer), "Finished task ownership must restore");
        for (int slot = 0; slot < buffer.size(); slot++) buffer.insert(slot, key, Long.MAX_VALUE, Actionable.MODULATE);
        var catalog = new PackagedRecipeCatalog(ObjectList.of());
        restored.tick(helper.getLevel(), catalog, buffer, IActionSource.empty());
        helper.assertTrue(operation.id().equals(claims.owner(target)), "A full return buffer must retain the claim and queued outputs");
        buffer.clear();
        restored.tick(helper.getLevel(), catalog, buffer, IActionSource.empty());
        helper.assertTrue(restored.hasWork(), "One long-sized flush cannot discard the rest of a BigInteger amount");
        restored.tick(helper.getLevel(), catalog, buffer, IActionSource.empty());
        helper.assertTrue(!restored.hasWork() && claims.available(target), "Only complete output settlement may release the machine");
        helper.assertValueEqual(buffer.getAmount(0), Long.MAX_VALUE, "First bounded output transfer must remain exact");
        helper.assertValueEqual(buffer.getAmount(1), Long.MAX_VALUE, "Second bounded output transfer must remain exact");
        helper.succeed();
    }

    @TestHolder("packaged_inactive_adaptive_route_drops_receipt_before_vanilla_return_conversion")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void switchingAdaptiveProviderPreservesOwnedRecoveryState(GameTestHelper helper) {
        var local = new BlockPos(2, 1, 2);
        helper.setBlock(local, DEBlocks.ADAPTIVE_PATTERN_PROVIDER.get());
        var host = (AdaptivePatternProviderBlockEntity) helper.getBlockEntity(local);
        host.getProviderInventory().setItemDirect(0, new ItemStack(DEItems.DIGITAL_PACKAGED_PATTERN_PROVIDER.get()));
        var logic = (AdaptivePatternProviderLogic) host.getLogic();
        var buffer = new PackagedReturnInventory(() -> {});
        var key = AEItemKey.of(Items.GOLD_INGOT);
        buffer.insert(0, key, 64, Actionable.MODULATE);
        var state = new PackagedDispatchState();
        var receipt = state.prepareRecoveryDrop(helper.getLevel(), host.getBlockPos(), "adaptive", buffer);
        helper.assertTrue(logic.restoreRecoveryItem(receipt), "Selected adaptive packaged route must restore its return buffer");
        host.getProviderInventory().setItemDirect(0, AEBlocks.PATTERN_PROVIDER.stack());
        var drops = new ObjectArrayList<ItemStack>();
        logic.addDrops(drops);
        helper.assertTrue(drops.stream().anyMatch(stack -> stack.is(DEItems.PACKAGED_RECOVERY.get())), "Inactive packaged ownership must still enter recovery escrow");
        helper.assertTrue(drops.stream().noneMatch(stack -> stack.is(Items.GOLD_INGOT)), "Escrowed returns must not also be dropped by the vanilla host");
        logic.clearContent();
        helper.succeed();
    }

    private static PackagedDispatchState state(GameTestHelper helper, PackagedOperationState operation) {
        var payload = new CompoundTag();
        var operations = new ListTag();
        operations.add(operation.save(helper.getLevel().registryAccess()));
        payload.put("operations", operations);
        return PackagedDispatchState.load(payload, helper.getLevel().registryAccess());
    }
}
