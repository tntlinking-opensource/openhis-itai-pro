# 门诊挂号 · 应用服务设计（AS）

> **文档职责**：说明「门诊挂号」流程的**接口契约（IF）**、业务语义、涉及聚合与 QueryService、规则（BR）、编排意图；作为上游契约供详细设计与实现对齐。
>
> **本文必须写**：IF 编号；用户动作与读写属性；关键校验与状态变化；Gap；异常口径；事件语义；非功能约束；业务向测试要点。
>
> **本文禁止写**：具体类名/应用方法名；Request/Response 字段明细；包路径；Mapper；伪代码；写库顺序；QueryService 字段组装规则。
>
> **页面边界**：门诊挂号收费窗口——选患者 → 费用性质/科室/医生/号源 → 可选预结算 → 确认挂号（可选一站式收费）→ 当日或条件列表 → 退号（可选联动退费）→ 补打挂号单。
>
> **覆盖声明**：完整契约以 **§2 IF-REG-01～11、IF-REG-15** 为准（IF-REG-12～14 为预约模块增量，不在本文展开）；当前前端已接通范围以 **§1.4**「本期」为准；未落地能力入 **§3 Gap**。
>
> **编码**：AS-OP-FIN-001 · **版本**：v2.45 · **日期**：2026-10-13

---

## 1. 场景概述

### 1.1 目标与边界


| 维度         | 说明                                                                      |
| ---------- | ----------------------------------------------------------------------- |
| **在内**     | 挂号主数据初始化（含性别下拉，与建档页同源值集）；患者检索与挂号前患者分流（按身份证复用/新建）；科室/医生/号源选择；确认挂号（含预结算语义）；支付挂号费；挂号列表查询；**已生成挂号记录修改科室**；退号可否校验与退号提交；补打挂号单 |
| **在外**     | 医保统筹结算细则、第三方支付渠道专属协议、纸质票据印刷物流                                           |
| **跨域**     | Encounter 创建与取消；收费域账户/费用明细/支付/票据编排（按机构启用情况）                             |
| **编排权威**   | 以本文 **§2、§4** 为准                                                        |
| **前置（摘要）** | 患者可被检索或在确认挂号前按身份证命中复用/新建；科室/医生/费用性质/号源主数据可用                             |
| **后置（摘要）** | 挂号成功则创建门诊就诊并形成列表可查询上下文；退号成功则就诊取消并按策略完成退费编排                              |


### 1.2 角色与关键动作


| 角色        | 关键动作                                                   |
| --------- | ------------------------------------------------------ |
| **挂号收费员** | 检索患者 → 填写挂号要素 →（可选）预结算 → 确认挂号 →（可选）支付 → 查询列表 → **改科室** → 退号 → 补打 |


### 1.3 术语与口径约定


| 术语        | 口径                                                  |
| --------- | --------------------------------------------------- |
| **预结算**   | 确认挂号前的计价与应付金额权威语义（IF-REG-06 `totalAmount`）；页旁「挂号费用」展示取自 IF-REG-01 下发的号源费映射，须与预结算同口径（**BR-REG-022**） |
| **待挂号**   | IF-REG-06 成功后就诊写模型为 **`EncounterStatus.PLANNED`**，QueryService 展示「待挂号」；**不可走 IF-REG-09/10 退号**（见 BR-REG-008）；**未完成 IF-REG-07 前**不应作为医生站 IF-DOR-01 的**待诊**查询条件（医生站以 **`encounterStatus`** 过滤，**待诊**典型为 **`REGISTERED`**，见 `AS-OP-DOR-001`） |
| **已挂号**   | IF-REG-07 缴费确认成功后就诊为 **`REGISTERED`**，与 `OutpatientPaymentSettledEvent` 投影及领域 `completeOutpatientRegistration` 一致 |
| **QueryService 能力**   | 右侧列表以QueryService 展示为主；**可否退号**须回写 Encounter/收费域状态机，不得仅凭列表行判定 |
| **DM 路径** | 下文聚合路径均指 `docs/领域/DomainModel/` 下对应文件 |
| **一致性**   | 事务边界由框架统一处理；本文§7仅表述业务侧原子性意图                         |


### 1.4 本期范围与非目标


| 类别             | 内容                                                                         |
| -------------- | -------------------------------------------------------------------------- |
| **本期（落地现状口径）** | 与当前页面已接通部分对齐：**IF-REG-01～06** 主线（初始化、检索、科室、医生、确认挂号）；右侧列表查询侧重 **IF-REG-08** |
| **待接通**        | 电子凭证/身份证/医保卡读卡；一站式正式结算与票据；补打打印链路；BR 要求的「只选科/只选医」联动与预结算权威替换                 |
| **非目标**        | 医保平台实时清算细则；住院登记                                                            |


### 1.5 领域清单（聚合 + 职责 + DM 路径）


| 聚合（域）                                                      | 在本场景中的职责（摘要）         | DM 路径                                      |
| ---------------------------------------------------------- | -------------------- | ------------------------------------------ |
| **Patient**                                                | 分页检索、建档与同一人探测        | `DM-ADM-001-Patient-患者.md`                 |
| **Encounter**                                              | 开立门诊就诊、列表口径、退号取消     | `DM-ADM-002-Encounter-就诊.md`               |
| **Organization**                                           | 门诊科室列表               | `DM-ADM-004-Organization-机构科室.md`          |
| **Practitioner**                                           | 医生列表（可按科室过滤）         | `DM-ADM-005-Practitioner-医务人员.md`          |
| **Contract**                                               | 费用性质列表               | `DM-FIN-001-Contract-费用性质.md`              |
| **HealthcareService**                                      | 号源/服务类型上下文           | `DM-ADM-007-HealthcareService-医疗服务.md`     |
| **ChargeItemDefinition**                                   | 挂号费定价口径              | `DM-ADM-010-ChargeItemDefinition-收费项定价.md` |
| **ChargeItem / Account / PaymentReconciliation / Invoice** | 费用明细、支付、票据（正式结算与退费时） | 各聚合对应 DM（详略以收费域为准）                         |


### 1.6 业务规则（BR-*）


| 规则 ID          | 描述                                                 | 绑定 IF                 |
| -------------- | -------------------------------------------------- | --------------------- |
| **BR-REG-001** | **只选科室、不选医生**：允许；不写 `REGISTRATION_DOCTOR`，医生站待诊按 **BR-DOR-009** 科室共享；**接诊后仅活动 `ADMITTER` 可见**；若仍选了医生，则与挂到医生口径一致（待诊仅该医生） | IF-REG-01 / IF-REG-06 |
| **BR-REG-002** | **只选医生、不选科室**：允许；选定医生后联动科室与可选号源                    | IF-REG-05 / IF-REG-06 |
| **BR-REG-003** | 确认挂号前须已选患者且必填项齐全（费用性质、号源类型；科室/医生满足 BR-REG-001/002） | IF-REG-06             |
| **BR-REG-021** | **患者年龄必填（前端产品）**：门诊挂号患者信息区年龄必填；填身份证或出生日期须即时回显年龄，填年龄（且无证）须回显出生日期；二者双向联动。产品默认身份证号必填（填证即带出生日+年龄，年龄必填对操作员无感）；院点可覆盖为身份证可选（无证须手填年龄）。**患者管理**建档/改档页证件号同口径（`patient-id-card.config`，国基与挂号一并覆盖可选）。主档 `id_card` 允许为空，**禁止**按年龄合成虚假身份证号 | IF-REG-06             |
| **BR-REG-004** | 科室/医生/号源变更时应付金额以预结算为准；关键变更后须刷新预结算                  | IF-REG-06             |
| **BR-REG-005** | 医生列表、号源列表默认不分页一次返回（上限在详设约束）；**医生列表仅含在门诊科室（CLINIC）挂了激活医师岗的在职人员**，不含仅在住院科室出诊的医师 | IF-REG-04 / IF-REG-05 |
| **BR-REG-006** | `patientId` 为空时，须先按身份证命中既有患者；未命中才创建患者后进入预结算/确认挂号      | IF-REG-06             |
| **BR-REG-007** | 身份证命中既有患者时禁止重复建档，直接复用命中患者继续挂号                         | IF-REG-06             |
| **BR-REG-008** | 退号仅允许对「可退」就诊状态执行；**仅 `REGISTERED`/`TRIAGED`（未接诊）**；**`PLANNED`（待挂号）不可退号**；**`IN_PROGRESS`/`ON_HOLD`/`DISCHARGED`（已接诊/完诊）不可直接退号**——须患者先至医生站 **取消接诊**（**AS-OP-DOR-001 IF-DOR-06**，医生同意）后再退；窗口提示「请让患者去医生处取消接诊后再退号」。与账单门禁（BR-REG-015）相互独立 | IF-REG-09 / IF-REG-10 |
| **BR-REG-009** | 正式结算对外原子一致；票据开票失败不回滚已成功支付（策略详设）。**医保挂号扫码前已 2207**：本地缴费须复用 CONFIRMED 回执写入统筹/个账与 `fin_payment_yb_settlement`，禁止因 ACTIVE 预结算已空而只落现金造成单边。**终态勾稽**：扫码挂接（`applyScanBeforeSettle`）之后，支付明细合计须等于挂号费 **ChargeItem** 合计（同 **AS-OP-FIN-002 · BR-OPC-024** 阶段 B）；失败 → **EX-OPC-405** / 支付规则冲突 | IF-REG-07 |
| **BR-REG-017** | **真正零元不开票**：票面（挂号收费项合计）**≤ 0** 时仅完成结算/挂号，**不**创建发票草稿、**不**调度博思开具；票面 **> 0**（含医保全报现金为 0）时按 **BR-INV-008** 开票（编排 `InvoiceAfterPaymentOrchestration`） | IF-REG-07             |
| **BR-REG-010** | 新建患者建档机构取当前登录机构；挂号就诊科室仍取前端所选挂号科室                     | IF-REG-03 / IF-REG-06 |
| **BR-REG-011** | **同日同科室防重**（产品默认）：同一自然日、同一患者、同一挂号科室下，若已存在占用名额的门诊就诊（含待挂号 `PLANNED`、已挂号及诊毕；**退号 `CANCELLED` / 未收费取消 `ENTERED_IN_ERROR` 释放名额**），则禁止再次确认预结算挂号。**院点放宽**：院点启动以**代码常量**注入 `OutpatientSameDaySameDeptDuplicatePolicy`（国基 `H22010202493OutpatientRegistrationConstants`；**禁止 yml**），允许同日同科多挂；写侧经命令 `skipSameDaySameDeptDuplicateCheck` 同步跳过领域 **BR-ENC-011** | IF-REG-06             |
| **BR-REG-012** | **退号退费仅整单全退 + 原路返回**：IF-REG-10 支付编排使用 `completeRegistrationCancelRefund`（原单+逆向单一次收口、**唯一**退费完成事件）；`requestOutpatientRefund` 须带原支付 `contract_no` 与渠道明细（含统筹/个账/自付，口径同 **BR-REF-011**）；**零元号例外**：正式结算应付为 0 且原支付无渠道明细时（对齐 **BR-REG-017**），允许空明细逆向单整单冲退；非零元仍禁止空明细；**不存在**产品「半退」、保留链 `PLANNED`、医生站 `REFUNDING` 过程态；**不得**调用门诊账单退费用的 `finalizeOriginalOutpatientPayment` + 半退 pre+settle 组合 | IF-REG-10             |
| **BR-REG-013** | 门诊退号链读侧按 **`encounterId` + `classEnum=AMB`** 加载就诊主档（库侧隔离）；传入住院等非门诊就诊 ID 拒绝退号 | IF-REG-09 / IF-REG-10 |
| **BR-REG-014** | **号源类型与医师配置**：号源类型只跟医师（用户管理 `doctorServiceIds`），与科室无关，**亦与当日排班/放号无关**；**须先选科室或医生**后才可选号源；**已选医生**时号源下拉=该医师可接诊号源（未配置则空列表，预结算 Gate 拒绝）；**未选医生**时号源=全部 ACTIVE；**禁止**与当日 Schedule/Slot 求交（手动挂号与用排班挂号解绑） | IF-REG-01 / IF-REG-06 |
| **BR-REG-016** | **点选号源占号**：仅当 IF-REG-06 请求显式携带 `slotId`（挂号台点选当日号源）时调用 `allocateWalkInSlot` 占用该 Slot；纯表单手动挂号（无 `slotId`/无 `appointmentId`）不读排班、不占号；预约转挂号走 `appointmentId`（IF-REG-13） | IF-REG-06 |
| **BR-REG-015** | **退号收费全收口**：除挂号费锚点外，就诊下**不得**存在待收费（`PLANNED`）、已收费（`SETTLED`）、退费中（`REFUNDING`）账单；**退药/退耗不在本规则重复校验**——已由门诊退费执行前 **AS-OP-FIN-003 IF-REF-04** 收口（费用能全退即药耗已完结）；**诊疗项目执行**不在本规则范围 | IF-REG-09 / IF-REG-10 |
| **BR-REG-018** | **复用患者回写联系电话**：有 `patientId` 直接复用；无 `patientId` 时按身份证命中复用；复用路径下若请求联系电话非空且与主档不一致，则回写主档联系电话（列表「联系电话」读主档）；新建路径仍随建档写入 | IF-REG-06 |
| **BR-REG-019** | **复用/新建患者写入地址全文**：请求 `address` 为身份证读卡等得到的地址全文（不拆省市区）；**新建**时随 `registerPatient` 写入主档 `Patient.address`；**复用**（有 `patientId` 或身份证命中）时非空且与主档不一致则 `updatePatientAddress` 回写；空串不覆盖主档 | IF-REG-06 |
| **BR-REG-020** | **复诊0元号资格**：产品号源展示名「复诊0元号」（0 元；种子默认 `bus_no=HS00000004`，**Gate 按 `name` trim 精确匹配识别，不按 bus_no**）；号源下拉**不做前端筛选**（全员可见）；预结算写侧 Gate：同一患者、**同一挂号科室**、前次就诊须**已接诊**（`IN_PROGRESS`/`ON_HOLD`/`DISCHARGED`），自 **`reception_time` 起 72 小时内**；跨科室不算复诊；前次号源不得为复诊0元号自身；接诊医生可与本次不同；不满足 → **`REG-BIZ-420`** | IF-REG-06 |
| **BR-REG-022** | **挂号费展示价**：有诊查费绑定（`activity_ids`）时按绑定 AD 当前 SALE 汇总；**空绑**为 0（零元号）。IF-REG-01 `serviceTypeFeeById` 按**全量 ACTIVE**号源构建（**不**随医师收窄），下拉仍按 **BR-REG-014** 收窄；页旁展示与有绑预结算同口径，禁止因收窄丢价而页旁显 0、弹窗应收非 0 | IF-REG-01 / IF-REG-06 |
| **BR-REG-023** | **挂号记录改科室**：挂号列表对 **`PLANNED`/`REGISTERED`/`TRIAGED`/`IN_PROGRESS`/`ON_HOLD`** 可改就诊科室；**完诊、退号、未收费取消不可改**。只改挂号科室，**不改挂号医生、不重占号源**。目标须为启用门诊科室，且与当前科室不同。同日同科占名额仍按 **BR-REG-011**（院点放宽时允许改入当日已挂过的科室）。挂号费若原记在旧科室，开单/执行/成本科室一并改到新科室；**医生站已开医嘱费用不跟随**。自费或尚未医保挂号：只改院内。已医保挂号且**尚未上传费用、尚未结算**：撤销后按新科室重挂；凭证用患者身份证，参保地用**当日读卡缓存**，禁止用医院就医地顶上；缓存过期或目标科室无医保编码则拒绝。**已上传医保费用或已正式结算**：拒绝，须先取消挂号或退号后再挂。院内修改与医保重挂同一事务，平台失败则院内回滚 | IF-REG-15 |


---

## 2. 页面与接口总览（契约概览）


| IF 编号     | 用户动作      | 接口语义（业务）                             | 读/写属性 | 关键规则（BR）                | 关键状态变化（自然语言）             | 涉及领域                                                                                | QueryService                       |
| --------- | --------- | ------------------------------------ | ----- | ----------------------- | ------------------------ | ----------------------------------------------------------------------------------- | ------------------------- |
| IF-REG-01 | 打开挂号页     | 初始化下拉：科室、医生、费用性质、号源类型、性别（与建档同源）；下发号源→挂号费映射供页旁展示（**BR-REG-022**） | 读     | **BR-REG-022**          | —                        | Organization / **`PractitionerRoleScopeLoader`** / Contract / HealthcareService / ChargeItemDefinition / 术语值集 | —                         |
| IF-REG-02 | 检索患者      | 分页关键字检索供选择；行内含性别码与展示、年龄、生日等供核对     | 读     | —                       | —                        | Patient                                                                             | PatientSummary（若接入）       |
| IF-REG-03 | 挂号前患者分流（并入 IF-REG-06） | `patientId`为空时按身份证命中复用；未命中新建患者后继续预结算 | 混合    | BR-REG-006 / BR-REG-007 / BR-REG-018 / BR-REG-019 | 复用既有患者或新增患者              | Patient                                                                             | PatientSummary            |
| IF-REG-04 | 加载科室列表    | 门诊科室全量或树                             | 读     | BR-REG-005              | —                        | Organization                                                                        | —                         |
| IF-REG-05 | 加载医生列表    | 按科室等职过滤的医生列表                         | 读     | BR-REG-005              | —                        | **`PractitionerRoleScopeLoader`** + Practitioner / Organization                   | —                         |
| IF-REG-06 | 确认挂号（预结算） | 校验并生成/刷新预结算，创建门诊就诊                   | 混合    | BR-REG-001～004、BR-REG-011、**BR-REG-020**、**BR-REG-022**、BR-REG-018、BR-REG-019 | 创建 Encounter，**初态 `PLANNED`（待挂号）**；**指定医师时写入活动 `REGISTRATION_DOCTOR` 参与者**（见 `DM-ADM-002` §6.1.1）；生成支付草稿 | ChargeItemDefinition / Encounter / Contract / Patient / Organization / Practitioner | ClinicRegister 门诊挂号列表（刷新） |
| IF-REG-07 | 支付挂号费     | 正式结算并固化费用与票据（实付 > 0 时）               | 写/混合  | BR-REG-009、**BR-REG-017** | 支付成功后就诊 **`PLANNED`→`REGISTERED`**（挂号正式生效） | Encounter / Account / ChargeItem / PaymentReconciliation / Invoice（条件） | ClinicRegister 门诊挂号列表（刷新） |
| IF-REG-08 | 查询挂号列表    | 分页列表；**行数据须含退号弹窗所需支付/费用摘要**（不再单列 IF） | 读     | —                       | **`PLANNED` 行须带回可结算的 `paymentDraftId`（若有草稿）**，支持列表内直接发起 IF-REG-07 | Encounter（读口径）                                                                      | ClinicRegister 门诊挂号列表     |
| IF-REG-09 | 退号前校验     | 是否可退的状态与收费全收口门控                       | 读     | BR-REG-008、013、015    | —                        | Encounter / ChargeItem                                         | —                         |
| IF-REG-10 | 提交退号      | 就诊取消并联动退费/冲红（若适用）                    | 写/混合  | BR-REG-008、012、013    | 就诊取消；退费推进                | Encounter / ChargeItem / PaymentReconciliation / Invoice / Account                  | ClinicRegister 门诊挂号列表（刷新） |
| IF-REG-11 | 补打挂号单     | 按标识查询挂号单展示数据供打印                      | 读     | —                       | —                        | —                                                                                   | RegistrationSlip 挂号单      |
| IF-REG-15 | 修改挂号科室    | 把已生成的挂号改到另一门诊科室                    | 写     | **BR-REG-023**、BR-REG-011 | 就诊科室变更；挂号费归属科室同步；已医保挂号且未上传费用时重挂 | Encounter / ChargeItem / Organization | ClinicRegister 门诊挂号列表（刷新） |


---

## 3. 缺口跟踪（Gap）

> **无**

---

## 4. 接口编排说明书（业务步骤）

### IF-REG-01 挂号表单主数据初始化


| 维度          | 说明                                          |
| ----------- | ------------------------------------------- |
| **触发前提**    | 收费员进入挂号页                                    |
| **主路径步骤**   | 下发科室、医生、费用性质、号源类型及性别等选项（可一次下发或可拆分，对产品保持一次加载体验；性别值集与患者建档页一致）；**医生下拉与 IF-REG-05 同口径**；**号源类型按 BR-REG-014 收窄**（传 `practitionerId` 时按医师可接诊号源；**不与当日排班/放号求交**） |
| **关键分支**    | 可按门诊过滤科室；前端须先选科室或医生后才开放号源下拉 |
| **失败分支**    | 主数据不可用 → 提示刷新或稍后重试（EX-REG-102）              |
| **查询侧说明** | 无                                           |


---

### IF-REG-02 患者模糊检索分页


| 维度          | 说明               |
| ----------- | ---------------- |
| **触发前提**    | 输入关键字            |
| **主路径步骤**   | 分页返回患者候选供选择；每行携带性别编码与展示文案、周岁年龄、出生日期等，便于收费员核对 |
| **关键分支**    | —                |
| **失败分支**    | 检索失败（EX-REG-101） |
| **查询侧说明** | 无                |


---

### IF-REG-03 挂号前患者分流（并入 IF-REG-06）


| 维度          | 说明               |
| ----------- | ---------------- |
| **触发前提**    | 执行 IF-REG-06 且未传 `patientId` |
| **主路径步骤**   | 按身份证命中患者 → 命中则复用（并可回写表单联系电话 **BR-REG-018**、地址全文 **BR-REG-019**）；未命中则以当前登录机构完成建档（含可选地址全文）并继续 IF-REG-06 |
| **关键分支**    | 身份证缺失或建档最小字段缺失时拒绝进入后续预结算；建档机构不采用前端挂号科室入参 |
| **失败分支**    | 校验失败（EX-REG-101） |
| **查询侧说明** | Patient 摘要读侧可选   |


---

### IF-REG-04 门诊科室列表


| 维度          | 说明         |
| ----------- | ---------- |
| **触发前提**    | 表单需要科室列表   |
| **主路径步骤**   | 返回门诊科室列表   |
| **失败分支**    | EX-REG-102 |
| **查询侧说明** | 无          |


---

### IF-REG-05 医生列表


| 维度          | 说明            |
| ----------- | ------------- |
| **触发前提**    | 已选科室或全局加载策略允许 |
| **主路径步骤**   | **`PractitionerRoleScopeLoader#listActivePractitionersForOutpatientRegistration`** 解析门诊科室范围内医师岗位 → **`PractitionerQueryService#listActivePractitionersByResolvedRoleScope`**；已选科室时按该科室收窄，未选科室时在全部门诊科室集合内收窄 |
| **失败分支**    | EX-REG-102    |
| **查询侧说明** | 无             |


---

### IF-REG-06 确认挂号（登记门诊就诊 + 预结算）


| 维度          | 说明                                                                      |
| ----------- | ----------------------------------------------------------------------- |
| **触发前提**    | 患者与挂号要素已选                                                               |
| **主路径步骤**   | 患者分流（有 `patientId` 直用 / 无则身份证命中复用 / 未命中新建；复用时按 **BR-REG-018** 回写联系电话、按 **BR-REG-019** 回写地址全文）→ 写前校验就诊可创建 → **（仅请求含 `slotId` 时按 BR-REG-016 占该号源）** → **开立门诊就诊时携带列表展示所需快照**（患者/科室/号源/挂号员、**若已选医师则含医师姓名快照及活动 `REGISTRATION_DOCTOR` 参与者**，供挂号QueryService 列表查询首写意向医生列及医生站 **BR-DOR-009** 待诊可见性）；就诊 **初态为 `PLANNED`（待挂号）** → 按定价在真实 `encounterId` 下生成 **ChargeItem** 并执行预结算门禁 → 读取落库 **ChargeItem.amount** 装配支付草稿并创建草稿 → 响应须含 **`encounterId` 与 `paymentDraftId`** → 刷新挂号QueryService 列表查询 |
| **关键分支**    | 支付草稿 `displayAmount`/`tenderedAmount` 与响应 `totalAmount` 须与已落库 **ChargeItem.amount** 一致，禁止仅用 `ChargeItemDefinition` 写支付头；挂号科室按前端所选值参与主数据校验与就诊创建（不等同于建档机构） |
| **失败分支**    | 必填缺失（EX-REG-001）；主数据无效（EX-REG-002）；规则拒绝创建（EX-REG-003）；预结算失败（EX-REG-004） |
| **查询侧说明** | ClinicRegister 列表出现或更新该行                                                |


---

### IF-REG-07 支付挂号费（正式结算）


| 维度          | 说明                               |
| ----------- | -------------------------------- |
| **触发前提**    | 预结算会话有效                          |
| **主路径步骤**   | 基于 `paymentDraftId` 校验可结算 → 按草稿 `charge_item_ids` **复用**预结算已生成的 **ChargeItem**（不重复创建）→ 幂等补齐就诊挂账账户 → 支付明细 `accountId` 与挂号费 **ChargeItem.account_id** 对齐 → **医保 merge 与扫码挂接** → **终态校验明细合计=号费**（**BR-REG-009** / **BR-OPC-024**）→ 固化支付流水 → 收费明细置已结算 → 就诊 **`PLANNED`→`REGISTERED`** →（可选）开票 → 刷新列表 |
| **失败分支**    | EX-REG-004                       |
| **查询侧说明** | 列表行金额、支付与票据状态刷新                  |


---

### IF-REG-08 门诊挂号列表查询


| 维度          | 说明                                               |
| ----------- | ------------------------------------------------ |
| **触发前提**    | 查询条件（日期、关键字等）；**关键字**语义：就诊业务号 `bus_no` **或** 患者姓名（含拼音/五笔），与收费窗一致；**不含**诊断描述 |
| **主路径步骤**   | 分页返回列表；**排除 `ENTERED_IN_ERROR`（未收费取消）**；**保留 `CANCELLED`（退号完成）**，且行内 `statusText` **固定为「退号」**（禁止展示枚举默认「已取消」）；**每行包含退号弹窗所需的支付与费用摘要**，避免再发起单独「退号前预查明细」接口；对 **`status_enum=PLANNED`** 的行，应用层 **批量**解析仍为草稿的支付流水，装配 **`paymentDraftId`**，供收费窗从列表直接调用 IF-REG-07；日期窗按 **挂号发生时刻**（号序/建档时刻语义）过滤，**不得**按「就诊结束时刻」筛当日未完诊记录；**行内「挂号费」金额**以写模型 **收费明细**（与预结算锚点一致）为准，应用层对本页就诊 **批量**查询后装配，**不**直接取QueryService `account_amount`（该列为QueryService 侧多源摘要）；打印次数/医生列仍由QueryService事件物化；**列表行须呈现患者性别（编码与展示文案）及就诊状态枚举码与状态文案**——与 `ClinicRegister` QueryService 组装一致，性别展示文案缺省时由应用层按行政性别编码解析；**已缴费行回显收费人 `cashierName`（支付 `enterer_id`）**；**行内「费用性质」`contractName` 取就诊优先账户合同名（有医保户优先医保，= 挂号身份），不跟挂号费账单挂账账户或支付渠道** |
| **失败分支**    | EX-REG-104                                       |
| **查询侧说明** | 纯展示                                              |


---

### IF-REG-09 退号可否校验


| 维度          | 说明                        |
| ----------- | ------------------------- |
| **触发前提**    | 用户发起退号意图                  |
| **主路径步骤**   | 校验就诊存在且类别为 **`AMB`**；**BR-REG-015** 账单门禁；**BR-REG-008**：仅 **`REGISTERED`/`TRIAGED`** 可退；**`PLANNED` 不可退**；**`IN_PROGRESS`/`ON_HOLD`/`DISCHARGED`** → 提示「请让患者去医生处取消接诊后再退号」（与账单无关） |
| **失败分支**    | 就诊不存在或类别非门诊 → EX-REG-005 / 类别门禁语义；收费未收口 → `CANCEL-BIZ-410`～`412`；已接诊未取消 → `CANCEL-BIZ-416` |
| **查询侧说明** | 无                         |


---

### IF-REG-10 退号提交


| 维度          | 说明                                     |
| ----------- | -------------------------------------- |
| **触发前提**    | IF-REG-09 通过                           |
| **主路径步骤**   | 再次确认可退 → 取消 Encounter →（若已付挂号费）进入退款窗口 → 申请逆向支付单 → **`completeRegistrationCancelRefund` 整单收口** → `returnRegistrationRefundChargeItems` 收口挂号收费明细 → 按需冲红发票；**若启用 booking**：尾调用 `onRegistrationCancelled`——默认（`RELEASE_SLOT_ONLY`/`CANCEL_APPOINTMENT`）回补号源并**软删**门诊预约行，释放 `uk_wkf_appt_patient_service_day`，允许同日同服务再挂；`KEEP_SLOT` 仅解绑就诊（详见 [booking 概要 §8.3](../../../模块/zxplat-module-booking/01-概要设计.md)） |
| **支付编排（独立）** | **仅全退、原路返回**：读原支付渠道明细 → `requestOutpatientRefund`（带 `contractNo` + `paymentDetails`）→ **`completeRegistrationCancelRefund`**（原单保持 `SUCCESS`，逆向单 `REFUNDED`，**无半退**）；失败时 `abortOutpatientRefundAttempt`；口径对齐 **BR-REF-011**；详见 **DM-FIN-002** §6.1.8 |
| **失败分支**    | EX-REG-005 / EX-REG-006                |
| **查询侧说明** | 列表行状态与金额刷新；退费完成事件由逆向单发布一次（日结不计双倍）                             |


---

### IF-REG-11 补打挂号单


| 维度          | 说明                   |
| ----------- | -------------------- |
| **触发前提**    | 选中已挂号记录              |
| **主路径步骤**   | 按QueryService 查询挂号单展示数据并交付打印   |
| **失败分支**    | 记录不存在或无权限            |
| **查询侧说明** | RegistrationSlip QueryService |


---

### IF-REG-15 修改挂号科室


| 维度          | 说明                   |
| ----------- | -------------------- |
| **触发前提**    | 挂号列表中待挂号至就诊中的记录（**BR-REG-023**） |
| **主路径步骤**   | 选择目标门诊科室 → 校验状态、科室启用且有变化、同日同科防重（**BR-REG-011**）→ 改就诊科室 → 挂号费归属科室同步 → 若已医保挂号且未上传费用、未结算，则撤销后按新科室重挂 → 刷新列表 |
| **关键分支**    | 自费或未医保挂号只改院内；已上传费用或已结算拒绝并提示先取消或退号；当日读卡缓存过期提示重新读卡 |
| **失败分支**    | 必填缺失、科室无效、状态不允许、科室未变化、医保重挂失败（EX-REG-007）；失败时院内修改回滚 |
| **查询侧说明** | 列表行科室刷新 |


---

### IF→DM 追溯摘要（评审用）


| IF                    | 业务语义锚点（不写实现方法名）              |
| --------------------- | ---------------------------- |
| IF-REG-01 / IF-REG-04 | Organization 门诊科室查询语义        |
| IF-REG-01 / IF-REG-05 | Practitioner 门诊医生查询语义        |
| IF-REG-03 / IF-REG-06 | Patient 按身份证复用或新建语义           |
| IF-REG-06             | Encounter 开立门诊就诊 + 防重校验语义    |
| IF-REG-08             | ClinicRegister QueryService §3 / §4   |
| IF-REG-09 / IF-REG-10 | Encounter 取消门诊就诊 + 收费域退费编排语义 |
| IF-REG-11             | RegistrationSlip QueryService         |
| IF-REG-15             | Encounter 更正挂号科室 + 挂号费归属科室同步     |


---

## 5. 业务异常与用户提示


| 语义代码       | 场景         | 用户提示方向              | 可重试 |
| ---------- | ---------- | ------------------- | --- |
| EX-REG-001 | 挂号提交必填缺失   | 补全后再提交              | 是   |
| EX-REG-002 | 主数据无效或停用   | 重新选择或刷新主数据          | 是   |
| EX-REG-003 | 就诊创建被拒绝    | 说明原因并引导更换科室/医生或稍后重试 | 视原因 |
| EX-REG-004 | 收费或预结算失败   | 说明是否需回滚挂号；必要时人工处理   | 视原因 |
| EX-REG-005 | 退号不允许      | 说明不可退原因（含 BR-REG-015 待收/已收/退费中）             | 否   |
| EX-REG-006 | 退号执行冲突或拒绝  | 刷新列表后重试或人工          | 是   |
| EX-REG-101 | 患者检索失败     | 稍后或调整关键字            | 是   |
| EX-REG-102 | 主数据加载失败    | 刷新页面                | 是   |
| EX-REG-103 | 挂号后列表未及时刷新 | 稍后从就诊记录确认           | 是   |
| EX-REG-104 | 列表查询失败     | 缩小条件或稍后             | 是   |
| EX-REG-105 | 退号后列表刷新失败  | 手动刷新                | 是   |
| EX-REG-007 | 修改挂号科室被拒绝或医保重挂失败 | 说明原因：状态不允许、科室未变、已结算须先退号、缺医保编码或须重新读卡 | 视原因 |


---

## 6. 事件语义与消费方

> 事件英文名与载荷以各 DM §5 为准；此处仅标挂号场景触发相位。


| 事件语义（自然语言 / DM 英文名以 DM 为准）                     | 触发相位（IF）        | 典型消费方       |
| ---------------------------------------------- | --------------- | ----------- |
| 门诊就诊已计划 / OutpatientRegistrationPlannedEvent   | IF-REG-06 创建就诊后（**`PLANNED` 待挂号**） | 挂号列表、叫号；**门诊信息**QueryService 同步消费展示快照（含 **`pyStr`/`wbStr`**，见 `DM-ADM-002` v1.4、`DM-ADM-002 §6.2 EncounterQueryService（OutpatientEncounterContextQueryModel）` v2.9），支撑医生站 **IF-DOR-01** 列表关键字；**医生站待诊单队列不依赖 `PLANNED` 态**（见 `AS-OP-DOR-001`） |
| 挂号收费明细创建/退费、账户开户/余额变更、门诊支付结清、挂号单已打印等事件 | IF-REG-06/07/11 及列表刷新 | **挂号列表 QueryService**金额/账户/打印次数等（与挂号单QueryService 并行查询） |
| 费用明细与支付结算相关事件                                  | IF-REG-07       | QueryService、票据、对账   |
| 门诊就诊已取消 / OutpatientRegistrationCancelledEvent | IF-REG-10       | 列表、号源释放编排   |
| 退款完成类语义                                        | IF-REG-10       | QueryService、对账      |


---

## 7. 非功能性需求


| 类别           | 要求                                                                                                                    |
| ------------ | --------------------------------------------------------------------------------------------------------------------- |
| **性能（经验目标）** | IF-REG-02 ≤800ms；IF-REG-01 ≤1500ms；IF-REG-06 ≤2000ms；IF-REG-08 ≤1000ms；IF-REG-07 / IF-REG-10 ≤2000ms；IF-REG-09 ≤800ms |
| **幂等**       | IF-REG-06 短时重复提交应幂等或返回同一预结算结果；IF-REG-07 同会话重复支付应幂等；IF-REG-10 重复退号应返回已处理语义                                             |
| **并发**       | 挂号占号、支付、退号以状态机为准，冲突时返回可理解原因                                                                                           |
| **重试**       | 主数据类读接口可指数退避少量重试；写接口由用户确认后重试，避免自动双写                                                                                   |
| **一致性**      | 挂号创建就诊对外强一致；退费跨域编排对外呈现一致可查状态                                                                                          |


---

## 8. 测试要点（业务视角）


| 类别     | 要点                                      |
| ------ | --------------------------------------- |
| **正向** | 检索→填单→确认挂号→列表可见；支付→列表金额与状态正确；退号前后列表一致   |
| **异常** | 必填不全、主数据停用、重复挂号规则拦截                     |
| **规则** | BR-REG-008 不可退拦截；BR-REG-009 支付与票据失败组合策略；**BR-REG-023** 改科室状态、已结算拒绝、医保重挂 |
| **并发** | 双开窗口同时对同一就诊退号                           |


---

## 9. 扩展：BPC 扫码支付（可选 Module）

> **真源**：[01-概要设计](../../模块/zxplat-module-unionpay-bpc/01-概要设计.md) · [模块/zxplat-module-unionpay-bpc/03 §3.10～§3.13](../../模块/zxplat-module-unionpay-bpc/03-详细设计与编码指导.md) · **成码**：[03 DEP-REG-01](../../模块/zxplat-module-unionpay-bpc/03-详细设计与编码指导.md)

| 维度 | 说明 |
| --- | --- |
| **适用 IF** | **IF-REG-07** 支付挂号费 |
| **触发** | 结算窗 **「扫码支付」** 且 `ThirdPartyPaymentExtension.isEnabled()` |
| **前端（通道 C）** | `prepareConsumerScan` → OpenHIS.Win consumer-scan → **IF-REG-07** 一次提交 |
| **settle 请求** | `paymentDetails` 含 `{ payChannelCode: SCAN_PAY, amount }`；**顶层** `clientScanResult` |
| **编排** | IF-REG-07 AppService **内** `applyClientScanResult` → 扫码行替换 **`WECHAT`/`ALIPAY`/`BANK_CARD`** → 领域 settle |
| **双存** | BPC 原值 `WEIX`/`ZFBA`/`UPAY` → `upc_bpc_order.pay_type`；HIS `pay_enum` 语义码（见 DM-FIN-002 §9.3） |
| **现金** | `CASH`，不经 BPC |
| **手工微信/支付宝** | `wechatAmount`/`alipayAmount` → **`WECHAT`/`ALIPAY`**，不经 BPC、无 `upc_bpc_order` |
| **失败** | Win/BPC 失败不 settle；EX-REG-004 可重试/改现金 |
| **半成功** | BPC 已成功但 settle 失败 → **重传完整** `clientScanResult` 补偿；禁止重复 C |
| **退号原路退（IF-REG-10）** | 与门诊退费同构：`submitScanRefund` 按 BPC `txnAmtFen` 退扫码段 → 顶层 `clientRefundResult` → `applyClientRefundResult`（等额/幂等）→ 本地退号；半成功凭证缓存与院内成功清缓存同 **AS-OP-FIN-003 §8.1** |
| **未装模块** | 不展示「扫码支付」；纯现金/记账不受影响 |

---

## 10. 关联文档与变更历史

### 9.1 关联文档


| 文档                                                                           |
| ---------------------------------------------------------------------------- |
| `docs/领域/DomainModel/DM-ADM-001-Patient-患者.md`                 |
| `docs/领域/DomainModel/DM-ADM-002-Encounter-就诊.md`               |
| `docs/领域/DomainModel/DM-ADM-004-Organization-机构科室.md`          |
| `docs/领域/DomainModel/DM-ADM-005-Practitioner-医务人员.md`          |
| `docs/领域/DomainModel/DM-FIN-001-Contract-费用性质.md`              |
| `docs/领域/DomainModel/DM-ADM-007-HealthcareService-医疗服务.md`     |
| `docs/领域/DomainModel/DM-ADM-010-ChargeItemDefinition-收费项定价.md` |
| `DM §6.2（DM-ADM-002 §6.2 `EncounterQueryService#pageClinicRegister``                |
| `DM §6.2（DM-ADM-002 §6.2 EncounterQueryService（OutpatientEncounterContextQueryModel）`（医生站就诊列表关键字口径） |
| `DM §6.2（DM-ADM-002 §6.2 `EncounterQueryService` 挂号单查询`                 |
| `docs/应用/zxplat-application/AppService/AS-OP-DOR-001-门诊医生站就诊管理.md`（下游接诊场景）        |
| `docs/模块/zxplat-module-unionpay-bpc/01-概要设计.md`（BPC 扫码支付扩展，§6） |


| 版本 | 日期 | 变更摘要 |
| --- | --- | --- |
| v2.45 | 2026-10-13 | 新增 **IF-REG-15** / **BR-REG-023**：挂号列表修改科室。已医保挂号且未上传费用时撤销后按新科室重挂；已结算拒绝。代码先行回写。对齐 **SDD v3.39**、**DM-ADM-002 v1.51**、**DM-ADM-011**。 |
| v2.44 | 2026-10-12 | **BR-REG-021**：澄清院点「身份证可选」覆盖范围含**患者管理**建档/改档（`patient-id-card.config`，与挂号同开关口径）。 |
| v2.43 | 2026-10-12 | **BR-REG-011** 院点放宽：平台策略 Bean + 院点常量写死注入（国基；不配 yml）；同日同科可多挂。对齐 **SDD v3.38**、**DM-ADM-002**。 |
| v2.42 | 2026-10-09 | 新增 **BR-REG-022**：页旁挂号费按绑定 AD SALE；`serviceTypeFeeById` 全量 ACTIVE 构建（不随医师收窄丢价）。对齐 **SDD v3.37**。 |
| v2.41 | 2026-09-18 | 新增 **BR-REG-021**：年龄必填 + 证/生日/年龄前端联动；主档允许空身份证、禁止按年龄合成证件号；国基等院点仅覆盖「身份证可选」。 |
| v2.40 | 2026-09-07 | **BR-REG-020**：产品号源更名为「复诊0元号」；写侧 Gate **按 `name` 识别**（常量 `NAME_HISTORICAL_FOLLOW_UP`），种子默认 `bus_no=HS00000004`（不参与识别）。对齐 **SDD v3.34**、平台种子 `V2__adm_ddl`。 |
| v2.39 | 2026-08-31 | **BR-REG-009** / IF-REG-07：扫码挂接后终态校验明细合计=号费；对齐 **AS-OP-FIN-002 · BR-OPC-024** 阶段 B |
| v2.38 | 2026-08-22 | **BR-REG-020**：产品号源「历史号复诊」（0 元）；同科室、接诊后 72h 内复诊资格写侧 Gate（`REG-BIZ-420`）；平台种子写入 `V2__adm_ddl`（`HS_HIST_FOLLOW_UP` + 定价 + 岗位可接诊）。对齐 **SDD v3.33**。 |
| v2.37 | 2026-08-11 | IF-REG-10：启用 booking 时退号尾调用须软删预约行（释放同日同服务防重键，可再挂）；对齐 booking 概要 §8.3 / GAP-BKG-001 v1.2。 |
| v2.36 | 2026-08-06 | **BR-REG-008**：退号仅 `REGISTERED`/`TRIAGED`；已接诊/暂离/完诊须先 **AS-OP-DOR-001 IF-DOR-06** 取消接诊；新增 `CANCEL-BIZ-416` 提示。对齐 **DM-ADM-002 v1.41**、**SDD v3.32**。 |
| v2.35 | 2026-08-03 | **BR-REG-001**：退役全局开关 `registrationDoctorRequired`（平台种子不再写入，存量由 health `V13` / 院点 `V4` 删除）；挂号医生统一可选；接诊后可见性对齐 **AS-OP-DOR-001 v1.17 / BR-DOR-009**（仅 `ADMITTER`）。详设 **SDD v3.31**。 |
| v2.34 | 2026-08-02 | **BR-REG-019**：IF-REG-06 请求增 `address`（地址全文）；新建随建档写入；复用非空且不同则 `updatePatientAddress`；空不覆盖。对齐 **SDD v3.30**。 |
| v2.33 | 2026-08-02 | **双账户开立**：人人个人现金自费户；省/市医保或工伤另开 `MEDICAL_INSURANCE`；挂号费按「医保支付挂号费」挂户；历史单户不强制修正。对齐 **DM-ADM-009 v1.3**、收费转自费 **AS-OP-FIN-002** |
| v2.32 | 2026-08-01 | §9：退号扫码原路退金额=BPC `txnAmtFen`；半成功凭证缓存/收口对齐 **AS-OP-FIN-003 v1.0.15**；对齐 **SDD v3.29** |
| v2.31 | 2026-07-30 | **BR-REG-015** 收敛：退号仅校验非挂号锚点账单三态（`PLANNED`/`SETTLED`/`REFUNDING`）；撤销 `CANCEL-BIZ-413`～`415` 药耗发放门禁，药耗收口改由 **AS-OP-FIN-003 IF-REF-04** 承担。对齐 **SDD v3.28**。 |
| v2.30 | 2026-07-29 | **BR-REG-018**：复用既有患者（有 `patientId` 直用 / 无则身份证命中）时，非空表单联系电话与主档不一致则回写主档；对齐 **SDD v3.27**。 |
| v2.29 | 2026-07-29 | IF-REG-10：**原路返回**——退号逆向单须拷贝原支付 `contract_no` 与渠道明细（含统筹/个账/自付）；无明细硬拦。对齐 **SDD v3.26**、**AS-OP-FIN-003 BR-REF-011**。 |
| v2.28 | 2026-07-26 | IF-REG-08 **关键字**：就诊业务号 / 患者姓名（拼音、五笔）；**禁止**按诊断描述检索；与收费窗及 `EncounterContextCriteriaSupport#clinicRegisterSpecification` 一致。对齐 **SDD v3.25**、**DM-ADM-002 v1.37**。 |
| v2.28 | 2026-07-22 | **BR-REG-001**：绑定全局开关 `registrationDoctorRequired`（种子 `G`/默认 `0`）；IF-REG-01 下发该标志；必选时 IF-REG-06 Gate 拒绝未选医生。挂到科室可见性对齐 **AS-OP-DOR-001 v1.16 / BR-DOR-009**。详设 **SDD v3.25**。 |
| v2.27 | 2026-07-16 | **取消/退号分流**：待挂号窗口「取消」→`ENTERED_IN_ERROR`（列表排除）；退号→`CANCELLED` 且 IF-REG-08 `statusText=退号`；**BR-REG-008/011** 同步。对齐 **DM-ADM-002 v1.35**、**SDD v3.24**。 |
| v2.26 | 2026-07-14 | **BR-REG-014/016** 解绑：手动挂号号源类型仅按医师可接诊配置收窄，**禁止 ∩ 当日排班**；占号仅当请求显式 `slotId`（点选号源）或预约转挂。对齐 **SDD v3.23**、booking 概要/功能清单。 |
| v2.26 | 2026-07-16 | **BR-REG-017**：零元挂号缴费跳过开票，对齐 **BR-INV-008** / `InvoiceAfterPaymentOrchestration` |
| v2.25.2 | 2026-07-13 | **BR-REG-015**：`CANCEL-BIZ-415` 药房在途提示改为「先完成门诊退费」（与窗口退费收口口径一致）。 |
| v2.25 | 2026-07-13 | 新增 **BR-REG-015**：IF-REG-09/10 退号前须账单/药品/耗材全收口（排除挂号锚点与诊疗执行）；对齐 **SDD v3.22**。 |
| v2.24 | 2026-07-04 | **BR-REG-011** 修正：占名额状态**含待挂号 `PLANNED`**（取消支付弹窗后仍阻断重复预结算）；仅 **`CANCELLED` 退号/撤销**释放。对齐 **DM-ADM-002 v1.27**、**SDD v3.19**。 |
| v2.23 | 2026-06-23 | §9 对齐 ADR/03：SCAN_PAY + clientScanResult；AppService 内 applyClientScanResult；语义码 |
| v2.22 | 2026-06-23 | §9：UI 文案「扫码支付」；payType 双存 220100/220200/220300；半成功补偿；对齐 unionpay-bpc P0 定稿 |
| v2.21 | 2026-06-12 | IF-REG-06：开立门诊指定医师时写入活动 **`REGISTRATION_DOCTOR`** 参与者（对齐 **DM-ADM-002 v1.25** / **AS-OP-DOR-001** **BR-DOR-009**）；详设 **SDD v3.18**。 |
| v2.20 | 2026-06-10 | IF-REG-01/05：医生下拉限定门诊科室（CLINIC）范围内有激活医师岗的在职人员；未选科室时排除仅住院科室挂岗医师；**BR-REG-005** 增补该口径 |
| v2.19 | 2026-05-28 | 六维对账：十方法与 SDD §3.1、IF-REG-10 `completeRegistrationCancelRefund` 与代码一致 |
| v2.18 | 2026-05-28 | ReadModel 废止后对齐 zxplat 实现 |
| v2.16 | 2026-05-10 | 新增 **BR-REG-011**：门诊挂号 **同日 + 同患者 + 同挂号科室** 防重（占名额含诊毕；`PLANNED` 未交费不占、`CANCELLED` 退号释放）；IF-REG-06 绑定该规则。对齐 **DM-ADM-002 v1.7**、**AS-OP-FIN-001-SDD v3.14**。 |
| v2.15 | 2026-05-04 | 对齐 **`AccountTypeCode`**：IF-REG-06/07 门诊开户与结算回填锚点为 **`PERSONAL_CASH_ACCOUNT`**（废止原 `adm_account.type_code=REG` 叙述）；详设 **AS-OP-FIN-001-SDD v3.13**、**DM-ADM-009 v1.1**。 |
| v2.14 | 2026-05-02 | §6：`OutpatientRegistrationPlannedEvent` 消费方补充 **门诊信息**QueryService 检索码快照（`pyStr`/`wbStr`）与 **IF-DOR-01** 关键字语义；§9.1 关联 `OutpatientEncounterContextQueryModel` QueryService。对齐 **DM-ADM-002 v1.4**。 |
| v2.13 | 2026-05-02 | 明确挂号生命周期：**预结算后 `PLANNED`、结算后 `REGISTERED`**；IF-REG-08 列表对 `PLANNED` 行回传 `paymentDraftId`；术语表区分待挂号/已挂号与医生站候诊口径；§6 消费方与 `AS-OP-DOR-001` 对齐。详设 `AS-OP-FIN-001-门诊挂号-SDD.md` v3.11。 |
| v2.12 | 2026-05-02 | IF-REG-02：患者检索分页行增补性别编码与展示、周岁年龄、出生日期（详设 `AS-OP-FIN-001-门诊挂号-SDD.md` v3.10 §3.3）。 |
| v2.11 | 2026-05-02 | IF-REG-01：`loadRegisterFormInit` 响应增补性别下拉，与患者建档页行政性别值集同源；详设见 `AS-OP-FIN-001-门诊挂号-SDD.md` v3.9 §3.2。 |
| v2.10 | 2026-05-02 | IF-REG-08：列表行补充性别（编码与展示）与就诊状态枚举码/文案的契约语义；详细字段与装配见 `AS-OP-FIN-001-门诊挂号-SDD.md` v3.8 §3.9。 |
| v2.9 | 2026-05-02 | IF-REG-08：列表行挂号费展示改为应用层按 **ChargeItem** 锚点批量回显（与QueryService `account_amount` 职责分离）；配套 `DM-ADM-002 §6.2 `EncounterQueryService#pageClinicRegister`` v2.6 §3.4.1、`AS-OP-FIN-001-门诊挂号-SDD.md` v3.7。 |
| v2.8 | 2026-05-02 | IF-REG-06/07：支付金额以 **ChargeItem** 为唯一事实源（草稿与 `totalAmount` 对齐落库明细）；IF-REG-07 正式结算复用预结算已创建的收费明细、禁止重复创建。对齐 `AS-OP-FIN-001-门诊挂号-SDD.md` v3.5。 |
| v2.7 | 2026-05-02 | 对齐挂号列表物化：IF-REG-06 明确可选医师姓名快照；IF-REG-08 明确金额/打印/医生列事件口径；§6 增补列表与挂号单并行QueryService 消费事件后刷新方说明。配套QueryService `DM-ADM-002 §6.2 `EncounterQueryService#pageClinicRegister`` v2.4、领域事件 `DM-ADM-002 §6.2 EncounterQueryService` v1.1、SDD v3.2。 |
| v2.6 | 2026-05-01 | 对齐 IF-REG-07 账户口径：正式结算链路在 `openEncounterAccounts` 后必须解析默认结算账户，并将 `accountId/accountCode` 回填到支付明细，避免 `fin_payment_rec_detail.account_id` 空值落库失败。（**v2.15** 起默认户类型码以 **`AccountTypeCode.PERSONAL_CASH_ACCOUNT`** 为准。） |
| v2.5 | 2026-05-01 | 对齐 IF-REG-07 最新实现：支付请求从契约层移除 `encounterId`，正式结算链路以 `paymentDraftId` 作为唯一定位锚点。 |
| v2.4 | 2026-05-01 | 对齐挂号预结算时序：IF-REG-06 明确“先创建 Encounter，再以 encounterId 创建支付草稿”的编排约束，避免支付草稿 `encounterId` 为空。 |
| v2.3 | 2026-05-01 | 明确机构来源双口径：新增 BR-REG-010；补充 IF-REG-03/06 语义——患者新建建档机构取当前登录机构，挂号就诊科室仍按前端所选值。 |
| v2.2 | 2026-05-01 | 对齐最新代码：将首次建档从独立接口语义调整为 `IF-REG-06` 前置患者分流（`patientId` 复用 / `idCardNo` 命中复用 / 未命中新建），同步修订 BR-REG-006/007 与 IF 追溯口径 |
| v2.1 | 2026-04-30 | 重构 §3 Gap：删除"链路未接通/试算规则"等模糊项，仅保留QueryModel缺失；Gap-REG-001/002 具体到 `ClinicRegisterQueryModel`/`RegistrationSlipQueryModel` 实体+Repository+Query 方法缺失 |
| v2.0 | 2026-04-29 | 对齐 AS SKILL §1～§9 骨架；合并「退号前预查」入 IF-REG-08，全文 IF 重编号为 IF-REG-01～11；§4 改为维度表编排；补 §7/§8；删除原 §10；配套修订 `AS-OP-FIN-001-门诊挂号-SDD.md` IF 编号        |
| v1.1 | 2026-04-25 | 上一版：IF-REG-01～12 与逐步骤编排表                                                                                                                   |


---

**文档负责人**：（待填） · **评审**：（待填）