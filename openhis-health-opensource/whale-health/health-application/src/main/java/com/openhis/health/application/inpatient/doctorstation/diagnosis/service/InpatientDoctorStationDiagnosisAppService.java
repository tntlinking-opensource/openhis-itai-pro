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
import com.openhis.health.application.inpatient.doctorstation.diagnosis.request.SaveEncounterDiagnosisChildRequest;
import com.openhis.health.application.inpatient.doctorstation.diagnosis.request.SaveEncounterDiagnosisRequest;
import com.openhis.health.application.inpatient.doctorstation.diagnosis.request.SwitchMainEncounterDiagnosisRequest;
import com.openhis.health.application.inpatient.doctorstation.diagnosis.response.CommonDiagnosesResponse;
import com.openhis.health.application.inpatient.doctorstation.diagnosis.response.ConditionDefinitionPageResponse;
import com.openhis.health.application.inpatient.doctorstation.diagnosis.response.ConditionDefinitionRowResponse;
import com.openhis.health.application.inpatient.doctorstation.diagnosis.response.DeleteEncounterDiagnosisResponse;
import com.openhis.health.application.inpatient.doctorstation.diagnosis.response.EncounterDiagnosisResponse;
import com.openhis.health.application.inpatient.doctorstation.diagnosis.response.SaveEncounterDiagnosisResponse;
import com.openhis.health.application.inpatient.doctorstation.diagnosis.response.SwitchMainEncounterDiagnosisResponse;
import com.openhis.health.application.inpatient.doctorstation.diagnosis.request.DeleteEncounterDiagnosisRequest;
import com.openhis.health.domain.administration.encounter.command.BatchUpsertEncounterDiagnosisAssociationCommand;
import com.openhis.health.domain.administration.encounter.command.RemoveEncounterDiagnosisAssociationCommand;
import com.openhis.health.domain.administration.encounter.command.SwitchPrimaryEncounterDiagnosisCommand;
import com.openhis.health.domain.administration.encounter.queries.model.EncounterDiagnosisQueryModel;
import com.openhis.health.domain.administration.encounter.service.EncounterDomainService;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.domain.clinical.conditiondefinition.queries.model.DoctorStationConditionDefinitionRowModel;
import com.openhis.health.domain.clinical.conditiondefinition.service.ConditionDefinitionQueryService;
import com.openhis.health.domain.custom.conditiontemplate.command.BindConditionTemplateCommand;
import com.openhis.health.domain.custom.conditiontemplate.queries.model.ConditionTemplateQueryModel;
import com.openhis.health.domain.custom.conditiontemplate.service.ConditionTemplateDomainService;
import com.openhis.health.domain.custom.conditiontemplate.service.ConditionTemplateQueryService;
import com.openhis.health.application.inpatient.doctorstation.encountermanage.support.InpatientDoctorStationEncounterVisibilityResolver;
import com.openhis.health.application.shared.diagnosis.EncounterDiagnosisReferenceSupport;
import com.openhis.health.application.shared.diagnosis.EncounterDiagnosisSnapshotAssembler;
import com.openhis.health.domain.administration.encounter.queries.model.InpatientEncounterContextQueryModel;
import com.openhis.health.domain.share.constants.DoctorStationDiagnosisConstants;
import com.openhis.health.domain.share.constants.InpatientDoctorStationConstants;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.InpatientDoctorStationDiagnosisConstants;
import com.openhis.health.domain.share.constants.TerminologyCodeSystemNameConstants;
import com.openhis.health.domain.share.enums.ConditionTemplateBinding;
import com.openhis.health.application.outpatient.doctorstation.diagnosis.request.BindCommonDiagnosisRequest;
import com.openhis.health.application.outpatient.doctorstation.diagnosis.response.BindCommonDiagnosisResponse;
import com.openhis.health.application.outpatient.doctorstation.diagnosis.support.PatientHistoryCommonDiagnosisAssembler;
import com.openhis.health.application.outpatient.doctorstation.diagnosis.support.PatientHistoryCommonDiagnosisRow;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.health.domain.terminology.service.CodeSystemQueryService;
import com.openhis.health.domain.share.enums.IptPatientDiseaseDiagType;
import com.openhis.whale.core.application.dto.OptionDto;
import com.openhis.whale.core.application.session.CurrentOperatorResolver;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import com.openhis.health.infrastructure.integration.emr.EmrAdmissionDiagnosisSyncExtension;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 住院医生站诊断录入应用编排。
 *
 * <p>读路径：从住院就诊快照解析当前诊断；分页检索诊断定义与常用模板。</p>
 * <p>写路径：批量保存/删除诊断，仅维护 adm_encounter_diagnosis。</p>
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
    /** 个人/科室常用模板写侧（与门诊 IF-DOR-08A 同构）。 */
    private final ConditionTemplateDomainService conditionTemplateDomainService;
    private final EncounterDomainService encounterDomainService;
    private final EncounterQueryService encounterQueryService;
    private final CurrentManagedOrganizationIdsResolver managedOrganizationIdsResolver;
    private final CurrentPractitionerResolver currentPractitionerResolver;
    /** 登录用户个人模板读路径须用 {@code whale_users.id}。 */
    private final CurrentOperatorResolver currentOperatorResolver;
    private final CodeSystemQueryService codeSystemQueryService;
    private final InpatientDoctorStationDiagnosisMapper inpatientDoctorStationDiagnosisMapper;
    private final PatientHistoryCommonDiagnosisAssembler patientHistoryCommonDiagnosisAssembler;
    private final EmrAdmissionDiagnosisSyncExtension emrAdmissionDiagnosisSyncExtension;
    private final EncounterDiagnosisReferenceSupport encounterDiagnosisReferenceSupport;

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
     * 批量保存住院诊断（仅维护 adm_encounter_diagnosis）。
     *
     * @param request 全量诊断批次（含主诊唯一、排序唯一等应用层校验）
     * @return 保存条数与 encounterDiagnosisId 列表
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
        // Gate：将被移除或改定义的诊断若仍被引用则拒绝（DD §8.1）
        this.assertWesternBatchReferenceGate(request);
        SaveEncounterDiagnosisResponse response;
        try {
            BatchUpsertEncounterDiagnosisAssociationCommand assocCmd =
                inpatientDoctorStationDiagnosisMapper.toBatchUpsertEncounterDiagnosisAssociationCommand(
                    request, operatorId);
            encounterDomainService.batchUpsertEncounterDiagnosisAssociation(assocCmd);
            List<Long> savedIds = this.collectWesternEncounterDiagnosisIds(request.getEncounterId());
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
     * 新增诊断时追加个人/科室常用模板绑定（与门诊 IF-DOR-08A 同构）。
     *
     * @param request 定义主键与勾选标志
     * @return 绑定结果
     * @throws BusinessException 未勾选、登录上下文缺失或写库失败
     */
    public BindCommonDiagnosisResponse bindCommonDiagnosis(BindCommonDiagnosisRequest request) {
        // Gate：定义主键与至少勾选一类常用
        if (request == null || request.getConditionDefinitionId() == null) {
            throw new BusinessException(ErrorCodeConstants.CONDITION_TEMPLATE_INVALID_ARGUMENT, "诊断定义标识不能为空");
        }
        boolean saveAsPersonal = Boolean.TRUE.equals(request.getSaveAsPersonal());
        boolean saveAsDepartment = Boolean.TRUE.equals(request.getSaveAsDepartment());
        if (!saveAsPersonal && !saveAsDepartment) {
            throw new BusinessException(ErrorCodeConstants.CONDITION_TEMPLATE_INVALID_ARGUMENT, "请至少勾选存个人常用或存科室常用");
        }
        // Pre：待绑定定义集合
        List<Long> definitionIds = new ArrayList<>();
        definitionIds.add(request.getConditionDefinitionId());
        if (request.getSyndromeDefinitionId() != null
            && !request.getSyndromeDefinitionId().equals(request.getConditionDefinitionId())) {
            definitionIds.add(request.getSyndromeDefinitionId());
        }
        Long operatorId = this.currentOperatorResolver.findOperatorIdOrNull();
        if (operatorId == null) {
            throw new BusinessException(ErrorCodeConstants.CONDITION_TEMPLATE_INVALID_ARGUMENT, "请先登录后再操作");
        }
        int personalBoundCount = 0;
        int departmentBoundCount = 0;
        // Cmd：个人常用
        if (saveAsPersonal) {
            List<Long> boundIds = this.bindTemplateDefinitions(
                operatorId, ConditionTemplateBinding.PERSONAL.getCode(), definitionIds, operatorId);
            personalBoundCount = boundIds.size();
        }
        // Cmd：科室常用
        if (saveAsDepartment) {
            Long orgId = this.managedOrganizationIdsResolver.resolveOrganizationUnitIdForQuery();
            if (orgId == null) {
                throw new BusinessException(ErrorCodeConstants.CONDITION_TEMPLATE_INVALID_ARGUMENT, "当前登录科室无效，无法存科室常用");
            }
            List<Long> boundIds = this.bindTemplateDefinitions(
                orgId, ConditionTemplateBinding.DEPARTMENT.getCode(), definitionIds, operatorId);
            departmentBoundCount = boundIds.size();
        }
        // Asm：成功响应
        BindCommonDiagnosisResponse response = new BindCommonDiagnosisResponse();
        response.setSuccess(Boolean.TRUE);
        response.setPersonalBoundCount(personalBoundCount);
        response.setDepartmentBoundCount(departmentBoundCount);
        response.setMessage("常用诊断已保存");
        return response;
    }

    /**
     * 按归属对象与绑定类型批量绑定诊断定义。
     *
     * @param objectId      归属对象
     * @param bindingEnum   绑定类型
     * @param definitionIds 定义主键列表
     * @param operatorId    操作人
     * @return 新插入行主键
     */
    private List<Long> bindTemplateDefinitions(
        Long objectId, String bindingEnum, List<Long> definitionIds, Long operatorId) {
        BindConditionTemplateCommand command = new BindConditionTemplateCommand();
        command.setObjectId(objectId);
        command.setBindingEnum(bindingEnum);
        command.setOperatorId(operatorId);
        List<BindConditionTemplateCommand.BindConditionTemplateItemCommand> items = new ArrayList<>();
        for (Long definitionId : definitionIds) {
            BindConditionTemplateCommand.BindConditionTemplateItemCommand item =
                new BindConditionTemplateCommand.BindConditionTemplateItemCommand();
            item.setDefinitionId(definitionId);
            items.add(item);
        }
        command.setItems(items);
        return this.conditionTemplateDomainService.bindConditionTemplate(command);
    }

    /**
     * 删除单条住院诊断（幂等：已删仍返回成功）。
     *
     * @param request 待删 encounterDiagnosisId 与就诊上下文
     * @return 删除结果与提示文案
     * @throws BusinessException 仍有医嘱引用或门控失败时抛出
     */
    public DeleteEncounterDiagnosisResponse delEncounterDiagnosis(DeleteEncounterDiagnosisRequest request) {
        Long encounterDiagnosisId = inpatientDoctorStationDiagnosisMapper.resolveDeleteEncounterDiagnosisId(request);
        // Gate：删除请求、住院类别、患者一致性与可写态
        inpatientDoctorStationDiagnosisMapper.validateDeleteEncounterDiagnosisRequest(
            encounterDiagnosisId,
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
        // Gate：医嘱/账单引用计数（DD §8.1）
        encounterDiagnosisReferenceSupport.assertNotReferenced(encounterDiagnosisId);
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT,
            "请先选择患者");
        try {
            RemoveEncounterDiagnosisAssociationCommand rmCmd = new RemoveEncounterDiagnosisAssociationCommand();
            RemoveEncounterDiagnosisAssociationCommand.RemoveEncounterDiagnosisAssociationItemCommand item =
                new RemoveEncounterDiagnosisAssociationCommand.RemoveEncounterDiagnosisAssociationItemCommand();
            item.setEncounterId(request.getEncounterId());
            item.setEncounterDiagnosisId(encounterDiagnosisId);
            item.setRemoveReason(InpatientDoctorStationDiagnosisConstants.DELETE_REASON_INPATIENT_DOCTOR_STATION);
            rmCmd.setItem(item);
            rmCmd.setOperatorId(operatorId);
            encounterDomainService.removeEncounterDiagnosisAssociation(rmCmd);
        } catch (BusinessException ex) {
            if (ErrorCodeConstants.CONDITION_NOT_FOUND.equals(ex.getErrorCode())) {
                return inpatientDoctorStationDiagnosisMapper.toDeleteEncounterDiagnosisResponse(true, "删除成功");
            }
            if (ErrorCodeConstants.ENCOUNTER_NOT_FOUND.equals(ex.getErrorCode())) {
                return inpatientDoctorStationDiagnosisMapper.toDeleteEncounterDiagnosisResponse(true, "删除成功");
            }
            throw ex;
        } catch (RuntimeException ex) {
            log.warn("delEncounterDiagnosis failed encounterDiagnosisId={}", encounterDiagnosisId, ex);
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_RULE_CONFLICT, "删除失败，请稍后重试或联系管理员");
        }
        syncAdmissionDiagnosisToEmrIfEnabled(request.getEncounterId(), gatePatientId);
        return inpatientDoctorStationDiagnosisMapper.toDeleteEncounterDiagnosisResponse(true, "删除成功");
    }

    /**
     * 切换住院就诊主诊断：目标行（或中医病证组）设为主诊，同就诊仅保留这一条/一组主诊。
     *
     * @param request 就诊与目标就诊诊断行
     * @return 切换结果
     * @throws BusinessException 参数/可见性/持久化失败
     */
    public SwitchMainEncounterDiagnosisResponse switchMainEncounterDiagnosis(
        SwitchMainEncounterDiagnosisRequest request) {
        // Gate：主键必填
        if (request == null || request.getEncounterId() == null) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "就诊标识不能为空");
        }
        if (request.getEncounterDiagnosisId() == null) {
            throw new BusinessException(ErrorCodeConstants.CONDITION_INVALID_ARGUMENT, "诊断标识不能为空");
        }
        // Pre：住院可见性与患者门控
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
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "请先登录后再操作");
        // Cmd：领域切换主诊
        try {
            SwitchPrimaryEncounterDiagnosisCommand cmd = new SwitchPrimaryEncounterDiagnosisCommand();
            cmd.setEncounterId(request.getEncounterId());
            cmd.setEncounterDiagnosisId(request.getEncounterDiagnosisId());
            cmd.setOperatorId(operatorId);
            this.encounterDomainService.switchPrimaryEncounterDiagnosis(cmd);
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.warn("switchMainEncounterDiagnosis failed encounterDiagnosisId={}",
                request.getEncounterDiagnosisId(), ex);
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_RULE_CONFLICT, "切换主诊断失败，请稍后重试或联系管理员");
        }
        syncAdmissionDiagnosisToEmrIfEnabled(request.getEncounterId(), gatePatientId);
        // Asm：成功响应
        SwitchMainEncounterDiagnosisResponse response = new SwitchMainEncounterDiagnosisResponse();
        response.setSuccess(Boolean.TRUE);
        response.setMessage("已设为主诊断");
        return response;
    }

    /**
     * 西医全量保存：汇总本批保留行与定义变更，交引用门禁校验。
     *
     * @param request 保存请求
     */
    private void assertWesternBatchReferenceGate(SaveEncounterDiagnosisRequest request) {
        Set<Long> keepIds = new HashSet<>();
        Map<Long, Long> defByDxId = new HashMap<>();
        List<SaveEncounterDiagnosisChildRequest> children = request.getDiagnosisChildList();
        if (children != null) {
            for (SaveEncounterDiagnosisChildRequest child : children) {
                if (child == null) {
                    continue;
                }
                Long dxId = child.getEncounterDiagnosisId();
                if (dxId == null) {
                    continue;
                }
                keepIds.add(dxId);
                if (child.getConditionDefinitionId() != null) {
                    defByDxId.put(dxId, child.getConditionDefinitionId());
                }
            }
        }
        encounterDiagnosisReferenceSupport.assertWesternBatchSaveAllowed(
            request.getEncounterId(), keepIds, defByDxId);
    }

    /**
     * 保存后收集本次就诊西医诊断行主键。
     *
     * @param encounterId 就诊主键
     * @return 就诊诊断行 id 列表
     */
    private List<Long> collectWesternEncounterDiagnosisIds(Long encounterId) {
        List<EncounterDiagnosisQueryModel> rows = encounterQueryService.listEncounterDiagnoses(encounterId);
        rows.sort(Comparator
            .comparing(EncounterDiagnosisQueryModel::getRank, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(EncounterDiagnosisQueryModel::getDiagnosisId, Comparator.nullsLast(Comparator.naturalOrder())));
        List<Long> ids = new ArrayList<>();
        for (EncounterDiagnosisQueryModel row : rows) {
            if (Boolean.TRUE.equals(row.getTcmFlag())) {
                continue;
            }
            if (row.getDiagnosisId() != null) {
                ids.add(row.getDiagnosisId());
            }
        }
        return ids;
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
        List<PatientHistoryCommonDiagnosisRow> historyRows =
            patientHistoryCommonDiagnosisAssembler.assemble(patientId);
        for (PatientHistoryCommonDiagnosisRow historyRow : historyRows) {
            CommonDiagnosisRowDto row = inpatientDoctorStationDiagnosisMapper.toCommonDiagnosisRowDto(historyRow, sourceLabels);
            if (row != null) {
                rows.add(row);
            }
        }
        return rows;
    }
}
