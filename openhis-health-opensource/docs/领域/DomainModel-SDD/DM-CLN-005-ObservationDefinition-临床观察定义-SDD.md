# DM-CLN-005 ObservationDefinition 临床观察定义 · DomainModel-SDD

> **对应 DM**：`docs/领域/DomainModel/DM-CLN-005-ObservationDefinition-临床观察定义.md`  
> **目标工程**：`zxplat-core/zxplat-domain`  
> **限界上下文**：clinical · 观察目录  
> **聚合**：ObservationDefinition（无子实体）

---

## 输入完整性检查结果

| 最小输入项 | 状态 | 说明 |
| ---------- | ---- | ---- |
| DM 路径 | 满足 | DM-CLN-005 |
| 包 `com.zxplat.domain.clinical.observationdefinition` | 满足 | |
| DM §6.1 ×2 | 满足 | |
| DM §5 ×2 事件 | 满足 | |
| health V3 `cln_observation_definition` | 满足 | |

---

## 1. 设计目标与边界

### 1.1 设计目标

- 固化 **ObservationDefinition** 目录 CRUD；支撑 PEIS CFG-03、DES 分科结论 category。
- 实例写入见 **DM-CLN-007**，本聚合 **不**写 `cln_observation`。

### 1.2 边界

- **在内**：`cln_observation_definition`。
- **在外**：与 AD 绑定在 `cus_activity_observation_link`（[DM-CUS-013](./DM-CUS-013-ActivityObservationLink-项目小项绑定.md)）。

---

## 2. 工程落点与包结构

### 2.2 包结构（`com.zxplat.domain.clinical.observationdefinition`）

| 相对包 | 内容 |
| ------ | ---- |
| `entity` | `ObservationDefinition` |
| `command` | `SaveObservationDefinitionCommand`、`DeactivateObservationDefinitionCommand` |
| `repository` | `ObservationDefinitionRepository` |
| `service` | `ObservationDefinitionDomainService` |
| `queries` | `ObservationDefinitionQueryService` |
| `queries.model` | `ObservationDefinitionModel` |

**存储口径**：`valueTypeEnum` / `statusEnum` / `categoryCode` → `String`。

---

## 3. 类型 / 字段命名 / 注解规则

遵循项目级成码门禁；`LocalDateTime` 审计字段；软删 `isDeleted`。

---

## 4. 实体 / 仓储 / 查询契约

### 4.2 聚合根 `ObservationDefinition`

| 字段名（Java） | 类型 | 必填 | 说明 |
| -------------- | ---- | ---- | ---- |
| `id` | `Long` | 系统 | PK |
| `code` | `String` | 是 | BR-CLN-OD-01 |
| `display` | `String` | 是 | |
| `valueTypeEnum` | `String` | 是 | |
| `unitCode` | `String` | 否 | |
| `categoryCode` | `String` | 是 | exam_item/dept_conclusion/… |
| `statusEnum` | `String` | 是 | active/inactive |
| `tenantId` | `Long` | 是 | |
| `orgId` | `Long` | 否 | |
| `isDeleted` | `Boolean` | 系统 | |
| `createdAt` | `LocalDateTime` | 系统 | |
| `updatedAt` | `LocalDateTime` | 系统 | |

### 4.3 子实体

无。

### 4.5 仓储

```java
public interface ObservationDefinitionRepository extends JpaRepository<ObservationDefinition, Long> {
    boolean existsByTenantIdAndCodeAndIsDeletedFalse(Long tenantId, String code);
    Optional<ObservationDefinition> findByIdAndIsDeletedFalse(Long id);
}
```

### 4.6 查询模型

#### 4.6.1 `ObservationDefinitionModel`（search / listByCategory）

| 字段名 | 类型 | 来源 | 说明 |
| ------ | ---- | ---- | ---- |
| `id` | `Long` | id | |
| `code` | `String` | code | |
| `display` | `String` | display | |
| `valueTypeEnum` | `String` | value_type_enum | |
| `unitCode` | `String` | unit_code | |
| `categoryCode` | `String` | category_code | |
| `statusEnum` | `String` | status_enum | |

#### 4.6.2 `ObservationDefinitionModel`（getById）

同 §4.6.1 全字段。

---

## 5. 写侧与读侧白名单

### 5.1 写侧 — `ObservationDefinitionDomainService`

#### 5.1.1 `save(SaveObservationDefinitionCommand cmd)` → `ObservationDefinition`

**命令文件**：`SaveObservationDefinitionCommand.java`

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `id` | `Long` | 更新 |
| `code` | `String` | 必填 |
| `display` | `String` | 必填 |
| `valueTypeEnum` | `String` | 必填 |
| `unitCode` | `String` | |
| `categoryCode` | `String` | 必填 |
| `statusEnum` | `String` | 默认 active |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | save |
| `repositoryChecks` | BR-CLN-OD-01 code 唯一 |
| `eventPublishAnchor` | `ObservationDefinitionSavedEvent` |
| `unitTestCaseId` | TC-CLN-OD-001 |

**方法契约摘要**：upsert；code 冲突拒绝；**注释锚点**：唯一校验 → persist → 事件。

---

#### 5.1.2 `deactivate(DeactivateObservationDefinitionCommand cmd)` → `ObservationDefinition`

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `id` | `Long` | 必填 |

| 绑定项 | 内容 |
| ------ | ---- |
| `statusTransitionMethod` | active → inactive |
| `eventPublishAnchor` | `ObservationDefinitionDeactivatedEvent` |
| `unitTestCaseId` | TC-CLN-OD-002 |

---

### 5.2 读侧 — `ObservationDefinitionQueryService`

| 方法 | 入参 | 出参 | `unitTestCaseId` |
| ---- | ---- | ---- | ---------------- |
| `getById` | `Long id` | `ObservationDefinitionModel` | TC-CLN-OD-R01 |
| `search` | `ObservationDefinitionQuery, Pageable` | `Page<ObservationDefinitionModel>` | TC-CLN-OD-R02 |
| `listByCategory` | `String categoryCode` | `List<ObservationDefinitionModel>` | TC-CLN-OD-R03 |

---

## 6. 不变式映射（DM §7）

| BR 编号 | 实现策略 |
| ------- | -------- |
| **BR-CLN-OD-01** | save 前 exists code |
| **BR-CLN-OD-02** | AS 停用前查在检引用 |
| **BR-CLN-OD-03** | categoryCode 枚举校验 |

---

## 7. 事件契约（DM §5）

| 事件 | 包路径 | 载荷 | 触发 |
| ---- | ------ | ---- | ---- |
| `ObservationDefinitionSavedEvent` | `com.zxplat.domain.share.events.clinical.observationdefinition` | `id, code, categoryCode, statusEnum` | save |
| `ObservationDefinitionDeactivatedEvent` | 同上 | `id` | deactivate |

**发布时机**：**beforeCommit**。

---

## 8. 删除与可见性契约

- 停用走 statusEnum；软删仅管理端。

---

## 9. 框架优先适配规则

- 遵循项目级成码门禁；包位于 `zxplat-domain/clinical/`。

---

## 10. 生成与验收清单

- [x] 实体与 health V3 DDL 一致（2026-07-19 领域成码）
- [x] uk_cln_obs_def_tenant_code（Repository `existsByTenantIdAndCodeAndIsDeletedFalse`）
- [ ] CFG-03 save/deactivate 单测
- [ ] listByCategory dept_conclusion 过滤（方法已提供，待 AS/单测）

---

## 11. 单次会话代码生成门禁（DoD）

### 11.1 前置输入

DM-CLN-005 v2.0、health V3

### 11.2 DoD

编译通过；关闭 GAP-PEIS-CFG-02 领域部分

### 11.3 失败处理

与 WKF AD 码冲突 → 文档修订 code 规则

---

## 定稿检查清单（可打勾）

- [ ] §5.1 = 2
- [ ] §7 = 2 事件

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
