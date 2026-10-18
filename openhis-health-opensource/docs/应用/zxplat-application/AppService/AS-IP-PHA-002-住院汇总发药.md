# 住院汇总发药 · 应用服务设计（AS）

> **文档职责**：说明「住院汇总发药」药房工作站的**接口契约（IF）**、业务语义、涉及聚合与 QueryService、规则（BR）、编排意图；作为 **AS-IP-NUR-010** 下游核发契约，供详细设计与实现对齐。
>
> **本文必须写**：IF 编号；用户动作与读写属性；关键校验与状态变化；Gap；异常口径；事件语义；非功能约束；业务向测试要点。
>
> **本文禁止写**：具体类名/应用方法名；Request/Response 字段明细；包路径；Mapper；伪代码；写库顺序；QueryService 字段组装规则。
>
> **页面边界**：住院药房人员在**汇总发药** Tab 查询护士站已**发送**的 type **27** 汇总单 → 查看合并明细与底层发放行（含批号）→ 指定**领药人**、（可选）采集追溯码、选择**调配药师** → **核发**；或对待发单 **驳回**；支持打印。
>
> **覆盖声明**：契约以 **§2 IF-IPS-01～06** 为准；处理 **MedicationDispense** 汇总批次（`SUMMARIZED`）与 **SupplyRequest** type **27** 审批链；**不包含**逐行住院发药（**AS-IP-PHA-001**）、护士站汇总保存/发送（**AS-IP-NUR-010**）、退药、医保上传、ChargeItem 写操作。库存扣减**仅**按 **MedicationDispense** 发放行（执行时 FIFO 已写 lot/location），**禁止** SupplyDelivery COMPLETED 触发 `allocateAndDecrease`。
>
> **编码**：AS-IP-PHA-002 · **版本**：v1.10 · **日期**：2026-10-16

---

## 1. 场景概述

### 1.1 目标与边界

| 维度 | 说明 |
| --- | --- |
| **在内** | IF-IPS-01 汇总单头分页（仅 **`summaryDispenseCompletedOnly`** + 发放表门控；待发固定 Supply **APPROVAL**）；IF-IPS-02 汇总单详情（合并 Supply 行 + 底层 **MedicationDispense** 发放行含批号/数量，供核对扣库；**已发药只读**）；IF-IPS-03 **核发**（批量 `summaryNo`：库存校验与扣减 → 发放行 **COMPLETED** → **创建并完成** SupplyDelivery（写 **receiverId**）→ Supply **AGREE**）；IF-IPS-04 **驳回**（`rejectRequest` + 释放发放行）；IF-IPS-05/06 打印（读侧/客户端） |
| **在外** | 护士站汇总保存/发送（**AS-IP-NUR-010**）；逐行住院发药（**AS-IP-PHA-001**）；汇总单撤回（`withdrawApproval`，本期无 UI）；取消汇总 UI；住院退药；医保进销存/商品销售上传；ChargeItem 任何写操作；用户改批号/改数量/改库房 |
| **跨域** | **SupplyRequest**（type 27）；**SupplyDelivery**；**MedicationDispense**；**InventoryItem**；**TraceNoManage**；**MedicationDefinition** |
| **编排权威** | 以本文 **§2、§4** 为准；**无** `PREPARATION→IN_PROGRESS` 保存配药步骤；追溯码/调配药师规则与 **AS-IP-PHA-001** 同族，在 **IF-IPS-03 核发**一并校验 |
| **前置（摘要）** | **AS-IP-NUR-010** 已保存（发放行 **SUMMARIZED** + Supply **PENDING_APPROVAL**）并**发送**（Supply **APPROVAL**）；发放行上库房/批号/数量由护士执行侧写入，本场景**不修改** |
| **后置（摘要）** | 核发成功 → 发放行 **COMPLETED**（写配药/发药人与时刻，**终态真源**）、Supply **AGREE**（审批链中转）、SupplyDelivery **COMPLETED**、库存扣减、追溯出库；驳回 → Supply **REJECT**、发放行回 **PREPARATION** 并清 `summary_no` |

### 1.2 角色与关键动作

| 角色 | 关键动作 |
| --- | --- |
| **住院药房发药人员** | 查询待核发汇总单 → 查看合并明细与发放行批号 → 指定领药人、（可选）追溯码与调配药师 → **核发**；或选中汇总单 **驳回** |
| **系统** | 按汇总单强加载 **SUMMARIZED** 发放行 → 按 lot+location 合并扣库 → 批量推进状态与 Supply 审批链 |

### 1.3 术语与口径约定

| 术语 | 口径 |
| --- | --- |
| **待核发汇总单** | type **27**、`bus_no` / `summaryNo` 一致；`SupplyRequest.status_enum = APPROVAL`（护士站 **IF-IP-SUM-05** 发送后） |
| **合并明细（Supply 行）** | 同 **AS-IP-NUR-010 · IF-IP-SUM-03**：按 itemId+单价合并；**lot_number 空**；数量展示 **`displayQuantityText`**（行单位直显，**BR-IP-SUM-015**）；持久化 **`itemQuantity`** 仍为拆零；合并行另含 **生产厂家**、**发放药房**；**批次号/追溯码**在合并层固定为空（`—`），批号真相在发放行明细 |
| **发放行明细（扣库真相源）** | 同一 `summaryNo` 下全部 **MedicationDispense**；核发前须 **SUMMARIZED**（**中间态**，以 `summary_no` 标识汇总路径）；核发后须 **COMPLETED**（**终态**）；各行保留 **location_id / lot_number / quantity / unit_code**（执行 FIFO 写入） |
| **领药人（receiverId）** | **IF-IPS-03 核发时必填**；写入 **SupplyDelivery.receiver_id**（护士站不再采集） |
| **调配药师** | 核发时指定，规则对齐 **BR-IPP-004**（不得与当前登录发药药师同人）；**无**独立「保存配药」接口 |
| **追溯码** | **汇总路径**：核发前校验（对齐 **BR-IPP-003**）；**仅**写入 **`SupplyDelivery.trace_no`**（按供应行）；**不**写 **`MedicationDispense.trace_no`**；核发成功后 **`cus_trace_number` + `cus_trace_number_flow` OUT**（汇总单号 + 药房，不关联患者） |
| **库存扣减** | **仅**按 **MedicationDispense** 行：有 `lot_number` 则按批号扣减；无批号则 `allocateAndDecrease`（与 **AS-IP-PHA-001** 发药同族）；**禁止**对 SupplyDelivery 行扣库 |
| **SupplyDelivery 创建时机** | **仅 IF-IPS-03 核发时**调用 `createSummarySupplyDelivery`，随后同事务推进 **COMPLETED**；护士站**不**预创建 |
| **QueryService 能力** | 单头 **pageMedicationSummarySlips**（加 `statusEnum=APPROVAL`）；合并行 **listMedicationSummarySlipLinesByBusNo**；发放行 **listMedicationDispensesBySummaryNo** |

### 1.4 本期范围与非目标

| 类别 | 内容 |
| --- | --- |
| **本期** | IF-IPS-01～06：待核发列表、详情、核发、驳回、打印（汇总工作台工具栏 **「打印汇总单」**，待核发/已发药均可；客户端复用汇总领药单模板） |
| **待接通** | —（前端汇总发药 Tab 与 IF-IPS-01～04/06 已接线） |
| **非目标** | 逐行住院发药（**AS-IP-PHA-001**）；护士站汇总领药写路径（**AS-IP-NUR-010**）；汇总单撤回/取消汇总 UI；改批号/改数量；医保上传；ChargeItem 写；耗材 |

### 1.5 领域清单（聚合 + 职责 + DM 路径）

| 聚合（域） | 在本场景中的职责（摘要） | DM 路径 |
| --- | --- | --- |
| **SupplyRequest** | type 27 汇总单审批态；核发 **AGREE**、驳回 **REJECT** | `DM-WKF-006-SupplyRequest-供应请求.md` |
| **SupplyDelivery** | 核发时创建汇总发放行；写领药人；完成态 **COMPLETED** | `DM-WKF-005-SupplyDelivery-供应发放.md` |
| **MedicationDispense** | 汇总批次发放行；扣库真相源；`completeSummaryDispense` / `cancelMedicationDispenseSummary` | `DM-MED-002-MedicationDispense-药品发放.md` |
| **InventoryItem** | 核发前校验、核发时扣减 | 物资库存相关 DM |
| **TraceNoManage** | 追溯码占用校验与出库 | 追溯码管理 DM |
| **MedicationDefinition** | 目录展示、unitBands 拆零换算 | `DM-MED-001-MedicationDefinition-药品定义.md` |
| **ChargeItem** | 详情单价/总价**只读**（合并行展示，与护士站 IF-IP-SUM-03 同族） | `DM-ADM-011-ChargeItem-收费明细.md` |

### 1.6 业务规则（BR-*）

| 规则 ID | 描述 | 绑定 IF |
| --- | --- | --- |
| **BR-IPS-001** | 列表仅 **type 27**；Tab 切换仅传 **`summaryDispenseCompletedOnly`**，由发放表收窄 `summary_no`（待发 **SUMMARIZED** / 已发药 **COMPLETED**）；待发另固定 Supply **APPROVAL**（服务端写死，不接受客户端审批态入参）；**管辖药房 id 集合非空**时 `purpose_location_id IN` 可操作药房；**集合为空且非 strict** 时不拼位置谓词（与 **IF-IP-SUM-02** 同族）；关键字/时段同族 | 01 |
| **BR-IPS-002** | **写路径**（核发/驳回）：同 `summaryNo` 下全部发放行须 **SUMMARIZED** 且 `summary_no` 一致；Supply 同号行须 **APPROVAL**。**读路径**：仅以发放表门控——同号发放行全部 **SUMMARIZED** 为待核对、全部 **COMPLETED** 为已发药；待发详情另断言 Supply **APPROVAL**（拒绝脏数据可读） | 02、03、04 |
| **BR-IPS-003** | **核发**须指定 **receiverId**（领药人） | 03 |
| **BR-IPS-004** | **汇总追溯码**：核发前校验未出库冲突（对齐 **BR-IPP-003**）；**落库** **`wkf_supply_delivery.trace_no`**（按 **SupplyRequest** 行）；**禁止**写入 **`MedicationDispense.trace_no`**；**`cus_trace_number` + `cus_trace_number_flow` OUT** 以汇总单号 + 药房 **purpose_location_id** 为维度，**不**关联患者发放行 | 03 |
| **BR-IPS-005** | 调配药师：须指定且**不得**与当前登录发药药师同人（对齐 **BR-IPP-004**） | 03 |
| **BR-IPS-006** | **库存扣减**仅按 **MedicationDispense** 持久化的 **location_id + lot_number（可空）+ quantity + unit_code**；数量先按 **BR-OPP-017** **操作单位→拆零（五类）** 再按 lot+location **合并**扣减（与 **AS-IP-PHA-001** 核发同族） | 03 |
| **BR-IPS-007** | **禁止** `SupplyDelivery` COMPLETED 触发库存扣减；Delivery 仅承载领药人/发药时间/完成态 | 03 |
| **BR-IPS-008** | 核发编排顺序：**先**库存校验与扣库 **→** `completeSummaryDispense`（**SUMMARIZED→COMPLETED**，写配药/发药人与时刻）**→** `createSummarySupplyDelivery` + Delivery 完成 **→** `agreeRequest`（Supply 仅为中转）；**同一事务** | 03 |
| **BR-IPS-009** | **驳回**：`rejectRequest` → `cancelMedicationDispenseSummary`（发放行 **SUMMARIZED→PREPARATION**，清 `summary_no`）；整批回滚 | 04 |
| **BR-IPS-010** | 已 **AGREE/COMPLETED** 汇总单禁止重复核发；已非 **APPROVAL** 禁止驳回 | 03、04 |
| **BR-IPS-011** | 本 AS **不读不写** ChargeItem 写模型；**不处理**医保、逐行发药、护士站汇总 | 01～06 |
| **BR-IPS-012** | **合并行界面列**对齐住院「按患者发药」药品表：项目名称、数量（**`displayQuantityText`**，**BR-IP-SUM-015**）、规格、批次号（合并层 `—`）、金额、发放库房、**追溯码（合并层展示 SupplyDelivery；待发可扫）**、厂家；批号真相在 **dispenseItems**；**dispenseItems 不含追溯码**；合并行按拼音首字母序，单头回填 **申领科室**（与 **AS-IP-NUR-010 BR-IP-SUM-020** 同源） | 02、06 |
| **BR-IPS-013** | 汇总 Tab 左侧列表与「按患者发药」同构：**待核对**（发放行 **SUMMARIZED** + Supply **APPROVAL**）/ **已发药**（发放行 **COMPLETED**，`summaryDispenseCompletedOnly=true`）；已发药详情只读（禁用核发/驳回/扫追溯码） | 01、02 |

---

## 2. 页面与接口总览（契约概览）

| IF 编号 | 用户动作 | 接口语义（业务） | 读/写 | 关键规则（BR） | 关键状态变化 | 涉及领域 | 查询服务 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| IF-IPS-01 | 汇总 Tab：查询待核发 / 已发药 | 分页查询 type 27 汇总单头（待发 **APPROVAL**+**SUMMARIZED**；已发药 **`summaryDispenseCompletedOnly`**+**COMPLETED**） | 读 | BR-IPS-001、**013**、011 | 无 | SupplyRequest + MedDispense | **MedicationDispenseQueryService#listSummaryNosWhereAllDispenseRowsHaveStatus** + **pageMedicationSummarySlips** |
| IF-IPS-02 | 点详情 | 汇总单**合并 Supply 行** + **发放行明细**（含批号/数量/患者锚点；合并行 **displayQuantityText**；已核发只读） | 读 | BR-IPS-002、011、**012**、**013** | 无 | SupplyRequest + MedDispense + MedDefinition | **listMedicationSummarySlipLinesByBusNo** + **listMedicationDispensesBySummaryNo** |
| IF-IPS-03 | 勾选汇总单 → 核发 | **批量汇总核发**（扣库 + 完成发放 + 创建 Delivery + AGREE） | 写 | BR-IPS-003～008、010、011 | SUMMARIZED→COMPLETED；新建 Delivery COMPLETED；APPROVAL→AGREE | MedDispense + Supply + Delivery + Inventory | — |
| IF-IPS-04 | 勾选汇总单 → 驳回 | **批量驳回**（reject + 释放发放行） | 写 | BR-IPS-009、010、011 | APPROVAL→REJECT；SUMMARIZED→PREPARATION | SupplyRequest + MedDispense | — |
| IF-IPS-05 | 打印待核发列表 | 客户端打印（复用 IF-IPS-01） | 读 | BR-IPS-011 | 无 | 同 IF-IPS-01 | 同 IF-IPS-01 |
| IF-IPS-06 | 打印汇总单 | 客户端打印（复用 IF-IPS-02） | 读 | BR-IPS-011 | 无 | 同 IF-IPS-02 | 同 IF-IPS-02 |

---

## 3. 缺口跟踪（Gap）

### 3.1 缺口分类说明

| 分类 | 含义 | 本文处理 |
| --- | --- | --- |
| **领域层 Gap** | DM §6 已定义或 AS 读/写必需，但 QueryService / QueryModel / 域写口径未就绪 | §3.2 |
| **应用层未实现** | AppService、Controller、DTO | **不算 Gap**（AS 成稿后成码） |
| **应用层编排（非 Gap）** | 扣库合并、Delivery 创建与完成、reject 串行 | 在应用层实现 |

### 3.2 领域层 Gap 清单

| Gap ID | 缺口类型 | 文件 + 章节 | 缺什么 / 不一致点 | 关闭条件 | 影响 IF | 优先级 | 状态 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| GAP-IPS-001 | QueryModel 过薄 | `DM-MED-002` §6.2.3 | ~~`MedicationDispenseSummaryItemQueryModel` 仅 id/status/quantity~~ | 扩展 QueryModel + `listMedicationDispensesBySummaryNo` 映射 | IF-IPS-02、03 | P0 | **已关闭** |
| GAP-IPS-002 | 域写口径 | `DM-WKF-005` §2.3 / 实体 | ~~`SupplyDelivery#toCompleted` 历史误写 PART_COMPLETED~~ | 修正实体完成态 + 回归 IF-IPS-03 | IF-IPS-03 | P0 | **已关闭** |
| GAP-IPS-003 | 域方法注释 | `SupplyDeliveryDomainService#createSummarySupplyDelivery` | ~~注释仍写「SEND 后创建」~~ | 更新 JavaDoc + DM-WKF-005 §1.1/§6.1.4 | IF-IPS-03 | P1 | **已关闭** |
| GAP-IPS-004 | 读侧过滤 | `SupplyRequestDocumentListQuery.statusEnum` | 域 Query **已支持** status 过滤 | SDD IF-IPS-01 固定传 **APPROVAL** | IF-IPS-01 | P1 | **已关闭（SDD）** |
| GAP-IPS-005 | 详情组装 | AS §4 IF-IPS-02 | 合并行 + 发放行明细需应用层编排 | **AS-IP-PHA-002-SDD** §3.3 Assembler | IF-IPS-02 | P1 | **已关闭** |
| GAP-IPS-006 | 应用层成码 | `AS-IP-PHA-002-SDD` §2 | ~~应用层待生成~~ | `InpatientPharmacySummaryDispenseAppService` 等已落地 | IF-IPS-01～04 | P0 | **已关闭** |

> **说明**：写侧 **`completeSummaryDispense`**、**`cancelMedicationDispenseSummary`**、**`createSummarySupplyDelivery`**、**`updateCompletedStatusBatch`**、**`agreeRequest`** / **`rejectRequest`** 等领域能力**已存在**；**扣库合并**与 **reject 串行**在 **IF-IPS-03/04 应用编排**实现（对齐 **AS-IP-PHA-001** 核发与 openhis `SummaryDispenseMedicine` 存量行为）。

---

## 4. 接口编排说明书（业务步骤）

### IF-IPS-01 汇总单头列表（待核对 / 已发药）

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 用户进入汇总发药 Tab 或切换「待核对 / 已发药」；待核对须护士站 **IF-IP-SUM-05 发送**（`APPROVAL`）；**PENDING_APPROVAL** 待发送单不在本列表 |
| **主路径步骤** | 解析可操作药房 → 按发放表门控收窄 `summary_no`（**SUMMARIZED** / **COMPLETED**）→ 分页查询 type **27** 汇总单头 → 展示单号、申请时间、药房、申请人、`summaryDispenseStatusText` |
| **关键分支** | 可按药房、申请时段、关键字（单号）筛选；**已发药** Tab 仅发放行 **COMPLETED**；**不含**已驳回（REJECT） |
| **失败分支** | 查询失败（EX-IPS-102） |
| **查询侧说明** | **MedicationSummarySlipQueryModel**（`pageMedicationSummarySlips`，`statusEnum` 由请求传入） |

### IF-IPS-02 汇总单详情（合并行 + 发放行）

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 用户点击某汇总单详情 |
| **主路径步骤** | ① 按 `summaryNo` 加载 **合并 Supply 行**（无批号；单价/总价只读，**BR-IP-SUM-009/010** 同族；数量 **`displayQuantityText`** 行单位直显，**BR-IP-SUM-015**）→ ② 加载同号 **MedicationDispense** 发放行列表（含 **批号、数量、单位、就诊/患者** 等扣库字段）→ ③ 装配药品展示（含厂家、发放药房） |
| **关键分支** | 发放行供药房核对扣库明细；合并行供总量核对 |
| **失败分支** | 单号不存在或状态不可读（EX-IPS-103）：发放行态不一致、非 **SUMMARIZED**/**COMPLETED**，或待发时 Supply 非 **APPROVAL** |
| **查询侧说明** | **MedicationSummarySlipLineQueryModel** + **MedicationDispenseSummaryItemQueryModel**（GAP-IPS-001 关闭后字段齐全） |

### IF-IPS-03 汇总核发

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 已选至少一张 **APPROVAL** 汇总单；已指定 **receiverId**；可选追溯码、**调配药师** |
| **主路径步骤** | ① 按 `summaryNo` 强加载 Supply 行，断言 **APPROVAL**（**BR-IPS-002/010**）→ ② 强加载全部 **SUMMARIZED** 发放行 → ③ 追溯码/调配药师校验（**BR-IPS-004/005**）→ ④ **合并**发放行按 lot+location 做库存充足校验 → ⑤ **统一扣库**（**BR-IPS-006**）→ ⑥ `completeSummaryDispense` → ⑦ `createSummarySupplyDelivery`（写 **receiverId**）→ ⑧ Delivery 批量 **COMPLETED** → ⑨ `agreeRequest` → ⑩ 追溯出库 → ⑪ **同一事务**提交（**BR-IPS-008**） |
| **关键分支** | 支持批量多张 `summaryNo`（建议 ≤50）；**无**保存配药中间态 |
| **失败分支** | 非 APPROVAL/已核发（EX-IPS-201）；库存不足（EX-IPS-203）；追溯/药师（EX-IPS-204）；其他域失败（EX-IPS-205） |
| **查询侧说明** | 写前强加载发放行与 Supply |

### IF-IPS-04 汇总驳回

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 用户选中 **APPROVAL** 汇总单并确认驳回 |
| **主路径步骤** | ① 断言 Supply **APPROVAL** → ② `rejectRequest`（同 `bus_no` 全行 **REJECT**）→ ③ `cancelMedicationDispenseSummary`（发放行 **SUMMARIZED→PREPARATION**，清 `summary_no`）→ ④ 同事务提交 |
| **关键分支** | 批量多张 summaryNo；**不**创建 SupplyDelivery |
| **失败分支** | 状态不允许（EX-IPS-202）；单号不存在（EX-IPS-103） |
| **查询侧说明** | 写前 `getSupplyByBusNo` |

### IF-IPS-05 打印待核发列表

| 维度 | 说明 |
| --- | --- |
| **触发前提** | IF-IPS-01 已有数据 |
| **主路径步骤** | 前端基于列表或再次调用 IF-IPS-01 |
| **关键分支** | 无写操作 |
| **失败分支** | — |
| **查询侧说明** | 复用 IF-IPS-01 |

### IF-IPS-06 打印汇总单

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 药房汇总发药工作台已选中汇总单，用户点击 **「打印汇总单」**（待核发 / 已发药只读态均可） |
| **主路径步骤** | 客户端按 `summaryNo` 拉取 **IF-IPS-02** 详情（含 **申领科室**、拼音序合并行）→ 静默打印；**无**独立后端写接口 |
| **关键分支** | 无写操作；已发药只读详情仍可打印 |
| **失败分支** | 单号无效（EX-IPS-103）或未选单（前端提示） |
| **查询侧说明** | 与护士站 **IF-IP-SUM-03** 打印同源（`applicantDepartmentName` + 拼音序 `drugRows`；scenario `ip-dispenseSummary-print`） |

---

## 5. 业务异常与用户提示

| 异常 ID | 场景 | 用户提示语义 | 可重试 |
| --- | --- | --- | --- |
| EX-IPS-101 | 未登录或无权 | 未登录或无权操作该药房 | 是 |
| EX-IPS-102 | 列表查询失败 | 加载失败，请稍后重试 | 是 |
| EX-IPS-103 | 汇总单不存在或状态已变 | 汇总单不存在或已失效，请刷新 | 是 |
| EX-IPS-201 | 重复核发或状态非 APPROVAL | 汇总单已核发或状态不允许，请刷新 | 刷新后重试 |
| EX-IPS-202 | 驳回状态不允许 | 当前状态无法驳回，请刷新 | 刷新后重试 |
| EX-IPS-203 | 库存停供或不足 | 按品种/批号提示库存不足 | 联系库管 |
| EX-IPS-204 | 追溯码或调配药师 | 追溯码冲突或请选择有效调配药师 | 视场景 |
| EX-IPS-205 | 批量校验或域写失败 | 核发/驳回失败，请检查汇总单状态 | 刷新后重试 |

---

## 6. 事件语义与消费方

| 事件（语义级） | 触发点 | 消费方 / 意图 | 幂等语义 |
| --- | --- | --- | --- |
| **MedicationDispenseSummaryCompletedEvent** | IF-IPS-03 核发成功 | 汇总/发药读侧刷新；计费域（域外） | 以 `summaryNo` |
| **SupplyDeliveryCreatedEvent** | IF-IPS-03 创建 Delivery | 汇总单头发药时间/领药人展示 | 以发放行 ID |
| **SupplyDeliveryCompletedEvent** | IF-IPS-03 Delivery 完成 | 同上 | 以发放行 ID |
| **SupplyRequestAgreedEvent** | IF-IPS-03 agreeRequest | Supply 读侧；**不**再触发库存扣减 | 以 `bus_no` |
| **SupplyRequestRejectedEvent** | IF-IPS-04 驳回 | 护士站汇总 Tab；待发明细刷新 | 以 `bus_no` |
| **MedicationDispenseSummaryCancelledEvent** | IF-IPS-04 释放发放行 | 护士站待汇总列表 | 以 `summaryNo` + 发放行 ID |
| 库存扣减 / 追溯出库 | IF-IPS-03 | 库存/监管 QueryService | 库存项+事务 / 码+单号 |

---

## 7. 非功能性需求

| 维度 | 要求 |
| --- | --- |
| **一致性** | IF-IPS-03/04 与发放行、Supply、Delivery、库存 **同一事务**；任一步失败 **整批回滚** |
| **幂等** | 重复核发依赖 **APPROVAL** 门控 + 已 AGREE 防重；重复驳回同理 |
| **性能** | 单次批量核发建议 ≤50 张汇总单；扣库 **先合并再写**（与 **AS-IP-PHA-001** 同族） |
| **审计** | 记录核发操作人、发药人/调配药师、领药人、发药时间、`summaryNo` |
| **ChargeItem** | 全 Tab **只读** |

---

## 8. 测试要点（业务视角）

| 类别 | 要点 |
| --- | --- |
| **正向** | 护士站发送后 IF-IPS-01 待核对可见 APPROVAL 单；核发后 **已发药** Tab 可见发放行 **COMPLETED** 单并可只读查看详情；合并行数量 **行单位直显**（如 `15支`，禁止 `1盒5支`）；核发后 COMPLETED + Delivery + 库存减（Supply **AGREE** 仅为审批链中转） |
| **扣库** | 同汇总单多行同 lot+location 合并扣减；无 lot 走 allocate；**不对 Delivery 扣库** |
| **驳回** | 驳回后 Supply REJECT；发放行回 PREPARATION 且无 summaryNo；护士站待汇总可再次勾选 |
| **门控** | 非 APPROVAL 拒绝核发/驳回；已 AGREE 拒绝重复核发 |
| **追溯/药师** | 追溯冲突拒绝；调配药师同人拒绝 |
| **并发** | 两窗口同时核发同一 summaryNo → 其一成功、其一 EX-IPS-201 |
| **打印** | IF-IPS-05/06 不触发写库；药房工作台可打当前汇总领药单（待核发/已发药） |

---

## 9. 关联文档与变更历史

### 9.1 关联文档

| 文档 | 关系 |
| --- | --- |
| `AS-IP-NUR-010-住院护士站汇总领药.md` | 上游：保存/发送；发放行 SUMMARIZED + Supply APPROVAL |
| `AS-IP-PHA-002-住院汇总发药-SDD.md` | 本 AS 应用层详细设计（IF-IPS-01～04 编排序列；**v1.2 已与代码对齐**） |
| `AS-IP-PHA-001-住院发药.md` | 对照：逐行发药；追溯/药师/扣库同族；**无**汇总 |
| `DM-MED-002-MedicationDispense-药品发放.md` | §6.1.11 取消汇总、§6.1.12 完成汇总发药 |
| `DM-WKF-006-SupplyRequest-供应请求.md` | type 27、agree/reject |
| `DM-WKF-005-SupplyDelivery-供应发放.md` | createSummarySupplyDelivery |
| openhis `SummaryDispenseMedicineAppServiceImpl` | 存量行为参考（扣库在发放行） |

### 9.2 变更历史

| 版本 | 日期 | 摘要 |
| --- | --- | --- |
| v1.10 | 2026-10-16 | **IF-IPS-02/06 / BR-IPS-012**：详情回填 **申领科室**、合并行拼音首字母序；打印与护士站 **BR-IP-SUM-020** 同源；代码先行回写 |
| v1.9 | 2026-10-13 | **IF-IPS-02 / BR-IPS-012**：合并行 `displayQuantityText` 对齐 **BR-IP-SUM-015** 修订——行单位直显（禁止 `1盒5支`）；金额仍只读累加 |
| v1.8 | 2026-10-12 | **IF-IPS-06 / UI**：药房汇总发药工作台接通「打印汇总单」（待核发/已发药均可）；关闭「待接通」过时表述 |
| v1.7 | 2026-08-19 | **BR-IPS-006**：汇总核发扣库与住院 **BR-IPP-015** / 门诊 **BR-OPP-017** 同口径（操作单位→拆零，五类优先） |
| v1.6 | 2026-08-02 | **IF-IPS-02 / UI**：合并行出参补贴码单位术语文案；批量扫码弹窗药品明细展示追溯码单位与码/拆零比；对齐 SDD **v1.6** / **AS-CUS-TRC-001** v2.4 |
| v1.5 | 2026-07-04 | **追溯码分流**：汇总路径 **`SupplyDelivery.trace_no`**（按供应行）；**禁止**写 **`MedicationDispense.trace_no`**；IF-IPS-03 DTO 改为 **`supplyRequestId`** |
| v1.3 | 2026-06-17 | IF-IPS-02 合并行新增 **`displayQuantityText`/`manufacturerName`/`purposeLocationName`**（**BR-IPS-012**）；数量展示对齐 **BR-IP-SUM-015**；前端汇总明细表列对齐「按患者发药」 |
| v1.2 | 2026-06-09 | **BR-IPS-001** 更正：type 27 列表按 `purpose_location_id` 收窄（非 `source_location_id`）；IF-IPS-01 与护士站 IF-IP-SUM-02 门控同族；仅 **APPROVAL**（已发送）可见 |
