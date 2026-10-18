# DM-CUS-010 TraceNumberRecord 追溯码记录 · DomainModel-SDD

> **对应 DM**：`docs/领域/DomainModel/DM-CUS-010-TraceNumberRecord-追溯码记录.md`  
> **对应 AS**：`health/AppService/AS-CUS-TRC-001-追溯码流转.md`（**v1.7**）  
> **对应 AS（报表）**：`health/AppService/AS-CUS-TRC-002-追溯码查询报表.md`（**v1.0**）  
> **目标工程**：`zxplat-core/zxplat-domain`  
> **限界上下文**：CUS 本地扩展  
> **聚合**：TraceNumber（主档）+ TraceNumberRecord（子表记录）

---

## 输入完整性检查结果

| 最小输入项 | 状态 | 说明 |
| ---------- | ---- | ---- |
| DM 路径 | 满足 | 已绑定 DM-CUS-010 v2.0.5 |
| SDD 输出路径 | 满足 | 本文件 |
| 目标模块 | 满足 | `zxplat-domain` |
| 包规则 `com.zxplat.domain.custom.tracenumber` | 满足 | **唯一**领域包；`tracenumberrecord` **已删除** |
| DM 第 6 章写/读白名单 | 满足 | §5 写 2 个、读 **10** 个 |
| DM 第 7 章 BR | 满足 | §6 映射 BR-TNR-001～015 |
| DM 第 5 章事件 | 满足 | §7 覆盖 2 个事件 |

---

## 1. 设计目标与边界

### 1.1 设计目标

- 固化**主表现态 + 子表流水**持久化与写读契约。
- 写路径唯一：`recordTraceRecords` 同事务 INSERT 子表 + UPDATE 主表。
- 领域层**禁止跨聚合**查药品目录（**B.6.4**）：写侧 `unitConversionSource` 由 Command 携带；读侧库位轧差 SUM `split_quantity`。
- 全院在院与库位列表**双轨读法**（主表 vs 子表聚合）。

### 1.2 边界

- **在内**：`cus_trace_number`、`cus_trace_number_flow`、领域服务、事件。
- **在外**：页面编排、跨域患者反查、医保报文。
- **历史表**：`cus_trace_number` + `cus_trace_number_flow` 单表**不再写入**；可选 Flyway 历史迁移见 §16（与成码包删除无关）。
- **本期成码范围（2026-07-06）**：领域读/写**仅药品**（`item_table=med_medication_definition`）；DM §4.1 器材枚举与 `adm_device_definition` 路径**文档保留、代码待后续迭代**；拒绝时错误码 `TRACE_NUMBER_007`。

---

## 2. 工程落点与包结构

### 2.1 Maven 模块

- `zxplat-domain`、`zxplat-domain`（Flyway）。

### 2.2 包结构（`com.zxplat.domain.custom.tracenumber`）

| 相对包 | 内容 |
| ------ | ---- |
| `entity` | `TraceNumber`、`TraceNumberRecord` |
| `command` | `RecordTraceRecordsCommand` 等 |
| `service` | `TraceNumberDomainService`、`TraceNumberQueryService` |
| `queries` / `queries.model` | 查询入参/出参 |
| `repository` | `TraceNumberRepository`、`TraceNumberRecordRepository` |
| `support | `TraceNumberLocationNetsupport |
| `share.constants` | `TraceNumberConstants`、`FieldNameConstants`（追溯字段） |
| `share.events.custom.tracenumber` | 领域事件 |

---

## 3. 类型 / 字段命名 / 注解规则

| 规则 | 说明 |
| ---- | ---- |
| 主键 | `Long` 雪花 |
| `*_enum` | `String` 存枚举 **code**（B.10.8.1） |
| `*_code` | `String` |
| 主表 | 可 UPDATE `remaining_split_quantity`、`in_hospital_status_enum` |
| 子表 | **只 INSERT** |
| `trace_no`（业务表逗号串） | VARCHAR(50000)（TRC-WIDE-R-001） |
| `trace_no`（主表/子表单码） | VARCHAR(255) 或实现定稿宽度 |

---

## 4. 实体 / 值对象 / 枚举 / 仓储 / 查询契约

### 4.1 表与实体映射

| 逻辑表 | 实体类 | 说明 |
| ------ | ------ | ---- |
| `cus_trace_number` | `TraceNumber` | 聚合根；一码一行 |
| `cus_trace_number_flow` | `TraceNumberRecord` | 子实体记录；物理表名历史保留 `flow` |

### 4.2 聚合根 `TraceNumber`（字段级契约）

| 字段名（Java） | 类型 | 必填 | 说明 / 约束 |
| -------------- | ---- | ---- | ----------- |
| `id` | `Long` | 是 | 主键 |
| `traceNo` | `String` | 是 | 单码；租户内唯一 |
| `itemTable` | `String` | 是 | 物理表名 |
| `itemId` | `Long` | 是 | 品项 ID |
| `lotNumber` | `String` | 是 | 批号 |
| `traceUnitCode` | `String` | 是 | 追溯码单位（包装粒度） |
| `remainingSplitQuantity` | `BigDecimal` | 是 | 拆零剩余；便捷字段 |
| `inHospitalStatusEnum` | `String` | 是 | `IN_HOSPITAL` / `NOT_IN_HOSPITAL` |

**不含** `locationId`。方法：`assertRequiredFields`、`applyHospitalIoDelta`。

### 4.3 子实体 `TraceNumberRecord`

| 字段名（Java） | 类型 | 必填 | 说明 |
| -------------- | ---- | ---- | ---- |
| `id` | `Long` | 是 | 主键 |
| `traceNumberId` | `Long` | 是 | FK 主档 |
| `traceNo` | `String` | 是 | 冗余 |
| `flowCategoryEnum` | `String` | 是 | `HOSPITAL_IO` / `INTERNAL_TRANSFER` |
| `flowDirectionEnum` | `String` | 是 | `IN` / `OUT` |
| `locationTypeEnum` | `String` | 否 | 记录用 |
| `locationId` | `Long` | 否 | 记录用；库位聚合读侧 |
| `locationStoreId` | `Long` | 否 | 可空 |
| `lotNumber` | `String` | 是 | 批号 |
| `quantity` | `BigDecimal` | 是 | 本条业务数量 |
| `unitCode` | `String` | 是 | 本条业务单位 |
| `splitQuantity` | `BigDecimal` | 是 | 拆零量（写侧换算落库） |
| `operationType` | `String` | 是 | 操作类型 code |
| `busNo` | `String` | 否 | 单据号 |

### 4.5 仓储接口

- `TraceNumberRepository`：`findByTraceNoAndIsDeletedFalse`、`findAllByTraceNoInAndIsDeletedFalse`、`save`
- `TraceNumberRecordRepository`：`saveAll`、`findAllByTraceNoAndIsDeletedFalseOrderByIdAsc` 等

### 4.6 查询模型（`queries.model`）

#### 4.6.1 `TraceNumberQueryModel`（`getTraceNumberByTraceNo`）

| 字段名 | 类型 | 说明 |
| ------ | ---- | ---- |
| `id` | `Long` | 主档 id |
| `traceNo` | `String` | 追溯码 |
| `itemTable` | `String` | 品项表 |
| `itemId` | `Long` | 品项 id |
| `lotNumber` | `String` | 批号 |
| `traceUnitCode` | `String` | 追溯码单位 |
| `remainingSplitQuantity` | `BigDecimal` | 拆零剩余 |
| `inHospitalStatusEnum` | `String` | 在院状态 |

#### 4.6.2 `TraceNumberRecordQueryModel`（`listRecordsByTraceNo`）

| 字段名 | 类型 | 说明 |
| ------ | ---- | ---- |
| `id` | `Long` | 记录 id |
| `traceNo` | `String` | 追溯码 |
| `flowCategoryEnum` | `String` | 记录类别 |
| `flowDirectionEnum` | `String` | IN/OUT |
| `locationId` | `Long` | 库位 |
| `quantity` | `BigDecimal` | 业务数量 |
| `unitCode` | `String` | 业务单位 |
| `splitQuantity` | `BigDecimal` | 拆零量 |
| `operationType` | `String` | 操作类型 |
| `busNo` | `String` | 单据号 |
| `createdAt` | `LocalDateTime` | 时间 |

#### 4.6.3 `InHospitalTraceNumberPageQueryModel`（`pageInHospitalTraceNumbers`）

同 `TraceNumberQueryModel` 列表字段。

#### 4.6.4 `LocationTraceNumberQueryModel`（`listTraceNumbersByLocationId`）

| 字段名 | 类型 | 说明 |
| ------ | ---- | ---- |
| `traceNo` | `String` | 追溯码 |
| `itemId` | `Long` | 品项 |
| `lotNumber` | `String` | 批号 |
| `locationNetSplitQuantity` | `BigDecimal` | 子表聚合净拆零量 |

#### 4.6.5 `TraceRecordPageQueryModel`（`getTraceRecordPage`）

同 `TraceNumberRecordQueryModel` + 分页。入参 `TraceRecordPageQuery`（`recordTimeStart`/`recordTimeEnd`）。

#### 4.6.6 `TraceNumberIdentityQueryModel`（`batchResolveTraceIdentityByTraceNos`）

| 字段名 | 类型 | 说明 |
| ------ | ---- | ---- |
| `traceNo` | `String` | 入参码（规范化后） |
| `resolveStatusEnum` | `String` | `OK` / `NOT_FOUND` / `NOT_IN_HOSPITAL` / `DUPLICATE_IN_REQUEST` |
| `itemTable` | `String` | 品项表；`NOT_FOUND` 时可空 |
| `itemId` | `Long` | 品项 id（药品/耗材定义） |
| `lotNumber` | `String` | 批号 |
| `traceUnitCode` | `String` | 追溯码单位 |
| `remainingSplitQuantity` | `BigDecimal` | 拆零剩余 |
| `inHospitalStatusEnum` | `String` | 在院状态 |

#### 4.6.7 `InventoryItemTraceNumberQueryModel`（`listInStockTraceNumbersByInventoryItemKey`）

| 字段名 | 类型 | 说明 |
| ------ | ---- | ---- |
| `traceNo` | `String` | 追溯码 |
| `locationNetSplitQuantity` | `BigDecimal` | 本库净拆零量 |
| `traceUnitCode` | `String` | 追溯码单位 |
| `inHospitalStatusEnum` | `String` | 全院在院状态 |

#### 4.6.8 `TraceNumberReportRowQueryModel`（`pageTraceNumberReport）

| 字段名 | 类型 | 说明 |
| ------ | ---- | ---- |
| `traceNo` | `String` | 追溯码 |
| `itemId` | `Long` | 药品 id |
| `lotNumber` | `String` | 批号 |
| `inHospitalStatusEnum` | `String` | 全院在院状态 |
| `remainingSplitQuantity` | `BigDecimal` | 拆零剩余 |
| `traceUnitCode` | `String` | 追溯码单位 |
| `locationStockStatusEnum` | `String` | `IN_LOCATION` / `OUT_OF_LOCATION` |
| `locationNetSplitQuantity` | `BigDecimal` | 本库净拆零量（可选） |
| `lastFlowAt` | `LocalDateTime` | 最近子表流水时间 |

**入参文件**：`TraceNumberReportPageQuery.java`（含 `queryScope`、`locationId`、关键字、流水时间区间、分页）。

---

## 5. 写侧与读侧白名单（命令字段全集）

### 5.1 写侧 — `TraceNumberDomainService`

#### 5.1.1 `recordTraceRecords(RecordTraceRecordsCommand cmd)` → `List<Long>`

**命令文件**：`RecordTraceRecordsCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `operatorId` | `Long` | 操作人 |
| `lines` | `List<RecordLine>` | 记录行列表 |
| `RecordLine.traceNo` | `String` | 追溯码 |
| `RecordLine.itemTable` | `String` | 品项表（建主档时） |
| `RecordLine.itemId` | `Long` | 品项 id |
| `RecordLine.lotNumber` | `String` | 批号 |
| `RecordLine.traceUnitCode` | `String` | 追溯码单位（建主档/进货） |
| `RecordLine.flowCategoryEnum` | `String` | `HOSPITAL_IO` / `INTERNAL_TRANSFER` |
| `RecordLine.flowDirectionEnum` | `String` | `IN` / `OUT` |
| `RecordLine.quantity` | `BigDecimal` | 业务数量 |
| `RecordLine.unitCode` | `String` | 业务单位 |
| `RecordLine.unitConversionSource` | `MedicationProductUnitConversionSource` | **应用层预加载**；领域禁止跨聚合查目录 |
| `RecordLine.locationId` | `Long` | 库位（记录） |
| `RecordLine.locationTypeEnum` | `String` | 库位类型 |
| `RecordLine.locationStoreId` | `Long` | 仓位 |
| `RecordLine.operationType` | `String` | 操作类型 |
| `RecordLine.busNo` | `String` | 单据号 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `TraceNumber.assertRequiredFields` / `TraceNumberRecord.assertRequiredFields` |
| `statusTransitionMethod` | `TraceNumber.applyHospitalIoDelta` |
| `repositoryChecks` | 主档存在性、剩余充足、调拨成对 |
| `eventPublishAnchor` | `TraceNumberRecordRecordedEvent` / `TraceNumberInitializedEvent` |
| `unitTestCaseId` | `TC-DM-CUS-010-W-001` |

**方法契约摘要**：校验 → 建/载主档 → 换算 `splitQuantity` → INSERT 子表 → UPDATE 主档（`HOSPITAL_IO`）→ beforeCommit 事件。

---

#### 5.1.2 `recordTraceRecordsFromLineItems(RecordTraceRecordsFromLineItemsCommand cmd)` → `List<Long>`

**命令文件**：`RecordTraceRecordsFromLineItemsCommand.java`

**字段全集**：`operatorId`、行列表（`traceNos` 逗号串、`itemTable`、`itemId`、`lotNumber`、`traceUnitCode`、`quantity`、`unitCode`、`unitConversionSource`、`flowCategoryEnum`、`flowDirectionEnum`、`locationId`、`operationType`、`busNo`）。

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | 同上 |
| `eventPublishAnchor` | `TraceNumberRecordRecordedEvent` |
| `unitTestCaseId` | `TC-DM-CUS-010-W-002` |

**方法契约摘要**：拆逗号串 → 组装 `RecordTraceRecordsCommand` → 委托 `recordTraceRecords`。

### 5.2 读侧 — `TraceNumberQueryService`

| 方法 | 入参 | 出参 | `unitTestCaseId` |
| ---- | ---- | ---- | ---------------- |
| `getTraceNumberByTraceNo` | `traceNo` | `TraceNumberQueryModel` | `TC-DM-CUS-010-R-001` |
| `listRecordsByTraceNo` | `traceNo` | `List<TraceNumberRecordQueryModel>` | `TC-DM-CUS-010-R-002` |
| `pageInHospitalTraceNumbers` | 分页/筛选 | `Page<InHospitalTraceNumberPageQueryModel>` | `TC-DM-CUS-010-R-003` |
| `listTraceNumbersByLocationId` | `locationId` | `List<LocationTraceNumberQueryModel>` | `TC-DM-CUS-010-R-004` |
| `getTraceRecordPage` | `TraceRecordPageQuery` | `Page<TraceRecordPageQueryModel>` | `TC-DM-CUS-010-R-005` |
| `batchResolveTraceIdentityByTraceNos` | `List<String> traceNos` | `List<TraceNumberIdentityQueryModel>` | `TC-DM-CUS-010-R-006` |
| `listInStockTraceNumbersByInventoryItemKey` | `locationId`、`itemTable`、`itemId`、`lotNumber` | `List<InventoryItemTraceNumberQueryModel>` | `TC-DM-CUS-010-R-007` |
| `pageTraceNumberReport` | `TraceNumberReportPageQuery` | `Page<TraceNumberReportRowQueryModel>` | `TC-DM-CUS-010-R-008` |
| `assertRemainingSplitSufficientForDispenseOut` | `Collection<String> traceCodes`、`BigDecimal lineQuantity`、`String unitCode`、`MedicationProductUnitConversionSource unitConversionSource` | void（失败抛 **`BusinessException`**） | `TC-DM-CUS-010-R-009` |
| `listFlowRecordsByInventoryItemKey` | `locationId`、`itemTable`、`itemId`、`lotNumber` | `List<TraceNumberRecordQueryModel>` | `TC-DM-CUS-010-R-010` |

**`assertRemainingSplitSufficientForDispenseOut` 契约摘要**：业务单位=销售包装时校验码个数与发药量一致且每码主档剩余 ≥ 1 盒拆零量；拆零发药时按均分规则校验；与 **`TraceNumberRecordLineAssemblySupport`** OUT 展开一致。**禁止**注入外聚合 QueryService（换算源由入参携带）。

**`batchResolveTraceIdentityByTraceNos` 契约摘要**：入参非空列表 → 请求内 trim/去重 → `repository.findAllByTraceNoInAndIsDeletedFalse` 批量加载 → 按入参顺序组装出参；主表无行则 `NOT_FOUND`；在院态不满足则 `NOT_IN_HOSPITAL`；重复入参码标记 `DUPLICATE_IN_REQUEST`（仅第二条起）。**不写库**。

**`listInStockTraceNumbersByInventoryItemKey` 契约摘要**：子表按 `locationId` SUM `split_quantity` 轧差净拆零量 **> 0**；主表过滤 `itemTable`/`itemId`/`lotNumber`；本期 `item_table=med_medication_definition`。

**`listFlowRecordsByInventoryItemKey` 契约摘要**：子表按 `locationId` + 主表 `itemTable`/`itemId`/`lotNumber` 过滤返回**全量流水**（非轧差）；供 CHS 3505/3506 按方向过滤。**不写库**。

**`pageTraceNumberReport` 契约摘要**：`item_table=med_medication_definition`；`queryScope` 用 `TraceNumberConstants.REPORT_QUERY_SCOPE_*`；`locationStockStatusEnum` 当前页内存过滤（DOC-CONFLICT：total 仍为主表分页值）；默认按 `lastFlowAt` 降序。

---

## 6. 不变式映射（DM §7）

| BR 编号 | 实现策略摘要 |
| ------- | ------------ |
| BR-TNR-001 | 主档必填校验 | `TC-DM-CUS-010-BR-001` |
| BR-TNR-002 | 子表只 INSERT | `TC-DM-CUS-010-BR-002` |
| BR-TNR-003 | 仅 `HOSPITAL_IO` 动主表剩余 | `TC-DM-CUS-010-BR-003` |
| BR-TNR-004 | 患者追溯按 operationType + direction 查子表 | `TC-DM-CUS-010-BR-004` |
| BR-TNR-005 | 软删过滤 | `TC-DM-CUS-010-BR-005` |
| BR-TNR-006 | 调拨成对、不动主表剩余 | `TC-DM-CUS-010-BR-006` |
| BR-TNR-007 | 逗号拆码清洗 | `TC-DM-CUS-010-BR-007` |
| BR-TNR-008 | 单位换算后动主表 | `TC-DM-CUS-010-BR-008` |
| BR-TNR-009 | 主表无 locationId | `TC-DM-CUS-010-BR-009` |
| BR-TNR-010 | 子表方向不可变 | `TC-DM-CUS-010-BR-010` |
| BR-TNR-011 | 出库不超剩余 | `TC-DM-CUS-010-BR-011` |
| BR-TNR-012 | 应用层码格式 Gate | `TC-DM-CUS-010-BR-012` |
| BR-TNR-013 | 库位列表子表聚合 | `TC-DM-CUS-010-BR-013` |
| BR-TNR-014 | 主子同事务 | `TC-DM-CUS-010-BR-014` |
| BR-TNR-015 | 批量 IN 查询 + 去重 + 条数上限 | `TC-DM-CUS-010-BR-015` |

---

## 7. 事件契约与触发规则（DM §5）

| 事件类名 | 最小载荷 | 触发方法 |
| -------- | -------- | -------- |
| `TraceNumberRecordRecordedEvent` | `traceNumberId`、`traceNo`、`recordCount`、`busNo`、`operationType` | `recordTraceRecords` |
| `TraceNumberInitializedEvent` | `traceNumberId`、`traceNo`、`traceUnitCode` | `recordTraceRecords`（首次建主档） |

发布时机：**beforeCommit**。

---

## 8. 删除与可见性契约

- 主档逻辑删除须与子表只读策略一致；子表流水**禁止物理删除**。
- 默认查询 `is_deleted=false`。

---

## 9. 框架优先适配规则

- 遵循项目级成码门禁；Repository 仅接口。
- 单位换算：`MedicationProductQuantityToSplitUnitConverter` 仅在**写侧**使用；倍数顺序为目录五类（拆零=1 → 剂量 → 采购 → 库存 → 包装），**同码「盒」不得用 `trace_unit_ratio` 覆盖五类**；仅操作单位不在五类时才回落贴码倍数。换算源由应用/support 预加载填入 Command（**B.6.4**）。库存 `wkf_inventory_item` 发退扣加与本器同口径（**AS-OP-PHA-001 BR-OPP-017**）。
- 读侧库位轧差：`TraceNumberLocationNetSupport#aggregateNetSplitByTraceNo` 直 SUM `split_quantity`，**禁止**读路径再查药品目录。

---

## 10. 生成与验收清单

- [ ] 主/子表 Flyway 与实体字段对齐 DM §3
- [ ] §5.1 写方法 2 个与 DM §6.1 一致
- [ ] §5.2 读方法 **9** 个与 DM §6.2 一致
- [ ] §13 Flyway **V107～V109** 与 §4 实体字段一致
- [ ] §14～§17 换算/聚合/迁移单测可复现
- [ ] 原 `com.zxplat.domain.custom.tracenumber` 包**已删除**；业务 AS 统一调 `TraceNumberDomainService`

---

## 11. 单次会话代码生成门禁（DoD）

- 写读实现编译通过；箱码/调拨/发药场景单测覆盖 BR-TNR-003、006、011、014。

---

## 定稿检查清单（可打勾）

- [x] 章节结构符合金骨架
- [x] `#### 5.1.n` 数量 = 2
- [x] §4.6 QueryModel 与读方法一一对应
- [x] 正文无 YAML/sdd_status/dm_code

---

## 13. Flyway 迁移（目标态）

> **真源路径**：`zxplat-domain/src/main/resources/db/migration/health/`  
> **现行**：贴码单位在药典 + 主档 `trace_unit_ratio` 快照 + 删供应行贴码列，均见 **health-final V107**。

### 13.1 `V107__cus_trace_number_master_flow.sql`

**`cus_trace_number`（主表）**

| 列名 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | `int8` | PK | 雪花 |
| 审计列 | — | 同项目 `BaseAuditedEntity` | `tenant_id`、`created_at`…`version` |
| `trace_no` | `varchar(255)` | NOT NULL | 单码；租户内业务唯一 |
| `item_table` | `varchar(255)` | NOT NULL | 品项表 |
| `item_id` | `int8` | NOT NULL | 品项 id |
| `lot_number` | `varchar(255)` | NOT NULL | 批号 |
| `trace_unit_code` | `varchar(64)` | NOT NULL | 追溯码单位 |
| `remaining_split_quantity` | `numeric(18,4)` | NOT NULL DEFAULT 0 | 拆零剩余 |
| `in_hospital_status_enum` | `varchar(32)` | NOT NULL | `IN_HOSPITAL` / `NOT_IN_HOSPITAL` |

**索引**：

- `uk_cus_trace_number_tenant_trace_no` UNIQUE `(tenant_id, trace_no)` WHERE `is_deleted = false`
- `idx_cus_trace_number_item` `(tenant_id, item_table, item_id, lot_number)`

**`cus_trace_number_flow`（子表）**

| 列名 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | `int8` | PK | 雪花 |
| 审计列 | — | 同项目 | 子表 **只 INSERT**，软删策略与主表一致 |
| `trace_number_id` | `int8` | NOT NULL, FK | → `cus_trace_number.id` |
| `trace_no` | `varchar(255)` | NOT NULL | 冗余 |
| `flow_category_enum` | `varchar(32)` | NOT NULL | `HOSPITAL_IO` / `INTERNAL_TRANSFER` |
| `flow_direction_enum` | `varchar(16)` | NOT NULL | `IN` / `OUT` |
| `location_type_enum` | `varchar(64)` | NULL | 记录用 |
| `location_id` | `int8` | NULL | 记录用 |
| `location_store_id` | `int8` | NULL | 可空 |
| `lot_number` | `varchar(255)` | NOT NULL | 批号 |
| `quantity` | `numeric(18,4)` | NOT NULL | 本条业务数量 |
| `unit_code` | `varchar(64)` | NOT NULL | 本条业务单位 |
| `operation_type` | `varchar(64)` | NOT NULL | 操作类型 code |
| `bus_no` | `varchar(255)` | NULL | 单据号 |

**V109 增量**（`V109__cus_trace_number_record_split_quantity.sql`）：

| 列名 | 类型 | 说明 |
| --- | --- | --- |
| `split_quantity` | `numeric(18,4)` | NOT NULL（新写入）；写侧换算落库，读侧轧差直读 |

**索引**：

- `idx_cus_tn_flow_trace_number_id` `(trace_number_id)`
- `idx_cus_tn_flow_loc_trace` `(location_id, trace_no)` WHERE `location_id IS NOT NULL`
- `idx_cus_tn_flow_bus_no` `(bus_no)` WHERE `bus_no IS NOT NULL`
- `idx_cus_tn_flow_trace_no_created` `(trace_no, created_at)`

### 13.2 ~~供应行 `trace_unit_code`~~（已废止）

- 历史曾规划供应行贴码单位列；**现行已删除**：见同脚本 **`health-final/V107__med_adm_trace_unit.sql`**（药典字段 + 主档快照 + `DROP wkf_supply_request.trace_unit_code`）。

### 13.3 旧表与旧包策略

- **`cus_trace_number` + `cus_trace_number_flow`**：历史单表，**新写路径禁止**；表可保留只读；可选 §16 迁移。
- **`com.zxplat.domain.custom.tracenumber`**：**已删除**（含 `TraceNumberDomainService`、`append*`、`adjust*`）。
- **`share.events.custom.tracenumberrecord`**：**已删除**；由 `share.events.custom.tracenumber` 取代。

---

## 14. 单位换算契约（BR-TNR-008）

**目标**：子表保留业务 `quantity` + `unit_code`；同时落库 `split_quantity`；动主表 `remaining_split_quantity` 时使用已换算拆零量。

**写侧**（`TraceNumberDomainService`）：

- 入参 `RecordLine.unitConversionSource`（`MedicationProductUnitConversionSource`）**须由应用层预加载**。
- 换算：`MedicationProductQuantityToSplitUnitConverter#medicationQuantityToSplitUnit(source, unitCode, quantity)` → `splitQuantity`。倍数：**五类优先**；`trace_unit_ratio` **仅**游离贴码单位。本换算用于流水 `split_quantity` / 主档剩余，**不得**理解成「发退库存优先贴码倍数」。
- **禁止**领域服务注入 `MedicationDefinitionQueryService`（**B.6.4**）。

**读侧**（`TraceNumberQueryService` / `TraceNumberLocationNetsupport）：

- 库位轧差：`net = Σ(IN 为 +split_quantity，OUT 为 -split_quantity)`；**不再**运行时查药品目录换算。

**数值示例**（箱码）：

- 主档 `trace_unit_code=箱`，目录 1箱=10瓶（拆零）
- 进货 IN：`quantity=1, unit_code=箱` → `remaining_split_quantity=10`
- 发药 OUT：`quantity=3, unit_code=瓶` → `remaining=7`，仍在院
- 再发 7 瓶 OUT → `remaining=0` → `NOT_IN_HOSPITAL`

---

## 15. 子表库位聚合算法（BR-TNR-013）

**实现类**：`TraceNumberLocationNetSupport#aggregateNetSplitByTraceNo`

**步骤**：

1. 从 `cus_trace_number_flow` 取满足 `location_id = :loc` 且 `is_deleted=false` 的记录。
2. 每条记录取已落库 `split_quantity`（NULL 跳过）。
3. 按 `trace_no` 聚合：`net = Σ(IN 为 +splitQty，OUT 为 -splitQty)`。**所有** `flow_category_enum` 均参与。
4. `net > 0` → 该码在该库位「仍在库」。

> **与主表区别**：主表 `remaining_split_quantity` **仅**受 `HOSPITAL_IO` 影响；本库 `net` 受**全部**子表流水影响。

---

## 16. 历史数据迁移（`V110__cus_trace_number_record_migrate.sql`，可选）

> **与 V109 区分**：V109 仅增 `split_quantity` 列；本节为旧单表 → 主子表**可选**一次性迁移脚本（未投产环境可跳过）。

**源**：`cus_trace_number` + `cus_trace_number_flow`（`status_enum` IN/OUT，含 `location_id`）

**主表派生**（每 `trace_no` 一行）：

1. 取该码**首条 IN** 的 `item_table/item_id/lot_number`；`trace_unit_code` 缺省取目录 **包装单位**。
2. 按 **§15** 思路，但**仅 `HOSPITAL_IO` 语义**重算 `remaining_split_quantity`（由 `operation_type` 映射 **DM §2.4.1/2.4.2**）。
3. `in_hospital_status_enum`：`remaining > 0` → `IN_HOSPITAL`，否则 `NOT_IN_HOSPITAL`。

**子表派生**：每条旧记录 INSERT 一行 `cus_trace_number_flow`：

| 旧字段 | 新字段 |
| --- | --- |
| `status_enum` | `flow_direction_enum` |
| `operation_type` | 映射 `flow_category_enum`（§2.4 白名单） |
| 其余列 | 同名映射 |

**验收**：迁移后抽样码，主表在院态与旧系统 IN/OUT 净额（全院）一致；子表 loc 聚合与旧 loc 维度一致。

---

## 17. `TraceNumber.applyHospitalIoDelta` 契约

| 入参 | 说明 |
| --- | --- |
| `flowCategoryEnum` | 非 `HOSPITAL_IO` → **no-op** |
| `flowDirectionEnum` | `IN` / `OUT` |
| `splitDelta` | 已换算拆零量（正数） |

| 方向 | 主表变更 |
| --- | --- |
| IN | `remaining += splitDelta`；若 `remaining > 0` → `IN_HOSPITAL` |
| OUT | 断言 `remaining >= splitDelta`（**BR-TNR-011**）；`remaining -= splitDelta`；若 `remaining <= 0` → `NOT_IN_HOSPITAL` |

---

## 18. 领域错误码（`ErrorCodeConstants` 目标态）

| 常量 | 语义 | 对应 EX |
| --- | --- | --- |
| `TRACE_NUMBER_NOT_FOUND` | 主档不存在 | — |
| `TRACE_NUMBER_NOT_IN_HOSPITAL` | 不在院或剩余不足 | EX-TRC-201 |
| `TRACE_NUMBER_INVALID_UNIT` | 单位不可换算 | EX-TRC-202 |
| `TRACE_NUMBER_TRANSFER_NOT_PAIRED` | 调拨未成对 | EX-TRC-206 |
| `TRACE_NUMBER_DUPLICATE_BUS_NO` | 同 bus_no 重复流水 | 幂等 Gate |
| `TRACE_NUMBER_INVALID_ARGUMENT` | 参数非法 | — |
| `TRACE_NUMBER_MEDICATION_ONLY` | 本期仅支持药品追溯 | — |

---

## 12. 与 DM 同步记录

| 日期 | 摘要 |
| --- | --- |
| 2026-08-19 | **§9 / §14**：换算器五类优先；禁止用贴码倍数覆盖同码包装单位做库存扣加；对齐 DM **v2.0.10** / **BR-OPP-017** |
| 2026-07-11 | **CHS 3505/3506**：§5.2 `listFlowRecordsByInventoryItemKey`（`TC-DM-CUS-010-R-010`）；对齐 DM **v2.0.7** |

| 2026-07-06 | **v2.0.5 成码对齐**：`TraceNumberRecord` / `recordTraceRecords`；`split_quantity`（V109）；B.6.4 禁止跨聚合；`tracenumberrecord` 包删除；事件 `TraceNumberRecordRecordedEvent` |
| 2026-07-06 | **v2.0.4 成码配套**：§13 Flyway V107～V109；§14～§17 换算/聚合/迁移/主表 delta；§18 错误码；**领域成码本期仅药品** |
| 2026-07-08 | **v2.0.6**：§6.2.9 发药 OUT 剩余 Gate；BR-TNR-016 每码展开规则 |
| 2026-07-06 | **v2.0.3 / AS-CUS-TRC-002**：§6.2.7/6.2.8 库存行本库码 + 报表分页读方法 |
| 2026-07-06 | **v2.0.2 / AS v1.5**：§2.4 动在院八类 vs 院内流转；BR-TNR-003 细化 |
| 2026-07-06 | **v2.0 对齐 AS v1.3**：主子表契约；`recordTraceRecords`；双轨读法；废弃单表 `append*` / `adjust*` |
| 2026-05-28 | 单表版（已 superseded） |

---

**本 SDD 正文结束。**
