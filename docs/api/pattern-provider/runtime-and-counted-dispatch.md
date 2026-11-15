# Pattern Provider 运行时绑定与 Counted Dispatch

冻结的 provider registration 会在 live AE2 provider publication 挂载时按 identity descriptor 绑定。只有 registration 声明了 `PatternProviderFactory` 时，才为该 provider lifecycle 创建 `CountedCraftingProviderAdapter`。

## Factory context

`PatternProviderFactory.create` 接收 `PatternProviderFactoryContext`：

- `provider()`：live `ICraftingProvider`；
- `container()`：同一 publication 对应的 terminal-visible `PatternContainer`；
- `identity()`：已解析的 stable live identity；
- `metadata()`：被选中的 immutable declaration metadata。

context 只在 factory callback 生命周期内有效。不要把 context 本身放进 computation cache，也不要由此依赖 Data Energistics 的 internal identity 实现。

factory 必须返回非空 adapter。factory 异常或返回 `null` 会记录为该 registration 的绑定失败，并与 AE2 mount lifecycle 隔离。

## 外部 provider identity

无法使用内置 block/part physical identity 时，通过 `registerRuntime` 为 terminal-visible container 的原生类型注册身份回调。原生类无需实现 DE 接口，也无需通过 Mixin 注入接口：

```java
import com.fish_dan_.data_energistics.api.registry.provider.runtime.ExternalPatternProviderIdentity;
import com.fish_dan_.data_energistics.api.registry.provider.definition.PatternProviderRuntimeRegistration;

import net.minecraft.resources.ResourceLocation;

import it.unimi.dsi.fastutil.objects.ObjectList;

registry.patternProviders().registerRuntime(PatternProviderRuntimeRegistration
        .<ExampleContainer>builder()
        .registrationId(ResourceLocation.fromNamespaceAndPath("example_mod", "provider_identity"))
        .providerType(ExampleContainer.class)
        .identitySource(container -> new ExternalPatternProviderIdentity(
                ResourceLocation.fromNamespaceAndPath("example_mod", "provider_family"),
                1,
                ObjectList.of(container.networkId(), container.providerId())))
        .build());
```

`type` 和正数 `schemaVersion` 必须与 `ProviderIdentityDescriptor.External` 一致；`canonicalFields` 必须有固定顺序，并在服务器重启后仍能确定同一 live provider。不要使用显示文本、对象 hash code 或每次启动变化的随机值。

示例中的 `ExampleContainer` 是集成方自己的原生类型，`networkId()` 和 `providerId()` 提供稳定的字符串 ID。身份回调注册在 container 类型上；publication 和终端同步共享同一身份解析路径，每次解析只读取一次身份。

如果 `ICraftingProvider` 本身不是 `PatternContainer`，为它注册 `.runtimeLink(ExamplePublication::host)`，连接到同生命周期的 terminal-visible container。完整类型分派、优先级和菜单示例见[运行时类型注册](runtime-registration.md)。

AE2 `AEBasePart` 子类直接根据 cable-bus 宿主、维度、安装面和部件物品解析物理身份，不要求实现 `PatternProviderLogicHost`。这也适用于 Advanced AE 的普通和小型供应器部件。已有物理身份的供应器通常不需要外部身份回调。

## Counted adapter

最小 adapter 需要实现 `prepareBatch` 和 `prepareBatchForTarget`。`captureCapacityFast` 是可选的容量观察；未提供独立路线时返回空集合，dispatcher 使用 provider 自身的普通单次提交路径。

需要公开多个真实路线或机器容量时，覆盖：

- `captureCapacityFast(pattern, prototype, requestedCount)`：只读捕获当前可用 target；
- `prepareBatchForTarget(..., target)`：为先前发布的准确 target 创建 admission。

`CountedCraftingCapacity` 使用 `OptionalLong.empty()` 表示“无法证明安全上限”。已知 `0` 表示容量耗尽，不能当成 unknown。已知数值必须非负。

Routing mode：

- `TARGETED`：dispatcher 选择并回指准确 target；
- `ORDERED`：adapter 保留选目标职责，并维持自身稳定 routing order；
- `AGGREGATE`：provider 通过一次 aggregate physical submission 接收 counted logical batch。

`CountedCraftingTarget.machine(routeId, machineId)` 适用于可证明的物理机器。同一物理机器可被多个 adapter 路径到达时，应发布相同、provider-independent 的 `machineId`，让 planner 避免超卖。不能证明时使用 `route(routeId)`，不要猜测 machine identity。

完整 prepare/commit 与所有权规则见[counted dispatch 契约](../crafting/counted-dispatch-contract.md)。
