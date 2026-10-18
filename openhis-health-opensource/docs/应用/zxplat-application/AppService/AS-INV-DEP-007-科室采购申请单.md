# 科室采购申请单 · 应用服务设计（AS）

> **文档职责**：说明「科室采购申请单」页面的**接口契约（IF）**、业务语义、涉及聚合与 QueryService、规则（BR）、编排意图；作为上游契约供详细设计与实现对齐。
>
> **本文必须写**：IF 编号；用户动作与读写属性；关键校验与状态变化；Gap；异常口径；事件语义；非功能约束；业务向测试要点。
>
> **本文禁止写**：具体类名/应用方法名；Request/Response 字段明细；包路径；Mapper；伪代码；写库顺序；QueryService 字段组装规则。
>
> **页面边界**：科室业务线 — 科室药房管理员维护 **`DEPT_PURCHASE_REQUEST`**（与 **`SupplyType#getCode()`** 一致）**采购申请**：列表、详情、新建/编辑/删除、提交/撤回、审批同意/驳回。**申请单**性质：审批同意**仅终态**，**不**触库存、**不**自动生成药库订货/进货。**药库订货单（`CABINET_PURCHASE_ORDER`）仍可手工直建**；亦可从本单**可选拉取**预填，见 **AS-INV-WHS-001** **IF-WHS-PO-12/13**。
>
> **填写同构**：**产品定义级选品**、单头/明细编辑与 **AS-INV-WHS-001**（药库订货）**同构**（**非**库存批号行选品；批号在进货录入）。与 **AS-INV-DEP-001**（科室请领，批号级、双端点）**不同构**。
>
> **位置口径**：单端点 **`purpose_location_id`** = **科室药房**（形态 **`DEPARTMENT_PHARMACY`**）；**`source_*` / `supplier_id` 恒空**（供应商在下游订货填写）。
>
> **编码**：AS-INV-DEP-007 · **版本**：v0.2 · **日期**：2026-07-15

---

## 1. 场景概述

### 1.1 目标与边界

| 维度 | 说明 |
| --- | --- |
| **在内** | 科室药房发起采购申请草稿；同一 `bus_no` 多行**产品定义级**明细；状态「待审核 ↔ 审核中 → 同意/驳回/撤回」；可编辑态改单与删单；选品同订货（目录+单位带+参考采购价） |
| **在外** | 药库订货建单与审批（**AS-INV-WHS-001**）；进货验收与库存入账（**AS-INV-WHS-002**）；科室请领/出库（**AS-INV-DEP-001** / **AS-INV-WHS-003**）；财务月结 |
| **跨域** | 本页同意**仅**置 **`AGREE`**；订货拉取与供应商绑定在 **AS-INV-WHS-001** |
| **编排权威** | 本文 **§2、§4**；写侧 **DM-WKF-006** **`createDeptPurchaseRequest`**（type **31**） |
| **前置（摘要）** | 会话可解析管辖科室药房；目录主数据可用 |
| **后置（摘要）** | 同意后药库可在订货页可选拉本单；驳回/撤回不产生订货 |

### 1.2 角色与关键动作

| 角色 | 关键动作 |
| --- | --- |
| **科室药房管理员/制单员** | 查询列表 → 新建申请 → 选品填量 → 提交审批 →（必要时）撤回 |
| **科室主管/审批人** | 同意 / 驳回 |
| **药库制单员**（下游，非本页） | 手工建订货 **或** 关联已同意本科室采购申请带入（**AS-INV-WHS-001**） |
| **管理员** | 可编辑态改单或逻辑删除 |

### 1.3 术语与口径约定

| 术语 | 口径 |
| --- | --- |
| **科室采购申请单** | `type_enum` = **`DEPT_PURCHASE_REQUEST`**（**§4.1 值 31**），**申请单**：同意前**不**触发 **SupplyDelivery** / **InventoryItem** |
| **`bus_no` 前缀** | **`SupplyType.busNoSeqKey`** = **`DPR`** + 业务日 + 当日序号；无法匹配回退 **`SR`** |
| **与订货关系** | 订货**可选** `original_bus_no` 指向本单；**非**强制上游；一张已同意申请至多被一张未删除订货引用（**BR-DEP-CPR-011**） |
| **源/目标位置** | 仅 **`purpose_location_id`** = 科室药房；列表默认按目的科室药房筛选；`0` = 可见全部科室药房 |
| **选品主表** | **ProductDefinition**（药品/耗材定义），与 **IF-WHS-PO-11** 同构；**不**展示批号 |
| **枚举口径** | 一律 **`getCode()`** 字符串 |
| **DM 路径** | `docs/领域/DomainModel/ 下对应文件 |

### 1.4 本期范围与非目标

| 类别 | 内容 |
| --- | --- |
| **本期** | 采购申请全生命周期 IF；产品定义级选品；同意仅终态；与 **WHS-001** 可选关联契约在订货侧落地 |
| **非目标** | 申请 agree 自动生成订货/进货；部分采购余量跟踪；财务审批（type 31 不在财务审批族）；供应商必填；批号/追溯码（进货侧） |

### 1.5 领域清单（聚合 + 职责 + DM 路径）

| 聚合（域） | 在本场景中的职责（摘要） | DM 路径 |
| --- | --- | --- |
| **SupplyRequest（WKF）** | **`createDeptPurchaseRequest`** 建单/修订；审批；逻辑删除 | `DM-WKF-006-SupplyRequest-供应请求.md` |
| **Location（ADM）** | 校验科室药房；推导 **`purpose_type_enum`** | `DM-ADM-003-Location-位置.md` |
| **ProductDefinition 等** | 选品/详情富化 | 目录 DM |
| **（下游）SupplyRequest** | **`createPurchaseOrder`** 仅在 **AS-INV-WHS-001** | `DM-WKF-006` §6.1.1 |

### 1.6 业务规则（BR-*）

| 规则 ID | 描述（语义锚点） | 绑定 IF |
| --- | --- | --- |
| **BR-DEP-CPR-001** | 最小建单必备字段；`type_enum` 固定 **`DEPT_PURCHASE_REQUEST`**（请求体不传） | IF-DEP-CPR-04 |
| **BR-DEP-CPR-002** | **`purpose_location_id`** 须为科室药房（**`DEPARTMENT_PHARMACY`**）；**`source_*`/`supplier_id` 不写** | IF-DEP-CPR-04 / IF-DEP-CPR-05 / IF-DEP-CPR-11 |
| **BR-DEP-CPR-003** | 位置合法 —— **BR-SR-006** | IF-DEP-CPR-04 |
| **BR-DEP-CPR-004** | 同一 `bus_no` 状态一致 —— **BR-SR-002** | IF-DEP-CPR-05～10 |
| **BR-DEP-CPR-005** | 仅可编辑态修改 —— **BR-SR-003**、**BR-SR-014** | IF-DEP-CPR-05 |
| **BR-DEP-CPR-006** | 提交审批 —— **BR-SR-004** | IF-DEP-CPR-06 |
| **BR-DEP-CPR-007** | 同意/驳回追溯 —— **BR-SR-005** | IF-DEP-CPR-08 / IF-DEP-CPR-09 |
| **BR-DEP-CPR-008** | 逻辑删除防下游引用 —— **BR-SR-010** | IF-DEP-CPR-10 |
| **BR-DEP-CPR-009** | 申请单：同意**不**自动生成订货/进货；订货在 **AS-INV-WHS-001** 创建（可手工或拉本单） | IF-DEP-CPR-08 |
| **BR-DEP-CPR-010** | 选品/明细为**产品定义级**（与订货同构）；**不**校验库存可用量 | IF-DEP-CPR-04 / IF-DEP-CPR-05 / IF-DEP-CPR-11 |
| **BR-DEP-CPR-011** | 已 **`AGREE`** 且已被未删除 **`CABINET_PURCHASE_ORDER`**（**`original_bus_no`** 指向本申请）引用的申请，**不得**再出现在可关联列表（**AS-INV-WHS-001** **IF-WHS-PO-12**） | （下游只读；本页 agree 不校验） |

---

## 2. 页面与接口总览（契约概览）

| IF 编号 | 用户动作 | 接口语义（业务） | 读/写 | 关键规则（BR） | 关键状态变化 | 涉及领域 | QueryService |
| --- | --- | --- | --- | --- | --- | --- | --- |
| IF-DEP-CPR-01 | 进入申请页面 | 初始化：**目的科室药房** **OptionDto**（含「全部」）；**SupplyStatus**；物品表类型 | 读 | — | — | Location | — |
| IF-DEP-CPR-02 | 查询/刷新列表 | 分页 **`DEPT_PURCHASE_REQUEST`**；**`bus_no` 合并一行** | 读 | — | — | SupplyRequest | **`SupplyRequestDocumentListQuery`** |
| IF-DEP-CPR-03 | 查看详情 | 按 **`bus_no`** 产品明细；富化品名/规格/厂家；**`unitBands`** 同订货 | 读 | — | — | SupplyRequest / ProductDefinition | **`getSupplyByBusNo`** |
| IF-DEP-CPR-04 | 新建申请单 | 创建 type 31 草稿；**`PENDING_APPROVAL`** | 写 | BR-DEP-CPR-001～003 / **010** | 新 `bus_no` | SupplyRequest / Location | 列表/详情刷新 |
| IF-DEP-CPR-05 | 编辑申请单 | 可编辑态改明细 | 写 | BR-DEP-CPR-005 / **010** | 明细变更 | SupplyRequest | 同上 |
| IF-DEP-CPR-06 | 提交审批 | 待审核 → 审核中 | 写 | BR-DEP-CPR-006 | → `APPROVAL` | SupplyRequest | 待办、列表 |
| IF-DEP-CPR-07 | 撤回审批 | 审核中撤回 | 写 | — | → `WITHDRAW` / `PENDING_APPROVAL` | SupplyRequest | 同上 |
| IF-DEP-CPR-08 | 审批同意 | 仅申请终态 | 写 | BR-DEP-CPR-007 / **009** | **`AGREE`**；**不**建订货、**不**改库存 | SupplyRequest | 列表/详情 |
| IF-DEP-CPR-09 | 审批驳回 | 审批拒绝 | 写 | BR-DEP-CPR-007 | `REJECT` | SupplyRequest | 同上 |
| IF-DEP-CPR-10 | 删除单据 | 逻辑删除 | 写 | BR-DEP-CPR-008 | 逻辑删除 | SupplyRequest | QueryService 隐藏 |
| IF-DEP-CPR-11 | 采购选品检索 | 分页产品定义（与 **IF-WHS-PO-11** 同构；**不**展示批号） | 读 | **BR-DEP-CPR-010** | — | ProductDefinition | 目录 QueryService |

---

## 3. 缺口跟踪（Gap）

| Gap ID | 缺口类型 | 文件 + 章节 | 缺什么 / 不一致点 | 关闭条件 | 影响 IF | 优先级 | 状态 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| — | — | — | **无待跟踪缺口**（领域枚举 **`DEPT_PURCHASE_REQUEST`**、**`createDeptPurchaseRequest`** / **`assertDeptPurchaseRequestLinesRevisable`** 已落地，与 **DM-WKF-006 v1.15** 一致） | — | — | — | — |

> 应用层 AppService 尚未成码**不是** Gap（由 **AS-INV-DEP-007-SDD** 驱动）。

---

## 4. 接口编排说明书（业务步骤）

### IF-DEP-CPR-01 进入科室采购申请页初始化

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 用户进入科室采购申请菜单 |
| **主路径步骤** | 加载管辖收窄后的科室药房 OptionDto（首项「全部」=`0`）；状态下拉；物品表类型 |
| **失败分支** | EX-DEP-CPR-101 |

### IF-DEP-CPR-02 列表查询

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 筛选、翻页、刷新 |
| **主路径步骤** | 按 **`DEPT_PURCHASE_REQUEST`**、**`purposeLocationId`**（`0` 展开可见科室药房）、日期/状态/关键字；**`bus_no` 合并一行** |
| **失败分支** | EX-DEP-CPR-102 |

### IF-DEP-CPR-03 详情

| 维度 | 说明 |
| --- | --- |
| **主路径步骤** | 按 **`bus_no`** 加载头与产品明细；富化 **`unitBands`**；返回 **`rowVersion`** |
| **关键分支** | 已同意：只读审批操作；可只读展示是否已被订货引用 |
| **失败分支** | EX-DEP-CPR-103 |

### IF-DEP-CPR-04 新建

| 维度 | 说明 |
| --- | --- |
| **主路径步骤** | 校验目的科室药房；产品定义级明细；持久化 **`DEPT_PURCHASE_REQUEST`** 草稿 |
| **失败分支** | EX-DEP-CPR-001 |
| **查询侧说明** | **SupplyRequestCreatedEvent** |

### IF-DEP-CPR-05 编辑

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 可编辑态；**`rowVersion`** 一致 |
| **主路径步骤** | 同号全量修订明细 |
| **失败分支** | EX-DEP-CPR-004 / EX-DEP-CPR-105 |

### IF-DEP-CPR-06 / 07 提交 / 撤回审批

与 **AS-INV-WHS-001** 同构；事件 **Submitted** / **Withdrawn**；失败 EX-DEP-CPR-003。

### IF-DEP-CPR-08 审批同意

| 维度 | 说明 |
| --- | --- |
| **主路径步骤** | 置 **`AGREE`**；**不**调用 `createPurchaseOrder` / 库存 |
| **关键分支** | UI 可提示药库订货模块可选拉取 |
| **失败分支** | EX-DEP-CPR-003 |

### IF-DEP-CPR-09 / 10 驳回 / 删除

与订货同构；删除须无阻塞下游订货引用（**BR-DEP-CPR-008**）。

### IF-DEP-CPR-11 采购选品检索

| 维度 | 说明 |
| --- | --- |
| **主路径步骤** | 与 **IF-WHS-PO-11** 同构：产品分页、关键字/分类、参考采购价与 **`unitBands`**；**不**展示批号 |
| **失败分支** | EX-DEP-CPR-102 |

---

## 5. 业务异常与用户提示

| 语义代码 | 场景 | 用户提示方向 | 可重试 |
| --- | --- | --- | --- |
| EX-DEP-CPR-001 | 建单校验失败 | 按提示补全位置/明细 | 是 |
| EX-DEP-CPR-003 | 状态不允许操作 | 刷新后重试 | 是 |
| EX-DEP-CPR-004 | 禁止编辑 | 说明原因 | 否 |
| EX-DEP-CPR-005 | 删除被拒绝 | 下游订货引用或状态原因 | 否 |
| EX-DEP-CPR-101 | 主数据加载失败 | 稍后重试 | 是 |
| EX-DEP-CPR-102 | 列表/选品查询失败 | 缩小条件 | 是 |
| EX-DEP-CPR-103 | 详情不可用 | 返回列表 | 视情况 |
| EX-DEP-CPR-104 | 重复同意 | 刷新后确认状态 | 是 |
| EX-DEP-CPR-105 | 并发编辑冲突 | 刷新后重试 | 是 |

---

## 6. 事件语义与消费方

| 事件（**DM-WKF-006** §5） | 触发相位（IF） | 典型消费方 |
| --- | --- | --- |
| `SupplyRequestCreatedEvent` | IF-DEP-CPR-04 | 列表、待办 |
| `SupplyRequestSubmittedEvent` | IF-DEP-CPR-06 | 待办、列表 |
| `SupplyRequestWithdrawnEvent` | IF-DEP-CPR-07 | 列表 |
| `SupplyRequestAgreedEvent` | IF-DEP-CPR-08 | 列表；**不**触发库存；订货可拉源 |
| `SupplyRequestRejectedEvent` | IF-DEP-CPR-09 | 列表 |
| `SupplyRequestDeletedEvent` | IF-DEP-CPR-10 | QueryService 隐藏 |

---

## 7. 非功能性需求

| 类别 | 要求 |
| --- | --- |
| **性能** | 列表与选品分页可接受 |
| **幂等** | 提交/同意/驳回短时重复为幂等或「已处理」 |
| **并发** | 修订乐观锁（**`rowVersion`**） |
| **审计** | 审批人、审批时间可追溯 |

---

## 8. 测试要点（业务视角）

| 类别 | 要点 |
| --- | --- |
| **正向** | 新建 → 提交 → 同意；药库订货可从本单拉预填；手工订货不依赖本单 |
| **异常** | 非科室药房建单、空明细；终态改单拒绝 |
| **规则** | BR-DEP-CPR-009 同意不动库存；BR-DEP-CPR-011 已被订货占用的申请不出现在拉源列表 |
| **跨场景** | 与 **AS-INV-WHS-001** `original_bus_no` 可追溯 |

---

## 9. 关联文档与变更历史

### 9.1 关联文档

| 文档 |
| --- |
| `DM-WKF-006-SupplyRequest-供应请求.md`（v1.15+） |
| `DM-ADM-003-Location-位置.md` |
| `AS-INV-WHS-001-药库订货单.md`（下游可选拉源） |
| `AS-INV-DEP-007-科室采购申请单-SDD.md` |
| `INV-inventory-功能清单与改造状态.md` |

### 9.2 变更历史

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v0.3 | 2026-07-15 | 物品来源表下拉默认优先 **耗材**（首项） |
| v0.2 | 2026-07-15 | §3 Gap 关闭：领域 **`DEPT_PURCHASE_REQUEST`** / **`createDeptPurchaseRequest`** 已落地 |
| v0.1 | 2026-07-15 | 初稿：科室采购申请申请单；选品同构订货；同意仅终态；下游订货可选拉取 |

---

**文档负责人**：（待填） · **评审**：（待填）
