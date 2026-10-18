# AS-PEIS-INT-005 第三方入站 · 应用服务详细设计（SDD）

> **编码**：AS-PEIS-INT-005-SDD · **版本**：v1.0 · **日期**：2026-06-27  
> **对应 AS**：AS-PEIS-INT-005  
> **模块**：`zxplat/peis-adapter`（独立 Maven 子模块，**非** peis-starter）

---

## 1. 实现边界

| 项 | 内容 |
|----|------|
| **模块** | `peis-adapter/inbound` |
| **类** | `PeisThirdPartyInboundController` + `PeisPersonDetailQueryFacade` |
| **边界** | HTTP 入站 → 只读 health QueryService；**不写** WorkItem / CLN |
| **实现阶段口径** | **目标态全量契约**（P0） |

### 1.1 与 INT-01 分工

| 方向 | AS | 模块 |
|------|-----|------|
| ZXPlat → 本院 LIS/PACS | INT-001 | health-application/peis |
| 第三方 → ZXPlat 读明细 | **INT-005** | **peis-adapter** |

---

## 2. 代码结构

### 2.1 包路径

```text
peis-adapter/src/main/java/com/openhis/health/adapter/peis/inbound/
├── PeisThirdPartyInboundController.java
├── PeisPersonDetailQueryFacade.java
├── auth/PeisAdapterAuthFilter.java
├── request/PeisPersonDetailRequest.java
└── response/PeisThirdPartyPersonDetailResponse.java
```

### 2.2 依赖注入（只读）

| 服务 | 用途 |
|------|------|
| `EncounterQueryService` | 按 busNo / id 读 PE 就诊 |
| `PatientQueryService` | 人口学 |
| `ServiceRequestQueryService` | 项目列表 |
| `ActivityDefinitionQueryService` | 项目名/编码 |
| `PeisServiceRequestExtQueryService` | dispatch 状态 |
| `LisWorkItemQueryService` / `PacsWorkItemQueryService` | 条码 → SR（可选） |

**禁止**注入 `PeisLisPacsDockAppService` 写路径。

---

## 3. HTTP 契约

| IF | Controller 方法 | Path | Request | Response |
|----|-----------------|------|---------|----------|
| INT-05 | `getPersonDetail` | `POST /peis-adapter/v1/lis/person/detail` | `PeisPersonDetailRequest` | `PeisThirdPartyPersonDetailResponse` |

**`PeisPersonDetailRequest`**

| 字段 | 类型 | 必填 |
|------|------|:--:|
| `busNo` | String | busNo/barcode 二选一 |
| `barcode` | String | 同上 |
| `sourceSystemCode` | String | 是（审计） |

---

## 4. 编排步骤（INT-05）

| # | 步骤 | 写库 |
|---|------|:--:|
| 1 | AuthFilter 校验 | — |
| 2 | 解析 Encounter(PE) | 读 |
| 3 | 加载 Patient + SR 列表 + AD 摘要 | 读 |
| 4 | 脱敏 + 组装 DTO | — |
| 5 | AuditEvent | 写 audit |

---

## 5. 配置

| Key | 说明 |
|-----|------|
| `zxplat.modules.peis.adapter.enabled` | 是否挂载 adapter 路由 |
| `zxplat.peis.adapter.api-keys` | 第三方鉴权 |

> **无 `peis-starter`**：PEIS 为 zxplat **主模块能力**；adapter 随 health 主应用或院点 profile 启用。

---

## 6. 测试

- busNo 命中 PE Encounter 返回 items
- 非 PE / 不存在 → 业务码
- 鉴权失败 401
- 不写 WorkItem

---

**本 SDD 正文结束。**
