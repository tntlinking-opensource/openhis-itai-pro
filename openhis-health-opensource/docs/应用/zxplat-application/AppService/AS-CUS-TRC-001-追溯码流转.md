# 追溯码流转 · 应用服务设计（AS）

> **文档职责**：全院追溯码**跨场景编排权威**——主表现态 + 子表流水、追溯码单位、院内在院与库位双轨查询、与各库房/发药 AS 衔接；**不绑定单一 UI 页面**。
>
> **编排权威**：动账路径**必须**调用 `TraceNumberDomainService#recordTraceRecords`（或 `recordTraceRecordsFromLineItems`）：**INSERT 子表 + UPDATE 主表**（同事务）；**禁止**各场景平行写主/子表。
>
> **领域模型**：`DM-CUS-010` v2.0.5（`cus_trace_number` + `cus_trace_number_flow`；包 `com.zxplat.domain.custom.tracenumber`）。
>
> **编码**：AS-CUS-TRC-001 · **版本**：v2.10 · **日期**：2026-08-19

---

## 1. 场景概述

### 1.1 目标与边界

| 维度 | 说明 |
| --- | --- |
| **在内** | 药库进货初始化主档 → 库房/发药/退药 **recordTraceRecords**；`trace_unit_code`；主表**在院+拆零剩余**；子表**库位记录**；双轨读法；**批量扫码身份反查 + 前端行级匹配**；住院核发 vs 护士分配/退药边界 |
| **在外** | 国家平台解析；医保 350x；订货单不建码 |
| **涉及域** | **TraceNumber** / **TraceNumberRecord**；**SupplyRequest**；**MedicationDispense**；**MedicationDefinition**（仅应用/support 预加载换算源） |

### 1.2 角色与关键动作

| 角色 | 关键动作 |
| --- | --- |
| **药库收货员** | 进货录码（贴码单位取药典，单据不选）→ 同意建主档 + 首条 `HOSPITAL_IO` IN 流水 |
| **库管/药房** | 入出库/调拨同意 → 记子表；调拨成对 `INTERNAL_TRANSFER` |
| **药房** | 发药 `HOSPITAL_IO` OUT；退药 `HOSPITAL_IO` IN |
| **病区护士** | 正向分配仅写 `MedicationDispense.trace_no`；退药发起从原发放行**查出**码带入待退行，**不写**主/子表 |
| **运维** | 主表查在院码；子表查库位码与审计 |
| **药房/库管/护士** | 批量扫码 → **IF-TRC-21** 反查身份 → 前端按 `medicationId + lotNumber` 自动填行 |

### 1.3 术语与口径约定

| 术语 | 口径 |
| --- | --- |
| **主表 `cus_trace_number`** | **一码一行**（盒码/箱码均 1 行）。`in_hospital_status_enum`、`remaining_split_quantity`、`trace_unit_code`、**`trace_unit_ratio`（建码快照）**。**无 `location_id`** |
| **子表 `cus_trace_number_flow`** | **只 INSERT**。`quantity`+`unit_code`（一次业务一行）；`location_id` **仅记录**；库位码列表**子表聚合** |
| **流水类别** | `HOSPITAL_IO`：**动在院**八类（进货/退货/发药/退药/领用/退领/报损/盘亏，见 **DM §2.4.1**）；`INTERNAL_TRANSFER`：**仅流转**（调拨/请领入库/退库等，**DM §2.4.2**） |
| **在院翻转** | `NOT_IN_HOSPITAL` 须 `HOSPITAL_IO` OUT 后 **`remaining_split_quantity ≤ 0`**；部分 OUT 不耗尽时仍在院 |
| **全院在院** | 主表：`IN_HOSPITAL` 且 `remaining_split_quantity > 0`；**不看 loc**（**BR-TRC-006**） |
| **库位有哪些码** | 子表按 `location_id` 聚合净拆零量 > 0 |
| **单位** | 子表 `unit_code` ≠ `trace_unit_code` 时写侧换算拆零后再动**主档剩余**。`trace_unit_ratio` **只服务追溯域**（应扫码数 / 剩余盖住）。**库存账面** `wkf_inventory_item` 发退扣加走发放 **操作单位×目录五类倍数**（**AS-OP-PHA-001 BR-OPP-017**），禁止用贴码倍数覆盖同码「盒」 |
| **逗号串展开（每码数量）** | **入库/期初**（硬 Gate）：异阶按拆零前填实入（半盒允许）；应扫数 = ceil(行拆零/每码拆零)。**消耗/流转**（发药 OUT 等，软边界）：已扫集合内按 **剩余升序**分摊，**每码实扣须落子表**；盖住即可，不再要求码个数 = ceil。同阶每码 ±1 贴码单位 |
| **业务表暂存** | 逗号串 `trace_no`，VARCHAR(50000)（**TRC-WIDE-R-001**） |
| **住院双路** | 汇总核发（AS-IP-PHA-002）写 `SupplyDelivery.trace_no` + 子表 OUT；护士分配（AS-IP-NUR-015）仅写发放行 |
| **退药** | **不新造码**；护士 IF-TRC-18/19 带出原码 → 药房确认 IF-TRC-17 记 `HOSPITAL_IO` IN |
| **扫码匹配** | **后端**：码 → 主表身份（`item_id` + `lot_number` + `trace_unit_code` + 在院态）；**前端**：身份与当前页候选行匹配填位；**保存**：各场景写 IF 复读 Gate |
| **扫码可选** | 全院各场景**均可不扫**；不扫不挡进销存/发药/清床等主流程，仅影响追溯主表/流水是否写入。入库后中途流转不扫为常态（**BR-TRC-021**） |
| **取码优先级** | **有码批**：`location_id` 仅优先（本库子表净量 &gt; 0）；本库无码 → 全院主表在院且剩余 &gt; 0（不看 loc）。本库匹配不到只说明码已不在该库/已出院，不是业务异常。**无码批**：不强制取码（**BR-TRC-006/020/022**） |
| **有码批 / 无码批** | **有码批**：主表该 `item_id+lot_number` **已存在任一未删除主档**。发药/出库等**消耗侧**：未建档/品批不匹配**仅软提示**，业务行可存码；**主档存在且剩余 ≤ 0 硬拦**；核发写流水须盖住，否则软跳过（**BR-TRC-006/020**）。**无码批**：发药仅写业务行 `trace_no`，**不**建主档、**不**写流水。**码身份硬 Gate**：进货 + 发药耗尽例外 |
| **退供应商取码** | **禁止**回放进货单当时扫的码串。IF-WHS-RT-13 预填须按品批查**当前剩余码**（本库优先且主档在院剩余&gt;0，空则全院在院剩余；截取受源库可用约束）；无码批则空串、不校验（**BR-TRC-022** / **AS-INV-WHS-005**） |
| **匹配键** | 行级自动匹配统一用 **`medicationId`（或 `itemId`）+ `lotNumber` + `traceNo`**；`traceNo` 已由反查确认，前端主要用前两维对齐行 |
| **贴码单位来源** | **药典** `trace_unit_code`+`trace_unit_ratio`（可游离五类单位）；单据不采；`supply.trace_unit_code` 废弃 |

### 1.4 本期范围与非目标

| 类别 | 内容 |
| --- | --- |
| **本期** | IF-TRC-01～21；主子表模型；全院写场景映射；批量扫码身份反查 |
| **非目标** | 护士写主/子表；主表存 loc；订货单建码 |

### 1.5 领域清单

| 聚合 | DM |
| --- | --- |
| **TraceNumber** + **TraceNumberRecord** | `DM-CUS-010` v2.0.5 |
| **MedicationDefinition / DeviceDefinition**（贴码单位） | `DM-MED-001` / `DM-ADM-008` |
| **MedicationDispense** | `DM-MED-002` |

### 1.6 业务规则（BR-*）

| 规则 ID | 描述 | 绑定 |
| --- | --- | --- |
| **BR-TRC-001** | 动账必须 `recordTraceRecords`；同事务 INSERT 子表 + UPDATE 主表 | 写 IF |
| **BR-TRC-002** | 子表只 INSERT；主档可 UPDATE 在院/剩余 | 写 IF |
| **BR-TRC-003** | 仅 **§2.4.1 白名单**业务记 `HOSPITAL_IO`；其余库房动账记 `INTERNAL_TRANSFER` | 写 IF |
| **BR-TRC-004** | `INTERNAL_TRANSFER` 同 `bus_no` 成对 OUT+IN；不改主表剩余 | IF-TRC-09 等 |
| **BR-TRC-005** | 新建码的 `trace_unit_code` **取自药典**（药品：`trace_unit_code`+`trace_unit_ratio` 必填；耗材可空，有码时须已维护）。进货/期初单据**不采集**贴码单位；**`wkf_supply_request` 已删除 `trace_unit_code` 列**。同意入库写入主档后，后续单据/发药**只读继承主档**；药典变更只影响之后新建的码 | IF-TRC-05 |
| **BR-TRC-006** | **消耗/流转侧**（发药/出库等）：未建档、品批不匹配均**软**——不挡保存配药/核发主流程；写追溯盖不住或码不可用则**跳过写主子表**（**BR-TRC-019/021**）。**例外硬拦**：主档已存在且 `remaining_split_quantity ≤ 0` 时禁止再扫/再提交（门诊/住院发药，`assertMasterCodesNotExhaustedForDispense`）。**进货**（IF-TRC-05）仍硬 Gate（应扫数 = ceil；码须可建档入库）。**期初**扫码可选、不做码数硬 Gate（**AS-BAS-INV-001 BR-007**）。有码批取码优先级（本库净量优先否则全院在院）仍用于读侧/预填。**无码批**见 **BR-TRC-020** | 写 IF |
| **BR-TRC-007** | 库位 Gate（可选）：子表聚合该 `location_id` 净量 > 0 | IF-TRC-07 等 |
| **BR-TRC-008** | 退药/退领复用原码；`HOSPITAL_IO` IN。**禁止新建主档**；IN 量须**镜像**对应 OUT 每码实扣（`recordReturnInLines` / `recordDeptIssueReturnMirroredIn`），**禁止**按当前剩余或剩余先出重算 | IF-TRC-11、13、16、17 |
| **BR-TRC-009** | 护士分配/退药带出：仅 `med_medication_dispense.trace_no` | — |
| **BR-TRC-010** | 汇总核发写 OUT；不写患者行 `trace_no` | IF-TRC-15 |
| **BR-TRC-011** | 业务表草稿可暂存；同意/核发/确认事务内写主/子表 | 写 IF |
| **BR-TRC-012** | 住院退药：IF-TRC-18 查正向码 → IF-TRC-19 写待退行 → IF-TRC-17 药房 IN | 17～19 |
| **BR-TRC-013** | **IF-TRC-21 只读预览**：批量反查主表身份，**不落库**、不写业务行 | IF-TRC-21 |
| **BR-TRC-014** | **行级匹配在前端**；消耗侧保存配药**不**复读码身份硬 Gate（仅进货硬拦）。写流水路径按盖住/主档可用性软跳过，禁止信任预览结果 alone 当作已入账 | 写 IF |
| **BR-TRC-015** | 批量扫码单次 ≤ 50 码；**单次反查 HTTP 入参**内 `trace_no` 去重标记（`DUPLICATE_IN_REQUEST`）；批量 `IN` 查主表。**不**表示发药多明细行禁止共扫同码（见 **BR-TRC-023**） | IF-TRC-21 |
| **BR-TRC-023** | **发药/消耗扫码软硬边界**：① 弹窗「待扫」/绑码硬上限=**ceil(操作量)**（开几就是几；禁止用码倍/拆零推个数）；② 码倍只用于盖住/分摊——前端盖住左侧需求须为**真拆零**（盒×包装比；拆零同名勿×码倍；禁止同阶直接拿操作量比主档片剩余）；③ **行内**同码硬拒；④ **跨行**同码允许，跨行剩余不足仅软提示；⑤ 主档无剩余/未建档/品批不匹配→**软提示**，不挡发药。**码身份硬 Gate 仅进货**。见 **DD §3.1 / §3.4** | IF-TRC-16、发药 UI、保存配药 |
| **BR-TRC-016** | `NOT_IN_HOSPITAL` 仅当 `HOSPITAL_IO` OUT 后 `remaining_split_quantity ≤ 0`；IN 类业务剩余 > 0 翻回在院 | 写 IF |
| **BR-TRC-017** | IF-TRC-22 仅返回指定 `location_id` 下净拆零量 **> 0** 的码；固定 `item_table=medication` | IF-TRC-22 |
| **BR-TRC-018** | IF-TRC-23 列表真源为主表 + 子表聚合；流水时间筛选用子表 EXISTS | IF-TRC-23 |
| **BR-TRC-019** | **进货 IN**（IF-TRC-05）：异阶按前填实入拆零（半盒允许）；**硬 Gate** 应扫数 = ceil(行拆零/每码拆零)。**期初 IN**：同前填写侧；**不做**码数硬 Gate（扫码可选）。**发药 OUT**（IF-TRC-16）：已扫集合内 **`allocateRemainingFirstSplitForDispenseOut`**（剩余升序），每码实扣落子表；覆盖判定 **`isRemainingSplitSufficientForDispenseOut`**（不抛，盖不住跳过写 OUT）。**退药 IN**（IF-TRC-17）：镜像 OUT 每码量，**禁止**剩余先出/前填重算。**同阶**亦按剩余先出分摊（非「每码强制 ±1」） | IF-TRC-05、16、17 |
| **BR-TRC-020** | **有码批 / 无码批分叉**（写流水）：以主表是否存在该品批码为准（与在院态/剩余无关）。**有码批**：核发/出库同意时若扫码且剩余盖住 → 写主表流水；未建档/不匹配/盖不住 → **软跳过**写流水，业务行仍可存 `trace_no`；**保存配药不做码身份硬校验**。**无码批**：允许业务行录码并发药/出库；**禁止**写主表/流水（Option A）；数量与应扫数仅软提示。**全场景码身份硬 Gate 仅进货**（IF-TRC-05）。期初西药/中成药允许不扫码 | IF-TRC-16、发药 slip、期初 |
| **BR-TRC-021** | **扫码可选**：不扫不挡主流程。前端若已扫但个数不足，须**弹窗确认**后方可继续（禁止静默放行）；确认后不足行按未扫处理（不提交不完整码），主流程可走通。清床缺码同为软提示 | 发药 UI、IF-IP-NUR-ATD-06a |
| **BR-TRC-022** | **退供应商等「要用码」读侧**：有码批按本库净量优先（**且**主档 `IN_HOSPITAL`/剩余&gt;0，防流转未扫净量滞后）、空则全院在院剩余取码；截取可叠加源库可用；**不得**用历史单据 `trace_no` 快照代替当前剩余。无码批不取码、不校验 | IF-TRC-06、IF-WHS-RT-13、IF-TRC-22 |

---

## 2. 页面与接口总览（契约概览）

> 本 AS 的 IF 表示**追溯编排能力**，由各业务 AS 在写路径**嵌入调用**；读 IF 可供追溯查询页或业务 Gate 复用。

| IF 编号 | 用户/系统动作 | 接口语义 | 读/写 | 关键规则 | 数据源 |
| --- | --- | --- | --- | --- | --- |
| IF-TRC-01 | Gate：码是否在院（全院） | 查主表在院状态与拆零剩余 | 读 | BR-TRC-006 | **主表** |
| IF-TRC-02 | 轨迹明细/审计 | 按码返回子表时间序流水 | 读 | BR-TRC-002 | **子表** |
| IF-TRC-03 | 某库房有哪些码 | 子表按 `location_id` 聚合净量 > 0 | 读 | — | **子表聚合** |
| IF-TRC-04 | 院内在院码分页 | 主表 `IN_HOSPITAL` 分页 | 读 | — | **主表** |
| IF-TRC-05 | 药库进货同意 | 建主档 + `HOSPITAL_IO` IN | 写 | BR-TRC-001、005、011 | 主+子 |
| IF-TRC-06 | 退供应商同意 | `HOSPITAL_IO` OUT | 写 | BR-TRC-001、006 | 主+子 |
| IF-TRC-07 | 药库出库同意 | `INTERNAL_TRANSFER` OUT（至药房等） | 写 | BR-TRC-004 | 主+子 |
| IF-TRC-08 | 药房请领入库同意 | `INTERNAL_TRANSFER` IN（药库 OUT 成对） | 写 | BR-TRC-004 | 主+子 |
| IF-TRC-09 | 院内调拨同意 | 同 `bus_no` 成对 `INTERNAL_TRANSFER` OUT+IN | 写 | BR-TRC-004 | 主+子 |
| IF-TRC-10 | 科室领用同意 | 来源 `INTERNAL_TRANSFER` OUT + 科室 IN | 写 | BR-TRC-004 | 主+子 |
| IF-TRC-11 | 科室退领同意 | 科室 OUT + 上级 IN | 写 | BR-TRC-008 | 主+子 |
| IF-TRC-12 | 科室领用同意 | `HOSPITAL_IO` OUT（**领用**） | 写 | BR-TRC-006、016 | 主+子 |
| IF-TRC-13 | 科室退领同意 | `HOSPITAL_IO` IN（**退领**） | 写 | BR-TRC-008、016 | 主+子 |
| IF-TRC-14 | 盘点/损益同意 | **盘点**：行存剩余扫码，与本库在库码差分 → 盘亏 OUT、盘盈 IN；**损益**：行存盈亏码，与数量正负同向直接 OUT/IN | 写 | BR-TRC-011、016 | 主+子 |
| IF-TRC-15 | 住院汇总核发 | `SupplyDelivery.trace_no` → `HOSPITAL_IO` OUT | 写 | BR-TRC-009、010 | 主+子 |
| IF-TRC-16 | 门诊/住院发药 | 发放行 `trace_no` → `HOSPITAL_IO` OUT | 写 | BR-TRC-006 | 主+子 |
| IF-TRC-17 | 住院药房退药确认 | 原码 `HOSPITAL_IO` IN | 写 | BR-TRC-008、012 | 主+子 |
| IF-TRC-18 | 护士退药：查正向码 | 从原已发发放行读 `trace_no` | 读 | — | MedDispense |
| IF-TRC-19 | 护士退药：写待退行 | UPDATE 待退发放行 `trace_no` | 写 | BR-TRC-009 | MedDispense |
| IF-TRC-20 | 院内在院码列表（简化） | 主表在院列表 | 读 | — | **主表** |
| IF-TRC-21 | **批量扫码身份反查** | 多码 → 主表品项+批号+在院态；供前端行级匹配 | 读 | BR-TRC-013～015 | **主表** |
| IF-TRC-22 | **库存行本库仍在库码** | 四元组 `(location, item, lot)` 子表聚合净量 > 0 | 读 | BR-TRC-017 | **子表聚合** |
| IF-TRC-23 | **追溯码报表分页** | 组合筛选 + 全院/本库双轨列分页 | 读 | BR-TRC-018 | **主+子** |

> **读 IF 页面绑定**：IF-TRC-22 → **AS-INV-WHS-004 · IF-INV-DTL-13**；IF-TRC-23 及 IF-TRC-01/02 详情 → **AS-CUS-TRC-002**。

### 2.1 供应域与发药 IF 映射（`operation_type`）

#### 2.1.1 `HOSPITAL_IO`（动在院 · DM §2.4.1）

| 业务 | `SupplyType` / 扩展码 | 追溯 IF | 方向 |
| --- | --- | --- | --- |
| **进货** | `CABINET_INBOUND_RECEIPT` | IF-TRC-05 | IN |
| **退货**（退供应商） | `CABINET_RETURN_TO_SUPPLIER` | IF-TRC-06 | OUT |
| **发药** | `MEDICATION_DISPENSE_OUT` 等 | IF-TRC-15、16 | OUT |
| **退药** | `MEDICATION_RETURN_IN` | IF-TRC-17 | IN |
| **领用** | `DEPT_ISSUE` | IF-TRC-12 | OUT |
| **退领** | `DEPT_ISSUE_RETURN` | IF-TRC-13 | IN |
| **报损** | `CABINET_GAIN_LOSS`（损失）等 | IF-TRC-14 | OUT（扫报损码直接减） |
| **报溢** | `CABINET_GAIN_LOSS`（盈余）等 | IF-TRC-14 | IN（扫报溢码直接加） |
| **盘亏** | `CABINET_STOCKTAKE`（短少）等 | IF-TRC-14 | OUT（剩余码差分） |
| **盘盈**（对称） | `CABINET_STOCKTAKE`（盈余）等 | IF-TRC-14 | IN（剩余码差分） |

#### 2.1.2 `INTERNAL_TRANSFER`（仅流转 · DM §2.4.2）

| 业务 | `SupplyType` code | 追溯 IF | 记法 |
| --- | --- | --- | --- |
| 药库出库（至药房） | `CABINET_STOCK_OUT` | IF-TRC-07 | OUT（与 IF-TRC-08 成对） |
| 药房请领入库 | `PHARMACY_STOCK_IN` | IF-TRC-08 | IN |
| 药房退库 | `PHARMACY_RETURN_TO_WAREHOUSE` | IF-TRC-09 | OUT+IN |
| 药库退库入库 | `CABINET_RETURN_STOCK_IN` | IF-TRC-09 | IN |
| 各类调拨 | `*_TRANSFER_OUT` / `*_TRANSFER_IN` | IF-TRC-09 | OUT / IN |
| 科室请领入库 | `DEPT_REQUISITION` + `DEPT_STOCK_IN` | IF-TRC-10 | OUT+IN |
| 科室退库 | `DEPT_RETURN` | IF-TRC-11 | OUT+IN |

> **扩展**：`operation_type` 与 `SupplyType#getCode()` 同值；发药扩展码见 **DM-CUS-010** §4.7。编排时**先判定 §2.1.1 vs §2.1.2**，再选 `flow_category_enum`。

### 2.2 明确不写主/子表的场景

| 场景 | 写哪里 | 原因 |
| --- | --- | --- |
| **护士正向分配**（AS-IP-NUR-015） | 仅 `med_medication_dispense.trace_no` | 汇总核发 IF-TRC-15 已 OUT（**BR-TRC-009**） |
| **护士退药带出**（IF-TRC-18、19） | 待退发放行 `trace_no` | 药房确认 IF-TRC-17 再 IN |
| **订货单**（`CABINET_PURCHASE_ORDER`） | 无 | 未入库 |
| **供应单草稿** | 业务表暂存 `trace_no` | 同意时走对应写 IF |

### 2.3 批量扫码：身份反查与行级匹配分工

```
用户批量扫码
    │
    ▼
IF-TRC-21（只读）
  入参：traceCodes[] + sceneContext（可选 locationId / encounterId / busNo / summaryNo）
  领域：TraceNumberQueryService#batchResolveTraceIdentityByTraceNos
  出参：每码 { traceNo, itemId, lotNumber, traceUnitCode, remainingSplitQuantity,
              inHospitalStatus, resolveStatus }
    │
    ▼
前端匹配（各业务页）
  候选行：当前发药明细 / 供应行 / 护士汇总组（页面已加载）
  规则：medicationId + lotNumber 对齐；同药同批多行 → 未配满优先或用户点选
  数量：结合 traceUnitCode、行待发数量判断需几枚码
    │
    ▼
保存（各场景写 IF）
  门诊发药 → IF-TRC-16；进货 → IF-TRC-05；护士分配 → IF-IP-TRC-04（仅 trace_no）
  保存路径复读：在院、码池、码数上限、重复 OUT 等 Gate
```

| 场景 | IF-TRC-21 之后 | 保存 IF | 额外 Gate（预览后仍须校验） |
| --- | --- | --- | --- |
| 门诊/住院发药 | 前端填发放行 | IF-TRC-16 | 主表在院 + 剩余充足（**BR-TRC-006**） |
| 药库进货 | 前端填 `trace_no`（贴码单位取药典，单据不采） | IF-TRC-05 | `NOT_FOUND` 可接受（同意时建主档） |
| 住院护士分配 | 前端填汇总组 | IF-IP-TRC-04 | 码须在汇总单 `SupplyDelivery` 码池（**BR-IP-TRC-005**） |
| 住院退药带出 | **不走** IF-TRC-21 | IF-TRC-18 | 查原发放行 `trace_no`，不反查主表建新码 |

> **与 IF-IP-TRC-03 关系**：护士站现有批量解析（`PharmacyTraceCodeResolvesupport）依赖码池内存匹配且**无批号**，同药多批易失败。演进路径：IF-IP-TRC-03 **先调 IF-TRC-21** 取主表批号，再叠加码池与汇总组匹配；门诊 `trace-scan-dialog` 用同一 IF-TRC-21 替换前端 mock。

### 2.4 业务撤销与冲正（追溯侧）

> **原则**：子表**只 INSERT**；撤销/冲正**不 DELETE/UPDATE 历史流水**，以**反向 `HOSPITAL_IO` 或成对 `INTERNAL_TRANSFER`** 记新流水；新流水 `bus_no` 须与正向可区分（冲正单号 / 原单号 + 后缀 / 退药单号）。

| 场景 | 追溯动作 | 绑定 IF / 规则 |
| --- | --- | --- |
| **供应单草稿/审批中撤回** | **不写**主/子表；业务表 `trace_no` 仍草稿 | **BR-TRC-011** |
| **供应单已同意后的业务冲正**（如退供应商、红字进货） | 按业务类型记反向 **`HOSPITAL_IO`**（进货冲正 → OUT；退供应商撤销 → IN） | **IF-TRC-06** 等；**BR-TRC-001** |
| **调拨/请领已同意后撤销** | 同 `bus_no`（或冲正 `bus_no`）成对 **`INTERNAL_TRANSFER`** 反向 OUT+IN | **BR-TRC-004** |
| **发药前作废**（`DECLINED`，未 `COMPLETED`） | **不写** OUT；发放行 `trace_no` 仅草稿 | **BR-TRC-011** |
| **发药后退药** | 复用原码 **`HOSPITAL_IO` IN**（**不新造码**）；按原发 OUT 每码实扣**镜像**加回同一主档（**DD §5**） | **IF-TRC-17**、**BR-TRC-008/012** |
| **同 `bus_no` 重复同意/核发** | Gate 拒绝（防重复流水） | **EX-TRC-208** 类 |

> **成码注意**：各业务 AS 撤销路径须在文档中显式列出是否触发上表行；未列出的撤销默认**不动**追溯主/子表。

---

## 3. 缺口跟踪（Gap）

> **文档层**：下列项已在 **DM-SDD §13～§18**、**AS-CUS-TRC-001-SDD**、**DM-WKF-006 `trace_unit_code`** 落盘；**成码**时按 §11 阶段执行。

| Gap ID | 类型 | 说明 | 文档落点 | 影响 |
| --- | --- | --- | --- | --- |
| GAP-TRC-001 | — | ~~供应行 `trace_unit_code`~~ | **已关闭**：列已删除（**health-final V107**）；贴码单位仅药典（**BR-TRC-005**） | IF-TRC-05 |
| GAP-TRC-007 | Flyway+领域 | 主子表 DDL + 实体 | **DM-SDD §13.1 V107** | 全部写 IF |
| GAP-TRC-008 | — | ~~`recordTraceRecords` 取代 `append*`~~ | **已关闭**（`tracenumberrecord` 包已删） | — |
| GAP-TRC-006 | Query | 待退↔原发 `trace_no` | **DM-MED-002**（待补读方法） | IF-TRC-18 |
| GAP-TRC-009 | — | ~~IF-TRC-21 AppService~~ | **已关闭**：嵌入进货/门诊发药/护士分配等业务 App | — |
| GAP-TRC-010 | Query | `listInStockTraceNumbersByInventoryItemKey` | **DM-SDD §5.2 R-007、§15** | IF-TRC-22 |
| GAP-TRC-011 | Query | `pageTraceNumberReport | **DM-SDD §5.2 R-008、§15** | IF-TRC-23 |
| GAP-TRC-012 | 业务嵌入 | ~~15 个 AppService 改调 Support~~ | **已关闭**：全部写 IF 已嵌入各业务 App + module-support | — |
| GAP-TRC-014 | 业务嵌入 | ~~盘点/损益 IF-TRC-14~~ | **已关闭**：6 个盘点/损益 App + `TraceNumberStocktakeGainLossTraceWritesupport | IF-TRC-14 |
| GAP-TRC-013 | 迁移 | `cus_trace_number` + `cus_trace_number_flow` → 主/子表 | **DM-SDD §16 V109** | 历史数据 |

---

## 4. 接口编排说明书（业务步骤）

### IF-TRC-01 全院在院判定

| 维度 | 说明 |
| --- | --- |
| **触发** | 发药/出库前 Gate；追溯查询 |
| **主路径** | 按 `trace_no` 查主表 → `in_hospital_status_enum` + `remaining_split_quantity`；**不**用 `location_id` |
| **失败分支** | 不在院或剩余不足 → **EX-TRC-201** |

### IF-TRC-03 库位码列表

| 维度 | 说明 |
| --- | --- |
| **触发** | 库房扫码录入前；库位盘点辅助 |
| **主路径** | `listTraceNumbersByLocationId`：子表按 `location_id` 轧差净拆零量 > 0 |
| **说明** | 调拨 IN/OUT、进货 IN、发药 OUT 等均参与 loc 读法 |

### IF-TRC-05 药库进货初始化

| 维度 | 说明 |
| --- | --- |
| **触发** | AS-INV-WHS-002 同意且行 `trace_no` 非空 |
| **主路径** | 从药典取 `trace_unit_code`+`trace_unit_ratio` → 拆码 → 每码 **`HOSPITAL_IO` IN**（异阶前填实入拆零，见 **BR-TRC-019**）；建主档（含比例快照）；写库前须满足 **BR-WHS-IN-013/014** |
| **失败分支** | 单位非法 **EX-TRC-202**；码超长 **EX-TRC-203** |

### IF-TRC-09 院内调拨

| 维度 | 说明 |
| --- | --- |
| **触发** | 调拨类供应单同意 |
| **主路径** | 同一 `bus_no`：来源 `INTERNAL_TRANSFER` OUT + 目的 IN；**主表剩余不变** |
| **失败分支** | 未成对 **EX-TRC-206** |

### IF-TRC-15 住院汇总核发

| 维度 | 说明 |
| --- | --- |
| **触发** | AS-IP-PHA-002 核发成功 |
| **主路径** | `SupplyDelivery.trace_no` 拆码 → `HOSPITAL_IO` OUT；**不写** `MedicationDispense.trace_no` |
| **示例** | 发 3 瓶：一条子表 `quantity=3`、`unit_code=瓶`；主表剩余减 3（拆零） |

### IF-TRC-16 门诊/住院发药

| 维度 | 说明 |
| --- | --- |
| **触发** | AS-OP-PHA-001 / AS-IP-PHA-001 发药 |
| **主路径** | 发放行 `trace_no` → **`isRemainingSplitSufficientForDispenseOut`**（盖住判定，不抛；**BR-TRC-006/019**）→ 盖住则 **`allocateRemainingFirstSplitForDispenseOut`** 写 `HOSPITAL_IO` OUT（每码实扣落子表；异阶剩余升序）；盖不住/空码**跳过**写追溯，不挡发药 |

### IF-TRC-17～19 住院退药双段

| 维度 | 说明 |
| --- | --- |
| **IF-TRC-18** | 护士从**原已发**发放行查出 `trace_no`（Query 能力，GAP-TRC-006） |
| **IF-TRC-19** | 护士写待退发放行 `trace_no`；**不写**主/子表 |
| **IF-TRC-17** | 药房确认退药：**`TraceNumberDispenseTraceOrchestrator#recordReturnInLines`**（或退领 **`recordDeptIssueReturnMirroredIn`**）按原 OUT 每码镜像 IN；定位 OUT：先退药行 `bus_no`，无则按拷贝 `trace_no` 只读子表回落未回冲原发 OUT（对冲行新单号）；**禁止**新建主档或按当前剩余重分摊；主表翻回在院并增剩余；写 IN 的 `bus_no` 仍用退药行单号 |

### IF-TRC-21 批量扫码身份反查

| 维度 | 说明 |
| --- | --- |
| **触发** | 发药/进货/护士分配等页面用户一次性扫入多枚码（扫码枪批量输入） |
| **入参** | `traceCodes[]`（必填，规范化后；单次 ≤ 50，**BR-TRC-015**）；`sceneContext`（可选：`DISPENSE` / `INBOUND` / `NURSE_ASSIGN` / `RETURN`）；上下文可选 `locationId`、`encounterId`、`busNo`、`summaryNo`（供应用层叠加场景 Gate，**非**领域 Query 必填） |
| **主路径** | 调用 `batchResolveTraceIdentityByTraceNos` 批量读主表 → 每码返回 `itemId`、`itemTable`、`lotNumber`、`traceUnitCode`、`remainingSplitQuantity`、`inHospitalStatusEnum`、`resolveStatusEnum` |
| **前端职责** | 用 `medicationId + lotNumber` 与当前页候选行匹配，将 `traceNo` 写入对应行/组；处理同药同批多行歧义。批量扫码弹窗「药品明细」展示 **追溯码单位** + **码倍**（`×N`，1 追溯码单位 = N 拆零）；发药：**主档优先，无主档回落药典** `trace_unit_*`；**盖住**用 `resolveDispenseOperationSplitQty` 输出真拆零再比主档剩余（**BR-TRC-023** / DD §3.1）；库房单据取目录追溯码单位；**不展示处方号**（列宽易截断且回显易歧义） |
| **出参** | `items[]`（与入参同序）；`failItems[]`（`NOT_FOUND`、`NOT_IN_HOSPITAL`、`DUPLICATE_IN_REQUEST` 等）；**允许部分成功** |
| **关键分支** | `NOT_FOUND`：进货场景可仍填行（同意时 IF-TRC-05 建主档）；发药场景提示重扫或拒收 |
| **关键分支** | `NOT_IN_HOSPITAL`：发药/出库场景标记不可用（**EX-TRC-201**） |
| **关键分支** | 同药同批命中多行：出参可带 `ambiguous=true`（应用层可选）；前端须用户确认或按未配满贪心 |
| **失败分支** | 空列表 **EX-TRC-101**；超 50 码 **EX-TRC-207**；格式非法 **EX-TRC-202** |
| **不写库** | 本 IF **禁止** UPDATE 业务行或主/子表；保存由各场景写 IF 完成（**BR-TRC-013、014**） |

### IF-TRC-22 库存行本库仍在库码

| 维度 | 说明 |
| --- | --- |
| **触发** | **AS-INV-WHS-004** 现库行点击「追溯码」；扫码弹窗药品展开（页面 `hospitalWide`） |
| **入参** | `itemId` 必填；本库路径另需 `locationId`、`lotNumber`；可选 **`hospitalWide`** / **`hospitalWideFallbackWhenEmpty`**（页面绑定 IF-INV-DTL-13） |
| **主路径** | 默认 `listInStockTraceNumbersByInventoryItemKey`；**`hospitalWide=true`** 时 `listInHospitalTraceNumbersByItem`（该药全部批号、主档在院且剩余 > 0），出参带 **`lotNumber`**、药典 **`splitUnitCode`**，**不**回填所在库位 |
| **关键分支** | 无仍在库/在院码 → 空列表（非错误）；本库空且 `hospitalWideFallbackWhenEmpty` → 同品同批全院回落（库取） |
| **失败分支** | 缺必填参数 → **EX-TRC-101** |
| **前端** | 本库弹窗展示码列表；扫码弹窗：**药品 → 批号列表 → 在院码**（只读，不按发药行批号过滤） |

### IF-TRC-23 追溯码报表分页

| 维度 | 说明 |
| --- | --- |
| **触发** | **AS-CUS-TRC-002 · IF-TRC-RPT-02/05** |
| **入参** | `queryScope`（`HOSPITAL`/`LOCATION`）、可选 `locationId`、`inHospitalStatusEnum`、`locationStockStatus`、`traceNoKeyword`、`medicationKeyword`、`lotNumberKeyword`、`flowTimeStart`/`flowTimeEnd`、分页 |
| **主路径** | `pageTraceNumberReport：`item_table=medication` → 主表 + 子表聚合装配列表行（在院态、本库态、最近流水时间等）→ 分页返回 |
| **关键分支** | `LOCATION` 范围须 `locationId` |
| **失败分支** | 参数非法 → **EX-TRC-101** |

### 双轨读法示例

入库 A 一箱 → 调拨 B 5 瓶、C 5 瓶 → B/C 各发 5 瓶：

- **主表**：`NOT_IN_HOSPITAL`，`remaining_split_quantity = 0`
- **子表 loc 聚合**：A/B/C 净量均为 0

---

## 5. 业务异常与用户提示

| 异常 ID | 语义 | 用户提示（示例） |
| --- | --- | --- |
| EX-TRC-201 | 码不在院或剩余不足 | 追溯码不在院内或已耗尽 |
| EX-TRC-202 | `trace_unit_code` 非法 | 追溯码单位不合法 |
| EX-TRC-203 | `trace_no` 超长 | 追溯码超过最大长度 |
| EX-TRC-204 | 退药码与正向不符 | 追溯码与发药记录不匹配 |
| EX-TRC-205 | 退药原行无码 | 原单据未登记追溯码，无法退药 |
| EX-TRC-206 | 调拨未成对 | 调拨流水未成对，请联系管理员 |
| EX-TRC-207 | 批量扫码超限 | 单次最多扫描 50 个追溯码 |
| EX-TRC-208 | 同药同批多行歧义 | 存在多条相同药品批号明细，请选择要填入的行 |
| EX-TRC-101 | 查询参数缺失 | 请提供追溯码 |

---

## 6. 事件语义与消费方

| 事件 | 触发时机 | 消费方 |
| --- | --- | --- |
| `TraceNumberInitializedEvent` | 进货首次建主档 | 审计 |
| `TraceNumberRecordRecordedEvent` | `recordTraceRecords` 成功 | 审计、药监上报（可选） |

> 废弃单表时代 `TraceNumberRecordAppendedEvent` / `AdjustedEvent`。

---

## 7. 非功能性需求

### 7.1 一致性

| 项 | 要求 |
| --- | --- |
| **事务** | 业务单动账与 IF-TRC 写主/子表**同一事务** |
| **幂等** | 同一 `bus_no` 重复同意须 Gate 拒绝重复流水 |

### 7.2 索引（落库）

| 表 | 索引 |
| --- | --- |
| `cus_trace_number` | `trace_no` 唯一 |
| `cus_trace_number_flow` | `trace_number_id`、`(location_id, trace_no)`、`bus_no` |

---

## 8. 测试要点（业务视角）

- 入库 A → 调拨 B/C → 各发完 → **主表不在院**；A/B/C loc 聚合均为 0
- 调拨不改主表 `remaining_split_quantity`
- 护士分配/退药带出不写主/子表；药房确认退药写 IN
- 主子同事务：库存/发放失败时追溯流水回滚
- 箱码发 3 瓶：一条子表 `qty=3`，主表减拆零量 3
- **IF-TRC-21**：一次扫 10 码 → 主表批量返回 10 条身份；前端按药+批填入对应行；保存时复读 Gate
- **IF-TRC-22/23**：库存行本库仍在库码；报表分页全院/本库双轨列
- 同药同批两行：预览可成功，前端须消歧后再保存
- **IF-TRC-21** 同一次反查入参重复码：第二条标记 `DUPLICATE_IN_REQUEST`（≠ 发药多行共扫同码；见 **BR-TRC-023**）
- 发药：行内同码硬拒；跨行同码合法；绑码个数 &gt; ceil(操作拆零) 硬拒

---

## 10. 成码阶段路线图（纯后端）

| 阶段 | 交付物 | 验收 |
| --- | --- | --- |
| **P0 文档** | DM v2.0.5、DM-SDD §13～18、AS-SDD、WKF `trace_unit_code` | 本表 §3 Gap 均有文档落点 |
| **P1 领域** | V107～V109 Flyway、`TraceNumber*` 实体、`DomainService`/`QueryService` 8 读 2 写 | 领域单测 TC-DM-CUS-010-* |
| **P2 Support** | `TraceNumberRecordOrchestrationsupport + `FlowCategoryResolver` | TC-TRC-SUP-001～005 |
| **P3 样板嵌入** | 进货 + 门诊发药 2 条写链 | 同意/发药事务内主子表正确 |
| **P4 全量嵌入** | AS-SDD §5 其余 AppService | 统一 `recordTraceRecords` |
| **P5 读链** | IF-TRC-21 AppService + 报表/库存读 | AS-CUS-TRC-002-SDD |
| **P6 迁移** | V110 历史单表迁移（可选） | 抽样码一致 |

---

## 11. 关联文档与变更历史

| 文档 | 路径 |
| --- | --- |
| DM | `DM-CUS-010-TraceNumberRecord-追溯码记录.md` v2.0.4 |
| DM-SDD | `DM-CUS-010-TraceNumberRecord-追溯码记录-SDD.md`（§13 Flyway） |
| **AS-SDD** | `AS-CUS-TRC-001-追溯码流转-SDD.md` |
| 报表 AS/SDD | `AS-CUS-TRC-002` / `AS-CUS-TRC-002-SDD` |
| 供应行 | `DM-WKF-006`（仅 `trace_no`；贴码单位见药典） |
| 进货 AS | `AS-INV-WHS-002`（同意路径 IF-TRC-05） |
| 护士分配 | `AS-IP-NUR-015` |
| 门诊发药 UI | `openhis-pro-ui` 追溯扫码弹窗（对接 IF-TRC-21） |
| 库存明细 AS | `AS-INV-WHS-004`（IF-INV-DTL-13） |

| 版本 | 日期 | 摘要 |
| --- | --- | --- |
| v2.12 | 2026-08-20 | **IF-TRC-17 / BR-TRC-008**：退药对冲行新 `bus_no` 时按拷贝 `trace_no` 回落未回冲原发 OUT（`listUnreturnedDispenseHospitalIoOutRecordsByTraceNos`）；治静默跳过写 IN |
| v2.11 | 2026-08-19 | **BR-TRC-023**：前端盖住需求须真拆零（对齐 DD v1.8）；禁止同阶盒数直接比主档片剩余导致待扫误变 0 |
| v2.10 | 2026-08-19 | 澄清贴码倍数仅追溯域；库存发退扣加走 **BR-OPP-017** 五类倍数，禁止同码「盒」用 `trace_unit_ratio` 覆盖 |
| v2.9 | 2026-08-12 | **BR-TRC-006**：发药硬拦「主档存在且剩余≤0」；未建档仍软；对齐 `assertMasterCodesNotExhaustedForDispense` |
| v2.8 | 2026-08-12 | **BR-TRC-006/014/020/023**：明确**码身份硬 Gate 仅进货**；消耗侧未建档/不匹配/不在院软提示，保存配药不硬拦；写流水仍盖住软跳过 |
| v2.7 | 2026-08-11 | **BR-TRC-022 / IF-TRC-22**：`listInStockTraceNumbersByInventoryItemKey` 叠加主档在院剩余过滤；退供应商 Prefiller 截码 `min(可退, 源库可用)` |
| v2.6 | 2026-08-05 | **BR-TRC-023**：发药绑码硬上限/跨行同码/行内重复；澄清 **BR-TRC-015** 仅反查入参去重；对齐 DD v1.6 §3.4 |
| v2.5 | 2026-08-02 | **UI**：扫码弹窗去掉处方号列；追溯码单位/码倍列收窄；发药无主档时追溯码单位/比例回落药典 |
| v2.4 | 2026-08-02 | **UI**：批量扫码弹窗「药品明细」展示追溯码单位与追溯码/拆零比例（发药主档快照 / 库房目录）；对齐 IF-OPP/IPP slip `traceUnitCodeText`+`traceUnitRatio` |
| v2.3 | 2026-07-25 | **BR-TRC-006**：硬 Gate 仅进货；期初扫码可选（对齐 DD v1.5 / AS-BAS-INV-001 v0.6） |
| v2.2 | 2026-07-25 | **BR-TRC-019** 纠偏：同阶亦剩余先出分摊，废止「同阶每码 ±1」歧义句；与 DD v1.4 Upload 实扣 `trdn_flag` / 前端盖住确认对齐 |
| v2.1 | 2026-07-25 | **DD v1.2 对齐**：**BR-TRC-019** 消耗侧剩余先出+每码 OUT；**BR-TRC-008** 退药/退领镜像 OUT；**IF-TRC-16/17** 软跳过与 `recordReturnInLines`；进货/期初硬 Gate 不变 |
| v1.11 | 2026-07-25 | **BR-TRC-021/022**：扫码可选（半扫须确认）；退供应商等取码=当前本库优先/全院在院剩余，禁止回放进货单码串；术语表补扫码可选/取码优先级 |
| v2.2 | 2026-07-27 | **IF-TRC-22**：`hospitalWide` 按药品全批在院码（`listInHospitalTraceNumbersByItem`）；扫码弹窗「药品→批号→码」 |
| v2.1 | 2026-07-27 | **IF-TRC-22 / IF-INV-DTL-13**：页面 **`hospitalWide`** 始终全院在院浏览；出参主档剩余 + 药典拆零单位；主档仍不存 `location_id` |
| v2.0 | 2026-07-23 | **贴码单位改药典**：药品必填/耗材可选成对；进货/期初单据不采；**删除** `wkf_supply_request.trace_unit_code`；主档 `trace_unit_ratio` 快照；入库/发退异阶前填实入拆零 + Gate ceil（**BR-TRC-005/019**） |
| v1.9 | 2026-07-08 | **BR-TRC-019** 初版（**已被 v2.0 取代**）：发药 OUT 前剩余 Gate |
| v1.7 | 2026-07-06 | **成码文档包**：Gap 文档落点、§10 阶段路线图、**§2.4 撤销冲正**；关联 AS-SDD + DM-SDD §13～18 |
| v1.6 | 2026-07-06 | **IF-TRC-22/23** 读侧：库存行本库仍在库码、追溯码报表分页；绑定 AS-CUS-TRC-002 / IF-INV-DTL-13 |
| v1.5 | 2026-07-06 | **§2.1.1/2.1.2**：动在院八类 vs 院内流转；BR-TRC-016 剩余耗尽才不在院；领用/退领归 `HOSPITAL_IO` |
| v1.4 | 2026-07-06 | **IF-TRC-21 批量扫码身份反查**：主表反查 + 前端行级匹配分工；BR-TRC-013～015 |
| v1.3 | 2026-07-06 | 主子表定稿：主表无 loc、子表承担库位与业务流水；`HOSPITAL_IO` vs `INTERNAL_TRANSFER`；一条业务一行；loc 不参与在院计算；IF-TRC-18/19 护士退药双段 |
| v1.2 | 2026-07-06 | 主子表草案 |
| v1.1 | 2026-07-06 | 住院退药：护士查/写码，药房确认 IN |
| v1.0 | 2026-07-06 | 首版（单表，已 superseded） |
