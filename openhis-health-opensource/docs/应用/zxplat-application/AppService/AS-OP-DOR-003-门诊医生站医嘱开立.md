# 门诊医生站 · 医嘱开立 · 应用服务设计（AS）

> **文档职责**：说明门诊医生站「开立医嘱」模块的**接口契约（IF）**、业务语义、涉及聚合与 QueryService、规则（BR）、编排意图；作为上游契约供详细设计与实现对齐。
>
> **本文必须写**：IF 编号；用户动作与读写属性；关键校验与状态变化；Gap；异常口径；事件语义；非功能约束；业务向测试要点。
>
> **本文禁止写**：具体类名/应用方法名；Request/Response 字段明细；包路径；Mapper；伪代码；写库顺序；QueryService 字段组装规则。
>
> **页面边界**：医生站主界面「开立医嘱」标签——工具栏（新增、签发、撤回、删除、并组/拆组、组套）、医嘱主表（状态、类型、医嘱内容、开立时间、签署人、开单科室等）、行内展开编辑卡片（项目检索、诊断绑定、费用性质、用法频次剂量、成组药品多行、保存/保存并签发/关闭）、行双击进入待签发医嘱编辑；**检验/检查/治疗申请单**快捷入口（独立列表 + 整张保存弹窗，与医嘱 Tab **共用**签发/签退，见 **IF-DOR-21/22**）。**中医处方专页、电子处方打印、报告查询 Tab、就诊队列与接诊生命周期**等归入其他 AS（见 §1.4）。
>
> **覆盖声明**：完整契约以 **§2 IF-DOR-10～23** 为准（其中 **IF-DOR-13 已废止**，费用性质下拉并入 **AS-OP-DOR-001 · IF-DOR-01**）；编排规则与 **ZXPlat 目标态**（`DM-MED-*` / `DM-WKF-*` / `DM-ADM-011` / `DM-CUS-006` 等）对齐，并按当前「开立医嘱」Tab 可执行动作组织接口。**HTTP 接入、请求体形态、应用层事务与实现侧依赖**以对应 AppService-SDD（`AS-OP-DOR-003-门诊医生站医嘱开立-SDD.md` **v3.62**）及工程已实现代码对齐说明为准，本文不展开类名与编排序列；**医嘱主表列表行须携带的信息维度与目标态字段名**以 SDD **§3.2**（含 **§3.2.1** 与既有医嘱请求行信息的迁移对照）为唯一权威。
>
> **编码**：AS-OP-DOR-003 · **版本**：v1.77 · **日期**：2026-10-13
---

## 1. 场景概述

### 1.1 目标与边界


| 维度 | 说明 |
| --- | --- |
| **在内** | **门诊皮试入口（§1.8）**：药品行勾选**皮试**（`skinTestFlag`）→ **IF-DOR-14** 后台联动生成「皮试检查」`ServiceRequest` + 操作费 **`ChargeItem` 草稿**（**BR-ORD-025**）；下游收费与护士登记见 **AS-OP-NUR-001 §1.7.2**（**住院皮试不在本文**）；**检验/检查/治疗申请单**（本期仅**临嘱**场景；长期医嘱无申请单入口，**不做**后端强制校验，见 **BR-REQ-007**）：独立申请单列表（**IF-DOR-22**）、整张保存（**IF-DOR-21**，申请单号前缀 **`AR`**、`getSeqByDay`）；申请单行仍为 **`ServiceRequest`** + **`ServiceRequestDetail`**（部位/样本）+ 主表 **`remark`**（纯备注）；**签发/签退**复用 **IF-DOR-15/16**（可与普通医嘱混批）；打印申请单号 = **`prescription_no`**（`lab_apply_form` / `exam_apply_form` / `procedure_apply_form`）；载入当前门诊就诊下已开立的药品/耗材/诊疗医嘱请求统一列表（IF-DOR-10，**含**申请单行并按 `prescription_no` 前缀由前端分组展示）；分页查询患者**全类型医嘱**统一列表（IF-DOR-20，单接口分 Tab：**当前医嘱** / **历史医嘱**，读口径与 IF-DOR-10 同构；**是否按处方形式展示由前端处理**）；**进入开立页或切换就诊时一次性拉取**用药频次术语、给药途径（用法）术语下拉（IF-DOR-18）；**费用性质（合同）全量下拉**由医生站就诊队列 **AS-OP-DOR-001 · IF-DOR-01** 分页列表响应 **`feeNatureOptions`** 一并提供，开立 Tab **复用**该数据（**不再**在本模块单独查询，**IF-DOR-13 废止**）；分页检索 **`ProductDefinition`** 目录并附带：按**接诊科室**解析 **`ServiceLocationTemplate`** 得**候选发药房**后与库存**求交**、**多库房预减加总**、默认发点/降级；**目录展示定价（IF-DOR-11，BR-ORD-011）**：**诊疗**行仅从 **`ChargeItemDefinition`** 中取价型为**项目售价（`SALE`）**的定价行写 **`unitPrice`/`chargeItemDefinitionId`**；**药品/耗材**目录仅从价型 **`LOT_NUMBER_PRICE`** 与定价表 **`lot_no`** 解析展示价——**存在预减后库存明细**时按 **`inventoryList` 各行的批号**逐行对齐批号售价并回填行级 **`price`/`chargeItemDefinitionId`**，目录汇总 **`unitPrice`/`chargeItemDefinitionId`** 与**首条**库存明细批号对应的定价行一致；**无库存**时由装配在 **`LOT_NUMBER_PRICE`** 内择优/兜底；QueryService **`price` 列不作为**目录展示单价权威；另含 **五类单位结构化槽位**、**名称拼音/五笔**、药品侧**默认频次/用法/剂型/限制用药及拆分属性展示名**（以领域事件后查询刷新为准）、以及 **`product_table_name`** 作为药/诊/材类型锚点；按用法或诊疗定义拉取绑定子项规则（IF-DOR-12）；在诊疗开立路径上**按诊疗活动定义**查询**可执行科室/地点规则**及组织、库位展示名（**IF-DOR-19**）；**暂存**一批医嘱变更——含新增、修改、删除行；药/材为 **Request + `ChargeItem` 草稿**（**不**暂存建发放，**BR-ORD-020**）（IF-DOR-14）；**签发**一批医嘱——含处方号、请求生效、**锚发放行的费用**待收费及检验检查跨系统申请等（IF-DOR-15）；**签退（撤回）**已提交但未进入不可撤回收费态的医嘱（IF-DOR-16）；维护药品医嘱**组号**以支持并组/拆组展示与发药（IF-DOR-17） |
| **在外** | 就诊队列与接诊、诊断录入与常用诊断维护、中医处方、电子处方票据、退费与收费窗口、独立报告查询页；**组套/医嘱模板**列表、预览结构、另存见 **AS-BAS-MST-017 · IF-BAS-OTL-07～08、10**（套用期 prepicked 目录 IF） |
| **涉及域** | 药品申请与发放（MedicationRequest / MedicationDispense）；耗材请求与发放（DeviceRequest / DeviceDispense）；诊疗服务申请（ServiceRequest / **ServiceRequestDetail**）；身体部位（BodyStructure）；样本定义（SpecimenDefinition）；诊疗项目定义及套餐子项（ActivityDefinition）；**开立目录QueryService（ProductDefinition）**；收费明细与定价（ChargeItem / ChargeItemDefinition）；就诊与诊断关联（Encounter / Condition / EncounterDiagnosis）；**接诊科室上下文→候选发药/发材点（`ServiceLocationTemplate`，与库存求交、预减加总、默认发点/降级及签发 FIFO 分拆衔接，见 §1.7）**；租户配置（LIS/PACS 地址等） |
| **编排权威** | 以本文 **§2、§4** 为准 |


### 1.2 角色与关键动作


| 角色 | 关键动作 |
| --- | --- |
| **门诊医生** | 进入开立医嘱 → 查看/刷新列表 → 新增或双击待签发记录进入编辑 → 检索并选择药品/诊疗/耗材 → 维护用法、频次、剂量、执行科室、诊断绑定、费用性质等 → **药品行勾选皮试**（`skinTestFlag`，**不**手工另加「皮试检查」诊疗行，**BR-ORD-025**）→ 保存草稿或签发 → 必要时撤回、删除、调整组号；或通过快捷入口打开**检验/检查/治疗申请单** → 选品（**IF-DOR-11** 按 `categoryEnum` 锚定申请单目录分类，分类 chips 按可选 **`typeEnum`** 收窄业务类型）→ 维护整单执行科室、行级部位/样本/备注 → **整张保存**（**IF-DOR-21**）→ 在申请单页或医嘱 Tab **签发**（**IF-DOR-15**） |
| **系统** | 合并 **`ProductDefinition`** 目录与**多库房**物理库存、**待发/草稿发放占用**预减口径，汇总展示「还可开多少」；按 **`ServiceLocationTemplate`** 与降级策略解析默认发药/发材点；目录装配中医**诊疗**仅采纳 **`SALE`**、**药耗**按 **FIFO 批号→`LOT_NUMBER_PRICE`**（与 **`DM-ADM-010`** 多行定价一致）；统一查价见 **BR-ORD-001** / `ClinicalCatalogPriceResolvesupport；**计费金额以后端写路径重算为准**（目录/预览 `unitPrice` 仅展示）；**皮试药**暂存时**自动**联动「皮试检查」诊疗与操作费草稿（**BR-ORD-025**）；**药品签发前**对就诊未收费**西药/中成药**执行**处方分方**并回写 `prescription_no`（**BR-ORD-023**；排除中药饮片）；签退时恢复草稿态与费用草稿态 |


### 1.3 术语与口径约定


| 术语 | 口径 |
| --- | --- |
| **医嘱请求** | 对患者一次具体开立事实的载体：药品、耗材、诊疗分属不同资源形态，在医生站列表中**统一 QueryService 组装**为行展示 |
| **暂存（保存）** | 请求处于**草稿**态；**药品/耗材不在请求上固化「最终发放库房/批号」**（开立科室落 **`MedicationRequest`/`DeviceRequest`**，`perform_location` 可空）；**暂存仅**持久化请求 + **按请求锚点**的 **`ChargeItem` 草稿**（**不**调用 `generateDispenseFromTemporaryOrder` / `generateDeviceDispenseFromTemporaryPath`，**BR-ORD-020**）；诊疗可展开套餐子项并落对应费用草稿 |
| **签发** | 请求进入**生效**态；签发批次内生成统一签署编码；**药品**：批次含药品签发时，**先**对本批待签西药/中成药 ∪ 已签发未收费**非中药**执行处方分方（**BR-ORD-023**，**签发以后才有方**，不给未参与本批的其它 DRAFT 写号；**排除** `tcmFlag=true` 中药饮片），再逐条签发并使用分方给出的 **`prescription_no`**（**禁止**逐药单独取号）；可按规则将**一条**药品/耗材请求**拆为多行发放**（多库房 FIFO 等，域口径见 `DM-MED-002` / `DM-WKF-007`）；**药品/耗材**：签发相位按 FIFO **重写**发放事实并**作废**仍锚在医嘱请求上的草稿费用，再按**各发放行**挂 **`ChargeItem` 草稿**后批量 **`DRAFT`→`PLANNED`**（**BR-ORD-014** / **BR-ORD-016**）；**诊疗**仍可按请求锚点迁移费用；检验/检查类诊疗在允许条件下发送跨系统申请 |
| **签退（撤回）** | 在费用未进入已收费前，将所选请求关联的、仍为 **`PLANNED` 且未结算**的费用明细回退 **`DRAFT`**，并将请求批量恢复为草稿态 |
| **目录检索关键字排序** | 命中关键字时：名称以关键字开头（或拼音首串以字母关键字开头）的项优先展示 |
| **QueryService 能力** | 列表加载以就诊维度医嘱与费用快照的读侧设计为准；本文不写字段映射细节 |
| **申请单** | 本期仅**检查 / 检验 / 治疗**三类、**临嘱**（`therapy_enum=TEMPORARY`）场景；多张明细共享同一 **`prescription_no`**（前缀 **`AR`**，按日取号）；**无**独立头表；同号下 **`category_enum` 唯一**；扩展信息：**部位/样本** → `wkf_service_request_detail`（`target_table`+`target_id`）；**纯备注** → `wkf_service_request.remark` |
| **申请单编辑** | **删旧重建**：软删同号下全部 **DRAFT** `ServiceRequest` 及关联 **Detail**、作废未计费费用后，**重新取新 `AR` 号**落库（**不沿用**旧号，**BR-REQ-002**） |
| **接入与工程口径** | 动态应用服务路由、JSON 请求体、写路径跨聚合顺序与**应用层是否声明事务**等，以 AS-SDD 与实现对齐说明为准 |


### 1.4 本期范围与非目标


| 类别 | 内容 |
| --- | --- |
| **本期（契约）** | IF-DOR-10～23：**检验/检查/治疗申请单**（**IF-DOR-21** 整张保存、**IF-DOR-22** 列表；扩展 **IF-DOR-18** 身体部位/样本下拉、**IF-DOR-11** `categoryEnum` 过滤）；**IF-DOR-23** 暂存前绑定解析；与既有门诊医生站医嘱开立核心能力对齐——列表、**页初始化**、目录检索、绑定规则、**IF-DOR-19**、**IF-DOR-20**、暂存、签发、签退、组号；**IF-DOR-13 已废止** |
| **本期（UI 接通）** | 工具栏与表格、编辑卡片「保存」等已布局；**检验/检查申请**演示弹窗已存在；**签发、撤回、删除、并组/拆组、组套**等部分回调仍为占位或与后端契约待联调——以迭代接通为准 |
| **非目标（申请单）** | 手术/输血/病理等其它申请单类型；**长期医嘱申请单**（产品无入口）；独立 `doc_request_form` 头表；`ActivityDefinition.specimen_code` 默认标本联动 |
| **非目标（同页其他 Tab）** | 「门诊病历」「诊断录入」「中医处方」「电子处方」「报告查询」——不在本文 IF 表收敛 |
| **非目标（有接口、本 Tab 无单一控件）** | **近 30 天检验检查重复开立提示**、拼接 **LIS/PACS 报告查看 URL** 等——本 Tab 未提供独立入口；若产品改在报告查询或其他工作站呈现，由对应 AS 收敛 |
| **非目标（组套）** | 工具栏「组套」所依赖的模板候选加载与预览**不**在本文 IF 表收敛，由 **AS-BAS-MST-017 · IF-BAS-OTL-07～08、10** 提供；选中后仍调本文暂存/签发 IF |
| **非目标（编辑卡片内未接线能力）** | 「保存并签发」若未绑定回调则签发仅以工具栏「签发」为准；产品确认后 UI 与 IF-DOR-15 触发入口应二选一并列或合并说明 |


### 1.5 领域清单（聚合 + 职责 + DM 路径）


| 聚合（域） | 在本场景中的职责（摘要） | DM 路径 |
| --- | --- | --- |
| **MedicationRequest / MedicationDispense** | 门诊药品医嘱生命周期、处方属性、组号、与发放及费用衔接 | `DM-MED-003-MedicationRequest-药品申请.md` / `DM-MED-002-MedicationDispense-药品发放.md` |
| **DeviceRequest / DeviceDispense** | 耗材医嘱与发放、费用衔接；门诊路径与 **MedicationRequest / MedicationDispense** **对称**（草稿不固化发放库房、拆行发放、计费锚发放行） | `DM-WKF-003-DeviceRequest-耗材请求.md` / `DM-WKF-007-DeviceDispense-耗材发放.md` |
| **ServiceRequest** | 诊疗医嘱、执行科室、基于药品的皮试检查关联、套餐子项；**申请单**同聚合，以 **`prescription_no`（`AR` 前缀）** 分组 | `DM-WKF-004-ServiceRequest-服务申请.md` |
| **BodyStructure** | 检查/治疗申请单可选**身体部位**主数据（`dia_body_structure`） | `DM-DIA-001-BodyStructure-身体部位.md` |
| **SpecimenDefinition** | 检验申请单**样本**主数据（`adm_specimen_definition`） | `DM-ADM-014-SpecimenDefinition-样本定义.md` |
| **ActivityDefinition** | 诊疗定义、活动类型（含检验/检查）、套餐子项 JSON 展开 | `DM-WKF-001-ActivityDefinition-诊疗项目定义.md` |
| **MedicationDefinition / DeviceDefinition** | 被检索的目录主数据及与收费项定义关联 | `DM-MED-001-MedicationDefinition-药品定义.md` / `DM-ADM-008-DeviceDefinition-器材定义.md` |
| **ProductDefinition（QueryService）** | 医生站开立页**药/诊/材**统一目录分页与检索投影（与 IF-DOR-11 权威读路径一致） | `DM-MED-001 / DM-WKF-001 / DM-ADM-008 §6.2 目录 QueryService` |
| **ChargeItem / ChargeItemDefinition** | 医嘱费用草稿与待收费迁移、删除与签退门控；**门诊药耗**与每条发放行 **1:1** 锚点及 **`LOT_NUMBER_PRICE`** 计价见 **`DM-ADM-011`** / **`DM-ADM-010`** | `DM-ADM-011-ChargeItem-收费明细.md` / `DM-ADM-010-ChargeItemDefinition-收费项定价.md` |
| **Account（就诊账户）** | IF-DOR-14 费用草稿挂账真源为 **`ChargeItem.account_id`**：按行费用性质合同匹配就诊账户（双账户默认医保户，选自费则挂个人现金户；单自费户仅现金户）。费用性质下拉来自就诊已开立账户（展示合同名）；`contract_id` 冗余落医嘱行 | `DM-ADM-009-Account-就诊账户.md` |
| **Encounter / Condition** | 就诊上下文、诊断与医嘱绑定 | `DM-ADM-002-Encounter-就诊.md` / `DM-CLN-003-Condition-诊断记录.md` |
| **Organization** | 登录科室、执行科室、位置主数据 | `DM-ADM-004-Organization-机构科室.md` |
| **ActivityExecutionLocation（域自定义）** | 诊疗活动定义维度的「可执行科室/地点」规则；**含项目级与大类默认（未配具体项目）两级**（医生站 **IF-DOR-19** 只读列表；展示名经 **Location**/**Organization** 解析） | 域包 `activityexecutionlocation`（规则实体与查询服务；事件见域 §5） |
| **ServiceLocationTemplate** | **接诊/开立科室上下文 → 候选发药/发材库房**规则（门诊与 `org_id`/接诊组织对齐，见 **`DM-CUS-006` v1.2**）；与 IF-DOR-11 可发量门禁、无库存降级及**签发 FIFO 拆库**消费 | `DM-CUS-006-ServiceLocationTemplate-取药科室.md` |
| **Practitioner** | 开方医生、请求作者上下文 | `DM-ADM-005-Practitioner-医务人员.md` |


### 1.6 业务规则（BR-*）


| 规则 ID | 描述 | 绑定 IF |
| --- | --- | --- |
| **BR-ORD-001** | **统一目录查价**（**废止**租户「医嘱定价来源」及 `pricingSourceCode`）：门诊/住院开嘱、护士划价、组套预览共用 `ClinicalCatalogPriceResolver`——**诊疗**仅 **SALE** 择优；**药品/耗材**按取药科室模板库位 **FIFO 首条可用库存批号** 匹配 **LOT_NUMBER_PRICE**（**有批号却无该批定价 → 不得跨批号 priority 冒充**；**仅无库存/无批号**时才允许在 **LOT_NUMBER_PRICE** 内择优） | IF-DOR-11 / IF-DOR-14 |
| **BR-ORD-002** | 目录结果仅含**已发布（有效）**目录行；支持按 **`product_table_name` 表名字面值集合**与划价标记等条件收窄 | IF-DOR-11 |
| **BR-ORD-003** | 非诊疗类行在**暂存**前做**库存可用性**校验：编辑已存在药品/耗材行时，先释放该行原发放占用再参与汇总校验；**签发相位不重复**该校验（该校验仅在暂存入口执行）。**首版实现**（`DoctorStationAdviceAppService#saveDoctorStationAdviceDraftBatch`）**未**调用库存汇总校验器；目录侧预减（IF-DOR-11）与签发 FIFO 不足时拒绝（IF-DOR-15）已落地 | IF-DOR-11 / IF-DOR-14 / IF-DOR-15 |
| **BR-ORD-014** | **签发**成功路径依赖：待签发的药品/耗材/诊疗请求已在**暂存**相位落库（药/材：**Request + 按请求锚点的 `ChargeItem` 草稿**，**不含**暂存相位发放行；诊疗另述）。**签发**相位：药品/耗材须按 **§1.7** **FIFO 拆分并重写**发放分段事实；**作废**仍锚在 **`MED_MEDICATION_REQUEST`/`WKF_DEVICE_REQUEST`** 上的未计费草稿费用；再按**每段发放行**生成 **`ChargeItem` 草稿**：**`service_*`＝医嘱请求**，**`product_*`＝药品/耗材定义**，**`dispense_*`＝该段 `med_medication_dispense`/`wkf_device_dispense` 主键**，并按 **`dispense_*`** 命中批量 **`DRAFT`→`PLANNED`**；诊疗路径仍以请求锚点迁移费用状态为主 | IF-DOR-14 / IF-DOR-15 |
| **BR-ORD-015** | 同一**批次**内对多类资源的处理**顺序**为：**药品 → 诊疗 → 耗材**（产品目标态；诊疗套餐子项展开仍仅在诊疗分支内发生）。**首版实现**按请求体 `lines`/`signTargets` **提交顺序**逐条路由，**不**在应用层二次排序；若需严格药→诊→材，由前端按序提交或后续在 AppService 收口 | IF-DOR-14 / IF-DOR-15 |
| **BR-ORD-004** | 删除任意医嘱行前，校验关联费用项**非已收费**；否则拒绝 | IF-DOR-14 |
| **BR-ORD-005** | 诊疗删除须同步删除其套餐子行及特定条件下基于药品请求衍生的已完成态子请求 | IF-DOR-14 |
| **BR-ORD-006** | **签发**批次内药品使用分方结果之 **`prescription_no`**（见 **BR-ORD-023**），请求态切至生效；**诊疗**：就诊下仍可按**医嘱请求锚点**将费用 **`DRAFT`→`PLANNED`**；**药品/耗材**：费用迁移对象为按 **BR-ORD-016** 锚定在**各发放行**上的 **`ChargeItem`**（与 **`DM-ADM-011`** 门诊预结算仅接受 `PLANNED` 一致） | IF-DOR-15 |
| **BR-ORD-023** | **门诊西药/中成药处方分方（仅门诊，DM-MED-003 §6.1.15）**：IF-DOR-15 批次**含药品**签发时，在 `signMedicationOrder` **之前**调用领域 **`splitPrescription`**；参与行 = 医生开立来源、无 **SETTLED** 费用、且属于 **本批待签药品（DRAFT）∪ 已 ACTIVE 未收费** 的**非中药饮片**（`tcmFlag≠true`）；**禁止**给未纳入本批的其它 DRAFT 写 `prescription_no`（签发以后才有方）；**禁止**将 `tcmFlag=true` 中药饮片纳入参与集——中药处方号由 **IF-DOR-37 / BR-TCM-ISO-003** 按方标识取号，西药签发不得改写已签发中药 `prescription_no`（否则会与代煎 companion 等方级费用快照漂移；**收费行药品号以结算盖章为准，见 AS-OP-FIN-002 · BR-OPC-026**）；每次签发对上述参与集重算（已 ACTIVE 未收费**西药/中成药**行的号可改写，防开一个签一个绕过）；分方维度含 **`med_type`**、**`pharmacology_category_code`（药品性质）**、**西药/中成药目录分类分轨（BR-MRQ-032）**、**FIFO 首段库房**、成组 `group_id`、西药与中成药各自合计 ≤5 种、单张金额上限（系统设置 **`outpatientPrescriptionAmountLimitYuan`**，默认 500，99999 关闭）；同步回写 **`rx_type_code`**（**含精二时整方固定 `8`，见 DM **BR-MRQ-033**）；**住院不调用** | IF-DOR-15 |
| **BR-ORD-024** | **诊疗行执行科室下拉（前端）**：① 选品预填**仅**使用 IF-DOR-11 目录行 **`defaultPerformOrgId`/`defaultPerformOrgName`**，**禁止**回落当前登录科室或以「当前登录机构」等占位文案作为展示名；② 编辑区选定诊疗定义后调用 **IF-DOR-19**，`rules` 非空时去重展示规则内执行科室；③ **`rules` 为空**时回落基础数据 **`executeLocation/init`** 之 **`executionOrgOptions`**（全院组织机构树扁平化）作为可选科室全集，**禁止**将当前登录科室单独塞进下拉；④ 规则仅一条时可自动选中；多条规则且当前值不在规则内时可预选首条（**仅配置命中场景**）；全院科室回落场景**不**强制预选；⑤ 历史已选值不在当前下拉集合内时须补入选项以保证回显 | IF-DOR-11 / IF-DOR-19 / IF-DOR-14 |
| **BR-ORD-007** | 签发**检验或检查**类诊疗时发送跨系统申请；载荷须携带该行 **`ServiceRequestDetail`** 解析之**部位/样本**标识与展示名（**BR-REQ-008**）；租户可配置「对接异常是否忽略仅记录」——忽略时不得阻断已本地持久化的签发事实 | IF-DOR-15 |
| **BR-ORD-008** | **签退**前每一关联费用项均不得为已收费；按服务资源类型分组将请求恢复草稿态；**仍处于 `PLANNED` 且未结算**的关联费用项**回退为 `DRAFT`**（与签发升位对称）；若无关联费用项则返回固定失败语义 | IF-DOR-16 |
| **BR-ORD-009** | **组号**仅作用于药品请求聚合内并组展示与发药协同；允许将组号置空实现拆组 | IF-DOR-17 |
| **BR-ORD-010** | 列表加载仅包含来源为**医生开立**的医嘱事实：药品/耗材/诊疗三类请求均按与暂存/签发一致的 **`GenerateSource.DOCTOR_PRESCRIPTION`（`doctor-prescription`）** 过滤（与 **`DM-MED-003` §4.5** / **`DM-WKF-003` §4.2** / **`DM-WKF-004` §4.6** 对齐）。装配列表行时：在 **`MedicationRequest`/`DeviceRequest`/`ServiceRequest`** 主数据上补充请求状态、皮试/注射等展示；**收费汇总态**（如行级 `chargeStatus`）须将同一请求下、已按 **BR-ORD-016** 锚定在 **`MedicationDispense`/`DeviceDispense`** 上的各 **`ChargeItem`** **归并推导**（多发放行→一行医嘱的汇总展示规则以实现约定码为准，如全草稿/存在待收费/存在已结算等） | IF-DOR-10 |
| **BR-ORD-011** | **目录分页真源**仍为 **`ProductDefinition`**；先用 **`ServiceLocationTemplate`** 按**接诊科室上下文**得**候选发药房集合**并**剔除**非候选库房库存；再对各候选点合并物理库存并扣**候选库房全局**草稿/待发占用（预减，**不限就诊/患者**；状态仅 `DRAFT`/`PREPARATION`）——**药品**另须 `now < limitTime`（草稿约 2h、待发约 24h）；**耗材**预占**不**按 `limitTime` 过期，直至护士执行推进或签退/取消释放（**DM-WKF-007 · BR-DD-QRY-003**）——占用行 **`quantity` 须按各行 `unit_code` 换算为拆零单位后再扣减**（**BR-ORD-021**）；**同库位多批**时预占按库位汇总、**不含批号**，须在 FIFO 批次遍历中**顺序消耗一次**剩余预占（**禁止**对每一批都减整库占用，例：批 10+5、预占 9 → 可开 6 非 1）；**「还可开多少」**= 候选集内预减后**拆零单位**数量的算术加总，再经 **`unitBands`** 换算为**包装单位**（与目录行 **`unitCode`** 一致，均为 **`package_unit_code`**）写入 **`availableQuantity`**（**`inventoryList[].quantity`** 仍为拆零单位）；**禁止**跨槽回落默认单位；**不下发** `def_encounter_unit_code` / `def_inhospital_unit_code`（主数据无维护入口，医生站目录不展示）；可并行展示 `inventoryList`；按模板 `display_order` 与库存做**默认发点**与**无库存降级**。**目录展示定价分流**：**诊疗**（`wkf_activity_definition`）仅从 **`ChargeItemDefinition`** 取 **`SALE`** 写汇总 **`unitPrice`/`chargeItemDefinitionId`**；**药品/耗材**仅从 **`LOT_NUMBER_PRICE`** 定价行解析——**有库存明细**时 **`inventoryList` 每行批号**与定价表 **`lot_no`** 对齐回填 **`price`/`chargeItemDefinitionId`**（批号售价），汇总 **`unitPrice`/`chargeItemDefinitionId`** 与**首条**库存批号对应之定价行一致；**无库存**时在 **`LOT_NUMBER_PRICE`** 内择优/兜底（**BR-ORD-001** 统一查价，**无**租户定价来源门控） | IF-DOR-11 |
| **BR-ORD-016** | **计费锚点与金额权威**：**每条** `ChargeItem` 与 **一条**药品或耗材**发放物理行**（计价粒度）**一一对应**（诊疗暂存锚请求表）；持久化锚点以 **`DM-ADM-011`** **`BR-CHG-001`** 为准。**入账 `quantity_value`/`quantity_unit`/`unit_price`/`total_price`** 须来自医嘱请求 **`quantity`/`unit_code`**（或绑定项载荷）+ **`ProductDefinitionDisplayDto`** + **`ChargeItemDefinition`**，经 **`ProductDefinitionOrderChargePricing`** / **`ProductDefinitionCatalogUnitBandsAssembler`** 换算：**定价表 `price_value` 一律为每包装单位价**（**DM-ADM-010 §1.5/§1.6**），槽价与账单单价**仅以包装单位为锚**做**包装→目标开立单位**线性换算（`P_target = P_pkg × R_target / R_pkg`）；**禁止**以拆零/其它槽单位为锚再往回乘倍数；**禁止**默认 `1`/`次`、整价直乘、复制签发模板金额或**从目录主单位回落**补全请求缺失的 `unit_code`；前端展示金额**不得**作为权威落库。**IF-DOR-15/37 签发分段挂费**：每一发放分段的 `unit_price` **必须**按该段 FIFO **`lotNumber`** 调用 **`ClinicalCatalogPriceResolver#selectExecutionPriceLineForChargeDraft`** 匹配 **`LOT_NUMBER_PRICE`**；批号未命中定价 → 业务异常；**禁止** `selectFirstPriceLineForChargeDraft` / 跨批号 **priority** 择优落账（避免发药单批号对、价格错） | IF-DOR-14 / IF-DOR-15 |
| **BR-ORD-017** | **药/材对称（门诊医生站）**：药品与耗材在 IF-DOR-11 目录读路径、IF-DOR-14/15 写路径上适用**同一套**责任边界——含 **`product_definition_id` + `product_table_name`** 锚点、**不于请求上固化最终发放库房**（开立科室在请求，库房在发放行）、签发可拆多行发放及 **BR-ORD-016** 计费锚点 | IF-DOR-11 / IF-DOR-14 / IF-DOR-15 |
| **BR-ORD-018** | **医生站主表展示排序号**：用户在主表维护的**行序意向**（拖序或等价交互），由前端在**暂存批次**按行随单提交；后端将整型排序号持久化到对应**药品/诊疗/耗材请求**事实，并在 **IF-DOR-10** 统一列表**回显**；合并列表排序以**已持久化排序号升序**优先、**未携带或未写入**的行排在后段，再以**开立时间**（`reqOpenedTime`，空回落 `reqAuthoredTime`）**倒序**作为次要键。**签发**不单独改写该字段；若需调整行序须在草稿态再次**暂存**并提交新排序号 | IF-DOR-10 / IF-DOR-14 |
| **BR-ORD-020** | **医生站暂存不建发放（与 `DM-MED-003` BR-MRQ-028、`DM-MED-002` BR-MDP-015 一致）**：IF-DOR-14 **不**调用 `generateDispenseFromTemporaryOrder` / `generateDeviceDispenseFromTemporaryPath`；药/材发放仅在 IF-DOR-15 **签发 FIFO** 建立；暂存费用 **`service_*` 锚请求表**，**`product_*` 锚三大目录定义**（`med_medication_definition` / `wkf_activity_definition` / `adm_device_definition` 与对应定义主键）；签发后费用另锚 **`dispense_*` 发放行**（**BR-ORD-016**） | IF-DOR-14 / IF-DOR-15 |
| **BR-ORD-021** | **库存拆零与医嘱开立单位对齐（IF-DOR-11 预减 / IF-DOR-15 FIFO，与药房发药扣减同口径）**：库房库存账面为**拆零（最小）单位**；**待发占用预减**须将发放行 **`quantity` 按各行 `unit_code` 换算为拆零**后再与物理库存比对、加总；**同库位多批**时库位级预占须在批次 FIFO 遍历中**顺序消耗**（先扣前批、剩余留给后批），**禁止**每批都减整库预占总量；目录 **`availableQuantity`** 为拆零净量经 **`unitBands`** 换算为**包装单位**展示（与 **`unitCode`** 同源，**`inventoryList[].quantity`** 仍为拆零）。**签发 FIFO**：内部在拆零单位上与批次可用量及占用比对；占用 = IF-DOR-11 目录占用（`DRAFT`/`PREPARATION`）**叠加**住院 `SUMMARIZED`（`listMedicationDispensesForInpatientSummarizedOccupancy`，**不**改目录「还可开」）；**发放分段 `quantity`/`unit_code` 须与医嘱请求一致，不得改单位**——开立单位为**拆零**（相对拆零倍数=1）时允许跨批次部分切分（如开立 10 片→6 片+4 片）；开立单位为**盒/采购/剂量等非拆零**（倍数>1）时**每段须为整数个开立单位**，单批次净可用不足以凑满 1 个开立单位则**跳过该批次**继续 FIFO（如开立 3 盒→A 批 2 盒 + C 批 1 盒；**禁止** 0.6 盒 + 0.4 盒）；仍无法覆盖则拒绝签发 | IF-DOR-11 / IF-DOR-15 |
| **BR-ORD-022** | **单位 code 与展示名成对下发（B.10.8.1）**：凡对外 Response 暴露 **`unitCode`**（及药品 **`doseUnitCode`**）处，须**同时**返回 **`unitName`** / **`doseUnitName`**（人读文案）。展示名解析：**术语码表预热**（`UNIT_CODE` / `DOSE_UNIT`）优先；未命中时仅回落**与同一 `unitCode` 对应槽位**的产品QueryService字典文本（**禁止**跨槽 `firstNonBlank` 导致 code/name 错位）。适用 **IF-DOR-10** 统一列表、**IF-DOR-11** 目录 **`inventoryList[]`**、**IF-DOR-12** 绑定行；**禁止**仅下发 code 或把 code 当作展示名 | IF-DOR-10 / IF-DOR-11 / IF-DOR-12 |
| **BR-ORD-026** | **中西医 Tab 分域（IF-DOR-10）**：开立医嘱 Tab 统一列表**排除** `tcm_flag=true` 的中药饮片药品行（由 **AS-OP-DOR-010 · IF-DOR-34** 专载）；**IF-DOR-20** 医嘱查询**不排除**饮片行 | IF-DOR-10 |
| **BR-ORD-027** | **西药目录排除中草药（IF-DOR-11）**：请求可选 **`excludeMedicationCategoryCodes`**（`med_category_code` 列表，如开立西药传 `["4"]` 排除中草药）；**仅**作用于 `med_medication_definition` 检索，与 `categoryEnum` 独立 | IF-DOR-11 |
| **BR-ORD-032** | **门诊禁开嘱托**（承接 **AS-IP-DOR-003 · BR-IP-ORD-028②**）：① **IF-DOR-11** 目录分页恒排除诊疗 `category_code=52`，并双锚排除目录名精确「嘱托」；② **IF-DOR-14** CREATE/UPDATE 诊疗行命中嘱托 → **EX-ORD-308**；③ 住院开立嘱托不在本文 | IF-DOR-11 / IF-DOR-14 |
| **BR-ORD-029** | **绑定解析（IF-DOR-23）**：产品口径为**签发前**对本次待签草稿主行调用 IF-DOR-23（暂存 IF-DOR-14 仅落主医嘱）；服务端对 **CREATE/UPDATE** 药品行（`methodCode`→`USAGE`）与诊疗行（`activityDefinitionId`→`ACTIVITY`）解析**直接**模板绑定（含合管优先），**禁止**用法→诊疗→耗材级联；数量取模板默认 `quantity`（**不乘**主医嘱数量）；**服务端不去重**；响应须标注触发源与 `based_on` 锚点；前端展示确认后再落派生。**组套套用除外**见 **BR-ORD-038** | IF-DOR-23 / IF-DOR-15 |
| **BR-ORD-038** | **组套套用禁止二次带出**：凡经**通用组套**或**申请单组套**套用开出的医嘱/申请单行，签发时**不得**再调用用法绑定、诊疗绑定、合管配置等自动带出耗材或诊疗（含 IF-DOR-23）；组套明细本身已含所需项目。实现：前端会话标记套用 `requestId` 并跳过签发触发行；IF-DOR-23 支持 `skipAutoBind=true` 时经 Gate 后返回空 `bindItems`。与 **AS-BAS-MST-017 · BR-OTL-APY-005** 对齐 | IF-DOR-23 / IF-DOR-15 / IF-DOR-21 |
| **BR-ORD-031** | **绑定派生行关联落库（IF-DOR-14）**：用法/诊疗模板「带出」的诊疗/耗材须与主医嘱可追溯关联——① 派生行 **`based_on_table`/`based_on_id` 必填**（成组西药：`based_on_id` 可任取组内一条 `med_medication_request.id`；非成组：锚触发行 requestId）；② 成组西药主嘱 **`package_id` 与 `group_id` 同源**；绑定**耗材**另写 **`package_id` 与主药组套一致**（对齐住院执行绑定 **BR-IP-NUR-004-GRP-003**）；诊疗派生仅 `based_on`（无 `package_id` 列）；③ 同组同用法同目标前端去重后**整组只落一条**派生行 | IF-DOR-14 / IF-DOR-23 |
| **BR-ORD-033** | **药耗 FIFO 签发批号与挂费一致**：IF-DOR-15/37 分段挂费前，发放行落库 `lot_number` 须与 FIFO 分段批号**完全一致**且非空、非护士占位；库存可用行无批号则拒绝签发；**禁止**仅按内存分段批号写 `ChargeItem` 而发放行批号空/漂移（防收银已收、护士执行报「发放批号缺失」）。实现：`DoctorStationAdviceFifoAllocationAssembler` + `OutpatientMedicationSignChargeOrchestrator#assertPersistedDispenseLotsMatchFifoSegments`；与 **BR-OPC-023** / **BR-NUR-006** / **PR-DD-LOT-002** 衔接 | IF-DOR-15 / IF-DOR-37 |
| **BR-ORD-034** | **门诊皮试药签发 FIFO 单批优先（仅门诊，住院不适用）**：① 适用范围：`skinTestFlag=true` 的药品行，**IF-DOR-15 签发**相位（暂存不建发放，**BR-ORD-020**）。② 分配：在模板库位序→效期 FIFO 上，找**第一个单批净可用 ≥ 开立量**的批号，**整量落该批**（可跳过更近但不够量的批；例：1 批不够、2 批够 → 两支均开在 2 批，**不提示**）。③ 无一单批够量、但多批合计够量 → **EX-ORD-309** 提示「【药名】单独批号数量不够本次就诊使用，跨批号可能需要多次皮试，是否确认开立？」；前端确认后带 **`confirmCrossLotSkinTest=true`** 重试，**本批允许跨批且不再提示**。④ 多批合计仍不足 → 既有库存不足拒绝。⑤ **住院**仍按批号决定是否追加皮试（**BR-IP-ORD-024**），不消费本规则 | IF-DOR-15 |
| **BR-ORD-035** | **药耗签发跨批次金额确认（IF-DOR-15）**：① FIFO 将一条药/耗材医嘱拆为**两个及以上不同批号**取用（**同批号多库位多段不弹窗**）时，写发放/挂费前须提示医生各批数量与金额及合计（与落账 `LOT_NUMBER_PRICE` 同口径）。② 未确认 → **EX-ORD-310**（整批事务回滚，等同未点签发）；文案形如「【品名】需跨批次取用：第1批 …元；第2批 …元；合计 …元。是否确认签发？」。③ 前端确认后带 **`confirmCrossLotPricing=true`** 重试，本批不再提示。④ **单批号**覆盖（含仅 1 个去重批号）不提示。⑤ 中医 Tab（IF-DOR-37）本期不消费本规则（编排重载默认已确认） | IF-DOR-15 |
| **BR-ORD-037** | **pending 调价禁止签发（对齐 BR-PRC-LOCK-007）**：IF-DOR-15 签发前对本批药/耗定义调用 `PendingPriceAdjustmentSignGate`；任一条存在 `pending` 调价则整批拒绝（`PRICE_ADJUSTMENT_004`） | IF-DOR-15 |
| **BR-ORD-036** | **诊疗开立数量口径（前端）**：编辑卡 / 申请单 / **组套套用确认弹窗** UI 录入 **单次执行数量** 与 **执行次数**（组套行 `defaultQuantity`×`defaultDays`；确认弹窗改数量仅改单次量，次数取预组装 `executionCount`）；提交 IF-DOR-14 / IF-DOR-21 时 **`quantity` = 单次执行数量 × 执行次数**（账单计价乘数，与 **BR-ORD-016** 一致），**`executionCount`** 仍为护士站可执行次数（**BR-NUR-010**）。回显时由落库总量 ÷ 执行次数还原单次量（须可整除还原）。**禁止**把执行次数当作账单数量；**禁止**组套确认后把单次量直接写入 `servicePayload.quantity`；**统计/报表仍认落库 `quantity` 总量，口径不变** | IF-DOR-10 / IF-DOR-14 / IF-DOR-21 |
| **BR-ORD-028** | **init 下拉稳定排序（IF-DOR-18）**：术语/频次/部位/样本/费用性质等 `OptionDto` 列表按 **`sort_order`/`display_order` 升序**输出（sort 为空排末位，再以 value 稳定次序） | IF-DOR-18 |
| **BR-ORD-030** | **退费读侧无感（IF-DOR-10 / IF-DOR-20）**：统一医嘱列表**仅排除**退费流程生成的逆向复制行（`refund_medicine_id` / `refund_device_id` / `refund_service_id` **非空**）；**保留**医生开立的原医嘱行（含状态列待退费/已退费/已完成）；**不得**按收费行 `refund_id` 或半退保留链隐藏原请求；与护士站处置单、**AS-OP-DOR-010** 中药 Tab 同口径 | IF-DOR-10 / IF-DOR-20 |
| **BR-ORD-019** | **医嘱主表列表信息完备性**：**IF-DOR-10** 返回的每一行须覆盖医生站主表与行内编辑卡片**回填**所需的**业务信息维度**——含资源类型锚点、**医嘱展示名与规格**、数量与单位、用法/频次/单次剂量与剂量单位、皮试与**皮试结果**、注射相关标志、执行或发药（材）**位置**（请求侧意向点）、**药品/耗材行**：签发后 **FIFO** 发放事实上的**首选发放库位 id 与名称**（同医嘱多条发放时取**发放主键升序第一条**所绑定的库位；未签发或无发放事实时为 **空**）、**药品行**：绑定诊断的**展示名**与 **`encounterDiagnosisIds`（有序 ids 串，主诊 id 第一；可多诊）**（与 **IF-DOR-14** 暂存语义一致；诊疗/耗材行本期不要求在本模块请求载荷或列表中绑定诊断）、**费用侧**可展示的金额与收费态（多 `ChargeItem` 时按 **BR-ORD-016** 归并规则汇总）、组号与处方号及处方类别、嘱托与备注、**申请单行**：`prescriptionNo`（`AR` 前缀）、**`serviceRequestDetails`**（部位/样本摘要，见 **BR-REQ-006**）、行级 **`remark`**（纯备注）；**前端医嘱内容 extension 区**须展示 `serviceRequestDetails` 与 `remark`（有值才展示，格式：`样本/部位：…；备注：…`，与 **BR-NUR-016** 口径一致）、诊疗**子项 JSON**、用药天数与拆零比、滴速与用药说明编码、代煎与中药付数（若域内存在）、停嘱时刻、**是否当前登录医师开立**（与 `onlyMine` 门控语义一致）、以及用于编辑器还原的 **`contentJson`（或等价结构化字段）** 等；**具体字段名、类型与既有主表展示项的对应关系**以 **AS-SDD §3.2** 迁移表为准；**入账金额权威**仍遵守 **BR-ORD-016**（列表金额为只读展示） | IF-DOR-10 |
| **BR-REQ-001** | **申请单号**：整张**首次保存**时 `getSeqByDay` 取号，前缀 **`AR`**（与药品处方 `CP`、诊疗业务号分序列）；**编辑保存**在删旧重建后**必须取新号**（**BR-REQ-002**） | IF-DOR-21 |
| **BR-REQ-002** | **申请单编辑**：传入**待替换**之旧 `prescriptionNo`（`AR` 前缀）时，须存在且仅含 **DRAFT** 行方可编辑；**软删**旧号下全部 `ServiceRequest` + **`ServiceRequestDetail`** + 作废未计费 **`ChargeItem`**，再按新载荷**重新取号**创建 | IF-DOR-21 |
| **BR-REQ-003** | 同一 `prescription_no` 下全部 `ServiceRequest` **`category_enum` 一致**，且与请求 **`categoryEnum` 参数**一致（检查 `TEST`/23、检验 `PROOF`/22、治疗 `TREATMENT`/21，存枚举 **code**） | IF-DOR-21 / IF-DOR-22 |
| **BR-REQ-004** | **整单一个执行科室**：`org_id` 写入该单下全部 `ServiceRequest`；默认值与 **IF-DOR-19** 同口径，前台可改 | IF-DOR-21 |
| **BR-REQ-005** | **Detail 目标白名单**：`target_table` 仅允许 `dia_body_structure`、`adm_specimen_definition`；**检查部位、检验样本、治疗部位均选填**（有 Detail 时须白名单表 + 有效 `targetId`）；**禁止**纯备注 Detail 行（纯备注写 **`ServiceRequest.remark`**，**BR-SRQ-DTL-001**） | IF-DOR-21 |
| **BR-REQ-006** | **Detail 随主档生命周期**：创建草稿时随 `CreateServiceRequestDraftCommand.detailItems` 写入；删除草稿时域 **`removeServiceRequestDraft`** 连带软删 Detail（**BR-SRQ-004**） | IF-DOR-21 / IF-DOR-14 |
| **BR-REQ-007** | **长临口径（文档）**：申请单仅用于**临嘱**开立场景；**长期医嘱无申请单**；后端**不**单独校验 `therapy_enum`（由前端入口约束） | IF-DOR-21 |
| **BR-REQ-008** | **整单诊断绑定**：`encounterDiagnosisIds`（有序 ids 串，主诊 id 第一）与 **`orgId` 同级**写入该单全部 `ServiceRequest`；与 **IF-DOR-14** 诊疗草稿诊断字段同源；**可空** | IF-DOR-21 |
| **BR-REQ-008** | **LIS/PACS 外发**：签发检验/检查类申请单行时，外呼载荷须含各 Detail 之 `target_table`/`target_id` 及解析后展示名 | IF-DOR-15 |
| **BR-TCM-ISO-001** | **中西医签发隔离**：本 Tab **IF-DOR-15** 签发批次**不得**包含 `tcm_flag=true` 的药品行（须转 **AS-OP-DOR-010 · IF-DOR-37**）；与 **BR-TCM-ISO-002** 对称 | IF-DOR-15 |
| **BR-ORD-025** | **门诊皮试联动（仅门诊，住院不适用）**：① **入口**：药品编辑卡片勾选**皮试**（`skinTestFlag=true`）；前端批次**仅**传药品行标志，**禁止**手工另加「皮试检查」诊疗 `servicePayload` 行。② **IF-DOR-14 暂存**：皮试药 Request 落库后**同一事务**联动创建/维持 companion **`ServiceRequest`**（活动=固定名 **「皮试检查」**；`generate_source_enum=doctor-prescription`；`therapy_enum=TEMPORARY`；`based_on_table=med_medication_request`，`based_on_id=皮试药 id`；诊断/执行科室/费用性质等**继承皮试药同行**；**幂等**同一皮试药至多一条）+ 锚 SR 的 **`ChargeItem` 草稿**（定价 **SALE**，**BR-ORD-016**）。③ **改 false 或 DELETE 皮试药**：同步软删联动 SR（草稿）并作废其未计费费用（**BR-ORD-004**）。④ **IF-DOR-15/16**：签发/签退皮试药时**对称**处理联动皮试检查 SR 及其费用（签发选中皮试药时可**自动扩**联动 SR，不要求医生单独勾选皮试行）；签退 companion **仅 ACTIVE**，草稿/已签退跳过；同批勾选药+companion 时去重，避免二次签退。⑤ **展示**：列表 companion 行医嘱名追加关联药文案，**可含规格**（示例「皮试检查（关联：氯化钠注射液 0.9%*250ml）」）。⑥ **下游**：收费后 SR **`COMPLETED`** → 护士皮试 Tab（**AS-OP-NUR-001 · IF-OP-STK-02/03**）仅登记 `AllergyIntolerance`；医生站列表回显 `skinTestOutcome*` | IF-DOR-14 / IF-DOR-15 / IF-DOR-16 |


### 1.7 门诊医生站·药品（耗材对称）链路需求真值

下列为**产品需求真值**，与 **IF-DOR-11 / IF-DOR-14 / IF-DOR-15** 及 **`DM-CUS-006`**、**`DM-MED-003`**、**`DM-MED-002`** 交叉一致：

| 阶段 | 行为 | 持久化/计算要点 |
| --- | --- | --- |
| **目录查询** | 从 **`ProductDefinition`** 分页检索药品（耗材同构） | 用 **`ServiceLocationTemplate`** 按**接诊科室上下文**解析**可从哪些药房发**；仅对这些库房取库存并做**预减**；**加总**（或列表加总列）得到**「还可开多少」**（**BR-ORD-011**） |
| **开立 UI** | 医生**不**选药房、**不**选批号 | 无请求落库 |
| **暂存** | 保存 | **每药/材行一条** `MedicationRequest` / `DeviceRequest`（单请求事实）；**`org_id`** 存**开立/接诊科室**；**不在请求上存发放库房**（`perform_location` 可空）。**暂存不建** `MedicationDispense`/`DeviceDispense`（**BR-ORD-020**）；**签发**按 FIFO 生成一条或多条发放行（见 **`DM-MED-002`** 数值例及 **`DM-WKF-007`** 对称说明） |
| **签发** | 批量签署 | 读取 **req 内药/材数量与 `unit_code`** + **开立科室** + **模板与院内配置** + **批次维 FIFO**（候选库位内按模板序→效期遍历；**内部拆零比对**，分段 **`quantity`/`unit_code` 与请求一致**，整包装开立禁止小数段，见 **BR-ORD-021**），生成**一条或多条** `MedicationDispense` / `DeviceDispense`，**同一 `med_req_id` / `device_req_id`**，**每行写实际 `location_id`/批号/数量**。例①（按库位）：A 库剩 1、B 库剩 2、开立 3 → **两条**发放：A×1 + B×2。例②（按批号·盒）：开立 3 盒（1 盒=10 片），A 批 25 片、B 批 6 片、C 批 10 片 → **A 批 2 盒 + C 批 1 盒**（B 批不足 1 盒跳过）。**皮试药**另见 **BR-ORD-034**：优先整量落单批；无一单批够量须确认后方可跨批。**跨批次金额**另见 **BR-ORD-035**：**跨批号**（≥2 个不同批号）时须确认各批数量与金额后再落发放 |

### 1.8 门诊皮试入口与联动（医生站侧）

> **定位**：皮试**发起点**在医生站药品行「皮试」勾选；**临床登记与皮试操作费**在护士站/收费（**AS-OP-NUR-001 §1.7**）。**住院皮试**由 **AS-IP-DOR-003 · BR-IP-ORD-024** 医生联动 SR（**不**挂费），护士 **AS-IP-NUR-011** 执行后锚 doctor SR 计费。

#### 1.8.1 医生站入口（UI → IF-DOR-14）

| 项 | 口径 |
| --- | --- |
| **交互** | 药品编辑卡片「皮试」开关（与 `skinTestFlag` / 列表 `skinTestFlagText` 回显一致）；**不**提供单独检索「皮试检查」诊疗并手工加行的入口 |
| **请求载荷** | `SaveMedicationAdviceDraftLinePayload.skinTestFlag`（Boolean）；**禁止**前端在 `lines` 中额外拼装 `wkf_activity_definition` 的「皮试检查」行 |
| **持久化** | 皮试标志写入 `med_medication_request.skin_test_flag`；联动 SR/费用由 **BR-ORD-025** 在 **IF-DOR-14** 后台完成 |

#### 1.8.2 端到端业务链（与护士站对齐）

```
医生勾选皮试（skinTestFlag=true）→ IF-DOR-14 暂存
    ├─ 皮试药 MedicationRequest + 药费 ChargeItem 草稿
    └─ 联动「皮试检查」ServiceRequest（doctor-prescription，based_on→皮试药）
           └─ 皮试操作费 ChargeItem 草稿
                  └─ IF-DOR-15 签发 → 费用 DRAFT→PLANNED
                         └─ 门诊收费（AS-OP-FIN-002）→ 皮试检查 SR COMPLETED
                                └─ 护士皮试 Tab（IF-OP-STK-02/03）登记结果
                                       └─ 医生站列表回显 skinTestOutcome*
```

| 阶段 | 本文（医生站） | 下游 |
| --- | --- | --- |
| 暂存 | **BR-ORD-025** 联动 SR + 双份费用草稿 | — |
| 签发/签退 | **IF-DOR-15/16** 含联动皮试检查 SR | — |
| 收费 | — | SR → **COMPLETED**（**AS-OP-NUR-001 §1.7.4** 列表门控） |
| 登记 | — | 仅 `AllergyIntolerance`（**IF-OP-STK-03**） |

#### 1.8.3 与住院差异（边界声明）

| 维度 | 门诊（本文） | 住院（不在本文） |
| --- | --- | --- |
| 皮试 FIFO / 批号 | **BR-ORD-034**：签发优先单批；无一单批够量确认后可跨批 | 按批号决定是否追加皮试检查（**BR-IP-ORD-024** / IF-IP-DOR-27） |
| 皮试检查 SR | 医生站 **IF-DOR-14** 联动 + 操作费草稿 | 医生站 **IF-IP-DOR-14** 联动 SR（**不**挂费）；护士登记后锚 doctor SR 计费（**AS-IP-NUR-011**） |
| 医生站职责 | 勾选标志 + 联动 SR/费用草稿 | 勾选标志 + 联动 SR（**无**费用草稿） |

---

## 2. 页面与接口总览（契约概览）


| IF 编号 | 用户动作 | 接口语义（业务） | 读/写属性 | 关键规则（BR） | 关键状态变化（自然语言） | 涉及领域 | 查询服务 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| IF-DOR-10 | 进入开立医嘱 Tab、切换当前就诊、或主动刷新列表 | 加载当前就诊下医生处方来源的**药品/耗材/诊疗**请求统一列表，并装配**收费汇总展示**（基于 **锚发放行的 `ChargeItem`** 归并到请求行）；**排除** `tcm_flag=true` 中药饮片药品行（**BR-ORD-026**）；**药/材**行另装配 **FIFO 首选发放库位**（发放主键升序首条，**BR-ORD-019**）；**`unitCode`/`unitName`（及剂量单位）成对回显**（**BR-ORD-022**）；目录/术语展示经 **`module.common`**（`OrderRequestCatalogWarmAssembler` / `TerminologyOptionLoader` / `ProductDefinitionCatalogUnitBandsAssembler`，与 IF-NUR-02 / IF-OPP-03 共用，**禁止**按行循环查库）；**回显**各请求已持久化的**展示排序号**并按 **BR-ORD-018** 与开立时间合并排序；列表行须满足 **BR-ORD-019**（主表列与编辑回填维度齐全，**不得**仅以占位摘要代替医嘱名与剂量等核心展示） | 读 | BR-ORD-010 / BR-ORD-016 / BR-ORD-017 / **BR-ORD-018** / **BR-ORD-019** / **BR-ORD-022** / **BR-ORD-026** / DeviceRequest / ServiceRequest + MedicationDispense / DeviceDispense + ChargeItem（只读聚合） | OutpatientEncounterContextQueryModel（费用/医嘱相关快照以DM §6 查询契约为准）；过渡期允许就诊维度直连聚合查询 |
| IF-DOR-11 | 编辑卡片内切换「全部/药品/诊疗/耗材」、输入关键字、翻页检索可开立项目；**申请单弹窗**按 **`categoryEnum`**（检查/检验/治疗）锚定目录分类，分类 chips 可选 **`typeEnum`**（业务类型） | 分页检索 **`ProductDefinition`**（名称命中**不按**开立目录QueryService的机构归属做额外收窄；机构上下文用于后续模板与库存装配）；**申请单路径**须传 **`categoryEnum`** 且 **`productTableNames` 仅含 `wkf_activity_definition`**（**BR-REQ-003**）；可选 **`excludeMedicationCategoryCodes`** 排除药品 `med_category_code`（**BR-ORD-027**）；**恒排除嘱托** `category_code=52` / 名「嘱托」（**BR-ORD-032**）；按**接诊科室**调 **`ServiceLocationTemplate`** 得**候选发药房**并与物理库存**求交**；仅在该集合上做**待发预减**（占用按 **`unit_code`→拆零**，**BR-ORD-021**）、**跨库加总**「还可开多少」（**BR-ORD-011**）；**`inventoryList[]` 含 `unitCode`/`unitName`（拆零）**（**BR-ORD-022**）；再经模板序做**默认发点/降级**；装配 **`ChargeItemDefinition`**：**诊疗**取 **`SALE`**；**药耗**取 **`LOT_NUMBER_PRICE`** 且与 **`inventoryList` 批号**对齐（见 **BR-ORD-011**） | 读 | BR-ORD-001 / BR-ORD-002 / BR-ORD-011 / **BR-ORD-021** / **BR-ORD-022** / **BR-ORD-027** / **BR-ORD-032** / BR-ORD-017 | 无写状态变化 | ProductDefinition（QueryService）/ ChargeItemDefinition / ServiceLocationTemplate | —（目录与库存定价组合读） |
| IF-DOR-12 | 选择具体用法编码或诊疗定义后加载绑定明细 | 查询医嘱绑定信息（用法绑耗材等 / 诊疗绑子项）；绑定行 **`unitCode`/`unitName` 成对**（**BR-ORD-022**） | 读 | **BR-ORD-022** | 无写状态变化 | ActivityDefinition / DeviceDefinition 等 | — |
| IF-DOR-23 | 工具栏「签发」前对本次待签草稿（与 IF-DOR-14 同构 payload）；或显式传入 `skipAutoBind` | **绑定解析**：对触发行解析直接模板/合管绑定，返回触发源与 `based_on` 锚点；**不去重、不级联**（**BR-ORD-029**）；`skipAutoBind=true` 或组套套用触发行跳过（**BR-ORD-038**） | 读 | **BR-ORD-029** / **BR-ORD-038** / **BR-ORD-022** | 无写状态变化 | ServiceDeviceTemplate / SpecimenTubeMerge | — |
| IF-DOR-13 | — | **已废止**（费用性质全量下拉并入 **AS-OP-DOR-001 · IF-DOR-01** 列表响应 `feeNatureOptions`；工程已下线单独 HTTP） | — | — | — | — | — |
| IF-DOR-18 | 进入开立医嘱 Tab、或切换当前就诊后需一次性装配编辑区术语下拉；**申请单弹窗**同接口拉取 **身体部位**、**样本定义** 下拉 | **合并只读**：用药频次术语下拉、给药途径（用法）术语下拉；**身体部位**（`BodyStructure`，**树形** `OptionDto`，仅 **ACTIVE**，节点含 **拼音/五笔**，申请单检查行 `ElTreeSelect` 多选且可按名称/编码/拼音/五笔模糊过滤）；**样本定义**（`SpecimenDefinition`，启用态平面下拉，项含 **拼音/五笔**，申请单检验行可按名称/编码/拼音/五笔模糊过滤）；**不含**费用性质（由前端复用 **IF-DOR-01** 返回之 `feeNatureOptions`） | 读 | — | 无写状态变化 | 术语编码系统；BodyStructure；SpecimenDefinition | BodyStructureQueryService；SpecimenDefinitionQueryService |
| IF-DOR-19 | 诊疗开立编辑区按**诊疗活动定义**加载可选执行科室/地点规则 | **只读**：返回**项目级规则 + 同诊疗大类的默认规则**（配置页可不配具体项目）；批量解析执行科室组织与执行地点之**展示名**（与 **IF-DOR-10** 同一就诊/患者门控）；**前端下拉回落**见 **BR-ORD-024** | 读 | **BR-ORD-024** | 无写状态变化 | 自定义 ActivityExecutionLocation 规则读路径；Organization / Location（展示名） | — |
| IF-DOR-14 | 编辑卡片点击「保存」、或工具栏「删除」确认后提交批次 | **暂存**医嘱变更批次：药品/耗材/诊疗分类处理新增、修改、删除；**药/材**仅 **Request + 按请求锚点 `ChargeItem` 草稿**（**BR-ORD-020**，发放仅在签发建立）；**药品行** `skinTestFlag=true` 时**联动**「皮试检查」`ServiceRequest` + 操作费草稿（**BR-ORD-025**）；**用法/诊疗绑定派生行**落 `based_on`（成组可任取组内药嘱 id）及绑定耗材 `package_id` 与主药组套对齐（**BR-ORD-031**）；按行接收并持久化**展示排序号**（**BR-ORD-018**）；**药品行**另可持久化单次用量、用药天数、皮试标记及 **`encounterDiagnosisIds`（有序 ids 串，主诊 id 第一；可多诊）** | 写 | BR-ORD-001 / BR-ORD-003 / BR-ORD-004 / BR-ORD-005 / BR-ORD-015 / BR-ORD-016 / BR-ORD-017 / **BR-ORD-018** / **BR-ORD-020** / **BR-ORD-025** / **BR-ORD-031** | 新行落草稿请求与草稿费用；改行更新请求及费用草稿；删行移除请求、作废未计费费用；皮试联动 SR 幂等同步；绑定派生可追溯 | 药品/耗材/诊疗请求、ChargeItem | OutpatientEncounterContextQueryModel（费用摘要类字段随草稿事件刷新） |
| IF-DOR-15 | 工具栏「签发」、或编辑卡片「保存并签发」（若接线） | **签发**医嘱批次：**先**门诊西药/中成药**处方分方**（**BR-ORD-023**：本批待签 ∪ 已签未收费非中药；排除饮片）→ 请求生效；**药/材**按 FIFO **重写发放**并重挂**发放锚点**费用后 **`DRAFT`→`PLANNED`**；**皮试药**优先单批落发放（**BR-ORD-034** / **EX-ORD-309**）；**跨批次**须确认各批数量与金额（**BR-ORD-035** / **EX-ORD-310**）；**诊疗**按请求锚点迁移费用；**选中皮试药**时**自动扩**锚定该药的联动皮试检查 SR（**BR-ORD-025**）；检验检查外发；**拒绝** `tcm_flag=true` 中药行 | 写 | BR-ORD-006 / **BR-ORD-023** / BR-ORD-007 / BR-ORD-014 / BR-ORD-015 / BR-ORD-016 / BR-ORD-017 / **BR-ORD-021** / **BR-ORD-034** / **BR-ORD-035** / **BR-TCM-ISO-001** / **BR-ORD-025** | 参与集分方重算（不给未签批发签草稿写号）；请求生效；联动皮试检查 SR 与费用同步升位；可能产生对外申请副作用 | 同 IF-DOR-14 + 工作流对外集成 | OutpatientEncounterContextQueryModel；收费队列（下游）；护士皮试 Tab（**AS-OP-NUR-001**） |
| IF-DOR-16 | 工具栏「撤回」 | **签退**所选医嘱：先将**发放锚点**及请求锚点上 **`PLANNED` 未结**费用回退 **`DRAFT`**；请求回草稿；**作废**发放锚点与请求锚点上的未计费草稿费用并**释放**药/材发放预占；**签退/删除皮试药**时**对称**处理联动皮试检查 SR（**BR-ORD-025**） | 写 | BR-ORD-008 / **BR-ORD-025** | 已收费不可撤；否则请求、发放事实与费用草稿清理语义与实现对齐 **BR-ORD-014** 对称路径 | ChargeItem + Request + Dispense | OutpatientEncounterContextQueryModel |
| IF-DOR-17 | 工具栏「并组/拆组」确认 | 批量更新药品医嘱**组号**（含置空拆组） | 写 | BR-ORD-009 | 同行展示组内关系变化 | MedicationRequest | OutpatientEncounterContextQueryModel（若投影组展示） |
| IF-DOR-20 | 医嘱查询视图切换 **当前医嘱/历史医嘱** Tab、刷新明细 | 单接口分页加载患者**门诊全类型医嘱**统一列表（药品+诊疗+耗材；读口径与 **IF-DOR-10** 同构；**是否按处方形式展示由前端处理**；**当前医嘱** Tab 按 `encounterId`；**历史医嘱** Tab 按 `patientId` 查全量并排除当前 `encounterId`，可选 `orderingDepartmentId` / `requestStatusEnumIn` 过滤；须与 IF-DOR-10 同一就诊/患者门控） | 读 | BR-ORD-010 | 无写状态变化 | MedicationRequest + ServiceRequest + DeviceRequest + ChargeItem（只读聚合） | `DoctorStationAdviceListAssembler` |
| IF-DOR-21 | 申请单弹窗点击「保存」 | **整张保存**检验/检查/治疗申请单：`categoryEnum` 区分类型；**新建**取 **`AR`** 号；**编辑**软删旧号下全部 DRAFT 行 + Detail + 费用后**取新 `AR` 号**重建；每行 `ServiceRequest`（临嘱、`prescription_no`、`org_id` 整单一致）+ **`detailItems`** + **`remark`** + **`ChargeItem` 草稿** | 写 | BR-REQ-001～008 / BR-ORD-016 / BR-REQ-006 | 新号下全部行 DRAFT + 费用草稿 | ServiceRequest；ServiceRequestDetail；ChargeItem | OutpatientEncounterContextQueryModel |
| IF-DOR-22 | 申请单 Tab/面板刷新；按类型切换检查/检验/治疗 | 按 `encounterId + categoryEnum` 查询 **`AR` 前缀**申请单列表（按 `prescription_no` 分组头 + 行摘要 + Detail 摘要） | 读 | BR-REQ-003 | 无写状态变化 | ServiceRequest；ServiceRequestDetail | ServiceRequestQueryService（+ Detail 批量读，见 Gap） |


---

## 3. 缺口跟踪（Gap）


| Gap ID | 缺口类型 | 文件 + 章节 | 缺什么 / 不一致点 | 关闭条件 | 影响 IF | 优先级 | 状态 |
|--------|----------|-------------|-------------------|----------|---------|--------|------|
| G-DOR-003-10 | 代码-文档不一致 | `AS-OP-DOR-003-SDD` §3.2 `DoctorStationAdviceRowDto` | **已关闭主体**：`DoctorStationAdviceRowDto` + `DoctorStationAdviceListAssembler` 已对齐 §3.2 主表列（展示名/规格/用法频次剂量/费用归并/FIFO 库位/`requestExtensions` 等）。**残余**：`prescriptionCategoryCode`/`prescriptionCategoryName`、`signedById` 在 DTO 已声明、装配层尚未赋值（可 `null`） | 上述三字段装配落库或 SDD 标为可选并评审签收 | IF-DOR-10 | P2 | **Closed**（主体） |
| G-DOR-003-11 | QueryService 能力 | `DM-WKF-004` §6.2.24 `ServiceRequestQueryService#mapDetailListModelsByServiceRequestIds` | ~~缺 Detail 批量读 + 展示名解析~~ | **已关闭**：**DM v1.1.14** + 领域读侧 `ServiceRequestDetailListModel` 已落地 | IF-DOR-10 / IF-DOR-22 / IF-DOR-15 | P1 | **Closed** |

（组套/模板能力见 **AS-BAS-MST-017**，不另记 Gap。）

---

## 4. 接口编排说明书（业务步骤）

### IF-DOR-10 加载当前就诊医嘱列表


| 维度 | 说明 |
| --- | --- |
| **触发前提** | 已选定门诊就诊；医生已登录 |
| **主路径步骤** | 校验就诊与患者一致性 → 按就诊标识分别装载 **药品**、**耗材**、**诊疗** 三类请求：其中**药/材**须与 IF-DOR-14/15 **同一** `generate_source`（医生站来源常量）过滤，**诊疗**按服务来源配置过滤（**BR-ORD-010**）→ **药品**侧排除 `tcm_flag=true` 饮片行（**BR-ORD-026**）→ 装载本就诊 **`ChargeItem`** 只读集合；**药/材**侧 **`service` 锚发放表主键**（**BR-ORD-016**），将多条发放级费用**归并**到对应 **`requestId`+`productTableName`**（及稳定 **`listRowKey`**，与 IF-DOR-11 同 SOH 分隔策略）所标识的列表行；**诊疗**侧收费归并与 **`ServiceRequest`** 锚定规则以实现/DM 契约为准 → 推导行级收费汇总态与展示金额口径（**展示用金额以已落库 `ChargeItem` 为准**，不得用前端缓存覆盖）→ **药/材**：按医嘱请求 id 批量解析现有 **`MedicationDispense`/`DeviceDispense`**，取**发放主键升序首条**的 **`location_id`** 并解析库位展示名，写入列表行 **FIFO 首选发放库位**（**BR-ORD-019**）→ 按「仅看本人」等选项过滤作者 → **合并排序**：优先按已持久化的**展示排序号**（**BR-ORD-018**），再按开立时间等次要键 → 为每行装配 **BR-ORD-019** 所列信息维度：从请求实体、关联定义/药品实例、位置、**药品行绑定之诊断定义**（若有）、费用行等来源填充**医嘱名与规格、数量单位、用法频次剂量、皮试与结果、嘱托与备注、子项 JSON、处方类别、代煎与付数、滴速与用药说明、停嘱时间、`contentJson`、当前用户开立标记**等（与 **AS-SDD §3.2 / §3.2.1** 一致；**诊疗/耗材**列表行本期不装配绑定诊断列）；状态类字段输出 **code + 展示文案** |
| **关键分支** | 列表为空时展示空态；**药/材**任一查询服务过渡期未提供仓储过滤时，允许内存过滤 `generate_source`，须在首版实现前收口为与 **BR-ORD-010** 等价的持久化查询 |
| **失败分支** | 查询失败 → EX-ORD-201 |
| **查询侧说明** | 纯读；写路径成功后由领域事件驱动读侧刷新 |

> **开立时间 `reqOpenedTime`（BR-ORD-018 次要排序键同源）**：须取自各请求主表已持久化列——药品/耗材/诊疗均为 `req_opened_time`（列表装配统一映射为 ISO local 字符串，契约见 **`AS-OP-DOR-003-SDD` §3.2** `reqOpenedTime`/`reqAuthoredTime`/`stopTime`）。**禁止**用审计 `create_time` 冒充开立时间；存量在领域层补写前 `req_opened_time` 可空，装配/排序可回落 `req_authored_time`。**门诊主表 UI 仅展示开立时间**（无停嘱右界）；`reqAuthoredTime` 可下发但**默认不展示**（仅追溯）。

---

### IF-DOR-11 分页检索可开立医嘱项目


| 维度 | 说明 |
| --- | --- |
| **触发前提** | 已具备登录科室上下文 |
| **主路径步骤** | 按 **表名列表**（SDD：`productTableNames`；空=药/诊/材三表）分页 **`ProductDefinition`** → 按 **接诊科室上下文** 调 **`ServiceLocationTemplate`** 得**候选发药房集合** → 库存预减与定价装配展示价（**BR-ORD-011** / **BR-ORD-001**）→ 目录行回显（字段级以 SDD 为准） |
| **关键分支** | **药/材对称（BR-ORD-017）**：`med_medication_definition` 与 `adm_device_definition` 行共享上述库存/发点/定价组装路径；目录行与预选均以 **`product_definition_id` + `product_table_name`** 区分药/诊/材；支持**划价标记**、**预选产品键集合**（成对 id+表名）、**`excludeMedicationCategoryCodes`**（**BR-ORD-027**）等收窄；**申请单路径**须传 **`categoryEnum`**；可选 **`typeEnum`** 按诊疗 `type_enum`（术语 `activity_type_code`）收窄分类 chips，**不**替代目录分类，**不**改变 LIS/PACS 外发（外发仍看 `ServiceRequest.category_enum`）；**不再**使用 `MED`/`SRV`/`DEV` 间接类型码收窄目录；**「全部」多表分页**：各表均按请求完整页大小取数再合并截断，**禁止**按表数均分页大小（避免关键字仅命中单表时「全部」结果少于单表 Tab，见 **AS-SDD v3.45** §3.3 规则 A）；关键字非空时对当前页结果做「名称/拼音优先」重排；定时任务等特殊上下文无登录科室时使用占位机构标识跳过科室默认重写 |
| **失败分支** | 检索失败 → EX-ORD-201 |
| **查询侧说明** | 纯读 |


---

### IF-DOR-12 查询医嘱绑定信息


| 维度 | 说明 |
| --- | --- |
| **触发前提** | 用户在用法或诊疗路径上选择了需要展开绑定关系的节点 |
| **主路径步骤** | 按绑定类型（用法绑耗材 / 诊疗绑子项）与主数据标识查询有效绑定行 |
| **关键分支** | 无绑定行时返回空集合由前端静默处理 |
| **失败分支** | 查询失败 → EX-ORD-201 |
| **查询侧说明** | 纯读 |


---

### IF-DOR-23 绑定解析（签发前带出）


| 维度 | 说明 |
| --- | --- |
| **触发前提** | 用户点击「签发」前对本次待签草稿主行；请求体与 IF-DOR-14 **同构**（`encounterId`/`patientId`/`lines`）；可选 `skipAutoBind` |
| **主路径步骤** | 校验就诊与患者一致性 → `skipAutoBind=true` 则直接空列表（**BR-ORD-038**）→ 否则跳过 `DELETE` 行 → 药品行按 `methodCode` 查 `USAGE` 直接绑定 → 诊疗行按合管优先否则 `ACTIVITY` 绑定 → 返回全部绑定行及触发源、`based_on` 锚点 |
| **关键分支** | 无绑定配置时返回空列表；**服务端不去重**；**禁止**级联展开；数量始终为模板默认量；**组套套用触发行不入参**（**BR-ORD-038**） |
| **失败分支** | 门控失败 → EX-ORD-201 |
| **查询侧说明** | 纯读；前端展示确认并去重后，再将派生诊疗/耗材行并入暂存并扩入签发目标 |


---

### IF-DOR-13 查询就诊费用性质（**已废止**）


| 维度 | 说明 |
| --- | --- |
| **状态** | **不再提供**本模块单独接口；费用性质 **全量下拉** 由 **AS-OP-DOR-001 · IF-DOR-01** `IF-DOR-01 就诊列表响应` 响应字段 **`feeNatureOptions`** 返回，开立医嘱 Tab **复用**医生站已拉取的队列页数据即可。 |
| **迁移说明** | 若产品仍需「仅刷新费用性质」而不刷新患者列表，可约定前端本地缓存 **IF-DOR-01** 的 `feeNatureOptions` 或由产品另立需求（不在本文 IF 内）。 |


---

### IF-DOR-18 医嘱开立页初始化（频次 / 用法）


| 维度 | 说明 |
| --- | --- |
| **触发前提** | 已选定门诊就诊与患者上下文（与列表/目录门控一致） |
| **主路径步骤** | 校验就诊与患者一致性 → 加载用药频次术语全量下拉 → 加载给药途径（用法）术语全量下拉 → 加载**身体部位树形**下拉（`getBodyStructureTree` → 仅 **ACTIVE** 的 `OptionDto` 树，含 `children` 与拼音/五笔）→ 加载**样本定义**下拉（`SpecimenDefinitionQueryService`，默认 **ACTIVE**，项含拼音/五笔） |
| **关键分支** | 费用性质选项**不在**本接口返回；须与 **IF-DOR-01** 同屏数据衔接；部位为**层级树**（停用节点剔除、启用子节点可上提）；申请单弹窗对部位/样本下拉做**前端**名称/编码/拼音/五笔模糊过滤（init 已透出检索码）；样本数据量大时允许分页/关键字检索子接口由 SDD 固化（本期 init **全量或上限**以实现为准） |
| **失败分支** | 就诊/患者门控失败 → EX-ORD-201；术语编码系统未配置时通常返回空下拉（以实现为准） |
| **查询侧说明** | 纯读 |


---

### IF-DOR-19 按诊疗定义查询可执行科室规则


| 维度 | 说明 |
| --- | --- |
| **触发前提** | 医生在诊疗医嘱编辑路径已选定 **诊疗活动定义**（`activityDefinitionId`），且当前会话持有有效门诊就诊与患者（与 **IF-DOR-10** 门控一致） |
| **主路径步骤** | 校验就诊与患者一致性 → 取就诊开立科室 `organizationId` → 按 **`activityDefinitionId`** 解析诊疗大类与业务类型 → 按**开立科室 + 诊疗定义 + 大类 + 业务类型**加载**单品 + 匹配类型层 + 大类默认**执行科室规则行 → 批量收集规则中的执行科室组织 id、执行地点 id → 调用位置/组织主数据解析 **展示名**，回填响应 |
| **关键分支** | 无规则时返回空列表；大类默认规则行的 `activityDefinitionId` 为 `null`；名称解析缺失时对应名字段可为空，仍以 id 为准 |
| **关键分支（前端，BR-ORD-024）** | 选品预填**仅**用 IF-DOR-11 **`defaultPerformOrg*`**；编辑区 **`rules` 非空**→下拉为规则内科室去重列表；**`rules` 为空**→下拉为 **`executeLocation/init`** 全院科室（**禁止**「当前登录机构」占位）；规则唯一时可自动选中 |
| **失败分支** | 就诊/患者门控失败 → EX-ORD-201；规则查询失败 → EX-ORD-201 |
| **查询侧说明** | 纯读 |


---

### IF-DOR-14 暂存医嘱变更批次


| 维度 | 说明 |
| --- | --- |
| **触发前提** | 已选定就诊与患者；批次内各行数据库操作类型（增/改/删）已标注 |
| **主路径步骤** | **保存语义**：按批次 `lines` **顺序**逐行 `CREATE`/`UPDATE`/`DELETE` 路由（**BR-ORD-015** 药→诊→材为产品序，实现见上表「首版按提交序」）→ **药**：每行一条 `MedicationRequest`，**`org_id`** 写接诊/开立科室，**不写发放药房**；**耗材**：每行一条 `DeviceRequest`，**`org_id`** 写接诊/开立科室，**发材房落取药科室模板默认配置**到 `location_id`/`perform_location`（医生**不**选房；换房在护士站 **BR-NUR-006**）；**仅**落 **`ChargeItem` 草稿**（**`service_*` 锚请求表**，**`product_*` 锚三大目录定义**，**BR-ORD-020**）；药耗定价经 **`resolveDraftChargePriceLine`**，前端回传 **`medicationPayload.dispenseLocationId` / `devicePayload.dispenseLocationId`**（目录 IF-DOR-11）时写路径 **不**二次 match 模板；**耗材**发材房写库以服务端默认配置为准（载荷 id 仅择价）；定价行缺失时阻断；**不**在暂存生成发放行；**药品行**成组/`based_on`/皮试联动等同既有 **BR-ORD-031/025** → 诊疗套餐逻辑不变；排序号 **BR-ORD-018** → 响应可带回新建药品请求 id 等 |
| **关键分支** | **签发后**同一 `med_req_id` 可对应**多条** `MedicationDispense`（多库房 FIFO，见 **§1.7**）；**暂存**相位**不应**存在发放行，由 IF-DOR-15 **FIFO 新建**。删除药品行时同步清理联动皮试检查 SR（**BR-ORD-025**）及衍生诊疗等；删除诊疗行时同步子项。**禁止**前端在 `lines` 中手工提交「皮试检查」诊疗行 |
| **失败分支** | 库存不足 → EX-ORD-104（提示文案由校验工具返回）；删除遇已收费 → EX-ORD-102；持久化失败 → EX-ORD-207 |
| **查询侧说明** | 草稿费用与请求写入触发EncounterQueryService（OutpatientEncounterContextQueryModel）费用摘要类更新意图（以QueryService §4.2 为准） |


---

### IF-DOR-15 签发医嘱批次


| 维度 | 说明 |
| --- | --- |
| **触发前提** | 批次非空；待签发行原则上已在**暂存**相位落库（见 **BR-ORD-014**） |
| **主路径步骤** | 校验就诊具备**默认自费账户**（与 IF-DOR-14 挂费一致）→ 生成批次签署编码 → 若批次**含药品**签发目标：**先**对本批待签西药/中成药 ∪ 已 ACTIVE 未收费**非中药**执行 **`splitPrescription`**（**BR-ORD-023**，不写其它 DRAFT 方号；排除 `tcmFlag=true`）→ **自动扩 target**：对批次内每条皮试药，若存在锚定该药的 doctor-prescription 皮试检查 SR 且未在 `signTargets` 中，**追加**至待签发集合（**BR-ORD-025**）→ **Gate pending 调价**（**BR-ORD-037** / **BR-PRC-LOCK-007**）→ 按 **BR-ORD-015** 顺序**签发**请求 → **药品**：签发前校验 **`tcm_flag≠true`**（**BR-TCM-ISO-001**）→ **药/材**：使用分方映射之 **`prescription_no`** 调用域签发并生效 → **作废**仍锚在 **`MED_MEDICATION_REQUEST`/`WKF_DEVICE_REQUEST`** 上的未计费草稿费用 → 依据 **req 数量、`unit_code`、开立机构、`ServiceLocationTemplate`、库存与占用（拆零口径，BR-ORD-021）**，在候选库位/批次上执行 **FIFO**，**替换**为 `MedicationDispense`/`DeviceDispense` **一条或多条**草稿发放 → 按**各发放行**生成 **`ChargeItem` 草稿**（**BR-ORD-016**）→ 将本批次新建之发放锚点费用批量 **`DRAFT`→`PLANNED`** → **诊疗**（含联动皮试检查 SR）：请求生效后仍可将锚在 **`WKF_SERVICE_REQUEST`** 上的草稿费用升为 **`PLANNED`** → 检验检查外发 |
| **关键分支** | **不重复**执行暂存相位的库存汇总校验（见 **BR-ORD-003**）；**整包装开立**跨批分段须为整数个开立单位，不足整包装之批次跳过（**BR-ORD-021**）；**中药饮片**不得在本 Tab 签发（**BR-TCM-ISO-001**）；**联动皮试检查 SR** 随皮试药签发升位，**不要求**医生单独勾选皮试行；外发异常且租户配置为忽略时记录日志不抛出；否则失败回滚语义由实现与事务策略在 SDD 固化 |
| **失败分支** | 外发失败（非忽略模式）→ EX-ORD-205；其它持久化失败 → EX-ORD-207 |
| **查询侧说明** | 请求生效与费用待收费迁移触发读侧更新；对外申请状态若有独立投影由工作流QueryService消费 |


---

### IF-DOR-16 签退（撤回）医嘱


| 维度 | 说明 |
| --- | --- |
| **触发前提** | 用户在列表中勾选至少一条待撤回的医嘱行 |
| **主路径步骤** | 按选中请求解析关联费用项 → 任一已收费则拒绝 → **药/材**：先将锚在 **`med_medication_dispense`/`wkf_device_dispense`**（及兼容遗留的**请求锚点**）上 **`PLANNED` 未结**费用批量回退 **`DRAFT`** → 执行域**签退**恢复请求草稿态 → **作废**上述锚点上仍未计费的 **`DRAFT` 费用** → **释放**对应药品/耗材发放预占 → **签退皮试药**时**对称**签退/清理锚定该药的联动皮试检查 SR 及其费用（**BR-ORD-025**）→ **诊疗**：将 **`WKF_SERVICE_REQUEST`** 锚点 **`PLANNED` 未结**费用回退 **`DRAFT`** 后签退请求 |
| **关键分支** | 未能解析到任何关联费用项时返回固定失败语义 |
| **失败分支** | 已收费不可撤 → EX-ORD-103；无关联费用 → EX-ORD-106；更新失败 → EX-ORD-207 |
| **查询侧说明** | 与 IF-DOR-14 草稿回写类事件意图一致 |


---

### IF-DOR-17 更新药品医嘱组号


| 维度 | 说明 |
| --- | --- |
| **触发前提** | 用户完成并组/拆组选择并确认 |
| **主路径步骤** | 将需要拆组的行组号置空 → 对其余行批量写入新组号 |
| **失败分支** | 更新失败 → EX-ORD-207 |
| **查询侧说明** | 组展示关系变化；若QueryService不投影组号则由前端以请求明细为准 |


---

### IF-DOR-21 保存申请单（整张）


| 维度 | 说明 |
| --- | --- |
| **触发前提** | 已选定门诊就诊与患者；申请单弹窗内至少一行明细；`categoryEnum` 为检查/检验/治疗之一 |
| **主路径步骤** | 校验就诊门控与默认自费账户（挂费同 **IF-DOR-14**）→ **编辑分支**：若传入 **`previousPrescriptionNo`**（旧 `AR` 号），校验其下**全部为 DRAFT** → **软删**旧号下全部 `ServiceRequest`（域 **`removeServiceRequestDraft`**，连带 **Detail**）并**作废**未计费 **`ChargeItem`** → **取号**：`getSeqByDay` 生成**新** **`AR`+序号**（**新建与编辑均取新号**，**BR-REQ-001/002**）→ 校验 **`categoryEnum`** 与各行诊疗 **`category_enum` 一致**（**BR-REQ-003**）→ 写入整单 **`orgId`**（**BR-REQ-004**）→ 写入整单 **`encounterDiagnosisIds`**（与 **`orgId` 同级**，透传至该单全部 `ServiceRequest`，**BR-REQ-008**）→ 逐行：**`createServiceRequestDraft`**（`therapy_enum=TEMPORARY`、`prescription_no`、 `generate_source=DOCTOR_PRESCRIPTION`、`detailItems`、`remark`）→ 逐行建 **`ChargeItem` 草稿**（**BR-ORD-016**）→ 响应返回**新** `prescriptionNo` 及各 `requestId` |
| **关键分支** | 执行科室默认值：首行诊疗定义经 **IF-DOR-19** 规则解析，前台可覆盖整单 `orgId`；**不考虑** `ActivityDefinition.specimen_code` 预填；整单诊断由前台从就诊已保存诊断中选择，**可空**（后端不强制校验必填） |
| **失败分支** | 旧号不存在或非全 DRAFT → EX-REQ-101；类别不一致或 Detail 校验失败 → EX-REQ-102；定价缺失 → EX-ORD-101；持久化失败 → EX-ORD-207 |
| **查询侧说明** | 草稿创建事件驱动 **IF-DOR-10/22** 刷新 |


---

### IF-DOR-22 查询申请单列表


| 维度 | 说明 |
| --- | --- |
| **触发前提** | 已选定就诊；`categoryEnum` 指定检查/检验/治疗 |
| **主路径步骤** | 校验就诊与患者一致性 → 按 `encounterId` + **`prescription_no` 前缀 `AR`** + **`category_enum`** 查询 **DRAFT/ACTIVE** 等状态的 `ServiceRequest` → 按 **`prescription_no` 分组**装配列表头（申请单号、开立时间、执行科室、行数、状态摘要）→ **`ServiceRequestQueryService#mapDetailListModelsByServiceRequestIds`** 批量加载各行 Detail 并解析部位/样本展示名 |
| **关键分支** | 空列表返回空态；已签发单仍展示于列表直至产品定义隐藏规则（默认展示） |
| **失败分支** | 查询失败 → EX-ORD-201 |
| **查询侧说明** | 纯读；与 **IF-DOR-10** 数据同源，前端可按 `prescriptionNo` 在统一医嘱表折叠展示 |


---

## 5. 业务异常与用户提示


| 语义代码 | 场景 | 用户提示方向 | 可重试 |
| --- | --- | --- | --- |
| EX-ORD-102 | 删除行时存在已收费费用 | 已收费项目无法删除，请刷新后核对 | 否 |
| EX-ORD-103 | 签退时存在已收费费用 | 已收费项目无法签退，请刷新后核对 | 否 |
| EX-ORD-104 | 暂存库存校验失败 | 展示后端返回的库存提示文案 | 视情况 |
| EX-ORD-105 | 签发外发失败（严格模式） | 提示外发失败原因并建议联系接口管理员 | 可协商 |
| EX-ORD-106 | 签退未找到关联费用项 | 提示无关联账单数据 | 否 |
| EX-ORD-201 | 只读类查询失败 | 稍后重试或缩小条件 | 是 |
| EX-ORD-205 | 签发外发失败（阻断模式） | 同 EX-ORD-105 | 视配置 |
| EX-ORD-207 | 写路径持久化失败 | 稍后重试或联系管理员 | 视原因 |
| EX-REQ-101 | 申请单编辑时旧号不存在或含非 DRAFT 行 | 该申请单不可编辑，请刷新后核对 | 否 |
| EX-REQ-102 | 申请单类别/Detail/执行科室校验失败 | 展示后端返回的具体原因 | 视情况 |


---

## 6. 事件语义与消费方

> 事件英文名与载荷以各 DM §5 为准；收费侧以 `DM-ADM-011` 为准；药品/耗材/诊疗请求以 `DM-MED-003`、`DM-WKF-003`、`DM-WKF-004` 为准。


| 事件语义（以 DM 命名为准） | 触发相位（IF） | 典型消费方 |
| --- | --- | --- |
| 药品/耗材/诊疗请求已保存或状态迁移类事件 | IF-DOR-14 / IF-DOR-15 / **IF-DOR-21** | EncounterQueryService（OutpatientEncounterContextQueryModel）、护士执行列表、药房待发、**护士皮试 Tab**（联动皮试检查 SR，**AS-OP-NUR-001 §1.7**） |
| 收费明细草稿创建或状态迁移类事件（**锚发放行**见 **BR-ORD-016**） | IF-DOR-14 / IF-DOR-15 / IF-DOR-16 | EncounterQueryService（OutpatientEncounterContextQueryModel）、收费窗口、对账 |
| 检验检查外发申请已受理类事件（若独立建模） | IF-DOR-15 | LIS/PACS 对接日志、报告QueryService；载荷含 **Detail 部位/样本**（**BR-REQ-008**） |


---

## 7. 非功能性需求


| 类别 | 要求 |
| --- | --- |
| **性能（经验目标）** | IF-DOR-10 ≤1200ms；IF-DOR-11 ≤1500ms（含库存与定价组装）；IF-DOR-14 ≤3000ms；IF-DOR-15 ≤4000ms（含外发）；IF-DOR-16 ≤1500ms；IF-DOR-17 ≤800ms；**IF-DOR-21** ≤3000ms；**IF-DOR-22** ≤1200ms |
| **幂等** | 同一批次短时重复提交应依赖请求主键与数据库约束呈现可理解结果；组号批量更新应可安全重放 |
| **并发** | 双终端同时编辑同一就诊医嘱时，以后写者为准或显式冲突提示，由实现选型并在 SDD 固化 |
| **一致性** | 库存校验与发放占用、费用状态迁移使用同一事务边界或补偿策略，避免出现「请求已删费用仍在」 |
| **审计** | 签发编码、处方号、签退动作、跨系统外发结果应可追溯 |


---

## 8. 测试要点（业务视角）


| 类别 | 要点 |
| --- | --- |
| **正向** | IF-DOR-10 空列表；IF-DOR-10 **药/材**同 `generate_source` 过滤 + 多 **`ChargeItem`** 归并展示；**IF-DOR-14** 皮试药 `skinTestFlag=true` 联动生成「皮试检查」SR + 操作费草稿（**BR-ORD-025**），改 false/删药同步清理；**IF-DOR-15** 签发皮试药时自动扩联动 SR 并升位费用；收费后 SR **`COMPLETED`** → 护士皮试 Tab 可见（**AS-OP-NUR-001**）；**IF-DOR-21** 新建检查/检验/治疗申请单取 **`AR` 号**；**IF-DOR-21** 编辑旧单后**新号**且旧 Detail 已软删；**IF-DOR-22** 按 `categoryEnum` 分组列表；**IF-DOR-15** 申请单行可与普通医嘱混批签发；**IF-DOR-15** 外发携带部位/样本；IF-DOR-10 **展示排序号**暂存后回显与列表序（**BR-ORD-018**）；IF-DOR-10 列表行含医嘱名/规格/剂量用法/皮试结果等与 **BR-ORD-019** 一致的可编辑回填信息；IF-DOR-11 **药/材**目录同路径、多库房预减与默认发点降级、**`availableQuantity` 包装单位展示与占用拆零预减（BR-ORD-021）**、**同库多批预占顺序消耗（例 10+5−9=6）**；IF-DOR-14 新增药品/耗材/诊疗及套餐子项首次保存；IF-DOR-15 签发后 **ChargeItem** 待收费与**分方后**处方号（**BR-ORD-023**）、**LOT_NUMBER_PRICE** 下金额以后端为准；IF-DOR-15 **同就诊多药分方**（麻精/饮片/超5种/超金额/不同 med_type）；IF-DOR-15 **盒开立跨批 FIFO 整盒分段（禁止 0.6 盒）/ 片开立可 6+4（BR-ORD-021）**；IF-DOR-17 并组后再拆组 |
| **异常** | EX-ORD-102/103/104/106；EX-ORD-101 定价缺失；**EX-REQ-101/102** 申请单编辑/校验；皮试检查活动定义未配置 → 暂存/联动失败 |
| **规则** | BR-ORD-003 仅暂存校验库存；BR-ORD-014 签发依赖先暂存；BR-ORD-015 药→诊→耗材顺序；**BR-TCM-ISO-001** 本 Tab 拒绝 `tcm_flag=true` 药品签发；BR-ORD-005 诊疗删除子项；BR-ORD-007 外发忽略配置；**BR-ORD-016/017** 计费锚点与药材对称；**BR-ORD-018** 排序号仅暂存写入、签发不单独改写；**BR-ORD-025** 皮试联动幂等、禁止手工加皮试检查行 |
| **并发** | 双开窗口对同一待签发记录先后保存与签发 |


---

## 9. 关联文档与变更历史

### 9.1 关联文档


| 文档 |
| --- |
| `health/AppService-SDD/AS-OP-DOR-003-门诊医生站医嘱开立-SDD.md`（**v3.49**，2026-08-18） |
| `health/AppService/AS-OP-NUR-001-门诊护士站.md`（**§1.7** 门诊皮试端到端链；**IF-OP-STK-02/03**） |
| `health/AppService-SDD/AS-OP-NUR-001-STK-皮试-SDD.md` |
| `health/AppService/AS-OP-DOR-001-门诊医生站就诊管理.md` |
| `health/AppService/AS-OP-DOR-002-门诊医生站诊断录入.md` |
| `health/AppService/AS-OP-FIN-002-门诊收费门诊收费.md`（皮试操作费结算 → SR `COMPLETED`） |
| `docs/领域/DomainModel/DM-MED-001-MedicationDefinition-药品定义.md |
| `docs/领域/DomainModel/DM-MED-003-MedicationRequest-药品申请.md |
| `docs/领域/DomainModel/DM-MED-002-MedicationDispense-药品发放.md |
| `docs/领域/DomainModel/DM-WKF-001-ActivityDefinition-诊疗项目定义.md |
| `docs/领域/DomainModel/DM-WKF-003-DeviceRequest-耗材请求.md |
| `docs/领域/DomainModel/DM-WKF-004-ServiceRequest-服务申请.md |
| `docs/领域/DomainModel/DM-WKF-007-DeviceDispense-耗材发放.md |
| `docs/领域/DomainModel/DM-ADM-011-ChargeItem-收费明细.md |
| `docs/领域/DomainModel/DM-ADM-009-Account-就诊账户.md 费用草稿默认挂账户） |
| `docs/领域/DomainModel/DM-ADM-010-ChargeItemDefinition-收费项定价.md |
| `docs/领域/DomainModel/DM-ADM-002-Encounter-就诊.md |
| `docs/领域/DomainModel/DM-DIA-001-BodyStructure-身体部位.md |
| `docs/领域/DomainModel/DM-ADM-014-SpecimenDefinition-样本定义.md |
| `docs/领域/DomainModel/DM-CUS-006-ServiceLocationTemplate-取药科室.md |
| `docs/领域/DomainModel/DM-CLN-001-AllergyIntolerance-过敏与不耐受.md |
| `DM §6.2（DM-ADM-002 §6.2 EncounterQueryService（OutpatientEncounterContextQueryModel）` |


### 9.2 变更历史


| 版本 | 日期 | 变更摘要 |
| --- | --- | --- |
| v1.78 | 2026-10-14 | **BR-ORD-021**：IF-DOR-15 FIFO 选批额外并入住院 `SUMMARIZED` 占用（目录 IF-DOR-11 仍仅 DRAFT/PREPARATION）；对齐 **DM-MED-002 v1.3.17** / **AS-SDD v3.63** |
| v1.77 | 2026-10-13 | **BR-ORD-038**：通用组套/申请单组套套用产物签发禁止再调绑定/合管带出；IF-DOR-23 增 `skipAutoBind`；对齐 **AS-SDD v3.62** / **AS-BAS-MST-017 v1.7** |
| v1.76 | 2026-10-12 | **BR-ORD-036**：组套套用确认弹窗诊疗行亦须 `quantity=单次×次数`（禁止确认后把单次量直接写入 IF-DOR-14）；对齐 **AS-SDD v3.61** |
| v1.75 | 2026-10-11 | **BR-REQ-005**：检查部位 / 检验样本 / 治疗部位均改为**选填**（代码先行回写；前后端不再强制每行至少 1 条 Detail）；对齐 **AS-SDD v3.60** |
| v1.74 | 2026-10-09 | **IF-DOR-14 耗材**：开立落取药科室模板默认发材房（医生不选房）；签发按默认房 FIFO；换房在护士站 **BR-NUR-006**；对齐 **DM-WKF-003 v1.2.14** |
| v1.73 | 2026-10-07 | **IF-DOR-19**：执行科室解析叠加可选业务类型层（单品 → 类型 → 大类）；对齐 **DM-CUS-001 v1.5.0** / **AS-SDD v3.59** |
| v1.72 | 2026-10-06 | **IF-DOR-11**：申请单分类 chips 按可选 **`typeEnum`**（术语 `activity_type_code`）查询；`categoryEnum` 仍锚定检查/检验/治疗目录分类；对齐 **DM-WKF-001 v1.0.17** / **AS-SDD v3.58** |
| v1.71 | 2026-09-16 | **BR-ORD-037**：IF-DOR-15 签发前 pending 调价互斥（`PendingPriceAdjustmentSignGate`）；对齐 **AS-FIN-PRC-001 v0.10** |
| v1.70 | 2026-09-06 | **BR-ORD-023**：分方回写仅药品请求；收费行处方号改结算快照（交叉 **AS-OP-FIN-002 · BR-OPC-026** / **DM-ADM-011 v1.6.19**） |
| v1.69 | 2026-08-28 | **BR-ORD-036**：诊疗开立 UI 单次执行数量 × 执行次数 → 落库 `quantity`（总量）+ `executionCount`；统计口径不变；对齐 **AS-SDD v3.56** |
| v1.68 | 2026-08-26 | **BR-ORD-035**：药耗 FIFO 跨批次签发须确认各批数量与金额（**EX-ORD-310** / `confirmCrossLotPricing`）；对齐 **AS-SDD v3.55** |
| v1.67 | 2026-08-24 | **BR-ORD-011**：药品待发预占窗 **12h→24h**（草稿仍 2h）；耗材不过期口径不变 |
| v1.66 | 2026-08-24 | **BR-ORD-011**：耗材待发预占**不过期**（执行或取消才释放）；药品仍 `now < limitTime`（约 12h）；对齐 **DM-WKF-007 · BR-DD-QRY-003** / **AS-SDD** 域查询表 |
| v1.65 | 2026-08-22 | **BR-ORD-034**：门诊皮试药 IF-DOR-15 签发 FIFO 单批优先；无一单批够量 → **EX-ORD-309**，确认后 `confirmCrossLotSkinTest=true` 允许跨批；住院不适用；对齐 **AS-SDD v3.52** |
| v1.64 | 2026-08-22 | **BR-ORD-033**：药耗 FIFO 签发批号须落库且与挂费分段一致；禁止「收费有批、发放无批」；对齐 **AS-OP-FIN-002 · BR-OPC-023** / **DM-WKF-007 PR-DD-LOT-002** |
| v1.63 | 2026-08-20 | **BR-ORD-023**：含精二药品性质的分方单元回写 `rx_type_code=8`（**DM BR-MRQ-033**）；对齐 **AS-SDD v3.50** / **DM-MED-003 v1.3.31** |
| v1.62 | 2026-08-18 | **IF-DOR-11 同库多批预减**：**BR-ORD-011/021** 明确库位级预占在 FIFO 批次遍历中顺序消耗一次（**禁止**每批重复扣整库占用）；数值例 10+5−9=6；对齐 **AS-SDD v3.49** / `DoctorStationAdviceInventoryUnitQuantityHelper#consumeLocationOccupancyForLot` |
| v1.61 | 2026-08-12 | **BR-ORD-023**：西药分方参与集**排除** `tcmFlag=true` 中药饮片；IF-DOR-15 不得改写 IF-DOR-37 已落中药 `prescription_no`。对应 **AS-SDD v3.48** |
| v1.60 | 2026-08-02 | **IF-DOR-15**：药线 FIFO 重建费用按行已落库 `contractId` 解析 `account_id`（避免整批默认医保覆盖选自费）；诊疗签发仍升位草稿费保留原挂账。对齐 **AS-SDD v3.47** |
| v1.59 | 2026-08-02 | **费用性质/挂账**：下拉=就诊已开立账户（展示合同名）；双账户默认医保户、可选自费；单自费无下拉；`ChargeItem.account_id` 为挂账真源。对齐 **DM-ADM-009 v1.3**、**AS-OP-FIN-001 v2.33** |
| v1.58 | 2026-08-02 | **IF-DOR-18**：申请单检查部位 / 检验样本下拉支持名称·编码·拼音·五笔模糊过滤（`OptionDto.pyStr`/`wbStr`）；对齐 **AS-SDD v3.46** / **DM-DIA-001 v1.0.3** |
| v1.57 | 2026-08-02 | **IF-DOR-11**：「全部」多表分页各表用完整页大小、禁止按表数均分；对齐 **AS-SDD v3.45** |
| v1.56 | 2026-07-31 | **BR-ORD-001 / BR-ORD-016**：签发 FIFO 分段挂费必须按分段 `lotNumber` 匹配 `LOT_NUMBER_PRICE`（`selectExecutionPriceLineForChargeDraft`）；禁止跨批号 priority 落账；对齐 **AS-SDD v3.43** |
| v1.55 | 2026-07-26 | **BR-ORD-025**：签退 companion 仅 ACTIVE、同批药+companion 去重；列表关联文案**可含规格**；对齐 **AS-SDD v3.42** |
| v1.54 | 2026-07-18 | **DD §11.7**：**BR-REQ-008** / IF-DOR-21 整单诊断改为 **`encounterDiagnosisIds`** 有序 ids 串（主诊第一）；对齐 **AS-SDD v3.40** |
| v1.53 | 2026-07-18 | **BR-ORD-032**：门诊禁开/不可见嘱托——IF-DOR-11 `excludeCategoryCodes=52` + 名「嘱托」；IF-DOR-14 **EX-ORD-308**；对齐 **AS-IP-DOR-003 BR-IP-ORD-028②** / **AS-SDD** |
| v1.52 | 2026-07-17 | **BR-ORD-031**：IF-DOR-14 绑定派生行落 `based_on`；成组药嘱 `package_id`=`group_id`；绑定耗材 `package_id` 与主药组套对齐（对齐住院 **BR-IP-NUR-004-GRP-003**）；对齐 **AS-SDD v3.38** |
| v1.51 | 2026-07-09 | **BR-ORD-030** 修正：仅按 `refund_*_id` 非空排除退费逆向复制行；撤销半退保留链误隐藏医生原开立行 |
| v1.50 | 2026-07-09 | **BR-ORD-030**：IF-DOR-10/20 读侧排除退费逆向复制行（`refund_*_id` 非空）；一收一退仅展示原医嘱；对齐护士站处置单过滤口径 |
| v1.49 | 2026-06-25 | **BR-ORD-019** 前端：医嘱列表 extension 区展示申请单 `serviceRequestDetails`（样本/部位）与 `remark`（口径同 **BR-NUR-016**） |
| v1.48 | 2026-06-24 | **IF-DOR-18 身体部位树形下拉**：`bodyStructureOptions` 改 `getBodyStructureTree` + 启用态 `OptionDto` 树；申请单检查行前端 `ElTreeSelect`；对齐 **DM-DIA-001 v1.0.2** / **AS-SDD v3.35** |
| v1.47 | 2026-06-23 | **IF-DOR-23 暂存前绑定解析**：新增 **BR-ORD-029**；纯读、不去重、不级联；用语统一为「暂存变更行」（避免与药品批号「批次」混淆）；对齐 **AS-SDD v3.34** / **DM-CUS-005 v1.0.4** |
| v1.46 | 2026-06-20 | **orchestration 落点对齐**：皮试/申请单/挂费上收 `module.orchestration.advice`；详见 **AS-OP-DOR-003-SDD v3.32** |（已迁至 application.shared.advice）
| v1.45 | 2026-06-20 | **support 落点对齐**：`OutpatientMedicationSignChargeOrchestrator、`ServiceApplyFormOrchestrationsupport、`DoctorStationSkinTestSyncOrchestrationsupport → `module/orchestration/advice` |
| v1.44 | 2026-06-18 | **门诊皮试入口（§1.8）**：新增 **BR-ORD-025**——药品行 `skinTestFlag` 联动「皮试检查」`ServiceRequest`（doctor-prescription，`based_on→皮试药`）+ 操作费 `ChargeItem` 草稿；**IF-DOR-14/15/16** 同步；下游对齐 **AS-OP-NUR-001 §1.7.2**；**住院不适用**；对齐 **AS-SDD v3.30** |
| v1.43 | 2026-06-17 | **BR-ORD-024** 诊疗行执行科室下拉：优先 **IF-DOR-19** 配置，无配置回落 **`executeLocation/init`** 全院科室；**BR-ORD-026** 中西医分域（IF-DOR-10 排除饮片）；**BR-ORD-027** 西药目录 `excludeMedicationCategoryCodes`；**BR-ORD-028** init 下拉排序；对齐 **AS-SDD v3.29** / **AS-OP-DOR-010 v1.6** |
| v1.42 | 2026-06-17 | **IF-DOR-11 目录单位口径**：目录行 **`unitCode`/`unitName`/`availableQuantity`** 均以 **`package_unit_code`** 为唯一主单位（**禁止**跨槽回落）；**不下发** `defEncounterUnit*` / `defInhospitalUnit*`；**BR-ORD-016/022** 同步（挂费禁止目录单位回落、展示名须与 code 同槽）；对齐 **AS-SDD v3.28** / **AS-OP-DOR-010 v1.5** |
| v1.41 | 2026-06-17 | **IF-DOR-15**：**BR-TCM-ISO-001** 拦截 `tcm_flag=true` 中药行；药/材签发 FIFO+分段挂费抽到 **`OutpatientMedicationSignChargeOrchestrator**（与 **IF-DOR-37** 共用）；**IF-DOR-14/15** 暂存/签发补 `@Transactional`；对齐 **AS-SDD v3.27** / **AS-OP-DOR-010 v1.4** |
| v1.40 | 2026-06-16 | **IF-DOR-16 耗材签退**：释放预占优先按发放主键集合定位、域层幂等（**DM-WKF-007 v1.2.3**）；**IF-DOR-21 整单诊断绑定**：**BR-REQ-008**、`conditionDefinitionId`/`encounterDiagnosisId` 整单透传；对齐 **AS-SDD v3.26**。 |
| v1.38 | 2026-06-16 | **成码对齐**：引用 **AS-SDD v3.25**（`ServiceApplyFormAssembler` + `ServiceApplyFormOrchestrationsupport） |
| v1.37 | 2026-06-16 | **检验/检查/治疗申请单**：新增 **IF-DOR-21/22**；**BR-REQ-001～008**；**G-DOR-003-11 已关闭**（**DM-WKF-004 v1.1.14** `mapDetailListModelsByServiceRequestIds`）；对齐 **AS-SDD v3.24** |
| v1.36 | 2026-06-11 | **IF-DOR-11/14 库位透传**：目录 `defaultDispenseLocationId` + `prepickedProductKeys`；暂存药耗经 `resolveDraftChargePriceLine`，前端回传 `dispenseLocationId` 时写路径不二次 match 模板；组套 IF-08 仅结构 |
| v1.36.1 | 2026-06-12 | **IF-DOR-20 读口径收敛为全类型医嘱**：`pageDoctorStationAdvice`；行 DTO 与 **IF-DOR-10** 同构；仅 `adviceScopeTabCode` 区分当前/历史；对齐 **AS-SDD v3.23** |
| v1.35 | 2026-06-12 | **IF-DOR-20 响应收敛**：药品医嘱查询响应**不含候选库存**（`inventoryDetailList` 归发药 IF-OPP-03）；对齐 **AS-SDD v3.22** |
| v1.34 | 2026-06-12 | **IF-DOR-20 语义收敛为医嘱查询**：`pageDoctorStationMedicationAdvice`；Tab **`adviceScopeTabCode`**（`CURRENT_ADVICE`/`HISTORY`）；历史医嘱纳入本期；是否按处方展示由前端处理；对齐 **AS-SDD v3.21** |
| v1.30 | 2026-06-11 | **门诊处方分方（BR-ORD-023）**：IF-DOR-15 签发前先 `splitPrescription`；**BR-ORD-006** 使用分方 `prescription_no`；系统设置 **`outpatientPrescriptionAmountLimitYuan`**；对齐 **DM-MED-003 v1.3.8** / **AS-SDD v3.14** |
| v1.29 | 2026-05-29 | **定价包装锚（DM-ADM-010 §1.6）**：**BR-ORD-016** 明确 `price_value` 为每包装单位价，槽价/账单**仅以包装单位为锚**做包装→开立单位换算；**禁止**拆零/默认开立单位锚再往回乘 |
| v1.28 | 2026-05-28 | 六维对账：`BR-ORD-003`/`BR-ORD-015` 标注首版实现差异；**G-DOR-003-10** 关闭主体（列表 DTO/装配已对齐 §3.2）；§9.1 SDD **v3.13** |
| v1.27 | 2026-05-28 | **BR-ORD-016 诊疗/药/材费用草稿**：暂存挂费须传 **`quantity`/`unit_code` + ProductDefinition QueryService 读侧**，经 **`ProductDefinitionOrderChargePricingsupport** 比例换算；**禁止**默认 1/「次」或整价直乘 |
| v1.27.1 | 2026-06-17 | **IF-DOR-19 / DM-CUS-001 v1.2.0**：执行科室规则支持**仅配诊疗大类**；IF-DOR-19 合并返回项目级 + 大类默认规则（`activityDefinitionId` 可空行）；关联 **DM-CUS-001 M3**。 |
| v1.26 | 2026-05-28 | ReadModel 废止后对齐 zxplat 实现（`EncounterContextFacade` / `module.querysupport Loader） |（已迁至 application.shared / feature support（见 DISSOLUTION.md））（已迁至 EncounterContextReader / application.shared.encounter）
| v1.25 | 2026-05-27 | **单位展示名成对（BR-ORD-022）**：IF-DOR-10/11/12 须 **`unitName`/`doseUnitName`**（术语预热 + 产品QueryService字典回落）；关联 **AS-SDD v3.9** |
| v1.24 | 2026-05-27 | **库存拆零与医嘱单位对齐（BR-ORD-021）**：IF-DOR-11 占用预减须按发放行 **`unit_code`→拆零**；**`availableQuantity`** 为拆零净量换算**包装/主计价单位**（**`inventoryList` 仍为拆零**）；IF-DOR-15 FIFO **内部拆零比对**、分段 **`quantity`/`unit_code` 与请求一致**——拆零开立可跨批部分切分（10 片→6+4），整包装开立须**整数个开立单位/批**（3 盒→2+1，**禁止** 0.6+0.4 盒）；§1.7 签发数值例、§2/§4 IF-DOR-11/15、§8 测试矩阵同步；关联 **AS-SDD v3.8**、药房扣减 **`MedicationProductQuantityToSplitUnitConverter`** |
| v1.23 | 2026-05-24 | 对齐 **`ActivityDefinitionQueryService` v3.12** / **AS-SDD v3.7**：诊疗新增后 **`ActivityDefinitionQueryService`** 须暴露 **`package_unit_code*`、`def_encounter_unit_code*`、`status_enum`**（应用 **`ProductDefinitionCatalogPageLoader`** 组装 + **`DM-WKF-001` flush**）；IF-DOR-11 **`unitCode`/`unitBands`** 依赖上述列（叠加 **v1.10** 槽语义与 **AS-SDD v3.6**） |
| v1.21 | 2026-05-18 | **IF-DOR-10**：列表目录/术语装配收口 **`module.common`** 共用组件（与 IF-NUR-02 / IF-OPP-03 一致）；对齐 **AS-SDD v3.4** |
| v1.20 | 2026-05-10 | **IF-DOR-11 / BR-ORD-011 目录定价分流**：诊疗目录仅 **`SALE`**；药耗目录仅 **`LOT_NUMBER_PRICE`**，库存明细按批号回填行级售价，汇总 **`unitPrice`** 对齐首条库存批号；无库存时在 **`LOT_NUMBER_PRICE`** 内择优；QueryService **`price`** 非展示权威；§4 IF-DOR-11 主路径同步；关联 **AS-SDD v3.1**、**`ProductDefinition` QueryService v3.9**、**DM-ADM-010 v1.1 §1.4**。 |
| v1.19 | 2026-05-10 | 新增 **IF-DOR-19**（按诊疗活动定义只读查询可执行科室/地点规则及展示名；与基础维护「诊疗可执行科室」同源）；§1.1/§1.5/§2/§4 同步；关联 **AS-SDD v3.0**。 |
| v1.18 | 2026-05-09 | **IF-DOR-15 / 药耗签发 FIFO 与发放锚点费用**：签发相位药品/耗材在域 `sign*` 之后**作废请求锚点草稿费**→应用层 FIFO 分段（模板库位序 + 库存可用行）→域 **`replaceFifo*DraftDispensesAfterDoctorStationSign`** 重写发放→按发放段批量建 **`ChargeItem` 草稿**→**`planDraftChargeItemsAfterAdviceSignedForAnchors`** 批量 **`DRAFT`→`PLANNED`**；**IF-DOR-16** 签退与发放/请求双锚点回退、释放预占与实现对齐；**IF-DOR-10 / BR-ORD-019** 列表行 **`fifoDispenseLocationId` / `fifoDispenseLocationName`**（发放主键升序首条库位）；签发须解析**自费账户**（与 IF-DOR-14 挂账一致）。关联 **AS-SDD v2.9**、**DM-MED-002 v1.3**、**DM-WKF-007 v1.2**、**DM-ADM-011-SDD**（§5.1.4c/§5.1.4d 批量锚点迁移）。 |
| v1.17 | 2026-05-08 | **IF-DOR-15 / IF-DOR-16 与收费明细状态**：明确签发成功后费用明细 **`DRAFT`→`PLANNED`**（与 **`DM-ADM-011`** 门诊预结算仅接受 `PLANNED` 对齐）；签退将 **`PLANNED` 未结**行回退 **`DRAFT`**；**BR-ORD-006**、**BR-ORD-008**、§2 总表、§4 IF-DOR-15/16 主路径同步；关联 **AS-SDD v2.8**、**DM-ADM-011 v1.2**。 |
| v1.15 | 2026-05-04 | **类型锚点 / IF-DOR-10**：列表与批次契约**对外**统一 **`productTableName`**（三张定义表名），**不下发** `resourceType`；§4 IF-DOR-10 主路径收费归并叙述与 **`listRowKey`**（SOH 分隔）对齐 **AS-SDD v2.6** §3.2 / §3.2.1。 |
| v1.14 | 2026-05-04 | **IF-DOR-10 / 医嘱列表**：新增 **BR-ORD-019**（主表列表信息完备性与编辑回填维度）；§2 IF-DOR-10、§4 IF-DOR-10 主路径、§8 正向用例同步；§3 增加 **G-DOR-003-10**（列表 DTO 与实现未对齐 SDD 目标态）；关联 **AS-SDD v2.5**（§3.2 字段表 + §3.2.1 既有医嘱请求行信息迁移对照）。 |
| v1.13 | 2026-05-04 | **IF-DOR-11**：明确开立目录名称命中**不因** `ProductDefinition` QueryService机构归属做额外收窄（与 **`ProductDefinition` QueryService v3.8**、**AS-SDD v2.4** 一致）；§2 总表 IF-DOR-11 列、§9.1 SDD 版本引用同步。 |
| v1.12 | 2026-05-04 | **§1.5 领域清单** 增补 **Account** 与 **`DM-ADM-009`**；§9.1 关联 SDD **v2.3**；与挂号默认个人现金账户、医嘱费用草稿挂账语义对齐（详设 §2.2/§2.3 **`AccountTypeCode`**）。 |
| v1.11 | 2026-05-04 | **IF-DOR-13 废止**：费用性质下拉迁入 **AS-OP-DOR-001 · IF-DOR-01**（`feeNatureOptions`）；**IF-DOR-18** 仅保留频次/用法术语下拉；§1.1/§2/§4 同步；关联 **AS-SDD v2.2**。 |
| v1.10 | 2026-05-04 | **IF-DOR-11**：目录行以 **`product_table_name`** 为药/诊/材类型锚点（不再使用 `adviceType`）；定价回显为**单价**（`ChargeItemDefinition` 批量查询，QueryService不落库金额）；五类单位以 **`unitBands`** 结构化；保留**名称拼音/五笔**；药品默认**频次/用法/剂型/限制用药**及拆分属性展示以领域事件后查询刷新为准；**批号**仅在 **`inventoryList`** 明细；**§1.1**、**§2 IF-DOR-11** 主路径与 §9.1 **AS-SDD v2.1**、`ProductDefinition` QueryService **v3.7** 对齐 |
| v1.9 | 2026-05-04 | 新增 **IF-DOR-18**（开立页初始化：用药频次、给药途径/用法、与 IF-DOR-13 等价的默认费用性质合并只读）；**IF-DOR-11** 主路径补充目录行**多单位与换算比**回显口径；**§1.1/§1.4/§2/§4** 同步；关联 **AS-SDD v2.0** |
| v1.8 | 2026-05-04 | 新增 **BR-ORD-018**（医生站主表**展示排序号**：IF-DOR-14 随批次持久化、IF-DOR-10 回显与合并排序）；**§2** IF-DOR-10/14 契约列与 **§4** IF-DOR-10/14 主路径同步；**§8** 测试矩阵补充；关联 **AS-SDD v1.9** |
| v1.7 | 2026-05-03 | **§1.1**「在内」「涉及域」与 **§2 IF-DOR-11** 契约列：固化**接诊科室→模板候选→与库存求交→预减加总**口径；**BR-ORD-011** 正文与 **§1.7** 真值表一致（此前因字符误配未落盘的一行已更正）；**§1.7** 暂存/签发行显式**药/材对称**与「还可开多少」措辞统一；文首版本与 **AS-SDD v1.7** 对齐 |
| v1.6 | 2026-05-03 | 新增 **§1.7 需求真值**（`ProductDefinition` 目录、`ServiceLocationTemplate`+接诊科室、预减加总、不选药房/批号、暂存单 req、签发 FIFO 多发放同 reqid 数值例）；重写 **BR-ORD-011**、**IF-DOR-11/14/15** 主路径；对齐 **AS-SDD v1.7** / **`DM-CUS-006` v1.2** / **`DM-MED-002` v1.2** / **`ProductDefinition` v3.4** |
| v1.5 | 2026-05-03 | **IF-DOR-10**：查询医嘱列表与 **BR-ORD-010** 补强——药/材 **`generate_source`** 对称过滤、**`ChargeItem`** 锚发放行后的**行级收费归并展示**；§2 总表与 §7 正向用例同步；对齐 **AS-SDD v1.6** |
| v1.4 | 2026-05-03 | 对齐 **AS-SDD v1.5** 与域稿：补充 **`ServiceLocationTemplate`/`ProductDefinition`** 职责；**BR-ORD-016**（计费锚发放行 + **`LOT_NUMBER_PRICE`** + 前端金额非权威）、**BR-ORD-017**（门诊药/材对称）；扩展 IF-DOR-11/14/15、§1.1/§1.3/§7 与 §9.1 关联文档 |
| v1.3 | 2026-05-03 | IF-DOR-11：目录收窄改为 **`product_table_name` 字面值集合**（对齐 SDD **v1.3**，废弃 `MED`/`SRV`/`DEV` 请求语义）；**BR-ORD-002**、IF-DOR-11 主路径/关键分支与 §9.1 SDD 版本引用同步 |
| v1.2 | 2026-05-03 | §9.1 关联 SDD：`AS-OP-DOR-003-门诊医生站医嘱开立-SDD.md` **v1.2**；IF-DOR-11 目录锚点 **`product_definition_id` + `product_table_name`** 与 AS 表 §4/IF-DOR-11 主路径、关键分支对齐 |
| v1.1 | 2026-05-03 | 对照既有实现补强：**BR-ORD-003** 明确库存校验仅暂存、**BR-ORD-014/015** 签发与暂存分支差异及药→诊→耗材顺序；**IF-DOR-11** 补充划价/类型/预选定义集合；**IF-DOR-14/15** 修正签发不重复发放与费用草稿初建、费用批量迁移范围及暂存成功返回药品请求标识；§3 组套暂不记 Gap；§1.4/§9.1 收敛组套非目标 |
| v1.0 | 2026-05-03 | 初稿：IF-DOR-10～17；对齐「开立医嘱」Tab UI 与医嘱应用服务编排（目录检索、绑定、费用性质、暂存/签发/签退、组号）；§1.4 收敛历史医嘱与报告 URL 等非本 Tab 能力 |


---

**文档负责人**：（待填） · **评审**：（待填）
