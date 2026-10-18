# 国家目录管理 · 应用服务详细设计（SDD）

> 编码：**AS-CHS-CAT-001-SDD** · v0.1 · 2026-07-11  
> **应用服务编号**：AS-CHS-CAT-001

## 1. 边界

| 项 | 内容 |
| --- | --- |
| AppService | `InsuranceNationalCatalogAppService` |
| HTTP | `/api/app/insuranceNationalCatalog` |
| infno | 1312 |

## 2. 包结构

```text
catalog/nationalcatalog/
  service/InsuranceNationalCatalogAppService.java
  request/  response/  mapper/
  invoker/Cat1312Invoker.java
```

## 3. HTTP ↔ IF

| 方法 | IF | 编排 |
| --- | --- | --- |
| `queryNationalCatalogPage` | IF-CHS-CAT-001-R-01 | `YbNationalCatalogQueryService` + Mapper |
| `refreshFromPlatform1312` | IF-CHS-CAT-001-W-01 | `Cat1312Invoker` → domain upsert |

## 4. 事务

读路径只读；`refreshFromPlatform1312` 写路径经 `AppServiceAspect` REQUIRED。
