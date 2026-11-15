# Pattern Provider 运行时类型注册

`registry.patternProviders().registerRuntime(...)` 为已有原生类型提供 DE 运行时能力。原生类无需依赖 DE 的实现接口，集成代码也不必通过 Mixin 向目标类注入这些接口。

注册发生在 `DataEnergisticsPlugin.register` 的 common-setup transaction 中。运行时回调全部在服务器线程调用。注册表冻结后不能追加回调；插件事务失败时，其全部运行时声明不会进入快照。

## 可注册的能力

| Builder 字段 | 原生目标 | 作用 |
| --- | --- | --- |
| `runtimeLink` | `ICraftingProvider` publication | 找到同生命周期的 terminal-visible `PatternContainer` |
| `identitySource` | terminal-visible container | 为无法使用内置物理身份的对象提供稳定外部身份 |
| `matchingMetadataSource` | terminal-visible container | 为没有静态 behavior metadata 的供应器提供当前配方类别、工作方块信息 |
| `menuOpener` | terminal-visible container | 打开该供应器自己的菜单，或返回明确的拒绝、委托结果 |

一个声明可提供一个或多个回调。四个能力分别选择匹配声明，因此 identity 和 metadata 可以来自不同注册。缺少某个回调表示使用后续声明或原生路径，不需要编写占位回调。

## Publication 与 container

示例中的 `ExampleLogic` 和 `ExampleContainer` 是集成方的原生类型；`host()` 返回逻辑对象已有的终端容器。只有集成插件依赖 DE API：

```java
import com.fish_dan_.data_energistics.api.registry.provider.definition.PatternProviderRuntimeRegistration;
import com.fish_dan_.data_energistics.api.registry.provider.runtime.ExternalPatternProviderIdentity;

import net.minecraft.resources.ResourceLocation;

import it.unimi.dsi.fastutil.objects.ObjectList;

registry.patternProviders().registerRuntime(PatternProviderRuntimeRegistration
        .<ExampleLogic>builder()
        .registrationId(ResourceLocation.fromNamespaceAndPath("example_mod", "provider_publication"))
        .providerType(ExampleLogic.class)
        .runtimeLink(ExampleLogic::host)
        .build());

registry.patternProviders().registerRuntime(PatternProviderRuntimeRegistration
        .<ExampleContainer>builder()
        .registrationId(ResourceLocation.fromNamespaceAndPath("example_mod", "provider_container"))
        .providerType(ExampleContainer.class)
        .identitySource(container -> new ExternalPatternProviderIdentity(
                ResourceLocation.fromNamespaceAndPath("example_mod", "provider_family"),
                1,
                ObjectList.of(container.networkId(), container.providerId())))
        .matchingMetadataSource(ExampleContainer::matchingMetadata)
        .build());
```

`matchingMetadata` 接收可空的当前配方类别，返回 `PatternProviderMatchingMetadata`。没有选中类别时收到 `null`；没有识别到目标时返回空 metadata。注册时不要捕获某个世界中的 live container；回调每次使用参数中的当前实例。

AE2 `AEBasePart` 子类和 `BlockEntity` 已有默认物理身份路径。它们通常只需注册菜单或 metadata，无需重新定义外部身份。外部身份字段必须保持固定顺序，且在服务器重启后仍标识同一对象；不要使用显示名、对象 hash code 或每次启动重新生成的随机值。

## 菜单

菜单回调接收原生 provider 和 `ServerPlayer`，并返回 `PatternProviderMenuOpenResult`。先打开菜单，再报告 `OPENED`；无法打开时返回 `DENIED`。返回 `PASS` 表示继续原生菜单路径。

```java
import com.fish_dan_.data_energistics.api.registry.provider.callback.PatternProviderMenuOpenResult;

registry.patternProviders().registerRuntime(PatternProviderRuntimeRegistration
        .<ExampleContainer>builder()
        .registrationId(ResourceLocation.fromNamespaceAndPath("example_mod", "provider_menu"))
        .providerType(ExampleContainer.class)
        .menuOpener((container, player) -> {
            var previousMenu = player.containerMenu;
            container.openNativeMenu(player);
            return player.containerMenu != previousMenu
                    ? PatternProviderMenuOpenResult.OPENED
                    : PatternProviderMenuOpenResult.DENIED;
        })
        .build());
```

按 identity family 声明的完整 group menu adapter 优先执行。它未处理时，才尝试上述单个 provider 回调。

## 选择顺序与缓存

`providerType` 可以是原生类，也可以是目标 Mod 原有的接口。父类型声明覆盖其所有子类。每种能力先选 `priority` 较大的声明；数值相同时保留插件快照中的声明顺序。默认优先级为 `0`。需要覆盖父类型行为时，为更具体类型的声明设置更高优先级。

运行时使用 fastutil 的引用键类型缓存，只在首次遇到某个运行时类时选择回调。缓存不保存 provider 实例、identity 或 metadata，所以同类不同实例和运行中状态变化仍会得到各自的当前结果。

回调异常交给已有的挂载、终端同步或菜单操作边界处理，不会静默改用虚拟身份。身份和绑定声明一次解析后共同返回，终端不会为了查询两者而重复调用外部身份回调。

## API 签名变更

`PatternProviderIdentitySource<T>`、`PatternProviderRuntimeLink<T>`、`PatternProviderMatchingMetadataSource<T>` 现在是注册用的函数式回调；方法接收原生对象参数。原先通过目标类实现这些接口的接入方式不再参与运行时发现。

工作站拓扑使用 `registerWorkstationSource`；目标对象直接实现 `PatternProviderWorkstationSource` 也不再参与发现。已有集成应迁移到注册调用并重新编译。回调迁移不改变物理身份的 canonical schema 或世界存档格式。
