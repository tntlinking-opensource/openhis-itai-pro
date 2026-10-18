# PeisAppService · FHIR 对齐领域模型

> **版本**：v1.5 · **日期**：2026-06-27  
> **落地方案（已定）**：**zxplat-domain 为主** — `Encounter(PE)` + `ServiceRequest` + **`cln_observation`** + `DiagnosticReport；目录 **[DM-CLN-005](./DM-CLN-005-ObservationDefinition-临床观察定义.md)** / 实例 **[DM-CLN-007](./DM-CLN-007-Observation-临床观察实例.md)**；PEIS 负责诊台/总检编排。  
> **跨模块约定**：[01-概要设计 §7](./01-概要设计.md#7-lispacspeis-跨模块架构约定)（LIS 全文见 [LisAppService/01 §10](../LisAppService/01-概要设计.md#10-lispacspeis-跨模块架构约定)）  
> **关联**：[00-功能清单.md](./00-功能清单.md) §1.3、[README.md](./README.md) §2、[./DM-ADM-002-Encounter-就诊.md](./DM-ADM-002-Encounter-就诊.md)

---

## 一、总览：健康体检链路

```text
booking（H 线）                    health（核心）                         peis-application
─────────────                    ──────────────                         ────────────────
Appointment / 到检签到
       │
       ▼
                          Encounter（class=PE，一次完整体检）
                               │
       套餐 ActivityDefinition ─┼─► ServiceRequest × N（体检项目实例，parent 成组）
       项目 ActivityDefinition  │         │
                               │         ├── Observation × M（小项结果，→ ObservationDefinition）
                               │         ├── LisWorkItem / PacsWorkItem（外发项目，经 SR）
                               │         └── Task（可选：复查、导检待办）
                               │
                               ├── ChargeItem[]（HIS 收费，只读 Reader）
                               ├── DiagnosticReport（总检报告，1 次体检 1 份主报告）
                               ├── Condition[]（可选：写入病历的阳性结论）
                               └── DetectedIssue[]（危急值/重大阳性）
```

**原则**：

1. **一次完整体检 = 一条 `Encounter`，`class_enum = PE`**（见 §三）；不再以 `PeisCheckOrder` 为 health 侧聚合根。
2. **目录三层**：套餐/项目 → `ActivityDefinition`；小项 → **`ObservationDefinition`**（[DM-CLN-005](./DM-CLN-005-ObservationDefinition-临床观察定义.md)）。
3. **运行时三层**：项目 → `ServiceRequest`；小项结果 → **`cln_observation`**；总检 → **`DiagnosticReport**（索引见 **`cln_diagnostic_report_index`**）。
4. **LIS/PACS** 仍按各自 Module 执行；检验事实在 **`lis_observation`**；PEIS 通过 `ServiceRequest` 外发，结果 **写入 `cln_observation`** 并 **`source_observation_id` 引用** `lis_observation`（见 [01 §7.2](./01-概要设计.md#72-observation-与-clnd1--d7)）。
5. **遗留 `PeisCheckOrder`** 仅作 **sdpeis 迁移别名 / 查询视图**（`bus_no` ↔ `Encounter.bus_no`），新开发不新建 peis 平行事实表（CUS 扩展表除外）。

---

## 二、遗留 sdpeis 三层目录 → ZXPlat 目录

| 遗留 | 说明 | ZXPlat 目录 | DM / 表 |
|------|------|------------|---------|
| `sys_check_package` | 体检套餐 | **组合 `ActivityDefinition`**（`children_flag=true`，类别标体检） | [DM-WKF-001](./DM-WKF-001-ActivityDefinition-诊疗项目定义.md) |
| `sys_check_project` | 体检项目（分科执行单位） | **`ActivityDefinition`**（子项或独立 AD；`type_enum`=检验/检查/其他） | 同上 |
| `sys_check_item` | 体检小项（结果录入单位） | **`ObservationDefinition`** | [DM-CLN-005](./DM-CLN-005-ObservationDefinition-临床观察定义.md) |
| 项目↔小项 | 组合关系 | **`ActivityObservationLink`（CUS）** | [DM-CUS-013](./DM-CUS-013-ActivityObservationLink-项目小项绑定.md) · `domain.custom` |
| 套餐适用性别/年龄 | 预约规则 | **booking** `bkg_health_check_package` 扩展 | [booking §4.9](../../../模块/zxplat-module-booking/00-功能清单.md) |
| `sys_check_diagnosis` | 诊断建议模板 | **`ConditionDefinition` + PEIS 扩展字段** 或 `peis_diagnosis_template` | [DM-CLN-002](./DM-CLN-002-ConditionDefinition-诊断定义.md) |
| `sys_check_result` | 常见结果 | **`peis_common_result`** | [DM-PEIS-003](./DM-PEIS-003-PeisCommonResult-常见结果.md) |
| `sys_auto_check_express*` | 自动诊断表达式 | **`peis_auto_express`** | [DM-PEIS-006](./DM-PEIS-006-PeisAutoExpress-自动诊断表达式.md) |
| `sys_critical_value` | 危急值规则 | **`DetectedIssue` 规则** 或 PEIS 配置扩展 | 与 LIS 危急值策略对齐 |

**booking 套餐与 AD 关系**（已定）：

- `bkg_health_check_package_item.activity_definition_id` → 套餐行引用 **`ActivityDefinition`**
- 预约时固化 **套餐快照 JSON**；到检登记后按快照展开 **`ServiceRequest`**

---

## 三、`Encounter.class_enum = PE`（健康体检）

### 3.1 扩展 `EncounterClass`

在 [DM-ADM-002](./DM-ADM-002-Encounter-就诊.md) §4.1 基础上 **新增**：

| value | code | info | 语义 |
|-------|------|------|------|
| 4 | **PE** | 健康体检 | 一次完整体检到检周期；**禁止**用 AMB 状态文案解释 PE 记录 |

**与 `HealthcareService` 分工**：

| 维度 | 字段 | 说明 |
|------|------|------|
| 就诊类型 | `Encounter.class_enum = PE` | 一次完整体检锚点；**进度见 §3.2 多模型** |
| 服务/号源 | `HealthcareService.category = PHYSICAL_EXAM` | 排班、预约、Location |
| 细粒度 | `Encounter.type_enum` / `service_type_id` | 入职体检、年度体检、团检等 |

### 3.2 体检进度：多模型联合（**不扩展** `Encounter.status_enum`）

> **硬约束**：`Encounter(class=PE)` **仅使用** DM-ADM-002 **通用** `status_enum`（`planned` / `in-progress` / `finished` / `cancelled` 等）。**禁止**新增 `PENDING_MASTER`、`MASTER_REVIEW`、`REPORT_ISSUED` 等 PE 专用值。  
> 列表/报表所需的「待总检 / 总检中 / 已发放」由下列 **事实模型联合** 或 **`PeisEncounterProgressQuery` 只读聚合** 得出。

| 业务维度 | 承载模型 | 关键字段 / 语义 |
|----------|----------|-----------------|
| 预约未到检 | booking · `Appointment` | 有预约、尚无 PE Encounter 或 Encounter=`planned` |
| 登记 / 收费 | `Encounter(PE)` + `ChargeItem`（Client） | 已登记；未收费可阻塞分科（REG-08） |
| 分科项目执行 | `ServiceRequest` + **`PeisServiceRequestExt`** | `exec_status_enum`: `pending` → `checked` → `abandoned` / `deferred` |
| LIS/PACS 外发 | **`PeisServiceRequestExt`** | `lis/pacs_dispatch_enum`: `notSent` → `sent` → `resultReceived` |
| 总检 / 报告正文 | `DiagnosticReport | `status_enum`: `partial` → `preliminary` → `final` |
| 报告发放 | `DiagnosticReport + **`PeisReportGrantExt`**（CUS，可选） | 打印 / 签收 / 邮寄 |
| 复查 | `Task`（`for=Encounter`）+ 复查 SR 子集 | 复查 Task 未关闭即「复查中」 |

```text
Encounter(PE): planned → in-progress → finished | cancelled   （通用态，仅此一层）
  ├─ ChargeItem（Client）           收费完成
  ├─ ServiceRequest[] + Ext       分科执行 / 外发
  ├─ DiagnosticReport             总检 final
  ├─ PeisReportGrantExt（可选）    发放签收
  └─ Task（可选）                  复查
```

**与 booking 衔接**：`Appointment` 到检签到 → 创建或激活 `Encounter(PE)`；`Encounter.bus_no` 与 booking **体检流水号 / busNo** 互查（IF-PEIS-BKG-02）。

**QueryService 规则**：`class_enum=PE` 时列表 **必须** 走 `PeisEncounterProgressQuery`（或等价聚合），**禁止**用 AMB 文案解释 PE，也 **禁止**假设 Encounter 上存在总检专用 status。

### 3.3 登记主路径（对应 01 功能清单 P0）

```text
1. 创建 Encounter(class=PE, patientId, orgId=体检中心, serviceTypeId=PHYSICAL_EXAM 服务)
2. 按套餐 ActivityDefinition 展开 ServiceRequest[]（parent_id 指向套餐父 SR 或 requisition 分组）
3. health 收费 AS → ChargeItem(encounterId)；PEIS REG-08 Client 确认已收费
4. `Encounter.status` → `in-progress`（通用）；分科/总检进度见 §3.2 多模型
5. 打印导引单/条码（report；LIS 项后续外发）
```

---

## 四、运行时事实

### 4.1 `ServiceRequest` — 体检项目实例

| 属性 | 说明 |
|------|------|
| `encounter_id` | 所属 PE 就诊 |
| `activity_id` | 引用 **`ActivityDefinition`（体检项目）** |
| `parent_id` / `group_id` / `requisition_id` | 套餐展开成组（同 [DM-WKF-004](./DM-WKF-004-ServiceRequest-服务申请.md)） |
| **项目执行态（CUS 扩展）** | `pending` → `checked` → `abandoned` / `deferred`；见 §4.4 |

**展开规则**：登记选套餐 → 读 AD 子项表 `listChildrenByActivityId` → 批量创建 SR；加项/减项在 **登记后、未收费/未执行前** 允许（REG-04）。

### 4.2 `cln_observation` — 小项结果实例（P0）

> **表前缀**：`cln_observation`（[DM-CLN-007](./DM-CLN-007-Observation-临床观察实例.md)）。**禁止** PEIS 私有平行事实表。

| 属性 | 说明 |
|------|------|
| `encounter_id` | PE 就诊 |
| `service_request_id` | 归属体检项目 |
| `definition_id` 或 `code` | 引用 **`ObservationDefinition`** |
| `value` / `interpretation` | 文本、数值、阴阳性等 |
| `status_enum` | `preliminary` → `final`（分科确认） |
| `source_observation_id` | 可选：引用 **`lis_observation.id`**；PACS 摘要可引用 DR index |

**来源**：

| 来源 | 写入方式 |
|------|----------|
| 分科手工/常见结果 | 诊台直接写 **`cln_observation`** |
| LIS | DES-08：`lis_observation` → 创建/更新 **`cln_observation`** + `source_observation_id` |
| PACS | 摘要写 **`cln_observation`**；完整报告经 **`cln_diagnostic_report_index`** 引用 PACS DR |
| 微信自测上传 | MOB-04 → `cln_observation`（P3） |

### 4.3 `DiagnosticReport — 总检报告

| 属性 | 说明 |
|------|------|
| **事实表** | **`cln_diagnostic_report**（[DM-CLN-008](./DM-CLN-008-DiagnosticReport-临床总检报告.md)） |
| **读侧索引** | `cln_diagnostic_report_index`，`module_enum=PEIS`，`source_report_id=cln_dr.id` |
| `encounter_id` | 一次体检 **1 份主报告**（团检个人报告仍按人+Encounter） |
| `status_enum` | `partial` → `preliminary` → `final` |
| `conclusion` / `summary` | 总检综述、结论（结构化字段 + PDF 由 report 渲染） |
| `result[]` | 引用关键 Observation、分科 Condition 摘要 |

分科 **诊断建议**（遗留 `peis_check_order_diagnosis_rel`）：

| 策略 | 落点 |
|------|------|
| **默认（P0）** | **`cln_observation`** + OD.`categoryCode=dept_conclusion`（每 SR）；模板来自 **ConditionDefinition**（CFG-002） |
| **可选同步病历** | 发布事件 → health **`Condition`** |

### 4.4 `PeisServiceRequestExt`（CUS）

> **成稿编号**：DM-PEIS-001

遗留「弃检/延检/清空/外发标志」不宜全部塞进 `ServiceRequest` 主表：

```text
peis_service_request_ext
  service_request_id  PK/FK
  exec_status_enum    pending | checked | abandoned | deferred
  lis_dispatch_enum   notSent | sent | resultReceived
  pacs_dispatch_enum  同上
```

**条码**：**不在此表**；真源 **`LisWorkItem.barcode` / PACS 检查号**，PEIS 经 `service_request_id` → WorkItem 反查（[01 §7.3](./01-概要设计.md#73-外发与扩展表d5--d6)）。条码绑定能力并入 **IF-PEIS-INT-01** 子能力（原 INT-02）。

AppService **DES-001 / INT-001** 读写本扩展；LIS/PACS 外发仍以 **`ServiceRequest` + LisWorkItem/PacsWorkItem** 为主链。

### 4.5 复查 — `Task` + 复查 SR

| 方案 | 说明 |
|------|------|
| **A（推荐）** | `Task`：`for` = Encounter，`focus` = 复查相关 SR；**不**改 Encounter 专用 status |
| B | 仅复查 SR 子集 + `PeisRecheckExt` 标记 |

---

## 五、与 LIS / PACS 的衔接（同进程，无项目对照表）

ZXPlat **一体化**部署时，PEIS、LIS、PACS 共用 **health `ActivityDefinition` + `ServiceRequest`**，外发链路：

```text
Encounter(PE) + ServiceRequest(activity_id = 同一套 AD)
        │
        ├── type=检验 (PROOF) ──► 医生站/PEIS INT-01 → LisWorkItemDomainService
        │                              └── lis_observation
        │                                       └── DES-08 → cln_observation（source_observation_id）
        │
        └── type=检查 (TEST)  ──► 医生站/PEIS INT-01 → PacsWorkItemDomainService
                                       └── pacs DR final → cln_diagnostic_report_index
                                                └── DES-08 摘要 → cln_observation
```

**不需要 `peis_lis_project_map` / `peis_pacs_project_map`**，原因：

| 遗留 sdpeis 为何要对照 | ZXPlat 一体化为何不用 |
|------------------------|----------------------|
| PEIS 自有目录 `sys_check_project` | 目录统一为 **`ActivityDefinition`** |
| LIS/PACS 是 **外部系统**，码表不同 | LIS/PACS 读 **同一 `activity_id` / `service_request_id`** |
| `peis_lis/pacs_*_compare` 做码表翻译 | 合管、通道、模态等归 **LIS/PACS CFG**（`lis_*` 扩展列、`pacs_*` 配置），不是 PEIS 再建一张对照 |

**PEIS 侧只配置「路由策略」**（可选，P1）：

- 按 `ActivityDefinition.type_enum`：检验 → LIS、检查 → PACS、其他 → 分科手工录入
- 或 `ActivityDefinition` 扩展标记 `dispatch_target_enum`（LIS / PACS / DEPT / NONE）

**AS-PEIS-INT-002** 在一体化模式下为 **外发路由与连接监控**（可选），**不是**项目码对照维护。

### 5.2 DES-08 / INT-01 时序（P0）

| 阶段 | 触发 | 行为 |
|------|------|------|
| **登记/分科保存** | REG-02 / DES-02 | 创建 SR + `peis_service_request_ext`；检验/检查项标记待外发 |
| **外发 INT-01** | DES-02 保存后 **或** 登记批量外发（院点配置） | 同进程调 LIS/PACS DomainService；写 ext `*_dispatch_enum=sent` |
| **条码绑定** | INT-01 子能力 | 读 `LisWorkItem.barcode` / Pacs `accessionNo`；**不**写 ext.barcode |
| **LIS 审核发布（P0 主路径）** | LIS **AUD-05** `publishReport | `lis_observation`（已 final）→ **`LisResultProjectionService#projectFromLis`** → upsert **`cln_observation`** + `source_observation_id`；同事务 upsert **`cln_diagnostic_report_index`**；有 ext 时 → `resultReceived` |
| **PACS 审核发布（P0 主路径）** | PACS **REP-07** 审核通过 | `pacs_diagnostic_report final → upsert **index** → **`projectFromPacsSummary`** → upsert **`cln_observation`**（检查摘要）；有 ext 时 → `resultReceived` |
| **DES-08 / INT-03（补偿）** | 诊台 **手动刷新** / 漏投影重试 | LIS/PACS 投影同上；AUD-05/REP-07 失败重试或院点关闭自动投影时 |

真源 BR：**BR-CLN-OBS-01**（[DM-CLN-007](./DM-CLN-007-Observation-临床观察实例.md)）；幂等键 `(serviceRequestId, definitionId)`。

**金域外送（口语「外发 LIS」）**：与 INT-01 **不矛盾**——先走本院 **LisWorkItem**（上表 INT-01 行）；若项目标记外送金域，LIS 侧经 [LisKingMed EXT](../../../模块/zxplat-module-lis-kingmed/00-功能清单.md) 上传；结果仍回 `lis_observation` → DES-08 投影。院点：**师大 9/1 前**；朝阳金域见 [LIS README](../LisAppService/README.md) 二期。

### 5.1 何时仍需要「对照」（adapter 场景，非默认）

| 场景 | 落点 |
|------|------|
| 过渡期对接 **外购** LIS/PACS | **peis-adapter** 或 lis/pacs-adapter 内码表（类似遗留 `peis_lis_*_compare`） |
| sdpeis **数据迁移** | 一次性映射脚本：`sys_check_project.bus_no` → `ActivityDefinition.id` |
| 区域平台 **FHIR 码表** 与院内 AD 不一致 | health **TerminologyCodeMapping**（全院级，非 PEIS 专用） |

默认 **P0 自研全栈院点**：无 PEIS 项目对照 IF；INT-06 降为 adapter 或删除。

---

## 六、团检与 booking

| 概念 | 落点 |
|------|------|
| 团检单位/学校 | booking `bkg_group_checkup_org` + `Organization` |
| 团检批次 | `bkg_group_checkup_batch` |
| 名单 | `bkg_group_checkup_roster` |
| 每人一次体检 | **每人一条 `Encounter(PE)`**；批次 ID 存 Encounter 扩展或 `class_json` |
| 合同/结算方式 | 组织扩展 + **health 收费**（团检记账） |

---

## 七、FHIR 互操作映射（导出层）

| ZXPlat 运行时 | FHIR R4 |
|--------------|---------|
| `Encounter(PE)` | `Encounter`（class 扩展或 VR/AMB + type 扩展） |
| `ActivityDefinition`（套餐/项目） | `ActivityDefinition` |
| `ObservationDefinition` | `ObservationDefinition` |
| `ServiceRequest` | `ServiceRequest` |
| `Observation` | `Observation` |
| `DiagnosticReport | `DiagnosticReport |
| `Condition` | `Condition` |
| `DetectedIssue` | `DetectedIssue` |
| `ChargeItem` | `ChargeItem`（或 Account/Invoice 组合） |
| `LisWorkItem` | `Specimen` |
| `Appointment` | `Appointment` |

---

## 八、概念清单与遗漏（Gap Analysis）

### 8.1 已由 health / booking / LIS / PACS 覆盖

| 概念 | 落点 | 状态 |
|------|------|------|
| 患者 | `Patient` | ✅ |
| 体检中心/科室 | `Organization` / `Location` | ✅ |
| 体检服务号源 | `HealthcareService`（`PHYSICAL_EXAM`） | ✅ |
| 预约/到检/导检队列 | booking `Appointment` / Queue | ✅ |
| 一次完整体检 | **`Encounter(PE)`** | ✅ [DM-ADM-002 §4.1](./DM-ADM-002-Encounter-就诊.md#41-就诊类别encounterclass)；Java 枚举待补 |
| 套餐/项目目录 | `ActivityDefinition` | ✅ |
| 项目实例/加项 | `ServiceRequest` | ✅ |
| 收费/发票 | `ChargeItem` / health 收费 AS | ✅ |
| 总检报告 PDF | `DiagnosticReport + PrintTemplate（AS-BAS-PRT） | ✅ |
| 检验/检查执行 | LIS/PACS Module | ✅ |
| 执行科室规则 | `ActivityExecutionLocation` | ✅ |
| 标本类型（检验项） | `SpecimenDefinition` | ✅ |
| 团检单位/批次/名单 | booking 扩展表 | ✅ |
| 医护/分检线分组 | `Practitioner` / `ProjectGroup`→Location 或 CUS | ✅ 部分 |
| 危急值（实例） | `DetectedIssue` | ✅ |
| 影像/附件 | `DocumentReference` / `Media` | ⚠️ 需接 PEIS 诊台 |

### 8.2 成码前领域扩展索引（P0/P1）

| 概念 | 建议 | 优先级 | 说明 |
|------|------|--------|------|
| **Encounter PE class** | 扩展 DM-ADM-002 `class_enum=PE` | **P0** | ✅ [DM-ADM-002 §4.1](./DM-ADM-002-Encounter-就诊.md#41-就诊类别encounterclass) |
| **项目↔小项绑定** | **`ActivityObservationLink` CUS** | **P0** | [DM-CUS-013](./DM-CUS-013-ActivityObservationLink-项目小项绑定.md) |
| **health 通用 Observation 实例** | **[DM-CLN-007](./DM-CLN-007-Observation-临床观察实例.md)** | **P0** | ✅ 含 dept_conclusion |
| **ObservationDefinition 目录** | **[DM-CLN-005](./DM-CLN-005-ObservationDefinition-临床观察定义.md)** | **P0** | ✅ |
| **health 总检 DR 事实** | **[DM-CLN-008](./DM-CLN-008-DiagnosticReport-临床总检报告.md)** | **P0** | ✅ 表 `cln_diagnostic_report |
| **报告统一索引** | **[DM-CLN-006](./DM-CLN-006-DiagnosticReportIndex-报告读侧索引.md)** | **P0** | ✅ |
| **体检进度只读聚合** | `PeisEncounterProgressQuery` | **P0** | AS-MAS/RPT Gap |
| **项目执行/外发扩展** | `peis_service_request_ext` | **P0** | [DM-PEIS-001](./DM-PEIS-001-PeisServiceRequestExt-项目执行扩展.md) |
| **就诊扩展（团检+快照）** | `peis_encounter_ext` | **P0** | [DM-PEIS-007](./DM-PEIS-007-PeisEncounterExt-就诊扩展.md) |
| **分科结论** | **cln_observation（dept_conclusion）** | **P0** | 不建 dept_conclusion 表 |
| **常见结果模板** | `peis_common_result` | **P0** | [DM-PEIS-003](./DM-PEIS-003-PeisCommonResult-常见结果.md) |
| **自动诊断表达式** | `peis_auto_express` | **P1** | 总检 MAS-06 |
| **PDF 报告参数** | `peis_report_layout_param` | **P1** | 遗留 `PeisPdfGenParams` |
| **健康档案（既往史/习惯）** | health **`Patient` 通用摘要** + Campus **`PatientStudent`/`PatientStaff` 扩展** | **P2** | BAS-05；PEIS 读 Reader |
| **申请单模板** | PrintTemplate（AS-BAS-PRT） 或 `DocumentReference` | **P1** | CFG-006 |

### 8.3 范围外 / 增值 Module（不阻塞 P0）

| 概念 | 落点 |
|------|------|
| 职业病危害/职检史 | **暂不规划**（遗留 `occupational*`） |
| 微信/mobile | [openhis-pro-mini](../../../openhis-pro-mini/) + [zxplat-module-miniapp](../../../模块/zxplat-module-miniapp/) |
| 健康证 | health 扩展 或 PEIS P3 院点选配 |
| 体检卡 | CUS 或 ChargeItem 预存 |
| 早餐/后勤 | **不做** |
| 调查问卷选项 | `Questionnaire` / booking 扩展 |
| 学生/教职工名册 | [Campus](../../../模块/zxplat-module-campus/) · STU-* / STF-* |
| 外部订单同步 | **不做**（`SynOrderController`） |
| 操作日志 / 库备份 | whale-framework |
| 宿舍/地址 | **不做** |

### 8.4 遗留 `PeisCheckOrder` 迁移口径

| 遗留 | ZXPlat |
|------|-------|
| `peis_check_order` | **`adm_encounter`**（`class=PE`）+ 可选 `peis_encounter_ext`（团检批次、套餐快照 ID） |
| `peis_check_order_project_rel` | **`wkf_service_request`** + `peis_service_request_ext` |
| `peis_check_order_item_rel` | **`Observation`**（或 cus 过渡表） |
| `peis_check_order_diagnosis_rel` | **`peis_dept_conclusion`** 或 DR 章节 |
| `peis_check_order_review_rel` | **`Task`** + 复查 SR |
| `peis_customer` | **`Patient`**（HIS 模式） |

---

## 九、Maven 与模块边界（建议）

```text
zxplat/
  zxplat-domain/clinical/   cln_observation、cln_observation_definition、cln_diagnostic_report、cln_diagnostic_report_index
  zxplat-domain/custom/     activityobservationlink（`cus_activity_observation_link`）、ActivityExecutionLocation…
  zxplat-domain/peis/       CUS：peis_service_request_ext、diagnosis_template…
  health-application/peis/  REG/DES/MAS/REP/INT AppService（编排）
  peis-adapter/             sdpeis 兼容、第三方入站（INT-05）
```

> **无独立 `peis-starter`**：PEIS 为 zxplat **主模块**；开关 `zxplat.modules.peis.enabled=true`。Maven 落点见 [01 §4](./01-概要设计.md#4-maven)。

**禁止**：在 peis-domain 复制 `Patient`、`ChargeItem`、`ActivityDefinition` 主数据。

---

## 十、修订记录

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.0 | 2026-06-19 | 首版：Encounter(PE) 中心化；AD/OD 目录；Gap 清单 |
| v1.1 | 2026-06-19 | 一体化：**无** `peis_*_project_map`；§5 外发路由 |
| v1.2 | 2026-06-19 | **多模型联合**表达体检进度；**不扩展** Encounter PE 专用 status；BAS/移动/职业病范围收敛 |
| v1.9 | 2026-06-27 | §8.2 标题；PACS REP-07 主路径 |
| v1.8 | 2026-06-27 | PACS REP-07 主路径；去「待建」；无 peis-starter |
| v1.7 | 2026-06-27 | AOL 迁入 **DM-CUS-013**；Maven `custom/activityobservationlink` |
| v1.5 | 2026-06-27 | §5.2 增金域外送 vs 院内 INT-01 口径 |
| v1.3 | 2026-06-27 | `cln_observation`、DR index、ext 去 barcode、Maven zxplat |
