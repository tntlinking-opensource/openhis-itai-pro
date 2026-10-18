# DM-MED-003 MedicationRequest 药品申请

> **限界上下文**：用药域 · 药品申请管理（药品医嘱）  
> **聚合类型**：单表聚合（物理表 `med_medication_request`；中药付数、代煎、中医标识及味数/煎法等扩展均在主表字段与 `content_json` 承载）  
> **版本**：v1.3.38

---

## 1. 概述

### 1.1 业务定义

药品申请（MedicationRequest）是 HIS 系统中**最核心的聚合之一**，表达一次药品医嘱/处方从**开立（草稿）→ 签发 → 校对 → 执行 → 终止**的完整生命周期。它承载用药医嘱的核心业务字段（药品、剂量、用法、频次、途径、数量、治疗类型等），是药品发放、费用计费、执行记录等下游领域对象的**稳定引用锚点**。

> **医生站开立（与 `DM-CUS-006` / `DM-MED-002` 对齐，AS-IP-DOR-003 / AS-OP-DOR-003）**：**暂存**时**一条编辑行对应一条** `MedicationRequest`（单请求事实）；**`org_id`** 承载**开立/接诊科室**（**`adm_organization.id`**），**不在请求上固化最终发放药房**（`perform_location` 存 **`adm_location.id`** 库位，医生站草稿可空，见 §3 字段表与 **BR-MRQ-029**）。**暂存费用草稿定价**时，应用层 IF-DOR-14 / IF-IP-DOR-14 可在请求载荷中携带 **`dispenseLocationId`**（目录 IF 已解析库位），经 **`ClinicalCatalogPriceResolveSupport#resolveDraftChargePriceLine`** 取 FIFO 批号售价——**仅**用于 `ChargeItem` 草稿择价，**不**写入本聚合 `perform_location`（**BR-MRQ-030**）。查询/展示/过滤「开立科室」**只读 `org_id`**，**禁止**以 `perform_location` 代替组织主键。**门诊临时**与**住院临时**暂存路径一致：仅持久化请求 + **ChargeItem** 草稿，**不在暂存**调用 `MedicationDispenseDomainService#generateDispenseFromTemporaryOrder`。**签发**后由应用编排按模板候选 + FIFO（`replaceFifoDraftDispensesAfterDoctorStationSign`）生成**一条或多条** `MedicationDispense`，**实际库房只写在发放行**。**住院长期**暂存/签发**不**建 `ChargeItem`、**不**建药品 `Dispense`（BR-IP-ORD-005）。

在 FHIR R5 中，MedicationRequest 通过 `intent` 字段区分请求意图（`order` = 正式医嘱、`original-order` = 原始医嘱、`reflex-order` = 系统自动派生医嘱），通过 `status` 字段表达医嘱在授权流程中的位置。

### 1.2 核心职责


| 职责     | 说明                              |
| ------ | ------------------------------- |
| 药品医嘱承载 | 记录药品、剂量、用法、频次、途径、数量等核心信息        |
| 生命周期管理 | 管理医嘱从开立→签发→校对→执行→停嘱/取消的状态流转     |
| 处方管理   | 生成处方号、签发编码，支持门诊/住院/急诊/出院带药等处方类型 |
| 成组医嘱   | 通过分组号支持成组输液等场景（溶媒 + 药品同时执行）     |
| 中西药分轨  | 西药/中成药与中药饮片在开立、修改路径上分轨，签发后共用    |
| 计费联动锚点 | 作为收费明细（ChargeItem）的引用来源，驱动计费与结算 |
| 合规管控锚点 | 承载合理用药审核、医保限制、毒麻精放管控等约束         |


### 1.3 适用范围


| 就诊类型 | 处方类型   | 说明                   |
| ---- | ------ | -------------------- |
| 门诊就诊 | 门诊处方   | 业务上等价临时医嘱，典型配合用药天数   |
| 住院就诊 | 住院长期医嘱 | 单次用量语义，支持首日量，持续执行至停嘱 |
| 住院就诊 | 住院临时医嘱 | 总量语义，频次须为「立即」        |
| 急诊就诊 | 急诊处方   | 用量限制更严格（一般不超过 3 日）   |
| 出院场景 | 出院带药处方 | 出院时开立的药品处方           |


### 1.4 与 ServiceRequest 的关系


| 维度   | MedicationRequest                                | ServiceRequest                |
| ---- | ------------------------------------------------ | ----------------------------- |
| 管理范围 | **药品类**医嘱（西药、中成药、中药饮片）                           | **非药品类**医嘱（检验、检查、治疗、手术等）      |
| 处方分方 | 药品处方分方（BR-MRQ-015 ～ 017）                         | 服务申请分方（见 ServiceRequest §3.3） |
| 分方约束 | **禁止**在同一算法中与服务申请混算                              | **禁止**在同一算法中与药品申请混算           |
| 合规管控 | 药品轨（BR-MRQ-018 ～ 020）                            | 服务轨（见 ServiceRequest §3.4）    |
| 共同锚点 | 均以 `就诊标识(EncounterId)` 和 `患者标识(PatientId)` 为关联基础 | 同左                            |


---

## 2. 聚合根与持久化形态

### 2.1 持久化形态

**持久化形态**：单表（扩展字段含 `content_json` 等，见 §3）。

聚合根对应表 `med_medication_request`，主键 `id`；一行表示一条药品医嘱/处方申请。中药付数、代煎、中医标识及味数/煎法等扩展均在主表与 `content_json` 承载，以落库实现与本稿约定为准。

**高频查询模式（建议索引口径）**：按 `encounter_id`、`patient_id`、`status_enum`、`prescription_no`、开立时间、成组号；护士校对台按就诊与状态筛选。

### 2.2 聚合根标识


| 属性     | 类型                    | 说明          |
| ------ | --------------------- | ----------- |
| 药品申请标识 | `MedicationRequestId` | 全局唯一标识，系统生成 |


### 2.3 状态机

```
                        ┌────────签退────────┐        ┌────────退回────────┐
                        │                    │        │                    │
                        v                    │        v                    │
  [DRAFT 草稿] ──签发──> [ACTIVE 已发送] ──校对──> [VERIFIED 已校对]
       │                     │                         │
       │                     │                         ├──(履约收口/退回等)──> [COMPLETED 已完成] 等
       │                     ├──取消──> [CANCELLED 已取消] <──取消──┘
       └──移除(物理删除)        │
                             └──停嘱(仅长期)──> [STOPPED 已停嘱]

  门诊收费收口：ACTIVE ──（不进 VERIFIED）──> COMPLETED（见实体 `activeToCompletedAfterOutpatientCharge`）。
  共享枚举全集与 sortOrder 对照见 §4.1；落库均为 RequestStatus#getCode() 字符串。
```

#### 状态定义

持久化 **`status_enum`** 存 **`RequestStatus#getCode()`**。下表 **`sortOrder`** 来自 **`com.zxplat.domain.share.enums.RequestStatus`**，**仅对照排序，不是落库值**。

| sortOrder（对照） | **落库 code** | 中文 display | 语义（本聚合常见路径） |
| ----- | ---- | ---- | ---- |
| 1 | `DRAFT` | 待签发 | 草稿；允许签发、修改、移除（未签发物理删除策略见 BR） |
| 2 | `ACTIVE` | 已发送 | 签发后；允许签退、校对、取消、停嘱（长期）、门诊收费收口至 COMPLETED |
| 3 | `VERIFIED` | 已校对 | **护士/病区校对**：实体方法 `activeToCompleted` 落此态（方法名历史遗留，语义为已校对） |
| 4 | `HOLD` | 暂挂 | 暂停 |
| 5 | `CANCELLED` | 已取消 | 取消保留记录 |
| 6 | `STOPPED` | 已停嘱 | 长期停嘱，写入服药结束时间 |
| 7 | `COMPLETED` | 已完成 | **门诊收费完成**等路径：`activeToCompletedAfterOutpatientCharge`；履约收口以实现编排为准 |


> **状态枚举说明**：与 **`DM-WKF-004` / `DM-WKF-003`** 共用 **`RequestStatus`**。`cancelled` 表示取消保留；`stopped` 表示停嘱；**`VERIFIED` 与 `COMPLETED` 不得混用**——门诊收费收口不进 `VERIFIED`（见实体 Javadoc）。

---

## 3. 实体

```text
MedicationRequest（聚合根，单表）
════════════════════════════════════════════════════════════

  ┌──────────────────────────────────────────────────────┐
  │                 MedicationRequest                      │
  │  - bus_no / prescription_no / status_enum / medication_id │
  │  - encounter_id / patient_id / quantity / therapy_enum …   │
  │  - perform_location / content_json / tcm_flag …            │
  └──────────────────────────────────────────────────────┘
```

### 3.1 药品申请（MedicationRequest）

药品申请聚合根属性，对应 `med_medication_request` 


| 属性          | 字段名                           | 类型         | 必填  | 说明                                                                                    |
| ----------- | ----------------------------- | ---------- | --- | ------------------------------------------------------------------------------------- |
| 药品申请标识      | `id`                          | `Long`     | 是   | 聚合根主键（雪花 ID）                                                                          |
| 业务号         | `bus_no`                      | `String`   | 是   | 药品申请业务号，全局唯一；`BusinessSequenceService.getSeqByDay`，前缀 **`AggregateTypeConstants.MEDICATION_REQUEST`（`MR`）** |
| 处方号         | `prescription_no`             | `String`   | 否   | 门诊分方/签发写入；`getSeqByDay`，前缀 **`AggregateTypeConstants.COMPOSITE_PRESCRIPTION`（`CP`）**；药房与打印按此聚合 |
| 分组号         | `group_id`                    | `Long`     | 否   | 成组医嘱（如成组输液）                                                                           |
| 组套标识        | `package_id`                  | `Long`     | 否   | 关联组套                                                                                  |
| 申请状态        | `status_enum`                 | `String`  | 是   | `RequestStatus`（§4.1，存枚举 code）                                                             |
| 状态原因        | `status_reason`               | `Integer`  | 否   | 状态变更原因                                                                                |
| 状态变更时间      | `status_changed_time`         | `DateTime` | 否   | 状态变更时间戳                                                                               |
| 请求意图        | `intent_enum`                 | `String`  | 是   | 请求意图（存枚举 code；数据字典/前端口径，以落库与交互契约为准）                                                                 |
| 请求类别        | `category_enum`               | `String`  | 是   | 与药品类别相同语义的请求类型（存枚举 code）                                                                        |
| 优先级         | `priority_enum`               | `String`  | 否   | `PriorityLevel`（§4.4，存枚举 code）                                                                 |
| 是否停止执行      | `perform_flag`                | `Boolean`  | 否   | 标志位：`false` 否 / `true` 是（以落库与交互契约为准）                                                       |
| 药品定义标识      | `medication_id`               | `Long`     | 是   | 关联药品主数据                                                                               |
| 患者标识        | `patient_id`                  | `Long`     | 是   | 关联患者                                                                                  |
| 就诊标识        | `encounter_id`                | `Long`     | 是   | 关联就诊                                                                                  |
| 开立医生标识      | `practitioner_id`             | `Long`     | 是   | 开立医生                                                                                  |
| 所在位置标识      | `location_id`                 | `Long`     | 否   | 所在位置（实现侧；与发放药房分列）                                                                     |
| 开立科室标识      | `org_id`                      | `Long`     | 是   | **组织主键**（`adm_organization.id`）；开立/接诊科室；处方 slip 展示「开单科室」、历史处方按科室过滤、模板候选库房解析入参均读此列（**BR-MRQ-029**） |
| 发放药房库位标识    | `perform_location`            | `Long`     | 否   | **库位主键**（`adm_location.id`）；已绑定具体发放药房时使用（如护士划价路径）；**医生站开立路径**草稿/暂存可为空，不在请求上固化最终发放药房；签发 FIFO 后事实库房以 **`MedicationDispense.location_id`** 为准。分方 P1 以签发 FIFO **首段库位**（`PrescriptionSplitLineContext.pharmacyLocationId`）判定，**非**将 `perform_location` 当科室 |
| 执行科室标识      | `perform_org`                 | `Long`     | 否   | 执行科室                                                                                  |
| 执行人类型       | `performer_enum`              | `String`  | 否   | 数据字典/前端口径（存枚举/字典 code）                                                                             |
| 执行人标识       | `performer_id`                | `Long`     | 否   | 执行人                                                                                   |
| 设备定义标识      | `device_def_id`               | `Long`     | 否   | 关联设备                                                                                  |
| 记录人标识       | `recorder_id`                 | `Long`     | 否   | 记录人                                                                                   |
| 开立时间        | `req_opened_time`             | `DateTime` | 否   | **首次建草稿/直建落库写一次**；签发、改草稿、签退**不覆盖**；复制/退药重开新行写新行创建时刻；住院可由 **改业务时间**（§6.1.6c / **IF-IP-DOR-26**）覆盖；**不**因改时变更开立人；与诊疗/耗材同构                                                                 |
| 签发时间        | `req_authored_time`           | `DateTime` | 否   | **仅签发**（及无草稿直建完成态）写入；草稿暂存**不写**本列；与诊疗/耗材同构；**仅系统追溯**，列表/报表**默认不展示**；**不作**业务排序主轴；**禁止**经改业务时间接口修改                                                               |
| 是否报告        | `report_flag`                 | `Boolean`  | 否   | 标志位：`false` 否 / `true` 是（以落库与交互契约为准）                                                       |
| 支持用药信息      | `support_info`                | `String`   | 否   | 支持用药信息                                                                                |
| 请求原因        | `reason_json`                 | `String`   | 否   | 请求原因 JSON（结构以落库与交互契约为准）                                                                    |
| 治疗类型        | `therapy_enum`                | `String`  | 是   | `TherapyType`：长期 / 临时 / **出院带药**（§4.2，存枚举 code）                                                       |
| 服药开始时间      | `effective_dose_start`        | `DateTime` | 否   | 用药开始时间（临床计划窗）                                                                                |
| 服药结束时间      | `effective_dose_end`          | `DateTime` | 否   | 用药计划结束时间（临床计划窗；**停嘱不写本列**）                                                                           |
| 停嘱时间        | `stop_time`                   | `DateTime` | 否   | 长期停嘱写入；取消停嘱清空；执行 Gate / 频次右界读本列；住院可由改业务时间覆盖（§6.1.6c）；**不**因改时变更停嘱人；与诊疗/耗材同构                                                               |
| 停嘱人标识       | `stopper_id`                  | `Long`     | 否   | **停嘱操作人**（医务人员主键）；停嘱写入；取消停嘱清空；改停嘱时间**不**改本列；与诊疗同构                                                               |
| 停嘱校对人标识    | `stop_performer_check_id`     | `Long`     | 否   | **停嘱校对人**（护士 Practitioner）；停嘱校对写入；取消停嘱校对/取消停嘱/新停嘱清空；改停嘱时间**保留**；空=停嘱待校或未停嘱；与诊疗同构 |
| 停嘱校对时间      | `stop_check_time`             | `DateTime` | 否   | 与 `stop_performer_check_id` 成对；改停嘱时间**不**改本列；与诊疗同构 |
| 数量          | `quantity`                    | `Decimal`  | 是   | **临时/出院带药**=总量；**长期**=单次用量（拆零/最小单位；同名接参、语义分治，§3.3、BR-MRQ-009）。**用量不一致**时仍可写序列首项作兜底；按槽发药以 `quantity_schedule` 为准（空则仍用本列，**BR-MRQ-035**） |
| 执行次数        | `execute_num`                 | `Integer`  | 否   | 执行次数                                                                                  |
| 数量单位编码      | `unit_code`                   | `String`   | 是   | 与 `quantity` 配对：临时为总量单位，长期为单次用量单位                                                     |
| 用法编码        | `method_code`                 | `String`   | 是   | 用法                                                                                    |
| 频次编码        | `rate_code`                   | `String`   | 是   | 用药频次                                                                                  |
| 单次剂量        | `dose`                        | `Decimal`  | 否   | 单次剂量（**计量单位**口径；与规格展示配套）。**用量一致**时承载唯一单次剂量；**用量不一致**时本列可空或仅存序列首项作兜底，展示/按槽读量以 `dose_schedule` 为准（**BR-MRQ-035**） |
| 剂量单位编码      | `dose_unit_code`              | `String`   | 否   | 剂量单位（计量）；**用量不一致时仍必填且各次相同**——schedule 不另存单位列                                                                                  |
| 剂量序列        | `dose_schedule`               | `String`   | 否   | **仅长期西药/中成药·用量不一致**：逗号分隔的**计量**剂量序列，顺序=当日频次执行点**早→晚**（例 `"2,1"`）；**不存**时刻；空=走原 `dose` 路径（**BR-MRQ-035**） |
| 单次最大剂量      | `max_dose`                    | `Decimal`  | 否   | 单次最大剂量                                                                                |
| 首日用量        | `first_dose`                  | `Decimal`  | 否   | **仅长期**：首次执行用量（负荷量等）；**不是**长期「单次用量」（§3.3、BR-MRQ-010）；**落库数量为最小/拆零单位**（开立 UI 可按计量录入后换算）                                          |
| 首日用量单位编码    | `first_dose_unit_code`        | `String`   | 否   | 与 `first_dose` 配对；**须为最小/拆零单位编码**（与长期 `unit_code` 同口径），禁止直接落计量单位码                                                                     |
| 数量序列        | `quantity_schedule`           | `String`   | 否   | **仅长期西药/中成药·用量不一致**：逗号分隔的**拆零/最小单位**用量序列，与 `dose_schedule` **一一对应、同序**（由各计量项按原换算写出）；空=走原 `quantity` 路径（**BR-MRQ-035**） |
| 首次持续时间      | `first_duration`              | `String`   | 否   | 实现侧字段                                                                                 |
| 给药间隔        | `dispense_interval`           | `String`   | 否   | 给药间隔                                                                                  |
| 单次发药数       | `dispense_per_quantity`       | `Integer`  | 否   | 单次发药数                                                                                 |
| 用药天数/配药供应天数 | `dispense_per_duration`       | `Integer`  | 否   | 门诊临时典型配合用药天数等（BR-MRQ-010）                                                             |
| 产品批号        | `lot_number`                  | `String`   | 否   | 开立/发放环节可写                                                                             |
| 追溯码         | `trace_no`                    | `String`   | 否   | 追溯码                                                                                   |
| 皮试标志        | `skin_test_flag`              | `Boolean`  | 否   | `false` 否 / `true` 是（以落库与交互契约为准）                                                           |
| 输液标志        | `infusion_flag`               | `Boolean`  | 否   | `false` 否 / `true` 是（以落库与交互契约为准）                                                           |
| 滴速编码        | `speed_code`                  | `String`   | 否   | 输液滴速编码                                                                                |
| 费用性质标识      | `contract_id`                 | `Long`     | 否   | 引用费用性质（Contract）主键，与 `DM-FIN-001` 的费用性质标识（ContractId）一致；持久化列名与类型若仍为历史形态，以迁移脚本与本稿 **§3** 为准 |
| 就诊诊断绑定有序 ids 串 | `encounter_diagnosis_ids` | `String` | 否 | 有序逗号分隔的 `adm_encounter_diagnosis.id`；**首位必须是主诊断**；同族（普通/慢病）不可混 |

| 退药关联标识      | `refund_medicine_id`          | `Long`     | 否   | 退药负向单指向原单                                                                             |
| 处方类别编码      | `rx_type_code`                | `String`   | 否   | 处方类别                                                                                  |
| 医保类别编码      | `yb_class_enum`               | `String`  | 否   | 类别医保编码                                                                                |
| 用药说明        | `dosage_instruction`          | `String`   | 否   | 用药说明                                                                                  |
| 请求基于表名      | `based_on_table`              | `String`   | 否   | 派生/绑定来源表名；**住院长嘱改量**时为 `med_medication_request`（**AS-IP-DOR-003 BR-IP-ORD-032**）；亦用于皮试 companion、划价父嘱、门诊半退克隆等，语义按场景区分 |
| 请求基于标识      | `based_on_id`                 | `Long`     | 否   | 派生/绑定来源主键；改量链指向被停/被替换的源药品申请 id |
| 请求内容        | `content_json`                | `String`   | 否   | 结构化扩展（中药多味配伍、煎法等 JSON，以实现与产品契约为准）                                                     |
| 中药付数        | `chinese_herbs_dose_quantity` | `Decimal`  | 否   | 方剂付数                                                                                  |
| 代煎标识        | `suffering_flag`              | `Boolean`  | 否   | `false` 否 / `true` 是                                                                  |
| 中医/中药标识     | `tcm_flag`                    | `Boolean`  | 否   | `true`=中药，`false`=非中药（以落库与交互契约为准）                                                          |
| 排序号         | `sort_number`                 | `Integer`  | 否   | 展示排序                                                                                  |
| 校对人标识       | `performer_check_id`          | `Long`     | 否   | 校对人                                                                                   |
| 校对时间        | `check_time`                  | `DateTime` | 否   | 校对时间                                                                                  |
| 生成来源        | `generate_source_enum`        | `String`  | 是   | `GenerateSource`（§4.5，存枚举 code）                                                                |
| 备注          | `remark`                      | `String`   | 否   | 备注                                                                                    |


> 通用审计字段继承自 `HisBaseEntity`（如 `create_by`、`create_time`、`update_by`、`update_time`、`tenant_id`、`is_deleted`），不在此重复展开。

### 3.2 子实体

本聚合为**单表聚合**，不设聚合内子实体。中药多味配伍等结构化内容由 `content_json` 与主表中药相关字段承载，生命周期与主档一致。

### 3.3 治疗类型与用量（西药/中成药）

> **适用范围**：本节及 BR-MRQ-009 / BR-MRQ-010 中关于长期/临时/**出院带药**、单次用量/总量、首日量、门诊/住院临时的约定，均针对 **西药/中成药** 开立路径（`createMedicationOrderDraft` / `changeMedicationOrderDraft`）。**中药**开立与修改走专用动作（BR-MRQ-011），帖数、味数、煎法等**不在本节套用**。

创建、修改西药/中成药医嘱时，接口层常**复用同一套字段名**（如 `quantity`、`unit_code`），须以 `therapy_enum` 解读业务语义。本节分**长期**、**临时**与**出院带药**；**首日量**只在长期内分述。

**长期医嘱**

- **单次用量与单位**：表达**每次给药/每次执行**的用量与单位，**不是**疗程总量。与临时共用字段名时，`quantity` / `unit_code` 语义为单次用量及单位（**拆零/最小单位**）；`dose` / `dose_unit_code` 承载**计量**单次剂量。开立 UI 按计量录入后换算出拆零写入 `quantity`（与实现契约一致）。
- **不得**将「单次用量」与「首日量」混淆；**不得**用临时医嘱的「总量」理解长期下的同名取值。
- **首日量**：字段 `first_dose`、`first_dose_unit_code`。仅开立**长期**医嘱时启用；**临时 / 出院带药（含门诊临时）不适用**首日量语义。首日量与「长期单次用量」不是同一概念，但**落库单位口径相同**（最小/拆零单位）；开立界面可按计量单位展示录入，保存时换算。
- **分次用量不一致（住院长期西药/中成药）**：同一频次下各执行点计量/发药量可不同。字段 `dose_schedule`（计量串）与 `quantity_schedule`（拆零串）**成对**；**空=无痛回落**原 `dose`/`quantity` 路径，**既有逻辑零改动**；有值时按槽序号取第 i 项（**BR-MRQ-035**）。单位仍用 `dose_unit_code` / `unit_code`（各次相同）。**与首日量正交**：可同时有值；开嘱日**首次执行槽**若填了 `first_dose`，该槽换量**优先首日量**（最高优先），其余槽及后续日按 schedule（有）或 `quantity`（无）。
- **住院入口·长期·保存态协作（编排）**：保存草稿时以持久化主表为主（含按需首日量、按需分次 schedule）；**不**挂 `ChargeItem`、**不**建 `MedicationDispense`（与 BR-IP-ORD-005、BR-MRQ-028 一致）。

**临时医嘱**

临时医嘱统一表达**总量与总量单位**；再按就诊入口分为**门诊临时**与**住院临时**。

- **门诊临时**：`quantity` / `unit_code` 为**本疗程/本次可发总量**及单位；门诊开立药品医嘱在业务上**等价临时**；典型配合 **用药天数**（`dispense_per_duration` 等）；不涉及首日量。
- **住院临时**：总量语义同上；**不按**门诊临时方式配合用药天数；**频次须为「立即」**（`rate_code` 或产品字典，以落库与交互契约为准）。**住院长/临/出院带药**医生站暂存/签发**均仅 Request**（**不**建 Charge/Dispense，见 **BR-IP-ORD-006**）；门诊临时仍可挂费用草稿（BR-MRQ-028）。

**出院带药（`DISCHARGE_MEDICATION`）**

- **仅住院**药品路径使用（**AS-IP-DOR-003 · BR-IP-ORD-029**）；门诊禁开。
- `quantity` / `unit_code` 语义**同临时（总量）**；**不提供**停嘱/取消停嘱；**无**处方有效期。
- 开立侧生命周期与住院临时同构（仅 Request）；执行后发放差异（不写 `summary_no`、仅逐行发药）由护士/药房应用编排承接，本聚合不另增写方法。

### 3.4 中药与 `content_json`

本聚合**不设独立领域子实体**：中药多味配伍、味序、特殊煎法等由主表字段 `tcm_flag`、`chinese_herbs_dose_quantity`、`suffering_flag` 与 `content_json` 结构化承载，与西药/中成药共用签发、签退、校对、退回、移除草稿等生命周期。移除待发送草稿时须**同事务**清理 `content_json` 及关联协作对象（**临时**路径下未计费 **ChargeItem** 草稿；**已签发**路径下由签退/释放发放等编排清理），见 BR-MRQ-011。

---

## 4. 值对象（含枚举）

### 4.1 申请状态（RequestStatus）

与 **§2.3** 同源：落库 **`status_enum`** = **`RequestStatus#getCode()`**；**`sortOrder`** 仅文档对照。

| **落库 code（String）** | sortOrder（对照） | 中文 display | 说明 |
| --- | --- | --- | --- |
| `DRAFT` | 1 | 待签发 | 草稿 |
| `ACTIVE` | 2 | 已发送 | 已签发 |
| `VERIFIED` | 3 | 已校对 | 住院核对路径常见收口 |
| `HOLD` | 4 | 暂挂 | 暂停 |
| `CANCELLED` | 5 | 已取消 | 取消 |
| `STOPPED` | 6 | 已停嘱 | 停嘱 |
| `COMPLETED` | 7 | 已完成 | 门诊收费完成等路径 |

> FHIR `status`/`intent` 交换可使用等价英文字符串域；**禁止**将 sortOrder 数字写入 `status_enum`。

### 4.2 治疗类型（TherapyType）

> **实现**：`com.zxplat.domain.share.enums.TherapyType`（落库 `therapy_enum` 存 **`getCode()`**：`LONG_TERM` / `TEMPORARY` / **`DISCHARGE_MEDICATION`**）。与 **`DM-WKF-004` §4.5**、**`wkf_service_request.therapy_enum`** 共用同一枚举。

| value（排序号） | 枚举值 | code（落库） | 说明                                      |
| --- | --- | --- | --------------------------------------- |
| 1   | 长期  | `LONG_TERM` | `quantity`/`unit_code` 为**单次用量**语义；可持续执行至停嘱；执行侧按次生成多条发放（**不等同**临时医嘱「拆分行合计」口径） |
| 2   | 临时  | `TEMPORARY` | `quantity`/`unit_code` 为**总量**语义；同一请求下拆分的多条发放数量之和须等于开立总量（药房保存配药 **BR-IPP-006** / **BR-OPP-006**） |
| 3   | 出院带药 | `DISCHARGE_MEDICATION` | `quantity`/`unit_code` 语义**同临时（总量）**；**仅住院**药品；**不提供**停嘱；开立同住院临时（**BR-IP-ORD-029**） |


### 4.3 请求意图（IntentType）


| 枚举值  | 英文               | 说明            |
| ---- | ---------------- | ------------- |
| 正式医嘱 | `order`          | 正式的药品医嘱       |
| 原始医嘱 | `original-order` | 原始开立医嘱（未经派生）  |
| 派生医嘱 | `reflex-order`   | 由系统或规则自动派生的医嘱 |
| 填充请求 | `filler-order`   | 药房发起的配药请求     |
| 实例医嘱 | `instance-order` | 具体执行实例        |


### 4.4 优先级（PriorityLevel）


| 枚举值 | 英文           | 说明    |
| --- | ------------ | ----- |
| 不紧急 | `not-urgent` | 常规    |
| 普通  | `ordinary`   | 普通优先级 |
| 优先  | `priority`   | 加急    |
| 紧急  | `emergency`  | 紧急处理  |
| 危   | `stat`       | 立即执行  |


### 4.5 生成来源（GenerateSource）

实现：**`com.zxplat.domain.share.enums.GenerateSource`**（`zxplat-domain`）。与诊疗/耗材/收费明细共用下表 **code**。

| 枚举常量 | code（落库） | 说明 |
| --- | --- | --- |
| `DOCTOR_PRESCRIPTION` | `doctor-prescription` | 医生在医生站开立 |
| `NURSE_PRICING` | `nurse-pricing` | 护士站划价产生 |
| `MEDICAL_TECH_PRICING` | `medical-tech-pricing` | 医技工作站耗材划价产生 |
| `REGISTRATION_REGISTER` | `registration` | 挂号时自动产生 |
| `ORDER_EXECUTE` | `order-execute` | 医嘱执行产生 |
| `AUTO_ROLL_FEES` | `auto-roll-fees` | 住院自动滚费产生 |

> **存储口径**：`generate_source_enum` 存 **`getCode()`** 英文字符串；**禁止**数字串作身份。


### 4.6 处方类别（PrescriptionCategory）


| 枚举值    | 英文           | 说明          |
| ------ | ------------ | ----------- |
| 门诊处方   | `outpatient` | 门诊场景        |
| 住院处方   | `inpatient`  | 住院场景        |
| 急诊处方   | `emergency`  | 急诊场景，用量限制更严 |
| 出院带药   | `discharge`  | 出院时开立       |
| 中药饮片处方 | `herbal`     | 中药饮片处方      |


### 4.7 用量语义快照（DosageSemantics）

封装 `治疗类型 + 数量 + 数量单位 + 首日用量 + 首日用量单位` 的联合语义，避免散落的枚举裸值判断。


| 属性     | 类型                | 说明                |
| ------ | ----------------- | ----------------- |
| 治疗类型   | `TherapyType` | 长期 / 临时           |
| 数量     | `Decimal`         | 长期 = 单次用量，临时 = 总量 |
| 数量单位   | `String`          | 配对单位              |
| 首日用量   | `Decimal`         | 仅长期适用（nullable）   |
| 首日用量单位 | `String`          | 仅长期适用（nullable）   |


> **不变式**：临时医嘱时首日用量须为空；长期医嘱时数量语义为单次用量，不得与总量混淆。

---

## 5. 领域事件

### 5.1 事件定义


| 事件名称（中文）    | 英文名称                                     | 触发时机         | 携带数据 / 最小载荷        | 消费方                |
| ----------- | ---------------------------------------- | ------------ | ------------------- | ------------------ |
| 西药医嘱草稿已创建   | `MedicationOrderDraftCreatedEvent`       | 创建西药/中成药医嘱草稿 | 药品申请标识、就诊标识、患者标识   | 计费预览、合理用药（若订阅）    |
| 医嘱草稿已修改     | `MedicationOrderDraftEditedEvent`        | 修改草稿成功       | 药品申请标识、变更字段摘要      | 同上                 |
| 医嘱草稿已移除     | `MedicationOrderDraftRemovedEvent`       | 移除草稿成功（含中药）  | 药品申请标识、就诊标识        | 计费回滚、QueryService           |
| 药品医嘱已签发     | `MedicationOrderSignedEvent`             | 签发成功（中西药共用）  | 药品申请标识、签发编码、处方号、`status_enum`（**ACTIVE**） | 护士校对、发药域、ChargeItem 锚点、`MedicationRequestQueryService` 列表 |
| 药品医嘱已签退     | `MedicationOrderSignOffEvent`            | 签退成功         | 药品申请标识              | 护士站、QueryService            |
| 药品医嘱已校对     | `MedicationOrderVerifiedEvent`           | 病区护士校对成功         | 药品申请标识、校对人标识、`status_enum`（**`VERIFIED`**）       | 药房发药、执行记录、`MedicationRequestQueryService` 列表          |
| 门诊收费后药品医嘱已完成 | `MedicationOrderCompletedAfterOutpatientChargeEvent` | 门诊正式结算收口（**IF-OPC-07**） | 药品申请标识、收费操作人、完成时间 | 医嘱全景/处方视图QueryService、药房队列 |
| 药品医嘱已退回     | `MedicationOrderRejectedEvent`           | 退回成功         | 药品申请标识、`status_enum`（**`DRAFT`**）、退回原因              | 医生站、`MedicationRequestQueryService`                |
| 长期医嘱已停嘱     | `LongTermMedicationOrderStoppedEvent`    | 长期医嘱停嘱成功     | 药品申请标识、停嘱时间、`statusEnum`（**STOPPED**） | 发药停止、计费收口、`med_medication_request` 主表读侧 |
| 长期医嘱已取消停嘱   | `LongTermMedicationOrderStopCancelledEvent` | 长期医嘱取消停嘱成功   | 药品申请标识、操作人标识、恢复后 `statusEnum`（**VERIFIED** 或 **ACTIVE**） | 护士执行列表、出院门禁读侧 |
| 医嘱分组已调整     | `MedicationOrderGroupAdjustedEvent`      | 分组调整成功       | 药品申请标识、分组号         | 输液/成组执行视图           |
| 药品医嘱已取消     | `MedicationOrderCancelledEvent`          | 取消成功         | 药品申请标识、取消原因        | 发药作废、计费冲正          |
| 中药饮片医嘱草稿已创建 | `HerbalMedicationOrderDraftCreatedEvent` | 创建中药饮片医嘱草稿   | 药品申请标识、就诊标识        | 计费预览、QueryService           |
| 中药饮片医嘱草稿已修改 | `HerbalMedicationOrderDraftEditedEvent`  | 修改中药草稿成功     | 药品申请标识、变更字段摘要      | 同上                 |
| 退药负向单已生成    | `MedicationOrderRefundRequestedEvent`    | 退药负向单生成      | 药品申请标识、退药关联标识      | 药房退药、退费            |
| 医嘱已重开新单     | `MedicationOrderReopenedEvent`           | 重开新单成功       | 药品申请标识              | 发药/计费新锚点           |


事件载荷以 `zxplat-domain` / 应用层 本稿 **§6.1** 为准。

### 5.2 事件触发规则


| 当前状态       | 操作           | 目标状态     | 发布事件                                     |
| ---------- | ------------ | -------- | ---------------------------------------- |
| —          | 创建草稿（西药/中成药） | 草稿       | `MedicationOrderDraftCreatedEvent`       |
| —          | 创建草稿（中药）     | 草稿       | `HerbalMedicationOrderDraftCreatedEvent` |
| 草稿         | 修改草稿         | 草稿       | `MedicationOrderDraftEditedEvent`        |
| 草稿         | 移除草稿         | （已删除）    | `MedicationOrderDraftRemovedEvent`       |
| 草稿         | 签发           | 已发送      | `MedicationOrderSignedEvent`             |
| 已发送        | 签退           | 草稿       | `MedicationOrderSignOffEvent`            |
| 已发送        | 病区护士校对           | 已校对（**VERIFIED**）      | `MedicationOrderVerifiedEvent`           |
| 已发送        | 门诊正式结算收口   | 已完成（**COMPLETED**）      | `MedicationOrderCompletedAfterOutpatientChargeEvent`（由 **`OutpatientChargeAdviceStateSyncService`** 编排，**AS-OP-FIN-002** **BR-OPC-015**） |
| 已发送        | 退回（护士校对 Tab）           | 草稿       | `MedicationOrderRejectedEvent`           |
| 已发送/已校对    | 停嘱（仅长期）      | 已停止      | `LongTermMedicationOrderStoppedEvent`    |
| 已停止（仅长期）   | 取消停嘱         | 已校对/已发送   | `LongTermMedicationOrderStopCancelledEvent` |
| 已发送/已校对/草稿 | 取消           | 已取消      | `MedicationOrderCancelledEvent`          |
| —          | 创建退药单        | 已取消（负向单） | `MedicationOrderRefundRequestedEvent`    |
| —          | 重开           | 同原单或显式覆盖 | `MedicationOrderReopenedEvent`           |


---

## 6. 领域服务

### 6.1 写侧领域服务（MedicationRequestDomainService）

> 写侧由应用服务用例编排事务后调用领域服务完成。命令类型、幂等细节、异常字面量见本稿 **§6.1** 及配套细化说明。

#### 6.1.1 创建医嘱草稿（西药/中成药）（`createMedicationOrderDraft`）

- **业务意义**：在就诊下创建一条西药/中成药药品申请，进入待发送（草稿）态，作为后续签发与计费的起点。
- **边界说明**：仅承载本聚合主档初始化与库存校验编排入口；费用草稿、发放占用等他域协作由应用订阅或同事务编排处理，不在此方法内展开实现细节。
- **入参**：药品申请内容命令、治疗类型、就诊标识（及命令对象所要求的其他字段子集，以本稿约定为准）。
- **出参**：药品申请标识（`MedicationRequestId`）。
- **前置条件**：满足就诊与患者引用完整性；`therapy_enum` 合法；长期/临时与 `quantity`/`unit_code` 语义一致（BR-MRQ-009、BR-MRQ-010）；首日量仅允许在长期路径出现（BR-MRQ-010）。
- **状态变化**：初始化为 `draft`（BR-MRQ-004）。
- **聚合内影响**：生成 `bus_no`；写入生成来源与处方类别（BR-MRQ-005）；**写入 `req_opened_time` 为首次暂存时刻**（见 §3）；执行非删除路径下的库存校验（BR-MRQ-001）。
- **事件输出**：`MedicationOrderDraftCreatedEvent`。
- **失败语义**：库存不足、必填缺失、治疗类型与用量组合非法时拒绝落库。

#### 6.1.2 修改医嘱草稿（西药/中成药）（`changeMedicationOrderDraft`）

- **业务意义**：在签发前修订西药/中成药医嘱字段，保持可签发前的业务事实一致。
- **边界说明**：仅更新本聚合待发送态记录；不替代库存域、费用域的协作编排。
- **入参**：药品申请标识、变更内容命令。
- **出参**：—。
- **前置条件**：当前状态须为 `draft`；用量语义与创建规则一致（BR-MRQ-009、BR-MRQ-010）。
- **状态变化**：`N/A`（维持 `draft`）。
- **聚合内影响**：按命令更新主表业务字段。
- **事件输出**：`MedicationOrderDraftEditedEvent`。
- **失败语义**：非草稿态、已收费不可删约束间接冲突、校验失败时拒绝。

#### 6.1.3 移除医嘱草稿（`removeMedicationOrderDraft`）

- **业务意义**：撤销未签发的药品申请行；西药、中成药与中药共用本入口（BR-MRQ-011）。
- **边界说明**：可触发协作对象清理，但退费与库存回补等他域事实由订阅方处理；本方法收口「待发送态删除」语义。
- **入参**：药品申请标识。
- **出参**：—。
- **前置条件**：须为 `draft`；关联费用项不得存在已收费态（BR-MRQ-002）。
- **状态变化**：聚合内移除或逻辑删除（以实现与持久化策略为准）。
- **聚合内影响**：删除主档；同事务清理 `content_json` 及发放占用、费用草稿等协作对象（BR-MRQ-011）。
- **事件输出**：`MedicationOrderDraftRemovedEvent`。
- **失败语义**：已收费、非草稿态时拒绝。

#### 6.1.4 签发医嘱（`signMedicationOrder`）

- **业务意义**：将待发送医嘱确认为已发送，生成处方维度事实，进入校对/执行链路；中西药共用（BR-MRQ-011）。
- **边界说明**：不写费用明细与发放明细本体；下游由事件驱动。
- **入参**：药品申请标识、签发编码（及命令中要求的处方号等，以应用编排为准）。
- **出参**：处方号（或等价回写结果，以本稿约定为准）。
- **前置条件**：当前状态须为 `draft`（BR-MRQ-004）。
- **状态变化**：`draft` → `active`（BR-MRQ-004）。
- **聚合内影响**：写入处方号、签发编码等签发要素（BR-MRQ-005）；**写入 `req_authored_time` 为签发时刻**（不覆盖 `req_opened_time`；与诊疗/耗材签发口径一致）。
- **事件输出**：`MedicationOrderSignedEvent`。
- **失败语义**：状态不允许、签发要素生成失败时拒绝。

#### 6.1.5 签退医嘱（`signOffMedicationOrder`）

- **业务意义**：将已发送医嘱撤回为待发送，供医生修订后再次签发。
- **边界说明**：费用草稿与发放计划回滚由他域订阅处理；本方法收口状态回退与签退约束。
- **入参**：药品申请标识。
- **出参**：—。
- **前置条件**：当前状态须为 `active`；关联费用项不得已收费（BR-MRQ-003）；幂等：已为 `draft` 可视为成功。
- **状态变化**：`active` → `draft`。
- **聚合内影响**：回写状态及相关签发展示字段策略以落库与交互契约为准。
- **事件输出**：`MedicationOrderSignOffEvent`。
- **失败语义**：已收费、状态非法时拒绝。

#### 6.1.6 长期医嘱停嘱（`stopLongTermMedicationOrder`）

- **业务意义**：终止长期药品医嘱的继续执行，写入用药结束时间锚点与停嘱人，并进入护士停嘱待校。
- **边界说明**：**仅**更新本聚合选中行；**不**级联 `based_on` 子嘱；不替代执行域登记。
- **入参**：药品申请标识、停嘱时间、停嘱人标识（`StopLongTermMedicationOrderCommand`：`id`、`stopTime`、`stopperId`/`operatorId`）。
- **出参**：—。
- **前置条件**：`therapy_enum` 须为长期；当前须为 **`VERIFIED`**（**禁止 ACTIVE 停嘱**，**BR-MRQ-006**）；实体 **`ensureLongTermForStop`**。
- **状态变化**：进入 `stopped`。
- **聚合内影响**：写入 `stop_time`、`stopper_id`；清空 `stop_performer_check_id`/`stop_check_time`；**不**写 `effective_dose_end`。
- **事件输出**：`LongTermMedicationOrderStoppedEvent`。
- **失败语义**：非长期、非 VERIFIED、重复停嘱等拒绝。

#### 6.1.6a 取消长期医嘱停嘱（`cancelStopLongTermMedicationOrder`）

- **业务意义**：撤销长期医嘱停嘱，恢复继续执行链路。
- **边界说明**：**仅**恢复本聚合行；与 **Procedure** 解耦；已停嘱校对须护士先取消停嘱校对。
- **入参**：药品申请标识、操作人标识（`CancelStopLongTermMedicationOrderCommand`：`id`、`operatorId`）。
- **出参**：—。
- **前置条件**：长期 + `stopped` + **`stop_performer_check_id` 为空**（**BR-MRQ-006a**）；实体 **`ensureLongTermStoppedForCancelStop`**。
- **状态变化**：`fromStopped`：`performer_check_id` 非空 → **`verified`**，否则 → **`active`**。
- **聚合内影响**：清空 `stop_time`、`stopper_id`、`stop_performer_check_id`、`stop_check_time`。
- **事件输出**：`LongTermMedicationOrderStopCancelledEvent`。
- **失败语义**：非长期、非已停止、已停嘱校对 → 拒绝。
- **实现状态**：**已落地**。

#### 6.1.6c 改药品申请业务时间（`changeMedicationRequestBusinessTime`）

- **业务意义**：住院医生站调整开立时间或停嘱时间；**不**改签发时间与开立人/停嘱人/停嘱校对痕迹。
- **边界说明**：**AS-IP-DOR-003 IF-IP-DOR-26**；改停嘱**保留** `stop_performer_check_id`/`stop_check_time`（**BR-MRQ-006b**）。
- **入参**：`ChangeMedicationRequestBusinessTimeCommand`：`id`、`timeKind`（`OPENED`/`STOP`）、`newTime`、`operatorId`。
- **出参**：—。
- **前置条件**：`STOP` 须长期且当前 `stopped`。
- **状态变化**：无（仅时间列）。
- **聚合内影响**：`OPENED` → 写 `req_opened_time`；`STOP` → 写 `stop_time`（不改停嘱人/停嘱校对字段）。
- **事件输出**：可选轻量审计事件。
- **失败语义**：种类非法、非已停嘱改停嘱、试图改签发 → 拒绝。
- **实现状态**：**已落地**。

#### 6.1.6d 停嘱校对（`verifyStopMedicationOrder`）

- **业务意义**：护士确认长期停嘱；只记停嘱校对痕迹，**不**改变 `STOPPED`。
- **边界说明**：由 **AS-IP-NUR-003 IF-IP-AUD-02** 停嘱校对分支编排；临时无此命令。
- **入参**：`VerifyStopMedicationOrderCommand`：`id`、`stopPerformerCheckId`、`stopCheckTime`。
- **出参**：—。
- **前置条件**：长期 + `STOPPED` + `stop_performer_check_id` 为空（**`ensureStopVerifiable`** / **BR-MRQ-006d**）。
- **状态变化**：无（仍 `STOPPED`）。
- **聚合内影响**：写 `stop_performer_check_id`、`stop_check_time`（`markStopChecked`）。
- **失败语义**：非长期、非 STOPPED、已停嘱校对、校对人为空 → 拒绝。
- **实现状态**：**已落地**。

#### 6.1.6e 取消停嘱校对（`cancelVerifyStopMedicationOrder`）

- **业务意义**：清空停嘱校对痕迹，解锁医生取消停嘱；状态仍 `STOPPED`。
- **边界说明**：**AS-IP-NUR-003 IF-IP-AUD-03** 取消停嘱校对分支；**本期暂不校验操作人**须为原停嘱校对人。
- **入参**：`CancelVerifyStopMedicationOrderCommand`：`id`（及可选 `operatorId` 审计）。
- **出参**：—。
- **前置条件**：`STOPPED` 且 `stop_performer_check_id` 非空（**`ensureStopVerifyCancellable`** / **BR-MRQ-006e**）。
- **状态变化**：无（仍 `STOPPED`）。
- **聚合内影响**：清 `stop_performer_check_id`、`stop_check_time`（`clearStopChecked`）。
- **失败语义**：非 STOPPED、尚未停嘱校对 → 拒绝。
- **实现状态**：**已落地**。

#### 6.1.6b 门诊收费后完成药品申请（`completeMedicationRequestAfterOutpatientCharge`）

- **业务意义**：门诊正式结算后，将仍为 **已发送** 的药品申请**幂等**收口为 **已校对（completed）**，与 **BR-OPC-015** 一致；**不**走住院护士核对（**VERIFIED**）语义。
- **边界说明**：由 **`OutpatientChargeAdviceStateSyncService`** 在收费项 **SETTLED** 后**同步**编排；发布 **`MedicationOrderCompletedAfterOutpatientChargeEvent`**（与护士 **`verifyMedicationOrder`** 的 **`MedicationOrderVerifiedEvent`** 区分）。
- **入参**：药品申请标识、收费操作人标识（可写入校对追溯字段，以实现为准）。
- **出参**：—。
- **前置条件**：当前须为允许收口的发送态；已为 **completed** 时幂等。
- **状态变化**：`active` → `completed`。
- **聚合内影响**：与完成/校对路径一致的状态与追溯字段。
- **事件输出**：`MedicationOrderCompletedAfterOutpatientChargeEvent`。
- **失败语义**：状态非法时拒绝。

#### 6.1.7 校对医嘱（`verifyMedicationOrder`）

- **业务意义**：病区护士校对通过，使医嘱进入**已校对**态（**`VERIFIED`**，与门诊收费 **`COMPLETED`** 分离）。
- **边界说明**：须写入校对人/时间（**BR-MRQ-007**）；不替代执行记录落库。
- **入参**：药品申请标识、校对人标识、校对时间。
- **出参**：—。
- **前置条件**：当前状态须为 `ACTIVE`。
- **状态变化**：`ACTIVE` → **`VERIFIED`**（实体方法 **`activeToCompleted`** 历史命名，语义为已校对）。
- **聚合内影响**：写入 `performer_check_id`、`check_time`（**BR-MRQ-007**）。
- **事件输出**：`MedicationOrderVerifiedEvent`（**`status_enum=VERIFIED`**）。
- **失败语义**：状态非法时拒绝。

#### 6.1.8 退回医嘱（`rejectMedicationOrder`）

- **业务意义**：校对不通过或医生对已校对未执行行签退，将医嘱退回医生修订，并保留校对/退回追溯信息。
- **边界说明**：提醒与待办由他域处理；住院护士站 **IF-IP-AUD-03** 对 **ACTIVE/VERIFIED** 发起退回（**VERIFIED** 须 **BR-IP-AUD-013** 门控）；住院医生站 **IF-IP-TCM-38** 在应用层完成执行/发药/费用门控（**BR-IP-TCM-009**）后，可对 **VERIFIED** 行调用本方法签退。
- **入参**：药品申请标识、校对人/操作人标识、校对/退回时间、退回原因。
- **出参**：—。
- **前置条件**：当前状态为 **ACTIVE** 或 **VERIFIED**（实体 **`ensureRejectable`**）。
- **状态变化**：`ACTIVE` → `DRAFT`；`VERIFIED` → `DRAFT`。
- **聚合内影响**：回退状态；**须**写入 `performer_check_id`、`check_time`（**BR-MRQ-007**）。
- **事件输出**：`MedicationOrderRejectedEvent`（**`status_enum=DRAFT`**）。
- **失败语义**：状态不允许时拒绝。

#### 6.1.9 调整药品成组（`adjustMedicationOrderGroup`）

- **业务意义**：维护成组输液等场景下的编组号，供执行与分方一致使用。
- **边界说明**：不推进医嘱主状态机；仅更新编组事实。
- **入参**：药品申请标识列表、分组号（可为空表示拆组，BR-MRQ-008）。
- **出参**：—。
- **前置条件**：列表内记录须允许编组调整（以落库与交互契约为准）。
- **状态变化**：`N/A`。
- **聚合内影响**：批量更新 `group_id`（BR-MRQ-008）。
- **事件输出**：`MedicationOrderGroupAdjustedEvent`。
- **失败语义**：标识不存在或并发冲突时拒绝。

#### 6.1.10 取消医嘱（`cancelMedicationOrder`）

- **业务意义**：对已发送/已校对等路径上的医嘱做保留记录的取消，区别于草稿物理删除（BR-MRQ-012）。
- **边界说明**：退费与库存冲销由应用编排；本方法不生成负向退药单（另有专门动作）。
- **入参**：药品申请标识（及可选取消原因等）。
- **出参**：—。
- **前置条件**：当前状态允许进入取消态（以实现白名单为准）。
- **状态变化**：进入 `cancelled`（BR-MRQ-012）。
- **聚合内影响**：更新状态；可按需要写入校对追溯字段（BR-MRQ-012）。
- **事件输出**：`MedicationOrderCancelledEvent`。
- **失败语义**：终态或业务不允许取消时拒绝。

#### 6.1.10a 废弃未签发草稿（`cancelUnsignedMedicationOrderDraft`）

- **业务意义**：调价配套（**AS-FIN-PRC-001 · BR-PRC-SUB-003a**）：仅废弃仍为 `DRAFT` 且 `req_authored_time` 空的行；**禁止**误伤已签发医嘱。
- **边界说明**：与 §6.1.10 通用取消分流；已签发或非草稿时**静默跳过**（返回 false），不抛错。
- **入参**：`CancelMedicationOrderCommand`。
- **出参**：是否实际废弃。
- **前置条件**：`status=DRAFT` 且从未签发。
- **状态变化**：`DRAFT -> CANCELLED`（不软删）。
- **实现锚点**：`MedicationRequestDomainService#cancelUnsignedMedicationOrderDraft`；实体 `ensureUnsignedDraftCancellable`。

#### 6.1.11 创建退药请求（负向单）（`createRefundMedicationOrder`）

- **业务意义**：基于原单生成退药/退费用的负向药品申请行，并建立 `refund_medicine_id` 关联（**BR-MRQ-013**）。
- **边界说明**：退费金额结算、库存回补由他域编排；本方法收口负向请求事实。
- **入参**：原药品申请标识。
- **出参**：新药品申请标识，或已存在的未完结负向单标识（幂等）。
- **前置条件**：原单为正向单（`refund_medicine_id` 为空）；须幂等防重复新建未完结负向单（**BR-MRQ-013**）。
- **状态变化**：新单 `initAsRefundNegativeOrder()` 进入 `CANCELLED`（待退口径，与耗材负向单一致；药房退药闭环后由 **`completeMedicationRequestAfterRefund`** 推进 `COMPLETED`）；幂等命中时不改已有行。
- **聚合内影响**：新建行；`refund_medicine_id` 指向原单；**全量拷贝**原单临床/展示字段；**新** `bus_no`；`quantity` 取原数量**相反数**；处方号 **`markAsRefundFromPrescription`** → **`T` + 原 `prescription_no`**；**已有未完结负向单（未软删且非 `COMPLETED`）时返回已有主键，不抛错**——支撑门诊同一药嘱多收费行共锚（**BR-CHG-001**）及本批内去重；**仅**当原单本身已是负向单（`refund_medicine_id` 非空）时拒绝（文案「已申请退药，请勿重复申请」）。查询范围**仅** `refund_medicine_id = 本原单 id`，**不**按患者/就诊扫他单。
- **事件输出**：`MedicationOrderRefundRequestedEvent`（幂等命中已有行时不重复发布）。
- **失败语义**：原单已是负向单时拒绝；原单不存在时拒绝。

#### 6.1.11a 退药闭环完成（`completeMedicationRequestAfterRefund`）

- **业务意义**：门诊药房退药执行后，将负向药品申请幂等收口为已完成，与 **AS-OP-PHA-002** 退药闭环对齐。
- **边界说明**：由药房退药应用编排调用；发布 **`MedicationOrderCompletedAfterOutpatientChargeEvent`**（与门诊收费完成事件类同，载荷区分操作场景）。
- **入参**：负向药品申请主键、操作人标识。
- **出参**：—。
- **前置条件**：负向申请可加载；`ensureCompletableAfterPharmacyRefund` 断言通过；已 completed 时幂等。
- **状态变化**：进入 `completed`。
- **聚合内影响**：`toCompletedAfterPharmacyRefund` 写状态与追溯字段。
- **事件输出**：`MedicationOrderCompletedAfterOutpatientChargeEvent`。
- **失败语义**：状态非法时拒绝。

#### 6.1.12 重开医嘱（`reopenMedicationOrder`）

- **业务意义**：在全退等条件满足后，拷贝原单生成新的正向医嘱行，继续常规生命周期（BR-MRQ-014）。
- **边界说明**：全退判断与费用回滚由应用编排；本方法收口新单生成事实。
- **入参**：原药品申请标识。
- **出参**：新药品申请标识。
- **前置条件**：满足全退+重开业务规则（BR-MRQ-014）。
- **状态变化**：新单状态以实现或命令显式覆盖为准。
- **聚合内影响**：新 `bus_no`；处方号约定加 `C` 等可追溯标记（BR-MRQ-014）；其余字段全量拷贝。
- **事件输出**：`MedicationOrderReopenedEvent`。
- **失败语义**：前置业务条件不满足时拒绝。

#### 6.1.13 创建中药医嘱草稿（`createHerbalMedicationOrderDraft`）

- **业务意义**：创建中药口径的待发送医嘱，与西药 `createMedicationOrderDraft` 分轨（BR-MRQ-011）。
- **边界说明**：主表中药字段与 `content_json` 一并初始化；煎煮中心等由他域编排。
- **入参**（`CreateHerbalMedicationOrderDraftCommand`）：`operatorId`、`encounterId`、`patientId`、`medicationId`（必填）、`quantity`（必填，**请求数量 = 单剂用量 × 付数**）、`chineseHerbsDoseQuantity`（付数）、`tcmFlag`（默认 true）、`sufferingFlag`、`dose`/`doseUnitCode`、`unitCode`、`methodCode`、`rateCode`、`therapyEnum`（长临 code，住院方头传入；未传回落临时）、`dispensePerDuration`、`contentJson`（含 `prescriptionGroupKey` 等方头扩展，**不落独立库列**）、`dosageInstruction`、`remark`、`encounterDiagnosisIds`（有序 ids 串，主诊 id 第一）、`contractId`、`orgId`、`performLocation`、`recorderId`、`sortNumber`、`generateSourceEnum`（默认医生开立）、`rxTypeCode`、`prescriptionNo`（签发前预写/复用）。
- **出参**：药品申请标识。
- **前置条件**：不得走西药创建命令语义（BR-MRQ-011）；`medicationId`、`quantity` 非空。
- **状态变化**：初始化为 `draft`。
- **聚合内影响**：写入 `tcm_flag=true`、`chinese_herbs_dose_quantity`（付数）、`quantity`（请求数量）、代煎及 `content_json` 等（BR-MRQ-011）；**写入 `req_opened_time` 为首次暂存时刻**（签发时写 `req_authored_time`，见 §6.1.4）。
- **事件输出**：`HerbalMedicationOrderDraftCreatedEvent`。
- **失败语义**：校验失败、与西药路径混用时拒绝。
- **典型消费方**：**AS-OP-DOR-010 · IF-DOR-36** 中药处方暂存。

#### 6.1.14 修改中药医嘱草稿（`changeHerbalMedicationOrderDraft`）

- **业务意义**：修订待发送态中药医嘱，与西药 `changeMedicationOrderDraft` 分轨（BR-MRQ-011）。
- **边界说明**：仅草稿；不写签发事实；**null 字段表示不变更**。
- **入参**（`ChangeHerbalMedicationOrderDraftCommand`）：`operatorId`、`id`（必填）、`medicationId`、`quantity`、`chineseHerbsDoseQuantity`、`sufferingFlag`、`dose`/`doseUnitCode`、`unitCode`、`methodCode`、`rateCode`、`dispensePerDuration`、`contentJson`、`dosageInstruction`、`remark`、`encounterDiagnosisIds`（有序 ids 串，主诊 id 第一）、`contractId`、`orgId`、`performLocation`、`sortNumber`、`rxTypeCode`、`prescriptionNo`。
- **出参**：—。
- **前置条件**：`draft` 且 `tcm_flag=true`（BR-MRQ-011）。
- **状态变化**：`N/A`。
- **聚合内影响**：更新主表中药相关字段与 `content_json`（BR-MRQ-011）。
- **事件输出**：`HerbalMedicationOrderDraftEditedEvent`。
- **失败语义**：非草稿、非中药路径、命令语义与西药路径混用时拒绝。
- **典型消费方**：**AS-OP-DOR-010 · IF-DOR-36** 中药处方暂存（更新行）。

#### 6.1.15 处方分方（`splitPrescription`）

- **业务意义**：将同一就诊下**未收费**药品申请行划分为**处方单元**（同一 `prescription_no` = 同一张处方），回写 `prescription_no` 与 `rx_type_code`，供签发、药房展示与打印共用。**不改变**医嘱主状态机（不替代 `signMedicationOrder`）。划分规则以 **§9.4** 及 **BR-MRQ-015～017、BR-MRQ-024～027** 为准。
- **实现范围（当前）**：**仅门诊**医生站签发编排调用（`AS-OP-DOR-003` IF-DOR-15）；**住院**签发路径**不**调用本方法。
- **边界说明**：
  - **与 ServiceRequest 侧服务申请分方算法分轨**，禁止在同一无边界函数中混算。
  - **打印模板、专用笺颜色、字体/水印/二维码版式、预览界面、打印机队列**属应用展示与基础设施；本方法只落库 **`prescription_no` / `rx_type_code`** 等领域事实。
  - **首版不回写**分方序号（如 `1/N`）、分方原因码；按 `prescription_no` 聚合即可满足药房与打印分组。
  - **首版不发布**分方领域事件；**禁止**通过改写 `group_id` 表达分方（成组号仅用于输液编组，见 §9.3）。
- **入参**（`SplitPrescriptionCommand`）：
  - `encounterId`、`operatorId`
  - `lines`：应用层预装配的 `PrescriptionSplitLineContext` 快照列表（领域层**不**反查外域）
  - `amountLimitYuan`：单张处方金额上限（元）；由系统设置 **`outpatientPrescriptionAmountLimitYuan`** 注入（默认 **500**；配置 **99999** 可实质关闭金额拆方）
- **行快照字段（`PrescriptionSplitLineContext`）**：`requestId`、`medicationId`（**药品目录主键**，同药同用法重复判定依据；**≤5 上限按 req 行数计，相同目录主键不合并**）、`methodCode`/`rateCode`/`dosageInstruction`/`dose`/`doseUnitCode`（**用法签名**，同目录+同用法不得同方）、`medType`（`adm_encounter_diagnosis.med_type`，`YbMedType` code）、`pharmacologyCategoryCode`（药品性质，术语 `medicine_properties`）、`medicationCategoryCode`、`tcmFlag`、`groupId`、`pharmacyLocationId`（签发 FIFO **首段**发放库位）、`chargeTotalPrice`（本行关联 `ChargeItem.totalPrice` 合计，仅金额装箱用；分方不改收费状态）
- **出参**（`SplitPrescriptionResult`）：`prescriptionNoByRequestId`、`rxTypeCodeByRequestId`；以及 `totalCount`（参与行数）、`splitUnitCount`（处方单元数）
- **参与集合与触发**（**签发以后才有方**）：
  - 同一 `encounter_id`、生成来源为**医生开立**、且**不存在**锚定该请求的 **SETTLED** 收费行；
  - 纳入行 = **本批待签发药品申请**（此时仍为 DRAFT）∪ **本就诊已签发 ACTIVE 且未 SETTLED** 的药品行；**禁止**把未纳入本批发签的其它 DRAFT 写入 `prescription_no`；
  - **每次**门诊批次签发**含药品**时，对上述参与集**重新分方**（防止「开一个签一个」绕过；已 ACTIVE 未收费行的 `prescription_no` **允许改写**）；
  - 调用时机：**签发循环之前**（`signMedicationOrder` 之前），由 `OutpatientPrescriptionSplitAssembler` 传入本批药品主键并委托本方法。
- **算法要点**（领域 `com.zxplat.domain.medication.medicationrequest.prescriptionsplit.OutpatientPrescriptionSplitPlanner`）：
  1. 按 `group_id` 将行聚为**不可拆包**（同组须同处方，P2）；**组内每条 req 仍单独计入 ≤5 行上限**，相同目录主键不合并；
  2. 按 **`medType`（医保医疗类别）** + 分方轨道（饮片 / 毒性 / 麻·精一 / **西药** / **中成药**；精二随目录分类入西药或中成药普通轨）+ **`pharmacyLocationId`（FIFO 主库房，P1）** 分区——**不同 `medType` 不得同处方**；**西药与中成药不得同处方**（**BR-MRQ-032**）；
  3. 西药轨道与中成药轨道内分别做**多轮**贪心装箱：每轮从待装原子包队列中尽量填满一桶；合计**药品申请行数 ≤5**（BR-MRQ-024，**每条 `MedicationRequest` 计 1**）、**同药品目录主键 + 同用法签名不得同方**（BR-MRQ-031，冲突包当轮跳过、后续包仍可填入当前桶）、单张 **`chargeTotalPrice` 合计 ≤ amountLimitYuan**（BR-MRQ-027，门诊）；
  4. 每个处方单元：`BusinessSequenceService.getSeqByDay(AggregateTypeConstants.COMPOSITE_PRESCRIPTION, …)` 生成 **`prescription_no`（`CP` 前缀）**，并推导单元级 **`rx_type_code`**（355，同行同值）。
- **特殊药品（目录 `pharmacology_category_code`，术语 `medicine_properties`）**：麻醉 `2`、精一 `4`、毒性 `3` → **独占处方**；精二 `5` → 可与**同目录分类**之西药或中成药**同方**（仍受 ≤5 种约束，且不得跨西药/中成药混方）；**普通药 `1`（含空按普通）** → 再按 `medication_category_code` 分西药/中成药轨；**中药饮片**（`tcmFlag=true`）→ **独占处方**（BR-MRQ-025）。**禁止**用门诊拆分属性 `part_attribute_enum`（`OrderCalcRuleEnum` 拆零规则）参与分方（与性质码同形数字串会误伤麻精轨）。
- **行快照字段**：除既有 medType / **`pharmacologyCategoryCode`** / FIFO 库房 / 金额外，须装配 **`medicationCategoryCode`**（目录 `medication_category_code`），供普通药西药/中成药分轨。
- **`rx_type_code` 推导（单元级，后端自动，医师不选）**：单元含精二（药品性质 `5`）→ **`8`（优先于西药/中成药急诊、儿科抬升）**；单元含麻/精一 → `7`；饮片：急诊医疗类别 → `4`、儿科 → `6`、否则 → **`2`（中药饮片规范码；术语历史冗余码 `9` 同义，禁止新写入，见 BR-MRQ-034）**；**西药（无精二）**：急诊 → `3`、儿科 → `5`、否则 → `1`；**中成药（无精二）**：急诊 → `11`、儿科 → `12`、否则 → `10`；毒性单独成方当前实现落 `1`（普通门诊西药码，专用管理以独占处方保证）。
- **状态变化**：`N/A`（不替代 `signMedicationOrder` 的状态迁移）。
- **聚合内影响**：`medicationRequestRepository.saveAll` 回写 `prescription_no`、`rx_type_code`；**不改** `group_id` 与其它签发字段。
- **事件输出**：无（首版）。
- **失败语义**：待分方行缺失 → `MEDICATION_REQUEST_NOT_FOUND`；违反强制装箱规则（成组被拆散、饮片与西药/中成药同单元、**西药与中成药同单元**、**同目录+同用法重复同方**、单组品种/金额超限等）→ 业务异常拒绝。

#### 6.1.16 半退保留链正向克隆（`cloneMedicationRequestForPreservedChargeRegeneration`）

- **业务意义**：门诊半退「保留行」账单重生时，按**源药品申请**有效事实在本聚合内插入一条**新主键**医嘱，供后续签发/发放/计费重新锚定；与 **DM-ADM-011 §6.1.9a**、**AS-OP-DOR-004** IF-DRA-03 编排对齐。
- **边界说明**：**正向克隆**语义，**禁止**复用 `createRefundMedicationOrder` 等退费负向命令充当「再生」；发放、费用行由各自聚合编排。
- **入参**：`CloneMedicationRequestForPreservedChargeRegenerationCommand`（至少含 `sourceMedicationRequestId`、`operatorId`）。
- **出参**：新药品申请主键。
- **前置条件**：源申请可加载且未逻辑删除；`status_enum` **不得**为 `CANCELLED` / `HOLD`；`CloneMedicationRequestForPreservedChargeRegenerationCommand` 之 `sourceMedicationRequestId` 与 `operatorId` 均非空。
- **状态变化**：新行 `status_enum = DRAFT`（`initDraft`）。
- **聚合内影响**：按源行复制可对齐之业务字段；签发与退费链字段清空（含 `prescription_no`、`sign_code`、`refund_medicine_id`、`performer_check_id`、`check_time`、`status_reason`、`stop_time` 等）；`based_on_table`=`med_medication_request`、`based_on_id`=源主键；新 `bus_no`（按日序列）；`recorder_id` / `requester_id`=`operatorId`；`req_opened_time`=当前时刻、`req_authored_time`=空；`is_deleted=false`；落库前执行 `validateDosageSemantics`。
- **事件输出**：**先** `save` **后**发布 **`MedicationOrderDraftCreatedEvent`**（**B.14**）。
- **失败语义**：`MEDICATION_REQUEST_INVALID_ARGUMENT`（入参或 `operatorId` 为空）；`MEDICATION_REQUEST_NOT_FOUND`（源不存在）；`MEDICATION_REQUEST_RULE_CONFLICT`（源已删、源状态为取消/暂挂、或剂量不变式校验失败等）。

#### 6.1.17 撤销未完结负向退药申请（`voidOpenRefundMedicationOrder`）

- **业务意义**：门诊退费申请撤销时，软删尚未完结的负向退药申请单。
- **边界说明**：对称诊疗 `voidOpenRefundServiceOrder` / 耗材 `voidOpenRefundDeviceOrder`。
- **入参**：相关 Command（申请标识集合 + 操作者）。
- **出参**：—。
- **前置条件**：目标为未完结负向单。
- **状态变化**：逻辑删除。
- **失败语义**：状态不允许则拒绝。

#### 6.1.18 退费撤销后作废重开草稿（`voidReopenedDraftsAfterOutpatientRefundRevoke`）

- **业务意义**：门诊退费撤销后，清理因退费流程重开产生的无效草稿行。
- **入参**：相关 Command。
- **出参**：—。

#### 6.1.19 代煎作废后清除 suffering 标记（`clearSufferingFlagAfterDecoctionVoided`）

- **业务意义**：代煎 companion 作废/软删后，清除关联中药申请上的 suffering 标记，避免挡住再补充。
- **入参**：相关 Command / 申请标识。
- **出参**：—。

#### 6.1.20 代煎补充时标记 suffering（`markSufferingFlagForDecoctionSupplement`）

- **业务意义**：代煎费补充路径标记关联申请，与医生站代煎门禁配合。
- **入参**：相关 Command。
- **出参**：—。

> **变更（2026-09-23 · 模式 B）**：按代码回写门诊退费/代煎相关写方法。

### 6.2 读侧查询服务（MedicationRequestQueryService）

> 查询类动作无写库，以「查询范围（读库，无写入）+ 组装规则」表达；用于写前校验、工作台列表与历史追溯。**按处方单元聚合的打印/预览用只读视图**（含分方序号、分方原因、单元内药品行顺序等）由**用药处方QueryService或就诊领域事件后查询刷新**提供，本节不另列独立查询方法。

#### 6.2.1 按主键查询药品申请（`loadMedicationRequestById`）

- **业务意义**：按主键加载单条聚合根，支撑写前存在性与状态校验。
- **边界说明**：只读查询，不写库，不做跨域编排。
- **查询范围（读库，无写入）**：`med_medication_request` 主表单条。
- **过滤口径**：主键精确匹配；默认排除逻辑删除（与基类约定一致，以落库与交互契约为准）。
- **组装规则**：映射为领域实体或查询 DTO；不在读侧改写业务状态。
- **返回约束**：不存在或无权限时抛明确异常；存在则返回单条。

#### 6.2.2 按就诊查询药品申请列表（`listMedicationRequestsByEncounter`）

- **业务意义**：返回某次就诊下的药品申请集合，支撑医嘱工作台。
- **边界说明**：只读查询，不写库，不做跨域编排；列表优先投影/QueryService（BR-MRQ-021）。
- **查询范围（读库，无写入）**：`med_medication_request` 按 `encounter_id` 过滤。
- **过滤口径**：租户与就诊标识必选；默认排除逻辑删除。
- **组装规则**：列表 DTO；字段口径与 §3.1 一致；可合并必要展示字段（以落库与交互契约为准）。
- **返回约束**：允许空列表；不分页或分页策略以落库与交互契约为准。

#### 6.2.3 查询患者历史药品申请（`listMedicationRequestHistoryByPatient`）

- **业务意义**：按患者查询历史开立记录，排除当前在院就诊混入（BR-MRQ-022）。
- **边界说明**：只读查询，不写库，不做跨域编排。
- **查询范围（读库，无写入）**：`med_medication_request` 按 `patient_id` 过滤。
- **过滤口径**：排除当前就诊维度（BR-MRQ-022）；默认排除逻辑删除。
- **组装规则**：历史列表 DTO；不回写枚举中文到主表。
- **返回约束**：允许空列表；大数据量时性能策略以落库与交互契约为准。

#### 6.2.4 删除前置校验（`checkMedicationRequestDeletable`）

- **业务意义**：在移除草稿前校验是否允许删除，避免已收费仍走删除路径。
- **边界说明**：只读校验，不写库；与删除写操作分层（BR-MRQ-023）。
- **查询范围（读库，无写入）**：本聚合主键定位 + `ChargeItem` 关联按 `service_table`/`service_id` 查询已收费项（以落库与交互契约为准）。
- **过滤口径**：存在已收费 `ChargeItem` 则校验不通过（BR-MRQ-002）。
- **组装规则**：输出允许/不允许及原因码（以落库与交互契约为准）。
- **返回约束**：不修改任何聚合状态。

#### 6.2.5 签退前置校验（`checkMedicationRequestSignOffAllowed`）

- **业务意义**：在签退前校验是否允许签退，与已收费约束对齐（BR-MRQ-003）。
- **边界说明**：只读校验，不写库；与签退写操作分层（BR-MRQ-023）。
- **查询范围（读库，无写入）**：同 6.2.4 的费用关联查询口径。
- **过滤口径**：存在已收费则不允许签退（BR-MRQ-003）。
- **组装规则**：输出允许/不允许及原因码（以落库与交互契约为准）。
- **返回约束**：不修改任何聚合状态。

#### 6.2.6 按原单批量查退药关联申请（`listByRefundMedicineIdIn`）

- **业务意义**：AS-OP-FIN-003 IF-REF-04：收费执行退费前，按拟退收费行锚定的原药品申请 id，查询 `refund_medicine_id` 命中的退药/负向申请行，供应用层判断是否存在 `CANCELLED` 等未完成退药语义。
- **边界说明**：只读；`MedicationRequestRepository#findByRefundMedicineIdIn` 下推；不做跨域编排。
- **过滤口径**：原药品申请主键集合；空集合返回空列表。
- **组装规则**：返回主表实体列表（含 `statusEnum`、`refundMedicineId`）。
- **返回约束**：不修改聚合状态；门禁结论由应用层结合 **RequestStatus** 与 **EX-REF-201** 输出。

#### 6.2.7 住院护士站批量按就诊+状态查询（仓储派生 · 下游消费）

- **业务意义**：**AS-IP-NUR-001** 等批量场景：按就诊主键集合与 `status_enum` 加载药品申请行；应用层与 **DM-WKF-004** 诊疗申请 UNION 后按 `encounter_id` 聚合计数。
- **边界说明**：只读；`MedicationRequestQueryService#listByEncounterIdsAndStatusEnum` → `MedicationRequestRepository#findByEncounterIdInAndStatusEnum`。
- **过滤口径**：`encounter_id IN (...)` 且 `status_enum =` 给定 **RequestStatus** code（如 `ACTIVE`、`VERIFIED`）；软删由仓储基座过滤。
- **返回约束**：允许空列表；禁止循环单条 `findByEncounterId`。

#### 6.2.8 解析药品定义主键（`resolveMedicationDefinitionId`）

- **业务意义**：**AS-IP-NUR-005** IF-IP-PRC-05 已划价列表：补费父医嘱为药品父行时，由 `based_on_id`（父 `med_medication_request.id`）解析 `medication_id`，再经 **`MedicationDefinitionQueryService#findAllByIds`** 展示名。
- **边界说明**：只读；单条主键加载，不做跨域编排。
- **查询范围（读库，无写入）**：`med_medication_request` 主表按 `id`。
- **过滤口径**：排除 `is_deleted=true`；不存在或已删返回 `null`。
- **组装规则**：返回 `medication_id` 字段值（药品定义主键）。
- **返回约束**：`requestId` 为空返回 `null`；**不**抛错。

#### 6.2.9 FIFO 签发上下文（`loadFifoSignContextOrThrow`）

- **业务意义**：门诊医生站药品签发后 FIFO 发放编排加载医嘱字段切片（含药品目录分类码）。
- **边界说明**：只读；可调用 **`MedicationDefinitionQueryService`** 解析分类；不存在抛 `MEDICATION_REQUEST_NOT_FOUND`。
- **入参**：药品申请主键。
- **出参**：`MedicationRequestFifoSignContext`（只读 DTO）。
- **返回约束**：主键不存在抛错。

#### 6.2.10 批量加载实体（`mapEntitiesByIds`）

- **业务意义**：退药明细等读组装批量取主表实体，避免应用层直访仓储。
- **过滤口径**：排除 `is_deleted=true`；未命中键不入 Map。
- **入参**：药品申请主键集合。
- **出参**：`Map<Long, MedicationRequest>`。

#### 6.2.11 批量解析 content_json（`mapContentJsonByIds`）

- **业务意义**：执行单等读侧富化批量取 `content_json`。
- **过滤口径**：空白 `content_json` 不入 Map；已删不入 Map。
- **入参**：药品申请主键集合。
- **出参**：`Map<Long, String>`。

#### 6.2.12 门诊医生站按来源过滤列表（`listMedicationRequestsByEncounterForDoctorStation`）

- **业务意义**：**AS-OP-DOR-003 BR-ORD-010** 医生站列表，按 `generate_source_enum` 下推过滤。
- **入参**：就诊标识、`generateSourceEnum`（均非空）。
- **出参**：`List<MedicationRequestListQueryModel>`。
- **失败语义**：入参空抛 `MEDICATION_REQUEST_INVALID_ARGUMENT`。

#### 6.2.13 发药单筛选主键（`listMedicationRequestIdsByEncounterScopeAndStatusIn`）

- **业务意义**：在单就诊或多就诊范围内，按 `status_enum IN (...)` 解析药品申请主键集合（发药单筛选等）。
- **入参**：`encounterId` 与 `encounterIdsScope` 二选一；状态 code 集合。
- **出参**：主键列表；无法构成范围或状态集合空时返回空列表。

#### 6.2.14 住院医生站药品医嘱列表（`listInpatientDoctorStationOrders`）

- **业务意义**：替代废止 **OrderInfo** RM 药嘱子集；**AS-IP-DOR-003** 住院医生站列表。
- **入参**：就诊标识（必填）、`therapyEnum`（可空表示不限治疗类型）。
- **出参**：`List<MedicationRequestListQueryModel>`。

#### 6.2.15 住院护士站校对列表（`listInpatientNurseStationAuditOrders`）

- **业务意义**：**AS-IP-NUR-003 IF-IP-AUD-01** 校对台药嘱子集。
- **入参**：就诊 id 列表、状态 code 列表、`therapyEnum`。
- **出参**：`List<MedicationRequestListQueryModel>`。

#### 6.2.16 住院护士站执行列表（`listInpatientNurseStationExecuteOrders`）

- **业务意义**：**AS-IP-NUR-004** 执行台药嘱子集；状态含 **VERIFIED**、**STOPPED**（停嘱后仍展示）。
- **入参**：就诊 id 列表、`therapyEnum`。
- **出参**：`List<MedicationRequestListQueryModel>`。

#### 6.2.17 住院护士站补费父医嘱列表（`listInpatientNurseStationSupplementOrders`）

- **业务意义**：**AS-IP-NUR-005 IF-IP-PRC-02** 补费父药嘱子集。
- **入参**：就诊 id 列表、`therapyEnum`。
- **出参**：`List<MedicationRequestListQueryModel>`。

#### 6.2.18 按主键加载列表行（`getListModelById` / `mustGetListModelById`）

- **业务意义**：按表路由加载单条列表投影；`mustGet*` 在不存在时抛错。
- **入参**：药品申请主键。
- **出参**：`MedicationRequestListQueryModel` 或 `null`（`get*` 路径）。

#### 6.2.19 就诊维度中药处方按方列表（`listTcmHerbalPrescriptionsByEncounter`）

- **业务意义**：**AS-OP-DOR-010 · IF-DOR-34**、**AS-IP-DOR-006 · IF-IP-TCM-34** 加载当前就诊中药处方；领域层完成按方聚合，应用层仅 enrich 目录名与费用态。
- **边界说明**：只读；**不**替代应用层 ChargeItem 展示 enrich。
- **入参**：`encounterId`、`generateSourceEnum`（均非空；医生站通常为医生开立来源码）；**`therapyEnum`（可选，非空时按 `therapy_enum` 收窄，供住院长临 Tab）**。
- **查询范围（读库，无写入）**：`med_medication_request` 按就诊 + 来源过滤 → 内存筛 `tcm_flag=true` 且未软删 → 可选 `therapy_enum` 等值过滤。
- **组装规则**：已签发行按 `prescription_no` 聚合；草稿行按 `content_json.prescriptionGroupKey` 聚合（无 key 时退化为单行簇）；输出 `TcmHerbalPrescriptionGroupQueryModel`（方头含 `therapyEnum`/`methodCode`/`rateCode`/`dispensePerDuration` 等 + `lines` 扁平行列表）。
- **返回约束**：允许空列表；`encounterId`/`generateSourceEnum` 空抛 `MEDICATION_REQUEST_INVALID_ARGUMENT`。

#### 6.2.20 患者历史中药处方按方摘要（`listTcmHerbalPrescriptionHistoryByPatient`）

- **业务意义**：**AS-OP-DOR-010 · IF-DOR-39** 左侧历史处方树；排除当前就诊。
- **边界说明**：只读；**仅**已签发（有 `prescription_no`）且医生开立来源的 `tcm_flag=true` 饮片行参与聚合。
- **入参**：`patientId`、`currentEncounterId`（均非空）。
- **查询范围（读库，无写入）**：`med_medication_request` 按患者且 `encounter_id != currentEncounterId`。
- **组装规则**：按 `prescription_no` 聚合为 `TcmHerbalPrescriptionHistoryGroupQueryModel`（处方号、来源就诊、开立时间、行数、饮片定义 id 列表）；药名展示由应用层补全。
- **返回约束**：允许空列表；入参空抛 `MEDICATION_REQUEST_INVALID_ARGUMENT`。

#### 6.2.21 门诊已签发处方药品行分页（`pageOutpatientSignedPrescriptionDrugLines`）

- **业务意义**：**AS-OP-PHA-005 · IF-OPQ-02** 门诊「发药管理 → 处方查询」平铺明细分页；正方（`CP…`）与退方（`TCP…`）均纳入。
- **边界说明**：只读；**仅本聚合** `MedicationRequestRepository`（**B.6.4**）。收费状态 / 发药状态 / AMB 就诊 / 费用性质合同 / 注射目录主键由应用层预解析后写入 `OutpatientSignedPrescriptionDrugLinePageQuery.resolved*`；本方法**禁止** `EXISTS`/`JOIN` 他聚合根。含输液 YES/NO：同聚合内按 `resolvedMedicationIdsForInjectFlag` 解析处方号后再展开整方行。
- **入参**：`OutpatientSignedPrescriptionDrugLinePageQuery`（`startDate`/`endDate`→`req_opened_time`（空回落 `req_authored_time`）；可选 `rxTypeCode`、`infusionFilterCode`、`orderingDepartmentId`（→`org_id` 等值，**BR-MRQ-029**）、诸 `resolved*`；分页键）。
- **查询范围（读库，无写入）**：`med_medication_request`：`prescription_no` 非空；**且** `status_enum ∈ {ACTIVE, COMPLETED}`（真正签发/收费完成后的行；排除仅被签发时重分方写了 CP、仍为 `DRAFT` 的行，及签退回草稿行）；未软删；开方时间闭区间；预解析 IN 收窄；可选开单科室 `org_id` 等值。
- **组装规则**：映射为 `MedicationRequestListQueryModel`（含 `contractId`）；收费/发药/目录/诊断展示由应用层装配。
- **返回约束**：`Page<MedicationRequestListQueryModel>`；无匹配为空页；`resolved*` 为**空列表**时拒查（恒假谓词）。
- **协作预解析（他聚合 QS，供应用层 Loader）**：
  - `ChargeItemQueryService#listDistinctServiceIdsByStatusEnumAndServiceTable`
  - `MedicationDispenseQueryService#listDistinctMedicationRequestIdsByStatusEnum`
  - `MedicationDefinitionQueryService#listIdsByInjectFlag`
  - `ContractQueryService#listIdsByCategoryEnum`
  - 就诊 AMB / 患者关键字：既有 `EncounterQueryService` / `PatientQueryService` 族

#### 6.2.22 按目录主键 + 签发时间窗投影申请主键（`listIdsByMedicationIdsAndAuthoredTimeBetween`）

- **业务意义**：**AS-RPT-015** 结余留用报表：在药典批次收窄后的目录主键集合上，按 `req_authored_time` 闭区间投影药品申请主键。
- **边界说明**：只读；**仅本聚合**；未软删；`medication_id` 按 `ID_IN_QUERY_CHUNK_SIZE` 分批 IN；`req_authored_time` 非空且落窗。
- **入参**：`medicationIds`、`authoredStartInclusive`、`authoredEndInclusive`；任一侧空则空列表。
- **出参**：`List<Long>` 去重申请主键。
- **实现锚点**：`MedicationRequestQueryService#listIdsByMedicationIdsAndAuthoredTimeBetween`。

#### 6.2.23 按药品定义列出未签发草稿（`listUnsignedDraftsByMedicationId`）

- **业务意义**：调价保存/提交在途配套（**AS-FIN-PRC-001 · BR-PRC-SUB-003a**）：按本单调价药品列出未签发 `DRAFT`，供应用层按开立时刻做 **48h** 新鲜窗口分流（窗口内提示开立医生/患者/项目名自删；窗口外静默 CANCELLED）。
- **边界说明**：只读；**仅本聚合**；`status=DRAFT`、未软删、`req_authored_time` 空；填充患者/开立人/有效开立时刻（`req_opened_time`，空则回落 `updated_at`）。
- **入参**：`medicationId`；空则空列表。
- **出参**：`List<StaleUnsignedMedicationRequestDraftQueryModel>`（id / encounterId / patientId / requesterId / medicationId / openedAt / updatedAt）。
- **实现锚点**：`MedicationRequestQueryService#listUnsignedDraftsByMedicationId`。

#### 6.2.24 硬在途药品申请判定（`existsHardInTransitRequestByMedicationId`）

- **业务意义**：调价提交硬在途互斥（已签发/已校对/暂挂；**不含** DRAFT）。
- **边界说明**：只读；**仅本聚合**。
- **实现锚点**：`MedicationRequestQueryService#existsHardInTransitRequestByMedicationId`。

### 6.3 领域边界说明

- `MedicationRequest` 只描述药品医嘱/处方本体及开立、签发、校对、停嘱、取消、退药重开等与申请行直接相关的业务事实；费用金额、库存实物、执行记录明细不在本聚合内维护。
- 收费明细（`ChargeItem`）、药品发放（`MedicationDispense`）通过事件或应用编排订阅协作；本聚合提供稳定 `id` 引用，不替代他域不变式。
- 合理用药引擎、医保事前审核、**纯 UI 装配的初始化接口**等应用层能力不得冒充本节领域方法；分方能力以 **§6.1.15** 收口，**禁止**与 `ServiceRequest` 分方在同一无边界算法中混算。
- **处方预览、模板与版式、打印机队列、水印、补打字样、打印审计日志**等以应用服务与基础设施为主；领域层通过 **§6.1.15** 落库**处方单元与分方元数据**；**按单元聚合的打印侧只读数据**由**QueryService 能力**查询，与 §6.2 查询服务区分职责，避免将版式规则写入领域服务。
- 写编排由 `MedicationRequestDomainService`（或应用层 Manager）承担；读编排由 `MedicationRequestQueryService` 承担（与 `DM-ADM-001` §6 分工一致）。

---

## 7. 不变式


| 编号         | 不变式                                                                          | 说明                                                                                                                                                |
| ---------- | ---------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------- |
| BR-MRQ-001 | 保存时须进行库存校验（非删除、非诊疗类项目）                                                       | 库存不足拒绝保存                                                                                                                                          |
| BR-MRQ-002 | 已收费的药品申请不允许删除                                                                | 关联 ChargeItem 存在已收费状态则拒绝                                                                                                                          |
| BR-MRQ-003 | 已收费的药品申请不允许签退                                                                | 签退前校验费用项状态                                                                                                                                        |
| BR-MRQ-004 | 保存写入草稿态，签发写入已发送态                                                             | 状态迁移的统一语义收口                                                                                                                                       |
| BR-MRQ-005 | 保存时生成 **`bus_no`（`MR`）** 与来源；门诊分方/签发写入 **`prescription_no`（`CP`）** | 号码生成在应用/领域编排中完成（**B.10.9**），聚合只持久化结果；**`bus_no` / `prescription_no` 分序列，禁止混用 `MR` 前缀** |
| BR-MRQ-006 | 停嘱仅允许长期医嘱；临时与**出院带药**不提供停嘱；**禁止 ACTIVE 停嘱**（须 **VERIFIED**） | 写 `stop_time`、`stopper_id` 与 `stopped`；清空停嘱校对两字段；**IF-IP-DOR-20** 仅停选中行 |
| BR-MRQ-006a | 取消停嘱仅允许长期且当前为 `stopped`，且**尚未停嘱校对**（`stop_performer_check_id` 空） | 恢复：`performer_check_id` 非空 → `verified`，否则 → `active`；清空 `stop_time`/`stopper_id`/停嘱校对两字段；已停嘱校对须先取消停嘱校对 |
| BR-MRQ-006b | 住院可改开立/停嘱时间；签发时间不可改；改停嘱时间**保留**停嘱校对字段 | `OPENED` 仅写 `req_opened_time`；`STOP` 仅写 `stop_time`（不改停嘱人/停嘱校对）；**禁止**写 `req_authored_time` |
| BR-MRQ-006d | 停嘱校对只写痕迹、不改状态 | `verifyStopMedicationOrder`：长期 + `STOPPED` + 未停嘱校对 → 写 `stop_performer_check_id`/`stop_check_time`；仍 `STOPPED` |
| BR-MRQ-006e | 取消停嘱校对只清痕迹、不改状态；本期不校验操作人 | `cancelVerifyStopMedicationOrder`：已停嘱校对 → 清两字段；仍 `STOPPED`；解锁医生取消停嘱 |
| BR-MRQ-007 | 校对/退回时写入校对人和校对时间                                                             | 退回须写入；校对可选                                                                                                                                        |
| BR-MRQ-008 | 分组号允许批量更新与置空                                                                 | 非空批量保存更新；置空批量条件更新                                                                                                                                 |
| BR-MRQ-009 | 长期与临时/出院带药的数量语义不同：长期 = 单次用量，临时与**出院带药** = 总量                                               | 同名字段由治疗类型区分含义；`DISCHARGE_MEDICATION` 对齐 **TEMPORARY**（**BR-IP-ORD-029**）                                                                                                                                     |
| BR-MRQ-010 | 门诊等价临时医嘱；首日量仅长期专有；住院临时频次须为「立即」                                               | 入口与治疗类型的联合约束                                                                                                                                      |
| BR-MRQ-035 | **分次用量不一致（长期）**：① 仅 `therapy_enum=LONG_TERM` 且西药/中成药可写 `dose_schedule`/`quantity_schedule`；临时/出院带药/中药/诊疗须空。② 两列**同空或同有值**；有值时均为逗号分隔正数串，**段数相等**且等于该频次**每日执行次数**（按频次配置日点个数；顺序=当日点**早→晚**）；禁止缺段/多段/非正数。③ `dose_schedule`=**计量**口径（对齐 `dose`）；`quantity_schedule`=**拆零**口径（对齐 `quantity`）；**禁止**把计量串写入 `quantity_schedule`。④ `dose_unit_code`/`unit_code` 在有 schedule 时仍必填且各次共用；**不**为 schedule 另增单位列。⑤ **无痛切换**：schedule 皆空 → 读写/校验/执行**只认** `dose`/`quantity`（原路径）；有值 → 按槽读 schedule，原单值可作兜底但**不得**再当「全日每次同一量」。⑥ 与 `first_dose` **正交可并存**；首次槽换量优先 `first_dose`。⑦ **不**改停嘱/取消停嘱/状态机/出院校验等领域写规则 | 开立落库与读模型透传；执行侧按槽取量见 **AS-IP-NUR-004** |
| BR-MRQ-011 | 中药待发送态的创建/修改须使用专用动作，不得与西药 `create`/`change` 混用；移除草稿、签发、签退、校对、退回、分组等与西药**共用** | 主表 `tcm_flag`、付数、代煎与 `content_json` 承载中药事实；移除时同事务清理扩展与协作对象                                                                                        |
| BR-MRQ-012 | 取消与移除草稿语义不同：取消保留记录追溯，移除删除未签发草稿                                               | 追溯性约束                                                                                                                                             |
| BR-MRQ-013 | 退药采用负向请求单；须幂等防重复（**已有未完结负向单 → 返回已有主键**；原单已是负向单才拒）；**全量拷贝**原单临床/展示字段；`prescription_no`=`T`+原号（**`markAsRefundFromPrescription`**，与 **BR-DR-007** 同构）；`refund_medicine_id` 指向原单；定位范围仅本原单，不按患者扫他单 | 数量与原单一致（绝对值）；金额对冲由 REFUND 发放与 ChargeItem 承担；支撑 **BR-CHG-001** 同药嘱多收费行 |
| BR-MRQ-014 | 全退 + 重开生成新正向单，处方号加「C」标记                                                      | 可追溯关联                                                                                                                                             |
| BR-MRQ-015 | 药品处方分方是本聚合的领域责任                                                              | 分方规则只作用于 MedicationRequest 集合                                                                                                                     |
| BR-MRQ-016 | 临床自动分方按 P0 ～ P5 优先级执行                                                        | 毒麻精放单独成方、药房不同拆方等                                                                                                                                  |
| BR-MRQ-017 | 中医方剂手动管理不启用全自动分方                                                             | 手动方剂不改变医保红线                                                                                                                                       |
| BR-MRQ-018 | 开立/签发前须满足国家法定用量与合理诊疗底线                                                       | 普通 ≤ 7 日，急诊 ≤ 3 日，慢性病最长 3 个月                                                                                                                      |
| BR-MRQ-019 | 涉及医保基金支付的药品处方须通过参保地医保规则校验                                                    | 属地化、可配置拦截策略                                                                                                                                       |
| BR-MRQ-020 | 院内执行职称/科室/病种分级限额                                                             | 纯自费仍可能受院内管理约束                                                                                                                                     |
| BR-MRQ-021 | 就诊内医嘱列表优先QueryService 刷新；写前校验再回到写模型逐项校验                                               | 避免为单次校验拼装多域大查询                                                                                                                                    |
| BR-MRQ-022 | 患者历史药品申请查询须按 `patient_id` 过滤并排除当前就诊                                          | 防止历史列表混入在院医嘱                                                                                                                                      |
| BR-MRQ-023 | 查询、校验、写入分层明确                                                                 | 写侧不以QueryService为唯一事实来源                                                                                                                                    |
| BR-MRQ-024 | 西药轨道或中成药轨道各自同一处方单元内**药品申请行数**须 ≤5（**每条 `MedicationRequest` 计 1，相同 `medicationId` 不合并**）；超限须拆分为多处方单元 | 中药饮片、麻/精一另按单独单元规则计数；西药与中成药分轨后**各自**独立计入本上限（**BR-MRQ-032**） |
| BR-MRQ-025 | 中药饮片须单独处方单元                                                                  | 不得与西药/中成药行共用同一单元                                                                                                                                   |
| BR-MRQ-026 | 麻/精一药品须单独处方单元                                                                 | 单元内不得包含任何其他药品；住院逐日用量在业务上可对应多日多单元（以实现/本稿 **§6.1** 为准）                                                                                                           |
| BR-MRQ-027 | 门诊普通处方金额分方阈值及豁免条件可配置（如非慢病非长处方超阈值分方；慢病长处方/急诊重症/自费等豁免）                               | 住院不适用门诊金额分方约束；出院带药可按门诊相关规则由合规上下文切换                                                                                                                   |
| BR-MRQ-031 | 同一处方单元内，**相同药品目录主键**（`medicationId`）且**用法签名相同**（`method_code` + `rate_code` + `dosage_instruction` + `dose` + `dose_unit_code`）的医嘱行不得共存；须拆为不同 `prescription_no`；**禁止**用药名判定重复；成组（`group_id`）内若违反则整组无法分方并拒绝签发 | 与 BR-MRQ-024 品种上限并行生效；用法不同允许同方                                                                                                                           |
| BR-MRQ-032 | **西药与中成药须分属不同处方单元**：按目录 `medication_category_code`（西药 `2` / 中成药 `1`）分轨装箱；回写 `rx_type_code` 西药为 `1`/`3`/`5`、中成药为 `10`/`11`/`12`（随急诊/儿科医疗类别抬升） | 精二可入**同轨**普通处方；饮片/麻精/毒性规则不变；**废止**「西成药同方」口径 |
| BR-MRQ-033 | **单元含精二须回写处方类别 `8`**：药品性质 `pharmacology_category_code=5`（精二）入西药/中成药轨道装箱后，单元内**任一行**命中精二 → 整方 `rx_type_code` 固定为术语 355 **`8`（精二）**，**优先于**西药/中成药急诊、儿科抬升；仍可与同轨普通药同方（≤5 种） | 对齐打印「精二」标识与门诊处方查询筛选项；麻/精一仍为 `7` 独占 |
| BR-MRQ-034 | **中药饮片处方类别规范码为 `2`**：术语 355 历史并存码 `9`（展示「中药饮片」）与码 `2`（原展示「门诊中药饮片」）**业务同义**；落库/筛选以 **`2`** 为准，`9` 停用且查询入参须归一为 `2` | 下拉仅保留一条「中药饮片」；禁止新写入 `9` |
| BR-MRQ-028 | **医生站暂存分轨（AS-IP-DOR-003 / AS-OP-DOR-003）**：`therapy_enum=LONG_TERM` 时应用层**仅**调用本聚合草稿写方法，**不**在暂存建 `MedicationDispense`、**不**挂 `ChargeItem`；`therapy_enum=TEMPORARY` 时与门诊临时相同（暂存 Request + 费用草稿，发放仅在签发 FIFO）；删除长期不 void 费用、删除临时先 void 未计费费用 | 与 **BR-IP-ORD-005/006/008**、**DM-MED-002 §6.1.15** 一致 |
| BR-MRQ-029 | **`org_id` 与 `perform_location` 主键空间分列（B.6.5）**：`org_id` 仅存 **`adm_organization.id`**（开立/接诊科室）；`perform_location` 仅存 **`adm_location.id`**（发放药房库位）。读模型/发药 slip/医生站医嘱查询展示「开单科室」、按科室过滤、**`ServiceLocationTemplate`** 候选库房解析入参**只许**用 `org_id`；**禁止**以 `perform_location` 冒充组织或科室 | 对齐 **IF-DOR-20**、**IF-IP-DOR-22**、**IF-OPP-03** slip 装配；与 **DM-CUS-006** 模板入参一致 |
| BR-MRQ-030 | **暂存费用草稿库位透传（医生站）**：IF-DOR-14 / IF-IP-DOR-14（**仅 TEMPORARY 临嘱**）请求载荷 **`medicationPayload.dispenseLocationId`** 为目录 IF 已解析库位，应用层 **`createChargeIfPossible`** 经 **`resolveDraftChargePriceLine`**（`clientResolvedDispenseLocationId`）择 **LOT_NUMBER_PRICE** 批号售价；**非空时不再**二次 match **`ServiceLocationTemplate`**；该字段**不**落库本聚合 `perform_location`；权威发放库房仍以 **`MedicationDispense.location_id`** 为准（签发 FIFO） | 与 **AS-OP-DOR-003** / **AS-IP-DOR-003**、**DM-CUS-006** §1.1、**DM-ADM-011** 暂存锚点一致 |


---

## 8. 上下文映射

### 8.1 上游依赖


| 上游上下文 | 聚合                   | 关联方式                                             | 说明                                                          |
| ----- | -------------------- | ------------------------------------------------ | ----------------------------------------------------------- |
| 患者管理  | Patient              | `患者标识(PatientId)`                                | 药品申请归属的患者                                                   |
| 就诊管理  | Encounter            | `就诊标识(EncounterId)`                              | 药品申请关联的就诊，门诊必填                                              |
| 药品管理  | MedicationDefinition | `药品定义标识(MedicationDefinitionId)`                 | 药品主数据（品名、规格、剂型等）                                            |
| 组织架构  | Organization         | `开立科室标识(OrganizationId)` → **`org_id`**              | **仅**组织主键；禁止把 `perform_location` 当科室外键（**BR-MRQ-029**） |
| 人员管理  | Practitioner         | `开立医生标识(PractitionerId)`                         | 开立医生                                                        |
| 位置管理  | Location             | `所在位置标识(LocationId)` → **`location_id`**；`发放药房库位(PerformLocationId)` → **`perform_location`** | 库位与组织**分列**；`perform_location`=`adm_location.id`；门诊医生站草稿可空，**事实库房**以 **`MedicationDispense.location_id`** 为准；耗材对称见 **`DM-WKF-003`** |
| 频次管理  | Frequency            | `频次编码(FrequencyCode)`                            | 用药频次（qd / bid / tid / st 等）                                 |
| 诊断管理  | Condition            | `诊断标识(ConditionId)`                              | 关联诊断                                                        |
| 费用性质  | Contract             | `费用性质标识(ContractId)`                             | 引用 `fin_contract.id`，与 `DM-FIN-001` 一致；医嘱侧持久化列以实现/Flyway 为准 |


### 8.2 下游影响


| 下游上下文 | 聚合                       | 触发方式 | 说明                                            |
| ----- | ------------------------ | ---- | --------------------------------------------- |
| 收费管理  | ChargeItem               | 事件订阅 | 签发后驱动费用项生成；门诊药线 **`ChargeItem` 与每条 `MedicationDispense` 1:1**，`(serviceTable, serviceId)` 锚发放行（**`DM-ADM-011`** **`BR-CHG-001`**）  |
| 药品发放  | MedicationDispense       | 事件订阅 | 签发后驱动发放计划；通过 `药品申请标识(MedicationRequestId)` 关联 |
| 执行记录  | Procedure                | 事件订阅 | 校对后驱动执行登记；通过 `requestId` 关联                   |
| 住院医嘱列表  | `MedicationRequestQueryService` + 应用 `InpatientOrderListSkeleton`           | 直读主表 | `listInpatientDoctorStationOrders` / `listInpatientNurseStation*` 等（**非** OrderInfo RM）                                 |
| 处方审核  | PrescriptionReviewRecord | 事件订阅 | 签发前/后的合理用药审核与处方点评                             |
| 就诊账户  | Account                  | 事件订阅 | 通过 ChargeItem 间接影响账户费用归集                      |


### 8.3 引用规则

> **统一规则**：
>
> - **门诊场景**：引用 `就诊标识(EncounterId)`，`住院就诊标识(EpisodeOfCareId)` 为空
> - **住院场景**：同时引用 `就诊标识(EncounterId)` 和 `住院就诊标识(EpisodeOfCareId)`
> - 患者引用统一使用 `患者标识(PatientId)`（对应 FHIR `subject` 字段）
> - 药品引用统一使用 `药品定义标识(MedicationDefinitionId)`（对应 FHIR `medication` 字段）

### 8.4 事件流示意图

```
Practitioner          MedicationRequest              ChargeItem         MedicationDispense     Procedure
    │                       │                            │                    │                   │
    │── 开立医嘱 ──────────>│                            │                    │                   │
    │                       │                            │                    │                   │
    │                       │── SignedEvent ────────────>│── 生成费用项         │                   │
    │                       │                            │                    │                   │
    │                       │── SignedEvent ─────────────────────────────────>│── 生成发放计划       │
    │                       │                            │                    │                   │
    │                       │── VerifiedEvent ──────────────────────────────────────────────────>│── 执行登记
    │                       │                            │                    │                   │
    │                       │── StoppedEvent ──────────>│── 停止计费           │── 停止发放          │
    │                       │                            │                    │                   │
    │                       │── RefundRequestedEvent ──>│── 生成退费项         │── 退药回库          │
    │                       │                            │                    │                   │
```

---

## 9. 补充

### 9.1 治疗类型与用量语义

与 **§3.3** 一致，此处不重复展开。中药用量与方剂结构见 **§3.4** 与 BR-MRQ-011。

### 9.2 中西药分轨规则


| 阶段          | 西药/中成药                              | 中药饮片                               |
| ----------- | ----------------------------------- | ---------------------------------- |
| 开立草稿        | `createMedicationOrderDraft`        | `createHerbalMedicationOrderDraft` |
| 修改草稿        | `changeMedicationOrderDraft`        | `changeHerbalMedicationOrderDraft` |
| 移除草稿        | **共用** `removeMedicationOrderDraft` | **共用**（同事务清理 `content_json` 与协作对象） |
| 签发          | **共用** `signMedicationOrder`        | **共用**（按 `中药标识` 分支）                |
| 签退/校对/退回/停嘱 | **共用**                              | **共用**                             |


### 9.3 成组医嘱

通过 `分组号(groupId)` 将多条药品申请归为一组。典型场景为**成组输液**（溶媒 + 药品同时执行的最小单位）。组内行在处方分方时须保持**同一处方单元**（P2 优先级）。分组号允许批量更新与置空（BR-MRQ-008）。

### 9.4 处方分方优先级


| 优先级    | 规则类型        | 典型逻辑            |
| ------ | ----------- | --------------- |
| **P0** | 毒麻精放等特殊管理药品 | 单独成方或限定合并集；**麻/精一/毒性**须独占单元（BR-MRQ-026）；**精二**可与**同轨**西药或中成药同单元（≤5 种，BR-MRQ-024），且整方 **`rx_type_code=8`（BR-MRQ-033）** |
| **P0′** | 医保医疗类别（`med_type`） | 由 `encounter_diagnosis_ids` 解析就诊诊断行 → `med_type`；同方诊断改为**有交集即可**（不必集合全等），方头取并集；**不同医疗类别不得同处方** |

| **P1** | 发药药房不一致     | 签发 FIFO **首段** `location_id` 不同 → 不同处方单元          |
| **P2** | 成组输液        | 已成组行同一单元（`group_id`，§9.4）；拆方不得拆散同组 |
| **P3** | 中西药 / 西药·中成药 | **中药饮片**须单独单元（BR-MRQ-025）；**西药与中成药须分轨**（**BR-MRQ-032**），各自受 BR-MRQ-024 品种数约束 |
| **P4** | 长期/临时、频次差异  | 默认不合并           |
| **P5** | 诊断/费别       | 不一致则拆方；门诊在合规前提下可按诊断或疗程拆分，**不得**为套取医保对同一诊疗单元内合理成组用药做无临床依据的硬拆 |

**领域分方规则要点**（与 §6.1.15、BR-MRQ-024～027、**BR-MRQ-032** 一致，作为 P0～P5 的补充叙述）：

- **须单独构成处方单元**：麻醉药品、**第一类精神药品**、**毒性药品**（目录 **`pharmacology_category_code`** / 术语 `medicine_properties` 判定，**禁止**读门诊拆分属性）不得与其他任何药品共用同一处方单元；**中药饮片**须单独成单元，不得与西药或中成药混单。**普通药**再按西药与中成药分轨（`medication_category_code`，**BR-MRQ-032**）；各轨内合计超过**五条药品申请**时，必须拆成多个单元。**成组（`group_id`）合并为不可拆包后，仍按组内每条 req 计入行数上限**。
- **同药同用法互斥**：同一处方单元内，相同 `medicationId` 且用法签名相同（见 **BR-MRQ-031**）的医嘱行不得共存；须拆分 `prescription_no`。
- **医保医疗类别**：绑定诊断之 `med_type` 不同须分属不同处方（门诊结算口径；见 **P0′**）。
- **门诊与住院差异**：**当前实现仅门诊**医生站签发调用 `splitPrescription`；住院不适用门诊金额分方阈值。**门诊**单处方单元 `ChargeItem.totalPrice` 合计超 **`outpatientPrescriptionAmountLimitYuan`**（默认 500，可配置 99999 关闭）须拆方；慢病/急诊/自费等**金额豁免**首版未单独建模，后续可在应用层扩展快照字段。
- **成组不可拆散**：同一 `group_id` 的成组行必须落在同一处方单元，与 P2、§9.4 一致。
- **禁止违规分方**：禁止对同一诊断、同一疗程、临床上一组应同单开具的用药进行无依据的多单拆分以规避监管；禁止在数据上将毒麻精或饮片先与普通药混在同一待分方集合再事后拆分，算法应在首次划分时即单独成单元。
- **打印与QueryService**：处方单元以 **`prescription_no`** 持久化；**分方序号/分方原因首版不落库**。按 `prescription_no` 聚合药品行供打印装配，由**用药处方QueryService或就诊维度投影**提供，不纳入 §6.2 所列查询服务方法集合。


### 9.5 处方合规与医保管控

药品处方须通过**三层事前校验**（推荐顺序，可配置为同一引擎多策略）：

1. **法定用量与合理诊疗**（BR-MRQ-018）：普通处方常用量一般不超过 7 日；急诊一般不超过 3 日；慢性病/老年病最长不超过 3 个月且须注明理由；安全、有效、经济，不得开具超常处方/大处方
2. **医保属地规则**（BR-MRQ-019）：参保地金额/病种/目录限制，可配置拦截策略；中药饮片医保多数地区设上限并管控药味数量；重点监控/谈判药/高值药常需限付适应症、事前备案、双签
3. **院内分级管理**（BR-MRQ-020）：住院医师/主治/副高及以上职称分级限额；科室/病种阈值；事前系统拦截（超限须填写合理用药理由或审批）

**特殊药品管理**：麻醉药品/精神药品/医疗用毒性药品/放射性药品/特殊使用级抗菌药物，在分方中对应 P0 优先级，用量间接限制金额（BR-MRQ-016）。

### 9.6 药品轨合规规则编号（BR-RX-001～007）


| 规则 ID     | 简述                           |
| --------- | ---------------------------- |
| BR-RX-001 | 国家法定处方用量底线                   |
| BR-RX-002 | 超常处方 / 大处方合规义务               |
| BR-RX-003 | 医保属地支付上限、病种、中药饮片、谈判药等（可配置拦截） |
| BR-RX-004 | 院内职称分级、科室病种阈值、事前拦截           |
| BR-RX-005 | 特殊药品法定用量与权限                  |
| BR-RX-006 | 纯自费与医保结算通道差异（药品轨）            |
| BR-RX-007 | 三层事前与处方点评联动                  |


### 9.7 退药与重开机制


| 场景      | 实现方式                   | 关键约束                           |
| ------- | ---------------------- | ------------------------------ |
| 退药/退费   | 基于原医嘱创建**负向请求单** | `refund_medicine_id` 指向原单；**全量拷贝**原单字段；处方号 **`T`+原 `prescription_no`**（实体 **`markAsRefundFromPrescription`**）；须幂等防重；**`C` 前缀仅用于全退+重开** |
| 全退 + 重开 | 原单全退后全量拷贝生成**正向新单**    | 新业务号；处方号加「C」标记；走常规签发/校对/执行生命周期 |


### 9.8 与 ChargeItem 的职责边界


| 职责      | MedicationRequest         | ChargeItem           |
| ------- | ------------------------- | -------------------- |
| 用药事实    | 承载药品医嘱本体信息                | —                    |
| 计费时机    | 签发后驱动费用产生                 | 管理每条收费明细             |
| 删除/签退约束 | 受费用状态反向约束（BR-MRQ-002/003） | 标记收费状态               |
| 价格计算    | —                         | 暂存：`createChargeIfPossible` + **`resolveDraftChargePriceLine`**（载荷 **`dispenseLocationId`**，**BR-MRQ-030**）；签发后按发放锚点重算/升位 |
| 医保拆分    | —                         | 通过 Account 管理医保/自费分摊 |

### 9.4 文档变更历史

| 版本 | 日期 | 摘要 |
| ---- | ---- | ---- |
| v1.3.38 | 2026-09-23 | **分次用量不一致**：§3 增 `dose_schedule`/`quantity_schedule`；§3.3 长期分述；**BR-MRQ-035**；对齐 **AS-IP-DOR-003 · BR-IP-ORD-037** / **AS-IP-NUR-004 · BR-IP-EXE-040** |
| v1.3.37 | 2026-09-16 | §6.1.10a：`cancelUnsignedMedicationOrderDraft`（仅未签发 DRAFT，防调价误伤已签发） |
| v1.3.36 | 2026-09-16 | §6.2.23/24：按药品列未签发草稿 + 硬在途；对齐 **AS-FIN-PRC-001 v0.12**（取消定时/软在途确认） |
| v1.3.35 | 2026-09-16 | （已由 v1.3.36 取代）开立日 7 天口径；硬软在途；对齐 AS v0.11 |
| v1.3.34 | 2026-09-16 | §6.2.23：`listStaleUnsignedDrafts`（调价在途配套陈旧草稿清理） |
| v1.3.33 | 2026-09-10 | §6.2.22：`listIdsByMedicationIdsAndAuthoredTimeBetween`（**AS-RPT-015**） |
| v1.3.32 | 2026-08-20 | **中药饮片类别同义（BR-MRQ-034）**：规范码 `2`；停用冗余码 `9`；展示统一「中药饮片」 |
| v1.3.31 | 2026-08-20 | **精二处方类别（BR-MRQ-033）**：单元含药品性质精二时回写 `rx_type_code=8`；分方仍可与同轨普通药同方；对齐术语 355「精二」与门诊处方查询筛选 |
| v1.3.30 | 2026-08-19 | §3 `based_on_*` 增补住院长嘱改量链语义（**AS-IP-DOR-003 BR-IP-ORD-032** / **BR-IP-EXE-038**） |
| v1.3.29 | 2026-08-08 | **停嘱校对**：§3 增补 `stop_performer_check_id`/`stop_check_time`；§6.1.6/6a/6b 门禁与清/留字段；§6.1.6d/6e 停嘱校对/取消停嘱校对；**BR-MRQ-006/006a/006b/006d/006e**；对齐 **AS-IP-NUR-003 v1.16** / **AS-IP-DOR-003 v1.39** |
| v1.3.28 | 2026-08-01 | **BR-MRQ-013 幂等修正**：§6.1.11 已有未完结负向单返回已有主键；仅原单已是负向单拒；不按患者扫他单；对齐 **AS-OP-DOR-004 v1.2.8** |
| v1.3.27 | 2026-07-18 | **App IF-IP-DOR-26 已落地**；§6.1.6c 实现状态与 AS v1.33 对齐 |
| v1.3.26 | 2026-07-18 | **领域落地**：`stopper_id` + `changeMedicationRequestBusinessTime`；**G-IP-DOR-003-06 Closed**；App 当时仍为目标态（已被 v1.3.27 关闭） |
| v1.3.25 | 2026-07-18 | **停嘱人 `stopper_id`** + **§6.1.6c 改业务时间**；停嘱/取消停嘱写清停嘱人；**BR-MRQ-006/006a/006b**；对齐 **AS-IP-DOR-003 v1.31 · BR-IP-ORD-031 / IF-IP-DOR-26**（领域代码待落地，**G-IP-DOR-003-06**） |
| v1.3.24 | 2026-07-16 | **TherapyType.DISCHARGE_MEDICATION（出院带药）**：§3.3/§4.2/`therapy_enum` 字段；**BR-MRQ-006/009**；关闭 **G-IP-DOR-003-05**；对齐 **AS-IP-DOR-003 · BR-IP-ORD-029** |
| v1.3.23 | 2026-07-15 | §6.2.21 **`pageOutpatientSignedPrescriptionDrugLines`**（**AS-OP-PHA-005**；关闭 **GAP-OPQ-001**；B.6.4 预解析口径） |
| v1.3.22 | 2026-07-15 | **西药/中成药分方分轨（BR-MRQ-032）**：废止西成药同方；按 `medication_category_code` 分轨；`rx_type_code` 中成药 `10`/`11`/`12`；§6.1.15/§9.4/BR-MRQ-024 对齐 |
| v1.3.21 | 2026-07-13 | **分方装箱**：改为多轮扫描待装队列（同药同用法冲突包当轮跳过、不 premature 封桶），修复 7 行拆成 3+4 应为 5+2 |
| v1.3.20 | 2026-07-13 | **BR-MRQ-024 计数口径**：≤5 改为按 **req 行数**（相同 `medicationId` 不合并）；§6.1.15/§9.4 对齐 |
| v1.3.19 | 2026-07-13 | **分方规则补强**：§6.1.15 行快照增补用法签名字段；成组后按各行累计；新增 **BR-MRQ-031**（同目录+同用法不得同方）；§9.4 对齐 `OutpatientPrescriptionSplitPlanner` |
| v1.3.18 | 2026-07-09 | **退药负向单状态回归 openhis**：§6.1.11 负向单入口态由 `DRAFT` 改为 **`CANCELLED`**（`initAsRefundNegativeOrder`）；§5.2 事件表「创建退药单→已取消」与实现对齐 |
| v1.3.17 | 2026-06-26 | **退药负向单（BR-MRQ-013）**：§6.1.11 **`createRefundMedicationOrder`** 全量拷贝原单临床字段；实体 **`markAsRefundFromPrescription`**（`T`+原 `prescription_no`）；§9.7 与 **BR-DR-007** 对齐；药房闭环 **`completeMedicationRequestAfterRefund`** → `COMPLETED` |
| v1.3.16 | 2026-06-20 | **住院中医（AS-IP-DOR-006）**：§6.1.8 `rejectMedicationOrder` / **`ensureRejectable`** 扩展允许 **VERIFIED**（**IF-IP-TCM-38** · **BR-IP-TCM-009**）；§6.2.19 `listTcmHerbalPrescriptionsByEncounter` 三参重载可选 `therapyEnum`（**IF-IP-TCM-34**）；关闭 **G-IP-TCM-001** |
| v1.3.15 | 2026-06-18 | **IF-IP-DOR-25 成码闭环**：§6.1.6a 标注**已落地**；入参/事件补 `operatorId`；实体 **`ensureLongTermStoppedForCancelStop`** / **`fromStopped`** 与实现对齐；**G-IP-DOR-003-03 Closed**；对齐 **AS v1.17** |
| v1.3.14 | 2026-06-18 | **取消停嘱（IF-IP-DOR-25）**：§6.1.6a `cancelStopLongTermMedicationOrder`；**BR-MRQ-006a**；事件 `LongTermMedicationOrderStopCancelledEvent`；**BR-MRQ-006** 与 IF-IP-DOR-20 对齐为仅停选中行 |
| v1.3.13 | 2026-06-16 | **中医 Tab 领域对齐（AS-OP-DOR-010）**：§6.1.13/14 补全中药 Command 字段与 `quantity`/付数口径；§6.1.15 分方算法类迁至 `prescriptionsplit` 包；§6.2.19/20 新增按方 QueryService 与读模型 |
| v1.3.12 | 2026-06-11 | **暂存库位透传（BR-MRQ-030）**：IF-DOR-14/IF-IP-DOR-14 载荷 `dispenseLocationId` 仅用于费用草稿 FIFO 择价，不落库 `perform_location`；对齐 **ClinicalCatalogPriceResolveSupport#resolveDraftChargePriceLine** |
| v1.3.11 | 2026-06-11 | **废止 `sign_code`**：医生站签发不再落库签发批次列；批次语义由 IF 层一次请求内多目标聚合表达，**`prescription_no`（CP）** 仍为分方/打印主键 |
| v1.3.10 | 2026-06-11 | **组织/库位分列（BR-MRQ-029）**：明确 `org_id`=`adm_organization.id`、`perform_location`=`adm_location.id`；读路径「开单科室」只读 `org_id`；§8.1 上下文映射与 §3 字段表同步 |
| v1.3.9 | 2026-06-11 | **业务编号分序列**：`bus_no`→`MR`、`prescription_no`→`CP`（分方）、`sign_code`→`ASI`（签发批次）；对齐 **B.10.9** / `AggregateTypeConstants` |
| v1.3.8 | 2026-06-11 | **门诊处方分方成码**：§6.1.15 对齐 `splitPrescription` + `OutpatientPrescriptionSplitPlanner`（仅门诊签发、回写 `prescription_no`/`rx_type_code`、系统设置金额阈值）；§9.4 增补 **P0′ med_type**、毒性独占、首版不落分方序号/原因 |
| v1.3.7 | 2026-05-28 | **M1**：§6.1.11a `completeMedicationRequestAfterRefund`；§6.2.9～18 FIFO/批量/医生站过滤/住院列表读法对齐 Java |
| v1.3.6 | 2026-05-28 | ReadModel 废止：**OrderInfo** → **`MedicationRequestQueryService`** / **`InpatientOrderListSkeleton`**；产品名 **`MedicationDefinitionQueryService#findAllByIds`** |
| v1.3.5 | 2026-05-24 | §4.2 统一 **`TherapyType`** 与 `LONG_TERM`/`TEMPORARY` 落库口径；明确长期按次发放 vs 临时拆分行合计 |
| v1.3.4 | 2026-05-24 | §6.2.8 **`resolveMedicationDefinitionId`**（已划价补费父药嘱展示，**AS-IP-NUR-005** IF-IP-PRC-05） |
| v1.3.3 | 2026-05-22 | 校对/退回事件补 `status_enum`；**verify** 明确 **VERIFIED**；**reject** 须写校对追溯；门诊收费 **COMPLETED** 与住院校对分离（对齐 **AS-IP-NUR-003**） |

<!-- MODE-B-METHOD-INVENTORY 2026-09-23 -->

## 附录 · 模式 B 已实现方法清单（2026-09-23）

> 自 zxplat-domain 对应 *DomainService / *QueryService 抽取 public 方法名，用于关闭方法白名单 DOC-DRIFT。细规仍以正文 §6 与源码为准；本表不替代九章成稿。

### DomainService：`MedicationRequestDomainService`

| 方法 |
|------|
| `createMedicationOrderDraft` |
| `changeMedicationOrderDraft` |
| `removeMedicationOrderDraft` |
| `voidOpenRefundMedicationOrder` |
| `voidReopenedDraftsAfterOutpatientRefundRevoke` |
| `signMedicationOrder` |
| `signOffMedicationOrder` |
| `stopLongTermMedicationOrder` |
| `cancelStopLongTermMedicationOrder` |
| `changeMedicationRequestBusinessTime` |
| `verifyStopMedicationOrder` |
| `cancelVerifyStopMedicationOrder` |
| `verifyMedicationOrder` |
| `completeMedicationRequestAfterOutpatientCharge` |
| `rejectMedicationOrder` |
| `adjustMedicationOrderGroup` |
| `cancelMedicationOrder` |
| `cancelUnsignedMedicationOrderDraft` |
| `createRefundMedicationOrder` |
| `completeMedicationRequestAfterRefund` |
| `reopenMedicationOrder` |
| `createHerbalMedicationOrderDraft` |
| `clearSufferingFlagAfterDecoctionVoided` |
| `markSufferingFlagForDecoctionSupplement` |
| `changeHerbalMedicationOrderDraft` |
| `splitPrescription` |
| `cloneMedicationRequestForPreservedChargeRegeneration` |

### QueryService：`MedicationRequestQueryService`

| 方法 |
|------|
| `loadMedicationRequestById` |
| `findById` |
| `listByIds` |
| `listByGroupIds` |
| `listByBasedOnAnchor` |
| `listByBasedOnAnchors` |
| `existsByMedicationId` |
| `existsInTransitRequestByMedicationId` |
| `listUnsignedDraftsByMedicationId` |
| `existsHardInTransitRequestByMedicationId` |
| `resolveMedicationDefinitionId` |
| `loadFifoSignContextOrThrow` |
| `mapFifoSignContextsByIdsOrThrow` |
| `mapEntitiesByIds` |
| `mapContentJsonByIds` |
| `listMedicationRequestsByEncounter` |
| `listMedicationRequestsByEncounterForDoctorStation` |
| `mapMedicationRequestsByEncounterIdsForDoctorStation` |
| `listTcmHerbalPrescriptionsByEncounter` |
| `findTcmPrescriptionNoByGroupKey` |
| `listTcmHerbalPrescriptionHistoryByPatient` |
| `listMedicationRequestIdsByPatientScopeAndStatusIn` |
| `listMedicationRequestIdsByPatientScopeAndOrgId` |
| `listMedicationRequestIdsByEncounterAndOrgId` |
| `listMedicationRequestIdsByEncounterIdsAndOrgId` |
| `listMedicationRequestIdsByOrgId` |
| `listMedicationRequestListModelsByIds` |
| `listMedicationRequestListModelsByPrescriptionNos` |
| `listDistinctSignedPrescriptionNosByOpenedTimeWindow` |
| `listMedicationRequestIdsByEncounterScopeAndStatusIn` |
| `listDistinctEncounterIdsByPatientForDoctorStation` |
| `listMedicationRequestHistoryByPatient` |
| `checkMedicationRequestDeletable` |
| `checkMedicationRequestSignOffAllowed` |
| `listByRefundMedicineIdIn` |
| `listByEncounterIdsAndStatusEnum` |
| `listInpatientDoctorStationOrders` |
| `listInpatientNurseStationAuditOrders` |
| `listInpatientNurseStationExecuteOrders` |
| `listInpatientNurseStationSupplementOrders` |
| `getListModelById` |
| `mustGetListModelById` |
| `pageOutpatientSignedPrescriptionDrugLines` |
| `countOutpatientSignedPrescriptionSummary` |
| `listOutpatientSignedMedicationRequestIdsInOpenedWindow` |
| `listIdsByMedicationIdsAndAuthoredTimeBetween` |
| `listDistinctPrescriptionNosByMedicationRequestIds` |
| `listSignedPrescriptionNosByMedicationIdsInOpenedWindow` |
| `countReferencingEncounterDiagnosisId` |
| `listIdsReferencingAnyEncounterDiagnosisId` |

