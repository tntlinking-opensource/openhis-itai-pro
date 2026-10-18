# 体检域 · 个检登记 · 应用服务设计（AS）

> **编码**：AS-PEIS-REG-001 · **版本**：v1.1 · **日期**：2026-07-19  
> **菜单**：个检登记 · **类名**：`PeisPersonalRegistrationAppService` · **路由**：`/peis/personal-register`

---

## 1. 场景概述

### 1.1 目标与边界

| 维度 | 说明 |
|------|------|
| **在内** | 客户检索、创建 PE Encounter、展开 SR、体检单查询、收费态 Client |
| **在外** | 收费页 → **[AS-PE-FIN-001](./AS-PE-FIN-001-体检收费.md)** `/peis/charge`（REG-09）；Campus 档案 Reader |
| **涉及域** | Encounter(PE)、ServiceRequest、`peis_service_request_ext` |

### 1.2 角色

| 角色 | 动作 |
|------|------|
| **登记员** | 检索客户、选套餐、登记、跳转收费 |
| **系统** | AD 展开 SR；初始化 ext |

### 1.3 术语

| 术语 | 口径 |
|------|------|
| **一次体检** | 1 × `Encounter(class=PE)` |
| **套餐展开** | AD children → ServiceRequest[] |
| **收费边界** | PEIS 仅 REG-08/09 Client，**无**内置收银 |

### 1.4 范围

| P0 | REG-01～04、REG-08/09 |
| P1+ | REG-05～07 |

### 1.5 领域

| 聚合 | 参考 |
|------|------|
| Encounter(PE) | [04 §3](../PeisAppService/04-FHIR领域模型.md) |
| ServiceRequest | DM-WKF-004 |
| PeisServiceRequestExt | [DM-PEIS-001](./DM-PEIS-001-PeisServiceRequestExt-项目执行扩展.md) |
| PeisEncounterExt | [DM-PEIS-007](./DM-PEIS-007-PeisEncounterExt-就诊扩展.md) |

### 1.6 BR

| 编号 | 规则 | IF |
|------|------|-----|
| **BR-REG-01** | 客户检索：Patient Client + Campus（可选） | REG-01 |
| **BR-REG-02** | 登记创建 Encounter(PE) + 按套餐展开 SR | REG-02 |
| **BR-REG-03** | 每条 SR 初始化 ext `exec_status=pending`、dispatch=notSent | REG-02 |
| **BR-REG-04** | 登记后 `Encounter.status` → `in-progress`（通用态） | REG-02 |
| **BR-REG-05** | 未收费（院点配置）阻塞分科 | REG-08 |
| **BR-REG-06** | REG-09 仅跳转/唤起 **AS-PE-FIN-001**（`/peis/charge`），不在 PEIS 写 ChargeItem | REG-09 |
| **BR-REG-07** | booking 到检可预填 Appointment → Encounter 关联 | BKG-001 |

---

## 2. 接口总览

| IF | 动作 | 语义 | 读写 | 关键规则 |
|----|------|------|:--:|----------|
| **IF-PEIS-REG-01** | 检索客户 | 身份证/姓名 | 读 | BR-REG-01 |
| **IF-PEIS-REG-02** | 创建登记 | 选套餐登记 | 写 | BR-REG-02～04 |
| **IF-PEIS-REG-03** | 体检单详情 | 查询 | 读 | — |
| **IF-PEIS-REG-04** | 项目调整 | 登记后加/减项 | 写 | BR-REG-02 | P0（师大 9/1） |
| **IF-PEIS-REG-08** | 读收费态 | ChargeItem Client | 读 | BR-REG-05 |
| **IF-PEIS-REG-09** | 唤起收费 | 跳转 **AS-PE-FIN-001** | — | BR-REG-06 |

---

## 3. 缺口跟踪（Gap）

**无待跟踪缺口**（DM-PEIS-001/007、**DM-CUS-013**、DM-ADM-002 §4.1 契约已齐；Flyway/领域代码由 SDD 驱动成码，**不是** Gap）。

---

## 4. 编排说明书

### 4.1 IF-PEIS-REG-01 检索客户

| 维度 | 说明 |
|------|------|
| **触发** | 登记页输入身份证/姓名 |
| **主路径** | Patient Client 分页检索；可选 Campus 补充学籍信息 |
| **失败** | 无匹配 → 提示建档 |

### 4.2 IF-PEIS-REG-02 创建登记

见 [04 §3.3](../PeisAppService/04-FHIR领域模型.md#33-登记主路径对应-01-功能清单-p0)。

| 步骤 | 说明 |
|------|------|
| 1 | 校验 Patient + 套餐 AD active |
| 2 | 防重复在检 Encounter(PE) |
| 3 | `PeisRegistrationDomainService#registerPersonalCheckIn`：Encounter + ext + 套餐展开 SR + ext |
| 4 | 返回 busNo / encounterId；可选导引单（report） |

### 4.3 IF-PEIS-REG-03 体检单详情

| 步骤 | 说明 |
|------|------|
| 1 | 读 Encounter + peis_encounter_ext |
| 2 | 列表 SR + peis_service_request_ext 执行态 |

### 4.4 IF-PEIS-REG-04 项目调整

| 步骤 | 说明 |
|------|------|
| 1 | 校验 Encounter 未总检/完成 |
| 2 | 加项：展开 AD → 新 SR + ext；减项：未执行 SR 可删 |
| 3 | 刷新 package_snapshot_json |

### 4.5 IF-PEIS-REG-08 读取收费态

读 `ChargeItemQueryPort#isEncounterPaid(encounterId)` → DTO 供 UI 展示/门禁。

### 4.6 IF-PEIS-REG-09 唤起收费

组装 **体检收费页**跳转上下文：`pathHint=/peis/charge`，携带 `encounterId`、`patientId`；**不写** ChargeItem。目标契约：[AS-PE-FIN-001](./AS-PE-FIN-001-体检收费.md)。

---

## 5. 业务异常

| 码 | 场景 | 提示 |
|----|------|------|
| `PACKAGE_INACTIVE` | 套餐停用 | 套餐不可用 |
| `PATIENT_NOT_FOUND` | 无客户 | 请先建档 |
| `ENCOUNTER_ALREADY_ACTIVE` | 重复登记 | 已有在检记录 |

---

## 6. 事件

| 事件 | 消费方 |
|------|--------|
| `PeisEncounterRegisteredEvent` | BKG、DES 待检（P1） |

---

## 7. NFR

登记事务：Encounter + SR + ext 原子；busNo 唯一。

---

## 8. 测试

- 选套餐 → N 条 SR + ext
- 收费态 Client 未付 → 分科门禁（院点开）
- 与 booking 预约号互查

---

## 9. 关联文档

- SDD：[AS-PEIS-REG-001-SDD](../AppService-SDD/AS-PEIS-REG-001-个检登记-SDD.md)
- 体检收费：[AS-PE-FIN-001](./AS-PE-FIN-001-体检收费.md)（REG-09 目标）
- CFG：[AS-PEIS-CFG-001](./AS-PEIS-CFG-001-体检目录.md)
- INT：[AS-PEIS-INT-001](./AS-PEIS-INT-001-LIS-PACS外发.md)
- 04：[PeisAppService/04](../PeisAppService/04-FHIR领域模型.md)

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.1 | 2026-06-27 | Gap 合规；§4 全 IF 编排；师大 9/1 |
| v1.0 | 2026-06-27 | 首版 P0 个检登记 |
