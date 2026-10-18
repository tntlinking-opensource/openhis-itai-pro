# DM-PACS-005 PacsDevice 设备台账 · DomainModel-SDD

> **对应 DM**：`docs/领域/DomainModel/DM-PACS-005-PacsDevice-设备台账.md`（v1.0，本 SDD 假定该 DM 为当期锁定版本）  
> **目标工程**：`zxplat-plugin/zxplat-module-pacs/module-pacs-domain`  
> **限界上下文**：检查域（PACS/RIS）· 设备台账配置  
> **聚合**：PacsDevice（子实体见 §4）

---

## 输入完整性检查结果

| 最小输入项 | 状态 | 说明 |
| ---------- | ---- | ---- |
| DM 路径 | 满足 | 已绑定 DM-PACS-005 |
| SDD 输出路径 | 满足 | 本文件 |
| 目标模块 | 满足 | `module-pacs-domain` |
| 包规则 `com.zxplat.plugin.pacs.domain.device` | 满足 | `device` 全小写 |
| 固定技术口径（ID Long、时间 LocalDateTime、软删、beforeCommit、分页等） | 满足 | 见 §9 |
| DM 写/读白名单（§5 + 附录） | 满足 | §5 覆盖 save/disable/delete/getById + search |
| DM §4 业务规则 | 满足 | §6 映射 BR-DEV-01～04 |
| DM 领域事件 | 满足 | 无（配置型聚合） |

**与 DM 显式差异（采纳依据：附录模式 B 与现网实现）**

| 点 | DM 差异位置 | SDD 采纳 |
| -- | ----------- | -------- |
| search 入参 | DM §5 含 keyword | 与 `PacsDeviceQueryService#search` 一致：keyword 模糊匹配 name/deviceCode |
| disable/delete | DM 写 `(id, operatorId)` | 独立 Command 文件（与成码门禁一致） |

---

## 1. 设计目标与边界

### 1.1 设计目标

- 在单一文档内固化 **PacsDevice 聚合** 的持久化模型、领域服务契约、查询契约与不变式实现策略，使下一会话可 **一次性** 生成/核对领域层并通过编译。
- 单表配置聚合；**deviceCode 租户内唯一**（BR-DEV-02）在 `save` 内校验。
- **单次成稿目标**：正文不出现「后续补充」「待确认」类措辞。

### 1.2 边界

- **在内**：`pacs_device` 编码、名称、模态、AE Title、机房名、启停、备注、软删与分页列表。
- **在外**：MWL/执行侧选用设备（只读引用 deviceId）；`pacs_modality_capability`（CFG-001）；遗留 EAV 参数（国基 P1 不做，BR-DEV-04）。
- **跨域仅端口声明**：登记/执行 AppService 下拉引用本聚合 id，不强制 DB 外键。

---

## 2. 工程落点与包结构

### 2.1 Maven 模块

- 模块：`zxplat-plugin/zxplat-module-pacs/module-pacs-domain`。

### 2.2 包结构（`com.zxplat.plugin.pacs.domain.device`）

| 相对包 | 内容 |
| ------ | ---- |
| `entity` | `PacsDevice` |
| `valueobject` | 无 |
| `enums`（共享） | 无；`enabledFlag` 为 `Boolean` |
| `command` | 每个写方法一个 Java 文件 |
| `repository` | `PacsDeviceRepository` |
| `service` | `PacsDeviceDomainService` |
| `queries` | `PacsDeviceQueryService` |
| `queries.model` | `PacsDeviceQueryModel` |
| `share.events` | 无 |

**存储口径（DM 优先）**：`modalityCode` / `deviceCode` → `String`；`enabledFlag` → `Boolean`。

---

## 3. 类型 / 字段命名 / 注解规则

| 规则 | 说明 |
| ---- | ---- |
| 主键 / 外键 | `Long`；主键 `id` |
| 时间 | `createdAt` / `updatedAt` / `deletedAt` → `LocalDateTime` |
| `*_code` | `deviceCode`、`modalityCode` → `String` |
| `_flag` | `enabledFlag` → `Boolean` |
| 软删 | `Boolean isDeleted` |
| 实体 | `@Entity` `@Table(name="pacs_device")` |
| 仓储 | `JpaRepository` + `JpaSpecificationExecutor`；允许派生 `findByTenantIdAndDeviceCode` |
| 分页 | 默认 `sortOrder ASC, updatedAt DESC` |
| 事务 | 领域服务不声明 `@Transactional` |
| 事件 | 无 |

---

## 4. 实体 / 值对象 / 枚举 / 仓储 / 查询契约

### 4.1 表与实体映射

| 逻辑表 | 实体类 | 说明 |
| ------ | ------ | ---- |
| `pacs_device` | `PacsDevice` | 聚合根 |

### 4.2 聚合根 `PacsDevice`（字段级契约）

| 字段名（Java） | 类型 | 必填 | 说明 / 约束 |
| -------------- | ---- | ---- | ----------- |
| `id` | `Long` | 系统 | 聚合根 |
| `deviceCode` | `String` | 是 | 业务编码（≈ SBID）；租户内未软删唯一（BR-DEV-02） |
| `name` | `String` | 是 | 设备名称 |
| `modalityCode` | `String` | 是 | 模态码 |
| `aeTitle` | `String` | 否 | DICOM AE Title |
| `stationName` | `String` | 否 | 机房/室名 |
| `enabledFlag` | `Boolean` | 是 | 默认 true |
| `sortOrder` | `Integer` | 否 | 列表排序 |
| `remark` | `String` | 否 | 备注 |
| `tenantId` | `Long` | 是 | 租户 |
| `isDeleted` | `Boolean` | 系统 | 软删 |
| `createdBy` | `Long` | 系统 | 创建人 |
| `createdAt` | `LocalDateTime` | 系统 | 创建时间 |
| `updatedBy` | `Long` | 系统 | 更新人 |
| `updatedAt` | `LocalDateTime` | 系统 | 更新时间 |
| `deletedBy` | `Long` | 系统 | 软删人 |
| `deletedAt` | `LocalDateTime` | 系统 | 软删时间 |

**不变式方法**：`assertRequiredFields` / `assertDeviceCodeUnique`

### 4.3 子实体

无。本聚合为单表。

### 4.4 子实体

无其他子实体。

### 4.5 仓储接口

```java
public interface PacsDeviceRepository
    extends JpaRepository<PacsDevice, Long>, JpaSpecificationExecutor<PacsDevice> {

    Optional<PacsDevice> findByTenantIdAndDeviceCode(Long tenantId, String deviceCode);
}
```

`findByTenantIdAndDeviceCode` 须受软删过滤器约束（底座已滤软删）。

### 4.6 查询模型（`queries.model`）

#### 4.6.1 `PacsDeviceQueryModel`（对应 `search`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `id` | `Long` | `pacs_device.id` | |
| `deviceCode` | `String` | `pacs_device.device_code` | |
| `name` | `String` | `pacs_device.name` | |
| `modalityCode` | `String` | `pacs_device.modality_code` | |
| `aeTitle` | `String` | `pacs_device.ae_title` | |
| `stationName` | `String` | `pacs_device.station_name` | |
| `enabledFlag` | `Boolean` | `pacs_device.enabled_flag` | |
| `sortOrder` | `Integer` | `pacs_device.sort_order` | 不含 remark |

#### 4.6.2 `getById` 出参

直接返回 `PacsDevice` 实体（含 `remark`），无独立 QueryModel。

---

## 5. 写侧与读侧白名单（命令字段全集）

### 5.1 写侧 — `PacsDeviceDomainService`

#### 5.1.1 `save(SavePacsDeviceCommand cmd)` → `PacsDevice`

**命令文件**：`SavePacsDeviceCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `id` | `Long` | 空=新建 |
| `deviceCode` | `String` | 必填；trim |
| `name` | `String` | 必填 |
| `modalityCode` | `String` | 必填 |
| `aeTitle` | `String` | 可选；空→null |
| `stationName` | `String` | 可选 |
| `enabledFlag` | `Boolean` | 可选；默认 true |
| `sortOrder` | `Integer` | 可选 |
| `remark` | `String` | 可选 |
| `tenantId` | `Long` | 必填 |
| `operatorId` | `Long` | 必填 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `assertRequiredFields` + `assertDeviceCodeUnique` |
| `statusTransitionMethod` | N/A |
| `repositoryChecks` | `findByTenantIdAndDeviceCode`；更新时 excludeId |
| `eventPublishAnchor` | 无 |
| `unitTestCaseId` | TC-DEV-001 |

**方法契约摘要**：前置：deviceCode/name/modalityCode/tenantId 非空；持久化：insert/update；失败：`INVALID_ARGUMENT` / `CODE_DUPLICATE` / `NOT_FOUND`；**注释锚点**：校验 → 唯一性 → 加载/new → save。

---

#### 5.1.2 `disable(DisablePacsDeviceCommand cmd)` → `PacsDevice`

**命令文件**：`DisablePacsDeviceCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `id` | `Long` | 必填 |
| `operatorId` | `Long` | 必填 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | N/A |
| `statusTransitionMethod` | `enabledFlag` → false |
| `repositoryChecks` | `getById` |
| `eventPublishAnchor` | 无 |
| `unitTestCaseId` | TC-DEV-002 |

**方法契约摘要**：前置：存在；持久化：停用；失败：`NOT_FOUND`；停用后 `enabledOnly` 列表不可见（BR-DEV-03）；**注释锚点**：加载 → 写 enabled → save。

---

#### 5.1.3 `delete(DeletePacsDeviceCommand cmd)` → `void`

**命令文件**：`DeletePacsDeviceCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `id` | `Long` | 必填 |
| `operatorId` | `Long` | 必填 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | N/A |
| `statusTransitionMethod` | N/A |
| `repositoryChecks` | `getById` |
| `eventPublishAnchor` | 无 |
| `unitTestCaseId` | TC-DEV-003 |

**方法契约摘要**：前置：存在；持久化：逻辑删；失败：`NOT_FOUND`；**注释锚点**：加载 → 软删 → save。

---

#### 5.1.4 `getById(GetPacsDeviceCommand cmd)` → `PacsDevice`

**命令文件**：`GetPacsDeviceCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `id` | `Long` | 必填 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | N/A |
| `statusTransitionMethod` | N/A |
| `repositoryChecks` | `findById` |
| `eventPublishAnchor` | 无 |
| `unitTestCaseId` | TC-DEV-004 |

**方法契约摘要**：前置：id 非空；持久化：只读；失败：`INVALID_ARGUMENT` / `NOT_FOUND`；**注释锚点**：校验 → findById。

---

### 5.2 读侧 — `PacsDeviceQueryService`（`domain.queries`）

所有查询 **默认** `isDeleted=false` + 租户过滤；分页在数据库侧统计 total。

| 方法 | 入参 | 出参（类名，字段见 §4.6） | 关键实现 | `unitTestCaseId` |
| ---- | ---- | ------------------------- | -------- | ---------------- |
| `search` | `modalityCode`（可空）、`enabledOnly`（可空）、`keyword`（可空，LIKE name/deviceCode）、`pageable` | `Page<PacsDeviceQueryModel>` | 默认 Sort `sortOrder ASC, updatedAt DESC` | TC-DEV-R01 |

**读方法注释锚点（统一）**：构建查询条件（含软删过滤）→ 单次 SQL/仓储调用并完成分页统计 → 组装 QueryModel（无写库）。

---

## 6. 不变式映射（DM §7）

| BR 编号 | 实现策略摘要 |
| ------- | ------------ |
| **BR-DEV-01** | `save` 校验 deviceCode/name/modalityCode；默认列表 `notDeleted` |
| **BR-DEV-02** | `assertDeviceCodeUnique` + 建议 DB 唯一索引 `(tenant_id, device_code)` 且 is_deleted=false 语义由应用保证 |
| **BR-DEV-03** | `search(enabledOnly=true)` 过滤；执行侧选用须读启用设备 |
| **BR-DEV-04** | 实体无 EAV/报告版式字段；扩展走独立聚合 |

---

## 7. 事件契约与触发规则（DM §5）

无。本聚合为配置型聚合，无领域事件。

**发布时机**：不适用。

---

## 8. 删除与可见性契约

- 软删：`isDeleted=true`；禁止物理 DELETE。
- 默认查询过滤软删行。
- 停用与软删正交；二者均导致「默认选用」不可用（BR-DEV-03）。

---

## 9. 框架优先适配规则

- 遵循项目级 Java 成码门禁。
- 继承 `BaseAuditedEntity`。
- AppService 事务切面管理 UoW。

---

## 10. 生成与验收清单

- [ ] `PacsDevice` 与 §4.2 一致
- [ ] 4 个 DomainService 写/读命令与 §5.1 一致
- [ ] `search` keyword 双字段 LIKE
- [ ] deviceCode 重复单测 TC-DEV-001
- [ ] Flyway `pacs_device` 与字段表同轮

---

## 11. 单次会话代码生成门禁（DoD）

### 11.1 前置输入

- 锁定 DM v1.0 + 本 SDD
- 目标：`module-pacs-domain`

### 11.2 DoD

- 编译通过；§5.1 四方法与 §5.2 search 行为一致。
- BR-DEV-02 可测。

### 11.3 失败处理

- 唯一性口径冲突 → 回改 SDD/DM，禁止静默允许多码。

---

## 定稿检查清单（可打勾）

- [x] 标题含 `DomainModel-SDD`
- [x] 输入完整性三列表
- [x] 章节 1～11 顺序正确
- [x] `#### 5.1.1`～`5.1.4` 逐方法块齐全
- [x] §4.2 与 DM §3 字段对齐
- [x] §6 全 BR 映射
- [x] 无待确认措辞

---

## 定稿评分（百分制）

| 维度 | 分值 | 自评 |
| ---- | ---- | ---- |
| 输入完整性 | 20 | 20 |
| 方法白名单对齐 | 25 | 25 |
| 不变式映射 | 20 | 20 |
| 事件契约 | 15 | 15 |
| 软删与框架一致 | 10 | 10 |
| 去重与无歧义 | 10 | 10 |
| **总分** | **100** | **≥95，可进入代码会话** |

---

**本 SDD 正文结束。**
