# CHS · 应用服务详细设计（SDD）索引

> **母文档**：`../AppService/AS-CHS-*.md`（页面边界 / IF 契约）  
> **代码根包**：`com.zxplat.plugin.chs.application`  
> **HTTP**：`POST /api/app/{serviceName}/{methodName}`（`AppServiceControllerRegistrar`）  
> **事务**：写路径由 `AppServiceAspect` 统一管理 UoW；AppService **禁止** `@Transactional`

---

## SDD 清单

| 编码 | SDD | AppService | HTTP 前缀 |
|------|-----|------------|-------------|
| AS-CHS-CAT-001 | [国家目录管理-SDD](./AS-CHS-CAT-001-国家目录管理-SDD.md) | `InsuranceNationalCatalogAppService` | `insuranceNationalCatalog` |
| AS-CHS-CAT-002 | [目录文件下载-SDD](./AS-CHS-CAT-002-目录文件下载-SDD.md) | `InsuranceCatalogDownloadAppService` | `insuranceCatalogDownload` |
| AS-CHS-CAT-003 | [院内对码上传-SDD](./AS-CHS-CAT-003-院内对码上传-SDD.md) | `InsuranceCatalogMappingAppService` | `insuranceCatalogMapping` |
| AS-CHS-CAT-004 | [对码Workbench-SDD](./AS-CHS-CAT-004-对码Workbench-SDD.md) | `InsuranceCatalogWorkbenchAppService` | `insuranceCatalogWorkbench` |
| AS-CHS-INV-001 | [进销存运维-SDD](./AS-CHS-INV-001-进销存运维-SDD.md) | `InsuranceInventoryAppService` | `insuranceInventory` |
| AS-CHS-REC-001 | [对账清算中心-SDD](./AS-CHS-REC-001-对账清算中心-SDD.md) | `InsuranceReconciliationAppService` | `insuranceReconciliation` |
| AS-CHS-MED-001 | [医保信息查询-SDD](./AS-CHS-MED-001-医保信息查询-SDD.md) | `InsuranceMedicalInfoQueryAppService` | `insuranceMedicalInfoQuery` |
| AS-CHS-AUD-001 | [事前提醒试调-SDD](./AS-CHS-AUD-001-事前提醒试调-SDD.md) | `InsuranceDetailAuditTryAppService` | `insuranceDetailAuditTry` |
| AS-CHS-FND-001 | [人员读卡-SDD](./AS-CHS-FND-001-人员读卡-SDD.md) | `InsurancePersonAppService` | `insurancePerson` |
| AS-CHS-FND-002 | [医保签到-SDD](./AS-CHS-FND-002-医保签到-SDD.md) | `InsuranceSignAppService` | `insuranceSign` |
| AS-CHS-FND-003 | [平台字典同步-SDD](./AS-CHS-FND-003-平台字典同步-SDD.md) | `InsurancePlatformDictAppService` | `insurancePlatformDict` |

**兼容**：`InsuranceSessionAppService` → 见 [AS-CHS-FND-002-SDD](./AS-CHS-FND-002-医保签到-SDD.md) §4。

---

## 包结构约定（application）

```text
application/{boundedContext}/
  {feature}/
    service/Insurance*AppService.java
    request/  response/  mapper/
    invoker/    # 唯一注入 InsuranceGatewayClient
    support/    # 多步 *Workflow、页内工具
  shared/support/             # catalog 跨页共享
```

---

## 修订记录

| 版本 | 日期 | 说明 |
|------|------|------|
| v0.1 | 2026-07-11 | 初稿：11 份 SDD + 索引 |
| v0.2 | 2026-07-11 | P1–P3：包路径去掉 `appservice`；reconciliation 迁 `request/response` |
| v0.3 | 2026-07-11 | P4：foundation request/response；medicalinfo orchestrator |
| v0.4 | 2026-07-11 | P5：`foundation/` 包聚合；catalog download `workflow/dto` |
| v0.5 | 2026-07-11 | P7：`*Workflow` 迁入 `support/`；medicalinfo 直调 Invoker |
| v0.6 | 2026-07-11 | P8：去掉 `orchestration/` → 页内 `invoker/` + `support/` |
