/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.doctorstation.diagnosis.service;

import com.zxplat.application.outpatient.doctorstation.diagnosis.dto.MaintainableCommonDiagnosisRowDto;
import com.zxplat.application.shared.validator.CurrentManagedOrganizationIdsResolver;
import com.zxplat.application.shared.validator.CurrentPractitionerResolver;
import com.zxplat.application.outpatient.doctorstation.diagnosis.dto.CommonDiagnosisRowDto;
import com.zxplat.application.outpatient.doctorstation.diagnosis.dto.EncounterDiagnosisRowDto;
import com.zxplat.application.outpatient.doctorstation.diagnosis.dto.OpspFilingDiagnosisRowDto;
import com.zxplat.application.outpatient.doctorstation.diagnosis.mapper.DoctorStationDiagnosisMapper;
import com.zxplat.application.outpatient.doctorstation.diagnosis.request.*;
import com.zxplat.application.outpatient.doctorstation.diagnosis.response.*;
import com.zxplat.domain.administration.encounter.command.RemoveEncounterDiagnosisAssociationCommand;
import com.zxplat.domain.administration.encounter.command.SwitchPrimaryEncounterDiagnosisCommand;
import com.zxplat.domain.administration.encounter.queries.model.EncounterDiagnosisQueryModel;
import com.zxplat.domain.administration.encounter.service.EncounterDomainService;
import com.zxplat.domain.administration.encounter.service.EncounterQueryService;
import com.zxplat.domain.clinical.conditiondefinition.queries.model.DoctorStationConditionDefinitionRowModel;
import com.zxplat.domain.clinical.conditiondefinition.queries.model.EnsureChronicSpecOpspResult;
import com.zxplat.domain.clinical.conditiondefinition.queries.model.OpspDiseCodeDefinitionMatchQueryModel;
import com.zxplat.domain.clinical.conditiondefinition.service.ConditionDefinitionDomainService;
import com.zxplat.domain.clinical.conditiondefinition.service.ConditionDefinitionQueryService;
import com.zxplat.domain.infrastructure.integration.insurance.benefit.InsuranceRegisterBenefitQueryExtension;
import com.zxplat.domain.infrastructure.integration.insurance.benefit.ExtensionIn.InsuranceOpspFilingQueryExtensionIn;
import com.zxplat.domain.infrastructure.integration.insurance.benefit.ExtensionOut.InsuranceOpspFilingEncounterSnapshot;
import com.zxplat.domain.infrastructure.integration.insurance.benefit.ExtensionOut.InsuranceOpspFilingLineSnapshot;
import com.zxplat.domain.infrastructure.integration.insurance.benefit.ExtensionOut.InsuranceOpspFilingQueryExtensionOut;
import com.zxplat.domain.custom.conditiontemplate.command.BindConditionTemplateCommand;
import com.zxplat.domain.custom.conditiontemplate.command.UnbindConditionTemplateCommand;
import com.zxplat.domain.custom.conditiontemplate.queries.model.ConditionTemplateQueryModel;
import com.zxplat.domain.custom.conditiontemplate.service.ConditionTemplateDomainService;
import com.zxplat.domain.custom.conditiontemplate.service.ConditionTemplateQueryService;
import com.zxplat.domain.administration.encounter.queries.model.OutpatientEncounterContextQueryModel;
import com.zxplat.application.shared.encounter.EncounterContextReader;
import com.zxplat.application.shared.diagnosis.EncounterDiagnosisReferenceSupport;
import com.zxplat.application.shared.diagnosis.EncounterDiagnosisSnapshotAssembler;
import com.zxplat.application.outpatient.doctorstation.diagnosis.support.PatientHistoryCommonDiagnosisAssembler;
import com.zxplat.application.outpatient.doctorstation.diagnosis.support.PatientHistoryCommonDiagnosisRow;
import com.zxplat.domain.share.constants.DoctorStationDiagnosisConstants;
import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.domain.share.constants.TerminologyCodeSystemNameConstants;
import com.zxplat.domain.share.enums.ConditionTemplateBinding;
import com.zxplat.domain.share.enums.EncounterClass;
import com.zxplat.domain.share.enums.YbMedType;
import com.zxplat.domain.terminology.service.CodeSystemQueryService;
import com.zxplat.framework.core.application.session.CurrentOperatorResolver;
import com.zxplat.framework.core.application.dto.OptionDto;
import com.zxplat.framework.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.zxplat.framework.core.application.util.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * 门诊医生站诊断录入编排：就诊诊断快照、常用树、慢特病备案导入候选、定义检索与保存/删除。
 *
 * <p>对应 AS-OP-DOR-002 / AS-SDD：IF-DOR-06/06A/06B/07/08/08A/09；常用维护 list/unbind 为医生站轻量维护。</p>
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
    /** 慢病备案未匹配点击补录：仅写 chronic_spec。 */
    private final ConditionDefinitionDomainService conditionDefinitionDomainService;
    private final ConditionTemplateQueryService conditionTemplateQueryService;
    /** 个人/科室常用模板写侧（IF-DOR-08A）。 */
    private final ConditionTemplateDomainService conditionTemplateDomainService;
    private final EncounterDomainService encounterDomainService;
    private final CurrentManagedOrganizationIdsResolver managedOrganizationIdsResolver;
    private final CurrentPractitionerResolver currentPractitionerResolver;
    /** 登录用户个人模板读路径须用 {@code zxplat_users.id}，与医务人员模板主键分离。 */
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
        // 聚合四类列表 + 门诊诊断类型下拉（仅读）。
        return this.doctorStationDiagnosisMapper.toCommonDiagnosesResponse(
            history, doctorRows, personalRows, orgRows, this.buildOutpatientMedicalTypeOptions());
    }

    /**
     * 门诊诊断表「诊断类型」下拉：YbMedType 门诊向常用码。
     *
     * @return 非空选项列表（value=code，label=display）
     */
    private List<OptionDto> buildOutpatientMedicalTypeOptions() {
        List<YbMedType> items = new ArrayList<>();
        items.add(YbMedType.GENERAL_OUTPATIENT);
        items.add(YbMedType.EMERGENCY);
        items.add(YbMedType.CHRONIC_SPECIAL_DISEASE_OUTPATIENT);
        items.add(YbMedType.CHRONIC_DISEASE_OUTPATIENT);
        items.add(YbMedType.SPECIAL_DISEASE_OUTPATIENT);
        items.add(YbMedType.TRADITIONAL_CHINESE_MEDICINE_OUTPATIENT);
        items.add(YbMedType.SPECIAL_MEDICATION);
        items.add(YbMedType.MATERNITY_OUTPATIENT);
        items.add(YbMedType.ACCIDENTAL_INJURY_OUTPATIENT);
        List<OptionDto> options = new ArrayList<>();
        for (YbMedType item : items) {
            options.add(new OptionDto(item.getSortOrder(), item.getCode(), item.getDisplay()));
        }
        return options;
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
        // Gate：未装配医保模块时隐藏入口，禁止发起 5302A（BR-DOD-012）。
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
            queryCommand.setInsurancePoolAreaCode(filingCtx.getInsurancePoolAreaCode());
            InsuranceOpspFilingQueryExtensionOut filingOut =
                this.registerBenefitQueryExtension.queryOpspFiling(queryCommand);
            // 平台返回空：展示空慢病备案树，不报错。
            if (filingOut != null && filingOut.getFilingLines() != null) {
                filingLines = filingOut.getFilingLines();
            }
        } catch (RuntimeException ex) {
            // EX-DOD-106：平台失败弱提示，不阻断诊断 Tab。
            log.warn(
                "慢特病备案查询失败 encounterId={} contractId={} psnNo={}: {}",
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
     * 点击未匹配慢病备案：幂等补录门诊慢特病种目录（仅 {@code chronic_spec}）并返回可导入定义。
     *
     * <p>对应 AS-OP-DOR-002 IF-DOR-06C / BR-DOD-013。不修改其它来源诊断目录；歧义/过期仍拒绝。</p>
     *
     * @param request 就诊/患者与备案码
     * @return 可导入定义
     * @throws BusinessException 门控失败、备案无效或不存在、歧义匹配
     */
    public EnsureOpspFilingDiagnosisResponse ensureOpspFilingDiagnosis(EnsureOpspFilingDiagnosisRequest request) {
        // Gate：CHS 未启用时禁止补录，避免医生站绕过目录维护写主数据。
        if (!this.registerBenefitQueryExtension.isEnabled()) {
            throw new BusinessException(ErrorCodeConstants.CONDITION_DEFINITION_INVALID_ARGUMENT, "医保模块未启用，无法补录慢病目录");
        }
        // Pre：门诊就诊上下文与串患者门禁。
        OutpatientEncounterContextQueryModel snapshot =
            this.EncounterContextReader.mustGetOutpatientContextByEncounterId(request.getEncounterId());
        this.doctorStationDiagnosisMapper.validateEncounterPatientGate(snapshot, request.getPatientId());
        // Gate：非门诊就诊不允许走慢病备案导入。
        if (!EncounterClass.AMB.getCode().equals(snapshot.getClassEnum())) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_STATUS, "当前就诊类别不允许导入慢病备案诊断");
        }
        String opspCode = StringUtils.trimToNull(request.getOpspDiseCode());
        // Gate：无备案码无法定位 5302A 行与目录主键。
        if (opspCode == null) {
            throw new BusinessException(ErrorCodeConstants.CONDITION_DEFINITION_INVALID_ARGUMENT, "慢特病种编码不能为空");
        }
        // Pre：解析 contractId/psnNo；自费或未读卡时无法核对备案。
        InsuranceOpspFilingEncounterSnapshot filingCtx =
            this.registerBenefitQueryExtension.resolveOpspFilingEncounter(request.getEncounterId());
        if (filingCtx == null
            || filingCtx.getContractId() == null
            || StringUtils.isBlank(filingCtx.getPsnNo())) {
            throw new BusinessException(ErrorCodeConstants.CONDITION_DEFINITION_INVALID_ARGUMENT, "当前就诊无有效医保人员上下文");
        }
        // Pre：再查慢特病备案（5301 主查），确认该码属于本患者有效备案（防前端伪造码写目录）。
        InsuranceOpspFilingLineSnapshot filingLine =
            this.findOpspFilingLineOrThrow(request.getEncounterId(), filingCtx, opspCode);
        // Gate：过期备案禁止补录与导入。
        if (!this.isOpspFilingValid(filingLine.getEnddate(), LocalDate.now())) {
            throw new BusinessException(ErrorCodeConstants.CONDITION_DEFINITION_INVALID_STATUS, "备案已过期，不可导入");
        }
        String opspName = StringUtils.trimToNull(filingLine.getOpspDiseName());
        // Pre：平台名为空时回退请求名，仍空则无法落目录。
        if (opspName == null) {
            opspName = StringUtils.trimToNull(request.getOpspDiseName());
        }
        if (opspName == null) {
            throw new BusinessException(ErrorCodeConstants.CONDITION_DEFINITION_INVALID_ARGUMENT, "慢特病种名称不能为空");
        }
        // Pre：已能唯一匹配则直接复用，避免多余写库。
        OpspDiseCodeDefinitionMatchQueryModel existing = this.resolveUniqueOpspMatchOrThrow(opspCode);
        boolean catalogWritten = false;
        if (existing == null || existing.getConditionDefinitionId() == null) {
            // Cmd：仅幂等写入/启用 chronic_spec，不改其它来源目录。
            EnsureChronicSpecOpspResult ensureResult =
                this.conditionDefinitionDomainService.ensureActiveChronicSpecOpspDisease(opspCode, opspName);
            catalogWritten = ensureResult.isCatalogWritten();
            existing = this.resolveUniqueOpspMatchOrThrow(opspCode);
        }
        // Gate：补录后仍无唯一启用目录，失败给信息科处理。
        if (existing == null || existing.getConditionDefinitionId() == null) {
            throw new BusinessException(ErrorCodeConstants.CONDITION_DEFINITION_NOT_FOUND, "慢病目录补录失败，请联系信息科");
        }
        // Asm：装配可导入定义供前端走 IF-DOR-08。
        String definitionName = existing.getName();
        if (StringUtils.isBlank(definitionName)) {
            definitionName = opspName;
        }
        EnsureOpspFilingDiagnosisResponse response = new EnsureOpspFilingDiagnosisResponse();
        response.setOpspDiseCode(opspCode);
        response.setOpspDiseName(opspName);
        response.setConditionDefinitionId(existing.getConditionDefinitionId());
        response.setConditionCode(existing.getConditionCode());
        response.setConditionDefinitionName(definitionName);
        response.setDiagnosisKind(
            DoctorStationDiagnosisConstants.resolveDiagnosisKindFromSource(existing.getSourceEnum()));
        response.setMedicalTypeCode(this.resolveOpspImportMedicalTypeCode(filingLine.getMedicalTypeCode()));
        response.setCreated(catalogWritten);
        return response;
    }

    /**
     * 备案导入医疗类别：无标签默认门诊慢病；合法 140104/140201 原样。
     *
     * @param medicalTypeCode 平台/打标结果
     * @return 非空 140104 或 140201
     */
    private String resolveOpspImportMedicalTypeCode(String medicalTypeCode) {
        String code = StringUtils.trimToNull(medicalTypeCode);
        if (YbMedType.SPECIAL_DISEASE_OUTPATIENT.getCode().equals(code)) {
            return YbMedType.SPECIAL_DISEASE_OUTPATIENT.getCode();
        }
        return YbMedType.CHRONIC_DISEASE_OUTPATIENT.getCode();
    }

    /**
     * 从备案查询结果中定位指定备案码行；平台失败或缺失时抛业务异常。
     *
     * @param encounterId 就诊标识（仅用于日志）
     * @param filingCtx   已解析的参保上下文
     * @param opspCode    归一化备案码
     * @return 非空备案行
     * @throws BusinessException 平台失败、备案中无该码
     */
    private InsuranceOpspFilingLineSnapshot findOpspFilingLineOrThrow(
        Long encounterId,
        InsuranceOpspFilingEncounterSnapshot filingCtx,
        String opspCode) {
        try {
            InsuranceOpspFilingQueryExtensionIn queryCommand = new InsuranceOpspFilingQueryExtensionIn();
            queryCommand.setContractId(filingCtx.getContractId());
            queryCommand.setPsnNo(filingCtx.getPsnNo().trim());
            queryCommand.setInsurancePoolAreaCode(filingCtx.getInsurancePoolAreaCode());
            InsuranceOpspFilingQueryExtensionOut filingOut =
                this.registerBenefitQueryExtension.queryOpspFiling(queryCommand);
            if (filingOut != null && filingOut.getFilingLines() != null) {
                for (InsuranceOpspFilingLineSnapshot line : filingOut.getFilingLines()) {
                    if (line != null && opspCode.equals(StringUtils.trimToNull(line.getOpspDiseCode()))) {
                        return line;
                    }
                }
            }
        } catch (RuntimeException ex) {
            log.warn(
                "慢特病备案核对失败 encounterId={} opspDiseCode={}: {}",
                encounterId,
                opspCode,
                ex.getMessage());
            throw new BusinessException(ErrorCodeConstants.CONDITION_DEFINITION_INVALID_ARGUMENT, "暂时无法核对慢病备案，请稍后重试");
        }
        throw new BusinessException(ErrorCodeConstants.CONDITION_DEFINITION_NOT_FOUND, "患者慢病备案中不存在该病种");
    }

    /**
     * 按备案码解析唯一启用目录匹配；歧义时拒绝静默择一。
     *
     * @param opspCode 归一化备案码
     * @return 唯一匹配投影；无命中返回 {@code null}
     * @throws BusinessException 同优先级多条目录
     */
    private OpspDiseCodeDefinitionMatchQueryModel resolveUniqueOpspMatchOrThrow(String opspCode) {
        List<OpspDiseCodeDefinitionMatchQueryModel> matches =
            this.conditionDefinitionQueryService.listActiveDoctorStationRowsByOpspDiseCodes(List.of(opspCode));
        if (matches == null || matches.isEmpty()) {
            return null;
        }
        OpspDiseCodeDefinitionMatchQueryModel match = matches.get(0);
        // Gate：歧义禁止静默导入，医生改手工检索。
        if (match != null && match.isAmbiguous()) {
            throw new BusinessException(
                ErrorCodeConstants.CONDITION_DEFINITION_INVALID_ARGUMENT, "院内目录存在多条匹配，请手工检索选择");
        }
        return match;
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
            com.zxplat.domain.administration.encounter.command.BatchUpsertEncounterDiagnosisAssociationCommand assocCmd =
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
        // Cmd：个人常用 → PERSONAL + zxplat_users.id（与 IF-DOR-06A 读侧一致）
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
     * 列出当前登录用户可维护的个人/科室常用诊断（含模板行主键）。
     *
     * <p>个人 = PERSONAL + 当前用户；科室 = DEPARTMENT + 当前登录科室。不含医师维度「常用」与全院绑定。</p>
     *
     * @param request 空请求体即可（归属由会话解析）
     * @return 个人列表与科室列表
     * @throws BusinessException 未登录时
     */
    public ListMaintainableCommonDiagnosesResponse listMaintainableCommonDiagnoses(
        ListMaintainableCommonDiagnosesRequest request) {
        // Gate：须已登录，否则无法解析个人归属
        Long operatorId = this.currentOperatorResolver.findOperatorIdOrNull();
        if (operatorId == null) {
            throw new BusinessException(ErrorCodeConstants.CONDITION_TEMPLATE_INVALID_ARGUMENT, "请先登录后再操作");
        }
        // Pre：术语来源展示映射 + 当前登录科室
        Map<String, String> sourceLabels = this.buildSourceLabelMap();
        Long orgId = this.managedOrganizationIdsResolver.resolveOrganizationUnitIdForQuery();
        // Asm：个人常用
        List<ConditionTemplateQueryModel> personalTemplates = this.conditionTemplateQueryService.listByOwnerAndType(
            operatorId, ConditionTemplateBinding.PERSONAL.getCode());
        List<MaintainableCommonDiagnosisRowDto> personalRows =
            this.mapTemplatesToMaintainableRows(personalTemplates, sourceLabels);
        // Asm：科室常用（无登录科室时返回空列表，不抛错，便于前端仍可维护个人）
        List<MaintainableCommonDiagnosisRowDto> departmentRows = new ArrayList<>();
        if (orgId != null) {
            List<ConditionTemplateQueryModel> orgTemplates = this.conditionTemplateQueryService.listByOwnerAndType(
                orgId, ConditionTemplateBinding.DEPARTMENT.getCode());
            departmentRows = this.mapTemplatesToMaintainableRows(orgTemplates, sourceLabels);
        }
        ListMaintainableCommonDiagnosesResponse response = new ListMaintainableCommonDiagnosesResponse();
        response.setPersonalList(personalRows);
        response.setDepartmentList(departmentRows);
        return response;
    }

    /**
     * 解绑个人/科室常用诊断模板行。
     *
     * <p>仅允许解绑归属当前用户或当前登录科室的行；禁止借本接口操作他人/他科/全院绑定。</p>
     *
     * @param request 模板行主键列表
     * @return 解绑结果
     * @throws BusinessException 未登录、主键为空、行不存在或无权解绑时
     */
    public UnbindCommonDiagnosisResponse unbindCommonDiagnosis(UnbindCommonDiagnosisRequest request) {
        // Gate：主键列表非空
        if (request == null || request.getTemplateIds() == null || request.getTemplateIds().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.CONDITION_TEMPLATE_INVALID_ARGUMENT, "模板行主键不能为空");
        }
        Long operatorId = this.currentOperatorResolver.findOperatorIdOrNull();
        if (operatorId == null) {
            throw new BusinessException(ErrorCodeConstants.CONDITION_TEMPLATE_INVALID_ARGUMENT, "请先登录后再操作");
        }
        Long orgId = this.managedOrganizationIdsResolver.resolveOrganizationUnitIdForQuery();
        // Pre：批量加载待解绑行，校验归属（个人→当前用户；科室→当前登录科室）
        List<ConditionTemplateQueryModel> ownedPersonal = this.conditionTemplateQueryService.listByOwnerAndType(
            operatorId, ConditionTemplateBinding.PERSONAL.getCode());
        Set<Long> allowedIds = new HashSet<>();
        for (ConditionTemplateQueryModel row : ownedPersonal) {
            if (row.getId() != null) {
                allowedIds.add(row.getId());
            }
        }
        if (orgId != null) {
            List<ConditionTemplateQueryModel> ownedDept = this.conditionTemplateQueryService.listByOwnerAndType(
                orgId, ConditionTemplateBinding.DEPARTMENT.getCode());
            for (ConditionTemplateQueryModel row : ownedDept) {
                if (row.getId() != null) {
                    allowedIds.add(row.getId());
                }
            }
        }
        List<Long> toUnbind = new ArrayList<>();
        for (Long templateId : request.getTemplateIds()) {
            if (templateId == null) {
                throw new BusinessException(ErrorCodeConstants.CONDITION_TEMPLATE_INVALID_ARGUMENT, "模板行主键不能为空");
            }
            // 非本用户/本科室绑定：拒绝，避免越权删他人常用
            if (!allowedIds.contains(templateId)) {
                throw new BusinessException(ErrorCodeConstants.CONDITION_TEMPLATE_INVALID_ARGUMENT, "无权解绑该常用诊断");
            }
            toUnbind.add(templateId);
        }
        // Cmd：领域批量软删
        UnbindConditionTemplateCommand command = new UnbindConditionTemplateCommand();
        command.setOperatorId(operatorId);
        List<UnbindConditionTemplateCommand.UnbindConditionTemplateItemCommand> items = new ArrayList<>();
        for (Long templateId : toUnbind) {
            UnbindConditionTemplateCommand.UnbindConditionTemplateItemCommand item =
                new UnbindConditionTemplateCommand.UnbindConditionTemplateItemCommand();
            item.setTemplateId(templateId);
            items.add(item);
        }
        command.setItems(items);
        this.conditionTemplateDomainService.unbindConditionTemplate(command);
        // Asm：成功响应
        UnbindCommonDiagnosisResponse response = new UnbindCommonDiagnosisResponse();
        response.setSuccess(Boolean.TRUE);
        response.setUnboundCount(toUnbind.size());
        response.setMessage("常用诊断已移除");
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
     * 将模板绑定列表解析为可维护常用诊断行（带回 templateId，保持模板顺序）。
     *
     * @param templates    模板只读行
     * @param sourceLabels 来源展示映射
     * @return 可维护行列表
     */
    private List<MaintainableCommonDiagnosisRowDto> mapTemplatesToMaintainableRows(
        List<ConditionTemplateQueryModel> templates,
        Map<String, String> sourceLabels) {
        List<MaintainableCommonDiagnosisRowDto> rows = new ArrayList<>();
        if (templates == null || templates.isEmpty()) {
            return rows;
        }
        List<Long> ids = new ArrayList<>();
        for (ConditionTemplateQueryModel template : templates) {
            if (template.getDefinitionId() != null) {
                ids.add(template.getDefinitionId());
            }
        }
        // 模板仅存 definitionId：一次性批量拉启用定义行
        List<DoctorStationConditionDefinitionRowModel> defs =
            this.conditionDefinitionQueryService.listActiveDoctorStationRowsByIds(ids);
        Map<Long, DoctorStationConditionDefinitionRowModel> byId = new HashMap<>();
        for (DoctorStationConditionDefinitionRowModel def : defs) {
            byId.put(def.getId(), def);
        }
        for (ConditionTemplateQueryModel template : templates) {
            DoctorStationConditionDefinitionRowModel def = byId.get(template.getDefinitionId());
            // 模板指向已停用/已删定义：仍展示编码占位，便于用户解绑脏数据
            MaintainableCommonDiagnosisRowDto row = new MaintainableCommonDiagnosisRowDto();
            row.setTemplateId(template.getId());
            row.setConditionDefinitionId(template.getDefinitionId());
            row.setSortWeight(template.getSortWeight());
            if (def != null) {
                CommonDiagnosisRowDto common = this.doctorStationDiagnosisMapper.toCommonDiagnosisRowDto(def, sourceLabels);
                row.setConditionCode(common.getConditionCode());
                row.setConditionDefinitionName(common.getConditionDefinitionName());
                row.setDiagnosisKind(common.getDiagnosisKind());
                row.setDiagnosisKindText(common.getDiagnosisKindText());
                row.setYbNo(common.getYbNo());
            } else {
                row.setConditionDefinitionName("（目录已停用或不存在）");
            }
            rows.add(row);
        }
        return rows;
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
            // 个人/科室常用：回填模板主键，供快捷树就地解绑
            row.setTemplateId(template.getId());
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
