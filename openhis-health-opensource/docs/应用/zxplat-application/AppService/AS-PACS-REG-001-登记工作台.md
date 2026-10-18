# 检查域 · 登记工作台 · 应用服务设计（AS）

> **编码**：AS-PACS-REG-001 · **版本**：v1.3 · **日期**：2026-09-22  
> **菜单**：登记工作台 · **类名**：`PacsRegistrationAppService` · **路由**：`POST /api/app/pacsRegistration/{method}`  
> **状态口径**：以 [DM-PACS-001](./DM-PACS-001-PacsWorkItem-检查工单.md) 为准——登记确认后 `registered` → `scheduled`（写 accessionNumber）

---

## 1. 场景概述

### 1.1 目标与边界

| 维度 | 说明 |
|------|------|
| **在内** | 待登记列表、登记信息编辑、生成检查号并确认待摄、作废登记、本地补登记（REG-04）、**登记条打印数据装配（REG-05）** |
| **在外** | INT-01 创建 WorkItem；EXE 执行；患者 PHI → health Patient；**出纸渲染/Host → PrintTemplate（AS-BAS-PRT-001）** |
| **涉及域** | PacsWorkItem（只读 Patient JOIN） |

### 1.2 角色与关键动作

| 角色 | 动作 |
|------|------|
| **登记员** | 确认/修改登记、生成 accessionNo、作废 |
| **系统** | 列表 JOIN Patient；禁止 WorkItem 冗余 PHI |

### 1.3 术语

| 术语 | 口径 |
|------|------|
| **accessionNo** | 检查号；登记确认时生成或写入 |
| **待摄** | status=`scheduled`（有检查号） |
| **PHI** | 姓名/证件仅来自 Patient 读侧 |

### 1.4 本期范围与非目标

| 类别 | 内容 |
|------|------|
| **本期** | REG-01～05（含边缘补登与登记条） |
| **非目标** | 页面内联建档 UI；服务端渲染 PDF/PNG |

### 1.5 领域清单

| 聚合 | DM |
|------|-----|
| PacsWorkItem | [DM-PACS-001](./DM-PACS-001-PacsWorkItem-检查工单.md) |

### 1.6 业务规则

| 编号 | 规则 | IF |
|------|------|-----|
| **BR-REG-01** | 列表 JOIN Patient，WorkItem 不存姓名/证件 | REG-01 |
| **BR-REG-02** | 登记确认 → 写入非空 accessionNumber，status → **`scheduled`**（DM BR-PWI-05） | REG-02 |
| **BR-REG-03** | 仅 registered/scheduled 可编辑 | REG-02 |
| **BR-REG-04** | 作废 → entered-in-error（REG-03） | REG-03 |
| **BR-REG-05** | 本地补登 **无** ServiceRequest orderLink；须 patientId + organizationId + modalityCode | REG-04 |
| **BR-REG-06** | encounterId 可空：空则复用当日同科室门诊就诊或开立新就诊（须 serviceTypeId） | REG-04 |
| **BR-REG-07** | 默认 confirmSchedule=true → 创建后立即生成 accessionNumber 并 scheduled | REG-04 |
| **BR-REG-08** | 登记条：须已有非空 accessionNumber，且 status ∈ scheduled/in-progress/completed | REG-05 |
| **BR-REG-09** | 登记条：本 AS 仅装配 printData + scenarioCode；出纸走 PrintTemplate preparePrint | REG-05 |

---

## 2. 页面与接口总览

| IF | 用户动作 | 语义 | 读写 | 关键规则 |
|----|----------|------|:--:|----------|
| **IF-PACS-REG-01** | 待登记列表 | 分页查询 | 读 | BR-REG-01 |
| **IF-PACS-REG-02** | 保存/确认登记 | 编辑；可选确认待摄 | 写 | BR-REG-02/03 |
| **IF-PACS-REG-03** | 作废登记 | 撤登记 | 写 | BR-REG-04 |
| **IF-PACS-REG-04** | 本地补登记 | 无 HIS 申请创建工单 | 写 | BR-REG-05～07 |
| **IF-PACS-REG-05** | 打印登记条 | 装配 printData | 读 | BR-REG-08/09 |

---

## 3. 缺口跟踪（Gap）

| Gap | 类型 | 说明 |
|-----|------|------|
| — | — | **无待跟踪缺口**（REG-04 已对齐 DM `createLocalWorkItem`） |

---

## 4. 接口编排说明书

### 4.1 IF-PACS-REG-01

| 维度 | 说明 |
|------|------|
| **主路径** | 按日期/科室查待登记 WorkItem → 批量取患者姓名 → 列表 DTO |
| **查询** | `PacsWorkItemQueryService#searchPendingRegistration` |

### 4.2 IF-PACS-REG-02

| 维度 | 说明 |
|------|------|
| **主路径** | 加载详情 → 校验可编辑 → `updateRegistration`（confirmSchedule=true 时生成号并 → scheduled） |
| **写** | `PacsWorkItemDomainService#updateRegistration` |

### 4.3 IF-PACS-REG-03

| 维度 | 说明 |
|------|------|
| **主路径** | 校验可作废 → `cancelWorkItem` |
| **写** | `PacsWorkItemDomainService#cancelWorkItem` |

### 4.4 IF-PACS-REG-04

| 维度 | 说明 |
|------|------|
| **主路径** | 校验患者 → 解析/开立就诊 → `createLocalWorkItem`（无 orderLink）→ 默认确认待摄 |
| **写** | `createLocalWorkItem` + `updateRegistration(confirmSchedule=true)` |
| **遗留对照** | `goAdd`/`save` + `goSurePacs` 中「无 APPLYNO」分支 |

### 4.5 IF-PACS-REG-05

| 维度 | 说明 |
|------|------|
| **主路径** | 加载工单详情 → 校验可打 → JOIN Patient/科室名 → 返回 slip（含 `pacs-registration-slip-print`） |
| **读** | `PacsWorkItemQueryService#getDetail` + Patient/Organization 读侧 |
| **出纸** | 前端 `printJob/preparePrint`（AS-BAS-PRT-001） |

---

## 5. 业务异常

| 码 | 场景 |
|----|------|
| `PACS_WORK_ITEM_INVALID_STATUS` | 非 registered/scheduled 不可编辑/作废 |
| `PACS_WORK_ITEM_NOT_FOUND` | 工单不存在 |
| `PATIENT_NOT_FOUND` | 本地补登患者不存在 |
| `PACS_WORK_ITEM_RULE_CONFLICT` | 无检查号不可打登记条 |

---

## 6. 事件语义

| 事件 | 触发 |
|------|------|
| `PacsWorkItemScheduledEvent` | 确认登记 → scheduled |
| `PacsWorkItemCancelledEvent` | 作废登记 |
| `PacsWorkItemCreatedEvent` | 本地补登创建（serviceRequestIds 为空） |

---

## 7. 非功能性需求

列表查询 P95 < 2s（Patient 姓名批量读）。

---

## 8. 测试要点

- 列表含患者姓名但 WorkItem 表无 PHI 列
- 确认后 accessionNo 非空且 status=scheduled
- 已执行工单不可编辑/作废

---

## 9. 关联文档

- SDD：[AS-PACS-REG-001-SDD](../AppService-SDD/AS-PACS-REG-001-登记工作台-SDD.md)
- DM：[DM-PACS-001](./DM-PACS-001-PacsWorkItem-检查工单.md)
- INT：[AS-PACS-INT-001](./AS-PACS-INT-001-HIS接单入口.md)

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.0 | 2026-06-27 | 首版 P0 REG |
| v1.1 | 2026-09-20 | D-4：BR-REG-02 对齐 DM（确认→scheduled）；补 REG-03 编排 |
| v1.2 | 2026-09-22 | 序号 59：REG-04 本地补登记 IF/BR/编排 |
| v1.3 | 2026-09-22 | 序号 60：REG-05 登记条打印（委托 PrintTemplate） |
