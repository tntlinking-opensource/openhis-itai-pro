# 基础资料 · 打印设计 · 应用服务设计（AS）

> **文档职责**：说明「打印模板」维护页与「院点打印模板配置（PrintBinding）」页的**接口契约（IF）**、业务语义、涉及聚合、规则（BR）、编排意图；作为上游契约供详细设计与实现对齐。
>
> **本文必须写**：IF 编号；用户动作与读写属性；关键校验与状态变化；Gap；异常口径；非功能约束；业务向测试要点。
>
> **本文禁止写**：具体类名/应用方法名；Request/Response 字段明细；包路径；Mapper；伪代码；写库顺序。
>
> **页面边界**：
> - **§1.4.1** 基础数据 → **打印模板**（`print-template/index.vue` + `preview-dialog.vue`）
> - **§1.4.2** 基础数据 → **打印模板配置 / 院点绑定**（`print-binding/index.vue`）
> - **§1.4.3** **Web 画布设计器**（`print-template/designer/index.vue`，**vue-plugin-hiprint**）消费 IF-13/14/15/ensureDesignDraft
>
> **编码**：AS-BAS-MST-019 · **版本**：v1.4 · **日期**：2026-07-26

---

## 1. 场景概述

### 1.1 目标与边界

| 维度 | 说明 |
| --- | --- |
| **在内** | **PrintTemplate** 分页列表、新建空白草稿、**Web hiprint 设计页**、保存草稿、发布、从 published 复制草稿、Web 预览（hiprint + samplePrintData）；**PrintBinding** 租户级场景 → **busNo** 映射的加载与保存（IF-BAS-PRT-21/22） |
| **在外** | 业务页运行时 **preparePrint** / **confirmPrint**（**AS-BAS-PRT-001**）；物理出纸与静默打印（**OpenHIS.Win** Host，**非**设计器）；legacy SqlReport（已删除） 模板 |
| **涉及域** | **PrintTemplate**（主）；**PrintBinding**（配置）；运行时消费 **PrintBinding** 解析结果读取 **PrintTemplate** published |
| **编排权威** | 以本文 **§2、§4** 为准 |

### 1.2 角色与关键动作

| 角色 | 关键动作 |
| --- | --- |
| **信息科 / 实施** | 模板页：列表筛选 → 新建 **busNo** → 进入 Web 设计页 → 保存草稿 → 发布 → 可选复制草稿再改；预览样例数据 |
| **信息科 / 院点管理员** | 绑定页：查看出厂场景列表（2 条 seed）→ 为每场景选择 **busNo**（须 published 候选）→ 保存**租户** override |
| **Web 设计页** | 路由 `print-template/designer`：`ensureDesignDraft` 加载 → hiprint `design()` 编辑 PrintJson → saveDraft / publish |
| **系统** | 列表合并平台出厂 + 租户模板；发布前校验 PrintJson；绑定保存须持久化租户级配置 |

### 1.3 术语与口径约定

| 术语 | 口径 |
| --- | --- |
| **PrintTemplate** | PrintJson 版式载体；状态 **draft** / **published** / **archived** |
| **busNo** | 全局模板业务键；API 与 Request **`busNo`**（全面改名，不再使用 PrintCode 类名） |
| **PrintBinding** | 稳定 **bindingCode** → **busNo**；**本期仅租户** override（`organization_id` 恒 NULL） |
| **PrintJson** | 存于 `print_json`；mm 坐标；禁止脚本键 |
| **预览** | Web：**getByBusNo** + **vue-plugin-hiprint** `preview(samplePrintData)`（浏览器内；非服务端出图） |
| **设计** | Web：**vue-plugin-hiprint** 画布设计器；读写走模板 IF（与列表页同一 Bearer 会话） |

### 1.4 本期范围与非目标

| 类别 | 内容 |
| --- | --- |
| **§1.4.1 模板页（本期契约）** | IF-BAS-PRT-11～17（含 **ensureDesignDraft**、**deleteTemplate**） |
| **§1.4.2 绑定页（本期契约）** | IF-BAS-PRT-21～22 |
| **§1.4.3 Web 设计器** | `designer/index.vue` + hiprint；消费 IF-13/14/15、ensureDesignDraft |
| **非目标（设计器）** | **OpenHIS.Win.Designer** / 独立 exe 画布设计器（**本期不做**） |
| **非目标** | 模板逻辑删除 UI；**院点**级 PrintTemplate / PrintBinding **organization** override（DM §9.1 deferred）；绑定页 **resolve**（供业务页，非本页 IF）；validatePrintJson 独立按钮（API 存在，UI 未暴露） |
| **archived 语义（本期在内）** | publish 将旧 **published** 标 **archived**；列表**默认不展示** archived，筛选 `status=archived` 可查历史版本（**BR-PRT-012**） |
| **非目标（菜单遗留）** | health V1 仍存在 Report 模块下旧「打印模板配置」菜单；Print Flyway V3 已注册新菜单，院点以 **print-binding** 页为准 |

### 1.5 领域清单（聚合 + 职责 + DM 路径）

| 聚合（域） | 在本场景中的职责（摘要） | DM 路径 |
| --- | --- | --- |
| **PrintTemplate** | 模板 CRUD、draft/publish、PrintJson 校验 | `DM-PRT-001-PrintTemplate-画布打印模板.md` |
| **PrintBinding** | 场景码 → busNo；**租户**配置（院点 binding deferred） | `DM-PRT-003-PrintBinding-业务场景模板绑定.md` |

### 1.6 业务规则（BR-*）

| 规则 ID | 描述 | 绑定 IF |
| --- | --- | --- |
| **BR-PRT-001** | `tenant_id` 非空；平台模板 tenant=0 | 11～16 |
| **BR-PRT-002** | **busNo** 非空 trim | 12～16、21～22 |
| **BR-PRT-003** | **print_json** 非空且通过校验器 | 12、14、15 |
| **BR-PRT-004** | 仅 **draft** 可 publish | 15 |
| **BR-PRT-005** | publish 时 version 递增 | 15 |
| **BR-PRT-007** | 平台 tenant 不查 draft；租户定制走 fork | 11、13、16 |
| **BR-PRT-008** | 禁止 formatter/styler/customScript | 12、14、15 |
| **BR-PRT-BND-001** | **binding_code** 非空 | 21、22 |
| **BR-PRT-BND-002** | 目标 **busNo** 非空 | 22 |
| **BR-PRT-BND-003** | `(tenant, organization, binding_code)` 唯一 | 22 |
| **BR-PRT-BND-005** | 解析顺序：**租户 → 平台**（院点层 deferred） | 21 |
| **BR-PRT-DES-QRY-001** | 列表合并平台 + 租户，同 **busNo** 租户覆盖出厂 | 11 |
| **BR-PRT-DES-QRY-002** | 设计态加载：**tenant draft 优先**，否则 published（含平台 fallback） | 13、14 |
| **BR-PRT-BND-UI-001** | 绑定候选仅展示可选用 **PrintTemplate**（published 为主；UI 展示 status） | 21 |

---

## 2. 页面与接口总览（契约概览）

| IF 编号 | 用户动作 | 接口语义（业务） | 读/写 | 关键规则（BR） | 关键状态变化（自然语言） | 涉及领域 | 查询服务 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| IF-BAS-PRT-11 | 模板页：关键词/状态筛选、查询、重置、翻页 | 分页展示合并后的 **PrintTemplate** 列表项 | 读 | BR-PRT-DES-QRY-001 | 无 | PrintTemplate | —（领域 Manager 合并列表） |
| IF-BAS-PRT-12 | 模板页：新建 → 确定 | 创建租户 **draft** 模板（空白 PrintJson；**busNo 后端生成**） | 写 | BR-PRT-002、003、007 | 新建 **draft** 行 | PrintTemplate | — |
| IF-BAS-PRT-13 | 模板页：预览；设计器：打开加载 | 按 **busNo** 解析设计态模板（draft 优先） | 读 | BR-PRT-DES-QRY-002 | 无 | PrintTemplate | — |
| IF-BAS-PRT-13b | 设计器：进入编辑 | **ensureDesignDraft**：无 draft 时从 published fork | 写 | BR-PRT-007 | 新增或返回 **draft** | PrintTemplate | — |
| IF-BAS-PRT-14 | 设计器：保存 | 保存 **draft** PrintJson（及可选 samplePrintData） | 写 | BR-PRT-003、008 | 保持/置 **draft** | PrintTemplate | — |
| IF-BAS-PRT-15 | 模板页/设计器：发布 | 自 draft **插入**新 **published** 行（version+1），旧 published→**archived**，draft 删除 | 写 | BR-PRT-003、004、005 | 新 published 行；旧 published archived | PrintTemplate | — |
| IF-BAS-PRT-16 | 模板页：复制草稿（published 行） | 从 **published** 复制为新 **draft**（无 draft 则新建） | 写 | BR-PRT-007 | 新增 **draft** 或返回已有 draft | PrintTemplate | — |
| IF-BAS-PRT-17 | 模板页：预览（组合） | IF-13 取模板 + hiprint 浏览器预览 | 读 | BR-PRT-003 | 无 | PrintTemplate | — |
| IF-BAS-PRT-18 | 模板页：删除 | 软删指定模板（draft/archived 等） | 写 | BR-PRT-002 | 软删 | PrintTemplate | — |
| IF-BAS-PRT-21 | 绑定页：进入/重置 | 加载场景绑定清单 + 每场景可选 **busNo** 候选 | 读 | BR-PRT-BND-001、005、UI-001 | 无 | PrintBinding + PrintTemplate | — |
| IF-BAS-PRT-22 | 绑定页：保存 | 持久化**租户** **bindingCode→busNo** 映射 | 写 | BR-PRT-BND-002、003 | upsert 绑定行 | PrintBinding | — |

---

## 3. 缺口跟踪（Gap）

| Gap ID | 缺口类型 | 文件 + 章节 | 缺什么 / 不一致点 | 关闭条件 | 影响 IF | 优先级 | 状态 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| G-PRT-BND-FE-01 | 前端 | 绑定页 | `print-binding/index.vue` 与 `api/print/print-binding.ts` 待建 | 前端 API + 页面落地 | 21、22 | P2 | Open |
| G-PRT-TPL-02 | 范围 defer | `DM-PRT-001` §9.1、BR-PRT-011 | **院点 organization override** 本期不做 | 产品确认 defer（2026-06-22） | 11、13、21 | P1 | **Deferred** |

> **说明**：IF-BAS-PRT-11～17、21～22 **后端与 Web 设计器已实现**；`createBlankDraft` 由后端生成 busNo；运行时场景码见 README-PRT `scenarioCode` 表（含 **`fin-price-adjustment-print`** 调价单；health-dev **V24** 扩场景种子，V74 建表）。

---

## 4. 接口编排说明书（业务步骤）

### IF-BAS-PRT-11 模板分页列表

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 用户进入打印模板页或点击查询/重置/翻页 |
| **主路径步骤** | 取当前租户 → 合并平台 (tenant=0) 与租户模板，同 **busNo** 租户覆盖 → 可选 keyword（busNo/name 模糊）、status 过滤 → 内存分页排序 → 返回 id、busNo、name、status、version、schemaVersion |
| **关键分支** | 无 keyword/status 时返回合并全集 |
| **失败分支** | 查询失败 → EX-PRT-DES-101 |
| **查询侧说明** | 纯读 |

### IF-BAS-PRT-12 新建空白模板

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 用户点「新建」并填写 **busNo**、可选 name |
| **主路径步骤** | 校验 **busNo** 非空 → 校验 PrintJson（空白合法结构）→ 查找租户是否已有同 **busNo** draft → 无则新建行：tenant=当前、status=**draft**、version 初值 1、写入默认 PrintJson → 保存 |
| **关键分支** | 已有 draft → 覆盖 PrintJson/名称（同 saveDraft 语义） |
| **失败分支** | busNo 空 → EX-PRT-DES-201；JSON 校验失败 → EX-PRT-DES-202 |
| **查询侧说明** | 写 |

### IF-BAS-PRT-13 按 busNo 加载（设计/预览）

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 预览弹窗打开；或 Web 设计页 `designer/index.vue` 加载 |
| **主路径步骤** | **resolveForDesign**：优先租户 **draft**，否则 **published**（租户无则平台出厂 fallback）→ 返回 busNo、name、status、version、PrintJson、samplePrintData |
| **关键分支** | 无任何模板 → EX-PRT-DES-102 |
| **失败分支** | 未登录/未选租户（设计器）→ EX-PRT-DES-103 |
| **查询侧说明** | 纯读 |

### IF-BAS-PRT-14 保存草稿

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 设计器点击保存（Web 新建走 IF-12，语义合并于此） |
| **主路径步骤** | PrintJson 校验 → 定位租户 draft（或新建）→ 写入 print_json、sample_print_data、name → status=**draft** → 持久化 |
| **关键分支** | 平台 tenant=0 **不**产生 draft（租户须 fork 后编辑） |
| **失败分支** | 校验失败 → EX-PRT-DES-202 |
| **查询侧说明** | 写 |

### IF-BAS-PRT-15 发布

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 用户对 **draft** 行点「发布」，或设计器发布 |
| **主路径步骤** | 定位租户 **draft**（若无 draft 则先 resolve 设计态）→ PrintJson 校验 → 若当前非 draft 则先 **fork** 新 draft → **publish**：**插入**新 **published** 行（version=同 scope 最新 published+1）→ 同 busNo 旧 **published** 标 **archived** → 源 **draft** 软删或硬删 |
| **关键分支** | 已是 published 且无 draft：fork 后再 publish |
| **失败分支** | 非 draft 且 fork 失败 → EX-PRT-DES-203；校验失败 → EX-PRT-DES-202 |
| **查询侧说明** | 写 |

### IF-BAS-PRT-16 复制草稿

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 用户对 **published** 行点「复制草稿」 |
| **主路径步骤** | 查 **published** → 若已有同 **busNo** draft 直接返回 → 否则从 published 复制 PrintJson 等字段插入新 **draft** 行 |
| **关键分支** | 已有 draft 不重复 fork |
| **失败分支** | 无 published → EX-PRT-DES-102 |
| **查询侧说明** | 写 |

### IF-BAS-PRT-17 预览（Web 组合）

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 用户点「预览」 |
| **主路径步骤** | 调用 IF-BAS-PRT-13 → 取 **samplePrintData**（无则用元素 testData）作 printData → **vue-plugin-hiprint** `preview()` → 弹窗或新窗口展示 |
| **关键分支** | 无 PrintJson / 渲染失败 → 前端提示 |
| **失败分支** | hiprint 未加载 → EX-PRT-DES-104 |
| **查询侧说明** | 读 + 浏览器内渲染 |

### IF-BAS-PRT-21 加载绑定配置

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 用户进入院点绑定页或点「重置」 |
| **主路径步骤** | 加载出厂 **bindingCode** 清单（V5 seed，**2 条**）→ 解析当前**租户** override → 每行附带 bindingName、当前 **busNo**、是否租户 override → 附带候选 **PrintTemplate** 列表（published + 租户 draft，**BR-PRT-BND-UI-001**） |
| **关键分支** | 无 override 时使用平台默认 **busNo** |
| **失败分支** | 后端未实现 → EX-PRT-BND-101（当前） |
| **查询侧说明** | 纯读 |

### IF-BAS-PRT-22 保存绑定配置

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 用户修改下拉后点「保存」 |
| **主路径步骤** | 校验每个 **bindingCode** 对应 **busNo** 非空 → 校验目标 **busNo** 存在 **published** 模板 → upsert **租户**绑定行（`organization_id` NULL）→ 返回最新配置快照 |
| **关键分支** | 部分场景未改 → 仍全量提交 bindings map |
| **失败分支** | 校验失败 → EX-PRT-BND-201；后端未实现 → EX-PRT-BND-101 |
| **查询侧说明** | 写 |

---

## 5. 业务异常与用户提示

| 异常 ID | 场景 | 用户提示（语义） | 可重试 |
| --- | --- | --- | --- |
| EX-PRT-DES-101 | 模板列表加载失败 | 模板列表加载失败，请稍后重试 | 是 |
| EX-PRT-DES-102 | busNo 无模板 | 未找到模板：{busNo} | 否 |
| EX-PRT-DES-103 | 设计器无登录/租户 | 请先登录并选择租户 | 是 |
| EX-PRT-DES-104 | 预览组件不可用 | 打印预览组件加载失败，请刷新重试 | 是 |
| EX-PRT-DES-201 | busNo 为空 | 请输入 busNo | 是 |
| EX-PRT-DES-202 | PrintJson 校验失败 | 模板格式不合法：{摘要} | 是 |
| EX-PRT-DES-203 | 发布状态不允许 | 当前状态不允许发布 | 否 |
| EX-PRT-BND-101 | 绑定 API 未就绪 | 院点绑定保存失败（服务未配置） | 是 |
| EX-PRT-BND-201 | busNo 为空或模板不存在 | 请为所有场景选择有效模板 | 是 |

---

## 6. 事件语义与消费方

| 事件 | 触发 IF | 消费方（语义） |
| --- | --- | --- |
| `PrintTemplatePublishedEvent`（规划） | 15 | 可选缓存刷新；业务 **preparePrint** 读取最新 published |
| `PrintTemplateDraftSavedEvent`（规划） | 12、14 | 可选审计 |
| — | 22 | 绑定保存后，业务页 **resolvePrintCode** / **preparePrint(bindingCode)** 读取新 **busNo** |

> 首期**不强制**发域事件；以表状态为准。

---

## 7. 非功能性需求

| 维度 | 要求 |
| --- | --- |
| **性能** | 模板列表内存合并后分页，租户模板量百级可接受；绑定候选列表随 published 模板数线性增长 |
| **权限** | 模板与绑定方法级均为 **`Print:design`**（与 Flyway V3 菜单一致） |
| **一致性** | 发布/保存单事务；绑定保存与模板发布无跨聚合强一致（最终一致：绑定指向的 busNo 须已 published） |
| **安全** | PrintJson 禁止脚本键；设计页与列表同 Web Bearer 会话 |
| **前端依赖** | `vue-plugin-hiprint`（npm）；设计页按需 `ensureHiprintReady()` |

---

## 8. 测试要点（业务视角）

| 类别 | 要点 |
| --- | --- |
| **模板列表** | 平台出厂 + 租户 override 同 busNo；keyword/status 筛选 |
| **新建/发布** | 空白模板 → 设计 → 发布 version=1；再次发布 version 递增且旧 published 为 archived |
| **fork** | published 复制草稿；已有 draft 不重复 |
| **预览** | samplePrintData hiprint 预览 |
| **Web 设计页** | design 路由加载 → 拖拽保存 → publish 后列表 version 递增 |
| **绑定** | 改场景 busNo 保存；刷新后租户 override 生效；候选仅含可用模板 |
| **负向** | 非法 PrintJson 拒绝发布；busNo 空拒绝 |

---

## 9. 关联文档与变更历史

### 9.1 关联文档

| 文档 | 关系 |
| --- | --- |
| `DM-PRT-001-PrintTemplate-画布打印模板.md` | 模板聚合 |
| `DM-PRT-003-PrintBinding-业务场景模板绑定.md` | 绑定聚合 |
| `AS-BAS-MST-018-打印日志查看.md` | 同模块审计只读 |
| `AS-BAS-PRT-001-打印任务运行时.md` | preparePrint / confirmPrint（运行时 Host 出纸） |
| `openhis-pro-ui` · `vue-plugin-hiprint` | Web 设计器与预览参考 |

### 9.2 变更历史

| 版本 | 日期 | 变更摘要 |
| --- | --- | --- |
| v1.4 | 2026-07-26 | 场景码表交叉：**`fin-price-adjustment-print`**；注明 V24 扩场景 / V74 建表分工（**README-PRT**） |
| v1.2 | 2026-06-22 | **产品定稿**：Web hiprint 设计器；取消 OpenHIS.Win exe 设计器；IF-17 改为浏览器预览 |
| v1.1 | 2026-06-22 | Gap 同步；关联 AS-BAS-PRT-001 |
| v1.0 | 2026-06-22 | 首版：模板 IF-11～17 + 绑定 IF-21～22 |
