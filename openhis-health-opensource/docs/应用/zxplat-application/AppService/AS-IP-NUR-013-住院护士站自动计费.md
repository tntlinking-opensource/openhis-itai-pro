# 住院护士站 · 自动计费 · 应用服务设计（AS）

> **文档职责**：工作台「住院记账 / 划价」模块内 **「自动计费」Tab** 契约——按就诊维护**可日结诊疗**自动计费配置（增删改启停）；只读展示**护理级别**（来自医嘱，不可在本 Tab 配置）；以及 **每日 00:05** 住院滚费定时任务（前一日自然日）编排语义。
>
> **页面边界**：**本 Tab** 挂载于 **AS-IP-NUR-005** 同一划价页（与「划价确费」「医嘱计费」并列）；**患者上下文**在 **AS-IP-NUR-001**（单选就诊）；自动计费配置 CRUD 在本文；护理级别只读区不在此表维护；**滚费落账**对齐 **IF-IP-EXE-02** 长期医嘱执行轨（Procedure + Charge **`generate_source=order-execute`**）；配置 ensure 的长期 SR 标记 **`generate_source=auto-roll-fees`** 且**禁止**护士站手动执行（**BR-IP-ARG-019**）。
>
> **编码**：AS-IP-NUR-013 · **版本**：v1.5.1 · **日期**：2026-09-27

---

## 1. 场景概述

### 1.1 目标与边界

| 维度 | 说明 |
| --- | --- |
| **在内** | IF-IP-ARG-01 诊疗选品目录（复用划价目录，仅诊疗表）；IF-IP-ARG-02 查询当前就诊自动计费配置（含护理级别只读列表）；IF-IP-ARG-03 新增配置；IF-IP-ARG-04 更新配置；IF-IP-ARG-05 删除配置；IF-IP-ARG-06 启用配置；IF-IP-ARG-07 停用配置；IF-IP-ARG-08 住院滚费定时任务（系统触发，无 UI 按钮）；IF-IP-ARG-09 配置行/护理行展开查询已滚费用记录（取消写路径复用 **AS-IP-NUR-012 IF-IP-CXL-01**） |
| **在外** | 左侧患者树/列表/详情（**AS-IP-NUR-001**）；划价确费 / 医嘱计费写路径（**AS-IP-NUR-005**）；医嘱校对（**AS-IP-NUR-003**）；医嘱执行（**AS-IP-NUR-004**）；取消执行编排本体（**AS-IP-NUR-012**，本 Tab 仅复用 HTTP）；分床/清床（**AS-IP-NUR-002**）；费用明细 Tab（**AS-IP-NUR-006**）；运维端全局规则维护（非 encounter 维度）；**复制模版**（从其它 encounter 批量复制配置，**本期不做**）；转科/请假/欠费冻结边界（**v1 不做**） |
| **涉及域** | ChargeItemAutoGen；ActivityDefinition；ChargeItemDefinition；ServiceRequest；ChargeItem；Encounter；EncounterLocation（在院/分床/清床边界） |
| **编排权威** | 配置写侧对齐 **DM-CUS-002** 五写方法；滚费任务对齐 **DM-CUS-002 §9.2.1** 与评审锁定口径（00:05、前一日自然日、分床～清床、护理医嘱驱动） |

### 1.2 角色与关键动作

| 角色 | 关键动作 |
| --- | --- |
| **病区护士** | 在 **AS-IP-NUR-001** 选中在院患者 → 划价页切 **自动计费** Tab → 查看已配置诊疗项与**只读护理级别列表** → 新增/改数量/启停/删除配置 → **展开护理行或配置行**查看已滚费用（业务日空=全住院期；选日后仅当天）→ 对待结算记录发起**取消**（复用取消执行） |
| **系统（定时任务）** | 每日 **00:05** 计算 `billDate = 前一日自然日` → 扫描在院 encounter → 按 ACTIVE 配置滚**诊疗**费 → 按当日有效**护理级别医嘱**滚**护理**费 → 幂等跳过已生成项 |

### 1.3 术语与口径约定

| 术语 | 口径 |
| --- | --- |
| **自动计费（UI）** | 划价页 Tab 名称；护士站按 encounter 维护可日结诊疗规则并触发定时滚费的场景统称 |
| **自动计费配置** | `cus_charge_item_auto_gen` 一行：`(encounterId, triggerSource, chargeDefinitionId, quantityValue, businessTime, billingPractitionerId, …)`；**不含**护理级别 |
| **住院滚费（任务）** | 定时任务 **IF-IP-ARG-08** 侧口径；按配置 + 护理医嘱批量生成前一日费用 |
| **可配置诊疗** | `trigger_source_table = wkf_activity_definition`；**允许**床位服务（51）及治疗/检查等可日结类目；**禁止**护理（26）、转科（32）、出院（34）、嘱托（52） |
| **护理级别** | `category_code = 26`；**不在配置表维护**；Tab 内**只读**展示当前/当日有效级别；滚费由任务按医嘱解析 |
| **计费日 billDate** | 定时任务每次处理 **1 个自然日**；任务于 **00:05** 跑 **前一日** |
| **在院滚费区间** | **分床后**起算；**记进不计出**：入院日跨午夜后计、**出院日（`end_time` 自然日）不计**；候选扫描仍为**已分床且未清床**，另以出院日 Gate 收口 |
| **护理有效口径（滚费）** | 仅 **`VERIFIED`**（校对未停嘱）参与，与配置 **`ACTIVE`** 同构；**`STOPPED` 一经停嘱即退出滚费池**（须另开新护理级别或办出院）；换级 **stop-old-start-new** 取 billDate 最后一条 `VERIFIED` |
| **落账来源（Charge）** | 滚费任务产出 `generate_source_enum = order-execute`（**IF-IP-EXE-02** 同构）；**非** `auto-roll-fees` / `nurse-pricing` |
| **配置滚费 SR** | **仅 IF-IP-ARG-03 新建配置**时 ensure 一条 **LONG_TERM**、`generate_source=auto-roll-fees`、`based_on=cus_charge_item_auto_gen.id` 的 VERIFIED 诊疗医嘱（`category_enum` 取自选品类目）；**IF-IP-ARG-08 滚费只读已有 SR 并按日执行**，任务侧**不**补建 SR |
| **幂等键（滚费）** | 同一 `billDate + serviceRequestId` 已有 `order-execute` Charge（`occurrence_time` 落在 billDate 自然日）→ **跳过**（任务内判定，**不**新增业务日志表） |
| **手动 vs 自动** | **不做**手工划价与自动滚费重复校验；护士可对已滚费用**取消划价**（**AS-IP-NUR-005 IF-IP-PRC-06**）；次日任务仍正常滚费 |
| **配置生效** | 新增日从**创建日**参与；**停用/删除**后不再参与；历史已生成 ChargeItem **不**回滚 |
| **ruleId 语义** | 更新/删/启停 Command 的 **`ruleId` = 表 `id`**；**无** `rule_id` 列 |

### 1.4 本期范围与非目标

| 类别 | 内容 |
| --- | --- |
| **本期** | IF-IP-ARG-01～09 |
| **非目标** | **复制模版**（从其它 encounter 复制配置）；转科当日双床计费；请假/欠费冻结停滚；运维端 encounter 无关全局规则页；药品/耗材自动计费；任务手工补跑 UI；滚费与手工划价重复拦截；部分数量冲销专用逻辑 |
| **老系统舍弃** | 老系统 `tmp_encounter_auto_roll` **物理删除**、任务按 **CURRENT_DATE** 幂等、护理费取 **COMPLETED** 全量护理请求（**非**「当日最后校对级别」）——新系统按本文口径，**不**照搬 |

### 1.5 领域清单（聚合 + 职责 + DM 路径）

| 聚合（域） | 职责摘要 | DM 路径 |
| --- | --- | --- |
| **ChargeItemAutoGen** | encounter 维度自动计费配置 CRUD + 启停 | `DM-CUS-002` §6.1 / §6.2 |
| **ActivityDefinition（读）** | 可日结诊疗选品；护理级别定义（category 26，只读） | `DM-WKF-001` |
| **ChargeItemDefinition（读）** | 配置锚定定价、滚费取价 | `DM-ADM-010` |
| **ServiceRequest** | 滚费落账时诊疗请求（与手动划价同构）；护理级别医嘱读侧 | `DM-WKF-004` |
| **ChargeItem** | 滚费费用事实；幂等查询 | `DM-ADM-011` |
| **Encounter / EncounterLocation** | 护士站可见性；在院/分床/清床边界 | `DM-ADM-002` |

### 1.6 业务规则（BR-*）

| 规则 ID | 描述 | 绑定 IF |
| --- | --- | --- |
| **BR-IP-ARG-001** | 配置读/写 `encounterId` 必填；须落在 **AS-IP-NUR-001** 可见范围 | 02～07、09 |
| **BR-IP-ARG-002** | 配置组合唯一：`(encounterId, triggerSourceTable, triggerSourceId, performingOrgId, chargeDefinitionId)`（**BR-CAG-001**） | 03、04 |
| **BR-IP-ARG-003** | 新增/更新时 `triggerSource` 须为**可日结诊疗**有效 ActivityDefinition（`trigger_source_table=wkf_activity_definition`）；**禁止**护理（26）、转科（32）、出院（34）、嘱托（52）；须能解析有效 **ChargeItemDefinition**（**BR-CAG-004**）；耗材/药品**不在本期** | **IF-IP-PRC-01**（选品）、03、04 |
| **BR-IP-ARG-004** | `quantityValue > 0`；缺省 **1** | 03、04 |
| **BR-IP-ARG-005** | 护理级别**仅只读展示**，**禁止**经本 Tab 写入 `ChargeItemAutoGen` | 02 |
| **BR-IP-ARG-006** | 定时任务：**00:05** 触发；`billDate` 缺省为**前一日**（00:00～00:15 窗口）；**手工**触发未传 `billDate` 时缺省**当日** | 08 |
| **BR-IP-ARG-007** | **自然日**计 1 次；**首次分床日**须 **billDate 日已过 00:00**（跨午夜）才计；区间起点取就诊 **`start_time`**（首次分床 `transitToInProgress` 锚点；**转床不更新入院时间**） | 08 |
| **BR-IP-ARG-008** | 滚费 encounter 范围：**已分床且未清床**（**AS-IP-NUR-002** 床态语义）；另 **记进不计出**：`end_time` 自然日不晚于 billDate 时整 encounter（床位+护理）当日不滚 | 08 |
| **BR-IP-ARG-009** | 护理滚费：仅 **`VERIFIED`** 参与（与配置 ACTIVE 同构）；**`STOPPED` 停嘱后即不滚**（须新开护理级别或出院）；换级 **stop-old-start-new** 取 billDate 最后 `VERIFIED` | 02、08 |
| **BR-IP-ARG-010** | 幂等：同 `billDate + serviceRequestId` 已有 `order-execute` ChargeItem → **跳过** | 08 |
| **BR-IP-ARG-011** | **不**校验与手工划价/医嘱计费的重复 | 08 |
| **BR-IP-ARG-012** | 已滚床位费用允许护士在本 Tab **取消**：**不**走划价冲销（IF-IP-PRC-06）；**复用 AS-IP-NUR-012 IF-IP-CXL-01**（按 `procedureId` 取消执行，物理删除待结算 `order-execute` Charge）；**不**软删配置、**不**软删 `auto-roll-fees` SR，故**不影响次日滚费** | 09（读）+ CXL-01（写） |
| **BR-IP-ARG-013** | 配置 **ACTIVE** 且未软删才参与滚费；**INACTIVE** / 已删不参与 | 06、07、08 |
| **BR-IP-ARG-014** | 滚费数量取配置 `quantityValue`（床位）；护理取医嘱数量（通常 **1**） | 08 |
| **BR-IP-ARG-015** | 滚费落账对齐 **IF-IP-EXE-02**：对已有 **VERIFIED 长期 SR** 登记 Procedure（LONG_TERM；顺序 **create→start→挂主执行人→complete**，与划价诊疗/医嘱执行同序，对齐 **DM-CLN-004**；`occurrence_time`/`recorded_time` 同值）+ **`order-execute`** 待结算 Charge；**配置项**时刻取规则 `business_time`（默认 **23:59:59**）拼 `billDate`；**护理**写死日末 **23:59:59**；任务可在次日凌晨触发，业务日仍记前不计出；**非**划价轨 IF-IP-PRC-02（划价走临时 Procedure，共用 `registerPrimaryPerformer`） | 08 |
| **BR-IP-ARG-016** | 配置写操作须带当前操作人（事件 `operatorId`） | 03～07 |
| **BR-IP-ARG-017** | 删除配置 = 领域 **软删**（`DISCARDED` + `is_deleted`）；**非**物理删 | 05 |
| **BR-IP-ARG-018** | 列表/滚费读侧：`performingOrgId` 科室匹配同 **BR-CAG-002**（全局空=不限；限定须上下文科室一致）；**IF-IP-ARG-03** 未传执行科室时默认锚定就诊 `organizationId` | 02、03、08 |
| **BR-IP-ARG-019** | **IF-IP-ARG-03** ensure 的长期 SR（`generate_source=auto-roll-fees`）**禁止**护士站手动执行（执行列表剔除）；`unit_code` 取床位活动 **`packageUnitCode`** 字典 code（**禁止**缺省汉字「次」）；护理仍走 **BR-IP-EXE-027**；**IF-IP-ARG-08**：**配置项**主执行人/Charge `performer_id` 取规则 **`billing_practitioner_id`**（缺省创建时当前登录人；存量空回落责任护士）；**护理**仍取责任护士 **PRIMARY_NURSE**，无则回落任务 `operatorId` | 03、08 |
| **BR-IP-ARG-020** | **IF-IP-ARG-09**：`ruleId` 与护理 `serviceRequestId` **二选一**。床位：`ruleId` 须属于当前 `encounterId` 且未软删，经 `findAutoRollBedServiceRequestIdByRuleId` 解析长期 SR。护理：`serviceRequestId` 须为本就诊医生开立护理级别（`VERIFIED`/`STOPPED`），经 `findNursingLevelForAutoRollPostedCharges` 校验。再列其 `order-execute` Charge（可选日期闭区间）；行内 `procedureId` + `cancelable` 供前端调 CXL-01 | 09 |
| **BR-IP-ARG-021** | 配置可维护 **`business_time`**（`LocalTime`，非空，默认 `23:59:59`）与 **`billing_practitioner_id`**（计费人，新建必填；可随时改）；改配置**仅**影响之后滚费，已落账不回溯 | 03、04、08 |

---

## 2. 页面与接口总览（契约概览）

| IF 编号 | 用户动作 | 接口语义 | 读/写 | 关键规则 | 状态变化 | 涉及领域 | 查询服务 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| IF-IP-ARG-01 | 新增/编辑弹窗：关键字、翻页检索诊疗项目 | **复用 IF-IP-PRC-01** 划价选品目录，传 **`productTableNames=[wkf_activity_definition]`**（**不**传 `categoryCode` 收窄；写侧按 **BR-IP-ARG-003** 排除护理/转科/出院/嘱托） | 读 | BR-IP-ARG-003 | 无 | ActivityDefinition / ProductDefinition | **IF-IP-PRC-01** `pageNurseStationPricingCatalog`（**不新增** HTTP） |
| IF-IP-ARG-02 | 进入 Tab / 切换患者 / 点**查询** | 查询当前就诊**自动计费配置列表** + **护理级别只读列表** | 读 | BR-IP-ARG-001、005、018 | 无 | ChargeItemAutoGen；ServiceRequest | `ChargeItemAutoGenQueryService#findRulesByEncounterId`；`ServiceRequestQueryService#listNursingLevelsForAutoRollTab` |
| IF-IP-ARG-03 | 点**新增** → 选项目与数量/业务时间/计费人 → **保存** | 新增一条自动计费配置 + ensure 长期滚费 SR | 写 | BR-IP-ARG-001～004、016、019、021 | 新建行 **ACTIVE** + 长期 SR | ChargeItemAutoGen；ServiceRequest | — |
| IF-IP-ARG-04 | 选中行 → **编辑** → 保存 | 部分更新配置（数量、定价、业务时间、计费人、备注等；**不含**启停） | 写 | BR-IP-ARG-001～004、002、021 | 业务字段更新 | ChargeItemAutoGen | — |
| IF-IP-ARG-05 | 选中行 → **删除** | 软删配置 | 写 | BR-IP-ARG-001、017 | **DISCARDED** + 软删 | ChargeItemAutoGen | — |
| IF-IP-ARG-06 | 选中行 → **启用** | 启用配置（**INACTIVE→ACTIVE**） | 写 | BR-IP-ARG-001、013、016 | **INACTIVE→ACTIVE** | ChargeItemAutoGen | — |
| IF-IP-ARG-07 | 选中行 → **停用** | 停用配置（**ACTIVE→INACTIVE**） | 写 | BR-IP-ARG-001、013、016 | **ACTIVE→INACTIVE** | ChargeItemAutoGen | — |
| IF-IP-ARG-08 | **系统** 00:05 定时 | 住院滚费（前一日 billDate；床位配置 + 护理医嘱） | 写 | BR-IP-ARG-006～011、014、015、018、019 | 对**已有**长期 SR 执行 IF-IP-EXE-02 槽位 + `order-execute` Charge | ChargeItemAutoGen；ServiceRequest；ChargeItem；Encounter | `ChargeItemAutoGenQueryService#findActiveRulesByEncounter`；`InpatientAutoRollEncounterContextLoader#listInpatientAutoRollEncounterContexts`；`ServiceRequestQueryService#findLastEffectiveNursingLevelForAutoRoll` / `#findAutoRollBedServiceRequestIdByRuleId`；`ChargeItemQueryService#existsOrderExecuteChargeForServiceRequestBillDate`；写：`InpatientAutoRollOrderExecutionOrchestrationSupport#executeLongTermServiceOrderSlot` |
| IF-IP-ARG-09 | 展开配置行/护理行 / 刷新已滚费用 | 按 `ruleId`（床位）或 `serviceRequestId`（护理）列出已落账 `order-execute` Charge（含 `procedureId`/`cancelable`；可选业务日闭区间） | 读 | BR-IP-ARG-001、012、020 | 无 | ChargeItemAutoGen；ServiceRequest；ChargeItem | `ServiceRequestQueryService#findAutoRollBedServiceRequestIdByRuleId` / `#findNursingLevelForAutoRollPostedCharges`；`ChargeItemQueryService#listOrderExecuteChargesByServiceRequestId` |

> **合并说明**：进入 Tab、切换左侧患者、点查询均走 **IF-IP-ARG-02**。选品弹窗检索与翻页走 **IF-IP-ARG-01**，**实现复用 AS-IP-NUR-005 IF-IP-PRC-01**，**不新增**选品接口。配置行/护理行「取消」**不新增**写 IF，复用 **AS-IP-NUR-012** `cancelNurseStationOrderExecutions`。

---

## 3. 缺口跟踪（Gap）

| Gap ID | 缺口类型 | 文件 + 章节 | 缺什么 / 不一致点 | 关闭条件 | 影响 IF | 优先级 | 状态 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| GAP-IP-ARG-001 | QueryService 能力 | `DM-WKF-004` §6.2.25 | 缺按 `encounterId + billDate` 解析**当日最后有效校对护理级别**读方法（category **26**；停嘱止算；换级 stop-old-start-new） | `findLastEffectiveNursingLevelForAutoRoll` + 领域实现 | IF-IP-ARG-02、08 | P0 | **已关闭** |
| GAP-IP-ARG-002 | QueryService 能力 | `DM-ADM-002` §6.2.21 | 缺滚费任务用**在院 encounter 列表**（分床后、清床前；含 accountId / 科室等滚费上下文） | `listInpatientAutoRollEncounterContexts` + 实现 | IF-IP-ARG-08 | P0 | **已关闭** |
| GAP-IP-ARG-003 | QueryService 能力 | `DM-ADM-011` §6.2.24 | 缺滚费**幂等查询**（`serviceRequestId + billDate + generate_source=order-execute`） | `existsOrderExecuteChargeForServiceRequestBillDate` + 实现 | IF-IP-ARG-08 | P0 | **已关闭** |
| GAP-IP-ARG-004 | DM 能力 | `DM-WKF-004` §6.1.20 | 缺床位配置 **ensure 长期 SR** 写能力（`based_on=cus_charge_item_auto_gen`；滚费改走 IF-IP-EXE-02） | `ensureAutoRollBedLongTermServiceRequest`（**仅 IF-IP-ARG-03**）+ 编排 `executeLongTermServiceOrderSlot`（**IF-IP-ARG-08**） | IF-IP-ARG-03、08 | P0 | **已关闭** |

> **说明**：
> - **ChargeItemAutoGen** 五写五读已在 **DM-CUS-002** 就绪，配置 IF-03～07 **不入 Gap**。
> - 诊疗选品复用目录读查询（仅 activity 表）；写侧类目黑名单由应用层 Gate，**不入 Gap**。
> - 配置列表 join 诊疗/定价展示名属应用层组装（**BR-CAG-QRY-003**），**不入 Gap**。
> - **复制模版**本期不做，**不入 Gap**。
> - 定时任务调度框架（Quartz/XXL）属基础设施，**不入 Gap**。

---

## 4. 接口编排说明书（业务步骤）

### IF-IP-ARG-01 诊疗选品目录（复用 IF-IP-PRC-01）

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 用户在 **自动计费** Tab 新增/编辑配置弹窗中检索诊疗项目 |
| **主路径步骤** | 调用 **`POST …/pageNurseStationPricingCatalog`**（**AS-IP-NUR-005 IF-IP-PRC-01**）→ Request 固定 **`productTableNames=[wkf_activity_definition]`**（**可不传** `categoryCode`）→ 其余分页/关键字/定价展示同划价选品 |
| **关键分支** | 关键字非空时名称/拼音优先；仅返回可绑定 **ChargeItemDefinition** 的诊疗行；前端可再滤护理/转科/出院/嘱托 |
| **失败分支** | 分页非法 → **EX-IP-PRC-101**（**不**单独定义 EX-IP-ARG-101） |
| **查询侧说明** | 纯读；**不**绑定 encounter；**禁止**为本 Tab 新增独立选品 HTTP |

### IF-IP-ARG-02 自动计费配置列表（含护理只读）

| 维度 | 说明 |
| --- | --- |
| **触发前提** | **AS-IP-NUR-001** 已选中一名在院患者；用户进入 Tab 或点**查询** |
| **主路径步骤** | 校验 **BR-IP-ARG-001** → 调 `findRulesByEncounterId` 取配置行（含 **INACTIVE**，按 priority/id 排序）→ 批量 join ActivityDefinition / ChargeItemDefinition / 计费人姓名 → 调 `listNursingLevelsForAutoRollTab` 解析护理级别只读列表（**BR-IP-ARG-005**；全住院期 `VERIFIED`+`STOPPED`；业务日不裁剪护理行）→ 装载在职护士 `nurseOptions` → 返回 `{ configs[], nursingLevels[], nursingLevelReadonly(=首行兼容), nurseOptions }` |
| **关键分支** | 无配置时空列表；护理无匹配医嘱时 `nursingLevels` 空 |
| **失败分支** | 就诊不可见 → EX-IP-ARG-201 |
| **查询侧说明** | 配置区 **`ChargeItemAutoGenQueryService`**；护理区 **`ServiceRequestQueryService#listNursingLevelsForAutoRollTab`**；计费人姓名 **`PractitionerQueryService`** |

### IF-IP-ARG-03 新增自动计费配置

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 用户选可日结诊疗项目、数量、业务时间、计费人，点**保存** |
| **主路径步骤** | 校验 **BR-IP-ARG-001～004**（含类目黑名单）→ 解析 `chargeDefinitionId` → 组装 `CreateAutoGenRuleCommand`（**无 id**；`businessTime` 空则日末、`billingPractitionerId` 空则当前登录人，**BR-IP-ARG-021**）→ 领域 `createAutoGenRule` → **`ensureAutoRollBedLongTermServiceRequest`**（**BR-IP-ARG-019**）→ 返回新行 **id** |
| **关键分支** | 组合键冲突 → 拒绝；缺省 quantity=1、status=**ACTIVE**、businessTime=**23:59:59** |
| **失败分支** | 重复配置 → EX-IP-ARG-301；类目禁止/定价无效 → EX-IP-ARG-302 |
| **查询侧说明** | 写后列表由前端刷新 **IF-IP-ARG-02** |

### IF-IP-ARG-04 更新自动计费配置

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 用户编辑已有配置（数量、业务时间、计费人、备注等），点**保存** |
| **主路径步骤** | 校验 **BR-IP-ARG-001** → `ruleId` = 表 **id** → `UpdateAutoGenRuleCommand` 非空覆盖（含 `businessTime`/`billingPractitionerId`，**BR-IP-ARG-021**）→ 领域 `updateAutoGenRule` |
| **关键分支** | 组合键变更时排除自身唯一性校验（**PR-CAG-001**） |
| **失败分支** | 行不存在/已软删 → EX-IP-ARG-401；冲突 → EX-IP-ARG-301 |
| **查询侧说明** | **不**经本 IF 改启停状态 |

### IF-IP-ARG-05 删除自动计费配置

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 用户确认**删除** |
| **主路径步骤** | 校验 **BR-IP-ARG-001** → `DeleteAutoGenRuleCommand(ruleId)` → 领域 `deleteAutoGenRule`（**软删**） |
| **关键分支** | 已删行再次删除按幂等策略拒绝或静默（以实现契约为准） |
| **失败分支** | 行不存在 → EX-IP-ARG-401 |
| **查询侧说明** | 软删后 **IF-IP-ARG-02** 不再展示 |

### IF-IP-ARG-06 启用自动计费配置

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 用户对 **INACTIVE** 行点**启用** |
| **主路径步骤** | 校验 **BR-IP-ARG-001** → `ActivateAutoGenRuleCommand(ruleId)` → 领域 `activateAutoGenRule`（**INACTIVE→ACTIVE**） |
| **关键分支** | 当前非 INACTIVE → 拒绝 |
| **失败分支** | 状态不允许 → EX-IP-ARG-501 |
| **查询侧说明** | — |

### IF-IP-ARG-07 停用自动计费配置

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 用户对 **ACTIVE** 行点**停用** |
| **主路径步骤** | 校验 **BR-IP-ARG-001** → `SuspendAutoGenRuleCommand(ruleId)` → 领域 `suspendAutoGenRule`（**ACTIVE→INACTIVE**） |
| **关键分支** | 当前非 ACTIVE → 拒绝 |
| **失败分支** | 状态不允许 → EX-IP-ARG-501 |
| **查询侧说明** | 停用后滚费任务 **IF-IP-ARG-08** 不再命中 |

### IF-IP-ARG-08 住院滚费定时任务

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 调度器 **00:05** 触发；租户上下文已绑定 |
| **主路径步骤** | ① 计算 **billDate**（定时 00:05 窗口缺省前一日；手工未传参缺省当日，**BR-IP-ARG-006**）。② `listInpatientAutoRollEncounterContexts` 扫描已分床未清床 encounter（含 `primaryNurseId`/`endTime`）。③ App Gate：入院跨午夜（**BR-IP-ARG-007**）→ **出院日及之后跳过**（**BR-IP-ARG-008** 记进不计出）→ 无账户跳过。④ **床位分支**：仅 ACTIVE 配置（**BR-IP-ARG-013**）。⑤ **护理分支**：`findLastEffectiveNursingLevelForAutoRoll` 仅 **`VERIFIED`**（**BR-IP-ARG-009**；停嘱即退池）。⑥ 单 encounter 失败**不**阻断批次 |
| **关键分支** | 入院日未跨午夜 → 跳过；出院日及之后 → 跳过；无 ACTIVE 配置 → 仅跑护理；护理已停嘱且无新 VERIFIED → 跳过护理；幂等命中 → 跳过 |
| **失败分支** | 定价不可解析 → 记失败跳过该行；在院列表为空 → 正常结束 |
| **查询侧说明** | 依赖 **GAP-IP-ARG-001～004** 已关闭之 QueryService / 写能力；**应用层** `executeInpatientAutoRollJob` + `NurseStationAutoRollEncounterProcessor` **已落地**（`zxplat/health-application`） |

### IF-IP-ARG-09 已滚费用记录列表（配置行 / 护理行）

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 护士在 **自动计费** Tab 展开某条床位配置行或护理级别行 |
| **主路径步骤** | 校验 **BR-IP-ARG-001/020**（`ruleId` 与护理 `serviceRequestId` 二选一）→ 床位：确认 `ruleId` 属于本就诊 → `findAutoRollBedServiceRequestIdByRuleId`；护理：`findNursingLevelForAutoRollPostedCharges` → `listOrderExecuteChargesByServiceRequestId`（可选 `dateFrom`/`dateTo`）→ 装配行（`procedureId`、金额、状态、`cancelable`） |
| **关键分支** | 无已滚费用 → 空列表；已结算行 `cancelable=false`；业务日空时不限 occurrence 日期 |
| **失败分支** | 就诊不可见/锚点未指定 → EX-IP-ARG-201；配置或护理医嘱不存在 → EX-IP-ARG-401；无关联床位 SR → EX-IP-ARG-902；加载异常 → EX-IP-ARG-901 |
| **查询侧说明** | **纯读**；取消写路径见 **AS-IP-NUR-012 IF-IP-CXL-01**（入参 `procedureIds`） |

---

## 5. 业务异常与用户提示

| 异常 ID | 场景 | 用户提示语义 | 可重试 |
| --- | --- | --- | --- |
| EX-IP-ARG-201 | 就诊不在护士站可见范围 | 当前患者不可操作 | 否 |
| EX-IP-ARG-301 | 配置组合键冲突 | 该诊疗项目已配置，请勿重复添加 | 否 |
| EX-IP-ARG-302 | 类目禁止 / 定价无效或已停用 | 护理/转科/出院/嘱托不能配置，或所选项目定价无效，请重新选择 | 否 |
| EX-IP-ARG-401 | 配置不存在或已删除 | 配置不存在或已失效，请刷新列表 | 否 |
| EX-IP-ARG-501 | 启停状态不允许 | 当前状态不允许该操作 | 否 |
| EX-IP-ARG-901 | 已滚费用列表加载失败 | 计费记录加载失败，请稍后重试 | 是 |
| EX-IP-ARG-902 | 配置未关联床位滚费医嘱 | 该配置尚未关联床位滚费医嘱，无法查看计费记录 | 否 |

> 定时任务失败以运维日志/监控告警为主，**不**弹窗至护士站 UI。  
> **IF-IP-ARG-01** 选品失败码复用 **AS-IP-NUR-005** **EX-IP-PRC-101**。
> **取消已滚费用**失败码复用 **AS-IP-NUR-012** 取消执行异常码。

---

## 6. 事件语义与消费方

| 事件 | 触发 IF | 语义 | 消费方 |
| --- | --- | --- | --- |
| `ChargeItemAutoGenEditedEvent` | IF-IP-ARG-03～07 | 配置增删改启停；`aggregateId`=表 **id** | 可选缓存刷新；**非**滚费触发器 |
| `AutoRollServiceRequestCreatedEvent` | IF-IP-ARG-03 | 新建配置 ensure 长期床位 SR（`generate_source=auto-roll-fees`） | 执行列表读侧剔除（**BR-IP-ARG-019**） |
| 滚费执行落账 | IF-IP-ARG-08 | `createPlannedChargeItemFromOrderExecution`（Charge `generate_source=order-execute`） | 费用明细读侧（**AS-IP-NUR-006**） |

> 滚费**不**订阅 `ChargeItemAutoGenEditedEvent` 即时执行；**仅**定时任务 **IF-IP-ARG-08** 驱动。

---

## 7. 非功能性需求

| 维度 | 要求 |
| --- | --- |
| **调度** | 每日 **00:05**（租户本地时区）；单租户串行或分片策略由基础设施定 |
| **幂等** | 任务重跑同 billDate **不**重复落账（**BR-IP-ARG-010**） |
| **性能** | 在院 encounter 批量扫描；定价/配置读侧批量组装，避免 N+1 |
| **一致性** | 单条滚费落账同事务（Request + Charge）；配置 CRUD 单规则单事务 |
| **审计** | 配置写操作带 `operatorId`；滚费任务带系统操作人/租户 |
| **安全** | 配置 IF 须护士站权限；任务仅内部调度触发 |

---

## 8. 测试要点（业务视角）

| 类别 | 要点 |
| --- | --- |
| **配置 CRUD** | 新增可日结诊疗配置（业务时间默认日末、计费人默认登录人）；护理/转科/出院/嘱托拒绝；重复组合拒绝；改数量/业务时间/计费人；软删后列表不可见；启停后滚费命中变化 |
| **只读护理** | Tab 展示护理级别但不写入配置表；无护理医嘱时空态 |
| **滚费-床位** | 00:05 滚前一日；分床～清床候选 + **出院日不计**（记进不计出）；跨午夜分床日；ACTIVE 配置 quantity；INACTIVE/已删不参与 |
| **滚费-护理** | 仅 VERIFIED；STOPPED 停嘱即退池（同配置停用）；换级取最后 VERIFIED；时刻日末；人=责任护士 |
| **手工补跑** | 任务参数 `billDate` 缺省为**前一日**；**配置创建日晚于 billDate 不参与**（BR-IP-ARG-013）；补跑当日须显式传 `billDate=当日`；费用明细查询日期须覆盖 billDate（配置项 `occurrence_time`/`recorded_time` = `billDate`+规则 `business_time`；护理为日末 `23:59:59`） |
| **幂等** | 同 billDate 重跑不重复；手工取消划价后次日仍滚 |
| **非目标** | **无**复制模版入口；**无**转科/请假/欠费冻结 v1 逻辑 |

---

## 9. 关联文档与变更历史

| 类型 | 路径 |
| --- | --- |
| 领域模型 | `docs/领域/DomainModel/DM-CUS-002-ChargeItemAutoGen-收费明细自动生成规则.md |
| 领域 SDD | `health/DomainModel-SDD/DM-CUS-002-ChargeItemAutoGen-收费明细自动生成规则-SDD.md` |
| 上游 AS | `health/AppService/AS-IP-NUR-001-住院护士站患者管理.md` |
| 同页 AS | `health/AppService/AS-IP-NUR-005-住院护士站划价.md`（划价页宿主；**自动计费 Tab 在本文**） |
| 应用 SDD | `health/AppService-SDD/AS-IP-NUR-013-住院护士站自动计费-SDD.md` |
| 同页宿主 SDD | `health/AppService-SDD/AS-IP-NUR-005-住院护士站划价-SDD.md` |
| 关联 AS | `health/AppService/AS-IP-NUR-002-住院护士站入出转管理.md`（分床/清床）；`health/AppService/AS-IP-NUR-003-住院护士站医嘱校对.md`（护理校对）；`health/AppService/AS-IP-NUR-006-住院护士站费用明细.md`（来源展示） |
| ChargeItem / SR | `docs/领域/DomainModel/DM-ADM-011-ChargeItem-收费明细.md |

---

## 10. 成码落地状态（与代码对齐）

| 层级 | 状态 | 落点 |
| --- | --- | --- |
| **领域读** | 已落地 | `findLastEffectiveNursingLevelForAutoRoll`、`listNursingLevelsForAutoRollTab`、`findNursingLevelForAutoRollPostedCharges`、`findAutoRollBedServiceRequestIdByRuleId`、`listInpatientAutoRollEncounterContexts`（含 `primaryNurseId`）、`existsOrderExecuteChargeForServiceRequestBillDate`、`listOrderExecuteChargesByServiceRequestId`、`ChargeItemAutoGen` 五读 |
| **领域写** | 已落地 | **`ensureAutoRollBedLongTermServiceRequest` 仅 IF-IP-ARG-03**；`createPlannedChargeItemFromOrderExecution`（滚费编排 IF-08）；`ChargeItemAutoGen` 五写；取消已滚费用复用 `voidOrderExecuteChargeItemsByProcedureIds`（CXL-01）；`createFromAutoRoll`/`priceByAutoRoll` **已废弃**（滚费无新调用） |
| **应用层** | 已落地 | `NurseStationInpatientBillingAppService` IF-IP-ARG-02～09；`NurseStationAutoRoll*` + `InpatientAutoRollOrderExecutionOrchestrationSupport` |
| **IF-IP-ARG-01** | 已落地 | 复用 **IF-IP-PRC-01** + `productTableNames=[wkf_activity_definition]`（**不**强制 `categoryCode=51`；写侧 **BR-IP-ARG-003** 黑名单） |
| **异常码** | 已落地 | `InpatientNurseStationConstants.EX_IP_ARG_201/301/302/401/501/901/902` |
| **域异常映射** | 已落地 | App `translateAutoGenBusinessException`：`CHARGE_ITEM_AUTO_GEN_*` → `EX-IP-ARG-*` |
| **可见性** | 已落地 | 自动计费 IF 使用 `EX-IP-ARG-201`（划价 IF 仍用 `EX-IP-PRC-102`） |
| **前端 Tab** | 已落地 | zxplat-ui 划价页「自动计费」Tab：配置 CRUD + 护理/配置展开已滚费用 + 取消（CXL-01）；业务日空=全住院期 |
| **调度** | **未绑定** | 基础设施 00:05 POST `executeInpatientAutoRollJob` 待配置 |

> IF-08 事务：任务入口由 **AppServiceAspect** 管理 UoW；单条落账 `NurseStationAutoRollLinePoster#postLine` 失败 **catch 后继续**，不波及其他 rule/encounter（**AS-IP-NUR-013-SDD** §5.2）。业务代码 **禁止** `@Transactional`。

### 变更历史

| 版本 | 日期 | 摘要 |
| --- | --- | --- |
| v1.6.0 | 2026-10-16 | **护理已滚费用展开/取消**：**IF-IP-ARG-02** `nursingLevels` + `listNursingLevelsForAutoRollTab`（billDate 空=全住院期）；**IF-IP-ARG-09**/**BR-IP-ARG-020** 支持护理 `serviceRequestId` 与 `dateFrom`/`dateTo`；取消仍复用 CXL-01 |
| v1.5.1 | 2026-09-27 | **BR-IP-ARG-015**：滚费 Procedure 编排改为 **create→start→挂主执行人→complete**（与划价/医嘱执行同序；修复 COMPLETED 后再挂执行人）；对齐 **DM-CLN-004** / **AS-IP-NUR-005 v2.18** |
| v1.5.0 | 2026-08-13 | **记进不计出 / 护理停嘱即退池**：**BR-IP-ARG-008** 增出院日 Gate（`end_time`）；**BR-IP-ARG-009** 仅 `VERIFIED` 滚费（`STOPPED` 同配置 INACTIVE） |
| v1.4.0 | 2026-08-12 | 配置增 **`business_time`/`billing_practitioner_id`**（**BR-IP-ARG-021**）；**BR-IP-ARG-015/019** 配置项读规则时刻与计费人，护理仍日末+责任护士 |
| v1.2.0 | 2026-07-17 | **IF-IP-ARG-09** 已滚费用展开读；**BR-IP-ARG-012** 改为复用取消执行（非划价冲销）；新增 **BR-IP-ARG-020**、EX-901/902；费用明细滚费行展示「自动计费」 |
| v1.1.1 | 2026-07-09 | **ensure 长期 SR 收口至 IF-IP-ARG-03**；IF-08 **只读** `findAutoRollBedServiceRequestIdByRuleId`，删除 lazy ensure / 历史补建；**BR-IP-ARG-019** 补充 `unit_code` 取活动 `packageUnitCode` |
| v1.1.0 | 2026-07-09 | 滚费改 **IF-IP-EXE-02** 长期医嘱执行轨；床位配置 ensure LONG_TERM SR（**BR-IP-ARG-019**）；Charge 来源改 **`order-execute`**；幂等改 **`serviceRequestId+billDate`**（**BR-IP-ARG-010**）；配置 SR 禁止手动执行 |
| v1.0.9 | 2026-07-09 | **BR-IP-ARG-007** 滚费 Gate 改锚就诊 `start_time`（首次分床；**转床不更新入院时间**）；`bedAssignedAt` 读模型为最早 BED 轨迹 |
| v1.0.8 | 2026-07-09 | **BR-IP-ARG-015** 滚费落账补齐 Procedure（对齐 IF-IP-PRC-02）；**BR-IP-ARG-006** 手工未传 `billDate` 缺省当日 |
| v1.0.7 | 2026-07-09 | **BR-IP-ARG-007** 滚费 Gate 改锚激活床位 `start_time`；**IF-IP-ARG-03** 缺省 `performingOrgId=organizationId`；§8 增补手工补跑与费用明细日期口径 |
| v1.0.6 | 2026-06-19 | 事务口径对齐框架 UoW（**AppServiceAspect**）；移除 per-line `@Transactional` 表述 |
| v1.0.5 | 2026-06-19 | **应用层已成码**：`NurseStationInpatientBillingAppService` IF-IP-ARG-02～08；§10 成码落地状态 |
| v1.0.4 | 2026-06-19 | **IF-IP-ARG-01** 改为复用 **IF-IP-PRC-01**（`categoryCode=51` 过滤）；**不新增**选品 HTTP；移除 **EX-IP-ARG-101** |
| v1.0.3 | 2026-06-19 | §2 IF 查询/写列对齐领域方法名；§3 Gap 关闭后文档与 DM v1.1.19 / v1.29 / v1.5.6 一致 |
| v1.0.2 | 2026-06-19 | §3 GAP-IP-ARG-001～004 **已关闭**（领域层 QueryService + 滚费写能力就绪，对齐 DM-WKF-004 / DM-ADM-002 / DM-ADM-011） |
| v1.0.1 | 2026-06-19 | UI/场景统称 **自动计费**（原「自动机费」）；§1.3 区分 **自动计费（UI）** 与 **住院滚费（任务）** |
| v1.0 | 2026-06-19 | 初版：划价页 **自动计费 Tab**（配置 CRUD + 护理只读）+ **00:05** 滚费任务；**不含复制模版**；对齐 **DM-CUS-002 v1.0.2** |
