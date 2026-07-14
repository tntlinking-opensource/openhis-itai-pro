/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.pharmacy.dispenserecord.support;

import com.openhis.health.domain.administration.encounter.queries.model.InpatientEncounterContextQueryModel;
import com.openhis.health.domain.administration.encounter.queries.model.OutpatientEncounterContextQueryModel;
import com.openhis.health.domain.administration.location.service.LocationQueryService;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.medication.medicationdispense.queries.model.MedicationDispenseSlipQueryModel;
import com.openhis.health.domain.medication.medicationrequest.queries.model.MedicationRequestListQueryModel;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.PharmacyDispenseRecordConstants;
import com.openhis.health.domain.share.constants.TerminologyCodeSystemNameConstants;
import com.openhis.health.domain.share.enums.DispenseStatus;
import com.openhis.health.domain.share.enums.MedicationDispenseType;
import com.openhis.health.application.shared.advice.TcmMethodCodeDisplayAssembler;
import com.openhis.health.application.shared.option.FrequencyOptionLoader;
import com.openhis.health.application.shared.option.TerminologyOptionLoader;
import com.openhis.health.application.shared.advice.OrderCatalogDisplayAssembler;
import com.openhis.health.application.inpatient.pharmacy.dispenserecord.dto.InpatientPharmacyDispenseRecordQueryRowDto;
import com.openhis.health.application.outpatient.pharmacy.dispenserecord.dto.PharmacyDispenseRecordQueryRowDto;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import lombok.RequiredArgsConstructor;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 发退药记录平铺行装配（AS-OP-PHA-004 / AS-IP-PHA-004）。
 *
 * <p>职责边界：批量读模型 → querysupport 中性 Row DTO；退药行金额为负；无 charge 时价格字段为空。</p>
 */
@Component
@RequiredArgsConstructor
public class PharmacyDispenseRecordReadAssembler {

    private final LocationQueryService locationQueryService;
    private final TerminologyOptionLoader terminologyOptionLoader;
    private final FrequencyOptionLoader frequencyOptionLoader;

    /**
     * 装配门诊发退药记录平铺行。
     *
     * @param slipRows                    发放事实读模型列表
     * @param medicationRequestByMedReqId 药嘱批量索引
     * @param productByMedicationId       药品展示索引
     * @param outpatientContextByEncounterId 门诊就诊上下文
     * @param chargeUnitPriceByDispenseId 单价 Map
     * @param chargeTotalPriceByDispenseId 金额 Map
     * @param orderingDepartmentNameByOrgId 开单科室名
     * @param dispenserNameByPractitionerId 发药人姓名
     * @return 平铺行列表；入参空时返回空列表
     */
    public List<PharmacyDispenseRecordQueryRowDto> assembleOutpatientRows(
        List<MedicationDispenseSlipQueryModel> slipRows,
        Map<Long, MedicationRequestListQueryModel> medicationRequestByMedReqId,
        Map<Long, ProductDefinitionDisplayDto> productByMedicationId,
        Map<Long, OutpatientEncounterContextQueryModel> outpatientContextByEncounterId,
        Map<Long, BigDecimal> chargeUnitPriceByDispenseId,
        Map<Long, BigDecimal> chargeTotalPriceByDispenseId,
        Map<Long, String> orderingDepartmentNameByOrgId,
        Map<Long, String> dispenserNameByPractitionerId) {
        if (slipRows == null || slipRows.isEmpty()) {
            return List.of();
        }
        AssemblyLabels labels = this.loadTerminologyLabels(true);
        Map<Long, String> pharmacyNameByLocationId = this.loadPharmacyNames(slipRows);
        List<PharmacyDispenseRecordQueryRowDto> rows = new ArrayList<>(slipRows.size());
        for (MedicationDispenseSlipQueryModel slip : slipRows) {
            if (slip == null) {
                continue;
            }
            PharmacyDispenseRecordQueryRowDto row = this.buildBaseRow(
                slip,
                medicationRequestByMedReqId,
                productByMedicationId,
                chargeUnitPriceByDispenseId,
                chargeTotalPriceByDispenseId,
                orderingDepartmentNameByOrgId,
                dispenserNameByPractitionerId,
                pharmacyNameByLocationId,
                labels,
                true);
            if (slip.getEncounterId() != null && outpatientContextByEncounterId != null) {
                OutpatientEncounterContextQueryModel context = outpatientContextByEncounterId.get(slip.getEncounterId());
                if (context != null) {
                    row.setPatientId(context.getPatientId());
                    row.setPatientName(context.getPatientName());
                    row.setEncounterBusNo(context.getEncounterBusNo());
                }
            }
            rows.add(row);
        }
        return rows;
    }

    /**
     * 装配住院发退药记录平铺行。
     *
     * @param slipRows                    发放事实读模型列表
     * @param medicationRequestByMedReqId 药嘱批量索引
     * @param productByMedicationId       药品展示索引
     * @param inpatientContextByEncounterId 住院就诊上下文
     * @param chargeUnitPriceByDispenseId 单价 Map
     * @param chargeTotalPriceByDispenseId 金额 Map
     * @param orderingDepartmentNameByOrgId 开单科室名
     * @param dispenserNameByPractitionerId 发药人姓名
     * @return 平铺行列表
     */
    public List<InpatientPharmacyDispenseRecordQueryRowDto> assembleInpatientRows(
        List<MedicationDispenseSlipQueryModel> slipRows,
        Map<Long, MedicationRequestListQueryModel> medicationRequestByMedReqId,
        Map<Long, ProductDefinitionDisplayDto> productByMedicationId,
        Map<Long, InpatientEncounterContextQueryModel> inpatientContextByEncounterId,
        Map<Long, BigDecimal> chargeUnitPriceByDispenseId,
        Map<Long, BigDecimal> chargeTotalPriceByDispenseId,
        Map<Long, String> orderingDepartmentNameByOrgId,
        Map<Long, String> dispenserNameByPractitionerId) {
        if (slipRows == null || slipRows.isEmpty()) {
            return List.of();
        }
        AssemblyLabels labels = this.loadTerminologyLabels(false);
        Map<Long, String> pharmacyNameByLocationId = this.loadPharmacyNames(slipRows);
        List<InpatientPharmacyDispenseRecordQueryRowDto> rows = new ArrayList<>(slipRows.size());
        for (MedicationDispenseSlipQueryModel slip : slipRows) {
            if (slip == null) {
                continue;
            }
            PharmacyDispenseRecordQueryRowDto base = this.buildBaseRow(
                slip,
                medicationRequestByMedReqId,
                productByMedicationId,
                chargeUnitPriceByDispenseId,
                chargeTotalPriceByDispenseId,
                orderingDepartmentNameByOrgId,
                dispenserNameByPractitionerId,
                pharmacyNameByLocationId,
                labels,
                false);
            InpatientPharmacyDispenseRecordQueryRowDto row = this.copyToInpatientRow(base);
            if (slip.getEncounterId() != null && inpatientContextByEncounterId != null) {
                InpatientEncounterContextQueryModel context = inpatientContextByEncounterId.get(slip.getEncounterId());
                if (context != null) {
                    row.setPatientId(context.getPatientId());
                    row.setPatientName(context.getPatientName());
                    row.setInpatientNo(context.getEncounterBusNo());
                    row.setWardName(context.getWardName());
                    row.setBedName(context.getBedName());
                }
            }
            rows.add(row);
        }
        return rows;
    }

    /**
     * 由发药人主档列表构建 practitionerId → 姓名 Map。
     *
     * @param practitioners 发药人主档列表
     * @return 姓名索引
     */
    public Map<Long, String> indexDispenserNames(List<Practitioner> practitioners) {
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
     * 装配发退药平铺行公共字段：类型、数量符号、费用与用法展示（门诊/住院共用）。
     *
     * @param slip                            发放事实读模型
     * @param medicationRequestByMedReqId     药嘱索引
     * @param productByMedicationId           药品展示索引
     * @param chargeUnitPriceByDispenseId     发放 id → 单价
     * @param chargeTotalPriceByDispenseId    发放 id → 金额
     * @param orderingDepartmentNameByOrgId   开单科室名
     * @param dispenserNameByPractitionerId   发药人姓名
     * @param pharmacyNameByLocationId        药房名称
     * @param labels                          术语标签缓存
     * @param outpatientMode                  true 时用法含门诊 TCM 给药途径
     * @return 中性平铺行 DTO
     */
    private PharmacyDispenseRecordQueryRowDto buildBaseRow(
        MedicationDispenseSlipQueryModel slip,
        Map<Long, MedicationRequestListQueryModel> medicationRequestByMedReqId,
        Map<Long, ProductDefinitionDisplayDto> productByMedicationId,
        Map<Long, BigDecimal> chargeUnitPriceByDispenseId,
        Map<Long, BigDecimal> chargeTotalPriceByDispenseId,
        Map<Long, String> orderingDepartmentNameByOrgId,
        Map<Long, String> dispenserNameByPractitionerId,
        Map<Long, String> pharmacyNameByLocationId,
        AssemblyLabels labels,
        boolean outpatientMode) {
        PharmacyDispenseRecordQueryRowDto row = new PharmacyDispenseRecordQueryRowDto();
        row.setDispenseId(slip.getDispenseId());
        row.setEncounterId(slip.getEncounterId());
        row.setMedReqId(slip.getMedReqId());
        row.setPharmacyLocationId(slip.getLocationId());
        if (slip.getLocationId() != null) {
            row.setPharmacyLocationName(pharmacyNameByLocationId.get(slip.getLocationId()));
        }
        boolean refundRow = this.isRefundRecordRow(slip);
        if (refundRow) {
            row.setRecordTypeCode(PharmacyDispenseRecordConstants.RECORD_TYPE_REFUND);
            row.setRecordTypeText(PharmacyDispenseRecordConstants.RECORD_TYPE_REFUND_LABEL);
        } else {
            row.setRecordTypeCode(PharmacyDispenseRecordConstants.RECORD_TYPE_DISPENSE);
            row.setRecordTypeText(PharmacyDispenseRecordConstants.RECORD_TYPE_DISPENSE_LABEL);
        }
        if (slip.getDispenseTime() != null) {
            row.setDispenseTime(slip.getDispenseTime().format(GeneralSharedConstants.DATE_TIME_FORMATTER));
        }
        Long dispenserId = slip.getDispenserId();
        row.setDispenserPractitionerId(dispenserId);
        if (dispenserId != null) {
            row.setDispenserName(dispenserNameByPractitionerId.get(dispenserId));
        }
        row.setPatientId(slip.getPatientId());
        row.setMedicationId(slip.getMedicationId());
        row.setLotNumber(slip.getLotNumber());
        row.setTraceNo(slip.getTraceNo());
        row.setSummaryNo(slip.getSummaryNo());
        BigDecimal quantity = slip.getDispenseQuantity();
        if (quantity == null) {
            quantity = slip.getQuantity();
        }
        if (quantity != null) {
            row.setQuantity(quantity.abs());
        }
        row.setUnitCode(slip.getUnitCode());
        if (StringUtils.isNotEmpty(slip.getUnitCode())) {
            row.setUnitText(OrderCatalogDisplayAssembler.resolveCategoryDisplay(labels.unitLabels, slip.getUnitCode()));
        }
        if (slip.getDispenseId() != null) {
            row.setUnitPrice(chargeUnitPriceByDispenseId.get(slip.getDispenseId()));
            BigDecimal totalAmount = chargeTotalPriceByDispenseId.get(slip.getDispenseId());
            if (totalAmount != null && refundRow) {
                row.setTotalAmount(totalAmount.negate());
            } else {
                row.setTotalAmount(totalAmount);
            }
        }
        MedicationRequestListQueryModel req = null;
        if (slip.getMedReqId() != null && medicationRequestByMedReqId != null) {
            req = medicationRequestByMedReqId.get(slip.getMedReqId());
        }
        String prescriptionNo = slip.getPrescriptionNo();
        if (StringUtils.isEmpty(prescriptionNo) && req != null) {
            prescriptionNo = req.getPrescriptionNo();
        }
        row.setPrescriptionNo(prescriptionNo);
        if (req != null) {
            row.setOrderingDepartmentId(req.getOrgId());
            if (req.getOrgId() != null) {
                row.setOrderingDepartmentName(orderingDepartmentNameByOrgId.get(req.getOrgId()));
            }
            row.setUsageText(this.buildUsageText(req, labels, outpatientMode));
        }
        if (slip.getMedicationId() != null && productByMedicationId != null) {
            ProductDefinitionDisplayDto product = productByMedicationId.get(slip.getMedicationId());
            if (product != null) {
                row.setMedicationCode(product.getProductBusNo());
                row.setMedicationName(product.getProductName());
                row.setMedicationSpec(product.getTotalVolume());
                row.setManufacturerText(product.getManufacturer());
            }
        }
        return row;
    }

    /**
     * 将中性平铺行投影为住院发退药行（追加就诊 busNo 等住院专属列）。
     *
     * @param base 门诊/住院共用装配结果
     * @return 住院行 DTO
     */
    private InpatientPharmacyDispenseRecordQueryRowDto copyToInpatientRow(PharmacyDispenseRecordQueryRowDto base) {
        InpatientPharmacyDispenseRecordQueryRowDto row = new InpatientPharmacyDispenseRecordQueryRowDto();
        row.setDispenseId(base.getDispenseId());
        row.setRecordTypeCode(base.getRecordTypeCode());
        row.setRecordTypeText(base.getRecordTypeText());
        row.setDispenseTime(base.getDispenseTime());
        row.setPharmacyLocationId(base.getPharmacyLocationId());
        row.setPharmacyLocationName(base.getPharmacyLocationName());
        row.setDispenserPractitionerId(base.getDispenserPractitionerId());
        row.setDispenserName(base.getDispenserName());
        row.setPatientId(base.getPatientId());
        row.setPatientName(base.getPatientName());
        row.setEncounterId(base.getEncounterId());
        row.setEncounterBusNo(base.getEncounterBusNo());
        row.setPrescriptionNo(base.getPrescriptionNo());
        row.setMedicationId(base.getMedicationId());
        row.setMedicationCode(base.getMedicationCode());
        row.setMedicationName(base.getMedicationName());
        row.setMedicationSpec(base.getMedicationSpec());
        row.setQuantity(base.getQuantity());
        row.setUnitCode(base.getUnitCode());
        row.setUnitText(base.getUnitText());
        row.setUnitPrice(base.getUnitPrice());
        row.setTotalAmount(base.getTotalAmount());
        row.setLotNumber(base.getLotNumber());
        row.setTraceNo(base.getTraceNo());
        row.setOrderingDepartmentId(base.getOrderingDepartmentId());
        row.setOrderingDepartmentName(base.getOrderingDepartmentName());
        row.setUsageText(base.getUsageText());
        row.setManufacturerText(base.getManufacturerText());
        row.setSummaryNo(base.getSummaryNo());
        row.setMedReqId(base.getMedReqId());
        return row;
    }

    /**
     * 批量预热发退药列表展示所需术语标签（频次、途径、剂量单位、计量单位）。
     *
     * @param outpatientMode true 时额外加载门诊 TCM 给药途径
     * @return 标签缓存
     */
    private AssemblyLabels loadTerminologyLabels(boolean outpatientMode) {
        AssemblyLabels labels = new AssemblyLabels();
        labels.rateLabels = this.frequencyOptionLoader.buildRateLabelMap();
        labels.methodLabels =
            this.terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.METHOD_CODE);
        if (outpatientMode) {
            labels.tcmMethodLabels =
                this.terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.TCM_METHOD_CODE);
        }
        labels.doseUnitLabels =
            this.terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.DOSE_UNIT);
        labels.unitLabels =
            this.terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.UNIT_CODE);
        return labels;
    }

    /**
     * 判定平铺行是否为退药记录（类型 REFUND 或状态 REFUNDED 任一为真）。
     *
     * @param slip 发放事实读模型
     * @return 退药行为 true
     */
    private boolean isRefundRecordRow(MedicationDispenseSlipQueryModel slip) {
        if (slip.getDispenseEnum() == MedicationDispenseType.REFUND) {
            return true;
        }
        if (slip.getStatusEnum() == DispenseStatus.REFUNDED) {
            return true;
        }
        return false;
    }

    /**
     * 拼接药嘱用法展示文案：频次 + 途径 + 单次剂量（门诊含 TCM 途径解析）。
     *
     * @param req            关联药嘱
     * @param labels         术语标签
     * @param outpatientMode 门诊模式开关
     * @return 用法文本；无药嘱时 null
     */
    private String buildUsageText(
        MedicationRequestListQueryModel req,
        AssemblyLabels labels,
        boolean outpatientMode) {
        if (req == null) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        String frequency = OrderCatalogDisplayAssembler.resolveCategoryDisplay(labels.rateLabels, req.getRateCode());
        if (StringUtils.isNotEmpty(frequency)) {
            parts.add(frequency);
        }
        String routeText;
        if (outpatientMode) {
            routeText = TcmMethodCodeDisplayAssembler.resolveMethodDisplayName(
                labels.tcmMethodLabels, labels.methodLabels, req.getTcmFlag(), req.getMethodCode());
        } else {
            routeText = OrderCatalogDisplayAssembler.resolveCategoryDisplay(labels.methodLabels, req.getMethodCode());
        }
        if (StringUtils.isNotEmpty(routeText)) {
            parts.add(routeText);
        }
        if (req.getDose() != null) {
            String doseUnit =
                OrderCatalogDisplayAssembler.resolveCategoryDisplay(labels.doseUnitLabels, req.getDoseUnitCode());
            String dosePart = req.getDose().toPlainString();
            if (StringUtils.isNotEmpty(doseUnit)) {
                dosePart = dosePart + doseUnit;
            }
            parts.add(dosePart);
        }
        if (parts.isEmpty()) {
            return null;
        }
        return String.join(" ", parts);
    }

    /**
     * 批量加载发放行涉及的药房名称。
     *
     * @param slipRows 发放事实读模型列表
     * @return 库位主键 → 药房名称
     */
    private Map<Long, String> loadPharmacyNames(List<MedicationDispenseSlipQueryModel> slipRows) {
        LinkedHashSet<Long> locationIds = new LinkedHashSet<>();
        for (MedicationDispenseSlipQueryModel slip : slipRows) {
            if (slip != null && slip.getLocationId() != null) {
                locationIds.add(slip.getLocationId());
            }
        }
        if (locationIds.isEmpty()) {
            return Map.of();
        }
        return this.locationQueryService.mapNamesByIds(locationIds);
    }

    private static final class AssemblyLabels {
        private Map<String, String> rateLabels;
        private Map<String, String> methodLabels;
        private Map<String, String> tcmMethodLabels;
        private Map<String, String> doseUnitLabels;
        private Map<String, String> unitLabels;
    }
}
