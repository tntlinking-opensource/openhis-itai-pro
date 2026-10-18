# 门诊护士站 · 患者队列 · 应用服务详细设计（SDD）

> **编码**：AS-OP-NUR-001-PAT-SDD  
> **版本**：v1.0.3 | **日期**：2026-08-19  
> **上级**：`AS-OP-NUR-001-门诊护士站-SDD.md`（总则）· `AS-OP-NUR-001-门诊护士站.md`（IF-NUR-01、**BR-NUR-001**、**BR-NUR-017**）

---

## 1. 边界

| 项 | 内容 |
| --- | --- |
| **职责** | 门诊护士站**左侧患者队列**（处置 Tab、皮试 Tab **共用**） |
| **IF** | **IF-NUR-01** |
| **不在本包** | 处置单、执行、皮试登记 → 见 **TRT** / **STK** 子 SDD |

## 2. 代码与 HTTP

```text
com.zxplat.domain.application.outpatient.nursestation.patient/
  service/OutpatientNurseStationPatientAppService.java
  mapper/OutpatientNurseStationPatientMapper.java
  mapper/OutpatientNurseStationPatientMapperImpl.java
  request/PageNurseStationPatientQueueRequest.java
  response/PageNurseStationPatientQueueResponse.java
  dto/NurseStationPatientQueueItemDto.java
```

| IF | 方法 | HTTP |
| --- | --- | --- |
| IF-NUR-01 | `pageNurseStationPatientQueue` | `POST /api/app/outpatientNurseStationPatient/pageNurseStationPatientQueue` |

## 3. 依赖

| 依赖 | 用途 |
| --- | --- |
| `EncounterContextReader` | `pageNurseStationOutpatientQueue`（**BR-NUR-001** 准入在域 Spec；可选 `orgId`→就诊 `organization_id`；可选 `pendingDeviceOnly`→**BR-NUR-017** EXISTS，分页前生效） |
| `OutpatientNurseStationPatientMapper` | Request ↔ `NurseStationOutpatientQueueQuery`、读模型 → `PagedResult` |
| `EncounterContractDisplayAssembler` | 本页就诊批量回填 **`contractDisplay`**（有医保账户优先合同名） |
| `DeviceRequestQueryService` | 本页就诊批量 `listDeviceRequestsByEncounterIds`（**BR-NUR-017** 红点） |
| `DeviceDispenseQueryService` | 本页就诊批量 `listDeviceDispensesByEncounterIds`（**BR-NUR-017** 发放态） |

## 4. 编排序列（IF-NUR-01）

| 序号 | 阶段 | 说明 |
| --- | --- | --- |
| 1 | Gate | 请求非空；**不**强制开立科室（空=`orgId` 未传=全部） |
| 2 | Pre | `toNurseStationOutpatientQueueQuery`（分页窗 + 可选 `orgId` / `startDate` / `endDate` / `pendingDeviceOnly`） |
| 3 | Pre | `EncounterContextReader#pageNurseStationOutpatientQueue`（**BR-NUR-001**；`pendingDeviceOnly=true` 时叠加待发耗材 EXISTS，**分页在过滤后生效**） |
| 4 | Asm | `toPageNurseStationPatientQueueResponse` |
| 5 | Asm | `EncounterContractDisplayAssembler#mapContractDisplayByEncounterIds` → 行内 `contractDisplay` |
| 6 | Asm | 本页 `listDeviceRequestsByEncounterIds` + `listDeviceDispensesByEncounterIds` → 行内 `hasPendingDevice`（**BR-NUR-017**） |

失败语义：**EX-NUR-101**（见上级 AS §5）。

### 4.1 Request `PageNurseStationPatientQueueRequest`

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `orgId` | Long | N | 开立科室；空/未传=全部；非空等值匹配就诊 `organization_id` |
| `keyword` | String | N | 姓名 / 拼音 / 五笔 OR 匹配 |
| `startDate` | LocalDateTime | N | 就诊开始时间下界（`AbstractPageQuery`） |
| `endDate` | LocalDateTime | N | 就诊开始时间上界 |
| `pendingDeviceOnly` | Boolean | N | **true**=仅仍有待发耗材的就诊（**BR-NUR-017**，分页前 EXISTS）；空/false=不收窄 |
| `pageNo` | Integer | N | 1-based，默认 1 |
| `pageSize` | Integer | N | 页大小 |

### 4.2 Response 行 `NurseStationPatientQueueItemDto`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `encounterId` | Long | 就诊主键 |
| `patientName` | String | 患者姓名 |
| `genderEnum` / `genderEnumText` | String | 性别 code / 展示文案 |
| `birthDate` | LocalDate | 出生日期 |
| `ageText` | String | 派生年龄 |
| `slipNo` | String | 业务流水/挂号展示 |
| `encounterTime` | LocalDateTime | 就诊时间 |
| `contractDisplay` | String | 费用性质（有医保账户优先合同名） |
| `hasPendingDevice` | Boolean | 是否仍有待发耗材（**BR-NUR-017**；对齐处置单侧栏「待发放」） |

### 4.3 待发耗材口径（**BR-NUR-017**）

- **请求纳入**：`DeviceRequest.status_enum = COMPLETED` 且 `refund_device_id` 为空（非退单镜像）。
- **仍待发放**：该请求下**无发放单**，或任一发放单状态为 `DRAFT` / `PREPARATION` / `IN_PROGRESS`（空状态按待发放）。
- **筛选 EXISTS**（`pendingDeviceOnly=true`）：域 Spec `EncounterContextCriteriaSupport#buildNurseStationPendingDeviceExists`：已完成非退单请求，且「存在上述待发态发放」或「不存在任何发放单」。
- **行内红点**：对本页就诊批量读请求/发放后内存判定，供左侧卡片红框与「待发耗材」标签；**选中态不得改用其它描边掩盖提醒**。

---

**变更**：v1.0.3（2026-08-19）**BR-NUR-017**：`pendingDeviceOnly` 分页前筛选；行内 `hasPendingDevice`；日期区间沿用 `startDate`/`endDate`；v1.0.2（2026-08-01）IF-NUR-01 可选开立科室 **`orgId`**（空=全部）；v1.0.1（2026-07-18）队列行增 **`contractDisplay`**（有医保账户优先）；v1.0.0 自 **AS-OP-NUR-001-SDD v1.3.0** 拆出患者子域。
