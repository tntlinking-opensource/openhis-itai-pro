/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.pharmacy.dispense.support;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.openhis.health.domain.administration.encounter.queries.model.OutpatientEncounterContextQueryModel;
import com.openhis.health.domain.administration.organization.entity.Organization;
import com.openhis.health.domain.administration.organization.service.OrganizationQueryService;
import com.openhis.health.domain.medication.medicationrequest.queries.model.MedicationRequestListQueryModel;
import com.openhis.health.domain.share.constants.FieldNameConstants;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.OutpatientPharmacyPrescriptionQueryConstants;
import com.openhis.health.domain.share.constants.PrescriptionSplitConstants;
import com.openhis.health.domain.share.enums.AdministrativeGender;
import com.openhis.health.domain.share.enums.OrganizationType;
import com.openhis.health.domain.share.enums.PublicationStatus;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.health.domain.share.utils.MonetaryAmountSupport;
import com.openhis.health.application.outpatient.pharmacy.dispense.dto.EncounterMedicationDispenseSlipRowDto;
import com.openhis.health.application.shared.diagnosis.EncounterDiagnosisSnapshotAssembler;
import com.openhis.health.application.shared.financial.ContractExpenseNatureAssembler;
import com.openhis.whale.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static com.openhis.whale.core.application.util.StringUtils.firstNonBlank;
import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

/**
 * 门诊发药 slip 行处方打印字段回补（患者/机构/诊断/药品展示行）。
 */
@Component
@RequiredArgsConstructor
public class EncounterMedicationDispenseSlipPrintEnricher {

    private final OrganizationQueryService organizationQueryService;
    private final EncounterDiagnosisSnapshotAssembler encounterDiagnosisSnapshotAssembler;
    /** 费用性质：药嘱 contract_id → ContractCategory 分类文案。 */
    private final ContractExpenseNatureAssembler contractExpenseNatureAssembler;

    /** 医院名称短窗缓存（避免每次 enrich 全量拉机构树）。 */
    private volatile CachedHospitalName cachedHospitalName;

    /**
     * 为门诊发药 slip 行填充处方打印字段。
     *
     * @param rows 已装配 slip 行
     * @param outpatientSnapshot 就诊上下文（非历史医嘱 scope）
     * @param preparerPractitionerNameByDispenseId 调配药师姓名（按发放单）
     * @param pricingPractitionerNameByDispenseId 划价人=收费人姓名（按发放单；来自结算收费明细 performer_id）
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
        for (EncounterMedicationDispenseSlipRowDto row : rows) {
            if (row == null) {
                continue;
            }
            row.setPatientName(patientName);
            row.setAgeText(ageText);
            row.setGenderEnumText(genderEnumText);
            row.setPatientPhone(patientPhone);
            row.setHospitalName(hospitalName);
            // 费用性质：药嘱 contract_id → 分类文案；无合同/未识别时回落自费（与发药笺面一致）
            String feeNatureText = resolveFeeNatureText(row, requestById, feeNatureTextByContractId);
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
            applyPrescriptionTypeLines(row);
            BigDecimal prescriptionTotal = totalPriceByPrescriptionKey.get(prescriptionKey);
            row.setPrescriptionTotalPrice(prescriptionTotal);
            row.setPrescriptionTotalPriceText(formatPrescriptionTotalPriceText(prescriptionTotal));
            row.setPrescriptionDateText(prescriptionDateByKey.get(prescriptionKey));
            if (row.getDispenseId() != null) {
                row.setPreparerPractitionerName(preparerNames.get(row.getDispenseId()));
                // 划价取收费人（结算 performer），禁止回落开单医生
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
     * 方头右上角两行：纸质处方分类文案 + 有效期（与样张「普通处方 / 当日有效」对齐）。
     * <p>勿回落 {@code tcmFlagText}/{@code skinTestFlagText} 等布尔展示「是/否」，亦勿直接印术语「门诊西药」。</p>
     *
     * @param row slip 行（就地写入 typeLine1/2）
     */
    private static void applyPrescriptionTypeLines(EncounterMedicationDispenseSlipRowDto row) {
        row.setPrescriptionTypeLine1(resolvePrescriptionPaperTypeLine1(row.getEncounterMedicationTypeCode()));
        row.setPrescriptionTypeLine2("当日有效");
    }

    /**
     * 按处方类别码（355）映射方头第一行纸质分类文案。
     *
     * @param rxTypeCode {@code rx_type_code}
     * @return 普通/急诊/儿科/麻精一文案
     */
    private static String resolvePrescriptionPaperTypeLine1(String rxTypeCode) {
        String code = trimToNull(rxTypeCode);
        if (code == null) {
            return "普通处方";
        }
        if (PrescriptionSplitConstants.RX_TYPE_EMERGENCY_WESTERN.equals(code)
            || PrescriptionSplitConstants.RX_TYPE_EMERGENCY_HERBAL.equals(code)
            || PrescriptionSplitConstants.RX_TYPE_EMERGENCY_PATENT.equals(code)) {
            return "急诊处方";
        }
        if (PrescriptionSplitConstants.RX_TYPE_PEDIATRIC_WESTERN.equals(code)
            || PrescriptionSplitConstants.RX_TYPE_PEDIATRIC_HERBAL.equals(code)
            || PrescriptionSplitConstants.RX_TYPE_PEDIATRIC_PATENT.equals(code)) {
            return "儿科处方";
        }
        if (PrescriptionSplitConstants.RX_TYPE_NARCOTIC_PSYCH_ONE.equals(code)) {
            return "麻精一";
        }
        return "普通处方";
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
