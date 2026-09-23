# 注册 Adaptive Pattern Provider

Adaptive Pattern Provider definition 把第三方 provider item 映射成一个完整 `AdaptivePatternProviderProfile`。从 `registry.adaptivePatternProviders()` 注册，不需要向内部 resolver 增加模组分支。

```java
import com.fish_dan_.data_energistics.api.registry.adaptive.AdaptivePatternProviderProfile;
import com.fish_dan_.data_energistics.api.registry.adaptive.AdaptivePatternProviderRegistration;

import net.minecraft.resources.ResourceLocation;

import appeng.api.stacks.AEItemKey;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;

AdaptivePatternProviderRegistration registration = new AdaptivePatternProviderRegistration(
        ResourceLocation.fromNamespaceAndPath("example_mod", "adaptive_provider"),
        providerStack -> {
            if (!ExampleItems.PATTERN_PROVIDER.is(providerStack)) {
                return null;
            }

            var icon = providerStack.copyWithCount(1);
            AEItemKey terminalIcon = AEItemKey.of(icon);
            if (terminalIcon == null) {
                throw new IllegalStateException("Example provider has no AE item key");
            }
            return new AdaptivePatternProviderProfile(
                    9,
                    icon,
                    terminalIcon,
                    icon.getHoverName(),
                    ObjectArrayList.of(
                            ResourceLocation.fromNamespaceAndPath("example_mod", "ritual")),
                    ObjectArrayList.of(
                            ResourceLocation.fromNamespaceAndPath("example_mod", "ritual_table")),
                    new ObjectOpenHashSet<>());
        });

registry.adaptivePatternProviders().register(registration);
```

## Definition 契约

`AdaptivePatternProviderDefinition.resolve` 接收一个非空候选 stack：

- 返回 `null` 表示该 definition 不识别候选；
- 返回 profile 表示完整认领，profile 的每个字段都必须有效；
- 不得保留或修改传入 stack；
- 应使用精确 item/组件/registry identity 匹配，不使用显示名、Java 类名或 namespace heuristic；
- 应无副作用，不假设只调用一次或固定线程。

registration ID 必须稳定且全局唯一，建议使用集成模组自己的 namespace。重复 ID 会使当前插件的原子 transaction 失败。

运行时 definitions 会按 registration ID 排序后查询。排序只用于确定性，不用于优先级：两个 definitions 同时返回 profile 时会抛出 ambiguity error，不会让较早注册项获胜。

definition 抛出的运行时异常会被记录并隔离，resolver 会继续检查其他 definitions。普通未匹配必须返回 `null`，不要用异常表达。

profile 字段和 capability 约定见[Profile 与 Capability](profiles-and-capabilities.md)。

Provider 专属按钮通过 registration 的 `toolbarActions` 声明，并通过客户端入口注册对应工厂；见[左侧工具栏注册](toolbar-registration.md)。

## 注册派发路由

有专属合成行为时，将 `AdaptivePatternProviderDispatch` 实现传给 registration。路由类直接实现接口，并放在所属集成模块内；核心只提供 `AdaptivePatternProviderDispatchTarget`，不再包含第三方 Mod 的识别、方向映射或机械合成实现。

- `handles(context)` 只负责判断是否认领样板。未认领时核心执行普通 AE2 路径；认领后 `dispatch(context)` 返回 `false` 表示本次未能派发，不会再次执行默认路径。
- `usesSpecialBatchRoute(pattern)` 控制是否采用单次派发准备。必须与该注册的批处理约束一致，避免普通批处理绕过专属路由。
- `dispatch` 实例会在多个供应器间共享。每台机器的可变数据通过 `target.routeState(State.class, State::new)` 获取，不得放在路由实例字段或静态字段中。
- `tick`、`hasWork`、存档和掉落会处理已经离开当前选择的路由，让已接收的输入与输出继续完成。开始新的后台操作前检查 `target.isSelected()`；排空旧缓存不依赖当前是否选中。
- `writeState` 接收以 registration ID 隔离的子标签。核心将这些标签保存在 `adaptive_dispatch_states` 中，切换供应器不会丢弃旧路由数据；暂时没有注册实现的子标签会原样保留。
- 迁移旧根标签时实现 `legacyStateKey()`，返回能够标识旧状态的键。共享旧状态的多个变体应返回相同键；核心优先交给当前选中的注册项，每份旧状态只恢复一次。`readState` 负责校验旧数据与新数据。

回调在宿主的 level 线程执行，不能从异步任务访问运行时 target，也不能在宿主移除后继续使用它。供应器方块与 Part 通过 `AECapabilities.GENERIC_INTERNAL_INV` 暴露返回库存；AppMek 会自动将它适配为化学品 capability，与 EAE、AAE 使用相同路径。不需要另外注册 `Capabilities.CHEMICAL`，也不需要专用化学处理器或工厂。方向限制在通用库存入口检查，返回过滤由库存本身执行。

这次调整替换了尚在开发中的 `DispatchTarget` 专属方法（例如 `pushMeteorite`、`pushAdvancedDirectional`）。使用这些旧方法的集成需要把具体行为移到自己的路由类，并重新编译；旧世界中的已知路由缓存通过上述迁移入口读取。
