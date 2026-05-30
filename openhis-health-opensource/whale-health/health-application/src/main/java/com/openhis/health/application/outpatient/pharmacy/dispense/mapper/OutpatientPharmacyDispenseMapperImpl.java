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

package com.openhis.health.application.outpatient.pharmacy.dispense.mapper;

import com.openhis.health.application.common.assembler.option.OptionDtoAssembler;
import com.openhis.health.application.common.assembler.order.OrderCatalogDisplayAssembler;
import com.openhis.health.application.common.pagination.PagedResultBuilder;
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
import com.openhis.health.domain.administration.organization.entity.Organization;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.medication.medicationdispense.command.CancelMedicationDispenseCommand;
import com.openhis.health.domain.medication.medicationdispense.command.DispenseMedicationCommand;
import com.openhis.health.domain.medication.medicationdispense.command.PrepareMedicationDispenseCommand;
import com.openhis.health.domain.medication.medicationdispense.entity.MedicationDispense;
import com.openhis.health.domain.medication.medicationrequest.queries.model.MedicationRequestListQueryModel;
import com.openhis.health.domain.administration.encounter.queries.model.OutpatientEncounterContextQueryModel;
import com.openhis.health.domain.medication.medicationdispense.queries.model.MedicationDispenseSlipQueryModel;
import com.openhis.health.application.querysupport.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.domain.share.constants.MedicationCommonConstants;
import com.openhis.health.domain.share.enums.MedicationDispenseType;
import com.openhis.health.domain.share.enums.TherapyType;
import com.openhis.health.domain.share.utils.EnumUtil;
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

/**
 * {@link OutpatientPharmacyDispenseMapper} 的实现：门诊发药工作站 IF-OPP-01～06 的请求/读模型/领域命令/响应字段搬运。
 *
 * <p>职责边界：无业务流程分支与不变量；null 安全回落为空集合或空分页壳（B.1 / A.4）。</p>
 */
@Component
public class OutpatientPharmacyDispenseMapperImpl implements OutpatientPharmacyDispenseMapper {

    /**
     * IF-OPP-01：装配初始化响应。
     *
     * @param pharmacies                  药房机构树
     * @param dispenseStatusOptions       发放状态下拉
     * @param encounterClassOptions       就诊类别下拉
     * @param notPerformedReasonOptions   未发药原因下拉（可为空）
     * @param preparerPage                药师分页实体页
     * @return 初始化响应
     */
    @Override
    public LoadPharmacyDispensePageInitResponse toLoadInitResponse(
        List<Organization> pharmacies,
        List<OptionDto> dispenseStatusOptions,
        List<OptionDto> encounterClassOptions,
        List<OptionDto> notPerformedReasonOptions,
        Page<Practitioner> preparerPage) {
        LoadPharmacyDispensePageInitResponse response = new LoadPharmacyDispensePageInitResponse();
        if (pharmacies == null) {
            response.setPharmacyOrganizationOptions(new ArrayList<>());
        } else {
            response.setPharmacyOrganizationOptions(OptionDtoAssembler.toOptions(pharmacies, Organization::getId, Organization::getName, 0L));
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
     * @param tcmOnly                          契约占位，忽略
     * @param westernOnly                      契约占位，忽略
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
        Map<String, String> doseUnitLabelMap,
        Map<String, String> businessUnitLabelMap,
        Map<String, String> rxFlagLabelMap,
        Map<String, String> partAttributeLabelMap) {
        List<EncounterMedicationDispenseSlipRowDto> content = new ArrayList<>();

        // 空分页防御：元数据由 PagedResultBuilder 统一写入（B.10.11）。
        if (slipPage == null) {
            return PagedResultBuilder.build(new PageEncounterMedicationDispenseSlipsResponse(), content, null);
        }

        // 步骤1：防御性初始化所有外部传入映射表，null 时置为空 Map 避免后续 NPE。
        Map<Long, MedicationRequestListQueryModel> reqMap = reqByMedReqId;
        if (reqMap == null) {
            reqMap = Map.of();
        }
        Map<Long, ProductDefinitionDisplayDto> prodMap = productByMedicationId;
        if (prodMap == null) {
            prodMap = Map.of();
        }
        Map<Long, List<MedicationDispenseInventoryDetailDto>> invMap = inventoryDetailListByDispenseId;
        if (invMap == null) {
            invMap = Map.of();
        }
        Map<Long, String> dispenserNames = dispensingPractitionerNameByDispenseId;
        if (dispenserNames == null) {
            dispenserNames = Map.of();
        }
        Map<Long, String> deptNames = orderingDepartmentNameByOrgId;
        if (deptNames == null) {
            deptNames = Map.of();
        }
        Map<Long, String> orderingDrNames = orderingPractitionerNameById;
        if (orderingDrNames == null) {
            orderingDrNames = Map.of();
        }
        Map<Long, String> locNames = dispenseLocationNameByLocationId;
        if (locNames == null) {
            locNames = Map.of();
        }
        Map<Long, BigDecimal> unitPriceMap = chargeUnitPriceByDispenseId;
        if (unitPriceMap == null) {
            unitPriceMap = Map.of();
        }
        Map<Long, BigDecimal> totalPriceMap = chargeTotalPriceByDispenseId;
        if (totalPriceMap == null) {
            totalPriceMap = Map.of();
        }
        Map<Long, String> traceMap = traceNoEffectiveByDispenseId;
        if (traceMap == null) {
            traceMap = Map.of();
        }
        Map<String, String> rateLabels = rateCodeLabelMap;
        if (rateLabels == null) {
            rateLabels = Map.of();
        }
        Map<String, String> methodLabels = methodCodeLabelMap;
        if (methodLabels == null) {
            methodLabels = Map.of();
        }
        Map<String, String> doseLabels = doseUnitLabelMap;
        if (doseLabels == null) {
            doseLabels = Map.of();
        }
        Map<String, String> bizUnitLabels = businessUnitLabelMap;
        if (bizUnitLabels == null) {
            bizUnitLabels = Map.of();
        }
        Map<String, String> rxLabels = rxFlagLabelMap;
        if (rxLabels == null) {
            rxLabels = Map.of();
        }
        Map<String, String> partLabels = partAttributeLabelMap;
        if (partLabels == null) {
            partLabels = Map.of();
        }
        // 步骤2：逐条搬运发药单行。读模型 slip + 医嘱 req + 目录 product + 术语预热映射 → 工作站列表行。
        // 无领域不变量分支，仅字段映射与默认值回落。
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
            row.setBusNo(slip.getBusNo());
            row.setPrescriptionNo(slip.getPrescriptionNo());
            row.setOrderingPractitionerId(slip.getMedRequesterId());
            if (slip.getMedRequesterId() != null) {
                row.setOrderingPractitionerName(orderingDrNames.get(slip.getMedRequesterId()));
            }
            row.setInventoryDetailList(invMap.get(slip.getDispenseId()));
            if (slip.getDispenseId() != null) {
                row.setUnitPrice(unitPriceMap.get(slip.getDispenseId()));
                row.setTotalPrice(totalPriceMap.get(slip.getDispenseId()));
            }

            ProductDefinitionDisplayDto product = prodMap.get(slip.getMedicationId());
            if (product != null) {
                row.setMedicationName(product.getProductName());
                row.setMerchandiseName(product.getMerchandiseName());
                row.setSpecificationText(product.getTotalVolume());
                // 生产厂家：读模型已投影为展示文本，禁止当作组织/供应商主键再查库
                row.setManufacturerName(product.getManufacturer());
                row.setProductTableName(product.getProductTableName());
                row.setMedicationCategoryCode(product.getCategoryCode());
                row.setMedicationCategoryText(product.getCategoryCodeDictText());
                row.setSplitRatio(product.getDoseUnitRatio());
                if (StringUtils.isNotBlank(product.getPartAttributeEnum())) {
                    row.setPartAttributeCode(product.getPartAttributeEnum());
                    row.setPartAttributeText(OrderCatalogDisplayAssembler.resolveCategoryDisplay(partLabels, product.getPartAttributeEnum()));
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
                row.setEncounterMedicationTypeText(OrderCatalogDisplayAssembler.resolveCategoryDisplay(rxLabels, req.getRxTypeCode()));
                row.setTherapyEnum(req.getTherapyEnum());
                TherapyType therapyType = EnumUtil.fromCode(TherapyType.class, req.getTherapyEnum());
                if (therapyType != null) {
                    row.setTherapyEnumText(therapyType.getDisplay());
                }
                row.setFrequencyText(OrderCatalogDisplayAssembler.resolveCategoryDisplay(rateLabels, req.getRateCode()));
                row.setRouteText(OrderCatalogDisplayAssembler.resolveCategoryDisplay(methodLabels, req.getMethodCode()));
                String doseUnitCodeForLabel = req.getDoseUnitCode();
                String doseText = OrderCatalogDisplayAssembler.resolveCategoryDisplay(doseLabels, doseUnitCodeForLabel);
                if (StringUtils.isBlank(doseText) && product != null) {
                    if (StringUtils.equals(doseUnitCodeForLabel, product.getDoseUnitCode())) {
                        doseText = product.getDoseUnitCodeDictText();
                    }
                }
                row.setDoseUnitText(doseText);
                String slipUnit = slip.getUnitCode();
                String unitTextResolved = OrderCatalogDisplayAssembler.resolveCategoryDisplay(bizUnitLabels, slipUnit);
                if (StringUtils.isBlank(unitTextResolved) && product != null) {
                    if (StringUtils.equals(slipUnit, product.getPurchaseUnitCode())) {
                        unitTextResolved = product.getPurchaseUnitCodeDictText();
                    }
                    if (StringUtils.isBlank(unitTextResolved) && StringUtils.equals(slipUnit, product.getStockUnitCode())) {
                        unitTextResolved = product.getStockUnitCodeDictText();
                    }
                    if (StringUtils.isBlank(unitTextResolved) && StringUtils.equals(slipUnit, product.getSplitUnitCode())) {
                        unitTextResolved = product.getSplitUnitCodeDictText();
                    }
                    if (StringUtils.isBlank(unitTextResolved) && StringUtils.equals(slipUnit, product.getPackageUnitCode())) {
                        unitTextResolved = product.getPackageUnitCodeDictText();
                    }
                    if (StringUtils.isBlank(unitTextResolved) && StringUtils.equals(slipUnit, product.getDefEncounterUnitCode())) {
                        unitTextResolved = product.getDefEncounterUnitCodeDictText();
                    }
                }
                row.setUnitText(unitTextResolved);
                row.setTcmFlag(req.getTcmFlag());
                if (Boolean.TRUE.equals(req.getTcmFlag())) {
                    row.setTcmFlagText(MedicationCommonConstants.TCM_CLASS_DISPLAY_TCM);
                } else {
                    if (req.getTcmFlag() == null) {
                        row.setTcmFlagText(null);
                    } else {
                        row.setTcmFlagText(MedicationCommonConstants.TCM_CLASS_DISPLAY_WESTERN_AND_PATENT);
                    }
                }
                row.setSkinTestFlag(req.getSkinTestFlag());
                if (Boolean.TRUE.equals(req.getSkinTestFlag())) {
                    row.setSkinTestFlagText(MedicationCommonConstants.WORKSTATION_BOOLEAN_DISPLAY_YES);
                } else {
                    if (req.getSkinTestFlag() == null) {
                        row.setSkinTestFlagText(null);
                    } else {
                        row.setSkinTestFlagText(MedicationCommonConstants.WORKSTATION_BOOLEAN_DISPLAY_NO);
                    }
                }
                row.setInfusionFlag(req.getInfusionFlag());
                if (Boolean.TRUE.equals(req.getInfusionFlag())) {
                    row.setInfusionFlagText(MedicationCommonConstants.WORKSTATION_BOOLEAN_DISPLAY_YES);
                } else {
                    if (req.getInfusionFlag() == null) {
                        row.setInfusionFlagText(null);
                    } else {
                        row.setInfusionFlagText(MedicationCommonConstants.WORKSTATION_BOOLEAN_DISPLAY_NO);
                    }
                }
            } else {
                if (product != null) {
                    row.setTcmFlag(Boolean.FALSE);
                    row.setTcmFlagText(MedicationCommonConstants.TCM_CLASS_DISPLAY_WESTERN_AND_PATENT);
                }
            }
            content.add(row);
        }

        return PagedResultBuilder.build(new PageEncounterMedicationDispenseSlipsResponse(), content, slipPage);
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
