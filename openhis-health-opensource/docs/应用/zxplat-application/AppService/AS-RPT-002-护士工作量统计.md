# 护士工作量统计 · 应用服务设计（AS）

> **文档职责**：说明「护士工作量统计」报表页的**接口契约（IF）**、业务语义、涉及聚合与 QueryService、规则（BR）、编排意图；作为 **health-application** 报表包实现的编排权威。
>
> **页面边界**：**门诊/住院单选必选**（**默认门诊**）→ **可选工作科室** → 日期窗 → **查询**；卡片墙按人分块；**两大业务 Tab**（**诊疗执行** / **耗材使用**），各 Tab 内 **汇总 / 明细** 双子 Tab（**默认汇总**）；**只读**；**导出仅当前主 Tab 的明细**。
>
> **编码**：AS-RPT-002 · **版本**：v1.10 · **日期**：2026-10-06

> **DOC-CONFLICT→金额口径已按 DD 收敛**：见 `health/设计决策/DD-报表中心-进售价口径与开立科室-2026-08.md`。禁 Payment 扫库；金额仅进/售价；**科室筛选 = 工作科室（岗位 `adm_pr_assign.org_id`）**，不是人员主档默认登录科室，也不是开立科室；诊疗无进价。存量默认登录科室由 Flyway **V12** 按分类补进对应角色岗位（NS→护士，TECH→医技）。

---

## 1. 场景概述

### 1.1 目标与边界

| 维度 | 说明 |
| --- | --- |
| **在内** | IF-RPT-NWL-01 页初始化；IF-RPT-NWL-02/03 **诊疗执行**汇总与明细分页；IF-RPT-NWL-04/05 **耗材使用**汇总与明细分页；IF-RPT-NWL-06 导出当前主 Tab 明细；IF-RPT-NWL-07 **护士卡片墙批量** |
| **在外** | 科室医生工作量（**AS-RPT-001**）；收费/退费/日结；药房发耗材；**MedicationRequest** 执行记录统计；医技工作站独立报表（本期检验/检查仍纳入护士口径）；数据权限 Gate；按**开立科室**（`requesting_org_id`）筛选（护士报表不使用） |
| **涉及域** | **Procedure** / **ProcedurePerformer**；**ServiceRequest** / **ActivityDefinition**；**DeviceDispense** / **DeviceRequest** / **DeviceDefinition**；**Encounter**；**Practitioner**；**PractitionerRole**（工作科室 `adm_pr_assign.org_id`）；**Organization**（护士站 / 医技） |
| **编排权威** | 本文 **§2、§4**；净执行对冲对齐 **DM-CLN-004 §6.2.6**；耗材护士路径对齐 **AS-OP-NUR-001 BR-NUR-014**、**AS-IP-NUR-005 BR-IP-PRC-019** |
| **数据锚点** | 诊疗执行以 **净剩 `COMPLETED` Procedure**（`request_table = wkf_service_request`）为准；耗材以护士站触发的 **有效发放行** 为准 |

### 1.2 角色与关键动作

| 角色 | 关键动作 |
| --- | --- |
| **护理管理 / 科室主任 / 财务** | 选 **门诊/住院**（必选）→ 可选 **工作科室** → 选日期 → **查询** → 看科室合计与各护士卡片 → 点卡片看个人明细 → **导出** |
| **系统** | 按 **`encounterScope`** 限定就诊域；按 **工作科室**（岗位 `adm_pr_assign.org_id`）收窄该科护士/医技集合；按单人可选再收窄；诊疗按 **ServiceRequest 类目**汇总；耗材按 **高/低值**汇总 |

### 1.3 术语与口径约定

| 术语 | 口径 |
| --- | --- |
| **门诊/住院（必选）** | 页面 **单选**：**门诊** / **住院**；持久化 **`encounterScope`** = **`OUTPATIENT`** / **`INPATIENT`**；**初始化默认门诊**；**汇总与明细查询均不可为空**；映射 `Encounter.class_enum`：**`AMB`** / **`IMP`**；切换时清空已选工作科室 |
| **默认工作科室（可选）** | 对应岗位 **`adm_pr_assign.org_id`**（人员维护「工作科室」；护士岗为护士站，医技岗为医技科室）；**未选** = 当前 **`encounterScope`** 下护士与医技全员工作量；**已选** = 仅统计在该科室挂了护士或医技岗位的人员；**禁止**用 `Practitioner.org_id` 或 `ChargeItem.requesting_org_id` 冒充 |
| **护士（可选）** | **未选** = 当前筛选下全部命中护士（全院或已选科室合计）；**已选** = 仅统计该护士（优先于科室集合） |
| **主 Tab** | **诊疗执行** | **耗材使用**——两类事实分开展示，**禁止**混在同一明细表 |
| **子 Tab** | 每个主 Tab 内 **汇总**（默认）/ **明细**；切换子 Tab 不切换筛选条件 |
| **诊疗执行事实** | 仅 **`wkf_service_request`** 锚定的 **Procedure**；**不含** `med_medication_request`（注射/输液/给药等由联动 **ServiceRequest** 承载，计在诊疗执行侧） |
| **净执行** | **`status_enum = COMPLETED`** 且**未被** `CANCEL`（`refund_id` 指向该 COMPLETED）对冲；长期医嘱多计划点按 **`occurrence_time` 分槽**各自计净次；**A 执行 → 取消 → C 再执行** = **C 计 1 次** |
| **执行人归属** | 净 COMPLETED 行的 **ProcedurePerformer** 主执行人；选护士时按执行人 id 收窄 |
| **执行时间窗** | 闭区间筛选 **`Procedure.recorded_time`**（实际操作时刻） |
| **执行汇总维度** | 按 **`ServiceRequest.category_enum`** 分组（如检验 **PROOF**、检查 **TEST**、治疗、护理等，见 **DM-WKF-004 §4.3**）；本期**不**拆医技独立报表 |
| **耗材纳入状态** | 读取全局 **`deviceAutoInventoryDeductSwich`**（**BR-NUR-014** 同开关）：**关** → **`PREPARED`**；**开** → **`COMPLETED`**；已取消回退至 **`PREPARATION`**、划价/执行取消软删或退发的行**不计** |
| **耗材执行人归属** | **关** → **`DeviceDispense.preparer_id`**；**开** → **`DeviceDispense.performer_id`** |
| **耗材时间窗** | **关** → **`prepare_time`**；**开** → **`dispense_time`**（缺失回退 **`prepare_time`**） |
| **耗材汇总维度** | 按 **`DeviceDefinition.hvcm_flag`**：**高值耗材** / **低值耗材**（**DM-ADM-008**） |
| **耗材计量** | 行数量 = **`COALESCE(dispense_quantity, quantity)`**；汇总为代数 **SUM** |
| **生成来源展示** | 统一 **`GenerateSource`** 枚举（**DM-ADM-011 §4.2** 等共用 code）；明细**单列**展示名：**诊疗** → 关联 **`ServiceRequest.generate_source_enum`**；**耗材** → 关联 **`DeviceRequest.generate_source_enum`**；**禁止** Request 与 ChargeItem 双列重复展示 |
| **明细排序** | **`recorded_time`**（诊疗）/ 耗材业务时间（见上）**倒序** |
| **分页** | 各主 Tab 的**明细**默认 **20** 条/页；**汇总无分页** |
| **导出** | **仅当前主 Tab**（诊疗执行 **或** 耗材使用）的**明细**数据；与对应明细 IF **同筛选、同口径、同排序**；**不含**汇总 sheet、**不含**另一主 Tab |
| **租户** | 仅当前租户；**不**做机构/个人数据权限 Gate |

#### 1.3.1 汇总 Tab 结构

| 主 Tab | 汇总行维度 | 计量列 |
| --- | --- | --- |
| **诊疗执行** | 各 **`category_enum`** 一行（展示类目名） | **次数**（净 COMPLETED Procedure 条数）；页脚另含 **`totalSaleAmount`** / **`totalPurchaseAmount`**（诊疗进价为 null） |
| **耗材使用** | **高值** / **低值** 各一行（或两行 + 合计） | **数量**（发放量 SUM）；页脚另含 **`totalSaleAmount`** / **`totalPurchaseAmount`** |

| 合计层级 | 口径 |
| --- | --- |
| **主 Tab 汇总页脚** | 当前 **`encounterScope` + 筛选条件**（含可选工作科室 / 单人）下该主 Tab 全部命中数据的合计；**未选科室且未选人** = 该域**全院**合计；**已选科室** = 该科全部护士/医技合计（**单块表**） |
| **明细 Tab** | 不重复汇总分块表；页脚可选展示与对应汇总 IF **一致**之 digest |

#### 1.3.2 明细 Tab 列（业务语义）

| 主 Tab | 必含列（语义） |
| --- | --- |
| **诊疗执行** | 项目名称；**诊疗类目**；**生成来源**（GenerateSource 展示名）；执行护士；**执行时间**（`recorded_time`）；患者/就诊标识（展示级）；**售价** / **进价**（诊疗进价 null） |
| **耗材使用** | 耗材名称；规格；**高/低值**；**生成来源**；执行护士（配药人/发药人口径见上）；**业务时间**；**数量**；单位；**售价** / **进价** |

> **金额列**：明细与汇总页脚展示关联 **ChargeItem** 的 **售价**（`total_price` 代数 SUM，**原价不改成折后**）与 **进价**（批号进价按 `quantity_unit` 分支；耗材有、诊疗无）；**已配置有效折扣时另展示优惠**（`|total_price|−|discount_amount|`，**BR-RPT-NWL-023**）；展示 **2 位 HALF_UP**；无匹配 chargeItem 时行售价为 **0**，优惠 **0**，进价 **null**。

### 1.4 本期范围与非目标

| 类别 | 内容 |
| --- | --- |
| **本期** | IF-RPT-NWL-01～07；双主 Tab × 双子 Tab；诊疗净执行 + 耗材净发放；GenerateSource 单列回显；明细/汇总页脚 **ChargeItem 金额** enrich；卡片墙批量；**工作科室**科室合计 |
| **非目标** | **`med_medication_request` Procedure** 统计；按**开立科室**（`requesting_org_id`）筛选；实收/成本/净收入；挂号登记来源（`registration`）纳入；**`auto-roll-fees`** 自动滚费纳入；医技独立报表（本期检验/检查仍计护士）；汇总 Tab 导出；跨主 Tab 合并导出 |

### 1.5 领域清单（聚合 + 职责 + DM 路径）

| 聚合（域） | 职责摘要 | DM 路径 |
| --- | --- | --- |
| **Procedure** | 诊疗执行事实、净执行对冲 | `DM-CLN-004-Procedure-执行记录.md` |
| **ServiceRequest** | 请求锚点、`category_enum`、`generate_source_enum` | `DM-WKF-004-ServiceRequest-服务申请.md` |
| **ActivityDefinition** | 诊疗项目名称 | `DM-WKF-001-ActivityDefinition-诊疗项目定义.md` |
| **DeviceDispense** | 耗材发放事实、配药人/发药人、数量 | `DM-WKF-007-DeviceDispense-耗材发放.md` |
| **DeviceRequest** | 耗材请求、`generate_source_enum` | `DM-WKF-003-DeviceRequest-耗材请求.md` |
| **DeviceDefinition** | 耗材名称、`hvcm_flag` | `DM-ADM-008-DeviceDefinition-器材定义.md` |
| **Encounter** | `class_enum` 门诊/住院域 | `DM-ADM-002-Encounter-就诊.md` |
| **Practitioner** | 护士展示、筛选 | `DM-ADM-005-Practitioner-医务人员.md` |

### 1.6 业务规则（BR-*）

| 规则 ID | 描述 | 绑定 IF |
| --- | --- | --- |
| **BR-RPT-NWL-001** | **`encounterScope` 必填**（`OUTPATIENT` / `INPATIENT`）；初始化默认 **`OUTPATIENT`**；`dateFrom`、`dateTo` **必填**且 `dateFrom ≤ dateTo`；默认当日；**`organizationId`（工作科室）可选**；**`practitionerId` 可选** | 01～06 |
| **BR-RPT-NWL-001a** | **已选工作科室**：解析护士岗或医技岗且 **`adm_pr_assign.org_id` = 该科室** 的人员主键集合写入读查询；**空集合须空结果**（禁止回落全院）；金额随执行人归属汇总，**不再**按 `requesting_org_id` 二次收窄，**禁止**再按 `Practitioner.org_id` 过滤 | 01～07 |
| **BR-RPT-NWL-002** | **`encounterScope`** 映射 `Encounter.class_enum`（`OUTPATIENT`→`AMB`，`INPATIENT`→`IMP`）；**禁止**跨域混查 | 02～06 |
| **BR-RPT-NWL-003** | 诊疗执行：仅 **`request_table = wkf_service_request`** 的 Procedure；**排除** `med_medication_request` | 02、03、06 |
| **BR-RPT-NWL-004** | 诊疗净执行：对齐 **DM-CLN-004 §6.2.6**——`COMPLETED` 且未被 `CANCEL.refund_id` 对冲；**排除** `STOPPED`（不执行） | 02、03、06 |
| **BR-RPT-NWL-005** | 诊疗时间筛选：**`recorded_time`** 闭区间 | 02、03、06 |
| **BR-RPT-NWL-006** | 选护士时：诊疗按 **ProcedurePerformer** 主执行人 id 收窄 | 02、03、06 |
| **BR-RPT-NWL-007** | 诊疗汇总：**`GROUP BY category_enum`**；类目展示名来自 **ActivityDefinition** / 码表 | 02 |
| **BR-RPT-NWL-008** | 诊疗明细：回显 **`ServiceRequest.generate_source_enum`** → **GenerateSource** 展示名（含 **`nurse-pricing`** 护士划价、**`doctor-prescription`** 医生开立、**`order-execute`** 医嘱执行派生等） | 03、06 |
| **BR-RPT-NWL-009** | 诊疗明细排序：**`recorded_time` DESC** | 03、06 |
| **BR-RPT-NWL-010** | 耗材纳入状态随 **`deviceAutoInventoryDeductSwich`**：**关**=`PREPARED`，**开**=`COMPLETED`；取消/回退后不再计入 | 04、05、06 |
| **BR-RPT-NWL-011** | 耗材归属：**关**→**`preparer_id`**；**开**→**`performer_id`** | 04、05、06 |
| **BR-RPT-NWL-012** | 耗材时间：**关**→**`prepare_time`**；**开**→**`dispense_time`**（空则 **`prepare_time`**） | 04、05、06 |
| **BR-RPT-NWL-013** | 耗材汇总：**`GROUP BY hvcm_flag`**（高值 / 低值） | 04 |
| **BR-RPT-NWL-014** | 耗材数量：**`COALESCE(dispense_quantity, quantity)`** 代数 SUM | 04、05、06 |
| **BR-RPT-NWL-015** | 耗材明细：回显 **`DeviceRequest.generate_source_enum`** → **GenerateSource** 展示名 | 05、06 |
| **BR-RPT-NWL-016** | **未选科室且未选护士**：汇总为当前 **`encounterScope`** **全院单块合计**；**已选科室**：该科全部护士合计；**已选护士**：单人合计 | 02、04 |
| **BR-RPT-NWL-017** | 点「查询」默认激活 **诊疗执行 · 汇总**；切换主 Tab / 子 Tab 时若明细未加载则调对应明细 IF page=1 | 前端交互；02～05 |
| **BR-RPT-NWL-018** | 汇总 IF 与对应明细 IF **筛选 payload 一致**；明细翻页**仅**调该主 Tab 明细 IF | 02/03、04/05 |
| **BR-RPT-NWL-019** | 导出 **IF-RPT-NWL-06** 仅 **当前主 Tab** 明细；**禁止**附带另一主 Tab 或汇总 | 06 |
| **BR-RPT-NWL-020** | 护士划价产生的诊疗 **ServiceRequest**（**`generate_source_enum = nurse-pricing`**）**须**可通过 Procedure 统计（划价确认写 Procedure；与 **AS-IP-NUR-005** **BR-IP-PRC-007** 对齐） | 02、03 |
| **BR-RPT-NWL-021** | 明细 **金额**：按行锚点匹配 **ChargeItem** 后 **`total_price` 代数 SUM**——诊疗：`procedure_id` 或 `service_table=wkf_service_request` + `service_id`；耗材：`service_table=wkf_device_dispense` + `service_id`、或 `dispense_table=wkf_device_dispense` + `dispense_id`、或 `service_table=wkf_device_request` + `service_id`；展示 **2 位 HALF_UP** | 02、03、05、06 |
| **BR-RPT-NWL-022** | 汇总页脚 **`totalAmount`** = 当前筛选下对应明细 IF **全量行金额代数和**（与 **BR-RPT-NWL-021** 同 enrich 规则）；**禁止**与次数/数量混在同一列；**禁止**套 `EXPORT_MAX_ROWS` 静默截断页脚（导出上限仅约束导出） | 02、04 |
| **BR-RPT-NWL-023** | **优惠/实收列**：售价保持 `total_price` 原价；优惠=`|total_price|−|discount_amount|`；实收=折后应收。**仅** IF-01 `discountFeatureEnabled=true` 时诊疗/耗材明细、卡片、页脚、导出展示优惠与实收；false 时不展示。落点 `ChargeItemDiscountDisplaySupport` | 01～07 |

---

## 2. 页面与接口总览（契约概览）

| IF 编号 | 用户动作 | 接口语义 | 读/写 | 关键规则 | 关键状态变化 | 涉及领域 | 查询服务 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| IF-RPT-NWL-01 | 进入报表页 | **门诊/住院单选**（**默认门诊**，必选）、**工作科室**下拉（可选，护士站+医技）、人员下拉（随科室收窄）、默认日期 | 读 | BR-RPT-NWL-001、001a、002 | 无 | Practitioner / Organization / Encounter | `PractitionerRoleScopeLoader`；`OrganizationQueryService` |
| IF-RPT-NWL-02 | 设筛选 + 点「查询」（诊疗·汇总） | 诊疗执行 **按 category_enum 汇总次数** + 页脚 **`totalExecutionCount` / `totalAmount`** | 读 | BR-RPT-NWL-001～009、016、020～022 | 无 | Procedure / ServiceRequest / Encounter / ChargeItem | `ProcedureQueryService#summarizeNurseExecutionWorkloadByCategory`；`NurseWorkloadQuerysupport；`NurseWorkloadAmountEnrichmentsupport |
| IF-RPT-NWL-03 | 诊疗执行 · 明细 Tab / 翻页 | 净执行 Procedure **分页明细** + GenerateSource + 类目 + **金额** | 读 | BR-RPT-NWL-001～009、018、021 | 无 | Procedure / ServiceRequest / ActivityDefinition / Practitioner / ChargeItem | `ProcedureQueryService#pageNurseExecutionWorkloadDetail`；`NurseWorkloadQuerysupport；`NurseWorkloadAmountEnrichmentsupport |
| IF-RPT-NWL-04 | 设筛选 + 点「查询」（耗材·汇总） | 耗材 **按高/低值汇总数量** + 页脚 **`totalQuantity` / `totalAmount`** | 读 | BR-RPT-NWL-001～002、010～016、022 | 无 | DeviceDispense / DeviceRequest / DeviceDefinition / Encounter / ChargeItem | `DeviceDispenseQueryService#summarizeNurseConsumableWorkloadByHvcm`；`NurseWorkloadQuerysupport；`NurseWorkloadAmountEnrichmentsupport |
| IF-RPT-NWL-05 | 耗材使用 · 明细 Tab / 翻页 | 有效发放行 **分页明细** + GenerateSource + 数量 + **金额** | 读 | BR-RPT-NWL-001～002、010～015、018、021 | 无 | DeviceDispense / DeviceRequest / DeviceDefinition / Practitioner / ChargeItem | `DeviceDispenseQueryService#pageNurseConsumableWorkloadDetail`；`NurseWorkloadQuerysupport；`NurseWorkloadAmountEnrichmentsupport |
| IF-RPT-NWL-06 | 点「导出」 | **当前主 Tab** 明细 Excel 全量 | 读 | BR-RPT-NWL-019、001～015 | 无 | 同 IF-03 或 IF-05 | 同当前主 Tab 明细 IF |
| IF-RPT-NWL-07 | 卡片墙加载 | 一次扫库后按护士分桶，返回类目/耗材 chip + 合计（轻量 enrich） | 读 | BR-RPT-NWL-001～002、007、013、022 | 无 | 同 IF-03 + IF-05 | `listNurseExecutionWorkloadDetail` / `listNurseConsumableWorkloadDetail` |

> **交互约定**：点「查询」→ **并行** 当前可见主 Tab 的汇总 IF + 明细 IF（page=1）；若用户位于 **诊疗执行 · 汇总**，并行 **IF-02 + IF-03**；切换至 **耗材使用** 后首次查询并行 **IF-04 + IF-05**；**切换主 Tab 不自动重查**（沿用上次查询条件，切子 Tab 懒加载明细）；导出 **IF-06** 绑定**当前主 Tab**。

---

## 3. 缺口跟踪（Gap）

**无待跟踪缺口**（2026-07-03：`ProcedureQueryService#summarizeNurseExecutionWorkloadByCategory` / `#pageNurseExecutionWorkloadDetail`；`DeviceDispenseQueryService#summarizeNurseConsumableWorkloadByHvcm` / `#pageNurseConsumableWorkloadDetail` 及 `NurseWorkloadQuerysupport 已落地，见 **DM-CLN-004 §6.2.10～6.2.11**、**DM-WKF-007 §6.2.20～6.2.22**、**DM-ADM-002 §6.2.32**、**DM-WKF-004 §6.2.26**）。

> **以下不算 Gap**：GenerateSource / 类目 / hvcm 展示名组装、Excel 列映射 — **应用层** report 包拼装；查询前须调用 **`NurseWorkloadQuerySupport#prepareNurseWorkloadQueryScopes`**（`application/report/nurseworkload/support`）。

---

## 4. 接口编排说明书（业务步骤）

### IF-RPT-NWL-01 页面初始化

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 用户进入护士工作量统计页 |
| **主路径步骤** | 下发 **门诊/住院单选**（**默认 `OUTPATIENT`**，**不可取消**）→ 下发**护士下拉**（可选，含「全部」）→ **默认日期=当日** → 默认 **诊疗执行 · 汇总** |
| **关键分支** | 切换门诊/住院 → **清空**已选护士 |
| **失败分支** | 主数据不可用 → EX-RPT-NWL-101 |
| **查询侧说明** | 只读 Practitioner；前端 API 须 `publicRequestClient` 解包 `data` |

### IF-RPT-NWL-02 诊疗执行汇总查询

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 已选 **`encounterScope`**、有效日期窗；用户点「查询」或停留在 **诊疗执行 · 汇总** |
| **主路径步骤** | **①** 校验 **BR-RPT-NWL-001～002、006**。**②** 加载命中净 COMPLETED Procedure（**BR-RPT-NWL-003、004** + **`Encounter.class_enum`** + 可选护士 + **`recorded_time`**）。**③** 关联 **ServiceRequest** 取 **`category_enum`**。**④** **`GROUP BY category_enum`** 计次（**BR-RPT-NWL-007**）。**⑤** 返回类目行 + **页脚合计**（**BR-RPT-NWL-016**） |
| **关键分支** | 无命中：空表 + 合计 0；未选护士 = **该域全院**合计 |
| **失败分支** | 日期非法 → EX-RPT-NWL-102；查询失败 → EX-RPT-NWL-104 |
| **查询侧说明** | 纯读；禁止 App 全表内存过滤 |

### IF-RPT-NWL-03 诊疗执行明细分页查询

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 与 IF-02 **相同筛选**；用户切 **诊疗执行 · 明细** 或翻页 |
| **主路径步骤** | **①** 同 IF-02 筛选 Gate。**②** **`pageNurseExecutionWorkloadDetail`** 得当前页 Procedure 键集。**③** 批量 **ServiceRequest** + **ActivityDefinition** enrich。**④** 回填 **GenerateSource** 展示名（**BR-RPT-NWL-008**）。**⑤** 批量 **Practitioner**、患者/就诊展示字段。**⑥** **BR-RPT-NWL-009** 排序分页返回 |
| **关键分支** | 无命中：空列表 |
| **失败分支** | 同 IF-02 |
| **查询侧说明** | 翻页 **不**触发 IF-02（**BR-RPT-NWL-018**） |

### IF-RPT-NWL-04 耗材使用汇总查询

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 已选 **`encounterScope`**、有效日期窗；用户点「查询」且当前主 Tab 为 **耗材使用 · 汇总**（或与 IF-02 并行首次加载） |
| **主路径步骤** | **①** 校验 **BR-RPT-NWL-001～002**。**②** 读取 **`deviceAutoInventoryDeductSwich`** 确定纳入状态与归属/时间字段（**BR-RPT-NWL-010～012**）。**③** 加载命中发放行 + **`Encounter.class_enum`** + 可选护士。**④** 关联 **DeviceDefinition** 取 **`hvcm_flag`**。**⑤** **`GROUP BY hvcm_flag`** 数量 SUM（**BR-RPT-NWL-013、014**）。**⑥** 页脚合计（**BR-RPT-NWL-016**） |
| **关键分支** | 无命中：空表 + 合计 0 |
| **失败分支** | 日期非法 → EX-RPT-NWL-102；查询失败 → EX-RPT-NWL-104 |
| **查询侧说明** | 纯读 |

### IF-RPT-NWL-05 耗材使用明细分页查询

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 与 IF-04 **相同筛选**；用户切 **耗材使用 · 明细** 或翻页 |
| **主路径步骤** | **①** 同 IF-04 Gate。**②** **`pageNurseConsumableWorkloadDetail`**。**③** 批量 **DeviceRequest** → **GenerateSource**（**BR-RPT-NWL-015**）。**④** **DeviceDefinition** enrich。**⑤** 业务时间倒序分页返回 |
| **关键分支** | 无命中：空列表 |
| **失败分支** | 同 IF-04 |
| **查询侧说明** | 翻页 **不**触发 IF-04（**BR-RPT-NWL-018**） |

### IF-RPT-NWL-06 导出明细 Excel

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 用户点「导出」 |
| **主路径步骤** | 识别**当前主 Tab** → 与 **IF-03**（诊疗）或 **IF-05**（耗材）**相同 Gate 与组装**（**BR-RPT-NWL-019**）→ 全量拉取明细 → Excel **仅当前主 Tab 明细列** → 返回文件流 |
| **关键分支** | 无数据 → EX-RPT-NWL-201 或空表导出（SDD 定一种） |
| **失败分支** | 业务失败同对应明细 IF；超行上限 → EX-RPT-NWL-105 |
| **查询侧说明** | **禁止**导出汇总或另一主 Tab |

### IF-RPT-NWL-07 护士卡片墙批量查询

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 与 IF-02/04 **相同筛选**（不含单护士收窄）；用户点「查询」后加载卡片墙 |
| **主路径步骤** | **①** Gate 同汇总。**②** 一次加载诊疗+耗材事实行。**③** 轻量 enrich（类目术语、耗材品名/规格/hvcm、金额锚点 IN；**跳过**患者/就诊展示）。**④** 按 `practitionerId` 分桶组装 chip + 合计。**⑤** 返回有数据护士的 `cards[]`（零值护士由前端用下拉补齐） |
| **关键分支** | 无命中：空 `cards[]` |
| **失败分支** | 同 IF-02/04 |
| **查询侧说明** | **禁止**前端翻页 IF-03/05 全量明细再分桶；弹窗明细仍走 IF-03/05 |

---

## 5. 业务异常与用户提示

| 异常 ID | 场景 | 用户提示 | 可重试 |
| --- | --- | --- | --- |
| EX-RPT-NWL-101 | 初始化失败 | 页面配置加载失败，请稍后重试 | 是 |
| EX-RPT-NWL-102 | 日期区间非法或未选门诊/住院 | 请选择门诊或住院及有效的查询日期 | 是 |
| EX-RPT-NWL-104 | 汇总或明细查询失败 | 查询失败，请稍后重试 | 是 |
| EX-RPT-NWL-105 | 导出失败或超上限 | 导出失败，请缩小日期范围后重试 | 是 |
| EX-RPT-NWL-201 | 导出无明细（若采用拒绝策略） | 当前条件下无明细可导出 | 是 |

---

## 6. 事件语义与消费方

| 事件 | 来源 | 对本页的影响 |
| --- | --- | --- |
| `ProcedureRecordCompletedEvent` / `ProcedureRecordCancelledEvent` | **AS-OP-NUR-001** / **AS-IP-NUR-004** / **AS-IP-NUR-012** | 手动查询后反映净执行变化 |
| `DeviceDispensePreparedEvent` / 发放完成等价事件 | 护士站耗材路径 | 手动查询后反映耗材统计 |
| `NursePricingServiceRequestCreatedEvent` | **AS-IP-NUR-005** | 划价诊疗/耗材来源行可识别；Procedure 落库后纳入诊疗执行 |

> 本页 **不发布**领域事件；**手动查询**为准。

---

## 7. 非功能性需求

| 维度 | 要求 |
| --- | --- |
| **性能** | 诊疗 IF-02/03 与耗材 IF-04/05 **独立 SQL**；Procedure、ServiceRequest、DeviceDispense、Definition **批量 IN**；导出分批拉取。**就诊 scope** 须叠加统计日窗（与就诊 `startTime/endTime` 相交），**禁止**按 AMB/IMP 无日期全历史扫。汇总页脚 **`totalAmount`**（**BR-RPT-NWL-022**）对事实行 **一次加载**后按 `ID_IN_QUERY_CHUNK_SIZE` 分块 ChargeItem enrich 累加；耗材售价/进价 **同一次**扫描；**禁止**按页重复扫净执行/候选发放全集，**禁止**为售价/进价各扫一次，**禁止**复用导出回路的展示 enrichment，**禁止**套 `EXPORT_MAX_ROWS` 静默截断页脚；金额 enrich **仅按锚点 IN**（procedure / serviceRequest / dispense / deviceRequest），**禁止**按就诊全量拉 ChargeItem。Support 预解析按 IF **按需** enrich SR 类目或耗材 hvcm，**禁止**诊疗/耗材 IF 互相扫无关聚合。**IF-07**：护士卡片墙 **一次**扫库按人分桶（轻量 enrich），**禁止**前端翻页 IF-03/05 全量明细再分桶，**禁止**「每位护士 × 汇总/明细」 |
| **进价列角（DD §2.4 Type B）** | 卡片墙页脚耗材进价 = 当次 `cards[].consumablePurchaseAmountTotal` 代数和（与 IF-07 同筛选）；前端纠偏；诊疗进价恒空 |
| **一致性** | 只读；查询时刻快照 |
| **权限** | 租户隔离；**无**额外 Gate |
| **导出** | **AnnotatedExcelExporter**；**仅当前主 Tab 明细列**；行数上限 SDD（建议 100000） |

---

## 8. 测试要点（业务视角）

| 类别 | 要点 |
| --- | --- |
| **门诊/住院** | 必选；默认门诊；切换清空护士；汇总与明细均校验 |
| **护士** | 未选=全院该域合计（**单块**，非分护士块）；已选=单人 |
| **主/子 Tab** | 诊疗与耗材分离；各含汇总/明细；默认诊疗·汇总 |
| **净执行** | A 执行→取消→C 再执行仅 C 计 1；长期多 occurrence 各计；不含 med Procedure |
| **划价** | `nurse-pricing` 来源可识别；Procedure 落库后计入（**BR-RPT-NWL-020**） |
| **耗材开关** | 关：PREPARED + preparer + prepare_time；开：COMPLETED + performer + dispense_time |
| **耗材取消** | 护士取消执行/划价取消后不再计入 |
| **GenerateSource** | 明细**单列**；诊疗来自 SR、耗材来自 DeviceRequest |
| **金额** | 明细行与汇总页脚 **`totalAmount`** 与 **BR-RPT-NWL-021/022** 一致；无 chargeItem 匹配时为 0 |
| **导出** | 仅当前主 Tab 明细；不含汇总与另一 Tab；导出列含 **金额** |
| **检验/检查** | 本期计入诊疗执行类目汇总 |

---

## 9. 关联文档与变更历史

| 类型 | 路径 |
| --- | --- |
| **Procedure** | `docs/领域/DomainModel/DM-CLN-004-Procedure-执行记录.md |
| **ServiceRequest** | `docs/领域/DomainModel/DM-WKF-004-ServiceRequest-服务申请.md |
| **DeviceDispense** | `docs/领域/DomainModel/DM-WKF-007-DeviceDispense-耗材发放.md |
| **GenerateSource** | `docs/领域/DomainModel/DM-ADM-011-ChargeItem-收费明细.md §4.2 |
| **门诊护士站** | `health/AppService/AS-OP-NUR-001-门诊护士站.md` |
| **住院执行/划价** | `health/AppService/AS-IP-NUR-004-住院护士站医嘱执行.md`、`AS-IP-NUR-005-住院护士站划价.md` |
| **并列 RPT** | `health/AppService/AS-RPT-001-科室医生工作量统计.md` |
| **SDD** | `health/AppService-SDD/AS-RPT-002-护士工作量统计-SDD.md`（**v1.4**，2026-08-19） |

| 版本 | 日期 | 变更摘要 |
| --- | --- | --- |
| v1.10 | 2026-10-06 | **BR-RPT-NWL-023**：售价保持原价；已配置有效折扣时展示优惠列；init `discountFeatureEnabled` |
| v1.9 | 2026-09-19 | **科室统计改为工作科室**：`adm_pr_assign.org_id`（护士站+医技）；Flyway V12 按分类把存量默认登录科室补进对应角色岗位（NS→护士，TECH→医技） |
| v1.8 | 2026-08-24 | **汇总页脚性能**：`sumConsumableAmountsForQuery` 售价/进价同扫；诊疗/耗材金额 IN 分块 enrich |
| v1.7 | 2026-08-24 | **汇总页脚金额**：`sum*AmountForQuery` 禁止套 `EXPORT_MAX_ROWS` 静默截断（导出上限仅约束导出） |
| v1.6 | 2026-08-19 | **科室统计**：筛选项改为 **默认登录科室**（`Practitioner.org_id`）；下拉仅启用 **护士站** 分类（注射室/采血室/疗区等）；已选科室汇总该科全部护士工作量 |
| v1.5 | 2026-08-12 | 耗材进价按操作单位分支（对齐 DD v1.2） |
| v1.4 | 2026-08-03 | **性能**：增 **IF-07** 护士卡片墙批量；前端改调批量 IF，禁止翻页全量明细分桶（口径不变） |
| v1.3 | 2026-08-03 | **性能**：汇总金额一次加载事实行；金额 enrich 仅锚点 IN；Support 按需 enrich；前端卡片一次拉明细按人分桶（口径不变） |
| v1.2 | 2026-07-06 | 增补明细/汇总 **ChargeItem 金额** enrich（**BR-RPT-NWL-021/022**）；应用层 **`NurseWorkloadAmountEnrichmentsupport** 已落地 |
| v1.1 | 2026-07-03 | 关闭 §3 Gap：领域读侧 **Procedure/DeviceDispense QueryService** + **`NurseWorkloadQuerysupport** 已落地 |
| v1.0 | 2026-07-03 | 首版：AS-RPT-002；双主 Tab（诊疗执行/耗材使用）× 双子 Tab；净 Procedure + 耗材发放；GenerateSource 单列；无科室筛选 |
