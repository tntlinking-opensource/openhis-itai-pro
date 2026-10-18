# DM-CUS-002 ChargeItemAutoGen 收费明细自动生成规则

> **限界上下文**：本地扩展 · 计费规则管理\
> **聚合类型**：实体聚合（单表配置型）\
> **版本**：v1.0.3 · **日期**：2026-06-19

## 1. 概述

### 1.1 业务定义

收费明细自动生成规则（ChargeItemAutoGen）定义了**诊疗项目与收费明细之间的自动映射关系**。它回答的核心业务问题是："**当某个诊疗项目被开立或执行时，应该自动生成哪些收费明细？**"——在医嘱执行、诊疗完成、**住院每日滚费**等业务节点上，系统据此自动创建对应的 ChargeItem，减少手工计费操作，保障计费准确性与及时性。

收费明细自动生成规则是**诊疗项目定义（ActivityDefinition）与收费项定价（ChargeItemDefinition）之间的桥梁**，属于配置型数据：全局规则由运维在管理端维护；**住院滚费**场景下按 **`encounter_id`（住院就诊）** 维度维护「**自动计费**」配置（如床位服务费），由护士在划价页 **「自动计费」Tab** 维护，定时任务按配置批量生成与手动划价同链路的 `ChargeItem`。本文档编码为 `DM-CUS-002`，无 FHIR 标准资源映射；对外交换时仅计费结果体现在 `ChargeItem` 中，规则本身不对外暴露。与 ActivityDefinition / ChargeItemDefinition / ChargeItem 的关系：**ActivityDefinition** 为触发来源，**ChargeItemAutoGen** 为映射规则，**ChargeItemDefinition** 为目标定价锚点，**ChargeItem** 为规则驱动生成的事实。

### 1.2 核心职责

| 职责 | 说明 |
|---|---|
| 自动计费映射 | 声明某触发来源（如诊疗项目定义）在达成触发条件时应自动生成哪些收费明细 |
| 多规则编排 | 同一触发来源可配置多条规则（N:M 映射），按优先级有序匹配 |
| 科室维度限定 | 支持按执行科室缩小匹配范围，实现同项目不同科室收费差异化 |
| 数量预设 | 每条规则预设默认生成数量，供应用编排使用 |
| 规则启停管理 | 通过状态启停控制规则的生效与失效，不影响历史数据 |
| 住院就诊维度配置 | 按 `encounter_id` 绑定单次住院的自动计费项（床位服务类诊疗 + 数量） |

### 1.3 适用范围

| 类型 | 说明 |
|---|---|
| 医嘱执行自动计费 | 医嘱执行时系统匹配自动生成规则，按规则创建 ChargeItem 草稿 |
| 诊疗完成自动计费 | 诊疗项目完成后按规则自动生成对应收费明细 |
| **住院滚费（自动计费）** | 护士在划价页「自动计费」Tab 按 encounter 配置床位服务类诊疗及数量；定时任务读取配置并生成前一日费用（护理级别由医嘱驱动，不在此表配置） |
| 差异化收费配置 | 不同科室执行同一诊疗项目时自动关联不同的收费项定价 |
| 规则维护管理 | 运维人员在管理端新增、修改、启停、删除自动生成规则 |
| 定价变更影响分析 | 通过反查规则评估某项 ChargeItemDefinition 变更对自动计费的影响 |

### 1.4 与关键上下游及交换边界的关系

规则行的上游为**住院就诊（Encounter，住院滚费场景必填 `encounter_id`）**、触发来源主数据（如 ActivityDefinition，以 `trigger_source_table` + `trigger_source_id` 表达）、收费项定价（ChargeItemDefinition，`charge_definition_id`）及可选的组织科室（Organization）；下游为应用编排依据匹配结果创建的收费明细（ChargeItem）。本聚合不解析临床医嘱语义，不参与计费金额试算。

| 维度 | 上游 | 本聚合 | 下游 |
|---|---|---|---|
| 职责 | 主档发布与定价事实 | 触发→目标定价映射与优先级 | ChargeItem 事实生成 |
| 引用 | 表名 + 主键、定价 ID | 规则行 ID | 应用层批量创建计费明细 |

---

## 2. 聚合根与持久化形态

### 2.1 持久化形态

**持久化形态**：单表。

聚合根对应表 `cus_charge_item_auto_gen`，主键 `id`；一行表示一条「**住院就诊** + 触发来源 → 收费项定价」映射规则（住院滚费场景下 `encounter_id` 必填）。

**高频查询模式（建议索引口径）**：按 `encounter_id`（住院滚费主路径）、`(trigger_source_table, trigger_source_id)` 与 `charge_definition_id` 过滤；执行路径匹配按租户 + 未软删 + `status_enum` 有效态；排序按 `priority`、`id` 升序。建议索引：`encounter_id`（`WHERE is_deleted = false`）。

### 2.2 聚合根标识

| 属性 | 字段名 | 类型 | 说明 |
|---|---|---|---|
| 规则主键 | `id` | `Long` | 表 `cus_charge_item_auto_gen.id`，雪花 ID；**持久化时自动生成**，创建命令不入参 |

> **命名约定**：Update / Delete / Activate / Suspend 等 Command 中的 Java 字段 **`ruleId`** 仅表示「待操作行的主键」，落库时 `repository.findById(command.getRuleId())`，**无** `rule_id` 表列。

### 2.3 状态机

不适用独立生命周期状态图；启用/停用由 `statusEnum` 表达，与软删正交。

```
[已启用] ──停用──> [已停用] ──恢复──> [已启用]
    │                   │
    └──废弃──> [已废弃]  └──废弃──> [已废弃]
```

#### 状态定义

| sortOrder | code | info | 语义 |
| --------- | ---- | ---- | ---- |
| 2 | `ACTIVE` | 已启用 | 生效中，参与执行路径规则匹配，允许编辑、停用、废弃 |
| 3 | `INACTIVE` | 已停用 | 暂时停用，不参与匹配但保留数据，允许恢复启用、废弃 |
| 4 | `DISCARDED` | 已废弃 | 逻辑废弃终态；软删时与 `is_deleted = true` 一并写入 |

> **状态枚举说明**：落库值为 `PublicationStatus.code`（`ACTIVE` / `INACTIVE` / `DISCARDED`），与 ChargeItemDefinition 发布状态同口径。执行路径匹配时**仅** `ACTIVE` 参与。

---

## 3. 实体

```text
ChargeItemAutoGen（聚合根，单表配置）
════════════════════════════════════════════════════════════

  ┌──────────────────────────────────────────────────────┐
  │  encounterId                                        │
  │  triggerSourceTable + triggerSourceId               │
  │  chargeDefinitionId / performingOrgId / quantity    │
  │  businessTime / billingPractitionerId               │
  │  priority / statusEnum / ruleName / remark         │
  └──────────────────────────────────────────────────────┘
```

### 3.1 收费明细自动生成规则（ChargeItemAutoGen）

ChargeItemAutoGen 聚合根的属性定义，对应 `cus_charge_item_auto_gen` 表。

| 属性 | 字段名 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| 规则标识 | `id` | `Long` | 是（库内） | 主键，雪花 ID；**仅落库后存在**，`createAutoGenRule` 命令不含此字段，由仓储保存后生成 |
| 规则名称 | `rule_name` | `String` | 是 | 管理端展示名称 |
| 状态 | `status_enum` | `String`（`PublicationStatus.code`） | 是 | 见 §2.3 状态定义、§4.1 |
| 住院就诊标识 | `encounter_id` | `Long` | 是 | 锚定 `adm_encounter.id`；住院滚费按 encounter 维护自动计费配置 |
| 触发来源表名 | `trigger_source_table` | `String` | 是 | 业务来源表名（落库字符串须与**来源聚合 DM 主档表名**一致，如诊疗 `wkf_activity_definition`，见 `DM-WKF-001` §3.1） |
| 触发来源主键 | `trigger_source_id` | `Long` | 是 | 业务来源记录主键（住院滚费：ActivityDefinition 主键） |
| 收费项定价标识 | `charge_definition_id` | `Long` | 是 | 锚定 `adm_charge_item_definition.id` |
| 执行科室标识 | `performing_org_id` | `Long` | 否 | 限定执行科室；为空表示全局规则，任意科室均可命中 |
| 默认数量 | `quantity_value` | `BigDecimal` | 是 | 生成 ChargeItem 的默认数量（默认 `1`） |
| 优先级 | `priority` | `Integer` | 是 | 同触发键下多规则排序（升序，数值越小越靠前；默认 `0`） |
| 滚费业务时刻 | `business_time` | `LocalTime` | 是 | 住院滚费配置项落账时刻（拼业务日；默认 `23:59:59`；护理滚费不读本列） |
| 滚费计费人 | `billing_practitioner_id` | `Long` | 是（新建） | `adm_practitioner.id`；写入 Charge `performer_id`；可随时改 |
| 备注 | `remark` | `String` | 否 | 规则补充说明 |

> 审计、租户、软删除字段继承自 `BaseAuditedEntity`，不在此处展开。

### 3.2 子实体

> 本聚合为单表配置模型，无聚合内子实体。

---

## 4. 值对象

### 4.1 发布状态（`PublicationStatus` → `statusEnum`）

| sortOrder | code | info | 语义说明 |
| --------- | ---- | ---- | ---- |
| 2 | `ACTIVE` | 已启用 | 参与规则匹配 |
| 3 | `INACTIVE` | 已停用 | 不参与匹配，可经 `activateAutoGenRule` 恢复 |
| 4 | `DISCARDED` | 已废弃 | 终态；`deleteAutoGenRule` 时与 `is_deleted = true` 一并写入 |

> 码值须与 **`DM-ADM-010` §4** 及院内 `PublicationStatus` 枚举一致，禁止与 §2.3 状态定义表冲突。若产品支持 `draft` 初态，取值行在 本稿 **§6.1** 中补全后再回写本文。

### 4.2 触发来源键（TriggerSourceKey）

由 `triggerSourceTable` + `triggerSourceId` 两个标量字段组合表达，定位触发来源的具体业务记录。

| 属性 | 类型 | 说明 |
|---|---|---|
| 触发来源表名 | `String` | 业务来源表名 |
| 触发来源主键 | `Long` | 业务来源记录主键 |

触发来源表名字符串（与各自 **DM 主档**一致，禁止与 Java 常量标识符混写）：

| 落库表名 | 对应聚合 | 权威 DM |
|---|---|---|
| `wkf_activity_definition` | ActivityDefinition | `DM-WKF-001` §3.1 |
| `med_medication_definition` | MedicationDefinition | `DM-MED-001` §3.1 |
| `adm_device_definition` | DeviceDefinition | `DM-ADM-008` §3.1 |

> `trigger_source_table` 与 `DM-ADM-010` 中 `instanceTable` **须同口径**（均取上表字符串）；跨文档禁止再写 `WOR_ACTIVITY_DEFINITION`、`CLN_DEVICE_DEFINITION` 等与 DM 不一致的占位。

### 4.3 术语表

| 名词 | 定义 |
|---|---|
| 触发来源 | 业务侧一条记录，由 `triggerSourceTable` + `triggerSourceId` 唯一定位（如某诊疗项目定义） |
| 住院就诊 | 单次住院 Encounter，由 `encounterId` 定位；住院滚费配置按 encounter 隔离 |
| 收费项定价 | 规则生成收费明细时所锚定的 ChargeItemDefinition，对应 `chargeDefinitionId`（列 `charge_definition_id`） |
| 执行科室限定 | 可选；`performingOrgId` 非空时，仅当执行上下文科室与该值一致时参与匹配；执行上下文无科室时不命中仅科室限定的规则 |
| 优先级 | 同一触发键下按 `priority` 升序、`id` 升序排序（数值越小越靠前） |
| 全局规则 | `performingOrgId` 为空的规则，任意执行科室上下文均可命中 |

---

## 5. 领域事件

### 5.1 事件定义

| 事件名称 | 英文名称 | 触发时机 | 携带数据 / 最小载荷 | 消费方 |
|---|---|---|---|---|
| 自动计费规则已变更 | `ChargeItemAutoGenEditedEvent` | 新增/更新/软删/启用或停用成功 | `aggregateId`（= 表 `id`）、`operation`、`triggerSourceTable`、`triggerSourceId`、`chargeDefinitionId`、`operatorId`（可空） | 计费编排、缓存/QueryService（可选） |

> 事件继承 `DomainEvent`（`eventId`、`eventTime`、`aggregateId`、`aggregateType=ChargeItemAutoGen`）。

### 5.2 事件触发规则

**写侧操作 → 发布事件**

| 操作 | 发布事件 |
|---|---|
| 新增规则（`createAutoGenRule`） | `ChargeItemAutoGenEditedEvent`（`OP_CREATE`） |
| 更新规则（`updateAutoGenRule`） | `ChargeItemAutoGenEditedEvent`（`OP_UPDATE`） |
| 软删规则（`deleteAutoGenRule`） | `ChargeItemAutoGenEditedEvent`（`OP_DELETE`） |
| 启用（`activateAutoGenRule`）/ 停用（`suspendAutoGenRule`） | `ChargeItemAutoGenEditedEvent`（`OP_STATUS_CHANGE`） |

**`statusEnum` 迁移与 `operation` 语义（与 §2.2 对齐）**

| 当前状态 | 操作 | 目标状态 | 说明 |
|---|---|---|---|
| — | 创建规则 | `ACTIVE`（缺省 `initActiveStatus`） | 见 §6.1.1 |
| `ACTIVE` | 停用 | `INACTIVE` | 对应 `suspendAutoGenRule` |
| `INACTIVE` | 启用 | `ACTIVE` | 对应 `activateAutoGenRule` |
| `ACTIVE` / `INACTIVE` | 废弃/软删 | `DISCARDED` + `is_deleted = true` | 对应 `deleteAutoGenRule`，终态不再变更 |

> 上述迁移的对外通知统一为 **§5.1** 中的 `ChargeItemAutoGenEditedEvent`（`operation` 区分语义），不在本文另造多套事件名。

---

## 6. 领域服务

### 6.1 写侧领域服务（ChargeItemAutoGenDomainService）

#### 6.1.1 新增自动计费规则（`createAutoGenRule`）

- **业务意义**：建立「触发来源 → 目标收费项定价」的自动生成规则，供医嘱执行等路径按触发键命中后生成 `ChargeItem`。
- **边界说明**：仅维护 `cus_charge_item_auto_gen` 行；不直接创建 `ChargeItem`、不计算金额；Command 来自 `command.chargeitemautogen` 包；`@Transactional` 由应用层定界。
- **入参**：`CreateAutoGenRuleCommand`（**无 `id`**；含 `encounterId`、触发来源表名/主键、`chargeDefinitionId`、可选执行科室、数量、优先级、状态等）。
- **出参**：`Long` — 新建行的 **`id`**（`repository.save` 后返回）。
- **前置条件**：BR-CAG-005/006 触发键非空；BR-CAG-004 目标定价存在且未软删；BR-CAG-001 组合唯一；缺省字段按约定默认 `quantityValue=1`、`priority=0`、`statusEnum=ACTIVE`（命令未指定时 `initActiveStatus`）。
- **状态变化**：`N/A`（新建即 `ACTIVE`，除非命令另有指定）。
- **聚合内影响**：插入一行规则主数据。
- **事件输出**：`ChargeItemAutoGenEditedEvent`（`OP_CREATE`）。
- **失败语义**：唯一冲突、外键无效、命令校验失败时拒绝。

#### 6.1.2 更新自动计费规则（`updateAutoGenRule`）

- **业务意义**：部分更新规则字段，使触发范围、目标定价或数量口径与业务变更同步。
- **边界说明**：不级联更新已生成的 `ChargeItem`；仅处理命令非空字段覆盖。
- **入参**：`UpdateAutoGenRuleCommand`（**`ruleId` 必填** = 已存在行的 `id`；其余业务字段可空，非空则覆盖）。
- **出参**：`—`。
- **前置条件**：BR-CAG-003 行存在且未软删；BR-CAG-001 + PR-CAG-001 唯一性排除自身；BR-CAG-004 若涉及目标定价则须有效。
- **状态变化**：`N/A`（启停须走 `activateAutoGenRule` / `suspendAutoGenRule`，本方法不改 `statusEnum`）。
- **聚合内影响**：更新单行对应列。
- **事件输出**：`ChargeItemAutoGenEditedEvent`（`OP_UPDATE`）。
- **失败语义**：行不存在、已软删、唯一或外键校验失败时拒绝。

#### 6.1.3 软删自动计费规则（`deleteAutoGenRule`）

- **业务意义**：使规则永久退出匹配集合，保留审计与历史追溯。
- **边界说明**：软删不物理清理历史；不删除已生成账单。
- **入参**：`DeleteAutoGenRuleCommand`（**`ruleId`** = 表 `id`，**`operatorId`**）。
- **出参**：`—`。
- **前置条件**：BR-CAG-010 行存在；已软删幂等可不重复发事件（以落库与交互契约为准）。
- **状态变化**：`ACTIVE/INACTIVE -> DISCARDED` 且 `is_deleted = true`（`markDeleted`）。
- **聚合内影响**：标记 `isDeleted`。
- **事件输出**：首次成功软删发 `ChargeItemAutoGenEditedEvent`（`OP_DELETE`）。
- **失败语义**：标识不存在且非幂等策略时拒绝。

#### 6.1.4 启用自动计费规则（`activateAutoGenRule`）

- **业务意义**：在不删行的情况下将规则置为**已启用**（`ACTIVE`），使其参与执行路径匹配。
- **边界说明**：不改变触发键与目标定价绑定关系（除非另有字段命令）；不写收费结果。
- **入参**：`ActivateAutoGenRuleCommand`（**`ruleId`** = 表 `id`，**`operatorId`**）。
- **出参**：`—`。
- **前置条件**：BR-CAG-003 行存在且未软删；BR-CAG-011 当前非 `DISCARDED`；当前为 `INACTIVE`（`assertActivatable`）。
- **状态变化**：`INACTIVE -> ACTIVE`。
- **聚合内影响**：更新 `statusEnum`。
- **事件输出**：`ChargeItemAutoGenEditedEvent`（`OP_STATUS_CHANGE`）。
- **失败语义**：当前状态不允许启用、行不存在或已软删时拒绝。

#### 6.1.5 停用自动计费规则（`suspendAutoGenRule`）

- **业务意义**：在不删行的情况下将规则置为**已停用**（`INACTIVE`），使其不参与执行路径匹配。
- **边界说明**：不改变触发键与目标定价绑定关系（除非另有字段命令）；不写收费结果。
- **入参**：`SuspendAutoGenRuleCommand`（**`ruleId`** = 表 `id`，**`operatorId`**）。
- **出参**：`—`。
- **前置条件**：BR-CAG-003 行存在且未软删；BR-CAG-011 当前非 `DISCARDED`；当前为 `ACTIVE`（`assertSuspendable`）。
- **状态变化**：`ACTIVE -> INACTIVE`。
- **聚合内影响**：更新 `statusEnum`。
- **事件输出**：`ChargeItemAutoGenEditedEvent`（`OP_STATUS_CHANGE`）。
- **失败语义**：当前状态不允许停用、行不存在或已软删时拒绝。

> **不设**统一 `changeAutoGenRuleStatus`（或等价「单命令切换目标状态」）入口：**启用与停用拆分为 `activateAutoGenRule` / `suspendAutoGenRule` 两个方法**，与 CUS 其它聚合「启停分方法」口径一致。

### 6.2 读侧查询服务（ChargeItemAutoGenQueryService）

#### 6.2.1 按触发键取生效规则列表（`findActiveRulesByTrigger`）

- **业务意义**：执行路径在「触发来源表 + 主键 + 可选科室」维度拉取候选规则并按优先级排序，为生成 `ChargeItem` 提供输入。
- **边界说明**：只读查询，不写库，不做跨域编排；不在此方法内生成账单。
- **查询范围（读库，无写入）**：`cus_charge_item_auto_gen` 未软删行；可按租户过滤。
- **过滤口径**：BR-CAG-002：仓储先按 `priority`、`id` 升序取行，再在内存筛 `ACTIVE` 与 `performingOrgId` 匹配。
- **组装规则**：返回有序列表，供应用层取首条或合并策略消费。
- **返回约束**：领域对象/DTO 列表；不含触发来源展示名 join（由应用层 `BR-CAG-QRY-003` 补全）。

#### 6.2.2 按住院就诊查询配置（`findRulesByEncounterId`）

- **业务意义**：划价页「**自动计费**」Tab 展示某次住院已配置的床位服务类自动计费项。
- **边界说明**：只读；含已停用行（由前端按 `status_enum` 展示）；不含护理级别（护理级别来自医嘱，只读展示由应用层组装）。
- **查询范围（读库，无写入）**：`cus_charge_item_auto_gen`；`encounter_id` 等值 + 未软删。
- **过滤口径**：`findAllByEncounterIdAndIsDeletedFalseOrderByPriorityAscIdAsc`。
- **组装规则**：按 `priority`、`id` 升序稳定列出。
- **返回约束**：列表可为空。

#### 6.2.3 按住院就诊取生效配置（`findActiveRulesByEncounter`）

- **业务意义**：住院滚费定时任务按 encounter 拉取 **ACTIVE** 且科室匹配的自动计费配置。
- **边界说明**：只读；不在此方法内生成 `ChargeItem`。
- **查询范围（读库，无写入）**：同 §6.2.2，再筛 `status_enum = ACTIVE` 与 `performingOrgId` 匹配（BR-CAG-002）。
- **组装规则**：返回有序列表，供应用层逐条调用与手动划价同链路的生成逻辑。
- **返回约束**：领域 QueryModel 列表。

#### 6.2.4 按收费项定价反查规则（`findRulesByChargeDefinitionId`）

- **业务意义**：定价变更或下线时，评估受影响的自动生成规则集合。
- **边界说明**：只读；不修改 `ChargeItemDefinition`。
- **查询范围（读库，无写入）**：本表；`charge_definition_id` 等价位过滤 + 未软删。
- **过滤口径**：`findAllByChargeDefinitionIdAndIsDeletedFalseOrderByIdAsc` 语义。
- **组装规则**：按 `id` 升序稳定列出。
- **返回约束**：列表可为空；不返回定价明细行。

#### 6.2.5 管理端分页查询（`pageChargeItemAutoGenRules`）

- **业务意义**：配置维护页按动态条件分页检索规则主数据（支持按 `encounterId` 过滤）。
- **边界说明**：只读；复杂筛选项与 join 展示可由应用层拼装后调用仓储 `Specification`（`BR-CAG-QRY-003`）。
- **查询范围（读库，无写入）**：`cus_charge_item_auto_gen` + `Specification` 动态 WHERE。
- **过滤口径**：租户、软删、状态、encounter、触发来源、收费项定价等组合条件。
- **组装规则**：分页对象含总行数、当前页数据；排序字段以产品为准。
- **返回约束**：分页 DTO；不嵌入导出二进制流（导出属应用层）。

### 6.3 领域边界说明

- `ChargeItemAutoGen` 仅描述「**encounter + 触发来源 → 收费项定价**」映射规则及启停；**ChargeItem** 的生成金额、医保分摊、账户引用由收费域编排完成。
- 写编排由 `ChargeItemAutoGenDomainService` 承担；读编排由 `ChargeItemAutoGenQueryService` 与仓储派生查询承担；触发来源展示名 join 业务表由应用层组装（`BR-CAG-QRY-003`）。
- 配置列表可直接查写表（`BR-CAG-QRY-001`）；按 `ACTIVE`/`INACTIVE` 过滤可在 QueryService 内存完成（`BR-CAG-QRY-002`）。

---

## 7. 不变式

| 编号 | 不变式 | 约束/落地要点 |
|---|---|---|
| `BR-CAG-001` | 同一 `(encounterId, triggerSourceTable, triggerSourceId, performingOrgId, chargeDefinitionId)` 在租户内禁止重复 | 创建 / 更新均校验；更新时排除自身 ID |
| `BR-CAG-002` | 科室匹配规则：`performingOrgId` 为空的全局规则任意上下文均可命中；非空规则仅当执行上下文科室非空且相等时命中；上下文无科室时不命中仅科室限定的规则 | 防止降级为全局的误匹配 |
| `BR-CAG-003` | 软删记录不参与默认业务查询，也不允许更新或启用/停用操作 | 仓储方法名含 `IsDeletedFalse`；已删行操作抛出异常 |
| `BR-CAG-004` | `chargeDefinitionId` 须指向存在且未软删的 ChargeItemDefinition | 创建 / 更新前校验；暂不校验定价 `status_enum` 或生效区间（如需要由应用层补强） |
| `BR-CAG-005` | 触发来源表名不允许为空 | 创建 / 更新时断言 |
| `BR-CAG-006` | 触发来源主键不允许为空 | 创建 / 更新时断言 |
| `BR-CAG-007` | 收费项定价标识（`chargeDefinitionId`）不允许为空 | 创建 / 更新时断言 |
| `BR-CAG-008` | 默认数量须大于零 | 防止生成无效收费明细 |
| `BR-CAG-009` | **启用与停用分属两个写方法**；不得以「目标状态可空」的隐式通用命令代替；各命令侧须能解析明确的状态迁移意图 | 与 `activateAutoGenRule` / `suspendAutoGenRule` 契约对齐 |
| `BR-CAG-010` | 更新 / 删除 / 启停操作的 **`ruleId`（Command）须对应已存在且未软删的 `id`（表）** | `findById(ruleId)` 前断言 |
| `BR-CAG-011` | 已废弃（`DISCARDED`）状态的规则不允许任何修改操作 | 终态保护；`assertEditable` |
| `BR-CAG-012` | 同触发键下命中多条规则时，按 `priority` 升序、`id` 升序有序解析 | 防止无序 List 导致不确定行为 |
| `BR-CAG-013` | `encounterId` 不允许为空 | 住院滚费场景下每条配置必须归属一次住院就诊；创建 / 更新时断言 |

### 7.1 子实体规则

> 本聚合为单表聚合，无聚合内子实体行；下列编号预留为 `PR-CAG-*` 过程规则。

- `PR-CAG-001`：更新时唯一性校验须排除自身规则 ID。
- `PR-CAG-002`：同触发键下排序与仓储 `OrderByPriorityAscIdAsc` 一致，避免解析顺序漂移。

---

## 8. 上下文映射

### 8.1 上游依赖

| 上游上下文 | 聚合 | 关联方式 | 说明 |
|---|---|---|---|
| 基础数据 · 诊疗项目目录 | ActivityDefinition | `触发来源表名 + 触发来源主键` | 规则的主要触发来源（住院滚费：床位服务类 `category_code = 51` 等） |
| 管理域 · 住院就诊 | Encounter | `encounter_id` | 住院滚费按 encounter 维度的自动计费配置归属 |
| 财务上下文 | ChargeItemDefinition | `charge_definition_id` | 规则锚定的收费项定价 |
| 组织架构 | Organization | `执行科室标识(OrganizationId)` | 可选，限定规则适用的执行科室 |

### 8.2 下游影响

| 下游上下文 | 聚合 | 触发方式 | 说明 |
|---|---|---|---|
| 财务收费上下文 | ChargeItem | 查询 | 医嘱执行 / 诊疗完成时，应用编排查询匹配规则后创建 ChargeItem |
| QueryService / 缓存 | — | 事件订阅 | 监听 `ChargeItemAutoGen*Event`，刷新规则缓存或QueryService |

### 8.3 引用规则

| 模型名称 | 关系类型 | 说明 |
|---|---|---|
| `DM-WKF-001 ActivityDefinition` | 关联 | 触发来源表 `wkf_activity_definition` 等 |
| `DM-ADM-010 ChargeItemDefinition` | 依赖 | `chargeDefinitionId` 锚定定价 |
| `DM-ADM-011 ChargeItem` | 依赖 | 规则匹配结果驱动生成收费明细 |
| `DM-ADM-004 Organization` | 关联 | 可选 `performingOrgId` 科室限定 |
| `DM-ADM-002 Encounter` | 关联 | `encounterId` 住院就诊归属 |

> **引用约定**：业务侧以表 **`id`** 引用规则行；Update/Delete/启停 Command 字段名 **`ruleId`** 与该 `id` 同值；住院滚费按 `encounterId` 查配置；追溯诊疗来源通过 `triggerSourceTable` + `triggerSourceId`。


### 8.4 事件流示意图

```
ActivityDefinition                ChargeItemAutoGen               ChargeItemDefinition              ChargeItem
       │                                │                                │                            │
       │── Published ─────────────────>│ (触发来源已发布，规则可匹配)      │                            │
       │                                │                                │                            │
       │                                │── 查询 chargeDefinitionId ────>│ (校验定价有效)               │
       │                                │                                │                            │
       │── 医嘱执行/诊疗完成 ──>         │                                │                            │
       │  (应用编排查询匹配规则)          │                                │                            │
       │                                │── 返回匹配规则列表 ────────────────────────────────────────>│ (生成 ChargeItem)
       │                                │                                │                            │
       │                                │── ChargeItemAutoGenEditedEvent ──> 缓存/QueryService（可选）       │
```

> 规则变更、操作与 `statusEnum` 迁移矩阵见 **§5.2**。

---

## 9. 补充

### 9.1 QueryService与查询规则

| 编号 | 规则 ID | 规则描述 | 约束 / 落地要点 |
|---|---|---|---|
| R-QRY-1 | `BR-CAG-QRY-001` | 配置列表可直接查写表 | 本版本不强制独立QueryService表 |
| R-QRY-2 | `BR-CAG-QRY-002` | 未删除由仓储保证；启用在 QueryService 内存过滤；排序与 PR-CAG-002 一致 | 仓储 `OrderByPriorityAscIdAsc` + QueryService 保序过滤 |
| R-QRY-3 | `BR-CAG-QRY-003` | 触发来源展示名称需 join 业务表，不在本聚合内冗余存储 | 由应用层组装 |

### 9.2 自动计费触发流程

```
1. 医嘱执行或诊疗完成事件到达应用编排层
2. 应用编排提取触发来源信息（triggerSourceTable + triggerSourceId + 执行科室 ID）
3. 调用 ChargeItemAutoGenQueryService#findActiveRulesByTrigger 获取匹配规则列表
4. 按优先级顺序遍历规则，逐条创建 ChargeItem：
   a. 从规则取 chargeDefinitionId，查询 ChargeItemDefinition 获取定价信息
   b. 从规则取 quantityValue 作为默认数量
   c. 生成来源标记为「自动生成」（generateSourceEnum 由应用层标注）
5. 将生成的 ChargeItem 列表持久化
```

### 9.2.1 住院滚费（自动计费）触发流程

```
1. 定时任务（每日 00:05）计算计费日 billDate = 前一日自然日
2. 扫描在院 encounter（分床后、清床前；边界判定由应用层/任务实现）
3. 对每个 encounter 调用 ChargeItemAutoGenQueryService#findActiveRulesByEncounter
4. 对每条 ACTIVE 配置（床位服务类，trigger_source_table = wkf_activity_definition）：
   a. 校验 billDate 是否在配置生效区间内（新增日从创建日起算；删停后不再参与）
   b. 幂等：若同日同 encounter 同 chargeDefinitionId 已有 ChargeItem 则跳过
   c. 调用与护士手动划价相同的 req + ChargeItem 生成链路，数量取 quantityValue
5. 护理级别费不在此表配置：由应用层按当日最后校对的有效护理医嘱单独分支生成
```

### 9.3 科室匹配逻辑详解

| 规则 performingOrgId | 执行上下文科室 | 是否命中 | 说明 |
|---|---|---|---|
| 空（全局规则） | 任意（含空） | **是** | 全局规则无科室限制 |
| 非空（科室限定规则） | 与规则一致 | **是** | 科室精确匹配 |
| 非空（科室限定规则） | 与规则不一致 | **否** | 科室不匹配 |
| 非空（科室限定规则） | 空 | **否** | 上下文无科室时不降级为全局 |

### 9.4 与 ChargeItemDefinition / ChargeItem 的职责边界

| 职责 | ChargeItemAutoGen | ChargeItemDefinition | ChargeItem |
|---|---|---|---|
| 定义对象 | encounter + 触发→定价映射规则 | 项目多维度价格事实 | 费用明细事实 |
| 生命周期 | 配置型，独立管理 | 配置型，独立管理 | 业务型，依赖触发事实 |
| 写操作 | 管理端运维人员维护 | 价格维护人员维护 | 由应用编排自动或手工创建 |
| 医保 | 不直接涉及 | 维护医保目录分类 | 记录医保结算状态 |
| 与触发来源关系 | 引用触发来源表名 + 主键 | 引用项目定位键（instanceTable + instanceId） | 引用服务来源表名 + 主键 |

### 9.5 规则维护注意事项

- **新增规则**：默认 `ACTIVE`（`initActiveStatus`）；停用后须经 `activateAutoGenRule` 恢复
- **停用规则**：停用后规则不参与匹配，但历史已生成的 ChargeItem 不受影响
- **废弃规则**：软删除不可逆，仅在确认规则永久废弃时操作
- **优先级调整**：同一触发键下如需插入新规则到特定顺序位，需统筹调整相关规则的 `priority` 值
- **科室限定规则与全局规则共存**：同一触发键下可同时存在全局规则和科室限定规则，匹配时两者均可命中（应用层按优先级取舍或全部执行，以编排逻辑为准）

---

> **关联文档：** `DM-ADM-010-ChargeItemDefinition`、`DM-ADM-011-ChargeItem`、`DM-CUS-001`、`DM-WKF-001`目录同名文档。


### 9.1 变更历史

| 版本 | 日期 | 摘要 |
| --- | --- | --- |
| v1.0.3 | 2026-06-19 | **应用层对接**：**AS-IP-NUR-013** 消费本聚合五写五读；`createdAt` 读模型支撑滚费 **BR-IP-ARG-013**（见 **AS-IP-NUR-013-SDD**） |
| v1.0.2 | 2026-06-19 | **住院滚费**：`encounter_id`、`charge_definition_id`；读侧 encounter 查询；**`id` 自动生成，Command `ruleId`≠表列**；状态码对齐 `PublicationStatus` |
| v1.0.1 | 2026-05-28 | **M1**：§6.1/§6.2 与 `ChargeItemAutoGenDomainService`/`ChargeItemAutoGenQueryService` 白名单对齐（5写+5读） |
| v1.0 | — | 初版 |

<!-- MODE-B-METHOD-INVENTORY 2026-09-23 -->

## 附录 · 模式 B 已实现方法清单（2026-09-23）

> 自 zxplat-domain 对应 *DomainService / *QueryService 抽取 public 方法名，用于关闭方法白名单 DOC-DRIFT。细规仍以正文 §6 与源码为准；本表不替代九章成稿。

### DomainService：`ChargeItemAutoGenDomainService`

| 方法 |
|------|
| `createAutoGenRule` |
| `updateAutoGenRule` |
| `deleteAutoGenRule` |
| `activateAutoGenRule` |
| `suspendAutoGenRule` |

### QueryService：`ChargeItemAutoGenQueryService`

| 方法 |
|------|
| `findRulesByEncounterId` |
| `findActiveRulesByEncounter` |
| `findActiveRulesByTrigger` |
| `findRulesByChargeDefinitionId` |
| `pageChargeItemAutoGenRules` |

