# 门诊发退药记录 · 应用服务设计（AS）

> **迁移声明（2026-07-21）**：本页将演进为报表中心 **「门诊发药报表」**，正式契约改由 **`AS-RPT-003`** 承接（汇总 + 明细）；**不另建**平行药品报表页。族边界见 `health/设计决策/DD-报表中心-发药耗材挂号费用汇总明细族.md`。在 **AS-RPT-003** 成稿前，本文仍为现网发退药记录读侧权威。
>
> **文档职责**：说明门诊药房「发退药记录」查询页的**接口契约（IF）**、业务语义、涉及聚合与 QueryService、规则（BR）、编排意图；作为 **health-application** `outpatient/pharmacy/dispenserecord` 包实现的编排权威。
>
> **页面边界**：筛选区（发药时间、药房、项目类型、发药人、药品关键字、患者关键字、记录类型）→ **分页查询**平铺明细 → 前端按**就诊**或**处方号**分组展示 → **导出 Excel**（与当前筛选一致）。**纯读**，无发药/退药写操作。
>
> **编码**：AS-OP-PHA-004 · **版本**：v1.3 · **日期**：2026-07-11（迁移声明 2026-07-21）

---

## 1. 场景概述

### 1.1 目标与边界

| 维度 | 说明 |
| --- | --- |
| **在内** | 页面初始化（筛选项下拉）；**分页查询**门诊场景已发生的药品发药/退药事实行；**导出**当前筛选结果；实现落点 **health-application / outpatient / pharmacy / dispenserecord** |
| **在外** | 门诊发药作业（**AS-OP-PHA-001**）；门诊退药确认（**AS-OP-PHA-002**）；耗材发放（`DeviceDispense`）；住院发药报表（**AS-RPT-004**）；小计/合计行（老系统按就诊或处方汇总小计，**本期不做**） |
| **涉及域** | **MedicationDispense**（事实主表）；**MedicationRequest**（处方号、开单科室、用法用量）；**MedicationDefinition**（药品展示与 `category_code`）；**ChargeItem**（单价/金额，锚点 `dispense_table + dispense_id`）；**Encounter** / **Patient**（就诊与患者摘要）；**Location**（药房）；**Practitioner**（发药人）；**Organization**（开单科室） |
| **编排权威** | 本文 **§2、§4**；记录口径以 **DM-MED-002** 发放事实为准，**不**改写发药工作站 supersede 隐藏规则 |
| **权限** | **报表式只读**：不按发药作业页 `CurrentManagedLocationIdsResolver` 管辖库房收窄；药房下拉为租户下**全部启用药房**；租户过滤由框架基线承担 |

### 1.2 角色与关键动作

| 角色 | 关键动作 |
| --- | --- |
| **门诊药房人员 / 药房管理** | 设定筛选条件 → 查询 → 切换前端「按就诊/按处方号」视图 → 导出 |
| **系统** | 下发筛选项；按条件分页返回**平铺明细行**；批量装配展示字段与账单价格；导出同条件全量 |

### 1.3 术语与口径约定

| 术语 | 口径 |
| --- | --- |
| **发药记录行** | 正向发放：`dispense_enum ∈ {OUTPATIENT, TEMPORARY}` 且 `status_enum = COMPLETED` |
| **退药记录行** | 退药对冲：`dispense_enum = REFUND` 且 `status_enum = REFUNDED` |
| **一发一退** | 正向 `COMPLETED` 与退药 `REFUNDED` **各占一行**；**不做**行合并或小计；**展示**已被退药收口的正向 `COMPLETED` 行（**不**套用发药工作站 supersede 排除）；**按处方（默认）**视图下同一临床方（`prescription_no` 去 `T` 前缀）**发药组与退药组相邻**（发药在上、退药在下） |
| **无部分退** | 业务上仅全量退；列表数量取发放单 `dispense_quantity`（或等价实发/实退字段） |
| **发药时间** | 筛选与排序统一用 `dispense_time`；`dispense_time` 为空的历史行**不纳入**时间窗（不退化为 `status_changed_time`） |
| **记录类型筛** | 全部 / 仅发药 / 仅退药（映射上述两类行） |
| **项目类型** | `med_medication_definition.category_code`，术语 **`medication_category_code`**（西药/中成药/中草药等）；**多选**；init 下拉与术语表一致，不扩展选项 |
| **价格** | 正向：`ChargeItem` 按 `med_medication_dispense` **dispense_id** 锚点批量读；退药：优先 dispense 锚点，**未命中时**按原药嘱 **`service_id`**（`refund_medicine_id`）补读（与 **AS-OP-PHA-002** 同源）；**不看** charge 筛选状态；发药为正、退药为负；无 charge **展示空** |
| **处方号** | API 返回存库值：发药 `CP…`、退药 **`TCP…`（`T`+原号，见 **DM-MED-003** **BR-MRQ-013**）**；**读侧禁止**回落原药嘱处方号/科室/用法；缺字段展示空；前端**按处方**视图列展示退药行为原 `CP…`（Tooltip 存库 `TCP…`）；导出 Excel 用存库完整号 |
| **药品/患者检索** | **两个独立**模糊条件，可同时生效；药品侧匹配名称/拼音/编码；患者侧匹配姓名等（与门诊队列关键字同族） |
| **展示模式** | 后端**平铺分页**；前端 `PharmacyDispenseRecordPage` + `dispense-record-group-display.ts` 支持 **按处方（默认）/ 按就诊/ 平铺**：按处方时以**完整** `prescription_no`（`CP…`/`TCP…`）为组合并 key，合并类型/发药时间/药房/发药人/患者/门诊号/处方号/开单科室等组头列；**临床方维度**重排使一发一退**相邻**；处方号列 `minWidth≥200`；**无小计行** |
| **长临嘱** | **不按**医嘱长期/临时过滤 |

### 1.4 本期范围与非目标

| 类别 | 内容 |
| --- | --- |
| **本期** | IF-OPR-01～03：初始化、分页查询、导出；前端列表页（`pharmacy/dispense-record/outpatient`）含按处方/按就诊/平铺展示切换 |
| **非目标** | 写路径（发药/退药/作废）；耗材；按就诊/处方**小计**；管辖库房权限收窄；charge 状态筛选；部分退数量口径；`PENDING_REFUND` / `DECLINED` / 未发完状态；住院记录 |

### 1.5 领域清单（聚合 + 职责 + DM 路径）

| 聚合 | 在本场景中的职责 | DM 路径 |
| --- | --- | --- |
| **MedicationDispense** | 发/退事实主键、时间、数量、库房、发药人、批号、追溯码、汇总单号 | `DM-MED-002-MedicationDispense-药品发放.md` |
| **MedicationRequest** | 处方号、开单科室、用法频次等医嘱快照 | `DM-MED-003-MedicationRequest-药品申请.md` |
| **MedicationDefinition** | 药品名称、规格、编码、`category_code` | `DM-MED-001-MedicationDefinition-药品定义.md` |
| **ChargeItem** | 单价、金额（锚点读，不参与状态过滤） | `DM-ADM-011-ChargeItem-收费明细.md` |
| **Encounter** | 门诊号、就诊上下文 | `DM-ADM-002-Encounter-就诊.md` |
| **Patient** | 患者姓名等摘要 | `DM-ADM-001-Patient-患者.md` |
| **Location** | 药房名称 | `DM-ADM-004-Location-位置.md` |
| **Practitioner** | 发药人姓名 | `DM-ADM-005-Practitioner-医务人员.md` |
| **Organization** | 开单科室名称 | `DM-ADM-003-Organization-组织.md` |

### 1.6 业务规则（BR-*）

| 规则 ID | 描述 | 绑定 IF |
| --- | --- | --- |
| **BR-OPR-001** | 仅纳入**已发生事实**：发药 `COMPLETED` 正向行 + 退药 `REFUNDED` 且 `dispense_enum=REFUND`；不含待退/作废/未发完 | IF-OPR-02、03 |
| **BR-OPR-002** | 场景收敛：正向 `dispense_enum ∈ {OUTPATIENT, TEMPORARY}`；退药 `REFUND+REFUNDED` 且 `encounter_id` 关联 **AMB** 就诊（无患者关键字时领域 **EXISTS** 按就诊类别收窄；有患者关键字时 `resolvedEncounterIdsForScene` IN）；仅 **MedicationDispense**，排除耗材 | IF-OPR-02、03 |
| **BR-OPR-003** | 时间窗：`dispense_time` 闭区间；排序 `dispense_time` **倒序**；空 `dispense_time` 不入窗 | IF-OPR-02、03 |
| **BR-OPR-004** | 记录类型筛：全部=两类并集；仅发药=正向 `COMPLETED`；仅退药=`REFUND+REFUNDED` | IF-OPR-02、03 |
| **BR-OPR-005** | **不**对正向 `COMPLETED` 应用退药 supersede 排除（与 **AS-OP-PHA-001** 队列/ slip 不同） | IF-OPR-02、03 |
| **BR-OPR-006** | 项目类型：`category_code IN` 请求多选（`medication_category_code` 码值） | IF-OPR-02、03 |
| **BR-OPR-007** | 药房：`location_id` 可选；未传不按库房过滤；下拉为租户下全部启用药房 | IF-OPR-01、02、03 |
| **BR-OPR-008** | 发药人：仅 `practitioner_id` 精确匹配可选筛选 | IF-OPR-02、03 |
| **BR-OPR-009** | 药品关键字与患者关键字**独立**；同时传则 AND | IF-OPR-02、03 |
| **BR-OPR-010** | 价格：正向按 **dispense_id** 锚点读 `ChargeItem`；退药未命中时按原药嘱 **service_id** 补读（`PharmacyDispenseRecordChargePricingsupport）；退药行金额为**负**；无 charge 为空 | IF-OPR-02、03 |
| **BR-OPR-011** | 列表**平铺**，禁止服务端按就诊/处方聚合小计 | IF-OPR-02、03 |
| **BR-OPR-012** | 导出条件与 IF-OPR-02 **完全一致**；不分页或按导出上限全量（SDD 定上限） | IF-OPR-03 |
| **BR-OPR-013** | **按处方**视图：同一临床方下发药组（`CP…`）与退药组（`TCP…`）**相邻**（发药在上、退药在下）；分组 key 为完整处方号；前端处方号列退药行展示原 `CP…` | IF-OPR-02（前端展示） |
| **BR-OPR-014** | 处方号、开单科室、用法用量**仅**取发放行或当前 `medReqId` 药嘱/发放快照；**禁止**读侧按 `refund_medicine_id` 回落原药嘱；依赖生成侧 **`T`+CP** 与字段拷贝 | IF-OPR-02、03 |

---

## 2. 页面与接口总览（契约概览）

| IF 编号 | 用户动作 | 接口语义（业务） | 读/写 | 关键规则（BR） | 关键状态变化 | 涉及领域 | 查询服务 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| IF-OPR-01 | 进入发退药记录页 | 下发筛选项（药房、项目类型、发药人、记录类型） | 读 | BR-OPR-007 | — | Location / Practitioner / 术语 | `LocationQueryService`；`PractitionerQueryService`；术语加载 |
| IF-OPR-02 | 设定筛选后查询/翻页/刷新 | 分页查询门诊发退药**平铺明细** | 读 | BR-OPR-001～013 | — | MedicationDispense 及关联读 | `MedicationDispenseQueryService#pageMedicationDispenseRecords`；`ChargeItemQueryService#listChargeItemsForMedicationDispensePricing`；`PharmacyDispenseRecordChargePricingsupport |
| IF-OPR-03 | 点击导出 | 按当前筛选导出 Excel | 读 | BR-OPR-012 | — | 同 IF-OPR-02 | `MedicationDispenseQueryService#listMedicationDispenseRecords` |

---

## 3. 缺口跟踪（Gap）

**无待跟踪缺口**（`DM-MED-002` §6.2.18 `pageMedicationDispenseRecords` / `listMedicationDispenseRecords` 已与领域代码对齐）。

---

## 4. 接口编排说明书（业务步骤）

### IF-OPR-01 页面初始化

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 用户进入门诊发退药记录页 |
| **主路径步骤** | ① 加载租户下**全部启用药房**（`adm_location`，药房/药库类型）。② 加载 **`medication_category_code`** 术语选项（与药品目录 `category_code` 对齐）。③ 加载发药人候选（本机构药师主档或近端有发药事实的 `practitioner_id` 集合，以实现为准）。④ 下发记录类型固定选项：全部 / 发药 / 退药 |
| **关键分支** | 无药房时仍返回空下拉，页面可打开 |
| **失败分支** | 元数据加载失败 → EX-OPR-101 |
| **查询侧说明** | 只读主数据与术语，不写库 |

### IF-OPR-02 分页查询发退药记录

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 用户已选或默认发药时间窗；可组合其它筛选项 |
| **主路径步骤** | ① 校验时间窗合法（`dateFrom ≤ dateTo`）。② 按 **BR-OPR-001～009** 调用发放域分页查询。③ 批量加载 `MedicationRequest`、药品定义、患者/就诊摘要、药房/科室/发药人名称。④ 按发放主键批量读 `ChargeItem`（**BR-OPR-010**）。⑤ 映射平铺行：记录类型、发药时间、药房、发药人、患者、门诊号、处方号、药品名/规格/编码、数量、单位、单价、金额、批号、追溯码、开单科室、用法用量、汇总单号（若有）等。⑥ 返回分页结果（**无小计行**） |
| **关键分支** | 记录类型=仅发药/仅退药时收窄状态与 `dispense_enum`；项目类型多选为空表示不按类目过滤 |
| **失败分支** | 时间窗非法 → EX-OPR-102；查询失败 → EX-OPR-103 |
| **查询侧说明** | 主事实在 `med_medication_dispense`；跨聚合批量装配，禁止 N+1 |

### IF-OPR-03 导出发退药记录

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 用户点击导出；筛选条件与列表查询一致 |
| **主路径步骤** | ① 复用 IF-OPR-02 同一筛选口径调用全量列表查询。② 列与列表展示字段一致。③ 生成 Excel 文件流或下载令牌（SDD 定传输形态） |
| **关键分支** | 零行时导出空表或提示无数据（产品约定，SDD 定一种） |
| **失败分支** | 同 IF-OPR-02；导出失败 → EX-OPR-104 |
| **查询侧说明** | 与 IF-OPR-02 同源；须设导出行数上限防 OOM |

---

## 5. 业务异常与用户提示

| 语义代码 | 场景 | 用户提示方向 | 可重试 |
| --- | --- | --- | --- |
| EX-OPR-101 | 初始化失败 | 页面初始化失败，请稍后重试 | 是 |
| EX-OPR-102 | 时间窗非法 | 请选择有效的发药时间范围 | 否 |
| EX-OPR-103 | 查询失败 | 查询失败，请稍后重试 | 是 |
| EX-OPR-104 | 导出失败 | 导出失败，请稍后重试 | 是 |

---

## 6. 事件语义与消费方

**本期无领域写操作，不发布、不消费领域事件。**

---

## 7. 非功能性需求

| 类别 | 要求 |
| --- | --- |
| **性能** | 分页查询 ≤ 2000ms（常规日窗 + 单药房）；导出 ≤ 30s（SDD 定最大行数，建议 ≤ 5000） |
| **一致性** | 纯读；价格以 charge 锚点为准，与 charge 状态无关 |
| **审计** | 查询/导出记操作日志（框架审计，可选） |
| **租户** | 数据自然限定当前租户；不按管辖库房收窄 |

---

## 8. 测试要点（业务视角）

| 类别 | 要点 |
| --- | --- |
| **正向** | 发药后可见 `COMPLETED` 行；退药确认后可见 `REFUNDED` 行；同一业务一发一退两行均存在 |
| **筛选** | 时间按 `dispense_time`；记录类型；项目类型多选；药房；发药人；药品/患者关键字组合 |
| **价格** | 发药金额为正；退药金额为负；无 charge 为空 |
| **边界** | 不含 `PENDING_REFUND`；不含耗材；不含住院 `INPATIENT` 行；无小计行 |
| **导出** | 与列表筛选一致；列与列表对齐 |

---

## 9. 关联文档与变更历史

| 类型 | 路径 |
| --- | --- |
| 发放 DM | `docs/领域/DomainModel/DM-MED-002-MedicationDispense-药品发放.md |
| 收费明细 DM | `docs/领域/DomainModel/DM-ADM-011-ChargeItem-收费明细.md |
| 关联 AS | `AS-OP-PHA-001-门诊发药.md`、`AS-OP-PHA-002-门诊退药.md` |
| 住院对照 | `AS-RPT-004-住院发药报表.md`（原 AS-IP-PHA-004 已废止） |

| 版本 | 日期 | 摘要 |
| --- | --- | --- |
| v1.3 | 2026-07-11 | **BR-OPR-002**：退药行须 AMB 就诊同窗；无患者关键字时由 `DM-MED-002` §6.2.18 EXISTS 收窄（与住院列表对称） |
| v1.2 | 2026-06-26 | **BR-OPR-014** 取消读侧原药嘱回落；处方/科室/用法仅当前药嘱；**BR-OPR-010** 退药价仍按原药嘱 service 补读 |
| v1.1 | 2026-06-26 | **退药展示闭环**：**BR-OPR-010** 退药价按原药嘱 service 补读；**BR-OPR-013** 按处方一发一退相邻 + 处方号 `T` 前缀展示口径；对齐 **DM-MED-003** **BR-MRQ-013** |
| v1.0 | 2026-06-21 | 初版：门诊发退药记录纯读查询 + 导出；统一 `medication_category_code`；一发一退平铺无小计 |
