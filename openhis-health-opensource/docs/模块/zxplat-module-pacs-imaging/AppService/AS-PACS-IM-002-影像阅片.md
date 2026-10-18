# 影像扩展 · 影像阅片 · 应用服务设计（AS）

> **编码**：AS-PACS-IM-002 · **版本**：v1.0 · **日期**：2026-09-21  
> **菜单**：影像阅片 · **类名**：`PacsViewerAppService`  
> **路由**：`POST /api/app/pacsViewer/{method}`  
> **选型**：OHIF v3；取图协议 DICOMweb；界面 `lng=zh`

---

## 1. 场景概述

### 1.1 目标与边界

| 维度 | 说明 |
|------|------|
| **在内** | 按工单拼 OHIF 启动 URL（StudyUID + 短期 Token） |
| **在外** | 窗宽、测量、MPR、像素代理语义；关键图画廊（IM-001 CAP-15） |
| **涉及域** | PacsWorkItem、ImagingStudy、阅片端点配置 |

### 1.2 角色与关键动作

| 角色 | 动作 |
|------|------|
| **技师 / 诊断医生 / 临床医生** | 从检查或报告打开阅片 |

科室入口与 HIS 入口是同一后端能力，不拆两个接口。

### 1.3 术语

| 术语 | 口径 |
|------|------|
| **启动 URL** | OHIF `/viewer?StudyInstanceUIDs={uid}&lng=zh`，另附短期 Token |
| **Token** | 只证明可看该 Study；不替代登录态以外的长期凭证 |

### 1.4 本期范围与非目标

| 类别 | 内容 |
|------|------|
| **本期（序号 55）** | VIEW-01/02/03/05 合并为一次启动 |
| **非目标** | VIEW-04 缩略图（关键图归 IM-001）；自研 Viewer |

### 1.5 领域清单

| 聚合 | DM |
|------|-----|
| PacsWorkItem | [DM-PACS-001](../../../领域/DomainModel/DM-PACS-001-PacsWorkItem-检查工单.md) |
| ImagingStudy、ViewerEndpoint | [DM-PACS-003](../DomainModel/DM-PACS-003-影像索引与阅片端点.md)；✅ 领域+应用已实现 |

### 1.6 业务规则

| 编号 | 规则 | IF |
|------|------|-----|
| **BR-VIEW-01** | 无 StudyUID 不能打开阅片 | VIEW-01 |
| **BR-VIEW-02** | 端点取自 IM-003 已启用配置；国基默认 OHIF + DICOMweb | VIEW-01 |
| **BR-VIEW-03** | ZXPlat 不返回像素，只返回 URL | VIEW-01 |

---

## 2. 页面与接口总览

| IF | 用户动作 | 语义 | 读写 | 关键规则 | 状态变化 | 涉及领域 |
|----|----------|------|:--:|----------|----------|----------|
| **IF-PACS-VIEW-01** | 打开阅片 | 返回 OHIF URL 与 Token | 读 | BR-VIEW-01/02/03 | — | PacsWorkItem、ImagingStudy、ViewerEndpoint |

---

## 3. 缺口跟踪（Gap）

| Gap ID | 缺口类型 | 文件 + 章节 | 缺什么 / 不一致点 | 关闭条件 | 影响 IF | 优先级 | 状态 |
|--------|----------|-------------|-------------------|----------|---------|--------|------|
| G-IM-VIEW-01 | QueryService 能力 | [DM-PACS-003](../DomainModel/DM-PACS-003-影像索引与阅片端点.md) §6.4 | `findByWorkItemId` | 实现该方法 | VIEW-01 | P0 | ✅ 已关闭 2026-09-22 |
| G-IM-VIEW-02 | QueryService 能力 | DM-PACS-003 §6.4 | `resolve` | 实现该方法 | VIEW-01 | P0 | ✅ 已关闭 2026-09-22 |

---

## 4. 接口编排说明书

### 4.1 IF-PACS-VIEW-01

| 维度 | 说明 |
|------|------|
| **前提** | 调用方已登录 |
| **主路径** | 加载工单 → 取 StudyUID → 按机构/模态取 OHIF 根地址 → 签发短期 Token → 返回完整启动 URL |
| **失败** | 无 Study；未配置端点；工单不存在 |
| **查询** | `PacsWorkItemQueryService#getDetail`；Study 与端点见 §3 |

---

## 5. 业务异常

| 码 | 场景 | 可重试 |
|----|------|:--:|
| `PACS_WORK_ITEM_NOT_FOUND` | 工单不存在 | 否 |
| `PACS_IMAGING_STUDY_NOT_FOUND` | 尚未入库 | 否 |
| `PACS_VIEWER_ENDPOINT_NOT_FOUND` | 未配置 OHIF 地址 | 否 |

---

## 6. 事件语义

本 AS 不发布领域事件。

---

## 7. 非功能性需求

Token 有效期建议不超过 10 分钟。DICOMweb 只经反代，不对浏览器暴露 Orthanc 管理口。

---

## 8. 测试要点

- 无 Study 时打开失败且不返回 OHIF 地址
- 返回 URL 含 StudyUID 与 `lng=zh`
- 响应体无像素、无 DCM

---

## 9. 关联文档

- SDD：[AS-PACS-IM-002-SDD](../AppService-SDD/AS-PACS-IM-002-影像阅片-SDD.md)
- 配置：[AS-PACS-IM-003](./AS-PACS-IM-003-阅片地址.md)
- 选型：[04-第三方组件选型.md](../04-第三方组件选型.md)

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.0 | 2026-09-21 | D-10；VIEW-01～03、05 合并；VIEW-04 不在本 AS |
| v1.1 | 2026-09-22 | 序号 55 成码：`PacsViewerAppService#launchViewer`；Gap 关闭 |
