# 住院护士站 · 患者管理 · 应用服务设计（AS）

> **文档职责**：住院护士站**一站式工作台**的**患者上下文**契约——病区/病房树、在院患者列表、患者详情、患者信息维护；全站右侧 Tab **共用**本 AS，**不**另建患者主数据源。
>
> **页面边界**：工作台**左侧**——① 病区/病房**树**（登录者管理病区）→ ② 按树节点筛选的**患者列表** → ③ 点选后的**患者详情**（信息尽量完整，含入科办理所需展示字段）→ ④ **患者信息维护**（病情、医护、**入院体征**，**不换床**）。**分床/换床**在 **AS-IP-NUR-002**（**首次分床**体征写）；入科 Tab **仅**床位列表与分床写操作。
>
> **编码**：AS-IP-NUR-001 · **版本**：v1.39 · **日期**：2026-10-15

---

## 1. 场景概述

### 1.1 目标与边界


| 维度       | 说明                                                                                                                                                    |
| -------- | ----------------------------------------------------------------------------------------------------------------------------------------------------- |
| **在内**   | **本期** IF-IP-NUR-01～04：病区病房树；在院患者分页列表（富字段）；患者详情（全景，**即**原「入科办理上下文」的读侧能力）；患者信息维护（写，不换床）                                                                |
| **在外**   | 床位列表、分床/换床/互换（**AS-IP-NUR-002**）；医嘱校对（**AS-IP-NUR-003**）、医嘱执行（**AS-IP-NUR-004**）；预交金（**AS-IP-FIN-002**）；登记（**AS-IP-FIN-001**）                                           |
| **涉及域**  | Encounter；Patient；Location（病区/病房/床）；EncounterParticipant；Account；**VitalSign**（**DM-DIA-002**）；MedicationRequest / ServiceRequest（任务计数）；Procedure（净执行记录）；Condition（诊断摘要）；**文书** Tab（**AS-IP-NUR-007/014**） |
| **编排权威** | 以本文 **§2、§4** 为准                                                                                                                                      |


### 1.2 角色与关键动作


| 角色       | 关键动作                                                                 |
| -------- | -------------------------------------------------------------------- |
| **病区护士** | 在树中选病区/病房 → 查看筛选后的患者列表（可按状态切换至**已出院结算**等）→ 点选患者看详情 → 维护病情/医护/体征。**出院召回**在**收银台已结算历史**办理（**AS-IP-FIN-004 IF-IP-RCL-01**）；**未清床**（`COMPLETED`）须至收银台 **`reverseInpatientSettle`**（**IF-IP-RVS-01**） |
| **系统**   | 按登录者**管理病区（硬权限）**加载树；列表/详情批量组装余额与任务计数                                 |


### 1.3 术语与口径约定


| 术语                    | 口径                                                                                                                     |
| --------------------- | ---------------------------------------------------------------------------------------------------------------------- |
| **管理病区**              | 当前登录用户**被授权管理**的病区集合（`PractitionerManagedScopeLoader`：岗位绑定 **WARD** 或绑定 **HOUSE** 时取其上级 **WARD**）；**医院租户硬权限**：未绑定或绑定均已停用时 **empty**，**不回落**机构/全院；宿主租户无绑定时由 `CurrentManagedWardLocationIdsResolver` 内部回落机构/全院（平台运维） |
| **病区病房树**             | **树形**：根为病区（`form_enum=wa`），子为病房（`form_enum=ho`）；**不含床位**节点。父子关系以 `adm_location.bus_no` 点分路径表达（病区 → 病房 → 病床三级，树仅展示前两级） |
| **位置级联**              | **病区 → 病房 → 病床**；子级 `bus_no` 为 `{父级.bus_no}.{子编码}`（对齐 **DM-ADM-003**）；筛选用病房 id 时，患者当前活动床位的 `bus_no` 须以该病房 `bus_no` 为前缀 |
| **列表筛选**              | 在管理病区范围内：可选 **病区 id**（该病区内全部患者）或 **病房 id**（仅该病房下患者）；二者同时传时以**更窄的病房**为准；均未选则在管理病区并集内查                                   |
| **护士站可见状态**           | **默认（在院/未清床）**：`IMP` 且 `status_enum ∈ { REGISTERED, IN_PROGRESS, ON_HOLD, PENDING, COMPLETED }`；排除 `PLANNED`、`CANCELLED`、`DISCHARGED`（**已清床离院**）。**列表状态筛选已废止**（~~BR-IP-NUR-012~~）；召回患者回 `IN_PROGRESS` 后仍在默认列表（未占床，床列「原{房}-{床}（未占床）」） |
| **入科状态筛选（前端本地）**   | 工作台列表下拉 **在科 / 待入科**（**不**走 `nurseStationStatusFilter`）：**在科** = 队列行中排除 `REGISTERED`（即已入科轨且未清床：`IN_PROGRESS`/`ON_HOLD`/`PENDING`/`COMPLETED`）；**待入科** = 仅 `REGISTERED`；**默认在科**；切换仅本地过滤、不重拉 IF-IP-NUR-02 |
| **已出院结算（列表筛选项）**      | 产品筛选项；读侧谓词为 `IMP` 且 `status_enum ∈ { COMPLETED, DISCHARGED }`（已财务全结，含**未清床**与**已清床**）；**不提供**列表「召回」行操作（召回在**结算页**） |
| **未清床反结**            | `COMPLETED` 仍占床：**不可召回**；收银台 **`InpatientSettleAppService#reverseInpatientSettle`**（**IF-IP-RVS-01**） |
| **患者详情**              | 列表信息的**超集**：含入科办理展示所需的床位、医护、诊断、费用、入院体征等；**无**单独「入科办理上下文」读接口                                                            |
| **患者信息维护**            | 更新病情、责任医护（**IF-IP-NUR-04**）；参与者收口须 **`status_enum=COMPLETED` 且 `end_time` 落库** 后 INSERT 新 `ACTIVE`；**不**变更床位与就诊状态。轨迹契约见 **AS-IP-NUR-002** §1.7 |
| **展示用账户余额**           | **库内 `Account.balance` − Σ(`PENDING_SETTLEMENT`.total_price)**（**不**含 `PLANNED` 待收费）；经 **`InpatientAccountBalanceDisplayAssembler`** 组装为**单一余额文案**（不含「可用/待结算」拆分）；`pendingSettlementTotal` 仍作明细字段返回 |
| **lowBalanceWarning** | 展示用余额 < 500 为 true                                                                                                     |
| **待校对/待执行条数**         | **按 encounter 聚合**的两个整数；见 BR-IP-NUR-004/005；由域层批量 count，**不**写入 `InpatientEncounterContextQueryModel` QueryService                                      |
| **净执行记录（当日）**         | 对齐 **AS-OP-NUR-001** **BR-NUR-005/013**：同一 `encounter_id` + 请求锚点下，`Procedure` **COMPLETED** 次数减去已被 `refund_id` 对冲的 **CANCEL** 次数；用于判定「当日尚未执行」 |
| **读侧组装**               | 列表/详情以 **`InpatientEncounterContextQueryModel` QueryService**为骨架；QueryService**未投影**的展示字段（如病房名、任务计数、患者扩展档等）由 App **批量查询业务表/域查询服务**组装，**禁止**为护士站单独扩展QueryService列 |


### 1.4 本期范围与非目标


| 类别      | 内容                                        |
| ------- | ----------------------------------------- |
| **本期**  | IF-IP-NUR-01～04                           |
| **非目标** | 床位级树/列表（**AS-IP-NUR-002**）；分床写操作；各 Tab 医嘱明细；体温单/护理记录 CRUD（**AS-IP-NUR-007/014**） |


### 1.5 领域清单（聚合 + 职责 + DM 路径）


| 聚合（域）                                  | 职责摘要          | DM 路径                        |
| -------------------------------------- | ------------- | ---------------------------- |
| **Encounter**                          | 状态、病情、入院/出科时间 | `DM-ADM-002-Encounter-就诊.md` |
| **Patient**                            | 人口学信息         | `DM-ADM-001-Patient-患者.md`   |
| **Location**                           | 病区、病房、床位      | `DM-ADM-003-Location-位置.md`  |
| **EncounterParticipant**               | 住院/主治/主任/责任护士 | `DM-ADM-002`                 |
| **Account**                            | 库内账户余额与展示组装         | `DM-ADM-009-Account-就诊账户.md` |
| **MedicationRequest / ServiceRequest** | 待校对/待执行计数（按 encounter） | `DM-MED-003` / `DM-WKF-004`  |
| **Procedure**                          | 当日净执行记录（待执行判定） | `DM-CLN-004-Procedure-执行记录.md` |
| **Condition / EncounterDiagnosis**     | 诊断摘要          | `DM-CLN-003` / `DM-ADM-002`  |
| **VitalSign**                          | 入院体征事实（读/仅维护写） | `DM-DIA-002`                 |


### 1.6 业务规则（BR-*）


| 规则 ID             | 描述                                                                                        | 绑定 IF           |
| ----------------- | ----------------------------------------------------------------------------------------- | --------------- |
| **BR-IP-NUR-001** | 树：域层查登录者**管理病区**（**不走QueryService**）；**仅返回岗位绑定的启用态病区**（医院租户无绑定则**空树**）；每病区下挂 `bus_no` 子前缀且 `form_enum=ho` 的**病房**节点（不含床位） | IF-IP-NUR-01    |
| **BR-IP-NUR-002** | 列表/详情：`IMP` + 可见状态并集（§1.3）；**且**须已挂**活动入院病区**轨迹或已分**活动床位**（二者至少其一）；**不含**未登记入院病区且无活动床位的就诊（含待入科但未挂病区者） | IF-IP-NUR-02、03 |
| **BR-IP-NUR-003** | 列表地理范围：在 BR-002 基础上，活动床位 `bus_no` 以所选**病房** `bus_no` 为前缀，或活动入院病区落在所选**病区**子树内；仅 `wardId` 时含该病区内全部病房/床位患者；无地理筛选时在**管理病区并集**内（`managedWardIds` 与 IF-01 树同一提取口径） | IF-IP-NUR-02    |
| **BR-IP-NUR-004** | **待校对条数**（`pendingAuditCount`，**per encounter**）：统计该就诊下药品+诊疗请求中同时满足 **BR-IP-AUD-002** 同源过滤，且落入校对 `pending` 并集者：① **开嘱待校** `status_enum=ACTIVE`；② **停嘱待校** 长期 `status_enum=STOPPED` 且 `stop_performer_check_id` 为空；二者均须 `generate_source_enum=doctor-prescription`；护士划价/滚费等非医生开立一律不计；**默认排除麻醉申请**（申请单号 **AA 前缀**；与 **AS-IP-NUR-003 · BR-IP-NUR-003-ANES-001** 列表默认口径一致）；与 **AS-IP-NUR-003 BR-IP-AUD-003** 对齐 | IF-IP-NUR-02、03 |
| **BR-IP-NUR-005** | **待执行条数**（`pendingExecuteCount`，**per encounter**）：统计该就诊下同时满足：① `status_enum=VERIFIED`；② `status_enum≠STOPPED`；③ `generate_source_enum=doctor-prescription`；④ **开嘱日起至统计日截止前**仍存在未终态计划槽（与 **IF-IP-EXE-01 待执行 Tab** 对齐：含跨日未完成的临时/出院带药、昨日未完成的长期槽；长期药品计划槽与 **IF-IP-EXE-01** 同源：`StandingOrderPackageBatchPlanTimeResolver`（**BR-IP-EXE-035** 院点一盒一点优先；否则 **BR-IP-EXE-036** 拆零单位下限切批；均未命中则频次日点）+ 命中一盒一点口服时叠加 **BR-IP-EXE-038** 按天抑制；诊疗长期仍按频次展开；命中 035/036/038 时**禁止**再按日频次预计点计待执；**已执行 COMPLETED 与不执行 Procedure STOPPED 均为终态，不算待执行**；取消对冲后净值仍待执行）的**请求行数**（按医嘱行 +1，不做槽位累加）；**排除**护士划价、床位滚费、转科/出院/护理级别；**默认排除麻醉申请**（申请单号 **AA 前缀**；与 **AS-IP-NUR-004 · BR-IP-NUR-004-ANES-001** 列表默认口径一致）；**皮试检查 companion SR** 与**皮试药**（`skin_test_flag=true`）均**计入**待执行（执行/计费走 **AS-IP-NUR-004**；结果登记见 **AS-IP-NUR-011**） | IF-IP-NUR-02、03 |
| **BR-IP-NUR-006** | 展示余额 = **库内余额 − 待结算合计**；`PLANNED` **不**扣减；`lowBalanceWarning`（<500）；批量账户 + `mapPendingSettlementTotalPriceByEncounterIds` | IF-IP-NUR-02、03 |
| **BR-IP-NUR-016** | 列表/详情 **`contractDisplay`（费用性质）**：有默认医保账户且 `contract_no` 有效时优先其合同名，否则登记个人现金户；**禁止**用科室名冒充费用性质；前端卡片 `feeType` 读本字段 | IF-IP-NUR-02、03 |
| **BR-IP-NUR-018** | 列表/详情 **`accountId`（挂账账户）**：存在**启用中**的 `MEDICAL_INSURANCE` 时回传医保户主键，供执行/划价/滚费挂 `ChargeItem.account_id`；医保户已停用或纯自费时回传个人现金户。**预交金余额仍只读个人现金户**（与 `accountId` 分离）。医保转自费见 **AS-IP-FIN-001 BR-IP-REG-021** | IF-IP-NUR-02、03；下游 **AS-IP-NUR-004/005/013** |
| **BR-IP-NUR-007** | 任务筛选：`pending-audit` → `pendingAuditCount>0`；`pending-execute` → `pendingExecuteCount>0`；`processed` → 二者均为 0（在 BR-IP-NUR-002 可见状态并集内） | IF-IP-NUR-02    |
| **BR-IP-NUR-008** | 关键字：姓名、拼音、五笔、住院号、床号                                                                       | IF-IP-NUR-02    |
| **BR-IP-NUR-009** | 列表尽量返回**完整卡片字段**（§4 IF-IP-NUR-02）；详情为列表字段**超集**（§4 IF-IP-NUR-03）                          | IF-IP-NUR-02、03 |
| **BR-IP-NUR-010** | 信息维护：仅 `IN_PROGRESS` 或 `REGISTERED`；可改病情、责任医护、**入院体征**（**不换床**）；**禁止**改床、改 `status_enum` | IF-IP-NUR-04    |
| **BR-IP-NUR-011** | 责任医护可在 **002 分床（ATD-02）** 或 **本 IF 维护** 写入；库表均为旧行 **COMPLETED** + **INSERT** 新 **ACTIVE**。**入院体征**：**002 ASSIGN** 首次分床写；**本 IF** 仅维护（不换床）写；规则同 **002 BR-IP-ATD-020～024**（维护时 `recorded_at` 锚定 `encounter.start_time`，`location_id` 取当前活动床） | IF-IP-NUR-04、002 ATD-02 |
| **BR-IP-NUR-014** | **入院体征读**：详情 **IF-IP-NUR-03** 返回 `admissionVitalSigns`；按 `encounter.start_time` 精确查询 `dia_vital_sign`（**禁止** `getLatestByEncounterId` 填入院表单，避免与三测单最新值混淆） | IF-IP-NUR-03    |
| **BR-IP-NUR-017** | 前端入科状态下拉：**在科** / **待入科** 互斥；在科 = 未清床已入科轨（排除 `REGISTERED`，含已结算待清床 `COMPLETED`）；待入科 = `REGISTERED`；默认在科；本地过滤不改 IF-IP-NUR-02 请求 | IF-IP-NUR-02（UI） |
| **BR-IP-NUR-012** | ~~**列表状态筛选**~~ **已废止（2026-07-16）**：`nurseStationStatusFilter` **仅允许空**（默认在院）；~~`SETTLED_DISCHARGED`~~ 已删除；非空 → 参数非法。召回见 **FIN-004 IF-IP-RCL-01** | IF-IP-NUR-02 |
| **BR-IP-NUR-013** | ~~**召回列表行操作**~~ **已废止（2026-07-16）**：护士站**不展示**「召回」；`COMPLETED` 须收银台 **IF-IP-RVS-01** 反结；`DISCHARGED` 须收银台 **IF-IP-RCL-01** 召回 | IF-IP-NUR-02 |
| **BR-IP-NUR-015** | 列表/详情返回当前护理级别：`nursingLevelServiceRequestId`、`nursingLevelActivityId`、`nursingLevelName`、`nursingLevelStatusEnum`；经 **`InpatientPatientQueueNursingLevelEnrichAssembler`** 批量 enrich（口径同 **DM-WKF-004** §6.2.28）；无护理时上述字段为 `null` | IF-IP-NUR-02、03 |


---

## 2. 页面与接口总览（契约概览）


| IF 编号        | 用户动作             | 接口语义                | 读/写 | 关键规则              | 状态变化  | 涉及领域                    | QueryService                |
| ------------ | ---------------- | ------------------- | --- | ----------------- | ----- | ----------------------- | ------------------ |
| IF-IP-NUR-01 | 进入工作台 / 刷新树      | 查询**病区病房树**         | 读   | BR-IP-NUR-001     | 无     | Location 域查询（管理病区 + `bus_no` 子树） | —（不走QueryService） |
| IF-IP-NUR-02 | 选树节点、关键字、任务筛选、**状态筛选**、刷新 | 分页查询**在院患者列表**（富字段） | 读   | BR-IP-NUR-002～009、012、013、015 | 无     | InpatientEncounterContextQueryModel + 域批量组装 | InpatientEncounterContextQueryModel + 业务表 |
| IF-IP-NUR-03 | 点选患者             | 查询**患者详情**（全景）      | 读   | BR-IP-NUR-002～009、014、015 | 无     | 同上 + VitalSign | InpatientEncounterContextQueryModel + 业务表 |
| IF-IP-NUR-04 | 保存病情/医护/入院体征（不换床） | **患者信息维护**          | 写   | BR-IP-NUR-010、011 | 无状态迁移 | Encounter / Participant / VitalSign | QueryService 刷新               |


---

## 3. 缺口跟踪（Gap）

| Gap ID | 类型 | 目标 | 缺什么 | 影响 IF | 优先级 |
| --- | --- | --- | --- | --- | --- |
| — | — | — | **无开放 Gap** | — | — |

> **已闭合（v1.13）**：**GAP-IP-NUR-006** — 读侧 `InpatientEncounterPageQuery#nurseStationStatusFilter`；应用 `pagePatientQueue`；前端列表范围筛选。**2026-07-16**：~~`recallEnabled` 召回行操作~~ 已废止，召回在结算页 **IF-IP-RCL-01**。
>
> **已闭合（v1.12）**：**GAP-IP-NUR-006** 读侧首版 — `EncounterContextPageQueryService` 护士站状态与召回地理收窄（`STATUS_FILTER_SETTLED_DISCHARGED`、`DISCHARGED` 末次 WARD）。
>
> **决议口径（2026-05-20）**：护士站**不扩展** `InpatientEncounterContextQueryModel` 字段；富展示与任务计数在 App 层QueryService + 域表批量组装。**出院召回写**在 **AS-IP-FIN-004** **IF-IP-RCL-01**（收银台；~~**002 ATD-07**~~ 已废止）。


---

## 4. 接口编排说明书（业务步骤）

### IF-IP-NUR-01 病区病房树


| 维度          | 说明                                                                            |
| ----------- | ----------------------------------------------------------------------------- |
| **触发前提**    | 进入护士站工作台或刷新左侧树                                                                |
| **主路径步骤**   | **`CurrentManagedWardLocationIdsResolver#listManagedActiveWardsOrEmpty`**（**PractitionerManagedScopeLoader** / **DM-ADM-006**；**医院租户**未绑定则 empty，App **不**调 `listAllActiveWards`）→ 对每个返回病区取 `bus_no` 前缀匹配的 `form_enum=ho` 病房（`{ward.bus_no}.%`）→ 组装树：病区节点 + 子病房（id、name、`bus_no`、`display_order`） |
| **关键分支**    | 单病区用户仅一树根；多病区多根或虚拟「全部」由 SDD 与 UI 约定                                           |
| **失败分支**    | EX-IP-NUR-101                                                                 |
| **查询侧说明** | 纯展示；病区/病房主数据变更后刷新                                                             |


### IF-IP-NUR-02 在院患者列表（富字段）


| 维度                         | 说明                                                                                                                                                                                                                                                                               |
| -------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **触发前提**                   | 树节点变更、关键字/任务筛选、**状态筛选**（`nurseStationStatusFilter`，默认在院）、分页、刷新                                                                                                                                                                                                                                                             |
| **主路径步骤**                  | `pageNurseStationQueue`（**Q-IPI-005**，地理/关键字；任务筛选 App 编排）→ 批量账户与展示余额（BR-IP-NUR-006）→ … → 派生展示文案；**无** `recallEnabled` 字段；无活动床时床列「原{房名}-{床名}（未占床）」 |
| **列表宜返回字段（业务语义，SDD 可增删列）** | **就诊**：`encounterId`、**`accountId`（挂账账户，见 BR-IP-NUR-018）**、住院号、状态及文案、病情、入院/入科/出院相关时间；**患者**：姓名、性别、年龄、患者 id；**位置**：病区/病房/床（未占床为「原{房}-{床}（未占床）」）；**科室**；**费别**；**费用**与展示余额；**任务**计数；**医护摘要**；**诊断**；**护理级别**；**检索码** |
| **关键分支**                   | 已挂入院病区但尚未分床仍可返回行；未挂入院病区且无活动床位不返回（**默认在院**）；选病房节点后列表窄于选病区；召回未占床患者有活动 WARD、无活动 BED，床列回填末次床展示文案；反结/召回见 **FIN-004** 结算页 |
| **失败分支**                   | EX-IP-NUR-101                                                                                                                                                                                                                                                                    |
| **查询侧说明**                | 纯展示                                                                                                                                                                                                                                                                              |


### IF-IP-NUR-03 患者详情（全景）


| 维度                   | 说明                                                                                                                                                                                                                           |
| -------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **触发前提**             | 列表点选 `encounterId`；入科 Tab 分床前亦**仅调本 IF**（不调 002 的读接口）                                                                                                                                                                        |
| **主路径步骤**            | 校验可见范围 → `EncounterContextReader#mustGetInpatientContextByEncounterId` → 账户与余额预警 → 域层单就诊任务计数（同 BR-004/005）→ **`InpatientPatientQueueNursingLevelEnrichAssembler#enrichByEncounterIds`** 解析护理级别（**BR-IP-NUR-015**）→ 业务表补全列表未带明细 → **`AdmissionVitalSignSupport#toSubmitDto`**（锚点 `encounter.start_time`，**BR-IP-NUR-014**）组装 **`admissionVitalSigns`** |
| **详情在列表基础上的增量（宜包含）** | **患者**：电话、证件等（`adm_patient`，QueryService无则查表）；**位置**：与列表同源的结构化字段（`wardId`/`wardName`/`houseName`/`bedId`/`bedName`，**禁止** API 透传 `location_json`）；**医护**：`EncounterParticipant` 全角色（`roleCode`=`EncounterParticipantRole#getCode()`，`roleName`=`getDisplay()`；读侧 **`listDisplayEncounterParticipants`**：活动优先，清床/召回后回落末次 COMPLETED）；**诊断**：与列表同源的 `diagnosisList` + `diagnosisSummary`（**禁止** API 透传 `diagnosis_json`）；**费用**：分项同列表；**入院体征**：`admissionVitalSigns`（**BR-IP-NUR-014**；7 项与 **002** `AdmissionVitalSignSubmitDto` 同结构）；**入科**：当前床；目标床仅换床展示对比时由前端传入 |
| **关键分支**             | 与列表数字一致（余额、计数、状态）                                                                                                                                                                                                            |
| **失败分支**             | EX-IP-NUR-102；EX-IP-NUR-101                                                                                                                                                                                                  |
| **查询侧说明**          | 纯展示                                                                                                                                                                                                                          |


### 辅助读：分床责任医护下拉（`loadAdmissionParticipantInit`）

| 维度 | 说明 |
| --- | --- |
| **触发前提** | **002** 入科 Tab 打开分床/换床抽屉；用户选择或变更入院科室、入院病区；住院登记弹窗拉主治下拉亦复用本接口 |
| **主路径步骤** | 可选 `organizationId` → 按科室激活 **ROLE_DOCTOR** 岗位收窄住院医师；可选 `wardLocationId` → 按病区 `location_id` 激活 **ROLE_NURSE** 岗位收窄责任护士；未传病区则护士列表为空 |
| **出参扩展** | 可选 `defaultChiefDoctorId` / `defaultAttendingDoctorId` / `defaultInpatientDoctorId`（字符串雪花）；产品默认 null；**院点 L2** 可按科室策略填入，前端仅在对应角色为空时带出，不覆盖已选手填 |
| **HTTP** | `POST /api/app/nurseStationEncounterManage/loadAdmissionParticipantInit`（编排在 **001** AppService，**不增 IF 编号**） |
| **失败分支** | EX-IP-NUR-101 |

### IF-IP-NUR-04 患者信息维护


| 维度          | 说明                                                              |
| ----------- | --------------------------------------------------------------- |
| **触发前提**    | 详情页保存；**仅维护**（无分床写操作）或换床/互换前保存医护/病情；**首次分床（ASSIGN）时 `admissionVitalSigns` 禁止经本 IF 提交**（须随 **002** ATD-02，避免 `start_time` 未落库） |
| **主路径步骤**   | 校验 BR-IP-NUR-010 → `saveNurseStationPatient`：更新 `priority_enum`；参与者**按角色**旧行 COMPLETED + INSERT 新 ACTIVE（禁止 UPDATE）→ **不**改 `adm_encounter_location` 与就诊状态 → **若**携带非空 `admissionVitalSigns` 且 `encounter.start_time` 非 null：同事务调 **`AdmissionVitalSignSupport#saveIfPresent`**（规则同 **002 BR-IP-ATD-023/024**；`recorded_at = encounter.start_time`；`location_id` = 当前活动床） |
| **关键分支**    | 与 **002** 分床区别：本 IF **不换床、不迁状态**；**无 `start_time`**（未入科）时跳过体征写；医护与 **002** 共用同一轨迹表，存储契约见 **AS-IP-NUR-002** §1.7 |
| **失败分支**    | EX-IP-NUR-201                                                   |
| **查询侧说明** | 详情/列表医护、病情、体征刷新                                                 |


---

## 5. 业务异常与用户提示


| 异常 ID         | 场景          | 用户提示        | 可重试 |
| ------------- | ----------- | ----------- | --- |
| EX-IP-NUR-101 | 树/列表/详情查询失败 | 加载失败，请稍后重试  | 是   |
| EX-IP-NUR-102 | 详情：就诊不可见    | 患者不可用，请刷新   | 是   |
| EX-IP-NUR-201 | 信息维护失败      | 保存失败，请联系管理员 | 是   |


---

## 6. 事件语义与消费方


| 事件                                 | 来源           | 影响      |
| ---------------------------------- | ------------ | ------- |
| `InpatientAdmissionCompletedEvent` | 登记           | 列表新增    |
| `InpatientBedAssignedEvent`        | **002** 分床   | 状态、床号   |
| `AccountBalanceEditedEvent`        | 预交金          | 余额、预警   |
| 校对/执行类事件                           | 医嘱 Tab       | 任务计数    |
| `NurseStationPatientSavedEvent`        | IF-IP-NUR-04 `saveNurseStationPatient` | 详情/列表医护、病情（投影待对齐） |
| `VitalSignSavedEvent`                  | IF-IP-NUR-04 入院体征维护；**002** ATD-02 ASSIGN | 详情 `admissionVitalSigns`；**007/014** 读侧 |


---

## 7. 非功能性需求


| 维度      | 要求                              |
| ------- | ------------------------------- |
| **性能**  | 列表批量账户与任务计数；树一次加载可缓存；列表默认分页 ≤50 |
| **一致性** | 列表与详情同源；树筛选与列表范围一致              |
| **权限**  | 管理病区外患者不得出现在列表；**未绑定管辖病区时列表为空**                  |


---

## 8. 测试要点（业务视角）


| 类别     | 要点                              |
| ------ | ------------------------------- |
| **树**  | 有管理病区仅返回授权子集；**无管理病区（医院租户）返回空树**；选病房后列表范围变窄 |
| **列表** | 富字段非空策略；已挂入院病区或已分床者在队；未挂病区且无床不在队（默认在院）；任务筛选与计数一致 |
| **状态筛选** | **已废止**；仅默认在院；召回未占床床列「原{房}-{床}（未占床）」；**无**护士站召回按钮 |
| **未清床** | `COMPLETED` → 收银台 **`InpatientSettleAppService#reverseInpatientSettle`**（**IF-IP-RVS-01**），不可召回 |
| **详情** | 较列表更全；入科 Tab 打开分床抽屉前只调详情        |
| **维护** | 不改床、不改状态；医护按角色 COMPLETED+INSERT；分床/换床走 002；体征本期不做 |
| **计数** | 待校对=`ACTIVE` 且 `doctor-prescription` 行数；待执行=`VERIFIED` 且医生开立且当日覆盖&lt;当日计划点数（排除划价/滚费/转科出院护理）；与医嘱 Tab 患者维度一致 |


---

## 9. 关联文档与变更历史



### 9.2 变更历史

| 版本 | 日期 | 变更摘要 |
| --- | --- | --- |
| v1.39 | 2026-10-15 | **`loadAdmissionParticipantInit`**：可选默认主任/主治/住院医生主键出参（产品空；院点可填）；登记/入科前端空位自动带出 |
| v1.38 | 2026-10-13 | **BR-IP-NUR-004/005 纠偏**：校/执气泡排除口径改为申请单号 **AA 前缀**；对齐 **AS-IP-NUR-003 v1.31** / **AS-IP-NUR-004 v2.67** |
| v1.37 | 2026-10-13 | **BR-IP-NUR-004/005**：校/执气泡默认排除麻醉申请；对齐 **AS-IP-NUR-003 v1.30** / **AS-IP-NUR-004 v2.66** |
| v1.36 | 2026-08-19 | **BR-IP-NUR-005**：待执计数叠加 **BR-IP-EXE-038** 口服一盒一点按天抑制（与 IF-IP-EXE-01 同源）；对齐 **AS-IP-NUR-004 v2.51** |
| v1.35 | 2026-08-17 | **BR-IP-NUR-018**：`accountId`=挂账账户（医保入院回传医保户；纯自费回传个人现金户）；余额仍只读个人现金户；本期无住院转自费 |
| v1.34 | 2026-08-17 | 未占床床列「原{房}-{床}（未占床）」；对齐 **DM-ADM-002 v1.44** |
| v1.33 | 2026-08-17 | IF-IP-NUR-03 医护读侧改为 **`listDisplayEncounterParticipants`**（活动优先 / 末次 COMPLETED）；对齐 **DM-ADM-002 v1.43** |
| v1.32 | 2026-08-17 | **入科状态下拉**：~~全部~~ → **在科 / 待入科**（**BR-IP-NUR-017**）；在科=未清床已入科轨（含 `COMPLETED` 待清床）；默认在科；前端本地过滤 |
| v1.31 | 2026-08-11 | **BR-IP-NUR-005**：待执计数对齐 **BR-IP-EXE-036** 拆零下限切批（与列表同源 Resolver；035 优先）；对齐 **AS-IP-NUR-004 v2.48** |
| v1.30 | 2026-08-08 | **BR-IP-NUR-004**：`pendingAuditCount` 计入停嘱待校（长期 `STOPPED` 且 `stop_performer_check_id` 空）；对齐 **AS-IP-NUR-003 v1.16** |
| v1.29 | 2026-08-07 | **BR-IP-NUR-005**：长期药品待执计数与 **IF-IP-EXE-01 / BR-IP-EXE-035** 一盒一点同源（`StandingOrderPackageBatchPlanTimeResolver`）；禁止按日频次预计点计不可拆口服长嘱 |
| v1.28 | 2026-07-22 | **BR-IP-NUR-004**：与 **AS-IP-NUR-003 v1.15** 对齐——废止 based_on 排除；澄清住院退药不造负向 Request |
| v1.27 | 2026-07-22 | **BR-IP-NUR-005**：皮试检查 companion SR **计入**待执行（废止住院执行互斥）；对齐 **AS-IP-NUR-011 v1.4** / **AS-IP-NUR-004 v2.39** |
| v1.26 | 2026-07-18 | **BR-IP-NUR-016**：队列/详情 `contractDisplay` 有医保账户优先；禁止科室名冒充费用性质 |
| v1.25 | 2026-07-17 | **BR-IP-NUR-005**：待执行计数**仅排除**皮试检查 companion SR；**皮试药计入**待执行（**v1.27 废止排除**） |
| v1.24 | 2026-07-16 | **收尾**：废止 ~~`SETTLED_DISCHARGED`~~ / ~~`nurseStationStatusFilter` 非空~~（**BR-IP-NUR-012**）；移除 `recallEnabled` 字段；未占床床列「原床（未占床）」 |
| v1.23 | 2026-07-16 | **出院召回 redesign**：废止护士站 `recallEnabled`/召回行操作（**BR-IP-NUR-013**）；召回在 **FIN-004 IF-IP-RCL-01** 结算页 |
| v1.22.1 | 2026-07-04 | **IF-IP-NUR-03 详情**：**BR-IP-NUR-015** 扩展至患者详情，返回 `nursingLevel*` 四字段 |
| v1.22 | 2026-07-04 | **护理级别列表 enrich**：**BR-IP-NUR-015**；IF-IP-NUR-02 返回 `nursingLevel*` 四字段；经 **`InpatientPatientQueueNursingLevelEnrichAssembler`** + **DM-WKF-004** §6.2.28 |
| v1.21 | 2026-06-21 | **展示文案简化**：列表/详情卡片仅展示单一余额文案；对齐 **AS-IP-FIN-002** v2.1 |
| v1.20 | 2026-06-20 | **support 落点对齐**：`InpatientAccountBalanceDisplayDtoApplier` → `module.common.assembler.inpatient`；队列读侧 → `module.querysupport.nursestation`；详见 **AS-IP-NUR-001-SDD** |（已迁至 application.inpatient.nursestation.{feature}.support）（已迁至 application.shared.inpatient）（已迁至 application.shared / feature support（见 DISSOLUTION.md））
| v1.19 | 2026-06-19 | **账户余额语义**：展示 = 库内 balance − 待结算；`InpatientAccountBalanceDisplayAssembler` 文案；对齐 **AS-IP-FIN-002** v2.0 |
| v1.18 | 2026-06-19 | **成码对齐**：IF-IP-NUR-04 前端 ASSIGN 体征仅随 **002** ATD-02；无 `start_time` 跳过体征写；Support 方法 `toSubmitDto`/`saveIfPresent` |
| v1.17 | 2026-06-19 | **入院体征**：**BR-IP-NUR-014** 详情读；IF-IP-NUR-04 仅维护写；与 **002 v2.12** / **DM-DIA-002** 对齐；首次分床写仍在 **002 ATD-02 ASSIGN** |
| v1.16 | 2026-06-18 | **成码类名锚点**：召回写 **`RecallReverseSettleSupport#execute`**；未清床反结 **`InpatientSettleAppService#reverseInpatientSettle`**；§4 IF-IP-NUR-02、§8 测试同步 |
| v1.15 | 2026-06-18 | **管理病区硬权限**：**BR-IP-NUR-001**、§1.3 术语、IF-IP-NUR-01 编排、§7/§8 同步；医院租户未绑定岗位病区时**空树/空列表**，不再回落 `listAllActiveWards` |
| v1.14 | 2026-06-18 | **召回口径对齐 002 v2.9**：**仅 `DISCHARGED` 展示召回**；`COMPLETED` 走 **AS-IP-FIN-004 反结**；**BR-IP-NUR-013**、§4 IF-IP-NUR-02、§8 测试同步 |
| v1.13 | 2026-06-18 | **定稿对齐成码**：**GAP-IP-NUR-006** 应用层 `pagePatientQueue` + `recallEnabled` + 前端列表筛选/召回按钮；写调 **002 ATD-07** |
| v1.12 | 2026-06-18 | **出院召回读侧**：**BR-IP-NUR-012/013** 列表状态筛选（默认在院、`SETTLED_DISCHARGED`）；召回入口行操作 → **002 ATD-07**；**GAP-IP-NUR-006 已闭合**（Q-IPI-005 状态筛选扩展） |
| v1.11 | 2026-06-11 | **BR-IP-NUR-002/003** 队列可见性收窄；**HOUSE** 岗位绑定回落上级 **WARD**；辅助读 **`loadAdmissionParticipantInit`**（分床医护下拉，HTTP 在 001、供 002 调用） |
| v1.10 | 2026-05-28 | 读模型废止后对齐 zxplat 实现（EncounterContextFacade / querysupport Loader）。 |（已迁至 EncounterContextReader / application.shared.encounter）
| 类型    | 文档                                            |
| ----- | --------------------------------------------- |
| 关联 AS | `AS-IP-NUR-002`（仅床位+分床）；`AS-IP-FIN-001/002`   |
| QueryService   | `DM-ADM-002 §6.2 EncounterQueryService（InpatientEncounterContextQueryModel）`（**Q-IPI-005**）     |
| SDD   | `AS-IP-NUR-001-住院护士站患者管理-SDD.md`（**v2.30**）   |
| UI    | `openhis-pro-ui/.../inpatient/nurse-station/` |



| 版本   | 日期         | 摘要                                            |
| ---- | ---------- | --------------------------------------------- |
| v1.0 | 2026-05-19 | 首版                                            |
| v1.1 | 2026-05-19 | 增病区病房树；详情=全景含原入科上下文；维护归入本 AS；列表富字段；与 002 职责拆分 |
| v1.2 | 2026-05-20 | Gap 决议：不扩展QueryService；任务计数 per encounter（ACTIVE/VERIFIED+当日净执行）；管理病区与 `bus_no` 树走域查询；体征维护暂缓 |
| v1.2.1 | 2026-05-20 | GAP-IP-NUR-003 关闭：`LocationQueryService` 管理病区/病房/`bus_no` 子节点查询与实现对齐 |
| v1.3 | 2026-05-20 | GAP-IP-NUR-002/004 关闭：`pageNurseStationQueue` + 各聚合批量查询；QueryService **Q-IPI-005**；SDD 落盘 |
| v1.3.1 | 2026-05-20 | 分层修正：删除域层 `inpatientnursestation` 跨聚合计数；计数与 BR-IP-NUR-007 归 App |
| v1.3.2 | 2026-05-20 | 任务筛选改回 `InpatientNurseStationConstants`（与前端 `pending-audit` 等 code 对齐） |
| v1.4 | 2026-05-20 | **BR-IP-NUR-011**：责任医护可在本 IF 或 **002 分床**写入；维护轨迹 **COMPLETED+INSERT**（按角色）；与 **AS-IP-NUR-002** §1.7 对齐 |
| v1.5 | 2026-05-20 | **§3 Gap 收口**：已闭合项合并为「无待跟踪缺口」；体征维护标为非目标（非 Gap） |
| v1.6 | 2026-05-20 | IF-IP-NUR-02/03 出参：`diagnosisList`+`diagnosisSummary`（禁止 `diagnosis_json`）；详情位置用结构化字段（禁止 `location_json`）；参与者 `roleName` 展示名 |
| v1.9 | 2026-05-24 | **BR-IP-NUR-004/005**：任务计数**排除** `generate_source_enum=nurse-pricing`；对齐 **AS-IP-NUR-005 v2.0** / **DM-WKF-004 v1.1.7** |
| v1.8 | 2026-05-22 | IF-IP-NUR-02 列表/详情增加 `accountId`（就诊默认个人现金账户）；供 IF-IP-EXE-02 执行回传 |
| v1.7 | 2026-05-20 | 参与者角色对齐共享枚举 `EncounterParticipantRole`（`zxplat-domain`，DM-ADM-002 §3.2.1） |



