# 检查域 · 系统参数 · 应用服务设计（AS）

> **编码**：AS-PACS-CFG-004 · **版本**：v1.0 · **日期**：2026-09-21  
> **类名**：`PacsSystemConfigAppService` · **路由**：`POST /api/app/pacsSystemConfig/{method}`  
> **国基口径**：P0 **只读**当前运行参数（yaml / adapter properties）；不落独立配置表

---

## 1. 边界

| 在内 | 读 adapter 开关、Mock 外发成功标志等运行时参数 |
| 在外 | 写配置改 yaml / 运维；影像 Viewer 属 IM-CFG |

## 2. 接口

| IF | 方法 | 语义 |
|----|------|------|
| **IF-PACS-CFG-04** | `getSystemParams` | 返回当前有效参数快照 |

## 3. 参数键（P0）

| 键 | 来源 |
|----|------|
| `adapter.enabled` | `zxplat.modules.pacs.adapter.enabled` |
| `adapter.mockOutboundSuccess` | `zxplat.modules.pacs.adapter.mock-outbound-success` |

## 4. 修订

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.0 | 2026-09-21 | 国基最小只读；解锁序号 40 |
