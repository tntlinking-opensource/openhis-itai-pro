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

package com.openhis.health.application.emergency.doctorstation.diagnosis.service;

import com.openhis.health.application.common.validator.CurrentOperatorResolver;
import com.openhis.health.application.emergency.doctorstation.diagnosis.assembler.EmgEncounterDiagnosisListAssembler;
import com.openhis.health.application.emergency.doctorstation.support.EmergencyEncounterContextSupport;
import com.openhis.health.application.outpatient.doctorstation.diagnosis.dto.CommonDiagnosisRowDto;
import com.openhis.health.application.outpatient.doctorstation.diagnosis.dto.EncounterDiagnosisRowDto;
import com.openhis.health.application.outpatient.doctorstation.diagnosis.mapper.DoctorStationDiagnosisMapper;
import com.openhis.health.application.outpatient.doctorstation.diagnosis.request.DeleteEncounterDiagnosisRequest;
import com.openhis.health.application.outpatient.doctorstation.diagnosis.request.GetCommonDiagnosesRequest;
import com.openhis.health.application.outpatient.doctorstation.diagnosis.request.GetEncounterDiagnosisRequest;
import com.openhis.health.application.outpatient.doctorstation.diagnosis.request.PageConditionDefinitionRequest;
import com.openhis.health.application.outpatient.doctorstation.diagnosis.request.SaveEncounterDiagnosisRequest;
import com.openhis.health.application.outpatient.doctorstation.diagnosis.response.CommonDiagnosesResponse;
import com.openhis.health.application.outpatient.doctorstation.diagnosis.response.ConditionDefinitionPageResponse;
import com.openhis.health.application.outpatient.doctorstation.diagnosis.response.ConditionDefinitionRowResponse;
import com.openhis.health.application.outpatient.doctorstation.diagnosis.response.DeleteEncounterDiagnosisResponse;
import com.openhis.health.application.outpatient.doctorstation.diagnosis.response.EncounterDiagnosisResponse;
import com.openhis.health.application.outpatient.doctorstation.diagnosis.response.SaveEncounterDiagnosisResponse;
import com.openhis.health.domain.administration.encounter.command.RemoveEncounterDiagnosisAssociationCommand;
import com.openhis.health.domain.administration.encounter.service.EncounterDomainService;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.administration.practitioner.service.PractitionerQueryService;
import com.openhis.health.domain.clinical.condition.command.DelEncounterDiagnosisCommand;
import com.openhis.health.domain.clinical.condition.command.SaveDoctorDiagnosisCommand;
import com.openhis.health.domain.clinical.condition.entity.Condition;
import com.openhis.health.domain.clinical.condition.repository.ConditionRepository;
import com.openhis.health.domain.clinical.condition.service.ConditionDomainService;
import com.openhis.health.domain.clinical.conditiondefinition.queries.model.DoctorStationConditionDefinitionRowModel;
import com.openhis.health.domain.clinical.conditiondefinition.service.ConditionDefinitionQueryService;
import com.openhis.health.domain.custom.conditiontemplate.queries.model.ConditionTemplateQueryModel;
import com.openhis.health.domain.custom.conditiontemplate.service.ConditionTemplateQueryService;
import com.openhis.health.domain.share.constants.DoctorStationDiagnosisConstants;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.TerminologyCodeSystemNameConstants;
import com.openhis.health.domain.share.enums.ConditionTemplateBinding;
import com.openhis.health.domain.terminology.service.CodeSystemQueryService;
import com.openhis.whale.core.application.dto.OptionDto;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.infrastructure.security.ICurrentUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 急诊医生站诊断录入：就诊诊断查询与维护、常用诊断、诊断定义分页检索。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmergencyDoctorStationDiagnosisAppService {

    private final EmergencyEncounterContextSupport encounterContextSupport;
    private final EmgEncounterDiagnosisListAssembler encounterDiagnosisListAssembler;
    private final ConditionDefinitionQueryService conditionDefinitionQueryService;
    private final ConditionTemplateQueryService conditionTemplateQueryService;
    private final ConditionRepository conditionRepository;
    private final ConditionDomainService conditionDomainService;
    private final EncounterDomainService encounterDomainService;
    private final PractitionerQueryService practitionerQueryService;
    private final ICurrentUser currentUser;
    private final CurrentOperatorResolver currentOperatorResolver;
    private final CodeSystemQueryService codeSystemQueryService;
    private final DoctorStationDiagnosisMapper doctorStationDiagnosisMapper;

    public EncounterDiagnosisResponse getEmergencyEncounterDiagnosis(GetEncounterDiagnosisRequest request) {
        encounterContextSupport.requireEmergencyEncounter(request.getEncounterId(), request.getPatientId());
        List<EncounterDiagnosisRowDto> rows =
            encounterDiagnosisListAssembler.assembleEncounterDiagnosisRows(request.getEncounterId());
        return doctorStationDiagnosisMapper.toEncounterDiagnosisResponse(rows);
    }

    public CommonDiagnosesResponse getEmergencyCommonDiagnoses(GetCommonDiagnosesRequest request) {
        Long userId = currentUser.getCurrentUserId();
        Long orgId = currentUser.getCurrentOrganizationUnitId();
        Practitioner practitioner = userId == null ? null : practitionerQueryService.findByUserId(userId);
        Long practitionerId = practitioner == null ? null : practitioner.getId();
        Map<String, String> sourceLabels = buildSourceLabelMap();
        List<CommonDiagnosisRowDto> history = buildPatientHistoryCommonRows(request.getPatientId(), sourceLabels);
        List<CommonDiagnosisRowDto> doctorRows = new ArrayList<>();
        if (practitionerId != null) {
            doctorRows = mapTemplatesToCommonRows(
                conditionTemplateQueryService.listByOwnerAndType(
                    practitionerId, ConditionTemplateBinding.PERSONAL.getCode()),
                sourceLabels);
        }
        List<CommonDiagnosisRowDto> personalRows = new ArrayList<>();
        if (userId != null) {
            personalRows = mapTemplatesToCommonRows(
                conditionTemplateQueryService.listByOwnerAndType(userId, ConditionTemplateBinding.PERSONAL.getCode()),
                sourceLabels);
        }
        List<CommonDiagnosisRowDto> orgRows = new ArrayList<>();
        if (orgId != null) {
            orgRows = mapTemplatesToCommonRows(
                conditionTemplateQueryService.listByOwnerAndType(orgId, ConditionTemplateBinding.DEPARTMENT.getCode()),
                sourceLabels);
        }
        return doctorStationDiagnosisMapper.toCommonDiagnosesResponse(history, doctorRows, personalRows, orgRows);
    }

    public ConditionDefinitionPageResponse pageEmergencyConditionDefinition(PageConditionDefinitionRequest request) {
        if (StringUtils.isNotBlank(request.getDiagnosisKindCode())) {
            String code = request.getDiagnosisKindCode().trim();
            if (!DoctorStationDiagnosisConstants.DIAGNOSIS_KIND_FILTER_WESTERN.equals(code)
                && !DoctorStationDiagnosisConstants.DIAGNOSIS_KIND_FILTER_TCM.equals(code)) {
                throw new BusinessException(
                    ErrorCodeConstants.CONDITION_DEFINITION_INVALID_ARGUMENT, "诊断中西医维度筛选参数非法");
            }
        }
        List<String> sourceIn = doctorStationDiagnosisMapper.parseDiagnosisKindSourceFilter(request.getDiagnosisKindCode());
        Page<DoctorStationConditionDefinitionRowModel> page =
            conditionDefinitionQueryService.pageActiveDoctorStationDefinitions(
                request.getKeyword(), sourceIn, request.getPageNo(), request.getPageSize());
        Map<String, String> sourceLabels = buildSourceLabelMap();
        List<ConditionDefinitionRowResponse> records = new ArrayList<>();
        for (DoctorStationConditionDefinitionRowModel model : page.getContent()) {
            records.add(doctorStationDiagnosisMapper.toConditionDefinitionRowResponse(model, sourceLabels));
        }
        return doctorStationDiagnosisMapper.toConditionDefinitionPageResponse(page, records);
    }

    public SaveEncounterDiagnosisResponse saveEmergencyEncounterDiagnosis(SaveEncounterDiagnosisRequest request) {
        doctorStationDiagnosisMapper.validateSaveEncounterDiagnosisRequest(request);
        encounterContextSupport.requireEmergencyEncounter(request.getEncounterId(), request.getPatientId());
        Long operatorId =
            currentOperatorResolver.requireOperatorId(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "请先登录后再操作");
        try {
            SaveDoctorDiagnosisCommand saveCmd =
                doctorStationDiagnosisMapper.toSaveDoctorDiagnosisCommand(request, operatorId);
            List<Long> savedIds = conditionDomainService.saveDoctorDiagnosis(saveCmd);
            var assocCmd = doctorStationDiagnosisMapper.toBatchUpsertEncounterDiagnosisAssociationCommand(
                request, savedIds, operatorId);
            encounterDomainService.batchUpsertEncounterDiagnosisAssociation(assocCmd);
            return doctorStationDiagnosisMapper.toSaveEncounterDiagnosisResponse(savedIds.size(), savedIds);
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.warn("saveEmergencyEncounterDiagnosis failed encounterId={}", request.getEncounterId(), ex);
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_RULE_CONFLICT, "保存失败，请稍后重试或联系管理员");
        }
    }

    public DeleteEncounterDiagnosisResponse deleteEmergencyEncounterDiagnosis(DeleteEncounterDiagnosisRequest request) {
        doctorStationDiagnosisMapper.validateDeleteEncounterDiagnosisRequest(
            request.getConditionId(), request.getEncounterId());
        encounterContextSupport.requireEmergencyEncounter(request.getEncounterId(), request.getPatientId());
        int adviceCnt = conditionRepository.countAdviceRefByConditionId(request.getConditionId());
        if (adviceCnt > 0) {
            throw new BusinessException(ErrorCodeConstants.CONDITION_RULE_CONFLICT, "先撤销或调整相关医嘱后再删");
        }
        Long operatorId =
            currentOperatorResolver.requireOperatorId(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "请先登录后再操作");
        try {
            RemoveEncounterDiagnosisAssociationCommand rmCmd = new RemoveEncounterDiagnosisAssociationCommand();
            var item = new RemoveEncounterDiagnosisAssociationCommand.RemoveEncounterDiagnosisAssociationItemCommand();
            item.setEncounterId(request.getEncounterId());
            item.setConditionId(request.getConditionId());
            item.setRemoveReason("EMERGENCY_DOCTOR_STATION_DELETE");
            rmCmd.setItem(item);
            rmCmd.setOperatorId(operatorId);
            encounterDomainService.removeEncounterDiagnosisAssociation(rmCmd);
            DelEncounterDiagnosisCommand delCmd = new DelEncounterDiagnosisCommand();
            delCmd.setConditionId(request.getConditionId());
            delCmd.setOperatorId(operatorId);
            delCmd.setReason("EMERGENCY_DOCTOR_STATION_DELETE");
            conditionDomainService.delEncounterDiagnosis(delCmd);
        } catch (BusinessException ex) {
            if (ErrorCodeConstants.CONDITION_NOT_FOUND.equals(ex.getErrorCode())
                || ErrorCodeConstants.ENCOUNTER_NOT_FOUND.equals(ex.getErrorCode())) {
                return doctorStationDiagnosisMapper.toDeleteEncounterDiagnosisResponse(true, "删除成功");
            }
            throw ex;
        } catch (RuntimeException ex) {
            log.warn("deleteEmergencyEncounterDiagnosis failed conditionId={}", request.getConditionId(), ex);
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_RULE_CONFLICT, "删除失败，请稍后重试或联系管理员");
        }
        return doctorStationDiagnosisMapper.toDeleteEncounterDiagnosisResponse(true, "删除成功");
    }

    private Map<String, String> buildSourceLabelMap() {
        Map<String, String> map = new HashMap<>();
        List<OptionDto> options =
            codeSystemQueryService.getOptionListByCodeSystemName(TerminologyCodeSystemNameConstants.CONDITION_DEFINITION_SOURCE);
        if (options == null) {
            return map;
        }
        for (OptionDto option : options) {
            if (option.getValue() != null) {
                map.put(option.getValue(), option.getLabel());
            }
        }
        return map;
    }

    private List<CommonDiagnosisRowDto> mapTemplatesToCommonRows(
        List<ConditionTemplateQueryModel> templates,
        Map<String, String> sourceLabels) {
        List<CommonDiagnosisRowDto> rows = new ArrayList<>();
        if (templates == null || templates.isEmpty()) {
            return rows;
        }
        List<Long> ids = new ArrayList<>();
        for (ConditionTemplateQueryModel template : templates) {
            if (template.getDefinitionId() != null) {
                ids.add(template.getDefinitionId());
            }
        }
        List<DoctorStationConditionDefinitionRowModel> defs =
            conditionDefinitionQueryService.listActiveDoctorStationRowsByIds(ids);
        Map<Long, DoctorStationConditionDefinitionRowModel> byId = new HashMap<>();
        for (DoctorStationConditionDefinitionRowModel def : defs) {
            byId.put(def.getId(), def);
        }
        for (ConditionTemplateQueryModel template : templates) {
            DoctorStationConditionDefinitionRowModel def = byId.get(template.getDefinitionId());
            if (def == null) {
                continue;
            }
            CommonDiagnosisRowDto row = doctorStationDiagnosisMapper.toCommonDiagnosisRowDto(def, sourceLabels);
            if (row != null) {
                rows.add(row);
            }
        }
        return rows;
    }

    private List<CommonDiagnosisRowDto> buildPatientHistoryCommonRows(Long patientId, Map<String, String> sourceLabels) {
        List<CommonDiagnosisRowDto> rows = new ArrayList<>();
        List<Condition> conditions = conditionRepository.findByPatientId(patientId);
        Set<Long> seenDef = new LinkedHashSet<>();
        List<Long> orderedIds = new ArrayList<>();
        for (Condition condition : conditions) {
            if (condition.getDefinitionId() == null) {
                continue;
            }
            if (seenDef.add(condition.getDefinitionId())) {
                orderedIds.add(condition.getDefinitionId());
            }
        }
        List<DoctorStationConditionDefinitionRowModel> defs =
            conditionDefinitionQueryService.listActiveDoctorStationRowsByIds(orderedIds);
        Map<Long, DoctorStationConditionDefinitionRowModel> byId = new HashMap<>();
        for (DoctorStationConditionDefinitionRowModel def : defs) {
            byId.put(def.getId(), def);
        }
        for (Long definitionId : orderedIds) {
            DoctorStationConditionDefinitionRowModel def = byId.get(definitionId);
            if (def == null) {
                continue;
            }
            rows.add(doctorStationDiagnosisMapper.toCommonDiagnosisRowDto(def, sourceLabels));
        }
        return rows;
    }
}
