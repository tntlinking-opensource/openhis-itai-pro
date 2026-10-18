# 住院医生站 · 就诊管理 · 应用服务设计（AS）

> **文档职责**：说明住院医生站「就诊管理」（患者上下文/左侧在院患者队列）的**接口契约（IF）**、业务语义、涉及聚合与 QueryService、规则（BR）、编排意图；作为上游契约供详细设计与实现对齐。
>
> **本文必须写**：IF 编号；用户动作与读写属性；关键校验与状态变化；Gap；异常口径；事件语义；非功能约束；业务向测试要点。
>
> **本文禁止写**：具体类名/应用方法名；Request/Response 字段明细；包路径；Mapper；伪代码；写库顺序；QueryService 字段组装规则。
>
> **页面边界**：住院医生站工作台**左侧在院患者列表**——按**当前登录科室**（与住院登记科室一致）筛选、关键字检索、分页刷新、点选当前患者；**不**提供病区树、患者信息维护、待校对/待执行计数。**右侧各 Tab**（诊断、医嘱、病历等）由其他 AS 收敛（见 §1.4）。
>
> **覆盖声明**：完整契约以 **§2 IF-IP-DOR-01** 为准；列表展示字段本期**从宽**（对齐护士站富卡片并剔除任务计数），后续产品可裁剪列。
>
> **编码**：AS-IP-DOR-001 · **版本**：v1.8 · **日期**：2026-09-09

---

## 1. 场景概述

### 1.1 目标与边界


| 维度 | 说明 |
| --- | --- |
| **在内** | **本期仅读**：分页查询住院医生站可见的在院患者列表（IF-IP-DOR-01）；供全站 Tab 共用「当前选中 `encounterId`」上下文 |
| **在外** | 病区/病房树；患者信息维护（病情/医护/换床）；待校对/待执行条数与任务筛选；住院登记、分床、转科/出院写操作；诊断/医嘱/病历/报告等 Tab 业务 |
| **涉及域** | Encounter（住院状态口径）；Patient；Location（床位展示）；Organization（科室过滤）；EncounterParticipant（医护摘要）；Account（展示余额）；Condition / EncounterDiagnosis（诊断摘要装配，只读） |
| **编排权威** | 以本文 **§2、§4** 为准 |


### 1.2 角色与关键动作


| 角色 | 关键动作 |
| --- | --- |
| **住院医生** | 进入医生站 → 按本科室查看在院患者列表（默认按住院号正序）→ 关键字搜住院号/姓名/床号 → 点选患者后在右侧 Tab 诊疗 |
| **系统** | 列表限定 **IMP** 且医生站可见状态；科室维度 = **当前登录科室**（与登记时 `organization_id` 一致）；读侧排序 **`encounter_bus_no` 升序** |


### 1.3 术语与口径约定


| 术语 | 口径 |
| --- | --- |
| **就诊管理 / 患者管理** | 本 AS 即住院医生站**患者上下文**；与清单「就诊管理」同义，**不**另建患者管理 AS |
| **当前登录科室** | 登录上下文中的机构科室，与住院登记写入的 **`adm_encounter.organization_id`（当前科室）** 对齐；列表筛选 **`organization_id = 当前登录科室`** |
| **医生站可见状态** | **`status_enum IN ('IN_PROGRESS','ON_HOLD')`**：`IN_PROGRESS` = **已入科**；`ON_HOLD` = **待转科** 或 **待出院**（同一状态码，展示由QueryService `transfer_org_id` / `discharge_type_enum` 分流，见 **DM-ADM-002** §2.3.2）。**排除** `PLANNED` / `REGISTERED`（待登记/待入科）/ `PENDING` / `COMPLETED` / `DISCHARGED` / `CANCELLED` |
| **状态展示文案** | `status_enum_text` 须走 **IMP 专规**（`InpatientEncounterStatusDisplayUtil`），**禁止** `EncounterStatus#getDisplay()` |
| **关键字** | 不区分大小写模糊：**患者姓名**、**住院号**（`encounter_bus_no`）、**床号**（`bed_name`）；与护士站检索工具口径一致 |
| **排序** | 默认 **`encounter_bus_no ASC`**（住院业务号正序）；实现可次级键 `encounter_id ASC` 保证稳定 |
| **列表字段（本期从宽）** | 以 **AS-IP-NUR-001** IF-IP-NUR-02 富卡片为基线**尽量多返**，**明确剔除** `pendingAuditCount`、`pendingExecuteCount` 及任务筛选相关入参；费用/余额/诊断摘要/医护/位置等可保留，后续产品裁剪 |
| **诊断展示** | 返回 `diagnosisList` + `diagnosisSummary`（结构化装配），**禁止** API 透传 `diagnosis_json` 原文 |
| **QueryService 能力** | 列表骨架 **`InpatientEncounterContextQueryModel`**，专用查询 **Q-IPI-006** `pageDoctorStationQueue`（**禁止**复用 `pageNurseStationQueue` / `pagePrepayQueue` 再拼医生站可见状态） |


### 1.4 本期范围与非目标


| 类别 | 内容 |
| --- | --- |
| **本期（契约）** | IF-IP-DOR-01：住院医生站在院患者分页列表（只读） |
| **本期（字段）** | 列表宜返字段**从宽**（见 §4 IF-IP-DOR-01），后续版本可删列 |
| **非目标（护士站已有）** | 病区病房树；患者详情独立读接口（本期列表即入口，详情字段可后续从列表扩展或另开 IF）；患者信息维护；任务计数与任务筛选 |
| **非目标（同站其他 Tab）** | 诊断录入（**AS-IP-DOR-002**）；医嘱、病历、报告、中医等 |
| **非目标（住院链路其他角色）** | 住院登记（**AS-IP-FIN-001**）、护士分床（**AS-IP-NUR-002**）、预交金（**AS-IP-FIN-002**） |


### 1.5 领域清单（聚合 + 职责 + DM 路径）


| 聚合（域） | 在本场景中的职责（摘要） | DM 路径 |
| --- | --- | --- |
| **Encounter** | 住院就诊状态、科室、业务号 | `DM-ADM-002-Encounter-就诊.md` |
| **Patient** | 人口学、检索码 | `DM-ADM-001-Patient-患者.md` |
| **Location** | 病区/病房/床展示（读侧解析） | `DM-ADM-003-Location-位置.md` |
| **Organization** | 科室过滤 | `DM-ADM-004-Organization-机构科室.md` |
| **EncounterParticipant** | 主治/住院/责任护士等摘要 | `DM-ADM-002-Encounter-就诊.md` |
| **Account** | 库内账户余额 + **待结算合计**（展示余额组装；**不含** `PLANNED`） | `DM-ADM-009` / `DM-ADM-011` |
| **ChargeItem（读）** | `PENDING_SETTLEMENT` 待结算汇总 | `DM-ADM-011` §6.2 |
| **Condition / EncounterDiagnosis** | 诊断摘要只读装配 | `DM-CLN-003-Condition-诊断记录.md` / `DM-ADM-002` |


### 1.6 业务规则（BR-*）


| 规则 ID | 描述 | 绑定 IF |
| --- | --- | --- |
| **BR-IP-DOR-001** | 列表仅 **`class_enum = IMP`**（住院就诊） | IF-IP-DOR-01 |
| **BR-IP-DOR-002** | 可见状态固定为 **`IN_PROGRESS` + `ON_HOLD`**（已入科、待转科/待出院）；读侧内置过滤，App **不得**再拼护士站/登记队列状态 | IF-IP-DOR-01 |
| **BR-IP-DOR-003** | 地理/科室范围：**当前会话科室**须在医师**出诊管辖科室**内，且 **`organization_id = 当前会话科室`**（与住院登记科室一致）；**不**按病区树、`managedWardIds` 收缩 | IF-IP-DOR-01 |
| **BR-IP-DOR-010** | **住院医生站可见性（与门诊 BR-DOR-009 不同）**：在 **BR-IP-DOR-003** 科室前提下，**该科全部** `IN_PROGRESS`/`ON_HOLD` 在院患者均可见；**禁止**按活动 `ATTENDING_DOCTOR`/`ADMITTER`、转科发起人等活动参与者收窄；列表（Q-IPI-006）与各 Tab 写路径 Gate **同源** | IF-IP-DOR-01；**AS-IP-DOR-002/003/006** 写 Gate |
| **BR-IP-DOR-004** | 关键字模糊：姓名、住院号、床号（`SearchCodeUtil` + `lower` 列，与 **Q-IPI-005** 工具一致） | IF-IP-DOR-01 |
| **BR-IP-DOR-005** | 排序：**`encounter_bus_no ASC`**（正序） | IF-IP-DOR-01 |
| **BR-IP-DOR-006** | **禁止**返回或编排 `pendingAuditCount`、`pendingExecuteCount` | IF-IP-DOR-01 |
| **BR-IP-DOR-007** | 诊断列：`diagnosisList` + `diagnosisSummary` 装配，**禁止**透传 `diagnosis_json` | IF-IP-DOR-01 |
| **BR-IP-DOR-008** | `status_enum_text` 走 IMP 专规派生（含 `ON_HOLD` 待转科/待出院分流） | IF-IP-DOR-01 |
| **BR-IP-DOR-009** | 列表返回当前护理级别：`nursingLevelServiceRequestId`、`nursingLevelActivityId`、`nursingLevelName`、`nursingLevelStatusEnum`；经 **`InpatientPatientQueueNursingLevelEnrichAssembler`** 批量 enrich（口径同 **DM-WKF-004** §6.2.28 / 滚费 **BR-IP-ARG-009**）；无护理时上述字段为 `null` | IF-IP-DOR-01 |
| **BR-IP-DOR-011** | 列表 **`contractDisplay`（费用性质）**：有默认医保账户且 `contract_no` 有效时优先其合同名，否则登记个人现金户合同名；医嘱开立默认费用性质同口径（**AS-OP-DOR-001 BR-DOR-010** 对齐） | IF-IP-DOR-01；**AS-IP-DOR-003** |
| **BR-IP-DOR-012** | 队列下发完整 **`idCard`**（患者主档，**不脱敏**），与护士站队列同口径 | IF-IP-DOR-01 |


---

## 2. 页面与接口总览（契约概览）


| IF 编号 | 用户动作 | 接口语义（业务） | 读/写属性 | 关键规则（BR） | 关键状态变化（自然语言） | 涉及领域 | 查询服务 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| IF-IP-DOR-01 | 进入医生站、切换科室上下文、输入关键字、翻页、刷新 | 分页查询**当前登录科室**下医生站可见在院患者列表（富字段，无任务计数） | 读 | BR-IP-DOR-001～012 | 无写状态变化 | Encounter / Patient / Location / Organization / Account / Condition / ServiceRequest | InpatientEncounterContextQueryModel（**Q-IPI-006**） |


---

## 3. 缺口跟踪（Gap）

**无待跟踪缺口。**

> **GAP-IP-DOR-001（已关闭，2026-05-20）**：`EncounterQueryService#pageDoctorStationQueue` 已落地（Q-IPI-006）；DM §6.2 查询方法/§3.3 已对齐 **AS-IP-DOR-001**。
>
> **应用层（已关闭，2026-05-28）**：`InpatientDoctorStationEncounterManageAppService#pagePatientQueue` 与 **AS-IP-DOR-001-SDD** IF-IP-DOR-01 已成码对齐。

---

## 4. 接口编排说明书（业务步骤）

### IF-IP-DOR-01 查询在院患者列表（住院医生站）


| 维度 | 说明 |
| --- | --- |
| **触发前提** | 医生已登录；具备科室上下文（与住院登记科室一致） |
| **主路径步骤** | 解析当前登录 **`organizationId`** → 组装分页查询（**Q-IPI-006**：内置 IMP + `IN_PROGRESS`/`ON_HOLD` + 科室等值 + 可选 `keyword`）→ **`pageDoctorStationQueue`** 取QueryService 分页骨架（排序 **`encounter_bus_no ASC`**）→ 对**当前页** `encounterId` 批量：账户与**展示余额**（公式同 **AS-IP-FIN-002** / 护士站 **BR-IP-NUR-006**）→ **`InpatientPatientQueueNursingLevelEnrichAssembler#enrichByEncounterIds`** 批量解析护理级别（**BR-IP-DOR-009**）→ QueryService缺列由 App **批量查表/enrichment**（如病房名、费别、拼音五笔等，口径可对齐护士站 `InpatientEncounterContextQueryModelQueueDisplayAssembler`）→ 诊断摘要经 **`AdmissionPrimaryDiagnosisAssembler`**（**BR-IP-DOR-007**）→ 派生 `statusEnumText`（**BR-IP-DOR-008**）、住院天数等展示文案 → 返回分页列表 |
| **列表宜返回字段（本期从宽，SDD 可删列）** | **就诊**：`encounterId`、`encounterBusNo`（住院号）、`statusEnum`、`statusEnumText`、`priorityEnum`、入院/入科时间、住院天数（派生）；**患者**：`patientId`、`patientName`、`genderEnum`、`ageText`、**`idCard`（完整号，不脱敏，BR-IP-DOR-012）**、`pinyinCode`、`wubiCode`；**位置**：`wardId`/`wardName`、`houseName`、`bedId`/`bedName`；**科室**：`organizationId`/`organizationName`；**费别**（有则返）；**费用**：`totalAmount`、`paidAmount`、`arrearsAmount`、`accountBalance`、`lowBalanceWarning`；**医护摘要**：主治/住院医师/责任护士姓名（有则返）；**诊断**：`diagnosisList`、`diagnosisSummary`；**护理级别**：`nursingLevelName`、`nursingLevelStatusEnum`（及 `nursingLevelServiceRequestId`/`nursingLevelActivityId`，**BR-IP-DOR-009**）；**不含**：`pendingAuditCount`、`pendingExecuteCount` |
| **关键分支** | 无关键字时返回本科室全部可见状态患者；列表为空显示空状态 |
| **失败分支** | 查询不可用 → EX-IP-DOR-101 |
| **查询侧说明** | 纯展示；随登记、分床、转科、出院、诊断等领域事件载荷刷新 |


---

## 5. 业务异常与用户提示


| 语义代码 | 场景 | 用户提示方向 | 可重试 |
| --- | --- | --- | --- |
| EX-IP-DOR-101 | 列表查询失败 | 加载失败，请稍后重试 | 是 |
| EX-IP-DOR-102 | 未选择科室上下文 | 请先选择科室 | 是 |
| EX-IP-DOR-103 | 就诊不在当前会话科室医生站可见范围（**BR-IP-DOR-010**） | 当前患者不在本科室可见范围，请刷新左侧列表 | 是 |


---

## 6. 事件语义与消费方


| 事件语义（DM 英文名以 DM 为准） | 列表侧影响（间接） |
| --- | --- |
| `InpatientAdmissionCompletedEvent` / `InpatientBedAssignedEvent` | 新患者入列或状态→已入科 |
| 转科/出院过渡类事件（`ON_HOLD` + `transfer_org_id` / `discharge_type_enum`） | 状态文案→待转科/待出院 |
| `EncounterDiagnosisAssociationUpsertedEvent` 等诊断类事件 | `diagnosisSummary` 刷新 |
| `AccountBalanceEditedEvent` | 展示余额、欠费预警刷新 |


> 本 IF **不发布**领域事件；仅消费投影结果。

---

## 7. 非功能性需求


| 类别 | 要求 |
| --- | --- |
| **性能（经验目标）** | IF-IP-DOR-01 ≤1000ms（含本页批量 enrichment） |
| **幂等** | 读接口天然幂等 |
| **并发** | 列表与右侧 Tab 写操作并发时，以就诊最新投影为准 |
| **一致性** | 科室过滤与登录科室、登记科室语义一致，避免「看见非本科室患者」 |
| **可裁剪** | 列表列集允许后续版本删减，但**不得**引入任务计数列 |


---

## 8. 测试要点（业务视角）


| 类别 | 要点 |
| --- | --- |
| **正向** | 本科室多条患者；`encounter_bus_no` 正序；关键字命中姓名/住院号/床号；`IN_PROGRESS` 显示已入科；`ON_HOLD`+`transfer_org_id` 显示待转科；`ON_HOLD`+`discharge_type_enum` 显示待出院 |
| **边界** | 无患者空列表；关键字无匹配；排除 `REGISTERED`/`PLANNED` 待入科患者 |
| **规则** | 不返回待校对/待执行计数；不透传 `diagnosis_json` |
| **回归** | 与护士站列表同患者时，诊断摘要/余额数字一致（除护士站多任务列） |


---

## 9. 关联文档与变更历史

### 9.1 关联文档


| 文档 |
| --- |
| `docs/领域/DomainModel/DM-ADM-002-Encounter-就诊.md` |
| `docs/领域/DomainModel/DM-ADM-001-Patient-患者.md` |
| `docs/领域/DomainModel/DM-ADM-003-Location-位置.md` |
| `docs/领域/DomainModel/DM-ADM-004-Organization-机构科室.md` |
| `DM §6.2（DM-ADM-002 §6.2 EncounterQueryService（InpatientEncounterContextQueryModel）`（**Q-IPI-006**） |
| `docs/应用/zxplat-application/AppService/AS-IP-NUR-001-住院护士站患者管理.md`（列表字段基线） |
| `docs/应用/zxplat-application/AppService/AS-IP-DOR-002-住院医生站诊断录入.md |
| `docs/领域/专题（SRS 待迁）/系统需求规格说明书-SRS.md`（FR-INPAT-005） |


### 9.2 变更历史


| 版本 | 日期 | 变更摘要 |
| --- | --- | --- |
| v1.8 | 2026-09-09 | **BR-IP-DOR-012**：队列下发完整 `idCard`（不脱敏，与护士站同口径） |
| v1.7 | 2026-07-18 | **BR-IP-DOR-011**：`contractDisplay` 有医保账户优先医保合同名；与门诊 **BR-DOR-010**、医嘱默认费用性质同口径 |
| v1.6 | 2026-07-09 | **BR-IP-DOR-010**：住院医生站可见性明确为**出诊科室全员**（不按主治/转科参与者收窄，与门诊 **BR-DOR-009** 不同）；新增 **EX-IP-DOR-103**；诊断/医嘱/中医 Tab 写 Gate 与 Q-IPI-006 同源 |
| v1.5 | 2026-07-04 | **护理级别列表 enrich**：**BR-IP-DOR-009**；IF-IP-DOR-01 返回 `nursingLevel*` 四字段；经 **`InpatientPatientQueueNursingLevelEnrichAssembler`** + **DM-WKF-004** §6.2.28 |
| v1.4 | 2026-06-21 | **展示文案简化**：患者队列仅展示单一余额；对齐 **AS-IP-FIN-002** v2.1 |
| v1.3 | 2026-06-19 | **账户余额语义**：展示 = 库内 balance − 待结算；对齐 **AS-IP-FIN-002** / **BR-IP-NUR-006** |
| v1.2 | 2026-05-28 | 读模型废止后对齐 zxplat 实现（EncounterContextFacade / querysupport Loader） |（已迁至 EncounterContextReader / application.shared.encounter）
| v1.1 | 2026-05-20 | 关闭 **GAP-IP-DOR-001**：`pageDoctorStationQueue` 读侧已落地 |
| v1.0 | 2026-05-20 | 初版：IF-IP-DOR-01 只读列表；科室=登录科室；可见状态 IN_PROGRESS+ON_HOLD；bus_no 正序；字段从宽且无任务计数；Gap Q-IPI-006 |


---

**文档负责人**：（待填） · **评审**：（待填）
