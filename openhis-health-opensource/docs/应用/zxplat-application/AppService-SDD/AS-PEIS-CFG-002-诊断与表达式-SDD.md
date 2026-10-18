# AS-PEIS-CFG-002 诊断与表达式 · 应用服务详细设计（SDD）

> **编码**：AS-PEIS-CFG-002-SDD · **版本**：v1.2 · **日期**：2026-06-27  
> **对应 AS**：AS-PEIS-CFG-002-诊断与表达式.md（v1.2）  
> **范围**：P0 CFG-08/09；P1 CFG-10～12（CFG-12 = CRT-01）

---

## 1. 实现边界

| 项 | 内容 |
|----|------|
| **模块** | `health-application/peis/config/diagnosis` |
| **类** | `PeisDiagnosisConfigAppService` |
| **方法权威** | DM-CLN-002、DM-PEIS-003、DM-PEIS-006 |
| **实现阶段口径** | **目标态全量契约**（P0 + P1 规则段） |

---

## 2. 代码结构

### 2.1 包路径

```
health-application/.../peis/config/diagnosis/
├── PeisDiagnosisConfigAppService.java
├── request/
│   ├── SaveDiagnosisTemplateRequest.java
│   ├── SaveCommonResultRequest.java
│   └── SaveAutoExpressRequest.java
├── response/
└── mapper/PeisDiagnosisConfigMapper.java + PeisDiagnosisConfigMapperImpl.java
```

### 2.2 领域方法白名单

| 服务 | 方法 | IF |
|------|------|-----|
| ConditionDefinitionDomainService | `save(SaveConditionDefinitionCommand)` | CFG-08 |
| ConditionDefinitionQueryService | `search(ConditionDefQuery, Pageable)` | CFG-08 读 |
| PeisCommonResultDomainService | `save(SaveCommonResultCommand)` | CFG-09 |
| PeisCommonResultQueryService | `listByObservationDefinitionId(Long)` | CFG-09 读 |
| PeisAutoExpressDomainService | `save(SaveAutoExpressCommand)` | CFG-10/11/12 |
| PeisAutoExpressQueryService | `search(AutoExpressQuery, Pageable)` | CFG-10/12 读 |
| PeisAutoExpressEvaluationService | `evaluate(encounterId)` | MAS-06（消费方） |

### 2.3 枚举、字典与值集（DM 与代码可追溯）

| 类别 | 代码实现锚点 | DM 追溯锚点 | 说明 |
|------|-------------|-------------|------|
| 诊断模板 | `SaveDiagnosisTemplateRequest.display` | DM-CLN-002 | CFG-08 |
| dept_conclusion OD | `SaveDiagnosisTemplateRequest.categoryCode` | DM-CLN-005 §4.1 | DES-04 |
| common_result | `SaveCommonResultRequest.valueString` | DM-PEIS-003 §2 | CFG-09 |
| express_script | `SaveAutoExpressRequest.expressScript` | DM-PEIS-006 §2 | CFG-10/12 |
| rule status | `SaveAutoExpressRequest.statusEnum` | DM-PEIS-006 §2 `status_enum` | active/inactive |

---

## 3. 按接口 ID 命名展开

### 3.1 应用服务接口

**HTTP**：`PeisDiagnosisConfigAppService` → `peisDiagnosisConfig`

| IF | 方法 | 中文 | HTTP |
|----|------|------|------|
| CFG-08 | `searchDiagnosisTemplate` | 查询诊断库 | `POST /api/app/peisDiagnosisConfig/searchDiagnosisTemplate` |
| CFG-08 | `saveDiagnosisTemplate` | 保存诊断库 | `POST /api/app/peisDiagnosisConfig/saveDiagnosisTemplate` |
| CFG-09 | `searchCommonResult` | 查询常见结果 | `POST /api/app/peisDiagnosisConfig/searchCommonResult` |
| CFG-09 | `saveCommonResult` | 保存常见结果 | `POST /api/app/peisDiagnosisConfig/saveCommonResult` |
| CFG-12 | `searchCriticalValueRule` | 查询危急值规则 | `POST /api/app/peisDiagnosisConfig/searchCriticalValueRule` |
| CFG-12 | `saveCriticalValueRule` | 保存危急值规则 | `POST /api/app/peisDiagnosisConfig/saveCriticalValueRule` |
| CFG-10 | `saveAutoExpress` | 保存诊断表达式 | `POST /api/app/peisDiagnosisConfig/saveAutoExpress` |

---

### 3.2 IF-PEIS-CFG-08 `saveDiagnosisTemplate`

#### Request

| 字段 | 类型 | 必填 |
|------|------|:--:|
| `definitionId` | `Long` | 否 |
| `code` | `String` | 是 |
| `display` | `String` | 是 |
| `categoryCode` | `String` | 否 |
| `statusEnum` | `String` | 是 |

#### 领域编排序列

| 序号 | 阶段 | 调用 | 说明 |
|------|------|------|------|
| 1 | Pre | 在检引用检查 | TEMPLATE_IN_USE |
| 2 | Cmd | `ConditionDefinitionDomainService#save(...)` | **写库点** ConditionDefinition |
| 3 | Asm | AuditLog | |

---

### 3.3 IF-PEIS-CFG-09 `saveCommonResult`

#### Request

| 字段 | 类型 | 必填 |
|------|------|:--:|
| `id` | `Long` | 否 |
| `observationDefinitionId` | `Long` | 否 |
| `deptOrgId` | `Long` | 否 |
| `display` | `String` | 是 |
| `valueString` | `String` | 是 |
| `interpretationCode` | `String` | 否 |

#### 领域编排序列

| 序号 | 阶段 | 调用 | 说明 |
|------|------|------|------|
| 1 | Cmd | `PeisCommonResultDomainService#save(SaveCommonResultCommand)` | **写库点** peis_common_result |

---

### 3.4 IF-PEIS-CFG-12 `saveCriticalValueRule`（CRT-01）

#### Request

| 字段 | 类型 | 必填 | 说明 |
|------|------|:--:|------|
| `id` | `Long` | 否 | |
| `name` | `String` | 是 | 规则名 |
| `expressScript` | `String` | 是 | 表达式 |
| `targetConditionDefId` | `Long` | 否 | 命中诊断 |
| `statusEnum` | `String` | 是 | active/inactive |
| `ruleTypeCode` | `String` | 是 | `CRITICAL_VALUE`（区分 CFG-10） |

#### 失败码

| 码 | 场景 |
|----|------|
| `RULE_SCRIPT_INVALID` | 表达式校验失败 |

#### 领域编排序列

| 序号 | 阶段 | 调用 | 说明 |
|------|------|------|------|
| 1 | Gate | expressScript 沙箱语法校验 | P1 |
| 2 | Cmd | `PeisAutoExpressDomainService#save(SaveAutoExpressCommand)` | **写库点** peis_auto_express |
| 3 | Asm | AuditLog | CRT-001 消费 |

---

### 3.5 IF-PEIS-CFG-10 `saveAutoExpress`（总检表达式，P1）

同 §3.4，`ruleTypeCode=AUTO_DIAGNOSIS`；MAS-06 调用 `PeisAutoExpressEvaluationService#evaluate`。

---

## 4. QueryService 与领域事件

- CFG-08/09 保存后 DES 通过 QueryService 刷新；CFG-12 保存后 CRT 规则引擎热加载（P2 可选）。

---

## 5. 写库顺序

| IF | 表 |
|----|-----|
| CFG-08 | ConditionDefinition |
| CFG-09 | peis_common_result |
| CFG-10/12 | peis_auto_express |

---

## 6. 非功能性需求实现

### 6.1 幂等性

- save 按 id 或 code/name upsert。

### 6.2 并发控制

- 乐观锁 version。

### 6.3 重试与容错

- 表达式校验失败不落库。

### 6.4 数据脱敏

- 无 P0。

### 6.5 审计日志

- 全部 save IF 记 AuditLog。

---

## 7. 测试要点

### 7.1 功能测试

| 场景 | 层级 | 自动化 | 优先级 |
|------|------|--------|:--:|
| DES-03 套用 | CFG-09 | 集成 | P0 |
| DES-04 模板 | CFG-08 | API | P0 |
| CRT 规则命中 | CFG-12 → CRT-002 | 集成 | P1 |
| MAS 表达式 | CFG-10 | 集成 | P1 |

### 7.2～7.5

- 与 DES/CRT/MAS 联调；Gap 见 AS §3。

---

## 8. 变更历史

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.2 | 2026-06-27 | CFG-08/12 全展开；CRT-01；对齐 AS v1.2 |
| v1.1 | 2026-06-27 | 补 §4～§7；HTTP |
| v1.0 | 2026-06-27 | P0 段 SDD |
