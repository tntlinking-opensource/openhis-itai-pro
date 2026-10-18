# 门诊医生站 · 住院申请 · 应用服务设计（AS）

> **文档职责**：说明门诊医生站「办理住院 / 住院申请」的**接口契约（IF）**、业务语义、涉及聚合与 QueryService、规则（BR）、编排意图；作为上游契约供详细设计与实现对齐。
>
> **本文必须写**：IF 编号；用户动作与读写属性；关键校验与状态变化；Gap；异常口径；事件语义；非功能约束；业务向测试要点。
>
> **本文禁止写**：具体类名/应用方法名；Request/Response 字段明细；包路径；Mapper；伪代码；写库顺序；QueryService 字段组装规则。
>
> **页面边界**：门诊医生站工具栏「办理住院」——在选定当前门诊就诊与患者后，提交住院申请要素；下游由住院收费登记（**AS-IP-FIN-001**）接续。
>
> **覆盖声明**：完整契约以 **§2 IF-IP-REQ-01～02** 为准；与门诊医生开住院申请业务能力对齐。
>
> **编码**：AS-OP-DOR-005 · **版本**：v1.12 · **日期**：2026-10-14

---

## 1. 场景概述

### 1.1 目标与边界

| 维度 | 说明 |
| --- | --- |
| **在内** | 由当前 **门诊** 就诊发起住院申请；**新增一条** `class_enum=IMP` 的住院就诊（**不修改**原门诊就诊）；通过 `amb_encounter_id` 关联源就诊；为住院就诊写入参与者、入院诊断关联、可选病区等；分配**与门诊不同的**住院业务号（`bus_no` 前缀） |
| **在外** | 住院处登记确认/改写预交金并开户收款、费用性质选择（**AS-IP-FIN-001**）；预交金专页续收/退款（**AS-IP-FIN-002**）；申请后修改或撤销（撤销仅住院处取消登记）；医保；患者建档（无患者则须先有就诊上下文） |
| **涉及域** | Encounter（门诊就诊校验 + 住院就诊创建）；Condition / EncounterDiagnosis（入院诊断）；Organization / Location（意向科室、病区）；Practitioner（申请医生参与者） |
| **编排权威** | 以本文 **§2、§4** 为准 |

### 1.2 角色与关键动作

| 角色 | 关键动作 |
| --- | --- |
| **门诊医生** | 在医生站选中当前就诊 → 办理住院 → 确认申请要素 → 提交 |
| **系统** | 防重复申请；生成住院业务号；落 **`IMP + PLANNED`**（产品语：预入院/待住院处办理） |

### 1.3 术语与口径约定

| 术语 | 口径 |
| --- | --- |
| **待登记（产品语）** | 医生提交申请后，写模型 **`IMP + PLANNED`**（**仅**本服务产生 `PLANNED`）；本期**不使用** `PRE_ADMISSION` |
| **待住院处办理** | 与 **`PLANNED`** 同义，等待 **AS-IP-FIN-001** 登记为 **`REGISTERED`** |
| **登记侧诊断** | 住院登记弹窗**只读展示**本住院线已落诊断（`InpatientEncounterContextQueryModel 诊断展示字段`）；**不得**在登记提交时修改（见 **AS-IP-FIN-001** **BR-IP-REG-007**） |
| **下游登记** | 住院登记后 **`REGISTERED`（待入科）**；护士分床后 **`IN_PROGRESS`**（见 **`DM-ADM-002` §2.3.1**） |
| **门诊转住院** | 入院途径默认写入 **2（门诊）**（与医保 adm_way / 登记 `in_way_code` 同口径）；入院类型另字段 |
| **双就诊模型** | 门诊就诊（`AMB`）与住院就诊（`IMP`）为**两条独立 Encounter 主档**；申请动作**只 INSERT 住院线**，源门诊就诊状态与主档**不变** |
| **住院业务号** | 住院 `bus_no`（住院号）须与门诊 `bus_no`（门诊号/就诊号）**前缀不同**、各自流水；禁止与门诊共用同一 `AggregateType` 序号前缀 |
| **诊断（产品/UI）** | 表单先选**西医/中医**（**默认西医**）；**中医**须**一病一症**；**西医**单条；可预填门诊主诊（中医预填须分别回显疾病/证候）；**诊断描述**可选，用户未填时不回填目录名、提交不传 `description` |
| **诊断（后台）** | 提交时以表单携带的入院诊断为准，**一律为住院就诊新建**就诊诊断关联；中医走 `batchUpsertTcmEncounterDiagnosisAssociation`、西医走 `upsertEncounterDiagnosisAssociation`；**申请/登记阶段按提交目录原样落库**（门诊医保名保留，供登记只读展示）；**门诊西医医保目录**若已绑国临，延后至**住院医生站** `getEncounterDiagnosis` 由 `EncounterInpatientAdmissionOrchestrator#ensureWesternInsuranceDiagnosesExpandedToClinical` **一对多展开**为全部 `clinical_v2`（原稿主诊落第一条，其余非主诊，医生站可删改）；`diag_type_code` 固定**入院诊断**（`31`，**BR-ENC-021**），Request **不传**诊断类型 |
| **实现落点** | 独立包 **`outpatient/doctorstation/inpatientadmission`**；HTTP **`doctorStationInpatientAdmission`**（与 **AS-OP-DOR-001** `encountermanage` 分离） |
| **QueryService 能力** | 门诊侧 OutpatientEncounterContextQueryModel；住院登记列表由 InpatientEncounterContextQueryModel 消费申请事件 |
| **DM 路径** | `docs/领域/DomainModel/` 下对应文件 |

### 1.4 本期范围与非目标

| 类别 | 内容 |
| --- | --- |
| **本期（契约）** | IF-IP-REQ-01～02 |
| **本期（UI 接通）** | 医生站「办理住院」：主诊区默认可视门诊主诊，可改选新开；提交走 **`doctorStationInpatientAdmission`** |
| **非目标** | 申请后修改申请内容；医生撤销申请；医生侧填写支付渠道/开户/settle；住院登记窗口任何写操作；预交金专页操作（**AS-IP-FIN-002**） |
| **本期扩（意向预交金）** | 医生可选填预交金**总额**；申请成功后对新建住院线落 `HOSPITAL_DEPOSIT` **DRAFT**（无支付明细、不开户、不 settle）；取消见 **AS-IP-FIN-001** 作废该草稿 |
| **本期扩（费用性质意向）** | 医生选择 **医保 / 自费**（`chargeIntent`）；写入新建住院线 `charge_intent`；登记窗口（**AS-IP-FIN-001**）未开户时据此预填费用性质（医保默认市医保）；**不**建 Account、**不**触发医保入院；提交真源仍为登记 `contractId` |
| **关联** | 就诊列表、接诊、暂离、完诊见 **AS-OP-DOR-001**（`encountermanage`）；**住院申请**见本文与 **AS-OP-DOR-005-SDD**（`inpatientadmission`） |

### 1.5 领域清单（聚合 + 职责 + DM 路径）

| 聚合（域） | 在本场景中的职责（摘要） | DM 路径 |
| --- | --- | --- |
| **Encounter** | 校验源就诊；创建 `IMP` 住院就诊、`ambEncounterId` 关联 | `DM-ADM-002-Encounter-就诊.md` |
| **Condition** | 入院诊断事实 | `DM-CLN-003-Condition-诊断记录.md` |
| **EncounterDiagnosis** | 住院主诊断关联 | `DM-ADM-002-Encounter-就诊.md` |
| **EncounterParticipant** | 申请医生（接诊医生角色语义） | `DM-ADM-002-Encounter-就诊.md` |
| **Organization / Location** | 意向住院科室、可选病区 | `DM-ADM-004` / `DM-ADM-003` |
| **PaymentReconciliation** | 可选意向预交金：仅落 `HOSPITAL_DEPOSIT` **DRAFT**（总额、无渠道明细） | `DM-FIN-002-PaymentReconciliation-支付流水.md` |

### 1.6 业务规则（BR-*）

| 规则 ID | 描述 | 绑定 IF |
| --- | --- | --- |
| **BR-IP-REQ-001** | 源就诊须为进行中的 **门诊** 就诊（`AMB` 且非终态） |
| **BR-IP-REQ-002** | 同一 `ambEncounterId` 下，不得存在另一条未取消的住院申请/在院入口线（**`PLANNED` 或后续非取消态**）；违反则拒绝 |
| **BR-IP-REQ-003** | **新增**住院就诊 **`IMP + PLANNED`**，分配**住院专用前缀**的 `bus_no`；`amb_encounter_id` 指向源门诊就诊 |
| **BR-IP-REQ-004** | 须为**住院就诊**登记申请医生参与者；须为**住院就诊**建立入院主诊断关联（新建子表数据；`diag_type_code` 固定入院诊断 **`31`**，见 **BR-ENC-021**）；门诊西医医保诊断**申请阶段原样落库**，按国临绑码的**一对多展开**延后至住院医生站（**AS-IP-DOR-002** IF-IP-DOR-02） |
| **BR-IP-REQ-005** | 表单提交的入院诊断为**新病情事实**时，须触发 **`AdmissionDiagnosisCreatedEvent`**；仅建立关联且复用已有 `condition_id` 时不发该事件 |
| **BR-IP-REQ-009** | **禁止**将住院申请实现为更新门诊 Encounter；**禁止**住院与门诊共用同一 `bus_no` 发号前缀 |
| **BR-IP-REQ-006** | 入院途径/类型随门诊转住院场景写入（途径默认 **2=门诊**；类型与途径分列） |
| **BR-IP-REQ-007** | 申请提交后**不可修改**；**不可**由医生撤销（取消见住院处 **AS-IP-FIN-001**） |
| **BR-IP-REQ-008** | 本期不调用预入院（`PRE_ADMISSION`）状态迁移 |
| **BR-IP-REQ-010** | 医生可选填预交金**总额**（`advanceDepositAmount`）；**> 0** 时对新建住院线 `collectAdvanceDeposit` 落 `HOSPITAL_DEPOSIT` **DRAFT**，`payment_enum=PAY`，**无支付明细**；**禁止**开户、**禁止** `settle`、**禁止**调账；未填或 ≤0 不落草稿 | IF-IP-REQ-01 |
| **BR-IP-REQ-011** | 医生**不**填写支付渠道；渠道与多笔明细仅在住院登记确认时由收费员填写（**AS-IP-FIN-001**）；意向草稿**不**出现在预交金专页（**AS-IP-FIN-002**，`PLANNED` 不可见且流水不展示 DRAFT） | IF-IP-REQ-01 |
| **BR-IP-REQ-012** | 医生可选/应选费用性质意向（`chargeIntent`：`INSURANCE`=医保 / `SELF_PAY`=自费）；写入新建住院线 `charge_intent`；供 **AS-IP-FIN-001** 未开户时预填费用性质（医保默认市医保）；非法 code 拒绝；**禁止**据此开立 Account 或调用医保入院；提交真源以登记 `contractId` 为准 | IF-IP-REQ-01 |

---

## 2. 页面与接口总览（契约概览）

| IF 编号 | 用户动作 | 接口语义（业务） | 读/写属性 | 关键规则（BR） | 关键状态变化（自然语言） | 涉及领域 | 查询服务 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| IF-IP-REQ-01 | 办理住院并提交申请 | 门诊医生发起住院申请（费用性质意向 + 可选意向预交金总额） | 写 | BR-IP-REQ-001～012 | 新建 **`IMP + PLANNED`**（可写 `charge_intent`）；可选 `HOSPITAL_DEPOSIT` DRAFT | Encounter / Condition / PaymentReconciliation | OutpatientEncounterContextQueryModel；InpatientEncounterContextQueryModel（下游） |
| IF-IP-REQ-02 | （可选）接诊前/办理住院前查询是否已申请 | 按门诊就诊标识查询是否已存在关联住院线 | 读 | BR-IP-REQ-002 | 无 | Encounter | — |

---

## 3. 缺口跟踪（Gap）

**无待跟踪缺口。**

---

## 4. 接口编排说明书（业务步骤）

### IF-IP-REQ-01 发起住院申请

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 已选定当前门诊就诊与患者；表单含意向住院科室、医保类别（存储层字段可保留，本期不编排医保交互）、病情优先级、**主诊断区（展示门诊主诊，可改选）**、可选病区、**可选预交金总额**等 |
| **主路径步骤** | 校验源门诊就诊 → 防重（同 `amb_encounter_id` 未取消住院线）→ **INSERT** 新住院 Encounter（`IMP+PLANNED`、新 `bus_no`、写 `amb_encounter_id`）→ 住院参与者 → 按表单为**该住院 encounter_id** 新建诊断关联（及按需新建 Condition）→ 可选意向病区 → **若预交金总额 > 0**：`collectAdvanceDeposit` 落意向 DRAFT（无明细、不开户、不 settle） |
| **关键分支** | 已存在关联住院则拒绝；未填或预交金 ≤0 跳过意向草稿 |
| **失败分支** | 防重失败 → EX-IP-REQ-201；源就诊不可用 → EX-IP-REQ-202；持久化失败 → EX-IP-REQ-203 |
| **查询侧说明** | 住院登记待办列表新增待登记行；门诊信息读侧可展示已申请语义（以实现为准） |

---

### IF-IP-REQ-02 是否已存在住院申请

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 医生站需要提示「已办理住院」或禁用重复按钮 |
| **主路径步骤** | 按门诊就诊标识查询是否已有关联住院就诊 → 返回布尔或简要状态供 UI 展示 |
| **失败分支** | 查询失败 → EX-IP-REQ-101 |
| **查询侧说明** | 无 |

---

## 5. 业务异常与用户提示

| 语义代码 | 场景 | 用户提示方向 | 可重试 |
| --- | --- | --- | --- |
| EX-IP-REQ-101 | 查询是否已申请失败 | 稍后重试 | 是 |
| EX-IP-REQ-201 | 同一门诊就诊重复申请 | 已存在住院申请，请到住院处登记 | 否 |
| EX-IP-REQ-202 | 源就诊不存在或不可转住院 | 确认就诊仍有效 | 视原因 |
| EX-IP-REQ-203 | 申请提交失败 | 稍后重试 | 视原因 |
| EX-IP-REQ-301 | 未选患者或就诊 | 请先选择患者 | 是 |

---

## 6. 事件语义与消费方

| 事件语义（DM 英文名以 DM 为准） | 触发相位（IF） | 典型消费方 |
| --- | --- | --- |
| 住院申请已发起 / InpatientAdmissionRequestedEvent | IF-IP-REQ-01 | InpatientEncounterContextQueryModel（含 `ward_id`←`event.wardId`）；**AS-IP-FIN-001** 待登记列表 `wardId`/`wardName` |
| 入院诊断已创建 / AdmissionDiagnosisCreatedEvent | IF-IP-REQ-01（新建诊断时） | InpatientEncounterContextQueryModel；质控 |
| 就诊诊断关联已维护 / EncounterDiagnosisAssociationUpsertedEvent | IF-IP-REQ-01 | 病历、医嘱 |

---

## 7. 非功能性需求

| 类别 | 要求 |
| --- | --- |
| **性能（经验目标）** | 申请提交 ≤2000ms |
| **幂等** | 短时重复提交应返回已存在申请语义，不重复创建 |
| **并发** | 双窗口同时对同一门诊就诊提交申请时，仅一条成功 |
| **一致性** | 住院就诊、诊断、参与者、位置同一事务提交 |
| **审计** | 申请医生、申请时间、源门诊就诊标识可追溯 |

---

## 8. 测试要点（业务视角）

| 类别 | 要点 |
| --- | --- |
| **正向** | 门诊进行中就诊可申请；申请后住院处可见待登记；UI 预填主诊时住院线仍新建诊断关联 |
| **异常** | 重复申请；无当前就诊；源就诊已完诊 |
| **规则** | BR-IP-REQ-002 防重；BR-IP-REQ-007 医生不可撤 |
| **联动** | 申请后 **AS-IP-FIN-001** 登记为 `REGISTERED`；取消后释放可再申请 |

---

## 9. 关联文档与变更历史

### 9.1 关联文档

| 文档 |
| --- |
| `docs/领域/DomainModel/DM-ADM-002-Encounter-就诊.md` |
| `docs/领域/DomainModel/DM-CLN-003-Condition-诊断记录.md` |
| `docs/领域/DomainModel/DM-ADM-004-Organization-机构科室.md` |
| `docs/领域/DomainModel/DM-ADM-003-Location-位置.md` |
| `DM §6.2（DM-ADM-002 §6.2 EncounterQueryService（OutpatientEncounterContextQueryModel）` |
| `DM §6.2（DM-ADM-002 §6.2 EncounterQueryService（InpatientEncounterContextQueryModel）` |
| `docs/应用/zxplat-application/AppService/AS-OP-DOR-001-门诊医生站就诊管理.md |
| `docs/应用/zxplat-application/AppService/AS-IP-FIN-001-住院登记.md |
| `docs/应用/zxplat-application/AppService-SDD/AS-OP-DOR-005-门诊医生站住院申请-SDD.md`（**v1.8**，2026-07-08） |

### 9.2 变更历史

| 版本 | 日期 | 变更摘要 |
| --- | --- | --- |
| v1.12 | 2026-10-14 | **BR-IP-REQ-012**：`chargeIntent` 供登记窗口预填费用性质（医保默认市医保）；对齐 **AS-IP-FIN-001 v1.24** / **BR-IP-REG-024** |
| v1.11 | 2026-10-14 | **BR-IP-REQ-012**：入院办理意向 `chargeIntent`（医保办/自费办）写入住院线，仅提示款台；对齐 **DM-ADM-002 v1.56** / **AS-IP-FIN-001** 回显 |
| v1.10 | 2026-10-12 | **西医国临展开时机**：申请/登记不再一对多展开；保留门诊医保目录落库；展开改由住院医生站 IF-IP-DOR-02 幂等触发（**BR-IP-REQ-004** 修订） |
| v1.6 | 2026-05-28 | 六维对账：`DoctorStationInpatientAdmissionAppService` 两方法与 SDD §3.1、门控与 `EncounterDomainService#requestInpatientAdmission` 对齐 |
| v1.9 | 2026-07-08 | **中医预填回显**与**诊断描述**口径：预填分别回填疾病/证候；`description` 用户未填时不传 |
| v1.8 | 2026-07-08 | **入院诊断中西医**：办理住院 UI 默认西医、中医一病一症；`admissionDiagnoses` 支持 `tcmFlag`/`tcmSyndromeGroup` |
| v1.7 | 2026-05-20 | 对齐 **DM-ADM-002 v1.21** / **BR-ENC-021**：入院诊断关联 `diag_type_code` 由领域 `saveAdmissionDx` 固定为 `31`（入院诊断）；应用层不传诊断类型 |
| v1.6 | 2026-05-20 | 事件载荷 `wardId`（申请 Request **`wardId`**）→ QueryService **InpatientEncounterContextQueryModel** §4.2 待登记阶段投影（对齐 **DM-ADM-002** v1.19） |

---

**文档负责人**：（待填） · **评审**：（待填）
