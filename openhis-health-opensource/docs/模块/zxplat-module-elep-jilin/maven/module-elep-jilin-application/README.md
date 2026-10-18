# elep-jilin-application

ELEP UI / hisproxy 应用层：`ElepPrescriptionAppService`、多步 Workflow、本地 PDF。

## 职责边界

| 允许 | 禁止 |
|------|------|
| `hisproxy/*AppService` HTTP 入口 | 依赖 bridge 编译（注入 domain access） |
| `prescription/workflow/` 多步编排 | 包名 `orchestration` |
| `prescription/pdf/` 本地 PDF（A0001→PDF→A0002） | 在 workflow 外散落 Gateway 注入 |
| Flyway `elep` 迁移 | |

## 包结构

```text
com.zxplat.elep.application/
  hisproxy/                 ElepPrescriptionAppService
  prescription/
    workflow/               ElepPrescriptionOrchestrator
    pdf/                    ElepPdfTemplateService
    support/                Clone、History、UploadLog
  catalog/                  ElepCatalogSearchAppService
  client/                   JilinElepPrescriptionClientImpl
  config/                   ElepAutoConfiguration
```

出站 A000x Invoker 在 **bridge** `prescription/invoker/`；workflow 注入 domain `ElepA000xInvokeAccess`。

## 命名·脚本

| 类型 | 包 | 示例 |
|------|-----|------|
| HTTP 入口 | `hisproxy/` | `ElepPrescriptionAppService` |
| 多步编排 | `prescription/workflow/` | `ElepPrescriptionOrchestrator` |
| 本地 PDF | `prescription/pdf/` | `ElepPdfTemplateService` |
| domain result | 无 `Dto` 后缀 | `ElepPrescriptionDraft` |

| 阶段 | 脚本 | 状态 |
|------|------|------|
| P1–P3 | `scripts/elep-p1-app-rename.ps1` | ✅ N/A |
| P7–P8 | `scripts/elep-p7-p8-app.ps1` | ✅ |
| P13 | `scripts/elep-p13-rename-dto.ps1` | ✅ |

见 [`../OPTIMIZATION-PLAYBOOK.md`](../OPTIMIZATION-PLAYBOOK.md).

## 与 CHS 差异

| 维度 | CHS application | ELEP application |
|------|-----------------|------------------|
| 出站 Invoker | 部分仍在 application | 已迁 **bridge** `{cap}/invoker/` |
| 多步链 | 挂号/结算步骤链 | A0001→PDF→A0002→A0003 |
| 本地生成 | 无 | `prescription/pdf/` 模板填充 |
| UI 入口 | health-application | `hisproxy/*AppService` |
