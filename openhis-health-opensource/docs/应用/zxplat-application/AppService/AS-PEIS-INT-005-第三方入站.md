# 体检域 · 第三方入站 · 应用服务设计（AS）

> **编码**：AS-PEIS-INT-005 · **版本**：v1.0 · **日期**：2026-06-27  
> **模块**：`peis-adapter`（入站 HTTP；**非** health-application/peis 菜单页）  
> **遗留**：`peis-third-party` · `/lis/person/detail` 等  
> **关联**：[08-Extension §5](../PeisAppService/08-Extension与外部依赖.md#5-peis-adapterint-05) · [00 INT-05](../PeisAppService/00-功能清单.md)

---

## 1. 场景概述

### 1.1 目标与边界

| 维度 | 说明 |
|------|------|
| **在内** | 外购 LIS/PACS/HIS **拉取** 体检人员/项目明细（只读）；鉴权与审计 |
| **在外** | WorkItem 创建（**INT-01** 本院一体化）；结果回写（LIS/PACS 各自 Module 或 adapter 回调） |
| **涉及域** | `Encounter(PE)`、`Patient`、`ServiceRequest`、`peis_encounter_ext`（只读聚合） |

### 1.2 角色

| 角色 | 动作 |
|------|------|
| **第三方系统** | 按体检号/条码拉取受检人+项目清单 |
| **peis-adapter** | HTTP 入站、DTO 映射、调 health QueryService |

### 1.3 术语

| 术语 | 口径 |
|------|------|
| **入站** | 第三方 → ZXPlat；与 INT-01（ZXPlat → LIS/Pacs WorkItem）**方向相反** |
| **busNo** | 体检流水号，映射 `Encounter.bus_no` |
| **禁止** | 本 AS **不**维护 `peis_*_project_map`；项目以 `activity_id` / SR 为准 |

### 1.4 范围

| P0 | IF-PEIS-INT-05（人员明细） |
| P1 | 扩展 IF（订单状态回查等，不占 P0 编号） |

### 1.6 BR

| 编号 | 规则 | IF |
|------|------|-----|
| **BR-ADP-01** | 仅 `Encounter.class=PE` 且非 cancelled 可查询 | INT-05 |
| **BR-ADP-02** | 响应 **不含** 未授权字段（身份证全号等按 adapter 配置脱敏） | INT-05 |
| **BR-ADP-03** | 每次入站写 **AuditEvent**（来源系统、busNo、结果码） | INT-05 |
| **BR-ADP-04** | 项目列表来自 **ServiceRequest + AD**，非遗留 compare 表 | INT-05 |

---

## 2. 接口总览

| IF | 动作 | 语义 | 读写 | 关键规则 |
|----|------|------|:--:|----------|
| **IF-PEIS-INT-05** | 拉取人员明细 | 按 busNo/条码返回受检人+SR 项目列表 | 读 | BR-ADP-01～04 |

**HTTP（adapter 层，示例）**

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/peis-adapter/v1/lis/person/detail` | 兼容遗留路径语义 |
| POST | `/peis-adapter/v1/pacs/person/detail` | PACS 侧同源 DTO（可选 alias） |

---

## 3. 缺口跟踪（Gap）

**无 zxplat-domain 写聚合 Gap**——本 AS 只读 QueryService + adapter DTO。

| 项 | 说明 |
|----|------|
| **实现 Gap** | `peis-adapter` Maven 模块 + 鉴权 Filter + DTO 映射（成码项，**非** AS §3 Gap 类型） |

---

## 4. 编排 · IF-PEIS-INT-05

| 步骤 | 说明 |
|------|------|
| 1 | 校验 API Key / 签名（adapter 配置） |
| 2 | 解析 `busNo` 或 `barcode` → 定位 `Encounter(PE)`（条码经 Lis/Pacs WorkItem 反查 SR→Encounter，D6） |
| 3 | 批量读 `Patient`、`ServiceRequest`、`ActivityDefinition` 摘要、`peis_encounter_ext` |
| 4 | 组装 `PeisThirdPartyPersonDetailResponse` |
| 5 | 写 AuditEvent；返回 |

---

## 5. 响应 DTO（最小字段）

| 字段 | 说明 |
|------|------|
| `busNo` | 体检流水 |
| `encounterId` | 内部 ID（第三方可配置是否返回） |
| `patientName` / `gender` / `birthDate` | 人口学 |
| `idCardMasked` | 脱敏证件 |
| `items[]` | `serviceRequestId`, `activityCode`, `activityName`, `typeEnum`, `dispatchStatus`（ext） |

---

## 6. 业务异常

| 码 | 场景 |
|----|------|
| `ENCOUNTER_NOT_FOUND` | busNo/条码无效 |
| `NOT_PE_ENCOUNTER` | 非体检就诊 |
| `ADAPTER_AUTH_FAILED` | 鉴权失败 |

---

## 7. NFR

- 只读接口；P95 &lt; 500ms（院点内网）
- 与 INT-01 **无** 事务耦合

---

## 8. 关联文档

- 外发（本院）：[AS-PEIS-INT-001](./AS-PEIS-INT-001-LIS-PACS外发.md)
- SDD：[AS-PEIS-INT-005-SDD](../AppService-SDD/AS-PEIS-INT-005-第三方入站-SDD.md)
- Maven：[01-概要设计 §4](../PeisAppService/01-概要设计.md#4-maven)

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.0 | 2026-06-27 | P0 INT-05 入站；师大 9/1 adapter 联调 |
