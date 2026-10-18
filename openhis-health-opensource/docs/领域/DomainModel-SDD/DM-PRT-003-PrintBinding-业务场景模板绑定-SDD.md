# DM-PRT-003 PrintBinding 业务场景模板绑定 · DomainModel-SDD

> **对应 DM**：`docs/领域/DomainModel/DM-PRT-003-PrintBinding-业务场景模板绑定.md`  
> **目标工程**：`zxplat/whale-zxplat-domain`  
> **限界上下文**：Print 本地扩展  
> **聚合**：PrintBinding（代码实体 `PrtPrintBinding`）

---

## 输入完整性检查结果

| 最小输入项 | 状态 | 说明 |
| ---------- | ---- | ---- |
| DM 路径 | 满足 | DM-PRT-003 v1.4 |
| SDD 输出路径 | 满足 | 本文件 |
| 目标模块 | 满足 | `print-domain` |
| 包规则 | 满足 | `com.zxplat.domain.custom` |
| 固定技术口径 | 满足 | 见 §9 |
| DM 第 6 章写/读白名单 | 满足 | §5 写 1 + 读 3 |
| DM 第 7 章 BR | 满足 | §6 |
| DM 第 5 章事件 | 满足 | §7（0 事件） |
| Flyway V5 seed | 满足 | 2 条平台默认 |

---

## 1. 设计目标与边界

### 1.1 设计目标

- 固化 `prt_print_binding` 与 `PrintBindingResolver`：`bindingCode` → `busNo`。
- **租户 override + 平台 seed fallback**；`organization_id` 恒 NULL。
- **saveTenantBindings** 全量 upsert 租户映射；保存时校验目标 **published** 模板存在。

### 1.2 边界

- **在内**：Resolver 读、save 写、实体 UK、enabled 语义。
- **在外**：绑定管理页 DTO 装配、`PrintBindingAppService`、preparePrint 编排。
- **跨域**：保存时调用 `PrtTemplateManager#findPublished` 校验 busNo。

---

## 2. 工程落点与包结构

### 2.1 Maven 模块

- 模块：`print-domain`。

### 2.2 包结构（`com.zxplat.domain.custom`）

| 相对包 | 内容 |
| ------ | ---- |
| `entity` | `PrtPrintBinding` |
| `command` | `SaveTenantBindingsCommand` |
| `repository` | `PrtPrintBindingRepository` |
| `service` | `PrintBindingResolver` |

---

## 3. 类型 / 字段命名 / 注解规则

| 规则 | 说明 |
| ---- | ---- |
| 主键 | `Long id` |
| `tenantId` | `0` = 平台 seed |
| `organizationId` | 本期恒 NULL |
| `enabled` | `Boolean` |
| 异常 | 无定义 binding → `PRT-404`；busNo 无 published → `PRT-422` |
| 仓储 | **禁止** `@Query` |

---

## 4. 实体 / 值对象 / 枚举 / 仓储 / 查询契约

### 4.1 表与实体映射

| 逻辑表 | 实体类 | 说明 |
| ------ | ------ | ---- |
| `prt_print_binding` | `PrtPrintBinding` | 配置聚合根 |

### 4.2 聚合根 `PrtPrintBinding`（字段级契约）

| 字段名（Java） | 类型 | 必填 | 说明 / 约束 |
| -------------- | ---- | ---- | ----------- |
| `id` | `Long` | 系统 | 主键 |
| `tenantId` | `Long` | 是 | `0` = 平台默认 |
| `organizationId` | `Long` | 否 | **本期恒 NULL** |
| `bindingCode` | `String` | 是 | 稳定入口码；**BR-PRT-BND-001** |
| `busNo` | `String` | 是 | 指向模板 busNo；**BR-PRT-BND-002** |
| `name` | `String` | 否 | seed 显示名 |
| `enabled` | `Boolean` | 是 | 默认 true；false 不参与 resolve |
| `createdAt` 等 / `isDeleted` | — | — | `BaseAuditedEntity` |

**UK**：`(tenant_id, organization_id, binding_code)`。

### 4.3 子实体

无。本聚合为单表。

### 4.5 仓储接口

`PrtPrintBindingRepository extends JpaRepository<PrtPrintBinding, Long>`

| 方法名 | 用途 |
| ------ | ---- |
| `findByTenantIdAndBindingCodeAndOrganizationIdAndIsDeletedFalse` | resolve |
| `findByTenantIdAndIsDeletedFalse` | 租户列表 |
| `findByTenantIdAndOrganizationIdIsNullAndIsDeletedFalse` | 租户 binding（org NULL） |

### 4.6 查询模型（`queries.model`）

读方法直接返回 `PrtPrintBinding` 实体或 `List<PrtPrintBinding>` / `String`（resolve 返回 busNo）。

#### 4.6.1 `resolve` 出参

直接返回 `String busNo`，无独立 QueryModel。

#### 4.6.2 `listDefinitions` 出参

直接返回 `List<PrtPrintBinding>`（平台 seed），无独立 QueryModel。

#### 4.6.3 `listOverridesForTenant` 出参

直接返回 `List<PrtPrintBinding>`，无独立 QueryModel。

#### 4.6.4 V5 出厂 seed（与 Flyway 一致）

| bindingCode | 默认 busNo | name（示意） |
| ----------- | ---------- | ------------ |
| `charge.cashier.receipt` | `charge_receipt` | 收费小票 |
| `outpatient.rx.print` | `outpatient_prescription` | 门诊处方 |

#### 4.6.5 `resolve` 解析顺序（实现伪码）

```text
1. trim(bindingCode)
2. row = repo(tenantId=effectiveTenant, org=NULL, code, enabled=true)
3. if row != null → return row.busNo
4. row = repo(tenantId=0, org=NULL, code, enabled=true)  // 平台 seed
5. if row != null → return row.busNo
6. throw BusinessException PRT-404
```

#### 4.6.6 `loadConfig` 合并语义（App Asm，非 domain QueryModel）

| 字段 | 说明 |
| ---- | ---- |
| `bindings[].bindingCode` | 来自 seed 全量 |
| `bindings[].busNo` | 租户 override 优先，否则 seed 默认 |
| `bindings[].tenantOverride` | 是否存在租户行 |
| `candidates[]` | listMergedForTenant 过滤 draft/published |

#### 4.6.7 `saveConfig` Request → Command 映射（App 协作）

| Request 字段 | Command 字段 | 说明 |
| ------------ | ------------ | ---- |
| `bindings` (Map) | `bindingCodeToBusNo` | 全量 bindingCode→busNo |
| — | `tenantId` | TenantContext.require |

---

## 5. 写侧与读侧白名单（命令字段全集）

### 5.1 写侧 — `PrintBindingResolver`

#### 5.1.1 `saveTenantBindings(SaveTenantBindingsCommand cmd)` → `void`

**命令文件**：`SaveTenantBindingsCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `tenantId` | `Long` | 非空；须为当前租户 |
| `bindingCodeToBusNo` | `Map<String, String>` | 全量；key=bindingCode，value=busNo |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | — |
| `statusTransitionMethod` | upsert；`organizationId=NULL` |
| `repositoryChecks` | 每个 busNo 须 `findPublished` 成功；**BR-PRT-BND-002** |
| `eventPublishAnchor` | 无 |
| `unitTestCaseId` | TC-PRT-BND-SAVE-001 |

**方法契约摘要**：trim map keys；逐 entry：校验 busNo trim 非空 → `findPublished(busNo)` → upsert 租户行（organizationId=NULL，enabled=true）；UK **BR-PRT-BND-003**；失败无 published → `PRT-422`。**注释锚点**：校验 busNo → 逐条 load/insert/update → save。

**saveTenantBindings 逐步说明**：

1. 拒绝 tenantId=0（不可修改平台 seed）。
2. 对 map 中每个 bindingCode：trim；busNo trim 非空。
3. 调用 `PrtTemplateManager#findPublished(busNo, tenantId)` 确认模板存在。
4. `findByTenantIdAndBindingCodeAndOrganizationIdAndIsDeletedFalse` 查已有行。
5. 存在则 update busNo；不存在则 insert 新行（organizationId=NULL，enabled=true）。
6. **本期不**软删 map 外历史 override 行（全量提交仅 upsert 传入项）。

---

### 5.2 读侧 — `PrintBindingResolver`

| 方法 | 入参 | 出参（类名，字段见 §4.6） | 关键实现 | `unitTestCaseId` |
| ---- | ---- | ------------------------- | -------- | ---------------- |
| `resolve` | `bindingCode`, `tenantId` | `String` busNo | 租户 enabled→平台 seed→fallback；**BR-PRT-BND-005** | TC-PRT-BND-RES-001 |
| `listDefinitions` | — | `List<PrtPrintBinding>` | `tenant_id=0` 未软删；供 IF-21 场景清单 | TC-PRT-BND-LST-001 |
| `listOverridesForTenant` | `tenantId` | `List<PrtPrintBinding>` | `tenant_id=tenantId` 且 org NULL | TC-PRT-BND-LST-002 |

**resolve 分支说明**：

| 步骤 | 条件 | 动作 |
| ---- | ---- | ---- |
| A | 租户行存在且 enabled | 返回租户 busNo |
| B | 否则平台 seed 行存在且 enabled | 返回平台 busNo |
| C | 否则 | `BusinessException PRT-404` |

**listDefinitions 与 listOverridesForTenant 协作**：`loadConfig` App 层先取 definitions 全量 seed，再 overlays overrides；同一 bindingCode 租户行覆盖展示 busNo，并标记 `tenantOverride=true`。

**读方法注释锚点（统一）**：trim bindingCode → 仓储查询（软删过滤）→ 返回结果（无写库）。

---

## 6. 不变式映射（DM §7）

| BR 编号 | 实现策略摘要 |
| ------- | ------------ |
| BR-PRT-BND-001 | resolve/save 前 trim bindingCode |
| BR-PRT-BND-002 | save 时 findPublished 校验 busNo |
| BR-PRT-BND-003 | UK 租户+org+bindingCode |
| BR-PRT-BND-004 | enabled=false 跳过 resolve |
| BR-PRT-BND-005 | resolve 顺序：租户→平台 |

---

## 7. 事件契约与触发规则（DM §5）

首期 **不发** 域事件；下表为空集。

| 事件类名 | 事件类包路径 | 最小载荷（字段） | 触发方法 |
| -------- | ------------ | ---------------- | -------- |

**发布时机**：无。

---

## 8. 删除与可见性契约

- 平台 seed（tenant=0）**不可**由租户 save 修改；仅租户 override 行可 upsert。
- 软删默认过滤；enabled=false 不参与 resolve 但仍可能出现在管理端只读合并（由 App 决定是否展示）。
- **organization_id** 本期恒 NULL；UK 仍含该列以兼容院点 deferred。
- 租户 override 与平台 seed **共存**：同 bindingCode 不同 tenant_id 两行，resolve 时租户优先。

---

## 9. 框架优先适配规则

- 实体 `BaseAuditedEntity` + `IMultiTenant`。
- Repository 禁 `@Query`；派生方法命名与 §4.5 一致。
- `PrintBindingResolver` 可注入 `PrtTemplateManager` 校验 published（领域内协作，**禁止** application 依赖 health-application）。
- `resolve` trim bindingCode；`enabled=false` 行跳过。
- 平台 seed（tenant=0）**不可**被 `saveTenantBindings` 修改；仅 upsert `tenantId=当前租户` 行。
- `saveTenantBindings` 全量 map：未出现在 map 中的租户 override **不**自动删除（若产品要求全量替换软删，须在 App IF-22 Gate 扩展——**本期**按 map upsert 仅更新传入项）。
- V5 seed 须保证 `charge.cashier.receipt`、`outpatient.rx.print` 两条平台默认可 resolve。
- `listDefinitions` 仅返回 `tenant_id=0` 且未软删行。
- 遵循项目级成码门禁。

---

## 10. 生成与验收清单

- [ ] `PrtPrintBinding` 与 DM §3.1、V5 Flyway 表结构对齐
- [ ] UK `(tenant_id, organization_id, binding_code)` 与 organization NULL 一致
- [ ] `SaveTenantBindingsCommand` + `saveTenantBindings` 写方法
- [ ] `resolve` / `listDefinitions` / `listOverridesForTenant` 读方法
- [ ] 租户 override busNo 覆盖平台 seed 默认
- [ ] 无租户 override 时 fallback 平台 seed busNo
- [ ] 非法 bindingCode（无 seed 定义）→ PRT-404
- [ ] save 指向未 published busNo → PRT-422
- [ ] `PrintBindingAppService` loadConfig/saveConfig 与 AS-SDD-019 编程序列一致
- [ ] `enabled=false` 不参与 resolve
- [ ] `saveTenantBindings` 拒绝 tenantId=0
- [ ] `PrintBindingAppService` HTTP loadConfig/saveConfig 与 AS-SDD-019 一致
- [ ] 单元测试 TC-PRT-BND-RES/LST/SAVE 锚点

---

## 11. 单次会话代码生成门禁（DoD）

### 11.1 前置输入

- 本 SDD + DM-PRT-003 v1.4 + V5 Flyway。

### 11.2 DoD

- `print-domain` 新增 binding 包编译通过。
- `PrintBindingAppService` 可注入 `PrintBindingResolver`。
- V5 Flyway seed 2 条与 DM §9.1 一致。
- `preparePrint` bindingCode 路径调用 `resolve` 后再 `findPublished`。
- 规范合规摘要：注释锚点、P0 rg。

### 11.3 失败处理

- 缺 seed → 补 V5 再测 resolve。
- resolve 仍硬编码 busNo → 改为读 `prt_print_binding`。
- save 未校验 published → 在 `saveTenantBindings` Pre 补 `findPublished`。

---

## 定稿检查清单（可打勾）

- [x] §1～§11 金骨架齐全
- [x] §5.1.1 写方法 1 节
- [x] §4.2 与 DM §3.1 对齐
- [x] §7 0 事件
- [x] §4.6 读方法 3 子节
- [x] 无待补充措辞

---

## 定稿评分（百分制）

| 维度 | 分值 | 自评 |
| ---- | ---- | ---- |
| 输入完整性 | 20 | 20 |
| 方法白名单对齐 | 25 | 24 |
| 不变式映射 | 20 | 20 |
| 事件契约 | 15 | 15 |
| 软删与框架一致 | 10 | 10 |
| 去重与无歧义 | 10 | 9 |
| **总分** | **100** | **98，可进入代码会话** |

---

**本 SDD 正文结束。**
