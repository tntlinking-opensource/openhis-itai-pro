/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.shared.inpatient;

import com.openhis.health.domain.administration.account.service.AccountDomainService;
import com.openhis.health.domain.administration.encounter.command.AdmissionConditionItem;
import com.openhis.health.domain.administration.encounter.command.BatchUpsertEncounterDiagnosisAssociationCommand;
import com.openhis.health.domain.administration.encounter.command.RegisterInpatientEncounterCommand;
import com.openhis.health.domain.administration.encounter.command.UpsertEncounterDiagnosisAssociationCommand;
import com.openhis.health.domain.administration.encounter.queries.model.EncounterDetailQueryModel;
import com.openhis.health.domain.administration.encounter.queries.model.EncounterDiagnosisQueryModel;
import com.openhis.health.domain.administration.encounter.service.EncounterDomainService;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.domain.clinical.condition.command.SaveDoctorDiagnosisCommand;
import com.openhis.health.domain.clinical.condition.command.SaveDoctorDiagnosisTcmCommand;
import com.openhis.health.domain.clinical.condition.service.ConditionDomainService;
import com.openhis.health.domain.clinical.condition.service.ConditionQueryService;
import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.health.domain.share.constants.BusinessSequenceConstants;
import com.openhis.health.domain.share.constants.DoctorStationDiagnosisConstants;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.enums.IptPatientDiseaseDiagType;
import com.openhis.health.domain.share.enums.YbMedType;
import com.openhis.whale.core.infrastructure.sequence.BusinessSequenceService;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 住院登记写路径跨聚合编排：开立登记账户与入院诊断（病情事实 + 就诊诊断关联）。
 *
 * <p>Encounter 写侧不再直接依赖 Account / Condition 聚合（B.6.1.3.1）。</p>
 */
@Component
@RequiredArgsConstructor
public class EncounterInpatientAdmissionOrchestrator {

    /** 账户写侧领域服务（住院登记个人现金户开立）。 */
    private final AccountDomainService accountDomainService;
    /** 业务序列服务（中医病证组号 {@code tcm_syndrome_group} 分配）。 */
    private final BusinessSequenceService businessSequenceService;
    /** 病情事实写侧领域服务（新开入院诊断 condition）。 */
    private final ConditionDomainService conditionDomainService;
    /** 病情事实读侧查询（主诊已关联定义复用判定）。 */
    private final ConditionQueryService conditionQueryService;
    /** 就诊写侧领域服务（就诊诊断关联 upsert）。 */
    private final EncounterDomainService encounterDomainService;
    /** 就诊读侧查询（患者反查与主诊存在判定）。 */
    private final EncounterQueryService encounterQueryService;

    /**
     * 住院登记落库后的账户开立与入院诊断写入（含有申请登记的诊断补落策略）。
     *
     * @param command 住院登记命令（直登时 {@link RegisterInpatientEncounterCommand#getPatientId()} 与项上 encounterId 须已就绪）
     */
    public void completeAfterInpatientRegistration(RegisterInpatientEncounterCommand command) {
        List<RegisterInpatientEncounterCommand.RegisterInpatientEncounterItemCommand> items = command.resolveItems();
        Long operatorId = command.getOperatorId();
        for (RegisterInpatientEncounterCommand.RegisterInpatientEncounterItemCommand item : items) {
            Long encounterId = item.getEncounterId();
            if (encounterId == null) {
                throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "住院登记缺少就诊标识");
            }
            Long patientId = this.resolvePatientId(command, encounterId);
            accountDomainService.openInpatientRegAccount(
                patientId, encounterId, item.getContractNo(), operatorId);
            this.applyPlannedRegistrationAdmissionDiagnosisPolicy(
                patientId, encounterId, item.getAdmissionDiagnoses(), operatorId);
        }
    }

    /**
     * 写入入院诊断：新开或复用病情事实后维护就诊诊断关联（住院申请等场景复用）。
     *
     * @param patientId 患者主键
     * @param encounterId 住院就诊主键
     * @param dxItems 入院诊断明细（可为空）
     * @param operatorId 操作人
     */
    public void saveAdmissionDiagnoses(
        Long patientId,
        Long encounterId,
        List<AdmissionConditionItem> dxItems,
        Long operatorId) {
        if (dxItems == null || dxItems.isEmpty()) {
            return;
        }
        List<AdmissionConditionItem> normalizedItems = this.assignMissingTcmSyndromeGroups(dxItems);
        List<UpsertEncounterDiagnosisAssociationCommand.UpsertEncounterDiagnosisAssociationItemCommand> westernAssocItems =
            new ArrayList<>();
        List<BatchUpsertEncounterDiagnosisAssociationCommand.DiagnosisItemCommand> tcmAssocItems =
            new ArrayList<>();
        int rank = 1;
        for (AdmissionConditionItem dx : normalizedItems) {
            Long conditionId = dx.getConditionId();
            if (conditionId == null) {
                conditionId = this.resolveConditionIdForAdmissionDxItem(
                    patientId, encounterId, dx, operatorId);
            }
            if (Boolean.TRUE.equals(dx.getTcmFlag())) {
                tcmAssocItems.add(this.newAdmissionTcmAssocItem(dx, conditionId, rank));
            } else {
                westernAssocItems.add(this.newAdmissionDiagnosisAssocItem(encounterId, dx, conditionId, rank));
            }
            rank++;
        }
        if (!westernAssocItems.isEmpty()) {
            UpsertEncounterDiagnosisAssociationCommand assocCmd = new UpsertEncounterDiagnosisAssociationCommand();
            assocCmd.setOperatorId(operatorId);
            assocCmd.setItems(westernAssocItems);
            encounterDomainService.upsertEncounterDiagnosisAssociation(assocCmd);
        }
        if (!tcmAssocItems.isEmpty()) {
            BatchUpsertEncounterDiagnosisAssociationCommand tcmCmd = new BatchUpsertEncounterDiagnosisAssociationCommand();
            tcmCmd.setOperatorId(operatorId);
            BatchUpsertEncounterDiagnosisAssociationCommand.BatchUpsertEncounterDiagnosisAssociationItemCommand batchItem =
                new BatchUpsertEncounterDiagnosisAssociationCommand.BatchUpsertEncounterDiagnosisAssociationItemCommand();
            batchItem.setEncounterId(encounterId);
            batchItem.setDiagnosisItems(tcmAssocItems);
            tcmCmd.setItem(batchItem);
            encounterDomainService.batchUpsertTcmEncounterDiagnosisAssociation(tcmCmd);
        }
    }

    /**
     * 为未携带组号的中医一病一症行分配 {@code tcm_syndrome_group} 序列号。
     *
     * @param dxItems 原始入院诊断项
     * @return 已补全组号的副本列表
     */
    private List<AdmissionConditionItem> assignMissingTcmSyndromeGroups(List<AdmissionConditionItem> dxItems) {
        List<AdmissionConditionItem> copied = new ArrayList<>();
        int pendingTcmCount = 0;
        for (AdmissionConditionItem source : dxItems) {
            AdmissionConditionItem target = this.copyAdmissionConditionItem(source);
            copied.add(target);
            if (Boolean.TRUE.equals(target.getTcmFlag()) && StringUtils.isEmpty(target.getTcmSyndromeGroup())) {
                pendingTcmCount++;
            }
        }
        if (pendingTcmCount == 0) {
            return copied;
        }
        List<String> newGroupKeys = businessSequenceService.getSeqByDay(
            AggregateTypeConstants.CONDITION, BusinessSequenceConstants.BUS_NO_SEQ_LENGTH, 1);
        String assignedGroupKey = newGroupKeys.get(0);
        for (AdmissionConditionItem item : copied) {
            if (Boolean.TRUE.equals(item.getTcmFlag()) && StringUtils.isEmpty(item.getTcmSyndromeGroup())) {
                item.setTcmSyndromeGroup(assignedGroupKey);
            }
        }
        return copied;
    }

    /**
     * 浅拷贝入院诊断项（登记/申请合并时避免修改原列表引用）。
     *
     * @param source 源诊断项
     * @return 字段拷贝的新实例
     */
    private AdmissionConditionItem copyAdmissionConditionItem(AdmissionConditionItem source) {
        AdmissionConditionItem target = new AdmissionConditionItem();
        target.setConditionId(source.getConditionId());
        target.setConditionDefinitionId(source.getConditionDefinitionId());
        target.setPrimaryFlag(source.getPrimaryFlag());
        target.setDescription(source.getDescription());
        target.setTcmFlag(source.getTcmFlag());
        target.setTcmSyndromeGroup(source.getTcmSyndromeGroup());
        return target;
    }

    /**
     * 有申请登记（{@code PLANNED→REGISTERED}）时的入院诊断策略：申请已落库则禁止登记侧再提交列表。
     *
     * @param patientId         患者主键
     * @param encounterId       住院就诊主键
     * @param admissionDxItems  登记侧提交的入院诊断项（可为空）
     * @param operatorId        操作人
     * @throws BusinessException 申请已落主诊且登记侧再次提交时
     */
    private void applyPlannedRegistrationAdmissionDiagnosisPolicy(
        Long patientId,
        Long encounterId,
        List<AdmissionConditionItem> admissionDxItems,
        Long operatorId) {
        if (admissionDxItems == null || admissionDxItems.isEmpty()) {
            return;
        }
        if (this.encounterHasPrimaryAdmissionDiagnosis(encounterId)) {
            throw new BusinessException(
                ErrorCodeConstants.ENCOUNTER_RULE_CONFLICT,
                "入院诊断已在住院申请时确定，登记不可修改");
        }
        this.saveAdmissionDiagnoses(patientId, encounterId, admissionDxItems, operatorId);
    }

    /**
     * 解析登记患者 ID：Command 未带时从已创建就诊反查。
     *
     * @param command     登记 Command
     * @param encounterId 就诊 ID
     * @return 患者 ID
     */
    private Long resolvePatientId(RegisterInpatientEncounterCommand command, Long encounterId) {
        if (command.getPatientId() != null) {
            return command.getPatientId();
        }
        EncounterDetailQueryModel encounter = encounterQueryService.mustGetEncounter(encounterId);
        return encounter.getPatientId();
    }

    /**
     * 就诊是否已存在主诊入院诊断（登记侧禁止覆盖申请已落库主诊）。
     *
     * @param encounterId 就诊 ID
     * @return 已有主诊为 true
     */
    private boolean encounterHasPrimaryAdmissionDiagnosis(Long encounterId) {
        if (encounterId == null) {
            return false;
        }
        List<EncounterDiagnosisQueryModel> rows = encounterQueryService.listEncounterDiagnoses(encounterId);
        for (EncounterDiagnosisQueryModel row : rows) {
            if (Boolean.TRUE.equals(row.getPrimaryFlag())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 为单条入院诊断项解析或新建 conditionId：已关联同定义则复用，否则按中医/西医分支落库。
     *
     * @param patientId   患者 ID
     * @param encounterId 就诊 ID
     * @param dx          入院诊断项
     * @param operatorId  操作人
     * @return condition 主键
     * @throws BusinessException 缺诊断目录或规则冲突
     */
    private Long resolveConditionIdForAdmissionDxItem(
        Long patientId,
        Long encounterId,
        AdmissionConditionItem dx,
        Long operatorId) {
        if (dx.getConditionDefinitionId() == null) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "入院诊断须指定病情或诊断目录");
        }
        Long existingConditionId = this.resolveExistingConditionIdForAdmissionDefinition(
            encounterId, dx.getConditionDefinitionId());
        if (existingConditionId != null) {
            return existingConditionId;
        }
        if (Boolean.TRUE.equals(dx.getTcmFlag())) {
            SaveDoctorDiagnosisTcmCommand saveTcmCmd = new SaveDoctorDiagnosisTcmCommand();
            saveTcmCmd.setEncounterId(encounterId);
            saveTcmCmd.setPatientId(patientId);
            saveTcmCmd.setOperatorId(operatorId);
            SaveDoctorDiagnosisTcmCommand.DoctorDiagnosisTcmItemCommand tcmItem =
                new SaveDoctorDiagnosisTcmCommand.DoctorDiagnosisTcmItemCommand();
            tcmItem.setDefinitionId(dx.getConditionDefinitionId());
            tcmItem.setDescription(dx.getDescription());
            tcmItem.setTcmFlag(Boolean.TRUE);
            tcmItem.setVerificationStatusEnum(
                DoctorStationDiagnosisConstants.ConditionVerificationStatusCode.CONFIRMED);
            saveTcmCmd.setItems(Collections.singletonList(tcmItem));
            List<Long> savedTcmIds = conditionDomainService.saveDoctorDiagnosisTcm(saveTcmCmd);
            return savedTcmIds.get(0);
        }
        SaveDoctorDiagnosisCommand saveCmd = new SaveDoctorDiagnosisCommand();
        saveCmd.setEncounterId(encounterId);
        saveCmd.setPatientId(patientId);
        saveCmd.setOperatorId(operatorId);
        saveCmd.setAdmissionDiagnosisFlow(Boolean.TRUE);
        SaveDoctorDiagnosisCommand.DoctorDiagnosisItemCommand diagItem =
            new SaveDoctorDiagnosisCommand.DoctorDiagnosisItemCommand();
        diagItem.setDefinitionId(dx.getConditionDefinitionId());
        diagItem.setDescription(dx.getDescription());
        diagItem.setVerificationStatusEnum(
            DoctorStationDiagnosisConstants.ConditionVerificationStatusCode.CONFIRMED);
        saveCmd.setItems(Collections.singletonList(diagItem));
        List<Long> savedIds = conditionDomainService.saveDoctorDiagnosis(saveCmd);
        return savedIds.get(0);
    }

    /**
     * 就诊主诊已关联同一诊断定义时复用已有 conditionId，避免重复创建入院诊断。
     *
     * @param encounterId            就诊 ID
     * @param conditionDefinitionId  诊断目录定义 ID
     * @return 可复用的 condition 主键；主诊不存在或定义不一致时 null
     */
    private Long resolveExistingConditionIdForAdmissionDefinition(
        Long encounterId, Long conditionDefinitionId) {
        if (encounterId == null || conditionDefinitionId == null) {
            return null;
        }
        List<EncounterDiagnosisQueryModel> rows = encounterQueryService.listEncounterDiagnoses(encounterId);
        Long linkedConditionId = null;
        for (EncounterDiagnosisQueryModel row : rows) {
            if (Boolean.TRUE.equals(row.getPrimaryFlag())) {
                linkedConditionId = row.getConditionId();
                break;
            }
        }
        if (linkedConditionId == null) {
            return null;
        }
        Long existingDefinitionId = conditionQueryService.findDefinitionIdByConditionId(linkedConditionId);
        if (existingDefinitionId == null || !conditionDefinitionId.equals(existingDefinitionId)) {
            return null;
        }
        return linkedConditionId;
    }

    /**
     * 构建西医入院诊断就诊关联 upsert 项。
     *
     * @param encounterId 就诊主键
     * @param dx          入院诊断项
     * @param conditionId 病情事实主键
     * @param rank        排序序号
     * @return 就诊诊断关联命令项
     */
    private UpsertEncounterDiagnosisAssociationCommand.UpsertEncounterDiagnosisAssociationItemCommand newAdmissionDiagnosisAssocItem(
        Long encounterId,
        AdmissionConditionItem dx,
        Long conditionId,
        int rank) {
        UpsertEncounterDiagnosisAssociationCommand.UpsertEncounterDiagnosisAssociationItemCommand assoc =
            new UpsertEncounterDiagnosisAssociationCommand.UpsertEncounterDiagnosisAssociationItemCommand();
        assoc.setEncounterId(encounterId);
        assoc.setConditionId(conditionId);
        assoc.setPrimaryFlag(dx.getPrimaryFlag());
        assoc.setRank(rank);
        assoc.setDescription(dx.getDescription());
        assoc.setDiagTypeCode(IptPatientDiseaseDiagType.ADMISSION.getCode());
        assoc.setMedType(YbMedType.GENERAL_OUTPATIENT.getCode());
        assoc.setTcmFlag(Boolean.FALSE);
        return assoc;
    }

    /**
     * 构建中医入院诊断批量关联项（含病证组号）。
     *
     * @param dx          入院诊断项
     * @param conditionId 病情事实主键
     * @param rank        排序序号
     * @return 中医就诊诊断关联命令项
     */
    private BatchUpsertEncounterDiagnosisAssociationCommand.DiagnosisItemCommand newAdmissionTcmAssocItem(
        AdmissionConditionItem dx,
        Long conditionId,
        int rank) {
        BatchUpsertEncounterDiagnosisAssociationCommand.DiagnosisItemCommand assoc =
            new BatchUpsertEncounterDiagnosisAssociationCommand.DiagnosisItemCommand();
        assoc.setConditionId(conditionId);
        assoc.setPrimaryFlag(dx.getPrimaryFlag());
        assoc.setRank(rank);
        assoc.setDescription(dx.getDescription());
        assoc.setDiagTypeCode(IptPatientDiseaseDiagType.ADMISSION.getCode());
        assoc.setMedType(YbMedType.GENERAL_OUTPATIENT.getCode());
        assoc.setTcmSyndromeGroup(StringUtils.trimToNull(dx.getTcmSyndromeGroup()));
        assoc.setTcmFlag(Boolean.TRUE);
        return assoc;
    }
}
