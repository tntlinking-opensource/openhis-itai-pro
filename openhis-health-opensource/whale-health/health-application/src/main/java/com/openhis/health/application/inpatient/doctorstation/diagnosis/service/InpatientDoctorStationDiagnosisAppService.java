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

package com.openhis.health.application.inpatient.doctorstation.diagnosis.service;

import com.openhis.health.module.common.validator.CurrentManagedOrganizationIdsResolver;
import com.openhis.health.module.common.validator.CurrentPractitionerResolver;
import com.openhis.health.application.inpatient.doctorstation.diagnosis.dto.CommonDiagnosisRowDto;
import com.openhis.health.application.inpatient.doctorstation.diagnosis.dto.EncounterDiagnosisRowDto;
import com.openhis.health.application.inpatient.doctorstation.diagnosis.mapper.InpatientDoctorStationDiagnosisMapper;
import com.openhis.health.application.inpatient.doctorstation.diagnosis.request.DeleteEncounterDiagnosisRequest;
import com.openhis.health.application.inpatient.doctorstation.diagnosis.request.GetCommonDiagnosesRequest;
import com.openhis.health.application.inpatient.doctorstation.diagnosis.request.GetEncounterDiagnosisRequest;
import com.openhis.health.application.inpatient.doctorstation.diagnosis.request.PageConditionDefinitionRequest;
import com.openhis.health.application.inpatient.doctorstation.diagnosis.request.SaveEncounterDiagnosisRequest;
import com.openhis.health.application.inpatient.doctorstation.diagnosis.response.CommonDiagnosesResponse;
import com.openhis.health.application.inpatient.doctorstation.diagnosis.response.ConditionDefinitionPageResponse;
import com.openhis.health.application.inpatient.doctorstation.diagnosis.response.ConditionDefinitionRowResponse;
import com.openhis.health.application.inpatient.doctorstation.diagnosis.response.DeleteEncounterDiagnosisResponse;
import com.openhis.health.application.inpatient.doctorstation.diagnosis.response.EncounterDiagnosisResponse;
import com.openhis.health.application.inpatient.doctorstation.diagnosis.response.SaveEncounterDiagnosisResponse;
import com.openhis.health.domain.administration.encounter.command.BatchUpsertEncounterDiagnosisAssociationCommand;
import com.openhis.health.domain.administration.encounter.command.RemoveEncounterDiagnosisAssociationCommand;
import com.openhis.health.domain.administration.encounter.service.EncounterDomainService;
import com.openhis.health.domain.clinical.condition.command.DelEncounterDiagnosisCommand;
import com.openhis.health.domain.clinical.condition.command.SaveDoctorDiagnosisCommand;
import com.openhis.health.domain.clinical.condition.entity.Condition;
import com.openhis.health.domain.clinical.condition.service.ConditionDomainService;
import com.openhis.health.domain.clinical.condition.service.ConditionQueryService;
import com.openhis.health.domain.clinical.conditiondefinition.queries.model.DoctorStationConditionDefinitionRowModel;
import com.openhis.health.domain.clinical.conditiondefinition.service.ConditionDefinitionQueryService;
import com.openhis.health.domain.custom.conditiontemplate.queries.model.ConditionTemplateQueryModel;
import com.openhis.health.domain.custom.conditiontemplate.service.ConditionTemplateQueryService;
import com.openhis.health.module.querysupport.encounter.EncounterContextFacade;
import com.openhis.health.module.shared.diagnosis.EncounterDiagnosisSnapshotAssembler;
import com.openhis.health.domain.administration.encounter.queries.model.InpatientEncounterContextQueryModel;
import com.openhis.health.domain.share.constants.DoctorStationDiagnosisConstants;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.InpatientDoctorStationDiagnosisConstants;
import com.openhis.health.domain.share.constants.TerminologyCodeSystemNameConstants;
import com.openhis.health.domain.share.enums.ConditionTemplateBinding;
import com.openhis.health.domain.share.enums.IptPatientDiseaseDiagType;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.health.domain.terminology.service.CodeSystemQueryService;
import com.openhis.health.integration.emr.EmrAdmissionDiagnosisSyncExtension;
import com.openhis.whale.core.application.dto.OptionDto;
import com.openhis.whale.core.application.session.CurrentOperatorResolver;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 住院医生站诊断录入应用编排。
 *
 * <p>读路径：从住院就诊快照解析当前诊断；分页检索诊断定义与常用模板。</p>
 * <p>写路径：批量保存/删除诊断，同步更新就诊诊断快照与领域 Condition。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InpatientDoctorStationDiagnosisAppService {

    private final EncounterContextFacade encounterContextFacade;
    private final EncounterDiagnosisSnapshotAssembler encounterDiagnosisSnapshotAssembler;
    private final ConditionDefinitionQueryService conditionDefinitionQueryService;
    private final ConditionTemplateQueryService conditionTemplateQueryService;
    private final ConditionQueryService conditionQueryService;
    private final ConditionDomainService conditionDomainService;
    private final EncounterDomainService encounterDomainService;
    private final CurrentManagedOrganizationIdsResolver managedOrganizationIdsResolver;
    private final CurrentPractitionerResolver currentPractitionerResolver;
    /** 登录用户个人模板读路径须用 {@code whale_users.id}。 */
    private final CurrentOperatorResolver currentOperatorResolver;
    private final CodeSystemQueryService codeSystemQueryService;
    private final InpatientDoctorStationDiagnosisMapper inpatientDoctorStationDiagnosisMapper;
    private final EmrAdmissionDiagnosisSyncExtension emrAdmissionDiagnosisSyncExtension;

    /**
     *
     * @param request 就诊与患者标识
     * @return 诊断行列表与 totalCount（与列表长度一致）
     * @throws BusinessException 快照缺失、患者不一致或 JSON 解析失败时抛出
     */
    public EncounterDiagnosisResponse getEncounterDiagnosis(GetEncounterDiagnosisRequest request) {
        InpatientEncounterContextQueryModel snapshot =
            encounterContextFacade.mustGetInpatientContextByEncounterId(request.getEncounterId());
        inpatientDoctorStationDiagnosisMapper.validateInpatientEncounterPatientGate(snapshot, request.getPatientId());
        inpatientDoctorStationDiagnosisMapper.validateInpatientEncounterClassImp(snapshot);
        // 实时扫表拼装诊断行，禁止仅依赖上下文内 diagnosisJson 中转（读模型废止后 §四）。
        List<EncounterDiagnosisRowDto> rows = inpatientDoctorStationDiagnosisMapper.parseDiagnosisJson(
            encounterDiagnosisSnapshotAssembler.buildSnapshot(request.getEncounterId()).diagnosisJson());
        return inpatientDoctorStationDiagnosisMapper.toEncounterDiagnosisResponse(rows);
    }

    /**
     *
     * @param request 患者标识（历史诊断）与登录上下文（模板）
     * @return 四类常用诊断树数据（患者历史/医师常用/个人/科室）
     */
    public CommonDiagnosesResponse getConditionDefinitionBusinessClass(GetCommonDiagnosesRequest request) {
        Long userId = currentOperatorResolver.findOperatorIdOrNull();
        Long orgId = managedOrganizationIdsResolver.resolveOrganizationUnitIdForQuery();
        Long practitionerId = currentPractitionerResolver.findPractitionerIdOrNull();
        Map<String, String> sourceLabels = this.buildSourceLabelMap();
        List<CommonDiagnosisRowDto> history = this.buildPatientHistoryCommonRows(request.getPatientId(), sourceLabels);
        List<CommonDiagnosisRowDto> doctorRows = new ArrayList<>();
        if (practitionerId != null) {
            List<ConditionTemplateQueryModel> doctorTemplates = conditionTemplateQueryService.listByOwnerAndType(
                practitionerId, ConditionTemplateBinding.PERSONAL.getCode());
            doctorRows = this.mapTemplatesToCommonRows(doctorTemplates, sourceLabels);
        }
        List<CommonDiagnosisRowDto> personalRows = new ArrayList<>();
        if (userId != null) {
            List<ConditionTemplateQueryModel> personalTemplates = conditionTemplateQueryService.listByOwnerAndType(
                userId, ConditionTemplateBinding.PERSONAL.getCode());
            personalRows = this.mapTemplatesToCommonRows(personalTemplates, sourceLabels);
        }
        List<CommonDiagnosisRowDto> orgRows = new ArrayList<>();
        if (orgId != null) {
            List<ConditionTemplateQueryModel> orgTemplates = conditionTemplateQueryService.listByOwnerAndType(
                orgId, ConditionTemplateBinding.DEPARTMENT.getCode());
            orgRows = this.mapTemplatesToCommonRows(orgTemplates, sourceLabels);
        }
        return inpatientDoctorStationDiagnosisMapper.toCommonDiagnosesResponse(
            history, doctorRows, personalRows, orgRows,
            EnumUtil.toOptions(IptPatientDiseaseDiagType.class));
    }

    /**
     *
     * @param request 关键字、中西医筛选与分页
     * @return 已发布定义分页行
     * @throws BusinessException 中西医筛选码非法时抛出
     */
    public ConditionDefinitionPageResponse getConditionDefinitionMetadata(PageConditionDefinitionRequest request) {
        if (StringUtils.isNotBlank(request.getDiagnosisKindCode())) {
            String code = request.getDiagnosisKindCode().trim();
            if (!DoctorStationDiagnosisConstants.DIAGNOSIS_KIND_FILTER_WESTERN.equals(code)
                && !DoctorStationDiagnosisConstants.DIAGNOSIS_KIND_FILTER_TCM.equals(code)) {
                throw new BusinessException(
                    ErrorCodeConstants.CONDITION_DEFINITION_INVALID_ARGUMENT,
                    "诊断中西医维度筛选参数非法");
            }
        }
        List<String> sourceIn =
            inpatientDoctorStationDiagnosisMapper.parseDiagnosisKindSourceFilter(request.getDiagnosisKindCode());
        Page<DoctorStationConditionDefinitionRowModel> page =
            conditionDefinitionQueryService.pageActiveDoctorStationDefinitions(
                request.getKeyword(),
                sourceIn,
                request.getPageNo(),
                request.getPageSize());
        Map<String, String> sourceLabels = this.buildSourceLabelMap();
        List<ConditionDefinitionRowResponse> records = new ArrayList<>();
        for (DoctorStationConditionDefinitionRowModel model : page.getContent()) {
            ConditionDefinitionRowResponse definitionRow =
                inpatientDoctorStationDiagnosisMapper.toConditionDefinitionRowResponse(model, sourceLabels);
            records.add(definitionRow);
        }
        return inpatientDoctorStationDiagnosisMapper.toConditionDefinitionPageResponse(page, records);
    }

    /**
     *
     * @param request 全量诊断批次（含主诊唯一、排序唯一等应用层校验）
     * @return 保存条数与 conditionId 列表
     * @throws BusinessException 门控失败或领域规则冲突时抛出
     */
    public SaveEncounterDiagnosisResponse saveDoctorDiagnosis(SaveEncounterDiagnosisRequest request) {
        inpatientDoctorStationDiagnosisMapper.validateSaveEncounterDiagnosisRequest(request);
        inpatientDoctorStationDiagnosisMapper.validateIptDiseaseTypeCodesInBatch(request);
        InpatientEncounterContextQueryModel snapshot =
            encounterContextFacade.mustGetInpatientContextByEncounterId(request.getEncounterId());
        inpatientDoctorStationDiagnosisMapper.validateInpatientEncounterPatientGate(snapshot, request.getPatientId());
        inpatientDoctorStationDiagnosisMapper.validateInpatientEncounterClassImp(snapshot);
        inpatientDoctorStationDiagnosisMapper.validateInpatientEncounterWritableGate(snapshot);
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT,
            "请先选择患者");
        SaveEncounterDiagnosisResponse response;
        try {
            SaveDoctorDiagnosisCommand saveCmd =
                inpatientDoctorStationDiagnosisMapper.toSaveDoctorDiagnosisCommand(request, operatorId);
            List<Long> savedIds = conditionDomainService.saveDoctorDiagnosis(saveCmd);
            BatchUpsertEncounterDiagnosisAssociationCommand assocCmd =
                inpatientDoctorStationDiagnosisMapper.toBatchUpsertEncounterDiagnosisAssociationCommand(
                    request, savedIds, operatorId);
            encounterDomainService.batchUpsertEncounterDiagnosisAssociation(assocCmd);
            response = inpatientDoctorStationDiagnosisMapper.toSaveEncounterDiagnosisResponse(savedIds.size(), savedIds);
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.warn("saveDoctorDiagnosis failed encounterId={}", request.getEncounterId(), ex);
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_RULE_CONFLICT, "保存失败，请稍后重试或联系管理员");
        }
        syncAdmissionDiagnosisToEmrIfEnabled(request.getEncounterId(), request.getPatientId());
        return response;
    }

    /**
     *
     * @param request 待删 conditionId 与就诊上下文
     * @return 删除结果与提示文案（幂等：已删仍返回成功）
     * @throws BusinessException 仍有医嘱引用或门控失败时抛出
     */
    public DeleteEncounterDiagnosisResponse delEncounterDiagnosis(DeleteEncounterDiagnosisRequest request) {
        inpatientDoctorStationDiagnosisMapper.validateDeleteEncounterDiagnosisRequest(
            request.getConditionId(),
            request.getEncounterId());
        InpatientEncounterContextQueryModel snapshot =
            encounterContextFacade.mustGetInpatientContextByEncounterId(request.getEncounterId());
        Long gatePatientId = request.getPatientId();
        if (gatePatientId == null) {
            gatePatientId = snapshot.getPatientId();
        }
        inpatientDoctorStationDiagnosisMapper.validateInpatientEncounterPatientGate(snapshot, gatePatientId);
        inpatientDoctorStationDiagnosisMapper.validateInpatientEncounterClassImp(snapshot);
        inpatientDoctorStationDiagnosisMapper.validateInpatientEncounterWritableGate(snapshot);
        int adviceCnt = conditionQueryService.countAdviceRefByConditionId(request.getConditionId());
        if (adviceCnt > 0) {
            throw new BusinessException(ErrorCodeConstants.CONDITION_RULE_CONFLICT, "先处理相关医嘱后再删");
        }
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT,
            "请先选择患者");
        try {
            RemoveEncounterDiagnosisAssociationCommand rmCmd = new RemoveEncounterDiagnosisAssociationCommand();
            RemoveEncounterDiagnosisAssociationCommand.RemoveEncounterDiagnosisAssociationItemCommand item =
                new RemoveEncounterDiagnosisAssociationCommand.RemoveEncounterDiagnosisAssociationItemCommand();
            item.setEncounterId(request.getEncounterId());
            item.setConditionId(request.getConditionId());
            item.setRemoveReason(InpatientDoctorStationDiagnosisConstants.DELETE_REASON_INPATIENT_DOCTOR_STATION);
            rmCmd.setItem(item);
            rmCmd.setOperatorId(operatorId);
            encounterDomainService.removeEncounterDiagnosisAssociation(rmCmd);
            DelEncounterDiagnosisCommand delCmd = new DelEncounterDiagnosisCommand();
            delCmd.setConditionId(request.getConditionId());
            delCmd.setOperatorId(operatorId);
            delCmd.setReason(InpatientDoctorStationDiagnosisConstants.DELETE_REASON_INPATIENT_DOCTOR_STATION);
            conditionDomainService.delEncounterDiagnosis(delCmd);
        } catch (BusinessException ex) {
            // 幂等删除：事实或关联已不存在时仍返回成功文案，避免重复点击报错。
            if (ErrorCodeConstants.CONDITION_NOT_FOUND.equals(ex.getErrorCode())) {
                return inpatientDoctorStationDiagnosisMapper.toDeleteEncounterDiagnosisResponse(true, "删除成功");
            }
            if (ErrorCodeConstants.ENCOUNTER_NOT_FOUND.equals(ex.getErrorCode())) {
                return inpatientDoctorStationDiagnosisMapper.toDeleteEncounterDiagnosisResponse(true, "删除成功");
            }
            throw ex;
        } catch (RuntimeException ex) {
            log.warn("delEncounterDiagnosis failed conditionId={}", request.getConditionId(), ex);
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_RULE_CONFLICT, "删除失败，请稍后重试或联系管理员");
        }
        syncAdmissionDiagnosisToEmrIfEnabled(request.getEncounterId(), gatePatientId);
        return inpatientDoctorStationDiagnosisMapper.toDeleteEncounterDiagnosisResponse(true, "删除成功");
    }

    private void syncAdmissionDiagnosisToEmrIfEnabled(Long encounterId, Long patientId) {
        if (!emrAdmissionDiagnosisSyncExtension.isEnabled()) {
            return;
        }
        try {
            emrAdmissionDiagnosisSyncExtension.syncAfterInpatientDiagnosisChanged(encounterId, patientId);
        } catch (RuntimeException ex) {
            log.warn("EMR admission diagnosis structured sync failed encounterId={}", encounterId, ex);
        }
    }

    /**
     * 构建诊断目录来源码 → 展示文案映射（术语系统 CONDITION_DEFINITION_SOURCE）。
     *
     * @return 来源 code 到中文标签；术语缺失时返回空 Map
     */
    private Map<String, String> buildSourceLabelMap() {
        Map<String, String> map = new HashMap<>();
        List<OptionDto> options = codeSystemQueryService.getOptionListByCodeSystemName(
            TerminologyCodeSystemNameConstants.CONDITION_DEFINITION_SOURCE);
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

    /**
     * 将模板行解析为常用诊断候选（批量装载定义元数据后去重装配）。
     *
     * @param templates    模板只读行
     * @param sourceLabels 目录来源展示映射
     * @return 常用诊断候选行；模板为空时返回空列表
     */
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
            rows.add(inpatientDoctorStationDiagnosisMapper.toCommonDiagnosisRowDto(def, sourceLabels));
        }
        return rows;
    }

    /**
     * 患者历史诊断事实去重后，按定义 id 顺序装配常用诊断候选。
     *
     * @param patientId    患者主键
     * @param sourceLabels 目录来源展示映射
     * @return 去重后的历史诊断候选；无历史时返回空列表
     */
    private List<CommonDiagnosisRowDto> buildPatientHistoryCommonRows(Long patientId, Map<String, String> sourceLabels) {
        List<CommonDiagnosisRowDto> rows = new ArrayList<>();
        List<Condition> conditions = conditionQueryService.findByPatientId(patientId);
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
            rows.add(inpatientDoctorStationDiagnosisMapper.toCommonDiagnosisRowDto(def, sourceLabels));
        }
        return rows;
    }
}
