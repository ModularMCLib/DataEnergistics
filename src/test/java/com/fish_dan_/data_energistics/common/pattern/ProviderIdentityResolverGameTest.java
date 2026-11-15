package com.fish_dan_.data_energistics.common.pattern;

import com.fish_dan_.data_energistics.Data_Energistics;

import appeng.api.implementations.blockentities.PatternContainerGroup;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGrid;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartCollisionHelper;
import appeng.blockentity.networking.CableBusBlockEntity;
import appeng.core.definitions.AEBlockEntities;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEParts;
import appeng.helpers.patternprovider.PatternContainer;
import appeng.parts.AEBasePart;
import appeng.util.inv.AppEngInternalInventory;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.jspecify.annotations.Nullable;

@GameTestHolder(Data_Energistics.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ProviderIdentityResolverGameTest {

    private ProviderIdentityResolverGameTest() {}

    @TestHolder("provider_part_without_ae2_logic_host_keeps_physical_identity")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void partWithoutAe2LogicHostKeepsPhysicalIdentity(GameTestHelper helper) {
        var resolver = ProviderIdentityResolver.create();
        var host = new PartHost(helper.absolutePos(new BlockPos(1, 1, 1)));
        host.setLevel(helper.getLevel());
        var identities = new ObjectOpenHashSet<String>();
        for (Direction side : Direction.values()) {
            host.mount(side);
            var identity = resolver.resolve(host.part);
            helper.assertTrue(identity instanceof ProviderIdentity.Part,
                    "A mounted terminal part must have a physical identity without AE2's logic-host interface");
            var partIdentity = (ProviderIdentity.Part) identity;
            helper.assertTrue(partIdentity.mount() == ProviderIdentity.Mount.fromDirection(side),
                    "Physical identity must preserve the mounted side");
            helper.assertTrue(partIdentity.blockPos().equals(host.getBlockPos()),
                    "Physical identity must preserve the cable-bus position");
            helper.assertTrue(partIdentity.dimensionId().equals(helper.getLevel().dimension().location()),
                    "Physical identity must preserve the dimension");
            helper.assertTrue(identities.add(identity.digest()), "Each mounted side must have a distinct identity");
            helper.assertTrue(identity.equals(resolver.resolve(host.part)),
                    "Repeated resolution must preserve the stable physical identity");
        }
        helper.succeed();
    }

    private static final class TerminalPart extends AEBasePart implements PatternContainer {

        private final InternalInventory patterns = new AppEngInternalInventory(null, 9);

        private TerminalPart() {
            super(AEParts.PATTERN_PROVIDER.get());
        }

        @Override
        public void getBoxes(IPartCollisionHelper helper) {
            helper.addBox(2, 2, 14, 14, 14, 16);
        }

        @Override
        public InternalInventory getTerminalPatternInventory() {
            return this.patterns;
        }

        @Override
        public @Nullable IGrid getGrid() {
            return getMainNode().getGrid();
        }

        @Override
        public PatternContainerGroup getTerminalGroup() {
            throw new AssertionError("Physical resolution must not use a display-based virtual identity");
        }
    }

    private static final class PartHost extends CableBusBlockEntity {

        private final TerminalPart part = new TerminalPart();
        private Direction side = Direction.NORTH;

        private PartHost(BlockPos position) {
            super(AEBlockEntities.CABLE_BUS.get(), position, AEBlocks.CABLE_BUS.block().defaultBlockState());
        }

        private void mount(Direction side) {
            this.side = side;
            this.part.setPartHostInfo(side, this, this);
        }

        @Override
        public @Nullable IPart getPart(@Nullable Direction side) {
            return side == this.side ? this.part : null;
        }
    }
}
