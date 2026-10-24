# 公共 GUI 按钮资源

公共控件表面统一放在 `assets/data_energistics/textures/guis/states/top_surface/`，按控件用途建立目录，以交互状态命名文件。

| 用途目录 | 含义与状态 |
| --- | --- |
| `button/toolbar` | 工具栏按钮：`normal`、`hovered`、`pressed`、`selected`、`disabled` |
| `button/action` | 编码等动作按钮：`normal`、`hovered`、`pressed`、`disabled` |
| `button/icon` | 图标按钮：`normal`、`hovered`、`disabled` |
| `button/step` | 数值步进按钮：`normal`、`hovered`、`pressed`、`disabled` |
| `button/list_entry` | 列表条目：`normal`、`hovered`、`pressed`、`selected`、`disabled` |
| `button/dialog_action` | 对话框操作按钮：`normal`、`hovered`、`disabled` |
| `button/destination_entry` | 目的地选择条目：`normal`、`hovered` |
| `button/flat` | 平面按钮：`normal`、`hovered`、`disabled` |
| `toggle/horizontal`、`toggle/vertical` | 横竖开关：`off`、`on`、`off_hovered`、`on_hovered` |
| `selector/radio` | 单选按钮：`unchecked`、`checked` 及各自的 `hovered` 状态 |
| `selector/mode`、`selector/structure` | 模式、结构选择控件的静态背景 |
| `tab/horizontal` | 切换页签：`normal`、`selected` |
| `slot/group` | 槽位组的完整边框、中段和底部片段 |
| `selection/slot`、`selection/text_field` | 槽位、文本框的选择高亮 |

状态名称来自调用处及实际图像；边框片段和静态背景按组成职责命名。逻辑上不同的按下、禁用或选中状态具有独立资源入口，即使当前图像一致，也可分别被资源包覆盖。

平台与终端开关使用提供的八张横竖开关图，保持原始尺寸。单选图像来自 AE2 19.2.18 的 `textures/guis/checkbox.png`，目的地条目来自同版本的长按钮表面。各控件的尺寸、裁剪、事件和状态逻辑沿用现有定义。

配置按钮的齿轮使用 `top_surface/gear.png`，从 AE2 19.2.18 的 `textures/guis/states.png` 中按 `Icon.COG` 的坐标 `(32, 64)` 裁出原始 16×16 图像。它与 `settings.png`、`small_top_surface/small_cog.png` 的现有图案分开命名。

旧横向开关的四张副本与新资源可见像素相同，仅完全透明像素的 RGB 不同，合并到对应状态。

Java、LSS 和 LDLib2 贴图编辑器直接引用分类后的路径。编辑器资源名使用 `states_top_surface_用途_状态`，目录、资源名和实际状态保持对应，不提供旧路径或旧资源名转接。
