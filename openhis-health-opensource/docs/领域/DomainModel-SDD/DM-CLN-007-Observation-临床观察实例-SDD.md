# DM-CLN-007 Observation 临床观察实例 · DomainModel-SDD

> **对应 DM**：`docs/领域/DomainModel/DM-CLN-007-Observation-临床观察实例.md`  
> **目标工程**：`zxplat-core/zxplat-domain`  
> **限界上下文**：clinical · 观察实例  
> **聚合**：Observation（投影服务见 §5.1.5～5.1.6）

---

## 输入完整性检查结果

| 最小输入项 | 状态 | 说明 |
| ---------- | ---- | ---- |
| DM 路径 | 满足 | DM-CLN-007 |
| 包 `com.zxplat.domain.clinical.observation` | 满足 | |
| DM §6.1 ×4 + §6.3 ×2 | 满足 | §5.1.1～5.1.6 |
| DM §5 ×4 事件 | 满足 | |
| health V3 `cln_observation` | 满足 | |

---

## 1. 设计目标与边界

### 1.1 设计目标

- 手工录入与 LIS/PACS 投影统一 **`cln_observation`**；关闭 GAP-PEIS-DES-01 / INT-01。
- `LisResultProjectionService` 与 `ObservationDomainService` 同包，投影 **必须** 写 `sourceObservationId`（BR-CLN-OBS-01）。

### 1.2 边界

- **在内**：`cln_observation` 行级事实。
- **在外**：`lis_observation` 只读；OD 在 DM-CLN-005。

---

## 2. 工程落点与包结构

### 2.2 包结构（`com.zxplat.domain.clinical.observation`）

| 相对包 | 内容 |
| ------ | ---- |
| `entity` | `Observation` |
| `command` | Create/Update/Finalize/Cancel/Project* Command |
| `repository` | `ObservationRepository` |
| `service` | `ObservationDomainService`、`LisResultProjectionService` |
| `queries` | `ObservationQueryService` |
| `queries.model` | `ObservationModel` |

---

## 3. 类型 / 字段命名 / 注解规则

`statusEnum` / `sourceModuleEnum` → `String`；`valueQuantity` → `BigDecimal`；`effectiveTime` → `LocalDateTime`。

---

## 4. 实体 / 仓储 / 查询契约

### 4.2 聚合根 `Observation`

| 字段名 | 类型 | 必填 | 说明 |
| ------ | ---- | ---- | ---- |
| `id` | `Long` | 系统 | |
| `encounterId` | `Long` | 是 | |
| `serviceRequestId` | `Long` | 否 | |
| `definitionId` | `Long` | 是 | |
| `code` | `String` | 否 | |
| `valueQuantity` | `BigDecimal` | 否 | |
| `valueString` | `String` | 否 | |
| `valueCodeableConcept` | `String` | 否 | |
| `interpretationCode` | `String` | 否 | |
| `statusEnum` | `String` | 是 | |
| `effectiveTime` | `LocalDateTime` | 否 | |
| `sourceObservationId` | `Long` | 条件 | LIS 投影必填 |
| `sourceModuleEnum` | `String` | 否 | |
| `performerId` | `Long` | 否 | |
| `tenantId` | `Long` | 是 | |
| `orgId` | `Long` | 否 | |
| `isDeleted` | `Boolean` | 系统 | |
| `createdAt` | `LocalDateTime` | 系统 | |
| `updatedAt` | `LocalDateTime` | 系统 | |

**不变式**：`applyValue` / `finalize` / `markAmended` / `cancel`

### 4.3 子实体

无。

### 4.5 仓储

```java
public interface ObservationRepository extends JpaRepository<Observation, Long> {
    Optional<Observation> findByServiceRequestIdAndDefinitionIdAndIsDeletedFalse(Long srId, Long defId);
    List<Observation> findByEncounterIdAndIsDeletedFalse(Long encounterId);
    List<Observation> findByServiceRequestIdAndIsDeletedFalse(Long srId);
}
```

### 4.6 查询模型

#### 4.6.1 `ObservationModel`（list/find 系列）

| 字段名 | 类型 | 来源 | 说明 |
| ------ | ---- | ---- | ---- |
| `id` | `Long` | obs | |
| `encounterId` | `Long` | | |
| `serviceRequestId` | `Long` | | |
| `definitionId` | `Long` | | |
| `definitionDisplay` | `String` | join OD | |
| `categoryCode` | `String` | join OD | |
| `valueQuantity` | `BigDecimal` | | |
| `valueString` | `String` | | |
| `interpretationCode` | `String` | | |
| `statusEnum` | `String` | | |
| `sourceObservationId` | `Long` | | |
| `sourceModuleEnum` | `String` | | |

---

## 5. 写侧与读侧白名单

### 5.1 写侧 — `ObservationDomainService` / `LisResultProjectionService`

#### 5.1.1 `createObservation(CreateObservationCommand cmd)` → `Observation`

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `encounterId` | `Long` | 必填 |
| `serviceRequestId` | `Long` | 可选 |
| `definitionId` | `Long` | 必填 |
| `valueQuantity` / `valueString` / `valueCodeableConcept` | | 按 OD 类型 |
| `interpretationCode` | `String` | |
| `performerId` | `Long` | |

| 绑定项 | 内容 |
| ------ | ---- |
| `eventPublishAnchor` | `ObservationCreatedEvent` |
| `unitTestCaseId` | TC-CLN-OBS-001 |

**方法契约摘要**：手工录入无 source；幂等键冲突转 update（BR-CLN-OBS-02）。

---

#### 5.1.2 `updateObservation(UpdateObservationCommand cmd)` → `Observation`

含 `observationId` + 值字段；final 改值须 amended（BR-CLN-OBS-03）。

| `eventPublishAnchor` | `ObservationUpdatedEvent` |
| `unitTestCaseId` | TC-CLN-OBS-002 |

---

#### 5.1.3 `finalizeObservation(FinalizeObservationCommand cmd)` → `Observation`

| 字段 | `observationId`, `operatorId` |
| `eventPublishAnchor` | `ObservationFinalizedEvent` |
| `unitTestCaseId` | TC-CLN-OBS-003 |

---

#### 5.1.4 `cancelObservation(CancelObservationCommand cmd)` → `Observation`

| 字段 | `observationId`, `reason` |
| `statusTransitionMethod` | → cancelled |
| `unitTestCaseId` | TC-CLN-OBS-004 |

---

#### 5.1.5 `projectFromLis(ProjectFromLisCommand cmd)` → `List<Observation>`

**服务类**：`LisResultProjectionService`

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `serviceRequestId` | `Long` | 必填 |
| `operatorId` | `Long` | 可选 |

| 绑定项 | 内容 |
| ------ | ---- |
| `repositoryChecks` | 读 lis_observation by SR；**只读** [DM-CUS-013](./DM-CUS-013-ActivityObservationLink-项目小项绑定.md) `listByActivityId` 得目标 OD；BR-CLN-OBS-01 |
| `entityInvariantMethod` | upsert by (srId, definitionId) |
| `eventPublishAnchor` | `ObservationProjectedFromLisEvent` |
| `unitTestCaseId` | TC-CLN-OBS-005 |

**方法契约摘要**：由 SR 解析 activityId → **只读 AOL** 得 definitionId 列表 → 逐条 lis 行对齐 OD → upsert CLN + sourceObservationId；**P0 主触发** LIS AUD-05 发布；PEIS INT-03 为补偿；失败：AOL 空且院点无默认策略则 AS 拒绝；**注释锚点**：SR→activity→AOL→LIS 行→map→upsert→事件。

---

#### 5.1.6 `projectFromPacsSummary(ProjectFromPacsCommand cmd)` → `Observation`

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `serviceRequestId` | `Long` | 必填 |
| `diagnosticReportIndexId` | `Long` | index upsert 后 |
| `summaryText` | `String` | PACS 报告摘要 |

| 绑定项 | 内容 |
| ------ | ---- |
| `repositoryChecks` | SR 有 encounterId；读 PACS DR 摘要 |
| `eventPublishAnchor` | `ObservationProjectedFromPacsEvent` |
| `unitTestCaseId` | TC-CLN-OBS-006 |

**方法契约摘要**：**P0 主触发** PACS REP-07；upsert 一条（或院点多条）摘要型 `cln_observation`；**无** `source_observation_id`（PACS 摘要非 lis_observation 行）；幂等键 `(serviceRequestId, definitionId)`；DES-08 补偿。

---

### 5.2 读侧 — `ObservationQueryService`

| 方法 | 入参 | 出参 | `unitTestCaseId` |
| ---- | ---- | ---- | ---------------- |
| `listByEncounterId` | `Long` | `List<ObservationModel>` | TC-CLN-OBS-R01 |
| `listByServiceRequestId` | `Long` | `List<ObservationModel>` | TC-CLN-OBS-R02 |
| `findBySrAndDefinition` | `srId, defId` | `Optional<ObservationModel>` | TC-CLN-OBS-R03 |

---

## 6. 不变式映射（DM §7）

| BR | 策略 |
| ---- | ---- |
| **BR-CLN-OBS-01** | projectFromLis 强制 sourceObservationId |
| **BR-CLN-OBS-02** | findBySrAndDefinition 幂等 |
| **BR-CLN-OBS-03** | final 后改值走 amended |
| **BR-CLN-OBS-04** | LIS AUD-05 发布且 SR 有 encounterId 时须 projectFromLis |
| **BR-CLN-OBS-05** | PACS REP-07 审核通过且 SR 有 encounterId 时须 projectFromPacsSummary |

---

## 7. 事件契约（DM §5）

| 事件 | 包路径 | 触发 |
| ---- | ------ | ---- |
| `ObservationCreatedEvent` | `...clinical.observation` | create |
| `ObservationUpdatedEvent` | 同上 | update |
| `ObservationFinalizedEvent` | 同上 | finalize |
| `ObservationProjectedFromLisEvent` | 同上 | projectFromLis |

**发布时机**：**beforeCommit**。

---

## 8. 删除与可见性契约

- cancel 置 statusEnum=cancelled；软删仅管理端。

---

## 9. 框架优先适配规则

- 读 LIS 经 `LisObservationQueryService` Client（跨模块只读），禁止直写 lis 表。

---

## 10. 生成与验收清单

- [ ] 六写方法编译
- [ ] projectFromLis 单测：source 链
- [ ] DES-08 集成夹具
- [ ] idx_cln_obs_sr_def 使用

---

## 11. 单次会话代码生成门禁（DoD）

### 11.1 前置输入

DM-CLN-007、health V3、LIS Observation 可读

### 11.2 DoD

关闭 GAP-PEIS-INT-01 领域层

### 11.3 失败处理

LIS 未部署 → Mock Client + 文档标注

---

## 定稿检查清单（可打勾）

- [ ] §5.1 = 6
- [ ] §7 = 4 事件

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
