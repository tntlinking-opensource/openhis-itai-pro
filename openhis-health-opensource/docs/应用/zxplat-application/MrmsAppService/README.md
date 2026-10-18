# MrmsAppService · 病案管理

> **定位**：ZXPlat **health 内嵌子域**（非常驻 Extension、无 starter）  
> **目标代码**：`zxplat/zxplat-domain/.../domain/mrms` + `health-application/.../application/mrms`  
> **迁移说明**：[MrmsAppService/README.md](../../health/MrmsAppService/README.md) · [模块化开发规范.md §7](../01-架构/模块化开发规范.md)  
> **成码规范**：[07-成码前Checklist.md](./07-成码前Checklist.md) · [`zxplat（病案/MRMS）-codegen.mdc`](../../../../.cursor/rules/zxplat（病案/MRMS）-codegen.mdc)  
> **版本**：v4.3 · **日期**：2026-08-13  
>
> **阶段（2026-09-01）**：**院内病案首页已齐**；**结算清单独立预览 + 国临/医保双码策略**已成码（见 [10](./10-病案首页交付任务清单.md) 上报交互 / 双码表）；医保上报联调见 §6～§8。

---

## 文档索引

| 文档 | 用途 |
|------|------|
| [00-功能清单.md](./00-功能清单.md) | 功能范围、Wave、MVP |
| [01-概要设计.md](./01-概要设计.md) | HLD：边界、表结构、领域服务 |
| [02-交互说明.md](./02-交互说明.md) | **页面 ↔ AppService ↔ API**（成码主入口） |
| [03-病案首页-详细设计.md](./03-病案首页-详细设计.md) | 首页字段、4401 映射 |
| [04-归档与质控-详细设计.md](./04-归档与质控-详细设计.md) | 质控、归档、卷宗 PDF、逾期 |
| [05-对外上报-详细设计.md](./05-对外上报-详细设计.md) | 医保 4401 |
| [06-病案首页-前端设计.md](./06-病案首页-前端设计.md) | openhis-pro-ui 组件 |
| [07-成码前Checklist.md](./07-成码前Checklist.md) | 成码门禁 |
| [08-Extension与外部依赖.md](./08-Extension与外部依赖.md) | MRMS Extension 索引 |
| [09-文档清单.md](./09-文档清单.md) | 维护索引 |
| [10-病案首页交付任务清单.md](./10-病案首页交付任务清单.md) | **交付：首页 + 一键上报(4401/4101A) + 4102/4103** |
| [11-卫统N041导出-字段对照.md](./11-卫统N041导出-字段对照.md) | 卫统 N041 CSV（232 列）字段对照与缺口 |
| [12-HQMS导出-字段对照.md](./12-HQMS导出-字段对照.md) | HQMS CSV（811 列）字段对照与缺口 |

---

## 代码结构

```text
zxplat/
  zxplat-domain/.../domain/mrms/
    caserecord/    Wave 1 主档 + 首页
    qc/            Wave 2 质控
    borrowrequest/ upload/   Wave 3
  health-integration-contracts/.../integration/
    EmrDocumentExtension, MedcasHomepageUploadExtension, …
  health-application/.../application/mrms/
    caserecord/{homepage,coding,volume}/ qcworkbench/ borrow/ outpatientemr/ inpatientemr/ upload/{medcas,hqms,weistat}/
    caserecord/homepage/support/   HomepageOpenPdfRenderer、MrmsEmrAdmissionConditionResolverAdapter
    caserecord/volume/support/     VolumePdfComposer
    application.shared.pdf/        PdfMergeUtil（通用）
    upload/medcas/support/         InsuranceEncounterLoader、InsuranceEncounterContext、Upload4401Validator
    upload/weistat/                MrmsWeistatExportAppService（N041 CSV；国基 UI 开）
    upload/hqms/                   MrmsHqmsExportAppService（HQMS CSV；国基 UI 开）
  zxplat-module-emr/module-emr-bridge/.../integration/emr/mrms/   EmrDocumentExtensionImpl（-Pwith-emr）
  zxplat-domain/.../V77～V86__mrms_*.sql
  zxplat-domain/.../V25__mrms_menu_homepage_fusion_medcas_ops.sql   菜单归拢（含门诊 path）
```

- **离院建档**：`InpatientDoctorStationAdviceAppService` afterCommit → `CaseRecordDomainService.bootstrapIfAbsent`
- **Flyway**：`V77～V86` 表结构；`V25` 菜单归拢（homepage/volume/medcas-ops + 门诊 path，`whale_menus`）
- **EMR 联调**：`mvn compile -Pwith-emr -pl zxplat/health-app -am -DskipTests`

---

## 编译

```powershell
cd D:\0.product\02.Whale\zxplat
mvn compile -pl zxplat/health-app -am -DskipTests
```
