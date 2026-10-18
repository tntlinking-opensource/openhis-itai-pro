# AS-PACS-CFG-003 设备台账 · 应用服务详细设计（SDD）

> **编码**：AS-PACS-CFG-003-SDD · **版本**：v1.0 · **日期**：2026-09-22  
> **对应 AS**：AS-PACS-CFG-003-设备台账.md（v1.0）  
> **实现阶段口径**：目标态全量契约（序号 44）

---

## 1. 实现边界

| 项 | 内容 |
|----|------|
| **模块** | `health-application` · `pacs/config/device` |
| **边界** | 设备台账 CRUD；不写 capability、不写 EAV 参数 |
| **方法权威** | DM-PACS-005 §5 |
| **实现方式** | `PacsDeviceConfigAppService`；HTTP 一律 POST |

---

## 2. 代码结构

```
health-application/.../pacs/config/device/
├── service/PacsDeviceConfigAppService.java
├── request/SearchDevicesRequest.java
├── request/SaveDeviceRequest.java
├── request/DeviceIdRequest.java
├── response/SearchDevicesResponse.java
├── response/SaveDeviceResponse.java
├── dto/PacsDeviceRowDto.java
└── mapper/PacsDeviceConfigMapper.java
```

| 注入 | 用途 |
|------|------|
| `PacsDeviceDomainService` | 写/查 |
| `CurrentPractitionerResolver` | 操作人 |
| `PageWindowGate` | 分页 |
| `PacsDeviceConfigMapper` | DTO |

### 2.2 领域方法白名单

| 方法 | IF |
|------|-----|
| `search` | CFG-03a |
| `save` | CFG-03b |
| `disable` | CFG-03c |
| `delete` | CFG-03d |
| `getById` | 内部 |

### 2.3 枚举与值集

| 类别 | 代码锚点 | DM |
|------|----------|-----|
| enabledFlag | `Boolean` | DM-PACS-005 §3 |
| modalityCode | `String` | 与 capability 同码约定 |

---

## 3. 按接口展开

### 3.1 方法表

| IF | 方法 | HTTP |
|----|------|------|
| IF-PACS-CFG-03a | `searchDevices` | `POST /api/app/pacsDeviceConfig/searchDevices` |
| IF-PACS-CFG-03b | `saveDevice` | `POST /api/app/pacsDeviceConfig/saveDevice` |
| IF-PACS-CFG-03c | `disableDevice` | `POST /api/app/pacsDeviceConfig/disableDevice` |
| IF-PACS-CFG-03d | `deleteDevice` | `POST /api/app/pacsDeviceConfig/deleteDevice` |

### 3.2 IF-PACS-CFG-03a `searchDevices`

**Request**：pageNo、pageSize、modalityCode（否）、enabledOnly（否）、keyword（否，匹配 name/deviceCode）  
**Response**：total、records（id/deviceCode/name/modalityCode/aeTitle/stationName/enabledFlag/sortOrder）

| 序号 | 阶段 | 调用 |
|------|------|------|
| 1 | Gate | PageWindowGate |
| 2 | Pre | DomainService#search |
| 3 | Asm | Mapper |

### 3.3 IF-PACS-CFG-03b `saveDevice`

**Request**：id（否）、deviceCode、name、modalityCode、aeTitle、stationName、enabledFlag、sortOrder、remark  
**Response**：id、deviceCode、name、enabledFlag

| 序号 | 阶段 | 调用 |
|------|------|------|
| 1 | Gate | requireOperatorId |
| 2 | Cmd | save |

### 3.4 IF-PACS-CFG-03c / 03d

**Request**：id  
**Cmd**：disable / delete

---

## 4. QueryService 与领域事件

无事件；读侧即时。

---

## 5. 写库顺序

| IF | Cmd | 表 |
|----|-----|-----|
| 03b | save | pacs_device |
| 03c | disable | 同上 |
| 03d | delete | 软删 |

---

## 6. NFR

幂等：同 id 更新覆盖；`deviceCode` 唯一由领域校验。

---

## 7. 测试要点

| 场景 | 优先级 |
|------|:------:|
| 保存后列表可见 | P0 |
| 同租户重复 deviceCode 拒绝 | P0 |
| 停用后 enabledOnly 不可见 | P0 |

---

## 8. 变更历史

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.0 | 2026-09-22 | D-12；驱动序号 44 |
