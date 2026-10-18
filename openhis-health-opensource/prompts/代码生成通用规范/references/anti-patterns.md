# 成码反例库（BAD → GOOD）

> **成码前阅读顺序**：① [comments-gate.md](comments-gate.md)（注释 P0）→ ② 本文件 → ③ [SKILL.md §2](../SKILL.md)。  
> 交付前对照 [checklist.md](../checklist.md) 注释检索 + P0 检索。

---

## C. 注释严重不足（最高频 P0，见 comments-gate）

```java
// BAD — 无 Gate/Pre/Cmd，if 无业务句，JavaDoc 敷衍
/** 保存医嘱。 */
public SaveResponse save(SaveRequest request) {
    gate.assertSame(request);
    if (request.getLines().isEmpty()) {
        throw new BusinessException(EX_EMPTY, "...");
    }
    return mapper.toResponse(domainService.save(mapper.toCmd(request)));
}

// GOOD — 阶段 // + 分支业务句 + 实质 JavaDoc
/**
 * 保存住院医嘱草稿（AS-IP-DOR-003 IF-saveDraft）。
 * 空列表拒绝；已签署行不可被草稿覆盖。
 *
 * @param request 就诊与草稿行；须通过患者-就诊 Gate
 * @return 持久化后的行 id 与状态展示字段
 */
public SaveResponse save(SaveRequest request) {
    // Gate：草稿只能写入当前接诊，避免跨就诊污染医嘱
    gate.assertSame(request.getPatientId(), request.getEncounterId());
    // Pre：至少一行方可落库，与 AS 空列表 EX 一致
    if (request.getLines().isEmpty()) {
        throw new BusinessException(EX_EMPTY, "草稿行不能为空");
    }
    // Cmd：领域服务负责版本与事件时序
    SaveAdviceCommand cmd = mapper.toCommand(request);
    AdviceAggregate saved = domainService.saveAdvice(cmd);
    // Asm：出参仅暴露契约字段，禁止 JSON 串
    return mapper.toSaveResponse(saved);
}
```

**DTO 字段 BAD**：`private Long encounterId;`（无 JavaDoc）  
**GOOD**：`/** 就诊主键，须与 Gate 校验的 encounter 一致。 */ private Long encounterId;`

### C2. 真实模式：Request 只有 `@Schema`、无字段 JavaDoc

> 来源形态：`PageInpatientDoctorStationMedicationAdviceRequest` 等待新增筛选字段时常见。

```java
// BAD — 仅 @Schema，审查者看不懂与 IF/BR 关系
@Schema(description = "true 仅中药")
private Boolean tcmOnly;

@Schema(description = "发放状态 code 集合；空表示不按发放状态过滤")
private List<String> dispenseStatusEnumIn;

// GOOD — JavaDoc 写业务口径；@Schema 仅 OpenAPI，不能替代
/** 为 true 时只查中药医嘱；与 {@code westernOnly} 互斥，由 AppService Gate 校验。 */
@Schema(description = "true 仅中药")
private Boolean tcmOnly;

/**
 * 发放状态过滤（{@code getCode()} 列表）；空或 null 表示不按发放状态收窄。
 *
 * @see com.zxplat.domain.share.enums.DispenseStatus
 */
@Schema(description = "发放状态 code 集合；空表示不按发放状态过滤")
private List<String> dispenseStatusEnumIn;
```

### C3. 真实模式：有 JavaDoc 但方法体「长循环 + if/else」零业务 `//`

> 来源形态：`DoctorStationAdviceAppService#listActivityExecutionLocations` 类——方法头合格，体内多步装配无阶段注释。

```java
// BAD — 方法头有 JavaDoc，但体内无 Gate/Pre/Asm，if/else 无业务句
public ListActivityExecutionLocationsResponse listActivityExecutionLocations(...) {
    OutpatientEncounterContextQueryModel snapshot =
        encounterContextFacade.mustGetOutpatientContextByEncounterId(request.getEncounterId());
    doctorStationAdviceMapper.validatePatientEncounter(...);
    List<ExecutionRuleListQueryModel> ruleModels =
        activityExecutionLocationQueryService.listExecutionRulesByActivityDefinition(...);
    for (ExecutionRuleListQueryModel src : ruleModels) {
        if (src.getExecutionOrgId() != null) {
            line.setExecutionOrgName(orgNameById.get(src.getExecutionOrgId()));
        } else {
            line.setExecutionOrgName(null);
        }
        // ... 其余 setter 仍无业务向 //
    }
    return resp;
}

// GOOD — 同样逻辑，补阶段与分支业务句（仍内联，不拆薄 private）
public ListActivityExecutionLocationsResponse listActivityExecutionLocations(...) {
    // Gate：须为当前门诊就诊且患者卡片一致，防止跨就诊查执行规则
    OutpatientEncounterContextQueryModel snapshot =
        encounterContextFacade.mustGetOutpatientContextByEncounterId(request.getEncounterId());
    doctorStationAdviceMapper.validatePatientEncounter(request.getPatientId(), snapshot.getPatientId());
    // Pre：按诊疗定义拉取可执行科室/地点规则（领域 Query，条件下推 DB）
    List<ExecutionRuleListQueryModel> ruleModels =
        activityExecutionLocationQueryService.listExecutionRulesByActivityDefinition(
            request.getActivityDefinitionId());
    // Pre：批量收集 org/location 主键，一次性解析展示名，避免 N+1
    for (ExecutionRuleListQueryModel row : ruleModels) {
        if (row.getExecutionOrgId() != null) {
            orgIds.add(row.getExecutionOrgId());
        }
        if (row.getExecutionLocationId() != null) {
            locIds.add(row.getExecutionLocationId());
        }
    }
    // Asm：规则行投影为 IF 契约 DTO，org/location 名称为空则展示 null
    for (ExecutionRuleListQueryModel src : ruleModels) {
        // ...
    }
    return resp;
}
```

---

## 1. 禁止 JSON 出参（B.6.2）

```java
// BAD
private String diagnosisJson;
dto.setLocationJson(entity.getLocationJson());

// GOOD
private List<DiagnosisItemDto> diagnoses;
dto.setWardName(loc.getWardName());
dto.setHouseName(loc.getHouseName());
dto.setBedName(loc.getBedName());
// 或经 DiagnosisJsonSnapshotParser 解析后 set 结构化列表
```

---

## 2. App 禁 ICurrentUser（B.6.5）

```java
// BAD
@RequiredArgsConstructor
public class FooAppService {
    private final ICurrentUser currentUser;
    Long op = currentUser.getCurrentUserId();
}

// GOOD
@RequiredArgsConstructor
public class FooAppService {
    private final CurrentOperatorResolver currentOperatorResolver;
    Long op = currentOperatorResolver.requireOperatorId();
}
```

---

## 3. 分页禁手写 0-based 换算（B.10.11）

```java
// BAD
response.setPageNo(page.getNumber() + 1);
response.setContent(rows);
response.setTotalCount(page.getTotalElements());

// GOOD
return pagedResultBuilder.build(response, dtoRows, page);
// 或 build(response, content, totalCount, pageNo, pageSize) — pageNo 已为 1-based
```

---

## 4. 禁 application 下 constants 包（B.10.2.1）

```java
// BAD — 路径 zxplat-application/.../outpatient/financial/charge/constants/
package com.zxplat.domain.application.outpatient.financial.charge.constants;
public interface ChargeConstants { String EX_001 = "EX-001"; }

// GOOD
package com.zxplat.domain.share.constants;
public interface OutpatientChargeConstants { String EX_001 = "EX-001"; }
```

---

## 5. 禁薄包装 private（B.5.1.1）

```java
// BAD
private LocalDateTime domainNow() { return LocalDateTime.now(); }
private XxxStatus resolveStatus(String code) {
    return EnumUtil.fromCode(XxxStatus.class, code);
}

// GOOD — 写在 public 主干，用 // 标业务步骤
LocalDateTime now = LocalDateTime.now();
XxxStatus status = EnumUtil.fromCode(XxxStatus.class, code);
```

---

## 6. 枚举身份只用 getCode()（B.10.8.1）

```java
// BAD
int s = Integer.parseInt(row.getStatusEnum());
if ("3".equals(code)) { ... }

// GOOD
String code = row.getStatusEnum();
XxxStatus status = EnumUtil.requireFromCode(XxxStatus.class, code);
entity.setStatusEnum(RequestStatus.ACTIVE.getCode());
```

---

## 7. Repository 禁 @Query（B.2）

```java
// BAD
@Query("SELECT e FROM Foo e WHERE e.name LIKE %:kw%")
List<Foo> search(@Param("kw") String kw);

// GOOD — 派生方法或 Specification + B.2.1
// cb.like(cb.lower(root.get(FIELD)), SearchCodeUtil.toCaseInsensitiveLikePattern(kw))
```

---

## 8. 领域事件 save 先于 publish（B.14）

```java
// BAD
for (Item item : items) {
    domainEventPublisher.publish(event);
    toSave.add(entity);
}
repository.saveAll(toSave);

// GOOD
repository.saveAll(toSave);
for (DomainEvent event : events) {
    domainEventPublisher.publish(event);
}
```

---

## 9. Excel 导出禁 App 内 POI 逐列（B.10.12）

```java
// BAD
XSSFWorkbook wb = new XSSFWorkbook();
Sheet sheet = wb.createSheet("data");
Row header = sheet.createRow(0);
header.createCell(0).setCellValue("姓名");

// GOOD
List<FooExportRowDto> rows = mapper.toExportRowDtos(enriched);
byte[] body = AnnotatedExcelExporter.exportToXlsxBytes("sheet", rows, FooExportRowDto.class);
```

---

## 10. 禁三元与 inheritDoc 占位（A.8、B.1）

```java
// BAD
String name = (dto.getName() != null) ? dto.getName() : "";
/** {@inheritDoc} */
@Override
public FooDto toDto(Foo entity) { ... }

// GOOD
String name;
if (dto.getName() != null) {
    name = dto.getName();
} else {
    name = "";
}
/** 将 Foo 实体映射为列表行 DTO，含展示名与状态文案。 */
@Override
public FooDto toDto(Foo entity) { ... }
```

---

## 11. busNo 走 BusinessSequenceService（B.10.9）

```java
// BAD
String busNo = "ENC" + System.currentTimeMillis();

// GOOD
String busNo = businessSequenceService.getSeqByDay(
    AggregateTypeConstants.ENCOUNTER, 8);
```

---

## 12. 批量 I/O 禁循环逐条（A.9.1）

```java
// BAD
for (Long id : ids) {
    if (repository.existsByCode(id)) { ... }
}

// GOOD
List<Foo> existing = repository.findByCodeIn(codes);
Map<String, Foo> index = existing.stream()
    .collect(Collectors.toMap(Foo::getCode, Function.identity()));
// 单次遍历入参 + Map 查找
```

---

## 13. 字符串空白归一（trimToNull 唯一入口）

> 细则见 `@zxplat-java-string-utils`。zxplat-chs-jilin 域内等价能力用 `ChsTextSupport.blankToNull`。

```java
// BAD — Apache 或类内重复
String code = org.apache.commons.lang3.StringUtils.trimToNull(raw);
private static String trimToNull(String value) {
    if (value == null || value.isBlank()) {
        return null;
    }
    return value.trim();
}

// BAD — 空白串当合法值入库
command.setBusNo(request.getBusNo());

// GOOD — zxplat-framework static import
import static com.zxplat.framework.core.application.util.StringUtils.trimToNull;

command.setBusNo(trimToNull(request.getBusNo()));
query.setKeyword(trimToNull(safeRequest.getKeyword()));
```

---

## 14. `support / `orchestration` 随意落包（health P0，见 SKILL §2.2.1 / B.6.6～B.6.3a）

```java
// BAD — 住院根目录 support，无 IF 归属
package com.zxplat.domain.application.inpatient.support;

// BAD — 医生站父级 support，过宽
package com.zxplat.domain.application.outpatient.doctorstation.support;

// BAD — 门诊调住院 application support（应 module.orchestration）
import com.zxplat.domain.application.inpatient.nursestation.orderexecute.support.DeviceDispenseNurseWorkflowSupport;

// BAD — 住院调门诊 application support
import com.zxplat.domain.application.outpatient.doctorstation.advice.support.ServiceApplyFormOrchestrationSupport;

// BAD — 护士站父级 support；只读 Gate 应 querysupport
package com.zxplat.domain.application.inpatient.nursestation.support;

// BAD — querysupClient 内写库
package com.zxplat.domain.module.querysupport.nursestation;
@Component
public class InpatientTcmDecoctionExecuteSupport {
    private final ChargeItemDomainService chargeItemDomainService;
}

// BAD — 与 domain.workflow 混淆
package com.zxplat.domain.module.workflow.advice;
```

```java
// GOOD — 跨顶层场景只读展示 → module.common
package com.zxplat.domain.module.common.assembler.inpatient;

// GOOD — 只读 Gate → querysupport
package com.zxplat.domain.module.querysupport.nursestation;

// GOOD — 跨子包写编排 → orchestration（禁止 workflow 包名）
package com.zxplat.domain.module.orchestration.advice;
@Component
public class ServiceApplyFormOrchestrationSupport { ... }

package com.zxplat.domain.module.orchestration.nursestation;
@Component
public class DeviceDispenseNurseWorkflowSupport { ... }

// GOOD — 仅单子模块写编排
package com.zxplat.domain.application.inpatient.nursestation.orderexecute.support;
@Component
public class InpatientTcmDecoctionExecuteSupport { ... }

// GOOD — 纯函数工具 → domain.share.utils（非 support）
package com.zxplat.domain.share.utils;
public final class BloodPressureParser {
    private BloodPressureParser() { }
    public static Optional<BloodPressure> parse(String raw) { ... }
}

// GOOD — AppService 边界 DTO↔Spec；orchestration 只收 Spec
// InpatientDoctorStationAdviceAppService.java
private SkinTestMedicationDraftSpec toSkinTestSpec(SaveInpatientMedicationAdviceDraftLinePayload payload) { ... }
doctorStationSkinTestSyncOrchestrationSupport.syncAfterMedicationDraftLine(
    ChargePolicy.INPATIENT, encounterId, ..., this.toSkinTestSpec(line.getMedicationPayload()));

// GOOD — querysupport 读 enrich + 中性 Row；AppService 映射
// OrderTemplateAppService.java
List<OrderTemplateLineEnrichRow> enrichRows = lines.stream().map(this::toOrderTemplateLineEnrichRow).toList();
orderTemplateLineCatalogEnrichAssembler.enrichLines(enrichRows, null);
```

**成码前自问**：是否只读？是否跨聚合？**几个 application 子包会调？**—— 未答清 **禁止** 新建类。

---

## D. 扩展 Module adapter 反例（booking / einvoice-boss）

> 真源：[模块化开发规范.md](../../../../docs/架构/模块化开发规范.md) §4

```java
// BAD — 完全限定 implements；实现类无 Impl 后缀
public class HealthServiceRequestReader
    implements com.zxplat.framework.booking.contracts.reader.HealthServiceRequestReader { }

// BAD — 同一类既读又写
public class HealthServiceRequestReaderImpl
    implements HealthServiceRequestReader, HealthServiceRequestBookingWriter { }

// BAD — adapter.support / adapter.health 与 domain.support 混淆
package com.zxplat.framework.booking.adapter.support;

// GOOD — import + Impl；读写拆分
import com.zxplat.framework.booking.contracts.reader.HealthServiceRequestReader;

@Component
public class HealthServiceRequestReaderImpl implements HealthServiceRequestReader { }

@Component
public class HealthServiceRequestBookingWriterImpl implements HealthServiceRequestBookingWriter { }

// GOOD — 内部复用放 internal
package com.zxplat.framework.booking.adapter.internal;
public class BookingSlotReaderImpl { }
```

```java
// BAD — 扩展 Module 顶层 application.support（跨场景辅助应放 share）
package com.zxplat.framework.booking.application.support;

// GOOD
package com.zxplat.framework.booking.application.share;
public final class BookingOperatorResolver { }
```
