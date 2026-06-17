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

package com.openhis.health.application.inpatient.nursestation.dispensesummary.assembler;

import com.openhis.health.module.common.assembler.product.MedicationPackageSplitQuantityDisplaySupport;
import com.openhis.health.module.common.assembler.product.ProductDefinitionCatalogUnitBandsAssembler;
import com.openhis.health.application.inpatient.nursestation.dispensesummary.dto.NurseStationDispenseSummaryOrderRowDto;
import com.openhis.health.application.inpatient.nursestation.dispensesummary.dto.NurseStationDispenseSummaryPatientGroupDto;
import com.openhis.health.application.inpatient.nursestation.dispensesummary.dto.NurseStationDispenseSummarySlipHeaderDto;
import com.openhis.health.application.inpatient.nursestation.dispensesummary.dto.NurseStationDispenseSummarySlipLineDto;
import com.openhis.health.application.inpatient.nursestation.dispensesummary.dto.NurseStationDispenseSummaryTimePointDto;
import com.openhis.health.module.querysupport.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.domain.administration.encounter.queries.model.InpatientEncounterContextQueryModel;
import com.openhis.health.domain.administration.location.entity.Location;
import com.openhis.health.domain.medication.medicationdispense.queries.model.MedicationDispensePickupPendingQueryModel;
import com.openhis.health.domain.medication.medicationrequest.queries.model.MedicationRequestListQueryModel;
import com.openhis.health.domain.share.constants.InpatientNurseStationConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.DispenseStatus;
import com.openhis.health.domain.share.enums.SupplyStatus;
import com.openhis.health.domain.share.enums.TherapyType;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.health.domain.workflow.supplyrequest.queries.model.MedicationSummarySlipLineQueryModel;
import com.openhis.health.domain.workflow.supplyrequest.queries.model.MedicationSummarySlipQueryModel;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 住院护士站汇总领药列表装配（IF-IP-SUM-01/02/03 Asm）。
 */
@Component
@RequiredArgsConstructor
public class NurseStationDispenseSummaryListAssembler {

    /**
     * 从护士站执行药嘱列表按中西药 Tab 收窄 medReqId（IF-IP-SUM-01 Pre ⑤）。
     *
     * @param executeOrders       {@link com.openhis.health.domain.medication.medicationrequest.service.MedicationRequestQueryService#listInpatientNurseStationExecuteOrders} 结果
     * @param medicineCategoryTab west / tcm
     * @return 解析后的 medReqId 列表；无命中时返回空表（域 Query 将返回空页）
     */
    public List<Long> resolveMedReqIds(
        List<MedicationRequestListQueryModel> executeOrders,
        String medicineCategoryTab) {
        List<Long> result = new ArrayList<>();
        if (executeOrders == null || executeOrders.isEmpty()) {
            return result;
        }
        String tab = StringUtils.trimToEmpty(medicineCategoryTab);
        for (MedicationRequestListQueryModel row : executeOrders) {
            if (row == null || row.getId() == null) {
                continue;
            }
            if (InpatientNurseStationConstants.MEDICINE_CATEGORY_TAB_WEST.equals(tab)) {
                if (Boolean.TRUE.equals(row.getTcmFlag())) {
                    continue;
                }
            } else if (InpatientNurseStationConstants.MEDICINE_CATEGORY_TAB_TCM.equals(tab)) {
                if (!Boolean.TRUE.equals(row.getTcmFlag())) {
                    continue;
                }
            }
            result.add(row.getId());
        }
        return result;
    }

    /**
     * 待汇总扁平行 → 就诊→医嘱→时间点分组（IF-IP-SUM-01 Asm ⑩；不做数量合并）。
     */
    public List<NurseStationDispenseSummaryPatientGroupDto> buildPatientGroups(
        Page<MedicationDispensePickupPendingQueryModel> page,
        Map<Long, MedicationRequestListQueryModel> medReqById,
        Map<String, ProductDefinitionDisplayDto> productByKey,
        Map<Long, InpatientEncounterContextQueryModel> encounterById,
        Map<Long, Location> locationById) {
        if (page == null || page.getContent() == null || page.getContent().isEmpty()) {
            return new ArrayList<>();
        }
        Map<Long, NurseStationDispenseSummaryPatientGroupDto> groupByEncounter = new LinkedHashMap<>();
        Map<Long, Map<Long, NurseStationDispenseSummaryOrderRowDto>> orderRowByEncounterAndMedReq = new LinkedHashMap<>();
        for (MedicationDispensePickupPendingQueryModel line : page.getContent()) {
            if (line == null || line.getEncounterId() == null || line.getMedReqId() == null) {
                continue;
            }
            NurseStationDispenseSummaryPatientGroupDto patientGroup = groupByEncounter.get(line.getEncounterId());
            if (patientGroup == null) {
                patientGroup = new NurseStationDispenseSummaryPatientGroupDto();
                patientGroup.setEncounterId(line.getEncounterId());
                patientGroup.setPatientId(line.getPatientId());
                InpatientEncounterContextQueryModel encounter = encounterById.get(line.getEncounterId());
                if (encounter != null) {
                    patientGroup.setPatientName(encounter.getPatientName());
                    patientGroup.setBedDisplay(encounter.getBedName());
                }
                patientGroup.setOrderRows(new ArrayList<>());
                groupByEncounter.put(line.getEncounterId(), patientGroup);
            }
            Map<Long, NurseStationDispenseSummaryOrderRowDto> orderRowByMedReq =
                orderRowByEncounterAndMedReq.get(line.getEncounterId());
            if (orderRowByMedReq == null) {
                orderRowByMedReq = new LinkedHashMap<>();
                orderRowByEncounterAndMedReq.put(line.getEncounterId(), orderRowByMedReq);
            }
            NurseStationDispenseSummaryOrderRowDto orderRow = orderRowByMedReq.get(line.getMedReqId());
            if (orderRow == null) {
                orderRow = this.buildOrderRow(line, medReqById, productByKey, locationById);
                orderRow.setTimePoints(new ArrayList<>());
                orderRowByMedReq.put(line.getMedReqId(), orderRow);
                patientGroup.getOrderRows().add(orderRow);
            }
            NurseStationDispenseSummaryTimePointDto timePoint = this.buildTimePoint(line, productByKey);
            orderRow.getTimePoints().add(timePoint);
        }
        List<NurseStationDispenseSummaryPatientGroupDto> groups = new ArrayList<>(groupByEncounter.values());
        for (NurseStationDispenseSummaryPatientGroupDto group : groups) {
            if (group.getOrderRows() == null) {
                continue;
            }
            group.getOrderRows().sort(Comparator.comparing(
                NurseStationDispenseSummaryOrderRowDto::getMedReqId,
                Comparator.nullsLast(Long::compareTo)));
            for (NurseStationDispenseSummaryOrderRowDto orderRow : group.getOrderRows()) {
                if (orderRow.getTimePoints() == null) {
                    continue;
                }
                orderRow.getTimePoints().sort(Comparator.comparing(
                    NurseStationDispenseSummaryTimePointDto::getPlannedDispenseTime,
                    Comparator.nullsLast(java.time.LocalDateTime::compareTo)));
            }
        }
        return groups;
    }

    /**
     * 域汇总单头 QueryModel → 展示 DTO（IF-IP-SUM-02/03）。
     */
    public NurseStationDispenseSummarySlipHeaderDto toSlipHeaderDto(
        MedicationSummarySlipQueryModel model,
        Map<Long, Location> locationById,
        Map<Long, String> practitionerNameById) {
        if (model == null) {
            return null;
        }
        NurseStationDispenseSummarySlipHeaderDto dto = new NurseStationDispenseSummarySlipHeaderDto();
        dto.setBusNo(model.getBusNo());
        dto.setApplyTime(model.getApplyTime());
        dto.setDispenseTime(model.getDispenseTime());
        dto.setPurposeLocationId(model.getPurposeLocationId());
        Location location = locationById.get(model.getPurposeLocationId());
        if (location != null) {
            dto.setPurposeLocationName(location.getName());
        }
        dto.setApplicantId(model.getApplicantId());
        dto.setApplicantName(practitionerNameById.get(model.getApplicantId()));
        dto.setReceiverId(model.getReceiverId());
        dto.setReceiverName(practitionerNameById.get(model.getReceiverId()));
        dto.setSupplyStatusEnum(model.getSupplyStatusEnum());
        dto.setSupplyStatusEnumText(this.resolveSupplyStatusText(model.getSupplyStatusEnum()));
        dto.setDeliveryStatusEnum(model.getDeliveryStatusEnum());
        dto.setDeliveryStatusEnumText(this.resolveDeliveryStatusText(model.getDeliveryStatusEnum()));
        return dto;
    }

    /**
     * 域合并明细 → 展示 DTO（IF-IP-SUM-03；禁止输出 lotNumber）。
     */
    public List<NurseStationDispenseSummarySlipLineDto> buildSlipLineDtos(
        List<MedicationSummarySlipLineQueryModel> domainLines,
        Map<String, ProductDefinitionDisplayDto> productByKey) {
        List<NurseStationDispenseSummarySlipLineDto> lines = new ArrayList<>();
        if (domainLines == null) {
            return lines;
        }
        for (MedicationSummarySlipLineQueryModel row : domainLines) {
            if (row == null) {
                continue;
            }
            NurseStationDispenseSummarySlipLineDto dto = new NurseStationDispenseSummarySlipLineDto();
            dto.setSupplyRequestId(row.getSupplyRequestId());
            dto.setItemId(row.getItemId());
            dto.setItemQuantity(row.getItemQuantity());
            dto.setUnitCode(row.getUnitCode());
            dto.setUnitPrice(row.getUnitPrice());
            dto.setTotalPrice(row.getTotalPrice());
            String productKey = ProductDefinitionKeyUtil.buildCompositeKey(
                TableNameConstants.MED_MEDICATION_DEFINITION, row.getItemId());
            ProductDefinitionDisplayDto product = productByKey.get(productKey);
            if (product != null) {
                dto.setMedicationName(StringUtils.firstNonBlank(product.getProductName(), product.getMerchandiseName()));
                dto.setSpecificationText(this.resolveSpecificationText(product));
                dto.setUnitCodeText(ProductDefinitionCatalogUnitBandsAssembler.resolveUnitDisplayName(
                    null, row.getUnitCode(), product));
                dto.setDisplayQuantityText(MedicationPackageSplitQuantityDisplaySupport.buildDisplayQuantityText(
                    row.getItemQuantity(), product));
            }
            lines.add(dto);
        }
        return lines;
    }

    /**
     * 批量索引 locationId → Location。
     */
    public Map<Long, Location> indexLocations(List<Location> locations) {
        Map<Long, Location> map = new HashMap<>();
        if (locations == null) {
            return map;
        }
        for (Location location : locations) {
            if (location != null && location.getId() != null) {
                map.put(location.getId(), location);
            }
        }
        return map;
    }

    private NurseStationDispenseSummaryOrderRowDto buildOrderRow(
        MedicationDispensePickupPendingQueryModel line,
        Map<Long, MedicationRequestListQueryModel> medReqById,
        Map<String, ProductDefinitionDisplayDto> productByKey,
        Map<Long, Location> locationById) {
        NurseStationDispenseSummaryOrderRowDto orderRow = new NurseStationDispenseSummaryOrderRowDto();
        orderRow.setMedReqId(line.getMedReqId());
        orderRow.setMedicationId(line.getMedicationId());
        orderRow.setDosageInstruction(line.getDosageInstruction());
        orderRow.setMethodCode(line.getMethodCode());
        orderRow.setFrequencyCode(line.getFrequencyCode());
        orderRow.setLocationId(line.getLocationId());
        Location location = locationById.get(line.getLocationId());
        if (location != null) {
            orderRow.setLocationName(location.getName());
        }
        MedicationRequestListQueryModel medReq = medReqById.get(line.getMedReqId());
        if (medReq != null) {
            orderRow.setGroupId(medReq.getGroupId());
            orderRow.setTherapyEnum(medReq.getTherapyEnum());
            orderRow.setTherapyEnumText(this.resolveTherapyDisplay(medReq.getTherapyEnum()));
        }
        String productKey = ProductDefinitionKeyUtil.buildCompositeKey(
            TableNameConstants.MED_MEDICATION_DEFINITION, line.getMedicationId());
        ProductDefinitionDisplayDto product = productByKey.get(productKey);
        if (product != null) {
            orderRow.setMedicationName(StringUtils.firstNonBlank(product.getProductName(), product.getMerchandiseName()));
            orderRow.setSpecificationText(this.resolveSpecificationText(product));
            orderRow.setMethodCodeText(StringUtils.firstNonBlank(product.getMethodCodeDictText(), line.getMethodCode()));
            orderRow.setFrequencyCodeText(StringUtils.firstNonBlank(product.getRateCodeDictText(), line.getFrequencyCode()));
        } else {
            orderRow.setMethodCodeText(line.getMethodCode());
            orderRow.setFrequencyCodeText(line.getFrequencyCode());
        }
        return orderRow;
    }

    private NurseStationDispenseSummaryTimePointDto buildTimePoint(
        MedicationDispensePickupPendingQueryModel line,
        Map<String, ProductDefinitionDisplayDto> productByKey) {
        NurseStationDispenseSummaryTimePointDto timePoint = new NurseStationDispenseSummaryTimePointDto();
        timePoint.setDispenseId(line.getDispenseId());
        timePoint.setProcedureId(line.getProcedureId());
        timePoint.setPlannedDispenseTime(line.getPlannedDispenseTime());
        timePoint.setQuantity(line.getQuantity());
        timePoint.setUnitCode(line.getUnitCode());
        timePoint.setChargeUnitPrice(line.getChargeUnitPrice());
        String productKey = ProductDefinitionKeyUtil.buildCompositeKey(
            TableNameConstants.MED_MEDICATION_DEFINITION, line.getMedicationId());
        ProductDefinitionDisplayDto product = productByKey.get(productKey);
        timePoint.setUnitCodeText(ProductDefinitionCatalogUnitBandsAssembler.resolveUnitDisplayName(
            null, line.getUnitCode(), product));
        return timePoint;
    }

    private String resolveSpecificationText(ProductDefinitionDisplayDto product) {
        if (product == null) {
            return null;
        }
        return StringUtils.firstNonBlank(product.getTotalVolume(), product.getMerchandiseName());
    }

    private String resolveTherapyDisplay(String therapyCode) {
        if (StringUtils.isBlank(therapyCode)) {
            return null;
        }
        TherapyType therapyType = EnumUtil.fromCode(TherapyType.class, therapyCode);
        if (therapyType != null) {
            return therapyType.getDisplay();
        }
        return null;
    }

    private String resolveSupplyStatusText(String supplyStatusEnum) {
        if (StringUtils.isBlank(supplyStatusEnum)) {
            return null;
        }
        SupplyStatus status = EnumUtil.fromCode(SupplyStatus.class, supplyStatusEnum.trim());
        if (status == null) {
            return null;
        }
        if (SupplyStatus.PENDING_APPROVAL.equals(status)) {
            return InpatientNurseStationConstants.SUMMARY_SUPPLY_STATUS_DRAFT_DISPLAY;
        }
        if (SupplyStatus.APPROVAL.equals(status)) {
            return InpatientNurseStationConstants.SUMMARY_SUPPLY_STATUS_APPROVAL_DISPLAY;
        }
        if (SupplyStatus.AGREE.equals(status)) {
            return InpatientNurseStationConstants.SUMMARY_SUPPLY_STATUS_AGREE_DISPLAY;
        }
        if (SupplyStatus.REJECT.equals(status)) {
            return InpatientNurseStationConstants.SUMMARY_SUPPLY_STATUS_REJECT_DISPLAY;
        }
        if (SupplyStatus.WITHDRAW.equals(status)) {
            return InpatientNurseStationConstants.SUMMARY_SUPPLY_STATUS_WITHDRAW_DISPLAY;
        }
        return status.getDisplay();
    }

    private String resolveDeliveryStatusText(String deliveryStatusEnum) {
        if (StringUtils.isBlank(deliveryStatusEnum)) {
            return null;
        }
        DispenseStatus status = EnumUtil.fromCode(DispenseStatus.class, deliveryStatusEnum.trim());
        if (status == null) {
            return null;
        }
        if (DispenseStatus.PREPARATION.equals(status)) {
            return InpatientNurseStationConstants.SUMMARY_DELIVERY_STATUS_PENDING_DISPLAY;
        }
        if (DispenseStatus.COMPLETED.equals(status)) {
            return InpatientNurseStationConstants.SUMMARY_DELIVERY_STATUS_COMPLETED_DISPLAY;
        }
        return status.getDisplay();
    }
}
