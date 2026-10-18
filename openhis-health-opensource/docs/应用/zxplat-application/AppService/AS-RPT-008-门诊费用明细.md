# 门诊费用明细 · 应用服务设计（AS）

> **文档职责**：报表中心「门诊费用明细」只读契约。**承接 AS-OP-FIN-005**：保留就诊块明细 + 财务金额列；**汇总 Tab**支持多维净额（费用类型 / 开立科室 / 费用大类 / 品规）。
>
> **编码**：AS-RPT-008 · **版本**：v1.17 · **日期**：2026-10-06  
> **族边界**：`health/设计决策/DD-报表中心-发药耗材挂号费用汇总明细族.md`  
> **进售价真源**：`health/设计决策/DD-报表中心-进售价口径与开立科室-2026-08.md`  
> **前身**：`AS-OP-FIN-005-门诊费用明细.md`（迁移期内双文档并存；以本文为报表中心目标态权威）

---

## 1. 场景概述

### 1.1 目标与边界

| 维度 | 说明 |
| --- | --- |
| **在内** | IF-RPT-OFD-01 初始化（含 `summaryDimensionOptions`）；IF-RPT-OFD-02 **多维净汇总**；IF-RPT-OFD-03 **就诊块明细分页**；IF-RPT-OFD-04 导出当前 Tab；IF-RPT-OFD-05 **明细分页进价列角**（异步） |
| **在外** | 收费/退费/日结写路径；住院费用明细（**AS-RPT-009**）；医生工作量（**AS-RPT-001**） |
| **涉及域** | **ChargeItem**（行额）；**PaymentReconciliation**（`bill_date`/收退 Tab，与日结同轴）；**Encounter** / **Patient**；**ProductDefinition**；**Practitioner**；**ChargeItemDefinition**（fin_type） |
| **编排权威** | 明细+汇总同 Payment 日窗净口径；汇总维 `FIN_TYPE`（默认）/ `DEPARTMENT` / `FEE_CATEGORY` / `ITEM` |
| **数据锚点** | Payment **`bill_date`**；ChargeItem **`SETTLED`∪`REFUNDING`∪`REFUNDED`**；退费 payment 负号冲减（BR-OP-FDT-012）；`REFUNDING` 计已收直至退费 Payment 冲减（与日结 static 对账） |

### 1.2 角色与关键动作

| 角色 | 关键动作 |
| --- | --- |
| **收费 / 财务 / 管理** | 汇总看各费用类型净多少钱 → 明细按就诊块看已结账单 → 导出 |

### 1.3 术语与口径约定

| 术语 | 口径 |
| --- | --- |
| **明细** | 按就诊块；金额列 **进价单价/总价、售价单价/总价**；**已配置有效折扣**（`discountFeatureEnabled`，ACTIVE 配置&gt;0）时另展示 **优惠金额 / 实收金额**（优惠=`|total_price|−|discount_amount|`，实收=折后应收；**BR-RPT-OFD-008** / **BR-OP-FDT-018**）；售价=`ChargeItem` 账单；药耗进价=批号 `LOT_NUMBER_COST` 同批号回放+包装锚行合计；**诊疗/挂号无进价** |
| **汇总** | 按所选汇总维：**数量**、**进价合计/售价合计**；**进价单价/售价单价仅 `ITEM`（品规）维展示**（批号/账单同源）；其它维不展示单价；**不展示行数** |
| **筛选** | 时间窗（Payment `bill_date`；前端 `datetimerange`）、**开立科室**（`ChargeItem.requesting_org_id`）、大类/细分类、患者、项目、收费人、费用性质、收退 Tab |
| **读价真源** | `health/设计决策/DD-报表中心-进售价口径与开立科室-2026-08.md`（覆盖旧「诊疗进价=账单价 / 实收列 / 挂号科室筛」） |
| **导出** | 仅当前 Tab；列集与当前 Tab 一致 |

> **DOC-CONFLICT→已按对账收敛（2026-08-17）**：相对曾写「ChargeItem-only / 禁止扫 Payment / 仅 SETTLED / 退费不入」——因门诊退费写路径会覆盖 `entered_date` 并把行改为 `REFUNDED`，与日结（Payment 快照）按日对账必须恢复 Payment `bill_date` 收退链路；进售价仍跟 ChargeItem 账单，医保分摊汇总可 `skipYbFundAmountAttach`。

### 1.4 本期范围与非目标

| 类别 | 内容 |
| --- | --- |
| **本期** | IF-RPT-OFD-01～04；菜单挂报表中心 |
| **非目标** | 跳转收费；未结算为主入口；住院 |

### 1.5 领域清单（聚合 + 职责 + DM 路径）

同 **AS-OP-FIN-005 §1.5**，并强调 **ChargeItemDefinition.fin_type** 供汇总。

### 1.6 业务规则（BR-*）

| 规则 ID | 描述 | 绑定 IF |
| --- | --- | --- |
| **BR-RPT-OFD-001** | 明细规则继承 **AS-OP-FIN-005 BR-OP-FDT-***（状态、payment、金额列、就诊块分页） | 03、04 |
| **BR-RPT-OFD-002** | 汇总按所选维净金额（默认 `FIN_TYPE`；另支持 `DEPARTMENT` / `FEE_CATEGORY` / `ITEM`）；退费冲减 | 02、04 |
| **BR-RPT-OFD-003** | 汇总与明细同筛选日期/科室等（收退 Tab 对汇总=净视角，明细仍可切 CHARGE/REFUND/ALL） | 02、03 |
| **BR-RPT-OFD-004** | 导出仅当前 Tab | 04 |
| **BR-RPT-OFD-005** | 时间窗必填且 `dateFrom≤dateTo`；前端 `datetimerange`；Request 仍为 `String dateFrom/dateTo`（`yyyy-MM-dd HH:mm:ss`，兼容仅日期）；仅日期时起补 `00:00:00`、止补 `23:59:59`；默认当日 `00:00:00～23:59:59` | 01～05 |
| **BR-RPT-OFD-007** | 进价列角：明细 Tab=Type A（IF-05 与 page 同筛选）；汇总 Tab=Type B（页脚=当次汇总行进价加总）；口径见 DD §2.4 / BR-RPT-PUR-CORNER-* | 02、03、05 |
| **BR-RPT-OFD-008** | 已配置 ACTIVE `cus_charge_discount_config` 时展示折扣相关列（配置本身即开关）。IF-01 下发 `discountFeatureEnabled`；true 时明细 Tab / 导出可见列含 **优惠金额、实收金额**（口径 **AS-OP-FIN-005 §1.3.1 / BR-OP-FDT-018**）；false 时不展示。汇总 Tab 仍只进/售价 | 01、03、04 |

---

## 2. 页面与接口总览（契约概览）

| IF 编号 | 用户动作 | 接口语义 | 读/写 | 关键规则 | 关键状态变化 | 涉及领域 | 查询服务 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| IF-RPT-OFD-01 | 进入页 | 默认时间窗、大类细分类、科室、收费人、Tab、`discountFeatureEnabled` | 读 | BR-RPT-OFD-005、008 | 无 | 主数据 / 折扣配置 | 同 OP-FIN-005 init + `ChargeDiscountApplySupport` |
| IF-RPT-OFD-02 | 汇总 | 多维净金额表 + 页脚（`summaryDimension`）；进价页脚=行加总（Type B） | 读 | BR-RPT-OFD-002～003、005、007 | 无 | ChargeItem / Payment / Definition | `summarizeOutpatientFeeDetailByFinType` + 应用层多维装配（已落地） |
| IF-RPT-OFD-03 | 明细 | 就诊块分页 + summary（售价列角；进价可空）+ `discountFeatureEnabled`（与 init 同真源） | 读 | BR-RPT-OFD-001、003、005、007、008 | 无 | 同 OP-FIN-005 | `pageOutpatientFeeDetailEncounterBlocks` |
| IF-RPT-OFD-04 | 导出 | 当前 Tab | 读 | BR-RPT-OFD-004、008 | 无 | 同上 | 同当前 Tab |
| IF-RPT-OFD-05 | 明细进价列角 | 与 IF-03 **同筛选**回填全命中 scope 进价合计（Type A） | 读 | BR-RPT-OFD-007、005 | 无 | ChargeItem / 调价 / 批号进价 | `FeeDetailMatchedScopePurchaseTotalSupport` |

---

## 3. 缺口跟踪（Gap）

| Gap ID | 缺口类型 | 文件 + 章节 | 缺什么 / 不一致点 | 关闭条件 | 影响 IF | 优先级 | 状态 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| GAP-RPT-OFD-001 | QueryService 能力 | `DM-ADM-011` §6.2.32 | 缺门诊费用按 fin_type **净汇总** | DM 补齐 + 实现 | 02、04 | P0 | **已关闭** |

### 3.1 Gap 关闭落点（2026-07-21）

| Gap ID | DM 章节 | Java 落点 |
| --- | --- | --- |
| GAP-RPT-OFD-001 | `DM-ADM-011` §6.2.32 | `ChargeItemQueryService#summarizeOutpatientFeeDetailByFinType` |

明细能力已关闭于 **AS-OP-FIN-005** Gap，**不算**新 Gap。

---

## 4. 接口编排说明书（业务步骤）

### IF-RPT-OFD-01 页面初始化

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 进入报表中心门诊费用明细 |
| **主路径步骤** | 同 AS-OP-FIN-005 IF-OP-FDT-01，并下发汇总/明细 Tab 默认；Asm `discountFeatureEnabled`（**BR-RPT-OFD-008**） |
| **关键分支** | — |
| **失败分支** | EX-RPT-OFD-101 |
| **查询侧说明** | 只读 |

### IF-RPT-OFD-02 多维净汇总

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 汇总 Tab |
| **主路径步骤** | 校验筛选与 `summaryDimension` → 按维聚合净进价/净售价（列集 SDD；默认 FIN_TYPE）→ 页脚 → 返回 |
| **关键分支** | 无命中全 0 |
| **失败分支** | EX-RPT-OFD-102/103 |
| **查询侧说明** | 与明细同日期/科室等条件下推 |

### IF-RPT-OFD-03 就诊块明细分页

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 明细 Tab |
| **主路径步骤** | 同 **AS-OP-FIN-005 IF-OP-FDT-02**；Asm 回写 `discountFeatureEnabled`（**BR-RPT-OFD-008**，与 IF-01 同真源） |
| **关键分支** | 收退 Tab |
| **失败分支** | 同前身 |
| **查询侧说明** | 复用既有就诊块分页 |

### IF-RPT-OFD-04 导出

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 导出 |
| **主路径步骤** | 汇总全量或明细全量（同前身导出列） |
| **关键分支** | 空表 |
| **失败分支** | EX-RPT-OFD-104 |
| **查询侧说明** | 行上限 |

---

## 5. 业务异常与用户提示

| 语义代码 | 场景 | 用户提示方向 | 可重试 |
| --- | --- | --- | --- |
| EX-RPT-OFD-101 | 初始化失败 | 页面配置加载失败 | 是 |
| EX-RPT-OFD-102 | 日期非法 | 请选择有效查询日期 | 是 |
| EX-RPT-OFD-103 | 查询失败 | 查询失败 | 是 |
| EX-RPT-OFD-104 | 导出失败 | 导出失败 | 是 |

（明细数据不完整等可继续使用前身 EX-OP-FDT-* 语义，SDD 映射。）

---

## 6. 事件语义与消费方

**本期无领域写操作。**

---

## 7. 非功能性需求

同 **AS-OP-FIN-005 §7**；汇总查询同级性能。

**一个月正常窗明细分页（强制）**：

- Support 先解析就诊白名单（日窗 distinct `encounter_id` 仅选 Long → AMB + 患者/挂号科室等；行级硬筛用 ChargeItem **轻量投影**，**禁止**为第 1 页预载满月 `ChargeItem` 实体）。
- 域层 **DB** `GROUP BY encounter_id ORDER BY MAX(entered_date) DESC` 分页（金标准对齐门诊收银队列 `pageEncounterIdsForOutpatientChargeQueue`），再 **仅对本页 encounter IN** 装实体并组块。
- 页脚售价：与分页同谓词的 SQL `SUM(total_price)`（或硬筛投影代数和）；**禁止**为页脚满月实体 / `listOutpatientFeeDetailChargeRows`。
- **汇总 Tab / 导出**可继续走全日窗候选路径（与 page 分叉），不得拖慢明细分页。

**进价列角（DD §2.4）**：

- **明细 Tab（Type A）**：IF-03 仅返回售价列角；进价合计由 **IF-RPT-OFD-05** 异步回填。Request 与 page **同筛选**（分页忽略）；管线 `FeeDetailMatchedScopePurchaseTotalSupport`（全命中 scope 批量历史进价；禁 RowQueryModel）。**禁止**用本页行加总冒充全窗列角。
- **汇总 Tab（Type B）**：页脚进价 = 当次 IF-02 汇总行 `purchaseAmountTotal` 代数和（前端 `sumReportPurchaseCornerFromRows` 纠偏）；与表内进价同筛选同管线。

---

## 8. 测试要点（业务视角）

| 类别 | 要点 |
| --- | --- |
| **明细** | 回归 OP-FIN-005 用例 |
| **汇总** | 类型净额 = 明细同筛选代数和 |
| **退费** | 冲减汇总；跨日退费后收费日/退费日与日结耗材（fin_type）可对平 |
| **对账** | 同日「日结耗材收入」= 费用明细筛耗材（fin_type/大类）售价净额 |

---

## 9. 关联文档与变更历史

| 类型 | 路径 |
| --- | --- |
| 前身 | `AS-OP-FIN-005-门诊费用明细.md` |
| 对称 | `AS-RPT-009-住院费用明细.md` |
| SDD | `AS-RPT-008-门诊费用明细-SDD.md` |

| 版本 | 日期 | 摘要 |
| --- | --- | --- |
| v1.17 | 2026-10-06 | IF-03 分页响应同步 `discountFeatureEnabled`，避免明细表先挂后补列失败 |
| v1.16 | 2026-10-06 | **BR-RPT-OFD-008**：折扣相关列显隐只认是否已配置 ACTIVE 折扣内容 |
| v1.15 | 2026-10-06 | **BR-RPT-OFD-008**：已配置有效折扣时明细 Tab/导出展示优惠金额、实收金额；init `discountFeatureEnabled`；对齐 AS-OP-FIN-005 **BR-OP-FDT-018** |
| v1.14 | 2026-09-02 | **时间窗**：前端 `datetimerange`；Request 仍为 `String dateFrom/dateTo`（`yyyy-MM-dd HH:mm:ss`，兼容仅日期）；默认当日 `00:00:00～23:59:59`（BR-RPT-OFD-005） |
| v1.13 | 2026-08-24 | ChargeItem 白名单增 `REFUNDING`（退费中计已收，与日结 static / AS-OP-FIN-005 v1.9 BR-OP-FDT-002 对齐） |
| v1.12 | 2026-08-24 | **进价列角**：IF-05 Type A（明细异步、与 page 同筛选）；汇总 Type B（页脚=行加总）；对齐 DD §2.4 |
| v1.11 | 2026-08-22 | 汇总 Tab 列：数量+进售单价（展示）+进售合计；移除行数 |
| v1.10 | 2026-08-17 | 恢复 Payment `bill_date`/收退；与日结对账；DOC-CONFLICT 收敛「禁止扫 Payment / 仅 SETTLED」 |
| v1.9 | 2026-08-11 | 汇总 Tab 多维：`FIN_TYPE`/`DEPARTMENT`/`FEE_CATEGORY`/`ITEM`；init `summaryDimensionOptions` |
| v1.8 | 2026-08-11 | 明细分页页脚恢复进价合计（命中 scope 批量历史进价，禁 RowQueryModel） |
| v1.7 | 2026-08-11 | §7：明细分页禁止满月 ChargeItem 实体预载；DB encounter 分页 + 本页装载；页脚 SQL SUM |
| v1.6 | 2026-08-11 | §7：明细分页页脚售价可对预载 scope 轻量加总，禁止再全窗物化 RowQueryModel |
| v1.5 | 2026-08-06 | 诊疗/挂号进价=账单价（对齐 AS-OP-FIN-005 v1.5）；药耗仅同批号回放 |
| v1.4 | 2026-08-03 | 药耗进价纠偏：批号 `LOT_NUMBER_COST`（对齐 AS-OP-FIN-005 v1.4）；有批号禁止目录 PURCHASE |
| v1.3 | 2026-08-02 | **费用性质**展示/筛选改跟收费行 `account_id→contract_no`（非就诊优先医保户）；对齐双账户转自费 |
| v1.0 | 2026-07-21 | 迁入 RPT；新增类型净汇总 |
| v1.2 | 2026-07-28 | 增 **费用性质** 筛选与就诊块列 **费用性质、医疗类别**；init `feeNatureOptions` |
| v1.1 | 2026-07-21 | 应用层已落地；§2 查询服务列对齐实现 |
