/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.surplusretention.support;

import com.openhis.health.application.outpatient.pharmacy.dispenserecord.support.PharmacyDispenseRecordChargePricingAssembler;
import com.openhis.health.application.report.surplusretention.dto.SurplusRetentionReportRowDto;
import com.openhis.health.application.shared.option.TerminologyOptionLoader;
import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.openhis.health.domain.administration.encounter.entity.Encounter;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.domain.administration.patient.service.PatientQueryService;
import com.openhis.health.domain.custom.supplier.service.SupplierQueryService;
import com.openhis.health.domain.medication.medicationdefinition.entity.MedicationDefinition;
import com.openhis.health.domain.medication.medicationdefinition.service.MedicationDefinitionQueryService;
import com.openhis.health.domain.medication.medicationdispense.entity.MedicationDispense;
import com.openhis.health.domain.medication.medicationdispense.queries.model.MedicationDispenseSlipQueryModel;
import com.openhis.health.domain.medication.medicationdispense.service.MedicationDispenseQueryService;
import com.openhis.health.domain.medication.medicationrequest.entity.MedicationRequest;
import com.openhis.health.domain.medication.medicationrequest.queries.model.MedicationRequestListQueryModel;
import com.openhis.health.domain.medication.medicationrequest.service.MedicationRequestQueryService;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.SurplusRetentionReportConstants;
import com.openhis.health.domain.share.constants.TerminologyCodeSystemNameConstants;
import com.openhis.health.domain.share.enums.DispenseStatus;
import com.openhis.health.domain.share.enums.EncounterClass;
import com.openhis.health.domain.share.enums.MedicationDispenseType;
import com.openhis.health.domain.share.utils.MonetaryAmountSupport;
import com.openhis.whale.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 药品结余留用报表行装配：页内主键批量装载后映射 19 列（AS-RPT-015；#4M-RFND 退药售价补价）。
 */
@Component
@RequiredArgsConstructor
public class SurplusRetentionReportRowAssembler {

    /** 药品申请读侧（签发时间与退药关联药嘱）。 */
    private final MedicationRequestQueryService medicationRequestQueryService;

    /** 药品目录读侧（品名/规格/剂型/包装/集采/厂家）。 */
    private final MedicationDefinitionQueryService medicationDefinitionQueryService;

    /** 发放读侧（实体 → slip，供 #4M-RFND 补价）。 */
    private final MedicationDispenseQueryService medicationDispenseQueryService;

    /** 就诊读侧（门诊住院号、入出院时刻）。 */
    private final EncounterQueryService encounterQueryService;

    /** 患者读侧（姓名批量）。 */
    private final PatientQueryService patientQueryService;

    /** 供应商读侧（生产企业名称）。 */
    private final SupplierQueryService supplierQueryService;

    /** 收费明细读侧（发放锚点售价）。 */
    private final ChargeItemQueryService chargeItemQueryService;

    /** 退药售价补价（#4M-RFND，写正额绝对值）。 */
    private final PharmacyDispenseRecordChargePricingAssembler pharmacyDispenseRecordChargePricingAssembler;

    /** 术语标签批量翻译（类别/剂型/单位）。 */
    private final TerminologyOptionLoader terminologyOptionLoader;

    /**
     * 将患者发退药实体装配为结余留用 19 列行。
     *
     * @param dispenses 发放实体页内容
     * @return 展示行；入参空则空列表
     */
    public List<SurplusRetentionReportRowDto> assembleRows(List<MedicationDispense> dispenses) {
        // 无明细：直接空
        if (dispenses == null || dispenses.isEmpty()) {
            return List.of();
        }
        EnrichmentContext ctx = this.buildEnrichmentContext(dispenses);
        List<SurplusRetentionReportRowDto> rows = new ArrayList<>(dispenses.size());
        // Loop：逐发放行映射 19 列
        for (MedicationDispense dispense : dispenses) {
            // 跳过空行
            if (dispense == null) {
                continue;
            }
            SurplusRetentionReportRowDto row = new SurplusRetentionReportRowDto();
            MedicationRequest request = null;
            if (dispense.getMedReqId() != null) {
                request = ctx.requestById.get(dispense.getMedReqId());
            }
            MedicationDefinition def = null;
            if (dispense.getMedicationId() != null) {
                def = ctx.medicationById.get(dispense.getMedicationId());
            }
            Encounter encounter = null;
            if (dispense.getEncounterId() != null) {
                encounter = ctx.encounterById.get(dispense.getEncounterId());
            }
            // 门诊住院号 ← 就诊 busNo
            if (encounter != null) {
                row.setOutpatientInpatientNo(StringUtils.trimToNull(encounter.getBusNo()));
            }
            // 患者姓名
            if (dispense.getPatientId() != null) {
                row.setPatientName(ctx.patientNameById.get(dispense.getPatientId()));
            }
            // 参保地区/类型：v1 允许空
            row.setInsuredRegion(GeneralSharedConstants.EMPTY_STRING);
            row.setInsuredType(GeneralSharedConstants.EMPTY_STRING);
            // 入出院：仅住院 IMP 填 start/end；门诊空
            if (encounter != null && EncounterClass.IMP.getCode().equals(encounter.getClassEnum())) {
                row.setAdmissionTime(encounter.getStartTime());
                row.setDischargeTime(encounter.getEndTime());
            }
            // 处方日期 ← 申请签发时间
            if (request != null) {
                row.setPrescriptionDate(request.getReqAuthoredTime());
            }
            // 目录列
            if (def != null) {
                row.setDrugGenericName(StringUtils.trimToNull(def.getName()));
                row.setProductName(StringUtils.trimToNull(def.getMerchandiseName()));
                row.setDrugCategoryText(this.labelOf(ctx.categoryLabelByCode, def.getCategoryCode()));
                row.setDoseFormText(this.labelOf(ctx.doseFormLabelByCode, def.getDoseFormCode()));
                row.setSpecification(StringUtils.trimToNull(def.getTotalVolume()));
                String packageUnitText = this.labelOf(ctx.unitLabelByCode, def.getPackageUnitCode());
                row.setPackageUnitText(packageUnitText);
                // 计价单位与包装同口径（售药单位）
                row.setPricingUnitText(packageUnitText);
                // 是否中选 ← centralPurchaseFlag
                if (Boolean.TRUE.equals(def.getCentralPurchaseFlag())) {
                    row.setSelectedProductText(SurplusRetentionReportConstants.YES_TEXT);
                } else {
                    row.setSelectedProductText(SurplusRetentionReportConstants.NO_TEXT);
                }
                if (def.getManufacturerId() != null) {
                    row.setManufacturerName(ctx.manufacturerNameById.get(def.getManufacturerId()));
                }
            } else {
                // 无目录：中选回落否
                row.setSelectedProductText(SurplusRetentionReportConstants.NO_TEXT);
            }
            // 数量 / 售价（退药负号）
            BigDecimal quantity = dispense.getDispenseQuantity();
            if (quantity == null) {
                quantity = dispense.getQuantity();
            }
            if (quantity == null) {
                quantity = BigDecimal.ZERO;
            }
            boolean refundRow = this.isRefundDispense(dispense);
            BigDecimal unitPrice = null;
            BigDecimal totalAmount = null;
            if (dispense.getId() != null) {
                unitPrice = ctx.unitPriceByDispenseId.get(dispense.getId());
                totalAmount = ctx.totalPriceByDispenseId.get(dispense.getId());
            }
            // 退药：数量与金额取负；单价保持正额（补价写入为正额绝对值）
            if (refundRow) {
                if (quantity.signum() > 0) {
                    quantity = quantity.negate();
                }
                if (totalAmount != null) {
                    totalAmount = MonetaryAmountSupport.applySignAtCalcScale(
                        totalAmount.abs(),
                        BigDecimal.valueOf(-1L));
                }
            }
            // 缺价保持 null，禁止 scaleDisplay(null)→0 冒充免费药
            if (unitPrice != null) {
                row.setUnitPrice(MonetaryAmountSupport.scaleDisplay(unitPrice));
            }
            row.setUsageQuantity(quantity);
            if (totalAmount != null) {
                row.setUsageAmount(MonetaryAmountSupport.scaleDisplay(totalAmount));
            }
            rows.add(row);
        }
        return rows;
    }

    /**
     * 批量装载申请/目录/就诊/患者/厂家/售价上下文。
     *
     * @param dispenses 本页发放行
     * @return 富集上下文
     */
    private EnrichmentContext buildEnrichmentContext(List<MedicationDispense> dispenses) {
        EnrichmentContext ctx = new EnrichmentContext();
        LinkedHashSet<Long> medReqIds = new LinkedHashSet<>();
        LinkedHashSet<Long> medicationIds = new LinkedHashSet<>();
        LinkedHashSet<Long> encounterIds = new LinkedHashSet<>();
        LinkedHashSet<Long> patientIds = new LinkedHashSet<>();
        LinkedHashSet<Long> dispenseIds = new LinkedHashSet<>();
        List<MedicationDispenseSlipQueryModel> slipRows = new ArrayList<>(dispenses.size());
        for (MedicationDispense dispense : dispenses) {
            if (dispense == null) {
                continue;
            }
            if (dispense.getMedReqId() != null) {
                medReqIds.add(dispense.getMedReqId());
            }
            if (dispense.getMedicationId() != null) {
                medicationIds.add(dispense.getMedicationId());
            }
            if (dispense.getEncounterId() != null) {
                encounterIds.add(dispense.getEncounterId());
            }
            if (dispense.getPatientId() != null) {
                patientIds.add(dispense.getPatientId());
            }
            if (dispense.getId() != null) {
                dispenseIds.add(dispense.getId());
            }
            slipRows.add(this.medicationDispenseQueryService.toSlipQueryModel(dispense));
        }
        // Pre：申请实体
        Map<Long, MedicationRequest> requestById =
            this.medicationRequestQueryService.mapEntitiesByIds(medReqIds);
        ctx.requestById = requestById;
        // Pre：目录
        Map<Long, MedicationDefinition> medicationById = new HashMap<>();
        LinkedHashSet<Long> manufacturerIds = new LinkedHashSet<>();
        for (MedicationDefinition def : this.medicationDefinitionQueryService.findAllByIds(medicationIds)) {
            if (def == null || def.getId() == null) {
                continue;
            }
            medicationById.put(def.getId(), def);
            if (def.getManufacturerId() != null) {
                manufacturerIds.add(def.getManufacturerId());
            }
        }
        ctx.medicationById = medicationById;
        // Pre：就诊
        Map<Long, Encounter> encounterById = new HashMap<>();
        for (Encounter encounter : this.encounterQueryService.listEncounterEntitiesByIds(encounterIds)) {
            if (encounter != null && encounter.getId() != null) {
                encounterById.put(encounter.getId(), encounter);
            }
        }
        ctx.encounterById = encounterById;
        // Pre：患者姓名
        ctx.patientNameById = this.patientQueryService.findPatientNamesByIds(patientIds);
        // Pre：厂家名
        ctx.manufacturerNameById = this.supplierQueryService.mapNamesByIds(manufacturerIds);
        // Pre：术语标签
        ctx.categoryLabelByCode =
            this.terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.MEDICATION_CATEGORY_CODE);
        ctx.doseFormLabelByCode =
            this.terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.DOSE_FORM_CODE);
        ctx.unitLabelByCode =
            this.terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.UNIT_CODE);
        // Pre：ChargeItem 售价（按发放 id）
        Map<Long, BigDecimal> unitPriceByDispenseId = new HashMap<>();
        Map<Long, BigDecimal> totalPriceByDispenseId = new HashMap<>();
        List<ChargeItem> chargeRows =
            this.chargeItemQueryService.listChargeItemsForMedicationDispensePricingByDispenseIds(
                dispenseIds, false);
        this.mergeChargePricingFromChargeItems(chargeRows, unitPriceByDispenseId, totalPriceByDispenseId);
        // Pre：#4M-RFND 退药补原正向售价（写入为正额，行装配再取负）
        Map<Long, MedicationRequestListQueryModel> medicationRequestByMedReqId =
            this.indexMedicationRequestsForRefundSupplement(slipRows);
        this.pharmacyDispenseRecordChargePricingAssembler.supplementRefundPricingFromOriginMedicationRequests(
            slipRows,
            medicationRequestByMedReqId,
            unitPriceByDispenseId,
            totalPriceByDispenseId);
        ctx.unitPriceByDispenseId = unitPriceByDispenseId;
        ctx.totalPriceByDispenseId = totalPriceByDispenseId;
        return ctx;
    }

    /**
     * 仅索引退药行药嘱（正向行不参与补价索引）。
     *
     * @param slipRows 发放事实
     * @return 退药相关 medReqId → 药嘱读模型
     */
    private Map<Long, MedicationRequestListQueryModel> indexMedicationRequestsForRefundSupplement(
        List<MedicationDispenseSlipQueryModel> slipRows) {
        LinkedHashSet<Long> medReqIds = new LinkedHashSet<>();
        if (slipRows == null) {
            return Map.of();
        }
        for (MedicationDispenseSlipQueryModel slip : slipRows) {
            // Gate：非退药行无需药嘱补价索引
            if (slip == null || !this.isRefundSlipRow(slip) || slip.getMedReqId() == null) {
                continue;
            }
            medReqIds.add(slip.getMedReqId());
        }
        Map<Long, MedicationRequestListQueryModel> medicationRequestByMedReqId = new HashMap<>();
        if (medReqIds.isEmpty()) {
            return medicationRequestByMedReqId;
        }
        for (MedicationRequestListQueryModel req
            : this.medicationRequestQueryService.listMedicationRequestListModelsByIds(medReqIds)) {
            if (req != null && req.getId() != null) {
                medicationRequestByMedReqId.put(req.getId(), req);
            }
        }
        return medicationRequestByMedReqId;
    }

    /**
     * 合并发放锚点收费行售价（同 dispense 多行加总后反推单价）。
     *
     * @param chargeRows    收费行
     * @param unitPriceOut  dispenseId → 单价
     * @param totalPriceOut dispenseId → 行额合计
     */
    private void mergeChargePricingFromChargeItems(
        List<ChargeItem> chargeRows,
        Map<Long, BigDecimal> unitPriceOut,
        Map<Long, BigDecimal> totalPriceOut) {
        if (chargeRows == null || chargeRows.isEmpty()) {
            return;
        }
        Map<Long, BigDecimal> totalByDispense = new HashMap<>();
        Map<Long, BigDecimal> qtyByDispense = new HashMap<>();
        for (ChargeItem chargeRow : chargeRows) {
            if (chargeRow == null || chargeRow.getDispenseId() == null) {
                continue;
            }
            Long dispenseKey = chargeRow.getDispenseId();
            if (chargeRow.getTotalPrice() != null) {
                BigDecimal existingTotal = totalByDispense.get(dispenseKey);
                if (existingTotal == null) {
                    totalByDispense.put(dispenseKey, chargeRow.getTotalPrice());
                } else {
                    totalByDispense.put(dispenseKey, existingTotal.add(chargeRow.getTotalPrice()));
                }
            }
            if (chargeRow.getQuantityValue() != null) {
                BigDecimal existingQty = qtyByDispense.get(dispenseKey);
                if (existingQty == null) {
                    qtyByDispense.put(dispenseKey, chargeRow.getQuantityValue());
                } else {
                    qtyByDispense.put(dispenseKey, existingQty.add(chargeRow.getQuantityValue()));
                }
            }
        }
        for (Map.Entry<Long, BigDecimal> entry : totalByDispense.entrySet()) {
            Long dispenseKey = entry.getKey();
            BigDecimal totalVal = entry.getValue();
            totalPriceOut.put(dispenseKey, totalVal);
            BigDecimal qtyVal = qtyByDispense.get(dispenseKey);
            if (qtyVal != null && qtyVal.compareTo(BigDecimal.ZERO) > 0) {
                unitPriceOut.put(dispenseKey, MonetaryAmountSupport.unitPriceFromLineTotal(totalVal, qtyVal));
            }
        }
    }

    /**
     * 判定发放实体是否退药行（类型 REFUND 或状态 REFUNDED）。
     *
     * @param dispense 发放实体
     * @return 退药为 true
     */
    private boolean isRefundDispense(MedicationDispense dispense) {
        if (dispense == null) {
            return false;
        }
        if (dispense.getDispenseEnum() == MedicationDispenseType.REFUND) {
            return true;
        }
        return DispenseStatus.REFUNDED.equals(dispense.getStatusEnum());
    }

    /**
     * 判定 slip 是否退药行。
     *
     * @param slip 发放事实
     * @return 退药为 true
     */
    private boolean isRefundSlipRow(MedicationDispenseSlipQueryModel slip) {
        if (slip == null) {
            return false;
        }
        if (slip.getDispenseEnum() == MedicationDispenseType.REFUND) {
            return true;
        }
        return DispenseStatus.REFUNDED.equals(slip.getStatusEnum());
    }

    /**
     * 术语 code → 展示文案；缺省空串。
     *
     * @param labelByCode 标签图
     * @param code        术语码
     * @return 文案或空串
     */
    private String labelOf(Map<String, String> labelByCode, String code) {
        String normalized = StringUtils.trimToNull(code);
        if (normalized == null || labelByCode == null) {
            return GeneralSharedConstants.EMPTY_STRING;
        }
        String label = labelByCode.get(normalized);
        if (label == null) {
            return GeneralSharedConstants.EMPTY_STRING;
        }
        return label;
    }

    /**
     * 页内富集上下文。
     */
    private static final class EnrichmentContext {

        /** 申请 id → 实体。 */
        private Map<Long, MedicationRequest> requestById = Map.of();

        /** 目录 id → 实体。 */
        private Map<Long, MedicationDefinition> medicationById = Map.of();

        /** 就诊 id → 实体。 */
        private Map<Long, Encounter> encounterById = Map.of();

        /** 患者 id → 姓名。 */
        private Map<Long, String> patientNameById = Map.of();

        /** 厂家 id → 名称。 */
        private Map<Long, String> manufacturerNameById = Map.of();

        /** 药品类别 code → 文案。 */
        private Map<String, String> categoryLabelByCode = Map.of();

        /** 剂型 code → 文案。 */
        private Map<String, String> doseFormLabelByCode = Map.of();

        /** 单位 code → 文案。 */
        private Map<String, String> unitLabelByCode = Map.of();

        /** 发放 id → 售价单价。 */
        private Map<Long, BigDecimal> unitPriceByDispenseId = Map.of();

        /** 发放 id → 售价行额。 */
        private Map<Long, BigDecimal> totalPriceByDispenseId = Map.of();
    }
}
