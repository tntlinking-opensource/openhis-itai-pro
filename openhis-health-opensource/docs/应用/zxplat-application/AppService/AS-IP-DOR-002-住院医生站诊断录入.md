# 住院医生站 · 诊断录入 · 应用服务设计（AS）

> **文档职责**：说明住院医生站「诊断录入」模块的**接口契约（IF）**、业务语义、涉及聚合与 QueryService、规则（BR）、编排意图；作为上游契约供详细设计与实现对齐。
>
> **本文必须写**：IF 编号；用户动作与读写属性；关键校验与状态变化；Gap；异常口径；事件语义；非功能约束；业务向测试要点。
>
> **本文禁止写**：具体类名/应用方法名；Request/Response 字段明细；包路径；Mapper；伪代码；写库顺序；QueryService 字段组装规则。
>
> **页面边界**：住院医生站「诊断录入」Tab——左侧历史/常用/个人/科室快捷树（仅查询消费）、右侧当前**住院就诊**诊断表、「新增诊断」弹窗（中西医切换、关键字检索）、行上删除；每行可选**患者疾病诊断类型**（住院专用）。**左侧患者列表**见 **AS-IP-DOR-001**；医嘱/病历/常用模板维护等见 §1.4。
>
> **覆盖声明**：完整契约以 **§2 IF-IP-DOR-02～07** 为准；业务能力与 **AS-OP-DOR-002**（门诊诊断）同构，差异仅为QueryService锚点、`IptPatientDiseaseDiagType` 落库与主诊唯一约束。**HTTP/成码细节**待 SDD，本文不展开。
>
> **编码**：AS-IP-DOR-002 · **版本**：v1.3 · **日期**：2026-08-13

---

## 1. 场景概述

### 1.1 目标与边界


| 维度 | 说明 |
| --- | --- |
| **在内** | 载入当前住院就诊已有诊断列表（IF-IP-DOR-02）；四类常用诊断快捷候选（IF-IP-DOR-03）；分页检索诊断定义（IF-IP-DOR-04）；全量批次保存就诊诊断（IF-IP-DOR-05）；删除就诊诊断（IF-IP-DOR-06）；**单点切换主诊**（IF-IP-DOR-07，西医单行 / 中医病证组）。保存时可将**患者疾病诊断类型**写入就诊诊断关联 **`adm_encounter_diagnosis.diag_type_code`**（与 `IptPatientDiseaseDiagType` 对齐） |
| **在外** | 患者列表（**AS-IP-DOR-001**）；医嘱开立、病历、报告；中医辨证扩展批次；诊断常用模板**维护后台**（本 Tab 仅消费查询） |
| **涉及域** | Condition；ConditionDefinition；Encounter / EncounterDiagnosis；ConditionTemplate；Organization / Practitioner |
| **编排权威** | 以本文 **§2、§4** 为准；写侧方法白名单以 **DM-CLN-003**、**DM-ADM-002** §6 为准 |


### 1.2 角色与关键动作


| 角色 | 关键动作 |
| --- | --- |
| **住院医生** | 在 **AS-IP-DOR-001** 选定患者后打开诊断 Tab → 查看/刷新列表与快捷树 → 选择诊断并填写类型（可选）→ 全量提交保存 → **快捷切换主诊**（不必整批保存）→ 必要时删除行 |
| **系统** | 读列表来自 **InpatientEncounterContextQueryModel** 诊断快照；保存/删除后刷新快照；删除前校验医嘱引用；保存批次校验**至多一个主诊断** |


### 1.3 术语与口径约定


| 术语 | 口径 |
| --- | --- |
| **与门诊同构** | IF-IP-DOR-02～07 分别对应 **AS-OP-DOR-002** 的 IF-DOR-06 / 06A / 07 / 08 / 09 / **09A**；编排顺序、全量批次、四类快捷树、中西医检索、删除门控、单点切换主诊**继承**门诊口径 |
| **当前就诊诊断列表** | 绑定在当前**住院**就诊（`class_enum=IMP`）；读 **`InpatientEncounterContextQueryModel 诊断展示字段`** 快照，经装配为行列表（含中西医、验证状态、**患者疾病诊断类型**展示等） |
| **患者疾病诊断类型** | 持久化码存入 **`adm_encounter_diagnosis.diag_type_code`**；枚举 **`IptPatientDiseaseDiagType`**（术语 `ipt_dise_type_code`，如 `31` 入院、`12` 主要、`13` 次要等）；API 字段名 **`iptDiseaseTypeCode`** / 展示 **`iptDiseaseTypeText`**，与门诊医生站 DTO 口径一致 |
| **类型是否必填** | **非必填**：保存时 `iptDiseaseTypeCode` 可空；非空时须为枚举合法 **code**，否则拒绝 |
| **主诊断** | 同一就诊保存批次内 **`mainDiagnosisFlag=1`（主诊断）至多一行**；违反则拒绝（**BR-IP-DOD-009**） |
| **全量替换策略** | 同 **BR-DOD-006**：提交完整批次，按行 ID 区分新增/更新；不在批次中的历史行由 IF-IP-DOR-06 显式删除或实现侧全量差异移除（SDD 固化） |
| **QueryService 能力** | 写前门控 **InpatientEncounterContextQueryModel**（`EncounterContextReader`）；诊断 Tab **`EncounterDiagnosisSnapshotAssembler#buildSnapshot`** → Mapper **`parseDiagnosisJson`**；列表/登记主诊断由 Loader enrichment 的 QueryModel 内部 JSON 经 **`AdmissionPrimaryDiagnosisAssembler`** 解析（**禁止** API 透传） |
| **诊断定义键名** | 统一 **`conditionDefinitionId`**、**`conditionDefinitionName`** |
| **门诊转住院西医** | 申请/直登**原样**落门诊**医保目录**；本站 **IF-IP-DOR-02** `getEncounterDiagnosis` 首次加载时幂等一对多展开为 **`clinical_v2`**（见 **AS-OP-DOR-005** / `ensureWesternInsuranceDiagnosesExpandedToClinical`）；列表展示国临名后由医生删改 |


### 1.4 本期范围与非目标


| 类别 | 内容 |
| --- | --- |
| **本期（契约）** | IF-IP-DOR-02～07：与门诊诊断 Tab 对齐的五类能力 + 住院类型码落库 + **单点切换主诊** |
| **非目标** | 患者列表、接诊、医嘱、病历、报告、中医扩展批次、常用模板 CRUD 维护页 |
| **非目标** | 按诊断名称单独过滤列表、全局联想检索等无 UI 入口能力 |


### 1.5 领域清单（聚合 + 职责 + DM 路径）


| 聚合（域） | 在本场景中的职责（摘要） | DM 路径 |
| --- | --- | --- |
| **Condition** | 诊断事实 CRUD、删除前门控 | `DM-CLN-003-Condition-诊断记录.md` |
| **ConditionDefinition** | 有效定义分页检索 | `DM-CLN-002-ConditionDefinition-诊断定义.md` |
| **Encounter（含 EncounterDiagnosis）** | 就诊诊断关联；`diag_type_code` 写入 | `DM-ADM-002-Encounter-就诊.md` |
| **ConditionTemplate** | 常用树只读 | `DM-CUS-003-ConditionTemplate-诊断常用模板.md` |
| **Organization / Practitioner** | 科室/医师绑定解析 | `DM-ADM-004` / `DM-ADM-005` |


### 1.6 业务规则（BR-*）


| 规则 ID | 描述 | 绑定 IF |
| --- | --- | --- |
| **BR-IP-DOD-001** | 删除前校验该诊断下**无已开立医嘱**；否则拒绝 | IF-IP-DOR-06 |
| **BR-IP-DOD-002** | 诊断定义检索仅**有效（发布）**目录；可按中西医收窄 `source` | IF-IP-DOR-04 |
| **BR-IP-DOD-003** | 四类快捷树按患者/当前医生/当前用户/当前登录科室分别查询 | IF-IP-DOR-03 |
| **BR-IP-DOD-004** | 保存：先落诊断事实，再落就诊诊断关联（含 `diag_type_code`←`iptDiseaseTypeCode`）；按行 ID 区分新增/更新 | IF-IP-DOR-05 |
| **BR-IP-DOD-005** | 列表加载补充中西医类别、验证状态、**患者疾病诊断类型**展示文案 | IF-IP-DOR-02 |
| **BR-IP-DOD-006** | 全量批次语义（同门诊 **BR-DOD-006**） | IF-IP-DOR-05 |
| **BR-IP-DOD-007** | 写操作就诊须为 **IMP**（能加载住院快照即可）；**不要求** `IN_PROGRESS`/`ON_HOLD`——**允许出院后补录/更正诊断**（2026-08-13 修订）；**科室全员可见**，不按主治/转科参与者收窄。**医嘱开立**仍走 **AS-IP-DOR-003** 独立可写态门控（`IN_PROGRESS`/`ON_HOLD`），与本规则无关 | IF-IP-DOR-05、06、07 |
| **BR-IP-DOD-008** | `iptDiseaseTypeCode` **可空**；非空须为 **`IptPatientDiseaseDiagType` 合法 code** | IF-IP-DOR-05 |
| **BR-IP-DOD-009** | 保存批次内**至多一个主诊断**（`mainDiagnosisFlag` 主诊标记） | IF-IP-DOR-05 |
| **BR-IP-DOD-010** | 保存就诊诊断关联时 `med_type` 须落库；`medicalTypeCode` 可空，空则默认 `YbMedType.GENERAL_OUTPATIENT`（`11`）；非空须为 `YbMedType` 合法 code | IF-IP-DOR-05 |
| **BR-IP-DOD-011** | **单点切换主诊**：指定就诊诊断行设为主诊；西医按单行，中医按 `tcm_syndrome_group` 整组；同就诊其余主诊清空（对齐 **BR-ENC-014**）。与 IF-IP-DOR-05 全量保存改主诊**并存**——快捷切换不必整批保存；成功后按需同步入院诊断至病历（实现侧门控） | IF-IP-DOR-07 |


---

## 2. 页面与接口总览（契约概览）


| IF 编号 | 用户动作 | 接口语义（业务） | 读/写属性 | 关键规则（BR） | 关键状态变化（自然语言） | 涉及领域 | 查询服务 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| IF-IP-DOR-02 | 进入诊断 Tab、切换患者 | 加载当前住院就诊已挂接的诊断列表 | 读 | BR-IP-DOD-005 | 无 | **`EncounterDiagnosisSnapshotAssembler`** + Mapper | 结构化诊断行列表 |
| IF-IP-DOR-03 | 进入 Tab、刷新快捷树 | 查询四类常用诊断快捷候选 | 读 | BR-IP-DOD-003 | 无 | Condition / ConditionDefinition / ConditionTemplate | — |
| IF-IP-DOR-04 | 新增诊断弹窗：中西医、关键字、翻页 | 分页检索有效诊断定义（**西医仅 `clinical_v2` 国临**；中医仍 tcm 目录；**不影响门诊** IF-DOR-07） | 读 | BR-IP-DOD-002 | 无 | ConditionDefinition | — |
| IF-IP-DOR-05 | 确认诊断列表并提交 | 全量批次保存诊断事实与就诊诊断关联（含可选 `diag_type_code`） | 写 | BR-IP-DOD-004、006、007、008、009 | 就诊下诊断新增/更新/移除 | Condition / Encounter | — |
| IF-IP-DOR-06 | 行删除 | 删除诊断事实并移除就诊关联 | 写 | BR-IP-DOD-001、007 | 诊断行移除；有医嘱则拒绝 | Condition / Encounter | — |
| IF-IP-DOR-07 | 行上「设为主诊」或等价快捷操作 | 单点切换就诊主诊断（西医单行 / 中医病证组） | 写 | BR-IP-DOD-007、011 | 目标行（或组）为主诊；同就诊其余主诊清空 | Encounter | — |


---

## 3. 缺口跟踪（Gap）

**无待跟踪缺口。**

> 写侧 `saveDoctorDiagnosis`、`batchUpsertEncounterDiagnosisAssociation`、`removeEncounterDiagnosisAssociation`、`delEncounterDiagnosis` 及 **InpatientEncounterContextQueryModel** EncounterQueryService 诊断 JSON 刷新已在 whale 工程落地；应用层住院医生站 **AppService 分包**属 SDD/成码范围，**非 Gap**。

---

## 4. 接口编排说明书（业务步骤）

### IF-IP-DOR-02 加载当前就诊诊断列表


| 维度 | 说明 |
| --- | --- |
| **触发前提** | 已在 **AS-IP-DOR-001** 选定住院就诊与患者 |
| **主路径步骤** | 按 `encounterId` 读取 **InpatientEncounterContextQueryModel** 诊断快照 → 解析为诊断行列表 → 补充中西医类别、验证状态文案、**患者疾病诊断类型**文案（`iptDiseaseTypeCode`→`iptDiseaseTypeText`） |
| **关键分支** | 空列表展示空状态 |
| **失败分支** | EX-IP-DOD-101；未选就诊 EX-IP-DOD-104 |
| **查询侧说明** | 纯读 |


---

### IF-IP-DOR-03 查询四类常用诊断快捷候选


| 维度 | 说明 |
| --- | --- |
| **触发前提** | 已选定住院就诊与患者 |
| **主路径步骤** | 与 **AS-OP-DOR-002** IF-DOR-06A 相同：病人历史（诊断事实聚合）、医生常用、用户个人、科室模板绑定四类并行只读查询 |
| **关键分支** | 任一类可为空 |
| **失败分支** | EX-IP-DOD-101 |
| **查询侧说明** | 纯读 |


---

### IF-IP-DOR-04 分页检索诊断定义


| 维度 | 说明 |
| --- | --- |
| **触发前提** | 医生发起新增诊断 |
| **主路径步骤** | 与 **AS-OP-DOR-002** IF-DOR-07 相同：仅有效目录、关键字、可选中西医 `source` 过滤、分页 |
| **失败分支** | EX-IP-DOD-101 |
| **查询侧说明** | 纯读 |


---

### IF-IP-DOR-05 保存就诊诊断（全量批次）


| 维度 | 说明 |
| --- | --- |
| **触发前提** | 已选定 IMP 就诊；批次非空；住院快照可读（**BR-IP-DOD-007**；**不**要求在院） |
| **主路径步骤** | 校验 **至多一个主诊断**（BR-IP-DOD-009）→ 校验各非空 `iptDiseaseTypeCode` 合法（BR-IP-DOD-008）→ 对批次每行：先 **保存/更新诊断事实**，再 **批量 upsert 就诊诊断关联**（关联项携带 `diag_type_code`，来源 `iptDiseaseTypeCode`）→ 域内拼装 `diagnosisJson` 发布关联类事件 → **InpatientEncounterContextQueryModel** QueryService 刷新 |
| **关键分支** | 类型码为空则关联行 `diag_type_code` 保持空；中医扩展批次分支归 **AS-IP-DOR-006 中医**（非目标） |
| **失败分支** | 主诊断多于一个 → EX-IP-DOD-106；类型码非法 → EX-IP-DOD-107；就诊不可写 → EX-IP-DOD-108；持久化失败 → EX-IP-DOD-105 |
| **查询侧说明** | 保存后左侧 **AS-IP-DOR-001** 列表 `diagnosisSummary` 随 Loader 再查 / 上下文刷新更新 |


---

### IF-IP-DOR-06 删除就诊诊断


| 维度 | 说明 |
| --- | --- |
| **触发前提** | 已选定当前住院就诊及待删诊断 |
| **主路径步骤** | 与 **AS-OP-DOR-002** IF-DOR-09 相同：医嘱引用计数→拒绝；先移除就诊关联，再删除/软删诊断事实；刷新住院诊断快照 |
| **失败分支** | EX-IP-DOD-102；EX-IP-DOD-105；EX-IP-DOD-108 |
| **查询侧说明** | 快照移除该条目 |


---

### IF-IP-DOR-07 切换就诊主诊断


| 维度 | 说明 |
| --- | --- |
| **触发前提** | 已选定住院就诊；目标就诊诊断行已存在；住院快照可读（**BR-IP-DOD-007**） |
| **主路径步骤** | **Gate**：就诊/诊断行标识必填 → **Pre**：住院可见性与患者门控、IMP 类别 → **Cmd**：领域切换主诊（西医单行 / 中医病证组；清空同就诊其余主诊，**BR-IP-DOD-011**）→ 成功后按需同步入院诊断至病历 → **Asm**：返回成功语义 |
| **关键分支** | 目标已是唯一主诊且无需写库时幂等成功；与 IF-IP-DOR-05 全量保存改主诊并存，快捷切换不必整批保存 |
| **失败分支** | 未选就诊/行 → EX-IP-DOD-104；就诊缺失/非 IMP → EX-IP-DOD-108；持久化失败 → EX-IP-DOD-105 |
| **查询侧说明** | 关联类事件后诊断快照再查可见新主诊；左侧列表 `diagnosisSummary` 随 Loader 再查更新 |


---

## 5. 业务异常与用户提示


| 语义代码 | 场景 | 用户提示方向 | 可重试 |
| --- | --- | --- | --- |
| EX-IP-DOD-101 | 列表或目录检索失败 | 缩小条件或稍后重试 | 是 |
| EX-IP-DOD-102 | 诊断已有医嘱引用 | 先处理相关医嘱后再删 | 否 |
| EX-IP-DOD-104 | 未选择患者或就诊 | 请先选择患者 | 是 |
| EX-IP-DOD-105 | 保存、删除或切换主诊失败 | 稍后重试或联系管理员 | 视原因 |
| EX-IP-DOD-106 | 主诊断多于一个 | 请只保留一个主诊断 | 否 |
| EX-IP-DOD-107 | 患者疾病诊断类型码无效 | 请重新选择诊断类型 | 否 |
| EX-IP-DOD-108 | 就诊不存在或非 IMP / 快照不可读 | 请先选择有效住院就诊 | 否 |


---

## 6. 事件语义与消费方


| 事件语义（DM 英文名以 DM 为准） | 触发相位（IF） | 典型消费方 |
| --- | --- | --- |
| `EncounterDiagnosisAssociationUpsertedEvent` | IF-IP-DOR-05、**IF-IP-DOR-07** | **InpatientEncounterContextQueryModel**、病历/质控、医嘱域 |
| `EncounterDiagnosisSavedEvent` | IF-IP-DOR-05 | **InpatientEncounterContextQueryModel** |
| `EncounterDiagnosisAssociationRemovedEvent` | IF-IP-DOR-06 | **InpatientEncounterContextQueryModel** |
| `EncounterDiagnosisDeletedEvent` | IF-IP-DOR-06 | **InpatientEncounterContextQueryModel**、病历 |


---

## 7. 非功能性需求


| 类别 | 要求 |
| --- | --- |
| **性能（经验目标）** | IF-IP-DOR-02 ≤800ms；IF-IP-DOR-03 ≤1200ms；IF-IP-DOR-04 ≤800ms；IF-IP-DOR-05 ≤2000ms；IF-IP-DOR-06 ≤800ms；IF-IP-DOR-07 ≤800ms |
| **幂等** | 重复批次提交依行 ID 与 DB 约束呈现可理解结果 |
| **并发** | 双终端编辑同一就诊诊断以后写为准或冲突提示（SDD 固化） |
| **一致性** | 删除门控与医嘱引用计数同源；快照与列表 `diagnosisSummary` 一致 |
| **审计** | 诊断类型、主诊、记录人/时刻可追溯 |


---

## 8. 测试要点（业务视角）


| 类别 | 要点 |
| --- | --- |
| **正向** | 空列表首次保存；带 `iptDiseaseTypeCode`（如入院/主要）保存后列表回显类型文案；不带类型码亦可保存；**IF-IP-DOR-07 西医单行/中医病证组切换主诊且不必整批保存** |
| **规则** | 批次两个主诊断 → EX-IP-DOD-106；非法类型码 → EX-IP-DOD-107；删除有医嘱 → EX-IP-DOD-102；**BR-IP-DOD-011 与 IF-IP-DOR-05 改主诊并存** |
| **QueryService 刷新** | 保存后 **AS-IP-DOR-001** 列表 `diagnosisSummary` 与 Tab 列表一致 |
| **状态** | 已出院（`DISCHARGED`）/ 待入科（`REGISTERED`）等**非在院**状态仍可保存、删除、切换主诊（**BR-IP-DOD-007** 2026-08-13 修订）；医嘱开立仍受在院门控 |
| **并发** | 双窗口同时保存同一就诊 |


---

## 9. 关联文档与变更历史

### 9.1 关联文档


| 文档 |
| --- |
| `docs/应用/zxplat-application/AppService/AS-OP-DOR-002-门诊医生站诊断录入.md |
| `docs/应用/zxplat-application/AppService/AS-IP-DOR-001-住院医生站就诊管理.md |
| `docs/领域/DomainModel/DM-ADM-002-Encounter-就诊.md` |
| `docs/领域/DomainModel/DM-CLN-002-ConditionDefinition-诊断定义.md` |
| `docs/领域/DomainModel/DM-CLN-003-Condition-诊断记录.md` |
| `docs/领域/DomainModel/DM-CUS-003-ConditionTemplate-诊断常用模板.md` |
| `DM §6.2（DM-ADM-002 §6.2 EncounterQueryService（InpatientEncounterContextQueryModel）` |
| `DM §6.2（DM-ADM-002 §6.2 EncounterQueryService` |


### 9.2 变更历史


| 版本 | 日期 | 变更摘要 |
| --- | --- | --- |
| v1.4 | 2026-10-12 | **IF-IP-DOR-02**：加载诊断前幂等展开门诊医保西医→国临；申请/登记阶段不再展开（对齐 **AS-OP-DOR-005** v1.10） |
| v1.3 | 2026-08-13 | **BR-IP-DOD-007 修订（诊断后补）**：就诊存在即可写诊断，**不再**要求 `IN_PROGRESS`/`ON_HOLD`；EX-IP-DOD-108 收窄为就诊缺失/非 IMP；医嘱可写态门控不变 |
| v1.2 | 2026-07-26 | 新增 **IF-IP-DOR-07** 单点切换主诊；**BR-IP-DOD-011**（西医单行/中医病证组，与 IF-IP-DOR-05 并存；成功后按需同步入院诊断至病历） |
| v1.1 | 2026-05-28 | 读模型废止后对齐 zxplat 实现（EncounterContextFacade / querysupport Loader）。 |后对齐 zxplat 实现（EncounterContextFacade / querysupport Loader）。 |（已迁至 EncounterContextReader / application.shared.encounter）
| v1.0 | 2026-05-20 | 初版：IF-IP-DOR-02～06 对齐门诊五接口；住院 `diag_type_code`/`IptPatientDiseaseDiagType`；类型非必填；主诊唯一；读 InpatientEncounterContextQueryModel 快照 |


---

**文档负责人**：（待填） · **评审**：（待填）
