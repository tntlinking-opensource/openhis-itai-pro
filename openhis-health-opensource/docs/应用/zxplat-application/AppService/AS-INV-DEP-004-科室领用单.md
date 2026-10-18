# 科室药房领用单 · 应用服务设计（AS）

> **页面边界**：科室药房业务线 — **`DEPT_ISSUE`** 主动制单；库位须为 **`LocationForm#DEPARTMENT_PHARMACY`**（`purpose_location_id`，扣/加库存真源）。**领用科室**为行政 **Organization**，客户端从全科室下拉手选，落库 **`consuming_organization_id`**（与科室药房解耦）；**禁止**往 `purpose_*` 存 orgId。**同意即扣本库库存**，单库房操作。
>
> **与临床发放无关**：**不**关联 `MedicationDispense` / `DeviceDispense`；**无**医嘱、患者、收费、待发占用。
>
> **与调出分工**：**不**走双端点调拨；**不**生成调入壳单；选品/明细编辑与 **AS-INV-DEP-008 科室调出单** 同构，落库与 agree 编排为单端点出库扣减。
>
> **编码**：AS-INV-DEP-004 · **版本**：v0.6 · **日期**：2026-07-26

---

## 1. 场景概述

### 1.1 目标与边界

| 维度 | 说明 |
|------|------|
| **在内** | 创建 **`DEPT_ISSUE`** 草稿；维护批号级明细；提交/撤回/同意/驳回/删除；**同意**后 `createSupplyDelivery` + **`purpose_location_id` 库存扣减** |
| **审批** | **药师自审**（`ROLE_PHARMACIST`）；**不须**财务岗位（**不在** `SupplyType#requiresFinanceApproval` 白名单） |
| **库位** | **`purpose_location_id`** 唯一真源；形态 **`DEPARTMENT_PHARMACY`**；`source_*` **NULL**（DM §3.1.1 单库房） |
| **领用科室** | 行政 **Organization**；客户端从 init **`organizationOptions`**（全启用科室）手选；建单/改单 **必填** **`consumingOrganizationId`** 落库 **`consuming_organization_id`**；与 **`purpose_location_id`（科室药房）** 解耦 |
| **选品** | 先选 **药品 / 耗材**（`item_table`），再按科室药房库存批号选品；与科室调出选品同构 |
| **在外** | 科室请领（**AS-INV-DEP-001**）；科室调出（**AS-INV-DEP-008**）；科室退领（**AS-INV-DEP-009**）；临床发药/发耗材 |

### 1.2 角色与关键动作

| 角色 | 动作 |
|------|------|
| 科室药房药师 | 新建/编辑领用单、选品、提交 |
| 科室药房药师（审批） | 同意/驳回/撤回（同岗自审） |

### 1.3 术语与口径约定

| 术语 | 口径 |
|------|------|
| **科室领用单** | `type_enum` = **`DEPT_ISSUE`**（文档序号 **29**） |
| **`bus_no` 前缀** | **`DEI`**（`SupplyType.busNoSeqPrefixForTypeCode`） |
| **科室药房** | `LocationForm#DEPARTMENT_PHARMACY`；单头 **`purposeLocationId`** / 落库 **`purpose_location_id`**（库存扣减真源） |
| **领用科室** | 行政 **Organization**；落库 **`consuming_organization_id`**；**非**库位、**非** `purpose_*` |
| **历史回落** | 列值为空时，读侧可回落 **`Location.organization_id`**（科室药房上级科室）展示 |
| **单号字段** | 列表/筛选锚点 **`purposeLocationId`**；`purposeLocationId=0` → **`InventoryDepartmentConstants.ALL_DEPARTMENT_PHARMACIES_LIST_FILTER_LOCATION_ID`** |
| **行数量** | **`item_quantity`** = 领用数量（拆零、正数）；保存前须 ≤ 可用量 |
| **枚举** | `type_enum` / `status_enum` 一律 **`SupplyType` / `SupplyStatus#getCode()`** 字符串 |

### 1.4 本期范围与非目标

| 类别 | 内容 |
|------|------|
| **本期** | 手工领用单全生命周期；药品+耗材选品；同意落账扣库；可选追溯码（`trace_no` 非空行） |
| **非目标** | 关联请领/出库/调拨 `original_bus_no`；医保上传（本期默认关闭）；护士站/医生站联动；**退领**见 **AS-INV-DEP-009** |

### 1.5 领域清单

| 聚合 | 职责 | DM |
|------|------|-----|
| **SupplyRequest** | 领用单建单、审批状态机 | `DM-WKF-006` |
| **SupplyDelivery** | 同意相位出库发放事实 | `DM-WKF-005` |
| **InventoryItem** | 科室药房库存扣减 | `DM-WKF-008` |
| **Location** | 科室药房与上级科室读模型 | `DM-ADM-00x` Location |
| **TraceNumberRecord** | 条件追溯轨迹 | `DM-CUS-010` |

### 1.6 业务规则（BR-*）

| 规则 ID | 描述 | 绑定 IF |
|---------|------|---------|
| **BR-DEP-IS-001** | `type_enum` 须为 **`DEPT_ISSUE`**；`purpose_location_id` 须启用中 **`DEPARTMENT_PHARMACY`**；`source_*` 须 **NULL** | IF-DEP-IS-04/05 |
| **BR-DEP-IS-002** | 建单/改单 **`consumingOrganizationId` 必填**且须为启用行政科室；落库 **`consuming_organization_id`**；**禁止**客户端改写 `purpose_*` 为 orgId；读侧 enrich 展示名 | IF-DEP-IS-01/03/04/05 |
| **BR-DEP-IS-003** | 明细 `item_quantity` **> 0**；草稿保存前每行 ≤ 源库可用量（与选品可用量口径一致） | IF-DEP-IS-04/05/11 |
| **BR-DEP-IS-004** | `item_table` 仅 **`MED_MEDICATION_DEFINITION`** / **`ADM_DEVICE_DEFINITION`** | IF-DEP-IS-11 |
| **BR-DEP-IS-005** | **同意**为唯一落账点：`agreeRequest` → `createSupplyDelivery` → **`decreaseQuantity`**（仅 `purpose_location_id`）；**禁止** `increaseQuantity`、**禁止** `createTransfer` / 调入壳单 | IF-DEP-IS-08 |
| **BR-DEP-IS-006** | 审批须 **药师岗位**；**不须**财务岗位 | IF-DEP-IS-06/08 |
| **BR-DEP-IS-007** | **禁止** `original_bus_no` 关联请领/出库/调拨 | IF-DEP-IS-04/05 |
| **BR-DEP-IS-008** | `trace_no` 非空行须写 **`cus_trace_number` + `cus_trace_number_flow`**，`operation_type` = **`DEPT_ISSUE`** | IF-DEP-IS-08 |
| **BR-DEP-IS-009** | 可编辑态仅 **`PENDING_APPROVAL` / `WITHDRAW` / `REJECT`**；删除同 **BR-SR-011** | IF-DEP-IS-05/10 |
| **BR-DEP-IS-010** | 详情历史单价遵循 **BR-PRICE-SRC-001**：仅展示落库 `price`，禁止当前目录价覆盖 | IF-DEP-IS-03 |

---

## 2. 页面与接口总览（契约概览）

| IF 编号 | 用户动作 | 接口语义 | 读/写 | 关键规则 | 关键状态变化 | 涉及领域 | 查询服务 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| IF-DEP-IS-01 | 进入页面 | init：科室药房下拉、**全启用行政科室 `organizationOptions`**、项目类型、SupplyStatus、**经手人（全体在职医务人员，含拼音）** | 读 | BR-DEP-IS-002 | — | Location / Organization / Practitioner | `LocationQueryService` / `OrganizationQueryService` / `PractitionerRoleScopeLoader` |
| IF-DEP-IS-02 | 列表查询 | 分页 **`DEPT_ISSUE`**，按 **`purposeLocationId`** / 状态 / 时间筛选 | 读 | BR-DEP-IS-001 | — | SupplyRequest | `SupplyRequestQueryService` |
| IF-DEP-IS-03 | 打开详情 | 批号级明细 + **领用科室**（优先落库 id，历史回落）+ 科室药房名 + 历史槽价 | 读 | BR-DEP-IS-002/010 | — | SupplyRequest / Organization | 同上 |
| IF-DEP-IS-04 | 新建 | **`createDeptIssue`** 草稿（**必填 `consumingOrganizationId`**） | 写 | BR-DEP-IS-001～004/007 | → `PENDING_APPROVAL` | SupplyRequest | — |
| IF-DEP-IS-05 | 编辑保存 | 可编辑态全量对齐改单（**必填 `consumingOrganizationId`**） | 写 | BR-DEP-IS-003/009 | 保持可编辑态 | SupplyRequest | `VerifyAbleEditQuery` |
| IF-DEP-IS-06 | 提交审批 | 待审核 → 审核中 | 写 | BR-DEP-IS-006/009 | → `APPROVAL` | SupplyRequest | — |
| IF-DEP-IS-07 | 撤回审批 | 审核中 → 已撤回 | 写 | BR-DEP-IS-009 | → `WITHDRAW` | SupplyRequest | — |
| IF-DEP-IS-08 | 同意 | 出库发放 + 科室药房扣减 +（条件）追溯 | 写 | BR-DEP-IS-005/006/008 | → `AGREE` + 库存− | SupplyRequest / SupplyDelivery / InventoryItem | `supplyRequestValidation` |
| IF-DEP-IS-09 | 驳回 | 审核中 → 驳回 | 写 | BR-DEP-IS-009 | → `REJECT` | SupplyRequest | — |
| IF-DEP-IS-10 | 删除 | 逻辑删除 | 写 | BR-DEP-IS-009 | 逻辑删除 | SupplyRequest | — |
| IF-DEP-IS-11 | 选品弹窗 | 按科室药房库存**批号级**分页选品（药品/耗材）；**选品 UI 须展示 `lotNumber`（批号）**（**AS-INV-WHS-003 §1.3**） | 读 | BR-DEP-IS-003/004 | — | InventoryItem | `InventoryItemQueryService` |

---

## 3. 缺口跟踪（Gap）

**无待跟踪缺口**（2026-07-01：领域层 **`DEPT_ISSUE`（29）**、`createDeptIssue`、`assertDeptIssueLinesRevisable` 已落地；应用层 **`inventoryDepartmentDeptIssue`**（`department/deptissue`）已按 AS-SDD 成码）。

---

## 4. 接口编排说明书（业务步骤）

### IF-DEP-IS-01 页初始化

| 维度 | 说明 |
|------|------|
| **触发前提** | 用户进入科室领用单页；会话机构有效 |
| **主路径** | 加载管辖范围内启用 **科室药房** 下拉；加载 **全启用行政科室** **`organizationOptions`**（`OrganizationType#DEPARTMENT`）；加载药品/耗材 **项目类型**、单据状态字典；加载 **经手人** 下拉为**全体在职医务人员**（**`listAllActivePractitionersForInventoryDocHandlerSelect`**，**OptionDto** 含 **`pyStr`/`wbStr`**；响应字段名仍为 **`pharmacistOptions`**）。**审批岗位门禁仍为药师**（**BR-DEP-IS-006**），与经手人下拉口径分离 |
| **关键分支** | 领用科室与科室药房独立选择；**不再**要求科室药房配置 `organization_id` 方可建单 |
| **失败分支** | 机构上下文缺失 → `DEP_IS_101` |

### IF-DEP-IS-02 列表

| 维度 | 说明 |
|------|------|
| **触发前提** | `purposeLocationId` 必填（`0` 表示全部可见科室药房） |
| **主路径** | 按 `DEPT_ISSUE` + 库位 + 状态 + 申请时间窗分页；`bus_no` 合并一行；行内展示 **领用科室名**（优先 **`consuming_organization_id`**，历史回落）、科室药房名、状态、制单/审批信息 |
| **失败分支** | 分页非法 / 越权库位 → `DEP_IS_001` / `DEP_IS_002` |

### IF-DEP-IS-03 详情

| 维度 | 说明 |
|------|------|
| **主路径** | 按 `busNo` 加载明细；校验类型；enrich 产品展示（规格/厂家）、领用科室、科室药房名；单价仅落库值；回显 **`endTime`/`remake`**（有效期/行备注） |
| **失败分支** | 不存在或类型不符 → `DEP_IS_103` |

### IF-DEP-IS-04 新建

| 维度 | 说明 |
|------|------|
| **主路径** | 校验库位、**`consumingOrganizationId`** 与明细；固定 `DEPT_ISSUE`；写 `purpose_location_id` + **`consuming_organization_id`**；`source_*` 空；生成 `DEI` 前缀 `bus_no`；初态 `PENDING_APPROVAL` |
| **失败分支** | 明细为空 / 超可用量 / **`consumingOrganizationId` 缺失或非法** → `DEP_IS_001` |

### IF-DEP-IS-05 编辑

| 维度 | 说明 |
|------|------|
| **主路径** | 可编辑态门禁通过后，同号全量对齐修订（与科室调出改单策略一致） |
| **失败分支** | 不可编辑 → `DEP_IS_004` |

### IF-DEP-IS-06～07 / 09 审批流转

| 维度 | 说明 |
|------|------|
| **主路径** | 标准 SupplyRequest 提交 / 撤回 / 驳回状态机 |
| **失败分支** | 状态不允许 → `DEP_IS_003` |

### IF-DEP-IS-08 同意

| 维度 | 说明 |
|------|------|
| **主路径** | 药师岗位门禁 → 防重复同意 → `agreeRequest` → 回读 AGREE → `createSupplyDelivery` → 逐行 **`decreaseQuantity`** @ `purpose_location_id` → `trace_no` 非空行写追溯 |
| **关键分支** | 库存不足 → 整单失败回滚；已有完成态发放 → 幂等拒绝 |
| **失败分支** | `DEP_IS_104` / `DEP_IS_105` |

### IF-DEP-IS-10 删除

| 维度 | 说明 |
|------|------|
| **主路径** | 仅可删态 `removeByBusNo` |
| **失败分支** | `DEP_IS_005` |

### IF-DEP-IS-11 选品

| 维度 | 说明 |
|------|------|
| **主路径** | 按 `purposeLocationId` + `productTableName` 分页库存；可选 **`positiveQuantityOnly`**（非必传；**`true`**=账面量>0）；展示可用量（与保存校验同口径）、批号、槽价 |
| **失败分支** | 库位非法 → `DEP_IS_002`；查询失败 → `DEP_IS_102` |

---

## 5. 业务异常与用户提示

| 语义代码 | 场景 | 可重试 |
| --- | --- | --- |
| DEP_IS_001 | 参数/明细校验失败 | 修正后重试 |
| DEP_IS_002 | 科室药房位置非法或越权 | 修正主数据或选库位 |
| DEP_IS_003 | 状态不允许提交/撤回/驳回 | 刷新后按态操作 |
| DEP_IS_004 | 禁止编辑 | — |
| DEP_IS_005 | 删除拒绝 | — |
| DEP_IS_101 | init 失败 | 可重试 |
| DEP_IS_102 | 列表/选品查询失败 | 可重试 |
| DEP_IS_103 | 单据不存在或类型不匹配 | — |
| DEP_IS_104 | 重复同意或发放/库存编排失败 | 刷新；库存不足须减数量 |
| DEP_IS_105 | 并发冲突 | 刷新后重试 |

---

## 6. 事件语义与消费方

| 事件 | 触发点 | 消费方 | 幂等 |
|------|--------|--------|------|
| `SupplyRequestCreatedEvent` | 新建 | 列表投影（若有） | bus_no |
| `SupplyRequestSubmittedEvent` | 提交 | — | bus_no |
| `SupplyRequestAgreedEvent` | 同意 | 库存读侧刷新 | bus_no + 发放幂等门禁 |
| `SupplyRequestRejectedEvent` / `SupplyRequestWithdrawnEvent` | 驳回/撤回 | — | bus_no |
| `SupplyRequestDeletedEvent` | 删除 | — | bus_no |

---

## 7. 非功能性需求

| 项 | 承诺 |
|----|------|
| **并发** | agree 与改单使用乐观锁；冲突提示 `DEP_IS_105` |
| **幂等** | 重复 agree 须 `supplyRequestValidation` + 发放完成态双重门禁 |
| **权限** | 科室药房库位管辖 Gate；药师岗位审批 |
| **审计** | 保留 `applicant_id` / `approver_id` / `apply_time` / `approval_time` |
| **一致性** | agree 单事务：状态 + 发放 + 库存 + 追溯 |

---

## 8. 测试要点（业务视角）

| 场景 | 要点 |
|------|------|
| 正向 | 选手科室药房 + 手选领用科室 → 保存 → 提交 → 同意 → 库存减少、领用科室展示与落库一致 |
| 单库房 | 同意前后 `source_location_id` 均为空；无调入壳单 |
| 边界 | **`consumingOrganizationId` 缺失拒绝建单**；领用科室可与科室药房上级科室不一致 |
| 历史 | 列空历史单读侧回落 `Location.organization_id` 展示 |
| 可用量 | 保存数量 > 可用量拒绝 |
| 审批 | 非药师不可同意；财务岗**非必须** |
| 隔离 | 与请领/调出/发药链无 `original_bus_no` 交叉 |
| 追溯 | `trace_no` 行写轨迹；空白行跳过 |
| 并发 | 双同意 / 编辑冲突 |

---

## 9. 关联文档与变更历史

| 文档 |
| --- |
| `DM-WKF-006-SupplyRequest-供应请求.md` |
| `DM-WKF-005-SupplyDelivery-供应发放.md` |
| `DM-WKF-008-InventoryItem-库存项.md` |
| `DM-CUS-010-TraceNumberRecord-追溯码记录.md` |
| `AS-INV-DEP-008-科室调出单.md`（选品同构参考） |
| `AS-INV-DEP-005-科室损益单.md`（单库位 agree 参考） |
| `AS-INV-DEP-004-科室领用单-SDD.md` |
| `AS-INV-DEP-009-科室退领单.md` |

| 版本 | 日期 | 摘要 |
| --- | --- | --- |
| v0.7 | 2026-08-31 | **IF-DEP-IS-01**：经手人下拉改为全体在职医务人员（含拼音）；**审批仍须药师岗**（与下拉分离） |
| v0.6 | 2026-07-26 | **领用科室与科室药房解耦**：落库 **`consuming_organization_id`**（health-final V111）；init 增 **`organizationOptions`**；建改单必填 **`consumingOrganizationId`**；废除由药房 `organization_id` 推导且不落表之旧口径 |
| v0.5 | 2026-07-26 | 列表聚合返回整单金额（侧栏总金额） |
| v0.4 | 2026-07-26 | 明细网格对齐请领列族：规格/厂家/金额/有效期/备注；写行落库 **`end_time`/`remake`**；详情回显 |
| v0.3 | 2026-07-15 | **IF-DEP-IS-11**：请求增可选 **`positiveQuantityOnly`**（非必传） |
| v0.2 | 2026-07-01 | 应用层 `department/deptissue` 成码完成；§3 Gap 关闭 |
| v0.1 | 2026-07-01 | 初稿；领域 Gap 已关闭（DM **v1.13** + `createDeptIssue`） |

---

**文档负责人**：（待填） · **评审**：（待填）
