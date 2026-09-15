/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.shared.inpatient;

import com.openhis.health.application.shared.financial.EncounterChargeAccountOpener;
import com.openhis.health.domain.administration.encounter.command.AdmissionConditionItem;
import com.openhis.health.domain.administration.encounter.command.BatchUpsertEncounterDiagnosisAssociationCommand;
import com.openhis.health.domain.administration.encounter.command.RegisterInpatientEncounterCommand;
import com.openhis.health.domain.administration.encounter.command.UpsertEncounterDiagnosisAssociationCommand;
import com.openhis.health.domain.administration.encounter.queries.model.EncounterDetailQueryModel;
import com.openhis.health.domain.administration.encounter.queries.model.EncounterDiagnosisQueryModel;
import com.openhis.health.domain.administration.encounter.service.EncounterDomainService;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.domain.clinical.conditiondefinition.queries.model.ConditionDefinitionBatchItemQueryModel;
import com.openhis.health.domain.clinical.conditiondefinition.service.ConditionDefinitionQueryService;
import com.openhis.health.domain.financial.contract.entity.Contract;
import com.openhis.health.domain.financial.contract.service.ContractQueryService;
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
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 住院登记写路径跨聚合编排：开立登记账户与入院诊断（仅 adm_encounter_diagnosis，不经 Condition）。
 *
 * <p>账户口径与门诊挂号一致：人人个人现金自费户；省/市医保或工伤另开医保户（预交金仍进个人现金户）。</p>
 * <p>入院西医诊断：门诊医保目录若已绑多个国临，落库时一对多展开为 {@code clinical_v2}，
 * 住院医生站自行删改；中医不展开。</p>
 */
@Component
@RequiredArgsConstructor
public class EncounterInpatientAdmissionOrchestrator {

    /** 就诊挂账双账户幂等开立（个人现金自费 + 可选医保户）。 */
    private final EncounterChargeAccountOpener encounterChargeAccountOpener;
    /** 费用性质读侧：判断入院合同是否需并行医保户。 */
    private final ContractQueryService contractQueryService;
    /** 业务序列服务（中医病证组号 {@code tcm_syndrome_group} 分配）。 */
    private final BusinessSequenceService businessSequenceService;
    /** 就诊写侧领域服务（就诊诊断关联 upsert）。 */
    private final EncounterDomainService encounterDomainService;
    /** 就诊读侧查询（患者反查与主诊存在判定）。 */
    private final EncounterQueryService encounterQueryService;
    /** 诊断目录读侧：门诊医保诊断 → 国临一对多展开。 */
    private final ConditionDefinitionQueryService conditionDefinitionQueryService;

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
            // Cmd：个人现金自费户必开；医保/工伤入院另开 MEDICAL_INSURANCE（contract_no=入院费用性质）
            String contractNo = item.getContractNo();
            boolean openMedicalInsurance = this.resolveOpenMedicalInsurance(contractNo);
            encounterChargeAccountOpener.ensureEncounterChargeAccounts(
                patientId, encounterId, contractNo, openMedicalInsurance, item.getInsutype(), operatorId);
            this.applyPlannedRegistrationAdmissionDiagnosisPolicy(
                patientId, encounterId, item.getAdmissionDiagnoses(), operatorId);
        }
    }

    /**
     * 入院费用性质是否需并行医保账户。
     *
     * @param contractNo 入院合同 bus_no
     * @return 需开 MEDICAL_INSURANCE 时 true
     */
    private boolean resolveOpenMedicalInsurance(String contractNo) {
        if (StringUtils.isEmpty(contractNo)) {
            return false;
        }
        Optional<Contract> contractOpt =
            contractQueryService.findOptionalContractEntityByBusNo(contractNo.trim());
        if (contractOpt.isEmpty()) {
            return false;
        }
        Contract contract = contractOpt.get();
        return encounterChargeAccountOpener.needsParallelMedicalInsuranceAccount(
            contract.getYbFlag(), contract.getCategoryEnum());
    }

    /**
     * 写入入院诊断：直挂 conditionDefinitionId 维护就诊诊断关联（住院申请等场景复用）。
     *
     * <p>西医：若提交的是门诊医保目录定义，且有国临绑码，则展开为全部 {@code clinical_v2} 行
     *（一对多），供住院医生站自行删改；已是国临或无对照则原样落库。中医不展开。</p>
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
        // Pre：门诊医保西医 → 国临一对多展开（住院西医真源）
        List<AdmissionConditionItem> expandedWestern =
                this.expandWesternInsuranceDiagnosesToClinical(dxItems);
        List<AdmissionConditionItem> normalizedItems = this.assignMissingTcmSyndromeGroups(expandedWestern);
        List<UpsertEncounterDiagnosisAssociationCommand.UpsertEncounterDiagnosisAssociationItemCommand> westernAssocItems =
            new ArrayList<>();
        List<BatchUpsertEncounterDiagnosisAssociationCommand.DiagnosisItemCommand> tcmAssocItems =
            new ArrayList<>();
        int rank = 1;
        for (AdmissionConditionItem dx : normalizedItems) {
            if (dx.getConditionDefinitionId() == null) {
                throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "入院诊断须指定诊断目录");
            }
            if (Boolean.TRUE.equals(dx.getTcmFlag())) {
                tcmAssocItems.add(this.newAdmissionTcmAssocItem(dx, rank));
            } else {
                westernAssocItems.add(this.newAdmissionDiagnosisAssocItem(encounterId, dx, rank));
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
     * 门诊西医医保目录诊断 → 国临目录一对多展开。
     *
     * <p>已是 {@code clinical_v2}、中医、或无国临绑码：原样保留。
     * 有绑码：替换为全部国临行；原稿主诊落在排序后第一条，其余非主诊；
     * 住院医生站可再删改。</p>
     *
     * @param dxItems 原始入院诊断项
     * @return 展开后的入院诊断项
     */
    private List<AdmissionConditionItem> expandWesternInsuranceDiagnosesToClinical(
            List<AdmissionConditionItem> dxItems) {
        List<AdmissionConditionItem> result = new ArrayList<>();
        if (dxItems == null || dxItems.isEmpty()) {
            return result;
        }
        // Pre：批量装载目录，判定来源与医保主码
        List<Long> defIds = new ArrayList<>();
        for (AdmissionConditionItem item : dxItems) {
            if (item != null && item.getConditionDefinitionId() != null) {
                defIds.add(item.getConditionDefinitionId());
            }
        }
        Map<Long, ConditionDefinitionBatchItemQueryModel> defById = new HashMap<>();
        if (!defIds.isEmpty()) {
            for (ConditionDefinitionBatchItemQueryModel model :
                    conditionDefinitionQueryService.getConditionDefinitionListByIds(defIds)) {
                if (model != null && model.getId() != null) {
                    defById.put(model.getId(), model);
                }
            }
        }
        Set<String> ybKeys = new LinkedHashSet<>();
        for (AdmissionConditionItem item : dxItems) {
            if (item == null || Boolean.TRUE.equals(item.getTcmFlag())) {
                continue;
            }
            ConditionDefinitionBatchItemQueryModel def = defById.get(item.getConditionDefinitionId());
            String ybKey = this.resolveYbKeyForClinicalExpand(def);
            if (ybKey != null) {
                ybKeys.add(ybKey);
            }
        }
        Map<String, List<Long>> clinicalIdsByYb =
                conditionDefinitionQueryService.mapClinicalDefinitionIdsByYbNos(ybKeys);
        Set<Long> emittedClinicalIds = new LinkedHashSet<>();
        for (AdmissionConditionItem source : dxItems) {
            if (source == null) {
                continue;
            }
            // 业务分支：中医不走国临展开
            if (Boolean.TRUE.equals(source.getTcmFlag())) {
                result.add(this.copyAdmissionConditionItem(source));
                continue;
            }
            ConditionDefinitionBatchItemQueryModel def = defById.get(source.getConditionDefinitionId());
            // 业务分支：已是国临 → 原样
            if (def != null
                    && DoctorStationDiagnosisConstants.SOURCE_CLINICAL_V2.equals(def.getSourceEnum())) {
                result.add(this.copyAdmissionConditionItem(source));
                continue;
            }
            String ybKey = this.resolveYbKeyForClinicalExpand(def);
            List<Long> clinicalIds = null;
            if (ybKey != null) {
                clinicalIds = clinicalIdsByYb.get(ybKey);
            }
            // 业务分支：无国临绑码 → 保留原门诊目录（医生站可改）
            if (clinicalIds == null || clinicalIds.isEmpty()) {
                result.add(this.copyAdmissionConditionItem(source));
                continue;
            }
            boolean first = true;
            for (Long clinicalId : clinicalIds) {
                // 同批展开去重，避免多条门诊医保码撞同一国临
                if (!emittedClinicalIds.add(clinicalId)) {
                    continue;
                }
                AdmissionConditionItem expanded = new AdmissionConditionItem();
                // 展开后均为新建住院线关联，不复用门诊就诊诊断主键
                expanded.setEncounterDiagnosisId(null);
                expanded.setConditionDefinitionId(clinicalId);
                // 原稿主诊落第一条国临；其余非主诊，由住院医生调整
                if (first) {
                    expanded.setPrimaryFlag(source.getPrimaryFlag());
                    first = false;
                } else {
                    expanded.setPrimaryFlag(Boolean.FALSE);
                }
                expanded.setDescription(source.getDescription());
                expanded.setTcmFlag(Boolean.FALSE);
                expanded.setTcmSyndromeGroup(null);
                result.add(expanded);
            }
        }
        return result;
    }

    /**
     * 解析用于反查国临的医保主码；国临/中医/无法解析时返回 null（表示不展开）。
     *
     * @param def 目录批量行
     * @return 医保主码或 null
     */
    private String resolveYbKeyForClinicalExpand(ConditionDefinitionBatchItemQueryModel def) {
        if (def == null) {
            return null;
        }
        String source = def.getSourceEnum();
        // Gate：国临自身不展开
        if (DoctorStationDiagnosisConstants.SOURCE_CLINICAL_V2.equals(source)) {
            return null;
        }
        // Gate：中医目录不展开
        if (source != null && DoctorStationDiagnosisConstants.CONDITION_SOURCE_CODES_TCM.contains(source)) {
            return null;
        }
        String ybNo = StringUtils.trimToNull(def.getYbNo());
        if (ybNo != null) {
            return ybNo;
        }
        return StringUtils.trimToNull(def.getConditionCode());
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
        target.setEncounterDiagnosisId(source.getEncounterDiagnosisId());
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
     * 构建西医入院诊断就诊关联 upsert 项。
     *
     * @param encounterId 就诊主键
     * @param dx          入院诊断项
     * @param rank        排序序号
     * @return 就诊诊断关联命令项
     */
    private UpsertEncounterDiagnosisAssociationCommand.UpsertEncounterDiagnosisAssociationItemCommand newAdmissionDiagnosisAssocItem(
        Long encounterId,
        AdmissionConditionItem dx,
        int rank) {
        UpsertEncounterDiagnosisAssociationCommand.UpsertEncounterDiagnosisAssociationItemCommand assoc =
            new UpsertEncounterDiagnosisAssociationCommand.UpsertEncounterDiagnosisAssociationItemCommand();
        assoc.setEncounterId(encounterId);
        assoc.setEncounterDiagnosisId(dx.getEncounterDiagnosisId());
        assoc.setConditionDefinitionId(dx.getConditionDefinitionId());
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
     * @param dx   入院诊断项
     * @param rank 排序序号
     * @return 中医就诊诊断关联命令项
     */
    private BatchUpsertEncounterDiagnosisAssociationCommand.DiagnosisItemCommand newAdmissionTcmAssocItem(
        AdmissionConditionItem dx,
        int rank) {
        BatchUpsertEncounterDiagnosisAssociationCommand.DiagnosisItemCommand assoc =
            new BatchUpsertEncounterDiagnosisAssociationCommand.DiagnosisItemCommand();
        assoc.setEncounterDiagnosisId(dx.getEncounterDiagnosisId());
        assoc.setConditionDefinitionId(dx.getConditionDefinitionId());
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
