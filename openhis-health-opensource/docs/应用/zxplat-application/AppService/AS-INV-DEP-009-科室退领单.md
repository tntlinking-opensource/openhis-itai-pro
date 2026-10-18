# 科室药房退领单 · 应用服务设计（AS）

> **页面边界**：科室药房业务线 — **`DEPT_ISSUE_RETURN`**（科室退领单）主动制单。明细**须**从已同意 **`DEPT_ISSUE`**（科室领用单）经 `original_bus_no` 拉取，**禁止自由选品**；拉取后可删行、调小退回数量，**禁止**新增源单不存在的品/批号。
>
> **与领用对偶**：领用 agree **扣减**本库 → 退领 agree **加回**同一 `purpose_location_id`（科室药房）；**单库房**；**不**生成壳单、**不**关联临床发放。
>
> **与科室退库分工**：**`DEPT_RETURN`（AS-INV-DEP-003）** 为科室药房→药库双端点退库；本单为领用未用完**退回科室药房库存**，`original_bus_no` 指向 **`DEPT_ISSUE`**，**非** `DEPT_STOCK_IN`。
>
> **编码**：AS-INV-DEP-009 · **版本**：v0.7 · **日期**：2026-08-31

---

## 1. 场景概述

### 1.1 目标与边界

| 维度 | 说明 |
|------|------|
| **在内** | 从已同意领用单拉取明细建 **`DEPT_ISSUE_RETURN`** 草稿；可编辑（删行/减量）；审批闭环；**同意**后 `createSupplyDelivery` + **`purpose_location_id` 库存增加** |
| **审批** | **药师自审**（`ROLE_PHARMACIST`）；**不须**财务岗位 |
| **库位** | **`purpose_location_id`** 与源领用单一致（科室药房）；`source_*` **NULL**（单库房） |
| **领用科室** | 自源 **`DEPT_ISSUE`** 表头 **`consuming_organization_id`** 带出并落库；单头 **科室药房 / 领用科室只读**；客户端**不可改** |
| **源单** | **`original_bus_no`** 必填，指向 **`AGREE`** 态 **`DEPT_ISSUE`** |
| **在外** | 科室领用（**AS-INV-DEP-004**）；科室退库（**AS-INV-DEP-003**）；临床 `DeviceDispense` |

### 1.2 角色与关键动作

| 角色 | 动作 |
|------|------|
| 科室药房药师 | 选源领用单、拉取明细、编辑退回量、保存 |
| 科室药房药师（审批） | 提交 / 同意 / 驳回 / 撤回 |

### 1.3 术语与口径约定

| 术语 | 口径 |
|------|------|
| **科室退领单** | `type_enum` = **`DEPT_ISSUE_RETURN`**（文档序号 **30**） |
| **`bus_no` 前缀** | **`DTL`** |
| **可退余量行键** | **`item_table + item_id + lot_number`**（**不含** `trace_no`） |
| **退回数量** | **`item_quantity`** = 本次退回量（拆零、正数） |
| **可退余量** | 源领用行数量 − 已同意退领占用 − 在途退领草稿/在审占用（同品批行键聚合） |
| **领用科室** | 继承源领用单 **`consuming_organization_id`**（历史源单列空时回落 **`Location.organization_id`**）；退领单头只读展示 |
| **追溯** | 行键匹配**不看**追溯码；若源领用行 `trace_no` 非空，退回行**须原样带回同一 `trace_no`**（同码） |
| **时效** | **不**限制源领用单审批通过后的可退时间窗 |

### 1.4 本期范围与非目标

| 类别 | 内容 |
|------|------|
| **本期** | 可关联领用单列表、按领用单拉取预填、退领全生命周期、多次退领直至余量耗尽 |
| **非目标** | 自由选品；超源单可退余量；无明细保存；退药库（走 **DEPT_RETURN**）；医保上传；临床链 |

### 1.5 领域清单

| 聚合 | 职责 | DM |
|------|------|-----|
| **SupplyRequest** | 退领建单、审批 | `DM-WKF-006` |
| **SupplyDelivery** | 同意相位退领发放 | `DM-WKF-005` |
| **InventoryItem** | 科室药房库存增加 | `DM-WKF-008` |
| **TraceNumberRecord** | 条件追溯（入库向） | `DM-CUS-010` |

### 1.6 业务规则（BR-*）

| 规则 ID | 描述 | 绑定 IF |
|---------|------|---------|
| **BR-DEP-TL-001** | `type_enum`=`**DEPT_ISSUE_RETURN**`；`original_bus_no` 必填且源为 **`AGREE`** 的 **`DEPT_ISSUE`** | IF-DEP-TL-04/05/12/13 |
| **BR-DEP-TL-002** | `purpose_location_id` 与源领用单一致；`source_*` **NULL**；**`consuming_organization_id`** 自源领用单继承落库，客户端**不可改** | IF-DEP-TL-04/05/13 |
| **BR-DEP-TL-003** | 明细行 ⊆ 源领用单品批（行键）；**禁止**新增源单不存在的品/批号 | IF-DEP-TL-04/05/13 |
| **BR-DEP-TL-004** | 每行 `item_quantity` **> 0**；≤ 该行**可退余量**；**整单至少一行** | IF-DEP-TL-04/05 |
| **BR-DEP-TL-005** | 同一源领用单**允许多张**退领单（直至各行余量耗尽） | IF-DEP-TL-12 |
| **BR-DEP-TL-006** | agree：`agreeRequest` → `createSupplyDelivery` → **仅** `increaseQuantity` @ `purpose_location_id`；**禁止**扣减/调拨/壳单 | IF-DEP-TL-08 |
| **BR-DEP-TL-007** | 审批须药师岗位；不须财务 | IF-DEP-TL-06/08 |
| **BR-DEP-TL-008** | 源行 `trace_no` 非空时退回行 `trace_no` **须与源领用行一致**；同意时写追溯，`operation_type`=`**DEPT_ISSUE_RETURN**` | IF-DEP-TL-04/05/08/13 |
| **BR-DEP-TL-009** | 可编辑态 / 删除门控同 **BR-SR-011** | IF-DEP-TL-05/10 |
| **BR-DEP-TL-010** | 详情槽价 **BR-PRICE-SRC-001**：仅落库 `price` | IF-DEP-TL-03 |

---

## 2. 页面与接口总览（契约概览）

| IF 编号 | 用户动作 | 接口语义 | 读/写 | 关键规则 | 关键状态变化 | 涉及领域 | 查询服务 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| IF-DEP-TL-01 | 进入页面 | init：科室药房、状态、**经手人（全体在职医务人员，含拼音）** | 读 | — | — | Location / Practitioner | `LocationQueryService` / `PractitionerRoleScopeLoader` |
| IF-DEP-TL-02 | 退领列表 | 分页 **`DEPT_ISSUE_RETURN`** | 读 | BR-DEP-TL-001 | — | SupplyRequest | `SupplyRequestQueryService` |
| IF-DEP-TL-03 | 详情 | 批号级明细 + 源领用单号 + **只读**科室药房/领用科室 | 读 | BR-DEP-TL-010 | — | SupplyRequest | 同上 |
| IF-DEP-TL-04 | 新建保存 | **`createDeptIssueReturn`** | 写 | BR-DEP-TL-001～004/008 | → `PENDING_APPROVAL` | SupplyRequest | — |
| IF-DEP-TL-05 | 编辑保存 | 可编辑态全量对齐 | 写 | BR-DEP-TL-003/004/009 | 保持可编辑态 | SupplyRequest | `VerifyAbleEditQuery` |
| IF-DEP-TL-06 | 提交 | 待审核 → 审核中 | 写 | BR-DEP-TL-007/009 | → `APPROVAL` | SupplyRequest | — |
| IF-DEP-TL-07 | 撤回 | 审核中 → 已撤回 | 写 | BR-DEP-TL-009 | → `WITHDRAW` | SupplyRequest | — |
| IF-DEP-TL-08 | 同意 | 退领发放 + 库存加回 +（条件）追溯 | 写 | BR-DEP-TL-006/007/008 | → `AGREE` + 库存+ | SupplyRequest / SupplyDelivery / InventoryItem | `supplyRequestValidation` |
| IF-DEP-TL-09 | 驳回 | → 驳回 | 写 | BR-DEP-TL-009 | → `REJECT` | SupplyRequest | — |
| IF-DEP-TL-10 | 删除 | 逻辑删除 | 写 | BR-DEP-TL-009 | 逻辑删除 | SupplyRequest | — |
| IF-DEP-TL-12 | 选源领用单 | 分页可关联 **`DEPT_ISSUE`**（仍有可退余量） | 读 | BR-DEP-TL-001/005 | — | SupplyRequest | `SupplyRequestQueryService` |
| IF-DEP-TL-13 | 拉取领用明细 | 按领用 `busNo` 预填退领草稿（含可退余量） | 读 | BR-DEP-TL-001～004/008 | — | SupplyRequest | 同上 |

---

## 3. 缺口跟踪（Gap）

**无待跟踪缺口**（领域层 **2026-07-01** 已落地：`DEPT_ISSUE_RETURN` / `createDeptIssueReturn` / agree 表 B type 30 / `DM-CUS-010` 示例；应用层 **`inventoryDepartmentDeptIssueReturn`** **2026-07-01** 已落地）。

| Gap ID | 缺口类型 | 文件 + 章节 | 缺什么 / 不一致点 | 关闭条件 | 影响 IF | 优先级 | 状态 |
|--------|----------|-------------|-------------------|----------|---------|--------|------|
| ~~**GAP-DEP-TL-001**~~ | DM 能力 | `DM-WKF-006` §4.1 | ~~缺 **`DEPT_ISSUE_RETURN`（30）** 与 **`DTL`** 单号前缀~~ | DM §4.1 + `SupplyType` 枚举 | 全部 | 高 | **已关闭** |
| ~~**GAP-DEP-TL-002**~~ | DM 能力 | `DM-WKF-006` §6.1 | ~~缺 **`createDeptIssueReturn`**~~ | DM §6.1.13 + 领域实现 | IF-DEP-TL-04/05 | 高 | **已关闭** |
| ~~**GAP-DEP-TL-003**~~ | DM 能力 | `DM-WKF-006` §6.1.16 表 B | ~~agree 编排缺 **type 30**~~ | DM 跨域表增补 | IF-DEP-TL-08 | 高 | **已关闭** |
| ~~**GAP-DEP-TL-004**~~ | DM 能力 | `DM-CUS-010` §4.4 | ~~追溯示例缺 **`DEPT_ISSUE_RETURN`**~~ | DM 增补 | IF-DEP-TL-08 | 中 | **已关闭** |

---

## 4. 接口编排说明书（业务步骤）

### IF-DEP-TL-12 可关联领用单

| 维度 | 说明 |
|------|------|
| **主路径** | 按科室药房筛选 **`DEPT_ISSUE` + `AGREE`**；按 `bus_no` 聚合；排除各行可退余量均已耗尽的领用单 |
| **失败分支** | `DEP_TL_102` |

### IF-DEP-TL-13 拉取领用明细

| 维度 | 说明 |
|------|------|
| **主路径** | 校验源单类型/状态/库位；按行键计算**可退余量**；返回预填行（含源领用数量、已退占用、建议退回上限、`trace_no` 原样，以及 **规格/厂家/金额/效期/行备注** 供网格回显）；表头 **科室药房 / 领用科室只读**（自源单 **`consuming_organization_id`** 解析） |
| **关键分支** | 可退余量=0 的行可默认不展示或展示为不可选（产品定展示策略；保存时 BR-DEP-TL-004 拒绝超量） |
| **失败分支** | 源单非法 → `DEP_TL_001` |

### IF-DEP-TL-04 / 05 新建与编辑

| 维度 | 说明 |
|------|------|
| **主路径** | `originalBusNo` 必填；校验行 ⊆ 源单、数量≤可退余量、至少一行；写 `DEPT_ISSUE_RETURN` + `purpose_location_id` + **`consuming_organization_id`（自源领用单，非客户端入参）** |
| **失败分支** | 无明细 / 超余量 / 非法品批 → `DEP_TL_001` |

### IF-DEP-TL-08 同意

| 维度 | 说明 |
|------|------|
| **主路径** | 药师门禁 → 防重复同意 → `agreeRequest` → `createSupplyDelivery` → 逐行 **`increaseQuantity`**（必要时 `createInventoryItem` 四元组）→ `trace_no` 非空写**入库向**追溯 |
| **失败分支** | `DEP_TL_104` / `DEP_TL_105` |

其余 IF（01/02/03/06/07/09/10）与 **AS-INV-DEP-004** 审批链同构，差异仅为类型与列表锚点。

---

## 5. 业务异常与用户提示

| 语义代码 | 场景 | 可重试 |
| --- | --- | --- |
| DEP_TL_001 | 参数/源单/明细/可退余量校验失败 | 修正后重试 |
| DEP_TL_002 | 科室药房越权或非法 | 换库位 |
| DEP_TL_003 | 状态不允许提交/撤回/驳回 | 刷新 |
| DEP_TL_004 | 禁止编辑 | — |
| DEP_TL_005 | 删除拒绝 | — |
| DEP_TL_101 | init 失败 | 可重试 |
| DEP_TL_102 | 列表/可关联领用查询失败 | 可重试 |
| DEP_TL_103 | 单据不存在或类型不匹配 | — |
| DEP_TL_104 | 重复同意或发放/库存编排失败 | 刷新 |
| DEP_TL_105 | 并发冲突 | 刷新后重试 |

---

## 6. 事件语义与消费方

与 **AS-INV-DEP-004** 相同（`SupplyRequest*Event` 族），`typeEnum`=`DEPT_ISSUE_RETURN`。

---

## 7. 非功能性需求

| 项 | 承诺 |
|----|------|
| **幂等** | 同领用单：双重 agree 门禁 + 发放完成态检测 |
| **并发** | 多退领单并行占用同一源行键时，保存/同意须串行化可退余量校验 |
| **一致性** | agree 单事务：状态 + 发放 + 库存 + 追溯 |

---

## 8. 测试要点（业务视角）

| 场景 | 要点 |
|------|------|
| 拉取 | 仅从 AGREE 领用单拉取；禁止自由加行；表头科室药房/领用科室只读 |
| 余量 | 部分退、多次退直至耗尽；超余量拒绝 |
| 空明细 | 零行不可保存 |
| 追溯 | 源行有码须同码退回；行键匹配不看追溯 |
| 库存 | agree 后科室药房库存增加；无壳单 |
| 分工 | 与 DEPT_RETURN 源单类型不混用 |

---

## 9. 关联文档与变更历史

| 文档 |
| --- |
| `AS-INV-DEP-004-科室领用单.md` |
| `AS-INV-PHA-003-药房退库单.md`（拉取/可退余量对偶） |
| `DM-WKF-006` / `DM-WKF-005` / `DM-WKF-008` / `DM-CUS-010` |
| `AS-INV-DEP-009-科室退领单-SDD.md` |

| 版本 | 日期 | 摘要 |
| --- | --- | --- |
| v0.7 | 2026-08-31 | **IF-DEP-TL-01**：经手人下拉改为全体在职医务人员（含拼音）；**审批仍须药师岗** |
| v0.6 | 2026-07-26 | **领用科室**自源领用单 **`consuming_organization_id`** 带出落库；单头科室药房/领用科室只读；数量可调；对齐 **AS-INV-DEP-004 v0.6** / **DM-WKF-006 v1.25** |
| v0.5 | 2026-07-26 | 列表聚合返回整单金额；效期回显：源领用→库存兜底 |
| v0.4 | 2026-07-26 | 明细列对齐请领：拉取/详情/写行增厂家、金额、效期、行备注；领域落 `end_time`/`remake` |
| v0.3 | 2026-07-01 | 应用层 **`inventoryDepartmentDeptIssueReturn`** 落地；规范刷一遍与 SDD 对齐 |
| v0.2 | 2026-07-01 | 领域层落地；§3 Gap 全部关闭；应用层待建 |
| v0.1 | 2026-07-01 | 初稿：从领用单拉取、可退余量、单库加回；§3 领域 Gap 待关闭 |

---

**文档负责人**：（待填） · **评审**：（待填）
