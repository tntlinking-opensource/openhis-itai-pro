# 住院护士站 · 皮试管理 · 应用服务设计（AS）

> **文档职责**：工作台右侧「皮试管理」独立 Tab 契约——按就诊查询待皮试/已登记行、双签保存皮试临床结果（`AllergyIntolerance`）；枚举初始化与误录删除。
>
> **页面边界**：**本 Tab**——点选左侧患者（**AS-IP-NUR-001** 同一患者列表，**不**另建皮试队列）→ 加载皮试行 → 填写结果/批号/双签 → 保存或删除。**患者上下文**在 **AS-IP-NUR-001**；**医嘱校对**在 **AS-IP-NUR-003**；**皮试检查**作为普通临嘱进 **AS-IP-NUR-004** 执行（操作费由执行计费，与其它诊疗无异）；取消执行走 **AS-IP-NUR-012**。
>
> **与门诊差异**：住院皮试检查 SR 由**医生站**联动生成（**AS-IP-DOR-003 · BR-IP-ORD-024**，doctor-prescription，**不**挂费）；护士须先**校对并执行**「皮试检查」临嘱后，方可在本 Tab 登记临床结果。本 Tab **不**划价、**不**写 Procedure。门诊仍可按 **AS-OP-NUR-001 §1.7** 在皮试 Tab 计费并与执行互斥。
>
> **去特殊化**：companion「皮试检查」SR **进** **AS-IP-NUR-004** 待执行，并计入 `pendingExecuteCount`（**BR-IP-NUR-005**）；**皮试药**同样进待执行（药费/发药）。本 Tab 仅承载阳/阴等临床附加事实。
>
> **编码**：AS-IP-NUR-011 · **版本**：v1.5.0 · **日期**：2026-07-22
>
> **v1.5 批号与多结果**：批号候选取自皮试药执行后 `MedicationDispense` FIFO 批号（**必填**且须 ∈ 候选）；同一皮试药可挂多条 companion「皮试检查」SR（换批重做，医生站 **IF-IP-DOR-27** 追加），列表**一行一 SR**；已登记行只读，医生站回显取**最新**一次结果覆盖展示。跨批软提示见 **AS-IP-NUR-004 · BR-IP-EXE-034**（长嘱皮试药执行后）。

---

## 1. 场景概述

### 1.1 目标与边界

| 维度 | 说明 |
| --- | --- |
| **在内** | IF-IP-STK-01 皮试表单枚举初始化；IF-IP-STK-02 按 **encounterId** 查询皮试待办/已登记列表（含批号候选、**executedFlag**）；IF-IP-STK-03 **保存皮试结果**（双签 + `AllergyIntolerance`，须皮试检查**已执行**）；IF-IP-STK-04 **删除**误录皮试记录 |
| **在外** | 左侧患者树/列表/详情（**AS-IP-NUR-001**）；医嘱校对（**AS-IP-NUR-003**）；医嘱执行/发药/操作费（**AS-IP-NUR-004**）；取消执行（**AS-IP-NUR-012**）；划价 Tab 手工选品（**AS-IP-NUR-005**）；医生站皮试联动 SR 开立/签发（**AS-IP-DOR-003 · BR-IP-ORD-024**）；阳性**硬拦截**发药或执行；费用冲销 |
| **涉及域** | MedicationRequest；ServiceRequest；ActivityDefinition；AllergyIntolerance；Procedure（只读门禁）；MedicationDispense（批号候选只读）；Encounter |
| **编排权威** | 以本文 **§2、§4** 为准；临床登记语义对齐门诊皮试结果落库；**计费语义归 **AS-IP-NUR-004****（执行皮试检查临嘱） |

### 1.2 角色与关键动作

| 角色 | 关键动作 |
| --- | --- |
| **病区护士** | 校对皮试药与皮试检查 → 在执行 Tab **执行皮试检查**（产生执行记录与操作费）→ 切至皮试 Tab → 选批号、填临床/验证状态、双签 → **保存**；误录结果可**删除**（不冲执行费） |
| **系统** | 列表展示**已校对**皮试药 + doctor 联动皮试检查 SR + 是否已执行 + 已有过敏结果；保存仅写过敏记录；医生站列表回显皮试结果 |

### 1.3 术语与口径约定

| 术语 | 口径 |
| --- | --- |
| **皮试药医嘱** | `MedicationRequest`：`generate_source_enum = doctor-prescription`，`skin_test_flag = true`，经护士校对后 `status_enum = VERIFIED` |
| **皮试检查（普通临嘱）** | 固定诊疗活动 **「皮试检查」**（`GeneralSharedConstants.SKIN_TEST_INSPECTION_ACTIVITY_NAME`）；**由医生站 IF-IP-DOR-14 联动**或 **IF-IP-DOR-27 换批追加**生成 **`generate_source_enum = doctor-prescription`** 的 `ServiceRequest`，`based_on_table = med_medication_request`，`based_on_id = 皮试药 id`，`therapy_enum = TEMPORARY`；同一皮试药可挂**多条** companion；进校对/执行/取消执行，与其它诊疗临嘱同轨；**护士 Tab 不新建 SR、不划价** |
| **临床结果** | `AllergyIntolerance`：`clinical_status_enum`、`verification_status_enum`；`request_id` 锚定上述 **doctor-prescription 皮试检查** `ServiceRequest.id`（**非**皮试药 `MedicationRequest.id`） |
| **双签** | `practitionerId` 与 `checkPractitionerId` **均必填** |
| **列表准入** | 皮试药须 **VERIFIED**（**BR-IP-STK-002**）；且须存在锚定该皮试药的 doctor-prescription「皮试检查」SR（**BR-IP-STK-009**）；装配 **executedFlag**（皮试检查是否净剩 COMPLETED Procedure） |
| **保存门禁** | companion 皮试检查 SR 须**已执行**（净剩 `Procedure` **COMPLETED**，**BR-IP-STK-004**）；首次/更新均仅 `saveSkinTestRecord`（**BR-IP-STK-005**） |
| **批号候选** | 取自该皮试药 `MedicationRequest` 下已产生的 `MedicationDispense.lot_number`（护士执行 FIFO 落库后）；**不**再读库存可用批；无发放批号则候选为空 |
| **批号必填** | IF-IP-STK-03 保存时 `lotNumber` **必填**且须落在该皮试药发放批号候选内（**BR-IP-STK-013**） |
| **多结果行** | 同一皮试药可对应**多条** companion 皮试检查 SR；列表按 **companion SR** 分行；已有 `AllergyIntolerance` 的行**只读**（可删误录）；未登记且已执行的行可填 |
| **医生站回显** | 住院医生站 `skinTestOutcome*`：同一皮试药下各 companion 关联过敏记录中，按 `recorded_date`（空则回落创建时间）取**最新一条**覆盖展示；历史记录仍保留；**护士站医嘱校对/执行列表**同源回显 `skinTestOutcomeCode`/`skinTestOutcomeText`，医嘱内容列跟在药名后展示阴/阳性 |

### 1.4 本期范围与非目标

| 类别 | 内容 |
| --- | --- |
| **本期** | IF-IP-STK-01～04 |
| **非目标** | 皮试专用患者队列；护士 Tab **新建** nurse-pricing 皮试检查 SR；本 Tab 划价/`ChargeItem`；门诊皮试「仅皮试就诊」切换；阳性拦截发药/执行；删除结果时费用冲销；本 Tab 写 Procedure；耗材皮试 |

### 1.5 领域清单（聚合 + 职责 + DM 路径）

| 聚合（域） | 职责摘要 | DM 路径 |
| --- | --- | --- |
| **MedicationRequest** | 皮试药医嘱；列表准入 **VERIFIED** + `skinTestFlag` | `DM-MED-003` |
| **ServiceRequest** | **医生联动**的皮试检查诊疗（doctor-prescription）；`based_on` → 皮试药；本 Tab **只读**，执行/计费在 **AS-IP-NUR-004** | `DM-WKF-004` |
| **ActivityDefinition** | 「皮试检查」诊疗定义 | `DM-WKF-001` |
| **AllergyIntolerance** | 皮试临床结果事实 | `DM-CLN-001` |
| **Procedure** | 只读：判定皮试检查是否已执行（净剩 COMPLETED） | `DM-CLN-002` |
| **InventoryItem** | —（v1.5 批号候选改发放批号，不再用库存可用批） | — |
| **MedicationDispense** | 皮试药执行 FIFO 发放行；批号候选只读 | `DM-MED-002` |
| **Encounter** | 可见性校验 | `DM-ADM-002` |

### 1.6 业务规则（BR-*）

| 规则 ID | 描述 | 绑定 IF |
| --- | --- | --- |
| **BR-IP-STK-001** | 读/写 `encounterId` 必填；就诊须落在 **AS-IP-NUR-001** 可见范围 | 02、03、04 |
| **BR-IP-STK-002** | **列表行来源**：当前就诊下 `generate_source_enum = doctor-prescription` 的药品请求，且 `skin_test_flag = true`，且 `status_enum = VERIFIED`；**排除**退药/作废关联行；对每条皮试药展开其全部 doctor-prescription「皮试检查」companion SR，**一行对应一条 companion SR** | 02 |
| **BR-IP-STK-003** | 列表须装配：药品名/规格、开立医生、皮试药 `requestId`、**doctor 皮试检查 SR id**、**executedFlag**、已有 `AllergyIntolerance` 结果（若有）、**发放批号候选**、枚举展示文案、**readOnlyFlag**（已登记则 true） | 02 |
| **BR-IP-STK-004** | **保存**须：① 校验皮试药仍为 **VERIFIED** 且属当前就诊；② 校验目标 companion「皮试检查」`ServiceRequest`（**BR-IP-STK-009**）；③ 校验该 SR 存在净剩 **COMPLETED** Procedure（已执行）；④ **批号必填且 ∈ 该皮试药发放批号候选**（**BR-IP-STK-013**）；⑤ `saveSkinTestRecord`，`request_id` = ② 的 SR id；**禁止**本 IF 创建 `ChargeItem` | 03 |
| **BR-IP-STK-005** | **更新保存**：仅允许对该 companion SR **尚无**过敏记录的行做首次登记后的误录修正；已登记行 UI 只读；若开放更新须仍满足 **BR-IP-STK-004** | 03 |
| **BR-IP-STK-006** | **废止（v1.4）**：原「同一 doctor SR 至多一条 ChargeItem / 本 Tab 幂等计费」——计费幂等归 **AS-IP-NUR-004** | — |
| **BR-IP-STK-007** | **双签**与临床/验证状态必填（**BR-ALI-002/004**） | 03 |
| **BR-IP-STK-008** | **删除**：允许 `deleteSkinTestRecord`（误录纠正）；**不**删 doctor SR、**不**联动冲销执行产生的费用、**不**取消 Procedure | 04 |
| **BR-IP-STK-009** | 皮试药须存在医生站联动的 **doctor-prescription**「皮试检查」`ServiceRequest`（`based_on → 皮试药`）；**不存在**则不可登记（**EX-IP-STK-207**），提示联系医生补开/暂存皮试 | 02、03 |
| **BR-IP-STK-010** | 保存/删除**不**变更皮试药 `MedicationRequest` 状态；**不**拦截发药/执行；**不**扣减皮试药库存；**不**写/改 Procedure | 03、04 |
| **BR-IP-STK-011** | 写路径须 `patientId` 与就诊一致；**不**要求 `accountId`（本 IF 不计费） | 03 |
| **BR-IP-STK-012** | **出院结算轨就诊 Gate**：就诊 `status_enum ∈ {PENDING, COMPLETED, DISCHARGED}` 时**禁止** IF-IP-STK-03 保存（禁止登记结果）；**仅** `IN_PROGRESS`/`ON_HOLD` 允许；委托 `InpatientPendingSettlementClinicalGateSupport`（**BR-IP-FLW-008**） | 03 |
| **BR-IP-STK-013** | **批号**：保存时 `lotNumber` 必填；候选 = 该皮试药医嘱下未删除 `MedicationDispense` 的去重 `lot_number`；不在候选内拒绝（**EX-IP-STK-210**）；无候选时提示先执行皮试药取药 | 03 |

---

## 2. 页面与接口总览（契约概览）

| IF 编号 | 用户动作 | 接口语义 | 读/写 | 关键规则 | 关键状态变化 | 涉及域 | 查询服务 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| IF-IP-STK-01 | 进入 Tab / 打开表单 | 皮试表单枚举初始化 | 读 | — | 无 | — | — |
| IF-IP-STK-02 | 点选患者后加载列表 | 按 **encounterId** 查询皮试待办/已登记行 | 读 | BR-IP-STK-001～003、009 | 无 | MedicationRequest / ServiceRequest / AllergyIntolerance / Procedure | MedicationRequestQueryService；ServiceRequestQueryService；AllergyIntoleranceQueryService；ProcedureQueryService |
| IF-IP-STK-03 | 填写结果并保存 | **保存皮试结果**（双签；须皮试检查已执行） | 写 | BR-IP-STK-004、005、007、009～012 | 新建或更新 AllergyIntolerance | AllergyIntolerance | — |
| IF-IP-STK-04 | 删除误录 | 删除皮试记录 | 写 | BR-IP-STK-001、008、010 | AllergyIntolerance 逻辑删除 | AllergyIntolerance | — |

---

## 3. 缺口跟踪（Gap）

| Gap ID | 缺口类型 | 文件 + 章节 | 缺什么 / 不一致点 | 关闭条件 | 影响 IF | 优先级 | 状态 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| GAP-IP-STK-001 | QueryService | `ActivityDefinitionQueryService` §6.2 | 按名称解析皮试检查 `activityId` | **已关闭** | IF-IP-STK-02、03 | P1 | **已关闭** |

> **口径（v1.4）**：**IF-IP-STK-03** **不再**调用 `ChargeItemDomainService#priceByNurseStation`；操作费由护士在 **AS-IP-NUR-004** 执行皮试检查临嘱时产生。

---

## 4. 接口编排说明书（业务步骤）

### IF-IP-STK-01 皮试表单枚举初始化

同 v1.0；纯读枚举选项。

---

### IF-IP-STK-02 查询就诊皮试列表

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 左侧 **AS-IP-NUR-001** 已点选患者 |
| **主路径步骤** | 校验 **BR-IP-STK-001** → 查询 **BR-IP-STK-002** 皮试药列表 → 解析皮试检查 `activityId` → 索引当前就诊 **doctor-prescription** 皮试检查 SR（`based_on → 皮试药`，**BR-IP-STK-009**）→ 左连接 `AllergyIntolerance`（`request_id = doctor SR.id`）→ 批量判定各 companion SR **executedFlag**（净剩 COMPLETED Procedure）→ 批号候选 → 装配展示文案 |
| **关键分支** | 医生未联动皮试检查 SR 的皮试药**不**进列表；无皮试药时返回空列表；未执行行可展示但不可保存 |
| **失败分支** | 就诊不可见 → EX-IP-STK-102；查询失败 → EX-IP-STK-103 |

---

### IF-IP-STK-03 保存皮试结果

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 护士填写批号、临床/验证状态、双签并点保存；皮试检查已在执行 Tab 完成 |
| **主路径步骤（首次）** | 校验 **BR-IP-STK-001、007、009、011、012** → 校验皮试药 **VERIFIED** → 加载锚定该皮试药的 **doctor-prescription** 皮试检查 SR → 校验该 SR **已执行**（**BR-IP-STK-004**）→ **saveSkinTestRecord**（`request_id` = doctor SR id） |
| **主路径步骤（更新）** | 校验记录存在且 `request_id` 指向本就诊 doctor 皮试检查 SR → 校验仍已执行 → 仅 **saveSkinTestRecord** |
| **关键分支** | 不存在 doctor 联动 SR → **EX-IP-STK-207**；未执行 → **EX-IP-STK-209** |
| **失败分支** | 参数缺失 → EX-IP-STK-201；皮试药不可登记 → EX-IP-STK-202；写失败 → EX-IP-STK-206 |
| **查询侧说明** | **AllergyIntoleranceRecordedEvent** 刷新医生站列表 |

---

### IF-IP-STK-04 删除皮试记录

| 维度 | 说明 |
| --- | --- |
| **主路径步骤** | 校验可见性 → 加载记录 → **deleteSkinTestRecord** |
| **失败分支** | 记录不存在 → EX-IP-STK-301；删除失败 → EX-IP-STK-303 |

---

## 5. 业务异常与用户提示

| 语义代码 | 场景 | 用户提示方向 | 可重试 |
| --- | --- | --- | --- |
| EX-IP-STK-101 | 枚举初始化失败 | 表单选项加载失败，请刷新 | 是 |
| EX-IP-STK-102 | 就诊不可见 | 请先选择患者或刷新列表 | 是 |
| EX-IP-STK-103 | 皮试列表查询失败 | 加载失败，请稍后重试 | 是 |
| EX-IP-STK-201 | 双签或必填状态缺失 | 请填写皮试结果并选择执行护士与核对护士 | 是 |
| EX-IP-STK-202 | 皮试药非已校对或不在本就诊 | 该皮试医嘱不可登记，请刷新列表 | 否 |
| EX-IP-STK-206 | 保存落库失败 | 保存失败，请稍后重试 | 是 |
| EX-IP-STK-207 | 缺少医生联动皮试检查 SR | 医生未开立皮试检查医嘱，请联系医生处理 | 否 |
| EX-IP-STK-208 | 就诊已待结算/已结算/已离院 | 患者已办理出院或已结算，不可变更医嘱及费用 | 否 |
| EX-IP-STK-209 | 皮试检查尚未执行 | 请先在「医嘱执行」完成皮试检查后再登记结果 | 是 |
| EX-IP-STK-210 | 批号缺失或不在发放候选内 | 请选择本次取药批号（须先执行皮试药） | 是 |
| EX-IP-STK-301 | 删除目标不存在 | 皮试记录不存在，请刷新 | 否 |
| EX-IP-STK-303 | 删除失败 | 删除失败，请稍后重试 | 是 |

> **废止（v1.4）**：**EX-IP-STK-203**（定价无效）、**EX-IP-STK-204**（重复计费）、**EX-IP-STK-302**（已计费不可删）——本 Tab 不再计费。历史：**EX-IP-STK-205**（v1.0 互斥）已废止。

---

## 6. 事件语义与消费方

| 事件语义 | 触发相位 | 典型消费方 |
| --- | --- | --- |
| `AllergyIntoleranceRecordedEvent` | IF-IP-STK-03 | 住院医生站医嘱列表皮试结果；过敏史 |
| `AllergyIntoleranceRemovedEvent` | IF-IP-STK-04 | 同上 |

> **废止发布**：`NursePricingServiceRequestCreatedEvent`（v1.0）；本 IF **不再**发布 ChargeItem 创建类事件（费用由 **AS-IP-NUR-004** 执行路径产生）。

---

## 7. 非功能性需求

| 类别 | 要求 |
| --- | --- |
| **性能** | IF-IP-STK-01～02 ≤1000ms；IF-IP-STK-03 ≤2000ms |
| **一致性** | `AllergyIntolerance.request_id` 锚定 doctor 皮试检查 SR；保存前须该 SR 已执行 |

---

## 8. 测试要点（业务视角）

| 类别 | 要点 |
| --- | --- |
| **正向** | 医生联动 SR + 皮试药/皮试检查校对 → 执行皮试检查（计费）→ 皮试 Tab 登记过敏记录；更新仅改结果；医生站回显阳性/阴性 |
| **准入** | 无 doctor 联动 SR 不进列表/不可保存（**EX-IP-STK-207**）；未校对皮试药不进列表；未执行不可保存（**EX-IP-STK-209**） |
| **计费** | 本 Tab **不**产生 ChargeItem；操作费仅来自执行 Tab |
| **异常** | 未执行保存 EX-IP-STK-209；删除误录不冲费 |
| **边界** | **不**改皮试药状态；**不**扣库存；取消执行走 **AS-IP-NUR-012** |

---

## 9. 关联文档与变更历史

### 9.1 关联文档

| 文档 |
| --- |
| `health/AppService/AS-IP-DOR-003-住院医生站医嘱开立.md`（**BR-IP-ORD-024** 医生联动 SR） |
| `health/AppService/AS-IP-NUR-001-住院护士站患者管理.md` |
| `health/AppService/AS-IP-NUR-003-住院护士站医嘱校对.md` |
| `health/AppService/AS-IP-NUR-004-住院护士站医嘱执行.md` |
| `health/AppService/AS-IP-NUR-012-住院护士站取消执行.md` |
| `health/AppService/AS-OP-NUR-001-门诊护士站.md`（§1.7 对照） |

### 9.2 变更历史

| 版本 | 日期 | 变更摘要 |
| --- | --- | --- |
| v1.5.1 | 2026-07-22 | 换批追加入口对齐医生站 **IF-IP-DOR-27**（`based_on` 锚长期皮试药） |
| v1.5.0 | 2026-07-22 | **批号**：候选改发放 FIFO；必填 **BR-IP-STK-013** / **EX-IP-STK-210**；**多 companion 分行**；已登记只读；医生站最新覆盖；跨批提示见 **AS-IP-NUR-004 BR-IP-EXE-034** |
| v1.4.0 | 2026-07-22 | **去特殊化**：皮试检查进 **AS-IP-NUR-004** 普通临嘱执行计费；本 Tab 仅登记临床结果；**BR-IP-STK-004** 改为已执行门禁；废止本 Tab 划价与 `pricedFlag` 删除门禁；新增 **EX-IP-STK-209**；废止 **EX-IP-STK-203/204/302**、**BR-IP-STK-006** 计费语义 |
| v1.3.0 | 2026-07-17 | **与执行 Tab 互斥收窄**：仅 companion「皮试检查」SR 不进待执行；**皮试药须进** **AS-IP-NUR-004** / `pendingExecuteCount`（**v1.4 已废止互斥**） |
| v1.2.0 | 2026-07-11 | **BR-IP-STK-012**：就诊 `PENDING/COMPLETED/DISCHARGED` 禁止 IF-IP-STK-03；**EX-IP-STK-208**；对齐 **BR-IP-FLW-008** |
| v1.1.0 | 2026-06-18 | **架构决议**：皮试检查 SR 改由 **AS-IP-DOR-003 · BR-IP-ORD-024** 医生联动（doctor-prescription）；护士 **IF-IP-STK-03** 首次保存改为**锚既有 doctor SR 计费**，**废止** nurse-pricing 补费新建 SR（**v1.4 再废止 Tab 计费**） |
| v1.0.1 | 2026-06-10 | **GAP-IP-STK-001 已关闭** |
| v1.0 | 2026-06-10 | 初版（nurse-pricing 补费路径，**已由 v1.1 废止**） |

---

**文档负责人**：（待填） · **评审**：（待填）
