# 住院医生站 · 医嘱开立 · 应用服务设计（AS）

> **文档职责**：说明住院医生站「开立医嘱」模块的**接口契约（IF）**、业务语义、涉及聚合与 QueryService、规则（BR）、编排意图；作为上游契约供详细设计与实现对齐。
>
> **本文必须写**：IF 编号；用户动作与读写属性；关键校验与状态变化；Gap；异常口径；事件语义；非功能约束；业务向测试要点。
>
> **本文禁止写**：具体类名/应用方法名；Request/Response 字段明细；包路径；Mapper；伪代码；写库顺序；QueryService 字段组装规则。
>
> **页面边界**：住院医生站主界面「开立医嘱」标签——工具栏（新增、签发、撤回、删除、并组/拆组）、**长期/临时/出院带药**分轨筛选、医嘱主表（行操作含**执行记录**）、行内编辑卡片（项目检索、诊断绑定、费用性质、用法频次剂量、成组药品多行、保存/签发/关闭）、**停嘱** / **取消停嘱**（均仅长期）、**改开立/停嘱时间**、**出院医嘱**独立入口；**左侧患者列表**见 **AS-IP-DOR-001**；诊断见 **AS-IP-DOR-002**；病历/报告/中医处方/组套模板等见 §1.4。
>
> **覆盖声明**：完整契约以 **§2 IF-IP-DOR-10～24、25、26、27、28** 为准（**IF-IP-DOR-13 废止**，费用性质下拉并入 **AS-IP-DOR-001** 待增补口径，与门诊 **AS-OP-DOR-001** 对齐）；业务能力与 **AS-OP-DOR-003** 同构并叠加住院差异（**TherapyType** 长/临/**出院带药**分轨、医生不开耗材、长期无账单/发放、**住院皮试联动 §1.8**、**嘱托医嘱 §1.9**、**出院带药 §1.10**、**首日用量 §1.11**、**分次用量不一致 §1.14**、**三时间轴与改业务时间 §1.12**、**长嘱改量 §1.13**、停嘱/取消停嘱、出院医嘱 + **EncounterHistory**、**检验/检查/治疗申请单**）；编排规则与 ZXPlat 目标态 DM 对齐。**HTTP/成码细节**见 **AS-IP-DOR-003-SDD**，本文不展开。
>
> **UI 说明**：住院医嘱 Tab 已按 **门诊医嘱 Tab 同构** + 长/临/**出院带药**筛选/停嘱/**取消停嘱**/出院入口落地；**出院医嘱**独立对话框（出院方式、计划出院时间、原因；出院方式为死亡时另填死亡时间、可选死亡原因）调用 **IF-IP-DOR-21**；**检验/检查/治疗申请单**同门诊快捷入口（**IF-IP-DOR-23/24**，**仅临嘱**）；列表出院行展示见 **IF-IP-DOR-10 增补**（**不**使用 `contentSummary` 拼接出院明细）；**嘱托医嘱**选目录「嘱托」后录入自由文本（**BR-IP-ORD-028**）；**出院带药** Tab 仅开药品，类型列展示「出院带药」（**BR-IP-ORD-029**）；**长期药品**编辑卡可填**首日用量**（**BR-IP-ORD-030**；习惯称「首日」，语义为**首次**用量）；**长期西药/中成药**可勾选**用量不一致**并按频次展开多剂量框，落库 `doseSchedule`/`quantitySchedule`（**BR-IP-ORD-037**；打印可不改）；列表**展示开立时间+开立人、停嘱时间+停嘱人**（签发时间不展示，**BR-IP-ORD-031**）；**改开立/停嘱时间**（**IF-IP-DOR-26**）已落地；**长期皮试药换批「追加皮试」**（**IF-IP-DOR-27**）已落地；**长嘱「改量」**（**IF-IP-DOR-28**，停旧+同药重开草稿 + `based_on` 链，**新嘱待签发**）已落地；命中院点一盒一点口服时列表展示剩余/已吃（**BR-IP-EXE-038** 同源）已落地；**医技执行进度**（执行科室=TECH 的诊疗行，医嘱内容蓝色扩展行展示「医技已执行」/「医技待执行」**并同段展示执行科室名**，**不**加独立列；`tech*` / `performLocation*` 同 **AS-MT-EXE-001** / **BR-IP-ORD-038**）已落地；**执行记录**（列表行操作「执行记录」只读弹窗：默认**全部**，可筛已执行/不执行/取消执行，**不含待执行**；按该行 `requestId`，成组含成员；**复用 IF-IP-EXE-01 读列表，不新开写 IF**）已落地。
>
> **编码**：AS-IP-DOR-003 · **版本**：v1.58 · **日期**：2026-10-15

---

## 1. 场景概述

### 1.1 目标与边界


| 维度 | 说明 |
| --- | --- |
| **在内** | **住院皮试入口（§1.8）**：药品行勾选**皮试**（`skinTestFlag`）→ **IF-IP-DOR-14** 后台联动生成「皮试检查」`ServiceRequest`（**不**建 `ChargeItem`，**BR-IP-ORD-024**）；下游护士登记与执行后计费见 **AS-IP-NUR-011**；**嘱托医嘱（§1.9）**：诊疗目录固定项「嘱托」（`category_code=52`）→ 医生录入自由文本写入 `entrust_content`，长/临均可，**BR-IP-ORD-028**；**出院带药（§1.10）**：第三种 `TherapyType.DISCHARGE_MEDICATION`，仅药品、开立同临嘱，**BR-IP-ORD-029**；**首日用量（§1.11）**：仅**长期药品**可选填 `first_dose`/`first_dose_unit_code`（**BR-IP-ORD-030**；语义为**首次**用量）；**三时间轴与改业务时间（§1.12）**：开立/停嘱可改、签发仅追溯且不可改（**BR-IP-ORD-031**，**IF-IP-DOR-26**）；**检验/检查/治疗申请单**（仅**临嘱**，规则同门诊 **AS-OP-DOR-003 · BR-REQ-***，**IF-IP-DOR-23/24**）；载入当前住院就诊下**医生开立**的药品/诊疗医嘱统一列表（IF-IP-DOR-10，含申请单行、**皮试结果**、**`serviceCategoryEnum` 诊疗类目**、**嘱托行 `entrustContent`**、**出院带药行**、**长期药 `firstDose*`**、**开立/停嘱时间与开立人/停嘱人**）；分页查询患者**全类型医嘱**统一列表（IF-IP-DOR-22，单接口分 Tab：**当前医嘱** / **历史医嘱**，读口径与 IF-IP-DOR-10 同构；**是否按处方形式展示由前端处理**）；开立页初始化频次/用法术语（IF-IP-DOR-18）；**ProductDefinition** 目录分页（**仅药品+诊疗**，含**护理级别** category **26**、**嘱托**，IF-IP-DOR-11；**出院带药 Tab 实务仅选药品**）；绑定规则（IF-IP-DOR-12）；按诊疗定义查可执行科室规则（IF-IP-DOR-19）；批次**暂存**（IF-IP-DOR-14）、**签发**（IF-IP-DOR-15）、**签退**（IF-IP-DOR-16）、药品**组号**（IF-IP-DOR-17）；长期医嘱**停嘱**（IF-IP-DOR-20）、**取消停嘱**（IF-IP-DOR-25）、**改开立/停嘱时间**（IF-IP-DOR-26）；**出院医嘱**开立（IF-IP-DOR-21）：`ServiceRequest`（出院类、临时）+ **EncounterHistory** 离院里程碑；**护理级别医嘱**（诊疗目录 category **26**，**仅长期**，**BR-IP-ORD-026**） |
| **在外** | 患者列表（**AS-IP-DOR-001**）；诊断（**AS-IP-DOR-002**）；护士划价与护士站校对/执行/不执行/取消执行（**Procedure** 与护士站 AS；**护理级别仅校对、不经执行 Tab**，见 **AS-IP-NUR-004 BR-IP-EXE-027**；**嘱托执行不计费**见 **BR-IP-ORD-028⑤**；**出院带药**执行出账与发放、**不写 `summary_no`/汇总过滤**、逐行发药与出院待发药门禁见 **BR-IP-ORD-029⑥**；**首日用量执行侧**槽位裁剪/`actualTime` 默认与首次槽换量见 **BR-IP-ORD-030⑥** 及 **AS-IP-NUR-004**、**AS-IP-PHA-001**、**AS-IP-NUR-010/PHA-002**、**AS-IP-NUR-002**）；已签发列表合并护士数据、转科/膳食/体位等**其它**特殊医嘱维护；**门诊开立嘱托**（由 **AS-OP-DOR-003** 禁开，本文仅声明住院侧可见可开）；**组套/医嘱模板**见 **AS-BAS-MST-017 · IF-BAS-OTL-07～08、10**；中医处方专页、收费窗口正式结算 |
| **涉及域** | MedicationRequest / MedicationDispense；ServiceRequest；ActivityDefinition；ProductDefinition（读）；ChargeItem / ChargeItemDefinition；Encounter / Condition；ServiceLocationTemplate；EncounterHistory（`cus_encounter_history`）；Account |
| **编排权威** | 以本文 **§2、§4** 为准 |


### 1.2 角色与关键动作


| 角色 | 关键动作 |
| --- | --- |
| **住院医生** | 在 **AS-IP-DOR-001** 选定患者 → 切换**长期/临时/出院带药** → 查看/刷新列表 → 检索药品或诊疗（**不选耗材**；**护理级别不在普通选品下拉**，经顶部「护理」快捷、`categoryEnum=26` 目录开立，**仅长期 + 频次 QD**，**BR-IP-ORD-026⑥**；**嘱托**从诊疗目录选固定项「嘱托」，长/临均可，录入自由文本，**BR-IP-ORD-028**；**出院带药 Tab 仅开药品目录项**，**BR-IP-ORD-029**）→ 维护用法频次剂量等 → **长期药品**可按需填**首日用量**（**BR-IP-ORD-030**）→ **长期西药/中成药**可勾选**用量不一致**并按频次展开多剂量框（**BR-IP-ORD-037**）→ **药品行勾选皮试**（长/临；出院带药**软约定不勾**，**BR-IP-ORD-029⑤**；**不**手工另加「皮试检查」诊疗行，**BR-IP-ORD-024**）→ 暂存或签发 → 必要时签退、删除、并组 → 对**长期**医嘱**停嘱** / **取消停嘱**（**换护理级别须先停旧再开新**，**BR-IP-ORD-026**）→ 对**长期药品**可**改量**（**IF-IP-DOR-28**，停旧+同药重开）→ 必要时**改开立时间或停嘱时间**（**IF-IP-DOR-26**，**BR-IP-ORD-031**）→ 开立**出院医嘱** |
| **系统** | 列表仅 **`GenerateSource.DOCTOR_PRESCRIPTION`**；**长/临/出院带药**暂存/签发**均仅**写请求、**不**建账单与发放（FIFO/费用在护士执行，**AS-IP-NUR-004**；**皮试检查**同其它诊疗临嘱在执行时计费；**嘱托例外**：护士执行**只写执行记录、不建账单**，**BR-IP-ORD-028⑤**）；皮试药暂存时**自动**联动 doctor-prescription「皮试检查」SR（**不**挂费）；**三种 therapy 均不校验库存**；出院开立校验吸氧/心电余量并写离院里程碑 |


### 1.3 术语与口径约定


| 术语 | 口径 |
| --- | --- |
| **与门诊同构** | IF-IP-DOR-10～19、14～17 分别对应 **AS-OP-DOR-003** 的 IF-DOR-10～19、14～17；差异见 **BR-IP-ORD-*** 与 §1.7 |
| **TherapyType** | 枚举 **`com.zxplat.domain.share.enums.TherapyType`**（`LONG_TERM` / `TEMPORARY` / **`DISCHARGE_MEDICATION`**），持久化 **`therapy_enum`**（String code）；药品申请与服务申请共用；**出院带药仅住院药品路径使用**（**BR-IP-ORD-029**） |
| **出院带药** | `therapy_enum=DISCHARGE_MEDICATION` 的住院药品医嘱；开立侧与**临时**同构（仅 Request、无处方有效期、不停嘱）；类型列展示「出院带药」；下游执行/发药见 **§1.10** / **BR-IP-ORD-029** |
| **门诊 ≈ 住院临时** | 门诊医嘱在语义上等价住院**临时**；门诊频次由产品约束为「立即」，**前端控制**，后端住院临时不强制 |
| **GenerateSource** | 医生开立写入 **`DOCTOR_PRESCRIPTION`**；列表与检索**仅展示医生来源**；**不展示** `NURSE_PRICING` |
| **停嘱 vs 取消停嘱 vs 不执行** | **停嘱**：须已开嘱校对后迁 `STOPPED`（**IF-IP-DOR-20**），写 `stop_time` + **停嘱人 `stopper_id`**，停嘱校对字段置空（待护士停嘱校对）；**取消停嘱**：须**尚未**停嘱校对（`stop_performer_check_id` 空），否则须护士先取消停嘱校对；恢复 **`VERIFIED`/`ACTIVE`**（**IF-IP-DOR-25**），清空 `stop_time`/`stopper_id`/停嘱校对两字段；**不执行**：执行域 **Procedure**，本文不收敛 |
| **三时间轴** | **开立** `req_opened_time`：首次暂存/直建写入；业务主轴（列表排序/展示/护士联动）；**可改**（**IF-IP-DOR-26**）。**签发** `req_authored_time`：点签发写入；**仅系统追溯**，列表**默认不展示**，**禁止修改**。**停嘱** `stop_time`：长期停嘱写入；业务右界；**可改**（已停嘱行，**IF-IP-DOR-26**）。详 **§1.12** / **BR-IP-ORD-031** |
| **开立人 / 停嘱人** | **开立人**：落库开立人列（药/诊 `requester_id`）；开立后**永不因改开立时间而变更**；列表「开立时间」后跟开立人姓名。**停嘱人**：新列 `stopper_id`，停嘱操作人写入；取消停嘱清空；改停嘱时间**不改**停嘱人；列表「停嘱时间」后跟停嘱人姓名。**不**单独落「改时人」 |
| **改业务时间** | **IF-IP-DOR-26**：一次请求改**开立时间或停嘱时间**之一（前端传时间种类）；直写 Request 对应列；**绝不**改 `req_authored_time`。**本期仅住院**；门诊改时间非目标（以后可同构扩展） |
| **申请单编辑** | **删旧重建取新号**：编辑时传旧 `AR` 号 → 软删旧 DRAFT 行 + Detail + 作废未计费费用 → **`getSeqByDay` 取新 `AR` 号**（**不沿用**旧号，**BR-IP-REQ-002**） |
| **出院过程数据** | 离院里程碑写入 **`EncounterHistory`**（表 **`cus_encounter_history`**），**不是**旧系统 `OrderProcess` 表 |
| **暂存 / 签发 / 签退** | 语义同 **AS-OP-DOR-003** §1.3；住院长期路径无账单/发放 |
| **嘱托医嘱** | 诊疗目录固定项，名称精确为 **「嘱托」**；`category_code=52`（术语「嘱托」）→ `ServiceRequest.category_enum=52`；`type_enum` 可用 **其他（5）**；定价 **0 元**、单位 **次**；**仅住院**长/临可开（门诊禁开）；医生自由文本写入 **`ServiceRequest.entrust_content`**；列表类型展示「嘱托 / 长期|临时医嘱」（类目名 + 长临），**医嘱内容**展示 `entrust_content`（**非**目录名）；校对/执行同常规诊疗，**不生成账单**（详 **§1.9** / **BR-IP-ORD-028**） |
| **首日用量（首次用量）** | 字段 `first_dose` / `first_dose_unit_code`（**DM-MED-003**）；习惯称「首日」，语义为**首次执行**用量，**不是**开立日每个频次点都换量；**仅长期药品**适用；开立 UI 按**计量单位**录入，**落库换算为最小单位**（与长期 `quantity`/`unit_code` 同口径）；临时/出院带药**禁止**；下游护士执行首次槽换量后与常规长嘱执行同路径，见 **§1.11** / **BR-IP-ORD-030**（承接 **AS-IP-NUR-004**） |
| **分次用量不一致** | 仅**住院长期西药/中成药**：医生可选「用量是否不一致」；不一致时按频次**每日执行次数**展开 N 个计量剂量框（顺序=当日执行点**早→晚**，**不存**时刻）；落库 `dose_schedule`（计量串）+ `quantity_schedule`（拆零串，与各剂量按**原换算**对齐）；单位仍用 `dose_unit_code` / `unit_code`。**schedule 皆空** → 完全走原 `dose`/`quantity`（零改动）；有值 → 展示与护士按槽取量走 schedule。与首日量**正交可并存**，首次槽优先首日量。见 **§1.14** / **BR-IP-ORD-037**；执行算量见 **AS-IP-NUR-004 · BR-IP-EXE-040** |
| **长嘱改量** | **IF-IP-DOR-28**：对长期**药品**停旧（或已停）后同药重开；新嘱 `based_on_table=med_medication_request`、`based_on_id=源嘱`；药品不可换、其余字段可改；多次改量链式；观察期不扣剩余。详 **§1.13** / **BR-IP-ORD-032**；剩余展示与待执行抑制见 **AS-IP-NUR-004 BR-IP-EXE-038** |
| **口服包装剩余** | 院点一盒一点口服长嘱读路径实时字段（已吃/剩余/预计领盒日）；医生站列表与护士执行列表同源；**不落表**（**BR-IP-EXE-038**） |
| **QueryService 能力** | 列表目标态 **InpatientOrderLookupLoader · InpatientOrderListSkeleton**；写前门控 **InpatientEncounterContextQueryModel**；过渡期允许域查询 + 装配（与门诊 IF-DOR-10 一致） |


### 1.4 本期范围与非目标


| 类别 | 内容 |
| --- | --- |
| **本期（契约）** | IF-IP-DOR-10～12、18～24、14～17、20（停嘱）、**25（取消停嘱）**、**26（改开立/停嘱时间）**、**27（追加皮试检查）**、**28（长嘱改量）**、21（出院医嘱）、22（全类型医嘱查询）；**申请单 IF-IP-DOR-23/24**；**嘱托医嘱（BR-IP-ORD-028）**；**出院带药（BR-IP-ORD-029，`TherapyType.DISCHARGE_MEDICATION`）**；**首日用量（BR-IP-ORD-030）**；**分次用量不一致（BR-IP-ORD-037）**；**医技执行进度展示（BR-IP-ORD-038）**；**三时间轴与人名展示（BR-IP-ORD-031）**；**长嘱改量（BR-IP-ORD-032）**；**同组频次一致（BR-IP-ORD-033）**；列表口服剩余展示（与 **BR-IP-EXE-038** 同源） |
| **非目标（改时间·门诊）** | 门诊医生站改开立/停嘱时间——本期**不**提供；以后若做可同构扩展，不在本文 IF 收敛 |
| **非目标（申请单）** | 手术/输血/病理等申请单；**长期医嘱申请单**；**出院带药申请单**；`doc_request_form` 头表 |
| **本期（UI）** | 住院医嘱 Tab：交互以门诊医嘱 Tab 为基线 + **长期/临时/出院带药** Tab/筛选 + **停嘱 / 取消停嘱** + **改开立/停嘱时间** + 出院医嘱入口 + **嘱托自由文本录入**；列表展示开立时间+开立人、停嘱时间+停嘱人（**不**展示签发时间） |
| **非目标（老代码有、新 UI/本期不收）** | 已签发列表合并护士划价；转科/膳食/体位等**其它**特殊医嘱（**护理级别**、**嘱托**、**出院带药**已在本期）；`reg-patient-*` 患者查询（归 **AS-IP-DOR-001**） |
| **非目标（门诊嘱托）** | 门诊医生站目录可见性与写路径禁开嘱托——契约落在 **AS-OP-DOR-003**（本文只约定住院侧） |
| **非目标（组套/模板）** | 组套抽屉列表/预览/另存**不**在本文 IF 收敛，由 **AS-BAS-MST-017 · IF-BAS-OTL-07～08、10** 提供；选中后仍调本文暂存/签发 IF |
| **非目标（护士站）** | 护士划价、校对、执行、取消执行、不执行、自动滚费 |
| **非目标（同站其他 Tab）** | 诊断录入、病历、报告、中医处方 |


### 1.5 领域清单（聚合 + 职责 + DM 路径）


| 聚合（域） | 在本场景中的职责（摘要） | DM 路径 |
| --- | --- | --- |
| **MedicationRequest / MedicationDispense** | 药品医嘱；长/临/**出院带药**分轨；**住院**暂存/签发**仅 Request**（发放在护士执行 FIFO，**AS-IP-NUR-004**；出院带药发放**不写 `summary_no`**，见 **BR-IP-ORD-029⑥**）；长期停嘱 | `DM-MED-003` / `DM-MED-002` |
| **ServiceRequest** | 诊疗医嘱；**嘱托**（`entrust_content` 正文，**BR-IP-ORD-028**）；**基于药品的皮试检查** companion 联动（**BR-IP-ORD-024**）；出院申请（`DISCHARGE`）；长期停嘱；**申请单**（`AR` 前缀 `prescription_no` + **Detail**） | `DM-WKF-004` |
| **BodyStructure / SpecimenDefinition** | 申请单部位/样本主数据 | `DM-DIA-001` / `DM-ADM-014` |
| **ActivityDefinition** | 诊疗定义（含固定项「嘱托」、`category_code=52`）；出院目录锚点 | `DM-WKF-001` |
| **ProductDefinition（QueryService）** | 开立目录（药+诊） | `DM-MED-001 / DM-WKF-001 / DM-ADM-008 §6.2 目录 QueryService` |
| **ChargeItem / ChargeItemDefinition** | **住院医生站不写**；护士执行时 **ORDER_EXECUTE** 新建（**AS-IP-NUR-004**）；门诊临嘱仍走开立侧草稿/升位 | `DM-ADM-011` / `DM-ADM-010` |
| **Encounter / Condition** | 就诊上下文、诊断绑定、出院时长校验 | `DM-ADM-002` / `DM-CLN-003` |
| **EncounterHistory** | 出院（及未来转科）离院/转科里程碑 | `DM-CUS-004-EncounterHistory-就诊历史.md` |
| **ServiceLocationTemplate** | 候选发药房、库存求交（同门诊） | `DM-CUS-006` |
| **Account** | 护士执行挂账（开立侧不要求自费账户） | `DM-ADM-009` |
| **Organization / Practitioner** | 开立科室、开方医生 | `DM-ADM-004` / `DM-ADM-005` |


### 1.6 业务规则（BR-*）


| 规则 ID | 描述 | 绑定 IF |
| --- | --- | --- |
| **BR-IP-ORD-001** | **统一目录查价**（同 **BR-ORD-001** / `ClinicalCatalogPriceResolvesupport）；**废止**租户定价来源门控与 `pricingSourceCode` 字段 | IF-IP-DOR-11 / 14 |
| **BR-IP-ORD-002** | 目录仅**有效**行；住院医生站检索 **product_table_name** 仅 **药品+诊疗**（**不含耗材**，**BR-IP-ORD-012**） | IF-IP-DOR-11 |
| **BR-IP-ORD-003** | **长期、临时、出院带药**医生开立**均不校验库存**（暂存不建发放，无「改前释放占用」） | IF-IP-DOR-14 |
| **BR-IP-ORD-004** | 列表/写路径仅 **`GenerateSource.DOCTOR_PRESCRIPTION`**；**不展示、不写入**护士划价来源 | IF-IP-DOR-10 / 14 |
| **BR-IP-ORD-005** | **长期**暂存/签发：**仅**持久化药品/诊疗 **Request**，**不**创建 **ChargeItem**、**不**创建药品 **Dispense** | IF-IP-DOR-14 / 15 |
| **BR-IP-ORD-006** | **住院长/临/出院带药对齐**：暂存/签发**仅**持久化 **Request**（**DRAFT→ACTIVE**、签署编码/处方号）；**不**建 **ChargeItem**、**不**建 **Dispense**、**不**在签发 FIFO；费用与发放由护士执行生成（**AS-IP-NUR-004 BR-IP-EXE-016/018**）；出院带药发放差异见 **BR-IP-ORD-029⑥**；**门诊临嘱不变**（仍 IF-DOR-14/15 草稿+签发 FIFO） | IF-IP-DOR-14 / 15 |
| **BR-IP-ORD-007** | 写编排顺序：**药品 → 诊疗**（**无耗材分支**） | IF-IP-DOR-14 / 15 |
| **BR-IP-ORD-008** | 删除：**长/临/出院带药**行均仅删请求（住院不在开立挂费）；诊疗删除同步子项；已收费不可删（同门诊 **BR-ORD-004/005**，费用锚在护士执行侧） | IF-IP-DOR-14 |
| **BR-IP-ORD-009** | **签退**：**住院长/临/出院带药药品**均仅 **signOff**；诊疗 **withdrawSigned**（**不**回滚开立侧 Dispense/Charge，因签发未建） | IF-IP-DOR-16 |
| **BR-IP-ORD-035** | **（废止）** 原「签退 · 医技双侧同态」硬拦已取消（协同 DD 软落地）；签退不再因医技执行净态拦截 | — |
| **BR-IP-ORD-010** | **组号**仅药品；可置空拆组（同 **BR-ORD-009**）；出院带药允许并组（**软约定**实务少用） | IF-IP-DOR-17 |
| **BR-IP-ORD-011** | 列表支持 **therapy_enum**（**TherapyType**：`LONG_TERM` / `TEMPORARY` / **`DISCHARGE_MEDICATION`**）筛选；合并排序：展示排序号升序优先，开立时间次之（同 **BR-ORD-018**） | IF-IP-DOR-10 / 14 |
| **BR-IP-ORD-012** | 医生站**不可开立耗材**；保存批次**拒绝**耗材类型行 | IF-IP-DOR-11 / 14 |
| **BR-IP-ORD-036** | **住院开立不带绑定**：`resolveInpatientDoctorStationAdviceDraftBinds` / `listDraftBindItems` **恒空**（诊疗绑诊疗、诊疗/用法绑耗材、合管派生采血费/试管**一律不带**）。住院范围 `cus_service_device_template` 仅由护士执行 **BR-IP-EXE-013** 挂载（**未合管**）；已合管检验开立与执行均不带（**AS-BAS-CFG-001 §7.7**）。门诊 IF-DOR-23 不变 | 暂存前绑定解析；申请单卡内耗材 enrich |
| **BR-IP-ORD-013** | **停嘱仅长期**（**BR-MRQ-006** / **BR-SRQ-016**）；临时与**出院带药不提供停嘱**；**禁止 ACTIVE 直接停嘱**：药品须 **`VERIFIED`**；诊疗须 **`VERIFIED`**（诊侧门诊路径仍允许 **`COMPLETED`** 停嘱，住院护士校对落 `VERIFIED`）；写 **`stop_time`** + **`stopper_id`=当前操作医生** 并迁 **STOPPED**；新停嘱清空 `stop_performer_check_id`/`stop_check_time`（待护士停嘱校对）；**仅停选中行**（**不**级联 `based_on` 子嘱） | IF-IP-DOR-20 |
| **BR-IP-ORD-025** | **取消停嘱仅长期**：须当前 **STOPPED** 且 **`stop_performer_check_id` 为空**（已停嘱校对则**拒绝**，须护士先 **IF-IP-AUD-03** 取消停嘱校对）；批量与 IF-IP-DOR-20 对称；恢复：**`performer_check_id` 非空 → `VERIFIED`，否则 → `ACTIVE`**；清空 `stop_time`、`stopper_id` **及停嘱校对两字段**；**不**级联子嘱；转科/出院/待出院门禁同前；**不**因 Procedure/Dispense 拒绝；**不**校验停嘱时间窗；极端路径误恢复 `VERIFIED` 产品接受口径不变 | IF-IP-DOR-25 |
| **BR-IP-ORD-014** | **出院医嘱**：`ServiceRequest.category_enum=34`（出院）、`therapy_enum=TEMPORARY`、`generate_source_enum=DOCTOR_PRESCRIPTION`；`reason_text`、`occurrence_start_time`（计划出院）；同就诊**不得重复**有效/草稿出院/转科申请（**BR-SRQ-010**）；**BR-SRQ-018** 吸氧/心电余量；**EncounterHistory** 离院里程碑写入 `out_way_code`（锚定 `request_id`）。**死亡出院**（术语 `out_way_code` code=`5`）：死亡时间必填、死亡原因选填；`reason_text` 优先表单出院原因，为空则回落死亡原因；同事务调用 **Patient.markPatientDeceased** 回写 `deceased_date`/`deceased_reason`（**BR-PAT-011**，不改患者主状态）；非死亡不写患者死亡字段 | IF-IP-DOR-21 |
| **BR-IP-ORD-017** | **出院/转科列表行**：API 下发专用字段（`serviceCategoryEnum`、`outWayCode`/`outWayName`、`reasonText`、`plannedDischargeTime`、`adviceDisplayName`）；**禁止**用 `contentSummary` 承载出院方式/原因/时间；`outWayName` 由术语 `out_way_code` 解析。`adviceDisplayName`：出院且 `outWayCode=5` 为「死亡出院」，其余出院为「出院医嘱」，转科为「转科医嘱」；护士站校对/执行/划价父医嘱列表经 **OrderView** 同源 | IF-IP-DOR-10 |
| **BR-IP-ORD-015** | 临时医嘱 UI 频次宜为「立即」；**前端约束**，后端住院不以此拒绝保存 | IF-IP-DOR-14 |
| **BR-IP-ORD-016** | 列表行信息维度对齐门诊 **BR-ORD-019**（含 **therapyType**、**开立时间+开立人**、**停嘱时间+停嘱人**、**皮试标志与皮试结果**、**输液滴速 `speedCode`/`speedName`** 等）；**不**以下发签发时间作主展示列（签发仅追溯，**BR-IP-ORD-031**）；**皮试结果**经 companion doctor-prescription 皮试检查 SR → `AllergyIntolerance` 装配 `skinTestOutcome*`；具体字段名以 SDD §3.2 为准；护士站校对/执行/划价等同构时间与人名展示由护士 AS 承接，数据同源 Request | IF-IP-DOR-10 / 22 |
| **BR-IP-ORD-038** | **医技执行进度 + 执行科室（列表只读）**：① 仅 **执行科室组织 class=TECH** 的诊疗行（`productTableName=wkf_activity_definition`，`ServiceRequest.org_id`→TECH）下发 `techPlannedExecutionCount` / `techCompletedCount` / `techCancelledCount`；非 TECH / 药品行字段为 null。② **计划次数**与医技站同口径（`ServiceAdviceDraftCommandHelper#resolvePlannedServicePerformCount`：长嘱强制 1）。③ **完成/取消**仅计 `cln_procedure` 且 `org_id=该 TECH 执行科`（护士侧 Procedure **不计入**）；净已执行=`max(0, completed−cancelled)`。④ **不改写** `displayStatus*`（仍为请求态+退费）；**不**新增独立表格列或执行态枚举列。⑤ **执行科室展示**：诊疗行 `performLocationId`/`performLocationName` **优先** `ServiceRequest.org_id`→组织名（与门诊 IF-DOR-10 同口径；**禁止**用 `location_id` 库位名冒充执行科）；无 org 时回落 `location_id`。⑥ 前端在**医嘱内容扩展行**（与出院「医嘱离院；出院时间」同风格蓝色副文案）展示：`净≥计划` → **「医技已执行」**，否则 **「医技待执行」**；**同段拼接执行科室名**（示例「医技已执行；检验科」）；非 TECH 诊疗行仍可只展示执行科室名；明细仍可复用 **IF-IP-EXE-01**。⑦ 与 **AS-MT-EXE-001**、出院门禁 **EX-IP-ATD-309** / `MedicalTechBilateralHomologySupport` TECH 轴同根 | IF-IP-DOR-10 / 22 |
| **BR-IP-ORD-024** | **住院皮试联动**：① **入口**：药品编辑卡片勾选**皮试**（`skinTestFlag=true`）；前端批次**仅**传药品行标志，**禁止**手工另加「皮试检查」诊疗 `servicePayload` 行。② **IF-IP-DOR-14 暂存**：皮试药 Request 落库后**同一事务**联动创建/维持 companion **`ServiceRequest`**（活动=固定名 **「皮试检查」**；`generate_source_enum=doctor-prescription`；**`therapy_enum=TEMPORARY`（固定临嘱，不继承皮试药长临；对齐门诊 BR-ORD-025；无长期皮试）**；`based_on_table=med_medication_request`，`based_on_id=皮试药 id`；诊断/执行科室/费用性质等**继承皮试药同行**；**首次**勾选幂等至多一条**草稿** companion；已存在**已签发** companion 时暂存**不**自动再建）；**不**创建 `ChargeItem`（遵守 **BR-IP-ORD-005/006**）。③ **换批追加**：**IF-IP-DOR-27** 对**长期**皮试药快捷**追加**新 companion「皮试检查」临嘱（仍 `based_on→该药`，**不**挂费）；同一皮试药允许**多条** companion（旧结果只读，护士 Tab 一行一 SR；医生站 `skinTestOutcome*` **最新覆盖**，见 **AS-IP-NUR-011**）。④ **改 false 或 DELETE 皮试药**：同步软删**全部草稿**联动 SR（**不**涉及费用作废——开立侧未挂费）。⑤ **IF-IP-DOR-15/16**：签发选中皮试药时**自动扩全部草稿**联动 SR；签退 companion **仅 ACTIVE**（草稿/已签退跳过）；同批勾选药+companion **去重**，避免二次签退；**不**挂费、**不** FIFO。⑥ **展示**：列表 companion 行医嘱名追加关联药文案，**可含规格**（示例「皮试检查（关联：氯化钠注射液 0.9%*250ml）」）。⑦ **下游**：护士校对后在 **AS-IP-NUR-004** **执行**皮试检查（操作费）；再在皮试 Tab（**AS-IP-NUR-011**）登记临床结果（**不计费**）；列表可下发 `needAppendSkinTestFlag`（长嘱皮试药发放批号 ≠ 最新皮试登记批号）提示换批重做 | IF-IP-DOR-14 / IF-IP-DOR-15 / IF-IP-DOR-16 / **IF-IP-DOR-27** |
| **BR-IP-ORD-018** | **废止（住院开立签发达 FIFO）**：签发侧 FIFO/库存拆零分段改由护士执行 **AS-IP-NUR-004**（**BR-IP-EXE-016** + 库存 Gate）；目录 IF-IP-DOR-11 仍可读库存/默认可发点供展示，**占用预减**与门诊 **BR-ORD-011/021** 同构（含**同库位多批**预占在 FIFO 批次遍历中**顺序消耗一次**，**禁止**每批重复扣整库占用；母版 **AS-OP-DOR-003-SDD v3.49** 规则 **D**） | IF-IP-DOR-11 |
| **BR-IP-ORD-022** | 与门诊 **BR-ORD-022** 同义：列表/目录库存/绑定行 **`unitCode`/`unitName`（及剂量单位）成对**；展示名术语预热 + 产品QueryService回落 | IF-IP-DOR-10 / IF-IP-DOR-11 / IF-IP-DOR-12 |
| **BR-IP-ORD-023** | 与门诊 **BR-ORD-024** 同义：诊疗行执行科室下拉优先 **IF-IP-DOR-19** 配置，无配置回落 **`executeLocation/init`** 全院科室；选品预填仅用目录 **`defaultPerformOrg*`**；**禁止**「当前登录机构」占位 | IF-IP-DOR-11 / IF-IP-DOR-19 / IF-IP-DOR-14 |
| **BR-IP-ORD-026** | **护理级别医嘱**（诊疗目录 `category_code=26` → `ServiceRequest.category_enum=26`）：① **仅长期**（`therapy_enum=LONG_TERM`），禁止临时；② 同就诊**仅一条有效**护理级别（`DRAFT`/`ACTIVE`/`VERIFIED`）；换级须 **IF-IP-DOR-20 停嘱旧单** 后再 **IF-IP-DOR-14/15** 开新单（**不**自动停旧）；③ 列表/分页/护士校对等读接口须下发 **`serviceCategoryEnum`/`categoryEnum`** 供前端识别；④ 护士站**仅校对**（**AS-IP-NUR-003**），**禁止**执行/不执行（**AS-IP-NUR-004 BR-IP-EXE-027**）；⑤ 滚费只读展示见 **AS-IP-NUR-013**；⑥ **开立入口（前端）**：普通医嘱选品下拉**按 `categoryCode=26` 过滤**，搜不到护理级别；仅顶部「护理」快捷弹窗开立；快捷开立**固定长期 + 频次 `QD`（每日一次）**写入请求，后端**透传**前端载荷（不改写频次） | IF-IP-DOR-10/11/14/15/20；IF-IP-AUD-01；IF-IP-ARG-02 |
| **BR-IP-ORD-027** | **出院结算轨就诊 Gate**：就诊 `status_enum ∈ {PENDING, COMPLETED, DISCHARGED}` 时**禁止** IF-IP-DOR-14/15/16/17/20/21/25/**26**/**27**/**28** 等写路径（暂存/签发/签退/停嘱/取消停嘱/改业务时间/出院医嘱/追加皮试/改量）；**仅** `IN_PROGRESS`/`ON_HOLD` 允许（`ON_HOLD` 待出院仍可开立，见 **SYS-IMP-009 GAP-FLW-002**）；委托 `InpatientPendingSettlementClinicalGateSupport`（**BR-IP-FLW-008**） | 14、15、16、17、20、21、25、26、**27**、**28** |
| **BR-IP-ORD-031** | **三时间轴与改业务时间（住院）**：① **开立** `req_opened_time`：业务主轴；首次暂存写入；**IF-IP-DOR-26** 可改；改后**不**改开立人 `requester_id`。② **签发** `req_authored_time`：仅追溯；列表**默认不展示**；**禁止修改**。③ **停嘱** `stop_time`：长期停嘱写入；**IF-IP-DOR-26** 可改（须已停嘱）；改后**不**改停嘱人 `stopper_id`，**保留** `stop_performer_check_id`/`stop_check_time`（改停嘱时间不重做停嘱校对）。④ **展示**：开立时间后跟开立人；停嘱时间后跟停嘱人；护士站同步。⑤ **改时入口**：按时间种类写对应列；**不**落「改时人」。⑥ **前端易用性时间窗（仅提示）**：开立/停嘱相对入院出院边界。⑦ **门诊本期不支持**改时间 | IF-IP-DOR-10/20/22/26；护士站读侧 |
| **BR-IP-ORD-032** | **长期药品改量**（**DD-住院长嘱口服一盒一点剩余计数与改量-2026-08**）：① **入口**：**IF-IP-DOR-28**；带出旧嘱全部信息，**`medication_id` 不可换**，其余字段均可改。② **在用改量**：源嘱须长期药品且 **`VERIFIED`** → 同事务停嘱（`stopTime` 可传，默认当前；停嘱人=操作医生）+ 新建长期药品**草稿**（`based_on_table=med_medication_request`、`based_on_id=源嘱 id`，同就诊、同 `medication_id`）。③ **已停改量**：源嘱 **`STOPPED`** → 不重复停嘱，直接建草稿并 `based_on` 锚源嘱。④ **多次改量**整链可追溯；剩余/领盒续算见 **AS-IP-NUR-004 BR-IP-EXE-038**。⑤ **观察期**：已停且尚无改量子嘱 → 不扣剩余，剩余挂已停条；开出新嘱后从新嘱开立日起按新量续扣，展示改挂链尾。⑥ **未领第一盒即改量** ≡ 新开一条（无结转剩余）。⑦ **无 basedOn 手停再开**不续算。⑧ 新草稿须再走 **IF-IP-DOR-15** 签发（本 IF 不替代签发）；就诊写门禁同 **BR-IP-ORD-027**；**仅药品**，诊疗/出院带药/临时不提供改量。⑨ **组号**：新嘱 **`group_id` 置空**，**禁止**继承源嘱组号（改量≠并组） | IF-IP-DOR-28；IF-IP-DOR-10/22 展示 |
| **BR-IP-ORD-033** | **同组成组药品频次须一致**：① 同一 `group_id` 下各药品行 **`rate_code` 必须相同**（禁止主药 QD、溶媒目录默认 ST 等异频并组）。② **IF-IP-DOR-14** 暂存批次：同组 CREATE/UPDATE 与库内既有成员对齐校验；不一致 → **EX-IP-ORD-308**。③ **IF-IP-DOR-17** 并组赋组：待并入行与目标组既有成员频次须唯一；不一致 → **EX-IP-ORD-308**。④ **前端**：编辑卡改频次须同步同组；选目录品项时若行已成组，**禁止**用目录默认频次覆盖组内已有频次。⑤ **拆组**不校验。⑥ 与 **BR-IP-ORD-010** 组号规则并列，不替代滴速等同组展示字段同步 | IF-IP-DOR-14/17 |
| **BR-IP-ORD-034** | **pending 调价禁止签发（对齐 BR-PRC-LOCK-007）**：IF-IP-DOR-15 签发前对本批药品定义调用 `PendingPriceAdjustmentSignGate`；任一条存在 `pending` 则整批拒绝（`PRICE_ADJUSTMENT_004`） | IF-IP-DOR-15 |
| **BR-IP-ORD-028** | **嘱托医嘱**：① **目录**：诊疗目录维护固定项，名称精确 **「嘱托」**；**`category_code=52`**（术语 `activity_category_code`「嘱托」）→ 开嘱写 **`ServiceRequest.category_enum=52`**；`type_enum` 可用 **其他（5）**；定价 **0 元**、单位 **次**；`pricing_flag` 可不参与划价（或等价 0 价）。② **范围**：**仅住院**长/临可开（`therapy_enum=LONG_TERM`/`TEMPORARY` 均可）；**禁止**以出院带药 therapy 开嘱托；**门诊**目录与写路径**禁开**（契约落 **AS-OP-DOR-003**，住院本文不实现门诊过滤）。③ **开立**：医生从 **IF-IP-DOR-11** 检索选中「嘱托」后，**必填**自由文本 → 落库 **`ServiceRequest.entrust_content`**（空/空白 → **EX-IP-ORD-306**）；`generate_source_enum=DOCTOR_PRESCRIPTION`；数量默认 **1**、单位 **次**（与目录一致）；医生可填 **`rate_code`/频次**，**仅展示**（列表/打印），**不**驱动护士执行计划点。④ **展示**：凡显示嘱托医嘱处，**医嘱内容**优先/唯一展示 **`entrust_content`**（医生站列表 `entrustContent`/`contentSummary`；护士站列表经 **OrderView** 写回 **`adviceDisplayName`**，均委托 **`InpatientEntrustOrderGateSupport#resolveServiceAdviceDisplayName`**；**AS-IP-NUR-002** 出院待执行列表 `displayName` 同源）；前端内容列**不得**只显示目录名「嘱托」。⑤ **下游**：护士站**正常校对、正常执行**（写 **Procedure** 执行记录）；执行时**禁止**创建 `ChargeItem`（**不生成账单**；契约由 **AS-IP-NUR-004** 增补执行侧 BR 承接，本文声明产品意图）。⑥ **识别**：以 **`category_enum=52`** 且/或活动定义名称 **「嘱托」** 判定嘱托行（禁止仅靠备注字段或 `ActivityType` 推断）。⑦ **执行次数（产品意图，承接 **AS-IP-NUR-004 · BR-IP-EXE-042**）**：无论长/临、无论医生开立频次为何，护士站**按「立即」整单只执行一次**（与临嘱单槽同构）；**禁止**按 `rate_code` 多日/多点展开 | IF-IP-DOR-10/11/14/15/22；**AS-IP-NUR-003/004/005**；**AS-IP-NUR-002** ATD-03 |
| **BR-IP-ORD-029** | **出院带药**：① **模型**：第三种 `TherapyType` code **`DISCHARGE_MEDICATION`**（展示「出院带药」），持久化 `therapy_enum`；**仅住院 IMP**；门诊禁开。② **范围**：药品目录（`med_medication_definition`）均可开；**不开**诊疗/申请单/嘱托/耗材；`quantity`/`unit_code` 语义**同临时（总量）**；**无**处方有效期。③ **开立生命周期**：暂存/签发/签退/并组与住院临时同构（**仅 Request**，遵守 **BR-IP-ORD-006**）；**不提供**停嘱/取消停嘱；就诊写门禁同 **BR-IP-ORD-027**（在院 `IN_PROGRESS`/`ON_HOLD` 可开，结算轨禁写）。④ **展示**：类型列 therapy 文案为「出院带药」（对应图中「临时医嘱」位置）。⑤ **软约定（不硬拒）**：实务为盒药带药；**不建议**勾皮试、开输液类用法；系统**不**因皮试/用法硬拒保存。⑥ **下游（产品意图，承接方见关联 AS）**：护士**校对/执行/不执行/取消执行**与临嘱一致；执行生成账单与 `MedicationDispense` 时**强制不写 `summary_no`**，汇总领药/汇总发药查询**一律过滤**该发放；仅走**住院逐行发药（AS-IP-PHA-001）**；护士办理出院待发药门禁（**AS-IP-NUR-002**）将未发完出院带药计入待发药项 | IF-IP-DOR-10/11/14/15/16/17/22；护士/药房 AS |
| **BR-IP-ORD-030** | **首日用量（首次用量）**：① **模型**：`first_dose` / `first_dose_unit_code`（**DM-MED-003** / **BR-MRQ-010**）；习惯称「首日」，语义为**首次执行**用量，**不是**开立日每个频次点换量。② **范围**：**仅长期药品**（`therapy_enum=LONG_TERM`）可填；**临时 / 出院带药 / 诊疗**禁止（非长期须空）。③ **开立**：编辑卡按**计量单位**可选填（同写剂量展示）；空 = 首次槽用量仍用常规单次量 `quantity`（有 `quantity_schedule` 时见 **BR-IP-ORD-037**）；有值须 **>0**；**落库**将计量数量换算为**最小/拆零单位**数量+单位（与长期 `quantity`/`unit_code` 同口径，禁止把计量单位码直接写入 `first_dose_unit_code`）。④ **与频次**：频次时钟点**可配置**，开立侧**禁止**写死 8/12/18 等时刻；执行侧点集合一律来自频次域展开。⑤ **废止旧补洞**：不再用「开立日 00:00 展开」把开嘱前已过点全部展开当作加量补洞；有/无首日量均以开嘱相对频次点裁剪（见⑥）。⑥ **下游（产品意图，承接 **AS-IP-NUR-004**）**：以**开嘱时间**为锚；对该频次开嘱日有序计划点：有已过点 → 首次 `planTime`=最近已过点，更早点不展示，开嘱后点照常；尚无已过点 → 首次=`当天第一个点`；已过当天最后一个点 → 首次=最后一个点且当日**只显示该槽**；**不新建** occurrence；`planTime` 仍为频次点，默认 `actualTime`=**开嘱时间 + 2 分钟**（双规，等同帮护士改执行时分；跨日钳开立日末）；**仅首次槽**在填了 `first_dose` 时用量/计价用首日量（已是最小单位），其余槽用常规单次量或分次 schedule（**BR-IP-ORD-037** / **BR-IP-EXE-040**）；发药/账单/库存 Gate **与常规长嘱槽同一路径**；防重锚 `planTime` **不变** | IF-IP-DOR-10/14/22；护士 AS |
| **BR-IP-ORD-037** | **分次用量不一致（长期药品）**：① **模型**：`dose_schedule` / `quantity_schedule`（**DM-MED-003 · BR-MRQ-035**）；对齐现有 `dose`（计量）与 `quantity`（拆零），**加字段加分支、不改原路径**。② **范围**：**仅住院长期西药/中成药**；临时/出院带药/中药/诊疗**禁止**（须空）。③ **开立 UI**：可选「用量是否不一致」；否 → schedule 空，单剂量框走原逻辑；是 → 按当前频次**每日执行次数**展开 N 个计量剂量框，**展开几个必填几个**（禁止缺/多）；顺序=当日频次点**早→晚**；**只存量，不存时刻**；`dose_unit_code` **必填且各次相同**。④ **落库**：前端按原「计量→拆零」换算写出 `quantity_schedule`（与 `dose_schedule` 段数/顺序一致）；`dose`/`quantity` 可写首项作兜底（给未改读路径），有 schedule 时展示与按槽算量**不依赖**单值。⑤ **校验（IF-IP-DOR-14/15）**：两 schedule 同空或同有值；有值时段数=频次日次数、每段正数、单位非空；非长期或中药携带非空 → 拒绝；改频次后段数须重对齐。⑥ **查询（IF-IP-DOR-10/22）**：长期药行**须下发** `doseSchedule`/`quantitySchedule`（及既有 `dose*`/`quantity*`）；有 schedule 时剂量列按序列展示（如 `2mg,1mg`），**打印可不改**。⑦ **与首日量**：正交可同时填；首次执行槽优先 `first_dose`（**BR-IP-ORD-030**）。⑧ **非目标/不波及**：停嘱/取消停嘱/不执行/取消执行/出院校验与办理、成组、改量链、医嘱模板本期**不**因本能力改规则；schedule 为空时护士算量与今日完全一致。⑨ **下游算量意图**：见 **AS-IP-NUR-004 · BR-IP-EXE-040**（有 schedule 则**分槽取量再累加**，禁止再用「单剂量×次数」） | IF-IP-DOR-10/14/15/22；护士 AS |
| **BR-IP-REQ-001～008** | 与门诊 **AS-OP-DOR-003 · BR-REQ-001～008** 同义（**`AR` 号**、编辑**取新号**、Detail/remark 口径、LIS/PACS 载荷）；绑定 **IF-IP-DOR-23/24/15**；**仅临嘱**，**不含**出院带药 | IF-IP-DOR-23 / 24 / 15 |


### 1.7 住院医生站 · 长/临/出院带药分轨需求真值

| 阶段 | 长期（`LONG_TERM`） | 临时（`TEMPORARY`） | 出院带药（`DISCHARGE_MEDICATION`） |
| --- | --- | --- | --- |
| **目录查询** | 同临时；仅药+诊 | 同门诊：模板库位、库存预减、定价（**BR-IP-ORD-001/002**；占用预减含**同库多批顺序消耗**，**BR-IP-ORD-018**） | **仅药品**目录（**BR-IP-ORD-029②**）；定价/库位展示同临时 |
| **暂存** | 仅 **MedicationRequest** / **ServiceRequest**（**DRAFT**） | **同长期**：仅 Request **DRAFT** | **仅 MedicationRequest DRAFT**（不开诊疗/申请单） |
| **签发** | 请求 **ACTIVE**、签署编码/处方号；**无**费用/发放 | **同长期**：请求 **ACTIVE**；**无**费用/发放 | **同临时**：请求 **ACTIVE**；**无**费用/发放 |
| **库存** | **不校验** | **不校验**（执行时 Gate，**AS-IP-NUR-004**） | **同临时** |
| **停嘱** | **IF-IP-DOR-20**（须 **VERIFIED**；写 `stop_time` + `stopper_id`；清停嘱校对字段） | **不提供** | **不提供** |
| **取消停嘱** | **IF-IP-DOR-25**（须 **STOPPED** 且未停嘱校对；清 `stop_time`/`stopper_id`/停嘱校对字段） | **不提供** | **不提供** |
| **改开立时间** | **IF-IP-DOR-26**（`timeKind=OPENED`） | **同左**（长/临/出院带药均可，门禁同 **BR-IP-ORD-027**） | **同临时** |
| **改停嘱时间** | **IF-IP-DOR-26**（`timeKind=STOP`；须已停嘱） | **不提供** | **不提供** |
| **护士执行后计费** | 护士站执行时 FIFO 多段建 Dispense + **ORDER_EXECUTE** Charge（**AS-IP-NUR-004**）；**皮试检查**同其它诊疗临嘱执行计费；**嘱托**执行**不**建 Charge（**BR-IP-ORD-028⑤**） | **同长期**（含嘱托不计费） | **同临时**；生成 Dispense **强制 `summary_no` 为空**（**BR-IP-ORD-029⑥**） |
| **发药路径** | 汇总或逐行（按护士/药房作业） | 同左 | **仅逐行发药（AS-IP-PHA-001）**；**不进**汇总领药/汇总发药 |
| **嘱托医嘱** | 可开（**BR-IP-ORD-028**） | **同长期** | **不可开** |
| **首日用量** | 药品可选填（**BR-IP-ORD-030**） | **禁止** | **禁止** |
| **分次用量不一致** | 西药/中成药可选（**BR-IP-ORD-037**） | **禁止** | **禁止** |
| **类型列文案** | 「长期医嘱」 | 「临时医嘱」 | 「出院带药」 |

### 1.8 住院皮试入口与联动（医生站侧）

> **定位**：皮试**发起点**在医生站药品行「皮试」勾选；**操作费**在护士执行「皮试检查」临嘱（**AS-IP-NUR-004**）；**临床登记**在护士皮试 Tab（**AS-IP-NUR-011**）。与门诊差异：住院医生联动 SR **不**挂 `ChargeItem`（**BR-IP-ORD-005/006**）；**不做**门诊收费门控。

#### 1.8.1 医生站入口（UI → IF-IP-DOR-14）

| 维度 | 口径 |
| --- | --- |
| **交互** | 药品编辑卡片「皮试」开关（与 `skinTestFlag` / 列表 `skinTestFlagText` 回显一致）；**不**提供单独检索「皮试检查」诊疗并手工加行的入口；**换批重做**走工具栏「追加皮试」（**IF-IP-DOR-27**），锚定选中**长期**皮试药 |
| **请求载荷** | `SaveInpatientMedicationAdviceDraftLinePayload.skinTestFlag`（Boolean）；**禁止**前端在 `lines` 中额外拼装 `wkf_activity_definition` 的「皮试检查」行；追加接口仅传 `medicationRequestIds` |
| **持久化** | 皮试标志写入 `med_medication_request.skin_test_flag`；联动 companion SR 由 **BR-IP-ORD-024** 在 **IF-IP-DOR-14** / **IF-IP-DOR-27** 后台完成（**不**建费用；`based_on` 必锚皮试药） |

#### 1.8.2 端到端链路（住院）

```
医生勾选皮试（skinTestFlag=true）→ IF-IP-DOR-14 暂存
    ├─ 皮试药 MedicationRequest（skin_test_flag=true）
    └─ 联动「皮试检查」ServiceRequest（doctor-prescription，based_on→皮试药）
           └─ 【不】建 ChargeItem（BR-IP-ORD-005/006）
                └─ IF-IP-DOR-15 签发（含自动扩 companion SR）→ ACTIVE
                     └─ 护士校对（AS-IP-NUR-003）
                          ├─ 执行「皮试检查」（AS-IP-NUR-004，操作费）
                          └─ 皮试 Tab（IF-IP-STK-02/03）登记临床结果（不计费）
                                 └─ 医生站列表回显 skinTestOutcome*

换批：长嘱皮试药执行跨批（BR-IP-EXE-034）→ 列表 needAppendSkinTestFlag
    └─ 医生 IF-IP-DOR-27 追加新 companion「皮试检查」（based_on→原长期药）
         └─ 签发 → 护士执行/登记（多行；旧只读；最新覆盖）
```

#### 1.8.3 与门诊皮试（AS-OP-DOR-003 · BR-ORD-025）差异

| 维度 | 门诊 | 住院（本文） |
| --- | --- | --- |
| 皮试检查 SR | 医生站 IF-DOR-14 联动 + **操作费草稿**；`therapy_enum=TEMPORARY` | 医生站 IF-IP-DOR-14 联动 SR，**不**挂费；**同** `therapy_enum=TEMPORARY`（不继承皮试药长临） |
| 操作费计费 | 门诊收费结算（SR → **COMPLETED**） | 护士在 **AS-IP-NUR-004** 执行皮试检查临嘱 → **PENDING_SETTLEMENT** |
| 护士 Tab 门控 | 皮试检查 SR **COMPLETED**（已收费） | 皮试药 **VERIFIED** + companion SR **已执行**（**AS-IP-NUR-011**） |
| `AllergyIntolerance.request_id` | doctor-prescription 皮试检查 SR.id | **同左** |

### 1.9 嘱托医嘱（医生站侧）

> **定位**：住院医生通过诊疗目录固定项「嘱托」开立**无计费、有正文**的医嘱；正文存 `entrust_content`；生命周期与常规诊疗同轨（暂存→签发→护士校对→执行），差异在**内容展示**、**执行不计费**与**执行仅一次**（频次仅展示）。

#### 1.9.1 医生站入口（UI → IF-IP-DOR-11 / 14）

| 维度 | 口径 |
| --- | --- |
| **目录** | **IF-IP-DOR-11** 检索诊疗可见名称 **「嘱托」**（`category_code=52`）；0 元、单位「次」 |
| **交互** | 选中「嘱托」后，编辑卡片提供**自由文本**输入（医嘱内容）；长/临 Tab 均可；频次可选填，**仅列表展示** |
| **请求载荷** | 诊疗行 `servicePayload.entrustContent`（必填）；`activityDefinitionId` 锚「嘱托」定义；`rateCode` 可透传 |
| **持久化** | `wkf_service_request.entrust_content`；`therapy_enum` 随当前长临；**不**建 `ChargeItem`（遵守 **BR-IP-ORD-005/006**） |

#### 1.9.2 端到端链路（住院）

```
医生检索并选中目录「嘱托」→ 录入自由文本（如「定时翻身」）+ 可选频次（仅展示）
    └─ IF-IP-DOR-14 暂存 ServiceRequest（entrust_content=正文，0 元目录）
           └─ IF-IP-DOR-15 签发 → ACTIVE
                └─ 护士校对 VERIFIED（AS-IP-NUR-003）
                     └─ 护士执行：写 Procedure 一次（无视频次，BR-IP-ORD-028⑦ / BR-IP-EXE-042）
                            └─ 【不】建 ChargeItem（BR-IP-ORD-028⑤ → AS-IP-NUR-004）
```

#### 1.9.3 展示口径

| 位置 | 口径 |
| --- | --- |
| **类型列** | 「嘱托」+「长期医嘱」或「临时医嘱」（`activityTypeName` + 长临文案） |
| **医嘱内容列** | **仅**医生录入的 `entrust_content`（例：定时翻身）；**禁止**只显示目录名「嘱托」 |
| **频次列** | 可展示医生开立的频次文案；**不**表示护士须按该频次多次执行 |
| **总金额** | 开立侧无账单；执行后仍无费用行 → 展示空/「—」 |

### 1.10 出院带药（医生站侧）

> **定位**：住院第三种 `TherapyType`；开立与**临时药品**同构；唯一业务差异在**执行后发放不进汇总、仅按患者逐行发药**（承接护士/药房 AS）。皮试/输液为**软约定**，系统不硬拒。

#### 1.10.1 医生站入口（UI → IF-IP-DOR-10 / 11 / 14）

| 维度 | 口径 |
| --- | --- |
| **分轨** | 编辑卡/列表筛选 Radio：**长期 / 临时 / 出院带药**；选「出院带药」时写/读 `therapyTypeCode=DISCHARGE_MEDICATION` |
| **目录** | **IF-IP-DOR-11** 实务仅选 `med_medication_definition`；药品目录内西药/中成药/中草药等均可（**BR-IP-ORD-029②**） |
| **交互** | 用法频次剂量等同临时；**软约定**不勾皮试、不开输液用法（**不**后端硬拒） |
| **持久化** | `med_medication_request.therapy_enum=DISCHARGE_MEDICATION`；`quantity` 总量语义同临时 |

#### 1.10.2 端到端链路（住院）

```
医生切「出院带药」→ 选药品 → IF-IP-DOR-14 暂存 MedicationRequest（therapy=DISCHARGE_MEDICATION）
    └─ IF-IP-DOR-15 签发 → ACTIVE（无 Charge/Dispense）
         └─ 护士校对 VERIFIED（AS-IP-NUR-003，与临嘱一致）
              └─ 护士执行：FIFO 建 Dispense + ORDER_EXECUTE Charge（AS-IP-NUR-004）
                     ├─ Dispense.summary_no 强制为空（BR-IP-ORD-029⑥）
                     ├─ 汇总领药/汇总发药查询过滤（AS-IP-NUR-010 / AS-IP-PHA-002）
                     └─ 仅住院逐行发药（AS-IP-PHA-001）
                          └─ 护士办理出院：未发完计入待发药门禁（AS-IP-NUR-002）
```

#### 1.10.3 展示口径

| 位置 | 口径 |
| --- | --- |
| **类型列** | 品类文案（如「西药」）+ **「出院带药」**（`therapyTypeText`；**非**「临时医嘱」） |
| **医嘱内容列** | 同常规药品行（品名/规格/用法用量等） |
| **总金额** | 开立侧无账单；护士执行后同临嘱费用汇总 |

### 1.11 首日用量（首次用量 · 医生站侧）

> **定位**：医生站只负责**录入并持久化** `first_dose`（可选）；习惯称「首日」，语义为**首次执行**用量。执行列表如何相对开嘱时间裁剪频次点、默认改 `actualTime`、仅首次槽换量——属护士站执行（**AS-IP-NUR-004**），本文 **BR-IP-ORD-030⑥** 声明产品意图。**禁止**写死频次时钟（点来自频次配置）。

#### 1.11.1 医生站入口（UI → IF-IP-DOR-10 / 14）

| 维度 | 口径 |
| --- | --- |
| **分轨** | **仅长期药品**编辑卡展示/可填；临时、出院带药、诊疗**无**首日量字段 |
| **字段** | `firstDose` + `firstDoseUnitCode` → `med_medication_request.first_dose` / `first_dose_unit_code`（**落库为最小单位**） |
| **空值** | 不填 = 首次槽仍用常规单次量；**时间点裁剪规则仍生效**（由护士 AS 承接） |
| **有值** | UI 按计量单位录入（例 12ml）；须 **>0**；保存时换算为最小单位（例 1 支）后与 `quantity`（长期单次用量）同口径分治，**不得**把计量单位码直接落库 |
| **清空** | 编辑卡删空后暂存须落库清空（UPDATE 显式 null / 域层 `syncFirstDose`）；禁止「省略字段=保留旧值」导致删不掉 |
| **列表回显** | **IF-IP-DOR-10/22** 长期药行下发库内 `firstDose` / `firstDoseUnitCode`（及单位展示名若有）；编辑卡回填时再还原为计量展示 |

#### 1.11.2 端到端意图（开立 → 执行）

```
医生开长期药（可选填首日用量）→ IF-IP-DOR-14 暂存 → IF-IP-DOR-15 签发
    └─ 护士校对 VERIFIED（AS-IP-NUR-003）
         └─ 护士执行列表（AS-IP-NUR-004）：
                ├─ 频次域展开开嘱日有序计划点（时钟可配置，禁止写死）
                ├─ 相对开嘱时间选「首次」planTime（已过取最近；未到取首点；过末日仅留末点）
                ├─ 首次槽：planTime=频次点，默认 actualTime=开嘱+2 分钟（双规）
                ├─ 填了 first_dose → 仅该槽 Dispense/Charge 用量用首日量
                └─ 其余槽：常规单次量；数据流/防重锚 planTime 不变
```

#### 1.11.3 展示口径

| 位置 | 口径 |
| --- | --- |
| **编辑卡** | 长期药品：单次用量旁可选「首日用量」；**按计量单位展示/录入**（同写剂量）；保存换算最小单位 |
| **列表** | 剂量列拼接「首日{数量}{单位}」（有值时；单位为库内最小单位展示名；与护士站校对/执行同源文案）；字段 `firstDose`/`firstDoseUnitCode`/`firstDoseUnitName` **须下发** |

### 1.14 分次用量不一致（长期药品 · 医生站侧）

> **定位**：解决「同一频次、不同执行点用量不同」（如日二次：早 2、晚 1）。医生站负责录入并持久化 `dose_schedule`/`quantity_schedule`；护士按槽取量与累加属 **AS-IP-NUR-004 · BR-IP-EXE-040**。**核心纪律：schedule 空则原逻辑零侵入；有值才加分支。**

#### 1.14.1 医生站入口（UI → IF-IP-DOR-10 / 14 / 15）

| 维度 | 口径 |
| --- | --- |
| **分轨** | **仅长期西药/中成药**编辑卡提供「用量是否不一致」；临时、出院带药、中药、诊疗**无**该入口 |
| **一致** | 不勾选 / 关 → 单剂量框；`doseSchedule`/`quantitySchedule` **空**；完全走现有 `dose`/`quantity` |
| **不一致** | 勾选 / 开 → 按当前频次每日执行次数展开 N 个**计量**剂量框；**必填满 N 个正数**；单位框仍一个（`doseUnitCode` 必填） |
| **顺序** | 第 1～N 框 = 当日频次执行点**早→晚**；**不存**时刻字符串 |
| **落库** | `doseSchedule`=计量逗号串；`quantitySchedule`=各框按**原计量→拆零换算**写出的拆零串（同序同段数）；`dose`/`quantity` 可写首项兜底 |
| **改频次** | 日次数变化 → 须重展开并重填；段数与日次数不一致 → 暂存/签发拒绝 |
| **与首日量** | 可同时填；语义正交（首日=入院后**第一次执行**；schedule=在院期间各频次点） |
| **清空不一致** | 关开关 / 删空序列 → schedule 落库清空，回落单剂量路径 |

#### 1.14.2 查询回显（IF-IP-DOR-10 / 22）

| 位置 | 口径 |
| --- | --- |
| **列表/编辑回填** | 下发 `doseSchedule`/`quantitySchedule`；有值时剂量展示走序列；编辑卡按段数还原多框 |
| **打印** | **本期可不改**（产品接受） |
| **护士站读** | 同源字段下发见 **AS-IP-NUR-004**（校对/执行列表） |

#### 1.14.3 端到端意图（开立 → 执行算量）

```
医生开长期药 → 可选「用量不一致」
  ├─ 否：dose/quantity 单值（今日路径）
  └─ 是：dose_schedule + quantity_schedule（N 段=日次数）
       → IF-IP-DOR-14 暂存 / IF-IP-DOR-15 签发（段数/正数/单位校验）
            └─ 护士执行（AS-IP-NUR-004）：
                   ├─ 频次展开 planTime 槽（顺序早→晚）
                   ├─ 有 quantity_schedule → 第 i 槽用第 i 段拆零量
                   ├─ 无 schedule → 仍用 quantity（零改动）
                   ├─ 首次槽若有 first_dose → 该槽优先首日量
                   └─ 一盒一点 / 拆零下限：按各槽临床量分别换算后累加切批（禁止单剂量×次数）
```

#### 1.14.4 明确不波及

停嘱 / 取消停嘱 / 不执行 / 取消执行 / 出院办理与待执行门禁规则本身、成组逻辑、改量链、医嘱模板——**本期不因本能力改契约**；仅当某槽「应发量」来自 schedule 时，下游用该量（门禁槽位集合算法不变）。

### 1.12 三时间轴与改业务时间（住院）

| 维度 | 口径 |
| --- | --- |
| **开立时间** | `req_opened_time`；首次暂存/直建写入；**业务主轴**（列表排序、展示、护士频次左界等）；**IF-IP-DOR-26** 可改 |
| **签发时间** | `req_authored_time`；点签发写入；**仅系统追溯**；列表**默认不展示**；**禁止任何改时接口写入** |
| **停嘱时间** | `stop_time`；长期停嘱写入；执行/滚费右界；**IF-IP-DOR-26** 可改（仅已停嘱长期行） |
| **开立人** | `requester_id`；开立后**永远**跟在开立时间后展示；改开立时间**不**改开立人 |
| **停嘱人** | `stopper_id`（新列）；停嘱操作人；跟在停嘱时间后展示；取消停嘱清空；改停嘱时间**不**改停嘱人 |
| **改时人** | **本期不落库** |
| **入口** | **IF-IP-DOR-26**：前端传时间种类（开立 / 停嘱）+ 新时间；后端只写对应列 |
| **门诊** | 本期**不支持**改时间 |
| **护士站** | 读侧同步展示开立/停嘱时间及开立人/停嘱人（数据同源 Request；契约承接护士 AS） |

### 1.13 长期药品改量（医生站侧）

> **定位**：改剂量/频次/用法等而不换药品；停旧+开新并用 `based_on` 串链，供执行侧口服一盒一点剩余续算（**BR-IP-EXE-038**）。裁决全文见 **DD-住院长嘱口服一盒一点剩余计数与改量-2026-08**。

#### 1.13.1 医生站入口（UI → IF-IP-DOR-28）

| 维度 | 口径 |
| --- | --- |
| **交互** | 长期药品行「改量」：弹窗/编辑卡**带出旧嘱全部字段**；**药品本身不可改**；其余均可改；**在用改量**编辑卡可改原嘱停嘱时间（默认当前，校验同停嘱） |
| **两种源状态** | **在用 `VERIFIED`**：自动停嘱 + 开新草稿；**已停 `STOPPED`**：只开新草稿（观察几天后再改量） |
| **关联** | 新嘱 `based_on_table=med_medication_request`、`based_on_id=源嘱`；多次改量链式 |
| **后续** | 新草稿走既有暂存微调（可选）→ **IF-IP-DOR-15** 签发 → 护士校对 → 执行 |

#### 1.13.2 端到端与剩余

```
在用改量：VERIFIED 长药 → IF-IP-DOR-28（停嘱 + 新草稿 based_on→旧）
已停改量：STOPPED 长药 → IF-IP-DOR-28（仅新草稿 based_on→旧）
    └─ 观察期（已停无子嘱）：不扣剩余；剩余挂已停条
    └─ 签发/校对新嘱后：从新嘱开立日起按新量续扣；剩余挂最新条
         └─ 护士执行列表：剩余够用的自然日不出下一盒 pending（BR-IP-EXE-038）
```

#### 1.13.3 改量当天日消耗（执行侧承接）

| 情形 | 当天理论日消耗 |
| --- | --- |
| 多→少 | 按**旧（多）** |
| 少→多 | 按**新（多）** |

---

## 2. 页面与接口总览（契约概览）


| IF 编号 | 用户动作 | 接口语义（业务） | 读/写 | 关键规则 | 关键状态变化 | 涉及领域 | 查询服务 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| IF-IP-DOR-10 | 进入医嘱 Tab、切换患者、切换长/临/**出院带药**、刷新 | 加载当前就诊**医生开立**的药品+诊疗统一列表（可选 **therapyType**：`LONG_TERM`/`TEMPORARY`/`DISCHARGE_MEDICATION`）；**`unitCode`/`unitName` 成对**（**BR-IP-ORD-022**）；**嘱托行**下发 `entrustContent`/`activityType*`（**BR-IP-ORD-028**）；出院带药行 `therapyTypeText=出院带药`（**BR-IP-ORD-029**）；**长期药**下发 `firstDose`/`firstDoseUnitCode`（**BR-IP-ORD-030**）及 **`doseSchedule`/`quantitySchedule`（**BR-IP-ORD-037**）**；**开立时间+开立人、停嘱时间+停嘱人**（**BR-IP-ORD-031**；签发时间可下发但不作主展示）；命中一盒一点口服时下发剩余/已吃/预计领盒日（**BR-IP-EXE-038** 同源）；**TECH 执行科诊疗**下发医技净执行进度 `techPlannedExecutionCount`/`techCompletedCount`/`techCancelledCount`（**BR-IP-ORD-038**，同 **AS-MT-EXE-001**）；**诊疗行**下发执行科室 `performLocationId`/`performLocationName`（优先 `org_id` 组织名，**BR-IP-ORD-038⑤**） | 读 | BR-IP-ORD-004/011/016/**022**/**028**/**029**/**030**/**031**/**032**/**037**/**038**；**BR-IP-EXE-038** | 无 | Med/Service Request；临时/出院带药侧 ChargeItem/Dispense 只读；Procedure（TECH 轴只读）；Organization（执行科名） | InpatientOrderLookupLoader · InpatientOrderListSkeleton（目标态）；InpatientEncounterContextQueryModel 门控；过渡期域查询装配 |
| IF-IP-DOR-11 | 编辑卡片检索：全部/药品/诊疗、关键字、翻页 | 分页 **ProductDefinition**（**不含耗材**；**含嘱托**固定项）；库存/定价/单位 bands 同门诊（**占用 `unit_code`→拆零预减**，**BR-IP-ORD-018**）；出院带药 Tab 实务仅选药品（**BR-IP-ORD-029**） | 读 | BR-IP-ORD-001/002/012 / **BR-IP-ORD-018**/**028**/**029** | 无 | ProductDefinition；ServiceLocationTemplate | — |
| IF-IP-DOR-12 | 选用法或诊疗后加载绑定 | 查询绑定子项/用法绑定的规则 | 读 | — | 无 | ActivityDefinition 等 | — |
| IF-IP-DOR-13 | — | **已废止**（费用性质下拉并入 **AS-IP-DOR-001**，对齐门诊 **IF-DOR-01** `feeNatureOptions`） | — | — | — | — | — |
| IF-IP-DOR-18 | 进入 Tab 或切换患者；**申请单弹窗**同接口 | 合并只读：用药频次、给药途径术语下拉；**身体部位树形**下拉（同门诊 **IF-DOR-18**，含拼音/五笔，申请单可模糊过滤）；**样本定义**平面下拉（含拼音/五笔，申请单可模糊过滤） | 读 | — | 无 | 术语；BodyStructure；SpecimenDefinition | — |
| IF-IP-DOR-19 | 诊疗编辑：按活动定义加载执行科室规则 | 只读可执行科室/地点规则及展示名（**含单品 / 业务类型 / 大类默认**）；**前端下拉回落**同门诊 **BR-ORD-024** | 读 | **BR-IP-ORD-023** | 无 | ActivityExecutionLocation | — |
| IF-IP-DOR-14 | 保存、删除确认 | **暂存**：**长/临/出院带药均仅 Request**；出院带药行 `therapy_enum=DISCHARGE_MEDICATION`（**BR-IP-ORD-029**）；**长期药品**可透传 `firstDose`/`firstDoseUnitCode`（**BR-IP-ORD-030**；非长期须空）及 **`doseSchedule`/`quantitySchedule`（**BR-IP-ORD-037**；非长期/中药须空；有值须段数=频次日次数）**；**药品行** `skinTestFlag=true` 时**联动**「皮试检查」SR（**不**挂费，**BR-IP-ORD-024**；出院带药软约定不勾）；**嘱托行**必填 `entrustContent`（**BR-IP-ORD-028**）；**同组药品频次一致**（**BR-IP-ORD-033**）；**不**校验库存 | 写 | BR-IP-ORD-003～008、011、012、015、**024**、**028**、**029**、**030**、**033**、**037** | 草稿请求；皮试 companion SR 幂等同步；嘱托正文落库；首日量/分次 schedule 落库 | Med/Service |
| IF-IP-DOR-15 | 签发 | **签发**：**长/临/出院带药均仅**请求 **ACTIVE**；**选中皮试药**时**自动扩**联动皮试检查 SR（**BR-IP-ORD-024**）；嘱托同行签发；**不**建 Dispense/Charge | 写 | BR-IP-ORD-005～007、**024**、**028**、**029** | 请求 ACTIVE；联动 SR 同步签发 | Med/Service |
| IF-IP-DOR-16 | 撤回 | **签退**：**长/临/出院带药药品**仅 **signOff**；诊疗 **withdrawSigned**；**TECH 同态已废**（不再过 **BR-IP-ORD-035**）；**签退/删除皮试药**时**对称**处理联动 SR（**BR-IP-ORD-024**） | 写 | BR-IP-ORD-009、**024**、**029** | 请求回草稿；联动 SR 对称签退/软删 | Request |
| IF-IP-DOR-17 | 并组/拆组 | 批量更新药品**组号**（含出院带药行）；赋组时校验同组频次一致（**BR-IP-ORD-033**） | 写 | BR-IP-ORD-010、**029**、**033** | 组号变更 | MedicationRequest | — |
| IF-IP-DOR-20 | 停嘱（长期） | 批量**停嘱**所选已开嘱校对的长期药/诊；写 `stop_time` + `stopper_id`；清停嘱校对字段 | 写 | BR-IP-ORD-013/**031** | **STOPPED** + 停嘱轴；进入护士停嘱待校 | Med/Service Request | InpatientOrderLookupLoader · InpatientOrderListSkeleton |
| IF-IP-DOR-25 | 取消停嘱（长期） | 批量**取消停嘱**所选 **STOPPED** 且**未**停嘱校对的长期行；清停嘱轴与停嘱校对字段 | 写 | BR-IP-ORD-025/**031** | **VERIFIED** 或 **ACTIVE** | Med/Service Request | InpatientOrderLookupLoader · InpatientOrderListSkeleton |
| IF-IP-DOR-26 | 改开立时间或停嘱时间 | 按行改 Request **开立时间或停嘱时间**之一（前端传时间种类）；**禁止**改签发时间；**不**改开立人/停嘱人 | 写 | BR-IP-ORD-031/**027** | 仅时间列变更；状态不变 | Med/Service Request | — |
| IF-IP-DOR-27 | 追加皮试检查（长期皮试药） | 对已勾皮试的**长期**药品快捷追加 companion「皮试检查」临嘱；`based_on→该药`；**不**挂费；已有未签发草稿则复用 | 写 | BR-IP-ORD-024/**027** | 新/既有草稿 companion SR | ServiceRequest | DoctorStationSkinTestSyncOrchestrator |
| IF-IP-DOR-28 | 长期药品**改量** | 停旧（若在用）+ 同药重开草稿；`based_on→源嘱`；药品不可换；可指定停嘱时间；**本 IF 不签发** | 写 | BR-IP-ORD-032/**013**/**027** | 源嘱 STOPPED（若原 VERIFIED）；新药嘱 DRAFT + basedOn | MedicationRequest | `InpatientDoctorStationAdviceAppService#changeLongTermMedicationDoseInpatientDoctorStationAdvice` |
| IF-IP-DOR-21 | 开立出院医嘱 | 创建出院 **ServiceRequest** + **EncounterHistory** 离院里程碑；出院方式为死亡时同事务标记患者死亡 | 写 | BR-IP-ORD-014 | 出院申请已创建；里程碑已记录；死亡出院已回写患者死亡时间 | ServiceRequest；EncounterHistory；Patient | InpatientEncounterContextQueryModel 门控 |
| IF-IP-DOR-22 | 医嘱查询视图切换 **当前医嘱/历史医嘱** Tab、刷新明细 | 单接口分页加载患者**住院全类型医嘱**统一列表（药品+诊疗；读口径与 **IF-IP-DOR-10** 同构；**是否按处方形式展示由前端处理**；**当前医嘱** Tab 按 `encounterId`；**历史医嘱** Tab 按 `patientId` 查全量并排除当前 `encounterId`；可选 `therapyTypeCode` / `orderingDepartmentId` / `requestStatusEnumIn` 过滤；须与 IF-IP-DOR-10 同一就诊/患者门控） | 读 | BR-IP-ORD-004 | 无 | MedicationRequest + ServiceRequest + ChargeItem（只读聚合） | `InpatientDoctorStationAdviceListAssembler` + `InpatientOrderListMergeAssembler` |
| IF-IP-DOR-23 | 申请单弹窗「保存」 | 同门诊 **IF-DOR-21**：整张保存；**临嘱** `ServiceRequest` + Detail + 费用草稿；**编辑取新 `AR` 号** | 写 | BR-IP-REQ-001～008 | 新号下 DRAFT 行 | ServiceRequest；Detail；ChargeItem | InpatientEncounterContextQueryModel |
| IF-IP-DOR-24 | 申请单列表刷新 | 同门诊 **IF-DOR-22**：`encounterId + categoryEnum` | 读 | BR-IP-REQ-003 | 无 | ServiceRequest；Detail | ServiceRequestQueryService |


---

## 3. 缺口跟踪（Gap）


| Gap ID | 缺口类型 | 文件 + 章节 | 缺什么 / 不一致点 | 关闭条件 | 影响 IF | 优先级 | 状态 |
|--------|----------|-------------|-------------------|----------|---------|--------|------|
| G-IP-DOR-003-01 | 查询服务 | `ServiceRequestQueryService / MedicationRequestQueryService 等（DM §6.2）` §2、§3 | ~~§2 未暴露 `generate_source_enum`~~ | 已补：医嘱查询结果 `generate_source_enum`、DM §6 查询契约 **Q-ORD-007**、`ServiceRequestQueryService#listServiceRequestsByGenerateSource`、**`InpatientOrderLookupLoader` · `InpatientOrderListSkeleton`** 按主表（`wkf_service_request` / `med_medication_request` 等）查询组装 | IF-IP-DOR-10 | P1 | **Closed** |
| G-IP-DOR-003-02 | QueryService 能力 | `DM-WKF-004` §6.2.24 | 同门诊 **G-DOR-003-11** | **已关闭**：`mapDetailListModelsByServiceRequestIds`（**DM v1.1.14**） | IF-IP-DOR-10 / IF-IP-DOR-24 | P1 | **Closed** |
| G-IP-DOR-003-03 | DM 写能力 | `DM-MED-003` §6.1.6a + `DM-WKF-004` §6.1.8a | ~~缺 **`cancelStopLongTermMedicationOrder`** / **`cancelStopServiceRequest`**、实体 **`fromStopped`** 状态恢复、取消停嘱领域事件~~ | 域命令 + 实体方法 + 事件落地并与 **BR-IP-ORD-025** 一致 | IF-IP-DOR-25 | P0 | **Closed** |
| G-IP-DOR-003-04 | DM 能力 | `DM-WKF-004` §4.3；`activity_category_code` | ~~缺嘱托类目 / 误用 ActivityType~~ | **已关闭**：`category_code=52`「嘱托」+ `ServiceRequestCategoryConstants.SERVICE_CATEGORY_ENTRUST`；`DM-WKF-004` v1.1.22；种子 `AD00000015` + 本地脚本 `db/local/seed_entrust_activity_definition.sql`（**已撤回**误增的 `ActivityType.ENTRUST`） | IF-IP-DOR-10/11/14 | P0 | **Closed** |
| G-IP-DOR-003-05 | DM 能力 | `DM-MED-003` §4.2；`DM-WKF-004` §4.5；共享枚举 `TherapyType` | ~~缺 **`DISCHARGE_MEDICATION`（出院带药）**~~ | **已关闭**：`TherapyType.DISCHARGE_MEDICATION`；`DM-MED-003` v1.3.24；`DM-WKF-004` v1.1.23；quantity 同 TEMPORARY（**BR-MRQ-009** / **BR-IP-ORD-029**） | IF-IP-DOR-10/11/14/15/16/17/22 | P0 | **Closed** |
| G-IP-DOR-003-06 | 代码-文档不一致 | `DM-MED-003` v1.3.25 §3/`§6.1.6*`；`DM-WKF-004` v1.1.24 §3/`§6.1.8*` | ~~DM 已约定 **`stopper_id`** 与改时命令；领域代码未落地~~ | **已关闭**：V5/V6 DDL + 实体/`stopper_id`；停嘱写停嘱人、取消清空；`change*BusinessTime` 领域命令；QueryModel 透出；本地脚本 `db/manual/add_three_request_stopper_id.sql`；**App IF-IP-DOR-26 已落地**（列表 `stopper*`、护士站人名同步） | IF-IP-DOR-20/25/26 | P0 | **Closed** |
| G-IP-DOR-003-07 | DM 能力 | `DM-MED-003` §3 / **BR-MRQ-035**；Create/Change Command | ~~缺 `dose_schedule`/`quantity_schedule` 列与草稿写透传~~ | **待关闭**：DDL + 实体/QueryModel/Command 透传；关闭后医生站 IF-IP-DOR-10/14 与护士 **BR-IP-EXE-040** 方可落库联调 | IF-IP-DOR-10/14/22；护士 EXE | P0 | **Open** |

> **非 Gap**：**AS-IP-DOR-001** 若未声明 `feeNatureOptions` 由就诊管理 AS/SDD 增补，**不得**入本文 Gap（**`InpatientDoctorStationAdviceAppService` 已落地**）。**门诊禁开嘱托**、**护士执行跳过账单**、**出院带药执行不写 `summary_no`/汇总过滤/逐行发药/出院待发药门禁**、**首日用量执行侧槽位/`actualTime`/换量**、**分次 schedule 执行侧分槽算量**属 **AS-OP-DOR-003** / **AS-IP-NUR-003/004** / **AS-IP-PHA-001** / **AS-IP-NUR-010** / **AS-IP-PHA-002** / **AS-IP-NUR-002** 应用编排目标态（本文 **BR-IP-ORD-029⑥** / **BR-IP-ORD-030⑥** / **BR-IP-ORD-037⑨** 仅声明产品意图），**不得**入本文 Gap。`first_dose` 域字段与 **BR-MRQ-010** 已存在，**不得**因「医生站 UI/暂存透传未接」入 Gap。**`dose_schedule`/`quantity_schedule` 以 **DM-MED-003 v1.3.38 / BR-MRQ-035** 为准**；领域实体/Command/DDL 未落地前见上表 **G-IP-DOR-003-07**。

---

## 4. 接口编排说明书（业务步骤）

### IF-IP-DOR-10 加载当前住院就诊医嘱列表


| 维度 | 说明 |
| --- | --- |
| **触发前提** | 已在 **AS-IP-DOR-001** 选定住院就诊；医生已登录 |
| **主路径步骤** | 校验就诊为 **IMP** 且医生站可见状态 → **InpatientOrderLookupLoader · InpatientOrderListSkeleton** 列表（**Q-ORD-007**，已含医生来源过滤）→ 若传入 **therapyType** 则按 **therapy_enum** 筛选（`LONG_TERM`/`TEMPORARY`/`DISCHARGE_MEDICATION`，**BR-IP-ORD-011/029**）→ 装配器叠加 **ServiceRequest** 明细（含 `mapListModelsByIds` 补全出院/转科行）与 **EncounterHistory** 出院方式 → 常规药/诊行：目录名、剂量频次、`contentSummary`（窄列，同门诊）→ **出院带药行**：`therapyTypeCode=DISCHARGE_MEDICATION`，`therapyTypeText=出院带药`（**BR-IP-ORD-029**）→ **长期药行**：下发 `firstDose`/`firstDoseUnitCode`（**BR-IP-ORD-030**）及 `doseSchedule`/`quantitySchedule`（**BR-IP-ORD-037**；有 schedule 时剂量展示走序列）→ **嘱托行**：下发 `entrustContent`、`serviceCategoryEnum=52`（类目展示「嘱托」）；医嘱内容展示以 **`entrustContent`** 为准（**BR-IP-ORD-028**）→ **皮试药行**：`skinTestFlag`/`skinTestFlagText` + 经 companion doctor 皮试检查 SR → `AllergyIntolerance` 装配 **`skinTestOutcomeCode`/`skinTestOutcomeText`**（**BR-IP-ORD-016/024**）→ **时间与人名**（**BR-IP-ORD-031**）：下发 `reqOpenedTime` + 开立人姓名、`stopTime` + 停嘱人姓名；签发时间不作主展示 → **出院行（category=34）**：`adviceDisplayName`、专用字段（**BR-IP-ORD-017**），`contentSummary` 为 null → 费用汇总**临时与出院带药**常规药品行（**不含**皮试操作费——护士登记后产生；**嘱托无费用**） |
| **关键分支** | 空列表空态；**不包含**护士划价、耗材请求；出院/转科不查 `wkf_activity_definition`（`activity_id` 占位 0） |
| **失败分支** | 查询失败 → EX-IP-ORD-201 |
| **查询侧说明** | 纯读；写后由请求/费用类事件刷新 **InpatientOrderListSkeleton** |


---

### IF-IP-DOR-11 分页检索可开立项目（药品+诊疗）


| 维度 | 说明 |
| --- | --- |
| **触发前提** | 登录科室上下文 |
| **主路径步骤** | 同 **IF-DOR-11**（**AS-OP-DOR-003**）：**ProductDefinition** 分页（支持 **`prepickedProductKeys`** 组套/历史 ids 收窄）→ **ServiceLocationTemplate** 与库存求交、预减（含**同库位多批**预占顺序消耗，**BR-IP-ORD-018**）、默认可发点 → 返回 **`defaultDispenseLocationId`** → 定价 **SALE**（诊疗）/ **LOT_NUMBER_PRICE**（药品）→ 单位 bands 等；**嘱托**固定项可被关键字/类型检索到（**BR-IP-ORD-028**） |
| **关键分支** | 检索范围**仅** `med_medication_definition` + `wkf_activity_definition`（**BR-IP-ORD-012**）；**禁止**返回耗材定义行；**含**名称「嘱托」/`category_code=52` 诊疗行；申请单弹窗同门诊：`categoryEnum` 锚定 21/22/23，可选 **`typeEnum`** 收窄业务类型（术语 `activity_type_code`） |
| **失败分支** | 检索失败 → EX-IP-ORD-201 |
| **查询侧说明** | 纯读 |


---

### IF-IP-DOR-12 查询医嘱绑定信息


| 维度 | 说明 |
| --- | --- |
| **触发前提** | 用户选择需展开绑定的用法或诊疗节点 |
| **主路径步骤** | 同门诊 **IF-DOR-12** 查询形态：`bindType` 为 `USAGE`/`ACTIVITY`；经 `cus_service_device_template` 住院范围（`rangeCode=INPATIENT`）启用态查询并回填展示名；**目标可为诊疗或耗材**。**检验已合管** → 空列表（合管开立/执行均不带，**BR-IP-ORD-036** / **AS-BAS-CFG-001 §7.7**）。本 IF 供护士执行 template 装载等读侧；**医生开立暂存前绑定解析恒空**（不经本 IF 落派生行） |
| **失败分支** | 主键无法解析 → `SERVICE_REQUEST_INVALID_ARGUMENT`；查询失败 → EX-IP-ORD-201 |
| **查询侧说明** | 纯读 |


---

### IF-IP-DOR-13 费用性质下拉（**已废止**）


| 维度 | 说明 |
| --- | --- |
| **状态** | 并入 **AS-IP-DOR-001** 患者列表（或等价就诊上下文接口）响应 **`feeNatureOptions`**，开立 Tab 复用，与门诊 **IF-DOR-01** 一致 |


---

### IF-IP-DOR-18 医嘱开立页初始化（频次/用法）


| 维度 | 说明 |
| --- | --- |
| **触发前提** | 已选定住院就诊 |
| **主路径步骤** | 同门诊 **IF-DOR-18** |
| **查询侧说明** | 纯读 |


---

### IF-IP-DOR-19 按诊疗定义查询可执行科室规则


| 维度 | 说明 |
| --- | --- |
| **触发前提** | 诊疗编辑路径已选 **activityDefinitionId** |
| **主路径步骤** | 同门诊 **IF-DOR-19**（含开立科室过滤 + 单品/业务类型/大类默认合并，见 **DM-CUS-001 v1.5.0**） |
| **关键分支（前端，BR-IP-ORD-023）** | 同门诊 **BR-ORD-024**：**IF-IP-DOR-19** 配置优先，无配置 **`executeLocation/init`** 全院科室；**禁止**「当前登录机构」占位 |
| **失败分支** | 门控失败 → EX-IP-ORD-201 |
| **查询侧说明** | 纯读 |


---

### IF-IP-DOR-14 暂存医嘱变更批次


| 维度 | 说明 |
| --- | --- |
| **触发前提** | 批次行标注增/改/删；就诊上下文有效 |
| **主路径步骤** | 校验就诊 **IMP** 可见状态 → 拒绝耗材类型行（**BR-IP-ORD-012**）→ **不执行库存校验**（**BR-IP-ORD-003**）→ 按 **药品→诊疗**（**BR-IP-ORD-007**）处理 → **长/临/出院带药**行：仅保存/更新/删除 **Request**（**DRAFT**），字段含 **therapy_enum**（含 **`DISCHARGE_MEDICATION`**）、**skinTestFlag**、**entrust_content**、**长期药 `first_dose`/`first_dose_unit_code`（**BR-IP-ORD-030**）**、**长期西药/中成药 `dose_schedule`/`quantity_schedule`（**BR-IP-ORD-037**；段数/正数/成对校验）** 等 → **出院带药**：仅药品行，`therapy_enum=DISCHARGE_MEDICATION`，数量语义同临时（**BR-IP-ORD-029**；皮试/输液**不硬拒**）；**禁止**写首日量与分次 schedule → **嘱托行**：活动定义须为「嘱托」/`category_code=52`，**`entrustContent` 必填**（**BR-IP-ORD-028**；禁止出院带药 therapy）→ **药品行**在 Request 落库后执行 **BR-IP-ORD-024** 皮试联动（`skinTestFlag=true` → 创建/维持 companion「皮试检查」`ServiceRequest`，**不**建 `ChargeItem`；`false`/DELETE → 软删联动 SR）→ 持久化展示排序号（**BR-IP-ORD-011**）→ 全部 **generate_source=DOCTOR_PRESCRIPTION** |
| **关键分支** | **DELETE 长/临/出院带药**：仅删请求；删除皮试药时同步清理联动皮试检查 SR（**BR-IP-ORD-024**）；诊疗删子项。**禁止**前端在 `lines` 中手工提交「皮试检查」诊疗行 |
| **失败分支** | 嘱托正文空 → **EX-IP-ORD-306**；同组药品频次不一致 → **EX-IP-ORD-308**（**BR-IP-ORD-033**）；分次 schedule 非法（段数/正数/非长期）→ **EX-IP-ORD-310**（**BR-IP-ORD-037**）；删除遇已收费 → EX-IP-ORD-102；持久化失败 → EX-IP-ORD-207 |
| **查询侧说明** | 草稿请求事件驱动 **InpatientOrderLookupLoader · InpatientOrderListSkeleton** |


---

### IF-IP-DOR-15 签发医嘱批次


| 维度 | 说明 |
| --- | --- |
| **触发前提** | 批次非空；待签发行已暂存 |
| **主路径步骤** | 生成批次**签署编码** → 药品**处方号** → **自动扩 target**：对批次内每条皮试药，若存在锚定该药的 doctor-prescription 皮试检查 SR 且未在 `signTargets` 中，**追加**至待签发集合（**BR-IP-ORD-024**）→ **Gate pending 调价**（**BR-IP-ORD-034** / **BR-PRC-LOCK-007**）→ **长/临/出院带药**：`signMedicationOrder` / `signServiceRequest`，**对称**签发联动皮试检查 SR，**无** Dispense/费用升位 |
| **关键分支** | 检验检查外发（若有）同门诊配置 |
| **失败分支** | 外发失败（严格模式）→ EX-IP-ORD-205；其它 → EX-IP-ORD-207 |
| **查询侧说明** | 请求生效 |


---

### IF-IP-DOR-16 签退（撤回）医嘱


| 维度 | 说明 |
| --- | --- |
| **触发前提** | 勾选至少一条可撤回医嘱 |
| **主路径步骤** | **长/临/出院带药药品**：`signOffMedicationOrder` → **签退/删除皮试药**时**对称**签退/清理锚定该药的联动皮试检查 SR（**BR-IP-ORD-024**）→ **诊疗**：直接 `withdrawSignedServiceRequest`（**BR-IP-ORD-035 已废，不再断言执行同态**） |
| **失败分支** | 域校验失败 → EX-IP-ORD-207；**EX-IP-ORD-309 预留不抛** |
| **查询侧说明** | 同暂存草稿回写 |

---

### IF-IP-DOR-17 更新药品医嘱组号


| 维度 | 说明 |
| --- | --- |
| **触发前提** | 用户确认并组/拆组 |
| **主路径步骤** | 拆组行组号置空 → 赋组前断言待并入行与目标组既有成员 **`rate_code` 唯一**（**BR-IP-ORD-033**）→ 其余行写入新组号 |
| **失败分支** | 同组频次不一致 → **EX-IP-ORD-308**；更新失败 → EX-IP-ORD-207 |
| **查询侧说明** | 组展示更新 |


---

### IF-IP-DOR-20 停嘱（长期医嘱）


| 维度 | 说明 |
| --- | --- |
| **触发前提** | 用户勾选至少一条**已开嘱校对**的**长期**医嘱并确认停嘱 |
| **主路径步骤** | 按选中行区分药品/诊疗 → 仅 **LONG_TERM** → 断言药品 **VERIFIED** / 诊疗 **VERIFIED**（诊侧门诊 **COMPLETED** 保留）→ 写 **`stop_time`** + **`stopper_id`=当前操作医生** → 清空停嘱校对两字段 → 状态 **STOPPED**（**仅选中行**，**BR-IP-ORD-013**） |
| **关键分支** | 非长期 / **ACTIVE** 未校对 → 拒绝；成组可同批全停 |
| **失败分支** | 状态非法（含 ACTIVE）→ EX-IP-ORD-304 同类；更新失败 → EX-IP-ORD-207 |
| **查询侧说明** | 停嘱事件刷新列表；护士校对 `pending` 出现停嘱待校（`stopAuditPending`） |


> **边界**：护士站「不执行」走 **Procedure**，**不得**调用本 IF。

---

### IF-IP-DOR-25 取消停嘱（长期医嘱）


| 维度 | 说明 |
| --- | --- |
| **触发前提** | 用户勾选至少一条**已停嘱且尚未停嘱校对**的**长期**医嘱并确认取消停嘱 |
| **主路径步骤** | 住院可写门控 → **出院门禁**（**BR-IP-ORD-025**）→ 按行区分药/诊 → 仅 **LONG_TERM** + **STOPPED** + **`stop_performer_check_id` 为空** → 恢复：**`performer_check_id` 非空 → `VERIFIED`，否则 → `ACTIVE`** → 清空 **`stop_time`/`stopper_id`/停嘱校对两字段** → **不**联动子嘱 |
| **关键分支** | 已停嘱校对 → 拒绝（提示先取消停嘱校对）；非 STOPPED / 非长期 → 拒绝；Procedure/Dispense **不**拦；**不**校验停嘱距今天数 |
| **失败分支** | 状态/长临/已停嘱校对 → EX-IP-ORD-304；出院门禁 → EX-IP-ORD-305；持久化失败 → EX-IP-ORD-207 |
| **查询侧说明** | 取消停嘱事件刷新列表；护士停嘱待校计数同步 |


> **边界**：与 IF-IP-DOR-20 对称；**不得**误触护士站 Procedure「不执行/取消执行」写路径。

---

### IF-IP-DOR-26 改开立时间或停嘱时间


| 维度 | 说明 |
| --- | --- |
| **触发前提** | 用户在住院医嘱列表选定行，选择改**开立时间**或改**停嘱时间**，确认新时间 |
| **主路径步骤** | 住院可写门控（**BR-IP-ORD-027**）→ 按行加载药/诊 Request → 校验时间种类：`OPENED` 写 `req_opened_time`（**不**改 `requester_id`）；`STOP` 写 `stop_time`（须长期且已停嘱；**不**改 `stopper_id`；**保留**停嘱校对两字段）→ **拒绝**任何对 `req_authored_time` 的写入意图 → 持久化 → 刷新列表 |
| **关键分支** | 临时/出院带药仅允许改开立；非已停嘱行改停嘱 → 拒绝；时间种类非法 → 拒绝 |
| **失败分支** | 门禁 → EX-IP-ORD-107；种类/状态非法 → EX-IP-ORD-307；持久化失败 → EX-IP-ORD-207 |
| **查询侧说明** | 纯列更新；列表按开立时间重排；护士站读同源列 |


> **边界**：**本期仅住院**；门诊不提供本 IF。改时**不**落「改时人」。签发时间**永远不可改**。

---

### IF-IP-DOR-27 追加皮试检查（长期皮试药·换批）


| 维度 | 说明 |
| --- | --- |
| **触发前提** | 用户勾选至少一条**长期**且已勾**皮试**的药品行（可参考列表 `needAppendSkinTestFlag` 跨批提示），点击「追加皮试」 |
| **主路径步骤** | 住院可写门控（**BR-IP-ORD-027**）→ 逐药校验：归属本就诊、`therapy=LONG_TERM`、`skinTestFlag=true`、非 STOPPED → 若已有**草稿** companion「皮试检查」则复用；否则**新建**临嘱 companion（`based_on→该药`，诊断/执行科室/费用性质继承，**不**挂费，**BR-IP-ORD-024③**）→ 医生再走 **IF-IP-DOR-15** 签发（自动扩草稿 companion） |
| **关键分支** | 临嘱皮试药不走本 IF（仍用首次勾选联动）；已有未签发草稿 → 不重复建；同一长期药允许多条已签发 companion（护士分行登记） |
| **失败分支** | 门禁 → EX-IP-ORD-107；非长期/非皮试/停嘱 → EX-IP-ORD-304；持久化失败 → EX-IP-ORD-207 |
| **查询侧说明** | 列表刷新后可见新临嘱行；皮试 Tab 多一行；`skinTestOutcome*` 仍最新覆盖 |


> **边界**：禁止前端手工拼「皮试检查」诊疗行；`needAppendSkinTestFlag` 仅软提示，**不**强制本 IF。

---

### IF-IP-DOR-28 长期药品改量


| 维度 | 说明 |
| --- | --- |
| **触发前提** | 用户选中一条**长期药品**行（`VERIFIED` 在用，或 `STOPPED` 已停），点击「改量」；弹窗带出旧嘱字段并提交变更（**禁止**改 `medicationId`） |
| **主路径步骤** | 住院可写门控（**BR-IP-ORD-027**）→ 加载源 `MedicationRequest`：同就诊、长期、药品 → **若 VERIFIED**：停嘱（`stopTime` 可传，默认当前；写 `stopper_id`；清停嘱校对字段，**BR-IP-ORD-013**）→ **若已 STOPPED**：跳过停嘱 → 按请求体创建新长期药品**草稿**（复制/覆盖可改字段；`medication_id`=源；`based_on_table=med_medication_request`、`based_on_id=源 id`；新 `bus_no`/`req_opened_time`）→ 返回新草稿 id → 前端刷新列表；医生再 **IF-IP-DOR-15** 签发 |
| **关键分支** | 多次改量：新嘱可再基于上一新嘱，形成链；未领第一盒即改量 → 无剩余结转（执行侧按新嘱独立出第一盒）；观察期已停改量 → 冻结剩余挂旧条直至本 IF 开出子嘱 |
| **失败分支** | 门禁 → EX-IP-ORD-107；非长期药品 / 状态非 VERIFIED 且非 STOPPED / 试图改药品 → EX-IP-ORD-304；停嘱或建草稿失败 → EX-IP-ORD-207 |
| **查询侧说明** | IF-IP-DOR-10/22 对链尾/观察期行回显口服剩余（**BR-IP-EXE-038**）；旧嘱被指走后不再挂剩余 |


> **边界**：**仅药品**；不替代签退/删除；不自动签发；手停后再开且未走本 IF（无 basedOn）不续算剩余。

---

### IF-IP-DOR-21 开立出院医嘱


| 维度 | 说明 |
| --- | --- |
| **触发前提** | 患者在院；出院医嘱表单完整（计划出院时间、出院方式、原科室/病区等）；出院方式为死亡时须填死亡时间 |
| **主路径步骤** | 校验就诊在院（**InpatientEncounterContextQueryModel** 门控）→ 死亡出院校验死亡时间必填 → 校验同就诊无重复 **DRAFT/ACTIVE** 转科/出院 **ServiceRequest**（**BR-SRQ-010**）→ 创建 **ServiceRequest**（**BR-IP-ORD-014**：`category_enum=34`、`therapy_enum=TEMPORARY`、`generate_source_enum=DOCTOR_PRESCRIPTION`、`reason_text`、`occurrence_start_time`=计划出院时间、数量 1）→ 同事务 **EncounterHistory.recordDischargeMilestone**（`out_way_code`、原/目标科室与位置，锚定 `request_id` + `wkf_service_request`）→ 出院方式为死亡时同事务 **Patient.markPatientDeceased**（死亡时间、死亡原因）→ 发布 **DischargeRequestCreatedEvent** / **DischargeMilestoneRecordedEvent**；死亡时另发布 **PatientDeceasedMarkedEvent** |
| **关键分支** | 计划出院时间为空则取当前时间；与转科（`category_enum=32`）互斥（重复校验已覆盖）；`reason_text` 优先出院原因，为空则用死亡原因；非死亡不标记患者死亡 |
| **失败分支** | 重复开立 → EX-IP-ORD-302（或与转科共用文案）；死亡出院未填死亡时间 → EX-IP-ORD-311；余量未用完 → EX-IP-ORD-301（**BR-SRQ-018**，实现期可后置）；死亡时间晚于当前 → 领域 **BR-PAT-011** 拒绝 |
| **列表回显** | **IF-IP-DOR-10** 按 **BR-IP-ORD-017** 展示；`out_way_code` 自里程碑，`reason_text` / `occurrence_start_time` 自 **ServiceRequest**；死亡出院主文案为「死亡出院」 |
| **查询侧说明** | **InpatientOrderLookupLoader · InpatientOrderListSkeleton** 增出院行；**InpatientEncounterContextQueryModel** 待护士校对后变更就诊展示态（下游） |


---

### IF-IP-DOR-23 保存申请单（整张）

同门诊 **IF-DOR-21**（**BR-IP-REQ-001～008**）。住院差异：**临嘱**路径建 **`ChargeItem` 草稿**；**长期**申请单无产品入口（**BR-IP-REQ-007** 文档口径）；就诊门控 **IMP** + **`InpatientEncounterContextQueryModel`**。**整单诊断**：请求 **`encounterDiagnosisIds`**（有序 ids 串，主诊 id 第一）与 **`orgId` 同级**（见 **AS-OP-DOR-003-SDD §3.5.4**）。

**编辑口径（与门诊一致）**：请求传 **`previousPrescriptionNo`**（旧 `AR` 号）→ 校验旧号下**全部为 DRAFT** → **`removeServiceRequestDraft`**（连带 Detail）+ 作废未计费费用 → **`getSeqByDay` 取新 `AR` 号**重建 → 响应返回**新** `prescriptionNo`（**不沿用**旧号，**BR-IP-REQ-002**）。

---

### IF-IP-DOR-24 查询申请单列表

同门诊 **IF-DOR-22**；读侧经 **`InpatientDoctorStationAdviceListAssembler`** 或专用装配器按 `prescription_no`（`AR`）分组。


---

## 5. 业务异常与用户提示


| 语义代码 | 场景 | 用户提示方向 | 可重试 |
| --- | --- | --- | --- |
| EX-IP-ORD-102 | 删除行存在已收费费用 | 已收费无法删除，请刷新核对 | 否 |
| EX-IP-ORD-103 | 签退存在已收费费用 | 已收费无法签退 | 否 |
| EX-IP-ORD-309 | **预留不抛**：历史签退同态码（**BR-IP-ORD-035 已废**） | — | — |
| EX-IP-ORD-107 | 就诊已待结算/已结算/已离院 | 患者已办理出院或已结算，不可变更医嘱及费用 | 否 |
| EX-IP-ORD-106 | 签退无关联费用 | 无关联账单数据 | 否 |
| EX-IP-ORD-201 | 只读查询失败 | 稍后重试 | 是 |
| EX-IP-ORD-205 | 签发外发失败（严格） | 提示原因并联系管理员 | 视配置 |
| EX-IP-ORD-207 | 写路径持久化失败 | 稍后重试 | 视原因 |
| EX-IP-ORD-304 | 停嘱：非长期/非已开嘱校对；取消停嘱：非长期/非 STOPPED/已停嘱校对 | 仅已校对长期可停嘱；已停嘱且未停嘱校对方可取消停嘱（已停嘱校对须先取消停嘱校对） | 否 |
| EX-IP-ORD-305 | 取消停嘱：出院/转科门禁 | 存在出院或转科申请，无法取消停嘱 | 否 |
| EX-IP-ORD-301 | 出院：吸氧/心电时长余量未用完 | 展示剩余小时数（与老系统文案一致） | 否 |
| EX-IP-ORD-302 | 出院：重复开立 | 请勿重复开立出院医嘱 | 否 |
| EX-IP-ORD-311 | 死亡出院：死亡时间为空 | 死亡时间不能为空 | 否 |
| EX-IP-ORD-306 | 嘱托医嘱：`entrustContent` 空或空白 | 请填写嘱托内容 | 否 |
| EX-IP-ORD-307 | 改业务时间：时间种类非法 / 非已停嘱改停嘱 / 试图改签发时间 | 不可修改该时间 | 否 |
| EX-IP-ORD-308 | 同组成组药品频次不一致（暂存/并组，**BR-IP-ORD-033**） | 同组成组药品的用药频次须一致，请统一后再保存或并组 | 否 |
| EX-IP-ORD-310 | 分次用量不一致：schedule 段数≠频次日次数 / 非正数 / 两列不成对 / 非长期西药中成药携带（**BR-IP-ORD-037**） | 请按频次填全各次剂量，或关闭「用量不一致」 | 否 |
| EX-IP-REQ-101 | 申请单编辑时旧号不可编辑 | 同门诊 **EX-REQ-101** | 否 |
| EX-IP-REQ-102 | 申请单校验失败 | 同门诊 **EX-REQ-102** | 视情况 |
| ~~EX-IP-ORD-303~~ | ~~出院：目录未维护~~ | **本期实现**不强制绑定诊疗目录 `ActivityDefinition`；若未来改为目录锚点再启用 | — |


---

## 6. 事件语义与消费方


| 事件语义（DM 命名） | 触发相位 | 典型消费方 |
| --- | --- | --- |
| 药品/诊疗请求草稿创建、签发、签退、删除类 | IF-IP-DOR-14～16 | 各 Request `*QueryService` 列表刷新；护士站列表；**护士皮试 Tab**（联动皮试检查 SR，**AS-IP-NUR-011**） |
| 长期医嘱已停嘱（`LongTermMedicationOrderStoppedEvent` 等） | IF-IP-DOR-20 | **InpatientOrderLookupLoader · InpatientOrderListSkeleton**；护士停嘱校对 |
| 服务申请已停止（`ServiceRequestStoppedEvent`） | IF-IP-DOR-20 | 同上 |
| 长期医嘱已取消停嘱（`LongTermMedicationOrderStopCancelledEvent` 等） | IF-IP-DOR-25 | **InpatientOrderLookupLoader · InpatientOrderListSkeleton**；护士执行/出院门禁读侧 |
| 服务申请已取消停嘱（`ServiceRequestStopCancelledEvent`） | IF-IP-DOR-25 | 同上 |
| 出院申请已创建（`DischargeRequestCreatedEvent`） | IF-IP-DOR-21 | 护士校对、就诊待出院态 |
| 离院里程碑已记录（`DischargeMilestoneRecordedEvent`） | IF-IP-DOR-21 | 病案、**InpatientEncounterContextQueryModel** 轨迹 |
| 患者已标记死亡（`PatientDeceasedMarkedEvent`） | IF-IP-DOR-21（仅死亡出院） | 患者主档、统计 |
| 收费明细草稿/状态迁移（仅临时） | IF-IP-DOR-14～16 | 住院费用摘要、护士执行计费 |


> **不包含** Procedure「不执行」相关事件（护士站 AS）。

---

## 7. 非功能性需求


| 类别 | 要求 |
| --- | --- |
| **性能（经验目标）** | IF-IP-DOR-10 ≤1200ms；IF-IP-DOR-11 ≤1500ms；IF-IP-DOR-14 ≤3000ms；IF-IP-DOR-15 ≤4000ms；IF-IP-DOR-20 ≤1500ms；IF-IP-DOR-25 ≤1500ms；IF-IP-DOR-26 ≤1500ms；IF-IP-DOR-21 ≤2000ms |
| **幂等** | 批次重复提交依赖请求主键约束；停嘱重复策略以领域命令幂等为准 |
| **并发** | 双端编辑同一就诊以后写或冲突提示，SDD 固化 |
| **一致性** | 出院 **ServiceRequest**、**EncounterHistory** 里程碑与（死亡时）患者死亡标记同事务，任一失败全回滚；长期不写费用避免孤儿账单 |
| **审计** | 签署编码、停嘱时间、出院方式可追溯 |


---

## 8. 测试要点（业务视角）


| 类别 | 要点 |
| --- | --- |
| **正向** | 长/临/**出院带药**筛选；三种 therapy 暂存/签发均仅 Request；列表无护士划价；**出院带药**：类型列「出院带药」、仅药品、同临嘱签发（**BR-IP-ORD-029**）；**首日用量**：长期药可填 `firstDose` 暂存/列表回显；临时/出院带药不可填（**BR-IP-ORD-030**）；**分次用量不一致**：长期西药日二次填 `2,1` → `doseSchedule`/`quantitySchedule` 落库；列表回显序列；段数≠日次数拒存；关开关后 schedule 清空回落单剂量（**BR-IP-ORD-037**）；**IF-IP-DOR-14** 皮试联动 SR（**不**挂费，**BR-IP-ORD-024**）；**IF-IP-DOR-15** 签发皮试药自动扩联动 SR；护士校对后皮试 Tab 可见；护士登记后医生站回显 `skinTestOutcome*`；**嘱托**：目录可见「嘱托」→ 录入正文暂存/签发 → 列表类型「嘱托/长临」、内容=`entrustContent`（**BR-IP-ORD-028**）；目录无耗材；**IF-IP-DOR-11** 同库多批预占顺序消耗（例 10+5−9=6，**BR-IP-ORD-018**）；停嘱仅长期→**STOPPED** + 停嘱人；**IF-IP-DOR-25** 取消停嘱：清停嘱人；**IF-IP-DOR-26** 改开立时间开立人不变、改停嘱时间停嘱人不变、签发不可改；列表开立/停嘱时间后跟人名；出院门禁拦截取消停嘱/改时；出院开立后列表展示（**BR-IP-ORD-017**）；重复出院拦截、里程碑写入；**死亡出院**：必填死亡时间，回写患者死亡时间/原因，列表与护士站主文案「死亡出院」；非死亡主文案仍为「出院医嘱」且不改患者死亡字段 |
| **异常** | EX-IP-ORD-102/103/301/302/303/304/305/**306**/**307**/**308**/**310**/**311** |
| **规则** | BR-IP-ORD-003 三种 therapy 均不校验库存；BR-IP-ORD-005/006 分轨；BR-IP-ORD-013 停嘱仅选中行+停嘱人；**BR-IP-ORD-025** 取消停嘱恢复规则；**BR-IP-ORD-024** 皮试联动幂等、禁止手工加皮试检查行；**BR-IP-ORD-028** 嘱托正文必填、展示与下游不计费意图；**BR-IP-ORD-029** 出院带药模型/展示/软约定与下游发放意图；**BR-IP-ORD-030** 仅长期药首日量、与单次用量分治、执行侧意图见护士 AS；**BR-IP-ORD-037** 分次 schedule 无痛切换、段数校验、不波及停嘱/出院办理；**BR-IP-ORD-031** 三时间轴与改时/人名 |
| **边界** | 护士「不执行」不走 IF-IP-DOR-20；出院带药皮试/输液**软约定不硬拒**；门诊禁开嘱托/出院带药/改时间不在本文 IF；汇总过滤/逐行发药/出院待发药门禁落护士/药房 AS；首日用量执行槽位/`actualTime`/换量落 **AS-IP-NUR-004**；分次 schedule 分槽算量落 **AS-IP-NUR-004 · BR-IP-EXE-040**；护士站时间人名展示承接合约另开护士 AS 修订 |


---

## 9. 关联文档与变更历史

### 9.1 关联文档


| 文档 |
| --- |
| `health/AppService-SDD/AS-IP-DOR-003-住院医生站医嘱开立-SDD.md`（**v1.60**，2026-10-08） |
| `health/AppService-SDD/AS-OP-DOR-003-门诊医生站医嘱开立-SDD.md`（**v3.49**，2026-08-18；IF-IP-DOR-11 母版规则 **D**） |
| `health/AppService/AS-IP-NUR-011-住院护士站皮试管理.md` |
| `health/AppService/AS-IP-NUR-003-住院护士站医嘱校对.md`（出院带药校对同临嘱） |
| `health/AppService/AS-IP-NUR-004-住院护士站医嘱执行.md`（嘱托执行不计费；出院带药执行出账+Dispense 且 **`summary_no` 空**；**首日用量**槽位/`actualTime`/首次换量承接 **BR-IP-ORD-030⑥**；**分次 schedule 分槽算量 BR-IP-EXE-040**；**口服剩余 BR-IP-EXE-038**，**v2.61**） |
| `health/设计决策/DD-住院长嘱口服一盒一点剩余计数与改量-2026-08.md`（改量链 + 剩余计数裁决） |
| `health/AppService/AS-IP-NUR-002-住院护士站入出转管理.md`（出院待发药门禁含出院带药） |
| `health/AppService/AS-IP-NUR-010-住院护士站汇总领药.md`（汇总过滤出院带药发放） |
| `health/AppService/AS-IP-PHA-001-住院发药.md`（逐行发药承接） |
| `health/AppService/AS-IP-PHA-002-住院汇总发药.md`（汇总不可见出院带药） |
| `health/AppService/AS-OP-DOR-003-门诊医生站医嘱开立.md`（门诊禁开嘱托/出院带药承接） |
| `health/AppService/AS-IP-DOR-001-住院医生站就诊管理.md` |
| `health/AppService/AS-IP-DOR-002-住院医生站诊断录入.md` |
| `docs/领域/DomainModel/DM-MED-003-MedicationRequest-药品申请.md` |
| `docs/领域/DomainModel/DM-MED-002-MedicationDispense-药品发放.md` |
| `docs/领域/DomainModel/DM-WKF-004-ServiceRequest-服务申请.md` |
| `docs/领域/DomainModel/DM-WKF-001-ActivityDefinition-诊疗项目定义.md` |
| `docs/领域/DomainModel/DM-CUS-004-EncounterHistory-就诊历史.md` |
| `docs/领域/DomainModel/DM-ADM-011-ChargeItem-收费明细.md` |
| `docs/领域/DomainModel/DM-CUS-006-ServiceLocationTemplate-取药科室.md` |
| `DM §6.2（DM-MED-001 / DM-WKF-001 / DM-ADM-008 §6.2 目录 QueryService` |
| `DM §6.2（ServiceRequestQueryService / MedicationRequestQueryService 等（DM §6.2）` |
| `DM §6.2（DM-ADM-002 §6.2 EncounterQueryService（InpatientEncounterContextQueryModel）` |


### 9.2 变更历史


| 版本 | 日期 | 变更摘要 |
| --- | --- | --- |
| v1.58 | 2026-10-15 | **代码先行回写**：**BR-IP-ORD-028③⑦**：嘱托频次仅展示；护士执行整单一次（承接 **AS-IP-NUR-004 · BR-IP-EXE-042**）；§1.9 同步 |
| v1.57 | 2026-10-12 | **剂量展示**：IF-IP-DOR-10/22 列表 `dosageDisplay`/`dailyDoseDisplay` 经 `MedicationDosageDisplayAssembler`——目录 `injectFlag` 且拆零&lt;1 主显计量单位（与护士站 OrderView 同源）；对齐 **AS-IP-NUR-003 v1.29** |
| v1.56 | 2026-10-11 | **BR-IP-REQ-005**（随门诊 **BR-REQ-005**）：检查部位 / 检验样本 / 治疗部位均选填；与 **AS-OP-DOR-003 v1.75** / **AS-OP-DOR-003-SDD v3.60** 同口径 |
| v1.55 | 2026-10-08 | **BR-IP-ORD-038**：诊疗行执行科室 `performLocation*` 优先 `org_id` 组织名；医嘱内容扩展行「医技已执行/待执行」**同段展示执行科室**；对齐 **AS-SDD v1.61** |
| v1.54 | 2026-10-08 | **死亡出院**：**BR-IP-ORD-014** 出院方式 code=`5` 时死亡时间必填、同事务 **markPatientDeceased**；**BR-IP-ORD-017** 主文案「死亡出院」（护士站 OrderView 同源）；**EX-IP-ORD-311**；对齐 **AS-SDD v1.60** |
| v1.53 | 2026-10-07 | **IF-IP-DOR-19**：执行科室叠加可选业务类型层；对齐 **DM-CUS-001 v1.5.0** / **AS-SDD v1.59** |
| v1.52 | 2026-10-06 | **IF-IP-DOR-11**：申请单分类 chips 按可选 **`typeEnum`**（术语 `activity_type_code`）查询；`categoryEnum` 仍锚定 21/22/23；对齐 **DM-WKF-001 v1.0.17** / **AS-SDD v1.58** |
| v1.51 | 2026-09-30 | **BR-IP-ORD-038**：IF-IP-DOR-10/22 TECH 执行科诊疗下发医技净执行进度（`techPlanned*`/`techCompleted*`/`techCancelled*`）；医嘱内容扩展行「医技已执行」/「医技待执行」（不加列）；对齐 **AS-MT-EXE-001** / 协同 DD；**AS-SDD v1.57** |
| v1.50 | 2026-09-23 | **BR-IP-ORD-037 / §1.14**：长期西药分次用量不一致（`doseSchedule`/`quantitySchedule`）；查询/暂存校验；下游算量意图交 **AS-IP-NUR-004 · BR-IP-EXE-040**；**G-IP-DOR-003-07**；对齐 **DM-MED-003 v1.3.38 / BR-MRQ-035** |
| v1.49 | 2026-09-22 | **BR-IP-ORD-036**：住院开立不带任何绑定（诊疗/耗材/合管）；未合管模板仅护士执行 **BR-IP-EXE-013**；对齐 **AS-BAS-CFG-001 §7.7** / **AS-IP-NUR-004 v2.60** |
| v1.48 | 2026-09-21 | **BR-IP-ORD-035 废止**：签退不再同态硬拦（协同 DD 软落地） |
| v1.47 | 2026-09-16 | **BR-IP-ORD-030 / §1.11**：首日用量 UI 按计量单位录入，落库换算最小单位（与长期 `quantity` 同口径）；执行首次槽与常规长嘱同路径 |
| v1.47 | 2026-09-19 | **BR-IP-ORD-035 / EX-IP-ORD-309**：IF-IP-DOR-16 签退 TECH 诊疗须护士/医技执行同态（停嘱除外）；见协同 DD |
| v1.46 | 2026-09-16 | **BR-IP-ORD-034**：IF-IP-DOR-15 签发前 pending 调价互斥；对齐 **AS-FIN-PRC-001 v0.10** |
| v1.45 | 2026-09-03 | **BR-IP-ORD-026⑥**：护理级别仅「护理」快捷开立；普通选品下拉前端按 `categoryCode=26` 过滤；快捷开立固定长期+`QD`（每日一次），后端透传；对齐 **AS-SDD v1.53** |
| v1.44 | 2026-09-01 | **BR-IP-ORD-033 / EX-IP-ORD-308**：同组成组药品频次须一致；IF-IP-DOR-14/17 Gate；前端同组同步频次、禁目录默认覆盖；对齐 **AS-SDD v1.52** |
| v1.43 | 2026-08-19 | **改量收口**：在用改量 UI 传可编辑 `stopTime`；口服剩余未领盒不展示 0/0；拆零单位用字典名；对齐 **AS-SDD v1.51** / **DD** |
| v1.42 | 2026-08-19 | **IF-IP-DOR-28 / BR-IP-EXE-038 成码落地**：改量写路径 + 口服剩余同源 Support；UI「改量」已落地；对齐 **AS-SDD v1.50** / **AS-IP-NUR-004** / **DD** |
| v1.41 | 2026-08-19 | **IF-IP-DOR-28 / BR-IP-ORD-032**：长期药品改量（停旧+同药重开、`based_on` 链、观察期冻结）；列表口服剩余展示与 **BR-IP-EXE-038** 同源；对齐 **DD-住院长嘱口服一盒一点剩余计数与改量-2026-08**、**AS-IP-NUR-004 v2.51**、**AS-IP-DOR-003-SDD v1.49** |
| v1.46 | 2026-09-16 | **§1.11.3 列表展示**：剂量列拼接「首日{数量}{单位}」；`firstDose*` 须下发（与护士站校对/执行同源） |
| v1.40 | 2026-08-18 | **IF-IP-DOR-11 同库多批预减**：**BR-IP-ORD-018** 目录占用预减与门诊 **BR-ORD-011/021** 对齐（FIFO 批次遍历顺序消耗库位预占；例 10+5−9=6）；§8 测试要点同步；对齐 **AS-IP-DOR-003-SDD v1.48** / **AS-OP-DOR-003-SDD v3.49** |
| v1.39 | 2026-08-08 | **停嘱校对门禁**：停嘱须 **VERIFIED**（诊侧 **COMPLETED** 门诊保留）；取消停嘱须未停嘱校对；取消停嘱清停嘱校对字段；改停嘱时间保留停嘱校对；对齐 **AS-IP-NUR-003 v1.16** / **DM-MED-003** / **DM-WKF-004** |
| v1.38 | 2026-08-04 | **输液滴速**：开立/列表/护士回显 `speedCode`；IF-IP-DOR-14/18；对齐 **AS-IP-DOR-003-SDD v1.45** / **DM-MED-003** |
| v1.37 | 2026-08-02 | **IF-IP-DOR-18**：申请单检查部位 / 检验样本下拉支持名称·编码·拼音·五笔模糊过滤（同门诊 **AS-OP-DOR-003 v1.58**）；对齐 **AS-IP-DOR-003-SDD v1.44** |
| v1.36 | 2026-07-26 | **BR-IP-ORD-024**：签退 companion 仅 ACTIVE、同批药+companion 去重；列表关联文案**可含规格**；与 **IF-IP-DOR-27** 多 companion 口径合并；对齐 **AS-IP-DOR-003-SDD v1.43** |
| v1.35 | 2026-07-22 | **BR-IP-ORD-024**：允许多 companion；新增 **IF-IP-DOR-27** 长期皮试药快捷追加「皮试检查」；列表 `needAppendSkinTestFlag`；对齐 **AS-IP-NUR-011** 多行 / **BR-IP-EXE-034** |
| v1.34 | 2026-07-22 | **BR-IP-ORD-024⑤ / §1.8**：皮试操作费改由 **AS-IP-NUR-004** 执行计费；皮试 Tab 仅登记结果；对齐 **AS-IP-NUR-011 v1.4** |
| v1.33 | 2026-07-18 | **App IF-IP-DOR-26 已落地**：`changeInpatientDoctorStationAdviceBusinessTime`；列表/护士站开立·停嘱时间+人名；对齐 **AS-IP-DOR-003-SDD v1.39** |
| v1.32 | 2026-07-18 | **G-IP-DOR-003-06 Closed**：领域 `stopper_id` + 改时命令 + V5/V6 DDL / 本地 SQL 落地；App IF-IP-DOR-26 当时仍为目标态（已被 v1.33 关闭） |
| v1.31 | 2026-07-18 | **三时间轴与改业务时间**：§1.12 / **BR-IP-ORD-031**；**IF-IP-DOR-26**；停嘱写 **`stopper_id`**、取消停嘱清空；列表开立/停嘱时间后跟人名；签发不可改、不主展示；**EX-IP-ORD-307**；**G-IP-DOR-003-06 Open**；对齐 **AS-IP-DOR-003-SDD v1.37** / **DM-MED-003** / **DM-WKF-004** |
| v1.40 | 2026-08-17 | **执行记录（UI）**：列表行操作「执行记录」只读弹窗——默认全部，可筛已执行/不执行/取消执行（不含待执行）；按该行 `requestId`（成组含成员）；**复用 IF-IP-EXE-01**，不新开写 IF；对齐 **AS-IP-DOR-003-SDD v1.48** |
| v1.30 | 2026-07-17 | **BR-IP-ORD-024**：皮试 companion SR 固定 `therapy_enum=TEMPORARY`（对齐门诊 BR-ORD-025；纠正原「继承皮试药长临」口径，无长期皮试）；对齐 **AS-IP-DOR-003-SDD v1.36** |
| v1.29 | 2026-07-16 | **首日用量（§1.11）**：新增 **BR-IP-ORD-030**（仅长期药；语义为**首次**用量；开嘱相对频次点裁剪 + 双规 `actualTime` + 仅首次槽换量的产品意图交 **AS-IP-NUR-004**；频次时钟禁止写死）；IF-IP-DOR-10/14 透传/回显；对齐 **AS-IP-DOR-003-SDD v1.35**（应用已落地） / **AS-IP-NUR-004 v2.34** |
| v1.28 | 2026-07-16 | **BR-IP-ORD-028④ 展示同源**：护士站 **OrderView**/`adviceDisplayName`、**ATD-03** `displayName` 与医生站共用 **`InpatientEntrustOrderGateSupport#resolveServiceAdviceDisplayName`**；对齐 **AS-IP-NUR-003-SDD v1.3.4** / **AS-IP-DOR-003-SDD v1.33** |
| v1.27 | 2026-07-16 | **嘱托/出院带药 App 编排 Closed**：暂存透传 `entrustContent`/`DISCHARGE_MEDICATION`；Gate `EX-IP-ORD-306`；列表文案对齐；对齐 **AS-IP-DOR-003-SDD v1.32** |
| v1.26 | 2026-07-16 | **G-IP-DOR-003-05 Closed**：`TherapyType.DISCHARGE_MEDICATION` + **DM-MED-003 v1.3.24** / **DM-WKF-004 v1.1.23**；对齐 **AS-IP-DOR-003-SDD v1.31**（应用层暂存/签发接枚举仍为目标态，已被 v1.27 关闭） |
| v1.25 | 2026-07-16 | **出院带药（§1.10）**：新增 **BR-IP-ORD-029**（`TherapyType.DISCHARGE_MEDICATION`；仅药品；开立同临嘱；类型列「出院带药」；皮试/输液软约定；下游不写 `summary_no`/仅逐行发药/出院待发药门禁意图）；§1.7 三列分轨；**G-IP-DOR-003-05 Open**；对齐 **AS-IP-DOR-003-SDD v1.30** |
| v1.24 | 2026-07-16 | **纠正**：嘱托识别改 **`category_code=52`**（撤回 `ActivityType.ENTRUST`）；**BR-IP-ORD-028** / Gap 关闭条件同步；对齐 **DM-WKF-004 v1.1.22** / **AS-IP-DOR-003-SDD v1.29** |
| v1.23 | 2026-07-16 | **G-IP-DOR-003-04 Closed**（初版误用 ActivityType，已被 v1.24 纠正为 category 52） |
| v1.22 | 2026-07-16 | **嘱托医嘱（§1.9）**：新增 **BR-IP-ORD-028**；**EX-IP-ORD-306**；对齐 **AS-IP-DOR-003-SDD v1.27** |
| v1.21 | 2026-07-11 | **BR-IP-ORD-027**：就诊 `PENDING/COMPLETED/DISCHARGED` 禁止医嘱写；**EX-IP-ORD-107**；对齐 **BR-IP-FLW-008** |
| v1.20 | 2026-06-24 | **IF-IP-DOR-18 身体部位树形下拉**：与门诊 **IF-DOR-18** 同构（`bodyStructureOptions` 树形 ACTIVE）；对齐 **DM-DIA-001 v1.0.2** / **AS-IP-DOR-003-SDD v1.25** / **AS-OP-DOR-003 v1.48** |
| v1.19 | 2026-06-20 | **orchestration 落点对齐**：皮试/申请单/挂费上收 `module.orchestration.advice`；详见 **AS-IP-DOR-003-SDD v1.24** |（已迁至 application.shared.advice）
| v1.18 | 2026-06-20 | **support 落点对齐**：`DoctorStationSkinTestSyncOrchestrationsupport、`ServiceApplyFormOrchestrationsupport → `module/orchestration/advice` |
| v1.17 | 2026-06-18 | **IF-IP-DOR-25 取消停嘱成码闭环**：前后端 + 域写能力落地；**G-IP-DOR-003-03 Closed**；UI 增补「取消停嘱」按钮；对齐 **AS-IP-DOR-003-SDD v1.22** |
| v1.16 | 2026-06-18 | **IF-IP-DOR-25 取消停嘱（长期）**：**BR-IP-ORD-025**（`performer_check_id` 推断恢复 **VERIFIED/ACTIVE**；出院门禁；不级联子嘱）；**BR-IP-ORD-013** 与实现对齐为「仅停选中行」；**G-IP-DOR-003-03** 域写能力 Gap；对齐 **AS-IP-DOR-003-SDD v1.21** |
| v1.15 | 2026-06-18 | **住院皮试联动（§1.8）**：新增 **BR-IP-ORD-024**——药品行 `skinTestFlag` 联动 doctor-prescription「皮试检查」SR（**不**挂费）；**IF-IP-DOR-14/15/16** 同步；下游对齐 **AS-IP-NUR-011 v1.1**（护士执行后计费）；**BR-IP-ORD-016** 增补皮试结果回显；对齐 **AS-IP-DOR-003-SDD v1.19** |
| v1.14 | 2026-06-17 | **架构决议**：住院长临暂存/签发**仅 Request**（**BR-IP-ORD-006/009**）；FIFO/Charge 迁至护士执行（**AS-IP-NUR-004**）；**BR-IP-ORD-018 废止（开立侧）**；门诊临嘱不变；对齐 **AS-IP-DOR-003-SDD v1.18** |
| v1.13 | 2026-06-17 | **BR-IP-ORD-023** 诊疗执行科室下拉与门诊 **BR-ORD-024** 对齐；对齐 **AS-OP-DOR-003 v1.43** / **AS-OP-DOR-003-SDD v3.29** |
| v1.12 | 2026-06-17 | **IF-IP-DOR-19 / DM-CUS-001 v1.2.0**：执行科室支持大类默认规则；同门诊 **IF-DOR-19** 合并项目级 + 大类默认；对齐 **AS-IP-DOR-003-SDD v1.16**。 |
| v1.11 | 2026-06-16 | **IF-IP-DOR-23 整单诊断绑定**：同门诊 **BR-REQ-008**；对齐 **AS-OP-DOR-003-SDD v3.26** / **AS-IP-DOR-003-SDD v1.14** |
| v1.10 | 2026-06-16 | **成码对齐**：引用 **AS-IP-DOR-003-SDD v1.13**（复用门诊 `ServiceApplyFormAssembler`） |
| v1.9 | 2026-06-16 | **申请单**：**IF-IP-DOR-23/24**；**BR-IP-REQ-001～008**；编辑**取新 `AR` 号**；**G-IP-DOR-003-02 已关闭**（**DM v1.1.14**）；对齐 **AS-IP-DOR-003-SDD v1.12** |
| v1.8 | 2026-06-11 | **IF-IP-DOR-11/14 库位透传**：`prepickedProductKeys` 收窄目录；临嘱暂存药耗经 `resolveDraftChargePriceLine` + 前端回传 `dispenseLocationId`（长期医嘱不适用） |
| v1.8 | 2026-06-12 | **IF-IP-DOR-22 读口径收敛为全类型医嘱**：`pageInpatientDoctorStationAdvice`；行 DTO 与 **IF-IP-DOR-10** 同构；仅 `adviceScopeTabCode` 区分当前/历史；对齐 **AS-IP-DOR-003-SDD v1.11** |
| v1.7 | 2026-06-12 | **IF-IP-DOR-22 响应收敛**：药品医嘱查询响应**不含候选库存**；对齐 **AS-IP-DOR-003-SDD v1.10** |
| v1.6 | 2026-06-12 | 新增 **IF-IP-DOR-22** 药品医嘱分页查询（当前医嘱/历史医嘱 Tab）；是否按处方展示由前端处理；对齐门诊 **IF-DOR-20** 与 **AS-IP-DOR-003-SDD v1.9** |
| v1.5 | 2026-05-28 | **BR-IP-ORD-006/BR-SRQ-007**：撤销域层长期 quantity 强制 1；暂存/签发费用草稿与门诊一致经 **`ProductDefinitionOrderChargePricingsupport** 按请求 **`quantity`/`unit_code`** 比例换算 |
| v1.4 | 2026-05-28 | ReadModel 废止后对齐 zxplat 实现 |
| v1.3 | 2026-05-27 | **BR-IP-ORD-022**：列表/目录/绑定单位展示名与门诊 **BR-ORD-022** 对齐；关联 **AS-OP-DOR-003-SDD v3.9** |
| v1.2 | 2026-05-27 | **BR-IP-ORD-018**：临时 IF-IP-DOR-11/15 与门诊 **BR-ORD-021** 对齐（占用 **`unit_code`→拆零**、**`availableQuantity`** 包装展示、FIFO 整包装整数分段）；§1.7/§2/§4 IF-IP-DOR-15 同步；关联 **AS-OP-DOR-003-SDD v3.8** |
| v1.0 | 2026-05-21 | 初版：对齐 **AS-OP-DOR-003** 与住院差异（TherapyType 长临、无耗材、无库存校验、医生来源过滤、停嘱、出院医嘱+EncounterHistory）；IF-IP-DOR-10～21；**G-IP-DOR-003-01** InpatientOrderLookupLoader待补 |
| v1.1 | 2026-05-24 | **出院列表契约**：**BR-IP-ORD-017**；IF-IP-DOR-10/21 与实现对齐（专用字段回显、出院行不写 `contentSummary`）；IF-IP-DOR-21 弱化目录锚点 **EX-IP-ORD-303** |
