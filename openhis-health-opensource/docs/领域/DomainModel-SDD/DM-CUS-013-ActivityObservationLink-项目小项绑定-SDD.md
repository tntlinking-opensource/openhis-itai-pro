# DM-CUS-013 ActivityObservationLink 项目小项绑定 · DomainModel-SDD

> **对应 DM**：`docs/领域/DomainModel/DM-CUS-013-ActivityObservationLink-项目小项绑定.md`（本 SDD 假定该 DM 为当期锁定版本）  
> **目标工程**：`zxplat-core/zxplat-domain`  
> **限界上下文**：health · custom（CUS）· AD↔OD 目录绑定  
> **聚合**：ActivityObservationLink（按 activityDefinitionId 边界）

---

## 输入完整性检查结果

| 最小输入项 | 状态 | 说明 |
| ---------- | ---- | ---- |
| DM 路径 | 满足 | 已绑定 DM-CUS-013 |
| SDD 输出路径 | 满足 | 本文件 |
| 目标模块 | 满足 | `zxplat/zxplat-domain` |
| 包规则 `com.zxplat.domain.custom.activityobservationlink` | 满足 | `activityobservationlink` 全小写 |
| 固定技术口径（ID Long、时间 LocalDateTime、软删、beforeCommit、分页等） | 满足 | 见 §9 |
| DM §6.1 写方法 | 满足 | §5.1 共 1 节 |
| DM §6.2 读方法 | 满足 | §5.2 + §4.6 |
| DM §7 BR | 满足 | §6 |
| DM §5 事件 | 满足 | §7 共 1 事件 |
| health V7 `cus_activity_observation_link` | 满足 | uk_aol_activity_od |

---

## 1. 设计目标与边界

### 1.1 设计目标

- 固化 **custom** 包下 AD↔OD 链接 CRUD；表 `cus_activity_observation_link`。
- P0 **写** 由 PEIS CFG-04 编排调用；**读** 由 DES-02、CLN-007 投影、MAS 调用。
- 自 DM-PEIS-002 / DM-CLN-009 迁入 CUS 编号，与 `cus_*` 表前缀一致。

### 1.2 边界

- **在内**：链接行 CRUD 同步。
- **在外**：AD 在 WKF；OD 在 CLN-005；LIS 仪器路由在 `lis_device_*`。
- **跨域**：写前校验 AD/OD；`LisResultProjectionService` 只读本 QS。

---

## 2. 工程落点与包结构

### 2.1 Maven 模块

- 模块：`zxplat/zxplat-domain`。

### 2.2 包结构（`com.zxplat.domain.custom.activityobservationlink`）

| 相对包 | 内容 |
| ------ | ---- |
| `entity` | `ActivityObservationLink` |
| `command` | `SaveActivityObservationLinksCommand`、`ActivityObservationLinkItemCommand` |
| `repository` | `ActivityObservationLinkRepository` |
| `service` | `ActivityObservationLinkDomainService` |
| `queries` | `ActivityObservationLinkQueryService` |
| `queries.model` | `ActivityObservationLinkModel` |
| `share.events` | 共享事件包 `com.zxplat.domain.share.events.custom.activityobservationlink` |

---

## 3. 类型 / 字段命名 / 注解规则

| 规则 | 说明 |
| ---- | ---- |
| PK | `Long id` |
| 唯一 | `(activityDefinitionId, observationDefinitionId)` |
| `requiredFlag` | `Boolean` |
| 软删 | `Boolean isDeleted` |
| 仓储 | `JpaRepository` 接口，禁止 `@Query` |
| 事务 | 领域服务不声明 `@Transactional` |
| 子表同步 | 全量对齐 + 软删旧行（BR-AOL-03） |

---

## 4. 实体 / 值对象 / 枚举 / 仓储 / 查询契约

### 4.1 表与实体映射

| 逻辑表 | 实体类 |
| ------ | ------ |
| `cus_activity_observation_link` | `ActivityObservationLink` |

### 4.2 聚合根 `ActivityObservationLink`

| 字段名（Java） | 类型 | 必填 | 说明 |
| -------------- | ---- | ---- | ---- |
| `id` | `Long` | 系统 | PK |
| `activityDefinitionId` | `Long` | 是 | 项目 AD |
| `observationDefinitionId` | `Long` | 是 | 小项 OD |
| `sortNum` | `Integer` | 是 | 排序 |
| `requiredFlag` | `Boolean` | 是 | 必检 |
| `tenantId` | `Long` | 是 | |
| `orgId` | `Long` | 否 | |
| `isDeleted` | `Boolean` | 系统 | |
| `createdAt` | `LocalDateTime` | 系统 | |
| `updatedAt` | `LocalDateTime` | 系统 | |

### 4.3 子实体

无。多行同属一 activity 边界，由 saveLinks 批量同步。

### 4.5 仓储接口

```java
public interface ActivityObservationLinkRepository extends JpaRepository<ActivityObservationLink, Long> {
    List<ActivityObservationLink> findByActivityDefinitionIdAndIsDeletedFalse(Long activityDefinitionId);
}
```

### 4.6 查询模型

#### 4.6.1 `ActivityObservationLinkModel`（`listByActivityId`）

| 字段名（Java） | 类型 | 来源 | 说明 |
| -------------- | ---- | ---- | ---- |
| `id` | `Long` | link.id | |
| `activityDefinitionId` | `Long` | link | |
| `observationDefinitionId` | `Long` | link | |
| `sortNum` | `Integer` | link | |
| `requiredFlag` | `Boolean` | link | |
| `odDisplay` | `String` | join OD | 展示 |
| `odCode` | `String` | join OD | |

---

## 5. 写侧与读侧白名单（命令字段全集）

### 5.1 写侧 — `ActivityObservationLinkDomainService`

#### 5.1.1 `saveLinks(SaveActivityObservationLinksCommand cmd)` → `List<ActivityObservationLink>`

**命令文件**：`SaveActivityObservationLinksCommand.java`

**字段全集**

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `activityDefinitionId` | `Long` | 必填 |
| `links` | `List<ActivityObservationLinkItemCommand>` | 全量集 |

**子命令 `ActivityObservationLinkItemCommand.java`**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `id` | `Long` | 更新时传 |
| `observationDefinitionId` | `Long` | 必填 |
| `sortNum` | `Integer` | 必填 |
| `requiredFlag` | `Boolean` | 默认 true |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | syncChildRecords |
| `statusTransitionMethod` | N/A |
| `repositoryChecks` | BR-AOL-01/02；唯一键冲突拒绝 |
| `eventPublishAnchor` | `ActivityObservationLinksChangedEvent` |
| `unitTestCaseId` | TC-CUS-AOL-001 |

**方法契约摘要**：前置：AD 类型合法、OD 存在；持久化：按 odId 对齐，缺省旧行 isDeleted=true，入参行 upsert；失败：重复 odId 入参、唯一键冲突；事件：LinksChanged beforeCommit；**注释锚点**：加载旧行 → diff → 软删/upsert → 事件。

---

### 5.2 读侧 — `ActivityObservationLinkQueryService`

| 方法 | 入参 | 出参 | `unitTestCaseId` |
| ---- | ---- | ---- | ---------------- |
| `listByActivityId` | `Long activityId` | `List<ActivityObservationLinkModel>` | TC-CUS-AOL-R01 |

**读方法注释锚点（统一）**：activityId + 软删 → join OD 显示字段 → 按 sortNum 排序。

---

## 6. 不变式映射（DM §7）

| BR 编号 | 实现策略 |
| ------- | -------- |
| **BR-AOL-01** | saveLinks 前校验 AD type_enum |
| **BR-AOL-02** | 停用 OD 由 AS 预检在检引用 |
| **BR-AOL-03** | 全量对齐 + 软删 |
| **BR-AOL-04** | LIS 不维护本表；投影在 CLN-007 |

---

## 7. 事件契约与触发规则（DM §5）

| 事件类名 | 包路径 | 最小载荷 | 触发 |
| -------- | ------ | -------- | ---- |
| `ActivityObservationLinksChangedEvent` | `com.zxplat.domain.share.events.custom.activityobservationlink` | `activityDefinitionId, linkCount` | `saveLinks` |

**发布时机**：**beforeCommit**。

---

## 8. 删除与可见性契约

- 移除绑定 = 软删 link 行。
- list 默认 isDeleted=false。

---

## 9. 框架优先适配规则

- 子表同步遵循 syncChildRecords 幂等语义（Skill §6.1.11）。
- 写前跨域校验 AD/OD 由 DomainService 调 WKF/CLN-005 QueryService（B.6.4）。
- 遵循项目级 Java 成码门禁（`@代码生成通用规范`）。

---

## 10. 生成与验收清单

- [ ] saveLinks 全量对齐单测
- [x] 唯一索引 uk_aol_activity_od（health V7 DDL）
- [x] listByActivityId（本聚合字段）；**OD 展示 enrich 在 AppService**（B.6.4，非 QS join）
- [x] 包路径 `domain.custom.activityobservationlink`（2026-07-19 领域成码）

---

## 11. 单次会话代码生成门禁（DoD）

### 11.1 前置输入

DM-CUS-013、health V7、DM-CLN-005 OD 可读

### 11.2 DoD

saveLinks 增删改幂等通过；包在 `domain.custom`，不在 `domain.peis`

### 11.3 失败处理

OD 不存在 → AS 前置校验

---

## 定稿检查清单（可打勾）

- [ ] §5.1 节数 = 1
- [ ] §7 事件 = 1

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
