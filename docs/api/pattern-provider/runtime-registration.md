# Pattern Provider 运行时类型注册

`registry.patternProviders().registerRuntime(...)` 为已有原生类型提供 DE 运行时能力。原生类无需依赖 DE 的实现接口，集成代码也不必通过 Mixin 向目标类注入这些接口。

注册发生在 `DataEnergisticsPlugin.register` 的插件事务中，冻结快照在 common setup 安装。身份、终端元数据和菜单操作由服务器调用；升级库存查询与派发策略工厂跟随原生对象所在的逻辑线程。注册表冻结后不能追加回调；插件事务失败时，其全部声明不会进入快照。

## 可注册的能力

| Builder 字段 | 原生目标 | 作用 |
| --- | --- | --- |
| `runtimeLink` | `ICraftingProvider` publication | 找到同生命周期的 terminal-visible `PatternContainer` |
| `identitySource` | terminal-visible container | 为无法使用内置物理身份的对象提供稳定外部身份 |
| `matchingMetadataSource` | terminal-visible container | 为没有静态 behavior metadata 的供应器提供当前配方类别、工作方块信息 |
| `menuOpener` | terminal-visible container | 打开该供应器自己的菜单，或返回明确的拒绝、委托结果 |
| `upgradeInventorySource` | 原生宿主或逻辑对象 | 读取当前升级库存，替代反射查找 `getUpgrades` / `getLogic` |
| `dispatchHooks` | 原生派发逻辑对象 | 创建该实例的派发、缓冲完成、Tick 与脉冲解锁策略 |

一个声明可提供一个或多个回调。各能力分别选择匹配声明，因此 identity、metadata 和执行策略可以来自不同注册。缺少某个回调表示使用后续声明或原生路径，不需要编写占位回调。

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

## 升级库存与实际派发逻辑

`upgradeInventorySource` 每次返回当前库存，不缓存库存对象。内置的 `IUpgradeableObject` 注册直接调用公开接口；AE2 provider host 的低优先级声明读取其原生逻辑上的升级能力。第三方自己的宿主可直接注册 `HostType::getUpgrades`，无需反射或注入 DE 接口。

`dispatchHooks` 为每个原生逻辑对象创建独立的 `PatternProviderDispatchHooks`。标准 AE2 与 Advanced AE 注册共享的红石派发策略；AECS 共振、陨石供应器和 Applied Create 机械供应器注册完成派发后可消费请求脉冲的策略。类型选择只做一次，完成标记归对应逻辑对象所有，不放入全局实例表。

| 生命周期调用 | 行为 |
| --- | --- |
| `redstoneInputChanged()` | 调用宿主的输入采样或调度入口 |
| `beforeWork()` | 更新红石状态，并消费符合卡和模式条件的待处理解锁脉冲 |
| `patternPushed()` | 记录成功的普通或 counted push；输入已全部送出时立即完成 |
| `afterWork()` | 在发送缓冲清空后结束待完成派发，并通知宿主 |
| `forcePulseUnlock()` | 在脉冲模式下提交一次原生产物请求，并报告原生环境是否接受 |

原生逻辑构造时只创建策略并捕获回调，子类尚未初始化的字段应留到生命周期调用时再读取。Mixin 负责这些原生调用点和必要的私有字段访问；派发条件、完成状态、脉冲消费和自动请求策略位于共享实现。AECS 和 Applied Create 的覆盖方法复用基类保存的同一策略，发送缓冲稍后才清空时，也会由工作循环完成通知。

自有逻辑可用 `RedstoneTuningDispatchContext.builder()` 包装已有方法，然后调用 `PatternProviderDispatchHooks.redstoneTuning(context)` 或 `requestingRedstoneTuning(context)`。普通工作循环、派发完成及合成请求应在服务器线程执行；原生输入变更入口若也在客户端调用，其 `inputChanged` 回调应沿用宿主的逻辑侧处理。

`PatternProviderDispatchHooks.requestPrimaryOutputs(level, grid, actionSource, patterns)` 封装实际自动合成请求。它异步等待计算结果，将真实计划提交回服务器线程；原生网络暂时不存在时不提交。计算、计划提交或第三方产物声明失败会记录上下文，不再被空 `catch` 吞掉。

## 原生菜单与界面扩展

原生供应器菜单使用 `registerMenu` 注册菜单局部的 `PatternProviderRedstoneMenu`，无需让菜单类实现 DE 接口。回调在 AE2 `MenuTypeBuilder` 的原生构造器返回后执行，服务端和客户端都会执行一次，因此可以保持原生槽位顺序，并在首次同步前追加升级槽、`DataSlot` 和 client action。

```java
import com.fish_dan_.data_energistics.api.registry.provider.menu.PatternProviderMenuRegistration;

registry.patternProviders().registerMenu(PatternProviderMenuRegistration.redstoneTuning(
        ExampleMenu.class, ExampleDeCompatibility::menuHost));
```

`menuHost` 返回 `RedstoneTuningMenuHost`，用库存、卡状态读取、模式读取和模式修改四项回调包装原生宿主。原生菜单和宿主本身都不必实现 DE 接口。模式 `0` 表示派发时发出信号，模式 `1` 表示消耗脉冲以解锁一次派发。客户端读取同步副本，服务器在接收 client action 时验证模式范围。宿主已有内置 DE 红石能力时，也可只传菜单类型；完全自定义控制可使用 record 构造器提供 `PatternProviderRedstoneMenu` 工厂。

内置 AE2、Advanced AE 和 AECS 声明复用 `RedstoneTuningMenuBinding`。红石状态通过一个原生 `DataSlot` 同步，升级卡存在状态和模式打包为整数，不再对三种菜单分别注入 `@GuiSync` 字段和 `broadcastChanges` 实现。该绑定只在创建时解析一次宿主能力；内置宿主能力仍由必要的 Mixin 提供。

客户端通过 `DataEnergisticsClientPlugin` 的 `patternProviderScreens()` 注册界面工厂，注册键也是原生菜单类型：

```java
import com.fish_dan_.data_energistics.api.registry.provider.client.PatternProviderScreenRegistration;
import com.fish_dan_.data_energistics.api.registry.provider.client.PatternProviderUpgradeLayout;

registry.patternProviderScreens().register(PatternProviderScreenRegistration.redstoneTuning(
        ExampleMenu.class, PatternProviderUpgradeLayout.INLINE_SINGLE));
```

界面工厂在完整的子类构造结束后、第一次布局之前执行，可通过 context 访问屏幕、已有 widgets 和工具栏按钮入口。返回的 `Runnable` 在原生 `updateBeforeRender` 完成后、widgets 更新前执行；不需要每帧处理时返回 `null`。窗口缩放和重复 `init` 复用已创建的按钮，不再调用工厂。

菜单和界面声明均优先使用距离实际菜单类最近的注册父类，同一菜单类只能声明一次。冲突只在插件注册事务提交时验证，运行时只查询 fastutil 引用键快照。不要在静态回调里捕获某个已打开菜单或界面。

AE2、Advanced AE 普通及小型供应器、AECS 可升级供应器的七个专用菜单和界面 Mixin 已迁为上述注册。通用菜单工厂、受保护菜单 API 桥接和界面生命周期钩子继续由 Mixin 接入；具体红石槽位、状态同步、按钮和样式实现位于注册工厂中。AECS 功能仍随插件正常注册。

可修改原生源码的 Mod 还可使用 `attachRedstoneTuning` 直接接入菜单和屏幕，并由自己的 `ModList.get().isLoaded("data_energistics")` 入口控制启用。构造时机、兼容包装与非 UI 的派发回调示例见 [可选依赖包装接入](optional-compatibility.md)。

## 选择顺序与缓存

`providerType` 可以是原生类，也可以是目标 Mod 原有的接口。父类型声明覆盖其所有子类。每种能力先选 `priority` 较大的声明；数值相同时保留插件快照中的声明顺序。默认优先级为 `0`。需要覆盖父类型行为时，为更具体类型的声明设置更高优先级。

运行时回调使用 JVM `ClassValue` 缓存原生类型的能力选择，可由客户端宿主查询和服务器派发共享，避免并发修改普通引用键 Map。缓存不保存 provider 实例、identity、metadata 或库存，所以同类不同实例和运行中状态变化仍会读取各自的当前数据。菜单和界面声明继续使用 fastutil 引用键快照，在创建时查找最近的注册父类。

回调异常交给已有的挂载、终端同步或菜单操作边界处理，不会静默改用虚拟身份。身份和绑定声明一次解析后共同返回，终端不会为了查询两者而重复调用外部身份回调。

## API 签名变更

`PatternProviderIdentitySource<T>`、`PatternProviderRuntimeLink<T>`、`PatternProviderMatchingMetadataSource<T>` 现在是注册用的函数式回调；方法接收原生对象参数。原先通过目标类实现这些接口的接入方式不再参与运行时发现。

工作站拓扑使用 `registerWorkstationSource`；目标对象直接实现 `PatternProviderWorkstationSource` 也不再参与发现。已有集成应迁移到注册调用并重新编译。回调迁移不改变物理身份的 canonical schema 或世界存档格式。

原 `PatternProviderMenuAccessor` 已移除。菜单局部控制改用 `PatternProviderRedstoneMenu`，第三方菜单通过 `registerMenu` 提供该控制对象；客户端扩展改用 `patternProviderScreens()`。本次接口调整不提供旧签名兼容层。
