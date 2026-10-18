# 住院患者入出明细 · 应用服务设计（AS）

> **文档职责**：报表中心「住院患者入出明细」只读契约。按入院科室、入院病区与入/出状态，查询一段时间内的入院/出院就诊（一行一次住院 Encounter），展示患者基本信息、位置（病区/病房/床）及有效已结费用合计；有转科时动态展示转往科室；页脚给出入院/出院人次（按 Encounter）。
>
> **编码**：AS-RPT-010 · **版本**：v1.8 · **日期**：2026-08-14  
> **族边界**：`health/设计决策/DD-报表中心-发药耗材挂号费用汇总明细族.md`（本页为族外增量报表，菜单与应用包仍挂 **报表中心**）  
> **菜单**：报表中心 · 住院患者入出明细  
> **应用包**：`application/report/inoutdetail/`（**应用层已落地**；仅住院，无 outpatient 子包）  
> **前端**：`views/report/inpatient-in-out-detail/` · 菜单 `InpatientInOutDetail`（报表中心）

---

## 1. 场景概述

### 1.1 目标与边界

| 维度 | 说明 |
| --- | --- |
| **在内** | IF-RPT-IOE-01～03：初始化、入出明细分页（含人次页脚与动态转科列元数据）、导出 |
| **在外** | 住院登记/入出转/结算/反结写路径；住院费用明细行级报表（**AS-RPT-009**）；在院名单实时查询（本页**不**提供「在院」状态筛） |
| **涉及域** | **Encounter**（住院入出与转科轨迹）；**Patient**；**Location**（病区/病房/床）；**Organization**；**PaymentReconciliation**（出院结算费用） |
| **编排权威** | 本文；产品裁决见会话收敛（入院科室/入院病区筛选；状态=全部/入院/出院；费用一列；转科动态列） |

### 1.2 角色与关键动作

| 角色 | 关键动作 |
| --- | --- |
| **住院收费 / 财务 / 病案 / 管理** | 按科室·病区·入出状态查区间内入院/出院名单 → 看已结费用与转科 → 看人次合计 → 导出 |

### 1.3 术语与口径约定

| 术语 | 口径 |
| --- | --- |
| **一行** | 一次住院就诊（`class_enum=IMP` 的 Encounter）；同一患者多次住院多行 |
| **入院日** | 住院登记完成时刻（主档 `start_time`） |
| **出院日** | 护士办理出院写入的结束时刻（主档 `end_time`）；未办理出院则空 |
| **状态·入院** | 入院日 ∈ 查询闭区间 |
| **状态·出院** | 出院日 ∈ 查询闭区间（须已有 `end_time`） |
| **状态·全部** | 区间内入院 ∪ 区间内出院（同一 Encounter 仍一行）；人次上入院、出院各计（见 BR） |
| **入院科室** | 办理住院登记时的科室；转科确认后主档 `organization_id` 会切到目标科。筛选：App 预写该科下病区 id → `resolvedAdmissionWardLocationIds`，按**最早 WARD** 命中；未分床用主档 `organization_id` |
| **入院病区** | 登记/首次入科所挂病区（WARD）；**与入院科室联动**（病区挂靠住院科室，见 **DM-ADM-003**）；未选科室时病区不可选；筛选项仅病区，不按病房/床位筛 |
| **位置展示** | 一行只展示一个病区-病房-床：优先当前活动床；无活动床则末次床/病房；未分床可空 |
| **转科列** | 存在转科（**最早入院 WARD 之后仍有其它 WARD 轨迹**，作「已确认转科」代理）时展示转往科室（多次可拼接）；**本页结果集内无人转科则不出现该列**（由 `showTransferColumn` 驱动，按**当前页**计算） |
| **已结费用** | 该 Encounter 下 `kind=INPATIENT_CLINIC` 支付收退净额（SUCCESS 正、REFUNDED 反结/退负）；**净额为 0 或不存在 → 字段空（禁止写 0）**；反结后尚未再结（待重结）→ 空 |
| **费用性质（身份）** | 列表/导出列；与挂号明细同属**入院身份**（就诊优先账户：用什么入院就是什么），**不**跟账单行 `account_id`；勿与 AS-RPT-009 行级费用性质混淆 |
| **人次** | 按 Encounter 计，不按患者去重；页脚至少 **入院人次**、**出院人次** |
| **排除** | `PLANNED`（未登记）、`CANCELLED`（取消登记）、逻辑删除 |
| **时间窗** | 日期（可含时分，SDD 定）闭区间；必填；默认当日 |
| **导出** | 与列表同筛选；触顶 `EXPORT_MAX_ROWS` 截断并回 `truncated`；xlsx **固定含「转往」列**（空单元格）；响应 `showTransferColumn` 按导出行集 |

### 1.4 本期范围与非目标

| 类别 | 内容 |
| --- | --- |
| **本期** | IF-RPT-IOE-01 / 01a / 02 / 03；菜单挂报表中心；单列表 + 页脚人次；科室→病区联动 |
| **非目标** | 「在院」状态筛；按病房/床位筛选；中途结算费用；费用类型拆分；跳转结算/入出转写路径；与 AS-RPT-009 并页 |

### 1.5 领域清单（聚合 + 职责 + DM 路径）

| 聚合 | 职责 | DM |
| --- | --- | --- |
| **Encounter** | 住院入出时间、入院科室/病区、位置轨迹、转科确认 | `DM-ADM-002` |
| **Patient** | 姓名、性别、年龄等人口学展示 | `DM-ADM-001` |
| **Location** | 病区/病房/床名称与层级 | `DM-ADM-003` |
| **Organization** | 入院科室、转往科室名称；科室/病区下拉 | `DM-ADM-004`（或住院登记同源主数据） |
| **PaymentReconciliation** | 出院结算/反结净额；待重结判定 | `DM-FIN-002` |

### 1.6 业务规则（BR-*）

| 规则 ID | 描述 | 绑定 IF |
| --- | --- | --- |
| **BR-RPT-IOE-001** | 日期必填；默认近一个月闭区间（今日往前 1 个自然月～今日） | 01～03 |
| **BR-RPT-IOE-002** | 仅 `IMP`；排除 `PLANNED`/`CANCELLED` | 02、03 |
| **BR-RPT-IOE-003** | 状态三选一：全部 / 入院 / 出院；默认全部 | 01～03 |
| **BR-RPT-IOE-004** | 入院：`start_time∈窗`；出院：`end_time∈窗`；全部=并集去重（按 encounterId） | 02、03 |
| **BR-RPT-IOE-005** | 可选筛：入院科室、入院病区；空=不限 | 01a、02、03 |
| **BR-RPT-IOE-005a** | **科室→病区联动**：病区选项 = 所选入院科室下启用病区（`Location.organization_id` + `form=WARD`）；未选科室 → 病区下拉空且禁用；切换科室 → 清空已选病区并重载选项 | 01、01a、02、03 |
| **BR-RPT-IOE-006** | 一行一 Encounter；位置仅一格当前/末次床位链 | 02、03 |
| **BR-RPT-IOE-007** | 已结费用一列；无有效净额或待重结 → null，禁止 0 | 02、03 |
| **BR-RPT-IOE-007a** | 费用性质一列：入院身份（就诊优先账户合同名）；不跟账单行 `account_id` | 02、03 |
| **BR-RPT-IOE-008** | 列表：**当前页**存在转科（见术语）→ `showTransferColumn=true` 且行上填转往信息；否则不展示转科列 | 02 |
| **BR-RPT-IOE-009** | 页脚：入院人次=窗内入院命中 Encounter 数；出院人次=窗内出院命中数；全部时同一人可同时计入两侧 | 02、03 |
| **BR-RPT-IOE-010** | 导出行集与列表同筛选口径；触顶截断回 `truncated`；xlsx 固定含「转往」列；响应 `showTransferColumn` 按导出行 | 03 |

---

## 2. 页面与接口总览（契约概览）

| IF 编号 | 用户动作 | 接口语义 | 读/写 | 关键规则 | 关键状态变化 | 涉及领域 | 查询服务 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| IF-RPT-IOE-01 | 进入页 | 默认日期、状态选项、入院科室下拉；病区初始为空 | 读 | BR-RPT-IOE-001、003、005a | 无 | Organization | `OrganizationQueryService#listOrganizationsForInpatientAdmission` |
| IF-RPT-IOE-01a | 切换入院科室 | 按科室加载启用病区下拉 | 读 | BR-RPT-IOE-005a | 无 | Location | `LocationQueryService#listWardsByOrganization` |
| IF-RPT-IOE-02 | 查询 | 入出明细分页 + 页脚人次 + `showTransferColumn` | 读 | BR-RPT-IOE-001～009 | 无 | Encounter / Patient / Location / Payment | `EncounterQueryService#pageInpatientInOutDetailReport` / `#countInpatientInOutDetailPersonTimes`；`PaymentReconciliationQueryService#mapInpatientClinicSettledNetAmountByEncounterIds` |
| IF-RPT-IOE-03 | 导出 | 同筛选全量 Excel | 读 | BR-RPT-IOE-001～010 | 无 | 同上 | 同 02（导出上限） |

---

## 3. 缺口跟踪（Gap）

| Gap ID | 缺口类型 | 文件 + 章节 | 缺什么 / 不一致点 | 关闭条件 | 影响 IF | 优先级 | 状态 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| GAP-RPT-IOE-001 | QueryService 能力 | `DM-ADM-002` §6.2.36 | 缺住院入出报表明细分页：按入院日/出院日窗、状态、入院科室、入院病区；含转科目标投影与位置链 | DM 补齐 `pageInpatientInOutDetailReport` + 领域实现 | 02、03 | P0 | **已关闭** |
| GAP-RPT-IOE-002 | QueryService 能力 | `DM-ADM-002` §6.2.37 | 缺入院/出院人次计数（与明细分页同过滤；全部时两侧可重叠计） | DM 补齐 `countInpatientInOutDetailPersonTimes` + 实现 | 02、03 | P0 | **已关闭** |
| GAP-RPT-IOE-003 | QueryService 能力 | `DM-FIN-002` §6.2.20 | 缺按 encounterId 批量映射出院结算净额；并识别待重结（可复用 `filterReversedPendingResettleEncounterIds`） | DM 补齐 `mapInpatientClinicSettledNetAmountByEncounterIds` + 实现 | 02、03 | P0 | **已关闭** |

### 3.1 Gap 关闭落点（2026-07-27）

| Gap ID | DM 章节 | Java 落点 |
| --- | --- | --- |
| GAP-RPT-IOE-001 | `DM-ADM-002` §6.2.36 | `EncounterQueryService#pageInpatientInOutDetailReport`；入院科室经 App 预写 `resolvedAdmissionWardLocationIds`（B.6.4） |
| GAP-RPT-IOE-002 | `DM-ADM-002` §6.2.37 | `EncounterQueryService#countInpatientInOutDetailPersonTimes` |
| GAP-RPT-IOE-003 | `DM-FIN-002` §6.2.20 | `PaymentReconciliationQueryService#mapInpatientClinicSettledNetAmountByEncounterIds`；待重结复用 `#filterReversedPendingResettleEncounterIds` |

> 应用层 AppService/Controller/DTO **不是** Gap；由本 AS 对应 SDD 驱动成码。

---

## 4. 接口编排说明书（业务步骤）

### IF-RPT-IOE-01 页面初始化

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 进入报表中心「住院患者入出明细」 |
| **主路径步骤** | 默认日期当日 → 状态选项（全部/入院/出院，默认全部）→ 住院入院科室下拉 → **病区选项置空**（待选科室后经 01a 加载）→ 返回 |
| **关键分支** | — |
| **失败分支** | EX-RPT-IOE-101 |
| **查询侧说明** | 只读；不对全院病区做平铺下发 |

### IF-RPT-IOE-01a 按入院科室加载病区

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 用户选择或切换入院科室 |
| **主路径步骤** | 校验科室 id → 加载该科下启用病区 → 返回选项；前端清空原病区选中 |
| **关键分支** | 科室清空 → 返回空病区列表 |
| **失败分支** | EX-RPT-IOE-101 |
| **查询侧说明** | `LocationQueryService#listWardsByOrganization`；与住院登记病区挂靠科室同口径 |

### IF-RPT-IOE-02 入出明细分页

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 查询或翻页 |
| **主路径步骤** | 校验日期与状态 → 若选了科室则预解析该科病区 id 写入领域 query → 按状态解析入院/出院时间谓词与科室病区 → 分页加载 Encounter 行 → 装配患者/入院科室病区/位置/转科 → 批量装已结净额（待重结置空）→ 计入院/出院人次页脚 → 置 `showTransferColumn` → 返回 |
| **关键分支** | 科室/病区空=不限；已选科室未选病区=该科全部入院病区（含未分床）；无转科行则隐藏转科列 |
| **失败分支** | EX-RPT-IOE-102/103 |
| **查询侧说明** | 条件下推；禁止把当前 `organization_id` 当作入院科室 |

### IF-RPT-IOE-03 导出

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 导出 |
| **主路径步骤** | 同 02 筛选分批拉全量（至 `EXPORT_MAX_ROWS`）→ Enrich → 写 xlsx（固定含「转往」列）→ 回 `truncated` / `showTransferColumn` |
| **关键分支** | 空表仍可导出表头 |
| **失败分支** | EX-RPT-IOE-102/104 |
| **查询侧说明** | 行上限 SDD 常量 |

---

## 5. 业务异常与用户提示

| 语义代码 | 场景 | 用户提示方向 | 可重试 |
| --- | --- | --- | --- |
| EX-RPT-IOE-101 | 未登录门禁失败，或初始化/病区主数据加载失败 | 页面初始化失败 / 查询失败 / 导出失败（按 IF 文案） | 是 |
| EX-RPT-IOE-102 | 日期非法或状态非法 | 请选择有效查询条件 | 否 |
| EX-RPT-IOE-103 | 查询失败 | 查询失败 | 是 |
| EX-RPT-IOE-104 | 导出运行时失败（触顶截断不抛本码，回 `truncated`） | 导出失败 | 是 |

---

## 6. 事件语义与消费方

**本期无领域写操作。**

---

## 7. 非功能性需求

| 类别 | 要求 |
| --- | --- |
| **性能** | 常规日窗分页 ≤ 2s；导出 ≤ 30s |
| **租户** | 当前租户 |
| **权限** | 报表中心只读；不按护士站管辖病区收窄 |
| **一致性** | 读己提交；费用与反结口径对齐 **DM-FIN-002** / **AS-IP-FIN-004** 待重结语义 |

---

## 8. 测试要点（业务视角）

| 类别 | 要点 |
| --- | --- |
| **状态** | 仅入院/仅出院/全部并集与人次两侧计数 |
| **转科** | 转科后按入院科室仍能查到；转科列仅有转科时出现；转往科室正确 |
| **联动** | 未选科室病区空；换科室后病区选项与选中值正确刷新 |
| **费用** | 已结有数；未结空；反结待重结空；禁止 0 |
| **排除** | 取消登记、未登记不进表 |
| **导出** | 同筛选；触顶截断；xlsx 固定含「转往」列 |

---

## 9. 关联文档与变更历史

| 类型 | 路径 |
| --- | --- |
| 族 DD | `设计决策/DD-报表中心-发药耗材挂号费用汇总明细族.md` |
| 对照 | `AS-RPT-009-住院费用明细.md`（费用行级，勿并页） |
| 结算/反结 | `AS-IP-FIN-003` / `AS-IP-FIN-004` |
| 登记/入出转 | `AS-IP-FIN-001` / `AS-IP-NUR-002` |
| DM | `DM-ADM-002` / `DM-FIN-002` / `DM-ADM-001` / `DM-ADM-003` |
| SDD | `AppService-SDD/AS-RPT-010-住院患者入出明细-SDD.md` |

| 版本 | 日期 | 摘要 |
| --- | --- | --- |
| v1.8 | 2026-08-14 | 默认业务日期改为近一个月（今日往前 1 自然月～今日） |
| v1.7 | 2026-08-14 | 住院天数改为**即进不计出**（11→13=2）；出院时间=计划出院/`end_time` |
| v1.6 | 2026-08-02 | 列表/导出增 **费用性质**列（入院身份优先账户；勿跟账单行挂账） |
| v1.5 | 2026-07-27 | 前端落地 + 菜单挂报表中心（`/report/inpatient-in-out-detail`，V121）；包名确认 `report.inoutdetail` |
| v1.4 | 2026-07-27 | 校验回写：应用包为 `report/inoutdetail`（无 inpatient 子段）；转科=WARD 轨迹代理；EX-101 含登录门禁 |
| v1.3 | 2026-07-27 | App 落地回写：`showTransferColumn`=当前页/导出行；导出 `truncated`；xlsx 固定转往列；分页 `totalCount` |
| v1.2 | 2026-07-27 | 入院科室→病区联动（IF-01a / BR-RPT-IOE-005a）；禁全院病区平铺 |
| v1.1 | 2026-07-27 | 关闭 GAP-RPT-IOE-001～003（DM + Encounter/Payment QS 落地） |
| v1.0 | 2026-07-27 | 首版：入出明细（无在院）；入院科室/病区；动态转科列；已结费用空值；人次页脚；挂报表中心 |
