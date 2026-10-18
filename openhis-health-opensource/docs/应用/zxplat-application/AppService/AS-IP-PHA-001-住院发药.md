# 住院发药 · 应用服务设计（AS）

> **文档职责**：说明「住院发药」药房工作站的**接口契约（IF）**、业务语义、涉及聚合与 QueryService、规则（BR）、编排意图；作为上游契约供详细设计与实现对齐。
>
> **本文必须写**：IF 编号；用户动作与读写属性；关键校验与状态变化；Gap；异常口径；事件语义；非功能约束；业务向测试要点。
>
> **本文禁止写**：具体类名/应用方法名；Request/Response 字段明细；包路径；Mapper；伪代码；写库顺序；QueryService 字段组装规则。
>
> **页面边界**：住院药房人员在发药工作站按**病区**（可选）、关键字（姓名/住院号/床号）、**已发药/待发药**筛选患者队列（**含出院轨**）→ 选择就诊 → 按西药/中药查看**按护士执行日虚拟分单**的药品发药明细（**只读**行上库房/批号/数量，**不提供**候选库存改选）→（可选）采集追溯码 → 选择调配药师 → **保存配药** → **发药**；支持单行 **作废**。
>
> **覆盖声明**：契约以 **§2 IF-IPP-01～06** 为准；仅处理 **药品发放（MedicationDispense）**，**不包含耗材**配发、**不包含**汇总发药（`SUMMARIZED` / 汇总单）、**不包含**退药。状态推进对齐 **DM-MED-002**：**PREPARATION → IN_PROGRESS → COMPLETED**；作废 **DECLINED**。药房**只认发放状态**；护士执行后产生可作业发放行；账单待结算与住院结算**不在本 AS 编排**。
>
> **编码**：AS-IP-PHA-001 · **版本**：v2.9 · **日期**：2026-08-31

---

## 1. 场景概述

### 1.1 目标与边界


| 维度 | 说明 |
| --- | --- |
| **在内** | 发药页初始化（调配药师候选、未发药原因、发药状态筛选值集、**病区候选**等）；**患者队列**：就诊在 **InpatientEncounterContextQueryModel** 上为 **在院或出院轨**（`status_enum ∈ { IN_PROGRESS, ON_HOLD, PENDING, COMPLETED, DISCHARGED }`），且存在至少一条 **药品 MedicationDispense**，`DispenseStatus ∈ { PREPARATION, IN_PROGRESS, COMPLETED }`；可按 **病区**（可选）、关键字（姓名/住院号/床号）、**已发药/待发药**收窄；**管辖库位非空**时发放事实 `location_id` 须落在 **`CurrentManagedLocationIdsResolver#listManagedLocationIdsOrEmpty`** 并集内；**管辖 empty** 过渡态不按 `location_id` 收窄。选定就诊后分页加载**药品发药单/发放行**（中西药切换；明细**不传**发药库房，与门诊 IF-OPP-03 一致）；列表展示**批号**与**单价/金额**（只读）；**不装配**候选库房×批号库存；用户**不可改**批号、实发数量。**保存配药**（`PREPARATION→IN_PROGRESS`：调配药师、追溯码等；按发放单**既有**库房+批号+数量做库存充足校验）→ **发药**（`IN_PROGRESS→COMPLETED`：再校验库存后扣减、追溯出库）→ 或 **作废**（`DECLINED`+原因） |
| **在外** | 汇总发药、摆药单批量核发、住院退药、医保上传、住院结算/预交金、追溯码国家库解析细则、打印机驱动 |
| **跨域** | **Encounter**；**MedicationRequest** / **MedicationDispense**；**InventoryItem**；**TraceNoManage** |
| **编排权威** | 以本文 **§2、§4** 为准；**保存配药**与**发药**分接口、顺序由前端保证；库存扣减仅在**发药**接口执行 |
| **前置（摘要）** | **护士医嘱执行**（**AS-IP-NUR-004**）或等价域内路径已将药品发放行推进至可药房作业态（如 **PREPARATION**）；发放行上库房/批号/数量由执行或签发侧写入，本场景**不修改** |
| **后置（摘要）** | **发药**成功 → `COMPLETED` 并按发放单行扣减库存、追溯出库；费用侧待结算/结算由收费域按执行与发放事件自行推进，药房不等待结算完成 |


### 1.2 角色与关键动作


| 角色 | 关键动作 |
| --- | --- |
| **住院药房调配/发药人员** | 按病区/关键字筛队列（已发/待发）→ 选择在院患者 → 切换中西药查看发药单 →（可选）扫追溯码 → 选调配药师 → **保存配药** → **发药**；或对单行 **作废** |


### 1.3 术语与口径约定


| 术语 | 口径 |
| --- | --- |
| **待发药患者队列** | 以**住院就诊**为粒度；含 **在院** 与 **出院轨**（办理出院后仍可见，直至无匹配发放事实）；展示字段来自 **InpatientEncounterContextQueryModel**（住院号、姓名、病区、床位等） |
| **处方列表 / 发放行** | 持久化粒度仍为药品发放明细行；**UI 虚拟发药单**按护士执行 **`Procedure.recorded_time` 自然日**分块（非处方号、非汇总单号）；同日内同 **`medReqId`** 多次执行可合并展示并累加数量，提交/扫码仍映射底层多条发放行；行上库房/批号/数量为**只读**；**单价/金额**只读展示，来自 **ChargeItem**（与发放分段 **1:1**） |
| **保存配药** | `PREPARATION→IN_PROGRESS`；记录调配药师、追溯码等；**不**接受用户改选库房/批号/实发数量；按发放单**已持久化**库房+批号+数量校验库存与医嘱数量口径 |
| **发药（核发）** | `IN_PROGRESS` 下再次校验库存后，按发放单所载扣减库存 → `COMPLETED`；追溯码出库 |
| **作废** | `PREPARATION` 或 `IN_PROGRESS` → `DECLINED` + **未发药原因 code**（`notPerformedReasonEnum` 字符串）；已 `COMPLETED` 走退药专流程（本期不做） |
| **QueryService 能力** | 队列展示 **InpatientEncounterContextQueryModel**；明细 **MedicationDispenseSlip** |
| **DM 路径** | `docs/领域/DomainModel/` 下对应文件 |


### 1.4 本期范围与非目标


| 类别 | 内容 |
| --- | --- |
| **本期** | IF-IPP-01～06：初始化、在院患者队列（**含病区筛选**）、药品发药单明细、**保存配药**、**发药**、**作废**；已发/待发 Tab；中西药切换；调配药师、追溯码 |
| **待接通** | 前端页面与接口接线以实现为准 |
| **非目标** | **汇总发药**（`SUMMARIZED`、`MedicationSummarySlip`）、住院发药单据（**AS-IP-PHA-002**）、护士站汇总领药（**AS-IP-NUR-010**）；**退药**；**耗材**；按长期/临时/`dispense_enum` 分流交互；**候选库房×批号库存**及用户改批号/改数量；**收费明细比价**（改批号重算账单）；**医保 3505**（发药时不上报：无住院结算 `setl_id`；统一由清床/无床直结合并上传，见 **chs-jilin/09**）；以结算/收费完成作为发药前置；UI 布局与组件选型 |


### 1.5 领域清单（聚合 + 职责 + DM 路径）


| 聚合（域） | 在本场景中的职责（摘要） | DM 路径 |
| --- | --- | --- |
| **Encounter** | 住院就诊锚点；与在院状态、病区位置一致 | `DM-ADM-002-Encounter-就诊.md` |
| **Patient** | 患者摘要 | `DM-ADM-001-Patient-患者.md` |
| **MedicationRequest** | 数量与单位对齐、拆分行校验锚点 | `DM-MED-003-MedicationRequest-药品申请.md` |
| **MedicationDispense** | 配药/发药状态迁移、批号/追溯/人员/时间（库房批号数量由上游写入）；挂接 **Procedure** 供按日虚拟分单 | `DM-MED-002-MedicationDispense-药品发放.md` |
| **Procedure** | 护士执行 **`recorded_time`**（IF-IPP-03 日分单真源；**非** planned） | `DM-CLN-004-Procedure-诊疗执行.md` |
| **InventoryItem** | 保存配药与核发时库存充足校验；核发扣减 | 物资库存相关 DM（以项目定稿为准） |
| **TraceNoManage** | 追溯码出库 | 追溯码管理 DM（以项目定稿为准） |
| **MedicationDefinition** | 目录展示、拆零比等 | 药品定义 DM（以项目定稿为准） |
| **InpatientEncounterContextQueryModel（QueryService）** | 在院队列展示、病区/床号/住院号关键字 | `DM-ADM-002 §6.2 EncounterQueryService（InpatientEncounterContextQueryModel）` |


### 1.6 业务规则（BR-*）


| 规则 ID | 描述 | 绑定 IF |
| --- | --- | --- |
| **BR-IPP-001** | **就诊队列**包含 **InpatientEncounterContextQueryModel 就诊状态 ∈ { IN_PROGRESS, ON_HOLD, PENDING, COMPLETED, DISCHARGED }** 的住院就诊（在院 + **办理出院后至清床离院**，便于待发核发与已发回查）；且存在 **药品 MedicationDispense**（**`summary_no` 为空**，见 **BR-IPP-012**），`DispenseStatus ∈ { PREPARATION, IN_PROGRESS, COMPLETED }`（默认不含 **PREPARED**）。**管辖库位非空**时 `location_id` ∈ **`CurrentManagedLocationIdsResolver#listManagedLocationIdsOrEmpty`** 并集；**管辖 empty** 时不拼 `location_id` 谓词（过渡，待身份模型注入）。**不按** `dispense_enum`（长期/临时）额外收窄。可选 **wardId**（仅前台传入时）：活动病区/床优先，**已清床**回落末次已完成病区/床。关键字模糊 **patient_name、encounter_bus_no、bed_name**（与预交金/护士站队列同族；清床后床号可命中末次床）。**队列不按** `status_changed_time` **时间窗筛选**；就诊排序按该就诊下命中发放行的 **`max(created_at)` 倒序**。**待发药**：就诊下仍有 `PREPARATION` 或 `IN_PROGRESS`；**已发药**：存在 `COMPLETED`（与明细 Tab 一致） | IF-IPP-02 |
| **BR-IPP-002** | **发药单列表**仅 **药品**（排除耗材；**排除** `summary_no` 非空行，见 **BR-IPP-012**）；按就诊、中药标识、`dispenseStatusEnumIn` 等过滤；**库房收敛与 BR-IPP-001 同源**：**管辖库位非空**且请求**未显式传** `locationId` 时，`MedicationDispense.location_id` ∈ **`CurrentManagedLocationIdsResolver#listManagedLocationIdsOrEmpty`**；**管辖 empty** 时不拼 `location_id` 谓词；**不装配**候选库房×批号库存；展示字段语义对齐门诊发药 SDD 发放行 DTO，**不含** `inventoryDetailList`；**批号**优先 **MedicationDispenseSlip** QueryService，领域聚合 **`lot_number` 非空时覆盖**；**单价/金额**只读展示，锚定 **ChargeItem** 且与发放分段 **1:1**（`dispense_table`+`dispense_id`，**不求和、不加权**；与门诊 IF-OPP-03 按 req 聚合口径**不同**）；枚举存 **String code**；出参须含 **`procedureId`/`recordedTime`**（护士执行锚点），供前端按日虚拟分单 | IF-IPP-03 |
| **BR-IPP-014** | **按日虚拟发药单（展示口径）**：选定患者后，未发/已发 Tab 各自过滤后，前端按 **`recordedTime` 自然日**虚拟分多张发药单（**不落库日单号**，与 **AS-IP-PHA-002 / AS-IP-NUR-010** 无关）；工作站与门诊同构提供 **发药单笺面 + 列表明细** 双视图；同日同 **`medReqId`** 多次执行合并为一行累加数量，不同医嘱即使同药亦分行；扫码应扫量按合并数量，提交仍按底层多条 `dispenseId`（配药/核发/追溯与门诊同构） | IF-IPP-03 UI / IF-IPP-04～05 |
| **BR-IPP-003** | **保存配药**前：追溯码与已出库追溯码求交非空则拒绝；追溯码写入 **`MedicationDispense.trace_no`** | IF-IPP-04 |
| **BR-IPP-009a** | **发药**成功后须追加 **`cus_trace_number` + `cus_trace_number_flow` OUT**（**`TraceNumberDispenseTraceOrchestrator#recordDispenseOutLines`**；盖住写 OUT、盖不住软跳过，同门诊 **BR-OPP-014**） | IF-IPP-05 |
| **BR-IPP-004** | **保存配药**须指定调配药师，且**不得**与当前登录药师同人 | IF-IPP-04 |
| **BR-IPP-005** | **保存配药**仅允许 `PREPARATION` | IF-IPP-04 |
| **BR-IPP-006** | **仅临时医嘱**（`TherapyType.TEMPORARY`）：同一 `MedicationRequest` 下拆分的多条 `MedicationDispense`，待发/在途行数量之和等于请求**总量**；**长期医嘱**按次生成多条发放，单次用量见 `DM-MED-003` §4.2，**不适用**本合计规则 | IF-IPP-04 |
| **BR-IPP-007** | **保存配药**：每条发放行实发数量与所服务医嘱开立数量一致（拆分行合计见 BR-IPP-006）；**禁止**通过本接口修改库房/批号/数量 | IF-IPP-04 |
| **BR-IPP-008** | **保存配药**：按发放单**已持久化**的库房+批号+数量校验可用库存充足。口径：`可用 = 该批物理 − 同库房同品**同批号**待发预占`（排除本行；**GAP-EXE-003**）；批号已拆分时**禁止**跨批计入预占 | IF-IPP-04 |
| **BR-IPP-009** | **发药**仅允许 `IN_PROGRESS`；扣减前**再次**按发放单所载库房+批号+数量校验库存（与 **BR-IPP-008** 同批预占口径；数量按 **BR-IPP-015** 先换成拆零）；成功后 `COMPLETED`、按**拆零量**扣库、追溯出库 | IF-IPP-05 |
| **BR-IPP-015** | **库存断言与扣库换算**与门诊 **BR-OPP-017** 同口径：发放行 **`unit_code`（操作单位）→ 拆零**（目录五类倍数优先）；同码「盒」禁止用贴码倍数覆盖五类；**仅游离贴码单位**才回落 `trace_unit_ratio` | IF-IPP-04 / 05 |
| **BR-IPP-010** | **作废**仅 `PREPARATION` 或 `IN_PROGRESS`，须提供未发药原因 **code**（`notPerformedReasonEnum`）→ `DECLINED` | IF-IPP-06 |
| **BR-IPP-011** | 本 AS **不处理**耗材、汇总发药、退药、改批号改数量、医保、结算前置 | IF-IPP-01～06 |
| **BR-IPP-012** | **按患者发药**队列（IF-IPP-02）与发药单列表（IF-IPP-03）**排除** `summary_no` 非空的发放行；护士站汇总绑定后及汇总核发后仅在 **AS-IP-PHA-002** 汇总 Tab / **AS-RPT-004** 住院发药报表查询 | IF-IPP-02、03 |
| **BR-IPP-013** | **追溯码软硬边界（同构门诊 BR-OPP-016 / AS-CUS-TRC-001 · BR-TRC-023）**：保存配药禁止整单跨行码去重硬拒；仅单行内同码硬拒；待扫/绑码硬上限=ceil(操作量)；跨行剩余不足仅软提示 | IF-IPP-04 UI / Pre |


---

## 2. 页面与接口总览（契约概览）


| IF 编号 | 用户动作 | 接口语义（业务） | 读/写 | 关键规则（BR） | 关键状态变化 | 涉及领域 | 查询服务 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| IF-IPP-01 | 进入住院发药页 | 下发初始化枚举（**可操作药房 Location**、病区候选、已发/待发筛选值集、调配药师候选等） | 读 | — | — | **Location**（药房+病区）/ Practitioner | — |
| IF-IPP-02 | 病区/关键字/已发待发筛选或刷新 | 分页查询**队列可见**就诊（在院 + 出院轨；存在药品发放且状态子集命中；**管辖库位非空时** `location_id IN`；可选病区） | 读 | BR-IPP-001 / BR-IPP-011 | — | Encounter / MedicationDispense | **InpatientEncounterContextQueryModel** |
| IF-IPP-03 | 选患者并切换中西药 | 分页加载就诊下**药品**发药单/发放行（**管辖库位非空时**服务端 `location_id IN`，与 IF-IPP-02 同源；只读批号数量；无候选库存） | 读 | BR-IPP-002 / BR-IPP-011 | — | MedicationRequest / MedicationDispense | MedicationDispenseSlip |
| IF-IPP-04 | 勾选行、选调配药师、提交保存 | **保存配药**：追溯码/调配药师；库存与数量校验；**不改**库房批号数量 | 写 | BR-IPP-003～008 / BR-IPP-011 | PREPARATION→IN_PROGRESS | MedicationDispense / TraceNoManage | MedicationDispenseSlip |
| IF-IPP-05 | 对已保存配药行点击发药 | **核发**：再校验库存→**拆零**扣库（**BR-IPP-015**）→追溯出库 | 写 | BR-IPP-009 / BR-IPP-011 / **BR-IPP-015** | IN_PROGRESS→COMPLETED；库存减 | 用药/库存/追溯 | MedicationDispenseSlip |
| IF-IPP-06 | 作废待发发放行 | **DECLINED** + 原因 | 写 | BR-IPP-010 / BR-IPP-011 | PREPARATION 或 IN_PROGRESS→DECLINED | MedicationDispense | MedicationDispenseSlip |


---

## 3. 缺口跟踪（Gap）

**无待跟踪缺口**（领域层能力已于 2026-05-24 落地：`MedicationDispenseQueryService#pageEncounterIdsForInpatientPharmacyPending`、`EncounterContextReader#listInpatientContextsByEncounterIdsOrdered`、`MedicationDispenseSlipPageQuery#inpatientMedicationOnly`）。


---

## 4. 接口编排说明书（业务步骤）

### IF-IPP-01 发药页初始化


| 维度 | 说明 |
| --- | --- |
| **触发前提** | 用户进入住院发药工作站 |
| **主路径步骤** | 装载**管辖内启用药房**（`adm_location`，`pharmacyOrganizationOptions` 的 value 为 locationId）、**调配药师候选**（须 **`ROLE_PHARMACIST`** 且 `adm_pr_assign.location_id` 落在请求 `pharmacyLocationId` 或管辖内启用药房）、未发药原因、发药状态下拉、**病区候选**（供队列筛选）、其他页面所需值集 |
| **关键分支** | — |
| **失败分支** | 元数据不可用（EX-IPP-101） |
| **查询侧说明** | 无 |


### IF-IPP-02 在院患者队列查询（含病区 / 已发待发）


| 维度 | 说明 |
| --- | --- |
| **触发前提** | 用户已登录且会话具备机构上下文；设定**病区**（可选）、关键字、已发药/待发药或刷新 |
| **主路径步骤** | 解析用户管辖库位（非空时写入 `locationIds`）→ 按 **MedicationDispense** 收敛 encounterId（药品发放、状态子集、可选库房 IN）→ 与队列可见就诊求交（在院 + 出院轨、可选病区、关键字）→ 按就诊 **`max(created_at)`** 倒序分页；批量回补 **InpatientEncounterContextQueryModel** 展示字段；本页就诊批量回填费用性质 `contractDisplay`（与门诊发药/医生站卡片同口径） |
| **关键分支** | 待发药：`PREPARATION`∪`IN_PROGRESS`；已发药：`COMPLETED`；管辖库位 empty 时不按库房收窄 |
| **失败分支** | 未登录或会话无效（EX-IPP-102）；查询失败（EX-IPP-102） |
| **查询侧说明** | 无（读侧查询） |


### IF-IPP-03 就诊药品发药单列表 / 明细加载


| 维度 | 说明 |
| --- | --- |
| **触发前提** | 已选住院就诊；可切换西药/中药；可选与队列一致的已发/待发；**不传**发药库房（与门诊 IF-OPP-03 一致，按就诊收敛全量待发/已发行） |
| **主路径步骤** | 分页查询 **MedicationDispenseSlip**（`inpatientMedicationOnly`；未传 `locationId` 时不拼库房谓词）→ 批量加载 **MedicationRequest** / **ProductDefinition** 展示字段（含规格旁 **`packageSplitRatioText`** 包装最小单位换算比，如 `1盒/36片`）→ 按本页 `dispenseId` 回补 **MedicationDispense** 聚合 **`lot_number`/`trace_no`/`procedure_id`**（非空覆盖 slip）→ 按 **`procedure_id`** 批量回填 **`Procedure.recorded_time`** → 按 **`ChargeItem` 与发放分段 1:1** 回填 **`unitPrice`/`totalPrice`**（状态含 DRAFT/PLANNED/SETTLED）→ 术语与目录装配；**不**返回候选库存；**不**做保存配药前 ChargeItem 比价；前端按 **BR-IPP-014** 虚拟日分单 |
| **关键分支** | 待发药：`PREPARATION`、`IN_PROGRESS`；已发药：`COMPLETED` |
| **失败分支** | 就诊无效或无权（EX-IPP-103） |
| **查询侧说明** | 无 |


### IF-IPP-04 保存配药


| 维度 | 说明 |
| --- | --- |
| **触发前提** | 已选调配药师；勾选 `PREPARATION` 行；可选提交追溯码 |
| **主路径步骤** | 追溯码占用校验；调配药师非本人；状态 `PREPARATION`；数量与医嘱一致（BR-IPP-006/007）；按发放单**既有**库房+批号+数量校验库存（BR-IPP-008）；**不**改写库房/批号/数量；`PREPARATION→IN_PROGRESS` 并记录调配者与时间 |
| **失败分支** | EX-IPP-201～204、EX-IPP-205（见 §5） |
| **查询侧说明** | MedicationDispenseSlip |


### IF-IPP-05 发药（核发）


| 维度 | 说明 |
| --- | --- |
| **触发前提** | 勾选 `IN_PROGRESS` 行 |
| **主路径步骤** | 发放前再次校验发放单所载库房+批号库存充足；按发放单扣减库存；`IN_PROGRESS→COMPLETED`；发药人/时间；**`recordDispenseOutLines`** 写追溯 OUT（盖住写、盖不住软跳过，**BR-OPP-014** 同构） |
| **失败分支** | EX-IPP-203、EX-IPP-205 |
| **查询侧说明** | MedicationDispenseSlip、库存与追溯投影 |


### IF-IPP-06 作废发放


| 维度 | 说明 |
| --- | --- |
| **触发前提** | 用户确认作废原因（提交 `notPerformedReasonEnum` code） |
| **主路径步骤** | 仅 `PREPARATION` 或 `IN_PROGRESS`；置 `DECLINED` 并写入原因 |
| **失败分支** | EX-IPP-208 |
| **查询侧说明** | MedicationDispenseSlip |


---

## 5. 业务异常与用户提示


| 异常 ID | 场景 | 用户提示语义 | 可重试 |
| --- | --- | --- | --- |
| EX-IPP-101 | 初始化失败 | 元数据加载失败 | 是 |
| EX-IPP-102 | 队列查询失败 | 列表加载失败 | 是 |
| EX-IPP-103 | 明细加载失败 | 无法加载发药单或无权访问 | 视权限 |
| EX-IPP-201 | 追溯码已出库 | 列出冲突追溯码 | 更换后重试 |
| EX-IPP-202 | 调配药师无效或同人 | 请选择调配药师 | 是 |
| EX-IPP-203 | 状态不符 | 配药或发药失败，请检查发药单状态 | 刷新后重试 |
| EX-IPP-204 | 单位/总量不匹配 | 配药失败：请检查单位或数量是否与医嘱一致 | 是 |
| EX-IPP-205 | 库存停供或不足 | 按品种提示 | 联系库管或核对执行批号 |
| EX-IPP-208 | 作废不允许或原因缺失 | 无法作废或请选择作废原因 | 视状态刷新 |


---

## 6. 事件语义与消费方


| 事件（语义级） | 触发点 | 消费方 / 意图 | 幂等语义 |
| --- | --- | --- | --- |
| 发放行 PREPARATION→IN_PROGRESS | 保存配药成功 | MedicationDispenseSlip、占用统计 | 以发放行 ID |
| 发放行 IN_PROGRESS→COMPLETED | 发药成功 | 同上；费用/对账（域外） | 同上 |
| 发放行→DECLINED | 作废成功 | MedicationDispenseSlip | 同上 |
| 库存扣减 | 核发 | 库存QueryService | 库存项+事务 |
| 追溯码出库 | 核发 | 监管查询 | 码+单号去重 |


---

## 7. 非功能性需求


| 类别 | 要求 |
| --- | --- |
| **性能** | 队列与明细分页满足工作站交互 |
| **并发** | 同一发放行并发配发须门禁防重复扣库 |
| **一致性** | 保存配药与发药可分事务；同一行须先保存后发药 |
| **审计** | 调配/发药人员与时间、批号与追溯码 |
| **安全** | 禁止调配药师与当前登录药师同人 |


---

## 8. 测试要点（业务视角）


| 类别 | 要点 |
| --- | --- |
| **正向** | 在院与出院轨队列；病区筛选；关键字（含床号）；已发/待发；IF-IPP-03 不传 `locationId` 能加载全量待发/已发行；批号/单价/金额展示；无候选库存；保存配药→发药状态与库存；作废 DECLINED |
| **规则** | BR-IPP-001～011 |
| **边界** | 办理出院（PENDING）/已结算待清床（COMPLETED）/已清床（DISCHARGED）只要仍有匹配发放事实即出现；管辖库位 empty 时不拼库房谓词；不可改批号数量 |
| **并发** | 同号并发一线成功 |


---

## 9. 关联文档与变更历史


| 类型 | 文档 |
| --- | --- |
| **DM** | `DM-MED-002-MedicationDispense-药品发放.md`、`DM-MED-003-MedicationRequest-药品申请.md`、`DM-ADM-002-Encounter-就诊.md` |
| **QueryService** | `DM-MED-002 §6.2 MedicationDispenseQueryService 发药单分页`、`DM-ADM-002 §6.2 EncounterQueryService（InpatientEncounterContextQueryModel）` |
| **上下游 AS** | `AS-IP-NUR-004-住院护士站医嘱执行.md`（执行产生待发发放行） |
| **对照 AS** | `AS-OP-PHA-001-门诊发药.md`（工作站交互同族；住院裁剪汇总/退药/改批号/医保/候选库存；**IF-IPP-03 价格**：住院 **ChargeItem↔发放 1:1**，门诊 **按 dispenseId 加权聚合**） |


| 版本 | 日期 | 变更摘要 |
| --- | --- | --- |
| v2.9 | 2026-08-31 | **IF-IPP-03**：发放行增 **`packageSplitRatioText`**（包装最小单位换算比展示，如 `1盒/36片`；规格列后；与门诊/库存明细同族）；对齐 SDD **v2.9** |
| v2.8 | 2026-08-19 | **BR-IPP-015**：住院发药库存断言/扣库与门诊 **BR-OPP-017** 同口径（操作单位→拆零，禁止追溯码倍数扣库）；对齐 SDD **v2.8** |
| v2.7 | 2026-08-17 | **BR-IPP-001 / IF-IPP-02**：队列可见态扩至 **PENDING/COMPLETED/DISCHARGED**（办理出院后仍可待发核发与已发回查）；病区/床号对已清床回落末次位置；对齐 SDD **v2.7** |
| v2.6 | 2026-08-07 | **BR-IPP-014 / IF-IPP-03**：按患者发药 UI 改为按护士执行 **`recorded_time` 自然日**虚拟发药单，并启用与门诊同构的 **发药单笺面 + 列表明细**；同日同 `medReqId` 合并展示累加数量；出参补 **`procedureId`/`recordedTime`**；与汇总发药/汇总领药无关；对齐 SDD **v2.6** |
| v2.5 | 2026-08-05 | **BR-IPP-013**：保存配药追溯码按行去重/扫多硬拦/跨行同码放行；对齐 **AS-CUS-TRC-001** v2.6 / **DD** v1.6 §3.4 / 门诊 **BR-OPP-016** |
| v2.4 | 2026-08-02 | **IF-IPP-03 / UI**：slip 出参补贴码单位术语文案；批量扫码弹窗药品明细展示追溯码单位与码/拆零比；对齐 SDD **v2.4** / **AS-CUS-TRC-001** v2.4 |
| v2.3 | 2026-07-29 | **BR-IPP-008/009**：库存断言预占与物理**同批号**口径（与门诊 **BR-OPP-008/009** / GAP-EXE-003 对齐） |
| v2.4 | 2026-08-08 | **IF-IPP-05**：明确发药时不上报医保 3505（无 setl_id）；院内 OUT 保留；3505 由清床/无床直结合并上传 |
| v2.3 | 2026-07-31 | **IF-IPP-02**：队列回填 `contractDisplay`（与门诊发药/医生站卡片同口径）；对齐 SDD **v2.3** |
| v2.2 | 2026-07-13 | **IF-IPP-03 / BR-IPP-002**：发药单明细与 IF-IPP-02 队列**同源**按管辖库位 `location_id IN` 收窄；废止 v1.5「未传 locationId 不按库房收窄」 |
| v2.1 | 2026-07-04 | **BR-IPP-009a**：IF-IPP-05 核发后 **`cus_trace_number` + `cus_trace_number_flow` OUT**（锚定 **`MedicationDispense.trace_no`**） |
| v1.9 | 2026-06-09 | **IF-IPP-01**：药房下拉改 **`adm_location`**；废止 `getOrganizationTreeForPharmacy`；`pharmacyOrganizationOptions.value`=locationId；对齐 SDD **v1.9** |
| v1.8 | 2026-05-28 | 读模型废止后对齐 zxplat 实现（EncounterContextFacade / querysupport Loader）。 |（已迁至 EncounterContextReader / application.shared.encounter）
| v1.0 | 2026-05-24 | 初版：IF-IPP-01～06；在院队列+病区筛选；无候选库存与改批号；不做汇总/退药/医保/结算前置；§3 领域层 Gap 三条 |
| v1.1 | 2026-05-24 | §3 领域层 Gap 已关闭：`pageEncounterIdsForInpatientPharmacyPending`、`EncounterContextFacade#listInpatientContextsByEncounterIdsOrdered`、`inpatientMedicationOnly` |（已迁至 EncounterContextReader / application.shared.encounter）
| v1.2 | 2026-05-24 | 与 AS-SDD/实现对齐：未发药原因初始化下拉枚举未落库前为空；作废仍提交 `notPerformedReasonEnum` code；**BR-IPP-006** 明确仅 `TherapyType.TEMPORARY`，长期医嘱不适用拆分行合计 |
| v1.3 | 2026-05-24 | **IF-IPP-02 / BR-IPP-001**：队列**禁止**以 `status_changed_time` 时间窗筛选；就诊排序改为 `max(created_at)` 倒序；**wardId** 仅前台传入时生效；对齐 SDD **v1.3** 与实现对齐 |
| v1.4 | 2026-05-24 | **IF-IPP-02 / BR-IPP-001**：队列库房收敛改为 **`CurrentManagedLocationIdsResolver`**；**禁止**以登录科室 `listPharmaciesByOrganization` 替代；管辖 empty 时不拼 `location_id` 谓词；对齐 SDD **v1.4** |
| v1.5 | 2026-05-24 | **IF-IPP-03**：`locationId` 改为**可选**（对齐 IF-OPP-03）；工作站选患者后通常仅传 `encounterId` 与 `dispenseStatusEnumIn`；未传时不按发放库房收窄 |
| v1.6 | 2026-05-24 | 与 SDD **v1.4～v1.5**、对话 B 实现对齐：IF-IPP-02 管辖库位 Gate、队列时间轴（禁 `status_changed_time` 筛选、`max(created_at)` 排序）；§1.1 去除队列「日期」筛选表述 |
| v1.7 | 2026-05-24 | **IF-IPP-03 / BR-IPP-002**：**批号**——`MedicationDispenseCreatedEvent` 载荷补 `lotNumber` 投影至 slip，列表以领域聚合回补覆盖；**单价/金额**——`ChargeItem` 与发放分段 **1:1** 直取（**非**门诊加权聚合）；对齐 SDD **v1.6**、`DM-MED-002` CreatedEvent、`MedicationDispenseSlip` QueryService **v2.5** |
