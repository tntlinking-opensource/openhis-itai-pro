# AS-PACS-CFG-001 模态与可执行项目 · 应用服务详细设计（SDD）

> **编码**：AS-PACS-CFG-001-SDD · **版本**：v1.0 · **日期**：2026-09-21  
> **对应 AS**：AS-PACS-CFG-001 v1.1  
> **实现阶段口径**：序号 41

---

## 1. 实现边界

| 项 | 内容 |
|----|------|
| **模块** | `health-application` · `pacs/config/modality` |
| **类** | `PacsModalityConfigAppService` |
| **领域** | `ModalityCapabilityDomainService` / `ModalityCapabilityQueryService` |
| **HTTP** | `POST /api/app/pacsModalityConfig/{method}` |

---

## 2. 包结构

```
pacs/config/modality/
├── service/PacsModalityConfigAppService.java
├── request/SearchCapabilitiesRequest.java
├── request/SaveCapabilityRequest.java
├── request/DisableCapabilityRequest.java
├── request/DeleteCapabilityRequest.java
├── response/...
├── dto/PacsCapabilityRowDto.java
└── mapper/PacsModalityConfigMapper.java
```

### 领域白名单

| 方法 | IF |
|------|-----|
| `search` / `save` / `disable` / `softDelete` | CFG-01a～d |
| `ActivityDefinitionQueryService#findOptional...` | 保存 Pre |
| `PacsWorkItemOrderRepository#existsByActivityIdAndIsDeletedFalse` | 删除 Pre |

---

## 3. 接口

| IF | 方法 |
|----|------|
| 01a | `searchCapabilities` |
| 01b | `saveCapability`（id 空=新建） |
| 01c | `disableCapability` |
| 01d | `deleteCapability` |

**SaveCapabilityRequest**：id?、activityId*、modalityCode*、enabledFlag、organizationId  

**失败码**：`ACTIVITY_DEFINITION_NOT_FOUND`、`PACS_CAPABILITY_IN_USE`、`PACS_CAPABILITY_NOT_FOUND`、`PACS_WORK_ITEM_INVALID_ARGUMENT`

---

## 4. 修订记录

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.0 | 2026-09-21 | D-8 |
