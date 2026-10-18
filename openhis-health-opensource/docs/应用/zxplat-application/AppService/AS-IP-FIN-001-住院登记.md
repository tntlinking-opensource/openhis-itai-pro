# 住院登记 · 应用服务设计（AS）

> **文档职责**：说明住院收费「住院登记」场景的**接口契约（IF）**、业务语义、涉及聚合与 QueryService、规则（BR）、编排意图；作为上游契约供详细设计与实现对齐。
>
> **本文必须写**：IF 编号；用户动作与读写属性；关键校验与状态变化；Gap；异常口径；事件语义；非功能约束；业务向测试要点。
>
> **本文禁止写**：具体类名/应用方法名；Request/Response 字段明细；包路径；Mapper；伪代码；写库顺序；QueryService 字段组装规则。
>
> **页面边界**：住院收费住院登记——**同一列表**按登记状态筛选（**默认待登记**，非 Tab 分页）→ 工具栏「无档登记」/ 行内「登记」「查看」「转医保」「取消」→ 登记弹窗（基础信息、住院信息、**可选填写预交金金额**）→ 提交登记 / 自费转医保 / 取消登记。**预交金续收、退款、单独维护**不在本页（**AS-IP-FIN-002**）。
>
> **覆盖声明**：完整契约以 **§2 IF-IP-REG-00～08** 为准；与住院登记业务能力对齐，并按当前 UI 可执行动作组织接口。
>
> **编码**：AS-IP-FIN-001 · **版本**：v1.25 · **日期**：2026-10-15

---

## 1. 场景概述

### 1.1 目标与边界

| 维度 | 说明 |
| --- | --- |
| **在内** | **本期全做** IF-IP-REG-01～08：列表按状态筛选（默认待登记）；**患者分页检索（登记下拉）**；无档登记入口；有申请/无申请登记；登记弹窗加载与提交；可选预交金（**回显医生意向 DRAFT 总额，收费员可改；>0 则当场开户并以多渠道明细 settle**；取消时作废未 settle 的意向 DRAFT）；取消登记；患者分流/建档/更新；**自费转医保**（读卡补录、2401、未结费用切医保户）；**查看窗口更正入院科室**（已 2401 时发 2403） |
| **在外** | **预交金续收、退款及预交金专页维护**（**AS-IP-FIN-002**，不在住院登记页）；入科分床（**AS-IP-NUR-002**）；Episode 住院全程；电子票据与第三方支付协议细则 |
| **涉及域** | Encounter（住院登记与取消）；Patient（检索/建档/更新）；Condition / EncounterDiagnosis（入院诊断）；Organization / Location（科室、病区、床位余量）；Contract（费用性质）；Account / PaymentReconciliation（仅当登记提交携带预交金金额时） |
| **编排权威** | 以本文 **§2、§4** 为准 |
| **上游** | 门诊医生住院申请见 **AS-OP-DOR-005**（产出 **`IMP + PLANNED`**） |

### 1.2 角色与关键动作

| 角色 | 关键动作 |
| --- | --- |
| **住院收费员** | 查询待登记/待入科等列表 → 无档登记或选中行登记 → 补全/修改科室与病区等（**有申请时入院诊断只读**）→ 选择费用性质 →（可选）填写预交金 → 提交登记 → 必要时取消登记；**自费已登记/在院**时读卡转医保 |
| **系统** | 推进住院就诊至在院执行前态；按金额决定是否当场开户收款；取消时校验账户余额 |

### 1.3 术语与口径约定

| 术语 | 口径 |
| --- | --- |
| **待登记（产品语）** | 医生已提交住院申请，写模型 **`IMP + PLANNED`**（**仅**医生申请产生，见 **AS-OP-DOR-005**）；本期**不使用** `PRE_ADMISSION` |
| **待入科（产品语）** | 住院处已完成登记，写模型 **`IMP + REGISTERED`**（有申请：`PLANNED→REGISTERED`；无申请直登：INSERT 即为 `REGISTERED`，**不经** `PLANNED`）；**尚未**护士分床 |
| **已入科（产品语）** | 写模型 **`IMP + IN_PROGRESS`**（护士分床后，**AS-IP-NUR-002**）；**不在本文** |
| **列表交互** | **非** Tab；**同一列表**按登记状态筛选（待登记 / 待入科 / 已入科），**默认待登记**；读侧映射见 §4 IF-IP-REG-01 |
| **先登记、后收费** | 登记 IF 必须先完成住院登记领域事实；预交金为登记提交后的可选子步骤（同请求可携带金额与多渠道明细，编排上登记成功后再收款） |
| **开户时机** | **登记完成时**幂等开立：人人个人现金户（`contract_no`=自费，供预交金余额）；省/市医保或工伤另开 `MEDICAL_INSURANCE`（`contract_no`=入院费用性质，**费用挂账真源**）。**仅当**预交金金额 **> 0** 时在个人现金户上收住院存款；未填或 ≤0 **不**另收款（续收走 **AS-IP-FIN-002**） |
| **意向预交金草稿** | 门诊转住院（**AS-OP-DOR-005**）可已落 `HOSPITAL_DEPOSIT` **DRAFT**（仅总额）；IF-IP-REG-02 **回显**该金额；收费员可改金额并以多条 `paymentDetails` 为准；确认时 settle→SUCCESS；取消 `PLANNED`/`REGISTERED` 前**软删**未 settle 的意向 DRAFT（及历史 CANCELLED） |
| **医生费用性质意向** | 门诊转住院可写 `charge_intent`（医保/自费）；IF-IP-REG-02 **未开户时预填「费用性质」下拉**（医保→默认市医保，自费→自费合同）；**不**单独展示「医生费用性质」；收费员可改选；**不**据此自动开户或医保入院；提交真源仍为 `contractId` |
| **IMP 状态语义** | `REGISTERED`/`IN_PROGRESS` 等住院阶段解释**仅在 `class_enum=IMP` 成立**；完整状态机见 **`DM-ADM-002` §2.3.1** |
| **QueryService 能力** | 列表以 `InpatientEncounterContextQueryModel` 为主；`status_enum` 与 Encounter 主档一致（待入科=`REGISTERED`，已入科=`IN_PROGRESS`） |
| **状态展示文案** | 列表/弹窗 `statusEnumText` 为 **IMP 产品阶段文案**（待登记/待入科/已入科等），见 **`DM-ADM-002` §2.3.2**；**禁止**使用 `EncounterStatus` 门诊 `display` |
| **患者选择（产品）** | 与门诊挂号一致：登记弹窗经 **IF-IP-REG-06** 分页检索患者并选中；亦可手填身份证等信息**新建患者**；提交时 **IF-IP-REG-03** 按 **BR-IP-REG-004** 分流建档 |
| **入院诊断（有申请）** | 申请阶段（**AS-OP-DOR-005**）已在**住院线**落主诊断（西医为**门诊医保目录原样**，尚未展开国临）；登记弹窗 **IF-IP-REG-02** **只读展示主诊断定义 ID + 名称**（**`AdmissionPrimaryDiagnosisAssembler#resolvePrimaryDiagnosisForRegistration`**：西医国临历史行回落**医保名**；中医一病一症合并为「病名（证候）」；不向 UI 暴露 JSON），**不得**在登记提交时新增或覆盖；**诊断描述**不在只读区回显目录名；国临一对多展开延后至住院医生站（**AS-IP-DOR-002**） |
| **入院诊断（直登）** | 无医生申请时，登记弹窗由收费员先选**西医/中医**（**默认西医**）；**中医**须**一病一症**两条提交（`tcmFlag=true`，组号后端分配）；**西医**单条；**诊断描述**可选，用户未填时 Request **不传** `description`；仅在 **IF-IP-REG-03** 提交时落库一次；`diag_type_code` 由领域 `saveAdmissionDx` 固定为入院诊断（`31`，**BR-ENC-021**） |
| **列表行操作** | **`PLANNED`（待登记）**：「登记」「取消」；**`REGISTERED`（待入科）**：「查看」「取消」；**`IN_PROGRESS`（已入科）**：「查看」；满足 **BR-IP-REG-019** 时额外展示「转医保」（列表或查看弹窗底部） |
| **登记弹窗只读（查看）** | IF-IP-REG-02 在 **`REGISTERED` 或 `IN_PROGRESS`** 时 `readOnly=true`；与「登记」共用读路径，关闭再次提交登记。查看窗口可另开 **科室变更**（**IF-IP-REG-08**），不把整张登记表单放开编辑（见 **BR-IP-REG-016**、**BR-IP-REG-023**） |
| **门诊医生（登记弹窗）** | 存在 `ambEncounterId`（门诊转住院）时，IF-IP-REG-02 **只读带回**源门诊就诊活动 **ADMITTER** 姓名；**禁止**手填或登记提交改写（见 **BR-IP-REG-017**） |
| **病区空床与入科床位（UI）** | 选入院病区后，**住院信息**区标题旁展示 IF-IP-REG-05 空床/总床摘要；**禁止**表单内重复「床位数」字段；护士分床后（`bedId` 有值）只读展示「入科床位」（`wardName` / `bedName`，见 **BR-IP-REG-018**） |
| **入院途径（`in_way_code`）** | UI 标签「入院途径」；与医保 **adm_way** / 病案 **adm_way_code** 同字典：**1** 急诊、**2** 门诊、**3** 其他医疗机构转入、**9** 其他；**门诊转住院**默认 **2**。**勿与** `admit_source_code`（入院类型，如 **6**=常规）混淆 |
| **DM 路径** | `docs/领域/DomainModel/` 下对应文件 |

### 1.4 本期范围与非目标

| 类别 | 内容 |
| --- | --- |
| **本期（契约）** | **全量** IF-IP-REG-01～08；费用性质含自费/合同/医保（登记、转医保、入院科室更正分路径） |
| **本期（UI 接通）** | 列表按状态筛选（默认待登记）、**无档登记**按钮、行登记/查看/取消、**行转医保**、登记弹窗内可选预交金金额 |
| **非目标** | **住院登记页上的预交金续收/退款/专户维护**；护士站分床；医生发起住院申请（**AS-OP-DOR-005**）；独立「更新患者」若 UI 无单独保存钮则并入登记提交；按收费项目部分转自费 |
| **非目标（舍弃）** | 按旧住院专用状态枚举展示的接口（`getEncounterZyStatus` 等）——统一改用 **`EncounterClass` + `EncounterStatus`** 口径 |

### 1.5 领域清单（聚合 + 职责 + DM 路径）

| 聚合（域） | 在本场景中的职责（摘要） | DM 路径 |
| --- | --- | --- |
| **Encounter** | 住院登记完成、取消；病区位置；登记员与**入院途径**（`in_way_code`）/ **入院类型**（`admit_source_code`）/时间 | `DM-ADM-002-Encounter-就诊.md` |
| **Patient** | 身份证复用/新建/更新主档 | `DM-ADM-001-Patient-患者.md` |
| **Condition** | 入院诊断事实；新建时发布入院诊断已创建事件 | `DM-CLN-003-Condition-诊断记录.md` |
| **EncounterDiagnosis** | 住院就诊与诊断关联 | `DM-ADM-002-Encounter-就诊.md` |
| **Organization / Location** | 住院科室、病区列表、床位余量查询 | `DM-ADM-004-Organization-机构科室.md` / `DM-ADM-003-Location-位置.md` |
| **Contract** | 费用性质（自费/合同） | `DM-FIN-001-Contract-费用性质.md` |
| **Account / PaymentReconciliation** | 可选预交金：开户与住院存款支付 | `DM-ADM-009-Account-就诊账户.md` / `DM-FIN-002-PaymentReconciliation-支付流水.md` |

### 1.6 业务规则（BR-*）

| 规则 ID | 描述 | 绑定 IF |
| --- | --- | --- |
| **BR-IP-REG-001** | **先登记、后收费**：须先完成住院登记（`IMP + REGISTERED`），再在**同一用例内**处理可选预交金；不得在未登记时单独收款；医生意向 DRAFT 仅在登记确认后 settle |
| **BR-IP-REG-002** | 登记提交时**须**幂等开立个人现金户（余额 0、`contract_no`=自费）；医保/工伤入院另开 `MEDICAL_INSURANCE`（`contract_no`=所选费用性质）。**仅当**预交金金额 **> 0** 时在个人现金户收款（多渠道明细合计=总额）；未传或 ≤0 **不**收款，若存在意向 DRAFT 则**软删**。费用挂账见 **AS-IP-NUR-001 BR-IP-NUR-018** / **AS-IP-FIN-003 BR-IP-STL-019** |
| **BR-IP-REG-003** | 对已有医生申请（`IMP + PLANNED`）登记时，住院处**可修改**意向科室、病区等申请要素后再登记；**入院诊断不在登记侧维护**（见 **BR-IP-REG-007**） |
| **BR-IP-REG-004** | 无患者档案时，按身份证命中则复用患者，未命中则新建后再登记（口径对齐门诊挂号患者分流）；主档 `id_card` 允许为空，**禁止**按年龄合成虚假身份证号 |
| **BR-IP-REG-020** | **患者年龄必填（前端产品）**：办理入院基本信息年龄必填；填身份证或出生日期即时回显年龄，填年龄（且无证）即时回显出生日期；二者双向联动。产品默认身份证号必填（填证即带出生日+年龄） | IF-IP-REG-03 |
| **BR-IP-REG-025** | **入院人口学采集（产品）**：登记弹窗基础信息可填国籍/民族/籍贯/婚姻/职业/现住址（省市区+详址）/工作单位/联系人（姓名·关系·电话），与病案首页病人基本信息对齐；提交时 `updatePatientFromIdCard=true` 叠写患者主档；登记 afterCommit 建病案 bootstrap 从主档自动取值（含 `curr_addr_codes`/`napl_codes`）。户口/邮编/出生地等仅病案列、主档无字段者本期不进登记页 | IF-IP-REG-02、IF-IP-REG-03 |
| **BR-IP-REG-005** | 无医生申请直登：`registerDirectInpatientEncounter` **INSERT 并完成登记**为 `IMP + REGISTERED`，**不产生** `PLANNED` |
| **BR-IP-REG-006** | 费用性质须为自费或合同择一，本期不编排医保并行户 |
| **BR-IP-REG-007** | **有申请**（携带住院 `encounterId`）：入院诊断以 **AS-OP-DOR-005** 申请阶段已写入的**住院线**为准；IF-IP-REG-02 展示住院诊断快照（**只读**；中医主诊展示名须合并同组病+证）；IF-IP-REG-03 **不得**新增、修改或覆盖主诊断（`admissionDiagnoses` 不传或为空；领域对已存在主诊跳过 `saveAdmissionDx`，满足 **BR-ENC-014**）。**无申请直登**：须在 IF-IP-REG-03 维护入院诊断（至少一条主诊断）；UI **默认西医**，中医须**一病一症**两条（疾病行 `primaryFlag=true`、证候行 `primaryFlag=false`，`tcmSyndromeGroup` 可空由编排层分配）；**诊断描述**用户未填时不传、不落库 |
| **BR-IP-REG-012** | 住院 `bus_no` 与门诊 `bus_no` **前缀须不同**（与 **AS-OP-DOR-005** BR-IP-REQ-009 一致） |
| **BR-IP-REG-008** | 取消登记：住院处操作；**不得**由医生站发起。可取消 **`PLANNED`** 或 **`REGISTERED`（未分床）**；预交金余额须为 **0**；取消前须**软删**该就诊下未 settle 的 `HOSPITAL_DEPOSIT` **DRAFT**（意向预交金）及历史 CANCELLED |
| **BR-IP-REG-009** | 取消前若账户余额 **> 0**，须先在预交金模块退清（**不在本 AS**），再允许取消 |
| **BR-IP-REG-021** | IF-IP-REG-02 对 `PLANNED` 回显最新 `HOSPITAL_DEPOSIT` DRAFT 的 `displayAmount`（及可选 `paymentDraftId`）；收费员改后的金额与多渠道明细以提交为准；无草稿时金额默认空/0 | IF-IP-REG-02、IF-IP-REG-03 |
| **BR-IP-REG-022** | 登记确认预交金：`paymentDetails` 可多条（微信/现金等）；合计须等于 `advanceDepositAmount`；已有意向 DRAFT 时先软删；**每一渠道各落一笔** `HOSPITAL_DEPOSIT` 并 settle（一渠道一流水），账户余额按合计一次调增；**禁止**无明细 settle | IF-IP-REG-03 |
| **BR-IP-REG-024** | IF-IP-REG-02：未开户（无 `contractId`）时按 `chargeIntent` **预填费用性质下拉**——`INSURANCE`→优先市医保合同、`SELF_PAY`→自费合同；**禁止**单独只读「医生费用性质」字段。IF-IP-REG-01 待登记无合同时队列费用性质列可回落 `chargeIntentText`（医保/自费）。收费员可改选；意向**不**自动开户或医保登记 | IF-IP-REG-01、IF-IP-REG-02 |
| **BR-IP-REG-010** | 取消成功后释放与门诊就诊的关联，允许对同一门诊再次发起住院申请 |
| **BR-IP-REG-011** | 同一门诊就诊关联的未取消住院线，在医生申请入口应已防重；登记侧不再重复创建 |
| **BR-IP-REG-013** | 住院登记读侧按 **`encounterId` + `classEnum=IMP`** 加载就诊主档（库侧隔离）；传入门诊就诊 ID 视为类别不符（**EX-IP-REG-102/003/006**），不得仅按主键命中后再筛类别 |
| **BR-IP-REG-014** | 列表/详情出参 `statusEnumText` 须按 **`DM-ADM-002` §2.3.2** 解析（共享工具 `InpatientEncounterStatusDisplayUtil`）；QueryService `status_enum_text` 可为回落，**不得**直接映射 `EncounterStatus#getDisplay()` |
| **BR-IP-REG-015** | 登记弹窗**入院科室**下拉须经 **`listOrganizationsForInpatientAdmission`**（`type_enum=DEPARTMENT` 且 `class_enum=INPATIENT`）；**禁止**使用 `wardBedInit` 全量机构或门诊/药房组织树代替 | IF-IP-REG-02 |
| **BR-IP-REG-016** | 列表行 **`REGISTERED`（待入科）** 与 **`IN_PROGRESS`（已入科）** 须展示「查看」；IF-IP-REG-02 返回 `readOnly=true`，弹窗不可再提交登记 | IF-IP-REG-01、IF-IP-REG-02 |
| **BR-IP-REG-017** | **门诊转住院**（`ambEncounterId` 非空）：登记弹窗「门诊医生」由 App 解析源门诊 **ADMITTER** 展示，**只读**；登记 IF **不得**接受前端改写 | IF-IP-REG-02 |
| **BR-IP-REG-018** | 登记弹窗选病区时空床余量仅在区段标题展示（IF-IP-REG-05）；**禁止**重复床位数表单项；已分床时只读展示 `bedId`/`bedName` 拼成的入科床位 | IF-IP-REG-02、IF-IP-REG-05 |
| **BR-IP-REG-019** | **自费转医保**：仅当就诊为 **`REGISTERED`/`IN_PROGRESS`/`ON_HOLD`**、**仅个人现金户**（无启用中的医保户）、**未**完成医保平台 2401、**无** `SETTLED` 费用时允许；读卡获取 `psnNo` 后选择省/市医保费用性质与有 `yb_no` 的主治医生；2401 **沿用原入院时间**；幂等开 `MEDICAL_INSURANCE` 户（已停用则重新启用并写入本次合同）；**未结** `ChargeItem` 批量切至医保户；**预交金余额仍留个人现金户** | IF-IP-REG-07 |
| **BR-IP-REG-021** | **医保转自费**：仅当就诊为 **`REGISTERED`/`IN_PROGRESS`/`ON_HOLD`**、存在启用中的医保户、**无** `SETTLED` 费用时允许。已 2401 时先 2302（有上传明细）再 2404（平台已出院则先 2405）；未结费用改挂个人现金户；**停用**医保户使后续挂账回落个人现金户；**预交金不动**。平台撤销失败不改本地挂账 | IF-IP-REG-09 |
| **BR-IP-REG-023** | **更正入院科室**（登记时选错，不是护士站转科）：仅 **`REGISTERED`/`IN_PROGRESS`**、**无** `SETTLED` 费用；目标科室须为启用中的住院科室。病区为启用病区，**不必**挂在目标科室下，可以改到其他科室的病区，也可以保持原病区。待入科只改科室+病区。已入科须选目标病区的床（空床，或仍属该病区的当前床）；床也可以不换。科室、病区、床都未变则拒绝。先写院内 `organization_id` 与病区/床轨迹，**不**改就诊状态、**不**写转科里程碑。已 2401 时接着发 **2403**（人员编号取入院映射，就诊凭证用身份证，不再次读卡；报文头参保地沿用该次 2401 成功报文中的区划，不用医院就医地顶上）；目标科室缺 `yb_no`、缺 `psn_no` 或患者缺身份证则拒绝。2403 失败则院内改动回滚 | IF-IP-REG-08 |

---

## 2. 页面与接口总览（契约概览）

| IF 编号 | 用户动作 | 接口语义（业务） | 读/写属性 | 关键规则（BR） | 关键状态变化（自然语言） | 涉及领域 | 查询服务 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| IF-IP-REG-00 | 进入住院登记页 | 加载支付渠道下拉（**ADR-PAY-001**；登记弹窗首笔预交金） | 读 | — | 无 | — | `PaymentChannelOptionProvider` |
| IF-IP-REG-01 | 按姓名/身份证/登记状态查询、刷新列表（**默认待登记**） | 分页查询住院登记列表（**按状态筛选，非 Tab**） | 读 | — | 无 | Encounter | EncounterContextReader · InpatientEncounterContextQueryModel |
| IF-IP-REG-02 | 点击「登记」或「查看」打开弹窗 | 加载登记上下文：患者主档、住院就诊与申请信息、**入院科室下拉**（`class_enum=INPATIENT`）、费用性质（未开户时按医生 `chargeIntent` 预填）、**门诊医生（只读）**、**入科床位（已分床时）**、**意向预交金总额（DRAFT）** 等 | 读 | BR-IP-REG-013、**BR-IP-REG-015～018**、**BR-IP-REG-021**、**BR-IP-REG-024** | 无 | Patient / Encounter / Organization / Location / Payment | InpatientEncounterContextQueryModel；`listOrganizationsForInpatientAdmission` |
| IF-IP-REG-03 | 无档登记入口 / 弹窗「登记」提交 | 提交住院登记（含无档、有申请、无申请直登）；可选预交金子路径（多渠道明细） | 写/混合 | BR-IP-REG-001～007、011、013、**021～022** | 有申请 **`PLANNED→REGISTERED`**；直登 **`INSERT+REGISTERED`**；可选开户收款 | Encounter / Patient / Condition / Contract / Account / Payment | InpatientEncounterContextQueryModel（写后刷新） |
| IF-IP-REG-04 | 列表「取消」 | 取消住院登记 | 写 | BR-IP-REG-008～010、013 | 可取消态 → **`CANCELLED`** | Encounter / Account（余额门禁） | InpatientEncounterContextQueryModel（写后刷新） |
| IF-IP-REG-05 | 登记弹窗选择科室后加载病区 | 按住院科室查询病区列表；按病区号查询总床/空床 | 读 | — | 无 | Location / Organization | — |
| IF-IP-REG-06 | 登记弹窗检索患者（下拉表格） | 按关键字分页检索患者主档（姓名/病历号/证件号等） | 读 | BR-IP-REG-004 | 无 | Patient | — |
| IF-IP-REG-07 | 列表/查看弹窗「转医保」 | 自费住院就诊读卡补录 → 2401 入院登记 → 未结费用切医保户 | 写/混合 | **BR-IP-REG-019** | 开立医保户；费用 `account_id` 切换 | Encounter / Contract / Account / ChargeItem | AccountQueryService；ChargeItemQueryService |
| IF-IP-REG-09 | 列表/查看弹窗「转自费」 | 医保住院撤销平台入院 → 未结费用切个人现金户 → 停用医保户 | 写 | **BR-IP-REG-021** | 医保户停用；费用 `account_id` 切至个人现金户 | Account / ChargeItem | AccountQueryService；ChargeItemQueryService |
| IF-IP-REG-08 | 查看弹窗「科室变更」 | 更正入院科室、病区；已入科同时改床。已 2401 则院内落库后发 2403 | 写/混合 | **BR-IP-REG-023** | 就诊状态不变；`organization_id` 与 WARD/床轨迹更新 | Encounter / Location | LocationQueryService；平台入院快照 |

---

## 3. 缺口跟踪（Gap）

**无待跟踪缺口。**（`GAP-IP-REG-001` 已关闭：产品口径、**`DM-ADM-002` §6.1.23** 与领域 **`cancelInpatientAdmission`** 已对齐——支持取消 **`PLANNED`** 与 **`REGISTERED`（未分床）**；登记后待入科为 **`REGISTERED`**，分床后 **`IN_PROGRESS`** 由护士站推进。）

---

## 4. 接口编排说明书（业务步骤）

### IF-IP-REG-01 住院登记待办列表查询

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 收费员进入住院登记页或变更筛选条件 |
| **主路径步骤** | 按关键字、身份证号、**登记状态**筛选分页（**默认：待登记**）→ 待登记=`IMP+PLANNED`；待入科=`IMP+REGISTERED`；已入科=`IMP+IN_PROGRESS` → 返回列表行（含 **`wardId`/`wardName`**（医生申请已投影意向病区）、**`bedId`/`bedName`（已分床时）**、**`statusEnum` + `statusEnumText`**，文案见 **BR-IP-REG-014**） |
| **关键分支** | **同一列表接口** + 状态条件；**非**按 Tab 拆页或拆 IF；未传状态时后端默认按「待登记」过滤；行内操作：**待登记**→登记/取消；**待入科**→查看/取消；**已入科**→查看（**BR-IP-REG-016**）；满足 **BR-IP-REG-019** 时行展示「转医保」 |
| **失败分支** | 查询不可用 → EX-IP-REG-101 |
| **查询侧说明** | 纯展示 |

---

### IF-IP-REG-02 登记上下文加载

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 选中列表行（登记或查看）或无档登记打开空弹窗 |
| **主路径步骤** | 有住院就诊标识时：加载患者基本信息、住院就诊详情（含门诊来源、意向科室/病区、**主诊断展示字段**、住院号、**门诊医生姓名**、**分床后 bedId/bedName**、**意向预交金 DRAFT 总额**）→ 返回 **`organizationOptions`**（住院分类科室）与费用性质下拉 → 用户选科室后由 **IF-IP-REG-05** 拉病区并在区段标题展示空床摘要；**主治医生下拉**复用护士站责任医护 init（**AS-IP-NUR-001**），院点可经 init 可选默认主键在空位自动带出 → **查看**与**登记**共用本读路径，查看不写库 |
| **关键分支** | 有申请（`PLANNED`）时诊断区**只读**（展示申请已落住院主诊，**非**查门诊主诊改写）；回显最新 `HOSPITAL_DEPOSIT` DRAFT 金额供收费员确认/改写（**BR-IP-REG-021**）；**`REGISTERED`/`IN_PROGRESS`** 时整单 **`readOnly=true`**（**BR-IP-REG-016**）；额外返回 **`switchToInsuranceEnabled`**（**BR-IP-REG-019**）；`ambEncounterId` 非空时门诊医生只读带回（**BR-IP-REG-017**）；已分床时展示入科床位、未分床不展示（**BR-IP-REG-018**）；无档登记无就诊标识时仅返回空模板与主数据下拉；直登无 `encounterId` 时诊断区可编辑（见 **BR-IP-REG-007**） |
| **失败分支** | 就诊不存在或类别非住院（`IMP`）→ EX-IP-REG-102 |
| **查询侧说明** | 无 |

---

### IF-IP-REG-03 提交住院登记

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 来自**无档登记**（无 `encounterId`）或列表行「登记」；弹窗校验通过 |
| **主路径步骤** | **患者分流**（`patientId` 优先 → 身份证命中复用 → 否则建档，对齐 **AS-OP-FIN-001**）→ **更新主档人口学**（`updatePatientFromIdCard`，**BR-IP-REG-025**）→ **登记**（有申请：`PLANNED→REGISTERED`；无申请直登：`registerDirectInpatientEncounter` 为 `REGISTERED`；均**不修改**门诊就诊）→ **仅直登**按表单为新建住院线维护入院诊断；有申请**不重写**申请已落诊断 → 开立账户（余额 0）→ **预交金**：金额 **> 0** 则校验多渠道明细合计 → 作废旧意向 DRAFT（若有）→ `collect`+`settle`+调账；金额 ≤0 则仅作废旧意向 DRAFT → afterCommit **病案 bootstrap** 自主档自动取值 |
| **关键分支** | 有申请 vs 无申请 vs 无档：共用本 IF，以是否携带住院就诊标识区分；有申请时 `admissionDiagnoses` 应为空（前端只读展示）；直登须携带可编辑诊断列表；门诊转住院时**入院途径**默认 **2**（门诊）、**入院类型**由上游或表单带入；收费员改金额/渠道以提交为准（**BR-IP-REG-022**） |
| **失败分支** | 必填缺失 → EX-IP-REG-001；主数据无效 → EX-IP-REG-002；登记状态不允许 → EX-IP-REG-003；收款失败 → EX-IP-REG-004（登记是否回滚以实现侧事务策略为准，业务上应原子） |
| **查询侧说明** | InpatientEncounterContextQueryModel 行 `status_enum` 变为 **`REGISTERED`**（待入科）；若当场收款则更新费用/账户摘要 |

---

### IF-IP-REG-04 取消登记

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 收费员对可取消行发起取消 |
| **主路径步骤** | 校验就诊处于 **`PLANNED`** 或 **`REGISTERED`（未分床）** → 查询就诊账户余额，**> 0** 则拒绝并提示先退预交金 → **作废**该就诊下 `HOSPITAL_DEPOSIT` DRAFT（**BR-IP-REG-008**）→ 执行取消住院登记 → 释放门诊关联 |
| **关键分支** | 已分床（已入科）不可走本 IF |
| **失败分支** | 余额不为零 → EX-IP-REG-005；已取消重复操作 → EX-IP-REG-006 |
| **查询侧说明** | 列表行消失或标记已取消 |

---

### IF-IP-REG-06 患者分页检索（登记弹窗下拉）

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 无档登记或直登弹窗中，用户在患者区输入关键字检索 |
| **主路径步骤** | 按 `keyword` + 分页参数调用 **门诊挂号**应用服务 **`OutpatientRegistrationAppService#pagePatientsForSelect`**（HTTP `POST /api/app/outpatientRegistration/pagePatientsForSelect`；**住院登记模块不暴露同名方法**）→ 返回患者列表行 → 用户选中后带入 `patientId` 提交 **IF-IP-REG-03** |
| **关键分支** | 仅读；不修改患者主档 |
| **失败分支** | 分页非法 → EX-IP-REG-101 |
| **查询侧说明** | 无 |

---

### IF-IP-REG-05 病区与床位余量查询

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 登记弹窗已选住院科室或录入病区号 |
| **主路径步骤** | 按科室返回启用病区列表（`formEnum=WARD`）→ 按病区返回总床/空床：仅统计其下 `HOUSE` 病房内 `ACTIVE` 的 `BED`；空床另要求 `operational_enum=NOT_IN_USE`（见 **DM-ADM-003** §6.2.2）→ 前端在**住院信息区段标题**展示 `空床 X / 总床 Y`，**不在**表单网格重复床位数（**BR-IP-REG-018**） |
| **失败分支** | 主数据不可用 → EX-IP-REG-102 |
| **查询侧说明** | 无 |

---

### IF-IP-REG-07 自费转医保

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 收费员对 **BR-IP-REG-019** 允许的住院就诊发起「转医保」；医保扩展已启用 |
| **主路径步骤** | 读身份证/医保卡/医保码获取 `psnNo` → 选择省/市医保费用性质 → 选择有 `yb_no` 的主治医生 → 确认 → 幂等开立 `MEDICAL_INSURANCE` 账户（保留既有个人现金户供预交金）→ 2401 入院登记（**入院时间取原就诊 `start_time`**）→ 批量将未结 `ChargeItem` 的 `account_id` 切至医保户 |
| **关键分支** | 列表行 `switchToInsuranceEnabled` 与 IF-IP-REG-02 同口径；转医保弹窗**仅**费用性质、主治医生可编辑，其余只读；**不**收预交金；已有双账户或已 2401 则拒绝 |
| **失败分支** | 已是医保入院 → EX-IP-REG-201；状态不允许 → EX-IP-REG-202；有已结算费用 → EX-IP-REG-203；未读卡 → EX-IP-REG-204；主治医生无效/缺 yb_no → EX-IP-REG-205；扩展未启用/非第三方合同/2401 失败 → EX-IP-REG-206 |
| **查询侧说明** | 成功后列表 `switchToInsuranceEnabled=false`；费用性质回显为医保合同 |

### IF-IP-REG-09 医保转自费

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 收费员对 **BR-IP-REG-021** 允许的医保住院发起「转自费」 |
| **主路径步骤** | 确认 → 已 2401 时撤销已上传明细（2302）并撤销入院（2404；平台已出院则先 2405）→ 未结费用改挂个人现金户 → 停用医保户 |
| **关键分支** | 列表与查看弹窗 `switchToSelfPayEnabled`；预交金不移动；平台撤销失败则本地挂账不变 |
| **失败分支** | 不是医保入院或缺少个人现金户 → EX-IP-REG-210；状态不允许 → EX-IP-REG-202；有已结算费用 → EX-IP-REG-203；扩展未启用 → EX-IP-REG-206；平台撤销失败 → EX-IP-REG-211 |
| **查询侧说明** | 成功后费用性质回显为自费；后续执行/划价挂个人现金户 |

### IF-IP-REG-08 更正入院科室

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 查看弹窗（`REGISTERED` 或 `IN_PROGRESS`）点击「科室变更」 |
| **主路径步骤** | 选择住院科室 → 病区列出全部启用病区（含其他科室，并带出当前病区）→ 已入科再选床（空床，或当前床仍在所选病区；默认当前床）→ 确认。先占新空床（若换床）→ 写科室与病区/床轨迹 → 释放原床 → 若已 2401 则发 2403 |
| **关键分支** | 待入科不传床；已入科床须属于所选病区；可以只改科室、病区和床都不换；科室、病区、床都未变则拒绝；已 2401 用入院映射 `psn_no` + 身份证凭证，不再次读卡，参保地沿用 2401 成功报文；未 2401 只改院内 |
| **失败分支** | 状态不允许或已有结算 → EX-IP-REG-207；床位无效 → EX-IP-REG-208；已医保入院但缺科室 `yb_no` / `psn_no` / 身份证，或 2403 失败 → EX-IP-REG-209（2403 失败整单回滚） |
| **查询侧说明** | 病区为全部启用病区（带主挂靠科室名，含当前病区）；床为目标病区空床 + 本就诊当前床（若已在该病区） |

---

## 5. 业务异常与用户提示

| 语义代码 | 场景 | 用户提示方向 | 可重试 |
| --- | --- | --- | --- |
| EX-IP-REG-001 | 登记必填项缺失 | 补全后再提交 | 是 |
| EX-IP-REG-002 | 科室/病区/费用性质等主数据无效 | 重新选择 | 是 |
| EX-IP-REG-003 | 当前状态不允许登记或重复登记 | 刷新列表确认 | 视原因 |
| EX-IP-REG-004 | 预交金收款失败 | 说明登记是否已生效，引导人工处理 | 视原因 |
| EX-IP-REG-005 | 取消时预交金不为 0 | 请先到预交金模块退款 | 否 |
| EX-IP-REG-006 | 重复取消或已入科不可取消 | 说明不可取消原因 | 否 |
| EX-IP-REG-101 | 列表查询失败 | 缩小条件或稍后 | 是 |
| EX-IP-REG-102 | 详情或主数据加载失败；或就诊类别非住院 | 刷新并确认选中住院登记行 | 是 |
| EX-IP-REG-201 | 已是医保入院或已在平台 2401 | 说明无需重复转医保 | 否 |
| EX-IP-REG-202 | 就诊状态不允许转医保 | 刷新列表确认当前阶段 | 否 |
| EX-IP-REG-203 | 存在已结算费用 | 说明须先处理已结费用 | 否 |
| EX-IP-REG-204 | 未读卡或缺少人员编号 | 请先读身份证/医保卡/医保码 | 是 |
| EX-IP-REG-205 | 主治医生未选或缺医保编码 | 重新选择主治医生 | 是 |
| EX-IP-REG-206 | 医保扩展未启用、费用性质非医保或 2401 失败 | 检查配置与读卡信息 | 视原因 |
| EX-IP-REG-207 | 状态不允许更正入院科室、已有结算，或科室病区未变化 | 说明当前不能改，或重新选择 | 视原因 |
| EX-IP-REG-208 | 床位不属于目标病区、不是空床，或待入科误传床位 | 重新选择床位 | 是 |
| EX-IP-REG-209 | 已医保入院但缺科室医保码、人员编号、身份证，或 2403 失败 | 补主数据或查看医保返回；院内不会留下半套科室 | 视原因 |

---

## 6. 事件语义与消费方

| 事件语义（DM 英文名以 DM 为准） | 触发相位（IF） | 典型消费方 |
| --- | --- | --- |
| 住院登记已完成 / InpatientAdmissionCompletedEvent | IF-IP-REG-03 | InpatientEncounterContextQueryModel（`REGISTERED` + `patient_id`/busNo/人口学投影，见QueryService §4.4）；护士站待入科列表 |
| 住院登记已取消 / InpatientAdmissionCancelledEvent | IF-IP-REG-04 | InpatientEncounterContextQueryModel；床位管理 |
| 入院诊断已创建 / AdmissionDiagnosisCreatedEvent | **AS-OP-DOR-005** 申请时；或 IF-IP-REG-03 **直登**新建病情时 | InpatientEncounterContextQueryModel；质控 |
| 就诊诊断关联已维护 / EncounterDiagnosisAssociationUpsertedEvent | **AS-OP-DOR-005** 申请时；或 IF-IP-REG-03 **直登**时 | 病历、医嘱 |
| 账户已开立 / AccountOpenedEvent | IF-IP-REG-03（有预交金时） | 费用摘要读侧 |
| 支付相关事件（住院存款） | IF-IP-REG-03（有预交金时） | 费用/流水读侧 |

---

## 7. 非功能性需求

| 类别 | 要求 |
| --- | --- |
| **性能（经验目标）** | 列表 ≤1000ms；登记提交 ≤2500ms（含可选收款） |
| **幂等** | 短时重复提交登记应对同一住院就诊返回已登记语义；收款幂等以支付流水为准 |
| **并发** | 有申请登记：`registerInpatientEncounter` **悲观行锁** + 锁内 `PLANNED` 断言（GAP-ATD-004）；双窗后写者 → **EX-IP-REG-003** |
| **一致性** | 登记与可选收款在同一业务事务内原子；收款失败不应留下「已登记未收款但误开户」 |
| **审计** | 登记员、登记时间、费用性质、可选首笔预交金可追溯 |

---

## 8. 测试要点（业务视角）

| 类别 | 要点 |
| --- | --- |
| **正向** | 待登记列表筛选；有申请登记→待入科；无档直登；无申请直登；带预交金登记当场开户；不带预交金不开户；**待入科/已入科行可「查看」只读弹窗**；门诊转住院门诊医生只读带回；已分床展示入科床位；**自费已入科读卡转医保**；**已入科查看窗口更正入院科室并在已 2401 时发出 2403** |
| **异常** | 预交金>0 时取消被拒；已分床不可取消；必填缺失；**有 SETTLED 费用时转医保被拒**；**已结算或出院后科室变更被拒**；**2403 失败时院内科室回滚** |
| **规则** | BR-IP-REG-002 金额分支；BR-IP-REG-007 有申请登记不改诊断、直登须落主诊；BR-IP-REG-008 取消范围；**BR-IP-REG-016～019** 查看/门诊医生/床位/转医保；**BR-IP-REG-023** 入院科室更正与转科分离 |
| **联动** | 医生申请后出现在待登记；取消后同一门诊可再申请（**AS-OP-DOR-005**） |

---

## 9. 关联文档与变更历史

### 9.1 关联文档

| 文档 |
| --- |
| `docs/领域/DomainModel/DM-ADM-002-Encounter-就诊.md` |
| `docs/领域/DomainModel/DM-ADM-001-Patient-患者.md` |
| `docs/领域/DomainModel/DM-CLN-003-Condition-诊断记录.md` |
| `docs/领域/DomainModel/DM-ADM-003-Location-位置.md` |
| `docs/领域/DomainModel/DM-FIN-001-Contract-费用性质.md` |
| `docs/领域/DomainModel/DM-ADM-009-Account-就诊账户.md` |
| `docs/领域/DomainModel/DM-FIN-002-PaymentReconciliation-支付流水.md` |
| `DM §6.2（DM-ADM-002 §6.2 EncounterQueryService（InpatientEncounterContextQueryModel）` |
| `docs/应用/zxplat-application/AppService/AS-OP-DOR-005-门诊医生站住院申请.md |
| `docs/应用/zxplat-application/AppService/AS-IP-FIN-002-住院预交金.md`（续收/退费） |
| `docs/应用/zxplat-application/AppService/AS-OP-FIN-001-门诊挂号.md`（患者分流与开户节奏参照） |

### 9.2 变更历史

| 版本 | 日期 | 变更摘要 |
| --- | --- | --- |
| v1.25 | 2026-10-15 | **BR-IP-REG-025**：登记弹窗采集国籍/民族/籍贯/婚姻/职业/现住址/工作单位/联系人并叠写主档；病案 bootstrap 带回；对齐 **AS-IP-FIN-001-SDD v1.26** |
| v1.24 | 2026-10-14 | **BR-IP-REG-024**：医生 `chargeIntent` 直接预填费用性质下拉（医保默认市医保）；去掉单独「医生费用性质」展示；对齐前端登记弹窗 |
| v1.23 | 2026-10-14 | **BR-IP-REG-024**：回显医生办理意向（医保办/自费办）；对齐 **AS-OP-DOR-005 BR-IP-REQ-012** / **DM-ADM-002** `charge_intent` |
| v1.22 | 2026-10-12 | **BR-IP-REG-023**：2403 报文头参保地沿用本次住院 2401 成功报文，避免空区划落成医院就医地被平台拒绝 |
| v1.21 | 2026-10-12 | **BR-IP-REG-023**：科室变更的病区、床不要求挂在目标科室下，可选其他科室病区，也可保持原病区原床只改入院科室 |
| v1.20 | 2026-10-12 | 新增 **IF-IP-REG-08** / **BR-IP-REG-023**：查看窗口更正入院科室（科室+病区，已入科含床）；已 2401 后发 2403，失败整单回滚。领域 `correctInpatientAdmissionDepartment` |
| v1.19 | 2026-10-11 | **BR-IP-REG-022**：登记预交金改为**一渠道一笔流水**（逐条 `paymentDetails` 各 collect+settle）；余额仍按合计调增 |
| v1.18 | 2026-10-11 | 意向预交金草稿：登记确认/取消/清空改为 **`softDeleteAdvanceDepositDraft`**（含历史 CANCELLED）；**BR-IP-REG-002/008/022** 作废→软删；对齐 **DM-FIN-002** §6.1.13a |
| v1.17 | 2026-09-18 | **BR-IP-REG-004** 明确空身份证可建档、禁合成证件号；新增 **BR-IP-REG-020** 年龄必填与证/生日/年龄前端联动（对齐门诊 **BR-REG-021**） |
| v1.16 | 2026-09-01 | 新增 **IF-IP-REG-07** 自费转医保（**BR-IP-REG-019**）；列表/上下文 `switchToInsuranceEnabled`；异常 **EX-IP-REG-201～206** |
| v1.25 | 2026-10-15 | **IF-IP-REG-02**：主治下拉复用护士站 init；院点可经可选默认主键在选科后空位自动带出（对齐 **AS-IP-NUR-001 v1.39**） |
| v1.15 | 2026-08-17 | **开户/挂账口径**：个人现金户=自费+预交金；医保/工伤另开医保户为费用挂账真源；对齐 **BR-IP-NUR-018** / **BR-IP-STL-019** |
| v1.15 | 2026-10-12 | **入院诊断展示**：登记只读优先医保名（`resolvePrimaryDiagnosisForRegistration`）；国临展开改由住院医生站触发（对齐 **AS-OP-DOR-005** / **AS-IP-DOR-002**） |
| v1.14 | 2026-08-13 | **`in_way_code` 对齐 adm_way**：术语口径 **1/2/3/9**（入院途径）；与 `admit_source_code`（入院类型）分列；V28 迁移 |
| v1.13 | 2026-07-08 | **中医主诊只读回显**：`DiagnosisSnapshotDisplayResolver` 合并一病一症为「病名（证候）」；**诊断描述**用户未填时不传/不回填目录名 |
| v1.12 | 2026-06-17 | **登记弹窗 UI 对齐成码**：待入科/已入科列表「查看」+ `readOnly`（**BR-IP-REG-016**）；门诊转住院门诊医生只读带回（**BR-IP-REG-017**）；空床摘要仅标题展示、已分床展示入科床位（**BR-IP-REG-018**）；IF-IP-REG-02 增 `outpatientDoctorName`/`bedId`/`bedName` 语义 |
| v1.11 | 2026-05-28 | **IF-IP-REG-06**：患者检索 HTTP 更正为 **`outpatientRegistration/pagePatientsForSelect`**（住院 App 无同名方法；与前端 `#/api/outpatient-registration` 一致）。 |
| v1.10 | 2026-05-28 | 读模型废止后对齐 zxplat 实现（EncounterContextFacade / querysupport Loader）。 |（已迁至 EncounterContextReader / application.shared.encounter）
| v1.0 | 2026-05-18 | 初版：住院登记 IF-IP-REG-01～05；`PLANNED` 作预入院、`IN_PROGRESS` 作待入科；登记与可选预交金分支 |
| v1.1 | 2026-05-18 | 关闭 GAP-IP-REG-001：`IN_PROGRESS` 未分床可取消；同步 DM §6.1.23；文档简称「住院登记」 |
| v1.2 | 2026-05-18 | 产品裁决：本期全做 IF-01～05；列表按状态筛选（默认待登记）非 Tab；无档登记；预交金仅登记弹窗填写、续收不在本页 |
| v1.3 | 2026-05-18 | 澄清：住院/门诊双 Encounter；登记改住院线非改门诊；诊断预填仅 UI；住院/门诊 bus_no 前缀分离 |
| v1.4 | 2026-05-19 | 预交金边界引用 **AS-IP-FIN-002**；读侧 `mustGetEncounterOfClass(IMP)` |
| v1.5 | 2026-05-19 | 对齐 **`DM-ADM-002` v1.11**：登记 `PLANNED→REGISTERED`；直登不经 `PLANNED`；待入科/已入科列表谓词；取消 `PLANNED`/`REGISTERED`；分床 `IN_PROGRESS` 归护士站 |
| v1.9 | 2026-05-20 | 新增 **IF-IP-REG-06** 患者分页检索；**IF-IP-REG-03** 建档分流与门诊同序（`patientId`→身份证→`registerPatient`，机构取当前登录机构） |
| v1.8 | 2026-05-20 | **BR-IP-REG-014** IMP 状态展示文案；IF-IP-REG-03 患者分流与门诊挂号对齐说明；列表 `statusEnumText` 引用 **DM-ADM-002** §2.3.2 |
| v1.7 | 2026-05-20 | IF-IP-REG-02 主诊断结构化展示（不暴露 JSON）；IF-IP-REG-01 列表展示字段回填（对齐 **AS-IP-FIN-001-SDD** v1.7） |
| v1.8 | 2026-05-20 | 对齐QueryService **v3.2**：医生申请 `InpatientAdmissionRequestedEvent.wardId` 投影至待登记列表 `wardId`（**DM-ADM-002** v1.19） |
| v1.12 | 2026-07-08 | **入院诊断中西医**：直登 UI 默认西医、中医一病一症；`AdmissionDiagnosisSubmitDto` 增 `tcmFlag`/`tcmSyndromeGroup`；**BR-IP-REG-007** 补述 |
| v1.11 | 2026-06-09 | **IF-IP-REG-02** 响应增加 `organizationOptions`（`listOrganizationsForInpatientAdmission`）；新增 **BR-IP-REG-015**；前端入院科室不再依赖 `wardBedInit` 全量 org |
| v1.10 | 2026-05-20 | 对齐 **DM-ADM-002 v1.21** / **BR-ENC-021**：`saveAdmissionDx` 落库时 `diag_type_code` 固定入院诊断（`31`）；申请/直登 Request 不传诊断类型 |
| v1.6 | 2026-05-19 | **入院诊断口径**：有申请时诊断在 **AS-OP-DOR-005** 已落住院线，登记只读展示、提交不重写（**BR-IP-REG-003/007**）；直登由收费员自选新增；事件触发相位与 **BR-ENC-014** 对齐 |

---

**文档负责人**：（待填） · **评审**：（待填）
