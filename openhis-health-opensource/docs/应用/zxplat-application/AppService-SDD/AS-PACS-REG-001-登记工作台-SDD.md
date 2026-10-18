# AS-PACS-REG-001 登记工作台 · 应用服务详细设计（SDD）

> **编码**：AS-PACS-REG-001-SDD · **版本**：v1.2 · **日期**：2026-09-22  
> **对应 AS**：AS-PACS-REG-001-登记工作台.md（v1.3）  
> **实现阶段口径**：目标态全量契约（序号 21～23、59 REG-04、**60 REG-05**）

---

## 1. 实现边界

| 项 | 内容 |
|----|------|
| **模块** | `zxplat/health-application` · `pacs/registration` |
| **边界** | 登记工作台列表/保存确认/作废；不写 PHI；不写 DICOM |
| **编排权威** | AS-PACS-REG-001 §2、§4 |
| **方法权威** | DM-PACS-001 §6 / Domain SDD |
| **实现方式** | `PacsRegistrationAppService`；HTTP 一律 POST |

### 术语口径

| 项 | 约定 |
|----|------|
| **事务** | 写 IF 单事务；读 IF 无写 |
| **确认登记** | `UpdatePacsRegistrationCommand.confirmSchedule=true` → accession + `scheduled` |
| **Patient 姓名** | `PatientQueryService#findPatientNamesByIds` 批量；禁止落 WorkItem |

---

## 2. 代码结构

### 2.1 包路径

```
health-application/.../pacs/registration/
├── service/PacsRegistrationAppService.java
├── request/SearchPendingRegistrationRequest.java
├── request/GetRegistrationDetailRequest.java
├── request/SaveRegistrationRequest.java
├── request/CancelRegistrationRequest.java
├── request/CreateLocalRegistrationRequest.java
├── request/LoadRegistrationSlipPrintRequest.java
├── response/SearchPendingRegistrationResponse.java
├── response/GetRegistrationDetailResponse.java
├── response/SaveRegistrationResponse.java
├── response/CancelRegistrationResponse.java
├── response/CreateLocalRegistrationResponse.java
├── dto/PacsRegistrationRowDto.java
├── dto/PacsRegistrationSlipPrintDto.java
├── mapper/PacsRegistrationMapper.java          # @Component 手工装配（无 MapStruct Impl）
├── support/PacsLocalRegistrationEncounterResolver.java
└── support/PacsRegistrationSlipPrintAssembler.java
```

| 注入 | 类型 | 用途 |
|------|------|------|
| `PacsWorkItemQueryService` | 查询 | 待登记列表 / 详情 |
| `PacsWorkItemDomainService` | 领域 | updateRegistration / cancelWorkItem |
| `PatientQueryService` | 查询 | 批量姓名 |
| `CurrentPractitionerResolver` | 应用 | 操作人 |
| `PageWindowGate` | 应用 | 分页门禁 |
| `PacsRegistrationMapper` | Mapper | DTO 装配 |
| `PacsLocalRegistrationEncounterResolver` | 应用 | REG-04 就诊解析 |
| `PacsRegistrationSlipPrintAssembler` | 应用 | REG-05 打印 slip |
| `EncounterDomainService` | 领域 | 开立门诊就诊 |
| `EncounterQueryService` | 查询 | 同日门诊防重复 |
| `OrganizationQueryService` | 查询 | REG-05 科室名 |

### 2.2 领域方法白名单

| 聚合 | 方法 | IF |
|------|------|-----|
| PacsWorkItem | `PacsWorkItemQueryService#searchPendingRegistration` | REG-01 |
| PacsWorkItem | `PacsWorkItemQueryService#getDetail` | REG-02 Pre |
| PacsWorkItem | `PacsWorkItemDomainService#updateRegistration(UpdatePacsRegistrationCommand)` | REG-02 |
| PacsWorkItem | `PacsWorkItemDomainService#cancelWorkItem(CancelPacsWorkItemCommand)` | REG-03 |
| PacsWorkItem | `PacsWorkItemDomainService#createLocalWorkItem(CreateLocalPacsWorkItemCommand)` | REG-04 |
| Encounter | `EncounterDomainService#registerOutpatientEncounter` | REG-04 |
| Encounter | `EncounterQueryService#checkOutpatientDuplicateRegistration` | REG-04 |
| Patient | `PatientQueryService#findPatientNamesByIds` | REG-01 Asm |
| Patient | `PatientQueryService#findOptionalPatientEntityById` | REG-04 Pre / REG-05 |
| Organization | `OrganizationQueryService#mapNamesByIds` | REG-05 |

### 2.3 枚举、字典与值集

| 类别 | 代码实现锚点 | DM 追溯锚点 | 说明 |
|------|-------------|-------------|------|
| 工单状态 | `com.zxplat.domain.share.enums.PacsWorkItemStatus` | DM-PACS-001 §3.3 | registered / scheduled / entered-in-error |
| 列表状态 | `PacsRegistrationRowDto.statusEnum` | DM-PACS-001 §3.1 `statusEnum` | 出站展示 |
| 检查号 | `SaveRegistrationRequest.accessionNumber` | DM-PACS-001 §3.1 `accessionNumber` | 可空则生成 |

---

## 3. 按接口 ID 命名展开

先读完各 IF「领域编排序列」，按序号执行。

### 3.1 应用服务接口（方法白名单）

| IF | 应用服务方法（英文） | 中文方法名 | HTTP |
|----|----------------------|------------|------|
| IF-PACS-REG-01 | `searchPendingRegistration` | 待登记列表 | `POST /api/app/pacsRegistration/searchPendingRegistration` |
| IF-PACS-REG-02 | `saveRegistration` | 保存登记 | `POST /api/app/pacsRegistration/saveRegistration` |
| IF-PACS-REG-03 | `cancelRegistration` | 作废登记 | `POST /api/app/pacsRegistration/cancelRegistration` |
| IF-PACS-REG-04 | `createLocalRegistration` | 本地补登记 | `POST /api/app/pacsRegistration/createLocalRegistration` |
| IF-PACS-REG-05 | `loadRegistrationSlipPrint` | 登记条打印 slip | `POST /api/app/pacsRegistration/loadRegistrationSlipPrint` |

另提供读详情（REG-02 编辑前）：`getRegistrationDetail` → `POST /api/app/pacsRegistration/getRegistrationDetail`（从属于 REG-02 读侧）。

### 3.1.1 编排约定

| 阶段 | 含义 | 失败时 |
|------|------|--------|
| **Gate** | 分页/主键/操作人 | 停止 |
| **Pre** | 加载与状态门禁 | 禁止 Cmd |
| **Cmd** | Domain 写 | 回滚 |
| **Asm** | Mapper | — |

### 3.2 IF-PACS-REG-01 `searchPendingRegistration`（待登记列表）

**Request**

| 字段 | 类型 | 必填 | 说明 |
|------|------|:----:|------|
| pageNo | Integer | 是 | ≥1 |
| pageSize | Integer | 是 | ≥1 |
| organizationId | Long | 否 | 科室 |
| modalityCode | String | 否 | 模态 |
| startDate | LocalDateTime | 否 | createdAt 起 |
| endDate | LocalDateTime | 否 | createdAt 止 |

**Response**

| 字段 | 类型 | 说明 |
|------|------|------|
| total | Long | 总数 |
| records | List\<PacsRegistrationRowDto\> | 行 |

**PacsRegistrationRowDto**

| 字段 | 类型 | 说明 |
|------|------|------|
| workItemId | Long | 工单 id |
| accessionNumber | String | 检查号 |
| patientId | Long | 患者 |
| patientName | String | 姓名（Patient JOIN） |
| encounterId | Long | 就诊 |
| organizationId | Long | 科室 |
| statusEnum | String | 状态 code |
| modalityCode | String | 模态 |
| bodySiteCode | String | 部位 |
| scheduledTime | LocalDateTime | 计划时间 |
| orderCount | Integer | 关联医嘱数 |
| createdAt | LocalDateTime | 创建时间 |

**领域编排序列**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
|------|------|------|----------|------|
| 1 | Gate | `PageWindowGate#requirePositivePageWindow` | request | 分页 |
| 2 | Pre | `PacsWorkItemQueryService#searchPendingRegistration` | SearchQuery + Pageable | 仅读 |
| 3 | Pre | `PatientQueryService#findPatientNamesByIds` | patientIds | 批量姓名 |
| 4 | Asm | `PacsRegistrationMapper#toRowDto` | QueryModel + name | 仅读路径 |

### 3.3 IF-PACS-REG-02 `saveRegistration`（保存登记）

**Request**

| 字段 | 类型 | 必填 | 说明 |
|------|------|:----:|------|
| workItemId | Long | 是 | 工单 |
| organizationId | Long | 否 | 科室 |
| modalityCode | String | 否 | 模态 |
| bodySiteCode | String | 否 | 部位 |
| deviceId | Long | 否 | 设备 |
| performerPractitionerId | Long | 否 | 执行人 |
| scheduledTime | LocalDateTime | 否 | 计划时间 |
| accessionNumber | String | 否 | 空则确认时生成 |
| confirmSchedule | Boolean | 否 | true=确认待摄 |

**Response**

| 字段 | 类型 | 说明 |
|------|------|------|
| workItemId | Long | 工单 |
| accessionNumber | String | 检查号 |
| statusEnum | String | 状态 |

**失败码**

| 码 | 场景 |
|----|------|
| `PACS_WORK_ITEM_NOT_FOUND` | 不存在 |
| `PACS_WORK_ITEM_INVALID_STATUS` | 不可编辑 |
| `PACS_WORK_ITEM_RULE_CONFLICT` | 检查号冲突 |

**领域编排序列**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
|------|------|------|----------|------|
| 1 | Gate | `CurrentPractitionerResolver#requireOperatorId` | — | 操作人 |
| 2 | Pre | `PacsWorkItemQueryService#getDetail` | workItemId | 存在性 |
| 3 | Cmd | `PacsWorkItemDomainService#updateRegistration(UpdatePacsRegistrationCommand)` | cmd | **写库点**；confirmSchedule 控制迁态 |
| 4 | Asm | 装配 Response | 实体 | — |

### 3.4 IF-PACS-REG-03 `cancelRegistration`（作废登记）

**Request**

| 字段 | 类型 | 必填 | 说明 |
|------|------|:----:|------|
| workItemId | Long | 是 | 工单 |
| reason | String | 否 | 原因 |

**Response**

| 字段 | 类型 | 说明 |
|------|------|------|
| workItemId | Long | 工单 |
| statusEnum | String | entered-in-error |
| success | Boolean | true |

**领域编排序列**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
|------|------|------|----------|------|
| 1 | Gate | `CurrentPractitionerResolver#requireOperatorId` | — | 操作人 |
| 2 | Cmd | `PacsWorkItemDomainService#cancelWorkItem(CancelPacsWorkItemCommand)` | cmd | **写库点** |

### 3.5 IF-PACS-REG-04 `createLocalRegistration`（本地补登记）

**Request**

| 字段 | 类型 | 必填 | 说明 |
|------|------|:----:|------|
| patientId | Long | 是 | 患者 |
| organizationId | Long | 是 | 科室 |
| modalityCode | String | 是 | 模态 |
| bodySiteCode | String | 否 | 部位 |
| activityId | Long | 否 | capability 校验 |
| deviceId | Long | 否 | 设备 |
| scheduledTime | LocalDateTime | 否 | 计划时间 |
| encounterId | Long | 否 | 已有就诊 |
| serviceTypeId | Long | 条件 | encounterId 空时必填 |
| accessionNumber | String | 否 | 空则确认时生成 |
| confirmSchedule | Boolean | 否 | 默认 true |

**Response**

| 字段 | 类型 | 说明 |
|------|------|------|
| workItemId | Long | 工单 |
| encounterId | Long | 就诊 |
| accessionNumber | String | 检查号 |
| statusEnum | String | 状态 |

**领域编排序列**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
|------|------|------|----------|------|
| 1 | Gate | `CurrentPractitionerResolver#requireOperatorId` | — | 操作人 |
| 2 | Pre | `PatientQueryService#findOptionalPatientEntityById` | patientId | 须存在 |
| 3 | Pre | `PacsLocalRegistrationEncounterResolver#resolveEncounterId` | patient/org/encounter/serviceType | 复用或开立就诊 |
| 4 | Cmd | `PacsWorkItemDomainService#createLocalWorkItem` | CreateLocalPacsWorkItemCommand | **写库点**；无 orderLink |
| 5 | Cmd | `PacsWorkItemDomainService#updateRegistration` | confirmSchedule=true | 默认确认待摄 |

### 3.6 IF-PACS-REG-05 `loadRegistrationSlipPrint`（登记条打印 slip）

**Request**

| 字段 | 类型 | 必填 | 说明 |
|------|------|:----:|------|
| workItemId | Long | 是 | 工单 |

**Response（PacsRegistrationSlipPrintDto）**

| 字段 | 类型 | 说明 |
|------|------|------|
| scenarioCode | String | 固定 `pacs-registration-slip-print` |
| workItemId | Long | 工单 |
| encounterId | Long | 就诊 |
| patientId | Long | 患者 |
| accessionNumber | String | 检查号 |
| patientName | String | 姓名 |
| genderEnumText | String | 性别展示 |
| ageText | String | 年龄展示 |
| organizationName | String | 科室名 |
| modalityCode | String | 模态 |
| bodySiteCode | String | 部位 |
| scheduledTimeText | String | 计划时间展示 |
| statusEnum | String | 状态 |

**失败码**

| 码 | 场景 |
|----|------|
| `PACS_WORK_ITEM_RULE_CONFLICT` | 无检查号 |
| `PACS_WORK_ITEM_INVALID_STATUS` | registered / entered-in-error 等不可打 |

**领域编排序列**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
|------|------|------|----------|------|
| 1 | Pre | `PacsWorkItemQueryService#getDetail` | workItemId | 存在性 + 状态门禁 |
| 2 | Pre | `PatientQueryService#findOptionalPatientEntityById` | patientId | 姓名/性别/年龄 |
| 3 | Pre | `OrganizationQueryService#mapNamesByIds` | organizationId | 科室名 |
| 4 | Asm | `PacsRegistrationSlipPrintAssembler#assemble` | — | 仅读；出纸另调 printJob |

### 3.7 `getRegistrationDetail`（登记详情，REG-02 读侧）

**Request**：`workItemId` 必填。  
**Response**：详情字段同 Domain `PacsWorkItemDetailQueryModel` + `patientName`。

| 序号 | 阶段 | 调用 | 说明 |
|------|------|------|------|
| 1 | Pre | `getDetail` | 仅读 |
| 2 | Pre | `findPatientNamesByIds` | 姓名 |
| 3 | Asm | Mapper | — |

---

## 4. QueryService 与领域事件

| 事件 | 读侧影响 |
|------|----------|
| `PacsWorkItemScheduledEvent` | 待登记列表减少；待摄列表增加 |
| `PacsWorkItemCancelledEvent` | 列表不再出现可编辑单 |

---

## 5. 写库顺序

| IF | App 方法 | Cmd | 写入 |
|----|----------|-----|------|
| REG-02 | saveRegistration | updateRegistration | `pacs_work_item` |
| REG-03 | cancelRegistration | cancelWorkItem | `pacs_work_item` + orders |
| REG-04 | createLocalRegistration | createLocalWorkItem + updateRegistration | `pacs_work_item`；可能 `adm_encounter` |

事务：单写方法默认同一事务；失败回滚。

---

## 6. 非功能性需求实现

### 6.1 幂等性

| 项 | 代码位置 | 关联步骤 |
|----|----------|----------|
| 已 scheduled 再确认 | Domain `confirmRegistration` / update 幂等路径 | §3.3 序号 3 |

### 6.2 并发控制

乐观依赖单聚合更新；检查号唯一索引防冲突。

### 6.3 重试与容错

写失败整单回滚；读无重试要求。

### 6.4 数据脱敏

姓名仅出站 DTO；WorkItem 无 PHI 列（BR-REG-01）。

### 6.5 审计日志

沿用 BaseAuditedEntity createdBy/updatedBy；关联 §3.3～3.4 Cmd。

---

## 7. 测试要点

### 7.1 功能测试

| 场景 | 层级 | 自动化 | 优先级 |
|------|------|--------|:------:|
| 待登记列表含姓名 | App | 可测 | P0 |
| 确认后 scheduled+accession | Domain/App | 可测 | P0 |
| 作废 entered-in-error | Domain/App | 可测 | P0 |
| 本地补登 scheduled+accession | App | 可测 | P1 |
| 登记条 slip 含 scenarioCode | App | 可测 | P1 |

### 7.2 非功能测试

列表批量姓名 P95。

### 7.3 查询一致性

事件后刷新待登记。

### 7.4 集成测试

开单签发→登记确认→库状态。

### 7.5 测试缺口跟踪

无（领域单测已有；App 测可后补）。

---

## 8. 变更历史

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.0 | 2026-09-20 | D-4 首版；对齐 DM 确认→scheduled；驱动序号 21～23 |
| v1.1 | 2026-09-22 | 序号 59 REG-04：`createLocalRegistration` + 就诊解析 |
| v1.2 | 2026-09-22 | 序号 60 REG-05：`loadRegistrationSlipPrint` + 模板种子 |
