---
doc_type: type_card
---

# ImagingStudyDomainService

| 项 | 值 |
|----|-----|
| 源码 | `com.zxplat.plugin.pacsimaging.domain.imagingstudy.service.ImagingStudyDomainService` |
| 路径 | `zxplat-plugin/zxplat-module-pacs-imaging/module-pacs-imaging-domain/src/main/java/com/zxplat/plugin/pacsimaging/domain/imagingstudy/service/ImagingStudyDomainService.java` |
| 分层 | service |

## 关键写方法

| 方法 | 说明 |
|------|------|
| `ensureStudy` | 按工单幂等确保 ImagingStudy |
| `bindFromCStore` | C-STORE 到达后绑定 StudyUID |

## 说明

影像检查索引写侧（DM-PACS-003 / BR-IMG-*）。

## 关联

- **领域模型**：[DM-PACS-003](../../DM-PACS-003-影像索引与阅片端点.md) · [DM-PACS-IM-001](../../DM-PACS-IM-001-ImagingStudy-影像检查索引.md)
- **领域索引**：[../../说明.md](../../说明.md)
