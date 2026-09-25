# Adaptive Provider Profile 与 Capability

`AdaptivePatternProviderProfile` 是一个安装后 provider stack 的完整、不可变视图：

| 字段 | 约束 |
| --- | --- |
| `slotsPerProvider` | 必须大于零 |
| `mainMenuIcon` | 非空 `ItemStack`；构造和读取时都会复制 |
| `terminalIcon` | 非空 `AEItemKey` |
| `displayName` | 非空 `Component`；构造和读取时都会复制 |
| `recipeCategoryIds` | 供应器理解的配方类别 ID；构造时去重、排序并冻结 |
| `workstationItemIds` | 供应器理解的工作方块物品 ID；构造时去重、排序并冻结 |
| `capabilities` | 非空 fastutil `ObjectSet<ResourceLocation>`；构造时复制并冻结 |

不要在 definition 外继续修改用于构造 profile 的 icon、component 或 capability set，也不要依赖对象 identity。消费行为应通过 profile 的值和 `supports(capability)` 判断。

`recipeCategoryIds` 和 `workstationItemIds` 是自适应供应器的静态匹配声明，适用于支持范围不随目标机器变化的 provider。配方查看器提供当前配方的类别和工作方块，两侧匹配后，供应器才会被标记为当前上传目标。

第三方自适应供应器可以直接在 profile 中声明这些集合：

```java
var recipeCategoryIds = ObjectArrayList.of(
        ResourceLocation.fromNamespaceAndPath("example_mod", "ritual"));
var workstationItemIds = ObjectArrayList.of(
        ResourceLocation.fromNamespaceAndPath("example_mod", "ritual_table"));

return new AdaptivePatternProviderProfile(
        9,
        icon,
        terminalIcon,
        icon.getHoverName(),
        recipeCategoryIds,
        workstationItemIds,
        capabilities);
```

`PackagedMachineAdapter` 另外提供默认的 `workstationItemIds()`。封包机器的配方类别继续来自 `recipeTypes()`，工作方块物品 ID 应在对应模组的 integration adapter 中声明。封包供应器不会把这些声明直接当成全局支持范围，而是检查自己当前相邻且已加载的机器，只返回实际识别到的 adapter 元数据；通用供应器逻辑不维护模组 ID 映射表。

## 内置 capability IDs

`AdaptivePatternProviderCapabilities` 当前提供：

- `METEORITE`：AE2 Crystal Science meteorite provider 处理；
- `ADVANCED_PATTERN`：AdvancedAE-specific pattern handling；
- `FILTERED_IMPORT`：filtered-import option；
- `MECHANICAL_CRAFTING`：Applied Create mechanical-crafting dispatch；
- `RESONATING`：resonating-pattern handling。

Capability 是可组合的 `ResourceLocation`，不是封闭 provider-kind enum。只声明实际实现的能力：

```java
ObjectOpenHashSet<ResourceLocation> capabilities = new ObjectOpenHashSet<>();
capabilities.add(AdaptivePatternProviderCapabilities.ADVANCED_PATTERN);
capabilities.add(AdaptivePatternProviderCapabilities.FILTERED_IMPORT);
```

不要因为某 item 来自特定 namespace 就自动附加 capability；namespace 本身不证明行为。如果第三方集成需要 Data Energistics 尚未理解的新行为，仅创建自己的 ID 不会自动添加运行逻辑，应先在公共 API 中形成明确契约。

## Profile 一致性

同一个稳定 item state 应解析出语义一致的 profile。slot count、capabilities 或 identity 不应依赖帧时间、客户端本地配置或不稳定迭代顺序。显示名可以来自 stack 的 hover name，但不能反过来用显示名决定是否匹配。

完整注册示例见[注册 Adaptive Pattern Provider](registration.md)。

Capability 不再隐式添加按钮。要显示输入过滤或目标面抽取，还要在 provider registration 的 `toolbarActions` 中声明相应动作 ID，详见[左侧工具栏注册](toolbar-registration.md)。
