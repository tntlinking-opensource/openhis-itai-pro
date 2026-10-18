# DM-LIS-008 LisConfig 模块参数 · DomainModel-SDD

> **对应 DM**：`docs/领域/缺口/文档无代码/DM-LIS-008-LisConfig-模块参数.md`（v1.0，本 SDD 假定该 DM 为当期锁定版本）  
> **目标工程：`zxplat``  
> **限界上下文**：检验域 · 配置管理  
> **聚合**：LisConfig（无子实体）

---

## 输入完整性检查结果

| 最小输入项 | 状态 | 说明 |
| ---------- | ---- | ---- |
| DM 路径 | 满足 | 已绑定 DM-LIS-008 |
| SDD 输出路径 | 满足 | 本文件 |
| 目标模块 | 满足 | `zxplat/zxplat-domain` |
| 包规则 `com.zxplat.domain.lis.lisconfig` | 满足 | `lisconfig` 全小写 |
| 固定技术口径（ID Long、软删、beforeCommit、分页等） | 满足 | 见 §9 |
| DM §2 持久化形态 | 满足 | §4 字段表对齐 |
| DM §3 业务规则 | 满足 | §6 映射（BR-CFG-01/02/03） |
| DM 领域事件 | 满足 | 无事件（DM 未定义；事件由 AS 层编排） |

---

## 1. 设计目标与边界

### 1.1 设计目标

- 在单一文档内固化 **LisConfig 聚合** 的持久化模型、领域服务契约、查询契约、不变式实现策略，使下一会话可 **一次性** 生成 `zxplat/zxplat-domain` 领域层实现并通过编译。
- LisConfig 为 KV 参数表，语义简单；**禁止** 读写 framework Setting（BR-CFG-01）。
- **单次成稿目标**：正文不出现「后续补充」「待确认」类措辞；缺口须在文档阶段关闭。

### 1.2 边界

- **在内**：`lis_config` KV 键值读写、唯一约束。
- **在外**：参数变更编排（CFG-007 AppService）；定时任务/其他模块通过 QueryService 读取配置。
- **跨域仅端口声明**：无。

---

## 2. 工程落点与包结构

### 2.1 Maven 模块

- 模块：`zxplat/zxplat-domain`（与当次输入限定一致）。

### 2.2 包结构（`com.zxplat.domain.lis.lisconfig`）

| 相对包 | 内容 |
| ------ | ---- |
| `entity` | `LisConfig` |
| `valueobject` | 无 |
| `enums`（共享） | 无新增枚举 |
| `command` | 每个写方法一个 Java 文件（见 §5） |
| `repository` | `LisConfigRepository`（仅 `JpaRepository` 接口） |
| `service` | `LisConfigDomainService`（直接实现类） |
| `queries` | `LisConfigQueryService`（直接实现类） |
| `queries.model` | `LisConfigQueryModel` |
| `share.events` | 无（事件如 `LisConfigChangedEvent` 由 AS 层编排发布） |

**存储口径（DM 优先）**：`configKey` / `configValue` / `remark` 采用 `String`。

---

## 3. 类型 / 字段命名 / 注解规则

| 规则 | 说明 |
| ---- | ---- |
| 主键 | `Long`；聚合根主键 `id` |
| 外键 | 无 |
| 唯一约束 | `(tenantId, orgId, configKey)` 唯一（orgId 可为 NULL） |
| 时间 | `LocalDateTime`（`createdAt` / `updatedAt`） |
| `_flag` | `Boolean` |
| 软删 | `Boolean isDeleted` |
| 仓储 | `interface LisConfigRepository extends JpaRepository<LisConfig, Long>`；禁止手写 `@Query` |
| 分页 | `Pageable`；默认 `configKey ASC` |
| 事务 | 领域服务 **不** 声明 `@Transactional`；参与 AppService `AppServiceAspect` 已开启的 UoW |

---

## 4. 实体 / 值对象 / 枚举 / 仓储 / 查询契约

### 4.1 表与实体映射

| 逻辑表 | 实体类 | 说明 |
| ------ | ------ | ---- |
| `lis_config` | `LisConfig` | 聚合根 · 单表 KV |

### 4.2 聚合根 `LisConfig`（字段级契约）

| 字段名（Java） | 类型 | 必填 | 说明 / 约束 |
| -------------- | ---- | ---- | ----------- |
| `id` | `Long` | 系统 | 聚合根标识（雪花 ID） |
| `tenantId` | `Long` | 是 | 租户（必填） |
| `orgId` | `Long` | 否 | 院区（可选，NULL 表示全院级） |
| `configKey` | `String` | 是 | 键（BR-CFG-01：禁止 framework Setting key 前缀） |
| `configValue` | `String` | 是 | 值 |
| `remark` | `String` | 否 | 说明 |
| `isDeleted` | `Boolean` | 系统 | 软删标记 |
| `createdBy` | `Long` | 系统 | 创建人 |
| `createdAt` | `LocalDateTime` | 系统 | 创建时间 |
| `updatedBy` | `Long` | 系统 | 更新人 |
| `updatedAt` | `LocalDateTime` | 系统 | 更新时间 |

**不变式方法**：无（纯 KV，无业务状态机）。

### 4.3 子实体

无。本聚合为单表。

### 4.5 仓储接口

```java
public interface LisConfigRepository extends JpaRepository<LisConfig, Long> {
    Optional<LisConfig> findByTenantIdAndOrgIdAndConfigKeyAndIsDeletedFalse(Long tenantId, Long orgId, String configKey);
    List<LisConfig> findByTenantIdAndIsDeletedFalse(Long tenantId);
    boolean existsByTenantIdAndConfigKeyAndIsDeletedFalse(Long tenantId, String configKey);
}
```

### 4.6 查询模型（`queries.model`）

#### 4.6.1 `LisConfigQueryModel`（对应 `searchConfigs`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `id` | `Long` | `lis_config.id` | |
| `configKey` | `String` | `lis_config.config_key` | |
| `configValue` | `String` | `lis_config.config_value` | |
| `remark` | `String` | `lis_config.remark` | |
| `orgId` | `Long` | `lis_config.org_id` | |

#### 4.6.2 `LisConfig`（对应 `getByKey`）

直接返回 `LisConfig` 实体（含 configValue），无独立 QueryModel。

#### 4.6.3 `LisConfig`（对应 `getByTenantId`）

直接返回 `LisConfig` 实体（含 configValue），无独立 QueryModel。

---

## 5. 写侧与读侧白名单（命令字段全集）

### 5.1 写侧 — `LisConfigDomainService`

#### 5.1.1 `save(SaveLisConfigCommand cmd)` → `LisConfig`

**命令文件**：`SaveLisConfigCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `id` | `Long` | 编辑时传 |
| `configKey` | `String` | 键 |
| `configValue` | `String` | 值 |
| `remark` | `String` | 说明 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `save` |
| `statusTransitionMethod` | 无 |
| `repositoryChecks` | BR-CFG-01：configKey 不以 framework Setting 前缀开头 |
| `eventPublishAnchor` | 无（事件由 AS 层编排） |
| `unitTestCaseId` | TC-CFG-001 |

**方法契约摘要**：前置：BR-CFG-01 校验 configKey 合法；持久化：`tenantId + configKey` 存在则 update，否则 insert；失败：非法 key → 拒绝；**注释锚点**：校验 key 前缀 → 幂等保存 → 返回。

---

#### 5.1.2 `batchSave(BatchSaveLisConfigCommand cmd)` → `List<LisConfig>`

**命令文件**：`BatchSaveLisConfigCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `configs` | `List<SaveLisConfigCommand>` | 批量 KV 项 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | 逐条 `save` |
| `statusTransitionMethod` | 无 |
| `repositoryChecks` | 逐条 BR-CFG-01 校验 |
| `eventPublishAnchor` | 无 |
| `unitTestCaseId` | TC-CFG-002 |

**方法契约摘要**：前置：逐条校验 configKey 前缀；持久化：逐条幂等保存；失败：任一 key 非法 → 整批拒绝；**注释锚点**：逐条校验 → 逐条 save → 返回列表。

---

### 5.2 读侧 — `LisConfigQueryService`（`domain.queries`）

所有查询 **默认** `isDeleted=false`。

| 方法 | 入参 | 出参（类名，字段见 §4.6） | 关键实现 | `unitTestCaseId` |
| ---- | ---- | ------------------------- | -------- | ---------------- |
| `searchConfigs` | `Long tenantId, Pageable` | `Page<LisConfigQueryModel>` | 分页 | TC-CFG-R01 |
| `getByKey` | `Long tenantId, String configKey` | `LisConfig` | 按 key 查实体 | TC-CFG-R02 |
| `getByTenantId` | `Long tenantId` | `List<LisConfig>` | 全量返回 | TC-CFG-R03 |

**读方法注释锚点（统一）**：构建查询条件（含软删过滤）→ 单次 SQL 调用 → 组装 QueryModel。

---

## 6. 不变式映射（DM §3）

| BR 编号 | 实现策略摘要 |
| ------- | ------------ |
| **BR-CFG-01** | `save` 前置：configKey 黑名单校验——不得以 framework Setting 的 key 前缀开头 |
| **BR-CFG-02** | 危险参数二次确认：由 AppService 编排实现，DomainService 不感知 |
| **BR-CFG-03** | 参数变更记录 AuditLog：由 AppService 编排调用 AuditEventDomainService |

---

## 7. 事件契约与触发规则

无。事件（如 `LisConfigChangedEvent`）由 AppService 编排触发，DomainService 不直接发布。

---

## 8. 删除与可见性契约

- 软删：`isDeleted = true`，不物理删除。
- 查询默认过滤 `isDeleted=false`。

---

## 9. 框架优先适配规则

- 遵循项目级 Java 成码门禁。
- 事务由 AppService `AppServiceAspect` / `ReadOnlyTransactionAspect` 管理。
- 唯一约束：`(tenantId, orgId, configKey)` 联合索引（orgId NULL 时特殊处理）。

---

## 10. 生成与验收清单

- [ ] `LisConfig` 实体 + 字段与 §4.2 一致
- [ ] 唯一约束 `(tenantId, orgId, configKey)` 正确实现
- [ ] BR-CFG-01 key 前缀黑名单校验
- [ ] Repository 无 `@Query`
- [ ] 读侧 3 个方法可用

---

## 11. 单次会话代码生成门禁（DoD）

### 11.1 前置输入

- 锁定 DM：`DM-LIS-008-LisConfig-模块参数.md`
- 本 SDD 文件
- 目标模块：`zxplat/zxplat-domain`

### 11.2 DoD

- `LisConfig` 实体编译通过，唯一约束生效。
- KV 读写方法功能正确。
- BR-CFG-01 前缀校验实现。

### 11.3 失败处理

- orgId=NULL 唯一约束冲突 → 回文档修订。

---

## 定稿检查清单（可打勾）

- [ ] 主章节结构与金骨架一致
- [ ] §5.1 子节数量 = 2
- [ ] §4.2 字段表与 DM §2 持久化形态表一一对应
- [ ] 全文无 YAML、无占位措辞

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
| **总分** | **100** | **98（可进入代码会话）** |

---

**本 SDD 正文结束。**
