# DM-PEIS-001 PeisServiceRequestExt 项目执行扩展 · DomainModel-SDD

> **对应 DM**：`docs/领域/DomainModel/DM-PEIS-001-PeisServiceRequestExt-项目执行扩展.md`  
> **目标工程**：`zxplat-core/zxplat-domain`  
> **限界上下文**：health · peis · 项目执行扩展  
> **聚合**：PeisServiceRequestExt（无子实体）

---

## 输入完整性检查结果

| 最小输入项 | 状态 | 说明 |
| ---------- | ---- | ---- |
| DM 路径 | 满足 | DM-PEIS-001 |
| 目标模块 | 满足 | `zxplat/zxplat-domain` |
| 包 `com.zxplat.plugin.peis.domain.servicerequestext` | 满足 | |
| DM §6.1 写方法 ×3 | 满足 | §5.1.1～5.1.3 |
| DM §5 事件 ×2 | 满足 | §7 |
| Flyway peis V1 | 满足 | `peis_service_request_ext` |

---

## 1. 设计目标与边界

### 1.1 设计目标

- 1:1 扩展 `ServiceRequest` 的执行态与外发标志；**禁止 barcode**（BR-PEIS-SR-02）。
- 支撑 REG 初始化、DES 确认/弃检、INT-01/DES-08 外发标志更新。
- 每个写方法独立 Command；事件 beforeCommit。

### 1.2 边界

- **在内**：`peis_service_request_ext`。
- **在外**：WorkItem 条码在 LIS/PACS；SR 生命周期在 WKF。
- **跨域**：写前校验 SR 存在（Repository/Client 只读）。

---

## 2. 工程落点与包结构

### 2.1 Maven 模块

`zxplat/zxplat-domain`

### 2.2 包结构（`com.zxplat.plugin.peis.domain.servicerequestext`）

| 相对包 | 内容 |
| ------ | ---- |
| `entity` | `PeisServiceRequestExt` |
| `command` | `InitPeisSrExtCommand`、`UpdatePeisSrExecStatusCommand`、`UpdatePeisSrDispatchCommand` |
| `repository` | `PeisServiceRequestExtRepository` |
| `service` | `PeisServiceRequestExtDomainService` |
| `queries` | `PeisServiceRequestExtQueryService` |
| `queries.model` | `PeisServiceRequestExtModel` |

**存储口径**：`execStatusEnum` / `lisDispatchEnum` / `pacsDispatchEnum` → `String`（code）。

---

## 3. 类型 / 字段命名 / 注解规则

| 规则 | 说明 |
| ---- | ---- |
| PK | `serviceRequestId`（Long，非自增业务 FK） |
| 枚举列 | `String` 存 code |
| 软删 | `isDeleted` |
| 事务/事件 | 不声明 `@Transactional`；beforeCommit 发布 |

---

## 4. 实体 / 值对象 / 枚举 / 仓储 / 查询契约

### 4.1 表与实体映射

| 逻辑表 | 实体类 |
| ------ | ------ |
| `peis_service_request_ext` | `PeisServiceRequestExt` |

### 4.2 聚合根 `PeisServiceRequestExt`

| 字段名（Java） | 类型 | 必填 | 说明 |
| -------------- | ---- | ---- | ---- |
| `serviceRequestId` | `Long` | 是 | PK/FK |
| `execStatusEnum` | `String` | 是 | pending/checked/abandoned/deferred |
| `lisDispatchEnum` | `String` | 是 | notSent/sent/resultReceived |
| `pacsDispatchEnum` | `String` | 是 | 同上 |
| `tenantId` | `Long` | 是 | |
| `orgId` | `Long` | 否 | |
| `isDeleted` | `Boolean` | 系统 | |
| `createdBy` | `Long` | 系统 | |
| `createdAt` | `LocalDateTime` | 系统 | |
| `updatedBy` | `Long` | 系统 | |
| `updatedAt` | `LocalDateTime` | 系统 | |

**不变式方法**：`markChecked()` / `markAbandoned(reason)` / `updateLisDispatch(code)` / `updatePacsDispatch(code)`

### 4.3 子实体

无。本聚合为单表。

### 4.5 仓储接口

```java
public interface PeisServiceRequestExtRepository extends JpaRepository<PeisServiceRequestExt, Long> {
    List<PeisServiceRequestExt> findByServiceRequestIdInAndIsDeletedFalse(Collection<Long> srIds);
    Optional<PeisServiceRequestExt> findByServiceRequestIdAndIsDeletedFalse(Long serviceRequestId);
}
```

主键即 `serviceRequestId`；`JpaRepository<PeisServiceRequestExt, Long>` 的 ID 类型与 PK 一致。

### 4.6 查询模型

#### 4.6.1 `PeisServiceRequestExtModel`（`findByServiceRequestId` / `listByEncounterId`）

| 字段名（Java） | 类型 | 来源 | 说明 |
| -------------- | ---- | ---- | ---- |
| `serviceRequestId` | `Long` | ext | |
| `encounterId` | `Long` | join SR | 展示 |
| `activityId` | `Long` | join SR | 项目 |
| `execStatusEnum` | `String` | ext | |
| `lisDispatchEnum` | `String` | ext | |
| `pacsDispatchEnum` | `String` | ext | |

`listByEncounterId` 通过 SR.encounter_id IN 查询后组装。

---

## 5. 写侧与读侧白名单（命令字段全集）

### 5.1 写侧 — `PeisServiceRequestExtDomainService`

#### 5.1.1 `initForServiceRequests(InitPeisSrExtCommand cmd)` → `List<PeisServiceRequestExt>`

**命令文件**：`InitPeisSrExtCommand.java`

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `serviceRequestIds` | `List<Long>` | 必填，REG 展开 SR |
| `tenantId` | `Long` | 必填 |
| `orgId` | `Long` | 可选 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | 批量 create 默认 pending/notSent |
| `statusTransitionMethod` | 初始态 |
| `repositoryChecks` | BR-PEIS-SR-01：已存在则跳过或幂等 |
| `eventPublishAnchor` | 无 |
| `unitTestCaseId` | TC-PEIS-SR-001 |

**方法契约摘要**：前置：SR 列表非空；持久化：缺失行 insert 默认值；已存在不重复；**注释锚点**：过滤已有 → batch insert → 返回。

---

#### 5.1.2 `updateExecStatus(UpdatePeisSrExecStatusCommand cmd)` → `PeisServiceRequestExt`

**命令文件**：`UpdatePeisSrExecStatusCommand.java`

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `serviceRequestId` | `Long` | 必填 |
| `encounterId` | `Long` | 必填（BR-PEIS-SR-04） |
| `execStatusEnum` | `String` | 必填 |
| `reason` | `String` | abandoned/deferred 时必填 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `markChecked` / `markAbandoned` |
| `statusTransitionMethod` | exec 迁移 |
| `repositoryChecks` | 行存在；SR.encounterId 匹配 |
| `eventPublishAnchor` | `PeisServiceRequestExecStatusChangedEvent` |
| `unitTestCaseId` | TC-PEIS-SR-002 |

**方法契约摘要**：前置：BR-PEIS-SR-03 院点策略在 AS 预检；持久化：update ext；失败：不存在/encounter 不匹配；**注释锚点**：校验归属 → 迁态 → save → 事件。

---

#### 5.1.3 `updateDispatchFlags(UpdatePeisSrDispatchCommand cmd)` → `PeisServiceRequestExt`

**命令文件**：`UpdatePeisSrDispatchCommand.java`

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `serviceRequestId` | `Long` | 必填 |
| `moduleEnum` | `String` | LIS / PACS |
| `dispatchEnum` | `String` | notSent/sent/resultReceived |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `updateLisDispatch` / `updatePacsDispatch` |
| `statusTransitionMethod` | dispatch 单向推进（院点可配置回退） |
| `repositoryChecks` | 行存在 |
| `eventPublishAnchor` | `PeisServiceRequestDispatchChangedEvent` |
| `unitTestCaseId` | TC-PEIS-SR-003 |

**方法契约摘要**：INT-01 置 sent；DES-08 置 resultReceived；**注释锚点**：校验 module → 更新对应 enum → save → 事件。

---

### 5.2 读侧 — `PeisServiceRequestExtQueryService`

| 方法 | 入参 | 出参 | `unitTestCaseId` |
| ---- | ---- | ---- | ---------------- |
| `findByServiceRequestId` | `Long srId` | `PeisServiceRequestExtModel` | TC-PEIS-SR-R01 |
| `listByEncounterId` | `Long encounterId` | `List<PeisServiceRequestExtModel>` | TC-PEIS-SR-R02 |

**读方法注释锚点（统一）**：join SR → 软删过滤 → 组装 Model。

---

## 6. 不变式映射（DM §7）

| BR 编号 | 实现策略 |
| ------- | -------- |
| **BR-PEIS-SR-01** | PK=serviceRequestId 唯一 |
| **BR-PEIS-SR-02** | 实体无 barcode 字段 |
| **BR-PEIS-SR-03** | checked 前置由 AS+Observation 协作 |
| **BR-PEIS-SR-04** | updateExecStatus 校验 encounterId |

---

## 7. 事件契约与触发规则（DM §5）

| 事件类名 | 事件类包路径 | 最小载荷 | 触发方法 |
| -------- | ------------ | -------- | -------- |
| `PeisServiceRequestExecStatusChangedEvent` | `com.zxplat.domain.share.events.custom.peisservicerequestext` | `serviceRequestId, encounterId, execStatusEnum` | `updateExecStatus` |
| `PeisServiceRequestDispatchChangedEvent` | 同上 | `serviceRequestId, moduleEnum, dispatchEnum` | `updateDispatchFlags` |

**发布时机**：全部 **beforeCommit**。

---

## 8. 删除与可见性契约

- ext 随 SR 逻辑删除策略：软删 ext 行，不物理删。
- 查询默认 `isDeleted=false`。

---

## 9. 框架优先适配规则

- 遵循项目级成码门禁；Repository 无 JPQL。

---

## 10. 生成与验收清单

- [ ] 实体 PK 为 serviceRequestId
- [ ] 三写方法 + 两读方法
- [ ] 两事件发布单测
- [ ] 无 barcode 列

---

## 11. 单次会话代码生成门禁（DoD）

### 11.1 前置输入

DM-PEIS-001、本 SDD、peis V1

### 11.2 DoD

编译通过；init/update 单测绿

### 11.3 失败处理

SR FK 未建 → 先 WKF 成码或文档标注依赖

---

## 定稿检查清单（可打勾）

- [ ] §5.1 节数 = 3
- [ ] §7 事件 = 2
- [ ] 字段表完整

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
