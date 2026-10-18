# AS-PEIS-GRP-001 团检管理 · 应用服务详细设计（SDD）

> **编码**：AS-PEIS-GRP-001-SDD · **版本**：v1.1 · **日期**：2026-07-19  
> **对应 AS**：AS-PEIS-GRP-001-团检管理.md（v1.2）

---

## 1. 实现边界

| 项 | 内容 |
|----|------|
| **模块** | `zxplat/health-application/peis/group` |
| **边界** | 单位/合同/批次；名册导入（学院/班级/教职工）；**到检确认**；团检进度 |
| **编排权威** | AS-PEIS-GRP-001 §2、§4 |
| **方法权威** | `PeisGroupBookingPort`、Campus Reader；**建单不在本 AS**（见 AS-PE-FIN-002） |
| **实现阶段口径** | 先收费后建单；GRP-05 = `confirmGroupArrival` |

### 术语口径

| 项 | 约定 |
|----|------|
| **团检建模** | 每人 1×Encounter(PE) + ext；**由 FIN-002 结算创建** |
| **号源** | booking `HealthcareService(PHYSICAL_EXAM)` + Slot，PEIS 只读 |
| **结算** | [AS-PE-FIN-002](../AppService/AS-PE-FIN-002-团检批量收费.md) |

---

## 2. 代码结构

### 2.1 包路径

```
health-application/src/main/java/com/openhis/health/application/peis/group/
├── PeisGroupCheckAdminAppService.java
├── request/
│   ├── SaveCompanyRequest.java
│   ├── SaveContractRequest.java
│   ├── SaveGroupBatchRequest.java
│   ├── CreateGroupOrderRequest.java
│   ├── ImportRosterRequest.java
│   ├── RegisterGroupMemberRequest.java
│   └── SearchGroupProgressRequest.java
├── response/
│   ├── GroupOrderResponse.java
│   ├── RegisterGroupMemberResponse.java
│   └── SearchGroupProgressResponse.java
├── dto/
│   ├── CompanyRowDto.java
│   └── GroupProgressRowDto.java
└── mapper/
    ├── PeisGroupCheckMapper.java
    └── PeisGroupCheckMapperImpl.java
```

| 依赖注入 | 类型 | 用途 |
|----------|------|------|
| `PeisGroup`BookingClient`（规划） | Extension | 单位/合同/批次/团体预约（booking 侧） |
| `CampusPerson`QueryReader`（规划） | Extension | GRP-04 名册 STU/STF |
| `PeisGroupBookingPort` | Port | 单位/批次/名册 |
| `CampusArchiveQueryReader` | Extension | 学院/班级学生、教职工 |
| `PeisEncounterProgressQuery` | 查询 | GRP-06 进度 |
| `EncounterQueryService` / Charge 态 | 查询 | GRP-05 已收费门禁 |

### 2.2 领域方法白名单

| 聚合/服务 | 方法 | 用于 IF |
|-----------|------|---------|
| PeisGroupBookingPort | `saveCompany(SaveCompanyCommand)` | GRP-01 |
| PeisGroupBookingPort | `saveContract(SaveContractCommand)` | GRP-02 |
| PeisGroupBookingPort | `saveGroupBatch(SaveGroupBatchCommand)` | GRP-03 |
| PeisGroupBookingPort | `createGroupOrder(CreateGroupOrderCommand)` | GRP-04 |
| PeisGroupBookingPort | `importRoster(ImportRosterCommand)` | GRP-04 |
| PeisGroupBookingPort | `confirmArrival(rosterId)` | GRP-05 |
| PeisGroupBookingPort | `importRosterFromCampus(…)` | GRP-04 |
| PeisEncounterProgressQuery | `searchGroupProgress(GroupProgressQuery, Pageable)` | GRP-06 |
| PeisGroupBookingPort | `saveSurveyOptions(SaveSurveyOptionsCommand)` | GRP-07 |

### 2.3 枚举、字典与值集（DM 与代码可追溯）

| 类别 | 代码实现锚点 | DM 追溯锚点 | 说明 |
|------|-------------|-------------|------|
| `group_flag` | `RegisterGroupMemberRequest.groupFlag` | DM-PEIS-007 §2 | 团检标识 |
| `company_id` | `RegisterGroupMemberRequest.companyId` | DM-PEIS-007 §2；BR-PE-EXT-02 | 团检必填 |
| `contract_id` | `RegisterGroupMemberRequest.contractId` | DM-PEIS-007 §2 | |
| `group_batch_id` | `CreateGroupOrderRequest.groupBatchId` | DM-PEIS-007 §2 | booking 批次 |
| `EncounterClass.PE` | `com.zxplat.domain.share.enums.EncounterClass.PE` | DM-ADM-002 §4.1 | |

---

## 3. 按接口 ID 命名展开

### 3.1 应用服务接口（方法白名单）

**HTTP**：`PeisGroupCheckAdminAppService` → `peisGroupCheckAdmin`

| IF | 应用服务方法（英文） | 中文方法名 | HTTP |
|----|---------------------|-----------|------|
| IF-PEIS-GRP-01 | `saveCompany` | 单位维护 | `POST /api/app/peisGroupCheckAdmin/saveCompany` |
| IF-PEIS-GRP-02 | `saveContract` | 合同维护 | `POST /api/app/peisGroupCheckAdmin/saveContract` |
| IF-PEIS-GRP-03 | `saveGroupBatch` | 批次维护 | `POST /api/app/peisGroupCheckAdmin/saveGroupBatch` |
| IF-PEIS-GRP-04 | `createGroupOrder` | 团体预约 | `POST /api/app/peisGroupCheckAdmin/createGroupOrder` |
| IF-PEIS-GRP-04 | `importRoster` | 导入名册 | `POST /api/app/peisGroupCheckAdmin/importRoster` |
| IF-PEIS-GRP-05 | `confirmGroupArrival` | 到检确认 | `POST /api/app/peisGroupCheckAdmin/confirmGroupArrival` |
| IF-PEIS-GRP-04 | `importRosterByCollegeClass` | 按学院班级导入 | `POST /api/app/peisGroupCheckAdmin/importRosterByCollegeClass` |
| IF-PEIS-GRP-06 | `searchGroupProgress` | 团检进度 | `POST /api/app/peisGroupCheckAdmin/searchGroupProgress` |
| IF-PEIS-GRP-07 | `saveSurveyOptions` | 调查问卷选项 | `POST /api/app/peisGroupCheckAdmin/saveSurveyOptions` |

### 3.1.1 编排约定

| 阶段 | 含义 | 失败时 |
|------|------|--------|
| **Gate** | 权限 / 参数 | 不再调用 |
| **Pre** | Client 读快照、BR | 抛业务异常 |
| **Cmd** | DomainService / Client 写 | 事务回滚 |
| **Asm** | Mapper | — |

---

### 3.2 IF-PEIS-GRP-01 `saveCompany`（单位维护）

#### Request

| 字段 | 类型 | 必填 | 说明 |
|------|------|:--:|------|
| `companyId` | `Long` | 否 | 空则新建 |
| `name` | `String` | 是 | |
| `orgId` | `Long` | 是 | |

#### 领域编排序列

| 序号 | 阶段 | 调用 | 说明 |
|------|------|------|------|
| 1 | Gate | name 非空 | |
| 2 | Cmd | `PeisGroupBookingPort#saveCompany(SaveCompanyCommand)` | **写库点**：booking 侧单位表 |
| 3 | Asm | 返回 companyId | |

---

### 3.3 IF-PEIS-GRP-02 `saveContract`（合同维护）

#### Request

| 字段 | 类型 | 必填 |
|------|------|:--:|
| `contractId` | `Long` | 否 |
| `companyId` | `Long` | 是 |
| `packageActivityId` | `Long` | 是 |
| `validFrom` | `LocalDate` | 是 |
| `validTo` | `LocalDate` | 是 |

#### 领域编排序列

| 序号 | 阶段 | 调用 | 说明 |
|------|------|------|------|
| 1 | Pre | `PeisGroupBookingPort#getCompany(companyId)` | 单位存在 |
| 2 | Cmd | `PeisGroupBookingPort#saveContract(SaveContractCommand)` | **写库点** |

---

### 3.4 IF-PEIS-GRP-03 `saveGroupBatch`（批次维护）

#### Request

| 字段 | 类型 | 必填 |
|------|------|:--:|
| `groupBatchId` | `Long` | 否 |
| `contractId` | `Long` | 是 |
| `batchName` | `String` | 是 |
| `slotId` | `Long` | 否 | PHYSICAL_EXAM Slot |

#### 领域编排序列

| 序号 | 阶段 | 调用 | 说明 |
|------|------|------|------|
| 1 | Pre | 校验 Slot.category=PHYSICAL_EXAM | booking Client |
| 2 | Cmd | `PeisGroupBookingPort#saveGroupBatch(SaveGroupBatchCommand)` | **写库点** |

---

### 3.5 IF-PEIS-GRP-04 `createGroupOrder`（团体预约）

#### Request

| 字段 | 类型 | 必填 |
|------|------|:--:|
| `groupBatchId` | `Long` | 是 |
| `expectedCount` | `Integer` | 是 |

#### 领域编排序列

| 序号 | 阶段 | 调用 | 说明 |
|------|------|------|------|
| 1 | Pre | `PeisGroupBookingPort#getGroupBatch(groupBatchId)` | |
| 2 | Cmd | `PeisGroupBookingPort#createGroupOrder(CreateGroupOrderCommand)` | **写库点**：booking 团体订单 |

---

### 3.6 IF-PEIS-GRP-04 `importRoster`（导入名册）

#### Request

| 字段 | 类型 | 必填 |
|------|------|:--:|
| `groupBatchId` | `Long` | 是 |
| `personIds` | `List<Long>` | 否 | Campus STU/STF |
| `fileToken` | `String` | 否 | Excel 导入 |

#### 失败码

| 失败码 | 场景 |
|--------|------|
| `PERSON_NOT_IN_CAMPUS` | 名册无效 |

#### 领域编排序列

| 序号 | 阶段 | 调用 | 说明 |
|------|------|------|------|
| 1 | Gate | groupBatchId 非空 | |
| 2 | Pre | `CampusPersonQueryPort#validatePersonIds(personIds)` | BR-GRP-02 |
| 3 | Cmd | `PeisGroupBookingPort#importRoster(ImportRosterCommand)` | **写库点** |

---

### 3.7 IF-PEIS-GRP-05 `registerGroupMember`（团体登记）

#### Request

| 字段 | 类型 | 必填 |
|------|------|:--:|
| `patientId` | `Long` | 是 |
| `groupBatchId` | `Long` | 是 |
| `companyId` | `Long` | 是 |
| `contractId` | `Long` | 是 |
| `packageActivityId` | `Long` | 是 |
| `orgId` | `Long` | 是 |

#### Response

| 字段 | 类型 | 说明 |
|------|------|------|
| `encounterId` | `Long` | |
| `busNo` | `String` | |

#### 失败码

| 失败码 | 场景 |
|--------|------|
| `COMPANY_REQUIRED` | group_flag 缺 company |
| `ROSTER_NOT_FOUND` | 不在批次名册 |

#### 领域编排序列

| 序号 | 阶段 | 调用 | 说明 |
|------|------|------|------|
| 1 | Gate | companyId、contractId 非空 | BR-PE-EXT-02 |
| 2 | Pre | `PeisGroupBookingPort#isOnRoster(groupBatchId, patientId)` | |
| 3 | Pre | `EncounterQueryService#existsActivePeEncounter(patientId)` | 防重复 |
| 4 | Cmd | `PeisRegistrationDomainService#registerGroupCheckIn(RegisterGroupCheckInCommand)` | **写库点**：encounter + ext(group_flag=true) + SR |
| 5 | Asm | `PeisGroupCheckMapper#toRegisterGroupMemberResponse` | |

---

### 3.8 IF-PEIS-GRP-06 `searchGroupProgress`（团检进度，P2）

#### Request

| 字段 | 类型 | 必填 |
|------|------|:--:|
| `groupBatchId` | `Long` | 是 |
| `pageNo` | `Integer` | 是 |
| `pageSize` | `Integer` | 是 |

#### GroupProgressRowDto

| 字段 | 类型 | 说明 |
|------|------|------|
| `encounterId` | `Long` | |
| `busNo` | `String` | |
| `patientName` | `String` | |
| `progressLabel` | `String` | PeisEncounterProgressQuery |

#### 领域编排序列

| 序号 | 阶段 | 调用 | 说明 |
|------|------|------|------|
| 1 | Pre | `PeisEncounterProgressQuery#searchGroupProgress(GroupProgressQuery, Pageable)` | **仅读路径** |
| 2 | Asm | Mapper | |

---

### 3.9 IF-PEIS-GRP-07 `saveSurveyOptions`（调查问卷，P2）

| 序号 | 阶段 | 调用 | 说明 |
|------|------|------|------|
| 1 | Cmd | `PeisGroupBookingPort#saveSurveyOptions` | booking 侧配置 |

---

## 4. QueryService 与领域事件

- GRP-05 发布 `PeisEncounterRegisteredEvent`（group_flag=true）。
- GRP-06 读侧走 `PeisEncounterProgressQuery`，禁止直查 ext 表拼装进度。

---

## 5. 写库顺序

| IF | 领域方法 | health 侧写入 | booking 侧 |
|----|----------|---------------|------------|
| GRP-05 | `registerGroupCheckIn` | encounter + ext + SR | — |
| GRP-01～04 | Client Cmd | — | 单位/合同/批次/订单 |

**事务边界**：GRP-05 health 单事务；跨 booking Client 失败时 health 已提交需补偿（P2 Outbox）。

---

## 6. 非功能性需求实现

### 6.1 幂等性

- GRP-05：existsActivePeEncounter + roster 校验。

### 6.2 并发控制

- 同 patient 并发登记 → 唯一约束。

### 6.3 重试与容错

- importRoster 部分 personId 失败可返回明细（P2）。

### 6.4 数据脱敏

- searchGroupProgress patientName 权限 Gate。

### 6.5 审计日志

- GRP-05 registerGroupCheckIn 记 AuditLog，关联 §3.7 序号 4。

---

## 7. 测试要点

### 7.1 功能测试

| 场景 | 要点 | 层级 | 自动化 | 优先级 |
|------|------|------|--------|:--:|
| 团体登记 | ext.group_flag=true | 功能 | 集成 | P1 |
| 名册导入 | Campus Client | 功能 | 集成 | P1 |
| 进度列表 | GRP-06 | 功能 | API | P2 |

### 7.2 非功能测试

| 场景 | 要点 | 优先级 |
|------|------|:--:|
| 批量登记 | 100 人 | P1 |

### 7.3 查询结果一致性

- GRP-06 与 DES/MAS 进度 label 一致。

### 7.4 集成测试

- booking 批次 + GRP-05 → REP-06 团打。

### 7.5 测试缺口跟踪

| 缺口 | 说明 | 优先级 |
|------|------|:--:|
| PeisGroupBookingPort | booking H 线契约 | P1 |

---

## 8. 变更历史

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.0 | 2026-06-27 | 首版；团检 ext + booking Client |
