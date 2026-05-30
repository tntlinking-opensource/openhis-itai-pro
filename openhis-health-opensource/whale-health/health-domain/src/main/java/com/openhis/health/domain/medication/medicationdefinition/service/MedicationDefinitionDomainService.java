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

package com.openhis.health.domain.medication.medicationdefinition.service;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.health.domain.share.constants.BusinessSequenceConstants;
import com.openhis.health.domain.medication.medicationdefinition.command.*;
import com.openhis.health.domain.medication.medicationdefinition.entity.MedicationDefinition;
import com.openhis.health.domain.medication.medicationdefinition.repository.MedicationDefinitionRepository;
import com.openhis.health.domain.medication.medicationdispense.repository.MedicationDispenseRepository;
import com.openhis.health.domain.medication.medicationrequest.repository.MedicationRequestRepository;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.events.medication.medicationdefinition.*;
import com.openhis.health.domain.share.utils.SearchCodeUtil;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.domain.event.IDomainEventPublisher;
import com.openhis.whale.core.infrastructure.sequence.BusinessSequenceService;
import org.apache.commons.lang3.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 药品定义写侧领域服务。
 */
@Service
@RequiredArgsConstructor
public class MedicationDefinitionDomainService {

    private final MedicationDefinitionRepository medicationDefinitionRepository;
    private final MedicationRequestRepository medicationRequestRepository;
    private final MedicationDispenseRepository medicationDispenseRepository;
    private final IDomainEventPublisher domainEventPublisher;
    /** 药品定义 busNo：主数据全局递增，前缀 {@link AggregateTypeConstants#MEDICATION_DEFINITION}。 */
    private final BusinessSequenceService businessSequenceService;

    /**
     * 新建药品定义。
     */
    public Long createMedicationDefinition(CreateMedicationDefinitionCommand command) {
        List<CreateMedicationDefinitionCommand.CreateMedicationDefinitionItemCommand> items = command.resolveItems();
        List<MedicationDefinition> toSave = new ArrayList<>();
        for (CreateMedicationDefinitionCommand.CreateMedicationDefinitionItemCommand item : items) {
            MedicationDefinition definition = new MedicationDefinition();
            this.fillDefinitionFromCreateItem(definition, item);
            definition.validateRequiredForCreate();
            definition.initPublished();
            toSave.add(definition);
        }
        this.assertBusNoUniqueInBatch(toSave);
        List<MedicationDefinition> saved = medicationDefinitionRepository.saveAll(toSave);
        for (MedicationDefinition definition : saved) {
            domainEventPublisher.publish(new MedicationDefinitionCreatedEvent(
                definition.getId(), definition.getBusNo(), definition.getName(), definition.getOrgId(), command.getOperatorId()));
        }
        return saved.get(0).getId();
    }

    /**
     * 编辑药品定义。
     */
    public void editMedicationDefinition(EditMedicationDefinitionCommand command) {
        List<EditMedicationDefinitionCommand.EditMedicationDefinitionItemCommand> items = command.resolveItems();
        List<MedicationDefinition> toSave = new ArrayList<>();
        for (EditMedicationDefinitionCommand.EditMedicationDefinitionItemCommand item : items) {
            MedicationDefinition definition = this.requireDefinition(item.getId());
            definition.ensureEditableStatus();
            this.fillDefinitionFromEditItem(definition, item);
            definition.setVersion(this.nextVersion(definition.getVersion()));
            toSave.add(definition);
        }
        medicationDefinitionRepository.saveAll(toSave);
        for (MedicationDefinition definition : toSave) {
            domainEventPublisher.publish(
                new MedicationDefinitionUpdatedEvent(
                    definition.getId(),
                    definition.getName(),
                    command.getOperatorId()));
        }
    }

    /**
     * 批量停用药品定义。
     */
    public void retireMedicationDefinitions(RetireMedicationDefinitionsCommand command) {
        List<RetireMedicationDefinitionsCommand.RetireMedicationDefinitionsItemCommand> items = command.resolveItems();
        List<MedicationDefinition> toSave = new ArrayList<>();
        for (RetireMedicationDefinitionsCommand.RetireMedicationDefinitionsItemCommand item : items) {
            MedicationDefinition definition = this.requireDefinition(item.getId());
            definition.ensureRetirable();
            definition.retire();
            toSave.add(definition);
        }
        medicationDefinitionRepository.saveAll(toSave);
        for (MedicationDefinition definition : toSave) {
            domainEventPublisher.publish(new MedicationDefinitionRetiredEvent(definition.getId(), definition.getStatusEnum(), command.getOperatorId()));
        }
    }

    /**
     * 批量重新启用药品定义。
     */
    public void activateMedicationDefinitions(ActivateMedicationDefinitionsCommand command) {
        List<ActivateMedicationDefinitionsCommand.ActivateMedicationDefinitionsItemCommand> items = command.resolveItems();
        List<MedicationDefinition> toSave = new ArrayList<>();
        for (ActivateMedicationDefinitionsCommand.ActivateMedicationDefinitionsItemCommand item : items) {
            MedicationDefinition definition = this.requireDefinition(item.getId());
            definition.ensureActivatable();
            definition.activate();
            toSave.add(definition);
        }
        medicationDefinitionRepository.saveAll(toSave);
        for (MedicationDefinition definition : toSave) {
            domainEventPublisher.publish(new MedicationDefinitionActivatedEvent(definition.getId(), definition.getStatusEnum(), command.getOperatorId()));
        }
    }

    /**
     * 废弃药品定义。
     */
    public void discardMedicationDefinition(DiscardMedicationDefinitionCommand command) {
        List<DiscardMedicationDefinitionCommand.DiscardMedicationDefinitionItemCommand> items = command.resolveItems();
        List<MedicationDefinition> toSave = new ArrayList<>();
        for (DiscardMedicationDefinitionCommand.DiscardMedicationDefinitionItemCommand item : items) {
            MedicationDefinition definition = this.requireDefinition(item.getId());
            this.assertNoExternalReference(definition.getId());
            definition.markDeleted(command.getOperatorId());
            toSave.add(definition);
        }
        medicationDefinitionRepository.saveAll(toSave);
        for (DiscardMedicationDefinitionCommand.DiscardMedicationDefinitionItemCommand item : items) {
            domainEventPublisher.publish(new MedicationDefinitionDeletedEvent(item.getId(), command.getOperatorId()));
        }
    }

    /**
     * 批量导入目录。
     */
    public ImportMedicationCatalogResult importMedicationCatalog(ImportMedicationCatalogCommand command) {
        int successCount = 0;
        int failCount = 0;
        List<MedicationDefinition> toSave = new ArrayList<>();
        for (ImportMedicationCatalogCommand.MedicationDefinitionImportRowCommand row : command.getRows()) {
            // 业务分支：空值校验失败则跳过该行计入失败。
            if (row.getOrgId() == null || row.getName() == null) {
                failCount++;
                continue;
            }
            MedicationDefinition definition = new MedicationDefinition();
            // 业务分支：空值校验失败则跳过该行计入失败。
            if (StringUtils.isBlank(row.getBusNo())) {
                definition.setBusNo(businessSequenceService.getSeq(AggregateTypeConstants.MEDICATION_DEFINITION, BusinessSequenceConstants.BUS_NO_SEQ_LENGTH));
            } else {
                definition.setBusNo(row.getBusNo());
            }
            definition.setName(row.getName());
            definition.setCategoryCode(row.getCategoryCode());
            definition.setPurchaseUnitCode(row.getPurchaseUnitCode());
            definition.setOrgId(row.getOrgId());
            definition.initPublished();
            definition.validateRequiredForCreate();
            toSave.add(definition);
            successCount++;
        }
        this.assertBusNoUniqueInBatch(toSave);
        medicationDefinitionRepository.saveAll(toSave);
        domainEventPublisher.publish(new MedicationCatalogImportedEvent(command.getBatchNo(), successCount, failCount, command.getOperatorId()));
        return new ImportMedicationCatalogResult(successCount, failCount);
    }

    /**
     * 同步医保目录对码。
     */
    public void syncInsuranceCatalogMapping(SyncInsuranceCatalogMappingCommand command) {
        List<SyncInsuranceCatalogMappingCommand.SyncInsuranceCatalogMappingItemCommand> items = command.resolveItems();
        List<MedicationDefinition> toSave = new ArrayList<>();
        for (SyncInsuranceCatalogMappingCommand.SyncInsuranceCatalogMappingItemCommand item : items) {
            MedicationDefinition definition = this.requireDefinition(item.getId());
            definition.setYbMatchFlag(item.getYbMatchFlag());
            definition.setYbNo(item.getYbNo());
            definition.setChrgitmLv(item.getChrgitmLv());
            definition.validateInsuranceMapping();
            toSave.add(definition);
        }
        medicationDefinitionRepository.saveAll(toSave);
        for (SyncInsuranceCatalogMappingCommand.SyncInsuranceCatalogMappingItemCommand item : items) {
            domainEventPublisher.publish(new MedicationDefinitionUpdatedEvent(item.getId(), "insuranceMapping", command.getOperatorId()));
        }
    }

    /**
     * 私有方法：requireDefinition。
     */
    private MedicationDefinition requireDefinition(Long id) {
        return medicationDefinitionRepository.findById(id).orElseThrow(
            () -> new BusinessException(ErrorCodeConstants.MEDICATION_DEFINITION_NOT_FOUND, "药品定义不存在"));
    }

    /**
     * 私有方法：assertBusNoUniqueInBatch。
     */
    private void assertBusNoUniqueInBatch(List<MedicationDefinition> definitions) {
        Map<Long, Set<String>> inputBusNoIndex = definitions.stream()
            .collect(Collectors.groupingBy(
                MedicationDefinition::getOrgId,
                Collectors.mapping(MedicationDefinition::getBusNo, Collectors.toSet())));
        int inputBusNoCount = definitions.size();
        int deduplicatedInputBusNoCount = inputBusNoIndex.values().stream().mapToInt(Set::size).sum();
        // 业务分支：空值校验失败则跳过该行计入失败。
        if (inputBusNoCount != deduplicatedInputBusNoCount) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_DEFINITION_RULE_CONFLICT, "导入批次内存在重复业务编码");
        }
        List<Long> orgIds = new ArrayList<>(inputBusNoIndex.keySet());
        Set<String> allBusNos = new HashSet<>();
        for (Set<String> busNoSet : inputBusNoIndex.values()) {
            allBusNos.addAll(busNoSet);
        }
        List<MedicationDefinition> existed = medicationDefinitionRepository.findByOrgIdInAndBusNoIn(
            orgIds, new ArrayList<>(allBusNos));
        for (MedicationDefinition existedDefinition : existed) {
            Set<String> currentOrgBusNos = inputBusNoIndex.get(existedDefinition.getOrgId());
            // 业务分支：空值校验失败则跳过该行计入失败。
            if (currentOrgBusNos != null && currentOrgBusNos.contains(existedDefinition.getBusNo())) {
                throw new BusinessException(ErrorCodeConstants.MEDICATION_DEFINITION_RULE_CONFLICT, "药品业务编码冲突");
            }
        }
    }

    /**
     * 私有方法：buildSimpleIndexCode。
     */
    private String buildSimpleIndexCode(String name) {
        // 业务分支：空值校验失败则跳过该行计入失败。
        if (StringUtils.isBlank(name)) {
            return null;
        }
        return name.trim().toUpperCase();
    }

    private void fillDefinitionFromCreateItem(
        MedicationDefinition definition,
        CreateMedicationDefinitionCommand.CreateMedicationDefinitionItemCommand item) {
        definition.setBusNo(StringUtils.defaultIfBlank(
            item.getBusNo(),
            businessSequenceService.getSeq(AggregateTypeConstants.MEDICATION_DEFINITION, BusinessSequenceConstants.BUS_NO_SEQ_LENGTH)));
        definition.setVersion(StringUtils.defaultIfBlank(item.getVersion(), "1"));
        definition.setName(item.getName());
        definition.setMerchandiseName(item.getMerchandiseName());
        definition.setPyStr(this.resolvePyCode(item.getPyStr(), item.getName()));
        definition.setWbStr(this.resolveWbCode(item.getWbStr(), item.getName()));
        definition.setMerchandisePyStr(
            this.resolvePyCode(item.getMerchandisePyStr(), item.getMerchandiseName()));
        definition.setMerchandiseWbStr(
            this.resolveWbCode(item.getMerchandiseWbStr(), item.getMerchandiseName()));
        definition.setCategoryCode(item.getCategoryCode());
        definition.setPurchaseUnitCode(item.getPurchaseUnitCode());
        definition.setSplitUnitCode(item.getSplitUnitCode());
        definition.setPackageUnitCode(item.getPackageUnitCode());
        definition.setStockUnitCode(item.getStockUnitCode());
        definition.setDoseUnitCode(item.getDoseUnitCode());
        definition.setDoseUnitRatio(item.getDoseUnitRatio());
        definition.setPurchaseUnitRatio(item.getPurchaseUnitRatio());
        definition.setStockUnitRatio(item.getStockUnitRatio());
        definition.setPackageUnitRatio(item.getPackageUnitRatio());
        definition.setOrgId(item.getOrgId());
        definition.setLocationId(item.getLocationId());
        definition.setDoseFormCode(item.getDoseFormCode());
        definition.setMethodCode(item.getMethodCode());
        definition.setRateCode(item.getRateCode());
        definition.setYbNo(item.getYbNo());
        definition.setYbMatchFlag(item.getYbMatchFlag());
        definition.setChrgitmLv(item.getChrgitmLv());
        definition.setPharmacologyCategoryCode(item.getPharmacologyCategoryCode());
        definition.setManufacturerId(item.getManufacturerId());
        definition.setManufacturerText(item.getManufacturerText());
        definition.setSupplyId(item.getSupplyId());
        definition.setApprovalNumber(item.getApprovalNumber());
        definition.setNationalDrugCode(item.getNationalDrugCode());
        definition.setTotalVolume(item.getTotalVolume());
        definition.setUsageLimit(item.getUsageLimit());
        definition.setDose(item.getDose());
        definition.setMaxUnit(item.getMaxUnit());
        definition.setDosageInstruction(item.getDosageInstruction());
        definition.setRestrictedScope(item.getRestrictedScope());
        definition.setPartAttributeEnum(item.getPartAttributeEnum());
        definition.setThoPartAttributeEnum(item.getThoPartAttributeEnum());
        definition.setDefEncounterUnitCode(item.getDefEncounterUnitCode());
        definition.setDefInhospitalUnitCode(item.getDefInhospitalUnitCode());
        definition.setItemMinQuantity(item.getItemMinQuantity());
        definition.setItemMaxQuantity(item.getItemMaxQuantity());
        definition.setBasicFlag(item.getBasicFlag());
        definition.setSkinTestFlag(item.getSkinTestFlag());
        definition.setInjectFlag(item.getInjectFlag());
        definition.setChildrenFlag(item.getChildrenFlag());
        definition.setSelfFlag(item.getSelfFlag());
        definition.setActiveFlag(item.getActiveFlag());
        definition.setRestrictedFlag(item.getRestrictedFlag());
        definition.setAntibioticFlag(item.getAntibioticFlag());
        definition.setRxFlag(item.getRxFlag());
        definition.setDrug69Code(item.getDrug69Code());
        definition.setDefinition(item.getDefinition());
        definition.setDddCode(item.getDddCode());
        definition.setDddUnitCode(item.getDddUnitCode());
        definition.setMinRateCode(item.getMinRateCode());
        definition.setMaxRateCode(item.getMaxRateCode());
    }

    private void fillDefinitionFromEditItem(
        MedicationDefinition definition,
        EditMedicationDefinitionCommand.EditMedicationDefinitionItemCommand item) {
        definition.setBusNo(StringUtils.defaultIfBlank(item.getBusNo(), definition.getBusNo()));
        definition.setName(item.getName());
        definition.setMerchandiseName(item.getMerchandiseName());
        definition.setPyStr(this.resolvePyCode(item.getPyStr(), item.getName()));
        definition.setWbStr(this.resolveWbCode(item.getWbStr(), item.getName()));
        definition.setMerchandisePyStr(
            this.resolvePyCode(item.getMerchandisePyStr(), item.getMerchandiseName()));
        definition.setMerchandiseWbStr(
            this.resolveWbCode(item.getMerchandiseWbStr(), item.getMerchandiseName()));
        definition.setCategoryCode(item.getCategoryCode());
        definition.setPurchaseUnitCode(item.getPurchaseUnitCode());
        definition.setSplitUnitCode(item.getSplitUnitCode());
        definition.setPackageUnitCode(item.getPackageUnitCode());
        definition.setStockUnitCode(item.getStockUnitCode());
        definition.setDoseUnitCode(item.getDoseUnitCode());
        definition.setDoseUnitRatio(item.getDoseUnitRatio());
        definition.setPurchaseUnitRatio(item.getPurchaseUnitRatio());
        definition.setStockUnitRatio(item.getStockUnitRatio());
        definition.setPackageUnitRatio(item.getPackageUnitRatio());
        definition.setLocationId(item.getLocationId());
        definition.setDoseFormCode(item.getDoseFormCode());
        definition.setMethodCode(item.getMethodCode());
        definition.setRateCode(item.getRateCode());
        definition.setYbNo(item.getYbNo());
        definition.setYbMatchFlag(item.getYbMatchFlag());
        definition.setChrgitmLv(item.getChrgitmLv());
        definition.setPharmacologyCategoryCode(item.getPharmacologyCategoryCode());
        definition.setManufacturerId(item.getManufacturerId());
        definition.setManufacturerText(item.getManufacturerText());
        definition.setSupplyId(item.getSupplyId());
        definition.setApprovalNumber(item.getApprovalNumber());
        definition.setNationalDrugCode(item.getNationalDrugCode());
        definition.setTotalVolume(item.getTotalVolume());
        definition.setUsageLimit(item.getUsageLimit());
        definition.setDose(item.getDose());
        definition.setMaxUnit(item.getMaxUnit());
        definition.setDosageInstruction(item.getDosageInstruction());
        definition.setRestrictedScope(item.getRestrictedScope());
        definition.setPartAttributeEnum(item.getPartAttributeEnum());
        definition.setThoPartAttributeEnum(item.getThoPartAttributeEnum());
        definition.setDefEncounterUnitCode(item.getDefEncounterUnitCode());
        definition.setDefInhospitalUnitCode(item.getDefInhospitalUnitCode());
        definition.setItemMinQuantity(item.getItemMinQuantity());
        definition.setItemMaxQuantity(item.getItemMaxQuantity());
        definition.setBasicFlag(item.getBasicFlag());
        definition.setSkinTestFlag(item.getSkinTestFlag());
        definition.setInjectFlag(item.getInjectFlag());
        definition.setChildrenFlag(item.getChildrenFlag());
        definition.setSelfFlag(item.getSelfFlag());
        definition.setActiveFlag(item.getActiveFlag());
        definition.setRestrictedFlag(item.getRestrictedFlag());
        definition.setAntibioticFlag(item.getAntibioticFlag());
        definition.setRxFlag(item.getRxFlag());
        definition.setDrug69Code(item.getDrug69Code());
        definition.setDefinition(item.getDefinition());
        definition.setDddCode(item.getDddCode());
        definition.setDddUnitCode(item.getDddUnitCode());
        definition.setMinRateCode(item.getMinRateCode());
        definition.setMaxRateCode(item.getMaxRateCode());
    }

    private String resolvePyCode(String inputCode, String sourceName) {
        if (StringUtils.isNotBlank(inputCode)) {
            return inputCode.trim().toUpperCase();
        }
        return SearchCodeUtil.toPyStr(StringUtils.defaultString(sourceName));
    }

    private String resolveWbCode(String inputCode, String sourceName) {
        if (StringUtils.isNotBlank(inputCode)) {
            return inputCode.trim().toUpperCase();
        }
        return SearchCodeUtil.toWbStr(StringUtils.defaultString(sourceName));
    }

    private String nextVersion(String currentVersion) {
        if (StringUtils.isBlank(currentVersion)) {
            return "1";
        }
        try {
            return String.valueOf(Integer.parseInt(currentVersion) + 1);
        } catch (NumberFormatException ex) {
            return currentVersion + "_1";
        }
    }

    /**
     * 私有方法：assertNoExternalReference。
     */
    private void assertNoExternalReference(Long medicationId) {
        // 业务分支：空值校验失败则跳过该行计入失败。
        if (medicationRequestRepository.existsByMedicationId(medicationId)
            || medicationDispenseRepository.existsByMedicationId(medicationId)) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_DEFINITION_RULE_CONFLICT, "药品已被业务引用，禁止废弃");
        }
    }
}

