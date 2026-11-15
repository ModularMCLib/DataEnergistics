package com.fish_dan_.data_energistics.common.entrypoint;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.api.registry.provider.definition.PatternProviderRuntimeRegistration;
import com.fish_dan_.data_energistics.api.registry.provider.runtime.PatternProviderMatchingMetadata;

import appeng.helpers.patternprovider.PatternContainer;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

@GameTestHolder(Data_Energistics.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PatternProviderRuntimeRegistrationGameTest {

    private PatternProviderRuntimeRegistrationGameTest() {}

    @TestHolder("provider_runtime_registration_is_atomic_frozen_and_closed")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void registrationIsAtomicFrozenAndClosed(GameTestHelper helper) {
        var accumulator = new PluginRegistrationAccumulator();
        var first = accumulator.createStaging("test", "first");
        var registration = registration("shared");
        first.patternProviders().registerRuntime(registration);
        accumulator.commit(first);
        expectClosed(helper, () -> first.patternProviders().registerRuntime(registration));
        var duplicate = accumulator.createStaging("test", "duplicate");
        duplicate.patternProviders().registerRuntime(registration("isolated"));
        duplicate.patternProviders().registerRuntime(registration);
        expectClosed(helper, () -> accumulator.commit(duplicate));
        duplicate.discard();
        var discarded = accumulator.createStaging("test", "discarded");
        discarded.patternProviders().registerRuntime(registration("discarded"));
        discarded.discard();
        expectClosed(helper, () -> accumulator.commit(discarded));
        var snapshot = accumulator.freeze();
        helper.assertTrue(snapshot.patternProviderRuntimeRegistrations().size() == 1 &&
                snapshot.patternProviderRuntimeRegistrations().getFirst() == registration,
                "Rejected and discarded plugin transactions must not leak runtime registrations");
        expectClosed(helper, () -> accumulator.createStaging("test", "late"));
        helper.succeed();
    }

    private static PatternProviderRuntimeRegistration<PatternContainer> registration(String name) {
        return PatternProviderRuntimeRegistration.<PatternContainer>builder()
                .registrationId(Data_Energistics.id("test/provider_runtime/" + name))
                .providerType(PatternContainer.class)
                .matchingMetadataSource((provider, category) -> PatternProviderMatchingMetadata.empty())
                .build();
    }

    private static void expectClosed(GameTestHelper helper, Runnable action) {
        try {
            action.run();
        } catch (IllegalStateException expected) {
            return;
        }
        helper.fail("Duplicate ownership and closed transactions must reject the operation");
    }
}
