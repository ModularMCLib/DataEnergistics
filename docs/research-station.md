# 数位化改造研究站

科研站提供主科技、物品升级和玩家强化三种研究。节点先由 Java 注册，Datapack 再覆盖允许修改的规则。本文件中的示例用于下游集成，不会自动加入游戏。

科研站连接 AE 网络以取得材料和 AE 能量；科研页面使用独立的 LDLib2 UI。外部 Mod 应通过研究 API 判断解锁状态，并在自身功能入口执行权限或能力限制。

主方块与左侧部件都拥有 AE 网络 BE，显示名称均为“数位化改造研究站”；两部分的背面与底面都可接线，相邻内侧通过原生 AE 节点自动连接。左侧部件只作为网络入口，主 BE 承担槽位、研究和一条频道需求，部件不增加闲置耗能或独立科研进度。旧存档中已有部件在区块加载后补建 BE，原研究 UUID 和已付款项目不变。

主体 `on` 模型仅按网络在线状态使用，在线待机时也保持 `on`；断电或缺少可用频道时使用 `off`。独立激光头的工作模型、摆动和半透明光束仍只在研究项目实际推进时启用，研究暂停不会把在线主体切回 `off`。

## Java 注册

在下游 Mod 的构造器中将监听器加入该 Mod 的 `IEventBus`。监听 `RegisterResearchNodesEvent`，不要在任意运行时 Tick 中调用注册。事件发生在 Minecraft 注册表可用之后；同一 ID 只能注册一次，开始加载研究 Datapack 后注册入口关闭。

下面的类可以由下游 Mod 构造器通过 `new ExampleResearchRegistration(modBus)` 安装。ID、翻译键和费用均为文档示例。

```java
package example.research;

import com.fish_dan_.data_energistics.api.research.RegisterResearchNodesEvent;
import com.fish_dan_.data_energistics.api.research.ResearchInput;
import com.fish_dan_.data_energistics.api.research.ResearchNode;
import com.fish_dan_.data_energistics.api.research.ResearchScope;
import com.fish_dan_.data_energistics.api.research.ResearchTarget;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.bus.api.IEventBus;

public final class ExampleResearchRegistration {

    public ExampleResearchRegistration(IEventBus modBus) {
        modBus.addListener(ExampleResearchRegistration::register);
    }

    private static void register(RegisterResearchNodesEvent event) {
        ResourceLocation technology = ResourceLocation.fromNamespaceAndPath("example_mod", "precision_tools");
        event.register(ResearchNode.builder(
                technology,
                ResearchScope.TECHNOLOGY,
                "research.example_mod.precision_tools",
                "research.example_mod.precision_tools.description",
                1_200,
                32)
                .icon(ResourceLocation.withDefaultNamespace("iron_ingot"))
                .input(new ResearchInput(AEItemKey.of(Items.IRON_INGOT), 16))
                .input(new ResearchInput(AEFluidKey.of(Fluids.WATER), 1_000))
                .build());

        event.register(ResearchNode.builder(
                ResourceLocation.fromNamespaceAndPath("example_mod", "reinforced_pickaxe"),
                ResearchScope.ITEM_UPGRADE,
                "research.example_mod.reinforced_pickaxe",
                "research.example_mod.reinforced_pickaxe.description",
                2_400,
                64)
                .icon(ResourceLocation.withDefaultNamespace("diamond_pickaxe"))
                .target(ResearchTarget.item(ResourceLocation.withDefaultNamespace("diamond_pickaxe")))
                .requiredPrerequisite(technology)
                .input(new ResearchInput(AEItemKey.of(Items.DIAMOND), 4))
                .build());
    }
}
```

`name` 与 `description` 必须是翻译键，由提供节点的 Mod 提供对应语言资源。`icon` 是物品 ResourceLocation。`researchTicks` 必须为正整数，`aePerTick` 必须是有限、非负数。输入数量必须为正 long；相同 AEKey 的累计费用超过 long 上限时，节点构造直接失败。物品升级必须提供至少一个目标物品 ID 或目标 item tag。

玩家强化使用相同 Builder，作用域选择 `ResearchScope.PLAYER_UPGRADE`，无需设置目标物品。`requiredCondition` 接受 `AdvancementResearchCondition` 或 `FtbQuestTaskCondition`，`optionalConditions` 添加至少满足一项的条件组；`mutuallyExclusive` 指定另一条路线，另一节点也必须声明对应的反向关系。

## 三种结果的存储

| Scope | 结果所在位置 | 查询方式 |
| --- | --- | --- |
| `TECHNOLOGY` | Overworld 的 `data_energistics_research` SavedData，以 UUID 主体保存节点 ID | `ResearchAccess.isUnlocked(...)` |
| `ITEM_UPGRADE` | 目标 ItemStack 的 `data_energistics:research_upgrades` DataComponent，保存升级节点 ID | `ResearchAccess.hasUpgrade(stack, id)` |
| `PLAYER_UPGRADE` | 同一世界数据中的独立玩家 UUID 账本，保存身体强化节点 ID | `ResearchAccess.hasPlayerUpgrade(player, id)` 或 `ResearchAccess.hasPlayerUpgrade(server, playerUuid, id)` |

科技项目在启动时固定主体快照：发起玩家、其当前共享队伍、科研站。共享队伍包括自定义研究队伍、vanilla scoreboard 队伍，以及已安装 FTB Teams 时的真实 party 成员关系。邀请和盟友关系不提供共享主体，FTB 个人队伍不会被重复当作共享队伍。

查询玩家科技时使用当前队伍关系的并集。退出队伍后，不再读取该队伍以后获得的科技；玩家本人已经保存的科技不会被删除。物品升级随物品保存，与持有者及其队伍无关。

玩家强化无需目标物品，完成时只写入项目发起者 UUID，不写入队伍或研究站主体。可用前置来自当前适用的主科技主体账本、当前玩家的强化账本与目标物品的升级组件，因此物品升级或身体强化可以引用主科技前置；三类完成结果仍分别保存。Java 使用 `requiredPrerequisite` 添加必需前置，使用 `optionalPrerequisites` 添加至少解锁一项的可选组。

科研站 UUID 存于方块实体 NBT，拆卸物品使用 `data_energistics:research_station_id` DataComponent 携带该 UUID。复制同一 UUID 的科研站共用一个世界内的目标物品、项目和退款账本，以及每个 server tick 的执行预算；它们不会生成独立材料副本或额外科研速度。

## Datapack 覆盖

路径为 `data/<namespace>/researchtechnology/<path>.json`，对应节点 ID `<namespace>:<path>`。专用目录避免读取其他 Mod 的 `research/` 数据格式，并允许下游 Java 注册节点使用自己的 namespace。节点必须已由 Java 注册。字段省略时继承 Java 默认值；提供 `inputs` 时替换全部材料费用，提供 `prerequisites` 时替换全部前置关系。`target` 对象提供后，内部省略的 `items` 或 `tags` 视为空集。

当前读取的字段如下：

| 字段 | 类型与约束 |
| --- | --- |
| `id` | 可选 ResourceLocation 字符串；必须等于文件路径对应 ID |
| `description` | 可选翻译键字符串 |
| `target` | 可选对象，包含 `items`、`tags` 两个可选 ResourceLocation 字符串数组 |
| `inputs` | 可选对象，包含 `items`、`fluids`、`resources` 三个可选“ID → 用量”映射；空对象表示无材料费用 |
| `prerequisites` | 可选对象；`required` 是全部必须解锁的 ID 数组，`optional` 是多个“任选其一”数组组成的数组；用空对象清空全部前置，可选组本身不得为空 |
| `conditions.required` | 可选条件对象数组，全部必须满足；提供后替换 Java 默认必需条件，空数组清空 |
| `conditions.optional` | 可选的条件数组分组；每个组至少满足一项，组本身不得为空；提供后替换 Java 默认可选组，空数组清空 |
| `mutually_exclusive` | 可选节点 ID 数组；目标必须存在、与本节点同作用域并双向声明；提供后替换 Java 默认互斥关系，空数组清空 |
| `research_ticks` | 可选整数，范围 1 至 2,147,483,647，单位 server ticks |
| `ae_per_tick` | 可选有限非负 JSON number，单位 AE/t |

`scope`、`name`、`icon` 固定取 Java 注册值，不能通过这些 JSON 覆盖入口修改。目标物品和普通 item/fluid 材料必须存在于 Minecraft 注册表。前置节点缺失或形成环时，该节点及依赖该非法链的节点不会发布；解析失败的覆盖文件也不会退回 Java 默认值。

条件使用 `{"type":"advancement","id":"minecraft:story/mine_diamond"}` 或 `{"type":"ftbquests_task","task":12345}`。FTB Quests 条件还可附加 `"quest":"example_mod:chapter/task_label"`，该 ResourceLocation 仅用于配置识别和诊断，实际查询始终使用数值 `task`。`conditions.required` 与 `conditions.optional` 独立覆盖，省略其中一项会保留对应 Java 默认值。非法 ID、重复项、空可选组、非双向互斥或跨作用域互斥会拒绝相关定义，并在日志记录文件与字段。

例如 `data/example_mod/researchtechnology/reinforced_pickaxe.json`：

```json
{
  "id": "example_mod:reinforced_pickaxe",
  "description": "research.example_mod.reinforced_pickaxe.description",
  "target": {
    "items": ["minecraft:diamond_pickaxe"],
    "tags": []
  },
  "inputs": {
    "items": {
      "minecraft:diamond": 8,
      "minecraft:iron_ingot": 16,
      "minecraft:nether_star": 7
    },
    "fluids": {
      "minecraft:water": 2000
    },
    "resources": {
      "data_energistics:manifest_binary": 67
    }
  },
  "prerequisites": {
    "required": ["example_mod:precision_tools"],
    "optional": [
      ["example_mod:advanced_tools", "example_mod:quantum_tools"]
    ]
  },
  "research_ticks": 3600,
  "ae_per_tick": 128
}
```

映射 key 已经提供 ID，材料类型由分组确定，普通条目只填用量。`items` 的 key 是物品 ID，`fluids` 的 key 是流体 ID，`resources` 的 key 是已注册的 AEKeyType ID。类型 codec 不需要额外字段时，资源也可以直接填数字，例如 `manifest_binary`。用量必须是 1 至 9,223,372,036,854,775,807 的整数；相同 AEKey 合计也不能超过此上限。物品单位是件数，流体单位是 mB，第三方 AEKey 使用其类型定义的原生数量单位。

需要精确匹配组件或提供 AEKey 的附加数据时，将数字换成对象。对象内 `amount` 可省略，默认为 1；物品和流体只支持额外的 `components`，使用 Minecraft DataComponentPatch codec。自定义资源对象的其他字段直接传给该类型的 codec，不能重复写 `#t` 或 `key_type`。同一 ID 有多个组件变体，或同一 AEKeyType 对应多种资源时，用数组列出各个对象：

```json
{
  "inputs": {
    "items": {
      "minecraft:diamond_pickaxe": {
        "amount": 2,
        "components": {"minecraft:damage": 5}
      }
    },
    "resources": {
      "data_energistics:digitalization": [
        {"resource": "data_energistics:data_flow", "amount": 100},
        {"resource": "data_energistics:stellar_flux", "amount": 250}
      ]
    }
  }
}
```

材料消耗使用上述映射结构；空费用使用 `"inputs": {}`。

需要生成自定义资源对象的附加字段时，可在拥有当前服务器注册表 Provider 的环境中使用实际 codec，将生成数据移除 `#t` 后放在 `resources` 对应类型 ID 下，并附加 `amount`：

```java
import appeng.api.stacks.AEKey;

import net.minecraft.core.HolderLookup;
import net.minecraft.resources.RegistryOps;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;

public static JsonElement researchKeyJson(HolderLookup.Provider registries, AEKey key) {
    RegistryOps<JsonElement> operations = registries.createSerializationContext(JsonOps.INSTANCE);
    JsonObject data = AEKey.CODEC.encodeStart(operations, key).getOrThrow().getAsJsonObject();
    data.remove("#t");
    return data;
}
```

节点 ID 是存档与外部集成的稳定契约。已付款项目保存启动时的完整节点规则；修改 Datapack 费用和时长会影响以后启动的项目。当前定义中不可用的节点停止推进，已付款项目、进度和目标物品仍保留。

## 运行、取消和退款

每个 station UUID 最多同时运行两个项目。项目按启动次序取得每 tick 能量；能量不足的项目暂停这一 tick，后续项目继续按顺序尝试取得能量。已经取得但不足一 tick 的能量记入项目余额。

启动时先模拟材料是否齐全，再提取实际材料并记录付款 journal。部分提取失败时尝试返还已提取材料；AE 网络暂时不能接收的余额继续保存，网络可用后再次返还。退款余额未清空时禁止新启动，避免覆盖待退账本。

节点内的“请求材料”会按材料总费用扣除网络已有库存及正在运行的合成任务最终产出，只向 AE2 请求剩余缺口。点击“开始研究”发现材料不足且没有退款余额时，也会触发同一请求流程。相同 station UUID、网络与 AEKey 的未完成规划会去重；规划结束后重新核对库存和正在合成的最终产出，再提交合成 CPU。无法合成的目标材料及规划发现的缺失原料会通过客户端的物品查看器收藏接口加入收藏栏，不另行打开合成窗口。提交失败或规划错误会提供具体提示。

材料请求不提取研究费用，也不占用两个科研名额；成功提交的 CPU 任务由 AE2 管理，产出进入网络，材料到达后再点击开始研究。未提交的规划在服务器 Tick 中轮询，与研究站区块是否加载无关；请求玩家离线、原研究站卸载或网络改变时取消规划，服务器停止时清理剩余规划。已提交的 CPU 任务与已付款研究进度分别由 AE2 和科研存档保存。

`cancel` 暂停科研并保留进度、已付款规则及能量余额，不退款。`resume` 继续已有项目，不再次收费。物品升级项目暂停时目标物品依然锁定，防止将已经支付的进度转移到另一物品。科技项目不要求目标槽位中放入物品。

原版成就与 FTB Quests 任务均按发起者判断。研究期间每 Tick 检查项目快照中的条件；失效时自动暂停，保留材料、进度和能量余额，恢复条件后需要从研究站手动继续。发起者离线时沿用最后一次保存的条件状态，重新上线后恢复实时检查。FTB Quests 是可选依赖；未安装、任务不存在或成就不存在时条件未满足，日志提供具体诊断，客户端只接收服务端结果。

互斥项目不能在相同适用主体上同时运行，暂停项目不占运行名额。完成时才写入互斥锁和胜出节点：主科技写入启动时的主体快照，物品升级写入目标物品的 `research_exclusions` 组件，玩家强化只写入发起者 UUID。胜出时删除同主体的冲突项目，清除其进度，不退材料；不存在其他物品升级项目时解除目标槽锁定。条件和互斥规则与材料、时长一起保存在项目快照中，Datapack 重载不改写已有项目。

损坏的站日志保留原始 NBT 并隔离，该 UUID 的科研站停止访问该日志。没有可识别 UUID 的坏条目、损坏的身份桥或解锁账本条目保存至 `quarantined_entries`，保留来源与原始 Tag；其他有效记录继续加载。日志包含异常上下文，隔离不会自动丢弃或伪造科研结果。

## 开发环境即时研究

命令通过 `DataEnergisticsCommands` 统一注册，仅开发环境存在，需要权限等级 2 并由玩家执行：

```text
/data_energistics research instant technology
/data_energistics research instant item
/data_energistics research instant player
```

这些命令持久化启用主体的免材料、免 AE、瞬时研究权限，不直接写入节点解锁。`technology` 作用于当前玩家和当前有效研究队伍，不写研究站主体；`item` 作用于主手物品的 `research_instant` 组件；`player` 只作用于当前玩家 UUID。权限启用后仍需从研究站发起具体节点，前置、外部条件与互斥规则仍必须满足。暂停项目也可在研究站继续时即时完成，已付款材料不退还；新节点不扣材料和能量，也不占并行项目名额。生产环境不注册命令，已保存的主体权限仍按存档生效。

## 外部 API 查询

世界科研访问必须在 server thread 上执行；读取 ItemStack 升级 ID 可以在客户端执行。注册 ID 本身不会替下游 Mod 自动禁用配方或机器，需要集成方在实际功能入口调用查询。

```java
import com.fish_dan_.data_energistics.api.research.ResearchAccess;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

public static boolean playerCanUsePrecisionTools(ServerPlayer player, UUID stationId) {
    ResourceLocation id = ResourceLocation.fromNamespaceAndPath("example_mod", "precision_tools");
    return ResearchAccess.isUnlocked(player, stationId, id);
}

public static boolean stationCanUsePrecisionTools(MinecraftServer server, UUID stationId) {
    ResourceLocation id = ResourceLocation.fromNamespaceAndPath("example_mod", "precision_tools");
    return ResearchAccess.isUnlocked(server, stationId, id);
}

public static boolean hasReinforcedPickaxeUpgrade(ItemStack stack) {
    ResourceLocation id = ResourceLocation.fromNamespaceAndPath("example_mod", "reinforced_pickaxe");
    return ResearchAccess.hasUpgrade(stack, id);
}
```

玩家查询的 station 参数可为 `null`，此时只读取玩家与其当前队伍主体。没有在线玩家的机器使用已知主体 UUID 查询，不会临时获得任意玩家的队伍权限。

## 队伍命令与界面

队伍命令由项目统一的 `DataEnergisticsCommands` 注册，入口为 `/data_energistics research team`。支持以下子命令：

```text
create <队伍名称>
invite <在线玩家名称或 UUID>
accept <邀请中的队伍 UUID>
reject <邀请中的队伍 UUID>
leave
kick <队员 UUID>
transfer <新队长 UUID>
disband
```

只有玩家可执行这些命令；邀请、移出成员、转让队长与解散由队长操作。队长退出前需转让身份，或直接解散队伍。邀请目标下线时邀请失效，邀请者下线不会撤销邀请。

研究站的队伍页提供相同的操作入口。整个研究界面铺满当前 GUI viewport，随窗口尺寸和 GUI 缩放重新布局，操作按钮集中在右侧。作用域按钮循环切换主科技、物品升级和玩家强化，页面按钮切换图谱与研究项目。鼠标悬停节点时展开材料、耗能与时长，左键点击固定展开描述、前置、条件、互斥关系、进度和研究操作；移开鼠标、切换页面或重开界面均保留选中，只有再次点击才回到悬停消耗视图。图谱通过左键拖动平移，空白、节点、展开内容和内部按钮上均可按住拖动；长按或已经拖动后松开不会触发节点切换或研究按钮，研究按钮只在短点击释放时执行。滚轮在空白画布、节点及展开内容上均可缩放，保留复位、筛选与关联高亮；界面不显示拖动与缩放的文字提示。宽屏可以同时显示研究项目列表，窄屏在图谱与项目页之间切换。

节点本体只显示标题、物品图标和状态，所有状态都不创建端口编辑区；前置连线使用标题边缘作为端点。悬停时消耗在右侧展开，宽度按当前语言与字体的实际内容计算，短内容收窄，较长内容增宽并在合理上限处换行；节点卡片与消耗区域上下等高对齐。点击后描述、前置、进度和研究按钮在上排下方向下展开，与上排同宽，文本换行和实际控件共同决定详情高度，同列节点按实际宽高排布。详情按项目状态显示“开始研究”“继续研究”或“取消”按钮；条件未满足时禁用研究按钮，已解锁节点显示已解锁状态。较长内容可通过图谱平移与缩放查看，不保留固定大小的空白详情区域。

升级目标槽位于界面顶部；“放入待升级物品”、可取放与锁定状态通过槽位 Tooltip 显示，不占用常驻文字区域。物品升级图谱以槽内物品的图标和名称作为第一个节点，再连接对应升级路线；没有可用升级时也保留物品节点，空槽时通过槽位 Tooltip 引导放入物品。

玩家强化图谱以玩家作为首节点，不要求物品。详情与项目列表展示条件类型、标识、必需或可选分组及满足状态。按钮使用服务端同步的发起者条件、主体互斥锁、冲突项目和操作权限；被锁路线显示红色阻断与胜出来源，图谱绘制互斥连线并加粗胜出节点的描边。

背包按钮显示或隐藏原生玩家槽位浮窗，拖动标题栏移动面板；目标槽与玩家背包仍由同一个独立 Minecraft Menu 管理。三位一体和研究站通过 `data_energistics:lss/shared_backpack.lss` 复用完整背包拼图与绿色悬停高亮，浮窗使用三位一体的原始背景。研究站背包保持固定的 261×156 GUI 像素面板、27×27 槽位和 12px 标题字号；窗口或全屏图谱尺寸变化只调整浮窗位置，保留拖动位置并限制在窗口边界内，物品绘制与命中区保持相同尺寸。Minecraft 自身的 GUI 缩放仍按原生行为作用于整个界面。

三个激光头复用同一份独立模型几何，运行贴图通过父模型覆盖。头部按素材挂点分别定位和旋转，待机时保持原始姿态；只在项目获得完整 Tick 能量并推进时，各自以不同相位摆动并发射半透明 `#a8ffe0` 激光。光束共用对应头部的变换，随摆动方向持续落在下方工作台表面范围内；待机、取消、断网和供能暂停时不进行摆动或发射。主体模型移除全部三处静态头部，渲染器的每层矩阵作用域均在 `finally` 中恢复。
