# 对码 Workbench · 应用服务详细设计（SDD）

> 编码：**AS-CHS-CAT-004-SDD** · v0.2 · 2026-07-29  
> **应用服务编号**：AS-CHS-CAT-004

## 1. 边界

| 项 | 内容 |
| --- | --- |
| AppService | `InsuranceCatalogWorkbenchAppService` |
| HTTP | `/api/app/insuranceCatalogWorkbench` |
| 平台 | 本地待对码分页；**1317** 出站经 `Cat1317Invoker` |

## 2. 包结构

```text
catalog/workbench/
  service/InsuranceCatalogWorkbenchAppService.java
  request/  response/  mapper/
domain/catalog/
  access/Cat1317Invoker.java
  entity/YbInstitutionCatalogMatch.java
  service/YbInstitutionCatalogMatch*
bridge/catalog/invoker/Cat1317Invoker.java
gateway/scheme/catalog/{model,assembler,parser}/Cat1317*
```

## 3. HTTP ↔ IF

| 方法 | IF | 编排 |
| --- | --- | --- |
| `pagePendingMappings` | IF-CHS-CAT-004-R-01 | domain 待对码分页 |
| `queryInstitutionCatalogMatchPage` | IF-CHS-CAT-004-R-02 | `YbInstitutionCatalogMatchQueryService` |
| `refreshFromPlatform1317` | IF-CHS-CAT-004-W-02 | `Cat1317Invoker` → upsert + 正向回写 HIS |

3301 批量上传由前端逐条调 **AS-CHS-CAT-003**。

## 4. 事务

读路径只读；`refreshFromPlatform1317` 写路径经 `AppServiceAspect` REQUIRED。
