/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.medication.medicationdefinition.service;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.health.domain.share.constants.BusinessSequenceConstants;
import com.openhis.health.domain.medication.medicationdefinition.command.*;
import com.openhis.health.domain.medication.medicationdefinition.entity.MedicationDefinition;
import com.openhis.health.domain.medication.medicationdefinition.repository.MedicationDefinitionRepository;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.events.medication.medicationdefinition.*;
import com.openhis.health.domain.share.utils.SearchCodeUtil;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.domain.event.IDomainEventPublisher;
import com.openhis.whale.core.infrastructure.sequence.BusinessSequenceService;
import com.openhis.whale.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 药品定义写侧领域服务。
 */
@Service
@RequiredArgsConstructor
public class MedicationDefinitionDomainService {

    private final MedicationDefinitionRepository medicationDefinitionRepository;
    private final IDomainEventPublisher domainEventPublisher;
    /** 药品定义 busNo：主数据全局递增，前缀 {@link AggregateTypeConstants#MEDICATION_DEFINITION}。 */
    private final BusinessSequenceService businessSequenceService;

    /** 采番撞号时单条最大重试次数（序号落后或并发占用）。 */
    private static final int BUS_NO_ALLOCATE_MAX_ATTEMPTS = 32;

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
     *
     * <p>Pre：应用层须先按编辑业务码 {@code CatalogEditabilityConstants#isLocked} 断言无业务引用。</p>
     */
    public void discardMedicationDefinition(DiscardMedicationDefinitionCommand command) {
        List<DiscardMedicationDefinitionCommand.DiscardMedicationDefinitionItemCommand> items = command.resolveItems();
        List<MedicationDefinition> toSave = new ArrayList<>();
        for (DiscardMedicationDefinitionCommand.DiscardMedicationDefinitionItemCommand item : items) {
            MedicationDefinition definition = this.requireDefinition(item.getId());
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
            definition.setBusNo(this.allocateUniqueBusNo());
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
     * 私有方法：assertBusNoUniqueInBatch（同租户唯一）。
     */
    private void assertBusNoUniqueInBatch(List<MedicationDefinition> definitions) {
        Set<String> inputBusNos = new HashSet<>();
        for (MedicationDefinition definition : definitions) {
            String busNo = definition.getBusNo();
            if (StringUtils.isEmpty(busNo)) {
                throw new BusinessException(ErrorCodeConstants.MEDICATION_DEFINITION_RULE_CONFLICT, "药品业务编码不能为空");
            }
            if (!inputBusNos.add(busNo)) {
                throw new BusinessException(ErrorCodeConstants.MEDICATION_DEFINITION_RULE_CONFLICT, "导入批次内存在重复业务编码");
            }
        }
        List<MedicationDefinition> existed = medicationDefinitionRepository.findByBusNoIn(new ArrayList<>(inputBusNos));
        if (existed != null && !existed.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_DEFINITION_RULE_CONFLICT, "药品业务编码冲突");
        }
    }

    /**
     * 私有方法：buildSimpleIndexCode。
     */
    private String buildSimpleIndexCode(String name) {
        // 业务分支：空值校验失败则跳过该行计入失败。
        if (StringUtils.isEmpty(name)) {
            return null;
        }
        return name.trim().toUpperCase();
    }

    /**
     * 新建药品定义：从创建 Command 项填充实体字段（含助记码与版本默认值）。
     *
     * @param definition 待写入实体
     * @param item       创建项
     */
    private void fillDefinitionFromCreateItem(
        MedicationDefinition definition,
        CreateMedicationDefinitionCommand.CreateMedicationDefinitionItemCommand item) {
        definition.setBusNo(this.allocateUniqueBusNo());
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
        definition.setTraceUnitCode(item.getTraceUnitCode());
        definition.setTraceUnitRatio(item.getTraceUnitRatio());
        definition.setOrgId(item.getOrgId());
        definition.setLocationId(item.getLocationId());
        definition.setDoseFormCode(item.getDoseFormCode());
        definition.setMethodCode(item.getMethodCode());
        definition.setRateCode(item.getRateCode());
        definition.setYbNo(item.getYbNo());
        definition.setYbMatchFlag(item.getYbMatchFlag());
        definition.setChrgitmLv(item.getChrgitmLv());
        definition.setMrSettlementTypeCode(item.getMrSettlementTypeCode());
        // 结余留用批次号：可选术语码，空表示未维护
        definition.setSurplusRetentionBatchCode(item.getSurplusRetentionBatchCode());
        definition.setPharmacologyCategoryCode(item.getPharmacologyCategoryCode());
        definition.setManufacturerId(item.getManufacturerId());
        // 目录维护不再采集供应商；应用层写入的 Command.supplyId 恒为 null
        definition.setSupplyId(item.getSupplyId());
        definition.setApprovalNumber(item.getApprovalNumber());
        definition.setNationalDrugCode(item.getNationalDrugCode());
        definition.setTotalVolume(item.getTotalVolume());
        definition.setUsageLimit(item.getUsageLimit());
        definition.setDose(item.getDose());
        definition.setMinUnit(item.getMinUnit());
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
        definition.setNationalMonitorFlag(item.getNationalMonitorFlag());
        definition.setCentralPurchaseFlag(item.getCentralPurchaseFlag());
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

    /**
     * 编辑药品定义：从编辑 Command 项覆盖可变字段（含助记码重算）。
     *
     * @param definition 待更新实体
     * @param item       编辑项
     */
    private void fillDefinitionFromEditItem(
        MedicationDefinition definition,
        EditMedicationDefinitionCommand.EditMedicationDefinitionItemCommand item) {
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
        definition.setTraceUnitCode(item.getTraceUnitCode());
        definition.setTraceUnitRatio(item.getTraceUnitRatio());
        definition.setLocationId(item.getLocationId());
        definition.setDoseFormCode(item.getDoseFormCode());
        definition.setMethodCode(item.getMethodCode());
        definition.setRateCode(item.getRateCode());
        definition.setYbNo(item.getYbNo());
        definition.setYbMatchFlag(item.getYbMatchFlag());
        definition.setChrgitmLv(item.getChrgitmLv());
        definition.setMrSettlementTypeCode(item.getMrSettlementTypeCode());
        // 结余留用批次号：可选术语码，空表示未维护
        definition.setSurplusRetentionBatchCode(item.getSurplusRetentionBatchCode());
        definition.setPharmacologyCategoryCode(item.getPharmacologyCategoryCode());
        definition.setManufacturerId(item.getManufacturerId());
        // 目录维护不再采集供应商；应用层写入的 Command.supplyId 恒为 null
        definition.setSupplyId(item.getSupplyId());
        definition.setApprovalNumber(item.getApprovalNumber());
        definition.setNationalDrugCode(item.getNationalDrugCode());
        definition.setTotalVolume(item.getTotalVolume());
        definition.setUsageLimit(item.getUsageLimit());
        definition.setDose(item.getDose());
        definition.setMinUnit(item.getMinUnit());
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
        definition.setNationalMonitorFlag(item.getNationalMonitorFlag());
        definition.setCentralPurchaseFlag(item.getCentralPurchaseFlag());
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

    /**
     * 拼音助记码：入参非空时规范化大写，否则由源名称自动生成。
     *
     * @param inputCode   请求携带 py；可空
     * @param sourceName  回落源名称（品名/商品名）
     * @return 拼音助记码
     */
    private String resolvePyCode(String inputCode, String sourceName) {
        if (StringUtils.isNotEmpty(inputCode)) {
            return inputCode.trim().toUpperCase();
        }
        return SearchCodeUtil.toPyStr(StringUtils.defaultString(sourceName));
    }

    /**
     * 五笔助记码：入参非空时规范化大写，否则由源名称自动生成。
     *
     * @param inputCode   请求携带 wb；可空
     * @param sourceName  回落源名称（品名/商品名）
     * @return 五笔助记码
     */
    private String resolveWbCode(String inputCode, String sourceName) {
        if (StringUtils.isNotEmpty(inputCode)) {
            return inputCode.trim().toUpperCase();
        }
        return SearchCodeUtil.toWbStr(StringUtils.defaultString(sourceName));
    }

    /**
     * 采番一条同租户未被占用的药品业务编码；冲突则继续取下一号。
     *
     * @return 未占用的业务编码
     */
    private String allocateUniqueBusNo() {
        for (int attempt = 0; attempt < BUS_NO_ALLOCATE_MAX_ATTEMPTS; attempt++) {
            String busNo = businessSequenceService.getSeq(
                AggregateTypeConstants.MEDICATION_DEFINITION,
                BusinessSequenceConstants.MASTER_DATA_2_PREFIX_SEQ_LENGTH);
            if (!medicationDefinitionRepository.existsByBusNo(busNo)) {
                return busNo;
            }
        }
        throw new BusinessException(
            ErrorCodeConstants.MEDICATION_DEFINITION_RULE_CONFLICT, "药品业务编码采番冲突，请稍后重试");
    }

    /**
     * 药品定义版本号递增：空版本从 1 起，数字版本 +1，非数字版本追加 {@code _1}。
     *
     * @param currentVersion 当前版本字符串
     * @return 下一版本号
     */
    private String nextVersion(String currentVersion) {
        if (StringUtils.isEmpty(currentVersion)) {
            return "1";
        }
        try {
            return String.valueOf(Integer.parseInt(currentVersion) + 1);
        } catch (NumberFormatException ex) {
            return currentVersion + "_1";
        }
    }
}
