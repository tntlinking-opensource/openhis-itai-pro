/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.doctorstation.diagnosis.service;

import com.openhis.health.application.shared.validator.CurrentManagedOrganizationIdsResolver;
import com.openhis.health.application.shared.validator.CurrentPractitionerResolver;
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
import com.openhis.health.application.inpatient.doctorstation.encountermanage.support.InpatientDoctorStationEncounterVisibilityResolver;
import com.openhis.health.application.shared.diagnosis.EncounterDiagnosisSnapshotAssembler;
import com.openhis.health.domain.administration.encounter.queries.model.InpatientEncounterContextQueryModel;
import com.openhis.health.domain.share.constants.DoctorStationDiagnosisConstants;
import com.openhis.health.domain.share.constants.InpatientDoctorStationConstants;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.InpatientDoctorStationDiagnosisConstants;
import com.openhis.health.domain.share.constants.TerminologyCodeSystemNameConstants;
import com.openhis.health.domain.share.enums.ConditionTemplateBinding;
import com.openhis.health.domain.share.enums.IptPatientDiseaseDiagType;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.health.domain.terminology.service.CodeSystemQueryService;
import com.openhis.health.infrastructure.integration.emr.EmrAdmissionDiagnosisSyncExtension;
import com.openhis.whale.core.application.dto.OptionDto;
import com.openhis.whale.core.application.session.CurrentOperatorResolver;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.openhis.whale.core.application.util.StringUtils;
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

    /** 医生站可见性 Gate（BR-IP-DOR-010）。 */
    private final InpatientDoctorStationEncounterVisibilityResolver inpatientDoctorStationEncounterVisibilityResolver;
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
     * 加载住院就诊当前诊断列表（实时扫表，禁止仅依赖上下文 JSON）。
     *
     * @param request 就诊与患者标识
     * @return 诊断行列表与 totalCount（与列表长度一致）
     * @throws BusinessException 快照缺失、患者不一致或 JSON 解析失败时抛出
     */
    public EncounterDiagnosisResponse getEncounterDiagnosis(GetEncounterDiagnosisRequest request) {
        // Gate：医生站可见范围（BR-IP-DOR-010）+ 患者一致性与 IMP 类别
        InpatientEncounterContextQueryModel snapshot =
            this.inpatientDoctorStationEncounterVisibilityResolver.assertVisible(
                request.getEncounterId(), request.getPatientId());
        inpatientDoctorStationDiagnosisMapper.validateInpatientEncounterClassImp(snapshot);
        // Pre：实时扫表拼装诊断行
        List<EncounterDiagnosisRowDto> rows = inpatientDoctorStationDiagnosisMapper.parseDiagnosisJson(
            encounterDiagnosisSnapshotAssembler.buildSnapshot(request.getEncounterId()).diagnosisJson());
        // Asm：映射诊断列表响应
        return inpatientDoctorStationDiagnosisMapper.toEncounterDiagnosisResponse(rows);
    }

    /**
     * 加载常用诊断候选（患者历史、医师常用、个人模板、科室模板四类）。
     *
     * @param request 患者标识（历史诊断）与登录上下文（模板）
     * @return 四类常用诊断树数据
     */
    public CommonDiagnosesResponse getConditionDefinitionBusinessClass(GetCommonDiagnosesRequest request) {
        // Pre：解析登录用户、机构与医务人员上下文
        Long userId = currentOperatorResolver.findOperatorIdOrNull();
        Long orgId = managedOrganizationIdsResolver.resolveOrganizationUnitIdForQuery();
        Long practitionerId = currentPractitionerResolver.findPractitionerIdOrNull();
        Map<String, String> sourceLabels = this.buildSourceLabelMap();
        List<CommonDiagnosisRowDto> history = this.buildPatientHistoryCommonRows(request.getPatientId(), sourceLabels);
        List<CommonDiagnosisRowDto> doctorRows = new ArrayList<>();
        // Branch：有医务人员 id 时加载个人模板
        if (practitionerId != null) {
            List<ConditionTemplateQueryModel> doctorTemplates = conditionTemplateQueryService.listByOwnerAndType(
                practitionerId, ConditionTemplateBinding.PERSONAL.getCode());
            doctorRows = this.mapTemplatesToCommonRows(doctorTemplates, sourceLabels);
        }
        List<CommonDiagnosisRowDto> personalRows = new ArrayList<>();
        // Branch：有登录用户 id 时加载用户个人模板
        if (userId != null) {
            List<ConditionTemplateQueryModel> personalTemplates = conditionTemplateQueryService.listByOwnerAndType(
                userId, ConditionTemplateBinding.PERSONAL.getCode());
            personalRows = this.mapTemplatesToCommonRows(personalTemplates, sourceLabels);
        }
        List<CommonDiagnosisRowDto> orgRows = new ArrayList<>();
        // Branch：有机构 id 时加载科室模板
        if (orgId != null) {
            List<ConditionTemplateQueryModel> orgTemplates = conditionTemplateQueryService.listByOwnerAndType(
                orgId, ConditionTemplateBinding.DEPARTMENT.getCode());
            orgRows = this.mapTemplatesToCommonRows(orgTemplates, sourceLabels);
        }
        // Asm：装配四类常用诊断响应
        return inpatientDoctorStationDiagnosisMapper.toCommonDiagnosesResponse(
            history, doctorRows, personalRows, orgRows,
            EnumUtil.toOptions(IptPatientDiseaseDiagType.class));
    }

    /**
     * 分页检索已发布诊断定义元数据。
     *
     * @param request 关键字、中西医筛选与分页
     * @return 已发布定义分页行
     * @throws BusinessException 中西医筛选码非法时抛出
     */
    public ConditionDefinitionPageResponse getConditionDefinitionMetadata(PageConditionDefinitionRequest request) {
        // Gate：中西医筛选码须为合法枚举值
        if (StringUtils.isNotEmpty(request.getDiagnosisKindCode())) {
            String code = request.getDiagnosisKindCode().trim();
            if (!DoctorStationDiagnosisConstants.DIAGNOSIS_KIND_FILTER_WESTERN.equals(code)
                && !DoctorStationDiagnosisConstants.DIAGNOSIS_KIND_FILTER_TCM.equals(code)) {
                throw new BusinessException(
                    ErrorCodeConstants.CONDITION_DEFINITION_INVALID_ARGUMENT,
                    "诊断中西医维度筛选参数非法");
            }
        }
        // Pre：解析中西医筛选并分页查询定义
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
        // Asm：映射定义分页响应
        return inpatientDoctorStationDiagnosisMapper.toConditionDefinitionPageResponse(page, records);
    }

    /**
     * 批量保存住院诊断（Condition + 就诊诊断快照关联）。
     *
     * @param request 全量诊断批次（含主诊唯一、排序唯一等应用层校验）
     * @return 保存条数与 conditionId 列表
     * @throws BusinessException 门控失败或领域规则冲突时抛出
     */
    public SaveEncounterDiagnosisResponse saveDoctorDiagnosis(SaveEncounterDiagnosisRequest request) {
        // Gate：批次结构、住院类别、患者一致性与可写态
        inpatientDoctorStationDiagnosisMapper.validateSaveEncounterDiagnosisRequest(request);
        inpatientDoctorStationDiagnosisMapper.validateIptDiseaseTypeCodesInBatch(request);
        InpatientEncounterContextQueryModel snapshot =
            this.inpatientDoctorStationEncounterVisibilityResolver.assertVisible(
                request.getEncounterId(), request.getPatientId());
        inpatientDoctorStationDiagnosisMapper.validateInpatientEncounterClassImp(snapshot);
        inpatientDoctorStationDiagnosisMapper.validateInpatientEncounterWritableGate(snapshot);
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT,
            "请先选择患者");
        SaveEncounterDiagnosisResponse response;
        try {
            // Cmd：保存 Condition 并同步就诊诊断关联
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
     * 删除单条住院诊断（幂等：已删仍返回成功）。
     *
     * @param request 待删 conditionId 与就诊上下文
     * @return 删除结果与提示文案
     * @throws BusinessException 仍有医嘱引用或门控失败时抛出
     */
    public DeleteEncounterDiagnosisResponse delEncounterDiagnosis(DeleteEncounterDiagnosisRequest request) {
        // Gate：删除请求、住院类别、患者一致性与可写态
        inpatientDoctorStationDiagnosisMapper.validateDeleteEncounterDiagnosisRequest(
            request.getConditionId(),
            request.getEncounterId());
        InpatientEncounterContextQueryModel snapshot;
        Long gatePatientId = request.getPatientId();
        if (gatePatientId != null) {
            snapshot = this.inpatientDoctorStationEncounterVisibilityResolver.assertVisible(
                request.getEncounterId(), gatePatientId);
        } else {
            snapshot = this.inpatientDoctorStationEncounterVisibilityResolver.assertVisibleEncounter(
                request.getEncounterId(),
                InpatientDoctorStationConstants.EX_IP_DOR_103,
                "当前患者不在本科室可见范围，请刷新左侧列表");
            gatePatientId = snapshot.getPatientId();
        }
        inpatientDoctorStationDiagnosisMapper.validateInpatientEncounterPatientGate(snapshot, gatePatientId);
        inpatientDoctorStationDiagnosisMapper.validateInpatientEncounterClassImp(snapshot);
        inpatientDoctorStationDiagnosisMapper.validateInpatientEncounterWritableGate(snapshot);
        // Gate：仍有医嘱引用时禁止删除
        int adviceCnt = conditionQueryService.countAdviceRefByConditionId(request.getConditionId());
        if (adviceCnt > 0) {
            throw new BusinessException(ErrorCodeConstants.CONDITION_RULE_CONFLICT, "先处理相关医嘱后再删");
        }
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT,
            "请先选择患者");
        try {
            // Cmd：移除就诊关联并删除 Condition
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
            // Branch：幂等删除，事实或关联已不存在时仍返回成功
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
        // Asm：返回删除成功响应
        return inpatientDoctorStationDiagnosisMapper.toDeleteEncounterDiagnosisResponse(true, "删除成功");
    }

    /**
     * 扩展启用时同步入院诊断至 EMR；失败仅 warn 不阻断主流程。
     *
     * @param encounterId 就诊 id
     * @param patientId   患者 id
     */
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
