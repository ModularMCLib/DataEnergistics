# 公共 GUI 按钮资源

按钮和开关统一放在 `assets/data_energistics/textures/guis/states/top_surface/`，Java、LSS 和 LDLib2 贴图编辑器直接引用该目录。

- `small_button_0/1/2`：10×10 小按钮的普通、悬停和按下表面。
- `medium_button_0/1/2`：14×14 按钮的普通、悬停和按下表面，替代原 `small_write_*` 路径；终端编码、拖动和砧升级按钮共用。
- `small_horizontal_tab` 及选中变体：保留原切换页签轮廓和裁剪坐标。
- `button`、`priority_button`、`wide_button`、`compact_button`：分别保留原 18×18、64×28、160×20、48×20 贴图；对应禁用和高亮变体沿用相同前缀。原 `botton` 拼写统一为 `button`。
- `long_button` 及高亮变体：200×20 长按钮，保留传送锚原先使用的 AE2 19.2.18 外观。
- `switch_horizontal_*`：22×12 横向开关；`switch_vertical_*`：12×22 纵向开关。两组均包含 `off`、`on`、`off_hovered`、`on_hovered` 四种状态，直接使用本轮提供的八张 PNG，不旋转或缩放源文件。
- `radio_*`：14×14 单选按钮的四种状态，按原终端坐标从 AE2 19.2.18 的 `textures/guis/checkbox.png` 提取，保持单选按钮原有外观。
- `checkbox`、`text_field_checkbox`、`structure_switch`、`mode_button`、`button_slot*` 和 `small_forge_button*` 同样使用此目录；重复的三张小锻造按钮只保留一份。原根目录的 `on/off`、`prepare_on/prepare_off` 四张开关图也集中到此目录，保留它们与新开关之间的外观差异。

平台与终端开关使用横向开关资源。各按钮的尺寸、九宫格裁剪、点击事件和状态逻辑保持原有定义；原分散路径不保留副本或兼容转接。贴图编辑器的现有资源名称继续指向新位置，新增横竖开关、单选按钮和长按钮按 `states_top_surface_*` 命名；小锻造按钮仅保留原 `states_top_surface_small_forge_button_*` 条目。
