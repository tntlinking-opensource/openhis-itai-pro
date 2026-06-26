/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.module.querysupport.pharmacy;

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
import com.openhis.health.module.common.assembler.advice.TcmMethodCodeDisplaySupport;
import com.openhis.health.module.common.assembler.option.FrequencyOptionLoader;
import com.openhis.health.module.common.assembler.option.TerminologyOptionLoader;
import com.openhis.health.module.common.assembler.order.OrderCatalogDisplayAssembler;
import com.openhis.health.module.querysupport.pharmacy.dto.InpatientPharmacyDispenseRecordQueryRowDto;
import com.openhis.health.module.querysupport.pharmacy.dto.PharmacyDispenseRecordQueryRowDto;
import com.openhis.health.module.querysupport.product.dto.ProductDefinitionDisplayDto;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
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
        if (StringUtils.isNotBlank(slip.getUnitCode())) {
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
        if (StringUtils.isBlank(prescriptionNo) && req != null) {
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
            }
        }
        return row;
    }

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
        row.setSummaryNo(base.getSummaryNo());
        row.setMedReqId(base.getMedReqId());
        return row;
    }

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

    private boolean isRefundRecordRow(MedicationDispenseSlipQueryModel slip) {
        if (slip.getDispenseEnum() == MedicationDispenseType.REFUND) {
            return true;
        }
        if (slip.getStatusEnum() == DispenseStatus.REFUNDED) {
            return true;
        }
        return false;
    }

    private String buildUsageText(
        MedicationRequestListQueryModel req,
        AssemblyLabels labels,
        boolean outpatientMode) {
        if (req == null) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        String frequency = OrderCatalogDisplayAssembler.resolveCategoryDisplay(labels.rateLabels, req.getRateCode());
        if (StringUtils.isNotBlank(frequency)) {
            parts.add(frequency);
        }
        String routeText;
        if (outpatientMode) {
            routeText = TcmMethodCodeDisplaySupport.resolveMethodDisplayName(
                labels.tcmMethodLabels, labels.methodLabels, req.getTcmFlag(), req.getMethodCode());
        } else {
            routeText = OrderCatalogDisplayAssembler.resolveCategoryDisplay(labels.methodLabels, req.getMethodCode());
        }
        if (StringUtils.isNotBlank(routeText)) {
            parts.add(routeText);
        }
        if (req.getDose() != null) {
            String doseUnit =
                OrderCatalogDisplayAssembler.resolveCategoryDisplay(labels.doseUnitLabels, req.getDoseUnitCode());
            String dosePart = req.getDose().toPlainString();
            if (StringUtils.isNotBlank(doseUnit)) {
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
