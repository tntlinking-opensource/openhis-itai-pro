# DM-CUS-005 ServiceDeviceTemplate 诊疗耗材绑定

> **限界上下文**：本地扩展 · 诊疗耗材绑定管理  
> **聚合类型**：实体聚合（单表配置型）  
> **版本**：v1.0.7 · **日期**：2026-10-06

---

## 1. 概述

### 1.1 业务定义

诊疗耗材绑定（ServiceDeviceTemplate）定义了**来源项（用法 / 诊疗项目）与绑定目标（诊疗项目 / 耗材定义）之间的默认绑定关系**。它回答的核心业务问题是："**当某个来源项被触发时，应自动附加哪些诊疗项目或耗材，默认带出多少数量？**"——在医生开立医嘱、护理执行等业务场景中，系统据此自动带出绑定的目标项目及其默认数量与单位，减少手工录入，保障配置一致性。

诊疗耗材绑定是 **ActivityDefinition**、**DeviceDefinition** 与业务消费方之间的桥梁，属配置型主数据。本文档编码为 `DM-CUS-005`；对外交换时效果体现在 `ServiceRequest` / `DeviceRequest`，规则本身不对外暴露。

### 1.2 核心职责

| 职责 | 说明 |
|---|---|
| 绑定关系维护 | 维护来源项与目标项之间的默认绑定关系（数量、单位、发布状态） |
| 多类型绑定支持 | 支持用法绑定、诊疗绑定两种绑定类型 |
| 发布状态管理 | 通过启用 / 停用状态控制绑定是否参与业务匹配 |
| 消费方查询支撑 | 为医嘱开立、护理执行等消费方提供按条件查询活跃绑定列表的能力 |

### 1.3 适用范围

| 类型 | 说明 |
|---|---|
| 诊疗耗材绑定配置 | 管理员在 WH-BND001 页面按 Tab 选定来源项，维护右侧绑定行（新增 / 更新保存；移除为**逻辑删除**） |
| 医嘱开立附加耗材 | **门诊**开立诊疗医嘱时按诊疗绑定自动附加；**住院开立不带**（**BR-IP-ORD-036**），未合管模板由护士执行挂载 |
| 门诊暂存前绑定解析 | 医生站 **IF-DOR-23**：IF-DOR-14 暂存前，按**暂存变更行**解析药品用法 / 诊疗定义上的**直接**模板绑定（**不级联**、**不去重**；去重与派生行落库由前端在 IF-DOR-14 前处理） |
| 护理执行附加耗材 | 护理站执行时，按用法绑定自动附加耗材或诊疗项目 |
| 绑定变更影响分析 | 通过事件通知下游缓存失效，保障消费方获取最新绑定关系 |

### 1.4 与关键上下游及交换边界的关系

绑定来源可指向用法术语概念或诊疗项目（ActivityDefinition）；绑定目标可指向诊疗项目或耗材定义（DeviceDefinition）。医嘱开立、护理执行等消费方通过查询服务读取**启用**绑定；产品定义展示字段由应用层编排。本聚合不维护库存与价格事实。

| 维度 | 上游 | 本聚合 | 消费方 |
|---|---|---|---|
| 主数据 | ActivityDefinition / DeviceDefinition / HealthcareService | 来源→目标绑定行 | ServiceRequest、结算与执行链路 |
| 删除语义 | — | 逻辑删除（见写侧） | 未删除行参与默认匹配 |

---

## 2. 聚合根与持久化形态

### 2.1 持久化形态

**持久化形态**：单表。

聚合根对应表 `cus_service_device_template`，主键 `id`；一行表示一条「来源项（用法/诊疗）→ 目标项（诊疗或耗材定义）」绑定规则。

**高频查询模式（建议索引口径）**：按 `item_table` + `item_id`、`type_code`、`range_code` 过滤；消费方匹配按未逻辑删除 + `status_enum = ACTIVE`；配置分页按来源维度与排序字段（以落库与交互契约为准）。

### 2.2 聚合根标识

| 属性 | 类型 | 说明 |
|---|---|---|
| 诊疗耗材绑定标识 | `ServiceDeviceTemplateId` | 系统内部唯一标识（`Long`，序列生成） |

### 2.3 状态机

绑定行以**发布状态**（启用 / 停用）表达是否参与匹配，不以独立生命周期状态机驱动；**从配置集中移除**采用**逻辑删除**（行保留，默认查询与消费方匹配均排除），不设草稿 / 已发布流转。

```
  [ACTIVE 已启用] ──deactivateBinding──> [INACTIVE 已停用] ──activateBinding──> [ACTIVE]
         │                                      │
         └──────────── deleteBinding ───────────┘
                              │
                              v
               [已逻辑删除] is_deleted=true；status_enum 置 DISCARDED
               （不参与匹配与默认列表；无写侧恢复，需重新 createBinding）
```

#### 发布状态（`status_enum`，String，对齐 `PublicationStatus.getCode()`）

| code | display | 语义 |
| ---- | ------- | ---- |
| `ACTIVE` | 已启用 | 参与业务匹配；消费方 `listBindingsByRangeTypeAndSource` 仅返回此态 |
| `INACTIVE` | 已停用 | 不参与匹配；配置页可见，可启停、更新或逻辑删除 |
| `DISCARDED` | 已废弃 | 逻辑删除时由 `markDeleted` 写入；与 `is_deleted=true` 叠加，默认查询排除 |

> **逻辑删除与状态列分工**：移除绑定走 `deleteBinding` → `is_deleted=true` 且 `status_enum=DISCARDED`；临时不参与匹配优先 `deactivateBinding`（`INACTIVE`），勿与逻辑删混用。

---

## 3. 实体

```text
ServiceDeviceTemplate（聚合根，单表配置）
════════════════════════════════════════════════════════════

  ┌──────────────────────────────────────────────────────┐
  │  typeCode / itemTable+itemId / devActTable+devActId  │
  │  quantity / unit / scope / status                    │
  └──────────────────────────────────────────────────────┘
```

### 3.1 诊疗耗材绑定（ServiceDeviceTemplate）

ServiceDeviceTemplate 聚合根的属性定义，对应 `cus_service_device_template` 表（物理表名以本稿 **§6.1** 与数据库定稿为准）。

| 属性 | 字段名 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| 绑定标识 | `id` | `Long` | 是 | 主键，雪花 ID |
| 绑定范围 | `range_code` | `String` | 是 | 使用范围编码：`OUTPATIENT`（门诊）/ `INPATIENT`（住院）；历史存量可能为 `HOSPITAL`（全院），见 §4.1 |
| 绑定类型 | `type_code` | `String` | 是 | `USAGE` / `ACTIVITY`，见 §4.2 |
| 来源表名 | `item_table` | `String` | 是 | 来源侧物理表名（用法 / 诊疗对应表），用于溯源与联合查询 |
| 来源项标识 | `item_id` | `String` | 是 | 来源项键（字符串）：用法绑定存 **`method_code` 术语 code**（`item_table=ter_concept`）；诊疗绑定存 **`wkf_activity_definition.id` 字符串** |
| 目标表名 | `dev_act_table` | `String` | 是 | 绑定目标物理表名（诊疗定义表或耗材定义表） |
| 目标项标识 | `dev_act_id` | `Long` | 是 | 绑定目标主键（诊疗或耗材定义） |
| 绑定数量 | `quantity` | `BigDecimal` | 是 | 触发来源项时默认带出的目标数量 |
| 单位编码 | `unit_code` | `String` | 是 | 与单位字典（`unit` 术语集）一致 |
| 发布状态 | `status_enum` | `String` | 是 | `PublicationStatus` 的 `getCode()`：`ACTIVE` / `INACTIVE` / `DISCARDED` 等，见 §2.3 |

> 物理表 `cus_service_device_template`（Flyway V37）含审计与 `is_deleted` 软删字段；**无物理 DELETE**。

### 3.2 子实体

> 本聚合为单表聚合，无聚合内子实体。

---

## 4. 值对象（含枚举）

### 4.1 绑定范围（`ServiceDeviceTemplateRangeCode`）

| 枚举值 | code | 说明 |
|---|---|---|
| 门诊 | `OUTPATIENT` | 门诊医嘱开立匹配（IF-DOR-12 / IF-DOR-23）；WH-BND001 新建可选 |
| 住院 | `INPATIENT` | **仅护士执行**匹配（**BR-IP-EXE-013**，未合管）；**住院医生开立不读本表带出**（**BR-IP-ORD-036**）；WH-BND001 新建可选 |
| 全院(历史) | `HOSPITAL` | 历史兼容码；配置页不再作为新建选项；医嘱侧不再按此码匹配 |

> 枚举落点：`com.zxplat.domain.share.enums.ServiceDeviceTemplateRangeCode`。
> 同一来源项（用法/诊疗）可分别维护门诊、住院两套绑定。

### 4.2 绑定类型（`ServiceDeviceTemplateTypeCode`）

| 枚举值 | code | 说明 |
|---|---|---|
| 用法绑定 | `USAGE` | 来源为用法术语概念（`ter_concept`），目标为诊疗或耗材 |
| 诊疗绑定 | `ACTIVITY` | 来源为诊疗项目定义（`wkf_activity_definition`），目标为诊疗或耗材 |

> 枚举落点：`com.zxplat.domain.share.enums.ServiceDeviceTemplateTypeCode`。持久化与 HTTP 契约一律使用 **String code**，禁止数字 `1/2` 及已废弃的 `SLOT`/`3`。

### 4.3 发布状态（配置行，`PublicationStatus`）

WH-BND001 配置页启停与消费方匹配共用 `PublicationStatus`，落库为 **String code**：

| 配置语义 | code | 说明 |
|---|---|---|
| 启用 | `ACTIVE` | 绑定参与业务匹配 |
| 停用 | `INACTIVE` | 绑定不参与匹配；配置页仍可见 |
| 逻辑删除伴随态 | `DISCARDED` | `deleteBinding` 时与 `is_deleted=true` 一并写入 |

> 枚举落点：`com.zxplat.domain.share.enums.PublicationStatus`。

### 4.4 来源表标识（SourceTableRef）

由 `itemTable` + `itemId` 两个标量字段组合表达，定位绑定来源的具体业务记录。

| 属性 | 类型 | 说明 |
|---|---|---|
| 来源表名 | `String` | 用法 / 诊疗对应的物理表名 |
| 来源项标识 | `String` | 来源项在各自表中的键：`USAGE` 为 **`method_code`**；`ACTIVITY` 为活动定义主键字符串 |

来源表名须与绑定类型语义一致：

| 绑定类型 code | 来源表名（`TableNameConstants`） | 对应聚合 |
|---|---|---|
| `USAGE` | `ter_concept` | 用法术语（`method_code` 体系 **code**，非 concept 主键） |
| `ACTIVITY` | `wkf_activity_definition` | ActivityDefinition |

### 4.5 目标表标识（TargetTableRef）

由 `devActTable` + `devActId` 两个标量字段组合表达，定位绑定目标的具体业务记录。

| 属性 | 类型 | 说明 |
|---|---|---|
| 目标表名 | `String` | 诊疗定义表或耗材定义表 |
| 目标项标识 | `Long` | 目标项主键 |

目标表名字符串（与 **DM 主档**一致）：

| 落库表名 | 对应聚合 | 权威 DM |
|---|---|---|
| `wkf_activity_definition` | ActivityDefinition | `DM-WKF-001` §3.1 |
| `adm_device_definition` | DeviceDefinition | `DM-ADM-008` §3.1 |

### 4.6 术语表

| 名词 | 定义 |
|---|---|
| 绑定范围 | 使用范围；当前实现以固定范围值为主，具体含义由范围字典解释 |
| 绑定类型 | 用法绑定、诊疗绑定等，对应 `typeCode` |
| 来源项 | 左侧选中的用法 / 诊疗，由 `itemTable` + `itemId` 与 `typeCode` 联合表达 |
| 绑定目标 | 被绑定的诊疗或耗材定义，由 `devActTable` + `devActId` 表达 |
| 绑定数量 / 单位 | 触发来源项时默认带出的目标数量与计量单位 |
| 发布状态 | 启用则参与自动附加与匹配；停用则不参与 |

---

## 5. 领域事件

### 5.1 事件定义

| 编号 | 事件名称 | 触发时机 | 消费方 | 版本 |
|---|---|---|---|---|
| 5.1.1 | `ServiceDeviceTemplateCreatedEvent` | 新绑定行创建成功 | 医嘱/护理编排、缓存（可选） | v1 |
| 5.1.2 | `ServiceDeviceTemplateEditedEvent` | 已有绑定行更新成功 | 同上 | v1 |
| 5.1.3 | `ServiceDeviceTemplateActivatedEvent` | 绑定从停用切换为启用 | 同上 | v1 |
| 5.1.4 | `ServiceDeviceTemplateRetiredEvent` | 绑定从启用切换为停用 | 同上 | v1 |
| 5.1.5 | `ServiceDeviceTemplateDeletedEvent` | 绑定行**逻辑删除**成功（删除标记落库） | 同上 | v1 |

> 实现若统一为 `ServiceDeviceTemplateEditedEvent` 合并发布，以落库与 **§5** 事件清单为准。

### 5.2 事件触发规则

| 当前状态 | 操作 | 目标状态 | 发布事件 |
|---|---|---|---|
| — | 新增绑定（`createBinding`） | `ACTIVE` 或命令指定 `INACTIVE` | `ServiceDeviceTemplateCreatedEvent` |
| `ACTIVE` | 更新绑定（`editBinding`） | `ACTIVE` | `ServiceDeviceTemplateEditedEvent` |
| `INACTIVE` | 更新绑定（`editBinding`） | `INACTIVE` | `ServiceDeviceTemplateEditedEvent` |
| `INACTIVE` | 启用绑定（`activateBinding`） | `ACTIVE` | `ServiceDeviceTemplateActivatedEvent` |
| `ACTIVE` | 停用绑定（`deactivateBinding`） | `INACTIVE` | `ServiceDeviceTemplateRetiredEvent` |
| `ACTIVE` / `INACTIVE`（未删） | 逻辑删除（`deleteBinding`） | `is_deleted=true`，`status_enum=DISCARDED` | `ServiceDeviceTemplateDeletedEvent` |

---

## 6. 领域服务

### 6.1 写侧领域服务（ServiceDeviceTemplateDomainService）

> 写侧方法不使用 `@Transactional`，由应用层定界。

#### 6.1.1 新增绑定（`createBinding`）

- **业务意义**：插入一条新的「来源 → 目标」绑定及数量、单位、范围与初始发布状态，供医嘱、护理路径自动附加耗材。
- **边界说明**：仅 `INSERT` 新行，不承担「按主键覆盖更新」语义；BR-SDT-001～012（不含已废弃 BR-SDT-005）；BR-SDT-004 等同 `editBinding`。
- **入参**：绑定类型、来源表名、来源项标识、目标表名、目标项标识、数量、单位、范围、初始 `status_enum` 等（与 `CreateServiceDeviceTemplateCommand` 对齐，以本稿约定为准）。
- **出参**：新建诊疗耗材绑定标识。
- **前置条件**：必填完整性；来源项语义与类型一致（BR-SDT-002）；数量为正（BR-SDT-008）；目标项引用有效（BR-SDT-009）；行尚未存在同一业务自然键冲突（若产品定义自然键，以落库与交互契约为准）。
- **状态变化**：新建为 `ACTIVE` 或命令指定的 `INACTIVE`。
- **聚合内影响**：插入一行；逻辑删除标记为未删除。
- **事件输出**：`ServiceDeviceTemplateCreatedEvent`（若实现合并为 `ServiceDeviceTemplateEditedEvent`，以落库与 **§5** 为准）。
- **失败语义**：非法绑定类型、引用无效、必填缺失时拒绝。

#### 6.1.2 更新绑定（`editBinding`）

- **业务意义**：在保留绑定主键前提下，调整数量、单位、范围、发布状态或来源/目标引用（若产品允许变更引用，以落库与交互契约为准），使配置与临床路径一致。
- **边界说明**：仅 `UPDATE` 已存在且**未逻辑删除**的行；不通过本方法「变相新增」；BR-SDT-012；不对来源+目标做强唯一时仍允许多条并存（BR-SDT-004）。
- **入参**：诊疗耗材绑定标识；待更新字段子集（与 `EditServiceDeviceTemplateCommand` 对齐，以本稿约定为准）。
- **出参**：`—`（或返回乐观锁版本，以落库与交互契约为准）。
- **前置条件**：目标行存在且未逻辑删除；来源/目标语义与 BR-SDT-002/008/009 仍满足。
- **状态变化**：`ACTIVE`/`INACTIVE` 间迁移可通过本方法改 `status_enum` 字段，或仍走 `activateBinding`/`deactivateBinding`（以落库与交互契约为准）。
- **聚合内影响**：更新单行可编辑列；不插入新主键行。
- **事件输出**：`ServiceDeviceTemplateEditedEvent`。
- **失败语义**：行不存在、已逻辑删除、非法字段或并发冲突时拒绝。

#### 6.1.3 删除绑定（逻辑删）（`deleteBinding`）

- **业务意义**：从配置集中逻辑移除绑定，使消费方不再命中该条规则，同时保留审计与下游事件追溯。
- **边界说明**：BR-SDT-007；**不执行物理 DELETE**；已删除行默认不出现在列表与 `listBindingsByRangeTypeAndSource` 结果中（BR-SDT-013）。
- **入参**：诊疗耗材绑定标识。
- **出参**：`—`。
- **前置条件**：BR-SDT-012 行存在且未逻辑删除；幂等策略：已删可视为成功（以落库与交互契约为准）。
- **状态变化**：`is_deleted: false → true`；`status_enum` 置 `DISCARDED`（见实体 `markDeleted`）。
- **聚合内影响**：更新删除标记字段；不删行。
- **事件输出**：`ServiceDeviceTemplateDeletedEvent`。
- **失败语义**：标识不存在且非幂等策略时拒绝。

#### 6.1.4 启用绑定（`activateBinding`）

- **业务意义**：将绑定重新纳入自动附加候选集合（BR-SDT-003）。
- **边界说明**：不修改来源/目标身份键；不写消费方业务单据。
- **入参**：诊疗耗材绑定标识。
- **出参**：`—`。
- **前置条件**：行存在且**未逻辑删除**；当前为 `INACTIVE`（或可启用态，以落库与交互契约为准）。
- **状态变化**：`INACTIVE → ACTIVE`。
- **聚合内影响**：更新状态字段。
- **事件输出**：`ServiceDeviceTemplateActivatedEvent`。
- **失败语义**：非法状态迁移时拒绝。

#### 6.1.5 停用绑定（`deactivateBinding`）

- **业务意义**：暂停绑定参与自动附加，保留行以便审计与快速恢复（BR-SDT-006）。
- **边界说明**：停用行不得被消费方默认查询返回（BR-SDT-006）。
- **入参**：诊疗耗材绑定标识。
- **出参**：`—`。
- **前置条件**：行存在且**未逻辑删除**、当前为 `ACTIVE`。
- **状态变化**：`ACTIVE → INACTIVE`。
- **聚合内影响**：更新状态字段。
- **事件输出**：`ServiceDeviceTemplateRetiredEvent`。
- **失败语义**：重复停用、状态非法时拒绝。

### 6.2 读侧查询服务（ServiceDeviceTemplateQueryService）

#### 6.2.1 按来源查启用绑定（`listBindingsByRangeTypeAndSource`）

- **业务意义**：消费方在医嘱/护理执行时按来源键拉取当前生效的耗材附加规则。
- **边界说明**：只读；BR-SDT-003 仅 `ACTIVE`；BR-SDT-006 排除 `INACTIVE`；BR-SDT-013 已逻辑删除行排除。
- **查询范围（读库，无写入）**：诊疗耗材绑定表。
- **过滤口径**：范围 + 类型 + 来源表 + 来源项；`status_enum=ACTIVE`；未逻辑删除。
- **组装规则**：列表可多条（数量/单位不同）；排序以落库与交互契约为准。
- **返回约束**：不含来源/目标展示名 join（`BR-SDT-QRY-004` 由应用层补）。

#### 6.2.2 分页绑定列表（`pageBindingsBySource`）

- **业务意义**：配置页分页查看含停用在内的绑定，便于运维核对 WH-BND001。
- **边界说明**：只读；可返回 `INACTIVE` 行；**默认排除**已逻辑删除行（BR-SDT-013）；可选按 `status_enum` 筛选（`ACTIVE`/`INACTIVE`）。
- **查询范围（读库，无写入）**：绑定表 + 分页参数。
- **过滤口径**：来源维度（`range_code`+`type_code`+`item_table`+`item_id`）+ 可选状态 + 未逻辑删除（默认）。
- **组装规则**：分页 DTO；含状态列便于启停操作入口。
- **返回约束**：不触发写侧；大数据量注意索引与分页深度。

#### 6.2.3 按范围与类型分页（`pageBindingsByRangeAndType` / `pageBindingsByRangeAndTypeAndStatus`）

- **业务意义**：未选具体来源项时，按 Tab 类型浏览该类型下全部绑定（运维/对账）。
- **边界说明**：只读；默认排除已逻辑删除行；后者带 `status_enum` 可选筛选。

#### 6.2.4 按主键查询（`findById`）

- **业务意义**：编辑回显单条绑定完整字段。
- **边界说明**：只读；可返回已停用行供只读展示；**已逻辑删除**行默认视为不存在（返回空或业务异常，以落库与交互契约为准）。
- **查询范围（读库，无写入）**：主键定位单行。
- **过滤口径**：无租户则全库键唯一；多租户带租户条件；默认 `未逻辑删除`。
- **组装规则**：领域实体或编辑 DTO。
- **返回约束**：不存在时返回空或抛业务异常（以落库与交互契约为准）。

### 6.3 领域边界说明

- 绑定展示名称 join 业务表由应用层组装（`BR-SDT-QRY-004`）；配置列表直接查写表（`BR-SDT-QRY-001`～`003`）。
- 写编排由 `ServiceDeviceTemplateDomainService` 承担；读编排由 `ServiceDeviceTemplateQueryService` 承担。
- **新增与更新分离**：`createBinding` 仅插入；`editBinding` 仅更新未删行；**移除**一律走 `deleteBinding` 逻辑删，不提供物理删除契约。

---

## 7. 不变式

| 编号 | 不变式 | 约束/落地要点 |
|---|---|---|
| `BR-SDT-001` | `createBinding` / `editBinding` 提交前须具备：绑定类型、来源表名、来源项标识、目标表名、目标项标识、数量、单位、发布状态 | 必填完整性约束 |
| `BR-SDT-002` | 来源项语义须与绑定类型一致（用法对应用法表，诊疗对应诊疗定义表） | 语义一致性约束 |
| `BR-SDT-003` | 仅 `status_enum=ACTIVE` 的绑定参与医嘱开立、护理执行等自动附加逻辑 | 业务匹配约束 |
| `BR-SDT-004` | 不对「来源 + 目标 + 类型」做强唯一约束，允许同一组合多条（如仅数量 / 状态不同） | 业务灵活性（可选产品层提示重复） |
| `BR-SDT-006` | `status_enum=INACTIVE` 的绑定不得被消费方自动附加逻辑返回 | 查询过滤约束 |
| `BR-SDT-007` | 删除均为**逻辑删除**：置删除标记，不执行物理 `DELETE`；历史追溯依赖行保留、审计字段与下游已消费事件 | 删除策略约束 |
| `BR-SDT-008` | 绑定数量须为正数（`quantity > 0`） | 数据合法性约束 |
| `BR-SDT-009` | 目标项标识须引用有效的诊疗定义或耗材定义 | 引用完整性约束 |
| `BR-SDT-010` | 单位编码须属于目标项的合法单位范围 | 单位合法性约束 |
| `BR-SDT-011` | 保存须显式指定 `OUTPATIENT` 或 `INPATIENT`；禁止新建/改存为 `HOSPITAL`；领域层对未传范围码仍兜底 `HOSPITAL` 仅兼容旧调用 | 范围码约定 |
| `BR-SDT-012` | 操作的绑定记录必须存在（按 ID 查找）且**未逻辑删除**（启停、更新、`deleteBinding` 前断言） | 存在性约束 |
| `BR-SDT-013` | 已逻辑删除行（`is_deleted=true`）不参与默认列表、分页与消费方 `listBindingsByRangeTypeAndSource` 命中 | 查询默认带「未删除」条件，与 `INACTIVE` 过滤叠加 |
| `BR-SDT-014` | WH-BND001 绑定目标（诊疗/耗材）须用目录分页 SelectTable 实时检索**启用态**档案；禁止仅用 OptionDto 全量下拉（`busNo+名称` 单行）作为选品主路径 | 配置页选品可读性与目录一致 |

### 7.1 子实体规则

> 本聚合为单表聚合，无聚合内子实体行。

---

## 8. 上下文映射

### 8.1 上游依赖

| 上游上下文 | 聚合 | 关联方式 | 说明 |
|---|---|---|---|
| 基础数据 · 诊疗项目目录 | ActivityDefinition | `来源表名 + 来源项标识` / `目标表名 + 目标项标识` | 作为绑定来源或绑定目标 |
| 基础数据 · 耗材定义 | DeviceDefinition | `目标表名 + 目标项标识` | 作为绑定目标 |
| 产品目录 BatchLoader | `MedicationDefinitionQueryService` / `DeviceDefinitionQueryService` 等 | 应用编排 | 解析目标默认单位与候选单位（**非** ProductDefinition RM） |

### 8.2 下游影响

| 下游上下文 | 聚合 | 触发方式 | 说明 |
|---|---|---|---|
| 医嘱开立 | ServiceRequest | 查询 | **门诊**开立按诊疗绑定自动附加；**住院开立恒不带**（**BR-IP-ORD-036**）；未合管由护士执行读本表 |
| 护理执行 | ServiceRequest / DeviceRequest | 查询 | 住院护士执行列表挂载 `range_code=INPATIENT` 未合管模板（**BR-IP-EXE-013**）；已合管空 |
| 护理执行 | Procedure | 查询 | 护理执行时按用法绑定自动附加耗材或诊疗 |
| 收费明细 | ChargeItem | 间接引用 | 绑定触发的目标项产生对应收费明细 |
| QueryService / 缓存 | — | 事件订阅 | 监听 `ServiceDeviceTemplate*Event`，刷新绑定缓存 |

### 8.3 引用规则

| 模型名称 | 关系类型 | 说明 |
|---|---|---|
| `DM-WKF-001 ActivityDefinition` | 关联 | 来源/目标可为诊疗定义 |
| `DM-ADM-008 DeviceDefinition` | 关联 | 目标可为耗材定义 |
| `DM-CUS-006 ServiceLocationTemplate` | 并列 | 取药科室与绑定关系分列配置 |


### 8.4 事件流示意图

```
ActivityDefinition      ServiceDeviceTemplate      ServiceRequest
       │                         │                       │
       │── Published ───────────>│                       │
DeviceDefinition ───────────────>│                       │
       │                         │<── 查询活跃绑定 ────────│
       │                         │── EditedEvent ────>   │ (缓存失效)
```

> 状态迁移矩阵见 **§5.2**。

---

## 9. 补充

### 9.1 QueryService与查询规则

| 编号 | 规则 ID | 规则描述 | 约束 / 落地要点 |
|---|---|---|---|
| R-QRY-1 | `BR-SDT-QRY-001` | 配置列表直接查写表 | 本版本不强制独立QueryService表 |
| R-QRY-2 | `BR-SDT-QRY-002` | 保存 / **逻辑删除**后列表立即可见（未删行与删行过滤口径一致） | 读写同一表 |
| R-QRY-3 | `BR-SDT-QRY-003` | 高频消费方可短期缓存 | 事件失效缓存 |
| R-QRY-4 | `BR-SDT-QRY-004` | 展示名称 join 业务表 | 应用层组装 |

### 9.2 消费方查询流程

```
1. 确定来源项及表名 → 2. listBindingsByRangeTypeAndSource → 3. 返回启用绑定列表
4. 应用层解析目标展示名与单位 → 5. 消费方按列表附加单据
```

**门诊医生站 IF-DOR-23（暂存前绑定解析）**：

| 步骤 | 说明 |
| --- | --- |
| 1 | 前端以与 **IF-DOR-14 同构**的 `lines`（**暂存变更行**）调用应用读接口 |
| 2 | 应用层跳过 `DELETE` 行；药品行以 `method_code`→`USAGE`；诊疗行以 `activityDefinitionId`→`ACTIVITY` |
| 3 | 调用 `ServiceDeviceTemplateQueryService#listBindingsByRangeTypeAndSource`（**仅直接模板**，**禁止**用法→诊疗→耗材级联） |
| 4 | 数量取模板默认 `quantity`（**不乘**主医嘱数量）；**服务端不去重** |
| 5 | 响应回填触发源（如 `triggerLineIndex`）及派生行 `based_on` 锚点；前端确认、去重后再 **IF-DOR-14** 落库 |

> 契约细节见 **AS-OP-DOR-003 · IF-DOR-23 / BR-ORD-029** 与 **AS-OP-DOR-003-SDD §3.4.1**。

**住院（与门诊分轨）**：`resolveInpatientDoctorStationAdviceDraftBinds` / `listDraftBindItems` **恒空**（**BR-IP-ORD-036**）；未合管 `range_code=INPATIENT` 模板由护士执行 **BR-IP-EXE-013** 挂载；已合管检验开立与执行均不带（**AS-BAS-CFG-001 §7.7**）。

### 9.3 与 ServiceLocationTemplate 的边界

| 维度 | ServiceDeviceTemplate | ServiceLocationTemplate |
|---|---|---|
| 配置对象 | 来源与目标绑定 | 开立科室与发药位置 |
| 删除策略 | **逻辑删除**（`deleteBinding`） | 整科覆盖（先删后插，仍属本表行级策略） |

### 9.4 逻辑删除与 WH-BND001

- **逻辑删除**不可在写侧直接「恢复行」；需重新 `createBinding` 或走数据修复流程；临时不参与匹配请优先用 `deactivateBinding`（`INACTIVE`）。
- **WH-BND001（诊疗耗材绑定）**：
  - 菜单路由：`/basic/servicedevicebinding`；权限 `Navigation.ServiceDeviceBinding`。
  - 两 Tab：`USAGE` 用法 / `ACTIVITY` 诊疗；左侧选来源项，右侧维护绑定表。
  - 用法/诊疗 Tab 均可选诊疗或耗材（`wkf_activity_definition` / `adm_device_definition`）。
  - 保存走 `createBinding`/`editBinding`；移除走 `deleteBinding`（逻辑删）。列表展示名由应用层 enrich（`BR-SDT-QRY-004`）。
  - **绑定目标选品（对齐合管配置 SelectTable）**：
    - 前端 `servicedevicebinding/index.vue` 绑定目标列使用 `SelectTable` 宽面板多列选品，**禁止**以 `ElOption` 拼接 `busNo+名称` 作为诊疗/耗材选品主路径。
    - **诊疗目标**（`wkf_activity_definition`）：`pageServiceCatalog` 分页检索，列与合管检验项目选品同构（编码、名称、分类、单位、售价、规格）；仅启用态；`includeReferencedPackageChildren=true`；主键以字符串传递（Snowflake）。
    - **耗材目标**（`adm_device_definition`）：`pageDeviceCatalog` 分页检索，列与合管耗材选品同构（编码、名称、规格、包装单位、最小单位、售价、厂家）；仅启用态。
    - 选中后写 `devActId`/`devActName`，并调用 `unitOptionsByTarget` 刷新单位（`BR-SDT-010`）。
    - **KeepAlive**：`onActivated` 仍重调 `init` 以刷新左侧用法/诊疗来源项；**不再**依赖 `init.deviceDefOptions` 或 `listDeviceDefOptions` 作为绑定目标候选。
    - `init.deviceDefOptions` / `listDeviceDefOptions` 仍可由应用层返回（兼容旧调用），**WH-BND001 选品主路径不再消费**（**BR-SDT-014**）。
  - **单位下拉**：保存前 `unitOptionsByTarget`（`BR-SDT-010`）。

### 9.5 来源项标识类型说明

`itemId` 为 `String`，因来源跨多表主键类型不一；须与 `item_table`、`type_code` 联合解析。

---

*领域模型负责人：[负责人] | 代码评审标准：见 `07-DDD实践守护/领域模型评审清单.md`*

> **关联文档：**
>
> - 诊疗项目定义 → `DM-WKF-001-ActivityDefinition-诊疗项目定义.md`
> - 耗材定义 → `DM-ADM-008-DeviceDefinition-器材定义.md`
> - 取药科室 → `DM-CUS-006-ServiceLocationTemplate-取药科室.md`

*最后更新：2026-10-06 *

---


### 9.6 变更历史

| 版本 | 日期 | 摘要 |
| --- | --- | --- |
| v1.0.7 | 2026-10-06 | **WH-BND001 绑定目标选品**：§9.4 / **BR-SDT-014** 改为合管同款目录分页 `SelectTable`（诊疗 `pageServiceCatalog`、耗材 `pageDeviceCatalog`）；不再以 `deviceDefOptions`/`listDeviceDefOptions` 为选品主路径 |
| v1.0.6 | 2026-09-22 | **住院消费方纠偏**：`INPATIENT` 仅护士执行挂载（**BR-IP-EXE-013**）；住院开立恒不带（**BR-IP-ORD-036**）；§1.3/§4.1/§8.2 同步 |
| v1.0.5 | 2026-06-24 | **WH-BND001 耗材下拉实时读库**：§9.4 增补 `listDeviceDefOptions` 与前端 KeepAlive/`onActivated` 刷新约束；修复绑定页仅依赖 `init` 快照导致新增耗材不可选 |
| v1.0.4 | 2026-06-23 | **IF-DOR-23 消费方**：§1.3 增补门诊暂存前绑定解析；§9.2 补充 IF-DOR-23 五步读路径（直接模板、不级联、不去重、暂存变更行用语） |
| v1.0.3 | 2026-06-05 | **M3**：移除号源绑定（`SLOT`）类型与 BR-SDT-005；绑定类型仅 `USAGE`/`ACTIVITY`；WH-BND001 两 Tab；Flyway V96 逻辑删存量 SLOT 行 |
| v1.0.2 | 2026-06-05 | **M2**：§4 绑定类型/范围/发布状态对齐 String code（`USAGE`/`ACTIVITY`/`SLOT`、`HOSPITAL`、`ACTIVE`/`INACTIVE`/`DISCARDED`）；§2.3 状态机与逻辑删语义；§6.2 补充分页读方法；§9.4 WH-BND001 路由与 Tab 约束；修正文档编码为 DM-CUS-005 |
| v1.0.1 | 2026-05-28 | **M1**：§6.1/§6.2 与 `ServiceDeviceTemplateDomainService`/`ServiceDeviceTemplateQueryService` 白名单对齐（5写+5读） |
| v1.0 | — | 初版 |

<!-- MODE-B-METHOD-INVENTORY 2026-09-23 -->

## 附录 · 模式 B 已实现方法清单（2026-09-23）

> 自 zxplat-domain 对应 *DomainService / *QueryService 抽取 public 方法名，用于关闭方法白名单 DOC-DRIFT。细规仍以正文 §6 与源码为准；本表不替代九章成稿。

### DomainService：`ServiceDeviceTemplateDomainService`

| 方法 |
|------|
| `createBinding` |
| `editBinding` |
| `deleteBinding` |
| `activateBinding` |
| `deactivateBinding` |

### QueryService：`ServiceDeviceTemplateQueryService`

| 方法 |
|------|
| `listBindingsByRangeTypeAndSource` |
| `pageBindingsBySource` |
| `pageBindingsBySourceAndStatus` |
| `pageBindingsByRangeAndType` |
| `pageBindingsByRangeAndTypeAndStatus` |
| `findById` |

