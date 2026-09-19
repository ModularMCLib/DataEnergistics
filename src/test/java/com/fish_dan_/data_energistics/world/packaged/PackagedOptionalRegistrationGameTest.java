package com.fish_dan_.data_energistics.world.packaged;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.entrypoint.DataEnergisticsEntrypointLoader;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class PackagedOptionalRegistrationGameTest {

    @TestHolder("packaged_mekmm_requires_amek_before_loading_optional_classes")
    @EmptyTemplate("9")
    @GameTest(template = "empty_9x9")
    public static void mekmmRequiresAmek(GameTestHelper helper) {
        var registry = DataEnergisticsEntrypointLoader.snapshot().packagedCrafting();
        long actual = registry.adapters().stream().filter(adapter -> adapter.id().getPath().startsWith("mekanism_more_")).count();
        long expected = ModList.get().isLoaded("mekmm") && ModList.get().isLoaded("appmek") ? 6 : 0;
        helper.assertValueEqual(actual, expected, "Every large-machine adapter must be gated by both installed mods");
        helper.succeed();
    }
}
