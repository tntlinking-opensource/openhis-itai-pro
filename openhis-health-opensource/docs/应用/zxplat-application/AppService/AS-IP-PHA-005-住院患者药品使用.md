# 住院患者药品使用 · 应用服务设计（AS）

> **隔离声明（2026-07-21）**：本页为 **用药明细 / 患者药品使用**（计划发药时间轴、可含待发），与报表中心 **住院发药报表（AS-RPT-004，由发退药记录演进）** **两码事**，禁止合并或互相替代。族边界见 `health/设计决策/DD-报表中心-发药耗材挂号费用汇总明细族.md`。
>
> **文档职责**：发药管理下「**住院患者药品使用**」查询页契约——按**单就诊**、**计划发药时间**（执行时间）闭区间查询住院药品发放事实；**明细**展示发药状态（待发/已发/已退）与**账单金额**；**汇总**按药品取**净用量/净金额**（用了减退了），**不**体现发药状态、**不**体现是否汇总发药。**纯读**。
>
> **页面边界**：选患者 → 设日期区间 → 切换「明细 / 汇总」→ **查询**。患者上下文由本页检索选定；住院发药/退药/发退药记录写读路径分属 **AS-IP-PHA-001～004**（发退药记录目标态见 **AS-RPT-004**）。
>
> **编码**：AS-IP-PHA-005 · **版本**：v1.2 · **日期**：2026-08-06
>
> **UI 交互清单（当期产品裁决，页面前端待建）**：① 进入本菜单；② 关键字检索并**单选**住院患者；③ 调整执行日期区间（默认当日）、切换明细/汇总、点查询；④ 查看明细行（含待发/已发/已退与金额）或汇总净用量。下列 IF 与该清单一一对应；**禁止**从领域状态机反向补造交互。

---

## 1. 场景概述

### 1.1 目标与边界

| 维度 | 说明 |
| --- | --- |
| **在内** | IF-IP-PMU-01 页初始化（默认当日日期区间、展示模式与发药状态展示值集）；IF-IP-PMU-02 关键字分页检索住院患者（单选前置）；IF-IP-PMU-03 按就诊 + 计划发药时间区间查询（**presentationMode** = 明细 \| 汇总），含行列表与合计净额 |
| **在外** | 住院按患者发药（**AS-IP-PHA-001**）；住院汇总发药（**AS-IP-PHA-002**）；住院退药（**AS-IP-PHA-003**）；住院发退药记录（**AS-IP-PHA-004**，仅已发/已退事实、时间轴为发药时间）；护士站费用明细（**AS-IP-NUR-006**，含诊/材、费用结算态）；多患者合并查看；导出/打印；库房/药房管辖收窄（本期不做） |
| **涉及域** | MedicationDispense；ChargeItem；MedicationDefinition / ProductDefinition；Encounter / InpatientEncounterContext；Patient |
| **编排权威** | 以本文 **§2、§4** 及当期产品裁决为准；读侧以 **MedicationDispense** 为行锚点，金额挂 **ChargeItem** 账单事实 |

### 1.2 角色与关键动作

| 角色 | 关键动作 |
| --- | --- |
| **住院药房人员** | 关键字检索 → **单选**住院患者 →（可选）调整执行日期区间 → 切换「明细 / 汇总」→ **查询** → 查看药品使用流水或净用量 |
| **系统** | 按就诊加载发放行 → 以 **计划发药时间** 闭区间过滤 → 映射展示态待发/已发/已退 → 批量挂接账单单价/金额 → 明细排序或按药品净额汇总 |

### 1.3 术语与口径约定

| 术语 | 口径 |
| --- | --- |
| **药品使用** | 医嘱**执行后**产生并可纳入计划发药时间轴的发放事实即计入「使用」；与是否已在药房核发完成**无关**（待发亦计入使用）。退药表示**不再使用**，汇总与合计须**冲减** |
| **执行时间 / 计划发药时间** | **过滤与明细排序**仍为 `MedicationDispense.planned_dispense_time`（本地日历日闭区间；**空值不入窗**）。**明细列客户可见时间**优先 `Procedure.recorded_time`（经 `dispense.procedure_id` 挂接），无 recorded 时回落 planned；与医嘱执行/汇总领药双轨一致 |
| **纳入发放状态（事实态）** | `PREPARATION`、`PREPARED`、`IN_PROGRESS`、`SUMMARIZED`、`PART_COMPLETED`、`COMPLETED`、`PENDING_REFUND`、`PART_REFUND`、`REFUNDED`；**不含** `DRAFT`、`DECLINED`、`STOPPED`、`CANCELLED`、`ON_HOLD` |
| **展示发药状态（仅明细）** | 三态文案：**待发** / **已发** / **已退**。映射：`PREPARATION`∪`PREPARED`∪`IN_PROGRESS`∪`SUMMARIZED`∪`PART_COMPLETED` → **待发**；`COMPLETED` → **已发**；`PENDING_REFUND`∪`PART_REFUND`∪`REFUNDED` → **已退**。**不**单独展示「已汇总」等中间文案 |
| **发放类型** | 正向：`dispense_enum ∈ { INPATIENT, TEMPORARY }`；退药：`dispense_enum = REFUND`（与 `REFUNDED`/`PENDING_REFUND`/`PART_REFUND` 同屏明细） |
| **明细粒度** | **一行 = 一条 MedicationDispense**；原发与退药行**均展示**；退药行金额按账单为负（或约定符号），须可区分 |
| **汇总粒度** | 按 **`medication_id`（药品定义）** 分组；取数量与金额**净额**（正向计入、退药冲减）；**不返回**发药状态；**不**按 `summary_no` 分桶；**不展示**计划发药时间 |
| **金额** | **一律来自账单 ChargeItem**（单价、行合计）；读侧锚点口径同住院发药/发退药记录价装载族（发放锚点 + 住院待结算补充）；**不看**费用结算状态是否已结；无账单时单价/金额可空，**不得**捏造 |
| **行级展示 vs 统计净额** | **明细行**单价/行合计按账单事实**原样**；**汇总行金额**与**合计区**对行合计**轧差净额** |
| **汇总数量** | 组内数量统一换算到包装单位（与护士站费用明细药品汇总同族：满包整数 + 不足一包小单位）；退药数量参与净额冲减 |
| **单就诊** | 仅接受当前选中 `encounterId`（+ `patientId` 交叉校验）；**禁止** `encounterIds` 批量 |
| **库房范围** | **本期不控制**：不按管辖药房 / `location_id` 收窄 |
| **默认日期** | 服务端下发 **当日** `[dateFrom, dateTo]`（本地日历日，闭区间） |

### 1.4 本期范围与非目标

| 类别 | 内容 |
| --- | --- |
| **本期** | IF-IP-PMU-01～03 |
| **非目标** | 多患者合并；导出/打印；写路径（发药/退药/作废）；耗材；按长期/临时分流页签；库房管辖收窄；护士站费用大类（诊/材）；替换 **AS-IP-PHA-004**；分页明细（单就诊区间内全量返回，数据量过大由 SDD 评估上限） |

### 1.5 领域清单（聚合 + 职责 + DM 路径）

| 聚合（域） | 职责摘要 | DM 路径 |
| --- | --- | --- |
| **MedicationDispense** | 使用行事实、计划发药时间、发放状态、数量、药品标识 | `DM-MED-002` |
| **ChargeItem** | 账单单价、行金额 | `DM-ADM-011` |
| **MedicationDefinition / ProductDefinition** | 名称、规格、包装/拆零、分类 | `DM-MED-001` / 目录 QueryService |
| **Encounter / InpatientEncounterContext** | 患者检索、住院号/床号/病区、就诊门禁 | `DM-ADM-002` §6.2.18 |
| **Patient** | 姓名等检索辅助 | `DM-ADM-001` |

### 1.6 业务规则（BR-*）

| 规则 ID | 描述 | 绑定 IF |
| --- | --- | --- |
| **BR-IP-PMU-001** | `encounterId`、`patientId` 必填且交叉一致；须为住院（IMP）就诊 | IF-IP-PMU-03 |
| **BR-IP-PMU-002** | `dateFrom`、`dateTo` 必填；`dateFrom ≤ dateTo`；默认均为**当日** | IF-IP-PMU-01、03 |
| **BR-IP-PMU-003** | 时间窗仅按 `planned_dispense_time` 本地日闭区间；空值不入结果集 | IF-IP-PMU-03 |
| **BR-IP-PMU-004** | 仅纳入 §1.3「纳入发放状态」集合；条件须在**查询层下推** | IF-IP-PMU-03 |
| **BR-IP-PMU-005** | 仅药品发放；正向 `INPATIENT`/`TEMPORARY` + 退药 `REFUND`；排除逻辑删除 | IF-IP-PMU-03 |
| **BR-IP-PMU-006** | **明细**：展示三态待发/已发/已退（§1.3 映射）；按 `planned_dispense_time` **倒序**；行金额账单原样；**客户可见时间列**优先 `recorded_time`（回落 planned） | IF-IP-PMU-03 |
| **BR-IP-PMU-007** | **汇总**：按 `medication_id` 净数量/净金额；**不**出发展示发药状态；**不**按是否汇总发药分列 | IF-IP-PMU-03 |
| **BR-IP-PMU-008** | 合计区为药品使用**净金额**；明细/汇总两种模式合计口径一致 | IF-IP-PMU-03 |
| **BR-IP-PMU-009** | **不**按 `location_id` / 管辖药房收窄 | IF-IP-PMU-02、03 |
| **BR-IP-PMU-010** | 患者检索：关键字模糊姓名 / 住院号 / 床号；默认在院态（`IN_PROGRESS`∪`ON_HOLD`）；分页；用户**单选**后方可查使用 | IF-IP-PMU-02 |
| **BR-IP-PMU-011** | **不支持**多就诊批量查询 | IF-IP-PMU-03 |
| **BR-IP-PMU-012** | 金额装配失败（缺账单）时行单价/金额可空，**不得**阻断整次查询（与发退药记录空价口径同族） | IF-IP-PMU-03 |

---

## 2. 页面与接口总览（契约概览）

| IF 编号 | 用户动作 | 接口语义 | 读/写 | 关键规则 | 状态变化 | 涉及领域 | 查询服务 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| IF-IP-PMU-01 | 进入「住院患者药品使用」页 | 下发默认当日日期区间、明细/汇总值集、展示发药状态说明 | 读 | BR-IP-PMU-002 | 无 | — | — |
| IF-IP-PMU-02 | 关键字检索/翻页选患者 | 分页检索住院在院患者候选 | 读 | BR-IP-PMU-009、010 | 无 | Encounter / Patient | `EncounterContextReader` / 住院就诊上下文 |
| IF-IP-PMU-03 | 调整日期、切换明细/汇总、点**查询** | 查询该患者药品使用（明细列表 **或** 汇总列表 + **合计净额**） | 读 | BR-IP-PMU-001～012 | 无 | MedicationDispense、ChargeItem、MedicationDefinition | `MedicationDispenseQueryService#listInpatientPatientMedicationUsageDispenseRows`；`ChargeItemQueryService`；产品批量 |

> **合并说明**：切换「明细 / 汇总」与调整日期后点查询，均为 **IF-IP-PMU-03**，通过 **presentationMode**（`DETAIL` \| `SUMMARY`）区分响应行形态；**合计净额**两种模式均返回。

---

## 3. 缺口跟踪（Gap）

**无待跟踪缺口。**

| Gap ID | 缺口类型 | 文件 + 章节 | 缺什么 / 不一致点 | 关闭条件 | 影响 IF | 优先级 | 状态 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| GAP-IP-PMU-001 | QueryService 能力 | `DM-MED-002` §6.2.21、`MedicationDispenseQueryService` | 缺按单就诊 + `planned_dispense_time` 闭区间 + 纳入状态白名单 + 住院正向/退药类型的使用行列表方法 | `listInpatientPatientMedicationUsageDispenseRows` + Query/Row 已实现并与 DM §6.2.21 一致 | IF-IP-PMU-03 | P0 | **已关闭** |

### 3.1 Gap 关闭落点（代码与文档，2026-07-15）

| Gap ID | 域文档 | Java 落点（`zxplat`） | 关键契约 |
| --- | --- | --- | --- |
| GAP-IP-PMU-001 | `DM-MED-002` §6.2.21 | `MedicationDispenseQueryService#listInpatientPatientMedicationUsageDispenseRows`；`InpatientPatientMedicationUsageDispenseQuery`；`InpatientPatientMedicationUsageDispenseRow` | 单就诊；`planned_dispense_time` 闭区间；默认使用态白名单；默认 `INPATIENT`/`TEMPORARY`/`REFUND`；不按库房收窄 |

> **说明**：应用层 AppService/Controller/DTO **不是** Gap，由 SDD 驱动成码。

---

## 4. 接口编排说明书（业务步骤）

### IF-IP-PMU-01 住院患者药品使用页初始化

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 用户进入本菜单页 |
| **主路径步骤** | 计算服务端**当日** `dateFrom=dateTo=today` → 返回默认日期区间 → 返回 `presentationMode` 可选值（`DETAIL`/`SUMMARY`）→ 返回展示发药状态三态说明（待发/已发/已退） |
| **关键分支** | 无患者选中时由前端拦截，不调用 IF-IP-PMU-03 |
| **失败分支** | 无业务失败（纯配置下发）；基础设施异常 → EX-IP-PMU-101 |
| **查询侧说明** | 纯读 |

### IF-IP-PMU-02 分页检索住院患者

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 用户输入关键字（可空）并检索/翻页 |
| **主路径步骤** | 校验分页参数 → 按 **BR-IP-PMU-010** 分页加载在院住院上下文（姓名/住院号/床号模糊）→ **不**按药房管辖收窄 → 返回候选行（就诊、患者、住院号、病区、床号等） |
| **关键分支** | 无命中返回空页 |
| **失败分支** | EX-IP-PMU-101 |
| **查询侧说明** | 只读；选定 `encounterId`/`patientId` 后供 IF-IP-PMU-03 使用 |

### IF-IP-PMU-03 查询住院患者药品使用（明细 / 汇总）

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 已单选就诊；设定 `dateFrom`/`dateTo`（默认当日）与 `presentationMode`；点**查询** |
| **主路径步骤** | 校验 **BR-IP-PMU-001、002、011** → 领域列表查询使用行（**BR-IP-PMU-003～005**：计划发药时间窗 + 状态白名单 + 发放类型）→ 批量装载药品定义 → 批量挂接 **ChargeItem** 账单价（**BR-IP-PMU-012**）→ **若 DETAIL**：组装明细（三态展示、时间倒序、金额原样 **BR-IP-PMU-006**）→ **若 SUMMARY**：按 `medication_id` 净数量/净金额（**BR-IP-PMU-007**）→ 计算合计净额（**BR-IP-PMU-008**）→ 返回 |
| **关键分支** | 结果为空：空列表 + 合计 0；`presentationMode` 非法 → EX-IP-PMU-201 |
| **失败分支** | 就诊/患者不一致或不存在 → EX-IP-PMU-102；日期非法 → EX-IP-PMU-202；域查询异常 → EX-IP-PMU-103 |
| **查询侧说明** | 纯展示；发药/退药成功后由用户重新查询 |

---

## 5. 业务异常与用户提示

| 异常 ID | 场景 | 用户提示 | 可重试 |
| --- | --- | --- | --- |
| EX-IP-PMU-101 | 初始化或患者检索失败 | 页面加载失败，请稍后重试 | 是 |
| EX-IP-PMU-102 | 就诊不存在 / 非住院 / 与 patientId 不一致 | 当前患者不可用，请重新选择 | 是 |
| EX-IP-PMU-103 | 药品使用数据加载失败 | 药品使用查询失败，请稍后重试 | 是 |
| EX-IP-PMU-201 | `presentationMode` 非 DETAIL/SUMMARY | 查询参数错误，请刷新页面 | 否 |
| EX-IP-PMU-202 | 日期区间非法（起止倒置或未传） | 请选择有效的查询日期 | 是 |

---

## 6. 事件语义与消费方

| 事件 | 来源 | 对本页的影响 |
| --- | --- | --- |
| `MedicationDispenseCreatedEvent` / 执行挂接推进待配药 | **AS-IP-NUR-004** 等 | 新增待发行；刷新后明细可见，汇总净用量增加 |
| `MedicationDispensedEvent` / 汇总发药完成 | **AS-IP-PHA-001/002** | 展示态待发→已发；汇总净额不变（仍算使用） |
| `MedicationDispenseRefundedEvent` | **AS-IP-PHA-003** 等 | 新增已退行；汇总与合计净额冲减 |

> 本页 **不发布** 领域事件；消费为**手动重新查询**，**不要求**实时推送。

---

## 7. 非功能性需求

| 维度 | 要求 |
| --- | --- |
| **性能** | 单就诊 + 日期区间内全量返回；发放查询须带状态与时间下推；药品定义、ChargeItem **批量 IN**，禁止 N+1 |
| **一致性** | 只读；展示以查询时刻发放表 + 账单事实为准 |
| **权限** | 报表式只读；本期不按药房管辖收窄；租户隔离由框架承担 |
| **金额刻度** | 与域金额字段一致；汇总数量换算 **6 位小数 HALF_UP** |

---

## 8. 测试要点（业务视角）

| 类别 | 要点 |
| --- | --- |
| **范围** | 含待发（含已汇总未核发）、已发、已退；不含草稿/作废/停止 |
| **时间** | 默认当日；跨日；按 `planned_dispense_time`；空计划时间不入窗 |
| **明细** | 一行一发放；三态映射正确；已汇总行展示为「待发」；金额来自账单；时间倒序 |
| **汇总** | 同药品净数量/净金额；退药冲减；**无**状态列；不区分汇总单 |
| **合计** | 两模式净额一致；退光后合计可为 0 |
| **患者** | 未选患者不可查；关键字命中在院患者 |
| **非目标** | 多患者；库房过滤；导出 |

---

## 9. 关联文档与变更历史

| 类型 | 路径 |
| --- | --- |
| 发放 DM | `docs/领域/DomainModel/DM-MED-002-MedicationDispense-药品发放.md |
| 费用 DM | `docs/领域/DomainModel/DM-ADM-011-ChargeItem-收费明细.md |
| 就诊 DM | `docs/领域/DomainModel/DM-ADM-002-Encounter-就诊.md |
| 关联 AS | `AS-IP-PHA-001`～`004`；`AS-IP-NUR-004`；`AS-IP-NUR-006`（形态对照，非本页读源） |
| AS-SDD | `AppService-SDD/AS-IP-PHA-005-住院患者药品使用-SDD.md` |

| 版本 | 日期 | 摘要 |
| --- | --- | --- |
| v1.2 | 2026-08-06 | **明细展示双轨**：客户可见「执行时间」优先 `Procedure.recorded_time`（经 `dispense.procedure_id`），回落 planned；**过滤/排序仍** `planned_dispense_time`（对齐 NUR-004/010） |
| v1.1 | 2026-07-15 | 关闭 **GAP-IP-PMU-001**：对齐 `DM-MED-002` §6.2.21 与领域 `listInpatientPatientMedicationUsageDispenseRows` |
| v1.0 | 2026-07-15 | 初版：发药管理「住院患者药品使用」；按患者 + 计划发药时间；明细三态 + 账单金额；汇总取净；库房不收窄；登记 **GAP-IP-PMU-001** |
