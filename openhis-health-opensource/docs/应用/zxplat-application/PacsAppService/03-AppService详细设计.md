# PacsAppService · 详细设计

> **原则**：**1 个主页面 = 1 个 AppService 类** → `/api/app/{camelCaseAppService}/{methodName}`  
> **菜单与路由真源**：[02-交互说明.md](./02-交互说明.md)  
> **功能 IF 真源**：[00-功能清单.md](./00-功能清单.md)  
> **领域模型**：[04-FHIR领域模型.md](./04-FHIR领域模型.md)  
> **影像扩展**：[../../../模块/zxplat-module-pacs-imaging/](../../../模块/zxplat-module-pacs-imaging/)（AS-PACS-IM-*）  
> **版本**：v1.6 · **日期**：2026-06-27

---

## 一、统计

| 类别 | 主页面数 | AppService 数 | 本文档 |
|------|----------|---------------|--------|
| **health-application/pacs 核心** | 10 | 10 | §二～§五 |
| **无页面 INT** | — | — | §五（非 AppService） |
| **zxplat-module-pacs-imaging 扩展** | 3 | 3 | §六 |
| **合计** | **13** | **14** | |

### 1.1 API 规范

与 [LisAppService/03 §1.1](../LisAppService/03-AppService详细设计.md) 相同；错误码前缀 **5-02-xxxx**。

---

## 二、AS 索引（10 页面）

### 2.1 检查业务（5）

| 编码 | 类名 | 主页面 | P | 契约 |
|------|------|--------|---|------|
| **AS-PACS-REG-001** | `PacsRegistrationAppService` | 登记工作台 | P0 | ✅ [独立 AS](../AppService/AS-PACS-REG-001-登记工作台.md) |
| **AS-PACS-EXE-001** | `PacsExamExecutionAppService` | 检查执行 | P0 | ✅ [独立 AS](../AppService/AS-PACS-EXE-001-检查执行.md) |
| **AS-PACS-REP-001** | `PacsReportComposeAppService` | 报告书写 | P0 | ✅ [独立 AS](../AppService/AS-PACS-REP-001-报告书写.md) |
| **AS-PACS-REP-002** | `PacsReportAuditAppService` | 报告审核 | P0 | ✅ [独立 AS](../AppService/AS-PACS-REP-002-报告审核.md) |
| **AS-PACS-REP-003** | `PacsReportQueryAppService` | 报告查询 | P1 | ✅ |

### 2.2 检查配置（4）

| 编码 | 类名 | 主页面 | P | 契约 |
|------|------|--------|---|------|
| **AS-PACS-CFG-001** | `PacsModalityConfigAppService` | 模态与可执行项目 | P0 | ✅ [独立 AS](../AppService/AS-PACS-CFG-001-模态与可执行项目.md) |
| **AS-PACS-CFG-002** | `PacsReportTemplateConfigAppService` | 报告模板 | P1 | ⏳ |
| **AS-PACS-CFG-003** | `PacsDeviceConfigAppService` | 设备台账 | P1 | ✅ |
| **AS-PACS-CFG-004** | `PacsModuleSettingsAppService` | PACS 系统参数 | P0 | §四.2 |

> **AS-PACS-CFG-001** 原「项目与对照」已废止 health 侧 map（[01 §7.5](./01-概要设计.md#75-项目对照--一体化无-mapd3)）；维护 **`pacs_modality_capability`**（接单 INT-07 真源）。

### 2.3 运营（1）

| **AS-PACS-LOG-001** | `PacsIntegrationLogAppService` | 接口日志 | P1 | ✅ |

---

## 三、检查业务 AS（P0 正文）

### 3.1 AS-PACS-REG-001 · 登记工作台

| 属性 | 值 |
|------|-----|
| **路由** | `/pacs/registration` |
| **优先级** | P0 |

#### IF 表

| # | IF | 方法名 | 路径 | P |
|---|-----|--------|------|---|
| REG-IF-01 | IF-PACS-REG-01 | `searchPending` | `/searchPending` | P0 |
| REG-IF-02 | IF-PACS-REG-02 | `getDetail` | `/getDetail` | P0 |
| REG-IF-03 | IF-PACS-REG-02 | `saveRegistration` | `/save` | P0 |
| REG-IF-04 | IF-PACS-REG-03 | `cancelRegistration` | `/cancel` | P1 |

#### BR

| 编号 | 规则 |
|------|------|
| **BR-REG-01** | 列表 **JOIN Patient** 取姓名/证件（WorkItem **不冗余** PHI，[01 §7.3](./01-概要设计.md#73-列表查询d8)） |
| **BR-REG-02** | 登记确认：WorkItem `registered`；生成 **accessionNo**（检查号） |
| **BR-REG-03** | 仅 `registered` / `scheduled` 可编辑登记信息 |

---

### 3.2 AS-PACS-EXE-001 · 检查执行

| **路由** | `/pacs/execution` |

#### IF 表

| # | IF | 方法名 | P |
|---|-----|--------|---|
| EXE-IF-01 | IF-PACS-EXE-01 | `searchPending` | P0 |
| EXE-IF-02 | IF-PACS-EXE-02 | `searchCompleted` | P0 |
| EXE-IF-05 | IF-PACS-EXE-05 | `completeExam` | P0 |

#### BR

| 编号 | 规则 |
|------|------|
| **BR-EXE-01** | `completeExam`：WorkItem → `completed`；可选写 ImagingStudy（imaging 扩展） |
| **BR-EXE-02** | 状态机：`registered` → `in-progress` → `completed` |

---

### 3.3 AS-PACS-REP-001 · 报告书写

| **路由** | `/pacs/report/compose` |

#### IF 表

| # | IF | 方法名 | P |
|---|-----|--------|---|
| REP-IF-01 | IF-PACS-REP-01 | `loadForEdit` | P0 |
| REP-IF-02 | IF-PACS-REP-02 | `saveDraft` | P0 |
| REP-IF-03 | IF-PACS-REP-03 | `submitReport | P0 |

#### BR

| 编号 | 规则 |
|------|------|
| **BR-REP-01** | 草稿 `preliminary`；提交 → 待审 |
| **BR-REP-02** | 关联 WorkItem 须 `completed` |

---

### 3.4 AS-PACS-REP-002 · 报告审核

| **路由** | `/pacs/report/audit` |

#### IF 表

| # | IF | 方法名 | P |
|---|-----|--------|---|
| REP-IF-07 | IF-PACS-REP-07 | `approve` | P0 |
| REP-IF-08 | IF-PACS-REP-08 | `reject` | P0 |

#### BR

| 编号 | 规则 |
|------|------|
| **BR-REP-AUD-01** | 审核通过：`pacs_diagnostic_report → `final` |
| **BR-REP-AUD-02** | **upsert** [DM-CLN-006](./DM-CLN-006-DiagnosticReportIndex-报告读侧索引.md) `cln_diagnostic_report_index` |
| **BR-REP-AUD-03** | INT-06 报告路径回传 HIS（adapter 可选） |

---

## 四、配置 AS（P0）

### 4.1 AS-PACS-CFG-001 · 模态与可执行项目

| **类名** | `PacsModalityConfigAppService`（原 CatalogConfig，**无** map UI） |
| **路由** | `/pacs/config/modality` |

| # | IF | 方法名 | P |
|---|-----|--------|---|
| CFG-IF-01 | IF-PACS-CFG-01 | `searchCapability` | P0 |
| CFG-IF-02 | IF-PACS-CFG-01 | `saveCapability` | P0 |

**BR-CFG-01**：每个开单 `activity_id` 须至少一条 `pacs_modality_capability`（与 INT-07 一致）。

### 4.2 AS-PACS-CFG-004 · 系统参数

| **路由** | `/pacs/config/settings` |

维护 `pacs_config`（自动登记、PDF 路径等）。

---

## 五、无独立菜单 · INT（非 AppService）

> 与 LIS 对齐：医生站 `DoctorStationAdviceAppService.sign()` 直调 **`PacsWorkItemDomainService`**；`PacsOrderIngressOrchestrator` 负责分组。  
> 详见 AS-PACS-INT-001、AS-PACS-INT-001-SDD。

| BR | 规则 |
|----|------|
| **BR-INT-03** | 可执行性：`pacs_modality_capability` |
| **BR-INT-01~02** | 幂等 + `PacsGroupingPolicy` |

**废止**：`ImagingOrderDispatchExtension`、独立 `PacsOrderIngressAppService` HTTP 端点。

---

## 六、zxplat-module-pacs-imaging 扩展 AS（3）

可选 `imaging-starter`。D-10 契约已落盘；成码前须关闭 AS §3（补 `DM-PACS-003`）。

| AS | 文档 |
|----|------|
| AS-PACS-IM-001 | [影像采集](../../../模块/zxplat-module-pacs-imaging/AppService/AS-PACS-IM-001-影像采集.md) |
| AS-PACS-IM-002 | [影像阅片](../../../模块/zxplat-module-pacs-imaging/AppService/AS-PACS-IM-002-影像阅片.md) |
| AS-PACS-IM-003 | [阅片地址](../../../模块/zxplat-module-pacs-imaging/AppService/AS-PACS-IM-003-阅片地址.md) |

---

## 七、pacs-adapter 边界

| 能力 | 落点 |
|------|------|
| 遗留 `/webservice/*`、WorkStationServer | **pacs-adapter** |
| 现场 PACS 码表（朝阳） | **adapter 内私有**，不进 health Flyway |
| 自助机 `echPacs*` | adapter P3 |

---

## 八、建议编写顺序

1. **INT**（AS-PACS-INT-001）→ **CFG-001/004** → **REG/EXE/REP P0**  
2. **REP-003**、**LOG-001**（P1）  
3. **zxplat-module-pacs-imaging**（院点选配）

---

## 九、修订记录

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.6 | 2026-06-27 | EXE/REP/CFG-001 独立 AS 互链 |
| v1.5 | 2026-06-27 | REG/INT 独立 AS 互链；INT-SDD 索引 |
| v1.4 | 2026-06-27 | P0 IF/BR 正文；INT 对齐 LIS；CFG-001 改 modality；zxplat 口径 |
| v1.3 | 2026-06-18 | 核心 10 AS + INT 索引 |
