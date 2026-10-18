# AS-PEIS-CFG-001 体检目录 · 应用服务详细设计（SDD）

> **编码**：AS-PEIS-CFG-001-SDD · **版本**：v1.3 · **日期**：2026-07-19  
> **对应 AS**：AS-PEIS-CFG-001-体检目录.md（v1.0）  
> **术语**：「外发 LIS」= 金域（LisKingMed）；CFG 目录与 INT-01 本院 WorkItem 同 `activity_id` — 见 [`PEIS-LIS-PACS文档口径`](../../../../.cursor/skills/PEIS-LIS-PACS文档口径/SKILL.md)

---

## 1. 实现边界

| 项 | 内容 |
|----|------|
| **模块** | `health-application/peis/config/catalog` |
| **边界** | 类型/小项 OD/项目 AD/套餐 AD；项目↔小项 link（**不含执行科室维护**，见基础 `DepartmentAppService`） |
| **方法权威** | DM-CLN-005 §3、DM-WKF-001、**DM-CUS-013** |
| **实现阶段口径** | **目标态全量契约**（P0） |

---

## 2. 代码结构

### 2.1 包路径

```
health-application/.../peis/config/catalog/
├── PeisCatalogConfigAppService.java
├── request/ （SearchCatalogRequest、SaveObservationDefRequest、SaveActivityRequest、SavePackageRequest）
├── response/
└── mapper/PeisCatalogMapper.java + PeisCatalogMapperImpl.java
```

| 依赖注入 | 类型 | 用途 |
|----------|------|------|
| `ObservationDefinitionDomainService` | 领域 | CFG-03 |
| `ActivityDefinitionDomainService` | 领域 | CFG-04/05 |
| `ActivityObservationLinkDomainService` | 领域 | CFG-04 |
| （无） | — | **CFG-01 已撤销**；科室主数据不注入本 AS |

### 2.2 领域方法白名单

| 服务 | 方法 | IF |
|------|------|-----|
| ObservationDefinitionDomainService | `save(SaveObservationDefinitionCommand)` | CFG-03 |
| ActivityDefinitionDomainService | `save(SaveActivityDefinitionCommand)` | CFG-04/05 |
| ActivityObservationLinkDomainService | `saveLinks(SaveActivityObservationLinksCommand)` | CFG-04 |
| ObservationDefinitionQueryService | `search(SearchObservationDefQuery)` | CFG-03 |
| ActivityDefinitionQueryService | `searchPackageCatalog(...)` / `searchActivityCatalog(...)` | CFG-04/05 读 |

### 2.3 枚举、字典与值集

| 类别 | 代码实现锚点 | DM 追溯锚点 | 说明 |
|------|-------------|-------------|------|
| AD type_enum | `SaveActivityRequest.typeEnum` | DM-WKF-001 | PROOF/TEST/OTHER |
| OD categoryCode | `SaveObservationDefRequest.categoryCode` | DM-CLN-005 §3 | exam_item / dept_conclusion |

---

## 3. 按接口 ID 命名展开

### 3.1 应用服务接口

| IF | 方法 | 中文 | HTTP |
|----|------|------|------|
| ~~CFG-01~~ | ~~`searchDept` / `saveDept`~~ | ~~执行科室~~ | **已撤销** |
| ~~CFG-02~~ | ~~`searchProjectType` / `saveProjectType`~~ | ~~项目类型~~ | **已撤销**（枚举随 CFG-04） |
| CFG-03 | `searchObservationDef` | 查询小项 | `POST /api/app/peisCatalogConfig/searchObservationDef` |
| CFG-03 | `saveObservationDef` | 保存小项 | `POST /api/app/peisCatalogConfig/saveObservationDef` |
| CFG-04 | `searchActivity` | 查询体检项目 | `POST /api/app/peisCatalogConfig/searchActivity` |
| CFG-04 | `saveActivity` | 保存项目 | `POST /api/app/peisCatalogConfig/saveActivity` |
| CFG-05 | `searchPackage` | 查询套餐 | `POST /api/app/peisCatalogConfig/searchPackage` |
| CFG-05 | `savePackage` | 保存套餐 | `POST /api/app/peisCatalogConfig/savePackage` |

### 3.2 IF-PEIS-CFG-01（已撤销）

执行科室不再由 `PeisCatalogConfigAppService` 维护；统一使用基础数据科室管理（`DepartmentAppService` / `adm_organization`）。项目/小项上的 `orgId` 绑定可后续用科室查询只读下拉完成，不在本页建档。

---

### 3.3 IF-PEIS-CFG-02（已撤销）

项目类型不再单独维护；`SaveActivityRequest.typeEnum` 直接使用 `ActivityType` 持久化码（检验 `1` → INT-01 LIS；检查 `2` → PACS）。

---

### 3.4 IF-PEIS-CFG-03 `searchObservationDef` / `saveObservationDef`（体检小项 OD）

#### saveObservationDef · Request

| 字段 | 类型 | 必填 | 说明 |
|------|------|:--:|------|
| `definitionId` | `Long` | 否 | |
| `code` | `String` | 是 | |
| `display` | `String` | 是 | |
| `valueTypeEnum` | `String` | 是 | quantity / string / codeable / boolean |
| `categoryCode` | `String` | 是 | exam_item / dept_conclusion / … |
| `unitCode` | `String` | 否 | |
| `statusEnum` | `String` | 是 | active / inactive |

#### 领域编排序列（saveObservationDef）

| 序号 | 阶段 | 调用 | 说明 |
|------|------|------|------|
| 1 | Gate | 参数门禁 | BR-CFG-01 |
| 2 | Pre | 在检 OD 引用检查 | BR-CFG-05 |
| 3 | Cmd | `ObservationDefinitionDomainService#save(SaveObservationDefinitionCommand)` | **写库点** `cln_observation_definition` |
| 4 | Asm | | |

#### 失败码

| 码 | 场景 |
|----|------|
| `CATALOG_IN_USE` | 在检不可停用 |
| `OD_CODE_DUPLICATE` | 小项码重复 |

---

### 3.5 IF-PEIS-CFG-04 `searchActivity` / `saveActivity`（体检项目 AD + 小项链接）

#### Request（节选）

| 字段 | 类型 | 必填 |
|------|------|:--:|
| `activityId` | `Long` | 否 |
| `code` | `String` | 是 |
| `display` | `String` | 是 |
| `typeEnum` | `String` | 是 |
| `observationDefinitionIds` | `List<Long>` | 否 |

#### 领域编排序列

| 序号 | 阶段 | 调用 | 说明 |
|------|------|------|------|
| 1 | Pre | 在检引用检查 | BR-CFG-05 |
| 2 | Cmd | `ActivityDefinitionDomainService#save(...)` | **写库点 1** wkf_activity_definition |
| 3 | Cmd | `ActivityObservationLinkDomainService#saveLinks(...)` | **写库点 2** cus_activity_observation_link |

#### 失败码

| 码 | 场景 |
|----|------|
| `CATALOG_IN_USE` | 在检不可停用 |

---

### 3.6 IF-PEIS-CFG-05 `searchPackage` / `savePackage`（体检套餐）

#### savePackage · Request

| 字段 | 类型 | 必填 | 说明 |
|------|------|:--:|------|
| `packageActivityId` | `Long` | 否 | |
| `code` | `String` | 是 | |
| `display` | `String` | 是 | |
| `childActivityIds` | `List<Long>` | 是 | 子项目 AD，须 active |
| `statusEnum` | `String` | 是 | |

#### 领域编排序列（savePackage）

| 序号 | 阶段 | 调用 | 说明 |
|------|------|------|------|
| 1 | Gate | 参数门禁 | childActivityIds 非空（BR-CFG-03） |
| 2 | Pre | `ActivityDefinitionQueryService#listByIds(childActivityIds)` | 子项均 active |
| 3 | Pre | 在检套餐引用检查 | BR-CFG-05 |
| 4 | Cmd | `ActivityDefinitionDomainService#save(SavePackageCommand)` | **写库点** 套餐 AD + `wkf_activity_definition_children` |
| 5 | Asm | | |

#### 失败码

| 码 | 场景 |
|----|------|
| `CHILDREN_EMPTY` | 套餐无子项 |
| `CHILD_INACTIVE` | 子项目停用 |
| `CATALOG_IN_USE` | 在检不可改套餐 |

---

## 4. QueryService 与领域事件

- CFG 列表读走 `ActivityDefinitionQueryService` / OD QueryService；保存后 REG/DES 读侧 eventual 刷新。

---

## 5. 写库顺序

| IF | Cmd 序号 | 领域方法 | 聚合写入顺序 |
|----|----------|----------|--------------|
| ~~CFG-01 saveDept~~ | — | **已撤销** | 科室主数据改走 `DepartmentAppService` |
| ~~CFG-02 saveProjectType~~ | — | **已撤销** | 类型随 CFG-04 `typeEnum` |
| CFG-03 saveObservationDef | 3 | `ObservationDefinitionDomainService#save` | cln_observation_definition |
| CFG-04 saveActivity | 2～3 | save + saveLinks | wkf_activity_definition → cus_activity_observation_link |
| CFG-05 savePackage | 4 | `ActivityDefinitionDomainService#save` | 套餐 AD + children |

**事务边界**：CFG-04 步骤 2～3 同一事务；CFG-05 步骤 4 单事务。

---

## 6. 非功能性需求实现

### 6.1 幂等性

- save 按 code 唯一 upsert。

### 6.2 并发控制

- AD/OD 乐观锁 version。

### 6.3 重试与容错

- 无外部依赖 P0。

### 6.4 数据脱敏

- 无 P0。

### 6.5 审计日志

- saveObservationDef/saveActivity/savePackage 记 AuditLog。

---

## 7. 测试要点

### 7.1 功能测试

| 场景 | 层级 | 自动化 | 优先级 |
|------|------|--------|:--:|
| ~~CFG-01 科室 CRUD~~ | — | — | 已撤销 |
| ~~CFG-02 类型字典~~ | — | — | 已撤销 |
| CFG-03 dept_conclusion OD | DES-04 可选 | API | P0 |
| 套餐展开 | REG SR 数 | 集成 | P0 |
| 在检停用 | CATALOG_IN_USE | API | P0 |
| PROOF 项目 INT-01 | LIS WorkItem | 集成 | P0 |

### 7.2 非功能测试

| 场景 | 要点 | 优先级 |
|------|------|:--:|
| 大套餐 | 100+ 子项保存 | P1 |

### 7.3 查询结果一致性

- searchPackageCatalog 与 REG 登记套餐一致。

### 7.4 集成测试

- CFG-05 套餐 → REG-02 createRegistration。

### 7.5 测试缺口跟踪

| 缺口 | 无 P0 |

---

## 8. 变更历史

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.3 | 2026-07-19 | 撤销 CFG-02 项目类型 Tab/接口 |
| v1.2 | 2026-07-19 | 撤销 CFG-01 执行科室接口与依赖注入 |
| v1.1 | 2026-06-27 | 全 IF CFG-01～05 展开；§5 写库矩阵；链 PEIS 术语 skill |
| v1.0 | 2026-06-27 | 首版 |
| v1.0-patch | 2026-06-27 | 补 §4～§8；修正 HTTP serviceName |
