# 院内对码上传 · 应用服务详细设计（SDD）

> 编码：**AS-CHS-CAT-003-SDD** · v0.1 · 2026-07-11  
> **应用服务编号**：AS-CHS-CAT-003

## 1. 边界

| 项 | 内容 |
| --- | --- |
| AppService | `InsuranceCatalogMappingAppService` |
| HTTP | `/api/app/insuranceCatalogMapping` |
| infno | 3301、3302 |

## 2. 包结构

```text
catalog/mapping/
  service/InsuranceCatalogMappingAppService.java   # 薄入口
  invoker/Cat3301Invoker.java, Cat3302Invoker.java
  mapper/CatalogPlatformMapper.java
  support/CatalogMappingUploadWorkflow.java
  support/MedicationCatalogMappingSuggestWorkflow.java
  support/CatalogMappingHealthAssemblySupport.java
  support/CatalogMappingRowSupport.java
```

## 3. HTTP ↔ IF（摘要）

| 方法族 | IF | infno |
| --- | --- | --- |
| `suggest*CatalogMapping` | IF-CHS-CAT-003-R-* | —（本地） |
| `upload*Catalog3301` | IF-CHS-CAT-003-W-01 | 3301 |
| `revoke*Catalog3302` | IF-CHS-CAT-003-W-02 | 3302 |

品类维度：medication / activity / device，共用 Invoker，Command 不同。

## 4. 事务

3301/3302 写路径单事务；成功后 domain 回写院内对码状态。
