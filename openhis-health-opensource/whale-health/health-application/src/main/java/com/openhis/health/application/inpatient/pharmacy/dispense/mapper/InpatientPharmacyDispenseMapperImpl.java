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

package com.openhis.health.application.inpatient.pharmacy.dispense.mapper;

import com.openhis.health.application.common.assembler.option.OptionDtoAssembler;
import com.openhis.health.application.common.assembler.order.OrderCatalogDisplayAssembler;
import com.openhis.health.application.common.pagination.PagedResultBuilder;
import com.openhis.health.application.inpatient.pharmacy.dispense.dto.CancelMedicationDispenseLineSubmitDto;
import com.openhis.health.application.inpatient.pharmacy.dispense.dto.EncounterMedicationDispenseSlipRowDto;
import com.openhis.health.application.inpatient.pharmacy.dispense.dto.PendingDispenseInpatientEncounterQueueRowDto;
import com.openhis.health.application.inpatient.pharmacy.dispense.dto.PractitionerOptionDto;
import com.openhis.health.application.inpatient.pharmacy.dispense.dto.SavePrepareInpatientDispenseLineSubmitDto;
import com.openhis.health.application.inpatient.pharmacy.dispense.response.CancelInpatientMedicationDispenseResponse;
import com.openhis.health.application.inpatient.pharmacy.dispense.response.DispenseInpatientMedicationsResponse;
import com.openhis.health.application.inpatient.pharmacy.dispense.response.LoadInpatientPharmacyDispensePageInitResponse;
import com.openhis.health.application.inpatient.pharmacy.dispense.response.PageEncounterMedicationDispenseSlipsResponse;
import com.openhis.health.application.inpatient.pharmacy.dispense.response.PagePendingDispenseInpatientEncounterQueueResponse;
import com.openhis.health.application.inpatient.pharmacy.dispense.response.SavePrepareInpatientMedicationDispenseResponse;
import com.openhis.health.domain.administration.organization.entity.Organization;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.medication.medicationdispense.command.CancelMedicationDispenseCommand;
import com.openhis.health.domain.medication.medicationdispense.command.DispenseMedicationCommand;
import com.openhis.health.domain.medication.medicationdispense.command.PrepareMedicationDispenseCommand;
import com.openhis.health.domain.medication.medicationdispense.entity.MedicationDispense;
import com.openhis.health.domain.medication.medicationrequest.queries.model.MedicationRequestListQueryModel;
import com.openhis.health.domain.administration.encounter.queries.model.InpatientEncounterContextQueryModel;
import com.openhis.health.domain.medication.medicationdispense.queries.model.MedicationDispenseSlipQueryModel;
import com.openhis.health.application.querysupport.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.domain.share.constants.MedicationCommonConstants;
import com.openhis.health.domain.share.enums.MedicationDispenseType;
import com.openhis.health.domain.share.enums.TherapyType;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.health.domain.share.utils.InpatientEncounterStatusDisplayUtil;
import com.openhis.whale.core.application.dto.OptionDto;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * {@link InpatientPharmacyDispenseMapper} 实现：住院发药 IF-IPP-01～06 的请求/读模型/领域命令/响应字段搬运。
 *
 * <p>职责边界：无业务流程分支与不变量；null 安全回落为空集合或空分页壳（B.1 / A.4）。</p>
 */
@Component
public class InpatientPharmacyDispenseMapperImpl implements InpatientPharmacyDispenseMapper {

    /**
     * IF-IPP-01：装配初始化响应。
     *
     * @param pharmacies                药房机构树
     * @param dispenseStatusOptions     发放状态下拉
     * @param notPerformedReasonOptions 未发药原因下拉
     * @param wardOptions               病区下拉
     * @param preparerPage              药师分页实体页
     * @return 初始化响应
     */
    @Override
    public LoadInpatientPharmacyDispensePageInitResponse toLoadInitResponse(
        List<Organization> pharmacies,
        List<OptionDto> dispenseStatusOptions,
        List<OptionDto> notPerformedReasonOptions,
        List<OptionDto> wardOptions,
        Page<Practitioner> preparerPage) {
        LoadInpatientPharmacyDispensePageInitResponse response = new LoadInpatientPharmacyDispensePageInitResponse();
        if (pharmacies == null) {
            response.setPharmacyOrganizationOptions(new ArrayList<>());
        } else {
            response.setPharmacyOrganizationOptions(
                OptionDtoAssembler.toOptions(pharmacies, Organization::getId, Organization::getName, 0L));
        }
        if (dispenseStatusOptions == null) {
            response.setDispenseStatusFilterOptions(new ArrayList<>());
        } else {
            response.setDispenseStatusFilterOptions(dispenseStatusOptions);
        }
        if (notPerformedReasonOptions == null) {
            response.setNotPerformedReasonOptions(new ArrayList<>());
        } else {
            response.setNotPerformedReasonOptions(notPerformedReasonOptions);
        }
        if (wardOptions == null) {
            response.setWardOptions(new ArrayList<>());
        } else {
            response.setWardOptions(wardOptions);
        }
        if (preparerPage == null) {
            response.setPreparerCandidatePage(Page.empty());
            return response;
        }
        List<PractitionerOptionDto> dtoRows = new ArrayList<>();
        for (Practitioner practitioner : preparerPage.getContent()) {
            if (practitioner == null) {
                continue;
            }
            PractitionerOptionDto row = new PractitionerOptionDto();
            row.setPractitionerId(practitioner.getId());
            row.setDisplayName(practitioner.getName());
            row.setOrgId(practitioner.getOrgId());
            dtoRows.add(row);
        }
        Page<PractitionerOptionDto> mapped =
            new PageImpl<>(dtoRows, preparerPage.getPageable(), preparerPage.getTotalElements());
        response.setPreparerCandidatePage(mapped);
        return response;
    }

    /**
     * IF-IPP-02：装配在院患者队列分页响应（1-based 页码，B.10.11）。
     *
     * @param encounterIdPage 就诊主键分页壳
     * @param infos           与队列顺序一致的住院信息读模型
     * @return 队列响应
     */
    @Override
    public PagePendingDispenseInpatientEncounterQueueResponse toInpatientQueuePage(
        Page<Long> encounterIdPage,
        List<InpatientEncounterContextQueryModel> infos) {
        List<PendingDispenseInpatientEncounterQueueRowDto> content = new ArrayList<>();
        if (encounterIdPage != null) {
            Map<Long, InpatientEncounterContextQueryModel> infoByEncounter = new HashMap<>();
            if (infos != null) {
                for (InpatientEncounterContextQueryModel info : infos) {
                    if (info == null || info.getEncounterId() == null) {
                        continue;
                    }
                    infoByEncounter.put(info.getEncounterId(), info);
                }
            }
            for (Long encounterId : encounterIdPage.getContent()) {
                if (encounterId == null) {
                    continue;
                }
                InpatientEncounterContextQueryModel info = infoByEncounter.get(encounterId);
                PendingDispenseInpatientEncounterQueueRowDto row = new PendingDispenseInpatientEncounterQueueRowDto();
                row.setEncounterId(encounterId);
                if (info != null) {
                    row.setEncounterBusNo(info.getEncounterBusNo());
                    row.setPatientId(info.getPatientId());
                    row.setPatientName(info.getPatientName());
                    row.setGenderEnum(info.getGenderEnum());
                    row.setAgeText(info.getAgeText());
                    row.setWardId(info.getWardId());
                    row.setWardName(info.getWardName());
                    row.setBedName(info.getBedName());
                    row.setOrganizationName(info.getOrganizationName());
                    row.setAttendingDoctorName(info.getAttendingDoctorName());
                    row.setStatusEnum(info.getStatusEnum());
                    String statusText = info.getStatusEnumText();
                    if (StringUtils.isBlank(statusText)) {
                        statusText = InpatientEncounterStatusDisplayUtil.resolveDisplayTextForReadModel(
                            info.getStatusEnum(), info.getTransferOrgId(), info.getDischargeTypeEnum());
                    }
                    row.setStatusEnumText(statusText);
                    row.setStartTime(info.getStartTime());
                }
                content.add(row);
            }
        }
        return PagedResultBuilder.build(new PagePendingDispenseInpatientEncounterQueueResponse(), content, encounterIdPage);
    }

    /**
     * IF-IPP-03：装配药品发药单分页响应（1-based 页码，B.10.11；不含候选库存）。
     *
     * @param slipPage                         发药单读模型分页
     * @param reqByMedReqId                    医嘱列表模型映射
     * @param productByMedicationId            药品定义读模型
     * @param tcmOnly                          契约占位，当前装配不做行过滤
     * @param westernOnly                      契约占位，当前装配不做行过滤
     * @param dispensingPractitionerNameByDispenseId 发药人姓名
     * @param encounterBusNo                   住院号
     * @param orderingDepartmentNameByOrgId    开立科室名称
     * @param orderingPractitionerNameById     开立医师姓名
     * @param dispenseLocationNameByLocationId 库房名称
     * @param traceNoEffectiveByDispenseId     追溯码有效值
     * @param rateCodeLabelMap                 频次术语
     * @param methodCodeLabelMap               途径术语
     * @param doseUnitLabelMap                 剂量单位术语
     * @param businessUnitLabelMap             发放单位术语
     * @param rxFlagLabelMap                   处方类别术语
     * @param partAttributeLabelMap            拆分属性术语
     * @return 分页响应
     */
    @Override
    @SuppressWarnings("unused")
    public PageEncounterMedicationDispenseSlipsResponse toSlipPage(
        Page<MedicationDispenseSlipQueryModel> slipPage,
        Map<Long, MedicationRequestListQueryModel> reqByMedReqId,
        Map<Long, ProductDefinitionDisplayDto> productByMedicationId,
        Boolean tcmOnly,
        Boolean westernOnly,
        Map<Long, String> dispensingPractitionerNameByDispenseId,
        String encounterBusNo,
        Map<Long, String> orderingDepartmentNameByOrgId,
        Map<Long, String> orderingPractitionerNameById,
        Map<Long, String> dispenseLocationNameByLocationId,
        Map<Long, String> traceNoEffectiveByDispenseId,
        Map<Long, String> lotNumberEffectiveByDispenseId,
        Map<Long, BigDecimal> chargeUnitPriceByDispenseId,
        Map<Long, BigDecimal> chargeTotalPriceByDispenseId,
        Map<String, String> rateCodeLabelMap,
        Map<String, String> methodCodeLabelMap,
        Map<String, String> doseUnitLabelMap,
        Map<String, String> businessUnitLabelMap,
        Map<String, String> rxFlagLabelMap,
        Map<String, String> partAttributeLabelMap) {
        List<EncounterMedicationDispenseSlipRowDto> content = new ArrayList<>();
        if (slipPage == null) {
            return PagedResultBuilder.build(new PageEncounterMedicationDispenseSlipsResponse(), content, null);
        }
        Map<Long, MedicationRequestListQueryModel> reqMap = Objects.requireNonNullElse(reqByMedReqId, Map.of());
        Map<Long, ProductDefinitionDisplayDto> prodMap = Objects.requireNonNullElse(productByMedicationId, Map.of());
        Map<Long, String> dispenserNames =
            Objects.requireNonNullElse(dispensingPractitionerNameByDispenseId, Map.of());
        Map<Long, String> deptNames = Objects.requireNonNullElse(orderingDepartmentNameByOrgId, Map.of());
        Map<Long, String> orderingDrNames = Objects.requireNonNullElse(orderingPractitionerNameById, Map.of());
        Map<Long, String> locNames = Objects.requireNonNullElse(dispenseLocationNameByLocationId, Map.of());
        Map<Long, String> traceMap = Objects.requireNonNullElse(traceNoEffectiveByDispenseId, Map.of());
        Map<Long, String> lotMap = Objects.requireNonNullElse(lotNumberEffectiveByDispenseId, Map.of());
        Map<Long, BigDecimal> unitPriceMap = Objects.requireNonNullElse(chargeUnitPriceByDispenseId, Map.of());
        Map<Long, BigDecimal> totalPriceMap = Objects.requireNonNullElse(chargeTotalPriceByDispenseId, Map.of());
        Map<String, String> rateLabels = Objects.requireNonNullElse(rateCodeLabelMap, Map.of());
        Map<String, String> methodLabels = Objects.requireNonNullElse(methodCodeLabelMap, Map.of());
        Map<String, String> doseLabels = Objects.requireNonNullElse(doseUnitLabelMap, Map.of());
        Map<String, String> bizUnitLabels = Objects.requireNonNullElse(businessUnitLabelMap, Map.of());
        Map<String, String> rxLabels = Objects.requireNonNullElse(rxFlagLabelMap, Map.of());
        Map<String, String> partLabels = Objects.requireNonNullElse(partAttributeLabelMap, Map.of());

        // 逐条搬运发药单行：读模型 + 医嘱 + 目录 + 术语预热映射（不含候选库存）。
        for (MedicationDispenseSlipQueryModel slip : slipPage.getContent()) {
            if (slip == null) {
                continue;
            }
            MedicationRequestListQueryModel req = reqMap.get(slip.getMedReqId());
            EncounterMedicationDispenseSlipRowDto row = new EncounterMedicationDispenseSlipRowDto();
            row.setEncounterId(slip.getEncounterId());
            row.setEncounterBusNo(encounterBusNo);
            row.setDispenseId(slip.getDispenseId());
            row.setMedReqId(slip.getMedReqId());
            row.setMedicationId(slip.getMedicationId());
            row.setDispenseLocationId(slip.getLocationId());
            if (slip.getLocationId() != null) {
                row.setDispenseLocationName(locNames.get(slip.getLocationId()));
            }
            row.setLotNumber(slip.getLotNumber());
            String lotEffective = lotMap.get(slip.getDispenseId());
            if (StringUtils.isNotBlank(lotEffective)) {
                row.setLotNumber(lotEffective);
            }
            String traceEffective = traceMap.get(slip.getDispenseId());
            if (StringUtils.isNotBlank(traceEffective)) {
                row.setTraceNo(traceEffective);
            } else {
                row.setTraceNo(slip.getTraceNo());
            }
            row.setDispensingPractitionerName(dispenserNames.get(slip.getDispenseId()));
            if (slip.getStatusEnum() != null) {
                row.setStatusEnum(slip.getStatusEnum().getCode());
                row.setStatusEnumText(slip.getStatusEnum().getDisplay());
            }
            MedicationDispenseType dispenseKind = slip.getDispenseEnum();
            if (dispenseKind != null) {
                row.setDispenseEnum(dispenseKind.getCode());
                row.setDispenseEnumText(dispenseKind.getDisplay());
            }
            row.setQuantity(slip.getQuantity());
            row.setUnitCode(slip.getUnitCode());
            if (slip.getDispenseId() != null) {
                row.setUnitPrice(unitPriceMap.get(slip.getDispenseId()));
                row.setTotalPrice(totalPriceMap.get(slip.getDispenseId()));
            }
            row.setBusNo(slip.getBusNo());
            row.setPrescriptionNo(slip.getPrescriptionNo());
            row.setOrderingPractitionerId(slip.getMedRequesterId());
            if (slip.getMedRequesterId() != null) {
                row.setOrderingPractitionerName(orderingDrNames.get(slip.getMedRequesterId()));
            }
            ProductDefinitionDisplayDto product = prodMap.get(slip.getMedicationId());
            if (product != null) {
                row.setMedicationName(product.getProductName());
                row.setMerchandiseName(product.getMerchandiseName());
                row.setSpecificationText(product.getTotalVolume());
                row.setManufacturerName(product.getManufacturer());
                row.setProductTableName(product.getProductTableName());
                row.setMedicationCategoryCode(product.getCategoryCode());
                row.setMedicationCategoryText(product.getCategoryCodeDictText());
                row.setSplitRatio(product.getDoseUnitRatio());
                if (StringUtils.isNotBlank(product.getPartAttributeEnum())) {
                    row.setPartAttributeCode(product.getPartAttributeEnum());
                    row.setPartAttributeText(
                        OrderCatalogDisplayAssembler.resolveCategoryDisplay(partLabels, product.getPartAttributeEnum()));
                }
            }
            if (req != null) {
                row.setOrderingDepartmentId(req.getPerformLocation());
                if (req.getPerformLocation() != null) {
                    row.setOrderingDepartmentName(deptNames.get(req.getPerformLocation()));
                }
                row.setRequestQuantity(req.getQuantity());
                row.setFrequencyCode(req.getRateCode());
                row.setRouteCode(req.getMethodCode());
                row.setDoseValue(req.getDose());
                row.setDoseUnitCode(req.getDoseUnitCode());
                row.setDispensePerSupplyDays(req.getDispensePerDuration());
                row.setPrescriptionGroupId(req.getGroupId());
                row.setRequestAuthoredTime(req.getReqAuthoredTime());
                row.setEncounterMedicationTypeCode(req.getRxTypeCode());
                row.setEncounterMedicationTypeText(
                    OrderCatalogDisplayAssembler.resolveCategoryDisplay(rxLabels, req.getRxTypeCode()));
                row.setTherapyEnum(req.getTherapyEnum());
                TherapyType therapyType = EnumUtil.fromCode(TherapyType.class, req.getTherapyEnum());
                if (therapyType != null) {
                    row.setTherapyEnumText(therapyType.getDisplay());
                }
                row.setFrequencyText(OrderCatalogDisplayAssembler.resolveCategoryDisplay(rateLabels, req.getRateCode()));
                row.setRouteText(OrderCatalogDisplayAssembler.resolveCategoryDisplay(methodLabels, req.getMethodCode()));
                String doseText = OrderCatalogDisplayAssembler.resolveCategoryDisplay(doseLabels, req.getDoseUnitCode());
                row.setDoseUnitText(doseText);
                row.setUnitText(OrderCatalogDisplayAssembler.resolveCategoryDisplay(bizUnitLabels, slip.getUnitCode()));
                row.setTcmFlag(req.getTcmFlag());
                if (Boolean.TRUE.equals(req.getTcmFlag())) {
                    row.setTcmFlagText(MedicationCommonConstants.TCM_CLASS_DISPLAY_TCM);
                } else if (req.getTcmFlag() != null) {
                    row.setTcmFlagText(MedicationCommonConstants.TCM_CLASS_DISPLAY_WESTERN_AND_PATENT);
                }
                row.setSkinTestFlag(req.getSkinTestFlag());
                if (Boolean.TRUE.equals(req.getSkinTestFlag())) {
                    row.setSkinTestFlagText(MedicationCommonConstants.WORKSTATION_BOOLEAN_DISPLAY_YES);
                } else if (req.getSkinTestFlag() != null) {
                    row.setSkinTestFlagText(MedicationCommonConstants.WORKSTATION_BOOLEAN_DISPLAY_NO);
                }
                row.setInfusionFlag(req.getInfusionFlag());
                if (Boolean.TRUE.equals(req.getInfusionFlag())) {
                    row.setInfusionFlagText(MedicationCommonConstants.WORKSTATION_BOOLEAN_DISPLAY_YES);
                } else if (req.getInfusionFlag() != null) {
                    row.setInfusionFlagText(MedicationCommonConstants.WORKSTATION_BOOLEAN_DISPLAY_NO);
                }
            }
            content.add(row);
        }
        return PagedResultBuilder.build(new PageEncounterMedicationDispenseSlipsResponse(), content, slipPage);
    }

    /**
     * IF-IPP-04：装配保存配药命令（库房/批号/数量取自聚合，非 Request）。
     *
     * @param operatorId            操作人用户 id
     * @param preparerPractitionerId 调配药师主键
     * @param lines                 提交行
     * @param dispenseById          发放单实体映射
     * @return 领域命令
     */
    @Override
    public PrepareMedicationDispenseCommand toPrepareMedicationDispenseCommand(
        Long operatorId,
        Long preparerPractitionerId,
        List<SavePrepareInpatientDispenseLineSubmitDto> lines,
        Map<Long, MedicationDispense> dispenseById) {
        PrepareMedicationDispenseCommand command = new PrepareMedicationDispenseCommand();
        command.setOperatorId(operatorId);
        List<PrepareMedicationDispenseCommand.Item> items = new ArrayList<>();
        Map<Long, MedicationDispense> dispenseMap = Objects.requireNonNullElse(dispenseById, Map.of());
        if (lines != null) {
            for (SavePrepareInpatientDispenseLineSubmitDto line : lines) {
                if (line == null || line.getDispenseId() == null) {
                    continue;
                }
                MedicationDispense dispense = dispenseMap.get(line.getDispenseId());
                PrepareMedicationDispenseCommand.Item item = new PrepareMedicationDispenseCommand.Item();
                item.setId(line.getDispenseId());
                item.setPreparerId(preparerPractitionerId);
                item.setTraceNo(line.getTraceNo());
                if (dispense != null) {
                    item.setLocationId(dispense.getLocationId());
                    item.setLotNumber(dispense.getLotNumber());
                    item.setQuantity(dispense.getQuantity());
                }
                items.add(item);
            }
        }
        command.setItems(items);
        return command;
    }

    /**
     * IF-IPP-04：装配保存配药响应。
     *
     * @param ids     已处理发放单 id
     * @param message 提示
     * @param success 是否成功
     * @return 响应
     */
    @Override
    public SavePrepareInpatientMedicationDispenseResponse toPrepareBatchResponse(List<Long> ids, String message, boolean success) {
        SavePrepareInpatientMedicationDispenseResponse response = new SavePrepareInpatientMedicationDispenseResponse();
        response.setProcessedDispenseIds(ids);
        response.setMessage(message);
        response.setSuccess(success);
        return response;
    }

    /**
     * IF-IPP-05：装配核发命令。
     *
     * @param operatorId     操作人用户 id
     * @param practitionerId 发药药师主键
     * @param dispensers     发放聚合行（顺序与提交一致）
     * @return 领域命令
     */
    @Override
    public DispenseMedicationCommand toDispenseCommand(Long operatorId, Long practitionerId, List<MedicationDispense> dispensers) {
        DispenseMedicationCommand command = new DispenseMedicationCommand();
        command.setOperatorId(operatorId);
        List<DispenseMedicationCommand.Item> items = new ArrayList<>();
        if (dispensers != null) {
            for (MedicationDispense dispense : dispensers) {
                if (dispense == null) {
                    continue;
                }
                DispenseMedicationCommand.Item item = new DispenseMedicationCommand.Item();
                item.setId(dispense.getId());
                item.setPractitionerId(practitionerId);
                item.setDispenseQuantity(dispense.getQuantity());
                items.add(item);
            }
        }
        command.setItems(items);
        return command;
    }

    /**
     * IF-IPP-05：装配核发响应。
     *
     * @param ids     已处理 id
     * @param message 提示
     * @param success 是否成功
     * @return 响应
     */
    @Override
    public DispenseInpatientMedicationsResponse toDispenseBatchResponse(List<Long> ids, String message, boolean success) {
        DispenseInpatientMedicationsResponse response = new DispenseInpatientMedicationsResponse();
        response.setProcessedDispenseIds(ids);
        response.setMessage(message);
        response.setSuccess(success);
        return response;
    }

    /**
     * IF-IPP-06：装配作废命令。
     *
     * @param operatorId 操作人用户 id
     * @param lines      提交行
     * @return 领域命令
     */
    @Override
    public CancelMedicationDispenseCommand toCancelMedicationDispenseCommand(
        Long operatorId,
        List<CancelMedicationDispenseLineSubmitDto> lines) {
        CancelMedicationDispenseCommand command = new CancelMedicationDispenseCommand();
        command.setOperatorId(operatorId);
        List<CancelMedicationDispenseCommand.Item> items = new ArrayList<>();
        if (lines != null) {
            for (CancelMedicationDispenseLineSubmitDto line : lines) {
                if (line == null) {
                    continue;
                }
                CancelMedicationDispenseCommand.Item item = new CancelMedicationDispenseCommand.Item();
                item.setId(line.getDispenseId());
                item.setNotPerformedReasonEnum(line.getNotPerformedReasonEnum());
                items.add(item);
            }
        }
        command.setItems(items);
        return command;
    }

    /**
     * IF-IPP-06：装配作废响应。
     *
     * @param ids     已处理 id
     * @param message 提示
     * @param success 是否成功
     * @return 响应
     */
    @Override
    public CancelInpatientMedicationDispenseResponse toCancelBatchResponse(List<Long> ids, String message, boolean success) {
        CancelInpatientMedicationDispenseResponse response = new CancelInpatientMedicationDispenseResponse();
        response.setProcessedDispenseIds(ids);
        response.setMessage(message);
        response.setSuccess(success);
        return response;
    }
}
