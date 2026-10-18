# 住院护士站 · 取消执行 · 应用服务设计（AS）

> **文档职责**：工作台「医嘱执行」Tab 内 **已执行** 槽位的批量**取消执行**契约——对已 **COMPLETED** 计划点做 **CANCEL 对冲**，并按执行前态回退发药/账单/汇总/绑定项；**不执行**编排仍归 **AS-IP-NUR-004 IF-IP-EXE-04**。
>
> **页面边界**：与 **AS-IP-NUR-004** 共用 `order-execute` 页——**已执行 Tab** 勾选槽位 → **取消执行**；列表与槽位装载仍走 **IF-IP-EXE-01**。**患者上下文**在 **AS-IP-NUR-001**；正向执行在 **AS-IP-NUR-004**；护士主动汇总与**取消执行退药只读查看**在 **AS-IP-NUR-010**（IF-IP-SUM-10）；汇总核发在 **AS-IP-PHA-002**；药房确认退药在 **AS-IP-PHA-003**。
>
> **编码**：AS-IP-NUR-012 · **版本**：v1.15 · **日期**：2026-10-16
>
> **医技协同**：对执行科室为 **TECH** 的住院诊疗，取消执行**不再**做双侧同态硬拦（护士/医技互不卡；历史 **BR-IP-CXL-017** 废止）。CANCEL 对冲**继承**被对冲 COMPLETED 的 `org_id`（同侧）。出院医技门禁见 **BR-IP-ATD-011⑤**（医技执行净态，**非**费用确费戳）。

---

## 1. 场景概述

### 1.1 目标与边界

| 维度 | 说明 |
| --- | --- |
| **在内** | IF-IP-CXL-01 批量**取消执行**（Procedure 对冲 + 药/诊/绑定分轨回退 + 汇总未发自动撤单重建 + 已发退药单）；术语与 **不执行** 对照（引用 **IF-IP-EXE-04**） |
| **在外** | 执行列表查询（**AS-IP-NUR-004 IF-IP-EXE-01**）；正向执行（**IF-IP-EXE-02**）；不执行写路径（**IF-IP-EXE-04**）；护士主动汇总/撤销（**AS-IP-NUR-010**）；药房核发/驳回（**AS-IP-PHA-002**）；住院结算/退费（**AS-IP-FIN-003** 等）；停嘱；修改计划执行时间 |
| **涉及域** | **Procedure**；**MedicationRequest / ServiceRequest**；**MedicationDispense**；**ChargeItem**；**DeviceRequest / DeviceDispense**；**SupplyRequest**；**InventoryItem**；**Encounter** |
| **编排权威** | 以本文 **§2、§4** 为准；写侧对齐住院护士 `adviceCancel` 业务语义，绑定耗材改为 **`based_on` 反查**（非 procedureId） |

### 1.2 角色与关键动作

| 角色 | 关键动作 |
| --- | --- |
| **病区护士** | 在 **AS-IP-NUR-001** 勾选患者 → 医嘱执行 Tab 切 **已执行** → 勾选计划点 → **取消执行**（填写取消原因） |
| **系统** | 校验净剩 COMPLETED 与费用/发放 Gate → 追加 CANCEL 行 → 按发放态回退主医嘱联动 → **强制**回退绑定项 → 汇总未发则同事务撤 Supply 并过滤重建 → 已发则生成待退药单 |

### 1.3 术语与口径约定

| 术语 | 口径 |
| --- | --- |
| **净剩可取消** | 状态 **COMPLETED** 且 id **不在** 任一 **CANCEL.refund_id** 集合中；与 **AS-IP-NUR-004 BR-IP-EXE-008**、门诊 **BR-NUR-005** 同构 |
| **CANCEL 对冲** | 新建 Procedure：**create → start → cancel**；`refund_id` **必填** = 被对冲 COMPLETED 行 id；`occurrence_time` 继承原行；`recorded_time` = 操作时刻 |
| **已发路径** | 生成 **PENDING_REFUND** 退药单；`procedure_id` 挂接**新 CANCEL 行**（非原 COMPLETED 行）；`med_req_id` 沿用原发放药嘱；不走汇总重建 |
| **不执行 vs 取消执行** | **不执行**（**IF-IP-EXE-04**）：pending 槽位 → Procedure **STOPPED**；**不动** Dispense/Charge/Request。**取消执行**（本文）：executed 槽位 → CANCEL 对冲 + 联动回退 |
| **长临分轨（发药）** | **长临统一**未发：`removeUndispensedDispense`（软删执行新建发放行）；已发走退药 |
| **长临分轨（账单）** | **长临统一**：执行族（`order-execute` / `anesthesia-execute`）+ `PENDING_SETTLEMENT` 行按 procedureId **逻辑删除**（`voidOrderExecuteChargeItemsByProcedureIds`） |
| **汇总未发自动重建** | 发放行 **SUMMARIZED** 时：**不**要求护士去汇总 Tab 手工撤销；同事务按 Supply 态自动撤整单 → **剔除行按 BR-IP-CXL-007 删/回退** → 剩余非空则按 **AS-IP-NUR-010** 合并规则重建 **PENDING_APPROVAL** 汇总单；**重建定价**须 **PENDING_SETTLEMENT** 且按 **`dispenseId`** 锚定（同 IF-IP-SUM-04），**禁止**误用 `serviceId` / 门诊发药定价口径 |
| **Supply 态分支** | **PENDING_APPROVAL**：`cancelMedicationDispenseSummary` + `removeByBusNo`；**APPROVAL**：`rejectRequest` + `cancelMedicationDispenseSummary` + **`removeByBusNo`（删驳回残单，避免取消药仍留在旧单）**；**AGREE** / 发放 **COMPLETED**：禁止汇总重建，走退药 |
| **绑定项定位** | 执行自动带通过主医嘱 **`requestTable + requestId`** 作 **`based_on_*` 反查**；来源 **`nurse-pricing`（现行）** 与历史 **`order-execute`**；**排除**皮试 companion（`doctor-prescription`）；再按**本槽关联**收窄——诊疗：绑定 Procedure.`occurrence_time` 与主槽 planTime **秒级对齐**（与执行列表槽位键同口径；Charge.`occurrence_time` 回落）；耗材：Charge.`procedure_id`=主 Procedure，或账单 occurrence 与主槽秒级对齐（防误冲手动补费/其它槽，避免纳秒差漏冲） |
| **绑定项回退** | **nurse-pricing**：诊疗 → Procedure CANCEL + **`offsetNursePricing*`**（Request **不**软删）；耗材 → **`revertInpatientPricingDeviceDispenses`** + **`offsetNursePricing*`**（DeviceRequest **不**软删）。**历史 order-execute**：诊疗逻辑删 SR + Procedure CANCEL + void Charge；耗材仅回退发放（Charge 随主 Procedure void） |
| **结算门禁** | 主医嘱或绑定项任一关联 Charge 为 **SETTLED / REFUNDING / REFUNDED** → **拒绝**取消执行 |

### 1.4 本期范围与非目标

| 类别 | 内容 |
| --- | --- |
| **本期** | IF-IP-CXL-01 |
| **非目标** | 独立「可取消列表」读接口（**已执行 Tab** 槽位由 **IF-IP-EXE-01** 提供 `procedureId`）；不执行写路径（见 **IF-IP-EXE-04**）；已结算费用退费编排；修改 occurrence_time；取消 **STOPPED**（不执行）记录 |

### 1.5 领域清单（聚合 + 职责 + DM 路径）

| 聚合（域） | 职责摘要 | DM 路径 |
| --- | --- | --- |
| **Procedure** | CANCEL 对冲；绑定诊疗派生 Procedure | `DM-CLN-004` |
| **MedicationRequest / ServiceRequest** | 主医嘱锚点；绑定诊疗派生 SR | `DM-MED-003` / `DM-WKF-004` |
| **MedicationDispense** | 未发删除/回退；已汇总撤单；已发待退 | `DM-MED-002` |
| **ChargeItem** | 长期逻辑删；临时回 PLANNED；结算 Gate | `DM-ADM-011` |
| **DeviceRequest / DeviceDispense** | 绑定耗材 based_on 反查与回退 | `DM-WKF-003` / `DM-WKF-007` |
| **SupplyRequest** | 汇总单撤/驳；重建写 PENDING_APPROVAL | `DM-WKF-006` |
| **InventoryItem** | 绑定耗材已发回补 | 库存 DM |
| **Encounter** | 可见性校验 | `DM-ADM-002` |

### 1.6 业务规则（BR-*）

| 规则 ID | 描述 | 绑定 IF |
| --- | --- | --- |
| **BR-IP-CXL-001** | 入参可见性：同 **BR-IP-AUD-001** / **BR-IP-EXE-001** | 01 |
| **BR-IP-CXL-002** | 入参须含至少一个 **procedureId**（净剩 COMPLETED）；同批 **禁止重复** procedureId；须属当前批量就诊集合 | 01 |
| **BR-IP-CXL-003** | Gate：每条 procedureId 记录存在、状态 **COMPLETED**、来源为 **MED/SERVICE_REQUEST**、**未被 CANCEL 对冲**、就诊一致 | 01 |
| **BR-IP-CXL-004** | **取消原因** `statusReasonText` **必填** | 01 |
| **BR-IP-CXL-005** | **费用 Gate**：按 procedureId 及绑定项 Charge，任一 **SETTLED / REFUNDING / REFUNDED** → **整批拒绝**；**但**已被 **BR-REF-020** / **BR-IP-RVS-011** 再生链替代的祖先 `SETTLED`（含多轮 `refund_id` 链）**不参与** Gate | 01 |
| **BR-IP-CXL-006** | 主路径顺序：① **绑定项回退**（based_on 反查）→ ② 主医嘱 **CANCEL** → ③ 主医嘱 Dispense/Charge 分轨 | 01 |
| **BR-IP-CXL-007** | **长临**药嘱未发：**统一** `removeUndispensedDispense`（软删执行新建 **MedicationDispense**） | 01 |
| **BR-IP-CXL-008** | **长临**执行新建 Charge（执行族 `order-execute` / `anesthesia-execute` + `PENDING_SETTLEMENT`）：**统一**按 procedureId **逻辑删除**（`GenerateSource#isOrderExecuteFamilyCode`） | 01 |
| **BR-IP-CXL-009** | **诊疗**无 Dispense：仅 Charge 按 **BR-IP-CXL-008** 回退 | 01 |
| **BR-IP-CXL-010** | 发放 **COMPLETED**：生成 **PENDING_REFUND** 退药单（`procedureId` 指向新 CANCEL 行）；**禁止**汇总重建 | 01 |
| **BR-IP-CXL-011** | 发放 **SUMMARIZED**：按 **§1.3 Supply 态分支** 同事务撤整单（**APPROVAL** 须 **reject + removeByBusNo**，禁止留下含取消行的驳回残单）；从该 `summaryNo` 全量发放行中 **剔除** 本次取消 id；**剔除行撤汇总后按 BR-IP-CXL-007 删/回退**；剩余非空则按 **BR-IP-SUM-005～008** 重建汇总（新 `summaryNo`；定价同 **IF-IP-SUM-04**） | 01 |
| **BR-IP-CXL-012** | 汇总重建 **并发**：事务内预读 Supply **status_enum**；写前校验未变；若已 **AGREE** 或发放已 **COMPLETED** → 拒绝（走 **BR-IP-CXL-010**）；若并发被改态 → **整批回滚**，提示刷新 | 01 |
| **BR-IP-CXL-013** | **绑定诊疗**：按槽关联（Procedure/`Charge.occurrence` 与主槽 **秒级**对齐）后——现行 **`nurse-pricing`** → Procedure CANCEL + **冲销留痕**（Request 不软删，对齐 **IF-IP-PRC-06**）；历史 **`order-execute`** → 逻辑删 SR + Procedure CANCEL + void Charge；排除皮试等非执行自动带 | 01 |
| **BR-IP-CXL-014** | **绑定耗材**：按槽（Charge.`procedure_id` 或账单 occurrence 秒级对齐）关联后——现行 **`nurse-pricing`** → 划价两轨 Dispense 回退 + **冲销留痕**；历史 **`order-execute`** → 仅 Dispense 回退；**DeviceRequest 不变** | 01 |
| **BR-IP-CXL-015** | 写操作批量：**同一事务**；任一步失败 **整批回滚**（对齐 **BR-IP-EXE-022**）；建议 ≤50 条 | 01 |
| **BR-IP-CXL-016** | **出院结算轨就诊 Gate**：就诊 `status_enum ∈ {PENDING, COMPLETED, DISCHARGED}` 时**禁止** IF-IP-CXL-01；**仅** `IN_PROGRESS`/`ON_HOLD` 允许；委托 `InpatientPendingSettlementClinicalGateSupport`（**BR-IP-FLW-008**） | 01 |
| **BR-IP-CXL-017** | **（废止）** 原「医技双侧同态」硬拦已取消；调用点保留空操作。出院见 **BR-IP-ATD-011⑤**（医技执行净态） | — |
| **BR-IP-NUR-012-TCM-001** | 取消执行写：勾选中药方槽位（`tcm_flag=true`）→ 按 `prescription_no` **扩全方**各味药行同 `occurrence` 的净剩 **COMPLETED** `procedureId`；Gate ≤50 按**扩行前**勾选计 | 01 |

---

## 2. 页面与接口总览（契约概览）

| IF 编号 | 用户动作 | 接口语义 | 读/写 | 关键规则 | 状态变化 | 涉及领域 | 查询服务 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| IF-IP-CXL-01 | 已执行 Tab：勾选槽位 → 取消执行 | 批量**取消执行**（含绑定项与汇总重建；中药整方扩行；**无** TECH 同态硬拦） | 写 | BR-IP-CXL-001～016；**BR-IP-NUR-012-TCM-001** | CANCEL 对冲；Dispense/Charge/Supply 分轨回退；可选重建汇总 | Procedure + Med/Service + Dispense + Charge + Device + Supply + Inventory | — |

> **读侧**：可取消槽位与 `procedureId` 由 **AS-IP-NUR-004 IF-IP-EXE-01** **已执行 Tab**（`executedSlots`）提供，**不另开读 IF**。

---

## 3. 缺口跟踪（Gap）

> **架构决议（2026-06-10）**：取消执行独立 **AS-IP-NUR-012**（**011** 已用于皮试）；汇总未发 **自动撤单 + 过滤重建**；**APPROVAL** 允许自动 `rejectRequest`；绑定项 **based_on 反查**；派生 SR **逻辑删除**；**SETTLED** 禁止取消。

| Gap ID | 缺口类型 | 文件 + 章节 | 缺什么 / 不一致点 | 关闭条件 | 影响 IF | 优先级 | 状态 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| GAP-IP-CXL-001 | DM 能力 | `DM-ADM-011` §6.1.5a + `ChargeItemDomainService` | 缺长期执行账单回退：按 `procedureId` **逻辑删除** `PENDING_SETTLEMENT` 且 `generate_source=ORDER_EXECUTE` 行（现有 `cancelExecutionRollbackChargeItem` 仅 → **DRAFT**） | DM 增补写命令 + 领域实现 + `ChargeItemExecutionCancelledEvent` | IF-IP-CXL-01 | P0 | **已关闭** |
| GAP-IP-CXL-002 | DM 能力 | `DM-ADM-011` §6.1.4l + `ChargeItemDomainService` | 缺临时执行账单逆激活：`PENDING_SETTLEMENT` → **PLANNED** 并清 **procedureId**（`activateChargeByExe` 逆操作） | DM 增补写命令 + 领域实现 | IF-IP-CXL-01 | P0 | **已关闭** |
| GAP-IP-CXL-003 | QueryService 能力 | `DM-ADM-011` §6.2.23 + `ChargeItemQueryService` | 缺按 **procedureId 集合**批量查 ChargeItem（取消 Gate 与回退定位） | DM §6.2 方法 + 实现 | IF-IP-CXL-01 | P0 | **已关闭** |
| GAP-IP-CXL-004 | QueryService 能力 | `DM-MED-002` §6.2.14 + `MedicationDispenseQueryService` | 缺按 **procedureId 集合**查药品发放行 | DM §6.2 方法 + Repository/实现 | IF-IP-CXL-01 | P0 | **已关闭** |
| GAP-IP-CXL-005 | QueryService 能力 | `DM-WKF-004` §6.2.23 + `ServiceRequestQueryService` | 缺按 **`based_on_table + based_on_id`** 列表查执行派生 ServiceRequest（现仅有 count） | DM §6.2 方法 + 实现 | IF-IP-CXL-01 | P0 | **已关闭** |
| GAP-IP-CXL-006 | QueryService 能力 | `DM-WKF-003` §6.2.10 + `DeviceRequestQueryService` | 缺按 **`based_on_table + based_on_id`** 列表查绑定耗材 DeviceRequest | DM §6.2 方法 + 实现 | IF-IP-CXL-01 | P0 | **已关闭** |
| GAP-IP-CXL-007 | DM 能力 | `DM-WKF-004` §6.1.18 + `ServiceRequestDomainService` | 缺执行派生 ServiceRequest **逻辑删除**写命令（`createExecutedServiceRequest` 对称） | DM 增补 + 领域实现 | IF-IP-CXL-01 | P0 | **已关闭** |

> **说明**：Procedure CANCEL、`removeUndispensedDispense`、`rollbackUndispensedDispense`、`createPendingRefundDispense`（**§6.1.8 已支持 `procedureId` 挂接 CANCEL 行**）、`cancelMedicationDispenseSummary`、`summarizeMedicationDispenses`、`removeByBusNo`、`rejectRequest`、DeviceDispense revert/回补等 **已存在**，**不入 Gap**；汇总过滤重建为 **应用层编排**（复用 **AS-IP-NUR-010** 合并规则）。

---

## 4. 接口编排说明书（业务步骤）

### IF-IP-CXL-01 批量取消执行

| 维度 | 说明 |
| --- | --- |
| **触发前提** | **已执行 Tab**；至少勾选一条带 **procedureId** 的槽位；取消原因已填；护士身份有效 |
| **主路径步骤** | 校验 **BR-IP-CXL-001～005**（可见性、净剩 COMPLETED、原因、**未结算**）→ **TECH 同态已废**（`assertNurseCancelHomology` 空操作）→ **中药整方扩行**（**BR-IP-NUR-012-TCM-001**）→ 预加载各 procedureId 的发放行与 Charge → 按主医嘱 **requestTable + requestId** **based_on 反查**绑定诊疗/耗材 → **先回退绑定项**（**BR-IP-CXL-013、014**）→ 对每个 procedureId：**CANCEL 对冲**（**BR-IP-CXL-003、004**）→ 主医嘱 **Dispense 分轨**（**BR-IP-CXL-007、010**）→ **Charge 分轨**（**BR-IP-CXL-008、009**）→ 若涉及 **SUMMARIZED**：按 Supply 态 **自动撤单** → **剔除行删/回退** → 剩余重建（**BR-IP-CXL-011**）→ 同事务提交（**BR-IP-CXL-015**） |
| **关键分支** | 仅药嘱/仅诊疗/含绑定项可单独成批；**PREPARATION** 直接删/回退；**SUMMARIZED** 整单撤后**剔除行须 BR-IP-CXL-007**再过滤重建；**COMPLETED** 仅退药单；绑定耗材无 procedure，**必须** based_on 路径；TECH / 非 TECH 均不再同态硬拦 |
| **失败分支** | EX-IP-CXL-201（未选/重复 procedureId）；EX-IP-CXL-202（不可取消/已对冲）；EX-IP-CXL-203（**已结算**）；EX-IP-CXL-204（汇总态并发冲突/已核发）；EX-IP-CXL-205（域写失败，整批回滚）；**EX-IP-CXL-207 预留不抛**（同态废止） |
| **查询侧说明** | 成功后 **IF-IP-EXE-01** 刷新：槽位 **executed → cancelled**；待发/汇总/退药队列按各领域事件更新 |

---

## 5. 业务异常与用户提示

| 异常 ID | 场景 | 用户提示 | 可重试 |
| --- | --- | --- | --- |
| EX-IP-CXL-201 | 未选可取消项或同批重复 procedureId | 请选择已执行且未取消的计划点 | 是 |
| EX-IP-CXL-202 | 执行记录不存在、非 COMPLETED、非本就诊或已被对冲 | 该计划点不可取消，请刷新后重试 | 是 |
| EX-IP-CXL-203 | 关联费用已结算或退费中 | 费用已结算，请先办理退费后再取消执行 | 否 |
| EX-IP-CXL-204 | 汇总单已被药房核发或并发改态 | 汇总单状态已变更，请刷新后重试 | 是 |
| EX-IP-CXL-205 | 域写失败 | 取消执行失败，请稍后重试 | 是 |
| EX-IP-CXL-206 | 就诊已待结算/已结算/已离院 | 患者已办理出院或已结算，不可变更医嘱及费用 | 否 |
| EX-IP-CXL-207 | **预留不抛**：历史 TECH 同态码（软落地后不再使用） | — | — |

---

## 6. 事件语义与消费方

| 事件 | 来源 | 影响 |
| --- | --- | --- |
| `ProcedureRecordCancelledEvent` | IF-IP-CXL-01 | executed→cancelled；Procedure 查询 |
| `ChargeItemExecutionCancelledEvent` | 账单回退/逻辑删除 | 住院待结算占用减少；费用查询 |
| `MedicationDispenseReserveReleasedEvent` / 删除事件 | 未发删/回退 | 待发预占；**IF-IP-SUM-01** 待汇总池 |
| `MedicationDispenseSummaryCancelledEvent` | 汇总撤单 | 汇总 Tab；待发明细 |
| `MedicationDispenseSummarizedEvent` | 过滤后重建 | 新 summaryNo 绑定 |
| `MedicationDispensePendingRefundCreatedEvent` | 已发退药 | **AS-IP-PHA-003** 退药队列 |
| DeviceDispense / InventoryItem 回补事件 | 绑定耗材已发回退 | 库存查询 |

---

## 7. 非功能性需求

| 维度 | 要求 |
| --- | --- |
| **性能** | 批量 procedureId IN；based_on 反查批量；同一 summaryNo 撤重建一次事务完成 |
| **一致性** | CANCEL 对冲与 **AS-OP-NUR-001 BR-NUR-005** 一致；绑定项与主医嘱**同事务** |
| **并发** | 汇总路径 **BR-IP-CXL-012**：Supply 态乐观校验；冲突整批回滚 |
| **事务** | 单批 ≤50 procedureId；失败整批回滚 |

---

## 8. 测试要点（业务视角）

| 类别 | 要点 |
| --- | --- |
| **Procedure** | CANCEL 行 refund_id 指向原 COMPLETED；executed 槽位净次数减 1 |
| **长期药·未发** | Dispense 软删；Charge 逻辑删 |
| **临时药·未发** | **同长期**（Dispense 软删；Charge 逻辑删） |
| **汇总·待发送** | 自动 removeByBusNo + 过滤重建；取消行不再出现在新单 |
| **汇总·已发送** | 自动 reject + removeByBusNo + 重建；驳回残单不留取消行；并发核发时拒绝 |
| **已发药** | PENDING_REFUND；`procedure_id` = 新 CANCEL 行；不删原 COMPLETED 发放事实 |
| **诊疗** | 仅 Charge 回退 |
| **绑定诊疗** | based_on 反查；派生 SR 逻辑删 |
| **绑定耗材** | based_on 反查；已发回补库存；DeviceRequest 不变 |
| **结算 Gate** | SETTLED 整批拒绝 |
| **TECH 同态** | **已废止**；取消不互卡。出院见 **BR-IP-ATD-011⑤**（医技执行净态） |
| **中药方整方取消** | 勾选代表行 → 扩全方同 occurrence 各味药 procedureId；代煎费随代表行 Charge 一并逻辑删除 |
| **不执行** | STOPPED 槽位不可走本 IF（仍走 IF-IP-EXE-04） |

---

## 9. 关联文档与变更历史

| 类型 | 文档 |
| --- | --- |
| 关联 AS | `AS-IP-NUR-001`；`AS-IP-NUR-004`（IF-IP-EXE-01/04）；`AS-IP-NUR-010`（汇总合并规则）；`AS-IP-PHA-002`；`AS-OP-NUR-001`（BR-NUR-005）；`AS-MT-EXE-001`（医技取消，同态已废）；`AS-IP-NUR-002`（出院 **BR-IP-ATD-011⑤**）；协同 DD |
| **SDD** | `AS-IP-NUR-012-住院护士站取消执行-SDD.md` |
| **module-support（读侧）** | `application.inpatient.nursestation.{feature}.support` |
| **App（写编排）** | `application.shared.nursestation-device-dispense` · `DeviceDispenseNurseWorkflowsupport（耗材回退，与执行/划价共用） |
| DM | `DM-CLN-004`、`DM-MED-002`、`DM-MED-003`、`DM-WKF-003`、`DM-WKF-004`、`DM-WKF-006`、`DM-WKF-007`、`DM-ADM-011` |

| 版本 | 日期 | 变更摘要 |
| --- | --- | --- |
| v1.15 | 2026-10-16 | **代码先行回写**：**BR-IP-CXL-008** 覆盖执行族（含 `anesthesia-execute`）；修复麻醉申请取消执行后费用残留；对齐 **DM-ADM-011 v1.6.27** |
| v1.14 | 2026-10-12 | **代码先行回写**：**BR-IP-CXL-013/014** 本槽关联改为 **秒级** occurrence 对齐（+ Charge.occurrence 回落），避免纳秒差导致取消执行漏冲绑定诊疗/耗材；对齐 `NurseStationOrderCancelExecuteWriteAssembler` |
| v1.13 | 2026-10-11 | **代码先行回写**：**BR-IP-CXL-013/014** 现行绑定项来源扩为 **`nurse-pricing`**（冲销留痕，Request 不软删）+ 历史 **`order-execute`**；按槽关联防误冲手动补费；对齐 **AS-IP-NUR-004 v2.64** |
| v1.12 | 2026-10-09 | **BR-IP-CXL-013**：绑定诊疗 based_on 反查仅 `order-execute`；排除皮试检查等开嘱联动 companion，避免取消药嘱执行误删非执行派生 SR |
| v1.11 | 2026-09-29 | CANCEL 对冲继承 COMPLETED.`org_id`（同侧）；头版对齐协同 DD / **BR-IP-ATD-011⑤** |
| v1.10 | 2026-09-21 | **BR-IP-CXL-017 废止**：取消不再同态硬拦（协同 DD 软落地） |
| v1.9 | 2026-09-19 | **BR-IP-CXL-017 / EX-IP-CXL-207**：TECH 诊疗取消须医技侧同态（两边都取消；停嘱除外）；对齐协同 DD / **AS-MT-EXE-001** |
| v1.8 | 2026-08-04 | **BR-IP-CXL-011** 修：汇总过滤重建定价对齐 IF-IP-SUM-04（`PENDING_SETTLEMENT`+`dispenseId`）；**APPROVAL** 自动驳回后 **removeByBusNo** 清残单，避免取消药仍留在汇总单 |
| v1.7 | 2026-07-11 | **BR-IP-CXL-016**：就诊 `PENDING/COMPLETED/DISCHARGED` 禁止 IF-IP-CXL-01；**EX-IP-CXL-206**；对齐 **BR-IP-FLW-008** |
| v1.6 | 2026-06-20 | **support 落点对齐**：读侧 → `module.querysupport.nursestation`；耗材回退编排 **`module.orchestration.nursestation`**；详见 **AS-SDD** |（已迁至 application.inpatient.nursestation.{feature}.support）（已迁至 application.shared.nursestation-device-dispense）（已迁至 application.shared / feature support（见 DISSOLUTION.md））
| v1.5 | 2026-06-20 | 对齐 **AS-IP-DOR-006** 下游：取消执行写路径 **中药整方扩行**（**BR-IP-NUR-012-TCM-001**）；Gate ≤50 仍按扩行前勾选计 |
| v1.4 | 2026-06-17 | **架构决议**：取消执行长临**同构**——未发统一 `removeUndispensedDispense`；Charge 统一 `voidOrderExecuteChargeItemsByProcedureIds`；对齐执行 **AS-IP-NUR-004 v2.25** / 开立 **AS-IP-DOR-003 v1.14**；**AS-SDD v1.2** |
| v1.3 | 2026-06-10 | **BR-IP-CXL-010** 口径：`createPendingRefundDispense` 挂 **`procedureId`=新 CANCEL 行**（**DM-MED-002 §6.1.8 v1.3.5**）；§1.3/§8 已发退药说明 |
| v1.1 | 2026-06-10 | §3 Gap 001～007 **已关闭**（领域层写/读能力落地，DM 回更） |
| v1.2 | 2026-06-10 | 关联 **AS-IP-NUR-012-SDD** v1.0（IF-IP-CXL-01 应用层目标态契约） |
| v1.3 | 2026-06-10 | **BR-IP-CXL-010** 口径：`createPendingRefundDispense` 挂 **`procedureId`=新 CANCEL 行**（**DM-MED-002 §6.1.8 v1.3.5**）；§1.3/§8 已发退药说明 |
