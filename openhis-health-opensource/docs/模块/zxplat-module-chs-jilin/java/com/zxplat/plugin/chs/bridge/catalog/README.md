# catalog bridge

医保目录出站（1312、13xx/9102 文件下载、3301/3302 对码上传撤销）。

## 包结构

```text
catalog/
  support/   CatalogPlatformSupport、CatalogFileParser*、CatalogDownloadZipCache、CatalogMappingRowSupport
  invoker/   Cat1312、Cat13xx、Cat3301、Cat3302、Cat9102FileDownloadAccessImpl
  workflow/  CatalogDownloadWorkflow、CatalogDownloadPhase1Workflow、CatalogMappingUploadWorkflow
  mapper/    CatalogPlatformMapper（1312 平台行 → domain upsert）
  support/dto/  目录下载 Phase1 内部 DTO
```

## infno 索引

| infno | 实现 | 说明 |
|-------|------|------|
| 1312 | `Cat1312Invoker` | 国家目录分页拉取 + upsert |
| 1301/1305… | `Cat13xxInvoker` | 文件元数据查询（workflow 内部） |
| 9102 | `Cat9102FileDownloadAccessImpl` | ZIP 下载（domain access 共享） |
| 3301 | `Cat3301Invoker` | 目录对照上传（workflow 内部） |
| 3302 | `Cat3302Invoker` | 目录对照撤销（workflow 内部） |

## 调用链

```text
InsuranceNationalCatalogAppService (application)
  → Cat1312Invoker (domain access)
  → Cat1312Invoker (bridge)

InsuranceCatalogDownloadAppService
  → CatalogDownloadAccess (domain access)
  → CatalogDownloadWorkflow (bridge)
      → CatalogDownloadPhase1Workflow → Cat13xxInvoker + Cat9102FileDownloadAccess
      → CatalogDownloadImportService

InsuranceCatalogMappingAppService
  → CatalogMappingUploadAccess (domain access)
  → CatalogMappingUploadWorkflow (bridge) → Cat3301/Cat3302Invoker
  → MedicationCatalogMappingSuggestWorkflow (application, 无 gateway) → Cat1312Invoker
```

脚本：`scripts/chs-p13-catalog-invoker.ps1`
