# DM-CLN-001 AllergyIntolerance 过敏与不耐受

> **限界上下文**：CLN（临床域）\
> **聚合类型**：实体聚合（单表）\
> **版本**：v1.0.1 · **日期**：2026-05-28

## 1. 概述

### 1.1 业务定义

过敏与不耐受用于沉淀门诊皮试场景下的过敏记录事实，核心回答“该患者在本次就诊中对哪些药品/物质存在风险提示”。

本上下文沿用通用枚举名（如 FHIR `ClinicalStatus` 的 `active`/`inactive`），**语义以门诊皮试业务为准**（如将阳性/阴性映射到上述取值），与跨场景 FHIR 通用释义不必一致。

### 1.2 核心职责

| 职责 | 说明 |
|---|---|
| 皮试记录沉淀 | 记录服务申请、就诊、患者、药品、批号及结果状态 |
| 风险状态表达 | 通过临床状态、验证状态、危险程度、严重程度表达风险级别 |
| 临床追溯 | 保留记录者、断言者、记录时间与说明，支持追责与回溯 |
| 读侧支撑 | 为皮试列表查询、过敏史标记提供结构化基础数据 |

### 1.3 适用范围

| 类型 | 说明 |
|---|---|
| 门诊皮试执行 | 护士记录皮试结果并保存 |
| 皮试记录维护 | 对误录记录进行更新或删除 |
| 医生站展示 | 展示就诊维度过敏记录及状态 |

---

## 2. 聚合根与持久化形态

### 2.1 持久化形态

**持久化形态**：单表。

聚合根对应表 `cln_allergy_intolerance`，主键 `id`；皮试记录以追加/同 ID 覆盖方式写入，删除通过框架审计字段 `isDeleted` 实现，不在本聚合字段表中单独定义。

**高频查询模式（建议索引口径）**：按 `encounter_id`、`patient_id`、`request_id` 列表过滤；按 `medication_id`、临床/验证状态枚举筛选；典型列表与皮试回显走上述列组合。

### 2.2 聚合根标识

| 属性 | 类型 | 说明 |
|---|---|---|
| 过敏记录标识 | `Long` | 主键 `id` |

### 2.3 状态机

> 本聚合为单表追加型记录，生命周期由应用层保存/删除动作驱动，无复杂业务状态机。

```
  [*] ──新增──> [已记录] ──更新（同ID）──> [已更新]
                    │                          │
                    └──────── 删除 ──> [已删除] <──删除──┘
```

#### 状态定义

| value | code | info | 语义 |
| ----- | ---- | ---- | ---- |
| 1 | recorded | 已记录 | 新增保存后的初始状态，允许更新、删除 |
| 2 | updated | 已更新 | 同ID覆盖保存后的状态，允许更新、删除 |
| 3 | deleted | 已删除 | 删除后不再参与查询 | — |

> **业务状态字段**（独立于持久化生命周期）：`clinicalStatusEnum`（临床状态）、`verificationStatusEnum`（验证状态）是业务语义字段，见 §4。

---

## 3. 实体

### 3.1 过敏与不耐受（AllergyIntolerance）

AllergyIntolerance 聚合根的属性定义，对应 `cln_allergy_intolerance` 表。

| 属性 | 字段名 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| 过敏记录标识 | `id` | `Long` | 是 | 主键 |
| 服务申请标识 | `request_id` | `Long` | 是 | 服务申请 ID |
| 就诊标识 | `encounter_id` | `Long` | 是 | 就诊 ID |
| 患者标识 | `patient_id` | `Long` | 是 | 患者 ID |
| 药品定义标识 | `medication_id` | `Long` | 否 | 药品定义 ID |
| 批号 | `lot_number` | `String` | 否 | 批次号 |
| 临床状态 | `clinical_status_enum` | `String` | 是 | 见 §4.1 ClinicalStatus（存枚举 code） |
| 验证状态 | `verification_status_enum` | `String` | 是 | 见 §4.1 ConditionVerificationStatus（存枚举 code） |
| 类型 | `type_enum` | `String` | 否 | 保留枚举位（存 code；暂无专用枚举时按约定字典值域） |
| 过敏原类别编码 | `category_code` | `String` | 否 | 过敏原类别编码 |
| 危险程度 | `criticality_enum` | `String` | 否 | 见 §4.1 Criticality（存枚举 code） |
| 过敏物质编码 | `code` | `String` | 否 | 过敏物质编码 |
| 反应描述 | `description` | `String` | 否 | 反应描述 |
| 严重程度 | `severity_enum` | `String` | 否 | 见 §4.1 Severity（存枚举 code） |
| 过敏发生日期 | `onset_date_time` | `Date` | 否 | 过敏发生开始日期 |
| 记录日期 | `recorded_date` | `Date` | 否 | 记录日期 |
| 最后反应日期 | `last_reaction_occurrence` | `Date` | 否 | 最后反应发生日期 |
| 曝露路线 | `exposure_route` | `String` | 否 | 曝露路线编码 |
| 记录者标识 | `practitioner_id` | `Long` | 否 | 记录者 ID |
| 断言者标识 | `check_practitioner_id` | `Long` | 否 | 断言者 ID |
| 备注 | `note` | `String` | 否 | 备注 |

> 当前为单表模型；反应子实体、交叉过敏映射、值对象快照结构为设计预留，实现中尚未落地。

---

## 4. 值对象

### 4.1 枚举说明（已实现，含取值明细）

#### 4.1.1 临床状态（`ClinicalStatus` -> `clinicalStatusEnum`）

> 取值与 **§4** 枚举列一致；**「语义说明」列描述的是本域皮试场景下的读法**，非通用 FHIR 跨场景定义。

| value | code | info | 语义说明 |
|---|---|---|---|
| 1 | `active` | 阳性 | 当前皮试结果为阳性风险状态 |
| 2 | `inactive` | 阴性 | 当前皮试结果为阴性状态 |
| 3 | `resolved` | 已解决 | 已处理或不再持续 |
| 4 | `unknown` | 未知 | 状态不明确或待确认 |

#### 4.1.2 验证状态（`ConditionVerificationStatus` -> `verificationStatusEnum`）

| value | code | info | 语义说明 |
|---|---|---|---|
| 1 | `UNCONFIRMED` | 未确诊 | 初步判断，尚未确认 |
| 2 | `PROVISIONAL` | 疑似 | 暂定状态，待进一步确认 |
| 3 | `DIFFERENTIAL` | 有差异 | 处于鉴别判断阶段 |
| 4 | `CONFIRMED` | 已确诊 | 已确认的诊断结论 |
| 5 | `REFUTED` | 已排除 | 该项风险被排除 |
| 6 | `ENTERED_IN_ERROR` | 未知 | 原记录标记为录入错误（当前实现 `info` 为“未知”） |

#### 4.1.3 严重程度（`Severity` -> `severityEnum`）

| value | code | info | 语义说明 |
|---|---|---|---|
| 0 | `mild` | 轻微 | 风险或反应程度较轻 |
| 1 | `moderate` | 中度 | 风险或反应程度中等 |
| 2 | `severe` | 严重 | 风险或反应程度较重 |

#### 4.1.4 危险程度（`Criticality` -> `criticalityEnum`）

| value | code | info | 语义说明 |
|---|---|---|---|
| 0 | `low` | 低 | 危险程度低 |
| 1 | `high` | 高 | 危险程度高 |
| 2 | `unassessable` | 无法评估 | 暂无法评估危险程度 |

> 相关枚举：`ClinicalStatus`、`ConditionVerificationStatus`、`Severity`、`Criticality`。

### 4.2 代码字段但未发现独立枚举

| 字段 | 当前口径 | 说明 |
|---|---|---|
| `typeEnum` | `String` | 保留枚举位（存 code；暂无专用枚举） |
| `categoryCode` | `String` | 类别编码，按编码字典使用 |
| `exposureRoute` | `String` | 曝露路线编码，按编码字典使用 |

---

## 5. 领域事件

### 5.1 事件定义

| 事件名称 | 英文名称 | 触发时机 | 携带数据 | 消费方 |
|---|---|---|---|---|
| 过敏记录已保存 | `AllergyIntoleranceRecordedEvent` | 保存皮试记录成功后（新增/更新） | 记录ID、就诊ID、患者ID、药品ID、状态字段 | ConditionQueryService 查询、过敏史标记 |
| 过敏记录已删除 | `AllergyIntoleranceRemovedEvent` | 删除皮试记录成功后 | 记录ID、就诊ID、患者ID | ConditionQueryService 查询、过敏史标记 |

> 设计预留：当前应用实现以 CRUD 为主，事件发布机制已在领域模型定义，待实现收敛后以实际为准。

### 5.2 事件触发规则

| 当前状态 | 操作 | 目标状态 | 发布事件 |
|---|---|---|---|
| — | 新增记录 | 已记录 | `AllergyIntoleranceRecordedEvent` |
| 已记录/已更新 | 更新记录 | 已更新 | `AllergyIntoleranceRecordedEvent` |
| 已记录/已更新 | 删除记录 | 已删除 | `AllergyIntoleranceRemovedEvent` |

---

## 6. 领域服务

### 6.1 写侧领域服务（AllergyIntoleranceDomainService）

#### 6.1.1 保存皮试记录（`saveSkinTestRecord`）

- **业务意义**：沉淀或修正门诊皮试产生的过敏风险事实。
- **边界说明**：仅处理 `cln_allergy_intolerance` 聚合内数据，不编排医嘱拦截、收费、库存等跨域流程。
- **入参**：`requestId`、`encounterId`、`patientId`、`clinicalStatusEnum`、`verificationStatusEnum`；更新时须带 `id`。其余字段（`medicationId`、`lotNumber`、`typeEnum`、`categoryCode`、`criticalityEnum`、`code`、`description`、`severityEnum`、`onsetDateTime`、`recordedDate`、`lastReactionOccurrence`、`exposureRoute`、`practitionerId`、`checkPractitionerId`、`note` 等）按 §3.1 选填。
- **出参**：过敏记录标识（`id`）；新增返回新主键，更新返回同一条记录的 `id`。
- **前置条件**：
  - `requestId`、`encounterId`、`patientId` 必填；
  - `clinicalStatusEnum`、`verificationStatusEnum` 必填；
  - 更新时 `id` 必须存在且可访问。
- **状态变化**：`— -> 已记录`（新增） / `已记录|已更新 -> 已更新`（更新）。
- **聚合内影响**：
  - 主表：写入或更新过敏记录主字段；
  - 子表：`N/A`（当前无子实体）。
- **事件输出**：`AllergyIntoleranceRecordedEvent`（载荷：`id`、`encounterId`、`patientId`、`medicationId`、状态字段）。
- **失败语义**：关键参数缺失、记录不存在、越权更新时返回业务异常。

#### 6.1.2 删除皮试记录（`deleteSkinTestRecord`）

- **业务意义**：移除误录或无效的皮试记录，避免后续风险判读失真。
- **边界说明**：仅处理本聚合删除语义，不级联修改外部聚合数据。
- **入参**：过敏记录标识（`recordId`，与聚合 `id` 同义）。
- **出参**：—。
- **前置条件**：
  - `recordId` 必填；
  - 目标记录存在。
- **状态变化**：`已记录|已更新 -> 已删除`。
- **聚合内影响**：
  - 主表：执行删除（逻辑/物理以实现口径为准）；
  - 子表：`N/A`。
- **事件输出**：`AllergyIntoleranceRemovedEvent`（载荷：`id`、`encounterId`、`patientId`）。
- **失败语义**：记录不存在或删除条件不满足时返回业务异常。

### 6.2 读侧查询服务（AllergyIntoleranceQueryService）

#### 6.2.1 查询皮试记录（`getRecordInfo`）

- **业务意义**：按就诊维度回显皮试记录和风险状态。
- **边界说明**：只读查询，不写库，不做跨域编排。
- **查询范围（读库，无写入）**：`cln_allergy_intolerance` 及必要关联QueryService。
- **过滤口径**：默认过滤删除记录。
- **组装规则**：主记录 + 批号 + 枚举文本。
- **返回约束**：无匹配时返回空集合。

#### 6.2.2 过敏史标记派生（`getAllergyHistoryFlag`）

- **业务意义**：派生患者是否存在过敏史标记，供病历与就诊概览展示。
- **边界说明**：只读派生，不写库，不做跨域编排；不回写任何聚合或QueryService 字段。
- **查询范围（读库，无写入）**：指定患者/就诊下有效过敏记录。
- **过滤口径**：排除删除记录。
- **组装规则**：按“是否存在有效记录”派生布尔值。
- **返回约束**：无记录返回 `false`。

### 6.3 领域边界说明

- 本聚合仅负责皮试过敏记录事实（保存、删除、查询口径定义），不直接编排医嘱拦截、收费、库存等跨域流程；
- 下游协同通过应用层查询编排或事件机制触发，聚合内不直接修改下游聚合状态；
- 字典初始化、页面组装属于应用层能力，领域层仅提供可复用的业务规则与数据语义；
- `init`（字典初始化）属于前端元数据装配能力，不纳入领域服务方法清单；
- 应用服务可复用本聚合的枚举定义（见 §4）进行选项下发，但该过程不构成领域行为。

---

## 7. 不变式

| 编号 | 不变式 | 说明 |
|---|---|---|
| BR-ALI-001 | 保存记录必须绑定申请、就诊、患者 | `requestId` / `encounterId` / `patientId` 必填 |
| BR-ALI-002 | 临床状态与验证状态必须成对记录 | `clinicalStatusEnum` 与 `verificationStatusEnum` 不能为空 |
| BR-ALI-003 | 删除必须基于记录ID | 防止误删 |
| BR-ALI-004 | 记录者、断言者若填写须可追溯到有效人员 | `practitionerId` / `checkPractitionerId` 非空时须为有效引用；未填则不强制 |

---

## 8. 上下文映射

### 8.1 上游依赖

| 上游上下文 | 聚合 | 关联方式 | 说明 |
|---|---|---|---|
| 工作流域（WKF） | ServiceRequest / ActivityDefinition | `requestId` + 皮试项目定义ID | 皮试记录来源 |
| 管理域（ADM） | Encounter / Patient / Practitioner | `encounterId` / `patientId` / `practitionerId` | 就诊、患者与执行人员锚点 |
| 药品域（MED） | MedicationDefinition | `medicationId` | 对应皮试药品 |

### 8.2 下游影响

| 下游上下文 | 聚合 | 触发方式 | 说明 |
|---|---|---|---|
| 病历文档 | EMR 投影 | 事件/查询 | 展示过敏事实 |
| 医生站用药安全 | MedicationRequest | 查询/事件 | 过敏史提示与后续拦截策略输入 |

### 8.3 引用规则

- 跨聚合引用仅使用 ID，不直接持有他聚合对象，也不复制外部聚合快照字段；
- 过敏分类、曝露路线等编码字段按字典解释；
- 查询侧负责枚举值转文本，聚合内仅持久化枚举值；
- 事件载荷遵循最小化原则，仅携带下游判断必需的标识与状态字段。

### 8.4 事件流示意图

```text
ServiceRequest/Encounter/Patient/MedicationDefinition
        └──(ID 引用)──> AllergyIntolerance
                              ├── AllergyIntoleranceRecordedEvent ──> EMR / MedicationRequest
                              └── AllergyIntoleranceRemovedEvent  ──> EMR / MedicationRequest
```

---

### 9.1 变更历史

| 版本 | 日期 | 摘要 |
| --- | --- | --- |
| v1.0.1 | 2026-05-28 | **M1**：§6.1/§6.2 与 `AllergyIntoleranceDomainService`/`AllergyIntoleranceQueryService` 白名单对齐（2写+2读） |
| v1.0 | — | 初版 |

---

## 9. 补充

无

<!-- MODE-B-METHOD-INVENTORY 2026-09-23 -->

## 附录 · 模式 B 已实现方法清单（2026-09-23）

> 自 zxplat-domain 对应 *DomainService / *QueryService 抽取 public 方法名，用于关闭方法白名单 DOC-DRIFT。细规仍以正文 §6 与源码为准；本表不替代九章成稿。

### DomainService：`AllergyIntoleranceDomainService`

| 方法 |
|------|
| `saveSkinTestRecord` |
| `saveDeclaredAllergyRecord` |
| `saveNkda` |
| `deleteSkinTestRecord` |
| `deleteAllergyHistoryRecord` |

### QueryService：`AllergyIntoleranceQueryService`

| 方法 |
|------|
| `getRecordInfo` |
| `listPatientAllergyHistory` |
| `mustGetRecordById` |
| `getAllergyHistoryFlag` |

