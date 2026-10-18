# AS-LIS-CFG-004 合管与试管方案 · 应用服务详细设计（SDD）

> **编码**：AS-LIS-CFG-004-SDD · **版本**：v2.0 · **日期**：2026-06-22
> **对应 AS**：AS-LIS-CFG-004-合管与试管方案.md（v2.0）
>
> **DOC-CONFLICT**：本 AS 对应 DM 缺失 — `lis_merge_profile` 与 `lis_tube_scheme` 无独立 DM 文档；SDD 方法契约以 AS §1.5/§4 为准，领域层按配置表模式实现。成码前需评估是否补齐 DM。

---

## 1. 实现边界

| 项 | 内容 |
|----|------|
| **模块** | `zxplat/health-application` |
| **边界** | 合管方案 CRUD；试管方案 CRUD；方案与仪器能力关联 |
| **编排权威** | AS-LIS-CFG-004 §2、§4 |
| **方法权威** | 无对应 DM（配置表聚合）；以 AS §1.6 BR 为准 |
| **实现阶段口径** | **目标态全量契约**（P0） |

### 术语口径

| 项 | 约定 |
|----|------|
| **事务口径** | AppService **不**声明 `@Transactional`；写/读事务分别由 `AppServiceAspect`、`ReadOnlyTransactionAspect` 管理 |
| **合管方案** | 定义哪些检验项目可合并到同一管血，按 priority 排序 |

---

## 2. 代码结构

### 2.1 包路径

```
health-application/src/main/java/com/openhis/health/application/lis/mergeprofile/
├── LisMergeProfileAppService.java
├── command/
│   ├── SaveMergeProfileCommand.java
│   ├── SaveTubeSchemeCommand.java
│   └── LinkCapabilityCommand.java
├── request/
│   ├── MergeProfileSearchRequest.java
│   └── TubeSchemeSearchRequest.java
├── response/
│   ├── MergeProfileSearchResponse.java
│   └── TubeSchemeSearchResponse.java
└── assembler/
    └── MergeProfileAssembler.java
```

| 依赖注入 | 类型 | 用途 |
|----------|------|------|
| `MergeProfileDomainService` | 领域 | saveProfile / saveTubeScheme / linkCapability |
| `MergeProfileQueryService` | 查询 | searchProfiles / searchTubeSchemes |

### 2.2 领域方法白名单

| 聚合 | 方法 | 用于 IF |
|------|------|---------|
| MergeProfile | `saveProfile(SaveMergeProfileCommand)` | MT-01 |
| TubeScheme | `saveTubeScheme(SaveTubeSchemeCommand)` | MT-02 |
| DeviceCapability | `linkCapability(LinkCapabilityCommand)` | MT-03 |
| MergeProfileQueryService | `searchProfiles(MergeProfileQuery, Pageable)` | MT-01 |
| TubeSchemeQueryService | `searchTubeSchemes(TubeSchemeQuery, Pageable)` | MT-02 |

### 2.3 枚举、字典与值集（DM 与代码可追溯）

| 类别 | 代码实现锚点 | DM 追溯锚点 | 说明 |
|------|-------------|------------|------|
| `enableFlag` | `MergeProfileSearchRequest.enableFlag` | AS-LIS-CFG-004 §1.6 BR-CFG-MT-01 | Boolean；仅 enableFlag=true 的项目属于 1 个 profile |
| 试管方案属性 | `SaveTubeSchemeRequest` 字段 | AS-LIS-CFG-004 §1.2 | 颜色/管帽/材质/容量 |

---

## 3. 按接口 ID 命名展开

### 3.1 应用服务接口（方法白名单）

**HTTP 路径生成规则**：`POST /api/app/{serviceName}/{methodName}`，serviceName = `LisMergeProfileAppService` → `lisMergeProfile`

| IF | 应用服务方法（英文） | 中文方法名 | HTTP |
|----|---------------------|-----------|------|
| IF-MT-01 | `saveProfile` | 合管方案 CRUD | `POST /api/app/lisMergeProfile/saveProfile` |
| IF-MT-02 | `saveTubeScheme` | 试管方案 CRUD | `POST /api/app/lisMergeProfile/saveTubeScheme` |
| IF-MT-03 | `linkCapability` | 关联能力 | `POST /api/app/lisMergeProfile/linkCapability` |

### 3.1.1 编排约定

| 阶段 | 含义 | 失败时 |
|------|------|--------|
| **Gate** | 权限 / 参数门禁 | 不再调用 |
| **Pre** | 写前只读：BR 校验 | 抛业务异常 |
| **Cmd** | 领域写 | 事务回滚 |
| **Asm** | DTO 装配 / 事件发布 | — |

---

### 3.2 IF-MT-01 `saveProfile`（合管方案保存）

#### 领域编排序列

| 序号 | 阶段 | 调用 | 说明 |
|------|------|------|------|
| 1 | **Gate** | 必填字段门禁 | 方案名称非空 |
| 2 | **Pre** | `MergeProfileQueryService#searchProfiles` | BR-CFG-MT-01：校验同一 enableFlag=true 的项目仅属 1 个 profile |
| 3 | **Cmd** | `MergeProfileDomainService#saveProfile(SaveMergeProfileCommand)` | **写库点** |
| 4 | **Asm** | 事件发布 | `MergeProfileChangedEvent` → 合管分组重算 |

#### 失败码

| 失败码 | 场景 |
|--------|------|
| `PROFILE_ITEM_DUPLICATE` | 项目重复绑定 |

---

### 3.3 IF-MT-02 `saveTubeScheme`（试管方案保存）

| 序号 | 阶段 | 调用 | 说明 |
|------|------|------|------|
| 1 | **Gate** | 必填字段门禁 | |
| 2 | **Cmd** | `TubeSchemeDomainService#saveTubeScheme(SaveTubeSchemeCommand)` | **写库点** |

---

### 3.4 IF-MT-03 `linkCapability`（关联能力）

| 序号 | 阶段 | 调用 | 说明 |
|------|------|------|------|
| 1 | **Gate** | 参数门禁 | |
| 2 | **Pre** | BR-CFG-MT-02：禁用前校验无活跃 DeviceCapability 关联 | |
| 3 | **Cmd** | `MergeProfileDomainService#linkCapability(LinkCapabilityCommand)` | **写库点** |

---

## 4. QueryService 与领域事件

- **领域事件**：`MergeProfileChangedEvent` 由 AS 层发布，触发合管分组重算。

---

## 5. 写库顺序

| IF | Cmd | 写入 |
|----|-----|------|
| MT-01 | `MergeProfileDomainService#saveProfile` | lis_merge_profile |
| MT-02 | `TubeSchemeDomainService#saveTubeScheme` | lis_tube_scheme |
| MT-03 | `MergeProfileDomainService#linkCapability` | lis_device_capability |

**事务边界**：单 IF 内同一事务；由 `AppServiceAspect` 管理。

---

## 6. 非功能性需求实现

无特殊。

---

## 7. 测试要点

| 场景 | 测试要点 | 优先级 |
|------|----------|:--:|
| 正向 | 保存合管方案并关联项目 | P0 |
| 异常 | 同一项目绑定两个 enableFlag=true 的 profile | P0 |

---

## 8. 变更历史

| 版本 | 日期 | 说明 |
|------|------|------|
| v2.0 | 2026-06-22 | 重新生成：修正事务口径；新增 DOC-CONFLICT 标注（无对应 DM）；补充 §2.3 |
| v1.0 | 2026-06-22 | 首版 |
