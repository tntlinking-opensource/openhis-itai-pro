# 住院护士站 · 医嘱校对 · 应用服务设计（AS）

> **文档职责**：工作台右侧「医嘱校对」Tab 契约——批量查询待校对/已校对/已停止/已作废医嘱行，以及**校对通过**（含**停嘱校对**）、**退回**（含**取消停嘱校对**）写操作。
>
> **页面边界**：**本 Tab**——校对状态切换、长临嘱筛选、查询刷新、勾选医嘱、校对通过/退回。**患者上下文**在 **AS-IP-NUR-001**；**医嘱执行**在 **AS-IP-NUR-004**。
>
> **编码**：AS-IP-NUR-003 · **版本**：v1.30 · **日期**：2026-10-13

---

## 1. 场景概述

### 1.1 目标与边界

| 维度 | 说明 |
| --- | --- |
| **在内** | IF-IP-AUD-01 医嘱校对列表（读）；IF-IP-AUD-02 校对通过（写，含**开嘱校对**与**停嘱校对**分支）；IF-IP-AUD-03 退回（写，含**开嘱退回**与**取消停嘱校对**分支） |
| **在外** | 左侧患者树/列表/详情（**AS-IP-NUR-001**）；医嘱执行（**AS-IP-NUR-004**）；护士划价医嘱；账户余额/费别展示（列表不返回）；绑定耗材/用法衍生项；医生停嘱/取消停嘱（**AS-IP-DOR-003**） |
| **涉及域** | MedicationRequest；ServiceRequest；InpatientOrderLookupLoader（住院医嘱列表骨架）；Encounter（可见性 + 转科/出院校对联动）；MedicationDefinition / ActivityDefinition；Frequency |
| **编排权威** | 以本文 **§2、§4** 为准；写侧对齐老系统 `adviceVerify` / `adviceReject` 与 **DM-MED-003** / **DM-WKF-004**（停嘱校对字段 `stop_performer_check_id` / `stop_check_time`） |

### 1.2 角色与关键动作

| 角色 | 关键动作 |
| --- | --- |
| **病区护士** | 勾选患者 → 切换「未校对/已校对」/ 长临嘱 → **查询** → 「未校对」勾选 → **校对通过**（开嘱或停嘱校对）或 **取消校对**；「已校对」同行看开嘱/停嘱校对人，有停嘱校对人则 **取消停嘱校对**，否则 **取消校对**（退回草稿） |
| **系统** | 列表按 Tab 过滤装载；开嘱校对改请求状态并记校对人/时间；**停嘱校对/取消停嘱校对只写/清两字段、不改 `STOPPED`**；转科/出院校对后联动就诊待转科/待出院 |

### 1.3 术语与口径约定

| 术语 | 口径 |
| --- | --- |
| **校对状态（auditTab）** | 见 **BR-IP-AUD-003** |
| **已校对态（住院护士校对）** | **药品 + 诊疗** 统一 `VERIFIED`；**执行 Tab**（**AS-IP-NUR-004**）仅装载此态医嘱 |
| **COMPLETED 边界** | `COMPLETED` 用于**门诊收费收口**、**执行完成**等路径，**不是**住院护士「校对通过」后的请求态 |
| **长临嘱（therapyType）** | `all` 不过滤；`long` → `LONG_TERM`；`temp` → `TEMPORARY` |
| **医嘱来源** | 仅 `generate_source_enum = doctor-prescription` |
| **医嘱类型** | 仅 `order_type ∈ { MED_REQUEST, SERVICE_REQUEST }` |
| **请求锚点（写接口入参）** | 每条须 `orderType`（`MED_REQUEST` / `SERVICE_REQUEST`）+ `requestId` |
| **校对人/时间** | 开嘱校对写 `performer_check_id` / `check_time`；取当前登录 **Practitioner**；时间服务端当前时刻 |
| **停嘱校对** | 仅**长期**且已 **STOPPED**；写 `stop_performer_check_id` + `stop_check_time`；**不**改 `status`（仍 `STOPPED`）；**临时医嘱无停嘱**故无停嘱校对；打印取 **`stopCheckerName`**（由 `stop_performer_check_id` 解析）；**不**自动 skip 剩余 pending（真 pending 走执行 Tab / **IF-IP-EXE-04**；假 pending 由 **BR-IP-EXE-038** 抑制） |
| **取消停嘱校对** | 逆流程：清 `stop_performer_check_id` + `stop_check_time`；**不**改 `STOPPED`；解锁医生 **IF-IP-DOR-25** 取消停嘱；**本期暂不校验操作人**须与原校对人一致 |
| **退药/退诊疗** | 住院**不**生成负向药/诊 Request（退药=发放对冲 **AS-IP-PHA-003**，取消执行回退账单 **AS-IP-NUR-012**；负向 Request 属门诊退费路径）；列表**禁止**用 `based_on` 当「派生/退单」排除 |
| **DRAFT 与退回** | 纯 `DRAFT`（无护士退回痕迹）**不**进本 Tab；护士**退回**后变 `DRAFT` 并**离开**护士校对列表，由医生站修订（老系统 SQL：`DRAFT` 且 `performer_check_id IS NOT NULL` 为退回痕迹，本 Tab 四个状态均不展示） |
| **特殊诊疗** | `category_enum = TRANSFER`（转科）、`DISCHARGE`（出院）在列表与普通诊疗同查；**校对通过**后须联动就诊（**BR-IP-AUD-011**） |
| **患者卡片** | 列表按 `encounterId` 分组；人口学/床号等由 **IF-IP-NUR-02/03** 提供，本接口不重复 |
| **读侧组装** | **方案 A**：InpatientOrderLookupLoader · InpatientOrderListSkeleton 骨架（**Q-IP-AUD-001**）+ 域查询补全明细；行展示字段委托共享 **`NurseStationOrderViewListAssembler`**（`orderview` 包，见 **AS-IP-NUR-003-SDD §2.5**）；**禁止** App 直查QueryService Repository |
| **中西药 Tab（medicineCategoryTab）** | `west`（默认，`tcm_flag=false`）/ `tcm`（`tcm_flag=true`）；`tcm` Tab **不展示**诊疗行（**BR-IP-NUR-003-TCM-001**）；**出院/转科等诊疗行可在 `west` Tab 出现，类型列须读 `orderKindDisplay`，不得与「西药」Tab 标签混淆** |
| **麻醉申请范围（anesthesiaApplyOnly）** | `true`=仅麻醉申请（申请单号 **AA 前缀**，诊疗与药品同口径）；`false`/空=**排除**麻醉申请（普通护士默认）。**无**类目 25 / 同单号池兼容。与执行/汇总领药/患者树校执气泡同源（**BR-IP-NUR-003-ANES-001**） |
| **中药方展示（一方一行）** | `tcm` Tab 下同 `prescription_no` 折叠为一行；`lineRequestIds` 承载方内全部 req id；代表行 `requestId` 取方内最小 sort/id；**代表行 `reqOpenedTime` 取方内各味开立轴最晚**（opened 空回落 authored；**不**改库，仅展示；写 Gate 仍按各味自身开立时间）（**BR-IP-NUR-003-TCM-002**） |
| **整方写扩行** | 校对/退回勾选中药代表行时，App 按 `prescription_no` 扩成方内全部 `requestId` 逐条域写；入参 Gate ≤50 按**扩行前**勾选计（**BR-IP-NUR-003-TCM-003**） |
| **西药成组（与门诊护士站一致）** | 列表回传 `groupId` 且同组聚邻；前端同 `groupId` 连续行合并括线展示；校对/退回写路径按同就诊同 `group_id` **整组扩行**（**BR-IP-NUR-003-GRP-001～002**） |

### 1.4 本期范围与非目标

| 类别 | 内容 |
| --- | --- |
| **本期** | IF-IP-AUD-01～03 |
| **非目标** | 绑定耗材；分页（按左侧多选患者全量返回）；账户余额/费别；首日量；隔日/按周频次；已校对 Tab 批量退回（UI 仅在未校对操作） |

### 1.5 领域清单（聚合 + 职责 + DM 路径）

| 聚合（域） | 职责摘要 | DM 路径 |
| --- | --- | --- |
| **MedicationRequest** | 开嘱校对/退回；停嘱校对/取消停嘱校对；状态与校对/停嘱校对字段 | `DM-MED-003` |
| **ServiceRequest** | 开嘱校对/退回；停嘱校对/取消停嘱校对；转科/出院类目 | `DM-WKF-004` |
| **Encounter** | 可见性；转科/出院校对后 `ON_HOLD` | `DM-ADM-002-Encounter-就诊.md` |
| **InpatientOrderLookupLoader（住院医嘱列表骨架）** | 校对列表骨架 | `ServiceRequestQueryService / MedicationRequestQueryService 等（DM §6.2）` |
| **Frequency** | 频次展示 | `DM-CUS-008-Frequency-频次.md` |

### 1.6 业务规则（BR-*）

| 规则 ID | 描述 | 绑定 IF |
| --- | --- | --- |
| **BR-IP-AUD-001** | 列表 `encounterIds` 非空；须落在 **AS-IP-NUR-001** 可见范围 | IF-IP-AUD-01 |
| **BR-IP-AUD-002** | 列表固定：`generate_source_enum = doctor-prescription`；`order_type ∈ {MED_REQUEST,SERVICE_REQUEST}`；**禁止**用 `based_on` 当排除条件（皮试检查、执行绑定子行等均可挂 based_on）。住院正常路径**无**负向药/诊 Request；读侧对 `refund_*_id`/诊疗负数量的排除仅为与门诊同源防御，**不是**住院退药主路径 | IF-IP-AUD-01 |
| **BR-IP-AUD-003** | **仅两 Tab**：`pending` → 开嘱待校（`ACTIVE`）∪ 停嘱待校（长期 `STOPPED` 且 `stop_performer_check_id` 空）；`passed` → `VERIFIED` ∪ 已停嘱校对（`STOPPED` 且 `stop_performer_check_id` 非空），**同一条只一行**，同时展示开嘱校对人与停嘱校对人；**无**已停止/已作废 Tab。临时医嘱无停嘱待校 | IF-IP-AUD-01 |
| **BR-IP-AUD-004** | `therapyType` long/temp 过滤 `therapy_enum`；all 不限 | IF-IP-AUD-01 |
| **BR-IP-AUD-005** | 列表排序：状态 → 组号/排序号（药）或开立时间（诊） | IF-IP-AUD-01 |
| **BR-IP-AUD-006** | 列表行含：锚点、名称摘要、长临、**`orderKindDisplay`（药品/检验/检查/治疗/手术/麻醉/出院/转科/护理/嘱托等，与 Tab 无关；按 `category_enum` 映射目录类别，**禁止**检验/检查/治疗/手术/麻醉一律回落「诊疗」；术式挂 `procedureDefinitionId` 时强制「手术」；`category_enum=52` 须为「嘱托」）**、用法频次、剂量、开立/停嘱、状态文案；**剂量展示**：委托 **`MedicationDosageDisplayAssembler`**——默认最小单位为主、括号附计量；**目录 `injectFlag=true` 且拆零量 &lt; 1** 时 `dosageDisplay`/`dailyDoseDisplay` **主显计量单位**（例一次 10 毫升 / 每日 10 毫升，**禁止**「0.1 瓶」）；**长期药行须回传 `firstDose`/`firstDoseUnitCode`/`firstDoseUnitName`（OrderView 同源；前端剂量列拼「首日…」，承接 **BR-IP-ORD-030**）**；**`performerCheckName`（开嘱校对人姓名）**；**`stopCheckerName`（停嘱校对人姓名，`stop_performer_check_id` 解析；打印同源）**；**`stopAuditPending`（布尔：长期 `STOPPED` 且停嘱校对为空 → 醒目标识停嘱待校）**；药嘱行**须返回 `groupId`**（`med_medication_request.group_id`，与住院医生站列表同源，未成组可为 null）；**皮试药行**须返回 **`skinTestFlag`**；护士已在皮试 Tab 登记时须回传 **`skinTestOutcomeCode`/`skinTestOutcomeText`**（companion doctor 皮试检查 SR → `AllergyIntolerance` 最新覆盖，与 **BR-IP-ORD-016** 同源）；前端医嘱内容列在药名规格后展示阴/阳性；**嘱托诊疗行** `adviceDisplayName` **优先** `entrust_content` 正文（**BR-IP-ORD-028④**），**禁止**只显示目录名「嘱托」；**出院诊疗行** `adviceDisplayName`：里程碑 `out_way_code=5`（死亡）为「死亡出院」，否则「出院医嘱」（与医生站 **BR-IP-ORD-017**、OrderView 同源）；**前端校对页不展示执行科室** | IF-IP-AUD-01 |
| **BR-IP-AUD-007** | 列表不返回余额、费别、绑定耗材 | IF-IP-AUD-01 |
| **BR-IP-AUD-008** | **校对通过（双分支）**：① **开嘱校对**：须 `ACTIVE` → `VERIFIED` + 写 `performer_check_id`/`check_time`（诊疗住院校对**不落** `COMPLETED`）；② **停嘱校对**：须长期且 `STOPPED` 且 `stop_performer_check_id` 为空 → 仅写 `stop_performer_check_id` + `stop_check_time`，**不改** `status`（仍 `STOPPED`）；成组长期可同批混开嘱/停嘱校对；临时无停嘱校对分支；**不**在校对事务内自动 skip pending | IF-IP-AUD-02 |
| **BR-IP-AUD-009** | **退回（双分支）**：① **开嘱退回**：须 `ACTIVE` 或 `VERIFIED` → `DRAFT`；写 `performerCheckId`/`checkTime`（**BR-MRQ-007** / **BR-SRQ-022**）；② **取消停嘱校对**：须 `STOPPED` 且 `stop_performer_check_id` 非空 → 清两字段，**不改** `STOPPED`；**本期暂不校验操作人**；取消后行回 `pending` 停嘱待校，医生方可 **IF-IP-DOR-25** | IF-IP-AUD-03 |
| **BR-IP-AUD-014** | **停嘱校对字段契约**（药/诊同构）：字段对 `stop_performer_check_id` + `stop_check_time`；医生取消停嘱须先取消停嘱校对；取消停嘱（医生）清空本对字段；改停嘱时间**保留**本对字段；再停嘱须重新停嘱校对 | IF-IP-AUD-02、03；**AS-IP-DOR-003** |
| **BR-IP-AUD-013** | **校对退回门控**（**ACTIVE/VERIFIED 均适用**）：App 校验医生开立/非派生行 + `InpatientAdviceRevokeGate` 断言无净剩 COMPLETED 执行、无在途/待发退/部分退发药、**无净已发**（正向 `COMPLETED` 数量减去 `REFUND+REFUNDED` 数量后仍大于 0 才阻塞；对齐 DM-MED-002 §6.1.4 正向不改态）、无待结算/已结算/退款中费用 | IF-IP-AUD-03 |
| **BR-IP-AUD-010** | 写接口批量：按 `orderType` 分组后逐条调域命令（域层诊疗校对命令一次一条）；**同一事务**提交一批；部分失败策略：整批失败回滚（首期） | IF-IP-AUD-02、03 |
| **BR-IP-AUD-011** | 诊疗校对通过后，若 `category_enum = TRANSFER` → 编排 `initiateDepartmentTransfer`（就诊 `IN_PROGRESS` → `ON_HOLD`，待转科）；若 `DISCHARGE` → 编排 `dischargeInpatientEncounter`（就诊 `IN_PROGRESS` → `ON_HOLD`，待出院）；**二者均仅改就诊主档，不释床、不收口占床位置**（释床仅 **AS-IP-NUR-002** 清床） | IF-IP-AUD-02 |
| **BR-IP-AUD-017** | **出院校对费用时间门禁**：开嘱校对出院类（`category_enum=34`、`ACTIVE`）前，须扫描本就诊**净生效**费用（`PLANNED`/`PENDING_SETTLEMENT`/`SETTLED`/`REFUNDING`；排除反结再生链已替代的历史 `SETTLED`；**冲销留痕**按 `refund_id` 轧差，负向行与净额≤0 的正向行**不参与**）；比对轴为**客户可见费用时间**（与费用明细同轴：有 Procedure→净剩 `recorded_time`；护士划价耗材→发放展示；滚费→`occurrence_time`；无法解析再回落 `occurrence_time`）；任一净生效费用业务时间 **严格晚于**计划出院时间（待校出院嘱 `occurrence_start_time`，空则回落开立/签发时刻）→ 整批校对失败（**EX-IP-AUD-205**），提示须列出越界项目名称与费用时间 | IF-IP-AUD-02 |
| **BR-IP-AUD-012** | 写操作前校验请求所属 `encounterId` 在护士站可见范围内 | IF-IP-AUD-02、03 |
| **BR-IP-NUR-003-TCM-001** | 列表 `medicineCategoryTab`：`west`→非中药；`tcm`→中药（`tcm_flag=true`）；`tcm` Tab 排除 `SERVICE_REQUEST` | IF-IP-AUD-01 |
| **BR-IP-NUR-003-TCM-002** | `tcm` Tab 同 `prescription_no` **一方一行**；行内 `tcmFlag`/`prescriptionNo`/`lineRequestIds`/`sufferingFlag`/`chineseHerbsDoseQuantity`；**代表行展示开立时间取方内最晚**（写扩行后仍按各味自身 `req_opened_time` 校验） | IF-IP-AUD-01 |
| **BR-IP-NUR-003-TCM-003** | 校对/退回写：勾选中药方代表行 → 按 `prescription_no` **扩全方**逐条域写 | IF-IP-AUD-02、03 |
| **BR-IP-NUR-003-GRP-001** | **西药成组展示**：药嘱行回传 `groupId`（`med_medication_request.group_id`）；列表同组聚邻；前端同 `groupId` 连续行**合并为一行括线展示**（对齐门诊护士站 / 住院医生站）；中药方折叠行不参与成组合并 | IF-IP-AUD-01 |
| **BR-IP-NUR-003-GRP-002** | **西药成组写扩行**：校对/退回勾选组内任一行（或前端合并代表行）→ App 按同就诊同 `group_id`（非中药）**扩全组**逐条域写（含成组**全停**后同批停嘱校对/取消停嘱校对）；与中药整方扩行同事务口径；Gate ≤50 按**扩行前**勾选计 | IF-IP-AUD-02、03 |
| **BR-IP-NUR-003-ANES-001** | **麻醉申请范围**：IF-IP-AUD-01 入参 `anesthesiaApplyOnly`；`true` 仅保留申请单号 **AA 前缀** 的麻醉申请（诊疗与药品）；`false`/空排除之。**无**类目 25 / 同单号池兼容。实现：`InpatientNurseStationAnesthesiaApplyScopeSupport`；与 **AS-IP-NUR-004 / 010 / 001** 校执气泡默认隐藏对齐 | IF-IP-AUD-01 |

---

## 2. 页面与接口总览（契约概览）

| IF 编号 | 用户动作 | 接口语义 | 读/写 | 关键规则 | 状态变化 | 涉及领域 | 查询服务 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| IF-IP-AUD-01 | 切换 Tab/长临、点查询 | 批量查询医嘱校对列表（含开嘱待校 / 停嘱待校、`stopAuditPending`、中西药 Tab、麻醉申请范围、中药方折叠、西药成组聚邻） | 读 | BR-IP-AUD-001～007、**014**；**BR-IP-NUR-003-TCM-001～002**；**BR-IP-NUR-003-GRP-001**；**BR-IP-NUR-003-ANES-001** | 无 | InpatientOrderLookupLoader · InpatientOrderListSkeleton + Med/Service | InpatientOrderLookupLoader · InpatientOrderListSkeleton + 域查询 |
| IF-IP-AUD-02 | 未校对 Tab 勾选 → 校对通过 | 批量校对通过：开嘱校对 **或** 停嘱校对（中药方 / 西药成组扩行；成组全停可同批；出院开嘱前费用时间门禁） | 写 | BR-IP-AUD-008、010～012、**014**、**017**；**BR-IP-NUR-003-TCM-003**；**BR-IP-NUR-003-GRP-002** | 开嘱：`ACTIVE→VERIFIED`；停嘱校对：仍 `STOPPED` + 写停嘱校对两字段；转科/出院诊后 Encounter→待转科/待出院 | Med/Service + Encounter + ChargeItem（读） | — |
| IF-IP-AUD-03 | 未校对/已校对/已停嘱校对行勾选 → 退回 | 批量退回：开嘱退回 **或** 取消停嘱校对（中药方 / 西药成组扩行；开嘱退回须 **BR-IP-AUD-013**） | 写 | BR-IP-AUD-009、010、012、013、**014**；**BR-IP-NUR-003-TCM-003**；**BR-IP-NUR-003-GRP-002** | 开嘱：`ACTIVE/VERIFIED→DRAFT`；取消停嘱校对：仍 `STOPPED` + 清停嘱校对两字段 | Med/Service | — |

---

## 3. 缺口跟踪（Gap）

> **架构决议（2026-05-22）**：列表读侧 **方案 A**——以 **InpatientOrderLookupLoader**为骨架 + 扩展 QueryService；校对（`VERIFIED`）与门诊收费收口（`COMPLETED`）**两码事**，不得混用语义。

| Gap ID | 缺口类型 | 文件 + 章节 | 缺什么 / 不一致点 | 关闭条件 | 影响 IF | 优先级 | 状态 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| GAP-IP-AUD-001 | 查询服务 | `ServiceRequestQueryService / MedicationRequestQueryService 等（DM §6.2）` §3 | 缺 **Q-IP-AUD-001** 及 `ServiceRequestQueryService#listInpatientNurseStationAuditOrders` | QueryService §3 + 查询服务已实现 | IF-IP-AUD-01 | P0 | **已关闭** |
| GAP-IP-AUD-004 | 代码-文档不一致 | `DM-WKF-004` §2.3/§5.2/§6.1.6 + `ServiceRequest` | 住院校对应 `ACTIVE→VERIFIED`；门诊收费仍 `→COMPLETED` | DM + 域代码一致 | IF-IP-AUD-02、**AS-IP-NUR-004** | P0 | **已关闭** |
| GAP-IP-AUD-005 | 代码-文档不一致 | 事件载荷 + `各 Request QueryService` + `InpatientOrderListSkeleton` §4.2 | 药/诊校对/退回事件缺 `statusEnum`；QueryService 未同步 InpatientOrderListSkeleton | 事件 + 投影 + QueryService §4.2 一致 | IF-IP-AUD-01 | P0 | **已关闭** |
| GAP-IP-AUD-006 | QueryService/查询 | `InpatientOrderLookupLoader · InpatientOrderListSkeletonPageQuery` | `passed` Tab 需 `statusEnumIn`（**仅 VERIFIED**） | 并入 Q-IP-AUD-001 | IF-IP-AUD-01 | P0 | **已关闭** |
| GAP-IP-AUD-002 | 代码-文档不一致 | `DM-MED-003` §6.1.8 + `MedicationRequestDomainService#rejectMedicationOrder` | 退回未写 `performerCheckId`、`checkTime` | DM + 领域代码一致 | IF-IP-AUD-03 | P1 | **已关闭** |
| GAP-IP-AUD-003 | 代码-文档不一致 | `DM-WKF-004` §6.1.7 + `ServiceRequestDomainService#rejectServiceRequestToDraft` | 同上 | DM + 领域代码一致 | IF-IP-AUD-03 | P1 | **已关闭** |

> **说明**：转科/出院校对后就诊联动所需 `EncounterDomainService#initiateDepartmentTransfer` / `#dischargeInpatientEncounter` **域方法已存在**，由 **IF-IP-AUD-02** 应用编排调用，**不入 Gap**。

---

## 4. 接口编排说明书（业务步骤）

### IF-IP-AUD-01 医嘱校对列表

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 左侧已选患者；切换 `auditTab`/`therapyType` 或点**查询** |
| **主路径步骤** | 校验可见性（BR-IP-AUD-001）→ **Q-IP-AUD-001** 批量加载（BR-IP-AUD-002～004；`pending` 含 ACTIVE 与停嘱待校 STOPPED）→ 批量补全药品/诊疗明细（含药嘱 **`groupId`**、停嘱校对字段）→ 委托 **`NurseStationOrderViewListAssembler`** 组装展示行（含 **`stopAuditPending`** / **`stopCheckerName`**）→ 排序（BR-IP-AUD-005）→ 按 `encounterId` 分组返回 |
| **关键分支** | `pending` 停嘱待校行醒目标识 `stopAuditPending=true`；`passed` **仅** `VERIFIED`；`stopped` 为已停嘱校对的 `STOPPED`；`TRANSFER`/`DISCHARGE` 与普通诊疗同列；某就诊无数据返回空数组 |
| **失败分支** | EX-IP-AUD-101；EX-IP-AUD-102 |
| **查询侧说明** | 纯展示；写成功后 QueryService 刷新；打印医嘱单停嘱校对人取 **`stopCheckerName`** |

### IF-IP-AUD-02 校对通过

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 「未校对」Tab 勾选至少一条；用户点**校对通过** |
| **主路径步骤** | 校验 BR-IP-AUD-012 → 若勾选含出院开嘱（`ACTIVE`）→ **BR-IP-AUD-017**：`DischargeAuditChargeTimeGate` 扫本就诊有效费用，客户可见费用时间不得晚于计划出院（越界列项目+时间，**EX-IP-AUD-205**）→ 按 `orderType` 分组 → **按行分支**（**BR-IP-AUD-008**）：`ACTIVE` → 药品 `verifyMedicationOrder` / 诊疗 `verifyServiceRequest`；长期 `STOPPED` 且停嘱校对为空 → 药品 `verifyStopMedicationOrder` / 诊疗 `verifyStopServiceRequest`（只写停嘱校对两字段）→ 对刚完成**开嘱校对**的诊疗 reload 类目，若 `TRANSFER`/`DISCHARGE` 同事务编排 Encounter（**BR-IP-AUD-011**；停嘱校对分支**不**联动 Encounter）→ 提交事务 |
| **关键分支** | 同批可混开嘱校对与停嘱校对；成组长期全停可同批停嘱校对；批量含多条转科/出院：各 encounter 分别联动；已是目标就诊态时幂等或拒收（SDD 定） |
| **失败分支** | EX-IP-AUD-201（状态/分支不允许）；EX-IP-AUD-202（不可见）；EX-IP-AUD-203（转科/出院就诊联动失败，整批回滚）；**EX-IP-AUD-205**（出院开嘱费用时间晚于计划出院，整批回滚） |
| **查询侧说明** | 开嘱：`MedicationOrderVerifiedEvent` / `ServiceRequestVerifiedEvent` → `VERIFIED`；停嘱校对：读侧刷新停嘱校对两字段，状态仍 `STOPPED`；**AS-IP-NUR-001** `pendingAuditCount` 同步减 |

### IF-IP-AUD-03 退回

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 「未校对」或「已校对」Tab 勾选开嘱行，或对**已停嘱校对**行点**退回**（取消停嘱校对）；可选填原因（开嘱退回用）；**校对通过**仍仅「未校对」Tab |
| **主路径步骤** | 校验 BR-IP-AUD-012 → **按行分支**（**BR-IP-AUD-009**）：① `ACTIVE`/`VERIFIED` → 须通过 **BR-IP-AUD-013** → `rejectMedicationOrder` / `rejectServiceRequestToDraft`；② `STOPPED` 且已停嘱校对 → `cancelVerifyStopMedicationOrder` / `cancelVerifyStopServiceRequest`（清两字段；**不**校验操作人；**不**走 **BR-IP-AUD-013**）→ 开嘱退回的转科/出院类诊疗尝试撤销 `ON_HOLD`（**GAP-ATD-008**）→ 同事务提交 |
| **关键分支** | `ACTIVE` 未校对直接退回；`VERIFIED` 已校对须在执行取消、退药/退费完成后方可退回；取消停嘱校对后行回 `pending`（`stopAuditPending`）；开嘱退回后医嘱不在护士校对任一 Tab |
| **失败分支** | EX-IP-AUD-301（开嘱退回状态不允许或 **BR-IP-AUD-013** 未满足）；EX-IP-AUD-204（取消停嘱校对失败：尚未停嘱校对/非 STOPPED/域断言）；EX-IP-AUD-302（不可见）；EX-IP-AUD-203（逆向编排/并发冲突） |
| **查询侧说明** | 开嘱退回 → `DRAFT`；取消停嘱校对 → 仍 `STOPPED`、停嘱校对字段空；医生站取消停嘱门禁解除 |

---

## 5. 业务异常与用户提示

| 异常 ID | 场景 | 用户提示 | 可重试 |
| --- | --- | --- | --- |
| EX-IP-AUD-101 | 列表查询失败 | 医嘱加载失败，请稍后重试 | 是 |
| EX-IP-AUD-102 | 所选患者均不可见 | 当前患者不可用，请刷新左侧列表 | 是 |
| EX-IP-AUD-201 | 开嘱校对非 ACTIVE，或停嘱校对非「STOPPED 且未停嘱校对」 | 所选医嘱状态已变更，请刷新后重试 | 是 |
| EX-IP-AUD-202 | 写操作就诊不可见 | 无权限操作该患者医嘱 | 否 |
| EX-IP-AUD-203 | 转科/出院联动失败 | 校对失败：患者流转状态无法更新 | 是 |
| EX-IP-AUD-204 | 取消停嘱校对失败（尚未停嘱校对、非长期 STOPPED、或域断言失败） | 仅已停嘱校对的医嘱可取消停嘱校对；停嘱待校请先停嘱校对 | 是 |
| EX-IP-AUD-205 | 出院校对：存在客户可见费用时间晚于计划出院（**BR-IP-AUD-017**） | 校对失败：以下费用发生时间晚于出院时间（…）：项目名（费用时间）；… | 是 |
| EX-IP-AUD-301 | 开嘱退回状态不允许或门控未满足 | 仅未校对/已校对且执行发药费用已处理完毕的医嘱可退回 | 是 |
| EX-IP-AUD-302 | 退回时就诊不可见 | 无权限操作该患者医嘱 | 否 |

---

## 6. 事件语义与消费方

| 事件 | 来源 | 影响 |
| --- | --- | --- |
| `MedicationOrderVerifiedEvent` | IF-IP-AUD-02 开嘱校对·药 | → `VERIFIED`；**AS-IP-NUR-001** `pendingAuditCount` 减 |
| `ServiceRequestVerifiedEvent` | IF-IP-AUD-02 开嘱校对·诊 | → `VERIFIED`；待校对计数减；转科/出院消费方更新就诊展示 |
| 停嘱校对写库（域命令，事件以实现为准） | IF-IP-AUD-02 停嘱校对 | 写 `stop_performer_check_id`/`stop_check_time`；状态仍 `STOPPED`；`pendingAuditCount` 减 |
| `MedicationOrderRejectedEvent` / `ServiceRequestRejectedToDraftEvent` | IF-IP-AUD-03 开嘱退回 | 状态 → `DRAFT`；待校对计数减；医生站待办刷新 |
| 取消停嘱校对写库（域命令，事件以实现为准） | IF-IP-AUD-03 取消停嘱校对 | 清停嘱校对两字段；仍 `STOPPED`；回 `pending`；解锁医生取消停嘱 |
| `DepartmentTransferInitiatedEvent` / `HospitalDischargedEvent` | IF-IP-AUD-02 转科/出院编排 | InpatientEncounterContextQueryModel 待转科/待出院文案 |
| 停嘱/取消停嘱/签发类事件 | 医生站 | 列表 Tab 可见性；新停嘱进入停嘱待校 |

---

## 7. 非功能性需求

| 维度 | 要求 |
| --- | --- |
| **性能** | 列表批量 IN；写操作一批建议 ≤50 条 |
| **一致性** | 待校对计数与 **BR-IP-NUR-004** 一致：开嘱待校（`ACTIVE`）**+ 停嘱待校**（长期 `STOPPED` 且停嘱校对空），同源 **BR-IP-AUD-002**；护士划价不经本 Tab |
| **事务** | IF-IP-AUD-02 含 Encounter 联动时与请求状态变更**同一事务** |
| **权限** | 管理病区在院患者 |

---

## 8. 测试要点（业务视角）

| 类别 | 要点 |
| --- | --- |
| **列表 Tab** | pending=ACTIVE∪停嘱待校；`stopAuditPending` 醒目；passed=**仅** VERIFIED；stopped=已停嘱校对 STOPPED；suspended 正确 |
| **开嘱校对** | 药/诊均→VERIFIED 且写校对人/时间；非 ACTIVE 拒绝 |
| **停嘱校对** | 只写两字段、仍 STOPPED；重复校对拒绝；临时无此分支；不自动 skip pending |
| **开嘱退回** | →DRAFT 且离开护士列表；校对人/时间可追溯 |
| **取消停嘱校对** | 清两字段、仍 STOPPED；不校验操作人；解锁医生取消停嘱 |
| **转科/出院** | 开嘱校对 TRANSFER/DISCHARGE 后就诊待转科/待出院；停嘱校对不改就诊 |
| **批量** | 药+诊混合；开嘱/停嘱校对同批；成组全停同批；部分非法态整批失败 |
| **边界** | 不可见 encounter；纯 DRAFT 不出现在列表；打印 `stopCheckerName` |
| **药嘱成组** | 同 `groupId` 多行列表回传一致组号并聚邻；前端合并括线；勾选校对/退回整组扩行；未成组为 null；诊嘱行无 `groupId` |

---

## 9. 关联文档与变更历史


### 9.2 变更历史

| 版本 | 日期 | 变更摘要 |
| --- | --- | --- |
| v1.31 | 2026-10-13 | **BR-IP-NUR-003-ANES-001 纠偏**：麻醉识别改为申请单号 **AA 前缀**（废止类目 25 / 同单号池）；对齐 **AS-SDD v1.3.25** / 代码先行 |
| v1.30 | 2026-10-13 | **BR-IP-NUR-003-ANES-001**：IF-IP-AUD-01 `anesthesiaApplyOnly`（默认排除麻醉申请）；对齐 **AS-SDD v1.3.24** |
| v1.29 | 2026-10-12 | **BR-IP-AUD-006 剂量展示**：输液（目录 `injectFlag`）且拆零&lt;1 时 `dosageDisplay`/`dailyDoseDisplay` 主显计量单位；对齐 **AS-SDD v1.3.23** / `MedicationDosageDisplayAssembler` |
| v1.28 | 2026-10-09 | **BR-IP-AUD-017**：出院校对仅拦**净生效**费用（冲销轧差后净额&gt;0；已整行冲销不拦）；对齐 **AS-SDD v1.3.22** |
| v1.27 | 2026-10-09 | **BR-IP-AUD-017**：比对轴改为客户可见费用时间（与费用明细同轴），不再仅比 `occurrence_time`；对齐 **AS-SDD v1.3.21** |
| v1.26 | 2026-10-09 | **BR-IP-AUD-017 / EX-IP-AUD-205**：出院校对前扫描本就诊有效费用，`occurrence_time` 晚于计划出院则失败并列出项目与时间；对齐 **AS-SDD v1.3.20** |
| v1.25 | 2026-10-08 | **BR-IP-AUD-006 `orderKindDisplay`**：按目录类目回显检验/检查/治疗/手术/麻醉；术式挂 PD→「手术」；禁止一律回落「诊疗」；对齐 `NurseStationOrderViewListAssembler#resolveOrderKindDisplay` |
| v1.24 | 2026-10-08 | **BR-IP-AUD-006**：出院行 `adviceDisplayName` 在 `out_way_code=5` 时为「死亡出院」，否则「出院医嘱」；OrderView 与医生站 **BR-IP-ORD-017** 同源；对齐 **AS-SDD v1.3.18** / **AS-IP-DOR-003 v1.54** |
| v1.19 | 2026-08-13 | **BR-IP-AUD-013 发药净额**：`InpatientAdviceRevokeGate` 对正向 `COMPLETED` 与 `REFUND+REFUNDED` 按数量对冲；全额退药后不再误拦校对退回（DM-MED-002 §6.1.4） |
| v1.18 | 2026-08-08 | **Tab 收敛**：取消已停止/已作废；`passed` 含 VERIFIED + 已停嘱校对 STOPPED（同行展示双校对人）；取消校对：有停嘱校对人→取消停嘱校对，否则→取消开嘱校对 |
| v1.17 | 2026-08-08 | **EX-IP-AUD-204**：取消停嘱校对失败独立错误码（与开嘱退回 **301** 分离）；写路径整批事务 **BR-IP-AUD-010** 与实现对齐 |
| v1.16 | 2026-08-08 | **停嘱校对契约**：字段 `stop_performer_check_id`/`stop_check_time`；**BR-IP-AUD-003/006/008/009/014**；IF-02 分支停嘱校对、IF-03 分支取消停嘱校对（不改 `STOPPED`；取消不校验操作人）；`pending` 含停嘱待校 + `stopAuditPending`；打印 `stopCheckerName`；对齐 **AS-IP-DOR-003** / **DM-MED-003** / **DM-WKF-004** / **BR-IP-NUR-004** |
| v1.15 | 2026-07-22 | **BR-IP-AUD-002 口径澄清**：住院退药**不**造负向 Request（发药对冲+账单回退）；纠偏重点是**废止 based_on 排除**；`refund_*_id` 过滤仅为门诊同源防御 |
| v1.15 | 2026-09-02 | **BR-IP-AUD-006**：IF-IP-AUD-01 皮试药行回传 **`skinTestOutcomeCode`/`skinTestOutcomeText`**（与医生站 **BR-IP-ORD-016** 同源）；校对列表医嘱内容跟在药名后展示阴/阳性 |
| v1.14 | 2026-07-22 | **BR-IP-AUD-002 纠偏**：退药/退诊疗排除改认 `refund_*_id`（负数量兜底）；**废止**用 `based_on` 当派生排除（医生开立皮试检查等可有 based_on） |
| v1.13 | 2026-07-22 | **BR-IP-AUD-002**：校对队列**不**再因 `based_on → med_medication_request` 剔除皮试检查 companion（须可校对→执行）；退诊疗仍仅锚 `wkf_service_request`/负数量 |
| v1.12 | 2026-07-18 | **BR-IP-AUD-006 / BR-IP-ORD-028**：`orderKindDisplay` 增补嘱托（`category_enum=52`→「嘱托」）；`NurseStationOrderViewListAssembler#resolveOrderKindDisplay`；对齐 **AS-SDD** |
| v1.11 | 2026-07-17 | **西药成组一起校对**：新增 **BR-IP-NUR-003-GRP-001/002**（列表聚邻+前端括线；写路径按 `group_id` 整组扩行，对齐门诊护士站展示与中药整方写扩行）；`InpatientNurseStationTcmOrchestrator#expandMedRequestIdsForNurseWrite` |
| v1.10 | 2026-07-16 | **BR-IP-ORD-028④**：嘱托行列表 `adviceDisplayName` 优先 `entrust_content`；共享 **`InpatientEntrustOrderGateSupport#resolveServiceAdviceDisplayName`**（**AS-IP-NUR-003-SDD §2.5**）；对齐医生站 **BR-IP-ORD-028** |
| v1.9 | 2026-07-09 | 列表行新增 **`orderKindDisplay`**（出院/转科/护理/药品/诊疗）；明确 `west` Tab 可含出院诊疗行，类型列不得与 Tab 标签混淆 |
| v1.7 | 2026-06-20 | **support 落点对齐**：护士站读侧 Loader/Assembler/`orderview` → `module.querysupport.nursestation`；详见 **AS-IP-NUR-003-SDD** / **01-架构/模块化开发规范** |（已迁至 application.inpatient.nursestation.{feature}.support）（已迁至 application.shared / feature support（见 DISSOLUTION.md））
| v1.6 | 2026-06-20 | 对齐 **AS-IP-DOR-006** 下游：`medicineCategoryTab` 中西药筛选、中药方一方一行展示、校对/退回整方扩行；**BR-IP-NUR-003-TCM-001～003** |
| v1.5 | 2026-05-28 | 读模型废止后对齐 zxplat 实现（EncounterContextFacade / querysupport Loader）。 |（已迁至 EncounterContextReader / application.shared.encounter）
| 类型 | 文档 |
| --- | --- |
| 关联 AS | `AS-IP-NUR-001`；`AS-IP-NUR-004`；`AS-IP-NUR-005`；`AS-IP-DOR-003` |
| AS-SDD | `AS-IP-NUR-003-住院护士站医嘱校对-SDD.md`（§2.5 共享 `orderview`） |
| 查询服务 | `ServiceRequestQueryService / MedicationRequestQueryService 等（DM §6.2）` |
| DM | `DM-MED-003`、`DM-WKF-004`、`DM-ADM-002` |
| UI | `openhis-pro-ui/.../inpatient/nurse-station/order-audit/` |

| 版本 | 日期 | 摘要 |
| --- | --- | --- |
| v1.23 | 2026-09-16 | **BR-IP-AUD-006**：校对列表长期药回传 `firstDose`/`firstDoseUnitCode`/`firstDoseUnitName`（OrderView 同源）；前端剂量列拼「首日…」；对齐 **AS-SDD v1.3.17** |
| v1.22 | 2026-09-16 | **废止 BR-IP-AUD-015**：删除设置 `inpatientStopVerifyAutoSkipPending` 及停嘱校对后自动 skip；真 pending 走 **IF-IP-EXE-04**，假 pending 靠 **BR-IP-EXE-038**；对齐 **AS-SDD v1.3.16** / 种子从 `V10__seed_platform` 去掉该设置 |
| v1.21 | 2026-09-11 | **BR-IP-AUD-015**（已废止于 v1.22）：停嘱校对成功后按设置可选自动不执行剩余 pending |
| v1.20 | 2026-09-07 | **BR-IP-NUR-003-TCM-002**：中药方折叠代表行展示开立时间改为方内最晚（不改库；写扩行仍按各味自身开立时间） |
| v1.5.1 | 2026-05-27 | **IF-IP-AUD-01** 列表行补充药嘱 **`groupId`**（`MedicationRequestListQueryModel`，与住院医生站/执行列表同源） |
| v1.0 | 2026-05-22 | 首版：IF-IP-AUD-01 列表 |
| v1.1 | 2026-05-22 | 增 IF-IP-AUD-02/03；转科/出院联动；退回追溯 Gap |
| v1.4 | 2026-05-24 | 列表行展示抽取至共享 **`orderview`** 装配器；**AS-SDD v1.1** §2.5；执行/划价父医嘱列表复用同口径 |
| v1.3 | 2026-05-22 | Gap 全关：方案 A 读侧；DM/QueryService 刷新事件载荷回更；诊疗校对 VERIFIED 与门诊收费 COMPLETED 分离 |
| v1.2 | 2026-05-22 | 裁决：药/诊校对后统一 VERIFIED；诊疗 COMPLETED 仅非住院校对路径；增 GAP-IP-AUD-004 |
