package com.fish_dan_.data_energistics.common.entrypoint.provider;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.api.registry.provider.callback.PatternProviderMenuOpenResult;
import com.fish_dan_.data_energistics.api.registry.provider.definition.PatternProviderMetadata;
import com.fish_dan_.data_energistics.api.registry.provider.definition.PatternProviderRegistration;
import com.fish_dan_.data_energistics.api.registry.provider.definition.PatternProviderRuntimeRegistration;
import com.fish_dan_.data_energistics.api.registry.provider.definition.ProviderIdentityDescriptor;
import com.fish_dan_.data_energistics.api.registry.provider.runtime.ExternalPatternProviderIdentity;
import com.fish_dan_.data_energistics.api.registry.provider.runtime.PatternProviderMatchingMetadata;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.CraftingProviderId;

import appeng.api.crafting.IPatternDetails;
import appeng.api.implementations.blockentities.PatternContainerGroup;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.KeyCounter;
import appeng.helpers.patternprovider.PatternContainer;
import appeng.util.inv.AppEngInternalInventory;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import org.jspecify.annotations.Nullable;

@GameTestHolder(Data_Energistics.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PatternProviderRuntimeAdaptersGameTest {

    private PatternProviderRuntimeAdaptersGameTest() {}

    @TestHolder("provider_runtime_callbacks_follow_priority_and_keep_live_metadata")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void callbacksFollowPriorityAndKeepLiveMetadata(GameTestHelper helper) {
        var fallback = PatternProviderRuntimeRegistration.<PatternContainer>builder()
                .registrationId(id("fallback"))
                .providerType(PatternContainer.class)
                .identitySource(provider -> identity("fallback"))
                .matchingMetadataSource((provider, category) -> new PatternProviderMatchingMetadata(
                        category == null ? ObjectList.of() : ObjectList.of(category), ObjectList.of()))
                .build();
        var preferred = PatternProviderRuntimeRegistration.<NativeContainer>builder()
                .registrationId(id("preferred"))
                .providerType(NativeContainer.class)
                .priority(10)
                .identitySource(provider -> identity(provider.key))
                .build();
        var registrations = new ObjectArrayList<PatternProviderRuntimeRegistration<?>>();
        registrations.add(fallback);
        registrations.add(preferred);
        var adapters = new PatternProviderRuntimeAdapters(registrations);
        registrations.clear();
        var provider = new NativeContainer("first");
        helper.assertTrue(adapters.identity(provider).equals(adapters.identity(new NativeContainer("first"))),
                "Native provider instances must share the registered stable identity without injected DE interfaces");
        helper.assertTrue(!adapters.identity(provider).equals(adapters.identity(new NativeContainer("second"))),
                "The class cache must not retain the first provider's identity");
        helper.assertTrue(adapters.matchingMetadata(provider, id("category/first")).recipeCategoryIds()
                .equals(ObjectList.of(id("category/first"))), "Capabilities must compose across matching registrations");
        helper.assertTrue(adapters.matchingMetadata(provider, id("category/second")).recipeCategoryIds()
                .equals(ObjectList.of(id("category/second"))), "Cached type selection must still read the current category");
        helper.assertTrue(adapters.matchingMetadata(provider, null).recipeCategoryIds().isEmpty(),
                "No selected category must reach the callback as null");
        helper.succeed();
    }

    @TestHolder("provider_runtime_unregistered_type_keeps_native_fallback")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void unregisteredTypeKeepsNativeFallback(GameTestHelper helper) {
        var adapters = new PatternProviderRuntimeAdapters(ObjectList.of());
        var provider = new NativeContainer("unregistered");
        helper.assertTrue(adapters.identity(provider) == null && adapters.container(provider) == null,
                "Unregistered native types must use the standard identity and publication paths");
        helper.assertTrue(adapters.matchingMetadata(provider, null) == null && !adapters.hasMenuOpener(provider),
                "Unregistered native types must not advertise registered metadata or menu behavior");
        helper.assertTrue(adapters.openMenu(provider, FakePlayerFactory.getMinecraft(helper.getLevel())) ==
                PatternProviderMenuOpenResult.PASS, "Unregistered menus must delegate to the native path");
        helper.succeed();
    }

    @TestHolder("provider_runtime_equal_priority_uses_declaration_order")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void equalPriorityUsesDeclarationOrder(GameTestHelper helper) {
        var first = PatternProviderRuntimeRegistration.<NativeContainer>builder()
                .registrationId(id("first"))
                .providerType(NativeContainer.class)
                .identitySource(provider -> identity("first"))
                .build();
        var second = PatternProviderRuntimeRegistration.<NativeContainer>builder()
                .registrationId(id("second"))
                .providerType(NativeContainer.class)
                .identitySource(provider -> identity("second"))
                .build();
        var forward = new PatternProviderRuntimeAdapters(ObjectList.of(first, second));
        var reverse = new PatternProviderRuntimeAdapters(ObjectList.of(second, first));
        var provider = new NativeContainer("native");
        helper.assertTrue(!forward.identity(provider).equals(reverse.identity(provider)),
                "Equal priorities must retain declaration order");
        helper.succeed();
    }

    @TestHolder("provider_runtime_mount_and_terminal_share_one_identity_callback")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void mountAndTerminalShareOneIdentityCallback(GameTestHelper helper) {
        int[] calls = { 0 };
        var hostRuntime = PatternProviderRuntimeRegistration.<NativeContainer>builder()
                .registrationId(id("host"))
                .providerType(NativeContainer.class)
                .identitySource(provider -> {
                    calls[0]++;
                    return identity(provider.key);
                })
                .build();
        var publicationRuntime = PatternProviderRuntimeRegistration.<NativePublication>builder()
                .registrationId(id("publication"))
                .providerType(NativePublication.class)
                .runtimeLink(NativePublication::container)
                .build();
        var metadata = new PatternProviderMetadata(id("behavior"),
                new ProviderIdentityDescriptor.External(id("family"), 1), ObjectList.of(), ObjectList.of());
        var behavior = new PatternProviderRegistration(metadata, null,
                context -> PatternProviderMenuOpenResult.PASS, null);
        var registry = new LivePatternProviderBindingRegistry(ObjectList.of(behavior), ObjectList.of(),
                ObjectList.of(hostRuntime, publicationRuntime), provider -> {
                    throw new AssertionError("Registered identity must not use the physical resolver");
                });
        var container = new NativeContainer("shared");
        var publication = new NativePublication(container);
        var publicationId = new CraftingProviderId(1, 1);
        registry.bind(publicationId, publication);
        helper.assertTrue(calls[0] == 1, "Mount must invoke the linked container identity exactly once");
        var resolved = registry.resolveContext(container);
        helper.assertTrue(calls[0] == 2, "Terminal resolution must invoke the identity callback once");
        helper.assertTrue(resolved.binding().registration() == behavior,
                "The native publication and terminal container must select the same behavior declaration");
        registry.unbind(publicationId);
        helper.succeed();
    }

    @TestHolder("provider_runtime_menu_callback_uses_native_provider_without_mixin")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void menuCallbackUsesNativeProviderWithoutMixin(GameTestHelper helper) {
        var container = new NativeContainer("menu");
        var player = FakePlayerFactory.getMinecraft(helper.getLevel());
        var registration = PatternProviderRuntimeRegistration.<NativeContainer>builder()
                .registrationId(id("menu"))
                .providerType(NativeContainer.class)
                .menuOpener((provider, opener) -> {
                    helper.assertTrue(provider == container && opener == player,
                            "Menu callback must receive the selected live provider and player");
                    return PatternProviderMenuOpenResult.DENIED;
                })
                .build();
        var adapters = new PatternProviderRuntimeAdapters(ObjectList.of(registration));
        helper.assertTrue(adapters.hasMenuOpener(container), "Registered menu must be advertised to the terminal");
        helper.assertTrue(adapters.openMenu(container, player) == PatternProviderMenuOpenResult.DENIED,
                "Explicit denial must reach the menu dispatcher");
        helper.succeed();
    }

    @TestHolder("provider_runtime_callback_failure_is_not_hidden_by_virtual_fallback")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void callbackFailureIsNotHiddenByVirtualFallback(GameTestHelper helper) {
        var failure = new IllegalStateException("callback failure");
        var registration = PatternProviderRuntimeRegistration.<NativeContainer>builder()
                .registrationId(id("failure"))
                .providerType(NativeContainer.class)
                .identitySource(provider -> { throw failure; })
                .build();
        var adapters = new PatternProviderRuntimeAdapters(ObjectList.of(registration));
        try {
            adapters.identity(new NativeContainer("broken"));
            helper.fail("A failed external identity callback must reach the operation boundary");
        } catch (IllegalStateException actual) {
            helper.assertTrue(actual == failure, "Callback failure must preserve its original cause");
        }
        helper.succeed();
    }

    private static ResourceLocation id(String path) {
        return Data_Energistics.id("test/provider_runtime/" + path);
    }

    private static ExternalPatternProviderIdentity identity(String key) {
        return new ExternalPatternProviderIdentity(id("family"), 1, ObjectList.of(key));
    }

    private record NativePublication(NativeContainer container) implements ICraftingProvider {

        @Override
        public ObjectList<IPatternDetails> getAvailablePatterns() {
            return ObjectList.of();
        }

        @Override
        public boolean isBusy() {
            return false;
        }

        @Override
        public boolean pushPattern(IPatternDetails pattern, KeyCounter[] inputs) {
            throw new AssertionError("Identity binding must not dispatch a pattern");
        }
    }

    private static final class NativeContainer implements PatternContainer {

        private final String key;
        private final InternalInventory patterns = new AppEngInternalInventory(null, 9);

        private NativeContainer(String key) {
            this.key = key;
        }

        @Override
        public @Nullable IGrid getGrid() {
            return null;
        }

        @Override
        public InternalInventory getTerminalPatternInventory() {
            return this.patterns;
        }

        @Override
        public PatternContainerGroup getTerminalGroup() {
            throw new AssertionError("Registered identity must not inspect display-derived groups");
        }
    }
}
