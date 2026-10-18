# 检查域 · 模态与可执行项目 · 应用服务设计（AS）

> **编码**：AS-PACS-CFG-001 · **版本**：v1.1 · **日期**：2026-09-21  
> **菜单**：模态与可执行项目 · **类名**：`PacsModalityConfigAppService` · **路由**：`POST /api/app/pacsModalityConfig/{method}`  
> **表**：`pacs_modality_capability`（V11）

---

## 1. 场景概述

### 1.1 目标与边界

| 维度 | 说明 |
|------|------|
| **在内** | capability 列表/保存/启停/软删 |
| **在外** | AD 目录；INT 接单校验读本表 |
| **废止** | health 侧 `pacs_item_map` |

### 1.2 BR

| 编号 | 规则 | IF |
|------|------|-----|
| **BR-CFG-01** | 开单 activity 须 ≥1 条启用 capability | CFG-01 |
| **BR-CFG-02** | `activityId` 须引用未软删 AD | CFG-01 |
| **BR-CFG-03** | 软删前若存在未软删 orderLink 引用该 activity → 拒绝 | CFG-01 |
| **BR-CFG-04** | 同租户 `activityId` 唯一（uk） | CFG-01 |

---

## 2. 接口总览

| IF | 方法 | 语义 | 读写 |
|----|------|------|:--:|
| **IF-PACS-CFG-01a** | `searchCapabilities` | 分页列表 | 读 |
| **IF-PACS-CFG-01b** | `saveCapability` | 新增/更新 | 写 |
| **IF-PACS-CFG-01c** | `disableCapability` | 停用 | 写 |
| **IF-PACS-CFG-01d** | `deleteCapability` | 软删 | 写 |

---

## 3. 关联

- SDD：[AS-PACS-CFG-001-SDD](../AppService-SDD/AS-PACS-CFG-001-模态与可执行项目-SDD.md)
- INT：[AS-PACS-INT-001](./AS-PACS-INT-001-HIS接单入口.md)
- 系统参数：[AS-PACS-CFG-004](./AS-PACS-CFG-004-系统参数.md)

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.1 | 2026-09-21 | 拆分列表/保存/停用/删除；对齐 V11 字段 |
| v1.0 | 2026-06-27 | 首版 |
