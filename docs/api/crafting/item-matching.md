# 全局三态物品匹配与原生配方规则

三态匹配属于 `api.crafting.matching` 的公共规则。样板编辑、材料备料、发配、动态产物和回收使用同一语义；封包供应器是使用方。扩展原生配方材料声明不需要注册封包机器。

本文按当前源码接口编写，未进行构建、测试或游戏实机验证。部署后仍需验证所集成 Mod 的原生配方、实际材料和回收路径。

## 模式与槽位

`ProcessingMatchMode` 提供三种显式选择：

| 模式 | 物品比较 | 使用条件 |
| --- | --- | --- |
| `EXACT` | 完整 `AEKey`，包括物品组件 | 默认严格语义 |
| `ID` | 同一物品 ID，允许组件不同 | 不允许用另一个物品 ID 替代 |
| `TAG` | 实际物品属于保存的原生配方标签之一 | 必须有明确、非空的原生标签声明 |

模式覆盖加工样板的输入和输出各槽，按原始稀疏槽索引保存。编码补充水桶、种子或返还物时，不能压缩、拆分或重排已有槽，否则模式会错误地应用到其他材料。

选择 Tag 后，系统从样板记录的准确配方 ID 自动解析该材料声明的标签，不从物品的全部标签猜测，也不让用户任意填一个无关标签。没有可用原生声明时拒绝该槽的 Tag 编码。普通固定产物通常没有标签输出声明；标签输入不自动意味着产物也能按 Tag 回收。

同一物品键匹配多个原生角色时，标签必须满足这些角色的共同声明。带组件条件、交集或差集的自定义 Ingredient 不能仅抽出内部标签后丢掉其他约束；当前不能完整解释这类声明时，不提供标签候选。原生配方仍需校验实际材料，Tag 模式不授权绕过机器条件。

## ItemMatchingRule

`ItemMatchingRule` 是不可变记录，字段为 `ProcessingMatchMode mode` 和 `ObjectList<ResourceLocation> tags`。公开集合使用 fastutil；构造器复制标签集合，不能通过修改原集合改变既有规则。也提供接受 `List<ResourceLocation>` 的便利构造器。

```java
import com.fish_dan_.data_energistics.api.crafting.matching.ItemMatchingRule;
import com.fish_dan_.data_energistics.api.crafting.matching.ProcessingMatchMode;

import net.minecraft.resources.ResourceLocation;

import it.unimi.dsi.fastutil.objects.ObjectList;

var exact = ItemMatchingRule.EXACT;
var byId = ItemMatchingRule.ID;
var byTag = new ItemMatchingRule(ProcessingMatchMode.TAG,
        ObjectList.of(ResourceLocation.fromNamespaceAndPath("c", "seeds")));
```

示例标签必须来自正在处理的配方声明，不能直接把示例标签写入任何样板。

- `matches(expected, actual)` 比较非空资源键，不修改组件、不消耗资源，也不比较数量。非物品资源应保持 EXACT；ID／TAG 仅适用于物品。
- `overlaps(own, other, theirs)` 判断两个物品接受域是否可能重叠，用于结算路由冲突检查。
- `save()` 返回独立 NBT 快照；`load(data)` 在存档／网络边界校验模式、标签名称及非空 Tag 声明。无需在每层重复归一化。
- EXACT／ID 必须使用空标签集合，TAG 必须使用非空集合，非法构造会抛异常。

在注册表和标签加载完成后构造并使用规则。标签成员查询应运行在逻辑服务端，或已同步注册表的客户端；不要在不具备安全注册表访问条件的后台线程调用标签比较。

## 注册原生材料角色

通过[插件注册](../entrypoint/plugin-registration.md)提供 `RecipeMatchingRuleAdapter`，然后调用：

```java
registry.recipeMatching().register(new ExampleRecipeMatchingRules());
```

这里的 `ExampleRecipeMatchingRules` 是集成方实现的适配器类型，必须完整实现以下契约：

| 方法 | 职责 |
| --- | --- |
| `id()` | 返回稳定、非空且唯一的注册 ID |
| `inputIngredients(level, recipeId)` | 返回完整原生输入角色，包括激活物和保留催化剂 |
| `outputIngredients(level, recipeId)` | 返回具有原生 Ingredient 约束的输出角色，例如归还催化剂；默认不负责 |

两个材料方法返回 `@Nullable ObjectList<Ingredient>`。`null` 表示该适配器不负责或配方不存在；不可变空列表表示负责该侧，但没有角色。列表位置描述原生角色，不等于样板稀疏槽号；重复角色必须保留，成员不得为 null。不得把非法配方或内部错误用空列表静默吞掉。

没有专属适配器负责时，输入采用 `Recipe.getIngredients()`，输出不推断标签。Botania 的实现补充花药台收尾 reagent 和符文祭坛催化剂；Ars 的实现补充灌注室中央材料与基座材料。返回物的实际 key、数量仍由原生机器决定。

适配器在 common setup 注册一次，运行时无状态，在服务端线程按准确 recipe ID 只读解析。不能持有 Level 或 Recipe 的长期引用，不能加载区块或修改世界。返回列表应为不可变 fastutil 集合，例如 `ObjectLists.unmodifiable(new ObjectArrayList<>(roles))`，并保留 Ingredient 的原始组件约束。

注册器执行事务内和事务间重复 ID 校验；失败插件的声明不发布。成功注册结果冻结为不可变、有确定顺序的集合。外部 Mod 使用 `DataEnergisticsRegistry.recipeMatching()`，不要依赖内部 snapshot、编码组件或解析器类名。

## 重叠数量分配

`ResourceCapacityMatching.accepts` 是公共数量分配求解器：

```java
import com.fish_dan_.data_energistics.api.crafting.matching.ResourceCapacityMatching;

boolean accepted = ResourceCapacityMatching.accepts(
        new long[] { 1, 1 }, new long[] { 1, 1 }, new long[] { 1, 1 },
        (expectedIndex, ruleIndex) -> expectedIndex == ruleIndex,
        (ruleIndex, actualIndex) -> ruleIndex == 0 || actualIndex == 0);
```

示例中第一条规则可以接受任一种实际资源，第二条只能接受实际资源 0。求解器把实际资源 1 分给第一条、实际资源 0 分给第二条，避免先占掉资源 0 导致错误失败。

调用方提供 `long[]` 数量和两个无副作用的授权谓词。该方法构建“预期资源 → 规则容量 → 实际资源”的容量网络，用增广路径处理 EXACT／ID／TAG 的重叠，不按单个物品计数循环。所有数组和谓词非空，容量非负；每次调用独占图状态，内部使用 fastutil 集合。

返回 true 表示所有实际数量都能合法分配，不代表预期数量已经全部到齐。完整结算仍须由调用方检查总量相等；partial 检查允许未到齐，但不能接收无授权或超出容量的实际产物。规则只改变等价关系，不改变配方数量、能量或时间。

## 动态产物路由与升级边界

能做容量分配不等于能确定产物所属任务。动态产物账本会检查接受域是否重叠：同一发配含重叠规则且使用不同结算路由时拒绝；与活动任务的重叠规则也必须满足其路由约束。不能依赖遍历顺序把物品任意记入另一条待完成订单。遇到歧义应收窄标签或统一可证明一致的结算语义。

本次三态规则使用新的样板存储结构，旧布尔“忽略 NBT”样板需要重新编码；升级前先结束旧封包任务并处理其机器内材料。全局 CPU 的既有在途任务仍保留读取兼容：旧 execution schema 及 reusable custody schema 中已明确的 SAME_ITEM 规则，在解码边界恢复为 ID，模板沿用原等待键；不会自动取得 Tag 权限。重新编码后核对各输入／输出槽模式和原生标签来源，真实回收物品不会被改写成样板模板。

数量发配契约另见 [Counted dispatch](counted-dispatch-contract.md)，虚拟完成语义另见 [Virtual output](virtual-output.md)。
