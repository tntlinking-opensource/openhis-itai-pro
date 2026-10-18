# 住院医生站 · 病历 · 应用服务详细设计（SDD）

> **编码**：AS-IP-DOR-004-SDD  
> **版本**：v1.0 | **日期**：2026-07-19  
> **对应 AS**：`AS-IP-DOR-004-住院医生站病历.md`（v1.0）  
> **同构参照**：`AS-OP-DOR-011-门诊医生站病历-SDD.md`（v1.0）— 文书/模板 HTTP 与领域 Cmd **同构**；差异见本文站别、病程导航、首次病程、诊断/医嘱复用服务名。

---

## 1. 实现边界

### 1.1 目标与边界

| 项 | 内容 |
| --- | --- |
| **模块** | 住院医生站 · 病历；EMR `DocumentAppService` / `TemplateAppService`；带入读复用 **住院** 诊断/医嘱 AppService |
| **边界** | 同门诊病历能力 + **病程导航列表** + **首次病程唯一**；主动带入插入光标；变更仅提醒 |
| **编排权威** | `AS-IP-DOR-004` §2、§4；带入文案 EMR 06 |
| **方法权威** | EMR `DocumentManager`；诊断 **AS-IP-DOR-002-SDD** IF-IP-DOR-02；医嘱 **AS-IP-DOR-003-SDD** IF-IP-DOR-10 |
| **严格对齐** | IF-IP-MR-01～10 与 AS 一致 |
| **实现阶段口径** | IF-IP-MR-01～08 **当前已上线**（含病程导航前端）；IF-IP-MR-09/10 读侧已上线、**带入 UI 目标态待接线** |

**Out of scope**：同 AS §1.4；入院结构化回写不在本 SDD 编排（既有 bridge）。

### 1.2 术语口径

| 项 | 约定 |
| --- | --- |
| **字段 / HTTP / 事务** | 同 **AS-OP-DOR-011-SDD** §1.2（`document`/`template`，一律 POST） |
| **站别** | `stationKind=INPATIENT`；个人模板 `encounterClassCode` 为住院适用码 |
| **病程** | `documentTypeCode=PROGRESS_NOTE`（与前端常量一致） |
| **首次病程** | 模板码 `PN_FIRST` → `DocumentManager.TEMPLATE_CODE_FIRST_PROGRESS` |

#### 1.2.1 文档语义码对照

| 文档语义码（AS §5） | 实现口径 | 场景 |
| --- | --- | --- |
| EX-IP-MR-101～109 | 同门诊 EX-OP-MR-101～109 映射 | 通用 |
| EX-IP-MR-110 | `ValidationException`「该就诊已存在首次病程记录」 | IF-IP-MR-05 + PN_FIRST |

---

## 2. 代码结构

### 2.1 包路径（类级清单）

```text
# EMR 文书/模板：同 AS-OP-DOR-011-SDD §2.1（DocumentAppService / TemplateAppService）

# 带入读侧（住院复用）
zxplat/health-application/.../inpatient/doctorstation/diagnosis/
  service/InpatientDoctorStationDiagnosisAppService.java   # getEncounterDiagnosis
zxplat/health-application/.../inpatient/doctorstation/advice/
  service/InpatientDoctorStationAdviceAppService.java      # list 医嘱（与 IF-IP-DOR-10 方法名对齐工程）

# 前端共用
openhis-pro-ui/.../components/outpatient/medical-record-form.vue  # stationKind=INPATIENT；病程导航
```

### 2.2 依赖注入表

| 依赖 | 用途 |
| --- | --- |
| `DocumentManager` / `DocumentAppService` | 列表/打开/创建/保存；病程过滤列表 |
| `TemplateAppService` | listForStation(INPATIENT) / getContent / create |
| `InpatientDoctorStationDiagnosisAppService` | IF-IP-MR-09 |
| `InpatientDoctorStationAdviceAppService` | IF-IP-MR-10 |
| 前端 | 病程上一张/下一张；静默打开最新病程；带入 insertContent（目标态） |

### 2.2.1 领域方法白名单

| 类型 | 方法 | 用于 IF |
| --- | --- | --- |
| `TemplateAppService` | `listForStation` | IF-IP-MR-01 |
| `TemplateAppService` | `getContent` | IF-IP-MR-02 |
| `DocumentAppService` | `getList` | IF-IP-MR-03、IF-IP-MR-08 |
| `DocumentAppService` | `getContent` | IF-IP-MR-04 |
| `DocumentManager` | `create` | IF-IP-MR-05 Cmd（含 PN_FIRST 校验） |
| `DocumentAppService` | `create` | IF-IP-MR-05 |
| `DocumentManager` | `saveContent` | IF-IP-MR-06 Cmd |
| `DocumentAppService` | `save` | IF-IP-MR-06 |
| `TemplateAppService` | `create` / `createTemplate` | IF-IP-MR-07 |
| `DocumentManager` | `findPage` | IF-IP-MR-03/08 |
| `DocumentManager` | `findById` | IF-IP-MR-04/06 |
| `InpatientDoctorStationDiagnosisAppService` | `getEncounterDiagnosis` | IF-IP-MR-09 |
| `InpatientDoctorStationAdviceAppService` | `listInpatientDoctorStationAdvice` | IF-IP-MR-10 |

### 2.3 枚举、字典与值集（DM 与代码可追溯）

| 类别 | 代码实现锚点 | DM 追溯锚点 | 说明 |
| --- | --- | --- | --- |
| 文书状态 | `DocumentContentDto.status`；`DocumentManager.STATUS_*` | `docs/模块/zxplat-module-emr/00-功能清单.md` 文书状态 | IF-IP-MR-04/06 |
| 站别 | `ListTemplatesForStationRequest.stationKind`=`INPATIENT` | `docs/模块/zxplat-module-emr/00-功能清单.md` 5.3 | IF-IP-MR-01 |
| 首次病程模板码 | `DocumentManager.TEMPLATE_CODE_FIRST_PROGRESS` | `docs/模块/zxplat-module-emr/00-功能清单.md` 13.7 | IF-IP-MR-05 |
| 病程类型 | `QueryDocumentRequest.documentTypeCode`（PROGRESS_NOTE） | `docs/模块/zxplat-module-emr/00-功能清单.md` 13.7 | IF-IP-MR-08 |
| 住院诊断类型展示 | `EncounterDiagnosisRowDto.iptDiseaseTypeText` | `DM-ADM-002` / AS-IP-DOR-002 | IF-IP-MR-09 文案 |
| 长/临/出院带药 | `TherapyType`（医嘱列表行） | `DM-MED-003` §4.2 | IF-IP-MR-10 展示 |
| 就诊标识 | `CreateDocumentRequest.encounterId` | `DM-ADM-002` §3 | Gate |

---

## 3. 按接口 ID 命名展开

> **先读完各 IF「领域编排序列」**，按序号执行。

### 3.1 应用服务接口（方法白名单）

| IF | 应用服务方法（英文） | 中文方法名 | HTTP |
| --- | --- | --- | --- |
| IF-IP-MR-01 | `listForStation` | 加载住院站别模板树 | `POST /api/app/template/listForStation` |
| IF-IP-MR-02 | `getContent` | 载入模板正文 | `POST /api/app/template/getContent` |
| IF-IP-MR-03 | `getList` | 查询历史文书列表 | `POST /api/app/document/getList` |
| IF-IP-MR-04 | `getContent` | 打开文书内容 | `POST /api/app/document/getContent` |
| IF-IP-MR-05 | `create` | 创建文书 | `POST /api/app/document/create` |
| IF-IP-MR-06 | `save` | 保存文书内容 | `POST /api/app/document/save` |
| IF-IP-MR-07 | `create` | 存为个人模板 | `POST /api/app/template/create` |
| IF-IP-MR-08 | `getList` | 病程导航列表 | `POST /api/app/document/getList` |
| IF-IP-MR-09 | `getEncounterDiagnosis`（复用） | 从诊断带入 | `POST /api/app/inpatientDoctorStationDiagnosis/getEncounterDiagnosis` |
| IF-IP-MR-10 | `listInpatientDoctorStationAdvice`（复用） | 从医嘱带入 | `POST /api/app/inpatientDoctorStationAdvice/listInpatientDoctorStationAdvice` |

### 3.1.1 编排约定

同 **AS-OP-DOR-011-SDD** §3.1.1（Gate / Pre / Cmd / Asm）。

---

### 3.2 IF-IP-MR-01 `listForStation`（加载住院站别模板树）

**复用** `AS-OP-DOR-011-SDD` §3.2 编排序列与 Request/Response 字段表；差异仅：

| 字段 | 取值 |
| --- | --- |
| `ListTemplatesForStationRequest.stationKind` | **`INPATIENT`** |

---

### 3.3 IF-IP-MR-02 `getContent`（载入模板正文）

**复用** `AS-OP-DOR-011-SDD` §3.3；Gate：须已选住院就诊（EX-IP-MR-102）。

---

### 3.4 IF-IP-MR-03 `getList`（查询历史文书列表）

**复用** `AS-OP-DOR-011-SDD` §3.4（Request/Response 字段表与编排序列）；`patientId`+`encounterId` 为住院上下文。历史区传 `includeLinkedEncounters=true` 时按 `amb_encounter_id` 展开当前∪源门诊；对端文书前端只读。

---

### 3.5 IF-IP-MR-04 `getContent`（打开文书内容）

**复用** `AS-OP-DOR-011-SDD` §3.5。

---

### 3.6 IF-IP-MR-05 `create`（创建文书）

| 项 | 值 |
| --- | --- |
| **App** | `DocumentAppService#create` |
| **HTTP** | `POST /api/app/document/create` |

**Request / Response 字段表**：同 **AS-OP-DOR-011-SDD** §3.6。

**关键校验**：同门诊 + **BR-IP-MR-004** 首次病程唯一。

**失败码**：另增 EX-IP-MR-110。

**领域编排序列**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | 前端 | encounterId、patientId、documentTypeCode | EX-IP-MR-102 |
| 2 | Pre | 解析 templateId | 可选 | 无效 EX-IP-MR-103 |
| 3 | Cmd | `DocumentManager#create` | 含 templateId | **写库点**：INSERT DRAFT；**若**模板码为 `PN_FIRST` 且同就诊已存在 → 抛 EX-IP-MR-110，**不写**；否则跳过该校验分支 |
| 4 | Asm | DocumentContentDto | 新文书 | — |

---

### 3.7 IF-IP-MR-06 `save`（保存文书内容）

**复用** `AS-OP-DOR-011-SDD` §3.7 全序列与字段表（EX 码换用 EX-IP-MR-*）。

---

### 3.8 IF-IP-MR-07 `create`（存为个人模板）

**复用** `AS-OP-DOR-011-SDD` §3.8；差异：`encounterClassCode` 为**住院**适用码；`fromDoctorStation=true`。

---

### 3.9 IF-IP-MR-08 `getList`（病程导航列表）

| 项 | 值 |
| --- | --- |
| **App** | `DocumentAppService#getList` |
| **HTTP** | `POST /api/app/document/getList` |

**Request：`QueryDocumentRequest`**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| patientId | String | 是 | 患者 |
| encounterId | String | 是 | 当前住院就诊 |
| documentTypeCode | String | 是 | **PROGRESS_NOTE** |
| includeLinkedEncounters | Boolean | 否 | **勿传 true**：病程导航仅扫当前住院就诊 |
| pageNo / pageSize | Integer | 是 | 建议一次拉全量导航集 |

**Response**：同 §3.4 `DocumentSummaryDto` 字段表。

**领域编排序列**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | 前端 | encounterId；住院站别 | 无就诊则跳过 |
| 2 | Query | `DocumentAppService#getList` | type=PROGRESS_NOTE | 仅读 |
| 3 | Asm | 前端按 createdAt **升序** | 列表 | 供上一张/下一张；切患者时可打开最新一份（再调 IF-IP-MR-04） |

---

### 3.10 IF-IP-MR-09 `getEncounterDiagnosis`（从诊断带入）

| 项 | 值 |
| --- | --- |
| **App** | `InpatientDoctorStationDiagnosisAppService#getEncounterDiagnosis` |
| **HTTP** | `POST /api/app/inpatientDoctorStationDiagnosis/getEncounterDiagnosis` |

**Request / 行字段**：同 **AS-OP-DOR-011-SDD** §3.9，并增加：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| iptDiseaseTypeCode / iptDiseaseTypeText | String | 住院诊断类型（文案可附） |

**领域编排序列**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | 前端 | 可编辑、有光标、有就诊 | EX-IP-MR-102/106/109 |
| 2 | Query | `InpatientDoctorStationDiagnosisAppService#getEncounterDiagnosis` | encounterId | **复用** IF-IP-DOR-02 序列 |
| 3 | Asm | 前端 formatDiagnosisImportText | 勾选行 | EMR 06；**不**写 admissionDiagnosisStructured |
| 4 | Cmd | — | — | 无服务端写；insertContent |

---

### 3.11 IF-IP-MR-10 `listInpatientDoctorStationAdvice`（从医嘱带入）

| 项 | 值 |
| --- | --- |
| **App** | `InpatientDoctorStationAdviceAppService#listInpatientDoctorStationAdvice`（**复用 AS-IP-DOR-003 IF-IP-DOR-10**） |
| **HTTP** | `POST /api/app/inpatientDoctorStationAdvice/listInpatientDoctorStationAdvice` |

**Request**：至少 `encounterId`（同 IF-IP-DOR-10 / `ListInpatientDoctorStationAdviceRequest`）。

**Response 带入消费字段**：同 **AS-OP-DOR-011-SDD** §3.10，并含住院列表已有：`therapyType`/`entrustContent`/停嘱状态等（以实现 DTO 为准）。

**领域编排序列**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | 前端 | 同 IF-IP-MR-09 | — |
| 2 | Query | `InpatientDoctorStationAdviceAppService#listInpatientDoctorStationAdvice` | encounterId | **含草稿与申请单**；复用 IF-IP-DOR-10 |
| 3 | Asm | formatAdviceImportText | 勾选；默认草稿+已签未停 | EMR 06 |
| 4 | Cmd | — | — | 无服务端写 |

---

## 4. QueryService 与领域事件

| 读模型 / 事件 | 触发 | 病历侧行为 |
| --- | --- | --- |
| 文书/病程列表 | create/save 后 | 刷新 IF-IP-MR-03/08 |
| 住院诊断保存 | AS-IP-DOR-002 | 黄条提醒；结构化回写**另路径**不经本 IF |
| 住院医嘱变更 | AS-IP-DOR-003 | 黄条提醒 |

---

## 5. 写库顺序（核心写接口视角）

### 5.1 Cmd 落库顺序一览

| IF | App 方法 | Cmd 序号 | 调用 | 聚合写入顺序 |
| --- | --- | --- | --- | --- |
| IF-IP-MR-05 | create | §3.6 序号 3 | `DocumentManager#create` | emr_document INSERT（PN_FIRST 冲突则不写） |
| IF-IP-MR-06 | save | 复用门诊 §3.7 序号 4 | `DocumentManager#saveContent` | UPDATE + 可选版本 |
| IF-IP-MR-07 | create | 复用门诊 §3.8 | Template 创建 | emr_template INSERT |
| IF-IP-MR-09/10 | — | — | 无 | — |

### 5.2 事务边界 / 失败回滚

| IF | 事务 | 失败回滚 |
| --- | --- | --- |
| 只读 IF | 无 | — |
| IF-IP-MR-05/06/07 | 域方法事务 | 异常回滚；PN_FIRST 冲突不落库 |

---

## 6. 非功能性需求实现（收敛为承诺表）

### 6.1 幂等性

| 项 | 代码位置（必须） | 关联步骤 |
| --- | --- | --- |
| save 最后写入 | `DocumentManager#saveContent` | IF-IP-MR-06 |
| PN_FIRST 不可重复创建 | `DocumentManager#create` | §3.6 序号 3 |

### 6.2 并发控制

| 项 | 代码位置（必须） | 关联步骤 |
| --- | --- | --- |
| 草稿创建者可写 | DocumentAppService | IF-IP-MR-06 Pre |
| 双开首次病程 | create 校验 | §3.6 序号 3 → EX-IP-MR-110 |

### 6.3 重试与容错

| 项 | 代码位置（必须） | 关联步骤 |
| --- | --- | --- |
| 自动保存失败提示 | medical-record-form | IF-IP-MR-06 |

### 6.4 数据脱敏

| 项 | 代码位置（必须） | 关联步骤 |
| --- | --- | --- |
| metadataValues | getContent | IF-IP-MR-04 |

### 6.5 审计日志

| 项 | 代码位置（必须） | 关联步骤 |
| --- | --- | --- |
| createdBy/updatedBy | 文书审计字段 | IF-IP-MR-05/06 |

---

## 7. 测试要点

### 7.1 功能测试

| 用例 | 测试层级 | 自动化归属 | 优先级 |
| --- | --- | --- | --- |
| INPATIENT 模板树 | 集成 | API | P0 |
| 重复 PN_FIRST 创建失败 | 集成 | API | P0 |
| 病程导航升序与上下张 | UI | 前端 | P0 |
| 切患者静默打开最新病程 | UI | 前端 | P1 |
| 诊断/医嘱带入（目标态） | UI | 前端 | P0 |
| 带入不改 admissionDiagnosisStructured | 集成 | API+库 | P0 |

### 7.2 非功能测试

| 用例 | 测试层级 | 自动化归属 | 优先级 |
| --- | --- | --- | --- |
| 病程 getList 性能 | 性能 | 压测 | P1 |

### 7.3 查询结果一致性

| 用例 | 测试层级 | 自动化归属 | 优先级 |
| --- | --- | --- | --- |
| create 病程后导航列表含新行 | 集成 | API | P0 |

### 7.4 集成测试

| 用例 | 测试层级 | 自动化归属 | 优先级 |
| --- | --- | --- | --- |
| 复用住院诊断列表与诊断 Tab 一致 | 集成 | API | P0 |

### 7.5 测试缺口跟踪

| 项 | 说明 |
| --- | --- |
| 带入 UI | 目标态；合入前 §7.1 带入用例阻塞 |

---

## 8. 变更历史

| 版本 | 日期 | 变更摘要 |
| --- | --- | --- |
| v1.0 | 2026-07-19 | 初版：对齐 AS-IP-DOR-004 v1.0；同构门诊 SDD；补 IF-IP-MR-08 病程导航、PN_FIRST、住院诊断/医嘱 HTTP；带入 UI 目标态 |
