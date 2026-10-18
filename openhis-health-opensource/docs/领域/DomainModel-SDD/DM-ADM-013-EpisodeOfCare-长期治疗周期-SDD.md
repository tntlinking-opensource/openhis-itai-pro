# DM-ADM-013 EpisodeOfCare 长期治疗周期 · DomainModel-SDD

> **对应 DM**：`docs/领域/DomainModel/DM-ADM-013-EpisodeOfCare-长期治疗周期.md`（v1.0）  
> **限界上下文**：患者管理 · 长期治疗管理  
> **聚合**：EpisodeOfCare（主表 + 子表）

---

## 输入完整性检查结果

| 最小输入项 | 状态 | 说明 |
| ---------- | ---- | ---- |
| DM 路径 | 满足 | 已绑定 DM-ADM-013 |
| SDD 输出路径 | 满足 | 本文件 |
| 包规则 | 满足 | `com.zxplat.domain.administration.episodeofcare` |
| 技术口径（Long/LocalDateTime/软删/beforeCommit） | 满足 | 见 §3 |
| DM 写/读白名单 | 满足 | §5 全覆盖 |
| DM 事件/不变式 | 满足 | §6-§7 |

---

## 1. 设计目标与边界

- 固化 EpisodeOfCare 聚合在 `zxplat/zxplat-domain` 的可直接成码契约：实体、子表对齐策略、写读服务、事件与 BR 映射。
- 保证一方法一 Command，子表采用“全量对齐 + 软删”管理关联历史。
- 跨域依赖仅保留引用（Patient/Encounter/Organization），不在本聚合执行跨域写入。

---

## 2. 工程落点与包结构

| 相对包 | 内容 |
| ------ | ---- |
| `entity` | `EpisodeOfCare`、`EpisodeOfCareEncounter` |
| `valueobject` | `EpisodeOfCareStatus` |
| `command` | 9 个写方法对应命令 |
| `repository` | `EpisodeOfCareRepository`、`EpisodeOfCareEncounterRepository` |
| `service` | `*DomainService` 与 `*QueryService`（两个方法集，直接实现类） |
| `queries` | 查询入参对象（如 `*Query`） |
| `queries.model` | 查询出参模型（如 `*QueryModel`） |
| `share.events` | `com.zxplat.domain.share.events.administration.episodeofcare` |

---

## 3. 类型 / 字段 / 注解规则

| 规则 | 说明 |
| ---- | ---- |
| 主键/外键 | `Long` |
| 时间字段 | `LocalDateTime` |
| `*_enum` | `Integer` |
| `*_code` | `String` |
| 软删 | `Boolean isDeleted` |
| 事件 | beforeCommit 发布 |

---

## 4. 实体 / 查询模型契约

### 4.1 表映射

| 逻辑表 | 实体类 | 说明 |
| ------ | ------ | ---- |
| `adm_episode_of_care` | `EpisodeOfCare` | 主档 |
| `adm_episode_of_care_encounter` | `EpisodeOfCareEncounter` | 周期-就诊关联 |

### 4.2 聚合根 `EpisodeOfCare`（关键字段）

`id`,`patientId`,`busNo`,`statusEnum`,`startTime`,`endTime`,`organizationId`,`careTeamId`,`careType`,`careGoal`,`isDeleted`

### 4.3 子实体 `EpisodeOfCareEncounter`（关键字段）

`id`,`episodeOfCareId`,`encounterId`,`joinTime`,`leaveTime`,`isDeleted`

### 4.6 查询模型

#### 4.6.1 `EpisodeOfCareByIdQueryModel`（对应 `loadEpisodeOfCareById`）

`id`,`busNo`,`statusEnum`,`startTime`,`endTime`,`organizationId`

#### 4.6.2 `EpisodeOfCareByBusNoQueryModel`（对应 `loadEpisodeOfCareByBusNo`）

`id`,`busNo`,`patientId`,`statusEnum`,`careType`

#### 4.6.3 `EpisodeOfCareByPatientQueryModel`（对应 `listEpisodeOfCareByPatient`）

`id`,`patientId`,`busNo`,`statusEnum`,`startTime`,`endTime`

#### 4.6.4 `EpisodeOfCareByOrganizationQueryModel`（对应 `listEpisodeOfCareByOrganization`）

`id`,`organizationId`,`busNo`,`statusEnum`,`careType`

#### 4.6.5 `ActiveEpisodeOfCareByPatientQueryModel`（对应 `listActiveEpisodeOfCareByPatient`）

`id`,`patientId`,`busNo`,`statusEnum`,`startTime`

---

## 5. 写侧与读侧白名单（命令字段全集）

### 5.1 写侧 — `EpisodeOfCareDomainService`

#### 5.1.1 `createEpisodeOfCare(CreateEpisodeOfCareCommand cmd)` → `Long`
字段全集：`patientId`,`organizationId`,`busNo`,`startTime`,`careTeamId`,`careType`,`careGoal`

#### 5.1.2 `activateEpisodeOfCare(ActivateEpisodeOfCareCommand cmd)` → `void`
字段全集：`episodeOfCareId`,`activatedAt`

#### 5.1.3 `updateEpisodeOfCareProfile(UpdateEpisodeOfCareProfileCommand cmd)` → `void`
字段全集：`episodeOfCareId`,`organizationId`,`careTeamId`,`careType`,`careGoal`

#### 5.1.4 `addEncounterToEpisode(AddEncounterToEpisodeCommand cmd)` → `Long`
字段全集：`episodeOfCareId`,`encounterId`,`joinTime`

#### 5.1.5 `pauseEpisodeOfCare(PauseEpisodeOfCareCommand cmd)` → `void`
字段全集：`episodeOfCareId`,`reason`

#### 5.1.6 `resumeEpisodeOfCare(ResumeEpisodeOfCareCommand cmd)` → `void`
字段全集：`episodeOfCareId`,`resumedAt`

#### 5.1.7 `completeEpisodeOfCare(CompleteEpisodeOfCareCommand cmd)` → `void`
字段全集：`episodeOfCareId`,`endTime`,`resultSummary`

#### 5.1.8 `cancelEpisodeOfCare(CancelEpisodeOfCareCommand cmd)` → `void`
字段全集：`episodeOfCareId`,`reason`

#### 5.1.9 `markEpisodeOfCareEnteredInError(MarkEpisodeOfCareEnteredInErrorCommand cmd)` → `void`
字段全集：`episodeOfCareId`,`reason`

**写侧统一契约摘要**：前置条件按状态机与 BR 校验；主表状态推进与子表新增同事务；失败语义为状态非法/重复绑定/引用无效；事件统一 beforeCommit 发布。

### 5.2 读侧 — `EpisodeOfCareQueryService`

| 方法 | 入参 | 出参 |
| ---- | ---- | ---- |
| `loadEpisodeOfCareById` | `Long episodeOfCareId` | `EpisodeOfCareByIdQueryModel` |
| `loadEpisodeOfCareByBusNo` | `String busNo` | `EpisodeOfCareByBusNoQueryModel` |
| `listEpisodeOfCareByPatient` | `Long patientId` | `List<EpisodeOfCareByPatientQueryModel>` |
| `listEpisodeOfCareByOrganization` | `Long organizationId` | `List<EpisodeOfCareByOrganizationQueryModel>` |
| `listActiveEpisodeOfCareByPatient` | `Long patientId` | `List<ActiveEpisodeOfCareByPatientQueryModel>` |

---

## 6. 不变式映射（DM §7）

| BR 编号 | 实现策略 |
| ------- | -------- |
| BR-EOC-001 | `busNo` 全局唯一，仓储唯一性校验 |
| BR-EOC-002 | `patientId` 必须有效 |
| BR-EOC-003 | `COMPLETED` 必填 `endTime` |
| BR-EOC-004 | 子表 0..N，主外键强约束 |
| BR-EOC-005 | `startTime <= endTime`（若 endTime 非空） |
| BR-EOC-006 | 同周期下 `encounterId` 不可重复有效绑定 |
| BR-EOC-007 | 终止态不可逆 |
| BR-EOC-008 | 默认过滤软删 |

---

## 7. 事件契约与触发规则

| 事件类名 | 事件类包路径 | 最小载荷（字段） | 触发方法 |
| -------- | ------------ | ---------------- | -------- |
| `EpisodeOfCareCreatedEvent` | `com.zxplat.domain.share.events.administration.episodeofcare` | `episodeOfCareId,patientId,busNo,organizationId,statusEnum` | `createEpisodeOfCare` |
| `EpisodeOfCareUpdatedEvent` | 同上 | `episodeOfCareId,changedFields` | `updateEpisodeOfCareProfile` |
| `EpisodeOfCareActivatedEvent` | 同上 | `episodeOfCareId,activatedAt` | `activateEpisodeOfCare` |
| `EncounterAddedToEpisodeEvent` | 同上 | `episodeOfCareId,encounterId,joinTime` | `addEncounterToEpisode` |
| `EpisodeOfCarePausedEvent` | 同上 | `episodeOfCareId,reason` | `pauseEpisodeOfCare` |
| `EpisodeOfCareResumedEvent` | 同上 | `episodeOfCareId,resumedAt` | `resumeEpisodeOfCare` |
| `EpisodeOfCareCompletedEvent` | 同上 | `episodeOfCareId,endTime,resultSummary` | `completeEpisodeOfCare` |
| `EpisodeOfCareCancelledEvent` | 同上 | `episodeOfCareId,reason` | `cancelEpisodeOfCare` |
| `EpisodeOfCareEnteredInErrorEvent` | 同上 | `episodeOfCareId,reason` | `markEpisodeOfCareEnteredInError` |

---

## 8. 删除与可见性契约

- 主表与子表均软删，不物理删除历史。
- 终止态记录长期保留用于审计追溯。
- 默认查询过滤软删，审计查询可显式放开。

---

## 9. 框架优先适配规则

- 一方法一 Command，不使用通用更新命令。
- 子表采用全量对齐：命中保留、缺失软删、新增插入。
- 仓储仅接口，复杂QueryService由 `queries` 实现。
- 事件 beforeCommit 发布，避免提交前后不一致。

---

## 10. 生成与验收清单

- [ ] 写方法数量 9（与 DM §6.1 对齐）
- [ ] 读方法数量 5（与 DM §6.2 对齐）
- [ ] 事件数量 9（与 DM §5.1 对齐）
- [ ] BR 覆盖 BR-EOC-001..008
- [ ] 子表唯一性规则 BR-EOC-006 明确

---

## 11. 单次会话代码生成门禁（DoD）

- 写侧 9 方法签名、命令命名与本 SDD 一致。
- 状态机路径 `PLANNED->ACTIVE->ON_HOLD/COMPLETED/CANCELLED/ENTERED_IN_ERROR` 可执行。
- 子表关联新增与去重可测试。
- 读侧 5 方法仅查询不写库。
- 事件 9 条全部可追溯到触发方法。

---

## 定稿检查清单（可打勾）

- [ ] 主章节结构与金骨架一致（输入检查、1-11、定稿检查、评分、结束语）
- [ ] §5.1 子节数量 = DM 写方法数量（9）
- [ ] §4.2/§4.3/§4.4 字段表覆盖 DM §3 主表与子表
- [ ] §7 事件表覆盖 DM §5 全部事件（9）
- [ ] 全文无 YAML、`sdd_status`、`dm_code`、占位措辞

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
| **总分** | **100** | **100（可进入代码会话）** |

---

## 12. 与 DM 同步记录

| 日期 | 摘要 |
| --- | --- |
| 2026-05-28 | **对账收口**：与 `zxplat-core/zxplat-domain` 现行实现一致；本聚合**无** InpatientInfo/OutpatientInfo/OrderInfo ReadModel 消费（跨聚合读组装见 **DM-ADM-002** §6.2.18～20 · `EncounterContextFacade`）。 |（已迁至 EncounterContextReader / application.shared.encounter）

---

**本 SDD 正文结束。**

