# 由宿主 Mod 控制的可选接入

公共函数分为菜单、客户端界面和实际派发逻辑三组。集成包装类依赖 DE API，原生菜单、宿主和派发类保持自己的类型。DE 作为可选编译依赖提供给包装类；主类只引用包装类中使用原生类型的方法签名。

## 菜单与屏幕直接接入

在自有菜单构造器完成原生槽位之后，由宿主 Mod 判断一次是否启用：

```java
import net.neoforged.fml.ModList;

if (ModList.get().isLoaded("data_energistics")) {
    ExampleDeMenuCompatibility.attach(this, host);
}
```

隔离的包装类负责 DE 调用。以下 `ExampleMenu` 和 `ExampleHost` 都是宿主 Mod 原有类型；它们无需实现 DE 接口：

```java
import com.fish_dan_.data_energistics.api.registry.provider.menu.PatternProviderMenuRegistration;
import com.fish_dan_.data_energistics.api.registry.provider.menu.RedstoneTuningMenuHost;

public final class ExampleDeMenuCompatibility {
    private ExampleDeMenuCompatibility() {}

    public static void attach(ExampleMenu menu, ExampleHost host) {
        PatternProviderMenuRegistration.attachRedstoneTuning(menu, new RedstoneTuningMenuHost(
                host.getTuningUpgrades(),
                host::hasTuningCard,
                host::getTuningMode,
                host::setTuningMode));
    }
}
```

该函数追加升级槽、原生 `DataSlot` 和模式修改 action，并将控制对象保存在该菜单中。菜单应在客户端和服务器各接入一次；同一菜单继承链使用直接接入或注册工厂中的一种。未注册菜单工厂的菜单会保留这个直接接入对象。

客户端屏幕同样在构造器完成原生按钮和 widgets 后调用隔离的客户端包装类：

```java
import com.fish_dan_.data_energistics.api.registry.provider.client.PatternProviderScreenRegistration;
import com.fish_dan_.data_energistics.api.registry.provider.client.PatternProviderUpgradeLayout;

public final class ExampleDeScreenCompatibility {
    private ExampleDeScreenCompatibility() {}

    public static void attach(ExampleScreen screen) {
        PatternProviderScreenRegistration.attachRedstoneTuning(
                screen, PatternProviderUpgradeLayout.INLINE_SINGLE);
    }
}
```

这会为该屏幕安排一次初始化，在第一次布局前安装按钮和升级面板。窗口缩放复用已创建的控件。高屏幕右侧升级条使用 `RIGHT_PANEL_VERTICAL`。客户端包装类仅由客户端调用入口引用。

## 实际派发逻辑的包装

对于自有派发实现，可用回调描述真实的宿主和缓冲状态，直接创建共享策略。以下方法放在隔离的兼容类中；回调都读取当前状态，`sendingInputs` 包括宿主自己的发送缓冲和外部目标中的待发送输入：

```java
import com.fish_dan_.data_energistics.api.registry.provider.dispatch.PatternProviderDispatchHooks;
import com.fish_dan_.data_energistics.api.registry.provider.dispatch.RedstoneTuningDispatchContext;

import net.minecraft.server.level.ServerLevel;

private static PatternProviderDispatchHooks createHooks(ExampleLogic logic, ExampleHost host) {
    var context = RedstoneTuningDispatchContext.builder()
            .hasCard(host::hasTuningCard)
            .mode(host::getTuningMode)
            .tick(host::tickTuning)
            .inputChanged(host::scheduleTuningInputCheck)
            .consumeInputPulse(host::consumeTuningPulse)
            .dispatched(host::onTuningDispatch)
            .sendingInputs(logic::hasPendingInputs)
            .requestOutputs(() -> {
                if (!(host.getBlockEntity().getLevel() instanceof ServerLevel level)) {
                    return false;
                }
                PatternProviderDispatchHooks.requestPrimaryOutputs(
                        level, host.getGrid(), logic.getActionSource(), logic.getAvailablePatterns());
                return true;
            })
            .build();
    return PatternProviderDispatchHooks.redstoneTuning(context);
}
```

标准模式会在成功派发完成时通知宿主，并在工作循环前消费待处理解锁脉冲。共振或机械实现若需要在派发完成时额外消费待处理请求脉冲，使用 `requestingRedstoneTuning(context)`。每个逻辑实例创建自己的策略。

宿主 Mod 可用自己定义的回调 record 包装 DE 策略，使原生类不出现 DE 字段类型：

```java
import java.util.function.BooleanSupplier;

// 由宿主 Mod 定义，与 DE 无关。
public record OptionalProviderDispatch(
        Runnable beforeWork, Runnable afterWork, Runnable inputChanged,
        Runnable pushed, BooleanSupplier forceUnlock) {}
```

兼容类对外的方法签名仍只使用宿主类型：

```java
public static OptionalProviderDispatch create(ExampleLogic logic, ExampleHost host) {
    var hooks = createHooks(logic, host);
    return new OptionalProviderDispatch(hooks::beforeWork, hooks::afterWork,
            hooks::redstoneInputChanged, hooks::patternPushed, hooks::forcePulseUnlock);
}
```

原生逻辑构造时只检查一次加载状态：

```java
this.optionalDispatch = ModList.get().isLoaded("data_energistics")
        ? ExampleDeLogicCompatibility.create(this, host)
        : null;
```

随后在对应的原生调用点执行这些回调：

| 原生调用点 | 包装后的回调 |
| --- | --- |
| 工作循环前 | `beforeWork().run()` |
| 工作循环完成后 | `afterWork().run()` |
| 红石输入变化时 | `inputChanged().run()` |
| 普通或 counted push 成功后 | `pushed().run()` |
| 原生主动脉冲解锁时 | `forceUnlock().getAsBoolean()` |

`optionalDispatch` 为 `null` 时沿用原生逻辑；工作循环不再重复查询 `ModList`。回调实例的生命周期由对应原生逻辑对象持有。工作、完成通知和产物请求在服务器线程执行；输入回调沿用宿主对客户端输入通知的处理。

## 使用现有原生钩子注册

当原生逻辑继承 AE2 或 Advanced AE、可以复用 DE 的通用派发钩子时，使用 `dispatchHooks` 注册该具体类型的策略。此时由通用 Mixin 自动转发生命周期，无需再从原生代码重复调用同一组回调：

```java
import com.fish_dan_.data_energistics.api.registry.provider.definition.PatternProviderRuntimeRegistration;

import net.minecraft.resources.ResourceLocation;

registry.patternProviders().registerRuntime(PatternProviderRuntimeRegistration
        .<ExampleLogic>builder()
        .registrationId(ResourceLocation.fromNamespaceAndPath("example_mod", "dispatch"))
        .providerType(ExampleLogic.class)
        .priority(100)
        .dispatchHooks(logic -> createHooks(logic, logic.getNativeHost()))
        .build());
```

注册发生在 DE 插件事务中。可选插件类通过 `@DataEnergisticsEntrypoint(requiredMods = "example_mod")` 声明原生类型加载条件；客户端注册放入 `clientOnly = true` 的入口。菜单工厂与屏幕工厂的注册方式见 [运行时类型注册](runtime-registration.md)。
