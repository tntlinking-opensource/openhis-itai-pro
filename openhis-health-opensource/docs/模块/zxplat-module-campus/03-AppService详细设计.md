# Campus · AppService 详细设计

> **原则**：**1 个主页面 = 1 个 AppService 类** → `/api/app/{camelCaseAppService}/{methodName}`  
> **菜单 / 流程**：[02-交互说明.md](./02-交互说明.md)  
> **功能 IF 真源**：[00-功能清单.md](./00-功能清单.md)  
> **契约正文**：`AppService/` · **详细设计**：`AppService-SDD/`（同编号对应）  
> **版本**：v1.3 · **日期**：2026-07-19

---

## 一、统计

| 类别 | 主页面数 | AppService 数 | 契约文档 |
|------|----------|---------------|----------|
| **组织（校区/学院/部门）** | 3 | 3 | [AS-CMP-ORG-*](./AppService/) ✅ |
| **档案（学生/教职工）** | 2 | 2 | STU/STF ✅ |
| **公费** | 5 | 5 | GF-* ✅（APR 迁移） |
| **同步/报表** | 2 | 2 | P2 待补正文 |
| **无页面** | — | 3 | ARCH、CALC、OPEN ✅ |
| **合计** | **12** | **15** | |

---

## 二、组织 AS（P0）

| 编码 | AppService 类名 | 主页面 | IF 族 | 契约 |
|------|-----------------|--------|-------|------|
| **AS-CMP-ORG-CMP-001** | `CampusSiteAppService` | 校区管理 | ORG-CMP | [AS](./AppService/AS-CMP-ORG-CMP-001-校区管理.md) · [SDD](./AppService-SDD/AS-CMP-ORG-CMP-001-校区管理-SDD.md) |
| **AS-CMP-ORG-COL-001** | `CampusCollegeAppService` | 学院管理 | ORG-COL | [AS](./AppService/AS-CMP-ORG-COL-001-学院管理.md) · [SDD](./AppService-SDD/AS-CMP-ORG-COL-001-学院管理-SDD.md) |
| **AS-CMP-ORG-DEP-001** | `CampusDepartmentAppService` | 部门管理 | ORG-DEP | [AS](./AppService/AS-CMP-ORG-DEP-001-部门管理.md) · [SDD](./AppService-SDD/AS-CMP-ORG-DEP-001-部门管理-SDD.md) |

---

## 三、档案 AS（P0）

| 编码 | AppService 类名 | 主页面 | IF 族 | 契约 |
|------|-----------------|--------|-------|------|
| **AS-CMP-STU-001** | `CampusStudentAppService` | 学生档案 | STU | [AS](./AppService/AS-CMP-STU-001-学生档案.md) · [SDD](./AppService-SDD/AS-CMP-STU-001-学生档案-SDD.md) |
| **AS-CMP-STF-001** | `CampusStaffAppService` | 教职工档案 | STF | [AS](./AppService/AS-CMP-STF-001-教职工档案.md) · [SDD](./AppService-SDD/AS-CMP-STF-001-教职工档案-SDD.md) |
| **AS-CMP-ARCH-001** | `CampusArchiveQueryAppService` | 无页面 Client | ARCH | [AS](./AppService/AS-CMP-ARCH-001-档案共通.md) · [SDD](./AppService-SDD/AS-CMP-ARCH-001-档案共通-SDD.md) |

---

## 四、公费 AS（P0～P1）

| 编码 | AppService 类名 | 主页面 | 优先级 | 契约 | 现状 |
|------|-----------------|--------|--------|------|------|
| **AS-CMP-GF-CFG-001** | `CampusGfConfigAppService` | 公费配置 | P0 | [AS](./AppService/AS-CMP-GF-CFG-001-公费配置.md) | 部分 |
| **AS-CMP-GF-RATIO-001** | `CampusGfRatioCatalogAppService` | 目录比例 | P0 | [AS](./AppService/AS-CMP-GF-RATIO-001-目录比例.md) | ❌ |
| **AS-CMP-GF-RATIO-002** | `CampusGfRatioItemAppService` | 项目比例 | P0 | [AS](./AppService/AS-CMP-GF-RATIO-002-项目比例.md) | ❌ |
| **AS-CMP-GF-APR-001** | `CampusGfApprovalAppService` | 待办/已办 | P0 | [AS](./AppService/AS-CMP-GF-APR-001-比例审批.md) | ✅ nenu-vue-his |
| **AS-CMP-GF-APR-002** | `CampusGfRequestRecordAppService` | 申请记录 | P0 | [AS](./AppService/AS-CMP-GF-APR-002-申请记录.md) | ✅ nenu-vue-his |
| **AS-CMP-GF-CALC-001** | `CampusGfChargeCalcAppService` | 无页面 | P0 | [AS](./AppService/AS-CMP-GF-CALC-001-收费试算.md) | ❌ |
| **AS-CMP-GF-FIN-001** | `CampusGfSettlementAppService` | 公费交账 | P1 | 待补 | ❌ |
| **AS-CMP-OPEN-001** | `CampusGfOpenApiAppService` | Open API | P0 | [AS](./AppService/AS-CMP-OPEN-001-OpenAPI.md) | ✅ nenu-vue-his |

---

## 五、同步与报表（P2）

| 编码 | AppService 类名 | 主页面 | IF 族 | 优先级 |
|------|-----------------|--------|-------|--------|
| **AS-CMP-SYNC-001** | `CampusDataSyncAppService` | 多校区同步（可选） | SYNC | P2 |
| **AS-CMP-RPT-001** | `CampusReportAppService` | 校医院报表 | RPT | P2 |

---

## 六、修订记录

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.3 | 2026-07-19 | 链接 AppService/SDD 契约正文；P0 可成码 |
| v1.2 | 2026-06-19 | 文档编号对齐 |
| v1.1 | 2026-06-19 | 档案变更记录；SYNC 多校区 |
| v1.0 | 2026-06-19 | 首版：15 AS |
