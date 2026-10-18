# 住院护士站 · 医嘱执行 · 应用服务设计（AS）

> **文档职责**：工作台右侧「医嘱执行」Tab 契约——批量装载**已校对**医嘱与四类执行槽位；**执行**、**不执行**写操作；列表与写路径均预留**绑定项**结构（无配置时返回空列表/跳过写分支）。
>
> **页面边界**：**本 Tab**——执行状态切换、长临嘱筛选、**截止时间**、查询刷新、槽位展示、勾选执行/不执行。**患者上下文**在 **AS-IP-NUR-001**；**医嘱校对**在 **AS-IP-NUR-003**；**取消执行**在 **AS-IP-NUR-012**（**IF-IP-CXL-01**，已执行 Tab）。**消费方**：住院医生站/病案临床侧栏「执行记录」只读弹窗复用 **IF-IP-EXE-01**（不写）。
>
> **编码**：AS-IP-NUR-004 · **版本**：v2.69 · **日期**：2026-10-15

---

## 1. 场景概述

### 1.1 目标与边界

| 维度 | 说明 |
| --- | --- |
| **在内** | IF-IP-EXE-01 执行列表（槽位 + **bindItems**）；IF-IP-EXE-02 **执行**（Procedure + 发药/账单 + **药嘱**库存 Gate + 绑定项写路径）；IF-IP-EXE-04 **不执行**（计划点 STOPPED） |
| **在外** | 左侧患者树/列表/详情（**AS-IP-NUR-001**）；**取消执行**（**AS-IP-NUR-012 IF-IP-CXL-01**）；医嘱校对（**AS-IP-NUR-003**）；账户余额/费别；UI「首日执行」筛选开关（若有，**本期后端不处理**；**首日用量/`first_dose` 槽位裁剪与换量见本期 BR-IP-EXE-004/032**）；**按周 / Q8H 等更复杂间隔**（待 **DM-CUS-008** 全量补全后再接）；修改计划执行时间；**LIS/PACS 外发协议与对码**（院点 L2 MedSync，见 **BR-IP-EXE-039**，**不入** health 核心写路径） |
| **涉及域** | MedicationRequest；ServiceRequest；InpatientOrderLookupLoader · InpatientOrderListSkeleton；Frequency；**Procedure**；**MedicationDispense**；**ChargeItem**；**DeviceRequest / DeviceDispense**（绑定耗材）；**ServiceDeviceTemplate**（绑定查询）；Encounter |
| **编排权威** | 以本文 **§2、§4** 为准；写侧对齐住院护士 `adviceExecute` / `adviceVoid` 业务语义 |

### 1.2 角色与关键动作

| 角色 | 关键动作 |
| --- | --- |
| **病区护士** | 勾选患者 → 设截止时间/执行 Tab/长临嘱 → **查询** → 查看槽位与绑定项 → 勾选待执行槽位 → **执行**（可勾选绑定项）或 **不执行** |
| **系统** | 装载 **VERIFIED** 医嘱 → 频次展开 + Procedure 槽位归并 → 批量挂载 **bindItems** → 执行时**药嘱**库存 Gate + FIFO 多段 → **长临统一**写 Procedure + 新建 Dispense（药，可跨批多行）+ 新建 **ORDER_EXECUTE** Charge（主医嘱）→ 绑定项有则按 **医嘱补费同构**落 **nurse-pricing**（绑定耗材**不**做药嘱库存 Gate） |

### 1.3 术语与口径约定

| 术语 | 口径 |
| --- | --- |
| **执行状态 Tab（executeTab）** | 过滤**当前 Tab 对应类别的计划时间点（槽位）**：一律 `planTime ≤ executionDeadline` 且属于该 Tab 的槽位（**含长嘱不可拆一盒一点**，**禁止**为盒尾 bypass deadline，**BR-IP-EXE-010/035**）；**医嘱行集合与展示字段不因 Tab 切换而变化**；**无「全部」Tab**；**仅列表** |
| **截止时间（executionDeadline）** | **仅 IF-IP-EXE-01**：列表展示窗口上界；与停嘱时间取 **min** 作为频次展开右边界；不可拆一盒一点时用于判定截止落在第几包装批次并选出第 1～N 盒**盒首锚点**（**BR-IP-EXE-035**）；**禁止**用于 IF-IP-EXE-02/04 写 Gate |
| **开嘱日区间（reqOpenedTimeFrom / reqOpenedTimeTo）** | **仅 IF-IP-EXE-01 可选**：装载完成后按行 `reqOpenedTime`（空回落签发时刻）闭区间收窄医嘱行；两端皆空则不过滤（医嘱执行页默认）；打印中心检查/检验/采血贴按开嘱日查询时传当日 `00:00:00`～`23:59:59`；同时传时须 from ≤ to；缺开嘱时刻的行**保留**（**BR-IP-EXE-037**） |
| **列表频次展开窗口** | 长期：先以开立日 **00:00:00**～`min(停嘱, executionDeadline)` 调用 `computeExecutionTimePoints`（频次时钟**可配置**，禁止写死）；再对**开嘱自然日**相对**开嘱时刻**做首次槽裁剪（**BR-IP-EXE-004/032**，承接 **AS-IP-DOR-003 BR-IP-ORD-030⑥**）；**全局 `inpatientStandingOrderSplittable=0` 且满足一盒一点资格**（长期 + 目录 `injectFlag≠true` + 包装≠拆零 + 口服类）时再切盒取盒首锚点（**BR-IP-EXE-035**）；**否则**若单槽临床拆零量 &lt; 1 且非「输液开瓶即弃」（输液目录默认排除，**皮下注射用法放行**），按满 1 拆零单位切批取单位首槽（**BR-IP-EXE-036**，产品默认）；临时：**不展开频次**；待执行单槽 `planTime` = 开立时刻；**嘱托**：**不展开频次**（与临嘱同构整单一次，**BR-IP-EXE-042**），`rate_code` 仅展示 |
| **首日用量（首次用量）** | 习惯称「首日」，语义为**首次执行**用量（`first_dose`）；**仅长期药品**；**仅开嘱日首次 `planTime` 槽**在填了首日量时换量；开嘱日其余点与后续日：有 `quantity_schedule` 则按槽取段，否则用常规 `quantity`；**不新建** occurrence（**BR-IP-EXE-032**）；不可拆切盒 / 拆零下限切批均在此口径之后累加拆零量 |
| **分次用量不一致** | 长期药品 `dose_schedule`（计量串）/`quantity_schedule`（拆零串）；**皆空** → 算量完全走原 `quantity`（零改动）；**有值** → 当日频次点按**早→晚**序号取第 i 段作该槽临床量，再进一盒一点/拆零下限；**禁止**再用「单剂量×次数」估日总量（**BR-IP-EXE-040**；承接 **AS-IP-DOR-003 · BR-IP-ORD-037**）。与首日量正交：首次槽优先 `first_dose` |
| **长嘱不可拆（一盒一点）** | 设置 `inpatientStandingOrderSplittable`：`1` 可拆（默认）/ `0` 不可拆。值为 `0` 且**同时**满足：长期药品、目录 **`injectFlag≠true`（非输液）**、`packageUnitCode ≠ splitUnitCode`、用法展示名以「口服」开头（含口服缓释/控释等）→ **一盒一点**（**BR-IP-EXE-035**，**院点特殊**）。输液目录即使用法误开口服也**不得**按箱切点/汇总领药。与产品拆零下限（**BR-IP-EXE-036**）正交；命中 035 时优先，不再叠 036 |
| **单位领用剩余（一盒一点 / 拆零下限）** | 命中 **BR-IP-EXE-035** 一盒一点或 **BR-IP-EXE-036** 拆零下限时，读路径**实时**计算理论剩余（领入拆零 − 按医嘱理论吃掉的拆零），**不落表**；挂在对应医嘱行；并按**自然日总需求**抑制过早的下一单位 pending（**BR-IP-EXE-038**；PACKAGE 领入=`packageUnitRatio`，MIN_SPLIT 领入=1；裁决见 **DD-住院长嘱口服一盒一点剩余计数与改量-2026-08** / **DD-住院执行-拆零单位下限取整-2026-08 §3**）。药典输液默认不参与；**皮下注射用法放行**（胰岛素）。改量链见 **AS-IP-DOR-003 BR-IP-ORD-032** |
| **拆零单位下限（产品默认）** | 单槽临床拆零量 &lt; 1，且**非**「输液开瓶即弃」时切批：列表按满 1 拆零单位回传单位首槽、pending 至多一个。药典 `injectFlag=true` 默认开瓶即弃（不切批/不续算）；**用法展示名以「皮下注射」开头时放行**（胰岛素等余量接着用，即使目录为输液）。写路径仍可抬到 `quantity=1` + `splitUnitCode`（**BR-IP-EXE-036**）。剩余/改量/出院叠加 **BR-IP-EXE-038 MIN_SPLIT**。详见 **DD-住院执行-拆零单位下限取整-2026-08** |
| **计划点（planTime）** | **长期**：频次展开并裁剪后的槽位，写侧 = Procedure **`occurrence_time`**；**同时**写入发药 **`plannedDispenseTime`**、账单 Charge **`occurrence_time`**（**汇总领药勾选时间点/截止/归组、防重、月封顶等一切逻辑操作**均锚定本字段）。**临时**：待执行列表 `planTime` = 开立时刻；**执行写路径强制 `planTime = actualTime`**（见下「临嘱双轨收敛」） |
| **实际执行时间（actualTime）** | 护士认定发生时刻 → Procedure **`recorded_time`**（及执行人登记时刻）；**默认**：开嘱日首次 pending 槽 = **开立时刻 + 2 分钟**（跨日钳开立日末）；**已停嘱**时停嘱日末 pending 槽 = 停嘱时刻（对称易用，同槽开嘱优先；列表 `defaultActualTime` / 写侧同口径）；其余槽 = `planTime`；**仅允许同自然日改 HH:mm**（**BR-IP-EXE-030**）。**长期**：**仅展示**，**禁止**改写 `plannedDispenseTime` / 账单 occurrence。**临时（临嘱双轨收敛）**：写侧 **`planTime` 与 `actualTime` 永远同值**——不改则默认同为开立+2 分钟，改 HH:mm 则 occurrence / recorded / 发药 `plannedDispenseTime` / 账单 occurrence **一起**变为改后时刻 |
| **槽位匹配键** | **长期**：**一律 Procedure.`occurrence_time`（planTime）**；汇总领药时间点读 Dispense.`planned_dispense_time`（=planTime）。**禁止**用 `recorded_time` 做槽位归类、deadline、汇总勾选或写 Gate。**临时**：净态整单归并（**BR-IP-EXE-006**）；写后 occurrence=recorded，读侧已执行展示该同值时刻 |
| **UI 时间列（单列）** | **双轨展示**：待执行优先 `defaultActualTime`（开嘱日首次=开立+2 分钟；已停嘱停嘱日末=停嘱；无则 `planTime`）；已执行优先 `recordedTime`（无则回落 `planTime`）；不执行/取消执行展示计划槽位时分（可拼 `operatorName`）。列名：待执行/不执行/取消=「计划时间」，已执行=「执行时间」。**禁止**双列并排计划+实际；**禁止**用 recorded/`defaultActualTime` 做勾选键/deadline/写 Gate（仍锚 `planTime`）。**已执行 / 不执行 / 取消执行**槽位标签在时刻后拼接 **`operatorName`**（如 `08:00 张三`），与汇总领药「时刻+数量」同构（**BR-IP-EXE-031**）；待执行仅时刻 |
| **四类槽位（同构）** | `pendingSlots` / `executedSlots` / `skippedSlots`（STOPPED）/ `cancelledSlots`（CANCEL）；cancel 对冲口径同 **BR-NUR-005**；**执行-取消对冲**后净态为 **hedged pending**（见 **BR-IP-EXE-008**），**同时**写入 `pendingSlots` 与 `cancelledSlots` |
| **hedged pending（执行-取消对冲净值）** | 某 occurrence 曾 COMPLETED 且已被 CANCEL（`refund_id` 指向）对冲，或末态为带 `refund_id` 的 CANCEL 行 → **净值仍为待执行**；读侧 **pending**（无 `procedureId`）+ **cancelled**（末态 CANCEL 行，含 `procedureId`）**双 Tab 展示**；写 Gate 与首次待执行同构（**BR-IP-EXE-014**） |
| **长临嘱 / 频次 / PRN** | 长期须 `rate_code`；临时单槽；PRN 不预生成计划点、仅据已有 Procedure 拆分 |
| **频次展开（护士站）** | **禁止** App 层写死「每天 + day_times」；长期计划点**统一**调用 `FrequencyQueryService#computeExecutionTimePoints`（**DM-CUS-008 §9.1**）；**须支持隔日（QOD / `interval` + `day_interval`）**；算法补全在频次域，护士站只消费结果 |
| **医嘱基线** | `generate_source_enum = doctor-prescription`；`order_type ∈ {MED_REQUEST,SERVICE_REQUEST}`；`status_enum ∈ {VERIFIED, STOPPED}`；停嘱后仍在列表展示，行状态为「已停嘱」 |
| **绑定项（bindItems）** | 列表行内数组，语义对齐老系统 `serviceBindDeviceList`：**诊疗绑耗材**（按 ServiceRequest 批量查）+ **药品用法绑诊疗/耗材**（按 MedicationRequest 批量查）；无模板配置时 **空数组**；执行写路径原样回传 |
| **执行联动（药/诊）** | **长临统一**：每计划点 **新建** Dispense（药，FIFO **可多段/多行**）+ **新建** Charge（`generate_source = ORDER_EXECUTE`，**`PENDING_SETTLEMENT` 待结算**）；**禁止**挂接签发侧草稿 Dispense/Charge（住院开立不再预建） |
| **库存 Gate** | 执行前 **App 层**组装（**仅药嘱**）：按 **开立机构** + **发放类别** 解析 `ServiceLocationTemplate` 候选药房顺位 → FIFO dry-run → 通过后写 Dispense `locationId` + `lotNumber`；可用量 = 批次可用量 − 全局待发预占（`listDispenseExeReserve`，**不限就诊**）；不足**整批失败**；**绑定耗材不参与** |
| **发放类别（distributionCategoryCode）** | 与住院医生站目录同口径：按药品 `medicationId` 取 `query_product_definition.category_code`；空则回落 `MedicationCommonConstants.DEFAULT_SERVICE_LOCATION_DISTRIBUTION_CATEGORY_CODE`（`DEFAULT`）；与 `cus_service_location_template.distribution_category_code` **精确匹配** |
| **不执行 vs 停嘱 vs 取消执行** | **不执行**：**仅**某 **planTime（occurrence）** 写 Procedure **STOPPED**，**不停** Request，**不**动发药/账单；**长期多计划点彼此独立**——跳过 08:00 **不得**清空同医嘱 16:00/22:00；临时虽仅单逻辑槽，语义仍是「该 occurrence」，不是废整条医嘱未来点。**停嘱**：Request → STOPPED；**取消执行**：见 **AS-IP-NUR-012**（CANCEL 对冲 + 联动回退） |
| **患者卡片** | 同校对 Tab，不重复 001 字段 |
| **中西药 Tab（medicineCategoryTab）** | `west`（默认）/ `tcm`；`tcm` Tab 仅中药药嘱、不展示诊疗行（**BR-IP-NUR-004-TCM-001**） |
| **麻醉申请范围（anesthesiaApplyOnly）** | `true`=仅麻醉申请（申请单号 **AA 前缀**）；`false`/空=排除麻醉申请（普通护士默认）。**无**类目 25 / 同单号池兼容。与校对/汇总领药/患者树执气泡同源（**BR-IP-NUR-004-ANES-001**） |
| **中药方展示（一方一行）** | `tcm` Tab 同 `prescription_no` 折叠；`lineRequestIds` 承载方内 req；**代表行 `reqOpenedTime` 取方内最晚**（opened 空回落 authored；仅展示，库内各味时间不变；执行写 Gate **BR-IP-EXE-024** 仍按扩行后各味自身开立时间）（**BR-IP-NUR-004-TCM-002**） |
| **整方写扩行** | 执行/不执行勾选中药方槽位 → 按 `prescription_no` 扩全方各味药行（**同 planTime**）；Gate ≤50 按扩行前计（**BR-IP-NUR-004-TCM-003**） |
| **西药成组（与门诊护士站一致）** | 列表回传 `groupId` 且同组聚邻；前端同 `groupId` 连续行合并括线展示；**合并代表行展示开立时间取组内最晚**（待执行 `defaultActualTime` 不得早于该最晚开立，必要时抬到最晚+2 分钟；与中药方 **BR-IP-NUR-004-TCM-002** 同口径）；执行/不执行写路径按同就诊同 `group_id` **整组扩行（同 planTime）**；扩行后写前若共用 `actualTime` 早于某成员开立则抬到该成员开立（临嘱同步 plan=actual）；组员各自发药/账单，**自动带**耗材/诊疗整组共用一份（**BR-IP-NUR-004-GRP-001～003**） |
| **代煎费（住院）** | 方头代煎=是时，**执行该方**按当次付数新建 `ORDER_EXECUTE` 代煎 `ChargeItem`；同方同 `planTime` 仅挂一次（**BR-IP-TCM-008**，编排本模块） |

### 1.4 本期范围与非目标

| 类别 | 内容 |
| --- | --- |
| **本期** | IF-IP-EXE-01、IF-IP-EXE-02、IF-IP-EXE-04；**首日用量执行侧**（开嘱日首次槽裁剪、`defaultActualTime`、仅首次槽换量，**BR-IP-EXE-004/032**）；**分次用量不一致分槽算量**（**BR-IP-EXE-040**，承接医生站 **BR-IP-ORD-037**）；**口服一盒一点剩余展示与待执行按天抑制**（**BR-IP-EXE-038**，与医生站改量 **IF-IP-DOR-28** 协同） |
| **非目标** | 取消执行写路径（**AS-IP-NUR-012**）；修改 occurrence_time；UI「首日执行」筛选开关后端；**按周 / 小时间隔（Q8H 等）**（频次域后续批次）；长期无 rateCode；账户余额/费别 |

### 1.5 领域清单（聚合 + 职责 + DM 路径）

| 聚合（域） | 职责摘要 | DM 路径 |
| --- | --- | --- |
| **MedicationRequest / ServiceRequest** | 已校对医嘱、生效窗、长临 | `DM-MED-003` / `DM-WKF-004` |
| **InpatientOrderLookupLoader（住院医嘱列表骨架）** | 执行列表骨架 | `ServiceRequestQueryService / MedicationRequestQueryService 等（DM §6.2）` |
| **Frequency** | 计划点展开（**daily + 隔日**；其余周期类型随 DM 补全） | `DM-CUS-008` |
| **Procedure** | 执行事实；occurrence/recorded | `DM-CLN-004` |
| **MedicationDispense** | 执行时 FIFO 新建（长临同构，可跨批多行） | `DM-MED-002` |
| **ChargeItem** | 执行时新建 **ORDER_EXECUTE**（长临同构） | `DM-ADM-011` |
| **ServiceRequest（派生）** | 用法绑诊疗执行复制 | `DM-WKF-004` §6.1.13 |
| **DeviceRequest / DeviceDispense** | 绑耗材执行 | `DM-WKF-003` / DeviceDispense DM |
| **ServiceDeviceTemplate** | 绑定模板只读 | `DM-CUS-*`（模板域） |
| **InventoryItem** | 执行前库存 Gate 只读（可用量汇总） | 库存读查询服务 |
| **MedicationDispense（读）** | 全局待发预占只读 | `listDispenseExeReserve` |
| **Encounter** | 可见性校验 | `DM-ADM-002` |

### 1.6 业务规则（BR-*）

| 规则 ID | 描述 | 绑定 IF |
| --- | --- | --- |
| **BR-IP-EXE-001** | 入参与可见性：同 **BR-IP-AUD-001** | 01、02、04 |
| **BR-IP-EXE-002** | 列表基线：`doctor-prescription` + `MED/SERVICE_REQUEST` + `status_enum ∈ {VERIFIED, STOPPED}`；停嘱后仍展示，状态文案「已停嘱」；**皮试检查** companion SR 与**皮试药**（`skin_test_flag=true`）均**须进**执行列表（操作费由本 IF 执行计费，与其它诊疗临嘱同轨；临床结果登记见 **AS-IP-NUR-011**）；**皮试药行**已登记时列表须回传 **`skinTestOutcomeCode`/`skinTestOutcomeText`** 并在医嘱内容列跟在药名后展示阴/阳性（与 **BR-IP-ORD-016** 同源） | 01 |
| **BR-IP-EXE-003** | 长期医嘱：`rate_code` 必填，否则剔除 | 01 |
| **BR-IP-EXE-004** | 列表频次展开（**仅 IF-IP-EXE-01**）：① 展开窗左界 = 开立 `req_opened_time` 所在**自然日 00:00:00**（药/诊/材同列；空则回落 `req_authored_time`；**禁止**用 `effective_dose_start`/`check_time` 作 `start`）；右界 = `min(stop_time, executionDeadline)`。② 调用 `computeExecutionTimePoints` 后，对**开嘱自然日**相对开嘱时刻做首次槽裁剪（**BR-IP-EXE-032** / **BR-IP-ORD-030⑥**）：有已过点 → 首次=`最近已过点`、更早点丢弃、开嘱后点保留；尚无已过点 → 当日点全留；已过当日最后点 → 当日仅留最后点。③ **禁止**写死频次时钟；**禁止**再以「开立日 00:00 全展开不加裁剪」充当首日量补洞 | 01 |
| **BR-IP-EXE-005** | 长期（**仅列表**）：在 **BR-IP-EXE-004** 展开窗内调用 `computeExecutionTimePoints(rateCode, start, end)`；**须含隔日**（QOD）；**禁止**护士站 App 自行 `plusDays(1)` 硬编码 | 01 |
| **BR-IP-EXE-032** | **首日用量（首次用量）执行侧**（承接 **BR-IP-ORD-030⑥**）：① `planTime` 仍为频次点，**不新建** occurrence；开嘱日首次 pending 槽列表回传 `defaultActualTime`=**开立时刻 + 2 分钟**（跨自然日钳开立日 `23:59:59`），写侧默认 `actualTime` 同口径（等同帮护士改执行时分）；**临时**单槽 planTime 仍锚开立，`defaultActualTime`/写侧默认同延后。② **仅**该首次槽在药品 `first_dose` 有值时 Dispense/Charge/库存 Gate 用量用首日量+单位；`first_dose` **已是最小单位**（开立侧由计量换算落库），与常规长嘱 `quantity`/`unit_code` **同口径、同执行路径**，执行侧**不再**做计量→拆零换算；其余槽：有 `quantity_schedule` 按 **BR-IP-EXE-040** 取段，否则用常规 `quantity`/`unit_code`。③ 空 `first_dose` → 首次槽用量仍用常规单次量或 schedule 首段（有 schedule 时），但**裁剪与 defaultActualTime 规则仍适用**。④ 发药/账单/防重锚 `planTime` **不变**。⑤ **停嘱日末槽对称易用（非换量）**：已停嘱时，停嘱自然日最后一个 `planTime ≤ stop_time` 的 pending 槽回传 `defaultActualTime`=停嘱时刻（写侧同口径）；停嘱早于当日首个频次点 → 当日无点；同槽既是开嘱日首次又是停嘱日末 → **开嘱优先**；**不**新建 occurrence、**不**改换量规则 | 01、02 |
| **BR-IP-EXE-040** | **分次用量不一致 · 执行算量**（承接 **AS-IP-DOR-003 · BR-IP-ORD-037** / **DM-MED-003 · BR-MRQ-035**）：① **无痛切换**：`quantity_schedule`（及成对 `dose_schedule`）**空** → `InpatientMedicationRequestExecutionMeasureHelper` 等**仍只读** `quantity`/`unit_code`（及首次槽 `first_dose`），与上线前行为一致。② **有值**：将逗号串解析为正数列表；对当日（及跨日同频次序）有序 `planTime` 槽，第 i 槽临床拆零量取第 i 段（1-based 与早→晚点对齐；跨自然日重复使用同一日序列）；`unit_code` 不变。③ **首次槽优先**：若该槽同时命中 **BR-IP-EXE-032** 且 `first_dose` 有值 → **仍用首日量**，不读该段 schedule。④ **一盒一点 / 拆零下限（BR-IP-EXE-035/036）**：列表累加与写路径抬量的「单槽临床拆零量」改为来自②的分槽量，再走原切批/抬量算法；**禁止**用单一 `quantity`×日次数估算。⑤ **查询展示（IF-IP-EXE-01）**：长期药行回传 `doseSchedule`/`quantitySchedule`（与医生站同源）；有值时剂量列可拼序列；**不**改停嘱/不执行/取消执行/出院校验槽位集合规则本身。⑥ **非法串**：开立侧应已拒；执行侧若遇段数不足则该槽回落 `quantity` 并记可观测告警（产品接受兜底，不静默用 0） | 01、02 |
| **BR-IP-EXE-035** | **住院长嘱不可拆 · 一盒一点**（设置 `inpatientStandingOrderSplittable`）：① 值为 `1`/缺省 → 与可拆现逻辑一致（按日频次槽逐点执行）。② 值为 `0` 且**同时**满足：**长期药品**、目录 **`injectFlag≠true`（药典非输液）**、目录 `packageUnitCode ≠ splitUnitCode`、用法展示名以「口服」开头（含口服缓释/控释等口服类）→ 启用一盒一点；**输液目录一律不启用**（即使用法误开口服）。③ **IF-IP-EXE-01 列表**：在 **BR-IP-EXE-004/032** 之后，按各槽发药量换算拆零累加；须**刚好等于**目录 `packageUnitRatio` 才切一盒；筛选 `executionDeadline` 落在第 N 盒 → 回传第 1～N 盒各**盒首 planTime**（每盒一个锚点，**禁止**展盒内全部频次点）；**同时最多一个 pending 锚点**（上一盒未执行/取消/不执行完前不出下一盒）；Tab 过滤用常规 `planTime ≤ executionDeadline`（**禁止**为盒尾 bypass deadline）。④ **IF-IP-EXE-02 写路径**：符合②时本槽 **`quantity=1`、`unitCode=packageUnitCode`**（对齐临嘱开一盒）→ **一条 Procedure、一条 Charge、一条 Dispense**；取消执行/不执行仍锚该单点。⑤ **停嘱**硬顶：不越过 `stop_time` 硬凑满盒；触及停嘱仍未凑满当前盒 → **仅回传已完整盒首**（无则空）；探测轮次耗尽仍未切出完整盒 → **空锚点收口**（`StandingOrderPackageBatchPlanTimeResolver#finishPackageBatchWhenCannotExtend` / `emptyResolvedPackageBatch`）；**禁止**降级可拆把开嘱～停嘱的日频次点再拆出来；已执行盒首由 Procedure 并集保留展示。⑥ 单槽超量、累加无法整除、缺包装比、单位换算失败、非口服类/包装拆零同码 → **降级可拆**。⑦ 出院门禁 ATD-03/05（`listLongTermStoppedPendingPlanTimes` / **BR-IP-ATD-011①′**）与 **Q-IP-EXE-001 / BR-IP-NUR-005** 同源启用一盒一点；硬顶空解析 = 无 pending = **不拦**出院；**禁止**再按可拆日频次点误拦已整包装执行的停嘱（含改量停旧嘱）。⑧ **口服剩余与按天抑制**：命中②时叠加 **BR-IP-EXE-038**（剩余展示 + 剩余够用的自然日不出下一盒 pending）；写路径④不变 | 01、02 |
| **BR-IP-EXE-038** | **单位领用 · 剩余计数与待执行按天抑制**（**DD-住院长嘱口服一盒一点剩余计数与改量-2026-08**；**DD-住院执行-拆零单位下限取整-2026-08 §3**）：① **不落表**，IF-IP-EXE-01 / 医生站列表 / **出院门禁 pending** / **BR-IP-NUR-005** 同源 Support。② **改量链**：沿 `based_on_table=med_medication_request` + `based_on_id` 串同就诊、同 `medication_id` 的长嘱；无 basedOn 手停重开**不**续算。③ **两种领用模式（035 优先）**：**PACKAGE**=命中 **BR-IP-EXE-035②**，领入=净 COMPLETED 盒首次数 × `packageUnitRatio`；**MIN_SPLIT**=命中 **BR-IP-EXE-036**（未叠 035），领入=净 COMPLETED 单位首槽次数 × **1**。不执行/取消不计；取消后再执行整单位重计。④ **已吃**=自首领用执行当日起按责任医嘱频次×剂量累加至 asOf；护士站 asOf=**min(列表截止日, 当前业务日)**；**观察期不扣**；改量开新后从新嘱开立日起按新量扣。⑤ **改量当天** max(旧,新) 日总量。⑥ **剩余**=领入−已吃；**下次领用日**自首领用日起扣领入，首个「当日需求 &gt; 行走剩余」之日；pending 锚该日首频次点（抑制假锚后空窗须**补回**；**补点前须认 occurrence 终态：不执行 Procedure STOPPED 与净 COMPLETED 同为终态，禁止只认已执行**，已终态不得补回假 pending）；领用日之前自然日**抑制**下一单位 pending。⑦ **展示宿主**：链尾；观察期挂已停条；从未有效领用不回填 0/0；字段名沿用 `oralPackage*`。⑧ 出院 **BR-IP-ATD-011①′** 抑制后无 pending → **不拦** | 01 |
| **BR-IP-EXE-036** | **拆零单位下限（产品默认）**（**DD-住院执行-拆零单位下限取整-2026-08**）：① 与 **BR-IP-EXE-035** 正交；命中 035 一盒一点时**不再**叠本规则。② **触发**：本槽**临床**发药量换算拆零后 `0 < split < 1`（长嘱常规单次；临嘱单槽同判写路径抬量），且**非**「输液开瓶即弃」——药典 `injectFlag=true` 默认排除列表切批/剩余；**用法展示名以「皮下注射」开头时放行**（胰岛素等，即使目录为输液）；半片等非输液目录仍按原规则。③ **IF-IP-EXE-01 列表（长嘱）**：在 **BR-IP-EXE-004/032** 之后按临床拆零累加，`>= 1` 切一单位，回传各单位**首槽 planTime**；硬顶未凑满当前单位仍回传该单位首槽；pending **同时最多一个**单位首槽；单槽 `>= 1` 或开瓶即弃输液 → 不触发、保持可拆日点。④ **IF-IP-EXE-02 写路径**：抬量后 **`quantity=1`、`unitCode=splitUnitCode`**，Charge/Dispense/库存 Gate **同源**（与列表切批正交）；药房配药期望量同抬量口径。⑤ **待执行计数（BR-IP-NUR-005）** 与列表同源切批。⑥ **剩余/改量/出院**：命中本规则列表切批时叠加 **BR-IP-EXE-038 MIN_SPLIT**（领入每次=1；改量 **IF-IP-DOR-28**；出院门禁同源抑制） | 01、02 |
| **BR-IP-EXE-023** | 写侧双轨（**长期**）：Procedure **`occurrence_time`**=planTime；发药 **`plannedDispenseTime`**=planTime；账单 Charge.**`occurrence_time`**=planTime（**逻辑**）；Procedure **`recorded_time`**=actualTime（**展示**）。**临时**：写前强制 `planTime=actualTime`，故 occurrence / recorded / 发药 / 账单 **同值**（**BR-IP-EXE-006**）。**`org_id`**=登录工作科室（与医技 TECH 侧隔离；取消对冲继承）。**禁止**写侧二次展开频次或复用 `executionDeadline` 做 Gate；允许勾选任意合法 planTime 补录/提前（不受列表 deadline 限制） | 02、04 |
| **BR-IP-EXE-030** | **实际执行时分**：默认 — 开嘱日首次槽 = **开立 + 2 分钟**（`defaultActualTime`；跨日钳开立日末）；**已停嘱**停嘱日末槽 = 停嘱时刻；其余 = `planTime`（护士不改则按此登记，**禁止**默认落点击瞬间 now）；**仅允许**与（对齐前）planTime **同一自然日**调整 HH:mm；批量统一改时分仅当所选槽位计划 HH:mm 相同，否则须分批。**临时**：对齐后 plan=actual，改不改时分两字段永远相等（默认开立+2 分钟） | 02 |
| **BR-IP-EXE-024** | 写路径时间边界：① 拒绝 `planTime` **晚于**停嘱时刻（药/诊统一 `stop_time`）；② 拒绝 `actualTime` **早于**开立时刻（优先 `req_opened_time`，空回落 `req_authored_time`；**秒级**；相等允许；缺开立字段不拦）；中药整方 / 西药成组扩行后写前可抬齐早于成员开立的共用 `actualTime`（**BR-IP-NUR-004-GRP-002**）；**不**用 deadline、当前时刻做写 Gate。前端工具栏提供「校验执行时间」只读扫选中患者当前列表全部槽位（与写 Gate 同口径，不写库；成组合并行开立取组内最晚） | 02、04 |
| **BR-IP-EXE-006** | **临时**：不展开频次；**整单仅一个逻辑槽**——待执行 `planTime`=开立时刻；存在**净** COMPLETED → **executed**；无 Procedure → pending；执行-取消对冲 → **hedged pending**（**BR-IP-EXE-008**）；末态 STOPPED→skipped；孤立 CANCEL → cancelled。**临嘱双轨收敛（写）**：执行提交前强制 `planTime=actualTime`，Procedure occurrence / recorded、发药 `plannedDispenseTime`、账单 occurrence **永远同值**（不改=开立锚点，改 HH:mm=改后时刻）。**读**：已执行槽 `planTime`=`occurrence_time`（与 recorded 同值），展示优先 `recordedTime`（缺则回落 planTime），**须**回传 `procedureId`/`operatorName` 供取消。**不执行**：仍锚开立 `planTime`，recorded=登记时刻。**「整单」≠「不执行整条医嘱」**：不执行只占用该 occurrence；长期多 planTime 仍按 **BR-IP-EXE-008** 分槽 | 01 |
| **BR-IP-EXE-007** | **长期**：Procedure 按 **occurrence_time** 分组；组内取**最后一条**为槽位最终态。**临时**：不按 occurrence 分组；取 id 最大为末态；净 COMPLETED 取**最后一条**未被 CANCEL 对冲的 COMPLETED | 01 |
| **BR-IP-EXE-008** | **长期**槽位分类：无记录→pending；COMPLETED 且未被 cancel 对冲→executed；**执行-取消对冲**（COMPLETED 已被 CANCEL 对冲，或末态 CANCEL 且 `refund_id` 非空）→ **hedged pending**：**同时**写入 `pendingSlots`（`planTime`，无 `procedureId`）与 `cancelledSlots`（末态 CANCEL 行）；孤立 CANCEL（无 `refund_id`）→ cancelled；STOPPED→skipped。**临时**：有净 COMPLETED→executed；执行-取消对冲→ **hedged pending**（双 Tab）；末态 STOPPED→skipped；孤立 CANCEL→cancelled；均无→pending。**净态判定**：长期按 occurrence 分组、组内 id 最大为末态，再结合 `CANCEL.refund_id`；多次执行/取消往返时**禁止**按最早 COMPLETED 误判 | 01 |
| **BR-IP-EXE-008R** | **废止**：取消后再执行与首次执行**同构**（**BR-IP-EXE-014/016/018**），均新建 Dispense/Charge；**禁止**挂接/激活签发侧草稿 | 01、02 |
| **BR-IP-EXE-009** | 列表装配阶段每条医嘱内部仍归并四类槽位；**响应**按 `executeTab` 仅回传当前 Tab 对应类别（其余三类为空数组） | 01 |
| **BR-IP-EXE-010** | `executeTab` 过滤**计划时间点（槽位）**是否回传；**医嘱行始终保留**；一律 `planTime ≤ executionDeadline`（含长嘱不可拆一盒一点）；**禁止** `all` Tab | 01 |
| **BR-IP-EXE-037** | **可选开嘱日收窄（仅 IF-IP-EXE-01）**：`reqOpenedTimeFrom`/`reqOpenedTimeTo` 任一非空时，按行 `reqOpenedTime` 闭区间过滤医嘱行；两端皆空原样返回；同时传须 from ≤ to（否则 Gate 拒绝）；缺开嘱时刻行保留；无行组丢弃。消费方：打印中心检查/检验/采血贴；医嘱执行页不传 | 01 |
| **BR-IP-EXE-011** | **禁止**用 `recorded_time` 做 deadline/Tab 过滤 | 01 |
| **BR-IP-EXE-012** | 列表不返回余额、费别；**须返回 bindItems**（可为空数组）；药嘱行**须返回 `groupId`**（`med_medication_request.group_id`，与医生站列表同源，未成组可为 null）；**须返回 `performerCheckOrgDisplay`（执行科室，见 BR-IP-EXE-029）**；**须返回 `orderKindDisplay`（与校对 OrderView 同源；嘱托=`category_enum=52`→「嘱托」，禁止回落「诊疗」）**；**嘱托诊疗行** `adviceDisplayName` **优先** `entrust_content`（**BR-IP-ORD-028④**；**AS-IP-NUR-003-SDD §2.5** 同源）；**长期药行须回传 `firstDose`/`firstDoseUnitCode`/`firstDoseUnitName`（OrderView 同源；前端剂量列拼「首日…」，承接 **BR-IP-ORD-030**）**；**长期药行须回传 `doseSchedule`/`quantitySchedule`（有值时剂量列可拼序列，承接 **BR-IP-ORD-037** / **BR-IP-EXE-040**）**；终态槽位操作人见 **BR-IP-EXE-031**；命中一盒一点口服时**须**按 **BR-IP-EXE-038** 回传剩余/已吃/预计领盒日相关展示字段（未命中可空） | 01 |
| **BR-IP-EXE-031** | **终态槽位操作人展示**：`executedSlots` / `skippedSlots` / `cancelledSlots` **须回传 `operatorName`**（有则前端拼「HH:mm 姓名」，同汇总领药时刻+数量）；**已执行**优先 `cln_procedure_performer` 主执行人姓名，**不执行/取消**无执行人时回落 Procedure `recorder_id` 姓名；`pendingSlots` 不回传；批量装配禁止 N+1 | 01 |
| **BR-IP-EXE-013** | 绑定项批量装载：药嘱按 **`med_medication_request.method_code`**（术语 code，与 `cus_service_device_template.item_id` 精确匹配）查 **USAGE→目标** 模板；诊嘱按 **`activityId`** 查 **ACTIVITY→目标** 模板（**仅一层**，不级联）；经 `InpatientDoctorStationAdviceOrderBindQueryService#listBindingsBySourceItemKey`；行内回填 `bindingId`/`busNo`/`specificationText`/`unitPrice`/`chargeItemDefinitionId` 等。**合管**：检验已合管 → **空**（开立与执行均不带，**AS-BAS-CFG-001 §7.7** / **BR-IP-ORD-036**）。**未合管**：挂载住院范围模板目标（**含诊疗与耗材**）；医生开立不带（**BR-IP-ORD-036**）。**麻醉申请**（方号 AA 前缀）：列表/写路径**不挂、不落** bindItems（`InpatientNurseStationAnesthesiaApplyScopeSupport#shouldSkipAutoBind`） | 01 |
| **BR-IP-EXE-013a** | **长期医嘱 bindItems 上次数量回显**：优先取上次执行自动带收费明细 **`quantityValue`**（现行 **`nurse-pricing`**；兼容历史 **`order-execute`**；纠正历史 SR 写死 1）；无账单时回落 Request **quantity**；冲销负向行跳过；**允许 0**（本趟不带出记忆；**负量**才跳过装载）；临时医嘱不覆盖模板量。写路径（IF-IP-EXE-02）：回传 `bindQuantity ≤ 0` 时 **continue**，**不**建补费行 | 01、02 |
| **BR-IP-EXE-014** | **执行 Gate**：可见性 + occurrence **净态为 pending、hedged pending 或 cancelled**（BR-IP-EXE-008；hedged pending / cancelled 允许再执行）+ `planTime` **不晚于停嘱** + `actualTime` **不早于开立**（BR-IP-EXE-024）+ 请求仍 **VERIFIED** + **库存 Gate（仅药嘱）**；绑定耗材不参与；任一失败**整批拒绝** | 02 |
| **BR-IP-EXE-025** | **取药科室模板键（仅药嘱库存 Gate）**：`organization_id` = 药嘱开立机构 `med_medication_request.org_id`（与医生站开立科室一致；**禁止**用护士登录科室或住院就诊 `organization_id` 替代）；`item_table` = `med_medication_definition`；`distribution_category_code` = 产品QueryService `category_code`，空则 `DEFAULT`（见 §1.3）；`ServiceLocationTemplateQueryService#listCandidateDistributionLocationIds` 无命中 → **EX-IP-EXE-302**（未配置候选药房） | 02 |
| **BR-IP-EXE-015** | 同一 Request 多计划点：Procedure 创建须 **`skipDuplicateRequestAnchorCheck=true`**（对齐门诊 **BR-NUR-005** 多次登记） | 02、04 |
| **BR-IP-EXE-016** | **药品（长临）**执行：每计划点 `create→start→complete` → 库存 Gate + FIFO **分段** → 每段 `generateDispenseFromExecution` → 每段新建 Charge（`ORDER_EXECUTE`，**待结算**，挂 `procedureId`/`dispense_id`）；跨批须**多行** Dispense/Charge | 02 |
| **BR-IP-EXE-026** | **药/诊嘱执行新建 Charge 金额**：`quantity_value`/`quantity_unit` 与对应 **`med_medication_request`/`wkf_service_request` 的 `quantity`/`unit_code`** 一致；`unit_price`/`total_price` 由 **`ChargeItemDefinition`**（**每包装单位**售价/批号售价，**DM-ADM-010 §1.6**）经 **`ProductDefinitionOrderChargePricingsupport** 以**包装价为唯一锚**换算至业务开立单位；**禁止**拆零锚再往回乘、默认「1 次」或复制签发模板整价（对齐 **BR-ORD-016**、**DM-ADM-011 BR-CHG-008**） | 02 |
| **BR-IP-EXE-027** | **护理级别**（`ServiceRequest.category_enum=26`）：**禁止** IF-IP-EXE-02/04 写路径（执行/不执行）；列表剔除（与转科/出院同源）；读列表行下发 **`categoryEnum`**；费用由 **AS-IP-NUR-013** 滚费任务按校对后护理级别驱动 | 01、02、04 |
| **BR-IP-EXE-028** | **出院结算轨就诊 Gate**：就诊 `status_enum ∈ {PENDING, COMPLETED, DISCHARGED}` 时**禁止** IF-IP-EXE-02/04 写路径；**仅** `IN_PROGRESS`/`ON_HOLD` 允许（`ON_HOLD` 待出院窗口仍可执行，见 **SYS-IMP-009 GAP-FLW-002**）；门禁看 **就诊态**，**不**看 `ChargeItem.PENDING_SETTLEMENT`；委托 `InpatientPendingSettlementClinicalGateSupport`（**BR-IP-FLW-008**） | 02、04 |
| **BR-IP-EXE-017** | **废止**：原临时挂接 `linkDispenseProcedure` / `activateChargeByExe`；住院改由 **BR-IP-EXE-016** 统一 | 02 |
| **BR-IP-EXE-018** | **诊疗（长临）**执行：每计划点 Procedure + **新建** Charge（`ORDER_EXECUTE`，**待结算**）；**嘱托例外见 BR-IP-EXE-033**；带子项落账见父+子同 `procedureId`/`service_id`，二次执行模板口径见 **BR-IP-EXE-041** | 02 |
| **BR-IP-EXE-041** | **执行费用模板禁止命中子项账单**：① `findChargeTemplate` 仅选 `parent_id` 为空的父行（子项与父项共用 `service_id`，取消未回干净或医技取消不回滚账单后再执行时，禁止把子项当模板）。② 药/诊新建 Charge 的 `productId`/`productTable` **以医嘱锚点为准**，模板不得覆盖。③ 防止二次执行父账品名被盖成子项目录（费用清单只展示父行时出现「没开过的项目」） | 02 |
| **BR-IP-EXE-033** | **嘱托执行不计费**（承接 **BR-IP-ORD-028⑤**）：`category_enum=52` 或活动名「嘱托」时，IF-IP-EXE-02 **正常写 Procedure**，**禁止** `createPlannedChargeItemFromOrderExecution`；费用明细读侧排除见 **AS-IP-NUR-006 BR-IP-CDT-020** | 02 |
| **BR-IP-EXE-042** | **嘱托整单只执行一次**（承接 **BR-IP-ORD-028⑦**）：① 识别同 **BR-IP-EXE-033**（类目 52 / 名「嘱托」）。② **IF-IP-EXE-01**：不论 `therapy_enum` 长/临、不论 `rate_code`，槽位与**临嘱同构**——整单仅一个逻辑槽，`planTime`=开立时刻；**禁止**按频次多日/多点展开；无 `rate_code` 的长期嘱托**仍进**列表（豁免 **BR-IP-EXE-003** 剔除）。③ **IF-IP-EXE-02**：写前 `planTime=actualTime` 双轨收敛同临嘱（**BR-IP-EXE-006**）；仍**不计费**（**BR-IP-EXE-033**）。④ **待执计数 / 出院门禁**与列表同源单槽。⑤ 列表仍可回传 `rateCode`/频次展示名（**仅展示**） | 01、02、04 |
| **BR-IP-EXE-039** | **检验/检查 LIS/PACS 外发（院点 L2）**：IF-IP-EXE-02 **整批成功后**，对本次勾选的诊疗 `SERVICE_REQUEST` 异步外发至 LIS/PACS；协议/对码/合管/补传与**门诊收费确认后** MedSync **同管线**（住院等价节点=护士执行完成，**非**门诊护士站）；**不阻断**执行响应；失败只记外发状态供手动补传；已外发幂等跳过；**禁止**写入 health 核心 AppService（院点 AOP + Support） | 02 |
| **BR-IP-EXE-034** | **长嘱皮试药跨批软提示**：执行 `therapy=LONG_TERM` 且 `skin_test_flag=true` 的药品槽位后，若本次 FIFO 段批号与该药**上次皮试登记批号**（无则与历史发放批号集合）不一致（出现新批号），响应附带 **warnings** 文案（如「{患者}的{药品}{旧批号}已用完，务必请取到新批次的{药品}后告知医生需要重新做皮试」）；**不**拒绝执行、**不**改状态；**临嘱不做**；下游重开皮试检查见 **AS-IP-NUR-011** | 02 |
| **BR-IP-EXE-019** | **绑定诊疗/耗材 = 医嘱补费同构**（`generate_source=nurse-pricing`，**based_on** 主医嘱；可进 **AS-IP-NUR-005** 已划价并冲销）：**绑定诊疗**（`activityId`）→ `createFromNursePricing` + `registerCompletedNursePricingProcedure`（occurrence=`planTime`）+ `priceByNurseStation`（**BR-IP-EXE-026** 价目）；**绑定耗材**（`deviceId`）：`unitCode` 优先 `bindItem.unitCode`，否则回落耗材目录；缺失拒绝（**EX-IP-EXE-304**）→ `createExecutedDeviceOrder`（**nurse-pricing** + **based_on** + **`performLocation`**）→ `generateDeviceDispenseFromTemporaryPath` → **`finalizeInpatientTemporaryPathDispense`**（**BR-IP-PRC-019**；**不**参与药嘱库存 Gate）→ **`priceByNurseStation`**（Charge.`procedure_id`=**主医嘱 Procedure**，供取消按槽冲销；有实发批号时**禁止**优先复用绑定项 `chargeItemDefinitionId`）。**禁止**再写 `order-execute` 派生绑定费（历史存量取消执行仍兼容） | 02 |
| **BR-IP-EXE-020** | 写路径入参携带列表回传 **bindItems**；与主医嘱 executeTimes 对齐 | 02 |
| **BR-IP-EXE-021** | **不执行**：入参**不得**带已有 `procedureId`；**每个勾选 planTime 各自**写 Procedure **STOPPED**（occurrence=planTime，recorded=当前）；Gate 同 BR-IP-EXE-023/024（occurrence 防重 + 不晚于停嘱）；净态须为 **pending 或 hedged pending**（**BR-IP-EXE-008**）；**不**写 Dispense/Charge/Request 停嘱；**禁止**因同 Request 已有其它 occurrence 的 STOPPED/COMPLETED 而拒绝或清空未勾选计划点 | 04 |
| **BR-IP-EXE-022** | 写操作批量：**同一事务**；部分失败策略首期**整批回滚** | 02、04 |
| **BR-IP-NUR-004-ANES-001** | **麻醉申请范围**：IF-IP-EXE-01 入参 `anesthesiaApplyOnly`；`true` 仅保留申请单号 **AA 前缀** 的麻醉申请；`false`/空排除之。**无**类目 25 / 同单号池兼容。实现：`InpatientNurseStationAnesthesiaApplyScopeSupport`；与 **AS-IP-NUR-003 / 010 / 001** 对齐 | 01 |
| **BR-IP-NUR-004-TCM-001** | 列表 `medicineCategoryTab`：`west`→非中药；`tcm`→中药；`tcm` Tab 排除 `SERVICE_REQUEST` | 01 |
| **BR-IP-NUR-004-TCM-002** | `tcm` Tab 同 `prescription_no` **一方一行**；行内 `tcmFlag`/`prescriptionNo`/`lineRequestIds`/`sufferingFlag`/`chineseHerbsDoseQuantity`；**代表行展示开立时间取方内最晚**（写扩行后 **BR-IP-EXE-024** 仍按各味自身开立时间） | 01 |
| **BR-IP-NUR-004-TCM-003** | 执行/不执行写：勾选中药方槽位 → 按 `prescription_no` **扩全方**各味药行（同 `planTime`） | 02、04 |
| **BR-IP-NUR-004-GRP-001** | **西药成组展示**：药嘱行回传 `groupId`；列表同组聚邻；前端同 `groupId` 连续行**合并为一行括线展示**（对齐门诊护士站）；槽位取代表行（组员同频次）；**合并代表行展示开立时间取组内最晚**（opened 空回落 authored；仅展示，库内各成员时间不变；待执行 `defaultActualTime` 若早于该最晚开立则抬到最晚+2 分钟）；中药方折叠行不参与成组合并 | 01 |
| **BR-IP-NUR-004-GRP-002** | **西药成组写扩行**：执行/不执行勾选组内任一行槽位（或前端合并代表行 + `lineRequestIds`）→ App 按同就诊同 `group_id`（非中药）**扩全组**各成员行（**同 planTime**）；优先消费列表回传 `lineRequestIds`；Gate ≤50 按扩行前计；**执行写路径**扩行并补默认/`alignTemporary` 后调用 `liftExpandedExecuteActualTimesNotBeforeOpened`——若共用 `actualTime` 早于某成员开立则抬到该成员开立（须与 `planTime` 同自然日；临嘱同步 `planTime=actualTime`）；写 Gate **BR-IP-EXE-024** 仍按各成员自身开立秒级校验 | 02、04 |
| **BR-IP-NUR-004-GRP-003** | **成组自动带共用**：成组扩行后各组员**各自**写 Procedure/发药/账单；**自动带** bindItems（用法绑诊疗/耗材）**整组仅落一次**（挂扩行代表行；`based_on` 取该代表 req；绑定耗材 `package_id` 可与主药嘱 `package_id` 对齐）；**禁止**同组同 planTime 对同一 binding 重复派生 | 02 |
| **BR-IP-TCM-008** | 方头代煎=是：执行该方按**当次付数刷新**代煎费（同方同 `planTime` void 未结算旧项后新建）；前端可回传 `lineRequestIds`/`prescriptionNo` 减轻扩行查库 | 02 |
| **BR-IP-EXE-029** | **执行科室展示**：取医嘱**校对人**（`performer_check_id`）岗位绑定**病区** `adm_location`，回显其 **`organization_id` 对应科室名**；护士岗位通常无直属 `org_id`；多病区时优先与患者就诊病区一致；经共享 `PractitionerCheckOrgDisplayResolver` + `NurseStationOrderViewDisplayCore` 装配，**禁止**执行模块重复实现 | 01 |

---

## 2. 页面与接口总览（契约概览）

| IF 编号 | 用户动作 | 接口语义 | 读/写 | 关键规则 | 状态变化 | 涉及领域 | 查询服务 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| IF-IP-EXE-01 | 切换 Tab/长临嘱/截止时间、点查询；打印中心可按开嘱日传区间 | 批量查询医嘱执行列表（槽位 + bindItems；中西药 Tab、麻醉申请范围、中药方折叠、西药成组聚邻；可选开嘱日收窄；一盒一点口服剩余展示；长期药 schedule 回显） | 读 | BR-IP-EXE-001～013、**013a**、**031**、**037**、**035**、**038**、**040**；**BR-IP-NUR-004-TCM-001～002**；**BR-IP-NUR-004-GRP-001**；**BR-IP-NUR-004-ANES-001** | 无 | InpatientOrderLookupLoader · InpatientOrderListSkeleton + Frequency + Procedure + 绑定模板 | InpatientOrderLookupLoader · InpatientOrderListSkeleton + 域查询 |
| IF-IP-EXE-02 | 勾选**待执行**或**取消执行** Tab 槽位（及绑定项）→ 执行 | 批量**执行**医嘱计划点（中药整方 / 西药成组扩行 + 代煎费；成组自动带共用） | 写 | BR-IP-EXE-014～020、022、023、024、**013a**、**041**；**BR-IP-NUR-004-TCM-003**；**BR-IP-NUR-004-GRP-002～003**；**BR-IP-TCM-008** | Procedure COMPLETED；长临新建 Dispense/Charge；绑定派生；代煎费 | Procedure + Med/Service + Dispense + Charge + Device | — |
| IF-IP-EXE-03 | — | **已合并至 AS-IP-NUR-012 IF-IP-CXL-01** | — | — | — | — | — |
| IF-IP-EXE-04 | 勾选待执行槽位 → 不执行 | 批量登记计划点**不执行**（中药整方 / 西药成组扩行） | 写 | BR-IP-EXE-021、022、023、024；**BR-IP-NUR-004-TCM-003**；**BR-IP-NUR-004-GRP-002** | Procedure STOPPED | Procedure | — |

---

## 3. 缺口跟踪（Gap）

> **架构决议（2026-05-22）**：执行写路径 = Procedure + 发药/账单联动 + **药嘱**执行前库存 Gate；绑定项列表/写路径已落地（绑定耗材**不**做库存 Gate/扣减）。**取消执行**不入本期 Gap，待汇总领药模块。
>
> **架构决议（2026-05-22，频次）**：护士站**查询与执行**的长期计划点，**一律委托** `FrequencyQueryService#computeExecutionTimePoints`；**隔日（QOD）为护士站必须正确消费的频次类型**；`computeExecutionTimePoints` 按 **DM-CUS-008 §9.1** 在频次域补全，**护士站不得复制展开算法**。

| Gap ID | 缺口类型 | 文件 + 章节 | 缺什么 / 不一致点 | 关闭条件 | 影响 IF | 优先级 | 状态 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| GAP-IP-EXE-001 | 查询服务 | `ServiceRequestQueryService / MedicationRequestQueryService 等（DM §6.2）` §3 | 缺 **Q-IP-EXE-001** 批量就诊执行列表骨架查询（`encounterIds` + `VERIFIED` + 来源/类型 + 可选 `therapyEnum`） | QueryService §3 + `ServiceRequestQueryService#listInpatientNurseStationExecuteOrders` 已实现 | IF-IP-EXE-01 | P0 | **已关闭** |
| GAP-IP-EXE-002 | DM 能力 | `DM-CLN-004` §6.1 + `ProcedureDomainService` | 缺 **不执行**写命令（`skipProcedureRecord` → 终态 **STOPPED**） | `SkipProcedureRecordCommand` + `Procedure#skip()` + `ProcedureRecordSkippedEvent` 已实现 | IF-IP-EXE-04 | P0 | **已关闭** |
| GAP-IP-EXE-003 | DM 能力 | `DM-MED-002` §6.1 + `MedicationDispenseDomainService` | 缺**临时医嘱执行挂接**已有发放单命令（写 `procedureId`、`plannedDispenseTime`、推进 **PREPARATION**） | `LinkDispenseProcedureCommand` + `linkDispenseProcedure` + `DispenseProcedureLinkedEvent` 已实现 | IF-IP-EXE-02 | P0 | **已关闭** |
| GAP-IP-EXE-004 | DM 能力 / 代码-文档不一致 | `DM-ADM-011` §6.1.4 + `ChargeItemDomainService` | 缺长期执行按 plan 新建 Charge（`ORDER_EXECUTE` + `procedureId`）及临时执行激活专用写命令 | `ActivateChargeByExeCommand` + `activateChargeByExe` + `createPlannedChargeItemFromOrderExecution` 已实现 | IF-IP-EXE-02 | P0 | **已关闭** |
| GAP-IP-EXE-005 | QueryService / 域查询 | 库存 DM + 发药读查询 | 缺执行前库存 Gate **域读**能力：全局待发预占 + 可用量汇总（FIFO 编排不在域 Gap） | `listDispenseExeReserve`（全局、目录占用状态集）+ `sumAvailableInventory` 已实现；FIFO dry-run 与 Gate 编排写入 **AS-SDD IF-IP-EXE-02**，**不入 Gap** | IF-IP-EXE-02 | P1 | **已关闭** |
| GAP-IP-EXE-006 | DM 能力 / 代码-文档不一致 | `DM-CUS-008` §9.1 + `FrequencyQueryService#computeExecutionTimePoints` | 实现仅「每日 + day_times」，未按 `cycle_type` / `day_interval` 展开隔日 | `computeExecutionTimePoints` 按 DM §9.1 字段驱动（日/短间隔/周）已实现；单元测试随 IF-IP-EXE-02 联调补 | IF-IP-EXE-01、02、04 | P0 | **已关闭** |

> **说明**：
> - 槽位拆分编排留在护士站 App 层，**计划点生成**统一调用 **FrequencyQueryService**；**禁止**护士站 App 复制展开算法。
> - **库存 Gate**：域层仅提供「可用量汇总 + 全局待发预占」两聚合读能力；**禁止** QueryService 跨聚合编排；FIFO 自动分配在 **IF-IP-EXE-02 AppService / `NurseStationExecutionInventoryAssembler`** 实现（**仅药嘱**；绑定耗材库存由其他页面处理），详见 **`AS-IP-NUR-004-住院护士站医嘱执行-SDD.md` §3.3**。
> - **发放类别（2026-05-22）**：库存 Gate 解析 `ServiceLocationTemplate` 时，`distribution_category_code` **必须**来自 `ProductDefinitionQueryModel.category_code`（与住院医生站 FIFO 一致），**禁止**写死 `DEFAULT` 或忽略产品目录分类；配置种子/运维数据时须保证 `cus_service_location_template.distribution_category_code` 与药品 `category_code` 对齐。
> - 绑定项**只读**可复用医生站 `InpatientDoctorStationAdviceOrderBindQueryService` / `ServiceDeviceTemplateQueryService`，**不入 Gap**；护士站须批量挂载到列表行。
> - 绑定**诊疗/耗材**写路径按医嘱补费同构：`createFromNursePricing` / `createExecutedDeviceOrder`（`nurse-pricing`）+ `priceByNurseStation`；编排落 `NurseStationOrderExecuteBindPricingOrchestrator`，**不单独入 Gap**。
> - **IF-IP-EXE-03** 取消执行已迁至 **AS-IP-NUR-012 IF-IP-CXL-01**。

---

## 4. 接口编排说明书（业务步骤）

### IF-IP-EXE-01 医嘱执行列表

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 左侧已选患者；用户设置 `executionDeadline`、`executeTab`、`therapyType` 或点**查询**；打印中心可另传 `reqOpenedTimeFrom`/`reqOpenedTimeTo` |
| **主路径步骤** | 校验可见性（BR-IP-EXE-001）→ **Q-IP-EXE-001** 加载骨架（BR-IP-EXE-002～003）→ 批量补全请求明细（含药嘱 **`groupId`**、**`basedOn*`**、**`doseSchedule`/`quantitySchedule`**）→ **OrderView** 明细索引与展示预热（`NurseStationOrderViewListAssembler`，见 **AS-IP-NUR-003-SDD §2.5**）→ 计算展开窗（BR-IP-EXE-004①）→ 长期频次展开 + **开嘱日首次槽裁剪**（BR-IP-EXE-004②/032）/ 临时单槽（BR-IP-EXE-005～006）→ **按槽临床量**（有 `quantity_schedule` 则 **BR-IP-EXE-040** 取段，否则 `quantity`；首次槽优先 `first_dose`）→ **长嘱一盒一点/拆零下限**（BR-IP-EXE-035 优先；否则 BR-IP-EXE-036：单槽拆零&lt;1 → 单位首槽；pending 至多一个锚点；累加用分槽量）→ **口服剩余计数与按天抑制下一盒 pending**（**BR-IP-EXE-038**）→ 批量 Procedure 按 occurrence 归并（BR-IP-EXE-007～008）→ 组装四类槽位（首次 pending 填 `defaultActualTime`）+ 拷贝展示内核至行 DTO（BR-IP-EXE-009/012）→ **批量解析 bindItems**（BR-IP-EXE-013）→ **长期上次量覆盖**（BR-IP-EXE-013a：现行 **`nurse-pricing`** / 历史 **`order-execute`** 账单优先，无则 Request 量；允许 0）→ 按 Tab+deadline **收窄槽位**（BR-IP-EXE-010）→ **终态槽位批量回填 `operatorName`**（BR-IP-EXE-031）→ **可选开嘱日收窄行**（BR-IP-EXE-037）→ 按 encounter 分组返回（**不查、不回传** `accountId`） |
| **关键分支** | PRN：无计划点；**QOD 隔日**：pending 槽位隔一天一个点；bindItems 无模板时 `[]` |
| **失败分支** | EX-IP-EXE-101；EX-IP-EXE-102；频次不可用 → EX-IP-EXE-201 |
| **查询侧说明** | 纯展示；写成功后 Procedure/Dispense/Charge 变更，下次查询刷新槽位 |

### IF-IP-EXE-02 执行

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 用户勾选至少一条槽位；入参含 `planTime`（occurrence 逻辑锚点）、`actualTime`（recorded，默认=planTime，仅同日可改时分）、**`accountId`（取自 IF-IP-NUR-02 挂账账户，见 BR-IP-NUR-018；服务端仍按医保优先覆盖）**；可选 bindItems |
| **主路径步骤** | Pre·写上下文（`prepareWriteContext` + `warmWritePathCatalogCaches`，**仅读路径批量/缓存**，见 **AS-SDD §3.3**）→ **Pre·挂账覆盖**（医保入院强制 `MEDICAL_INSURANCE`，纯自费个人现金户；**BR-IP-NUR-018**）→ 校验 BR-IP-EXE-001、014、023、024、**030**（可见性 + occurrence 防重 + 不晚于停嘱 + **actualTime 不早于开立** + VERIFIED + **`accountId` 非空** + **药嘱**库存 Gate + **actualTime 同日**）→ 按 `orderType` 分组 → 对每个 planTime：**Procedure** `create`（skipDuplicate）→ `start` → `complete`（**occurrence=planTime，recorded=actualTime**）→ 药品/诊疗/绑定分轨（BR-IP-EXE-016～020、**026**、**035④**、**040**；药嘱槽量经 `InpatientMedicationRequestExecutionMeasureHelper`：**有 `quantity_schedule` 按槽取段**，否则 `quantity`；首次槽优先 `first_dose`；**一盒一点**时药嘱 `quantity=1`、`unitCode=packageUnitCode`，**一条 Dispense + 一条 Charge**；Dispense `plannedDispenseTime` 与 Charge occurrence **=planTime**；月封顶/防重/汇总领药时间点均用 planTime；长期/绑定新建 Charge 经 **`ProductDefinitionOrderChargePricingsupport** 写金额）→ 同事务提交（BR-IP-EXE-022） |
| **关键分支** | 补录/提前：planTime 可为任意合法 occurrence（如 21 日、23 日），**不受**列表 `executionDeadline` 限制；同一 occurrence 净态为 **executed / skipped / 孤立 cancelled** → 拒绝；**hedged pending**（执行-取消对冲）与 **pending** 可再执行；**药嘱**模板未命中（开立机构/发放类别与 `cus_service_location_template` 不一致）→ **EX-IP-EXE-302**「未配置候选药房」；候选药房有配置但物理库存不足 → 同码「药品库存不足」；整批失败；**整批成功后**院点可异步外发检验/检查 LIS/PACS（**BR-IP-EXE-039**，不阻断本 IF） |
| **失败分支** | EX-IP-EXE-301（occurrence 已占用）；EX-IP-EXE-302（库存不足）；EX-IP-EXE-303（不可见/非 VERIFIED/**晚于停嘱**/**actualTime 早于开立**）；EX-IP-EXE-304（域写失败，整批回滚） |
| **查询侧说明** | Procedure/Dispense/Charge 事件 → InpatientOrderListSkeleton / 费用 / 发药QueryService 刷新 |

### IF-IP-EXE-04 不执行

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 用户勾选待执行槽位 → 点**不执行**；入参**无** procedureId |
| **主路径步骤** | 校验 BR-IP-EXE-001、023、024（可见性 + occurrence 防重 + 不晚于停嘱；**不做**库存 Gate）→ 对每个 planTime 调用 Procedure **不执行**写命令 → STOPPED（BR-IP-EXE-021）→ 同事务提交 |
| **关键分支** | 已有 procedureId 入参 → 拒绝；不触 Dispense/Charge；不要求 VERIFIED |
| **失败分支** | EX-IP-EXE-401（occurrence 已占用）；EX-IP-EXE-402（携带 procedureId）；EX-IP-EXE-403（**晚于停嘱**或域 STOPPED 写失败） |
| **查询侧说明** | Procedure STOPPED → 槽位迁入 skippedSlots |

---

## 5. 业务异常与用户提示

| 异常 ID | 场景 | 用户提示 | 可重试 |
| --- | --- | --- | --- |
| EX-IP-EXE-101 | 列表查询失败 | 执行列表加载失败，请稍后重试 | 是 |
| EX-IP-EXE-102 | 所选患者均不可见 | 当前患者不可用，请刷新左侧列表 | 是 |
| EX-IP-EXE-201 | 医嘱引用频次不可用 | 部分医嘱频次配置异常，请联系管理员 | 是 |
| EX-IP-EXE-301 | 执行时 occurrence 净态不可写（已净执行 / 已不执行 / 孤立 cancelled） | 该计划点已执行或已登记不执行，请刷新后重试 | 是 |
| EX-IP-EXE-302 | 库存 Gate 失败（含**未配置候选药房**：开立机构 + 发放类别未命中模板；或候选药房**库存不足**） | 未配置候选药房 / 【医嘱号】在某某药房库存不足（批量合并提示） | 是 |
| EX-IP-EXE-303 | 写操作不可见/非 VERIFIED/**planTime 晚于停嘱**/**actualTime 早于开立** | 无权限操作、医嘱状态已变更，或计划点晚于停嘱时间 / 执行时间早于开立时间 | 是 |
| EX-IP-EXE-304 | 执行域写失败 | 医嘱执行失败，请稍后重试 | 是 |
| EX-IP-EXE-401 | 不执行时 occurrence 已有终态 | 该计划点已执行或已登记不执行 | 是 |
| EX-IP-EXE-402 | 不执行携带 procedureId | 不执行不支持对已执行记录操作 | 否 |
| EX-IP-EXE-403 | 不执行域写失败或**晚于停嘱** | 不执行登记失败，或计划点晚于停嘱时间 | 是 |
| EX-IP-EXE-307 | 就诊已待结算/已结算/已离院 | 患者已办理出院或已结算，不可变更医嘱及费用 | 否 |

---

## 6. 事件语义与消费方

| 事件 | 来源 | 影响 |
| --- | --- | --- |
| `ProcedureRecordCompletedEvent` | IF-IP-EXE-02 | 槽位 pending→executed；ProcedureQueryService 查询 |
| `ProcedureRecordSkippedEvent` | IF-IP-EXE-04 | pending→skipped |
| `DispenseProcedureLinkedEvent` | IF-IP-EXE-02 临时药品 | 草稿发放单挂 Procedure → PREPARATION |
| `MedicationDispenseCreatedEvent` / 挂接推进事件 | IF-IP-EXE-02 长期/临时 | 发药单状态；药房待办 |
| `ChargeItemActivatedByExecutionEvent` / 护士划价/执行新建事件 | IF-IP-EXE-02 | 住院**待结算**费用行增加；刷新展示余额 |
| `ServiceRequestExecutedCreatedEvent` | IF-IP-EXE-02 绑定诊疗 | 派生诊疗事实 |
| `*VerifiedEvent` | 校对（**AS-IP-NUR-003**） | 新进入执行列表 |
| 停嘱事件 | 医生站 | 生效窗缩短 |

---

## 7. 非功能性需求

| 维度 | 要求 |
| --- | --- |
| **性能** | 列表：批量 encounter + Procedure IN；bindItems 批量模板；库存 Gate 批量 IN。**写路径 IF-IP-EXE-02**：`prepareWriteContext` 按 `orderId:orderType` **去重**加载 OrderInfo / Procedure 历史；按就诊预热费用行；`warmWritePathCatalogCaches` 批量预热产品读模型、定价行、临时药嘱发放单（`WriteContextBundle` 缓存，未命中回落单条查询）；**禁止**同医嘱多 `planTime` 重复单条查库；**不改变** Gate/Cmd 业务规则 |
| **一致性** | Procedure 对冲与 **AS-OP-NUR-001** **BR-NUR-005** 一致；执行与发药/账单**同事务** |
| **时间** | 全链路 `LocalDateTime`；**长期双轨**：逻辑用 planTime（Procedure.occurrence / 发药 plannedDispenseTime / 账单 occurrence），展示用 recorded=actualTime；**临嘱**：写前 `planTime=actualTime`，逻辑字段与 recorded **同值**；**列表** deadline 与 Tab 用 **occurrence**；**写 Gate** 仅 occurrence 防重 + 晚于停嘱 + actual 同日，**不用** deadline / 当前日 |
| **事务** | IF-IP-EXE-02/04 一批建议 ≤50 槽位；失败整批回滚（首期） |

---

## 8. 测试要点（业务视角）

| 类别 | 要点 |
| --- | --- |
| **列表** | 切换 Tab 医嘱行不变、仅计划点类别变化；四类槽位字段同构，非当前 Tab 为空数组；bindItems 无配置为空数组；药嘱行含 **groupId**（同组聚邻，前端括线合并）；**已执行/不执行/取消执行**槽位标签含 **`operatorName`**（如 `08:00 张三`），待执行仅时刻（**BR-IP-EXE-031**） |
| **隔日 QOD** | 长期 QOD 医嘱 pending 槽位**隔一天**一个（非每天）；TID 与 QOD 同窗对比点数不同 |
| **开嘱日首次槽（已过点）** | 21 日 12:00 开立、频次含 08:00/12:00/18:00（示例时钟，以配置为准）、截止 24 日 → 21 日 pending 含 **12:00（首次，defaultActualTime=12:02）+18:00**，**不含 08:00**；22～24 日各点照常 |
| **开嘱日尚无已过点** | 21 日 07:30 开立、当日首点 08:00 → 21 日 pending **含全部当日点**；首次=08:00，`defaultActualTime`=07:32 |
| **开嘱已过当日最后点** | 21 日 23:59 开立、当日末点 18:00 → 21 日 pending **仅 18:00**（首次），`defaultActualTime`=23:59:59（+2 跨日钳开立日末） |
| **停嘱日末槽** | 16 日停嘱 14:00、频次 08/12/18 → pending 含 08/12（无 18）；12:00 为末槽，`defaultActualTime`=14:00；停嘱 07:00 → 当日无点 |
| **首日量仅首次槽** | 同上首次槽且 `first_dose` 有值 → 仅该槽换量；同日后续点与次日点：有 `quantity_schedule` 按段，否则常规 `quantity` |
| **分次用量不一致** | 日二次 schedule=`1,0.5`（拆零）：早槽用 1、晚槽用 0.5；一盒一点/拆零下限按各槽量累加；空 schedule 与上线前一致；停嘱/不执行/出院校验槽位集合不变 |
| **写 Gate** | 22 日 8:00/16:00 各执行一次后不可重复；23 日 8:00 仍可执行；21 日补录可执行；planTime 晚于停嘱拒绝；**actualTime 须与 planTime 同一自然日**（时可不同，BR-IP-EXE-030）；不改则 actual=plan |
| **长嘱不可拆一盒一点** | `inpatientStandingOrderSplittable=0` + 长期口服 + 非输液目录 + 包装≠拆零：TID 每槽 1 片/盒 24 片 → pending 仅盒首锚点（非盒内全部点）；同时最多一个 pending；执行后 Dispense/Charge 各 1 条、`quantity=1`+包装单位；下一盒锚点在上盒执行/取消/不执行后出现；非口服或包装=拆零或 **`injectFlag=true`（用法误开口服亦同）** → 与可拆一致；Tab/deadline 不 bypass 盒尾 |
| **口服剩余 / 改量续算** | 盒 10 片 QD：执行后理论 3 天 → 已吃 3 剩余 7；改量 BID 后剩 7 时前 3 个新量服药日**无**下一盒 pending，第 4 日首点 pending，执行后剩余 9；观察期（已停未改量）不扣、剩余挂已停条；取消盒执行后未领重计；无 basedOn 手开新嘱不继承剩余（**BR-IP-EXE-038**） |
| **写路径性能（回归）** | 同一长期医嘱一次勾选多 `planTime`（如 12 点）执行：Gate/Cmd 结果与优化前一致；Procedure/Charge/Dispense 条数与状态不变；仅读库次数下降 |
| **执行·药嘱** | 长临每 plan 新建 Dispense(PREPARATION，可 FIFO 多段) + Charge(**待结算**)；Procedure occurrence=planTime |
| **执行·诊疗** | 长临新建 **待结算** Charge |
| **库存 Gate** | **药嘱**按开立机构 + 产品 `category_code` 命中模板；未命中或预占后不足整批失败；通过后可写；绑定耗材不参与 |
| **绑定** | 勾选 bindItems 时按 **nurse-pricing 补费同构**落诊疗/耗材（可进医嘱计费已划价并冲销）；绑定耗材 → **`finalizeInpatientTemporaryPathDispense`**（开关 **关** PREPARED / **开** COMPLETED+扣库）；批次末合并扣库；**成组药嘱**：同组同 planTime 自动带只落一次（**BR-IP-NUR-004-GRP-003**）；**BR-IP-EXE-013a**：长期列表上次量以自动带账单 `quantityValue` 优先（nurse-pricing / 历史 order-execute）；写侧 `≤0` 不落费 |
| **不执行** | STOPPED 槽位；Request 不停；无 Dispense/Charge |
| **边界** | 非 VERIFIED/无 rateCode 不出现；cancel 对冲不出现在 executed；**hedged pending** 同时出现在 pending 与 cancelled Tab，不出现在 executed |
| **执行-取消对冲** | 某 planTime 执行后再取消 → **hedged pending**：待执行 Tab 与取消执行 Tab **均**展示该 planTime，两 Tab 勾选均可再执行；净值与 **BR-NUR-005** 一致 |
| **出院结算轨 Gate** | `ON_HOLD` 可执行；`PENDING`/`COMPLETED`/`DISCHARGED` → **EX-IP-EXE-307**；`IN_PROGRESS` + 大量待结算费用仍可执行 |

---

## 9. 关联文档与变更历史

### 9.2 变更历史

| 版本 | 日期 | 变更摘要 |
| --- | --- | --- |
| v2.26 | 2026-06-18 | **BR-IP-EXE-006/008/014/021**：执行-取消对冲净态 **hedged pending**（**BR-NUR-005** 净值仍待执行）；读侧 **pending+cancelled 双 Tab** 展示；写 Gate 允许 pending / hedged pending / 孤立 cancelled 再执行；对齐 `NurseStationOrderExecuteListReadAssembler` |
| v2.25 | 2026-06-17 | **架构决议**：住院长临执行**同构**——执行时 FIFO 多段新建 Dispense/Charge；**废止 BR-IP-EXE-017/008R**（`linkDispenseProcedure`/`activateChargeByExe`）；对齐开立 **AS-IP-DOR-003 v1.14**；**AS-SDD v2.9** |
| v2.24 | 2026-06-17 | **BR-IP-EXE-008R** 取消后再执行分轨（**已由 v2.25 废止，统一新建**） |
| v2.21 | 2026-06-11 | **BR-IP-EXE-019** 绑定耗材 **`unitCode`** 回落链 + `CreateExecutedDeviceOrderCommand.unitCode` |
| v2.20 | 2026-06-10 | **IF-IP-EXE-03** 取消执行迁至 **AS-IP-NUR-012 IF-IP-CXL-01**；§1.1/1.3/1.4/§2/§3 交叉引用更新 |
| v2.19 | 2026-06-08 | **BR-IP-EXE-019** 绑定耗材改为 **`finalizeInpatientTemporaryPathDispense`**（**BR-IP-PRC-019**）；对齐护士划价/门诊护士站开关两轨 |
| v2.18 | 2026-06-06 | **BR-IP-EXE-013/019**：用法绑定改 **method_code** 查模板；绑定耗材 **based_on** + **performLocation** |
| v2.15 | 2026-05-28 | 六维对账：`NurseStationOrderExecuteAppService` 三方法与 SDD §3.1（IF-IP-EXE-01/02/04）对齐 |
| v2.14 | 2026-05-28 | ReadModel 废止后对齐 zxplat 实现 |

## 9. 域层已实现能力对照（Gap 关闭后）

> **架构决议汇总**（2026-05-22）：① 长期计划点**只**调 `FrequencyQueryService#computeExecutionTimePoints`（字段驱动，隔日=`day_interval`）；② 库存 Gate **App 层**组装，**禁止** QueryService 跨聚合；③ 护士**不**手选库位/批号，FIFO 自动分配；④ 待发预占**全局**（不限就诊），状态=`NurseStationExecutionConstants.DISPENSE_RESERVE_STATUSES_FOR_EXECUTION_GATE`（**宽于** IF-DOR-11 目录预减，含 `SUMMARIZED` 等）；⑤ **住院执行**（2026-06-17）：长临统一 `generateDispenseFromExecution` + `createPlannedChargeItemFromOrderExecution`，FIFO 可跨批多行；⑥ **发放类别**与住院医生站目录一致：Gate 用 `ProductDefinitionQueryModel.category_code` 匹配 `cus_service_location_template.distribution_category_code`；⑦ **列表行展示字段**委托 **`NurseStationOrderViewListAssembler`**（**AS-IP-NUR-003-SDD §2.5**），执行模块仅叠加槽位与 bindItems。

| 能力 | 域入口 | DM / SDD | 用途 IF |
| --- | --- | --- | --- |
| 执行列表骨架 | `ServiceRequestQueryService#listInpatientNurseStationExecuteOrders` | `InpatientOrderListSkeleton` **Q-IP-EXE-001** | 01 |
| 计划点展开 | `FrequencyQueryService#computeExecutionTimePoints` | `DM-CUS-008` §9.1 | 01 |
| 不执行写 | `ProcedureDomainService#skipProcedureRecord` | `DM-CLN-004` §6.1.6 | 04 |
| 执行完成写 | `createProcedureRecord` → `start` → `complete` | `DM-CLN-004` §6.1.1～4 | 02 |
| 发药（长临） | `generateDispenseFromExecution`（FIFO 多段，含 `lotNumber`） | `DM-MED-002` §6.1.7 | 02 |
| 新建账单（长临） | `createPlannedChargeItemFromOrderExecution` | `DM-ADM-011` §6.1.4d | 02 |
| 绑定诊疗（补费同构） | `NurseStationOrderExecuteBindPricingOrchestrator#priceBindActivityAsNurseSupplement`（`createFromNursePricing` + Procedure + `priceByNurseStation`） | `DM-WKF-004` / `DM-ADM-011` / `DM-CLN-004` | 02 bind |
| 绑定耗材（补费同构） | `NurseStationOrderExecuteBindPricingOrchestrator#priceBindDeviceAsNurseSupplement`（`createExecutedDeviceOrder` nurse-pricing → `finalizeInpatientTemporaryPathDispense` → `priceByNurseStation`，Charge.`procedure_id`=主 Procedure） | `DM-WKF-003` / `DM-WKF-007` / **BR-IP-PRC-019** | 02 bind |
| 可用量汇总 | `InventoryItemQueryService#sumAvailableInventory` | `DM-WKF-008` §6.2.12 | 02 Gate |
| 全局待发预占 | `MedicationDispenseQueryService#listDispenseExeReserve` | `DM-MED-002` §6.2.10 | 02 Gate |
| 共享常量 | `NurseStationExecutionConstants` | `domain.share.constants` | 02 预占状态集 |

**待 AS-SDD 成码（应用层）**：**已完成**——见 **`AS-IP-NUR-004-住院护士站医嘱执行-SDD.md` v1.2**。

---

## 10. 关联文档与变更历史

| 类型 | 文档 |
| --- | --- |
| **AS-SDD** | `AS-IP-NUR-004-住院护士站医嘱执行-SDD.md` |
| **App（写编排）** | `NurseStationOrderExecuteBindPricingOrchestrator`（绑定补费同构）· `DeviceDispenseNurseWorkflowOrchestrator` · `InpatientTcmDecoctionExecuteOrchestrator` |
| **module-support（读侧）** | `application.inpatient.nursestation.{feature}.support` · 执行列表 Loader/Assembler |
| 关联 AS | `AS-IP-NUR-001`；`AS-IP-NUR-003`；`AS-IP-NUR-012`（IF-IP-CXL-01）；`AS-IP-DOR-003`（绑定查询、**IF-IP-DOR-28 改量**）；`AS-OP-NUR-001`（BR-NUR-005） |
| 设计决策 | `health/设计决策/DD-住院长嘱口服一盒一点剩余计数与改量-2026-08.md`；`DD-住院执行-拆零单位下限取整-2026-08.md` |
| 查询服务 | `ServiceRequestQueryService / MedicationRequestQueryService 等（DM §6.2）` |
| DM | `DM-CLN-004`、`DM-MED-002`、`DM-MED-003`、`DM-WKF-003`、`DM-WKF-004`、`DM-WKF-007`、`DM-ADM-011`、`DM-CUS-008`、`DM-WKF-008` |
| Domain SDD | 上列 DM 对应 `DomainModel-SDD/*-SDD.md` |
| UI | `openhis-pro-ui/.../inpatient/nurse-station/order-execute/` |

| 版本 | 日期 | 摘要 |
| --- | --- | --- |
| v2.71 | 2026-10-16 | **代码先行回写**：**BR-IP-EXE-036/038**：输液目录默认开瓶即弃，但用法「皮下注射」放行拆零下限切批/剩余（胰岛素）；035 仍一律排除输液；对齐 **AS-SDD v2.55** / **DD** |
| v2.70 | 2026-10-15 | **代码先行回写**：**BR-IP-EXE-035/036/038**：药典 `injectFlag=true`（输液）排除一盒一点与拆零下限列表切批/剩余续算（开瓶即弃；用法误开口服也不按箱）；写路径抬整拆零仍可；对齐 **AS-SDD v2.54** / **DD** 两份 |
| v2.69 | 2026-10-15 | **代码先行回写**：**BR-IP-EXE-042 / BR-IP-ORD-028⑦**：嘱托执行无视频次、整单一次（与临嘱单槽同构）；列表/待执计数/写双轨收敛；对齐 **AS-SDD v2.53** |
| v2.68 | 2026-10-15 | **代码先行回写**：**BR-IP-NUR-004-GRP-001/002**——西药成组合并展示开立取组内最晚、待执行 `defaultActualTime` 不得早于该最晚；写扩行后 `liftExpandedExecuteActualTimesNotBeforeOpened` 抬齐后开成员（与中药方 **TCM-002** 同口径防 **BR-IP-EXE-024**）；对齐 **AS-SDD v2.52** |
| v2.67 | 2026-10-13 | **BR-IP-NUR-004-ANES-001 纠偏**：麻醉识别改为 **AA 前缀**；**BR-IP-EXE-013** 明确麻醉申请不走自动带；对齐 **AS-SDD v2.51** / **AS-IP-NUR-003 v1.31** |
| v2.66 | 2026-10-13 | **BR-IP-NUR-004-ANES-001**：IF-IP-EXE-01 `anesthesiaApplyOnly`（默认排除麻醉申请）；对齐 **AS-SDD v2.50** / **AS-IP-NUR-003 v1.30** |
| v2.65 | 2026-10-12 | **剂量展示**：IF-IP-EXE-01 `dosageDisplay`/`dailyDoseDisplay` 随 OrderView——输液拆零&lt;1 主显计量单位；对齐 **AS-IP-NUR-003 v1.29** / **AS-SDD v2.49** |
| v2.64 | 2026-10-11 | **代码先行回写**：**BR-IP-EXE-019/013a** 执行自动带诊疗/耗材改为 **nurse-pricing 医嘱补费同构**（可进医嘱计费已划价并冲销）；取消执行按槽冲销（**AS-IP-NUR-012**）；历史 order-execute 兼容 |
| v2.63 | 2026-10-07 | **BR-IP-EXE-041**：执行费用模板跳过带子项子账单；药/诊 product 禁模板覆盖；代码先行回写 |
| v2.62 | 2026-09-29 | **BR-IP-EXE-023**：Procedure.`org_id`=登录工作科室（与医技 TECH 侧隔离）；对齐协同 DD / **DM-CLN-004** / **AS-IP-NUR-012** |
| v2.61 | 2026-09-23 | **BR-IP-EXE-040**：分次 `quantity_schedule` 分槽算量（无痛回落）；**BR-IP-EXE-012/032** 交叉；IF-IP-EXE-01/02 主路径；对齐 **AS-IP-DOR-003 v1.50 · BR-IP-ORD-037** / **DM-MED-003 v1.3.38**；合并远端 **BR-IP-EXE-038⑥** 领用日补点认不执行终态 |
| v2.60 | 2026-09-23 | **BR-IP-EXE-038⑥**：预计领用日补点须认不执行（STOPPED）与净已执行为终态，禁止只认 COMPLETED 再补假 pending（出院 LONG_PENDING_SLOTS）；对齐 **DD** / **AS-SDD v2.45** / `allowsNextClaimPendingReinjection` |
| v2.59.1 | 2026-09-22 | **BR-IP-EXE-013 纠偏**：未合管诊嘱/药嘱执行挂载住院模板**含耗材与诊疗**；合管仍空；开立恒不带（**BR-IP-ORD-036**）；废止「执行侧过滤 deviceId」；对齐 **AS-BAS-CFG-001 §7.7** / **AS-SDD v2.45** |
| v2.57 | 2026-09-14 | **BR-IP-EXE-038** 扩展覆盖拆零下限（MIN_SPLIT 领入=1）；列表/待执/**出院门禁**同源抑制；交叉 **BR-IP-EXE-036⑥** / **AS-IP-NUR-002**；对齐 **DD** 两份与 **AS-SDD v2.43** |
| v2.56 | 2026-09-11 | **BR-IP-EXE-035⑤⑦**：停嘱硬顶/探测耗尽空锚点收口（禁止日点 FALLBACK）；出院门禁改 `listLongTermStoppedPendingPlanTimes` 同源；改量停旧嘱无假 pending 不拦；交叉 **AS-IP-NUR-002 v2.21**（停嘱校对自动 skip 已于 **AS-IP-NUR-003 v1.22** 废止）；对齐 **AS-SDD v2.42** |
| v2.55 | 2026-09-07 | **BR-IP-NUR-004-TCM-002**：中药方折叠代表行展示开立时间改为方内最晚（不改库；**BR-IP-EXE-024** 写 Gate 仍按各味自身开立时间）；对齐护士校对 **AS-IP-NUR-003 v1.20** |
| v2.59 | 2026-09-16 | **BR-IP-EXE-032**：明确 `first_dose` 落库为最小单位，执行与常规长嘱同路径（承接开立侧计量→拆零换算） |
| v2.58 | 2026-09-16 | **BR-IP-EXE-012**：执行列表长期药回传 `firstDose`/`firstDoseUnitCode`/`firstDoseUnitName`（OrderView 同源）；前端剂量列拼「首日…」 |
| v2.54 | 2026-09-02 | **住院不自动带耗材**：开立 IF-IP-DOR-23 不走合管、过滤器材目标；执行 **BR-IP-EXE-013** 诊嘱过滤耗材；对齐 **AS-BAS-CFG-001 §7.7** / **AS-SDD v2.41** |
| v2.53 | 2026-08-31 | **BR-IP-EXE-039**：IF-IP-EXE-02 成功后院点 L2 异步 LIS/PACS 外发（对齐门诊收费 MedSync；不入 health 核心）；院点文档见 `project-H22010402403/.../orderexecute` |
| v2.52 | 2026-08-19 | **BR-IP-EXE-038⑦**：未领盒不回填剩余展示字段；拆零单位字典名；对齐 **DD** / **AS-IP-DOR-003 v1.43** |
| v2.51 | 2026-08-19 | **BR-IP-EXE-038** / **BR-IP-EXE-035⑧**：口服一盒一点剩余计数（不落表）、按天抑制下一盒 pending、改量 `based_on` 链与观察期冻结；列表展示字段；对齐 **DD-住院长嘱口服一盒一点剩余计数与改量-2026-08**、**AS-IP-DOR-003 BR-IP-ORD-032 / IF-IP-DOR-28**；**AS-SDD v2.38**（成码落地） |
| v2.50 | 2026-08-17 | **挂账口径**：IF-IP-EXE-02 服务端按 **BR-IP-NUR-018** 覆盖 `accountId`（医保入院挂医保户）；对齐 **AS-IP-NUR-001 v1.35** |
| v2.51 | 2026-09-02 | **BR-IP-EXE-002**：IF-IP-EXE-01 皮试药行回传 **`skinTestOutcomeCode`/`skinTestOutcomeText`**；执行列表医嘱内容跟在药名后展示阴/阳性（与 **BR-IP-ORD-016** 同源） |
| v2.50 | 2026-08-14 | **BR-IP-EXE-037**：IF-IP-EXE-01 可选 `reqOpenedTimeFrom`/`reqOpenedTimeTo` 按开嘱日收窄行（打印中心检查/检验/采血贴）；医嘱执行页不传则与既有口径一致；对齐 **AS-SDD v2.36** |
| v2.49 | 2026-08-13 | **BR-IP-EXE-035⑦**：出院门禁待执行槽位与 **Q-IP-EXE-001 / BR-IP-NUR-005** 同源启用一盒一点（废止「出院保持可拆」）；对齐 **AS-SDD v2.35** |
| v2.49 | 2026-08-17 | **BR-IP-EXE-019**：绑定耗材 Charge 按 FIFO 主发放批号定 LOT_NUMBER_PRICE；有实发批号禁止优先绑定项 definitionId |
| v2.48 | 2026-08-11 | **BR-IP-EXE-036**：产品默认拆零单位下限（列表单位首槽 + 写路径抬到 1 拆零）；与 035 院点一盒一点正交；对齐 **DD-住院执行-拆零单位下限取整-2026-08** |
| v2.47 | 2026-08-07 | **BR-IP-EXE-035⑤**：停嘱硬顶未凑满当前盒时**禁止**降级可拆日点，仅回传已完整盒首（无则空）；已执行盒首仍由 Procedure 并集展示；对齐 **AS-SDD v2.33** |
| v2.46 | 2026-08-05 | **BR-IP-EXE-032/030**：开嘱日首次槽（含临嘱单槽）`defaultActualTime`/写侧默认由「=开立」改为「开立 + 2 分钟」（跨日钳开立日末）；频次 `planTime` 锚点不变；对齐 **AS-SDD v2.32** |
| v2.45 | 2026-08-05 | **BR-IP-EXE-035 修订（一盒一点）**：废止「整盒展满全部频次槽」；触发=长期+包装≠拆零+口服类；列表仅回传盒首锚点、pending 至多一个、Tab 用常规 deadline；**IF-IP-EXE-02** 写路径整包装 `quantity=1`+`packageUnitCode`；对齐 **AS-SDD v2.31**、**AS-IP-NUR-010 v1.13**（废止 **BR-IP-SUM-017**） |
| v2.44 | 2026-07-27 | **BR-IP-EXE-024/014**：执行写 Gate 增补 `actualTime` 不得早于开立（秒级，缺字段不拦，**EX-IP-EXE-303**）；前端工具栏「校验执行时间」只读扫选中患者当前列表；对齐 **AS-SDD v2.29** |
| v2.43 | 2026-07-26 | **BR-IP-EXE-013a**：长期 bindItems 上次量真源=**ORDER_EXECUTE 账单优先**于派生 Request；**0 合法**；写侧 ≤0 不派生；对齐 **AS-SDD v2.28** |
| v2.42 | 2026-07-22 | **长嘱不可拆（BR-IP-EXE-035）**：设置 `inpatientStandingOrderSplittable=0` 时 IF-IP-EXE-01 长期药品按包装拆零量刚好切盒展满；停嘱/凑不齐降级可拆；写路径不变；前端右键增「全选/取消全选当前医嘱时点」；对齐 **AS-SDD v2.27** |
| v2.41 | 2026-07-22 | **临嘱双轨收敛**：修订 **BR-IP-EXE-006/023/030** 与 §1.3——临时执行写前强制 `planTime=actualTime`，occurrence/recorded/发药/账单同值；长期仍逻辑≠展示双轨；对齐 `alignTemporaryExecutePlanAndActualTimes` / 前端临嘱写项；**AS-SDD v2.26** |
| v2.39 | 2026-07-17 | §1.3 **UI 时间列**：待执行优先 `defaultActualTime`（首次槽=开嘱时刻）；已执行优先 `recordedTime`；勾选/deadline/写 Gate 仍锚 `planTime`；下游汇总领药/费用明细同双轨 |
| v2.39 | 2026-07-18 | **BR-IP-EXE-033 / BR-IP-ORD-028⑤**：嘱托执行只写 Procedure、禁止建 Charge；**BR-IP-EXE-012** 增补 `orderKindDisplay=嘱托`；对齐 **AS-IP-NUR-006 BR-IP-CDT-020** / **AS-SDD** |
| v2.40 | 2026-07-22 | **BR-IP-EXE-034**：长嘱皮试药执行跨批软提示（响应 `warnings`）；对齐 **AS-IP-NUR-011 v1.5** |
| v2.39 | 2026-07-22 | **BR-IP-EXE-002**：皮试检查 companion SR **纳入**待执行（废止住院执行互斥）；对齐 **AS-IP-NUR-011 v1.4** / **AS-IP-NUR-001** |
| v2.38 | 2026-07-17 | **BR-IP-EXE-002**：执行列表**仅排除**皮试检查 companion SR；**皮试药回待执行**（**v2.39 废止排除**） |
| v2.37 | 2026-07-17 | **成组自动带共用**：**BR-IP-NUR-004-GRP-003**——成组扩行后 bindItems 仅挂代表行；组员各自发药/账单；绑定耗材 `package_id` 可对齐主药嘱；对齐 **AS-SDD v2.21** |
| v2.36 | 2026-07-17 | **西药成组一起执行**：新增 **BR-IP-NUR-004-GRP-001/002**（列表聚邻+前端括线；执行/不执行按 `group_id` 整组同 planTime 扩行，对齐门诊护士站展示与中药整方写扩行）；`expandMedRequestIdsForNurseWrite` |
| v2.35 | 2026-07-17 | **不执行单点澄清**：修订 **BR-IP-EXE-006/021** 与 §1.3——不执行仅占勾选 **planTime/occurrence**；长期其它频次点仍 pending；临时「整单」=单逻辑槽非整条医嘱废点；终态槽 `planTime` 固定 occurrence（废止临时把 recorded 写入 planTime）；对齐读组装 `toTemporaryTerminalSlotDto` / 前端禁止加载后自动全选 |
| v2.34 | 2026-07-16 | **首日用量执行侧**：修订 **BR-IP-EXE-004**（展开窗 00:00 + 开嘱日首次槽裁剪）；新增 **BR-IP-EXE-032**（`defaultActualTime`/仅首次槽换量）；修订 **BR-IP-EXE-030**；废止「开立日全展开不加裁剪」补洞；承接 **AS-IP-DOR-003 BR-IP-ORD-030⑥**；对齐 **AS-SDD v2.18** |
| v2.33 | 2026-07-16 | **BR-IP-EXE-031**：已执行/不执行/取消执行槽位回传 **`operatorName`**，UI 时刻后拼接操作人（同汇总领药时刻+数量）；对齐 **AS-SDD v2.17** |
| v2.32 | 2026-07-16 | **BR-IP-EXE-030** + 修订 **023**：执行双轨——逻辑（Procedure.occurrence / 发药 plannedDispenseTime / 账单 occurrence / 汇总领药）=planTime；展示（recorded）=actualTime；默认 actual=plan；仅同日改时分；UI 单列按 Tab；测试要点/时间口径与 **AS-IP-NUR-010 v1.8** 对齐 |
| v2.31 | 2026-07-16 | **BR-IP-ORD-028④**：嘱托行 `adviceDisplayName` 优先 `entrust_content`；与校对列表共用 **OrderView** + **`InpatientEntrustOrderGateSupport#resolveServiceAdviceDisplayName`**；对齐 **AS-IP-NUR-003-SDD v1.3.4** |
| v2.30 | 2026-07-16 | **BR-IP-EXE-029**：IF-IP-EXE-01 列表行回传 **`performerCheckOrgDisplay`（执行科室）**；校对人岗位病区 → 上级科室；共享 `PractitionerCheckOrgDisplayResolver`；对齐 **AS-SDD v2.14** |
| v2.29 | 2026-07-11 | **BR-IP-EXE-028**：就诊 `PENDING/COMPLETED/DISCHARGED` 禁止 IF-IP-EXE-02/04；**EX-IP-EXE-307**；对齐 **SYS-IMP-009 BR-IP-FLW-008** |
| v2.28 | 2026-06-20 | **support 落点对齐**：`DeviceDispenseNurseWorkflowsupport、`InpatientTcmDecoctionExecutesupport → `module.orchestration.nursestation`；护士站读侧 → `module.querysupport.nursestation`；详见 **AS-SDD v2.12** |（已迁至 application.inpatient.nursestation.{feature}.support）（已迁至 application.shared.nursestation-device-dispense）（已迁至 application.shared / feature support（见 DISSOLUTION.md））
| v2.27 | 2026-06-20 | 对齐 **AS-IP-DOR-006** 下游：`medicineCategoryTab`、中药方一方一行、执行/不执行整方扩行、**BR-IP-TCM-008** 代煎费；**BR-IP-NUR-004-TCM-001～003** |
| v2.26 | 2026-06-18 | **BR-IP-EXE-006/008/014/021**：执行-取消对冲 **hedged pending**；pending+cancelled 双 Tab；对齐 **AS-SDD v2.10** |
| v2.17 | 2026-05-29 | **BR-IP-EXE-006/007/008**：临时嘱整单按净 COMPLETED 判定已执行；已执行 Tab `planTime` 取最后一次净执行 `recorded_time`；禁止与开立时刻并列 pending |
| v2.16 | 2026-05-28 | **BR-IP-EXE-019**：绑定耗材 Charge 同步 **BR-IP-EXE-026**；§4 主路径去除「仅补 definitionId」旧叙述 |
| v2.15 | 2026-05-28 | **BR-IP-EXE-026** 扩展至诊疗执行/绑定诊疗；统一 **ProductDefinitionOrderChargePricingSupport** 比例换算，禁止默认 1/「次」 |
| v2.14 | 2026-05-28 | **BR-IP-EXE-026**：药嘱执行新建 Charge 按 `quantity`/`unit_code` 与定价行单位槽比例换算金额，禁止复制签发模板「次」+ 包装整价 |
| v2.13 | 2026-05-24 | **BR-IP-EXE-004/005**：列表频次展开左边界改为开立自然日 00:00:00（含开立当日频次时点）；**AS-SDD v1.8** |
| v2.12 | 2026-05-24 | `createExecutedDeviceOrder` 域签名改为 `List<Long>`（批量契约，执行 bind 单条 `get(0)`）；对齐 **DM-WKF-003** v1.2.2 / **AS-IP-NUR-005** |
| v2.11 | 2026-05-24 | 列表行展示委托共享 **`orderview`**；**AS-SDD v1.7** §2.5；与校对/划价父医嘱同口径 |
| v2.10 | 2026-05-23 | **executeTab 语义修正**：Tab 过滤**计划时间点（槽位）**而非医嘱行；切换 Tab 医嘱行不变；移除 `all` Tab；修订 **BR-IP-EXE-009/010** |
| v2.9 | 2026-05-22 | IF-IP-EXE-01 列表行补充药嘱 **`groupId`**（`MedicationRequestListQueryModel`，与住院医生站列表同源） |
| v2.8 | 2026-05-22 | **账户与费用**：**仅 IF-IP-NUR-02** 列表查/回传 `accountId`；IF-IP-EXE-02 由前端回传（执行模块不查账户）；长期/绑定新建 Charge 补全 `definitionId`（定价查询，与 IF-IP-DOR-11 同构） |
| v2.7 | 2026-05-22 | **库存 Gate 模板键**：新增 **BR-IP-EXE-025**；发放类别与住院医生站对齐（`ProductDefinitionQueryModel.category_code` → `distribution_category_code` 精确匹配，禁止写死 `DEFAULT`）；§1.3 / EX-302 / 测试要点同步 |
| v2.6 | 2026-05-22 | **写 Gate 口径澄清**：planTime=occurrence 仅防重+停嘱边界；executionDeadline **仅列表**；actualTime 与 planTime 解耦；新增 **BR-IP-EXE-024**；修订 BR-014/023 与 EX-301/303/401/403 |
| v2.5 | 2026-05-22 | **库存 Gate 收窄为仅药嘱**；绑定耗材写路径落地（DeviceRequest/Dispense/Charge），**不参与**库存 Gate/扣减；对齐应用层成码 |
| v2.4 | 2026-05-22 | 全量文档对齐：§9 域层能力对照；DM/SDD/RM 同步；新增 **AS-SDD** |
| v1.0 | 2026-05-22 | 首版：仅 IF-IP-EXE-01；槽位拆分 |
| v2.0 | 2026-05-22 | 增 IF-IP-EXE-02/04；bindItems 列表+写路径；执行联动发药/账单+库存 Gate；暴露 Gap 002～005；取消执行延后 |
| v2.1 | 2026-05-22 | 护士站**须支持隔日（QOD）**计划点；频次展开委托 DM-CUS-008；新增 GAP-IP-EXE-006、BR-IP-EXE-023 |
| v2.2 | 2026-05-22 | 架构决议：库存 Gate 全局预占 + App 层 FIFO；禁止跨聚合 QueryService 编排 |
| v2.3 | 2026-05-22 | **GAP-IP-EXE-001～006 全部关闭**；§1.3 库存 Gate 口径对齐域读 + App FIFO；QueryService补 **Q-IP-EXE-001** |


