# 住院医生站 · 就诊管理 · 应用服务详细设计（SDD）

> **编码**：AS-IP-DOR-001-SDD  
> **版本**：v1.0.2 | **日期**：2026-05-20 | **作者**：（待填）

---

## 1. 实现边界

### 1.1 目标与边界

| 项 | 内容 |
| --- | --- |
| **模块** | 住院医生站 · 就诊管理（患者上下文 / 左侧在院患者队列，`inpatient/doctorstation/encountermanage`） |
| **边界** | **本期仅读**：分页查询当前登录科室下医生站可见在院患者列表（IF-IP-DOR-01）；**不**含病区树、患者详情独立 IF、患者信息维护、任务计数与任务筛选 |
| **编排权威** | `AS-IP-DOR-001-住院医生站就诊管理.md`（v1.1）§2、§4 |
| **方法权威** | `DM-ADM-002`、`DM-ADM-001`、`DM-ADM-009`；`DM-ADM-002` §6.2 `EncounterQueryService`（`InpatientEncounterContextQueryModel`） |
| **严格对齐** | IF 编号 **IF-IP-DOR-01**，不得增删改号 |
| **实现阶段口径** | **已落地**：`EncounterContextReader#pageInpatientDoctorStationQueue` + `InpatientDoctorStationEncounterManageAppService` |

**Out of scope（重复 AS §1.4）**

| 类别 | 内容 |
| --- | --- |
| 病区/病房树、患者详情、信息维护 | **AS-IP-NUR-001** |
| 诊断 / 医嘱 / 病历 Tab | **AS-IP-DOR-002** 起 |
| 护士站任务计数 | **BR-IP-DOR-006** 明确剔除 |

**上游依赖**

| 文档 | 关系 |
| --- | --- |
| `AS-IP-NUR-001-SDD` | 富卡片字段与装配口径参照（**剔除**任务计数步骤 5～11） |
| `AS-IP-FIN-002` | 展示余额公式与 `InpatientDisplayAccountBalanceAssembler` |
| `DM-ADM-002 §6.2 EncounterQueryService（InpatientEncounterContextQueryModel）` | **Q-IPI-006**、`organizationId` 由 App 写入 |

### 1.2 术语口径

| 项 | 约定 |
| --- | --- |
| **状态/筛选 code** | 持久化与比较一律 **String `code`**（`EncounterStatus.getCode()`、`EncounterClass.getCode()`） |
| **科室范围** | `organization_id = CurrentManagedOrganizationIdsResolver#resolveOrganizationUnitIdForQuery()`（须在出诊管辖科室内）；**禁止** Request 传 `organizationId`（**B.10.11 / B.6.5**） |
| **可见性（BR-IP-DOR-010）** | **科室全员**：`IMP + IN_PROGRESS/ON_HOLD` 且 `organization_id` 等于会话科室即可见；**禁止**按 `ATTENDING_DOCTOR`/转科参与者收窄（**不同于**门诊 **BR-DOR-009**） |
| **可见状态** | 读侧 **Q-IPI-006** 内置 `IN_PROGRESS` + `ON_HOLD`；App **不得**再拼护士站五态并集 |
| **分页** | `AbstractPageQuery`；`pageNo` **1-based**；`PageWindowGate` + `PagedResultBuilder` |
| **展示余额** | **`InpatientAccountBalanceDisplayAssembler`**：`computeDisplayAccountBalance(account, pending)` + 单一余额文案（`accountBalanceSummaryText`）；`lowBalanceWarning` **< 500** |
| **HTTP 接入口径** | `AppServiceControllerRegistrar`：**一律 `POST`** `POST /api/app/inpatientDoctorStationEncounterManage/{methodName}`；`serviceName` 由 `InpatientDoctorStationEncounterManageAppService` 推导 |

#### 1.2.1 文档语义码与 `ErrorCodeConstants` 对照

| 文档语义码 | 实现 `ErrorCode`（典型触发） |
| --- | --- |
| EX-IP-DOR-101 | QueryService/批量查询失败 → `BusinessException` 包装，消息码 `InpatientDoctorStationConstants.EX_IP_DOR_101` |
| EX-IP-DOR-102 | 未选择科室上下文 → `ENCOUNTER_INVALID_ARGUMENT`（`resolveOrganizationUnitIdForQuery()` 为空） |
| EX-IP-DOR-103 | 就诊不在医生站可见范围 → `InpatientDoctorStationConstants.EX_IP_DOR_103`（**BR-IP-DOR-010**） |

---

## 2. 代码结构

### 2.1 包路径（类级清单）

```text
zxplat/health-application/src/main/java/com/openhis/health/application/inpatient/doctorstation/
  encountermanage/
    service/
      InpatientDoctorStationEncounterManageAppService.java
    request/
      PagePatientQueueRequest.java
    response/
      PagePatientQueueResponse.java
    dto/
      DoctorStationPatientQueueItemDto.java
    mapper/
      InpatientDoctorStationEncounterManageMapper.java
      InpatientDoctorStationEncounterManageMapperImpl.java
```

> 包名：`com.zxplat.domain.application.inpatient.doctorstation.encountermanage`  
> AppService Bean 名 → HTTP 前缀：`inpatientDoctorStationEncounterManage`

**跨包复用（不重复造 DTO/装配器）**

| 复用类 | 用途 |
| --- | --- |
| `com.zxplat.domain.application.shared.inpatient.InpatientInfoQueueDisplayAssembler` | 队列行展示回填 |
| `com.zxplat.domain.application.shared.inpatient.AdmissionPrimaryDiagnosisAssembler` | `diagnosis_json` → `diagnosisList`/`diagnosisSummary` |
| `com.zxplat.domain.application.shared.inpatient.InpatientDisplayAccountBalanceAssembler` | 展示余额与低余额预警 |
| `com.zxplat.domain.application.inpatient.nursestation.encountermanage.mapper.NurseStationEncounterManageMapper` | **可选**：`toPatientQueueItemDto` 入参 `counts=null` 时任务字段保持 null（**禁止**赋值计数） |

### 2.2 依赖注入表

| 注入类型 | 类名（FQCN） | 职责 | 来源 |
| --- | --- | --- | --- |
| 应用服务 | `InpatientDoctorStationEncounterManageAppService` | IF-IP-DOR-01 编排 | 本模块 |
| Facade | `EncounterContextReader` | `pageInpatientDoctorStationQueue`（**Q-IPI-006**） | `application.shared.encounter` |
| 查询服务 | `EncounterQueryService` | 当前页 `priority_enum` 批量回填 | domain.administration.encounter |
| 查询服务 | `AccountQueryService` | 当前页账户批量（禁止 N+1） | domain.administration.account |
| 查询服务 | `ChargeItemQueryService` | 当前页 **待结算合计** 批量（禁止 N+1） | domain.administration.chargeitem |
| 机构收窄 | `CurrentManagedOrganizationIdsResolver` | `resolveOrganizationUnitIdForQuery` → 写入 `InpatientEncounterPageQuery.organizationId` | `module.common.validator` |
| 门禁 | `com.zxplat.domain.module.common.validator.PageWindowGate` | 分页入站校验 | health-module-support / module.common |
| Assembler | `InpatientInfoQueueDisplayAssembler` | 缺列 overlay | health-module-support / module.common |
| Assembler | `AdmissionPrimaryDiagnosisAssembler` | 诊断摘要 | health-module-support / module.common |
| Assembler | `InpatientPatientQueueNursingLevelEnrichAssembler` | 护理级别批量 enrich（**BR-IP-DOR-009**） | health-module-support / application.shared.encounter |
| Support | `InpatientDoctorStationEncounterVisibilitySupport` | Tab 写路径可见性 Gate（**BR-IP-DOR-010**；与 Q-IPI-006 同源） | health-module-support / application.shared.encounter |
| Assembler | `InpatientDisplayAccountBalanceAssembler` | 展示余额 | health-module-support / module.common |
| Mapper | `InpatientDoctorStationEncounterManageMapper` + `Impl` | Query/Response 装配 | 本模块 mapper |
| Mapper（可选） | `NurseStationEncounterManageMapper` | 行 DTO 装配复用 | nursestation.encountermanage |

### 2.3 枚举、字典与值集（DM 与代码可追溯）

| 类别 | 代码实现锚点 | DM 追溯锚点 | 说明 |
| --- | --- | --- | --- |
| 就诊类别 | `com.zxplat.domain.share.enums.EncounterClass` | `DM-ADM-002` §3 `adm_encounter.class_enum` | 读侧固定 **IMP**（Q-IPI-006 内置） |
| 就诊状态 | `com.zxplat.domain.share.enums.EncounterStatus` | `DM-ADM-002` §2.3.2 IMP 专规 | 可见 **`IN_PROGRESS`**、**`ON_HOLD`** |
| 病情/优先级 | `DoctorStationPatientQueueItemDto.priorityEnum` | `DM-ADM-002` §3 `priority_enum` | QueryService未投影时 App 批量查 Encounter |
| 低余额阈值 | `InpatientNurseStationConstants.LOW_BALANCE_WARNING_THRESHOLD` | `AS-IP-DOR-001` 对齐 **BR-IP-NUR-006** | 复用护士站常量 **500** 元 |
| 业务消息码 | `InpatientDoctorStationConstants.EX_IP_DOR_*` | `AS-IP-DOR-001` §5 | **目标态待生成**（`zxplat-domain`） |
| QueryService查询入参 | `InpatientEncounterPageQuery.organizationId` | QueryService §3.3 **BR-IP-DOR-003** | App 经 **`CurrentManagedOrganizationIdsResolver`** 写入，**非** Request 字段 |

### 2.4 领域方法白名单

| 类型 | 方法 | 用于 IF |
| --- | --- | --- |
| `EncounterContextReader` | `pageInpatientDoctorStationQueue` | IF-IP-DOR-01 |
| `AccountQueryService` | `mapRegistrationAccountsByEncounterIds` | IF-IP-DOR-01 |
| `EncounterQueryService` | `mapPriorityEnumByEncounterIds` | IF-IP-DOR-01 |
| `InpatientPatientQueueNursingLevelEnrichAssembler` | `enrichByEncounterIds` | IF-IP-DOR-01（内部调 `ServiceRequestQueryService#mapNursingLevelForPatientQueueByEncounterIds` + `ActivityDefinitionQueryService#findAllByIds`） |

> **禁止**白名单出现：`pageInpatientNurseStationQueue`、`MedicationRequestQueryService`、`ProcedureQueryService`、`LocationQueryService#listManagedWardsForPractitioner`（本 IF 无地理树/任务筛选）。护理级别须经 **`InpatientPatientQueueNursingLevelEnrichAssembler`** 收口，App **不得**直注 `ServiceRequestQueryService`。

---

## 3. 按接口 ID 命名展开

先读完 IF 的「领域编排序列（严格顺序）」，按序号执行；Gate/Pre 任一失败都不得进入后续步骤。

### 3.1 应用服务接口（方法白名单）

| IF | 应用服务方法（英文） | 中文方法名 | HTTP |
| --- | --- | --- | --- |
| IF-IP-DOR-01 | `pagePatientQueue` | 查询在院患者列表 | `POST /api/app/inpatientDoctorStationEncounterManage/pagePatientQueue` |

### 3.1.1 编排约定

| 阶段 | 含义 | 失败时 |
| --- | --- | --- |
| **Gate** | 分页合法、科室上下文非空 | 不再调用后续步骤 |
| **Pre** | QueryService 分页、批量账户/优先级、展示装配 | 抛业务异常 |
| **Asm** | Mapper + Assembler 纯 DTO 装配 | — |

---

### 3.2 IF-IP-DOR-01 `pagePatientQueue`（查询在院患者列表）

| 项 | 内容 |
| --- | --- |
| **App** | `InpatientDoctorStationEncounterManageAppService#pagePatientQueue` |
| **HTTP** | `POST /api/app/inpatientDoctorStationEncounterManage/pagePatientQueue` |

#### Request 字段表（JSON 请求体，与 `PagePatientQueueRequest` 一致）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `keyword` | String | N | 姓名 / 住院号 / 床号；不区分大小写模糊 |
| `pageNo` | Integer | Y | 1-based |
| `pageSize` | Integer | Y | 建议 ≤50 |

> **禁止** Request 出现：`organizationId`、`wardId`、`houseId`、`nurseStationTaskFilter`、`nurseStationExecutionDate`（**B.10.11** / **BR-IP-DOR-003**）。

#### Response 字段表

`PagePatientQueueResponse` **继承** `com.zxplat.framework.core.application.dto.PagedResult<DoctorStationPatientQueueItemDto>`（**B.10.11**），对外 JSON 字段如下：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `content` | List | `List<DoctorStationPatientQueueItemDto>`（当前页行） |
| `totalCount` | Long | 总条数 |
| `pageNo` | Integer | 当前页（1-based） |
| `pageSize` | Integer | 页大小 |

**`DoctorStationPatientQueueItemDto`**（对齐护士站 `PatientQueueItemDto`，**不含** `pendingAuditCount`/`pendingExecuteCount`）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `encounterId` | Long | 就诊 id |
| `encounterBusNo` | String | 住院号 |
| `statusEnum` | String | 就诊状态 code |
| `statusEnumText` | String | IMP 专规文案（`InpatientEncounterStatusDisplayUtil`；**BR-IP-DOR-008**） |
| `patientId` | Long | 患者 id |
| `patientName` | String | 姓名 |
| `genderEnum` | String | 性别 code |
| `genderEnumText` | String | 性别展示 |
| `ageText` | String | 年龄 |
| `idCard` | String | 身份证号（患者主档完整号，**不脱敏**；读模型未投影时 App 批量回填） |
| `wardId` | Long | 病区 id |
| `wardName` | String | 病区名 |
| `houseName` | String | 病房名（App enrichment，有则返） |
| `bedId` | Long | 床 id |
| `bedName` | String | 床号 |
| `organizationId` | Long | 科室 id |
| `organizationName` | String | 科室名 |
| `priorityEnum` | String | 病情/优先级 code |
| `startTime` | LocalDateTime | 入院时间 |
| `admissionTime` | LocalDateTime | 入科时间 |
| `totalAmount` | BigDecimal | 累计费用 |
| `paidAmount` | BigDecimal | 已支付 |
| `arrearsAmount` | BigDecimal | 欠费 |
| `accountBalance` | BigDecimal | **展示可用余额**（兼容字段） |
| `prepayNetBalance` | BigDecimal | 库内 `balance` |
| `pendingSettlementTotal` | BigDecimal | 待结算合计 |
| `availableBalance` | BigDecimal | 同 `accountBalance` |
| `accountBalanceSummaryText` | String | 主展示文案 |
| `accountBalanceSubText` | String | 副展示文案 |
| `accountBalanceDisplayMode` | String | `NORMAL` / `REVERSED_PENDING_RESETTLE` |
| `lowBalanceWarning` | Boolean | 展示余额 < 阈值 |
| `attendingDoctorName` | String | 主治摘要 |
| `primaryNurseName` | String | 责任护士摘要 |
| `diagnosisSummary` | String | 诊断一行摘要（**禁止**透传 `diagnosis_json`） |
| `diagnosisList` | List\<String\> | 诊断展示列表 |
| `pinyinCode` | String | 拼音码 |
| `wubiCode` | String | 五笔码 |
| `nursingLevelServiceRequestId` | Long | 当前护理级别服务申请 id；无护理时为 null（**BR-IP-DOR-009**） |
| `nursingLevelActivityId` | Long | 护理级别 activityId |
| `nursingLevelName` | String | 护理级别展示名 |
| `nursingLevelStatusEnum` | String | 护理级别医嘱状态（`DRAFT`/`ACTIVE`/`VERIFIED`/`STOPPED` 等） |

#### 失败码口径表

| 失败码 | 场景 | 用户提示 | 实现 `ErrorCode` |
| --- | --- | --- | --- |
| EX-IP-DOR-101 | QueryService/批量查询失败 | 加载失败，请稍后重试 | 包装为 `BusinessException`，消息码 `EX_IP_DOR_101` |
| EX-IP-DOR-102 | 登录科室为空 | 请先选择科室 | `ENCOUNTER_INVALID_ARGUMENT` |

#### 领域编排序列（严格顺序）

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | `PageWindowGate#requirePositivePageWindow` | `pageNo`/`pageSize` | 失败 → EX-IP-DOR-101 |
| 2 | Gate | `CurrentManagedOrganizationIdsResolver#resolveOrganizationUnitIdForQuery` | 会话科室 | 空 → EX-IP-DOR-102 |
| 3 | Pre | `InpatientDoctorStationEncounterManageMapper#toInpatientEncounterPageQuery` | `keyword`、步骤 2 的 `organizationId` | 置位 `doctorStationQueue=true`；写入 `organizationId`；**禁止**护士站字段 |
| 4 | Pre | `PageQueryBinder#bind` | Request、步骤 3 Query | 分页绑定 |
| 5 | Pre | **`EncounterContextReader#pageInpatientDoctorStationQueue`** | 步骤 3 Query | **Q-IPI-006**；排序 `encounter_bus_no ASC` |
| 6 | Pre | `AccountQueryService#mapRegistrationAccountsByEncounterIds` | **当前页** encounterId 集合 | **BR-IP-DOR-006** 余额；禁止 N+1 |
| 7 | Pre | `EncounterQueryService#mapPriorityEnumByEncounterIds` | 当前页 ids | 病情优先级回填 |
| 8 | Pre | `InpatientInfoQueueDisplayAssembler#buildOverlay` | 当前页QueryService行 | 缺列回填 |
| 8b | Pre | `InpatientPatientQueueNursingLevelEnrichAssembler#enrichByEncounterIds` | **当前页** encounterId 集合；`referenceDate=当日` | **BR-IP-DOR-009**；禁止 N+1 |
| 9 | Pre | App `buildDisplayEnrichment`（可选） | 当前页行 | 病房名/检索码等；**不**拉任务计数 |
| 10 | Asm | `AdmissionPrimaryDiagnosisAssembler` + Mapper `toDoctorStationPatientQueueItemDto` + App `applyNursingLevelDisplay` | QueryService行 + 账户 + overlay + 步骤 8b | **禁止** set 任务计数字段；**禁止** API 透传 `diagnosis_json` |
| 11 | Asm | `InpatientDoctorStationEncounterManageMapper#toPagePatientQueueResponse` | 步骤 5～10 | 纯装配 |

#### Mapper 接口方法签名

```java
InpatientEncounterPageQuery toInpatientEncounterPageQuery(PagePatientQueueRequest request, Long organizationId);
DoctorStationPatientQueueItemDto toDoctorStationPatientQueueItemDto(
    InpatientEncounterContextQueryModel row,
    BigDecimal accountBalance,
    boolean lowBalanceWarning,
    InpatientInfoQueueDisplayAssembler.InpatientQueueDisplayOverlay displayOverlay,
    String priorityEnum,
    List<String> diagnosisList,
    String diagnosisSummary,
    DoctorStationQueueDisplayEnrichment enrichment);
PagePatientQueueResponse toPagePatientQueueResponse(
    List<DoctorStationPatientQueueItemDto> rows,
    Page<InpatientEncounterContextQueryModel> page);
```

> 分页响应须经 `PagedResultBuilder#build` 装配，禁止手写 `page.getNumber()+1`。

---

## 4. QueryService 与事件刷新

### 4.1 QueryService查询语义

| QueryService | 查询服务方法 | 用于 IF | 查询结果用途 |
| --- | --- | --- | --- |
| InpatientEncounterContextQueryModel | `EncounterContextReader#pageInpatientDoctorStationQueue` | IF-IP-DOR-01 | 医生站队列骨架；内置 IMP + `IN_PROGRESS`/`ON_HOLD` + `organization_id` + 关键字 + `bus_no ASC` |

### 4.2 QueryService 查询语义（事件 → QueryService更新）

| 领域事件 | 投影目标 | 更新动作 | 列表侧影响 |
| --- | --- | --- | --- |
| 分床/转科/出院/诊断关联等（见QueryService §4） | `InpatientEncounterContextQueryModel` 各展示列 | INSERT/UPDATE | 列表刷新后行字段变化；本 IF 只读 |

---

## 5. 写库顺序（核心写接口视角）

本 AS **无写接口**。

| IF | App 方法 | Cmd | 说明 |
| --- | --- | --- | --- |
| IF-IP-DOR-01 | `pagePatientQueue` | — | 纯读；无 `@Transactional` |

---

## 6. 非功能性需求实现（收敛为承诺表）

### 6.1 幂等性

| 场景 | 承诺 | 实现方式 | 关联步骤 |
| --- | --- | --- | --- |
| 列表重复查询 | 天然幂等 | 无写副作用 | §3.2 全序列 |

### 6.2 并发控制

| 场景 | 承诺 | 实现方式 | 关联步骤 |
| --- | --- | --- | --- |
| 列表并发刷新 | 以后写投影为准 | 只读无锁 | §3.2 序号 5 |

### 6.3 重试与容错

| 场景 | 承诺 | 实现方式 | 关联步骤 |
| --- | --- | --- | --- |
| 读失败 | EX-IP-DOR-101 | AppService 统一包装 | §3.2 |

### 6.4 数据脱敏

| 场景 | 承诺 | 实现方式 |
| --- | --- | --- |
| 患者姓名/证件 | 按产品脱敏 | Mapper/Assembler 装配层（若启用） |

### 6.5 审计日志

本 IF 无写操作，无审计命令。

---

## 7. 测试要点

### 7.1 功能测试

| 用例 | 预期 | 优先级 | 自动化归属 |
| --- | --- | --- | --- |
| 仅返回本科室 IMP 在院患者 | `organization_id` 过滤生效 | P0 | App IT |
| 状态仅 IN_PROGRESS + ON_HOLD | 不含 PLANNED/REGISTERED 等 | P0 | App IT |
| 关键字命中姓名/住院号/床号 | 模糊检索 | P0 | App IT |
| 默认排序住院号升序 | `encounter_bus_no ASC` | P0 | App IT |
| 响应无任务计数字段 | DTO 无 pending* 或恒 null | P0 | Contract |
| 诊断列为 list+summary | 非 diagnosis_json 原文 | P0 | App IT |
| 未选科室 | EX-IP-DOR-102 | P0 | App IT |

### 7.2 非功能测试

| 用例 | 预期 | 优先级 |
| --- | --- | --- |
| 列表页账户批量 | 无 N+1 | P0 |
| pageSize≤50 | Gate 通过 | P1 |

### 7.3 QueryService一致性

| 用例 | 预期 | 优先级 |
| --- | --- | --- |
| 保存诊断后列表摘要更新 | QueryService 刷新延迟可接受 | P1 |

### 7.4 集成测试

| 用例 | 预期 | 优先级 |
| --- | --- | --- |
| POST 注册 | `POST /api/app/inpatientDoctorStationEncounterManage/pagePatientQueue` | P0 |

---

## 8. 变更历史

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1.0.3 | 2026-09-09 | IF-IP-DOR-01 队列 DTO 增 `idCard`（完整号不脱敏）；对齐 **AS v1.8 / BR-IP-DOR-012** |
| v1.0.2 | 2026-07-04 | **BR-IP-DOR-009**：IF-IP-DOR-01 增护理级别四字段 + 编排序列 8b；依赖 **`InpatientPatientQueueNursingLevelEnrichAssembler`** |
| v1.0.1 | 2026-05-28 | **L2 对齐**：IF-IP-DOR-01 分页改 **`EncounterContextFacade#pageInpatientDoctorStationQueue`**（§2.2/§2.4/§3.2 编排序列） |（已迁至 EncounterContextReader / application.shared.encounter）
| v* | 2026-05-28 | **ReadModel 弃用对齐**：§2.1/§2.2 增补 `module.querysupport（`EncounterContextFacade`、`*Loader`）；就诊上下文改 `*EncounterContextQueryModel` + Facade；医嘱列表骨架改 `InpatientOrderListSkeleton` + `InpatientOrderListMergeAssembler`/`InpatientOrderLookupLoader`；§3/§4 序列与 QueryService 口径与 `zxplat` 一致 |（已迁至 application.shared / feature support（见 DISSOLUTION.md））（已迁至 EncounterContextReader / application.shared.encounter）
| v1.0 | 2026-05-20 | 首版：IF-IP-DOR-01 目标态契约；对齐 **AS-IP-DOR-001** v1.1、QueryService **Q-IPI-006** v3.3（`organizationId` App 写入）；参照 **AS-IP-NUR-001-SDD** 剔除任务计数编排 |
