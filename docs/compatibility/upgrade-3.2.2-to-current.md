# 3.2.2 到当前版本的升级边界

本次迁移只支持正式版本 `v3.2.2-1.21` 写出的格式到当前版本的直接读取。3.2.2 之前的版本和开发期间的中间格式不再作为迁移输入。升级世界、玩家数据和客户端配置前请先备份；本文不承诺降级读取。

## 保留的直接路径

| 数据 | 3.2.2 写出格式 | 当前读取行为 |
| --- | --- | --- |
| Trinity 样板核心 | state version 5；槽位容量 72 / 144 / 576 | 读取并保存为当前 state version 6 |
| Trinity 执行快照 | schema 9；数量使用 byte array；same-item policy 使用代表物品列表 | 读取 schema 9，并与当前 schema 10 共存 |
| Trinity CPU 运行时 | schema 3；`storage_unlimited` 与 `storage_capacity` | 继续读取 schema 3 |
| Trinity CPU 逻辑 | schema 5 | 继续读取 schema 5 |
| Trinity 执行任务 | schema 5 | 继续读取 schema 5 |
| Trinity 借用账本 | schema 2；数量使用 byte array | 继续读取 schema 2 |
| Trinity 已用时间 | byte array 数量统计 | 继续读取当前表示 |
| 可复用材料端点 | schema 2 | 读取 schema 2，并保留当前 schema 3 |
| 塔绑定 | schema 2 | 继续读取 schema 2 与当前 schema 3 |
| 集成充能器 | `storage_layout_version = 2`；三槽流体、机器模式和待退款物品 | 只读取布局版本 2 |
| 异步处理工厂 | `storage_layout_version = 3` | 只读取布局版本 3 |
| Trinity 世界 SavedData | schema 2 | 只读取 schema 2 |

执行快照和可复用材料端点只保留从 3.2.2 到当前的必要路径；中间开发 schema 不计入兼容范围。3.2.2 中仍使用 long 的批次数量、样板槽待处理数量和退款数量继续按原字段读取，这些字段不能因执行快照数量改为 byte array 而一并删除兼容支持。

机器保存数据同样按正式 3.2.2 的布局处理。集成充能器不会再尝试读取旧的 `processing_mode`、单一 `fluid`、旧槽位移位或旧模块槽迁移；缺少或不匹配布局版本会拒绝加载。仍存在的 `legacy_module_refund` 只用于接收 3.2.2 已写出的待退款物品。

## 移除内容

- 3.2.2 之前的执行、运行时、任务、借用账本、样板核心和 SavedData schema。
- 集成充能器更早的单流体、旧槽位和旧模式字段。
- 可复用材料端点 schema 1 以及其他开发期间的中间格式。
- 仅为旧格式存在的容量编码、计划基线迁移和缺失字段回退。

当前代码仍保留正常业务所需的第三方集成、注册别名和网络协议；`legacy` 或 `compatibility` 名称本身不代表可以删除。本文描述的是存档读取边界，不是对所有历史代码名称的机械清理。

升级前请使用与当前版本匹配的客户端和服务端，并保留备份。仓库中的编译检查不能替代真实 3.2.2 世界升级验证。
