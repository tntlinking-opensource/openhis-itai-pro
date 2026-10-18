# DM-DOC-001 InventoryItemSnapshot 库存明细快照

> **限界上下文**：Document（DOC）· 库存明细历史镜像  
> **代码模块**：`zxplat/zxplat-*`  
> **聚合类型**：单表实体（append-only 静表）  
> **版本**：v1.0 · **日期**：2026-06-28  
> **Flyway 真源**：`zxplat-domain/.../db/migration/health/`（表 **`doc_inventory_item_snapshot`**）  
> **对齐 openhis**：`doc_inventory_item_static`（**DocInventoryItemStatic**）语义；ZXPlat 列集按 **AS-INV-WHS-004 §1.3 / SDD §3.11.2** 四价 + code 事实模型收简

---

## 1. 概述

### 1.1 业务定义

**库存明细快照（InventoryItemSnapshot）** 记录某自然日定时任务执行后，租户全量现库 **InventoryItem** 批号级明细的**不可变冻结镜像**，供「备份库存」分页、汇总、导出与备份单号下拉使用。

- **写入**：仅 **AS-INV-WHS-004 IF-10** 批量 INSERT；写入前复用现库 IF-02 同等装配链路（**BR-INV-DTL-019**），落库列见 **§3.1**。
- **读取**：**IF-04 / 05 / 07 / 11** 只读本表；**禁止**联结 **ChargeItemDefinition** 重算四价（**AS BR-INV-DTL-018**）。
- **与 WKF InventoryItem 边界**：本表**非** InventoryItem 聚合子实体；不参与 FIFO、停供/恢复、数量变更。

本文档编码 **`DM-DOC-001`**；**无 FHIR 标准资源一一映射**。

### 1.2 核心职责

| 职责 | 说明 |
| --- | --- |
| 历史镜像 | 按 **`bus_no`** 批次冻结现库明细事实列 |
| 备份读真源 | 备份列表/汇总/导出 SELECT 本表 |
| 日级幂等支撑 | 按自然日判定是否已成功落库（**AS BR-INV-DTL-020**） |
| 审计追溯 | **`inventory_item_id`** 保留写入时刻源库存项引用 |

### 1.3 适用范围

| 场景 | 说明 |
| --- | --- |
| 定时快照 | Cron 触发全量现库 → 批量写入 |
| 备份单号下拉 | 租户历史 **`bus_no`** 降序去重 |
| 备份库存查询 | 带 **`bus_no`** 谓词的分页/汇总/导出 |

### 1.4 与关键上下游及交换边界的关系

| 维度 | 上游 | 本静表 | 下游 |
| --- | --- | --- | --- |
| 职责 | IF-02 现库装配 | 冻结落库 | 备份读 IF-04～07、IF-11 |
| 数据 | **InventoryItem** + 定义 + 定价（仅写路径） | 事实列 + code | **InventoryDetailBackupListRowDto** |
| 引用 | **DM-WKF-008**（源行）、**DM-ADM-010**（写路径定价） | 无 FK | **AS-INV-WHS-004** |

> **边界**：备份读**不**回读现库 **InventoryItem** 补列；**不**持久化货位、**不**持久化 `*_text` 字典翻译列、**不**持久化 `purchase_unit_ratio` 与重复展示单价字段。

---

## 2. 聚合根与持久化形态

### 2.1 持久化形态

**单表 append-only**；业务语义**不可 UPDATE**（软删仅框架层）。

| 物理表 | 说明 |
| --- | --- |
| `doc_inventory_item_snapshot` | 聚合根 / 静表行 |

**索引与约束**：

| 名称 | 类型 | 列 | 说明 |
| --- | --- | --- | --- |
| `doc_inventory_item_snapshot_pkey` | PRIMARY KEY | `id` | |
| `uk_doc_inv_snap_tenant_bus_item` | UNIQUE | `(tenant_id, bus_no, inventory_item_id)` | 同批次同一源库存项仅一行 |
| `idx_doc_inv_snap_tenant_bus` | INDEX | `(tenant_id, bus_no)` | 备份分页/汇总/导出 |
| `idx_doc_inv_snap_tenant_bus_py` | INDEX | `(tenant_id, bus_no, py_str)` | 备份列表 **`py_str` ASC** 排序 |

**批次单号 `bus_no`**：一次快照批次对外单号，前缀 **`ABU`** + 自然日 **`yyyyMMdd`** + 日内 **2 位**序号（例 **`ABU2026062801`**）；同批次内所有明细行共享同一 **`bus_no`**。接口语义名 **`backupBusNo`** ↔ 库列 **`bus_no`**。

### 2.2 聚合根标识

| 属性 | 类型 | 说明 |
| --- | --- | --- |
| 快照行标识 | `InventoryItemSnapshotId` | 主键 **`id`**（`Long`，Snowflake） |
| 源库存项引用 | — | **`inventory_item_id`**（写入时刻 **`wkf_inventory_item.id`**） |

---

## 3. 实体

### 3.1 DocInventoryItemSnapshot（聚合根）

> 金额精度与 **`ChargeItemAmountScaleConstants`** 一致（`numeric(18,4)`）；数量 `numeric(18,2)`。

**A. 批次与审计**

| 属性 | 字段名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| 快照行标识 | `id` | `Long` | 是 | 主键 |
| 租户 | `tenant_id` | `Long` | 是 | |
| 批次单号 | `bus_no` | `String(32)` | 是 | **ABU**+日+序号 |
| 快照时刻 | `snapshot_time` | `timestamptz` | 是 | 本批次快照完成时刻（行内冗余） |
| 源库存项 | `inventory_item_id` | `Long` | 是 | 写入时刻源 **InventoryItem** 主键 |
| 创建时间 | `created_at` | `timestamptz` | 是 | 落库时间 |
| 创建人 | `created_by` | `Long` | 否 | 任务操作者/系统账号 |
| 软删 | `is_deleted` | `Boolean` | 是 | 默认 false；快照行通常不删 |

**B. 物品引用 ID**

| 属性 | 字段名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| 物品表 | `item_table` | `String(64)` | 是 | 如 `med_medication_definition` |
| 物品主键 | `item_id` | `Long` | 是 | |
| 收费项定义 | `charge_item_definition_id` | `Long` | 否 | 写入时产品读模型 |
| 库房 | `warehouse_id` | `Long` | 是 | **不含货位** |
| 供应商 | `supplier_id` | `Long` | 是 | |

**C. 产品主数据文本（冻结）**

| 属性 | 字段名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| 项目编码 | `product_bus_no` | `String(64)` | 否 | |
| 项目名称 | `name` | `String(255)` | 是 | |
| 商品名称 | `merchandise_name` | `String(255)` | 否 | |
| 规格 | `total_volume` | `String(255)` | 否 | |
| 厂家/产地 | `manufacturer` | `String(255)` | 否 | |
| 批准文号 | `approval_number` | `String(128)` | 否 | |
| 医保码 | `yb_no` | `String(64)` | 否 | |
| 追溯码 | `trace_no` | `String(50000)` | 否 | |
| 拼音 | `py_str` | `String(255)` | 否 | 备份关键字/排序 |
| 五笔 | `wb_str` | `String(255)` | 否 | 备份关键字 |

**D. 批号、数量与包装单位**

| 属性 | 字段名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| 生产批号 | `lot_number` | `String(255)` | 是 | |
| 账面数量 | `quantity` | `numeric(18,2)` | 是 | **拆零最小单位**数量（冻结原值） |
| 拆零↔包装倍数 | `package_unit_ratio` | `numeric(18,4)` | 否 | 1 包装 = ratio 拆零 |
| 包装单位码 | `package_unit_code` | `String(64)` | 否 | 字典 code |

**E. 四价（包装单位口径，冻结）**

| 属性 | 字段名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| 批号进价 | `lot_number_cost_amount` | `numeric(18,4)` | 否 | 包装整包进价 |
| 批号售价 | `lot_number_price_amount` | `numeric(18,4)` | 否 | 包装整包售价 |
| 进价行总额 | `line_cost_total` | `numeric(18,4)` | 否 | 写入时刻按 **BR-INV-DTL-007** 冻结 |
| 售价行总额 | `line_price_total` | `numeric(18,4)` | 否 | 同上 |

**F. 效期**

| 属性 | 字段名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| 生产日期 | `production_date` | `timestamptz` | 否 | 写入时刻解析结果 |
| 效期 | `expiration_date` | `timestamptz` | 否 | |
| 剩余过期天数 | `remaining_days` | `Integer` | 否 | 写入时刻冻结（**BR-INV-DTL-003** 备份筛选用） |

**G. 状态与分类 code（仅 code，不含翻译列）**

| 属性 | 字段名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| 供应状态 | `inventory_status_enum` | `String(32)` | 是 | **`PublicationStatus` code** |
| 医保等级 | `chrgitm_lv` | `Integer` | 否 | |
| 药品分类 | `medication_category_code` | `String(64)` | 否 | |
| 耗材分类 | `device_category_code` | `String(64)` | 否 | |
| 剂型 | `dose_form_code` | `String(64)` | 否 | 字典 code |

**显式不持久化**：`location_store_id`、货位名、`purchase_unit_ratio`、`package_unit_ratio_text`、全部 `*_text` / `*Label` / `*StockText` 列、重复展示单价字段（`display_cost_unit_price` 等）。

---

## 4. 值对象与枚举

### 4.1 引用枚举与字典（存库形态）

| 字段 | 存库类型 | 说明 |
| --- | --- | --- |
| `inventory_status_enum` | `String` | 枚举 **code**（**`PublicationStatus`**） |
| `medication_category_code` / `device_category_code` / `dose_form_code` / `package_unit_code` | `String` | 字典 **code** |
| `chrgitm_lv` | `Integer` | 医保等级码 |

备份读侧 `*Label` 由应用层按 code **只读翻译**，**不得**重算行总额。

---

## 5. 领域事件

首期 **不发** 域事件；静表行即事实记录（**AS §6** 语义级「库存明细已快照」由任务日志表达）。

---

## 6. 领域服务与应用编排

### 6.1 写侧 — `DocInventoryItemSnapshotDomainService`

| 方法 | 调用方 | 说明 |
| --- | --- | --- |
| `batchAppendSnapshots(BatchAppendDocInventoryItemSnapshotCommand cmd)` | **IF-10**（`InventoryWarehouseInventoryDetailAppService`） | 批量 INSERT；**append-only** |

> **`bus_no` 生成**与 **日级 skip** 判定在应用层 Pre 步骤（**SequenceService**、`existsSuccessfulBackupForDate`）；**现库 0 行时不分配 `bus_no`、不 INSERT**（AS **R-012**）；DomainService **不**负责序号分配。

### 6.2 读侧 — `DocInventoryItemSnapshotQueryService`

| 方法 | 说明 | 用于 |
| --- | --- | --- |
| `existsSuccessfulBackupForDate(tenantId, localDate)` | 自然日是否已有 **`ABU`+yyyyMMdd** 前缀之成功批次 | IF-10 Pre（**BR-INV-DTL-020**） |
| `findDistinctBusNoByTenant(tenantId)` | **`bus_no` DESC** 去重列表 | IF-11 |
| `pageSnapshotRows(DocInventoryItemSnapshotPageQuery query, Pageable pageable)` | 备份明细分页（静表谓词；关键字**不含批号**） | IF-04 |
| `listSnapshotRowsForSum(query, maxRows)` | 备份汇总候选行（≤3 万） | IF-05 |
| `listSnapshotRowsForExport(query, maxRows)` | 备份导出行 | IF-07 |

### 6.3 写入映射（应用层职责摘要）

IF-10 在现库 **`InventoryDetailListRowDto`** 完成 **BR-INV-DTL-005/007** 装配后，映射 **§3.1** 事实列落库；**禁止**落库 `*Label` / `packageStockText` / `splitStockText`。典型映射：`lotCostPrice` → `lot_number_cost_amount`，`lotSalePrice` → `lot_number_price_amount`，`lineCostTotal` → `line_cost_total`，`linePriceTotal` → `line_price_total`。

### 6.4 读侧 DTO 语义（应用层）

**`InventoryDetailBackupListRowDto`** 与 **`InventoryDetailListRowDto`** 对客字段**同构**；**`id`** = **`inventory_item_id`**（与现库列表语义一致）；快照行主键 **`id`** 仅内部/审计使用。

备份读可只读联结 **Location** / **Supplier** 补 **库房名/供应商名**；**禁止**联结 **ChargeItemDefinition** 改价。

---

## 7. 不变式

| 编号 | 不变式 | 说明 |
| --- | --- | --- |
| **BR-DOC-SNAP-001** | `bus_no` 非空且符合 **ABU**+`yyyyMMdd`+2 位序号 | 批次标识 |
| **BR-DOC-SNAP-002** | `(tenant_id, bus_no, inventory_item_id)` 唯一 | 同批次不重复源行 |
| **BR-DOC-SNAP-003** | append-only；无业务 UPDATE | 镜像不可变 |
| **BR-DOC-SNAP-004** | 落库列 = **§3.1** 事实全集 | 对齐 **AS BR-INV-DTL-017** |
| **BR-DOC-SNAP-005** | 字典/枚举列只存 **code** | 对齐 **AS BR-INV-DTL-011** |
| **BR-DOC-SNAP-006** | **不含货位**相关列 | |
| **BR-DOC-SNAP-007** | 同租户同自然日至多一次成功快照 | **AS BR-INV-DTL-020**；由 Pre + UNIQUE 批次策略保证 |
| **BR-DOC-SNAP-008** | 备份读四价取自本表冻结列 | **AS BR-INV-DTL-018** |

---

## 8. 上下文映射

### 8.1 上游

| 来源 | 关系 |
| --- | --- |
| **DM-WKF-008 InventoryItem** | 写路径读源行；`inventory_item_id` 冗余 |
| **MedicationDefinition / DeviceDefinition** | 写路径冻结产品文本 |
| **ChargeItemDefinition** | **仅写路径**批号定价 |
| **AS-INV-WHS-004 IF-10** | 唯一写入口 |

### 8.2 下游

| 消费方 | 方式 |
| --- | --- |
| 库存明细页备份视图 | IF-04 / 05 / 07 |
| 备份单号下拉 | IF-11 |
| 合规 / 报表 | 导出（规划） |

### 8.3 引用规则

| 模型 | 关系 |
| --- | --- |
| **DM-WKF-008** | 逻辑引用源库存项；**无 FK** |
| **DM-ADM-003 Location** | 备份读可选补库房名 |
| **DM-CUS-007 Supplier** | 备份读可选补供应商名 |

---

## 9. 补充

### 9.1 与 openhis `doc_inventory_item_static` 差异

| 点 | openhis | ZXPlat **DM-DOC-001** |
| --- | --- | --- |
| 表名 | `doc_inventory_item_static` | `doc_inventory_item_snapshot` |
| 定价 | 多种展示列 | **四价** + code 事实模型 |
| 翻译列 | 部分 `*_text` | **不持久化**；读侧按需翻译 |
| 货位 | 视实现 | **明确不含** |

### 9.2 变更历史

| 版本 | 日期 | 摘要 |
| --- | --- | --- |
| **v1.0** | **2026-06-28** | 初版：DOC 域 **`doc_inventory_item_snapshot`**、四价事实列、QueryService/DomainService 白名单、与 **AS-INV-WHS-004** 对齐 |

---

**本 DM 正文结束。**

<!-- MODE-B-METHOD-INVENTORY 2026-09-23 -->

## 附录 · 模式 B 已实现方法清单（2026-09-23）

> 自 zxplat-domain 对应 *DomainService / *QueryService 抽取 public 方法名，用于关闭方法白名单 DOC-DRIFT。细规仍以正文 §6 与源码为准；本表不替代九章成稿。

### DomainService：`DocInventoryItemSnapshotDomainService`

| 方法 |
|------|
| `batchAppendSnapshots` |

### QueryService：`DocInventoryItemSnapshotQueryService`

| 方法 |
|------|
| `existsSuccessfulBackupForDate` |
| `findDistinctBusNoByTenant` |
| `findDistinctBusNoOptionsByTenant` |
| `findDistinctItemTablesByTenantBusNoAndWarehouse` |
| `pageSnapshotRows` |
| `listSnapshotRowsForSum` |
| `listSnapshotRowsForExport` |

