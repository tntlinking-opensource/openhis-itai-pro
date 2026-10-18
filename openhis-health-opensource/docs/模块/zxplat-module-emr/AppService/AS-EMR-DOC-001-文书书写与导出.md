# EMR · 文书书写与导出 · 应用服务设计（AS）

> **文档职责**：说明 EMR 插件「电子病历文书」全量 HTTP 能力的**接口契约（IF）**、业务语义、涉及聚合、规则（BR）、编排意图；与 `DocumentAppService` 已实现对齐（Mode B）。
>
> **HTTP 与字段表权威**：`../AppService-SDD/AS-EMR-DOC-001-文书书写与导出-SDD.md`。
>
> **编码**：AS-EMR-DOC-001 · **版本**：v1.0 · **日期**：2026-09-24

---

## 1. 场景概述

### 1.1 目标与边界

| 维度 | 说明 |
| --- | --- |
| **在内** | 文书创建/打开/分页列表/保存/另存/软删；工作页初始化；版本列表/历史正文/回滚；结构树解析与按数据项名批量写值；锁定状态变更；编辑器图片上传；单份/合并 PDF 生成；校验与质控结果（占位）；打开文书时附带就诊元数据展示值 |
| **在外** | 模板正文维护（见 AS-EMR-TPL-001）；元数据字段目录维护与独立解析 API（见 AS-EMR-META-001）；文书类型后台（见 AS-EMR-DTYPE-001）；诊断/医嘱主动带入（医生站 AS-OP-DOR-011）；HIS 就诊事实写操作 |
| **涉及域** | EMR Document（含 DocumentType 读）；EncounterMetadata 解析（读）；可选 HIS 就诊上下文（`EmrAdmissionContextLoader`） |
| **编排权威** | 以本文 **§2、§4** 为准；领域不变式见 **DM-EMR-001** |

### 1.2 角色与关键动作

| 角色 | 关键动作 |
| --- | --- |
| **临床医生** | 选类型/模板创建文书 → 编辑 → 保存（可选版本）→ 打印预览 PDF；查看历史版本并回滚 |
| **病历管理员** | 锁定/解锁文书；合并多份文书 PDF |
| **系统/集成** | 按结构树回填数据项；校验接口预留 |

### 1.3 术语与口径约定

| 术语 | 口径 |
| --- | --- |
| **正文** | Tiptap/ProseMirror JSON 字符串 |
| **只读** | 锁定/归档，或门诊侧非创建者（住院/病程/会诊等多人可编规则由应用层判定） |
| **关联就诊并集** | 列表查询 `includeLinkedEncounters=true` 时展开门诊↔住院关联就诊 ID |
| **别名方法** | `getContent` 与 `getDocumentContent` 同一契约；`save` 返回摘要，`saveDocument` 额外返回新版本号 |

### 1.4 本期范围与非目标

| 类别 | 内容 |
| --- | --- |
| **本期（契约）** | §2 全部 IF-EMR-DOC-01～19，与 `POST /api/app/document/*` 一致 |
| **占位** | `validate`、`getQualityResult` 返回空结构，业务规则待产品定义 |
| **非目标** | 领域事件对外发布（DM §5 暂无）；归档专用 API（`ArchiveDocumentRequest` 未暴露于 AppService） |

### 1.5 领域清单（聚合 + 职责 + DM 路径）

| 聚合（域） | 在本场景中的职责（摘要） | DM 路径 |
| --- | --- | --- |
| **Document** | 生命周期、版本、结构写 JSON、类型读 | `docs/模块/zxplat-module-emr/DomainModel/document/DM-EMR-001-Document-电子病历文书.md` |
| **Metadata 解析** | 打开/PDF 时键值展示 | `docs/模块/zxplat-module-emr/DomainModel/metadata/DM-EMR-003-MetadataFieldDef-元数据字段定义.md` |

### 1.6 业务规则（BR-*）

| 规则 ID | 描述 | 绑定 IF |
| --- | --- | --- |
| **BR-EMR-DOC-001** | 锁定或归档禁止保存内容 | IF-EMR-DOC-05 / 06 |
| **BR-EMR-DOC-002** | 同就诊下首次病程模板（`PN_FIRST`）唯一 | IF-EMR-DOC-02 |
| **BR-EMR-DOC-003** | 软删后业务查询不可见 | IF-EMR-DOC-04 / 09 |
| **BR-EMR-DOC-004** | 回滚须指定 versionId 或 versionNumber | IF-EMR-DOC-13 |
| **BR-EMR-DOC-005** | 门诊文书仅创建者可改/删；住院/病程/会诊等放宽 | IF-EMR-DOC-05 / 06 / 09 |
| **BR-EMR-DOC-006** | PDF 须提供 documentId 或内联 content | IF-EMR-DOC-14 / 15 |
| **BR-EMR-DOC-007** | `changeLockState`：`LOCK`→锁定，否则回草稿 | IF-EMR-DOC-19 |

---

## 2. 页面与接口总览（契约概览）

| IF 编号 | 用户动作 | 接口语义（业务） | 读/写 | 关键规则（BR） | 关键状态变化 | 涉及领域 | 查询服务 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| IF-EMR-DOC-01 | 编辑器插入图片 | 上传 Base64 图片落盘，返回访问 URL | 写（文件） | — | 无 DB 文书变更 | 应用支撑 | — |
| IF-EMR-DOC-02 | 新建文书 | 按患者/就诊/类型创建草稿，可选模板初始化 | 写 | BR-002 / BR-005 | 新增 DRAFT | Document | DocumentManager 读 |
| IF-EMR-DOC-03 | 打开文书 | 按 id 或 patient+encounter+type 取正文与只读、元数据 | 读 | BR-005 | 无 | Document / Metadata | DocumentManager |
| IF-EMR-DOC-04 | 文书列表 | 分页过滤；可选关联就诊并集 | 读 | BR-003 | 无 | Document | DocumentManager |
| IF-EMR-DOC-05 | 保存 | 写正文，可选版本快照；返回摘要 | 写 | BR-001 / BR-005 | 内容更新 | Document | — |
| IF-EMR-DOC-06 | 保存并要版本号 | 同保存，返回 newVersionNumber | 写 | 同 IF-05 | 同 IF-05 | Document | — |
| IF-EMR-DOC-07 | 另存为新文书 | 复制源文书为新草稿 | 写 | BR-005 | 新 DRAFT | Document | — |
| IF-EMR-DOC-08 | 提交前校验 | 占位：返回空错误列表 | 读 | — | 无 | Document | — |
| IF-EMR-DOC-09 | 删除文书 | 软删；锁定/归档或非创建者拒绝 | 写 | BR-001 / BR-005 | 软删 | Document | — |
| IF-EMR-DOC-10 | 进入 EMR 工作页 | 返回可用文书类型编码等 | 读 | — | 无 | DocumentType | DocumentManager |
| IF-EMR-DOC-11 | 版本历史 | 某文书版本摘要列表 | 读 | — | 无 | Document | DocumentManager |
| IF-EMR-DOC-12 | 查看历史版本正文 | 只读快照 content | 读 | — | 无 | Document | DocumentManager |
| IF-EMR-DOC-13 | 回滚版本 | 将历史版本写回当前并再快照 | 写 | BR-004 | 内容+新版本 | Document | — |
| IF-EMR-DOC-14 | 生成 PDF | 单份渲染 Base64 | 读+渲染 | BR-006 | 无 | Document / Metadata | DocumentManager |
| IF-EMR-DOC-15 | 合并 PDF | 多 documentId 顺序渲染合并 | 读+渲染 | BR-006 | 无 | Document | — |
| IF-EMR-DOC-16 | 结构树 | 解析 region/section/dataElement | 读 | — | 无 | Document | DocumentStructureManager |
| IF-EMR-DOC-17 | 批量写数据项 | 按 name 写 attrs.value 并保存 | 写 | BR-001 | 内容更新 | Document | — |
| IF-EMR-DOC-18 | 质控结果 | 占位：空结果 | 读 | — | 无 | Document | — |
| IF-EMR-DOC-19 | 锁定/解锁 | 更新文书 status | 写 | BR-007 | LOCKED↔DRAFT | Document | — |

> **别名**：IF-EMR-DOC-03 对应应用方法 `getDocumentContent`；`getContent` 为同一 IF 的别名入口。

---

## 3. 缺口跟踪（Gap）

**无待跟踪缺口。**

> 读侧由 **DM-EMR-001 §6.2** 在 `DocumentManager` / `DocumentStructureManager` 上已定义并实现，不另建 `DocumentQueryService`；与 AS 编排一致，不作为 QueryService 能力缺口。

---

## 4. 接口编排说明书（业务步骤）

### IF-EMR-DOC-01 上传编辑器图片

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 编辑器插入本地/粘贴图片 |
| **主路径步骤** | 校验 Base64 与文件名 → 落盘至 EMR 上传目录 → 返回相对 URL |
| **关键分支** | MIME 类型可选 |
| **失败分支** | IO 失败 → 基础设施异常 |
| **查询侧说明** | 无 |

### IF-EMR-DOC-02 创建文书

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 患者、就诊、文书类型已知；可选模板 id |
| **主路径步骤** | 领域创建草稿 → 模板/默认模板初始化正文与标题 → 返回含 content 的详情 |
| **关键分支** | 首次病程唯一冲突拒绝 |
| **失败分支** | 类型/模板无效 → 业务校验失败 |
| **查询侧说明** | 创建后可触发 IF-EMR-DOC-04 刷新 |

### IF-EMR-DOC-03 打开文书内容

| 维度 | 说明 |
| --- | --- |
| **触发前提** | documentId 或三元组定位 |
| **主路径步骤** | 加载文书 → 计算只读 → 解析就诊元数据键值 → 返回 |
| **关键分支** | 未找到返回空 DTO |
| **失败分支** | — |
| **查询侧说明** | 纯读 |

### IF-EMR-DOC-04 文书分页列表

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 至少患者或就诊过滤之一 |
| **主路径步骤** | 归一化分页 → 组装 encounterId 集合（含关联）→ 分页查询 → 摘要（含主诉、科室、保存人） |
| **关键分支** | 无数据空页 |
| **失败分支** | 查询失败可重试 |
| **查询侧说明** | 纯读 |

### IF-EMR-DOC-05 / IF-EMR-DOC-06 保存文书

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 有效 documentId；非只读 |
| **主路径步骤** | 加载文书 → 编辑权限与状态门禁 → 写 content（可选版本）→ 返回摘要或版本号 |
| **关键分支** | saveVersion=true 时递增版本 |
| **失败分支** | 锁定/归档/非创建者 → EX-EMR-DOC-107 |
| **查询侧说明** | 无强制读刷新 |

### IF-EMR-DOC-07 另存为

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 源文书存在 |
| **主路径步骤** | 复制内容为新草稿（新 patient/encounter/type 由请求指定）→ 返回新文书详情 |
| **关键分支** | — |
| **失败分支** | 源不存在 |
| **查询侧说明** | — |

### IF-EMR-DOC-08 校验（占位）

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 产品未定义规则 |
| **主路径步骤** | 返回空 errors |
| **关键分支** | — |
| **失败分支** | — |
| **查询侧说明** | — |

### IF-EMR-DOC-09 删除文书

| 维度 | 说明 |
| --- | --- |
| **触发前提** | documentId 有效 |
| **主路径步骤** | 加载 → 锁定/归档/权限门禁 → 软删 |
| **关键分支** | id 空则 noop |
| **失败分支** | 不可删 → EX-EMR-DOC-107 |
| **查询侧说明** | 列表需刷新 |

### IF-EMR-DOC-10 工作页初始化

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 进入 EMR 模块 |
| **主路径步骤** | 读取全部文书类型 code 列表 → 返回初始化 DTO |
| **关键分支** | printOptions 当前为空列表 |
| **失败分支** | — |
| **查询侧说明** | DocumentManager.listDocumentTypes |

### IF-EMR-DOC-11～12 版本读

| 维度 | 说明 |
| --- | --- |
| **触发前提** | documentId；读正文时另需 versionId 或 versionNumber |
| **主路径步骤** | 查版本列表或单版本 content → 历史正文只读 |
| **关键分支** | 版本不存在返回空 content |
| **失败分支** | — |
| **查询侧说明** | 纯读 |

### IF-EMR-DOC-13 回滚版本

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 目标版本存在 |
| **主路径步骤** | 将历史 content 写回当前文书并生成新版本记录 |
| **关键分支** | versionId 与 versionNumber 二选一 |
| **失败分支** | 参数不全 → BR-004 |
| **查询侧说明** | — |

### IF-EMR-DOC-14 生成 PDF

| 维度 | 说明 |
| --- | --- |
| **触发前提** | documentId 或内联 content |
| **主路径步骤** | 解析正文 → 合并打印元数据（含 resolveValues）→ OpenPDF 渲染 → Base64+页数 |
| **关键分支** | 预览模式可截断页数 |
| **失败分支** | 无内容 → EX-EMR-DOC-201 |
| **查询侧说明** | 读文书与元数据 |

### IF-EMR-DOC-15 合并 PDF

| 维度 | 说明 |
| --- | --- |
| **触发前提** | documentIds 非空 |
| **主路径步骤** | 逐份 IF-EMR-DOC-14 渲染 → 合并字节 → 返回 |
| **关键分支** | 全部 id 无效则失败 |
| **失败分支** | 无可合并文书 |
| **查询侧说明** | — |

### IF-EMR-DOC-16 结构树

| 维度 | 说明 |
| --- | --- |
| **触发前提** | documentId 或内联 content |
| **主路径步骤** | 解析 JSON 为结构项树（可过滤 nodeTypes、仅首层） |
| **关键分支** | 无 content 空列表 |
| **失败分支** | — |
| **查询侧说明** | 纯读 |

### IF-EMR-DOC-17 按名写值

| 维度 | 说明 |
| --- | --- |
| **触发前提** | documentId 与 name→value 非空 |
| **主路径步骤** | 加载文书 → 结构服务改 JSON → saveContent（不版本） |
| **关键分支** | 文书不存在 noop |
| **失败分支** | — |
| **查询侧说明** | — |

### IF-EMR-DOC-18 质控（占位）

| 维度 | 说明 |
| --- | --- |
| **触发前提** | — |
| **主路径步骤** | 返回空 passed/issues |
| **关键分支** | — |
| **失败分支** | — |
| **查询侧说明** | — |

### IF-EMR-DOC-19 锁定状态

| 维度 | 说明 |
| --- | --- |
| **触发前提** | documentId、action |
| **主路径步骤** | 映射 action 为 status → updateStatus |
| **关键分支** | 非 LOCK 则回 DRAFT |
| **失败分支** | 文书不存在 |
| **查询侧说明** | — |

---

## 5. 业务异常与用户提示

| 语义代码 | 场景 | 用户提示方向 | 可重试 |
| --- | --- | --- | --- |
| EX-EMR-DOC-101 | 列表/打开失败 | 加载失败，请稍后重试 | 是 |
| EX-EMR-DOC-104 | 文书不存在 | 文书不存在或已删除 | 否 |
| EX-EMR-DOC-107 | 锁定/归档/权限 | 文书已锁定或归档，或仅创建者可操作 | 否 |
| EX-EMR-DOC-201 | PDF 无内容 | 请提供文书或正文 | 否 |
| EX-EMR-DOC-202 | PDF 正文为空 | 文档内容为空 | 否 |

---

## 6. 事件语义与消费方

| 事件语义 | 触发相位 | 消费方 |
| --- | --- | --- |
| （本期无领域事件发布） | — | DM-EMR-001 §5 |

---

## 7. 非功能性需求

| 类别 | 约束 |
| --- | --- |
| **性能** | 列表/打开目标 ≤1500ms；保存 ≤2000ms；PDF 视页数 |
| **幂等** | 重复保存以最后一次为准 |
| **并发** | 创建者/住院多人规则由服务端门禁 |
| **审计** | 创建/更新人可追溯 |

---

## 8. 测试要点（业务视角）

| 类别 | 要点 |
| --- | --- |
| **正向** | 创建→保存→打开；版本保存与回滚；PDF 单份与合并；setValuesByNames |
| **异常** | 锁定后保存；他人删门诊文书；合并空 ids |
| **规则** | PN_FIRST 唯一；关联就诊列表并集 |

---

## 9. 关联文档与变更历史

### 9.1 关联文档

| 类型 | 路径 |
| --- | --- |
| DM | `docs/模块/zxplat-module-emr/DomainModel/document/DM-EMR-001-Document-电子病历文书.md` |
| 元数据 AS | `./AS-EMR-META-001-元数据字段与就诊解析.md` |
| 模板 AS | `./AS-EMR-TPL-001-模板管理.md` |
| 医生站病历 | `AppService/AS-OP-DOR-011-门诊医生站病历.md` |
| SDD | `../AppService-SDD/AS-EMR-DOC-001-文书书写与导出-SDD.md` |

### 9.2 变更历史

| 版本 | 日期 | 变更摘要 |
| --- | --- | --- |
| v1.0 | 2026-09-24 | Mode B：对齐 DocumentAppService 全量方法；IF-EMR-DOC-01～19 |
