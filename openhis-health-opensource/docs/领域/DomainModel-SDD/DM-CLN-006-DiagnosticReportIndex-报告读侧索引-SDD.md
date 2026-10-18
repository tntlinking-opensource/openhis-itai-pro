# DM-CLN-006 DiagnosticReportIndex 报告读侧索引 · DomainModel-SDD

> **对应 DM**：`docs/领域/DomainModel/DM-CLN-006-DiagnosticReportIndex-报告读侧索引.md`  
> **目标工程**：`zxplat-core/zxplat-domain`  
> **限界上下文**：clinical · 报告索引  
> **聚合**：DiagnosticReportIndex

---

## 输入完整性检查结果

| 最小输入项 | 状态 | 说明 |
| ---------- | ---- | ---- |
| DM 路径 | 满足 | DM-CLN-006 |
| 包 `com.zxplat.domain.clinical.diagnosticreportindex` | 满足 | |
| DM §6.1 ×4 | 满足 | |
| DM §5 ×2 事件 | 满足 | |
| health V3 | 满足 | |

---

## 1. 设计目标与边界

### 1.1 设计目标

- 跨 LIS/PACS/PEIS **统一列表索引**；MAS-04 upsertFromPeis；LIS/PACS 事件消费 upsert。
- **不存**报告正文（BR-DR-IDX-03）。

### 1.2 边界

- **在内**：`cln_diagnostic_report_index`。
- **在外**：各模块 DR 事实表。

---

## 2. 工程落点与包结构

### 2.2 包结构（`com.zxplat.domain.clinical.diagnosticreportindex`）

| 相对包 | 内容 |
| ------ | ---- |
| `entity` | `DiagnosticReportIndex` |
| `command` | Upsert* / MarkCancelled Command |
| `repository` | `DiagnosticReportIndexRepository` |
| `service` | `DiagnosticReportIndexUpsertService` |
| `queries` | `DiagnosticReportIndexQueryService` |
| `queries.model` | `DiagnosticReportIndexRowModel` |

---

## 3. 类型 / 字段命名 / 注解规则

`moduleEnum` / `statusEnum` → `String`；`uniqueKey` = `{moduleEnum}:{sourceReportId}`。

---

## 4. 实体 / 仓储 / 查询契约

### 4.2 聚合根 `DiagnosticReportIndex`

字段与 DM §3.1 一致（§4.2 全表对齐 health V3）。

### 4.3 子实体

无。

### 4.5 仓储

```java
public interface DiagnosticReportIndexRepository extends JpaRepository<DiagnosticReportIndex, Long> {
    Optional<DiagnosticReportIndex> findByUniqueKeyAndIsDeletedFalse(String uniqueKey);
}
```

### 4.6 查询模型

#### 4.6.1 `DiagnosticReportIndexRowModel`（searchByPatient / searchByEncounter）

| 字段名 | 类型 | 来源 | 说明 |
| ------ | ---- | ---- | ---- |
| `id` | `Long` | index | |
| `moduleEnum` | `String` | | |
| `sourceReportId` | `Long` | | |
| `encounterId` | `Long` | | |
| `patientId` | `Long` | | |
| `statusEnum` | `String` | | |
| `publishedAt` | `LocalDateTime` | | |
| `title` | `String` | | |
| `pdfStorageKey` | `String` | | |
| `externalViewUrl` | `String` | | |

#### 4.6.2 `DiagnosticReportIndexRowModel`（getByUniqueKey）

同 §4.6.1。

---

## 5. 写侧与读侧白名单

### 5.1 写侧 — `DiagnosticReportIndexUpsertService`

#### 5.1.1 `upsertFromLis(UpsertDrIndexFromLisCommand cmd)` → `DiagnosticReportIndex`

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `sourceReportId` | `Long` | LIS DR id |
| `patientId` | `Long` | |
| `encounterId` | `Long` | |
| `serviceRequestId` | `Long` | 可选 |
| `statusEnum` | `String` | final/amended |
| `publishedAt` | `LocalDateTime` | |
| `title` | `String` | |
| `pdfStorageKey` | `String` | |

| 绑定项 | 内容 |
| ------ | ---- |
| `repositoryChecks` | BR-DR-IDX-01 status 过滤 |
| `eventPublishAnchor` | `DiagnosticReportIndexUpsertedEvent` |
| `unitTestCaseId` | TC-CLN-DRIDX-001 |

**方法契约摘要**：uniqueKey=LIS:sourceReportId 幂等 upsert。

---

#### 5.1.2 `upsertFromPacs(UpsertDrIndexFromPacsCommand cmd)` → `DiagnosticReportIndex`

结构同 5.1.1；moduleEnum=PACS。

| `unitTestCaseId` | TC-CLN-DRIDX-002 |

---

#### 5.1.3 `upsertFromPeis(UpsertDrIndexFromPeisCommand cmd)` → `DiagnosticReportIndex`

| 字段 | 含 `sourceReportId`（PEIS DR）、`encounterId`、`patientId`、`title` |
| 调用 | MAS-04 总检完成 |
| `unitTestCaseId` | TC-CLN-DRIDX-003 |

---

#### 5.1.4 `markCancelled(MarkDrIndexCancelledCommand cmd)` → `DiagnosticReportIndex`

| 字段 | `moduleEnum`, `sourceReportId`, `reason` |
| `statusTransitionMethod` | → cancelled 或软删 |
| `eventPublishAnchor` | `DiagnosticReportIndexCancelledEvent` |
| `unitTestCaseId` | TC-CLN-DRIDX-004 |

---

### 5.2 读侧 — `DiagnosticReportIndexQueryService`

| 方法 | 入参 | 出参 | `unitTestCaseId` |
| ---- | ---- | ---- | ---------------- |
| `searchByPatient` | `patientId, DrIndexSearchQuery, Pageable` | `Page<DiagnosticReportIndexRowModel>` | TC-CLN-DRIDX-R01 |
| `searchByEncounter` | `encounterId` | `List<DiagnosticReportIndexRowModel>` | TC-CLN-DRIDX-R02 |
| `getByUniqueKey` | `moduleEnum, sourceReportId` | `DiagnosticReportIndexRowModel` | TC-CLN-DRIDX-R03 |

---

## 6. 不变式映射（DM §7）

| BR | 策略 |
| ---- | ---- |
| **BR-DR-IDX-01** | upsert 拒绝非 final（院点 preliminary 例外配置） |
| **BR-DR-IDX-02** | markCancelled |
| **BR-DR-IDX-03** | 实体无正文列 |

---

## 7. 事件契约（DM §5）

| 事件 | 包路径 | 触发 |
| ---- | ------ | ---- |
| `DiagnosticReportIndexUpsertedEvent` | `...clinical.diagnosticreportindex` | upsert* |
| `DiagnosticReportIndexCancelledEvent` | 同上 | markCancelled |

---

## 8. 删除与可见性契约

- 软删；列表默认 active 行。

---

## 9. 框架优先适配规则

- LIS/PACS 事件消费在 infrastructure 或 application listener，调用 UpsertService。

---

## 10. 生成与验收清单

- [ ] uk_cln_dr_index_unique_key
- [ ] MAS-04 upsertFromPeis 单测
- [ ] REP/历史列表读 searchByEncounter

---

## 11. 单次会话代码生成门禁（DoD）

### 11.1 前置输入

DM-CLN-006 v1.2、health V3

### 11.2 DoD

四写三读编译通过

### 11.3 失败处理

PEIS DR 表未定 → upsertFromPeis 先接 CLN DR 占位

---

## 定稿检查清单（可打勾）

- [ ] §5.1 = 4
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
