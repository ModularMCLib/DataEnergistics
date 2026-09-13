# Adaptive 左侧工具栏注册

工具栏分为 common 动作声明和 client 按钮工厂两个部分，均通过 Data Energistics 自己的 annotation entrypoint 注册。common 阶段不加载客户端类，也不需要 Mixin 或 EventBusSubscriber。

## 已接入的动作

| 动作 ID 常量 | 作用 | 声明范围 |
| --- | --- | --- |
| `BLOCKING_MODE` | 普通 AE2 阻挡模式 | 所有 Adaptive 菜单 |
| `LOCK_CRAFTING_MODE` | 合成锁定模式 | 所有 Adaptive 菜单 |
| `PATTERN_ACCESS_TERMINAL` | 在样板管理终端显示或隐藏 | 所有 Adaptive 菜单 |
| `PREVIOUS_PAGE` / `NEXT_PAGE` | 样板翻页 | 所有 Adaptive 菜单，单页时隐藏 |
| `REDSTONE_TUNING` | 红石调谐卡模式 | 所有 Adaptive 菜单，无卡时隐藏 |
| `FILTERED_IMPORT` | 输入过滤 | Advanced AE 的 provider 注册 |
| `RESONATING_PULL` | 抽取目标面容器 | AE2CS 谐振及扩展谐振 provider 注册 |

这些常量位于 `AdaptivePatternProviderToolbarActions`。普通阻挡与第三方的智能阻挡是不同设置；本次不接入智能阻挡和智能翻倍。帮助、优先级和升级槽继续由 AE2 的界面组件提供。

## Common 入口声明

在 `DataEnergisticsPlugin.register` 中创建 `AdaptivePatternProviderRegistration` 时，通过第四个参数声明 provider 专属按钮。例如已经提供输入过滤行为的 provider 可以使用：

```java
import com.fish_dan_.data_energistics.api.registry.adaptive.AdaptivePatternProviderRegistration;
import com.fish_dan_.data_energistics.api.registry.adaptive.AdaptivePatternProviderToolbarAction;
import com.fish_dan_.data_energistics.api.registry.adaptive.AdaptivePatternProviderToolbarActions;

import it.unimi.dsi.fastutil.objects.ObjectList;

registry.adaptivePatternProviders().register(new AdaptivePatternProviderRegistration(
        registrationId,
        definition,
        dispatch,
        ObjectList.of(new AdaptivePatternProviderToolbarAction(
                AdaptivePatternProviderToolbarActions.FILTERED_IMPORT))));
```

`definition` 和 `dispatch` 是该 provider 已注册的识别与分发逻辑。按钮声明本身不添加服务端功能：输入过滤需要 `FILTERED_IMPORT` capability，目标面抽取需要 `RESONATING` capability。

公共动作由菜单统一提供，不必在每个 provider 中重复声明；重复的公共动作 ID 会合并。每种模组的专属动作声明保留在自己的 `integration/ae/<mod>` 注册类中，公共构造工厂不通过模组分支推导按钮。

## Client 入口注册工厂

注册自定义动作时，客户端入口要提供相同动作 ID 的工厂。下面的入口示例注册一个使用既有 AE2 设置通道的终端显示按钮：

```java
package com.example.integration.client;

import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsEntrypoint;
import com.fish_dan_.data_energistics.api.entrypoint.client.DataEnergisticsClientPlugin;
import com.fish_dan_.data_energistics.api.entrypoint.client.DataEnergisticsClientRegistry;
import com.fish_dan_.data_energistics.api.registry.adaptive.client.AdaptivePatternProviderToolbarButton;

import appeng.api.config.Settings;
import appeng.api.config.YesNo;
import appeng.client.gui.Icon;
import appeng.client.gui.widgets.ToggleButton;
import appeng.core.localization.GuiText;
import appeng.core.network.serverbound.ConfigButtonPacket;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

@DataEnergisticsEntrypoint(clientOnly = true, requiredMods = "example_mod")
public final class ExampleProviderToolbar implements DataEnergisticsClientPlugin {
    @Override
    public void register(DataEnergisticsClientRegistry registry) {
        registry.adaptivePatternProviderToolbar().register(
                ResourceLocation.fromNamespaceAndPath("example_mod", "terminal_visibility"),
                350,
                context -> {
                    var button = new ToggleButton(
                            Icon.PATTERN_ACCESS_SHOW, Icon.PATTERN_ACCESS_HIDE,
                            GuiText.PatternAccessTerminal.text(), GuiText.PatternAccessTerminalHint.text(),
                            ignored -> PacketDistributor.sendToServer(new ConfigButtonPacket(
                                    Settings.PATTERN_ACCESS_TERMINAL,
                                    context.handlingRightClick().getAsBoolean())));
                    return new AdaptivePatternProviderToolbarButton(button,
                            () -> button.setState(context.menu().getShowInAccessTerminal() == YesNo.YES));
                });
    }
}
```

要显示该自定义按钮，common provider 的 `toolbarActions` 还需声明 `example_mod:terminal_visibility`。真实项目通常使用自己的图标、设置和数据通道；这个示例沿用 AE2 设置，便于展示左右键处理与服务端状态同步。

## 生命周期与约束

- 客户端入口只在 queued client setup 执行一次。每个插件单独暂存，回调失败或动作 ID 冲突时丢弃该插件的全部工厂；失败会记录插件类和 owning mod。
- 注册结束即冻结，之后继续写 registrar 会抛出异常。`order` 越小越靠前，同序按动作 ID 排序。
- 工厂每次打开菜单都创建新按钮和更新回调。不得跨 screen 共享可变 widget。
- `AdaptivePatternProviderToolbarContext.menu()` 返回公开的 `AdaptivePatternProviderToolbarMenu` 设置接口，不暴露内部 menu 类。布尔状态和回调使用 `boolean`，没有可空分发结果或数字状态替代。
- 状态更新回调在客户端渲染布局之前执行，只更新可见性、启用状态、图标等显示，不发送网络包。
- provider 槽同步或换入其他 provider 后，菜单重新解析动作声明，已创建的按钮随之显示或隐藏；不需要关闭再打开界面。
- Common 动作 ID 如果没有对应客户端工厂，会报告具体缺失 ID。输入过滤和抽取动作的服务端处理仍校验当前 provider 的声明及 capability，避免使用换入前的按钮状态操作不支持该功能的 provider。
- `@NullMarked` 表达非空 API 契约；ID 冲突、关闭注册后写入等生命周期错误在注册边界显式检查。

对 Native AE2 或附属原生 Provider 界面的既有兼容 Mixin 不在这条 Adaptive 工具栏注册链路中。
