# 体检域 · 外发集成配置 · 应用服务设计（AS）

> **编码**：AS-PEIS-INT-002 · **版本**：v1.1 · **日期**：2026-06-27  
> **菜单**：外发集成配置 · **类名**：`PeisIntegrationConfigAppService` · **路由**：`/peis/integration`  
> **术语**：须与 [`PEIS-LIS-PACS文档口径`](../../../../.cursor/skills/PEIS-LIS-PACS文档口径/SKILL.md)、[`whale-peis-codegen.mdc`](../../../../.cursor/rules/whale-peis-codegen.mdc) 一致

---

## 1. 场景概述

### 1.1 目标与边界

| 维度 | 说明 |
|------|------|
| **在内** | 外发 **路由策略**（INT-06）；外购 LIS/PACS **连接**（INT-07）；同步 **日志/重试**（INT-08） |
| **在外** | **INT-01** 本院 WorkItem 外发 → [AS-PEIS-INT-001](./AS-PEIS-INT-001-LIS-PACS外发.md)；**金域外送** → [LisKingMed](../../../模块/zxplat-module-lis-kingmed/)（**非**本页项目对照） |
| **涉及域** | `peis_config` 路由 key；**peis-adapter** 连接与日志（Client） |

### 1.2 角色

| 角色 | 动作 |
|------|------|
| **信息科/管理员** | 配置路由、外购连接、查看同步日志 |
| **系统** | INT-01 读路由；retrySync 触发重发 |

### 1.3 术语（硬门禁）

| 术语 | 口径 |
|------|------|
| **INT-01 外发** | PEIS → **本院** LisWorkItem / PacsWorkItem |
| **外发 LIS（口语）** | **金域** LisKingMed EXT；**不在**本 AS 维护码表 |
| **INT-06 路由** | 按 `ActivityDefinition.type_enum`：PROOF→LIS、TEST→PACS；**禁止** PEIS↔LIS 项目 map |
| **一体化院点（师大）** | 可无外购连接，INT-06 仅 enabled 开关 + 自动外发策略 |
| **外购院点** | INT-07 adapter 连接 + INT-08 日志；码表在 **adapter 内**，不进 health Flyway |
| **条码** | 仍归 **INT-01** 子能力，**不在**本页 |

### 1.4 范围

| P1 | INT-06（save/getDispatchRoute） |
| P2 | INT-07 连接、INT-08 日志/retrySync |
| 非目标 | `peis_lis_project_map` / 项目对照 UI（D3 已废止）；金域 EXT 参数（LisKingMed 模块） |

### 1.5 领域清单

| 组件 | 职责 | 参考 |
|------|------|------|
| PeisIntegrationConfigPort | adapter 路由/连接/重试 | peis-adapter |
| PeisSyncLogQueryPort | 同步日志读 | peis-adapter |
| PeisConfigDomainService | 可选 `peis.integration.*` key | DM-PEIS-004 |
| PeisLisPacsDockAppService | 执行外发 | INT-001 |

### 1.6 业务规则（BR）

| 编号 | 规则 | IF |
|------|------|-----|
| **BR-INT2-01** | 一体化院点可无 map，路由仅 targetSystem + enabled | INT-06 |
| **BR-INT2-02** | **禁止** Request/配置含项目对照、itemMap 字段 | INT-06 |
| **BR-INT2-03** | 外购连接须 endpointUrl（或 connectionJson） | INT-07 |
| **BR-INT2-04** | retrySync 仅 status=failed 日志 | INT-08 |
| **BR-INT2-05** | 金域外送开关在 **LIS/LisKingMed**，不在 PEIS 重复 | — |

---

## 2. 页面与接口总览（契约概览）

| IF | 用户动作 | 接口语义 | 读写 | 关键规则 | 状态变化 | 涉及域 | 查询服务 |
|----|----------|----------|:--:|----------|----------|----------|----------|
| **IF-PEIS-INT-06** | 保存路由 | 外发路由策略 | 写 | BR-INT2-01/02 | adapter 配置 | Extension | — |
| **IF-PEIS-INT-06** | 查询路由 | 读当前路由 | 读 | — | — | Extension | — |
| **IF-PEIS-INT-07** | 连接配置 | 外购 LIS/PACS 连接 | 写 | BR-INT2-03 | 加密存储 | adapter | — |
| **IF-PEIS-INT-08** | 同步日志 | 分页查失败/成功 | 读 | P2 | — | Extension | PeisSyncLogQueryPort |
| **IF-PEIS-INT-08** | 重试 | 对 failed 重发 | 写 | BR-INT2-04 | 新 log | Client → INT-01 | — |

---

## 3. 缺口跟踪（Gap）

**无待跟踪缺口**（本 AS 编排经 **peis-adapter Client** 与 `peis_config`；**无** zxplat-domain 写聚合待建。INT-01 执行仍见 [GAP-PEIS-INT-01](./AS-PEIS-INT-001-LIS-PACS外发.md) `LisResultProjectionService`。）

---

## 4. 接口编排说明书（业务步骤）

### 4.1 IF-PEIS-INT-06 保存外发路由（saveDispatchRoute）

| 步骤 | 说明 |
|------|------|
| 1 | Gate：校验 Request **无** itemMap / 对照表字段（BR-INT2-02） |
| 2 | 外购院点：endpointUrl 必填 |
| 3 | `PeisIntegrationConfigPort#saveDispatchRoute` |
| 4 | AuditLog（不含密钥） |

**路由语义示例（一体化）**：

```text
type_enum=PROOF → 同进程 LIS（INT-01）
type_enum=TEST  → 同进程 PACS（INT-01）
标记外送金域   → LIS 侧 LisKingMed（非 PEIS map）
```

### 4.2 IF-PEIS-INT-06 查询外发路由（getDispatchRoute）

| 步骤 | 说明 |
|------|------|
| 1 | Client `#getDispatchRoute(orgId)` 只读 |
| 2 | 返回 enabled、targetSystemCode、endpointUrl（脱敏） |

### 4.3 IF-PEIS-INT-07 连接配置（P2）

| 步骤 | 说明 |
|------|------|
| 1 | connectionJson 敏感字段不落日志 |
| 2 | Client `#saveAdapterConnection` 加密写 adapter |

### 4.4 IF-PEIS-INT-08 同步日志与重试（P2）

**searchSyncLogs**：Client 分页，按 orgId、status、时间过滤。

**retrySync**：

| 步骤 | 说明 |
|------|------|
| 1 | Pre：log status=failed |
| 2 | Client `#retrySync` → 触发 INT-01 外发 |
| 3 | 返回新 logId；HTTP 不阻塞长事务 |

---

## 5. 业务异常与用户提示

| 码 | 场景 | 提示 |
|----|------|------|
| `ROUTE_INVALID` | 含禁止的对照字段 | 不支持项目对照配置 |
| `ENDPOINT_REQUIRED` | 外购缺 URL | 请填写连接地址 |
| `SYNC_LOG_NOT_FAILED` | 非 failed 重试 | 仅失败记录可重试 |
| `ADAPTER_UNAVAILABLE` | Client 不可用 | 集成服务未启用 |

---

## 6. 事件语义与消费方

| 事件 | 消费方 |
|------|--------|
| 路由变更 | INT-01 下次外发读新策略（缓存 TTL） |
| retrySync 成功 | DES-08 可手工/事件刷新结果 |

---

## 7. 非功能性需求

| 项 | 承诺 |
|----|------|
| **安全** | connectionJson 响应不回显密钥 |
| **幂等** | saveDispatchRoute 按 orgId+target 覆盖 |
| **审计** | 连接变更 AuditLog |
| **重试** | adapter 侧指数退避（INT-08） |

---

## 8. 测试要点（业务视角）

- saveDispatchRoute 带 itemMap 字段 → ROUTE_INVALID
- 一体化院点 getDispatchRoute → PROOF/TEST 路由，无对照表
- INT-01 外发读 INT-06 策略
- retrySync failed 日志 → INT-01 再 dispatch
- **金域**项：LIS 标记外送 → LisKingMed，本页 **无** map 配置项

---

## 9. 关联文档与变更历史

- SDD：[AS-PEIS-INT-002-SDD](../AppService-SDD/AS-PEIS-INT-002-外发集成配置-SDD.md)
- 执行：[AS-PEIS-INT-001](./AS-PEIS-INT-001-LIS-PACS外发.md)
- 跨模块：[Peis 01 §7](../PeisAppService/01-概要设计.md#7-lispacspeis-跨模块架构约定)、[04 §5](../PeisAppService/04-FHIR领域模型.md#五与-lis--pacs-的衔接同进程无项目对照表)
- 金域：[zxplat-module-lis-kingmed 00](../../../模块/zxplat-module-lis-kingmed/00-功能清单.md)

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.1 | 2026-06-27 | 补全 §1～§8；金域 vs INT-06 口径；Gap 合规 |
| v1.0 | 2026-06-27 | 首版骨架 |
