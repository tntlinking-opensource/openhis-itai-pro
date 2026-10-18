# AS-PACS-IM-003 阅片地址 · SDD

> **编码**：AS-PACS-IM-003-SDD · **版本**：v1.0 · **日期**：2026-09-21  
> **对应 AS**：AS-PACS-IM-003 v1.0  
> **实现阶段口径**：目标态。**已实现**（2026-09-22）：`module-pacs-imaging-domain` ViewerEndpoint + `module-pacs-imaging-application` `PacsViewerUrlConfigAppService`。

---

## 1. 实现边界

| 项 | 内容 |
|----|------|
| **模块** | `module-pacs-imaging-application` · `config` |
| **边界** | OHIF 根地址的列表、保存、匹配 |
| **编排权威** | AS-PACS-IM-003 |
| **实现方式** | `PacsViewerUrlConfigAppService`；HTTP 一律 POST |

---

## 2. 代码结构

```text
com.zxplat.plugin.pacsimaging.application.config
  service/PacsViewerUrlConfigAppService.java
  request/ListViewerEndpointsRequest.java
  request/SaveViewerEndpointRequest.java
  request/ResolveViewerEndpointRequest.java
  response/ListViewerEndpointsResponse.java
  response/SaveViewerEndpointResponse.java
  response/ResolveViewerEndpointResponse.java
  dto/ViewerEndpointRowDto.java
  mapper/PacsViewerUrlConfigMapper.java
  mapper/PacsViewerUrlConfigMapperImpl.java
```

| 类型 | 用途 |
|------|------|
| `ViewerEndpointDomainService` | 目标态 `save` |
| `ViewerEndpointQueryService` | 目标态 `list` / `resolve` |

| 类别 | 代码实现锚点 | DM 追溯锚点 | 说明 |
|------|--------------|-------------|------|
| 根地址 | `SaveViewerEndpointRequest.baseUrl` | DM-PACS-003 §3 ViewerEndpoint.baseUrl（已实现） | 须 http(s) |
| 模态 | `SaveViewerEndpointRequest.modalityCode` | 同表 modalityCode；空=该机构默认 | 目标态 |

---

## 3. 按接口 ID 命名展开

| IF | 方法 | 中文 | HTTP |
|----|------|------|------|
| CFG-01 | `listViewerEndpoints` | 端点列表 | `POST /api/app/pacsViewerUrlConfig/listViewerEndpoints` |
| CFG-01 | `saveViewerEndpoint` | 保存端点 | `POST /api/app/pacsViewerUrlConfig/saveViewerEndpoint` |
| CFG-02 | `resolveViewerEndpoint` | 匹配端点 | `POST /api/app/pacsViewerUrlConfig/resolveViewerEndpoint` |

**save Request**：`orgId` Long 可空（空=全局）；`modalityCode` String 可空；`baseUrl` String 必填；`enabled` Boolean 必填。

**resolve Request**：`orgId` Long 可空；`modalityCode` String 可空。

**resolve Response**：`baseUrl` String；`matchedBy` String（`ORG_MODALITY` / `ORG_DEFAULT` / `GLOBAL`）。

| 序号 | 阶段 | 调用 | 说明 |
|------|------|------|------|
| 1 | Gate | `save` 时 baseUrl 为 http(s) | 否则 `PACS_VIEWER_ENDPOINT_INVALID` |
| 2 | Pre | `ViewerEndpointQueryService#list` | **目标态**；保存前找出同机构+模态的启用行 |
| 3 | Cmd | `ViewerEndpointDomainService#save` | **目标态**，写库点；同键旧行改为停用 |
| 4 | Asm | Mapper | |

`resolve`：仅 `ViewerEndpointQueryService#resolve`，顺序为机构+模态 → 机构默认 → 全局默认。

---

## 4. QueryService 与领域事件

无领域事件。IM-002 每次 `launchViewer` 调用 `resolve`。

---

## 5. 写库顺序

| IF | Cmd | 事务 |
|----|-----|------|
| CFG-01 `saveViewerEndpoint` | 停用旧行 + 插入新行，同一 `save` | 单事务 |

---

## 6. 非功能

| 项 | 落点 |
|----|------|
| 并发 | 同一 orgId+modalityCode 保存串行，避免两条同时启用 |
| 审计 | 保存记操作者与 baseUrl，不记 Token |

---

## 7. 测试要点

| 用例 | 层级 | 优先级 |
|------|------|--------|
| 非法 URL 拒绝 | 应用 | P0 |
| 第二条保存后 resolve 只命中新行 | 领域（DM 落地后） | P0 |
| 仅全局默认时仍能匹配 | 应用 | P0 |

---

## 8. 变更历史

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.0 | 2026-09-21 | D-10 |
