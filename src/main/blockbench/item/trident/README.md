# 数据三叉戟模型

`data_trident.bbmodel` 是通过 BBMCP 从 Blockbench 导出的可编辑源文件，包含 40 个方块、逐面 UV 和内嵌的 32×32 纹理。外部纹理使用相对路径，指向 `src/main/resources/assets/data_energistics/textures/item/tool_weapon/data_trident_entity.png`。

运行时资源分别放在以下位置：

- 几何模型：`src/main/resources/assets/data_energistics/models/item/trident/geometry.json`。
- 蓄力显示模型：`src/main/resources/assets/data_energistics/models/item/trident/throwing.json`。
- 物品入口：`src/main/resources/assets/data_energistics/models/item/data_trident.json`。
- 物品图标和模型纹理：`src/main/resources/assets/data_energistics/textures/item/tool_weapon/`。
- 手持和投掷共用的渲染代码：`src/main/java/com/fish_dan_/data_energistics/client/render/trident/`。

重新导出 Java 模型时，应保留所有方块的位置、逐面 UV 和纹理引用。原始导出包含 11 个完全被相邻方块覆盖的未贴图面，运行时模型省略这些内部面，并将粒子纹理指向已导出的贴图；面向 Minecraft 1.21.1 时不使用新版导出的 `format_version` 标记。

模型在编辑器中沿 XY 对角线摆放，`DataTridentModel` 负责以 0.68 倍尺寸将其对齐到原版三叉戟的坐标系。`DataTridentItemRenderer` 仅在第一、第三人称手持时将握点向柄尾移动 4 个模型单位；该偏移不影响投掷实体。手持显示、蓄力、人物手臂动作和投掷飞行继续使用原版流程，不需要额外的关键帧动画文件。
