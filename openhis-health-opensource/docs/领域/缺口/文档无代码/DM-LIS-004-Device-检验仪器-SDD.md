# DM-LIS-004 Device 检验仪器 · DomainModel-SDD

> **对应 DM**：`docs/领域/缺口/文档无代码/DM-LIS-004-Device-检验仪器.md`（v1.0，本 SDD 假定该 DM 为当期锁定版本）  
> **目标工程：`zxplat``  
> **限界上下文**：检验域 · 仪器管理  
> **聚合**：Device（子实体见 §4）

---

## 输入完整性检查结果

| 最小输入项 | 状态 | 说明 |
| ---------- | ---- | ---- |
| DM 路径 | 满足 | 已绑定 DM-LIS-004 |
| SDD 输出路径 | 满足 | 本文件 |
| 目标模块 | 满足 | `zxplat/zxplat-domain` |
| 包规则 `com.zxplat.domain.lis.device` | 满足 | `device` 全小写 |
| 固定技术口径（ID Long、时间 LocalDateTime、软删、beforeCommit、分页等） | 满足 | 见 §9 |
| DM §4 业务规则 | 满足 | §6 映射（BR-DEV-01~04）；§5 写方法由 AS §4 编排推导 |
| DM 持久化形态 | 满足 | §4 字段表对齐（主表 + channel + capability） |
| DM 领域事件 | 满足 | 无（DM 未定义事件） |

---

## 1. 设计目标与边界

### 1.1 设计目标

- 在单一文档内固化 **Device 聚合** 的持久化模型、领域服务契约、查询契约与不变式实现策略。
- 子表同步（channel / capability）采用 **全量对齐 + 软删旧行**。
- **单次成稿目标**：正文不出现「后续补充」「待确认」类措辞。

### 1.2 边界

- **在内**：`lis_device`、`lis_device_channel`、`lis_device_capability` 聚合内状态、字段、不变式。
- **在外**：仪器管理编排（CFG-003 AppService）。
- **跨域仅端口声明**：对 LisWorkItem 仅声明禁用前校验引用。

---

## 2. 工程落点与包结构

### 2.1 Maven 模块

- 模块：`zxplat/zxplat-domain`。

### 2.2 包结构（`com.zxplat.domain.lis.device`）

| 相对包 | 内容 |
| ------ | ---- |
| `entity` | `Device`、`DeviceChannel`、`DeviceCapability` |
| `valueobject` | 无 |
| `enums`（共享） | `DeviceStatus`（active/inactive），复用 `com.zxplat.domain.share.enums` |
| `command` | 每个写方法一个 Java 文件 |
| `repository` | `DeviceRepository`、`DeviceChannelRepository`、`DeviceCapabilityRepository`（仅 `JpaRepository` 接口） |
| `service` | `DeviceDomainService`（直接实现类） |
| `queries` | `DeviceQueryService`（直接实现类） |
| `queries.model` | `DeviceQueryModel` |
| `share.events` | `com.zxplat.domain.share.events.lis.device`（如有事件） |

---

## 3. 类型 / 字段命名 / 注解规则

| 规则 | 说明 |
| ---- | ---- |
| 主键 / 外键 | `Long`；聚合根主键 `id`；外键 `deviceId`、`activityId`、`tubeSchemeId`、`mergeProfileId` |
| 时间 | `createdAt`/`updatedAt` → `LocalDateTime` |
| `*_enum` | `statusEnum` → `String`（存 DeviceStatus 枚举 code） |
| `*_code` | 无 |
| `_flag` | 无 |
| 软删 | `Boolean isDeleted` |
| 实体 | `@Entity`，显式 `@Table`/`@Column` |
| 仓储 | `interface XxxRepository extends JpaRepository<Entity, Long>`；禁止手写 `@Query` |
| 分页 | `Pageable`；默认 `createdAt DESC` |
| 事务 | 领域服务 **不** 声明 `@Transactional`；参与 AppService `AppServiceAspect` |

---

## 4. 实体 / 值对象 / 枚举 / 仓储 / 查询契约

### 4.1 表与实体映射

| 逻辑表 | 实体类 | 说明 |
| ------ | ------ | ---- |
| `lis_device` | `Device` | 聚合根 |
| `lis_device_channel` | `DeviceChannel` | 子实体 · 通道映射 |
| `lis_device_capability` | `DeviceCapability` | 子实体 · 检验能力 |

### 4.2 聚合根 `Device`（字段级契约）

| 字段名（Java） | 类型 | 必填 | 说明 / 约束 |
| -------------- | ---- | ---- | ----------- |
| `id` | `Long` | 系统 | 聚合根标识 |
| `busNo` | `String` | 是 | 院内编码 |
| `deviceName` | `String` | 是 | 显示名 |
| `manufacturer` | `String` | 否 | 厂商 |
| `modelNumber` | `String` | 否 | 型号 |
| `serialNumber` | `String` | 否 | 序列号 |
| `locationId` | `Long` | 否 | 检验科位置 |
| `statusEnum` | `String` | 是 | active / inactive |
| `bidiConfig` | `String` (JSON) | 否 | 双向通讯配置 |
| `tenantId` | `Long` | 是 | 租户 |
| `orgId` | `Long` | 是 | 院区 |
| `isDeleted` | `Boolean` | 系统 | 软删标记 |
| `createdBy` | `Long` | 系统 | 创建人 |
| `createdAt` | `LocalDateTime` | 系统 | 创建时间 |
| `updatedBy` | `Long` | 系统 | 更新人 |
| `updatedAt` | `LocalDateTime` | 系统 | 更新时间 |

### 4.3 子实体 `DeviceChannel`

| 字段名 | 类型 | 必填 | 说明 |
| ------ | ---- | ---- | ---- |
| `id` | `Long` | 系统 | 子实体标识 |
| `deviceId` | `Long` | 是 | 外键 → `lis_device.id` |
| `channelNo` | `Integer` | 是 | 通道号 |
| `activityId` | `Long` | 是 | 项目码 |
| `unitCode` | `String` | 否 | 单位 |
| `dilutionFactor` | `BigDecimal` | 否 | 稀释倍数 |
| `isDeleted` | `Boolean` | 系统 | 软删标记 |

### 4.4 子实体 `DeviceCapability`

| 字段名 | 类型 | 必填 | 说明 |
| ------ | ---- | ---- | ---- |
| `id` | `Long` | 系统 | 子实体标识 |
| `deviceId` | `Long` | 是 | 外键 → `lis_device.id` |
| `activityId` | `Long` | 是 | 可检项目 |
| `tubeSchemeId` | `Long` | 是 | 接受的试管方案 |
| `mergeProfileId` | `Long` | 否 | 关联合管方案（可选） |
| `statusEnum` | `String` | 是 | active / inactive |
| `isDeleted` | `Boolean` | 系统 | 软删标记 |

### 4.5 仓储接口

```java
public interface DeviceRepository extends JpaRepository<Device, Long> {
    Optional<Device> findByBusNoAndIsDeletedFalse(String busNo);
    boolean existsByBusNoAndIsDeletedFalse(String busNo);
}
```

### 4.6 查询模型（`queries.model`）

#### 4.6.1 `DeviceQueryModel`（对应 `searchDevices`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `id` | `Long` | `lis_device.id` | |
| `busNo` | `String` | `lis_device.bus_no` | |
| `deviceName` | `String` | `lis_device.device_name` | |
| `statusEnum` | `String` | `lis_device.status_enum` | |
| `channelCount` | `Integer` | COUNT(`lis_device_channel`) | 通道数 |
| `createdAt` | `LocalDateTime` | `lis_device.created_at` | |

#### 4.6.2 `DeviceDetailQueryModel`（对应 `getDetail`）

同 §4.2 + channels 列表 + capabilities 列表。

#### 4.6.3 `DeviceDetailQueryModel`（对应 `getById`）

同 §4.6.2。

---

## 5. 写侧与读侧白名单（命令字段全集）

### 5.1 写侧 — `DeviceDomainService`

#### 5.1.1 `saveDevice(SaveDeviceCommand cmd)` → `Device`

**命令文件**：`SaveDeviceCommand.java`

**字段全集**：与 §4.2 聚合根字段一致（id 编辑时传）。

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | 无 |
| `statusTransitionMethod` | 无 |
| `repositoryChecks` | busNo 唯一 |
| `eventPublishAnchor` | 无 |
| `unitTestCaseId` | TC-DEV-001 |

**方法契约摘要**：前置：busNo 唯一性；持久化：id 存在则 update，否则 insert。

---

#### 5.1.2 `toggleDeviceStatus(ToggleDeviceStatusCommand cmd)` → `Device`

**命令文件**：`ToggleDeviceStatusCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `deviceId` | `Long` | 必填 |
| `targetStatus` | `String` | active / inactive |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | 无 |
| `statusTransitionMethod` | active ↔ inactive |
| `repositoryChecks` | BR-DEV-03：禁用前校验无进行中 LisWorkItem |
| `unitTestCaseId` | TC-DEV-002 |

#### 5.1.3 `syncChannels(SyncChannelsCommand cmd)` → `List<DeviceChannel>`

**命令文件**：`SyncChannelsCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `deviceId` | `Long` | 必填 |
| `channels` | `List<ChannelItem>` | 全量 channel 列表 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | 全量对齐 + 软删旧行 |
| `repositoryChecks` | BR-DEV-01：同 deviceId 下 channelNo 唯一 |
| `unitTestCaseId` | TC-DEV-003 |

#### 5.1.4 `syncCapabilities(SyncCapabilitiesCommand cmd)` → `List<DeviceCapability>`

**命令文件**：`SyncCapabilitiesCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `deviceId` | `Long` | 必填 |
| `capabilities` | `List<CapabilityItem>` | 全量 capability 列表 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | 全量对齐 + 软删旧行 |
| `repositoryChecks` | BR-DEV-02：每行唯一；BR-DEV-04：activityId 须有对应 channel |
| `unitTestCaseId` | TC-DEV-004 |

### 5.2 读侧 — `DeviceQueryService`

| 方法 | 入参 | 出参 | 关键实现 | `unitTestCaseId` |
| ---- | ---- | ---- | -------- | ---------------- |
| `searchDevices` | `DeviceQuery` | `Page<DeviceQueryModel>` | 分页 | TC-DEV-R01 |
| `getDetail` | `Long deviceId` | `DeviceDetailQueryModel` | 含 channels + capabilities | TC-DEV-R02 |

---

## 6. 不变式映射（DM §4）

| BR 编号 | 实现策略摘要 |
| ------- | ------------ |
| **BR-DEV-01** | `syncChannels` 校验同 deviceId 下 channelNo 唯一 |
| **BR-DEV-02** | `syncCapabilities` 校验 (deviceId, activityId, tubeSchemeId) 唯一 |
| **BR-DEV-03** | `toggleDeviceStatus` 禁用前校验无进行中 LisWorkItem（由 AS 层查询） |
| **BR-DEV-04** | `syncCapabilities` 校验每个 activityId 须有对应 channel |

---

## 7. 事件契约与触发规则

无。本聚合为配置型聚合，无领域事件。

---

## 8. 删除与可见性契约

- 软删：`isDeleted = true`，不物理删除。
- 查询默认过滤 `isDeleted=false`。

---

## 9. 框架优先适配规则

- 遵循项目级 Java 成码门禁。
- 事务由 AppService `AppServiceAspect` / `ReadOnlyTransactionAspect` 管理。
- channel 的 channelNo + deviceId 联合唯一。

---

## 10. 生成与验收清单

- [ ] `Device` + `DeviceChannel` + `DeviceCapability` 实体与 §4 一致
- [ ] 4 条 BR 不变式正确实现
- [ ] 子表同步全量对齐 + 软删旧行
- [ ] Repository 无 `@Query`

---

## 11. 单次会话代码生成门禁（DoD）

### 11.1 前置输入

- 锁定 DM：`DM-LIS-004-Device-检验仪器.md`
- 本 SDD 文件
- 目标模块：`zxplat/zxplat-domain`

### 11.2 DoD

- 3 表实体编译通过，子表关联正确。
- 全部 4 个写方法功能正确。

---

## 定稿检查清单（可打勾）

- [ ] 主章节结构与金骨架一致
- [ ] §5.1 子节数量 = 4
- [ ] §4.2/§4.3/§4.4 字段表与 DM §3 一一对应
- [ ] 全文无 YAML、无占位措辞

---

## 定稿评分（百分制）

| 维度 | 分值 | 自评 |
| ---- | ---- | ---- |
| 输入完整性 | 20 | 20 |
| 方法白名单对齐 | 25 | 24 |
| 不变式映射 | 20 | 20 |
| 事件契约 | 15 | 15 |
| 软删与框架一致 | 10 | 10 |
| 去重与无歧义 | 10 | 9 |
| **总分** | **100** | **98（可进入代码会话）** |

---

**本 SDD 正文结束。**
