# 远程连接器链接契约

共享链接类型位于 `api.registry.connector`。连接器物品可用于均分塔、自适应样板供应器和极限承载接口；`ConnectorEndpoint` 负责后两者的远程链接编辑，均分塔继续使用其塔绑定流程。

## 链接与宿主

`ConnectorLink` 保存绝对 `BlockPos`、目标 capability 查询面 `Direction`、独立 `ConnectorMode` 和接口库存槽位。自适应供应器的槽位为 `-1`；接口槽位从 `0` 开始，对应库存逻辑槽位，不能按当前菜单页重新编号。

`INPUT` 向目标发送输入，`PULL` 从目标抽回，`BOTH` 参与两个方向。每条链接保存自己的模式。修改 `ConnectorEndpoint.mode()` 对应的默认模式只影响后续新建链接，不能改变已有链接的模式。

远程能力只属于完整方块。自适应供应器和极限承载接口的面板形态不能绑定远程链接。连接器中的宿主维度必须与玩家当前维度一致；未加载的目标不会触发区块加载。粘贴使用剪贴板里的原始绝对目标，不能相对新宿主平移，也不能跨宿主类型或维度粘贴。

## 编辑与路由

`ConnectorEndpoint` 只能在宿主所在世界线程使用。`bindings()` 返回按注册顺序排列的不可变快照；写操作由宿主负责保存并请求正常方块更新。`toggle` 以目标位置、查询面和槽位识别同一条链接，新增时返回 `true`，删除时返回 `false`。`replace` 替换完整集合并返回实际链接数。

调用方必须先验证玩家操作权限、宿主类型、维度和当前解锁槽位。接口保留容量卡卸下后锁定槽位的已有链接，但暂停执行这些链接；因此 `replace` 允许保留合法范围内的锁定槽位。新建或粘贴链接必须使用当前解锁槽位。

`ConnectorRouteTargets.resolve(target, INPUT/PULL)` 按请求方向筛选链接，双向链接参与两类查询。已配置链接但该方向没有匹配项时返回空集合；仅在完全没有配置链接时回退到供应器相邻目标。消费方必须直接使用返回链接的绝对位置和绑定面，不能再按供应器位置计算相邻目标。

极限承载接口的输入读取绑定库存槽位；抽回内容直接进入返回库存，不受配置标记和相邻面主动拉取设置控制。链接顺序、模式、槽位、链接遍历游标和每条链接独立的来源游标随方块存档保存，多个目标不会共用来源游标。暂时无法进入返回栏的已抽取内容也会保存，后续继续尝试返回。

## 名称迁移与兼容性

旧 API 名称迁移如下，下游源码需要更新 import 并重新编译：

| 原类型（`api.registry.adaptive`） | 新类型（`api.registry.connector`） |
| --- | --- |
| `AdaptiveProviderConnectorBinding` | `ConnectorLink` |
| `AdaptiveProviderConnectorMode` | `ConnectorMode` |
| `AdaptiveProviderConnectorRoutes` | `ConnectorRouteTargets` |
| `AdaptiveProviderConnectorPolicy` | `ConnectorPolicy` |

`ConnectorLink` 保留不带槽位的二参数、三参数构造形式，默认槽位为 `-1`。这些 Java 类名调整不改变物品注册 ID、数据组件 ID、网络包 ID 和已有自适应链接的 NBT 字段。旧剪贴板缺少宿主类型时按自适应供应器处理，缺少槽位时使用 `-1`。

连接器物品、剪贴板、滚轮包和渲染器内部类改用 `RemoteLink` / `Connector` 命名，它们不属于稳定 API。策略存档字段也统一为 `connector_policy`（模式、游标和目标字段使用对应的 `connector_*` 名称）；读取 3.2.2 存档时只接受一次旧 `adaptive_connector_*` 字段并写回新字段，不保留其他中间版本格式。数据组件流编码新增接口槽位和全选状态，客户端与服务端必须使用匹配版本；当前网络协议版本为 `26.9.13`，后续继续采用年、月、日格式。
