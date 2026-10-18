# 财务管理 · 调价明细 · 应用服务设计（AS）

> **文档职责**：说明「调价明细」查询页的**接口契约（IF）**、业务语义、涉及聚合与 QueryService、规则（BR）、编排意图；作为 **health-application** 财务调价包实现的编排权威。
>
> **页面边界**：独立**只读**查询页 — 多条件筛选 → **按调价项目主行**分页展示已生效调价记录 → **展开/查看详情**展示子表批号影响明细；**不提供**制单、提交、审批；**不提供**导出（本期）。
>
> **与 AS-FIN-PRC-001 分工**：PRC-001 为调价单**单据闭环**（列表按 `bus_no`）；本页为**已生效调价台账**（列表按 `cus_price_adjustment` 项目主行），面向财务/物价**事后查询与审计**。
>
> **编码**：AS-FIN-PRC-002 · **版本**：v1.2 · **日期**：2026-07-31

---

## 1. 场景概述

### 1.1 目标与边界

| 维度 | 说明 |
| --- | --- |
| **在内** | IF-PRC-DTL-01 页初始化；IF-PRC-DTL-02 分页查询（仅 `approved`、按项目主行）；IF-PRC-DTL-03 查看项目行详情与子表批号明细 |
| **在外** | 调价制单/审批（**AS-FIN-PRC-001**）；限价单独菜单；库存锁、在途预检；目录主数据 CRUD；导出 Excel（本期非目标） |
| **涉及域** | **读侧**：PriceAdjustment / PriceAdjustmentDetail；**MedicationDefinition / DeviceDefinition / ActivityDefinition / HealthcareService**（项目展示 enrich）；租户上下文 |
| **编排权威** | 本文 **§2、§4**；纳入状态与盈亏口径对齐 **DM-CUS-011** |
| **数据锚点** | 主行 **`cus_price_adjustment`**（`status_enum = approved`）；子表 **`cus_price_adjustment_detail`**；时间筛选锚 **`approve_time`** |

### 1.2 角色与关键动作

| 角色 | 关键动作 |
| --- | --- |
| **财务 / 物价 / 管理人员** | 设定筛选（生效时间窗、项目类型、项目关键字、单号）→ **查询** → 浏览项目行列表（盈亏合计）→ **展开或打开详情**查看批号/数量/调前调后价与子表盈亏 |
| **系统** | 租户内加载已生效调价主行；批量 enrich 项目名称与编码；子表展示物流多批号或诊疗一对一行 |

### 1.3 术语与口径约定

| 术语 | 口径 |
| --- | --- |
| **列表行粒度** | 一行 = **`cus_price_adjustment` 一条主行**（一个调价项目；同一 `bus_no` 下多项目则多行） |
| **纳入状态** | **仅** `PriceAdjustmentStatus.APPROVED`（`approved`）；**不含** `draft` / `pending` / `rejected` / `deprecated` |
| **生效时间** | **`approve_time`**（审批通过时刻）；列表展示列与**时间筛选**均锚定此字段 |
| **时间筛选** | **`approve_time` 闭区间** `[approveTimeFrom, approveTimeTo]`（本地时间戳）；默认 **当月**由**前端自带**（对齐财务审批；init **不下发**时间窗） |
| **项目类型** | 三档 `item_table` Tab 或等效筛选：`med_medication_definition`（药品）、`adm_device_definition`（耗材）、`wkf_activity_definition`（诊疗活动）；**不含**医疗服务；**可选**不选表示三类并集 |
| **项目关键字** | 在**当前已选 `item_table` 范围内**（未选类型则在三类目录并集内）对 **项目名称** 与 **院内项目编码** 模糊匹配；双填 **AND**，单填单条件 |
| **调价单号** | `bus_no` **可选**精确或前缀模糊（与 PRC-001 列表关键字语义一致）；空则不收窄 |
| **列表盈亏合计** | 行级 **`totalImpact`** = 该主行下全部子表 **`purchase_impact` 之和 + `selling_impact` 之和**（提交时已固化，审批后不变） |
| **列表不展示调前/调后价** | 列表区**不出现**调前进价/售价、目标进价/售价列；仅展示 **盈亏合计** 及项目、单号、生效时间等元数据 |
| **详情调前/调后** | 详情区展示：**调后** = 主行 `new_purchase_amount` / `new_selling_amount`；**调前** = 子表 `old_purchase_amount` / `old_selling_amount`（按批号行）；物流双侧、诊疗仅售价侧 |
| **展示口径 `adjustmentStyle`** | `logistics`：药品/耗材，子表多批号；`clinical`：诊疗/服务，子表一对一（批号占位 `-`、数量 `1`） |
| **租户** | 仅当前租户；**不**额外做机构/个人数据权限 Gate |
| **菜单归属** | **一级「财务管理」** → **二级「调价明细」**（与「价格调整」并列） |
| **前后端路由** | 前端建议 `/finance/price-adjustment-detail`；API 与 PRC-001 同域：`POST /api/app/priceAdjustmentDetail/{method}`（**SDD** 定最终路由名） |
| **代码包** | `application.finance.priceadjustmentdetail`（与 PRC-001 制单包 `priceadjustment` 分离；见 **AS-SDD** §2.1） |

#### 1.3.1 列表展示列（业务语义）

| 展示列 | 业务含义 |
| --- | --- |
| **生效时间** | `approve_time` |
| **调价单号** | `bus_no` |
| **项目类型** | `item_table` 展示文案 |
| **项目名称 / 院内编码** | 目录 enrich |
| **盈亏合计** | `totalImpact`（**§1.3** 公式） |
| **调价原因** | `adjustment_reason` 展示文案（可选列，与 PRC-001 详情一致） |
| **制单人 / 审批人** | `operator_id` / `approver_id` enrich（可选列） |

#### 1.3.2 详情与子表展示列

| 层级 | 展示内容 |
| --- | --- |
| **主行摘要** | 单号、项目、生效时间、调后进价/调后售价（物流双侧；诊疗仅售价）、行级盈亏合计 |
| **子表行** | 批号 `lot_number`、影响数量 `quantity`、调前进价/调前售价、进价盈亏 `purchase_impact`、售价盈亏 `selling_impact` |

### 1.4 本期范围与非目标

| 类别 | 内容 |
| --- | --- |
| **本期** | IF-PRC-DTL-01～03 |
| **非目标** | 导出；制单/提交/审批/撤销；草稿与待审批查询；列表展示调前/调后金额；限价菜单；跳转 PRC-001 编辑（只读审计页，**不**要求编排写操作）；按 `org_id` 筛选（租户隔离即可） |

### 1.5 领域清单（聚合 + 职责 + DM 路径）

| 聚合（域） | 在本场景中的职责（摘要） | DM 路径 |
| --- | --- | --- |
| **PriceAdjustment / PriceAdjustmentDetail** | 已生效主行与子表快照读 | `DM-CUS-011-PriceAdjustment-价格调整.md` |
| **MedicationDefinition** | 药品项目名/编码 enrich | `DM-MED-001` |
| **DeviceDefinition** | 耗材项目名/编码 enrich | `DM-ADM-008` |
| **ActivityDefinition** | 诊疗活动 enrich | `DM-WKF-001` |
| **HealthcareService** | 医疗服务 enrich | `DM-ADM-009`（以 DM 真源为准） |
| **Practitioner / User** | 制单人、审批人展示（可选） | 主数据 DM |

### 1.6 业务规则（BR-*）

| 规则 ID | 描述 | 绑定 IF |
| --- | --- | --- |
| **BR-PRC-DTL-001** | `approveTimeFrom`、`approveTimeTo` 必填；`approveTimeFrom ≤ approveTimeTo`；默认**当月**由**前端自带**（月初～月末） | 01、02、03 |
| **BR-PRC-DTL-002** | 查询结果**仅** `status_enum = approved`；其它状态**零命中** | 02、03 |
| **BR-PRC-DTL-003** | 时间过滤锚 **`approve_time`**，**禁止**用 `adjustment_time` 或 `submit_time` 替代 | 02 |
| **BR-PRC-DTL-004** | `item_table` 筛选为三档合法值之一（药/耗/诊疗）；空表示三类并集 | 02 |
| **BR-PRC-DTL-005** | 项目关键字：名称与院内编码双填 **AND**；须在 **BR-PRC-DTL-004** 圈定的目录范围内解析 `item_id` 集合后下推主表过滤 | 02 |
| **BR-PRC-DTL-006** | `bus_no` 可选；非空时参与主表过滤 | 02 |
| **BR-PRC-DTL-007** | 行级盈亏 **`totalImpact`** = Σ子表(`purchase_impact`) + Σ子表(`selling_impact`)；缺失 impact 按 0；**禁止**用「(调后−调前)×数量」现场重算覆盖已固化值 | 02、03 |
| **BR-PRC-DTL-008** | 列表**禁止**返回调前/调后金额列；详情**必须**返回主行目标价与子表调前价及分项盈亏 | 02、03 |
| **BR-PRC-DTL-009** | 分页按**主行**维度；默认 `pageSize` 与平台列表默认一致（**SDD** 建议 20）；排序 **`approve_time` 降序**，同秒按主行 `id` 降序 | 02 |
| **BR-PRC-DTL-010** | 详情仅允许查询 **approved** 且未软删之主行；子表为该行下全部未删明细 | 03 |
| **BR-PRC-DTL-011** | 项目关键字命中目录为空集时返回**空列表**（非失败） | 02 |
| **BR-PRC-DTL-012** | 目录 enrich 失败（主行 `item_id` 无法解析展示名）→ **整次查询失败** | 02、03 |

---

## 2. 页面与接口总览（契约概览）

| IF 编号 | 用户动作 | 接口语义（业务） | 读/写 | 关键规则（BR） | 关键状态变化 | 涉及领域 | QueryService |
| --- | --- | --- | --- | --- | --- | --- | --- |
| IF-PRC-DTL-01 | 进入调价明细页 | 初始化：三档 `item_table` Tab 等选项；默认生效时间由前端自带当月（init 不下发时间窗） | 读 | BR-PRC-DTL-001 | — | PriceAdjustment（枚举投影） | — |
| IF-PRC-DTL-02 | 设定筛选 + 点「查询」/翻页 | 分页返回**已生效调价项目主行**列表（含行级盈亏合计；**不含**调前/调后价；**不含**子表） | 读 | BR-PRC-DTL-001～009、011、012 | — | PriceAdjustment + 各定义目录 | `PriceAdjustmentQueryService#countApprovedLines` + `#pageApprovedLines` |
| IF-PRC-DTL-03 | 展开行 / 点「详情」 | 按主行 `id` 加载主行摘要 + 全部子表明细（批号、数量、调前价、分项盈亏、主行调后价） | 读 | BR-PRC-DTL-002、007、008、010、012 | — | PriceAdjustment + 目录读 | `PriceAdjustmentQueryService#getApprovedLineById` |

> **合并说明**：切换 Tab、改时间窗、关键字、单号后点查询，均为 **IF-PRC-DTL-02**；列表展开子表若走懒加载，展开动作为 **IF-PRC-DTL-03**。

---

## 3. 缺口跟踪（Gap）

| Gap ID | 缺口类型 | 文件 + 章节 | 缺什么 / 不一致点 | 关闭条件 | 影响 IF | 优先级 | 状态 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| GAP-PRC-DTL-001 | QueryService 能力 | `DM-CUS-011` §6.2、`PriceAdjustmentQueryService` | 缺**已生效项目主行**专用分页：`approve_time` 闭区间 + `item_table` + `item_id IN` + 可选 `bus_no` + **`approved` 硬过滤** + 主行分页计数 | `countApprovedLines` + `pageApprovedLines`（及 `PriceAdjustmentApprovedLineListQuery`）已实现并与本文 BR 一致 | IF-PRC-DTL-02 | P0 | **已关闭** |
| GAP-PRC-DTL-002 | QueryService 能力 | `DM-CUS-011` §6.2、`PriceAdjustmentQueryService` | 缺按主行 `id` 加载**单条 approved 主行 + 子表**读模型 | `getApprovedLineById` 已实现；与 `PriceAdjustmentLineQueryModel` 一致 | IF-PRC-DTL-03 | P0 | **已关闭** |

> **说明**：项目关键字预解析（跨目录查 `item_id`）、目录 enrich、Practitioner enrich 属**应用层编排**，**不入 Gap**。`findApprovedHistoryByItem` 仅单项目历史，**不能**替代 IF-PRC-DTL-02。`countDistinctBusNo` / `listDistinctBusNoPage` 为单据维度，**不能**替代本场景主行分页。

### 3.1 Gap 关闭落点（2026-06-27）

| Gap ID | DM 章节 | Java 落点 | 备注 |
| --- | --- | --- | --- |
| GAP-PRC-DTL-001 | `DM-CUS-011` §6.2 | `PriceAdjustmentQueryService#countApprovedLines`、`#pageApprovedLines`；`PriceAdjustmentApprovedLineListQuery`；`PriceAdjustmentApprovedLineListPredicatesupport | `itemIds` 空列表短路空页 |
| GAP-PRC-DTL-002 | `DM-CUS-011` §6.2 | `PriceAdjustmentQueryService#getApprovedLineById` | 非 `approved` 抛 `NOT_FOUND` |

---

## 4. 接口编排说明书（业务步骤）

### IF-PRC-DTL-01 调价明细页初始化

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 用户进入「财务管理 · 调价明细」页 |
| **主路径** | 下发三档 `item_table` Tab 与展示口径 `adjustmentStyle` 映射；**调价原因手写，不下发枚举**；**默认生效时间不下发**（前端自带当月，对齐财务审批） |
| **失败分支** | 枚举投影失败不阻断页面，前端可用 code 回退 |

### IF-PRC-DTL-02 分页查询调价明细

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 有效生效时间区间；用户点查询或翻页 |
| **主路径** | **①** 校验 **BR-PRC-DTL-001、004、006**。**②** 若有关键字：在 **BR-PRC-DTL-004** 范围内按表路由目录 QueryService，解析 `item_id` 集合（**BR-PRC-DTL-005**）；空集直接返回空页。**③** 调用领域读侧 **`countApprovedLines`** → **`pageApprovedLines`**（**BR-PRC-DTL-002、003、009**）。**④** 批量 enrich 项目名称、院内编码、制单人/审批人（可选列）。**⑤** 对每主行汇总子表 impact 得 **`totalImpact`**（**BR-PRC-DTL-007**）；**禁止**在列表 DTO 填充调前/调后价（**BR-PRC-DTL-008**）。**⑥** 返回 `rows`、`pageNo`、`pageSize`、`totalCount` |
| **关键分支** | 无命中：空 `rows` + `totalCount=0`；未选 `item_table` 时关键字在三类目录并集检索 |
| **失败分支** | 时间非法 → EX-PRC-DTL-102；目录 enrich 失败 → EX-PRC-DTL-103；查询失败 → EX-PRC-DTL-104 |
| **查询侧说明** | 纯读；`approved` 与 `approve_time` 谓词须在查询层下推；禁止 App 全表加载后内存过滤 |

### IF-PRC-DTL-03 查看调价项目详情与子表

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 有效主行 `id`（来自列表展开或详情入口） |
| **主路径** | **①** 读侧加载 **approved** 主行及全部子表（**BR-PRC-DTL-010**）。**②** enrich 项目展示字段。**③** 组装主行调后价与子表批号、数量、调前价、分项盈亏；计算行级 **`totalImpact`**（**BR-PRC-DTL-007**）。**④** 返回详情 DTO |
| **关键分支** | 诊疗子表：批号占位 `-`、数量 `1`、无进价列；物流多批号全量返回 |
| **失败分支** | 主行不存在或非 `approved` → EX-PRC-DTL-101；enrich 失败 → EX-PRC-DTL-103 |
| **查询侧说明** | 纯读；子表须批量加载，禁止按批号循环仓储 |

---

## 5. 业务异常与用户提示

| 异常 ID | 场景 | 用户提示 | 可重试 |
| --- | --- | --- | --- |
| EX-PRC-DTL-101 | 详情主行不存在或状态非已生效 | 调价记录不存在或尚未生效 | 否 |
| EX-PRC-DTL-102 | 生效时间区间非法 | 请选择有效的生效日期 | 是 |
| EX-PRC-DTL-103 | 项目主数据无法解析 | 调价项目数据不完整，请联系管理员核对目录配置 | 否 |
| EX-PRC-DTL-104 | 列表查询执行失败 | 调价明细查询失败，请稍后重试 | 是 |

---

## 6. 事件语义与消费方

| 事件 | 来源 | 对本页的影响 |
| --- | --- | --- |
| `PriceAdjustmentApprovedEvent` | **AS-FIN-PRC-001** IF-PRC-13 | 新生效记录纳入查询范围；用户刷新后可见 |
| `PriceAdjustmentDraftSavedEvent` / `Submitted` / `Rejected` / `Withdrawn` | PRC-001 | **不影响**本页（非 `approved` 不纳入） |

> 本页 **不发布**领域事件；消费为**用户手动查询**，不要求实时推送。

---

## 7. 非功能性需求

| 维度 | 要求 |
| --- | --- |
| **性能** | 主行分页在 DB 下推；子表在详情或批量 enrich 时 **IN** 加载；关键字预解析批量查目录，禁止 per-row 目录查询 |
| **一致性** | 只读；以查询时刻持久化快照为准；盈亏展示**以子表已固化 impact 为准** |
| **权限** | 租户隔离；只读查询，无写 Gate |
| **金额刻度** | 域金额 **6 位小数 HALF_UP**；`totalImpact` 在后端累加后返回 |

---

## 8. 测试要点（业务视角）

| 类别 | 要点 |
| --- | --- |
| **范围** | 仅 `approved`；`pending`/`draft`/`rejected` 不出现 |
| **时间** | 按 `approve_time` 过滤；默认当月（前端）；跨日区间 |
| **项目类型** | 四 Tab 各自收窄；不选 Tab 为并集 |
| **关键字** | 名称/编码单填与双填 AND；无命中空列表 |
| **单号** | `bus_no` 可选过滤 |
| **列表列** | 有盈亏合计；**无**调前/调后价 |
| **盈亏** | `totalImpact` = Σpurchase_impact + Σselling_impact；与 DM 固化值一致 |
| **详情** | 物流多批号；诊疗一对一；调后价在主行、调前价在子表 |
| **分页** | `approve_time` 降序；翻页稳定 |
| **审批后** | PRC-001 同意后本页可查到对应行 |

---

## 9. 关联文档与变更历史

| 类型 | 路径 |
| --- | --- |
| **领域模型** | `docs/领域/DomainModel/DM-CUS-011-PriceAdjustment-价格调整.md |
| **领域 SDD** | `health/DomainModel-SDD/DM-CUS-011-PriceAdjustment-价格调整-SDD.md` |
| **上游 AS（制单）** | `health/AppService/AS-FIN-PRC-001-价格调整.md` |
| **领域读侧** | `DM-CUS-011` §6.2 `countApprovedLines` / `pageApprovedLines` / `getApprovedLineById` |
| **参照（只读查询页）** | `health/AppService/AS-OP-FIN-005-门诊费用明细.md` |
| **应用 SDD** | `health/AppService-SDD/AS-FIN-PRC-002-调价明细-SDD.md` |
| **院点清库** | 朝阳《期初后清库》：`cus_price_adjustment*` **留**（中药入库自动调价不删） |

| 版本 | 日期 | 变更摘要 |
| --- | --- | --- |
| v1.2 | 2026-07-31 | 关联朝阳期初后清库：调价主从表保留 |
| v1.1 | 2026-06-27 | 关闭 **GAP-PRC-DTL-001～002**；对齐 `DM-CUS-011` §6.2 与 `PriceAdjustmentQueryService` 已生效主行读侧 |
| v1.0 | 2026-06-27 | 首版：调价明细只读查询页 IF-PRC-DTL-01～03；仅 `approved`；`approve_time` 筛选；列表按项目主行、仅盈亏合计；详情含子表批号明细；Gap GAP-PRC-DTL-001～002 |
