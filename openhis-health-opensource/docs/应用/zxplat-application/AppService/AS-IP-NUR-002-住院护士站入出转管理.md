# 住院护士站 · 入出转管理 · 应用服务设计（AS）

> **文档职责**：入出转右侧 **「入科」「出」** Tab 契约——**床位列表**（占床/空床）、**分床/换床/互换**（入科写）、**出院前待办**（待执行/待发·待退药读）、**办理出院**与**清床**（写）。患者树/列表/详情/维护在 **AS-IP-NUR-001**；**校对出院医嘱 → 待出院**在 **AS-IP-NUR-003**；**出院结算 → 已结算**在 **AS-IP-FIN-003**。
>
> **页面边界**：与左侧病区树同范围；入科 Tab 拖放分床/换床/互换；出 Tab 选占床患者 → 看待办 → 门禁通过后**出院** → 收费员结算后**清床**；患者详情一律 **IF-IP-NUR-03**。
>
> **状态权威**：住院 `IMP` 状态机以 **`DM-ADM-002` §2.3.1～2.3.2** 为准（本 AS 不重复造态）。
>
> **编码**：AS-IP-NUR-002 · **版本**：v2.26 · **日期**：2026-09-23

**配套文档**

| 类型 | 路径 |
| --- | --- |
| 应用层 SDD | `04-架构设计/软件详细设计-SDD/AppService-SDD/AS-IP-NUR-002-住院护士站入出转管理-SDD.md` |
| 查询服务 | `DM §6.2（DM-ADM-002 §6.2 EncounterQueryService（InpatientEncounterContextQueryModel）` |
| 领域事件 | `DM §6.2（DM-ADM-002 §6.2 EncounterQueryService` |
| 成码包 | `zxplat/.../nursestation/ATDManager` · `NurseStationATDManagerAppService` |

---

## 1. 场景概述

### 1.1 目标与边界

| 维度 | 说明 |
| --- | --- |
| **在内** | IF-IP-NUR-ATD-01 床位列表（**入科 / 出** 面板）；IF-IP-NUR-ATD-02 分床/换床/互换（写；**ASSIGN 含入院体征写**）；IF-IP-NUR-ATD-03 出院前**待执行**医嘱列表（读）；IF-IP-NUR-ATD-04 出院前**待发/待退药**列表（读）；IF-IP-NUR-ATD-05 **办理出院**（写）；IF-IP-NUR-ATD-06 **清床**（写）；IF-IP-NUR-ATD-06a **缺追溯码软提示**（读） |
| **已迁出** | ~~IF-IP-NUR-ATD-07 出院召回~~ → **AS-IP-FIN-004** **IF-IP-RCL-01**（收银台已结算历史；**非**护士站写入口） |
| **在外** | 患者树/列表/详情/维护（**AS-IP-NUR-001**）；**出院召回写**（**AS-IP-FIN-004** **IF-IP-RCL-01**，收银台）；**入院体征读回填**与**仅维护不换床写**（**001** IF-IP-NUR-03/04）；**转出** Tab（转科确认写，另期）；开立/签发出院医嘱（**AS-IP-DOR-003**）；**校对**出院医嘱及 `IN_PROGRESS→ON_HOLD`（**AS-IP-NUR-003** BR-IP-AUD-011）；医嘱执行写操作（**AS-IP-NUR-004**）；住院发药作业（**AS-IP-PHA-001**）；住院结算（**AS-IP-FIN-003**）；**反结/召回**（**AS-IP-FIN-004**）；**文书** Tab（**AS-IP-NUR-007/014**） |
| **涉及域** | Location；**`adm_encounter_location`**；Encounter；Patient；**VitalSign**（**DM-DIA-002**）；术语 **`vital_signs_code`**；**`InpatientEncounterContextQueryModel` QueryService**；**`adm_encounter_participant`**；MedicationRequest / ServiceRequest；Procedure；MedicationDispense |
| **编排权威** | 入科写：`DM-ADM-002` §6.1.17～18a + `DM-ADM-003` 床态；校对出院：`§6.1.21`；办理出院：`§6.1.22`；结算：`§6.1.23`（**AS-IP-FIN-003**）；清床：`§6.1.24` + 应用层 `releaseBed`；**出院召回**：**AS-IP-FIN-004** **IF-IP-RCL-01**（~~本 AS ATD-07~~ 已废止） |

### 1.2 角色与关键动作

| 角色 | 关键动作 |
| --- | --- |
| **病区护士** | **入科**：床位图 → 分床抽屉填写**入院体征**（可选）与责任医护 → **首次分床**/**换床/互换**。**出**：看待办 → 停嘱/执行/发药/退药清零 → **出院** →（收费员结算后）**清床**（**无活动床**全结轨**跳过**清床） |
| **系统** | 按 Tab 过滤占床患者；出院门禁；推进就诊态；清床收口位置/参与者/床资源；刷新 001 列表与床位图。**出院召回**由收银台 **FIN-004 IF-IP-RCL-01** 处理 |

### 1.3 术语与口径约定

| 术语 | 口径 |
| --- | --- |
| **床位列表** | **以床为单元**：位置块 + 占床患者块（`IN_USE` 时）。范围内**全部** `ACTIVE` 床位；**不含**未分床患者（在 **001 列表**） |
| **面板（panel）** | `admission` = 入科 Tab；`discharge` = 出 Tab。同一 IF-IP-NUR-ATD-01，**地理范围**一致，**占床患者过滤**不同（见 **BR-IP-ATD-010**） |
| **出 Tab 可见患者** | `IMP` 且（`ON_HOLD` 且 **待出院** 分流）或 `PENDING` 或 `COMPLETED`。**排除**：`ON_HOLD`+`transfer_org_id`（待转科 → **转出** Tab，本期不做）、`IN_PROGRESS`/`REGISTERED`/`DISCHARGED` 等。分流规则同 **DM-ADM-002` §2.3.2**、`InpatientEncounterStatusDisplayUtil` |
| **待出院** | 校对出院类诊疗医嘱后就诊 `ON_HOLD`；展示「待出院」。**办理出院**前置态 |
| **待结算 / 已结算（在院）** | `PENDING` / `COMPLETED`（**财务终态，人仍可占床**）；出 Tab **仍展示**直至清床 → `DISCHARGED` |
| **护士站整站可见** | 与 **AS-IP-NUR-001** §1.3 一致：`REGISTERED`～`COMPLETED`；**不含** `DISCHARGED`（清床后自护士站消失） |
| **占床患者信息** | 活动 **`adm_encounter_location`（床位级）** 反查 + **InpatientEncounterContextQueryModel** 组装（与 001 卡片同口径） |
| **范围联动** | 与 **IF-IP-NUR-01/02** 同一 `wardId`/`houseId`（**BR-IP-NUR-003**） |
| **首次分床 / 换床 / 互换** | 同 v1.x：**BR-IP-ATD-002～005**；仅 `panel=admission` 可提交 ATD-02 |
| **入院体征** | 入科分床抽屉可选录入：身高、体重、体温、心率、脉搏、收缩压/舒张压；**持久化** `dia_vital_sign`（**DM-DIA-002**）；**写**：**仅** ATD-02 **`ASSIGN`** 成功路径（**BR-IP-ATD-020**）；**读/仅维护写**见 **001** IF-IP-NUR-03/04；与后续三测单/护理记录共用同一事实表 |
| **办理出院** | 待出院 `ON_HOLD` → **`PENDING`**；**仅 `ON_HOLD` 可点**（`PENDING` 幂等由后端处理，前端禁用重复提交）；须满足 **BR-IP-ATD-011**；`end_time` 可取已校对出院医嘱计划时刻（无则服务端当前时刻） |
| **取消办理出院** | 待结算 `PENDING` → **`ON_HOLD`**（仍有有效出院嘱）或 **`IN_PROGRESS`**（出院嘱已撤尽）；**仅 `PENDING` 可点**；清空 `end_time`；**不**撤嘱、**不**反结；已结算须走 **FIN-004** |
| **清床** | 仅 **`COMPLETED`**（**AS-IP-FIN-003** 出院全结后）；前端 **仅 `COMPLETED` 启用清床按钮**；**展示账户余额须为 0**（同 **BR-IP-NUR-006** / `InpatientDisplayAccountBalanceAssembler`）；`clearDischargedBed` + 活动床 **`releaseBed`** → **`DISCHARGED`** |
| **待执行（出院门禁）** | 已校对/已停嘱（`VERIFIED`/`STOPPED`）医生开立药品/诊疗；**长期**须已停嘱且停嘱时刻前**无待执行槽位**（槽位判定与 **AS-IP-NUR-004 Q-IP-EXE-001** / **BR-IP-EXE-035** 同源；一盒一点硬顶空解析=无点不拦）；**临时**须整单净态已完结（与 **BR-IP-EXE-006/008** 同源）。编排**禁止**含转科/出院/护理类诊疗 |
| **待发/待退药（出院门禁）** | 该就诊下药品发放 `DispenseStatus ∈ { PREPARATION, SUMMARIZED, PENDING_REFUND }`（**不含** `IN_PROGRESS` 作门禁，配药中由发药站处理）；编排可查 **MedicationDispense** 读侧 |
| **读侧** | 床位图 ATD-01；待办 ATD-03/04；详情 **IF-IP-NUR-03** |
| **阻塞原因码（ATD-03）** | `LONG_NOT_STOPPED`（长期未停嘱）、`LONG_PENDING_SLOTS`（长期已停嘱仍有待执行时点）、`TEMP_NOT_COMPLETED`（临时未完结）；常量见 `InpatientNurseStationConstants.DISCHARGE_BLOCKING_*` |
| **pendingPlanTimes（ATD-03）** | 仅 `blockingReason=LONG_PENDING_SLOTS` 回传；与门禁 / **Q-IP-EXE-001** pending 槽 **同源**（`listLongTermStoppedPendingPlanTimes`）；其它阻塞原因空列表；**禁止**另算日频次点。出 Tab 可据此勾选批量不执行（复用 **IF-IP-EXE-04**） |
| **反结** | 按**单笔成功结算单**逆向；**收银台已结算历史**（**AS-IP-FIN-004** IF-IP-RVS-01）。**未清床**（`COMPLETED` 仍占床）在**收银台**办理，**不叫召回** |
| **出院召回** | **产品写入口在收银台**（**AS-IP-FIN-004** **IF-IP-RCL-01**）；**仅** **`DISCHARGED`**；**禁止**护士站列表/ATD 发起。召回 = 反结内核（`allowDischarged=true`）+ `DISCHARGED→IN_PROGRESS` + INSERT 活动 **WARD**（**无**活动床；`requiresBedAssignment=false`） |
| **召回 vs 反结** | **反结不依赖召回**；未离院（`COMPLETED`）只能**反结**（IF-IP-RVS-01），不能**召回** |

### 1.4 本期范围与非目标

| 类别 | 内容 |
| --- | --- |
| **本期** | IF-IP-NUR-ATD-01～06 |
| **非目标** | **转出** Tab（转科确认、`initiateDepartmentTransfer` / `confirmDepartmentTransfer` 写，另期 AS 或 002 续章）；入科办理上下文读接口；**仅编辑不换床**（**001** IF-IP-NUR-04，含体征维护）；未分床区在床位列表；`atdManageInit`；体温单/护理记录 CRUD（**AS-IP-NUR-007/014**）；住院结算/反结页 UI 细节（**AS-IP-FIN-003/004**）；**未清床反结**收银台编排细节（归 **AS-IP-FIN-004**）；发药/执行**写**（仅出院前**读**待办）；召回后自动复开医嘱/自动恢复医护 |

### 1.8 出 Tab 端到端链路（跨 AS）

| 序号 | 角色/系统 | AS | 就诊态变化 | 床位/QueryService要点 |
| --- | --- | --- | --- | --- |
| 1 | 医生开立出院医嘱 | **AS-IP-DOR-003** | — | — |
| 2 | 护士校对出院医嘱 | **AS-IP-NUR-003** | `IN_PROGRESS` → `ON_HOLD`（待出院） | `discharge_type_enum` 写入；`InpatientEncounterContextQueryModel` 展示「待出院」 |
| 3 | 护士办理出院 | **本 AS ATD-05** | `ON_HOLD`（待出院）→ `PENDING`（待结算） | `end_time` 写入；**仍占床**；出 Tab 仍可见 |
| 3′ | 取消办理出院（可选逆向） | **本 AS ATD-08** | `PENDING` → `ON_HOLD` 或 `IN_PROGRESS` | 清 `end_time`；有有效出院嘱回待出院，无嘱回已入科；再走步骤 2/3 |
| 4 | 收费员出院全结 | **AS-IP-FIN-003** | `PENDING` → **`COMPLETED`**（有活动床）或 **`DISCHARGED`**（无活动床，召回未占床轨） | 有床仍占床；无床**跳过**步骤 5 |
| 5 | 护士清床 | **本 AS ATD-06** | `COMPLETED` → `DISCHARGED` | **仅**步骤 4 为 `COMPLETED` 时执行；`releaseBed` |

> 护士站在步骤 2 之后、步骤 5 之前：患者可在出 Tab 选床格 → ATD-03/04 看待办 → ATD-05 出院（须过 **BR-IP-ATD-011**）。

### 1.10 财务回退与出院召回（跨 AS，与 §1.8 逆向）

#### 1.10.1 未清床：反结（收银台，**非召回**）

| 序号 | 角色/系统 | AS | 前置 `status_enum` | 反结后要点 |
| --- | --- | --- | --- | --- |
| 0 | 收费员在结算页选已结支付单 | **AS-IP-FIN-004** | `COMPLETED`（**仍占床**） | — |
| 1 | **反结**（按所选结算单） | **AS-IP-FIN-004** | 同上 | 支付/ChargeItem **复制再生**；**撤销出院医嘱**；出院全结单则 `COMPLETED→IN_PROGRESS`；**保留**活动床/参与者 |
| 2 | — | — | `IN_PROGRESS` | 患者回在院流程；须重新校对出院嘱 → ATD-05 办理出院 → 再结算 |

> **硬约束**：`COMPLETED` 且未清床 **禁止**走出院召回（须收银台 **IF-IP-RVS-01** 反结）。

#### 1.10.2 已清床离院：出院召回（**收银台**，非护士站）

| 序号 | 角色/系统 | AS | 前置 `status_enum` | 召回后 `status_enum` | 位置/床要点 |
| --- | --- | --- | --- | --- | --- |
| 0 | 收费员在**已结算**历史选 `DISCHARGED` 患者 | **AS-IP-FIN-004** IF-IP-STL-07 | **`DISCHARGED`** | — | **`recallEnabled=true`** |
| 1 | **召回**（内嵌反结 + 位置恢复） | **IF-IP-RCL-01** `recallInpatientDischarge` | `DISCHARGED` | **`IN_PROGRESS`** | INSERT 活动 **WARD**；**无**活动床；`requiresBedAssignment=false` |
| 2 | — | — | `IN_PROGRESS` | 须重新校对出院嘱 → ATD-05 → 再结算；**无床**二次全结直接 **`DISCHARGED`** |

> **硬约束**：~~**IF-IP-NUR-ATD-07**~~ 已废止；~~`RecallReverseSettleSupport`~~ 已删除；召回写统一在 **`InpatientSettleAppService`**。

### 1.9 出 Tab 可见性与读接口门禁（BR-IP-ATD-010）

**可见（占床患者块仍展示）**：`status_enum` 为下列之一，且非 `DISCHARGED`：

| 条件 | 说明 |
| --- | --- |
| `ON_HOLD` + 待出院 | `transfer_org_id` 为空且（`discharge_type_enum` 非空 **或** 主档 `out_way_code` 非空） |
| `PENDING` | 已办理出院、待财务全结 |
| `COMPLETED` | 已财务全结、待清床 |

**不可见**：`IN_PROGRESS`/`REGISTERED`（未进入出院流程）、`ON_HOLD`+`transfer_org_id`（待转科，**转出** Tab 另期）、`DISCHARGED`（已清床）。

**读接口统一 Pre**（ATD-03/04/05/06）：`EncounterContextReader#mustGetInpatientContextByEncounterId` + App 内联 **BR-IP-ATD-010**（实现 **`mustGetInpatientContextForDischargeTab`**）；不满足时映射各 IF 失败码（103/104/304），**禁止**用QueryService作写前唯一真源。

### 1.5 领域清单

| 聚合 / 表 | 职责 | DM |
| --- | --- | --- |
| **Location（床位）** | 床号、病房、`operational_enum` | `DM-ADM-003` |
| **`adm_encounter_location`** | 占床关联；清床收口活动行 | `DM-ADM-002` §3.3 |
| **Encounter** | 状态迁移；清床 | `DM-ADM-002` §2.3.1、§6.1.21～22 |
| **Patient** | 人口学 | `DM-ADM-001` |
| **`InpatientEncounterContextQueryModel`（QueryService）** | 列表/床位图展示；`transfer_org_id` / `discharge_type_enum` 分流 | DM §6.2 查询方法 |
| **`adm_encounter_participant`** | 分床/清床收口 | `DM-ADM-002` §3.2 |
| **MedicationRequest / ServiceRequest** | 待执行门禁与 ATD-03 | `DM-MED-003` / `DM-WKF-004` |
| **Procedure** | 临时医嘱执行终态 | `DM-CLN-004` |
| **MedicationDispense** | 待发/待退药门禁与 ATD-04 | `DM-MED-002` |
| **VitalSign** | 入院体征事实行（EAV）；时点覆盖写 | `DM-DIA-002` |

### 1.7 位置与参与者轨迹存储契约

| 子表 | 规则 |
| --- | --- |
| **禁止 UPDATE** | 换人、换位置：**旧行** `COMPLETED` + **`end_time`** → **INSERT** 新 `ACTIVE` |
| **`adm_encounter_location`** | **WARD** 不动；**HOUSE+BED** 活动至多一条；清床：**全部**活动位置 `markCompleted` |
| **`adm_encounter_participant`** | 分床全量收口+INSERT；**001** 按角色换人；清床：**全部**活动参与者 `markCompleted` |
| **床资源** | 分床/换床/互换：`occupyBed` / `releaseBed`；清床：**须**对活动床 `releaseBed`（与 `clearDischargedBed` 同事务编排） |

### 1.6 业务规则（BR-*）

| 规则 ID | 描述 | 绑定 IF |
| --- | --- | --- |
| **BR-IP-ATD-001** | 床位列表：范围内**全部** `ACTIVE` 床位；**空床/占床判定以 `adm_location.operational_enum` 为准**（`NOT_IN_USE`=空床、`IN_USE`=占床）；**禁止**仅凭活动 `adm_encounter_location` 有无决定空床；`IN_USE` 且反查到就诊时必返占床患者块（经 **BR-IP-ATD-010** 过滤后可为 null）；`NOT_IN_USE` 时占床块**强制**为空；占床块批量装载**须** `EncounterContextReader#listInpatientContextsByEncounterIdsOrdered`（**禁止** `listInpatientEncounterContexts`+`encounterIdsIn`，否则会套用登记列表默认状态过滤漏 `ON_HOLD`/`PENDING`/`COMPLETED`） | IF-IP-NUR-ATD-01 |
| **BR-IP-ATD-009** | 占床反查活动床位级 `adm_encounter_location`（**仅**用于 `IN_USE` 床组装患者块）；一床一患；`IN_USE` 无活动位置须告警；`NOT_IN_USE` 仍有活动位置须告警且不得展示占床块 | IF-IP-NUR-ATD-01 |
| **BR-IP-ATD-010** | **`panel=discharge`**：占床患者仅当就诊满足 §1.3「出 Tab 可见患者」；**`panel=admission`**：不过滤（凡占床在院患者均展示，含 `IN_PROGRESS` 等） | IF-IP-NUR-ATD-01 |
| **BR-IP-ATD-002** | 首次分床：`IMP+REGISTERED`、目标床 `NOT_IN_USE` 等（同 v1.x） | IF-IP-NUR-ATD-02 |
| **BR-IP-ATD-003** | 首次分床：`occupyBed` → `assignInpatientBed`（同事务）→ `IN_PROGRESS` | IF-IP-NUR-ATD-02 |
| **BR-IP-ATD-004** | 换空床：`confirmBedTransfer` + `releaseBed`/`occupyBed` | IF-IP-NUR-ATD-02 |
| **BR-IP-ATD-005** | 互换：`swapInpatientBeds` + 床态对调 | IF-IP-NUR-ATD-02 |
| **BR-IP-ATD-006** | 写操作幂等：`encounterId+targetBedId+业务类型` | IF-IP-NUR-ATD-02 |
| **BR-IP-ATD-007** | 占床余额展示公式同 **BR-IP-NUR-006**（可选） | IF-IP-NUR-ATD-01 |
| **BR-IP-ATD-008** | 目标床 `IN_USE` 禁止普通分床/换床 | IF-IP-NUR-ATD-02 |
| **BR-IP-ATD-011** | **出院门禁**（对齐老系统 `hospitalDischarge`）：① 存在**长期**未停嘱 → 拒绝；①′ 存在**长期已停嘱**且停嘱时刻前仍有待执行槽位（与 **Q-IP-EXE-001** / **BR-IP-EXE-035/036** 切批 + **BR-IP-EXE-038** 剩余按天抑制 **同源**：`listLongTermStoppedPendingPlanTimes` 非空才拦；硬顶空解析或剩余仍够抑制后**无假 pending**；**预计领用日补点须认不执行终态**，禁止不执行后仍补假 pending 误拦）→ 拒绝；② **临时**医嘱净态仍为 pending（与 **BR-IP-EXE-006/008** 同源；诊疗仅计**护士侧** Procedure；**执行科室 `class_enum=TECH` 的临时诊疗不计入本条**，改由⑤）→ 拒绝；③ 存在 `PREPARATION`/`SUMMARIZED`/`PENDING_REFUND` 药品发放 → 拒绝；④ 就诊须 **`ON_HOLD` 且待出院**（已校对出院医嘱，**非** `IN_PROGRESS`）；⑤ **医技执行**：医生开立且执行科室 `class_enum=TECH` 的诊疗须有该执行科医技侧净剩 COMPLETED Procedure；耗材 `medical-tech-pricing` 在自动扣库关时须发放 `COMPLETED`。**不再**读 `fee_confirmer_*`。真 pending 须执行或不执行（出 Tab / **IF-IP-EXE-04**） | IF-IP-NUR-ATD-05 |
| **BR-IP-ATD-012** | **办理出院**：门禁通过后 `ON_HOLD(待出院)→PENDING`；写 **`end_time`**（出院医嘱计划时刻优先） | IF-IP-NUR-ATD-05 |
| **BR-IP-ATD-025** | **取消办理出院**：仅 `IMP+PENDING`；有生效态出院嘱（`ACTIVE`/`VERIFIED`/`COMPLETED`）→ `ON_HOLD` 保留 `out_way_code`；否则 → `IN_PROGRESS` 清 `out_way_code`；均清 `end_time`；与收银台结算并发时非 `PENDING` 拒绝（**EX-IP-ATD-310**） | IF-IP-NUR-ATD-08 |
| **BR-IP-ATD-013** | **清床**：仅 `IMP+COMPLETED`；`clearDischargedBed`；活动床 **`releaseBed`**；就诊 → `DISCHARGED` | IF-IP-NUR-ATD-06 |
| **BR-IP-ATD-016** | **清床余额门禁**：**展示余额**（库内 `balance` − 待结算，同 **BR-IP-NUR-006**）**须为 0**；非零拒绝清床 | IF-IP-NUR-ATD-06 |
| **BR-IP-ATD-020** | **清床未冲销销售门禁**：存在成功清床汇总 3505 且对应 3506 未成功时拒绝再清床（**EX-IP-ATD-404**） | IF-IP-NUR-ATD-06 |
| **BR-IP-ATD-021** | **缺追溯码软提示**：已核发正向行缺 `trace_no` 时前端 tip（发药日+药名），**不硬拦**办理出院/清床 | IF-IP-NUR-ATD-06a |
| **BR-IP-ATD-014** | **待执行列表**：该就诊下已校对/已停嘱医生开立医嘱；**排除**转科/出院/护理类诊疗；阻塞项同 **BR-IP-ATD-011**①/①′/②；`LONG_PENDING_SLOTS` 行须回传 **`pendingPlanTimes`**（与①′同源） | IF-IP-NUR-ATD-03 |
| **BR-IP-ATD-015** | **待发/待退药列表**：该就诊下发放状态 ∈ `PREPARATION`、`SUMMARIZED`、`PENDING_REFUND` | IF-IP-NUR-ATD-04 |
| **BR-IP-ATD-017** | ~~**出院召回门禁**~~ **已迁** **AS-IP-FIN-004** **BR-IP-RCL-001** | ~~IF-IP-NUR-ATD-07~~ |
| **BR-IP-ATD-018** | ~~**召回内嵌反结**~~ **已迁** **BR-IP-RCL-002** | ~~IF-IP-NUR-ATD-07~~ |
| **BR-IP-ATD-019** | ~~**召回位置恢复**~~ **已迁** **BR-IP-RCL-003**（`DISCHARGED→IN_PROGRESS`+WARD，无床） | ~~IF-IP-NUR-ATD-07~~ |
| **BR-IP-ATD-020** | **入院体征写时机**：**仅** ATD-02 **`operationType=ASSIGN`**（首次分床）在 `occupyBed` + `assignInpatientBed` **成功后**写入；**TRANSFER** / **SWAP** **不写**（对齐老系统换床/互换不改入院体征） | IF-IP-NUR-ATD-02 |
| **BR-IP-ATD-021** | **入院体征记录时点**：`recorded_at` = 分床后就诊 `start_time`（`transitToInProgress` 时刻，与入科时间一致）；**禁止**使用登记时刻或 Request 传入自定义时刻 | IF-IP-NUR-ATD-02 |
| **BR-IP-ATD-022** | **入院体征项编码**：须为术语体系 **`vital_signs_code`** 启用 code；首版项：`HEIGHT`、`WEIGHT`、`TEMP`、`HR`、`PULSE`、`SBP`、`DBP`；**禁止**合并血压串（与老系统 `doc_statistics` 不同，ZXPlat 收缩压/舒张压各一行） | IF-IP-NUR-ATD-02 |
| **BR-IP-ATD-023** | **时点覆盖写 merge**：调用 `saveVitalSign` 前，Support **须**加载该 `encounter_id` + `recorded_at` + `type_enum=TEMPERATURE_SHEET` 已有主表及明细，与本次提交非空项 **merge** 后再提交（**DM-DIA-002 BR-VS-005**：未出现在提交明细列表的 code 会被软删） | IF-IP-NUR-ATD-02 |
| **BR-IP-ATD-024** | **入院体征可选**：用户一项均未填时 **跳过** 领域写；体温写明细 `term_code=vital_signs_code`、`item_code=TEMP`，其余写明细；`location_id` 取目标床 `bedLocationId`；`recorder_id` 取当前登录医务人员 | IF-IP-NUR-ATD-02 |

---

## 2. 页面与接口总览（契约概览）

| IF 编号 | 用户动作 | 接口语义 | 读/写 | 关键规则 | 状态变化 | 涉及领域 |
| --- | --- | --- | --- | --- | --- | --- |
| IF-IP-NUR-ATD-01 | 进入入科/出 Tab、与左侧同范围 | **床位列表**（`panel`：入科/出） | 读 | BR-IP-ATD-001、007、009、010 | 无 | Location；EncounterLocation；InpatientEncounterContextQueryModel |
| IF-IP-NUR-ATD-02 | 入科 Tab 确认分床、换床/互换 | **分床/换床/互换**（**ASSIGN** 可携入院体征） | 写 | BR-IP-ATD-002～008、020～024 | `REGISTERED→IN_PROGRESS`；床态；**ASSIGN** 可选写 `dia_vital_sign` | Encounter；Location；VitalSign |
| IF-IP-NUR-ATD-03 | 出 Tab 选患者查看待执行 | **出院前待执行医嘱**列表 | 读 | BR-IP-ATD-014 | 无 | Med/Service Request；Procedure |
| IF-IP-NUR-ATD-04 | 出 Tab 选患者查看待发药 | **出院前待发/待退药**列表 | 读 | BR-IP-ATD-015 | 无 | MedicationDispense |
| IF-IP-NUR-ATD-05 | 出 Tab **出院** | **办理出院** | 写 | BR-IP-ATD-011、012 | `ON_HOLD(待出院)→PENDING` | Encounter |
| IF-IP-NUR-ATD-08 | 出 Tab **取消办理出院** | **取消办理出院** | 写 | BR-IP-ATD-025 | `PENDING→ON_HOLD` 或 `PENDING→IN_PROGRESS` | Encounter |
| IF-IP-NUR-ATD-06 | 出 Tab **清床** | **清床离院** | 写 | BR-IP-ATD-013、016、020 | `COMPLETED→DISCHARGED`；释床 | Encounter；Location；EncounterLocation；Participant |
| IF-IP-NUR-ATD-06a | 出 Tab **清床前** | **缺追溯码软提示列表** | 读 | BR-IP-ATD-021 | 无状态变更 | MedicationDispense；MedicationDefinition |

> ~~**IF-IP-NUR-ATD-07**~~ 出院召回已迁至 **AS-IP-FIN-004** **IF-IP-RCL-01**（收银台已结算历史）。

---

## 3. 缺口跟踪（Gap）

| Gap ID | 类型 | 目标 | 缺什么 | 影响 IF | 优先级 |
| --- | --- | --- | --- | --- | --- |
| — | — | — | **无开放 Gap** | — | — |

> **说明（非 Gap）**：`clearDischargedBed`、`completeInpatientFinancialSettlement` 领域已实现；`releaseBed` 由本 AS 应用编排（§4 ATD-06）。**应用层 ATD-01～06** 已落地。**出院召回写**已迁至 **FIN-004 IF-IP-RCL-01**（~~ATD-07~~ / ~~`RecallReverseSettleSupport`~~ 已废止）。

---

## 4. 接口编排说明书（业务步骤）

### IF-IP-NUR-ATD-01 床位列表（入科 / 出）

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 进入入科或出 Tab；`wardId`/`houseId` 与左侧树一致；请求带 **`panel`**（`admission` \| `discharge`） |
| **主路径步骤** | ① 收集范围内全部床位（同 v1.x：`listBedsUnderParent`、`busNo` 排序、`houseName` 批量解析）。② 批量反查活动床位级 `adm_encounter_location`。③ **仅 `operational_enum=IN_USE` 的床位**将其占床就诊 id 经 **`listInpatientContextsByEncounterIdsOrdered`** 批量装载 `InpatientEncounterContextQueryModel` 与 001 同源组装器（**BR-IP-ATD-001**；**禁止** `listInpatientEncounterContexts`+`encounterIdsIn`）。④ **`panel=discharge`**：占床块仅保留满足 **BR-IP-ATD-010** 的就诊；不满足者 occupant 置 null（床仍 `IN_USE` 时位置块保留运营态）。⑤ 合并位置块 + 占床患者块：`NOT_IN_USE` **强制** occupant=null（即使步骤②仍有活动位置行，记 **BR-IP-ATD-009** 告警） |
| **关键分支** | 入科 Tab 可拖拽分床/换床/互换；出 Tab 仅选患者、看待办与出院/清床，**不调** ATD-02 |
| **失败分支** | EX-IP-ATD-101 |
| **查询侧说明** | 纯展示；写操作后刷新本接口与 **001** |

### IF-IP-NUR-ATD-02 分床/换床/互换

| 维度 | 说明 |
| --- | --- |
| **触发前提** | **仅入科 Tab**；患者来自 001；打开分床抽屉前可调 **001** 辅助读 `loadAdmissionParticipantInit`（按入院科室/病区加载医护下拉）；抽屉内**入院体征**读回填走 **001** **IF-IP-NUR-03**；提交时前端先 **001** `savePatientInfo` 保存医护/病情，再调本 IF（**ASSIGN 时 `admissionVitalSigns` 仅随本 IF 提交，禁止经 IF-IP-NUR-04 先写**，避免 `start_time` 未落库） |
| **主路径步骤** | ① **ASSIGN**：`occupyBed` → `assignInpatientBed`（同事务；占床失败整单回滚）。**TRANSFER**：`occupyBed`（新床）→ `confirmBedTransfer` → `releaseBed`（原床）。**SWAP**：`swapInpatientBeds` → `swapBedsOperational`（双床排序行锁，release + 交叉 occupy 单次落库，GAP-ATD-003）。② **仅 `ASSIGN` 且**请求携带非空入院体征项：**在步骤 ① 成功后**（同事务）经 **`AdmissionVitalSignSupport#saveIfPresent`** 调 **`VitalSignDomainService#saveVitalSign`**：`patient_id`、`encounter_id` 与就诊一致；`recorded_at` = 分床后 `encounter.start_time`；`type_enum=TEMPERATURE_SHEET`；`temperature` 写主表；merge 该时点已有主表+明细后 upsert `HEIGHT`/`WEIGHT`/`HR`/`PULSE`/`SBP`/`DBP` 明细 |
| **关键分支** | **`TRANSFER`/`SWAP`**：不写入院体征；**`ASSIGN`** 体征全空：跳过步骤 ②；**`ASSIGN` 且 `start_time` 仍空**：跳过步骤 ②（禁止 fallback 当前时刻）；**仅维护不换床**的体征变更走 **001** IF-IP-NUR-04（本 IF 不调） |
| **失败分支** | EX-IP-ATD-201、EX-IP-ATD-202 |
| **查询侧说明** | 刷新 ATD-01（`panel=admission`）与 001；体征详情经 **001** 详情刷新 |

### IF-IP-NUR-ATD-03 出院前待执行医嘱列表

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 出 Tab 选中一名患者；`encounterId` 必填 |
| **主路径步骤** | ① Gate：就诊 `IMP` 且属于出 Tab 可见范围。② 查询该就诊下**已校对/已停嘱**、**医生开立**的药品/诊疗请求；**排除**转科、出院、护理类诊疗及退药关联行。③ **长期未停** → `LONG_NOT_STOPPED`。④ **长期已停**：`listLongTermStoppedPendingPlanTimes`（与门禁 / **Q-IP-EXE-001** 同源）非空 → `LONG_PENDING_SLOTS`，并回填 **`pendingPlanTimes`**。⑤ **临时**：整单净态仍 pending → `TEMP_NOT_COMPLETED`（不回传计划点）。⑥ 返回阻塞行；UI 对 `LONG_PENDING_SLOTS` 可展示时点并批量不执行（**IF-IP-EXE-04**） |
| **失败分支** | EX-IP-ATD-103 |
| **查询侧说明** | 非 `LONG_PENDING_SLOTS` 行 `pendingPlanTimes` 为空列表 |

### IF-IP-NUR-ATD-04 出院前待发/待退药列表

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 出 Tab 选中患者；`encounterId` 必填 |
| **主路径步骤** | ① Gate：就诊可见性同 ATD-03。② 查询该就诊下 `MedicationDispense`，状态 ∈ **BR-IP-ATD-015**。③ 关联药品请求/药品定义/执行科室等展示字段（读侧批量，禁止 N+1）。④ 返回列表 |
| **失败分支** | EX-IP-ATD-104 |
| **查询侧说明** | 无 |

### IF-IP-NUR-ATD-05 办理出院

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 出 Tab；用户点击**出院**；`encounterId` 必填；就诊须满足 §1.9 可见性 |
| **主路径步骤** | ① Gate：操作人登录、`encounterId` 非空。② Pre：QueryService + **出 Tab 可见**（否则 EX-IP-ATD-304）。③ Pre：`IMP` 就诊加载。④ Pre：若已 `PENDING` → **幂等成功**（不重复写库）。⑤ Pre：**待出院**（`ON_HOLD`+无转科+有 `discharge_type_enum`，否则 305）。⑥ Pre：**BR-IP-ATD-011** 门禁（301/302/303/307）。⑦ Pre：`dischargedAt` = 出院类诊疗 `occurrence_start_time`，无则当前时刻。⑧ Cmd：`executeInpatientNurseDischarge`（`ON_HOLD→PENDING`，`end_time`）。⑨ 发布 `HospitalDischargedEvent`（`status_enum=PENDING`）。⑩ 返回 `succeeded=true` |
| **关键分支** | 已 `PENDING`：步骤 ④ 直接成功（幂等）；`COMPLETED` 未清床仍可见但办理出院应被 304/305 拦截 |
| **失败分支** | EX-IP-ATD-301～306 |
| **查询侧说明** | 刷新 ATD-01（`panel=discharge`）、001 列表/详情；**AS-IP-FIN-003** 结算队列可出现该患 |

### IF-IP-NUR-ATD-08 取消办理出院

| 项 | 内容 |
| --- | --- |
| **触发前提** | 出 Tab；用户点击**取消办理出院**；就诊须为 **`PENDING`** |
| **主路径步骤** | ① Gate：操作人登录、`encounterId` 非空。② Pre：出 Tab 可见 + `IMP`。③ Pre：是否仍有生效态出院嘱 → `restoreToInProgress`。④ Pre：已为目标态（`ON_HOLD`/`IN_PROGRESS`）→ 幂等成功。⑤ Pre：非 `PENDING` → **EX-IP-ATD-310**。⑥ Cmd：`cancelInpatientNurseDischarge`（清 `end_time`；有嘱→`ON_HOLD`，无嘱→`IN_PROGRESS`+清 `out_way_code`）。⑦ 发布 `HospitalDischargedEvent`（载荷为取消后态）。⑧ 返回 `succeeded` + `restoredStatusEnum` |
| **关键分支** | 与收银台并发结算成功变 `COMPLETED` → 310，提示走反结 |
| **失败分支** | EX-IP-ATD-310、311 |
| **查询侧说明** | 刷新 ATD-01（`panel=discharge`）、001 列表；结算队列该患出队 |

### IF-IP-NUR-ATD-06 清床

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 出 Tab；用户点击**清床**；就诊 **`COMPLETED`**（**AS-IP-FIN-003** 出院全结后）；**展示账户余额为 0**（**BR-IP-ATD-016**）；无未冲销清床汇总销售（**BR-IP-ATD-020**）；`encounterId` 必填 |
| **推荐前置** | 调用 **IF-IP-NUR-ATD-06a**：有 tip 则前端确认（仍允许清床） |
| **主路径步骤** | ① Gate：操作人登录。② Pre：就诊 `IMP+COMPLETED`；未冲销清床净销售合并 3505 → **EX-IP-ATD-404**；**展示账户余额为 0**（否则 **EX-IP-ATD-403**）；存在**唯一**活动床位级位置（否则 EX-IP-ATD-402）。③ Cmd：`clearDischargedBed`（收口活动位置/参与者；`transitToDischarged`）。④ 同事务：对该床 `releaseBed`。⑤ afterCommit 异步住院净销售合并 3505（药+批+包装锚价；码去重；IMP setl_id）。⑥ 返回成功 |
| **关键分支** | 非 `COMPLETED` → EX-IP-ATD-401；展示余额非零 → **EX-IP-ATD-403**；未冲销销售 → **EX-IP-ATD-404** |
| **失败分支** | EX-IP-ATD-401、**EX-IP-ATD-403**、**EX-IP-ATD-404**、EX-IP-ATD-402 |
| **查询侧说明** | 患者自护士站列表消失（`DISCHARGED`）；床位图该床为空 |

### IF-IP-NUR-ATD-06a 缺追溯码软提示

| 维度 | 说明 |
| --- | --- |
| **方法** | `listMissingDispenseTraceTips` → `POST /api/app/nurseStationATDManager/listMissingDispenseTraceTips` |
| **触发前提** | 清床按钮点击前（或确认框打开时）；`encounterId` 必填 |
| **出参** | `hasMissingTrace` + `tips[]`（`dispenseDate`、`medicationName`、`lotNumber`、`tipText`） |
| **门禁** | **无写、不硬拦**；提示文案示例：「2026-07-18 阿莫西林胶囊（批号 A01）未分配追溯码」 |
| **关键分支** | tips 为空 → 前端无需确认框 |

### ~~IF-IP-NUR-ATD-07~~ 出院召回（**已废止 · 2026-07-16**）

> **写能力已迁至** **AS-IP-FIN-004** **IF-IP-RCL-01** `recallInpatientDischarge`（`POST /api/app/inpatientSettle/recallInpatientDischarge`）。护士站**不提供**召回产品入口；历史 **EX-IP-ATD-501～504** 由 **EX-IP-RCL-001～004** 承接。

---

## 5. 业务异常与用户提示

| 异常 ID | 场景 | 提示 | 可重试 |
| --- | --- | --- | --- |
| EX-IP-ATD-101 | 床位列表失败 | 床位加载失败 | 是 |
| EX-IP-ATD-103 | 待执行列表失败 | 待执行医嘱加载失败 | 是 |
| EX-IP-ATD-104 | 待发药列表失败 | 待发药品加载失败 | 是 |
| EX-IP-ATD-201 | 目标床已占 | 请选择空床或互换 | 是 |
| EX-IP-ATD-202 | 分床失败 | 分床失败，请联系管理员 | 是 |
| EX-IP-ATD-301 | 长期医嘱未停 | 长期医嘱需要先停嘱 | 否 |
| EX-IP-ATD-307 | 长期已停嘱仍有待执行时点 | 长期医嘱仍有待执行时点，请先执行或不执行登记 | 否 |
| EX-IP-ATD-302 | 临时医嘱未执行完 | 存在未执行或未完成的临时医嘱，请先执行 | 否 |
| EX-IP-ATD-303 | 待发/待退药未处理 | 患者有待取的药品，请先取药 | 否 |
| EX-IP-ATD-304 | 状态不允许出院 | 当前状态不允许办理出院，请刷新 | 否 |
| EX-IP-ATD-305 | 未待出院 | 请先开立并校对出院医嘱 | 否 |
| EX-IP-ATD-306 | 出院办理失败 | 出院失败，请刷新后重试 | 是 |
| EX-IP-ATD-308 | 缺追溯码软提示码（前端） | 存在未分配追溯码的已发药记录（后端不硬拦） | — |
| EX-IP-ATD-309 | 出院医技项目未执行或医技划价耗材未发放（**BR-IP-ATD-011⑤**） | 请完成医技执行后再办理出院 | 否 |
| EX-IP-ATD-310 | 取消办理出院状态不允许（须 `PENDING`；**BR-IP-ATD-025**） | 仅待结算患者可取消办理出院；已结算请走收银台反结 | 否 |
| EX-IP-ATD-311 | 取消办理出院持久化失败 | 取消办理出院失败，请刷新后重试 | 是 |
| EX-IP-ATD-401 | 未结算不可清床 | 请等待出院结算完成后再清床 | 否 |
| EX-IP-ATD-403 | 展示余额非零不可清床 | 患者账户余额须为零后才能清床 | 否 |
| EX-IP-ATD-404 | 未冲销清床汇总销售不可再清 | 上次清床汇总销售尚未冲销成功，请完成冲销或联系运维后再清床 | 是 |
| EX-IP-ATD-402 | 清床失败 | 清床失败，请检查病床状态 | 是 |
| EX-IP-ATD-501～504 | ~~护士站召回~~ | **已废止**；见 **AS-IP-FIN-004** **EX-IP-RCL-001～004** | — |

---

## 6. 事件语义与消费方

| 事件 | 触发 | 消费 |
| --- | --- | --- |
| `InpatientBedAssignedEvent` | 首次分床（ATD-02） | InpatientEncounterContextQueryModel；001 列表 |
| `VitalSignSavedEvent` | ATD-02 **ASSIGN** 入院体征写成功（**DM-DIA-002**） | 001 详情入院体征；**007/014** 读侧 |
| `BedOccupiedEvent` / `BedReleasedEvent` | `occupyBed` / `releaseBed` | 床位图；Location 运营态 |
| `HospitalDischargedEvent` | 校对出院（003，`ON_HOLD`）；办理出院（`PENDING`，若载荷区分）；清床（`DISCHARGED`） | InpatientEncounterContextQueryModel 状态/床字段/出院方式 |
| `TerminalCleaningCompletedEvent` | 清床（ATD-06） | InpatientEncounterContextQueryModel；床位板可收治 |
| 就诊 `status_enum` 变更 | ATD-05/06/07 | 001 队列；**AS-IP-FIN-003** 队列（`PENDING`/`COMPLETED`） |
| `InpatientDischargeRecalledEvent` | **FIN-004 IF-IP-RCL-01** | InpatientEncounterContextQueryModel：`status_enum→IN_PROGRESS`；`requiresBedAssignment=false` |
| `ServiceRequestRejectedToDraftEvent` | **FIN-004** 召回内嵌反结 | 医嘱列表；`discharge_type_enum` 投影清除 |
| `InpatientSettleCancelledEvent` 等 | **FIN-004** 召回内嵌反结 | 支付/ChargeItem 读侧；待结算队列 |

---

## 7. 非功能性需求

| 维度 | 要求 |
| --- | --- |
| **一致性** | 与 001 树范围一致；入科/出 Tab 切换后床位范围不变、占床过滤随 `panel` 变；出院/清床后 001 + 双 Tab 床位图同步刷新 |
| **事务** | ATD-02 **ASSIGN**：Encounter 分床 + 床态 + 入院体征（若有）**同 App 事务**；ATD-05、ATD-06 写路径单事务；ATD-06 **须** Encounter 清床与 `releaseBed` 同提交 |
| **并发** | 同床并发分床拒绝（**EX-IP-ATD-201**）：GAP-ATD-001/002/003；出院/清床（**EX-IP-ATD-304/401**）：ATD-05/06 就诊悲观行锁 + 锁内断言（GAP-ATD-005 阶段 A）；`assertTargetBedVacant` 为软预检 |

---

## 8. 测试要点

| 类别 | 要点 |
| --- | --- |
| **入科** | 同 v1.x：分床/换床/互换、床态与 `adm_encounter_location` |
| **入院体征** | **ASSIGN** 填写部分项落库；再次打开抽屉 **001** 详情回填；**TRANSFER/SWAP** 不改体征；仅维护走 **001** IF-IP-NUR-04 |
| **出 Tab 范围** | 校对出院后 `ON_HOLD` 待出院可见；`PENDING`/`COMPLETED` 仍占床可见；`DISCHARGED` 不可见 |
| **待办读** | ATD-03/04 仅展示阻塞出院项；处理后可出院 |
| **出院门禁** | 长期未停嘱/临时未执行/待发退药拦截；通过后 `→PENDING` |
| **清床** | 仅 `COMPLETED` 且展示余额为 0 可清；未冲销清床汇总销售 → **EX-IP-ATD-404**；缺追溯码 tip（06a）后仍可清；成功后床空、`DISCHARGED` |
| **链路** | 003 校对出院 → 本 AS 出院 → FIN-003 全结 → 本 AS 清床 |
| **未清床反结** | `COMPLETED` 占床 → 收银台 **FIN-004 反结** → 回 `IN_PROGRESS`；须重新校对→办理出院→再结算 |
| **召回** | ~~护士站 ATD-07~~ → 收银台 **IF-IP-RCL-01**；`DISCHARGED→IN_PROGRESS`+WARD（无床） |

---

## 9. 关联文档与变更历史


### 9.2 变更历史

| 版本 | 日期 | 变更摘要 |
| --- | --- | --- |
| v2.29 | 2026-10-17 | **IF-IP-NUR-ATD-08 / BR-IP-ATD-025**：取消办理出院（`PENDING→ON_HOLD/IN_PROGRESS`）；出 Tab 按钮；**EX-IP-ATD-310/311**；对齐 DM §6.1.19a |
| v2.28 | 2026-09-29 | **BR-IP-ATD-011②**：TECH 执行科临时诊疗不计入护士临时净态（改由⑤ / EX-IP-ATD-309）；过滤后仅医技侧 Procedure 时护士临时不拦 |
| v2.27 | 2026-09-29 | **BR-IP-ATD-011⑤**：出院改卡医技执行净态（+非自动扣库耗材发放）；不再读 `fee_confirmer_*`；临时净态仅计护士侧 Procedure |
| v2.26 | 2026-09-23 | **BR-IP-ATD-011①′**：预计领用日补点须认不执行终态（交叉 **AS-IP-NUR-004 v2.61** / **BR-IP-EXE-038**）；禁止不执行后假 pending 误拦出院 |
| v2.25 | 2026-09-21 | **BR-IP-ATD-011⑤** 改为医技相关费用未确费；废执行同态/医技未做硬拦（协同 DD 软落地） |
| v2.24.1 | 2026-09-19 | §5 异常表补登 **EX-IP-ATD-309**（出院医技未做/双侧同态；对齐 **BR-IP-ATD-011⑤**） |
| v2.24 | 2026-09-19 | **BR-IP-ATD-011⑤**：出院增「医技未做」+ 护士/医技双侧同态（停嘱除外）；见协同 DD |
| v2.23 | 2026-09-16 | **废止**停嘱校对自动 skip 交叉（原 **AS-IP-NUR-003 BR-IP-AUD-015**）；出院真 pending 仅出 Tab / **IF-IP-EXE-04**；假 pending 仍靠 **BR-IP-EXE-038** |
| v2.22 | 2026-09-14 | **BR-IP-ATD-011①′**：出院 pending 与 **BR-IP-EXE-038**（含拆零下限 MIN_SPLIT）抑制同源；对齐 **AS-IP-NUR-004 v2.57** |
| v2.21 | 2026-09-11 | **BR-IP-ATD-011①′ / ATD-03**：`LONG_PENDING_SLOTS` 与执行列表同源回传 **`pendingPlanTimes`**；一盒一点硬顶空解析后无假 pending 不拦出院；真 pending 可出 Tab 批量不执行（**IF-IP-EXE-04**）；曾交叉停嘱校对自动 skip（已废止于 v2.23）；对齐 **AS-SDD v2.19** / **AS-IP-NUR-004 v2.56** |
| v2.20 | 2026-07-19 | **清床**：缺追溯码改前端软提示 **IF-IP-NUR-ATD-06a**；新增 **BR-IP-ATD-020/021**、**EX-IP-ATD-404**（未冲销清床汇总销售禁再清） |
| v2.19 | 2026-07-16 | **出院召回 redesign**：废止 **IF-IP-NUR-ATD-07** 护士站写入口；召回迁至 **FIN-004 IF-IP-RCL-01**；**BR-IP-ATD-017～019** 标记已迁 **BR-IP-RCL-001～003**；删除 **`RecallReverseSettleSupport`** 引用；§1.8 无床全结跳过清床 |
| v2.18 | 2026-07-16 | **BR-IP-ORD-028④**：ATD-03 待执行列表嘱托行 `displayName` 优先 `entrust_content`；对齐 **AS-IP-NUR-002-SDD v2.17** |
| v2.17 | 2026-07-11 | **未清床反结成码回更**：收银台反结就诊 **`COMPLETED→IN_PROGRESS`**（非 `PENDING`）；§1.10.1 测试要点同步 |
| v2.16 | 2026-07-11 | **BR-IP-ATD-011①′**：长期已停嘱若停嘱时刻前仍有待执行槽位（与 **Q-IP-EXE-001** 同源）阻塞出院；ATD-03 新增 `LONG_PENDING_SLOTS`；ATD-05 新增 **EX-IP-ATD-307** |
| v2.15 | 2026-07-11 | **ATD-01 占床块装载修正**：`listInpatientContextsByEncounterIdsOrdered` 替代 `listInpatientEncounterContexts`+`encounterIdsIn`；**BR-IP-ATD-001** 增补禁止登记列表状态过滤；修复待出院（`ON_HOLD`）占床在入科 Tab 仅显示「占床」空壳 |
| v2.14 | 2026-07-09 | 出 Tab 前端按钮与链路对齐：**办理出院** 仅 `ON_HOLD`；**清床** 仅 `COMPLETED`（后端门禁不变） |
| v2.13 | 2026-06-19 | **成码对齐**：`AdmissionVitalSignSaveContext`/`VitalSignAdmissionItemCodeConstants`；ASSIGN 无 `start_time` 跳过体征写；前端 ASSIGN 体征仅随 ATD-02 |
| v2.12 | 2026-06-19 | **入院体征（入科首次分床）**：纳入 **IF-IP-NUR-ATD-02 ASSIGN** 写路径；**BR-IP-ATD-020～024**；对齐 **DM-DIA-002** + 老系统 ATD 仅首次分床/维护写体征语义；读/仅维护写交叉 **001** IF-IP-NUR-03/04 |
| v2.11 | 2026-06-18 | **ATD-07 文档对齐成码**：内嵌反结锚点 `RecallReverseSettlesupport；§4 IF-IP-NUR-ATD-07 步骤 ④ 细化 |
| v2.11.1 | 2026-06-19 | **BR-IP-ATD-016** 清床门禁：展示可用余额 = 库内 balance − 待结算（**不**再减已结算） |
| v2.10 | 2026-06-18 | **召回口径修正**：**仅 `DISCHARGED` 可召回**；`COMPLETED` 未清床走 **AS-IP-FIN-004 反结**（非召回）；**召回内嵌反结**（非收银台前置）；**撤销出院医嘱归反结**；召回本体仅恢复就诊位置 + 二次分床；§1.10 拆为 1.10.1/1.10.2；BR-IP-ATD-017～019、ATD-07 编排同步 |
| v2.8 | 2026-06-18 | **定稿对齐成码**：**ATD-07** 应用层 `recallInpatientDischarge` + 前端召回入口已成码；**GAP-IP-ATD-009** 全栈闭合；出院反结改为**收银台/AS-IP-FIN-003 业务前置**（非 ATD-07 同事务）；事务口径 §4/§7 同步（**v2.9 废止**） |
| v2.7 | 2026-06-18 | **出院召回**：**IF-IP-NUR-ATD-07**、§1.10 逆向链路、**BR-IP-ATD-017～019**；分轨 `COMPLETED→IN_PROGRESS` / `DISCHARGED→REGISTERED`+WARD；反结前置；出院嘱 **`rejectServiceRequestToDraft`**；**GAP-IP-ATD-009 已闭合**（领域层） |
| v2.6 | 2026-06-17 | **BR-IP-ATD-011② 临时净态**：对齐 **AS-IP-NUR-004 BR-IP-EXE-006/008**；执行→发药→取消执行→退药全链路结束后不再阻塞出院 |
| v2.5 | 2026-06-17 | **ATD-01 空床口径**：**BR-IP-ATD-001/009** 明确空床/占床以 `operational_enum` 为准，不得仅凭活动 `adm_encounter_location`；批量读模型仅 `IN_USE` 床；`NOT_IN_USE`+活动位置残留告警 |
| v2.4 | 2026-05-29 | **清床余额门禁**：新增 **BR-IP-ATD-016**、**EX-IP-ATD-403**；清床前展示账户余额须为 0（口径同 **BR-IP-NUR-006**）。 |
| v2.4.1 | 2026-06-11 | ATD-02 分床抽屉医护下拉改调 **001** `loadAdmissionParticipantInit`（按入院科室/病区收窄） |
| v2.3 | 2026-05-28 | 读模型废止后对齐 zxplat 实现（EncounterContextFacade / querysupport Loader）。 |（已迁至 EncounterContextReader / application.shared.encounter）
| 类型 | 文档 |
| --- | --- |
| 上游 | `AS-IP-NUR-001`；`AS-IP-NUR-003`（校对出院）；`AS-IP-NUR-004`（执行口径）；`AS-IP-PHA-001`（发药）；`AS-IP-FIN-003`（结算）；`AS-IP-FIN-004`（反结/召回 **IF-IP-RCL-01**） |
| DM | `DM-ADM-002` §2.3.1～2.3.2、§6.1.17～18a、§6.1.21～22；`DM-ADM-003` |
| 查询服务 | `DM-ADM-002 §6.2 EncounterQueryService（InpatientEncounterContextQueryModel）` |

| 版本 | 日期 | 摘要 |
| --- | --- | --- |
| v1.0 | 2026-05-19 | 首版 |
| v1.1 | 2026-05-19 | 仅保留床位列表+分床写；详情归 001 |
| v1.2 | 2026-05-20 | 全部床位；`IN_USE`/`NOT_IN_USE`；参与者轨迹 |
| v1.3 | 2026-05-20 | 位置 + 占床患者块 |
| v1.4 | 2026-05-20 | 关闭 GAP-001/002/004 |
| v1.5 | 2026-05-20 | §3 Gap 收口（入科） |
| v1.6 | 2026-05-20 | ATD-01：`busNo` 排序、`houseName` 批量 |
| v2.0 | 2026-05-26 | **出 Tab**：`panel`、待执行/待发药读、办理出院、清床；§3 新增 GAP-006～008；状态机对齐 **DM-ADM-002` §2.3.1**；结算/清床分工 **AS-IP-FIN-003** |
| v2.1 | 2026-05-26 | **§3 Gap 闭合**：`executeInpatientNurseDischarge`、DM §6.1.21～24 修订、`HospitalDischargedEvent.dischargeTypeEnum` |
| v2.2 | 2026-05-26 | **文档收口**：§1.8 出 Tab 全链路、§1.9 可见性/门禁；ATD-05 编排与成码一致；应用层 ATD-01～06 已落地；配套 DM §6.2 SDD 交叉索引 |
