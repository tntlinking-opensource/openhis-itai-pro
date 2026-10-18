# 住院护士站 · 费用明细 · 应用服务设计（AS）

> **文档职责**：工作台右侧「**费用明细**」Tab 契约——按**单就诊**、**日期区间**（默认**当日**）查询住院在院费用；**明细**与**汇总**两视图；**合计**（费用大类 + 分类字典 + 总合计）；**只读**；**不**经 `InpatientChargeDetail` QueryService。
>
> **页面边界**：**本 Tab**——切换日期、点查询、切换「明细 / 汇总」Tab、查看合计区。**患者上下文**在 **AS-IP-NUR-001**（单选就诊）；划价写操作在 **AS-IP-NUR-005**；医嘱执行在 **AS-IP-NUR-004**。
>
> **编码**：AS-IP-NUR-006 · **版本**：v1.18 · **日期**：2026-10-17

---

## 1. 场景概述

### 1.1 目标与边界

| 维度 | 说明 |
| --- | --- |
| **在内** | IF-IP-CDT-01 费用明细页初始化（默认日期区间）；IF-IP-CDT-02 按就诊 + 日期区间查询（**presentationMode** = 明细 \| 汇总），含行列表与**合计块** |
| **在外** | 左侧患者树/列表/详情（**AS-IP-NUR-001**）；划价/补费写路径（**AS-IP-NUR-005**）；医嘱执行写路径（**AS-IP-NUR-004**）；医生站开立/签发（**AS-IP-DOR-003**）；预交金与出院结算（**AS-IP-FIN-002** 及后续住院结算 AS）；**多患者合并查看**；导出；医生站/收费窗嵌入（后续迭代）。**费用清单/明细单打印**为前端复用 IF-IP-CDT-02（**BR-IP-CDT-021**），非独立 IF |
| **涉及域** | ChargeItem；Procedure；ProductDefinition（QueryService）；Encounter（可见性） |
| **编排权威** | 以本文 **§2、§4** 及评审锁定业务口径为准；读侧 **ChargeItem 业务表** + **MedicationDefinitionQueryService**，**禁止** App 直查 `InpatientChargeDetail` QueryService Repository |

### 1.2 角色与关键动作

| 角色 | 关键动作 |
| --- | --- |
| **病区护士** | 左侧选中**一名**在院患者 → 进入费用明细 Tab →（可选）调整日期区间 / 费用状态 / **项目关键字** → **查询** → 切换「明细 / 汇总」查看流水与合计；**汇总**视图可**展开**查看组内每次执行/划价明细 |
| **系统** | 按状态白名单与挂号锚点排除加载 `adm_charge_item` → 解析**客户可见业务时间**（执行/划价诊疗→Procedure.`recorded_time`；耗材划价→发放；滚费→`occurrence_time`）→ 关联产品QueryService → 明细排序或汇总分组 → 计算合计（汇总/合计区用**净额**） |

### 1.3 术语与口径约定

| 术语 | 口径 |
| --- | --- |
| **纳入状态（明细）** | `PENDING_SETTLEMENT`（待结算）、`SETTLED`（已结算）、`REFUNDING`（退款中）、`REFUNDED`（已退款）；**不含** `DRAFT`、`PLANNED` |
| **挂号费排除** | `service_table = adm_healthcare_service` 的行**不**进入本 Tab 任一视图与合计 |
| **业务时间（客户可见）** | **进窗、排序、列展示同一轴**：医嘱执行 / 护士划价诊疗（净剩）→ 净剩 COMPLETED Procedure **`recorded_time`**；**护士/医技划价诊疗冲销留痕**（Procedure 已对冲）→ 仍用原 COMPLETED **`recorded_time`**（与耗材成对可见同构，**BR-IP-CDT-018** / **BR-IP-CDT-016** 例外）；护士划价耗材 → 主发放展示时刻（`dispense_time` 优先，否则 `prepare_time`）；滚费 → 账单 `occurrence_time`。医嘱执行路径无法解析客户轴 → 该行跳过（不回落 occurrence 给用户） |
| **净剩 Procedure** | 同一就诊下：存在 `CANCEL` 且 `refund_id` 指向某 `COMPLETED` 时，该 `COMPLETED` 与对应 `CANCEL` **均不作为**医嘱/执行路径客户可见时间锚点（**BR-IP-CDT-015**）。**例外**：`generate_source ∈ {nurse-pricing, medical-tech-pricing}` 且 `service_table=wkf_service_request` 的冲销成对 Charge，可按 `procedure_id` 回查原 COMPLETED.`recorded_time` 进窗（实现：`InpatientNurseStationCostDetailLoader`） |
| **occurrence_time** | **仅后端逻辑 / 排障**（真正何时操作）；**对用户无感**——不用于本 Tab 日期过滤、排序或「业务时间」列 |
| **展示时间（明细列 / 汇总 activityLines）** | 与上表**业务时间（客户可见）**同源（DTO `businessDateTime` / `displayDateTime` 同值）；**禁止**用 occurrence 给用户进窗或展示 |
| **明细粒度** | **一行 = 一条 `ChargeItem`**；**原单与退费单均展示**；退费行须带**醒目标识**（如 `refundIndicator` / 状态文案，SDD 定字段名） |
| **汇总粒度** | 按 **`product_id`** 分组（组套见 **BR-IP-CDT-006**）；主行**不展示**业务时间；组内明细见 **BR-IP-CDT-018** `activityLines` |
| **汇总展开明细** | 每条汇总行携带 `activityLines`：**一行 = 组内一条 `ChargeItem` 对应的执行、划价或自动计费记录**；展示业务时间、操作、操作人、类型（执行/划价/自动计费/取消执行/**冲销**/取消自动计费）、账单数量、**总价**（`total_price` 原样，冲销负额保留）；**取消类**标红（**BR-IP-CDT-018**）。护士站「冲销」后产生负向 Charge，本 Tab 可见「划价」+「冲销」成对行；明细「来源」列负向行展示「护士冲销」；床位滚费取消执行会物理删除费用，本 Tab 不再展示该行 |
| **展示金额 vs 统计净额** | **行级** `unit_price`、`total_price` **始终按账单事实原样返回**（与是否已退、是否退光无关）；**汇总行金额**、**合计区**对 `total_price` **轧差净额**（退费冲减） |
| **汇总单价** | 展示账单单价；**同一 product 单价一致**，取组内任一条 `unit_price`；**不加权均价** |
| **汇总数量** | 组内数量统一换算到**包装单位**（**BR-IP-CDT-011**）：满包返回**整数** `packageQuantity`；不足一包**另返** `splitUnitQuantity`（小单位数量）；**禁止**后端返回「0.x 盒」类包装小数 |
| **费用大类（块顺序）** | 由 **`service_table`** 映射：**药品** → **诊疗** → **耗材**；块内**不要求**再排序 |
| **细分类目（category）** | 来自 **ProductDefinition.`category_code`**（及字典译文）；**缺失则报错**（**BR-IP-CDT-009**） |
| **费用大类映射（`service_table`）** | `med_medication_request` 及药品相关锚点 → **药品**；`wkf_service_request` → **诊疗**；`wkf_device_request` → **耗材**；未识别表名 → **报错** |
| **来源展示** | 可读 `generate_source_enum`（如医生开立 / 护士划价 / 医嘱执行）及费用状态；**床位滚费**覆盖「自动计费」；**护士划价冲销负向行**（`refund_id` / 数量或金额为负等，同 **BR-IP-CDT-018**）覆盖「护士冲销」（`generate_source` 仍为 `nurse-pricing`） |
| **单就诊** | 仅接受 **AS-IP-NUR-001** 当前选中 `encounterId`；**禁止** `encounterIds` 批量 |
| **默认日期** | 服务端下发 **当日** `[dateFrom, dateTo]`（本地日历日，闭区间） |
| **读侧组装** | `ChargeItemQueryService`（或等价域查询）+ 批量 `Procedure` + `MedicationDefinitionQueryService`；**禁止** `ChargeItemQueryService` |

### 1.4 本期范围与非目标

| 类别 | 内容 |
| --- | --- |
| **本期** | IF-IP-CDT-01～02 |
| **非目标** | 多患者合并查询；导出；费用冲销/删单；账户余额/费别列（由 001 卡片承担）；医生站/收费窗同能力；分页（与护士站其它 Tab 一致，单就诊区间内全量返回，数据量过大时由 SDD 评估上限）；`InpatientChargeDetail` QueryService路径 |

### 1.5 领域清单（聚合 + 职责 + DM 路径）

| 聚合（域） | 职责摘要 | DM 路径 |
| --- | --- | --- |
| **ChargeItem** | 费用事实查询、状态过滤、挂号锚点排除 | `DM-ADM-011` §6.2.3 / §6.2.4 |
| **Procedure** | 医嘱/执行路径业务时间与明细时间 | `DM-CLN-004` |
| **ProductDefinition（读）** | 名称、规格、包装/拆零比、`category_code` | `DM-MED-001 / DM-WKF-001 / DM-ADM-008 §6.2 目录 QueryService` |
| **Encounter** | 护士站可见性 Gate | `DM-ADM-002` |

### 1.6 业务规则（BR-*）

| 规则 ID | 描述 | 绑定 IF |
| --- | --- | --- |
| **BR-IP-CDT-001** | `encounterId` 必填；须落在 **AS-IP-NUR-001** 护士站可见范围 | 02 |
| **BR-IP-CDT-002** | `dateFrom`、`dateTo` 必填；`dateFrom ≤ dateTo`；默认均为**当日** | 01、02 |
| **BR-IP-CDT-003** | 明细/汇总**结果集**内，每条 ChargeItem 须可解析**客户可见业务时间**（执行/净剩划价诊疗→净剩 Procedure.`recorded_time`；划价诊疗冲销留痕→原 COMPLETED.`recorded_time`；划价耗材→发放展示；滚费→账单 `occurrence_time`）；结果集内应解析而缺关键锚点（如医嘱缺 `procedure_id`）→ **整次查询失败** | 02 |
| **BR-IP-CDT-016** | **医嘱/执行路径**：Procedure 已对冲或无 recorded → 该行**静默跳过**（不进入结果集、不触发 203）；**不对用户**回落 `occurrence_time`。**例外（划价冲销留痕）**：`generate_source ∈ {nurse-pricing, medical-tech-pricing}` 诊疗 Charge 在 Procedure 已对冲时，按 `procedure_id` 回查原 COMPLETED.`recorded_time` 进窗，保证「划价」+「冲销」成对可见（**BR-IP-CDT-018**）；耗材划价仍走发放展示轴 | 02 |
| **BR-IP-CDT-015** | 医嘱/执行路径：`procedure_id` **必须**落在就诊维度**净剩 COMPLETED** 且具备 `recorded_time`（`ProcedureQueryService#mapNetCompletedRecordedTimeByEncounterId`）；已对冲的 COMPLETED **不得**作为医嘱/执行路径客户可见时间（划价冲销留痕见 **BR-IP-CDT-016** 例外） | 02 |
| **BR-IP-CDT-004** | 仅 `status_enum ∈ { PENDING_SETTLEMENT, SETTLED, REFUNDING, REFUNDED }`；`service_table <> adm_healthcare_service`；条件须在**查询层下推** | 02 |
| **BR-IP-CDT-005** | 业务时间（本地日期）∈ `[dateFrom, dateTo]`（闭区间） | 02 |
| **BR-IP-CDT-006** | **组套**：子项行 `parent_id` 指向父 ChargeItem；**展示与汇总分组**使用**父项 `product_id`**；明细仍**一行一条 charge**，项目名称/规格走父项产品QueryService | 02 |
| **BR-IP-CDT-007** | 每条 ChargeItem **`product_id` 非空**；为空 → **整次查询失败**（**BR-IP-CDT-009**） | 02 |
| **BR-IP-CDT-008** | **明细**按业务时间**倒序**；**汇总**块顺序：药品 → 诊疗 → 耗材；`service_table` 映射：`med_medication_request`/`med_medication_dispense`→药品，`wkf_service_request`→诊疗，`wkf_device_request`/`wkf_device_dispense`→耗材 | 02 |
| **BR-IP-CDT-009** | **ProductDefinition** 须能解析 `category_code`（字典可译）；缺失 → **整次查询失败** | 02 |
| **BR-IP-CDT-010** | **合计区**：费用大类小计、category 小计、总合计均按 **`total_price` 净额**（含退费行负金额）；与行级展示单价/行合计**解耦** | 02 |
| **BR-IP-CDT-011** | **汇总数量**：按产品QueryService `package_unit_ratio` 等将各行 `quantity_value`/`quantity_unit` 换算到**包装单位**；换算链 **6 位小数 HALF_UP**（与 `ProductDefinition` QueryService §多单位倍数一致）；输出 `packageQuantity`（整数）+ 可选 `splitUnitQuantity` | 02 |
| **BR-IP-CDT-012** | **汇总行** `unit_price` 取组内任一条；`lineTotalAmount`（或等价字段）为组内 `total_price` **净额合计**；`unit_price` **不**因净额为 0 而改写 | 02 |
| **BR-IP-CDT-013** | **退费标识**：`REFUNDING`/`REFUNDED` 或业务约定的退费行须在明细行上可区分；原单与退单**均保留**在明细列表 | 02 |
| **BR-IP-CDT-014** | **不支持** `encounterIds` 批量；一次请求仅服务**当前选中患者** | 02 |
| **BR-IP-CDT-017** | 读侧排除反结/半退再生链上已被有效叶替代的祖先 `SETTLED` 行（**BR-REF-020** / **BR-IP-RVS-011**）；多轮「结算→反结」后「已结算」筛选**不展示**已被对冲的历史行；经 `InpatientNurseStationCostDetailLoader` 固定 `excludeSupersededSettledLines=true` | 02 |
| **BR-IP-CDT-018** | **汇总展开**：`presentationMode=SUMMARY` 时每条汇总行返回 `activityLines`；组内按**业务时间倒序**；`generate_source ∈ {nurse-pricing}`（及历史 Charge 上 `auto-roll-fees`）→ 活动类型 **划价**；**床位滚费**（Charge `generate_source=order-execute` 且锚点 SR `generate_source=auto-roll-fees`）→ 活动类型 **自动计费**；其余 → **执行**；**操作人**：诊疗/滚费优先 Procedure 主执行人，耗材优先 DeviceDispense `performer_id`（空则 `preparer_id`）；**禁止**再读收费行操作人戳；**冲销/取消执行/取消自动计费**判定：`REFUNDING`/`REFUNDED`、`refund_id` 非空、或 `quantity_value`/`total_price` 为负 → `cancelHighlight=true`；护士划价负向行活动类型展示 **「冲销」**，其余取消类为对应「取消*」文案；**明细视图来源列**：床位滚费覆盖「自动计费」；护士划价冲销行覆盖「护士冲销」 | 02 |
| **BR-IP-CDT-019** | **项目关键字**（可选）：对展示用产品 **名称**（`productName`）与 **院内编码**（`productBusNo`）做**忽略大小写包含**匹配，命中**任一**即保留；空白不过滤；组套子项按**父项**产品匹配；明细/汇总/合计均基于过滤后结果集 | 02 |
| **BR-IP-CDT-020** | **嘱托不计费展示排除**（承接 **BR-IP-ORD-028⑤** / **BR-IP-EXE-033**）：展示产品 `category_code=52` 或产品名精确「嘱托」的 Charge 行**不进入**明细/汇总/合计；覆盖存量误建账单；新执行写路径禁止再建 | 02 |
| **BR-IP-CDT-021** | **费用清单/明细单打印所见即所打**：打印入口复用 IF-IP-CDT-02，查询参数须与费用明细页当前筛选一致（`dateFrom`/`dateTo`/`statusEnum`/`anesthesiaStatisticsOnly`；清单=`SUMMARY`、明细单=`DETAIL`）。**禁止**传 `excludeOffsetPairs`、`printExpandPackageChildren`（API 可保留给其它调用方，护士站本入口不得开）。单头：`费用总额`=`totals.grandTotal`，`预缴金额`=`totals.prepayNetBalance`，`预缴余额`=`预缴金额 − 费用总额`（**禁止**再用患者队列 `availableBalance` 拼单头，避免总额与余额各算各的） | 打印（复用 02） |

---

## 2. 页面与接口总览（契约概览）

| IF 编号 | 用户动作 | 接口语义 | 读/写 | 关键规则 | 状态变化 | 涉及领域 | 查询服务 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| IF-IP-CDT-01 | 进入费用明细 Tab / 首次打开 | 下发默认**当日**日期区间与 presentation 枚举说明 | 读 | BR-IP-CDT-002 | 无 | — | — |
| IF-IP-CDT-02 | 调整日期区间、费用状态、**项目关键字**、切换明细/汇总 Tab、点**查询**；**打印费用清单/明细单**（同参复用） | 查询住院费用（明细列表 **或** 汇总列表 + **合计块**） | 读 | BR-IP-CDT-001～021 | 无 | ChargeItem、Procedure、ProductDefinition | ProductDefinition（读）；ChargeItem 业务表 |

> **合并说明**：切换「明细 / 汇总」与调整日期后点查询，均为 **IF-IP-CDT-02**，通过 **presentationMode**（`DETAIL` \| `SUMMARY`）区分响应行形态；**合计块**两种模式均返回（统计口径一致，均为净额）。

---

## 3. 缺口跟踪（Gap）

| Gap ID | 缺口类型 | 文件 + 章节 | 缺什么 / 不一致点 | 关闭条件 | 影响 IF | 优先级 | 状态 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| GAP-IP-CDT-001 | DM 能力 | `DM-ADM-011` §6.2.15、`ChargeItemQueryService` | 缺住院费用明细专用查询（业务日期 + 状态白名单 + 挂号排除 + 净 Procedure 业务时间） | `listInpatientNurseStationCostDetailChargeRows` + `ChargeItemQueryCommand.businessDateFrom/To` 已实现 | IF-IP-CDT-02 | P0 | **已关闭** |
| GAP-IP-CDT-002 | DM 能力 | `DM-CLN-004` §6.2.8～6.2.9、`ProcedureQueryService` | 缺 `procedure_id` 批量加载与净剩 COMPLETED 业务时间索引 | `listByIds` + `mapNetCompletedOccurrenceTimeByEncounterId` + `ProcedureRepository#findAllByIdIn` 已实现 | IF-IP-CDT-02 | P0 | **已关闭** |

> **说明**：
> - **ProductDefinition** `category_code`、包装/拆零比字段已在QueryService §2 定义；App 批量 `getByProductIds` 类方法若已存在则**不入 Gap**，仅在 SDD 引用 **Q-PD** 批量契约。
> - **组套父项 `product_id` 解析**（`parent_id` 链）、**汇总数量换算**、**合计聚合**属应用层编排，**不入 Gap**。
> - **`InpatientChargeDetail` QueryService**本期**刻意不使用**，**不入 Gap**。
> - 业务日期过滤在 ChargeItem **状态下推**之后、按**净剩 Procedure / 划价 occurrence_time** 做闭区间过滤（§6.2.15）；禁止 App 全表加载后替代本方法。

### 3.1 Gap 关闭落点（代码与文档，2026-05-24）

| Gap ID | 域文档 | Java 落点（`zxplat`） | 关键契约 |
| --- | --- | --- | --- |
| GAP-IP-CDT-001 | `DM-ADM-011` §6.2.15 | `ChargeItemQueryService#listInpatientNurseStationCostDetailChargeRows` | `businessDateFrom/To`；默认状态四态 + 排除 `adm_healthcare_service`；返回 `InpatientNurseStationCostDetailChargeRow` |
| GAP-IP-CDT-002 | `DM-CLN-004` §6.2.8～6.2.9 | `ProcedureQueryService#listByIds`；`#mapNetCompletedOccurrenceTimeByEncounterId` | 净剩 COMPLETED；`ProcedureRepository#findAllByIdIn` |

> **应用层待 SDD 成码**：`inpatient/nursestation/costdetail`（或项目约定包名）AppService、HTTP、DTO、Mapper、汇总数量 Assembler。

---

## 4. 接口编排说明书（业务步骤）

### IF-IP-CDT-01 费用明细页初始化

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 用户进入费用明细 Tab，或左侧切换患者后首次进入本 Tab |
| **主路径步骤** | 计算服务端**当日** `dateFrom=dateTo=today` → 返回默认日期区间 → 返回 `presentationMode` 可选值（`DETAIL`/`SUMMARY`）及费用大类块顺序说明（药品→诊疗→耗材） |
| **关键分支** | 无患者选中时由前端拦截，不调用本 IF；若调用且缺 `encounterId` 由 IF-02 Gate 拒绝 |
| **失败分支** | 无业务失败（纯配置下发） |
| **查询侧说明** | 纯读 |

### IF-IP-CDT-02 查询住院费用（明细 / 汇总）

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 左侧已选**单就诊**；用户设定 `dateFrom`/`dateTo`（默认当日）、`statusEnum`、可选 `keyword` 与 `presentationMode`；点**查询** |
| **主路径步骤** | 校验 **BR-IP-CDT-001、002、014**（可见性、日期、单就诊）→ **`ChargeItemQueryService#listInpatientNurseStationCostDetailChargeRows`**（**BR-IP-CDT-004/005/015**：状态 + 挂号排除 + 净 Procedure/划价业务时间 + 日期闭区间 + 倒序）→ 批量加载 **ProductDefinition** → 校验 **product_id**、**category_code**（**BR-IP-CDT-007、009**）→ 组套归并父项 **product_id**（**BR-IP-CDT-006**）→ **可选关键字过滤**（**BR-IP-CDT-019**：名称/院内编码模糊）→ **若 `DETAIL`**：组装明细行（原样 `unit_price`/`total_price`、业务时间、来源、状态、退费标识 **BR-IP-CDT-013**）→ **若 `SUMMARY`**：按父项 `product_id` 分组 → 净额合计 `total_price`（**BR-IP-CDT-010、012**）→ 数量换算（**BR-IP-CDT-011**）→ 填 `unit_price` → 装配组内 `activityLines`（**BR-IP-CDT-018**）→ 按 **service_table** 落块顺序输出（**BR-IP-CDT-008**）→ 计算**合计块**（大类/category/总计，**净额 BR-IP-CDT-010**）→ 返回 |

> **打印（BR-IP-CDT-021）**：护士站「打印费用清单 / 打印费用明细单」与打印中心同入口须与本页当前筛选**同参复查** IF-IP-CDT-02，纸面行与合计对齐屏上「总合计」。**禁止**为打印另开 `excludeOffsetPairs` / `printExpandPackageChildren`（历史曾因此把清单总额打偏、预缴余额仍跟队列欠费）。API 可选入参仍保留：其它单据（如国基收费小票）可按院点文档传 `printExpandPackageChildren`；**费用清单/明细单不传**。
| **关键分支** | 查询结果为空：返回空列表 + 合计为 0；`presentationMode` 非法 → EX-IP-CDT-201 |
| **失败分支** | 就诊不可见 → EX-IP-CDT-102；日期非法 → EX-IP-CDT-202；缺 product_id / category / 业务时间 / 未知 service_table → EX-IP-CDT-203；域查询异常 → EX-IP-CDT-101 |
| **查询侧说明** | 纯展示；划价/执行/结算成功后由用户刷新 |

---

## 5. 业务异常与用户提示

| 异常 ID | 场景 | 用户提示 | 可重试 |
| --- | --- | --- | --- |
| EX-IP-CDT-101 | 费用数据加载失败 | 费用明细加载失败，请稍后重试 | 是 |
| EX-IP-CDT-102 | 就诊不在护士站可见范围 | 当前患者不可用，请刷新左侧列表 | 是 |
| EX-IP-CDT-201 | `presentationMode` 非 DETAIL/SUMMARY | 查询参数错误，请刷新页面 | 否 |
| EX-IP-CDT-202 | 日期区间非法（起止倒置或未传） | 请选择有效的查询日期 | 是 |
| EX-IP-CDT-203 | 账单缺产品/分类/业务时间或表名无法归类 | 费用数据不完整，请联系管理员核对收费项目配置 | 否 |

---

## 6. 事件语义与消费方

| 事件 | 来源 | 对本 Tab 的影响 |
| --- | --- | --- |
| `NursePricingChargeItemCreatedEvent` | **AS-IP-NUR-005** | 新增待结算划价行；刷新后明细可见 |
| `ChargeItemActivatedByExecutionEvent` / 执行相关 Charge 事件 | **AS-IP-NUR-004** | 执行入账待结算行增加 |
| `InpatientChargeItemsSettledEvent` / 退费类 ChargeItem 事件 | 住院结算/退费（后续 FIN AS） | 状态→已结算/退款中/已退款；明细原退单均可见，汇总与合计净额变化 |

> 本 Tab **不发布**领域事件；消费为**护士手动刷新**或 Tab 内重新查询，**不要求**实时推送。

---

## 7. 非功能性需求

| 维度 | 要求 |
| --- | --- |
| **性能** | 单就诊 + 日期区间内全量返回；ChargeItem 查询须带状态与 `service_table` 下推；Procedure、ProductDefinition **批量 IN**，禁止 N+1 |
| **一致性** | 只读；展示以查询时刻 `adm_charge_item` 为准（最终一致结算事件延迟可接受，刷新可收敛） |
| **权限** | 与 **AS-IP-NUR-001** 管理病区在院患者可见性一致 |
| **金额刻度** | 域金额字段与展示换算 **6 位小数 HALF_UP**；包装数量整数与小单位字段分离（**BR-IP-CDT-011**） |

---

## 8. 测试要点（业务视角）

| 类别 | 要点 |
| --- | --- |
| **范围** | 仅待结算/已结算/退款中/已退款；不含草稿、待收费；不含挂号锚点 |
| **关键字** | 名称命中；编码命中；名称+编码 OR；空白不过滤；合计随过滤结果变化 |
| **日期/净执行** | 默认当日；跨日区间；医嘱/净剩划价诊疗按净剩 Procedure **`recorded_time`** 日；划价诊疗冲销留痕按原 COMPLETED **`recorded_time`** 日成对可见；耗材划价按发放展示日；滚费按 `occurrence`；医嘱执行已对冲 COMPLETED 不得作客户可见时间 |
| **明细** | 一行一 charge；原单+退单同屏；退单醒目标识；时间倒序；行单价/行合计不因净额统计而篡改 |
| **汇总** | 同 product 合并；组套仅父项；块顺序药→诊→材；包装整数 + 不足一包小单位字段；单价展示、净额行合计；**可展开** `activityLines` 查看每次执行/划价（时间、操作人、数量、总价）；取消类 `cancelHighlight` 标红 |
| **合计** | 两 Tab 均返回；大类/category/总计为净额；退光后合计为 0 但汇总行仍可展示单价 |
| **打印** | 同日期/状态打清单：纸面费用总额=屏上总合计；预缴余额=预交金−费用总额；不传冲销成对剔除/检验子项展开 |
| **异常** | 缺 category、缺 product_id、procedure 缺失 → 整单失败提示 |
| **非目标** | 多患者；InpatientChargeDetail 路径；导出 |

---

## 9. 关联文档与变更历史


### 9.2 变更历史

| 版本 | 日期 | 变更摘要 |
| --- | --- | --- |
| v1.18 | 2026-10-17 | **代码先行回写**：**BR-IP-CDT-021** 费用清单/明细单打印所见即所打——与页同参复用 IF-02，禁止打印另开 `excludeOffsetPairs`/`printExpandPackageChildren`；单头预缴余额=`预交金−费用总额`；对齐前端 `buildFeeListPrintSource` / `buildFeeDetailPrintSource` |
| v1.17 | 2026-10-15 | **代码先行回写**：汇总展开 `activityLines` 增补 `totalPrice`（账单行合计原样）；前端展开子表增加「总价」列；对齐 **AS-SDD v1.15** |
| v1.16 | 2026-10-11 | **代码先行回写**：**BR-IP-CDT-016** 划价诊疗冲销留痕例外——Procedure 已对冲时仍用原 COMPLETED.`recorded_time` 进窗，保证「划价」+「护士冲销」成对可见（对齐 **InpatientNurseStationCostDetailLoader** / **BR-IP-CDT-018**）；医嘱执行路径仍静默跳过 |
| v1.15 | 2026-10-06 | IF-IP-CDT-02 `printExpandPackageChildren` 收窄为**仅检验**子账单展开；检查/治疗仍父项。院点见 `docs/projects/project-H22010202493/application/print/README.md` |
| v1.14 | 2026-10-06 | IF-IP-CDT-02 可选 `printExpandPackageChildren`（默认 false）：业务打印出带子项诊疗子账单；页面 Tab 不传。院点用法见 `docs/projects/project-H22010202493/application/print/README.md` |
| v1.13 | 2026-08-06 | **客户可见时间单轴**：进窗/排序/列 = `recorded_time`（耗材=发放；滚费=occurrence）；`occurrence` 对用户无感；废止「过滤用 occurrence、列用 recorded」双轨；对齐 **InpatientNurseStationCostDetailLoader** |
| v1.12 | 2026-08-06 | §1.3 **展示时间**：护士划价有 Procedure 用 `recorded_time`；耗材划价用发放时刻；过滤仍 occurrence；对齐 **AS-IP-NUR-005 v2.14** / **BR-IP-PRC-030** |
| v1.11 | 2026-07-20 | **BR-IP-CDT-018**：护士划价冲销负向行活动类型「冲销」、明细来源列「护士冲销」；对齐 IF-IP-PRC-06 UI「冲销」；`NurseStationCostDetailReadAssembler` |
| v1.10 | 2026-07-18 | **BR-IP-CDT-020**：费用明细排除嘱托活动账单（`category_code=52` / 名「嘱托」）；对齐 **BR-IP-EXE-033** / **BR-IP-ORD-028⑤** |
| v1.9 | 2026-07-17 | **BR-IP-CDT-018**：床位滚费（order-execute + auto-roll-fees SR）活动类型/来源展示「自动计费」；取消类文案含「取消自动计费」 |
| v1.8 | 2026-07-17 | §1.3 **展示时间双轨**：过滤/排序仍用 occurrence（业务时间）；明细列/`activityLines` 优先 `displayDateTime`=`recorded_time`；对齐 **AS-IP-NUR-004** 展示=执行、逻辑=计划 |
| v1.7 | 2026-07-17 | **BR-IP-CDT-019**：IF-IP-CDT-02 可选 `keyword`，按项目名称/院内编码模糊过滤；明细/汇总/合计同步 |
| v1.6 | 2026-07-16 | **BR-IP-CDT-018**：汇总行增加可展开 `activityLines`（组内执行/划价明细；取消类标红）；前端汇总表支持展开子表 |
| v1.4 | 2026-07-09 | **BR-IP-CDT-003**：`auto-roll-fees` 业务时间对齐账单 `occurrence_time`（与护士划价同路径）；修复滚费行在费用明细 Tab 不可见/整单失败 |
| v1.3 | 2026-06-20 | **support 落点对齐**：`InpatientNurseStationCostDetailLoader` → `module.querysupport.nursestation`；详见 **AS-IP-NUR-006-SDD** |（已迁至 application.inpatient.nursestation.{feature}.support）（已迁至 application.shared / feature support（见 DISSOLUTION.md））
| v1.2 | 2026-05-28 | 读模型废止后对齐 zxplat 实现（EncounterContextFacade / querysupport Loader）。 |（已迁至 EncounterContextReader / application.shared.encounter）
| 类型 | 文档 |
| --- | --- |
| 关联 AS | `AS-IP-NUR-001`；`AS-IP-NUR-004`；`AS-IP-NUR-005`；`AS-IP-DOR-003`；`AS-IP-FIN-002` |
| DM | `DM-ADM-011` §6.2.15；`DM-CLN-004` §6.2.8～6.2.9；`DM-ADM-002` |
| 查询服务 | `DM-MED-001 / DM-WKF-001 / DM-ADM-008 §6.2 目录 QueryService`（**禁止**本场景使用 `InpatientChargeDetail住院费用明细（DM §6.2 QueryService）`） |
| AS-SDD | `AS-IP-NUR-006-住院护士站费用明细-SDD.md` |
| UI | `openhis-pro-ui/.../inpatient/nurse-station/cost-details/` |

### 9.1 域层已实现能力对照（Gap 关闭后）

| 能力 | 域入口 | 文档锚点 | 用途 IF |
| --- | --- | --- | --- |
| 费用明细候选行 | `ChargeItemQueryService#listInpatientNurseStationCostDetailChargeRows` | DM-ADM-011 §6.2.15 | IF-IP-CDT-02 |
| 净剩执行时刻索引 | `ProcedureQueryService#mapNetCompletedOccurrenceTimeByEncounterId` | DM-CLN-004 §6.2.9 | IF-IP-CDT-02 |
| Procedure 批量主键读 | `ProcedureQueryService#listByIds` | DM-CLN-004 §6.2.8 | IF-IP-CDT-02（关联扩展） |
| 产品QueryService | `MedicationDefinitionQueryService` 批量 | ProductDefinition §3 | IF-IP-CDT-02 |

> **应用层成码**：见 `AS-IP-NUR-006-住院护士站费用明细-SDD.md`（`inpatient/nursestation/costdetail`）。

| 版本 | 日期 | 摘要 |
| --- | --- | --- |
| v1.2.1 | 2026-05-27 | **BR-IP-CDT-016** Procedure 对冲孤儿行：闭区间外跳过；**BR-IP-CDT-008** 增补 `wkf_device_dispense` 耗材映射 |
| v1.1 | 2026-05-24 | **BR-IP-CDT-015** 净剩 Procedure 业务时间；关闭 **GAP-IP-CDT-001/002**；对齐 `DM-ADM-011` §6.2.15、`DM-CLN-004` §6.2.8～6.2.9 |
| v1.0 | 2026-05-24 | 首版：IF-IP-CDT-01～02；明细/汇总 + 净额合计；业务表 + ProductDefinition；明确不用 InpatientChargeDetail |
