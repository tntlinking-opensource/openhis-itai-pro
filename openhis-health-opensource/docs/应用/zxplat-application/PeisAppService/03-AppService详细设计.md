# PeisAppService · AS 清单（应用契约索引）

> **原则**：**1 个主页面 = 1 个 AppService 类** → `/api/app/{camelCaseAppService}/{methodName}`  
> **菜单与路由真源**：[02-交互说明.md](./02-交互说明.md)  
> **功能 IF 真源**：[00-功能清单.md](./00-功能清单.md)  
> **版本**：v1.9 · **日期**：2026-07-19

---

## 一、统计

| 类别 | 主页面数 | AppService 数 | 契约文档 |
|------|----------|---------------|----------|
| **health-application/peis 核心** | 14 | 14 | AS-PEIS-*（本文 §二、§七） |
| **无页面** | — | 3 | INT-001、INT-005（adapter）、BKG-001 |
| **health 收费（非 PEIS）** | 2（挂载菜单） | 2 | **[AS-PE-FIN-001](../AppService/AS-PE-FIN-001-体检收费.md)** 个检；**[AS-PE-FIN-002](../AppService/AS-PE-FIN-002-团检批量收费.md)** 团检批量；退费/折扣/日结仍见 `AS-OP-FIN-*` |
| **Campus（非 PEIS）** | — | — | [Campus](../../../模块/zxplat-module-campus/03-AppService详细设计.md) · 学生/教职工名册 |
| **openhis-pro-mini（非 PEIS AS）** | — | — | [zxplat-module-miniapp](../../../模块/zxplat-module-miniapp/) · 微信端 |
| **合计（需写 PEIS AS）** | **14** | **16** | 含 2 无页面 |

> RBAC（`peis-system`）归 **whale-framework**。**收费/发票/折扣/费用报表** 归 **health 主程序**。**操作日志/库备份** 复用 framework，不建 `AS-PEIS-LOG-*`。  
> **已废止**：~~AS-PEIS-BAS-001~~、~~AS-PEIS-LOG-001~~、~~AS-PEIS-INT-003~~、~~AS-PEIS-MOB/OCC-*~~（见 §三）。

---

## 二、health-application/peis 核心 AS（14 页面 + 2 无页面）

### 2.1 登记（1）

| 编码 | AppService 类名 | 主页面 | 优先级 | 契约状态 |
|------|-----------------|--------|--------|----------|
| **AS-PEIS-REG-001** | `PeisPersonalRegistrationAppService` | 个检登记 | P0 | ✅ [AS](../AppService/AS-PEIS-REG-001-个检登记.md) · [SDD](../AppService-SDD/AS-PEIS-REG-001-个检登记-SDD.md) |

> 含 **REG-08/09**：读取 HIS 收费态、唤起 **体检收费页**（AS-PE-FIN-001 `/peis/charge`），**不**内置收银写库。客户检索（原 BAS-01）在本 AS 内。

### 2.1b health 体检收费（挂载菜单，非 PEIS AS）

| 编码 | AppService 类名 | 主页面 | 优先级 | 契约状态 |
|------|-----------------|--------|--------|----------|
| **AS-PE-FIN-001** | `PhysicalExamChargeAppService` | 体检收费（`/peis/charge`） | P0 | ✅ [AS](../AppService/AS-PE-FIN-001-体检收费.md) · [SDD](../AppService-SDD/AS-PE-FIN-001-体检收费-SDD.md) |
| **AS-PE-FIN-002** | `PhysicalExamGroupChargeAppService` | 团检批量收费（团检页 Tab） | P0 | ✅ [AS](../AppService/AS-PE-FIN-002-团检批量收费.md) · [SDD](../AppService-SDD/AS-PE-FIN-002-团检批量收费-SDD.md) |

> 归属 **health 主程序**；**禁止**新建 `AS-PEIS-FIN-*`。001=个检先登记后收费；**002=团检先收费后建 Encounter(待检)**。

### 2.2 团检（1）

| 编码 | AppService 类名 | 主页面 | 优先级 | 契约状态 |
|------|-----------------|--------|--------|----------|
| **AS-PEIS-GRP-001** | `PeisGroupCheckAdminAppService` | 团检管理 | P0 | ✅ [AS](../AppService/AS-PEIS-GRP-001-团检管理.md) · [SDD](../AppService-SDD/AS-PEIS-GRP-001-团检管理-SDD.md) |

> Tab：单位/合同/批次/名册/到检/收费(调 FIN-002)；**学院/班级/教职工** 读 Campus；**不**在 GRP 建 Encounter。  
> **待改造**：单位树、批次→体检分组（一组多套餐）、按合同导入并选定套餐——[10-团检单位树分组与名册选定方案.md](./10-团检单位树分组与名册选定方案.md)。

### 2.3 诊台与复查（4）

| 编码 | AppService 类名 | 主页面 | 优先级 | 契约状态 |
|------|-----------------|--------|--------|----------|
| **AS-PEIS-DES-001** | `PeisDeptExamDeskAppService` | 分科诊台 | P0 | ✅ [AS](../AppService/AS-PEIS-DES-001-分科诊台.md) · [SDD](../AppService-SDD/AS-PEIS-DES-001-分科诊台-SDD.md) |
| **AS-PEIS-MAS-001** | `PeisMasterReviewDeskAppService` | 总检诊台 | P0 | ✅ [AS](../AppService/AS-PEIS-MAS-001-总检诊台.md) · [SDD](../AppService-SDD/AS-PEIS-MAS-001-总检诊台-SDD.md) |
| **AS-PEIS-REC-001** | `PeisRecheckManageAppService` | 复查管理 | P1 | ✅ [AS](../AppService/AS-PEIS-REC-001-复查管理.md) · [SDD](../AppService-SDD/AS-PEIS-REC-001-复查管理-SDD.md) |
| **AS-PEIS-CRT-001** | `PeisCriticalValueDeskAppService` | 危急值处理 | P1 | ✅ [AS](../AppService/AS-PEIS-CRT-001-危急值处理.md) · [SDD](../AppService-SDD/AS-PEIS-CRT-001-危急值处理-SDD.md) |

### 2.4 报告（2）

| 编码 | AppService 类名 | 主页面 | 优先级 | 契约状态 |
|------|-----------------|--------|--------|----------|
| **AS-PEIS-REP-001** | `PeisReportPrintAppService` | 报告打印 | P0 | ✅ [AS](../AppService/AS-PEIS-REP-001-报告打印.md) · [SDD](../AppService-SDD/AS-PEIS-REP-001-报告打印-SDD.md) |
| **AS-PEIS-REP-002** | `PeisReportGrantAppService` | 报告发放 | P1 | ✅ [AS](../AppService/AS-PEIS-REP-002-报告发放.md) · [SDD](../AppService-SDD/AS-PEIS-REP-002-报告发放-SDD.md) |

### 2.5 集成配置（1 页面 + 1 无页面）

| 编码 | AppService 类名 | 主页面 / 触发 | 优先级 | 契约状态 |
|------|-----------------|---------------|--------|----------|
| **AS-PEIS-INT-002** | `PeisIntegrationConfigAppService` | 外发路由/外购连接（可选） | P2 | ✅ [AS](../AppService/AS-PEIS-INT-002-外发集成配置.md) · [SDD](../AppService-SDD/AS-PEIS-INT-002-外发集成配置-SDD.md) |
| **AS-PEIS-INT-005** | `PeisThirdPartyInboundController` | 第三方入站（adapter） | P0 | ✅ [AS](../AppService/AS-PEIS-INT-005-第三方入站.md) · [SDD](../AppService-SDD/AS-PEIS-INT-005-第三方入站-SDD.md) |
| **AS-PEIS-INT-001** | `PeisLisPacsDockAppService` | 诊台/登记内外发（无菜单） | P0 | ✅ [AS](../AppService/AS-PEIS-INT-001-LIS-PACS外发.md) · [SDD](../AppService-SDD/AS-PEIS-INT-001-LIS-PACS外发-SDD.md) |

### 2.6 主数据配置（3）

| 编码 | AppService 类名 | 主页面 | 优先级 | 契约状态 |
|------|-----------------|--------|--------|----------|
| **AS-PEIS-CFG-001** | `PeisCatalogConfigAppService` | 体检目录 | P0 | ✅ [AS](../AppService/AS-PEIS-CFG-001-体检目录.md) · [SDD](../AppService-SDD/AS-PEIS-CFG-001-体检目录-SDD.md) |
| **AS-PEIS-CFG-002** | `PeisDiagnosisConfigAppService` | 诊断与表达式 | P0/P1 | ✅ [AS](../AppService/AS-PEIS-CFG-002-诊断与表达式.md) · [SDD](../AppService-SDD/AS-PEIS-CFG-002-诊断与表达式-SDD.md) |
| **AS-PEIS-CFG-003** | `PeisModuleSettingsAppService` | 系统参数 | P0 | ✅ [AS](../AppService/AS-PEIS-CFG-003-系统参数.md) · [SDD](../AppService-SDD/AS-PEIS-CFG-003-系统参数-SDD.md) |

> **目录页** 合并遗留 7 个 system 菜单。**收费/发票配置**（遗留 system/charge、relateProject）归 health。

### 2.7 统计（2）

| 编码 | AppService 类名 | 主页面 | 优先级 | 契约状态 |
|------|-----------------|--------|--------|----------|
| **AS-PEIS-RPT-001** | `PeisStatisticsAppService` | 体检业务统计 | P2 | ✅ [AS](../AppService/AS-PEIS-RPT-001-业务统计.md) · [SDD](../AppService-SDD/AS-PEIS-RPT-001-业务统计-SDD.md) |
| **AS-PEIS-RPT-002** | `PeisDirectorDashboardAppService` | 主任业务查询 | P3 | ✅ [AS](../AppService/AS-PEIS-RPT-002-主任业务查询.md) · [SDD](../AppService-SDD/AS-PEIS-RPT-002-主任业务查询-SDD.md) |

### 2.8 无独立菜单 — booking 衔接（1）

| 编码 | AppService 类名 | 触发方 | 优先级 | 契约状态 |
|------|-----------------|--------|--------|----------|
| **AS-PEIS-BKG-001** | `PeisBookingBridgeAppService` | booking 到检事件 / PEIS 登记读预约 | P1 | ✅ [AS](../AppService/AS-PEIS-BKG-001-预约衔接.md) · [SDD](../AppService-SDD/AS-PEIS-BKG-001-预约衔接-SDD.md) |

**说明**：`peis-third-party` 入站见 **INT-005**（peis-adapter）；出站 `LisClient`/`PacsClient` 见 INT-001 / adapter。

---

## 三、已废止 / 移出 PEIS 的 AS

| 原编码 | 原类名 | 迁移至 / 说明 |
|--------|--------|---------------|
| ~~AS-PEIS-FIN-001~~ | `PeisPersonalBillingAppService` | **AS-PE-FIN-001**（health · 体检收费） |
| ~~AS-PEIS-FIN-002~~ | `PeisDiscountConfigAppService` | health 收费/权限 AS |
| ~~AS-PEIS-CFG-003~~（旧） | `PeisBillingConfigAppService` | health 收费/基础数据 AS |
| ~~AS-PEIS-BAS-001~~ | `PeisExamPersonRegistryAppService` | Campus STU/STF + health Patient/Practitioner；REG-01 覆盖客户检索 |
| ~~AS-PEIS-LOG-001~~ | `PeisOperateLogAppService` | whale-framework 审计 |
| ~~AS-PEIS-INT-003~~ | `PeisExternalOrderIngressAppService` | **不做**（`SynOrderController`） |
| ~~AS-PEIS-MOB-*~~ | 微信移动 AS | [openhis-pro-mini](../../../openhis-pro-mini/) + miniapp |
| ~~AS-PEIS-OCC-*~~ | 职业病 AS | **暂不规划** |

---

## 四、AS 文档命名与存放

```text
docs/领域/PeisAppService/AppService/
  AS-PEIS-REG-001-个检登记.md
  AS-PEIS-DES-001-分科诊台.md
  …
```

每份 AS 须含：§1 页面边界、§2 IF 表、§4 BR、与 [00-功能清单](./00-功能清单.md) IF 编号互引。

---

## 四、SDD 索引（16 份）

| AS | SDD |
|----|-----|
| 全部 AS-PEIS-* | [AppService-SDD/AS-PEIS-*-SDD.md](../AppService-SDD/) |

---

## 五、建议编写顺序

1. **AS-PEIS-CFG-001/003**（目录与参数，阻塞登记）  
2. **AS-PEIS-REG-001**（P0 登记 + HIS 收费态 Client）  
3. **AS-PEIS-DES-001** + **AS-PEIS-INT-001** + **AS-PEIS-INT-002**（分科 + 外发）  
4. **AS-PEIS-MAS-001** + **AS-PEIS-REP-001**（总检 + 报告）  
5. **AS-PEIS-BKG-001**（与 booking 联调）  
6. **AS-PEIS-GRP-001**、**REC/CRT/REP-002**（P1）  
7. **AS-PEIS-RPT-***（P2+）

---

## 七、P0 无页面 AS 要点

### AS-PEIS-INT-001 · LIS/PACS 外发

| 属性 | 值 |
|------|-----|
| **类名** | `PeisLisPacsDockAppService`（support，无菜单） |
| **触发** | DES-001 保存、REG-02 登记后（院点可配置自动/手动） |

| IF | 行为 |
|----|------|
| **INT-01** | 按 SR 列表调 LIS/PACS DomainService；含 **条码绑定** 子能力 |
| **INT-03** | 主动拉取（可选，与 DES-08 配合） |

详见 [04-FHIR §5.2](./04-FHIR领域模型.md#52-des-08--int-01-时序p0)、[AS-PACS-INT-001](../AppService/AS-PACS-INT-001-HIS接单入口.md)、[AS-LIS-INT-001](../AppService/AS-LIS-INT-001-HIS接单入口.md)。

### AS-PEIS-DES-001 · 分科诊台（摘要）

| IF | P | 要点 |
|----|---|------|
| DES-02 | P0 | 写 **`cln_observation`**（[DM-CLN-007](./DM-CLN-007-Observation-临床观察实例.md)） |
| DES-08 | P0 | LIS/PACS → CLN 投影（**补偿**；主路径 LIS AUD-05） |

---

## 八、修订记录

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.9 | 2026-07-19 | health 收费定点 **AS-PE-FIN-001**；废止「仅 OP-FIN / 或专用」模糊表述；§2.1b 挂载 |
| v1.0 | 2026-06-19 | 首版：18 核心页面 + 3 无页面；mobile/occupational 增值 |
| v1.1 | 2026-06-19 | 移除 FIN AS、CFG 收费页；核心 **15** 页；费用归 health |
| v1.2 | 2026-06-19 | 一体化部署：**取消** PEIS↔LIS/PACS 项目对照；INT-002 改为外发路由/adapter |
| v1.3 | 2026-06-19 | 核心 **14** 页；移除 BAS/LOG/INT-003；移动→openhis-pro-mini；职业病暂不规划 |
| v1.8 | 2026-06-27 | **16 份 SDD 均扩至 LIS 同级**；P0 §6/§7 补全；HTTP serviceName 修正；03 互链 AS+SDD |
| v1.7 | 2026-06-27 | 16 AS + 16 SDD 齐；DM-PEIS-*；Flyway 06 |
| v1.6 | 2026-06-27 | REG/CFG/MAS/REP 独立 AS 互链 |
| v1.5 | 2026-06-27 | §七 P0 INT/DES 要点；DM-CLN-005/006 链接；health-application/peis 口径 |
| v1.4 | 2026-06-27 | DEC 回归主文档；引用改 01 §7 |
