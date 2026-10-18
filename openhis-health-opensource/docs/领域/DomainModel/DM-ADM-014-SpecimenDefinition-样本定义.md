# DM-ADM-014 SpecimenDefinition 样本定义

> **限界上下文**：管理域 · 检验标本目录主数据  
> **聚合类型**：实体聚合（单表）  
> **版本**：v1.2.1 · **日期**：2026-08-02

---

## 1. 概述

### 1.1 业务定义

样本定义（SpecimenDefinition）用于维护院内**检验标本类型**目录主数据，承载业务编码、名称、标本种类、检索码及发布状态，供诊疗项目定义、检验医嘱开立、采血条码打印等下游业务引用。

### 1.2 核心职责

| 职责 | 说明 |
|---|---|
| 目录主数据维护 | 维护标本类型基础档案及检索字段 |
| 发布状态管理 | 通过 `status_enum` 管理启用/停用（`PublicationStatus`） |
| 检索字段支持 | 提供拼音码、五笔码，支撑维护页与选品快速检索 |
| 下游引用锚点 | 供 `ActivityDefinition.specimen_code` 等业务通过编码或主键引用 |

### 1.3 适用范围

| 类型 | 说明 |
|---|---|
| 基础资料维护 | 样本定义新增、编辑、删除、批量启用/停用 |
| 诊疗目录配置 | 检验类诊疗项目绑定默认标本类型 |
| 临床开立与打印 | 医生站/护士站展示标本名称；采血条码等打印场景消费标本类型名称 |

### 1.4 与 ActivityDefinition 的关系

- **ActivityDefinition**（`DM-WKF-001`）通过字段 `specimen_code` 引用本聚合的 **`bus_no`**（业务编码），表达「该检验项目要求的标本类型」。
- 本聚合只维护标本目录主数据，不承载采样、送检、检验执行等流程事实。

---

## 2. 聚合根与持久化形态

### 2.1 持久化形态

**持久化形态**：单表

聚合根对应单表，聚合边界以主表主键 `id`（样本定义标识）划分。

**表结构**：
- 主表：`adm_specimen_definition`（主键：`id`）
- 索引：`uk_adm_specimen_definition_bus_no`（`bus_no` 在未删记录上唯一）、`idx_adm_specimen_definition_type_enum`、`idx_adm_specimen_definition_status_enum`

**高频查询模式**：
- 按样本定义标识查询：主表主键查询
- 按业务编码查询：`bus_no` 唯一索引
- 按标本种类筛选：`type_enum` 索引扫描
- 按状态筛选：`status_enum` 索引扫描
- 关键字检索：`name`、`bus_no`、`py_str`、`wb_str` 模糊匹配

### 2.2 聚合根标识

| 属性 | 类型 | 说明 |
|---|---|---|
| 样本定义标识 | `Long` | 主键，雪花 ID |

### 2.3 状态机

```
  [ACTIVE] ──停用──> [INACTIVE]
       ^                  │
       └──启用────────────┘
```

#### 状态定义

| code | info | 语义 |
|---|---|---|
| `ACTIVE` | 已启用 | 可用于新增业务引用与目录绑定 |
| `INACTIVE` | 已停用 | 不再用于新增引用，历史引用保持可追溯 |

> **状态枚举说明**：持久化字段 `status_enum` 使用共享枚举 **`PublicationStatus`**（**String 存储，存枚举 `code`**）。目录管理主路径仅使用 **`ACTIVE` / `INACTIVE`** 两态；新建默认 **`ACTIVE`**。`DRAFT`、`DISCARDED` 保留给兼容与逻辑删除场景，维护页主路径不暴露。

---

## 3. 实体

本聚合为单表聚合，无子实体。

### 3.1 样本定义（SpecimenDefinition）

SpecimenDefinition 聚合根的属性定义，对应 `adm_specimen_definition` 表。

| 属性 | 字段名 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| 样本定义标识 | `id` | `Long` | 是 | 主键，雪花 ID |
| 业务编码 | `bus_no` | `String` | 是 | 院内唯一业务编码；下游 `ActivityDefinition.specimen_code` 引用本字段 |
| 样本名称 | `name` | `String` | 是 | 面向临床与维护页的显示名称 |
| 拼音码 | `py_str` | `String` | 否 | 名称拼音检索码 |
| 五笔码 | `wb_str` | `String` | 否 | 名称五笔检索码 |
| 标本种类 | `type_enum` | `String` | 是 | 存 `SpecimenType.getCode()`，见 §4.1 |
| 发布状态 | `status_enum` | `String` | 是 | 存 `PublicationStatus.getCode()` |

> **审计字段**：继承自 `BaseAuditedEntity`（`createdAt` / `updatedAt` / `createdBy` / `updatedBy` / `deletedAt` / `deletedBy` / 租户 / `isDeleted` 等），不在属性表中重复列出。

---

## 4. 枚举

### 4.1 标本种类（SpecimenType）

共享枚举，标识标本的生物学/采集类别，供维护页下拉与列表筛选。

| code | info | 说明 |
|---|---|---|
| `BLOOD` | 血液样本 | 全血、血清、血浆等 |
| `URINE` | 尿液样本 | 晨尿、随机尿等 |
| `SALIVA` | 唾液样本 | 唾液采集 |
| `TISSUE` | 组织样本 | 活检、病理组织等 |
| `CSF` | 脑脊液样本 | 腰椎穿刺脑脊液 |
| `SPUTUM` | 痰液样本 | 呼吸道分泌物 |
| `FECES` | 粪便样本 | 大便标本 |
| `SWAB` | 拭子样本 | 咽拭子、鼻拭子等 |
| `BONE_MARROW` | 骨髓样本 | 骨髓穿刺标本 |
| `SEMEN` | 精液样本 | 精液分析 |
| `AMNIOTIC_FLUID` | 羊水样本 | 产前诊断 |
| `HAIR` | 毛发样本 | 毛发检测 |
| `NAIL` | 指甲样本 | 指甲检测 |
| `OTHER` | 其他 | 未列明种类 |

> **存储口径**：对应主表字段 `type_enum`，使用**字符串编码存储（存枚举 `code`）**；与 `status_enum` 同属 `*_enum` 列，**不**使用术语码表。

### 4.2 发布状态（PublicationStatus）

| code | info | 说明 |
|---|---|---|
| `DRAFT` | 草稿 | 兼容态，维护页主路径不使用 |
| `ACTIVE` | 已启用 | 目录可用 |
| `INACTIVE` | 已停用 | 目录停用 |
| `DISCARDED` | 已废弃 | 逻辑删除兼容态 |

> **存储口径**：对应主表字段 `status_enum`，使用**字符串编码存储（存枚举 `code`）**。启停主路径使用 `ACTIVE` ↔ `INACTIVE`。

---

## 5. 领域事件

### 5.1 事件定义

| 事件名称 | 英文名称 | 触发时机 | 携带数据 | 消费方 |
|---|---|---|---|---|
| 样本定义已创建 | `SpecimenDefinitionCreatedEvent` | 新增目录成功后 | `id`、`busNo`、`name`、`typeEnum`、`statusEnum` | QueryService / 缓存（若启用） |
| 样本定义已更新 | `SpecimenDefinitionUpdatedEvent` | 编辑目录成功后 | `id`、`changedFields` | QueryService / 缓存（若启用） |
| 样本定义已删除 | `SpecimenDefinitionDeletedEvent` | 逻辑删除成功后 | `id` | QueryService / 缓存（若启用） |
| 样本定义已启用 | `SpecimenDefinitionActivatedEvent` | 批量启用成功后 | `id`、`statusEnum` | 目录候选过滤 |
| 样本定义已停用 | `SpecimenDefinitionDeactivatedEvent` | 批量停用成功后 | `id`、`statusEnum` | 目录候选过滤 |

### 5.2 事件触发规则

| 当前状态 | 操作 | 目标状态 | 发布事件 |
|---|---|---|---|
| — | 新建样本定义 | `ACTIVE` | `SpecimenDefinitionCreatedEvent` |
| `ACTIVE` / `INACTIVE` | 维护目录信息 | 原状态保持 | `SpecimenDefinitionUpdatedEvent` |
| `ACTIVE` | 批量停用 | `INACTIVE` | `SpecimenDefinitionDeactivatedEvent` |
| `INACTIVE` | 批量启用 | `ACTIVE` | `SpecimenDefinitionActivatedEvent` |
| 任意未删 | 逻辑删除 | 已删除（`isDeleted`） | `SpecimenDefinitionDeletedEvent` |

---

## 6. 领域服务

### 6.1 写侧领域服务（SpecimenDefinitionDomainService）

#### 6.1.1 新建样本定义（`createSpecimenDefinition`）

- **业务意义**：创建可被诊疗目录与临床流程引用的标本类型主档。
- **边界说明**：只维护本聚合主数据，不联动修改 `ActivityDefinition`。
- **前置条件**：`busNo` 唯一（`BR-SPDEF-001`）；`name`、`typeEnum` 必填。
- **状态变化**：初始化为 `ACTIVE`。
- **聚合内影响**：插入 `adm_specimen_definition`；按名称生成 `pyStr`、`wbStr`（`BR-SPDEF-002`）。
- **事件输出**：`SpecimenDefinitionCreatedEvent`。

#### 6.1.2 维护样本定义（`updateSpecimenDefinition`）

- **业务意义**：修正既有标本目录的业务属性。
- **边界说明**：仅更新本表字段；不级联改写下游已落库引用。
- **前置条件**：目标记录存在且未逻辑删除；`busNo` 变更时仍满足唯一性（`BR-SPDEF-001`）。
- **状态变化**：无强制状态迁移（`N/A`）。
- **聚合内影响**：按主键更新目录字段；名称变更时重算检索码（`BR-SPDEF-002`）。
- **事件输出**：`SpecimenDefinitionUpdatedEvent`。

#### 6.1.3 删除样本定义（`deleteSpecimenDefinition`）

- **业务意义**：将不再使用的标本目录从可用集合移除，保留审计追溯。
- **边界说明**：逻辑删除，不物理抹除，写入 `deletedBy` / `deletedAt`；**当前阶段**应用层删除不校验 `ActivityDefinition` 下游引用（`BR-SPDEF-004` 预留后续接入）。
- **前置条件**：目标记录存在且未删除。
- **状态变化**：`N/A`（以 `isDeleted` 表达失效）。
- **聚合内影响**：标记逻辑删除。
- **事件输出**：`SpecimenDefinitionDeletedEvent`。

#### 6.1.4 批量停用样本定义（`deactivateSpecimenDefinitions`）

- **业务意义**：将目录移出新业务可选集合。
- **边界说明**：只改发布状态，不删除历史事实。
- **前置条件**：目录存在且当前为 `ACTIVE`。
- **状态变化**：`ACTIVE -> INACTIVE`（批量）。
- **聚合内影响**：批量更新 `status_enum`。
- **事件输出**：`SpecimenDefinitionDeactivatedEvent`（逐条）。

#### 6.1.5 批量启用样本定义（`activateSpecimenDefinitions`）

- **业务意义**：恢复停用目录可被新业务引用。
- **边界说明**：不自动补建缺失的下游绑定。
- **前置条件**：目录当前为 `INACTIVE` 且未逻辑删除。
- **状态变化**：`INACTIVE -> ACTIVE`（批量）。
- **聚合内影响**：批量更新 `status_enum`。
- **事件输出**：`SpecimenDefinitionActivatedEvent`（逐条）。

### 6.2 读侧查询服务（SpecimenDefinitionQueryService）

#### 6.2.1 样本定义分页查询（`pageSpecimenDefinition`）

- **业务意义**：按筛选条件分页获取标本目录列表，供维护页展示。
- **边界说明**：只读查询，不写库。
- **查询范围（读库，无写入）**：`adm_specimen_definition` 主表。
- **过滤口径**：默认排除逻辑删除；可按 `statusEnum`、`typeEnum`、关键字（`name`/`bus_no`/`py_str`/`wb_str`）组合过滤。
- **组装规则**：按 `updated_at` 降序、主键降序稳定排序。
- **返回约束**：标准分页结构；空页返回空列表。

#### 6.2.2 样本定义详情查询（`getSpecimenDefinition`）

- **业务意义**：读取单条目录详情供查看、编辑回显与写前断言。
- **边界说明**：只读查询。
- **查询范围（读库，无写入）**：按 `id` 查询主档。
- **返回约束**：不存在或已删返回 `null`；应用层 `get` 转为 `EntityNotFoundException`。

#### 6.2.3 关键字检索（`searchByKeyWord`）

- **业务意义**：按名称/编码/拼音/五笔快速检索标本目录，供下拉选品与维护页检索。
- **边界说明**：只读查询。
- **过滤口径**：默认仅返回 `ACTIVE` 且未删记录；调用方可放宽状态条件。
- **返回约束**：无命中返回空集合；投影含 **`pyStr`/`wbStr`**。
- **消费方（IF-DOR-18 / IF-IP-DOR-18）**：医生站 init 无关键字拉取启用样本上限（实现为 500），映射为带 **`pyStr`/`wbStr`** 的 `OptionDto`；申请单检验行前端再按名称/编码/拼音/五笔做客户端模糊过滤。

#### 6.2.4 按业务编码查询（`getByBusNo`）

- **业务意义**：按 `bus_no` 精确解析标本定义，供 `ActivityDefinition` 绑定校验与名称回填。
- **边界说明**：只读查询。
- **查询范围（读库，无写入）**：`bus_no` 唯一匹配。
- **返回约束**：不存在返回空。

#### 6.2.5 批量加载样本定义（`listByIds`）

- **业务意义**：按标识集合一次性加载目录实体，支撑批量写前校验或名称批量回填。
- **边界说明**：只读查询。
- **查询范围（读库，无写入）**：`id in (...)`，默认排除逻辑删除。
- **返回约束**：部分缺失时返回命中子集。

### 6.3 领域边界说明

- 本聚合维护「标本类型目录主数据」，不直接处理采样、送检、检验结果等执行事务。
- 与诊疗项目目录的绑定关系由 `ActivityDefinition` 侧持有 `specimen_code`；编码有效性校验与删除前引用阻断**当前未接入**，待诊疗项目绑定落地后由应用层编排。
- 采血条码、检验申请等下游仅通过 `bus_no` 或 `id` 引用本聚合，不在本聚合内回写下游状态。

---

## 7. 不变式（BR-SPDEF-*）

| 编号 | 规则 | 说明 |
|---|---|---|
| BR-SPDEF-001 | `busNo` 唯一 | 新增与编辑时按 `bus_no` 去重 |
| BR-SPDEF-002 | 编辑名称需重算检索码 | 更新时同步 `pyStr` / `wbStr` |
| BR-SPDEF-003 | 启停只改状态不删历史 | 停用后仍保留历史引用与审计 |
| BR-SPDEF-004 | 删除前下游引用校验（预留） | 领域删除仅逻辑删除；**当前实现**应用层不校验 `ActivityDefinition.specimen_code` 引用，后续接入绑定能力后再由应用层写前阻断或提示解绑 |
| BR-SPDEF-005 | 停用后限制新增引用 | `INACTIVE` 记录不得作为新增绑定的候选默认值 |

---

## 8. 上下文映射

### 8.1 上游依赖

| 上游 | 关联字段 | 说明 |
|---|---|---|
| 共享枚举 | `status_enum` | `PublicationStatus` |
| 共享枚举 | `type_enum` | `SpecimenType`（§4.1） |

### 8.2 下游影响

| 下游 | 触发方式 | 说明 |
|---|---|---|
| ActivityDefinition | 编码引用 | `specimen_code` 引用本聚合 `bus_no` |
| 采血条码打印 | 查询 | 按标本定义解析 `typeName` 等展示字段 |
| 检验医嘱开立 | 查询 | 开立/展示检验项目关联的标本类型名称 |

### 8.3 引用规则

- 下游创建或维护业务数据时通过 `specimen_code`（业务编码）或 `specimenDefinitionId`（主键）引用本聚合，不复制全量目录字段。
- 目录停用后应限制新增业务引用，历史单据保持可追溯。
- 逻辑删除后的记录不得出现在默认可选列表。

### 8.4 事件流示意图

```text
SpecimenType / PublicationStatus
        └──(编码口径)──> SpecimenDefinition
                              ├── SpecimenDefinitionCreatedEvent / UpdatedEvent / DeletedEvent
                              ├── SpecimenDefinitionActivatedEvent / DeactivatedEvent
                              └── 下游：ActivityDefinition / 采血条码 / 检验开立（查询引用）
```

---

## 9. 补充

### 9.1 变更历史

| 版本 | 日期 | 摘要 |
|---|---|---|
| v1.2.1 | 2026-08-02 | **§6.2.3**：明确 IF-DOR-18/IF-IP-DOR-18 init 透出 `pyStr`/`wbStr`，供申请单检验样本前端模糊过滤 |
| v1.2 | 2026-06-12 | 与 `zxplat` 成码对齐：审计基类 `BaseAuditedEntity`；`bus_no` 部分唯一索引；删除写 `operatorId` 审计；BR-SPDEF-004 标注当前阶段不校验诊疗项目引用；补充应用层落点索引 |
| v1.1 | 2026-06-12 | 标本种类字段由 `type_code` 更名为 `type_enum`，存 `SpecimenType` 枚举 code |
| v1.0 | 2026-06-12 | 初版：单表标本目录主数据；字段含 `bus_no`、`name`、`py_str`、`wb_str`、`type_enum`、`status_enum`；提供增删改查与批量启停 |

### 9.2 实现落点索引（与代码同步）

| 层次 | 包 / 类 | 说明 |
|---|---|---|
| Flyway | `V76__adm_specimen_definition.sql` | 表 `adm_specimen_definition` |
| 领域实体 | `com.zxplat.domain.administration.specimendefinition.entity.SpecimenDefinition` | 聚合根 |
| 写服务 | `...specimendefinition.service.SpecimenDefinitionDomainService` | 5 个写方法 |
| 读服务 | `...specimendefinition.service.SpecimenDefinitionQueryService` | 5 个读方法 |
| 共享枚举 | `com.zxplat.domain.share.enums.SpecimenType` | `type_enum` 合法值 |
| 领域事件 | `com.zxplat.domain.share.events.administration.specimendefinition.*` | 5 类事件 |
| 错误码 | `ErrorCodeConstants.SPECIMEN_DEFINITION_001`～`005` | 参数/不存在/状态/编码冲突/规则冲突 |
| 应用服务 | `com.zxplat.domain.application.basic.specimendefinition.service.SpecimenDefinitionAppService` | `init` / `page` / `get` / `create` / `update` / `delete` / `enable` / `disable` / `batchEnable` / `batchDisable` / `search` |
| 应用入参 | 单主键动作为 `Long id`（如 `deleteSpecimenDefinition`、`enableSpecimenDefinition`），**不使用** `IdValueRequest`；前端以字符串提交主键防精度丢失 |
| 应用出参 | `SpecimenDefinitionDto.id` 使用 `@JsonSerialize(ToStringSerializer.class)` | 响应主键 JSON 为 string |

<!-- MODE-B-METHOD-INVENTORY 2026-09-23 -->

## 附录 · 模式 B 已实现方法清单（2026-09-23）

> 自 zxplat-domain 对应 *DomainService / *QueryService 抽取 public 方法名，用于关闭方法白名单 DOC-DRIFT。细规仍以正文 §6 与源码为准；本表不替代九章成稿。

### DomainService：`SpecimenDefinitionDomainService`

| 方法 |
|------|
| `createSpecimenDefinition` |
| `updateSpecimenDefinition` |
| `deleteSpecimenDefinition` |
| `deactivateSpecimenDefinitions` |
| `activateSpecimenDefinitions` |

### QueryService：`SpecimenDefinitionQueryService`

| 方法 |
|------|
| `pageSpecimenDefinition` |
| `getSpecimenDefinition` |
| `searchByKeyWord` |
| `getByBusNo` |
| `listByIds` |

