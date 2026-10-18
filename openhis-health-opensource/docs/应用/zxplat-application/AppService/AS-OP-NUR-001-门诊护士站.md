# 门诊护士站 · 应用服务设计（AS）

> **文档职责**：说明门诊护士站场景的**接口契约（IF）**、业务语义、涉及聚合与 QueryService、规则（BR）、编排意图；作为上游契约供详细设计与实现对齐。
>
> **本文必须写**：IF 编号；用户动作与读写属性；关键校验与状态变化；Gap；异常口径；事件语义；非功能约束；业务向测试要点。
>
> **本文禁止写**：具体类名/应用方法名；Request/Response 字段明细；包路径；Mapper；伪代码；写库顺序；QueryService 字段组装规则。
>
> **页面边界**：门诊护士站三栏工作台——左侧患者队列（开立科室默认全部、关键字、**就诊日期区间**、**仅待发耗材**、刷新、点选患者；有待发耗材的卡片须可见提醒）；中区医嘱/处置执行列表与「执行并记录」；右侧耗材勾选、耗材房、打印动作入口、最近执行记录与「查看全部」抽屉；底栏已选项汇总、「一键清空」与「批量执行」。
>
> **编排对齐**：处置执行、队列与处置单查询、执行记录查询等与初版**门诊处置**应用编排一致。
>
> **皮试说明**：门诊皮试**不**单独成 AS 文档，契约收敛于本文 **§1.7、§2.1、§4（IF-OP-STK-01～04）** 与 **STK-SDD**；后端 `skintest` 包已实现；与处置**共用 IF-NUR-01**（`patient` 包）左侧列表，皮试为独立 Tab（**UI 尚未落地**，见 §1.4）。
>
> **覆盖声明**：§2 主表为**处置** IF（`treatment` 包）；§2.1 为**皮试** IF（`skintest` 包，后端已实现）；**患者队列** IF-NUR-01 独立 `patient` 包，处置/皮试 Tab 共用；队列准入见 **BR-NUR-001**。
>
> **编码**：AS-OP-NUR-001 · **版本**：v1.9.8 · **日期**：2026-08-19

---

## 1. 场景概述

### 1.1 目标与边界


| 维度 | 说明 |
| --- | --- |
| **在内** | 门诊就诊患者队列分页查询（**可选开立科室**、**就诊开始时间区间**、关键字过滤，默认全部科室、工作台默认当天起止；**队列准入与各请求聚合关联**，规则见 BR-NUR-001；**待发耗材提醒与可选仅看待发**见 **BR-NUR-017**）；点选患者后按 **就诊主键** 查询处置详情：**分诊疗 / 药品 / 耗材三张同构列表**返回，请求汇聚目录基础信息，账单经发放链路关联（见 BR-NUR-002 / BR-NUR-009）；**诊疗执行记录**查询；对所选诊疗/药品登记执行记录、对所选耗材按 **BR-NUR-014** 推进 **已配药** 或 **已发药** 并条件扣库存；**批量取消执行**（诊疗/药品追加取消类执行记录、耗材对称回退至执行前 **待配药** 并条件回补库存，见 BR-NUR-011～012）；与登录护士身份相关的执行人解析 |
| **明确不在本场景** | **收费明细回滚**（`ChargeItem` 取消执行回退账单等）——取消执行**不**改变已计费收费事实；退费/冲账由收费、退费类应用服务承担 |
| **在外** | 医嘱开立与签发、收费计价规则本身、药房摆药发药工作站主流程、通用报表打印模板引擎、住院护士站 |
| **涉及域** | Encounter（门诊队列口径）；ServiceRequest / MedicationRequest / DeviceRequest（请求侧）；ChargeItem（已计费门控）；Procedure（执行记录）；DeviceDispense / InventoryItem（耗材发放与库存）；组织与参与者上下文用于过滤与执行人 |
| **编排权威** | 以本文 **§2、§4** 为准 |


### 1.2 角色与关键动作


| 角色 | 关键动作 |
| --- | --- |
| **门诊护士** | 按**就诊日期区间**、可选开立科室（默认全部）、关键字与可选「仅待发耗材」刷新队列 → 点选患者（待发耗材卡片须有可见提醒）→ 查看待执行项目 → 勾选处置/药品行与待发放耗材 → 开关为开时可选指定耗材房 → **执行并记录**（或底栏批量执行）→ 必要时打开执行记录抽屉查阅历史 |
| **系统** | 患者队列按 BR-NUR-001 与诊疗/药品/耗材请求联合过滤，并按 **BR-NUR-017** 回填待发耗材标记、可选在分页前收窄；处置单合并退费关联记录更新展示状态；执行前校验已计费；按频次控制多次执行；耗材按 **BR-NUR-014** 推进已配药或就地改房后已发药并条件扣库存 |


### 1.3 术语与口径约定


| 术语 | 口径 |
| --- | --- |
| **处置单** | 面向护士执行的「可执行项」视图：来源包含诊疗服务申请、药品申请、以及关联的耗材发放草稿；展示层须合并「退费/退耗材」关联记录对发放态的影响（仅保留发放主档行并刷新其发放状态语义） |
| **账单与请求** | **收费明细跟着发放与计费链路走**，与诊疗/药品/耗材请求**未必一对一**；列表回显须在指定请求（或发放）锚点下聚合多条账单行：**总价为金额之和**，**收费状态取首条**——与QueryService **Q-ORD-006** 一致：**`adm_charge_item.id` 升序第一条**的 `status_enum` |
| **三列表应答** | 同一接口按请求类型拆为 **诊疗列表、药品列表、耗材列表**；**行级字段命名三套列表保持一致**以便前端复用组件；前端传入的 **诊疗类型（诊疗类目）筛选仅作用于诊疗列表**，不影响另两张列表 |
| **执行** | 对诊疗/药品请求：在满足已计费且未超出允执行次数时，新增一条**已完成**状态的执行记录（门诊医嘱执行类目）；对耗材：按全局开关 **BR-NUR-014**，**关** 时仅将待发发放单推进为 **已配药**（写配药人，**不**扣库存，**不**变更 **DeviceRequest**）；**开** 时可就地只改库房后推进为 **已发药** 并按签发批号合并扣减库存（耗材房规则见 **BR-NUR-006**） |
| **列表上的次数与数量** | **仅诊疗列表**承载「计划执行次数 / 已完成次数 / 已取消次数」语义（见 BR-NUR-010）；**药品与耗材列表以数量及发放态为主**，不在产品语义上强调「执行次数」维度；批量执行门控仍按 BR-NUR-005 与初版一致 |
| **取消执行** | **诊疗/药品**：护士勾选**具体已完成执行记录**（`procedureId`）；系统**追加一条** `EventStatus.CANCEL` 记录，其 `refund_id` 指向被对冲的 **COMPLETED** 行 `cln_procedure.id`（**BR-PRO-005**），二者在列表语义上视为已抵消；再次执行仍走 IF-NUR-04 新增 **COMPLETED**。**不**联动收费明细状态。**耗材**：按 **BR-NUR-014** 对称回退——执行前为 **待配药**（`PREPARATION`），取消后恢复 **待配药**；**关** 路径取消 **已配药**（`PREPARED`），**开** 路径取消 **已发放**（`COMPLETED`）并**回补库存**；**不**变更 **DeviceRequest** 状态；**与诊疗/药品 Procedure 链路无关** |
| **净剩执行记录** | 取消抽屉/勾选列表仅展示**尚未被 CANCEL 对冲**的 **COMPLETED** 行；已被 `refund_id` 关联的 **COMPLETED** 与对应 **CANCEL** 均不展示（见 **IF-NUR-03B**） |
| **皮试（门诊）** | 独立 Tab，**复用 IF-NUR-01 患者列表**（不另建皮试队列）；列表以医生开立的「皮试检查」诊疗 `ServiceRequest` 为主表，**须门诊收费结算后**（SR **`COMPLETED`**）才展示；护士保存**仅**写 `AllergyIntolerance`，**不**产生费用。完整口径见 **§1.7** |
| **护士站队列准入请求** | 用于判定就诊是否出现在左侧列表的请求集合：覆盖诊疗申请、药品申请、耗材请求等；**状态码、退费关联字段、输液标识、皮试标记**等以实现侧对应 DM 为准，本文仅约束业务语义 |
| **待发耗材** | 就诊下仍有护士未执行完的耗材：已收费完成、非退单镜像、**收费非退费态**的耗材请求，且尚无发放单或发放单仍为草稿/待配药/待发药（**不含**退费作废发放）。口径与右侧处置单「待发放」一致，见 **BR-NUR-017** / **BR-NUR-018** |
| **QueryService 能力** | 队列侧重EncounterQueryService（OutpatientEncounterContextQueryModel）（须能表达 BR-NUR-001 的请求联合准入）；项目列表与执行侧重医嘱信息与医嘱执行单QueryService；本文不写字段映射细节 |


### 1.4 本期范围与非目标


| 类别 | 内容 |
| --- | --- |
| **本期（契约）** | IF-NUR-01～05、**IF-NUR-03B**：队列、处置单、执行记录查询（全量分页 **IF-NUR-03** + 净剩可取消列表 **IF-NUR-03B**）、批量执行与**按 procedureId 批量取消执行**（IF-NUR-05）；**页面是否呈现取消入口**以实现/产品排期为准，不阻塞后端契约 |
| **非目标（同页交互但无对应处置编排）** | 「一键清空」为前端选择态重置，不单独开立 IF；四类打印（瓶贴、处置单、采血码、处方单）——初版门诊处置应用服务未收敛完整打印编排，**本期仅保留 UI 入口语义**，后端打印任务承载落在打印/报告类应用服务或后续迭代，不在本文 IF 表承诺 |
| **非目标（初版有、本页无入口）** | **门诊处置初期数据**加载；**输血贴查询**；初版**专用「仅皮试就诊」列表切换**（已废弃：与本页默认队列重复，默认队列已按 BR-NUR-001 含皮试药准入） |
| **UI 未接入（后端已实现）** | 皮试 Tab 前端未落地；**IF-OP-STK-01～04** 已落盘；联调以 **§1.7** 与 **STK-SDD** 为准 |
| **后端子域（v1.9）** | **患者** `OutpatientNurseStationPatientAppService`（IF-NUR-01）· **处置** `OutpatientNurseStationTreatmentAppService`（IF-NUR-02～05/03B）· **皮试** `OutpatientNurseStationSkinTestAppService`（IF-OP-STK-01～04）；SDD 分拆 **PAT / TRT / STK** |
| **非目标（其它工作站）** | 输液巡视、门诊药房审方发药等由专科 AS 覆盖 |


### 1.5 领域清单（聚合 + 职责 + DM 路径）


| 聚合（域） | 在本场景中的职责（摘要） | DM 路径 |
| --- | --- | --- |
| **Encounter** | 队列限定门诊就诊生命周期口径；与诊疗/药品/耗材请求联合判定是否纳入护士站列表 | `DM-ADM-002-Encounter-就诊.md` |
| **ServiceRequest** | 诊疗类医嘱请求；处置执行关联 | `DM-WKF-004-ServiceRequest-服务申请.md` |
| **MedicationRequest** | 药品类医嘱请求；列表侧重数量展示；**批量执行门控**仍可按初版使用药品「执行次数」字段对比 Procedure 净次数 | `DM-MED-003-MedicationRequest-药品申请.md` |
| **DeviceRequest / DeviceDispense** | 耗材请求与发放单；出库与追溯 | `DM-WKF-003-DeviceRequest-耗材请求.md` / `DM-WKF-007-DeviceDispense-耗材发放.md` |
| **ChargeItem** | 执行前「已计费」门控；处置列表上与发放链路关联，多行聚合计价 | `DM-ADM-011-ChargeItem-收费明细.md` |
| **Procedure** | 门诊医嘱执行事实（完成/取消语义） | `DM-CLN-004-Procedure-执行记录.md` |
| **InventoryItem** | 耗材库存数量更新 | `DM-WKF-008-InventoryItem-库存项.md` |
| **AllergyIntolerance** | 皮试临床结果事实；`request_id` 锚定医生开立「皮试检查」`ServiceRequest`（**非**皮试药 `MedicationRequest`） | `DM-CLN-001-AllergyIntolerance-过敏与不耐受.md` |
| **ActivityDefinition** | 固定诊疗「皮试检查」活动定义 | `DM-WKF-001-ActivityDefinition-诊疗项目定义.md` |


### 1.6 业务规则（BR-*）


| 规则 ID | 描述 | 绑定 IF |
| --- | --- | --- |
| **BR-NUR-001** | 患者队列须限定**门诊类别**就诊；且同一就诊须**至少存在一条**同时满足下列条件的请求（诊疗申请 / 药品申请 / 耗材请求等，与各 DM 请求实体对齐）：① 请求状态为 **`COMPLETED`**；② **退费关联标识为空**（无有效退费关联主键，语义以实现侧 DM 字段为准）；③ 且满足下列**任一**：存在**诊疗类**开立请求；或存在**耗材类**开立请求；或存在药品申请且**输液标识为真**；或存在药品申请且**皮试标记为真**。关键字匹配须覆盖**患者姓名、拼音检索码、五笔检索码**；**可选开立科室**筛选：未传=全部科室，传入则等值匹配就诊挂号/开立科室上下文；**可选就诊开始时间区间**：传入起止时刻则按就诊开始时间闭区间收窄，未传不按日过滤（工作台默认当天起止）；分页与排序口径与工作站上下文一致。**患者列表与中区诊疗/右侧耗材同一可见性轴**（见 **`health/设计决策/DD-门诊护士站与医技工作站-诊疗耗材可见性-2026-09.md`**）：护士站队列保留「本站有活」就诊（非 TECH 诊疗 / 非 TECH 耗材房待处理耗材 / ③ 输液或皮试药）。**同一就诊可同时出现在护士站与医技站列表**（两边都有活时）；点选后 IF-NUR-02 中/右区只返回本站轴下行，不得把对方站项目/耗材一并吐出。仅医技活、无护士站活的就诊归医技站（同发后护士站不再列出）。医技站上线同发；未上线前不得先按本轴从护士站藏医技活就诊/耗材 | IF-NUR-01 |
| **BR-NUR-017** | **待发耗材提醒与筛选**：队列行须标明该就诊是否仍有待护士发放的耗材。判定对齐处置单侧栏「待发放」：存在已完成且非退单镜像、**收费汇总仍为 PLANNED/SETTLED（有效计费）**的耗材请求，且该请求尚无发放单、或任一发放单仍为草稿/待配药/待发药（**不含**退费作废 `DECLINED` 等终态；空状态按待发放）；**且发放耗材房上级科室须属本站可见轴**（护士站：非 TECH；与 DD 同轴，勿把医技科耗材房待发算进护士站红点）。勾选「仅待发耗材」时，须在**分页前**按该口径收窄列表（含有效计费 EXISTS）。队列卡片对命中行给出可见提醒（红框与「待发耗材」标签），**当前选中卡片亦不得掩盖该提醒** | IF-NUR-01 |
| **BR-NUR-002** | 点选患者后以 **就诊主键** 查询：按三类请求分别装载并与各自**目录/定义**汇聚基础信息；诊疗/药品/耗材的过滤与初版「处置项目」一致（请求侧已完成等）；**叠加本站可见性轴**（同 DD / BR-NUR-001）：诊疗仅返回非 TECH 执行科室行，**例外**：执行科室为 TECH 且类目为**检验（22）**且**非**合管派生「采血费」的行仍返回，供前端下方「医技列表」采血贴，**不得**进入上方待执行勾选；**采血费**（内置名称精确匹配，类目虽为 22）**不**走本例外、**不**打采血贴（仅计费）；耗材仅返回发放耗材房挂靠科室（及组织祖先）非 TECH 的行；药品仍按既有输液等护士站口径。发放侧诊疗可为空发放占位、药品/耗材按初版与发放单关联；**收费明细经发放/计费链路关联**，与单一请求**不一定一对一**；行内展示 **总价 = 关联账单金额之和**，**收费状态 = `adm_charge_item.id` 升序首条**（与 **OrderExecutionSheetLoader** 计费聚合口径 / **BR-ORD-005** 一致）；**耗材侧栏**另回填主发放行 **签发批号 `lotNumber`** 与账单升序首条 **`unitPrice`**（供卡片展示；占位批号不下发） | IF-NUR-02 |
| **BR-NUR-003** | 若同一请求存在退费/退耗材关联记录，列表展示须用关联记录刷新主发放记录的展示状态，**明细行层面不再重复保留退单镜像行** | IF-NUR-02 |
| **BR-NUR-018** | **耗材侧栏退费展示**：IF-NUR-02 `deviceLines`（右侧耗材明细）对收费汇总为 **REFUNDING/REFUNDED** 的耗材请求**仍返回**，展示「待退费/已退费」，**不可勾选**随批执行（对齐中区药品退费展示）。有效计费（PLANNED/SETTLED）下发放态为 **DECLINED/STOPPED/CANCELLED/REFUNDED/PENDING_REFUND** 的不返回，避免误显示「待发放」。IF-NUR-04 执行前仅保留有效计费发放行。待发红点 / 「仅待发」（**BR-NUR-017**）仍不含退费态 | IF-NUR-02 / IF-NUR-04 |
| **BR-NUR-009** | 应答拆成 **三张列表**（诊疗 / 药品 / 耗材），**行字段名三套一致**；前端传入的 **诊疗类型（诊疗类目码）仅过滤诊疗列表**，药品列表与耗材列表**忽略**该参数；耗材列表另受 **BR-NUR-018** 约束 | IF-NUR-02 |
| **BR-NUR-010** | **诊疗列表行**：计划执行次数取 **诊疗请求 `execution_count`**（临嘱/门诊；与计费 `quantity` 分离）；**已完成次数** = 该请求下执行记录中「完成」态条数；**已取消次数** = 「取消」态条数（与初版门诊处置查询后置统计一致）。**药品列表与耗材列表行**：以 **数量**、发放状态、计费聚合为主，**不使用执行次数语义**填充与同构字段（留空或零由 SDD 约定） | IF-NUR-02 |
| **BR-NUR-015** | **申请单聚组展示**：诊疗列表行须回填 `prescriptionNo`（`wkf_service_request.prescription_no`）；**`AR` 前缀**（检验/检查/治疗申请单整单号）行在列表中**按申请单号聚邻排序**（组内 `sortNumber` → `requestId`），**不合并行**；前端中区待执行表在**同组首行医嘱内容上方**展示申请单标签，同组浅底/左侧色条/组间分隔线；非申请单行无额外占位 | IF-NUR-02 |
| **BR-NUR-016** | **申请单明细回显**：`AR` 前缀诊疗行须批量预取 `ServiceRequestDetail` 并回填 `serviceRequestDetails[]`（部位/样本展示名 + 明细同行 `remark`）及行级纯备注 `remark`（`wkf_service_request.remark`）；前端中区待执行表在医嘱内容 extension 区展示「样本/部位：…；备注：…」（有值才展示）；口径对齐 IF-DOR-10 `serviceRequestDetails` | IF-NUR-02 |
| **BR-NUR-004** | 批量执行中，凡诊疗/药品类条目，执行前必须存在对应收费明细且至少一条处于**已计费**态；否则该批拒绝或剔除后拒绝（与初版「未找到已计费收费项」一致） | IF-NUR-04 |
| **BR-NUR-005** | 每一次「执行」门控与初版门诊处置一致：**诊疗**计划次数取请求 **quantity**；**药品**计划次数取请求 **executeNum**（字段语义以 DM 为准）；对比既有 Procedure **完成次数减取消次数**；仅当净次数仍小于计划次数时才允许新增**完成态**记录 | IF-NUR-04 |
| **BR-NUR-006** | **耗材房**（**仅当 BR-NUR-014 开关为开**时）：护士载荷传入耗材房时，写前断言目标房下**签发批号**可用量，够量则就地改发放行 `location_id`，并回写请求 `location_id`+`perform_location`（**与发放行必须一致**）；不足则提示 **「【耗材名】批号xxx可用库存不足，无法执行发放」**；**禁止**改批号、**禁止**重跑 FIFO、**禁止**软删重建发放主键、**禁止**改已收费账单。缺真实批号则拒绝并提示医生改签。**未传**时沿用医生开立默认发材房（签发已锁库房与批号），仍按该房该批断言库存并回写请求使与发放行一致。开关为 **关** 时**不要求**传耗材房。院点可仅在前端强制必选。**上游防呆**：签发挂费见 **BR-ORD-033**、收银见 **BR-OPC-023** | IF-NUR-04 |
| **BR-NUR-007** | **批号锁定**：护士执行扣库须使用医生签发已写入发放行的批号；**不**在护士换房/执行/**取消执行**路径重跑 FIFO 或置空批号。FIFO 与多批拆行仅发生在**医生签发**侧；签发分段批号不得为空（库存无真实批号则拒绝签发）；库存不足时拒绝或提示核对库存 | IF-NUR-04 |
| **BR-NUR-008** | 并发下对待发放耗材过滤须避免重复发放（仅处理仍为**准备中**态的发放行） | IF-NUR-04 |
| **BR-NUR-014** | **耗材是否自动扣库**（全局开关 `deviceAutoInventoryDeductSwich`，`SettingDefinitionEnum`，`"1"` 开 / `"0"` 关）：**关**——IF-NUR-04 耗材仅 **PREPARATION→PREPARED**（配药人=执行护士），**不**扣库存，**不**调用 `executeDeviceOrder`，**DeviceRequest** 不变；**开**——按 **BR-NUR-006** 可选就地只改库房（不改批号）后 **PREPARATION→COMPLETED** 并按签发批号 **合并扣减** 库存；IF-NUR-05 对称回退至执行前 **PREPARATION**（**关**：`revertPreparedBatchToPreparation`；**开**：`revertCompletedBatchToPreparation` + 库存回补）；编排见 **`DeviceDispenseNurseWorkflowSupport`** | IF-NUR-04 / IF-NUR-05 |
| **BR-NUR-011** | **取消执行（诊疗/药品）**：入参为勾选的 **已完成执行记录主键**（`procedureId`），**非**来源请求主键；每条勾选追加一条 **CANCEL**（`create→start→cancel`，`skipDuplicateRequestAnchorCheck=true`），`refund_id` **必填**且等于被对冲的 **COMPLETED** 行 id（**BR-PRO-005**）；须为当前就诊、状态 **COMPLETED**、尚未被其它 CANCEL 的 `refund_id` 占用；同批禁止重复勾选同一 `procedureId`。**不**校验、**不**变更收费明细 | IF-NUR-05 |
| **BR-NUR-013** | **净剩可取消列表**：同一就诊（可选缩窄单条 `requestId+requestTable`）下，返回所有**未被对冲**的 **COMPLETED** 行；若存在 **CANCEL** 且 `refund_id` 指向某 **COMPLETED**，则该 **COMPLETED** 与对应 **CANCEL** 均**不返回**；仅含诊疗/药品来源表 | IF-NUR-03B |
| **BR-NUR-012** | **取消执行（耗材）**：仅允许对护士执行后 **已配药**（`PREPARED`，开关 **关**）或 **已发放**（`COMPLETED`，开关 **开**）的发放行取消；对称回退至执行前 **待配药**（`PREPARATION`）；**保留医生签发批号**（**BR-DD-009** / **BR-NUR-007**，禁止置空 `lot_number`、禁止重跑 FIFO），仅清空拣配/发放操作字段与追溯码；**开** 路径按实发批号与库房**回补库存**（占位批号跳过）；**不**变更 **DeviceRequest**；**不**调用 `revokeDeviceOrderExecution` | IF-NUR-05 |


### 1.7 门诊皮试（目标态附属契约）

> **定位**：与 **§2 处置 IF** 同属门诊护士站工作台，但业务链路与费用时机**独立**；**不**拆 `AS-OP-NUR-002` 单独文档。与住院皮试（**AS-IP-NUR-011**）对照阅读。

#### 1.7.1 页面与患者上下文

| 项 | 口径 |
| --- | --- |
| **工作台布局** | 三栏与处置相同：左侧患者队列 → 中区/右侧按 Tab 切换；皮试为**独立 Tab**（目标态），**不**嵌入处置三列表 |
| **患者列表** | **与处置共用 IF-NUR-01**；护士在左侧点选同一患者后，可切「处置」或「皮试」Tab，**encounterId / patientId 一致** |
| **皮试专用队列** | **不提供**；初版「仅皮试就诊」切换**废弃**——皮试患者已通过 BR-NUR-001 ③「药品皮试标记为真」进入默认队列 |
| **与处置列表关系** | IF-NUR-02 处置三列表**不**承载皮试登记；皮试行由 **IF-OP-STK-02** 单独查询，避免与药品/诊疗执行语义混淆 |

#### 1.7.2 端到端业务链（医生 → 收费 → 护士）

```
医生站开立皮试药（MedicationRequest，skinTestFlag=true）
    └─ 同时/联动开立「皮试检查」诊疗（ServiceRequest，doctor-prescription，based_on→皮试药）
           └─ 同步落 ChargeItem（皮试操作费，DRAFT→待收）
                  └─ 门诊收费结算（AS-OP-FIN-002）→ 皮试检查 SR 置 COMPLETED
                         └─ 护士皮试 Tab（IF-OP-STK-02）可见该行 → 双签保存结果（IF-OP-STK-03）
                                └─ 仅写 AllergyIntolerance；医生站回显 skinTestOutcome*
```

| 阶段 | 角色 | 动作 | 费用/状态 |
| --- | --- | --- | --- |
| ① 开立 | 医生 | 皮试药 `MedicationRequest` + 「皮试检查」`ServiceRequest`（`based_on_table=med_medication_request`，`based_on_id=皮试药 id`） | 皮试药费、皮试操作费各一条 `ChargeItem`（开立时多为 **DRAFT/待收**） |
| ② 结算 | 收费 | 门诊收费勾选含皮试操作费（及皮试药费）并结算 | 皮试检查 SR → **`COMPLETED`**（门诊口径=已收费关闭）；`ChargeItem` → **已结算** |
| ③ 列表 | 护士 | IF-OP-STK-02 按就诊加载皮试行 | **门控**：仅 **`COMPLETED`** 的皮试检查 SR 进列表（**未收费不可登记**） |
| ④ 登记 | 护士 | IF-OP-STK-03 双签保存临床/验证状态、批号 | **仅** `saveSkinTestRecord`；**不**创建 SR/Charge；**不**扣皮试药库存 |
| ⑤ 回显 | 医生站 | 读 `AllergyIntolerance` 临床状态 | 与住院相同投影语义 |

#### 1.7.3 与住院皮试（AS-IP-NUR-011）差异

| 维度 | 门诊（本文） | 住院（AS-IP-NUR-011 v1.1） |
| --- | --- | --- |
| 皮试操作费医嘱 | 医生开立「皮试检查」`ServiceRequest` | 医生联动「皮试检查」`ServiceRequest`（**BR-IP-ORD-024**，**不**挂费） |
| 费用结算时机 | **门诊收费后**护士才可见列表 | 护士**首次保存**同事务锚 doctor SR 计费（待结算） |
| 列表主表 | 皮试检查 `ServiceRequest`（`COMPLETED`） | 皮试药 `MedicationRequest`（**VERIFIED**）+ doctor 联动 SR |
| 列表门控 | 皮试操作费 **已结算**（SR `COMPLETED`） | 皮试药 **已校对** + 存在 doctor 联动 SR |
| 护士保存写侧 | 仅 `AllergyIntolerance` | 首次：锚 doctor SR 的 `ChargeItem` + `AllergyIntolerance`（**不**新建 SR） |
| `AllergyIntolerance.request_id` | 医生开立「皮试检查」SR.id | **同左**（doctor-prescription SR.id） |
| 删除 | 误录可删过敏记录（**不**冲销已结算费用） | 仅**未计费**可删 |

#### 1.7.4 皮试列表查询机制（IF-OP-STK-02 语义）

| 项 | 口径 |
| --- | --- |
| **主表** | 当前就诊下、活动为「皮试检查」的 **`ServiceRequest`**（`generate_source_enum = doctor-prescription`） |
| **关联** | `based_on` → 皮试药 **`MedicationRequest`**（取药品名、开立医生、发放意向库房）；左连接 **`AllergyIntolerance`**（`request_id = 皮试检查 SR.id`） |
| **准入过滤** | 皮试检查 SR **`status_enum = COMPLETED`**（与初版 `getRecordInfo` 一致：= 门诊收费已关闭）；`based_on_id` 非空 |
| **不含** | 不以皮试药 `MedicationRequest` 单独作主表；**不要求**皮试药 `VERIFIED`；**不**要求皮试药已发药才进列表 |
| **批号候选** | 按皮试药 `medication_id` + 关联发放意向 `location_id` 只读库存批号；**不**扣库存 |
| **空列表** | 医生已开皮试但未收费 → 列表为空（**非错误**，提示先收费） |

#### 1.7.5 皮试业务规则（BR-OP-STK-*）

| 规则 ID | 描述 | 绑定 IF |
| --- | --- | --- |
| **BR-OP-STK-001** | 皮试读/写须 **encounterId** 有效，且就诊须能通过 **IF-NUR-01** 同一可见性口径（与处置共用患者上下文） | 02、03、04 |
| **BR-OP-STK-002** | **列表行来源**：当前就诊下 `activity_id = 皮试检查定义` 且 `generate_source_enum = doctor-prescription` 的 `ServiceRequest`，且 **`status_enum = COMPLETED`**；每行对应一条皮试检查 SR | 02 |
| **BR-OP-STK-003** | 列表须装配：皮试药名、皮试药 `medicationRequestId`、皮试检查 `serviceRequestId`、开立医生、已有过敏记录（若有）、批号候选、枚举展示文案 | 02 |
| **BR-OP-STK-004** | **保存**：`practitionerId`、`checkPractitionerId` 均必填；`clinical_status_enum`、`verification_status_enum` 必填；`request_id` **必须**为列表行的皮试检查 SR.id | 03 |
| **BR-OP-STK-005** | **保存不写费用**：禁止在 IF-OP-STK-03 创建/更新 `ServiceRequest` 或 `ChargeItem`；皮试操作费已在医生开立+门诊收费链路完成 | 03 |
| **BR-OP-STK-006** | **更新保存**：已存在 `allergyRecordId` 时仅 `saveSkinTestRecord` 覆盖；**禁止**变更 `request_id` 指向其它 SR | 03 |
| **BR-OP-STK-007** | **删除**：允许删除误录 `AllergyIntolerance`；**不**删除皮试检查 SR、**不**冲销已结算 `ChargeItem` | 04 |
| **BR-OP-STK-008** | 保存/删除**不**变更皮试药/皮试检查请求状态；**不**拦截发药/处置执行；**不**扣减皮试药库存 | 03、04 |

---

## 2. 页面与接口总览（契约概览）


| IF 编号 | 用户动作 | 接口语义（业务） | 读/写属性 | 关键规则（BR） | 关键状态变化（自然语言） | 涉及域 | 查询服务 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| IF-NUR-01 | 选择队列**日期区间**、可选开立科室（默认全部）、输入关键字、勾选「仅待发耗材」、点击刷新 | 分页查询**门诊**就诊患者队列（**按请求准入**：见 BR-NUR-001；可选开立科室与日期区间收窄；行内待发耗材标记与可选仅看待发：见 **BR-NUR-017**） | 读 | BR-NUR-001、**BR-NUR-017** | 无写状态变化 | Encounter / ServiceRequest / MedicationRequest / DeviceRequest / DeviceDispense | EncounterContextReader · OutpatientEncounterContextQueryModel |
| IF-NUR-02 | 点选患者后加载中区列表 | 按 **encounterId** 查询处置详情：**三张同构列表**（诊疗 / 药品 / 耗材）+ 目录与账单聚合（BR-NUR-002 / BR-NUR-009 / BR-NUR-010 / **BR-NUR-015** / **BR-NUR-016**） | 读 | BR-NUR-002～003 / BR-NUR-009～010 / **BR-NUR-015** / **BR-NUR-016** | 无写状态变化 | Encounter / ServiceRequest / MedicationRequest / DeviceRequest / ChargeItem / DeviceDispense / Procedure（诊疗次数统计） | OrderExecutionSheetLoader · 域请求查询 |
| IF-NUR-03 | 打开「执行记录」抽屉、切换记录类型筛选 | 分页查询**诊疗执行记录全量**（含已完成、已取消等；可按请求、状态、时间范围收缩） | 读 | — | 无写状态变化 | Procedure | OrderExecutionSheet（医嘱执行单） |
| IF-NUR-03B | 打开「取消执行」勾选列表（或执行记录抽屉内可取消 Tab） | 查询**净剩可取消**的已完成执行记录（**BR-NUR-013**）；已对冲的 COMPLETED/CANCEL 对不展示 | 读 | BR-NUR-013 | 无写状态变化 | Procedure | —（域查询 `cln_procedure`，非QueryService 分页） |
| IF-NUR-04 | 勾选执行项与耗材、**耗材房（开关为开时：传入则就地只改 `location_id`；未传回落医生签发库房与批号；院点可前端必选）**，点击「执行并记录」或底栏「批量执行」 | **批量处置执行**：诊疗/药品登记完成执行（门控 BR-NUR-005）；耗材按 **BR-NUR-014** 推进已配药或就地改房后已发药+扣库（**BR-NUR-006～008**） | 写 | BR-NUR-004～008、014 | 新建 Procedure（完成）；DeviceDispense→PREPARED 或 COMPLETED；条件 InventoryItem 扣减；**DeviceRequest 状态不变**（护士指定房时可回写 `perform_location`，不改批号） | Procedure / DeviceDispense / InventoryItem / ChargeItem（读校验） | OrderExecutionSheet；库存侧快照/流水类QueryService |
| IF-NUR-05 | 在净剩列表中勾选 **procedureId**，或勾选已执行耗材（**PREPARED** 或 **COMPLETED**），触发**取消执行** | **批量取消执行**：诊疗/药品按 **procedureId** 追加 **CANCEL**（**BR-NUR-011**）；耗材对称回退 **PREPARATION**（**BR-NUR-012**）；**不涉及收费回滚** | 写 | BR-NUR-011～012、014 | 新建 Procedure（取消）；DeviceDispense 回退 PREPARATION；条件 InventoryItem 回补；**DeviceRequest 不变** | Procedure / DeviceDispense / InventoryItem | OrderExecutionSheet；库存QueryService |


### 2.1 门诊皮试（已实现·UI 未接入）

> 与 §2 主表**并列**；后端已落盘 **`OutpatientNurseStationSkinTestAppService`**（`skintest` 包），HTTP **`outpatientNurseStationSkinTest`**；详 **AS-OP-NUR-001-STK-皮试-SDD**。编号 **IF-OP-STK-*** 与住院 **IF-IP-STK-*** 对称。

| IF 编号 | 用户动作 | 接口语义 | 读/写 | 关键规则 | 关键状态变化 | 涉及域 | 查询服务 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| IF-OP-STK-01 | 进入皮试 Tab / 打开表单 | 皮试表单枚举初始化 | 读 | — | 无 | —（枚举） | — |
| IF-OP-STK-02 | 点选患者后加载皮试列表 | 按 **encounterId** 查询待登记/已登记皮试行 | 读 | BR-OP-STK-001～003 | 无 | ServiceRequest / MedicationRequest / AllergyIntolerance / InventoryItem | ServiceRequestQueryService；MedicationRequestQueryService；AllergyIntoleranceQueryService；库存只读 |
| IF-OP-STK-03 | 填写结果并保存 | **保存皮试临床结果**（双签） | 写 | BR-OP-STK-004～006、008 | 新建或更新 `AllergyIntolerance`；**不**写 SR/Charge | AllergyIntolerance | — |
| IF-OP-STK-04 | 删除误录 | 删除皮试过敏记录 | 写 | BR-OP-STK-001、007、008 | 过敏记录逻辑删除；**不**删 SR/Charge | AllergyIntolerance | — |

---

## 3. 缺口跟踪（Gap）

| Gap ID | 缺口类型 | 文件 + 章节 | 缺什么 / 不一致点 | 关闭条件 | 影响 IF | 优先级 | 状态 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| GAP-OP-STK-001 | QueryService | `ActivityDefinitionQueryService` §6.2 | 按名称解析皮试检查 `activityId` | **已关闭**（2026-06-10）：与 **GAP-IP-STK-001** 同源——`GeneralSharedConstants.SKIN_TEST_INSPECTION_ACTIVITY_NAME = "皮试检查"` + `#requireIdByExactName(name)` | IF-OP-STK-02、03 | P1 | **已关闭** |

> **口径**：**IF-OP-STK-03** 复用 `AllergyIntoleranceDomainService#saveSkinTestRecord` / `#deleteSkinTestRecord` 不在此表重复登记；应用层缺口**不**以 Gap 表表达（**GAP-OP-STK-001** 已关闭；皮试 AppService 已实现）。

> **口径**：Gap 仅登记**本应用服务契约尚未闭合、需产品/领域拍板后才能实现**的项。下列内容**不**记入 Gap：**Procedure** 在其它场景对进行中记录调用 `cancelProcedureRecord`、护士站 IF-NUR-05 采用追加 **CANCEL** 记录——属**不同 IF 的合法路径并存**；领域实现与领域事件后查询刷新细节、联调与测试验证——见 **SDD** §1.3 **DOC-CONFLICT** 与 §7 测试要点，**不**以 Gap 表表达。

（历史关闭：**GAP-NUR-001** → EncounterContextReader#pageNurseStationOutpatientQueue §3.5；**GAP-NUR-002** → OrderExecutionSheetLoader §3.4 / **BR-ORD-005**。v1.5 曾误登 **GAP-NUR-C01～C03**，v1.5.1 移除。）

---

## 4. 接口编排说明书（业务步骤）

### IF-NUR-01 查询门诊护士站患者队列


| 维度 | 说明 |
| --- | --- |
| **触发前提** | 护士已登录并具备工作站上下文 |
| **主路径步骤** | 组装分页条件（**开立科室可选，默认全部**；**就诊开始时间区间**可选） → 限定就诊类别为**门诊** → **按 BR-NUR-001 与诊疗申请、药品申请、耗材请求联合判定**：仅保留「至少一条」同时满足状态 **`COMPLETED`**、退费关联为空、且（诊疗开立 **或** 耗材开立 **或** 药品输液标识 **或** 药品皮试标记）的请求所挂钩的就诊 → **叠加护士站可见性轴**（与中区/右侧同 DD：非 TECH 诊疗或非 TECH 耗材房耗材或输液/皮试药；医技站上线同发后排除「仅医技活」就诊） → 若传入开立科室则等值匹配就诊挂号/开立科室上下文 → 应用日期区间与关键字 → 勾选「仅待发耗材」时按 **BR-NUR-017** 在分页前收窄 → 按姓名、拼音码、五笔码等与关键字 OR 匹配 → 返回分页结果 → 对行内性别等枚举做展示文案解析；出生日期存在时派生年龄展示 → 本页行回填是否仍有待发耗材（**BR-NUR-017**） |
| **关键分支** | 不满足 BR-NUR-001（含本站可见性轴无活）的就诊不出现在本列表（空页属正常结果）；未选开立科室时不按开立科室收窄（可见性轴仍按执行科室/发放耗材房收窄）；未勾选「仅待发耗材」时不按耗材待发收窄，但仍回填行内提醒标记；初版「仅皮试就诊」专用列表切换非本页能力，见 §1.4 |
| **失败分支** | 查询不可用 → EX-NUR-101 |
| **查询侧说明** | 纯读；依赖EncounterQueryService（OutpatientEncounterContextQueryModel）队列场景 |


---

### IF-NUR-02 查询当前就诊处置单列表


| 维度 | 说明 |
| --- | --- |
| **触发前提** | 已在队列中选定就诊（具备 **encounterId**） |
| **入参意图** | **就诊主键** 必填；**诊疗类型（诊疗类目码）** 可选，**仅缩小诊疗列表**，不传则诊疗列表不按类目过滤 |
| **主路径步骤** | 分别以诊疗申请、药品申请、耗材请求（及关联发放）为主表查询当前就诊数据 → **按本站可见性轴收窄**（诊疗：执行科室非 TECH，**真实检验 22 例外**仍返回供采血贴，**采血费除外**；耗材：发放耗材房挂靠科室及祖先非 TECH；药品：既有护士站口径；与 IF-NUR-01 / DD 同轴，两边都有活的患者点开后不得吐出对方站行） → **一次**批量加载三类**目录/定义**与类目术语（`module.common` 共用装配，**禁止**按医嘱循环查库）→ **一次**预热药品用法/频次/单位术语与产品字典（步骤 **8b**，对齐 IF-DOR-10 **BR-ORD-022**）→ **一次**批量预取申请单 `ServiceRequestDetail`（**BR-NUR-016**，对齐 IF-DOR-10）→ 耗材发放主键映射 **一次 IN 查询**后内存分组（**禁止**按耗材请求逐条查发放 id）→ 补全 `itemName`、`itemCode`（`bus_no`）、规格、检索码及 **类目 code + 类目展示名**（`medCategoryCodeText` 等）→ **药品列表**额外回显单次剂量/总量/用法/频次/天数（`dose*` / `method*` / `unitName` / `dispensePerDuration`）→ 按 **发放与计费链路**挂载收费明细（允许多行），在行锚点上 **金额字段聚合求和**、**收费状态取约定排序首条** → 应用 BR-NUR-003 合并退费镜像对主单发放态的覆盖 → 按 BR-NUR-009 组装 **三张字段同构的列表** → 对 **诊疗列表**按请求批量加载 Procedure，按 BR-NUR-010 写入完成次数与取消次数；**诊疗列表**回填 `prescriptionNo`、**BR-NUR-016** 部位/样本/备注并按 **BR-NUR-015** 申请单号聚邻排序 → **药品/耗材列表**按 BR-NUR-010 不使用执行次数语义，药品强调数量与用法用量展示 |
| **类目口径** | Request **诊疗类型**参数仅过滤诊疗列表（比对请求 `therapy_enum`）；应答行内 `serviceCategoryCode` / `medCategoryCode` 为**定义表类目** + `*Text` 展示，**不得**与筛选参数混为同一字段语义 |
| **关键分支** | 某一类列表无数据时返回该类空数组（非错误）；分页策略若为三类独立分页须在 SDD 明确，默认可与初版一致按合并查询后再拆分 |
| **失败分支** | 查询不可用 → EX-NUR-102 |
| **查询侧说明** | 纯读；OrderExecutionSheetLoader 与域请求查询须覆盖三列表与计费聚合（见 OrderExecutionSheetLoader 与域请求查询）；诊疗次数统计与医嘱执行单投影口径一致 |


---

### IF-NUR-03 查询诊疗执行记录（全量分页）


| 维度 | 说明 |
| --- | --- |
| **触发前提** | 护士打开执行记录抽屉「查看全部」或切换筛选 |
| **主路径步骤** | 可按单个请求标识缩窄；可按执行状态、分类与时间范围过滤 → 分页查询**全量**执行记录（含 **CANCEL** 与已对冲的 **COMPLETED**）→ 枚举执行状态展示文案 |
| **失败分支** | 查询不可用 → EX-NUR-103 |
| **查询侧说明** | 纯读；医嘱执行单QueryService |


---

### IF-NUR-03B 查询净剩可取消执行记录


| 维度 | 说明 |
| --- | --- |
| **触发前提** | 护士打开取消执行勾选列表，或抽屉内「可取消」视图 |
| **入参意图** | **就诊主键** 必填；可选 **requestId + requestTable** 缩窄至单条医嘱 |
| **主路径步骤** | 校验就诊存在 → 加载该就诊下诊疗/药品来源的 Procedure → 收集所有 **CANCEL** 行的 `refund_id` 为已对冲集合 → 仅返回状态为 **COMPLETED** 且 id **不在**已对冲集合中的行 → 按记录时间倒序展示 |
| **关键分支** | 无净剩行时返回空列表（非错误）；**不**返回 **CANCEL** 行本身 |
| **失败分支** | 查询不可用 → EX-NUR-103；缩窄时缺少 `requestTable` → 参数错误 |
| **查询侧说明** | 纯读；直接域表查询，与 IF-NUR-03 QueryService 分页职责分离 |


---

### IF-NUR-04 批量处置执行（含耗材发放）


| 维度 | 说明 |
| --- | --- |
| **触发前提** | 至少勾选一条执行项或待发耗材；护士身份有效 |
| **主路径步骤（诊疗/药品）** | 校验勾选非空 → 批量解析请求标识对应的收费明细 → **剔除**不存在任一已计费明细的条目 → 对每个保留条目读取来源请求（诊疗或药品）以解析就诊、患者、计划执行次数 → 读取既有执行记录并统计完成与取消 → 若完成减取消仍小于计划次数则新增一条**完成态**门诊医嘱执行记录并持久化 → **登记主执行人至 `cln_procedure_performer`（与 Procedure 暂 1:1，`functionCode=PRIMARY`）**；否则跳过新增 → 任一步持久化返回空标识则视为失败 |
| **主路径步骤（耗材）** | 若勾选含耗材发放：读取 **BR-NUR-014** 开关 → 解析执行人（允许入口覆盖）→ 委托 **`DeviceDispenseNurseWorkflowSupport#applyOutpatientNurseDeviceDispenses`**：**关**——逐条校验仍为 **准备中**，拣配为 **已配药**（配药人=执行护士），**不**扣库；**开**——校验耗材房（**BR-NUR-006**），写前断言目标房签发批号库存，必要时**就地只**回写 `location_id`/`perform_location`（**禁止**改批号、**禁止**FIFO、**禁止**软删重建、**禁止**改账单）后拣配+发放为 **已发药**，批次末按签发批号 **合并扣减** 库存（**BR-NUR-007**）；**不**调用 `executeDeviceOrder`；**DeviceRequest** 状态不变 |
| **关键分支** | 仅耗材或仅诊疗/药品分支可单独成功；诊疗/药品路径全部因计费原因剔除时须拒绝；开关 **关** 时无耗材房 Gate |
| **失败分支** | 未选任何项目 → EX-NUR-201；无已计费收费项 → EX-NUR-202；执行记录未能创建 → EX-NUR-203；开关 **开** 且未选耗材房 → EX-NUR-204；库存或批号分配失败 → EX-NUR-205～207；发放批量保存失败 → EX-NUR-208 |
| **查询侧说明** | 执行记录QueryService 增量刷新；耗材发放与InventoryItemQueryService 查询 Consumer 按各领域事件更新 |


---

### IF-NUR-05 批量取消执行


| 维度 | 说明 |
| --- | --- |
| **触发前提** | 至少勾选一条净剩 **procedureId**（**COMPLETED**）或护士执行后的耗材发放行（**PREPARED** 或 **COMPLETED**）；护士身份有效；**同一就诊**内批量 |
| **主路径步骤（诊疗/药品）** | 校验勾选非空 → 对每个 **procedureId** 校验：记录存在、状态 **COMPLETED**、属于当前就诊、来源为诊疗/药品表、尚未被 **CANCEL.refund_id** 对冲 → 追加一条 **CANCEL** 态 Procedure（`refund_id` = 该 **procedureId**，取消原因必填）→ 同批禁止重复 **procedureId** |
| **主路径步骤（耗材）** | 若勾选含耗材发放：委托 **`DeviceDispenseNurseWorkflowSupport#revertOutpatientNurseDeviceDispenses`**——**PREPARED** → `revertPreparedBatchToPreparation`；**COMPLETED** → 回退前快照批号/库位（用于库存回补）→ `revertCompletedBatchToPreparation`（**保留签发 `lot_number`**）→ 合并 **increaseQuantities** 回补；统一恢复 **PREPARATION**；**DeviceRequest** 不变 |
| **关键分支** | 仅诊疗/药品或仅耗材可单独成功；**不**读取、**不**写入 ChargeItem |
| **失败分支** | 未选任何可取消项 → EX-NUR-209；执行记录不可取消 → EX-NUR-210；发放态不允许取消 → EX-NUR-211；库存回补失败 → EX-NUR-212；发放回退失败 → EX-NUR-213 |
| **查询侧说明** | 执行记录QueryService 刷新后出现 **CANCEL** 行；发放与InventoryItemQueryService 查询按 DeviceDispense / InventoryItem 事件更新 |


---

### IF-OP-STK-01 皮试表单枚举初始化

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 护士打开皮试 Tab 或编辑表单 |
| **主路径步骤** | 装载 **ClinicalStatus**、**ConditionVerificationStatus**、**Criticality**、**Severity** 枚举选项（code + 展示文案） |
| **失败分支** | 枚举装载失败 → EX-OP-STK-101 |
| **查询侧说明** | 纯读；与初版 `init` 同构 |


---

### IF-OP-STK-02 查询就诊皮试列表

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 左侧 **IF-NUR-01** 已点选患者（**encounterId**） |
| **入参意图** | **就诊主键** 必填；**patientId** 可选 Gate（与就诊一致） |
| **主路径步骤** | 校验 **BR-OP-STK-001** → `requireIdByExactName(GeneralSharedConstants.SKIN_TEST_INSPECTION_ACTIVITY_NAME)` 解析皮试检查 `activityId` → 查询当前就诊 **`COMPLETED`** 的皮试检查 `ServiceRequest`（**BR-OP-STK-002**）→ 经 `based_on` 装载皮试药信息与发放意向库房 → 左连接已有 `AllergyIntolerance`（`request_id` = SR.id）→ 只读库存批号候选 → 装配枚举与人员展示名 |
| **关键分支** | 医生已开皮试检查但**未门诊收费** → 空列表（正常，前端可提示「请先完成皮试费结算」）；已有过敏记录行仍展示供修改 |
| **失败分支** | 就诊上下文无效 → EX-OP-STK-102；查询失败 → EX-OP-STK-103 |
| **查询侧说明** | 纯读；**禁止**用 IF-NUR-02 处置三列表代替本接口 |


---

### IF-OP-STK-03 保存皮试临床结果

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 护士在皮试列表行填写批号、临床/验证状态、双签并保存 |
| **入参意图** | **encounterId**、**patientId**、皮试检查 **serviceRequestId**（列表主键）、皮试药 **medicationRequestId**、**medicationId**、批号、记录时间、临床/验证状态、双签人员；更新时带 **allergyRecordId** |
| **主路径步骤** | 校验 **BR-OP-STK-001、004** → 校验皮试检查 SR 仍为 **COMPLETED** 且属本就诊 → **saveSkinTestRecord**（`request_id` = 皮试检查 SR.id）→ **禁止**创建/更新 SR 或 ChargeItem（**BR-OP-STK-005**） |
| **关键分支** | 更新路径：仅 `saveSkinTestRecord`（**BR-OP-STK-006**） |
| **失败分支** | 双签或状态缺失 → EX-OP-STK-201；SR 不可登记（非 COMPLETED 或不属本就诊）→ EX-OP-STK-202；写失败 → EX-OP-STK-203 |
| **查询侧说明** | **AllergyIntoleranceRecordedEvent** 刷新医生站 `skinTestOutcome*` |


---

### IF-OP-STK-04 删除皮试记录

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 护士对误录行点删除 |
| **入参意图** | **allergyRecordId**；**encounterId**（校验） |
| **主路径步骤** | 校验 **BR-OP-STK-001** → **deleteSkinTestRecord** → **不**删除皮试检查 SR、**不**冲销 ChargeItem（**BR-OP-STK-007**） |
| **失败分支** | 记录不存在 → EX-OP-STK-301；删除失败 → EX-OP-STK-303 |
| **查询侧说明** | **AllergyIntoleranceRemovedEvent** 刷新医生站展示 |


---

## 5. 业务异常与用户提示


| 语义代码 | 场景 | 用户提示方向 | 可重试 |
| --- | --- | --- | --- |
| EX-NUR-101 | 队列查询失败 | 缩小条件或稍后重试 | 是 |
| EX-NUR-102 | 处置单查询失败 | 刷新患者或稍后重试 | 是 |
| EX-NUR-103 | 执行记录查询失败 | 稍后重试 | 是 |
| EX-NUR-201 | 未选择执行项目 | 请先勾选待执行项目或耗材 | 是 |
| EX-NUR-202 | 所选项目无已计费收费项 | 指引核对计费状态或联系管理员 | 否 |
| EX-NUR-203 | 执行登记未能落库 | 执行失败，请联系管理员 | 是 |
| EX-NUR-204 | 开关为开且耗材发放未选择耗材房 | 请选择耗材房后重试 | 是 |
| EX-NUR-205 | 耗材库存查询为空或批号无法分配 | 检查库存维护或联系库房 | 否 |
| EX-NUR-206 | 拆分后仍存在无批号发放行 | 库存不足，请检查耗材库存 | 否 |
| EX-NUR-207 | 命中停供库存或汇总数量不足 | **【耗材名】批号xxx可用库存不足，无法执行发放**（须点名；与 `InventoryItemQueryService#assertSufficientForDeviceDispenseAtLocationLot` 一致） | 否 |
| EX-NUR-208 | 发放结果批量保存失败 | 请联系管理员 | 是 |
| EX-NUR-301 | 未选择患者即加载列表 | 请先选择患者 | 是 |
| EX-NUR-209 | 未选择可取消项 | 请先勾选可取消的执行记录或已执行耗材 | 是 |
| EX-NUR-210 | 执行记录不可取消（非已完成、已对冲、非本就诊等） | 所选执行记录不可取消 | 否 |
| EX-NUR-211 | 耗材发放态不允许取消 | 仅已配药或已发药的发放可取消执行 | 否 |
| EX-NUR-212 | 库存回补失败 | 库存回补失败，请核对批号与库房 | 否 |
| EX-NUR-213 | 发放回退失败 | 取消执行失败，请联系管理员 | 是 |
| EX-OP-STK-101 | 皮试枚举初始化失败 | 表单选项加载失败，请刷新 | 是 |
| EX-OP-STK-102 | 就诊上下文无效 | 请先选择患者或刷新列表 | 是 |
| EX-OP-STK-103 | 皮试列表查询失败 | 加载失败，请稍后重试 | 是 |
| EX-OP-STK-201 | 双签或必填状态缺失 | 请填写皮试结果并选择执行护士与核对护士 | 是 |
| EX-OP-STK-202 | 皮试检查不可登记 | 皮试费未结算或医嘱不可登记，请刷新 | 否 |
| EX-OP-STK-203 | 保存落库失败 | 保存失败，请稍后重试 | 是 |
| EX-OP-STK-301 | 删除目标不存在 | 皮试记录不存在，请刷新 | 否 |
| EX-OP-STK-303 | 删除失败 | 删除失败，请稍后重试 | 是 |


---

## 6. 事件语义与消费方

> 事件英文名与载荷以各 DM §5 为准。


| 事件语义（DM 英文名以 DM 为准） | 触发相位（IF） | 典型消费方 |
| --- | --- | --- |
| 执行记录已创建 / 执行记录状态已变更（Procedure 相关） | IF-NUR-04 | 医嘱执行单QueryService；医嘱全景投影 |
| 执行记录已取消（ProcedureRecordCancelledEvent 等） | IF-NUR-05 | 医嘱执行单QueryService；医嘱全景投影 |
| 耗材发放状态已推进（DeviceDispense 相关，含 PREPARED/COMPLETED） | IF-NUR-04 | 供应链与InventoryItemQueryService 查询；库房快照类QueryService |
| 耗材发放已回退待配药（DeviceDispense 回退类语义） | IF-NUR-05 | 供应链与InventoryItemQueryService 查询；医嘱信息发放态展示 |
| 库存数量已调整（InventoryItem 相关） | IF-NUR-04 / IF-NUR-05 | 库存快照/流水QueryService |
| `AllergyIntoleranceRecordedEvent` | IF-OP-STK-03 | 门诊医生站医嘱列表皮试结果；过敏史 |
| `AllergyIntoleranceRemovedEvent` | IF-OP-STK-04 | 同上 |


---

## 7. 非功能性需求


| 类别 | 要求 |
| --- | --- |
| **性能（经验目标）** | IF-NUR-01～03 / **03B** ≤1000ms；IF-NUR-04 / IF-NUR-05 ≤3000ms（含库存拆分/回补路径） |
| **幂等** | 同一请求在执行次数已满后重复提交不得新增完成记录；净次数为 0 时重复取消不得新增 CANCEL 记录；耗材发放对非准备中行不得重复扣库存；对已回退准备中的发放行不得重复回补 |
| **并发** | 多名护士对同一待发耗材并发执行时，以后写者校验为准，返回可理解的冲突或库存变更提示；对已发放耗材并发取消时以后写者校验为准 |
| **一致性** | 队列就诊标识与处置单、执行写入须引用同一就诊主键 |
| **审计** | 执行记录须可追溯执行护士；耗材发放须可追溯批号、库房、发放时间 |


---

## 8. 测试要点（业务视角）


| 类别 | 要点 |
| --- | --- |
| **正向** | BR-NUR-001 队列准入与日期区间；**BR-NUR-017** 待发耗材行内提醒与「仅待发耗材」分页前过滤；IF-NUR-02 三列表与计费聚合；BR-NUR-006/014：开关 **开** 时传入耗材房则**就地只**改 `location_id`（禁止改批号/FIFO/软删重建发放 id），未传回落医生签发库房与批号，**关** 时不要求；IF-NUR-04 开关 **关**→PREPARED 不扣库；开关 **开**→COMPLETED+扣库；IF-NUR-05 对称回退 PREPARATION；**DeviceRequest** 状态执行/取消均不变（指定房可回写 `perform_location`） |
| **异常** | 未勾选执行 EX-NUR-201；未计费 EX-NUR-202；开关 **开** 缺库房 EX-NUR-204；取消无可选项 EX-NUR-209 |
| **规则** | BR-NUR-001～018；BR-NUR-011/012/013 取消与净剩列表；**BR-NUR-017** 待发耗材提醒与筛选；**BR-NUR-018** 耗材侧栏排除退费 |
| **边界** | IF-NUR-05 **不**触发收费明细回滚 |
| **并发** | 双开窗口对同一待发耗材同时执行；对已发放耗材同时取消 |
| **皮试（目标态）** | 未收费不出现皮试行；保存不写费用；删除不冲销；与 **AS-IP-NUR-011** 住院补费路径互斥语义 |


### 8.1 门诊皮试测试要点（目标态）

| 类别 | 要点 |
| --- | --- |
| **列表门控** | 皮试检查 SR **COMPLETED** 才进列表；未收费为空列表 |
| **查询主表** | 以皮试检查 SR 为主，经 `based_on` 关联皮试药；`AllergyIntolerance.request_id` = SR.id |
| **保存** | 双签必填；**不**新增 SR/Charge；医生站回显更新 |
| **删除** | 可删过敏记录；皮试检查 SR 与已结算费用不变 |
| **患者列表** | 与 IF-NUR-01 共用；皮试药标记可使就诊进队列 |
| **对照** | 住院路径见 **AS-IP-NUR-011**（VERIFIED 皮试药 + 护士补费） |


---

## 9. 关联文档与变更历史

### 9.1 关联文档


| 文档 |
| --- |
| `docs/领域/DomainModel/DM-ADM-002-Encounter-就诊.md` |
| `docs/领域/DomainModel/DM-WKF-004-ServiceRequest-服务申请.md` |
| `docs/领域/DomainModel/DM-MED-003-MedicationRequest-药品申请.md` |
| `docs/领域/DomainModel/DM-WKF-003-DeviceRequest-耗材请求.md` |
| `docs/领域/DomainModel/DM-WKF-007-DeviceDispense-耗材发放.md` |
| `docs/领域/DomainModel/DM-WKF-008-InventoryItem-库存项.md` |
| `docs/领域/DomainModel/DM-ADM-011-ChargeItem-收费明细.md` |
| `docs/领域/DomainModel/DM-CLN-004-Procedure-执行记录.md` |
| `docs/领域/DomainModel/DM-CLN-001-AllergyIntolerance-过敏与不耐受.md` |
| `DM §6.2（DM-ADM-002 §6.2 EncounterQueryService（OutpatientEncounterContextQueryModel）` |
| `DM §6.2（ServiceRequestQueryService / MedicationRequestQueryService 等（DM §6.2）` |
| `DM §6.2（DM-CLN-004 §6.2 ProcedureQueryService / OrderExecutionSheetLoader` |
| `DM §6.2（DM §6.2 各 Request QueryService` |
| `DM §6.2（DM-WKF-008 §6.2 InventoryItemQueryService` |
| `AS-IP-NUR-011-住院护士站皮试管理.md` |
| `AS-OP-FIN-002-门诊收费门诊收费.md`（皮试操作费结算 → SR `COMPLETED`） |
| `AS-OP-NUR-001-门诊护士站-SDD.md`（总则） |
| `AS-OP-NUR-001-PAT-患者队列-SDD.md` / `AS-OP-NUR-001-TRT-处置-SDD.md` / `AS-OP-NUR-001-STK-皮试-SDD.md` |
| `DM-WKF-001-ActivityDefinition-诊疗项目定义.md` |


### 9.2 变更历史


| 版本 | 日期 | 变更摘要 |
| --- | --- | --- |
| v1.9.18 | 2026-10-12 | **BR-NUR-002**：TECH 检验例外排除合管派生「采血费」（类目 22 仅计费、不进医技列表/采血贴）；代码先行回写 |
| v1.9.17 | 2026-10-09 | **BR-NUR-006**：护士选耗材房校验库存后改发放行并回写请求 `location_id`+`perform_location`（与发放一致）；未选走医生默认发材房 |
| v1.9.16 | 2026-10-09 | **BR-NUR-002**：耗材有效库位=请求库位空则回落主发放库位（修请求未写 location 仍进护士站）；TECH 轴仍排除 |
| v1.9.15 | 2026-10-09 | **BR-NUR-002**：TECH 检验仅供采血贴医技列表、不进待执行；耗材可见性按挂靠科室及祖先 TECH 轴排除（对齐可见性 DD §3 / `DeviceSupplyTechVisibilitySupport`） |
| v1.9.14 | 2026-09-14 | **BR-NUR-018**：退费中/已退费耗材侧栏**仍展示**「待退费/已退费」且不可勾选（对齐药品）；待发红点仍不含退费态 |
| v1.9.13 | 2026-09-14 | **BR-NUR-017/018**：待发/侧栏改为须 **PLANNED/SETTLED** 有效计费；IF-NUR-04 执行前剔除已退费耗材发放；对齐退费申请未发路径全量作废发放（**AS-OP-DOR-004 BR-DRA-006**） |
| v1.9.12 | 2026-08-24 | **IF-NUR-02 / BR-NUR-002**：耗材侧栏回填签发 **`lotNumber`** 与账单首条 **`unitPrice`**；**BR-NUR-006 / EX-NUR-207**：库存不足提示点名耗材与批号；耗材待发预占不过期、药品待发预占 24h 见 **AS-OP-DOR-003 · BR-ORD-011** / **DM-WKF-007 · BR-DD-QRY-003** |
| v1.9.11 | 2026-08-22 | **BR-NUR-006** 交叉引用上游 **BR-ORD-033** / **BR-OPC-023**（签发挂费与收银先锁发放批号，护士执行仍作末道门禁） |
| v1.9.10 | 2026-08-21 | **BR-NUR-018**：耗材侧栏排除收费退费态与退费作废发放；**BR-NUR-017** 待发判定同步不含 `DECLINED` 等 |
| v1.9.11 | 2026-09-19 | **BR-NUR-001/002 / IF-NUR-01/02**：两边都有活时患者两边可见、点开只看本站行；明细同轴过滤 |
| v1.9.10 | 2026-09-19 | **BR-NUR-001 / IF-NUR-01**：患者列表与中区诊疗/右侧耗材同一可见性轴（非 TECH / 发放耗材房上级科室；见 DD）；医技站上线同发 |
| v1.9.9 | 2026-08-21 | **BR-NUR-012 / BR-DD-009**：取消执行回退 **保留**医生签发批号（不置空、不重跑 FIFO）；对齐 **DM-WKF-007 v1.2.12** |
| v1.9.8 | 2026-08-19 | **IF-NUR-01 / BR-NUR-001 / BR-NUR-017**：队列日期改为**就诊开始时间区间**（工作台默认当天起止）；行内回填待发耗材提醒（对齐处置单「待发放」）；可选「仅待发耗材」在分页前收窄；选中卡片不得掩盖提醒 |
| v1.9.7 | 2026-06-25 | **耗材发放**：取消执行门控与 **DM-WKF-007 v1.2.4** 对齐——已发路径仅 `COMPLETED`（移除分次发放语义） |
| v1.9.6 | 2026-06-25 | **BR-NUR-016**：申请单行回显部位/样本明细与行级备注；前端 extension 区展示 |
| v1.9.5 | 2026-06-25 | **BR-NUR-015** 展示调整：申请单号改为同组首行医嘱内容内联标签（取消独立空列） |
| --- | --- | --- |
| v1.9.3 | 2026-06-20 | **support 落点对齐**：`DeviceDispenseNurseWorkflowsupport 主实现归 **`module.orchestration.nursestation`**（门诊/住院/划价/执行 AppService 注入，**BR-NUR-014** 行为不变）；详见 **AS-OP-NUR-001-SDD** / **01-架构/模块化开发规范** |（已迁至 application.shared.nursestation-device-dispense）
| v1.9.2 | 2026-06-16 | **IF-NUR-02**：药品列表回显规格、单次剂量/总量、用法/频次/天数（`NurseStationTreatmentLineDto` 增补字段；步骤 **8b** 术语预热，对齐 IF-DOR-10 **BR-ORD-022**）；详见 **AS-OP-NUR-001-SDD v1.3.2** |
| v1.9.1 | 2026-06-10 | §2.1/§3 与实现对齐：皮试/STK 已实现表述；关联 **PAT/TRT/STK-SDD** |
| v1.9.0 | 2026-06-10 | **子域拆包**：`patient` / `treatment` / `skintest` 三包三 AppService；HTTP 前缀三分；SDD 分拆 PAT/TRT/STK |
| v1.8.2 | 2026-06-10 | **IF-OP-STK-01～04 后端已实现**（`OutpatientNurseStationSkinTestAppService`）；§1.4 皮试 UI 仍为未接入 |
| v1.8.1 | 2026-06-10 | **GAP-OP-STK-001 已关闭**：`GeneralSharedConstants.SKIN_TEST_INSPECTION_ACTIVITY_NAME = "皮试检查"` + `#requireIdByExactName`（与 **GAP-IP-STK-001** 同源） |
| v1.8 | 2026-06-10 | 新增 **§1.7、§2.1、§4（IF-OP-STK-01～04）**：门诊皮试目标态契约（共用 IF-NUR-01 患者列表、独立 Tab）；明确列表以皮试检查 SR **`COMPLETED`** 为门控、护士保存不写费用；与 **AS-IP-NUR-011** 对照；登记 **GAP-OP-STK-001** |
| v1.7 | 2026-06-08 | **BR-NUR-014** 耗材是否自动扣库开关；IF-NUR-04/05 耗材路径改为 **`DeviceDispenseNurseWorkflowsupport**；**不**调用 `executeDeviceOrder`/`revokeDeviceOrderExecution`；取消对称回退 **PREPARATION**；新增 **`revertPreparedBatchToPreparation`**（**DM-WKF-007 §6.1.24**） |
| v1.6.3 | 2026-05-28 | 读模型废止后对齐 zxplat 实现（EncounterContextFacade / querysupport Loader）。 |（已迁至 EncounterContextReader / application.shared.encounter）
| v1.6.2 | 2026-05-18 | **IF-NUR-02**：耗材发放 id 映射改为域层 **一次批量 IN 查询**（对齐 A.9.1）；目录/术语仍走 `module.common` 共用装配（行为与 v1.6.1 一致） |
| v1.6.1 | 2026-05-18 | **IF-NUR-02**：明确目录 `itemName`、药品 `medCategoryCode`/`medCategoryCodeText` 来源；Request 诊疗类目筛选与 Response 定义类目回显分离 |
| v1.6 | 2026-05-18 | 新增 **IF-NUR-03B** 净剩可取消执行记录列表（**BR-NUR-013**）；**IF-NUR-05** 诊疗/药品取消改为入参 **procedureId**（`refund_id` 指向被对冲 **COMPLETED**，**BR-PRO-005**）；**IF-NUR-03** 明确为全量分页；术语与 **EX-NUR-210** 口径更新 |
| v1.5.1 | 2026-05-18 | §3：移除误登 **GAP-NUR-C01～C03**（非护士站契约缺口）；Gap 口径说明 |
| v1.5 | 2026-05-18 | 新增 **IF-NUR-05** 批量取消执行；**BR-NUR-011～012**；明确**不**含收费回滚；异常 **EX-NUR-209～213**；事件与测试要点增量 |
| v1.4 | 2026-05-10 | §3：Gap 表清空为「无待跟踪缺口」，历史关闭项移至括号说明 |
| v1.3 | 2026-05-10 | §3：**GAP-NUR-001/002 已关闭**——EncounterContextFacade#pageNurseStationOutpatientQueue §3.5、OrderExecutionSheetLoader §3.4 / **BR-ORD-005**；BR-NUR-002 / §1.3 首条收费状态固定为 **`adm_charge_item.id` 升序** |（已迁至 EncounterContextReader / application.shared.encounter）
| v1.2 | 2026-05-10 | IF-NUR-02：encounterId 详情、三列表同构、诊疗类目仅过滤诊疗列表；账单随发放多行聚合计价与首条收费状态；BR-NUR-009～010；诊疗列表 Procedure 次数口径对齐初版门诊处置查询，药品/耗材列表仅数量维度；BR-NUR-005～006 对齐初版执行/库房逻辑；登记 GAP-NUR-002 |
| v1.9.4 | 2026-08-01 | **IF-NUR-01 / BR-NUR-001**：患者队列支持**可选开立科室**筛选（默认全部；传入则等值匹配就诊挂号/开立科室上下文） |
| v1.1 | 2026-05-10 | IF-NUR-01 / BR-NUR-001：患者队列改为请求驱动准入（`COMPLETED`、退费关联为空、诊疗或耗材或输液标识或皮试标记）；§3 登记 GAP-NUR-001（OutpatientEncounterContextQueryModel QueryService待补场景） |
| v1.0 | 2026-05-09 | 初版：按门诊护士站 UI 抽取 IF-NUR-01～04；处置执行编排对齐初版门诊处置应用服务；皮试/取消执行/专用队列等无页面入口能力列入 §1.4 |


---

**文档负责人**：（待填） · **评审**：（待填）
