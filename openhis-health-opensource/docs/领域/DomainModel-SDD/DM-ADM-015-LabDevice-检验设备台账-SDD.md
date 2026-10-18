# DM-ADM-015 LabDevice 检验设备台账 · DomainModel-SDD

> **对应 DM**：`docs/领域/DomainModel/DM-ADM-015-LabDevice-检验设备台账.md`（v1.0，本 SDD 假定该 DM 为当期锁定版本）  
> **目标工程**：`zxplat-core/zxplat-domain`  
> **限界上下文**：Administration · 检验/科室设备主数据  
> **聚合**：LabDevice（子实体见 §4）

---

## 输入完整性检查结果

| 最小输入项 | 状态 | 说明 |
| ---------- | ---- | ---- |
| DM 路径 | 满足 | 已绑定 DM-ADM-015 |
| SDD 输出路径 | 满足 | 本文件 |
| 目标模块 | 满足 | `zxplat-domain` |
| 包规则 `com.zxplat.domain.administration.labdevice` | 满足 | `labdevice` 全小写 |
| 固定技术口径（ID Long、时间 LocalDateTime、软删、beforeCommit、分页等） | 满足 | 见 §3、§9 |
| DM 写/读白名单 | 满足 | 模式 B：附录 5 写 + 4 读；§5 全覆盖 |
| DM 业务规则 | 满足 | §6 映射 BR-LAD-001～003 |
| DM 领域事件 | 满足 | DM 未定义事件；§7 声明无事件类 |

**与 DM 显式差异（采纳依据：模式 B，以源码为准）**

| 点 | DM 差异位置 | SDD 采纳 |
| -- | ----------- | -------- |
| 章节体例 | DM 无标准 §5 事件 / §6 服务九章 | 方法白名单取 DM 附录 + `LabDeviceDomainService` / `LabDeviceQueryService` 公有方法 |
| `bus_no` 唯一 | §2 写租户级唯一 | 实现 `existsByBusNo` / `existsByBusNoAndIdNot`（全局 busNo 冲突校验，与 Flyway 索引一致） |
| 领域事件 | 无 | 写路径不发布领域事件 |

---

## 1. 设计目标与边界

### 1.1 设计目标

- 固化 **LabDevice 聚合**（`adm_device`）持久化模型、写/读领域服务契约与 BR 实现策略，支撑合管配置左栏设备选择与设备台账维护。
- **每个写方法对应独立 Command 文件**（`delete`/启停为 `BatchCommand` 形态）；`bus_no` 由 `BusinessSequenceService` 生成，前缀 **LAD**。
- **单次成稿目标**：正文无占位措辞；契约与现有 `zxplat-domain` 实现对齐（模式 B）。

### 1.2 边界

- **在内**：检验/合管设备台账 CRUD、批量启停、检索码维护、分页与关键字检索。
- **在外**：耗材定义 `adm_device_definition`、合管绑定 `cus_specimen_tube_merge`、供应商主数据维护。
- **跨域仅端口声明**：`manufacturerId` 引用 `cus_supplier`；`orgId`/`locationId` 引用管理域主数据；本聚合不注入外域 Repository。

---

## 2. 工程落点与包结构

### 2.1 Maven 模块

- 模块：`zxplat-domain`（实体/命令/仓储/领域服务/查询）；共享枚举 `PublicationStatus`、错误码与序列表常量位于 `zxplat-share`（`com.zxplat.domain.share.*`）。

### 2.2 包结构（`com.zxplat.domain.administration.labdevice`）

| 相对包 | 内容 |
| ------ | ---- |
| `entity` | `LabDevice` |
| `valueobject` | 无 |
| `enums`（共享） | `com.zxplat.domain.share.enums.PublicationStatus` |
| `command` | `CreateLabDeviceCommand`、`UpdateLabDeviceCommand`、`DeleteLabDeviceCommand`、`DeactivateLabDevicesCommand`、`ActivateLabDevicesCommand` |
| `repository` | `LabDeviceRepository` |
| `service` | `LabDeviceDomainService`、`LabDeviceQueryService` |
| `queries` | `PageLabDeviceQuery`、`SearchLabDeviceQuery` |
| `queries.model` | `LabDevicePageItemQueryModel`、`LabDeviceDetailQueryModel`、`LabDeviceSearchItemQueryModel` |
| `share.events` | 本聚合无领域事件类 |

**存储口径（DM 优先）**：`status_enum` → `String`（`PublicationStatus` code）；`category_code` → `String`（术语 `adm_device_category_code`）；日期列 `manufactureDate`/`expirationDate` → `LocalDate`。

---

## 3. 类型 / 字段命名 / 注解规则

| 规则 | 说明 |
| ---- | ---- |
| 主键 / 外键 | `Long`；`id`、`orgId`、`locationId`、`manufacturerId` |
| 时间 | 业务日期 `*Date` → `LocalDate`；审计字段 → `LocalDateTime`（基类） |
| `*_enum` | `String`（存枚举 code） |
| `*_code` | `String`（字典/术语 code） |
| `_flag` | 本聚合无 |
| 软删 | 基类 `isDeleted`；读默认 `isDeleted=false` |
| 实体 | `@Entity` + `@Table(name = "adm_device")` |
| 仓储 | 仅 `JpaRepository` 接口 |
| 分页 | `Pageable`；无关键字时默认 `updatedAt DESC, id DESC` |
| 事务 | 领域服务不声明 `@Transactional` |
| 事件 | 本聚合写路径无 `DomainEventPublisher` 调用 |

---

## 4. 实体 / 值对象 / 枚举 / 仓储 / 查询契约

### 4.1 表与实体映射

| 逻辑表 | 实体类 | 说明 |
| ------ | ------ | ---- |
| `adm_device` | `LabDevice` | 检验/合管设备台账单表聚合 |

### 4.2 聚合根 `LabDevice`（字段级契约）

| 字段名（Java） | 类型 | 必填 | 说明 / 约束 |
| -------------- | ---- | ---- | ----------- |
| `id` | `Long` | 系统 | 主键 |
| `busNo` | `String` | 是 | 业务编码 LAD 前缀；租户内唯一（未删） |
| `name` | `String` | 是 | 设备名称 |
| `pyStr` | `String` | 否 | 拼音检索码 |
| `wbStr` | `String` | 否 | 五笔检索码 |
| `orgId` | `Long` | 否 | 归属科室 |
| `locationId` | `Long` | 否 | 安装位置 |
| `statusEnum` | `String` | 是 | `ACTIVE` / `INACTIVE` |
| `categoryCode` | `String` | 否 | 设备分类术语 |
| `modelNumber` | `String` | 否 | 型号 |
| `serialNumber` | `String` | 否 | 序列号 |
| `manufacturerId` | `Long` | 否 | 生产厂家 |
| `manufactureDate` | `LocalDate` | 否 | 生产日期 |
| `expirationDate` | `LocalDate` | 否 | 失效/报废日期 |
| `description` | `String` | 否 | 备注 |
| `isDeleted` | `Boolean` | 系统 | 逻辑删除 |

**不变式方法**：`assertBusNoNotBlank`、`assertNameNotBlank`、`refreshSearchCodeFromName`、`initAsActive`、`assertActive`、`assertInactive`、`activate`、`deactivate`、`markDeleted`。

### 4.3 子实体

无。本聚合为单表。

### 4.5 仓储接口

- `LabDeviceRepository extends JpaRepository<LabDevice, Long>`
- `existsByBusNo(String busNo)`、`existsByBusNoAndIdNot(String busNo, Long id)`
- `findByIdIn(Collection<Long> ids)`

### 4.6 查询模型（`queries.model`）

#### 4.6.1 `LabDevicePageItemQueryModel`（对应 `pageLabDevice` / `listActiveDevices`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `id` | `Long` | `adm_device.id` | 主键 |
| `busNo` | `String` | `adm_device.bus_no` | 业务编码 |
| `name` | `String` | `adm_device.name` | 名称 |
| `statusEnum` | `String` | `adm_device.status_enum` | 发布状态 |
| `categoryCode` | `String` | `adm_device.category_code` | 分类 |
| `pyStr` | `String` | `adm_device.py_str` | 拼音 |
| `wbStr` | `String` | `adm_device.wb_str` | 五笔 |
| `orgId` | `Long` | `adm_device.org_id` | 科室 |
| `locationId` | `Long` | `adm_device.location_id` | 位置 |
| `modelNumber` | `String` | `adm_device.model_number` | 型号 |
| `serialNumber` | `String` | `adm_device.serial_number` | 序列号 |
| `manufacturerId` | `Long` | `adm_device.manufacturer_id` | 厂家 |
| `manufactureDate` | `LocalDate` | `adm_device.manufacture_date` | 生产日期 |
| `expirationDate` | `LocalDate` | `adm_device.expiration_date` | 失效日期 |
| `description` | `String` | `adm_device.description` | 备注 |
| `updatedAt` | `LocalDateTime` | 审计列 | 分页排序 |

#### 4.6.2 `LabDeviceDetailQueryModel`（对应 `getLabDevice`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `id` | `Long` | `adm_device.id` | 主键 |
| `busNo` | `String` | `adm_device.bus_no` | 业务编码 |
| `name` | `String` | `adm_device.name` | 名称 |
| `statusEnum` | `String` | `adm_device.status_enum` | 状态 |
| `categoryCode` | `String` | `adm_device.category_code` | 分类 |
| `pyStr` | `String` | `adm_device.py_str` | 拼音 |
| `wbStr` | `String` | `adm_device.wb_str` | 五笔 |
| `orgId` | `Long` | `adm_device.org_id` | 科室 |
| `locationId` | `Long` | `adm_device.location_id` | 位置 |
| `modelNumber` | `String` | `adm_device.model_number` | 型号 |
| `serialNumber` | `String` | `adm_device.serial_number` | 序列号 |
| `manufacturerId` | `Long` | `adm_device.manufacturer_id` | 厂家 |
| `manufactureDate` | `LocalDate` | `adm_device.manufacture_date` | 生产日期 |
| `expirationDate` | `LocalDate` | `adm_device.expiration_date` | 失效日期 |
| `description` | `String` | `adm_device.description` | 备注 |

#### 4.6.3 `LabDeviceSearchItemQueryModel`（对应 `searchByKeyWord`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `id` | `Long` | `adm_device.id` | 主键 |
| `busNo` | `String` | `adm_device.bus_no` | 业务编码 |
| `name` | `String` | `adm_device.name` | 名称 |
| `statusEnum` | `String` | `adm_device.status_enum` | 状态 |
| `categoryCode` | `String` | `adm_device.category_code` | 分类 |
| `pyStr` | `String` | `adm_device.py_str` | 拼音 |
| `wbStr` | `String` | `adm_device.wb_str` | 五笔 |

---

## 5. 写侧与读侧白名单（命令字段全集）

### 5.1 写侧 — `LabDeviceDomainService`

#### 5.1.1 `createLabDevice(CreateLabDeviceCommand command)` → `Long`

**命令文件**：`CreateLabDeviceCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `name` | `String` | 设备名称 |
| `pyStr` | `String` | 拼音；可空则按名称生成 |
| `wbStr` | `String` | 五笔；可空则按名称生成 |
| `orgId` | `Long` | 归属科室 |
| `locationId` | `Long` | 安装位置 |
| `categoryCode` | `String` | 设备分类 |
| `modelNumber` | `String` | 型号 |
| `serialNumber` | `String` | 序列号 |
| `manufacturerId` | `Long` | 生产厂家 |
| `manufactureDate` | `LocalDate` | 生产日期 |
| `expirationDate` | `LocalDate` | 失效日期 |
| `description` | `String` | 备注 |

> `busNo` 不由命令传入；服务内 `BusinessSequenceService.getSeq(LAB_DEVICE, …)` 生成。

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `initAsActive` + `assertBusNoNotBlank` + `assertNameNotBlank` + `refreshSearchCodeFromName` |
| `statusTransitionMethod` | `initAsActive` |
| `repositoryChecks` | `existsByBusNo` 须 false |
| `eventPublishAnchor` | N/A |
| `unitTestCaseId` | `TC-DM-ADM-015-W-001` |

**方法契约摘要**：前置名称非空；持久化 INSERT 默认 ACTIVE；冲突 busNo 抛 `LAB_DEVICE_BUS_NO_CONFLICT`；无领域事件。**注释锚点**：生成 busNo → 装配可选字段 → 不变式 → 唯一校验 → save。

---

#### 5.1.2 `updateLabDevice(UpdateLabDeviceCommand command)` → `void`

**命令文件**：`UpdateLabDeviceCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `id` | `Long` | 主键 |
| `name` | `String` | 名称；变更时清空 py/wb 后重算 |
| `pyStr` | `String` | 拼音 |
| `wbStr` | `String` | 五笔 |
| `orgId` | `Long` | 科室 |
| `locationId` | `Long` | 位置 |
| `categoryCode` | `String` | 分类 |
| `modelNumber` | `String` | 型号 |
| `serialNumber` | `String` | 序列号 |
| `manufacturerId` | `Long` | 厂家 |
| `manufactureDate` | `LocalDate` | 生产日期 |
| `expirationDate` | `LocalDate` | 失效日期 |
| `description` | `String` | 备注 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `assertBusNoNotBlank` + `assertNameNotBlank` + `refreshSearchCodeFromName` |
| `statusTransitionMethod` | N/A |
| `repositoryChecks` | `loadLabDevice(id)`；`existsByBusNoAndIdNot` |
| `eventPublishAnchor` | N/A |
| `unitTestCaseId` | `TC-DM-ADM-015-W-002` |

**方法契约摘要**：前置 id 有效且未删；名称变更重算检索码；查无记录抛 `LAB_DEVICE_NOT_FOUND`。**注释锚点**：load → 增量字段 → 名称变更分支 → 不变式 → save。

---

#### 5.1.3 `deleteLabDevice(DeleteLabDeviceCommand command)` → `void`

**命令文件**：`DeleteLabDeviceCommand.java`；子项 `DeleteLabDeviceItemCommand`（字段 `id`）

**字段全集（命令头）**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `operatorId` | `Long` | 操作人 |
| `items` | `List<DeleteLabDeviceItemCommand>` | 批量 id |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `markDeleted(operatorId)` |
| `statusTransitionMethod` | N/A |
| `repositoryChecks` | `findByIdIn` 须与请求 id 数一致且均未删 |
| `eventPublishAnchor` | N/A |
| `unitTestCaseId` | `TC-DM-ADM-015-W-003` |

**方法契约摘要**：批量逻辑删除；任一 id 无效或已删抛 `LAB_DEVICE_NOT_FOUND`。**注释锚点**：解析 ids → 加载 → markDeleted → saveAll。

---

#### 5.1.4 `deactivateLabDevices(DeactivateLabDevicesCommand command)` → `void`

**命令文件**：`DeactivateLabDevicesCommand.java`；子项含 `id`

**字段全集**：批量 `DeactivateLabDeviceItemCommand.id` 列表（继承 `BatchCommand`）。

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `assertActive` + `deactivate` |
| `statusTransitionMethod` | `deactivate`（ACTIVE→INACTIVE） |
| `repositoryChecks` | `findByIdIn` 整批命中 |
| `eventPublishAnchor` | N/A |
| `unitTestCaseId` | `TC-DM-ADM-015-W-004` |

**方法契约摘要**：仅 ACTIVE 可停用；状态非法抛 `LAB_DEVICE_INVALID_STATUS`。**注释锚点**：加载 → assertActive → deactivate → saveAll。

---

#### 5.1.5 `activateLabDevices(ActivateLabDevicesCommand command)` → `void`

**命令文件**：`ActivateLabDevicesCommand.java`；子项含 `id`

**字段全集**：批量 `ActivateLabDeviceItemCommand.id` 列表。

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `assertInactive` + `activate` |
| `statusTransitionMethod` | `activate`（INACTIVE→ACTIVE） |
| `repositoryChecks` | `findByIdIn` 整批命中 |
| `eventPublishAnchor` | N/A |
| `unitTestCaseId` | `TC-DM-ADM-015-W-005` |

**方法契约摘要**：仅 INACTIVE 可启用。**注释锚点**：同 deactivate 对称。

### 5.2 读侧 — `LabDeviceQueryService`

所有查询 **默认** `isDeleted=false`；分页在数据库侧过滤与统计 total。

| 方法 | 入参 | 出参（类名，字段见 §4.6） | 关键实现 | `unitTestCaseId` |
| ---- | ---- | ------------------------- | -------- | ---------------- |
| `pageLabDevice` | `PageLabDeviceQuery` + `Pageable` | `Page<LabDevicePageItemQueryModel>` | keyword/status/category/orgId；关键字精确优先排序 | `TC-DM-ADM-015-R-001` |
| `listActiveDevices` | `String keyword`, `Integer limit` | `List<LabDevicePageItemQueryModel>` | 委托 `searchByKeyWord`，固定 ACTIVE | `TC-DM-ADM-015-R-002` |
| `getLabDevice` | `Long id` | `LabDeviceDetailQueryModel` | 不存在或已删返回 null | `TC-DM-ADM-015-R-003` |
| `searchByKeyWord` | `SearchLabDeviceQuery` | `List<LabDeviceSearchItemQueryModel>` | 默认 ACTIVE；limit 默认 50 | `TC-DM-ADM-015-R-004` |

**`PageLabDeviceQuery` 字段**：`keyword`、`statusEnum`、`categoryCode`、`orgId`。

**读方法注释锚点（统一）**：构建 Specification（软删）→ 单次仓储分页/列表 → 投影 QueryModel（无写库）。

---

## 6. 不变式映射（DM §7）

| BR 编号 | 实现策略摘要 |
| ------- | ------------ |
| BR-LAD-001 | `assertBusNoNotConflict`：`existsByBusNo` / `existsByBusNoAndIdNot` |
| BR-LAD-002 | `assertNameNotBlank`；`refreshSearchCodeFromName`（SearchCodeUtil） |
| BR-LAD-003 | 启停仅改 `status_enum`：`activate`/`deactivate`；删除走 `markDeleted` 非状态混用 |

---

## 7. 事件契约与触发规则（DM §5）

| 事件类名 | 事件类包路径 | 最小载荷（字段） | 触发方法 |
| -------- | ------------ | ---------------- | -------- |
| （无） | — | — | 本聚合写路径不发布领域事件 |

**发布时机**：不适用。

---

## 8. 删除与可见性契约

- 删除为逻辑删除（`markDeleted`），禁止物理 DELETE（除非框架软删实现内部调用）。
- 读侧默认过滤 `isDeleted=false`。
- 合管配置等跨聚合读设备存在性时使用 `getLabDevice`，null 视为不存在。

---

## 9. 框架优先适配规则

- 审计/租户/软删继承 `BaseAuditedEntity`。
- 遵循项目级成码门禁（Repository 禁 `@Query`、禁三元等）。
- 批量命令继承 `BatchCommand` + 明细项 `id` 模式。

---

## 10. 生成与验收清单

- [ ] 实体与 Flyway `adm_device` 一致
- [ ] 五个写方法 + 五个 Command（含 Batch 子项）
- [ ] 四个读方法 + Query 入参与三个 QueryModel
- [ ] BR-LAD-001～003 可测用例绑定
- [ ] `zxplat-domain` 编译通过

---

## 11. 单次会话代码生成门禁（DoD）

### 11.1 前置输入

- 锁定 DM-ADM-015 与本 SDD。

### 11.2 DoD

- 方法/字段/软删与本文一致；模式 B 已实现代码为对照基准。

### 11.3 失败处理

- DOC-CONFLICT 以 DM + 本 SDD 差异表为准回写文档或改码。

---

## 定稿检查清单（可打勾）

- [x] 章节骨架与金母版同构
- [x] 写方法 5 = DM 附录
- [x] 读方法 4 = DM 附录
- [x] §4.2 / §4.6 字段表完整
- [x] 无「待确认/后续补充」措辞

---

## 定稿评分（百分制）

| 维度 | 分值 | 自评 |
| ---- | ---- | ---- |
| 输入完整性 | 20 | 19 |
| 方法白名单对齐 | 25 | 25 |
| 不变式映射 | 20 | 20 |
| 事件契约 | 15 | 15 |
| 软删与框架一致 | 10 | 10 |
| 去重与无歧义 | 10 | 10 |
| **总分** | **100** | **99，可进入代码会话** |

---

**本 SDD 正文结束。**
