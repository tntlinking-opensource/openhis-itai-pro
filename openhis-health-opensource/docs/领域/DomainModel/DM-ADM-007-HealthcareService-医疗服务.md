# DM-ADM-007 HealthcareService 医疗服务

> **限界上下文**：管理域 · 医疗服务管理
> **聚合类型**：实体聚合
> **版本**：v1.0.2 · **日期**：2026-08-22

## 1. 概述

### 1.1 业务定义

医疗服务（HealthcareService）是对医疗机构可提供的**具体服务能力的标准化描述**，如"内科普通门诊"、"超声检查"。它是 HIS 中**预约挂号、号源管理、排班、岗位可提供服务**等业务的基础数据锚点。

在临床业务中，医疗服务回答的核心问题是："**某个科室能提供什么服务？患者可以挂什么号？**"——它是号源创建、排班管理和预约挂号的底层支撑。

### 1.2 核心职责


| 职责      | 说明                                        |
| ------- | ----------------------------------------- |
| 服务能力描述  | 为机构可提供的每一项服务赋予唯一标识与标准化属性                  |
| 挂号类型管理  | 作为门诊挂号号源的分类基础（普通门诊、专家门诊、特需门诊、**复诊0元号**等）            |
| 科室服务关联  | 将服务能力绑定到提供科室（Organization）与服务地点（Location） |
| 排班号源锚点  | 作为 Schedule / Slot 排班体系的服务引用目标            |
| 诊查费定价联动 | 作为收费域 ChargeItemDefinition 诊查费定价的"引用锚点"   |


### 1.3 适用范围


| 类型   | 说明                     |
| ---- | ---------------------- |
| 门诊挂号 | 患者按服务类型选择挂号，如普通门诊、专家门诊 |
| 预约挂号 | 号源关联到具体医疗服务，如预约消化内科专家号 |
| 体检服务 | 健康体检对应的服务类型，如入职体检、年度体检 |
| 远程医疗 | 线上诊疗服务，如远程会诊、互联网门诊     |


### 1.4 与 Organization / Location 的关系

- **Organization（提供科室）**：每个医疗服务必须关联一个提供科室，表示"谁能提供这项服务"。通过 `提供科室标识(OrganizationId)` 引用。
- **Location（服务地点）**：医疗服务可选关联默认服务地点，表示"在哪里提供这项服务"。通过 `地点标识(LocationId)` 引用。
- 一个科室可提供多项医疗服务；一项医疗服务仅归属一个提供科室。

## 2. 聚合根与持久化形态

### 2.1 持久化形态

**持久化形态**：单表

聚合根对应单表，聚合边界以主表主键 `id`（医疗服务标识）划分。

**表结构**：

- 主表：`adm_healthcare_service`（主键：`id`）
- 索引：`offered_org_id`（提供科室）、`category_code`（服务类别）、`type_code`（服务类型）、`status_enum`（状态）、`active_flag`（是否启用）

**高频查询模式**：

- 按医疗服务标识查询：主表主键查询
- 按提供科室查询：`offered_org_id` 索引扫描
- 按服务类别筛选：`category_code` 索引扫描
- 按服务类型筛选：`type_code` 索引扫描
- 按状态筛选：`status_enum` 索引扫描

### 2.2 聚合根标识


| 属性     | 类型                    | 说明          |
| ------ | --------------------- | ----------- |
| 医疗服务标识 | `HealthcareServiceId` | 全局唯一标识，系统生成 |


### 2.3 状态机

```
  [DRAFT] ──发布──> [ACTIVE] ──停用──> [RETIRED] ──重新发布──> [ACTIVE]
    │                                   │
    └──废弃──> [DELETED]                  └──废弃──> [DELETED]
```

#### 状态定义


| value | code    | info     | 语义                         |
| ----- | ------- | -------- | -------------------------- |
| 0     | DRAFT   | 创建后的初始状态 | 可编辑全部属性，允许编辑、发布、废弃         |
| 1     | ACTIVE  | 已发布到生产   | 可被排班/号源/岗位引用，允许编辑（受限）、停用   |
| 2     | RETIRED | 暂时停用     | 不可被新业务选用，历史引用仍有效，允许重新发布、废弃 |
| 3     | DELETED | 逻辑删除     | 不可恢复                       |


> **状态枚举说明**：持久化字段 `status_enum`（`PublicationStatus` 枚举，**String 存储，存枚举 code**），`DRAFT / ACTIVE / RETIRED / DELETED` 四态，与定义类聚合（ActivityDefinition、ActivityExecutionLocation 等）保持一致。`active_flag`（Boolean）为派生字段，`status_enum == ACTIVE` 时为 `true`，其余为 `false`。

## 3. 实体

### 3.1 医疗服务（HealthcareService）

HealthcareService 聚合根的属性定义，对应 `adm_healthcare_service` 表。


| 属性     | 字段名                         | 类型                      | 必填  | 说明                     |
| ------ | --------------------------- | ----------------------- | --- | ---------------------- |
| 服务名称   | `name`                      | `String`                | 是   | 面向临床的显示名称，如"内科普通门诊"    |
| 服务类别   | `category_code`             | `ServiceCategory`（值对象）  | 是   | 见 §4.1，门诊/住院/体检/其他  |
| 服务类型   | `type_code`                 | `ServiceType`（值对象）      | 是   | 见 §4.2，普通门诊/专家门诊/特需门诊等 |
| 专科分类   | `specialty_code`            | `Specialty`（值对象）        | 否   | 见 §4.3，临床专科标识          |
| 提供科室标识 | `offered_org_id`            | `OrganizationId`        | 是   | 关联提供科室，                |
| 地点标识   | `location_id`               | `LocationId`            | 否   | 关联服务地点                 |
| 发布状态   | `status_enum`               | `PublicationStatus`（值对象）        | 是   | 生命周期状态（DRAFT/ACTIVE/RETIRED/DELETED），见 §2.3 |
| 是否启用   | `active_flag`               | `Boolean`                    | 是   | `status_enum == ACTIVE` 时为 `true`，其余为 `false` |
| 是否需要预约 | `appointment_required_flag` | `Boolean`               | 是   | 是否必须先预约才能挂号            |
| 医保编码   | `yb_no`                     | `String`                | 否   | 对应医保服务目录编码             |
| 业务编码   | `bus_no`                    | `String`                | 否   | 院内业务编码，用于收费对照          |
| 说明     | `comment`                   | `String`                | 否   | 服务补充描述                 |
| 额外详情   | `extra_details`             | `String`                | 否   | 面向患者的额外说明（如注意事项）       |
| 联系方式   | `contact`                   | `ContactInfo`（值对象）      | 否   | 见 §4.4，服务联系信息          |


> **审计字段**：继承自 `BaseAuditedEntity`（`createdAt` / `updatedAt` / `createdBy` / `updatedBy`），不在属性表中重复列出。

## 4. 值对象

### 4.1 服务类别（ServiceCategory）

对应 FHIR `category`，标识医疗服务的大类。


| 编码值 | 英文              | 说明         |
| --- | --------------- | ---------- |
| 1   | `OUTPATIENT`    | 门诊类服务      |
| 2   | `INPATIENT`     | 住院类服务      |
| 4   | `PHYSICAL_EXAM` | 健康体检类服务    |
| 5   | `TELEHEALTH`    | 互联网/远程诊疗服务 |
| 0   | `OTHER`         | 其他类型服务     |


> **存储口径**：对应主表字段 `category_code`，使用字符串编码存储。

### 4.2 服务类型（ServiceType）

对应 FHIR `type`，标识医疗服务的具体类型。


| 编码值 | 英文                      | 说明         |
| --- | ----------------------- | ---------- |
| 1   | `GENERAL_OUTPATIENT`    | 普通门诊号      |
| 2   | `EXPERT_OUTPATIENT`     | 专家门诊号      |
| 3   | `SPECIAL_OUTPATIENT`    | 特需/VIP 门诊号 |
| 5   | `RENOWNED_DOCTOR`       | 名医工作室门诊号   |
| 6   | `CONVENIENT_OUTPATIENT` | 简易门诊/便民门诊号 |
| 0   | `OTHER`                 | 其他类型       |


> **存储口径**：对应主表字段 `type_code`，使用字符串编码存储。

### 4.3 专科分类（Specialty）

对应 FHIR `specialty`，标识该服务所属的临床专科。


| 属性   | 类型       | 说明                |
| ---- | -------- | ----------------- |
| 专科编码 | `String` | 国家卫健委临床专科编码       |
| 专科名称 | `String` | 如"消化内科"、"骨科"、"儿科" |


> **存储口径**：对应主表字段 `specialty_code`，使用字符串存储。

### 4.4 联系信息（ContactInfo）

对应 FHIR `contact`，描述服务的联系方式。


| 属性   | 类型       | 说明        |
| ---- | -------- | --------- |
| 联系电话 | `String` | 科室/服务联系电话 |
| 联系地址 | `String` | 服务地址描述    |


> **存储口径**：对应主表字段 `contact`，使用字符串存储（可包含电话和地址的组合信息）。

### 4.5 可用时段口径（AvailabilityRule）

> 该项作为**业务字典/值对象口径**保留，不代表新增实体或新表。

用于表达医疗服务可开放的时段规则语义，当前由排班域（`Schedule/Slot`）承载具体时段数据，`HealthcareService` 侧不新增 `AvailableTime` 子实体。


| 属性   | 类型          | 说明          |
| ---- | ----------- | ----------- |
| 星期   | `DayOfWeek` | 周一至周日       |
| 是否全天 | `Boolean`   | true 表示全天开放 |
| 开始时间 | `LocalTime` | 非全天场景使用     |
| 结束时间 | `LocalTime` | 非全天场景使用     |


#### 4.5.1 星期枚举（DayOfWeek）


| 枚举值 | 英文          | 说明  |
| --- | ----------- | --- |
| 周一  | `monday`    | —   |
| 周二  | `tuesday`   | —   |
| 周三  | `wednesday` | —   |
| 周四  | `thursday`  | —   |
| 周五  | `friday`    | —   |
| 周六  | `saturday`  | —   |
| 周日  | `sunday`    | —   |


---

## 5. 领域事件

### 5.1 事件定义


| 事件名称      | 英文名称                                | 触发时机     | 携带数据                             | 消费方          |
| --------- | ----------------------------------- | -------- | -------------------------------- | ------------ |
| 医疗服务已创建   | `HealthcareServiceCreatedEvent`     | 创建成功     | 医疗服务标识, 服务名称, 服务类别, 服务类型, 提供科室标识 | QueryService/缓存       |
| 医疗服务已发布   | `HealthcareServicePublishedEvent`   | 从草稿发布到生产 | 医疗服务标识, 服务名称                     | 排班/号源、定价联动   |
| 医疗服务已修改   | `HealthcareServiceModifiedEvent`    | 属性被修改    | 医疗服务标识, 修改字段列表                   | QueryService/缓存、定价联动  |
| 医疗服务已停用   | `HealthcareServiceRetiredEvent`     | 被停用      | 医疗服务标识, 停用原因                     | 排班/号源（停止新号源） |
| 医疗服务已重新发布 | `HealthcareServiceReactivatedEvent` | 从停用恢复为发布 | 医疗服务标识                           | 排班/号源        |
| 医疗服务已废弃   | `HealthcareServiceAbolishedEvent`   | 被逻辑删除    | 医疗服务标识, 废弃原因                     | QueryService/缓存       |


### 5.2 事件触发规则


| 当前状态    | 操作     | 目标状态    | 发布事件                                |
| ------- | ------ | ------- | ----------------------------------- |
| —       | 创建医疗服务 | DRAFT   | `HealthcareServiceCreatedEvent`     |
| DRAFT   | 发布     | ACTIVE  | `HealthcareServicePublishedEvent`   |
| DRAFT   | 废弃     | DELETED | `HealthcareServiceAbolishedEvent`   |
| ACTIVE  | 修改信息   | ACTIVE  | `HealthcareServiceModifiedEvent`    |
| ACTIVE  | 停用     | RETIRED | `HealthcareServiceRetiredEvent`     |
| RETIRED | 重新发布   | ACTIVE  | `HealthcareServiceReactivatedEvent` |
| RETIRED | 废弃     | DELETED | `HealthcareServiceAbolishedEvent`   |


## 6. 领域服务

### 6.1 医疗服务领域服务（HealthcareServiceDomainService）

#### 6.1.1 创建医疗服务（`createService`）

- **业务意义**：创建医疗服务主数据草稿。
- **前置条件**：服务名称非空（BR-HCS-001）；提供科室有效（BR-HCS-002）。
- **状态变化**：初始化为 DRAFT。
- **事件输出**：`HealthcareServiceCreatedEvent`。

#### 6.1.2 发布医疗服务（`publishService`）

- **业务意义**：将草稿服务发布为可引用状态。
- **前置条件**：当前为 DRAFT；名称、类别、类型、提供科室必填完整。
- **状态变化**：DRAFT -> ACTIVE。
- **事件输出**：`HealthcareServicePublishedEvent`。

#### 6.1.3 修改医疗服务（`modifyService`）

- **业务意义**：维护医疗服务属性。
- **前置条件**：当前为 DRAFT 或 ACTIVE；已发布状态下禁止改类别/类型（BR-HCS-009）。
- **事件输出**：`HealthcareServiceModifiedEvent`。

#### 6.1.4 停用医疗服务（`retireService`）

- **业务意义**：将服务从新业务可选集合移除。
- **前置条件**：当前须为 ACTIVE。
- **状态变化**：ACTIVE -> RETIRED。
- **事件输出**：`HealthcareServiceRetiredEvent`。

#### 6.1.5 重新发布医疗服务（`reactivateService`）

- **业务意义**：恢复停用服务为可引用状态。
- **前置条件**：当前须为 RETIRED。
- **状态变化**：RETIRED -> ACTIVE。
- **事件输出**：`HealthcareServiceReactivatedEvent`。

#### 6.1.6 废弃医疗服务（`abolishService`）

- **业务意义**：终止服务并进入不可恢复终态。
- **前置条件**：当前为 DRAFT 或 RETIRED（BR-HCS-005）；不存在未完成排班/预约引用（BR-HCS-006）。
- **状态变化**：DRAFT/RETIRED -> DELETED。
- **事件输出**：`HealthcareServiceAbolishedEvent`。

### 6.2 读侧查询服务（HealthcareServiceQueryService）

#### 6.2.1 挂号号源列表（`listServicesForRegistration`）

- **业务意义**：提供挂号可选服务集合。
- **查询范围**：按提供科室（必填）查询已发布服务，可按岗位标识取交集过滤。

#### 6.2.2 按主键加载服务（`loadHealthcareServiceById`）

- **业务意义**：为写前校验或预结算读取服务主数据。
- **查询范围**：按 `HealthcareServiceId` 查询服务主档。

### 6.3 领域边界说明

- 本聚合维护「医疗服务」主数据与发布状态，不在本聚合内实现号源余量、排班 Slot 生成、岗位可服务交集等业务规则计算。
- Organization、Location 等被引用方由各自聚合维护；挂号与排班编排通过应用层组合本聚合QueryService与外部上下文。

## 7. 不变式


| 编号         | 不变式                                    | 说明                               |
| ---------- | -------------------------------------- | -------------------------------- |
| BR-HCS-001 | 服务名称不得为空                               | 每个医疗服务必须有可辨识的名称                  |
| BR-HCS-002 | 提供科室标识(OrganizationId)必须指向有效机构         | 通常由登录上下文赋值，必要时写前校验               |
| BR-HCS-003 | 仅"ACTIVE"状态的服务可被排班/号源/预约引用             | DRAFT、RETIRED、DELETED 状态均不可被业务选用 |
| BR-HCS-004 | "RETIRED"状态的服务不可作为新挂号/新排班选项，但历史引用仍有效   | 由应用层在号源查询时过滤，不影响已存在的就诊/排班记录      |
| BR-HCS-005 | "DELETED"状态的服务不可恢复                     | 废弃为终态，需要时应创建新服务                  |
| BR-HCS-006 | 存在未完成排班/预约引用时，服务不可直接废弃                 | 应先完成或取消相关排班/预约，再执行废弃             |
| BR-HCS-007 | 医疗服务与诊查费定价(ChargeItemDefinition)须可一致追溯 | 由应用层在创建/修改时同步维护                  |
| BR-HCS-008 | "ACTIVE"状态下不允许修改服务类别与服务类型              | 避免已引用的号源语义发生变化                   |
| BR-HCS-009 | 医保编码非空时，编码格式须符合国家医保服务目录规范              | 医保数据完整性                          |


## 8. 上下文映射

### 8.1 上游依赖


| 上游上下文 | 聚合           | 关联方式                     | 说明          |
| ----- | ------------ | ------------------------ | ----------- |
| 组织架构  | Organization | `提供科室标识(OrganizationId)` | 医疗服务归属的提供科室 |
| 地点管理  | Location     | `地点标识(LocationId)`       | 服务默认地点（可选）  |


### 8.2 下游影响


| 下游上下文 | 聚合                   | 触发方式 | 说明                                                   |
| ----- | -------------------- | ---- | ---------------------------------------------------- |
| 排班管理  | Schedule / Slot      | 引用   | 排班号源关联 `医疗服务标识(HealthcareServiceId)`                 |
| 预约挂号  | Appointment          | 引用   | 预约记录引用医疗服务确定号源类型                                     |
| 岗位管理  | PractitionerRole     | 引用   | 岗位可提供服务列表通过 `医疗服务标识(HealthcareServiceId)` 引用         |
| 收费管理  | ChargeItemDefinition | 事件订阅 | 监听 `HealthcareServicePublishedEvent`，应用层联动创建/更新诊查费定价 |
| 就诊管理  | Encounter            | 引用   | 就诊记录引用医疗服务标识，标识本次就诊的挂号类型                             |


### 8.3 引用规则

> **统一规则**：
>
> - 所有引用医疗服务的聚合，统一使用 `医疗服务标识(HealthcareServiceId)` 引用
> - 引用时须校验目标服务状态为"已发布"（BR-HCS-003）
> - 诊查费定价联动由应用层编排，本聚合不直接维护定价表（BR-HCS-008）

### 8.4 事件流示意图

```
Organization         HealthcareService              Schedule/Slot           Appointment
     │                      │                            │                      │
     │── 提供科室 ──────>│                            │                      │
     │                      │── Published ──────────>│ (可创建号源)          │
     │                      │                            │                      │
     │                      │── Published ──────────────────────────────>│ (可预约)
     │                      │                            │                      │
     │                      │── Retired ────────────>│ (停止新号源)          │
     │                      │                            │                      │

HealthcareService              PractitionerRole          ChargeItemDefinition
     │                              │                          │
     │── Published ────────────>│ (可关联岗位)              │
     │                              │                          │
     │── Published ──────────────────────────────────────>│ (联动创建诊查费)
     │                              │                          │
     │── Modified ───────────────────────────────────────>│ (联动更新诊查费)
     │                              │                          │
```

### 9.5 变更历史

| 版本 | 日期 | 摘要 |
| --- | --- | --- |
| v1.0 | — | 首版 |
| v1.0.3 | 2026-09-07 | 产品种子更名「复诊0元号」（默认 `bus_no=HS00000004`，0 元，主键 `322957328119180160`）；**资格 Gate 按 `name` 识别**。对齐 **AS-OP-FIN-001 v2.40 / BR-REG-020** |
| v1.0.2 | 2026-08-22 | 产品种子「历史号复诊」（`bus_no=HS_HIST_FOLLOW_UP`，0 元，主键 `322957328119180160`）；种子落点 `health/V2__adm_ddl`；资格门禁见 **AS-OP-FIN-001 BR-REG-020** |
| v1.0.1 | 2026-05-28 | **M1**：§6.1/§6.2 与 `HealthcareServiceDomainService`/`HealthcareServiceQueryService` 白名单对齐（6写+2读） |

---

## 9. 补充

### 9.1 与 Schedule / PractitionerRole 的协作


| 维度   | HealthcareService | Schedule / Slot          | PractitionerRole         |
| ---- | ----------------- | ------------------------ | ------------------------ |
| 性质   | 服务能力定义（静态）        | 号源时段安排（动态）               | 医生岗位（静态）                 |
| 关联方向 | —                 | 引用 `HealthcareServiceId` | 引用 `HealthcareServiceId` |
| 生命周期 | 长期有效              | 按排班周期创建                  | 随人事变动调整                  |
| 职责边界 | 描述"能做什么"          | 描述"什么时候能做"               | 描述"谁能做"                  |


### 9.2 诊查费定价联动流程

医疗服务与诊查费定价的联动由**应用服务层**编排，本聚合不直接维护定价表：

1. **创建联动**：创建医疗服务时，若入参包含诊查费金额，应用层同步调用收费域创建 ChargeItemDefinition
2. **修改联动**：修改医疗服务时，应用层按"存在则更新，不存在则补建"同步诊查费
3. **停用联动**：停用医疗服务时，应用层决定是否同步停用对应的诊查费定价
4. **职责边界**：定价本体由 ChargeItemDefinition 管理，HealthcareService 仅作为引用锚点

<!-- MODE-B-METHOD-INVENTORY 2026-09-23 -->

## 附录 · 模式 B 已实现方法清单（2026-09-23）

> 自 zxplat-domain 对应 *DomainService / *QueryService 抽取 public 方法名，用于关闭方法白名单 DOC-DRIFT。细规仍以正文 §6 与源码为准；本表不替代九章成稿。

### DomainService：`HealthcareServiceDomainService`

| 方法 |
|------|
| `createService` |
| `publishService` |
| `modifyService` |
| `retireService` |
| `reactivateService` |
| `abolishService` |

### QueryService：`HealthcareServiceQueryService`

| 方法 |
|------|
| `listServicesForRegistration` |
| `listServicesForRegistrationManage` |
| `loadHealthcareServiceById` |
| `loadHealthcareServiceMapByIds` |
| `isHistoricalFollowUpService` |
| `findActiveHealthcareServiceByBusNo` |

