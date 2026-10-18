# EMR · 元数据字段与就诊解析 · 应用服务设计（AS）

> **编码**：AS-EMR-META-001 · **版本**：v1.0 · **日期**：2026-09-24  
> **SDD**：`../AppService-SDD/AS-EMR-META-001-元数据字段与就诊解析-SDD.md`  
> **HTTP**：`POST /api/app/metadataField/*`、`POST /api/app/encounterMetadata/*`

---

## 1. 场景概述

### 1.1 目标与边界

| 维度 | 说明 |
| --- | --- |
| **在内** | 元数据字段定义 CRUD、启用列表、引用检查；按患者/就诊/文书解析字段展示值；按文书 id 委托解析 |
| **在外** | 将解析值写入文书 JSON（AS-EMR-DOC-001 · setValuesByNames）；HIS 读细节在 bridge |
| **涉及域** | MetadataFieldDef；Document（读 patient/encounter）；EmrAdmissionContextLoader |
| **编排权威** | **DM-EMR-003** |

### 1.2 角色与关键动作

| 角色 | 关键动作 |
| --- | --- |
| **管理员** | 维护 fieldKey、valueSource、脱敏规则 |
| **编辑器** | 打开文书或 PDF 前 resolveValues 填充芯片 |

### 1.3 术语与口径约定

| 术语 | 口径 |
| --- | --- |
| **fieldKey** | 字母开头，2～64 字符 |
| **valueSource** | 与 `MetadataValueSource` 对齐的大写 code |
| **解析** | 只读，不写 HIS |

### 1.4 本期范围与非目标

| 类别 | 内容 |
| --- | --- |
| **本期** | IF-EMR-META-01～09 |
| **非目标** | 持久化某次解析结果 |

### 1.5 领域清单

| 聚合 | 职责 | DM |
| --- | --- | --- |
| **MetadataFieldDef** | 目录与规则 | `docs/模块/zxplat-module-emr/DomainModel/metadata/DM-EMR-003-MetadataFieldDef-元数据字段定义.md` |
| **Document** | resolveForDocument 反查就诊 | `DM-EMR-001` |

### 1.6 业务规则（BR-*）

| 规则 ID | 描述 | 绑定 IF |
| --- | --- | --- |
| **BR-EMR-META-001** | fieldKey 租户内唯一 | IF-EMR-META-04 |
| **BR-EMR-META-002** | valueSource 合法且定义完整 | IF-EMR-META-04 / 05 |
| **BR-EMR-META-003** | 被模板引用禁止物理删 | IF-EMR-META-06 |
| **BR-EMR-META-004** | 解析只读外部聚合 | IF-EMR-META-08 / 09 |

---

## 2. 页面与接口总览（契约概览）

| IF 编号 | 用户动作 | 接口语义 | 读/写 | 关键规则 | 状态变化 | 涉及领域 | 查询服务 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| IF-EMR-META-01 | 字段维护列表 | 分页查询字段定义 | 读 | — | 无 | Metadata | Repository |
| IF-EMR-META-02 | 下拉/芯片配置 | 启用字段列表 | 读 | — | 无 | Metadata | Repository |
| IF-EMR-META-03 | 查看详情 | 按 id 查询 | 读 | — | 无 | Metadata | Repository |
| IF-EMR-META-04 | 新建字段 | 创建定义 | 写 | BR-001/002 | insert | Metadata | — |
| IF-EMR-META-05 | 编辑字段 | 更新（fieldKey 不可改） | 写 | BR-002 | update | Metadata | — |
| IF-EMR-META-06 | 删除字段 | 物理删；引用检查 | 写 | BR-003 | delete | Metadata | ReferenceChecker |
| IF-EMR-META-07 | 删前检查 | 是否被模板引用 | 读 | BR-003 | 无 | Metadata | ReferenceChecker |
| IF-EMR-META-08 | 解析就诊元数据 | patientId 必填 → 键值 Map | 读 | BR-004 | 无 | Metadata / HIS | MetadataValueResolver |
| IF-EMR-META-09 | 按文书解析 | documentId → 反查后同 IF-08 | 读 | BR-004 | 无 | Document / Metadata | DocumentManager |

---

## 3. 缺口跟踪（Gap）

| Gap ID | 缺口类型 | 文件 + 章节 | 缺什么 / 不一致点 | 关闭条件 | 影响 IF | 优先级 | 状态 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Gap-EMR-META-001 | DM 能力 | `DM-EMR-003` §6.1 | 无 MetadataField 写侧 DomainService 收敛校验与删除规则 | DM §6 增补 DomainService 且代码实现 | IF-EMR-META-04～06 | P2 | **已关闭**（2026-09-24：`MetadataFieldDefDomainService` + Command） |

---

## 4. 接口编排说明书（业务步骤）

### IF-EMR-META-01 分页列表

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 管理页 |
| **主路径步骤** | 租户+软删+过滤 → 分页 → DTO |
| **关键分支** | request 可 null 用默认分页 |
| **失败分支** | — |
| **查询侧说明** | 纯读 |

### IF-EMR-META-02 启用列表

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 编辑器/配置 |
| **主路径步骤** | 查全部启用 → 可选 keyword 内存过滤 → sortOrder |
| **关键分支** | — |
| **失败分支** | — |
| **查询侧说明** | 纯读 |

### IF-EMR-META-03 详情

| 维度 | 说明 |
| --- | --- |
| **触发前提** | id |
| **主路径步骤** | 按 id 加载未删 → DTO |
| **关键分支** | — |
| **失败分支** | 不存在 |
| **查询侧说明** | 纯读 |

### IF-EMR-META-04 创建

| 维度 | 说明 |
| --- | --- |
| **触发前提** | fieldKey、label、valueSource |
| **主路径步骤** | 格式与唯一校验 → 定义规则校验 → insert |
| **关键分支** | 默认值 editable/enabled/sortOrder |
| **失败分支** | 重复 fieldKey |
| **查询侧说明** | — |

### IF-EMR-META-05 更新

| 维度 | 说明 |
| --- | --- |
| **触发前提** | id |
| **主路径步骤** | 加载 → 增量更新 → 再校验定义 → save |
| **关键分支** | fieldKey 不可改 |
| **失败分支** | 不存在 |
| **查询侧说明** | — |

### IF-EMR-META-06 删除

| 维度 | 说明 |
| --- | --- |
| **触发前提** | id |
| **主路径步骤** | 引用检查 → 物理 delete |
| **关键分支** | 被引用拒绝 |
| **失败分支** | 提示改用禁用 |
| **查询侧说明** | — |

### IF-EMR-META-07 引用检查

| 维度 | 说明 |
| --- | --- |
| **触发前提** | fieldKey |
| **主路径步骤** | 扫描启用模板 content → 返回模板名列表 |
| **关键分支** | — |
| **失败分支** | fieldKey 空 |
| **查询侧说明** | 纯读 |

### IF-EMR-META-08 解析值

| 维度 | 说明 |
| --- | --- |
| **触发前提** | patientId |
| **主路径步骤** | 组装 MetadataResolveRequest → MetadataValueResolver → values DTO |
| **关键分支** | 可选 fieldKeys 子集；metadataOverrides |
| **失败分支** | patientId 空 |
| **查询侧说明** | 纯读 |

### IF-EMR-META-09 按文书解析

| 维度 | 说明 |
| --- | --- |
| **触发前提** | documentId |
| **主路径步骤** | DocumentManager 加载文书 → 构造 IF-08 请求 → 委托 resolveValues |
| **关键分支** | — |
| **失败分支** | 文书不存在 |
| **查询侧说明** | 纯读 |

---

## 5. 业务异常与用户提示

| 语义代码 | 场景 | 用户提示方向 | 可重试 |
| --- | --- | --- | --- |
| EX-EMR-META-101 | 列表失败 | 加载失败 | 是 |
| EX-EMR-META-102 | fieldKey 重复/格式 | 字段键不合法或已存在 | 否 |
| EX-EMR-META-103 | 删除被引用 | 已被模板引用，请禁用 | 否 |
| EX-EMR-META-104 | 解析 | patientId 不能为空 | 否 |

---

## 6. 事件语义与消费方

本期无领域事件。

---

## 7. 非功能性需求

| 类别 | 约束 |
| --- | --- |
| **性能** | 启用列表全量内存过滤（字段量可控） |
| **脱敏** | maskRule 在解析链路透传 |

---

## 8. 测试要点（业务视角）

| 类别 | 要点 |
| --- | --- |
| **正向** | CRUD；resolveValues；resolveForDocument |
| **异常** | 引用阻止删除；非法 valueSource |

---

## 9. 关联文档与变更历史

### 9.1 关联文档

| 类型 | 路径 |
| --- | --- |
| DM | `docs/模块/zxplat-module-emr/DomainModel/metadata/DM-EMR-003-MetadataFieldDef-元数据字段定义.md` |
| 文书 AS | `./AS-EMR-DOC-001-文书书写与导出.md` |
| SDD | `../AppService-SDD/AS-EMR-META-001-元数据字段与就诊解析-SDD.md` |

### 9.2 变更历史

| 版本 | 日期 | 变更摘要 |
| --- | --- | --- |
| v1.0 | 2026-09-24 | Mode B；serviceName 确认 metadataField / encounterMetadata |
