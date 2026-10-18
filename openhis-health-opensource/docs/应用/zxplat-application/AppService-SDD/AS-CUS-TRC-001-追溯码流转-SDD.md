# 追溯码流转 · 应用服务详细设计（SDD）

> 编码：**AS-CUS-TRC-001-SDD**  
> 版本：**v1.10** | 日期：**2026-08-19** | 作者：（待填）

## 1. 实现边界

### 1.1 目标与边界

| 项 | 内容 |
| --- | --- |
| 模块 | **跨场景追溯编排**（无单一 UI 页）：**module-support 写编排** + **业务 App 嵌入读/写** |
| 边界 | **在内**：`TraceNumberRecordOrchestrationSupport` 等供 15+ 业务 AppService 嵌入；**IF-TRC-21** 批量身份反查嵌入消费方 App（进货/门诊发药/护士分配等）；**不在内**：独立 `application/custom` 包、各库房/发药页 Controller |
| 编排权威 | `AS-CUS-TRC-001-追溯码流转.md`（**v2.9**） |
| 方法权威 | **DM-CUS-010** §6.1/§6.2；**DM-SDD** §5、§13～§17 |
| 严格对齐 | 写路径 **`recordTraceRecords`**；`tracenumberrecord` 包**已删除** |
| 实现阶段口径 | **已成码**：Support + 业务嵌入 + 报表见 **AS-CUS-TRC-002-SDD** |

### 1.2 术语口径

| 项 | 约定 |
| --- | --- |
| 写入口 | 业务 AppService 同意/核发/确认事务内调用 **Support**，Support 调 **`TraceNumberDomainService#recordTraceRecordsFromLineItems`** |
| 流水类别 | 由 **`SupplyType` / 发药扩展码** 查 **AS-CUS-TRC-001 §2.1.1/2.1.2** 映射表 |
| 单位换算 | **DM-SDD §14**；Support 预加载 **`MedicationProductUnitConversionSource`** 填入 Command。流水拆零：**五类优先**；库存账面发退扣加同 **BR-OPP-017**，禁止贴码倍数覆盖同码包装单位 |
| 同事务 | Support **不**开新事务；参与调用方 `@Transactional` |
| 幂等 | 同 `bus_no` + 同 `operation_type` 已存在子表流水 → Gate 拒绝（**EX-TRC-208** 类） |

### 1.3 实现裁决（TRC-SDD-R-*）

| 裁决 ID | 内容 |
| --- | --- |
| **TRC-SDD-R-001** | 写 Support 落 **`health-module-support** · `com.zxplat.domain.module.common.assembler.tracenumber` |
| **TRC-SDD-R-002** | **IF-TRC-21** 无独立 AppService；嵌入 **`InventoryWarehouseInboundReceiptAppService`**、**`OutpatientPharmacyDispenseAppService`**、**`NurseStationTraceAssignmentAppService`** 等，直调 **`TraceNumberQueryService#batchResolveTraceIdentityByTraceNos`** |
| **TRC-SDD-R-003** | HTTP 走各业务 App 自动注册前缀（如 **`inventoryWarehouseInboundReceipt`**、**`outpatientPharmacyDispense`**）；**禁止** `/api/app/traceNumber/` 独立前缀 |
| **TRC-SDD-R-004** | 业务嵌入**不**新增独立 Controller；仍走各业务包现有 AppService |
| **TRC-SDD-R-005** | 业务嵌入统一 **`TraceNumberDomainService#recordTraceRecords*`**；`tracenumberrecord` 包**已删除** |
| **TRC-SDD-R-006** | **入库 IN**：异阶前填实入拆零（硬 Gate ceil 不变）。**发药 OUT**：**`TraceNumberQueryService#isRemainingSplitSufficientForDispenseOut`**（盖住判定，不抛）+ **`#allocateRemainingFirstSplitForDispenseOut`**（剩余升序，每码实扣落子表）；盖不住跳过写 OUT。**退药/退领 IN**：**`TraceNumberDispenseTraceOrchestrator#recordReturnInLines`** / **`TraceNumberSupplyAgreeTraceOrchestrator#recordDeptIssueReturnMirroredIn`** 镜像 OUT，禁止重算；退药行 `bus_no` 无 OUT 时按拷贝 `trace_no` 经 **`TraceNumberQueryService#listUnreturnedDispenseHospitalIoOutRecordsByTraceNos`** 回落 |
| **TRC-SDD-R-007** | **BR-TRC-020 有码批/无码批**：以 **`existsMasterByItemLot`** 分叉**写流水**。保存配药：**不做**未建档/品批不匹配硬拦；**须**调用 **`assertMasterCodesNotExhaustedForDispense`**（主档存在且剩余≤0 硬拒）。有码批核发写流水须盖住，否则软跳过；无码批跳过写主表。发药 slip 回填 **`lotHasTraceMaster`** |
| **TRC-SDD-R-008** | **BR-TRC-022 / IF-WHS-RT-13**：退供应商拉进货明细后，追溯码由 **`ReturnToSupplierRemainingTracePrefiller`** 按源药库本库净量优先（**`listInStockTraceNumbersByInventoryItemKey`** 须主档 `IN_HOSPITAL` 且剩余&gt;0）、空则全院在院剩余预填；截码 `min(可退余量, 源库可用量)` ceil；**禁止** Mapper 回放进货行 `trace_no`。无码批清空 |
| **TRC-SDD-R-009** | **BR-TRC-021**：门诊发药 UI 不扫放行；已扫但个数不足 → **确认弹窗**；确认后不足行清空再提交（避免有码批核发码数硬 Gate），禁止静默 |

## 2. 代码结构

### 2.1 Support 包（写编排）

```text
com/openhis/health/module/common/assembler/tracenumber/
  TraceNumberRecordOrchestrationSupport.java
  TraceNumberRecordLineAssemblySupport.java
  TraceNumberFlowCategoryResolver.java
  TraceNumberSupplyAgreeTraceWriteSupport.java
  TraceNumberDispenseTraceWriteSupport.java
  TraceNumberDispenseTraceOrchestrator.java
  TraceNumberSupplyAgreeTraceOrchestrator.java
  TraceNumberBatchIdentityResolveSupport.java
  TraceNumberStocktakeGainLossTraceWriteSupport.java
  command/
    RecordTraceFromSupplyLineCommand.java
    RecordTraceFromDispenseLineCommand.java
    RecordTraceTransferPairCommand.java
```

### 2.2 IF-TRC-21 嵌入点（无独立 Application 包）

| 消费方 AppService | 方法 | HTTP 前缀（自动注册） |
| --- | --- | --- |
| `InventoryWarehouseInboundReceiptAppService` | `batchResolveTraceIdentity` | `inventoryWarehouseInboundReceipt` |
| `OutpatientPharmacyDispenseAppService` | `batchResolveTraceIdentity` | `outpatientPharmacyDispense` |
| `NurseStationTraceAssignmentAppService` | IF-IP-TRC-03 内 enrich 主表批号 | `nurseStationTraceAssignment` |

**读侧 Support**：`TraceNumberBatchIdentityResolvesupport（Gate/拆分）；领域 **`TraceNumberQueryService#batchResolveTraceIdentityByTraceNos`**。

### 2.3 依赖注入（Support 使用方）

| 依赖 | 用途 |
| --- | --- |
| `TraceNumberDomainService` | `recordTraceRecords` / `recordTraceRecordsFromLineItems` |
| `MedicationProductUnitConversionSource` | 由 Support 从药品目录批量加载后填入 Command 行 |
| `TraceNumberFlowCategoryResolver` | §2.1.1/2.1.2 映射 |

## 3. `TraceNumberRecordOrchestrationsupport（写侧核心）

### 3.1 `recordFromSupplyLine(RecordTraceFromSupplyLineCommand cmd)`

**触发**：供应单同意路径（进货/调拨/领用等）在库存入账**之后**、同事务内。

**命令字段**：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `operatorId` | `Long` | 操作人 |
| `supplyTypeCode` | `String` | `SupplyType#getCode()` |
| `busNo` | `String` | 单据号 |
| `itemTable` | `String` | 品项表 |
| `itemId` | `Long` | 品项 id |
| `lotNumber` | `String` | 批号 |
| `traceNos` | `String` | 逗号串；空白跳过 |
| `traceUnitCode` | `String` | 进货必填；其它继承主档 |
| `quantity` | `BigDecimal` | 行业务数量 |
| `unitCode` | `String` | 行业务单位 |
| `locationId` | `Long` | 子表记录库位 |
| `locationTypeEnum` | `String` | 可空 |
| `flowDirectionEnum` | `String` | 单端 OUT/IN；调拨由 3.3 成对 |

**编排序列**：

1. Gate：`traceNos` 非空才继续。
2. `TraceNumberFlowCategoryResolver#resolve(supplyTypeCode)` → `flow_category` + 默认方向。
3. 组装 `recordTraceRecordsFromLineItemsCommand` → `TraceNumberDomainService#recordTraceRecordsFromLineItems`。
4. 返回流水 id 列表（可选；业务方可忽略）。

### 3.2 `recordFromDispenseLine(RecordTraceFromDispenseLineCommand cmd)`

**触发**：门诊/住院发药、汇总核发、退药确认。

| 字段 | 说明 |
| --- | --- |
| `operationType` | `MEDICATION_DISPENSE_OUT` / `MEDICATION_RETURN_IN` 等 |
| `flowCategoryEnum` | 固定 `HOSPITAL_IO` |
| `flowDirectionEnum` | OUT / IN |
| 其余 | 同 3.1 |

**Pre（OUT）**：**`isRemainingSplitSufficientForDispenseOut`** 判定已扫剩余合计是否盖住本次拆零（**不抛**；false → 跳过写主子表，等同未扫）。盖住后写侧调用 **`allocateRemainingFirstSplitForDispenseOut`**（剩余升序，每码实扣须落子表）。**进货/供应 IN** 仍走前填 + 硬 Gate，不在此软化。

**Pre（退药/退领 IN）**：**`recordReturnInLines`** / **`recordDeptIssueReturnMirroredIn`** 按原 OUT 子表每码量镜像加回；退药对冲行新 `bus_no` 时先本行单号、再按拷贝 `trace_no` 回落未回冲 OUT；**禁止**调用剩余先出/前填对当前剩余重分摊。

### 3.3 `recordTransferPair(RecordTraceTransferPairCommand cmd)`

**触发**：调拨/请领入库/退库等 **成对** OUT+IN。

| 字段 | 说明 |
| --- | --- |
| `busNo` | 同一单据号 |
| `outLocationId` / `inLocationId` | 来源/目的 |
| `lines` | 每码一行；`flow_category=INTERNAL_TRANSFER` |

**约束**：同一 `bus_no` 内 OUT 条数与 IN 条数按码/数量成对（**BR-TNR-006**）。

### 3.4 `TraceNumberStocktakeGainLossTraceOrchestrator`（IF-TRC-14）

**触发**：盘点/损益 agree、库存盈亏入账**之后**、同事务内。

**盘点**（`recordFromAgreedLines`）：行 `trace_no` 为实盘**剩余扫码**。

| 差分 | 写 |
| --- | --- |
| 读本库在库码集合 | `listInStockTraceNumbersByInventoryItemKey` |
| 盘亏（`item_quantity` &lt; 0） | `在库码 − 剩余扫码` → 各缺失码 `HOSPITAL_IO` OUT |
| 盘盈（`item_quantity` &gt; 0） | `剩余扫码 − 在库码` → 各盈余新码 `HOSPITAL_IO` IN |
| 空白 `trace_no` 且盘亏 | 追溯不变（跳过 OUT，不强制扫、不默认全出） |

**损益**（`recordDirectFromAgreedGainLossLines`）：行 `trace_no` 为**盈亏码本身**；与 `item_quantity` 正负同向直接加减（无码行跳过）。

| 方向 | 写 |
| --- | --- |
| 报损（`item_quantity` &lt; 0） | 扫到的码 → `HOSPITAL_IO` OUT（数量取盈亏绝对值） |
| 报溢（`item_quantity` &gt; 0） | 扫到的码 → `HOSPITAL_IO` IN |

## 4. IF-TRC-21 批量扫码身份反查（嵌入业务 App）

> **禁止** `TraceNumberResolveAppService` / `application/custom/tracenumber` 独立包。

### 4.1 嵌入方法契约（以进货为例）

| 项 | 内容 |
| --- | --- |
| 方法 | `batchResolveTraceIdentity(BatchResolveTraceIdentityRequest)` |
| Request | `traceNos: List<String>`（≤50） |
| Response | `items` + `failItems`（与入参同序） |
| 编排序列 | Gate（`TraceNumberBatchIdentityResolvesupport）→ `TraceNumberQueryService#batchResolveTraceIdentityByTraceNos` → DTO 映射 |

### 4.2 原 §4 独立 App 说明（已废弃）

~~`POST /api/app/traceNumber/batchResolveTraceIdentity`~~ → 改走各消费方 App 前缀。

## 5. 业务嵌入替换对照表

| 业务 AppService | 旧调用 | 新 Support 方法 | flow_category | AS IF |
| --- | --- | --- | --- | --- |
| `InventoryWarehouseInboundReceiptAppService` | `recordTraceRecords*` | `recordFromSupplyLine` | `HOSPITAL_IO` IN | IF-TRC-05 |
| `InventoryWarehouseReturnToSupplierAppService` | 同上 | `recordFromSupplyLine` | `HOSPITAL_IO` OUT | IF-TRC-06 |
| `InventoryWarehouseStockOutAppService` | 同上 | `recordTransferPair` | `INTERNAL_TRANSFER` | IF-TRC-07/08 |
| `InventoryPharmacyStockInAppService` | 同上 | `recordTransferPair` | `INTERNAL_TRANSFER` | IF-TRC-08 |
| `InventoryWarehouseTransferOutAppService` | 同上 | `recordTransferPair` | `INTERNAL_TRANSFER` | IF-TRC-09 |
| `InventoryPharmacyTransferOutAppService` | 同上 | `recordTransferPair` | `INTERNAL_TRANSFER` | IF-TRC-09 |
| `InventoryDepartmentTransferOutAppService` | 同上 | `recordTransferPair` | `INTERNAL_TRANSFER` | IF-TRC-09 |
| `InventoryDepartmentDeptIssueAppService` | 同上 | `recordFromSupplyLine` | `HOSPITAL_IO` OUT | IF-TRC-12 |
| `InventoryDepartmentDeptIssueReturnAppService` | 同上 | `recordFromSupplyLine` | `HOSPITAL_IO` IN | IF-TRC-13 |
| `InventoryPharmacyReturnToWarehouseAppService` | 同上 | `recordTransferPair` | `INTERNAL_TRANSFER` | IF-TRC-09 |
| `InventoryDepartmentReturnToWarehouseAppService` | 同上 | `recordTransferPair` | `INTERNAL_TRANSFER` | IF-TRC-11 |
| `OutpatientPharmacyDispenseAppService` | 同上 | `recordFromDispenseLine` | `HOSPITAL_IO` OUT | IF-TRC-16 |
| `InpatientPharmacyDispenseAppService` | 同上 | `recordFromDispenseLine` | `HOSPITAL_IO` OUT | IF-TRC-16 |
| `InpatientPharmacySummaryDispenseAppService` | 同上 | `recordFromDispenseLine` | `HOSPITAL_IO` OUT | IF-TRC-15 |
| `OutpatientPharmacyReturnMedicineAppService` | 同上 | `recordFromDispenseLine` | `HOSPITAL_IO` IN | IF-TRC-17 |
| `InpatientPharmacyReturnMedicineAppService` | 同上 | `recordFromDispenseLine` | `HOSPITAL_IO` IN | IF-TRC-17 |
| `InventoryWarehouseStocktakeAppService` | 同上 | `recordFromAgreedLines` | `HOSPITAL_IO` OUT/IN | IF-TRC-14 |
| `InventoryPharmacyStocktakeAppService` | 同上 | `recordFromAgreedLines` | `HOSPITAL_IO` OUT/IN | IF-TRC-14 |
| `InventoryDepartmentStocktakeAppService` | 同上 | `recordFromAgreedLines` | `HOSPITAL_IO` OUT/IN | IF-TRC-14 |
| `InventoryWarehouseGainLossAppService` | 同上 | `recordDirectFromAgreedGainLossLines` | `HOSPITAL_IO` OUT/IN | IF-TRC-14 |
| `InventoryPharmacyGainLossAppService` | 同上 | `recordDirectFromAgreedGainLossLines` | `HOSPITAL_IO` OUT/IN | IF-TRC-14 |
| `InventoryDepartmentGainLossAppService` | 同上 | `recordDirectFromAgreedGainLossLines` | `HOSPITAL_IO` OUT/IN | IF-TRC-14 |

> 盘点 IF-TRC-14：行 `trace_no` 存**实盘剩余码**；同意时与本库在库码差分 → 未扫到 OUT、多扫到 IN。
> 损益 IF-TRC-14：行 `trace_no` 存**盈亏码**；同意时与数量正负同向直接 OUT/IN。

> 盘点/损益类供应单：映射 **IF-TRC-14**，`HOSPITAL_IO` 方向按盈亏；**剩余码差分**见上表。

## 6. QueryService 与领域事件

| 能力 | 领域方法 | 消费方 |
| --- | --- | --- |
| 批量身份反查 | `batchResolveTraceIdentityByTraceNos` | §4 |
| 报表/库存读 | `pageTraceNumberReport 等 | **AS-CUS-TRC-002-SDD** |
| 写事件 | `TraceNumberRecordRecordedEvent` | 审计（可选订阅） |

## 7. 写库顺序（业务嵌入视角）

以进货同意为例（与 **AS-INV-WHS-002-SDD** 同事务）：

1. `SupplyRequest` → AGREE  
2. `SupplyDelivery` INSERT  
3. 定价 / `InventoryItem` 入账  
4. **`TraceNumberRecordOrchestrationSupport#recordFromSupplyLine`**（每非空 `trace_no` 行）  
5. 提交；失败整体回滚  

## 8. 非功能性需求

| 项 | 实现 |
| --- | --- |
| 事务 | 嵌入调用方事务；Support 无 `@Transactional` |
| 批量扫码 | ≤50；`IN` 查询主表 |
| 废弃路径 | `TraceNumberRecord*` 包只读至 V109 迁移完成 |

## 9. 测试要点

| 用例 ID | 场景 |
| --- | --- |
| TC-TRC-SUP-001 | 进货 1 码 → 主档建立 + 1 条 HOSPITAL_IO IN |
| TC-TRC-SUP-002 | 调拨成对 → 主表剩余不变 + 2 条 INTERNAL_TRANSFER |
| TC-TRC-SUP-003 | 发药 OUT 耗尽 → NOT_IN_HOSPITAL |
| TC-TRC-SUP-004 | 退药 IN → 回在院 |
| TC-TRC-SUP-005 | 同 bus_no 重复写入 → 拒绝 |
| TC-TRC-RES-001 | batchResolve 10 码 → 顺序与状态 |
| TC-TRC-RES-002 | NOT_IN_HOSPITAL + scene=DISPENSE → failItem |

## 11. 业务撤销与冲正

见 **AS-CUS-TRC-001 §2.4**。Supcontracts 层**不**单独提供撤销 Service；各业务 AppService 在冲正/退药路径按表调用对应 **IF-TRC-06/09/17**。

## 12. 变更历史

| 版本 | 日期 | 摘要 |
| --- | --- | --- |
| v1.11 | 2026-08-19 | **UI 盖住**：前端 `resolveDispenseOperationSplitQty` 须输出真拆零（对齐 **AS** v2.11 / **DD** v1.8）；同阶盒数禁止直接比主档片剩余 |
| v1.10 | 2026-08-19 | 单位换算对齐 **DM-CUS-010 v2.0.10** / **BR-OPP-017**：五类优先，贴码倍数不覆盖同码包装单位做库存扣加；对齐 **AS** v2.10 |
| v1.9 | 2026-08-12 | **TRC-SDD-R-007**：发药耗尽硬拦 `assertMasterCodesNotExhaustedForDispense`；对齐 **AS** v2.9 |
| v1.8 | 2026-08-12 | **TRC-SDD-R-007**：消耗侧保存配药取消码身份硬 Gate（仅进货硬拦）；对齐 **AS-CUS-TRC-001** v2.8 |
| v1.7 | 2026-08-11 | **TRC-SDD-R-008**：本库在库读侧叠加主档在院剩余；Prefiller 截码 `min(可退, 源库可用)`；对齐 **AS-CUS-TRC-001** v2.7 / **AS-INV-WHS-005** v0.5 |
| v1.6 | 2026-08-02 | **UI**：扫码弹窗去掉处方号；追溯码单位/码倍列收窄；发药无主档回落药典；对齐 **AS-CUS-TRC-001** v2.5 |
| v1.5 | 2026-08-02 | **UI**：批量扫码弹窗「药品明细」展示 `traceUnitCode(Text)` 与 `traceUnitRatio`（1 码单位=N 拆零）；发药主档快照 / 库房目录；对齐 **AS-CUS-TRC-001** v2.4 |
| v1.5 | 2026-08-20 | **TRC-SDD-R-006 / Pre**：退药镜像 OUT 回落 `listUnreturnedDispenseHospitalIoOutRecordsByTraceNos`（对冲行新 bus_no） |
| v1.4 | 2026-07-25 | **DD v1.2**：**TRC-SDD-R-006** 剩余先出 OUT + 镜像 IN；废止 **`assertRemainingSplitSufficientForDispenseOut`** 硬 Gate；编排权威 **AS v2.1** |
| v1.3 | 2026-07-25 | **TRC-SDD-R-008/009**：**BR-TRC-021/022**；退供应商剩余码预填；门诊半扫确认；编排权威 **AS v1.11** |
| v1.2 | 2026-07-24 | **TRC-SDD-R-007 / BR-TRC-020**：有码批硬校验写流水、无码批仅业务行；`lotHasTraceMaster`；`batchExistsTraceMasterByItemLot`；编排权威 **AS v1.10** |
| v1.1 | 2026-07-08 | **TRC-SDD-R-006**：每码数量展开 + **`assertRemainingSplitSufficientForDispenseOut`**；**§3.2** 发药 Pre Gate；编排权威 **AS v1.9** |
| v1.0 | 2026-07-06 | 首版：Support 写编排 + IF-TRC-21 读 AppService + 业务替换表 + §11 撤销冲正引用 |

---

**本 SDD 正文结束。**
