# AS-PEIS-REG-001 个检登记 · 应用服务详细设计（SDD）

> **编码**：AS-PEIS-REG-001-SDD · **版本**：v1.0 · **日期**：2026-06-27  
> **对应 AS**：AS-PEIS-REG-001-个检登记.md（v1.0）

---

## 1. 实现边界

| 项 | 内容 |
|----|------|
| **模块** | `zxplat/health-application/peis` |
| **边界** | 客户检索；创建 PE Encounter；套餐展开 SR；登记后加/减项；收费态 Client；唤起 health 收费上下文 |
| **编排权威** | AS-PEIS-REG-001 §2、§4 |
| **方法权威** | [AS-PEIS-REG-ORCH-SDD](../AppService-SDD/AS-PEIS-REG-ORCH-登记编排-SDD.md)、DM-PEIS-001、DM-ADM-002 |
| **实现阶段口径** | **目标态全量契约**（P0，8 月首版） |

### 术语口径

| 项 | 约定 |
|----|------|
| **事务口径** | AppService **不**声明 `@Transactional`；写/读由 `AppServiceAspect` / `ReadOnlyTransactionAspect` 管理 |
| **收费** | PEIS **不写** ChargeItem；REG-08 读 Reader，REG-09 返回跳转上下文 |
| **一次体检** | `Encounter.class_enum = PE`（DM-ADM-002 §4.1） |

---

## 2. 代码结构

### 2.1 包路径

```
health-application/src/main/java/com/openhis/health/application/peis/registration/
├── PeisPersonalRegistrationAppService.java
├── command/
│   ├── RegisterPersonalCheckInCommand.java
│   └── AdjustPeServiceRequestsCommand.java
├── request/
│   ├── SearchCustomerRequest.java
│   ├── CreateRegistrationRequest.java
│   ├── AdjustServiceRequestsRequest.java
│   └── GetChargeStatusRequest.java
├── response/
│   ├── SearchCustomerResponse.java
│   ├── CreateRegistrationResponse.java
│   ├── CheckOrderDetailResponse.java
│   ├── ChargeStatusResponse.java
│   └── ChargeLaunchContextResponse.java
├── dto/
│   ├── CustomerRowDto.java
│   └── ServiceRequestLineDto.java
└── mapper/
    ├── PeisRegistrationMapper.java
    └── PeisRegistrationMapperImpl.java
```

| 依赖注入 | 类型 | 用途 |
|----------|------|------|
| `PeisRegistrationDomainService` | 领域 | registerPersonalCheckIn / adjustServiceRequests |
| `PeisEncounterExtQueryService` | 查询 | REG-03 详情 |
| `PeisEncounterProgressQuery` | 查询 | 进度摘要（可选 REG-03） |
| `PatientQueryService` | 查询 | REG-01 客户检索（DM-ADM-001 §6.2） |
| `ChargeItem`QueryReader`（规划） | Extension | REG-08 收费态 |
| `ActivityDefinitionQueryService` | 查询 | 套餐/项目校验 |

### 2.2 领域方法白名单

| 聚合/服务 | 方法 | 用于 IF |
|-----------|------|---------|
| PeisRegistrationDomainService | `registerPersonalCheckIn(RegisterPersonalCheckInCommand)` | REG-02 |
| PeisRegistrationDomainService | `adjustServiceRequests(AdjustPeServiceRequestsCommand)` | REG-04 |
| PeisEncounterExtQueryService | `getByEncounterId(Long)` | REG-03 |
| PatientQueryService | `searchByIdCardOrName(PatientSearchQuery, Pageable)` | REG-01 |
| ChargeItemQueryPort | `isEncounterPaid(Long encounterId)` | REG-08 |

### 2.3 枚举、字典与值集（DM 与代码可追溯）

| 类别 | 代码实现锚点 | DM 追溯锚点 | 说明 |
|------|-------------|-------------|------|
| `EncounterClass.PE` | `com.zxplat.domain.share.enums.EncounterClass.PE` | DM-ADM-002 §4.1 | 健康体检就诊 |
| `EncounterStatus` | `com.zxplat.domain.share.enums.EncounterStatus` | DM-ADM-002 §2.3 | planned / in-progress / finished |
| `PeisExecStatus` | `com.zxplat.plugin.peis.domain.enums.PeisExecStatus` | DM-PEIS-001 §2 `exec_status_enum` | 目标态待生成 |
| `PeisDispatchStatus` | `com.zxplat.plugin.peis.domain.enums.PeisDispatchStatus` | DM-PEIS-001 §2 `*_dispatch_enum` | 目标态待生成 |

---

## 3. 按接口 ID 命名展开

> 先读完各 IF 的「领域编排序列」，按序号从上到下执行。

### 3.1 应用服务接口（方法白名单）

**HTTP 路径生成规则**：`POST /api/app/{serviceName}/{methodName}`，`PeisPersonalRegistrationAppService` → `peisPersonalRegistration`

| IF | 应用服务方法（英文） | 中文方法名 | HTTP |
|----|---------------------|-----------|------|
| IF-PEIS-REG-01 | `searchCustomer` | 检索客户 | `POST /api/app/peisPersonalRegistration/searchCustomer` |
| IF-PEIS-REG-02 | `createRegistration` | 创建登记 | `POST /api/app/peisPersonalRegistration/createRegistration` |
| IF-PEIS-REG-03 | `getCheckOrderDetail` | 体检单详情 | `POST /api/app/peisPersonalRegistration/getCheckOrderDetail` |
| IF-PEIS-REG-04 | `adjustServiceRequests` | 项目调整 | `POST /api/app/peisPersonalRegistration/adjustServiceRequests` |
| IF-PEIS-REG-08 | `getChargeStatus` | 读取收费态 | `POST /api/app/peisPersonalRegistration/getChargeStatus` |
| IF-PEIS-REG-09 | `getChargeLaunchContext` | 唤起收费上下文 | `POST /api/app/peisPersonalRegistration/getChargeLaunchContext` |

### 3.1.1 编排约定

| 阶段 | 含义 | 失败时 |
|------|------|--------|
| **Gate** | 租户 / 参数 / 权限 | 不再调用 |
| **Pre** | 写前只读校验 | 抛业务异常，**禁止进入 Cmd** |
| **Cmd** | 领域写 | 事务回滚 |
| **Asm** | Mapper 装配 Response | — |

---

### 3.2 IF-PEIS-REG-01 `searchCustomer`（检索客户）

#### Request

| 字段 | 类型 | 必填 | 说明 |
|------|------|:--:|------|
| `idCardNo` | `String` | 否 | 身份证 |
| `nameKeyword` | `String` | 否 | 姓名 |
| `pageNo` | `Integer` | 是 | |
| `pageSize` | `Integer` | 是 | |

#### Response

| 字段 | 类型 | 说明 |
|------|------|------|
| `rows` | `List<CustomerRowDto>` | |
| `total` | `Long` | |

#### CustomerRowDto

| 字段 | 类型 | 说明 |
|------|------|------|
| `patientId` | `Long` | |
| `name` | `String` | |
| `idCardNo` | `String` | |
| `genderCode` | `String` | |
| `phone` | `String` | |

#### 领域编排序列

| 序号 | 阶段 | 调用 | 说明 |
|------|------|------|------|
| 1 | Gate | 参数门禁 | idCardNo 与 nameKeyword 至少一项 |
| 2 | Pre | `PatientQueryService#searchByIdCardOrName(PatientSearchQuery, Pageable)` | **仅读路径** |
| 3 | Asm | `PeisRegistrationMapper#toSearchCustomerResponse` | |

---

### 3.3 IF-PEIS-REG-02 `createRegistration`（创建登记）

#### Request

| 字段 | 类型 | 必填 | 说明 |
|------|------|:--:|------|
| `patientId` | `Long` | 是 | |
| `packageActivityId` | `Long` | 是 | 套餐 AD |
| `orgId` | `Long` | 是 | 体检中心 |
| `bookingAppointmentId` | `Long` | 否 | BKG 预填 |

#### Response

| 字段 | 类型 | 说明 |
|------|------|------|
| `encounterId` | `Long` | |
| `busNo` | `String` | 体检流水号 |
| `serviceRequestCount` | `Integer` | |

#### 失败码

| 失败码 | 场景 |
|--------|------|
| `PACKAGE_INACTIVE` | 套餐停用 |
| `PATIENT_NOT_FOUND` | 患者不存在 |
| `ENCOUNTER_ALREADY_ACTIVE` | 重复在检 |

#### 领域编排序列

| 序号 | 阶段 | 调用 | 说明 |
|------|------|------|------|
| 1 | Gate | 参数门禁 | patientId、packageActivityId 非空 |
| 2 | Pre | `ActivityDefinitionQueryService#getById(packageActivityId)` | 套餐 active |
| 3 | Pre | `EncounterQueryService#existsActivePeEncounter(patientId)` | BR：防重复在检 |
| 4 | Cmd | `PeisRegistrationDomainService#registerPersonalCheckIn(RegisterPersonalCheckInCommand)` | **写库点**：encounter + ext + SR + srExt |
| 5 | Asm | `PeisRegistrationMapper#toCreateRegistrationResponse` | |
| 6 | Asm | 事件 | `PeisEncounterRegisteredEvent` |

---

### 3.4 IF-PEIS-REG-03 `getCheckOrderDetail`（体检单详情）

#### Request

| 字段 | 类型 | 必填 | 说明 |
|------|------|:--:|------|
| `encounterId` | `Long` | 是 | |

#### Response

| 字段 | 类型 | 说明 |
|------|------|------|
| `encounterId` | `Long` | |
| `busNo` | `String` | |
| `patientId` | `Long` | |
| `packageActivityId` | `Long` | |
| `serviceRequestLines` | `List<ServiceRequestLineDto>` | |

#### ServiceRequestLineDto

| 字段 | 类型 | 说明 |
|------|------|------|
| `serviceRequestId` | `Long` | |
| `activityId` | `Long` | |
| `display` | `String` | |
| `execStatusCode` | `String` | ext |

#### 领域编排序列

| 序号 | 阶段 | 调用 | 说明 |
|------|------|------|------|
| 1 | Gate | encounterId 非空 | |
| 2 | Pre | `PeisEncounterExtQueryService#getByEncounterId(encounterId)` | |
| 3 | Pre | `PeisServiceRequestExtQueryService#listByEncounterId(encounterId)` | |
| 4 | Asm | `PeisRegistrationMapper#toCheckOrderDetailResponse` | **仅读路径** |

---

### 3.5 IF-PEIS-REG-04 `adjustServiceRequests`（项目调整）

#### Request

| 字段 | 类型 | 必填 | 说明 |
|------|------|:--:|------|
| `encounterId` | `Long` | 是 | |
| `addActivityIds` | `List<Long>` | 否 | 加项 |
| `removeServiceRequestIds` | `List<Long>` | 否 | 减项 |

#### 失败码

| 失败码 | 场景 |
|--------|------|
| `ENCOUNTER_STATUS_INVALID` | 已总检/完成不可改 |
| `SR_ALREADY_EXECUTED` | 项目已执行不可删 |

#### 领域编排序列

| 序号 | 阶段 | 调用 | 说明 |
|------|------|------|------|
| 1 | Gate | 至少 add 或 remove 非空 | |
| 2 | Pre | `EncounterQueryService#mustGetEncounter(encounterId)` | class=PE |
| 3 | Pre | 执行态校验 | 未 checked 方可删 |
| 4 | Cmd | `PeisRegistrationDomainService#adjustServiceRequests(AdjustPeServiceRequestsCommand)` | **写库点** |
| 5 | Cmd | `PeisEncounterExtDomainService#updatePackageSnapshot` | 刷新快照 |

---

### 3.6 IF-PEIS-REG-08 `getChargeStatus`（读取收费态）

| 序号 | 阶段 | 调用 | 说明 |
|------|------|------|------|
| 1 | Pre | `ChargeItemQueryPort#isEncounterPaid(encounterId)` | **仅读路径** |
| 2 | Asm | 返回 `ChargeStatusResponse.paid` | |

---

### 3.7 IF-PEIS-REG-09 `getChargeLaunchContext`（唤起收费上下文）

| 序号 | 阶段 | 调用 | 说明 |
|------|------|------|------|
| 1 | Pre | `PeisEncounterExtQueryService#getByEncounterId` | |
| 2 | Asm | 返回跳转上下文：`pathHint=/peis/charge`、`encounterId`、`patientId`（**AS-PE-FIN-001**） | **无 PEIS 写库** |

---

## 4. QueryService 与领域事件

- **事件**：`PeisEncounterRegisteredEvent`（REG-02）→ DES 待检刷新（P1 订阅）。
- **读侧**：REG-03 禁止直查 Repository；走 ext + SR ext QueryService。

---

## 5. 写库顺序

| IF | App 方法 | Cmd 序号 | 领域方法 | 聚合写入顺序 |
|----|----------|----------|----------|--------------|
| REG-02 | `createRegistration` | 4 | `PeisRegistrationDomainService#registerPersonalCheckIn` | adm_encounter → peis_encounter_ext → wkf_service_request → peis_service_request_ext |
| REG-04 | `adjustServiceRequests` | 4～5 | adjust + updatePackageSnapshot | SR 增删 + ext 刷新 |

**事务边界**：REG-02/04 单 IF 内同一事务；失败全回滚。

---

## 6. 非功能性需求实现

### 6.1 幂等性

- REG-02：`existsActivePeEncounter` 防重复；busNo 唯一索引。

### 6.2 并发控制

- 同一 patientId 并发登记 → 唯一约束或乐观锁。

### 6.3 重试与容错

- ChargeItemQueryPort 超时 → 返回 unknown，REG-08 不阻塞登记（院点配置）。

### 6.4 数据脱敏

- searchCustomer 返回 idCardNo 按权限掩码。

### 6.5 审计日志

- REG-02/04 记 AuditLog，关联 §3.3 序号 4、§3.5 序号 4。

---

## 7. 测试要点

### 7.1 功能测试

| 场景 | 测试要点 | 层级 | 自动化 | 优先级 |
|------|----------|------|--------|:--:|
| 正向 | 选套餐 → N 条 SR + ext | 功能 | 集成 | P0 |
| REG-04 | 加项/减项后快照更新 | 功能 | API | P0 |
| 收费 | REG-08 未付 → 院点门禁 | 功能 | API | P0 |
| 异常 | 重复在检拒绝 | 功能 | API | P0 |

### 7.2 非功能测试

| 场景 | 要点 | 优先级 |
|------|------|:--:|
| 并发登记 | 同 patient 仅 1 Encounter | P0 |

### 7.3 查询结果一致性

- REG-03 serviceRequestLines 与 DES 待检列表 SR 一致。

### 7.4 集成测试

- booking 预约号预填 encounter ext（BKG-001）。

### 7.5 测试缺口跟踪

| 缺口 | 说明 | 优先级 |
|------|------|:--:|
| ChargeItemQueryPort | health 收费 contracts 契约桩 | P0 |

---

## 8. 变更历史

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.0 | 2026-06-27 | 首版；对齐 AS-LIS-SMP-001 SDD 深度；REG-04 P0 |
