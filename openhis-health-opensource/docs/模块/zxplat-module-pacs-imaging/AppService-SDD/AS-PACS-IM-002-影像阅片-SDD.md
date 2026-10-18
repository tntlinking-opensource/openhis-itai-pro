# AS-PACS-IM-002 影像阅片 · SDD

> **编码**：AS-PACS-IM-002-SDD · **版本**：v1.0 · **日期**：2026-09-21  
> **对应 AS**：AS-PACS-IM-002 v1.0  
> **实现阶段口径**：已实现（2026-09-22）。`launchViewer` 读工单/Study/端点并签发短期 Token。

---

## 1. 实现边界

| 项 | 内容 |
|----|------|
| **模块** | `module-pacs-imaging-application` · `viewer` |
| **边界** | 返回 OHIF 启动 URL + Token；不取像素 |
| **编排权威** | AS-PACS-IM-002 |
| **实现方式** | `PacsViewerAppService`；HTTP 一律 POST |

Token 由应用层签发，不进领域写库。

---

## 2. 代码结构

```text
com.zxplat.plugin.pacsimaging.application.viewer
  service/PacsViewerAppService.java
  request/LaunchViewerRequest.java
  response/LaunchViewerResponse.java
```

| 类型 | 用途 |
|------|------|
| `PacsWorkItemQueryService` | `getDetail` |
| `ImagingStudyQueryService` | 目标态 `findByWorkItemId` |
| `ViewerEndpointQueryService` | 目标态 `resolve` |
| `ViewerTokenIssuer` | 应用层，签短期 Token |

| 类别 | 代码实现锚点 | DM 追溯锚点 | 说明 |
|------|--------------|-------------|------|
| 启动请求 | `LaunchViewerRequest.workItemId` | DM-PACS-001 §3 工单主键 | |
| StudyUID | `LaunchViewerResponse.studyInstanceUid` | DM-PACS-003 §3 ImagingStudy.studyInstanceUid（已实现） | 目标态 |

---

## 3. 按接口 ID 命名展开

| IF | 方法 | 中文 | HTTP |
|----|------|------|------|
| VIEW-01 | `launchViewer` | 打开阅片 | `POST /api/app/pacsViewer/launchViewer` |

**Request**：`workItemId` Long 必填。

**Response**：`launchUrl` String；`studyInstanceUid` String；`expiresAt` String（ISO-8601）。

| 序号 | 阶段 | 调用 | 说明 |
|------|------|------|------|
| 1 | Gate | workItemId 非空 | |
| 2 | Pre | `PacsWorkItemQueryService#getDetail` | 不存在 → `PACS_WORK_ITEM_NOT_FOUND` |
| 3 | Pre | `ImagingStudyQueryService#findByWorkItemId` | **目标态**；空 → `PACS_IMAGING_STUDY_NOT_FOUND` |
| 4 | Pre | `ViewerEndpointQueryService#resolve` | **目标态**；空 → `PACS_VIEWER_ENDPOINT_NOT_FOUND` |
| 5 | Asm | 拼接 `{baseUrl}/viewer?StudyInstanceUIDs={uid}&lng=zh` 并附 Token | 仅读，无 Cmd |

---

## 4. QueryService 与领域事件

无写、无事件。端点以后台每次读取为准。

---

## 5. 写库顺序

无 Cmd。

---

## 6. 非功能

| 项 | 落点 |
|----|------|
| Token | `ViewerTokenIssuer`，建议 ≤ 10 分钟，只含 studyInstanceUid |
| 脱敏 | 响应无患者姓名以外的影像像素 |

---

## 7. 测试要点

| 用例 | 层级 | 优先级 |
|------|------|--------|
| 无 Study 不返回 URL | 应用 | P0 |
| URL 含 StudyUID 与 lng=zh | 应用 | P0 |

---

## 8. 变更历史

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.0 | 2026-09-21 | D-10 |
| v1.1 | 2026-09-22 | 序号 55：`launchViewer` 已实现 |
