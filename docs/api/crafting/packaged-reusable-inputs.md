# 原生封包的可复用输入

封包机器可通过 `PackagedMachineAdapter.supportsReusableInputs()` 接入数据核心／三位一体 CPU 的可复用输入会话。普通输入按发配次数增加，可复用输入按同一执行目标需要同时持有的数量准备；它们不作为每轮普通产物回到网络。

## 声明与执行

适配需要同时具备两个声明：

- 机器适配器覆盖 `supportsReusableInputs()` 返回 `true`，表示每次原生执行都能通过 `PackagedMachineOperation.returned()` 交回实际的完整物品 key 和数量。
- 插件通过 `registry.reusableInputs().register(...)` 注册 `ReusableInputRuleAdapter`，根据准确配方 ID、具体机器目标、完整单次输入快照和材料角色，为真正不消耗的槽返回 `ReusableInputRule.unchanged(...)`。

当前封包桥只接受 `UNCHANGED`。它不会根据一次观察到的相同返还推断无限使用，也不会把普通材料自动改成催化剂。规则解析和接单准备都在服务端线程上执行，不得修改世界或保留查询中的 live 对象；角色不明时返回空规则。

调用原生机器时，每轮包含本轮耗材和一套已持有的催化剂。只有实际取回的物品才成为下一轮持有资产；整批结束或取消到达安全点后，通过 `ReusableCraftingProviderAdapter.settleReusableSession()` 定向归还 CPU。不能把同一套工具同时记入普通输出和工具结算。

Ars 灌注室是此机制的具体使用者。样板输入包含魔源宝石和基座材料，普通输出只有精华；基座材料由规则适配器识别。单台机器制作 64 个防御精华时，CPU 准备 64 个魔源宝石，以及各一个发酵蛛眼、糖和牛奶桶。并发使用两台机器则各需要一套同时持有的催化剂。

## 提供者扩展

独立封包提供者实现 `ReusableCraftingProviderAdapter`。自适应 profile 可以通过新增的 `AdaptivePatternProviderDispatch.reusableAdapter(target)` 返回同一能力；默认返回 `null`，不影响既有 profile。返回的 adapter 可以是一次查询视图，但 session、投入材料、实际产物和已确认结算历史必须属于提供者的持久状态。

新接单只允许当前启用的路线。切换 profile 后，原路线已经拥有的 session 仍必须可查询、关闭和结算；不能把暂时离线、断开链接或模式切换当作没有资产的证据。`reusableCustody()` 的覆盖范围不完整时必须明确报告。

## 异步与保存约束

原生机器跨多个 Tick 加工，投料不等于完成。执行端必须将 session 的在途材料与机器的物理进度一起保存，使用 session ID、operation ID、append sequence 和目标身份对应同一次执行。

- 未完成时保留在途材料，不进行普通产物结算，也不提前退款。
- 恢复未完成操作时续接已保存的机器阶段，不能再次创建同一份投入。
- 完成记录需要保留实际回收结果的证据，直到 session 完成记账。重载后的已完成检查点只在相同物理证据确认后恢复结算。
- 关闭或让出机器须等到原生安全点。已投入但无法确认归属的物品不会被补造。
- 拆除提供者的恢复凭证同时包含会话和物理操作状态；原位置、同类型和单次兑换约束保持不变。

内部异步 endpoint 和其 NBT codec 不属于稳定外部 API。第三方只依赖 `api.**` 契约；不要调用内部恢复方法来跳过资产核对。

本轮已通过 IDEA 类型检查，并从现有游戏会话采集了修复前的原生拒绝原因；新的会话路径、重载及取消后的实际行为尚未运行构建或实机回归。
