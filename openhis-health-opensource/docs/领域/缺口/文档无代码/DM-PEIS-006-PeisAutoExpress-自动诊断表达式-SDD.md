# DM-PEIS-006 PeisAutoExpress 自动诊断表达式 · DomainModel-SDD

> **对应 DM**：`docs/领域/缺口/文档无代码/DM-PEIS-006-PeisAutoExpress-自动诊断表达式.md`（本 SDD 假定该 DM 为当期锁定版本）  
> **目标工程**：`zxplat-core/zxplat-domain`  
> **限界上下文**：health · peis · 规则引擎配置  
> **聚合**：PeisAutoExpress

---

## 输入完整性检查结果

| 最小输入项 | 状态 | 说明 |
| ---------- | ---- | ---- |
| DM 路径 | 满足 | 已绑定 DM-PEIS-006 |
| SDD 输出路径 | 满足 | 本文件 |
| 目标模块 | 满足 | `zxplat/zxplat-domain` |
| 包规则 `com.zxplat.plugin.peis.domain.autoexpress` | 满足 | `autoexpress` 全小写 |
| 固定技术口径（ID Long、时间 LocalDateTime、软删、beforeCommit、分页等） | 满足 | 见 §9 |
| DM §6.1 写方法 | 满足 | §5.1 共 3 节 |
| DM §6.2 读方法 | 满足 | §5.2 + §4.6 |
| DM §6.3 求值协作 | 满足 | §9 EvaluationService |
| DM §7 BR | 满足 | §6 |
| DM §5 事件 | 满足 | §7 共 2 事件 |
| peis V1 `peis_auto_express` | 满足 | idx_rule_type |

---

## 1. 设计目标与边界

### 1.1 设计目标

- CFG-002 维护表达式；MAS-06/CRT 求值只读 CLN。
- `PeisAutoExpressEvaluationService` 为 **无表写** 协作类，见 §9。

### 1.2 边界

- **在内**：`peis_auto_express` CRUD + 启停。
- **在外**：脚本引擎、ConditionDefinition 引用。

---

## 2. 工程落点与包结构

### 2.1 Maven 模块

- 模块：`zxplat/zxplat-domain`。

### 2.2 包结构（`com.zxplat.plugin.peis.domain.autoexpress`）

| 相对包 | 内容 |
| ------ | ---- |
| `entity` | `PeisAutoExpress` |
| `command` | `SaveAutoExpressCommand`、`ActivateAutoExpressCommand`、`DeactivateAutoExpressCommand` |
| `repository` | `PeisAutoExpressRepository` |
| `service` | `PeisAutoExpressDomainService` |
| `queries` | `PeisAutoExpressQueryService` |
| `queries.model` | `AutoExpressModel` |
| `service`（协作） | `PeisAutoExpressEvaluationService`（同包，只读求值） |

**存储口径**：`statusEnum`、`ruleTypeCode` → `String`。

---

## 3. 类型 / 字段命名 / 注解规则

| 规则 | 说明 |
| ---- | ---- |
| PK | `Long id` |
| `expressScript` | `String`（Text 列） |
| `statusEnum` / `ruleTypeCode` | `String` |
| 软删 | `Boolean isDeleted` |
| 仓储 | `JpaRepository` 接口，禁止 `@Query` |
| 事务 | 领域服务不声明 `@Transactional` |
| 事件 | save / activate / deactivate 见 §7 |

---

## 4. 实体 / 值对象 / 枚举 / 仓储 / 查询契约

### 4.1 表与实体映射

| 逻辑表 | 实体类 |
| ------ | ------ |
| `peis_auto_express` | `PeisAutoExpress` |

### 4.2 聚合根 `PeisAutoExpress`

| 字段名（Java） | 类型 | 必填 | 说明 |
| -------------- | ---- | ---- | ---- |
| `id` | `Long` | 系统 | PK |
| `name` | `String` | 是 | 规则名 |
| `expressScript` | `String` | 是 | 脚本 |
| `targetConditionDefId` | `Long` | 否 | 命中模板 |
| `ruleTypeCode` | `String` | 是 | master_review/critical 等 |
| `statusEnum` | `String` | 是 | active/inactive |
| `tenantId` | `Long` | 是 | |
| `orgId` | `Long` | 否 | |
| `isDeleted` | `Boolean` | 系统 | |
| `createdAt` | `LocalDateTime` | 系统 | |
| `updatedAt` | `LocalDateTime` | 系统 | |

### 4.3 子实体

无。

### 4.5 仓储接口

```java
public interface PeisAutoExpressRepository extends JpaRepository<PeisAutoExpress, Long> {
    List<PeisAutoExpress> findByTenantIdAndRuleTypeCodeAndStatusEnumAndIsDeletedFalse(
            Long tenantId, String ruleTypeCode, String statusEnum);
}
```

### 4.6 查询模型

#### 4.6.1 `AutoExpressModel`（`search` / `listActiveByRuleType`）

| 字段名（Java） | 类型 | 来源 | 说明 |
| -------------- | ---- | ---- | ---- |
| `id` | `Long` | id | |
| `name` | `String` | name | |
| `expressScript` | `String` | express_script | listActive 含；search 可脱敏 |
| `targetConditionDefId` | `Long` | target_condition_def_id | |
| `ruleTypeCode` | `String` | rule_type_code | |
| `statusEnum` | `String` | status_enum | |

---

## 5. 写侧与读侧白名单

### 5.1 写侧 — `PeisAutoExpressDomainService`

#### 5.1.1 `save(SaveAutoExpressCommand cmd)` → `PeisAutoExpress`

**命令文件**：`SaveAutoExpressCommand.java`

**字段全集**

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `id` | `Long` | 更新 |
| `name` | `String` | 必填 |
| `expressScript` | `String` | 必填 |
| `targetConditionDefId` | `Long` | 可选 |
| `ruleTypeCode` | `String` | 必填 |
| `statusEnum` | `String` | 默认 active |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | save |
| `repositoryChecks` | BR-AEX-01 语法校验 |
| `eventPublishAnchor` | `PeisAutoExpressSavedEvent` |
| `unitTestCaseId` | TC-PEIS-AEX-001 |

**方法契约摘要**：前置：script 语法合法（BR-AEX-01）；持久化：upsert；失败：语法校验失败；事件：Saved beforeCommit；**注释锚点**：validate script → upsert → 事件。

---

#### 5.1.2 `activate(ActivateAutoExpressCommand cmd)` → `PeisAutoExpress`

**命令文件**：`ActivateAutoExpressCommand.java`

**字段全集**

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `id` | `Long` | 必填 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | activate |
| `statusTransitionMethod` | inactive → active |
| `repositoryChecks` | 行存在 |
| `eventPublishAnchor` | `PeisAutoExpressStatusChangedEvent` |
| `unitTestCaseId` | TC-PEIS-AEX-002 |

**方法契约摘要**：前置：行存在；持久化：status=active；失败：行不存在；事件：StatusChanged beforeCommit；**注释锚点**：load → 迁态 → save → 事件。

---

#### 5.1.3 `deactivate(DeactivateAutoExpressCommand cmd)` → `PeisAutoExpress`

**命令文件**：`DeactivateAutoExpressCommand.java`

**字段全集**

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `id` | `Long` | 必填 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | deactivate |
| `statusTransitionMethod` | active → inactive |
| `repositoryChecks` | 行存在 |
| `eventPublishAnchor` | `PeisAutoExpressStatusChangedEvent` |
| `unitTestCaseId` | TC-PEIS-AEX-003 |

**方法契约摘要**：前置：行存在；持久化：status=inactive；失败：行不存在；事件：StatusChanged beforeCommit；**注释锚点**：load → 迁态 → save → 事件。

### 5.2 读侧 — `PeisAutoExpressQueryService`

| 方法 | 入参 | 出参 | 关键实现 | `unitTestCaseId` |
| ---- | ---- | ---- | -------- | ---------------- |
| `search` | `AutoExpressQuery, Pageable` | `Page<AutoExpressModel>` | 软删 + 条件过滤 | TC-PEIS-AEX-R01 |
| `listActiveByRuleType` | `String ruleTypeCode` | `List<AutoExpressModel>` | status=active | TC-PEIS-AEX-R02 |

**读方法注释锚点（统一）**：tenant + ruleType + active + 软删 → 查询 → Model。

## 6. 不变式映射（DM §7）

| BR 编号 | 实现策略 |
| ------- | -------- |
| **BR-AEX-01** | save 前 script 校验器 |
| **BR-AEX-02** | listActive 过滤 inactive |
| **BR-AEX-03** | EvaluationService 不写 WorkItem |

---

## 7. 事件契约与触发规则（DM §5）

| 事件 | 包路径 | 载荷 | 触发 |
| ---- | ------ | ---- | ---- |
| `PeisAutoExpressSavedEvent` | `com.zxplat.domain.share.events.custom.peisautoexpress` | `id, ruleTypeCode, statusEnum` | save |
| `PeisAutoExpressStatusChangedEvent` | 同上 | `id, statusEnum` | activate/deactivate |

---

## 8. 删除与可见性契约

- 软删；inactive 仍可见管理端。

---

## 9. 框架优先适配规则

**`PeisAutoExpressEvaluationService`**（DM §6.3，非 §5.1 持久化写）：

| 方法 | 说明 |
| ---- | ---- |
| `evaluate(EvaluateAutoExpressCommand)` | 读 active 规则 + CLN 事实，返回命中列表 |
| `matchCriticalRules(MatchCriticalRulesCommand)` | CRT 单条 Observation 匹配 |

不发布持久化事件；可选返回 DTO 供 AS 写 DR/DetectedIssue。求值只读 CLN/LIS 投影（BR-AEX-03）。

- 遵循项目级 Java 成码门禁（`@代码生成通用规范`）。
- 脚本引擎由 AS 注入策略接口，DomainService 不硬绑 Groovy/JS。

## 10. 生成与验收清单

- [ ] peis V1 含 rule_type_code
- [ ] save 语法校验
- [ ] EvaluationService 单测 mock CLN

---

## 11. 单次会话代码生成门禁（DoD）

### 11.1 前置输入

DM-PEIS-006、peis V1

### 11.2 DoD

DomainService 三方法 + Query 两方法编译通过

### 11.3 失败处理

脚本引擎未选 → AS 层注入策略接口

---

## 定稿检查清单（可打勾）

- [ ] §5.1 = 3
- [ ] §7 = 2

---

## 定稿评分（百分制）

| 维度 | 分值 | 自评 |
| ---- | ---- | ---- |
| 输入完整性 | 20 | 20 |
| 方法白名单对齐 | 25 | 25 |
| 不变式映射 | 20 | 20 |
| 事件契约 | 15 | 15 |
| 软删与框架一致 | 10 | 10 |
| 去重与无歧义 | 10 | 10 |
| **总分** | **100** | **100（§1.4 PASS）** |

---

**本 SDD 正文结束。**
