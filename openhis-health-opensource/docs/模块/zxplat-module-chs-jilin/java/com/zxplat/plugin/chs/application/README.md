# CHS application 层

> 对齐 health：`com.zxplat.plugin.chs.application.{boundedContext}/{feature}/service|request|response|mapper|invoker|support/`  
> **AS/SDD**：`docs/模块/zxplat-module-chs-jilin/AppService/` · `AppService-SDD/`

## 包结构（v3.6）

```text
com.zxplat.plugin.chs.application/
  catalog/          nationalcatalog | download | mapping | workbench | shared
  inventory/
  reconciliation/   request/ + response/
  medicalinfo/      AppService 直调 Invoker（同页多 query）
  foundation/       person | sign | session | platformdict
  config/
```

**页内子包（按需，无 orchestration 中间层）**：

```text
{feature}/
  service/     *AppService
  request/ response/ mapper/   # HTTP 契约
  invoker/     唯一注入 InsuranceGatewayClient（单 infno）
  support/     工具、多步 *Workflow；内部 dto/ 按需
```

跨页共享：`catalog/shared/support/`（如 `CatalogFileParser`，对账 3202 亦用）。

## 编排规则

| 类型 | 职责 |
|------|------|
| `*AppService` | HTTP 入口；单 infno 可直调 Invoker |
| `invoker/` | 唯一注入 `InsuranceGatewayClient` |
| `support/*Workflow` | 多步流程（可选，仅真有编排时） |
| `support/` | 页内工具、平台上下文、文件解析 |

**不做**：为每个 AppService 强制插入 Orchestrator/Workflow 转发层。

## JavaDoc 约定

| 层级 | 类注释 | 方法注释 |
|------|--------|----------|
| `*AppService` | API 前缀 + 页职责（2 行+） | 每个 public 方法：infno 或「本地查询」 |
| `*Invoker` | `【infno】` + gateway 出站 + DB/文件副作用 | `invoke` 一行说明 |
| `*Workflow` | 多步流程摘要 | 各 public 入口方法 |
| `request/response` | HTTP 契约用途 | 非 obvious 字段 |
| `mapper/support` | 映射方向或工具范围 | public 方法按需 |

标杆示例：`catalog/nationalcatalog/service/InsuranceNationalCatalogAppService.java`、`medicalinfo/service/InsuranceMedicalInfoQueryAppService.java`（直调 Invoker）。

## 落地记录

| 阶段 | 内容 | 状态 |
|------|------|------|
| P1 | `reconciliation/dto` → `request/response` | ✅ |
| P2 | `catalog/mapping` → `CatalogMappingUploadWorkflow` + health support | ✅ |
| P3 | 去掉包名 `appservice` 段 | ✅ |
| P4 | foundation `dto`→`request/response`；删除 medicalinfo 空壳编排 | ✅ |
| P5 | `foundation/` 包聚合；catalog download 内部 dto | ✅ |
| P6 | codegen + 22 文档 | ✅ |
| P7 | 取消强制 Orchestrator 层；`*Workflow` 迁入 `support/` | ✅ |
| P8 | 去掉 `orchestration/` 目录段 → 页内 `invoker/` + `support/` | ✅ |
| P11 | medicalinfo cap Invoker 迁至 bridge；application 仅依赖 domain access | ✅ |

脚本：`scripts/p1-p3-application-migrate.ps1` · `scripts/p4-foundation-dto-migrate.ps1` · `scripts/p5-migrate.ps1` · `scripts/p7-orchestrator-to-workflow.ps1` · `scripts/p8-flatten-orchestration.ps1` · `scripts/chs-p11-medicalinfo-invoker.ps1`
