/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.doctorstation.diagnosis.service;

import com.openhis.health.application.shared.validator.CurrentManagedOrganizationIdsResolver;
import com.openhis.health.application.shared.validator.CurrentPractitionerResolver;
import com.openhis.health.application.outpatient.doctorstation.diagnosis.dto.CommonDiagnosisRowDto;
import com.openhis.health.application.outpatient.doctorstation.diagnosis.dto.EncounterDiagnosisRowDto;
import com.openhis.health.application.outpatient.doctorstation.diagnosis.dto.OpspFilingDiagnosisRowDto;
import com.openhis.health.application.outpatient.doctorstation.diagnosis.mapper.DoctorStationDiagnosisMapper;
import com.openhis.health.application.outpatient.doctorstation.diagnosis.request.*;
import com.openhis.health.application.outpatient.doctorstation.diagnosis.response.*;
import com.openhis.health.domain.administration.encounter.command.RemoveEncounterDiagnosisAssociationCommand;
import com.openhis.health.domain.administration.encounter.command.SwitchPrimaryEncounterDiagnosisCommand;
import com.openhis.health.domain.administration.encounter.queries.model.EncounterDiagnosisQueryModel;
import com.openhis.health.domain.administration.encounter.service.EncounterDomainService;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.domain.clinical.conditiondefinition.queries.model.DoctorStationConditionDefinitionRowModel;
import com.openhis.health.domain.clinical.conditiondefinition.queries.model.OpspDiseCodeDefinitionMatchQueryModel;
import com.openhis.health.domain.clinical.conditiondefinition.service.ConditionDefinitionQueryService;
import com.openhis.health.infrastructure.integration.insurance.benefit.InsuranceRegisterBenefitQueryExtension;
import com.openhis.health.infrastructure.integration.insurance.benefit.ExtensionIn.InsuranceOpspFilingQueryExtensionIn;
import com.openhis.health.infrastructure.integration.insurance.benefit.ExtensionOut.InsuranceOpspFilingEncounterSnapshot;
import com.openhis.health.infrastructure.integration.insurance.benefit.ExtensionOut.InsuranceOpspFilingLineSnapshot;
import com.openhis.health.infrastructure.integration.insurance.benefit.ExtensionOut.InsuranceOpspFilingQueryExtensionOut;
import com.openhis.health.domain.custom.conditiontemplate.command.BindConditionTemplateCommand;
import com.openhis.health.domain.custom.conditiontemplate.queries.model.ConditionTemplateQueryModel;
import com.openhis.health.domain.custom.conditiontemplate.service.ConditionTemplateDomainService;
import com.openhis.health.domain.custom.conditiontemplate.service.ConditionTemplateQueryService;
import com.openhis.health.domain.administration.encounter.queries.model.OutpatientEncounterContextQueryModel;
import com.openhis.health.application.shared.encounter.EncounterContextReader;
import com.openhis.health.application.shared.diagnosis.EncounterDiagnosisReferenceSupport;
import com.openhis.health.application.shared.diagnosis.EncounterDiagnosisSnapshotAssembler;
import com.openhis.health.application.outpatient.doctorstation.diagnosis.support.PatientHistoryCommonDiagnosisAssembler;
import com.openhis.health.application.outpatient.doctorstation.diagnosis.support.PatientHistoryCommonDiagnosisRow;
import com.openhis.health.domain.share.constants.DoctorStationDiagnosisConstants;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.TerminologyCodeSystemNameConstants;
import com.openhis.health.domain.share.enums.ConditionTemplateBinding;
import com.openhis.health.domain.share.enums.EncounterClass;
import com.openhis.health.domain.terminology.service.CodeSystemQueryService;
import com.openhis.whale.core.application.session.CurrentOperatorResolver;
import com.openhis.whale.core.application.dto.OptionDto;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * 门诊医生站诊断录入编排：就诊诊断快照、常用树、慢特病备案导入候选、定义检索与保存/删除。
 *
 * <p>对应 AS-OP-DOR-002 / AS-SDD：IF-DOR-06/06A/06B/07/08/08A/09。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DoctorStationDiagnosisAppService {

    private final EncounterQueryService encounterQueryService;
    private final EncounterContextReader EncounterContextReader;
    private final EncounterDiagnosisSnapshotAssembler encounterDiagnosisSnapshotAssembler;
    private final PatientHistoryCommonDiagnosisAssembler patientHistoryCommonDiagnosisAssembler;
    private final ConditionDefinitionQueryService conditionDefinitionQueryService;
    private final ConditionTemplateQueryService conditionTemplateQueryService;
    /** 个人/科室常用模板写侧（IF-DOR-08A）。 */
    private final ConditionTemplateDomainService conditionTemplateDomainService;
    private final EncounterDomainService encounterDomainService;
    private final CurrentManagedOrganizationIdsResolver managedOrganizationIdsResolver;
    private final CurrentPractitionerResolver currentPractitionerResolver;
    /** 登录用户个人模板读路径须用 {@code whale_users.id}，与医务人员模板主键分离。 */
    private final CurrentOperatorResolver currentOperatorResolver;
    private final CodeSystemQueryService codeSystemQueryService;
    private final DoctorStationDiagnosisMapper doctorStationDiagnosisMapper;
    /** 医保待遇扩展：未启用 CHS 时 isEnabled=false，慢病备案入口隐藏。 */
    private final InsuranceRegisterBenefitQueryExtension registerBenefitQueryExtension;
    private final EncounterDiagnosisReferenceSupport encounterDiagnosisReferenceSupport;

    /**
     * 查询当前就诊诊断列表（读快照）。
     *
     * @param request 就诊与患者上下文
     * @return 诊断列表
     * @throws BusinessException 就诊不存在、非门诊读模型、患者上下文不一致或快照解析失败
     */
    public EncounterDiagnosisResponse getEncounterDiagnosis(GetEncounterDiagnosisRequest request) {
        // 强依赖门诊信息读模型，缺失即视为未选择有效就诊（医生站统一口径 EX-DOD-104）。
        OutpatientEncounterContextQueryModel snapshot =
            this.EncounterContextReader.mustGetOutpatientContextByEncounterId(request.getEncounterId());
        // 校验患者上下文与读模型一致，避免串患者卡片展示诊断。
        this.doctorStationDiagnosisMapper.validateEncounterPatientGate(snapshot, request.getPatientId());
        // 医生站诊断页仅服务门诊类别读模型行，防止住院行误入。
        if (!EncounterClass.AMB.getCode().equals(snapshot.getClassEnum())) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_STATUS, "当前就诊类别不允许在此加载门诊诊断列表");
        }
        // 实时扫表拼装诊断行，禁止仅依赖上下文 diagnosisJson 中转（读模型废止后 §四）。
        List<EncounterDiagnosisRowDto> rows = this.doctorStationDiagnosisMapper.parseDiagnosisJson(
            this.encounterDiagnosisSnapshotAssembler.buildSnapshot(request.getEncounterId()).diagnosisJson());
        return this.doctorStationDiagnosisMapper.toEncounterDiagnosisResponse(rows);
    }

    /**
     * 加载四类常用诊断快捷候选。
     *
     * <p><b>规模（B.2）</b>：「病人历史」分支对患者维度就诊诊断做扫描后去重再批量拉目录；
     * 长期就诊极多患者时单次请求成本偏高，后续若需收敛应增加时间窗或条数上限（须产品/规格确认）。</p>
     *
     * @param request 至少包含患者标识
     * @return 四类列表
     * @throws BusinessException 查询失败或上下文缺失
     */
    public CommonDiagnosesResponse getConditionDefinitionBusinessClass(GetCommonDiagnosesRequest request) {
        // 门控：术语展示依赖登录用户与科室上下文；缺失时仍允许返回空树，但医师维度无法解析。
        Long userId = this.currentOperatorResolver.findOperatorIdOrNull();
        Long orgId = this.managedOrganizationIdsResolver.resolveOrganizationUnitIdForQuery();
        Long practitionerId = this.currentPractitionerResolver.findPractitionerIdOrNull();
        Map<String, String> sourceLabels = this.buildSourceLabelMap();
        // 病人历史诊断来自就诊诊断行去重后的定义集合（非 Condition 事实表）。
        List<CommonDiagnosisRowDto> history = this.buildPatientHistoryCommonRows(request.getPatientId(), sourceLabels);
        // 医生常用模板：绑定对象按医师主键解释（若无法解析医师则返回空列表）。
        List<CommonDiagnosisRowDto> doctorRows = new ArrayList<>();
        if (practitionerId != null) {
            List<ConditionTemplateQueryModel> doctorTemplates = this.conditionTemplateQueryService.listByOwnerAndType(
                practitionerId, ConditionTemplateBinding.PERSONAL.getCode());
            doctorRows = this.mapTemplatesToCommonRows(doctorTemplates, sourceLabels);
        }
        // 用户个人模板（绑定 PERSONAL + 用户 id）。
        List<CommonDiagnosisRowDto> personalRows = new ArrayList<>();
        if (userId != null) {
            List<ConditionTemplateQueryModel> personalTemplates = this.conditionTemplateQueryService.listByOwnerAndType(
                userId, ConditionTemplateBinding.PERSONAL.getCode());
            personalRows = this.mapTemplatesToCommonRows(personalTemplates, sourceLabels);
        }
        // 科室模板（绑定 DEPARTMENT + 科室 id）。
        List<CommonDiagnosisRowDto> orgRows = new ArrayList<>();
        if (orgId != null) {
            List<ConditionTemplateQueryModel> orgTemplates = this.conditionTemplateQueryService.listByOwnerAndType(
                orgId, ConditionTemplateBinding.DEPARTMENT.getCode());
            orgRows = this.mapTemplatesToCommonRows(orgTemplates, sourceLabels);
        }
        // 聚合四类列表为响应（仅读）。
        return this.doctorStationDiagnosisMapper.toCommonDiagnosesResponse(history, doctorRows, personalRows, orgRows);
    }

    /**
     * 查询患者慢特病备案可导入诊断（IF-DOR-06B）。
     *
     * <p>CHS 未启用时直接返回 {@code opspFilingImportEnabled=false}，不调平台；
     * 写入仍由前端勾选后走 {@link #saveDoctorDiagnosis}。</p>
     *
     * @param request 就诊与患者上下文
     * @return 启用标志与备案候选行
     * @throws BusinessException 就诊不存在、非门诊或串患者
     */
    public PatientOpspFilingDiagnosisResponse getPatientOpspFilingDiagnosis(
        GetPatientOpspFilingDiagnosisRequest request) {
        // Gate：未装配医保模块时隐藏入口，禁止发起 5301（BR-DOD-012）。
        if (!this.registerBenefitQueryExtension.isEnabled()) {
            return this.doctorStationDiagnosisMapper.toPatientOpspFilingDiagnosisResponse(false, List.of());
        }
        // Pre：门诊就诊上下文与串患者门禁。
        OutpatientEncounterContextQueryModel snapshot =
            this.EncounterContextReader.mustGetOutpatientContextByEncounterId(request.getEncounterId());
        this.doctorStationDiagnosisMapper.validateEncounterPatientGate(snapshot, request.getPatientId());
        // 仅门诊诊断 Tab 消费本接口。
        if (!EncounterClass.AMB.getCode().equals(snapshot.getClassEnum())) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_STATUS, "当前就诊类别不允许加载慢病备案诊断");
        }
        // Pre：解析 psnNo/contractId（平台映射 / 预结算 / 读卡缓存）；自费或未读卡时返回空列表但仍标记启用。
        InsuranceOpspFilingEncounterSnapshot filingCtx =
            this.registerBenefitQueryExtension.resolveOpspFilingEncounter(request.getEncounterId());
        if (filingCtx == null
            || filingCtx.getContractId() == null
            || StringUtils.isBlank(filingCtx.getPsnNo())) {
            return this.doctorStationDiagnosisMapper.toPatientOpspFilingDiagnosisResponse(true, List.of());
        }
        List<InsuranceOpspFilingLineSnapshot> filingLines = new ArrayList<>();
        try {
            InsuranceOpspFilingQueryExtensionIn queryCommand = new InsuranceOpspFilingQueryExtensionIn();
            queryCommand.setContractId(filingCtx.getContractId());
            queryCommand.setPsnNo(filingCtx.getPsnNo().trim());
            InsuranceOpspFilingQueryExtensionOut filingOut =
                this.registerBenefitQueryExtension.queryOpspFiling(queryCommand);
            // 平台返回空：展示空慢病备案树，不报错。
            if (filingOut != null && filingOut.getFilingLines() != null) {
                filingLines = filingOut.getFilingLines();
            }
        } catch (RuntimeException ex) {
            // EX-DOD-106：平台失败弱提示，不阻断诊断 Tab。
            log.warn(
                "5301 慢特病备案查询失败 encounterId={} contractId={} psnNo={}: {}",
                request.getEncounterId(),
                filingCtx.getContractId(),
                filingCtx.getPsnNo(),
                ex.getMessage());
            return this.doctorStationDiagnosisMapper.toPatientOpspFilingDiagnosisResponse(true, List.of());
        }
        // Pre：当前就诊已开立定义，用于 alreadyOnEncounter。
        Set<Long> onEncounterDefinitionIds = this.loadOnEncounterDefinitionIds(request.getEncounterId());
        List<String> diseCodes = new ArrayList<>();
        for (InsuranceOpspFilingLineSnapshot line : filingLines) {
            if (line == null || StringUtils.isBlank(line.getOpspDiseCode())) {
                continue;
            }
            diseCodes.add(line.getOpspDiseCode().trim());
        }
        Map<String, OpspDiseCodeDefinitionMatchQueryModel> matchByCode = new HashMap<>();
        // 无备案码时跳过目录映射查询。
        if (!diseCodes.isEmpty()) {
            List<OpspDiseCodeDefinitionMatchQueryModel> matches =
                this.conditionDefinitionQueryService.listActiveDoctorStationRowsByOpspDiseCodes(diseCodes);
            for (OpspDiseCodeDefinitionMatchQueryModel match : matches) {
                if (match != null && StringUtils.isNotBlank(match.getOpspDiseCode())) {
                    matchByCode.put(match.getOpspDiseCode().trim(), match);
                }
            }
        }
        LocalDate today = LocalDate.now();
        List<OpspFilingDiagnosisRowDto> rows = new ArrayList<>();
        for (InsuranceOpspFilingLineSnapshot line : filingLines) {
            if (line == null || StringUtils.isBlank(line.getOpspDiseCode())) {
                continue;
            }
            String code = line.getOpspDiseCode().trim();
            boolean valid = this.isOpspFilingValid(line.getEnddate(), today);
            OpspDiseCodeDefinitionMatchQueryModel match = matchByCode.get(code);
            String matchStatus;
            String matchHint = null;
            // 过期优先标记，禁止导入。
            if (!valid) {
                matchStatus = DoctorStationDiagnosisConstants.OpspFilingMatchStatus.EXPIRED;
                matchHint = "备案已过期，不可导入";
            } else if (match == null) {
                matchStatus = DoctorStationDiagnosisConstants.OpspFilingMatchStatus.UNMATCHED;
                matchHint = "该病种未维护院内目录对码，请联系信息科维护后再导入";
            } else if (match.isAmbiguous()) {
                matchStatus = DoctorStationDiagnosisConstants.OpspFilingMatchStatus.AMBIGUOUS;
                matchHint = "院内目录存在多条匹配，请手工检索选择";
            } else if (match.getConditionDefinitionId() != null
                && onEncounterDefinitionIds.contains(match.getConditionDefinitionId())) {
                matchStatus = DoctorStationDiagnosisConstants.OpspFilingMatchStatus.ALREADY_ON_ENCOUNTER;
                matchHint = "该诊断已在当前就诊列表中";
            } else {
                matchStatus = DoctorStationDiagnosisConstants.OpspFilingMatchStatus.MATCHED;
            }
            rows.add(this.doctorStationDiagnosisMapper.toOpspFilingDiagnosisRowDto(
                line, match, matchStatus, matchHint, valid));
        }
        // Asm：启用标志为 true，供前端渲染「慢病备案」分支。
        return this.doctorStationDiagnosisMapper.toPatientOpspFilingDiagnosisResponse(true, rows);
    }

    /**
     * 分页检索已发布诊断定义。
     *
     * @param request 检索条件
     * @return 分页结果
     * @throws BusinessException 参数非法
     */
    public ConditionDefinitionPageResponse getConditionDefinitionMetadata(PageConditionDefinitionRequest request) {
        // 门控：诊断中西医维度筛选只允许 1/2/空，避免构造无法下推到 SQL 的非法 source 集合。
        if (StringUtils.isNotEmpty(request.getDiagnosisKindCode())) {
            String code = request.getDiagnosisKindCode().trim();
            if (!DoctorStationDiagnosisConstants.DIAGNOSIS_KIND_FILTER_WESTERN.equals(code)
                && !DoctorStationDiagnosisConstants.DIAGNOSIS_KIND_FILTER_TCM.equals(code)) {
                throw new BusinessException(ErrorCodeConstants.CONDITION_DEFINITION_INVALID_ARGUMENT, "诊断中西医维度筛选参数非法");
            }
        }
        // 将筛选入参解析为目录来源编码集合；null 表示不按来源收敛。
        List<String> sourceIn = this.doctorStationDiagnosisMapper.parseDiagnosisKindSourceFilter(request.getDiagnosisKindCode());
        // 分页检索仅返回已启用目录行，关键字同时命中名称/编码/拼音/五笔列。
        Page<DoctorStationConditionDefinitionRowModel> page = this.conditionDefinitionQueryService.pageActiveDoctorStationDefinitions(
            request.getKeyword(),
            sourceIn,
            request.getPageNo(),
            request.getPageSize());
        Map<String, String> sourceLabels = this.buildSourceLabelMap();
        List<ConditionDefinitionRowResponse> records = new ArrayList<>();
        for (DoctorStationConditionDefinitionRowModel model : page.getContent()) {
            // 每行补充目录来源中文名、中西医标签等展示字段；无批量 API，仅内存装配 acceptable。
            ConditionDefinitionRowResponse definitionRow =
                this.doctorStationDiagnosisMapper.toConditionDefinitionRowResponse(model, sourceLabels);
            records.add(definitionRow);
        }
        // 装配分页元数据与行列表。
        return this.doctorStationDiagnosisMapper.toConditionDefinitionPageResponse(page, records);
    }

    /**
     * 医生保存诊断（仅维护 adm_encounter_diagnosis，不经 Condition）。
     *
     * @param request 全量批次
     * @return 保存结果
     * @throws BusinessException 参数/上下文/持久化失败
     */
    public SaveEncounterDiagnosisResponse saveDoctorDiagnosis(SaveEncounterDiagnosisRequest request) {
        // 门控：批次结构与主诊/排序等业务校验，失败则阻断写库。
        this.doctorStationDiagnosisMapper.validateSaveEncounterDiagnosisRequest(request);
        // 加载读模型快照并校验患者上下文，避免写入错误就诊下的诊断事实。
        OutpatientEncounterContextQueryModel snapshot =
            this.EncounterContextReader.mustGetOutpatientContextByEncounterId(request.getEncounterId());
        this.doctorStationDiagnosisMapper.validateEncounterPatientGate(snapshot, request.getPatientId());
        // 就诊类别门禁：仅允许门诊就诊保存诊断
        if (!EncounterClass.AMB.getCode().equals(snapshot.getClassEnum())) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_STATUS, "当前就诊类别不允许在此保存门诊诊断");
        }
        // 解析当前操作人主键，未登录时阻断写操作
        Long operatorId = currentPractitionerResolver.requireOperatorId(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "请先登录后再操作");
        // Gate：将被移除或改定义的诊断若仍被医嘱/账单引用则拒绝（DD §8.1）
        this.assertWesternBatchReferenceGate(request);
        SaveEncounterDiagnosisResponse response;
        try {
            com.openhis.health.domain.administration.encounter.command.BatchUpsertEncounterDiagnosisAssociationCommand assocCmd =
                this.doctorStationDiagnosisMapper.toBatchUpsertEncounterDiagnosisAssociationCommand(request, operatorId);
            this.encounterDomainService.batchUpsertEncounterDiagnosisAssociation(assocCmd);
            List<Long> savedIds = this.collectWesternEncounterDiagnosisIds(request.getEncounterId());
            response = this.doctorStationDiagnosisMapper.toSaveEncounterDiagnosisResponse(savedIds.size(), savedIds);
        } catch (BusinessException ex) {
            // 业务规则类失败原样上抛，便于前端展示具体错误码与文案。
            throw ex;
        } catch (RuntimeException ex) {
            // 非预期异常：打日志后统一包装，避免栈信息直出给患者端。
            log.warn("saveDoctorDiagnosis failed encounterId={}", request.getEncounterId(), ex);
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_RULE_CONFLICT, "保存失败，请稍后重试或联系管理员");
        }
        return response;
    }

    /**
     * 新增诊断时追加个人/科室常用模板绑定（IF-DOR-08A）。
     *
     * <p>个人绑定 objectId = 当前登录用户；科室绑定 objectId = 当前登录科室。
     * 已存在同对象+绑定类型+定义的行幂等跳过。</p>
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
        // Pre：待绑定定义集合（西医单条；中医可附带证候）
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
        // Cmd：个人常用 → PERSONAL + whale_users.id（与 IF-DOR-06A 读侧一致）
        if (saveAsPersonal) {
            List<Long> boundIds = this.bindTemplateDefinitions(
                operatorId, ConditionTemplateBinding.PERSONAL.getCode(), definitionIds, operatorId);
            personalBoundCount = boundIds.size();
        }
        // Cmd：科室常用 → DEPARTMENT + 当前登录科室
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
     * 按归属对象与绑定类型批量绑定诊断定义（内部复用领域 bind）。
     *
     * @param objectId      归属对象（用户或科室）
     * @param bindingEnum   绑定类型 code
     * @param definitionIds 诊断定义主键列表
     * @param operatorId    操作人
     * @return 本次新插入行主键
     */
    private List<Long> bindTemplateDefinitions(
        Long objectId, String bindingEnum, List<Long> definitionIds, Long operatorId) {
        BindConditionTemplateCommand command = new BindConditionTemplateCommand();
        command.setObjectId(objectId);
        command.setBindingEnum(bindingEnum);
        command.setOperatorId(operatorId);
        List<BindConditionTemplateCommand.BindConditionTemplateItemCommand> items = new ArrayList<>();
        // 按定义列表组装绑定明细
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
     * 删除就诊诊断（按就诊诊断行主键软删关联，不经 Condition）。
     *
     * @param request 诊断与就诊标识
     * @return 删除结果
     * @throws BusinessException 非幂等可恢复错误（如仍被医嘱引用）时抛出
     */
    public DeleteEncounterDiagnosisResponse delEncounterDiagnosis(DeleteEncounterDiagnosisRequest request) {
        Long encounterDiagnosisId = this.doctorStationDiagnosisMapper.resolveDeleteEncounterDiagnosisId(request);
        // 门控：参数必填校验。
        this.doctorStationDiagnosisMapper.validateDeleteEncounterDiagnosisRequest(encounterDiagnosisId, request.getEncounterId());
        // 读模型门控，避免未选择就诊即删除。
        OutpatientEncounterContextQueryModel snapshot =
            this.EncounterContextReader.mustGetOutpatientContextByEncounterId(request.getEncounterId());
        // 就诊类别门禁：仅允许门诊就诊删除诊断
        if (!EncounterClass.AMB.getCode().equals(snapshot.getClassEnum())) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_STATUS, "当前就诊类别不允许在此删除门诊诊断");
        }
        // Gate：医嘱/账单引用计数门控（DD §8.1）
        this.encounterDiagnosisReferenceSupport.assertNotReferenced(encounterDiagnosisId);
        // 解析当前操作人主键
        Long operatorId = currentPractitionerResolver.requireOperatorId(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "请先登录后再操作");
        try {
            RemoveEncounterDiagnosisAssociationCommand rmCmd = new RemoveEncounterDiagnosisAssociationCommand();
            RemoveEncounterDiagnosisAssociationCommand.RemoveEncounterDiagnosisAssociationItemCommand item =
                new RemoveEncounterDiagnosisAssociationCommand.RemoveEncounterDiagnosisAssociationItemCommand();
            item.setEncounterId(request.getEncounterId());
            item.setEncounterDiagnosisId(encounterDiagnosisId);
            item.setRemoveReason("DOCTOR_STATION_DELETE");
            rmCmd.setItem(item);
            rmCmd.setOperatorId(operatorId);
            this.encounterDomainService.removeEncounterDiagnosisAssociation(rmCmd);
        } catch (BusinessException ex) {
            // 幂等：重复删除时关联可能已不存在，视为成功以避免前端抖动。
            if (ErrorCodeConstants.CONDITION_NOT_FOUND.equals(ex.getErrorCode())) {
                return this.doctorStationDiagnosisMapper.toDeleteEncounterDiagnosisResponse(true, "删除成功");
            }
            if (ErrorCodeConstants.ENCOUNTER_NOT_FOUND.equals(ex.getErrorCode())) {
                return this.doctorStationDiagnosisMapper.toDeleteEncounterDiagnosisResponse(true, "删除成功");
            }
            throw ex;
        } catch (RuntimeException ex) {
            log.warn("delEncounterDiagnosis failed encounterDiagnosisId={}", encounterDiagnosisId, ex);
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_RULE_CONFLICT, "删除失败，请稍后重试或联系管理员");
        }
        return this.doctorStationDiagnosisMapper.toDeleteEncounterDiagnosisResponse(true, "删除成功");
    }

    /**
     * 切换就诊主诊断：目标行（或中医病证组）设为主诊，同就诊仅保留这一条/一组主诊。
     *
     * @param request 就诊与目标就诊诊断行
     * @return 切换结果
     * @throws BusinessException 参数/上下文/持久化失败
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
        // Pre：门诊就诊上下文与患者门控
        OutpatientEncounterContextQueryModel snapshot =
            this.EncounterContextReader.mustGetOutpatientContextByEncounterId(request.getEncounterId());
        if (request.getPatientId() != null) {
            this.doctorStationDiagnosisMapper.validateEncounterPatientGate(snapshot, request.getPatientId());
        }
        // 就诊类别门禁：仅允许门诊就诊切换主诊
        if (!EncounterClass.AMB.getCode().equals(snapshot.getClassEnum())) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_STATUS, "当前就诊类别不允许在此切换门诊主诊断");
        }
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "请先登录后再操作");
        // Cmd：领域切换主诊（清空其它主诊 + 目标/病证组置主）
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
        this.encounterDiagnosisReferenceSupport.assertWesternBatchSaveAllowed(
            request.getEncounterId(), keepIds, defByDxId);
    }

    /**
     * 保存后收集本次就诊西医诊断行主键（按 rank 排序，不含中医病证组行）。
     *
     * @param encounterId 就诊主键
     * @return 就诊诊断行 id 列表
     */
    private List<Long> collectWesternEncounterDiagnosisIds(Long encounterId) {
        List<EncounterDiagnosisQueryModel> rows = this.encounterQueryService.listEncounterDiagnoses(encounterId);
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
     * 构建目录来源码到展示文案映射（单次请求内复用）。
     *
     * @return 映射
     */
    private Map<String, String> buildSourceLabelMap() {
        Map<String, String> map = new HashMap<>();
        List<OptionDto> options = this.codeSystemQueryService.getOptionListByCodeSystemName(
                TerminologyCodeSystemNameConstants.CONDITION_DEFINITION_SOURCE);
        if (options == null) {
            // 术语服务不可用时返回空映射：下游仍展示来源码，不阻断诊断检索主流程。
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
     * 将模板绑定列表解析为常用诊断行（保持模板顺序，过滤无效定义）。
     *
     * @param templates    模板只读行
     * @param sourceLabels 来源展示映射
     * @return 常用行列表
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
        // 模板仅存 definitionId：一次性批量拉启用定义行，避免逐条查库。
        List<DoctorStationConditionDefinitionRowModel> defs = this.conditionDefinitionQueryService.listActiveDoctorStationRowsByIds(ids);
        Map<Long, DoctorStationConditionDefinitionRowModel> byId = new HashMap<>();
        for (DoctorStationConditionDefinitionRowModel def : defs) {
            byId.put(def.getId(), def);
        }
        for (ConditionTemplateQueryModel template : templates) {
            DoctorStationConditionDefinitionRowModel def = byId.get(template.getDefinitionId());
            // 模板指向已停用/已删定义：静默跳过，避免常用树出现不可选脏数据。
            if (def == null) {
                continue;
            }
            CommonDiagnosisRowDto row = this.doctorStationDiagnosisMapper.toCommonDiagnosisRowDto(def, sourceLabels);
            rows.add(row);
        }
        return rows;
    }

    /**
     * 基于患者历史就诊诊断构造常用行（西医按定义去重；中医按病证组合并）。
     *
     * @param patientId    患者
     * @param sourceLabels 来源展示映射
     * @return 常用行列表
     */
    private List<CommonDiagnosisRowDto> buildPatientHistoryCommonRows(Long patientId, Map<String, String> sourceLabels) {
        List<CommonDiagnosisRowDto> rows = new ArrayList<>();
        // Pre：Bridge 装配患者历史（西医按定义去重；中医按病证组合并展示）
        List<PatientHistoryCommonDiagnosisRow> historyRows =
            this.patientHistoryCommonDiagnosisAssembler.assemble(patientId);
        // Asm：映射为快捷树 CommonDiagnosisRowDto
        for (PatientHistoryCommonDiagnosisRow historyRow : historyRows) {
            CommonDiagnosisRowDto row = this.doctorStationDiagnosisMapper.toCommonDiagnosisRowDto(historyRow, sourceLabels);
            if (row != null) {
                rows.add(row);
            }
        }
        return rows;
    }

    /**
     * 加载当前就诊已挂接诊断定义主键集合（用于慢病备案去重）。
     *
     * @param encounterId 就诊标识
     * @return 定义主键集合
     */
    private Set<Long> loadOnEncounterDefinitionIds(Long encounterId) {
        Set<Long> ids = new HashSet<>();
        List<EncounterDiagnosisRowDto> rows = this.doctorStationDiagnosisMapper.parseDiagnosisJson(
            this.encounterDiagnosisSnapshotAssembler.buildSnapshot(encounterId).diagnosisJson());
        if (rows == null) {
            return ids;
        }
        for (EncounterDiagnosisRowDto row : rows) {
            if (row != null && row.getConditionDefinitionId() != null) {
                ids.add(row.getConditionDefinitionId());
            }
        }
        return ids;
    }

    /**
     * 判断慢特病备案是否仍在有效期内（结束日为空或 ≥ 今天）。
     *
     * @param endDateRaw 结束日期字符串（yyyy-MM-dd）
     * @param today      当天
     * @return true 表示可导入窗口内
     */
    private boolean isOpspFilingValid(String endDateRaw, LocalDate today) {
        if (StringUtils.isBlank(endDateRaw)) {
            return true;
        }
        try {
            LocalDate end = LocalDate.parse(endDateRaw.trim());
            // 结束日当天仍视为有效。
            return !end.isBefore(today);
        } catch (DateTimeParseException ex) {
            // 平台日期异常：保守视为有效，由医生结合名称判断。
            return true;
        }
    }
}
