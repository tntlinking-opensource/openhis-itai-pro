# 住院医生站 · 中医 · 应用服务设计（AS）

> **文档职责**：说明住院医生站「中医处方」Tab 的**接口契约（IF）**、业务语义、涉及聚合与 QueryService、规则（BR）、编排意图；作为上游契约供详细设计与实现对齐。
>
> **本文必须写**：IF 编号；用户动作与读写属性；关键校验与状态变化；Gap；异常口径；事件语义；非功能约束；业务向测试要点。
>
> **本文禁止写**：具体类名/应用方法名；Request/Response 字段明细；包路径；Mapper；伪代码；写库顺序；QueryService 字段组装规则。
>
> **页面边界**：住院医生站主界面「中医处方」标签——**中医病证组**维护区（病名+证候成组录入/删除）、**中药处方**多 Tab 工作区（**长期/临时**分轨筛选、左侧协定/常用/历史组套树、处方 Tab 切换/新增、方头表单、明细表与编辑卡片、工具栏暂存/签发/撤回/删除）、**停嘱** / **取消停嘱**（均仅长期）、组套/医嘱模板抽屉套用。**左侧患者列表**见 **AS-IP-DOR-001**；西医诊断 Tab 见 **AS-IP-DOR-002**；西医/中成药医嘱见 **AS-IP-DOR-003**；护士校对/执行/汇总领药见 **AS-IP-NUR-003/004/010**；住院发药/退药见 **AS-IP-PHA-001/003**。
>
> **覆盖声明**：完整契约以 **§2 IF-IP-TCM-30～42** 为准；与 **AS-OP-DOR-010** 同构并叠加住院差异（**TherapyType** 长临分轨、医生站**仅 Request** 不发药不算费、临嘱频次「立即」、长期方头一致性、停嘱/取消停嘱、**按方改开立时间**、代煎费改由**护士执行**按次计费）；组套/模板列表与预览由 **AS-BAS-MST-017** 提供。**HTTP/成码细节**见对应 AppService-SDD，本文不展开。
>
> **版本**：v1.7 · **日期**：2026-09-09

---

## 1. 场景概述

### 1.1 目标与边界

| 维度 | 说明 |
| --- | --- |
| **在内** | 中医 Tab 页初始化（用法/频次/长临上下文，**IF-IP-TCM-29**）；加载当前住院就诊**中医病证组**列表（IF-IP-TCM-30）；分页检索**中医病名/证候**目录（IF-IP-TCM-31）；**全量批次**保存/更新中医病证组（IF-IP-TCM-32）；按组删除中医病证（IF-IP-TCM-33）；加载当前就诊**中药处方**列表（按方聚合，可选 **therapyType** 筛选，IF-IP-TCM-34）；分页检索**中药饮片**目录（IF-IP-TCM-35）；**暂存**一张或多张中药处方（方头+明细行，含 **therapyEnum**，IF-IP-TCM-36）；**签发**中药处方（按 **`prescriptionGroupKey` 映射 `prescription_no`**，IF-IP-TCM-37）；**签退/删除**中药处方或方内行（IF-IP-TCM-38）；查询患者**历史中药处方**（IF-IP-TCM-39）；**停嘱**（长期整方，IF-IP-TCM-40）；**取消停嘱**（长期整方，IF-IP-TCM-41）；**按方改开立时间**（整方各味统一 `req_opened_time`，IF-IP-TCM-42）。组套/医嘱模板：**消费** **AS-BAS-MST-017 · IF-BAS-OTL-07/08**（`template_kind_enum=TCM`），套用后仍调 **IF-IP-TCM-36** |
| **在外** | 患者列表（**AS-IP-DOR-001**）；西医诊断 Tab（**AS-IP-DOR-002**，**不含**中医病证组成组）；西医/中成药/诊疗医嘱（**AS-IP-DOR-003**）；护士校对/执行/汇总领药（**AS-IP-NUR-003/004/010**）；住院发药/退药（**AS-IP-PHA-001/003**）；组套模板维护后台（**AS-BAS-MST-017** 维护端）；电子处方打印、出院带药专页 |
| **涉及域** | Condition；ConditionDefinition；Encounter / EncounterDiagnosis；MedicationRequest；ActivityDefinition（代煎诊疗项目定义，执行侧消费）；ProductDefinition；OrderTemplate；ServiceLocationTemplate；Practitioner / Organization |
| **编排权威** | 以本文 **§2、§4** 为准 |

### 1.2 角色与关键动作

| 角色 | 关键动作 |
| --- | --- |
| **住院医生** | 在 **AS-IP-DOR-001** 选定患者 → 进入中医 Tab → 切换**长临** → 维护**一病一证**诊断组 → 新增/切换中药处方 Tab → 从组套树套用或手工选药 → 维护方头（付数、用法、频次、煎煮法、代煎等）与明细 → 暂存 → 签发；对**长期**方**停嘱** / **取消停嘱**；必要时**按方改开立时间**、撤回、删除处方或删除病证组 |
| **系统** | 病证组落库写入 `tcm_syndrome_group` 与 `tcm_flag`；中药行强制 `tcm_flag=true`；签发按 **`prescriptionGroupKey` → `prescription_no`**，**不**调用西医全就诊 greedy 分方；医生站**仅**持久化 Request（**ACTIVE**），**不**建发放与账单；**代煎费**在护士执行该方时按**当次付数**生成/刷新（**AS-IP-NUR-004**，非本文签发相位） |

### 1.3 术语与口径约定

| 术语 | 口径 |
| --- | --- |
| **与门诊同构（继承）** | 中医病证组、一病一证、`prescriptionGroupKey` 不落库、方头+行、饮片目录、`BR-TCM-DIA-*` / `BR-TCM-RX-001～005` / `BR-TCM-RX-011` 等语义与 **AS-OP-DOR-010** 一致，本文仅写**住院增量** |
| **TherapyType（长临）** | 每张方（全部味药行）共享同一 **`therapy_enum`**：`LONG_TERM`（长期）/ `TEMPORARY`（临时）；列表可按 **therapyType** 筛选（对齐 **AS-IP-DOR-003 · BR-IP-ORD-011**） |
| **住院医生站签发链路** | 对齐 **AS-IP-DOR-003 §1.7**：长临暂存/签发**均仅** `MedicationRequest`；**不**创建 `ChargeItem`、**不**创建药品 `MedicationDispense`；**不**在医生站校验库存（**BR-IP-ORD-003**） |
| **临嘱频次** | **长期**方：`rate_code` **不得**为「立即」（**BR-IP-TCM-005**）。**临时**方：新建默认立即，医生可改频次，后端**不再强制** `STAT`（与西药 **BR-IP-ORD-015** 一致）。创建草稿须把方头 `therapyEnum` 写入领域命令，**禁止**领域层写死临时 |
| **方头一致性（长期）** | 同一逻辑方（同 **`prescriptionGroupKey`** 草稿簇或同 **`prescription_no`** 已签发行）内各行方头字段**必须一致**：至少含 **`rateCode`**、**`chineseHerbsDoseQuantity`**（付数）、**`sufferingFlag`**（代煎）、**`methodCode`**（用法）；违反拒绝暂存/签发（**BR-IP-TCM-006**） |
| **一方一行（下游展示）** | 护士站/药房列表过滤后：中药**展示**一行=一整方（`prescription_no` 聚合）；西药一行=一味药。**数据流不变**：仍多 `MedicationRequest`、执行后多 `MedicationDispense`、多 `ChargeItem`（**AS-IP-NUR-003/004/010**、**AS-IP-PHA-001** 补展示与方级勾选展开，**禁止**新建合并实体） |
| **整方操作（UI）** | 护士校对/执行/汇总领药/药房发药：勾选「一方」时应用层解析同 `prescription_no` 下全部 req 逐条调域命令；医生站停嘱/取消停嘱亦按**整方**（同 `prescription_no`）批量 |
| **中西医隔离** | **AS-IP-DOR-003 · IF-IP-DOR-15** 签发**不得**包含 `tcm_flag=true` 行；本文 IF-IP-TCM-37 **不得**处理 `tcm_flag≠true` 行；**禁止**对中医 Tab 调用 **BR-ORD-023** 全就诊分方 |
| **代煎费（住院）** | 租户配置代煎 **ActivityDefinition**；**签发不产生代煎费**；护士**执行该方**时，若方头代煎=是，按**当次付数**同步生成/刷新代煎 **`ChargeItem`（`ORDER_EXECUTE`，待结算）**（**BR-IP-TCM-008**，编排归属 **AS-IP-NUR-004**）；代煎=否则不生成 |
| **每次执行计费** | 药行：护士执行每计划点按味药逐条 FIFO 新建 Dispense + `ORDER_EXECUTE` Charge（**AS-IP-NUR-004 · BR-IP-EXE-016**）；与门诊「签发即挂费」不同 |
| **签退/删除门控** | 因医生站未预建账单/发放，门控改为：同方任一 req 已存在**净执行 Procedure**、**已发药 Dispense（COMPLETED）** 或**不可回退 ChargeItem** 则拒绝签退/删除；仅 **DRAFT/ACTIVE**（未校对）或已校对但未执行路径可签退（具体态组合 SDD 固化） |
| **QueryService 门控** | 写前 **`InpatientEncounterContextQueryModel`**（`class_enum=IMP`）；列表读侧复用 `listTcmHerbalPrescriptionsByEncounter` 等（医生开立来源 + `tcm_flag=true`） |
| **组套/医嘱模板** | **IF-BAS-OTL-07/08**；`template_kind_enum=TCM`；套用映射新 **`prescriptionGroupKey`** 并调 **IF-IP-TCM-36** |

### 1.4 本期范围与非目标

| 类别 | 内容 |
| --- | --- |
| **本期（契约）** | IF-IP-TCM-29～41：初始化、病证组 CRUD、中药按方暂存/签发/签退/删除、饮片目录、历史处方、**长临筛选**、长期**停嘱/取消停嘱**；`prescriptionGroupKey` 不落库 |
| **本期（UI）** | 住院医生站复用共享 `tcm-advice` 组件；后端 API `POST /api/app/inpatientDoctorStationTcm/{methodName}` **已就绪**；**长临筛选、停嘱按钮、前端路由接通**以迭代为准 |
| **非目标（同站其他 Tab）** | 患者列表、西医诊断、西医医嘱、病历、报告、电子处方 |
| **非目标（中成药）** | **中成药**仍在 **AS-IP-DOR-003** 开立；**不在**中医 Tab 选品 |
| **非目标（组套维护）** | **AS-BAS-MST-017** 维护端 |
| **非目标（门诊路径）** | 医生站签发建发放/挂费、门诊收银、门诊代煎签发挂费（**AS-OP-DOR-010 · BR-TCM-RX-006/007/009**） |
| **非目标（护士/药房编排细节）** | 方级展示、中西药 Tab、`medicineCategoryTab`、执行代煎费编排——在 **AS-IP-NUR-003/004/010**、**AS-IP-PHA-001/003** 补条款，本文仅声明边界与 **BR-IP-TCM-008** |
| **非目标（术语下拉 duplicate）** | 初始化 IF-IP-TCM-29 独立下发中药用法/用药频次（频次与 **IF-IP-DOR-18** 同源）；不在 IF-IP-TCM-30～41 重复编号 |

### 1.5 领域清单（聚合 + 职责 + DM 路径）

| 聚合（域） | 在本场景中的职责（摘要） | DM 路径 |
| --- | --- | --- |
| **Condition** | 中医诊断事实；`tcm_flag` | `DM-CLN-003-Condition-诊断记录.md` |
| **ConditionDefinition** | 中医病名/证候目录分页 | `DM-CLN-002-ConditionDefinition-诊断定义.md` |
| **Encounter（含 EncounterDiagnosis）** | 住院就诊与病/证关联；`tcm_syndrome_group` | `DM-ADM-002-Encounter-就诊.md` |
| **MedicationRequest** | 中药饮片医嘱；长临、付数、代煎、方头扩展；签发/签退/停嘱 | `DM-MED-003-MedicationRequest-药品申请.md` |
| **ActivityDefinition** | 代煎诊疗项目定义（执行侧引用） | `DM-WKF-001-ActivityDefinition-诊疗项目定义.md` |
| **MedicationDefinition / ProductDefinition** | 饮片目录检索 | `DM-MED-001-MedicationDefinition-药品定义.md` |
| **OrderTemplate** | 中医组套只读消费 | `DM-CUS-012-OrderTemplate-组套模板.md` |
| **ServiceLocationTemplate** | 目录候选库位（展示/参考，医生站不预占） | `DM-CUS-006-ServiceLocationTemplate-取药科室.md` |
| **Practitioner / Organization** | 开方医生、开立科室 | `DM-ADM-005` / `DM-ADM-004` |

### 1.6 业务规则（BR-*）

| 规则 ID | 描述 | 绑定 IF |
| --- | --- | --- |
| **BR-IP-TCM-001** | 写操作前就诊须为 **IMP** 且在 **InpatientEncounterContextQueryModel** 可见范围内 | IF-IP-TCM-32～38、40、41 |
| **BR-TCM-DIA-001** | 每个中医病证组**必须**含 **1 病名 + 1 证候** | IF-IP-TCM-32 |
| **BR-TCM-DIA-002** | 同组病/证共享 **`tcm_syndrome_group`**；组级主诊/排序同步到组内两行 | IF-IP-TCM-32 |
| **BR-TCM-DIA-003** | 全就诊**主诊病证组**至多 **1** 个 | IF-IP-TCM-32 |
| **BR-TCM-DIA-004** | 删除病证组前校验组内**任一** `conditionId` **不存在已开立中药医嘱**引用 | IF-IP-TCM-33 |
| **BR-TCM-DIA-005** | 中医目录分页仅 **ACTIVE**；`catalogRole` 决定 source | IF-IP-TCM-31 |
| **BR-TCM-DIA-006** | 病证组**全量批次**；仅对齐 `tcm_flag=true` 关联，**不误删**西医诊断 | IF-IP-TCM-32 |
| **BR-TCM-ISO-001** | **AS-IP-DOR-003 · IF-IP-DOR-15** 签发**不得**含 `tcm_flag=true` 行 | （跨 AS） |
| **BR-TCM-ISO-002** | 本文签发/签退**仅**处理 `tcm_flag=true` 医生开立行 | IF-IP-TCM-37 / 38 |
| **BR-TCM-ISO-003** | 禁止调用 **BR-ORD-023** `splitPrescription`；处方号按 **BR-TCM-RX-002** | IF-IP-TCM-37 |
| **BR-IP-TCM-002** | 住院中医 Tab 与西医医嘱 Tab **签发路径隔离**（同 **BR-TCM-ISO-001/002**） | IF-IP-TCM-37 / 38 |
| **BR-IP-TCM-003** | 医生站暂存/签发**仅 Request**；**不**建 `ChargeItem` / `MedicationDispense`（**BR-IP-ORD-005**） | IF-IP-TCM-36 / 37 |
| **BR-IP-TCM-004** | 医生站**不校验库存**（**BR-IP-ORD-003**）；库存 Gate 在护士执行 | IF-IP-TCM-36 / 37 |
| **BR-IP-TCM-005** | **长期**方：`therapy_enum=LONG_TERM` 且 `rate_code` **不得为立即**。**临时**方：`rate_code` 由医生选择，**不**强制立即（新建默认 `STAT` 仅前端/默认值）。创建时 `CreateHerbalMedicationOrderDraftCommand.therapyEnum` 取方头；未传才回落临时 | IF-IP-TCM-36 / 37 |
| **BR-IP-TCM-006** | 同方（同 key 或同 `prescription_no`）各行方头 **`rateCode` / `chineseHerbsDoseQuantity` / `sufferingFlag` / `methodCode`** 等须一致 | IF-IP-TCM-36 / 37 |
| **BR-TCM-RX-001** | **`prescriptionGroupKey` 不落库**；持久化以 **`prescription_no`** 关联同方各行 | IF-IP-TCM-36 / 37 |
| **BR-TCM-RX-002** | 签发时每个 **`prescriptionGroupKey` → 1 个 `prescription_no`**（中草药序列）；同 key 已有未撤回处方号则**复用** | IF-IP-TCM-37 |
| **BR-TCM-RX-003** | 目录 **`categoryCode=中草药`**；落库 **`tcm_flag=true`** | IF-IP-TCM-35 / 36 |
| **BR-TCM-RX-004** | 每张方**须绑定 1 个中医病证组** | IF-IP-TCM-36 / 37 |
| **BR-TCM-RX-005** | **请求数量 = 单剂用量 × 付数**；后端重算为准 | IF-IP-TCM-36 / 37 |
| **BR-TCM-RX-011** | 中药草稿须走**专用**写路径（**BR-MRQ-011**） | IF-IP-TCM-36 |
| **BR-IP-TCM-007** | 签发写入 **`rx_type_code`** 为**住院中药饮片**类别（可随医疗类型抬升）；**不做** greedy 分方 | IF-IP-TCM-37 |
| **BR-IP-TCM-008** | **代煎=是**：护士**执行该方**时按**当次付数**生成/刷新代煎 **`ChargeItem`**；**代煎=否**不生成；**医生站签发不产生代煎费** | （**AS-IP-NUR-004** 编排） |
| **BR-IP-TCM-009** | **签退/删除**：同方无净执行、无已发药、无不可回退费用方可；已校对未执行允许签退 | IF-IP-TCM-38 |
| **BR-IP-TCM-010** | **停嘱/取消停嘱**仅**长期**；按 **`prescription_no` 整方**批量；语义对齐 **BR-IP-ORD-013/025** | IF-IP-TCM-40 / 41 |
| **BR-IP-TCM-011** | **按方改开立时间**：按 **`prescription_no` 和/或 `prescriptionGroupKey` 整方扩行**，方内各味统一写同一 `req_opened_time`；**不**改开立人 / `req_authored_time`；语义对齐 **BR-IP-ORD-031** / **IF-IP-DOR-26 OPENED**；**签发不自动统一**开立时间（各味草稿时刻保留，护士站折叠展示取最晚见 **AS-IP-NUR-003/004**） | IF-IP-TCM-42 |
| **BR-TCM-RX-012** | 列表行下发 **`doseUnitName`/`unitName`/`retailUnitPrice`**：已挂费优先 **`ChargeItem.unit_price`**（**BR-PRICE-SRC-001**），否则目录 **DOSE 槽**参考价（与门诊 IF-DOR-34 一致） | IF-IP-TCM-34 |
| **BR-TCM-RX-014** | **IF-IP-TCM-35** 选品下拉**不做同名合并**：口径同门诊 **BR-TCM-RX-014**（与西药目录同构，各目录行展示；`availableQuantity`=目录各批合计）。暂存绑所选目录；签发写路径仍按库存 FIFO（住院签发侧无门诊分段挂费，见 **BR-IP-TCM-007**）。**禁止**读侧假合并 id / 自动换厂家 | IF-IP-TCM-35 |
| **BR-TCM-TPL-001** | 组套套用：`group_key` → 新 `prescriptionGroupKey` | IF-IP-TCM-36 |

---

## 2. 页面与接口总览（契约概览）

| IF 编号 | 用户动作 | 接口语义（业务） | 读/写 | 关键规则（BR） | 关键状态变化（自然语言） | 涉及领域 | 查询服务 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| IF-IP-TCM-29 | 进入中医 Tab、切换患者 | 中医 Tab 初始化（用法/频次下拉；长临上下文默认值） | 读 | — | 无 | 术语 | — |
| IF-IP-TCM-30 | 进入 Tab、切换患者、刷新病证区 | 加载当前住院就诊**中医病证组**列表 | 读 | BR-TCM-DIA-002 | 无 | Encounter / Condition | 就诊诊断读侧 + enrich |
| IF-IP-TCM-31 | 病证弹窗：检索病名/证候、翻页 | 分页检索**中医病名或证候**目录 | 读 | BR-TCM-DIA-005 | 无 | ConditionDefinition | 目录分页 |
| IF-IP-TCM-32 | 确认病证组列表提交 | **全量批次**保存中医病证组 | 写 | BR-TCM-DIA-001～003 / 006；BR-IP-TCM-001 | 病证组新增/更新 | Condition / Encounter | InpatientEncounterContextQueryModel |
| IF-IP-TCM-33 | 删除某一病证组 | 按 **`tcm_syndrome_group`** 删除组内全部诊断 | 写 | BR-TCM-DIA-004；BR-IP-TCM-001 | 病证组删除或拒绝 | Condition / Encounter | InpatientEncounterContextQueryModel |
| IF-IP-TCM-34 | 进入 Tab、切换长临、切换处方 Tab、刷新 | 加载当前就诊**中药处方**列表（按方聚合；可选 **therapyType** 筛选） | 读 | BR-TCM-RX-001 / 012；BR-IP-TCM-005 | 无 | MedicationRequest | `MedicationRequestQueryService#listTcmHerbalPrescriptionsByEncounter` |
| IF-IP-TCM-35 | 编辑卡片检索饮片、翻页 | 分页检索**中药饮片**目录（契约同 **IF-IP-DOR-11** 行；强制中草药；**BR-TCM-RX-014** 不做同名折叠） | 读 | BR-TCM-RX-003 / **BR-TCM-RX-014** | 无 | ProductDefinition | 目录组合读 |
| IF-IP-TCM-36 | 保存、组套套用、工具栏暂存 | **暂存**一张或多张中药处方（方头+行；**therapyEnum**；**prescriptionGroupKey**） | 写 | BR-IP-TCM-003～006；BR-TCM-RX-001/004/005/011；BR-TCM-TPL-001 | 草稿 req 新增/更新/删除 | MedicationRequest | InpatientEncounterContextQueryModel |
| IF-IP-TCM-37 | 工具栏签发 | **签发**所选中药方：映射 **`prescription_no`**；请求 **ACTIVE**；**无**发放/账单 | 写 | BR-IP-TCM-002/003/005/006/007；BR-TCM-ISO-002/003；BR-TCM-RX-002 | 草稿→**ACTIVE**；同方共享处方号 | MedicationRequest | InpatientEncounterContextQueryModel |
| IF-IP-TCM-38 | 撤回或删除处方/行 | **签退**或**删除**中药方（未执行/未发药路径） | 写 | BR-TCM-ISO-002；BR-IP-TCM-009 | 生效→草稿或行删除 | MedicationRequest | InpatientEncounterContextQueryModel |
| IF-IP-TCM-39 | 侧栏历史处方、关键字过滤 | 查询患者**非当前就诊**历史中药处方摘要 | 读 | BR-TCM-ISO-002 | 无 | MedicationRequest | `MedicationRequestQueryService#listTcmHerbalPrescriptionHistoryByPatient` |
| IF-IP-TCM-40 | 停嘱（长期方） | 批量**停嘱**所选**长期**中药方（按 **`prescription_no` 整方**） | 写 | BR-IP-TCM-010；BR-IP-TCM-001 | 长期 req → **STOPPED** + 结束时间 | MedicationRequest | InpatientEncounterContextQueryModel |
| IF-IP-TCM-41 | 取消停嘱（长期方） | 批量**取消停嘱**所选 **STOPPED** 长期中药方（整方） | 写 | BR-IP-TCM-010；对齐 **BR-IP-ORD-025** | **VERIFIED** 或 **ACTIVE**；清空结束时间 | MedicationRequest | InpatientEncounterContextQueryModel |
| IF-IP-TCM-42 | 工具栏「改开立时间」 | **按方**将所选中药方各味 **`req_opened_time` 统一为同一时刻**（已签按处方号、草稿按方标识扩行） | 写 | BR-IP-TCM-011；BR-IP-TCM-001；对齐 **BR-IP-ORD-031** | 仅开立时间列变更；状态/开立人不变 | MedicationRequest | InpatientEncounterContextQueryModel |

> **组套/医嘱模板**：**AS-BAS-MST-017 · IF-BAS-OTL-07/08**；套用写入仍走 **IF-IP-TCM-36**。

---

## 3. 缺口跟踪（Gap）

| Gap ID | 缺口类型 | 文件 + 章节 | 缺什么 / 不一致点 | 关闭条件 | 影响 IF | 优先级 | 状态 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| **G-IP-TCM-001** | **QueryService 能力** | `DM-MED-003` §6.2.19 | ~~`listTcmHerbalPrescriptionsByEncounter` 入参尚无 `therapyEnum` 筛选~~ | DM §6.2 增补可选 `therapyEnum` 参数并实现 | IF-IP-TCM-34 | **P1** | **已关闭** |

> 门诊中医领域 Gap **G-TCM-010-01～03** 已关闭；住院增量 **G-IP-TCM-001** 已关闭（`MedicationRequestQueryService#listTcmHerbalPrescriptionsByEncounter(encounterId, generateSourceEnum, therapyEnum)`）。代煎费执行编排归属 **AS-IP-NUR-004** 补条款，**非**领域层 Gap。

---

## 4. 接口编排说明书（业务步骤）

### IF-IP-TCM-29 中医 Tab 页初始化

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 已选定住院患者与就诊 |
| **主路径步骤** | 合并只读：中药**用法**术语、**用药频次**术语（与 **IF-IP-DOR-18** 频次同源）；下发长临筛选默认值（如默认 **长期** 或 **全部**，SDD 固化） |
| **关键分支** | 频次列表含「立即」供临嘱使用 |
| **失败分支** | 术语加载失败 → EX-IP-TCM-101 |
| **查询侧说明** | 纯读 |

---

### IF-IP-TCM-30 加载当前就诊中医病证组列表

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 已选定住院就诊 |
| **主路径步骤** | **IMP** 门控 → 查询 `tcm_flag=true` 就诊诊断关联 → 按 **`tcm_syndrome_group`** 分组 → 装配病名（`tcm_dx`）+ 证候（`tcm_syndrome`）及组级主诊/排序 |
| **关键分支** | 仅病或仅证的异常组过滤不展示 |
| **失败分支** | 查询失败 → EX-IP-TCM-101 |
| **查询侧说明** | 纯读 |

---

### IF-IP-TCM-31 分页检索中医病名/证候目录

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 用户打开病证录入并检索 |
| **主路径步骤** | 仅 **ACTIVE** → 按 **`catalogRole`** 收窄 source → 关键字分页 |
| **关键分支** | 未传 `catalogRole` 拒绝或默认病名（SDD 固化） |
| **失败分支** | 检索失败 → EX-IP-TCM-101 |
| **查询侧说明** | 纯读 |

---

### IF-IP-TCM-32 保存中医病证组（全量批次）

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 已选定住院就诊；提交批次非空 |
| **主路径步骤** | **BR-IP-TCM-001** 门控 → 校验每组 **1 病+1 证**、主诊唯一 → upsert 诊断事实（`tcm_flag=true`）→ **`batchUpsertTcmEncounterDiagnosisAssociation`** 全量对齐（**仅** `tcm_flag=true` 子集，**不误删西医**） |
| **关键分支** | 组 ID 空=新建；非空=更新 |
| **失败分支** | EX-IP-TCM-201 / 202 / 105 |
| **查询侧说明** | 写后刷新病证列表 |

---

### IF-IP-TCM-33 删除中医病证组

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 已选定待删 **`tcm_syndrome_group`** |
| **主路径步骤** | 统计组内 `conditionId` 中药医嘱引用 → 大于零拒绝 → 否则删除组内全部诊断与关联 |
| **关键分支** | 组已不存在 → 提示刷新 |
| **失败分支** | EX-IP-TCM-102 / 105 |
| **查询侧说明** | 写后自列表移除 |

---

### IF-IP-TCM-34 加载当前就诊中药处方列表

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 已选定住院就诊；可选 **therapyType**（long/temp/all） |
| **主路径步骤** | 调用 `listTcmHerbalPrescriptionsByEncounter`（医生开立 + `tcm_flag=true`，按 `prescription_no` / `prescriptionGroupKey` 聚合）→ 若传入 **therapyType** 则按 **`therapy_enum`** 筛选 → enrich 目录名、**方头长临**、单位展示名、参考零售价、病证组摘要 |
| **关键分支** | 无处方空态；多 Tab 多方 |
| **失败分支** | EX-IP-TCM-101 |
| **查询侧说明** | 纯读 |

---

### IF-IP-TCM-35 分页检索中药饮片目录

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 用户在编辑卡片检索药品 |
| **主路径步骤** | 分页 **ProductDefinition** → **强制** `categoryCode=中草药` → 与 **IF-IP-DOR-11** 同构装配库存预减/定价；**禁止**当前页同名折叠（**BR-TCM-RX-014**） |
| **关键分支** | 支持 `prepickedProductKeys`；写路径仍绑具体目录 id |
| **失败分支** | EX-IP-TCM-101 |
| **查询侧说明** | 纯读 |

---

### IF-IP-TCM-36 暂存中药处方

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 已选定就诊；方已绑病证组；含 **`prescriptionGroupKey`**、**therapyEnum**、方头/行 |
| **主路径步骤** | **BR-IP-TCM-001** 门控 → 校验病证组 → 饮片目录 → **BR-IP-TCM-005/006** 长临与方头一致性 → **不校验库存** → 每行走中药专用草稿路径（`tcm_flag`、`therapy_enum`、付数、代煎、`content_json`）→ **不**写 `ChargeItem` → 删除标记行移除草稿 req |
| **关键分支** | 组套套用映射新 key；**不写** `prescription_no` |
| **失败分支** | EX-IP-TCM-203 / 209 / 210 / 105 |
| **查询侧说明** | 写后 IF-IP-TCM-34 可刷新 |

---

### IF-IP-TCM-37 签发中药处方

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 待签发行均为中药草稿 |
| **主路径步骤** | 按 **`prescriptionGroupKey` 分组** → 校验 **BR-IP-TCM-005/006** → 每组取/生成 **`prescription_no`** → **`rx_type_code`**（**BR-IP-TCM-007**）→ **不**调用 `splitPrescription` → 逐行 **sign** → 请求 **ACTIVE**；**不**建 Dispense/ChargeItem/代煎费 |
| **关键分支** | 多方可同批签发；同批共享 **signCode** 展示 |
| **失败分支** | EX-IP-TCM-205 / 209 / 210 / 105 |
| **查询侧说明** | 写后展示 `prescription_no` 与长临 |

---

### IF-IP-TCM-38 签退/删除中药处方

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 用户撤回整方或删除方/行 |
| **主路径步骤** | 校验 `tcm_flag=true` → **BR-IP-TCM-009** 执行/发药/费用门控 → **签退**：请求回 **DRAFT**（已校对退回须写退回痕迹，对齐护士退回语义）→ **删除**：移除草稿 req |
| **关键分支** | 删整方=同方全部行；删单行=仅该行（长期已停嘱行另论） |
| **失败分支** | EX-IP-TCM-103 / 207 / 210 |
| **查询侧说明** | 写后 IF-IP-TCM-34 刷新 |

---

### IF-IP-TCM-39 查询患者中医历史处方

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 已选定患者；排除当前就诊 |
| **主路径步骤** | `listTcmHerbalPrescriptionHistoryByPatient` → 按 `prescription_no` 聚合 → 可选 keyword 过滤 |
| **关键分支** | 只读引用；套用走 IF-IP-TCM-36 |
| **失败分支** | EX-IP-TCM-101 |
| **查询侧说明** | 纯读 |

---

### IF-IP-TCM-40 停嘱（长期中药方）

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 用户勾选至少一张**长期**中药方并确认停嘱 |
| **主路径步骤** | **BR-IP-TCM-001** 门控 → 解析 **`prescription_no` 下全部长期 req** → 校验 **LONG_TERM** 且可停 → 批量写结束时间并 **STOPPED**（**不**级联护士执行派生临嘱） |
| **关键分支** | 非长期方拒绝；已 **STOPPED** 跳过或拒绝（SDD 固化） |
| **失败分支** | EX-IP-TCM-211 |
| **查询侧说明** | 写后刷新列表；护士执行列表展示「已停嘱」 |

---

### IF-IP-TCM-41 取消停嘱（长期中药方）

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 用户勾选至少一张**已停嘱**长期中药方 |
| **主路径步骤** | 出院门禁（对齐 **BR-IP-ORD-025**）→ 解析整方 req → 仅 **LONG_TERM** 且 **STOPPED** → 恢复：**`performer_check_id` 非空 → VERIFIED`，否则 → ACTIVE** → 清空结束时间 |
| **关键分支** | 非 STOPPED / 非长期拒绝；已有执行/发药**仍允许**（对齐西医取消停嘱） |
| **失败分支** | EX-IP-TCM-212 / 213 |
| **查询侧说明** | 写后刷新 |

---

### IF-IP-TCM-42 按方改开立时间

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 当前中药方已有落库药味；用户点「改开立时间」并选定新时刻 |
| **主路径步骤** | **BR-IP-TCM-001** 门控 → 按 **`prescription_no` 和/或 `prescriptionGroupKey` 整方扩行** → 方内各味调用领域改开立时间（**OPENED**）→ 统一写入同一 `req_opened_time` |
| **关键分支** | 已签方用处方号；草稿方用方标识；两侧可同时传入去重 |
| **失败分支** | 范围为空 / 无药味行 / 就诊不可写 |
| **查询侧说明** | 写后刷新；护士站折叠展示与各味库内时间一致（已统一） |

---

## 5. 业务异常与用户提示

| 语义代码 | 场景 | 用户提示方向 | 可重试 |
| --- | --- | --- | --- |
| EX-IP-TCM-101 | 列表或目录检索失败 | 缩小条件或稍后重试 | 是 |
| EX-IP-TCM-102 | 病证组已有中药医嘱引用 | 先调整相关处方后再删 | 否 |
| EX-IP-TCM-103 | 处方已执行/已发药，不可撤回或删除 | 需走取消执行/退药流程 | 否 |
| EX-IP-TCM-104 | 未选择患者或就诊 | 请先选择患者 | 是 |
| EX-IP-TCM-105 | 保存或删除持久化失败 | 稍后重试 | 视原因 |
| EX-IP-TCM-201 | 病证组缺一病一证 | 每组须同时选择病名与证候 | 否 |
| EX-IP-TCM-202 | 主诊病证组超过 1 个 | 请只保留一个主诊病证组 | 否 |
| EX-IP-TCM-203 | 中药方未绑定病证组 | 请先维护并绑定中医病证组 | 否 |
| EX-IP-TCM-205 | 签发批次含非中药行 | 请在对应 Tab 签发 | 否 |
| EX-IP-TCM-207 | 当前状态不允许撤回/删除 | 刷新列表后重试 | 视原因 |
| EX-IP-TCM-208 | 临嘱频次非立即（存量） | 后端已不再以此拒绝；文案仅兼容旧提示 | 否 |
| EX-IP-TCM-209 | 同方方头字段不一致 | 请统一该方付数/频次/用法/代煎 | 否 |
| EX-IP-TCM-210 | 长期方频次不能为「立即」 | 请为长期方选择有效用药频次 | 否 |
| EX-IP-TCM-211 | 停嘱：非长期或状态不允许 | 仅长期生效中药方可停嘱 | 否 |
| EX-IP-TCM-212 | 取消停嘱：非 STOPPED 或非长期 | 仅已停嘱的长期方可取消停嘱 | 否 |
| EX-IP-TCM-213 | 取消停嘱：出院门禁 | 存在有效出院/转科申请，不可取消停嘱 | 否 |

---

## 6. 事件语义与消费方

| 事件语义（DM 英文名以 DM 为准） | 触发相位（IF） | 典型消费方 |
| --- | --- | --- |
| `EncounterDiagnosisAssociationUpsertedEvent` 等 | IF-IP-TCM-32 | 就诊读模型、病历 |
| `EncounterDiagnosisAssociationRemovedEvent` 等 | IF-IP-TCM-33 | 同上 |
| `HerbalMedicationOrderDraftCreatedEvent` / `EditedEvent` | IF-IP-TCM-36 | 医嘱列表 QueryService |
| `MedicationOrderSignedEvent` | IF-IP-TCM-37 | **护士校对**（AS-IP-NUR-003）、医嘱列表 |
| `MedicationOrderRevokedEvent` | IF-IP-TCM-38 | 护士列表刷新 |
| `LongTermMedicationOrderStoppedEvent` 等 | IF-IP-TCM-40 | 护士执行列表、停嘱展示 |
| `LongTermMedicationOrderStopCancelledEvent` 等 | IF-IP-TCM-41 | 护士执行/出院门禁读侧 |
| 护士执行产生 Dispense/Charge（含代煎） | **AS-IP-NUR-004** | 汇总领药、住院发药、计费 |

---

## 7. 非功能性需求

| 类别 | 要求 |
| --- | --- |
| **性能（经验目标）** | IF-IP-TCM-30/34 ≤800ms；IF-IP-TCM-32 ≤2000ms；IF-IP-TCM-36 ≤2000ms；IF-IP-TCM-37 ≤1500ms（无 FIFO/挂费） |
| **幂等** | 相同 **`prescriptionGroupKey`** 重复签发复用 `prescription_no` 或返回可理解冲突 |
| **并发** | 双终端同方编辑以后写为准或冲突提示（SDD 固化） |
| **一致性** | **BR-TCM-ISO-001/002** 中西医路径隔离；住院医生站**禁止**预建发放/账单 |
| **审计** | 病证组、签发 signCode、长临、停嘱时刻可追溯 |

---

## 8. 测试要点（业务视角）

| 类别 | 要点 |
| --- | --- |
| **正向** | 长临筛选；长期方多味暂存/签发得同一 `prescription_no`；临嘱 `rateCode=立即`；长期方头一致性校验；签发后**无** ChargeItem/Dispense；护士校对后执行→多 req 多 Dispense 多 Charge；代煎=是时**每次执行**按付数生成代煎费；长期整方停嘱/取消停嘱 |
| **异常** | EX-IP-TCM-210 长期方频次为立即；EX-IP-TCM-209 方头不一致；EX-IP-TCM-103 已执行不可删；EX-IP-TCM-213 出院门禁 |
| **规则** | 中医签发不触发 **BR-ORD-023**；**BR-IP-TCM-003** 医生站无账单；付数×单剂数量 |
| **隔离** | 西医 Tab 签发不含中药；中医 Tab 不含西药 |
| **下游** | 护士中西药 Tab 过滤后中药一方一行；汇总领药/发药分轨 |

---

## 9. 关联文档与变更历史

### 9.1 关联文档

| 文档 |
| --- |
| `health/AppService/AS-IP-DOR-001-住院医生站就诊管理.md` |
| `health/AppService/AS-IP-DOR-002-住院医生站诊断录入.md` |
| `health/AppService/AS-IP-DOR-003-住院医生站医嘱开立.md` |
| `health/AppService/AS-OP-DOR-010-门诊医生站中医.md` |
| `health/AppService/AS-BAS-MST-017-组套模板.md` |
| `health/AppService/AS-IP-NUR-003-住院护士站医嘱校对.md` |
| `health/AppService/AS-IP-NUR-004-住院护士站医嘱执行.md` |
| `health/AppService/AS-IP-NUR-010-住院护士站汇总领药.md` |
| `health/AppService/AS-IP-PHA-001-住院发药.md` |
| `health/AppService/AS-IP-PHA-003-住院退药.md` |
| `docs/领域/DomainModel/DM-MED-003-MedicationRequest-药品申请.md |
| `docs/领域/DomainModel/DM-ADM-002-Encounter-就诊.md |
| `health/需求/001中西医处方开立-分方规则（门诊-住院）.md` |

### 9.2 变更历史

| 版本 | 日期 | 变更摘要 |
| --- | --- | --- |
| v1.7 | 2026-09-09 | **BR-IP-TCM-005**：临时方不再强制立即频次；创建草稿写入方头 `therapyEnum`（领域未传才回落临时）；EX-IP-TCM-208 降为存量文案 |
| v1.6 | 2026-09-07 | **IF-IP-TCM-42 / BR-IP-TCM-011**：按方改开立时间（整方统一 `req_opened_time`）；签发不自动统一；护士站折叠展示最晚见 **AS-IP-NUR-003/004**；对齐 **AS-IP-DOR-006-SDD** |
| v1.5 | 2026-08-01 | **BR-TCM-RX-014** 废止读侧同名合并：IF-IP-TCM-35 选品与西药同构；对齐 **AS-IP-DOR-006-SDD** / 门诊 **AS-OP-DOR-010 v1.12** |
| v1.4 | 2026-07-24 | **BR-TCM-RX-014**：IF-IP-TCM-35 同名合并（与门诊一致）；目录改为下发库存预减行供合并；对齐 **AS-IP-DOR-006-SDD**（已被 v1.5 废止读侧合并） |
| v1.2 | 2026-06-20 | **support 落点对齐**：住院中医应用层落 `inpatient/doctorstation/chinesemedicine`（**无**父级 `doctorstation/support）；详见 **AS-IP-DOR-006-SDD** |
| v1.1 | 2026-06-20 | 关闭 **G-IP-TCM-001**：`listTcmHerbalPrescriptionsByEncounter` 增补可选 `therapyEnum`；`TcmHerbalPrescriptionGroupQueryModel` 增补方头 `therapyEnum` |
| v1.0 | 2026-06-20 | 首版：住院中医 Tab AS；对齐 **AS-OP-DOR-010** 并叠加长临分轨、医生站仅 Request、临嘱立即、方头一致性、整方停嘱、执行时代煎费（**BR-IP-TCM-008**）；IF **IF-IP-TCM-29～41** |

---

**文档负责人**：（待填） · **评审**：（待填）
