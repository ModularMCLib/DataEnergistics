package com.fish_dan_.data_energistics.ae2.patternprovider.adaptive;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.api.registry.adaptive.AdaptiveProviderConnectorMode;
import com.fish_dan_.data_energistics.api.registry.adaptive.AdaptiveProviderConnectorPolicy;
import com.fish_dan_.data_energistics.blockentity.patternprovider.AdaptivePatternProviderBlockEntity;
import com.fish_dan_.data_energistics.part.AdaptivePatternProviderPart;
import com.fish_dan_.data_energistics.registry.DEBlocks;
import com.fish_dan_.data_energistics.registry.DEItems;

import appeng.blockentity.networking.CableBusBlockEntity;
import appeng.core.definitions.AEBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import io.netty.buffer.Unpooled;

@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class AdaptiveConnectorVisualSyncGameTest {

    private AdaptiveConnectorVisualSyncGameTest() {}

    @TestHolder("connector_block_visual_update_retains_faces_order_and_removal")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void blockUpdateRetainsFacesOrderAndRemoval(GameTestHelper helper) {
        StreamProvider source = new StreamProvider(helper.absolutePos(new BlockPos(1, 1, 1)));
        StreamProvider recipient = new StreamProvider(source.getBlockPos());
        source.setLevel(helper.getLevel());
        recipient.setLevel(helper.getLevel());
        AdaptivePatternProviderLogic logic = (AdaptivePatternProviderLogic) source.getLogic();
        AdaptivePatternProviderLogic received = (AdaptivePatternProviderLogic) recipient.getLogic();
        BlockPos first = helper.absolutePos(new BlockPos(30, 2, -15));
        BlockPos second = first.above(7);
        logic.bindConnectorTarget(first, Direction.WEST);
        logic.bindConnectorTarget(second, Direction.UP);
        helper.assertValueEqual(source.updates, 2, "Each binding must request a client update without needing a menu");
        helper.assertTrue(copyBlockUpdate(helper, source, recipient), "Initial bindings change the client stream state");
        helper.assertValueEqual(received.connectorTargets(), logic.connectorTargets(), "World overlays receive exact positions, faces and order");
        helper.assertTrue(!copyBlockUpdate(helper, source, recipient), "Repeated updates are idempotent");

        logic.setConnectorMode(AdaptiveProviderConnectorMode.PULL);
        logic.setConnectorPolicy(AdaptiveProviderConnectorPolicy.PRIORITY);
        logic.unbindConnectorTarget(first, Direction.WEST);
        copyBlockUpdate(helper, source, recipient);
        helper.assertValueEqual(received.connectorMode(), AdaptiveProviderConnectorMode.PULL, "Mode changes reach the client");
        helper.assertValueEqual(received.connectorPolicy(), AdaptiveProviderConnectorPolicy.PRIORITY, "Policy changes reach the client");
        helper.assertValueEqual(received.connectorTargets(), logic.connectorTargets(), "Unbinding removes stale lines");
        logic.unbindConnectorTarget(second, Direction.UP);
        copyBlockUpdate(helper, source, recipient);
        helper.assertTrue(received.connectorTargets().isEmpty(), "An empty update clears the last rendered link");
        helper.assertValueEqual(recipient.updates, 0, "Decoding display data must not enqueue outgoing updates");
        helper.succeed();
    }

    @TestHolder("connector_part_visual_update_replaces_targets_independently_of_nbt")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void partUpdateReplacesTargetsIndependentlyOfNbt(GameTestHelper helper) {
        BlockPos position = new BlockPos(1, 1, 1);
        helper.setBlock(position, AEBlocks.CABLE_BUS.block());
        CableBusBlockEntity bus = helper.getBlockEntity(position);
        AdaptivePatternProviderPart source = new AdaptivePatternProviderPart(DEItems.ADAPTIVE_PATTERN_PROVIDER_PART.get());
        AdaptivePatternProviderPart recipient = new AdaptivePatternProviderPart(DEItems.ADAPTIVE_PATTERN_PROVIDER_PART.get());
        source.setPartHostInfo(Direction.NORTH, bus.getCableBus(), bus);
        recipient.setPartHostInfo(Direction.NORTH, bus.getCableBus(), bus);
        BlockPos target = helper.absolutePos(new BlockPos(24, 3, 18));
        source.getLogic().bindConnectorTarget(target, Direction.DOWN);
        source.getLogic().setConnectorPolicy(AdaptiveProviderConnectorPolicy.PRIORITY);
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            source.writeToStream(buffer);
            helper.assertTrue(recipient.readFromStream(buffer), "The native part update stream must include bindings");
            helper.assertValueEqual(buffer.readableBytes(), 0, "Part update consumes its complete payload");
            helper.assertValueEqual(recipient.getLogic().connectorTargets(), source.getLogic().connectorTargets(), "Panel overlays get the same links as block overlays");
            source.getLogic().unbindConnectorTarget(target, Direction.DOWN);
            buffer.clear();
            source.writeToStream(buffer);
            recipient.readFromStream(buffer);
            helper.assertTrue(recipient.getLogic().connectorTargets().isEmpty(), "Panel unbind clears the client link list");
        } finally {
            buffer.release();
        }
        helper.succeed();
    }

    private static boolean copyBlockUpdate(GameTestHelper helper, StreamProvider source, StreamProvider recipient) {
        // Exercise AE2's real update-tag path, not saveToNBT/readFromNBT which never reached the client before this fix.
        byte[] update = source.getUpdateTag(helper.getLevel().registryAccess()).getByteArray("#upd");
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(update), helper.getLevel().registryAccess());
        try {
            boolean changed = recipient.receive(buffer);
            helper.assertValueEqual(buffer.readableBytes(), 0, "Block update consumes its complete payload");
            return changed;
        } finally {
            buffer.release();
        }
    }

    private static final class StreamProvider extends AdaptivePatternProviderBlockEntity {

        private int updates;

        private StreamProvider(BlockPos position) {
            super(position, DEBlocks.ADAPTIVE_PATTERN_PROVIDER.get().defaultBlockState());
        }

        @Override
        public void markForClientUpdate() {
            this.updates++;
        }

        private boolean receive(RegistryFriendlyByteBuf data) {
            return super.readFromStream(data);
        }
    }
}
