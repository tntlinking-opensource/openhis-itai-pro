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

package com.openhis.health.application.inpatient.financial.registration.support;

import com.openhis.health.domain.administration.account.service.AccountDomainService;
import com.openhis.health.domain.administration.encounter.command.AdmissionConditionItem;
import com.openhis.health.domain.administration.encounter.command.RegisterInpatientEncounterCommand;
import com.openhis.health.domain.administration.encounter.command.UpsertEncounterDiagnosisAssociationCommand;
import com.openhis.health.domain.administration.encounter.queries.model.EncounterDetailQueryModel;
import com.openhis.health.domain.administration.encounter.queries.model.EncounterDiagnosisQueryModel;
import com.openhis.health.domain.administration.encounter.service.EncounterDomainService;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.domain.clinical.condition.command.SaveDoctorDiagnosisCommand;
import com.openhis.health.domain.clinical.condition.service.ConditionDomainService;
import com.openhis.health.domain.clinical.condition.service.ConditionQueryService;
import com.openhis.health.domain.share.constants.DoctorStationDiagnosisConstants;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.enums.IptPatientDiseaseDiagType;
import com.openhis.health.domain.share.enums.YbMedType;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
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
public class EncounterInpatientAdmissionWriteSupport {

    private final AccountDomainService accountDomainService;
    private final ConditionDomainService conditionDomainService;
    private final ConditionQueryService conditionQueryService;
    private final EncounterDomainService encounterDomainService;
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
        List<UpsertEncounterDiagnosisAssociationCommand.UpsertEncounterDiagnosisAssociationItemCommand> assocItems =
            new ArrayList<>();
        int rank = 1;
        for (AdmissionConditionItem dx : dxItems) {
            Long conditionId = dx.getConditionId();
            if (conditionId == null) {
                conditionId = this.resolveConditionIdForAdmissionDxItem(
                    patientId, encounterId, dx, operatorId);
            }
            assocItems.add(this.newAdmissionDiagnosisAssocItem(encounterId, dx, conditionId, rank));
            rank++;
        }
        UpsertEncounterDiagnosisAssociationCommand assocCmd = new UpsertEncounterDiagnosisAssociationCommand();
        assocCmd.setOperatorId(operatorId);
        assocCmd.setItems(assocItems);
        encounterDomainService.upsertEncounterDiagnosisAssociation(assocCmd);
    }

    /**
     * 有申请登记（{@code PLANNED→REGISTERED}）时的入院诊断策略：申请已落库则禁止登记侧再提交列表。
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

    private Long resolvePatientId(RegisterInpatientEncounterCommand command, Long encounterId) {
        if (command.getPatientId() != null) {
            return command.getPatientId();
        }
        EncounterDetailQueryModel encounter = encounterQueryService.mustGetEncounter(encounterId);
        return encounter.getPatientId();
    }

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
        return assoc;
    }
}
