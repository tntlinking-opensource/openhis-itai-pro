/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.financial.registration.service;

import com.zxplat.application.shared.option.OptionDtoAssembler;
import com.zxplat.application.shared.pagination.PageQueryBinder;
import com.zxplat.application.shared.pagination.PagedResultBuilder;
import com.zxplat.application.shared.validator.CurrentManagedOrganizationIdsResolver;
import com.zxplat.application.shared.validator.CurrentPractitionerResolver;
import com.zxplat.framework.core.application.paging.PageWindowGate;
import com.zxplat.application.shared.financial.PaymentChannelOptionProvider;
import com.zxplat.application.shared.validator.PaymentChannelGate;
import com.zxplat.application.inpatient.financial.registration.dto.AdmissionDiagnosisSubmitDto;
import com.zxplat.application.shared.inpatient.AdmissionDiagnosisSubmitGate;
import com.zxplat.application.inpatient.financial.registration.dto.PaymentDetailDto;
import com.zxplat.application.inpatient.financial.registration.dto.RegistrationQueueItemDto;
import com.zxplat.application.inpatient.financial.registration.dto.RegistrationEncounterDto;
import com.zxplat.application.inpatient.financial.registration.dto.RegistrationPatientDto;
import com.zxplat.application.shared.inpatient.InpatientInfoQueueDisplayAssembler;
import com.zxplat.application.inpatient.financial.registration.mapper.InpatientRegistrationMapper;
import com.zxplat.domain.administration.account.command.AdjustAdvanceDepositBalanceCommand;
import com.zxplat.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.zxplat.domain.administration.account.service.AccountDomainService;
import com.zxplat.application.inpatient.financial.registration.dto.AdmissionDepartmentBedOptionDto;
import com.zxplat.application.inpatient.financial.registration.dto.WardOptionDto;
import com.zxplat.application.inpatient.financial.registration.request.CancelRegistrationRequest;
import com.zxplat.application.inpatient.financial.registration.request.ChangeAdmissionDepartmentRequest;
import com.zxplat.application.inpatient.financial.registration.request.ListAdmissionDepartmentBedsRequest;
import com.zxplat.application.inpatient.financial.registration.request.ListAdmissionDepartmentWardsRequest;
import com.zxplat.application.inpatient.financial.registration.request.ListWardsByDepartmentRequest;
import com.zxplat.application.inpatient.financial.registration.request.LoadInpatientRegistrationPageInitRequest;
import com.zxplat.application.inpatient.financial.registration.request.LoadRegistrationContextRequest;
import com.zxplat.application.inpatient.financial.registration.request.LoadWardBedCapacityRequest;
import com.zxplat.application.inpatient.financial.registration.request.PageRegistrationQueueRequest;
import com.zxplat.application.inpatient.financial.registration.request.SubmitRegistrationRequest;
import com.zxplat.application.inpatient.financial.registration.request.SwitchInpatientToInsuranceRequest;
import com.zxplat.application.inpatient.financial.registration.request.SwitchInpatientToSelfPayRequest;
import com.zxplat.application.inpatient.financial.registration.response.CancelInsuranceInpatientAdmissionResponse;
import com.zxplat.application.inpatient.financial.registration.response.ChangeAdmissionDepartmentResponse;
import com.zxplat.application.inpatient.financial.registration.response.ListAdmissionDepartmentBedsResponse;
import com.zxplat.application.inpatient.financial.registration.response.CancelRegistrationResponse;
import com.zxplat.application.inpatient.financial.registration.response.ListWardsByDepartmentResponse;
import com.zxplat.application.inpatient.financial.registration.response.LoadInpatientRegistrationPageInitResponse;
import com.zxplat.application.inpatient.financial.registration.response.LoadRegistrationContextResponse;
import com.zxplat.application.inpatient.financial.registration.response.LoadWardBedCapacityResponse;
import com.zxplat.application.inpatient.financial.registration.response.PageRegistrationQueueResponse;
import com.zxplat.application.inpatient.financial.registration.response.SubmitRegistrationResponse;
import com.zxplat.application.inpatient.financial.registration.response.SwitchInpatientToInsuranceResponse;
import com.zxplat.application.inpatient.financial.registration.response.SwitchInpatientToSelfPayResponse;
import com.zxplat.application.inpatient.financial.registration.support.InpatientRegistrationInsuranceSwitchSupport;
import com.zxplat.application.shared.financial.EncounterChargeAccountOpener;
import com.zxplat.application.shared.financial.EncounterInsutypeDisplayAssembler;
import com.zxplat.application.shared.inpatient.EncounterInpatientAdmissionOrchestrator;
import com.zxplat.domain.administration.account.entity.Account;
import com.zxplat.domain.administration.account.service.AccountQueryService;
import com.zxplat.domain.administration.encounter.command.CancelInpatientAdmissionCommand;
import com.zxplat.domain.administration.encounter.command.CorrectInpatientAdmissionDepartmentCommand;
import com.zxplat.domain.administration.encounter.command.RegisterInpatientEncounterCommand;
import com.zxplat.domain.administration.encounter.command.SaveNurseStationPatientCommand;
import com.zxplat.domain.administration.encounter.queries.model.EncounterDetailQueryModel;
import com.zxplat.domain.administration.encounter.service.EncounterDomainService;
import com.zxplat.domain.administration.encounter.service.EncounterQueryService;
import com.zxplat.application.shared.encounter.EncounterContextReader;
import com.zxplat.domain.administration.location.command.OccupyBedCommand;
import com.zxplat.domain.administration.location.command.ReleaseBedCommand;
import com.zxplat.domain.administration.location.entity.Location;
import com.zxplat.domain.administration.location.service.LocationDomainService;
import com.zxplat.domain.administration.location.service.LocationQueryService;
import com.zxplat.domain.administration.organization.entity.Organization;
import com.zxplat.domain.administration.organization.service.OrganizationQueryService;
import com.zxplat.domain.administration.patient.command.ModifyPatientProfileCommand;
import com.zxplat.domain.administration.patient.command.RegisterPatientCommand;
import com.zxplat.domain.administration.patient.queries.model.PatientCoreQueryModel;
import com.zxplat.domain.administration.patient.queries.model.PatientDetailQueryModel;
import com.zxplat.domain.administration.patient.queries.model.PatientSimpleQueryModel;
import com.zxplat.domain.administration.patient.service.PatientDomainService;
import com.zxplat.domain.administration.patient.service.PatientQueryService;
import com.zxplat.domain.administration.practitioner.entity.Practitioner;
import com.zxplat.domain.administration.practitioner.service.PractitionerQueryService;
import com.zxplat.domain.administration.encounter.queries.model.EncounterParticipantQueryModel;
import com.zxplat.domain.share.constants.EncounterConstants;
import com.zxplat.domain.financial.contract.entity.Contract;
import com.zxplat.domain.financial.contract.queries.model.ContractDetailQueryModel;
import com.zxplat.domain.financial.contract.queries.model.ContractListItemQueryModel;
import com.zxplat.domain.financial.contract.service.ContractQueryService;
import com.zxplat.domain.financial.paymentreconciliation.command.CollectAdvanceDepositCommand;
import com.zxplat.domain.financial.paymentreconciliation.command.SettleInpatientPaymentCommand;
import com.zxplat.domain.financial.paymentreconciliation.queries.model.PaymentReconciliationQueryModel;
import com.zxplat.domain.financial.paymentreconciliation.service.PaymentReconciliationDomainService;
import com.zxplat.domain.financial.paymentreconciliation.service.PaymentReconciliationQueryService;
import com.zxplat.domain.share.enums.PaymentKind;
import com.zxplat.domain.share.enums.PaymentStatus;
import com.zxplat.domain.mrms.caserecord.service.CaseRecordDomainService;
import com.zxplat.domain.infrastructure.integration.financial.orchestration.PaymentReconciliationSettleOrchestration;
import com.zxplat.domain.infrastructure.integration.noop.IntegrationAfterCommitSupport;
import com.zxplat.domain.administration.encounter.queries.InpatientEncounterPageQuery;
import com.zxplat.domain.administration.encounter.queries.model.InpatientEncounterContextQueryModel;
import com.zxplat.domain.terminology.service.CodeSystemQueryService;
import com.zxplat.domain.share.constants.InpatientRegistrationConstants;
import com.zxplat.domain.share.utils.MonetaryAmountSupport;
import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.domain.share.constants.TerminologyCodeSystemNameConstants;
import com.zxplat.domain.share.enums.EncounterClass;
import com.zxplat.domain.share.enums.EncounterParticipantRole;
import com.zxplat.domain.share.enums.YbMedType;
import com.zxplat.domain.share.enums.EncounterStatus;
import com.zxplat.domain.share.enums.LocationForm;
import com.zxplat.domain.share.enums.LocationOperational;
import com.zxplat.domain.share.enums.LocationStatus;
import com.zxplat.domain.share.enums.OrganizationClassification;
import com.zxplat.domain.share.enums.PublicationStatus;
import com.zxplat.framework.core.application.dto.OptionDto;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.domain.infrastructure.integration.insurance.inpatient.InpatientChargeInsuranceExtension;
import com.zxplat.domain.infrastructure.integration.insurance.inpatient.ExtensionIn.InpatientChargeAdmissionExtensionIn;
import com.zxplat.domain.infrastructure.integration.insurance.inpatient.ExtensionIn.InpatientChargeAdmissionPlatformQueryExtensionIn;
import com.zxplat.domain.infrastructure.integration.insurance.inpatient.ExtensionIn.InpatientChargeEncounterExtensionIn;
import com.zxplat.domain.infrastructure.integration.insurance.inpatient.ExtensionIn.InpatientChargeRevokeFeeDetailExtensionIn;
import com.zxplat.domain.infrastructure.integration.insurance.inpatient.ExtensionOut.InpatientChargeAdmissionPlatformQueryExtensionOut;
import com.zxplat.domain.infrastructure.integration.insurance.inpatient.ExtensionOut.InpatientChargeAdmissionExtensionOut;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 住院登记应用服务（AS-IP-FIN-001，IF-IP-REG-01～09）。
 *
 * <p>职责边界：</p>
 * <ul>
 *   <li>Gate：分页窗口、登记状态筛选、科室/病区/预交金明细与余额门禁。</li>
 *   <li>Pre：患者解析/建档、住院上下文与主数据加载、医保入院可选衔接。</li>
 *   <li>Cmd：开立/完成住院就诊、收取预交金、取消登记与医保撤销；登记成功后幂等建病案主档。</li>
 *   <li>Asm：经 {@link InpatientRegistrationMapper} 装配队列/上下文/登记响应。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InpatientRegistrationAppService {

    private final PatientDomainService patientDomainService;
    private final PatientQueryService patientQueryService;
    private final CurrentManagedOrganizationIdsResolver managedOrganizationIdsResolver;
    private final CurrentPractitionerResolver currentPractitionerResolver;
    private final PageWindowGate pageWindowGate;
    private final PaymentChannelGate paymentChannelGate;
    private final PaymentChannelOptionProvider paymentChannelOptionProvider;
    private final OrganizationQueryService organizationQueryService;
    private final LocationQueryService locationQueryService;
    private final LocationDomainService locationDomainService;
    private final ContractQueryService contractQueryService;
    private final EncounterDomainService encounterDomainService;
    private final EncounterInpatientAdmissionOrchestrator encounterInpatientAdmissionOrchestrator;
    private final AdmissionDiagnosisSubmitGate admissionDiagnosisSubmitGate;
    private final EncounterQueryService encounterQueryService;
    private final EncounterContextReader EncounterContextReader;
    private final AccountQueryService accountQueryService;
    private final AccountDomainService accountDomainService;
    private final PaymentReconciliationDomainService paymentReconciliationDomainService;
    private final PaymentReconciliationQueryService paymentReconciliationQueryService;
    private final PaymentReconciliationSettleOrchestration paymentReconciliationSettleOrchestration;
    private final InpatientRegistrationMapper inpatientRegistrationMapper;
    private final InpatientInfoQueueDisplayAssembler inpatientInfoQueueDisplayAssembler;
    private final CodeSystemQueryService codeSystemQueryService;
    private final PractitionerQueryService practitionerQueryService;

    private final InpatientChargeInsuranceExtension inpatientChargeInsuranceExtension;

    /** 自费转医保：未结费用切医保账户。 */
    private final InpatientRegistrationInsuranceSwitchSupport inpatientRegistrationInsuranceSwitchSupport;

    /** 就诊双账户幂等开立（转医保时补开医保户）。 */
    private final EncounterChargeAccountOpener encounterChargeAccountOpener;

    /** 就诊险种（职工/居民）批量装配：登记队列与弹窗回显。 */
    private final EncounterInsutypeDisplayAssembler encounterInsutypeDisplayAssembler;

    /** 住院费用读侧（转医保门禁：已结算占用）。 */
    private final ChargeItemQueryService chargeItemQueryService;

    /** 入院登记成功后幂等建病案主档（发号 + autoFill）。 */
    private final CaseRecordDomainService caseRecordDomainService;

    /**
     * IF-IP-REG-00：住院登记页初始化。
     *
     * @param request 占位请求
     * @return 用户可选支付渠道下拉
     * @throws BusinessException 初始化失败时抛出 EX-IP-REG-101
     */
    public LoadInpatientRegistrationPageInitResponse loadInpatientRegistrationPageInit(
        LoadInpatientRegistrationPageInitRequest request) {
        try {
            // Pre：用户可选支付渠道与住院结算/预交金口径对齐（ADR-PAY-001）。
            List<OptionDto> paymentChannels = paymentChannelOptionProvider.loadUserSelectableOptions();
            // Asm：Mapper 装配支付渠道下拉。
            return inpatientRegistrationMapper.toLoadInpatientRegistrationPageInitResponse(paymentChannels);
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new BusinessException(
                InpatientRegistrationConstants.EX_IP_REG_101,
                "初始化失败，请稍后重试");
        }
    }

    /**
     * 分页查询住院登记待办队列（IF-IP-REG-01）。
     *
     * @param request 分页与筛选条件（登记状态、身份证、关键字、科室、病区、入院日）
     * @return 登记队列分页结果
     * @throws BusinessException 分页或筛选参数非法时抛出（EX-IP-REG-101）
     */
    public PageRegistrationQueueResponse pageInpatientRegistrationQueue(PageRegistrationQueueRequest request) {
        // Gate：分页窗口须为正，登记状态筛选码须合法。
        pageWindowGate.requirePositivePageWindow(
            request,
            InpatientRegistrationConstants.EX_IP_REG_101,
            "分页参数不合法，请检查页码与每页条数");
        this.validateRegistrationListStatuses(request.getRegistrationListStatuses());
        if (StringUtils.isNotBlank(request.getRegistrationListStatus())) {
            this.validateRegistrationListStatuses(List.of(request.getRegistrationListStatus().trim()));
        }
        // Pre：绑定分页参数与登记状态筛选。
        InpatientEncounterPageQuery query = new InpatientEncounterPageQuery();
        PageQueryBinder.bind(request, query);
        if (request.getRegistrationListStatuses() != null && !request.getRegistrationListStatuses().isEmpty()) {
            query.setRegistrationListStatuses(
                this.normalizeRegistrationListStatuses(request.getRegistrationListStatuses()));
        } else if (StringUtils.isNotBlank(request.getRegistrationListStatus())) {
            query.setRegistrationListStatuses(List.of(request.getRegistrationListStatus().trim()));
        }
        // Pre：科室 / 病区 / 入院日闭区间下推读侧（与关键字、登记状态 AND）。
        if (request.getOrganizationId() != null) {
            query.setOrganizationId(request.getOrganizationId());
        }
        if (request.getWardId() != null) {
            query.setWardId(request.getWardId());
        }
        if (request.getAdmissionDateFrom() != null) {
            query.setAdmissionDateFrom(request.getAdmissionDateFrom());
        }
        if (request.getAdmissionDateTo() != null) {
            query.setAdmissionDateTo(request.getAdmissionDateTo());
        }
        // Pre：按身份证精确命中患者；未命中则直接返回空页，避免全表扫描。
        if (StringUtils.isNotBlank(request.getIdCard())) {
            PatientSimpleQueryModel matched = patientQueryService.findPatientByIdCard(request.getIdCard().trim());
            if (matched == null || matched.getId() == null) {
                // Asm：身份证无对应患者时返回空分页，前端展示「无数据」。
                return PagedResultBuilder.build(
                    new PageRegistrationQueueResponse(),
                    new ArrayList<>(),
                    0L,
                    request.getPageNo(),
                    request.getPageSize());
            }
            query.setPatientId(matched.getId());
        }
        // Pre：拉取住院登记读模型分页并批量解析源门诊就诊与展示覆盖层。
        Page<InpatientEncounterContextQueryModel> page = EncounterContextReader.pageInpatientRegQueue(query);
        Map<Long, Long> ambEncounterIdByEncounterId =
            this.loadAmbEncounterIdIndex(page.getContent());
        Set<Long> encounterIds = new LinkedHashSet<>();
        for (InpatientEncounterContextQueryModel row : page.getContent()) {
            if (row != null && row.getEncounterId() != null) {
                encounterIds.add(row.getEncounterId());
            }
        }
        Map<Long, String> contractNoByEncounterId =
            accountQueryService.mapPreferredContractNoByEncounterIds(encounterIds);
        Map<Long, InpatientInfoQueueDisplayAssembler.InpatientQueueDisplayOverlay> displayOverlay =
            inpatientInfoQueueDisplayAssembler.buildOverlay(page.getContent(), contractNoByEncounterId);
        Map<String, String> inWayLabelByCode = this.buildTerminologyLabelByCode(
            TerminologyCodeSystemNameConstants.IN_WAY_CODE);
        // Asm：映射队列行并封装分页响应。
        List<RegistrationQueueItemDto> rows =
            inpatientRegistrationMapper.toRegistrationQueueItemDtoList(
                page.getContent(),
                ambEncounterIdByEncounterId,
                displayOverlay,
                inWayLabelByCode);
        // Pre：批量回填医保险种类别（职工/居民），供列表「医保类别」列展示。
        this.enrichRegistrationQueueInsutype(rows, encounterIds);
        this.enrichRegistrationQueueInsurance(rows);
        this.enrichRegistrationQueueSwitchToInsurance(rows);
        this.enrichRegistrationQueueSwitchToSelfPay(rows);
        return inpatientRegistrationMapper.toPageRegistrationQueueResponse(rows, page);
    }

    /**
     * 加载住院登记表单上下文（IF-IP-REG-02）：患者/就诊快照与费用性质、科室下拉。
     *
     * @param request 就诊 ID 与科室（可空）
     * @return 患者、就诊与费用性质下拉
     * @throws BusinessException 就诊不存在或非住院类时抛出（EX-IP-REG-102）
     */
    public LoadRegistrationContextResponse loadInpatientRegistrationContext(LoadRegistrationContextRequest request) {
        RegistrationPatientDto patientDto = null;
        RegistrationEncounterDto encounterDto = null;
        Map<String, String> inWayLabelByCode = this.buildTerminologyLabelByCode(
            TerminologyCodeSystemNameConstants.IN_WAY_CODE);
        Map<String, String> admitSourceLabelByCode = this.buildTerminologyLabelByCode(
            TerminologyCodeSystemNameConstants.ADMIT_SOURCE_CODE);
        List<OptionDto> inWayCodeOptions = codeSystemQueryService.getOptionListByCodeSystemName(
            TerminologyCodeSystemNameConstants.IN_WAY_CODE);
        List<OptionDto> admitSourceCodeOptions = codeSystemQueryService.getOptionListByCodeSystemName(
            TerminologyCodeSystemNameConstants.ADMIT_SOURCE_CODE);
        List<OptionDto> admCondCodeOptions = codeSystemQueryService.getOptionListByCodeSystemName(
            TerminologyCodeSystemNameConstants.ADM_COND_CODE);
        // Pre：有就诊 ID 时加载住院上下文与患者主档快照。
        if (request.getEncounterId() != null) {
            InpatientEncounterContextQueryModel inpatientRow =
                this.mustGetInpatientContextForRegistration(request.getEncounterId());
            EncounterDetailQueryModel encounter = encounterQueryService.mustGetEncounterOfClass(
                request.getEncounterId(),
                EncounterClass.IMP.getCode(),
                InpatientRegistrationConstants.EX_IP_REG_102,
                "就诊记录不是住院类别，无法办理住院登记");
            encounterDto = inpatientRegistrationMapper.toRegistrationEncounterDto(
                inpatientRow,
                encounter,
                inWayLabelByCode,
                admitSourceLabelByCode);
            // Pre：费用性质回显 — 优先医保账户合同，否则个人现金户（与护士站/预交金卡片同口径）。
            if (encounterDto != null) {
                encounterDto.setContractId(
                    this.resolvePreferredContractIdForEncounter(request.getEncounterId()));
            }
            // Pre：医保险种类别回显（职工/居民），登记弹窗「医保类别」只读。
            if (encounterDto != null) {
                encounterDto.setInsutype(this.resolveInsutypeForEncounter(request.getEncounterId()));
            }
            // Pre：医生办理意向已由 Mapper 从就诊头/上下文写入 chargeIntent（BR-IP-REG-024），仅提示款台。
            // Pre：门诊转住院意向预交金 DRAFT 回显总额，供收费员确认或改写（BR-IP-REG-021）。
            if (encounterDto != null) {
                this.fillIntentAdvanceDepositDraft(encounterDto, request.getEncounterId());
            }
            // Pre：门诊转住院时从源门诊就诊解析接诊医生，登记弹窗只读展示。
            if (encounterDto != null && encounterDto.getAmbEncounterId() != null) {
                encounterDto.setOutpatientDoctorName(
                    this.resolveOutpatientDoctorName(encounterDto.getAmbEncounterId()));
            }
            PatientDetailQueryModel patientDetail =
                patientQueryService.loadPatientDetail(inpatientRow.getPatientId());
            if (patientDetail != null && patientDetail.getPatient() != null) {
                // Asm：主档含婚姻/职业/现住址，供登记弹窗回显
                patientDto = inpatientRegistrationMapper.toRegistrationPatientDto(
                    patientDetail.getPatient());
            }
        }
        // Pre：加载费用性质与住院科室下拉选项。
        Page<ContractListItemQueryModel> contractPage =
            contractQueryService.listContracts(request.getOrganizationId(), Pageable.unpaged());
        List<OptionDto> contractOptions =
            inpatientRegistrationMapper.toContractOptionList(contractPage.getContent());
        // 入院科室：仅类别=住院的启用科室，与病区挂靠口径一致。
        List<Organization> inpatientDepartments = organizationQueryService.listOrganizationsForInpatientAdmission();
        List<OptionDto> organizationOptions = OptionDtoAssembler.toOptions(
            inpatientDepartments, Organization::getId, Organization::getName, 0L);
        // Gate：非待登记（PLANNED）打开弹窗为只读查看，防止误改已登记/在院/已结算等信息。
        boolean readOnly = false;
        if (encounterDto != null) {
            String statusEnum = encounterDto.getStatusEnum();
            if (!EncounterStatus.PLANNED.getCode().equals(statusEnum)) {
                readOnly = true;
            }
        }
        boolean switchToInsuranceEnabled = false;
        boolean switchToSelfPayEnabled = false;
        if (request.getEncounterId() != null && encounterDto != null) {
            boolean platformAdmitted = this.isInsurancePlatformAdmitted(request.getEncounterId());
            switchToInsuranceEnabled = this.computeSwitchToInsuranceEnabled(
                request.getEncounterId(),
                encounterDto.getStatusEnum(),
                platformAdmitted,
                null);
            switchToSelfPayEnabled = this.computeSwitchToSelfPayEnabled(
                request.getEncounterId(),
                encounterDto.getStatusEnum(),
                platformAdmitted,
                null);
        }
        // Asm：装配登记上下文响应。
        return inpatientRegistrationMapper.toLoadRegistrationContextResponse(
            patientDto,
            encounterDto,
            contractOptions,
            organizationOptions,
            inWayCodeOptions,
            admitSourceCodeOptions,
            admCondCodeOptions,
            readOnly,
            switchToInsuranceEnabled,
            switchToSelfPayEnabled);
    }

    /**
     * 将术语下拉选项转为 code → 展示文案索引，供 Mapper 回填列表/详情。
     *
     * @param codeSystemName 术语编码体系名称
     * @return 标签索引
     */
    private Map<String, String> buildTerminologyLabelByCode(String codeSystemName) {
        List<OptionDto> options = codeSystemQueryService.getOptionListByCodeSystemName(codeSystemName);
        Map<String, String> labelByCode = new HashMap<>();
        if (options == null) {
            return labelByCode;
        }
        for (OptionDto option : options) {
            if (option == null || option.getValue() == null) {
                continue;
            }
            String code = String.valueOf(option.getValue()).trim();
            if (code.isEmpty()) {
                continue;
            }
            labelByCode.put(code, option.getLabel());
        }
        return labelByCode;
    }

    /**
     * 提交住院正式登记（IF-IP-REG-03）：直登或有申请登记，可选预交金与医保入院。
     *
     * @param request 登记表单
     * @return 登记结果（含可选预交金支付单与医保就诊 ID）
     * @throws BusinessException 必填缺失、主数据无效、状态不允许或收款失败时抛出
     */
    public SubmitRegistrationResponse submitInpatientRegistration(SubmitRegistrationRequest request) {
        // Gate：登记必填项、入院诊断与预交金明细门禁。
        this.validateSubmitRegistrationRequest(request);
        // Pre：校验费用性质、科室与病区归属。
        ContractDetailQueryModel contract = contractQueryService.findContractById(request.getContractId());
        if (contract == null || StringUtils.isBlank(contract.getBusNo())) {
            throw new BusinessException(
                InpatientRegistrationConstants.EX_IP_REG_002,
                "费用性质无效，请重新选择");
        }
        Organization organization =
            organizationQueryService.loadOrganizationByIdForDisplay(request.getOrganizationId());
        if (organization == null) {
            throw new BusinessException(
                InpatientRegistrationConstants.EX_IP_REG_002,
                "住院科室无效，请重新选择");
        }
        this.assertWardBelongsToOrganization(request.getOrganizationId(), request.getWardId());
        Long registrarId = this.resolveRegistrarId(request.getOperatorId());
        // Pre：患者分流（复用/身份证命中/新建）并按需更新主档。
        Long patientId = this.resolvePatientIdForSubmit(request);
        this.updatePatientProfileIfRequested(request, patientId);
        Long encounterId = request.getEncounterId();
        // Cmd：组装住院登记命令并区分直登与有申请登记路径。
        RegisterInpatientEncounterCommand registerCommand =
            inpatientRegistrationMapper.toRegisterInpatientEncounterCommand(
                request, encounterId, registrarId, contract.getBusNo());
        if (encounterId == null) {
            // Cmd：直登路径开立新住院就诊。
            registerCommand.setPatientId(patientId);
            encounterId = encounterDomainService.registerDirectInpatientEncounter(registerCommand);
            for (RegisterInpatientEncounterCommand.RegisterInpatientEncounterItemCommand item
                : registerCommand.resolveItems()) {
                item.setEncounterId(encounterId);
            }
        } else {
            // Gate：有申请时登记前须仍为 PLANNED，防止重复登记或状态漂移。
            EncounterDetailQueryModel encounterBeforeRegister = encounterQueryService.mustGetEncounterOfClass(
                encounterId,
                EncounterClass.IMP.getCode(),
                InpatientRegistrationConstants.EX_IP_REG_003,
                "就诊类别不是住院，不能登记");
            if (!EncounterStatus.PLANNED.getCode().equals(encounterBeforeRegister.getStatusEnum())) {
                throw new BusinessException(
                    InpatientRegistrationConstants.EX_IP_REG_003,
                    "当前就诊状态不允许登记，请刷新列表");
            }
            this.assertNoAdmissionDiagnosesOnPlannedRegistration(request);
            this.clearAdmissionDiagnosesOnRegisterCommand(registerCommand);
            try {
                encounterDomainService.registerInpatientEncounter(registerCommand);
            } catch (BusinessException ex) {
                // 登记并发或状态漂移：领域 ENCOUNTER_INVALID_STATUS 映射为 EX-IP-REG-003（GAP-ATD-004）。
                if (ErrorCodeConstants.ENCOUNTER_INVALID_STATUS.equals(ex.getErrorCode())) {
                    throw new BusinessException(
                        InpatientRegistrationConstants.EX_IP_REG_003,
                        "当前就诊状态不允许登记，请刷新列表");
                }
                throw ex;
            } catch (OptimisticLockingFailureException ex) {
                throw new BusinessException(
                    InpatientRegistrationConstants.EX_IP_REG_003,
                    "当前就诊状态不允许登记，请刷新列表");
            }
        }
        encounterInpatientAdmissionOrchestrator.completeAfterInpatientRegistration(registerCommand);
        this.saveAttendingDoctorOnRegistration(request, encounterId, registrarId);
        boolean accountOpened = true;
        // Cmd：可选收取预交金并完成支付落账与账户余额调整。
        Long paymentId = this.collectAdvanceDepositIfRequired(request, patientId, encounterId);
        EncounterDetailQueryModel registeredEncounter = encounterQueryService.mustGetEncounterOfClass(
            encounterId,
            EncounterClass.IMP.getCode(),
            InpatientRegistrationConstants.EX_IP_REG_003,
            "就诊类别不是住院，不能登记");
        String busNo = registeredEncounter.getBusNo();
        // Cmd：医保费用性质时衔接入院登记（2401），获取 mdtrtId。
        String ybMdtrtId = this.runInsuranceAdmissionIfNeeded(request, encounterId);
        // Cmd：登记事务提交后幂等建病案（失败不回滚登记；打开首页仍有 bootstrap 兜底）
        this.scheduleCaseRecordBootstrapAfterCommit(encounterId, registrarId);
        // Asm：返回登记结果供前端跳转或打印。
        return inpatientRegistrationMapper.toSubmitRegistrationResponse(
            encounterId, busNo, paymentId, accountOpened, ybMdtrtId);
    }

    /**
     * 住院登记事务提交后幂等建病案主档与首页（病案号留空，首次保存首页再发号）。
     *
     * <p>放 afterCommit：建档失败不拖垮登记；同就诊重复调用由 {@code bootstrapIfAbsent} 幂等。</p>
     *
     * @param encounterId 住院就诊主键
     * @param operatorId  登记操作人（可为 null）
     */
    private void scheduleCaseRecordBootstrapAfterCommit(Long encounterId, Long operatorId) {
        // Gate：缺就诊主键则无法建档
        if (encounterId == null) {
            return;
        }
        IntegrationAfterCommitSupport.runAfterCommit(() -> {
            encounterQueryService.findOptionalEncounterEntityById(encounterId).ifPresent(encounter -> {
                // Gate：就诊须带租户与患者，否则无法发病案号
                if (encounter.getTenantId() == null || encounter.getPatientId() == null) {
                    log.warn(
                        "入院登记后跳过病案建档：就诊缺少 tenantId/patientId encounterId={}",
                        encounterId);
                    return;
                }
                try {
                    // Cmd：幂等建档 + 新建时 HIS autoFill
                    caseRecordDomainService.bootstrapIfAbsent(
                        encounter.getTenantId(),
                        encounter.getPatientId(),
                        encounterId,
                        operatorId);
                } catch (RuntimeException ex) {
                    // 建档失败不抛回登记链路；病案首页打开时仍可 bootstrap 兜底
                    log.warn(
                        "入院登记后病案建档失败 encounterId={} patientId={}",
                        encounterId,
                        encounter.getPatientId(),
                        ex);
                }
            });
        });
    }

    /**
     * 登记提交时按需衔接医保入院登记（2401）：扩展未启用、非医保合同或缺 psn_no 时跳过。
     *
     * @param request     登记请求（合同、读卡信息）
     * @param encounterId 住院就诊 ID
     * @return 平台 mdtrt_id；未登记或扩展未命中时 null
     * @throws BusinessException 医保登记缺主治医生或 yb_no
     */
    private String runInsuranceAdmissionIfNeeded(SubmitRegistrationRequest request, Long encounterId) {
        if (!inpatientChargeInsuranceExtension.isEnabled()) {
            return null;
        }
        if (request.getContractId() == null
            || !inpatientChargeInsuranceExtension.supportsThirdPartyContract(request.getContractId())) {
            return null;
        }
        if (StringUtils.isBlank(request.getPsnNo())) {
            throw new BusinessException(
                InpatientRegistrationConstants.EX_IP_REG_002,
                "医保登记须先读卡获取人员编号");
        }
        this.assertAttendingDoctorForInsurance(request);
        InpatientChargeAdmissionExtensionIn command = new InpatientChargeAdmissionExtensionIn();
        command.setEncounterId(encounterId);
        command.setContractId(request.getContractId());
        command.setPayerCertType(request.getYbMediumType());
        command.setPayerCertNo(request.getYbCardInfo());
        command.setInsuredPersonNo(request.getPsnNo());
        command.setInsuplcAdmdvs(request.getInsuplcAdmdvs());
        command.setInsutype(request.getInsutype());
        command.setMedType(request.getMedType());
        // Cmd：Extension → 2401 入院登记，返回 mdtrt_id
        InpatientChargeAdmissionExtensionOut result = inpatientChargeInsuranceExtension.ensureAdmission(command);
        if (result == null || StringUtils.isBlank(result.getEncounterPlatformId())) {
            return null;
        }
        return result.getEncounterPlatformId().trim();
    }

    /**
     * 登记提交时写入主治医生参与者（供 2401 atddr_no 解析）。
     *
     * @param request     登记请求
     * @param encounterId 住院就诊 ID
     * @param operatorId  操作人 ID
     */
    private void saveAttendingDoctorOnRegistration(
        SubmitRegistrationRequest request,
        Long encounterId,
        Long operatorId) {
        if (request.getAttendingDoctorId() == null) {
            return;
        }
        SaveNurseStationPatientCommand command = new SaveNurseStationPatientCommand();
        command.setOperatorId(operatorId);
        SaveNurseStationPatientCommand.ItemCommand item = new SaveNurseStationPatientCommand.ItemCommand();
        item.setEncounterId(encounterId);
        SaveNurseStationPatientCommand.ParticipantLine line = new SaveNurseStationPatientCommand.ParticipantLine();
        line.setRoleCode(EncounterParticipantRole.ATTENDING_DOCTOR.getCode());
        line.setPractitionerId(request.getAttendingDoctorId());
        item.setParticipants(List.of(line));
        command.setItem(item);
        encounterDomainService.saveNurseStationPatient(command);
    }

    /**
     * 医保登记门禁：须选择主治医生且医师主档含 yb_no。
     *
     * @param request 登记请求
     * @throws BusinessException 未选医师或缺 yb_no
     */
    private void assertAttendingDoctorForInsurance(SubmitRegistrationRequest request) {
        if (request.getAttendingDoctorId() == null) {
            throw new BusinessException(
                InpatientRegistrationConstants.EX_IP_REG_002,
                "医保登记须选择主治医生");
        }
        Practitioner doctor = practitionerQueryService.findById(request.getAttendingDoctorId());
        if (doctor == null) {
            throw new BusinessException(
                InpatientRegistrationConstants.EX_IP_REG_002,
                "主治医生无效，请重新选择");
        }
        if (StringUtils.isBlank(doctor.getYbNo())) {
            throw new BusinessException(
                InpatientRegistrationConstants.EX_IP_REG_002,
                "主治医生缺少医保编码(yb_no): " + doctor.getName());
        }
    }

    /**
     * 取消登记时按需撤销医保平台入院（2404）：扩展未启用或非第三方合同时静默跳过。
     *
     * @param encounter 住院就诊
     * @param account   登记账户（解析合同）
     */
    private void runInsuranceRevokeAdmissionIfNeeded(EncounterDetailQueryModel encounter, Account account) {
        if (!inpatientChargeInsuranceExtension.isEnabled() || encounter == null) {
            return;
        }
        Long contractId = this.resolveInsuranceContractId(account, encounter.getId());
        if (contractId == null || !inpatientChargeInsuranceExtension.supportsThirdPartyContract(contractId)) {
            return;
        }
        InpatientChargeEncounterExtensionIn command = new InpatientChargeEncounterExtensionIn();
        command.setEncounterId(encounter.getId());
        command.setContractId(contractId);
        inpatientChargeInsuranceExtension.revokeAdmission(command);
    }

    /**
     * 是否已向医保平台撤销入院登记（2404）。
     *
     * @param encounter 住院就诊
     * @param account     登记账户
     * @return 已调用并成功撤销平台入院时为 true
     */
    private boolean runInsuranceRevokeAdmissionReturningRevoked(
        EncounterDetailQueryModel encounter,
        Account account) {
        if (!inpatientChargeInsuranceExtension.isEnabled() || encounter == null) {
            return false;
        }
        Long contractId = this.resolveInsuranceContractId(account, encounter.getId());
        if (contractId == null || !inpatientChargeInsuranceExtension.supportsThirdPartyContract(contractId)) {
            return false;
        }
        InpatientChargeEncounterExtensionIn command = new InpatientChargeEncounterExtensionIn();
        command.setEncounterId(encounter.getId());
        command.setContractId(contractId);
        inpatientChargeInsuranceExtension.revokeAdmission(command);
        return true;
    }

    /**
     * 解析就诊展示用费用性质合同 ID：优先医保账户，否则个人现金户（与护士站/预交金卡片同口径）。
     *
     * @param encounterId 住院就诊主键
     * @return 合同主键；无可用合同号或未命中主数据时 null
     */
    private Long resolvePreferredContractIdForEncounter(Long encounterId) {
        if (encounterId == null) {
            return null;
        }
        Map<Long, String> preferredContractNoByEncounterId =
            accountQueryService.mapPreferredContractNoByEncounterIds(Collections.singleton(encounterId));
        String contractNo = preferredContractNoByEncounterId.get(encounterId);
        if (StringUtils.isBlank(contractNo)) {
            return null;
        }
        Map<String, Long> contractIdByBusNo =
            contractQueryService.mapContractIdByBusNoIn(Collections.singleton(contractNo.trim()));
        return contractIdByBusNo.get(contractNo.trim());
    }

    /**
     * 解析撤销医保入院所用合同 ID。
     *
     * <p>登记账户是个人现金户，其合同不是医保。已 2401 时必须以平台入院映射上的合同发 2404，
     * 不能因现金合同先返回而跳过撤销。</p>
     *
     * @param account     登记账户（个人现金户，仅在没有平台入院映射时作医保合同回落）
     * @param encounterId 就诊 ID（查平台快照）
     * @return 医保合同 ID；未入院且账户也不是医保合同时 null
     */
    private Long resolveInsuranceContractId(Account account, Long encounterId) {
        Long fromSnapshot = this.resolvePlatformAdmissionContractId(encounterId);
        // 已在平台入院：用映射上的合同发 2404，避免现金户合同把撤销跳过
        if (fromSnapshot != null) {
            return fromSnapshot;
        }
        Long fromAccount = this.resolveInsuranceContractIdFromAccount(account);
        // 现金户合同不是第三方医保，不能拿来发 2404
        if (fromAccount != null
            && inpatientChargeInsuranceExtension.supportsThirdPartyContract(fromAccount)) {
            return fromAccount;
        }
        return null;
    }

    /**
     * 从平台入院快照取已 2401 的合同 ID。
     *
     * @param encounterId 就诊 ID
     * @return 平台入院合同；未入院或扩展未启用时 null
     */
    private Long resolvePlatformAdmissionContractId(Long encounterId) {
        if (encounterId == null || !inpatientChargeInsuranceExtension.isEnabled()) {
            return null;
        }
        List<InpatientChargeAdmissionPlatformQueryExtensionOut> snapshots =
            inpatientChargeInsuranceExtension.findAdmissionPlatformSnapshots(
                new InpatientChargeAdmissionPlatformQueryExtensionIn().setEncounterIds(List.of(encounterId)));
        for (InpatientChargeAdmissionPlatformQueryExtensionOut snapshot : snapshots) {
            if (snapshot == null || snapshot.getContractId() == null) {
                continue;
            }
            // 未完成 2401 的快照不能拿来撤销
            if (Boolean.TRUE.equals(snapshot.getPlatformAdmitted())) {
                return snapshot.getContractId();
            }
        }
        return null;
    }

    /**
     * 由登记账户 contract_no 解析合同主键。
     *
     * @param account 登记账户
     * @return 合同 ID；账户或 busNo 为空、未命中合同时 null
     */
    private Long resolveInsuranceContractIdFromAccount(Account account) {
        if (account == null || StringUtils.isBlank(account.getContractNo())) {
            return null;
        }
        Contract contract = contractQueryService
            .findOptionalContractEntityByBusNo(account.getContractNo().trim())
            .orElse(null);
        if (contract != null) {
            return contract.getId();
        }
        return null;
    }

    /**
     * 取消住院登记（IF-IP-REG-05）：预交金须清零，可选撤销医保入院。
     *
     * @param request 取消请求（就诊 ID、操作人）
     * @return 取消结果
     * @throws BusinessException 余额大于 0 或已分床等不允许取消时抛出
     */
    public CancelRegistrationResponse cancelInpatientRegistration(CancelRegistrationRequest request) {
        // Gate：取消登记须携带就诊与操作人。
        if (request.getEncounterId() == null || request.getOperatorId() == null) {
            throw new BusinessException(
                InpatientRegistrationConstants.EX_IP_REG_001,
                "取消登记缺少就诊或操作人");
        }
        // Pre：加载住院就诊与登记账户余额快照。
        EncounterDetailQueryModel encounter = encounterQueryService.mustGetEncounterOfClass(
            request.getEncounterId(),
            EncounterClass.IMP.getCode(),
            InpatientRegistrationConstants.EX_IP_REG_006,
            "就诊记录不是住院类别，不能取消住院登记");
        Long patientId = encounter.getPatientId();
        Account account = accountQueryService.getRegistrationAccount(patientId, request.getEncounterId());
        long balanceSnapshot = 0L;
        if (account != null && account.getBalance() != null) {
            // Gate：预交金余额须为 0 才允许取消登记，防止资金悬空。
            if (account.getBalance().compareTo(BigDecimal.ZERO) > 0) {
                throw new BusinessException(
                    InpatientRegistrationConstants.EX_IP_REG_005,
                    "预交金余额不为 0，请先退费后再取消登记");
            }
            balanceSnapshot = account.getBalance().longValue();
        }
        // Cmd：取消前作废未 settle 的意向/预下单预交金 DRAFT（BR-IP-REG-008）。
        this.abortHospitalDepositDrafts(request.getEncounterId());
        // Cmd：撤销医保入院（若适用）并取消住院就诊。
        CancelInpatientAdmissionCommand cancelCommand =
            inpatientRegistrationMapper.toCancelInpatientAdmissionCommand(request, balanceSnapshot);
        try {
            this.runInsuranceRevokeAdmissionIfNeeded(encounter, account);
            encounterDomainService.cancelInpatientAdmission(cancelCommand);
        } catch (BusinessException ex) {
            throw new BusinessException(
                InpatientRegistrationConstants.EX_IP_REG_006,
                ex.getMessage());
        }
        // Asm：返回取消成功标识。
        return inpatientRegistrationMapper.toCancelRegistrationResponse(request.getEncounterId());
    }

    /**
     * 待入科（REGISTERED）医保退入院：撤销 2404 并取消住院登记（对齐门诊待挂号医保退号）。
     *
     * @param request 取消请求（就诊 ID、操作人）
     * @return 撤销结果
     * @throws BusinessException 非待入科、预交金未清零或平台撤销失败时抛出
     */
    public CancelInsuranceInpatientAdmissionResponse cancelInsuranceInpatientAdmission(
        CancelRegistrationRequest request) {
        // Gate：就诊与操作人必填，否则无法定位撤销对象。
        if (request.getEncounterId() == null || request.getOperatorId() == null) {
            throw new BusinessException(
                InpatientRegistrationConstants.EX_IP_REG_001,
                "取消登记缺少就诊或操作人");
        }
        // Pre：须为住院 IMP 且待入科（REGISTERED），否则不允许医保退入院。
        EncounterDetailQueryModel encounter = encounterQueryService.mustGetEncounterOfClass(
            request.getEncounterId(),
            EncounterClass.IMP.getCode(),
            InpatientRegistrationConstants.EX_IP_REG_006,
            "就诊记录不是住院类别，不能取消住院登记");
        if (!EncounterStatus.REGISTERED.getCode().equals(encounter.getStatusEnum())) {
            throw new BusinessException(
                InpatientRegistrationConstants.EX_IP_REG_006,
                "仅待入科状态可医保退入院");
        }
        Long patientId = encounter.getPatientId();
        Account account = accountQueryService.getRegistrationAccount(patientId, request.getEncounterId());
        long balanceSnapshot = 0L;
        // Gate：预交金须已清零，否则先退预交金再撤销入院。
        if (account != null && account.getBalance() != null) {
            if (account.getBalance().compareTo(BigDecimal.ZERO) > 0) {
                throw new BusinessException(
                    InpatientRegistrationConstants.EX_IP_REG_005,
                    "预交金余额不为 0，请先退费后再取消登记");
            }
            balanceSnapshot = account.getBalance().longValue();
        }
        // Pre：须已在医保平台完成 2401 入院登记，否则走普通取消登记。
        boolean platformAdmitted = this.isInsurancePlatformAdmitted(request.getEncounterId());
        if (!platformAdmitted) {
            throw new BusinessException(
                InpatientRegistrationConstants.EX_IP_REG_006,
                "该记录未在医保平台完成入院登记，请使用取消登记");
        }
        // Cmd：取消前作废残留预交金 DRAFT（与普通取消登记同口径）。
        this.abortHospitalDepositDrafts(request.getEncounterId());
        CancelInpatientAdmissionCommand cancelCommand =
            inpatientRegistrationMapper.toCancelInpatientAdmissionCommand(request, balanceSnapshot);
        boolean insuranceRevoked = false;
        try {
            // Cmd：先 2404 撤销医保入院，再本地取消住院登记。
            insuranceRevoked = this.runInsuranceRevokeAdmissionReturningRevoked(encounter, account);
            encounterDomainService.cancelInpatientAdmission(cancelCommand);
        } catch (BusinessException ex) {
            throw new BusinessException(
                InpatientRegistrationConstants.EX_IP_REG_006,
                ex.getMessage());
        }
        // Asm：返回取消后状态与平台撤销标记。
        return new CancelInsuranceInpatientAdmissionResponse()
            .setEncounterStatus(EncounterStatus.CANCELLED.getCode())
            .setInsurancePlatformRevoked(insuranceRevoked);
    }

    /**
     * 住院自费转医保（IF-IP-REG-07）：读卡补录、开医保户、2401 入院登记并切换未结费用。
     *
     * @param request 转医保请求（含读卡字段与目标合同）
     * @return 转医保结果（含 mdtrtId 与切换行数）
     * @throws BusinessException 门禁不满足或平台登记失败时抛出
     */
    public SwitchInpatientToInsuranceResponse switchEncounterToInsurance(
        SwitchInpatientToInsuranceRequest request) {
        // Gate：请求体与医保扩展须齐备。
        if (request == null || request.getEncounterId() == null) {
            throw new BusinessException(
                InpatientRegistrationConstants.EX_IP_REG_001,
                "住院就诊不能为空");
        }
        if (!inpatientChargeInsuranceExtension.isEnabled()) {
            throw new BusinessException(
                InpatientRegistrationConstants.EX_IP_REG_206,
                "医保扩展未启用，无法转医保");
        }
        Long encounterId = request.getEncounterId();
        EncounterDetailQueryModel encounter = encounterQueryService.mustGetEncounterOfClass(
            encounterId,
            EncounterClass.IMP.getCode(),
            InpatientRegistrationConstants.EX_IP_REG_102,
            "就诊记录不是住院类别，无法转医保");
        // Gate：仅纯自费在院且未 2401、无已结算占用时允许转医保。
        this.assertSwitchToInsuranceAllowed(
            encounterId,
            encounter.getStatusEnum(),
            this.isInsurancePlatformAdmitted(encounterId),
            chargeItemQueryService.sumSettledTotalPriceByEncounterId(encounterId));
        ContractDetailQueryModel contract = contractQueryService.findContractById(request.getContractId());
        if (contract == null || StringUtils.isBlank(contract.getBusNo())) {
            throw new BusinessException(
                InpatientRegistrationConstants.EX_IP_REG_002,
                "费用性质无效，请重新选择");
        }
        if (!inpatientChargeInsuranceExtension.supportsThirdPartyContract(request.getContractId())) {
            throw new BusinessException(
                InpatientRegistrationConstants.EX_IP_REG_206,
                "所选费用性质不支持医保入院登记");
        }
        if (StringUtils.isBlank(request.getPsnNo())) {
            throw new BusinessException(
                InpatientRegistrationConstants.EX_IP_REG_204,
                "转医保须先读卡获取人员编号");
        }
        this.assertAttendingDoctorIdForInsurance(request.getAttendingDoctorId());
        Long operatorId = this.resolveRegistrarId(request.getOperatorId());
        Long patientId = encounter.getPatientId();
        // Cmd：幂等开立医保挂账账户（个人现金户保留供预交金）。
        boolean openMedicalInsurance = encounterChargeAccountOpener.needsParallelMedicalInsuranceAccount(
            contract.getYbFlag(), contract.getCategoryEnum());
        if (!openMedicalInsurance) {
            throw new BusinessException(
                InpatientRegistrationConstants.EX_IP_REG_206,
                "所选费用性质非医保合同，无法转医保");
        }
        encounterChargeAccountOpener.ensureEncounterChargeAccounts(
            patientId,
            encounterId,
            contract.getBusNo(),
            true,
            request.getInsutype(),
            operatorId);
        this.saveAttendingDoctorOnSwitch(request, encounterId, operatorId);
        // Cmd：2401 入院登记，使用原入院时间（非转医保当天）。
        String ybMdtrtId = this.runInsuranceAdmissionForSwitch(request, encounterId);
        Long insuranceAccountId = accountQueryService.getMedicalInsuranceAccount(encounterId);
        // 先前医保转自费会停用医保户；开户幂等不会重新启用，这里按本次合同启用
        if (insuranceAccountId == null) {
            accountDomainService.reactivateMedicalInsuranceAccount(
                encounterId, contract.getBusNo(), request.getInsutype());
            insuranceAccountId = accountQueryService.getMedicalInsuranceAccount(encounterId);
        }
        if (insuranceAccountId == null) {
            throw new BusinessException(
                InpatientRegistrationConstants.EX_IP_REG_206,
                "医保账户开立失败，请稍后重试");
        }
        int switchedCount = inpatientRegistrationInsuranceSwitchSupport.switchUnsettledChargeItemsToInsuranceAccount(
            encounterId, insuranceAccountId, operatorId);
        // Asm：返回平台 mdtrtId 与切换行数。
        return inpatientRegistrationMapper.toSwitchInpatientToInsuranceResponse(
            ybMdtrtId, switchedCount, insuranceAccountId);
    }

    /**
     * 住院医保转自费（IF-IP-REG-09）：撤销平台入院与已上传明细，未结费用改挂个人现金户并停用医保户。
     *
     * <p>预交金仍留在个人现金户。已结算费用须先取消结算。平台撤销失败时不改本地挂账。</p>
     *
     * @param request 转自费请求（就诊与操作人）
     * @return 切换行数与个人现金账户
     * @throws BusinessException 门禁不满足或平台撤销失败时抛出
     */
    public SwitchInpatientToSelfPayResponse switchEncounterToSelfPay(SwitchInpatientToSelfPayRequest request) {
        // Gate：没有就诊无法定位要改挂的住院。
        if (request == null || request.getEncounterId() == null) {
            throw new BusinessException(
                InpatientRegistrationConstants.EX_IP_REG_001,
                "住院就诊不能为空");
        }
        Long encounterId = request.getEncounterId();
        // Pre：须为住院就诊，门诊号不能走住院转自费。
        EncounterDetailQueryModel encounter = encounterQueryService.mustGetEncounterOfClass(
            encounterId,
            EncounterClass.IMP.getCode(),
            InpatientRegistrationConstants.EX_IP_REG_102,
            "就诊记录不是住院类别，无法转自费");
        boolean platformAdmitted = this.isInsurancePlatformAdmitted(encounterId);
        BigDecimal settledTotal = chargeItemQueryService.sumSettledTotalPriceByEncounterId(encounterId);
        // Gate：在院医保入院且无已结费用才允许整单转自费。
        this.assertSwitchToSelfPayAllowed(
            encounterId, encounter.getStatusEnum(), platformAdmitted, settledTotal);
        Long operatorId = this.resolveRegistrarId(request.getOperatorId());
        Account selfPayAccount = accountQueryService.getSelfAccount(encounterId);
        // 个人现金户承接未结费用和原预交金；缺失则不能改挂
        if (selfPayAccount == null || selfPayAccount.getId() == null) {
            throw new BusinessException(
                InpatientRegistrationConstants.EX_IP_REG_210,
                "个人现金账户不存在，无法转自费");
        }
        // Cmd：先撤平台入院，避免本地已自费但医保仍占在院名额。
        if (platformAdmitted) {
            this.revokePlatformStayForSelfPay(encounter, selfPayAccount);
        }
        int switchedCount = inpatientRegistrationInsuranceSwitchSupport.switchUnsettledChargeItemsToSelfPayAccount(
            encounterId, selfPayAccount.getId(), operatorId);
        // Cmd：停用医保户后，执行/划价/滚费的默认挂账回到个人现金户。
        accountDomainService.deactivateMedicalInsuranceAccount(encounterId);
        // Asm：返回切户行数，预交金余额不在本响应中变更。
        return inpatientRegistrationMapper.toSwitchInpatientToSelfPayResponse(
            switchedCount, selfPayAccount.getId());
    }

    /**
     * 待办队列批量回填医保险种类别码（职工/居民）。
     *
     * @param rows         队列行
     * @param encounterIds 本页就诊主键集合
     */
    private void enrichRegistrationQueueInsutype(
        List<RegistrationQueueItemDto> rows, Set<Long> encounterIds) {
        if (rows == null || rows.isEmpty() || encounterIds == null || encounterIds.isEmpty()) {
            return;
        }
        // Pre：账户优先、读卡缓存/预结算回落（与医生站队列同口径）。
        Map<Long, String> insutypeByEncounterId =
            encounterInsutypeDisplayAssembler.mapInsutypeByEncounterIds(encounterIds);
        if (insutypeByEncounterId == null || insutypeByEncounterId.isEmpty()) {
            return;
        }
        for (RegistrationQueueItemDto row : rows) {
            if (row == null || row.getEncounterId() == null) {
                continue;
            }
            String insutype = insutypeByEncounterId.get(row.getEncounterId());
            if (StringUtils.isNotEmpty(insutype)) {
                row.setInsutype(insutype.trim());
            }
        }
    }

    /**
     * 解析单次就诊的医保险种类别码（310/390 等）；无码返回 null。
     *
     * @param encounterId 住院就诊 ID
     * @return 险种码或 null
     */
    private String resolveInsutypeForEncounter(Long encounterId) {
        if (encounterId == null) {
            return null;
        }
        Map<Long, String> insutypeByEncounterId =
            encounterInsutypeDisplayAssembler.mapInsutypeByEncounterIds(List.of(encounterId));
        if (insutypeByEncounterId == null || insutypeByEncounterId.isEmpty()) {
            return null;
        }
        String insutype = insutypeByEncounterId.get(encounterId);
        if (StringUtils.isEmpty(insutype)) {
            return null;
        }
        return insutype.trim();
    }

    /**
     * 待入科队列批量回填「是否已在医保平台完成 2401 入院登记」标记。
     *
     * @param rows 队列行；扩展未启用或空列表时无操作
     */
    private void enrichRegistrationQueueInsurance(List<RegistrationQueueItemDto> rows) {
        if (rows == null || rows.isEmpty() || !inpatientChargeInsuranceExtension.isEnabled()) {
            return;
        }
        List<Long> encounterIds = new ArrayList<>();
        for (RegistrationQueueItemDto row : rows) {
            if (row != null && row.getEncounterId() != null) {
                encounterIds.add(row.getEncounterId());
            }
        }
        if (encounterIds.isEmpty()) {
            return;
        }
        Map<Long, InpatientChargeAdmissionPlatformQueryExtensionOut> snapshotByEncounter = new HashMap<>();
        for (InpatientChargeAdmissionPlatformQueryExtensionOut snapshot :
            inpatientChargeInsuranceExtension.findAdmissionPlatformSnapshots(
                new InpatientChargeAdmissionPlatformQueryExtensionIn().setEncounterIds(encounterIds))) {
            if (snapshot != null && snapshot.getEncounterId() != null) {
                snapshotByEncounter.put(snapshot.getEncounterId(), snapshot);
            }
        }
        for (RegistrationQueueItemDto row : rows) {
            if (row == null || row.getEncounterId() == null) {
                continue;
            }
            InpatientChargeAdmissionPlatformQueryExtensionOut snapshot = snapshotByEncounter.get(row.getEncounterId());
            if (snapshot != null) {
                row.setInsurancePlatformRegistered(snapshot.getPlatformAdmitted());
            }
        }
    }

    /**
     * 判断就诊是否已在医保平台完成入院登记（2401 快照 platform_admitted=true）。
     *
     * @param encounterId 就诊 ID
     * @return 已平台入院为 true
     */
    private boolean isInsurancePlatformAdmitted(Long encounterId) {
        if (encounterId == null || !inpatientChargeInsuranceExtension.isEnabled()) {
            return false;
        }
        List<InpatientChargeAdmissionPlatformQueryExtensionOut> snapshots =
            inpatientChargeInsuranceExtension.findAdmissionPlatformSnapshots(
                new InpatientChargeAdmissionPlatformQueryExtensionIn().setEncounterIds(List.of(encounterId)));
        for (InpatientChargeAdmissionPlatformQueryExtensionOut snapshot : snapshots) {
            if (snapshot != null && Boolean.TRUE.equals(snapshot.getPlatformAdmitted())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 按住院科室查询下属病区下拉（IF-IP-REG-04）。
     *
     * @param request 科室 ID
     * @return 病区选项列表
     * @throws BusinessException 科室 ID 为空时抛出
     */
    public ListWardsByDepartmentResponse listWardsByDepartment(ListWardsByDepartmentRequest request) {
        // Gate：住院科室不能为空。
        if (request.getOrganizationId() == null) {
            throw new BusinessException(
                InpatientRegistrationConstants.EX_IP_REG_001,
                "住院科室不能为空");
        }
        // Pre：查询该科挂靠的启用病区（主挂靠 ∪ servedOrganizationIds）并映射为下拉。
        List<Location> wards = locationQueryService.listWardsByOrganization(request.getOrganizationId());
        // Asm：封装病区选项响应。
        return inpatientRegistrationMapper.toListWardsByDepartmentResponse(wards);
    }

    /**
     * 加载病区床位容量（IF-IP-REG-04）：总床位数与空床数。
     *
     * @param request 病区位置 ID
     * @return 总床与空床数
     * @throws BusinessException 病区 ID 为空或无效时抛出
     */
    public LoadWardBedCapacityResponse loadWardBedCapacity(LoadWardBedCapacityRequest request) {
        // Gate：病区不能为空且须为启用中的 WARD 形态位置。
        if (request.getWardId() == null) {
            throw new BusinessException(
                InpatientRegistrationConstants.EX_IP_REG_001,
                "病区不能为空");
        }
        this.requireActiveWard(request.getWardId());
        // Pre：统计病区总床与空床数量。
        Long total = locationQueryService.countTotalBedsByWard(request.getWardId());
        Long vacant = locationQueryService.countBedsByWard(request.getWardId());
        // Asm：返回床位容量摘要。
        return inpatientRegistrationMapper.toLoadWardBedCapacityResponse(
            request.getWardId(), total, vacant);
    }

    /**
     * 更正入院科室可选病区：全部启用病区，不限目标科室。
     *
     * <p>入院科室和实际住的病区可以不是同一个科。下拉带主挂靠科室名，便于辨认。</p>
     *
     * @param request 占位请求，可空
     * @return 启用病区
     */
    public ListWardsByDepartmentResponse listAdmissionDepartmentWards(
        ListAdmissionDepartmentWardsRequest request) {
        // Gate：不按科室过滤。请求体只为与登记其它接口对齐，内容不参与筛选。
        List<Location> locations = locationQueryService.listAllByFormCode(LocationForm.WARD.getCode());
        List<Location> activeWards = new ArrayList<>();
        Set<Long> organizationIds = new LinkedHashSet<>();
        for (Location ward : locations) {
            if (ward == null || ward.getId() == null || Boolean.TRUE.equals(ward.getIsDeleted())) {
                continue;
            }
            // 停用病区不能再选作入院病区
            if (!LocationStatus.ACTIVE.getCode().equals(ward.getStatusEnum())) {
                continue;
            }
            activeWards.add(ward);
            if (ward.getOrganizationId() != null) {
                organizationIds.add(ward.getOrganizationId());
            }
        }
        // Pre：一次取出主挂靠科室名称，避免按病区逐条查机构。
        Map<Long, String> organizationNameById = organizationQueryService.mapNamesByIds(organizationIds);
        List<WardOptionDto> options = new ArrayList<>();
        for (Location ward : activeWards) {
            WardOptionDto option = new WardOptionDto();
            option.setWardId(ward.getId());
            option.setWardCode(ward.getBusNo());
            option.setWardName(ward.getName());
            if (ward.getOrganizationId() != null) {
                option.setOrganizationName(organizationNameById.get(ward.getOrganizationId()));
            }
            options.add(option);
        }
        // Asm：病区选项，名称里另带主挂靠科室。
        ListWardsByDepartmentResponse response = new ListWardsByDepartmentResponse();
        response.setWards(options);
        return response;
    }

    /**
     * 更正入院科室可选床：目标病区空床，以及本就诊已占用且仍在该病区下的当前床。
     *
     * @param request 病区，以及可选的当前就诊
     * @return 可选床
     * @throws BusinessException 病区为空或不是启用中的病区
     */
    public ListAdmissionDepartmentBedsResponse listAdmissionDepartmentBeds(
        ListAdmissionDepartmentBedsRequest request) {
        // Gate：病区不能为空，且须是启用中的 WARD。
        if (request == null || request.getWardId() == null) {
            throw new BusinessException(
                InpatientRegistrationConstants.EX_IP_REG_001,
                "病区不能为空");
        }
        this.requireActiveWard(request.getWardId());
        Long currentBedId = null;
        // Pre：已入科患者的当前床若就在该病区，允许继续选它，避免只改科室时被迫换床。
        if (request.getEncounterId() != null) {
            InpatientEncounterContextQueryModel context =
                this.mustGetInpatientContextForRegistration(request.getEncounterId());
            if (context != null) {
                currentBedId = context.getBedId();
            }
        }
        List<AdmissionDepartmentBedOptionDto> beds = new ArrayList<>();
        List<Location> houses = locationQueryService.listHousesUnderWard(request.getWardId());
        for (Location house : houses) {
            if (house == null || house.getId() == null) {
                continue;
            }
            List<Location> houseBeds = locationQueryService.listBedsUnderParent(house.getId());
            for (Location bed : houseBeds) {
                if (bed == null || bed.getId() == null) {
                    continue;
                }
                if (!LocationStatus.ACTIVE.getCode().equals(bed.getStatusEnum())) {
                    continue;
                }
                boolean currentBed = bed.getId().equals(currentBedId);
                // 占用中的别人的床不能选；自己的当前床保留
                if (!currentBed
                    && !LocationOperational.NOT_IN_USE.getCode().equals(bed.getOperationalEnum())) {
                    continue;
                }
                AdmissionDepartmentBedOptionDto option = new AdmissionDepartmentBedOptionDto();
                option.setBedId(bed.getId());
                option.setHouseId(house.getId());
                option.setBedName(bed.getName());
                option.setHouseName(house.getName());
                option.setCurrentBed(currentBed);
                beds.add(option);
            }
        }
        // Asm：空床在前，当前床仍返回供「只改科室」。
        ListAdmissionDepartmentBedsResponse response = new ListAdmissionDepartmentBedsResponse();
        response.setBeds(beds);
        return response;
    }

    /**
     * 更正入院科室（IF-IP-REG-08）：先改院内科室、病区和已入科床位，已 2401 再发 2403。
     *
     * <p>2403 读的是当前就诊科室。医保调用失败时本方法事务回滚，避免院内已改、平台仍是原科室。</p>
     *
     * @param request 目标科室、病区；已入科须带床位。病区可以不属于目标科室，也可以保持原病区原床。
     * @return 是否已通知医保平台
     * @throws BusinessException 状态、床位或医保资料不满足时抛出
     */
    @Transactional(rollbackFor = Exception.class)
    public ChangeAdmissionDepartmentResponse changeAdmissionDepartment(ChangeAdmissionDepartmentRequest request) {
        // Gate：就诊、科室、病区缺一不可。
        if (request == null || request.getEncounterId() == null
            || request.getOrganizationId() == null || request.getWardId() == null) {
            throw new BusinessException(
                InpatientRegistrationConstants.EX_IP_REG_001,
                "更正入院科室缺少就诊、科室或病区");
        }
        // Pre：加载住院就诊，后续门禁用它的状态、科室和结算占用。
        EncounterDetailQueryModel encounter = encounterQueryService.mustGetEncounterOfClass(
            request.getEncounterId(),
            EncounterClass.IMP.getCode(),
            InpatientRegistrationConstants.EX_IP_REG_102,
            "就诊记录不是住院类别，不能更正入院科室");
        boolean inProgress = EncounterStatus.IN_PROGRESS.getCode().equals(encounter.getStatusEnum());
        boolean registered = EncounterStatus.REGISTERED.getCode().equals(encounter.getStatusEnum());
        // 出院、转科中、待登记都不在登记窗口改入院科室
        if (!inProgress && !registered) {
            throw new BusinessException(
                InpatientRegistrationConstants.EX_IP_REG_207,
                "仅待入科或已入科可以更正入院科室");
        }
        BigDecimal settledTotal = chargeItemQueryService.sumSettledTotalPriceByEncounterId(request.getEncounterId());
        // 已结算费用的入院科室以结算当时为准，登记窗口不再改
        if (MonetaryAmountSupport.nullToZero(settledTotal).compareTo(BigDecimal.ZERO) > 0) {
            throw new BusinessException(
                InpatientRegistrationConstants.EX_IP_REG_207,
                "该住院已有结算费用，不能更正入院科室");
        }
        Organization department = organizationQueryService.loadOrganizationByIdForDisplay(request.getOrganizationId());
        if (department == null
            || department.getClassEnum() != OrganizationClassification.INPATIENT
            || !PublicationStatus.ACTIVE.getCode().equals(department.getStatusEnum())) {
            throw new BusinessException(
                InpatientRegistrationConstants.EX_IP_REG_002,
                "请选择启用中的住院科室");
        }
        // 病区不必挂在新入院科室下，可以留在原病区，也可以改到其他科室的病区
        this.requireActiveWard(request.getWardId());
        Long currentWardId = encounterQueryService.requireActiveLocationId(
            request.getEncounterId(),
            LocationForm.WARD.getCode(),
            InpatientRegistrationConstants.EX_IP_REG_002,
            "当前就诊没有入院病区，不能更正",
            InpatientRegistrationConstants.EX_IP_REG_002,
            "当前就诊存在多条活动病区");
        Long fromBedId = null;
        if (inProgress) {
            if (request.getBedId() == null) {
                throw new BusinessException(
                    InpatientRegistrationConstants.EX_IP_REG_208,
                    "已入科更正入院科室须选择床位");
            }
            fromBedId = encounterQueryService.requireActiveLocationId(
                request.getEncounterId(),
                LocationForm.BED.getCode(),
                InpatientRegistrationConstants.EX_IP_REG_208,
                "当前就诊没有活动床位",
                InpatientRegistrationConstants.EX_IP_REG_208,
                "当前就诊存在多条活动床位");
        }
        // 待入科只改科室和病区，分床仍由护士站办理
        if (!inProgress && request.getBedId() != null) {
            throw new BusinessException(
                InpatientRegistrationConstants.EX_IP_REG_208,
                "待入科不能在更正入院科室时分床");
        }
        boolean sameBed = inProgress && request.getBedId().equals(fromBedId);
        boolean sameOrg = request.getOrganizationId().equals(encounter.getOrganizationId());
        boolean sameWard = request.getWardId().equals(currentWardId);
        // 科室、病区、床都没动才拒绝；只改科室、病区和床保持原样是允许的
        if (sameOrg && sameWard && (!inProgress || sameBed)) {
            throw new BusinessException(
                InpatientRegistrationConstants.EX_IP_REG_207,
                "入院科室、病区与床位未变化");
        }
        boolean platformAdmitted = this.isInsurancePlatformAdmitted(request.getEncounterId());
        // 先挡住发不出 2403 的资料，避免占床后再失败回滚
        if (platformAdmitted) {
            this.assertInsuranceDepartmentChangeReady(request.getEncounterId(), department);
        }
        Long houseId = null;
        Long operatorId = this.resolveRegistrarId(request.getOperatorId());
        if (inProgress) {
            houseId = this.requireBedUnderWard(request.getBedId(), request.getWardId(), sameBed);
            // 换到空床：先占新床，医保失败时与院内改动一起回滚
            if (!sameBed) {
                this.occupyTargetBed(request.getBedId(), request.getEncounterId(), operatorId);
            }
        }
        CorrectInpatientAdmissionDepartmentCommand command = new CorrectInpatientAdmissionDepartmentCommand();
        command.setEncounterId(request.getEncounterId());
        command.setDepartmentId(request.getOrganizationId());
        command.setWardId(request.getWardId());
        command.setHouseId(houseId);
        command.setBedId(request.getBedId());
        // Cmd：写就诊科室与病区/床轨迹。2403 随后读取的就是这份新科室。
        encounterDomainService.correctInpatientAdmissionDepartment(command);
        if (inProgress && !sameBed) {
            this.releasePreviousBed(fromBedId, request.getEncounterId(), operatorId);
        }
        boolean insuranceChanged = false;
        if (platformAdmitted) {
            // Cmd：院内已是新科室后再发 2403；失败则整单回滚。
            this.runAdmissionDepartmentInsuranceChange(request.getEncounterId());
            insuranceChanged = true;
        }
        // Asm：告诉前端这次有没有打到医保。
        ChangeAdmissionDepartmentResponse response = new ChangeAdmissionDepartmentResponse();
        response.setSuccess(Boolean.TRUE);
        response.setInsurancePlatformChanged(insuranceChanged);
        return response;
    }

    /**
     * 已医保入院时，目标科室医保码、平台人员编号和身份证必须齐，否则 2403 发不出去。
     *
     * @param encounterId 住院就诊主键
     * @param department  目标住院科室
     * @throws BusinessException 缺医保科室编码、人员编号或身份证
     */
    private void assertInsuranceDepartmentChangeReady(Long encounterId, Organization department) {
        if (department == null || StringUtils.isBlank(department.getYbNo())) {
            throw new BusinessException(
                InpatientRegistrationConstants.EX_IP_REG_209,
                "目标科室缺少医保科室编码，不能变更医保入院信息");
        }
        InpatientChargeAdmissionPlatformQueryExtensionOut snapshot =
            this.requirePlatformAdmissionSnapshot(encounterId);
        if (snapshot == null || StringUtils.isBlank(snapshot.getPsnNo())) {
            throw new BusinessException(
                InpatientRegistrationConstants.EX_IP_REG_209,
                "未找到医保人员编号，不能变更医保入院信息");
        }
        InpatientEncounterContextQueryModel context =
            this.mustGetInpatientContextForRegistration(encounterId);
        if (context == null || StringUtils.isBlank(context.getIdCard())) {
            throw new BusinessException(
                InpatientRegistrationConstants.EX_IP_REG_209,
                "患者缺少身份证号，不能变更医保入院信息");
        }
    }

    /**
     * 院内科室已更新后发送 2403。人员编号取入院映射，就诊凭证用身份证，不再次读卡。
     *
     * @param encounterId 住院就诊主键
     * @throws BusinessException 平台变更失败时抛出，由调用方事务回滚院内改动
     */
    private void runAdmissionDepartmentInsuranceChange(Long encounterId) {
        InpatientChargeAdmissionPlatformQueryExtensionOut snapshot =
            this.requirePlatformAdmissionSnapshot(encounterId);
        if (snapshot == null || snapshot.getContractId() == null) {
            throw new BusinessException(
                InpatientRegistrationConstants.EX_IP_REG_209,
                "未找到医保入院合同，不能变更医保入院信息");
        }
        InpatientChargeAdmissionExtensionIn command = new InpatientChargeAdmissionExtensionIn();
        command.setEncounterId(encounterId);
        command.setContractId(snapshot.getContractId());
        command.setInsuredPersonNo(snapshot.getPsnNo());
        command.setInsuplcAdmdvs(snapshot.getInsuplcAdmdvs());
        command.setIdCardAsMdtrtCert(Boolean.TRUE);
        inpatientChargeInsuranceExtension.changeInpatientInfo(command);
    }

    /**
     * 取已完成 2401 的平台入院快照。
     *
     * @param encounterId 住院就诊主键
     * @return 已入院快照；未入院时 null
     */
    private InpatientChargeAdmissionPlatformQueryExtensionOut requirePlatformAdmissionSnapshot(Long encounterId) {
        if (encounterId == null || !inpatientChargeInsuranceExtension.isEnabled()) {
            return null;
        }
        List<InpatientChargeAdmissionPlatformQueryExtensionOut> snapshots =
            inpatientChargeInsuranceExtension.findAdmissionPlatformSnapshots(
                new InpatientChargeAdmissionPlatformQueryExtensionIn().setEncounterIds(List.of(encounterId)));
        for (InpatientChargeAdmissionPlatformQueryExtensionOut snapshot : snapshots) {
            if (snapshot != null && Boolean.TRUE.equals(snapshot.getPlatformAdmitted())) {
                return snapshot;
            }
        }
        return null;
    }

    /**
     * 校验床位属于目标病区。当前床不要求空闲，新床必须是空床。
     *
     * @param bedId    目标床位
     * @param wardId   目标病区
     * @param sameBed  为 true 表示仍住当前床
     * @return 病房主键
     * @throws BusinessException 床位不在该病区或新床不是空床
     */
    private Long requireBedUnderWard(Long bedId, Long wardId, boolean sameBed) {
        Location bed = locationQueryService.findOptionalById(bedId).orElse(null);
        if (bed == null || !LocationForm.BED.getCode().equals(bed.getFormEnum())) {
            throw new BusinessException(
                InpatientRegistrationConstants.EX_IP_REG_208,
                "请选择有效床位");
        }
        Location house = null;
        if (bed.getParentId() != null) {
            house = locationQueryService.findOptionalById(bed.getParentId()).orElse(null);
        }
        if (house == null
            || !LocationForm.HOUSE.getCode().equals(house.getFormEnum())
            || !wardId.equals(house.getParentId())) {
            throw new BusinessException(
                InpatientRegistrationConstants.EX_IP_REG_208,
                "床位不属于所选病区");
        }
        if (!sameBed && !LocationOperational.NOT_IN_USE.getCode().equals(bed.getOperationalEnum())) {
            throw new BusinessException(
                InpatientRegistrationConstants.EX_IP_REG_208,
                "请选择空床");
        }
        return house.getId();
    }

    /**
     * 占用目标空床（与护士站换床同一命令）。
     *
     * @param bedId       目标床
     * @param encounterId 住院就诊
     * @param operatorId  操作人
     */
    private void occupyTargetBed(Long bedId, Long encounterId, Long operatorId) {
        OccupyBedCommand occupyCommand = new OccupyBedCommand();
        occupyCommand.setOperatorId(operatorId);
        OccupyBedCommand.OccupyBedItemCommand item = new OccupyBedCommand.OccupyBedItemCommand();
        item.setLocationId(bedId);
        item.setEncounterId(encounterId);
        item.setOccupyFromReservation(Boolean.FALSE);
        occupyCommand.setItem(item);
        locationDomainService.occupyBed(occupyCommand);
    }

    /**
     * 释放原床，使原病区床位重新可分配。
     *
     * @param bedId       原活动床
     * @param encounterId 住院就诊
     * @param operatorId  操作人
     */
    private void releasePreviousBed(Long bedId, Long encounterId, Long operatorId) {
        ReleaseBedCommand releaseCommand = new ReleaseBedCommand();
        releaseCommand.setOperatorId(operatorId);
        ReleaseBedCommand.ReleaseBedItemCommand item = new ReleaseBedCommand.ReleaseBedItemCommand();
        item.setLocationId(bedId);
        item.setEncounterId(encounterId);
        releaseCommand.setItem(item);
        locationDomainService.releaseBed(releaseCommand);
    }

    /**
     * 校验登记列表状态筛选码列表。
     *
     * @param registrationListStatuses 筛选码列表，可空
     * @throws BusinessException 非法筛选码
     */
    private void validateRegistrationListStatuses(List<String> registrationListStatuses) {
        if (registrationListStatuses == null || registrationListStatuses.isEmpty()) {
            return;
        }
        for (String raw : registrationListStatuses) {
            if (StringUtils.isBlank(raw)) {
                continue;
            }
            String code = raw.trim();
            if (!InpatientRegistrationConstants.isValidRegistrationListStatus(code)) {
                throw new BusinessException(
                    InpatientRegistrationConstants.EX_IP_REG_101,
                    "登记状态筛选无效，请重新选择");
            }
        }
    }

    /**
     * 去重并规范化登记列表状态筛选码。
     *
     * @param registrationListStatuses 原始筛选码
     * @return 非空去重列表
     */
    private List<String> normalizeRegistrationListStatuses(List<String> registrationListStatuses) {
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String raw : registrationListStatuses) {
            if (StringUtils.isNotBlank(raw)) {
                unique.add(raw.trim());
            }
        }
        return new ArrayList<>(unique);
    }

    /**
     * 提交登记请求门控。
     *
     * @param request 登记请求
     * @throws BusinessException 必填项缺失
     */
    private void validateSubmitRegistrationRequest(SubmitRegistrationRequest request) {
        boolean missingCore = request.getOrganizationId() == null
            || request.getWardId() == null
            || request.getStartTime() == null
            || request.getContractId() == null
            || request.getOperatorId() == null;
        if (missingCore) {
            throw new BusinessException(
                InpatientRegistrationConstants.EX_IP_REG_001,
                "请补齐住院科室、病区、入院时间、费用性质与操作人");
        }
        if (request.getPatientId() == null && StringUtils.isBlank(request.getIdCard())) {
            throw new BusinessException(
                InpatientRegistrationConstants.EX_IP_REG_001,
                "请选择患者或填写身份证号");
        }
        if (request.getPatientId() == null && StringUtils.isBlank(request.getPatientName())) {
            throw new BusinessException(
                InpatientRegistrationConstants.EX_IP_REG_001,
                "新建患者时姓名不能为空");
        }
        if (request.getEncounterId() == null) {
            this.assertDirectRegistrationAdmissionDiagnoses(request);
        } else {
            this.assertNoAdmissionDiagnosesOnPlannedRegistration(request);
        }
    }

    /**
     * 有医生申请登记时禁止在请求体中提交入院诊断（诊断已在申请阶段写入住院线）。
     *
     * @param request 登记请求
     * @throws BusinessException 携带入院诊断列表时抛出
     */
    private void assertNoAdmissionDiagnosesOnPlannedRegistration(SubmitRegistrationRequest request) {
        List<AdmissionDiagnosisSubmitDto> rows = request.getAdmissionDiagnoses();
        if (rows == null || rows.isEmpty()) {
            return;
        }
        throw new BusinessException(
            InpatientRegistrationConstants.EX_IP_REG_003,
            "有医生申请时入院诊断以申请为准，登记不可提交或修改入院诊断");
    }

    /**
     * 直登登记须携带可编辑入院诊断且至少一条主诊断（BR-IP-REG-007）。
     *
     * @param request 登记请求
     * @throws BusinessException 列表为空或结构不合法时抛出
     */
    private void assertDirectRegistrationAdmissionDiagnoses(SubmitRegistrationRequest request) {
        List<AdmissionDiagnosisSubmitDto> rows = request.getAdmissionDiagnoses();
        List<AdmissionDiagnosisSubmitGate.AdmissionDiagnosisRowView> views = new ArrayList<>();
        if (rows != null) {
            views.addAll(rows);
        }
        admissionDiagnosisSubmitGate.validateAdmissionDiagnosisRows(
            views,
            "直登登记须填写入院诊断",
            InpatientRegistrationConstants.EX_IP_REG_001);
    }

    /**
     * 有申请登记不向领域命令传递入院诊断，避免登记侧误触发写诊断。
     *
     * @param registerCommand 住院登记命令
     */
    private void clearAdmissionDiagnosesOnRegisterCommand(RegisterInpatientEncounterCommand registerCommand) {
        for (RegisterInpatientEncounterCommand.RegisterInpatientEncounterItemCommand item
            : registerCommand.resolveItems()) {
            item.setAdmissionDiagnoses(null);
        }
    }

    /**
     * 断言病区服务所选住院科室且为启用 WARD（主挂靠或 type_json 多科挂靠命中）。
     *
     * @param organizationId  科室 ID
     * @param wardId  病区位置 ID
     * @throws BusinessException 病区无效或未挂靠该科
     */
    private void assertWardBelongsToOrganization(Long organizationId, Long wardId) {
        Location ward = this.requireActiveWard(wardId);
        // 多科共用一病区：主挂靠或 servedOrganizationIds 任一命中即通过
        if (!locationQueryService.wardServesOrganization(ward, organizationId)) {
            throw new BusinessException(
                InpatientRegistrationConstants.EX_IP_REG_002,
                "病区不属于所选住院科室，请重新选择");
        }
    }

    /**
     * 加载并校验启用中的病区位置。
     *
     * @param wardId 病区位置 ID
     * @return 病区实体
     * @throws BusinessException 不存在、非病区或已停用时抛出
     */
    private Location requireActiveWard(Long wardId) {
        Location ward = locationQueryService.findOptionalById(wardId).orElse(null);
        if (ward == null || Boolean.TRUE.equals(ward.getIsDeleted())) {
            throw new BusinessException(
                InpatientRegistrationConstants.EX_IP_REG_002,
                "入院病区不存在，请重新选择");
        }
        if (!LocationForm.WARD.getCode().equals(ward.getFormEnum())) {
            throw new BusinessException(
                InpatientRegistrationConstants.EX_IP_REG_002,
                "所选位置不是病区，请重新选择");
        }
        if (!LocationStatus.ACTIVE.getCode().equals(ward.getStatusEnum())) {
            throw new BusinessException(
                InpatientRegistrationConstants.EX_IP_REG_002,
                "入院病区已停用，请重新选择");
        }
        return ward;
    }

    /**
     * 解析登记员主键：请求优先，否则回落当前登录用户。
     *
     * @param operatorIdFromRequest 请求中的操作人
     * @return 登记员 ID
     * @throws BusinessException 无法解析操作人时抛出
     */
    private Long resolveRegistrarId(Long operatorIdFromRequest) {
        Long registrarId = operatorIdFromRequest;
        if (registrarId == null) {
            registrarId = currentPractitionerResolver.requireOperatorId(
                InpatientRegistrationConstants.EX_IP_REG_001,
                "操作人不能为空");
        }
        return registrarId;
    }

    /**
     * 患者分流（SDD §3.4 步骤 5～6）：复用 patientId、身份证命中或新建患者。
     *
     * @param request 登记请求
     * @return 患者 ID
     * @throws BusinessException 新建患者字段不足时抛出
     */
    private Long resolvePatientIdForSubmit(SubmitRegistrationRequest request) {
        if (request.getPatientId() != null) {
            return request.getPatientId();
        }
        if (StringUtils.isNotBlank(request.getIdCard())) {
            PatientSimpleQueryModel matched =
                patientQueryService.findPatientByIdCard(request.getIdCard().trim());
            if (matched != null && matched.getId() != null) {
                return matched.getId();
            }
        }
        RegisterPatientCommand registerCommand =
            inpatientRegistrationMapper.toRegisterPatientCommand(request);
        Long orgId = request.getOrganizationId();
        if (orgId == null) {
            orgId = managedOrganizationIdsResolver.requireSessionOrganizationUnitId(
                InpatientRegistrationConstants.EX_IP_REG_001,
                "当前登录机构不能为空");
        }
        if (registerCommand.getItem() != null) {
            registerCommand.getItem().setOrganizationId(orgId);
        }
        return patientDomainService.registerPatient(registerCommand);
    }

    /**
     * 同身份证更新主档（SDD §3.4 步骤 7）。
     *
     * @param request   登记请求
     * @param patientId 已解析患者 ID
     */
    private void updatePatientProfileIfRequested(SubmitRegistrationRequest request, Long patientId) {
        // Gate：前端未勾选/未置位「更新主档」时跳过，避免误改档案
        if (!Boolean.TRUE.equals(request.getUpdatePatientFromIdCard())) {
            return;
        }
        // Gate：无患者主键则无法改档
        if (patientId == null) {
            return;
        }
        // Pre：加载既有主档，改档时保留登记页未编辑字段（血型等），供人口学叠写与病案 bootstrap
        PatientCoreQueryModel existingCore = null;
        PatientDetailQueryModel detail = patientQueryService.loadPatientDetail(patientId);
        if (detail != null) {
            existingCore = detail.getPatient();
        }
        // Cmd：以既有主档为底叠写登记表单人口学（国籍/民族/籍贯/婚姻/职业/住址/联系人等）
        ModifyPatientProfileCommand profileCommand =
            inpatientRegistrationMapper.toModifyPatientProfileCommand(request, patientId, existingCore);
        patientDomainService.modifyPatientProfile(profileCommand);
    }

    /**
     * 预交金收取与结算（SDD §3.4 步骤 12～14）；金额≤0 时软删意向草稿并跳过收款。
     *
     * <p>有医生意向 DRAFT 时先软删；再按支付明细<strong>逐渠道</strong>各落一笔 HOSPITAL_DEPOSIT
     * 并 settle（一渠道一流水，BR-IP-REG-022）；账户余额按合计一次调增。</p>
     *
     * @param request     登记请求
     * @param patientId   患者 ID
     * @param encounterId 住院就诊 ID
     * @return 最后一笔支付单 ID；未收款时为 null
     * @throws BusinessException 明细缺失、账户不存在或收款失败时抛出
     */
    private Long collectAdvanceDepositIfRequired(
        SubmitRegistrationRequest request,
        Long patientId,
        Long encounterId) {
        BigDecimal advanceAmount = request.getAdvanceDepositAmount();
        // 收费员清空预交金：软删医生意向 DRAFT，本单不收款
        if (advanceAmount == null || advanceAmount.compareTo(BigDecimal.ZERO) <= 0) {
            this.abortHospitalDepositDrafts(encounterId);
            return null;
        }
        this.validateAdvanceDepositPaymentDetails(request, advanceAmount);
        Account registrationAccount =
            accountQueryService.getRegistrationAccount(patientId, encounterId);
        if (registrationAccount == null || registrationAccount.getId() == null) {
            throw new BusinessException(
                InpatientRegistrationConstants.EX_IP_REG_004,
                "登记账户不存在，无法收取预交金");
        }
        // Cmd：旧意向/预下单 DRAFT 软删后按渠道重建，保证以改后为准
        this.abortHospitalDepositDrafts(encounterId);
        // Cmd：经办人写 practitioner.id，与门诊/预交金/结算写路径一致（禁止 zxplat_users.id）。
        Long entererId = currentPractitionerResolver.requireOperatorId(
            InpatientRegistrationConstants.EX_IP_REG_004,
            "预交金收款失败，请重试");
        try {
            Long lastPaymentId = null;
            // Cmd：每条支付渠道各 collect+settle 一笔流水，避免多渠道挤在同一支付头
            for (PaymentDetailDto detail : request.getPaymentDetails()) {
                // 校验阶段已过滤非法行；此处再防空行
                if (detail == null || detail.getAmount() == null) {
                    continue;
                }
                CollectAdvanceDepositCommand collectCommand =
                    inpatientRegistrationMapper.toCollectAdvanceDepositCommandForChannel(
                        patientId,
                        encounterId,
                        registrationAccount.getId(),
                        detail.getAmount(),
                        detail);
                // 装配失败视为明细脏数据，与合计校验口径一致拒绝
                if (collectCommand == null) {
                    throw new BusinessException(
                        InpatientRegistrationConstants.EX_IP_REG_001,
                        "收取预交金时必须填写支付明细");
                }
                collectCommand.setEntererId(entererId);
                Long paymentId = paymentReconciliationDomainService.collectAdvanceDeposit(collectCommand);
                SettleInpatientPaymentCommand settleCommand =
                    inpatientRegistrationMapper.toSettleInpatientPaymentCommand(paymentId);
                paymentReconciliationSettleOrchestration.settleInpatientPayment(settleCommand);
                lastPaymentId = paymentId;
            }
            // Cmd：余额按合计一次调增，与多笔流水总额一致
            AdjustAdvanceDepositBalanceCommand balanceCmd = new AdjustAdvanceDepositBalanceCommand();
            balanceCmd.setPatientId(patientId);
            balanceCmd.setEncounterId(encounterId);
            balanceCmd.setBalanceDelta(advanceAmount);
            balanceCmd.setOperatorId(request.getOperatorId());
            accountDomainService.adjustAdvanceDepositBalance(balanceCmd);
            return lastPaymentId;
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new BusinessException(
                InpatientRegistrationConstants.EX_IP_REG_004,
                "预交金收款失败，请重试");
        }
    }

    /**
     * 将就诊下最新 HOSPITAL_DEPOSIT DRAFT 回填到登记上下文（BR-IP-REG-021）。
     *
     * @param encounterDto 登记就诊 DTO
     * @param encounterId  住院就诊主键
     */
    private void fillIntentAdvanceDepositDraft(RegistrationEncounterDto encounterDto, Long encounterId) {
        Optional<PaymentReconciliationQueryModel> draftOpt =
            paymentReconciliationQueryService.findLatestHospitalDepositDraftByEncounter(encounterId);
        // 无意向/预下单草稿时保持金额为空，由收费员自行填写
        if (draftOpt.isEmpty()) {
            return;
        }
        PaymentReconciliationQueryModel draft = draftOpt.get();
        encounterDto.setAdvanceDepositAmount(draft.getDisplayAmount());
        encounterDto.setPaymentDraftId(draft.getId());
    }

    /**
     * 软删就诊下住院预交金意向草稿（及历史 CANCELLED 作废稿），流水不可见。
     *
     * <p>登记确认重建 / 清空金额 / 取消登记时调用；成功收款单不触碰。</p>
     *
     * @param encounterId 住院就诊主键
     */
    private void abortHospitalDepositDrafts(Long encounterId) {
        // 无就诊主键时无法定位草稿，直接跳过
        if (encounterId == null) {
            return;
        }
        // Cmd：当前 DRAFT 意向稿软删
        List<PaymentReconciliationQueryModel> drafts =
            paymentReconciliationQueryService.listHospitalDepositDraftsByEncounter(encounterId);
        for (PaymentReconciliationQueryModel draft : drafts) {
            if (draft == null || draft.getId() == null) {
                continue;
            }
            paymentReconciliationDomainService.softDeleteAdvanceDepositDraft(draft.getId());
        }
        // Cmd：历史 abort 留下的 CANCELLED 预交金也软删，避免预交金页仍可见
        List<PaymentReconciliationQueryModel> allPayments =
            paymentReconciliationQueryService.findPaymentsByEncounter(encounterId);
        for (PaymentReconciliationQueryModel payment : allPayments) {
            if (payment == null || payment.getId() == null) {
                continue;
            }
            if (payment.getKindEnum() != PaymentKind.HOSPITAL_DEPOSIT) {
                continue;
            }
            if (!PaymentStatus.CANCELLED.equals(payment.getStatusEnum())) {
                continue;
            }
            paymentReconciliationDomainService.softDeleteAdvanceDepositDraft(payment.getId());
        }
    }

    /**
     * 校验登记首笔预交金支付明细：非空、每行正数、渠道合法、合计等于主金额。
     *
     * @param request       登记请求
     * @param advanceAmount 预交金主金额
     * @throws BusinessException 明细缺失或合计不等时抛 EX-IP-REG-001
     */
    private void validateAdvanceDepositPaymentDetails(
        SubmitRegistrationRequest request,
        BigDecimal advanceAmount) {
        // 无预交金时不校验明细
        if (advanceAmount == null || advanceAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        if (request.getPaymentDetails() == null || request.getPaymentDetails().isEmpty()) {
            throw new BusinessException(
                InpatientRegistrationConstants.EX_IP_REG_001,
                "收取预交金时必须填写支付明细");
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (PaymentDetailDto row : request.getPaymentDetails()) {
            if (row == null || row.getAmount() == null) {
                throw new BusinessException(
                    InpatientRegistrationConstants.EX_IP_REG_001,
                    "支付明细合计与预交金金额不一致");
            }
            if (row.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException(
                    InpatientRegistrationConstants.EX_IP_REG_001,
                    "支付明细合计与预交金金额不一致");
            }
            row.setPayChannelCode(paymentChannelGate.requirePersistablePayChannelCode(
                row.getPayChannelCode(),
                InpatientRegistrationConstants.EX_IP_REG_001,
                "支付方式不合法"));
            sum = sum.add(row.getAmount());
        }
        // Gate：支付明细合计与预交金主金额须在结算刻度（2 位）一致（BR-MON-001 / BR-IP-PREP-010）。
        if (!MonetaryAmountSupport.equalsAtSettleScale(sum, advanceAmount)) {
            throw new BusinessException(
                InpatientRegistrationConstants.EX_IP_REG_001,
                "支付明细合计与预交金金额不一致");
        }
    }

    /**
     * 加载住院就诊上下文；不存在时统一抛 EX-IP-REG-102。
     */
    private InpatientEncounterContextQueryModel mustGetInpatientContextForRegistration(Long encounterId) {
        try {
            return EncounterContextReader.mustGetInpatientContextByEncounterId(encounterId);
        } catch (BusinessException ex) {
            throw new BusinessException(
                InpatientRegistrationConstants.EX_IP_REG_102,
                "住院登记信息不存在或尚未同步，请稍后重试");
        }
    }

    /**
     * 按源门诊就诊解析接诊医生姓名（ADMITTER），供门诊转住院登记弹窗只读展示。
     *
     * <p>展示口径：活动参与者优先，无活动行时回落末次 COMPLETED（退号收口后仍可回显）。</p>
     *
     * @param ambEncounterId 源门诊就诊 ID
     * @return 接诊医生姓名；无参与者或未维护姓名时返回 null
     */
    private String resolveOutpatientDoctorName(Long ambEncounterId) {
        if (ambEncounterId == null) {
            return null;
        }
        List<EncounterParticipantQueryModel> participants =
            encounterQueryService.listDisplayEncounterParticipantsByType(
                ambEncounterId, EncounterConstants.ROLE_ADMITTER);
        if (participants == null || participants.isEmpty()) {
            return null;
        }
        Long practitionerId = null;
        for (EncounterParticipantQueryModel participant : participants) {
            if (participant != null && participant.getPractitionerId() != null) {
                practitionerId = participant.getPractitionerId();
                break;
            }
        }
        if (practitionerId == null) {
            return null;
        }
        List<Practitioner> practitioners =
            practitionerQueryService.findAllByIds(Collections.singleton(practitionerId));
        if (practitioners == null || practitioners.isEmpty()) {
            return null;
        }
        Practitioner practitioner = practitioners.get(0);
        if (practitioner == null || StringUtils.isBlank(practitioner.getName())) {
            return null;
        }
        return practitioner.getName().trim();
    }

    /**
     * 为本页住院上下文行批量加载源门诊就诊 ID 索引。
     */
    private Map<Long, Long> loadAmbEncounterIdIndex(List<InpatientEncounterContextQueryModel> rows) {
        if (rows == null || rows.isEmpty()) {
            return Map.of();
        }
        Set<Long> encounterIds = new LinkedHashSet<>();
        for (InpatientEncounterContextQueryModel row : rows) {
            if (row.getEncounterId() != null) {
                encounterIds.add(row.getEncounterId());
            }
        }
        if (encounterIds.isEmpty()) {
            return Map.of();
        }
        return encounterQueryService.mapAmbEncounterIdByEncounterIds(encounterIds);
    }

    /**
     * 队列批量回填「是否允许自费转医保」标记。
     *
     * @param rows 队列行
     */
    private void enrichRegistrationQueueSwitchToInsurance(List<RegistrationQueueItemDto> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        List<Long> encounterIds = new ArrayList<>();
        for (RegistrationQueueItemDto row : rows) {
            if (row != null && row.getEncounterId() != null) {
                encounterIds.add(row.getEncounterId());
            }
        }
        Map<Long, BigDecimal> settledByEncounter =
            chargeItemQueryService.mapSettledTotalPriceByEncounterIds(encounterIds);
        for (RegistrationQueueItemDto row : rows) {
            if (row == null || row.getEncounterId() == null) {
                continue;
            }
            boolean enabled = this.computeSwitchToInsuranceEnabled(
                row.getEncounterId(),
                row.getStatusEnum(),
                row.getInsurancePlatformRegistered(),
                settledByEncounter);
            row.setSwitchToInsuranceEnabled(enabled);
        }
    }

    /**
     * 队列批量回填「是否允许医保转自费」。
     *
     * @param rows 队列行
     */
    private void enrichRegistrationQueueSwitchToSelfPay(List<RegistrationQueueItemDto> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        List<Long> encounterIds = new ArrayList<>();
        for (RegistrationQueueItemDto row : rows) {
            if (row != null && row.getEncounterId() != null) {
                encounterIds.add(row.getEncounterId());
            }
        }
        Map<Long, BigDecimal> settledByEncounter =
            chargeItemQueryService.mapSettledTotalPriceByEncounterIds(encounterIds);
        Map<Long, Account> insuranceByEncounter =
            accountQueryService.mapMedicalInsuranceAccountsByEncounterIds(encounterIds);
        for (RegistrationQueueItemDto row : rows) {
            if (row == null || row.getEncounterId() == null) {
                continue;
            }
            boolean hasInsuranceAccount = insuranceByEncounter.get(row.getEncounterId()) != null;
            boolean enabled = this.computeSwitchToSelfPayEnabled(
                row.getStatusEnum(),
                Boolean.TRUE.equals(row.getInsurancePlatformRegistered()),
                hasInsuranceAccount,
                settledByEncounter.get(row.getEncounterId()));
            row.setSwitchToSelfPayEnabled(enabled);
        }
    }

    /**
     * 判断是否允许医保转自费：在院、仍有启用医保户、无已结算费用；已 2401 时医保扩展须可用。
     *
     * @param encounterId       就诊 ID；仅单条加载时用于补查已结算合计
     * @param statusEnum        就诊状态
     * @param platformAdmitted  是否已 2401
     * @param settledByEncounter 批量已结算汇总；单条时 null 则现场查
     * @return 允许时为 true
     */
    private boolean computeSwitchToSelfPayEnabled(
        Long encounterId,
        String statusEnum,
        boolean platformAdmitted,
        Map<Long, BigDecimal> settledByEncounter) {
        boolean hasInsuranceAccount = accountQueryService.getMedicalInsuranceAccount(encounterId) != null;
        BigDecimal settledTotal = BigDecimal.ZERO;
        if (settledByEncounter != null) {
            BigDecimal cached = settledByEncounter.get(encounterId);
            if (cached != null) {
                settledTotal = cached;
            }
        } else {
            settledTotal = chargeItemQueryService.sumSettledTotalPriceByEncounterId(encounterId);
        }
        return this.computeSwitchToSelfPayEnabled(
            statusEnum, platformAdmitted, hasInsuranceAccount, settledTotal);
    }

    /**
     * 判断是否允许医保转自费（队列与写路径共用，不再访问数据库）。
     *
     * @param statusEnum           就诊状态
     * @param platformAdmitted     是否已 2401
     * @param hasInsuranceAccount  是否仍有启用中的医保户
     * @param settledTotal         已结算合计
     * @return 允许时为 true
     */
    private boolean computeSwitchToSelfPayEnabled(
        String statusEnum,
        boolean platformAdmitted,
        boolean hasInsuranceAccount,
        BigDecimal settledTotal) {
        if (!this.isSwitchableStatusForInsuranceSwitch(statusEnum)) {
            return false;
        }
        // 没有启用中的医保户：已经是自费挂账，无需再转
        if (!hasInsuranceAccount) {
            return false;
        }
        if (MonetaryAmountSupport.nullToZero(settledTotal).compareTo(BigDecimal.ZERO) > 0) {
            return false;
        }
        // 扩展未启用时无法确认平台是否仍在院，禁止只改本地挂账
        if (!inpatientChargeInsuranceExtension.isEnabled()) {
            return false;
        }
        return true;
    }

    /**
     * 转自费写路径门禁：不满足时抛业务异常。
     *
     * @param encounterId      就诊 ID
     * @param statusEnum       就诊状态
     * @param platformAdmitted 是否已 2401
     * @param settledTotal     已结算合计
     */
    private void assertSwitchToSelfPayAllowed(
        Long encounterId,
        String statusEnum,
        boolean platformAdmitted,
        BigDecimal settledTotal) {
        boolean hasInsuranceAccount = accountQueryService.getMedicalInsuranceAccount(encounterId) != null;
        // 纯自费入院没有医保户可停
        if (!hasInsuranceAccount) {
            throw new BusinessException(
                InpatientRegistrationConstants.EX_IP_REG_210,
                "该患者不是医保入院，无需转自费");
        }
        if (!this.isSwitchableStatusForInsuranceSwitch(statusEnum)) {
            throw new BusinessException(
                InpatientRegistrationConstants.EX_IP_REG_202,
                "当前就诊状态不允许转自费");
        }
        if (MonetaryAmountSupport.nullToZero(settledTotal).compareTo(BigDecimal.ZERO) > 0) {
            throw new BusinessException(
                InpatientRegistrationConstants.EX_IP_REG_203,
                "存在已结算费用，请先取消结算后再转自费");
        }
        // 扩展关闭时查不到 2401 快照，不能把仍在平台在院的患者改成本地自费
        if (!inpatientChargeInsuranceExtension.isEnabled()) {
            throw new BusinessException(
                InpatientRegistrationConstants.EX_IP_REG_206,
                "医保扩展未启用，无法撤销平台入院");
        }
    }

    /**
     * 转自费前撤销平台在院：已上传明细先 2302，再 2404；平台已出院则先 2405。
     *
     * @param encounter 住院就诊
     * @param cashAccount 个人现金户（无平台映射时的合同回落）
     */
    private void revokePlatformStayForSelfPay(EncounterDetailQueryModel encounter, Account cashAccount) {
        Long contractId = this.resolveInsuranceContractId(cashAccount, encounter.getId());
        // 已 2401 却解析不到医保合同：不能盲撤，避免用错合同
        if (contractId == null) {
            throw new BusinessException(
                InpatientRegistrationConstants.EX_IP_REG_211,
                "未找到医保入院合同，无法撤销平台入院");
        }
        List<Long> chargeItemIds =
            inpatientRegistrationInsuranceSwitchSupport.listEncounterChargeItemIds(encounter.getId());
        // 无本地费用时 2302 不会出站；有上传记录才撤销明细
        if (!chargeItemIds.isEmpty()) {
            try {
                InpatientChargeRevokeFeeDetailExtensionIn revokeFees = new InpatientChargeRevokeFeeDetailExtensionIn();
                revokeFees.setEncounterId(encounter.getId());
                revokeFees.setContractId(contractId);
                revokeFees.setChargeItemIds(chargeItemIds);
                inpatientChargeInsuranceExtension.revokeFeeDetails(revokeFees);
            } catch (RuntimeException ex) {
                if (!this.isSelfPayRevokeIdempotent(ex)) {
                    throw new BusinessException(
                        InpatientRegistrationConstants.EX_IP_REG_211,
                        "医保费用明细撤销失败：" + this.platformFailureMessage(ex));
                }
            }
        }
        try {
            this.invokeRevokeAdmission(encounter.getId(), contractId);
        } catch (RuntimeException ex) {
            // 平台已办理出院时，须先 2405 再撤入院
            if (!this.isPlatformDischargeBlockingAdmissionRevoke(ex)) {
                throw new BusinessException(
                    InpatientRegistrationConstants.EX_IP_REG_211,
                    "医保入院撤销失败：" + this.platformFailureMessage(ex));
            }
            try {
                InpatientChargeEncounterExtensionIn revokeDischarge = new InpatientChargeEncounterExtensionIn();
                revokeDischarge.setEncounterId(encounter.getId());
                revokeDischarge.setContractId(contractId);
                inpatientChargeInsuranceExtension.revokeDischarge(revokeDischarge);
            } catch (RuntimeException dischargeEx) {
                if (!this.isSelfPayRevokeIdempotent(dischargeEx)) {
                    throw new BusinessException(
                        InpatientRegistrationConstants.EX_IP_REG_211,
                        "医保出院撤销失败：" + this.platformFailureMessage(dischargeEx));
                }
            }
            try {
                this.invokeRevokeAdmission(encounter.getId(), contractId);
            } catch (RuntimeException retryEx) {
                throw new BusinessException(
                    InpatientRegistrationConstants.EX_IP_REG_211,
                    "医保入院撤销失败：" + this.platformFailureMessage(retryEx));
            }
        }
    }

    /**
     * 调用 2404 撤销入院。平台已撤销时由调用方按幂等文案处理。
     *
     * @param encounterId 就诊 ID
     * @param contractId  医保合同 ID
     */
    private void invokeRevokeAdmission(Long encounterId, Long contractId) {
        InpatientChargeEncounterExtensionIn command = new InpatientChargeEncounterExtensionIn();
        command.setEncounterId(encounterId);
        command.setContractId(contractId);
        inpatientChargeInsuranceExtension.revokeAdmission(command);
    }

    /**
     * 平台撤销类失败是否表示目标已经不存在，可继续本地切户。
     *
     * @param ex 平台或扩展抛出的异常
     * @return 可视为已撤销时 true
     */
    private boolean isSelfPayRevokeIdempotent(RuntimeException ex) {
        String message = this.platformFailureMessage(ex);
        if (message.contains("已撤销") || message.contains("已经撤销") || message.contains("重复撤销")) {
            return true;
        }
        if (message.contains("不存在") && (message.contains("明细") || message.contains("费用") || message.contains("入院"))) {
            return true;
        }
        if (message.contains("未查询到") && message.contains("就诊")) {
            return true;
        }
        return false;
    }

    /**
     * 2404 失败是否因为平台已出院，需要先 2405。
     *
     * @param ex 2404 异常
     * @return 文案指向出院/离院状态时 true
     */
    private boolean isPlatformDischargeBlockingAdmissionRevoke(RuntimeException ex) {
        String message = this.platformFailureMessage(ex);
        if (message.contains("出院") || message.contains("离院")) {
            return true;
        }
        return false;
    }

    /**
     * 取平台失败文案，空消息时给收费员可识别的兜底句。
     *
     * @param ex 异常
     * @return 非空文案
     */
    private String platformFailureMessage(RuntimeException ex) {
        if (ex == null || ex.getMessage() == null || ex.getMessage().isBlank()) {
            return "平台返回失败";
        }
        return ex.getMessage().trim();
    }

    /**
     * 判断是否允许自费转医保（BR-IP-REG-019）。
     *
     * @param encounterId                 就诊 ID
     * @param statusEnum                  就诊状态
     * @param insurancePlatformRegistered 是否已 2401
     * @param settledByEncounter          批量已结算汇总；单条时可 null 则现场查
     * @return 允许时为 true
     */
    private boolean computeSwitchToInsuranceEnabled(
        Long encounterId,
        String statusEnum,
        Boolean insurancePlatformRegistered,
        Map<Long, BigDecimal> settledByEncounter) {
        if (encounterId == null || !inpatientChargeInsuranceExtension.isEnabled()) {
            return false;
        }
        if (!this.isSwitchableStatusForInsuranceSwitch(statusEnum)) {
            return false;
        }
        if (Boolean.TRUE.equals(insurancePlatformRegistered)) {
            return false;
        }
        if (accountQueryService.hasDualChargeAccounts(encounterId)) {
            return false;
        }
        BigDecimal settledTotal = BigDecimal.ZERO;
        if (settledByEncounter != null) {
            BigDecimal cached = settledByEncounter.get(encounterId);
            if (cached != null) {
                settledTotal = cached;
            }
        } else {
            settledTotal = chargeItemQueryService.sumSettledTotalPriceByEncounterId(encounterId);
        }
        if (MonetaryAmountSupport.nullToZero(settledTotal).compareTo(BigDecimal.ZERO) > 0) {
            return false;
        }
        return true;
    }

    /**
     * 转医保写路径门禁：不满足时抛业务异常。
     *
     * @param encounterId                 就诊 ID
     * @param statusEnum                  就诊状态
     * @param insurancePlatformAdmitted     是否已 2401
     * @param settledTotalPrice             已结算合计
     */
    private void assertSwitchToInsuranceAllowed(
        Long encounterId,
        String statusEnum,
        boolean insurancePlatformAdmitted,
        BigDecimal settledTotalPrice) {
        if (accountQueryService.hasDualChargeAccounts(encounterId)) {
            throw new BusinessException(
                InpatientRegistrationConstants.EX_IP_REG_201,
                "该患者已是医保入院，无需转医保");
        }
        if (insurancePlatformAdmitted) {
            throw new BusinessException(
                InpatientRegistrationConstants.EX_IP_REG_201,
                "该患者已在医保平台完成入院登记");
        }
        if (!this.isSwitchableStatusForInsuranceSwitch(statusEnum)) {
            throw new BusinessException(
                InpatientRegistrationConstants.EX_IP_REG_202,
                "当前就诊状态不允许转医保");
        }
        if (MonetaryAmountSupport.nullToZero(settledTotalPrice).compareTo(BigDecimal.ZERO) > 0) {
            throw new BusinessException(
                InpatientRegistrationConstants.EX_IP_REG_203,
                "存在已结算费用，不可转医保");
        }
    }

    /**
     * 允许转医保的 IMP 就诊状态：待入科、在院、待出院窗口。
     *
     * @param statusEnum 就诊状态码
     * @return 允许时为 true
     */
    private boolean isSwitchableStatusForInsuranceSwitch(String statusEnum) {
        if (StringUtils.isBlank(statusEnum)) {
            return false;
        }
        String code = statusEnum.trim();
        if (EncounterStatus.REGISTERED.getCode().equals(code)) {
            return true;
        }
        if (EncounterStatus.IN_PROGRESS.getCode().equals(code)) {
            return true;
        }
        if (EncounterStatus.ON_HOLD.getCode().equals(code)) {
            return true;
        }
        return false;
    }

    /**
     * 医保登记门禁：须选择主治医生且医师主档含 yb_no。
     *
     * @param attendingDoctorId 主治医生 ID
     * @throws BusinessException 未选医师或缺 yb_no
     */
    private void assertAttendingDoctorIdForInsurance(Long attendingDoctorId) {
        if (attendingDoctorId == null) {
            throw new BusinessException(
                InpatientRegistrationConstants.EX_IP_REG_205,
                "转医保须选择主治医生");
        }
        Practitioner doctor = practitionerQueryService.findById(attendingDoctorId);
        if (doctor == null) {
            throw new BusinessException(
                InpatientRegistrationConstants.EX_IP_REG_205,
                "主治医生无效，请重新选择");
        }
        if (StringUtils.isBlank(doctor.getYbNo())) {
            throw new BusinessException(
                InpatientRegistrationConstants.EX_IP_REG_205,
                "主治医生缺少医保编码(yb_no): " + doctor.getName());
        }
    }

    /**
     * 转医保时写入或更新主治医生参与者（供 2401 atddr_no）。
     *
     * @param request     转医保请求
     * @param encounterId 就诊 ID
     * @param operatorId  操作人
     */
    private void saveAttendingDoctorOnSwitch(
        SwitchInpatientToInsuranceRequest request,
        Long encounterId,
        Long operatorId) {
        if (request.getAttendingDoctorId() == null) {
            return;
        }
        SaveNurseStationPatientCommand command = new SaveNurseStationPatientCommand();
        command.setOperatorId(operatorId);
        SaveNurseStationPatientCommand.ItemCommand item = new SaveNurseStationPatientCommand.ItemCommand();
        item.setEncounterId(encounterId);
        SaveNurseStationPatientCommand.ParticipantLine line = new SaveNurseStationPatientCommand.ParticipantLine();
        line.setRoleCode(EncounterParticipantRole.ATTENDING_DOCTOR.getCode());
        line.setPractitionerId(request.getAttendingDoctorId());
        item.setParticipants(List.of(line));
        command.setItem(item);
        encounterDomainService.saveNurseStationPatient(command);
    }

    /**
     * 转医保时衔接医保入院登记（2401）。
     *
     * @param request     转医保请求
     * @param encounterId 就诊 ID
     * @return 平台 mdtrt_id
     */
    private String runInsuranceAdmissionForSwitch(
        SwitchInpatientToInsuranceRequest request,
        Long encounterId) {
        InpatientChargeAdmissionExtensionIn command = new InpatientChargeAdmissionExtensionIn();
        command.setEncounterId(encounterId);
        command.setContractId(request.getContractId());
        command.setPayerCertType(request.getYbMediumType());
        command.setPayerCertNo(request.getYbCardInfo());
        command.setInsuredPersonNo(request.getPsnNo());
        command.setInsuplcAdmdvs(request.getInsuplcAdmdvs());
        command.setInsutype(request.getInsutype());
        String medType = request.getMedType();
        if (StringUtils.isBlank(medType)) {
            medType = YbMedType.GENERAL_HOSPITALIZATION.getCode();
        }
        command.setMedType(medType);
        InpatientChargeAdmissionExtensionOut result = inpatientChargeInsuranceExtension.ensureAdmission(command);
        if (result == null || StringUtils.isBlank(result.getEncounterPlatformId())) {
            throw new BusinessException(
                InpatientRegistrationConstants.EX_IP_REG_206,
                "医保入院登记失败，请检查读卡信息与网络");
        }
        return result.getEncounterPlatformId().trim();
    }

}
