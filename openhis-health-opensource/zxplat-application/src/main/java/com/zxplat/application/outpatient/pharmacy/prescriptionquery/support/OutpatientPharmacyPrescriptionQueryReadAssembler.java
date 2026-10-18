/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.pharmacy.prescriptionquery.support;

import com.zxplat.application.outpatient.pharmacy.prescriptionquery.dto.OutpatientPharmacyPrescriptionDrugLineDto;
import com.zxplat.application.shared.option.FrequencyOptionLoader;
import com.zxplat.application.shared.option.TerminologyOptionLoader;
import com.zxplat.application.shared.product.MedicationMerchandiseDisplaySupport;
import com.zxplat.domain.administration.chargeitem.entity.ChargeItem;
import com.zxplat.domain.administration.encounter.queries.model.OutpatientEncounterContextQueryModel;
import com.zxplat.domain.administration.practitioner.entity.Practitioner;
import com.zxplat.domain.clinical.conditiondefinition.queries.model.ConditionDefinitionBatchItemQueryModel;
import com.zxplat.domain.custom.supplier.service.SupplierQueryService;
import com.zxplat.domain.financial.contract.queries.model.ContractListItemQueryModel;
import com.zxplat.domain.medication.medicationdefinition.entity.MedicationDefinition;
import com.zxplat.domain.medication.medicationdispense.entity.MedicationDispense;
import com.zxplat.domain.medication.medicationrequest.queries.model.MedicationRequestListQueryModel;
import com.zxplat.domain.share.constants.GeneralSharedConstants;
import com.zxplat.domain.share.constants.TableNameConstants;
import com.zxplat.domain.share.constants.TerminologyCodeSystemNameConstants;
import com.zxplat.domain.share.enums.ChargeItemStatus;
import com.zxplat.domain.share.enums.YbMedType;
import com.zxplat.domain.share.utils.EncounterDiagnosisIdsSupport;
import com.zxplat.domain.share.utils.EnumUtil;
import com.zxplat.framework.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 门诊处方查询平铺行装配（AS-OP-PHA-005 · IF-OPQ-02）。
 *
 * <p>职责：页内批量索引 → 明细行 DTO（方头 + 详情用药字段）；方级 {@code infusionPrescriptionFlag}
 * 按处方号对本页行做目录 {@code inject_flag} ANY（跨页拆分方时以本页可见行为准）。
 * 方头 {@code diagnosisName} 为同处方号下各药行绑定诊断<strong>并集</strong>展示名。</p>
 */
@Component
@RequiredArgsConstructor
public class OutpatientPharmacyPrescriptionQueryReadAssembler {

    /** 术语码 → 文案（单位、处方类别、途径、剂量单位、用法说明）。 */
    private final TerminologyOptionLoader terminologyOptionLoader;

    /** 频次主数据码 → 文案（cus_frequency）。 */
    private final FrequencyOptionLoader frequencyOptionLoader;

    /** 费用性质分类文案解析。 */
    private final OutpatientPharmacyPrescriptionQuerySupport prescriptionQuerySupport;

    /** 生产厂家名称：按药品 {@code manufacturerId} 批量解析 cus_supplier.name。 */
    private final SupplierQueryService supplierQueryService;

    /**
     * 装配平铺明细行。
     *
     * @param requestRows                 领域分页药品申请读模型
     * @param medicationById              药品定义索引
     * @param chargeByRequestId           药品申请 id → 收费明细（锚点 med_medication_request）
     * @param dispenseByRequestId         药品申请 id → 代表发放行
     * @param conditionById               诊断定义索引
     * @param conditionDefinitionIdByEncounterDiagnosisId 就诊诊断 id → 诊断定义 id
     * @param medTypeByEncounterDiagnosisId 就诊诊断 id → med_type
     * @param contractById                费用性质索引
     * @param encounterContextById        门诊就诊上下文
     * @param practitionerNameById        开单医生姓名
     * @param organizationNameById        开单科室名称（药嘱 {@code org_id}）
     * @return 平铺行；入参空时返回空列表
     */
    public List<OutpatientPharmacyPrescriptionDrugLineDto> assembleRows(
        List<MedicationRequestListQueryModel> requestRows,
        Map<Long, MedicationDefinition> medicationById,
        Map<Long, ChargeItem> chargeByRequestId,
        Map<Long, MedicationDispense> dispenseByRequestId,
        Map<Long, ConditionDefinitionBatchItemQueryModel> conditionById,
        Map<Long, Long> conditionDefinitionIdByEncounterDiagnosisId,
        Map<Long, String> medTypeByEncounterDiagnosisId,
        Map<Long, ContractListItemQueryModel> contractById,
        Map<Long, OutpatientEncounterContextQueryModel> encounterContextById,
        Map<Long, String> practitionerNameById,
        Map<Long, String> organizationNameById) {
        if (requestRows == null || requestRows.isEmpty()) {
            return List.of();
        }
        Map<String, String> rxTypeLabelByCode =
            this.terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.RX_TYPE_CODE);
        Map<String, String> unitLabelByCode =
            this.terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.UNIT_CODE);
        Map<String, String> methodLabelByCode =
            this.terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.METHOD_CODE);
        Map<String, String> doseUnitLabelByCode =
            this.terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.DOSE_UNIT);
        Map<String, String> dosageInstructionLabelByCode =
            this.terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.DOSAGE_INSTRUCTION);
        Map<String, String> rateLabelByCode = this.frequencyOptionLoader.buildRateLabelMap();
        Map<String, Boolean> infusionByPrescriptionNo =
            this.buildInfusionFlagByPrescriptionNo(requestRows, medicationById);
        // Pre：同处方号诊断并集文案，覆盖行方头 diagnosisName
        Map<String, String> unionDiagnosisByPrescriptionNo = this.buildUnionDiagnosisNameByPrescriptionNo(
            requestRows, conditionById, conditionDefinitionIdByEncounterDiagnosisId);
        // Pre：页内生产厂家 id → 名称（目录已无 manufacturer_text）
        Map<Long, String> manufacturerNameById = this.buildManufacturerNameById(medicationById);
        List<OutpatientPharmacyPrescriptionDrugLineDto> rows = new ArrayList<>(requestRows.size());
        // Loop：逐药品申请行装配方头+明细展示字段
        for (MedicationRequestListQueryModel requestRow : requestRows) {
            if (requestRow == null) {
                continue;
            }
            OutpatientPharmacyPrescriptionDrugLineDto dto = this.assembleOneRow(
                requestRow,
                medicationById,
                chargeByRequestId,
                dispenseByRequestId,
                conditionById,
                conditionDefinitionIdByEncounterDiagnosisId,
                medTypeByEncounterDiagnosisId,
                contractById,
                encounterContextById,
                practitionerNameById,
                organizationNameById,
                manufacturerNameById,
                rxTypeLabelByCode,
                unitLabelByCode,
                methodLabelByCode,
                doseUnitLabelByCode,
                dosageInstructionLabelByCode,
                rateLabelByCode,
                infusionByPrescriptionNo);
            // 方头诊断：同 prescription_no 绑定诊断全集（有交集同方后展示并集）
            String prescriptionNo = dto.getPrescriptionNo();
            if (prescriptionNo != null) {
                String unionName = unionDiagnosisByPrescriptionNo.get(prescriptionNo);
                if (StringUtils.isNotEmpty(unionName)) {
                    dto.setDiagnosisName(unionName);
                }
            }
            rows.add(dto);
        }
        return rows;
    }

    /**
     * 按处方号汇总本页各药行 {@code encounter_diagnosis_ids} 并集并解析展示名。
     *
     * @param requestRows 药品申请读模型
     * @param conditionById 诊断定义索引
     * @param conditionDefinitionIdByEncounterDiagnosisId 就诊诊断 id → 定义 id
     * @return 处方号 → 顿号拼接诊断名
     */
    private Map<String, String> buildUnionDiagnosisNameByPrescriptionNo(
        List<MedicationRequestListQueryModel> requestRows,
        Map<Long, ConditionDefinitionBatchItemQueryModel> conditionById,
        Map<Long, Long> conditionDefinitionIdByEncounterDiagnosisId) {
        Map<String, List<String>> idsTextsByPrescriptionNo = new LinkedHashMap<>();
        for (MedicationRequestListQueryModel requestRow : requestRows) {
            if (requestRow == null) {
                continue;
            }
            String prescriptionNo = requestRow.getPrescriptionNo();
            if (StringUtils.isEmpty(prescriptionNo)) {
                continue;
            }
            List<String> bucket = idsTextsByPrescriptionNo.get(prescriptionNo);
            if (bucket == null) {
                bucket = new ArrayList<>();
                idsTextsByPrescriptionNo.put(prescriptionNo, bucket);
            }
            bucket.add(requestRow.getEncounterDiagnosisIds());
        }
        Map<String, String> result = new HashMap<>();
        for (Map.Entry<String, List<String>> entry : idsTextsByPrescriptionNo.entrySet()) {
            String unionIds = EncounterDiagnosisIdsSupport.unionOrderedIdsTexts(entry.getValue());
            String display = EncounterDiagnosisIdsSupport.joinOrderedDisplayNames(
                unionIds,
                encounterDiagnosisId -> {
                    if (conditionDefinitionIdByEncounterDiagnosisId == null || conditionById == null) {
                        return null;
                    }
                    Long definitionId = conditionDefinitionIdByEncounterDiagnosisId.get(encounterDiagnosisId);
                    if (definitionId == null) {
                        return null;
                    }
                    ConditionDefinitionBatchItemQueryModel condition = conditionById.get(definitionId);
                    if (condition == null) {
                        return null;
                    }
                    return condition.getName();
                });
            if (StringUtils.isNotEmpty(display)) {
                result.put(entry.getKey(), display);
            }
        }
        return result;
    }

    /**
     * 由医生主档列表构建 practitionerId → 姓名索引。
     *
     * @param practitioners 主档列表
     * @return 姓名 Map
     */
    public Map<Long, String> indexPractitionerNames(List<Practitioner> practitioners) {
        Map<Long, String> result = new HashMap<>();
        if (practitioners == null) {
            return result;
        }
        for (Practitioner practitioner : practitioners) {
            if (practitioner != null && practitioner.getId() != null) {
                result.put(practitioner.getId(), practitioner.getName());
            }
        }
        return result;
    }

    /**
     * 由收费明细列表构建药品申请 id → ChargeItem（仅 med_medication_request 锚点；同申请多行取首条）。
     *
     * @param chargeItems 收费明细
     * @return 索引
     */
    public Map<Long, ChargeItem> indexChargeByMedicationRequestId(List<ChargeItem> chargeItems) {
        Map<Long, ChargeItem> result = new HashMap<>();
        if (chargeItems == null) {
            return result;
        }
        for (ChargeItem chargeItem : chargeItems) {
            if (chargeItem == null || chargeItem.getServiceId() == null) {
                continue;
            }
            // 仅保留药品申请锚点费用行，排除其它 service_table
            if (!TableNameConstants.MED_MEDICATION_REQUEST.equals(chargeItem.getServiceTable())) {
                continue;
            }
            if (!result.containsKey(chargeItem.getServiceId())) {
                result.put(chargeItem.getServiceId(), chargeItem);
            }
        }
        return result;
    }

    /**
     * 由发放列表构建药品申请 id → 代表发放行（同申请多段取首条有效行）。
     *
     * @param dispenses 发放实体
     * @return 索引
     */
    public Map<Long, MedicationDispense> indexDispenseByMedicationRequestId(List<MedicationDispense> dispenses) {
        Map<Long, MedicationDispense> result = new HashMap<>();
        if (dispenses == null) {
            return result;
        }
        for (MedicationDispense dispense : dispenses) {
            if (dispense == null || dispense.getMedReqId() == null) {
                continue;
            }
            if (!result.containsKey(dispense.getMedReqId())) {
                result.put(dispense.getMedReqId(), dispense);
            }
        }
        return result;
    }

    /**
     * 药品定义列表 → id 索引。
     *
     * @param definitions 药品定义
     * @return 索引
     */
    public Map<Long, MedicationDefinition> indexMedicationById(List<MedicationDefinition> definitions) {
        Map<Long, MedicationDefinition> result = new HashMap<>();
        if (definitions == null) {
            return result;
        }
        for (MedicationDefinition definition : definitions) {
            if (definition != null && definition.getId() != null) {
                result.put(definition.getId(), definition);
            }
        }
        return result;
    }

    /**
     * 诊断批量项 → id 索引。
     *
     * @param conditions 诊断定义
     * @return 索引
     */
    public Map<Long, ConditionDefinitionBatchItemQueryModel> indexConditionById(
        List<ConditionDefinitionBatchItemQueryModel> conditions) {
        Map<Long, ConditionDefinitionBatchItemQueryModel> result = new HashMap<>();
        if (conditions == null) {
            return result;
        }
        for (ConditionDefinitionBatchItemQueryModel condition : conditions) {
            if (condition != null && condition.getId() != null) {
                result.put(condition.getId(), condition);
            }
        }
        return result;
    }

    /**
     * 费用性质列表 → id 索引。
     *
     * @param contracts 合同简表
     * @return 索引
     */
    public Map<Long, ContractListItemQueryModel> indexContractById(List<ContractListItemQueryModel> contracts) {
        Map<Long, ContractListItemQueryModel> result = new HashMap<>();
        if (contracts == null) {
            return result;
        }
        for (ContractListItemQueryModel contract : contracts) {
            if (contract != null && contract.getId() != null) {
                result.put(contract.getId(), contract);
            }
        }
        return result;
    }

    /**
     * 门诊上下文列表 → encounterId 索引。
     *
     * @param contexts 门诊上下文
     * @return 索引
     */
    public Map<Long, OutpatientEncounterContextQueryModel> indexEncounterContextById(
        List<OutpatientEncounterContextQueryModel> contexts) {
        Map<Long, OutpatientEncounterContextQueryModel> result = new HashMap<>();
        if (contexts == null) {
            return result;
        }
        for (OutpatientEncounterContextQueryModel context : contexts) {
            if (context != null && context.getEncounterId() != null) {
                result.put(context.getEncounterId(), context);
            }
        }
        return result;
    }

    /**
     * 本页处方号 → 方级含输液标志（目录 inject_flag ANY）。
     *
     * @param requestRows    药品申请行
     * @param medicationById 药品定义索引
     * @return 处方号 → 是否含输液
     */
    private Map<String, Boolean> buildInfusionFlagByPrescriptionNo(
        List<MedicationRequestListQueryModel> requestRows,
        Map<Long, MedicationDefinition> medicationById) {
        Map<String, Boolean> flags = new HashMap<>();
        for (MedicationRequestListQueryModel row : requestRows) {
            if (row == null || StringUtils.isEmpty(row.getPrescriptionNo())) {
                continue;
            }
            flags.putIfAbsent(row.getPrescriptionNo(), Boolean.FALSE);
        }
        for (MedicationRequestListQueryModel row : requestRows) {
            if (row == null || StringUtils.isEmpty(row.getPrescriptionNo())) {
                continue;
            }
            if (row.getMedicationId() == null || medicationById == null) {
                continue;
            }
            MedicationDefinition definition = medicationById.get(row.getMedicationId());
            if (definition != null && Boolean.TRUE.equals(definition.getInjectFlag())) {
                flags.put(row.getPrescriptionNo(), Boolean.TRUE);
            }
        }
        return flags;
    }

    /**
     * 装配单行明细 DTO（方头字段 + 药品行字段；不含业务流程分支）。
     *
     * @param requestRow               药品申请读模型；调用方保证非空
     * @param medicationById           药品定义索引
     * @param chargeByRequestId        收费明细索引
     * @param dispenseByRequestId      发放代表行索引
     * @param conditionById            诊断定义索引
     * @param conditionDefinitionIdByEncounterDiagnosisId 就诊诊断 id → 诊断定义 id
     * @param medTypeByEncounterDiagnosisId 就诊诊断 id → med_type
     * @param contractById             费用性质索引
     * @param encounterContextById     门诊就诊上下文
     * @param practitionerNameById     开单医生姓名
     * @param organizationNameById     开单科室名称
     * @param manufacturerNameById     生产厂家 id → 名称（页内批量预热）
     * @param rxTypeLabelByCode        处方类别术语文案
     * @param unitLabelByCode          单位术语文案
     * @param methodLabelByCode        用药途径术语文案
     * @param doseUnitLabelByCode      剂量单位术语文案
     * @param dosageInstructionLabelByCode 用法说明术语文案
     * @param rateLabelByCode          用药频次文案（cus_frequency）
     * @param infusionByPrescriptionNo 方级含输液标志
     * @return 平铺行 DTO
     */
    private OutpatientPharmacyPrescriptionDrugLineDto assembleOneRow(
        MedicationRequestListQueryModel requestRow,
        Map<Long, MedicationDefinition> medicationById,
        Map<Long, ChargeItem> chargeByRequestId,
        Map<Long, MedicationDispense> dispenseByRequestId,
        Map<Long, ConditionDefinitionBatchItemQueryModel> conditionById,
        Map<Long, Long> conditionDefinitionIdByEncounterDiagnosisId,
        Map<Long, String> medTypeByEncounterDiagnosisId,
        Map<Long, ContractListItemQueryModel> contractById,
        Map<Long, OutpatientEncounterContextQueryModel> encounterContextById,
        Map<Long, String> practitionerNameById,
        Map<Long, String> organizationNameById,
        Map<Long, String> manufacturerNameById,
        Map<String, String> rxTypeLabelByCode,
        Map<String, String> unitLabelByCode,
        Map<String, String> methodLabelByCode,
        Map<String, String> doseUnitLabelByCode,
        Map<String, String> dosageInstructionLabelByCode,
        Map<String, String> rateLabelByCode,
        Map<String, Boolean> infusionByPrescriptionNo) {
        OutpatientPharmacyPrescriptionDrugLineDto dto = new OutpatientPharmacyPrescriptionDrugLineDto();
        dto.setMedicationRequestId(requestRow.getId());
        dto.setPrescriptionNo(requestRow.getPrescriptionNo());
        dto.setEncounterId(requestRow.getEncounterId());
        dto.setPatientId(requestRow.getPatientId());
        dto.setOrderingPractitionerId(requestRow.getRequesterId());
        // 开单科室：只读药嘱 org_id（BR-MRQ-029，禁止用 perform_location）
        dto.setOrderingDepartmentId(requestRow.getOrgId());
        Long firstEncounterDiagnosisId = EncounterDiagnosisIdsSupport.firstId(requestRow.getEncounterDiagnosisIds());
        Long resolvedConditionDefinitionId = null;
        if (firstEncounterDiagnosisId != null && conditionDefinitionIdByEncounterDiagnosisId != null) {
            resolvedConditionDefinitionId =
                conditionDefinitionIdByEncounterDiagnosisId.get(firstEncounterDiagnosisId);
        }
        dto.setConditionDefinitionId(resolvedConditionDefinitionId);
        dto.setRxTypeCode(requestRow.getRxTypeCode());
        dto.setContractId(requestRow.getContractId());
        dto.setMedicationId(requestRow.getMedicationId());
        dto.setQuantity(requestRow.getQuantity());
        dto.setUnitCode(requestRow.getUnitCode());
        dto.setSortNumber(requestRow.getSortNumber());
        // 详情用药字段：分组 / 途径 / 频次 / 剂量 / 天数 / 用法说明 / 皮试
        dto.setGroupId(requestRow.getGroupId());
        dto.setMethodCode(requestRow.getMethodCode());
        dto.setRateCode(requestRow.getRateCode());
        dto.setDose(requestRow.getDose());
        dto.setDoseUnitCode(requestRow.getDoseUnitCode());
        dto.setDispensePerDuration(requestRow.getDispensePerDuration());
        // 中药付数：方头字段；西药行通常为空
        dto.setChineseHerbsDoseQuantity(requestRow.getChineseHerbsDoseQuantity());
        dto.setDosageInstructionCode(requestRow.getDosageInstruction());
        dto.setSkinTestFlag(requestRow.getSkinTestFlag());
        if (requestRow.getSkinTestFlag() != null) {
            if (Boolean.TRUE.equals(requestRow.getSkinTestFlag())) {
                dto.setSkinTestFlagText("是");
            } else {
                dto.setSkinTestFlagText("否");
            }
        }
        // 开立时间主轴：opened 空则回落 authored；两列尽量都填供追溯
        LocalDateTime openedAt = requestRow.getReqOpenedTime();
        if (openedAt == null) {
            openedAt = requestRow.getReqAuthoredTime();
        }
        if (openedAt != null) {
            dto.setReqOpenedTime(openedAt.format(GeneralSharedConstants.DATE_TIME_FORMATTER));
        }
        if (requestRow.getReqAuthoredTime() != null) {
            dto.setReqAuthoredTime(
                requestRow.getReqAuthoredTime().format(GeneralSharedConstants.DATE_TIME_FORMATTER));
        }
        // 方级含输液：同源目录 inject_flag ANY（BR-OPQ-013）
        if (requestRow.getPrescriptionNo() != null && infusionByPrescriptionNo != null) {
            dto.setInfusionPrescriptionFlag(infusionByPrescriptionNo.get(requestRow.getPrescriptionNo()));
        }
        // 处方类别术语文案
        if (requestRow.getRxTypeCode() != null && rxTypeLabelByCode != null) {
            dto.setRxTypeText(rxTypeLabelByCode.get(requestRow.getRxTypeCode()));
        }
        // 计量单位术语文案（总量单位；dose_unit 码表未命中时回落 unit_code）
        if (requestRow.getUnitCode() != null) {
            dto.setUnitText(this.resolveUnitDisplayText(
                requestRow.getUnitCode(), unitLabelByCode, doseUnitLabelByCode));
        }
        // 用药途径术语文案
        if (requestRow.getMethodCode() != null && methodLabelByCode != null) {
            dto.setMethodText(methodLabelByCode.get(requestRow.getMethodCode()));
        }
        // 剂量单位术语文案（优先 dose_unit；未命中回落 unit_code，与医生站开立口径一致）
        if (requestRow.getDoseUnitCode() != null) {
            dto.setDoseUnitText(this.resolveUnitDisplayText(
                requestRow.getDoseUnitCode(), doseUnitLabelByCode, unitLabelByCode));
        }
        // 用法说明术语文案
        if (requestRow.getDosageInstruction() != null && dosageInstructionLabelByCode != null) {
            dto.setDosageInstructionText(dosageInstructionLabelByCode.get(requestRow.getDosageInstruction()));
        }
        // 用药频次：cus_frequency 主数据文案
        if (requestRow.getRateCode() != null && rateLabelByCode != null) {
            dto.setRateText(rateLabelByCode.get(requestRow.getRateCode()));
        }
        // 门诊号、患者姓名、身份证明文、电话（就诊上下文可回落 patientId；列表脱敏由前端负责）
        if (requestRow.getEncounterId() != null && encounterContextById != null) {
            OutpatientEncounterContextQueryModel context = encounterContextById.get(requestRow.getEncounterId());
            if (context != null) {
                dto.setEncounterBusNo(context.getEncounterBusNo());
                dto.setPatientName(context.getPatientName());
                dto.setIdCard(context.getIdCard());
                dto.setPatientPhone(context.getPhone());
                if (dto.getPatientId() == null) {
                    dto.setPatientId(context.getPatientId());
                }
            }
        }
        // 医保类别：绑定诊断首位 med_type → YbMedType 文案
        if (firstEncounterDiagnosisId != null && medTypeByEncounterDiagnosisId != null) {
            String medTypeCode = medTypeByEncounterDiagnosisId.get(firstEncounterDiagnosisId);
            if (StringUtils.isNotEmpty(medTypeCode)) {
                dto.setMedTypeCode(medTypeCode);
                YbMedType medTypeEnum = EnumUtil.fromCode(YbMedType.class, medTypeCode.trim());
                if (medTypeEnum != null) {
                    dto.setMedTypeText(medTypeEnum.getDisplay());
                }
            }
        }
        // 开单医生姓名
        if (requestRow.getRequesterId() != null && practitionerNameById != null) {
            dto.setOrderingPractitionerName(practitionerNameById.get(requestRow.getRequesterId()));
        }
        // 开单科室名称（org_id → Organization.name）
        if (requestRow.getOrgId() != null && organizationNameById != null) {
            dto.setOrderingDepartmentName(organizationNameById.get(requestRow.getOrgId()));
        }
        // 诊断名称：按绑定 ids 有序全量拼接（一药多诊断；conditionDefinitionId 仍取首位供兼容）
        dto.setDiagnosisName(EncounterDiagnosisIdsSupport.joinOrderedDisplayNames(
            requestRow.getEncounterDiagnosisIds(),
            encounterDiagnosisId -> {
                if (conditionDefinitionIdByEncounterDiagnosisId == null || conditionById == null) {
                    return null;
                }
                Long definitionId = conditionDefinitionIdByEncounterDiagnosisId.get(encounterDiagnosisId);
                if (definitionId == null) {
                    return null;
                }
                ConditionDefinitionBatchItemQueryModel condition = conditionById.get(definitionId);
                if (condition == null) {
                    return null;
                }
                return condition.getName();
            }));
        // 药品名称/规格/厂家/本行注射标志（查处方：有商品名则括号显示；打印勿复用本口径）
        if (requestRow.getMedicationId() != null && medicationById != null) {
            MedicationDefinition definition = medicationById.get(requestRow.getMedicationId());
            if (definition != null) {
                dto.setMedicationName(MedicationMerchandiseDisplaySupport.formatDisplayName(
                    definition.getName(), definition.getMerchandiseName()));
                dto.setMedicationSpec(definition.getTotalVolume());
                // 厂家展示名：manufacturerId → cus_supplier.name（页内批量预热）
                if (definition.getManufacturerId() != null && manufacturerNameById != null) {
                    String manufacturerName = manufacturerNameById.get(definition.getManufacturerId());
                    if (StringUtils.isNotEmpty(manufacturerName)) {
                        dto.setManufacturerText(manufacturerName);
                    }
                }
                dto.setInjectFlag(definition.getInjectFlag());
            }
        }
        // 收费状态 / 单价 / 是否计价（无费用行时留空，不匹配非空收费筛）
        if (requestRow.getId() != null && chargeByRequestId != null) {
            ChargeItem chargeItem = chargeByRequestId.get(requestRow.getId());
            if (chargeItem != null) {
                dto.setChargeStatusEnum(chargeItem.getStatusEnum());
                dto.setChargeStatusText(this.resolveChargeStatusText(chargeItem.getStatusEnum()));
                dto.setUnitPrice(chargeItem.getUnitPrice());
                // 非草稿即进入收费窗口，视为已计价
                boolean chargeable = !ChargeItemStatus.DRAFT.getCode().equals(chargeItem.getStatusEnum());
                dto.setChargeableFlag(chargeable);
                if (chargeable) {
                    dto.setChargeableFlagText("是");
                } else {
                    dto.setChargeableFlagText("否");
                }
            }
        }
        // 发药状态（多段发放取代表行）
        if (requestRow.getId() != null && dispenseByRequestId != null) {
            MedicationDispense dispense = dispenseByRequestId.get(requestRow.getId());
            if (dispense != null && dispense.getStatusEnum() != null) {
                dto.setDispenseStatusEnum(dispense.getStatusEnum().getCode());
                dto.setDispenseStatusText(dispense.getStatusEnum().getDisplay());
            }
        }
        // 费用性质分类；分类码未识别时回落合同名称，避免列表空白
        if (requestRow.getContractId() != null && contractById != null) {
            ContractListItemQueryModel contract = contractById.get(requestRow.getContractId());
            if (contract != null) {
                dto.setContractCategoryCode(contract.getCategoryEnum());
                String categoryText =
                    this.prescriptionQuerySupport.resolveContractCategoryText(contract.getCategoryEnum());
                if (StringUtils.isEmpty(categoryText)) {
                    categoryText = contract.getName();
                }
                dto.setContractCategoryText(categoryText);
            }
        }
        return dto;
    }

    /**
     * 从本页药品定义收集 {@code manufacturerId}，批量解析生产厂家名称。
     *
     * @param medicationById 药品定义索引；可空
     * @return manufacturerId → supplier.name；无厂家时返回空 Map
     */
    private Map<Long, String> buildManufacturerNameById(Map<Long, MedicationDefinition> medicationById) {
        if (medicationById == null || medicationById.isEmpty()) {
            return Map.of();
        }
        Set<Long> manufacturerIds = new LinkedHashSet<>();
        for (MedicationDefinition definition : medicationById.values()) {
            if (definition == null || definition.getManufacturerId() == null) {
                continue;
            }
            manufacturerIds.add(definition.getManufacturerId());
        }
        if (manufacturerIds.isEmpty()) {
            return Map.of();
        }
        return supplierQueryService.mapNamesByIds(new ArrayList<>(manufacturerIds));
    }

    /**
     * 解析单位展示文案：主码表优先，次码表回落（剂量单位与包装单位码常混用）。
     *
     * @param unitCode     单位 code
     * @param primaryMap   优先术语映射
     * @param fallbackMap  回落术语映射
     * @return 展示文案；皆未命中时 null
     */
    private String resolveUnitDisplayText(
        String unitCode,
        Map<String, String> primaryMap,
        Map<String, String> fallbackMap) {
        if (StringUtils.isEmpty(unitCode)) {
            return null;
        }
        String code = unitCode.trim();
        if (primaryMap != null) {
            String primary = primaryMap.get(code);
            if (StringUtils.isNotEmpty(primary)) {
                return primary;
            }
        }
        if (fallbackMap != null) {
            String fallback = fallbackMap.get(code);
            if (StringUtils.isNotEmpty(fallback)) {
                return fallback;
            }
        }
        return null;
    }

    /**
     * 收费状态 code → 展示文案。
     *
     * @param statusCode ChargeItemStatus code
     * @return 文案；未知码返回 null
     */
    private String resolveChargeStatusText(String statusCode) {
        if (StringUtils.isEmpty(statusCode)) {
            return null;
        }
        ChargeItemStatus status = EnumUtil.fromCode(ChargeItemStatus.class, statusCode);
        if (status == null) {
            return null;
        }
        return status.getDisplay();
    }
}
