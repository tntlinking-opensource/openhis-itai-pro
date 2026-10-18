# AS-PEIS-BKG-001 预约衔接 · 应用服务详细设计（SDD）

> **编码**：AS-PEIS-BKG-001-SDD · **版本**：v1.0 · **日期**：2026-06-27  
> **对应 AS**：AS-PEIS-BKG-001-预约衔接.md（v1.0）

---

## 1. 实现边界

| 项 | 内容 |
|----|------|
| **模块** | `zxplat/health-application/peis/booking` |
| **边界** | 消费 booking 到检事件；创建/激活 PE Encounter；预约号与体检号互查 |
| **编排权威** | AS-PEIS-BKG-001 §2、§4 |
| **方法权威** | DM-PEIS-007（PeisRegistrationDomainService）、booking Client |
| **实现阶段口径** | **目标态全量契约**（P1） |

### 术语口径

| 项 | 约定 |
|----|------|
| **号源** | booking `HealthcareService.category = PHYSICAL_EXAM`，与门诊 Slot 分池 |
| **事务口径** | AppService **不**声明 `@Transactional` |
| **到检** | booking 签到成功 → BKG-01 创建或激活 Encounter(PE) |

---

## 2. 代码结构

### 2.1 包路径

```
health-application/src/main/java/com/openhis/health/application/peis/booking/
├── PeisBookingBridgeAppService.java
├── request/
│   ├── OnAppointmentCheckedInRequest.java
│   └── LookupByAppointmentOrBusNoRequest.java
├── response/
│   ├── OnAppointmentCheckedInResponse.java
│   └── LookupByAppointmentOrBusNoResponse.java
└── mapper/
    ├── PeisBookingBridgeMapper.java
    └── PeisBookingBridgeMapperImpl.java
```

| 依赖注入 | 类型 | 用途 |
|----------|------|------|
| `PeisRegistrationDomainService` | 领域 | 到检创建/激活 |
| `PeisEncounterExtQueryService` | 查询 | busNo / appointment 互查 |
| `PeisBookingAppointment旧命名（已废止） | Extension | 读预约快照、回写 encounterId |
| `EncounterQueryService` | 查询 | 校验 PE 状态 |

### 2.2 领域方法白名单

| 聚合/服务 | 方法 | 用于 IF |
|-----------|------|---------|
| PeisRegistrationDomainService | `registerFromAppointment(RegisterFromAppointmentCommand)` | BKG-01 |
| PeisRegistrationDomainService | `activateEncounterFromCheckIn(ActivatePeCheckInCommand)` | BKG-01（已有草稿） |
| PeisEncounterExtQueryService | `findByBusNo(String)` | BKG-02 |
| PeisEncounterExtQueryService | `findByBookingAppointmentId(Long)` | BKG-02 |
| PeisBookingAppointmentPort | `getAppointmentSnapshot(Long)` | BKG-01 Pre |
| PeisBookingAppointmentPort | `linkEncounter(Long appointmentId, Long encounterId)` | BKG-01 Asm |

### 2.3 枚举、字典与值集（DM 与代码可追溯）

| 类别 | 代码实现锚点 | DM 追溯锚点 | 说明 |
|------|-------------|-------------|------|
| `EncounterClass.PE` | `com.zxplat.domain.share.enums.EncounterClass.PE` | DM-ADM-002 §4.1 | 体检就诊 |
| `HealthcareServiceCategory.PHYSICAL_EXAM` | `com.zxplat.domain.share.enums.HealthcareServiceCategory.PHYSICAL_EXAM` | AS-PEIS-BKG-001 §1.3 | 体检号源 |
| `booking_appointment_id` | `OnAppointmentCheckedInRequest.appointmentId` | DM-PEIS-007 §2 | ext 关联预约 |
| `bus_no` | `LookupByAppointmentOrBusNoResponse.busNo` | DM-PEIS-007 §2 | 体检流水号 |

---

## 3. 按接口 ID 命名展开

> 先读完各 IF 的「领域编排序列」，按序号从上到下执行。

### 3.1 应用服务接口（方法白名单）

**HTTP 路径生成规则**：`POST /api/app/{serviceName}/{methodName}`，`PeisBookingBridgeAppService` → `peisBookingBridge`

| IF | 应用服务方法（英文） | 中文方法名 | HTTP |
|----|---------------------|-----------|------|
| IF-PEIS-BKG-01 | `onAppointmentCheckedIn` | 到检创建或激活体检 | `POST /api/app/peisBookingBridge/onAppointmentCheckedIn` |
| IF-PEIS-BKG-02 | `lookupByAppointmentOrBusNo` | 预约号体检号互查 | `POST /api/app/peisBookingBridge/lookupByAppointmentOrBusNo` |

### 3.1.1 编排约定

| 阶段 | 含义 | 失败时 |
|------|------|--------|
| **Gate** | 参数 / 租户 | 不再调用 |
| **Pre** | 读预约快照、幂等校验 | 抛业务异常 |
| **Cmd** | 领域写 | 事务回滚 |
| **Asm** | Client 回写 + Mapper | — |

---

### 3.2 IF-PEIS-BKG-01 `onAppointmentCheckedIn`（到检创建或激活体检）

#### Request

| 字段 | 类型 | 必填 | 说明 |
|------|------|:--:|------|
| `appointmentId` | `Long` | 是 | booking 预约 ID |
| `patientId` | `Long` | 是 | |
| `packageActivityId` | `Long` | 是 | 套餐 AD |
| `orgId` | `Long` | 是 | 体检中心 |
| `operatorId` | `Long` | 是 | 操作人 |

#### Response

| 字段 | 类型 | 说明 |
|------|------|------|
| `encounterId` | `Long` | |
| `busNo` | `String` | |
| `created` | `Boolean` | true=新建；false=激活已有 |

#### 失败码

| 失败码 | 场景 |
|--------|------|
| `APPOINTMENT_NOT_FOUND` | 预约不存在 |
| `APPOINTMENT_CATEGORY_INVALID` | 非 PHYSICAL_EXAM 号源 |
| `ENCOUNTER_ALREADY_FINISHED` | 关联 Encounter 已结束 |

#### Mapper 方法签名

- `toOnAppointmentCheckedInResponse(PeisEncounterExtModel, boolean created): OnAppointmentCheckedInResponse`

#### 领域编排序列（严格顺序）

| 序号 | 阶段 | 调用 | 说明 |
|------|------|------|------|
| 1 | Gate | 参数门禁 | appointmentId、patientId 非空 |
| 2 | Pre | `PeisBookingAppointmentPort#getAppointmentSnapshot(appointmentId)` | BR-BKG-01：category=PHYSICAL_EXAM |
| 3 | Pre | `PeisEncounterExtQueryService#findByBookingAppointmentId(appointmentId)` | 若已有 ext → 跳步骤 5；否则继续步骤 4 |
| 4 | Cmd | `PeisRegistrationDomainService#registerFromAppointment(RegisterFromAppointmentCommand)` | **写库点**：新建 encounter + ext |
| 5 | Cmd | `PeisRegistrationDomainService#activateEncounterFromCheckIn(ActivatePeCheckInCommand)` | **写库点**（仅当步骤 3 命中）：planned → in-progress |
| 6 | Asm | `PeisBookingAppointmentPort#linkEncounter(appointmentId, encounterId)` | 回写 booking |
| 7 | Asm | `PeisBookingBridgeMapper#toOnAppointmentCheckedInResponse` | |

---

### 3.3 IF-PEIS-BKG-02 `lookupByAppointmentOrBusNo`（预约号体检号互查）

#### Request

| 字段 | 类型 | 必填 | 说明 |
|------|------|:--:|------|
| `appointmentId` | `Long` | 否 | 与 busNo 二选一 |
| `busNo` | `String` | 否 | |

#### Response

| 字段 | 类型 | 说明 |
|------|------|------|
| `encounterId` | `Long` | 可空 |
| `appointmentId` | `Long` | 可空 |
| `busNo` | `String` | |
| `patientId` | `Long` | |

#### 失败码

| 失败码 | 场景 |
|--------|------|
| `LOOKUP_KEY_REQUIRED` | 两参数皆空 |
| `NOT_FOUND` | 无匹配 |

#### 领域编排序列

| 序号 | 阶段 | 调用 | 说明 |
|------|------|------|------|
| 1 | Gate | 参数解析 | 若 appointmentId 非空 → 步骤 2；否则若 busNo 非空 → 步骤 3；否则失败 |
| 2 | Pre | `PeisEncounterExtQueryService#findByBookingAppointmentId(appointmentId)` | **仅读路径** |
| 3 | Pre | `PeisEncounterExtQueryService#findByBusNo(busNo)` | **仅读路径** |
| 4 | Asm | `PeisBookingBridgeMapper#toLookupResponse` | |

---

## 4. QueryService 与领域事件

- **事件**：BKG-01 新建时发布 `PeisEncounterRegisteredEvent`（与 REG-02 同型）。
- **读侧**：BKG-02 禁止直查 Repository；走 `PeisEncounterExtQueryService`。

---

## 5. 写库顺序

| IF | App 方法 | Cmd 序号 | 领域方法 | 聚合写入顺序 |
|----|----------|----------|----------|--------------|
| BKG-01 | `onAppointmentCheckedIn` | 4 | `registerFromAppointment` | adm_encounter → peis_encounter_ext → SR |
| BKG-01 | `onAppointmentCheckedIn` | 5 | `activateEncounterFromCheckIn` | adm_encounter.status |

**事务边界**：BKG-01 单 IF 同一事务；步骤 4 与 5 **互斥**，仅执行其一。

---

## 6. 非功能性需求实现

### 6.1 幂等性

- BKG-01：步骤 3 命中已有 ext 时走激活路径，禁止重复建 Encounter。

### 6.2 并发控制

- 同一 appointmentId 并发到检 → ext 唯一索引 + 事务隔离。

### 6.3 重试与容错

- booking Client 回写失败 → 记录补偿任务，Encounter 已创建不回滚（Outbox P2）。

### 6.4 数据脱敏

- BKG-02 返回 patientId 需权限 Gate。

### 6.5 审计日志

- BKG-01 Cmd 写库点记 AuditLog，关联 §3.2 序号 4/5。

---

## 7. 测试要点

### 7.1 功能测试

| 场景 | 测试要点 | 层级 | 自动化 | 优先级 |
|------|----------|------|--------|:--:|
| 正向新建 | 到检 → encounter + ext | 功能 | 集成 | P1 |
| 幂等激活 | 重复到检 → activate | 功能 | 集成 | P1 |
| 互查 | appointmentId ↔ busNo | 功能 | API | P1 |

### 7.2 非功能测试

| 场景 | 要点 | 优先级 |
|------|------|:--:|
| 并发到检 | 仅 1 Encounter | P1 |

### 7.3 查询结果一致性

- BKG-02 与 REG-03 同一 encounterId 口径一致。

### 7.4 集成测试

- booking PHYSICAL_EXAM Slot 预约 → BKG-01 → REG-03 可读。

### 7.5 测试缺口跟踪

| 缺口 | 说明 | 优先级 |
|------|------|:--:|
| booking Client Mock | 需 H 线契约桩 | P1 |

---

## 8. 变更历史

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.0 | 2026-06-27 | 首版；对齐 AS-LIS-SMP-001 SDD 深度 |
