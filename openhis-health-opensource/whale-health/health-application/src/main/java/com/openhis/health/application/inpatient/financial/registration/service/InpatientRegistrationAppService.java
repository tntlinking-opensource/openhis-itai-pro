/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.financial.registration.service;

import com.openhis.health.application.shared.option.OptionDtoAssembler;
import com.openhis.health.application.shared.pagination.PageQueryBinder;
import com.openhis.health.application.shared.pagination.PagedResultBuilder;
import com.openhis.health.application.shared.validator.CurrentManagedOrganizationIdsResolver;
import com.openhis.health.application.shared.validator.CurrentPractitionerResolver;
import com.openhis.health.application.shared.validator.PageWindowGate;
import com.openhis.health.application.shared.financial.PaymentChannelOptionProvider;
import com.openhis.health.application.shared.validator.PaymentChannelGate;
import com.openhis.health.application.inpatient.financial.registration.dto.AdmissionDiagnosisSubmitDto;
import com.openhis.health.application.shared.inpatient.AdmissionDiagnosisSubmitGate;
import com.openhis.health.application.inpatient.financial.registration.dto.PaymentDetailDto;
import com.openhis.health.application.inpatient.financial.registration.dto.RegistrationQueueItemDto;
import com.openhis.health.application.inpatient.financial.registration.dto.RegistrationEncounterDto;
import com.openhis.health.application.inpatient.financial.registration.dto.RegistrationPatientDto;
import com.openhis.health.application.shared.inpatient.InpatientInfoQueueDisplayAssembler;
import com.openhis.health.application.inpatient.financial.registration.mapper.InpatientRegistrationMapper;
import com.openhis.health.domain.administration.account.command.AdjustAdvanceDepositBalanceCommand;
import com.openhis.health.domain.administration.account.service.AccountDomainService;
import com.openhis.health.application.inpatient.financial.registration.request.CancelRegistrationRequest;
import com.openhis.health.application.inpatient.financial.registration.request.ListWardsByDepartmentRequest;
import com.openhis.health.application.inpatient.financial.registration.request.LoadInpatientRegistrationPageInitRequest;
import com.openhis.health.application.inpatient.financial.registration.request.LoadRegistrationContextRequest;
import com.openhis.health.application.inpatient.financial.registration.request.LoadWardBedCapacityRequest;
import com.openhis.health.application.inpatient.financial.registration.request.PageRegistrationQueueRequest;
import com.openhis.health.application.inpatient.financial.registration.request.SubmitRegistrationRequest;
import com.openhis.health.application.inpatient.financial.registration.response.CancelInsuranceInpatientAdmissionResponse;
import com.openhis.health.application.inpatient.financial.registration.response.CancelRegistrationResponse;
import com.openhis.health.application.inpatient.financial.registration.response.ListWardsByDepartmentResponse;
import com.openhis.health.application.inpatient.financial.registration.response.LoadInpatientRegistrationPageInitResponse;
import com.openhis.health.application.inpatient.financial.registration.response.LoadRegistrationContextResponse;
import com.openhis.health.application.inpatient.financial.registration.response.LoadWardBedCapacityResponse;
import com.openhis.health.application.inpatient.financial.registration.response.PageRegistrationQueueResponse;
import com.openhis.health.application.inpatient.financial.registration.response.SubmitRegistrationResponse;
import com.openhis.health.application.shared.inpatient.EncounterInpatientAdmissionOrchestrator;
import com.openhis.health.domain.administration.account.entity.Account;
import com.openhis.health.domain.administration.account.service.AccountQueryService;
import com.openhis.health.domain.administration.encounter.command.CancelInpatientAdmissionCommand;
import com.openhis.health.domain.administration.encounter.command.RegisterInpatientEncounterCommand;
import com.openhis.health.domain.administration.encounter.command.SaveNurseStationPatientCommand;
import com.openhis.health.domain.administration.encounter.queries.model.EncounterDetailQueryModel;
import com.openhis.health.domain.administration.encounter.service.EncounterDomainService;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.application.shared.encounter.EncounterContextReader;
import com.openhis.health.domain.administration.location.entity.Location;
import com.openhis.health.domain.administration.location.service.LocationQueryService;
import com.openhis.health.domain.administration.organization.entity.Organization;
import com.openhis.health.domain.administration.organization.service.OrganizationQueryService;
import com.openhis.health.domain.administration.patient.command.ModifyPatientProfileCommand;
import com.openhis.health.domain.administration.patient.command.RegisterPatientCommand;
import com.openhis.health.domain.administration.patient.queries.model.PatientCoreQueryModel;
import com.openhis.health.domain.administration.patient.queries.model.PatientDetailQueryModel;
import com.openhis.health.domain.administration.patient.queries.model.PatientSimpleQueryModel;
import com.openhis.health.domain.administration.patient.service.PatientDomainService;
import com.openhis.health.domain.administration.patient.service.PatientQueryService;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.administration.practitioner.service.PractitionerQueryService;
import com.openhis.health.domain.administration.encounter.queries.model.EncounterParticipantQueryModel;
import com.openhis.health.domain.share.constants.EncounterConstants;
import com.openhis.health.domain.financial.contract.entity.Contract;
import com.openhis.health.domain.financial.contract.queries.model.ContractDetailQueryModel;
import com.openhis.health.domain.financial.contract.queries.model.ContractListItemQueryModel;
import com.openhis.health.domain.financial.contract.service.ContractQueryService;
import com.openhis.health.domain.financial.paymentreconciliation.command.CollectAdvanceDepositCommand;
import com.openhis.health.domain.financial.paymentreconciliation.command.SettleInpatientPaymentCommand;
import com.openhis.health.domain.financial.paymentreconciliation.service.PaymentReconciliationDomainService;
import com.openhis.health.infrastructure.integration.financial.orchestration.PaymentReconciliationSettleOrchestration;
import com.openhis.health.domain.administration.encounter.queries.InpatientEncounterPageQuery;
import com.openhis.health.domain.administration.encounter.queries.model.InpatientEncounterContextQueryModel;
import com.openhis.health.domain.terminology.service.CodeSystemQueryService;
import com.openhis.health.domain.share.constants.InpatientRegistrationConstants;
import com.openhis.health.domain.share.utils.MonetaryAmountSupport;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.TerminologyCodeSystemNameConstants;
import com.openhis.health.domain.share.enums.EncounterClass;
import com.openhis.health.domain.share.enums.EncounterParticipantRole;
import com.openhis.health.domain.share.enums.EncounterStatus;
import com.openhis.health.domain.share.enums.LocationForm;
import com.openhis.health.domain.share.enums.LocationStatus;
import com.openhis.whale.core.application.dto.OptionDto;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.health.infrastructure.integration.insurance.inpatient.InpatientChargeInsuranceExtension;
import com.openhis.health.infrastructure.integration.insurance.inpatient.ExtensionIn.InpatientChargeAdmissionExtensionIn;
import com.openhis.health.infrastructure.integration.insurance.inpatient.ExtensionIn.InpatientChargeAdmissionPlatformQueryExtensionIn;
import com.openhis.health.infrastructure.integration.insurance.inpatient.ExtensionIn.InpatientChargeEncounterExtensionIn;
import com.openhis.health.infrastructure.integration.insurance.inpatient.ExtensionOut.InpatientChargeAdmissionPlatformQueryExtensionOut;
import com.openhis.health.infrastructure.integration.insurance.inpatient.ExtensionOut.InpatientChargeAdmissionExtensionOut;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 住院登记应用服务（AS-IP-ADM-001，IF-IP-REG-01～06）。
 *
 * <p>职责边界：</p>
 * <ul>
 *   <li>Gate：分页窗口、登记状态筛选、科室/病区/预交金明细与余额门禁。</li>
 *   <li>Pre：患者解析/建档、住院上下文与主数据加载、医保入院可选衔接。</li>
 *   <li>Cmd：开立/完成住院就诊、收取预交金、取消登记与医保撤销。</li>
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
    private final ContractQueryService contractQueryService;
    private final EncounterDomainService encounterDomainService;
    private final EncounterInpatientAdmissionOrchestrator encounterInpatientAdmissionOrchestrator;
    private final AdmissionDiagnosisSubmitGate admissionDiagnosisSubmitGate;
    private final EncounterQueryService encounterQueryService;
    private final EncounterContextReader EncounterContextReader;
    private final AccountQueryService accountQueryService;
    private final AccountDomainService accountDomainService;
    private final PaymentReconciliationDomainService paymentReconciliationDomainService;
    private final PaymentReconciliationSettleOrchestration paymentReconciliationSettleOrchestration;
    private final InpatientRegistrationMapper inpatientRegistrationMapper;
    private final InpatientInfoQueueDisplayAssembler inpatientInfoQueueDisplayAssembler;
    private final CodeSystemQueryService codeSystemQueryService;
    private final PractitionerQueryService practitionerQueryService;

    private final InpatientChargeInsuranceExtension inpatientChargeInsuranceExtension;

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
     * @param request 分页与筛选条件（登记状态、身份证关键字）
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
        Map<Long, InpatientInfoQueueDisplayAssembler.InpatientQueueDisplayOverlay> displayOverlay =
            inpatientInfoQueueDisplayAssembler.buildOverlay(page.getContent());
        Map<String, String> inWayLabelByCode = this.buildTerminologyLabelByCode(
            TerminologyCodeSystemNameConstants.IN_WAY_CODE);
        // Asm：映射队列行并封装分页响应。
        List<RegistrationQueueItemDto> rows =
            inpatientRegistrationMapper.toRegistrationQueueItemDtoList(
                page.getContent(),
                ambEncounterIdByEncounterId,
                displayOverlay,
                inWayLabelByCode);
        this.enrichRegistrationQueueInsurance(rows);
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
            // Pre：门诊转住院时从源门诊就诊解析接诊医生，登记弹窗只读展示。
            if (encounterDto != null && encounterDto.getAmbEncounterId() != null) {
                encounterDto.setOutpatientDoctorName(
                    this.resolveOutpatientDoctorName(encounterDto.getAmbEncounterId()));
            }
            PatientDetailQueryModel patientDetail =
                patientQueryService.loadPatientDetail(inpatientRow.getPatientId());
            if (patientDetail != null && patientDetail.getPatient() != null) {
                PatientCoreQueryModel core = patientDetail.getPatient();
                LocalDateTime birthDateTime = null;
                if (core.getBirthDate() != null) {
                    birthDateTime = core.getBirthDate().atStartOfDay();
                }
                patientDto = inpatientRegistrationMapper.toRegistrationPatientDto(
                    core.getId(),
                    core.getName(),
                    core.getIdCard(),
                    core.getGenderEnum(),
                    birthDateTime,
                    core.getPhone());
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
        // Gate：待入科（REGISTERED）与已入科（IN_PROGRESS）打开弹窗为只读查看，防止误改已登记信息。
        boolean readOnly = false;
        if (encounterDto != null) {
            String statusEnum = encounterDto.getStatusEnum();
            if (EncounterStatus.REGISTERED.getCode().equals(statusEnum)
                || EncounterStatus.IN_PROGRESS.getCode().equals(statusEnum)) {
                readOnly = true;
            }
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
            readOnly);
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
        // Asm：返回登记结果供前端跳转或打印。
        return inpatientRegistrationMapper.toSubmitRegistrationResponse(
            encounterId, busNo, paymentId, accountOpened, ybMdtrtId);
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
     * 解析撤销/查询医保入院所用合同 ID：优先账户 contract_no，回落平台入院快照。
     *
     * @param account     登记账户
     * @param encounterId 就诊 ID（查平台快照）
     * @return 第三方合同 ID；无法解析时 null
     */
    private Long resolveInsuranceContractId(Account account, Long encounterId) {
        Long contractId = this.resolveInsuranceContractIdFromAccount(account);
        if (contractId != null) {
            return contractId;
        }
        if (encounterId == null || !inpatientChargeInsuranceExtension.isEnabled()) {
            return null;
        }
        List<InpatientChargeAdmissionPlatformQueryExtensionOut> snapshots =
            inpatientChargeInsuranceExtension.findAdmissionPlatformSnapshots(
                new InpatientChargeAdmissionPlatformQueryExtensionIn().setEncounterIds(List.of(encounterId)));
        for (InpatientChargeAdmissionPlatformQueryExtensionOut snapshot : snapshots) {
            if (snapshot == null || snapshot.getEncounterId() == null) {
                continue;
            }
            if (Boolean.TRUE.equals(snapshot.getPlatformAdmitted()) && snapshot.getContractId() != null) {
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
        // Pre：查询科室下启用病区并映射为下拉。
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
     * 断言病区属于科室且为 WARD 形态。
     *
     * @param organizationId  科室 ID
     * @param wardId  病区位置 ID
     * @throws BusinessException 病区无效
     */
    private void assertWardBelongsToOrganization(Long organizationId, Long wardId) {
        Location ward = this.requireActiveWard(wardId);
        if (ward.getOrganizationId() == null || !ward.getOrganizationId().equals(organizationId)) {
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
        if (!Boolean.TRUE.equals(request.getUpdatePatientFromIdCard())) {
            return;
        }
        if (patientId == null) {
            return;
        }
        ModifyPatientProfileCommand profileCommand =
            inpatientRegistrationMapper.toModifyPatientProfileCommand(request, patientId);
        patientDomainService.modifyPatientProfile(profileCommand);
    }

    /**
     * 预交金收取与结算（SDD §3.4 步骤 12～14）；金额≤0 时跳过。
     *
     * @param request     登记请求
     * @param patientId   患者 ID
     * @param encounterId 住院就诊 ID
     * @return 支付单 ID；未收款时为 null
     * @throws BusinessException 明细缺失、账户不存在或收款失败时抛出
     */
    private Long collectAdvanceDepositIfRequired(
        SubmitRegistrationRequest request,
        Long patientId,
        Long encounterId) {
        BigDecimal advanceAmount = request.getAdvanceDepositAmount();
        if (advanceAmount == null || advanceAmount.compareTo(BigDecimal.ZERO) <= 0) {
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
        CollectAdvanceDepositCommand collectCommand =
            inpatientRegistrationMapper.toCollectAdvanceDepositCommand(
                request, patientId, encounterId, registrationAccount.getId());
        // Cmd：经办人写 practitioner.id，与门诊/预交金/结算写路径一致（禁止 whale_users.id）。
        collectCommand.setEntererId(currentPractitionerResolver.requireOperatorId(
            InpatientRegistrationConstants.EX_IP_REG_004,
            "预交金收款失败，请重试"));
        try {
            Long paymentId = paymentReconciliationDomainService.collectAdvanceDeposit(collectCommand);
            SettleInpatientPaymentCommand settleCommand =
                inpatientRegistrationMapper.toSettleInpatientPaymentCommand(paymentId);
            paymentReconciliationSettleOrchestration.settleInpatientPayment(settleCommand);
            AdjustAdvanceDepositBalanceCommand balanceCmd = new AdjustAdvanceDepositBalanceCommand();
            balanceCmd.setPatientId(patientId);
            balanceCmd.setEncounterId(encounterId);
            balanceCmd.setBalanceDelta(advanceAmount);
            balanceCmd.setOperatorId(request.getOperatorId());
            accountDomainService.adjustAdvanceDepositBalance(balanceCmd);
            return paymentId;
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new BusinessException(
                InpatientRegistrationConstants.EX_IP_REG_004,
                "预交金收款失败，请重试");
        }
    }

    /**
     *
     * @param request       登记请求
     * @param advanceAmount 预交金主金额
     * @throws BusinessException 明细缺失或合计不等时抛 EX-IP-REG-001
     */
    private void validateAdvanceDepositPaymentDetails(
        SubmitRegistrationRequest request,
        BigDecimal advanceAmount) {
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
     * 按源门诊就诊解析活动接诊医生姓名（ADMITTER），供门诊转住院登记弹窗只读展示。
     *
     * @param ambEncounterId 源门诊就诊 ID
     * @return 接诊医生姓名；无参与者或未维护姓名时返回 null
     */
    private String resolveOutpatientDoctorName(Long ambEncounterId) {
        if (ambEncounterId == null) {
            return null;
        }
        List<EncounterParticipantQueryModel> participants =
            encounterQueryService.listActiveEncounterParticipantsByType(
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

}
