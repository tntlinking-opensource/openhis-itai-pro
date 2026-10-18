# 住院药房 · 退药管理 · 应用服务设计（AS）

> **文档职责**：说明住院药房在护士站**取消执行**（已发药路径）产生待退药单后，完成退药确认（实物退回、库存回补、追溯入库）的业务流程、接口契约与编排意图。待退药单由 **AS-IP-NUR-012** 生成，本场景**仅**推进发放状态与库存/追溯补偿；**不**编排取消执行、**不**重复冲减/回退账单。
>
> **本文必须写**：IF 编号；用户动作与读写属性；关键校验与状态变化；Gap；异常口径；事件语义；非功能约束；业务向测试要点。
>
> **本文禁止写**：具体类名/应用方法名；Request/Response 字段明细；包路径；Mapper；伪代码；写库顺序；QueryService 字段组装规则。
>
> **页面边界**：住院药房工作站「退药管理」——加载初始化元数据 → 查询待退/已退**在院**患者列表 → 查看退药明细（**单条发放行**）→ **确认退药**。与护士站取消执行、库存管理、追溯码管理、出院门禁（**AS-IP-NUR-002**）通过领域事实与事件联动。
>
> **成码对齐**（ZXPlat 工程落地时）：须遵守《**代码生成通用规范**》**B.10.8.1**（状态/枚举持久化 `getCode()` 字符串）、**B.10.11**（分页参数约定）、**B.14**（领域事件须在持久化成功之后发布）。
>
> **编码**：AS-IP-PHA-003 · **版本**：v1.5 · **日期**：2026-08-19

---

## 1. 场景概述

### 1.1 目标与边界

| 维度 | 说明 |
| --- | --- |
| **在内** | 加载退药页初始化元数据（读）；分页查询**在院**就诊下存在待退/已退**药品**发放事实的患者队列（读）；按就诊查询**单条**退药发放明细（读）；**确认退药**（写：状态推进、原已发发放行收口、库存回补、追溯码入库） |
| **在外** | 护士站取消执行写路径（**AS-IP-NUR-012 IF-IP-CXL-01**）；汇总领药/汇总核发（**AS-IP-NUR-010** / **AS-IP-PHA-002**）；逐行住院发药（**AS-IP-PHA-001**）；**拒绝退药**；门诊退药（**AS-OP-PHA-002**）；耗材退药；医保退货；部分数量退药；住院结算/退费编排 |
| **涉及域** | **Encounter**；**MedicationRequest**（原药嘱只读）；**MedicationDispense**；**InventoryItem**；**TraceNumberRecord** |
| **编排权威** | 以 **AS-IP-NUR-012** 生成的 `PENDING_REFUND` **单条**待退发放单为入口；本场景仅推进至 `REFUNDED`；费用回退已在取消执行完成，本场景**不写** ChargeItem |

### 1.2 角色与关键动作

| 角色 | 关键动作 |
| --- | --- |
| **住院药房药师** | 打开退药管理 → 按病区/关键字筛在院患者 → 选择患者查看待退**发放行** → 核对实物 → **确认退药** |
| **系统** | 加载病区/退药状态筛选项；按就诊分页收敛队列；展示单条待退/已退明细；确认后推进状态、回补库存、追溯入库；释放出院门禁中的待退药占用（**AS-IP-NUR-002 BR-IP-ATD-011**） |

### 1.3 术语与口径约定

| 术语 | 口径 |
| --- | --- |
| **待退药发放单** | **AS-IP-NUR-012** 对已 **COMPLETED** 原发放行调用待退生成后的一条 **`PENDING_REFUND`** 发放行；**一行待退对应一行原已发事实**；与汇总单号 **无** 页面级合并展示 |
| **原已发发放行** | 取消执行前已 **COMPLETED** 的正向发放行（`dispense_enum` 非 REFUND）；**确认退药时不修改其状态**；发药统计计正向行，退药统计计 `dispense_enum=REFUND` 且 `REFUNDED` 的对冲行 |
| **procedure 锚点** | 待退行 `procedure_id` 挂 **CANCEL** 对冲 Procedure；`med_req_id` **沿用**原发放药嘱（对齐 **AS-IP-NUR-012 §1.3**） |
| **批号/追溯** | 库存回补与追溯入库使用**原已发发放行**（或待退行已拷贝）上的 `lot_number` / `trace_no`；本页**不可**改批号 |
| **确认退药** | 药房收实物后：`PENDING_REFUND` → `REFUNDED`；全量退回（`dispense_quantity = quantity`） |
| **出院门禁** | 就诊下仍存在 `PENDING_REFUND` 时 **AS-IP-NUR-002** 拒绝办理出院；本场景确认成功后该占用消除，**无需**本页单独编排出院 |
| **全退** | 不支持部分数量退药 |

### 1.4 本期范围与非目标

| 类别 | 内容 |
| --- | --- |
| **本期（契约）** | **IF-IPR-01～04**：初始化、分页队列、退药明细、**确认退药** |
| **本期（UI）** | 住院药房「退药管理」页（交互同族 **AS-OP-PHA-002**，**无**拒绝退药按钮） |
| **非目标** | **拒绝退药** / `DECLINED` 写路径；汇总 Tab / 汇总单维度操作；耗材；**医保 3506**（确认退药当时不上报；退量计入清床/无床直结净销售合并，见 **chs-jilin/09**）；双人复核；部分退药；医生站负向药嘱（门诊退费路径）；明细金额展示（可选列，非闭环必需）；已出院患者历史退药队列 |

### 1.5 领域清单（聚合 + 职责 + DM 路径）

| 聚合（域） | 在本场景中的职责（摘要） | DM 路径 |
| --- | --- | --- |
| **Encounter** | 在院就诊锚点；队列与 **InpatientEncounterContextQueryModel** 一致 | `DM-ADM-002-Encounter-就诊.md` |
| **MedicationRequest** | 原药嘱只读（名称、规格、开立医生等展示） | `DM-MED-003-MedicationRequest-药品申请.md` |
| **MedicationDispense** | 待退确认（`PENDING_REFUND` → `REFUNDED`，仅 REFUND 对冲行）；**原 COMPLETED 正向行只读、不改态** | `DM-MED-002-MedicationDispense-药品发放.md` |
| **InventoryItem** | 按批号+库房+药品回补库存 | 库存相关 DM |
| **TraceNumberRecord** | 追溯码入库（IN） | `DM-CUS-010-TraceNumberRecord-追溯码记录.md` |

### 1.6 业务规则（BR-*）

| 规则 ID | 规则描述 | 绑定 IF |
| --- | --- | --- |
| **BR-IPR-001** | **患者队列**：就诊 **在院**（`InpatientEncounterContextQueryModel.status_enum ∈ { IN_PROGRESS, ON_HOLD }`）；且存在 **药品** `MedicationDispense` 状态 ∈ `{ PENDING_REFUND, REFUNDED }`；`dispense_enum ∈ { INPATIENT, TEMPORARY, REFUND }`；**管辖库位非空**时 `location_id ∈ CurrentManagedLocationIdsResolver#listManagedLocationIdsOrEmpty()`；**管辖 empty** 时不拼库房谓词；可选 **wardId**、关键字（姓名/住院号/床号，同 **AS-IP-PHA-001 BR-IPP-001**）；Tab：**待退**=`PENDING_REFUND` 仍存在；**已退**=仅有 `REFUNDED` 且无待退 | IF-IPR-02 |
| **BR-IPR-002** | **明细**：按就诊加载 **单条**待退/已退 **药品**发放行；只读展示批号、数量、原药嘱信息；**不含**汇总合并行；可选 `refundStatus` 过滤；**库房收敛与 BR-IPR-001 同源**：**管辖库位非空**时 `location_id ∈ CurrentManagedLocationIdsResolver#listManagedLocationIdsOrEmpty()`；**管辖 empty** 时不拼库房谓词 | IF-IPR-03 |
| **BR-IPR-003** | **确认退药**仅允许 `PENDING_REFUND` | IF-IPR-04 |
| **BR-IPR-004** | 已 **REFUNDED** 不可重复确认（幂等拒绝） | IF-IPR-04 |
| **BR-IPR-005** | 须**全量**退回；不支持部分退药 | IF-IPR-04 |
| **BR-IPR-006** | 确认成功须回补库存（回补量 = 待退行 **`unit_code` 操作单位 → 拆零**，与门诊 **BR-OPP-017** / **BR-PHA-034** 同口径；**禁止** `trace_unit_*`）；追溯 IN 经 **`recordReturnInLines`** **镜像**原发 OUT 每码实扣（**DD §5** / **BR-TRC-008**），**禁止**新建主档或按当前剩余重分摊 | IF-IPR-04 |
| **BR-IPR-007** | 退药写路径**仅**推进 `dispense_enum=REFUND` 待退/已退行；**禁止改写**原 COMPLETED 发药单（统计层通过对冲行，不对原单改态）。追溯镜像与门诊同构：先按退药行 `bus_no`，无则按拷贝 `trace_no` **只读**追溯子表回落未回冲原发 OUT（**BR-IPR-006** / DD §5） | IF-IPR-03/04 |
| **BR-IPR-008** | 本页**不写** ChargeItem；**不**调用门诊负向药嘱完成类写能力 | IF-IPR-04 |
| **BR-IPR-009** | 本页**仅**药品 `MedicationDispense`；**不**处理耗材 | IF-IPR-02～04 |
| **BR-IPR-010** | 本页**不提供**拒绝退药；**不**产生 `DECLINED` | IF-IPR-04 |

---

## 2. 页面与接口总览（契约概览）

| IF 编号 | 用户动作 | 接口语义（业务） | 读/写 | 关键规则（BR） | 关键状态变化 | 涉及领域 | QueryService |
| --- | --- | --- | --- | --- | --- | --- | --- |
| IF-IPR-01 | 打开退药管理页 | 加载初始化元数据（病区候选、退药状态下拉、可操作药房等） | 读 | — | — | Location / Encounter 上下文 | — |
| IF-IPR-02 | 查询/刷新退药患者列表 | 分页查询在院且存在待退/已退**药品**发放事实的就诊 | 读 | BR-IPR-001、009 | — | Encounter、MedicationDispense | 住院退药患者队列 QueryService |
| IF-IPR-03 | 选择患者查看明细 | 按就诊查询**单条**药品退药发放明细 | 读 | BR-IPR-002、009 | — | MedicationDispense、MedicationRequest | 住院退药明细 QueryService |
| IF-IPR-04 | 勾选明细 → 确认退药 | 对勾选待退发放行执行确认 | 写 | BR-IPR-003～010 | 待退 `PENDING_REFUND`→`REFUNDED`（REFUND 行）；原正向 **COMPLETED 不变** | MedicationDispense、InventoryItem、TraceNumberRecord | — |

---

## 3. 缺口跟踪（Gap）

**本期无待办业务缺口。** 下列 Gap 已于 2026-06-10 关闭（领域层 QueryService / 写侧 **`createPendingRefundDispense`** 与 **DM-MED-002** 对齐）。

| Gap ID | 缺口类型 | 文件 + 章节 | 缺什么 / 不一致点 | 关闭条件 | 影响 IF | 优先级 | 状态 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| GAP-IPR-001 | QueryService 能力 | `DM-MED-002` §6.2.13 | **`pageEncounterIdsForInpatientPharmacyReturn`** | 已实现 | IF-IPR-02 | P0 | **已关闭** |
| GAP-IPR-002 | QueryService 能力 | `DM-MED-002` §6.2.13 | **`listInpatientReturnMedicationDispensesForDetail`**、`mapAggregatedInpatientRefundStatusCodeByEncounterIds` | 已实现 | IF-IPR-03 | P0 | **已关闭** |
| GAP-IPR-003 | DM 能力 | `DM-MED-002` §6.1.8 | **`createPendingRefundDispense`** 拷贝 **`lot_number`/`trace_no`**、可选 **`procedureId`** | 已实现 | IF-IPR-04 | P0 | **已关闭** |

> **说明**：`refundDispensedMedication`、出院门禁 **BR-IP-ATD-011** 等**已存在**，不入 Gap；退药与原发药单**无**读写关联——待退 REFUND 行在 **`createPendingRefundDispense`** 时已复制批号/追溯码；明细仅读待退行 + 原药嘱（**BR-IPR-007**）。

---

## 4. 接口编排说明书（业务步骤）

### IF-IPR-01 加载页面初始化元数据

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 用户打开住院药房「退药管理」 |
| **主路径步骤** | ① 装载管辖内启用药房/病区候选（对齐 **AS-IP-PHA-001 IF-IPP-01** 同族）；② 退药状态下拉（`PENDING_REFUND` 待退药、`REFUNDED` 已退药） |
| **关键分支** | — |
| **失败分支** | 元数据加载失败（EX-IPR-101） |
| **查询侧说明** | 无写库 |

### IF-IPR-02 分页查询退药患者列表

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 用户进入页面或执行查询/刷新 |
| **主路径步骤** | ① 解析管辖库房（非空则写入过滤）；② 按 **MedicationDispense** 收敛存在 `PENDING_REFUND` 或 `REFUNDED` 的 **在院** `encounter_id`；③ 与 **InpatientEncounterContextQueryModel** 求交（在院状态、可选病区、关键字）；④ 按就诊下命中发放行的最近状态变更时间倒序分页；⑤ 批量回补住院上下文展示字段；⑥ 本页就诊批量回填费用性质 `contractDisplay`（与门诊发药/医生站卡片同口径） |
| **关键分支** | 待退 Tab：就诊下仍有 `PENDING_REFUND`；已退 Tab：存在 `REFUNDED` 且无待退 |
| **失败分支** | 查询条件非法或系统异常（EX-IPR-102） |
| **查询侧说明** | 无写库；**不按**汇总单号聚合 |

### IF-IPR-03 查询退药明细

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 用户从队列选择某一在院就诊 |
| **主路径步骤** | ① 按就诊加载状态为 `PENDING_REFUND` 或 `REFUNDED` 的**药品**待退/已退发放行（**逐行**）；② 关联原 **MedicationRequest** 展示药品名称、批号、数量、开单医生等（批号/追溯码取自待退行自身，创建待退时已从原单复制）；③ 可选按退药状态过滤 |
| **关键分支** | 无匹配返回空列表 |
| **失败分支** | 就诊无效或无权（EX-IPR-103） |
| **查询侧说明** | 无写库；**不**展示汇总合并明细 |

### IF-IPR-04 确认退药

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 用户勾选一条或多条 **PENDING_REFUND** 明细并点击「确认退药」 |
| **主路径步骤** | ① 校验状态为 `PENDING_REFUND`（**BR-IPR-003**）；② 幂等：已 `REFUNDED` 拒绝（**BR-IPR-004**）；③ 域确认退药：待退 REFUND 行 → `REFUNDED`，写入退药数量（全量）、退药人、退药时间（**BR-IPR-005**）；④ **不**触及原发药单（**BR-IPR-007**）；⑤ 按待退行批号+库房+药品回补库存（**BR-IPR-006**）；⑥ **`recordReturnInLines`** 镜像 OUT 每码 IN（原发未扫跳过）；⑦ **不**写 ChargeItem、**不**完成负向药嘱（**BR-IPR-008**） |
| **关键分支** | 多行批量：药品逐行处理；任一行失败策略以事务边界为准（建议整批回滚） |
| **失败分支** | 状态不允许（EX-IPR-201）；重复确认（EX-IPR-202）；库存信息缺失（EX-IPR-203）；未选择退药项（EX-IPR-301） |
| **查询侧说明** | 成功后队列/明细刷新；**AS-IP-NUR-002** 出院门禁待退列表同步减少 |

---

## 5. 业务异常与用户提示

| 语义代码 | 场景 | 用户提示方向 | 可重试 |
| --- | --- | --- | --- |
| EX-IPR-101 | 初始化加载失败 | 数据加载失败，请稍后重试 | 是 |
| EX-IPR-102 | 队列查询失败 | 列表加载失败，请稍后重试 | 是 |
| EX-IPR-103 | 明细加载失败/无权 | 无法加载退药明细 | 视权限 |
| EX-IPR-201 | 非 `PENDING_REFUND` | 当前状态不允许退药，请刷新后重试 | 否 |
| EX-IPR-202 | 已 `REFUNDED` 重复确认 | 该药品已退药，请勿重复操作 | 否 |
| EX-IPR-203 | 批号/库存项缺失 | 库存信息异常，请联系管理员 | 否 |
| EX-IPR-301 | 未勾选退药项 | 请选择要退药的药品 | 否 |
| EX-IPR-410 | 确认成功 | 退药成功 | — |

---

## 6. 事件语义与消费方

| 事件语义（自然语言 / 英文名以 DM 为准） | 触发相位 | 典型消费方 |
| --- | --- | --- |
| 药品已退药（`MedicationDispenseRefundedEvent`） | IF-IPR-04 确认成功 | 库存查询；追溯码查询 |
| 待退药已生成（`MedicationDispensePendingRefundCreatedEvent`） | **AS-IP-NUR-012**（上游） | 本页退药队列（IF-IPR-02） |
| 追溯码已入库（`TraceNumberInboundedEvent`，若 DM 定义） | IF-IPR-04 确认成功 | 追溯码 QueryService |

> **费用**：取消执行已发布 `ChargeItemExecutionCancelledEvent` 等；本 IF **不**重复触发费用冲销。

---

## 7. 非功能性需求

| 类别 | 要求 |
| --- | --- |
| **性能（经验目标）** | IF-IPR-01 ≤300ms；IF-IPR-02 ≤800ms；IF-IPR-03 ≤500ms；IF-IPR-04 ≤3000ms（含库存回补、追溯写入） |
| **幂等** | 重复确认须被 **BR-IPR-004** 拦截 |
| **并发** | 同一待退行并发确认以后到者状态校验失败 |
| **审计** | 须记录退药操作人、操作时间 |
| **成码规范（ZXPlat）** | 枚举/状态落库 **String `code`**（**B.10.8.1**）；分页 **1-based**（**B.10.11**）；事件 **B.14** |
| **出院一致性** | 确认成功后就诊下不应再存在 blocking `PENDING_REFUND`（与 **BR-IP-ATD-011** 一致） |

---

## 8. 测试要点（业务视角）

| 类别 | 要点 |
| --- | --- |
| **正向** | 取消执行产生待退 → 队列可见 → 明细单条展示 → 确认后待退 REFUND 行 `REFUNDED`、原正向仍 `COMPLETED`；库存回补；追溯 IN |
| **队列** | 在院过滤；病区/关键字；待退/已退 Tab；管辖库房 |
| **异常** | 重复确认；非 `PENDING_REFUND` 确认；批号缺失 |
| **规则** | BR-IPR-001～010；全量数量；**无**拒绝退药入口 |
| **出院** | 有待退时 **AS-IP-NUR-002** 拒绝出院；确认退药后可出院（配合 ATD-05 联测） |
| **边界** | 与汇总核发**无**页面耦合；一行待退对应一行 REFUND 对冲发放 |

---

## 9. 关联文档与变更历史

### 9.1 关联文档

| 文档 |
| --- |
| `docs/应用/zxplat-application/AppService/AS-IP-NUR-012-住院护士站取消执行.md |
| `docs/应用/zxplat-application/AppService/AS-IP-NUR-002-住院护士站入出转管理.md |
| `docs/应用/zxplat-application/AppService/AS-IP-PHA-001-住院发药.md |
| `docs/应用/zxplat-application/AppService/AS-OP-PHA-002-门诊退药.md`（同族对照；**无**拒绝退药） |
| `docs/领域/DomainModel/DM-MED-002-MedicationDispense-药品发放.md` |
| `docs/领域/DomainModel/DM-MED-003-MedicationRequest-药品申请.md` |

### 9.2 变更历史

| 版本 | 日期 | 变更摘要 |
| --- | --- | --- |
| v1.6 | 2026-08-20 | **BR-IPR-007**：明确禁止改写原单；追溯镜像允许按拷贝 `trace_no` 只读子表回落未回冲 OUT（与门诊 v1.8 / DD §5 同构） |
| v1.5 | 2026-08-19 | **BR-IPR-006**：回补量 = 待退行 **操作单位→拆零（五类）**，与门诊 **BR-OPP-017** / **BR-PHA-034** 同口径 |
| v1.4 | 2026-08-08 | **非目标**：明确确认退药不报医保 3506（与发药不报 3505 对称；净量进清床/无床直结合并） |
| v1.3 | 2026-07-31 | **IF-IPR-02**：队列回填 `contractDisplay`（与门诊发药/医生站卡片同口径）；对齐 SDD **v1.5** |
| v1.2 | 2026-07-02 | **IF-IPR-04**：追溯入库 **`location_type_enum`** 与供应头同源（**`requireSupplyRequestTypeForLocationId`**，科室药房→`PHARMACY`） |
| v1.0 | 2026-06-10 | 首版：IF-IPR-01～04；上游 AS-IP-NUR-012；仅确认退药、无拒绝；单条发放明细；在院队列；Gap 001～003 |
| v1.3 | 2026-06-10 | **BR-IPR-007 扩口径**：退药与原发药单无读写关联；读侧仅待退行+原药嘱 |
| v1.2 | 2026-06-10 | **BR-IPR-007 修订**：确认退药不改原 COMPLETED 正向行；发药/退药通过对冲 REFUND 行统计 |
| v1.1 | 2026-06-10 | §3 Gap 001～003 **已关闭**（`MedicationDispenseQueryService` 住院退药读能力 + DM §6.1.8 待退拷贝批号/追溯） |
