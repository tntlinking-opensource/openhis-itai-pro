# DM-CLN-003 Condition 诊断记录

> **限界上下文**：CLN（临床域）\
> **聚合类型**：实体聚合\
> **版本**：v1.4.0 · **日期**：2026-07-18
>
> ## 退役声明（权威）
>
> **自 2026-07-18 起本聚合业务退役**，见 [`health/设计决策/DD-诊断绑定改造-一药多诊断与Condition退役.md`](../设计决策/DD-诊断绑定改造-一药多诊断与Condition退役.md)。
>
> | 项 | 口径 |
> |----|------|
> | 表 `cln_condition` | **保留**，允许永久空表 |
> | 写 / 读 | **禁止**业务调用 `ConditionDomainService` / `ConditionQueryService`（类已 `@Deprecated`） |
> | 就诊诊断 | 改由 `adm_encounter_diagnosis.condition_definition_id` 直挂目录 |
> | 医嘱/账单绑定 | `encounter_diagnosis_ids`（有序 ids 串）指向就诊诊断行 |
>
> 下文为历史契约存档，**不得再作为成码依据**。

## 1. 概述

### 1.1 业务定义

诊断记录（Condition）是患者侧诊断事实聚合，负责沉淀"患者被诊断了什么"的事实数据。它承载验证状态、临床状态、分类、严重程度、发病与记录信息等内容，并通过 `definitionId` 引用诊断定义目录。

本聚合关注事实落库，不直接维护就诊诊断关联（`EncounterDiagnosis`）与ConditionQueryService 查询；相关协同由应用服务编排完成。

### 1.2 核心职责

| 职责 | 说明 |
|---|---|
| 诊断事实落库 | 保存患者被诊断的事实数据，含状态、分类、发病信息 |
| 多状态表达 | 通过验证状态与临床状态组合精确描述诊断可信度与病程状态 |
| 诊断引用目录 | 通过 `definitionId` 引用诊断定义，实现定义与事实分离 |
| 事实生命周期管理 | 支持保存（新增/更新）与删除操作，保留历史可追溯 |

### 1.3 适用范围

| 类型 | 说明 |
|---|---|
| 医生站诊断录入 | 医生在就诊中保存或更新患者诊断 |
| 入院诊断创建 | 住院登记时建立入院诊断事实 |
| 诊断删除 | 删除错误录入的诊断记录（有医嘱引用时禁止删除） |
| 病历/就诊QueryService | 为ConditionQueryService 查询与就诊QueryService提供诊断事实来源 |

### 1.4 与 FHIR Condition 的关系

| 维度 | 说明 |
|---|---|
| FHIR 对齐 | 对应 FHIR R5 `Condition` 资源，描述患者的临床诊断事实 |
| 验证状态 | `verificationStatusEnum` 对应 `Condition.verificationStatus` |
| 临床状态 | `clinicalStatusEnum` 对应 `Condition.clinicalStatus` |
| 本地扩展 | `tcmFlag`（中医标识）、`ybNo`（医保编码）为本地扩展字段 |

---

## 2. 聚合根与持久化形态

### 2.1 持久化形态

**持久化形态**：单表。

聚合根对应表 `cln_condition`，主键 `id`；诊断事实由单行承载，验证状态与临床状态以枚举字段表达。

**高频查询模式（建议索引口径）**：按 `patient_id`、`definition_id` 过滤；与就诊的关联由应用层或QueryService维护；列表可按验证/临床状态、分类、记录时间组合筛选。

### 2.2 聚合根标识

| 属性 | 类型 | 说明 |
|---|---|---|
| 诊断记录标识 | `ConditionId` | 主键，雪花 ID |

### 2.3 状态机

`verificationStatusEnum` 与 `clinicalStatusEnum` 共同表达诊断状态。

```
验证状态迁移：

[未确诊] ──→ [疑似] ──→ [有差异] ──→ [已确诊] ──→ [康复]
   │              │                         │
   └──────────────→ [已排除]          [复发] ──→ [再发] ──→ [不活跃]
```

#### 2.3.1 验证状态（`ConditionVerificationStatus`）

| 状态 | 英文 | 说明 | 允许的操作 |
|---|---|---|---|
| 未确诊 | `UNCONFIRMED` | 初步怀疑 | 更新状态、删除 |
| 疑似 | `PROVISIONAL` | 暂定诊断 | 更新状态、删除 |
| 有差异 | `DIFFERENTIAL` | 鉴别诊断 | 更新状态 |
| 已确诊 | `CONFIRMED` | 最终确认 | 更新状态 |
| 已排除 | `REFUTED` | 已排除该诊断 | — |
| 录入错误 | `ENTERED_IN_ERROR` | 录入有误 | — |

#### 2.3.2 临床状态（`ConditionClinicalStatus`）

| 值 | code | 中文 |
|---|---|---|
| 1 | `ACTIVE` | 活跃 |
| 2 | `RECURRENCE` | 复发 |
| 3 | `RELAPSE` | 再发 |
| 4 | `INACTIVE` | 不活跃 |
| 5 | `REMISSION` | 体验复发风险 |
| 6 | `RESOLVED` | 康复 |
| 7 | `UNKNOWN` | 未知 |

> 说明：上述中文来自枚举展示列 `info`，保留原文口径。取值级说明见 **§4.3 / §4.4**。

---

## 3. 实体

Condition 聚合内无子实体，全部字段由聚合根承载。

### 3.1 Condition（聚合根，对应表：`cln_condition`）

| 属性 | 字段名 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| 诊断记录标识 | `id` | `Long` | 是 | 主键 |
| 验证状态 | `verification_status_enum` | `Integer` | 否 | 验证状态，见 §2.3.1 |
| 临床状态 | `clinical_status_enum` | `Integer` | 否 | 临床状态，见 §2.3.2 |
| 诊断分类 | `category_enum` | `Integer` | 否 | 分类，见 §4.1 |
| 严重程度 | `severity_enum` | `Integer` | 否 | 严重程度，见 §4.2 |
| 疾病/诊断编码 | `bus_no` | `String` | 否 | 疾病或诊断编码 |
| 身体部位JSON | `body_site_json` | `String` | 否 | 身体部位 JSON |
| 身体结构标识 | `body_structure_id` | `Long` | 否 | 身体结构ID |
| 就诊标识 | `encounter_id` | `Long` | 是 | 诊断事实归属的就诊ID；保存时必须与命令上下文一致，不允许跨就诊改写 |
| 患者标识 | `patient_id` | `Long` | 是 | 患者ID |
| 描述 | `description` | `String` | 否 | 疾病与诊断描述 |
| 初次发病时间 | `onset_datetime` | `Date` | 否 | 初次发病时间 |
| 初次发病年龄 | `onset_age` | `Integer` | 否 | 初次发病年龄 |
| 初次发病描述 | `onset_text` | `String` | 否 | 初次发病描述 |
| 症状减轻时间 | `abatement_datetime` | `Date` | 否 | 症状减轻时间 |
| 症状减轻年龄 | `abatement_age` | `Integer` | 否 | 症状减轻年龄 |
| 症状减轻时期 | `abatement_string` | `String` | 否 | 症状减轻时期 |
| 记录时间 | `recorded_datetime` | `Date` | 否 | 记录时间 |
| 记录人标识 | `recorder_id` | `Long` | 否 | 记录人 |
| 阶段总结 | `stage_summary_text` | `String` | 否 | 阶段总结 |
| 证明 | `evidence` | `String` | 否 | 证明 |
| 医保编码 | `yb_no` | `String` | 否 | 医保编码 |
| 诊断定义标识 | `definition_id` | `Long` | 否 | 诊断定义ID |
| 中医标识 | `tcm_flag` | `Boolean` | 否 | 中医标识，`true`=中医诊断 |

---

## 4. 值对象

### 4.1 诊断分类（`ConditionCategory` -> `categoryEnum`）

| value | code | info | 语义说明 |
|---|---|---|---|
| 1 | `problem-list-item` | 问题列表 | 长期问题清单视角 |
| 2 | `encounter-diagnosis` | 就诊诊断 | 就诊过程中的诊断条目 |

### 4.2 严重程度（`ConditionSeverity` -> `severityEnum`）

| value | code | info | 语义说明 |
|---|---|---|---|
| 1 | `severe` | 严重的 | 病情严重 |
| 2 | `midgrade` | 中等的 | 病情中等 |
| 3 | `mild` | 温和的 | 病情较轻 |

### 4.3 验证状态（`ConditionVerificationStatus` -> `verificationStatusEnum`）

| value | code | info | 语义说明 |
|---|---|---|---|
| 1 | `UNCONFIRMED` | 未确诊 | 初步怀疑，待确认 |
| 2 | `PROVISIONAL` | 疑似 | 暂定诊断 |
| 3 | `DIFFERENTIAL` | 有差异 | 鉴别诊断阶段 |
| 4 | `CONFIRMED` | 已确诊 | 最终确认 |
| 5 | `REFUTED` | 已排除 | 排除该诊断 |
| 6 | `ENTERED_IN_ERROR` | 未知 | 录入错误（当前实现 `info` 为“未知”） |

### 4.4 临床状态（`ConditionClinicalStatus` -> `clinicalStatusEnum`）

| value | code | info | 语义说明 |
|---|---|---|---|
| 1 | `ACTIVE` | 活跃The disease is currently active. | 当前活跃 |
| 2 | `RECURRENCE` | 复发The disease has recurred after a period of remission. | 缓解后复发 |
| 3 | `RELAPSE` | 再发The disease has relapsed after a period of improvement. | 改善后再发 |
| 4 | `INACTIVE` | 不活跃The disease is currently inactive. | 当前不活跃 |
| 5 | `REMISSION` | 体验复发风险The disease is in remission. | 缓解期，仍有复发风险 |
| 6 | `RESOLVED` | 康复The disease has been resolved. | 已康复或已解决 |
| 7 | `UNKNOWN` | 位置The disease status is unknown. | 状态未知（当前实现 `info` 含“位置”文案） |

---

## 5. 领域事件

### 5.1 事件定义

| 事件名称 | 英文名称 | 触发时机 | 消费方 | 携带数据 |
|---|---|---|---|---|
| 就诊诊断已保存 | `EncounterDiagnosisSavedEvent` | 保存就诊诊断成功后 | ConditionQueryService 查询、就诊QueryService（设计预留） | `conditionId`、`patientId` |
| 就诊诊断已删除 | `EncounterDiagnosisDeletedEvent` | 删除诊断成功后 | ConditionQueryService 查询、就诊QueryService（设计预留） | `conditionId` |
| 入院诊断已创建 | `AdmissionDiagnosisCreatedEvent` | 住院申请/登记**新开**病情事实后 | 住院QueryService、质控 | `conditionId`、`definitionId`、`patientId` |

> 事件名强调就诊/入院协同语义；**聚合锚点仍为 `Condition`**，载荷以 `conditionId` 为主标识。  
> 触发：`saveDoctorDiagnosis` 且命令 `admissionDiagnosisFlow=true` 时，对**本次新增**（无 `id`）的诊断项在落库后发布；复用已有 `conditionId` 仅建就诊关联时不发本事件。

### 5.2 事件触发规则

| 当前状态 | 操作 | 目标状态 | 发布事件 |
|---|---|---|---|
| — | 保存医生诊断 | 已记录/已更新 | `EncounterDiagnosisSavedEvent`（设计预留） |
| 已记录/已更新 | 删除就诊诊断 | 已删除 | `EncounterDiagnosisDeletedEvent`（设计预留） |
| — | 新开入院诊断（`admissionDiagnosisFlow=true`） | 已记录 | `AdmissionDiagnosisCreatedEvent` |

---

## 6. 领域服务

### 6.1 写侧领域服务（ConditionDomainService）

#### 6.1.1 医生站保存西医诊断（`saveDoctorDiagnosis`）

- **业务意义**：按医生站提交内容批量落库诊断事实。
- **边界说明**：聚合关注事实写入；就诊关系维护由应用层协同。
- **入参**：西医诊断事实 DTO 列表（含患者/就诊上下文、诊断字段）；字段级与批量契约以实现/DTO 为准。
- **出参**：保存结果（成功条数、主键列表或等价返回体，以落库与交互契约为准）。
- **前置条件**：
  - 入参列表非空；
  - 同一患者/就诊上下文一致；
  - 每条西医诊断满足最小字段完整性。
- **状态变化**：`—|已记录 -> 已记录|已更新`。
- **聚合内影响**：批量写入 `cln_condition`，刷新 `recordedDatetime`、`recorderId`，并由应用层维护 `EncounterDiagnosis` 关系。
- **事件输出**：`EncounterDiagnosisSavedEvent`；当 `admissionDiagnosisFlow=true` 且项为新增时另发 `AdmissionDiagnosisCreatedEvent`。
- **失败语义**：批次参数非法或写入失败时返回业务异常。

#### 6.1.2 医生站保存中医诊断（`saveDoctorDiagnosisTcm`）

- **业务意义**：按中医诊疗口径保存诊断事实，覆盖中医场景增强字段。
- **边界说明**：仅负责中医诊断事实写入，不处理跨域编排。
- **入参**：中医诊断事实 DTO 列表（含 `tcmFlag` 等扩展字段）；字段级以实现/DTO 为准。
- **出参**：保存结果（同 6.1.1 口径）。
- **前置条件**：
  - 入参列表非空；
  - 同一患者/就诊上下文一致；
  - 每条中医诊断满足最小字段完整性与中医扩展字段约束。
- **状态变化**：`—|已记录 -> 已记录|已更新`。
- **聚合内影响**：批量写入 `cln_condition`，刷新 `recordedDatetime`、`recorderId`，并执行中医分支字段处理。
- **事件输出**：`EncounterDiagnosisSavedEvent`（设计预留）。
- **失败语义**：中医分支参数不合法或保存失败时返回业务异常。

#### 6.1.3 删除就诊诊断（`delEncounterDiagnosis`）

- **业务意义**：删除误录诊断，防止影响临床决策与统计。
- **边界说明**：只处理诊断记录删除，不直接清理外部业务数据。
- **入参**：待删除诊断记录标识 `conditionId`（及实现所需租户/操作者等上下文）。
- **出参**：— 或删除结果对象。
- **前置条件**：
  - `conditionId` 必填且记录存在；
  - 若已开医嘱或被下游引用，则禁止删除。
- **状态变化**：`已记录|已更新 -> 已删除`。
- **聚合内影响**：删除或标记删除诊断记录（按实现口径）。
- **事件输出**：`EncounterDiagnosisDeletedEvent`（设计预留）。
- **失败语义**：存在医嘱引用或记录不存在时返回业务异常。

### 6.2 读侧查询服务（ConditionQueryService）

#### 6.2.1 按ID集合查询诊断记录（`getConditionListById`）

- **业务意义**：批量回填患者诊断事实详情。
- **边界说明**：只读查询，不写库，不做跨域编排。
- **查询范围（读库，无写入）**：`cln_condition` 按 ID 集合查询。
- **过滤口径**：只返回有效记录。
- **组装规则**：聚合主表字段并返回诊断事实集合。
- **返回约束**：部分不存在时返回命中子集。

#### 6.2.2 查询就诊下诊断清单（`getEncounterDiagnosis`）

- **业务意义**：按就诊标识加载本次就诊关联的诊断事实清单（医生站展示、顺序敏感场景共用；历史上若存在 `getEncounterDiagnosisByEncounterId` 等价入口，与本方法合并为同一读契约）。
- **边界说明**：只读查询，不写库，不做跨域编排。
- **查询范围（读库，无写入）**：就诊关联诊断记录QueryService或等效关联查询；事实数据来自 `cln_condition` 及就诊—诊断关联表/投影，以落库与交互契约为准。
- **过滤口径**：按 `encounterId` 精确过滤；默认排除已删除记录。
- **组装规则**：返回 Condition 事实并按需追加目录展示字段（如名称）；**保留就诊侧约定的诊断顺序**（若关联表或QueryService维护顺序字段）。
- **返回约束**：无数据返回空集合。

#### 6.2.3 查询诊断定义检索列表（`getDiagnosisList`）

- **业务意义**：为诊断录入提供可选目录项候选。
- **边界说明**：只读查询，不写库，不做跨域编排；**目录数据主权在 `ConditionDefinition` 聚合**。若本方法仅作为与录入页共用的查询端口，领域上应对齐 `ConditionDefinitionQueryService`（或等价目录读端口），避免在 `Condition` 聚合内复制目录持久化语义。
- **查询范围（读库，无写入）**：`ConditionDefinition` 可用目录QueryService（或经应用层转调目录读端口的同一结果集）。
- **过滤口径**：按关键字和可用状态过滤。
- **组装规则**：返回候选项核心字段（编码、名称、分类等）。
- **返回约束**：无命中返回空集合。

#### 6.2.4 按患者查询诊断事实（`findByPatientId`）

- **业务意义**：扫描患者历史诊断事实，供医生站「病人历史」维度装配快捷树（**IF-DOR-06A** / **IF-IP-DOR-03**）。
- **边界说明**：只读查询，不写库；应用层**必须**经本方法访问，**禁止**直调 **`ConditionRepository`**（**B.2**）。
- **查询范围（读库，无写入）**：`cln_condition` 按 **`patient_id`** 过滤。
- **过滤口径**：默认排除已删除记录。
- **组装规则**：返回 **`Condition`** 实体列表（含 **`definitionId`** 等事实字段）；应用层由 **`PatientHistoryCommonDiagnosisAssembler`** 结合 **`EncounterQueryService#listActiveAssociationsByConditionIds`** 装配：西医按定义去重，中医按就诊内 **`tcm_syndrome_group`** 合并病+证。
- **返回约束**：无数据返回空集合。

#### 6.2.5 统计诊断下医嘱引用数（`countAdviceRefByConditionId`）

- **业务意义**：删除诊断前 **BR-CND-003** 门控：统计该诊断事实是否已被医嘱引用。
- **边界说明**：只读计数，不写库；应用层**必须**经本方法访问，**禁止**直调 **`ConditionRepository`**（**B.2**）。
- **查询范围（读库，无写入）**：跨医嘱事实表统计（实现以仓储 **`ConditionRepository#countAdviceRefByConditionId`** 为准）。
- **过滤口径**：按 **`conditionId`** 精确计数。
- **返回约束**：**`>0`** 时应用层抛 **`CONDITION_RULE_CONFLICT`**；**当前 `zxplat` 为 default 占位恒返回 0**（见仓储 JavaDoc `DOC-CONFLICT`），实计补全前删除门控可能失效。

### 6.3 领域边界说明

- 本聚合负责"患者诊断事实"生命周期，不负责诊断目录维护（目录由 `ConditionDefinition` 聚合承担）；
- 诊断保存、删除等核心规则在领域层定义；医生站批量处理和页面装配属于应用层编排；
- 与医嘱、就诊关联的跨聚合一致性通过应用服务协同，不在本聚合内直接更新下游聚合。

### 6.4 仓储接口补充（`ConditionRepository`）

> 本节描述**读删门控**用到的仓储契约；**应用层须经 `ConditionQueryService#findByPatientId` / `#countAdviceRefByConditionId` 调用**（**B.2**），不得在应用层注入本仓储。

| 方法 | 业务意义 | 说明 |
|---|---|---|
| `findByPatientId(Long patientId)` | 病人历史诊断事实扫描 | 由 **`ConditionQueryService#findByPatientId`** 封装；**IF-DOR-06A** 病人历史分支在应用层对 `definitionId` 去重后，通常再转调 `ConditionDefinitionQueryService#listActiveDoctorStationRowsByIds` 装配展示（见 `DM-CLN-002` §6.2.7）。 |
| `countAdviceRefByConditionId(Long conditionId)` | 删除前医嘱引用计数 | **BR-CND-003** 门控：由 **`ConditionQueryService#countAdviceRefByConditionId`** 封装；**>0** 时禁止删除。工程实现须跨医嘱事实表统计；**当前 `zxplat` 为 default 占位恒返回 0**（见仓储 JavaDoc `DOC-CONFLICT`），与 `AS-OP-DOR-002-SDD` §1.2.1 口径一致——实计补全前删除门控可能失效。 |

---

## 7. 不变式

| 编号 | 不变式 | 说明 |
|---|---|---|
| BR-CND-001 | 诊断事实必须归属患者 | `patientId` 是事实归属核心字段 |
| BR-CND-002 | 保存诊断时必须刷新记录信息 | `saveDoctorDiagnosis` / `saveDoctorDiagnosisTcm` 强制写入记录时间和记录人 |
| BR-CND-003 | 删除诊断前必须检查医嘱引用 | `delEncounterDiagnosis` 前须取得该诊断下已开立医嘱条数并判定为 0；应用层经 **`ConditionQueryService#countAdviceRefByConditionId`**（**B.2** 禁直调 Repository）实现，**方法名以实现为准** |
| BR-CND-004 | 诊断定义与诊断事实分离 | `definitionId` 仅做引用，不复制目录全量数据 |
| BR-CND-005 | 诊断事实必须归属就诊 | `encounterId` 为事实归属字段，写侧保存时与命令就诊上下文强一致 |

---

## 8. 上下文映射

### 8.1 上游依赖

| 上游上下文 | 聚合 | 关联方式 | 说明 |
|---|---|---|---|
| 管理域（ADM） | `Patient` | `患者标识(PatientId)` 引用 | 诊断事实归属患者 |
| 临床目录（CLN） | `ConditionDefinition` | `诊断定义标识(DefinitionId)` 引用 | 复用诊断目录定义 |
| 就诊域（ADM/CLN） | Encounter | `就诊标识(EncounterId)` 引用 | 绑定就诊上下文 |

### 8.2 下游影响

| 下游上下文 | 聚合 | 触发方式 | 说明 |
|---|---|---|---|
| 病历QueryService | EMR 诊断视图 | 事件/查询 | 展示诊断事实与状态 |
| 就诊QueryService | EncounterDiagnosis | 应用层协同 | 维护就诊诊断关联 |
| 临床质控 | 质控规则引擎 | 事件/查询 | 消费诊断变更用于规则判断 |

### 8.3 引用规则

- 诊断事实以 `患者标识(PatientId)` 为主锚点，引用目录时使用 `诊断定义标识(DefinitionId)`；
- 引用目录时遵循"目录可用性校验"，已停用目录不应用于新增诊断；
- 删除/变更诊断前须检查医嘱等下游引用，避免悬挂业务数据；
- 跨聚合协同只通过标识引用与事件通知，不在本聚合内直接落外部聚合写入。

### 8.4 事件流示意图

```text
Patient/ConditionDefinition/Encounter
        └──(ID 引用)──> Condition
                              ├── EncounterDiagnosisSavedEvent    ──> EMR / EncounterDiagnosis / 质控
                              ├── EncounterDiagnosisDeletedEvent  ──> EMR / EncounterDiagnosis / 质控
                              └── AdmissionDiagnosisCreatedEvent  ──> 住院QueryService / 质控
```

---

## 9. 补充

### 9.1 变更历史

| 版本 | 日期 | 摘要 |
|---|---|---|
| v1.3.1 | 2026-05-28 | **M1**：§6.1/§6.2 与 `ConditionDomainService`/`ConditionQueryService` 白名单对齐（3写+5读） |
| v1.3 | 2026-05-21 | **`ConditionQueryService`** 增补 **`findByPatientId`**、**`countAdviceRefByConditionId`**；应用层 **B.2** 禁直调 **`ConditionRepository`**；对齐 **AS-OP-DOR-002-SDD** / **AS-IP-DOR-002-SDD** |
| v1.2 | 2026-05-18 | `saveDoctorDiagnosis` 支持 `admissionDiagnosisFlow`；实现 `AdmissionDiagnosisCreatedEvent`（住院申请新开病情）；对齐 `DM-ADM-002` v1.8 / `AS-OP-DOR-005` |
| v1.1 | 2026-05-02 | 新增 §6.4 `ConditionRepository`（`findByPatientId`、`countAdviceRefByConditionId`）与 BR-CND-003 落地说明；对齐门诊医生站诊断录入 AS/SDD |
| v1.0 | 2026-05-02 | 初版 |

<!-- MODE-B-METHOD-INVENTORY 2026-09-23 -->

## 附录 · 模式 B 已实现方法清单（2026-09-23）

> 自 zxplat-domain 对应 *DomainService / *QueryService 抽取 public 方法名，用于关闭方法白名单 DOC-DRIFT。细规仍以正文 §6 与源码为准；本表不替代九章成稿。

### DomainService：`ConditionDomainService`

| 方法 |
|------|
| `saveDoctorDiagnosis` |
| `saveDoctorDiagnosisTcm` |
| `delEncounterDiagnosis` |

### QueryService：`ConditionQueryService`

| 方法 |
|------|
| `getConditionListById` |
| `getEncounterDiagnosis` |
| `findByPatientId` |
| `findDefinitionIdByConditionId` |
| `countAdviceRefByConditionId` |

