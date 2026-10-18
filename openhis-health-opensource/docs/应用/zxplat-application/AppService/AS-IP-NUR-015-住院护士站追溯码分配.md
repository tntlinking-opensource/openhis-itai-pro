# 住院护士站 · 追溯码分配 · 应用服务设计（AS）

> **文档职责**：工作台右侧「追溯码分配」Tab 契约——在**汇总发药已核发**后，由病区护士将药师扫入汇总池的追溯码**归因到患者**，写入 **`MedicationDispense.trace_no`**；**不改发放状态、不扣库、不写追溯 OUT**。
>
> **页面边界**：**本 Tab**——在 **AS-IP-NUR-001** 点选患者 → 加载**同药同批汇总行** → **批量扫码** → 后台按**药品 + 批号**自动匹配 → **保存分配**。**患者上下文**在 **AS-IP-NUR-001**；**汇总领药**在 **AS-IP-NUR-010**；**汇总核发/药师扫池**在 **AS-IP-PHA-002**；**逐行住院发药**在 **AS-IP-PHA-001**（漏扫不进本 Tab）。
>
> **追溯解析**：批量扫码后的「码 → 药品 + 批号 + 汇总组」匹配能力由 **`PharmacyTraceCodeResolvesupport** 提供（与 **AS-OP-PHA-001** 追溯弹窗同族）；**本文默认该能力已在后端就绪**。
>
> **编码**：AS-IP-NUR-015 · **版本**：v1.2 · **日期**：2026-07-08

---

## 1. 场景概述

### 1.1 目标与边界

| 维度 | 说明 |
| --- | --- |
| **在内** | IF-IP-TRC-01 待分配患者队列（可选筛选/待办计数）；IF-IP-TRC-02 按患者加载**药+批汇总**待分配行；IF-IP-TRC-03 **批量解析追溯码**（只读匹配，同门诊「一起扫」）；IF-IP-TRC-04 **保存患者追溯码分配**（只写 `MedicationDispense.trace_no`） |
| **在外** | 左侧患者树/列表/详情（**AS-IP-NUR-001**）；汇总保存/发送（**AS-IP-NUR-010**）；药房汇总核发与 Supply 码池采集（**AS-IP-PHA-002**）；逐行住院发药追溯（**AS-IP-PHA-001**）；**`cus_trace_number` + `cus_trace_number_flow` OUT/IN**；库存扣减；医保 **3505** / 商品销售上传；追溯码国家库解析细则 |
| **涉及域** | **Encounter**；**MedicationDispense**；**MedicationDefinition**；**SupplyRequest**（type 27）；**SupplyDelivery**（码池只读） |
| **编排权威** | 以本文 **§2、§4** 为准；写侧**仅**更新发放行 `trace_no`，**禁止**变更 `status_enum`、**禁止**写 SupplyDelivery / TraceNumberRecord |
| **前置（摘要）** | **AS-IP-PHA-002** 已对相关 `summaryNo` **核发**（发放行 **COMPLETED**）；药师可选在核发时把码写入 **`SupplyDelivery.trace_no`**（汇总码池） |
| **后置（摘要）** | 患者维度发放行 `trace_no` 补齐；**不**触发二次出库登记；下游医保/监管上传**本期不接** |

### 1.2 角色与关键动作

| 角色 | 关键动作 |
| --- | --- |
| **病区护士** | 在 **AS-IP-NUR-001** 选患者（或从 IF-IP-TRC-01 待办队列进入）→ 查看**同药同批**汇总行及已分配码 → **批量扫码/粘贴** → 确认匹配结果 → **保存** |
| **系统** | 筛选汇总路径下 **COMPLETED 且 trace 空** 的发放行 → 按 **medicationId + lotNumber** 聚合展示 → 调用 **`PharmacyTraceCodeResolvesupport** 按药品+批号匹配 → 校验码 ∈ 对应 **SupplyDelivery 码池** → 展开写底层 **`MedicationDispense.trace_no`** |

### 1.3 术语与口径约定

| 术语 | 口径 |
| --- | --- |
| **汇总路径** | 发放行 **`summary_no` 非空** 且由 **AS-IP-NUR-010 → AS-IP-PHA-002** 核发为 **COMPLETED**；**无 `summary_no` 的逐行发药**不在本 Tab |
| **待分配** | `MedicationDispense.status_enum = COMPLETED` 且 **`trace_no` 为空**（或汇总组内仍有未写 trace 的底层行）；**须** `summary_no` 非空 |
| **汇总组（展示行）** | 同一患者、同一 **`summary_no`** 内，按 **`medication_id + lot_number`** 合并为一行；数量 = 组内底层发放行 `quantity` 按 **unit_code → 拆零** 累加；展示批号来自底层发放行（执行 FIFO 已写） |
| **码池** | 该 **`summary_no`** 下全部 **SupplyDelivery** 行 `trace_no` 并集（逗号拆分、去重）；护士**只能**使用池内码；**禁止**提交池外第 N 个码 |
| **批量扫码** | 一次提交多个追溯码（扫/粘贴）；**非**「一行一扫手工点选」 |
| **自动匹配** | **`PharmacyTraceCodeResolveSupport#resolveTraceCodes`**：输入码列表 + 当前患者待分配汇总组 → 输出每组命中码；匹配键 = **药品 + 批号**（与门诊追溯弹窗同族） |
| **共用码** | **允许**同一追溯码字符串出现在**多患者/多汇总组/多底层行**（非一码一患者）；落库时**多行重复同一 `trace_no` 字符串** |
| **拆零** | **不免扫**；汇总组发药量含拆零时仍须按 **BR-IP-TRC-008** 采集 |
| **码数上限** | 与开立/发药量相关：多盒可多码，不满一盒 **至少 1 码**；单汇总组提交码数 **≤ 该组发药数量**（拆零口径，见 **BR-IP-TRC-008**） |
| **药师未扫** | 汇总核发时药师**可不扫**码池；护士 Tab **仍可打开**；若护士提交码而对应 **summary 码池为空** → 拒绝（**EX-IP-TRC-204**） |
| **同患者多 summaryNo** | 各 **`summary_no` 独立码池**；IF-IP-TRC-02 按组展示 `summaryNo`；匹配与保存**不得跨 summary 混用码池** |
| **写库锚点** | **仅** `med_medication_dispense.trace_no`；**不写** `wkf_supply_delivery.trace_no`；**不写** OUT |

### 1.4 本期范围与非目标

| 类别 | 内容 |
| --- | --- |
| **本期** | IF-IP-TRC-01～04 |
| **非目标** | 逐行住院发药补扫（**AS-IP-PHA-001**）；修改发放状态/批号/数量；SupplyDelivery / TraceNumberRecord 写路径；医保 **3505**；追溯码国家库解析；打印机驱动；**强制**药师核发时必须扫满码池 |

### 1.5 领域清单（聚合 + 职责 + DM 路径）

| 聚合（域） | 在本场景中的职责（摘要） | DM 路径 |
| --- | --- | --- |
| **Encounter** | 患者/就诊可见性锚点 | `DM-ADM-002-Encounter-就诊.md` |
| **MedicationDispense** | 已核发发放行；**仅写 `trace_no`** | `DM-MED-002-MedicationDispense-药品发放.md` |
| **MedicationDefinition** | 药品展示、包装单位、码数上限换算 | `DM-MED-001-MedicationDefinition-药品定义.md` |
| **SupplyRequest** | type 27 汇总单；关联 `summary_no` | `DM-WKF-006-SupplyRequest-供应请求.md` |
| **SupplyDelivery** | **只读**汇总码池 `trace_no` | `DM-WKF-005-SupplyDelivery-供应发放.md` |

### 1.6 业务规则（BR-*）

| 规则 ID | 描述 | 绑定 IF |
| --- | --- | --- |
| **BR-IP-TRC-001** | 读/写 `encounterId` 必填；就诊须落在 **AS-IP-NUR-001** 当前用户可见住院就诊集合内 | 01～04 |
| **BR-IP-TRC-002** | **准入**：仅 **`summary_no` 非空** 且 **`status_enum = COMPLETED`** 的发放行进入本 Tab；**无 `summary_no` 的逐行发药**不得出现在 IF-IP-TRC-02 | 01、02 |
| **BR-IP-TRC-003** | **待分配**：底层发放行 `trace_no` 为空；已全部写 trace 的汇总组可展示为「已分配」只读 | 02 |
| **BR-IP-TRC-004** | **展示合并键**：同一患者 + 同一 `summary_no` 内 **`medication_id + lot_number`** 合并为一行；批号取底层发放行持久化值 | 02 |
| **BR-IP-TRC-005** | **码池范围**：保存/解析时，每个码须属于该汇总组所属 **`summary_no`** 下全部 **SupplyDelivery.trace_no** 并集；否则拒绝（**EX-IP-TRC-201**） | 03、04 |
| **BR-IP-TRC-006** | **共用码**：允许同一码字符串绑定多组/多行；**不**做「一码一患者」独占 | 03、04 |
| **BR-IP-TRC-007** | **IF-IP-TRC-03** 为**只读**匹配，**不落库** | 03 |
| **BR-IP-TRC-008** | **码数**：单汇总组提交独立码数 **≤ 该组发药数量**（拆零累加口径，与 **BR-IP-SUM-008** 同族）；多盒可多码；不满一盒至少 1 码；**拆零不免扫** | 03、04 |
| **BR-IP-TRC-009** | **IF-IP-TRC-04** **仅**更新 **`MedicationDispense.trace_no`**；**禁止**变更 `status_enum`、**禁止**写库存、**禁止**写 **`cus_trace_number` + `cus_trace_number_flow` OUT** | 04 |
| **BR-IP-TRC-010** | 展开写底层行：同汇总组内**全部**底层 `MedicationDispense` 行写入**相同** `trace_no` 字符串（逗号分隔多码）；共用码时多行重复同一字符串 | 04 |
| **BR-IP-TRC-011** | **同患者多 summaryNo**：码池、匹配、保存**按 summaryNo 隔离**；**禁止**用 A 单的码池给 B 单的组 | 02～04 |
| **BR-IP-TRC-012** | **覆盖保存**：本期允许对已非空 `trace_no` 的汇总组**再次保存**覆盖（产品未接医保前）；覆盖时仍须满足码池与码数规则 | 04 |
| **BR-IP-TRC-013** | 药师核发未写 Supply 码池时：IF-02 仍展示待分配行；IF-04 若用户提交非空码且池为空 → **EX-IP-TRC-204** | 02、04 |
| **BR-IP-TRC-014** | 追溯码格式：20 位数字串（与门诊 **`TRACE_CODE_LENGTH=20`** 同族）；非法格式 **EX-IP-TRC-202** | 03、04 |

---

## 2. 页面与接口总览（契约概览）

| IF 编号 | 用户动作 | 接口语义（业务） | 读/写 | 关键规则（BR） | 关键状态变化 | 涉及领域 | 查询服务 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| IF-IP-TRC-01 | Tab 进入 / 刷新待办 | 分页查询**有待追溯分配**的住院就诊（可选关键字） | 读 | BR-IP-TRC-001、002 | 无 | Encounter + MedDispense | **MedicationDispenseQueryService#pageEncountersPendingTraceAssignment** |
| IF-IP-TRC-02 | 点选患者 | 加载该患者**药+批汇总**待分配/已分配行（含 `summaryNo`、码池摘要） | 读 | BR-IP-TRC-001～004、011、013 | 无 | MedDispense + SupplyDelivery + MedDefinition | **MedicationDispenseQueryService#listPatientTraceAssignmentAggregateLines** + **SupplyDeliveryQueryService#selectByRequestIdList** |
| IF-IP-TRC-03 | 追溯弹窗：批量扫码 | **批量解析追溯码**（药品+批号→汇总组；只读） | 读 | BR-IP-TRC-005～008、014 | 无 | MedDispense + SupplyDelivery | **`PharmacyTraceCodeResolveSupport#resolveTraceCodes`** |
| IF-IP-TRC-04 | 确认保存 | **保存患者追溯码分配**（展开写底层 `trace_no`） | 写 | BR-IP-TRC-005～012 | **无状态变更**；仅 `trace_no` UPDATE | MedDispense | — |

---

## 3. 缺口跟踪（Gap）

### 3.1 缺口分类说明

| 分类 | 含义 | 本文处理 |
| --- | --- | --- |
| **领域层 Gap** | DM §6 / §6.2 尚不具备 AS 编排所需能力 | §3.2 |
| **应用层未实现** | AppService、Controller、DTO、`PharmacyTraceCodeResolvesupport 接线 | **不算 Gap**（SDD 驱动成码） |
| **追溯解析** | 与门诊同族的批量匹配 | **不算 Gap**（按产品决议**默认后端已具备**） |

### 3.2 领域层 Gap 清单

| Gap ID | 缺口类型 | 文件 + 章节 | 缺什么 / 不一致点 | 关闭条件 | 影响 IF | 优先级 | 状态 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| GAP-IP-TRC-001 | DM 能力 | `DM-MED-002` §6.1.16 | ~~缺 **`assignTraceNoToCompletedDispense`**~~ | `MedicationDispenseDomainService#assignTraceNoToCompletedDispense` + `AssignTraceNoToCompletedDispenseCommand` 已实现 | IF-IP-TRC-04 | P0 | **已关闭** |
| GAP-IP-TRC-002 | QueryService 能力 | `DM-MED-002` §6.2.19 | ~~缺 **`pageEncountersPendingTraceAssignment`**~~ | `EncountersPendingTraceAssignmentPageQuery` + `EncounterPendingTraceAssignmentQueryModel` 已实现 | IF-IP-TRC-01 | P1 | **已关闭** |
| GAP-IP-TRC-003 | QueryService 能力 | `DM-MED-002` §6.2.20 | ~~缺 **`listPatientTraceAssignmentAggregateLines`**~~ | `PatientTraceAssignmentAggregateLineQueryModel` 已实现 | IF-IP-TRC-02 | P1 | **已关闭** |

> **说明**：**SupplyDelivery** 码池读侧 **`SupplyDeliveryQueryService#selectByRequestIdList`**（**DM-WKF-005 §6.2.2**）**已存在**。**`PharmacyTraceCodeResolvesupport** 为应用/supcontracts 层能力，**不记入 §3.2**。

### 3.3 产品待定（非 Gap，评审跟踪）

| 编号 | 议题 | 当前 AS 默认 |
| --- | --- | --- |
| Q-TRC-001 | 单汇总组 **`maxCodes` 与包装规格**精确公式 | **BR-IP-TRC-008** 粗口径：码数 ≤ 拆零发药量；细粒度锚 **`package_unit_ratio`** 在 SDD/App 实现 |
| Q-TRC-002 | 批号匹配边界（空批号、多批混组） | 合并键 **`lot_number` 非空** 才成组；空批号组 **拒绝保存**（**EX-IP-TRC-203**） |
| Q-TRC-003 | 医保对接后是否禁止覆盖已上传 trace | 本期 **BR-IP-TRC-012** 允许覆盖 |

---

## 4. 接口编排说明书（业务步骤）

### IF-IP-TRC-01 待分配患者队列

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 用户进入追溯码 Tab 或点**刷新**；可选关键字 |
| **主路径步骤** | 解析管辖病区可见 encounter → 分页查询存在**待分配**汇总发放行的就诊（**BR-IP-TRC-002**）→ 返回就诊摘要 + **待分配组数** |
| **关键分支** | 可与 **AS-IP-NUR-001** 当前选中患者联动；无待办时空列表 |
| **失败分支** | 无权（**EX-IP-TRC-101**）；查询失败（**EX-IP-TRC-102**） |
| **查询侧说明** | **`MedicationDispenseQueryService#pageEncountersPendingTraceAssignment`** |

### IF-IP-TRC-02 患者药批汇总行

| 维度 | 说明 |
| --- | --- |
| **触发前提** | **AS-IP-NUR-001** 已选患者；或 IF-IP-TRC-01 点选一行 |
| **主路径步骤** | 校验可见性（**BR-IP-TRC-001**）→ 加载该就诊 **COMPLETED + summary_no 非空** 发放行（**BR-IP-TRC-002**）→ 按 **`summary_no + medication_id + lot_number`** 聚合（**BR-IP-TRC-004**）→ 装配药品展示 → 按 **`summary_no`** 加载 **SupplyRequest** 行 → **`selectByRequestIdList`** 读 **SupplyDelivery** 码池 → 返回 **`tracePoolPreview`**（摘要）与 **`tracePoolCodes`**（同 summary 完整列表，供前端自动预填）→ 标记每组 **pending / assigned**（**BR-IP-TRC-003**） |
| **关键分支** | 同患者多个 **`summaryNo`** 分组展示；已分配组回显 `traceNo` |
| **失败分支** | 未选患者（**EX-IP-TRC-101**）；无待分配数据（空列表，非错误） |
| **查询侧说明** | **`MedicationDispenseQueryService#listPatientTraceAssignmentAggregateLines`** + **SupplyDeliveryQueryService#selectByRequestIdList** |

### IF-IP-TRC-03 批量解析追溯码

| 维度 | 说明 |
| --- | --- |
| **触发前提** | IF-IP-TRC-02 已加载；用户在追溯弹窗**批量**提交码列表 |
| **主路径步骤** | 校验 encounter 与汇总组快照 → 规范化码（**BR-IP-TRC-014**）→ 加载各 **`summaryNo`** 码池（**BR-IP-TRC-005**）→ 调用 **`PharmacyTraceCodeResolveSupport#resolveTraceCodes`**（药品+批号→汇总组）→ 校验命中码 ∈ 对应码池 → 校验码数（**BR-IP-TRC-008**）→ 返回**匹配预览**（组 id、命中码列表）；**不写库**（**BR-IP-TRC-007**） |
| **关键分支** | 部分码无法匹配 → 返回失败明细（**EX-IP-TRC-205**）；池外码 → **EX-IP-TRC-201** |
| **失败分支** | 非法码格式（**EX-IP-TRC-202**）；跨 summary 混池（**EX-IP-TRC-206**） |
| **查询侧说明** | 只读；与 **AS-OP-PHA-001** 追溯弹窗 **`resolveTraceCodeMock`** 同形，对接 **`PharmacyTraceCodeResolvesupport** |

### IF-IP-TRC-04 保存患者追溯码分配

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 用户确认 IF-IP-TRC-03 预览或手工确认各组 `traceNo` |
| **主路径步骤** | ① 校验 encounter 可见（**BR-IP-TRC-001**）→ ② 强加载各组底层 **COMPLETED** 发放行（**BR-IP-TRC-002**）→ ③ 再次校验码池（**BR-IP-TRC-005**）与码数（**BR-IP-TRC-008**）→ ④ 码池为空且用户提交了码 → **EX-IP-TRC-204** → ⑤ 对每个汇总组，调用 **`MedicationDispenseDomainService#assignTraceNoToCompletedDispense`** 写入相同 `trace_no` 到组内**全部**底层行（**BR-IP-TRC-010**）→ ⑥ **同一事务**提交（**BR-IP-TRC-009**） |
| **关键分支** | **覆盖保存**（**BR-IP-TRC-012**）；**共用码**允许多行相同字符串（**BR-IP-TRC-006**） |
| **失败分支** | 状态非 COMPLETED（**EX-IP-TRC-207**）；批号空（**EX-IP-TRC-203**）；码池/码数（**EX-IP-TRC-201/208**） |
| **查询侧说明** | 写前 **mustGetMedicationDispense** / 批量 by ids |

---

## 5. 业务异常与用户提示

| 异常 ID | 场景 | 用户提示语义 | 可重试 |
| --- | --- | --- | --- |
| EX-IP-TRC-101 | 未选患者或无权 | 请先选择患者或无权访问 | 是 |
| EX-IP-TRC-102 | 列表查询失败 | 加载失败，请稍后重试 | 是 |
| EX-IP-TRC-201 | 码不在汇总码池 | 追溯码不在本汇总单药房码池内，请核对或联系药房 | 是 |
| EX-IP-TRC-202 | 码格式非法 | 追溯码须为 20 位有效编码 | 是 |
| EX-IP-TRC-203 | 批号缺失 | 该药品批号缺失，无法分配追溯码 | 否 |
| EX-IP-TRC-204 | 码池为空 | 药房未录入汇总追溯码，暂无法保存 | 否 |
| EX-IP-TRC-205 | 部分码未匹配 | 部分追溯码无法匹配到药品批号，请核对 | 是 |
| EX-IP-TRC-206 | 跨汇总单混用码 | 追溯码与汇总单不匹配 | 是 |
| EX-IP-TRC-207 | 发放行状态不允许 | 药品尚未核发完成，无法分配 | 刷新后重试 |
| EX-IP-TRC-208 | 码数超限 | 追溯码数量不能超过发药数量 | 是 |

---

## 6. 事件语义与消费方

| 事件（语义级） | 触发点 | 消费方 / 意图 | 幂等语义 |
| --- | --- | --- | --- |
| **（无新增领域事件）** | IF-IP-TRC-04 仅 UPDATE `trace_no` | — | — |

> **说明**：本场景** deliberately 不**发布 `MedicationDispensedEvent` 或追溯 OUT 事件；核发事实与 OUT 已在 **AS-IP-PHA-002** 完成。

---

## 7. 非功能性需求

| 维度 | 要求 |
| --- | --- |
| **一致性** | IF-IP-TRC-04 多组保存 **同一事务**；任一组失败整批回滚 |
| **幂等** | 相同组重复提交相同 `trace_no` 视为覆盖（**BR-IP-TRC-012**） |
| **性能** | 单患者汇总组建议 ≤100；单次批量解析码 ≤50 |
| **审计** | 记录操作人、`encounterId`、各 `summaryNo`、组级 `trace_no` 变更 |
| **安全** | 码池只读；**禁止**客户端绕过池校验 |

---

## 8. 测试要点（业务视角）

| 类别 | 要点 |
| --- | --- |
| **准入** | 无 `summary_no` 的 COMPLETED 行**不出**现在 IF-02；逐行发药漏扫**不进** Tab |
| **聚合** | 同药同批多底层行 → IF-02 **一行**；数量拆零累加正确 |
| **码池** | 仅 SupplyDelivery 池内码可保存；池外第 4 码 → **EX-IP-TRC-201** |
| **匹配** | 批量 3 码自动落到 2 个药批组；批号不符不匹配 |
| **共用码** | 两患者各保存同一码字符串 → 均成功 |
| **写路径** | 保存后 `status_enum` 仍为 **COMPLETED**；**无** OUT 记录新增 |
| **空池** | 药师未扫：IF-02 可见待分配；IF-04 提交码 → **EX-IP-TRC-204** |
| **覆盖** | 已分配组再次保存新码 → 底层行 trace 更新 |
| **多 summary** | 同患者两 summaryNo → 码池隔离 |

---

## 9. 关联文档与变更历史

### 9.1 关联文档

| 文档 | 关系 |
| --- | --- |
| `AS-IP-NUR-001-住院护士站患者管理.md` | 上游：患者/就诊上下文 |
| `AS-IP-NUR-010-住院护士站汇总领药.md` | 上游：汇总路径 |
| `AS-IP-PHA-002-住院汇总发药.md` | 上游：核发 + Supply 码池 |
| `AS-OP-PHA-001-门诊发药.md` | 同族：批量追溯解析交互 |
| `DM-MED-002-MedicationDispense-药品发放.md` | 发放行 `trace_no` |
| `DM-WKF-005-SupplyDelivery-供应发放.md` | 码池只读 |

### 9.2 变更历史

| 版本 | 日期 | 摘要 |
| --- | --- | --- |
| v1.2 | 2026-07-08 | **IF-IP-TRC-02**：组 DTO 增补 **`tracePoolCodes`**（汇总码池完整列表） |
| v1.1 | 2026-07-04 | 关闭 **GAP-IP-TRC-001～003**：`DM-MED-002` §6.1.16 / §6.2.19～20 领域能力已成码 |
| v1.0 | 2026-07-04 | 首版：汇总核发后护士追溯分配；IF-IP-TRC-01～04；默认 **`PharmacyTraceCodeResolvesupport** 已就绪；写路径仅 **`MedicationDispense.trace_no`** |
