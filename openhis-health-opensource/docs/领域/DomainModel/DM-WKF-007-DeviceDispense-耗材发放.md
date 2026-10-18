# DM-WKF-007 DeviceDispense 耗材发放

> **限界上下文**：WKF 域 · 耗材发放管理  
> **聚合类型**：实体聚合  
> **版本**：v1.2.11

---

## 1. 概述

### 1.1 业务定义

耗材发放（DeviceDispense）记录耗材从请求到实际发放的业务事实，是库存扣减、追溯码出库、收费联动的重要事实单据。

### 1.2 核心职责


| 职责        | 说明                                          |
| --------- | ------------------------------------------- |
| 发放事实留痕    | 记录发放数量、批号、发放人、时间、位置等                        |
| 发放状态管理    | 管理草稿、待配药、待发药、已发放、作废、停止、撤回、已汇总等状态            |
| 执行与请求关联   | 通过 `device_req_id`、`procedure_id` 回溯请求和执行过程 |
| 库存与追溯协同锚点 | 为库存和追溯系统提供批号、位置、追溯码依据                       |


### 1.3 适用范围


| 场景      | 说明                                                       |
| ------- | -------------------------------------------------------- |
| 医嘱草稿生成  | 医嘱保存后生成发放草稿（`DRAFT`）                                     |
| 收费推进待配药 | 收费/划价完成后，可将关联行批量由 `DRAFT` 推进为 `PREPARATION`（由用例编排调用写侧能力） |
| 执行生成待配药 | 医嘱执行后生成待配药/已发放单                                          |
| 发放工作台操作 | 药房工作台完成发放、作废、停止、汇总                                       |
| 逆向流程    | 取消执行、撤回、退耗材等逆向场景                                         |


### 1.4 与 MedicationDispense 对称（门诊计费锚点）

门诊医生站路径下，**耗材与药品**在「暂存不选库房/批号 → 签发拆单 → 每条发放物理行落库房/批号/数量」上同构。与 **`MedicationDispense`** 一致：**每条** `DeviceDispense` 物理行与 **`ChargeItem` 一对一**（`service_table` + `service_id` 锚定发放表主键，口径见 **`DM-ADM-011`** **`BR-CHG-001`** 及该文档 §3.1 表下说明）；本聚合方法边界仍**不写** `ChargeItem` 本体，由应用层计费编排创建/激活。定价上 **`ChargeItemDefinition`** 的 **`LOT_NUMBER_PRICE`** 与批号绑定，**入账金额由后端按定义重算**，前端展示金额仅作校验。


---

## 2. 聚合根与持久化形态

### 2.1 持久化形态

**持久化形态**：主表（无聚合内子表）。

**表结构**：

- 主表：`wkf_device_dispense`（主键：`id`）
- 子表：无。

**高频查询模式**：

- 按发放标识：主键查询。
- 按耗材请求：主表 `device_req_id` 索引扫描。
- 按执行记录：`procedure_id` 索引扫描。
- 按就诊/患者：主表 `encounter_id` / `patient_id` 辅助列表与分页。

### 2.2 聚合根标识


| 属性     | 类型                 | 说明                                  |
| ------ | ------------------ | ----------------------------------- |
| 耗材发放标识 | `DeviceDispenseId` | 全局唯一，对应主表主键 `id`（雪花或序列策略以落库与交互契约为准） |
| 发放业务号  | `bus_no`（业务侧）      | `getSeqByDay(DDP, 4)` 按日流水；与表字段 `bus_no` 一致 |


### 2.3 状态机（DispenseStatus）

```text
[DRAFT(草稿)] ──> [PREPARATION(待配药)] ──> [IN_PROGRESS(待发药)] ──> [COMPLETED(已发放)]
                        │                                                     │
                        ├──> [ON_HOLD(暂停)] ──> [STOPPED(停止)]              ├──> [SUMMARIZED(已汇总)]
                        ├──> [PREPARED(已配药)]                               ├──> [CANCELLED(撤回)]
                        └──> [DECLINED(拒绝)] ──> [STOPPED]                  ├──> [REFUNDED(已退药)]
                                                                             └──> [PENDING_REFUND] ──> [REFUNDED]
```

#### 状态定义


| value | code           | info | 语义                         |
| ----- | -------------- | ---- | -------------------------- |
| 0     | DRAFT          | 草稿   | 发放草稿，可修改/删除，允许修改 / 删除 / 执行 |
| 1     | PREPARATION    | 待配药  | 执行后生成，等待配药，允许配药 / 暂停 / 拒绝  |
| 2     | PREPARED       | 已配药  | 已配药待发，允许发药                 |
| 3     | IN_PROGRESS    | 待发药  | 配药完成，等待发药，允许发药 / 停止        |
| 4     | COMPLETED      | 已发放  | 发放完成，允许汇总 / 撤回 / 退药        |
| 5     | SUMMARIZED     | 已汇总  | 汇总中间态                      |
| 6     | PART_COMPLETED | 部分发药 | 与药品共用枚举；**耗材侧不产生**，门禁实现可兼容 |
| 7     | ON_HOLD        | 暂停   | 暂停执行，允许恢复 / 停止             |
| 8     | STOPPED        | 停止   | 停止/作废                      |
| 9     | DECLINED       | 拒绝   | 拒绝发药，允许停止                  |
| 10    | PENDING_REFUND | 待退药  | 申请退药中，允许确认退药               |
| 11    | PART_REFUND    | 部分退药 | 部分退药完成                     |
| 12    | REFUNDED       | 已退药  | 全部退药完成                     |
| 13    | CANCELLED      | 撤回   | 已发放后撤回                     |
| 99    | UNKNOWN        | 未知   | 历史迁移场景                     |


> **状态枚举说明**：持久化字段 `status_enum` 为 **String**，存 `DispenseStatus` 的 **code（英文常量名）**，与语义一一对应；数值 `value` 仅用于文档排序/对照，不作为落库事实源。

---

## 3. 实体

```
DeviceDispense（聚合根，单表聚合）
════════════════════════════════════════════════════════════

  ┌──────────────────────────────────────────────────────┐
  │                   DeviceDispense                      │
  │  bus_no / status_enum / device_req_id / procedure_id │
  │  device_def_id / quantity / unit_code / lot_number   │
  │  location_id / encounter_id / patient_id             │
  │  preparer_id / performer_id / trace_no / planned_dispense_time │
  └──────────────────────────────────────────────────────┘
```

### 3.1 耗材发放（DeviceDispense）

DeviceDispense 聚合根的属性定义，对应 `wkf_device_dispense` 表。


| 属性      | 字段名                         | 类型           | 必填  | 说明                       |
| ------- | --------------------------- | ------------ | --- | ------------------------ |
| 耗材发放标识  | `id`                        | `Long`       | 是   | 主键                       |
| 发放业务号   | `bus_no`                    | `String`     | 是   | 按日流水 `DDP` + yyyyMMdd + 4 位序号 |
| 耗材申请标识  | `device_req_id`             | `Long`       | 是   | 关联耗材请求                   |
| 发放状态    | `status_enum`               | `Integer`    | 是   | `DispenseStatus` 枚举      |
| 来源表     | `based_on_table`            | `String`     | 否   | 基于对象表名                   |
| 来源标识    | `based_on_id`               | `Long`       | 否   | 基于对象 ID                  |
| 未发放原因   | `not_performed_reason_enum` | `Integer`    | 否   | 停止/作废原因（见 §4.2）          |
| 发放类别    | `dispense_category_enum`    | `Integer`    | 否   | 继承请求类别                   |
| 发放数量    | `quantity`                  | `BigDecimal` | 是   | 申请数量                     |
| 发放单位编码  | `unit_code`                 | `String`     | 是   | 计量单位                     |
| 产品批号    | `lot_number`                | `String`     | 否   | 批号                       |
| 耗材定义标识  | `device_def_id`             | `Long`       | 是   | 耗材目录                     |
| 患者标识    | `patient_id`                | `Long`       | 是   | 患者                       |
| 就诊标识    | `encounter_id`              | `Long`       | 是   | 就诊                       |
| 发放人标识   | `performer_id`              | `Long`       | 否   | 发药人                      |
| 发放器材房标识 | `location_id`               | `Long`       | 否   | 器材房                      |
| 发放类型编码  | `part_type_code`            | `String`     | 否   | 历史字段；耗材侧未使用                 |
| 已发放数量   | `dispense_quantity`         | `BigDecimal` | 否   | 实际已发放量                   |
| 配药人标识   | `preparer_id`               | `Long`       | 否   | 配药人                      |
| 配药时间    | `prepare_time`              | `DateTime`   | 否   | 配药时间                     |
| 发药时间    | `dispense_time`             | `DateTime`   | 否   | 发药时间                     |
| 限制发药时间  | `limit_time`                | `DateTime`   | 否   | 过期时间；预减窗口见 **BR-DD-006** |
| 预定发药时间  | `planned_dispense_time`     | `DateTime`   | 否   | 执行生成发放单场景的计划发药时间         |
| 发放目的地   | `des_location_id`           | `String`     | 否   | 目的地标识（物理类型以实现迁移为准）       |
| 接收人     | `rec_practitioner_id`       | `String`     | 否   | 接收人标识（物理类型以实现迁移为准）       |
| 支持信息    | `support_info`              | `String`     | 否   | 辅助说明/支持信息                |
| 使用说明    | `usage_instruction`         | `String`     | 否   | 使用说明快照                   |
| 发放频次编码  | `dispense_frequency_code`   | `String`     | 否   | 与请求侧频次对齐（若有）             |
| 追溯码     | `trace_no`                  | `String`     | 否   | 逗号分隔多码                   |
| 执行标识    | `procedure_id`              | `Long`       | 否   | 关联执行记录                   |


> 审计、租户、逻辑删除等字段继承基类实体（以落库与交互契约为准），不在此重复列举。

### 3.2 跨域关联（非本聚合维护）

- **DeviceRequest**：`device_req_id` 指向请求事实。
- **DeviceDefinition**：`device_def_id` 指向耗材目录。
- **Encounter / Patient**：就诊与患者锚点。
- **库存、收费**：由应用服务编排写入协作域，本聚合仅提供锚点字段与状态。
- **追溯码轨迹**：发放/退回产生的 OUT、IN 轨迹事实写入 `cus_trace_number` + `cus_trace_number_flow`（聚合 **TraceNumberRecord**），定义与字段口径见 **DM-CUS-011-TraceNumberRecord-追溯码记录.md**；本表 `trace_no` 为发放侧展示与校验锚点，与轨迹表通过业务单据号等关联（以 **DM-CUS-011** §4 及编排为准）。

---

## 4. 值对象

### 4.1 发放状态（DispenseStatus）


| 编码值 | code（缩写） | 名称   |
| --- | -------- | ---- |
| 1   | `DR`     | 草稿   |
| 2   | `PR`     | 待配药  |
| 3   | `IN`     | 待发药  |
| 4   | `CO`     | 已发放  |
| 5   | `ON`     | 暂停   |
| 6   | `ST`     | 停止   |
| 7   | `DE`     | 拒绝发药 |
| 8   | `SR`     | 已汇总  |
| 9   | `PC`     | 部分发药 |
| 10  | `PFR`    | 部分退药 |
| 12  | `RE`     | 已退药  |
| 13  | `UN`     | 未知   |
| 14  | `PD`     | 已配药  |
| 15  | `CA`     | 撤回   |
| 16  | `PRD`    | 待退药  |


> **存储口径**：`status_enum` 使用**字符串编码（存枚举 code）**；本节 `code` 列为文档缩写，不作为落库码值；发生冲突时以实现枚举的 code 为权威。

### 4.2 未发放原因（NotPerformedReason）

`DeviceDispense` 停发、作废等场景写入 `not_performed_reason_enum`，与院内码表对齐。


| 编码值 | code | 说明  |
| --- | ---- | --- |
| 1   | `1`  | 退费  |
| 9   | `9`  | 异常  |


> 下拉与展示若存在更多枚举项，以实现码表为准；聚合写库须满足 **BR-DD-003**（停止/作废须填原因）。

### 4.3 协作值对象（推荐收口方式）

下列抽象用于**收口枚举判断、库存协作维度、追溯多码**，与 **§7** 不变式及 **DM-CUS-011** 配套；是否独立为 Java 值对象以仓储层命名为准，**语义**以下列为准：


| 名称              | 职责                                                                                       |
| --------------- | ---------------------------------------------------------------------------------------- |
| **发放状态快照**      | 联合 `status_enum` 与 `not_performed_reason_enum`，统一终态判断与可否继续发放的门禁。                         |
| **库存扣减口径（五元组）** | `device_def_id` + `lot_number` + `quantity` + `unit_code` + `location_id`，与库存域扣减/回补请求一致。 |
| **追溯标识打包**      | 逗号分隔 `trace_no` 的拆分、规范化；无实物码时的**占位生成**须闭合应发数量且不削弱 **BR-DD-002**（策略以落库与交互契约为准）。           |


> 工作台读展示可将 `PREPARATION` / `PREPARED` / `IN_PROGRESS` 合并为「待发药」口径，与持久化枚举并存时须在集成层显式映射。

---

## 5. 领域事件

事件命名以 **DeviceDispense** / **Device** 等为前缀、过去分词节奏（如 **Dispensed**）与院内发放类事件一致；载荷字段以 `zxplat-domain` 及 **§5.1**/**§6.1** 为准。

### 5.1 事件定义


| 事件名称（中文）  | 英文名称                                      | 触发时机                                                                                        | 携带数据（示意）                                         |
| --------- | ----------------------------------------- | ------------------------------------------------------------------------------------------- | ------------------------------------------------ |
| 耗材发放单已生成  | `DeviceDispenseCreatedEvent`              | 发放单生成（`DRAFT` 或 `PREPARATION`，耗材待发拣配）                                                       | 发放标识、`device_def_id`、就诊标识等                       |
| 耗材已拣配     | `DeviceDispensePreparedEvent`             | 拣配/绑定批号与追溯成功（**§6.1.1** `prepareDeviceDispense`）                                            | 发放标识、拣配人、拣配时间等                                   |
| 耗材已发放     | `DeviceDispensedEvent`                    | 核对发放成功（**§6.1.2** `dispenseDevice`） | 发放标识、`device_def_id`、批号、数量等                      |
| 耗材发放已作废   | `DeviceDispenseDeclinedEvent`             | 作废未发成功                                                                                      | 发放标识、`not_performed_reason_enum`                 |
| 耗材发放已停止   | `DeviceDispenseStoppedEvent`              | 停发成功（退费/终止未发等）                                                                              | 发放标识、`device_req_id`、`not_performed_reason_enum` |
| 耗材发放已撤回   | `DeviceDispenseCancelledEvent`            | 已发放后撤回成功                                                                                    | 发放标识、`bus_no`                                    |
| 耗材已退      | `DeviceDispenseRefundedEvent`             | 退耗材确认成功                                                                                     | 发放标识、退回数量等                                       |
| 耗材发放已汇总   | `DeviceDispenseSummarizedEvent`           | 盘点置为已汇总成功（**§6.1.12** `updateDispenseStatusSummarized`）                                     | 发放标识、汇总批次锚点（若有）                                  |
| 盘点回写发放已完成 | `DeviceDispenseSummaryCompletedEvent`     | 盘点回写为已发放成功（**§6.1.13** `updateDispenseStatusCompletedFromSummary`）                          | 汇总锚点、发放标识列表                                      |
| 盘点汇总已取消   | `DeviceDispenseSummaryCancelledEvent`     | 取消盘点汇总成功（**§6.1.14** `cancelDeviceDispenseSummary`）                                         | 汇总锚点                                             |
| 退耗材申请已生成  | `DeviceDispensePendingRefundCreatedEvent` | 待退申请生成                                                                                      | 发放标识、退数量等                                        |
| 预减占用已释放   | `DeviceDispenseReserveReleasedEvent`      | 预减/占用释放                                                                                     | 发放标识、释放数量等                                       |


> **命名说明**：已发放主事件名为 **DeviceDispensedEvent**。若现存实现或既有载荷仍以 **DeviceDispenseConfirmedEvent** 发布，须在演进中收敛为 **DeviceDispensedEvent**，避免双名长期并存。

### 5.2 事件触发规则


| 当前状态（示意）    | 操作               | 目标状态（示意）                           | 发布事件                                                                    |
| ----------- | ---------------- | ---------------------------------- | ----------------------------------------------------------------------- |
| —           | 生成发放单（医嘱开立/保存路径） | `DRAFT`                            | `DeviceDispenseCreatedEvent`                                            |
| —           | 生成发放单（执行路径）      | `PREPARATION` 等                    | `DeviceDispenseCreatedEvent`                                            |
| 草稿          | 推进为待配药           | `PREPARATION`                      | — 或补充领域事件以落库与交互契约为准                                                     |
| 待配药         | 匹配发放批号与拆单        | `PREPARATION`（可拆行，同态）              | —（**§6.1.11** `matchDispenseLotNumber`，可审计补事件以本稿约定为准）                   |
| 待配药         | 拣配/绑定批号追溯        | `PREPARED` / `IN_PROGRESS`（见 §2.3） | `DeviceDispensePreparedEvent`                                           |
| 待发药窗口       | 核对发放             | `COMPLETED`                         | `DeviceDispensedEvent`                                                  |
| 待配药 / 待发药窗口 | 作废未发             | `DECLINED`                         | `DeviceDispenseDeclinedEvent`                                           |
| 可停发态        | 停发               | `STOPPED`                          | `DeviceDispenseStoppedEvent`                                            |
| 待配药         | 盘点置为已汇总          | `SUMMARIZED`                       | `DeviceDispenseSummarizedEvent`（可无，见 **§6.1.12**）                       |
| 已汇总         | 盘点回写为已发放         | `COMPLETED`                        | `DeviceDispenseSummaryCompletedEvent`（可无，见 **§6.1.13**）                 |
| 已汇总         | 取消盘点汇总           | `PREPARATION`                      | `DeviceDispenseSummaryCancelledEvent`                                   |
| —           | 生成待退申请           | `PENDING_REFUND`                   | `DeviceDispensePendingRefundCreatedEvent`                               |
| 待退          | 退耗材确认            | `REFUNDED`                         | `DeviceDispenseRefundedEvent`                                           |
| 草稿 / 待配药    | 释放预减/占用          | 已停止 / 删除等                          | `DeviceDispenseReserveReleasedEvent`                                    |
| 已发放可撤回      | 撤回               | `CANCELLED`                        | `DeviceDispenseCancelledEvent`                                          |


> **状态常量**：「当前状态」中文列与 **§2.3**、`DispenseStatus` 英文常量同义（药品域遗留展示名与持久化枚举并存）；**操作**/**发布事件**列按**耗材发放**域口径书写，并与 **§6.1** 事件输出一致。若编排含 `IN_PROGRESS` 衔接，以实现状态机为准。

> **发布说明**：事件为领域契约；实现若为同步编排未显式发布，须在迁移计划中补齐，避免与 **§5.1** 长期偏离。**§6.1.21** `revertCompletedBatchToPreparation` 等批量回退是否发布事件以落库实现与本稿约定为准（**§5.1** 未单列契约时不得臆造事件名）。

---

## 6. 领域服务

### 6.1 写侧领域服务（DeviceDispenseDomainService）

写侧由应用服务编排事务边界后调用领域服务完成状态与事实收口。

#### 6.1.1 拣配耗材（`prepareDeviceDispense`）

- **业务意义**：在待发拣配态（`PREPARATION`）完成拣配并绑定 `lot_number`、`trace_no` 等，将发放单推进至待发核对发放窗口（`PREPARED` / `IN_PROGRESS`，以实现编排为准）。
- **边界说明**：仅写本聚合；不在本方法内完成库存账面扣减（发放成功由协作方处理，与 **BR-DD-004** 一致）。
- **入参**：耗材发放标识、拣配人标识、追溯码、批号、数量等。
- **出参**：—。
- **前置条件**：状态须为 `PREPARATION`（或允许拣配的子集，与 **BR-DD-001** 一致）；追溯与批号规则见 **BR-DD-007**、**PR-DD-TRACE-001**。
- **状态变化**：`PREPARATION` → `PREPARED` / `IN_PROGRESS`（以落库与交互契约为准）。
- **聚合内影响**：写入拣配人、拣配时间、批号、追溯等；刷新状态时间。
- **事件输出**：`DeviceDispensePreparedEvent`。
- **失败语义**：状态不允许、追溯校验失败、必填事实缺失时拒绝。

#### 6.1.2 核对发放（`dispenseDevice`）

- **业务意义**：核对无误后确认发放事实，推进为已发放并触发库存扣减与计费编排。
- **边界说明**：不写 `ChargeItem` 本体、不写库存台账；仅本聚合状态与数量事实原子一致（与 **BR-DD-004** 一致）。
- **入参**：耗材发放标识、发放人标识、已发数量等（以本稿约定为准）。
- **出参**：—。
- **前置条件**：处于待发核对发放窗口（`PREPARED` 与/或 `IN_PROGRESS`）；五元组完整；**BR-DD-005**。
- **状态变化**：待发核对发放窗口 → `COMPLETED`（一次发全量）。
- **聚合内影响**：写入发放人、发放完成时间、`dispense_quantity`、`status_enum` 等。
- **事件输出**：`DeviceDispensedEvent`（别名 **DeviceDispenseConfirmedEvent**，见 **§5.1** 命名说明）。
- **失败语义**：状态不允许、库存不足、追溯失败、五元组不完整时拒绝。

#### 6.1.3 作废未发（`cancelUndispensedDeviceDispense`）

- **业务意义**：未完成发放路径作废，写入原因并进入 `DECLINED`。
- **边界说明**：仅写本聚合；库存与追溯回滚由编排按策略调用 **6.1.10** 等完成。
- **入参**：耗材发放标识、`not_performed_reason_enum`。
- **出参**：`—`。
- **前置条件**：允许作废的未完成态；**BR-DD-003**。
- **状态变化**：→ `DECLINED`。
- **聚合内影响**：写入原因枚举及作废侧时间/操作人（以落库与交互契约为准）。
- **事件输出**：`DeviceDispenseDeclinedEvent`。
- **失败语义**：状态不允许、原因缺失时拒绝。

#### 6.1.4 退耗材确认（`refundDispensedDeviceDispense`）

- **业务意义**：在待退耗材（`PENDING_REFUND`）窗口确认退回，推进 `REFUNDED` 并驱动库存回补与 **DM-CUS-011** IN 轨迹编排。
- **边界说明**：不写库存主账；负向耗材申请收口由协作方在发放确认后调用 **DM-WKF-003** `completeDeviceRequestAfterRefund`（`DeviceDispenseNurseWorkflowSupport` 编排）。
- **入参**：耗材发放标识、退回数量等（以本稿约定为准）。
- **出参**：`—`。
- **前置条件**：`PENDING_REFUND`（若采用两阶段）；**BR-DD-008** 幂等。
- **状态变化**：待退耗材 → 已退耗材（或直接退，以实现分支为准）。
- **聚合内影响**：更新状态与退量相关字段（以落库与交互契约为准）。
- **事件输出**：`DeviceDispenseRefundedEvent`。
- **失败语义**：非待退态、超退、幂等冲突时拒绝。

#### 6.1.4a 拒绝待退耗材（`declinePendingRefundDeviceDispense`）

- **业务意义**：门诊药房拒绝退耗材申请，待退记录推进为已拒绝终态。
- **入参**：`DeclinePendingRefundDeviceDispenseCommand`（含拒绝原因、操作人）。
- **状态变化**：`PENDING_REFUND` → `DECLINED`。
- **事件输出**：`DeviceDispenseDeclinedEvent`。

#### 6.1.5 由请求生成发放草稿（`generateDeviceDispenseFromTemporaryPath`）

- **业务意义**：由耗材请求生成发放草稿，用于预减占用与收费前工作台。
- **边界说明**：不写 **ChargeItem**；预减统计口径见 **BR-DD-QRY-003**。
- **入参**：请求侧锚点、数量单位、就诊患者等（以本稿约定为准）。
- **出参**：新建发放标识或实体（以落库与交互契约为准）。
- **前置条件**：请求合法且无重复创建策略命中。
- **状态变化**：新建 `DRAFT`。
- **聚合内影响**：插入主表草稿行；写入 `device_req_id`、`limit_time` 等。
- **事件输出**：`DeviceDispenseCreatedEvent`。
- **失败语义**：重复创建、请求非法、必填缺失时拒绝。

#### 6.1.6 草稿推进为待发拣配（`PREPARATION`）（`promoteDeviceDispenseToPreparation`）

- **业务意义**：收费/划价完成后批量 `DRAFT` → `PREPARATION`。
- **边界说明**：不新建主键；仅状态与时间字段；与 **BR-DD-006** 一致。
- **入参**：待推进的发放标识集合或请求键（以本稿约定为准）。
- **出参**：`—` 或更新条数（以落库与交互契约为准）。
- **前置条件**：当前为 `DRAFT` 且业务允许推进。
- **状态变化**：`DRAFT` → `PREPARATION`。
- **聚合内影响**：批量更新 `status_enum` 与相关时间字段。
- **事件输出**：—（本步可无事件；若实现补充 Created 以外事件以本稿约定为准）。
- **失败语义**：状态非法、部分行未满足收费门控时拒绝或部分失败策略以落库与交互契约为准。

#### 6.1.7 由执行生成待发拣配发放单（`generateDeviceDispenseFromExecution`）

- **业务意义**：执行后生成或更新发放单并进入 `PREPARATION`。
- **边界说明**：不写 **Procedure** 本体；仅回写/关联本表 `procedure_id` 等锚点。
- **入参**：`DeviceRequest`、`procedure_id`、`location_id`、`planned_dispense_time` 等。
- **出参**：发放标识（新建或更新路径以落库与交互契约为准）。
- **前置条件**：执行引用合法；重复生成策略未命中。
- **状态变化**：`PREPARATION`（或更新后保持待发链路允许态）。
- **聚合内影响**：插入或更新主表；写入执行与计划发药时间等。
- **事件输出**：`DeviceDispenseCreatedEvent`（更新路径是否复用以落库与交互契约为准）。
- **失败语义**：重复生成、执行引用非法时拒绝。

#### 6.1.8 执行直发已发放（`generateIssuedDeviceDispenseFromExecution`）

- **业务意义**：执行即领用/即发放，跳过待发拣配工作台，直接落 `COMPLETED`。
- **边界说明**：库存扣减仍由编排触发，本方法只收口发放事实态与数量字段。
- **入参**：执行上下文、五元组相关字段包（以本稿约定为准）。
- **出参**：发放标识。
- **前置条件**：策略开关；**BR-DD-002**。
- **状态变化**：创建或更新为 `COMPLETED`（及已发量字段，以落库与交互契约为准）。
- **聚合内影响**：写入完成态事实字段。
- **事件输出**：`DeviceDispensedEvent`。
- **失败语义**：策略不允许、库存门控失败时拒绝。

#### 6.1.9 生成待退申请（`createPendingRefundDeviceDispense`）

- **业务意义**：基于已发放事实生成待退记录，衔接退费与退库工作台。
- **边界说明**：不完成退费记账；仅本聚合待退态事实。
- **入参**：原发放标识、申请退量、**可选** `refundDeviceRequestId`（门诊申请退费负向耗单主键；传入时待退行 `device_req_id` 锚定负向单，对称药嘱 `refundRequestId`）。
- **出参**：待退记录标识或 `—`（以落库与交互契约为准）。
- **前置条件**：原发放处于允许申请退的状态子集。
- **状态变化**：`PENDING_REFUND`（或等价）。
- **聚合内影响**：写入待退关联字段与数量（以落库与交互契约为准）。
- **事件输出**：`DeviceDispensePendingRefundCreatedEvent`。
- **失败语义**：重复待退、状态不允许时拒绝。

#### 6.1.10 释放预减占用（`releaseDeviceDispenseReserve`）

- **业务意义**：医嘱取消、停嘱或删除未发等场景释放草稿 / `PREPARATION` 待发占用；**门诊医生站 IF-DOR-16** 签退路径与药品 `releaseDispenseReserve` 对称，**逻辑删除**未发发放行。
- **边界说明**：与 **6.1.16** 删除路径协调，避免双重释放；应用层优先以**逗号分隔发放主键**定位（WD① 查询结果），无发放事实时可回落 `deviceReqId`。
- **入参**：`idListOrRequestKey`（逗号分隔发放 id、单 id 或 `deviceReqId`/supportInfo，以本稿约定为准）。
- **出参**：`—`。
- **前置条件**：定位到须释放的占用集合；仅处理 **DRAFT / PREPARATION** 态且未逻辑删除的行（与 **`DM-MED-002` §6.1.9** 同口径）。
- **状态变化**：可释放行 **`is_deleted=true`**（保留原 `status_enum`，不写 `STOPPED`）；已发放等行跳过。
- **聚合内影响**：逻辑删除可释放行；不扣减已完成库存事实。
- **事件输出**：存在实际释放行时发布 `DeviceDispenseReserveReleasedEvent`；无可释放行时不发事件。
- **失败语义**：**未命中任何发放行**或**均已释放**时**幂等静默返回**（与 `DM-MED-002` §6.1.9 一致）；定位键为空时拒绝；并发冲突时拒绝。

#### 6.1.11 匹配发放批号与拆单（`matchDispenseLotNumber`）

- **业务意义**：将待发拣配态（`PREPARATION`）发放单自动匹配到库存批号（按效期先进先出，**PR-DD-LOT-001**），确保后续发放具备可发批号；单批库存不足时**拆分**发放单。
- **边界说明**：读取库存明细做批号分配；**不在本动作内**扣减库存主账；拆分与更新同事务提交。
- **入参**：待匹配发放标识集合或请求键（以本稿约定为准）。
- **出参**：— 或返回拆分后的标识列表（以落库与交互契约为准）。
- **前置条件**：仅处理 `PREPARATION` 且未逻辑删除的行。
- **状态变化**：保持 `PREPARATION`；可能新增子行（新主键、新 `bus_no`/`lot_number`）。
- **聚合内影响**：原单写入 `lot_number`；不足时拆出新行并重置 `id` / `bus_no` / `lot_number` 等，批量 `saveOrUpdateBatch` 等价落库（以实现 ORM 为准）。
- **事件输出**：无（盘点/批号匹配为静默收口时可不发布；若产品要求审计可补事件，以本稿约定为准）。
- **失败语义**：无可用批号、并发拆分冲突、非 `PREPARATION` 时拒绝。

#### 6.1.12 更新发放为已汇总（`updateDispenseStatusSummarized`）

- **业务意义**：科室耗材**盘点汇总**流程中，将发放单批量置为已汇总中间态（`SUMMARIZED`）。
- **边界说明**：不写库存扣减事实；与盘点主流程编排一致。
- **入参**：发放主键列表或盘点批次键（以本稿约定为准）。
- **出参**：—。
- **前置条件**：行状态允许进入 `SUMMARIZED`；防重复汇总。
- **状态变化**：`*`（允许集合）→ `SUMMARIZED`（批量）。
- **聚合内影响**：按 `id` 批量更新 `status_enum` 等；若有汇总批次锚点字段以落库实现为准（主表无 `summary_no` 列时锚点放他处，以本稿 **§6.1** 与 **§3.1** 为准）。
- **事件输出**：无或 `DeviceDispenseSummarizedEvent`（以实现是否补发行为准）。
- **失败语义**：状态非法、跨批次混选时拒绝。

#### 6.1.13 盘点回写为已发放（`updateDispenseStatusCompletedFromSummary`）

- **业务意义**：科室盘点流程**收口**后，将已汇总（`SUMMARIZED`）发放单批量回写为已发放完成态（`COMPLETED`），与库存扣减等同流程编排衔接。
- **边界说明**：不在本方法内写库存台账；库存扣减由协作域同事务/事件完成。
- **入参**：汇总回写键（如 `dev_dispense_id`、`bus_no` 或盘点批次键，以本稿约定为准）；须与 **6.1.12** 中 **updateDispenseStatusSummarized** 的单参/多参重载严格区分，避免与「仅置 `SUMMARIZED`」混用。
- **出参**：—。
- **前置条件**：目标行须为 `SUMMARIZED` 且盘点链路允许回写。
- **状态变化**：`SUMMARIZED` → `COMPLETED`（批量）。
- **聚合内影响**：按 `id` 批量更新 `status_enum` 及发放完成相关时间等（以落库与交互契约为准）。
- **事件输出**：无或 `DeviceDispenseSummaryCompletedEvent`（以落库与交互契约为准）。
- **失败语义**：回写键不匹配、存在未收口子任务时拒绝。

#### 6.1.14 取消汇总（`cancelDeviceDispenseSummary`）

- **业务意义**：撤销盘点汇总绑定，清空汇总锚点并回到待发拣配链路（`PREPARATION`），以便重新分单或改汇总。
- **边界说明**：仅写本聚合；不自动冲销已发生的库存扣减事实；跨域回滚由应用编排。
- **入参**：汇总批次键或等价定位参数（以本稿约定为准）。
- **出参**：—。
- **前置条件**：目标行须处于允许取消汇总的态（与 **§5.2** 一致）；存在下游硬锁定时拒绝。
- **状态变化**：`SUMMARIZED` → `PREPARATION`（语义以落库与交互契约为准）。
- **聚合内影响**：清空或解绑汇总锚点字段；回写 `status_enum` 等。
- **事件输出**：`DeviceDispenseSummaryCancelledEvent`。
- **失败语义**：单号不存在、状态不允许、存在不允许取消的下游依赖时拒绝。

#### 6.1.15 回退未发（临时路径）（`rollbackUndispensedDeviceDispense`）

- **业务意义**：临时路径下将 `PREPARATION` 待发拣配或已汇总但未发放单据回退为草稿（`DRAFT`），保留主键便于再次推进；与 **6.1.16** 物理删除路径区分。
- **边界说明**：非物理删除；不写库存扣减事实；与 **6.1.10** 释放预减策略协调，避免双重释放。
- **入参**：耗材发放标识列表（以本稿约定为准）。
- **出参**：—。
- **前置条件**：目标状态须为允许回退的集合（待拣配/已汇总等，见 **§5.2**）。
- **状态变化**：`PREPARATION` / `SUMMARIZED`（允许子集）→ `DRAFT`（以落库与交互契约为准）。
- **聚合内影响**：回写状态；清理汇总号等策略以落库与交互契约为准。
- **事件输出**：以实现是否发布补充事件为准；未在 **§5.1** 统一命名则不写死。
- **失败语义**：状态不允许、列表含非法标识时拒绝。
- **关联**：批量已发放回退至 `PREPARATION` 见 **6.1.21** `revertCompletedBatchToPreparation`（须满足 **BR-DD-009**）。

#### 6.1.16 删除未发（长期路径）（`removeUndispensedDeviceDispense`）

- **业务意义**：长期路径下删除未进入完成态的发放行并释放预减占用；用于执行撤销等场景。
- **边界说明**：与 **6.1.15** 回退语义不同；删除前须无下游硬依赖或已由编排解除；与 **6.1.10** 协调避免双重释放。
- **入参**：耗材发放标识列表或请求维度键（以本稿约定为准）。
- **出参**：—。
- **前置条件**：目标须为允许删除的未发放集合；须满足释放预减门控。
- **状态变化**：物理删除或等价逻辑删除（以实现持久化策略为准）。
- **聚合内影响**：移除主表行或标记删除；不扣减已完成库存事实。
- **事件输出**：可伴随 `DeviceDispenseReserveReleasedEvent`（与 **6.1.10** 协调）。
- **失败语义**：已发放等不可删状态、引用未释放时拒绝。

#### 6.1.17 停发未发（`stopUndispensedDeviceDispense`）

- **业务意义**：退费、终止未发等场景将符合条件的未发放行批量推进为已停止（`STOPPED`），并写入原因。
- **边界说明**：仅写本聚合；库存与追溯回滚由编排按策略调用 **6.1.10** 等完成；须 **BR-DD-003**。
- **入参**：待停发发放标识集合、`not_performed_reason_enum` 等（以本稿约定为准）。
- **出参**：— 或更新条数（以落库与交互契约为准）。
- **前置条件**：行处于允许停发的未完成态子集。
- **状态变化**：→ `STOPPED`（批量，以实现状态机为准）。
- **聚合内影响**：批量更新 `status_enum`、原因及操作痕迹字段。
- **事件输出**：`DeviceDispenseStoppedEvent`。
- **失败语义**：状态不允许、原因缺失、部分行未满足门控时拒绝或按产品策略部分失败。

#### 6.1.18 暂停发放（`holdDeviceDispense`）

- **业务意义**：按耗材定义或工作台维度将发放行批量置为暂停（`ON_HOLD`），阻塞后续发放直至恢复或停发。
- **边界说明**：仅写本聚合；不替代医嘱停嘱状态机。
- **入参**：发放标识集合或筛选键、暂停原因等（以本稿约定为准）。
- **出参**：—。
- **前置条件**：当前状态允许进入 `ON_HOLD`（见 **§5.2**）。
- **状态变化**：允许态 → `ON_HOLD`（批量）。
- **聚合内影响**：批量更新 `status_enum` 与暂停相关字段。
- **事件输出**：—（若产品要求审计可补事件，以本稿约定为准）。
- **失败语义**：状态不允许、并发冲突时拒绝。

#### 6.1.19 撤回已发放（`recallDispensedDeviceDispense`）

- **业务意义**：已发放后的撤回路径，将发放单推进为已撤回（`CANCELLED`），并驱动库存回补与追溯 IN 等编排（由他域消费事件或同事务完成）。
- **边界说明**：不写库存主账；仅本聚合状态与撤回事实收口。
- **入参**：耗材发放标识、撤回操作人及原因等（以本稿约定为准）。
- **出参**：—。
- **前置条件**：原单处于允许撤回的完成态子集；须满足双人/追溯等门禁（与 **BR-DD-004**、**BR-DD-007** 编排一致）。
- **状态变化**：`COMPLETED`（或允许子集）→ `CANCELLED`。
- **聚合内影响**：写入撤回时间、操作人、原因等。
- **事件输出**：`DeviceDispenseCancelledEvent`。
- **失败语义**：状态不允许、库存回补门控失败、追溯不一致时拒绝。

#### 6.1.21 批量回退至待发拣配（`revertCompletedBatchToPreparation`）

- **业务意义**：取消执行或纠错场景下，将已完成发放批量回退至 `PREPARATION`；**保留医生签发 `lot_number`**，仅清空发放量/发放人/时间/追溯码，便于按原签发批号再次执行（护士不重跑 FIFO）。
- **边界说明**：须 **BR-DD-009**（**保留** `lot_number`；清空 `trace_no`，`dispense_quantity` 归零、清空发放人/发放时间等）；库存回补与追溯 IN 由编排完成；须与 **6.1.12** `updateDispenseStatusSummarized`（仅置 `SUMMARIZED`）及 **6.1.13** `updateDispenseStatusCompletedFromSummary`（汇总回写完成）严格区分，避免同名重载混用。
- **入参**：发放主键列表或盘点/执行回退键（以本稿约定为准）。
- **出参**：—。
- **前置条件**：目标行处于允许回退的完成态子集；防与 **6.1.13** 盘点回写路径误用。
- **状态变化**：`COMPLETED`（允许子集）→ `PREPARATION`（批量）。
- **聚合内影响**：批量清空发放操作字段与追溯码并重置状态；**不**置空签发批号。
- **事件输出**：以落库实现与本稿约定为准（**§5.1** 未单列契约时不得臆造事件名）。
- **失败语义**：存在不可回退的下游事实、部分行状态不一致时拒绝或全单回滚策略以落库与交互契约为准。

#### 6.1.24 批量回退已配药至待发拣配（`revertPreparedBatchToPreparation`）

- **业务意义**：护士取消执行（开关 **关**）或对称回退场景下，将 **PREPARED** 批量回退至 **PREPARATION**；**保留医生签发 `lot_number`**，仅清空拣配人/追溯/拣配时间。
- **边界说明**：与 **6.1.21** `revertCompletedBatchToPreparation`（**COMPLETED→PREPARATION**）对称；**不**回补库存（未发药路径）；编排见 **`DeviceDispenseNurseWorkflowSupport#revertOutpatientNurseDeviceDispenses`**。
- **入参**：发放主键列表、`operatorId`（`RevertPreparedBatchToPreparationCommand`）。
- **状态变化**：`PREPARED` → `PREPARATION`（批量）。
- **聚合内影响**：清空 `preparer_id`、`trace_no`、`prepare_time` 等拣配字段；**保留** `lot_number`。
- **事件输出**：以落库实现为准（**§5.1** 未单列契约时不得臆造事件名）。

#### 6.1.25 修正客户可见发放时刻（`amendDeviceDispenseDisplayTime`）

- **业务意义**：护士站划价「修改划价客户可见时间」（**AS-IP-NUR-005 · IF-IP-PRC-07**）修正主发放单的客户可见时刻；发放时间与 Procedure.`recorded_time` 同属展示轴。
- **边界说明**：仅写本聚合展示时刻字段；**优先**改 `dispense_time`（已有发放时刻或已发态），否则改 `prepare_time`；**不改** `planned_dispense_time`、状态、批号、库存、追溯；**不**写 Charge / Request / Procedure。
- **入参**：`AmendDeviceDispenseDisplayTimeCommand`——`dispenseId`、`displayTime`（新客户可见时刻）、`operatorId`。
- **出参**：—。
- **前置条件**：主发放行存在且未逻辑删除；`displayTime` 非空；须存在可改的展示字段（`dispense_time` 或 `prepare_time` 路径可达）。
- **状态变化**：`N/A`（状态不变）。
- **聚合内影响**：有 `dispense_time`（或已发路径）则覆盖 `dispense_time`；否则覆盖 `prepare_time`。
- **事件输出**：无（展示轴修正；**§5.1** 未单列契约时不得臆造事件名）。
- **失败语义**：发放不存在、无可改展示字段、或时刻为空时拒绝。

#### 6.1.22 门诊医生站签发后按 FIFO 重写草稿发放（`replaceFifoDraftDispensesAfterDoctorStationSign`）

- **业务意义**：与 **`DM-MED-002` §6.1.15** **对称**，服务于 **AS-OP-DOR-003 · IF-DOR-15** 耗材分支：`DeviceRequest` 签发成功后，按应用层 FIFO 分段**替换** `wkf_device_dispense` 草稿发放事实，支撑 **`ChargeItem`** 与发放主键 **1:1**。
- **边界说明**：不写 **`ChargeItem`**；载荷 **`ReplaceFifoDeviceDispenseDraftsCommand`**。
- **入参**：操作者、`device_req_id`、分段集合（库位、批号、数量）等。
- **出参**：替换后的 **`wkf_device_dispense` 主键列表**。
- **前置条件**：请求已签发；分段与数量口径合法；同请求下仅允许覆盖 **DRAFT / PREPARATION** 且未逻辑删除的行。
- **聚合内影响**：对可覆盖行 **逻辑删除** 后批量插入新 **DRAFT** 行（与药品 `replaceFifoDraftDispensesAfterDoctorStationSign` 一致）；已逻辑删除行不参与门控。
- **事件输出**：替换前存在可覆盖行时发布 `DeviceDispenseReserveReleasedEvent`；新行发布 `DeviceDispenseCreatedEvent`。
- **失败语义**：存在不可覆盖的非终态发放行（如已拣配/已发）时拒绝。

#### 6.1.23 批量推进草稿为待发拣配（`promoteDraftDispensesForDeviceRequests`）

- **业务意义**：门诊正式结算后批量将 `DRAFT` 发放行委托 **`promoteDeviceDispenseToPreparation`**（**AS-OP-FIN-002** BR-OPC-015）。
- **入参**：`deviceReqIds` 集合、`operatorId`；空集合幂等。

#### 6.1.26 作废待退耗材发放（`voidPendingRefundDeviceDispense`）

- **业务意义**：作废尚未确认的待退耗材发放行（退费撤销等编排）。对称药品 `voidPendingRefundMedicationDispense`。

#### 6.1.27 退费撤销后恢复已拒绝待退耗材（`restoreDeclinedDeviceDispenseAfterRefundRevoke`）

- **业务意义**：门诊退费申请撤销后，恢复先前拒绝的待退耗材发放事实。

#### 6.1.28 护士执行更新发放库位（`updateLocationForNurseExecute`）

- **业务意义**：门诊/住院护士执行路径回写发放库位。

> **变更（2026-09-23 · 模式 B）**：按代码回写退费/库位相关写方法。

### 6.2 读侧查询服务（DeviceDispenseQueryService）

查询类动作无写库，以「查询范围（读库，无写入）+ 组装规则」表达QueryService；用于列表/详情、写前强加载及与写操作分层的**前置校验**（与 **BR-DD-017**、**§7** 中 **BR-DD-QRY-*** 一致）。**不写主表、不做跨域状态迁移**。

#### 6.2.1 按主键查询发放单（`loadDeviceDispenseById`）

- **业务意义**：按主键加载单条聚合根，支撑详情回显与一般存在性判断。
- **边界说明**：只读查询，不写库，不做跨域编排。
- **查询范围（读库，无写入）**：`wkf_device_dispense` 主表单条。
- **过滤口径**：主键精确匹配；默认排除逻辑删除（以落库与交互契约为准）。
- **组装规则**：映射为领域实体或查询 DTO。
- **返回约束**：不存在或无权限时是否抛错由本稿与实现约定；存在则返回单条。

#### 6.2.2 按就诊查询发放单列表（`listDeviceDispensesByEncounter`）

- **业务意义**：返回某次就诊下的耗材发放集合，支撑发放工作台与执行闭环展示。
- **边界说明**：只读查询，不写库，不做跨域编排；列表优先投影/QueryService（与 **BR-DD-QRY-001** 一致）。
- **查询范围（读库，无写入）**：`wkf_device_dispense` 按 `encounter_id` 过滤。
- **过滤口径**：租户与就诊标识必选；默认排除逻辑删除；可按状态子集过滤（以落库与交互契约为准）。
- **组装规则**：列表 DTO；字段口径与 **§3.1** 一致；工作台合并展示口径见 **BR-DD-QRY-002**。
- **返回约束**：允许空列表；分页策略以落库与交互契约为准。

#### 6.2.2a 按就诊主键集合批量查询发放单（`listDeviceDispensesByEncounterIds`）

- **业务意义**：按本页就诊主键 **IN** 批量加载发放单，供门诊护士站队列判定发放是否仍为待发放（**AS-OP-NUR-001 BR-NUR-017**）。
- **边界说明**：只读；仅本聚合 Repository；`IdInQueryChunkSupport` 分批。
- **查询范围（读库，无写入）**：`wkf_device_dispense` 按 `encounter_id IN (...)`。
- **过滤口径**：空集合返回空表；调用方再按请求锚点分组并判定 `DRAFT`/`PREPARATION`/`IN_PROGRESS`（空状态按待发放）。
- **组装规则**：映射 `DeviceDispenseListModel`。
- **返回约束**：允许空列表。

#### 6.2.3 按主键加载发放单（须存在）（`mustGetDeviceDispense`）

- **业务意义**：在拣配、发放、作废、退耗材等写操作前，按主键加载本条记录；**记录必须存在且当前上下文可读**。无记录、无权限或不可见时抛业务异常，避免写路径对 `null` 的隐式依赖。
- **边界说明**：只读，不改数据库，不编排他域写操作。**与 6.2.1 对比**：本条面向写前加载，**无记录必抛错**；**6.2.1** 面向详情/列表，「无记录」是否抛错由本稿与实现约定。
- **查询范围（读库，无写入）**：`wkf_device_dispense` 按主键一行。
- **过滤口径**：主键等于入参发放标识；默认不查已逻辑删除行（与 **6.2.1** 一致）。
- **组装规则**：领域实体或查询 DTO，由实现约定；读方法内不改状态字段。
- **返回约束**：有记录则返回；否则抛明确业务/权限异常，由应用层转换提示。

#### 6.2.4 拣配前置校验（`validateDeviceDispensePreparable`）

- **业务意义**：调用 **6.1.1** 前校验状态、追溯与批号门控，减少无效写事务。
- **边界说明**：只读校验，不写库；与 **6.1.1** 分层（**BR-DD-017**）。
- **查询范围（读库，无写入）**：本聚合主键定位 + 协作方只读查询（追溯码状态等，以落库与交互契约为准）。
- **过滤口径**：状态须为 `PREPARATION`（或允许拣配子集）；规则对齐 **BR-DD-001**、**BR-DD-007**、**PR-DD-TRACE-001**。
- **组装规则**：输出允许/不允许及原因码（以落库与交互契约为准）。
- **返回约束**：不修改任何聚合状态。

#### 6.2.5 发放前置校验（`validateDeviceDispensable`）

- **业务意义**：调用 **6.1.2** 前校验状态、五元组完整性与库存足额等（库存结论以协作方返回为准）。
- **边界说明**：只读校验，不写库；与 **6.1.2** 分层（**BR-DD-017**）。
- **查询范围（读库，无写入）**：本聚合主键定位 + 库存可用量等只读查询（以落库与交互契约为准）。
- **过滤口径**：状态须处于待发核对发放窗口（`PREPARED` 与/或 `IN_PROGRESS`）；五元组满足 **BR-DD-004**、**BR-DD-005**。
- **组装规则**：输出允许/不允许及原因码（以落库与交互契约为准）。
- **返回约束**：不修改任何聚合状态。

#### 6.2.6 作废前置校验（`validateDeviceDispenseCancellable`）

- **业务意义**：调用 **6.1.3** 前校验是否允许进入 `DECLINED`。
- **边界说明**：只读校验，不写库；与 **6.1.3** 分层（**BR-DD-017**）。
- **查询范围（读库，无写入）**：本聚合主键定位。
- **过滤口径**：须为允许作废的未完成态（与 **§5.2** 一致）；**BR-DD-003** 原因枚举合法等。
- **组装规则**：输出允许/不允许及原因码（以落库与交互契约为准）。
- **返回约束**：不修改任何聚合状态。

#### 6.2.7 退耗材前置校验（`validateDeviceDispenseRefundable`）

- **业务意义**：调用 **6.1.4** 前校验待退态与幂等，防重复退。
- **边界说明**：只读校验，不写库；与 **6.1.4** 分层（**BR-DD-017**）。
- **查询范围（读库，无写入）**：本聚合主键定位。
- **过滤口径**：状态须为 `PENDING_REFUND`（若采用两阶段）；**BR-DD-008**。
- **组装规则**：输出允许/不允许及原因码（以落库与交互契约为准）。
- **返回约束**：不修改任何聚合状态。

#### 6.2.8 按执行 ID 批量查询发放（`getDevDispenseByProcedureId`）

- **业务意义**：为取消执行、执行回滚加载执行关联的全部发放单。
- **边界说明**：只读查询，不写库，不做跨域编排。
- **查询范围（读库，无写入）**：`wkf_device_dispense` 按 `procedure_id`。
- **过滤口径**：排除逻辑删除；可按状态子集筛选（以落库与交互契约为准）。
- **组装规则**：按时间或 `bus_no` 排序返回列表。
- **返回约束**：允许空列表；大批量时分页策略以落库与交互契约为准。

#### 6.2.9 按请求 ID 列表查询发放（`selectByRequestIdList`）

- **业务意义**：为退耗材、请求变更加载请求维度的发放集合。
- **边界说明**：只读查询，不写库，不做跨域编排。
- **查询范围（读库，无写入）**：`wkf_device_dispense` 按 `device_req_id` IN 查询。
- **过滤口径**：排除逻辑删除。
- **组装规则**：按请求分组或平铺列表，以实现契约为准。
- **返回约束**：入参列表长度上限以落库与交互契约为准。

#### 6.2.10 发放初始化查询（`init`）

- **业务意义**：为耗材发放工作台加载科室、拣配人、未发放原因字典等初始化数据。
- **边界说明**：只读查询与字典聚合；**不得**在本方法内写发放主表。
- **查询范围（读库，无写入）**：字典/配置/人员科室等只读源（以落库与交互契约为准）。
- **过滤口径**：租户、科室可见性。
- **组装规则**：按前端约定结构返回。
- **返回约束**：缺失字典项时返回空或错误码以落库与交互契约为准。

#### 6.2.11 就诊分页查询（`getEncounterInfoListPage`）

- **业务意义**：耗材发放工作台按就诊维度分页展示待发放患者集合。
- **边界说明**：只读查询；就诊内聚合，不扩展为跨患者统计域方法。
- **查询范围（读库，无写入）**：就诊与发放联合QueryService或主表 JOIN（以落库与交互契约为准）。
- **过滤口径**：待发放状态子集、科室过滤；展示合并口径见 **BR-DD-QRY-002**。
- **组装规则**：分页 DTO 与 UI 合同一致。
- **返回约束**：分页元数据完整。

#### 6.2.12 发放单分页查询（`getDeviceDispenseOrderList`）

- **业务意义**：发放执行页按条件分页查询发放单据明细。
- **边界说明**：只读查询，不写库，不做跨域编排。
- **查询范围（读库，无写入）**：`wkf_device_dispense` 及列表展示所需关联视图。
- **过滤口径**：状态、库房、时间窗等组合条件。
- **组装规则**：列表字段与排序与 UI 一致。
- **返回约束**：默认排除逻辑删除。

#### 6.2.13 按请求查发放主键（`listDeviceDispenseIdsByDeviceRequest`）

- **出参**：`List<Long>` 发放 id。

#### 6.2.14 批量按请求分组发放 id（`mapDeviceDispenseIdsByDeviceRequestIds`）

- **出参**：`Map<Long, List<Long>>`（`deviceReqId → dispenseIds`）。

#### 6.2.15 请求→主发药库位（`mapDeviceRequestToPrimaryDispenseLocationId`）

- **出参**：`Map<Long, Long>`。

#### 6.2.16 医嘱开立目录占用（`listDeviceDispensesForOrderCatalogOccupancy`）

- **业务意义**：IF-DOR-11 候选库位×目录耗材定义批量规格查询（**不限就诊**；`DRAFT`/`PREPARATION` **不**按 `limitTime` 过期，直至执行或取消释放；与药品侧 24h 窗不同，见 **BR-DD-QRY-003**）。

#### 6.2.17 发放→请求反查（`mapDeviceDispenseIdsToDeviceReqIds`）

- **出参**：`Map<Long, Long>`。

#### 6.2.18 批量按请求查实体（`listDeviceDispensesByDeviceReqIds`）

- **出参**：`List<DeviceDispense>`。

#### 6.2.19 库房日结耗材发放批量读（`listDeviceDispensesByLocationAndDispenseWindow`）

- **业务意义**：**AS-INV-RPT-001 · IF-INV-SET-02**（耗材 Tab）按 **发放库位** 与 **发放时间自然日窗**，批量拉取 **已完成发药 / 已退药** 发放行，供应用层按 **`deviceDefId`**（品种级汇总键）汇总发退药数量与金额。
- **边界说明**：只读；Query 类型归属本聚合 `queries` 包（`DeviceDispenseSettlementWindowQuery` / `DeviceDispenseSettlementLineQueryModel`）；**不**替代 `getDeviceDispenseOrderList` 工作台分页；**不**新建跨域读包。
- **查询范围（读库，无写入）**：`wkf_device_dispense` 及日结所需字段（`quantity`、`dispense_time`、`status_enum`、`location_id`、`device_def_id`、批号/价锚字段等）。
- **过滤口径**：
  - `location_id` = 入参库房；
  - `dispense_time` 落在入参 **左开右闭** 自然日窗（与 **BR-INV-SET-004** 一致）；
  - `status_enum IN` 日结统计态（**已完成发药、已退药** 等，与 **BR-INV-SET-010** 耗材列一致）；
  - 排除逻辑删除。
- **组装规则**：返回明细行列表；**不在本方法内**做品种汇总或发/退方向换算（由应用层 **`DailySettlementAssemblysupport** 按 `status_enum` 拆分列）。
- **实现锚点**：`**DeviceDispenseQueryService#listDeviceDispensesByLocationAndDispenseWindow`**；应用消费 **`InventoryWarehouseDailySettlementAppService#loadAssemblyContext`**（**AS-INV-RPT-001**）。
- **返回约束**：允许空列表；须可走 `location_id` + `dispense_time` 索引。

#### 6.2.20 护士耗材工作量候选发放行（`listNurseConsumableWorkloadCandidateDispenses`）

- **业务意义**：**AS-RPT-002** 汇总/明细共用候选集；供 `NurseWorkloadQuerysupport 解析 `hvcm_flag` enrich。
- **边界说明**：只读；纳入状态随全局开关——关 `PREPARED` + `preparer_id` + `prepare_time`；开 `COMPLETED` + `performer_id` + `dispense_time`（空回落 `prepare_time`）。
- **入参**：`NurseWorkloadPageQuery`（预解析 `resolvedEncounterScope` 与 `deviceAutoInventoryDeductEnabled`）。
- **返回约束**：无命中返回空列表。

#### 6.2.21 护士耗材使用量按高/低值汇总（`summarizeNurseConsumableWorkloadByHvcm`）

- **业务意义**：**AS-RPT-002 IF-RPT-NWL-04** 按 `DeviceDefinition.hvcm_flag` 汇总数量。
- **边界说明**：只读；数量 `COALESCE(dispense_quantity, quantity)` 代数 SUM；`hvcmFlagByDeviceDefId` 由 Support 预解析。
- **返回约束**：无命中返回空列表。

#### 6.2.22 护士耗材使用量明细分页（`pageNurseConsumableWorkloadDetail`）

- **业务意义**：**AS-RPT-002 IF-RPT-NWL-05** 返回 dispense 键集 + `deviceReqId` + 归属护士 + 数量 + 业务时间。
- **边界说明**：只读；业务时间倒序；默认 20 条/页。
- **返回约束**：`Page<NurseConsumableWorkloadDetailQueryModel>`。

#### 6.2.23 耗材使用报表患者侧明细（`pageConsumableUsageDispenseLines` / `listConsumableUsageDispenseLines`）

- **业务意义**：**AS-RPT-005 / AS-RPT-006** 患者侧发放事实明细分页/导出；与科室领用并集由应用层编排。
- **边界说明**：只读；**仅本聚合**；门诊/住院域由应用层预写 `resolvedEncounterScope`；时间轴 **`dispense_time`**。
- **过滤口径**：`status_enum ∈ {COMPLETED, PART_COMPLETED, REFUNDED}`；可选库位/品规/时间窗。
- **组装规则**：`ConsumableUsageDispenseLineQueryModel`（含 **`performerId`**＝`performer_id`，供应用层回填执行人/发耗材人姓名）。
- **实现锚点**：`DeviceDispenseQueryService#pageConsumableUsageDispenseLines` / `#listConsumableUsageDispenseLines`。

#### 6.2.24 耗材使用报表患者侧净汇总（`summarizeConsumableUsageDispense`）

- **业务意义**：按患者 / 科室 / 库位 / 品规净数量汇总（**不做**药品类型式主汇总）。
- **边界说明**：只读；科室维须预写 `organizationIdByEncounterId`；金额由应用层 ChargeItem enrich。
- **实现锚点**：`DeviceDispenseQueryService#summarizeConsumableUsageDispense`。

### 6.3 领域边界说明

- 本聚合负责耗材发放事实及状态流转；**器材目录**由 **DeviceDefinition** 维护。
- **退耗材**收口为 `REFUNDED` 时须满足 **BR-DD-008**（幂等）；库存回补与 **DM-CUS-011** 轨迹 IN 由编排完成。
- **库存扣减/回补、追溯码 OUT/IN、收费联动**为跨域编排：领域层定义触发点与 **BR-DD-004** 等前置约束，**不**在其他聚合表上直接写库。
- 读写模型以发放主表为锚；**不在本聚合内**维护 **DeviceRequest** 完整快照。
- 读侧契约见 **§6.2**（**6.2.1～6.2.12**）；列表/投影与写前 `mustGet` / `validate` 分层及预减展示口径见 **§7** **BR-DD-017** 与 **BR-DD-QRY-001～003**。
- 事件名称为对外契约；`DeviceDispensedEvent` 与历史 **DeviceDispenseConfirmedEvent** 并存时须在迁移计划中收敛；避免与 **§5** 长期偏离。

---

## 7. 不变式


| 编号            | 不变式           | 说明                                                                                                                                                 |
| ------------- | ------------- | -------------------------------------------------------------------------------------------------------------------------------------------------- |
| BR-DD-001     | 关键状态迁移受限      | 发放完成前须处于允许完成的配发链路状态（以实现状态机为准）                                                                                                                      |
| BR-DD-002     | 已发放数量约束       | 完成态须满足 `dispense_quantity` 与 `quantity` 口径一致 |
| BR-DD-003     | 停止/作废须写原因     | `not_performed_reason_enum` 必填；**作废未发**进入 `DECLINED`，**停发/退费终止未发**进入 `STOPPED`；准入状态集合以实现状态机为准。                                                     |
| BR-DD-004     | 库存校验通过后才能完成发放 | 发放确认前须通过库存可用性校验；含**同批号停供**（如库存态 `RETIRED`）禁发；**应发量折算最小单位**后不得大于可用量；失败则整单不进入完成态。                                                                    |
| BR-DD-005     | 配药人与发药人分离     | 工作台发放须满足双人规则；若同时传入 `preparer_id` 与当前发药人，**二者不得相同**。                                                                                                |
| BR-DD-006     | 限制时间窗口        | 草稿与执行生成行的 `limit_time` 窗口不同（如 `+2h` / `+24h`，以落库与交互契约为准；耗材预减读侧忽略过期，见 **BR-DD-QRY-003**）                                                                                           |
| BR-DD-007     | 追溯与批号一致       | 追加至 `cus_trace_number` + `cus_trace_number_flow`（**DM-CUS-011**）的轨迹须与发放行 `lot_number`、`trace_no`、业务单据锚点一致；**有码**时逐码 OUT/IN 语义闭合；**无码**时占位策略须满足数量闭合且不削弱 **BR-DD-002**。 |
| BR-DD-008     | 退耗材幂等         | `REFUNDED` 终态不得重复退；重复请求须拒绝或可安全忽略（以落库与交互契约为准）。                                                                                                      |
| BR-DD-009     | 取消执行回退保留签发批号 | `COMPLETED`/`PREPARED` → `PREPARATION` 类回退须**保留**签发 `lot_number`（护士不重跑 FIFO）；清空 `trace_no`，`dispense_quantity` 归零并清空拣配/发放操作字段。待退行/回退草稿亦**不得**置空已锁定批号。 |
| BR-DD-017     | 查询/校验/写入分层    | 读侧不写库；写前 `mustGet`* / `validate`* 与QueryService 列表查询分层，不得以工作台 SQL 替代领域写模型为唯一事实源。                                                                                |
| BR-DD-QRY-001 | 写前必回写模型       | 批量写前须按主键重新加载写模型并逐条校验，**不得**仅以列表/投影结果作为唯一事实源。                                                                                                       |
| BR-DD-QRY-002 | 待发核对发放展示合并    | 工作台可将 `PREPARATION` / `PREPARED` / `IN_PROGRESS` 合并为「待发核对发放」列表口径展示，与 **§4.3** 脚注中「待发药」合并展示**同指**（集成层须显式映射）。                                        |
| BR-DD-QRY-003 | 预减占用口径        | 预减统计仅计入 `DRAFT` / `PREPARATION` 且未逻辑删除的行；**不**按 `limit_time` 过期释放（与药品目录预减约 24h 窗不同）。须护士执行推进或签退/取消释放后才不再计入可用量预减。                                                                              |


### 7.1 过程约定（PR-DD）


| 编号              | 约定          | 说明                                                                  |
| --------------- | ----------- | ------------------------------------------------------------------- |
| PR-DD-BUSNO-001 | `bus_no` 生成 | 新建发放行 `getSeqByDay(DDP, 4)`；禁止父号 `.step` 级联子号。 |
| PR-DD-LOT-001   | 批号与拆单       | 自动匹配优先按效期升序（FEFO）；单批不足时拆行，新行独立主键与 `bus_no`/`lot_number`，**同事务**持久化。 |
| PR-DD-LOT-002   | 签发批号非空     | `replaceFifoDraftDispensesAfterDoctorStationSign` 各 FIFO 分段 `lot_number` 必须非空；库存无真实批号不得签发成功。 |
| PR-DD-LOT-003   | 挂费与发放批号一致 | 门诊医生站分段挂费写 `ChargeItem` 前，发放行落库 `lot_number` 须与 FIFO 分段一致（**AS-OP-DOR-003 · BR-ORD-033**）；正式结算再验一次（**AS-OP-FIN-002 · BR-OPC-023**）。 |
| PR-DD-TRACE-001 | 多码与解析       | 逗号分隔 `trace_no` 的拆分、去空、规范化；与 **BR-DD-007**、**DM-CUS-011** 逐条轨迹一一对应。 |


---

## 8. 上下文映射

### 8.1 上游依赖


| 上游上下文 | 聚合               | 关联方式                           | 说明      |
| ----- | ---------------- | ------------------------------ | ------- |
| 耗材申请  | DeviceRequest    | `device_req_id`                | 发放来源请求  |
| 基础数据  | DeviceDefinition | `device_def_id`                | 耗材目录    |
| 患者管理  | Patient          | `patient_id`                   | 发放患者    |
| 就诊管理  | Encounter        | `encounter_id`                 | 发放就诊    |
| 组织架构  | Practitioner     | `performer_id` / `preparer_id` | 配药与发药人员 |
| 位置服务  | Location         | `location_id`                  | 器材房     |


### 8.2 下游影响


| 下游上下文 | 聚合                                           | 触发方式  | 说明                                                    |
| ----- | -------------------------------------------- | ----- | ----------------------------------------------------- |
| 库存管理  | InventoryItem                                | 应用层编排 | 发放扣减、撤回/退回回补                                          |
| 追溯码记录 | TraceNumberRecord（`cus_trace_number` + `cus_trace_number_flow`） | 应用层编排 | 见 **DM-CUS-011**：按发放事实追加 OUT/IN 轨迹，与发放行 `trace_no` 对齐 |
| 收费管理  | ChargeItem                                   | 应用层编排 | 发放后计费、退回退费                                            |


### 8.3 引用规则

> **统一规则**：跨域只传递 **发放标识**、请求标识等 ID；禁止下游直接读取 `wkf_device_dispense` 表承载对方业务状态；跨域写库由应用层事务编排，本聚合方法不写入协作聚合主表。

### 8.4 事件流示意图

```text
DeviceRequest ──触发──> DeviceDispense（DRAFT / PREPARATION）
                              │ `DeviceDispensedEvent`（历史别名 `DeviceDispenseConfirmedEvent`）
                              ├──> InventoryItem（扣减）
                              ├──> `cus_trace_number` + `cus_trace_number_flow`（OUT，DM-CUS-011）
                              └──> ChargeItem（收费）
                              │ DeviceDispenseCancelledEvent
                              ├──> InventoryItem（回补）
                              └──> `cus_trace_number` + `cus_trace_number_flow`（IN，DM-CUS-011）
```

---

## 9. 补充

### 9.1 主路径提要（请求 → 收费/划价 → 发放）


| 阶段      | 典型触发        | 发放侧收口（参见 **§6.1**）                                             |
| ------- | ----------- | -------------------------------------------------------------- |
| 草稿      | 医嘱保存、临时路径生成 | **6.1.5** `generateDeviceDispenseFromTemporaryPath` → `DRAFT`  |
| 待发拣配    | 门诊收费/划价批量推进 | **6.1.6** `promoteDeviceDispenseToPreparation` → `PREPARATION` |
| 护士划价/绑定/门诊执行 | 开关 **关** | **WorkflowSupport** → **PREPARED**（**不**扣库；**不**改 DeviceRequest） |
| 护士划价/绑定/医技划价/门诊执行 | 开关 **开** | **Orchestrator** → 请求有 `lotNumber` **锁批定批**，空则 FIFO → **COMPLETED** + 合并扣库；同事务待扣预减（**不**改 DeviceRequest 状态） |
| 执行生成    | 执行后生成或更新发放单 | **6.1.7** / **6.1.8**（待发拣配或直发 `COMPLETED`）                     |
| 拣配与核对发放 | 工作台完成事实     | **6.1.1** → **6.1.2**；批号匹配与拆单见 **6.1.11**（**PR-DD-LOT-001**）   |
| 科室盘点汇总  | 盘点流程        | **6.1.12**～**6.1.14**                                          |


### 9.2 逆向流程提要（作废 / 停发 / 撤回 / 退耗材）


| 意图       | 典型终态                         | 写侧入口（参见 **§6.1**）                                 |
| -------- | ---------------------------- | ------------------------------------------------- |
| 未完成即作废   | `DECLINED`                   | **6.1.3**                                         |
| 退费或终止未发  | `STOPPED`（须原因 **BR-DD-003**） | **6.1.17** 等                                      |
| 已发放后撤回   | `CANCELLED`                  | **6.1.19**                                        |
| 退实物      | `REFUNDED`（幂等 **BR-DD-008**） | **6.1.9** → **6.1.4**                             |
| 释放草稿占用   | 逻辑删除（IF-DOR-16 签退 / FIFO 替换） | **6.1.10**、**6.1.16**、**6.1.22**                |
| 临时路径回退草稿 | `DRAFT`                      | **6.1.15**；批量完成态回退待发拣配见 **6.1.21**（**BR-DD-009**） |
| 护士取消执行（开关关） | `PREPARATION`                | **6.1.24** `revertPreparedBatchToPreparation`（**PREPARED→PREPARATION**；**不**回补库存） |
| 护士取消执行（开关开） | `PREPARATION`                | **6.1.21** `revertCompletedBatchToPreparation` + 编排回补库存 |


库存回补、追溯码 IN、费用冲销等由应用层编排消费事件或同事务调用协作域完成（与 **§8.2** 一致）。

### 9.3 预减统计说明

预减用于**可用量展示/门控**，统计范围与 **§7 BR-DD-QRY-003** 一致：


| 条件    | 说明                                                 |
| ----- | -------------------------------------------------- |
| 状态范围  | 仅 `DRAFT` 与 `PREPARATION`                          |
| 时间窗   | **无过期窗**：待发一直预占至执行或取消（`limit_time` 仍可落库作审计/兼容，读侧预减忽略） |
| 聚合维度  | 以耗材定义、库房/位置、批号等五元组口径与库存域对齐（见 **§4.3** 值对象说明）       |
| 与真实库存 | 预减为读侧/统计语义；**实际扣减以库存域校验与发放确认写路径为准**（**BR-DD-004**） |


### 9.4 工作台「待发核对发放」展示合并

- 列表层可将 `PREPARATION` / `PREPARED` / `IN_PROGRESS` 合并为「待发核对发放」展示口径，与 **§4.3** 脚注中「待发药」合并展示**同指**；持久化仍以 `status_enum` 为准（**BR-DD-QRY-002**）。
- 集成层须显式映射，避免把展示态写回主表事实。

### 9.5 发放 `bus_no`

- 每条 `wkf_device_dispense` 物理行独立 `getSeqByDay(DDP, 4)`；批号不足拆行时各行各自采番（**PR-DD-LOT-001**），**不**使用 `父busNo.stepNo` 级联。

### 9.6 与协作聚合的职责边界


| 职责      | DeviceDispense（本聚合）                 | InventoryItem | TraceNumberRecord（**DM-CUS-011**） | ChargeItem |
| ------- | ----------------------------------- | ------------- | --------------------------------- | ---------- |
| 发放事实    | 状态、数量、批号、追溯锚点、人员时间                  | —             | —                                 | —          |
| 库存扣减/回补 | 触发点与五元组口径（**BR-DD-004**）            | 执行账面增减        | —                                 | —          |
| 追溯轨迹    | 触发点与行级 `trace_no` 一致（**BR-DD-007**） | —             | 追加 OUT/IN                         | —          |
| 计费/退费   | 发放完成或退耗材完成后发事件                      | —             | —                                 | 订阅事件计费或冲销  |

### 9.7 文档变更历史

| 版本 | 日期 | 摘要 |
| ---- | ---- | ---- |
| v1.2.16 | 2026-10-18 | **模式 B**：§9.1 临时路径自动扣库——请求有 `lotNumber` 锁批定批，空则 FIFO；同事务待扣预减；对齐 **AS-IP-NUR-005 v2.25** / **DM-WKF-003 v1.2.15** |
| v1.2.15 | 2026-08-24 | 交叉文案：药品待发预占窗对照 **12h→24h**（耗材不过期不变）；对齐 **AS-OP-DOR-003 v1.67** / **DM-MED-002** |
| v1.2.14 | 2026-08-24 | **BR-DD-QRY-003 / §6.2.16 / §9.3**：耗材目录预占**不过期**（待发一直占至执行或取消）；与药品约 12h `limitTime` 窗分流；交叉 **AS-OP-DOR-003 · BR-ORD-011** |
| v1.2.13 | 2026-08-22 | **PR-DD-LOT-003**：挂费/收银须与发放行签发批号一致；交叉 **BR-ORD-033** / **BR-OPC-023** |
| v1.2.12 | 2026-08-21 | **BR-DD-009** 修订：取消执行回退 **保留**签发 `lot_number`（护士不重跑 FIFO）；§6.1.21/§6.1.24 同步；**PR-DD-LOT-002** 签发批号非空；待退/回退草稿不清批号 |
| v1.2.11 | 2026-08-19 | §6.2.2a `listDeviceDispensesByEncounterIds`（门诊护士站队列 **BR-NUR-017** 发放态批量读） |
| v1.2.10 | 2026-08-06 | §6.1.25 `amendDeviceDispenseDisplayTime`（`dispense_time` 优先否则 `prepare_time`；**AS-IP-NUR-005 IF-IP-PRC-07**） |
| v1.2.9 | 2026-08-01 | **§6.2.23**：`ConsumableUsageDispenseLineQueryModel` 明确带出 **`performerId`**（对齐 AS-RPT-005/006 执行人列） |
| v1.2.8 | 2026-07-13 | **§6.1.10 / §6.1.22**：IF-DOR-16 签退与 FIFO 签发替换改为**逻辑删除** DRAFT/PREPARATION 行（对齐 **`DM-MED-002` §6.1.9** `markDeleted`）；修正签退后再签发被 `STOPPED` 行拦截的问题。 |
| v1.2.7 | 2026-07-03 | **§6.2.20～6.2.22** 护士工作量耗材读侧（**AS-RPT-002**） |
| v1.2.6 | 2026-07-02 | **§6.2.19**：对齐 **`zxplat`** 读模型字段 **`deviceDefId`** 与应用层 **`inventory/dailysettlement`** 消费路径 |
| v1.2.5 | 2026-07-02 | **§6.2.19**：**AS-INV-RPT-001** 库房日结耗材发退药批量读 **`listDeviceDispensesByLocationAndDispenseWindow`** 落地 **`DeviceDispenseQueryService`** |
| v1.2.4 | 2026-06-25 | **移除耗材分次发放**：删除 **§6.1.20** `appendCompletedDeviceDispenseStep`；`bus_no` 仅按日流水；核对发放一次发全量至 `COMPLETED` |
| v1.2.3 | 2026-06-16 | **§6.1.10** `releaseDeviceDispenseReserve`：与药品释放预占对称——仅处理 DRAFT/PREPARATION/PREPARED；未命中或已释放**幂等静默**；IF-DOR-16 应用层优先逗号发放 id 定位。交叉 **AS-OP-DOR-003-SDD v3.26**。 |
| v1.2.2 | 2026-06-08 | 新增 **§6.1.24** `revertPreparedBatchToPreparation`（护士取消执行开关 **关**）；§9.1/§9.2 补充护士站两轨与对称回退 |
| v1.2.1 | 2026-05-28 | **M1**：§6.1.4a/§6.1.23 + §6.2.13～18 对齐 Java |

---

<!-- MODE-B-METHOD-INVENTORY 2026-09-23 -->

## 附录 · 模式 B 已实现方法清单（2026-09-23）

> 自 zxplat-domain 对应 *DomainService / *QueryService 抽取 public 方法名，用于关闭方法白名单 DOC-DRIFT。细规仍以正文 §6 与源码为准；本表不替代九章成稿。

### DomainService：`DeviceDispenseDomainService`

| 方法 |
|------|
| `prepareDeviceDispense` |
| `dispenseDevice` |
| `cancelUndispensedDeviceDispense` |
| `refundDispensedDeviceDispense` |
| `declinePendingRefundDeviceDispense` |
| `voidPendingRefundDeviceDispense` |
| `restoreDeclinedDeviceDispenseAfterRefundRevoke` |
| `generateDeviceDispenseFromTemporaryPath` |
| `replaceFifoDraftDispensesAfterDoctorStationSign` |
| `updateLocationForNurseExecute` |
| `promoteDeviceDispenseToPreparation` |
| `promoteDraftDispensesForDeviceRequests` |
| `generateDeviceDispenseFromExecution` |
| `generateIssuedDeviceDispenseFromExecution` |
| `createPendingRefundDeviceDispense` |
| `releaseDeviceDispenseReserve` |
| `matchDispenseLotNumber` |
| `updateDispenseStatusSummarized` |
| `updateDispenseStatusCompletedFromSummary` |
| `cancelDeviceDispenseSummary` |
| `rollbackUndispensedDeviceDispense` |
| `removeUndispensedDeviceDispense` |
| `stopUndispensedDeviceDispense` |
| `holdDeviceDispense` |
| `recallDispensedDeviceDispense` |
| `revertCompletedBatchToPreparation` |
| `revertPreparedBatchToPreparation` |
| `amendDeviceDispenseDisplayTime` |

### QueryService：`DeviceDispenseQueryService`

| 方法 |
|------|
| `loadDeviceDispenseById` |
| `listDeviceDispenseIdsByDeviceRequest` |
| `mapDeviceDispenseIdsByDeviceRequestIds` |
| `mapDeviceRequestToPrimaryDispenseLocationId` |
| `listDeviceDispensesByEncounter` |
| `listActiveDeviceDispensesByEncounterId` |
| `listDeviceDispensesByEncounterIds` |
| `listDeviceDispensesForOrderCatalogOccupancy` |
| `mustGetDeviceDispense` |
| `validateDeviceDispensePreparable` |
| `validateDeviceDispensable` |
| `validateDeviceDispenseCancellable` |
| `validateDeviceDispenseRefundable` |
| `getDevDispenseByProcedureId` |
| `selectByRequestIdList` |
| `resolvePrimaryDispenseIdForDeviceRequest` |
| `resolvePrimaryDispenseForDeviceRequest` |
| `mapPrimaryDisplayTimeByDeviceReqIds` |
| `mapPrimaryDisplayTimeByDisplayTimeWindow` |
| `mapPrimaryDispenseIdByDeviceReqIds` |
| `mapPrimaryDispenseStatusByDeviceReqIds` |
| `init` |
| `getEncounterInfoListPage` |
| `getDeviceDispenseOrderList` |
| `listDeviceDispensesByLocationAndDispenseWindow` |
| `findById` |
| `mapDeviceDispenseIdsToDeviceReqIds` |
| `listDeviceDispensesByIds` |
| `listDeviceDispensesByDeviceReqIds` |
| `existsInTransitDispenseByDeviceDefId` |
| `existsByDeviceDefIdAndLotNumber` |
| `listNurseConsumableWorkloadCandidateDispenses` |
| `summarizeNurseConsumableWorkloadByHvcm` |
| `pageNurseConsumableWorkloadDetail` |
| `listNurseConsumableWorkloadDetail` |
| `pageConsumableUsageDispenseLines` |
| `listConsumableUsageDispenseLines` |
| `pageConsumableUsageDispenseMergedByEncounterAndDevice` |
| `listConsumableUsageDispenseMergedByEncounterAndDevice` |
| `listConsumableUsageDispenseLinesForMergedKeys` |
| `summarizeConsumableUsageDispense` |
| `summarizeConsumableUsageDispenseWithLines` |

