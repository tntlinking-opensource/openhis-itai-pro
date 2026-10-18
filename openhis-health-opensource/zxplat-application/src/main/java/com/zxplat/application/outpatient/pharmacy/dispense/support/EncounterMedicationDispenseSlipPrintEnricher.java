/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.pharmacy.dispense.support;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.zxplat.domain.administration.encounter.queries.model.OutpatientEncounterContextQueryModel;
import com.zxplat.domain.administration.encounter.service.EncounterQueryService;
import com.zxplat.domain.administration.organization.entity.Organization;
import com.zxplat.domain.administration.organization.service.OrganizationQueryService;
import com.zxplat.domain.medication.medicationrequest.queries.model.MedicationRequestListQueryModel;
import com.zxplat.domain.share.constants.FieldNameConstants;
import com.zxplat.domain.share.constants.GeneralSharedConstants;
import com.zxplat.domain.share.constants.OutpatientPharmacyPrescriptionQueryConstants;
import com.zxplat.domain.share.constants.PrescriptionSplitConstants;
import com.zxplat.domain.share.enums.AdministrativeGender;
import com.zxplat.domain.share.enums.OrganizationType;
import com.zxplat.domain.share.enums.PublicationStatus;
import com.zxplat.domain.share.enums.YbMedType;
import com.zxplat.domain.share.utils.EncounterDiagnosisIdsSupport;
import com.zxplat.domain.share.utils.EnumUtil;
import com.zxplat.domain.share.utils.MonetaryAmountSupport;
import com.zxplat.application.outpatient.pharmacy.dispense.dto.EncounterMedicationDispenseSlipRowDto;
import com.zxplat.application.shared.diagnosis.EncounterDiagnosisSnapshotAssembler;
import com.zxplat.application.shared.financial.ContractExpenseNatureAssembler;
import com.zxplat.application.shared.financial.EncounterContractDisplayAssembler;
import com.zxplat.framework.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static com.zxplat.framework.core.application.util.StringUtils.firstNonBlank;
import static com.zxplat.framework.core.application.util.StringUtils.trimToNull;

/**
 * 门诊发药 slip 行处方打印字段回补（患者/机构/诊断/药品展示行），
 * 并回填笺面/打印「就诊类型」用的医保医疗类别（{@code med_type}）。
 */
@Component
@RequiredArgsConstructor
public class EncounterMedicationDispenseSlipPrintEnricher {

    private final OrganizationQueryService organizationQueryService;
    private final EncounterDiagnosisSnapshotAssembler encounterDiagnosisSnapshotAssembler;
    /** 就诊诊断读侧：批量解析 {@code adm_encounter_diagnosis.med_type}。 */
    private final EncounterQueryService encounterQueryService;
    /** 费用性质：药嘱 contract_id → ContractCategory 分类文案。 */
    private final ContractExpenseNatureAssembler contractExpenseNatureAssembler;
    /** 就诊优先合同展示名（与发药队列 / 医生站卡片 contractDisplay 同口径）。 */
    private final EncounterContractDisplayAssembler encounterContractDisplayAssembler;

    /** 医院名称短窗缓存（避免每次 enrich 全量拉机构树）。 */
    private volatile CachedHospitalName cachedHospitalName;

    /**
     * 为门诊发药 slip 行填充处方打印字段。
     *
     * @param rows 已装配 slip 行
     * @param outpatientSnapshot 就诊上下文（非历史医嘱 scope）
     * @param preparerPractitionerNameByDispenseId 调配药师姓名（按发放单）
     * @param pricingPractitionerNameByDispenseId 划价人=收费人姓名（按发放单；来自结算收费明细 enterer_id）
     * @param medicationRequestByMedReqId 药嘱索引（费用性质 contract_id）
     */
    public void enrichOutpatientPrintFields(
        List<EncounterMedicationDispenseSlipRowDto> rows,
        OutpatientEncounterContextQueryModel outpatientSnapshot,
        Map<Long, String> preparerPractitionerNameByDispenseId,
        Map<Long, String> pricingPractitionerNameByDispenseId,
        Map<Long, MedicationRequestListQueryModel> medicationRequestByMedReqId) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        String hospitalName = resolveHospitalName();
        String patientName = null;
        String ageText = null;
        String genderEnumText = null;
        String patientPhone = null;
        if (outpatientSnapshot != null) {
            patientName = trimToNull(outpatientSnapshot.getPatientName());
            ageText = trimToNull(outpatientSnapshot.getAgeText());
            genderEnumText = resolveGenderText(outpatientSnapshot.getGenderEnum());
            patientPhone = trimToNull(outpatientSnapshot.getPhone());
        }
        // Pre：按处方号聚合各药行绑定诊断并集，作西药方头诊断（不再用整次就诊诊断摘要）
        Map<String, String> conditionSummaryByPrescriptionKey =
            buildPrescriptionDiagnosisUnionByKey(rows, medicationRequestByMedReqId);
        Map<String, BigDecimal> totalPriceByPrescriptionKey = sumTotalPriceByPrescription(rows);
        Map<String, String> prescriptionDateByKey = resolvePrescriptionDateByKey(rows);
        Map<Long, String> feeNatureTextByContractId =
            this.buildFeeNatureTextByContractId(medicationRequestByMedReqId);
        // Pre：就诊优先合同名（与队列卡片 contractDisplay 同口径，作患者费用性质主来源）
        String encounterContractDisplay = resolveEncounterContractDisplay(outpatientSnapshot);
        Map<Long, String> preparerNames;
        if (preparerPractitionerNameByDispenseId == null) {
            preparerNames = Map.of();
        } else {
            preparerNames = preparerPractitionerNameByDispenseId;
        }
        Map<Long, String> pricingNames;
        if (pricingPractitionerNameByDispenseId == null) {
            pricingNames = Map.of();
        } else {
            pricingNames = pricingPractitionerNameByDispenseId;
        }
        Map<Long, MedicationRequestListQueryModel> requestById;
        if (medicationRequestByMedReqId == null) {
            requestById = Map.of();
        } else {
            requestById = medicationRequestByMedReqId;
        }
        // Pre：按处方号解析医保医疗类别（一张方一种 med_type；供笺面「就诊类型」）
        Map<String, String> medTypeCodeByPrescriptionKey =
            this.buildMedTypeCodeByPrescriptionKey(rows, requestById);
        for (EncounterMedicationDispenseSlipRowDto row : rows) {
            if (row == null) {
                continue;
            }
            row.setPatientName(patientName);
            row.setAgeText(ageText);
            row.setGenderEnumText(genderEnumText);
            row.setPatientPhone(patientPhone);
            row.setHospitalName(hospitalName);
            // 费用性质（患者维度）：优先就诊合同名（与队列卡片一致）；空则药嘱分类文案；再空回落自费
            String feeNatureText = encounterContractDisplay;
            if (StringUtils.isEmpty(feeNatureText)) {
                feeNatureText = resolveFeeNatureText(row, requestById, feeNatureTextByContractId);
            }
            if (StringUtils.isEmpty(feeNatureText)) {
                feeNatureText = OutpatientPharmacyPrescriptionQueryConstants.CONTRACT_CATEGORY_SELF_CHARGE_LABEL;
            }
            row.setFeeNatureText(feeNatureText);
            if (Boolean.TRUE.equals(row.getTcmFlag())) {
                row.setPrescriptionDocumentTitle("中药处方笺");
            } else {
                row.setPrescriptionDocumentTitle("处方笺");
            }
            String prescriptionKey = prescriptionGroupKey(row);
            // 西药方头：同处方号绑定诊断并集（强制覆盖行级单诊摘要，保证查询/打印全集一致）
            if (!Boolean.TRUE.equals(row.getTcmFlag())) {
                String unionSummary = conditionSummaryByPrescriptionKey.get(prescriptionKey);
                if (StringUtils.isNotEmpty(unionSummary)) {
                    row.setConditionSummaryText(unionSummary);
                }
            }
            // Asm：笺面就诊类型 ← 本方绑定诊断 med_type（YbMedType 展示）
            applyEncounterMedicationType(row, medTypeCodeByPrescriptionKey.get(prescriptionKey));
            // Asm：方头纸质分类锚定 rx_type_code；慢病 med_type 覆盖默认「普通处方」
            applyPrescriptionTypeLines(row, requestById);
            BigDecimal prescriptionTotal = totalPriceByPrescriptionKey.get(prescriptionKey);
            row.setPrescriptionTotalPrice(prescriptionTotal);
            row.setPrescriptionTotalPriceText(formatPrescriptionTotalPriceText(prescriptionTotal));
            row.setPrescriptionDateText(prescriptionDateByKey.get(prescriptionKey));
            if (row.getDispenseId() != null) {
                row.setPreparerPractitionerName(preparerNames.get(row.getDispenseId()));
                // 划价取收费人（ChargeItem.enterer_id），禁止回落开单医生
                String pricingName = pricingNames.get(row.getDispenseId());
                if (StringUtils.isNotEmpty(pricingName)) {
                    row.setPricingPractitionerName(pricingName);
                }
            }
            applyMedicationDisplayFields(row);
        }
    }

    /**
     * 按处方分组键汇总各药嘱 {@code encounter_diagnosis_ids} 并集，再解析为顿号拼接展示名。
     *
     * @param rows                        slip 行
     * @param medicationRequestByMedReqId 药嘱索引
     * @return 处方分组键 → 方头诊断文案
     */
    private Map<String, String> buildPrescriptionDiagnosisUnionByKey(
        List<EncounterMedicationDispenseSlipRowDto> rows,
        Map<Long, MedicationRequestListQueryModel> medicationRequestByMedReqId) {
        Map<String, String> result = new HashMap<>();
        // Gate：无 slip / 无药嘱索引则无法回填方头诊断
        if (rows == null || rows.isEmpty() || medicationRequestByMedReqId == null || medicationRequestByMedReqId.isEmpty()) {
            return result;
        }
        Map<String, List<String>> idsTextsByKey = new LinkedHashMap<>();
        // Loop：按处方键收集各药行绑定 ids 串
        for (EncounterMedicationDispenseSlipRowDto row : rows) {
            if (row == null || Boolean.TRUE.equals(row.getTcmFlag())) {
                continue;
            }
            String key = prescriptionGroupKey(row);
            Long medReqId = row.getMedReqId();
            if (medReqId == null) {
                continue;
            }
            MedicationRequestListQueryModel requestRow = medicationRequestByMedReqId.get(medReqId);
            if (requestRow == null) {
                continue;
            }
            List<String> bucket = idsTextsByKey.get(key);
            if (bucket == null) {
                bucket = new ArrayList<>();
                idsTextsByKey.put(key, bucket);
            }
            bucket.add(requestRow.getEncounterDiagnosisIds());
        }
        // Asm：分组 ids 并集 → 顿号展示名
        return encounterDiagnosisSnapshotAssembler.mapUnionDisplayNameByGroupKey(idsTextsByKey);
    }

    /**
     * 从药嘱索引收集 contract_id，批量解析费用性质分类文案。
     *
     * @param medicationRequestByMedReqId 药嘱索引
     * @return 合同主键 → 分类文案
     */
    private Map<Long, String> buildFeeNatureTextByContractId(
        Map<Long, MedicationRequestListQueryModel> medicationRequestByMedReqId) {
        if (medicationRequestByMedReqId == null || medicationRequestByMedReqId.isEmpty()) {
            return Map.of();
        }
        Set<Long> contractIds = new HashSet<>();
        // Loop：收集本页/本就诊药嘱上的费用性质主键
        for (MedicationRequestListQueryModel requestRow : medicationRequestByMedReqId.values()) {
            if (requestRow != null && requestRow.getContractId() != null) {
                contractIds.add(requestRow.getContractId());
            }
        }
        return this.contractExpenseNatureAssembler.mapCategoryTextByContractIdIn(contractIds);
    }

    /**
     * 单行费用性质文案：slip.medReqId → 药嘱 contract_id → 分类文案。
     *
     * @param row                         slip 行
     * @param requestById                 药嘱索引
     * @param feeNatureTextByContractId   合同 → 文案
     * @return 分类文案；无则 null
     */
    private static String resolveFeeNatureText(
        EncounterMedicationDispenseSlipRowDto row,
        Map<Long, MedicationRequestListQueryModel> requestById,
        Map<Long, String> feeNatureTextByContractId) {
        if (row.getMedReqId() == null) {
            return null;
        }
        MedicationRequestListQueryModel requestRow = requestById.get(row.getMedReqId());
        if (requestRow == null || requestRow.getContractId() == null) {
            return null;
        }
        return feeNatureTextByContractId.get(requestRow.getContractId());
    }

    /**
     * 解析就诊优先合同展示名（与发药队列 {@code contractDisplay} 同口径）。
     *
     * @param outpatientSnapshot 门诊就诊上下文；可空
     * @return 合同名称；无就诊或无合同时 null
     */
    private String resolveEncounterContractDisplay(OutpatientEncounterContextQueryModel outpatientSnapshot) {
        if (outpatientSnapshot == null || outpatientSnapshot.getEncounterId() == null) {
            return null;
        }
        Map<Long, String> displayByEncounterId =
            this.encounterContractDisplayAssembler.mapContractDisplayByEncounterIds(
                Collections.singletonList(outpatientSnapshot.getEncounterId()));
        return trimToNull(displayByEncounterId.get(outpatientSnapshot.getEncounterId()));
    }

    /**
     * 按处方分组键解析医保医疗类别码：同方各药行绑定诊断并集后取首位非空 {@code med_type}。
     *
     * <p>业务不变量：一张方只能是一种医疗类别（普通门诊 / 慢病等），不可能混绑。</p>
     *
     * @param rows        slip 行
     * @param requestById 药嘱索引
     * @return 处方分组键 → {@code YbMedType#getCode()}；无可用 med_type 时不含键
     */
    private Map<String, String> buildMedTypeCodeByPrescriptionKey(
        List<EncounterMedicationDispenseSlipRowDto> rows,
        Map<Long, MedicationRequestListQueryModel> requestById) {
        Map<String, String> result = new HashMap<>();
        // Gate：无 slip / 无药嘱则无法回填就诊类型
        if (rows == null || rows.isEmpty() || requestById == null || requestById.isEmpty()) {
            return result;
        }
        Map<String, List<String>> idsTextsByKey = new LinkedHashMap<>();
        Set<Long> allDiagnosisIds = new HashSet<>();
        // Loop：按处方键收集各药行绑定诊断 ids，并汇总批量查 med_type 的主键
        for (EncounterMedicationDispenseSlipRowDto row : rows) {
            // 空行或无药嘱主键：跳过本行诊断收集
            if (row == null || row.getMedReqId() == null) {
                continue;
            }
            MedicationRequestListQueryModel requestRow = requestById.get(row.getMedReqId());
            // 药嘱未入索引：无法取绑定诊断
            if (requestRow == null) {
                continue;
            }
            String idsText = requestRow.getEncounterDiagnosisIds();
            // 未绑诊断：不参与本方 med_type 决议
            if (StringUtils.isEmpty(idsText)) {
                continue;
            }
            String key = prescriptionGroupKey(row);
            List<String> bucket = idsTextsByKey.get(key);
            // 本方首次出现：新建 ids 桶
            if (bucket == null) {
                bucket = new ArrayList<>();
                idsTextsByKey.put(key, bucket);
            }
            bucket.add(idsText);
            allDiagnosisIds.addAll(EncounterDiagnosisIdsSupport.parseUniqueOrderedIds(idsText, true));
        }
        // Gate：本页无绑定诊断则整批不回填
        if (allDiagnosisIds.isEmpty()) {
            return result;
        }
        Map<Long, String> medTypeByDiagnosisId =
            this.encounterQueryService.mapMedTypeByEncounterDiagnosisIds(allDiagnosisIds);
        // Loop：各方取并集首位有 med_type 的诊断作为本方医疗类别
        for (Map.Entry<String, List<String>> entry : idsTextsByKey.entrySet()) {
            String unionIds = EncounterDiagnosisIdsSupport.unionOrderedIdsTexts(entry.getValue());
            List<Long> orderedIds = EncounterDiagnosisIdsSupport.parseUniqueOrderedIds(unionIds, true);
            String resolvedMedType = null;
            for (Long diagnosisId : orderedIds) {
                // 非法主键段跳过
                if (diagnosisId == null) {
                    continue;
                }
                String medType = trimToNull(medTypeByDiagnosisId.get(diagnosisId));
                // 取并集有序首位非空 med_type 作为本方唯一医疗类别
                if (medType != null) {
                    resolvedMedType = medType;
                    break;
                }
            }
            // 本方有可用 med_type 才写入结果图
            if (resolvedMedType != null) {
                result.put(entry.getKey(), resolvedMedType);
            }
        }
        return result;
    }

    /**
     * 将处方级 med_type 写入 slip 行（code + YbMedType 展示名）。
     *
     * @param row         slip 行
     * @param medTypeCode {@code YbMedType#getCode()}；可空
     */
    private static void applyEncounterMedicationType(EncounterMedicationDispenseSlipRowDto row, String medTypeCode) {
        String code = trimToNull(medTypeCode);
        // 无 med_type：清空，避免残留旧 rx_type_code 误显
        if (code == null) {
            row.setEncounterMedicationTypeCode(null);
            row.setEncounterMedicationTypeText(null);
            return;
        }
        row.setEncounterMedicationTypeCode(code);
        YbMedType medTypeEnum = EnumUtil.fromCode(YbMedType.class, code);
        // 合法 YbMedType：笺面展示枚举中文名（普通门诊 / 门诊慢病等）
        if (medTypeEnum != null) {
            row.setEncounterMedicationTypeText(medTypeEnum.getDisplay());
        } else {
            // 未知码仍回传 code，避免笺面空白且不伪造文案
            row.setEncounterMedicationTypeText(code);
        }
    }

    /**
     * 方头右上角两行：纸质处方分类文案 + 有效期（与样张「普通处方 / 当日有效」对齐）。
     * <p>分类优先锚定药嘱 {@code rx_type_code}（急诊/儿科/麻精）；默认「普通处方」时若本方
     * {@code med_type} 为慢病/慢特病/特病，则写「慢病处方」（与发药单就诊类型同源）。</p>
     *
     * @param row         slip 行（就地写入 typeLine1/2；须已回填 encounterMedicationTypeCode）
     * @param requestById 药嘱索引（取 {@code rx_type_code}）
     */
    private static void applyPrescriptionTypeLines(
        EncounterMedicationDispenseSlipRowDto row,
        Map<Long, MedicationRequestListQueryModel> requestById) {
        String rxTypeCode = null;
        // 有药嘱主键时从索引取 rx_type_code，供纸质方头分类
        if (row.getMedReqId() != null && requestById != null) {
            MedicationRequestListQueryModel requestRow = requestById.get(row.getMedReqId());
            // 命中药嘱则写入处方类别码
            if (requestRow != null) {
                rxTypeCode = requestRow.getRxTypeCode();
            }
        }
        row.setPrescriptionTypeLine1(
            resolvePrescriptionPaperTypeLine1(rxTypeCode, row.getEncounterMedicationTypeCode()));
        row.setPrescriptionTypeLine2("当日有效");
    }

    /**
     * 按处方类别码（355）与医保医疗类别映射方头第一行纸质分类文案。
     *
     * @param rxTypeCode  {@code rx_type_code}
     * @param medTypeCode {@code YbMedType#getCode()}（可空）
     * @return 普通/慢病/急诊/儿科/麻精一/精二文案
     */
    private static String resolvePrescriptionPaperTypeLine1(String rxTypeCode, String medTypeCode) {
        String code = trimToNull(rxTypeCode);
        // 急诊西药/中药/中成药类别 → 急诊处方
        if (PrescriptionSplitConstants.RX_TYPE_EMERGENCY_WESTERN.equals(code)
            || PrescriptionSplitConstants.RX_TYPE_EMERGENCY_HERBAL.equals(code)
            || PrescriptionSplitConstants.RX_TYPE_EMERGENCY_PATENT.equals(code)) {
            return "急诊处方";
        }
        // 儿科西药/中药/中成药类别 → 儿科处方
        if (PrescriptionSplitConstants.RX_TYPE_PEDIATRIC_WESTERN.equals(code)
            || PrescriptionSplitConstants.RX_TYPE_PEDIATRIC_HERBAL.equals(code)
            || PrescriptionSplitConstants.RX_TYPE_PEDIATRIC_PATENT.equals(code)) {
            return "儿科处方";
        }
        // 麻精一专用类别
        if (PrescriptionSplitConstants.RX_TYPE_NARCOTIC_PSYCH_ONE.equals(code)) {
            return "麻精一";
        }
        // 精二类别（可与普通西药/中成药同方，方头须标精二）
        if (PrescriptionSplitConstants.RX_TYPE_PSYCH_TWO.equals(code)) {
            return "精二";
        }
        // 慢病/慢特病/特病医疗类别 → 慢病处方（与发药单 med_type 对齐）
        if (isChronicMedTypeCode(medTypeCode)) {
            return "慢病处方";
        }
        return "普通处方";
    }

    /**
     * 是否慢病系医保医疗类别（门诊慢特病 / 门诊慢病 / 门诊特病）。
     *
     * @param medTypeCode {@code YbMedType#getCode()}
     * @return true=慢病系
     */
    private static boolean isChronicMedTypeCode(String medTypeCode) {
        String code = trimToNull(medTypeCode);
        if (code == null) {
            return false;
        }
        if (YbMedType.CHRONIC_SPECIAL_DISEASE_OUTPATIENT.getCode().equals(code)) {
            return true;
        }
        if (YbMedType.CHRONIC_DISEASE_OUTPATIENT.getCode().equals(code)) {
            return true;
        }
        if (YbMedType.SPECIAL_DISEASE_OUTPATIENT.getCode().equals(code)) {
            return true;
        }
        return false;
    }

    /**
     * 装配药品左栏（名称+规格+数量）与用法用量展示串。
     *
     * @param row slip 行
     */
    private static void applyMedicationDisplayFields(EncounterMedicationDispenseSlipRowDto row) {
        String name = firstNonBlank(row.getMedicationName(), row.getMerchandiseName());
        String spec = trimToNull(row.getSpecificationText());
        String specPart;
        if (spec == null) {
            specPart = "";
        } else {
            specPart = "(" + spec + ")";
        }
        String qtyPart = formatQuantityWithUnit(row.getQuantity(), row.getUnitText());
        String namePart;
        if (name == null) {
            namePart = "";
        } else {
            namePart = name;
        }
        String left = namePart + specPart;
        if (StringUtils.isNotEmpty(qtyPart)) {
            left = left + " X" + qtyPart;
        }
        row.setMedicationDisplayLeft(StringUtils.trimToEmpty(left));
        row.setMedicationDisplayRight(qtyPart);
        row.setMedicationUsageDisplayText(buildUsageDisplay(row));
    }

    /**
     * 拼接「用法用量：途径 / 频次 / 剂量」展示串。
     *
     * @param row slip 行
     * @return 展示文案；各段皆空则 null
     */
    private static String buildUsageDisplay(EncounterMedicationDispenseSlipRowDto row) {
        List<String> parts = new ArrayList<>();
        String route = trimToNull(row.getRouteText());
        if (route != null) {
            parts.add(route);
        }
        String freq = trimToNull(row.getFrequencyText());
        if (freq != null) {
            parts.add(freq);
        }
        String dosePart = formatDosePart(row.getDoseValue(), row.getDoseUnitText());
        if (dosePart != null) {
            parts.add(dosePart);
        }
        if (parts.isEmpty()) {
            return null;
        }
        return "用法用量：" + String.join(" / ", parts);
    }

    /**
     * 剂量数值与单位拼接（仅单位时返回单位）。
     *
     * @param doseValue    剂量
     * @param doseUnitText 单位文案
     * @return 剂量段；皆空则 null
     */
    private static String formatDosePart(BigDecimal doseValue, String doseUnitText) {
        if (doseValue == null) {
            return trimToNull(doseUnitText);
        }
        String dose = doseValue.stripTrailingZeros().toPlainString();
        String unit = trimToNull(doseUnitText);
        if (unit == null) {
            return dose;
        }
        return dose + unit;
    }

    /**
     * 数量与单位拼接（仅单位时返回单位）。
     *
     * @param quantity 数量
     * @param unitText 单位文案
     * @return 数量段；皆空则 null
     */
    private static String formatQuantityWithUnit(BigDecimal quantity, String unitText) {
        if (quantity == null) {
            return trimToNull(unitText);
        }
        String qty = quantity.stripTrailingZeros().toPlainString();
        String unit = trimToNull(unitText);
        if (unit == null) {
            return qty;
        }
        return qty + unit;
    }

    /**
     * 处方药费合计展示（前缀「药费：」+ 6 位刻度金额）。
     *
     * @param total 处方合计
     * @return 展示串；null 则 null
     */
    private static String formatPrescriptionTotalPriceText(BigDecimal total) {
        if (total == null) {
            return null;
        }
        return "药费：" + MonetaryAmountSupport.scaleDisplay(total).toPlainString();
    }

    /**
     * 按处方分组键汇总各行 totalPrice。
     *
     * @param rows slip 行
     * @return 分组键 → 合计
     */
    private static Map<String, BigDecimal> sumTotalPriceByPrescription(List<EncounterMedicationDispenseSlipRowDto> rows) {
        Map<String, BigDecimal> totals = new HashMap<>();
        for (EncounterMedicationDispenseSlipRowDto row : rows) {
            if (row == null) {
                continue;
            }
            String key = prescriptionGroupKey(row);
            BigDecimal lineTotal;
            if (row.getTotalPrice() == null) {
                lineTotal = BigDecimal.ZERO;
            } else {
                lineTotal = row.getTotalPrice();
            }
            totals.merge(key, lineTotal, BigDecimal::add);
        }
        return totals;
    }

    /**
     * 每组处方取最早 requestOpenedTime 格式化为日期展示。
     *
     * @param rows slip 行
     * @return 分组键 → 日期文案
     */
    private static Map<String, String> resolvePrescriptionDateByKey(List<EncounterMedicationDispenseSlipRowDto> rows) {
        Map<String, LocalDateTime> earliest = new LinkedHashMap<>();
        for (EncounterMedicationDispenseSlipRowDto row : rows) {
            if (row == null || row.getRequestOpenedTime() == null) {
                continue;
            }
            String key = prescriptionGroupKey(row);
            LocalDateTime current = earliest.get(key);
            if (current == null || row.getRequestOpenedTime().isBefore(current)) {
                earliest.put(key, row.getRequestOpenedTime());
            }
        }
        Map<String, String> result = new HashMap<>();
        for (Map.Entry<String, LocalDateTime> entry : earliest.entrySet()) {
            result.put(entry.getKey(), entry.getValue().format(GeneralSharedConstants.DATE_DISPLAY_FORMATTER));
        }
        return result;
    }

    /**
     * 处方汇总分组键：优先 prescriptionNo，否则 dispenseId，再否则行哈希。
     *
     * @param row slip 行
     * @return 非空分组键
     */
    private static String prescriptionGroupKey(EncounterMedicationDispenseSlipRowDto row) {
        String prescriptionNo = trimToNull(row.getPrescriptionNo());
        if (prescriptionNo != null) {
            return prescriptionNo;
        }
        if (row.getDispenseId() != null) {
            return "dispense:" + row.getDispenseId();
        }
        return "row:" + Objects.hash(row.getMedReqId(), row.getMedicationId());
    }

    /**
     * 就诊诊断摘要：优先 conditionDefinitionName，否则解析 diagnosisJson。
     *
     * @param encounterId 就诊
     * @return 诊断摘要；无则 null
     */
    private String resolveDiagnosisSummary(Long encounterId) {
        EncounterDiagnosisSnapshotAssembler.DiagnosisSnapshot snapshot =
            encounterDiagnosisSnapshotAssembler.buildSnapshot(encounterId);
        if (snapshot == null) {
            return null;
        }
        if (StringUtils.isNotEmpty(snapshot.conditionDefinitionName())) {
            return snapshot.conditionDefinitionName();
        }
        return summarizeDiagnosisJson(snapshot.diagnosisJson());
    }

    /**
     * 从诊断 JSON 数组提取病名并以「；」拼接。
     *
     * @param diagnosisJson 快照 JSON
     * @return 摘要；解析失败或无名则 null
     */
    private static String summarizeDiagnosisJson(String diagnosisJson) {
        if (StringUtils.isEmpty(diagnosisJson)) {
            return null;
        }
        try {
            JSONArray array = JSONArray.parseArray(diagnosisJson);
            if (array == null || array.isEmpty()) {
                return null;
            }
            List<String> names = new ArrayList<>();
            for (int i = 0; i < array.size(); i++) {
                JSONObject item = array.getJSONObject(i);
                if (item == null) {
                    continue;
                }
                String name = item.getString(FieldNameConstants.CONDITION_DEFINITION_NAME);
                if (StringUtils.isEmpty(name)) {
                    name = item.getString("name");
                }
                if (StringUtils.isNotEmpty(name)) {
                    names.add(name.trim());
                }
            }
            if (names.isEmpty()) {
                return null;
            }
            return String.join("；", names);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    /**
     * 机构树中首个启用态 HOSPITAL 类型组织名称（进程内短窗缓存，降低点患者反复拉树成本）。
     *
     * @return 医院名；无则 null
     */
    private String resolveHospitalName() {
        long nowMs = System.currentTimeMillis();
        CachedHospitalName cached = this.cachedHospitalName;
        // 短窗命中则直接复用，避免每次 IF-OPP-03 全量 getOrganizationTree
        if (cached != null
            && nowMs - cached.loadedAtMs() < GeneralSharedConstants.SERVICE_LOCATION_TEMPLATE_ORG_CACHE_TTL_MS) {
            return cached.name();
        }
        String hospitalName = null;
        for (Organization organization : organizationQueryService.getOrganizationTree()) {
            if (organization == null || organization.getId() == null) {
                continue;
            }
            if (!PublicationStatus.ACTIVE.getCode().equals(organization.getStatusEnum())) {
                continue;
            }
            if (OrganizationType.HOSPITAL.getCode().equals(organization.getTypeEnum())) {
                hospitalName = trimToNull(organization.getName());
                break;
            }
        }
        this.cachedHospitalName = new CachedHospitalName(hospitalName, nowMs);
        return hospitalName;
    }

    /**
     * 医院名称缓存条目。
     *
     * @param name       医院展示名；可空
     * @param loadedAtMs 加载时刻（epoch ms）
     */
    private record CachedHospitalName(String name, long loadedAtMs) {
    }

    /**
     * 性别枚举 code → 展示文案；未知则回落原 code。
     *
     * @param genderEnum 性别 code
     * @return 展示文案
     */
    private static String resolveGenderText(String genderEnum) {
        AdministrativeGender gender = EnumUtil.fromCode(AdministrativeGender.class, genderEnum);
        if (gender != null) {
            return gender.getDisplay();
        }
        return trimToNull(genderEnum);
    }
}
