# 门诊处方查询 · 应用服务设计（AS）

> **文档职责**：说明门诊药房「发药管理 → 处方查询 / 门诊处方查看」页的**接口契约（IF）**、业务语义、涉及聚合与 QueryService、规则（BR）、编排意图；作为 **health-application** `outpatient/pharmacy/prescriptionquery` 包实现的编排权威。
>
> **页面边界**：筛选区（开方时间、收费状态、发药状态、是否含输液、处方类别、**医保类别**、费用性质、**开单科室**、**点评状态**、**是否合理**、**药品类型（药典 category_code）**、患者关键字等）→ **分页查询**药品申请**平铺明细行** → 前端按**处方号**聚合成**方级列表**（一张方一行；点评态 enrichment 消费 **AS-OP-PHA-007** IF-PRV-07）→ 点击进入**详情抽屉**展示用药明细 → 详情内**打印处方**（与门诊发药 slip 打印同场景）。**纯读**，无发药/退药/收费写操作。
>
> **交互清单来源**：产品裁决（2026-07-21）——列表不展示药品明细行；方头（诊断、单号、状态等）在列表；明细在详情；打印对齐药房。产品补裁（2026-07-23）：列表/筛增 **医保类别**（`med_type`）与 **电话号**；费用性质筛收敛为自费/省医保/市医保三档。产品补裁（2026-08-01）：列表/筛增 **开单科室**（药嘱 `org_id`）。产品补裁（2026-08-29）：筛增 **点评状态** / **是否合理**（Pre 读 **PrescriptionReview**）与 **药品类型**（药典 `medication_category_code` / 目录 `category_code`，方级）。
>
> **编码**：AS-OP-PHA-005 · **版本**：v1.8 · **日期**：2026-09-14

---

## 1. 场景概述

### 1.1 目标与边界

| 维度 | 说明 |
| --- | --- |
| **在内** | 页面初始化（筛选项与默认开方时间窗）；**分页查询**门诊已签发药品处方明细（正方 + 退方）；返回行内患者、电话、开单医生、**开单科室**、诊断、是否含输液、开方时间、收费状态、发药状态、处方类别、**医保类别**、费用性质及药品明细展示字段（含途径/频次/剂量/天数/**中药付数**/单价/皮试/是否计价等详情列）；可选处方号精确筛供详情整方重载；实现落点 **health-application / outpatient / pharmacy / prescriptionquery** |
| **在外** | 门诊发药作业（**AS-OP-PHA-001**）；门诊退药确认（**AS-OP-PHA-002**）；门诊发退药记录（**AS-OP-PHA-004**，仅已发/已退事实）；住院处方查询；导出 Excel（本期不做）；写路径（发药/退药/收费）；打印数据集装配（前端复用发药 slip + 打印运行时） |
| **涉及域** | **MedicationRequest**（主事实：已签发处方行、处方号、开方时间、处方类别、诊断、费用性质锚点、**开立科室 `org_id`**）；**MedicationDefinition**（药品展示、**`inject_flag`**、**`category_code` 药品类型**）；**MedicationDispense**（发药状态）；**ChargeItem**（收费状态）；**Contract**（费用性质分类）；**Encounter** / **EncounterDiagnosis** / **Patient**（门诊就诊、医保医疗类别 `med_type`、患者电话）；**Practitioner**（开单医生）；**Organization**（开单科室名称）；**ConditionDefinition**（诊断名称）；**PrescriptionReview**（点评态筛 Pre 预写有效点评方号；列表 enrichment 见 **AS-OP-PHA-007**） |
| **编排权威** | 本文 **§2、§4**；处方单元以 **`prescription_no`** 为准（**DM-MED-003**） |
| **权限** | **报表式只读**：**本期不按**管辖药房收窄（产品裁决「暂时不限制」）；租户过滤由框架基线承担 |

### 1.2 角色与关键动作

| 角色 | 关键动作 |
| --- | --- |
| **门诊药房人员 / 药房管理** | 进入页 → 设定筛选 → 查询 / 翻页 / 刷新 → 在**方级列表**查看处方号/诊断/状态等 → 进入详情查看药品明细 → 详情内打印处方 |
| **系统** | 下发筛选项与默认**本年**开方时间；按条件分页返回**平铺药品申请行**；装配方级与行级展示字段；前端聚合成方级列表并在详情重载整方 |

### 1.3 术语与口径约定

| 术语 | 口径 |
| --- | --- |
| **已签发处方行** | `med_medication_request`：`prescription_no` **非空**，**且** `status_enum ∈ {ACTIVE, COMPLETED}`；排除仍为草稿（含签发重分方仅预写了 CP 号、尚未真正签发）及签退回草稿行；含正方（`CP…`）与退方（**`TCP…`**，`T`+原号，**BR-MRQ-013**） |
| **开方时间** | 筛选与排序统一用 `req_opened_time`（开立时刻，业务主轴，**DM-MED-003**）；闭区间按日；空时间行**不入窗**（存量可回落 `req_authored_time` 兼容） |
| **门诊收敛** | 关联就诊 `class_enum = AMB`；排除住院发放场景 |
| **处方号分组** | 后端按**明细行条数**分页（`pageSize`/`totalCount` = 行数）；页脚**总处方量**另对 `prescription_no` 做 `COUNT DISTINCT`（一号一方）；本页返回平铺明细行；前端按完整 `prescription_no`（`CP…`/`TCP…`）聚合成**一张方一行**列表；详情按处方号精确筛重载整方明细 |
| **一张方状态一致** | 产品裁定：同一 `prescription_no` 下各行的**收费状态、发药状态**业务上一致；列表方头取该方任意一行（建议首行）的状态展示；筛选按行方状态下推即可 |
| **是否含输液（方级）** | **不读** `MedicationRequest.infusion_flag`；以药品目录 **`MedicationDefinition.inject_flag`** 为准：同一 `prescription_no` 下**任一行**对应目录 `inject_flag=true` → 方头「含输液=是」；筛选「含输液」时须返回该方**全部行**（含非注射药品行），不得只返回注射行 |
| **收费状态** | 锚定本药品申请的 `ChargeItem`（`service_table=med_medication_request` + `service_id`）之 `status_enum`（**DM-ADM-011** / `ChargeItemStatus`）；无费用行时展示空，且不匹配非空收费状态筛 |
| **发药状态** | 锚定本药品申请的 `MedicationDispense` 之 `status_enum`（**DM-MED-002** / `DispenseStatus`）；允许多段 FIFO 时取与业务一致的代表态（产品裁定方内一致，取任一有效发放行）；无发放行时展示空，且不匹配非空发药状态筛 |
| **处方类别** | `rx_type_code`，术语 **355**（`rx_type_code` / 处方类别代码）；与医保类别无关（如门诊西药 / 门诊中药饮片） |
| **医保类别** | 药嘱 `encounter_diagnosis_ids` **首位** → `adm_encounter_diagnosis.med_type` → **`YbMedType`**（普通门诊 / 门诊慢病 / 急诊等）；同方诊断同族，方头取首位即可 |
| **费用性质** | `MedicationRequest.contract_id` → **Contract.**`category_enum`（`ContractCategory` / 库内常见 `ChargeTypeCategory` 数字码）；**筛选项仅三档**：自费 / 省医保 / 市医保；筛选须双码并查；列表展示仍按存库分类码解析（存量公费/职工有值则如实显示） |
| **电话号** | 就诊上下文患者联系电话（`Patient.phone` / 门诊上下文 `phone`） |
| **开单科室** | 药嘱 **`org_id`**（`adm_organization.id`）；列表展示科室名；筛选等值匹配；**禁止**以 `perform_location` 冒充（**BR-MRQ-029**） |
| **点评状态** | **PENDING（待点评）**=已发药（发放 COMPLETED）且无有效点评；**REVIEWED**=有效点评；未发药等不可评不计入待点评、列表展示「—」。筛选 PENDING 时 Pre 排除有效点评方并叠加 COMPLETED 发放收窄（B.6.4） |
| **是否合理** | 有效点评 `reasonable_flag`；单独筛或与已点评组合；与待点评叠用 → 空结果 |
| **药品类型** | 药典术语 **`medication_category_code`** ↔ 目录 **`MedicationDefinition.category_code`**；**方级**：任一行命中则整方明细入选（同含输液 YES）；**≠** 处方类别 `rx_type_code`、**≠** 费用性质 `contractCategoryCode` |
| **诊断** | 取该行绑定就诊诊断对应诊断定义名称并集；产品裁定分方后一方诊断集合有交集 |
| **正方与退方** | **均纳入**查询范围；列表以处方号前缀区分；本期**不**另设「仅正方/仅退方」筛选项（需要时可后续加） |

### 1.4 本期范围与非目标

| 类别 | 内容 |
| --- | --- |
| **本期** | IF-OPQ-01～02：初始化、分页查询（含可选 `prescriptionNo` 精确筛与详情用药字段）；菜单挂报表/发药管理；前端方级列表 + 详情抽屉 + 药房同款处方打印 |
| **非目标** | 导出；住院；按管辖药房强制收窄；服务端按方聚合小计行；改写发药/收费状态；医生站分方写路径；`inject_flag` 目录维护补数；皮试状态/结果观测读（本期仅 `skinTestFlag`） |

### 1.5 领域清单（聚合 + 职责 + DM 路径）

| 聚合 | 在本场景中的职责 | DM 路径 |
| --- | --- | --- |
| **MedicationRequest** | 已签发处方明细主表、处方号、开方时间、处方类别、诊断锚点、费用性质锚点、开单人、开立科室 | `DM-MED-003-MedicationRequest-药品申请.md` |
| **MedicationDefinition** | 药品名称/规格/编码；**`inject_flag`** 判定含输液；**`category_code`** 药品类型筛 | `DM-MED-001-MedicationDefinition-药品定义.md` |
| **MedicationDispense** | 发药状态读 | `DM-MED-002-MedicationDispense-药品发放.md` |
| **ChargeItem** | 收费状态读 | `DM-ADM-011-ChargeItem-收费明细.md` |
| **Contract** | 费用性质分类 | `DM-FIN-001-Contract-费用性质.md` |
| **Encounter** | 门诊 AMB 收窄、门诊号；就诊诊断 `med_type`（医保类别） | `DM-ADM-002-Encounter-就诊.md` |
| **Patient** | 患者姓名、电话等摘要 | `DM-ADM-001-Patient-患者.md` |
| **Practitioner** | 开单医生姓名 | `DM-ADM-005-Practitioner-医务人员.md` |
| **Organization** | 开单科室名称；init 科室下拉 | `DM-ADM-004-Organization-机构科室.md` |
| **ConditionDefinition** | 诊断名称 | `DM-CLN-002-ConditionDefinition-诊断定义.md` |
| **PrescriptionReview** | 点评态/是否合理筛 Pre（有效点评方号）；列表 enrichment 见 AS-OP-PHA-007 | `DM-CUS-015-PrescriptionReview-处方点评.md` |

### 1.6 业务规则（BR-*）

| 规则 ID | 描述 | 绑定 IF |
| --- | --- | --- |
| **BR-OPQ-001** | 仅纳入**已签发**药品申请：`prescription_no` 非空 **且** `status_enum ∈ {ACTIVE, COMPLETED}`；正方 `CP…` 与退方 `TCP…` 均含；排除医嘱草稿（含仅重分方写了 CP 的未签发行）与签退后草稿 | IF-OPQ-02 |
| **BR-OPQ-002** | 场景仅门诊：就诊 **AMB**；仅 **MedicationRequest** 药品行，排除耗材 | IF-OPQ-02 |
| **BR-OPQ-003** | 时间窗：`req_opened_time` 闭区间（空回落 `req_authored_time`）；排序 `req_opened_time` **倒序**，同方内按行序/主键稳定排序；空开方时间不入窗 | IF-OPQ-02 |
| **BR-OPQ-004** | 默认时间窗为**本年**（init 下发当年首末日）；查询时开方起止**必填**且 `from ≤ to` | IF-OPQ-01、02 |
| **BR-OPQ-005** | 收费状态筛：可选；命中行关联 `ChargeItem.status_enum`；方内状态一致前提下按行过滤 | IF-OPQ-02 |
| **BR-OPQ-006** | 发药状态筛：可选；命中行关联 `MedicationDispense.status_enum`；须能查出已收未发、未收未发、已收已发等组合（与收费筛独立 AND） | IF-OPQ-02 |
| **BR-OPQ-007** | 含输液筛：可选三态（全部 / 是 / 否）；基于目录 **`inject_flag`** 的**方级**判定；「是」返回含注射目录药品之方的**全部明细行** | IF-OPQ-02 |
| **BR-OPQ-008** | 处方类别筛：可选；`rx_type_code` 精确匹配（术语 355） | IF-OPQ-02 |
| **BR-OPQ-009** | 费用性质筛：可选；经 `contract_id` → `Contract.category_enum`；init 仅下发自费/省医保/市医保三档 code；**库内数字码（ChargeTypeCategory `1`/`3`/`4`）与契约枚举名（`SELF_CHARGE`/`PROVINCIAL_INSURANCE`/`MUNICIPAL_INSURANCE`）双码并查** | IF-OPQ-01、02 |
| **BR-OPQ-010** | 患者关键字可选；模糊匹配姓名/院内编码/**身份证**/拼音/五笔（与门诊队列关键字同族，**B.2.1** 先精确后模糊）；重名时可用身份证定位；与其它条件 AND | IF-OPQ-02 |
| **BR-OPQ-015** | 医保类别筛：可选；经绑定诊断 `med_type`（`YbMedType` code）预解析药品申请 id（B.6.4，禁止跨根 EXISTS） | IF-OPQ-02 |
| **BR-OPQ-016** | 开单科室筛：可选；药嘱 **`org_id`** 精确匹配；init 下发门诊启用科室；列表展示科室名（批量 `OrganizationQueryService#mapNamesByIds`） | IF-OPQ-01、02 |
| **BR-OPQ-017** | 点评状态筛：可选 PENDING/REVIEWED；PENDING=**已发药待点评**（排除有效点评方 **且** 叠加发放 COMPLETED，未发药等不可评不入选）；REVIEWED=方号 IN 有效点评；Pre 调 `listActivePrescriptionNos`；**禁止** MR QS 跨根读点评表 | IF-OPQ-01、02 |
| **BR-OPQ-018** | 是否合理筛：可选；与已点评组合或单独传时按有效点评 `reasonable_flag` 收窄方号；与待点评叠用 → 空结果 | IF-OPQ-01、02 |
| **BR-OPQ-019** | 药品类型筛：可选；药典 `medication_category_code` → `MedicationDefinitionQueryService#listIdsByCategoryCodesIn` → 方级展开整方明细（同含输液 YES）；≠ `rx_type_code` / 费用性质 | IF-OPQ-01、02 |
| **BR-OPQ-011** | **不**按药房 `location_id` / 管辖库房过滤（本期）；门诊 AMB / 患者关键字由应用层预写 `resolvedEncounterIds`，领域**不** EXISTS 就诊表 | IF-OPQ-01、02 |
| **BR-OPQ-012** | 列表接口按**明细行条数**分页（`pageSize`/`totalCount` = 行数），本页仍返回平铺明细行，**禁止**服务端按处方聚合小计行；页脚**总处方量**为 `COUNT DISTINCT prescription_no`（与分页条数分口径）；前端聚合成方级列表，详情用 `prescriptionNo` 精确筛重载整方 | IF-OPQ-02 |
| **BR-OPQ-013** | 方头「含输液」展示与筛选同源：目录 `inject_flag`，不做医嘱 `infusion_flag` 回落 | IF-OPQ-02 |
| **BR-OPQ-014** | 退方处方号 API 返回存库值 `TCP…`；前端可将退方列展示为原 `CP…`（Tooltip 存库号），与发退药记录一致属展示层可选 | IF-OPQ-02 |

---

## 2. 页面与接口总览（契约概览）

| IF 编号 | 用户动作 | 接口语义（业务） | 读/写 | 关键规则（BR） | 关键状态变化 | 涉及领域 | 查询服务 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| IF-OPQ-01 | 进入处方查询页 | 下发筛选项（收费/发药状态、处方类别 355、**医保类别**、费用性质三档、含输液三态、**开单科室**、**点评状态**、**是否合理**、**药品类型**）与默认**本年**开方日期 | 读 | BR-OPQ-004、009、011、016～019 | — | 术语 / Contract / YbMedType / Organization / PrescriptionReview 枚举 | `ContractQueryService#listAllContracts`；术语加载（含 `medication_category_code`）；枚举选项装配；`OrganizationQueryService#listOrganizationsForOutpatientRegistration` |
| IF-OPQ-02 | 设定筛选后查询/翻页/刷新；详情按处方号重载 | 分页查询门诊已签发处方**平铺药品行**（可选处方号精确筛）；行含医保类别、电话、**身份证（明文，前端脱敏）**、**开单科室** | 读 | BR-OPQ-001～019 | — | MedicationRequest 及关联读；点评筛 Pre | `MedicationRequestQueryService#pageOutpatientSignedPrescriptionDrugLines`；Loader 预写 charge/dispense/inject/contract/medType/encounter/category/review `resolved*`；页内批量装配；点评 enrichment 由前端消费 IF-PRV-07 |

---

## 3. 缺口跟踪（Gap）

| Gap ID | 缺口类型 | 文件 + 章节 | 缺什么 / 不一致点 | 关闭条件 | 影响 IF | 优先级 | 状态 |
|--------|----------|-------------|-------------------|----------|---------|--------|------|
| GAP-OPQ-001 | QueryService 能力 | `DM-MED-003` §6.2.21 | 已落地 `pageOutpatientSignedPrescriptionDrugLines` + 他聚合预解析方法（Charge/Dispense/Definition/Contract） | DM §6.2.21 与领域代码一致 | IF-OPQ-02 | P0 | **已关闭** |

**无待跟踪缺口。**

---

## 4. 接口编排说明书（业务步骤）

### IF-OPQ-01 页面初始化

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 用户进入门诊处方查询页（发药管理菜单） |
| **主路径步骤** | ① 计算默认开方日起止为**本年**首末日。② 加载收费状态选项（`ChargeItemStatus` 业务常用码，至少含待收费/已结算/退费中/已退费等，SDD 定全集）。③ 加载发药状态选项（`DispenseStatus` 业务常用码，至少含待配/配药中/已配/已发/已退等，须覆盖未发与已发）。④ 加载术语 **355** `rx_type_code` 处方类别选项。⑤ 加载**医保类别**选项（门诊向 `YbMedType`）。⑥ 加载费用性质分类选项（**仅**自费 / 省医保 / 市医保三档）。⑦ 加载含输液固定三态：全部 / 是 / 否。⑧ 加载**开单科室**选项（门诊启用科室）。⑨ 加载**点评状态**（PENDING/REVIEWED）与**是否合理**选项。⑩ 加载**药品类型**选项（术语 `medication_category_code`）。⑪ 返回上述下拉与默认日期 |
| **关键分支** | 术语或合同为空时仍返回空下拉，页面可打开 |
| **失败分支** | 元数据加载失败 → EX-OPQ-101 |
| **查询侧说明** | 只读元数据与术语，不写库 |

### IF-OPQ-02 分页查询门诊处方明细

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 用户已提供合法开方时间窗；可组合收费状态、发药状态、含输液、处方类别、医保类别、费用性质、开单科室、点评状态、是否合理、药品类型、患者关键字 |
| **主路径步骤** | ① 校验时间窗合法。② 按需预解析患者关键字（姓名/身份证/门诊号等同族）/ AMB 就诊范围为 `resolvedEncounterIds`。③ 按需预解析收费状态→药品申请 id、发药状态→药品申请 id、注射目录 id、费用性质合同 id、**医保类别→药品申请 id**、**药品类型→目录 id（方级）**、**点评状态/是否合理→有效点评方号 INCLUDE/EXCLUDE**。④ 调用 `pageOutpatientSignedPrescriptionDrugLines`（含可选 `orderingDepartmentId`→`org_id`）。⑤ 批量装药品定义、发放状态、收费状态、合同分类、就诊/患者摘要（含电话、**身份证明文**）、开单医生、**开单科室名**、诊断名称、**医保类别**。⑥ 计算方级含输液标志。⑦ 映射平铺行并返回分页（**无小计行**；身份证列表脱敏由前端） |
| **关键分支** | 各筛选项为空表示不限制；含输液「是/否」按方级收窄后展开方内全部行 |
| **失败分支** | 时间窗非法 → EX-OPQ-102；查询失败 → EX-OPQ-103 |
| **查询侧说明** | 主事实在 `med_medication_request`；跨聚合批量装配，禁止 N+1 |

---

## 5. 业务异常与用户提示

| 语义代码 | 场景 | 用户提示方向 | 可重试 |
| --- | --- | --- | --- |
| EX-OPQ-101 | 初始化失败 | 页面初始化失败，请稍后重试 | 是 |
| EX-OPQ-102 | 时间窗非法 | 请选择有效的开方时间范围 | 否 |
| EX-OPQ-103 | 查询失败 | 查询失败，请稍后重试 | 是 |

---

## 6. 事件语义与消费方

**本期无领域写操作，不发布、不消费领域事件。**

---

## 7. 非功能性需求

| 类别 | 要求 |
| --- | --- |
| **性能** | 分页查询 ≤ 2000ms（常规月窗）；跨表 EXISTS 须有索引支撑（`req_opened_time`、`prescription_no`、charge/service、dispense/med_req） |
| **一致性** | 纯读；状态以 ChargeItem / MedicationDispense 当前值为准 |
| **审计** | 查询记操作日志（框架审计，可选） |
| **租户** | 数据限定当前租户；本期不按管辖药房收窄 |

---

## 8. 测试要点（业务视角）

| 类别 | 要点 |
| --- | --- |
| **正向** | 签发后未收费未发药可见；收费后未发可见；发药完成后可见；退方 `TCP…` 可见 |
| **筛选** | 开方时间；收费与发药状态独立组合；含输液是/否（方内含非注射行仍整方返回）；处方类别；医保类别；费用性质（三档）；开单科室；点评状态；是否合理；药品类型（药典 category_code，方级）；患者关键字（姓名/身份证/门诊号等） |
| **分组** | 平铺无小计；前端方级列表一张方一行；详情整方明细字段齐全 |
| **打印** | 详情打印与药房发药处方场景一致（西药/中药分流）；无发放 slip 时提示不可打 |
| **边界** | 不含医嘱草稿（含仅有 CP 仍为 DRAFT）；不含住院；不含按药房过滤；目录无 `inject_flag` 的药品视为非输液 |

---

## 9. 关联文档与变更历史

| 类型 | 路径 |
| --- | --- |
| 药品申请 DM | `docs/领域/DomainModel/DM-MED-003-MedicationRequest-药品申请.md |
| 药品定义 DM | `docs/领域/DomainModel/DM-MED-001-MedicationDefinition-药品定义.md |
| 发放 DM | `docs/领域/DomainModel/DM-MED-002-MedicationDispense-药品发放.md |
| 收费明细 DM | `docs/领域/DomainModel/DM-ADM-011-ChargeItem-收费明细.md |
| 费用性质 DM | `docs/领域/DomainModel/DM-FIN-001-Contract-费用性质.md |
| 关联 AS | `AS-OP-PHA-001-门诊发药.md`、`AS-OP-PHA-002-门诊退药.md`、`AS-OP-PHA-004-门诊发退药记录.md`、`AS-OP-PHA-007-门诊处方点评.md`（IF-PRV-07 enrichment；点评态筛 Pre） |

| 版本 | 日期 | 摘要 |
| --- | --- | --- |
| v1.8 | 2026-09-14 | IF-OPQ-02 明细回传中药付数 `chineseHerbsDoseQuantity`；详情方头展示；**BR-OPQ-009** 费用性质筛双码并查（`PROVINCIAL_INSURANCE`↔`3` / `MUNICIPAL_INSURANCE`↔`4`） |
| v1.7 | 2026-09-09 | **BR-OPQ-004**：init 默认开方窗改为**本年**首末日 |
| v1.6 | 2026-08-29 | 筛增**点评状态**/**是否合理**（BR-OPQ-017/018）与**药品类型**药典 `category_code`（BR-OPQ-019，方级）；init 下发对应选项；跨 AS 协作 **AS-OP-PHA-007** |
| v1.5 | 2026-08-25 | **BR-OPQ-010** 明示患者关键字含**身份证**（重名定位）；列表回传 `idCard` 明文，**前端脱敏**展示；**BR-OPQ-012** 分页按明细行条数，页脚总处方量为 `COUNT DISTINCT prescription_no`（与条数分口径） |
| v1.4 | 2026-08-01 | 列表/筛增 **开单科室**（药嘱 `org_id` / Organization）；**BR-OPQ-016** |
| v1.3 | 2026-07-23 | 列表/筛增 **医保类别**（`med_type`/`YbMedType`）与 **电话号**；费用性质筛收敛自费/省医保/市医保三档；**BR-OPQ-015** |
| v1.2 | 2026-07-21 | **交互改版**：方级列表 + 详情抽屉 + 药房同款打印；IF-OPQ-02 增 `prescriptionNo` 精确筛与详情用药字段；修订 **BR-OPQ-012** |
| v1.1 | 2026-07-15 | **GAP-OPQ-001 关闭**：领域 `pageOutpatientSignedPrescriptionDrugLines` 落地（B.6.4 预解析，无跨根 EXISTS） |
| v1.0 | 2026-07-15 | 初版：门诊处方查询纯读；开方时间窗；正方+退方；收费/发药分筛；方级目录 inject_flag；GAP-OPQ-001 |
