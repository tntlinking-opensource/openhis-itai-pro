# DM-PACS-003 影像索引、关键图与阅片端点

> **限界上下文**：影像扩展（`zxplat-module-pacs-imaging`）  
> **聚合**：ImagingStudy、Media、ViewerEndpoint（三个独立聚合，同文档）  
> **版本**：v1.0 · **日期**：2026-09-21  
> **输入**：[AS-PACS-IM-001](../../../模块/zxplat-module-pacs-imaging/AppService/AS-PACS-IM-001-影像采集.md)、[AS-PACS-IM-002](../../../模块/zxplat-module-pacs-imaging/AppService/AS-PACS-IM-002-影像阅片.md)、[AS-PACS-IM-003](../../../模块/zxplat-module-pacs-imaging/AppService/AS-PACS-IM-003-阅片地址.md)、[04-第三方组件选型](../../../模块/zxplat-module-pacs-imaging/04-第三方组件选型.md)  
> **工单**：[DM-PACS-001](./DM-PACS-001-PacsWorkItem-检查工单.md)（本 DM **不**修改其状态机；只通过 `workItemId` 关联，并由应用层调用已有 `markHasMedia`）

像素不进本模型。DICOM 在 Orthanc，关键图字节在 MinIO 桶 `key-images`，这里只存索引。

---

## 1. 概述

### 1.1 业务定义

一次已检工单最多对应一条 **ImagingStudy**（DICOM Study 索引）。关键图是 **Media** 行，指向 MinIO 对象键。阅片根地址是 **ViewerEndpoint**，按机构与模态匹配，供 OHIF 启动 URL 使用。

### 1.2 核心职责

| 聚合 | 职责 |
|------|------|
| ImagingStudy | 保存 Study/Series UID，幂等绑定到工单 |
| Media | 保存关键图对象键与内容类型；不保存字节 |
| ViewerEndpoint | 保存 OHIF 根地址；同一机构+模态仅一条启用 |

### 1.3 非本聚合职责

| 不在内 | 归属 |
|--------|------|
| 工单状态、检查号生成 | DM-PACS-001 |
| `hasMedia` 标志 | 应用层在本聚合写成功后调用 `PacsWorkItemDomainService#markHasMedia` |
| C-STORE 收图、STOW、预签名 | imaging-adapter（Orthanc / MinIO） |
| 窗宽与像素渲染 | OHIF |

---

## 2. 聚合根与持久化形态

| 表 | 聚合根 | 关系 |
|----|--------|------|
| `pacs_imaging_study` | ImagingStudy | 与工单 1:0..1 |
| `pacs_media` | Media | 与工单 1:N |
| `pacs_viewer_endpoint` | ViewerEndpoint | 按机构/模态配置 |

标识均为 Long 雪花 `id`。模块落点：`zxplat-module-pacs-imaging` 的 imaging-domain，包 `com.zxplat.framework.pacsimaging.domain.imagingstudy`、`…media`、`…viewerendpoint`。Flyway 在该模块，不进 health 的 PACS 工单脚本。

---

## 3. 领域模型

### 3.1 ImagingStudy

| 字段 | 类型 | 必填 | 说明 |
|------|------|:----:|------|
| id | Long | 是 | 主键 |
| workItemId | Long | 是 | 引用 PacsWorkItem；租户内唯一 |
| studyInstanceUid | String | 是 | DICOM Study Instance UID；租户内唯一 |
| seriesInstanceUid | String | 否 | 首条或主 Series；可后补 |
| accessionNumber | String | 否 | 冗余检查号，便于与 C-STORE 对照；真源仍是工单 |
| modalityCode | String | 否 | 来自工单或 DICOM |
| tenantId | Long | 是 | 租户 |
| createdAt / updatedAt | Instant | 是 | 审计 |

### 3.2 Media

| 字段 | 类型 | 必填 | 说明 |
|------|------|:----:|------|
| id | Long | 是 | 主键 |
| workItemId | Long | 是 | 所属工单 |
| objectKey | String | 是 | MinIO `key-images` 对象键；租户内唯一 |
| contentType | String | 是 | `image/jpeg` 或 `image/png` |
| sourceEnum | String | 是 | `KEY_IMAGE` 或 `SCREENSHOT` |
| tenantId | Long | 是 | 租户 |
| createdAt | Instant | 是 | 审计 |

### 3.3 ViewerEndpoint

| 字段 | 类型 | 必填 | 说明 |
|------|------|:----:|------|
| id | Long | 是 | 主键 |
| organizationId | Long | 否 | 空表示全局默认 |
| modalityCode | String | 否 | 空表示该机构（或全局）默认 |
| baseUrl | String | 是 | http(s) 根地址，不含 StudyUID |
| enabled | Boolean | 是 | 是否启用 |
| tenantId | Long | 是 | 租户 |
| createdAt / updatedAt | Instant | 是 | 审计 |

### 3.4 枚举

**MediaSource**：`KEY_IMAGE`、`SCREENSHOT`。存库为 String code。

ViewerEndpoint 无独立状态机。启用冲突由保存方法处理：同一 `(tenantId, organizationId, modalityCode)` 新记录启用时，旧的启用行改为停用。`organizationId`、`modalityCode` 的空值在唯一约束中按同一哨兵比较（实现时空与空相等）。

---

## 4. 聚合关系

```text
PacsWorkItem 1:0..1 ──► ImagingStudy
PacsWorkItem 1:N   ──► Media
ViewerEndpoint 独立配置，IM-002 按机构/模态读取
```

工单删除或作废不级联删除 Orthanc 里的 DICOM。索引行是否保留由后续作废策略决定；本期不提供作废接口。

---

## 5. 领域事件

本三个聚合**不发布**替代 `PacsWorkItemMediaFlagUpdatedEvent` 的事件。有图标志仍只由工单 `markHasMedia` 发出。

| 事件 | 触发 | 消费方 |
|------|------|--------|
| 无新增必须事件 | — | 应用层写完索引后调用工单 `markHasMedia` |

---

## 6. 领域服务方法

### 6.1 ImagingStudyDomainService

| 方法 | 说明 |
|------|------|
| `ensureStudy(EnsureImagingStudyCommand)` | 已有该 `workItemId` 或该 `studyInstanceUid` 则返回原行并可补 Series；否则插入。不接收像素 |
| `bindFromCStore(BindImagingStudyFromCStoreCommand)` | 入参已含 `workItemId` 与 `studyInstanceUid`；语义同 ensureStudy，供 C-STORE 回写。重复通知幂等 |

### 6.2 MediaDomainService

| 方法 | 说明 |
|------|------|
| `saveKeyImage(SaveKeyImageCommand)` | 入参含已写入 MinIO 的 `objectKey`、`contentType`、`sourceEnum`。不上传字节 |
| `deleteKeyImage(DeleteKeyImageCommand)` | 按 `mediaId`+`workItemId` 删除索引。对象删除由应用层在调用前或调用成功后交给 adapter；领域只保证索引删除 |

### 6.3 ViewerEndpointDomainService

| 方法 | 说明 |
|------|------|
| `save(SaveViewerEndpointCommand)` | 校验 `baseUrl` 为 http(s)。若 `enabled=true`，先停用同一租户+机构+模态的其他启用行，再保存 |

### 6.4 QueryService

| 服务 | 方法 |
|------|------|
| `ImagingStudyQueryService` | `findByWorkItemId(workItemId)` → 0..1 |
| `MediaQueryService` | `listByWorkItemId(workItemId)`；`countByWorkItemId(workItemId)` |
| `ViewerEndpointQueryService` | `list(organizationId)`；`resolve(organizationId, modalityCode)` |

`resolve` 顺序：机构+模态 → 机构默认（模态空）→ 全局默认（机构空且模态空）。都没有则空。

**工单侧依赖（DM-PACS-001 读模型增补）**：C-STORE 回写前，应用层用 `PacsWorkItemQueryService#findByAccessionNumber(accessionNumber)` 得到 `workItemId`。仓储已有 `findByAccessionNumberAndTenantId`，查询服务尚未暴露该方法。

---

## 7. 业务规则

| 编号 | 规则 |
|------|------|
| **BR-IMG-01** | 一个工单至多一条 ImagingStudy |
| **BR-IMG-02** | `studyInstanceUid`、`objectKey` 在租户内唯一 |
| **BR-IMG-03** | Media 只接受 jpeg/png |
| **BR-IMG-04** | 领域方法不写 Orthanc、不写 MinIO |
| **BR-IMG-05** | 保存启用端点时，同键旧启用行必须停用 |
| **BR-IMG-06** | `baseUrl` 不得包含 `StudyInstanceUIDs` |

应用层在 `ensureStudy` / `saveKeyImage` 成功后 `markHasMedia(true)`。删除关键图后，若 `countByWorkItemId=0` 且不存在 ImagingStudy，则 `markHasMedia(false)`。

---

## 8. 与配置及其他聚合

| 项 | 口径 |
|----|------|
| 归档 | Orthanc。Study UID 来自 C-STORE 或 STOW 响应 |
| 关键图桶 | MinIO `key-images` |
| 阅片 | OHIF `baseUrl` + DICOMweb，不在本聚合拼像素请求 |
| 开关 | `zxplat.modules.pacs.imaging.enabled` |

---

## 9. FHIR 映射

| 本聚合 | FHIR |
|--------|------|
| ImagingStudy | ImagingStudy（identifier = Study Instance UID） |
| Media | Media 或 DocumentReference（url = 对象键，不把字节放进 Bundle） |
| ViewerEndpoint | 无标准资源；Endpoint 仅作互操作可选映射 |

---

## 10. 修订记录

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.0 | 2026-09-21 | 关闭 AS-PACS-IM-* 对 ImagingStudy/Media/ViewerEndpoint 的文档缺口；领域代码尚未生成 |

> **同目录配置写侧**：模态能力 ModalityCapabilityDomainService 的 save / disable / softDelete 见 [DM-PACS-001](./DM-PACS-001-PacsWorkItem-检查工单.md) §6.1a（本稿不重复展开）。

<!-- MODE-B-METHOD-INVENTORY 2026-09-23 -->

## 附录 · 模态能力交叉引用（2026-09-23）

> `ModalityCapabilityDomainService` 的 `save` / `disable` / `softDelete` 以 **DM-PACS-001** 为准，本稿不重复展开。
