# 住院费用明细 · 应用服务设计（AS）

> **文档职责**：报表中心「住院费用明细」只读契约；对齐 **AS-RPT-008**（就诊块明细 + 多维净汇总）。**≠** 护士站费用明细操作页（**AS-IP-NUR-006**）。
>
> **编码**：AS-RPT-009 · **版本**：v1.14 · **日期**：2026-10-08  
> **族边界**：`health/设计决策/DD-报表中心-发药耗材挂号费用汇总明细族.md`
>
> **DOC-CONFLICT→已按 DD 收敛**：旧述「payment 扫库 / 实收列为本期必出」已按 `health/设计决策/DD-报表中心-进售价口径与开立科室-2026-08.md` 收敛（ChargeItem-only）。**已配置有效折扣时明细 Tab 展示优惠/实收列**（对齐 **AS-RPT-008 BR-RPT-OFD-008**）。

---

## 1. 场景概述

### 1.1 目标与边界

| 维度 | 说明 |
| --- | --- |
| **在内** | IF-RPT-IFD-01～05：初始化（含 `summaryDimensionOptions`）、多维净汇总、就诊块明细分页、导出、**明细分页进价列角**（异步 Type A） |
| **在外** | 门诊费用 **AS-RPT-008**；护士站费用 **AS-IP-NUR-006**；住院结算写路径 |
| **涉及域** | **ChargeItem**（权威）；**Encounter** / 在院上下文；**Patient**；**ProductDefinition**；**Practitioner**；fin_type；日结仍用 Payment（本页不扫） |
| **编排权威** | 对齐 AS-RPT-008：ChargeItem-only + 仅 SETTLED；场景 **IMP** |

### 1.2 角色与关键动作

| 角色 | 关键动作 |
| --- | --- |
| **住院收费 / 财务** | 汇总看多维净额 → 明细按住院就诊块看已结账单 → 导出 |

### 1.3 术语与口径约定

| 术语 | 口径 |
| --- | --- |
| **明细** | 按住院就诊分块；金额列 **进价/售价单价总价**；**已配置有效折扣**（`discountFeatureEnabled`）时另展示 **优惠金额 / 实收金额**（优惠=`|total_price|−|discount_amount|`，实收=折后应收；**BR-RPT-IFD-008** / 对齐 **BR-RPT-OFD-008**）；诊疗无进价 |
| **按患者维度合并** | 明细 Tab 可选 `lineMergeMode=BY_DEFINITION`：同一就诊块内相同**产品品规**（`product_table + product_id + quantityUnit`；无产品时回落定价行 `definitionId + quantityUnit`）净合计数量与金额（退费数量取负）。**优先产品而非定价行**：同项目调价换 SALE 后仍并为一行。不同产品（如静脉输液主项与「第2瓶加收」）仍分行。清空业务时间/开单人/执行人/收费人等逐条差异列；**分页单位仍为就诊块**（块内合并，禁止为合并再全窗物化） |
| **数量口径** | `LINE`：展示各收费行落库 `quantity_value`（开立/执行业务量，**不强制为 1**）；`BY_DEFINITION`：同桶数量代数净合计。页脚 summary 不受合并影响 |
| **汇总** | 同 AS-RPT-008：**数量**、**进价单价/售价单价**（批号/账单同源）、**进价合计/售价合计**；维同 AS-RPT-008；**不展示行数** |
| **筛选** | 时间窗（前端 `datetimerange`）、**开立科室**（`requesting_org_id`）、大类/细分类等 |
| **筛选** | 时间窗、科室、大类/细分类、患者、项目、收费人、**费用性质**；REFUND Tab 恒空 |
| **明细列** | 明细行 **费用性质** = `ChargeItem.account_id → Account.contract_no → fin_contract`（转自费后跟新账户）；**医疗类别**仍取主诊断 med_type；不覆盖行内 `insuranceTypeDisplay` |
| **开单医生** | 仅账单锚定 **医生开立** Request（`generate_source=doctor-prescription`）的 `requester_id`；护士划价 / 自动滚费等 **不回填**（留空）；与门诊同构 |
| **收费人** | 仅结算/退费完成写入的 ChargeItem **`enterer_id`**（待结留空；禁止用开单人/执行人冒充） |
| **执行人** | **临嘱/长嘱同口径**：有 `procedure_id` 时取 Procedure 主执行人；耗材取 DeviceDispense 发放/配药人；**不再**回落收费行操作人戳 |
| **时间** | ChargeItem **`entered_date`** 时间窗（闭区间至秒；禁止扫 Payment） |
| **导出** | 仅当前 Tab |

### 1.4 本期范围与非目标

| 类别 | 内容 |
| --- | --- |
| **本期** | IF-RPT-IFD-01～04；报表中心挂载 |
| **非目标** | 护士站在院操作；门诊；未结算为主入口 |

### 1.5 领域清单（聚合 + 职责 + DM 路径）

| 聚合 | 职责 | DM |
| --- | --- | --- |
| **ChargeItem** | 住院费用行 | `DM-ADM-011` |
| **PaymentReconciliation** | 收退时间与金额 | `DM-FIN-002` |
| **Encounter** | 住院就诊块 | `DM-ADM-002` |
| **ProductDefinition / Practitioner** | 展示 | 对应 DM |

### 1.6 业务规则（BR-*）

| 规则 ID | 描述 | 绑定 IF |
| --- | --- | --- |
| **BR-RPT-IFD-001** | 仅 IMP；已结/已退 + 成功支付链路 | 02～04 |
| **BR-RPT-IFD-002** | 明细按就诊块分页；金额列对齐门诊费用明细（进价/售价单价总价；折扣列见 **BR-RPT-IFD-008**） | 03、04 |
| **BR-RPT-IFD-003** | 汇总按所选维净进价 / 净售价（默认 `FIN_TYPE`） | 02、04 |
| **BR-RPT-IFD-004** | 导出仅当前 Tab | 04 |
| **BR-RPT-IFD-005** | 时间窗必填且 `dateFrom≤dateTo`；前端 `datetimerange`；Request 仍为 `String dateFrom/dateTo`（`yyyy-MM-dd HH:mm:ss`，兼容仅日期）；仅日期时起补 `00:00:00`、止补 `23:59:59`；默认当日 `00:00:00～23:59:59` | 01～04 |
| **BR-RPT-IFD-006** | **禁止**复用护士站费用明细 IF 作为本报表契约 | 全文 |
| **BR-RPT-IFD-007** | 明细可选 `lineMergeMode`：`LINE`（默认）/ `BY_DEFINITION`；`BY_DEFINITION` 仅块内按**产品** `product_table+product_id+quantityUnit` 净合并（无产品回落 definitionId+单位；退费数量取负）；页脚/汇总/进价列角与合并无关；非法码 → EX-RPT-IFD-102 | 03、04 DETAIL |
| **BR-RPT-IFD-008** | 已配置 ACTIVE `cus_charge_discount_config` 时展示折扣相关列（配置本身即开关）。IF-01 下发 `discountFeatureEnabled`；true 时明细 Tab / 导出可见列含 **优惠金额、实收金额**（口径 **AS-RPT-008 BR-RPT-OFD-008**）；false 时不展示。汇总 Tab 仍只进/售价 | 01、03、04 |

---

## 2. 页面与接口总览（契约概览）

| IF 编号 | 用户动作 | 接口语义 | 读/写 | 关键规则 | 关键状态变化 | 涉及领域 | 查询服务 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| IF-RPT-IFD-01 | 进入页 | 筛选项、`discountFeatureEnabled` | 读 | BR-RPT-IFD-005、008 | 无 | 主数据 / 折扣配置 | 主数据 Query + `ChargeDiscountApplySupport` |
| IF-RPT-IFD-02 | 汇总 | 多维净金额（`summaryDimension`） | 读 | BR-RPT-IFD-001、003、005 | 无 | ChargeItem / Payment | `summarizeInpatientFeeDetailByFinType` + 应用层多维装配（已落地） |
| IF-RPT-IFD-03 | 明细 | 就诊块分页（可选块内品规合并）+ `discountFeatureEnabled` | 读 | BR-RPT-IFD-001～002、005、007、008 | 无 | 同上 | `pageInpatientFeeDetailEncounterBlocks`（已落地） |
| IF-RPT-IFD-04 | 导出 | 当前 Tab | 读 | BR-RPT-IFD-004、007、008 | 无 | 同上 | 同当前 Tab |
| IF-RPT-IFD-04 | 导出 | 当前 Tab | 读 | BR-RPT-IFD-004、007 | 无 | 同上 | 同当前 Tab |

---

## 3. 缺口跟踪（Gap）

| Gap ID | 缺口类型 | 文件 + 章节 | 缺什么 / 不一致点 | 关闭条件 | 影响 IF | 优先级 | 状态 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| GAP-RPT-IFD-001 | QueryService 能力 | `DM-ADM-011` §6.2.33～34 | 缺住院费用明细就诊块分页与 fin_type 净汇总（报表口径） | DM 补齐 + 实现 | 02～04 | P0 | **已关闭** |

### 3.1 Gap 关闭落点（2026-07-21）

| Gap ID | DM 章节 | Java 落点 |
| --- | --- | --- |
| GAP-RPT-IFD-001 | `DM-ADM-011` §6.2.33～34 | `ChargeItemQueryService#pageInpatientFeeDetailEncounterBlocks` / `#summarizeInpatientFeeDetailByFinType` |

> `queryInpatientCostDetails` 等护士站/单就诊视图 **不能**直接替代本报表（报表走 **ChargeItem-only** scope，**禁止扫 Payment**）。

---

## 4. 接口编排说明书（业务步骤）

### IF-RPT-IFD-01～04

与 **AS-RPT-008 §4** 同构，场景改为住院；异常 **EX-RPT-IFD-101～104**。

---

## 5. 业务异常与用户提示

| 语义代码 | 场景 | 用户提示方向 | 可重试 |
| --- | --- | --- | --- |
| EX-RPT-IFD-101 | 初始化失败 | 页面配置加载失败 | 是 |
| EX-RPT-IFD-102 | 日期非法 | 请选择有效查询日期 | 是 |
| EX-RPT-IFD-103 | 查询失败 | 查询失败 | 是 |
| EX-RPT-IFD-104 | 导出失败 | 导出失败 | 是 |

---

## 6. 事件语义与消费方

**本期无领域写操作。**

---

## 7. 非功能性需求

同 **AS-RPT-008 §7**（一个月正常窗：明细分页 DB encounter 分页 + 仅本页装载；**禁止**满月 `ChargeItem` 实体预载；页脚售价 SQL SUM / 投影合计）。

**进价列角（对齐 AS-RPT-008 / DD §2.4）**：明细 Tab=IF-RPT-IFD-05（与 page 同筛选，Type A）；汇总 Tab=当次汇总行进价加总（Type B）。

---

## 8. 测试要点（业务视角）

| 类别 | 要点 |
| --- | --- |
| **对齐** | 金额列与门诊费用明细一致 |
| **隔离** | 与护士站费用页筛选/状态不同 |
| **汇总** | 所选维净额冲减退费 |
| **合并** | `BY_DEFINITION`：同就诊同产品编码多行（含不同定价行）→ 1 行且数量相加；主项与加收项（不同 product）仍分行；`LINE` 数量=落库值 |

---

## 9. 关联文档与变更历史

| 类型 | 路径 |
| --- | --- |
| 对称 | `AS-RPT-008-门诊费用明细.md` |
| 隔离 | `AS-IP-NUR-006-住院护士站费用明细.md` |
| SDD | `AS-RPT-009-住院费用明细-SDD.md` |

| 版本 | 日期 | 摘要 |
| --- | --- | --- |
| v1.14 | 2026-10-08 | **BR-RPT-IFD-007**：`BY_DEFINITION` 合并键改为产品品规优先（修复同项目多定价行合并不生效）；明确 LINE 数量=落库值非强制 1；块内 chargeItemId 去重 |
| v1.13 | 2026-10-06 | IF-03 分页响应同步 `discountFeatureEnabled`，避免明细表先挂后补列失败 |
| v1.12 | 2026-10-06 | **BR-RPT-IFD-008**：已配置有效折扣时明细 Tab/导出展示优惠金额、实收金额；init `discountFeatureEnabled`；对齐 AS-RPT-008 **BR-RPT-OFD-008** |
| v1.11 | 2026-09-09 | 明细可选 **按患者维度合并**（`lineMergeMode=BY_DEFINITION`）：同就诊块内同 definition 净合计；BR-RPT-IFD-007；性能：块内合并、分页单位不变 |
| v1.10 | 2026-09-02 | **时间窗**：前端 `datetimerange`；Request 仍为 `String dateFrom/dateTo`（`yyyy-MM-dd HH:mm:ss`，兼容仅日期）；默认当日 `00:00:00～23:59:59`（BR-RPT-IFD-005） |
| v1.9 | 2026-08-12 | 开单医生仅医生开立 Request；划价/滚费不回填；明细/导出增 **执行人**（临嘱/长嘱均优先 Procedure 主执行人） |
| v1.8 | 2026-08-11 | 汇总 Tab 多维对齐 AS-RPT-008；init `summaryDimensionOptions` |
| v1.7 | 2026-08-11 | §7：同 AS-RPT-008，禁止满月实体预载；DB/混合轴分页 + 本页装载 |
| v1.6 | 2026-08-11 | §7：同 AS-RPT-008，页脚售价预载轻量加总、禁止再全窗物化 |
| v1.5 | 2026-08-06 | 诊疗/挂号进价=账单价（对齐 AS-RPT-008 / AS-OP-FIN-005 v1.5） |
| v1.0 | 2026-07-21 | 首版 |
| v1.4 | 2026-08-03 | 药耗进价纠偏：批号价；对齐 AS-RPT-008 / AS-OP-FIN-005 v1.4 |
| v1.3 | 2026-08-02 | **费用性质**展示/筛选改跟收费行 `account_id→contract_no`（对齐 AS-RPT-008 v1.3） |
| v1.2 | 2026-07-28 | 增 **费用性质** 筛选与就诊块列 **费用性质、医疗类别**（对齐 AS-RPT-008 v1.2） |
| v1.1 | 2026-07-21 | 应用层已落地；§2 查询服务列对齐实现 |
