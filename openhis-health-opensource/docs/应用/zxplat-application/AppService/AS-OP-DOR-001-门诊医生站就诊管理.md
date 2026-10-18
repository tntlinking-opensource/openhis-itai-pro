# 门诊医生站 · 就诊管理 · 应用服务设计（AS）

> **文档职责**：说明门诊医生站「就诊管理」模块的**接口契约（IF）**、业务语义、涉及聚合与 QueryService、规则（BR）、编排意图；作为上游契约供详细设计与实现对齐。
>
> **本文必须写**：IF 编号；用户动作与读写属性；关键校验与状态变化；Gap；异常口径；事件语义；非功能约束；业务向测试要点。
>
> **本文禁止写**：具体类名/应用方法名；Request/Response 字段明细；包路径；Mapper；伪代码；写库顺序；QueryService 字段组装规则。
>
> **页面边界**：门诊医生站左侧就诊队列与右侧主工作区——切换在诊/待诊、按日期与关键字刷新列表、点选当前患者；工具栏对当前就诊执行**完诊、取消接诊、暂离**（同页「**办理住院**」见 **AS-OP-DOR-005**；「**退费申请**」见 **AS-OP-DOR-004**；「处方单」等见 §1.4）。
>
> **覆盖声明**：完整契约以 **§2 IF-DOR-01～04、IF-DOR-06** 为准；**办理住院**契约见 **AS-OP-DOR-005**。
>
> **编码**：AS-OP-DOR-001 · **版本**：v1.19 · **日期**：2026-08-20

---

## 1. 场景概述

### 1.1 目标与边界


| 维度 | 说明 |
| --- | --- |
| **在内** | 本科室门诊就诊列表查询（**`encounterStatus` 可缺省**：缺省时默认**已挂号/在诊/暂离/诊毕**四态并集；传入时按单队列过滤）；医生接诊；就诊暂离；门诊完诊；**取消接诊**（医生同意退号） |
| **在外** | 病历与诊断表单细节、医嘱/处方开立、**退费申请（AS-OP-DOR-004）**与票据、报告调阅；**办理住院 / 住院申请**见 **AS-OP-DOR-005**；住院登记见 **AS-IP-FIN-001**；窗口退号见 **AS-OP-FIN-001** |
| **涉及域** | Encounter（门诊状态与参与者）；住院 Encounter 创建与诊断关联；Clinical 侧诊断事实（住院申请场景）；Organization（科室过滤） |
| **编排权威** | 以本文 **§2、§4** 为准 |


### 1.2 角色与关键动作


| 角色 | 关键动作 |
| --- | --- |
| **门诊医生** | 查看在诊/待诊等列表 → 点选患者 →（待接诊场景下）发起接诊 → 诊疗过程中可暂离 → 完诊；若同意退号则**取消接诊**后由窗口退号 |
| **系统** | 按登录科室与医生过滤列表；维护接诊医生参与者与时间；推进就诊与患者主题状态 |


### 1.3 术语与口径约定


| 术语 | 口径 |
| --- | --- |
| **在诊列表** | **显式**选择 **`IN_PROGRESS` 单队列**时：就诊状态为执行中；**仅活动接诊医生**（`ADMITTER`）可见（无论挂号是否指定医生）。列表按**接诊时间降序**（后接诊在上）。缺省多状态并集中的在诊/暂离行同此口径 |
| **待诊列表** | **已正式挂号、待接诊**：IF-DOR-01 请求传 **`EncounterStatus.REGISTERED`**（**不用** **`PLANNED` 待挂号** 充当待诊）；**挂号时指定了医生**则仅该医生可见，**挂到科室未指定医生**则本科室医生均可见；排序按就诊开始时间（挂号时间）**升序**（先挂号先看诊）；**`staffPractitionerId` 须为当前登录用户关联的医师主键**（与 `userInfo.practitionerId` 一致，禁用 number 传输雪花 ID） |
| **诊毕列表** | **`DISCHARGED`**：本科室医生均可见（不按参与者收窄） |
| **接诊** | 将待接诊就诊推入执行中：写入接诊时刻；将既有接诊医生参与者收口为已完成并新增当前医生为活动接诊者 |
| **取消接诊** | 医生同意退号：将在诊/暂离/完诊回退为已挂号待诊并收口活动接诊医生；**与账单无关**；**须本诊病历、诊断与医生开立医嘱均已删除**（BR-DOR-011）；之后窗口方可 IF-REG-09/10 退号 |
| **办理住院** | 工具栏动作；业务契约见 **AS-OP-DOR-005**（本文不展开） |
| **QueryService 能力** | 列表以 **EncounterContextReader** · **OutpatientEncounterContextQueryModel** 为主；本文不写字段映射细节 |
| **DM 路径** | `docs/领域/DomainModel/` 下对应文件 |


### 1.4 本期范围与非目标


| 类别 | 内容 |
| --- | --- |
| **本期（契约）** | IF-DOR-01～04、**IF-DOR-06**：就诊列表、接诊、暂离、完诊、取消接诊 |
| **本期（UI 接通）** | 列表筛选、刷新、暂离、完诊、**取消接诊**等；**办理住院**见 **AS-OP-DOR-005** |
| **非目标（同页其他模块）** | 工具栏「**退费申请**」（**AS-OP-DOR-004**）、「**办理住院**」（**AS-OP-DOR-005**）、「处方单」、病历/诊断/医嘱等 Tab 业务——不在本文 IF 表收敛 |
| **非目标（无页面入口的能力）** | 医生站主数据占位初始化、接诊统计查询等——初版接口存在但本页未配置操作入口，不纳入 §2 |


### 1.5 领域清单（聚合 + 职责 + DM 路径）


| 聚合（域） | 在本场景中的职责（摘要） | DM 路径 |
| --- | --- | --- |
| **Encounter** | 门诊列表口径；接诊/暂离/完诊/取消接诊状态与参与者 | `DM-ADM-002-Encounter-就诊.md` |
| **Organization** | 列表限定当前登录科室 | `DM-ADM-004-Organization-机构科室.md` |
| **Practitioner** | 接诊医生参与者解析；**接诊写路径**须解析医师展示名并随命令/事件落快照，供投影与QueryService `doctorId`/`doctorName` 一致消费 | `DM-ADM-005-Practitioner-医务人员.md` |


### 1.6 业务规则（BR-*）


| 规则 ID | 描述 | 绑定 IF |
| --- | --- | --- |
| **BR-DOR-001** | IF-DOR-01：**始终**限定当前登录机构科室；**须**携带当前登录医师主键（`staffPractitionerId`），读侧按 **BR-DOR-009** 做可见性过滤 | IF-DOR-01 |
| **BR-DOR-002** | IF-DOR-01：**`encounterStatus` 可空**——空时读侧为 **`REGISTERED`/`IN_PROGRESS`/`ON_HOLD`/`DISCHARGED`** 多值 IN（不含待挂号 **`PLANNED`**、不含 **`TRIAGED`** 等，除非前端显式单选该状态）；非空时按单状态过滤。**待诊**典型单选 **`REGISTERED`**（**不用** **`PLANNED`** 充当待诊）。**排序**：单选/并集内**待诊**（`REGISTERED`/`TRIAGED`，及约定的 `PLANNED` 单查）按**开始时间升序**（先挂号先看诊）；单选/并集内**在诊/暂离**（`IN_PROGRESS`/`ON_HOLD`）按**接诊时间降序**（后接诊在上）；**缺省并集**按状态桶 + 桶内上述业务键排序（前端按 Tab 过滤后相对序仍成立）；诊毕等其余按开始时间降序 | IF-DOR-01 |
| **BR-DOR-003** | 接诊时须将同就诊下既有接诊医生参与者收口为已完成，再新增当前登录医生为活动接诊者 | IF-DOR-02 |
| **BR-DOR-004** | 接诊成功后就诊进入执行中，患者主题进入**接受诊疗**语义 | IF-DOR-02 |
| **BR-DOR-005** | 暂离成功后就诊进入暂停态，患者主题进入**暂离**语义 | IF-DOR-03 |
| **BR-DOR-006** | 完诊成功后就诊进入诊毕态，患者主题进入**离院**语义 | IF-DOR-04 |
| **BR-DOR-011** | 取消接诊成功后就诊回退 **`REGISTERED`**；收口活动 `ADMITTER`；语义为**医生同意退号**（**与账单无关**）。仅 `IN_PROGRESS`/`ON_HOLD`/`DISCHARGED` 可取消接诊。**前置**：本诊须已删净病历（EMR 文书）、就诊诊断（`adm_encounter_diagnosis` 未软删行）及医生开立医嘱（药/耗/诊疗，`generate_source=doctor-prescription` 未软删行）；未清空则拒绝（**EX-DOR-105**）。本接口**不**自动删临床数据 | IF-DOR-06 |
| **BR-DOR-009** | IF-DOR-01 列表可见性（在**本科室**前提下）：**以是否存在活动 `REGISTRATION_DOCTOR` 区分待诊「挂到医生 / 挂到科室」**——**`REGISTERED`/`TRIAGED`（待诊）**：无活动 `REGISTRATION_DOCTOR` 且无活动 `ADMITTER` 时科室全员可见；存在活动 `REGISTRATION_DOCTOR` 时**仅匹配医师**可见；**读侧兼容历史数据**：待诊态仅有活动 `ADMITTER`（开立阶段误写）时，按 `ADMITTER` 匹配当前登录医师；**`IN_PROGRESS`/`ON_HOLD`（接诊后）**——**无论挂号是否指定医生，仅活动 `ADMITTER` 可见**；**`DISCHARGED`（诊毕）**——科室全员可见。**开立门诊就诊**指定医师时须写 `REGISTRATION_DOCTOR`（见 `DM-ADM-002` §6.1.1），**禁止**在开立阶段写 `ADMITTER`。挂号医生统一可选（**BR-REG-001**）；可见性待诊按是否已写 `REGISTRATION_DOCTOR`、接诊后按 `ADMITTER` 判定 | IF-DOR-01 |
| **BR-DOR-010** | IF-DOR-01 列表行 **`contractDisplay`（费用性质展示）**：有默认**医保账户**且其 `contract_no` 有效时优先展示该合同名称；否则展示登记**个人现金账户**合同名称；**禁止**前端写死「自费」。医嘱开立 init 费用性质下拉默认项与本口径一致（优先合同排首位，选自费才选自费合同） | IF-DOR-01；**AS-OP-DOR-003** 医嘱 init |

---

## 2. 页面与接口总览（契约概览）


| IF 编号 | 用户动作 | 接口语义（业务） | 读/写属性 | 关键规则（BR） | 关键状态变化（自然语言） | 涉及领域 | 查询服务 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| IF-DOR-01 | 可选切换单队列（**`encounterStatus`**）、或**不传**用默认四态并集；关键字、日期、刷新 | 分页查询本科室就诊患者列表（**BR-DOR-009** 可见性），行内返回 **`contractDisplay`（费用性质展示，BR-DOR-010）**；医嘱费用性质下拉由医嘱 init 按就诊加载（列表页 `feeNatureOptions` 可为空） | 读 | BR-DOR-001 / BR-DOR-002 / **BR-DOR-009** / **BR-DOR-010** | 无写状态变化 | Encounter / Organization / Practitioner / **Account / Contract（费用性质）** | EncounterContextReader · OutpatientEncounterContextQueryModel |
| IF-DOR-02 | 待接诊队列中发起接诊（交互以产品确认为准） | 医生接诊：写入接诊时间并切换接诊医生参与者 | 写 | BR-DOR-003 / BR-DOR-004 | **已挂号/已分诊**（`REGISTERED`/`TRIAGED`）→ 执行中；患者主题 → 接受诊疗 | Encounter | OutpatientEncounterContextQueryModel（写后刷新） |
| IF-DOR-03 | 点击「暂离」 | 就诊暂离 | 写 | BR-DOR-005 | 执行中/其他允许态 → 暂停；患者主题 → 暂离 | Encounter | OutpatientEncounterContextQueryModel（写后刷新） |
| IF-DOR-04 | 点击「完诊」 | 门诊完诊 | 写 | BR-DOR-006 | 执行中或暂停 → 诊毕；患者主题 → 离院 | Encounter | OutpatientEncounterContextQueryModel（写后刷新） |
| IF-DOR-06 | 点击「取消接诊」 | 取消接诊（医生同意退号） | 写 | BR-DOR-011 | 在诊/暂离/完诊 → 已挂号待诊 | Encounter | OutpatientEncounterContextQueryModel（写后刷新） |


---

## 3. 缺口跟踪（Gap）

**无待跟踪缺口。**

---

## 4. 接口编排说明书（业务步骤）

### IF-DOR-01 查询就诊患者列表


| 维度 | 说明 |
| --- | --- |
| **触发前提** | 医生已登录；具备科室上下文；**当前登录用户已关联医师主键**（`staffPractitionerId`←`userInfo.practitionerId`） |
| **主路径步骤** | 校验 **`staffPractitionerId` 必填** → 若 **`encounterStatus` 缺失**则映射为**已挂号/在诊/暂离/诊毕**并集；若传入单状态则按该状态过滤 → 读侧按 **BR-DOR-009**（待诊按挂号医生/兼容历史 ADMITTER；在诊/暂离仅接诊医生；诊毕科室共享）与登录科室、日期、**关键字**分页查询（QueryService **Q-OPI-001**：**患者姓名、拼音检索码、五笔检索码、就诊业务号** 之 **OR** 模糊匹配）→ 对**本页就诊**一次性批量加载**收费明细**并在内存汇总**费用总额、已支付、已退款**（口径与门诊挂号列表「按就诊批量费项事实汇总」一致；**不经支付流水**）→ 按**同一科室组织上下文**加载**费用性质（合同）主数据全量**并作为**下拉选项**一并返回（无主数据配置时为空列表，**不阻断**患者列表）→ 列表行 **`genderEnum`** 直透QueryService **`AdministrativeGender` 持久化 code**（与患者主档一致）；年龄等展示字段同QueryService |
| **关键分支** | **`staffPractitionerId` 必填且须与登录医师一致**；单队列待诊典型为 **`REGISTERED`**（须 **`completeOutpatientRegistration` 后**，不含 `PLANNED`）；缺省并集时 BR-DOR-009 按各行 `status_enum` 分别判定可见性 |
| **失败分支** | 查询不可用 → EX-DOR-101 |
| **查询侧说明** | 纯展示；目标QueryService为门诊信息列表场景 |


---

### IF-DOR-02 医生接诊


| 维度 | 说明 |
| --- | --- |
| **触发前提** | 已选定待接诊就诊；医生身份有效 |
| **主路径步骤** | 写前按请求中的接诊医师主键加载主数据并取**展示姓名快照** → 更新就诊：写入接诊时刻、就诊状态为执行中、患者主题为接受诊疗 → 将同就诊下既有接诊医生参与者标记为已完成 → 新增当前医生为活动接诊者；**事件载荷**须携带接诊医师 ID 与上述姓名快照，供投影与列表QueryService统一使用 **`doctorId`/`doctorName` 语义**（禁止以 `practitioner*` 等异名键替代） |
| **关键分支** | 就诊类别须为门诊（非门诊须拒绝，避免误用本工作站链路） |
| **失败分支** | 医师主键无效或主数据缺失 → EX-DOR-104；更新影响行数为 0 → EX-DOR-102 |
| **查询侧说明** | EncounterQueryService（OutpatientEncounterContextQueryModel）及挂号侧列表等消费方，依接诊事件物化接诊时刻、状态与**医生列** |


---

### IF-DOR-03 患者暂离


| 维度 | 说明 |
| --- | --- |
| **触发前提** | 已选定当前就诊 |
| **主路径步骤** | 更新就诊为暂停态，患者主题为暂离 |
| **失败分支** | 更新影响行数为 0 → EX-DOR-102 |
| **查询侧说明** | EncounterQueryService（OutpatientEncounterContextQueryModel）状态刷新 |


---

### IF-DOR-04 门诊完诊


| 维度 | 说明 |
| --- | --- |
| **触发前提** | 已选定当前就诊 |
| **主路径步骤** | 更新就诊为诊毕态，患者主题为离院 |
| **失败分支** | 更新影响行数为 0 → EX-DOR-102 |
| **查询侧说明** | EncounterQueryService（OutpatientEncounterContextQueryModel）终态刷新 |


---

### IF-DOR-06 取消接诊


| 维度 | 说明 |
| --- | --- |
| **触发前提** | 已选定在诊、暂离或完诊就诊；医生同意退号；**本诊病历、诊断与医嘱已全部删除** |
| **主路径步骤** | 校验临床痕迹已空 → 将就诊回退为已挂号；清空接诊/完诊时刻；收口活动接诊医生参与者 |
| **关键分支** | 就诊类别须为门诊；与账单无关；**有未删病历/诊断/医嘱则拒绝（EX-DOR-105）** |
| **失败分支** | 当前非在诊/暂离/完诊 → 拒绝；临床痕迹未清 → EX-DOR-105；更新影响行数为 0 → EX-DOR-102 |
| **查询侧说明** | 写后刷新医生站队列（回到待诊） |


---

## 5. 业务异常与用户提示


| 语义代码 | 场景 | 用户提示方向 | 可重试 |
| --- | --- | --- | --- |
| EX-DOR-101 | 列表查询失败 | 缩小条件或稍后重试 | 是 |
| EX-DOR-102 | 接诊/暂离/完诊/取消接诊未更新任何记录 | 刷新列表后确认就诊是否仍有效 | 是 |
| EX-DOR-104 | 接诊医师不存在或已失效 | 确认医师主数据或重新选择接诊人 | 否 |
| EX-DOR-105 | 取消接诊时本诊仍有病历/诊断/医嘱 | 先删除本诊全部病历、诊断与医嘱后再取消接诊 | 是 |
| EX-DOR-301 | 未选择患者即操作 | 请先选择患者 | 是 |


---

## 6. 事件语义与消费方

> 事件英文名与载荷以各 DM §5 为准。


| 事件语义（DM 英文名以 DM 为准） | 触发相位（IF） | 典型消费方 |
| --- | --- | --- |
| 门诊接诊已开始 / OutpatientReceptionStartedEvent | IF-DOR-02 | 叫号、大屏、EncounterQueryService（OutpatientEncounterContextQueryModel）；**挂号列表等**依赖本事件内 **`doctorId`/`doctorName`** 物化医生列（与DM §6.2 查询契约一致） |
| 门诊就诊已暂停 / OutpatientEncounterOnHoldEvent | IF-DOR-03 | 队列、EncounterQueryService（OutpatientEncounterContextQueryModel） |
| 门诊就诊已完成 / OutpatientEncounterCompletedEvent | IF-DOR-04 | 收费、统计、EncounterQueryService（OutpatientEncounterContextQueryModel） |
| 门诊接诊已取消 / OutpatientReceptionCancelledEvent | IF-DOR-06 | 医生站队列、挂号列表等 |

---

## 7. 非功能性需求


| 类别 | 要求 |
| --- | --- |
| **性能（经验目标）** | IF-DOR-01 ≤1000ms；IF-DOR-02～04 ≤800ms |
| **幂等** | IF-DOR-02～04 对终态就诊重复写应拒绝或返回无副作用结果 |
| **并发** | 两医生同时对同一就诊接诊或完诊时，以后写者校验为准，返回可理解冲突 |
| **一致性** | 列表过滤条件与写操作使用同一科室与医生语义；**BR-DOR-009** 保证「看见即可接诊/操作」与队列口径一致 |
| **审计** | 接诊时刻、办理住院创建事实应可追溯至操作者与门诊就诊标识 |


---

## 8. 测试要点（业务视角）


| 类别 | 要点 |
| --- | --- |
| **正向** | 在诊/待诊等列表与排序；接诊后患者进入在诊列表；暂离/完诊后状态与列表一致；**清空临床痕迹后取消接诊回到待诊且窗口可退号** |
| **异常** | 未选患者；列表为空；写库 0 行；接诊医师主数据缺失（EX-DOR-104）；待诊态取消接诊被拒；**仍有病历/诊断/医嘱时取消接诊被拒（EX-DOR-105）** |
| **规则** | BR-DOR-003 接诊参与者收口；**BR-DOR-011** 取消接诊（含临床痕迹清空门禁） |
| **并发** | 双开窗口对同一就诊先后接诊或完诊 |


---

## 9. 关联文档与变更历史

### 9.1 关联文档


| 文档 |
| --- |
| `docs/领域/DomainModel/DM-ADM-002-Encounter-就诊.md` |
| `docs/领域/DomainModel/DM-ADM-004-Organization-机构科室.md` |
| `docs/领域/DomainModel/DM-ADM-005-Practitioner-医务人员.md` |
| `docs/领域/DomainModel/DM-CLN-003-Condition-诊断记录.md` |
| `DM §6.2（DM-ADM-002 §6.2 EncounterQueryService（OutpatientEncounterContextQueryModel）` |
| `DM §6.2（DM-ADM-002 §6.2 `EncounterQueryService#pageClinicRegister`` |
| `DM §6.2（DM-ADM-002 §6.2 EncounterQueryService` |
| `docs/应用/zxplat-application/AppService/AS-OP-FIN-001-门诊挂号.md |
| `docs/应用/zxplat-application/AppService/AS-OP-DOR-004-门诊医生站申请退费.md |
| `docs/应用/zxplat-application/AppService/AS-OP-DOR-005-门诊医生站住院申请.md |
| `docs/应用/zxplat-application/AppService/AS-IP-FIN-001-住院收费住院登记.md |


### 9.2 变更历史


| 版本 | 日期 | 变更摘要 |
| --- | --- | --- |
| v1.19 | 2026-08-20 | **BR-DOR-011 / IF-DOR-06**：取消接诊前须本诊病历、诊断与医生开立医嘱均已删除（**EX-DOR-105**）；与账单仍无关、不自动删临床数据。详设 **SDD v1.3.10**；对齐 **DM-ADM-002 v1.46**。 |
| v1.18 | 2026-08-06 | 新增 **IF-DOR-06 / BR-DOR-011** 取消接诊（医生同意退号）；对齐 **DM-ADM-002 v1.41**、挂号 **BR-REG-008**（已接诊不可直接退号）。详设 **SDD v1.3.9**。 |
| v1.17 | 2026-08-03 | **BR-DOR-009**：接诊后（`IN_PROGRESS`/`ON_HOLD`）**统一仅活动 `ADMITTER` 可见**（挂到科室不再科室共享）；待诊/诊毕口径不变。对齐挂号 **BR-REG-001** 退役必选开关。详设 **SDD v1.3.8**。 |
| v1.16 | 2026-07-22 | **BR-DOR-009**：挂到科室（无活动 `REGISTRATION_DOCTOR`）时，**接诊后（`IN_PROGRESS`/`ON_HOLD`）仍科室全员可见**；挂到医生时接诊后仍仅 `ADMITTER`。§1.3 在诊列表口径同步。详设 **SDD v1.3.6**。 |
| v1.15 | 2026-07-18 | **BR-DOR-002 / §1.3**：待诊队列改按**开始时间升序**（先挂号先看诊）；在诊/暂离按**接诊时间降序**（后接诊在上）；缺省多状态并集按状态桶 + 桶内业务键排序。**BR-DOR-010**：列表行 **`contractDisplay`** 有医保账户优先医保合同名；医嘱默认费用性质同口径；纠正「列表附带全量 feeNatureOptions」与实现脱节（下拉改由医嘱 init 按就诊加载）。详设 **SDD v1.3.5**。 |
| v1.14 | 2026-06-12 | **BR-DOR-009** 收紧：待诊可见性兼容历史误写 **`ADMITTER`**；明确 **`registerOutpatientEncounter` 须写 `REGISTRATION_DOCTOR`**（对齐 `DM-ADM-002` §6.1.1）；§1.3/§4 补充 `staffPractitionerId` 与登录 `practitionerId` 一致及 **`REGISTERED` 前置（缴费确认）**；详设 **SDD v1.3.4**。 |
| v1.13 | 2026-06-12 | IF-DOR-01：新增 **BR-DOR-009** 门诊医生站列表可见性——待诊按挂号医生（未指定则科室共享）、在诊/暂离按接诊医生、诊毕科室共享；**`staffPractitionerId` 改为必填**；修订 BR-DOR-001 / §1.3 / §4 IF-DOR-01；详设 **SDD v1.3.3**。 |
| v1.12 | 2026-05-28 | 读模型废止后对齐 zxplat 实现（EncounterContextFacade / querysupport Loader）。 |（已迁至 EncounterContextReader / application.shared.encounter）
| v1.11 | 2026-05-18 | 办理住院拆至 **AS-OP-DOR-005**；本文收敛 IF-DOR-01～04。 |
| v1.10 | 2026-05-15 | 页面边界与 §1.4：工具栏「退费申请」收敛至 **AS-OP-DOR-004**；§9.1 增加关联。 |
| v1.9 | 2026-05-04 | IF-DOR-01：列表响应**附带费用性质全量下拉**（原 **AS-OP-DOR-003 IF-DOR-13** 能力迁入，避免医嘱包重复接口）；详设 **SDD v1.3.0**。 |
| v1.8 | 2026-05-03 | IF-DOR-01：列表行性别以 **`AdministrativeGender` 持久化 code** 与QueryService一致直透（**不**做派生整型/文案转换）；详设 **SDD v1.2.9**；QueryService **`OutpatientEncounterContextQueryModel` v3.1**。 |
| v1.7 | 2026-05-02 | IF-DOR-01：列表**三金额**在分页之后按**本页就诊**对**收费明细**做一次批量加载并内存汇总展示；**不**经支付流水。QueryService **`OutpatientEncounterContextQueryModel` v3.0** §2 口径约束；详设 **SDD v1.2.8**。 |
| v1.6 | 2026-05-02 | IF-DOR-01：关键字检索语义与 **`OutpatientEncounterContextQueryModel` v2.9** §3.4 对齐（姓名 / 拼音码 / 五笔码 / 就诊业务号，**OR** 模糊）。详设 **SDD v1.2.7**。 |
| v1.5 | 2026-05-02 | IF-DOR-01：**`encounterStatus` 可选**；未传时默认 **`REGISTERED`/`IN_PROGRESS`/`ON_HOLD`/`DISCHARGED`** 并集，不按接诊医师过滤；传入时按前台单队列，**在诊**仍须接诊医师语义（BR-DOR-001 收紧为显式在诊队列）。BR-DOR-002 / §4 IF-DOR-01 同步；详设 **SDD v1.2.6**；QueryService **OutpatientEncounterContextQueryModel v2.8** §3.4。 |
| v1.4 | 2026-05-02 | IF-DOR-01：列表查询统一以 **`EncounterStatus encounterStatus`** 入参过滤（与QueryService `status_enum` 一致），**删除** `DoctorStationOutpatientQueueType` / `queueType`；待诊典型传 **`REGISTERED`**；详设见 `AS-OP-DOR-001-门诊医生站就诊管理-SDD` v1.2.5。 |
| v1.3 | 2026-05-02 | 候诊列表语义与挂号链路对齐：**仅 `REGISTERED`/`TRIAGED`**，**排除 `PLANNED` 待挂号**；术语「待诊」统一为「候诊」；BR-DOR-002 / IF-DOR-01 / IF-DOR-02 同步；API 曾使用独立队列类型枚举（已由 v1.4 收敛为 `EncounterStatus`）。 |
| v1.2 | 2026-05-02 | IF-DOR-02：接诊前须解析医师展示名并写入事件快照，QueryService 刷新统一 `doctorId`/`doctorName`；新增 EX-DOR-104；§1.5 Practitioner 职责与 §6 消费方补充；§9.1 关联挂号列表 QueryService与就诊生命周期DM §6.2 |
| v1.1 | 2026-05-01 | 办理住院初始态统一为 DM **`IMP + PLANNED`**；关闭 GAP-DOR-001/002；§3 无待跟踪缺口 |
| v1.0 | 2026-05-01 | 初版：按 Skill 骨架建立 IF-DOR-01～05；对齐医生站列表与主页面写能力及住院申请编排 |


---

**文档负责人**：（待填） · **评审**：（待填）
