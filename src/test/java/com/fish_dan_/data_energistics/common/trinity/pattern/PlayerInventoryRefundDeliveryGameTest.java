package com.fish_dan_.data_energistics.common.trinity.pattern;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class PlayerInventoryRefundDeliveryGameTest {

    private PlayerInventoryRefundDeliveryGameTest() {}

    @TestHolder("trinity_refund_fills_ae_across_long_chunks_before_inventory_fallback")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void fillsAeAcrossLongChunksBeforeInventoryFallback(GameTestHelper helper) {
        BigInteger amount = BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.valueOf(19L));
        List<Long> offers = new ArrayList<>();
        var inserted = PlayerInventoryRefundDelivery.insertIntoNetwork(amount, offered -> {
            offers.add(offered);
            return offered;
        });
        helper.assertValueEqual(inserted.remaining(), BigInteger.ZERO, "AE accepts the whole amount across long-sized calls");
        helper.assertValueEqual(offers, List.of(Long.MAX_VALUE, 19L), "Every available AE chunk is tried before fallback");

        int[] calls = { 0 };
        var full = PlayerInventoryRefundDelivery.insertIntoNetwork(amount, offered -> calls[0]++ == 0 ? offered : 0L);
        helper.assertValueEqual(calls[0], 2, "AE is retried after accepting a complete first chunk");
        helper.assertValueEqual(full.remaining(), BigInteger.valueOf(19L), "Only the amount AE cannot accept remains for inventory");
        helper.assertTrue(!full.retryRequired(), "A full AE storage response allows inventory fallback");
        helper.succeed();
    }

    @TestHolder("trinity_refund_retains_large_remainder_after_bounded_ae_transfer_work")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void retainsRemainderAfterBoundedAeTransferWork(GameTestHelper helper) {
        BigInteger maximumChunk = BigInteger.valueOf(Long.MAX_VALUE);
        BigInteger amount = maximumChunk
                .multiply(BigInteger.valueOf(PlayerInventoryRefundDelivery.MAX_NETWORK_TRANSFERS + 1L))
                .add(BigInteger.valueOf(3L));
        int[] calls = { 0 };
        var result = PlayerInventoryRefundDelivery.insertIntoNetwork(amount, offered -> {
            calls[0]++;
            return offered;
        });

        helper.assertValueEqual(calls[0], PlayerInventoryRefundDelivery.MAX_NETWORK_TRANSFERS,
                "One refund call observes its bounded AE transfer budget");
        helper.assertValueEqual(result.remaining(), maximumChunk.add(BigInteger.valueOf(3L)),
                "Unattempted material remains available for a later retry");
        helper.assertTrue(result.retryRequired(), "Budget exhaustion keeps the remainder out of player fallback");
        helper.succeed();
    }
}
