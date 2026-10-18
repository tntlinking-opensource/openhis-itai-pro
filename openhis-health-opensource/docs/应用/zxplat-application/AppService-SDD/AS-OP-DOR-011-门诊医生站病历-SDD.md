# 门诊医生站 · 病历 · 应用服务详细设计（SDD）

> **编码**：AS-OP-DOR-011-SDD  
> **版本**：v1.0 | **日期**：2026-07-19  
> **对应 AS**：`AS-OP-DOR-011-门诊医生站病历.md`（v1.0）

---

## 1. 实现边界

### 1.1 目标与边界

| 项 | 内容 |
| --- | --- |
| **模块** | 门诊医生站 · 病历；HTTP 落在 **EMR** `DocumentAppService` / `TemplateAppService`；带入读复用 **health** 诊断/医嘱 AppService |
| **边界** | 模板树/正文、文书列表/打开/创建/保存、存为个人模板；诊断/医嘱主动带入（读列表 + **前端**插入光标）；变更提醒纯前端 |
| **编排权威** | `AS-OP-DOR-011` §2、§4；带入文案 `docs/模块/zxplat-module-emr/06-诊断与医嘱主动带入文书.md`；EMR 契约见同目录 DomainModel/AppService |
| **方法权威** | EMR `DocumentManager` / 模板域方法；诊断读 **AS-OP-DOR-002-SDD** IF-DOR-06；医嘱读 **AS-OP-DOR-003-SDD** IF-DOR-10 |
| **严格对齐** | IF-OP-MR-01～09 与 AS 一致，不得增删改号 |
| **实现阶段口径** | **混合**：IF-OP-MR-01～07 为 **当前已上线范围**（`document`/`template`）；IF-OP-MR-08/09 读侧已上线、**前端带入 UI 为目标态待接线** |

**Out of scope（同 AS §1.4）**

| 类别 | 内容 |
| --- | --- |
| 接诊/完诊 | AS-OP-DOR-001 |
| 诊断/医嘱写 | AS-OP-DOR-002/003 |
| 打印/合并 PDF | 本期病历 Tab 未暴露 |
| 入院结构化回写 | 非门诊；非主动带入 |

### 1.2 术语口径

| 项 | 约定 |
| --- | --- |
| **字段口径** | 文书/模板主键雪花 **String** 入参；`status` 为 EMR 文书状态字面值 |
| **QueryService口径** | 文书列表/打开走 EMR `DocumentManager` 读；带入诊断/医嘱走 health 既有列表 IF，本 SDD 不重定义其装配 |
| **事务口径** | EMR 写方法在 `DocumentManager`/`Template` 域内事务；应用层不另声明跨模块事务；带入无服务端写事务 |
| **HTTP 接入口径** | 一律 `POST`：`POST /api/app/{serviceName}/{methodName}`；`DocumentAppService`→`document`；`TemplateAppService`→`template` |
| **新建会话** | 先 `template/getContent`，首次保存再 `document/create`（可带当前编辑 content 由前端先 create 再 save，以实现为准：当前实现 create 用模板初始化，再 save 覆盖） |

#### 1.2.1 文档语义码对照

| 文档语义码（AS §5） | 实现口径 | 场景 |
| --- | --- | --- |
| EX-OP-MR-101 | 基础设施/远程失败 | 列表加载失败 |
| EX-OP-MR-102 | 前端 Gate / 业务提示 | 未接诊 |
| EX-OP-MR-103 | `ValidationException` / 空模板 | 模板无效 |
| EX-OP-MR-104 | `EntityNotFoundException` | 文书不存在 |
| EX-OP-MR-105 | `ValidationException` | 创建/存模板失败 |
| EX-OP-MR-106 | `readOnly` / 前端禁用 | 只读 |
| EX-OP-MR-107 | `ValidationException`「文书已锁定或归档」 | 锁定/归档保存 |
| EX-OP-MR-108 | 前端 Gate | 无医务人员 |
| EX-OP-MR-109 | 前端 Gate | 带入无光标 |

---

## 2. 代码结构

### 2.1 包路径（类级清单）

```text
zxplat-plugin/zxplat-module-emr/module-emr-application/src/main/java/com/zxplat/plugin/emr/application/
  document/
    service/DocumentAppService.java
    request/CreateDocumentRequest.java
    request/GetDocumentContentRequest.java
    request/QueryDocumentRequest.java
    request/SaveDocumentRequest.java
    response/DocumentContentDto.java
    response/DocumentSummaryDto.java
    response/SaveDocumentResponse.java   # 若工程以 DocumentSummaryDto/SaveDocumentResult 为准则对齐实现
  template/
    service/TemplateAppService.java
    request/ListTemplatesForStationRequest.java
    request/GetTemplateContentRequest.java
    request/CreateTemplateRequest.java
    response/TemplateDto.java

# 带入读侧（复用，不新建类）
# 带入读侧：宿主应用内门诊医生站 AppService（Wave4）
# DoctorStationDiagnosisAppService#getEncounterDiagnosis
# DoctorStationAdviceAppService#listDoctorStationAdvice

# 前端（目标态带入）
# 前端（目标态带入）：apps/web-ele … medical-record-form / SimpleEditor
```

> HTTP：`document` / `template` / `doctorStationDiagnosis` / `doctorStationAdvice`  
> EMR 契约真源：`docs/模块/zxplat-module-emr/`（DM-EMR-001/002、AS-EMR-DOC/TPL）。
> 本模块 **无** 独立 `DoctorStationMedicalRecordAppService`；医生站病历 Tab 直接编排上述服务。

### 2.2 依赖注入表

| 依赖 | 用途 |
| --- | --- |
| `DocumentManager` | create / saveContent / findPage / findById（经 DocumentAppService） |
| `Template` 域服务（TemplateAppService 内） | listForStation / getContent / createTemplate |
| `EmrAdmissionContextLoader`（可选） | getContent 元数据 `metadataValues` |
| `DoctorStationDiagnosisAppService` | IF-OP-MR-08 复用 `getEncounterDiagnosis` |
| `DoctorStationAdviceAppService` | IF-OP-MR-09 复用 `listDoctorStationAdvice` |
| 前端编辑器 | IF-OP-MR-08/09 `insertContent`；变更提醒会话态 |

### 2.2.1 领域方法白名单

| 类型 | 方法 | 用于 IF |
| --- | --- | --- |
| `TemplateAppService` | `listForStation` | IF-OP-MR-01 |
| `TemplateAppService` | `getContent` | IF-OP-MR-02 |
| `DocumentAppService` | `getList` | IF-OP-MR-03 |
| `DocumentAppService` | `getContent` | IF-OP-MR-04 |
| `DocumentManager` | `create` | IF-OP-MR-05 Cmd |
| `DocumentAppService` | `create` | IF-OP-MR-05 |
| `DocumentManager` | `saveContent` | IF-OP-MR-06 Cmd |
| `DocumentAppService` | `save` | IF-OP-MR-06 |
| `TemplateAppService` | `create` / `createTemplate` | IF-OP-MR-07 |
| `DoctorStationDiagnosisAppService` | `getEncounterDiagnosis` | IF-OP-MR-08 Pre/Query |
| `DoctorStationAdviceAppService` | `listDoctorStationAdvice` | IF-OP-MR-09 Pre/Query |
| `DocumentManager` | `findPage` | IF-OP-MR-03（经 getList） |
| `DocumentManager` | `findById` | IF-OP-MR-04/06 Pre |

### 2.3 枚举、字典与值集（DM 与代码可追溯）

| 类别 | 代码实现锚点 | DM 追溯锚点 | 说明 |
| --- | --- | --- | --- |
| 文书状态 | `DocumentContentDto.status`；`DocumentManager.STATUS_DRAFT` / `STATUS_LOCKED` / `STATUS_ARCHIVED` / `STATUS_SUBMITTED` | `docs/模块/zxplat-module-emr/00-功能清单.md` 文书状态；`DocumentManager` 不变式 | IF-OP-MR-04/06；锁定/归档不可改 |
| 站别 | `ListTemplatesForStationRequest.stationKind`（`OUTPATIENT`） | `docs/模块/zxplat-module-emr/00-功能清单.md` 功能 5.3 适用就诊 | IF-OP-MR-01 |
| 模板范围 | `CreateTemplateRequest.scopeLevel`（个人等） | `docs/模块/zxplat-module-emr/00-功能清单.md` 功能 5.3 | IF-OP-MR-07 |
| 适用就诊类 | `CreateTemplateRequest.encounterClassCode`（门诊） | 同上 | IF-OP-MR-07 |
| 首次病程模板码 | `DocumentManager.TEMPLATE_CODE_FIRST_PROGRESS`（`PN_FIRST`） | `docs/模块/zxplat-module-emr/00-功能清单.md` 13.7 | 门诊少用；与住院共享域 |
| 就诊标识 | `CreateDocumentRequest.encounterId` / `patientId` | `DM-ADM-002` §3 就诊主键语义 | IF-OP-MR-05 Gate |
| 诊断中西医 | `EncounterDiagnosisRowDto.diagnosisKind` | `DM-CLN-002` / AS-OP-DOR-002 | IF-OP-MR-08 文案 |
| 主诊标记 | `EncounterDiagnosisRowDto.mainDiagnosisFlag` | `DM-ADM-002` 就诊诊断关联 | IF-OP-MR-08 |
| 医嘱行展示 | `DoctorStationAdviceRowDto.adviceDisplayName` 等 | `AS-OP-DOR-003` / DM-MED-003 列表投影 | IF-OP-MR-09 |

---

## 3. 按接口 ID 命名展开

> **先读完各 IF「领域编排序列」**，按序号执行；**Gate/Pre 失败不得进入 Cmd**。

### 3.1 应用服务接口（方法白名单）

| IF | 应用服务方法（英文） | 中文方法名 | HTTP |
| --- | --- | --- | --- |
| IF-OP-MR-01 | `listForStation` | 加载门诊站别模板树 | `POST /api/app/template/listForStation` |
| IF-OP-MR-02 | `getContent` | 载入模板正文 | `POST /api/app/template/getContent` |
| IF-OP-MR-03 | `getList` | 查询历史文书列表 | `POST /api/app/document/getList` |
| IF-OP-MR-04 | `getContent` | 打开文书内容 | `POST /api/app/document/getContent` |
| IF-OP-MR-05 | `create` | 创建文书 | `POST /api/app/document/create` |
| IF-OP-MR-06 | `save` | 保存文书内容 | `POST /api/app/document/save` |
| IF-OP-MR-07 | `create` | 存为个人模板 | `POST /api/app/template/create` |
| IF-OP-MR-08 | `getEncounterDiagnosis`（复用） | 从诊断带入（读列表） | `POST /api/app/doctorStationDiagnosis/getEncounterDiagnosis` |
| IF-OP-MR-09 | `listDoctorStationAdvice`（复用） | 从医嘱带入（读列表） | `POST /api/app/doctorStationAdvice/listDoctorStationAdvice` |

### 3.1.1 编排约定（应用层如何实现 AS §4）

| 阶段 | 含义 | 失败时 |
| --- | --- | --- |
| **Gate** | 参数/接诊/只读/光标门禁（含前端） | 不再后续 |
| **Pre** | 写前只读加载、状态校验 | 禁止 Cmd |
| **Cmd** | `DocumentManager` / 模板创建写库 | 事务回滚见 §5 |
| **Asm** | DTO 装配或前端文案格式化 | — |

记法：`DocumentManager#create` 表示对注入 Bean 调用。

---

### 3.2 IF-OP-MR-01 `listForStation`（加载门诊站别模板树）

| 项 | 值 |
| --- | --- |
| **App** | `TemplateAppService#listForStation` |
| **HTTP** | `POST /api/app/template/listForStation` |

**Request：`ListTemplatesForStationRequest`**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| stationKind | String | 是 | 固定 `OUTPATIENT` |
| documentTypeCode | String | 否 | 按类型过滤 |
| enabled | Boolean | 否 | 建议 `true` |

**Response：`List<TemplateDto>`**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | String/Long | 模板主键 |
| name | String | 名称 |
| code | String | 编码 |
| documentTypeCode | String | 文书类型 |
| documentTypeName | String | 类型名 |
| scopeLevel | String | 个人/科室/全院 |
| encounterClassCode | String | 适用就诊 |
| isEnabled / enabled | Boolean | 启用 |

**关键校验**：`stationKind` 非空。

**领域编排序列**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | — | stationKind=OUTPATIENT | 非法则 EX-OP-MR-101 |
| 2 | Query | `TemplateAppService#listForStation` | Request | 仅读路径；按登录用户可见范围过滤 |
| 3 | Asm | 前端组树 | TemplateDto 列表 | 无 Cmd |

---

### 3.3 IF-OP-MR-02 `getContent`（载入模板正文）

| 项 | 值 |
| --- | --- |
| **App** | `TemplateAppService#getContent` |
| **HTTP** | `POST /api/app/template/getContent` |

**Request：`GetTemplateContentRequest`**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| templateId | String | 是 | 雪花字符串 |

**Response：`TemplateDto`（含 content）**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | String | 模板 ID |
| documentTypeCode | String | 文书类型（供后续 create） |
| content | String | 模板 JSON 正文 |
| name / code | String | 展示 |

**关键校验**：前端须已有 `encounterId`（**BR-OP-MR-001**），否则 EX-OP-MR-102。

**领域编排序列**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | 前端 | encounterId 非空 | 否则 EX-OP-MR-102，不调后端 |
| 2 | Gate | — | templateId 非空 | 否则 EX-OP-MR-103 |
| 3 | Query | `TemplateAppService#getContent` | templateId | 仅读；载入新建会话，**不** create |
| 4 | Asm | 前端 setContent | content | 本地会话 |

---

### 3.4 IF-OP-MR-03 `getList`（查询历史文书列表）

| 项 | 值 |
| --- | --- |
| **App** | `DocumentAppService#getList` |
| **HTTP** | `POST /api/app/document/getList` |

**Request：`QueryDocumentRequest`**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| patientId | String | 是 | 患者 |
| encounterId | String | 否 | 就诊收窄 |
| encounterIds | `List<String>` | 否 | 显式多就诊过滤；与 encounterId 取并集 |
| includeLinkedEncounters | Boolean | 否 | `true` 且有 encounterId 时，按 `amb_encounter_id` 展开当前∪关联对端再查 |
| documentTypeCode | String | 否 | 类型 |
| status | String | 否 | 状态 |
| pageNo / pageSize | Integer | 是 | 分页（前端历史区可用大 pageSize） |

**Response：分页 `DocumentSummaryDto`**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | String | 文书 ID |
| patientId / encounterId | String | 上下文 |
| documentTypeCode / documentTypeName | String | 类型 |
| title | String | 标题 |
| status | String | 状态 |
| createdAt / updatedAt | String | 时间 |
| sourceTemplateId | String | 来源模板 |
| createdBy | String | 创建者（只读判定） |

**领域编排序列**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | — | patientId | 空则空列表 |
| 2 | Query | `DocumentManager#findPage`（经 `DocumentAppService#getList`） | 筛选条件；`includeLinkedEncounters` 时经 `EmrLinkedEncounterResolver` 展开就诊并集 | 仅读路径 |
| 3 | Asm | 映射 DocumentSummaryDto | 页数据 | — |

---

### 3.5 IF-OP-MR-04 `getContent`（打开文书内容）

| 项 | 值 |
| --- | --- |
| **App** | `DocumentAppService#getContent` |
| **HTTP** | `POST /api/app/document/getContent` |

**Request：`GetDocumentContentRequest`**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| documentId | String | 条件 | 与 patient+encounter+type 二选一路径 |
| patientId | String | 条件 | 取最新时用 |
| encounterId | String | 条件 | 取最新时用 |
| documentTypeCode | String | 条件 | 取最新时用 |

**Response：`DocumentContentDto`**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | String | 文书 ID |
| patientId / encounterId | String | 上下文 |
| documentTypeCode | String | 类型 |
| title | String | 标题 |
| status | String | 状态 |
| content | String | 正文 JSON |
| readOnly | boolean | 只读：锁定/归档；或非病程/非会诊且非创建者（**BR-OP-MR-005**） |
| metadataValues | Map | 元数据芯片（与主动带入解耦） |

**失败码**：不存在 → EX-OP-MR-104。

**领域编排序列**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | — | 解析 documentId 或三元组 | 参数不足失败 |
| 2 | Pre | `DocumentManager#findById`（或按患者就诊类型取最新） | id | 无则 EX-OP-MR-104 |
| 3 | Asm | 装配 DocumentContentDto + readOnly + metadataValues | 文档实体 | 仅读；**BR-OP-MR-005** 只读标记 |

---

### 3.6 IF-OP-MR-05 `create`（创建文书）

| 项 | 值 |
| --- | --- |
| **App** | `DocumentAppService#create` |
| **HTTP** | `POST /api/app/document/create` |

**Request：`CreateDocumentRequest`**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| patientId | String | 是 | 患者 |
| encounterId | String | 是 | 就诊（门诊须已接诊） |
| documentTypeCode | String | 是 | 文书类型 |
| templateId | String | 否 | 来源模板 |

**Response：`DocumentContentDto`**（同 §3.5 字段表）

**关键校验**：前端 encounterId；后端模板存在性；若 `PN_FIRST` 同就诊已存在则失败（域规则，门诊少见）。

**失败码**：EX-OP-MR-102 / 105；首次病程冲突同住院 EX 语义。

**领域编排序列**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | 前端 | encounterId、patientId、documentTypeCode | 缺省 EX-OP-MR-102 |
| 2 | Pre | 解析 templateId | 可选 | 无效模板 EX-OP-MR-103 |
| 3 | Cmd | `DocumentManager#create`（经 AppService） | patient/encounter/type/templateId | **写库点**：插入 `emr_document`，status=DRAFT；若 PN_FIRST 且已存在 → 抛校验，不写 |
| 4 | Asm | DocumentContentDto | 新文书 | 返回 id 供后续 save |

---

### 3.7 IF-OP-MR-06 `save`（保存文书内容）

| 项 | 值 |
| --- | --- |
| **App** | `DocumentAppService#save` |
| **HTTP** | `POST /api/app/document/save` |

**Request：`SaveDocumentRequest`**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| documentId | String | 是 | 文书 |
| content | String | 是 | 正文 JSON 字符串 |
| saveVersion | Boolean | 否 | 是否打版本 |

**Response**：保存结果（含 `documentId`；实现可为 `DocumentSummaryDto` / `SaveDocumentResponse`）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| documentId / id | String | 文书 ID |
| status | String | 状态 |
| newVersionNumber | Integer | 可选版本号 |

**关键校验**：非只读；非 LOCKED/ARCHIVED；非病程/非会诊须创建者可写（**BR-OP-MR-004/005**）。

**失败码**：EX-OP-MR-106 / 107。

**领域编排序列**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | 前端 / App | documentId、content；readOnly=false | 只读 EX-OP-MR-106 |
| 2 | Pre | `DocumentManager#findById` | documentId | 无则 EX-OP-MR-104 |
| 3 | Pre | 状态与创建者校验 | status、createdBy vs 当前用户；病程/会诊豁免创建者限制 | LOCKED/ARCHIVED → EX-OP-MR-107；非病程/非会诊且非创建者 → EX-OP-MR-106 |
| 4 | Cmd | `DocumentManager#saveContent` | documentId, content, saveVersion | **写库点**：更新 content；可选版本表 |
| 5 | Asm | 保存结果 DTO | — | — |

---

### 3.8 IF-OP-MR-07 `create`（存为个人模板）

| 项 | 值 |
| --- | --- |
| **App** | `TemplateAppService#create`（医生站 `fromDoctorStation=true`） |
| **HTTP** | `POST /api/app/template/create` |

**Request：`CreateTemplateRequest`（医生站子集）**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| name | String | 是 | 模板名 |
| code | String | 是 | 编码（前端可生成） |
| documentTypeCode | String | 是 | 当前文书类型 |
| scopeLevel | String | 是 | 个人 |
| encounterClassCode | String | 是 | 门诊适用码 |
| ownerPractitionerId | String | 是 | 当前医务人员 |
| fromDoctorStation | Boolean | 是 | `true`，强制个人 |
| content | String | 是 | 当前正文 |
| enabled | Boolean | 否 | 默认 true |

**Response：`TemplateDto`**（同 §3.2 字段 + content 可选）

**关键校验**：ownerPractitionerId 非空（EX-OP-MR-108）。

**领域编排序列**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | 前端 | 医务人员、正文、文书类型 | EX-OP-MR-108 |
| 2 | Cmd | `TemplateAppService#create` / `#createTemplate` | CreateTemplateRequest | **写库点**：插入个人模板 |
| 3 | Asm | TemplateDto | — | 前端再调 IF-OP-MR-01 刷新 |

---

### 3.9 IF-OP-MR-08 `getEncounterDiagnosis`（从诊断带入·读列表）

| 项 | 值 |
| --- | --- |
| **App** | `DoctorStationDiagnosisAppService#getEncounterDiagnosis`（**复用 AS-OP-DOR-002 IF-DOR-06**） |
| **HTTP** | `POST /api/app/doctorStationDiagnosis/getEncounterDiagnosis` |

**Request**（与 IF-DOR-06 一致）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| encounterId | String | 是 | 当前门诊就诊 |

**Response 行：`EncounterDiagnosisRowDto`（带入消费字段）**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| encounterDiagnosisId | String | 行主键 |
| conditionDefinitionId | String | 定义 ID |
| conditionCode | String | 编码 |
| conditionDefinitionName | String | 名称 |
| diagnosisKind | String | WESTERN / TCM |
| diagnosisKindText | String | 中西医文案 |
| mainDiagnosisFlag | Integer | 1 主诊 |
| diagnosisSortNo | Integer | 排序 |
| diagnosisDesc | String | 说明 |
| tcmSyndromeGroup | String | 中医病证组 |
| sourceEnum | String | 病名/证候来源 |

**关键校验**：前端可编辑、有光标（EX-OP-MR-109）；encounterId（EX-OP-MR-102）。

**领域编排序列**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | 前端 | encounterId；非只读；编辑器可插入 | 失败 EX-OP-MR-102/106/109 |
| 2 | Query | `DoctorStationDiagnosisAppService#getEncounterDiagnosis` | encounterId | **复用** IF-DOR-06 全序列；仅读 |
| 3 | Asm | 前端 `formatDiagnosisImportText` | 勾选行 | 文案 **BR-OP-MR-010** / EMR 06 |
| 4 | Cmd | — | — | **无服务端写**；前端 `insertContent` 标脏；保存走 IF-OP-MR-06 |

> 变更提醒（**BR-OP-MR-009**）：诊断保存成功事件仅前端黄条，**不**进入本 IF Cmd。

---

### 3.10 IF-OP-MR-09 `listDoctorStationAdvice`（从医嘱带入·读列表）

| 项 | 值 |
| --- | --- |
| **App** | `DoctorStationAdviceAppService#listDoctorStationAdvice`（**复用 AS-OP-DOR-003 IF-DOR-10**） |
| **HTTP** | `POST /api/app/doctorStationAdvice/listDoctorStationAdvice` |

**Request**（与 IF-DOR-10 一致，至少）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| encounterId | String | 是 | 当前就诊 |

**Response 行：`DoctorStationAdviceRowDto`（带入消费字段）**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| listRowKey / requestId | String | 行键 |
| adviceDisplayName | String | 项目名 |
| dosageDisplay | String | 剂量展示 |
| dose / doseUnitName | Decimal/String | 剂量 |
| methodName / rateName | String | 用法/频次 |
| quantity / unitName | Decimal/String | 数量单位 |
| groupId | String | 成组 |
| prescriptionNo | String | 申请单/处方号（识别申请单） |
| entrustContent | String | 嘱托 |
| requestStatus / 状态展示字段 | String | 草稿/已签等（以实现列表字段名为准） |
| basedOnServiceRequestId | String | 申请单关联（若有） |

**关键校验**：同 IF-OP-MR-08 Gate；默认勾选草稿+已签发未停（前端）。

**领域编排序列**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | 前端 | 同 IF-OP-MR-08 | EX-OP-MR-102/106/109 |
| 2 | Query | `DoctorStationAdviceAppService#listDoctorStationAdvice` | encounterId | **复用** IF-DOR-10；**含草稿与申请单行**，不过滤 |
| 3 | Asm | 前端 `formatAdviceImportText` | 勾选行 | EMR 06 / **BR-OP-MR-008** |
| 4 | Cmd | — | — | **无服务端写**；`insertContent` |

---

## 4. QueryService 与领域事件

| 读模型 / 事件 | 触发 | 病历侧行为 |
| --- | --- | --- |
| EMR 文书列表 | create/save 成功后前端刷新 | 再调 IF-OP-MR-03 |
| 就诊诊断保存事件 | AS-OP-DOR-002 | **仅**黄条提醒（BR-OP-MR-009），不自动 insert |
| 医嘱暂存/签发成功 | AS-OP-DOR-003 | **仅**黄条提醒 |
| `metadataValues` | getContent | 既有芯片填充，与主动带入解耦 |

---

## 5. 写库顺序（核心写接口视角）

### 5.1 Cmd 落库顺序一览

| IF | App 方法 | Cmd 序号 | 调用 | 聚合写入顺序 |
| --- | --- | --- | --- | --- |
| IF-OP-MR-05 | create | §3.6 序号 3 | `DocumentManager#create` | 1. emr_document INSERT |
| IF-OP-MR-06 | save | §3.7 序号 4 | `DocumentManager#saveContent` | 1. emr_document UPDATE；2. 可选版本 INSERT |
| IF-OP-MR-07 | create | §3.8 序号 2 | Template 创建 | 1. emr_template INSERT |
| IF-OP-MR-08/09 | — | — | 无 Cmd | 无库写 |

### 5.2 事务边界 / 失败回滚

| IF | 事务 | 失败回滚 |
| --- | --- | --- |
| IF-OP-MR-01～04、08、09 | 无（只读或前端） | — |
| IF-OP-MR-05/06/07 | 域服务方法事务 | 异常回滚当次写 |

---

## 6. 非功能性需求实现（收敛为承诺表）

### 6.1 幂等性

| 项 | 代码位置（必须） | 关联步骤 |
| --- | --- | --- |
| 重复 save 以最后 content 为准 | `DocumentManager#saveContent` | §3.7 序号 4 |
| 带入可重复插入 | 前端 insertContent | §3.9/3.10 序号 4 |

### 6.2 并发控制

| 项 | 代码位置（必须） | 关联步骤 |
| --- | --- | --- |
| 非病程/非会诊仅创建者可写 | DocumentAppService readOnly / 保存前校验 | §3.7 序号 3 |
| 锁定/归档拒绝 | `DocumentManager#saveContent` | §3.7 序号 3～4 |

### 6.3 重试与容错

| 项 | 代码位置（必须） | 关联步骤 |
| --- | --- | --- |
| 自动保存失败 Toast | medical-record-form | §3.7；不阻断编辑 |
| 列表失败可重试 | 前端 | §3.2～3.4 |

### 6.4 数据脱敏

| 项 | 代码位置（必须） | 关联步骤 |
| --- | --- | --- |
| metadataValues 已脱敏 | getContent 元数据装配 | §3.5 序号 3 |

### 6.5 审计日志

| 项 | 代码位置（必须） | 关联步骤 |
| --- | --- | --- |
| 文书 createdBy/updatedBy | 审计字段 | §3.6～3.7 Cmd |

---

## 7. 测试要点

### 7.1 功能测试

| 用例 | 测试层级 | 自动化归属 | 优先级 |
| --- | --- | --- | --- |
| 模板树 OUTPATIENT 加载 | 集成 | API | P0 |
| 载入模板→create→save | 集成 | API+UI | P0 |
| 非创建者打开非病程/非会诊只读；病程与会诊可多人编 | 集成 | API | P0 |
| 锁定后 save 拒绝 | 单元/集成 | API | P0 |
| 存为个人模板后 listForStation 可见 | 集成 | API | P0 |
| 诊断带入插入光标（目标态） | UI | 前端 | P0 |
| 医嘱带入含草稿/申请单（目标态） | UI | 前端 | P0 |
| 变更仅黄条不改正文（目标态） | UI | 前端 | P0 |

### 7.2 非功能测试

| 用例 | 测试层级 | 自动化归属 | 优先级 |
| --- | --- | --- | --- |
| getList/getContent ≤1500ms | 性能 | 压测 | P1 |
| save ≤2000ms | 性能 | 压测 | P1 |

### 7.3 查询结果一致性

| 用例 | 测试层级 | 自动化归属 | 优先级 |
| --- | --- | --- | --- |
| create 后 getList 可见 | 集成 | API | P0 |
| 带入不改变诊断/医嘱表行数 | 集成 | API | P0 |

### 7.4 集成测试

| 用例 | 测试层级 | 自动化归属 | 优先级 |
| --- | --- | --- | --- |
| 未接诊前端拦截 create | UI | E2E | P0 |
| 复用 getEncounterDiagnosis 与诊断 Tab 一致 | 集成 | API | P0 |

### 7.5 测试缺口跟踪

| 项 | 说明 |
| --- | --- |
| 带入 UI | 目标态未接线前，§7.1 带入用例标记阻塞至前端合入 |

---

## 8. 变更历史

| 版本 | 日期 | 变更摘要 |
| --- | --- | --- |
| v1.0 | 2026-07-19 | 初版：对齐 AS-OP-DOR-011 v1.0；EMR document/template HTTP；带入复用诊断/医嘱 IF；§2.3 双锚；IF-OP-MR-08/09 前端 Cmd 为空；带入 UI 目标态待接线 |
