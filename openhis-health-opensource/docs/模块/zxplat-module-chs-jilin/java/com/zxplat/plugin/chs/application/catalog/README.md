# catalog 应用层包结构

医保目录相关 UI 按 **主页面 = 一个 AppService** 组织，对齐 `health-application` 的页面切片习惯。

**文档**：AS/SDD 见 `docs/模块/zxplat-module-chs-jilin/AppService/` 与 `AppService-SDD/`（CAT-001～004）。

## 页面 ↔ API ↔ 包

| 主页面 | AppService | HTTP 前缀 |
|--------|------------|-----------|
| 国家目录管理 | `nationalcatalog.service.InsuranceNationalCatalogAppService` | `/api/app/insuranceNationalCatalog/*` |
| 目录文件下载 | `download.service.InsuranceCatalogDownloadAppService` | `/api/app/insuranceCatalogDownload/*` |
| 院内对码上传/推荐 | `mapping.service.InsuranceCatalogMappingAppService` | `/api/app/insuranceCatalogMapping/*` |
| 对码 Workbench | `workbench.service.InsuranceCatalogWorkbenchAppService` | `/api/app/insuranceCatalogWorkbench/*` |

## 子包约定

每个页面切片：

```text
{页面}/
  service/     *AppService（HTTP 入口，仅注入 domain access / 无 gateway 的 support）
  request/     入参
  response/    出参
  mapper/      Request ↔ Domain 映射
  support/     无 gateway 的页内编排（如 MedicationCatalogMappingSuggestWorkflow）
```

**gateway 出站**已迁至 `bridge/catalog/`（P13）；application 通过 domain access 端口调用：

| 端口 | 用途 |
|------|------|
| `Cat1312Invoker` | 1312 国家目录刷新、对码推荐补库 |
| `CatalogDownloadAccess` | 13xx 文件下载探测/启动/进度 |
| `CatalogMappingUploadAccess` | 3301/3302 对码上传撤销 |

## 调用链示例（目录下载）

```text
InsuranceCatalogDownloadAppService
  → CatalogDownloadAccess (domain)
      → CatalogDownloadWorkflow (bridge)
          → CatalogDownloadPhase1Workflow
              → Cat13xxInvoker (1301)
              → Cat9102FileDownloadAccess (9102)
          → CatalogDownloadImportService (本地入库)
```

详见 `bridge/catalog/README.md`。
