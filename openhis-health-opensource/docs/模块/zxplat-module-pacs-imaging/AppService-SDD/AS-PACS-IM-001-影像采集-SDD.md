# AS-PACS-IM-001 影像采集 · SDD

> **编码**：AS-PACS-IM-001-SDD · **版本**：v1.2 · **日期**：2026-09-22  
> **对应 AS**：AS-PACS-IM-001 v1.2  
> **实现口径**：**已实现**（`PacsImageCaptureAppService`；真实 Orthanc/MinIO 见序号 57；C-STORE 回调 + MWL 查询见序号 58）。`PacsWorkItem*` / ImagingStudy / Media 领域均已有。

---

## 1. 实现边界

| 项 | 内容 |
|----|------|
| **模块** | `zxplat-module-pacs-imaging` · `module-pacs-imaging-application` |
| **边界** | 采集列表、Study 绑定、STOW、C-STORE 回写、MinIO 关键图 |
| **编排权威** | AS-PACS-IM-001 §2、§4 |
| **方法权威** | DM-PACS-001 §6；DM-PACS-003 ImagingStudy/Media |
| **实现方式** | `PacsImageCaptureAppService`；HTTP 一律 POST |

| 项 | 约定 |
|----|------|
| **事务** | 索引写入与 `markHasMedia` 同一事务；对象存储失败则回滚索引 |
| **像素** | DCM 在 Orthanc；关键图在 MinIO `key-images` |

---

## 2. 代码结构

### 2.1 包路径

```text
com.zxplat.plugin.pacsimaging.application.capture
  service/PacsImageCaptureAppService.java
  request/ListPendingCaptureRequest.java
  request/GetCaptureDetailRequest.java
  request/EnsureStudyRequest.java
  request/UploadDicomRequest.java
  request/OnCStoreReceivedRequest.java
  request/SaveKeyImageRequest.java
  request/DeleteKeyImageRequest.java
  request/ListKeyImagesRequest.java
  request/ListModalityWorklistRequest.java
  response/ListPendingCaptureResponse.java
  response/GetCaptureDetailResponse.java
  response/EnsureStudyResponse.java
  response/UploadDicomResponse.java
  response/OnCStoreReceivedResponse.java
  response/SaveKeyImageResponse.java
  response/DeleteKeyImageResponse.java
  response/ListKeyImagesResponse.java
  response/ListModalityWorklistResponse.java
  dto/PendingCaptureRowDto.java
  dto/KeyImageRowDto.java
  dto/ModalityWorklistRowDto.java
  mapper/PacsImageCaptureMapper.java

com.zxplat.plugin.pacsimaging.application.inbound
  PacsImagingCStoreNotifyController.java
```

> 说明：手工 `@Component` Mapper（无 MapStruct `*MapperImpl`）。CAP-10 生产入站走 integration Controller；AppService `onCStoreReceived` 仍可被同进程调用。
### 2.2 依赖

| 类型 | 用途 |
|------|------|
| `PacsWorkItemQueryService` | 已有，`getDetail` / `searchCompleted` / `searchPendingExecution` / `findByAccessionNumber` |
| `PacsWorkItemDomainService` | 已有，`markHasMedia` |
| `ImagingStudyDomainService` / `MediaDomainService` | 已有 |
| `PatientQueryService` | 可选姓名；失败不阻断 MWL |
| `OrthancStowClient` | 应用端口；`orthanc.enabled` 时 `HttpOrthancStowClient` |
| `MinioKeyImageStore` | 应用端口；`minio.enabled` 时真实客户端 |

### 2.3 枚举与字段

| 类别 | 代码实现锚点 | DM 追溯锚点 | 说明 |
|------|--------------|-------------|------|
| 工单已检 | `PacsWorkItemDetailQueryModel.statusEnum` | DM-PACS-001 §3 `statusEnum` | 采集前须 completed。目标态字段名与 QueryModel 一致 |
| 有图标志 | `MarkPacsWorkItemHasMediaCommand.hasMedia` | DM-PACS-001 §6 `markHasMedia` | 已有命令 |
| 关键图对象键 | `KeyImageRowDto.objectKey` | DM-PACS-003 §3 `Media.objectKey` | 已实现 |

---

## 3. 按接口 ID 命名展开

### 3.1 方法白名单

| IF | 方法 | 中文 | HTTP |
|----|------|------|------|
| CAP-01 | `listPendingCapture` | 待采集列表 | `POST /api/app/pacsImageCapture/listPendingCapture` |
| CAP-03 | `getCaptureDetail` | 采集详情 | `POST /api/app/pacsImageCapture/getCaptureDetail` |
| CAP-06 | `ensureStudy` | 绑定 Study | `POST /api/app/pacsImageCapture/ensureStudy` |
| CAP-07 | `listModalityWorklist` | MWL 待扫查询 | `POST /api/app/pacsImageCapture/listModalityWorklist` |
| CAP-09 | `uploadDicom` | 上传 DCM | `POST /api/app/pacsImageCapture/uploadDicom` |
| CAP-10 | `onCStoreReceived` | C-STORE 回写 | 入站 `POST /api/integration/pacs-imaging/cstore-notify`（亦可 App 同名） |
| CAP-13 | `saveKeyImage` | 保存关键图 | `POST /api/app/pacsImageCapture/saveKeyImage` |
| CAP-14 | `deleteKeyImage` | 删除关键图 | `POST /api/app/pacsImageCapture/deleteKeyImage` |
| CAP-15 | `listKeyImages` | 关键图列表 | `POST /api/app/pacsImageCapture/listKeyImages` |

### 3.1.1 编排约定

| 阶段 | 含义 | 失败时 |
|------|------|--------|
| Gate | 参数、已检 | 停止 |
| Pre | 读工单 | 不进入 Cmd |
| Cmd | 领域写 | 回滚 |
| Asm | 装配 DTO | — |

写 IF 的 ImagingStudy/Media Cmd **已落地**；应用层经端口 stub 编排 CAP-09/10/13。

### 3.2 CAP-13 `saveKeyImage`（保存关键图）

**Request**：`workItemId` Long 必填；`contentBase64` String 必填；`contentType` String 必填（image/jpeg 或 image/png）。

**Response**：`mediaId` Long；`objectKey` String；`hasMedia` Boolean。

| 序号 | 阶段 | 调用 | 说明 |
|------|------|------|------|
| 1 | Gate | 参数非空、类型为图片 | |
| 2 | Pre | `PacsWorkItemQueryService#getDetail` | 状态须 completed，否则 `PACS_WORK_ITEM_INVALID_STATUS` |
| 3 | — | `MinioKeyImageStore#put` | 桶 `key-images`；失败则不写库 |
| 4 | Cmd | `MediaDomainService#saveKeyImage` | 写库点 |
| 5 | Cmd | `PacsWorkItemDomainService#markHasMedia(MarkPacsWorkItemHasMediaCommand)` | 写库点，hasMedia=true |
| 6 | Asm | `PacsImageCaptureMapper#toSaveKeyImageResponse` | |

CAP-14 对称：先删 MinIO 再删 Media，若已无 Study 且无剩余关键图则 `markHasMedia(false)`。

### 3.3 CAP-06 / CAP-09 / CAP-10

| 序号 | 阶段 | 调用 | 说明 |
|------|------|------|------|
| 1 | Pre | `PacsWorkItemQueryService#getDetail` | CAP-10 按 accession 查找 `findByAccessionNumber` |
| 2 | — | `OrthancStowClient#store` | **仅 CAP-09**（默认 Noop stub） |
| 3 | Cmd | `ImagingStudyDomainService#ensureStudy` 或 `#bindFromCStore` | 同一 StudyUID 幂等 |
| 4 | Cmd | `PacsWorkItemDomainService#markHasMedia` | hasMedia=true |

### 3.4 只读

CAP-01：`PacsWorkItemQueryService#searchCompleted`，再按 Study/Media 是否存在过滤。  
CAP-03：`getDetail` + Study/Media 摘要。  
CAP-07：`searchPendingExecution` + 可选姓名；不生成 `.wl`。  
CAP-15：`MediaQueryService#listByWorkItemId` 并为每条签发预签名 URL。

---

## 4. QueryService 与领域事件

`markHasMedia` 在值变化时发布 `PacsWorkItemMediaFlagUpdatedEvent`（DM-PACS-001 已有）。列表读侧跟随工单查询，不另建投影。

---

## 5. 写库顺序

| IF | Cmd | 顺序 |
|----|-----|------|
| CAP-13 | MinIO put → Media 插入 → markHasMedia | 同一事务含后两步；put 失败不写库 |
| CAP-14 | MinIO delete → Media 删除 → 条件 markHasMedia | 同上 |
| CAP-06/09/10 | ensureStudy/bind → markHasMedia | 同一事务；STOW 失败不写库 |

---

## 6. 非功能

| 项 | 落点 |
|----|------|
| 幂等 | CAP-10 以 StudyUID+workItemId 唯一 |
| 审计 | 保存/删除关键图记 workItemId 与 objectKey，不记图像字节 |

---

## 7. 测试要点

| 用例 | 层级 | 优先级 |
|------|------|--------|
| 未检不能上传 | 应用 | P0 |
| 重复 C-STORE 不插第二行 Study | 领域 + 应用回调 | P0 |
| 回调 Token 错误 → 401 | Controller | P0 |
| MWL 列表待执行态 | 应用 | P0 |
| 关键图 URL 与 OHIF Study URL 分离 | 应用 | P0 |

---

## 8. 变更历史

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.0 | 2026-09-21 | D-10。ImagingStudy/Media 调用为目标态，成码前关闭 AS §3 |
| v1.1 | 2026-09-22 | 实现口径改为已实现（端口 stub）；手工 Mapper；Gap 关闭 |
| v1.2 | 2026-09-22 | 序号 58：入站 cstore-notify；listModalityWorklist；CAP-10 关闭骨架 |
