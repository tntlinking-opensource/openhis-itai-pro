# 门诊护士站 · 处置 · 应用服务详细设计（SDD）

> **编码**：AS-OP-NUR-001-TRT-SDD  
> **版本**：v1.0.2 | **日期**：2026-06-25  
> **上级**：`AS-OP-NUR-001-门诊护士站-SDD.md` §3.2～§3.7、§5 · `AS-OP-NUR-001-门诊护士站.md`（IF-NUR-02～05、IF-NUR-03B）

---

## 1. 边界

| 项 | 内容 |
| --- | --- |
| **职责** | 处置单三列表、执行记录抽屉、净剩可取消列表、批量执行与批量取消 |
| **IF** | **IF-NUR-02**、**IF-NUR-03**、**IF-NUR-03B**、**IF-NUR-04**、**IF-NUR-05** |
| **不在本包** | 患者队列（**PAT**）、皮试（**STK**） |

## 2. 代码与 HTTP

```text
com.zxplat.domain.application.outpatient.nursestation.treatment/
  service/OutpatientNurseStationTreatmentAppService.java
  mapper/OutpatientNurseStationTreatmentMapper.java
  request/ response/ dto/
```

| IF | 方法 | HTTP |
| --- | --- | --- |
| IF-NUR-02 | `queryEncounterTreatmentSheet` | `POST /api/app/outpatientNurseStationTreatment/queryEncounterTreatmentSheet` |
| IF-NUR-03 | `listProcedureRecordsForNurseStation` | `POST /api/app/outpatientNurseStationTreatment/listProcedureRecordsForNurseStation` |
| IF-NUR-03B | `listNetRemainingProcedureRecordsForNurseStation` | `POST /api/app/outpatientNurseStationTreatment/listNetRemainingProcedureRecordsForNurseStation` |
| IF-NUR-04 | `batchPerformDisposition` | `POST /api/app/outpatientNurseStationTreatment/batchPerformDisposition` |
| IF-NUR-05 | `batchCancelDisposition` | `POST /api/app/outpatientNurseStationTreatment/batchCancelDisposition` |

## 3. 依赖（摘要）

| 依赖 | IF |
| --- | --- |
| `EncounterQueryService` | 02～05 就诊门闸 |
| `ServiceRequestQueryService` / `MedicationRequestQueryService` / `DeviceRequestQueryService` | 02、04、05 |
| `ChargeItemQueryService` | 02 计费聚合；04 已计费门控 |
| `ProcedureQueryService` / `OrderExecutionSheetLoader` | 03、03B、04、05 |
| `ProcedureDomainService` | 04、05 |
| `DeviceDispenseNurseWorkflowsupport / `InventoryItemDomainService` | 04、05 |
| `TerminologyOptionLoader` / `FrequencyOptionLoader` / `ProductDefinitionBatchLoader` | 02 步骤 **8b** 药品用法/频次/单位展示 |
| `OutpatientNurseStationTreatmentMapper` | 全 IF Asm/Cmd 映射 |

完整依赖表与 Gate/Pre/Cmd 逐步序列见上级 **AS-OP-NUR-001-SDD** §2.2、§3.2～§3.7、§5。

## 4. 写侧红线

- IF-NUR-05 **不**调用 `ChargeItemDomainService` 写侧  
- **不**调用 `DeviceRequestDomainService#executeDeviceOrder` / `#revokeDeviceOrderExecution`  
- 耗材路径经 **`DeviceDispenseNurseWorkflowsupport**（**BR-NUR-014**）

---

**变更**：

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1.0.7 | 2026-10-12 | IF-NUR-02 / **BR-NUR-002**：TECH 检验例外排除合管派生「采血费」；代码先行回写 |
| v1.0.6 | 2026-10-09 | IF-NUR-02：TECH 检验仅采血贴；耗材按挂靠科室及祖先 TECH 轴排除（`DeviceSupplyTechVisibilitySupport`）；代码先行回写 |
| v1.0.5 | 2026-06-25 | IF-NUR-03 彻底移除 content_json / performer_list_json 加载与解析；HTTP 仅下发展示列 |
| v1.0.4 | 2026-06-25 | IF-NUR-03 执行记录抽屉：后端直接下发 `orderItemDisplayName` / `actionDisplayText` / `operatorDisplayName` / `operatorRoleDisplayText`，禁止前端解析 JSON |
| v1.0.3 | 2026-06-25 | IF-NUR-02 **BR-NUR-016**：申请单 `remark` + `serviceRequestDetails` 批量回填 |
| v1.0.2 | 2026-06-25 | IF-NUR-02 **BR-NUR-015**：诊疗 `prescriptionNo` 回填与申请单聚邻排序 |
| v1.0.1 | 2026-06-16 | IF-NUR-02 药品行展示字段与步骤 **8b**（见上级 SDD **v1.3.2**） |
| v1.0.0 | 2026-06-10 | 自 **AS-OP-NUR-001-SDD v1.3.0** 拆出处置子域（原 `OutpatientNurseStationAppService` 更名） |
