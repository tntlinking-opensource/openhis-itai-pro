# 检查域 · 报告模板 · 应用服务设计（AS）

> **编码**：AS-PACS-CFG-002 · **版本**：v1.0 · **日期**：2026-09-22  
> **菜单**：报告模板 · **类名**：`PacsReportTemplateConfigAppService` · **路由**：`POST /api/app/pacsReportTemplateConfig/{method}`  
> **领域**：[DM-PACS-004](./DM-PACS-004-PacsReportTemplate-报告模板.md)  
> **套用**：书写台 [AS-PACS-REP-001](./AS-PACS-REP-001-报告书写.md) IF-PACS-REP-04

---

## 1. 场景概述

### 1.1 目标与边界

| 维度 | 说明 |
|------|------|
| **在内** | 模板列表/保存/启停/软删 |
| **在外** | 报告事实书写/审核；打印版式 PRT；设备台账 CFG-003 |
| **涉及域** | PacsReportTemplate |

### 1.2 角色

| 角色 | 动作 |
|------|------|
| **影像科管理员** | 维护科室/院级模板 |
| **报告医生** | 可选维护个人模板；书写台套用 |

### 1.3 术语

| 术语 | 口径 |
|------|------|
| **套用** | 将模板 findings/conclusion 写入当前草稿报告（REP-04） |
| **个人模板** | `ownerPractitionerId` 非空 |

### 1.4 本期与非目标

| 类别 | 内容 |
|------|------|
| **本期（序号 43）** | 平铺 CRUD + 启用过滤；REP-04 套用 |
| **非目标** | 遗留树形目录、设备绑定 SBID、富文本编辑器 |

### 1.5 领域清单

| 聚合 | DM |
|------|-----|
| PacsReportTemplate | [DM-PACS-004](./DM-PACS-004-PacsReportTemplate-报告模板.md) |

### 1.6 业务规则

| 编号 | 规则 | IF |
|------|------|-----|
| **BR-CFG2-01** | 名称必填 | CFG-02b |
| **BR-CFG2-02** | 停用/软删后不可被套用 | CFG-02c/d、REP-04 |
| **BR-CFG2-03** | 列表默认可按 modalityCode 过滤 | CFG-02a |

---

## 2. 页面与接口总览

| IF | 用户动作 | 语义 | 读写 | 关键规则 |
|----|----------|------|:--:|----------|
| **IF-PACS-CFG-02a** | 模板列表 | 分页查询 | 读 | BR-CFG2-03 |
| **IF-PACS-CFG-02b** | 保存模板 | 新增/更新 | 写 | BR-CFG2-01 |
| **IF-PACS-CFG-02c** | 停用 | enabled=false | 写 | BR-CFG2-02 |
| **IF-PACS-CFG-02d** | 删除 | 软删 | 写 | BR-CFG2-02 |

---

## 3. 缺口跟踪（Gap）

**无待跟踪缺口**（DM-PACS-004 §5 方法与 Flyway 同期落地）。

---

## 4. 接口编排说明书

### 4.1 IF-PACS-CFG-02a

| 维度 | 说明 |
|------|------|
| **主路径** | 按模态/启停分页查模板 → 列表 DTO |

### 4.2 IF-PACS-CFG-02b

| 维度 | 说明 |
|------|------|
| **主路径** | 校验名称 → save 聚合 |

### 4.3 IF-PACS-CFG-02c / 02d

| 维度 | 说明 |
|------|------|
| **主路径** | 加载 → disable / 软删 |

---

## 5. 业务异常

| 码 | 场景 |
|----|------|
| `PACS_REPORT_TEMPLATE_NOT_FOUND` | 模板不存在或已删 |
| `PACS_REPORT_TEMPLATE_INVALID_ARGUMENT` | 名称空等 |

---

## 6. 事件语义

本期不发领域事件（配置型，列表即时读库）。

---

## 7. 非功能性需求

列表 P95 < 1s。

---

## 8. 测试要点

- 保存后列表可见；停用后 REP-04 拒绝；软删不出现在默认列表

---

## 9. 关联文档与变更历史

- SDD：[AS-PACS-CFG-002-SDD](../AppService-SDD/AS-PACS-CFG-002-报告模板-SDD.md)
- 套用：AS-PACS-REP-001 IF-PACS-REP-04
- 遗留：`TemplateManagerController`

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.0 | 2026-09-22 | D-11 首版；解锁序号 43 |
