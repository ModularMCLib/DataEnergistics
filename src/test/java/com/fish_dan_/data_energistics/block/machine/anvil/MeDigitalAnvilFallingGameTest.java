package com.fish_dan_.data_energistics.block.machine.anvil;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.blockentity.machine.anvil.MeDigitalAnvilBlockEntity;
import com.fish_dan_.data_energistics.common.anvil.DigitalAnvilUpgrade;
import com.fish_dan_.data_energistics.common.research.item.ItemResearchUpgrades;
import com.fish_dan_.data_energistics.entity.anvil.FallingDigitalAnvilEntity;
import com.fish_dan_.data_energistics.registry.DEBlocks;
import com.fish_dan_.data_energistics.registry.DEDataComponents;
import com.fish_dan_.data_energistics.registry.DEEntities;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class MeDigitalAnvilFallingGameTest {

    private static final BlockPos START = new BlockPos(2, 6, 2);
    private static final BlockPos LANDING = new BlockPos(2, 2, 2);
    private static final AEItemKey REFUND = AEItemKey.of(Items.DIAMOND);
    private static final ItemResearchUpgrades UPGRADES = ItemResearchUpgrades.EMPTY.with(DigitalAnvilUpgrade.UNRESTRICTED_LEVELS.researchId());

    private MeDigitalAnvilFallingGameTest() {}

    @GameTest(template = "empty_9x9", timeoutTicks = 100)
    public static void unsupportedPlacementSurvivesEntityReloadWithResearchAndRefunds(GameTestHelper helper) {
        helper.setBlock(LANDING.below(), Blocks.STONE);
        placeUpgradedAnvil(helper);
        helper.runAtTickTime(4, () -> {
            var falling = helper.getEntities(DEEntities.FALLING_DIGITAL_ANVIL.get());
            helper.assertValueEqual(falling.size(), 1, "Unsupported placement must create one falling anvil");
            CompoundTag saved = new CompoundTag();
            helper.assertTrue(falling.getFirst().save(saved), "The falling anvil must be saveable");
            falling.getFirst().discard();
            var restored = EntityType.loadEntityRecursive(saved, helper.getLevel(), entity -> entity);
            helper.assertTrue(restored instanceof FallingDigitalAnvilEntity, "Reload must retain the registered falling entity type");
            helper.getLevel().addFreshEntity(restored);
        });
        helper.succeedWhen(() -> {
            helper.assertBlockPresent(DEBlocks.ME_DIGITAL_ANVIL.get(), LANDING);
            MeDigitalAnvilBlockEntity host = helper.getBlockEntity(LANDING);
            helper.assertValueEqual(host.getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING), Direction.EAST,
                    "Landing must retain the anvil orientation");
            helper.assertValueEqual(host.components().get(DEDataComponents.RESEARCH_UPGRADES.get()), UPGRADES,
                    "Research must survive falling and reloading");
            helper.assertValueEqual(host.getCustomName().getString(), "Falling anvil", "Custom names must survive falling");
            helper.assertValueEqual(host.payment().resourceRefunds().getLong(REFUND), 3L, "Pending refunds must land with the machine");
            helper.assertTrue(helper.getEntities(EntityType.ITEM).isEmpty(), "Falling must not duplicate refunds into item drops");
        });
    }

    @GameTest(template = "empty_9x9", timeoutTicks = 100)
    public static void supportedAnvilFallsOnlyAfterItsSupportIsRemoved(GameTestHelper helper) {
        helper.setBlock(LANDING.below(), Blocks.STONE);
        helper.setBlock(START.below(), Blocks.STONE);
        helper.setBlock(START, DEBlocks.ME_DIGITAL_ANVIL.get());
        helper.runAtTickTime(6, () -> {
            helper.assertBlockPresent(DEBlocks.ME_DIGITAL_ANVIL.get(), START);
            helper.assertTrue(helper.getEntities(DEEntities.FALLING_DIGITAL_ANVIL.get()).isEmpty(), "A supported anvil must stay in place");
            helper.setBlock(START.below(), Blocks.AIR);
        });
        helper.succeedWhen(() -> helper.assertBlockPresent(DEBlocks.ME_DIGITAL_ANVIL.get(), LANDING));
    }

    @GameTest(template = "empty_9x9", timeoutTicks = 100)
    public static void failedLandingDropsResearchAndRefundsExactlyOnce(GameTestHelper helper) {
        helper.setBlock(LANDING.below(), Blocks.STONE);
        helper.setBlock(LANDING, Blocks.TORCH);
        placeUpgradedAnvil(helper);
        helper.succeedWhen(() -> {
            var drops = helper.getEntities(EntityType.ITEM);
            helper.assertValueEqual(drops.size(), 2, "A failed landing must drop one upgraded anvil and one refund stack");
            var anvil = drops.stream().map(entity -> entity.getItem()).filter(stack -> stack.is(DEBlocks.ME_DIGITAL_ANVIL.asItem())).findFirst().orElseThrow();
            helper.assertValueEqual(anvil.getCount(), 1, "A failed landing must not duplicate the anvil");
            helper.assertValueEqual(anvil.get(DEDataComponents.RESEARCH_UPGRADES.get()), UPGRADES, "The dropped item must retain research");
            long refunded = 0;
            for (var entity : drops) {
                GenericStack wrapped = GenericStack.fromItemStack(entity.getItem());
                if (wrapped != null && wrapped.what().equals(REFUND)) refunded += wrapped.amount();
            }
            helper.assertValueEqual(refunded, 3L, "Pending resource refunds must be dropped exactly once");
            helper.assertTrue(helper.getEntities(DEEntities.FALLING_DIGITAL_ANVIL.get()).isEmpty(), "A failed landing must remove its falling entity");
        });
    }

    private static void placeUpgradedAnvil(GameTestHelper helper) {
        helper.setBlock(START, DEBlocks.ME_DIGITAL_ANVIL.get().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST));
        MeDigitalAnvilBlockEntity host = helper.getBlockEntity(START);
        host.setComponents(DataComponentMap.builder().set(DEDataComponents.RESEARCH_UPGRADES.get(), UPGRADES).build());
        host.setName("Falling anvil");
        host.payment().resourceRefunds().put(REFUND, 3);
    }
}
