# DM-CLN-004 Procedure 执行记录

> **限界上下文**：临床域 · 执行管理\
> **聚合类型**：实体聚合\
> **版本**：v1.1.3 · **日期**：2026-08-06

## 1. 概述

### 1.1 业务定义

执行记录（Procedure）是对医嘱或诊疗请求“已执行/未执行/取消执行”等结果的事实落账，承载执行状态、执行时点、执行人与来源请求引用，作为临床执行闭环与后续收费/发放编排的上游事实依据。

### 1.2 核心职责


| 职责     | 说明                                                    |
| ------ | ----------------------------------------------------- |
| 执行事实留痕 | 记录来源请求、执行状态、执行时间、执行结果                                 |
| 执行人留痕  | 记录执行参与者、开始/结束时间、角色类型                                  |
| 执行链路关联 | 通过 `requestId + requestTable + refundId` 串联原执行与取消执行链路 |
| 查询投影支撑 | 为门诊/住院执行台提供列表、明细与按请求回查能力                              |
| 跨域锚点   | 作为 ChargeItem、Dispense 等下游编排的触发事实输入                   |


### 1.3 适用范围


| 类型   | 说明                   |
| ---- | -------------------- |
| 门诊执行 | 记录门诊检查/检验/治疗等执行事实    |
| 住院执行 | 记录住院医嘱与护理执行事实        |
| 取消执行 | 记录取消执行事实，并关联原始执行记录   |
| 执行查询 | 支撑按请求、按条件、按标识的执行记录查询 |


### 1.4 与 ServiceRequest / MedicationRequest 的关系

Procedure 代表“执行事实”，ServiceRequest / MedicationRequest 代表“执行请求”。


| 维度   | Procedure                          | ServiceRequest / MedicationRequest |
| ---- | ---------------------------------- | ---------------------------------- |
| 语义定位 | 已发生的执行事实                           | 待执行或已开立的请求指令                       |
| 生命周期 | 围绕执行过程变化                           | 围绕开立/调整/停止变化                       |
| 关联方式 | 通过 `requestId + requestTable` 引用请求 | 被 Procedure 以只读标识引用                |


---

## 2. 聚合根与持久化形态

### 2.1 持久化形态

**持久化形态**：主表 + 子表。

- 主表：`cln_procedure`（执行记录聚合根）
- 子表：`cln_procedure_performer`（执行人 1..N，外键 `procedure_id`）

**高频查询模式（建议索引口径）**：按 `request_id` + `request_table` 幂等回查；执行台列表按 `encounter_id`、`patient_id`、`status_enum`、`occurrence_time` / `recorded_time` 筛选；执行人明细按 `procedure_id` 查询。

### 2.2 聚合根标识

| 属性     | 类型            | 说明          |
| ------ | ------------- | ----------- |
| 执行记录标识 | `ProcedureId` | 全局唯一主键，系统生成 |

### 2.3 状态机

```
                    ┌──恢复──┐
                    ▼        │
[待执行] ──开始──> [执行中] ──暂停──> [暂停]
    │                  │
    │                  ├──完成──> [已执行]
    │                  │
    │                  └──停止──> [停止]
    │
    └──取消──> [取消执行]
[执行中] ──取消──> [取消执行]
```


| value | code | info | 语义 |
| ----- | ---- | ---- | ---- |
| 1 | PREPARATION | 待执行 | 已创建，等待执行，允许开始执行、取消 |
| 2 | IN_PROGRESS | 执行中 | 正在执行，允许完成、暂停、停止、取消 |
| 3 | ON_HOLD | 暂停 | 执行中断待恢复，允许恢复执行、停止、取消 |
| 4 | COMPLETED | 已执行 | 执行完成 | — |
| 5 | STOPPED | 停止 | 执行中止 | — |
| 6 | CANCEL | 取消执行 | 执行前/执行中被取消 | — |
| 7 | NOT_DONE | 未完成 | 未达到完成条件 | — |
| 8 | ENTERED_IN_ERROR | 录入错误 | 错误录入需纠正 | — |
| 9 | UNKNOWN | 未知 | 状态不明 | — |


---

## 3. 实体

```
Procedure（聚合根）
════════════════════════════════════════════════════════════

  ┌──────────────────────────────────────────────────────┐
  │                     Procedure                        │
  │                                                      │
  │  - requestId / requestTable / refundId              │
  │  - statusEnum / categoryEnum                         │
  │  - encounterId / patientId                           │
  │  - occurrenceTime / recordedTime / resultText        │
  │                                                      │
  │  ┌──────────────────────────────────────────────┐    │
  │  │ ProcedurePerformer（实体，1..N）             │    │
  │  │ - practitionerId / functionCode              │    │
  │  │ - startTime / endTime                        │    │
  │  │ - orgFromId                                  │    │
  │  └──────────────────────────────────────────────┘    │
  │                                                      │
  └──────────────────────────────────────────────────────┘
```

### 3.1 执行记录（Procedure）

执行记录聚合根属性定义，对应 `cln_procedure` 表。


| 属性        | 字段名                   | 类型                       | 必填  | 说明              |
| --------- | --------------------- | ------------------------ | --- | --------------- |
| 执行记录标识    | `id`                  | `Long`                   | 是   | 聚合根主键           |
| 来源请求标识    | `request_id`          | `Long`                   | 是   | 来源请求 ID         |
| 来源请求表名    | `request_table`       | `String`                 | 是   | 来源请求所属表（区分请求类型） |
| 执行状态      | `status_enum`         | `EventStatus`（值对象）       | 是   | 见 §4.1          |
| 状态原因      | `status_reason_text`  | `String`                 | 否   | 状态变更原因          |
| 执行类别      | `category_enum`       | `ProcedureCategory`（值对象） | 是   | 见 §4.2          |
| 执行编码      | `procedure_code`      | `String`                 | 否   | 诊疗操作编码          |
| 就诊标识      | `encounter_id`        | `EncounterId`            | 是   | 所属就诊            |
| 患者标识      | `patient_id`          | `PatientId`              | 是   | 所属患者            |
| 发生时间      | `occurrence_time`     | `DateTime`               | 否   | 计划/实际发生时间       |
| 发生时期      | `occurrence_period`   | `String`                 | 否   | 时间段文本           |
| 发生年龄      | `occurrence_age`      | `String`                 | 否   | 发生时年龄描述         |
| 发生时间描述    | `occurrence_text`     | `String`                 | 否   | 文本化时间说明         |
| 记录时间      | `recorded_time`       | `DateTime`               | 否   | 实际入库时间          |
| 记录人标识     | `recorder_id`         | `PractitionerId`         | 否   | 记录责任人           |
| 是否有报告     | `report_flag`         | `Boolean`                | 否   | `false`/`true` 标记 |
| 报告关联患者    | `reported_patient_id` | `PatientId`              | 否   | 报告对象患者          |
| 执行科室标识    | `org_id`              | `OrganizationId`         | 否   | 登录工作科室；护士写 NS（等非 TECH）、医技写 TECH；分侧按组织 `class_enum`；历史空值兼容护士侧 |
| 执行位置标识    | `location_id`         | `LocationId`             | 否   | 执行地点            |
| 理由        | `reason_text`         | `String`                 | 否   | 执行原因说明          |
| 身体部位 JSON | `body_site_json`      | `String`                 | 否   | 身体部位扩展          |
| 身体结构标识    | `body_structure_id`   | `Long`                   | 否   | 关联身体结构          |
| 结果        | `result_text`         | `String`                 | 否   | 执行结果描述          |
| 报告 JSON   | `report_json`         | `String`                 | 否   | 报告扩展            |
| 并发症 JSON  | `complication_json`   | `String`                 | 否   | 并发症扩展           |
| 跟进指南 JSON | `follow_up_json`      | `String`                 | 否   | 随访建议扩展          |
| 组号        | `group_id`            | `Long`                   | 否   | 批次分组            |
| 取消执行关联标识  | `refund_id`           | `Long`                   | 否   | 关联被取消的执行记录      |


### 3.2 执行人（ProcedurePerformer）

执行人子实体属性定义，对应 `cln_procedure_performer` 表。与 Procedure 为 1:N。


| 属性      | 字段名               | 类型               | 必填  | 说明           |
| ------- | ----------------- | ---------------- | --- | ------------ |
| 执行人记录标识 | `id`              | `Long`           | 是   | 子实体主键        |
| 执行记录标识  | `procedure_id`    | `ProcedureId`    | 是   | 关联 Procedure |
| 参与者类型   | `function_code`   | `String`         | 否   | 执行角色类型       |
| 开始时间    | `start_time`      | `DateTime`       | 是   | 执行起始时间       |
| 结束时间    | `end_time`        | `DateTime`       | 否   | 执行结束时间       |
| 执行人标识   | `practitioner_id` | `PractitionerId` | 是   | 执行责任人        |
| 外部机构标识  | `org_from_id`     | `String`         | 否   | 外协/飞刀来源机构    |


---

## 4. 值对象

### 4.1 执行状态（EventStatus）

> **成码枚举**：`com.zxplat.domain.share.enums.EventStatus`；取消执行持久化 **`CANCEL`**；**`STOPPED`** 由 **`skipProcedureRecord`**（住院护士站 IF-IP-EXE-04「不执行」）写入，**不停嘱、不写发药/账单**；历史实现码 **`ABORTED`** 与 **`CANCEL`** 同义，由迁移脚本收敛。

| value | code               | info | 语义        |
| ----- | ------------------ | ---- | --------- |
| 1     | `PREPARATION`      | 待执行  | 已创建但未开始执行 |
| 2     | `IN_PROGRESS`      | 执行中  | 正在执行      |
| 3     | `NOT_DONE`         | 未完成  | 因业务原因未完成  |
| 4     | `ON_HOLD`          | 暂停   | 临时中断，可恢复  |
| 5     | `STOPPED`          | 停止   | 已中止，不再继续  |
| 6     | `COMPLETED`        | 已执行  | 执行完成      |
| 7     | `ENTERED_IN_ERROR` | 录入错误 | 记录错误需纠正   |
| 8     | `UNKNOWN`          | 未知   | 状态不可判定    |
| 9     | `CANCEL`           | 取消执行 | 执行被取消     |


### 4.2 执行类别（ProcedureCategory）


| value | code        | info                     | 语义       |
| ----- | ----------- | ------------------------ | -------- |
| 1     | `24642003`  | `PSYCHIATRY`             | 精神疗法     |
| 2     | `409063005` | `COUNSELLING`            | 咨询       |
| 3     | `409073007` | `EDUCATION`              | 宣教       |
| 4     | `387713003` | `SURGICAL`               | 手术       |
| 5     | `15220000`  | `LABORATORY`             | 化验       |
| 6     | `363679005` | `IMAGING`                | 影像       |
| 7     | `122869004` | `MEASUREMENT`            | 测量       |
| 8     | `46947000`  | `MANIPULATION`           | 治疗       |
| 9     | `410606002` | `SOCIAL_SERVICE`         | 社会相关服务   |
| 10    | `10`        | `OUTPATIENT_ADVICE`      | 门诊医嘱执行   |
| 11    | `11`        | `INPATIENT_ADVICE`       | 住院医嘱执行   |
| 12    | `12`        | `INPATIENT_NURSE_ADVICE` | 住院护理医嘱执行 |


---

## 5. 领域事件

### 5.1 事件定义


| 事件名称    | 英文名称                            | 触发时机        | 携带数据 / 最小载荷                        | 消费方 |
| ------- | ------------------------------- | ----------- | ---------------------------------- | --- |
| 执行记录已创建 | `ProcedureRecordCreatedEvent`   | 创建执行记录成功后   | `procedureId`、`requestId`、`requestTable`、`statusEnum` | ChargeItem 编排、发放编排、ProcedureQueryService 查询 |
| 执行人已编辑  | `ProcedurePerformerEditedEvent` | 新增/更新执行人成功后 | `procedureId`、`performerId`、`functionCode`、时间区间   | ProcedureQueryService 查询、工作量统计 |
| 执行记录已完成 | `ProcedureRecordCompletedEvent` | 执行完成成功后     | `procedureId`、完成时间、`resultText`/`reportJson` 摘要          | ChargeItem 编排、病历/报告归档 |
| 执行记录已取消 | `ProcedureRecordCancelledEvent` | 取消执行成功后     | `procedureId`、`statusReasonText`、`refundId`        | 收费冲正编排、发放取消、ProcedureQueryService 查询 |
| 执行记录已登记不执行 | `ProcedureRecordSkippedEvent` | 登记计划点不执行成功后（**IF-IP-EXE-04**） | `procedureId`、`requestId`、`requestTable`、`statusEnum`、`occurrenceTime`、`recordedTime` | 护士站执行槽位投影、执行看板 |


### 5.2 事件触发规则

> 状态名与 **§4.1 `EventStatus`** 的 `code` 列一致（持久化字段 `status_enum`）。

| 当前状态                    | 操作     | 目标状态          | 发布事件                            |
| ----------------------- | ------ | ------------- | ------------------------------- |
| —                       | 创建执行记录 | `PREPARATION`       | `ProcedureRecordCreatedEvent`   |
| `PREPARATION`                 | 开始执行   | `IN_PROGRESS` | （无单独领域事件时可不发布；按需增补）                     |
| `PREPARATION` / `IN_PROGRESS` | 编辑执行人  | 状态不变          | `ProcedurePerformerEditedEvent` |
| `IN_PROGRESS`           | 完成执行   | `COMPLETED`   | `ProcedureRecordCompletedEvent` |
| `PREPARATION` / `IN_PROGRESS` | 取消执行   | `CANCEL`   | `ProcedureRecordCancelledEvent` |
| `PREPARATION` / `IN_PROGRESS` | 登记不执行 | `STOPPED` | `ProcedureRecordSkippedEvent` |


---

## 6. 领域服务

### 6.1 写侧领域服务（ProcedureDomainService）

#### 6.1.1 创建执行记录（`createProcedureRecord`）

- **业务意义**：建立执行事实主档，生成可被后续执行动作稳定引用的 `ProcedureId`。
- **边界说明**：仅处理 Procedure 聚合内创建，不展开来源请求创建流程。
- **入参**：来源请求标识、来源请求表名、就诊标识、患者标识、执行类别、发生时间等基础信息。
- **出参**：执行记录标识（`ProcedureId`）。
- **前置条件**：`requestId + requestTable`、`encounterId`、`patientId` 必填且合法。
- **状态变化**：`N/A -> PREPARATION`（`EventStatus`，见 §4.1）。
- **聚合内影响**：写入主表基础字段并建立来源引用。
- **事件输出**：`ProcedureRecordCreatedEvent`。
- **失败语义**：来源请求定位失败或关键标识缺失时拒绝创建。

#### 6.1.2 进入执行中（`startProcedureExecution`）

- **业务意义**：显式完成「待执行（`PREPARATION`）」到「执行中（`IN_PROGRESS`）」的状态衔接，避免跳过开始环节直接完成。
- **边界说明**：只改变主状态，不处理资源排班与外部协同。
- **入参**：执行记录标识。
- **出参**：—。
- **前置条件**：当前状态必须为 `PREPARATION`。
- **状态变化**：`PREPARATION -> IN_PROGRESS`。
- **聚合内影响**：更新主表 `statusEnum`。
- **事件输出**：当前仓库无单独领域事件（可按需增补）。
- **失败语义**：状态不匹配时拒绝变更。

#### 6.1.3 维护执行人（`appendProcedurePerformer`）

- **业务意义**：记录“谁在何时以何角色执行”，支撑责任追溯与工作量统计。
- **边界说明**：仅维护 `ProcedurePerformer` 子实体，不改写来源请求数据。
- **入参**：执行记录标识、执行人标识、角色编码、开始/结束时间。
- **出参**：—。
- **前置条件**：主记录存在且处于 `PREPARATION` / `IN_PROGRESS`；`practitionerId` 必填；时间区间合法。
- **状态变化**：`N/A`。
- **聚合内影响**：新增或更新执行人子记录，并收敛同角色活动记录。
- **事件输出**：`ProcedurePerformerEditedEvent`。
- **失败语义**：主记录不存在、角色冲突、时间区间非法时拒绝写入。

#### 6.1.4 完成执行（`completeProcedureRecord`）

- **业务意义**：确认执行已完成，收口结果与报告语义；**当 `operatorId` 非空时写入 `recorder_id` 与 `recorded_time`**（护士划价/医嘱执行共用）。
- **边界说明**：仅完成聚合内状态与结果收口，不展开费用结算和病历归档。
- **入参**：执行记录标识、结果文本、报告载荷、**操作人（`adm_practitioner.id`，可选）**。
- **出参**：—。
- **前置条件**：当前状态必须为 `IN_PROGRESS`；完成时至少具备 `resultText` 或 `reportJson` 之一。
- **状态变化**：`IN_PROGRESS -> COMPLETED`。
- **聚合内影响**：更新结果字段；**补写 `recorder_id`/`recorded_time`（若命令携带 operatorId 且 recorded_time 仍空）**；收口活动执行人记录。
- **事件输出**：`ProcedureRecordCompletedEvent`。
- **失败语义**：状态不匹配或结果语义缺失时拒绝完成。

#### 6.1.5 取消执行（`cancelProcedureRecord`）

- **业务意义**：确认执行终止并保留完整取消追溯链路。
- **边界说明**：仅处理 Procedure 聚合内取消事实，不展开退款与外部同步细节。
- **入参**：执行记录标识、取消原因、取消关联标识（可选）。
- **出参**：—。
- **前置条件**：当前状态必须为 `PREPARATION` 或 `IN_PROGRESS`；`statusReasonText` 必填。
- **状态变化**：`PREPARATION` / `IN_PROGRESS -> CANCEL`（`EventStatus.code=CANCEL`，取消执行终态）。
- **聚合内影响**：更新主状态、取消原因、`refundId` 关联并收口活动执行人。
- **应用编排约束（取消对冲新建行）**：护士站取消执行 / 取消划价 / 门诊取消 / 医技取消须 **`create→start→挂主执行人 PRIMARY（操作人）→cancel`**，与执行 `create→start→挂人→complete` 同构；**禁止**终态后再挂执行人。
- **事件输出**：`ProcedureRecordCancelledEvent`。
- **失败语义**：已完成记录不可取消；跨请求链路关联非法时拒绝写入。

#### 6.1.6 登记计划点不执行（`skipProcedureRecord`）

- **业务意义**：住院护士站对某一**计划执行时刻**登记「不执行」事实，槽位迁入 skipped；**不同于停嘱**（Request 仍 VERIFIED）与**取消执行**（CANCEL 对冲）。
- **边界说明**：仅写 Procedure 聚合；**不**写 Dispense、Charge、Request 停嘱；长期多计划点须 **`skipDuplicateRequestAnchorCheck=true`**（**AS-IP-NUR-004 BR-IP-EXE-015**）。
- **入参**：`SkipProcedureRecordCommand`——`requestId`、`requestTable`、`encounterId`、`patientId`、`categoryEnum`、`occurrenceTime`（计划点）、`recordedTime`（登记时刻）、`operatorId`（必填，兼主执行人）、可选 `statusReasonText`、`skipDuplicateRequestAnchorCheck`。
- **出参**：执行记录标识。
- **前置条件**：请求锚点、就诊、患者、类别、`occurrenceTime`、`operatorId` 必填；默认同 request 仅允许一条 Procedure，多计划点须显式放开重复门禁。
- **状态变化**：`create(PREPARATION)→start(IN_PROGRESS)→挂 PRIMARY→STOPPED`（与执行/取消同构，**禁止**跳过执行人直接终态）。
- **聚合内影响**：写入 `occurrence_time`、`recorded_time`、`recorder_id`、来源引用，以及 `cln_procedure_performer` 主执行人（`operatorId`）。
- **事件输出**：`ProcedureRecordSkippedEvent`（**save 之后**发布）。
- **失败语义**：同 request 已存在 Procedure 且未放开重复门禁时拒绝；操作人为空拒绝。
- **代码先行回写**：2026-09-29 — `Procedure#skip` 允许 `PREPARATION`/`IN_PROGRESS`；`skipProcedureRecord` 内挂 PRIMARY。

#### 6.1.7 修正客户可见记录时刻（`amendProcedureRecordedTime`）

- **业务意义**：护士站划价「修改划价客户可见时间」（**AS-IP-NUR-005 · IF-IP-PRC-07**）仅修正已完成执行记录的客户可见时刻；**只改** `recorded_time`。
- **边界说明**：仅写本聚合 `recorded_time`；**不改** `occurrence_time`、状态、执行人、结果；**不**写 Charge / Request；不发布额外领域事件（展示轴修正，非状态迁移）。
- **入参**：`AmendProcedureRecordedTimeCommand`——`procedureId`、`recordedTime`（新客户可见时刻）、`operatorId`。
- **出参**：—。
- **前置条件**：主记录存在且状态为 **`COMPLETED`**（净剩 COMPLETED 由应用层定位后传入）；`recordedTime` 非空。
- **状态变化**：`N/A`（状态不变）。
- **聚合内影响**：覆盖写入 `recorded_time`；`occurrence_time` 保持不变。
- **事件输出**：无（展示轴修正）。
- **失败语义**：记录不存在、非 `COMPLETED`、或时刻为空时拒绝。

### 6.2 读侧查询服务（ProcedureQueryService）

#### 6.2.1 按请求查询执行记录（`getProcedureRecords`）

- **业务意义**：按来源请求回查执行记录，用于幂等判断与来源追踪。
- **边界说明**：只读查询，不写库，不做跨域编排。
- **查询范围（读库，无写入）**：`cln_procedure` 主表，按 `requestId/requestTable` 检索。
- **过滤口径**：默认过滤逻辑删除数据，仅返回符合租户与机构权限的数据。
- **组装规则**：以请求为维度返回执行记录集合，按需附带执行人概览。
- **返回约束**：允许空结果；结果项需包含状态与关键时点。

#### 6.2.2 按条件查询执行记录投影（`queryProcedureProjections`）

- **业务意义**：为列表、看板、报表提供领域事件后查询刷新入口。
- **边界说明**：只读查询，不写库，不做跨域编排。
- **查询范围（读库，无写入）**：领域事件后查询刷新或聚合查询视图。
- **过滤口径**：按状态、时间、科室、类别等条件过滤。
- **组装规则**：返回轻量投影对象，避免写模型联表膨胀。
- **返回约束**：当前实现可返回空列表占位，待投影表/Mapper 接入后替换。

#### 6.2.3 查询执行记录明细（`loadProcedureById` / `mustGetProcedure`）

- **业务意义**：按主键加载主记录并支持写前校验。
- **边界说明**：只读查询，不写库，不做跨域编排。
- **查询范围（读库，无写入）**：按执行记录标识查询 `cln_procedure`。
- **过滤口径**：默认过滤逻辑删除并校验租户权限。
- **组装规则**：返回主记录，必要时结合执行人明细查询。
- **返回约束**：`mustGetProcedure` 在记录不存在时抛明确异常。

#### 6.2.4 查询执行人明细（`listPerformerByProcedure`）

- **业务意义**：获取执行责任链明细，支撑追溯与展示。
- **边界说明**：只读查询，不写库，不做跨域编排。
- **查询范围（读库，无写入）**：`cln_procedure_performer` 按 `procedureId` 查询。
- **过滤口径**：仅返回有效且权限可见的执行人记录。
- **组装规则**：按时间排序输出执行人轨迹。
- **返回约束**：允许空集合。

#### 6.2.5 完成前联合校验（`checkProcedureCompletable`）

- **业务意义**：在 `completeProcedureRecord` 前做统一可完成性校验，避免写侧重复拼接判断。
- **边界说明**：只做校验，不写库，不做跨域编排。
- **查询范围（读库，无写入）**：主记录 + 活动执行人记录。
- **过滤口径**：以目标 `procedureId` 精确过滤。
- **组装规则**：按固定顺序执行并汇总校验结果（全部通过才可完成）：
  - 记录存在且可见（非逻辑删除、租户权限可见）；
  - 主状态必须是 `IN_PROGRESS`（`PREPARATION` / `CANCEL` / `COMPLETED` 等均失败）；
  - 结果语义满足最小门槛（`resultText` 与 `reportJson` 至少一项非空）；
  - 若存在活动执行人记录，则活动记录可收口（时间区间合法、可写回结束时间）；
  - 并发版本未冲突（版本号/更新时间未被他人写入覆盖）。
- **返回约束**：返回 `pass + failCode + failReason`；任一校验失败即阻断完成动作。

#### 6.2.6 净剩完成行查询（`listNetRemainingCompletedProcedureRecords`）

- **业务意义**：按来源请求回查**未被 CANCEL 对冲**的 `COMPLETED` 行，供门诊/住院护士站判定「是否仍有净执行」。
- **边界说明**：只读；实现于 `ProcedureQueryService`；来源表限药品/诊疗申请持久化名。
- **返回约束**：无命中返回空集合。

#### 6.2.7 住院护士站批量按就诊查询执行记录（仓储派生 · 下游消费）

- **业务意义**：**AS-IP-NUR-001** 待执行计数：按就诊主键集合加载 `cln_procedure`；应用层按自然日 + 请求锚点计算净执行（对齐 §6.2.6 对冲规则）。
- **边界说明**：只读；`ProcedureQueryService#listByEncounterIds` → `ProcedureRepository#findByEncounterIdIn`。
- **过滤口径**：仅消费 `request_table ∈ { med_medication_request, wkf_service_request }` 的行；当日以 `occurrence_time`（空则 `recorded_time`）自然日窗口。
- **返回约束**：禁止按请求逐条 `findByEncounterIdAndRequestIdAndRequestTable` 拼批量效果。

#### 6.2.8 按主键批量查询执行记录（`listByIds`）

- **业务意义**：按 `procedure_id` 集合一次加载执行记录实体，供费用明细等读侧批量关联。
- **边界说明**：只读；`ProcedureQueryService#listByIds` → `ProcedureRepository#findAllByIdIn`。
- **返回约束**：入参为空返回空列表；未命中主键不出现在结果中（不抛错）。

#### 6.2.9 净剩 COMPLETED 业务时间索引（`mapNetCompletedOccurrenceTimeByEncounterId`）

- **业务意义**：**AS-IP-NUR-006** 费用明细业务时间：医嘱/执行路径 ChargeItem 的 `procedure_id` **必须**映射到净剩 `COMPLETED` 的 `occurrence_time`。
- **边界说明**：只读；内部复用 §6.2.6 `listNetRemainingCompletedProcedureRecords` 对冲规则（`CANCEL`+`refund_id` 抵消对应 `COMPLETED`）。
- **返回约束**：`Map<procedureId, occurrenceTime>`；无净剩行返回空 Map。

#### 6.2.10 护士诊疗执行工作量按类目汇总（`summarizeNurseExecutionWorkloadByCategory`）

- **业务意义**：**AS-RPT-002 IF-RPT-NWL-02** 按 `ServiceRequest.category_enum` 汇总净 COMPLETED 次数。
- **边界说明**：只读；`request_table = wkf_service_request`；净执行口径同 §6.2.6；`recorded_time` 闭区间；`category_enum` 由 Support 预解析写入 `NurseWorkloadPageQuery.categoryEnumByServiceRequestId`。
- **入参**：`NurseWorkloadPageQuery`（须经 `NurseWorkloadQuerySupport#prepareNurseWorkloadQueryScopes`）。
- **返回约束**：无命中返回空列表。

#### 6.2.11 护士诊疗执行工作量明细分页（`pageNurseExecutionWorkloadDetail`）

- **业务意义**：**AS-RPT-002 IF-RPT-NWL-03** 返回净 COMPLETED Procedure 键集 + `serviceRequestId` + 主执行人 + `recorded_time`。
- **边界说明**：只读；排序 `recorded_time DESC`；默认 20 条/页（`NurseWorkloadConstants.DEFAULT_PAGE_SIZE`）。
- **入参**：`NurseWorkloadPageQuery`（含分页键）。
- **返回约束**：`Page<NurseExecutionWorkloadDetailQueryModel>`。

### 6.3 领域边界说明

- `Procedure` 领域动作仅描述执行记录与执行人子实体的行为，不承接收费、发放、退费等应用层编排。
- 通过 `requestId + requestTable` 对来源请求做标识化只读引用，不在聚合内内嵌请求聚合对象。
- 读侧服务只提供查询与投影能力，不进行任何写入或跨域补偿逻辑。

---

### 9.1 变更历史

| 版本 | 日期 | 摘要 |
| --- | --- | --- |
| v1.1.4 | 2026-09-29 | §3.1 `org_id`：登录工作科室分侧（NS/TECH）；历史空值兼容护士侧；对齐协同 DD / **BR-IP-ATD-011⑤** |
| v1.1.3 | 2026-08-06 | §6.1.7 `amendProcedureRecordedTime`（仅改 COMPLETED `recorded_time`；**AS-IP-NUR-005 IF-IP-PRC-07**） |
| v1.1.2 | 2026-07-03 | §6.2.10～6.2.11 护士工作量读侧（**AS-RPT-002**） |
| v1.1.1 | 2026-05-28 | **M1**：§6.1/§6.2 与 `ProcedureDomainService`/`ProcedureQueryService` 白名单对齐（6写+9读） |
| v1.1 | 2026-05-24 | §6.2.8～6.2.9 费用明细净剩执行时刻索引，**AS-IP-NUR-006** |
| v1.0 | — | 初版 |

---

## 7. 不变式


| 编号         | 不变式             | 说明                                    |
| ---------- | --------------- | ------------------------------------- |
| BR-PRO-001 | 执行记录必须绑定来源请求    | `requestId + requestTable` 不可为空       |
| BR-PRO-002 | 执行记录必须绑定就诊与患者   | `encounterId`、`patientId` 不可为空        |
| BR-PRO-003 | 执行记录终态后不可回退到非终态 | `COMPLETED`、`CANCEL`（`EventStatus`）不可回退          |
| BR-PRO-004 | 完成动作必须具备结果语义    | 至少具备 `resultText` 或 `reportJson` 之一   |
| BR-PRO-005 | 取消动作必须可追溯       | `statusReasonText` 必填，`refundId` 链路合法 |
| BR-PRO-006 | 聚合写入必须先查后改      | 写动作前必须加载主记录并校验状态                      |
| BR-PRO-007 | 并发写入必须具备冲突保护    | 禁止覆盖式并发更新                             |
| BR-PRO-008 | 读侧查询不改变执行记录状态   | CQRS 读写分离约束                           |


---

## 8. 上下文映射

### 8.1 上游依赖


| 上游上下文 | 聚合                                 | 关联方式                       | 说明     |
| ----- | ---------------------------------- | -------------------------- | ------ |
| 医嘱域   | ServiceRequest / MedicationRequest | `requestId + requestTable` | 执行来源请求 |
| 就诊域   | Encounter                          | `encounterId`              | 执行归属就诊 |
| 患者域   | Patient                            | `patientId`                | 执行归属患者 |
| 人员域   | Practitioner                       | `practitionerId`           | 执行责任人  |


### 8.2 下游影响


| 下游上下文 | 聚合                                  | 触发方式         | 说明              |
| ----- | ----------------------------------- | ------------ | --------------- |
| 收费域   | ChargeItem                          | 应用层编排 / 事件消费 | 依据执行事实生成或更新收费明细 |
| 发放域   | MedicationDispense / DeviceDispense | 应用层编排 / 事件消费 | 依据执行事实生成发放流程    |
| 执行看板  | 执行记录投影                              | 查询           | 展示执行进度与状态统计     |


### 8.3 引用规则

> **统一规则**：所有引用执行记录的领域模型遵循以下原则：
>
> - 执行来源统一使用 `requestId + requestTable`，避免跨聚合对象耦合
> - 下游对 Procedure 为只读引用，不直接改写 Procedure 主状态
> - 取消执行通过 `refundId` 追溯原执行事实，保持链路一致

### 8.4 事件流示意图

```text
ServiceRequest/MedicationRequest      Procedure               ChargeItem           Dispense
             │                           │                         │                  │
             │── 执行触发输入 ─────────>│                         │                  │
             │                           │── ProcedureRecordCreatedEvent ──>│ (计费编排)        │
             │                           │                         │                  │
             │                           │── ProcedureRecordCreatedEvent ──────────────────>│ (发放编排)
             │                           │                         │                  │
             │── 取消请求输入 ─────────>│                         │                  │
             │                           │── ProcedureRecordCancelledEvent ->│ (冲正/调整编排)  │
```

---

## 9. 补充

### 9.1 变更记录

| 日期 | 说明 |
|------|------|
| 2026-09-29 | **代码先行回写**：列表终态槽位操作人展示（**BR-IP-EXE-031**）**仅** `cln_procedure_performer` PRIMARY；`recorder_id` 仍写审计、**不**作展示回落 |
| 2026-09-29 | **代码先行回写**：取消对冲新建行须 `create→start→挂 PRIMARY→cancel`（护士站取消执行/取消划价/门诊取消/医技取消同构）；`skipProcedureRecord` 改为 `create→start→挂 PRIMARY→STOPPED`，`Procedure#skip` 允许 PREPARATION/IN_PROGRESS；禁止 CANCEL/STOPPED 行无主执行人 |

<!-- MODE-B-METHOD-INVENTORY 2026-09-23 -->

## 附录 · 模式 B 已实现方法清单（2026-09-23）

> 自 zxplat-domain 对应 *DomainService / *QueryService 抽取 public 方法名，用于关闭方法白名单 DOC-DRIFT。细规仍以正文 §6 与源码为准；本表不替代九章成稿。

### DomainService：`ProcedureDomainService`

| 方法 |
|------|
| `createProcedureRecord` |
| `startProcedureExecution` |
| `appendProcedurePerformer` |
| `completeProcedureRecord` |
| `cancelProcedureRecord` |
| `skipProcedureRecord` |
| `amendProcedureRecordedTime` |

### QueryService：`ProcedureQueryService`

| 方法 |
|------|
| `getProcedureRecords` |
| `mapProcedureRecordsByRequestAnchorForEncounters` |
| `listByEncounterIds` |
| `listByIds` |
| `mapNetCompletedOccurrenceTimeByEncounterId` |
| `mapNetCompletedRecordedTimeByServiceRequestIds` |
| `findNetCompletedProcedureIdByServiceRequest` |
| `mapNetCompletedRecordedTimeByEncounterId` |
| `listNetCompletedServiceRequestProceduresByRecordedTimeWindow` |
| `listNetCompletedMedicationRequestProceduresByRecordedTimeWindow` |
| `listNetCompletedProceduresByRequestTableAndRecordedTimeWindow` |
| `listNetRemainingCompletedProcedureRecords` |
| `pageProceduresForOrderExecutionSheet` |
| `mapPerformerListJsonByProcedureIds` |
| `mapPrimaryPractitionerIdByProcedureIds` |
| `queryProcedureProjections` |
| `loadProcedureById` |
| `listPerformerByProcedure` |
| `checkProcedureCompletable` |
| `summarizeNurseExecutionWorkloadByCategory` |
| `pageNurseExecutionWorkloadDetail` |
| `listNurseExecutionWorkloadDetail` |

