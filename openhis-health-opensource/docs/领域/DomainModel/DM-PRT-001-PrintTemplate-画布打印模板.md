# DM-PRT-001 PrintTemplate 画布打印模板

> **限界上下文**：本地扩展 · 画布打印（Print）  
> **代码模块**：`zxplat/zxplat-*`  
> **聚合类型**：单表实体聚合（`print_json` 为 JSON 文档载荷，非子表）  
> **版本**：v1.7 · **日期**：2026-06-28  
> **Flyway 真源**：`zxplat-domain/.../db/migration/health/V74__prt_print.sql`（`bus_no` + `scenario_code` + JSONB + 含 `status` 唯一键；见 §2.1、§9.2）  
> **决策纪要**：2026-06-22 Print SDD 决策（方案 A 版本模型、publish 插行 + archived、院点 override 本期 defer）

---

## 1. 概述

### 1.1 业务定义

**画布打印模板（PrintTemplate）** 是医疗机构内 **PrintJson** 版式的持久化载体。每条记录对应一个 **`busNo`**（库列 `bus_no`，后端按日序号生成，如 `PT202606260005`）在特定 **租户** 下某一 **状态 + 版本** 的设计快照；**published** 行可带稳定 **`scenarioCode`**（如 `op-rx-print`）供业务页运行时解析。

它回答的核心问题是：「**业务页或 Web 设计器按 busNo 取到的 mm 画布长什么样？**」

- **PrintJson**（字段 `print_json`）为版式真源：panels、printElements、坐标与绑定字段等。
- **`printData`** 由 Vue 在运行时组装，**不**存入本聚合；Java Print **不查业务库**。
- 与 legacy whale-print（layoutJson / flow）**硬隔离**；不兼容 HiprintJson 直接导入。

本文档编码为 **`DM-PRT-001`**；**无 FHIR 标准资源一一映射**（见 §9.5）。

### 1.2 核心职责

| 职责 | 说明 |
| --- | --- |
| 模板生命周期 | `draft` 编辑 → `publish` 插入新 `published` 行（version 递增）→ 同 busNo 旧 `published` 标 `archived` |
| 多租户与出厂模板 | `tenant_id = 0` 为平台出厂模板；租户可 override |
| 院点 override | **本期不做**；`organization_id` 列保留，本期恒 `NULL`（租户全局）；见 §9.1 |
| 设计器协同 | Web **vue-plugin-hiprint** 设计页 + 模板列表经 AppService 读写 draft 或 published |
| 运行时取模板 | `preparePrint` 按 **`scenarioCode`**（或直传 **busNo**）读 **最新 published**（不含 `archived`） |

### 1.3 适用范围

| 场景 | scenarioCode | busNo 示例（种子） |
| --- | --- | --- |
| 门诊收费小票 | `op-charge-print` | `PT202606260005` |
| 门诊处方 | `op-rx-print` | `PT202606260006` |
| 门诊挂号小票 | `op-reg-print` | `PT202606260001` |
| 门诊处置单 | `op-treatment-print` | `PT202606260004` |
| 输液瓶签 | `op-infusionLabel-print` | `PT202606260002` |
| 门诊费用日结单 | `op-dailySettlement-print` | `PT202606260003` |
| 院点自定义 | 新建时可选填 | 后端 `nextBusNo()` 生成 |

### 1.4 与关键上下游及交换边界的关系

| 维度 | 上游 | 本聚合 | 下游 |
| --- | --- | --- | --- |
| 职责 | 信息科 / 实施维护版式 | 模板头 + PrintJson 载荷 | 运行时取 published 模板出纸 |
| 数据 | — | `bus_no`、`print_json` | `printData` 由各业务 AppService → Vue 组装 |
| 引用 | — | `PrintTemplateId` | preparePrint、设计器、PrintBinding 解析后的 busNo |

> **边界**：本聚合**不**创建 ChargeItem、MedicationRequest 等业务事实；**不**在 Java 端查库组装 printData。

### 1.5 产品装配

Print 为 **HIS 内置能力**（非增值包）。`prt_template` 随 health-app 启动执行 Flyway；菜单 **基础数据 → 打印模板**。

---

## 2. 聚合根与持久化形态

### 2.1 持久化形态

**单表聚合**；无独立子实体表。`print_json`、`sample_print_data` 为 **JSONB**。

| 物理表 | 说明 |
| --- | --- |
| `prt_template` | 聚合根；主键 `id` |

**唯一约束（已定稿）**：`(tenant_id, organization_id, bus_no, status, version)`

> **方案 A**：`draft` 与 `published` 可共用同一 `version` 号；`archived` 行保留历史 version。

**建议索引**：

- `idx_prt_template_lookup (tenant_id, bus_no, status, version DESC)`

### 2.2 聚合根标识

| 属性 | 类型 | 说明 |
| --- | --- | --- |
| 模板标识 | `PrintTemplateId` | 系统内部唯一标识，对应主键 `id`（`Long`） |

业务侧稳定身份：**`busNo` + tenant + organization + status + version**。

### 2.3 状态机

```text
                    forkDraft（从当前 published 复制）
  [published] ──────────────────────────────> [draft]
       │                                         │
       │         publish（校验 → 插新 published 行） │
       │         draft 软删或硬删                   │
       └──────── 旧 published ──> [archived] ────┘
```

#### 状态定义（`PrintTemplateStatus` → `status`）

存库 **VARCHAR**，值为枚举 **code**（字符串）：

| code | 说明 | 可编辑 | 可被 preparePrint 读取 |
| --- | --- | --- | --- |
| `draft` | 草稿 | ✓（saveDraft） | ✗ |
| `published` | 已发布（当前有效版） | ✗（须 fork 为新 draft） | ✓（仅最新 published） |
| `archived` | 已归档（历史 published） | ✗ | ✗ |

> **设计态读取**：`getByBusNo` / 设计器加载时 **优先 tenant draft**，否则 **published**（含平台 fallback）；**不**读 `archived`。

#### publish 落库语义（已定稿）

1. 校验源 **draft** 的 PrintJson。  
2. 计算 `nextVersion = 同 scope 最新 published.version + 1`（无 published 则从 1）。  
3. **插入**新行：`status=published`、`version=nextVersion`、复制载荷字段。  
4. 将同 `(tenant, organization, bus_no)` 下**其他** `published` 行改为 `archived`。  
5. 源 **draft** 行 **软删**（`is_deleted=true`）。

---

## 3. 实体

### 3.1 PrintTemplate（聚合根）

| 属性 | 字段名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| 模板标识 | `id` | `Long` | 是 | 主键 |
| 租户 | `tenant_id` | `Long` | 是 | `0` = 平台出厂；见 **BR-PRT-001** |
| 院点 | `organization_id` | `Long` | 否 | **本期恒 NULL**（列保留；院点 override 见 §9.1 deferred） |
| 业务编码 | `bus_no` | `String(64)` | 是 | 模板业务键（API **`busNo`**）；新建由 `PrtTemplateDomainService#nextBusNo` 生成 |
| 场景码 | `scenario_code` | `String(64)` | 否 | 稳定运行时入口（API **`scenarioCode`**）；**published** 行租户内唯一（partial UK） |
| 显示名 | `name` | `String(128)` | 是 | 列表展示 |
| 状态 | `status` | `String(16)` | 是 | `draft` / `published` / `archived` |
| 版本 | `version` | `Integer` | 是 | publish 递增；出厂 seed 从 1 起 |
| 版式 JSON | `print_json` | `JSONB` | 是 | PrintJson 文档；API `PrintJson` |
| 结构版本 | `schema_version` | `Integer` | 是 | 冗余自 JSON 根 `schemaVersion`，默认 1 |
| 预览样例数据 | `sample_print_data` | `JSONB` | 否 | 设计器/Web 预览用；非运行时 printData |
| 审计 | `created_*` / `updated_*` / `is_deleted` | — | — | `BaseAuditedEntity` |

### 3.2 PrintJson 载荷（非独立表）

存储于 `print_json`，根对象结构如下：

```json
{
  "schemaVersion": 1,
  "engine": "Print",
  "panels": [{
    "index": 0,
    "width": 80,
    "height": 200,
    "paperHeader": 49.5,
    "paperFooter": 780,
    "printElements": []
  }]
}
```

| 根字段 | 必填 | 说明 |
| --- | --- | --- |
| `schemaVersion` | ✓ | 当前 `1` |
| `engine` | ✓ | 固定 `"Print"` |
| `panels[]` | ✓ | 至少 1 项 |
| `panels[].width` / `height` | ✓ | 纸型宽高，**mm** |
| `panels[].paperHeader` / `paperFooter` | | 页眉/页尾线 Y 坐标（**pt**，自纸张顶部）；与 `options.top`（mm）单位不同；OpenHIS.Win 分页续表用 |
| `panels[].printElements[]` | ✓ | 画布元素列表 |

元素对象示例：

```json
{
  "printElementType": { "type": "text", "title": "文本" },
  "options": {
    "left": 10, "top": 10, "width": 100, "height": 20,
    "field": "patientName", "title": "姓名", "testData": "张三",
    "fontSize": 12, "fontWeight": "normal", "textAlign": "left"
  }
}
```

| 规则 | 说明 |
| --- | --- |
| `printElementType.type` | `text` / `table` / `hline` / `longText` / `image` / `barcode` / `qrcode` / `vline` / `rect` |
| `options.left/top/width/height` | **pt** 绝对坐标（hiprint 约定，OpenHIS.Win 渲染时转 mm） |
| `options.field` | 绑定运行时 `printData` 路径（点路径或简单键名） |
| `options.testData` | 仅设计预览；运行时忽略 |
| 禁止键 | `formatter`、`styler`、`customScript` |

校验由 **`PrintJsonValidator`** 执行（publish / saveDraft 前）。

---

## 4. 值对象与枚举

### 4.1 PrintTemplateStatus（`status`）

见 §2.3；存库 **VARCHAR**，值为枚举 **code**。

### 4.2 PrtTenantScope

| 常量 | 值 | 说明 |
| --- | --- | --- |
| `PLATFORM_TENANT_ID` | `0` | 出厂模板租户 |

---

## 5. 领域事件

首期 **不强制** 发域事件；可选扩展：

| 事件 | 触发时机 |
| --- | --- |
| `PrintTemplatePublishedEvent` | 新 `published` 行插入成功 |
| `PrintTemplateDraftSavedEvent` | saveDraft 成功 |

---

## 6. 领域服务与应用编排

### 6.1 PrintTemplateManager（domain）

| 方法 | 职责 |
| --- | --- |
| `findPublished(busNo, tenantId)` | 取最新 `published`（不含 `archived`）；租户无则 fallback 平台；**本期忽略 organizationId** |
| `resolveForDesign(busNo, tenantId)` | 设计态：draft 优先，否则 published |
| `findTenantDraft` | 租户草稿（平台 tenant=0 不查 draft） |
| `listMergedForTenant` | 列表：平台 + 租户按 busNo 合并（默认不含 `archived`） |
| `saveDraft` | 置 `draft` 并保存 |
| `publish` | 插新 `published` 行、归档旧 published、删除 draft（§2.3） |
| `forkDraftFromPublished` | 从当前 published 复制新 draft（可与 published 同 version，靠 status 区分） |

### 6.2 PrintTemplateAppService（application）

| 方法 | 权限 | 说明 |
| --- | --- | --- |
| `queryPage` | `Print:design` | 模板分页列表 |
| `getByBusNo` | `Print:design` | 设计器/Web 加载 |
| `saveDraft` | `Print:design` | 保存草稿 |
| `publish` | `Print:design` | 发布 |
| `forkDraft` | `Print:design` | 从 published 复制草稿 |
| `validatePrintJson` | `Print:design` | 仅校验 |

> API / Request 类名统一 **`busNo`**（全面改名，不再使用 `PrintCode` 类名）。

### 6.3 PrintJobAppService.preparePrint（读模板）

经 **DM-PRT-003** 可选解析 `bindingCode` 后，只读 **published** 模板；返回：

| 字段 | 说明 |
| --- | --- |
| `traceId` | 服务端生成；confirm 须带回（**DM-PRT-002**） |
| `busNo` | resolved 模板键 |
| `templateVersion` | resolved published 的 `version` |
| `PrintJson` | 版式文档 |
| `samplePrintData` | 可选 |

**不写库**（trace 持久化在 confirm 侧日志表）。

---

## 7. 不变式

| 编号 | 不变式 | 说明 |
| --- | --- | --- |
| BR-PRT-001 | `tenant_id` 非空 | 平台模板为 `0` |
| BR-PRT-002 | `bus_no` 非空且 trim | |
| BR-PRT-003 | `print_json` 非空且通过 `PrintJsonValidator` | publish / saveDraft |
| BR-PRT-004 | 仅 `draft` 可作为 publish 源 | |
| BR-PRT-005 | publish 插入行 `version` = 同 scope 最新 published + 1（无则从 1） | |
| BR-PRT-006 | preparePrint **仅**读 `published` | |
| BR-PRT-007 | 平台 tenant 无 draft 查询 | 租户定制走 fork + save |
| BR-PRT-008 | 禁止 JSON 内 `formatter` / `styler` / `customScript` 键 | 安全 |
| BR-PRT-009 | element `type` 须在白名单 | text/table/hline/… |
| BR-PRT-010 | `(tenant_id, organization_id, bus_no, status, version)` 唯一 | 库约束 |
| BR-PRT-011 | （deferred）院点 override | 见 §9.1 |
| BR-PRT-012 | `archived` 不参与列表默认查询与 preparePrint | |

### 7.1 版本与 draft/published 共存（已定稿）

- **方案 A**：唯一键含 `status`；`fork` 可插入与 published 同 version 的 draft。  
- **publish**：**插入**新 published 行，**非**原地改 status。  
- **历史**：旧 published → `archived`，不软删（除非产品另行清理策略）。

---

## 8. 上下文映射

### 8.1 上游

| 来源 | 关系 |
| --- | --- |
| 信息科 / 实施 | 维护模板、出厂 seed |
| Windows Designer | **已取消**（本期仅 Web hiprint 设计器） |

### 8.2 下游

| 消费方 | 方式 |
| --- | --- |
| Vue `usePrintJob` | `preparePrint` → Host 打印 → `confirmPrint` |
| Web 模板列表 | CRUD + 预览 |
| **DM-PRT-003 PrintBinding** | `bindingCode` → `busNo` → 本聚合 |

### 8.3 引用规则

| 模型 | 关系 |
| --- | --- |
| DM-PRT-002 PrintLog | 逻辑引用 `bus_no` + `template_version`；无 FK |
| DM-PRT-003 PrintBinding | 引用 `bus_no`；不复制 JSON |

---

## 9. 补充

### 9.1 院点 override（deferred）

**本期不做**。`findPublished` 仅：

1. `(tenant_id, bus_no)` 租户最新 `published`  
2. `(0, bus_no)` 平台出厂 fallback  

`organization_id` 列保留，Flyway 与实体保留字段，值为 `NULL`。

### 9.2 列类型

**本期定稿 JSONB**：`print_json`、`sample_print_data`（清库重跑，无 TEXT 迁移路径）。

### 9.3 版本模型

见 **§7.1**（已定稿，不再阻塞 SDD）。

### 9.4 出厂 seed（V74）

| scenarioCode | busNo（示例） | 名称 |
| --- | --- | --- |
| `op-charge-print` | `PT202606260005` | 门诊收费小票 |
| `op-rx-print` | `PT202606260006` | 门诊处方 |
| `op-reg-print` | `PT202606260001` | 门诊挂号小票 |
| `op-treatment-print` | `PT202606260004` | 门诊处置单 |
| `op-infusionLabel-print` | `PT202606260002` | 输液瓶签 |
| `op-dailySettlement-print` | `PT202606260003` | 门诊费用日结单 |

### 9.5 与 FHIR / 标准的映射说明

本聚合为院内 **版式配置模型**，**无**与 FHIR 资源的一一强制映射：

| 概念 | FHIR 近似（仅概念参考，非映射） |
| --- | --- |
| 已打印文档 | `DocumentReference` + `Binary`（PDF/图片） |
| 操作审计 | `AuditEvent`（粒度与字段均不等同） |
| 版式模板 / busNo | **无标准资源** |

---

## 10. 变更历史

| 版本 | 日期 | 摘要 |
| --- | --- | --- |
| v1.6 | 2026-06-22 | 设计器定稿：Web vue-plugin-hiprint；取消 OpenHIS.Win exe 设计器 |
| v1.5 | 2026-06-22 | SDD 决策对齐：方案 A 唯一键、publish 插行 + archived、JSONB、院点 defer；prepare 返回 traceId |
| v1.4 | 2026-06-22 | 聚合命名统一为 **PrintTemplate**（原 PrtTemplate） |
| v1.3 | 2026-06-22 | 明确新模块 Flyway 自 V1 起即 `bus_no` |
| v1.2 | 2026-06-22 | 迁入 `health/DomainModel` |
| v1.1 | 2026-06-22 | 业务键统一为 `bus_no`（API `busNo`） |
| v1.0 | 2026-06-22 | 初稿 |

<!-- MODE-B-METHOD-INVENTORY 2026-09-23 -->

## 附录 · 模式 B 已实现方法清单（2026-09-23）

> 自 zxplat-domain 对应 *DomainService / *QueryService 抽取 public 方法名，用于关闭方法白名单 DOC-DRIFT。细规仍以正文 §6 与源码为准；本表不替代九章成稿。

### DomainService：`PrtTemplateDomainService`

| 方法 |
|------|
| `nextBusNo` |
| `saveDraft` |
| `publish` |
| `forkDraftFromPublished` |
| `deleteByBusNo` |

### QueryService：`PrtTemplateQueryService`

| 方法 |
|------|
| `findPublished` |
| `resolveForDesign` |
| `findTenantDraft` |
| `findDraftById` |
| `listMergedForTenant` |
| `findPublishedByScenarioCode` |

