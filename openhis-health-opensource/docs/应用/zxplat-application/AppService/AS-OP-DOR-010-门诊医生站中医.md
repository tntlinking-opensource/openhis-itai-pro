# 门诊医生站 · 中医 · 应用服务设计（AS）

> **文档职责**：说明门诊医生站「中医」Tab 的**接口契约（IF）**、业务语义、涉及聚合与 QueryService、规则（BR）、编排意图；作为上游契约供详细设计与实现对齐。
>
> **本文必须写**：IF 编号；用户动作与读写属性；关键校验与状态变化；Gap；异常口径；事件语义；非功能约束；业务向测试要点。
>
> **本文禁止写**：具体类名/应用方法名；Request/Response 字段明细；包路径；Mapper；伪代码；写库顺序；QueryService 字段组装规则。
>
> **页面边界**：医生站主界面「中医」标签——**中医病证组**维护区（病名+证候成组录入/删除）、**中药处方**多 Tab 工作区（左侧协定/常用/历史组套树、处方 Tab 切换/新增、方头表单、明细表与编辑卡片、工具栏暂存/签发/撤回/删除）、组套/医嘱模板抽屉套用。**就诊队列、接诊、西医诊断 Tab、西医医嘱 Tab、电子处方打印、报告查询**等归入其他 AS（见 §1.4）。
>
> **覆盖声明**：完整契约以 **§2 IF-DOR-30～39** 为准；中医诊断「病+证成组」与中药处方「按方标识出号、不做门诊西药式全就诊分方」为本期核心口径；组套/模板列表与预览结构由 **AS-BAS-MST-017** 提供，套用后仍调本文暂存/签发 IF。**HTTP 接入、请求体形态、应用层事务与实现侧依赖**以对应 AppService-SDD 为权威，本文不展开类名与编程序列。
>
> **版本**：v1.19

---

## 1. 场景概述

### 1.1 目标与边界


| 维度 | 说明 |
| --- | --- |
| **在内** | 加载当前就诊**中医病证组**列表（IF-DOR-30）；分页检索**中医病名/证候**目录（IF-DOR-31）；**全量批次**保存/更新中医病证组（IF-DOR-32）；按组删除中医病证（IF-DOR-33）；加载当前就诊**中药处方**列表（按方聚合，IF-DOR-34）；分页检索**中药饮片**目录（IF-DOR-35）；**暂存**一张或多张中药处方（方头+明细行，IF-DOR-36）；**签发**中药处方（按前端方标识映射 `prescription_no`、含代煎费，IF-DOR-37）；**签退/删除**中药处方或方内行（IF-DOR-38）；查询患者**历史中药处方**（IF-DOR-39）。组套/医嘱模板：**消费** **AS-BAS-MST-017 · IF-BAS-OTL-07/08**（`template_kind_enum=TCM`），套用后调用 IF-DOR-36 |
| **在外** | 就诊生命周期（**AS-OP-DOR-001**）；西医诊断 Tab（**AS-OP-DOR-002**）；西医/中成药/诊疗/耗材开立与门诊全就诊处方分方（**AS-OP-DOR-003**，含 **IF-DOR-15/BR-ORD-023**）；组套模板维护后台（**AS-BAS-MST-017** 维护端 IF-BAS-OTL-01～06）；电子处方票据、退费、药房发药、报告查询 |
| **涉及域** | 诊断事实（Condition）；诊断定义（ConditionDefinition）；就诊与就诊诊断关联（Encounter / EncounterDiagnosis）；药品申请与发放（MedicationRequest / MedicationDispense）；收费明细与定价（ChargeItem / ChargeItemDefinition）；诊疗项目定义（ActivityDefinition，代煎费）；**开立目录 QueryService（ProductDefinition）**；组套模板（OrderTemplate，只读消费）；接诊科室→候选发药房（ServiceLocationTemplate）；就诊账户（Account） |
| **编排权威** | 以本文 **§2、§4** 为准 |


### 1.2 角色与关键动作


| 角色 | 关键动作 |
| --- | --- |
| **门诊医生** | 进入中医 Tab → 维护**一病一证**诊断组 → 新增/切换中药处方 Tab → 从左侧组套树套用或手工选药 → 维护方头（剂数、用法、天数、煎煮法、代煎等）与明细 → 暂存 → 签发；必要时撤回、删除处方或删除病证组 |
| **系统** | 病证组落库时写入 `tcm_syndrome_group` 与 `tcm_flag`；中药行强制 `tcm_flag=true` 且目录仅中草药；签发时按 **`prescriptionGroupKey`** 分组取/复用 `prescription_no`，**不**调用西医 Tab 全就诊 greedy 分方；代煎=是时按付数生成代煎 **`ChargeItem`**；删除病证组前校验医嘱引用 |


### 1.3 术语与口径约定


| 术语 | 口径 |
| --- | --- |
| **中医病证组（TcmDiagnosisGroup）** | 临床最小单元 = **1 个中医病名 + 1 个中医证候**；持久化对应 **2 条** `Condition` + **2 条** `EncounterDiagnosis`，共享同一 **`tcm_syndrome_group`**（组标识落库）；组级维护 **主诊标记、排序号、医疗类型、诊断描述** |
| **一病一证** | 每组**必须**同时包含病名（目录 source=`tcm_dx`）与证候（source=`tcm_syndrome`）；缺任一即拒绝保存 |
| **prescriptionGroupKey** | 前端生成的**一张中药方**交互标识（如 UUID）；**不落库**；暂存/签发请求体携带；后端按 key 将多味药行归并为同一逻辑方，并在签发相位映射为同一 **`prescription_no`**；已签发且同 key 对应行已有处方号则**复用** |
| **中药处方（方头+行）** | 方头：剂数（付数）、用法、**用药频次**、天数、煎煮法、备注、代煎标识、绑定的**中医病证组**等；方内每味药对应一条 **`tcm_flag=true`** 的药品申请草稿/生效行；**请求数量 = 单剂用量 × 付数**（后端重算为准） |
| **目录角色 catalogRole** | IF-DOR-31：`DISEASE`=中医病名（source `tcm_dx`）；`SYNDROME`=中医证候（source `tcm_syndrome`） |
| **中药饮片目录** | IF-DOR-35：目录 **`categoryCode`** 限定为**中草药**（与 `DM-MED-001` 饮片分类一致）；落库行 **`tcm_flag=true`** |
| **中西医隔离** | **AS-OP-DOR-003 · IF-DOR-15** 签发批次**不得**处理 `tcm_flag=true` 行；本文 IF-DOR-37 **不得**处理 `tcm_flag≠true` 行；**禁止**对中医 Tab 草稿调用 **BR-ORD-023** 全就诊分方 |
| **代煎费** | 租户配置的**代煎诊疗项目**（ActivityDefinition）；签发且方头代煎=是时，按**付数**生成/刷新同方代煎 companion **`ServiceRequest`（`based_on`→代表药行，`prescription_no` 空）+ `ChargeItem`（PLANNED）**；代煎=否时按同方 based_on 匹配移除代煎费项 |
| **组套/医嘱模板** | 与西医 Tab 相同交互：**IF-BAS-OTL-07** 列表、**IF-BAS-OTL-08** 预览结构；中医 Tab 固定 **`template_kind_enum=TCM`**；套用后映射为新 **`prescriptionGroupKey`** 并调 **IF-DOR-36** |
| **QueryService 能力** | 写前门控 **OutpatientEncounterContextQueryModel**；列表读侧以就诊维度药品申请 QueryService + 费用只读聚合为准 |
| **接入与工程口径** | 动态应用服务路由 `POST /api/app/doctorStationTcm/{methodName}`（实现类 `DoctorStationTcmAppService`）；JSON 请求体；写路径跨聚合顺序与**应用层 `@Transactional`** 以 AS-SDD 为准 |


### 1.4 本期范围与非目标


| 类别 | 内容 |
| --- | --- |
| **本期（契约）** | IF-DOR-30～39：中医病证组成组 CRUD、中药处方按方暂存/签发/签退/删除、饮片目录检索、历史处方查询；**prescriptionGroupKey 不落库**；**含代煎费** |
| **本期（UI 接通）** | `tcm-advice.vue` 已具备多处方 Tab、组套树、编辑卡片、工具栏占位；**持久化联调**以迭代接通为准 |
| **非目标（同页其他 Tab）** | 「就诊队列」「门诊病历」「诊断录入（西医）」「开立医嘱（西医）」「电子处方」「报告查询」 |
| **非目标（中成药）** | **中成药**仍在 **AS-OP-DOR-003** 西医医嘱 Tab 开立；**不在**中医 Tab 选品列表 |
| **非目标（组套维护）** | 组套模板 CRUD 维护页——**AS-BAS-MST-017** 维护端；本 Tab 仅消费 IF-BAS-OTL-07/08 |
| **非目标（老代码舍弃）** | 中医诊断 **`save`/`update` 双接口**、查询返回 `{illness,symptom}` 双数组、`updateId` 拼接更新——本期统一为**病证组**读写模型 |
| **非目标（门诊西药分方）** | 金额上限 greedy 装箱、西成药 ≤5 种等同 **BR-ORD-023** 规则——**不**适用于中医 Tab（**BR-MRQ-017** 手动方剂口径） |
| **非目标（术语下拉 duplicate）** | 中医 Tab 术语下拉由 **`initDoctorStationTcm`** 独立下发（中药用法 + 用药频次，频次与 **AS-OP-DOR-003 · IF-DOR-18** 同源）；不在 IF-DOR-30～39 表重复编号 |


### 1.5 领域清单（聚合 + 职责 + DM 路径）


| 聚合（域） | 在本场景中的职责（摘要） | DM 路径 |
| --- | --- | --- |
| **Condition** | 中医诊断事实；`tcm_flag`、验证状态、目录关联 | `DM-CLN-003-Condition-诊断记录.md` |
| **ConditionDefinition** | 中医病名/证候目录分页检索 | `DM-CLN-002-ConditionDefinition-诊断定义.md` |
| **Encounter（含 EncounterDiagnosis）** | 就诊与病/证关联；`tcm_syndrome_group`、`tcm_flag`、排序与主诊 | `DM-ADM-002-Encounter-就诊.md` |
| **MedicationRequest / MedicationDispense** | 中药饮片医嘱生命周期；付数、代煎、`content_json` 方头扩展；签发/签退 | `DM-MED-003-MedicationRequest-药品申请.md` / `DM-MED-002-MedicationDispense-药品发放.md` |
| **ChargeItem / ChargeItemDefinition** | 药行费用草稿/待收费；代煎诊疗费 | `DM-ADM-011-ChargeItem-收费明细.md` / `DM-ADM-010-ChargeItemDefinition-收费项定价.md` |
| **ActivityDefinition** | 代煎诊疗项目定义（租户配置项） | `DM-WKF-001-ActivityDefinition-诊疗项目定义.md` |
| **MedicationDefinition / ProductDefinition** | 中药饮片目录检索与定价 | `DM-MED-001-MedicationDefinition-药品定义.md` |
| **OrderTemplate** | 中医组套/医嘱模板只读消费 | `DM-CUS-012-OrderTemplate-组套模板.md` |
| **ServiceLocationTemplate** | 候选发药房、库存预减（与 IF-DOR-35 同口径） | `DM-CUS-006-ServiceLocationTemplate-取药科室.md` |
| **Account** | 饮片费用草稿挂账与西医同口径：双账户默认**医保户**、单户个人现金；**代煎**账单挂**个人现金自费户**（**BR-TCM-RX-009**，只改账单 `account_id`，不动 Account 主数据） | `DM-ADM-009-Account-就诊账户.md` |
| **Practitioner / Organization** | 开方医生、开立科室 | `DM-ADM-005-Practitioner-医务人员.md` / `DM-ADM-004-Organization-机构科室.md` |


### 1.6 业务规则（BR-*）


| 规则 ID | 描述 | 绑定 IF |
| --- | --- | --- |
| **BR-TCM-DIA-001** | 每个中医病证组**必须**含 **1 病名 + 1 证候**；缺任一侧拒绝保存 | IF-DOR-32 |
| **BR-TCM-DIA-002** | 同组病/证共享同一 **`tcm_syndrome_group`**；**主诊标记、排序号**在**组级**维护并同步到组内两行 | IF-DOR-32 |
| **BR-TCM-DIA-003** | 全就诊**主诊病证组**至多 **1** 个 | IF-DOR-32 |
| **BR-TCM-DIA-004** | 删除病证组前须校验组内**任一** `conditionId` **不存在已开立中药医嘱**引用；否则拒绝 | IF-DOR-33 |
| **BR-TCM-DIA-005** | 中医目录分页仅 **ACTIVE** 定义；`catalogRole` 决定 source 集合（`tcm_dx`/`tcm_syndrome`） | IF-DOR-31 |
| **BR-TCM-DIA-006** | 病证组保存为**全量批次**：不在批次中的历史中医组由前端显式 IF-DOR-33 删除或后端全量差异移除（与 **AS-OP-DOR-002** 批次语义一致） | IF-DOR-32 |
| **BR-TCM-ISO-001** | **AS-OP-DOR-003 · IF-DOR-15** 签发**不得**包含 `tcm_flag=true` 的药品行 | （跨 AS，见 **AS-OP-DOR-003**） |
| **BR-TCM-ISO-002** | 本文 IF-DOR-37/38 **仅**处理 `tcm_flag=true` 且来源为医生开立的药品行 | IF-DOR-37 / IF-DOR-38 |
| **BR-TCM-ISO-003** | 中医 Tab 签发**禁止**调用 **BR-ORD-023** 全就诊 `splitPrescription`；处方号按 **BR-TCM-RX-002** 分配。对称约束：IF-DOR-15 西药分方参与集**禁止**纳入 `tcmFlag=true`，不得改写已签发中药 `prescription_no` | IF-DOR-37 / IF-DOR-15 |
| **BR-TCM-RX-001** | **`prescriptionGroupKey` 不落库**；前后端以请求体携带；持久化以 **`prescription_no`** 关联同方各行 | IF-DOR-36 / IF-DOR-37 |
| **BR-TCM-RX-002** | 签发时每个 **`prescriptionGroupKey` → 1 个 `prescription_no`**（按日序列，前缀与中草药处方序列一致）；同 key 下已有未撤回的处方号则**复用** | IF-DOR-37 |
| **BR-TCM-RX-003** | 中药目录检索 **`categoryCode=中草药`**；落库 **`tcm_flag=true`**；**禁止**选入非饮片 | IF-DOR-35 / IF-DOR-36 |
| **BR-TCM-RX-004** | 每张中药方**须绑定 1 个中医病证组**（`tcm_syndrome_group`）；未绑定拒绝暂存/签发 | IF-DOR-36 / IF-DOR-37 |
| **BR-TCM-RX-005** | **请求数量 = 单剂用量 × 付数**；库存校验与计费以后端重算为准 | IF-DOR-36 / IF-DOR-37 |
| **BR-TCM-RX-006** | 暂存：中药行 **Request + 按请求锚点 ChargeItem 草稿**；**不**建发放（与 **BR-ORD-020** 一致） | IF-DOR-36 |
| **BR-TCM-RX-007** | 签发：逐行 **sign** 后须完整执行 **void 请求锚点暂存费 → FIFO 替换发放 → 按发放分段重建 `ChargeItem` 草稿（每段 `unit_price` 按该段 **`lotNumber`** 匹配 **`LOT_NUMBER_PRICE`**，同 **AS-OP-DOR-003 · BR-ORD-016**；**禁止**跨批号 priority）→ `dispense_*` 锚点 `DRAFT→PLANNED`**（与 **IF-DOR-15** 药线同构，共用 `OutpatientMedicationSignChargeOrchestrator`；**禁止**仅 `plan` 而无分段草稿）；**不分方**；签发前 **pending 调价互斥**同 **BR-ORD-037** / **BR-PRC-LOCK-007** | IF-DOR-37 |
| **BR-TCM-RX-008** | 签发时推导 **`rx_type_code`** 为**门诊中药饮片**类别（可随就诊医疗类型抬升急诊/儿科饮片类别）；**不做** greedy 金额/品种装箱 | IF-DOR-37 |
| **BR-TCM-RX-009** | **代煎=是**：签发成功后按**付数**为同方生成/刷新代煎 companion **`ServiceRequest` + `ChargeItem`（PLANNED）**；companion 以 **`based_on`→方内代表 `MedicationRequest`** 关联，**禁止**把中药方号（`CP`/`TCP`）写入 SR/`ChargeItem.prescription_no`（该列在 SR 仅申请单 **`AR`**；方号真源只在药嘱）。**门诊**代煎账单挂就诊**个人现金自费户**（SR `contract_id` 可写自费冗余），**禁止**开立/改写 `Account`；转医保跳过该账单行（**AS-OP-FIN-002 · BR-OPC-022**）；**住院代煎**费用性质可跟就诊医保，本期不强制自费。**代煎=否**/签退：按同方 based_on 匹配 void 代煎费项。读侧（列表/发药）经 based_on→药嘱反查方号；负向退代煎绑退药嘱时回原正向 **CP** 归方。**代煎费收银退款终态（`REFUNDED`）**：须同步医生站——清方内饮片 **`suffering_flag`**，并收口因申请退费而停嘱的代煎 companion（**AS-OP-FIN-003 · BR-REF-025**）；「有效代煎 companion」仅计草稿/已发送态，停嘱/已完成/已取消不挡再补充。**单独取消代煎**：同方代煎收费行经 based_on 反查方号后，若态为待结算/已结算/退费中则拒绝（须走退费；**不**依赖 live companion SR） | IF-DOR-37 / IF-REF-05 |
| **BR-TCM-RX-010** | 删除/签退：方内任一行费用**已收费**则拒绝整方操作；未收费则请求回草稿、释放发放预占、费用回退草稿 | IF-DOR-38 |
| **BR-TCM-RX-011** | 中药草稿创建/修改须走**中药专用**领域写路径（**BR-MRQ-011**），不得与西药 `create`/`change` 混用 | IF-DOR-36 |
| **BR-TCM-RX-012** | **IF-DOR-34** 行须下发 **`doseUnitName`/`unitName`**（对齐 **BR-ORD-022**）及 **`retailUnitPrice`**（元/剂量单位）：已挂费优先 **`ChargeItem.unit_price`**，否则目录 **DOSE 槽**单价（与 IF-DOR-11 开立目录口径一致） | IF-DOR-34 |
| **BR-TCM-RX-013** | **IF-DOR-34** 读侧**排除**退费逆向复制饮片行（`refund_medicine_id` 非空）；与 **AS-OP-DOR-003 · BR-ORD-030** 同口径 | IF-DOR-34 |
| **BR-TCM-RX-014** | **IF-DOR-35** 选品下拉**不做同名合并**：与西药 **IF-DOR-11** 同构，同名多厂家/多目录各行展示；`availableQuantity`=该目录各批净可用合计。暂存仍绑所选目录 id；**签发**写路径仍按库存 **FIFO 跨批**分段发放与挂费（**BR-TCM-RX-007**）。**禁止**读侧假合并 id / 自动换厂家 | IF-DOR-35 |
| **BR-TCM-TPL-001** | 组套套用：模板 **`group_key` → 新 `prescriptionGroupKey`**；付数/代煎优先取模板头/行默认（**AS-BAS-MST-017 · BR-OTL-023～025**） | IF-DOR-36（套用入口） |


---

## 2. 页面与接口总览（契约概览）


| IF 编号 | 用户动作 | 接口语义（业务） | 读/写属性 | 关键规则（BR） | 关键状态变化（自然语言） | 涉及领域 | 查询服务 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| IF-DOR-30 | 进入中医 Tab、切换当前就诊、或刷新病证区 | 加载当前就诊**中医病证组**列表（按组聚合：病名+证候+组级属性） | 读 | BR-TCM-DIA-002 | 无写状态变化 | Encounter / Condition / ConditionDefinition | Encounter 维度诊断关联读侧 + 目录展示 enrich |
| IF-DOR-31 | 病证录入弹窗：切换病名/证候、输入关键字、翻页 | 分页检索有效**中医病名或证候**目录（`catalogRole`） | 读 | BR-TCM-DIA-005 | 无写状态变化 | ConditionDefinition | —（目录分页） |
| IF-DOR-32 | 确认病证组列表（新增/编辑后统一提交） | **全量批次**保存中医病证组（展开为病+证两行事实与就诊关联） | 写 | BR-TCM-DIA-001～003 / BR-TCM-DIA-006 | 新增或更新病证组；刷新 `tcm_syndrome_group` 与主诊/排序 | Condition / Encounter | OutpatientEncounterContextQueryModel |
| IF-DOR-33 | 删除某一病证组 | 按 **`tcm_syndrome_group`** 删除组内全部诊断事实与就诊关联 | 写 | BR-TCM-DIA-004 | 病证组消失；有中药医嘱引用则拒绝 | Condition / Encounter | OutpatientEncounterContextQueryModel |
| IF-DOR-34 | 进入中医 Tab、切换就诊、切换处方 Tab、或刷新 | 加载当前就诊**中药处方**列表（按 `prescription_no` 或草稿簇聚合为方头+行）；行含 **`doseUnitName`/`unitName`/`retailUnitPrice`**（**BR-TCM-RX-012**）；**排除**退费逆向复制行（**BR-TCM-RX-013**） | 读 | BR-TCM-RX-001 / BR-TCM-ISO-002 / **BR-TCM-RX-012** / **BR-TCM-RX-013** | 无写状态变化 | MedicationRequest + ChargeItem | `MedicationRequestQueryService#listTcmHerbalPrescriptionsByEncounter` |
| IF-DOR-35 | 编辑卡片内检索中药、输入关键字、翻页 | 分页检索**中药饮片**目录（行契约与 IF-DOR-11 同构；强制 `categoryCode=中草药`；**BR-TCM-RX-014** 不做同名折叠） | 读 | BR-TCM-RX-003 / **BR-TCM-RX-014** / BR-ORD-011（定价分流） | 无写状态变化 | ProductDefinition / ServiceLocationTemplate | —（目录组合读） |
| IF-DOR-36 | 编辑卡片「保存」、组套套用确认、或工具栏暂存 | **暂存**一张或多张中药处方（方头+行；携带 **`prescriptionGroupKey`**） | 写 | BR-TCM-RX-001 / 004～006 / 011 / BR-TCM-TPL-001 | 草稿中药行与费用草稿新增/更新/删除 | MedicationRequest / ChargeItem | OutpatientEncounterContextQueryModel |
| IF-DOR-37 | 工具栏「签发」、或编辑卡片「保存并签发」（若接线） | **签发**所选中药处方：按 **`prescriptionGroupKey` 映射 `prescription_no`**；请求生效；FIFO 发放与费用 **PLANNED**；**代煎费** | 写 | BR-TCM-ISO-002 / 003 / BR-TCM-RX-002 / 005 / 007～009 | 草稿→生效；同方共享处方号；代煎费待收费 | MedicationRequest / MedicationDispense / ChargeItem / ActivityDefinition | OutpatientEncounterContextQueryModel |
| IF-DOR-38 | 工具栏「撤回」或「删除」处方/行 | **签退**或**删除**中药处方（或方内单行）：费用未收费方可执行 | 写 | BR-TCM-ISO-002 / BR-TCM-RX-010 | 生效→草稿或行删除；代煎费同步清理 | 同 IF-DOR-37 | OutpatientEncounterContextQueryModel |
| IF-DOR-39 | 左侧树切换「历史处方」、或关键字过滤后点选 | 查询患者**非当前就诊**的历史中药处方摘要（可按处方号/时间展示） | 读 | BR-TCM-ISO-002 | 无写状态变化 | MedicationRequest | `MedicationRequestQueryService#listTcmHerbalPrescriptionHistoryByPatient` |


> **组套/医嘱模板**：打开抽屉、切换 Tab、预览、套用——**不**在本文 IF 表重复列出；见 **AS-BAS-MST-017 · IF-BAS-OTL-07/08**；套用写入仍走 **IF-DOR-36**。

---

## 3. 缺口跟踪（Gap）

| Gap ID | 缺口类型 | 文件 + 章节 | 缺什么 / 不一致点 | 关闭条件 | 影响 IF | 优先级 | 状态 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| **G-TCM-010-01** | **代码-文档不一致** | `DM-MED-003` §6.1.13 / §6.1.14 vs `MedicationRequestDomainService` | ~~Java 中药草稿实现过薄~~ | 领域 Service 与 Command 已对齐 DM（`createHerbalMedicationOrderDraft` / `changeHerbalMedicationOrderDraft` 落全字段） | IF-DOR-36 / IF-DOR-37 | **P0** | **已关闭** |
| **G-TCM-010-02** | **DM 能力** | `DM-ADM-002` §6.1.12 | ~~batchUpsert 全量语义误删西医~~ | 新增 `EncounterDomainService#batchUpsertTcmEncounterDiagnosisAssociation`（仅软删 `tcm_flag=true` 子集） | IF-DOR-32 | **P1** | **已关闭** |
| **G-TCM-010-03** | **QueryService 能力** | `DM-MED-003` §6.2 | ~~无按方聚合读接口~~ | 新增 `listTcmHerbalPrescriptionsByEncounter` / `listTcmHerbalPrescriptionHistoryByPatient` + 读模型 | IF-DOR-34 / IF-DOR-39 | **P2** | **已关闭** |

---

## 4. 接口编排说明书（业务步骤）

### IF-DOR-30 加载当前就诊中医病证组列表


| 维度 | 说明 |
| --- | --- |
| **触发前提** | 已选定门诊就诊与患者 |
| **主路径步骤** | 校验就诊上下文 → 查询 `tcm_flag=true` 的就诊诊断关联 → 按 **`tcm_syndrome_group`** 分组 → 每组装配 **病名**（source `tcm_dx`）与 **证候**（source `tcm_syndrome`）展示字段及组级主诊/排序/医疗类型/描述 |
| **关键分支** | 仅一侧（仅病或仅证）的异常组可标记为数据异常或过滤不展示 |
| **失败分支** | 查询失败 → EX-TCM-101 |
| **查询侧说明** | 纯读 |


---

### IF-DOR-31 分页检索中医病名/证候目录


| 维度 | 说明 |
| --- | --- |
| **触发前提** | 用户打开病证录入并选择病名或证候检索 |
| **主路径步骤** | 仅 **ACTIVE** 目录 → 按 **`catalogRole`** 收窄 source（`tcm_dx` 病名 / `tcm_syndrome` 证候）→ 关键字匹配名称及拼音/五笔 → 分页返回并补充分类展示文案 |
| **关键分支** | 未传 `catalogRole` 时拒绝或默认病名（以实现/SDD 固化） |
| **失败分支** | 检索失败 → EX-TCM-101 |
| **查询侧说明** | 纯读 |


---

### IF-DOR-32 保存中医病证组（全量批次）


| 维度 | 说明 |
| --- | --- |
| **触发前提** | 已选定就诊与患者；提交批次非空 |
| **主路径步骤** | 校验每组 **1 病 + 1 证** → 校验全就诊主诊组数量 → 对每组：新增则生成 **`tcm_syndrome_group`**，更新则沿用组号 → `saveDoctorDiagnosisTcm` upsert **2 条**诊断事实（`tcm_flag=true`）→ `batchUpsertTcmEncounterDiagnosisAssociation` 全量对齐本批中医就诊关联（**仅**软删 `tcm_flag=true` 且未再出现的关联，**不误删西医**）→ 不在批次内的历史中医组由前端 IF-DOR-33 显式删除或 diff 后 `removeEncounterDiagnosisAssociation` + `delEncounterDiagnosis` |
| **关键分支** | 组 ID 为空=新建组；组 ID 非空=更新组内病/证 |
| **失败分支** | 缺一病一证 → EX-TCM-201；主诊冲突 → EX-TCM-202；持久化失败 → EX-TCM-105 |
| **查询侧说明** | 写后刷新就诊诊断读模型 |


---

### IF-DOR-33 删除中医病证组


| 维度 | 说明 |
| --- | --- |
| **触发前提** | 已选定待删组的 **`tcm_syndrome_group`** |
| **主路径步骤** | 解析组内全部 `conditionId` → 统计中药医嘱引用数 → 大于零拒绝 → 否则删除组内全部诊断事实与就诊关联 |
| **关键分支** | 组已不存在 → 提示刷新 |
| **失败分支** | 存在医嘱引用 → EX-TCM-102；删除失败 → EX-TCM-105 |
| **查询侧说明** | 写后自列表移除该组 |


---

### IF-DOR-34 加载当前就诊中药处方列表


| 维度 | 说明 |
| --- | --- |
| **触发前提** | 已选定门诊就诊 |
| **主路径步骤** | 调用 `listTcmHerbalPrescriptionsByEncounter`（医生开立来源 + `tcm_flag=true`，领域层已按 `prescription_no` / `content_json.prescriptionGroupKey` 聚合）→ 应用层 enrich 目录名、ChargeItem 费用态与单价、**单位展示名**、**单剂零售价**（**BR-TCM-RX-012**）、病证组摘要 |
| **关键分支** | 无处方时展示空态；多 Tab 对应多方 |
| **失败分支** | 查询失败 → EX-TCM-101 |
| **查询侧说明** | 纯读 |


---

### IF-DOR-35 分页检索中药饮片目录


| 维度 | 说明 |
| --- | --- |
| **触发前提** | 用户于中药编辑卡片检索药品 |
| **主路径步骤** | 分页 **ProductDefinition** → **强制** `categoryCode=中草药` → 复用 **IF-DOR-11** 同一装配器与 **`DoctorStationAdviceCatalogRowDto`** 行契约（候选库位 ∩ 库存、占用预减、`LOT_NUMBER_PRICE` 批号对齐、`unitBands` 五槽）；**禁止**二次映射为饮片专用字段；**禁止**当前页同名折叠 |
| **关键分支** | 支持 `prepickedProductKeys`（组套套用后补齐）；关键字优先排序 |
| **失败分支** | 检索失败 → EX-TCM-101 |
| **查询侧说明** | 纯读；签发跨批 FIFO 不在本 IF |


---

### IF-DOR-36 暂存中药处方


| 维度 | 说明 |
| --- | --- |
| **触发前提** | 已选定就诊；方已绑定病证组；请求含 **`prescriptionGroupKey`** 与方头/行 |
| **主路径步骤** | 校验病证组存在 → 校验目录均为饮片 → **暂存前库存校验**（编辑行先释放原占用；数量按 **单剂×付数**）→ 对每行走**中药专用**草稿写路径（`tcm_flag`、付数、代煎、`content_json` 方头）→ 写 **ChargeItem 草稿**（锚请求，不建发放）→ **UPDATE** 行先 **`voidUnbilledDraftChargeItemsByServiceAnchor`（带 operator）** 再改请求并重挂费（与 **IF-DOR-14** 西药对称）→ 删除标记行移除请求与未计费费用 |
| **关键分支** | 组套套用：模板行映射为新 key 后批量暂存；**不**写 `prescription_no` |
| **失败分支** | 未绑病证组 → EX-TCM-203；库存不足 → EX-TCM-204；持久化失败 → EX-TCM-105 |
| **查询侧说明** | 写后 IF-DOR-34 可刷新 |


---

### IF-DOR-37 签发中药处方


| 维度 | 说明 |
| --- | --- |
| **触发前提** | 待签发行均为已暂存中药草稿；费用未收费 |
| **主路径步骤** | 按提交行中的 **`prescriptionGroupKey` 分组** → **Gate pending 调价**（**BR-TCM-RX-007** / **BR-PRC-LOCK-007**）→ 每组取或生成 **`prescription_no`**（已存在则复用）→ 写入 **`rx_type_code`**（饮片类别，**BR-TCM-RX-008**）→ **不**调用全就诊 **splitPrescription** → 逐行 **sign** → 每行 **void 请求锚点暂存费 → FIFO 替换发放 → 按发放分段批量建 `ChargeItem` 草稿 → `dispense_*` 锚点升 `PLANNED`**（**BR-TCM-RX-007**，与 **IF-DOR-15** 药线共用 Support）→ 若方头 **代煎=是**：按同方 based_on 清理旧代煎 companion → 按**付数**生成代煎 **ServiceRequest（based_on→代表药行，不写方号）+ ChargeItem**；若 **代煎=否**：按同方 based_on 删除代煎费 |
| **关键分支** | 批次可含多个 `prescriptionGroupKey`（多张方一次签发）；同批次共享统一 **signCode**（与西医签发展示一致） |
| **失败分支** | 含非中药行 → EX-TCM-205；FIFO 不足 → EX-TCM-204；代煎项目未配置 → EX-TCM-206 |
| **查询侧说明** | 写后列表展示 `prescription_no` 与生效态 |


---

### IF-DOR-38 签退/删除中药处方


| 维度 | 说明 |
| --- | --- |
| **触发前提** | 用户撤回整张方或删除方/行 |
| **主路径步骤** | 校验目标行 **`tcm_flag=true`** → 校验关联费用**非已收费** → **签退**：请求回草稿、**PLANNED→DRAFT**、释放发放预占、清理代煎费（若整方撤回）→ **删除**：移除草稿请求与未计费费用（已签发需先签退） |
| **关键分支** | 删整方=组内全部行；删单行=仅该行 |
| **失败分支** | 已收费 → EX-TCM-103；状态不允许 → EX-TCM-207 |
| **查询侧说明** | 写后 IF-DOR-34 刷新 |


---

### IF-DOR-39 查询患者中医历史处方


| 维度 | 说明 |
| --- | --- |
| **触发前提** | 已选定患者；当前就诊已知（用于排除） |
| **主路径步骤** | 调用 `listTcmHerbalPrescriptionHistoryByPatient`（排除当前就诊，已签发 `tcm_flag=true` 饮片按 `prescription_no` 聚合）→ 应用层可选 keyword 过滤并装配展示摘要 |
| **关键分支** | 可按关键字过滤处方名/药品名（前端或后端，SDD 固化） |
| **失败分支** | 查询失败 → EX-TCM-101 |
| **查询侧说明** | 纯读；**只读引用**，套用仍走 IF-DOR-36 |


---

## 5. 业务异常与用户提示


| 语义代码 | 场景 | 用户提示方向 | 可重试 |
| --- | --- | --- | --- |
| EX-TCM-101 | 列表或目录检索失败 | 缩小条件或稍后重试 | 是 |
| EX-TCM-102 | 病证组已有中药医嘱引用 | 先撤销或调整相关处方后再删 | 否 |
| EX-TCM-103 | 处方/行已收费，不可撤回或删除 | 需走退费流程 | 否 |
| EX-TCM-104 | 未选择患者或就诊 | 请先选择患者 | 是 |
| EX-TCM-105 | 保存或删除持久化失败 | 稍后重试或联系管理员 | 视原因 |
| EX-TCM-201 | 病证组缺一病一证 | 每组须同时选择病名与证候 | 否 |
| EX-TCM-202 | 主诊病证组超过 1 个 | 请只保留一个主诊病证组 | 否 |
| EX-TCM-203 | 中药方未绑定病证组 | 请先维护并绑定中医病证组 | 否 |
| EX-TCM-204 | 库存不足 | 调整品种、付数或换批 | 否 |
| EX-TCM-205 | 签发批次含非中药行 | 请在对应 Tab 签发 | 否 |
| EX-TCM-206 | 代煎诊疗项目未配置 | 联系管理员维护代煎项目 | 否 |
| EX-TCM-207 | 当前状态不允许撤回/删除 | 刷新列表后重试 | 视原因 |


---

## 6. 事件语义与消费方

> 事件英文名与载荷以各 DM §5 为准。


| 事件语义（DM 英文名以 DM 为准） | 触发相位（IF） | 典型消费方 |
| --- | --- | --- |
| `EncounterDiagnosisAssociationUpsertedEvent` / `EncounterDiagnosisSavedEvent` | IF-DOR-32 | 就诊读模型、病历/质控 |
| `EncounterDiagnosisAssociationRemovedEvent` / `EncounterDiagnosisDeletedEvent` | IF-DOR-33 | 同上 |
| `HerbalMedicationOrderDraftCreatedEvent` / `HerbalMedicationOrderDraftEditedEvent` | IF-DOR-36 | 医嘱列表 QueryService、库存预减 |
| `MedicationOrderSignedEvent`（或域内等价签发事件） | IF-DOR-37 | 收费队列、药房发药、处方审核 |
| `MedicationOrderRevokedEvent`（或域内等价签退事件） | IF-DOR-38 | 收费回退、库存释放 |


---

## 7. 非功能性需求


| 类别 | 要求 |
| --- | --- |
| **性能（经验目标）** | IF-DOR-30/34 ≤800ms；IF-DOR-31/35 ≤800ms；IF-DOR-32 ≤2000ms；IF-DOR-36 ≤2000ms；IF-DOR-37 ≤3000ms（含 FIFO 与代煎费） |
| **幂等** | 相同 **`prescriptionGroupKey`** 重复签发应复用已有 `prescription_no` 或返回可理解冲突 |
| **并发** | 双终端同时编辑同一就诊病证组或同一处方，以后写为准或返回冲突（SDD 固化） |
| **一致性** | **BR-TCM-ISO-001/002** 中西医签发路径互不覆盖；删除/签退与收费态校验同源 |
| **审计** | 病证组记录人/时刻、中药签发 signCode、代煎费生成须可追溯 |


---

## 8. 测试要点（业务视角）


| 类别 | 要点 |
| --- | --- |
| **正向** | IF-DOR-32 新建/更新多组病证；IF-DOR-36 多方多行暂存；IF-DOR-37 两 `prescriptionGroupKey` 同批签发得两个 `prescription_no`；**签发后门诊收银可见饮片 PLANNED 费用**（分段挂费完整）；代煎=是/generate 代煎费；IF-DOR-35 与 IF-DOR-11 字段同构；组套套用后暂存 |
| **异常** | EX-TCM-201 缺一证；EX-TCM-102 有处方时删病证组；EX-TCM-204 库存不足；EX-TCM-103 已收费不可撤 |
| **规则** | BR-TCM-ISO-003 中医签发不触发 **BR-ORD-023**；BR-TCM-RX-005 付数乘剂量；BR-TCM-RX-002 key 与处方号 1:1 |
| **隔离** | 西医 Tab 签发不影响中医草稿；中医 Tab 签发不影响西医行 |
| **并发** | 同方双签、病证组与开方并行编辑 |


---

## 9. 关联文档与变更历史

### 9.1 关联文档


| 文档 |
| --- |
| `health/AppService/AS-OP-DOR-001-门诊医生站就诊管理.md` |
| `health/AppService/AS-OP-DOR-002-门诊医生站诊断录入.md` |
| `health/AppService/AS-OP-DOR-003-门诊医生站医嘱开立.md` |
| `health/AppService/AS-BAS-MST-017-组套模板.md` |
| `docs/领域/DomainModel/DM-CLN-002-ConditionDefinition-诊断定义.md |
| `docs/领域/DomainModel/DM-CLN-003-Condition-诊断记录.md |
| `docs/领域/DomainModel/DM-ADM-002-Encounter-就诊.md |
| `docs/领域/DomainModel/DM-MED-001-MedicationDefinition-药品定义.md |
| `docs/领域/DomainModel/DM-MED-003-MedicationRequest-药品申请.md |
| `docs/领域/DomainModel/DM-MED-002-MedicationDispense-药品发放.md |
| `docs/领域/DomainModel/DM-ADM-011-ChargeItem-收费明细.md |
| `docs/领域/DomainModel/DM-WKF-001-ActivityDefinition-诊疗项目定义.md |
| `docs/领域/DomainModel/DM-CUS-012-OrderTemplate-组套模板.md |
| `docs/领域/DomainModel/DM-CUS-006-ServiceLocationTemplate-取药科室.md |
| `health/需求/001中西医处方开立-分方规则（门诊-住院）.md` |


### 9.2 变更历史


| 版本 | 日期 | 变更摘要 |
| --- | --- | --- |
| v1.20 | 2026-09-16 | **IF-DOR-37**：签发前 pending 调价互斥（同 **BR-ORD-037** / **BR-PRC-LOCK-007**）；对齐 **AS-FIN-PRC-001 v0.10** |
| v1.19 | 2026-09-06 | **BR-TCM-RX-009**：单独取消代煎须按收费态（待结/已结/退费中）拦截，经 based_on 反查方号，不依赖 live companion SR |
| v1.18 | 2026-09-06 | **BR-TCM-RX-009**：代煎费收银退款终态须同步医生站清 `suffering_flag` 并收口停嘱 companion；对齐 **AS-OP-FIN-003 · BR-REF-025** |
| v1.17 | 2026-09-06 | **BR-TCM-RX-009**：代煎 companion 以 `based_on`→药嘱关联，SR/`ChargeItem` **不**写中药方号；读侧/void/退代煎归方经 based_on（负向回原 CP）；对齐成码 `TcmDecoctionAssembler` / `TcmDecoctionBasedOnPrescriptionResolver` |
| v1.16 | 2026-08-02 | **BR-TCM-RX-009**：代煎签发挂个人现金自费户（与挂号双账户/转自费目标户对齐）；去掉收银展示硬盖。对齐 **AS-OP-FIN-002 v1.27**、双账户开户改造 |
| v1.15 | 2026-08-02 | **IF-DOR-36/37 挂账**：饮片费用默认双账户医保户（与 **AS-OP-DOR-003** 同构）；代煎初版仍写展示覆盖（已由 v1.16 纠偏） |
| v1.14 | 2026-08-02 | **BR-TCM-RX-009** 纠偏：门诊代煎自费仅账单/医嘱侧，**禁止**动 Account；对齐 **AS-OP-DOR-010-SDD v1.11**、**AS-OP-FIN-002 v1.25** |
| v1.13 | 2026-08-02 | **BR-TCM-RX-009** 扩展：门诊代煎强制自费（初版曾写开户，已由 v1.14 纠偏）；与 **AS-OP-FIN-002 · BR-OPC-020～022** 混结/转账户口径对齐 |
| v1.12 | 2026-08-01 | **BR-TCM-RX-014** 废止读侧同名合并：IF-DOR-35 选品与西药同构（各目录行展示）；签发仍 FIFO 跨批（**BR-TCM-RX-007**）；对齐 **AS-OP-DOR-010-SDD** |
| v1.11 | 2026-07-31 | **BR-TCM-RX-007**：签发分段挂费按分段 `lotNumber` 匹配 `LOT_NUMBER_PRICE`（同 **BR-ORD-016**）；编排类落点 `outpatient.doctorstation.advice.support.OutpatientMedicationSignChargeOrchestrator` |
| v1.10 | 2026-07-24 | **BR-TCM-RX-014**：IF-DOR-35 当前页同名合并（FIFO 头目录开立、当前批/合并总量、厂家取当前批）；对齐 **AS-OP-DOR-010-SDD**（已被 v1.12 废止读侧合并） |
| v1.9 | 2026-07-09 | **BR-TCM-RX-013**：IF-DOR-34 排除退费逆向复制饮片行；对齐 **AS-OP-DOR-003 · BR-ORD-030** |
| v1.8 | 2026-06-20 | **orchestration 落点对齐**：`OutpatientMedicationSignChargeOrchestrator → `module.orchestration.advice` |（已迁至 application.shared.advice）
| v1.7 | 2026-06-20 | **support 落点对齐**：中药签发分段挂费复用 **`OutpatientMedicationSignChargeOrchestrator**（`module/orchestration/advice`）；详见 **AS-OP-DOR-010-SDD** |
| v1.6 | 2026-06-17 | **IF-DOR-34（BR-TCM-RX-012）**：`TcmRxLineDto` 增补 `doseUnitName`/`unitName`/`retailUnitPrice`；对齐 **AS-OP-DOR-003 v1.43** / **AS-OP-DOR-010-SDD v1.5** |
| v1.5 | 2026-06-17 | **`initDoctorStationTcm`** 独立下发中药用法/用药频次；方头/暂存/列表增补 **`rateCode`**；领域按方聚合 `TcmHerbalPrescriptionGroupQueryModel.rateCode`；**IF-DOR-35** 目录单位与 **IF-DOR-11** 对齐：主单位=包装单位，**不下发** `defEncounterUnit*`/`defInhospitalUnit*`；对齐 **AS-OP-DOR-003 v1.42** / **AS-OP-DOR-010-SDD v1.4** / **AS-SDD v3.28** |
| v1.4 | 2026-06-17 | **IF-DOR-35** 响应行与 **IF-DOR-11** 同构（废止饮片专用目录 DTO）；**IF-DOR-37** 明确签发分段挂费全链路（**BR-TCM-RX-007**）；**IF-DOR-36** UPDATE void 与西药对称；对齐 **OutpatientMedicationSignChargeOrchestrator** / **AS-OP-DOR-003-SDD v3.27** |
| v1.3 | 2026-06-16 | 应用层成码对齐：HTTP `doctorStationTcm`；契约类名缩短（`TcmRx*`）；常量 `DoctorStationTcmConstants` 落 domain.share.constants |
| v1.2 | 2026-06-16 | §2/§4 与领域成码对齐：`batchUpsertTcmEncounterDiagnosisAssociation`、按方 QueryService 方法名与编排口径 |
| v1.1 | 2026-06-16 | §3 增补并关闭 Gap **G-TCM-010-01～03**（领域层中药草稿、中医批次 upsert、按方 QueryService） |


---

**文档负责人**：（待填） · **评审**：（待填）
