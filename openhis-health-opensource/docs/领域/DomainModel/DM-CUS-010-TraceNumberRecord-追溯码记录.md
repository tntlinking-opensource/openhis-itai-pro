# DM-CUS-010 TraceNumberRecord 追溯码记录

> **限界上下文**：系统基础 · 追溯码管理  
> **FHIR 映射**：无标准 FHIR R5 对应资源（系统级自定义模型）  
> **聚合类型**：实体聚合（**主表 + 子表流水**）  
> **版本**：v2.0.10 · **日期**：2026-08-19

---

## 1. 概述

### 1.1 业务定义

追溯码（TraceNumber）聚合以**单个追溯码字符串**为最小业务单元：**主表**登记院内是否仍有该码及码身份；**子表流水**记录每次动账的数量、业务单位、库位与操作类型。主表**不承载库位**；子表 `location_id` **仅作流转记录**，参与「某库房有哪些码」的读侧聚合，**不参与**全院「码是否仍在院内」的剩余量计算（见 §2.3、BR-TNR-003）。不维护库存数量主账。

### 1.2 核心职责

| 职责 | 说明 |
|---|---|
| 主表现态 | 一码一行：在院/不在院、拆零剩余量（便捷字段）、`trace_unit_code`、品项与批号 |
| 子表流水 | 只 INSERT；一次业务动账一行（如发 3 瓶 = 1 条 `quantity=3`）；承载库位、方向、数量与单位 |
| 院内在院判定 | 主表 + **进出院类**流水汇总；**院内调拨**成对记子表、**不改变**主表剩余 |
| 库位码列表 | 子表按 `location_id` 聚合（含调拨 IN/OUT、进货、发药 OUT 等），支撑「某库房有哪些码」 |
| 患者去向追溯 | 经发药/退药子表流水与 `MedicationDispense` 关联 |
| 监管查询 | 药监上报与审计轨迹 |
| **扫码身份反查** | 批量 `trace_no` → 主表品项 + 批号 + 在院态 + 追溯码单位（**§6.2.6**）；供各场景前端行级匹配，**不写库** |

### 1.3 适用范围

| 类型 | 说明 |
|---|---|
| 采购入库 | 高值耗材到货后扫码录入追溯码，记录入库轨迹 |
| 科室领用/发药 | 科室领用或药房发药时，记录出库轨迹并关联业务单据 |
| 调拨 | 仓库间调拨时，来源仓记录出库、目的仓记录入库 |
| 退药/退库 | 未使用耗材退回时，记录反向轨迹 |
| 在院查询 | 主表查码是否仍在院内；子表按库位查该库房关联的码 |
| 患者追溯 | 根据追溯码查询耗材流向，定位最终使用患者 |
| 药监上报 | 将轨迹数据汇总上报至国家药品监督管理局追溯平台 |

### 1.4 与关键上下游及交换边界的关系

轨迹事实来源于入库、发药、调拨等业务单据；库存主账在其他聚合。写侧统一：**INSERT 子表 + 同步 UPDATE 主表**（同事务）。全院剩余仅由 **进出院类**流水驱动主表；库位维度仅读子表聚合。

| 维度 | 上游 | 本聚合 | 下游 |
|---|---|---|---|
| 事实 | 业务单据与项目定义 | 轨迹行追加 | InventoryItemQueryService 查询、审计、药监上报 |
| 患者 | — | 不直接存患者标识 | 经发药聚合间接追溯 |

**交换**：无独立 FHIR R5 资源；院内扁平轨迹对外可组合映射为 `Device.udiCarrier` 扩展与 `AuditEvent` 等（以集成规范为准）。

---

## 2. 聚合根与持久化形态

### 2.1 持久化形态

**持久化形态**：主表 + 子表。

| 表 | 实体 | 说明 |
|---|---|---|
| `cus_trace_number` | `TraceNumber` | **聚合根**；一码一行；可 UPDATE |
| `cus_trace_number_flow` | `TraceNumberRecord` | **子实体记录**；只 INSERT；物理表名历史保留 `flow` |

**高频查询**：主表按 `trace_no` 唯一；子表按 `trace_number_id`、`trace_no`、`location_id`、`bus_no`、时间序。

### 2.2 聚合根标识

| 属性 | 类型 | 说明 |
|---|---|---|
| 追溯码主档标识 | `TraceNumberId` | 主表雪花 ID |
| 追溯码记录标识 | `TraceNumberRecordId` | 子表雪花 ID |

### 2.3 状态说明

**主表在院状态**（`in_hospital_status_enum`，可 UPDATE）：

| 枚举 code | 说明 |
|---|---|
| `IN_HOSPITAL` | 该码在院内仍有剩余（`remaining_split_quantity > 0`） |
| `NOT_IN_HOSPITAL` | 该码已耗尽或已离开院内（如全部发予患者、退供应商） |

**子表流水类别**（`flow_category_enum`，创建时确定，不可变）：

| 枚举 code | 说明 | 对主表剩余 |
|---|---|---|
| `HOSPITAL_IO` | **进出院**（动主表剩余与在院态）；见 §2.4 白名单 | **增减** `remaining_split_quantity`；剩余 ≤ 0 翻 `NOT_IN_HOSPITAL`；IN 类业务剩余 > 0 翻回 `IN_HOSPITAL` |
| `INTERNAL_TRANSFER` | **院内流转**（调拨/请领入库/退库等）；见 §2.4 | **不变** 主表剩余与在院态 |

**子表轨迹方向**（`flow_direction_enum`）：`IN` / `OUT`（与 **TraceDirection** 同族）。

**读侧双轨**：

```
全院（主表）：
  在院 = in_hospital_status_enum = IN_HOSPITAL
  剩余拆零量 = remaining_split_quantity（写侧维护；可由 HOSPITAL_IO 流水重算校验）

库位（子表聚合，loc 仅在此参与计算）：
  某 location_id 下该码净量 > 0 → 该库仍「挂着」此码
  例：入库 A 一箱 → 调拨 B 5 瓶、C 5 瓶 → B/C 各发 5 瓶 → 主表不在院；A/B/C 库位聚合均为 0
```

> **盒码 / 箱码**：1 盒 1 码或 10 瓶共 1 箱码，均 **主表 1 行**；`trace_unit_code` 表示码贴在哪级包装；主表剩余一律 **拆零（最小单位）**。

### 2.4 业务分类：动在院 vs 仅流转

**判定原则**：

- 仅 **`HOSPITAL_IO`** 流水改变主表 `remaining_split_quantity` 与 `in_hospital_status_enum`。
- **`INTERNAL_TRANSFER`** 只记子表库位轨迹，**不**改变主表在院态与全院剩余。
- 主表翻 **`NOT_IN_HOSPITAL`** 的充分条件：`HOSPITAL_IO` **OUT** 类动账后 `remaining_split_quantity ≤ 0`（**前提：剩余耗尽**，非每次 OUT 都立刻不在院）。
- 主表翻回 **`IN_HOSPITAL`**：`HOSPITAL_IO` **IN** 类动账后 `remaining_split_quantity > 0`。

#### 2.4.1 `HOSPITAL_IO` 白名单（控制是否在院）

| 业务 | 典型 `operation_type` / 场景 | 方向 | 对主表剩余 |
|---|---|---|---|
| **进货** | `CABINET_INBOUND_RECEIPT` 等 | IN | **增**；有剩余即在院 |
| **退货**（退供应商） | `CABINET_RETURN_TO_SUPPLIER` | OUT | **减**；剩余 ≤ 0 → 不在院 |
| **发药** | `MEDICATION_DISPENSE_OUT`、住院汇总核发 OUT | OUT | **减**；发予患者，耗尽 → 不在院 |
| **退药** | `MEDICATION_RETURN_IN` | IN | **增**；退回院内，剩余 > 0 → 在院 |
| **领用**（科室消耗领出） | `DEPT_ISSUE` | OUT | **减**；离开可用库存，耗尽 → 不在院 |
| **退领** | `DEPT_ISSUE_RETURN` | IN | **增**；未消耗退回，剩余 > 0 → 在院 |
| **报损** | 损益/报损类（如 `CABINET_GAIN_LOSS` 损失方向） | OUT | **减**；损毁出院，耗尽 → 不在院 |
| **盘亏** | 盘点短少（`CABINET_STOCKTAKE` 等盘亏方向） | OUT | **减**；账实短少出院，耗尽 → 不在院 |

> **对称 IN 类**（未在上表逐条点名但同族）：**盘盈** 等 `HOSPITAL_IO` IN，按「剩余 > 0 → 在院」处理。

#### 2.4.2 `INTERNAL_TRANSFER`（仅院内流转，不动在院）

| 业务 | 典型 `operation_type` | 记法 |
|---|---|---|
| 药库↔药库 / 药库↔药房 / 药房↔科室 **调拨** | `CABINET_TRANSFER_*`、`PHARMACY_TRANSFER_*`、`DEPT_TRANSFER_*` | 同 `bus_no` 成对 OUT+IN |
| 药房**请领入库** | `PHARMACY_STOCK_IN` | 成对 OUT（药库）+ IN（药房） |
| 科室**请领入库**（到科室二级库） | `DEPT_REQUISITION` + `DEPT_STOCK_IN` | 成对 OUT+IN |
| 科室**退库**、药房**退库** | `DEPT_RETURN`、`PHARMACY_RETURN_TO_WAREHOUSE` 等 | 成对 OUT+IN |

> **区分要点**：**请领入库**把货移到科室/药房库位，货仍在院内 → `INTERNAL_TRANSFER`；**领用（DEPT_ISSUE）**是科室消耗发出，货离开可用追溯库存 → `HOSPITAL_IO` OUT。

---

## 3. 实体

### 3.1 追溯码主档（TraceNumber，聚合根）

对应 `cus_trace_number`。

| 属性 | 字段名 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| 主档标识 | `id` | `Long` | 是 | 雪花 ID |
| 追溯码 | `trace_no` | `String` | 是 | 单码；租户内业务唯一；**TRC-WIDE-R-001** VARCHAR(50000) 用于业务表逗号串，主表存单码 |
| 项目所属表 | `item_table` | `String` | 是 | 物理表名，见 §4.1 |
| 项目标识 | `item_id` | `Long` | 是 | 药品/耗材定义 ID |
| 批号 | `lot_number` | `String` | 是 | 批号 |
| 追溯码单位 | `trace_unit_code` | `String` | 是 | 码所贴包装单位（盒/箱等）；建码时从药典写入，见 §4.6 |
| 追溯码→拆零比快照 | `trace_unit_ratio` | `BigDecimal` | 是 | 建码时 1 贴码单位对应拆零量；**应扫码数 / 主档剩余盖住**用本列；**禁止**用于 `wkf_inventory_item` 发退扣加（扣库走发放操作单位×五类倍数） |
| 拆零剩余量 | `remaining_split_quantity` | `BigDecimal` | 是 | **最小单位**剩余；便捷字段，写 `HOSPITAL_IO` 流水时同步 UPDATE |
| 在院状态 | `in_hospital_status_enum` | `String` | 是 | `IN_HOSPITAL` / `NOT_IN_HOSPITAL` |
| 租户/审计 | 基类字段 | — | — | `BaseAuditedEntity` |

> **主表不含 `location_id`**。全院是否还有该码只看本表 + `HOSPITAL_IO` 语义。

### 3.2 追溯码记录（TraceNumberRecord，子实体）

对应 `cus_trace_number_flow`（物理表名历史保留；领域模型称 **record**）。

| 属性 | 字段名 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| 记录标识 | `id` | `Long` | 是 | 雪花 ID |
| 主档标识 | `trace_number_id` | `Long` | 是 | FK → `cus_trace_number.id` |
| 追溯码 | `trace_no` | `String` | 是 | 冗余便于查询 |
| 记录类别 | `flow_category_enum` | `String` | 是 | `HOSPITAL_IO` / `INTERNAL_TRANSFER` |
| 轨迹方向 | `flow_direction_enum` | `String` | 是 | `IN` / `OUT` |
| 仓库类型 | `location_type_enum` | `String` | 否 | 见 §4.2；**记录用** |
| 仓库标识 | `location_id` | `Long` | 否 | **记录用**；库位列表读侧聚合 |
| 仓位标识 | `location_store_id` | `Long` | 否 | 可空 |
| 批号 | `lot_number` | `String` | 是 | 与主档一致 |
| 数量 | `quantity` | `BigDecimal` | 是 | 本条业务数量（一次动账一行，如发 3 瓶则 `quantity=3`） |
| 业务单位 | `unit_code` | `String` | 是 | 本条 `quantity` 的单位 |
| 拆零量 | `split_quantity` | `BigDecimal` | 是 | 写侧换算后落库；读侧库位轧差直 SUM（**BR-TNR-008**） |
| 操作类型 | `operation_type` | `String` | 是 | 见 §4.4 |
| 业务单据号 | `bus_no` | `String` | 否 | 上游单号 |

> 子表 **只 INSERT**；禁止 UPDATE 业务字段、禁止物理 DELETE。

---

## 4. 值对象（含枚举）

### 4.1 项目所属表类型（ItemTableType）

标识追溯对象所属的业务表类型，确保不同业务类型的追溯码不会混用。

| 枚举值 | 英文 | 说明 | 对应聚合 |
|---|---|---|---|
| 药品 | `medication` | 药品类追溯（含中药饮片、化学药、生物制品） | MedicationDefinition |
| 器材 | `device` | 器材/高值耗材类追溯 | DeviceDefinition |

> **持久化提示**：业务表 `item_table` 常直接存物理表名（如 `med_medication_definition`、`adm_device_definition`），与上表领域枚举一一映射（承接早期版本 §8）。

### 4.2 仓库类型（LocationType）

标识追溯轨迹发生的仓库分类。

| 枚举值 | 英文 | 说明 |
|---|---|---|
| 药房 | `pharmacy` | 门诊药房、住院药房 |
| 药库 | `drug-warehouse` | 药品总库/二级库 |
| 器材库 | `device-warehouse` | 耗材/器材仓库 |
| 科室 | `department` | 科室二级库 |

### 4.3 轨迹方向（TraceDirection）

子表 `flow_direction_enum`：`IN` / `OUT`。

### 4.4 流水类别（TraceFlowCategory）

子表 `flow_category_enum`：`HOSPITAL_IO`（**§2.4.1 白名单**，动主表剩余与在院态）/ `INTERNAL_TRANSFER`（**§2.4.2**，院内流转成对，不动主表）。

### 4.5 在院状态（InHospitalStatus）

主表 `in_hospital_status_enum`：`IN_HOSPITAL` / `NOT_IN_HOSPITAL`。

### 4.6 追溯码单位（`trace_unit_code`）与比例快照（`trace_unit_ratio`）

码所贴包装单位。**新建码**：同意入库/期初确认时从**药典** `MedicationDefinition` / `DeviceDefinition` 的 `trace_unit_code` + `trace_unit_ratio` 写入主档（可游离于五类业务单位）。进货/期初单据**不采集**；**`wkf_supply_request` 无 `trace_unit_code` 列**（已删）。**已建码**：应扫码数与主档剩余盖住只认主档本字段及 **`trace_unit_ratio` 快照**；药典变更不回写历史码。剩余一律记拆零实量（半盒无特殊态）。**发药/退药对库存账面的扣加**不读本字段：发放行 **`unit_code` 操作单位 × 目录五类拆零倍数**（**AS-OP-PHA-001 BR-OPP-017**）；贴码单位与「盒」同码时不得用本倍数把 10 盒扣成 10 片。

### 4.7 操作类型（`operation_type` / `TraceNumberRecordOperationType`）

标识追溯轨迹的业务来源。**主轴**与 **`wkf_supply_request.type_enum`（`SupplyType#getCode()`）对齐**：凡由供应请求单驱动的入库/出库/调拨/科室领用/科室退领等追溯，`operation_type` **必须** 填写与该行供应单 **`type_enum`** 相同的字面值（例如药库进货为 **`CABINET_INBOUND_RECEIPT`**、科室领用为 **`DEPT_ISSUE`**、科室退领为 **`DEPT_ISSUE_RETURN`**），禁止平行使用与供应域不一致的别名（如 ~~`purchase-in`~~）。**扩展**：不落在供应请求上的门诊发药、多发药、患者退药等，使用专用码 **`MEDICATION_DISPENSE_OUT`**、**`MEDICATION_EXTRA_DISPENSE_OUT`**、**`MEDICATION_RETURN_IN`**（见 `com.zxplat.domain.share.enums.TraceNumberRecordOperationType`）。持久化一律为 **字符串 code**（B.10.8.1）。

| 分组 | 持久化码（示例） | 说明 |
|---|---|---|
| 供应域（与 `SupplyType` 同码） | `CABINET_INBOUND_RECEIPT`、`PHARMACY_STOCK_IN`、`CABINET_STOCK_OUT` 等 | 与 `wkf_supply_request.type_enum` 一致，按实际业务单类型取值 |
| 门诊发药出库（扩展） | `MEDICATION_DISPENSE_OUT` | 常规发药出库追溯 |
| 多发药出库（扩展） | `MEDICATION_EXTRA_DISPENSE_OUT` | 补发/加发等出库追溯 |
| 退药入库（扩展） | `MEDICATION_RETURN_IN` | 患者退药等入库追溯 |

### 4.8 库位追溯键（TraceNumberLocationKey，读侧值对象）

联合 `trace_no + location_id` 用于子表聚合「某库房有哪些码」；**不用于**主表在院判定。

### 4.9 扫码身份解析状态（TraceIdentityResolveStatus）

批量反查主表时，**每码**返回下列状态之一（存枚举 **code** 字符串）：

| 枚举 code | 说明 |
|---|---|
| `OK` | 主表命中，返回 `item_id`、`lot_number`、`trace_unit_code`、在院态与剩余 |
| `NOT_FOUND` | 主表无此码（进货扫码建档前、或码从未入库） |
| `NOT_IN_HOSPITAL` | 主表命中但 `in_hospital_status_enum = NOT_IN_HOSPITAL` 或 `remaining_split_quantity ≤ 0` |
| `DUPLICATE_IN_REQUEST` | 同一请求内重复提交相同 `trace_no`（应用层去重标记） |

> **行级匹配**（码填到哪条发药行/供应行）**不在**本聚合 QueryService 内完成；由各业务 AS 前端按 `item_id + lot_number` 与页面候选行匹配，保存时各场景写 IF 复读 Gate。

---

## 5. 领域事件

### 5.1 事件定义

| 事件名称 | 英文名称 | 触发时机 | 携带数据 | 消费方 |
|---|---|---|---|---|
| 追溯记录已写入 | `TraceNumberRecordRecordedEvent` | `recordTraceRecords` 成功后 | 主档 id、trace_no、记录条数、operation_type、bus_no | 审计、进销存（可选） |
| 追溯主档已初始化 | `TraceNumberInitializedEvent` | 进货首次建主档时 | trace_no、item_id、trace_unit_code | 审计 |

### 5.2 事件触发规则

| 操作 | 发布事件 |
|---|---|
| 首次进货建主档 + 记记录 | `TraceNumberInitializedEvent` + `TraceNumberRecordRecordedEvent` |
| 后续动账记记录 | `TraceNumberRecordRecordedEvent` |

> 单表时代 `cus_trace_number` + `cus_trace_number_flow` 及 `append*` / `adjust*` API **已删除**；统一 `recordTraceRecords` 写入。

---

## 6. 领域服务

### 6.1 写侧领域服务（TraceNumberDomainService）

包路径：`com.zxplat.domain.custom.tracenumber`。

#### 6.1.1 记录追溯码记录（`recordTraceRecords`）

- **业务意义**：统一动账入口：INSERT 子表记录 + 同步 UPDATE 主表（`HOSPITAL_IO` 时增减 `remaining_split_quantity` 与在院状态）。
- **入参**：`RecordTraceRecordsCommand`（含多行 `RecordLine`：`trace_no`、`flow_category_enum`、`flow_direction_enum`、`quantity`、`unit_code`、`unit_conversion_source`（**应用层预加载**，领域层**禁止**跨聚合查药品目录）、`location_id`、`operation_type`、`bus_no` 等；进货行含 `trace_unit_code`）。
- **出参**：新插入记录主键列表。
- **前置条件**：`HOSPITAL_IO` 出库前主表须在院且剩余充足；`INTERNAL_TRANSFER` 须成对出现；`unit_conversion_source` 非空且单位可换算为拆零。
- **聚合内影响**：无码则 INSERT 主档；INSERT 子表（含 `split_quantity`）；按类别 UPDATE 主档。
- **事件输出**：`TraceNumberRecordRecordedEvent`（及首次 `TraceNumberInitializedEvent`）。

#### 6.1.2 从业务行批量记录（`recordTraceRecordsFromLineItems`）

- **业务意义**：由供应/发放行 `trace_no` 逗号串展开，结合行数量与单位调用 `recordTraceRecords`。
- **边界**：一行业务一次或按数量拆多行记录，由 Command 指定；禁止空码；每行须携带 `unit_conversion_source`。
- **每码数量（Support 展开，BR-TNR-016）**：**入库 IN / 发药 OUT / 退药 IN** 异阶时按拆零公比**前填分摊**（**剩余=实入/实发拆零**，半盒允许）；同阶（业务单位=主档贴码单位）时每码 ±1。应扫数 = ceil(行拆零/每码拆零)。新建码贴码单位与比例取药典（非默认包装）。

#### 6.1.3 按业务单冲平在院 HOSPITAL_IO IN（`reverseHospitalIoInByBusNo`）

- **业务意义**：期初作废/覆盖前，按 `bus_no` 将该单已写入的 **HOSPITAL_IO IN** 剩余冲平（出库对冲），使主档不再保留该单造成的在院量。
- **边界**：仅冲本 `bus_no` 相关记录；**真覆盖**场景由应用在写新 IN **之前**调用（或 VoidOrchestrator 内）；禁止仅靠增量 IN 叠量。
- **应用编排**：同意路径见 **AS-BAS-INV-001** IF-08；作废见 `InventoryOpeningVoidOrchestrator`。

### 6.2 读侧查询服务（TraceNumberQueryService）

包路径：`com.zxplat.domain.custom.tracenumber`。读侧**禁止**跨聚合查药品目录（**B.6.4**）；库位轧差直读子表 `split_quantity`。

#### 6.2.1 按码查主档（`getTraceNumberByTraceNo`）

- 返回主表：在院状态、`remaining_split_quantity`、`trace_unit_code` 等。

#### 6.2.2 按码查记录时间序（`listRecordsByTraceNo`）

- 子表全量轨迹；含 `location_id`、`quantity`、`unit_code`、`split_quantity`、`flow_category_enum`。

#### 6.2.3 院内在院码分页（`pageInHospitalTraceNumbers`）

- 主表 `in_hospital_status_enum = IN_HOSPITAL` 分页。

#### 6.2.4 按库位查关联码（`listTraceNumbersByLocationId`）

- **子表聚合**：按 `location_id` SUM `split_quantity` 轧差净量 > 0 的 `trace_no` 列表。

#### 6.2.5 追溯记录审计分页（`getTraceRecordPage`）

- 子表组合筛选分页（管理端审计）；入参 `TraceRecordPageQuery`（`recordTimeStart`/`recordTimeEnd`）。

#### 6.2.6 批量扫码身份反查（`batchResolveTraceIdentityByTraceNos`）

- **业务意义**：用户一次性扫入多枚追溯码时，按码批量反查**主表**，得到品项身份与在院态，供前端将码自动填到对应发药行、供应行或汇总组（匹配键：`item_id` + `lot_number` + `trace_no`）。
- **入参**：`traceNos`（`List<String>`，规范化后、请求内去重；单次上限由应用层约定，建议 ≤ 50）。
- **出参**：与入参**同序**的 `TraceNumberIdentityQueryModel` 列表（每码一条；未命中亦占位返回 `resolveStatus`）。
- **查询策略**：`WHERE trace_no IN (...)` 批量读主表；**禁止**逐码 N+1。
- **不含**：候选行业务 id、码池校验、跨患者歧义消解——由 **AS-CUS-TRC-001 · IF-TRC-21** 及各场景 AS 叠加。

#### 6.2.7 按库存行查本库仍在库码（`listInStockTraceNumbersByInventoryItemKey`）

- **业务意义**：库存明细行点击「追溯码」时，查指定库房 + 药品 + 批号下**子表聚合净拆零量 > 0** 的追溯码列表。
- **入参**：`locationId`、`itemTable`（本期固定 **`medication`**）、`itemId`、`lotNumber`。
- **出参**：`List<InventoryItemTraceNumberQueryModel>`（`traceNo`、`locationNetSplitQuantity`、**`remainingSplitQuantity`**（主档拆零剩余）、`traceUnitCode`、`traceUnitRatio` 等）。
- **查询策略**：子表按 `location_id` 轧差 + 主表 `item_id`/`lot_number`/`item_table` 过滤；**仅**净量 > 0。
- **绑定**：**AS-INV-WHS-004 · IF-INV-DTL-13** / **IF-TRC-22**。
- **关联**：扫码弹窗药品展开走页面 **`hospitalWide`** → 应用层调 **`listInHospitalTraceNumbersByItem`**（该药全部批号、主档在院且剩余 > 0；出参含 `lotNumber`；**不**写主档 `location_id`）。

#### 6.2.8 追溯码报表分页（`pageTraceNumberReport）

- **业务意义**：追溯码查询报表列表；支持全院 / 指定库房、在院态、本库态、追溯码/药品/批号关键字、流水时间区间组合筛选。
- **入参**：`TraceNumberReportPageQuery`（含 `queryScope`、`locationId`、`inHospitalStatusEnum`、`locationStockStatusEnum`、`traceNoKeyword`、`medicationItemIds`（应用层预解析）、`lotNumberKeyword`、`flowTimeStart`/`flowTimeEnd`、分页）。
- **出参**：`Page<TraceNumberReportRowQueryModel>`（主表在院态、本库净量/状态、最近流水时间、品项批号等）。
- **查询策略**：主表 + 子表聚合；流水时间用子表 EXISTS；`item_table=medication`。
- **绑定**：**AS-CUS-TRC-002 · IF-TRC-RPT-02** / **IF-TRC-23**。

#### 6.2.9 发药 OUT 前剩余拆零量断言（`assertRemainingSplitSufficientForDispenseOut`）

- **业务意义**：保存配药/发药写库前，按逗号串码列表校验主档 **`remaining_split_quantity`** 是否覆盖本次 OUT 扣减量；规则与 **`TraceNumberRecordLineAssemblySupport`** 展开一致（**BR-TNR-016**）。
- **入参**：`traceCodes`（集合）、`lineQuantity`（发药行业务数量）、`unitCode`（发药业务单位）、`unitConversionSource`（药品单位换算源，由应用/support 预加载）。
- **规则摘要**：应扫码数 = 操作量按主档 `trace_unit_code` 换算（同阶直接用操作量；异阶用拆零公比向上取整，至少 1）；每码校验 `remaining_split_quantity` 覆盖本次分摊扣减（单码发 3 片则该码剩余 ≥ 3）。
- **失败**：码不在院/剩余不足/码数与发药量不一致 → **`BusinessException`**（**`TRACE_NUMBER_NOT_IN_HOSPITAL`** / **`TRACE_NUMBER_INVALID_ARGUMENT`**）。
- **绑定**：**AS-OP-PHA-001 · IF-OPP-04**（**BR-OPP-014**）；**AS-CUS-TRC-001 · IF-TRC-16**（**`TraceNumberDispenseTraceOrchestrator`** 写前亦可再调用）。

#### 6.2.10 按库存行查追溯流水（`listFlowRecordsByInventoryItemKey`）

- **业务意义**：CHS 进销存 **3505/3506** 读路径：按库位 + 品项 + 批号返回**全部**子表流水（**非轧差**）；调用方按 `recordDirectionEnum` / `busNo` 过滤 OUT/IN。
- **入参**：`locationId`、`itemTable`（默认药品表）、`itemId`、`lotNumber`。
- **出参**：`List<TraceNumberRecordQueryModel>`；参数不全时返回空列表。
- **绑定**：`zxplat-module-chs-jilin` · `InventoryUploadContextLoader` / `InventoryUploadTraceLineSupport` / `InventoryThirdPartyUploadOrchestrator`。

#### 6.2.11 品批是否已有主档（`existsMasterByItemLot` / `batchExistsMasterByItemLot`）

- **业务意义**：判定 **有码批 / 无码批**（**AS-CUS-TRC-001 · BR-TRC-020**）。主表该 `item_table+item_id+lot_number` 存在任一未删除行即为有码批（**与在院态、剩余量无关**）。
- **入参**：`itemTable`、`itemId`、`lotNumber`；批量键列表。
- **出参**：`boolean` / `Map<key, Boolean>`。
- **用途**：发药 slip 回填 `lotHasTraceMaster`；写侧 OUT/调拨对无码批跳过主表流水（仅业务行 `trace_no`）。
- **绑定**：**AS-CUS-TRC-001 · BR-TRC-020**；门诊/住院发药 IF-OPP/IPP slip。

#### 6.2.12 进货/退供应商按单列小码（msfx 专用封装）

> **裁决**：码上放心 Gap **UPL-01=B**（2026-08-15）。在既有 `listHospitalIoIn/OutRecordsByBusNoAndOperationTypes` 之上提供语义化封装，供 `zxplat-module-mashangfangxin` 组报；实现可委托通用方法，**禁止**平行 SQL。

##### `listInboundSmallCodesByBusNo(String busNo)`

- **业务意义**：药库采购入库同意后，按 `bus_no` 列出 **HOSPITAL_IO IN** 小码流水（去重后供 102 上传）。
- **实现口径**：`listHospitalIoInRecordsByBusNoAndOperationTypes(busNo, [CABINET_INBOUND_RECEIPT])`，再按 `trace_no` 去重保序。
- **出参**：`List<TraceNumberRecordQueryModel>`（或仅小码字符串列表的薄封装，SDD/实现二选一，字段须含 `traceNo`）。
- **绑定**：**AS-MSFX-UPL-001 · IF-02/08**（`his_scene=WAREHOUSE_PURCHASE_IN`）。

##### `listSupplierReturnSmallCodesByBusNo(String busNo)`

- **业务意义**：药库退供应商同意后，按 `bus_no` 列出 **HOSPITAL_IO OUT** 小码（供 202 上传）。
- **实现口径**：`listHospitalIoOutRecordsByBusNoAndOperationTypes(busNo, [CABINET_RETURN_TO_SUPPLIER])`（operation_type 与 `SupplyType.CABINET_RETURN_TO_SUPPLIER` 同字面），再按 `trace_no` 去重保序。
- **出参**：同入库封装。
- **绑定**：**AS-MSFX-UPL-001 · IF-02/08**（`his_scene=SUPPLIER_RETURN`）。

> 门/住发药、退药继续用既有 `listDispenseHospitalIoOutRecordsByBusNo` / 退药 IN 镜像方法，不在本小节重复定义。

### 6.3 领域边界说明

- **在院语义**：以主表 `in_hospital_status_enum` + `remaining_split_quantity` 为准（BR-TNR-003）。
- **库位语义**：以子表 `TraceNumberLocationKey` 聚合为准（BR-TNR-013）。
- **写路径唯一**：业务 AS 必须调用 `recordTraceRecords`，禁止平行写主/子表。
- **跨聚合禁令（B.6.4）**：`TraceNumberDomainService` / `TraceNumberQueryService` **不得**注入 `MedicationDefinitionQueryService` 等外聚合 QS；写侧换算源由 Command 携带，读侧库位轧差读 `split_quantity`。
- **扫码读路径**：`batchResolveTraceIdentityByTraceNos` **只读**；预览结果不可替代保存 Gate（**BR-TRC-014**）。
- **本期成码范围（2026-07-06）**：领域读/写仅 **药品**（`item_table=med_medication_definition`）；器材路径文档保留、实现待后续。

---

## 7. 不变式

| 编号 | 不变式 | 说明 |
|---|---|---|
| BR-TNR-001 | 主档须含 `trace_no`、`item_table`、`item_id`、`lot_number`、`trace_unit_code` | 主档完整性 |
| BR-TNR-002 | 子表流水只 INSERT；禁止改业务字段 | 审计不可篡改 |
| BR-TNR-003 | **在院**以主表为准；**仅 §2.4.1 `HOSPITAL_IO` 白名单**改变 `remaining_split_quantity`；`NOT_IN_HOSPITAL` 须剩余 ≤ 0 | 全院语义 |
| BR-TNR-004 | 发药/退药患者追溯按 `operation_type` + `flow_direction_enum` 查子表 | 患者追溯 |
| BR-TNR-005 | 查询默认过滤 `is_deleted=false` | 软删 |
| BR-TNR-006 | `INTERNAL_TRANSFER` 须同 `bus_no` 成对 OUT+IN；**不改变**主表剩余 | 调拨方案 A |
| BR-TNR-007 | 逗号拆码须清洗空值 | 数据清洁 |
| BR-TNR-008 | 写子表时须换算拆零落库 `split_quantity` 后再动主表；读侧库位轧差直 SUM `split_quantity` | 单位一致 |
| BR-TNR-009 | 主表禁止存 `location_id` | 职责分离 |
| BR-TNR-010 | 子表 `flow_direction_enum` 创建后不可改 | 流水不可变 |
| BR-TNR-011 | `HOSPITAL_IO` 出库不得超过主表 `remaining_split_quantity`（换算后） | 防超发 |
| BR-TNR-012 | UDI/药监码格式由应用层 Gate 校验 | 合规 |
| BR-TNR-013 | 库位码列表仅子表按 `location_id` 聚合；loc **不参与**主表在院计算 | 双轨读法 |
| BR-TNR-014 | 动账须同事务：INSERT 子表 + UPDATE 主表（如需） | 主子同步 |
| BR-TNR-015 | 批量身份反查须 `IN` 批量读主表；请求内 `trace_no` 去重；单次条数上限由应用层 Gate | 扫码性能 |
| BR-TNR-016 | 逗号串展开：异阶按拆零前填分摊（剩余=实入/实发拆零，半盒允许）；同阶每码 ±1 贴码单位；应扫数 ceil；新建码取药典单位+比例快照；**Gate 与写侧一致** | 写 IF / 发药 Pre |

---

## 8. 上下文映射

### 8.1 上游依赖

| 上游上下文 | 聚合 | 关联方式 | 说明 |
|---|---|---|---|
| 器材定义 | DeviceDefinition | `item_id` + `item_table`（device） | 器材类追溯对象，提供 UDI 编码定义 |
| 药品定义 | MedicationDefinition | `item_id` + `item_table`（medication） | 药品类追溯对象；目录五类单位供 `trace_unit_code` 校验 |
| 位置管理 | Location | 子表 `location_id`（**记录用**） | 流水发生的仓库/库位主数据；**主表不引用** |
| 供应请求/发放 | SupplyRequest / SupplyDelivery | `bus_no`、行 `trace_no` | 入库/出库/调拨等业务来源；`trace_unit_code` 在进货行初始化 |
| 药品发放 | MedicationDispense | 行 `trace_no`（**暂存**） | 护士分配/退药带出码；患者追溯经应用层跨域查询 |

### 8.2 下游影响

| 下游上下文 | 聚合 | 触发方式 | 说明 |
|---|---|---|---|
| 审计日志 | AuditEvent | 事件订阅 | 监听 `TraceNumberRecordRecordedEvent` / `TraceNumberInitializedEvent` |
| 药监上报 | 外部接口 | 事件订阅/定时任务 | 按子表流水汇总上报国家药品追溯平台 |
| 库存查询 | InventoryItem | **无直接写依赖** | 库存主账在 **InventoryItem**；追溯主/子表**不维护**库存数量 |

### 8.3 引用规则

> **统一规则**：
> - 主档引用：`TraceNumberId`（`cus_trace_number.id`）
> - 记录引用：`TraceNumberRecordId`（`cus_trace_number_flow.id`）；业务侧通常按 `trace_no` + `bus_no` 关联
> - 品项引用：`item_id` + `item_table` → DeviceDefinition / MedicationDefinition
> - 库位引用：仅子表 `location_id` → Location；**禁止**在主表存 `location_id`
> - **不持有患者引用**——患者追溯经 MedicationDispense / DeviceDispense 反查子表 `operation_type` + `flow_direction_enum`

### 8.4 事件流示意图

```
SupplyRequest     SupplyDelivery     MedicationDispense     TraceNumber              审计/药监
      │                  │                    │                    │                      │
      │── 进货同意 ─────>│                    │                    │                      │
      │  trace_no        │                    │                    │                      │
      │  trace_unit_code │                    │                    │                      │
      │──────────────────────────────────────>│ recordTraceRecords   │                      │
      │                  │                    │  INSERT record     │                      │
      │                  │                    │  UPDATE 主档       │                      │
      │                  │                    │── InitializedEvent ──────────────────────>│
      │                  │                    │── RecordRecordedEvent ───────────────────>│
      │                  │                    │                    │                      │
      │── 调拨同意 ─────>│                    │                    │                      │
      │                  │───────────────────>│ INTERNAL_TRANSFER  │                      │
      │                  │                    │  成对 OUT+IN       │                      │
      │                  │                    │  (主档剩余不变)     │                      │
      │                  │                    │                    │                      │
      │                  │── 汇总核发 ───────>│                    │ HOSPITAL_IO OUT      │
      │                  │  trace_no          │                    │                      │
      │                  │                    │── 护士分配 ───────>│ 仅 MedDispense       │
      │                  │                    │   trace_no         │ 不写主/子表          │
```

---

## 9. 补充

### 9.1 UDI 与国家药监追溯码说明

中国 HIS 系统中，高值耗材追溯涉及两套编码体系：

| 编码体系 | 全称 | 说明 |
|---|---|---|
| UDI | 医疗器械唯一标识（Unique Device Identification） | 由国家药品监督管理局发布，采用 GS1/HIBCC/ICCBBA 标准，覆盖 DI（设备标识）+ PI（生产标识：批号/序列号/有效期） |
| 国家药品追溯码 | 中国药品电子监管码 / 药品追溯码 | 覆盖药品的生产→流通→使用全链路追溯 |

主表 `trace_no` 可存储以上任一编码。UDI 格式校验由应用层 Gate 负责（**BR-TNR-012**），领域层保障主/子表事实一致。

### 9.2 追溯码扫码典型流程

```
场景一：药库进货扫码（HOSPITAL_IO IN）

1. 库管扫描包装追溯码（贴码单位与比例取药典，单据不选）
2. 进货单同意后，应用层拆逗号串 trace_no
3. 每码调用 recordTraceRecords：建主档（写入 trace_unit_code + trace_unit_ratio 快照）+ 首条 HOSPITAL_IO IN
   （异阶前填实入拆零；location_id 记目的药库）
4. 发布 TraceNumberInitializedEvent + TraceNumberRecordRecordedEvent

场景二：院内调拨（INTERNAL_TRANSFER）

1. 调拨单同意，同一 bus_no 成对记：来源仓 OUT + 目的仓 IN
2. 主表 remaining_split_quantity 不变；库位列表由子表聚合更新

场景三：门诊/住院发药（HOSPITAL_IO OUT）

1. 药房从业务行 trace_no 拆码，校验主表在院且剩余充足（全院，不看 loc）
2. 一次发 3 瓶记一条子表：quantity=3、unit_code=瓶；主表剩余减拆零量
3. 耗尽则 in_hospital_status_enum → NOT_IN_HOSPITAL

场景四：住院护士分配（不写主/子表）

1. 汇总核发已在 IF-TRC-15 记 HOSPITAL_IO OUT
2. 护士 AS-IP-NUR-015 仅 UPDATE MedicationDispense.trace_no

场景五：批量扫码自动匹配（IF-TRC-21）

1. 用户一次扫入多枚码（扫码枪逗号/回车分隔）
2. 前端调用 IF-TRC-21 → 主表批量反查得 item_id + lot_number + trace_unit_code + 在院态
3. 前端用 medicationId + lotNumber 与当前页候选行（发药明细/供应行/护士汇总组）匹配填位
4. 同药同批多行时前端处理歧义（未配满优先/用户点选）；保存时各场景写 IF 复读 Gate
```

### 9.3 与 MedicationDispense / InventoryItem 的职责边界

| 职责 | MedicationDispense | InventoryItem | TraceNumber + TraceNumberRecord |
|---|---|---|---|
| 管理对象 | 单次发放/退药行 | 批号维库存数量 | 追溯码在院态 + 动账记录 |
| 追溯码 | 逗号串 `trace_no` 暂存 | 不持有 | 主表单码 + 子表记录 |
| 库位 | 不维护码库位 | 五元组库位账 | 子表 `location_id` 仅记录 |
| 患者关联 | 直接关联患者 | 无 | 无（经 MedDispense 反查） |
| 写模型 | 护士可 UPDATE `trace_no` | 库存增减 | recordTraceRecords 统一入口 |

### 9.4 关联文档

| 文档 | 路径 |
|---|---|
| 应用服务编排 | `AS-CUS-TRC-001-追溯码流转.md` v1.6 |
| 查询报表 AS | `AS-CUS-TRC-002-追溯码查询报表.md` |
| 详细设计 SDD | `DM-CUS-010-TraceNumberRecord-追溯码记录-SDD.md` |

---

### 9.5 变更历史

| 版本 | 日期 | 摘要 |
| --- | --- | --- |
| v2.0.10 | 2026-08-19 | **§4.6 / `trace_unit_ratio`**：贴码倍数仅应扫码数与主档剩余盖住；**禁止**用于 `wkf_inventory_item` 发退扣加（扣库走发放操作单位×五类倍数，**BR-OPP-017**） |
| v2.0.9 | 2026-08-15 | **§6.2.12** `listInboundSmallCodesByBusNo` / `listSupplierReturnSmallCodesByBusNo`（msfx UPL-01=B） |
| v2.0.8 | 2026-07-24 | **§6.2.11** `existsMasterByItemLot` / `batchExistsMasterByItemLot`（有码批/无码批 · BR-TRC-020） |
| v2.0.7 | 2026-07-11 | **§6.2.10** `listFlowRecordsByInventoryItemKey`（CHS 3505/3506 非轧差流水读；**zxplat-module-chs-jilin**） |
| v2.0.6 | 2026-07-08 | **§6.2.9** **`assertRemainingSplitSufficientForDispenseOut`**；**§6.1.2/BR-TNR-016** 每码数量展开规则（入库 1 码 1 盒；销售包装发药 ±1 包装） |
| v2.0.5 | 2026-07-06 | **成码对齐**：包 `com.zxplat.domain.custom.tracenumber`（**`tracenumberrecord` 已删**）；子实体 `TraceNumberRecord`；`recordTraceRecords`；`split_quantity`（V109）；禁止跨聚合（B.6.4）；事件 `TraceNumberRecordRecordedEvent` |
| v2.0.4 | 2026-07-06 | 成码配套：Flyway/迁移/换算/聚合算法见 **DM-SDD §13～§16**；`trace_unit_code` 见 **DM-WKF-006**；**领域成码本期仅药品**（`med_medication_definition`） |
| v2.0.3 | 2026-07-06 | **查询报表读侧**：§6.2.7 库存行本库仍在库码；§6.2.8 报表分页；对齐 AS-CUS-TRC-002 / IF-TRC-22/23 |
| v2.0.2 | 2026-07-06 | **§2.4 业务分类**：动在院八类 vs 院内流转；在院翻转须剩余耗尽 |
| v2.0.1 | 2026-07-06 | **批量扫码身份反查**：§4.9、`batchResolveTraceIdentityByTraceNos`、BR-TNR-015；对齐 AS v1.4 · IF-TRC-21 |
| v2.0.0 | 2026-07-06 | **主子表重构**：`cus_trace_number` 主档 + `cus_trace_number_flow` 流水；在院/剩余与库位双轨；`trace_unit_code`；`recordTraceRecords` 统一写路径；废弃单表 IN/OUT 净额派生 |
| v1.1.1 | 2026-05-28 | 单表追加式（已 superseded） |

<!-- MODE-B-METHOD-INVENTORY 2026-09-23 -->

## 附录 · 模式 B 已实现方法清单（2026-09-23）

> 自 zxplat-domain 对应 *DomainService / *QueryService 抽取 public 方法名，用于关闭方法白名单 DOC-DRIFT。细规仍以正文 §6 与源码为准；本表不替代九章成稿。

### DomainService：`TraceNumberDomainService`

| 方法 |
|------|
| `recordTraceRecords` |
| `recordTraceRecordsFromLineItems` |
| `reverseHospitalIoInByBusNo` |

### QueryService：`TraceNumberQueryService`

| 方法 |
|------|
| `getTraceNumberByTraceNo` |
| `findTraceUnitCodeByTraceNo` |
| `findTraceUnitRatioByTraceNo` |
| `listRecordsByTraceNo` |
| `pageInHospitalTraceNumbers` |
| `listTraceNumbersByLocationId` |
| `getTraceRecordPage` |
| `batchResolveTraceIdentityByTraceNos` |
| `listInStockTraceNumbersByInventoryItemKey` |
| `listInHospitalTraceNumbersByItem` |
| `listInHospitalTraceNumbersByItemLot` |
| `existsMasterByItemLot` |
| `findTraceUnitSnapshotByItemLot` |
| `batchFindTraceUnitSnapshotByItemLot` |
| `batchExistsMasterByItemLot` |
| `buildItemLotMasterExistsMapKey` |
| `ItemLotMasterExistsLookupKey` |
| `resolvePreferredTraceUnitSnapshot` |
| `batchResolvePreferredTraceUnitSnapshots` |
| `TraceUnitSnapshotLookupKey` |
| `buildTraceUnitSnapshotMapKey` |
| `listFlowRecordsByInventoryItemKey` |
| `pageTraceNumberReport` |
| `assertNoDuplicateTraceCodes` |
| `assertMasterCodesNotExhaustedForDispense` |
| `assertCodesInHospitalForDispense` |
| `assertCodesInNetStockForDispenseLine` |
| `isRemainingSplitSufficientForDispenseOut` |
| `allocateRemainingFirstSplitForDispenseOut` |
| `listDispenseHospitalIoOutRecordsByBusNo` |
| `listUnreturnedDispenseHospitalIoOutRecordsByTraceNos` |
| `listHospitalIoOutRecordsByBusNoAndOperationTypes` |
| `listHospitalIoInRecordsByBusNoAndOperationTypes` |
| `listInboundSmallCodesByBusNo` |
| `listSupplierReturnSmallCodesByBusNo` |
| `listDistinctInboundReceiptBusNosSince` |
| `getTraceNo` |
| `setTraceNo` |
| `getSplitQuantity` |
| `setSplitQuantity` |

