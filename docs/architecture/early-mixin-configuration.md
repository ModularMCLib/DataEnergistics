# 早期 Mixin 配置与加载条件

Mixin 入口统一为唯一的 `data_energistics.mixins.json`，所有条目相对于 `core.mixins`，早期配置位于 `core.config`。加载条件由内嵌的 conditional-mixin 0.6.5 处理，工程不再维护条件注解或 ASM 求值器。

```text
core/mixins/
├── DataEnergisticsMixinPlugin.java
├── normal/          # 普通 Mixin，继续按目标类的包名分层
├── target/          # PolyMixin 动态目标提供器，不注册为 Mixin 条目
└── dev/             # 仅开发环境启用
    └── datagen/     # 同时要求开发环境和 datagen 启动器
```

普通兼容 Mixin 的目标包名可能以 `dev` 开头，例如 EMI、Flywheel 和 FTB；这些类属于 `normal.dev.*`，不受开发环境规则限制。对照 `ec0608803d6e4a6adb95ea48cf5dc5b7e075e8c0` 的父版本，目前没有实际的开发或 datagen 专用 Mixin；两个包保留用途说明，新增注入时仍在同一个 JSON 中登记 `dev.*` 或 `dev.datagen.*`。

配置在启动阶段读取一次，修改后需要完整重启。文件为 `config/data_energistics/data_energistics-early.properties`，按名称排序保存，使用 UTF-8 无 BOM。

## 配置职责

| 类型 | 职责 |
| --- | --- |
| `DataEnergisticsEarlyConfig` | 单例入口、只读规则查询和统计 |
| `EarlyMixinRuleDiscovery` | 从唯一 Mixin JSON 自动注册各级包及完整类名规则 |
| `EarlyConfigBuilder` | 注册规则、解析用户选择、构建不可变快照 |
| `EarlyConfigProperties` | Properties 文件读取和保存 |
| `Option` | 默认值、用户来源、Mod 来源、父级和委托状态 |

包与类规则默认启用。例如：

```properties
# 禁用整个包及其子包。
normal.dev.ftb.mods.ftbchunks=false

# 禁用一个具体 Mixin。
normal.appeng.core.localization.TooltipsMixin=false
```

祖先禁用优先，子规则 `true` 无法重新启用被祖先禁用的类。隐藏的 `dev` 和 `dev.datagen` 由环境控制，配置文件不能覆盖。委托规则可以正向或反向跟随另一个规则，但不能绕过缺失依赖或已禁用的包祖先。多个 Mod 覆盖时禁用优先，并保留全部来源。

普通规则新增 `normal.` 前缀。已登记的旧包名和旧类名配置会自动迁移到对应的新名称，保存时只输出新键；同时存在旧键与新键时，以显式的新键为准。旧的隐藏环境键 `dev`、`dev.datagen` 仍不能被配置覆盖，也不会迁移为普通规则。

空白样板槽的功能设置位于 `DataEnergisticsConfiguration` 的主 YAML 文件 `config/data_energistics/data_energistics.yaml`，不属于早期 Mixin 规则，也不放入只负责提取器产出规则的 `DataExtractorRulesConfiguration`：

```yaml
patternEncoding:
  networkBackedBlankPatternSlot: true
```

关闭该选项只选择 AE2 的本地槽行为，其他编码终端 Mixin 功能继续工作。`@Configurable.Synchronized` 标记在布尔字段上，由 Configuration 在玩家登录时下发服务端值、直接更新现有配置实例，并在客户端断线时恢复本地值。菜单及其客户端界面直接读取同一个配置，不再保存菜单模式副本、用 `@GuiSync` 重复同步开关或通过菜单方法中转配置查询。`BlankPatternProxyMenu` 只保留空白样板存取操作，客户端仍需检查菜单是否具备此能力。

Configuration 3.1.1 将同步字段限制为 `MAIN_MENU` 更新，游戏过程中不应用修改；不能再叠加 `GAME_RESTART`，否则库会拒绝注册。此注解不能标记在分组对象上。

旧 Properties 键 `pattern_encoding.network_backed_blank_pattern_slot` 已移除，不提供别名或自动迁移；如果此前显式设为 `false`，请将上面的 YAML 字段设为 `false`。旧键会按未知早期规则忽略，并在配置规范化时清理。

## 注解条件

可选 Mod 的条件直接声明在 Mixin 类上，不再保留包名到 Mod ID 的兼容映射：

```java
@Restriction(require = { @Condition("avaritia"), @Condition("emi") })
@Mixin(value = ExtremeSmithingRecipeCategory.class, remap = false)
abstract class ExtremeSmithingEmiInputsMixin {}
```

- `require` 中的所有 `@Condition` 都满足才通过。
- `conflict` 中的任意 `@Condition` 满足时禁用。
- `@Condition(value = "sodium", versionPredicates = "[0.6,0.8)")` 使用 NeoForge 的 Maven 版本区间，不能使用 Fabric 的 `>=0.6 <0.8` 语法。
- 开发和 datagen 环境继续由隐藏的 `dev`、`dev.datagen` 规则控制。

插件继承 `RestrictiveMixinConfigPlugin`，先执行早期配置开关，再委托库读取 class 元数据及启动阶段的 Mod 版本。条件声明只使用 MOD 条件，不使用需要反射实例化的 TESTER 条件。`preApply` 调用父类并保留 Celestweave 的应用前诊断，`postApply` 直接沿用父类实现，使合并到目标类的条件注解继续得到清理。

Sodium 的通用钩子要求 `[0.6,)`，renderer 生命周期钩子要求 `[0.6,0.8)`，cached batch 生命周期钩子要求 `[0.8,)`。Sodium 存在时禁用原版地形渲染钩子。

## 第三方 Mixin 回调

MixinSquared 0.3.7 通过内嵌 NeoForge GAMELIBRARY 的初始化 Mixin 自动启动，不声明虚构的 Mod ID，也不手工重复 bootstrap。

`PatternEncodingTermMenuExtendedaeplusMixin` 以 `priority = 1500` 和 `@TargetHandler` 定位 EAEP 的 `ContainerPatternEncodingTermMenuMixin.eap$serverUploadAfterEncode(CallbackInfo)`。菜单具备 DE 主编码 Mixin 的 `PatternEncodingPreferenceMenu` 能力时，阻止该回调再次上传，使用现有 handoff 完成上传；主编码 Mixin 未启用时放行原回调。兼容 Mixin 的配置开关同时控制此拦截和 handoff。主编码 Mixin 使用默认优先级，不再依赖 `900` 的应用顺序绕行。

该目标使用 `require = 1`，EAEP 改名或改变签名时会报告不匹配。新增第三方回调兼容时必须指定来源 Mixin 和完整方法签名，并声明对应 Mod 条件。

## 动态机器目标

PolyMixin 1.0.2 只在 `LargeMachineOutputOwnershipMixin` 上通过 `@DynamicTargets` 启用。该接口 Mixin 声明 `IBoundingBlock` 目标，库将它转换为具体类的注入副本；插件本身不实现 `DynamicTargetProvider`，其他 Mixin 维持原来的目标。

官方当前只提供 CurseMaven 下载方式。`forge` 版本目录记录 CurseForge 文件 ID `8799357`，`libs` 记录真实版本 `1.0.2`。依赖直接使用 CurseMaven 原始坐标，并沿用项目现有的 `jarJar(api(...))` 打包方式。

`core.mixins.target.LargeRecipeMachineTargets` 与普通注入分包，不登记为 Mixin 条目；它只在 PolyMixin 的启动目标发现阶段使用。目标提供器复用库的 ClassGraph 继承信息，发现所有已加载 Mod 中继承 `TileEntityRecipeMachine`、实现 `IBoundingBlock` 的类型，再通过 Mixin 字节码 SPI 检查其自身声明的 `handleEject()V`。只接收具有方法体的实例方法，排除接口、抽象方法、native 方法、静态方法和错误签名。库的共享扫描不包含方法元数据，因此不调用 ClassGraph 的方法查询 API，也不加载可选机器类。

当前 Mekanism More Machine 的五个原始目标符合该契约，其他 Mod 的同契约机器也会自动接入。加载条件为 `mekanism` 与 `appmek`，不要求安装 Mekanism More Machine。输出归属判断继续只读取机器占用状态，重复经过父子类钩子不会消耗或重复产出物品。

注解和提供器均设置 `relaxInjectionRequirements = false`，注入要求为 `require = 1`。无法读取候选类时报告具体类名，不用静默放宽匹配掩盖问题。新增目标只需满足契约，不需要修改类名列表。

`normal.com.jerry.meklm.common.tile.machine.LargeMachineOutputOwnershipMixin` 类级配置键及其包祖先控制全部动态副本，旧键按普通规则迁移。PolyMixin 将副本的 `shouldApplyMixin` 查询转交给原始名称，早期配置不会为运行时生成类添加新键。

## 无法判定时的行为

已确认的 `false`、缺失依赖、版本不匹配和环境排斥会阻止注入。未知配置键、非法布尔值和无法读取的 Properties 配置保留原有容错。条件元数据由 conditional-mixin 自行处理：缺失的 Mixin 类和非法版本区间会拒绝注入，其他元数据错误按库规则报告，不再由工程捕获后强制放行。正常的条件排斥以 DEBUG 记录原因。

无法解析的配置文件保留原内容，不用默认值覆盖；可正常读取的配置会规范化为规则说明和明确的用户选择。兼容处理只覆盖这次普通 Mixin 分包导致的已登记规则改名，不恢复已经移除的功能键。

配置集合内部优先使用 fastutil，布尔映射采用 primitive `Object2BooleanMap`，公开快照和集合视图只读。
