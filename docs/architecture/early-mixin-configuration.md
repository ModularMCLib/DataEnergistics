# 早期 Mixin 配置与加载条件

Mixin 入口统一为 `data_energistics.mixins.json`。实际 Mixin 位于 `core.mixins`，条件注解和 ASM 求值器位于 `core.mixin.condition`，早期配置位于 `core.config`。条件基础设施放在 Mixin 保留包之外，避免普通类被 Mixin 的包加载限制拦截。

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
map.ftbchunks=false

# 禁用一个具体 Mixin。
ae.ae2.client.gui.TooltipsMixin=false
```

祖先禁用优先，子规则 `true` 无法重新启用被祖先禁用的类。隐藏的 `dev` 和 `dev.datagen` 由环境控制，配置文件不能覆盖。委托规则可以正向或反向跟随另一个规则，但不能绕过缺失依赖或已禁用的包祖先。多个 Mod 覆盖时禁用优先，并保留全部来源。

`pattern_encoding.network_backed_blank_pattern_slot` 是空白样板槽的功能开关，关闭它只选择 AE2 的本地槽行为，其他编码终端 Mixin 功能继续工作。

## 注解条件

可选 Mod 的条件直接声明在 Mixin 类上，不再保留包名到 Mod ID 的兼容映射：

```java
@RequireMods({ "avaritia", "emi" })
@Mixin(value = ExtremeSmithingRecipeCategory.class, remap = false)
abstract class ExtremeSmithingEmiInputsMixin {}
```

- `RequireMods`：全部指定 Mod 都存在才通过。
- `RequireModVersion`：指定 Mod 已加载，版本满足上下界；支持独立的包含边界设置。
- `ConflictWithMod`：任意指定 Mod 存在时禁用。
- `RequireEnvironment`：指定环境条件全部满足才通过。

这些注解保留在 class 字节码中。插件使用 ASM 读取元数据，不通过反射或 `Class.forName` 加载 Mixin 和可选 Mod 的类。版本来自启动阶段的 `LoadingModList`。

Sodium 版本低于 `0.8` 时使用 renderer 生命周期钩子；从 `0.8` 起使用 cached batch 生命周期钩子。Sodium 存在时禁用原版地形渲染钩子。

## 无法判定时的行为

已确认的 `false`、缺失依赖、版本不匹配和环境排斥会阻止注入。未知配置键、非法布尔值、无法读取的配置和无法解释的条件元数据默认放行，并记录读取或解析错误。一个无法解释的注解不会覆盖另一个已确认的依赖排斥。

无法解析的配置文件保留原内容，不用默认值覆盖；可正常读取的配置会规范化为规则说明和明确的用户选择。配置没有旧 API、别名或迁移回退层。

集合内部优先使用 fastutil，布尔映射采用 primitive `Object2BooleanMap`，公开快照和集合视图只读。Properties 和 ASM 的边界继续使用它们自身要求的标准 API。
