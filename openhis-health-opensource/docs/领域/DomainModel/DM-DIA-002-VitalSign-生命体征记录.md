# DM-DIA-002 VitalSign 生命体征记录

> **限界上下文**：DIA域 · 生命体征与护理测量事实  
> **聚合类型**：实体聚合（主表 + 明细 EAV）  
> **版本**：v2.1.0 · **日期**：2026-06-20

---

## 1. 概述

### 1.1 业务定义

生命体征记录（VitalSign）是患者侧**测量与护理观察事实**聚合，采用 **主表 + 明细 code/value** 模型：

- **主表** `dia_vital_sign`：一次录入单据（体温单或护理记录），承载归属、时点、单号及公共头信息；
- **明细表** `dia_vital_sign_detail`：该单据下各观测项的 **`term_code` + `item_code` + `item_value`**（通用字典项行模型）。

单据类型由固定枚举 **`type_enum`** 区分（`TEMPERATURE_SHEET` 体温单、`NURSING_RECORD` 护理记录）。**同一就诊、同一 `recorded_at` 允许多张主表**，不做时点唯一约束。

明细项 **`item_code`** 须在其 **`term_code`** 所指的术语体系下存在对应字典项；体征类观测默认 **`term_code = vital_signs_code`**（`ter_code_system.name`）。页面上可填写的观测项均须有对应字典项，院方可自行扩展；未来特殊事件等可引入独立 `term_code`（如 `temperature_sheet_event_code`），**不增表**。

**体温**与其它观测项一样写入明细：`term_code=vital_signs_code`、`item_code=TEMP`（Flyway **V97** 自主表 `temperature` 迁移而来）。

能力边界对齐 openhis「三测单/体温单 + 护理记录体征」的**业务结果**；读写契约、事务与删除策略以 ZXPlat 领域层命令 + 逻辑删除为准。

### 1.2 核心职责

| 职责 | 说明 |
|---|---|
| 单据落库 | 按就诊 + 记录时点 + `type_enum` 保存主表及明细 |
| 主表级覆盖写 | 按主表 `id` 以提交明细集覆盖该单下全部有效明细行 |
| 体温单高性能读 | 体温曲线/按日体温点读明细 `term_code=vital_signs_code` 且 `item_code=TEMP`，经主表 join |
| 事实生命周期 | 主表逻辑删除级联软删明细；禁止物理 DELETE |
| 读侧供给 | 按就诊、患者、日期范围、类型查询，供护士站体温单与护理记录 |

### 1.3 适用范围

| 场景 | 说明 |
|---|---|
| 护士站体温单 | `type_enum=TEMPERATURE_SHEET`；曲线读明细 TEMP |
| 护士站护理记录 | `type_enum=NURSING_RECORD`；应用服务 **AS-IP-NUR-014**；UI 字段映射见 §9.1.3 |
| 入院体征 | 应用层编排，类型按场景写入 |
| EMR 元数据 | 最新体温取明细 `TEMP`；其余项取明细最新 |

### 1.4 与 openhis `doc_vital_signs` / `doc_emr` 的差异

| 维度 | openhis | ZXPlat（本聚合） |
|---|---|---|
| 结构 | 扁平行 EAV 或 EMR `contextJson` | 主表 + 明细 EAV |
| 体温单/护理记录 | 分散在体征表与 EMR | 统一聚合，`type_enum` 区分 |
| 体征 code | DTO 硬编码 | 术语 `vital_signs_code`（明细 `term_code` + `item_code`） |
| 记录时间 | `recording_date` + `time_point` | `recorded_at`（`LocalDateTime`） |
| 单号 | 无 | `bus_no` 租户内唯一 |
| 体温 | 与别的 code 同行存储 | 明细 `term_code=vital_signs_code`、`item_code=TEMP` |
| 删除 | 物理 DELETE | 逻辑删除 + 明细级联 |

### 1.5 与 FHIR Observation 的关系

| 维度 | 说明 |
|---|---|
| FHIR 对齐 | 主表 + 每行明细可视为 `Observation` 的本地投影 |
| 本地扩展 | `type_enum`、`bus_no`、`baby_no`、`location_id`（病床） |
| 编码主权 | 明细 `term_code` + `item_code` 引用术语；默认 `term_code=vital_signs_code` |

---

## 2. 聚合根与持久化形态

### 2.1 持久化形态

**持久化形态**：主表 + 明细表（1 : N）。

```
VitalSign（聚合根，主表）
    │
    ├── VitalSignDetail（明细，EAV 行）
    ├── VitalSignDetail
    └── …
```

- 主表 `dia_vital_sign`：一行 = **一张单据**（一次录入）。
- 明细 `dia_vital_sign_detail`：一行 = 该单据下一个 **`term_code` + `item_code`** 测量/观察值。

**业务自然键（有效行）**：

| 层级 | 自然键 |
|---|---|
| 主表 | `tenant_id` + `bus_no` |
| 明细 | `tenant_id` + `vital_sign_id` + `term_code` + `item_code` |

**不约束** `encounter_id + recorded_at` 唯一；同一时点可有多张主表（不同 `bus_no` 或同类型多条）。

**高频查询与索引口径**：

| 场景 | 表 | 建议索引 |
|---|---|---|
| 体温单曲线 | **主表 join 明细** | 主表 `(encounter_id, recorded_at)` + 明细 `(vital_sign_id, term_code, item_code)` WHERE `item_code=TEMP` |
| 体温单按类型 | **主表** | `(encounter_id, type_enum, recorded_at)` WHERE `is_deleted=false` |
| 患者历史 | **主表** | `(patient_id, recorded_at)` WHERE `is_deleted=false` |
| 单号 | **主表** | `UNIQUE (tenant_id, bus_no)` WHERE `is_deleted=false` |
| 加载明细 | **明细** | `(vital_sign_id)` WHERE `is_deleted=false` |

> **性能原则**：体温单曲线经主表过滤时点后 join 明细取 `TEMP`；明细表随观测项数量线性膨胀，读侧须带 `encounter_id` 或 `vital_sign_id` 约束。

### 2.2 聚合根标识

| 属性 | 类型 | 说明 |
|---|---|---|
| 生命体征单据标识 | `VitalSignId`（`Long`） | 主表主键 |
| 明细标识 | `VitalSignDetailId`（`Long`） | 明细主键 |

### 2.3 状态机

本聚合无复杂业务状态机；有效/无效由逻辑删除表达。

```
  [有效主表+明细] ──主表逻辑删除──> [已删除]（明细级联软删）
  [有效] ──主表级明细覆盖写──> [有效]（未提交 code 软删；已提交 upsert）
```

---

## 3. 实体

### 3.1 生命体征单据（VitalSign，聚合根）

对应表 `dia_vital_sign`。

| 属性 | 字段名 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| 单据标识 | `id` | `Long` | 是 | 主键 |
| 患者标识 | `patient_id` | `Long` | 是 | 冗余，便于按患者查 |
| 就诊标识 | `encounter_id` | `Long` | 是 | 归属就诊 |
| 记录时点 | `recorded_at` | `LocalDateTime` | 是 | 测量/记录发生时间 |
| 单据类型 | `type_enum` | `String` | 是 | 固定枚举 code，见 §4.1 |
| 单号 | `bus_no` | `String` | 是 | 租户内唯一业务单号 |
| 病床位置标识 | `location_id` | `Long` | 否 | **病床** `Location.id` |
| 婴儿序号 | `baby_no` | `Integer` | 否 | 多胎；自然键比对时 null 视为 0 |
| 记录人标识 | `recorder_id` | `Long` | 否 | `Practitioner.id` |
| 备注 | `remark` | `String` | 否 | 整单备注 |

> 通用审计、租户、逻辑删除继承 `BaseAuditedEntity`。

### 3.2 生命体征明细（VitalSignDetail，子实体）

对应表 `dia_vital_sign_detail`。

| 属性 | 字段名 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| 明细标识 | `id` | `Long` | 是 | 主键 |
| 主表标识 | `vital_sign_id` | `Long` | 是 | FK → `dia_vital_sign.id` |
| 术语体系 | `term_code` | `String` | 是 | `ter_code_system.name`（如 `vital_signs_code`） |
| 字典项编码 | `item_code` | `String` | 是 | `ter_concept.code` |
| 测量/观察值 | `item_value` | `String` | 是 | 文本存储 |
| 单位 | `units` | `String` | 否 | 计量单位 |
| 明细备注 | `remark` | `String` | 否 | 行级备注（备用） |

> 明细继承 `BaseAuditedEntity`（含逻辑删除）。**不冗余** `patient_id`/`encounter_id`/`recorded_at`（读侧经主表 join 或应用层组装）。

### 3.3 体征项字典（术语体系，非本聚合表）

**不建** `dia_vital_sign_definition`；明细项由术语模块维护。

| 维度 | 说明 |
|---|---|
| 编码体系 | `ter_code_system.name` ↔ 明细 `term_code` |
| 字典项 | `ter_concept.code` ↔ 明细 `item_code` |
| 维护入口 | 编码/术语维护功能 |
| 读侧 | `CodeSystemQueryService#getOptionListByCodeSystemName(term_code)` |
| 扩展原则 | **页面上可填写的项均须有字典**；院方自行增删 |
| 默认单位 | 可存于 `ter_concept.definition`，保存时回落到明细 `units` |

**勿混淆**

| 字段 | 体系 | 说明 |
|---|---|---|
| `type_enum` | **固定枚举** `VitalSignTypeEnum` | 单据类型，**不**进 `ter_code_system` |
| `term_code` | 术语 `ter_code_system.name` | 明细所属字典体系 |
| `item_code` | 术语 `ter_concept.code` | 明细字典项；体征默认 `term_code=vital_signs_code` |

---

## 4. 值对象（含枚举）

### 4.1 单据类型（`type_enum`）

持久化为 `String`，取值须为 `VitalSignTypeEnum` 的 **code**（**不是**术语字典）。

| code | 中文 | 说明 |
|---|---|---|
| `TEMPERATURE_SHEET` | 体温单 | 三测单/体温单录入 |
| `NURSING_RECORD` | 护理记录 | 护士站护理记录单 |

### 4.2 字典项编码（`item_code`，明细）

- 须与同行 `term_code` 所指的术语体系下 **启用态** `ter_concept.code` 一致。
- 体征类默认 `term_code=vital_signs_code`；体温为 `item_code=TEMP`。
- 写侧保存前经 `CodeSystemQueryService` 按 `term_code` 校验 `item_code` 启用。

### 4.3 体温（明细 `item_code=TEMP`）

- 持久化为明细 `item_value`（`term_code=vital_signs_code`）；数值/拒测等校验由应用层承担。
- 体温单曲线、最近体温、EMR 体温摘要读明细 `TEMP`（经主表 join）。

### 4.4 测量值（`item_value`）

- 持久化为 `String`；兼容数值、复合值（如 `120/80`）、护理汉字项。

### 4.5 记录时点（`recorded_at`）

- 单字段 `LocalDateTime`。
- **不**限制同一 `recorded_at` 仅一条主表。

---

## 5. 领域事件

### 5.1 事件定义

| 事件名称 | 英文名称 | 触发时机 | 携带数据 | 消费方 |
|---|---|---|---|---|
| 生命体征单据已保存 | `VitalSignSavedEvent` | 主表+明细保存成功后 | `vitalSignId`、`encounterId`、`patientId`、`recordedAt`、`typeEnum`、明细 `id` 列表 | QueryService、EMR、传染病 T+0（预留） |
| 生命体征单据已删除 | `VitalSignDeletedEvent` | 主表逻辑删除成功后 | `vitalSignId` 或 `ids`、`encounterId` | 同上 |

### 5.2 事件触发规则

| 当前状态 | 操作 | 目标状态 | 发布事件 |
|---|---|---|---|
| — / 有效 | 保存单据（主+明细） | 有效 | `VitalSignSavedEvent` |
| 有效 | 按主表 id 删除 | 已删除（明细级联） | `VitalSignDeletedEvent` |

---

## 6. 领域服务

### 6.1 写侧领域服务（VitalSignDomainService）

> 写侧由应用层定界事务；明细保存前按 `term_code` 校验 `item_code` 启用。

#### 6.1.1 保存生命体征单据（`saveVitalSign`）

- **业务意义**：新增或更新一张主表及其明细集；更新时按 **主表 id** 做明细覆盖写。
- **入参**：`SaveVitalSignCommand`：
  - 头：`id?`（空则新增）、`encounterId`、`patientId`、`recordedAt`、`typeEnum`、`busNo?`（新增时由应用层或领域生成）、`locationId?`、`babyNo?`、`recorderId?`、`remark?`；
  - 明细：`items`：`List<{ termCode?, itemCode, itemValue, units?, remark? }>`（`termCode` 缺省 `vital_signs_code`；可空列表）。
- **出参**：主表 `id`。
- **前置条件**：
  - `encounterId`、`patientId`、`recordedAt`、`typeEnum` 必填；
  - `typeEnum` 为合法枚举 code；
  - 新增时 `busNo` 非空且租户内唯一（BR-VS-002）；
  - 主表 `items` **至少一项非空**，或 **`type_enum=NURSING_RECORD` 且 `remark` 非空**（BR-VS-008）；
  - 每项明细 `term_code`/`item_code`/`item_value` 非空；`item_code` 在对应 `term_code` 下启用（BR-VS-003）；
  - `patient_id` 与就诊一致（BR-VS-001）。
- **状态变化**：更新时，该主表下**未出现在 items 的有效明细自然键** → 逻辑删除；items 各行 → 按 `(term_code, item_code)` upsert。
- **事件输出**：`VitalSignSavedEvent`。

#### 6.1.2 按主表 id 删除单据（`deleteVitalSignById`）

- **业务意义**：逻辑删除主表及下属全部有效明细。
- **入参**：`DeleteVitalSignByIdCommand`（`id`、`operatorId`）。
- **事件输出**：`VitalSignDeletedEvent`。

#### 6.1.3 批量按主表 id 删除（`deleteVitalSignByIds`）

- **业务意义**：批量删除多张单据。
- **入参**：`DeleteVitalSignByIdsCommand`（`ids`、`operatorId`）。
- **事件输出**：`VitalSignDeletedEvent`。

### 6.2 读侧查询服务（VitalSignQueryService）

#### 6.2.1 按就诊查询主表列表（`listHeadersByEncounterId`）

- **业务意义**：护理记录列表、单据分页；可选 `typeEnum`、`recordedAt` 起止、`babyNo`。
- **数据源**：**仅主表**；排序 `recorded_at DESC`。

#### 6.2.2 体温单曲线点（`listTemperaturePointsByEncounterId`）

- **业务意义**：体温单曲线/表格体温列。
- **数据源**：主表 `recorded_at` + 明细 `term_code=vital_signs_code` 且 `item_code=TEMP`；可按 `type_enum=TEMPERATURE_SHEET` 过滤。
- **过滤**：`encounterId` 必填；`recordedAtFrom/To` 可选。

#### 6.2.3 按主表 id 加载单据及明细（`getWithDetailsById`）

- **业务意义**：编辑回显、打印单条。
- **返回**：主表 + 有效明细列表；明细 `display` 由应用层术语解析。

#### 6.2.4 按就诊查询明细行（`listDetailsByEncounterId`）

- **业务意义**：需要观测项明细且不必逐单加载时；经主表 join。
- **过滤**：可选 `typeEnum`、`itemCodes`、`recordedAt` 起止。

#### 6.2.5 按患者与日期范围查主表（`listHeadersByPatientAndDateRange`）

- **业务意义**：患者维度历史单据列表。

#### 6.2.6 查询就诊最新体征摘要（`getLatestByEncounterId`）

- **业务意义**：EMR 元数据、护士站卡片。
- **返回约束**：
  - 各 `(term_code, item_code)` 分组取 `recorded_at` 最大（经主表 join）；体温即 `item_code=TEMP`。

#### 6.2.7 判断记录时点是否存在主表（`existsAtRecordedTime`）

- **业务意义**：体温单/护理记录 upsert 前判断同就诊+同时点是否已有主表（可选 `babyNo`）。
- **入参**：`encounterId`（必填）、`recordedAt`（必填）、`babyNo`（可空）。
- **返回**：`boolean`；仅统计未逻辑删除主表。

### 6.3 领域边界说明

- 本聚合负责 `dia_vital_sign` + `dia_vital_sign_detail`；字典归术语域。
- 体温单周次、住院天数、术后天数由应用层结合 `Encounter`、`Procedure` 计算。
- `bus_no` 生成规则由应用层或编号服务提供，DM 只约束唯一与非空。

---

## 7. 不变式

| 编号 | 不变式 | 说明 |
|---|---|---|
| BR-VS-001 | 单据必须归属患者与就诊 | 主表 `patient_id`、`encounter_id` 必填且一致 |
| BR-VS-002 | 主表 `bus_no` 租户内唯一 | 有效主表不得重复 `tenant_id + bus_no` |
| BR-VS-003 | 明细 `item_code` 须在对应 `term_code` 下为启用态术语 | 经 `CodeSystemQueryService` 按 `term_code` 校验 |
| BR-VS-004 | 删除一律逻辑删除 | 主表删除须级联软删明细；禁止物理 DELETE |
| BR-VS-005 | 明细覆盖写按主表 id | 未出现在提交集的该单有效明细自然键 `(term_code, item_code)` 视为删除 |
| BR-VS-006 | `location_id` 表示病床 | 床位形态，非科室 |
| BR-VS-007 | 体温写明细 TEMP | `term_code=vital_signs_code`、`item_code=TEMP`；主表无 `temperature` 列 |
| BR-VS-008 | 保存须至少一项业务值 | 明细至少一行，或 **`type_enum=NURSING_RECORD` 且 `remark` 非空** |

---

## 8. 上下文映射

### 8.1 上游依赖

| 上游 | 关联 | 说明 |
|---|---|---|
| ADM `Patient` | `patient_id` | 归属 |
| ADM `Encounter` | `encounter_id` | 归属 |
| ADM `Location` | `location_id` | 病床 |
| ADM `Practitioner` | `recorder_id` | 记录人 |
| 术语 | `term_code` + `item_code` | 明细字典；默认 `term_code=vital_signs_code` |

### 8.2 下游影响

| 下游 | 触发 | 说明 |
|---|---|---|
| EMR 元数据 | 查询 | 最新体征摘要 |
| NUR 护士站 · 护理记录 | 查询 + 事件 | **AS-IP-NUR-014**；`type_enum=NURSING_RECORD` |
| NUR 护士站 · 体温单 | 查询 + 事件 | **AS-IP-NUR-007**；`type_enum=TEMPERATURE_SHEET` |
| CRB | 事件 T+0（预留） | 传染病上报 |

### 8.3 引用规则

- 体温单曲线以 **主表** `encounter_id` + `recorded_at` 过滤，join 明细 `item_code=TEMP`；
- 明细读侧必须带 `encounter_id` 或 `vital_sign_id`，禁止无约束全表扫描；
- 停用术语 concept 不影响历史只读；写侧拒绝停用 code。

---

## 9. 补充

### 9.1 术语种子（`vital_signs_code`）

Flyway 预置体系 `vital_signs_code`。**体温与其它体征同为明细行**：`term_code=vital_signs_code`、`item_code=TEMP`。

**体温单常用项**

| code | display | definition |
|---|---|---|
| `SBP` | 收缩压 | mmHg |
| `DBP` | 舒张压 | mmHg |
| `PULSE` | 脉搏 | 次/分 |
| `RESP` | 呼吸 | 次/分 |
| `SPO2` | 血氧饱和度 | % |
| `HR` | 心率 | 次/分 |
| `PHYS_COOL` | 物理降温 | ℃ |
| `INTAKE` | 入量 | ml |
| `OUTPUT` | 出量 | ml |
| `STOOL` | 大便次数 | 次/天 |
| `URINE_VOL` | 尿量 | ml |
| `CCU_HR` | CCU心率 | bpm |
| `INCUBATOR` | 暖箱温度 | ℃ |
| `BLOOD_KETONE` | 血酮 | mmol/L |
| `WAIST` | 腰围 | cm |
| `ENEMA` | 灌肠次数 | 次/天 |
| `STOOL_AFTER_ENEMA` | 灌肠后大便 | 次/天 |
| `STOOL_VOL` | 大便量 | g |

> 与 openhis `VitalSignsSaveDto` 001–021 对齐：001 体温写明细 `TEMP`；002–021 进明细。V90 另含 `HEIGHT`/`WEIGHT`/`BG`/`PAIN`/`URINE_FREQ` 等，V96 补护理扩展与 openhis 体温单 7 项；**V97** 字段通用化（`term_code`/`item_code`/`item_value`）并迁移主表体温。

**护理记录扩展项**（对齐护士站页面可填项）

| code | display | definition |
|---|---|---|
| `BP` | 血压 | mmHg |
| `CONSCIOUSNESS` | 意识 | |
| `PUPIL_L` | 瞳孔左 | |
| `PUPIL_R` | 瞳孔右 | |
| `OXYGEN_MODE` | 吸氧方式 | |
| `OXYGEN_RATE` | 吸氧 | L/分 |
| `OXYGEN_FLOW` | 氧流量 | |
| `INTAKE_NAME` | 入量名称 | |
| `INTAKE_ROUTE` | 入量途径 | |
| `OUTPUT_NAME` | 出量名称 | |
| `OUTPUT_CHARACTER` | 出量颜色性状 | |
| `SKIN_STATUS` | 皮肤情况 | |
| `TUBE_CARE` | 管路护理 | |

> **不进术语字典的护理记录字段**（落主表，见 §9.1.1）：病情观察及护理 → `remark`；护士签名 → `recorder_id`（展示侧关联 Practitioner 姓名）。

> 院方可在编码维护页继续扩展观测/测量项；不沿用 openhis `001`–`021` 数字编码。

### 9.1.1 主表非字典字段（护理记录页）

下列字段在护士站页面可填，但**不**作为 `term_code=vital_signs_code` 明细行存储：

| 页面字段 | 主表列 | 说明 |
|---|---|---|
| 病情观察及护理 | `remark` | 自由文本叙述 |
| 护士签名 | `recorder_id` | `Practitioner.id`；读侧解析姓名展示 |

### 9.1.3 护士站护理记录 UI 字段映射（AS-IP-NUR-014）

下列映射为护理记录 Tab 写库/读侧 pivot 的**领域事实落点**；应用层 `NursingRecordItemCodeConstants`（`zxplat-domain`）须与本表 code 一致。

| UI 字段 | 落库位置 | `term_code` / `item_code` / 列 |
|---|---|---|
| `temperature` | 明细 | `vital_signs_code` / `TEMP` |
| `conditionMeasure` | 主表 | `remark` |
| `nurseSignature` | 主表 | `recorder_id`（展示名由应用层关联 Practitioner） |
| `pulse` | 明细 | `vital_signs_code` / `PULSE` |
| `respiratory` | 明细 | `vital_signs_code` / `RESP` |
| `heartRate` | 明细 | `vital_signs_code` / `HR` |
| `oxygenSaturation` | 明细 | `vital_signs_code` / `SPO2` |
| `consciousness` | 明细 | `vital_signs_code` / `CONSCIOUSNESS` |
| `pupilLeft` / `pupilRight` | 明细 | `vital_signs_code` / `PUPIL_L` / `PUPIL_R` |
| `oxygenMode` / `oxygenRate` / `oxygenFlow` | 明细 | `vital_signs_code` / `OXYGEN_MODE` / `OXYGEN_RATE` / `OXYGEN_FLOW` |
| `intakeName` / `intakeAmount` / `intakeRoute` | 明细 | `vital_signs_code` / `INTAKE_NAME` / `INTAKE` / `INTAKE_ROUTE` |
| `outputName` / `outputAmount` / `outputColorCharacter` | 明细 | `vital_signs_code` / `OUTPUT_NAME` / `OUTPUT` / `OUTPUT_CHARACTER` |
| `skinStatus` | 明细 | `vital_signs_code` / `SKIN_STATUS` |
| `tubeCare` | 明细 | `vital_signs_code` / `TUBE_CARE` |
| `bloodPressure` | 明细 | 应用层拆为 `SBP`+`DBP`（如 `128/76`）；无法拆分整段写 `BP` |

**读侧编排（应用层，非本聚合表结构）**：

- 列表/打印：`listHeadersByEncounterId` + `listDetailsByEncounterId`（`type_enum=NURSING_RECORD`）批量读后 pivot；`SBP`/`DBP`（或 `BP`）合并展示为 UI `bloodPressure`。
- 记录时点：`recorded_at = recordDate + recordTime`（`LocalDateTime`）。
- **不落库**：`reservedCol1`/`reservedCol2`（AS-IP-NUR-014 §1.4 非目标）。

### 9.1.2 入科入院体征映射（AS-IP-NUR-001/002）

`AdmissionVitalSignSubmitDto` 与领域落库对应关系（`type_enum=TEMPERATURE_SHEET`，`recorded_at=encounter.start_time`）：

| DTO 字段 | 落库位置 | `term_code` / `item_code` |
|---|---|---|
| `temperature` | 明细 | `vital_signs_code` / `TEMP` |
| `heightCm` | 明细 | `vital_signs_code` / `HEIGHT` |
| `weightKg` | 明细 | `vital_signs_code` / `WEIGHT` |
| `heartRate` | 明细 | `vital_signs_code` / `HR` |
| `pulse` | 明细 | `vital_signs_code` / `PULSE` |
| `bloodPressureHigh` | 明细 | `vital_signs_code` / `SBP` |
| `bloodPressureLow` | 明细 | `vital_signs_code` / `DBP` |

写侧经 `AdmissionVitalSignSupport#saveIfPresent` 调 `saveVitalSign`；读侧经 `toSubmitDto` 从 `listHeadersByEncounterId` + `getWithDetailsById` 组装。

### 9.2 变更历史

| 版本 | 日期 | 摘要 |
| --- | --- | --- |
| v2.1.0 | 2026-06-20 | 明细通用化：`term_code`/`item_code`/`item_value`/`remark`；体温下沉明细 TEMP；删主表 `temperature`（V97） |
| v2.0.4 | 2026-06-19 | §9.1.3 护理记录 UI 字段映射；§1.3/§8.2 挂 AS-IP-NUR-014 |
| v2.0.3 | 2026-06-19 | §6.2.7 补 `existsAtRecordedTime` |
| v2.0.2 | 2026-06-19 | BR-VS-008 护理记录允许仅 remark；§9.1 V96 补 openhis 体温单 7 项术语 |
| v2.0.1 | 2026-06-19 | §9.1 移除非字典项（病情观察/护士签名）；补 §9.1.1 主表字段与 §9.1.2 入科映射 |
| v2.0.0 | 2026-06-19 | 单表 EAV 改为主表+明细；新增 `type_enum`、`bus_no`、主表 `temperature`；覆盖写改为主表 id 粒度；体温单读性能口径 |
| v1.1.0 | 2026-06-18 | 术语字典 `vital_signs_code`；编号 DM-DIA-002 |
| v1.0.0 | 2026-06-18 | 初版单表 EAV |

<!-- MODE-B-METHOD-INVENTORY 2026-09-23 -->

## 附录 · 模式 B 已实现方法清单（2026-09-23）

> 自 zxplat-domain 对应 *DomainService / *QueryService 抽取 public 方法名，用于关闭方法白名单 DOC-DRIFT。细规仍以正文 §6 与源码为准；本表不替代九章成稿。

### DomainService：`VitalSignDomainService`

| 方法 |
|------|
| `saveVitalSign` |
| `deleteVitalSignById` |
| `deleteVitalSignByIds` |

### QueryService：`VitalSignQueryService`

| 方法 |
|------|
| `listHeadersByEncounterId` |
| `listTemperaturePointsByEncounterId` |
| `getWithDetailsById` |
| `listDetailsByEncounterId` |
| `listHeadersByPatientAndDateRange` |
| `getLatestByEncounterId` |
| `existsAtRecordedTime` |

