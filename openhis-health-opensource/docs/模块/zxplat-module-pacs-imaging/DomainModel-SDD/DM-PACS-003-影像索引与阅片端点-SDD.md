# DM-PACS-003 影像索引、关键图与阅片端点 · DomainModel-SDD

> **对应 DM**：`docs/领域/DomainModel/DM-PACS-003-影像索引与阅片端点.md`（本 SDD 假定该 DM 为当期锁定版本）  
> **目标工程**：`zxplat-plugin/zxplat-module-pacs-imaging(Wave3)`  
> **限界上下文**：影像扩展  
> **聚合**：ImagingStudy、Media、ViewerEndpoint（三个单表聚合，无子实体；同模块一份 SDD）

---

## 输入完整性检查结果

| 最小输入项 | 状态 | 说明 |
| ---------- | ---- | ---- |
| DM 路径 | 满足 | 已绑定 DM-PACS-003 |
| SDD 输出路径 | 满足 | 本文件 |
| 目标模块 | 满足 | `module-pacs-imaging-domain` |
| 包规则 | 满足 | `com.zxplat.plugin.pacsimaging.domain.{imagingstudy,media,viewerendpoint}` 全小写 |
| 固定技术口径 | 满足 | Long、LocalDateTime、软删、beforeCommit、一方法一 Command；见 §9 |
| DM 第 6 章写/读白名单 | 满足 | §5 覆盖 5 个写方法与 §6.4 全部读方法 |
| DM 第 7 章 BR | 满足 | §6 |
| DM 第 5 章事件 | 满足 | §7：无本聚合必须事件 |

**与 DM 显式差异（采纳依据：项目级类型口径）**

| 点 | DM 差异位置 | SDD 采纳 |
| -- | ----------- | -------- |
| 时间类型 | DM §3 写 Instant | `*At` 统一 `LocalDateTime` |
| 软删 | DM §3 未列 | 三张表补 `isDeleted` |
| 包前缀 | 技能默认 `com.zxplat.domain` | 本模块独立工程，用 `com.zxplat.plugin.pacsimaging.domain` |
| 枚举 | DM `sourceEnum` 为 String 码 | 存库 `String`；枚举类 `MediaSource` 放共享枚举包 |

---

## 1. 设计目标与边界

### 1.1 设计目标

- 固化三个聚合的表、命令、查询与不变式，使下一会话可一次生成 `module-pacs-imaging-domain` 并通过编译。
- 每个写方法一个 Command 文件，字段全集在 §5 列出。
- 正文不留待补项。像素、对象存储、工单 `hasMedia` 不在本模块领域服务内完成。

### 1.2 边界

- **在内**：Study/Series 索引、关键图对象键、阅片根地址。
- **在外**：Orthanc、MinIO、OHIF、工单状态机。
- **跨域端口**：应用层在本聚合写成功后调用 `com.zxplat.plugin.pacs.domain.workitem.service.PacsWorkItemDomainService#markHasMedia`。C-STORE 回写前调用 `PacsWorkItemQueryService#findByAccessionNumber`（DM-PACS-001 SDD §5.2 已列，实现若缺失则在 zxplat-domain 补该方法，不在本模块复制工单表）。

---

## 2. 工程落点与包结构

### 2.1 Maven 模块

- 模块：`zxplat-module-pacs-imaging/module-pacs-imaging-domain`。
- 依赖：`zxplat-domain` 仅编译期引用工单主键类型与错误码风格，不把 imaging 实体放进 health。

### 2.2 包结构

| 相对包 | 内容 |
| ------ | ---- |
| `imagingstudy.entity` / `media.entity` / `viewerendpoint.entity` | 三个实体 |
| `…command` | §5 每个写方法一个文件 |
| `…repository` | 仅 `JpaRepository` 接口 |
| `…service` | 三个 `*DomainService` 直接实现类 |
| `…queries` | 三个 `*QueryService` 直接实现类 |
| `…queries.model` | §4.6 |
| 共享枚举 | `com.zxplat.domain.share.enums.MediaSource` |
| 事件 | 本聚合不新增事件类 |

**存储口径**：`sourceEnum`、`modalityCode` 均为 `String`。`enabled` 为 `Boolean`。

---

## 3. 类型 / 字段命名 / 注解规则

| 规则 | 说明 |
| ---- | ---- |
| 主键 / 外键 | `Long`；`workItemId`、`organizationId` 与 DM 同名 |
| 时间 | `createdAt`、`updatedAt` 为 `LocalDateTime` |
| `*_enum` / `*_code` | `String` |
| 软删 | `Boolean isDeleted` |
| 实体 | `@Entity` + `@Table` + `@Column` |
| 仓储 | 仅接口，无自定义实现类，无手写 `@Query` |
| 分页 | 列表查询默认 `createdAt DESC` |
| 事务 | 领域服务不声明 `@Transactional` |
| 事件 | 本聚合不 `publish` |

---

## 4. 实体 / 值对象 / 枚举 / 仓储 / 查询契约

### 4.1 表与实体映射

| 逻辑表 | 实体类 | 说明 |
| ------ | ------ | ---- |
| `pacs_imaging_study` | `ImagingStudy` | 单表聚合 |
| `pacs_media` | `Media` | 单表聚合 |
| `pacs_viewer_endpoint` | `ViewerEndpoint` | 单表聚合 |

### 4.2 聚合根 `ImagingStudy`

| 字段名（Java） | 类型 | 必填 | 说明 / 约束 |
| -------------- | ---- | ---- | ----------- |
| `id` | `Long` | 系统 | 雪花 |
| `workItemId` | `Long` | 是 | 租户内唯一（BR-IMG-01） |
| `studyInstanceUid` | `String` | 是 | 租户内唯一（BR-IMG-02） |
| `seriesInstanceUid` | `String` | 否 | 可后补 |
| `accessionNumber` | `String` | 否 | 冗余检查号 |
| `modalityCode` | `String` | 否 | 模态码 |
| `tenantId` | `Long` | 是 | 租户 |
| `isDeleted` | `Boolean` | 系统 | 软删 |
| `createdBy` | `Long` | 系统 | |
| `createdAt` | `LocalDateTime` | 系统 | |
| `updatedBy` | `Long` | 系统 | |
| `updatedAt` | `LocalDateTime` | 系统 | |

**不变式方法**：`bindIdentity(workItemId, studyInstanceUid)` / `fillSeries(seriesInstanceUid)`。禁止字段承载像素或患者姓名。

### 4.3 聚合根 `Media`

| 字段名 | 类型 | 必填 | 说明 |
| ------ | ---- | ---- | ---- |
| `id` | `Long` | 系统 | |
| `workItemId` | `Long` | 是 | |
| `objectKey` | `String` | 是 | 租户内唯一 |
| `contentType` | `String` | 是 | 仅 `image/jpeg`、`image/png` |
| `sourceEnum` | `String` | 是 | `KEY_IMAGE` 或 `SCREENSHOT` |
| `tenantId` | `Long` | 是 | |
| `isDeleted` | `Boolean` | 系统 | |
| `createdBy` | `Long` | 系统 | |
| `createdAt` | `LocalDateTime` | 系统 | |

无子实体。删除必须 `findByIdAndWorkItemId`，工单不一致则拒绝。

### 4.4 聚合根 `ViewerEndpoint`

| 字段名 | 类型 | 必填 | 说明 |
| ------ | ---- | ---- | ---- |
| `id` | `Long` | 系统 | |
| `organizationId` | `Long` | 否 | 空=全局 |
| `modalityCode` | `String` | 否 | 空=该范围默认 |
| `baseUrl` | `String` | 是 | http 或 https 根，不得含 `StudyInstanceUIDs` |
| `enabled` | `Boolean` | 是 | |
| `tenantId` | `Long` | 是 | |
| `isDeleted` | `Boolean` | 系统 | |
| `createdBy` | `Long` | 系统 | |
| `createdAt` | `LocalDateTime` | 系统 | |
| `updatedBy` | `Long` | 系统 | |
| `updatedAt` | `LocalDateTime` | 系统 | |

**不变式方法**：`assertBaseUrl` / `disable`。

### 4.5 仓储接口

```java
public interface ImagingStudyRepository extends JpaRepository<ImagingStudy, Long> {
    Optional<ImagingStudy> findByWorkItemIdAndTenantId(Long workItemId, Long tenantId);
    Optional<ImagingStudy> findByStudyInstanceUidAndTenantId(String studyInstanceUid, Long tenantId);
}

public interface MediaRepository extends JpaRepository<Media, Long> {
    Optional<Media> findByIdAndWorkItemId(Long id, Long workItemId);
    Optional<Media> findByObjectKeyAndTenantId(String objectKey, Long tenantId);
    List<Media> findByWorkItemIdOrderByCreatedAtDesc(Long workItemId);
    long countByWorkItemId(Long workItemId);
}

public interface ViewerEndpointRepository extends JpaRepository<ViewerEndpoint, Long> {
    List<ViewerEndpoint> findByTenantIdAndOrganizationIdOrderByCreatedAtDesc(Long tenantId, Long organizationId);
    List<ViewerEndpoint> findByTenantIdAndEnabledTrue(Long tenantId);
}
```

`resolve` 在 QueryService 内对 `findByTenantIdAndEnabledTrue` 的结果按 §5.2 顺序挑选，不写 `@Query`。机构为空的列表用 `findByTenantIdAndOrganizationIdIsNull`。若基类已过滤软删，方法名不再追加 `IsDeletedFalse`。

### 4.6 查询模型

#### 4.6.1 `ImagingStudyQueryModel`（`findByWorkItemId`）

| 字段名 | 类型 | 来源 | 说明 |
| ------ | ---- | ---- | ---- |
| `id` | `Long` | `pacs_imaging_study.id` | |
| `workItemId` | `Long` | `work_item_id` | |
| `studyInstanceUid` | `String` | `study_instance_uid` | |
| `seriesInstanceUid` | `String` | `series_instance_uid` | |
| `accessionNumber` | `String` | `accession_number` | |
| `modalityCode` | `String` | `modality_code` | |

没有行时返回空 Optional，不返回 null 集合。

#### 4.6.2 `MediaQueryModel`（`listByWorkItemId`）

| 字段名 | 类型 | 来源 | 说明 |
| ------ | ---- | ---- | ---- |
| `id` | `Long` | `pacs_media.id` | |
| `workItemId` | `Long` | `work_item_id` | |
| `objectKey` | `String` | `object_key` | 不含预签名 URL |
| `contentType` | `String` | `content_type` | |
| `sourceEnum` | `String` | `source_enum` | |
| `createdAt` | `LocalDateTime` | `created_at` | |

`countByWorkItemId` 返回 `long`，无 QueryModel。

#### 4.6.3 `ViewerEndpointQueryModel`（`list` / `resolve`）

| 字段名 | 类型 | 来源 | 说明 |
| ------ | ---- | ---- | ---- |
| `id` | `Long` | `pacs_viewer_endpoint.id` | |
| `organizationId` | `Long` | `organization_id` | |
| `modalityCode` | `String` | `modality_code` | |
| `baseUrl` | `String` | `base_url` | |
| `enabled` | `Boolean` | `enabled` | |
| `matchedBy` | `String` | 无列 | 仅 `resolve` 填充：`ORG_MODALITY` / `ORG_DEFAULT` / `GLOBAL` |

---

## 5. 写侧与读侧白名单（命令字段全集）

### 5.1 写侧 — ImagingStudy / Media / ViewerEndpoint

#### 5.1.1 `ensureStudy(EnsureImagingStudyCommand cmd)` → `ImagingStudy`

**命令文件**：`EnsureImagingStudyCommand.java`

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `workItemId` | `Long` | 必填 |
| `studyInstanceUid` | `String` | 必填 |
| `seriesInstanceUid` | `String` | 可选 |
| `accessionNumber` | `String` | 可选 |
| `modalityCode` | `String` | 可选 |
| `tenantId` | `Long` | 必填 |
| `operatorId` | `Long` | 必填 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `bindIdentity`；有 Series 时 `fillSeries` |
| `statusTransitionMethod` | 无状态机 |
| `repositoryChecks` | 先按 workItemId 查，再按 studyInstanceUid 查；命中则补 Series 后返回；两个键指向不同行则失败 `PACS_IMAGING_STUDY_BIND_FAILED` |
| `eventPublishAnchor` | 无 |
| `unitTestCaseId` | TC-IMG-001 |

**方法契约摘要**：前置：UID 非空；持久化：插入或原行更新 Series；失败：同一 UID 已属于别的工单；**注释锚点**：按工单查 → 按 UID 查 → 插入或补 Series。

---

#### 5.1.2 `bindFromCStore(BindImagingStudyFromCStoreCommand cmd)` → `ImagingStudy`

**命令文件**：`BindImagingStudyFromCStoreCommand.java`

字段与 `EnsureImagingStudyCommand` 相同。

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | 委托 `ensureStudy` |
| `repositoryChecks` | 同 5.1.1；重复通知返回原行 |
| `eventPublishAnchor` | 无 |
| `unitTestCaseId` | TC-IMG-002 |

**方法契约摘要**：不接收像素；幂等与 ensureStudy 相同。

---

#### 5.1.3 `saveKeyImage(SaveKeyImageCommand cmd)` → `Media`

**命令文件**：`SaveKeyImageCommand.java`

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `workItemId` | `Long` | 必填 |
| `objectKey` | `String` | 必填，调用前已写入 MinIO |
| `contentType` | `String` | 必填 |
| `sourceEnum` | `String` | 必填 |
| `tenantId` | `Long` | 必填 |
| `operatorId` | `Long` | 必填 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | 校验 contentType ∈ {image/jpeg, image/png}；sourceEnum ∈ MediaSource |
| `repositoryChecks` | `objectKey` 已存在则返回原行（幂等） |
| `eventPublishAnchor` | 无 |
| `unitTestCaseId` | TC-IMG-003 |

**方法契约摘要**：只插索引行；失败码 `PACS_KEY_IMAGE_TYPE_INVALID`；**注释锚点**：校验类型 → 按 objectKey 查 → 插入。

---

#### 5.1.4 `deleteKeyImage(DeleteKeyImageCommand cmd)` → `void`

**命令文件**：`DeleteKeyImageCommand.java`

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `mediaId` | `Long` | 必填 |
| `workItemId` | `Long` | 必填 |
| `tenantId` | `Long` | 必填 |
| `operatorId` | `Long` | 必填 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | 无 |
| `repositoryChecks` | `findByIdAndWorkItemId`；不存在或不属于该工单则 `PACS_KEY_IMAGE_NOT_FOUND` |
| `eventPublishAnchor` | 无 |
| `unitTestCaseId` | TC-IMG-004 |

**方法契约摘要**：软删该行。不删 MinIO 对象。应用层随后用 `countByWorkItemId` 与 `findByWorkItemId` 决定是否 `markHasMedia(false)`。

---

#### 5.1.5 `save(SaveViewerEndpointCommand cmd)` → `ViewerEndpoint`

**命令文件**：`SaveViewerEndpointCommand.java`（包 `viewerendpoint.command`）

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `id` | `Long` | 可选；空则新增 |
| `organizationId` | `Long` | 可选 |
| `modalityCode` | `String` | 可选；空白视为空 |
| `baseUrl` | `String` | 必填 |
| `enabled` | `Boolean` | 必填 |
| `tenantId` | `Long` | 必填 |
| `operatorId` | `Long` | 必填 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `assertBaseUrl`：以 `http://` 或 `https://` 开头，且不含 `StudyInstanceUIDs` |
| `repositoryChecks` | `enabled=true` 时，将同一 tenant + 同一 organizationId（空与空相等）+ 同一 modalityCode（空与空相等）的其他启用行 `disable` |
| `eventPublishAnchor` | 无 |
| `unitTestCaseId` | TC-IMG-005 |

**方法契约摘要**：失败码 `PACS_VIEWER_ENDPOINT_INVALID`；**注释锚点**：校验 URL → 停用同键旧行 → 插入或更新。

### 5.2 读侧

| 方法 | 入参 | 出参 | 关键实现 | `unitTestCaseId` |
| ---- | ---- | ---- | -------- | ---------------- |
| `ImagingStudyQueryService#findByWorkItemId` | `workItemId` | `Optional<ImagingStudyQueryModel>` | 仓储按工单+租户 | TC-IMG-R01 |
| `MediaQueryService#listByWorkItemId` | `workItemId` | `List<MediaQueryModel>` | `createdAt DESC` | TC-IMG-R02 |
| `MediaQueryService#countByWorkItemId` | `workItemId` | `long` | 软删后的行数 | TC-IMG-R03 |
| `ViewerEndpointQueryService#list` | `organizationId` 可空 | `List<ViewerEndpointQueryModel>` | 空机构列全局行 | TC-IMG-R04 |
| `ViewerEndpointQueryService#resolve` | `organizationId`、`modalityCode` 均可空 | `Optional<ViewerEndpointQueryModel>` | 启用行中按机构+模态、机构默认、全局默认取第一条 | TC-IMG-R05 |

**读方法注释锚点**：软删过滤 → 一次仓储读取 → 组装 QueryModel。`resolve` 的 `matchedBy` 在组装时写入。

---

## 6. 不变式映射（DM §7）

| BR 编号 | 实现策略摘要 |
| ------- | ------------ |
| **BR-IMG-01** | `pacs_imaging_study (tenant_id, work_item_id)` 唯一；ensure 命中则不插第二行 |
| **BR-IMG-02** | `(tenant_id, study_instance_uid)`、`(tenant_id, object_key)` 唯一 |
| **BR-IMG-03** | `saveKeyImage` 拒绝非 jpeg/png |
| **BR-IMG-04** | 领域类不引用 Orthanc/MinIO 客户端 |
| **BR-IMG-05** | `save` 在启用新行前停用同键旧启用行 |
| **BR-IMG-06** | `assertBaseUrl` 拒绝空串、非 http(s)、含 `StudyInstanceUIDs` |

---

## 7. 事件契约与触发规则（DM §5）

| 事件类名 | 事件类包路径 | 最小载荷 | 触发方法 |
| -------- | ------------ | -------- | -------- |
| 无 | — | — | 五个写方法均不发布 |

有图标志变化由应用层调用工单 `markHasMedia`，事件类仍是 `PacsWorkItemMediaFlagUpdatedEvent`，不在本模块生成。

---

## 8. 删除与可见性契约

- `deleteKeyImage` 与端点停用都走软删或 `enabled=false`，不物理删除。
- 查询默认 `isDeleted=false`。
- 停用端点保留行，供审计；`resolve` 只看 `enabled=true`。

---

## 9. 框架优先适配规则

- 遵循项目级 Java 成码门禁。
- 事务由调用方 AppService 的现有切面管理。
- 唯一索引与 §6 的 BR-IMG-01/02 同轮建表。
- 预签名 URL、STOW、C-STORE 不进入本模块。

---

## 10. 生成与验收清单

- [ ] 三个实体字段与 §4.2～§4.4 一致，含 `isDeleted`
- [ ] `MediaSource` 仅两个码
- [ ] 5 个 Command 文件与 §5 字段一致
- [ ] 5 个写方法 + 5 个读方法
- [ ] 无领域事件类
- [ ] Repository 无 `@Query`
- [ ] QueryModel 无预签名 URL、无患者姓名
- [ ] 领域代码不依赖 Orthanc/MinIO

---

## 11. 单次会话代码生成门禁（DoD）

### 11.1 前置输入

- 锁定 DM：`DM-PACS-003-影像索引与阅片端点.md` v1.0
- 本 SDD
- 目标模块：`zxplat-module-pacs-imaging/module-pacs-imaging-domain`（模块尚未创建，成码会话先建模块再生成类）

### 11.2 DoD

- 编译通过
- TC-IMG-001～005 与 R01～R05 有对应单测或在成码说明中列出尚未运行的用例名
- 不修改工单状态机

### 11.3 失败处理

- 唯一约束冲突且不是幂等命中：抛 `PACS_IMAGING_STUDY_BIND_FAILED`，不插入半行
- 命令缺必填：在领域服务入口拒绝，不写库

---

## 定稿检查清单（可打勾）

- [x] §5 写方法与 DM §6.1～§6.3 一一对应
- [x] §5.2 读方法与 DM §6.4 一一对应
- [x] 每条 BR 在 §6 有实现策略
- [x] §7 与 DM §5「无必须事件」一致
- [x] 无「待确认」措辞
- [x] 时间类型已改为 LocalDateTime 并在差异表声明

---

## 定稿评分（百分制）

| 维度 | 分值 | 自评 |
| ---- | ---- | ---- |
| 输入完整性 | 20 | 20 |
| 方法白名单对齐 | 25 | 25 |
| 不变式映射 | 20 | 20 |
| 事件契约 | 15 | 15 |
| 字段与命令全集 | 20 | 20 |
| **合计** | 100 | 100 |
