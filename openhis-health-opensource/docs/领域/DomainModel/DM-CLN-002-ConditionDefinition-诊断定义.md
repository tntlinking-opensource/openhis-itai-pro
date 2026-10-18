# DM-CLN-002 ConditionDefinition 诊断定义

> **限界上下文**：CLN（临床域）\
> **聚合类型**：实体聚合（单表）\
> **版本**：v1.2.0 · **日期**：2026-07-18

## 1. 概述

### 1.1 业务定义

诊断定义（ConditionDefinition）是临床目录主数据，承载"可被引用的诊断条目"。该聚合只管理目录项本身（编码、名称、分类、医保标记、发布状态），不管理患者侧诊断事实。患者诊断事实由 `Condition` 聚合承载，并通过 `definitionId` 引用本聚合。

### 1.2 核心职责


| 职责      | 说明                           |
| ------- | ---------------------------- |
| 目录主数据维护 | 维护病种目录，支撑门诊/住院诊断录入候选         |
| 检索字段支持  | 提供拼音码、五笔码检索字段，支撑医生站快速检索      |
| 医保属性管理  | 提供医保标记、医保编码、对码标记等结算相关基础属性    |
| 可用性控制   | 通过 `statusEnum` 控制目录项启用/停用状态 |


### 1.3 适用范围


| 类型      | 说明                            |
| ------- | ----------------------------- |
| 目录管理    | 病种目录新增、编辑、批量启用/停用、导入导出        |
| 医生站诊断录入 | 提供诊断候选列表，按关键字/拼音/分类检索         |
| 医保对码    | 作为医保对码的基础数据锚点                 |
| 下游引用    | 患者诊断事实通过 `definitionId` 引用本聚合 |


### 1.4 与 FHIR ConditionDefinition 的关系


| 维度      | 说明                                                                |
| ------- | ----------------------------------------------------------------- |
| FHIR 对齐 | 对应 FHIR R5 `ConditionDefinition` 资源，描述可引用的诊断条目                    |
| 状态字段    | `statusEnum` 对应 `ConditionDefinition.status`（`PublicationStatus`） |
| 本地扩展    | `sourceEnum`（所属分类）、`ybFlag`（医保标记）等为本地扩展字段                         |


---

## 2. 聚合根与持久化形态

### 2.1 持久化形态

**持久化形态**：单表。

聚合根对应表 `cln_condition_definition`，主键 `id`；目录项由单行承载，可用性由 `status_enum`（`PublicationStatus`）控制。

**高频查询模式（建议索引口径）**：`condition_code` 唯一约束；检索走 `name`、`py_str`、`wb_str`；管理列表按 `source_enum`、`status_enum`、关键字组合过滤。

### 2.2 聚合根标识

| 属性     | 类型     | 说明       |
| ------ | ------ | -------- |
| 诊断定义标识 | `Long` | 主键，雪花 ID |

### 2.3 状态机（发布状态）

状态枚举与 `PublicationStatus` 一致：

```
[草稿] ──启用──> [已启用] ──停用──> [已停用]
  │                 │                   │
  └────────→ [未知] ←───────────────────┘
  [已停用] ──重新启用──> [已启用]
```

#### 状态定义

| value | code | info | 语义 |
| ----- | ---- | ---- | ---- |
| 1 | draft | 草稿 | 未正式启用，允许启用、转为未知 |
| 2 | active | 已启用 | 可用于诊断候选，允许停用、转为未知 |
| 3 | retired | 已停用 | 不再用于新增候选，允许重新启用、转为未知 |
| 4 | unknown | 未知 | 兼容状态 | — |

> 说明：状态枚举值：`1=draft`、`2=active`、`3=retired`、`4=unknown`。

---

## 3. 实体

ConditionDefinition 聚合内无子实体，全部字段由聚合根承载。

### 3.1 ConditionDefinition（聚合根，对应表：`cln_condition_definition`）


| 属性     | 字段名              | 类型        | 必填  | 说明          |
| ------ | ---------------- | --------- | --- | ----------- |
| 诊断定义标识 | `id`             | `Long`    | 是   | 主键          |
| 所属分类   | `source_enum`    | `Integer` | 是   | 见 §4.1      |
| 诊断目录编码 | `condition_code` | `String`  | 是   | 诊断目录编码      |
| 诊断名称   | `name`           | `String`  | 是   | 诊断名称        |
| 拼音检索码  | `py_str`         | `String`  | 否   | 拼音检索码       |
| 五笔检索码  | `wb_str`         | `String`  | 否   | 五笔检索码       |
| 类型编码   | `type_code`      | `String`  | 否   | 类型编码        |
| 描述     | `description`    | `String`  | 否   | 描述          |
| 医保标记   | `yb_flag`        | `Boolean` | 否   | 医保标记        |
| 特慢病编码  | `disease_code`   | `String`  | 否   | 特慢病编码（选填）   |
| 医保编码   | `yb_no`          | `String`  | 否   | **国临目录**绑医保码（西医上传/结算清单真源）；医保目录行自身主码一般用 `condition_code` |
| 国临码    | `clinic_code`    | `String`  | 否   | 对侧回写字段（医保目录可挂国临）；**列表反查真源**为「国临行 `yb_no`=本行医保主码」，非本字段 |
| 医保对码标记 | `yb_match_flag`  | `Boolean` | 否   | 医保对码标记      |
| 发布状态   | `status_enum`    | `Integer` | 是   | 发布状态，见 §4.2 |


> 审计与租户等基础字段继承 `HisBaseEntity`，实现侧实体未在文档中逐项展开。
>
> **双码分流**：病案首页与**住院**医生站西医选码/落库用国临（`source_enum=clinical_v2` 的 `condition_code`；绑医保写 `yb_no`）；医保结算与 4401/4101A **西医**上传输出 `yb_no`（禁止国临顶替）。**门诊转住院**：西医入院诊断若为医保目录且已绑国临，`EncounterInpatientAdmissionOrchestrator` 按 `yb_no` **一对多展开**为全部 `clinical_v2` 行。**中医**住院选码走 `tcm_dx`/`tcm_syndrome`（目录码即落库码）；不对国临对码；结算清单预览国临列空、医保列回显；上传出站用该码。病案首页编辑仅「诊断名称/编码」：中医正常显示目录码/名；西医优先回显国临（有绑码则反查），无绑码时仍展示落库码避免空行。**门诊**医生站西医仍走 `hospital_dx`/`general_dx`（系统设置 `doctorStationWesternDiagnosisSources`），**不含** `clinical_v2`。编码唯一性按 `(source_enum, condition_code)`。

---

## 4. 值对象

### 4.1 所属分类（`ConditionDefinitionSource` -> `sourceEnum`）


| value | code | info      | 语义说明      |
| ----- | ---- | --------- | --------- |
| 0     | —    | 院内诊断      | 术语 code=`hospital_dx`；医院自维护院内诊断目录（非医保导入） |
| 1     | `01` | 疾病与诊断目录   | 术语 code=`general_dx`；医保 1307 |
| 2     | `02` | 门诊慢特病种目录  | 术语 code=`chronic_spec`；医保 1309 |
| 3     | `03` | 按病种付费病种目录 | 术语 code=`disease_pkg`；医保 1310 |
| 4     | —    | （历史）付费诊断  | 术语 code=`paid_dx`；同属 1310，侧栏停用 |
| 5     | `04` | 日间手术治疗病种目录 | 术语 code=`day_surgery_dx`；医保 1311 |
| 6     | `05` | 中医疾病目录    | 术语 code=`tcm_dx`；医保 1314 |
| 7     | `06` | 中医证候目录    | 术语 code=`tcm_syndrome`；医保 1315 |
| 8     | `07` | 肿瘤形态学目录   | 术语 code=`tumor_morph`；医保 1313 |
| 9     | —    | 国家临床版2.0   | 术语 code=`clinical_v2`；病案首页 + **住院**医生站西医选码全量 |
| 10    | —    | 体检诊断      | 术语 code=`peis_dx`；PEIS 诊断建议库（与院内诊断隔离） |


> **存储口径**：`cln_condition_definition.source_enum` 持久化为 **术语 code 字符串**（如 `hospital_dx`、`general_dx`、`tcm_dx`、`clinical_v2`、`peis_dx`），与 `DoctorStationDiagnosisConstants` 及 `condition_definition_source` 种子一致。医保类 display 与 13xx 官方文档目录名一致（去掉「下载」后缀）。**DOC-CONFLICT**：上表 value 列（`01`～`07`）为历史整型展示锚点；医生站读侧/filter 以 **code 字符串** 为准。`clinical_v2` **纳入住院**西医候选（`INPATIENT_WESTERN_DIAGNOSIS_SEARCH_SOURCES`），**不**纳入**门诊**西医候选（门诊默认 `hospital_dx,general_dx`）；`peis_dx` 不进门诊/住院医生站候选；`hospital_dx` / `peis_dx` 均不参与医保/国临双向对码回写。**对码方向**：仅「国临绑医保」（写 `clinical_v2.yb_no`）；编辑医保目录不提供绑国临 UI；医保列表国临列由 `mapClinicBindingsByYbNos` 反查。**中医**（`tcm_dx`/`tcm_syndrome`）不对国临→医保。

### 4.2 发布状态（`PublicationStatus` -> `statusEnum`）


| value | code      | info | 语义说明        |
| ----- | --------- | ---- | ----------- |
| 1     | `draft`   | 草稿   | 草稿态，不对外提供候选 |
| 2     | `active`  | 启用   | 启用态，可用于新增引用 |
| 3     | `retired` | 停用   | 停用态，不用于新增引用 |
| 4     | `unknown` | 未知   | 兼容态，仅用于历史兼容 |


### 4.3 设计预留值对象

- 设计预留：`ConditionCode`、`ConditionNameSearchKey` 等值对象当前由字符串字段直接承载，尚未抽象独立类。

> 相关枚举：`ConditionDefinitionSource`、`PublicationStatus`。

---

## 5. 领域事件

### 5.1 事件定义


| 事件名称    | 英文名称                               | 触发时机    | 消费方              | 携带数据                                     |
| ------- | ---------------------------------- | ------- | ---------------- | ---------------------------------------- |
| 诊断定义已创建 | `ConditionDefinitionCreatedEvent`  | 新增目录成功后 | 领域事件后查询刷新、缓存刷新（设计预留） | `id`、`conditionCode`、`name`、`statusEnum` |
| 诊断定义已编辑 | `ConditionDefinitionEditedEvent`   | 编辑目录成功后 | 领域事件后查询刷新、缓存刷新（设计预留） | `id`、变更字段                                |
| 诊断定义已启用 | `ConditionDefinitionEnabledEvent`  | 启用目录成功后 | 医生站候选缓存（设计预留）    | `id`、`statusEnum`                        |
| 诊断定义已停用 | `ConditionDefinitionDisabledEvent` | 停用目录成功后 | 医生站候选缓存（设计预留）    | `id`、`statusEnum`                        |


> 设计预留：以上事件名称为设计口径，当前代码未见显式领域事件发布实现。

### 5.2 事件触发规则


| 当前状态       | 操作     | 目标状态           | 发布事件                                     |
| ---------- | ------ | -------------- | ---------------------------------------- |
| —          | 新增病种目录 | 草稿/已启用（按初始化策略） | `ConditionDefinitionCreatedEvent`（设计预留）  |
| 草稿/已启用/已停用 | 编辑病种目录 | 状态可不变          | `ConditionDefinitionEditedEvent`（设计预留）   |
| 已停用        | 批量启用   | 已启用            | `ConditionDefinitionEnabledEvent`（设计预留）  |
| 已启用        | 批量停用   | 已停用            | `ConditionDefinitionDisabledEvent`（设计预留） |


---

## 6. 领域服务

### 6.1 写侧领域服务（ConditionDefinitionDomainService）

#### 6.1.1 新增病种目录（`addDisease`）

- **业务意义**：创建可被临床引用的诊断目录条目。
- **边界说明**：只维护目录主数据，不创建患者诊断事实。
- **入参**：病种编码、名称、所属分类、发布状态及检索码等（与 `addDisease` 应用契约一致，字段级见实现/DTO）。
- **出参**：新建诊断定义主键 `id`（或等价布尔/结果对象，以落库与交互契约为准）。
- **前置条件**：
  - `conditionCode` 唯一；
  - `name`、`sourceEnum`、`statusEnum` 等必填字段完整。
- **状态变化**：`— -> 草稿|已启用`（取决于初始化策略）。
- **聚合内影响**：
  - 主表：新增 `cln_condition_definition` 记录；
  - 子表：`N/A`（当前无子实体）。
- **事件输出**：`ConditionDefinitionCreatedEvent`（设计预留）。
- **失败语义**：编码重复或参数不合法时返回业务异常。

#### 6.1.2 新增医保病种目录（`addYbDisease`）

- **业务意义**：创建医保口径的病种目录条目。
- **边界说明**：仍属于目录主数据范围，不处理医保对码流程编排。
- **入参**：在 `addDisease` 基础上增加医保标记、医保编码、对码标记等字段。
- **出参**：新建主键或操作结果（当前实现可能返回 `false`，以代码为准）。
- **前置条件**：医保相关字段（如 `ybFlag`、`ybNo`）满足业务约束。
- **状态变化**：`— -> 草稿|已启用`（与普通新增一致）。
- **聚合内影响**：主表新增记录并写入医保字段。
- **事件输出**：`ConditionDefinitionCreatedEvent`（设计预留）。
- **失败语义**：当前实现返回 `false`，视为能力待完善。

#### 6.1.3 编辑病种目录（`editDisease`）

- **业务意义**：修正目录项基础信息，保持目录可维护性。
- **边界说明**：只更新目录项自身，不改写已落库的患者诊断事实。
- **入参**：目标 `id` 及待更新字段（名称、编码、分类、描述、医保字段等）。
- **出参**：— 或更新结果对象。
- **前置条件**：
  - 目标 `id` 存在；
  - 变更后编码仍满足唯一性。
- **状态变化**：`草稿|已启用|已停用 -> 原状态`（通常不变）。
- **聚合内影响**：更新主表并重算 `pyStr`、`wbStr`。
- **事件输出**：`ConditionDefinitionEditedEvent`（设计预留）。
- **失败语义**：记录不存在、编码冲突时返回业务异常。

#### 6.1.4 批量停用病种目录（`editDiseaseStop`）

- **业务意义**：将目录项从候选集中下线，阻止新增引用。
- **边界说明**：仅变更本聚合可用状态，不直接处理下游缓存一致性。
- **入参**：待停用的诊断定义 `id` 集合。
- **出参**：— 或批量操作结果。
- **前置条件**：入参数组非空且目标记录存在。
- **状态变化**：`已启用 -> 已停用`。
- **聚合内影响**：批量更新 `statusEnum=RETIRED`。
- **事件输出**：`ConditionDefinitionDisabledEvent`（设计预留）。
- **失败语义**：目标不存在或状态非法时返回业务异常。

#### 6.1.5 批量启用病种目录（`editDiseaseStart`）

- **业务意义**：恢复目录项可用性，重新进入候选集。
- **边界说明**：仅变更目录状态，不编排外部QueryService重建。
- **入参**：待启用的诊断定义 `id` 集合。
- **出参**：— 或批量操作结果。
- **前置条件**：目标记录存在且允许由停用恢复启用。
- **状态变化**：`已停用 -> 已启用`。
- **聚合内影响**：批量更新 `statusEnum=ACTIVE`。
- **事件输出**：`ConditionDefinitionEnabledEvent`（设计预留）。
- **失败语义**：状态不允许迁移时返回业务异常。

### 6.2 读侧查询服务（ConditionDefinitionQueryService）

#### 6.2.1 按ID查询诊断定义（`getConditionDefinitionListById`）

- **业务意义**：获取单条目录定义用于详情回显或校验。
- **边界说明**：只读查询，不写库，不做跨域编排。
- **查询范围（读库，无写入）**：`cln_condition_definition` 主表。
- **过滤口径**：按主键精确匹配。
- **组装规则**：返回定义本体字段与必要展示字段。
- **返回约束**：不存在返回空对象或空集。

#### 6.2.2 按ID集合查询诊断定义（`getConditionDefinitionListByIds`）

- **业务意义**：批量回填诊断定义名称和属性。
- **边界说明**：只读查询，不写库，不做跨域编排。
- **查询范围（读库，无写入）**：`id in (...)`。
- **过滤口径**：仅返回命中记录。
- **组装规则**：按输入顺序或业务排序返回。
- **返回约束**：部分缺失时返回命中子集。

#### 6.2.3 按关键字检索诊断定义（`getConditionDefinitionListBySearchKey`）

- **业务意义**：支撑医生站按编码/名称/拼音/五笔检索诊断目录。
- **边界说明**：只读查询，不写库，不做跨域编排。
- **查询范围（读库，无写入）**：`condition_code`、`name`、`py_str`、`wb_str`。
- **过滤口径**：关键字模糊匹配 + 可用状态过滤。
- **组装规则**：返回候选列表及检索命中展示字段。
- **返回约束**：无命中返回空集合。

#### 6.2.4 查询病种目录分页（`getDiseaseList`）

- **业务意义**：提供诊断目录统一分页检索能力，作为目录管理与诊断元数据视图的共同领域查询入口。
- **边界说明**：只读查询，不写库，不做跨域编排。
- **查询范围（读库，无写入）**：主表分页查询。
- **过滤口径**：按分类、状态（`DiseasePageQuery.statusEnum`，`ACTIVE`/`INACTIVE`）、关键字及业务类型（如中西医）等条件统一过滤。
- **组装规则**：返回标准分页结构；应用层可基于同一结果组装“目录管理视图”或“诊断元数据视图”。
- **返回约束**：分页空页返回空列表与总数。

#### 6.2.5 查询病种目录详情（`getDiseaseOne`）

- **业务意义**：读取单个目录项完整配置。
- **边界说明**：只读查询，不写库，不做跨域编排。
- **查询范围（读库，无写入）**：按 `id` 读取主表。
- **过滤口径**：主键精确匹配。
- **组装规则**：返回目录字段与检索码字段。
- **返回约束**：不存在返回空对象或业务提示。

#### 6.2.6 医生站分页检索已发布目录（`pageActiveDoctorStationDefinitions`）

- **业务意义**：支撑门诊医生站「新增诊断」弹窗 IF-DOR-07（`AS-OP-DOR-002` / `AS-OP-DOR-002-SDD`）在**仅已启用（`ACTIVE`）且未软删**目录行中分页检索候选定义。
- **边界说明**：只读；使用 JPA `Specification` 动态条件，**非**独立QueryService专表。
- **查询范围（读库，无写入）**：`cln_condition_definition`。
- **过滤口径**：`is_deleted=false`；`status_enum=ACTIVE`；可选 `source_enum IN (...)`（入参为**字符串编码集合**，用于中西医等目录来源收窄，集合与码表须一致）；关键字非空时对 `name`、`condition_code`、`py_str`、`wb_str` 做 OR 模糊匹配。
- **排序**：`updated_at` 降序，主键降序作为稳定次序。
- **组装规则**：返回 `Page<DoctorStationConditionDefinitionRowModel>`（读侧投影，字段见下表）。
- **返回约束**：合法页码/页大小外由实现归一（如页码 `<1` 视为 1）。

**`DoctorStationConditionDefinitionRowModel`（读侧行投影，非聚合实体）**

| 属性 | 类型（实现口径） | 说明 |
|---|---|---|
| 诊断定义标识 | `Long` | 同聚合根 `id` |
| 诊断目录编码 | `String` | `condition_code` |
| 诊断名称 | `String` | `name` |
| 拼音检索码 | `String` | `py_str` |
| 五笔检索码 | `String` | `wb_str` |
| 所属分类编码 | `String` | `source_enum` 持久化字符串 |
| 发布状态编码 | `String` | `status_enum` 持久化字符串 |
| 医保标记 | `Boolean` | `yb_flag` |

#### 6.2.7 医生站按主键批量装载已发布目录行（`listActiveDoctorStationRowsByIds`）

- **业务意义**：在 IF-DOR-06A、IF-DOR-07 等场景中，对已收集的诊断定义主键集合**批量**取回医生站展示用字段；自动过滤已软删或非 `ACTIVE` 行。
- **边界说明**：只读；**不保证**返回顺序与入参 `ids` 一致，调用方若需稳定顺序应自行重排。
- **查询范围（读库，无写入）**：`id in (...)` 命中后内存过滤 `ACTIVE` + 未删除。
- **过滤口径**：见上。
- **组装规则**：返回 `List<DoctorStationConditionDefinitionRowModel>`。
- **返回约束**：`ids` 为空或 null 时返回空列表。

#### 6.2.8 医生站按门慢门特病种码批量解析已发布目录行（`listActiveDoctorStationRowsByOpspDiseCodes`）

- **业务意义**：支撑门诊医生站 IF-DOR-06B：将医保平台门慢门特备案病种代码批量解析为**已启用**诊断定义，供「慢病备案」候选勾选写入。
- **边界说明**：只读；**不**调用医保平台；**不**写库；一对多歧义时由本方法按稳定优先级择一条，调用方须能区分「未命中」。
- **查询范围（读库，无写入）**：`cln_condition_definition`，`is_deleted=false` 且 `status_enum=ACTIVE`。
- **匹配优先级（对每个入参码独立）**：
  1. `disease_code` 精确等于入参码；
  2. 否则 `yb_no` 精确等于入参码；
  3. 否则 `condition_code` 精确等于入参码。
- **歧义处理**：同一优先级命中多行时，优先 `source_enum=chronic_spec`（特慢病诊断目录），再按 `updated_at` 降序、主键降序取一条。
- **组装规则**：返回 `List<OpspDiseCodeDefinitionMatchQueryModel>`（见下表）；入参码无命中时**不**出现在结果中（由应用层标未匹配）。
- **返回约束**：`opspDiseCodes` 为空或 null 时返回空列表；入参去空白后忽略空串。

**`OpspDiseCodeDefinitionMatchQueryModel`（读侧匹配投影，非聚合实体）**

| 属性 | 类型（实现口径） | 说明 |
|---|---|---|
| 门慢门特病种码 | `String` | 入参备案码（归一化后） |
| 诊断定义标识 | `Long` | 命中目录主键 |
| 诊断目录编码 | `String` | `condition_code` |
| 诊断名称 | `String` | `name` |
| 所属分类编码 | `String` | `source_enum` |
| 匹配字段 | `String` | `disease_code` / `yb_no` / `condition_code`（实际命中列） |

### 6.3 领域边界说明

- 本聚合是"诊断目录主数据"，不承载患者诊断事实（患者事实由 `Condition` 聚合管理）；
- 聚合内负责目录新增、编辑、启停等规则，医生站页面组装与批处理流程由应用层编排；
- 与医保、字典等跨域同步属于集成编排，不在本聚合内直接落事务耦合；
- **医生站 IF-DOR-07** 的目录分页检索使用 **`pageActiveDoctorStationDefinitions`**；**常用树 / 病人历史**等对已收集 `definitionId` 的批量展示装配使用 **`listActiveDoctorStationRowsByIds`**；**慢特病备案导入（IF-DOR-06B）** 使用 **`listActiveDoctorStationRowsByOpspDiseCodes`**。病种管理后台等**管理列表**仍可复用 **`getDiseaseList`** / **`getDiseaseOne`**，由应用层按场景选择入口，避免混用持久化过滤语义。

---

## 7. 不变式


| 编号         | 不变式                      | 说明                                        |
| ---------- | ------------------------ | ----------------------------------------- |
| BR-CDF-001 | 诊断目录编码唯一                 | `addDisease` 先按 `conditionCode` 查重        |
| BR-CDF-002 | 诊断名称变更需同步检索码             | `editDisease` 同步重算 `pyStr/wbStr`          |
| BR-CDF-003 | 启停仅通过 `statusEnum` 控制可用性 | `editDiseaseStart/editDiseaseStop` 批量更新状态 |
| BR-CDF-004 | 目录定义与患者事实分离              | 本聚合不存患者ID/就诊ID                            |


---

## 8. 上下文映射

### 8.1 上游依赖

| 上游上下文 | 聚合 | 关联方式 | 说明 |
|---|---|---|---|
| 标准与字典域 | 诊断编码体系/医保字典 | 编码对齐 | 提供目录编码与分类口径 |
| 机构配置域 | 机构目录配置 | 配置引用 | 控制目录启停和可见范围 |

### 8.2 下游影响

| 下游上下文 | 聚合 | 触发方式 | 说明 |
|---|---|---|---|
| 临床域（CLN） | `Condition` | ID 引用/查询 | 患者诊断事实通过 `definitionId` 引用目录 |
| 医生站应用 | 诊断录入页 | 查询 | IF-DOR-07 依赖 `pageActiveDoctorStationDefinitions`；IF-DOR-06A 等依赖 `listActiveDoctorStationRowsByIds`；IF-DOR-06B 依赖 `listActiveDoctorStationRowsByOpspDiseCodes`（见 §6.2.6～6.2.8） |
| 字典管理应用 | 目录维护页 | 写读 | 承载目录维护与检索 |

### 8.3 引用规则

- 业务事实引用诊断定义时仅保存 `definitionId`，不复制目录全量字段或冗余快照；
- 目录停用后应阻止新增引用，历史事实记录保持可追溯；
- 跨域按编码与标识对齐，避免在目录聚合中写入患者/就诊事实字段；
- 对外事件仅发布目录变更最小载荷，避免把目录模型泄漏为跨域写模型。

### 8.4 事件流示意图

```text
诊断编码体系/配置来源
        └──(编码与配置口径)──> ConditionDefinition
                                      ├── ConditionDefinitionCreatedEvent  ──> 医生站候选缓存/QueryService
                                      ├── ConditionDefinitionEditedEvent   ──> 医生站候选缓存/QueryService
                                      ├── ConditionDefinitionEnabledEvent  ──> 医生站候选缓存/QueryService
                                      └── ConditionDefinitionDisabledEvent ──> 医生站候选缓存/QueryService
```


---

## 9. 补充

### 9.1 变更历史

| 版本 | 日期 | 摘要 |
|---|---|---|
| v1.2.0 | 2026-07-18 | 新增 §6.2.8 `listActiveDoctorStationRowsByOpspDiseCodes` 与 `OpspDiseCodeDefinitionMatchQueryModel`（支撑 AS-OP-DOR-002 IF-DOR-06B） |
| v1.1.1 | 2026-05-28 | **M1**：§6.1/§6.2 与 `ConditionDefinitionDomainService`/`ConditionDefinitionQueryService` 白名单对齐（5写+7读） |
| v1.1 | 2026-05-02 | 增补 `ConditionDefinitionQueryService#pageActiveDoctorStationDefinitions`、`#listActiveDoctorStationRowsByIds` 及 `DoctorStationConditionDefinitionRowModel`；修订 §6.3 医生站与病种管理查询入口分工（对齐 `zxplat` 与 `AS-OP-DOR-002-SDD`） |
| v1.0 | 2026-05-02 | 初版 |

<!-- MODE-B-METHOD-INVENTORY 2026-09-23 -->

## 附录 · 模式 B 已实现方法清单（2026-09-23）

> 自 zxplat-domain 对应 *DomainService / *QueryService 抽取 public 方法名，用于关闭方法白名单 DOC-DRIFT。细规仍以正文 §6 与源码为准；本表不替代九章成稿。

### DomainService：`ConditionDefinitionDomainService`

| 方法 |
|------|
| `addDisease` |
| `ensureActiveChronicSpecOpspDisease` |
| `addYbDisease` |
| `editDisease` |
| `importClinicalV2Catalog` |
| `importYbConditionCatalog` |
| `editDiseaseStop` |
| `delete` |
| `editDiseaseStart` |

### QueryService：`ConditionDefinitionQueryService`

| 方法 |
|------|
| `getConditionDefinitionListById` |
| `getConditionDefinitionListByIds` |
| `getConditionDefinitionListBySearchKey` |
| `searchClinicalV2ByKeyword` |
| `mapNameByClinicCodes` |
| `mapClinicBindingsByYbNos` |
| `mapClinicalDefinitionIdsByYbNos` |
| `mapDiagnosisKindByConditionCodes` |
| `resolveClinicalPresentationFromDefinition` |
| `mapClinicalPresentationByStoredCodes` |
| `mapYbNoByClinicCodes` |
| `mapYbMappingByClinicCodes` |
| `mapNamesByYbNos` |
| `getDiseaseList` |
| `pageActiveDoctorStationDefinitions` |
| `listActiveDoctorStationRowsByIds` |
| `listActiveDoctorStationRowsByOpspDiseCodes` |
| `getDiseaseOne` |
| `getDiagnosisList` |

