/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.pharmacy.dispense.mapper;

import com.openhis.health.application.shared.option.OptionDtoAssembler;
import com.openhis.health.application.shared.pagination.PagedResultBuilder;
import com.openhis.health.application.outpatient.pharmacy.dispense.dto.CancelMedicationDispenseLineSubmitDto;
import com.openhis.health.application.outpatient.pharmacy.dispense.dto.EncounterMedicationDispenseSlipRowDto;
import com.openhis.health.application.outpatient.pharmacy.dispense.dto.MedicationDispenseInventoryDetailDto;
import com.openhis.health.application.outpatient.pharmacy.dispense.dto.PendingDispenseEncounterQueueRowDto;
import com.openhis.health.application.outpatient.pharmacy.dispense.dto.PractitionerOptionDto;
import com.openhis.health.application.outpatient.pharmacy.dispense.dto.SavePrepareDispenseLineSubmitDto;
import com.openhis.health.application.outpatient.pharmacy.dispense.response.CancelOutpatientMedicationDispenseResponse;
import com.openhis.health.application.outpatient.pharmacy.dispense.response.DispenseOutpatientMedicationsResponse;
import com.openhis.health.application.outpatient.pharmacy.dispense.response.LoadPharmacyDispensePageInitResponse;
import com.openhis.health.application.outpatient.pharmacy.dispense.response.PageEncounterMedicationDispenseSlipsResponse;
import com.openhis.health.application.outpatient.pharmacy.dispense.response.PagePendingDispenseEncounterQueueResponse;
import com.openhis.health.application.outpatient.pharmacy.dispense.response.SavePrepareOutpatientMedicationDispenseResponse;
import com.openhis.health.domain.administration.location.entity.Location;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.medication.medicationdispense.command.CancelMedicationDispenseCommand;
import com.openhis.health.domain.medication.medicationdispense.command.DispenseMedicationCommand;
import com.openhis.health.domain.medication.medicationdispense.command.PrepareMedicationDispenseCommand;
import com.openhis.health.domain.medication.medicationdispense.entity.MedicationDispense;
import com.openhis.health.domain.medication.medicationrequest.queries.model.MedicationRequestListQueryModel;
import com.openhis.health.domain.administration.encounter.queries.model.OutpatientEncounterContextQueryModel;
import com.openhis.health.domain.medication.medicationdispense.queries.model.MedicationDispenseSlipQueryModel;
import com.openhis.health.application.outpatient.pharmacy.dispense.support.EncounterMedicationDispenseSlipReadAssembler;
import com.openhis.health.application.outpatient.pharmacy.dispense.dto.MedicationDispenseSlipPageResult;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.openhis.whale.core.application.dto.OptionDto;
import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;

/**
 * {@link OutpatientPharmacyDispenseMapper} 的实现：门诊发药工作站 IF-OPP-01～06 的请求/读模型/领域命令/响应字段搬运。
 *
 * <p>职责边界：无业务流程分支与不变量；null 安全回落为空集合或空分页壳（B.1 / A.4）。</p>
 */
@Component
@RequiredArgsConstructor
public class OutpatientPharmacyDispenseMapperImpl implements OutpatientPharmacyDispenseMapper {

    private final EncounterMedicationDispenseSlipReadAssembler encounterMedicationDispenseSlipReadAssembler;

    /**
     * IF-OPP-01：装配初始化响应。
     *
     * @param pharmacies                  可操作药房位置列表（adm_location）
     * @param dispenseStatusOptions       发放状态下拉
     * @param encounterClassOptions       就诊类别下拉
     * @param notPerformedReasonOptions   未发药原因下拉（可为空）
     * @param preparerPage                药师分页实体页
     * @return 初始化响应
     */
    @Override
    public LoadPharmacyDispensePageInitResponse toLoadInitResponse(
        List<Location> pharmacies,
        List<OptionDto> dispenseStatusOptions,
        List<OptionDto> encounterClassOptions,
        List<OptionDto> notPerformedReasonOptions,
        Page<Practitioner> preparerPage) {
        LoadPharmacyDispensePageInitResponse response = new LoadPharmacyDispensePageInitResponse();
        if (pharmacies == null) {
            response.setPharmacyOrganizationOptions(new ArrayList<>());
        } else {
            response.setPharmacyOrganizationOptions(OptionDtoAssembler.toOptions(pharmacies, Location::getId, Location::getName, 0L));
        }
        if (dispenseStatusOptions == null) {
            response.setDispenseStatusFilterOptions(new ArrayList<>());
        } else {
            response.setDispenseStatusFilterOptions(dispenseStatusOptions);
        }
        if (encounterClassOptions == null) {
            response.setEncounterClassOptions(new ArrayList<>());
        } else {
            response.setEncounterClassOptions(encounterClassOptions);
        }
        if (notPerformedReasonOptions == null) {
            response.setNotPerformedReasonOptions(new ArrayList<>());
        } else {
            response.setNotPerformedReasonOptions(notPerformedReasonOptions);
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
            row.setOrgName(null);
            dtoRows.add(row);
        }
        Page<PractitionerOptionDto> mapped = new PageImpl<>(dtoRows, preparerPage.getPageable(), preparerPage.getTotalElements());
        response.setPreparerCandidatePage(mapped);
        return response;
    }

    /**
     * IF-OPP-02：装配就诊队列分页响应（1-based 页码，B.10.11）。
     *
     * <p>分页字段遵循 PagedResult 规范（B.10.11）：content 承载列表数据，totalCount 承载总条数，
     * pageNo/pageSize 为 1-based 自然数。</p>
     *
     * @param encounterIdPage               就诊主键分页壳
     * @param infos                         门诊信息读模型（顺序与队列一致）
     * @param dispensePendingSummaryByEncounterId 待发摘要（可空）
     * @return 队列响应
     */
    @Override
    public PagePendingDispenseEncounterQueueResponse toQueuePage(
        Page<Long> encounterIdPage,
        List<OutpatientEncounterContextQueryModel> infos,
        Map<Long, String> dispensePendingSummaryByEncounterId) {
        List<PendingDispenseEncounterQueueRowDto> content = new ArrayList<>();

        if (encounterIdPage != null) {
            Map<Long, OutpatientEncounterContextQueryModel> infoByEncounter = new HashMap<>();
            if (infos != null) {
                for (OutpatientEncounterContextQueryModel info : infos) {
                    if (info == null || info.getEncounterId() == null) {
                        continue;
                    }
                    infoByEncounter.put(info.getEncounterId(), info);
                }
            }
            Map<Long, String> summaryMap = dispensePendingSummaryByEncounterId;
            if (summaryMap == null) {
                summaryMap = Map.of();
            }
            for (Long encounterId : encounterIdPage.getContent()) {
                if (encounterId == null) {
                    continue;
                }
                OutpatientEncounterContextQueryModel info = infoByEncounter.get(encounterId);
                PendingDispenseEncounterQueueRowDto row = new PendingDispenseEncounterQueueRowDto();
                row.setEncounterId(encounterId);
                row.setDispensePendingSummaryText(summaryMap.get(encounterId));
                if (info != null) {
                    row.setEncounterBusNo(info.getEncounterBusNo());
                    row.setPatientId(info.getPatientId());
                    row.setPatientName(info.getPatientName());
                    row.setGenderEnum(info.getGenderEnum());
                    row.setAgeText(info.getAgeText());
                    row.setOrganizationName(info.getOrganizationName());
                    row.setDoctorName(info.getDoctorName());
                    row.setStatusEnum(info.getStatusEnum());
                    row.setStatusEnumText(info.getStatusEnumText());
                    row.setStartTime(info.getStartTime());
                }
                content.add(row);
            }
        }

        return PagedResultBuilder.build(new PagePendingDispenseEncounterQueueResponse(), content, encounterIdPage);
    }

    /**
     * IF-OPP-03：装配药品发药单分页响应（1-based 页码，B.10.11）。
     *
     * <p>分页字段遵循 PagedResult 规范（B.10.11）：content 承载列表数据，totalCount 承载总条数，
     * pageNo/pageSize 为 1-based 自然数。</p>
     *
     * @param slipPage                         发药单读模型分页
     * @param reqByMedReqId                    医嘱列表模型映射
     * @param productByMedicationId            药品定义读模型（按 medicationId）
     * @param inventoryDetailListByDispenseId  候选库存明细（按发放单）
     * @param tcmOnly                          仅中药饮片
     * @param westernOnly                      仅西药中成药
     * @param dispensingPractitionerNameByDispenseId 发药人姓名（按发放单）
     * @param encounterBusNo                   就诊业务号
     * @param orderingDepartmentNameByOrgId    开立科室名称
     * @param orderingPractitionerNameById      开立医师姓名
     * @param dispenseLocationNameByLocationId 发放库房名称
     * @param chargeUnitPriceByDispenseId       收费单价（按发放单）
     * @param chargeTotalPriceByDispenseId      收费金额（按发放单）
     * @param traceNoEffectiveByDispenseId      追溯码有效值
     * @param rateCodeLabelMap                  频次术语（{@link TerminologyOptionLoader#buildLabelMap} 预热）
     * @param methodCodeLabelMap                途径术语（同上）
     * @param doseUnitLabelMap                  剂量单位术语（同上）
     * @param businessUnitLabelMap              业务/库存单位术语（UNIT_CODE，可与库存明细共用同一张映射）
     * @param rxFlagLabelMap                    处方类别术语（同上）
     * @param partAttributeLabelMap             拆分属性术语（同上）；行内解析用 {@link OrderCatalogDisplayAssembler#resolveCategoryDisplay}
     * @return 分页响应
     */
    @Override
    @SuppressWarnings("unused")
    public PageEncounterMedicationDispenseSlipsResponse toSlipPage(
        Page<MedicationDispenseSlipQueryModel> slipPage,
        Map<Long, MedicationRequestListQueryModel> reqByMedReqId,
        Map<Long, ProductDefinitionDisplayDto> productByMedicationId,
        Map<Long, List<MedicationDispenseInventoryDetailDto>> inventoryDetailListByDispenseId,
        Boolean tcmOnly,
        Boolean westernOnly,
        Map<Long, String> dispensingPractitionerNameByDispenseId,
        String encounterBusNo,
        Map<Long, String> orderingDepartmentNameByOrgId,
        Map<Long, String> orderingPractitionerNameById,
        Map<Long, String> dispenseLocationNameByLocationId,
        Map<Long, BigDecimal> chargeUnitPriceByDispenseId,
        Map<Long, BigDecimal> chargeTotalPriceByDispenseId,
        Map<Long, String> traceNoEffectiveByDispenseId,
        Map<String, String> rateCodeLabelMap,
        Map<String, String> methodCodeLabelMap,
        Map<String, String> tcmMethodCodeLabelMap,
        Map<String, String> doseUnitLabelMap,
        Map<String, String> businessUnitLabelMap,
        Map<String, String> rxFlagLabelMap,
        Map<String, String> partAttributeLabelMap) {
        MedicationDispenseSlipPageResult result = encounterMedicationDispenseSlipReadAssembler.assembleOutpatientSlipPage(
            slipPage,
            reqByMedReqId,
            productByMedicationId,
            toQuerySupportInventoryMap(inventoryDetailListByDispenseId),
            tcmOnly,
            westernOnly,
            dispensingPractitionerNameByDispenseId,
            encounterBusNo,
            orderingDepartmentNameByOrgId,
            orderingPractitionerNameById,
            dispenseLocationNameByLocationId,
            chargeUnitPriceByDispenseId,
            chargeTotalPriceByDispenseId,
            traceNoEffectiveByDispenseId,
            rateCodeLabelMap,
            methodCodeLabelMap,
            tcmMethodCodeLabelMap,
            doseUnitLabelMap,
            businessUnitLabelMap,
            rxFlagLabelMap,
            partAttributeLabelMap,
            Map.of());
        return toSlipPageResponse(result);
    }

    @Override
    public PageEncounterMedicationDispenseSlipsResponse toSlipPageResponse(MedicationDispenseSlipPageResult result) {
        PageEncounterMedicationDispenseSlipsResponse response = new PageEncounterMedicationDispenseSlipsResponse();
        if (result == null) {
            return PagedResultBuilder.buildEmpty(response);
        }
        List<EncounterMedicationDispenseSlipRowDto> rows = new ArrayList<>();
        if (result.getContent() != null) {
            for (com.openhis.health.application.outpatient.pharmacy.dispense.dto.EncounterMedicationDispenseSlipRowDto source
                : result.getContent()) {
                if (source == null) {
                    continue;
                }
                EncounterMedicationDispenseSlipRowDto target = new EncounterMedicationDispenseSlipRowDto();
                BeanUtils.copyProperties(source, target);
                if (source.getInventoryDetailList() != null) {
                    List<MedicationDispenseInventoryDetailDto> inventoryRows = new ArrayList<>();
                    for (com.openhis.health.application.outpatient.pharmacy.dispense.dto.MedicationDispenseInventoryDetailDto inv
                        : source.getInventoryDetailList()) {
                        if (inv == null) {
                            continue;
                        }
                        MedicationDispenseInventoryDetailDto invTarget = new MedicationDispenseInventoryDetailDto();
                        BeanUtils.copyProperties(inv, invTarget);
                        inventoryRows.add(invTarget);
                    }
                    target.setInventoryDetailList(inventoryRows);
                }
                rows.add(target);
            }
        }
        return PagedResultBuilder.build(
            response, rows, result.getTotalCount(), result.getPageNo(), result.getPageSize());
    }

    private static Map<Long, List<com.openhis.health.application.outpatient.pharmacy.dispense.dto.MedicationDispenseInventoryDetailDto>>
    toQuerySupportInventoryMap(Map<Long, List<MedicationDispenseInventoryDetailDto>> inventoryDetailListByDispenseId) {
        if (inventoryDetailListByDispenseId == null) {
            return Map.of();
        }
        Map<Long, List<com.openhis.health.application.outpatient.pharmacy.dispense.dto.MedicationDispenseInventoryDetailDto>> mapped =
            new HashMap<>();
        for (Map.Entry<Long, List<MedicationDispenseInventoryDetailDto>> entry : inventoryDetailListByDispenseId.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            List<com.openhis.health.application.outpatient.pharmacy.dispense.dto.MedicationDispenseInventoryDetailDto> converted =
                new ArrayList<>();
            for (MedicationDispenseInventoryDetailDto source : entry.getValue()) {
                if (source == null) {
                    continue;
                }
                com.openhis.health.application.outpatient.pharmacy.dispense.dto.MedicationDispenseInventoryDetailDto target =
                    new com.openhis.health.application.outpatient.pharmacy.dispense.dto.MedicationDispenseInventoryDetailDto();
                BeanUtils.copyProperties(source, target);
                converted.add(target);
            }
            mapped.put(entry.getKey(), converted);
        }
        return mapped;
    }

    /**
     * IF-OPP-04：装配保存配药命令。
     *
     * @param operatorId   操作人用户 id
     * @param preparerId   调配药师主键
     * @param lines        提交行
     * @return 领域命令
     */
    @Override
    public PrepareMedicationDispenseCommand toPrepareMedicationDispenseCommand(
        Long operatorId,
        Long preparerId,
        List<SavePrepareDispenseLineSubmitDto> lines) {
        PrepareMedicationDispenseCommand command = new PrepareMedicationDispenseCommand();
        command.setOperatorId(operatorId);
        List<PrepareMedicationDispenseCommand.Item> items = new ArrayList<>();
        if (lines != null) {
            for (SavePrepareDispenseLineSubmitDto line : lines) {
                if (line == null) {
                    continue;
                }
                PrepareMedicationDispenseCommand.Item item = new PrepareMedicationDispenseCommand.Item();
                item.setId(line.getDispenseId());
                item.setPreparerId(preparerId);
                item.setLocationId(line.getDispenseLocationId());
                item.setLotNumber(line.getLotNumber());
                item.setTraceNo(line.getTraceNo());
                item.setQuantity(line.getQuantity());
                items.add(item);
            }
        }
        command.setItems(items);
        return command;
    }

    /**
     * IF-OPP-04：装配保存配药响应。
     *
     * @param ids     已处理发放单 id
     * @param message 提示
     * @param success 是否成功
     * @return 响应
     */
    @Override
    public SavePrepareOutpatientMedicationDispenseResponse toPrepareBatchResponse(List<Long> ids, String message, boolean success) {
        SavePrepareOutpatientMedicationDispenseResponse response = new SavePrepareOutpatientMedicationDispenseResponse();
        response.setProcessedDispenseIds(ids);
        response.setMessage(message);
        response.setSuccess(success);
        return response;
    }

    /**
     * IF-OPP-05：装配核发命令。
     *
     * @param operatorId      操作人用户 id
     * @param practitionerId  发药药师主键
     * @param dispensers      发放聚合行（顺序与提交一致）
     * @return 领域命令
     */
    @Override
    public DispenseMedicationCommand toDispenseMedicationCommand(Long operatorId, Long practitionerId, List<MedicationDispense> dispensers) {
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
     * IF-OPP-05：装配核发响应。
     *
     * @param ids     已处理 id
     * @param message 提示
     * @param success 是否成功
     * @return 响应
     */
    @Override
    public DispenseOutpatientMedicationsResponse toDispenseBatchResponse(List<Long> ids, String message, boolean success) {
        DispenseOutpatientMedicationsResponse response = new DispenseOutpatientMedicationsResponse();
        response.setProcessedDispenseIds(ids);
        response.setMessage(message);
        response.setSuccess(success);
        return response;
    }

    /**
     * IF-OPP-06：装配作废命令。
     *
     * @param operatorId 操作人用户 id
     * @param lines      提交行
     * @return 领域命令
     */
    @Override
    public CancelMedicationDispenseCommand toCancelMedicationDispenseCommand(Long operatorId, List<CancelMedicationDispenseLineSubmitDto> lines) {
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
     * IF-OPP-06：装配作废响应。
     *
     * @param ids     已处理 id
     * @param message 提示
     * @param success 是否成功
     * @return 响应
     */
    @Override
    public CancelOutpatientMedicationDispenseResponse toCancelBatchResponse(List<Long> ids, String message, boolean success) {
        CancelOutpatientMedicationDispenseResponse response = new CancelOutpatientMedicationDispenseResponse();
        response.setProcessedDispenseIds(ids);
        response.setMessage(message);
        response.setSuccess(success);
        return response;
    }
}
