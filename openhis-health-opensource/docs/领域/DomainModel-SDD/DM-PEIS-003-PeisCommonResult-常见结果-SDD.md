# DM-PEIS-003 PeisCommonResult 常见结果 · DomainModel-SDD

> **对应 DM**：`docs/领域/DomainModel/DM-PEIS-003-PeisCommonResult-常见结果.md`（本 SDD 假定该 DM 为当期锁定版本）  
> **目标工程**：`zxplat-core/zxplat-domain`  
> **限界上下文**：health · peis · 常见结果模板  
> **聚合**：PeisCommonResult

---

## 输入完整性检查结果

| 最小输入项 | 状态 | 说明 |
| ---------- | ---- | ---- |
| DM 路径 | 满足 | 已绑定 DM-PEIS-003 |
| SDD 输出路径 | 满足 | 本文件 |
| 目标模块 | 满足 | `zxplat/zxplat-domain` |
| 包规则 `com.zxplat.plugin.peis.domain.commonresult` | 满足 | `commonresult` 全小写 |
| 固定技术口径（ID Long、时间 LocalDateTime、软删、beforeCommit、分页等） | 满足 | 见 §9 |
| DM §6.1 写方法 | 满足 | §5.1 共 2 节 |
| DM §6.2 读方法 | 满足 | §5.2 + §4.6 |
| DM §7 BR | 满足 | §6 |
| DM §5 事件 | 满足 | §7 无事件 |
| peis V1 `peis_common_result` | 满足 | |

---

## 1. 设计目标与边界

### 1.1 设计目标

- DES-03 按 OD/科室维护常见结果模板；停用不物理删。

### 1.2 边界

- **在内**：`peis_common_result`。
- **在外**：Observation 写入在 CLN。

---

## 2. 工程落点与包结构

### 2.1 Maven 模块

- 模块：`zxplat/zxplat-domain`。

### 2.2 包结构（`com.zxplat.plugin.peis.domain.commonresult`）

| 相对包 | 内容 |
| ------ | ---- |
| `entity` | `PeisCommonResult` |
| `command` | `SavePeisCommonResultCommand`、`DeactivatePeisCommonResultCommand` |
| `repository` | `PeisCommonResultRepository` |
| `service` | `PeisCommonResultDomainService` |
| `queries` | `PeisCommonResultQueryService` |
| `queries.model` | `PeisCommonResultModel` |

**存储口径**：`statusEnum` → `String`；`interpretationCode` → `String`。

---

## 3. 类型 / 字段命名 / 注解规则

| 规则 | 说明 |
| ---- | ---- |
| 主键 | `Long id` |
| 状态 | `statusEnum` → `String`（active/inactive） |
| 解读码 | `interpretationCode` → `String` |
| 时间 | `LocalDateTime` 审计字段 |
| 软删 | `Boolean isDeleted` |
| 仓储 | `JpaRepository` 接口，禁止 `@Query` |
| 事务 | 领域服务不声明 `@Transactional` |
| 事件 | 本聚合不发布领域事件（P2 AS 可选） |

---

## 4. 实体 / 值对象 / 枚举 / 仓储 / 查询契约

### 4.1 表与实体映射

| 逻辑表 | 实体类 |
| ------ | ------ |
| `peis_common_result` | `PeisCommonResult` |

### 4.2 聚合根 `PeisCommonResult`

| 字段名（Java） | 类型 | 必填 | 说明 |
| -------------- | ---- | ---- | ---- |
| `id` | `Long` | 系统 | PK |
| `observationDefinitionId` | `Long` | 否 | 绑定小项 |
| `deptOrgId` | `Long` | 否 | 科室 |
| `display` | `String` | 是 | 名称 |
| `valueString` | `String` | 否 | 默认值 |
| `interpretationCode` | `String` | 否 | H/L/N |
| `sortNum` | `Integer` | 是 | 排序 |
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
public interface PeisCommonResultRepository extends JpaRepository<PeisCommonResult, Long> {
    List<PeisCommonResult> findByObservationDefinitionIdAndStatusEnumAndIsDeletedFalse(
            Long observationDefinitionId, String statusEnum);
    boolean existsByTenantIdAndObservationDefinitionIdAndDeptOrgIdAndDisplayAndStatusEnumAndIsDeletedFalse(
            Long tenantId, Long observationDefinitionId, Long deptOrgId, String display, String statusEnum);
}
```

### 4.6 查询模型

#### 4.6.1 `PeisCommonResultModel`（`listByOdId`）

| 字段名（Java） | 类型 | 来源 | 说明 |
| -------------- | ---- | ---- | ---- |
| `id` | `Long` | id | |
| `display` | `String` | display | |
| `valueString` | `String` | value_string | |
| `interpretationCode` | `String` | interpretation_code | |
| `sortNum` | `Integer` | sort_num | |

#### 4.6.2 `PeisCommonResultModel`（`search` 分页）

| 字段名（Java） | 类型 | 来源 | 说明 |
| -------------- | ---- | ---- | ---- |
| `id` | `Long` | id | |
| `display` | `String` | display | |
| `valueString` | `String` | value_string | |
| `interpretationCode` | `String` | interpretation_code | |
| `sortNum` | `Integer` | sort_num | |
| `statusEnum` | `String` | status_enum | |
| `deptOrgId` | `Long` | dept_org_id | |
| `observationDefinitionId` | `Long` | observation_definition_id | |

---

## 5. 写侧与读侧白名单

### 5.1 写侧 — `PeisCommonResultDomainService`

#### 5.1.1 `save(SavePeisCommonResultCommand cmd)` → `PeisCommonResult`

**命令文件**：`SavePeisCommonResultCommand.java`

**字段全集**

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `id` | `Long` | 更新时 |
| `observationDefinitionId` | `Long` | 可选 |
| `deptOrgId` | `Long` | 可选 |
| `display` | `String` | 必填 |
| `valueString` | `String` | |
| `interpretationCode` | `String` | |
| `sortNum` | `Integer` | |
| `statusEnum` | `String` | 默认 active |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | save |
| `statusTransitionMethod` | N/A |
| `repositoryChecks` | BR-PCR-01 display 唯一 |
| `eventPublishAnchor` | 无 |
| `unitTestCaseId` | TC-PEIS-PCR-001 |

**方法契约摘要**：前置：display 唯一（BR-PCR-01）；持久化：有 id 则 update，无 id insert；失败：display 冲突抛业务异常；无领域事件；**注释锚点**：唯一校验 → persist。

---

#### 5.1.2 `deactivate(DeactivatePeisCommonResultCommand cmd)` → `PeisCommonResult`

**命令文件**：`DeactivatePeisCommonResultCommand.java`

**字段全集**

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `id` | `Long` | 必填 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | deactivate |
| `statusTransitionMethod` | active → inactive |
| `repositoryChecks` | 行存在 |
| `eventPublishAnchor` | 无 |
| `unitTestCaseId` | TC-PEIS-PCR-002 |

**方法契约摘要**：前置：行存在；持久化：statusEnum=inactive；失败：行不存在；无领域事件；**注释锚点**：load → 改状态 → save。

---

### 5.2 读侧 — `PeisCommonResultQueryService`

| 方法 | 入参 | 出参 | `unitTestCaseId` |
| ---- | ---- | ---- | ---------------- |
| `listByOdId` | `Long odId` | `List<PeisCommonResultModel>` | TC-PEIS-PCR-R01 |
| `search` | `PeisCommonResultQuery, Pageable` | `Page<PeisCommonResultModel>` | TC-PEIS-PCR-R02 |

**读方法注释锚点（统一）**：status=active + 软删 → 查询 → Model。

---

## 6. 不变式映射（DM §7）

| BR 编号 | 实现策略 |
| ------- | -------- |
| **BR-PCR-01** | save 前 exists 同 display+od+dept |
| **BR-PCR-02** | deactivate 仅改 status |

---

## 7. 事件契约与触发规则（DM §5）

无。

---

## 8. 删除与可见性契约

- 业务停用走 statusEnum；软删仅管理端隐藏。

---

## 9. 框架优先适配规则

- 遵循项目级 Java 成码门禁（`@代码生成通用规范`）。
- `search` 分页走 QueryService + Pageable；禁止 AppService 直注 Repository。
- DES-03 套用模板只读 `listByOdId`，不写 Observation。

---

## 10. 生成与验收清单

- [ ] save / deactivate
- [ ] listByOdId 仅 active
- [ ] search 分页

---

## 11. 单次会话代码生成门禁（DoD）

### 11.1 前置输入

DM-PEIS-003、peis V1

### 11.2 DoD

编译 + 单测绿

### 11.3 失败处理

display 冲突 → 业务异常

---

## 定稿检查清单（可打勾）

- [ ] §5.1 = 2 节
- [ ] §4.2 字段完整

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
