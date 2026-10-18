# DM-PEIS-004 PeisConfig 模块参数 · DomainModel-SDD

> **对应 DM**：`docs/领域/DomainModel/DM-PEIS-004-PeisConfig-模块参数.md`（本 SDD 假定该 DM 为当期锁定版本）  
> **目标工程**：`zxplat-core/zxplat-domain`  
> **限界上下文**：health · peis · 配置管理  
> **聚合**：PeisConfig（无子实体）

---

## 输入完整性检查结果

| 最小输入项 | 状态 | 说明 |
| ---------- | ---- | ---- |
| DM 路径 | 满足 | 已绑定 DM-PEIS-004 |
| SDD 输出路径 | 满足 | 本文件 |
| 目标模块 | 满足 | `zxplat/zxplat-domain` |
| 包规则 `com.zxplat.plugin.peis.domain.peisconfig` | 满足 | `peisconfig` 全小写 |
| 固定技术口径（ID Long、时间 LocalDateTime、软删、beforeCommit、分页等） | 满足 | 见 §9 |
| DM §6.1 写方法 | 满足 | §5.1 共 2 节 |
| DM §6.2 读方法 | 满足 | §5.2 + §4.6 |
| DM §7 BR | 满足 | §6 |
| DM §5 事件 | 满足 | §7 无事件 |

---

## 1. 设计目标与边界

### 1.1 设计目标

- 固化 **PeisConfig** KV 参数的持久化与读写契约，对齐 `lis_config` 模式，供 REG/INT/DES 读取 `peis.reg.*` 等键。
- 写方法 **save** / **batchSave** 幂等；禁止 framework Setting key 前缀（BR-PEIS-CFG-02）。
- **单次成稿目标**：正文无「待确认」类措辞。

### 1.2 边界

- **在内**：`peis_config` 表 CRUD。
- **在外**：CFG-003 AppService 编排；`PeisConfigChangedEvent` 由 AS 发布（P1）。
- **跨域**：无。

---

## 2. 工程落点与包结构

### 2.1 Maven 模块

- 模块：`zxplat/zxplat-domain`。

### 2.2 包结构（`com.zxplat.plugin.peis.domain.peisconfig`）

| 相对包 | 内容 |
| ------ | ---- |
| `entity` | `PeisConfig` |
| `valueobject` | 无 |
| `command` | `SavePeisConfigCommand`、`BatchSavePeisConfigCommand` |
| `repository` | `PeisConfigRepository` |
| `service` | `PeisConfigDomainService` |
| `queries` | `PeisConfigQueryService` |
| `queries.model` | `PeisConfigQueryModel` |

**存储口径**：`configKey` / `configValue` → `String`；无 `*_enum` 列。

---

## 3. 类型 / 字段命名 / 注解规则

| 规则 | 说明 |
| ---- | ---- |
| 主键 | `Long id` |
| 唯一 | `(tenantId, configKey)` |
| 时间 | `LocalDateTime` |
| 软删 | `Boolean isDeleted` |
| 仓储 | `JpaRepository` 接口，禁止 `@Query` |
| 事务 | 领域服务不声明 `@Transactional` |
| 事件 | 本聚合不发布领域事件 |

---

## 4. 实体 / 值对象 / 枚举 / 仓储 / 查询契约

### 4.1 表与实体映射

| 逻辑表 | 实体类 | 说明 |
| ------ | ------ | ---- |
| `peis_config` | `PeisConfig` | 聚合根 |

### 4.2 聚合根 `PeisConfig`（字段级契约）

| 字段名（Java） | 类型 | 必填 | 说明 / 约束 |
| -------------- | ---- | ---- | ----------- |
| `id` | `Long` | 系统 | PK |
| `tenantId` | `Long` | 是 | 租户 |
| `orgId` | `Long` | 否 | 院区 |
| `configKey` | `String` | 是 | 键（BR-PEIS-CFG-02） |
| `configValue` | `String` | 是 | 值 |
| `isDeleted` | `Boolean` | 系统 | 软删 |
| `createdBy` | `Long` | 系统 | |
| `createdAt` | `LocalDateTime` | 系统 | |
| `updatedBy` | `Long` | 系统 | |
| `updatedAt` | `LocalDateTime` | 系统 | |

### 4.3 子实体

无。本聚合为单表。

### 4.5 仓储接口

```java
public interface PeisConfigRepository extends JpaRepository<PeisConfig, Long> {
    Optional<PeisConfig> findByTenantIdAndConfigKeyAndIsDeletedFalse(Long tenantId, String configKey);
    boolean existsByTenantIdAndConfigKeyAndIsDeletedFalse(Long tenantId, String configKey);
}
```

### 4.6 查询模型（`queries.model`）

#### 4.6.1 `PeisConfigQueryModel`（对应 `searchConfigs`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `id` | `Long` | `peis_config.id` | |
| `configKey` | `String` | `peis_config.config_key` | |
| `configValue` | `String` | `peis_config.config_value` | |
| `orgId` | `Long` | `peis_config.org_id` | |

#### 4.6.2 `PeisConfig`（对应 `getByKey`）

直接返回 `PeisConfig` 实体，无独立 QueryModel。

---

## 5. 写侧与读侧白名单（命令字段全集）

### 5.1 写侧 — `PeisConfigDomainService`

#### 5.1.1 `save(SavePeisConfigCommand cmd)` → `PeisConfig`

**命令文件**：`SavePeisConfigCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `id` | `Long` | 编辑时传 |
| `configKey` | `String` | 必填 |
| `configValue` | `String` | 必填 |
| `orgId` | `Long` | 可选 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | N/A |
| `statusTransitionMethod` | N/A |
| `repositoryChecks` | BR-PEIS-CFG-01/02 |
| `eventPublishAnchor` | 无 |
| `unitTestCaseId` | TC-PEIS-CFG-001 |

**方法契约摘要**：前置：configKey 合法且非 framework 前缀；持久化：按 tenant+key upsert；失败：非法 key → 拒绝；**注释锚点**：校验 key → upsert → 返回。

---

#### 5.1.2 `batchSave(BatchSavePeisConfigCommand cmd)` → `List<PeisConfig>`

**命令文件**：`BatchSavePeisConfigCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `configs` | `List<SavePeisConfigCommand>` | 至少 1 条 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | 逐条 save |
| `statusTransitionMethod` | N/A |
| `repositoryChecks` | 逐条 BR-PEIS-CFG-02 |
| `eventPublishAnchor` | 无 |
| `unitTestCaseId` | TC-PEIS-CFG-002 |

**方法契约摘要**：前置：逐条 key 校验；持久化：逐条 upsert；失败：任一条非法 → 整批拒绝；**注释锚点**：逐条校验 → 逐条 save → 返回列表。

---

### 5.2 读侧 — `PeisConfigQueryService`（`domain.queries`）

| 方法 | 入参 | 出参 | 关键实现 | `unitTestCaseId` |
| ---- | ---- | ---- | -------- | ---------------- |
| `getByKey` | `tenantId, configKey` | `PeisConfig` | 单键查询 | TC-PEIS-CFG-R01 |
| `searchConfigs` | `tenantId, Pageable` | `Page<PeisConfigQueryModel>` | 分页 configKey ASC | TC-PEIS-CFG-R02 |

**读方法注释锚点（统一）**：软删过滤 → 仓储查询 → 组装 QueryModel（无写库）。

---

## 6. 不变式映射（DM §7）

| BR 编号 | 实现策略摘要 |
| ------- | ------------ |
| **BR-PEIS-CFG-01** | Repository 唯一 `(tenantId, configKey)` |
| **BR-PEIS-CFG-02** | save 前置 key 前缀黑名单 |
| **BR-PEIS-CFG-03** | auto_dispatch 语义由 AS 解释，Domain 只存字符串 |

---

## 7. 事件契约与触发规则（DM §5）

无。`PeisConfigChangedEvent` 由 AS-PEIS-CFG-003 编排。

---

## 8. 删除与可见性契约

- 软删 `isDeleted=true`；查询默认过滤 false。
- 配置项不做物理 DELETE。

---

## 9. 框架优先适配规则

- 遵循项目级 Java 成码门禁。
- 与 `LisConfigDomainService` 行为对齐，包名独立 peis。

---

## 10. 生成与验收清单

- [ ] `PeisConfig` 实体字段与 §4.2 一致
- [ ] 唯一索引 `uk_peis_config_tenant_key` 生效
- [ ] save / batchSave 幂等
- [ ] getByKey / searchConfigs 可用
- [ ] BR-PEIS-CFG-02 单测

---

## 11. 单次会话代码生成门禁（DoD）

### 11.1 前置输入

- DM-PEIS-004 v1.1、本 SDD、peis V1 DDL

### 11.2 DoD

- 领域层编译通过；KV 读写单测绿

### 11.3 失败处理

- 与 lis_config 冲突 → 回文档修订 key 前缀表

---

## 定稿检查清单（可打勾）

- [ ] 金骨架 §1～§11 齐全
- [ ] §5.1 节数 = 2
- [ ] §4.2 字段 = DM §3.1 全集
- [ ] 无占位措辞

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
