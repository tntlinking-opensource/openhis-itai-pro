# 诊断绑定改造：一药多诊断 & Condition 退役

> **文档性质**：改造基线（按本文落地 = 对话定稿效果）  
> **定稿日期**：2026-07-18  
> **适用范围**：门诊医生站诊断录入 / 医嘱开立 / 申请单 / 分方 / 挂费 / 门诊医保结算；住院诊断与开嘱若绑诊断则同构  
> **历史数据**：本期**不管**（不做迁移、不做双读兼容）  
> **进度入口**：见 **§11.0 当前进度总览**（已完成 / 下一步优先顺序）

---

## 1. 改造要解决什么

### 1.1 原设计问题

| 原状 | 问题 |
|------|------|
| 一条药品/诊疗/耗材请求只绑 **一个** 诊断 | 临床上一药/一疗可对应多个病，单绑是设计失误 |
| `condition_definition_id` + `encounter_diagnosis_id` 成对落在 req | 两维冗余；且单值无法表达多诊断 |
| `cln_condition` 在诊断保存时写入 | **只当跳板**（为满足 `encounter_diagnosis.condition_id`），未按「病情事实表」本意使用 |
| 结算经 `ChargeItem.encounter_diagnosis_id` → Condition → Definition 取 `dise_codg` | Condition 无独立业务价值；`adm_encounter_diagnosis.diag_code` / `condition_code` 列实际未存（常 NULL） |
| 分方分区键主要是 `medType + 轨道 + 库房` | 未把「诊断集合全等」做成硬分区 |

### 1.2 目标效果（验收口径）

1. 开嘱时从**本次就诊诊断**中**多选**，一药/一疗可绑多个诊断。  
2. 同一请求行内：诊断医疗类别族不可混——**要么全普通，要么全慢病**（由所选 `adm_encounter_diagnosis.med_type` 判定）。  
3. 分方：两行的诊断集合**有交集即可同方**（不必全等）；方头诊断取同方各药绑定诊断**并集**；`med_type` 决定方种类（普通方 / 慢病方等）。  
4. 结算：可用**多个**诊断（2203 `diseinfo` 全量上传绑定 ids）；ids 串首位仅统计/`maindiag_flag`/分组代表；`dise_codg` 目录码不经 Condition。  
5. `cln_condition`：**停写、停查、不关联**；表可保留但业务上孤立（允许永远为空）。  
6. 存多值 ID 用 **String + 逗号分隔**；比较时解析后比集合，禁止原始串直接 `equals`。  
7. **`encounter_diagnosis_ids` 有序：第 1 个必须是就诊主诊断**。  
8. 某条就诊诊断一旦被医嘱/账单引用：**不可改诊断内容、不可删**（产品硬规则，系统落校验）。系统中无改 `med_type` 的入口。

---

## 2. 最终数据模型

### 2.1 目标关系（唯一业务链）

```text
cln_condition_definition          （诊断目录：name / condition_code / yb_no …）
         ↑ condition_definition_id
adm_encounter_diagnosis           （本次就诊诊断行：med_type / primary_flag / …）
         ↑ encounter_diagnosis_ids（多值，有序 ids 串）
med_*_request / wkf_*_request
adm_charge_item                   （挂费时从请求拷贝同口径 ids）
```

### 2.2 `cln_condition`（退役，非删表）

| 项 | 约定 |
|----|------|
| 表 | **保留**，可不物理删除 |
| 写 | **禁止**再插入/更新业务数据 |
| 读 | **禁止**业务查询依赖 |
| 关联 | 其他表 **删除** 对 Condition 的外键/字段引用（见 §3） |
| 语义 | 与现行系统**无任何关系**；允许永久空表 |

> 说明：这不是「风险待观察」，而是**本期必做**——把 Condition 从链路中孤立出去。

### 2.3 `adm_encounter_diagnosis`（就诊诊断 = 开嘱可选锚点）

| 字段 | 处理 |
|------|------|
| `condition_definition_id` | **新增（或启用）**：直挂 `cln_condition_definition.id` |
| `condition_id` | **删除**（从本表及其他表一并去掉对 Condition 的关联） |
| `med_type` | **保留**：是否慢病 / 医疗类别的判定源（开嘱前查询本表） |
| `primary_flag` | **保留**：就诊级是否主诊断；与「请求上第 1 个 id 必须是主诊断」配合 |
| `diag_code` / `condition_code` | 可留列但**不作为** `dise_codg` 来源（现网常空）；码以目录为准 |

**开嘱选诊断**：只能从**本次就诊**的 `adm_encounter_diagnosis` 中选（与现交互一致，改为多选）。

### 2.4 请求表（药品 / 诊疗 / 耗材）

涉及表（至少）：

- `med_medication_request`
- `wkf_service_request`
- `wkf_device_request`

| 字段 | 处理 |
|------|------|
| `encounter_diagnosis_ids` | **新增**：`varchar`/`text`，逗号分隔的就诊诊断主键字符串 |
| `encounter_diagnosis_id` | **删除**（单值列退役） |
| `condition_definition_id` | **删除**（改由就诊诊断行上的 definition 表达；请求不再冗余目录 id） |

中药等同源请求若有同样两列，一并按上表处理。

### 2.5 账单表 `adm_charge_item`

| 字段 | 处理 |
|------|------|
| `encounter_diagnosis_ids` | **新增**：与请求同协议；**挂费时从上游请求拷贝** |
| `encounter_diagnosis_id` | **删除** |
| `condition_id`（若存在） | **删除**（切断 Condition） |
| 是否再存 `condition_definition_ids` / `med_type` | **不必**。结算用账单上的 `encounter_diagnosis_ids` 回查就诊诊断行 → `med_type` / definition → `dise_codg`，保持「结算不查请求」 |

非医嘱费用（如挂号费）：无诊断绑定时 `encounter_diagnosis_ids` 可空，`med_type` 用既有固定规则（如挂号 12、缺省普通 11），**不强制**带 ids。

### 2.6 字段语义澄清（避免再混）

| 概念 | 归属 | 说明 |
|------|------|------|
| 是否主诊断 | **就诊级** `adm_encounter_diagnosis.primary_flag` | 与「请求绑了哪个诊断」不是同一概念 |
| `encounter_diagnosis_ids` | **请求/账单级** | 本行绑定的就诊诊断行集合；**第 1 个 = 本次就诊主诊断行 id** |
| `med_type` | **就诊诊断行** | 普通/慢病等；开嘱校验与分方种类均认它 |
| `dise_codg` | **医保接口病种编码** | 来自 `cln_condition_definition.yb_no`（优先）或 `condition_code`，**不经** Condition，也**不依赖** encounter 上空的 diag/condition_code 列 |

---

## 3. Condition 孤立：必须删除的关联（清单级）

以下为改造时必须扫净的方向（实现以全库检索为准，本文不得漏意图）：

1. **表字段**：凡 `condition_id` 指向 `cln_condition` 的列（含 `adm_encounter_diagnosis`、`adm_charge_item` 等）——删除或停用并改代码。  
2. **诊断保存**：门诊/住院/入院/中医等路径——**不再**调用创建/更新 Condition；只维护 `adm_encounter_diagnosis`（写 `condition_definition_id`、`med_type` 等）。  
3. **读路径**：列表组装、医保分组、病历自动填、常用诊断、开嘱回填等——凡 `encounter_dx → condition → definition` 改为 `encounter_dx → condition_definition_id → definition`。  
4. **领域命令/DTO/前端**：去掉 Condition 主键作为业务必填的地方；API 以 `encounterDiagnosisId` / `conditionDefinitionId`（就诊诊断行上）为准。  
5. **禁止新增**对 `cln_condition` 的业务依赖（表可留作废弃壳）。

---

## 4. `encounter_diagnosis_ids` 存储与比较协议

### 4.1 存储

- 类型：**String**（库列字符串类型），**不是** Long，**不是** 数组类型列。  
- 格式：逗号分隔，例如 `"10001,10002,10003"`。  
- **有序**：  
  - **第 1 个 id = 本次就诊主诊断**对应的 `adm_encounter_diagnosis.id`（便于统计、展示、`maindiag_flag`）。  
  - 其后为其余选中诊断（顺序可按选择顺序；但不得把非主诊放到首位）。  
  - **结算上传认集合全量**（2203 多行），不以「只取第一位」截断。  
- 前端 / API：**全程当字符串**传递与持有；**禁止** `Number(id)` / 解析后再拼导致 Long 精度丢失。

### 4.2 写入校验（请求保存时）

1. 非空时：每个 id 必须属于**当前就诊**下未软删的 `adm_encounter_diagnosis`。  
2. **同族校验**：所有行的 `med_type` 必须同属一族：  
   - **普通族**与**慢病族**不可混在同一请求行（与开场约束一致）。  
   - 族划分以实现时 `YbMedType` 约定为准（普通如 `11`；慢病/慢特病如 `14` / `140104` / `140201` 等——实现前与产品对一下码表集合，写进常量）。  
3. **主诊断首位**：`ids[0]` 必须对应 `primary_flag = true` 的就诊诊断行；且集合中**必须包含**主诊断。  
4. 解析方式：`split(',')` → 每段 `trim` → 去空；写入前可再规范化空白，但**保留「主诊在首位」的顺序约定**（其余位是否排序由实现定，建议其余位稳定排序以免无意义抖动）。

### 4.3 比较（分方「有交集可同方」）

- **禁止**对原始 ids 串 字符串直接 `equals`。  
- **同方判定**：解析 → trim → 去重 → 两集**有交集**即可同方（`EncounterDiagnosisIdsSupport#compatibleForSamePrescription`）；双侧皆空亦相容；一侧空一侧非空不相容。  
- **方头展示**：同方各药 ids **并集**（有序去重后拼展示名），不是取单行、也不是要求集合全等。  
- canonical `diagnosisSetKey`（排序后 join）仍可用于调试/规范化，**不再**作硬分区 equals。

---

## 5. 开嘱 / 申请单行为

### 5.1 药品 / 诊疗 / 耗材草稿

- UI：诊断由单选改为**多选**（数据源 = 本次就诊诊断列表）。  
- 提交 payload：`encounterDiagnosisIds: string`（有序 ids 串）或 `string[]`（服务端再规范化成 有序 ids 串）；**不再**传成对的单值 `conditionDefinitionId` + `encounterDiagnosisId` 作为绑定主数据。  
- 写库：只落 `encounter_diagnosis_ids`。  
- 回显：用 ids 反查就诊诊断列表勾选；名称等从 definition 解析。

### 5.2 诊疗申请单（整单诊断）

- 原整单单值 `conditionDefinitionId` / `encounterDiagnosisId` 改为整单 **`encounterDiagnosisIds`**（同样多值 + 主诊第一 + 同族校验）。  
- 写入该单下全部 `ServiceRequest` 的 `encounter_diagnosis_ids`。

### 5.3 皮试伴随诊疗等继承

- 原「继承同行 `encounterDiagnosisId` / `conditionDefinitionId`」改为继承同行 **`encounter_diagnosis_ids` 整串**（或等价数组）。

### 5.4 住院

- 若住院开立**本期仍不绑诊断**：ids 可空，行为与现「不传诊断」一致。  
- 若住院诊断录入改造：与门诊同样——只写 encounter_diagnosis + `condition_definition_id`，不写 Condition。

---

## 6. 分方规则（药品）

### 6.1 分区键（目标）

在现有 `medType + 分方轨道 + pharmacyLocationId` 基础上，诊断**不再**进硬分区键；同分区内装箱时用诊断相容判定：

```text
硬分区：medType | splitTrack | pharmacyLocationId
同方诊断：两侧绑定集有交集（或双侧皆空）→ 可同方；一侧空一侧非空 → 不同方
方头展示：同 prescription_no 下各药 encounter_diagnosis_ids 有序并集
```

| 维度 | 规则 |
|------|------|
| 诊断相容 | 两行（或桶内并集 vs 待装包）诊断 id 集**有交集即可同方**；多一个 / 少一个只要仍有共用 id 仍可同方；互斥（无交集）才拆 |
| `medType` | 由绑定诊断行解析（同族已保证一行一个种类）；区分普通处方 / 慢病处方等；仍入硬分区 |
| 轨道 / 库房 / 成组 / 金额等 | 保持现有 BR-MRQ 规则 |

### 6.2 装配

- `PrescriptionSplitLineContext` 增加诊断集键（或 ids 原文 + 规划器内规范化）。  
- **不再**通过单值 `encounter_diagnosis_id` → 仅反查 `med_type`；改为：由 `encounter_diagnosis_ids` 加载行，取 `med_type`（同族任取/取主诊行均可，结果应一致）。

### 6.3 与「主诊第一」的关系

- 分方认**有交集**，不认「谁在 ids 串第一位」作为分方差异。  
- 主诊第一只约束存储与结算代表语义。  
- 方头诊断展示为同方各药绑定诊断的**并集**（读侧按 `prescription_no` 聚合），不是单行 ids。

---

## 7. 挂费与结算

### 7.1 挂费

- 创建 `ChargeItem` 时：从对应 Request **拷贝** `encounter_diagnosis_ids`。  
- 不再写 `encounter_diagnosis_id` / `condition_id`。  
- 医生站现网挂费装配若原本未写诊断列：改造后**必须写入** ids，避免结算缺诊断。

### 7.2 结算读法（保持「不查请求」）

```text
ChargeItem.encounter_diagnosis_ids
  → 解析多个 adm_encounter_diagnosis（有序并集；本组账单 ids 串）
  → 2203 diseinfo：按序上传多行（全部绑定诊断）
  → med_type（同族；种类分组）
  → 每行 condition_definition_id → definition.yb_no / condition_code → 诊断码
```

| 项 | 约定 |
|----|------|
| 多诊断 | **必须支持上传多个**：2203 `diseinfo[]` 带本组账单绑定的全部诊断 |
| ids 串首位 | **仅**用于 `maindiag_flag=1`、展示/统计与分组代表码；**不是**「结算只结算主诊」 |
| 2204 `dise_codg` | 平台费用明细为单值字段：填组代表码（慢病组分码或 ids 串首位对应码）；不替代 2203 多诊上传 |
| Condition | **不参与** |
| 是否回查 Request | **不需要**（账单自带 ids） |

### 7.3 医保分组影响

- 分组键：`accountId + medType`；慢病附加代表 `diseCodg`（取 ids 串 **首位**对应码，便于分组维度稳定）。  
- 2203：组内按账单 `encounter_diagnosis_ids` 有序并集上传**多行** `diseinfo`。  
- 2204：费用行 `dise_codg` 仍为单值（接口限制），用组代表码；临床多诊以 2203 为准。

---

## 8. 产品硬规则（门禁）

### 8.1 引用后不可改、不可删

- **改**：指改诊断内容（换 definition、改关键临床含义等）；**不是**指改主诊标记以外的无关展示。  
- **删**：软删/移除就诊诊断行。  
- **触发**：该 `adm_encounter_diagnosis.id` 已被任一请求或账单的 `encounter_diagnosis_ids` 引用。  
- **实现**：诊断更新/删除前反查引用；命中则业务拒绝。  
- **`med_type`**：系统无入口可改；不必为「结算跟活数据」再做快照列（产品已关闭该口子）。

### 8.2 同族不可混

- 与 §4.2 一致；普通 + 慢病不得出现在同一请求行的 ids 中。

### 8.3 主诊断必须在首位且必选

- 有绑定诊断时：集合含主诊，且 ids 串第一位为主诊 id。  
- **用途**：统计、列表展示、`maindiag_flag`、分组代表码；**不**表示结算只传主诊。

---

## 9. 明确不做 / 不在本期

| 项 | 说明 |
|----|------|
| 历史数据迁移 | **不管** |
| 物理删除 `cln_condition` 表 | 不删表，只孤立 |
| 请求/账单再存 `condition_definition_ids` | 不需要（经 encounter 行可达） |
| 请求上快照 `med_type` 列 | 非必须（无改 med_type 入口 + 引用后禁改删） |
| 最小改动备选（仅有序 ids 串 definition + 保留 Condition） | **已否决**；以本文「就诊诊断中心 + Condition 退役」为准 |

---

## 10. 与早期讨论方案的对照（防返工）

| 曾讨论方案 | 结论 |
|------------|------|
| 仅 `condition_definition_id` 改 有序 ids 串，保留单值 `encounter_diagnosis_id` | 否决为终态；可理解但不如终态干净 |
| 请求存 `condition_definition_ids` + 快照 `med_type`，废弃 encounter | 否决为终态；分方/开嘱改为认就诊诊断行 ids |
| 账单完全不存诊断 | 否决；账单存 `encounter_diagnosis_ids`，结算不查请求 |
| 完全删除 Condition 表 | 本期不删表，但**等价于业务删除**（停写停查断关联） |
| `dise_codg` 必须从 encounter 的 diag/condition_code 列取 | 错误；从 **definition** 取 |
| `encounter_diagnosis_id` 表示主诊 | 错误；主诊是就诊 `primary_flag`；请求 ids **第一位存放**主诊行 id |

---

## 11. 改造工作分解（按域）与落地状态

> **状态图例**：✅ 已完成 · 🔲 进行中/未做 · ⏸ 依赖前序  
> **更新**：2026-07-18（后端多诊绑定写校验 / 分方 diagnosisSetKey / 挂费拷贝 / 引用门禁已落地；**前端医嘱/申请单诊断多选已落地**；表字段基线此前已完成；**不新建 Flyway**）

### 11.0 当前进度总览（下一步从这里看）

| 顺序 | 事项 | 状态 | 说明 |
|------|------|------|------|
| — | 库表基线 DDL + Condition 停写停查 + 诊断录入改 Encounter | ✅ | 见 §11.1、§11.2；**不必再改表设计** |
| **①** | **前端：医嘱/申请单诊断多选 + `encounterDiagnosisIds`** | ✅ | 多选、主诊第一、string ID、回填 |
| **②** | **后端：开嘱写入校验（同族 + 主诊首位 + 属本就诊）** | ✅ | `EncounterQueryService#assertBindableEncounterDiagnosisIds`；Med/Svc/Device DS 落库前调用；族常量 `EncounterDiagnosisBindingConstants` |
| **③** | 分方诊断有交集可同方 | ✅ | 硬分区 `medType\|track\|loc`；装箱 `EncounterDiagnosisIdsSupport#compatibleForSamePrescription`；方头并集展示 |
| **④** | 挂费拷贝 ids + 结算多诊上传 | ✅ | 挂费拷贝已落地；2203 `diseinfo` 按账单 ids 串 有序并集多行上传；首位仅 maindiag/分组代表 |
| **⑤** | 引用后门禁（禁改内容、禁删） | ✅ | `EncounterDiagnosisReferenceSupport`；门诊/住院西医 + 中医病证组（同源门禁）；**门诊**中药写 `encounterDiagnosisIds`；**住院开立不绑诊断**（中西医 ids 可空） |
| **⑥** | 清 Request 旧 `conditionId` 兼容回退；AS-SDD 医嘱/收费细扫 | ✅ | 收尾完成；**AS/SDD 医嘱开立、收费** 已对齐 `encounterDiagnosisIds` |

**本地库**：若库按旧脚本建成，须按新基线脚本对齐列（重建或手工改列）；**不是**再出一张设计表。

### 11.1 库表 / 迁移

1. ✅ `adm_encounter_diagnosis`：`condition_definition_id`；删除 `condition_id`。  
2. ✅ 三 req：`encounter_diagnosis_ids`；删除 `encounter_diagnosis_id`、`condition_definition_id`。  
3. ✅ `adm_charge_item`：`encounter_diagnosis_ids`；删除旧诊断列。  
4. ✅ 业务侧切断 Condition 关联（类 `@Deprecated`）。  
5. ✅ 历史数据：不迁。

### 11.2 诊断录入（门诊 / 住院 / 入院 / 中医）

1. ✅ 保存只写/更 `adm_encounter_diagnosis`（含 `condition_definition_id`、`med_type`、`primary_flag`…）。  
2. ✅ 去掉 Condition 领域写调用。  
3. ✅ 查询列表改为 definition 直连。  
4. ✅ 引用门禁：被 ids 引用则禁改内容、禁删。

### 11.3 医嘱开立 / 申请单 / 皮试

1. ✅ Payload / 领域落库字段改为 `encounter_diagnosis_ids`（后端契约已齐）。  
2. ✅ 同族 + 主诊首位校验（写路径硬校验）。  
3. ✅ 前端多选 + Long 安全（string）。  
4. ✅ 列表回显按 ids（前端为主；后端列表已回 `encounterDiagnosisIds`）。

### 11.4 分方

1. ✅ 硬分区不含诊断集；同分区装箱按诊断**有交集**可同方。  
2. ✅ 方头诊断 = 同 `prescription_no` 绑定诊断并集（药房查询 / 门诊·住院发药 slip / 处方打印 / 前端笺面与报表，读侧强制覆盖行级单诊）。  
2. ✅ `medType` 从 ids 对应行解析（装配侧 有序 ids 串→medType + setKey）。  
3. ✅ 集合比较规范化（禁止裸 ids 串 `equals`）。

### 11.5 挂费 / 收费 / 医保

1. ✅ 挂费创建时拷贝 ids（按请求锚点回查；命令显式传入优先）。  
2. ✅ 待收费 / 预结算：2203 按账单 `encounter_diagnosis_ids` 多诊上传；2204/分组代表码取首位或慢病组码。  
3. ✅ 删除对 Condition、单值 `encounter_diagnosis_id` 的结算依赖。

### 11.6 前端

1. ✅ 诊断选择组件多选；提交 ids 串（`encounterDiagnosisIds`）。  
2. ✅ 主诊默认勾选且固定为序列第一。  
3. ✅ ID 全部 string（禁止 Number）。  
4. ✅ 申请单、医嘱编辑回填对齐（列表 `encounterDiagnosisIds`）。

### 11.7 文档同步

- ✅ DD 本文、DM-CLN-003 退役、DM-ADM-002/011、DM-MED-003、DM-WKF-003/004、AS-OP-DOR-002 要点已回更。  
- ✅ AS/SDD 医嘱开立（OP/IP）、收费（OP-FIN-002）细章节与前端类型已对齐 **`encounterDiagnosisIds`**（2026-07-18）。

---

## 12. 验收清单（按此勾选 = 聊完想要的效果）

- [x] 业务代码不再写/读 `cln_condition`；相关 `condition_id` 已从他表删除。（后端 2026-07-18 已落地：DDL + Encounter/Req/Charge + AppService/CHS/EMR/WI/院点；Condition* 仅 `@Deprecated` 空壳）  
- [x] `adm_encounter_diagnosis` 存 `condition_definition_id`；三 req + 账单存 `encounter_diagnosis_ids`。  
- [x] 一药/一疗可绑多个本次就诊诊断。（前端多选 + 后端写校验）  
- [x] 普通与慢病诊断不能绑在同一请求行。（后端 `ENC_DX_BIND_002`）  
- [x] `encounter_diagnosis_ids` 写入时第一位是主诊断 id；比较做解析集合比较。（写校验 + 分方 setKey）  
- [x] 分方：诊断集合有交集 → 可同方；无交集 → 不同方；`med_type` 区分方种类；方头展示并集。  
- [x] 挂费账单带同口径 `encounter_diagnosis_ids`；结算不查请求、不查 Condition。  
- [x] `dise_codg`/诊断码来自 `cln_condition_definition`；结算 **2203 上传多个诊断**；ids 串首位仅 maindiag/统计/分组代表（非「只结算主诊」）。  
- [x] 就诊诊断被引用后不可改内容、不可删。  
- [x] 无历史迁移要求。  

---

## 13. 一句话总纲

**就诊诊断行是唯一绑定锚点（挂目录 definition + med_type）；请求与账单只存有序多值 `encounter_diagnosis_ids`（主诊第一便于统计）；Condition 整网孤立；分方认诊断集合有交集可同方 + med_type 种类，方头诊断取并集；结算认账单 ids，2203 多诊全量上传，2204/分组单值代表取首位或慢病组码。**
