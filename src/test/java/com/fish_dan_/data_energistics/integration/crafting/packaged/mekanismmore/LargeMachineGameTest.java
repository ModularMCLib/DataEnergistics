package com.fish_dan_.data_energistics.integration.crafting.packaged.mekanismmore;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.crafting.packaged.execution.PackagedOperationState;
import com.fish_dan_.data_energistics.world.packaged.PackagedMachineClaims;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.ids.AEComponents;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import com.jerry.meklm.common.tile.machine.TileEntityLargeRotaryCondensentrator;
import me.ramidzkh.mekae2.ae2.MekanismKey;
import mekanism.api.MekanismAPI;
import mekanism.api.chemical.ChemicalStack;
import mekanism.common.tile.interfaces.IRedstoneControl.RedstoneControl;

import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;

@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class LargeMachineGameTest {

    @TestHolder("packaged_mekmm_infuser_waits_for_energy_and_returns_real_chemicals")
    @EmptyTemplate("9")
    @GameTest(template = "empty_9x9", timeoutTicks = 180)
    public static void infuser(GameTestHelper helper) {
        run(helper, LargeMachineKind.INFUSER, "large_chemical_infuser", "mekanism:chemical_infusing/sulfuric_acid",
                chemical("sulfur_trioxide"), chemical("water_vapor"), false, 3);
    }

    @TestHolder("packaged_mekmm_mixer_returns_real_pigment")
    @EmptyTemplate("9")
    @GameTest(template = "empty_9x9", timeoutTicks = 180)
    public static void mixer(GameTestHelper helper) {
        run(helper, LargeMachineKind.PIGMENT_MIXER, "large_pigment_mixer", "mekanism:pigment_mixing/blue_green_to_cyan",
                chemical("blue"), chemical("green"), false, 2);
    }

    @TestHolder("packaged_mekmm_separator_returns_both_chemical_outputs")
    @EmptyTemplate("9")
    @GameTest(template = "empty_9x9", timeoutTicks = 180)
    public static void separator(GameTestHelper helper) {
        run(helper, LargeMachineKind.SEPARATOR, "large_electrolytic_separator", "mekanism:separator/water",
                AEFluidKey.of(Fluids.WATER), AEFluidKey.of(Fluids.WATER), false, 2);
    }

    @TestHolder("packaged_mekmm_rotary_fluid_to_chemical")
    @EmptyTemplate("9")
    @GameTest(template = "empty_9x9", timeoutTicks = 180)
    public static void evaporate(GameTestHelper helper) {
        run(helper, LargeMachineKind.ROTARY, "large_rotary_condensentrator", "mekanism:rotary/water_vapor",
                AEFluidKey.of(Fluids.WATER), AEFluidKey.of(Fluids.WATER), true, 2);
    }

    @TestHolder("packaged_mekmm_rotary_chemical_to_fluid")
    @EmptyTemplate("9")
    @GameTest(template = "empty_9x9", timeoutTicks = 180)
    public static void condense(GameTestHelper helper) {
        run(helper, LargeMachineKind.ROTARY, "large_rotary_condensentrator", "mekanism:rotary/water_vapor",
                chemical("water_vapor"), chemical("water_vapor"), false, 2);
    }

    @TestHolder("packaged_mekmm_solar_requires_native_sunlight")
    @EmptyTemplate("9")
    @GameTest(template = "empty_9x9", timeoutTicks = 240)
    public static void solar(GameTestHelper helper) {
        run(helper, LargeMachineKind.SOLAR_ACTIVATOR, "large_solar_neutron_activator", "mekanism:processing/lategame/polonium",
                chemical("nuclear_waste"), chemical("nuclear_waste"), false, 2);
    }

    @TestHolder("packaged_mekmm_nuclear_consumes_per_tick_chemical_budget")
    @EmptyTemplate("9")
    @GameTest(template = "empty_9x9", timeoutTicks = 240)
    public static void nuclear(GameTestHelper helper) {
        run(helper, LargeMachineKind.NUCLEOSYNTHESIZER, "large_antiprotonic_nucleosynthesizer", "data_energistics:packaged/mekanismmore/nuclear",
                AEItemKey.of(Items.PAPER), chemical("antimatter"), false, 2);
    }

    private static MekanismKey chemical(String name) {
        return MekanismKey.of(new ChemicalStack(MekanismAPI.CHEMICAL_REGISTRY.getHolderOrThrow(
                ResourceKey.create(MekanismAPI.CHEMICAL_REGISTRY.key(), ResourceLocation.fromNamespaceAndPath("mekanism", name))), 1));
    }

    private static void run(GameTestHelper helper, LargeMachineKind kind, String blockId, String recipeName,
                            AEKey first, AEKey second, boolean mode, long cycles) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(4, 2, 4));
        var block = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("mekmm", blockId));
        helper.assertTrue(block != Blocks.AIR, "Native large machine must be installed");
        level.setBlockAndUpdate(pos, block.defaultBlockState());
        if (level.getBlockEntity(pos) instanceof TileEntityLargeRotaryCondensentrator rotary && rotary.getMode() != mode) rotary.nextMode();
        var layout = kind.layout(level.getBlockEntity(pos));
        helper.assertTrue(layout != null, "Native machine must expose its expected resource ports");
        var recipeId = ResourceLocation.parse(recipeName);
        var recipe = level.getRecipeManager().byKey(recipeId).orElseThrow().value();
        var unit = LargeMachineRecipePlan.unit(recipe, first, second, mode);
        helper.assertTrue(unit != null, "Native recipe must match the test input keys");
        var counter = new KeyCounter();
        for (var input : unit.inputs()) counter.add(input.what(), input.amount() * cycles);
        var inputs = new KeyCounter[] { counter };
        var pattern = new OutputPattern(unit.outputs().stream().map(s -> new GenericStack(s.what(), s.amount() * cycles)).toList());
        var adapter = new LargeMachineAdapter(kind);
        if (kind == LargeMachineKind.ROTARY) recipeId = recipeId.withPrefix(mode ? "/decondensentrating/" : "/condensentrating/");
        if (kind == LargeMachineKind.NUCLEOSYNTHESIZER) {
            var viewerInputs = List.of(unit.inputs().getFirst(), new GenericStack(second, 1200));
            var encoded = PatternDetailsHelper.encodeProcessingPattern(viewerInputs, unit.outputs());
            var completed = adapter.completeEncoding(level, recipeId, encoded);
            helper.assertTrue(completed != null, "Nuclear encoding must resolve the exact native duration");
            helper.assertValueEqual(completed.get(AEComponents.ENCODED_PROCESSING_PATTERN).sparseInputs().get(1).amount(),
                    unit.inputs().getLast().amount(), "Viewer per-tick preview must be replaced with the complete gas budget");
        }
        var preparation = adapter.prepare(level, pos, Direction.UP, recipeId, pattern, inputs);
        helper.assertTrue(preparation != null, "Exact native envelope should be accepted: " + kind);
        helper.assertTrue(layout.empty(), "Read-only admission must leave all real slots empty");
        var extra = new KeyCounter();
        extra.addAll(counter);
        extra.add(AEItemKey.of(Items.DIRT), 1);
        helper.assertTrue(adapter.prepare(level, pos, Direction.UP, recipeId, pattern, new KeyCounter[] { extra }) == null,
                "Foreign extra inputs must be rejected before mutation");
        layout.tile().setControlType(RedstoneControl.HIGH);
        if (kind == LargeMachineKind.SOLAR_ACTIVATOR) {
            for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) level.setBlockAndUpdate(pos.offset(x, 4, z), Blocks.STONE.defaultBlockState());
        }
        var operation = new PackagedOperationState(adapter.id(), recipeId, pos, Direction.UP, preparation, inputs);
        var claims = PackagedMachineClaims.get(level);
        helper.assertTrue(claims.acquire(pos, operation.id()), "Test must claim its real machine");
        operation.advance(level, adapter);
        helper.assertTrue(operation.failure() == null, "Physical input delivery must succeed: " + operation.failure());
        for (int i = 0; i < unit.inputs().size(); i++) helper.assertValueEqual(layout.inputs().get(i).contents(), unit.inputs().get(i), "Input must exist in the actual tank or slot");
        PackagedOperationState[] saved = { PackagedOperationState.load(operation.save(level.registryAccess()), level.registryAccess()) };
        boolean[] released = { false };
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(saved[0].save(level.registryAccess()).getList("outputs", Tag.TAG_COMPOUND).isEmpty(), "No output may be synthesized while the machine is stopped");
            layout.tile().setControlType(RedstoneControl.DISABLED);
            for (var energy : layout.tile().getEnergyContainers(null)) energy.setEnergy(energy.getMaxEnergy());
            if (layout.tile() instanceof TileEntityLargeRotaryCondensentrator rotary) rotary.nextMode();
            if (kind == LargeMachineKind.SOLAR_ACTIVATOR) {
                level.setDayTime(6000);
                level.setWeatherParameters(6000, 0, false, false);
            }
        });
        if (kind == LargeMachineKind.SOLAR_ACTIVATOR) helper.runAfterDelay(20, () -> {
            helper.assertTrue(saved[0].save(level.registryAccess()).getList("outputs", Tag.TAG_COMPOUND).isEmpty(), "Covered solar panels must not produce chemicals");
            for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
                for (int y = 3; y < 10; y++) level.setBlockAndUpdate(pos.offset(x, y, z), Blocks.AIR.defaultBlockState());
            }
        });
        if (kind == LargeMachineKind.ROTARY) helper.runAfterDelay(20, () -> {
            helper.assertTrue(saved[0].save(level.registryAccess()).getList("outputs", Tag.TAG_COMPOUND).isEmpty(),
                    "Changing rotary direction must hold the in-flight cycle");
            helper.assertValueEqual(layout.inputs().getFirst().contents(), unit.inputs().getFirst(),
                    "Mode change must not consume or reverse the in-flight resource");
            ((TileEntityLargeRotaryCondensentrator) layout.tile()).nextMode();
        });
        helper.onEachTick(() -> {
            if (!released[0]) saved[0].advance(level, adapter);
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(saved[0].failure() == null, "Native operation must not fail: " + saved[0].failure());
            var state = saved[0].save(level.registryAccess());
            helper.assertTrue(state.getBoolean("complete"), "Native processing must finish: " + kind);
            if (kind != LargeMachineKind.SOLAR_ACTIVATOR) helper.assertTrue(layout.tile().getEnergyContainers(null).stream()
                    .anyMatch(energy -> energy.getEnergy() < energy.getMaxEnergy()), "Native processing must spend machine energy");
            var returned = new LinkedHashMap<AEKey, BigInteger>();
            var list = state.getList("outputs", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                var entry = list.getCompound(i);
                returned.put(AEKey.fromTagGeneric(level.registryAccess(), entry.getCompound("key")), new BigInteger(entry.getString("amount")));
            }
            for (var output : pattern.getOutputs()) helper.assertValueEqual(returned.get(output.what()), BigInteger.valueOf(output.amount()), "Ledger must contain exactly the physical output");
            helper.assertTrue(layout.inputs().stream().allMatch(p -> p.contents() == null) && layout.outputs().stream().allMatch(p -> p.contents() == null), "Native input/output tanks must be drained exactly once");
            if (!released[0]) {
                claims.release(pos, saved[0].id());
                released[0] = true;
            }
        });
    }

    private record OutputPattern(List<GenericStack> getOutputs) implements IPatternDetails {

        public AEItemKey getDefinition() {
            return AEItemKey.of(Items.PAPER);
        }

        public IInput[] getInputs() {
            return new IInput[0];
        }
    }
}
