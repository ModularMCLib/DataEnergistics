---
navigation:
  parent: data_energistics:items-blocks-machines/0_data_energistics.md
  title: 升级
  icon: data_energistics:card_saber_energy
  position: 4
item_ids:
- data_energistics:card_saber_energy
- data_energistics:redstone_tuning_card
- data_energistics:multiplication_card
---

# 升级

## 聚能卡
<Row>
    <ItemLink id="data_energistics:card_saber_energy"/>  
    <ItemImage id="data_energistics:card_saber_energy" />
    <RecipeFor id="data_energistics:card_saber_energy" />
</Row>
聚能卡:  
<ItemImage id="data_energistics:star_shard" /> : 最终伤害 = 基础伤害 × (聚能卡数量 × 2) × 当前速度(3.15) [暴击再 × 1.5]  当<ItemImage id="data_energistics:data_light_saber" components="ae2:stored_energy=20000.0d" />为弹药时增加5%最大百分比真实伤害
<ItemImage id="data_energistics:data_light_saber" /> : 最大伤害 = 基础伤害 × (聚能卡数量 × 2) 增加40ae额外耗能（总耗电 50ae）, <ItemImage id="data_energistics:data_light_saber" components="ae2:stored_energy=20000.0d" />左键攻击可以发射出光刃,<ItemImage id="data_energistics:data_sanctifier" components="ae2:stored_energy=20000.0d" />的体型会比原本大一倍  
<ItemImage id="data_energistics:data_crystal_cutting_knife" /> : 扩大传送范围  
以下工具还会增加一个数据流存储槽  
<ItemImage id="data_energistics:data_crystal_sword" /> : 最大伤害 = 基础伤害 × (聚能卡数量 × 2) 增加40ae额外耗能（总耗电 50ae），攻击时消耗20数据流剥夺实体20Tick AI  
<ItemImage id="data_energistics:data_crystal_axe" /> : 消耗20数据能连锁一整棵树，增加40ae额外耗能（总耗电 50ae）   
<ItemImage id="data_energistics:data_crystal_pickaxe" /> : 消耗20数据连锁周边矿石，并将其复制一份，增加40ae额外耗能（总耗电 50ae）   
<ItemImage id="data_energistics:data_crystal_hoe" /> : 消耗20数据耕地永不失水，增加40ae额外耗能（总耗电 50ae）   
<ItemImage id="data_energistics:data_crystal_shovel" /> : 可以潜行右键调整3×3或5×5的破坏范围，增加40ae额外耗能（总耗电 50ae），破坏时消耗20数据  


## 红石调整卡

<Row>
    <ItemLink id="data_energistics:redstone_tuning_card" />
    <ItemImage id="data_energistics:redstone_tuning_card"  />
    <RecipeFor id="data_energistics:redstone_tuning_card" />
</Row>

红石调整卡用于给样板供应器和物质聚合弩增加红石联动能力，每台设备最多安装 1 张。

### 样板供应器

<Row>
    <ItemImage id="ae2:pattern_provider" />
    <ItemImage id="data_energistics:adaptive_pattern_provider" />
    <ItemImage id="data_energistics:adaptive_pattern_provider_part" />
</Row>

支持 AE2 样板供应器、自适应样板供应器及已兼容的其他样板供应器。安装后可在界面中切换两种模式：

- 发出模式：供应器分发合成内容时，对外发出一次短暂红石脉冲。
- 接收模式：供应器接收到红石脉冲后，对内部每个当前可合成的样板各请求一次主产物。

### 物质聚合弩

<Row>
    <ItemImage id="data_energistics:star_shard" />
</Row>

安装后，普通弹药的单发耗能变为基础耗能的 5 倍，同时弹射物会追踪距离最近的非玩家活体。

## 倍增卡

<ItemImage id="data_energistics:multiplication_card" scale="4" />

<Recipe id="data_energistics:data_reassembler/multiplication_card" />

使用数据重组器制作。进制收容球体必须装满二进制（2,040），256M 数位化磁盘必须只装数据流并达到满字节（2,147,483,584）。收容球体的电量不影响配方匹配。

每次还需输入末影和数蚀液各 **16,384 B**（各 16,384,000 mB），以及 **1,365 二进制**。这些二进制从机器的特殊资源输入槽提供。

倍增卡提高兼容机器的产出，每台最多安装 **1 张**。它作用于新生成的产物，不会反复倍增输出槽中已有的物品，也不会通过延长加工时间来增产。

| 设备来源 | 产出规则 |
| --- | --- |
| 数据能源学 | 原产出 × 2 |
| 其他兼容 Mod | 原产出 × 1.5，物品数量向上取整：1 → 2、3 → 5、5 → 8 |
| 发电设备 | 按对应来源倍率提高发电量；能量保留小数，不套用物品的取整规则 |

### 支持设备

- 数据能源学：数据充压一体机、[数据重组器](6.3_data_reassembler.md)、数据异步处理工厂、数据拟生场，以及本模组两种太阳能板及其贴片。
- AE2：压印器、谐振仓。
- ExtendedAE（EAE）：电路切片机、水晶装配器、扩展压印器。
- EAEP：超级电路切片机、超级水晶装配器。
- AdvancedAE（AAE）：反应仓。
- 水晶科技：晶能谐振仓、熵变反应室、晶能聚合器、晶能粉碎机、电路蚀刻器、晶体催生仓。
- ECO：集成工作站。

第三方设备需要安装对应 Mod。加工前应留出增产后的输出空间；原料、能源和机器结构仍需满足原生条件。
