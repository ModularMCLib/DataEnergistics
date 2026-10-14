# 数字化转型研究站

科研站提供科技解锁与物品升级两种科研。节点先由 Java 注册，Datapack 再覆盖允许修改的规则。默认不注册示例科技；本文件中的示例用于下游集成，不会自动加入游戏。

科研站连接 AE 网络以取得材料和 AE 能量；科研页面使用独立的 LDLib2 UI。外部 Mod 应通过研究 API 判断解锁状态，并在自身功能入口执行权限或能力限制。

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
                .prerequisite(technology)
                .input(new ResearchInput(AEItemKey.of(Items.DIAMOND), 4))
                .build());
    }
}
```

`name` 与 `description` 必须是翻译键，由提供节点的 Mod 提供对应语言资源。`icon` 是物品 ResourceLocation。`researchTicks` 必须为正整数，`aePerTick` 必须是有限、非负数。输入数量必须为正 long；相同 AEKey 的累计费用超过 long 上限时，节点构造直接失败。物品升级必须提供至少一个目标物品 ID 或目标 item tag。

## 两种结果的存储

| Scope | 结果所在位置 | 查询方式 |
| --- | --- | --- |
| `TECHNOLOGY` | Overworld 的 `data_energistics_research` SavedData，以 UUID 主体保存节点 ID | `ResearchAccess.isUnlocked(...)` |
| `ITEM_UPGRADE` | 目标 ItemStack 的 `data_energistics:research_upgrades` DataComponent，保存升级节点 ID | `ResearchAccess.hasUpgrade(stack, id)` |

科技项目在启动时固定主体快照：发起玩家、其当前共享队伍、科研站。共享队伍包括自定义研究队伍、vanilla scoreboard 队伍，以及已安装 FTB Teams 时的真实 party 成员关系。邀请和盟友关系不提供共享主体，FTB 个人队伍不会被重复当作共享队伍。

查询玩家科技时使用当前队伍关系的并集。退出队伍后，不再读取该队伍以后获得的科技；玩家本人已经保存的科技不会被删除。物品升级随物品保存，与持有者及其队伍无关。

科研站 UUID 存于方块实体 NBT，拆卸物品使用 `data_energistics:research_station_id` DataComponent 携带该 UUID。复制同一 UUID 的科研站共用一个世界内的目标物品、项目和退款账本，以及每个 server tick 的执行预算；它们不会生成独立材料副本或额外科研速度。

## Datapack 覆盖

路径为 `data/<namespace>/researchtechnology/<path>.json`，对应节点 ID `<namespace>:<path>`。专用目录避免读取 Astral Sorcery 等 Mod 自有的 `research/` 数据格式，同时允许其他 Mod 的 Java 注册节点使用自己的 namespace。之前使用 `data/<namespace>/research/` 的研究站覆盖文件需移动到新目录；节点 ID 与世界解锁存档无需修改。节点必须已由 Java 注册。字段省略时继承 Java 默认值；提供数组时替换整个数组。`target` 对象提供后，内部省略的 `items` 或 `tags` 视为空集。

当前读取的字段如下：

| 字段 | 类型与约束 |
| --- | --- |
| `id` | 可选 ResourceLocation 字符串；必须等于文件路径对应 ID |
| `description` | 可选翻译键字符串 |
| `target` | 可选对象，包含 `items`、`tags` 两个可选 ResourceLocation 字符串数组 |
| `inputs` | 可选材料对象数组；空数组表示无材料费用 |
| `prerequisites` | 可选已注册研究 ID 字符串数组；空数组清除前置节点 |
| `research_ticks` | 可选整数，范围 1 至 2,147,483,647，单位 server ticks |
| `ae_per_tick` | 可选有限非负 JSON number，单位 AE/t |

`scope`、`name`、`icon` 固定取 Java 注册值，不能通过这些 JSON 覆盖入口修改。目标物品和普通 item/fluid 材料必须存在于 Minecraft 注册表。前置节点缺失或形成环时，该节点及依赖该非法链的节点不会发布；解析失败的覆盖文件也不会退回 Java 默认值。

例如 `data/example_mod/researchtechnology/reinforced_pickaxe.json`：

```json
{
  "id": "example_mod:reinforced_pickaxe",
  "description": "research.example_mod.reinforced_pickaxe.description",
  "target": {
    "items": ["minecraft:diamond_pickaxe"],
    "tags": []
  },
  "inputs": [
    {"type": "item", "id": "minecraft:diamond", "amount": 8},
    {"type": "fluid", "id": "minecraft:water", "amount": 2000},
    {
      "type": "ae_key",
      "key": {"#t": "ae2:i", "id": "minecraft:iron_ingot"},
      "amount": 16
    }
  ],
  "prerequisites": ["example_mod:precision_tools"],
  "research_ticks": 3600,
  "ae_per_tick": 128
}
```

每个材料对象的 `amount` 必须是 1 至 9,223,372,036,854,775,807 的整数；相同 AEKey 合计也不能超过此上限。物品单位是件数，流体单位是 mB，第三方 AEKey 使用其类型定义的原生数量单位。

`type` 的三种形式：

- `item`：使用 `id` 物品 ID 与 `amount`。
- `fluid`：使用 `id` 流体 ID 与 `amount`。
- `ae_key`：使用 `key` 序列化对象与 `amount`；用于 DataComponent 精确匹配及第三方已注册 AEKey 类型。

当前 AE2 19.2.18 的通用 AEKey codec 使用 `#t` 字段分派已注册的 key type。物品类型为 `ae2:i`，流体类型为 `ae2:f`。这两种类型均使用 `id`，并可附带符合 Minecraft DataComponentPatch codec 的 `components` 对象；数量由外层 `amount` 表达，不写在 `key` 中。第三方类型的 `#t` 值和剩余字段必须来自该类型实际注册的 codec，不可仅凭资源 ID 推测。

需要生成复杂 key JSON 时，可在拥有当前服务器注册表 Provider 的环境中使用实际 codec：

```java
import appeng.api.stacks.AEKey;

import net.minecraft.core.HolderLookup;
import net.minecraft.resources.RegistryOps;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;

public static JsonElement researchKeyJson(HolderLookup.Provider registries, AEKey key) {
    RegistryOps<JsonElement> operations = registries.createSerializationContext(JsonOps.INSTANCE);
    return AEKey.CODEC.encodeStart(operations, key).getOrThrow();
}
```

节点 ID 是存档与外部集成的稳定契约。已付款项目保存启动时的完整节点规则；修改 Datapack 费用和时长会影响以后启动的项目。当前定义中不可用的节点停止推进，已付款项目、进度和目标物品仍保留。

## 运行、取消和退款

每个 station UUID 最多同时运行两个项目。项目按启动次序取得每 tick 能量；能量不足的项目暂停这一 tick，后续项目继续按顺序尝试取得能量。已经取得但不足一 tick 的能量记入项目余额。

启动时先模拟材料是否齐全，再提取实际材料并记录付款 journal。部分提取失败时尝试返还已提取材料；AE 网络暂时不能接收的余额继续保存，网络可用后再次返还。退款余额未清空时禁止新启动，避免覆盖待退账本。

`cancel` 暂停科研并保留进度、已付款规则及能量余额，不退款。`resume` 继续已有项目，不再次收费。物品升级项目暂停时目标物品依然锁定，防止将已经支付的进度转移到另一物品。科技项目不要求目标槽位中放入物品。

损坏的站日志保留原始 NBT 并隔离，该 UUID 的科研站停止访问该日志。没有可识别 UUID 的坏条目、损坏的身份桥或解锁账本条目保存至 `quarantined_entries`，保留来源与原始 Tag；其他有效记录继续加载。日志包含异常上下文，隔离不会自动丢弃或伪造科研结果。

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

研究站的队伍页提供相同的操作入口。背包按钮显示或隐藏原生玩家槽位浮窗，拖动标题栏移动面板；目标槽与玩家背包仍由同一个独立 Minecraft Menu 管理。节点点击只选中并显示详情，研究通过独立按钮启动；画布支持平移、缩放、复位与筛选。

三个激光头复用同一份独立模型几何，运行贴图通过父模型覆盖。头部按素材挂点分别定位和旋转，待机时保持原始姿态；只在项目获得完整 Tick 能量并推进时，各自以不同相位摆动并发射半透明 `#a8ffe0` 激光。光束共用对应头部的变换，随摆动方向持续落在下方工作台表面范围内；待机、取消、断网和供能暂停时不进行摆动或发射。主体模型移除全部三处静态头部，渲染器的每层矩阵作用域均在 `finally` 中恢复。
