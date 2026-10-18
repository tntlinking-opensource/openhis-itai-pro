# 科室请领单 · 应用服务设计（AS）

> **文档职责**：说明「科室药房请领单」页面的**接口契约（IF）**、业务语义、涉及聚合与 QueryService、规则（BR）、编排意图；作为上游契约供详细设计与实现对齐。
>
> **本文必须写**：IF 编号；用户动作与读写属性；关键校验与状态变化；Gap；异常口径；事件语义；非功能约束；业务向测试要点。
>
> **本文禁止写**：具体类名/应用方法名；Request/Response 字段明细；包路径；Mapper；伪代码；写库顺序；QueryService 字段组装规则。
>
> **页面边界**：科室业务线 — 科室药房人员向药库发起 **`DEPT_REQUISITION`**（与 **`SupplyType#getCode()`** 一致）请领**申请**：列表、详情、新建/编辑/删除、提交/撤回、审批同意/驳回。**请领为申请单**：审批同意前**不**触发 **SupplyDelivery** / **InventoryItem** 数量变更；**不**在本页自动生成药库出库单或科室入库单。**药库出库单（`CABINET_STOCK_OUT`）由用户在「药库出库单」应用服务另行创建**（可手工建单，或从已同意请领快捷带入），见 **AS-INV-WHS-003**；与 **AS-INV-PHA-002**（药房请领）**对称**。
>
> **选品与页面 IF**：**批号级选品**、init/列表/详情字段族与 **AS-INV-DEP-002**（科室入库）及 **AS-INV-PHA-002**（药房请领）**同构**；**审批语义**与 **AS-INV-WHS-001**（药库订货）**同构**（同意仅终态，不编排执行单）。
>
> **位置口径（强制统一）**：全链路采用 **DM 物流双端点** — **`source_location_id`** = **药库**（出方/扣减侧）、**`purpose_location_id`** = **科室药房**（入方/接收侧，形态 **`DEPARTMENT_PHARMACY`**）；**`source_type_enum`/`purpose_type_enum`** 由 **Location** 推导，存 **`SupplyRequestType#getCode()`** 字符串。
>
> **覆盖声明**：本稿以当期业务裁决、**DM-WKF-006 v1.12** 与已落地 `department/requisition` 后端为基线；正式定稿前须与科室请领页 UI 全量按钮对照修订 **§1.4、§2、§4**。
>
> **编码**：AS-INV-DEP-001 · **版本**：v0.3 · **日期**：2026-07-26

---

## 1. 场景概述

### 1.1 目标与边界

| 维度 | 说明 |
| --- | --- |
| **在内** | 科室药房向药库发起请领草稿；同一 `bus_no` 多行**批号级**明细；状态沿「待审核 ↔ 审核中 → 同意/驳回/撤回」流转；可编辑态改单与删单；**选品**以**来源药库库存批号行**为主（效期、可用量，与 **IF-DEP-QL-11** 同构） |
| **在外** | 药库出库执行、源库扣减、目的科室药房增加、科室入库壳单生成（**AS-INV-WHS-003** / **AS-INV-DEP-002**）；药库/药房/科室**调出单不消费请领**；药房请领（**`PHARMACY_REQUISITION`**）；财务月结、发药链 |
| **跨域** | 出库单创建、批号执行与库存落账在 **AS-INV-WHS-003**；本场景审批同意**仅**将请领单置为 **`AGREE`**，**不**调用 `createStockOut` / 编排入库执行单 |
| **编排权威** | 以本文 **§2、§4** 为准；写侧以 **DM-WKF-006** **`createRequisition`**（type **20**）为准 |
| **前置（摘要）** | 会话可解析科室药房 **Location**；来源药库与目的科室药房主数据可用；科室药房仅向药库请领（对齐 **BR-SR-017** 类位置约束） |
| **后置（摘要）** | 请领 **同意**后，药库人员可在出库模块从本单快捷带入建单；**驳回/撤回**不产生出库/入库执行单 |

### 1.2 角色与关键动作

| 角色 | 关键动作 |
| --- | --- |
| **科室药房制单员** | 查询列表 → 新建请领 → 按批选品 → 提交审批 →（必要时）撤回 |
| **科室主管/审批人** | 同意 / 驳回 |
| **药库制单员**（下游，非本页） | 在出库页手工建单或**关联已同意科室请领**带入（**AS-INV-WHS-003** **IF-WHS-SO-12/13**） |
| **管理员** | 待审核态修改或逻辑删除 |

### 1.3 术语与口径约定

| 术语 | 口径 |
| --- | --- |
| **科室请领单** | `type_enum` = **`DEPT_REQUISITION`**（**`SupplyType` 值 20**），**申请单**性质：同意前**不**触发发放与库存变更 |
| **`bus_no` 前缀** | 新建且首行未显式传入 **`busNo`** 时：**`SupplyType.busNoSeqKey`**（请领 **`DQR`**）+ 业务日 + 当日序号；无法匹配白名单时回退 **`SR`** |
| **两阶段（实现分工）** | 领域语义：请领同意后可衔接出库执行（**DM-WKF-006**）；**本产品线**将出库单创建放在 **AS-INV-WHS-003**，本页同意**不**自动生成出库/入库单（对齐 **BR-DEP-QL-009**） |
| **源/目标位置** | **`source_location_id`** = 药库；**`purpose_location_id`** = 科室药房（**`DEPARTMENT_PHARMACY`**）；列表默认按**目的科室药房**（**`purposeLocationId`**）筛选 |
| **选品主表** | **库存批号行** + **ProductDefinition**；**`sourceLocationId`** 锚来源药库；**`unitBands`**、**`purchaseUnitPrice`**（**`LOT_NUMBER_COST`**）与 **IF-PHA-IN-11** / **IF-WHS-SO-11** 同构；**选品 UI 须展示 `lotNumber`（批号）**（见 **AS-INV-WHS-003 §1.3**） |
| **与出库数量** | 本期**不支持部分发货/请领余量跟踪**；出库侧带入后**允许编辑**，请领与出库数量可不一致；**一张已同意请领仅允许被一张未删除出库单引用**（**BR-DEP-QL-011**，与 **BR-PHA-QL-011** 同构） |
| **枚举口径** | 一律 **`getCode()`** 字符串 |
| **DM 路径** | 下文聚合路径均指 `docs/领域/DomainModel/ 下对应文件 |

### 1.4 本期范围与非目标

| 类别 | 内容 |
| --- | --- |
| **本期** | 请领申请全生命周期 IF；批号级选品；同意**仅**状态终态；与 **WHS-003** 关联契约（列表/拉取明细）在出库侧落地 |
| **非目标** | 请领 agree 自动生成出库/入库；部分发货与已发余量；财务审批（type 20 不在财务审批族）；追溯码写入（执行阶段在出库 agree，见 **AS-INV-WHS-003**） |

### 1.5 领域清单（聚合 + 职责 + DM 路径）

| 聚合（域） | 在本场景中的职责（摘要） | DM 路径 |
| --- | --- | --- |
| **SupplyRequest（WKF）** | **`createRequisition`**（type 20）建单/修订；审批；逻辑删除 | `DM-WKF-006-SupplyRequest-供应请求.md` |
| **Location（ADM）** | 校验药库/科室药房位置；推导 **`source_type_enum`/`purpose_type_enum`** | `DM-ADM-003-Location-位置.md` |
| **InventoryItem（WKF）** | 选品读侧：批号、效期、可用量（**不**在请领 agree 写库存） | `DM-WKF-008-InventoryItem-库存项.md` |
| **ProductDefinition** | 选品/详情富化 | 目录 DM |
| **（下游）SupplyRequest** | **`createStockOut`**（type 4）仅在 **AS-INV-WHS-003** | `DM-WKF-006` §6.1.4 |

### 1.6 业务规则（BR-*）

| 规则 ID | 描述（语义锚点） | 绑定 IF |
| --- | --- | --- |
| **BR-DEP-QL-001** | 最小建单必备字段；`type_enum` 固定 **`DEPT_REQUISITION`**（请求体不传） | IF-DEP-QL-04 |
| **BR-DEP-QL-002** | **`source_location_id`** 须为药库、**`purpose_location_id`** 须为科室药房（**`DEPARTMENT_PHARMACY`**） | IF-DEP-QL-04 / IF-DEP-QL-05 / IF-DEP-QL-11 |
| **BR-DEP-QL-003** | 源/目的位置合法 —— **BR-SR-006** | IF-DEP-QL-04 |
| **BR-DEP-QL-004** | 同一 `bus_no` 状态一致 —— **BR-SR-002** | IF-DEP-QL-05～10 |
| **BR-DEP-QL-005** | 仅可编辑态修改 —— **BR-SR-003**、**BR-SR-014** | IF-DEP-QL-05 |
| **BR-DEP-QL-006** | 提交审批 —— **BR-SR-004** | IF-DEP-QL-06 |
| **BR-DEP-QL-007** | 同意/驳回追溯 —— **BR-SR-005** | IF-DEP-QL-08 / IF-DEP-QL-09 |
| **BR-DEP-QL-008** | 逻辑删除防下游引用 —— **BR-SR-010** | IF-DEP-QL-10 |
| **BR-DEP-QL-009** | 请领为**申请单**：同意**不**自动生成出库/入库执行单；出库在 **AS-INV-WHS-003** 创建 | IF-DEP-QL-08 |
| **BR-DEP-QL-010** | 选品/明细：**`item_quantity`**（拆零）≤ 该 **`inventoryItemId`** 在 **`sourceLocationId`** 下**可用量**（行级锚点，**禁止** `item_id+批号` 聚合）；选品可选 **`positiveQuantityOnly=true`** 时仅 **`quantity > 0`** 批号行（非必传；未传/否则含零账面） | IF-DEP-QL-04 / IF-DEP-QL-05 / IF-DEP-QL-11 |
| **BR-DEP-QL-011** | 已 **`AGREE`** 且已被未删除 **`CABINET_STOCK_OUT`**（**`original_bus_no`** 指向本请领）引用的请领单，**不得**再次出现在可关联列表（**AS-INV-WHS-003** **IF-WHS-SO-12**） | （下游只读规则，本页 agree 不校验） |
| **BR-DEP-QL-012** | 列表聚合行须 enrich **制单人/审批人展示名**（由 **`applicant_id`/`approver_id`** 批量解析）与 **`totalAmount`**（同号各行 **`total_price`** 之和）；对齐 **AS-INV-PHA-002** **BR-PHA-QL-012** | IF-DEP-QL-02 |
| **BR-DEP-QL-013** | 新建/修订明细须持久化批号 **`end_time`**（有效期/失效日期，来自选品效期）；详情回显 **`endTime`**（**BR-SR-020**） | IF-DEP-QL-04 / IF-DEP-QL-05 / IF-DEP-QL-03 |

---

## 2. 页面与接口总览（契约概览）

| IF 编号 | 用户动作 | 接口语义（业务） | 读/写 | 关键规则（BR） | 关键状态变化 | 涉及领域 | QueryService |
| --- | --- | --- | --- | --- | --- | --- | --- |
| IF-DEP-QL-01 | 进入请领页面 | 初始化：**目的科室药房**与**来源药库**分列 **OptionDto**；**SupplyStatus** | 读 | — | — | Location | — |
| IF-DEP-QL-02 | 查询/刷新列表 | 分页 **`DEPT_REQUISITION`**；**`bus_no` 合并一行**；展示源药库/目的科室药房；**enrich 制单人/审批人/总金额**（**BR-DEP-QL-012**） | 读 | **BR-DEP-QL-012** | — | SupplyRequest | **`SupplyRequestDocumentListQuery`** |
| IF-DEP-QL-03 | 查看详情 | 按 **`bus_no`** 批号级明细；富化品名/规格/厂家；**`unitBands`** 与 **IF-DEP-IN-03** 同构；回显 **`startTime`/`endTime`**（**BR-DEP-QL-013**） | 读 | **BR-DEP-QL-013** | — | SupplyRequest / ProductDefinition | **`getSupplyByBusNo`** |
| IF-DEP-QL-04 | 新建请领单 | 创建 type 20 草稿；**`PENDING_APPROVAL`** | 写 | BR-DEP-QL-001～003 / **010** / **013** | 新 `bus_no` | SupplyRequest / Location / InventoryItem | 列表/详情刷新 |
| IF-DEP-QL-05 | 编辑请领单 | 可编辑态改批号级明细 | 写 | BR-DEP-QL-005 / **010** / **013** | 明细变更 | SupplyRequest | 同上 |
| IF-DEP-QL-06 | 提交审批 | 待审核 → 审核中 | 写 | BR-DEP-QL-006 | `PENDING_APPROVAL` → `APPROVAL` | SupplyRequest | 待办、列表 |
| IF-DEP-QL-07 | 撤回审批 | 审核中撤回 | 写 | — | `APPROVAL` → `WITHDRAW` / `PENDING_APPROVAL` | SupplyRequest | 同上 |
| IF-DEP-QL-08 | 审批同意 | 审批通过（**仅申请单**） | 写 | BR-DEP-QL-007 / **009** | **`AGREE`**；**不**生成出库/入库、**不**改库存 | SupplyRequest | 列表/详情 |
| IF-DEP-QL-09 | 审批驳回 | 审批拒绝 | 写 | BR-DEP-QL-007 | `REJECT` | SupplyRequest | 同上 |
| IF-DEP-QL-10 | 删除单据 | 逻辑删除 | 写 | BR-DEP-QL-008 | 逻辑删除 | SupplyRequest | QueryService 隐藏 |
| IF-DEP-QL-11 | 请领选品检索 | 分页：**库存批号行** + **ProductDefinition**；与 **IF-PHA-IN-11** 同构；**选品 UI 须展示 `lotNumber`（批号）**（**AS-INV-WHS-003 §1.3**） | 读 | **BR-DEP-QL-010** | — | InventoryItem / ProductDefinition | 库存 + 产品 QueryService |

---

## 3. 缺口跟踪（Gap）

| Gap ID | 缺口类型 | 文件 + 章节 | 缺什么 / 不一致点 | 关闭条件 | 影响 IF | 优先级 | 状态 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| — | — | — | 无（**DM-WKF-006 v1.12** 已修订请领 agree 不自动编排执行单） | — | — | — | — |

> 说明：**`createRequisition` 修订路径**（type **20**）已落地 **`SupplyRequest#assertDeptRequisitionLinesRevisable`**（与药房请领 **PHA-QL-SDD-R-002** 同构），**不**另开 Gap。

---

## 4. 接口编排说明书（业务步骤）

### IF-DEP-QL-01 进入科室药房请领页面初始化

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 用户进入科室药房请领菜单 |
| **主路径步骤** | 加载**目的科室药房**、**来源药库**位置列表（分列 **OptionDto**）；**单据状态**下拉 |
| **关键分支** | 位置经管辖收窄（与 **IF-DEP-IN-01** 同源策略） |
| **失败分支** | EX-DEP-QL-101 |
| **查询侧说明** | 无 |

---

### IF-DEP-QL-02 科室药房请领单列表查询

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 筛选、翻页、刷新 |
| **主路径步骤** | 按 **`DEPT_REQUISITION`**、**`purposeLocationId`**（目的科室药房）、源药库、日期/状态/关键字筛选；**`bus_no` 合并一行** |
| **关键分支** | **`purposeLocationId=0`** 展开为可见科室药房集合（列表哨兵与药房请领对偶） |
| **失败分支** | EX-DEP-QL-102 |
| **查询侧说明** | **`SupplyRequestDocumentListQuery`** 去重分页 |

---

### IF-DEP-QL-03 科室药房请领单详情

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 列表进入或深链 |
| **主路径步骤** | 按 **`bus_no`** 加载头与批号级明细；**效期**、**`unitBands`**、品名富化 |
| **关键分支** | 已同意单：只读审批类操作；可展示是否已被出库引用（只读，QueryService） |
| **失败分支** | EX-DEP-QL-103 |
| **查询侧说明** | 详情展示 |

---

### IF-DEP-QL-04 新建科室药房请领单

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 科室药房上下文有效 |
| **主路径步骤** | 校验源药库/目的科室药房；从选品写入批号级明细（含 **`end_time`** 效期，**BR-DEP-QL-013**）；持久化 **`DEPT_REQUISITION`** 草稿 |
| **关键分支** | 明细超可用量 → 拒绝（**BR-DEP-QL-010**） |
| **失败分支** | EX-DEP-QL-001 |
| **查询侧说明** | **SupplyRequestCreatedEvent** |

---

### IF-DEP-QL-05 编辑科室药房请领单

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 可编辑态（**`PENDING_APPROVAL`** 等，**BR-DEP-QL-005**） |
| **主路径步骤** | 全量对齐修订明细（含 **`end_time`** 效期，**BR-DEP-QL-013**）；再次校验可用量；领域门控 **`assertDeptRequisitionLinesRevisable`** |
| **关键分支** | 非请领类型或终态 → 拒绝 |
| **失败分支** | EX-DEP-QL-004 / EX-DEP-QL-105 |
| **查询侧说明** | 修订路径**不**发布 **CreatedEvent** |

---

### IF-DEP-QL-06 提交审批

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 待审核；明细非空 |
| **主路径步骤** | 推进至审核中 |
| **失败分支** | EX-DEP-QL-003 |
| **查询侧说明** | **SupplyRequestSubmittedEvent** |

---

### IF-DEP-QL-07 撤回审批

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 审核中 |
| **主路径步骤** | 撤回 |
| **失败分支** | EX-DEP-QL-003 |
| **查询侧说明** | **SupplyRequestWithdrawnEvent** |

---

### IF-DEP-QL-08 审批同意

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 审核中 |
| **主路径步骤** | 聚合内推进至 **`AGREE`**；记录审批人/时间；**不**调用 `createStockOut`、**不**写 **SupplyDelivery** / **InventoryItem** |
| **关键分支** | 与 **BR-DEP-QL-009** 一致：用户提示可前往药库出库模块建单 |
| **失败分支** | EX-DEP-QL-003 |
| **查询侧说明** | **SupplyRequestAgreedEvent**（仅请领行状态刷新） |

---

### IF-DEP-QL-09 审批驳回

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 审核中 |
| **主路径步骤** | 驳回终态；可携带驳回原因 |
| **失败分支** | EX-DEP-QL-003 |
| **查询侧说明** | **SupplyRequestRejectedEvent** |

---

### IF-DEP-QL-10 删除单据

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 可删状态；无阻塞下游（已被出库引用时 **BR-SR-010** 语义） |
| **主路径步骤** | 逻辑删除 |
| **失败分支** | EX-DEP-QL-005 |
| **查询侧说明** | **SupplyRequestDeletedEvent** |

---

### IF-DEP-QL-11 科室药房请领选品检索

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 新建/编辑打开选品；须传 **`sourceLocationId`**（来源药库） |
| **主路径步骤** | 分页库存批号行 + **ProductDefinition**；可选 **`positiveQuantityOnly`**（非必传；**`true`**=来源药库账面量>0）；**效期**、**可用量**、**`unitBands`** |
| **失败分支** | EX-DEP-QL-102 |
| **查询侧说明** | 无 |

---

## 5. 业务异常与用户提示

| 语义代码 | 场景 | 用户提示方向 | 可重试 |
| --- | --- | --- | --- |
| EX-DEP-QL-001 | 建单校验失败 | 按提示补全位置/明细 | 是 |
| EX-DEP-QL-003 | 状态不允许操作 | 刷新后重试 | 是 |
| EX-DEP-QL-004 | 禁止编辑 | 说明原因 | 否 |
| EX-DEP-QL-005 | 删除被拒绝 | 下游引用或状态原因 | 否 |
| EX-DEP-QL-101 | 主数据加载失败 | 稍后重试 | 是 |
| EX-DEP-QL-102 | 列表/选品查询失败 | 缩小条件 | 是 |
| EX-DEP-QL-103 | 详情不可用 | 返回列表 | 视情况 |
| EX-DEP-QL-105 | 并发编辑冲突 | 刷新后重试 | 是 |

---

## 6. 事件语义与消费方

| 事件（**DM-WKF-006** §5） | 触发相位（IF） | 典型消费方 |
| --- | --- | --- |
| `SupplyRequestCreatedEvent` | IF-DEP-QL-04 | QueryService 列表、待办 |
| `SupplyRequestSubmittedEvent` | IF-DEP-QL-06 | 待办、列表 |
| `SupplyRequestWithdrawnEvent` | IF-DEP-QL-07 | 列表 |
| `SupplyRequestAgreedEvent` | IF-DEP-QL-08 | 列表；**不**触发库存/发放编排 |
| `SupplyRequestRejectedEvent` | IF-DEP-QL-09 | 列表 |
| `SupplyRequestDeletedEvent` | IF-DEP-QL-10 | QueryService 隐藏 |
| （下游）`SupplyRequestCreatedEvent` 等 | **AS-INV-WHS-003** 建出库 | 出库列表、库存 |

---

## 7. 非功能性需求

| 类别 | 要求 |
| --- | --- |
| **性能** | 批号级选品分页可接受 |
| **幂等** | 提交/同意/驳回短时重复请求幂等或明确已处理 |
| **并发** | 同一单据并发编辑乐观控制 |
| **一致性** | 请领 agree 与库存变更**严格分离**；出库关联以 **`original_bus_no`** 追溯 |
| **审计** | 审批人、审批时间、源/目的位置、批号与数量可追溯 |

---

## 8. 测试要点（业务视角）

| 类别 | 要点 |
| --- | --- |
| **正向** | 新建 → 提交 → 同意 → 列表 **`AGREE`**；**库存不变**；药库出库可从本单带入 |
| **规则** | **BR-DEP-QL-002** 科室药房→药库；**BR-DEP-QL-010** 超可用量拒绝；**BR-DEP-QL-009** 同意不生成执行单 |
| **下游** | **AS-INV-WHS-003** 关联请领：`AGREE` 态、`original_bus_no`、带入字段完整；已引用请领不出现在可关联列表；出库 agree 生成 **`DEPT_STOCK_IN`** 壳单（目的为科室药房时） |
| **对偶** | 与 **AS-INV-PHA-002** 全 IF 对称；审批与 **AS-INV-WHS-001** 同构 |

---

## 9. 关联文档与变更历史

### 9.1 关联文档

| 文档 |
| --- |
| `docs/领域/DomainModel/DM-WKF-006-SupplyRequest-供应请求.md |
| `docs/领域/DomainModel/DM-WKF-008-InventoryItem-库存项.md |
| `docs/领域/DomainModel/DM-ADM-003-Location-位置.md |
| `health/AppService/AS-INV-PHA-002-药房请领单.md`（对称母版） |
| `health/AppService/AS-INV-DEP-002-科室入库单.md`（下游壳单台账） |
| `health/AppService/AS-INV-WHS-003-药库出库单.md`（下游执行与 **IF-WHS-SO-12/13**） |

### 9.2 变更历史

| 版本 | 日期 | 摘要 |
| --- | --- | --- |
| v0.3 | 2026-07-26 | **IF-DEP-QL-02/03**：列表 enrich 制单人/审批人/总金额（**BR-DEP-QL-012**）；建改单持久化 **`end_time`** 效期（**BR-DEP-QL-013** / **BR-SR-020**）；对称 **AS-INV-PHA-002 v0.4** |
| v0.2 | 2026-07-15 | **IF-DEP-QL-11**：**`positiveQuantityOnly`** 改为请求可选（非必传）；与 **AS-INV-PHA-002** 同构 |
| v0.1 | 2026-07-01 | 初稿：**DEPT_REQUISITION** 申请单全 IF；对称 **AS-INV-PHA-002**；同意不自动生成执行单；出库侧可关联拉取 |

### 9.3 IF→DM 追溯摘要

| IF | 业务语义锚点 |
| --- | --- |
| IF-DEP-QL-04～10 | **DM-WKF-006** **`createRequisition`**（20）、§6.1.12～16 |
| IF-DEP-QL-08 | **`agreeRequest`**；**不**编排出库/入库执行单 |
| IF-DEP-QL-11 | **DM-WKF-008**；**ProductDefinition** |

---

**文档负责人**：（待填） · **评审**：（待填）
