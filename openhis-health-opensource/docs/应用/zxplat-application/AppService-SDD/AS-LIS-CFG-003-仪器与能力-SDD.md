# AS-LIS-CFG-003 仪器与能力 · 应用服务详细设计（SDD）

> **编码**：AS-LIS-CFG-003-SDD · **版本**：v2.0 · **日期**：2026-06-22
> **对应 AS**：AS-LIS-CFG-003-仪器与能力.md（v2.0）

---

## 1. 实现边界

| 项 | 内容 |
|----|------|
| **模块** | `zxplat/health-application` |
| **边界** | 仪器 CRUD；通道映射；检验能力配置；BIDI 通讯配置(P2)；试剂管理(P2) |
| **编排权威** | AS-LIS-CFG-003 §2、§4 |
| **方法权威** | DM-LIS-004 §4 BR（DeviceDomainService 由 SDD 推导） |
| **实现阶段口径** | **目标态全量契约**（P0 仪器/通道/能力；P2 BIDI/试剂） |

### 术语口径

| 项 | 约定 |
|----|------|
| **事务口径** | AppService **不**声明 `@Transactional`；写/读事务分别由 `AppServiceAspect`、`ReadOnlyTransactionAspect` 管理 |
| **BIDI** | C++ 程序负责仪器实际通讯；LIS 侧仅配置连接信息+worklist 下发+结果接收 |

---

## 2. 代码结构

### 2.1 包路径

```
health-application/src/main/java/com/openhis/health/application/lis/device/
├── LisDeviceAppService.java
├── command/
│   ├── SaveDeviceCommand.java
│   ├── SaveDeviceChannelCommand.java
│   ├── SaveDeviceCapabilityCommand.java
│   └── ToggleDeviceCommand.java
├── request/
│   └── DeviceSearchRequest.java
├── response/
│   └── DeviceSearchResponse.java
└── assembler/
    └── DeviceAssembler.java
```

| 依赖注入 | 类型 | 用途 |
|----------|------|------|
| `DeviceDomainService` | 领域 | saveDevice / saveChannel / saveCapability / toggle |
| `DeviceQueryService` | 查询 | searchDevices / getDetail |
| `LisWorkItemQueryService` | 查询（跨聚合） | BR-DEV-03：禁用前校验无进行中工单 |

### 2.2 领域方法白名单

| 聚合 | 方法 | 用于 IF |
|------|------|---------|
| Device | `saveDevice(SaveDeviceCommand)` | DEV-02 |
| Device | `saveChannel(SaveDeviceChannelCommand)` | DEV-CH |
| Device | `saveCapability(SaveDeviceCapabilityCommand)` | DEV-CAP |
| Device | `toggle(ToggleDeviceCommand)` | DEV-TOGGLE |
| DeviceQueryService | `searchDevices(DeviceQuery, Pageable)` | DEV-01 |
| DeviceQueryService | `getDetail(Long id)` | DEV-01 |

### 2.3 枚举、字典与值集（DM 与代码可追溯）

| 类别 | 代码实现锚点 | DM 追溯锚点 | 说明 |
|------|-------------|------------|------|
| `DeviceStatus` | `com.zxplat.domain.share.enums.DeviceStatus` | DM-LIS-004 §3.1 | active / inactive |

---

## 3. 按接口 ID 命名展开

### 3.1 应用服务接口（方法白名单）

**HTTP 路径生成规则**：`POST /api/app/{serviceName}/{methodName}`，serviceName = `LisDeviceAppService` → `lisDevice`

| IF | 应用服务方法（英文） | 中文方法名 | HTTP |
|----|---------------------|-----------|------|
| IF-DEV-01 | `searchDevices` | 仪器分页/模糊搜索 | `POST /api/app/lisDevice/searchDevices` |
| IF-DEV-02 | `saveDevice` | 仪器保存 | `POST /api/app/lisDevice/saveDevice` |
| IF-DEV-CH | `saveChannel` | 通道维护 | `POST /api/app/lisDevice/saveChannel` |
| IF-DEV-CAP | `saveCapability` | 能力配置 | `POST /api/app/lisDevice/saveCapability` |
| IF-DEV-TOGGLE | `toggleDevice` | 启用/禁用 | `POST /api/app/lisDevice/toggleDevice` |

### 3.1.1 编排约定

| 阶段 | 含义 | 失败时 |
|------|------|--------|
| **Gate** | 权限 / 参数门禁 | 不再调用 |
| **Pre** | 写前只读：加载快照、状态与 BR 校验 | 抛业务异常 |
| **Cmd** | 领域写：DeviceDomainService | 事务回滚 |
| **Asm** | DTO 装配 | — |

---

### 3.2 IF-DEV-TOGGLE `toggleDevice`（启用/禁用）

#### Request

| 字段 | 类型 | 必填 | 说明 |
|------|------|:--:|------|
| `deviceId` | `Long` | 是 | 仪器 ID |
| `targetStatus` | `String` | 是 | active / inactive |

#### 失败码

| 失败码 | 场景 |
|--------|------|
| `DEVICE_HAS_ACTIVE_WORKITEMS` | 禁用时有关联进行中工单 |

#### 领域编排序列（严格顺序）

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
|------|------|------|----------|------|
| 1 | **Gate** | 参数门禁 | deviceId 非空 | |
| 2 | **Pre** | `DeviceQueryService#getDetail(deviceId)` | deviceId | 加载仪器快照 |
| 3 | **Pre** | `LisWorkItemQueryService#hasActiveByDevice(deviceId)` | deviceId | BR-DEV-03：若目标为 inactive 且有进行中工单 → 拒绝 |
| 4 | **Cmd** | `DeviceDomainService#toggle(ToggleDeviceCommand)` | deviceId, targetStatus | **写库点** |

---

### 3.3 IF-DEV-02 `saveDevice`（仪器保存）

#### Request

| 字段 | 类型 | 必填 | 说明 |
|------|------|:--:|------|
| `id` | `Long` | 否 | 编辑时传 |
| `busNo` | `String` | 是 | 院内编码 |
| `deviceName` | `String` | 是 | 显示名 |
| `manufacturer` | `String` | 否 | 厂商 |
| `modelNumber` | `String` | 否 | 型号 |
| `serialNumber` | `String` | 否 | 序列号 |
| `locationId` | `Long` | 否 | 检验科位置 |

#### 领域编排序列

| 序号 | 阶段 | 调用 | 说明 |
|------|------|------|------|
| 1 | **Gate** | 必填字段门禁 | busNo / deviceName 非空 |
| 2 | **Cmd** | `DeviceDomainService#saveDevice(SaveDeviceCommand)` | **写库点**：新增或更新主表 |
| 3 | **Asm** | `DeviceAssembler#toDto(Device)` | 返回 DTO |

---

### 3.4 IF-DEV-CH `saveChannel`（通道维护）

| 序号 | 阶段 | 调用 | 说明 |
|------|------|------|------|
| 1 | **Gate** | 参数门禁 | deviceId 非空 |
| 2 | **Pre** | `DeviceQueryService#getDetail(deviceId)` | 校验仪器存在 |
| 3 | **Cmd** | `DeviceDomainService#saveChannel(SaveDeviceChannelCommand)` | **写库点**：全量对齐+软删旧行；BR-DEV-01：通道号唯一 |
| 4 | **Asm** | `DeviceAssembler#toDetailDto(Device)` | 返回完整 Device |

---

### 3.5 IF-DEV-CAP `saveCapability`（能力配置）

| 序号 | 阶段 | 调用 | 说明 |
|------|------|------|------|
| 1 | **Gate** | 参数门禁 | deviceId 非空 |
| 2 | **Pre** | `DeviceQueryService#getDetail(deviceId)` | 校验仪器存在 |
| 3 | **Cmd** | `DeviceDomainService#saveCapability(SaveDeviceCapabilityCommand)` | **写库点**：全量对齐+软删旧行；BR-DEV-02：唯一性；BR-DEV-04：activityId 须有对应 channel |
| 4 | **Asm** | `DeviceAssembler#toDetailDto(Device)` | 返回完整 Device |
| 5 | **Asm** | 事件发布 | `DeviceCapabilityChangedEvent`（AS 层编排） |

---

### 3.6 IF-DEV-01 `searchDevices`（仪器分页/模糊搜索）

| 阶段 | 调用 | 说明 |
|------|------|------|
| **Pre** | `DeviceQueryService#searchDevices(DeviceQuery, Pageable)` | **仅读路径** |

---

## 4. QueryService 与领域事件

- **查询语义**：DeviceQueryService 按 busNo / deviceName / statusEnum 过滤分页。
- **领域事件**：`DeviceCapabilityChangedEvent` 由 AS 层在 DEV-CAP 成功后发布，触发 INT 合管重算。

---

## 5. 写库顺序

| IF | App 方法 | Cmd 序号 | 领域调用 | 聚合写入顺序 |
|----|----------|----------|----------|-------------|
| DEV-02 | saveDevice | §3.3 序号 2 | `DeviceDomainService#saveDevice(SaveDeviceCommand)` | 主表 lis_device 单行 upsert |
| DEV-CH | saveChannel | §3.4 序号 3 | `DeviceDomainService#saveChannel(SaveDeviceChannelCommand)` | 主表 + 子表 lis_device_channel 全量对齐 |
| DEV-CAP | saveCapability | §3.5 序号 3 | `DeviceDomainService#saveCapability(SaveDeviceCapabilityCommand)` | 主表 + 子表 lis_device_capability 全量对齐 |
| DEV-TOGGLE | toggleDevice | §3.2 序号 4 | `DeviceDomainService#toggle(ToggleDeviceCommand)` | 主表 statusEnum 更新 |

**事务边界**：单 IF 内同一事务；由 `AppServiceAspect` 管理。

---

## 6. 非功能性需求实现

### 6.1 幂等性

- DEV-02：按 busNo 唯一，重复 save → upsert。

### 6.2 并发控制

- DEV-TOGGLE：乐观锁或状态校验防止并发切换。

---

## 7. 测试要点

### 7.1 功能测试

| 场景 | 测试要点 | 优先级 |
|------|----------|:--:|
| 正向 | 仪器 CRUD | P0 |
| 正向 | 通道唯一性 | P0 |
| 异常 | 禁用仪器 → 有进行中工单 → 拒绝 | P0 |

---

## 8. 变更历史

| 版本 | 日期 | 说明 |
|------|------|------|
| v2.2 | 2026-06-22 | 去 Extension：`WorkItemStatusGateway` → 直接注入 `LisWorkItemQueryService`（跨聚合读，同模块） |
| v2.1 | 2026-06-22 | 移除 port：`WorkItemStatusGateway` → 直接注入 `LisWorkItemQueryService`（跨聚合读，不升格为 Extension） |
| v2.0 | 2026-06-22 | 重新生成：修正事务口径（AppService 不声明 @Transactional）；修正包路径；补充 §2.3 枚举 |
| v1.0 | 2026-06-22 | 首版 |
