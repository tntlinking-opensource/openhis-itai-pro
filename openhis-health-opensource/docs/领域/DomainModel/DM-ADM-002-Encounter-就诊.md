# DM-ADM-002 Encounter 就诊

> **限界上下文**：患者管理 · 就诊管理  
> **聚合类型**：实体聚合  
> **版本**：v1.55

## 1. 概述

### 1.1 业务定义

就诊（Encounter）是患者在医疗机构内发生的**一次完整诊疗交互过程**，涵盖从门诊挂号（或住院申请）到诊毕（或出院）的全生命周期。Encounter 是病历、医嘱、费用、检验检查等业务对象的**关联锚点**，为跨域协作提供稳定的 `就诊标识(EncounterId)`。

### 1.2 核心职责


| 职责       | 说明                              |
| -------- | ------------------------------- |
| 诊疗生命周期管理 | 表达一次就诊从创建、分诊、接诊、诊疗到结束/取消的完整状态流转 |
| 参与者管理    | 管理就诊过程中的医生、护士等参与者关系与时间跟踪        |
| 位置轨迹管理   | 记录就诊过程中患者的诊室、病区、床位等位置变更轨迹       |
| 诊断关联管理   | 维护就诊与诊断之间的关联关系，承载主诊排序与医保字段      |
| 就诊原因管理   | 记录就诊原因、主诉与补充说明                  |
| 跨域关联锚点   | 为病历、医嘱、费用、检验检查等提供稳定的就诊标识        |


### 1.3 适用范围


| 类型         | 说明                      |
| ---------- | ----------------------- |
| 门诊就诊（AMB）  | 预结算开立就诊（`PLANNED` 待挂号）→ **挂号费结清**（`REGISTERED` 已挂号）→ 分诊（可选）→ 候诊→ 接诊→ 诊疗→ 完诊，单日内完成 |
| 住院就诊（IMP）  | 医生申请（待登记）→ 住院登记（待入科）→ 分床（已入科）→ 出院医嘱/转科过渡 → 护士出院（待结算）→ 收费结算（已结算）→ 清床离院（已出院）；**状态语义仅 IMP 成立**，见 §2.3.1 |


### 1.4 与 EpisodeOfCare 的关系

Encounter（就诊）与 EpisodeOfCare的职责划分是评审中的核心关注点。慢病管理、康复随访等“同一患者多次就诊”场景下，每一次来院交互都独立建模为一条 Encounter；住院阶段也继续使用 Encounter 表达单次在院诊疗事实，跨科流转通过 EncounterLocation 的轨迹变更表达：


| 维度    | Encounter           | EpisodeOfCare        |
| ----- | ------------------- | -------------------- |
| 管理范围  | 单次就诊周期（挂号→诊毕）       | 住院全程管理（入院→在院→出院）     |
| 门诊场景  | 完整覆盖                | 不参与                  |
| 住院场景  | 每次在院期间的诊疗交互         | 串联多次 Encounter 的住院总线 |
| 转科/转床 | 在 Encounter 内维护位置轨迹 | 记录科室级归属变更            |
| 出院结算  | 标记单次就诊结束            | 触发住院周期终结             |


> **边界规则**：门诊场景只引用 Encounter；住院场景同时引用 Encounter 和 EpisodeOfCare。转科操作归属 Encounter 聚合内的位置轨迹管理，EpisodeOfCare 只记录科室级归属变更。

### 1.5 下游应用服务（住院结算链）

| AS | 本聚合能力 | DM 锚点 |
| --- | --- | --- |
| **AS-IP-FIN-003** 住院结算 | 出院全结 `PENDING→COMPLETED` 或 **无活动床** `PENDING→DISCHARGED` | §6.1.20 `completeInpatientFinancialSettlement` |
| **AS-IP-FIN-004** 住院反结 | 出院全结对称回退 `COMPLETED→IN_PROGRESS` | §6.1.20a `revertInpatientFinancialSettlement` |
| **AS-IP-NUR-002** 护士入出转 | 护士出院 `ON_HOLD→PENDING`（全结前置）；**取消办理出院** `PENDING→ON_HOLD/IN_PROGRESS` | §6.1.19 `executeInpatientNurseDischarge`；§6.1.19a `cancelInpatientNurseDischarge` |
| **AS-IP-NUR-002** 清床 | `COMPLETED→DISCHARGED`（全结后置） | §6.1.21 `clearDischargedBed` |

## 2. 聚合根与持久化形态

### 2.1 持久化形态

**持久化形态**：主表+子表

聚合根对应主表，子实体对应子表，通过外键 `encounter_id` 关联。聚合边界以主表主键 `id`（就诊标识）划分，主表一行对应子表 N 行（0 ≤ N）。

**表结构**：

- 主表：`adm_encounter`（主键：`id`）
- 子表1：`adm_encounter_participant`（外键：`encounter_id`）
- 子表2：`adm_encounter_location`（外键：`encounter_id`）
- 子表3：`adm_encounter_diagnosis`（外键：`encounter_id`）
- 子表4：`adm_encounter_reason`（外键：`encounter_id`）
- 关系：1:N，子表行数不限，可为 0

**高频查询模式**：

- 按就诊标识查询：单表主键查询主表，必要时 JOIN 子表
- 按业务号查询：主表 `bus_no` 索引扫描
- 按患者查询就诊列表：主表 `patient_id` 索引扫描
- 按机构科室查询：主表 `organization_id` 索引扫描

### 2.2 聚合根标识


| 属性   | 类型            | 说明                 |
| ---- | ------------- | ------------------ |
| 就诊标识 | `EncounterId` | 全局唯一标识，系统生成（雪花 ID） |


### 2.3 状态机

```
门诊链路（AMB）

  [PLANNED] ──挂号费结清（领域 completeOutpatientRegistration / 应用编排）──> [REGISTERED]
      │
      ├──分诊──> [TRIAGED] ──接诊──> [IN_PROGRESS]
      │
      └──直接接诊（无分诊机构，须已为 REGISTERED）────────────────> [IN_PROGRESS]
      │                                       │
      │                                ┌──────┴──────┐
      │                       暂离     ▼             ▼   完诊
      │                         [ON_HOLD]      [DISCHARGED]
      │                            │
      │                            └──恢复──> [IN_PROGRESS]
      │
      └──取消──> [CANCELLED]
  任意非终止状态（权限允许）──> [ENTERED_IN_ERROR]


住院链路（IMP）

  [PLANNED] ──住院登记（有申请）──> [REGISTERED] ──分床──> [IN_PROGRESS]
      │                              │                      │
      │                              │               ┌──────┴──────┐
      │                              │        转科医嘱 ▼             ▼ 出院医嘱
      │                              │            [ON_HOLD]    [ON_HOLD]
      │                              │         （待转科过渡）  （待出院）
      │                              │               │             │
      │                              │               └──确认转科──> [REGISTERED]
      │                              │                             │
      │                              │                             ▼ 护士出院执行
      │                              │                         [PENDING] 待结算
      │                              │                             │
      │                              │                             ▼ 住院结算
      │                              │                         [COMPLETED] 已结算
      │                              │                             │
      │                              │                             ▼ 清床离院
      │                              │                         [DISCHARGED] 已出院
      │                              │
      └──取消登记──> [CANCELLED]     └──取消登记──> [CANCELLED]
      （仅 PLANNED / REGISTERED，未分床前）

  无医生申请直登（收费窗）：INSERT+登记 ──> [REGISTERED]（不经 PLANNED，与有申请登记后汇合）──> 分床 ──> [IN_PROGRESS]

  任意非终止状态（权限允许）──> [ENTERED_IN_ERROR]
```

#### 状态定义


| value | code             | info（通用码表） | 语义（须结合 `class_enum`） |
| ----- | ---------------- | ------------ | --------------------- |
| 1     | PLANNED          | 待计划/待挂号      | **AMB**：见 §2.3 门诊链路；**IMP**：**仅**医生住院申请后 |
| 2     | REGISTERED       | 已挂号          | **AMB**：挂号费结清；**IMP**：**待入科**（已登记、未分床或转科后回待入科） |
| 3     | TRIAGED          | 已分诊          | 门诊                  |
| 4     | PRE_ADMISSION    | 预入院          | **本期 IMP 主链路不使用**（遗留/扩展位） |
| 5     | IN_PROGRESS      | 在诊           | **AMB**：接诊中；**IMP**：**已入科**（已分床在院） |
| 6     | ON_HOLD          | 暂离           | **AMB**：暂离；**IMP**：**过渡/待办**（待转科或待出院，由医嘱类型区分，见 §2.3.1） |
| 7     | DISCHARGED       | 诊毕/出院        | **AMB**：诊毕；**IMP**：**已出院**（清床离院，**业务终态**） |
| 8     | COMPLETED        | 已完成          | **IMP**：**已结算**（财务终态，**临床未离院**）；门诊其它语义以实现为准 |
| 9     | CANCELLED        | 已取消          | **AMB**：退号（挂号窗口列表展示文案为「退号」，不展示「已取消」）；**IMP**：**取消住院登记**（终止态） |
| 10    | PENDING          | 待出院结算        | **IMP**：**待结算**（护士出院执行后） |
| 11    | ENTERED_IN_ERROR | 错误录入         | **AMB**：未收费待挂号窗口「取消」（与退号 `CANCELLED` 分流）；其它场景为误操作标记 |


#### 2.3.1 住院（`IMP`）状态语义专规（必读）

> **硬约束**：下列产品阶段与迁移规则 **仅在 `class_enum = IMP` 时成立**。QueryService、应用服务、报表、收费窗口在解释 `status_enum` 时 **必须**带 `class_enum` 条件；**禁止**用住院语义解释门诊同码记录。

| 产品阶段 | `status_enum` | 触发摘要 |
| --- | --- | --- |
| 待登记 | `PLANNED` | **仅**门诊医生住院申请（`requestInpatientAdmission`） |
| 待入科 | `REGISTERED` | 住院登记完成（**含无申请直登**，登记后未分床）；转科确认后回到待入科 |
| 已入科 | `IN_PROGRESS` | **仅**护士分床成功后（有申请与直登路径一致） |
| 待转科 / 待出院 | `ON_HOLD` | 医生开立**转科**或**出院**医嘱后进入过渡；**同一就诊不会同时存在两种医嘱**；展示「待转科」或「待出院」由**当前有效医嘱类型**判定，**不是**两个不同 `status_enum` |
| 待结算 | `PENDING` | 护士执行完出院操作后 |
| 已结算 | `COMPLETED` | 住院收费结算完成（**财务终态**） |
| 已出院 | `DISCHARGED` | 护士清床、彻底离院（**业务终态**） |
| 取消登记 | `CANCELLED` | 住院登记取消（**仅** `PLANNED` / `REGISTERED`，且预交金余额为 0） |
| **出院召回（逆向）** | **`DISCHARGED→IN_PROGRESS`**（+ INSERT 活动 WARD，**无**活动床） | **AS-IP-FIN-004** **IF-IP-RCL-01**（收银台已结算历史）；先反结内核再 §6.1.25 |
| **未清床反结（逆向）** | **`COMPLETED→IN_PROGRESS`** | **AS-IP-FIN-004** IF-IP-RVS-01：`revertInpatientFinancialSettlement` / `recallFromCompletedToInProgress`；**仍占床**；须重新校对出院嘱并办理出院后再结算；**非**召回 |

**终态与在院收费：**

- **业务终态**：`DISCHARGED`（人、床、住院线结束）。
- **财务终态**：`COMPLETED`（结算完成，人仍可仍在院待清床）。
- **预交金（AS-IP-FIN-002）**：仅允许在 **`COMPLETED` 之前**（即 `REGISTERED` / `IN_PROGRESS` / `ON_HOLD` / `PENDING`）收取或退全部可退预交金；`PLANNED` 走登记首笔；`COMPLETED` / `DISCHARGED` / `CANCELLED` **禁止收退**。队列只读：`COMPLETED`/`DISCHARGED`/`CANCELLED`（退院）须存在已收口预交金流水才入「不在院/全部」队列（**AS-IP-FIN-002** BR-IP-PREP-001）。
- **出院结算轨临床/费用写收口（IMP `PENDING` 之后）**：就诊 `status_enum ∈ {PENDING, COMPLETED, DISCHARGED}` 时，**禁止**医嘱开立/签发/签退、医嘱执行/不执行/取消执行、护士划价/补费/取消划价、皮试保存计费（首次）等**一切变更医嘱或产生/修改账单**的写路径；**仅** `IN_PROGRESS` / `ON_HOLD` 允许（`ON_HOLD` 待出院窗口仍可执行/划价，见 **SYS-IMP-009 GAP-FLW-002**）。门禁看 **就诊态**，**不**看 `ChargeItem.status_enum`（在院患者名下可有大量 `PENDING_SETTLEMENT` 费用，**不**触发本门禁）。统一提示：「患者已办理出院或已结算，不可变更医嘱及费用」；实现锚点 `InpatientPendingSettlementClinicalGateSupport`（**SYS-IMP-009 BR-IP-FLW-008**）。

> **状态枚举说明**：
>
> - **code** 与实现 `EncounterStatus#getCode()` **字符串**一一对应；表中 `value` 列与实现内 **sortOrder** 对齐（便于排序展示，非另一套业务码）。
> - **门诊（AMB）**：`PLANNED` = 待挂号（未结清挂号费）；`REGISTERED` = 已挂号（结清后）；**禁止**在 `PLANNED` 下执行门诊接诊（`assertReceptionAllowed`）；门诊分诊须在 **`REGISTERED`** 后方可执行（`assertTriagedFromPlanned`）；**禁止**在 `PLANNED` 下执行门诊退号（`assertCancelable`，须已进入 **`REGISTERED`/`TRIAGED`**）；**已接诊/暂离/完诊**须先 `cancelOutpatientReception` 回退待诊后方可退号。
> - **住院（IMP）**：完整语义 **仅**见 §2.3.1；**展示文案**见 §2.3.2；`PRE_ADMISSION` 不在本期主链路使用。
> - **未启用分诊的门诊机构**：仍须先 **`PLANNED`→`REGISTERED`**（结清挂号费），再由 `REGISTERED` 直接进入 `IN_PROGRESS` 接诊，**不得**从 `PLANNED` 直接接诊。

#### 2.3.2 住院（`IMP`）状态展示文案专规（必读）

> **硬约束**：`EncounterStatus` 枚举上的 **`display`/`info` 为门诊通用码表文案**（如 `PLANNED`→「待挂号」、`REGISTERED`→「已挂号」、`IN_PROGRESS`→「在诊」）。**凡 `class_enum = IMP` 的记录**，前端/API/QueryService列 `status_enum_text` **禁止**直接取 `EncounterStatus#getDisplay()` 或 `EnumUtil#getInfo()`。
>
> **唯一解析入口（成码）**：
>
> - 共享常量：`com.zxplat.domain.share.constants.InpatientEncounterStatusDisplayConstants`
> - 共享工具：`com.zxplat.domain.share.utils.InpatientEncounterStatusDisplayUtil#resolveDisplayText` / `#resolveDisplayTextForQueryService`
>
> **落点（须一致）**：

| 落点 | 说明 |
| --- | --- |
| `EncounterQueryService`（住院队列展示） | `InpatientEncounterStatusDisplayUtil` 写入 `status_enum_text`；`status_enum` 来自查询时加载的 `adm_encounter` 或事件载荷 |
| `InpatientInfoQueueDisplayAssembler` 及登记/预交金/护士站 Mapper | 列表/卡片出参 `statusEnumText` 优先经 `InpatientEncounterStatusDisplayUtil` 解析；QueryService存量错误文案可被覆盖 |
| 领域事件载荷 | 事件 JSON 中 `statusEnum` 仍为 code；**不要求**在事件中重复携带 IMP 展示文案（展示在消费侧统一解析） |

**`status_enum` → 产品展示文案（IMP 专规，与 §2.3.1 产品阶段对齐）**

| `status_enum` | 展示文案（`InpatientEncounterStatusDisplayConstants`） | 备注 |
| --- | --- | --- |
| `PLANNED` | 待登记 | 仅医生住院申请 |
| `REGISTERED` | 待入科 | 登记完成、未分床；转科确认后回待入科 |
| `IN_PROGRESS` | 已入科 | 护士分床后 |
| `ON_HOLD` | 待转科 **或** 待出院 | 同一码；**须**结合QueryService `transfer_org_id` / `discharge_type_enum` 区分（见下行） |
| `PENDING` | 待结算 | 护士出院执行后 |
| `COMPLETED` | 已结算 | 财务终态 |
| `DISCHARGED` | 已出院 | 业务终态 |
| `CANCELLED` | 取消登记 | 住院登记取消 |
| `PRE_ADMISSION` | 预入院 | 扩展位，本期主链路一般不用 |
| `ENTERED_IN_ERROR` | 错误录入 | 误操作标记 |

**`ON_HOLD` 展示分流（QueryService辅助列）**

| 条件 | 展示文案 |
| --- | --- |
| `status_enum = ON_HOLD` 且 `transfer_org_id` 非空 | 待转科 |
| `status_enum = ON_HOLD` 且 `discharge_type_enum` 非空（且无转科目标） | 待出院 |
| 仅 `ON_HOLD`、无上述辅助列 | 默认 **待出院**（与 §2.3.1「由当前有效医嘱类型判定」一致；QueryService须在转科发起时写入 `transfer_org_id`） |

**与门诊同码对照（禁止混用展示）**

| code | 门诊 `EncounterStatus.display`（勿用于 IMP） | IMP 展示（§2.3.2） |
| --- | --- | --- |
| `PLANNED` | 待挂号 | 待登记 |
| `REGISTERED` | 已挂号 | 待入科 |
| `IN_PROGRESS` | 在诊 | 已入科 |
| `DISCHARGED` | 完诊 | 已出院 |
| `CANCELLED` | 已取消 | 取消登记 |

## 3. 实体

```
Encounter（聚合根）
════════════════════════════════════════════════════════════

  ┌──────────────────────────────────────────────────────┐
  │                   Encounter                          │
  │                                                      │
  │  - patientId / busNo / classEnum / statusEnum        │
  │  - startTime / endTime / organizationId              │
  │  - serviceTypeId / receptionTime                     │
  │                                                      │
  │  ┌──────────────────────────────────────────────┐    │
  │  │ EncounterParticipant（实体，0..N）            │    │
  │  │ - typeCode / practitionerId                  │    │
  │  │ - statusEnum / startTime / endTime           │    │
  │  └──────────────────────────────────────────────┘    │
  │                                                      │
  │  ┌──────────────────────────────────────────────┐    │
  │  │ EncounterLocation（实体，0..N）               │    │
  │  │ - locationId / formEnum / statusEnum         │    │
  │  │ - startTime / endTime                        │    │
  │  └──────────────────────────────────────────────┘    │
  │                                                      │
  │  ┌──────────────────────────────────────────────┐    │
  │  │ EncounterDiagnosis（实体，0..N）              │    │
  │  │ - conditionId / maindiseFlag / diagSrtNo     │    │
  │  │ - diagnosisDesc / iptDiseTypeCode            │    │
  │  └──────────────────────────────────────────────┘    │
  │                                                      │
  │  ┌──────────────────────────────────────────────┐    │
  │  │ EncounterReason（实体，0..N）                 │    │
  │  │ - useCode / valueTable / valueId / note      │    │
  │  └──────────────────────────────────────────────┘    │
  │                                                      │
  └──────────────────────────────────────────────────────┘
```

### 3.1 就诊（Encounter）

Encounter 聚合根的属性定义，对应 `adm_encounter` 表。


| 属性       | 字段名                   | 类型                       | 必填  | 说明                                 |
| -------- | --------------------- | ------------------------ | --- | ---------------------------------- |
| 患者标识     | `patient_id`          | `PatientId`              | 是   | 关联患者聚合根的 ID                        |
| 就诊分组标识   | `group_id`            | `Long`                   | 否   | 就诊分组或事件分组 ID                       |
| 就诊业务号    | `bus_no`              | `String`                 | 是   | 全局唯一；**门诊**与**住院**分序列发号（前缀不同，如 `EN` vs `ZY`，见 `AggregateTypeConstants`） |
| 就诊状态     | `status_enum`         | `EncounterStatus`（值对象）   | 是   | 见 §2.2 状态机                         |
| 就诊类别     | `class_enum`          | `EncounterClass`（值对象）    | 是   | 见 §4.1                             |
| 医保类别编码   | `yb_class_enum`       | `String`                | 否   | 医保类别编码                             |
| 医保类别文本   | `yb_class_text`       | `String`                 | 否   | 医保类别展示文本                           |
| 类别扩展     | `class_json`          | `String`                 | 否   | 类别扩展 JSON                          |
| 优先级      | `priority_enum`       | `EncounterPriority`（值对象） | 否   | 见 §4.2                             |
| 就诊类型     | `type_enum`           | `String`                | 否   | 更细粒度的就诊类型编码                        |
| 号源类型标识   | `service_type_id`     | `Long`                   | 否   | 服务类型或号源类型 ID（关联 HealthcareService） |
| 就诊对象状态   | `subject_status_enum` | `SubjectStatus`（值对象）     | 否   | 见 §4.3                             |
| 就诊开始时间   | `start_time`          | `DateTime`               | 否   | 接诊时设置                              |
| 就诊结束时间   | `end_time`            | `DateTime`               | 否   | 完诊/出院时设置                           |
| 机构科室标识   | `organization_id`     | `DepartmentId`           | 是   | 机构、院区或科室 ID                        |
| 显示顺序     | `display_order`       | `Integer`                | 否   | 当日序号                               |
| 初复诊标识    | `first_enum`          | `String`                | 否   | 初诊/复诊标识                            |
| 入院来源编码   | `admit_source_code`   | `String`                 | 否   | 入院类型编码（术语 `admit_source`；如 **6**=常规）；**与** `in_way_code` **独立** |
| 入院途径编码   | `in_way_code`         | `String`                 | 否   | 入院途径（术语 `in_way_code`）；与医保 **adm_way** / 病案 **adm_way_code** 同口径：**1** 急诊、**2** 门诊、**3** 其他医疗机构转入、**9** 其他 |
| 接诊时间     | `reception_time`      | `DateTime`               | 否   | 正式接诊时间                             |
| 登记员标识    | `registrar_id`        | `Long`                   | 否   | 登记员 ID                             |
| 关联门诊就诊标识 | `amb_encounter_id`    | `EncounterId`            | 否   | 门诊转住院场景下的原门诊就诊 ID；无申请直登可为空          |
| 出院方式编码   | `out_way_code`        | `String`                 | 否   | 出院方式编码                             |
| 费用性质意向   | `charge_intent`       | `String`                 | 否   | 医生转住院意向：`INSURANCE`=医保 / `SELF_PAY`=自费（`ChargeIntent` code）；供登记窗口未开户时预填费用性质（医保默认市医保）；**非**真实医保入院或已开户合同，提交真源仍以款台 `contractId` 为准 |

### 3.2 就诊参与者（EncounterParticipant）

记录就诊过程中参与的医务人员及其角色与时间。


| 属性     | 字段名               | 类型               | 必填  | 说明                                  |
| ------ | ----------------- | ---------------- | --- | ----------------------------------- |
| 参与者标识  | `id`              | `Long`           | 是   | 聚合内唯一，主键                            |
| 就诊标识   | `encounter_id`    | `EncounterId`    | 是   | 外键，关联 Encounter（`adm_encounter.id`） |
| 角色编码   | `role_code`       | `String`         | 是   | 参与者角色码，见 §3.2.1                     |
| 医务人员标识 | `practitioner_id` | `PractitionerId` | 是   | 外键，关联 Practitioner                  |
| 活动状态   | `status_enum`     | `String`        | 是   | `ACTIVE`（活动中）/ `COMPLETED`（已完成）     |
| 参与开始时间 | `start_time`      | `DateTime`       | 否   | 参与开始时间                              |
| 参与结束时间 | `end_time`        | `DateTime`       | 否   | 终态动作时补齐                             |


#### 3.2.1 参与者角色编码


| 编码值 | 英文                    | 说明         |
| --- | --------------------- | ---------- |
| 1   | `REGISTRATION_DOCTOR` | 挂号时指定的受理医生 |
| 2   | `ADMITTER`            | 实际接诊的医生    |
| 3   | `ATTENDING_DOCTOR`    | 负责治疗的主治医生  |
| 4   | `CHIEF_DOCTOR`        | 上级查房/会诊主任  |
| 5   | `PRIMARY_NURSE`       | 住院责任护士     |
| 6   | `TRIAGE_NURSE`        | 门诊分诊护士  |


> **存储口径**：对应子表字段 `role_code`，使用字符串编码存储（存枚举 `code`，与 `com.zxplat.domain.share.enums.EncounterParticipantRole#getCode()` 一致）。
>
> **共享枚举（成码）**：`zxplat-domain` → `EncounterParticipantRole`（`EnumInterface`）；应用层展示名经 `EncounterParticipantRoleDisplayUtil` / `EnumUtil#fromCode` 取 `getDisplay()`。

### 3.3 就诊位置（EncounterLocation）

记录就诊过程中患者的位置轨迹。


| 属性     | 字段名            | 类型                  | 必填  | 说明                                              |
| ------ | -------------- | ------------------- | --- | ----------------------------------------------- |
| 位置标识   | `id`           | `Long`              | 是   | 聚合内唯一，主键                                        |
| 就诊标识   | `encounter_id` | `EncounterId`       | 是   | 外键，关联 Encounter（`adm_encounter.id`）             |
| 位置资源标识 | `location_id`  | `Long`              | 是   | 外键，关联 Location（床位/诊室/病区）                        |
| 位置形态   | `form_enum`    | `LocationForm`（值对象） | 否   | 见 §4.4                                          |
| 占用状态   | `status_enum`  | `String`           | 是   | `planned` / `active` / `reserved` / `completed` |
| 占用开始时间 | `start_time`   | `DateTime`          | 否   | 占用开始时间                                          |
| 占用结束时间 | `end_time`     | `DateTime`          | 否   | 位置切换时对旧记录补齐                                     |


### 3.4 就诊原因（EncounterReason）

记录就诊原因、主诉与补充说明，只追加不删除。


| 属性     | 字段名            | 类型            | 必填  | 说明                                  |
| ------ | -------------- | ------------- | --- | ----------------------------------- |
| 原因标识   | `id`           | `Long`        | 是   | 聚合内唯一，主键                            |
| 就诊标识   | `encounter_id` | `EncounterId` | 是   | 外键，关联 Encounter（`adm_encounter.id`） |
| 原因用途编码 | `use_code`     | `String`      | 是   | 原因用途编码                              |
| 原因来源表名 | `reason_table` | `String`      | 否   | 具体原因类型来源表名                          |
| 原因值标识  | `reason_id`    | `Long`        | 否   | 原因 ID                               |
| 原因描述   | `description`  | `String`      | 否   | 原因描述/备注                             |


> 原因记录只追加不删除；如需更正，新增一条标记"更正"的记录并保留原记录用于审计。

### 3.5 就诊诊断关联（EncounterDiagnosis）

就诊与诊断之间的关联实体，承载主诊排序与医保字段。诊断本体由 Clinical 域（Condition 聚合）维护。


| 属性       | 字段名                  | 类型            | 必填  | 说明                                  |
| -------- | -------------------- | ------------- | --- | ----------------------------------- |
| 诊断关联标识   | `id`                 | `Long`        | 是   | 聚合内唯一，主键                            |
| 就诊标识     | `encounter_id`       | `EncounterId` | 是   | 外键，关联 Encounter（`adm_encounter.id`） |
| 诊断标识     | `condition_definition_id` | `Long`        | 是   | 诊断目录主键（`cln_condition_definition.id`）；**不再**关联 `cln_condition`（见 DD-诊断绑定改造） |

| 主诊断标记    | `primary_flag`       | `Boolean`     | 否   | 同一就诊最多一条**未软删**主诊；软删关联时须同时卸主诊（`primary_flag=false`） |
| 诊断排序号    | `rank`               | `Integer`     | 否   | 同一就诊内不得重复                           |
| 诊断描述     | `description`        | `String`      | 否   | 诊断描述文本                              |
| 住院疾病诊断类型 | `diag_type_code`     | `String`      | 否   | 术语 `ipt_dise_type_code` 的 **code** 字符串（如入院诊断 `31`）；存 `IptPatientDiseaseDiagType#getCode()`，禁止整型身份 |
| 住院疾病对应码  | `diag_code`          | `Integer`     | 否   | 住院患者疾病诊对应码                          |
| 入院病情代码   | `condition_code`     | `Integer`     | 否   | 入院疾病病情代码                            |
| 医疗类型     | `med_type`           | `String`      | 否   | 医保医疗类别；存 `YbMedType#getCode()`（如 `11` 普通门诊）；门诊开诊断未传时默认 `GENERAL_OUTPATIENT` |
| 最高诊断依据标记 | `highest_diag_flag`  | `Boolean`     | 否   | 最高诊断依据标记                            |
| 中医证候组号   | `tcm_syndrome_group` | `String`      | 否   | 中医证候组号                              |
| 中医标识     | `tcm_flag`           | `Boolean`     | 否   | `true`=中医诊断，`false`=西医诊断       |

> **入院/登记诊断类型（BR-ENC-021）**：经 `saveAdmissionDx`（`requestInpatientAdmission`、`registerInpatientEncounter`、`registerDirectInpatientEncounter`）写入的 `EncounterDiagnosis`，`diag_type_code` **由领域固定**为 `IptPatientDiseaseDiagType.ADMISSION`（持久化码 **`31`**，展示「入院诊断」）。`AdmissionConditionItem` **不含** `diagTypeCode`；应用层 Request/Mapper **不传**该字段。
>
> **门诊医生站诊断类型（BR-OP-DOD-011）**：门诊医生站保存（`batchUpsertEncounterDiagnosisAssociation`）写入的 `EncounterDiagnosis`，`diag_type_code` **固定**为 `IptPatientDiseaseDiagType.OUTPATIENT`（**`2`**，门诊诊断）；Request **不传**、不覆盖。
>
> **住院医生站诊断类型**：住院医生站保存仍可由前端传入 `iptDiseaseTypeCode`（可空）。

## 4. 值对象

### 4.1 就诊类别（EncounterClass）


| value | code   | info | 语义  |
| ----- | ------ | ---- | --- |
| 1     | IMP    | 住院   | —   |
| 2     | AMB    | 门诊   | —   |
| 3     | OBSENC | 留观   | —   |
| 4     | **PE** | **健康体检** | 一次完整体检周期；进度见 [Peis 04 §3.2](../../../../应用/zxplat-application/PeisAppService/04-FHIR领域模型.md#32-体检进度多模型联合不扩展-encounterstatus_enum) |

> **成码状态（2026-07-19）**：`EncounterClass.PE`（`code=PE`，`info=健康体检`）已落地于 `zxplat-domain`；追踪：[Peis 07-成码前Checklist §7](../../../../应用/zxplat-application/PeisAppService/07-成码前Checklist.md#7-代码现状2026-07-19)。


> **存储口径**：对应主表字段 `class_enum`，使用**字符串编码存储（存枚举 code）**。

### 4.2 优先级（ActPriority）


| value | code | info    | 语义        |
| ----- | ---- | ------- | --------- |
| 1     | A    | 尽快      | ASAP      |
| 2     | CR   | 回调结果    | —         |
| 3     | EL   | 择期      | Elective  |
| 4     | EM   | 急救      | Emergency |
| 5     | P    | 术前      | Preop     |
| 6     | PRN  | 按需      | As Needed |
| 7     | R    | 常规      | Routine   |
| 8     | RR   | 加急报告    | —         |
| 9     | S    | 最高优先级   | Stat      |
| 10    | T    | 时间关键    | —         |
| 11    | UD   | 遵医嘱     | —         |
| 12    | UR   | 紧急      | Urgent    |
| 13    | CS   | 回调安排    | —         |
| 14    | CSP  | 回调申请者安排 | —         |
| 15    | CSR  | 联系接受者安排 | —         |


> **编码对齐**：完整 HL7 ActPriority 编码，实现 `ActPriority` 枚举 15 值。
> **存储口径**：对应主表字段 `priority_enum`，使用**字符串编码存储（存枚举 code）**。
> **优先级规则**：数值越小越优先（1最优先，15最不优先）

### 4.3 就诊对象状态（EncounterSubjectStatus）


| value | code           | info | 语义  |
| ----- | -------------- | ---- | --- |
| 1     | ARRIVED        | 已到达  | —   |
| 2     | TRIAGED        | 已分诊  | —   |
| 3     | RECEIVING_CARE | 已看诊  | —   |
| 4     | ON_LEAVE       | 已离开  | —   |
| 5     | DEPARTED       | 已完成  | —   |


> **存储口径**：对应主表字段 `subject_status_enum`，使用**字符串编码存储（存枚举 code）**。

### 4.4 位置形态（LocationForm）


| value | code | info | 语义         |
| ----- | ---- | ---- | ---------- |
| 1     | SI   | 集合点  | Site       |
| 2     | BU   | 建筑   | Building   |
| 3     | WI   | 连廊   | Wing       |
| 4     | WA   | 病区   | Ward       |
| 5     | LVL  | 楼层   | Level      |
| 6     | CO   | 走廊   | Corridor   |
| 7     | RO   | 诊室   | Room       |
| 8     | BD   | 床    | Bed        |
| 9     | VE   | 运输工具 | Vehicle    |
| 10    | HO   | 病房   | House      |
| 11    | CA   | 药库   | Cabinet    |
| 12    | RD   | 路    | Road       |
| 13    | AREA | 区域   | Area       |
| 15    | VI   | 虚拟   | Virtual    |
| 16    | PH   | 药房   | Pharmacy   |
| 17    | WH   | 仓库   | Warehouse  |
| 18    | DE   | 科室   | Department |


> **说明**：就诊位置轨迹中常用的形态为病区(4)、诊室(7)、床(8)。完整枚举与 `Location` 域共用。
> **存储口径**：对应子表字段 `form_enum`，使用**字符串编码存储（存枚举 code）**。

### 4.5 位置占用状态（EncounterActivityStatus）


| value | code      | info | 语义  |
| ----- | --------- | ---- | --- |
| 1     | PLANNED   | 已安排  | —   |
| 2     | ACTIVE    | 使用中  | —   |
| 3     | RESERVED  | 已保留  | —   |
| 4     | COMPLETED | 已完成  | —   |


> **说明**：用于 `EncounterLocation.statusEnum` 和 `EncounterParticipant.statusEnum`。

### 4.6 就诊状态快照（EncounterStatusSnapshot）

联合封装 `就诊类别 + 就诊状态 + 就诊类型` 的联合语义，避免散落的枚举裸值判断。


| 属性   | 类型                | 说明               |
| ---- | ----------------- | ---------------- |
| 就诊类别 | `EncounterClass`  | AMB / IMP |
| 就诊状态 | `EncounterStatus` | 当前状态             |
| 就诊类型 | `String`         | 细粒度类型            |


> **核心方法**：
>
> - `isTerminal()`：状态为 `DISCHARGED`、`CANCELLED` 或 `ENTERED_IN_ERROR` 时返回 true
> - `isOutpatient()`：类别为 AMB
> - `isInpatient()`：类别为 IMP
> - `canStartReceptionDirectly()`：**门诊**须为 `AMB + REGISTERED`（未分诊机构从已挂号态直接接诊）。禁止 `AMB + PLANNED` 直出接诊。
> - 所有状态迁移方法应先通过此快照判断当前是否终止态，再决定是否允许继续操作

## 5. 领域事件

### 5.1 事件定义


| 事件名称     | 英文名称                                         | 触发时机        | 携带数据                              | 消费方        |
| -------- | -------------------------------------------- | ----------- | --------------------------------- | ---------- |
| 门诊就诊已计划  | `OutpatientRegistrationPlannedEvent`         | 开立门诊就诊成功    | 写模型初态 **`AMB + PLANNED`（待挂号）**。**锚点**：就诊标识、患者标识、号源类型标识、机构科室标识、就诊业务号；**展示快照**（由应用经命令装配、领域落事件）：患者业务号/姓名/**拼音检索码 `pyStr`/五笔检索码 `wbStr`**（与患者主档口径一致，供门诊信息列表关键字查询）/性别/电话/出生日期/年龄文、挂号科室与号源名称、开始时间与展示序、挂号员、`registrationDoctorId`/`registrationDoctorName`（来自开立命令中的意向医师主键与姓名，可空）、挂号单号、就诊类别编码；**`statusEnum`（初始态 `PLANNED`，由 `EncounterDomainService` 取聚合根当前状态写入，QueryService从事件读取，禁止硬编码）** | 号源、**挂号列表/挂号单/门诊信息**等领域事件后查询刷新、统计  |
| 门诊挂号缴费已确认 | `OutpatientRegistrationConfirmedEvent`       | `completeOutpatientRegistration` 写模型持久化成功后 | **锚点**：就诊标识（`aggregateId`）、患者标识；**`statusEnum`**（与 `adm_encounter.status_enum` 一致，典型 `REGISTERED`，由领域服务取聚合根写入，QueryService从事件读取，禁止硬编码） | **挂号列表/挂号单/门诊信息**等QueryService就诊展示状态QueryService 刷新；与支付域 `OutpatientPaymentSettledEvent` 解耦 |
| 门诊接诊已开始  | `OutpatientReceptionStartedEvent`            | 门诊接诊成功      | 就诊标识, **`doctorId`（接诊医师主键）**, **`doctorName`（接诊医师姓名展示快照）**, 接诊时间；**对外事件载荷与QueryService/QueryService 字段同名**，禁止使用 `practitionerId`/`practitionerName` 等异名键替代 | 医生站、叫号、大屏、**挂号列表等QueryService**  |
| 门诊接诊已取消  | `OutpatientReceptionCancelledEvent`          | 取消门诊接诊成功    | 就诊标识, 患者标识, `cancelledAt`；**`statusEnum`**（回退后典型 `REGISTERED`，由领域取聚合根写入） | 医生站队列、挂号列表等QueryService |
| 门诊就诊已暂停  | `OutpatientEncounterOnHoldEvent`             | 门诊暂离成功      | 就诊标识                              | 队列系统、QueryService   |
| 门诊就诊已完成  | `OutpatientEncounterCompletedEvent`          | 门诊完诊成功      | 就诊标识, 患者标识, 结束时间                  | 收费、统计、质控   |
| 门诊就诊已取消  | `OutpatientRegistrationCancelledEvent`       | 取消门诊就诊      | 就诊标识, 患者标识, 取消原因；**落库后发布**：`statusEnum`（取消后就诊状态码，与 `Encounter.statusEnum` 一致）、`cancelledAt`（退号业务时刻，命令项 `cancelAt` 缺省则取操作时刻） | 号源、退款编排、**门诊信息/挂号列表/挂号单 QueryService**、统计 |
| 诊断关联已维护  | `EncounterDiagnosisAssociationUpsertedEvent` | 新增或修改就诊诊断关联 | 就诊标识, 诊断标识, 主诊标记                  | 医嘱、病历、统计   |
| 诊断关联已移除  | `EncounterDiagnosisAssociationRemovedEvent`  | 删除就诊诊断关联    | 就诊标识, 诊断标识                        | 病历、质控      |
| 住院申请已发起  | `InpatientAdmissionRequestedEvent`           | 发起住院申请（**INSERT** 新 `IMP` 就诊） | 就诊标识、患者标识、拟收治科室标识、`ambEncounterId`、`busNo`、`classEnum`、`statusEnum`（`PLANNED`）、`inWayCode`、`priorityEnum`、意向病区（`wardId`，来自申请项 `wardLocationId`，可选） | 住院登记、住院QueryService |
| 住院预入院已开始 | `InpatientPreAdmissionStartedEvent`          | 进入预入院阶段     | 就诊标识, 预入院项目列表                     | 住院QueryService      |
| 住院登记已完成  | `InpatientAdmissionCompletedEvent`           | 完成住院登记      | 就诊标识, 患者标识, 入院科室, 登记员, 状态码, 入院时间, 业务号, 意向病区(`wardId`/`wardName` 可选) | 应用层 `EncounterContextReader` 住院队列刷新、`PatientQueryService` 人口学回查、账户、护理站   |
| 住院分床已完成  | `InpatientBedAssignedEvent`                  | 入科分床成功      | 就诊标识, 位置资源标识, 床位号, 患者标识, 患者姓名快照, 患者性别文本, 患者年龄文本, 主管医生标识, 入院时间 | 护理站、床位系统   |
| 转床已确认    | `BedTransferredEvent`                        | 确认转床成功      | 就诊标识, 旧位置标识, 新位置标识                | 床位展示、床旁系统  |
| 转科已发起    | `DepartmentTransferInitiatedEvent`           | 发起转科        | 就诊标识, 原科室标识, 目标科室标识               | 病区交接、床位管理  |
| 转科已确认    | `DepartmentTransferConfirmedEvent`           | 转科确认        | 就诊标识, 目标科室标识, 生效时间                | 住院管理、交接大屏  |
| 住院已出院    | `HospitalDischargedEvent`                    | 住院出院        | 就诊标识, 患者标识, 出院方式, 结束时间            | 结算、出院文书、统计 |
| 清床已完成    | `TerminalCleaningCompletedEvent`             | 清床完成        | 就诊标识, 床位标识                        | 床位可用状态、统计  |
| 住院登记已取消  | `InpatientAdmissionCancelledEvent`           | 取消住院登记      | 就诊标识, 取消原因                        | 住院QueryService、床位管理 |
| 护士站患者信息已保存 | `NurseStationPatientSavedEvent`              | 护士站保存病情/医护成功（**无状态迁移**） | 就诊标识、患者标识、`priorityEnum`（保存后）、`statusEnum`（未迁移）、`savedAt`、`operatorId` | **AS-IP-NUR-001** 详情/列表医护与病情展示；住院QueryService（QueryService 刷新待对齐） |
| 住院财务结算已完成 | `InpatientFinancialSettlementCompletedEvent` | 出院全结收费成功（**AS-IP-FIN-003**） | 就诊标识、患者标识、**`statusEnum`（典型 `COMPLETED`）**、`settlementTime` | 住院QueryService、预交金门禁、结算队列 |

> **命名约定（与QueryService 刷新对齐）**：`OutpatientReceptionStartedEvent` 的对外载荷使用 **`doctorId`/`doctorName`**；`EncounterParticipant` 子表仍用主数据引用字段（如 `practitionerId`）持久化活动接诊者，与命令/事件中的医师主键在接诊成功时应一致。开立阶段意向医师在事件中体现为 **`registrationDoctorId`/`registrationDoctorName`**（与命令项 `practitionerId`/`practitionerName` 映射）。

### 5.2 事件触发规则


| 当前状态                         | 操作        | 目标状态               | 发布事件                                   |
| ---------------------------- | --------- | ------------------ | -------------------------------------- |
| —                            | 开立门诊就诊    | AMB + PLANNED      | `OutpatientRegistrationPlannedEvent`   |
| AMB + PLANNED                | 门诊缴费确认（领域 `completeOutpatientRegistration`） | AMB + REGISTERED | `OutpatientRegistrationConfirmedEvent`（QueryService **`REGISTERED`** 展示由此事件驱动；支付域 `OutpatientPaymentSettledEvent` **不含**就诊状态，见 `DM-FIN-002` §5.1.1） |
| AMB + REGISTERED / TRIAGED   | 接诊        | AMB + IN_PROGRESS  | `OutpatientReceptionStartedEvent`      |
| AMB + REGISTERED             | 直接接诊（无分诊） | AMB + IN_PROGRESS  | `OutpatientReceptionStartedEvent`      |
| AMB + IN_PROGRESS / ON_HOLD / DISCHARGED | 取消接诊（医生同意退号） | AMB + REGISTERED | `OutpatientReceptionCancelledEvent` |
| AMB + INPROGRESS             | 暂离        | AMB + ONHOLD       | `OutpatientEncounterOnHoldEvent`       |
| AMB + ONHOLD                 | 恢复        | AMB + INPROGRESS   | —                                      |
| AMB + INPROGRESS / ONHOLD    | 完诊        | AMB + DISCHARGED   | `OutpatientEncounterCompletedEvent`    |
| AMB + REGISTERED / TRIAGED / IN_PROGRESS | 取消（退号） | AMB + CANCELLED | `OutpatientRegistrationCancelledEvent` |
| AMB + PLANNED | 未收费取消（窗口「取消」） | AMB + ENTERED_IN_ERROR | `OutpatientRegistrationCancelledEvent`（`statusEnum=ENTERED_IN_ERROR`） |
| —                            | 发起住院申请    | IMP + PLANNED      | `InpatientAdmissionRequestedEvent`     |
| IMP + PLANNED                | 预入院       | IMP + PREADMISSION | `InpatientPreAdmissionStartedEvent`    |
| IMP + PLANNED / PREADMISSION | 住院登记（有申请） | IMP + REGISTERED   | `InpatientAdmissionCompletedEvent`     |
| —                            | 无申请直登登记   | IMP + REGISTERED   | `InpatientAdmissionCompletedEvent`     |
| IMP + REGISTERED             | 分床        | IMP + IN_PROGRESS  | `InpatientBedAssignedEvent`            |
| IMP + INPROGRESS             | 转床        | IMP + INPROGRESS   | `BedTransferredEvent`                  |
| IMP + IN_PROGRESS（双方）    | 互换床      | IMP + IN_PROGRESS  | `BedTransferredEvent`（各一条）        |
| IMP + INPROGRESS             | 发起转科      | IMP + ONHOLD       | `DepartmentTransferInitiatedEvent`     |
| IMP + ONHOLD                 | 确认转科      | IMP + REGISTERED   | `DepartmentTransferConfirmedEvent`     |
| IMP + INPROGRESS             | 出院        | IMP + DISCHARGED   | `HospitalDischargedEvent`              |
| IMP + DISCHARGED             | 清床        | IMP + DISCHARGED   | `TerminalCleaningCompletedEvent`       |
| IMP + PLANNED / PREADMISSION | 取消        | IMP + CANCELLED    | `InpatientAdmissionCancelledEvent`     |


## 6. 领域服务

### 6.1 就诊领域服务（EncounterDomainService）

#### 6.1.1 开立门诊就诊（`registerOutpatientEncounter`）

- **业务意义**：确认一次门诊就诊成立，生成稳定 `EncounterId/busNo` 作为后续锚点。
- **边界说明**：只创建 Encounter 聚合事实，不处理号源占用、收费、支付等跨域编排。
- **入参/出参**：患者标识、号源类型标识、机构科室标识、意向接诊医师主键与**姓名展示快照**（可选，成对出现）；命令项另携带患者/科室/号源/挂号员等**展示快照**供事件物化；返回就诊标识。
- **前置条件**：患者有效；默认满足 BR-ENC-011 防重；命令 `skipSameDaySameDeptDuplicateCheck=true` 时跳过该防重（院点策略放宽，见 **BR-ENC-011**）；未指定医生时按 BR-ENC-012 落默认医生。
- **状态变化**：`AMB + PLANNED` 初始化。
- **聚合内影响**：新建就诊主档、写入业务键；当命令项 **`practitionerId` 非空**时，新建活动 **`REGISTRATION_DOCTOR`** 参与者（**禁止**在开立阶段写入 **`ADMITTER`**；接诊参与者由 **§6.1.4** `startOutpatientReception` 写入）。
- **事件输出**：`OutpatientRegistrationPlannedEvent`（展示快照含患者 **`pyStr`/`wbStr`**，由命令项装配、领域服务写入事件，供门诊信息领域事件后查询刷新与列表关键字查询；事件内意向医师字段名为 **`registrationDoctorId`/`registrationDoctorName`**，由命令项意向医师映射，与DM §6 查询契约一致；**`statusEnum` 由 `EncounterDomainService` 在发布前取聚合根当前状态写入，QueryService统一从事件读取，禁止QueryService侧硬编码**）。

#### 6.1.2 门诊缴费确认 / 挂号正式生效（`completeOutpatientRegistration`）

- **业务意义**：门诊挂号费结清后，将就诊从 **待挂号** 推进为 **已挂号**，与收费域支付成功语义衔接。
- **边界说明**：仅 **`AMB`**。支付域 `OutpatientPaymentSettledEvent` **仅**表达收银结清，**不**携带就诊生命周期状态（见 `DM-FIN-002` §5.1）。QueryService 侧「已挂号」展示必须由就诊域事件驱动，避免普通门诊收银误推进挂号相关QueryService 刷新。
- **入参/出参**：就诊标识。
- **前置条件**：当前 `status_enum=PLANNED`；聚合根 `transitToRegistered()` 内断言。
- **状态变化**：`PLANNED -> REGISTERED`。
- **聚合内影响**：更新主档状态。
- **事件输出**：`OutpatientRegistrationConfirmedEvent`（**落库成功后**发布；载荷 **`statusEnum`** 取自聚合根当前状态，典型 `REGISTERED`；QueryService从事件读取并经 `EnumUtil` 派生展示文本，禁止硬编码；发布时机遵循工程 **B.14**）。

#### 6.1.4 门诊接诊（`startOutpatientReception`）

- **业务意义**：声明接诊开始，进入诊疗执行态。
- **边界说明**：只更新就诊与参与者，不展开医生站流程编排；**接诊命令须携带 `doctorId` 与 `doctorName` 展示快照**（由应用层解析主数据后写入命令项），领域发布 `OutpatientReceptionStartedEvent` 时一并落事件，供QueryService 刷新与挂号列表等消费。
- **前置条件**：就诊须为 **`AMB + REGISTERED/TRIAGED`**；**禁止 `AMB + PLANNED` 接诊**（`assertReceptionAllowed`）；无分诊机构时须 **`REGISTERED`** 后直接接诊。
- **状态变化**：`REGISTERED/TRIAGED -> IN_PROGRESS`。
- **聚合内影响**：写接诊时间，关闭旧接诊医生并新增当前接诊医生。
- **事件输出**：`OutpatientReceptionStartedEvent`。

#### 6.1.5 门诊暂离（`markOutpatientLeave`）

- **业务意义**：表达诊疗临时中断但未终止。
- **边界说明**：只改状态，不触发终态收口逻辑。
- **前置条件**：就诊须为 `AMB + IN_PROGRESS`。
- **状态变化**：`IN_PROGRESS -> ON_HOLD`。
- **聚合内影响**：更新主档状态。
- **事件输出**：`OutpatientEncounterOnHoldEvent`。

#### 6.1.6 门诊完诊（`completeOutpatientEncounter`）

- **业务意义**：确认门诊诊疗结束并收口。
- **边界说明**：只做 Encounter 终态收口，不处理结算编排。
- **前置条件**：就诊须为 `AMB + IN_PROGRESS/ON_HOLD`；满足 BR-ENC-004。
- **状态变化**：`IN_PROGRESS/ON_HOLD -> DISCHARGED`。
- **聚合内影响**：收口接诊参与者并补齐结束时间。
- **事件输出**：`OutpatientEncounterCompletedEvent`。

#### 6.1.6a 取消门诊接诊（`cancelOutpatientReception`）

- **业务意义**：医生同意退号——将已接诊（含暂离、完诊）回退为已挂号待诊，供收费窗口执行退号；**与账单无关**。
- **边界说明**：本聚合**只**更新就诊状态与接诊参与者。本诊病历/诊断/医嘱须已删净的门禁由应用层 **AS-OP-DOR-001 IF-DOR-06 Pre（BR-DOR-011）** 跨聚合校验；领域方法**不**自动删临床数据、**不**跨聚合读药嘱/文书。
- **前置条件**：`AMB + IN_PROGRESS/ON_HOLD/DISCHARGED`（`assertCancelReceptionAllowed`）；完诊为业务终态时豁免终止态拦截（对齐重新接诊路径）。应用层另要求临床痕迹已空。
- **状态变化**：`IN_PROGRESS/ON_HOLD/DISCHARGED -> REGISTERED`；清空 `receptionTime`/`endTime`。
- **聚合内影响**：收口活动 `ADMITTER` 参与者。
- **事件输出**：`OutpatientReceptionCancelledEvent`。

#### 6.1.7 取消门诊就诊（`cancelOutpatientRegistration`）

- **业务意义**：确认本次门诊就诊取消，不再继续后续流程（产品口语「退号」）。
- **边界说明**：只终止 Encounter，退款/号源释放由应用层或他域编排。
- **前置条件**：`assertCancelable`：**门诊**仅 **`REGISTERED`/`TRIAGED`**；**不含** `PLANNED`（待挂号）、**不含** `IN_PROGRESS`/`ON_HOLD`/`DISCHARGED`（须先 §6.1.6a 取消接诊，表示医生同意退号）。
- **状态变化**：满足前置的上述态 **`-> CANCELLED`**。
- **聚合内影响**：收口参与者并记录取消原因。
- **事件输出**：`OutpatientRegistrationCancelledEvent`（`statusEnum=CANCELLED`）。
- **展示约定**：**AS-OP-FIN-001** IF-REG-08 挂号列表对 `CANCELLED` 行回写 `statusText=退号`，**禁止**对收费窗展示枚举默认「已取消」。

#### 6.1.7a 作废待挂号（`cancelPlannedOutpatientRegistration`）

- **业务意义**：未收费待挂号（`PLANNED`）窗口「取消」：作废挂单事实，释放号源占用。
- **边界说明**：不走 §6.1.7 退号门禁；支付草稿作废、医保预结算撤销由 **AS-OP-FIN-001** 编排。
- **前置条件**：`AMB + PLANNED`。
- **状态变化**：`PLANNED -> ENTERED_IN_ERROR`（与退号 `CANCELLED` 分流）。
- **聚合内影响**：终止就诊；事件带取消原因。
- **事件输出**：`OutpatientRegistrationCancelledEvent`（`statusEnum=ENTERED_IN_ERROR`）。
- **列表约定**：IF-REG-08 **排除** `ENTERED_IN_ERROR`，收费窗不展示未收费取消行。

#### 6.1.7b 更正门诊挂号科室（`correctOutpatientRegistrationDepartment`）

- **业务意义**：挂号窗口把已生成的门诊就诊改到另一挂号科室（**AS-OP-FIN-001** IF-REG-15 / **BR-REG-023**）。
- **边界说明**：只写就诊 `organization_id`。不改状态、不改参与者、不重占号源、不发领域事件。挂号费归属科室由收费明细聚合另写。
- **前置条件**：`AMB`，且状态为 `PLANNED`/`REGISTERED`/`TRIAGED`/`IN_PROGRESS`/`ON_HOLD`。目标科室与当前不同。命令未置 `skipSameDaySameDeptDuplicateCheck` 时，按挂号日对目标科室执行 **BR-ENC-011**。
- **状态变化**：`N/A`。
- **聚合内影响**：更新 `organization_id`。
- **事件输出**：无。
- **失败语义**：缺参、状态不允许、科室未变化、同日同科已占名额时拒绝。

#### 6.1.8 维护就诊诊断关联（`upsertEncounterDiagnosisAssociation`）

- **业务意义**：维护就诊-诊断关联事实，支撑完诊/出院校验与展示。
- **边界说明**：仅维护关联，不决定 Condition 本体生命周期。
- **前置条件**：主诊唯一、排序不冲突（BR-ENC-014）。
- **状态变化**：`N/A`。
- **聚合内影响**：新增或更新 `EncounterDiagnosis`。
- **事件输出**：`EncounterDiagnosisAssociationUpsertedEvent`。

#### 6.1.9 批量维护就诊诊断关联（`batchUpsertEncounterDiagnosisAssociation`）

- **业务意义**：一次性收敛诊断集合级约束，避免逐条写入中间态不一致。
- **边界说明**：仅维护关联集合，不处理 Clinical 域诊断本体。
- **前置条件**：集合级主诊唯一与排序唯一校验通过。
- **状态变化**：`N/A`。
- **聚合内影响**：批量新增/更新 `EncounterDiagnosis`。
- **事件输出**：`EncounterDiagnosisAssociationUpsertedEvent`。
- **典型消费方（编排追溯）**：门诊医生站诊断全量保存 **IF-DOR-08**（`AS-OP-DOR-002`）由应用层组装 `BatchUpsertEncounterDiagnosisAssociationCommand` **直接**维护本关联（含 `conditionDefinitionId`）；**不再**先写 `ConditionDomainService#saveDoctorDiagnosis`。

#### 6.1.9a 批量维护就诊**中医**诊断关联（`batchUpsertTcmEncounterDiagnosisAssociation`）

- **业务意义**：中医 Tab **全量批次**保存病证组时，仅对 **`tcm_flag=true`** 的就诊诊断关联做集合对齐，**不误删**同就诊下西医诊断关联（**AS-OP-DOR-010 · BR-TCM-DIA-006**）。
- **边界说明**：与 §6.1.9 共用 `BatchUpsertEncounterDiagnosisAssociationCommand`；批次内每条诊断项须 `tcm_flag=true`，写入时强制 `tcm_flag=true`；**不**删除 `Condition` 本体。
- **前置条件**：集合级主诊唯一与排序唯一校验通过（同 §6.1.9）；批次项均为中医项。
- **状态变化**：`N/A`。
- **聚合内影响**：批量新增/更新本批中医关联；对同就诊下 **`tcm_flag=true` 且未再出现于本批** 的关联行软删；**`tcm_flag=false` 关联行保持不变**。
- **事件输出**：对本批活动关联逐条 `EncounterDiagnosisAssociationUpsertedEvent`（B.14 先落库后发事件）。
- **典型消费方（编排追溯）**：**AS-OP-DOR-010 · IF-DOR-32** 在 `ConditionDomainService#saveDoctorDiagnosisTcm` 落病/证事实后调用；**显式删组**仍须 IF-DOR-33 走 `removeEncounterDiagnosisAssociation` + `delEncounterDiagnosis`（本方法不删 Condition）。
- **互斥**：**禁止** IF-DOR-32 调用 §6.1.9 `batchUpsertEncounterDiagnosisAssociation`（全量软删会误伤西医关联）。

#### 6.1.9b 切换就诊主诊断（`switchPrimaryEncounterDiagnosis`）

- **业务意义**：医生在诊断列表上**单点**将指定就诊诊断行（或其所在中医病证组）设为主诊，并清空同就诊其余主诊标记，保证同一就诊仅一条/一组主诊（**BR-ENC-014**）。
- **边界说明**：仅改 `EncounterDiagnosis.primaryFlag`；不增删关联行、不写 Condition 本体；与全量批次保存改主诊（§6.1.9 / §6.1.9a）**并存**。
- **命令**：`SwitchPrimaryEncounterDiagnosisCommand`（`encounterId`、`encounterDiagnosisId`、`operatorId`）。
- **前置条件**：目标行存在、未删且归属本就诊；西医按单行切换，中医（`tcm_flag=true` 且 `tcm_syndrome_group` 非空）按病证组整组切换。
- **状态变化**：`N/A`。
- **聚合内影响**：目标行（或组内全部行）`primaryFlag=true`；同就诊其余活动行 `primaryFlag=false`；无变更时幂等返回。
- **事件输出**：对发生主诊标记变更的行逐条发布 `EncounterDiagnosisAssociationUpsertedEvent`（先落库后发事件）。
- **典型消费方（编排追溯）**：**AS-OP-DOR-002 · IF-DOR-09A**、**AS-IP-DOR-002 · IF-IP-DOR-07**（应用层 `switchMainEncounterDiagnosis`）。

#### 6.1.10 移除就诊诊断关联（`removeEncounterDiagnosisAssociation`）

- **业务意义**：撤销不再适用的就诊-诊断关联。
- **边界说明**：只影响关联关系，不删除 Condition 本体。
- **前置条件**：关联记录存在且可移除。
- **状态变化**：`N/A`。
- **聚合内影响**：逻辑删除 `EncounterDiagnosis`（`is_deleted=true`），并同步 `primary_flag=false`，使已删行不再占主诊名额（**BR-ENC-014**）；西医/中医全量批次软删旧行同口径。
- **事件输出**：`EncounterDiagnosisAssociationRemovedEvent`。
- **典型消费方（编排追溯）**：门诊医生站诊断删除 **IF-DOR-09** 在调用 `ConditionDomainService#delEncounterDiagnosis` **之前**先解除本就诊与待删诊断的关联，以满足「先关联、后事实」的跨聚合顺序（详见 `AS-OP-DOR-002-SDD` §3.6）；住院医生站医保→国临展开先本方法软删医保主诊再 upsert 国临主诊。

#### 6.1.11 发起住院申请（`requestInpatientAdmission`）

- **业务意义**：**新增**住院就诊（`IMP + PLANNED`），确认住院申请事实；**不修改**源门诊就诊。
- **边界说明**：写住院主档、申请参与者、可选意向病区（`WARD`）与入院诊断关联；不展开住院登记/收费窗口流程。
- **入参/出参**：`RequestInpatientAdmissionItemCommand` 含 `ambEncounterId`、`patientId`、`targetDepartmentId`、`requestDoctorId`、可选 `wardId`、`admitSourceCode`、`inWayCode`、`priorityCode`、`chargeIntent`、`admissionDiagnoses`（`List<AdmissionConditionItem>`）；返回新建住院 `encounterId`。
- **前置条件**（写侧内嵌，读侧可前置）：源就诊 `AMB` 且可转住院（**BR-IP-REQ-001**）；同一 `ambEncounterId` 无未取消住院线（**BR-IP-REQ-002**）；患者与组织信息有效。
- **状态变化**：初始化为 `IMP + PLANNED`。
- **聚合内影响**：写入 `amb_encounter_id`、住院专用 `bus_no`、可选 `charge_intent`（费用性质意向：医保/自费，供登记预填）、参与者、可选病区位置、就诊诊断关联（`diag_type_code=31` 入院诊断，**BR-ENC-021**；及按需新开 `Condition`）。**不**开立 Account、**不**触发医保入院。
- **事件输出**：`InpatientAdmissionRequestedEvent`（载荷含状态/业务号/可选 `wardId` 等，供QueryService 刷新从事件读取；与写侧 `EncounterLocation(WARD)` 一致）。

#### 6.1.12 住院预入院（`startPreAdmission`）

- **业务意义**：确认住院进入预入院阶段。
- **边界说明**：只改变 Encounter 阶段，不展开检查/准备业务处理。
- **前置条件**：就诊须为 `IMP + PLANNED`。
- **状态变化**：`PLANNED -> PRE_ADMISSION`。
- **聚合内影响**：记录预入院项目信息。
- **事件输出**：`InpatientPreAdmissionStartedEvent`。

#### 6.1.13 住院登记（`registerInpatientEncounter`）

- **业务意义**：确认住院登记落实，进入**待入科**（尚未分床）。
- **边界说明**：写住院主档、病区位置；账户开立由应用层编排 **DM-ADM-009**，不展开收费窗口流程。**入院诊断**：医生申请阶段（`requestInpatientAdmission`）已 `saveAdmissionDx` 时，本命令**不得**再次写入主诊断（若命令项携带 `admissionDiagnoses` 且住院线已有主诊，则跳过 `saveAdmissionDx`，满足 **BR-ENC-014**）；与 **AS-IP-FIN-001** **BR-IP-REG-007** 一致。
- **前置条件**：就诊须为 `IMP + PLANNED`（**仅**医生申请后）；`PRE_ADMISSION` 本期主链路不使用。
- **并发（GAP-ATD-004）**：写路径须 `PESSIMISTIC_WRITE` 加载就诊行，锁内 `assertInpatientRegistrationAllowed()` 后再 `transitToInpatientRegistered`；批量登记按 `encounterId` 升序加锁；同事务至 save 与事件发布。
- **状态变化**：`PLANNED -> REGISTERED`。
- **聚合内影响**：补齐住院主记录；病区：申请已写且 `wardLocationId` 不变则**保留**活动 WARD，变更则旧 WARD **COMPLETED** 后 **INSERT** 新 WARD（禁止重复两条活动 WARD）；记录登记时间与登记员；**不**覆盖申请已落入院诊断。
- **事件输出**：`InpatientAdmissionCompletedEvent`（载荷 `statusEnum` 典型为 `REGISTERED`）。

#### 6.1.13a 无申请直登登记（`registerDirectInpatientEncounter`）

- **业务意义**：收费窗口无医生申请时 **INSERT 并完成登记**，**不产生** `PLANNED` 态。
- **边界说明**：与 §6.1.16 共用 `RegisterInpatientEncounterCommand`（命令级 `patientId` 必填）；不发布 `InpatientAdmissionRequestedEvent`。**入院诊断**须在本命令内 `saveAdmissionDx` **唯一落库一次**（收费员自选目录新增，至少一条主诊断）。
- **前置条件**：患者与科室/病区有效；命令不含既有 `encounterId`；`admissionDiagnoses` 非空且含主诊断。
- **状态变化**：新建 `IMP + REGISTERED`。
- **聚合内影响**：开户、病区位置、入院诊断（本路径 `saveAdmissionDx` 写诊断，`diag_type_code` 固定入院诊断，**BR-ENC-021**）。
- **事件输出**：`InpatientAdmissionCompletedEvent`。

#### 6.1.14 住院分床（`assignInpatientBed`）

- **业务意义**：确定当前在院床位归属，并进入**已入科**。
- **边界说明**：Encounter 维护位置轨迹与**首次**责任医护参与者；床位 `operational_enum`（`IN_USE`/`NOT_IN_USE`）由 Location 域 `occupyBed`/`releaseBed` 负责（**AS-IP-NUR-002** 应用层编排）。
- **前置条件**：就诊须为 `IMP + REGISTERED`。
- **状态变化**：`REGISTERED -> IN_PROGRESS`。
- **聚合内影响**：**保留**活动 `WARD`；仅收口活动 `HOUSE`/`BED` 后 **INSERT** 新 `HOUSE` + `BED` 活动行（禁止原地 UPDATE）；可选更新 `priority_enum`；提交 `participants` 时收口**全部**活动参与者后批量 INSERT。
- **事件输出**：`InpatientBedAssignedEvent`。
- **命令**：`AssignInpatientBedCommand`（`operatorId`；子项：`encounterId`、**`houseLocationId`**、`locationId`（床）、`formEnum`、`bedCode`、`assignTime`、**`priorityEnum`**、**`participants`**）。

#### 6.1.15 确认转床（`confirmBedTransfer`）

- **业务意义**：已入科患者迁至**空床**，收敛当前床位位置轨迹。
- **边界说明**：只维护 Encounter 内位置；**不**新建参与者；床运营态由 Location 域与应用层编排。
- **前置条件**：就诊须为 `IMP + IN_PROGRESS`；`fromLocationId` 须与当前活动床位一致。
- **状态变化**：`N/A`（保持 `IN_PROGRESS`）。
- **聚合内影响**：仅收口活动 `HOUSE`/`BED` 后 INSERT 新 `HOUSE` + `BED`（**WARD** 不动）。
- **事件输出**：`BedTransferredEvent`。
- **命令**：`ConfirmBedTransferCommand`（子项：`encounterId`、`fromLocationId`、**`toHouseLocationId`**、`toLocationId`、`effectiveAt`、可选 `toFormEnum`、`toBedCode`）。

#### 6.1.15b 更正入院科室（`correctInpatientAdmissionDepartment`）

- **业务意义**：登记时选错入院科室，在收费窗口改正科室、病区和已入科床位（**AS-IP-FIN-001** IF-IP-REG-08）。
- **边界说明**：不进入转科 `ON_HOLD`，不写转科里程碑，不收口参与者。床运营态仍由应用层 `occupyBed`/`releaseBed`。已 2401 的医保变更在应用层、本命令之后调用。
- **前置条件**：就诊为 `IMP` 且 `REGISTERED` 或 `IN_PROGRESS`；已入科须带目标床；待入科不得带床。
- **状态变化**：`N/A`（保持原状态）。
- **聚合内影响**：更新 `organization_id`；活动 `WARD` 按需收口后 INSERT；已入科且床变化时收口活动 `HOUSE`/`BED` 后 INSERT 新病房+床。
- **事件输出**：仅换床时 `BedTransferredEvent`。
- **命令**：`CorrectInpatientAdmissionDepartmentCommand`（`encounterId`、`departmentId`、`wardId`、已入科换床时 `houseId`/`bedId`）。

#### 6.1.15a 互换床（`swapInpatientBeds`）

- **业务意义**：两名已入科患者**对调**当前活动床位（**AS-IP-NUR-002** BR-IP-ATD-005）。
- **边界说明**：原子收口双方活动位置并互写对方床位；**不**改参与者、**不**改就诊状态；发布**两条** `BedTransferredEvent`（消费方与单患者转床相同）。
- **前置条件**：双方均为 `IMP + IN_PROGRESS`；双方均存在唯一活动 `BED` 级位置。
- **状态变化**：`N/A`（双方保持 `IN_PROGRESS`）。
- **聚合内影响**：双方仅收口活动 `HOUSE`/`BED` 后交叉 INSERT 对方病房+床位（**WARD** 不动）。
- **事件输出**：`BedTransferredEvent` ×2。
- **命令**：`SwapInpatientBedsCommand`（`operatorId`；子项：`encounterIdA`、`encounterIdB`、**`toHouseLocationIdA`**、**`toHouseLocationIdB`**、`effectiveAt`）。

#### 6.1.16 发起转科（`initiateDepartmentTransfer`）

- **业务意义**：启动转科流程并进入过渡态。
- **边界说明**：只处理 Encounter 内位置/参与者收口与过渡，不展开病区交接业务细节。
- **前置条件**：就诊须为 `IMP + IN_PROGRESS`。
- **并发（GAP-ATD-006）**：`requireEncounterForUpdate` + `Encounter` `@Version`；`ENCOUNTER_INVALID_STATUS` → **EX-IP-AUD-203**（校对联动）。
- **状态变化**：`IN_PROGRESS -> ON_HOLD`。
- **聚合内影响**：关闭原病区位置和责任人，建立目标病区关系。
- **事件输出**：`DepartmentTransferInitiatedEvent`。

#### 6.1.17 确认转科（`confirmDepartmentTransfer`）

- **业务意义**：确认转科生效，回到在院执行态。
- **边界说明**：只确认 Encounter 内目标位置与生效时间。
- **前置条件**：就诊须为 `IMP + ON_HOLD`。
- **并发（GAP-ATD-006）**：`requireEncounterForUpdate` + `Encounter` `@Version`。
- **状态变化**：`ON_HOLD -> REGISTERED`（转科后待新科分床，**非**已入科）。
- **聚合内影响**：确认目标位置关系与生效时间。
- **事件输出**：`DepartmentTransferConfirmedEvent`。

#### 6.1.18 校对出院医嘱（`dischargeInpatientEncounter`）

- **业务意义**：医生开立出院医嘱并经护士校对后，就诊进入**待出院**过渡态（**AS-IP-NUR-003** BR-IP-AUD-011）。
- **边界说明**：只迁移 Encounter 主档状态与出院方式；**不**办理护士出院、不结算、**不清床**；**禁止**收口 `adm_encounter_location` / `adm_encounter_participant`、**禁止** `releaseBed`（释床仅 **§6.1.22** `clearDischargedBed`）。
- **前置条件**：就诊须为 `IMP + IN_PROGRESS`；满足 BR-ENC-004 与 BR-ENC-006。
- **并发（GAP-ATD-006）**：`requireEncounterForUpdate` + 异类已校对诊疗 Pre 门禁；`ENCOUNTER_INVALID_STATUS` / 乐观锁 → **EX-IP-AUD-203**。
- **状态变化**：`IN_PROGRESS -> ON_HOLD`（QueryService结合 `discharge_type_enum` 展示「待出院」，见 §2.3.2）。
- **聚合内影响**：写入 `out_way_code` 等出院字段；**不**写 `end_time`（护士办理出院时写入）；**活动占床位置行保持 ACTIVE**（患者仍在床位图占床，直至清床）。
- **事件输出**：`HospitalDischargedEvent`（载荷 `statusEnum=ON_HOLD`，`dischargeTypeEnum=discharge`）。
- **命令**：`DischargeInpatientEncounterCommand`（`operatorId`；子项：`encounterId`、`outWayCode`、`dischargedAt`（医嘱计划时刻）、`summary`）。

#### 6.1.18a 撤销转科/出院过渡（`revertTransferOrDischargeHold`，GAP-ATD-008）

- **业务意义**：转科/出院医嘱逆向（护士退回、医生签退/停嘱）后，撤销 `ON_HOLD` 过渡态，与 §6.1.16/§6.1.18 对称。
- **前置条件**：就诊须为 `IMP + ON_HOLD`；App 已确认里程碑 `requestId` 对齐且无其它已校对转科/出院残留。
- **并发**：`requireEncounterForUpdate` + `Encounter` `@Version`。
- **状态变化**：`ON_HOLD -> IN_PROGRESS`。
- **聚合内影响**：转科轨按最近参与者收口批次 INSERT 活动行；出院轨清空 `outWayCode`。
- **命令**：`RevertTransferOrDischargeHoldCommand`。

#### 6.1.19 护士办理出院（`executeInpatientNurseDischarge`）

- **业务意义**：护士在入出转「出」Tab 门禁通过后，将患者推进**待出院结算**（**AS-IP-NUR-002** IF-IP-NUR-ATD-05）。
- **边界说明**：不校验医嘱/发药/执行（由应用层 Pre 编排）；不负责收费结算。
- **前置条件**：就诊须为 `IMP + ON_HOLD`；应用层须已排除**待转科**（`transfer_org_id` 非空不得调用）。已为 `PENDING` 时**幂等跳过**。
- **并发（GAP-ATD-005）**：`PESSIMISTIC_WRITE` + `Encounter` `@Version`；`OptimisticLockingFailureException` → **EX-IP-ATD-304/402/504**（ATD-05/06/07）。
- **状态变化**：`ON_HOLD -> PENDING`。
- **聚合内影响**：写入 `end_time`（命令 `dischargedAt`：**须为出院医嘱计划时间** `occurrence_start_time`，空则服务端当前时刻）。业务出院时间以此为准；后续清床/无床结算**不得覆盖**。
- **事件输出**：`HospitalDischargedEvent`（载荷 `statusEnum=PENDING`，`endTime`）。
- **命令**：`ExecuteInpatientNurseDischargeCommand`（`operatorId`；子项：`encounterId`、`dischargedAt`）。

#### 6.1.19a 取消护士办理出院（`cancelInpatientNurseDischarge`）

- **业务意义**：护士误办出院或需再整理费用/医嘱时，将**待结算**对称回退（**AS-IP-NUR-002** IF-IP-NUR-ATD-08）。
- **边界说明**：不撤出院医嘱、不碰费用/床位；**不**调医保 2405（护士办理出院本就不调 2402）。已结算须走 **AS-IP-FIN-004** 反结。
- **前置条件**：就诊须为 `IMP + PENDING`；应用层判定是否仍有生效态出院嘱（`ACTIVE`/`VERIFIED`/`COMPLETED`）并传入 `restoreToInProgress`。已为目标态时**幂等跳过**。
- **并发（GAP-ATD-005）**：`PESSIMISTIC_WRITE` + `Encounter` `@Version`；`OptimisticLockingFailureException` → **EX-IP-ATD-310**。
- **状态变化**：有有效出院嘱 `PENDING → ON_HOLD`；无有效出院嘱 `PENDING → IN_PROGRESS`。
- **聚合内影响**：清空 `end_time`；回已入科时清空 `out_way_code`；回待出院时保留 `out_way_code`。
- **事件输出**：`HospitalDischargedEvent`（载荷 `statusEnum` 为取消后态）。
- **命令**：`CancelInpatientNurseDischargeCommand`（`operatorId`；子项：`encounterId`、`restoreToInProgress`）。
- **实体方法**：`Encounter#cancelNurseDischarge(boolean)`。

#### 6.1.20 住院出院财务全结（`completeInpatientFinancialSettlement`）

- **业务意义**：住院收费窗口**出院全结**后，就诊进入财务/离院终态（**AS-IP-FIN-003** BR-IP-STL-003）。
- **边界说明**：仅出院全结路径；**中途结算**不得调用。**有活动床**时落到 `COMPLETED`（仍须护士清床）；**无活动床**（召回未占床轨）直接 `DISCHARGED` 并收口活动 WARD 等位置。
- **前置条件**：就诊须为 `IMP + PENDING`。
- **并发（GAP-ATD-005）**：`requireEncounterForUpdate` + `Encounter` `@Version`。
- **状态变化**：**有活动床** `PENDING -> COMPLETED`；**无活动床** `PENDING -> DISCHARGED`（活动位置 `markCompleted`）。
- **聚合内影响**：无床轨收口活动 `adm_encounter_location`（WARD 等）；`transitToDischarged` **不覆盖**已有 `end_time`（业务出院时间仍为办理出院写入的计划出院时间；仅空时回落结算时刻）。
- **事件输出**：`InpatientFinancialSettlementCompletedEvent`（载荷 `statusEnum` 为 `COMPLETED` 或 `DISCHARGED`、`settlementTime`）。
- **命令**：`CompleteInpatientFinancialSettlementCommand`（`encounterId`、`settlementTime`、`operatorId`）。

#### 6.1.20a 住院取消出院全结（`revertInpatientFinancialSettlement`）

- **业务意义**：住院收费窗口**出院全结反结**后，就诊回到在院执行态（**AS-IP-FIN-004** IF-IP-RVS-01；DM §6.1.25 未清床轨 `recallFromCompletedToInProgress`）。
- **边界说明**：仅对称回退就诊主档临床态；**不**执行反结支付/ChargeItem/撤销出院嘱（由应用层 **FIN-004** 先行完成）；**不清**活动床/参与者轨迹。
- **前置条件**：就诊须为 `IMP + COMPLETED`（未清床占床）。
- **并发（GAP-ATD-005）**：`requireEncounterForUpdate` + `Encounter` `@Version`。
- **状态变化**：`COMPLETED → IN_PROGRESS`；清空 `end_time`、`out_way_code`。
- **聚合内影响**：无位置/参与者收口。
- **事件输出**：无专用领域事件（反结/撤销出院嘱事件由应用层发布）。
- **命令**：无独立 Command；应用层传入 `encounterId` 调用本方法。

#### 6.1.21 清床（`clearDischargedBed`）

- **业务意义**：结算完成后护士**清床离院**，收口床位与参与者轨迹（**AS-IP-NUR-002** IF-IP-NUR-ATD-06）；应用层同事务 **`releaseBed`** 释放床运营态。
- **边界说明**：只收口 Encounter 活动位置/参与者并迁移主档终态；Location 床态由应用层 `releaseBed` 编排。
- **前置条件**：就诊须为 `IMP + COMPLETED`（**非** `DISCHARGED`）。
- **并发（GAP-ATD-005）**：`PESSIMISTIC_WRITE` 加载就诊行，锁内 `assertClearBedAllowed` 后 `transitToDischarged`；释床见 GAP-ATD-003。
- **状态变化**：`COMPLETED -> DISCHARGED`。
- **聚合内影响**：活动 `adm_encounter_location`、`adm_encounter_participant` 标 **COMPLETED**；写入 `end_time`（若尚未写入则与清床时刻对齐）。
- **事件输出**：`HospitalDischargedEvent`（`statusEnum=DISCHARGED`）；`TerminalCleaningCompletedEvent`（`bedId`）。

#### 6.1.22 取消住院登记（`cancelInpatientAdmission`）

- **业务意义**：终止尚未进入在院执行阶段的住院就诊（含医生申请后未登记、以及住院处已登记待入科但未分床）。
- **边界说明**：只终止 Encounter 记录；账户余额校验依赖应用层编排查询 Account 域；**已分床**（活动床位级位置）不可取消。
- **前置条件**：就诊须为 `IMP`，且状态为 **`PLANNED` 或 `REGISTERED`（未分床）**；预交金余额为 0（应用层传入余额快照校验）。
- **状态变化**：`PLANNED` 或 `REGISTERED` → `CANCELLED`；取消后释放 `ambEncounterId` 关联以便同一门诊可再申请。
- **聚合内影响**：更新主档状态并记录取消原因；清空门诊关联锚点。
- **事件输出**：`InpatientAdmissionCancelledEvent`。

#### 6.1.23 护士站保存患者信息（`saveNurseStationPatient`）

- **业务意义**：住院护士站在不换床、不迁移就诊状态的前提下，修正病情优先级与责任医护参与者（**AS-IP-NUR-001** IF-IP-NUR-04）。
- **边界说明**：只维护 `adm_encounter.priority_enum` 与 `adm_encounter_participant` 活动轨迹；**禁止**改 `status_enum`、**禁止**写 `adm_encounter_location`（分床/转床走 `assignInpatientBed` 等独立命令）。
- **前置条件**：就诊须为 `IMP`；`status_enum` 为 **`REGISTERED` 或 `IN_PROGRESS`**（对齐 **AS-IP-NUR-001** **BR-IP-NUR-010**）；同一请求内参与者 `roleCode` 不重复。
- **状态变化**：`N/A`（不迁移主档状态）。
- **聚合内影响**：可选更新 `priority_enum`；对每个提交的 `roleCode` 将活动参与者行标为 **COMPLETED** 后 **INSERT** 新 **ACTIVE** 行（禁止原地 UPDATE；与分床「全量收口」策略不同）。
- **事件输出**：`NurseStationPatientSavedEvent`。
- **命令**：`SaveNurseStationPatientCommand`（`operatorId`；子项 `ItemCommand`：`encounterId`、`priorityEnum`、`participants`（`ParticipantLine`：`roleCode`、`practitionerId`）、`savedAt`）。

#### 6.1.25 出院召回（`recallInpatientDischarge`）

- **业务意义**：**已清床离院**（`DISCHARGED`）患者经 **AS-IP-FIN-004** **IF-IP-RCL-01** 拉回**已入科**态；**应用层须先**经 **`InpatientSettleAppService#executeReverseInpatientSettle(..., allowDischarged=true)`**（对称反结；含撤销出院医嘱）**成功**，再调用本方法恢复就诊位置。
- **边界说明**：本方法**只**迁移就诊主档与活动病区轨迹；**不**执行反结、**不**撤销出院嘱、**不** INSERT 活动床、**不**恢复参与者。
- **前置条件**：就诊须为 `IMP`；**`status_enum = DISCHARGED`**；应用层已保证内嵌反结成功。
- **并发（GAP-ATD-005）**：反结完成后 `PESSIMISTIC_WRITE` 加载就诊行，锁内断言 `DISCHARGED` 后 `recallFromDischargedToInProgress`。
- **状态变化**：`DISCHARGED → IN_PROGRESS` + **INSERT** 末次 `COMPLETED` **WARD** 对应之新 **ACTIVE** **WARD** 行（**不**占物理床）。
- **聚合内影响**：清空 `out_way_code`、`end_time`；调用 `upsertActiveWard`（INSERT 语义）。
- **事件输出**：`InpatientDischargeRecalledEvent`（`previousStatusEnum`、`statusEnum`、`requiresBedAssignment=false`、`wardLocationId`）。
- **命令**：`RecallInpatientDischargeCommand`（`operatorId`；子项：`encounterId`、`wardLocationId`（必填）、`recalledAt`）。
- **读侧辅助**：`EncounterQueryService#findLastCompletedWardLocationIdByEncounterId`（应用 Pre 解析末次病区）。

### 6.2 读侧查询服务（EncounterQueryService）

> 查询类动作无写库，以「查询范围（读库，无写入）+ 组装规则」表达 **领域** `EncounterQueryService`；**仅**加载 `adm_encounter` 主档与子表（参与者/位置/诊断）及就诊域内派生查询。**禁止**在领域层做患者/科室/医师/床位/诊断 JSON 等跨聚合组装（见 **§6.2.18～§6.2.20** 与 `04-架构设计/ReadModel弃用迁移/10-就诊上下文读组装-禁止领域层Support.md`）。已废止 **InpatientInfo / OutpatientInfo** 读聚合；住院/门诊队列展示使用 **`InpatientEncounterContextQueryModel` / `OutpatientEncounterContextQueryModel`**，由应用层 **`EncounterContextReader`**（`application.shared.encounter`）编排。

#### 6.2.1 按主键加载就诊（`loadEncounterById` / `mustGetEncounter`）

- **业务意义**：为“先查再改”提供统一入口，确认就诊存在与联合状态合法。
- **边界说明**：只读查询，不写库，不做跨域编排。
- **查询范围（读库，无写入）**：就诊主档，必要时联查子实体。
- **返回约束**：不存在或状态非法时抛明确异常。

#### 6.2.2 按业务号加载就诊（`loadEncounterByBusNo`）

- **业务意义**：按业务号定位唯一就诊，支撑集成与追踪。
- **边界说明**：只读查询，不改写业务数据。
- **查询范围（读库，无写入）**：`adm_encounter` 主表。
- **返回约束**：依赖 `busNo` 唯一性。

#### 6.2.3 查询活动参与者（`listActiveEncounterParticipants`）

- **业务意义**：提供**当前仍在岗**责任团队视图，供写前门禁与「须有活动责任人」判定。
- **边界说明**：只读查询，不替代历史审计查询；**禁止**用于结算/护士站/病案等卡片展示（清床收口或召回未恢复后会空）。
- **查询范围（读库，无写入）**：`adm_encounter_participant` 且 `status_enum=ACTIVE`。
- **返回约束**：仅返回活动态参与者。

#### 6.2.3a 查询展示参与者（`listDisplayEncounterParticipants` / `listDisplayParticipantEntitiesByEncounterIds`）

- **业务意义**：列表/卡片/病案自动取值等**展示**场景解析主治/责任护士/接诊医生等；在院有活动行时与活动口径一致，不在院（清床离院收口参与者、召回不恢复参与者）时仍能回显末次责任人。
- **边界说明**：只读；**不得**替代 `listActive*` 做写前门禁或滚费责任判定。
- **查询范围（读库，无写入）**：`adm_encounter_participant`。
- **过滤口径**：
  - 同 `encounter_id` + `role_code`：**优先** `status_enum=ACTIVE`；
  - 该角色无活动行时：取 `status_enum=COMPLETED` 且 `end_time` 最大的一行；
  - 批量方法对入参就诊集合一次性装载，禁止按就诊循环单查。
- **返回约束**：每就诊每角色至多一条；可同时含 ACTIVE 与（其它角色的）COMPLETED 行。
- **单角色变体**：`listDisplayEncounterParticipantsByType(encounterId, roleCode)`。

#### 6.2.4 按角色查询参与者（`listActiveEncounterParticipantsByType`）

- **业务意义**：按角色定位**当前活动**责任人（主治/责任护士等），供写侧与门禁。
- **边界说明**：只读查询，不负责角色变更；展示回显见 **§6.2.3a**。
- **查询范围（读库，无写入）**：活动参与者 + 角色过滤。
- **返回约束**：用于当前责任关系判断。

#### 6.2.5 查询位置轨迹（`listEncounterLocations` / `getEncounterLocationInfo`）

- **业务意义**：回显位置轨迹与当前占用位置。
- **边界说明**：只读查询，资源主状态解释仍以 Location 域为准。
- **查询范围（读库，无写入）**：`adm_encounter_location` 轨迹数据。
- **返回约束**：支持全轨迹与当前位置信息两类视图。

#### 6.2.6 查询就诊诊断（`listEncounterDiagnoses`）

- **业务意义**：支撑完诊/出院校验与病历展示。
- **边界说明**：只读查询，不修改诊断关联。
- **查询范围（读库，无写入）**：`adm_encounter_diagnosis`。
- **返回约束**：返回主诊与排序信息。

#### 6.2.7 就诊投影分页（`queryEncounterProjections`）

- **业务意义**：通用就诊列表/报表分页，按患者、类别、状态、科室过滤。
- **边界说明**：只读查询，直接查 `adm_encounter` 主表组装 `EncounterProjectionQueryModel`；**不**经独立投影表。
- **查询范围（读库，无写入）**：`adm_encounter` 主表 + Specification 动态谓词。
- **返回约束**：分页结果；当前实现 `patientName` 以 `patientId` 字符串占位（展示列须由应用层经 `PatientQueryService` 回填，见 doc-10）。

#### 6.2.8 完诊前校验（`checkOutpatientCompletable`）

- **业务意义**：在执行 `completeOutpatientEncounter` 前，先确认“这次门诊现在允许完诊”，避免写侧执行到一半才失败。
- **边界说明**：只读校验，不修改就诊状态、不收口参与者。
- **查询范围（读库，无写入）**：就诊主档（类别+状态）与必要子实体（参与者、诊断）。
- **过滤口径**：至少校验当前为 `AMB + IN_PROGRESS/ON_HOLD`，并满足 BR-ENC-004（负责医生约束）；若配置要求主诊断，再校验 BR-ENC-006。
- **返回约束**：通过则返回“可完诊”；不通过返回明确失败原因（如“状态不允许完诊”“缺少负责医生”“缺少主诊断”），由写侧拦截。

#### 6.2.9 门诊防重校验（`checkOutpatientDuplicateRegistration`）

- **业务意义**：在开立门诊前，判断同一自然日内「同一患者 + 同一目标科室（`organizationId`，挂号所选组织机构）」是否已存在**占用名额**的门诊就诊（含待挂号 `PLANNED`、已交费流程及诊毕等），用于阻断重复挂号；**退号/撤销（`CANCELLED`）释放名额**。
- **边界说明**：只读判断，不创建或修改 Encounter。
- **查询范围（读库，无写入）**：`adm_encounter`，联合键为 **`patientId` + `organizationId` + 自然日**；「同日」按就诊行 **`created_at`** 落在 **`[当日 00:00, 次日 00:00)`**（与应用层开立就诊的 `occurredAt` 日历日、`OutpatientDuplicateCheckQuery.registrationDate` 对齐）。
- **过滤口径**：
  - **就诊类别**：`class_enum = AMB`。
  - **占名额状态**：`status_enum` 为 **`PLANNED`、`REGISTERED`、`TRIAGED`、`IN_PROGRESS`、`ON_HOLD`、`DISCHARGED`、`COMPLETED`**（待挂号与已交费后的就诊事实或诊毕终态均占用；**不含 `CANCELLED`**）。
  - **软删**：仅统计 `is_deleted = false`。
- **返回约束**：
  - **重复**：命中上述条件 → `duplicated=true`，并宜返回冲突 `encounterIds` 供前端提示；
  - **可开立**：未命中 → `duplicated=false`，可继续 `registerOutpatientEncounter`（领域侧默认 `assertNoOutpatientDuplicate` 与上述口径一致；命令 `skipSameDaySameDeptDuplicateCheck=true` 时写路径跳过，见 **BR-ENC-011** 例外）。

#### 6.2.9a 复诊0元号资格校验（`existsQualifyingHistoricalFollowUpPriorVisit`）

- **业务意义**：挂号预结算选定产品号源「复诊0元号」（应用层按 **`name` trim 精确匹配** `HealthcareServiceConstants.NAME_HISTORICAL_FOLLOW_UP`；种子默认 `bus_no=HS00000004`，**不按 bus_no 识别**）时，判断同一患者、**同一挂号科室**是否存在可作为复诊依据的前次门诊就诊（**BR-REG-020**）。
- **边界说明**：只读；不创建或修改 Encounter；跨科室不算复诊。
- **查询范围（读库，无写入）**：`adm_encounter`（`class_enum=AMB`）。
- **过滤口径**：
  - **患者与科室**：`patient_id`、`organization_id` 与本次挂号一致。
  - **已接诊态**：`status_enum ∈ { IN_PROGRESS, ON_HOLD, DISCHARGED }`（不含待收费 `PLANNED`、未接诊 `REGISTERED`/`TRIAGED`）。
  - **接诊时刻窗**：`reception_time` 非空，且 `reception_time <= asOf` 且 `reception_time >= asOf - 72h`（自接诊起向后 72 小时内）。
  - **排除号源**：`service_type_id` 不得为复诊0元号自身（防止连环续挂）。
  - **软删**：仅统计 `is_deleted = false`。
- **返回约束**：存在至少一条 → `true`（允许挂复诊0元号）；否则 `false`（应用层 Gate 抛 `REG-BIZ-420`）。

#### 6.2.10 断言源就诊可转住院（`assertAmbForInpatientReq`）

- **业务意义**：住院申请写操作前，确认源门诊就诊存在且处于可办理住院的状态。
- **边界说明**：只读；不修改源就诊。
- **过滤口径**：`class_enum = AMB`；非终止态；`status_enum ∈ { REGISTERED, TRIAGED, IN_PROGRESS, ON_HOLD }`。
- **失败语义**：类别不符、已终态或状态不允许时抛业务异常。

#### 6.2.11 查询未取消住院线（`findOpenInpatientLine` / `existsOpenInpatientLine`）

- **业务意义**：按 `ambEncounterId` 判断该门诊就诊是否已有关联的未取消住院线，支撑防重与医生站「已申请」展示。
- **边界说明**：只读；返回 `OpenInpatientLineQuery`（`exists`、`encounterId`、`busNo`、`statusEnum`）。
- **查询范围**：`adm_encounter` 中 `class_enum=IMP`、`amb_encounter_id` 匹配且 `status_enum ≠ CANCELLED` 的最新一条。
- **返回约束**：存在则 `exists=true` 并带回住院就诊标识与业务号；不存在则 `exists=false`。

#### 6.2.11a 批量判定在院住院患者（`findPatientIdsWithActiveInpatientStay`）

- **业务意义**：按患者主键集合批量判定是否仍有在院住院线，供门诊日结患者分项「是否住院」展示（**AS-OP-FIN-004 / BR-OP-DCL-032**）。
- **边界说明**：只读；不写库；空入参返回空集；按患者去重后分批 IN（每批 ≤500）。
- **查询范围（读库，无写入）**：`adm_encounter`，`patient_id IN (...)` 且 `class_enum=IMP` 且 `status_enum ∉ { CANCELLED, ENTERED_IN_ERROR, DISCHARGED, COMPLETED }` 且未软删。
- **返回约束**：`Set<patientId>`（有命中住院线的患者）；调用方据此置 `hospitalized=true`。
- **实现锚点**：`EncounterQueryService#findPatientIdsWithActiveInpatientStay` → `EncounterRepository#findByPatientIdInAndClassEnumAndStatusEnumNotInAndIsDeletedFalse`。

#### 6.2.12 批量反查床位占床就诊（`mapActiveBedEncounterIdByLocationIds`）

- **业务意义**：护士站入科床位列表（AS-IP-NUR-002 ATD-01）按床位 `location_id` 批量解析当前占床 `encounter_id`，支撑 BR-IP-ATD-009「一床一患」展示与组装。
- **边界说明**：只读；不写库；空床不在返回映射中。
- **查询范围（读库，无写入）**：`adm_encounter_location`，条件为 `location_id IN (...)` 且 `form_enum=BED` 且 `status_enum=ACTIVE`（仓储 `findAllByLocationIdInAndFormEnumAndStatusEnum`）。
- **返回约束**：`Map<bedLocationId, encounterId>`；同一床位若命中多条活动占床轨迹则抛 `ENCOUNTER_RULE_CONFLICT`。

#### 6.2.13 解析活动位置（`requireActiveLocationId`）

- **业务意义**：按就诊与位置形态解析当前唯一活动 `location_id`；供护士站入出转 Pre（`form_enum=BED`/`HOUSE`）及写侧 `EncounterDomainService#requireActiveBedId`（委托本读方法，`form_enum=BED`）共用。
- **边界说明**：只读；WARD 轨迹不在此接口解析范围内；错误码与文案由**调用方**传入（与 `mustGetEncounterOfClass` 同模式）。
- **查询范围（读库，无写入）**：指定 `encounter_id` 下 `status_enum=ACTIVE` 轨迹，再按入参 `form_enum` 筛选（如 `BED`、`HOUSE`）。
- **返回约束**：恰好一条匹配时返回 `location_id`；无匹配时抛调用方 `notFoundErrorCode`；多条匹配时抛调用方 `duplicateErrorCode`。
- **典型调用**：
  - 换床/互换 Pre：`form_enum=BED`，应用层传 `EX-IP-ATD-201`；
  - 互换 Pre 病房：`form_enum=HOUSE`，应用层传 `EX-IP-ATD-201`；
  - 领域写侧转床校验：`form_enum=BED`，传 `ENCOUNTER_INVALID_STATUS` / `ENCOUNTER_RULE_CONFLICT`。

#### 6.2.14 按就诊类别断言加载（`mustGetEncounterOfClass`）

- **业务意义**：写操作前按 `encounterId` + 期望 `classEnum` 加载就诊，区分「不存在」与「类别不符」。
- **边界说明**：只读；住院登记（`IMP`）、门诊退号（`AMB`）等场景须用本方法替代裸 `findById`。
- **查询范围（读库，无写入）**：`adm_encounter` 按主键 + `class_enum`。
- **返回约束**：匹配则返回 `EncounterDetailQueryModel`；不存在 → `ENCOUNTER_NOT_FOUND`；类别不符 → 调用方错误码。

#### 6.2.15 批量反查辅助映射

| 方法 | 业务意义 | 查询范围 |
| --- | --- | --- |
| `mapAmbEncounterIdByEncounterIds` | 住院线批量解析源门诊 `amb_encounter_id` | `adm_encounter`（`class_enum=IMP`） |
| `mapBusNoByEncounterIds` | 批量反查业务号 | `adm_encounter.bus_no` |
| `mapPatientIdByEncounterIds` | 批量反查患者标识 | `adm_encounter.patient_id` |
| `mapPriorityEnumByEncounterIds` | 批量反查优先级 | `adm_encounter.priority_enum` |
| `mapMedTypeByEncounterDiagnosisIds` | 按诊断关联批量反查医保类别 | `adm_encounter_diagnosis.med_type` |
| `mapPreferredMedTypeByEncounterIds` | 按就诊批量取 **主诊断优先** 的 `med_type`（无则首条非空） | `adm_encounter_diagnosis` |
| `listEncounterIdsByBusNoKeyword` | 发药单列表：业务号关键字反查就诊主键 | `adm_encounter.bus_no` 模糊匹配 |
| `listEncounterIdsByPatientIds` | 发药单列表：患者主键集合反查就诊主键 | `adm_encounter.patient_id IN (...)` |
| `listEncounterIdsByWardLocationIds` | 护士站汇总单管辖病区门控：按病区 WARD 轨迹（含已收口）反查就诊主键 | `adm_encounter_location`：`location_id IN` + `form_enum=WARD`；供 **AS-IP-NUR-010** IF-02 |

#### 6.2.16 读侧门控加载实体（`requireEncounterEntityOfClass`）

- **业务意义**：应用层校验后读取 `organizationId` 等头字段，或写编排前的只读门控。
- **边界说明**：只读；与 `mustGetEncounterOfClass` 同类校验，返回 **`Encounter` 实体**（非 `EncounterDetailQueryModel`）。
- **返回约束**：不存在 → `ENCOUNTER_NOT_FOUND`；类别不符 → 调用方错误码。

#### 6.2.30 工作量就诊 scope（`listEncounterIdsForPractitionerWorkloadScope`）

- **业务意义**：**AS-RPT-001** / **AS-RPT-007**：按 **`class_enum` + `organization_id IN (...)`** 解析候选就诊 id；报表读侧应叠加 **业务日窗**，避免无界历史扫。
- **边界说明**：只读；排除逻辑删除。带日窗重载语义为就诊时段与 `[windowFrom, windowTo]` **相交**：`startTime ≤ windowTo` 且（`endTime` 空或 `endTime ≥ windowFrom`）；任一日窗参数为空则不加时段谓词（兼容旧调用）。
- **入参**：`encounterClassCode`（AMB/IMP）、`organizationIds`；可选 `windowFrom` / `windowTo`（`LocalDateTime`）。
- **返回约束**：参数无效返回空列表。

#### 6.2.31 工作量就诊上下文批量（`mapPractitionerWorkloadEncounterContextByIds` / `filterEncounterIdsByActiveRegistrationDoctor`）

- **业务意义**：挂号号源分组读 **`organization_id` / `service_type_id` / `status_enum`**；选医生时按活动 **`REGISTRATION_DOCTOR`** 参与者收窄就诊；应用层未选科室时按 **`service_type_id` 跨 org 合并**（AS-RPT-001 v1.3）。
- **边界说明**：只读；`filterEncounterIdsByActiveRegistrationDoctor` 在 **`practitionerId` 为空** 时返回空集合（由应用层决定是否调用）。应用层应对 **payment 命中就诊** 批量拉 context，禁止对科室 scope 内全部历史就诊拉 context。
- **出参**：`PractitionerWorkloadEncounterContextQueryModel` map / 命中就诊 id 集合。

#### 6.2.32 护士工作量就诊 scope（`listEncounterIdsForNurseWorkloadScope`）

- **业务意义**：**AS-RPT-002** / **AS-RPT-005/006**：按 **`class_enum`** 解析该域就诊 id（**无科室收窄**）；报表读侧应叠加 **业务日窗**。
- **边界说明**：只读；排除逻辑删除。带日窗重载相交口径同 **§6.2.30**。
- **入参**：`encounterClassCode`（AMB/IMP）；可选 `windowFrom` / `windowTo`。
- **返回约束**：参数无效返回空列表。

#### 6.2.16a 末次已完成病区（`findLastCompletedWardLocationIdByEncounterId`）

- **业务意义**：出院召回已清床轨 Pre 解析末次 `COMPLETED` **WARD** 位置主键（§6.1.25）。
- **边界说明**：只读；按 `end_time` 取最大；无匹配返回空。
- **查询范围（读库，无写入）**：`adm_encounter_location`（`form_enum=WARD`，`status_enum=COMPLETED`）。
- **返回约束**：`Optional<Long>` 病区 `location_id`。

#### 6.2.17 门诊就诊上下文（`OutpatientEncounterContextQueryModel` · 应用层）

> **落点**：`health-application` · `EncounterContextReader` → `OutpatientEncounterContextLoader`（**非** `EncounterQueryService`）。真源：**`10-就诊上下文读组装-禁止领域层Support.md`**。

| Facade 方法 | 典型消费方 |
| --- | --- |
| `pageOutpatientEncounterContexts` | 门诊医生站 IF-DOR-01（**Q-OPI-001**） |
| `pageNurseStationOutpatientQueue` | 门诊护士站队列（入参 `NurseStationOutpatientQueueQuery`：可选 `orgId` 等值匹配就诊 `organization_id`，空=全部开立科室；`startDate`/`endDate` 按就诊开始时间闭区间；**`pendingDeviceOnly=true`** 时叠加待发耗材 EXISTS——已完成非退单 `DeviceRequest` 且发放单缺失或仍为 `DRAFT`/`PREPARATION`/`IN_PROGRESS`，**分页在过滤后生效**；**AS-OP-NUR-001 BR-NUR-017**） |
| `getOutpatientContextByEncounterId` / `mustGetOutpatientContextByEncounterId` | 单就诊上下文回显 |
| `listOutpatientContextsByEncounterIdsOrdered` | 批量按序组装 |

- **边界说明**：只读；Loader 编排 `EncounterQueryService` + `PatientQueryService` + 参与者/诊断 Assembler 等；关键字 OR 匹配姓名/`py_str`/`wb_str`/业务号。
- **排序（Q-OPI-001 / 医生站 IF-DOR-01）**：待诊（`REGISTERED`/`TRIAGED`，及约定的 `PLANNED` 单查）按 `startTime` **升序**；在诊/暂离（`IN_PROGRESS`/`ON_HOLD`）按 `receptionTime` **降序**；缺省多状态并集按状态桶 + 桶内上述业务键（见 `EncounterContextCriteriaSupport`，契约 **AS-OP-DOR-001 BR-DOR-002**）。
- **返回约束**：`OutpatientEncounterContextQueryModel` 分页或列表；对外 Response 须经 Mapper 结构化诊断字段（**B.6.2**），禁止透传 `diagnosisJson`。

#### 6.2.17b 医技工作站患者队列（`pageMedicalTechWorkstationQueue`）

> **落点**：`EncounterQueryService#pageMedicalTechWorkstationQueue`（返回 `Page<Encounter>`）；规约由 `EncounterContextCriteriaSupport#medicalTechWorkstationSpecification` 产出（跨聚合 EXISTS 与护士站队列同模式，**不**在 QS 内联 `subquery`）。人口学/诊断上下文组装仍归应用层 `EncounterContextReader`（后续 App 成码，**非**本方法）。对齐 **AS-MT-EXE-001** IF-MT-EXE-01 / **BR-MT-EXE-002～003、023**；可见性 DD。

| 项 | 口径 |
| --- | --- |
| **入参** | `MedicalTechWorkstationQueueQuery`：`loginTechOrgId`（登录 TECH 科，必填）；`encounterScope`=`OUTPATIENT`\|`INPATIENT`（→`AMB`/`IMP`）；`keyword`（患者姓名/拼音/五笔）；`startDate`/`endDate`（就诊开始闭区间，**仅门诊**）；`pendingDeviceOnly`（可选）；`executionQueueFilter`=`PENDING`\|`EXECUTED`（可选，BR-MT-EXE-023）；`inpatientStayFilter`（仅住院） |
| **有活（硬门槛）** | 同就诊须存在本站可见诊疗：`ServiceRequest` 未删、退费关联空、`org_id` ∈ 共看可见 TECH，且状态——门诊 **`COMPLETED`**、住院 **`VERIFIED`**。仅耗材待发、无本站诊疗**不进队** |
| **仅待发** | `pendingDeviceOnly=true` 时在诊疗有活之上再叠加本科耗材房待发 EXISTS |
| **待执行/已执行** | `PENDING`：仍有尚可执行诊疗（净完成&lt;计划）；门诊另含待发耗材。`EXECUTED`：诊疗均已做满；门诊另无待发耗材；**住院不看耗材**。空/非法码不按执行态收窄 |
| **出参** | `Page<Encounter>`（软删已滤；默认按 `start_time`/`id` 降序；关键字时 Spec 内 B.2.1 精确优先） |
| **边界** | 只读；`loginTechOrgId` 或 scope 非法 → 空页；**禁止**把护士站队列 Spec 冒充本方法 |

#### 6.2.18 住院就诊上下文（`InpatientEncounterContextQueryModel` · 应用层）

> **落点**：`EncounterContextReader` → `InpatientEncounterContextLoader`；人口学列可由 `PatientQueryService#mapInpatientProjectionSnapshotsByIds` 回填（**DM-ADM-001 §6.2.9**）。

| Facade 方法 | 典型消费方 |
| --- | --- |
| `pageInpatientRegQueue` | 住院登记队列 |
| `pageInpatientPrepayQueue` | 预交金队列 |
| `pageInpatientNurseStationQueue` | 护士站患者管理（**Q-IPN-001**）；支持 `nurseStationStatusFilter` / `statusEnumIn`（**SETTLED_DISCHARGED**=`COMPLETED`∪`DISCHARGED`；`DISCHARGED` 按末次已完成 WARD 地理收窄） |
| `pageInpatientDoctorStationQueue` | 医生站在院队列（**Q-IPI-006**） |
| `pageInpatientSettleQueue` | 住院结算队列 |
| `pageInpatientEncounterContexts` / `listInpatientEncounterContexts` | 通用住院上下文分页/列表 |
| `getInpatientContextByEncounterId` / `mustGetInpatientContextByEncounterId` | 单就诊上下文 |
| `listInpatientContextsByEncounterIdsOrdered` | 批量按序组装 |

- **边界说明**：只读；联查床位/病房/科室/参与者等；过滤口径与各 AS IF 对齐（在院、待结算等）。
- **参与者展示**：`EncounterContextQueryService` 经 `listDisplayParticipantEntitiesByEncounterIds` 装配主治/责任护士——**活动优先**，同角色无活动行回落末次 `COMPLETED`（清床收口、召回不恢复参与者后仍可展示）。
- **位置展示**：活动 WARD/BED 优先；无活动床回落末次已完成床，展示「原{房名}-{床名}（未占床）」（无房名则「原{床名}（未占床）」）；`DISCHARGED` 无活动病区时回落末次已完成 WARD。占床判定仍仅认 `ACTIVE`。
- **ON_HOLD 分流**：`InpatientEncounterContextLoader#applyInpatientHoldRoutingFields` 在 `ON_HOLD` 时：主档 `out_way_code` 非空**优先**判为待出院（`discharge_type_enum`）；否则再查转科里程碑设 `transfer_org_id`（避免校对出院后误分流为待转科，**BR-IP-ATD-010**）。
- **返回约束**：`InpatientEncounterContextQueryModel`；列表 `statusEnumText` 可由 `InpatientInfoQueueDisplayAssembler` 覆盖（**§2.3.2**）。

#### 6.2.19 挂号/号条（`ClinicRegisterQueryModel` / `RegistrationSlipQueryModel` · 应用层）

> **落点**：`EncounterContextReader` → `ClinicRegistrationLoader`。

| Facade 方法 | 典型消费方 |
| --- | --- |
| `pageClinicRegister` | 挂号台列表 |
| `pageRegistrationSlip` | 号条/挂号单分页 |
| `getRegistrationSlipByEncounterId` | 按就诊加载单条号条（**非** `getRegistrationSlipById`） |

- **边界说明**：只读；编排 `adm_encounter` 与患者/号源/参与者；支付金额摘要等仍由 `PaymentReconciliation` / `ChargeItem` 应用编排。
- **关键字（`pageClinicRegister` / `ClinicRegisterPageQuery.keyword`）**：与收费窗一致——就诊业务号 `bus_no` **或** 患者姓名（含拼音/五笔，`existsPatientKeyword`）；B.2.1 精确命中置顶（`applyEncounterKeywordExactFirstOrder`）；规约落点 `EncounterContextCriteriaSupport#clinicRegisterSpecification`；**禁止**按诊断描述匹配。
- **返回约束**：分页或 `Optional` 单条。列表行含 `organizationId`，供 IF-REG-15 回显当前科室。

#### 6.2.20 已付门诊防重辅助（`existsPaidOutpatientEncounterForDepartmentOnCalendarDay`）

- **业务意义**：按自然日 + 科室判断患者是否已有已付费门诊就诊（与防重规则互补）。
- **边界说明**：只读；不写库。
- **查询范围（读库，无写入）**：`adm_encounter`（`AMB` + 占名额状态）。
- **返回约束**：`boolean`。

#### 6.2.21 滚费在院就诊扫描（`listInpatientAutoRollEncounterContexts`）

- **业务意义**：**AS-IP-NUR-013** BR-IP-ARG-008：滚费任务扫描「已分床且未清床」IMP 就诊，返回 accountId / 科室等落账上下文。
- **边界说明**：只读；状态 **`IN_PROGRESS`/`ON_HOLD`/`COMPLETED`**（排除 **`DISCHARGED`**）；须激活 **`BED`** 轨迹（`EncounterLocation.status_enum=ACTIVE`）；账户经 **`AccountQueryService#mapRegistrationAccountsByEncounterIds`** 批量解析。应用层另按 **`endTime` 记进不计出**：出院自然日不晚于 billDate 则整 encounter 当日不滚。
- **查询范围（读库，无写入）**：`adm_encounter` + `adm_encounter_location`；账户表只读 join 由 Account 读服务承担。
- **返回约束**：`List<InpatientAutoRollEncounterQueryModel>`（含 `encounterId`、`patientId`、`organizationId`、`accountId`、`startTime`、`endTime`、`bedAssignedAt`（全部 BED 轨迹最早 `start_time`；**转床不替代**）、`statusEnum`、`bedLocationId`、`primaryNurseId`）；无 account 时 `accountId=null`（应用层决定是否跳过）。

#### 6.2.33 就诊上下文单条装载（`EncounterContextQueryService` · 跨模块 bridge）

> **落点**：`zxplat-domain` · `EncounterContextQueryService`（**非** `health-application` · `EncounterContextReader`）。  
> **消费方**：`zxplat-module-chs-jilin` bridge `ChsOutpatientInsuranceContextLoader` / `ChsInpatientInsuranceContextLoader`（220x/230x 出站前组装 Command）。  
> **真源**：`docs/模块/zxplat-module-chs-jilin/17-zxplat-module-chs-jilin-架构-v3.md` §2。

| 方法 | 业务意义 | 出参 |
| --- | --- | --- |
| `mustGetOutpatientContextByEncounterId` | 单条门诊就诊上下文（患者/科室/医师/服务类型等） | `OutpatientEncounterContextQueryModel` |
| `mustGetInpatientContextByEncounterId` | 单条住院就诊上下文 | `InpatientEncounterContextQueryModel` |
| `getOutpatientContextByEncounterId` / `getInpatientContextByEncounterId` | 同上，不存在时 `Optional.empty()` | 同上 |

- **边界说明**：在 **zxplat-domain** 内编排 `Encounter` + `Patient` + `Organization` + `Practitioner` + `HealthcareService` + `Location` 等跨聚合只读组装；**禁止** bridge 注入 `*Repository` 或自写 SQL。
- **与 §6.2.17～18 关系**：应用层队列/分页仍走 `EncounterContextReader`；本服务仅服务 Extension 链**单就诊出站**薄上下文。

#### 6.2.34 门诊挂号明细报表明细分页（`pageOutpatientRegistrationDetailReport`）

- **业务意义**：**AS-RPT-007** 明细 Tab：AMB 挂号事实分页（科室/接诊医生/号源/状态/时间）；费用性质由应用层 enrich。
- **边界说明**：只读；**仅本聚合** Encounter + Participant；时间轴 `coalesce(startTime, createdAt)`。
- **入参**：`OutpatientRegistrationReportPageQuery`。接诊医生/关键字收窄由应用层预写 **`resolvedEncounterIds`**（医生筛复用 **§6.2.34a** `filterEncounterIdsByActiveAdmitterDoctor`，**禁止**在本方法内另写医生全表扫描）。可选 **`statusEnum`** 单值收窄就诊状态（空不收窄）。
- **出参**：`Page<OutpatientRegistrationReportLineQueryModel>`（可含活动 `REGISTRATION_DOCTOR` 兼容字段；应用层明细展示仅接诊医生）。
- **实现锚点**：`EncounterQueryService#pageOutpatientRegistrationDetailReport`。

#### 6.2.34a 接诊医生候选过滤（`filterEncounterIdsByActiveAdmitterDoctor`）

- **业务意义**：**AS-RPT-007** 医生筛：在候选就诊中保留活动 **`ADMITTER`** 为指定医师的就诊。
- **边界说明**：只读；仅本聚合 Participant；`practitionerId` 空返回空集合。与 **§6.2.31** `filterEncounterIdsByActiveRegistrationDoctor`（工作量仅挂号医生）分立。
- **实现锚点**：`EncounterQueryService#filterEncounterIdsByActiveAdmitterDoctor`。

#### 6.2.34b 门诊挂号明细报表挂/退统计（`countOutpatientRegistrationDetailReportStats`）

- **业务意义**：**AS-RPT-007** 明细页脚：同筛选 **挂号 / 已退 / 有效**（对齐门诊挂号当日统计）。
- **口径**：挂号排除 `PLANNED`/`ENTERED_IN_ERROR`；已退仅 `CANCELLED`；有效 = 挂号 − 已退（下限 0）。
- **边界说明**：只读；过滤与 §6.2.34 同 AMB/业务日/科室/`resolvedEncounterIds`/`statusEnum`。
- **出参**：`OutpatientRegistrationDetailStatsQueryModel`。
- **实现锚点**：`EncounterQueryService#countOutpatientRegistrationDetailReportStats`。

#### 6.2.35 门诊挂号明细报表净汇总（`summarizeOutpatientRegistrationByOrgOrDoctorAndServiceType`）

- **业务意义**：**AS-RPT-007** 汇总 Tab：按科室或**接诊医生** × 号源类型计**净**挂号笔数。
- **净口径**：有效挂号计入；**CANCELLED / PLANNED / ENTERED_IN_ERROR 不计入**（退号后状态为 CANCELLED，等价冲减）。
- **状态筛**：与 §6.2.34 同，可选 `OutpatientRegistrationReportPageQuery.statusEnum`（空不收窄）；若筛到不计入净额的状态，汇总行可为空。
- **医生维**：活动 **ADMITTER**（`mapActiveAdmitterPractitionerIdByEncounterIds`）；未接诊桶 practitioner 可空。
- **实现锚点**：`EncounterQueryService#summarizeOutpatientRegistrationByOrgOrDoctorAndServiceType`。

#### 6.2.36 住院患者入出明细报表明细分页（`pageInpatientInOutDetailReport`）

- **业务意义**：**AS-RPT-010**：IMP 入出就诊分页；一行一 Encounter；投影入院日/出院日、最早入院病区、当前/末次病区病房床、转科后病区 id 序列。
- **边界说明**：只读；**仅本聚合** Encounter + EncounterLocation；**禁止** join `adm_location`。入院科室筛：应用层经 Location QS 预写 **`resolvedAdmissionWardLocationIds`**（该科下病区），本方法按**最早 WARD** ∈ 集合命中；未分床另以主档 `organization_id` 命中。排除 `PLANNED`/`CANCELLED`。
- **入参**：`InpatientInOutDetailReportPageQuery`（`dateFrom`/`dateTo`、`movementStatus=ALL|ADMISSION|DISCHARGE`、`organizationId`、`wardLocationId`、`resolvedAdmissionWardLocationIds`、可选 `resolvedEncounterIds`）。
- **出参**：`Page<InpatientInOutDetailReportLineQueryModel>`。
- **实现锚点**：`EncounterQueryService#pageInpatientInOutDetailReport`。

#### 6.2.37 住院患者入出明细报表人次计数（`countInpatientInOutDetailPersonTimes`）

- **业务意义**：**AS-RPT-010** 页脚：入院人次（`start_time∈窗`）与出院人次（`end_time∈窗`）；按 Encounter 计；**忽略** `movementStatus`；两侧可重叠。
- **边界说明**：过滤口径与 §6.2.36 同科室/病区/排除态；只读。
- **出参**：`InpatientInOutDetailPersonTimesQueryModel`。
- **实现锚点**：`EncounterQueryService#countInpatientInOutDetailPersonTimes`。

### 6.3 领域边界说明

- `Encounter` 领域动作只描述 `Encounter` 聚合根及其子实体 `EncounterParticipant / EncounterLocation / EncounterReason / EncounterDiagnosis` 的业务行为。
- `EncounterDiagnosis` 归 `Encounter` 聚合独占写入，其他聚合不得直接写该关联。
- 产品口语中的"挂号""退号"通常是应用服务用例，往往需要编排号源、支付、退款、医保、账户等多个领域；在 `Encounter` 文档中只保留"开立门诊就诊""取消门诊就诊"等最小领域动作。
- 查询类动作无写库操作，以QueryService表达，遵循 CQRS 分离原则。

## 7. 不变式


| 编号         | 不变式                                                           | 说明         |
| ---------- | ------------------------------------------------------------- | ---------- |
| BR-ENC-001 | 创建就诊时必须存在有效的 `患者标识`、`就诊类别`、`机构科室标识`                           | 聚合初始化三要素校验 |
| BR-ENC-002 | `就诊开始时间` 只能在进入 INPROGRESS 时设置，`就诊结束时间` 只能在终止态设置，且结束时间 >= 开始时间 | 时间区间合法性    |
| BR-ENC-003 | `DISCHARGED`、`CANCELLED`、`ENTERED_IN_ERROR` 为终止状态，终止后不可恢复到非终止状态；**例外**：住院（`IMP`）经 §6.1.25 `recallInpatientDischarge` 允许 `DISCHARGED→IN_PROGRESS`（仅出院召回已清床轨，须专用聚合方法） | 终止态不可逆（含 IMP 召回豁免） |
| BR-ENC-004 | 完诊或出院前必须至少存在一名负责医生参与者                                         | 参与者约束      |
| BR-ENC-005 | 同一时刻同一就诊只能有一个活动中的位置记录（同一位置形态）                                 | 位置唯一性约束    |
| BR-ENC-006 | 若配置要求出院前必须有主诊断，则必须存在唯一**未软删**主诊记录（`primary_flag=true` 且 `is_deleted=false`） | 主诊断约束      |
| BR-ENC-007 | 业务取消不能用技术软删代替；已有外部关联的就诊不得物理删除                                 | 取消与删除分离    |
| BR-ENC-008 | 聚合更新必须带版本号，发生并发冲突时拒绝写入（乐观锁）                                   | 并发一致性      |
| BR-ENC-009 | 相同语义的重复状态变更应视为幂等成功                                            | 幂等保护       |
| BR-ENC-010 | 写操作前应先查询就诊并校验联合状态（先查再改）                                       | 状态前置断言     |
| BR-ENC-011 | 同一自然日、同一患者标识、同一目标科室（`organizationId`）下，不得存在第二条占用名额的门诊就诊（`class_enum = AMB`，且 `status_enum` 为待挂号 `PLANNED`、已交费后或诊毕等占名额态；**`CANCELLED`（退号）与 `ENTERED_IN_ERROR`（未收费取消）均释放名额**）。**例外**：`RegisterOutpatientEncounterCommand.skipSameDaySameDeptDuplicateCheck=true` 时写路径跳过 `assertNoOutpatientDuplicate`（由应用层院点策略驱动，对齐 **AS-OP-FIN-001 BR-REG-011** 放宽） | 门诊挂号防重（读查询 `checkOutpatientDuplicateRegistration` / 写路径 `assertNoOutpatientDuplicate`；可跳过） |
| BR-ENC-012 | 未指定医生参与者时，优先使用科室默认医生；若无默认医生且产品要求必须有医生，则拒绝开立                   | 缺省接诊医生     |
| BR-ENC-013 | 就诊业务号全局唯一，创建后不可修改                                             | 业务号唯一性     |
| BR-ENC-014 | 同一就诊中诊断排序号不得重复；**未软删**关联上主诊标识必须唯一。软删诊断关联时须卸 `primary_flag`；主诊冲突/存在性校验仅认活动行（`findFirstByEncounterIdAndPrimaryFlagTrueAndIsDeletedFalse`），已软删行不占名额 | 诊断排序约束     |
| BR-ENC-015 | 参与者时间区间必须落在就诊时间区间内                                            | 参与者时间约束    |
| BR-ENC-016 | 新增活动位置时必须关闭上一条活动位置并补齐结束时间                                     | 位置切换一致性    |
| BR-ENC-017 | 原因记录支持追加不删除；更正通过新增更正记录实现                                      | 原因审计追踪     |
| BR-ENC-018 | 同一 `amb_encounter_id` 下不得存在第二条未取消（`status_enum ≠ CANCELLED`）的 `IMP` 住院就诊 | 住院申请防重（读 `findOpenInpatientLine` / 写 `assertNoOpenInpatientForAmb`） |
| BR-ENC-019 | 住院 `bus_no` 与门诊 `bus_no` 须分序列发号，前缀不得共用（如住院 `ZY`、门诊 `EN`） | 业务号分域 |
| BR-ENC-021 | 经 `saveAdmissionDx` 落入院诊断关联时，`adm_encounter_diagnosis.diag_type_code` 固定为 `IptPatientDiseaseDiagType.ADMISSION`（code **`31`**）；命令项 `AdmissionConditionItem` 与应用层入院诊断 DTO **不得**携带该字段 | 入院诊断类型落库（`newAdmissionDiagnosisAssocItem`） |


## 8. 上下文映射

### 8.1 上游依赖


| 上游上下文 | 聚合                | 关联方式                          | 说明          |
| ----- | ----------------- | ----------------------------- | ----------- |
| 患者管理  | Patient           | `患者标识(PatientId)`             | 创建就诊时验证患者存在 |
| 组织架构  | Organization      | `机构科室标识(DepartmentId)`        | 就诊归属的机构/科室  |
| 位置管理  | Location          | `位置资源标识(LocationId)`          | 床位/诊室/病区资源  |
| 医务人员  | Practitioner      | `医务人员标识(PractitionerId)`      | 参与者关联的医务人员  |
| 医疗服务  | HealthcareService | `号源类型标识(HealthcareServiceId)` | 号源/排班关联     |


### 8.2 下游影响


| 下游上下文 | 聚合                | 触发方式 | 说明                                                                                    |
| ----- | ----------------- | ---- | ------------------------------------------------------------------------------------- |
| 就诊账户  | Account           | 事件订阅 | 监听 `OutpatientRegistrationPlannedEvent` / `InpatientAdmissionCompletedEvent`，自动创建就诊账户 |
| 药品申请  | MedicationRequest | 引用   | 药品医嘱通过 `就诊标识(EncounterId)` 关联就诊                                                       |
| 服务申请  | ServiceRequest    | 引用   | 服务申请通过 `就诊标识(EncounterId)` 关联就诊                                                       |
| 临床诊断  | Condition         | 事件订阅 | 监听 `EncounterDiagnosisAssociationUpsertedEvent`，触发诊断与就诊协同                             |
| 病历管理  | Composition       | 引用   | 病历文书绑定到就诊                                                                             |
| 费用管理  | ChargeItem        | 引用   | 收费明细通过 `就诊标识(EncounterId)` 关联就诊                                                       |
| 结算管理  | Invoice           | 事件订阅 | 监听 `OutpatientEncounterCompletedEvent` / `HospitalDischargedEvent`，触发结算流程             |
| 住院管理  | EpisodeOfCare     | 事件订阅 | 监听 `InpatientAdmissionCompletedEvent`，关联住院全程管理                                        |
| 就诊历史  | EncounterHistory  | 事件订阅 | 监听转科/出院等里程碑事件                                                                         |


### 8.3 引用规则

> **就诊专项**：所有需要引用就诊的领域模型，遵循以下原则：
>
> - **门诊场景**：引用 `就诊标识(EncounterId)`，`住院就诊标识(EpisodeOfCareId)` 为空
> - **住院场景**：同时引用 `就诊标识(EncounterId)` 和 `住院就诊标识(EpisodeOfCareId)`
> - 患者引用统一使用 `患者标识(PatientId)`（对应 FHIR `subject` 字段）

### 8.4 事件流示意图

```
Encounter                Account          MedicationRequest     EpisodeOfCare     EncounterHistory
    │                       │                    │                    │                  │
    │── PlannedEvent ──────>│ (创建门诊账户)      │                    │                  │
    │                       │                    │                    │                  │
    │── TriagedEvent ───────────────────────────────────────────────────────────────────>│
    │                       │                    │                    │                  │
    │── ReceptionStarted ──────────────────────>│ (可开立医嘱)        │                  │
    │                       │                    │                    │                  │
    │── Completed ─────────>│ (触发结算)          │                    │                  │
    │                       │                    │                    │                  │
    │── AdmissionCompleted >│ (创建住院账户)      │                    │ (关联住院全程)    │
    │                       │                    │                    │                  │
    │── TransferInitiated ──────────────────────────────────────────────────────────────>│
    │── TransferConfirmed ──────────────────────────────────────────>│                  │
    │                       │                    │                    │                  │
    │── Discharged ────────>│ (触发结算)          │                    │ (住院周期终结)    │
    │                       │                    │                    │                  │
```

## 9. 补充

### 9.1 限界上下文交互总览

```
               ┌─────────────────────────────────────────────────────┐
               │              患者管理上下文（BC-ADM）                 │
               │  ┌──────────┐  ┌───────────────┐  ┌────────────┐   │
               │  │ Patient  │  │  Encounter    │  │ Location   │   │
               │  │ (患者)   │  │  (就诊:聚合根) │  │ (位置)     │   │
               │  └──────────┘  └───────┬───────┘  └────────────┘   │
               │                        │                            │
               └────────────────────────┼────────────────────────────┘
                                        │
          ┌─────────────────────────────┼─────────────────────────────────┐
          │                             │                                 │
          ▼                             ▼                                 ▼
┌──────────────────┐     ┌───────────────────────┐         ┌──────────────────────┐
│ 临床诊疗上下文    │     │  财务结算上下文         │         │  诊疗工作流上下文      │
│ (BC-CLN)         │     │  (BC-FIN)              │         │  (BC-WKF)            │
│                  │     │                        │         │                      │
│ Condition        │     │ Account                │         │ MedicationRequest    │
│ AllergyIntoler.  │     │ ChargeItem             │         │ ServiceRequest       │
│ Observation      │     │ Invoice                │         │ Task                 │
│ Composition      │     │ PaymentReconciliation  │         │ CarePlan             │
└──────────────────┘     └────────────────────────┘         └──────────────────────┘
```

### 9.2 聚合内对象生命周期状态图

```
                    Encounter 聚合内对象生命周期
─────────────────────────────────────────────────────────────────
Encounter     │ PLANNED → TRIAGED → IN_PROGRESS ←→ ON_HOLD → DISCHARGED
(聚合根)       │                                                    │
              │ 任意非终止态 → CANCELLED / ENTERED_IN_ERROR         │
─────────────────────────────────────────────────────────────────
Participant   │ [创建:ACTIVE] ───────────────────────> [COMPLETED]
(参与者)       │   (接诊/分诊时创建)        (完诊/出院时或角色切换时收口)
─────────────────────────────────────────────────────────────────
Location      │ [planned] → [active] → [completed]
(位置轨迹)     │   (分床计划)  (入住)    (转床/出院时关闭)
─────────────────────────────────────────────────────────────────
Diagnosis     │ [关联] ←──────────────→ [移除]
(诊断关联)     │   (upsert 维护)         (remove 撤销)
─────────────────────────────────────────────────────────────────
Reason        │ [追加] ──> [不可删除，只可新增更正记录]
(原因)         │
─────────────────────────────────────────────────────────────────
```

### 9.3 门诊完整就诊流程

```
1. 开立就诊（registerOutpatientEncounter）
   │  → AMB + PLANNED（待挂号）
   │  → 创建就诊、写入号源类型、落默认医生
   │
2. 挂号费结清（completeOutpatientRegistration，应用编排于支付成功路径）
   │  → AMB + REGISTERED（已挂号）
   │
3. 分诊（triageEncounter，可选；无分诊机构可跳过）
   │  → AMB + TRIAGED
   │  → 护士评估分诊级别、写入分诊参与者
   │
4. 候诊（队列/医生站；不含 PLANNED 待缴费患者）
   │  → 队列叫号系统管理
   │
5. 接诊（startOutpatientReception）
   │  → AMB + IN_PROGRESS
   │  → 写入接诊医生参与者、记录接诊时间
   │
6. 诊疗过程
   │  → 开立诊断（upsertEncounterDiagnosisAssociation）
   │  → 开立医嘱（MedicationRequest / ServiceRequest 引用 EncounterId）
   │  → 记录病历（Composition 引用 EncounterId）
   │
7. 完诊（completeOutpatientEncounter）
   │  → AMB + DISCHARGED
   │  → 收口参与者、补齐结束时间
   │  → 触发下游结算
```

### 9.4 住院关键流程与 Encounter/EpisodeOfCare 协作


| 业务操作 | Encounter 职责               | EpisodeOfCare 职责      |
| ---- | -------------------------- | --------------------- |
| 住院申请 | 创建 IMP + PLANNED 就诊        | —                     |
| 预入院  | IMP + PREADMISSION         | —                     |
| 住院登记 | IMP + INPROGRESS，建立病区位置    | 创建住院全程记录，关联 Encounter |
| 在院诊疗 | 管理参与者/位置/诊断/原因             | 管理治疗团队与计划             |
| 转科   | ONHOLD → INPROGRESS，维护位置轨迹 | 记录科室级归属变更             |
| 转床   | 更新位置记录                     | —                     |
| 出院   | IMP + DISCHARGED           | 标记住院周期终结              |


### 9.6 文档变更历史

| 版本 | 日期 | 摘要 |
| --- | --- | --- |
| v1.58 | 2026-10-17 | §6.1.19a `cancelInpatientNurseDischarge`：取消护士办理出院 `PENDING→ON_HOLD/IN_PROGRESS`；对齐 **AS-IP-NUR-002** IF-IP-NUR-ATD-08（代码先行回写） |
| v1.57 | 2026-10-14 | §3.1 / §6.1.11：`charge_intent` 供登记未开户预填费用性质（医保默认市医保）；对齐 **AS-IP-FIN-001 v1.24** / **BR-IP-REG-024**（代码先行回写） |
| v1.56 | 2026-10-14 | §3.1 / §6.1.11：`charge_intent`（入院办理意向 `INSURANCE`/`SELF_PAY`）；医生提示款台，非真实费用性质。代码先行回写，对齐 **AS-OP-DOR-005** / **AS-IP-FIN-001** |
| v1.55 | 2026-10-14 | §3 `primary_flag` / §6.1.10 / **BR-ENC-006**/**BR-ENC-014**：软删诊断关联同步卸主诊；主诊唯一与存在性仅约束未软删行。代码先行回写（住院医生站医保→国临展开先删后插） |
| v1.54 | 2026-10-13 | §6.1.7b：`correctOutpatientRegistrationDepartment`（挂号窗口更正就诊科室；不改状态与参与者）。代码先行回写，对齐 **AS-OP-FIN-001** IF-REG-15 |
| v1.53 | 2026-10-13 | §2.3.1 预交金队列：不在院三态须 EXISTS 已收口预交金流水才入队。代码先行回写，对齐 **AS-IP-FIN-002 v2.7** |
| v1.52 | 2026-10-13 | §2.3.1 预交金口径：写门禁仍禁 `COMPLETED`/`DISCHARGED`/`CANCELLED`；队列只读补充 `CANCELLED`（退院）可经 **AS-IP-FIN-002** `stayScope=NOT_IN_HOSPITAL`/`ALL` 查阅。代码先行回写 |
| v1.51 | 2026-10-13 | §6.2.11a：`findPatientIdsWithActiveInpatientStay`（批量判定在院 IMP；排除取消/错误录入/完诊/已完成）。代码先行回写，对齐 **AS-OP-FIN-004 / BR-OP-DCL-032** |
| v1.50 | 2026-10-12 | §6.1.15b：`correctInpatientAdmissionDepartment`（登记窗口更正入院科室/病区/已入科床；不走转科）。代码先行回写，对齐 **AS-IP-FIN-001** IF-IP-REG-08 |
| v1.50 | 2026-10-14 | §6.2.17b：`executionQueueFilter` 待执行/已执行整单分桶（门诊项目+耗材，住院仅项目；对齐 **AS-MT-EXE-001 BR-MT-EXE-023**）；代码先行回写 |
| v1.49 | 2026-10-12 | §6.1.1 / **BR-ENC-011**：命令 `skipSameDaySameDeptDuplicateCheck` 可跳过同日同科防重（院点策略；代码先行回写）。对齐 **AS-OP-FIN-001 v2.43**。 |
| v1.48 | 2026-09-19 | §6.2.17b：`pageMedicalTechWorkstationQueue` + `MedicalTechWorkstationQueueQuery`（TECH 有活 / 待发耗材；关闭 **GAP-MT-EXE-001**） |
| v1.47 | 2026-09-07 | §6.2.9a：号源更名「复诊0元号」；应用层按 **`name`** 识别（种子默认 `bus_no=HS00000004`）。对齐 **AS-OP-FIN-001 v2.40 / BR-REG-020**。 |
| v1.46 | 2026-08-20 | §6.1.6a：明确取消接诊临床痕迹清空门禁在应用层 **AS-OP-DOR-001 IF-DOR-06 / BR-DOR-011**；领域仍仅迁状态与 ADMITTER。 |
| v1.45 | 2026-08-19 | §6.2.17：`pageNurseStationOutpatientQueue` 增 **`pendingDeviceOnly`** 待发耗材 EXISTS（分页前）；日期窗沿用 `startDate`/`endDate`（就诊开始时间）。对齐 **AS-OP-NUR-001 v1.9.8 / BR-NUR-017** |
| v1.44 | 2026-08-17 | §6.2.18 无活动床展示改为「原{房名}-{床名}（未占床）」（床父级 HOUSE）；无房名退化「原{床名}（未占床）」 |
| v1.43 | 2026-08-17 | §6.2.3a **展示参与者**（活动优先 / 末次 COMPLETED）；§6.2.18 住院上下文参与者与位置展示口径（清床收口、召回不恢复后卡片仍回显主治/责任护士） |
| v1.42 | 2026-08-14 | §6.1.19/20/21：**业务出院时间**=`end_time`=出院医嘱计划时间；清床/无床全结 **不覆盖**已有 `end_time`（与实现 `Encounter.transitToDischarged` 对齐） |
| v1.41 | 2026-08-06 | **取消接诊**：新增 §6.1.6a `cancelOutpatientReception`（`IN_PROGRESS`/`ON_HOLD`/`DISCHARGED`→`REGISTERED`）；§5.1/§5.2 增 `OutpatientReceptionCancelledEvent`；§6.1.7/`assertCancelable` 退号仅 `REGISTERED`/`TRIAGED`（已接诊须先取消接诊）。对齐 **AS-OP-DOR-001 IF-DOR-06**、**AS-OP-FIN-001 BR-REG-008**。 |
| v1.40 | 2026-08-01 | §6.2.17：`pageNurseStationOutpatientQueue` 入参 `NurseStationOutpatientQueueQuery`（可选 `orgId`→就诊 `organization_id`，空=全部开立科室；**AS-OP-NUR-001** IF-NUR-01） |
| v1.40 | 2026-08-13 | §6.2.21 滚费读模型增补 **`endTime`**；应用层记进不计出 Gate（对齐 **AS-IP-NUR-013 v1.5.0 BR-IP-ARG-008**） |
| v1.40 | 2026-08-13 | §3.1：`in_way_code` 对齐医保 **adm_way**（1/2/3/9）；`admit_source_code` 明确为入院类型，与入院途径分列 |
| v1.39 | 2026-07-27 | §6.2.36～37 住院患者入出明细报表分页/人次（**AS-RPT-010**；入院病区=最早 WARD；科室筛经 `resolvedAdmissionWardLocationIds`） |
| v1.38 | 2026-07-26 | 新增 §6.1.9b `switchPrimaryEncounterDiagnosis`（西医单行/中医病证组切换主诊；消费方 **AS-OP-DOR-002 IF-DOR-09A**、**AS-IP-DOR-002 IF-IP-DOR-07**） |
| v1.38 | 2026-07-29 | §6.2.30/32 工作量/护士就诊 scope 增补业务日窗重载（相交口径）；§6.2.31 注明 context 仅对 payment 命中就诊批量加载（报表性能） |
| v1.37 | 2026-07-26 | §6.2.19：挂号台 `pageClinicRegister` 关键字改为业务号+患者姓名（拼音/五笔），**禁止**诊断描述；对齐 **AS-OP-FIN-001 v2.28** / `EncounterContextCriteriaSupport` |
| v1.36 | 2026-07-18 | §6.2.17：**Q-OPI-001** 排序对齐 **AS-OP-DOR-001 v1.15 / BR-DOR-002**——待诊 `startTime` 升序、在诊/暂离 `receptionTime` 降序、缺省并集状态桶排序 |
| v1.35 | 2026-07-16 | **门诊取消分流**：§6.1.7a `cancelPlannedOutpatientRegistration`→`ENTERED_IN_ERROR`；退号仍 `CANCELLED`；§5.2 增事件行；§2.3/`BR-ENC-011`：两终态均释放名额；挂号列表 `CANCELLED` 展示「退号」、排除 `ENTERED_IN_ERROR`（**AS-OP-FIN-001**） |
| v1.33 | 2026-07-11 | §6.2.33 **`EncounterContextQueryService`** 跨模块 bridge 单条就诊上下文（**zxplat-module-chs-jilin** 门诊/住院结算 ContextLoader） |
| v1.34 | 2026-07-16 | **出院召回 redesign**：§6.1.25 `DISCHARGED→IN_PROGRESS`+INSERT WARD（无床）；**FIN-004 IF-IP-RCL-01**；§6.1.20 无活动床全结→`DISCHARGED`；**BR-ENC-003** 豁免边更新 |
| v1.33 | 2026-07-11 | §6.1.20a `revertInpatientFinancialSettlement`：**出院全结反结** `COMPLETED→IN_PROGRESS`；§2.3.1 增补未清床反结轨；§1.5 增补 **AS-IP-FIN-004** |
| v1.32 | 2026-07-09 | §6.2.21 **`bedAssignedAt`** 语义改为全部 BED 轨迹最早 `start_time`；滚费 Gate 锚就诊 `start_time`（转床不更新入院时间） |
| v1.31 | 2026-07-03 | §6.2.32 护士工作量就诊 scope（**AS-RPT-002**） |
| v1.31.1 | 2026-07-04 | **BR-ENC-011** / §6.2.9：占名额状态**含 `PLANNED` 待挂号**；仅 `CANCELLED` 退号/撤销释放；读/写侧共用 `EncounterStatus#outpatientDuplicateBlockingStatusCodes` |
| v1.30 | 2026-07-03 | §6.2.30～31 工作量就诊 scope / 上下文 / 挂号医生过滤（**AS-RPT-001** 关闭 **GAP-RPT-WKL-003**） |
| v1.30 | 2026-07-09 | §6.2.21 滚费读模型增补 **`bedAssignedAt`**（**BR-IP-ARG-007/008**；成码 v1.0.9 修正语义见 v1.32） |
| v1.29 | 2026-06-19 | §6.2.21 **`listInpatientAutoRollEncounterContexts`** + **`InpatientAutoRollEncounterQueryModel`**（**AS-IP-NUR-013** 关闭 **GAP-IP-ARG-002**） |
| v1.28 | 2026-06-18 | 文档对齐成码：§6.1.25 / §2.3.1 应用层锚点 **`RecallReverseSettlesupport**（对称 FIN-004，非跨 AS 调 `InpatientSettleAppService`） |
| v1.27 | 2026-06-18 | **出院召回**：§6.1.25 `recallInpatientDischarge`；§6.2.16a `findLastCompletedWardLocationIdByEncounterId`；§2.3.1 逆向边；**BR-ENC-003** IMP 召回豁免；§6.2.18 护士站 `nurseStationStatusFilter` |
| v1.26 | 2026-06-16 | **中医 Tab（AS-OP-DOR-010）**：新增 §6.1.9a `batchUpsertTcmEncounterDiagnosisAssociation`（仅软删 `tcm_flag=true` 子集，不误删西医关联） |
| v1.25 | 2026-06-12 | §6.1.1：开立门诊指定医师时写入活动 **`REGISTRATION_DOCTOR`** 参与者，**禁止**开立阶段写 **`ADMITTER`**（与 **BR-DOR-009** / `AS-OP-DOR-001` v1.14 对齐）。 |
| v1.24.1 | 2026-05-28 | **M1 对账**：§6.1/§6.2 与 `EncounterDomainService`/`EncounterQueryService`/`EncounterContextFacade` 白名单复核通过（无新增领域方法） |（已迁至 EncounterContextReader / application.shared.encounter）
| v1.24 | 2026-05-28 | ReadModel 废止：`EncounterQueryService` 收口为就诊主档/子表只读；§6.2.18～6.2.20 就诊上下文/挂号改 **应用层 `EncounterContextFacade`**；废止 **InpatientInfo/OutpatientInfo** 读聚合表述；对齐 doc **10-就诊上下文读组装** 与 `zxplat` |（已迁至 EncounterContextReader / application.shared.encounter）
| v1.23 | 2026-05-26 | §1.5 下游应用服务（**AS-IP-FIN-003** / **AS-IP-NUR-002**）全链路追溯 |
| v1.22 | 2026-05-26 | §6.1.21～24 住院出院链路与实现对齐；§5.1 增补 `InpatientFinancialSettlementCompletedEvent`；§6.1.23 `completeInpatientFinancialSettlement`（**AS-IP-FIN-003**）；取消登记/护士站保存顺延 **§6.1.25/§6.1.26**；删除重复「财务全结」段落 |
| v1.21 | 2026-05-20 | §3.5 `diag_type_code` 对齐 String/code 口径；新增 **BR-ENC-021**：`saveAdmissionDx` 写入入院诊断关联时 `diag_type_code` 固定 `IptPatientDiseaseDiagType.ADMISSION`（`31`）；§6.1.14/§6.1.16a 聚合影响补述。 |
| v1.20 | 2026-05-20 | §6.2.13：活动位置解析收口为 `requireActiveLocationId`（`form_enum` 区分床/病房）；删除原 §6.2.14 双方法描述；写侧 `requireActiveBedId` 委托读侧（**AS-IP-NUR-002-SDD** v1.8）。 |
| v1.19 | 2026-05-20 | §5.1 / §6.1.14：`InpatientAdmissionRequestedEvent` 载荷增补 `wardId`（申请 Request **`wardId`**）；待登记投影 `ward_id` 见 **§6.2.19** / **InpatientEncounterContextQueryModel** |
| v1.18 | 2026-05-20 | §5.1：`InpatientAdmissionCompletedEvent` 载荷表对齐 Java（`statusEnum`/`startTime`/`busNo`/`wardId`）；读侧人口学由 **`PatientQueryService`** 回查（**§6.2.19**，废止 InpatientInfo RM） |
| v1.14 | 2026-05-20 | §6.1.17～18a：分床命令增 **`priorityEnum`/`participants`**；转床校验活动床与 **`toFormEnum`**；新增 **`swapInpatientBeds`**（对齐 **AS-IP-NUR-002** GAP-IP-ATD-001/002/004）。 |
| v1.15 | 2026-05-20 | §6.1.17～18a/24：位置 **WARD 保留**、**HOUSE+BED** COMPLETED+INSERT；命令增 **`houseLocationId`** / **`toHouseLocationId`**；参与者双入口与禁止 UPDATE 轨迹契约（对齐 **AS-IP-NUR-002** §1.7）。 |
| v1.16 | 2026-05-20 | §6.1.16：有申请登记时病区 **upsert**（与 §6.1.14 已写 WARD 同 id 幂等，换病区 COMPLETED+INSERT）。 |
| v1.17 | 2026-05-20 | 新增 **§2.3.2 IMP 状态展示文案专规**：禁止 IMP 记录使用 `EncounterStatus#getDisplay()`；统一 `InpatientEncounterStatusDisplayUtil`；`ON_HOLD` 按 `transfer_org_id`/`discharge_type_enum` 分流待转科/待出院。 |
| v1.13 | 2026-05-20 | §5.1 / §6.1.24：新增 **`saveNurseStationPatient`**、`SaveNurseStationPatientCommand`、`NurseStationPatientSavedEvent`（对齐 **AS-IP-NUR-001** IF-IP-NUR-04 / **BR-IP-NUR-010**；禁止改床改状态）。 |
| v1.12 | 2026-05-19 | §6.1.16/§6.1.16a **入院诊断分工**：申请已落主诊时登记跳过 `saveAdmissionDx`；直登在本命令内唯一写诊断；对齐 **AS-IP-FIN-001** **BR-IP-REG-007**、**BR-ENC-014**。 |
| v1.12 | 2026-10-13 | QueryService 增补 **`listEncounterIdsByWardLocationIds`**（WARD 轨迹含已收口；**AS-IP-NUR-010** 汇总单管辖病区门控；代码先行回写）。 |
| v1.11 | 2026-05-19 | **IMP 状态机**：§2.3.1 专规；登记 `PLANNED→REGISTERED`；**无申请直登**不经 `PLANNED`、登记后亦为 `REGISTERED`；分床 `REGISTERED→IN_PROGRESS`；转科确认回 `REGISTERED`；出院/结算/清床链路与预交金门禁交叉引用。 |
| v1.10 | 2026-05-18 | 入院诊断命令项类型重命名：`AdmissionDxItem` → **`AdmissionConditionItem`**（`command/AdmissionConditionItem.java`）。 |
| v1.9 | 2026-05-18 | 住院登记：`RegisterInpatientEncounter` 扩展病区/诊断/账户；`InpatientAdmissionCompletedEvent` 载荷补 `statusEnum/startTime/busNo`；费用性质经 **DM-ADM-009** `adm_account.contract_no` 锚定。 |
| v1.8 | 2026-05-18 | 住院申请：双 Encounter INSERT、`amb_encounter_id`、住院/门诊分前缀发号；`requestInpatientAdmission` 写参与者/病区/入院诊断；`InpatientAdmissionRequestedEvent` 载荷扩充；读侧 `assertAmbForInpatientReq`、`findOpenInpatientLine`；**BR-ENC-018/019**。 |
| v1.7 | 2026-05-10 | §6.2.9 / **BR-ENC-011**：门诊重复挂号防重改为 **同一自然日 + 患者 + 目标科室**；占名额状态含诊毕（`DISCHARGED`）等、`PLANNED` 不占、`CANCELLED` 释放；类别含 **AMB**；「同日」锚定 `created_at`。对齐 `EncounterRepository#existsPaidOutpatientEncounterForDepartmentOnCalendarDay` 实现。 |
| v1.6 | 2026-05-09 | §5.1 增补 `OutpatientRegistrationConfirmedEvent`；§5.2/§6.1.2：`completeOutpatientRegistration` 落库后发布该事件；QueryService `REGISTERED` 与支付 `OutpatientPaymentSettledEvent` 解耦（对齐 `DM-FIN-002` v1.3）。 |
| v1.5 | 2026-05-02 | §6.1.12 / §6.1.13 增补门诊医生站诊断录入（`AS-OP-DOR-002` IF-DOR-08/09）编排消费方说明，与 `zxplat` 及 `AS-OP-DOR-002-SDD` 写顺序对齐。 |
| v1.4 | 2026-05-02 | §5.1 `OutpatientRegistrationPlannedEvent` 展示快照补充 **`pyStr`/`wbStr`**（命令项装配，领域 `registerOutpatientEncounter` 写入事件；门诊信息领域事件后查询刷新与 **Q-OPI-001** 关键字查询对齐，见 `DM-ADM-002 §6.2 EncounterQueryService（OutpatientEncounterContextQueryModel）` v2.9）。 |
| v1.3 | 2026-05-02 | §5.1 `OutpatientRegistrationPlannedEvent` 载荷补充 `statusEnum`（由领域服务取聚合根状态写入，QueryService从事件读取，禁止硬编码）；§6.1.1 事件输出说明补充 `statusEnum` 契约；§6.1.2 曾约定由支付事件携带就诊状态（**已由 v1.6 废止**，改 `OutpatientRegistrationConfirmedEvent`）。 |
| v1.2 | 2026-05-02 | 对齐挂号生命周期：**`REGISTERED`（已挂号）**；`PLANNED` 调整为门诊待挂号/待结清；§2.3 状态机、§2 状态表 value 与 `EncounterStatus` sortOrder；§4.6 `canStartReceptionDirectly`；§5.2 触发规则；新增 **§6.1.2 `completeOutpatientRegistration`**；§6.1.3～6.1.23 顺延编号；§6.1.3/6.1.4 与 `assertTriagedFromPlanned`/`assertReceptionAllowed` 一致；§9.3 门诊主流程。 |
| v1.1 | 2026-05-02 | §5.1 / §6.1.1 / §6.1.3：对齐实现——`OutpatientRegistrationPlannedEvent` 增补展示快照与 `registrationDoctor*`；`OutpatientReceptionStartedEvent` 统一 **`doctorId`/`doctorName`** 载荷命名及消费方（含挂号QueryService）。 |
| v1.0 | （既有） | 初版骨架与事件表 |

<!-- MODE-B-METHOD-INVENTORY 2026-09-23 -->

## 附录 · 模式 B 已实现方法清单（2026-09-23）

> 自 zxplat-domain 对应 *DomainService / *QueryService 抽取 public 方法名，用于关闭方法白名单 DOC-DRIFT。细规仍以正文 §6 与源码为准；本表不替代九章成稿。

### DomainService：`EncounterDomainService`

| 方法 |
|------|
| `registerOutpatientEncounter` |
| `registerPhysicalExamEncounter` |
| `voidPhysicalExamEncounter` |
| `finishEncounter` |
| `startOutpatientReception` |
| `cancelOutpatientReception` |
| `markOutpatientLeave` |
| `completeOutpatientEncounter` |
| `completeOutpatientRegistration` |
| `cancelPlannedOutpatientRegistration` |
| `correctOutpatientRegistrationDepartment` |
| `cancelOutpatientRegistration` |
| `upsertEncounterDiagnosisAssociation` |
| `batchUpsertEncounterDiagnosisAssociation` |
| `batchUpsertTcmEncounterDiagnosisAssociation` |
| `removeEncounterDiagnosisAssociation` |
| `switchPrimaryEncounterDiagnosis` |
| `requestInpatientAdmission` |
| `startPreAdmission` |
| `registerInpatientEncounter` |
| `registerDirectInpatientEncounter` |
| `assignInpatientBed` |
| `confirmBedTransfer` |
| `correctInpatientAdmissionDepartment` |
| `swapInpatientBeds` |
| `initiateDepartmentTransfer` |
| `confirmDepartmentTransfer` |
| `dischargeInpatientEncounter` |
| `revertTransferOrDischargeHold` |
| `executeInpatientNurseDischarge` |
| `cancelInpatientNurseDischarge` |
| `completeInpatientFinancialSettlement` |
| `revertInpatientFinancialSettlement` |
| `recallInpatientDischarge` |
| `clearDischargedBed` |
| `saveNurseStationPatient` |
| `cancelInpatientAdmission` |

### QueryService：`EncounterContextQueryService`

| 方法 |
|------|
| `mustGetOutpatientContextByEncounterId` |
| `mustGetInpatientContextByEncounterId` |
| `getOutpatientContextByEncounterId` |
| `getInpatientContextByEncounterId` |
| `batchLoadOutpatientContexts` |
| `batchLoadInpatientContexts` |
| `listOutpatientContextsByEncounterIdsOrdered` |
| `listInpatientContextsByEncounterIdsOrdered` |

### QueryService：`EncounterQueryService`

| 方法 |
|------|
| `loadEncounterById` |
| `mustGetEncounter` |
| `existsActivePeEncounter` |
| `listActivePeEncounters` |
| `existsPeEncounterInCalendarYear` |
| `existsAnyNonCancelledPeEncounter` |
| `listPeEncounterEntitiesByPatientId` |
| `findOptionalEncounterEntityById` |
| `countInpatientAdmissionsBefore` |
| `mustGetEncounterOfClass` |
| `requireEncounterEntityOfClass` |
| `loadEncounterByBusNo` |
| `listEncounterIdsByBusNoKeyword` |
| `listEncounterIdsByPatientIds` |
| `listEncounterIdsByWardLocationIds` |
| `listEncounterIdsByActiveAdmitterPractitionerIds` |
| `mapActiveAdmitterPractitionerIdByEncounterIds` |
| `listOutpatientAmbEncounterIdsForCashierScope` |
| `listOutpatientAmbEncounterIdsByOrganization` |
| `listInpatientImpEncounterIdsByOrganization` |
| `listInpatientInHospitalEncounterIds` |
| `listInpatientInHospitalEncounterIdsByActiveBedLocationIds` |
| `filterOutpatientAmbEncounterIds` |
| `listPeEncounterIdsForCashierScope` |
| `filterPeEncounterIds` |
| `filterInpatientInHospitalEncounterIds` |
| `listActiveEncounterParticipants` |
| `listActiveEncounterParticipantsByType` |
| `listDisplayEncounterParticipants` |
| `listDisplayEncounterParticipantsByType` |
| `listEncounterLocations` |
| `findFirstBedAssignedAt` |
| `findBedClearedAt` |
| `getEncounterLocationInfo` |
| `listEncounterDiagnoses` |
| `listEncounterDiagnosesByIds` |
| `assertBindableEncounterDiagnosisIds` |
| `countAdviceOrChargeRefByEncounterDiagnosisId` |
| `listActiveAssociationsByConditionDefinitionIds` |
| `mapMedTypeByEncounterDiagnosisIds` |
| `mapPreferredMedTypeByEncounterIds` |
| `listEncounterDiagnosisIdsByMedTypeAndEncounterIdIn` |
| `listEncounterDiagnosisEntitiesByIds` |
| `findOptionalEncounterEntityByBusNo` |
| `queryEncounterProjections` |
| `checkOutpatientCompletable` |
| `checkOutpatientDuplicateRegistration` |
| `existsQualifyingHistoricalFollowUpPriorVisit` |
| `existsPaidOutpatientEncounterForDepartmentOnCalendarDay` |
| `findOpenInpatientLine` |
| `existsOpenInpatientLine` |
| `resolveLinkedEncounterIds` |
| `assertAmbForInpatientReq` |
| `mapAmbEncounterIdByEncounterIds` |
| `mapEncounterBusNoByIds` |
| `mapBusNoByEncounterIds` |
| `mapPatientIdByEncounterIds` |
| `mapPriorityEnumByEncounterIds` |
| `mapActiveBedEncounterIdByLocationIds` |
| `requireActiveLocationId` |
| `pageEncounterEntities` |
| `pageMedicalTechWorkstationQueue` |
| `countEncounterEntities` |
| `countDistinctBusNoEncounterEntities` |
| `listEncounterEntities` |
| `listEncounterEntitiesByIds` |
| `findInpatientEncounterEntityById` |
| `listInpatientEncounterEntitiesByIds` |
| `listActiveParticipantEntitiesByEncounterIds` |
| `listDisplayParticipantEntitiesByEncounterIds` |
| `listActiveEncounterLocationEntitiesByEncounterIds` |
| `mapDisplayWardLocationIdByEncounterIds` |
| `mapDisplayBedLocationIdByEncounterIds` |
| `findLastCompletedWardLocationIdByEncounterId` |
| `mapLastCompletedWardLocationIdByEncounterIds` |
| `mapLastCompletedBedLocationIdByEncounterIds` |
| `listInpatientAutoRollEncounterBaseContexts` |
| `listEncounterIdsForPractitionerWorkloadScope` |
| `listEncounterIdsForNurseWorkloadScope` |
| `mapPractitionerWorkloadEncounterContextByIds` |
| `filterEncounterIdsByActiveRegistrationDoctor` |
| `mapActiveRegistrationDoctorByEncounterIds` |
| `filterEncounterIdsByActiveAdmitterDoctor` |
| `pageOutpatientRegistrationDetailReport` |
| `countOutpatientRegistrationDetailReportStats` |
| `summarizePractitionerWorkloadRegistrationByOrgAndServiceType` |
| `listEncounterIdsForRegistrationBusinessWindow` |
| `summarizeOutpatientRegistrationByOrgOrDoctorAndServiceType` |
| `pageInpatientInOutDetailReport` |
| `countInpatientInOutDetailPersonTimes` |

