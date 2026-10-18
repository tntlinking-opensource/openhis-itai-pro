# DM-CUS-004 EncounterHistory 就诊历史

> **限界上下文**：本地扩展 · 就诊历史管理\
> **聚合类型**：实体聚合（单表聚合，追加事实行）\
> **版本**：v1.0.1 · **日期**：2026-05-28

## 1. 概述

### 1.1 业务定义

就诊历史（EncounterHistory）是**就诊（Encounter）生命周期中关键里程碑的追加式快照**，以一行一事实的方式记录转科、离院等与医嘱请求关联的过程节点，为医生站、护士站、病案管理和对外交换提供「就诊内关键流转轨迹」的查询基础。

EncounterHistory 是典型的 **CQRS 读侧投影**——它不驱动 Encounter 主状态迁移，而是在写侧医嘱落库后同事务追加一行事实记录，供下游读侧场景（病历调阅、病案首页、质控统计）消费。EncounterHistory 是 Encounter 的**读侧投影补充**，不替代 Encounter 主档状态，也不替代 EncounterLocation 的连续位置轨迹维护。本文档编码为 `DM-CUS-004`；语义对齐 [FHIR R5 EncounterHistory](https://hl7.org/fhir/R5/encounterhistory.html)；对外交换时里程碑需拆分映射到 `Encounter`（历史片段扩展）与 `Location` 组合。

### 1.2 核心职责

| 职责 | 说明 |
|---|---|
| 里程碑快照记录 | 在转科/离院等医嘱请求落库后，追加一行事实行，记录原/目标科室与病区的变迁 |
| 就诊流转轨迹查询 | 按就诊维度提供时间序里程碑列表，支撑流转轨迹展示与病历调阅 |
| 出院方式解析 | 记录离院方式编码，为病案首页、对外接口提供出院方式查询依据 |
| 审计可追溯 | 以追加为主、受控更正为辅，保障事实链完整性与审计合规 |

### 1.3 适用范围

| 类型 | 说明 |
|---|---|
| 医生站 / 护士站流转查看 | 展示当前就诊的转科、离院等历史里程碑 |
| 病案首页生成 | 取出院方式、末次转科信息等填充病案首页字段 |
| 病历调阅 | 按患者维度聚合历次就诊的里程碑快照，形成摘要视图 |
| 质控与统计 | 按里程碑类型统计科室流转频次、平均住院转科次数等 |
| 对外交换 / 集成 | 为区域卫生信息平台等外部系统提供就诊过程摘要数据 |

### 1.4 与关键上下游及交换边界的关系

里程碑行归属一次就诊（Encounter），事实来源主要为服务申请（ServiceRequest）落库时的同事务编排；病案首页、质控统计等通过查询或订阅事件消费。本聚合**不替代** Encounter 状态机，也不承担位置连续轨迹职责（见 EncounterLocation 等相关模型）。

| 维度 | 上游 | 本聚合 | 下游 |
|---|---|---|---|
| 锚点 | Encounter、ServiceRequest | 追加式里程碑事实行 | CaseRecord、质控QueryService、对外集成 |
| 一致性 | 与医嘱同事务写入 | 只追加、受控更正 | 查询侧最终一致 |

---

## 2. 聚合根与持久化形态

### 2.1 持久化形态

**持久化形态**：单表。

聚合根对应表 `cus_encounter_history`，主键 `id`；一行表示一次就诊内与医嘱请求关联的里程碑快照。

**高频查询模式（建议索引口径）**：按 `encounter_id` 时间序列表；按 `request_id` + `request_table_name` 回查；病案/质控按时间窗与里程碑类型筛选。

### 2.2 聚合根标识

| 属性 | 类型 | 说明 |
|---|---|---|
| 就诊历史标识 | `EncounterHistoryId` | 系统内部唯一标识（雪花 ID） |

### 2.3 状态机

本聚合以**事实行追加**表达里程碑，不设独立业务状态机；就诊生命周期仍以 `Encounter` 为准。

```
  [已记录] ──更正──> [已更正]
      │
      └──废弃──> [已废弃]（逻辑删除）
```

#### 记录状态说明

| 状态 | 英文 | 说明 |
|---|---|---|
| 已记录 | `recorded` | 里程碑事实行已追加 |
| 已更正 | `corrected` | 原事实行经受控更正流程修改 |
| 已废弃 | `deleted` | 逻辑删除，不参与默认查询 |

> 追加为主、更正为辅：常规场景仅追加新行；确需更正时走受控更正策略（新增更正行或标记修改），不得破坏审计链。

---

## 3. 实体

```text
EncounterHistory（聚合根，单表追加事实）
════════════════════════════════════════════════════════════

  ┌──────────────────────────────────────────────────────┐
  │  encounterId / milestoneType / requestRef           │
  │  OrganizationTransition / LocationTransition       │
  │  DischargeWay / audit timestamps                     │
  └──────────────────────────────────────────────────────┘
```

### 3.1 就诊历史（EncounterHistory）

EncounterHistory 聚合根的属性定义，对应 `cus_encounter_history` 表。

| 属性 | 字段名 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| 就诊历史标识 | `id` | `Long` | 是 | 主键，雪花 ID |
| 里程碑类型名称 | `name` | `String` | 是 | 过程类型业务键（如转科、离院等业务常量对应的语义标签） |
| 就诊标识 | `encounter_id` | `Long` | 是 | 归属哪一次就诊过程 |
| 请求标识 | `request_id` | `Long` | 否 | 关联医嘱请求主键（如 ServiceRequest） |
| 请求表名 | `request_table_name` | `String` | 否 | 请求所在逻辑表名，与请求标识成对解析 |
| 原科室标识 | `original_organization_id` | `Long` | 否 | 科室变迁：原科室 ID |
| 目标科室标识 | `target_organization_id` | `Long` | 否 | 科室变迁：目标科室 ID |
| 原病区/位置标识 | `original_location_id` | `Long` | 否 | 位置变迁：原病区/位置 ID |
| 目标病区/位置标识 | `target_location_id` | `Long` | 否 | 位置变迁：目标病区/位置 ID |
| 出院方式编码 | `out_way_code` | `String` | 否 | 离院类里程碑专用，见 §4.4 |

> 审计字段（`createdBy / updatedBy` 等）继承自 `BaseAuditedEntity`，与院内约定一致。

### 3.2 子实体

> 本聚合为单表聚合，无聚合内子实体。所有里程碑信息通过聚合根属性与值对象表达。

---

## 4. 值对象

### 4.1 里程碑类型（MilestoneType → `name` / 类型字段）

 落库的里程碑类型与 §3.1 中 `name`/类型列一致；若落库为编码，须与下列 `code` 可互转。

| value（若落库编码） | code | info | 语义说明 |
|---|---|---|---|
| 1 | `transfer` | 转科 | 科室间转移（含科室 + 病区变迁） |
| 2 | `discharge` | 离院 | 出院/转院/死亡等 |
| 3 | `admission_to_dept` | 入科 | 住院首次分配科室 |
| 4 | `bed_transfer` | 转床 | 同科室内床位/病区变更 |

### 4.2 科室变迁（OrganizationTransition）

| 属性 | 类型 | 说明 |
|---|---|---|
| 原科室标识 | `DepartmentId` | 变迁前所在科室 |
| 目标科室标识 | `DepartmentId` | 变迁后目标科室 |

> 转科类里程碑必填；离院类里程碑记录离院时所在科室上下文。

### 4.3 位置变迁（LocationTransition）

| 属性 | 类型 | 说明 |
|---|---|---|
| 原位置标识 | `LocationId` | 变迁前病区/床位 |
| 目标位置标识 | `LocationId` | 变迁后目标病区/床位 |

> 与科室变迁配合使用，表达科室维度 + 位置维度的双重变迁。

### 4.4 出院方式（DischargeWay）

| 属性 | 类型 | 说明 |
|---|---|---|
| 出院方式编码 | `String` | 按院内字典编码（如医嘱字典） |
| 出院方式名称 | `String` | 编码对应的显示文本 |

> 仅离院类里程碑使用。病案首页、对外接口依赖此值对象。

---

## 5. 领域事件

### 5.1 事件定义

| 事件名称 | 英文名称 | 触发时机 | 携带数据 / 最小载荷 | 消费方 |
|---|---|---|---|---|
| 转科里程碑已记录 | `TransferMilestoneRecordedEvent` | 转科医嘱落库后追加事实行 | `encounterHistoryId`、`encounterId`、`requestId` | 病案、QueryService、质控（可选） |
| 离院里程碑已记录 | `DischargeMilestoneRecordedEvent` | 离院医嘱落库后追加事实行 | `encounterHistoryId`、`encounterId`、`outWayCode`（若有） | 病案、QueryService、质控（可选） |
| 入科里程碑已记录 | `AdmissionMilestoneRecordedEvent` | 入科事实落库 | `encounterHistoryId`、`encounterId` | 病案、QueryService（可选） |
| 里程碑已更正 | `MilestoneCorrectedEvent` | 受控更正 | `encounterHistoryId`、更正原因锚点 | 病案、审计、QueryService（可选） |
| 里程碑已废弃 | `MilestoneRetiredEvent` | 逻辑废弃 | `encounterHistoryId` | 病案、QueryService（可选） |

### 5.2 事件触发规则

**写侧方法 → 发布事件**

| 写侧方法 | 发布事件 |
|---|---|
| `recordTransferMilestone` | `TransferMilestoneRecordedEvent` |
| `recordDischargeMilestone` | `DischargeMilestoneRecordedEvent` |
| `recordAdmissionMilestone` | `AdmissionMilestoneRecordedEvent` |
| `correctMilestone` | `MilestoneCorrectedEvent` |
| `retireMilestone` | `MilestoneRetiredEvent` |

**状态 / 操作矩阵（与 §2.3 记录状态语义对齐）**

| 当前状态 | 操作 | 目标状态 | 发布事件 |
|---|---|---|---|
| — | 记录转科里程碑 | 已记录 | `TransferMilestoneRecordedEvent` |
| — | 记录离院里程碑 | 已记录 | `DischargeMilestoneRecordedEvent` |
| — | 记录入科里程碑 | 已记录 | `AdmissionMilestoneRecordedEvent` |
| 已记录 | 受控更正 | 已更正 | `MilestoneCorrectedEvent` |
| 已记录 / 已更正 | 废弃 | 已废弃 | `MilestoneRetiredEvent` |

---

## 6. 领域服务

### 6.1 写侧领域服务（EncounterHistoryDomainService）

> 写侧由应用服务编排事务后调用；`@Transactional` 由应用层定界；与医嘱请求等同事务写入须满足 `BR-EHR-009`。

#### 6.1.1 记录转科里程碑（`recordTransferMilestone`）

- **业务意义**：在就诊维度追加一条转科事实，用于流转轨迹与病案追溯。
- **边界说明**：不修改 `Encounter` 主档状态机；不写其他聚合；请求引用须可解析。
- **入参**：就诊标识、请求标识、请求表名、原/目标科室标识、原/目标位置标识等。
- **出参**：新建就诊历史标识。
- **前置条件**：就诊存在且未终止（BR-EHR-008）；BR-EHR-004 转科科室变迁不得全空；BR-EHR-003 请求标识与表名成对。
- **状态变化**：`N/A`（追加事实行）。
- **聚合内影响**：插入一行里程碑记录。
- **事件输出**：`TransferMilestoneRecordedEvent`。
- **失败语义**：就诊状态不允许、引用不成对、必填缺失时拒绝。

#### 6.1.2 记录离院里程碑（`recordDischargeMilestone`）

- **业务意义**：记录离院类里程碑及出院方式，支撑病案首页与统计口径。
- **边界说明**：不直接办结就诊；出院方式字典校验属域内或应用层协同（以本稿约定为准）。
- **入参**：就诊标识、请求标识、请求表名、出院方式编码、科室/位置上下文等。
- **出参**：就诊历史标识。
- **前置条件**：就诊存在；BR-EHR-005 出院方式编码有效；上下文与开立表单一致。
- **状态变化**：`N/A`。
- **聚合内影响**：插入离院里程碑行。
- **事件输出**：`DischargeMilestoneRecordedEvent`。
- **失败语义**：字典无效、引用不完整时拒绝。

#### 6.1.3 记录入科里程碑（`recordAdmissionMilestone`）

- **业务意义**：住院路径下入科事实落库，衔接床位与护理上下文。
- **边界说明**：仅适用于住院类就诊语义；不分配床位。
- **入参**：就诊标识、目标科室标识、目标位置标识等。
- **出参**：就诊历史标识。
- **前置条件**：就诊存在且为住院类型；目标科室必填（以不变式/本稿 **§6.1** 为准）。
- **状态变化**：`N/A`。
- **聚合内影响**：插入入科里程碑行。
- **事件输出**：`AdmissionMilestoneRecordedEvent`。
- **失败语义**：就诊类型不符、科室缺失时拒绝。

#### 6.1.4 更正里程碑（`correctMilestone`）

- **业务意义**：在保留审计链前提下纠正错误里程碑字段，满足病案与质控要求。
- **边界说明**：BR-EHR-006 禁止直接覆盖破坏审计；策略为新增更正行或受控修改以落库与交互契约为准。
- **入参**：就诊历史标识、更正字段集、更正原因。
- **出参**：`—`。
- **前置条件**：原记录为已记录且非废弃；更正原因必填。
- **状态变化**：依策略可能为「追加更正关联」或「受控字段迁移」。
- **聚合内影响**：新增更正事实或受限更新（不破坏追溯）。
- **事件输出**：`MilestoneCorrectedEvent`。
- **失败语义**：原记录不可更正、原因缺失时拒绝。

#### 6.1.5 废弃里程碑（`retireMilestone`）

- **业务意义**：逻辑废弃错误录入的里程碑，使其退出默认列表与统计。
- **边界说明**：BR-EHR-007 废弃行不参与默认查询；不物理删除。
- **入参**：就诊历史标识、废弃原因。
- **出参**：`—`。
- **前置条件**：原记录非已废弃状态。
- **状态变化**：`已记录 -> 已废弃（逻辑删除标记）`。
- **聚合内影响**：逻辑删除标记。
- **事件输出**：`MilestoneRetiredEvent`。
- **失败语义**：重复废弃、标识不存在时拒绝。

### 6.2 读侧查询服务（EncounterHistoryQueryService）

#### 6.2.1 按就诊列里程碑（`listMilestonesByEncounter`）

- **业务意义**：按时间顺序展示单就诊下全部里程碑，用于流转轨迹与病历调阅。
- **边界说明**：只读查询，不写库，不做跨域编排；BR-EHR-007 默认排除逻辑删除行。
- **查询范围（读库，无写入）**：就诊历史表；`encounterId` 过滤。
- **过滤口径**：未逻辑删除；可按里程碑类型扩展筛选。
- **组装规则**：按创建时间升序。
- **返回约束**：事实列表；不含跨聚合大报表投影。

#### 6.2.2 查最近转科上下文（`findLatestTransferContext`）

- **业务意义**：解析末次转科的原/目标科室与位置，供当前上下文默认值或提示。
- **边界说明**：只读查询，不写库，不做跨域编排；单条结果语义，不修改就诊主档。
- **查询范围（读库，无写入）**：转科类里程碑子集；就诊 + 可选原科室过滤。
- **过滤口径**：按创建时间倒序取第一条有效记录。
- **组装规则**：返回最近一条的科室/位置上下文 DTO。
- **返回约束**：无记录时返回空；不推断未发生的转科。

#### 6.2.3 解析离院方式（`resolveDischargeWay`）

- **业务意义**：从离院里程碑或联合 `Encounter.endTime` 解析对外口径的离院方式，供病案首页与接口。
- **边界说明**：只读查询，不写库，不做跨域编排；多条离院事实时策略需与业务确认或以本稿 **§6.1** 联合判定为准。
- **查询范围（读库，无写入）**：离院类里程碑；可选读 `Encounter` 只读投影。
- **过滤口径**：未删除；时间或有效性规则以落库与交互契约为准。
- **组装规则**：归一为单一离院方式编码或带置信度的结果（以落库与交互契约为准）。
- **返回约束**：不写入任何表；歧义场景由应用层提示人工确认。

### 6.3 领域边界说明

- `EncounterHistory` 仅追加/更正/废弃里程碑事实行，不维护 Encounter 主状态机；**Encounter** 状态迁移由他域负责。
- 写编排与读编排分离；同一事务内与 ServiceRequest 落库一致（`BR-EHR-009`）。
- 页面级大报表、患者全量轨迹投影不强制记入本节编号。

---

## 7. 不变式

| 编号 | 不变式 | 约束/落地要点 |
|---|---|---|
| BR-EHR-001 | 每行里程碑必须归属唯一就诊：`就诊标识(EncounterId)` 必填 | 就诊归属完整性 |
| BR-EHR-002 | 里程碑类型必须为有效枚举值 | 类型合法性 |
| BR-EHR-003 | 请求标识与请求表名须成对可解析，不得出现孤立引用 | 引用完整性 |
| BR-EHR-004 | 转科类里程碑：科室变迁（原科室 / 目标科室）不得全空 | 转科业务完整性 |
| BR-EHR-005 | 离院类里程碑：出院方式编码按院内字典有效，不得为空 | 离院业务完整性 |
| BR-EHR-006 | 事实行以追加为主；确需更正时走受控更正策略，不得直接覆盖原记录破坏审计链 | 审计安全性 |
| BR-EHR-007 | 逻辑删除记录不参与默认列表与统计查询 | 查询一致性 |
| BR-EHR-008 | 就诊不存在或已终止时，拒绝新增里程碑（受控补录除外） | 就诊状态约束 |
| BR-EHR-009 | 医嘱请求未保存成功时，不应写入里程碑行（同一事务回滚） | 事务一致性 |
| BR-EHR-010 | 同一就诊下可存在多条同类型里程碑（如多次转科），按时间序排列 | 多次变迁合法性 |

### 7.1 子实体规则

> 本聚合为单表聚合，无聚合内子实体行。

---

## 8. 上下文映射

### 8.1 上游依赖

| 上游上下文 | 聚合 | 关联方式 | 说明 |
|---|---|---|---|
| 就诊管理 | Encounter | `就诊标识(EncounterId)` | 里程碑归属的就诊上下文 |
| 工作流 · 服务申请 | ServiceRequest | `请求标识 + 请求表名` | 转科/离院等医嘱请求事实来源 |
| 组织架构 | Organization | `科室标识(DepartmentId)` | 原/目标科室解析 |
| 位置管理 | Location | `位置标识(LocationId)` | 原/目标病区/床位解析 |

### 8.2 下游影响

| 下游上下文 | 聚合 | 触发方式 | 说明 |
|---|---|---|---|
| 病案管理 | CaseRecord | 查询 | 病案首页取出院方式、转科信息 |
| 质控统计 | — | 事件 / 查询 | 转科频次、平均住院里程碑统计 |
| 对外交换 | — | 查询 | 区域卫生平台获取就诊过程摘要 |
| 领域事件后查询刷新 | — | 事件 | 事件驱动的QueryService刷新 |

### 8.3 引用规则

| 模型名称 | 关系类型 | 说明 |
|---|---|---|
| `DM-ADM-002 Encounter` | 关联 | `encounterId` 归属就诊 |
| `DM-WKF-004 ServiceRequest` | 依赖 | `请求标识 + 请求表名` 指向医嘱事实 |
| `DM-ADM-003 Location` | 关联 | 位置变迁中的 `LocationId` |
| `DM-ADM-004 Organization` | 关联 | 科室变迁中的科室标识 |

> **引用约定**：下游以 `EncounterHistoryId` 引用里程碑行；患者维度通过 Encounter 间接得到。


### 8.4 事件流示意图

```
ServiceRequest          EncounterHistory          CaseRecord          质控/QueryService
      │                        │                      │                    │
      │── 转科医嘱落库 ───────>│                      │                    │
      │                        │── TransferMilestone  │                    │
      │                        │   RecordedEvent ────>│ (病案首页填充)      │
      │                        │                ─────────────────────────>│
      │                        │                      │                    │
      │── 出院医嘱落库 ───────>│                      │                    │
      │                        │── DischargeMilestone │                    │
      │                        │   RecordedEvent ────>│ (出院方式)          │
      │                        │                ─────────────────────────>│
      │                        │                      │                    │
      │                        │<── 查询里程碑列表 ────│                    │
```

> 事件主路径与状态矩阵见 **§5.2**；里程碑记录状态定义见 **§2.3**。

---

## 9. 补充

### 9.1 CQRS QueryService定位

| 维度 | 说明 |
|---|---|
| 写入时机 | 由写侧业务动作（转科/离院医嘱落库）在同一事务中追加，非独立写模型 |
| 读取场景 | 病历调阅、病案首页、流转轨迹展示、质控统计等纯查询场景 |
| 与 Encounter 的关系 | Encounter 维护就诊主状态（状态机驱动），EncounterHistory 维护过程快照（追加事实） |
| 最终一致性 | 里程碑与医嘱请求同事务落库，保证强一致；下游QueryService通过事件实现最终一致 |

### 9.2 与 EncounterLocation 的职责划分

| 维度 | EncounterLocation | EncounterHistory |
|---|---|---|
| 管理对象 | 就诊内连续位置轨迹（当前在哪） | 就诊内关键里程碑快照（发生了什么） |
| 粒度 | 每次位置变更一行 | 仅医嘱驱动的关键节点一行 |
| 权威性 | 位置维度的权威来源 | 里程碑维度的查询基础 |
| 触发方式 | 位置变更业务自动维护 | 医嘱请求落库时同事务追加 |

### 9.3 转科里程碑记录流程

```
转科医嘱开立时的里程碑记录流程：

1. 医生在医生站开立转科医嘱（ServiceRequest）
2. ServiceRequest 保存成功后，应用服务编排调用 recordTransferMilestone
3. 同一事务内追加 EncounterHistory 一行：
   - 里程碑类型 = transfer
   - 科室变迁 = 原科室 → 目标科室
   - 位置变迁 = 原病区 → 目标病区
4. 发布 TransferMilestoneRecordedEvent
5. Encounter 主状态变更由接诊/转科完成等后续动作负责（非本聚合职责）
```

### 9.4 受控更正策略

| 更正方式 | 适用场景 | 审计要求 |
|---|---|---|
| 新增更正行 | 科室/位置填写错误，需记录更正痕迹 | 原行保留，新行标记为已更正，关联原行 |
| 受控字段修改 | 出院方式编码录入有误等简单字段更正 | 记录更正原因、更正人、更正时间 |
| 逻辑废弃 | 误操作产生的无效记录 | 逻辑删除标记，不物理删除 |

---

> **关联文档：** `DM-ADM-002-Encounter`、`DM-WKF-004-ServiceRequest`。


### 9.1 变更历史

| 版本 | 日期 | 摘要 |
| --- | --- | --- |
| v1.0.1 | 2026-05-28 | **M1**：§6.1/§6.2 与 `EncounterHistoryDomainService`/`EncounterHistoryQueryService` 白名单对齐（7写+7读） |
| v1.0 | — | 初版 |

<!-- MODE-B-METHOD-INVENTORY 2026-09-23 -->

## 附录 · 模式 B 已实现方法清单（2026-09-23）

> 自 zxplat-domain 对应 *DomainService / *QueryService 抽取 public 方法名，用于关闭方法白名单 DOC-DRIFT。细规仍以正文 §6 与源码为准；本表不替代九章成稿。

### DomainService：`EncounterHistoryDomainService`

| 方法 |
|------|
| `recordTransferMilestone` |
| `recordDischargeMilestone` |
| `recordBedCardPrintMilestone` |
| `recordAdmissionMilestone` |
| `correctMilestone` |
| `retireMilestone` |

### QueryService：`EncounterHistoryQueryService`

| 方法 |
|------|
| `listMilestonesByEncounter` |
| `findLatestTransferContext` |
| `resolveDischargeWay` |
| `listEncounterHistoryEntitiesByEncounterIds` |

