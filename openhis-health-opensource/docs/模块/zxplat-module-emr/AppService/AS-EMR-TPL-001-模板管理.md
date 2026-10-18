# EMR · 模板管理 · 应用服务设计（AS）

> **编码**：AS-EMR-TPL-001 · **版本**：v1.0 · **日期**：2026-09-24  
> **SDD**：`../AppService-SDD/AS-EMR-TPL-001-模板管理-SDD.md`

---

## 1. 场景概述

### 1.1 目标与边界

| 维度 | 说明 |
| --- | --- |
| **在内** | 模板后台分页维护；医生站按站别/可见范围加载模板树；模板 CRUD、启用/停用、复制；维护页拉取文书类型下拉 |
| **在外** | 文书实例生命周期（AS-EMR-DOC-001）；文书类型 CRUD（AS-EMR-DTYPE-001，本场景只读复用 getList） |
| **涉及域** | EMR Template；DocumentType（读） |
| **编排权威** | 本文 §2、§4；**DM-EMR-002** |

### 1.2 角色与关键动作

| 角色 | 关键动作 |
| --- | --- |
| **模板管理员** | 分页查询 → 新建/编辑/复制/停用/删除全院或科室模板 |
| **临床医生** | 医生站 `listForStation` 选模板；「存为模版」走 create 且 `fromDoctorStation=true` |

### 1.3 术语与口径约定

| 术语 | 口径 |
| --- | --- |
| **范围** | PERSONAL / DEPARTMENT / HOSPITAL |
| **站别** | `stationKind`：OUTPATIENT / INPATIENT → 映射就诊类别 AMB / IMP |
| **别名** | `create`→`createTemplate`；`update`→`updateTemplate`；`delete`→`deleteTemplate`；`getContent`→内部 `getTemplate` |

### 1.4 本期范围与非目标

| 类别 | 内容 |
| --- | --- |
| **本期** | IF-EMR-TPL-01～10 |
| **非目标** | 模板版本历史；模板审批流 |

### 1.5 领域清单

| 聚合 | 职责 | DM |
| --- | --- | --- |
| **Template** | CRUD、范围、站别可见 | `docs/模块/zxplat-module-emr/DomainModel/template/DM-EMR-002-Template-电子病历模板.md` |
| **DocumentType** | 下拉与类型名展示 | `DM-EMR-001` §3.2（类型实体） |

### 1.6 业务规则（BR-*）

| 规则 ID | 描述 | 绑定 IF |
| --- | --- | --- |
| **BR-EMR-TPL-001** | 模板 code 唯一 | IF-EMR-TPL-05 / 06 / 09 |
| **BR-EMR-TPL-002** | 范围与 organizationId/owner 配套 | IF-EMR-TPL-05 / 06 |
| **BR-EMR-TPL-003** | 医生站只见 enabled 且范围匹配 | IF-EMR-TPL-02 |
| **BR-EMR-TPL-004** | 软删后不可见 | IF-EMR-TPL-01 / 04 / 07 |
| **BR-EMR-TPL-005** | 医生站另存仅 PERSONAL/DEPARTMENT，所有者取会话 | IF-EMR-TPL-05 |

---

## 2. 页面与接口总览（契约概览）

| IF 编号 | 用户动作 | 接口语义 | 读/写 | 关键规则 | 状态变化 | 涉及领域 | 查询服务 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| IF-EMR-TPL-01 | 模板维护列表 | 分页过滤模板（列表不含 content） | 读 | BR-004 | 无 | Template | TemplateRepository |
| IF-EMR-TPL-02 | 医生站模板树 | 站别+可见范围启用模板 | 读 | BR-003 | 无 | Template | TemplateRepository |
| IF-EMR-TPL-03 | 点选模板（简） | 按 templateId 取含 content | 读 | — | 无 | Template | TemplateRepository |
| IF-EMR-TPL-04 | 模板详情 | 按 id 取含 content | 读 | BR-004 | 无 | Template | TemplateRepository |
| IF-EMR-TPL-05 | 新建模板 | 创建模板（含医生站分支） | 写 | BR-001/002/005 | insert | Template | — |
| IF-EMR-TPL-06 | 编辑模板 | 增量更新 | 写 | BR-001/002 | update | Template | — |
| IF-EMR-TPL-07 | 删除模板 | 软删 | 写 | BR-004 | 软删 | Template | — |
| IF-EMR-TPL-08 | 启用/停用 | 切换 enabled | 写 | — | enabled | Template | — |
| IF-EMR-TPL-09 | 复制模板 | 副本新 code/name | 写 | BR-001 | insert | Template | — |
| IF-EMR-TPL-10 | 文书类型下拉 | 启用类型 code/name（别名 getDocumentTypeList） | 读 | — | 无 | DocumentType | DocumentTypeAppService |

> **别名**：IF-EMR-TPL-05 应用入口 `create` 与 `createTemplate` 等价；IF-EMR-TPL-03 使用 `getContent`，内部等同 IF-EMR-TPL-04。

---

## 3. 缺口跟踪（Gap）

| Gap ID | 缺口类型 | 文件 + 章节 | 缺什么 / 不一致点 | 关闭条件 | 影响 IF | 优先级 | 状态 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Gap-EMR-TPL-001 | DM 能力 | `DM-EMR-002` §6.1 | 无 `TemplateDomainService` 收敛范围/编码/软删不变式 | DM §6 增补写侧服务签名且领域代码实现 | IF-EMR-TPL-05～09 | P2 | **已关闭**（2026-09-24：`TemplateDomainService` + Command） |

---

## 4. 接口编排说明书（业务步骤）

### IF-EMR-TPL-01 分页列表

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 管理页查询 |
| **主路径步骤** | 归一化分页 → Specification 过滤 → 映射 DTO（无 content） |
| **关键分支** | keyword/searchKey 二选一 |
| **失败分支** | — |
| **查询侧说明** | 纯读 |

### IF-EMR-TPL-02 医生站模板树

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 有效 stationKind |
| **主路径步骤** | 取当前用户科室/个人 id → 站别+可见性 Specification → 按范围排序返回 |
| **关键分支** | 无模板空列表 |
| **失败分支** | stationKind 无效 |
| **查询侧说明** | 纯读 |

### IF-EMR-TPL-03 / IF-EMR-TPL-04 模板正文

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 模板 id |
| **主路径步骤** | 查未删模板 → 映射含 content DTO |
| **关键分支** | id 空或不存在返回 null |
| **失败分支** | — |
| **查询侧说明** | 纯读 |

### IF-EMR-TPL-05 创建

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 名称、类型等 |
| **主路径步骤** | code 唯一校验 → 范围字段校验（医生站分支强制会话 owner/org）→ 持久化 |
| **关键分支** | fromDoctorStation 限制范围级别 |
| **失败分支** | 编码重复；无医务人员/科室 |
| **查询侧说明** | — |

### IF-EMR-TPL-06 更新

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 模板存在 |
| **主路径步骤** | 增量字段 → 变更 scope 时重校验 → 保存 |
| **关键分支** | code 变更时唯一性 |
| **失败分支** | 不存在 |
| **查询侧说明** | — |

### IF-EMR-TPL-07 删除

| 维度 | 说明 |
| --- | --- |
| **触发前提** | id 有效 |
| **主路径步骤** | 软删标志 |
| **关键分支** | id 空 noop |
| **失败分支** | 不存在 |
| **查询侧说明** | — |

### IF-EMR-TPL-08 启用切换

| 维度 | 说明 |
| --- | --- |
| **触发前提** | templateId |
| **主路径步骤** | 更新 enabled |
| **关键分支** | — |
| **失败分支** | 不存在 |
| **查询侧说明** | — |

### IF-EMR-TPL-09 复制

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 源模板存在 |
| **主路径步骤** | 复制字段与新 code/name → insert |
| **关键分支** | 默认名「（副本）」 |
| **失败分支** | 新 code 冲突 |
| **查询侧说明** | — |

### IF-EMR-TPL-10 文书类型下拉

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 维护页或模板表单 |
| **主路径步骤** | 委托 documentType getList（默认 enabled=true）→ 映射 code/name |
| **关键分支** | — |
| **失败分支** | — |
| **查询侧说明** | 读 DocumentType |

---

## 5. 业务异常与用户提示

| 语义代码 | 场景 | 用户提示方向 | 可重试 |
| --- | --- | --- | --- |
| EX-EMR-TPL-101 | 列表失败 | 加载失败 | 是 |
| EX-EMR-TPL-102 | stationKind 无效 | 站别参数无效 | 否 |
| EX-EMR-TPL-103 | 编码重复 | 模板编码已存在 | 否 |
| EX-EMR-TPL-104 | 医生站存模板 | 未关联医务人员或未选科室 | 否 |

---

## 6. 事件语义与消费方

本期无领域事件。

---

## 7. 非功能性需求

| 类别 | 约束 |
| --- | --- |
| **性能** | listForStation 不含 content；getList 默认不含 content |
| **审计** | 创建/更新人可追溯 |

---

## 8. 测试要点（业务视角）

| 类别 | 要点 |
| --- | --- |
| **正向** | 全院/科室/个人可见性；复制；启用停用 |
| **规则** | 医生站另存个人/科室；code 唯一 |

---

## 9. 关联文档与变更历史

### 9.1 关联文档

| 类型 | 路径 |
| --- | --- |
| DM | `docs/模块/zxplat-module-emr/DomainModel/template/DM-EMR-002-Template-电子病历模板.md` |
| 文书 AS | `./AS-EMR-DOC-001-文书书写与导出.md` |
| 类型 AS | `./AS-EMR-DTYPE-001-文书类型管理.md` |
| SDD | `../AppService-SDD/AS-EMR-TPL-001-模板管理-SDD.md` |

### 9.2 变更历史

| 版本 | 日期 | 变更摘要 |
| --- | --- | --- |
| v1.0 | 2026-09-24 | Mode B 初版；Gap TemplateDomainService |
