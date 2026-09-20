# Counted Crafting Dispatch 契约

Counted dispatch 允许一个 pattern provider 用一次 physical submission 接收多个相同 logical crafts。该 API 的关键不是循环调用次数，而是 prepare、capacity、commit 之间严格的只读和所有权边界。

## Prepare

`CountedCraftingProviderAdapter.prepareBatch` 接收：

- crafting plan 选中的准确 `IPatternDetails`；
- 每个 pattern input slot 的单次 craft `KeyCounter[]` prototype；
- 正数 `requestedCount`。

prepare 阶段只读：可以检查 provider 当前状态，但不得消费 input、预留容量、推进 routing cursor、修改或保留 prototype。

返回 `null` 表示当前一个 craft 也不能接收。返回 admission 时，`admission.count()` 必须在 `1..requestedCount` 闭区间内；越界会使当前 provider attempt 失败。

## Capacity 与 target

覆盖 `captureCapacity` 时：

- 返回当前可用 target 的非空 immutable observations；
- 空 list 表示没有可用 route；
- 捕获过程不得预留、消费或推进 cursor；
- `OptionalLong.empty()` 是 unknown，已知 `0` 是 exhausted；
- `logicalCrafts` 和 `maximumSingleBatch` 的已知值必须非负。

target route identity 在一个 provider instance 生命周期内必须稳定。只有能证明多个路线指向同一物理机器时才发布共同 machine identity。

覆盖 `prepareBatchForTarget` 时，只为调用方传回的准确 target 准备 admission；target 已失效应返回 `null`，不要偷偷改投另一台机器。

## Admission 与 commit

一个 `CountedCraftingAdmission`：

- 在 server thread 创建；
- 最多 commit 一次；
- 在同一 server thread、针对同一 prototype commit；
- `count()` 是固定正数；
- commit 返回后不得保留 world、grid 或 mutable prototype reference。

`commit(prototype)` 的所有权语义：

- 返回 `true`：provider 接管 prototype 和 admission 表示的全部 logical copies；
- 返回 `false` 或在所有权转移前抛出：必须保持所有 prototype counters 不变；
- 一旦跨过不可逆 provider boundary，必须立刻让 `hasTransferredInputOwnership()` 返回 `true`；
- 该状态一旦为 `true` 必须永久保持，即使之后 commit 返回 `false` 或抛异常；
- 一旦 provider 修改任何 prototype counter，调用方会保守地把整个 admission 当作已转移。

典型 admission 结构：

```java
final class ExampleAdmission implements CountedCraftingAdmission {

    private final long count;
    private boolean transferred;

    ExampleAdmission(long count) {
        this.count = count;
    }

    @Override
    public long count() {
        return this.count;
    }

    @Override
    public boolean hasTransferredInputOwnership() {
        return this.transferred;
    }

    @Override
    public boolean commit(KeyCounter[] prototype) {
        validateWithoutMutation(prototype, this.count);
        this.transferred = true;
        submitToMachine(prototype, this.count);
        return true;
    }
}
```

示例中的 `transferred = true` 必须发生在第一次外部 mutation 之前。不要在 mutation 之后才设置，也不要在 catch 中重新改回 `false`。

## 原生封包容量

`PackagedMachineAdapter.batchCapacity(...)` 为一台已加载、已识别且未被占用的机器报告 `0..requestedCount` 次逻辑合成的容量。参数中的样板和 `KeyCounter[] prototype` 都是一份逻辑合成，方法只读，不得扣料、取得占用或改变轮询游标。默认返回 1；只有能实际接收和加工多份输入的适配器才覆盖它。

封包接单把容量和目标固定在一次性 admission 中，提交时重新检查同一目标和容量，再按 count 缩放输入及输出校验数量。匹配模式、原样板定义、槽索引和标签权限不因批量改变。完整输入转入持久封包操作后，admission 必须报告已转移，后续保存或成功回调异常不能使 CPU 再次使用这份材料。

自适应 profile 通过 `AdaptivePatternProviderDispatch.countedAdapter(target)` 提供可选批量入口，默认 `null` 保留单份路线。独立封包供应器和自适应封包路线均把真实容量提交给 CPU 的容量快照与目标准备路径；仅实现普通 `pushPattern` 而没有 counted 调度的调用方仍按单次接口工作。

Mek 大机器使用原生输入、输出端口容量并持续补料排料；物品实体路线采用真实堆叠上限、能源／魔力和原生配方选择约束。批量不是跳过机器时间或放宽产物数量。可复用催化剂另外遵守[会话契约](packaged-reusable-inputs.md)，不能通过把整套工具乘 count 来实现批量。

## 禁止的实现方式

- prepare 时先扣 input，commit 失败再“尽量退回”；
- 用 `-1` 表示 unknown capacity；应使用 `OptionalLong.empty()`；
- 把 `0` capacity 当作 unknown；
- TARGETED admission 收到旧 target 后改投别的 route；
- commit 返回 `false`，但已经修改 prototype 且仍报告未转移；
- 保存 prototype、world 或 grid 引用供异步线程继续处理。

Provider metadata、factory 和 live identity 见[运行时绑定](../pattern-provider/runtime-and-counted-dispatch.md)。
