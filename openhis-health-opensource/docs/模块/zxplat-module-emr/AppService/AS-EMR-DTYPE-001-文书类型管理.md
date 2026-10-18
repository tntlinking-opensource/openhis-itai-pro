# EMR · 文书类型管理 · 应用服务设计（AS）

> **编码**：AS-EMR-DTYPE-001 · **版本**：v1.0 · **日期**：2026-09-24  
> **SDD**：`../AppService-SDD/AS-EMR-DTYPE-001-文书类型管理-SDD.md`  
> **实体 DM**：无独立 DM 文件；类型字段见 **DM-EMR-001 §3.2 DocumentType**

---

## 1. 场景概述

### 1.1 目标与边界

| 维度 | 说明 |
| --- | --- |
| **在内** | 文书类型列表/详情/新建/更新/删除；默认模板绑定与引用检查 |
| **在外** | 文书与模板业务使用 type code（AS-EMR-DOC/TPL） |
| **涉及域** | DocumentType（EMR 插件）；Template、Document（引用检查读） |
| **编排权威** | **DM-EMR-001 §3.2** |

### 1.2 角色与关键动作

| 角色 | 关键动作 |
| --- | --- |
| **管理员** | 维护 code/name/category/排序/默认模板/启用 |

### 1.3 术语与口径约定

| 术语 | 口径 |
| --- | --- |
| **code** | 创建后不可通过 update 修改 |
| **categoryCode** | OUTPATIENT / INPATIENT / SURGERY 等分类 |

### 1.4 本期范围与非目标

| 类别 | 内容 |
| --- | --- |
| **本期** | IF-EMR-DTYPE-01～05 |
| **非目标** | 类型级权限 |

### 1.5 领域清单

| 聚合 | 职责 | DM |
| --- | --- | --- |
| **DocumentType** | 类型配置表 | `DM-EMR-001` §3.2 |

### 1.6 业务规则（BR-*）

| 规则 ID | 描述 | 绑定 IF |
| --- | --- | --- |
| **BR-EMR-DTYPE-001** | code 唯一 | IF-EMR-DTYPE-03 |
| **BR-EMR-DTYPE-002** | 默认模板须启用且 documentTypeCode 一致 | IF-EMR-DTYPE-03 / 04 |
| **BR-EMR-DTYPE-003** | 仍被模板或文书引用不可删 | IF-EMR-DTYPE-05 |

---

## 2. 页面与接口总览（契约概览）

| IF 编号 | 用户动作 | 接口语义 | 读/写 | 关键规则 | 状态变化 | 涉及领域 | 查询服务 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| IF-EMR-DTYPE-01 | 类型列表 | enabled/keyword 过滤，sortOrder 排序 | 读 | — | 无 | DocumentType | Repository |
| IF-EMR-DTYPE-02 | 类型详情 | 按 id，含默认模板名 | 读 | — | 无 | DocumentType | Repository |
| IF-EMR-DTYPE-03 | 新建类型 | code/name 等 | 写 | BR-001/002 | insert | DocumentType | — |
| IF-EMR-DTYPE-04 | 更新类型 | 增量；可清默认模板 | 写 | BR-002 | update | DocumentType | — |
| IF-EMR-DTYPE-05 | 删除类型 | 引用检查后物理删 | 写 | BR-003 | delete | DocumentType | Template/Document count |

---

## 3. 缺口跟踪（Gap）

**无待跟踪缺口。**

> 类型维护由 `DocumentTypeAppService` + Repository 实现，与 **DM-EMR-001 §3.2** 一致；无独立 DocumentType DomainService 为 Mode B 既定形态，不记 Gap。

---

## 4. 接口编排说明书（业务步骤）

### IF-EMR-DTYPE-01 列表

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 管理页或下拉 |
| **主路径步骤** | 租户+软删+过滤 → 排序 → DTO 列表 |
| **关键分支** | request 可 null |
| **失败分支** | — |
| **查询侧说明** | 纯读 |

### IF-EMR-DTYPE-02 详情

| 维度 | 说明 |
| --- | --- |
| **触发前提** | id |
| **主路径步骤** | findById → 补默认模板名称 → DTO |
| **关键分支** | — |
| **失败分支** | 不存在 |
| **查询侧说明** | 纯读 |

### IF-EMR-DTYPE-03 创建

| 维度 | 说明 |
| --- | --- |
| **触发前提** | code、name |
| **主路径步骤** | 唯一与长度校验 → 组装实体 → 校验默认模板 → save |
| **关键分支** | isEnabled 默认 true |
| **失败分支** | 编码重复；模板不匹配 |
| **查询侧说明** | — |

### IF-EMR-DTYPE-04 更新

| 维度 | 说明 |
| --- | --- |
| **触发前提** | id |
| **主路径步骤** | 加载 → 增量更新 name/enabled/sort/category/默认模板 |
| **关键分支** | clearDefaultTemplate |
| **失败分支** | 不存在；模板校验失败 |
| **查询侧说明** | — |

### IF-EMR-DTYPE-05 删除

| 维度 | 说明 |
| --- | --- |
| **触发前提** | id |
| **主路径步骤** | 统计模板/文书引用 → 无引用则 delete |
| **关键分支** | — |
| **失败分支** | 仍被引用 |
| **查询侧说明** | count 只读 |

---

## 5. 业务异常与用户提示

| 语义代码 | 场景 | 用户提示方向 | 可重试 |
| --- | --- | --- | --- |
| EX-EMR-DTYPE-101 | 列表失败 | 加载失败 | 是 |
| EX-EMR-DTYPE-102 | 编码重复 | 文书类型编码已存在 | 否 |
| EX-EMR-DTYPE-103 | 删除引用 | 已被模板/文书引用 | 否 |
| EX-EMR-DTYPE-104 | 默认模板 | 模板不存在或未启用或类型不一致 | 否 |

---

## 6. 事件语义与消费方

本期无领域事件。

---

## 7. 非功能性需求

| 类别 | 约束 |
| --- | --- |
| **数据量** | 类型数量有限，列表全表排序可接受 |

---

## 8. 测试要点（业务视角）

| 类别 | 要点 |
| --- | --- |
| **正向** | CRUD；绑定默认模板 |
| **异常** | 有模板引用时删除失败 |

---

## 9. 关联文档与变更历史

### 9.1 关联文档

| 类型 | 路径 |
| --- | --- |
| DM | `docs/模块/zxplat-module-emr/DomainModel/document/DM-EMR-001-Document-电子病历文书.md` §3.2 |
| 模板 AS | `./AS-EMR-TPL-001-模板管理.md` |
| SDD | `../AppService-SDD/AS-EMR-DTYPE-001-文书类型管理-SDD.md` |

### 9.2 变更历史

| 版本 | 日期 | 变更摘要 |
| --- | --- | --- |
| v1.0 | 2026-09-24 | Mode B 对齐 DocumentTypeAppService |
