/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.financial.registration.service;

import com.openhis.health.application.outpatient.financial.registration.dto.*;
import com.openhis.health.application.outpatient.financial.registration.support.OutpatientRegistrationCancelBlockResult;
import com.openhis.health.application.outpatient.financial.registration.support.OutpatientHistoricalFollowUpRegistrationGate;
import com.openhis.health.application.outpatient.financial.registration.support.OutpatientRegistrationCancelGate;
import com.openhis.health.application.outpatient.financial.registration.support.RegisterBenefitAreaHintHelper;
import com.openhis.health.application.outpatient.financial.registration.support.RegisterContractMatchResolver;
import com.openhis.health.application.outpatient.financial.registration.support.RegistrationReceiptPrintAssembler;
import com.openhis.health.application.outpatient.financial.registration.mapper.OutpatientRegistrationMapper;
import com.openhis.health.application.outpatient.financial.registration.request.*;
import com.openhis.health.application.outpatient.financial.registration.response.*;
import com.openhis.health.application.shared.financial.EncounterChargeAccountOpener;
import com.openhis.health.application.shared.financial.EncounterContractDisplayAssembler;
import com.openhis.health.application.shared.financial.InsuranceSettlementCashAlignSupport;
import com.openhis.health.domain.administration.account.entity.Account;
import com.openhis.health.domain.administration.account.service.AccountDomainService;
import com.openhis.health.domain.administration.account.service.AccountQueryService;
import com.openhis.health.domain.administration.chargeitem.command.BatchChargeItemIdsCommand;
import com.openhis.health.domain.administration.chargeitem.command.CreateChargeItemCommand;
import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitem.query.ChargeItemQueryCommand;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemDomainService;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.openhis.health.domain.administration.chargeitemdefinition.entity.ChargeItemDefinition;
import com.openhis.health.domain.administration.chargeitemdefinition.query.ChargeItemDefinitionQueryCommand;
import com.openhis.health.domain.administration.chargeitemdefinition.service.ChargeItemDefinitionQueryService;
import com.openhis.health.domain.administration.encounter.command.CancelOutpatientRegistrationCommand;
import com.openhis.health.domain.administration.encounter.command.RegisterOutpatientEncounterCommand;
import com.openhis.health.domain.administration.encounter.queries.ClinicRegisterPageQuery;
import com.openhis.health.domain.administration.encounter.queries.OutpatientDuplicateCheckQuery;
import com.openhis.health.domain.administration.encounter.queries.RegistrationSlipPageQuery;
import com.openhis.health.domain.administration.encounter.queries.model.ClinicRegisterQueryModel;
import com.openhis.health.domain.administration.encounter.queries.model.EncounterDetailQueryModel;
import com.openhis.health.domain.administration.encounter.queries.model.OutpatientDuplicateCheckQueryModel;
import com.openhis.health.domain.administration.encounter.queries.model.RegistrationSlipQueryModel;
import com.openhis.health.domain.administration.encounter.service.EncounterDomainService;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.domain.administration.healthcareservice.entity.HealthcareService;
import com.openhis.health.domain.administration.healthcareservice.service.HealthcareServiceQueryService;
import com.openhis.health.domain.administration.invoice.command.CancelInvoiceCommand;
import com.openhis.health.domain.administration.invoice.command.CreateDraftInvoiceCommand;
import com.openhis.health.domain.administration.invoice.entity.Invoice;
import com.openhis.health.domain.administration.invoice.query.InvoiceQueryCommand;
import com.openhis.health.domain.administration.invoice.service.InvoiceDomainService;
import com.openhis.health.domain.administration.invoice.service.InvoiceQueryService;
import com.openhis.health.domain.administration.organization.entity.Organization;
import com.openhis.health.domain.administration.organization.service.OrganizationQueryService;
import com.openhis.health.domain.administration.patient.command.RegisterPatientCommand;
import com.openhis.health.domain.administration.patient.command.UpdatePatientAddressCommand;
import com.openhis.health.domain.administration.patient.command.UpdatePatientPhoneCommand;
import com.openhis.health.domain.administration.patient.queries.PatientPagedQuery;
import com.openhis.health.domain.administration.patient.queries.model.PatientCoreQueryModel;
import com.openhis.health.domain.administration.patient.queries.model.PatientDetailQueryModel;
import com.openhis.health.domain.administration.patient.queries.model.PatientPageItemQueryModel;
import com.openhis.health.domain.administration.patient.queries.model.PatientSimpleQueryModel;
import com.openhis.health.domain.administration.patient.service.PatientDomainService;
import com.openhis.health.domain.administration.patient.service.PatientQueryService;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.administration.practitioner.service.PractitionerQueryService;
import com.openhis.health.domain.financial.contract.entity.Contract;
import com.openhis.health.domain.financial.contract.queries.model.ContractDetailQueryModel;
import com.openhis.health.domain.financial.contract.queries.model.ContractListItemQueryModel;
import com.openhis.health.domain.financial.contract.service.ContractQueryService;
import com.openhis.health.domain.financial.paymentreconciliation.command.AbortPreSettlementBeforePaymentCommand;
import com.openhis.health.domain.financial.paymentreconciliation.command.CreatePaymentDraftFromPreSettleCommand;
import com.openhis.health.domain.financial.paymentreconciliation.command.PaymentDetailItemCommand;
import com.openhis.health.domain.financial.paymentreconciliation.command.PaymentYbSettlementItemCommand;
import com.openhis.health.domain.financial.paymentreconciliation.command.RequestOutpatientRefundCommand;
import com.openhis.health.domain.financial.paymentreconciliation.command.SettleOutpatientPaymentCommand;
import com.openhis.health.domain.financial.paymentreconciliation.PaymentYbSettlementStatus;
import com.openhis.health.domain.financial.paymentreconciliation.queries.model.PaymentRecDetailQueryModel;
import com.openhis.health.domain.financial.paymentreconciliation.queries.model.PaymentReconciliationQueryModel;
import com.openhis.health.domain.financial.paymentreconciliation.service.PaymentReconciliationDomainService;
import com.openhis.health.domain.financial.paymentreconciliation.service.PaymentReconciliationQueryService;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.EncounterConstants;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.OutpatientRegistrationConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.constants.WhalePlatformRoleCodeConstants;
import com.openhis.health.domain.share.enums.*;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.health.domain.share.utils.InsuranceContractCategorySupport;
import com.openhis.health.domain.share.utils.MonetaryAmountSupport;
import com.openhis.health.domain.terminology.service.CodeSystemQueryService;
import com.openhis.health.application.shared.validator.CurrentManagedOrganizationIdsResolver;
import com.openhis.health.application.shared.validator.CurrentPractitionerResolver;
import com.openhis.whale.core.application.session.CurrentOperatorResolver;
import com.openhis.health.application.shared.validator.PageWindowGate;
import com.openhis.health.application.shared.financial.PaymentChannelOptionProvider;
import com.openhis.health.application.shared.validator.PaymentChannelGate;
import com.openhis.health.application.shared.payment.ThirdPartyPaymentRefundOrchestrator;
import com.openhis.health.application.shared.payment.ThirdPartyPaymentSettleOrchestrator;
import com.openhis.health.infrastructure.integration.payment.ThirdPartyPaymentExtension;
import com.openhis.health.application.basic.administration.support.PractitionerHealthcareServiceResolver;
import com.openhis.health.application.basic.administration.support.PractitionerRoleScopeLoader;
import com.openhis.health.application.outpatient.financial.registration.support.ClinicRegistrationLoader;
import com.openhis.health.infrastructure.integration.insurance.outpatient.orchestration.OutpatientChargeInsuranceCompensationOrchestration;
import com.openhis.health.infrastructure.integration.financial.orchestration.InvoiceAfterPaymentOrchestration;
import com.openhis.health.infrastructure.integration.financial.orchestration.PaymentReconciliationSettleOrchestration;
import com.openhis.health.infrastructure.integration.einvoice.orchestration.BossElectronicInvoiceAfterCommitOrchestration;
import com.openhis.health.infrastructure.integration.insurance.outpatient.OutpatientChargeInsuranceExtension;
import com.openhis.health.infrastructure.integration.insurance.outpatient.OutpatientRegistrationInsuranceExtension;
import com.openhis.health.infrastructure.integration.workinjury.WorkInjuryModuleExtension;
import com.openhis.health.infrastructure.integration.workinjury.WorkInjuryRegisterExtension;
import com.openhis.health.infrastructure.integration.workinjury.ExtensionOut.WorkInjuryCapabilityExtensionOut;
import com.openhis.health.infrastructure.integration.insurance.platformdict.InsurancePlatformDictDisplayExtension;
import com.openhis.health.infrastructure.integration.insurance.outpatient.ExtensionIn.OutpatientChargeConfirmExtensionIn;
import com.openhis.health.infrastructure.integration.insurance.outpatient.ExtensionIn.OutpatientChargePreSettleListExtensionIn;
import com.openhis.health.infrastructure.integration.insurance.outpatient.ExtensionIn.OutpatientChargePreSettleExtensionIn;
import com.openhis.health.infrastructure.integration.insurance.outpatient.ExtensionIn.OutpatientChargeRevokeFeeDetailExtensionIn;
import com.openhis.health.infrastructure.integration.insurance.outpatient.ExtensionIn.OutpatientRegistrationPlatformQueryExtensionIn;
import com.openhis.health.infrastructure.integration.insurance.outpatient.ExtensionIn.OutpatientRegistrationPlatformExtensionIn;
import com.openhis.health.infrastructure.integration.insurance.outpatient.ExtensionOut.OutpatientRegistrationPlatformExtensionOut;
import com.openhis.health.infrastructure.integration.insurance.outpatient.ExtensionOut.OutpatientRegistrationPlatformQueryExtensionOut;
import com.openhis.health.infrastructure.integration.insurance.outpatient.ExtensionOut.OutpatientChargeConfirmExtensionOut;
import com.openhis.health.infrastructure.integration.insurance.outpatient.ExtensionOut.OutpatientChargePreSettleExtensionOut;
import com.openhis.health.infrastructure.integration.insurance.outpatient.ExtensionOut.OutpatientChargePreSettleQueryExtensionOut;
import com.openhis.health.infrastructure.integration.insurance.outpatient.ExtensionOut.OutpatientChargeSettlementRecordExtensionOut;
import com.openhis.health.infrastructure.integration.insurance.outpatient.ExtensionIn.OutpatientChargeRevokeSettleExtensionIn;
import com.openhis.health.infrastructure.integration.insurance.benefit.InsuranceRegisterBenefitQueryExtension;
import com.openhis.health.infrastructure.integration.insurance.benefit.ExtensionIn.InsuranceCumulativeQueryExtensionIn;
import com.openhis.health.infrastructure.integration.insurance.benefit.ExtensionIn.InsuranceOpspFilingQueryExtensionIn;
import com.openhis.health.infrastructure.integration.insurance.benefit.ExtensionIn.InsurancePersonQueryExtensionIn;
import com.openhis.health.infrastructure.integration.insurance.benefit.ExtensionIn.InsuranceTreatmentCheckExtensionIn;
import com.openhis.health.infrastructure.integration.insurance.benefit.ExtensionOut.InsuranceCumulativeLineSnapshot;
import com.openhis.health.infrastructure.integration.insurance.benefit.ExtensionOut.InsuranceCumulativeQueryExtensionOut;
import com.openhis.health.infrastructure.integration.insurance.benefit.ExtensionOut.InsuranceCoverageLineSnapshot;
import com.openhis.health.infrastructure.integration.insurance.benefit.ExtensionOut.InsuranceOpspFilingLineSnapshot;
import com.openhis.health.infrastructure.integration.insurance.benefit.ExtensionOut.InsuranceOpspFilingQueryExtensionOut;
import com.openhis.health.infrastructure.integration.insurance.benefit.ExtensionOut.InsurancePersonQueryExtensionOut;
import com.openhis.health.infrastructure.integration.insurance.benefit.ExtensionOut.InsuranceTreatmentCheckExtensionOut;
import com.openhis.health.infrastructure.integration.insurance.benefit.ExtensionOut.InsuranceTreatmentLineSnapshot;
import com.openhis.health.infrastructure.integration.booking.ExtensionIn.BookableSlotsQueryExtensionIn;
import com.openhis.health.infrastructure.integration.booking.ExtensionIn.OutpatientBookingLookupExtensionIn;
import com.openhis.health.infrastructure.integration.booking.ExtensionIn.OutpatientBookingRegistrationValidateExtensionIn;
import com.openhis.health.infrastructure.integration.booking.ExtensionIn.OutpatientRegistrationCancelExtensionIn;
import com.openhis.health.infrastructure.integration.booking.ExtensionIn.OutpatientWalkInSlotAllocateExtensionIn;
import com.openhis.health.infrastructure.integration.booking.ExtensionIn.OutpatientBookingLoadExtensionIn;
import com.openhis.health.infrastructure.integration.booking.outpatient.OutpatientBookingQueryExtension;
import com.openhis.health.infrastructure.integration.booking.outpatient.OutpatientBookingRegistrationExtension;
import com.openhis.health.infrastructure.integration.booking.ExtensionOut.BookableSlotHintQueryExtensionOut;
import com.openhis.health.infrastructure.integration.booking.ExtensionOut.OutpatientBookingQueryExtensionOut;
import com.openhis.whale.core.application.dto.OptionDto;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 门诊挂号应用服务（AS-OP-ADM-001）。
 *
 * <p>职责边界：</p>
 * <ul>
 *   <li>Gate：请求参数合法性校验（患者信息、号源类型、科室医生等必填项）。</li>
 *   <li>Pre：患者主档解析（既有患者复用/身份证查重/新建建档）、就诊防重校验、主数据加载。</li>
 *   <li>Cmd：就诊开立（PLANNED 态）、账户开立（人人个人现金自费户；医保/工伤另开医保户）、
 *          挂号费按 ybRegisterFee 挂账、支付草稿生成、正式结算后状态迁移（REGISTERED 态）、发票开具。</li>
 *   <li>Asm：通过 {@link OutpatientRegistrationMapper} 统一装配响应 DTO，避免编排层直接拼装。</li>
 * </ul>
 *
 * <p>核心流程：</p>
 * <ol>
 *   <li>预结算（IF-REG-01）：患者解析 → 防重校验 → 就诊开立 → 账户开立 → 挂号费定价 → 收费项创建 → 支付草稿。</li>
 *   <li>正式缴费（IF-REG-02）：支付草稿校验 → 支付落账 → 收费项结算 → 状态迁移 → 发票开具。</li>
 *   <li>退号退费（IF-REG-04）：可退校验 → 就诊取消 → 退费窗口 → 红冲发票。</li>
 * </ol>
 *
 * <p>接入：由 {@code AppServiceControllerRegistrar} 动态注册为
 * {@code POST /api/app/outpatientRegistration/{methodName}}；不另写 {@code RestController}。</p>
 *
 * <p>本类不承载领域不变量定义，所有写入必须通过 DomainService 完成。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutpatientRegistrationAppService {

    /** 患者写侧领域服务。 */
    private final PatientDomainService patientDomainService;
    /** 患者读侧查询服务。 */
    private final PatientQueryService patientQueryService;
    /** 科室查询服务。 */
    private final OrganizationQueryService organizationQueryService;
    /** 医生查询服务。 */
    private final PractitionerQueryService practitionerQueryService;
    /** 岗位 → 医务人员主键预解析（B.6.1.3.1）。 */
    private final PractitionerRoleScopeLoader practitionerRoleScopeLoader;
    /** 医师可接诊号源收窄与 Gate。 */
    private final PractitionerHealthcareServiceResolver practitionerHealthcareServiceResolver;
    /** 费用性质查询服务。 */
    private final ContractQueryService contractQueryService;
    /** 诊疗服务查询服务。 */
    private final HealthcareServiceQueryService healthcareServiceQueryService;
    /** 就诊写侧领域服务。 */
    private final EncounterDomainService encounterDomainService;
    /** 就诊读侧查询服务。 */
    private final EncounterQueryService encounterQueryService;
    private final ClinicRegistrationLoader clinicRegistrationLoader;
    /** 退号收费前置门禁（BR-REG-015；仅账单三态）。 */
    private final OutpatientRegistrationCancelGate outpatientRegistrationCancelGate;
    /** 复诊0元号资格门禁（BR-REG-020；按名称识别）。 */
    private final OutpatientHistoricalFollowUpRegistrationGate outpatientHistoricalFollowUpRegistrationGate;
    /** 账户写侧领域服务。 */
    private final AccountDomainService accountDomainService;
    /** 账户读侧查询服务。 */
    private final AccountQueryService accountQueryService;
    /** 就诊挂账双账户幂等开立与挂号费挂户解析。 */
    private final EncounterChargeAccountOpener encounterChargeAccountOpener;
    /** 就诊费用性质展示（优先医保账户合同，挂号身份口径）。 */
    private final EncounterContractDisplayAssembler encounterContractDisplayAssembler;
    /** 收费定义查询服务。 */
    private final ChargeItemDefinitionQueryService chargeItemDefinitionQueryService;
    /** 收费项目写侧服务。 */
    private final ChargeItemDomainService chargeItemDomainService;
    /** 收费项目查询服务。 */
    private final ChargeItemQueryService chargeItemQueryService;
    /** 支付对账写侧领域服务。 */
    private final PaymentReconciliationDomainService paymentReconciliationDomainService;
    /** 支付对账读侧查询服务。 */
    private final PaymentReconciliationQueryService paymentReconciliationQueryService;
    /** 发票写侧领域服务。 */
    private final InvoiceDomainService invoiceDomainService;
    /** 发票查询服务。 */
    private final InvoiceQueryService invoiceQueryService;
    /** 门诊挂号读模型查询服务。 */    /** 挂号单读模型查询服务。 */    /** 门诊挂号应用层映射器。 */
    private final OutpatientRegistrationMapper outpatientRegistrationMapper;
    /** 术语枚举 → 下拉选项（性别等与患者主档对齐）。 */
    private final CodeSystemQueryService codeSystemQueryService;
    /** 当前登录用户上下文（展示名等，非操作人/机构门控）。 */
    private final CurrentOperatorResolver currentOperatorResolver;
    /** 会话机构门控（B.6.1）。 */
    private final CurrentManagedOrganizationIdsResolver managedOrganizationIdsResolver;
    /** 登录操作人门控（B.6.1）。 */
    private final CurrentPractitionerResolver currentPractitionerResolver;
    /** 分页窗口入站门控（B.10.11）。 */
    private final PageWindowGate pageWindowGate;

    /** 支付渠道入站门控。 */
    private final PaymentChannelGate paymentChannelGate;

    private final PaymentChannelOptionProvider paymentChannelOptionProvider;

    private final ThirdPartyPaymentSettleOrchestrator thirdPartyPaymentSettleOrchestrator;

    private final ThirdPartyPaymentRefundOrchestrator thirdPartyPaymentRefundOrchestrator;

    /** 第三方收单扩展（半成功：BPC SUCCESS 门禁）。 */
    private final ThirdPartyPaymentExtension thirdPartyPaymentExtension;

    /** 门诊挂号第三方平台登记扩展。 */
    private final OutpatientRegistrationInsuranceExtension outpatientRegistrationInsuranceExtension;

    /** 1901 平台字典展示（参保地 label）。 */
    private final InsurancePlatformDictDisplayExtension platformDictDisplayExtension;

    /** 门诊收费第三方结算扩展（挂号费医保 2203→2207）。 */
    private final OutpatientChargeInsuranceExtension chargeInsuranceExtension;

    /** 门诊挂号待遇第三方查询扩展（1101/2001/5302A/5206）。 */
    private final InsuranceRegisterBenefitQueryExtension registerBenefitQueryExtension;

    /** 工伤 Module capability 门控。 */
    private final WorkInjuryModuleExtension workInjuryModuleExtension;

    /** 工伤登记（9102 / qualification 绑定）。 */
    private final WorkInjuryRegisterExtension workInjuryRegisterExtension;

    /** 参保地与费用性质医保通道匹配。 */
    private final RegisterContractMatchResolver registerContractMatchResolver;

    /** 挂号收费部分医保正式结算失败后的 2208/2601 自动补偿编排。 */
    private final OutpatientChargeInsuranceCompensationOrchestration outpatientChargeInsuranceCompensationOrchestration;

    /** 门诊挂号预约只读 Extension（IF-REG-12～14）。 */
    private final OutpatientBookingQueryExtension outpatientBookingQueryExtension;

    /** 门诊挂号预约写侧衔接 Extension（IF-REG-07/10、现场占号）。 */
    private final OutpatientBookingRegistrationExtension outpatientBookingRegistrationExtension;

    private final PaymentReconciliationSettleOrchestration paymentReconciliationSettleOrchestration;
    private final BossElectronicInvoiceAfterCommitOrchestration bossElectronicInvoiceAfterCommitOrchestration;
    private final InvoiceAfterPaymentOrchestration invoiceAfterPaymentOrchestration;

    private final RegistrationReceiptPrintAssembler registrationReceiptPrintAssembler;

    /**
     * 加载挂号表单初始化数据。
     *
     * @param request 初始化请求
     * @return 初始化结果
     */
    public LoadRegisterFormInitResponse loadRegisterFormInit(LoadRegisterFormInitRequest request) {
        // Pre：加载科室、医生、费用性质、号源类型与性别下拉主数据。
        List<Organization> organizations = organizationQueryService.listOrganizationsForOutpatientRegistration();
        List<Practitioner> practitioners = practitionerRoleScopeLoader.listActivePractitionersForOutpatientRegistration(
            WhalePlatformRoleCodeConstants.ROLE_DOCTOR, request.getOrganizationId());
        Page<ContractListItemQueryModel> contractPage = contractQueryService.listContracts(request.getOrganizationId(), Pageable.unpaged());
        List<ContractListItemQueryModel> contracts = contractPage.getContent();
        List<HealthcareService> healthcareServices = healthcareServiceQueryService.listServicesForRegistration();
        // Pre：指定医师时按用户管理可接诊号源收窄（BR-REG-014）；与排班/放号无关，禁止 ∩ 当日 Schedule
        healthcareServices = practitionerHealthcareServiceResolver.narrowByPractitioner(
            healthcareServices, request.getPractitionerId());
        Map<String, BigDecimal> serviceTypeFeeById = buildServiceTypeFeeById(healthcareServices);
        // Pre：性别下拉与患者建档页一致。
        List<OptionDto> genderOptions = codeSystemQueryService.getOptionListByEnum(AdministrativeGender.class);
        // Asm：统一转换为前端下拉结构并返回。
        LoadRegisterFormInitResponse response = outpatientRegistrationMapper.toLoadRegisterFormInitResponse(
            outpatientRegistrationMapper.toOrganizationOptionList(organizations),
            outpatientRegistrationMapper.toPractitionerOptionList(practitioners),
            outpatientRegistrationMapper.toContractOptionList(contracts),
            outpatientRegistrationMapper.toHealthcareServiceOptionList(healthcareServices),
            serviceTypeFeeById,
            genderOptions
        );
        Map<String, String> contractCategoryById = new LinkedHashMap<>();
        for (ContractListItemQueryModel contract : contracts) {
            if (contract.getId() != null) {
                contractCategoryById.put(String.valueOf(contract.getId()), contract.getCategoryEnum());
            }
        }
        response.setContractCategoryById(contractCategoryById);
        Map<String, ContractInsuranceProfileDto> contractInsuranceProfileById = new LinkedHashMap<>();
        for (ContractListItemQueryModel contract : contracts) {
            if (contract.getId() == null) {
                continue;
            }
            // Pre：列表项已带 adm_vs/yb_flag，禁止再按合同 id 循环回表实体（原 N+1）
            ContractInsuranceProfileDto profile = new ContractInsuranceProfileDto()
                .setContractId(String.valueOf(contract.getId()))
                .setCategoryCode(contract.getCategoryEnum())
                .setBusNo(contract.getBusNo())
                .setTreatmentAreaCode(
                    registerContractMatchResolver.resolveTreatmentAreaCode(contract.getId(), contract.getAdmVs()))
                .setYbFlag(contract.getYbFlag());
            contractInsuranceProfileById.put(String.valueOf(contract.getId()), profile);
        }
        response.setContractInsuranceProfileById(contractInsuranceProfileById);
        response.setPaymentChannelOptions(paymentChannelOptionProvider.loadUserSelectableOptions());
        if (workInjuryModuleExtension.isEnabled()) {
            WorkInjuryCapabilityExtensionOut capability = workInjuryModuleExtension.capability();
            response.setWorkInjuryCapability(capability);
        }
        return response;
    }

    /**
     * 加载挂号待遇预览（医保 1101 / 学生公费学籍）。
     */
    public LoadRegisterBenefitPreviewResponse loadRegisterBenefitPreview(LoadRegisterBenefitPreviewRequest request) {
        // Gate：费用性质必填，否则无法判定待遇分支。
        if (request.getContractId() == null) {
            throw new BusinessException("REG-VAL-001", "费用性质不能为空");
        }
        ContractDetailQueryModel contract = contractQueryService.findContractById(request.getContractId());
        if (contract == null) {
            throw new BusinessException("REG-BIZ-404", "主数据失效，请刷新");
        }
        LoadRegisterBenefitPreviewResponse response = new LoadRegisterBenefitPreviewResponse();
        // Pre：工伤合同走 Win 9102 提示，不调用医保 1101。
        if (this.isWorkInjuryContract(request.getContractId())) {
            response.setBenefitKind("WORK_INJURY");
            response.setHintMessage("工伤挂号须 OpenHIS.Win 完成 9102 审批查询；挂号费一律自费");
            return response;
        }
        if (isPublicChargeContract(contract)) {
            return buildStudentBenefitPreview(request, response);
        }
        if (isInsuranceContract(contract)) {
            return buildInsuranceBenefitPreview(request, response);
        }
        response.setBenefitKind("NONE");
        return response;
    }

    /**
     * 批量解析号源类型对应的 SALE 零售价，供挂号页选择号源后展示挂号费。
     *
     * @param services 可挂号号源列表
     * @return 号源主键（String）→ 销售价（元）
     */
    private Map<String, BigDecimal> buildServiceTypeFeeById(List<HealthcareService> services) {
        Map<String, BigDecimal> result = new LinkedHashMap<>();
        if (services == null || services.isEmpty()) {
            return result;
        }
        List<Long> serviceIds = new ArrayList<>();
        for (HealthcareService service : services) {
            if (service != null && service.getId() != null) {
                serviceIds.add(service.getId());
            }
        }
        if (serviceIds.isEmpty()) {
            return result;
        }
        ChargeItemDefinitionQueryCommand queryCommand = new ChargeItemDefinitionQueryCommand();
        queryCommand.setInstanceTable(TableNameConstants.ADM_HEALTHCARE_SERVICE);
        queryCommand.setInstanceIds(serviceIds);
        List<ChargeItemDefinition> priceRows = chargeItemDefinitionQueryService.findPriceLinesByProjects(queryCommand);
        for (ChargeItemDefinition row : priceRows) {
            if (row == null || row.getInstanceId() == null || row.getPriceValue() == null) {
                continue;
            }
            if (!PriceType.SALE.getCode().equals(row.getPriceTypeCode())) {
                continue;
            }
            result.putIfAbsent(String.valueOf(row.getInstanceId()), row.getPriceValue());
        }
        return result;
    }

    /**
     * 分页检索可选患者。
     *
     * @param request 分页请求
     * @return 分页结果
     * @throws BusinessException 页码或页大小非法时抛出
     */
    public PagePatientsForSelectResponse pagePatientsForSelect(PagePatientsForSelectRequest request) {
        // Gate：分页窗口须为正。
        pageWindowGate.requirePositivePageWindow(request, "REG-PAT-VAL-001", "分页参数不合法");
        // Pre：组装领域分页查询命令。
        PatientPagedQuery query = new PatientPagedQuery();
        query.setKeyword(request.getKeyword());
        query.setPageNo(request.getPageNo());
        query.setPageSize(request.getPageSize());
        // Pre：查询患者分页。
        Page<PatientPageItemQueryModel> page = patientQueryService.searchPatientsPaged(query);
        // Asm：映射为前端可选患者列表。
        List<PatientItemDto> records = outpatientRegistrationMapper.toPatientItemDtoList(page);
        return outpatientRegistrationMapper.toPagePatientsForSelectResponse(page, records);
    }

    /**
     * 查询可挂号科室列表。
     *
     * @param request 科室查询请求
     * @return 科室下拉结果
     */
    public ListDepartmentsResponse listDepartments(ListDepartmentsRequest request) {
        // Pre：读取可挂号科室并映射为下拉结构。
        List<Organization> organizations = organizationQueryService.listOrganizationsForOutpatientRegistration();
        // Asm：OptionDto 列表包装为响应。
        return outpatientRegistrationMapper.toListDepartmentsResponse(outpatientRegistrationMapper.toOrganizationOptionList(organizations));
    }

    /**
     * 查询科室下可挂号医生列表。
     *
     * @param request 医生查询请求
     * @return 医生下拉结果
     */
    public ListPractitionersResponse listPractitioners(ListPractitionersRequest request) {
        // Pre：按科室加载可挂号医生（ROLE_DOCTOR 岗位范围）。
        List<Practitioner> practitioners = practitionerRoleScopeLoader.listActivePractitionersForOutpatientRegistration(
            WhalePlatformRoleCodeConstants.ROLE_DOCTOR, request.getOrganizationId());
        // Asm：映射为医生下拉响应。
        return outpatientRegistrationMapper.toListPractitionersResponse(outpatientRegistrationMapper.toPractitionerOptionList(practitioners));
    }

    /**
     * IF-REG-12：挂号台加载有效门诊预约。
     */
    public LoadAppointmentForRegisterResponse loadAppointmentForRegister(LoadAppointmentForRegisterRequest request) {
        // Gate：预约模块须启用，否则挂号台不可加载预约。
        if (!outpatientBookingQueryExtension.isEnabled()) {
            throw new BusinessException("BKG-REG-404", "预约模块未启用");
        }
        if (request == null) {
            throw new BusinessException("REG-VAL-001", "查询参数不完整");
        }
        boolean hasBusNo = StringUtils.isNotBlank(request.getAppointmentBusNo());
        // Gate：patientId 与 appointmentBusNo 至少传一，否则无法定位预约。
        if (!hasBusNo && request.getPatientId() == null) {
            throw new BusinessException("REG-VAL-001", "patientId 与 appointmentBusNo 至少传一个");
        }
        // Pre：组装 booking 查询命令并解析挂号日。
        OutpatientBookingLookupExtensionIn lookupQuery = new OutpatientBookingLookupExtensionIn();
        lookupQuery.setPatientId(request.getPatientId());
        lookupQuery.setAppointmentBusNo(request.getAppointmentBusNo());
        LocalDate registrationDate;
        if (request.getRegistrationDate() != null) {
            registrationDate = request.getRegistrationDate();
        } else {
            registrationDate = LocalDate.now();
        }
        lookupQuery.setRegistrationDate(registrationDate);
        OutpatientBookingQueryExtensionOut snapshot = outpatientBookingQueryExtension.findActiveOutpatientBooking(lookupQuery)
            .orElseThrow(() -> new BusinessException("BKG-REG-404", "未找到有效预约"));
        return outpatientRegistrationMapper.toLoadAppointmentForRegisterResponse(snapshot);
    }

    /**
     * IF-REG-13：预约转挂号预结算（Gate + 复用 IF-REG-06）。
     */
    public PreRegisterSettlementResponse preRegisterFromAppointment(PreRegisterSettlementRequest request) {
        if (request == null || request.getAppointmentId() == null) {
            throw new BusinessException("REG-VAL-001", "appointmentId 不能为空");
        }
        if (!outpatientBookingQueryExtension.isEnabled()) {
            throw new BusinessException("BKG-REG-404", "预约模块未启用");
        }
        return this.preRegisterSettlement(request);
    }

    /**
     * IF-REG-14：现场挂号可约时段摘要。
     */
    public ListBookableSlotHintsResponse listBookableSlotHints(ListBookableSlotHintsRequest request) {
        ListBookableSlotHintsResponse response = new ListBookableSlotHintsResponse();
        response.setHints(new ArrayList<>());
        if (!outpatientBookingQueryExtension.isEnabled() || request == null || request.getOrganizationId() == null) {
            return response;
        }
        LocalDate registrationDate;
        if (request.getRegistrationDate() != null) {
            registrationDate = request.getRegistrationDate();
        } else {
            registrationDate = LocalDate.now();
        }
        Long healthcareServiceId = request.getHealthcareServiceId();
        List<BookableSlotHintQueryExtensionOut> hints = outpatientBookingQueryExtension.listBookableSlots(
            new BookableSlotsQueryExtensionIn()
                .setOrganizationId(request.getOrganizationId())
                .setPractitionerId(request.getPractitionerId())
                .setHealthcareServiceId(healthcareServiceId)
                .setDate(registrationDate));
        return outpatientRegistrationMapper.toListBookableSlotHintsResponse(hints);
    }

    /**
     * 执行挂号预结算并生成支付草稿。
     *
     * @param request 预结算请求
     * @return 预结算结果
     * @throws BusinessException 挂号要素不完整、重复挂号、主数据失效或定价不存在时抛出
     */
    public PreRegisterSettlementResponse preRegisterSettlement(PreRegisterSettlementRequest request) {
        // Gate：门控挂号关键要素，避免后续编排出现空主键；医生可选（BR-REG-001，未选则挂到科室）。
        if (request.getContractId() == null || request.getOrganizationId() == null || request.getServiceTypeId() == null) {
            throw new BusinessException("REG-VAL-001", "请补齐挂号要素");
        }
        // 预结算前先收敛患者主档：优先使用已选患者，其次按身份证命中既有患者，未命中则新建患者。
        Long patientId = resolvePatientIdForPreRegister(request);
        request.setPatientId(patientId);
        if (request.getAppointmentId() != null && outpatientBookingQueryExtension.isEnabled()) {
            this.runBookingRegistrationGate(request);
        }
        // 与开立就诊同一业务时刻，保证防重自然日与领域 occurredAt 一致。
        LocalDateTime occurredAt = LocalDateTime.now();
        // 同患者、同目标科室、当日：凡占名额就诊（含待挂号 PLANNED 与诊毕）均阻断；仅 CANCELLED 撤销/退号后释放。
        // Pre：应用层早失败给出 REG-BIZ-409；领域 registerOutpatientEncounter 仍会再断言一次防并发窗。
        OutpatientDuplicateCheckQuery duplicateCheckQuery = new OutpatientDuplicateCheckQuery();
        duplicateCheckQuery.setPatientId(patientId);
        duplicateCheckQuery.setOrganizationId(request.getOrganizationId());
        duplicateCheckQuery.setRegistrationDate(occurredAt.toLocalDate());
        OutpatientDuplicateCheckQueryModel duplicateCheckQueryModel = encounterQueryService.checkOutpatientDuplicateRegistration(duplicateCheckQuery);
        if (Boolean.TRUE.equals(duplicateCheckQueryModel.getDuplicated())) {
            throw new BusinessException("REG-BIZ-409", EncounterConstants.OUTPATIENT_DUPLICATE_REGISTRATION_MESSAGE);
        }
        // 装载结算所需主数据，主数据缺失时拒绝继续。
        ContractDetailQueryModel contract = contractQueryService.findContractById(request.getContractId());
        Organization organization = organizationQueryService.loadOrganizationByIdForDisplay(request.getOrganizationId());
        HealthcareService healthcareService = healthcareServiceQueryService.loadHealthcareServiceById(request.getServiceTypeId());
        if (contract == null || organization == null || healthcareService == null) {
            throw new BusinessException("REG-BIZ-404", "主数据失效，请刷新");
        }
        // Gate：复诊0元号须同科室接诊后 72 小时内（BR-REG-020；按名称识别）；下拉可见，写侧拦截
        outpatientHistoricalFollowUpRegistrationGate.assertEligibleForHistoricalFollowUpRegistration(
            patientId, request.getOrganizationId(), healthcareService, occurredAt);
        String contractBusNo = null;
        if (StringUtils.isNotBlank(contract.getBusNo())) {
            contractBusNo = contract.getBusNo().trim();
        }
        // 若传入医生标识，则校验主数据并取出展示名，供挂号命令与领域事件写入列表医生列。
        Practitioner registrationPractitioner = null;
        if (request.getPractitionerId() != null) {
            registrationPractitioner = practitionerQueryService.findById(request.getPractitionerId());
            if (registrationPractitioner == null) {
                throw new BusinessException("REG-BIZ-404", "主数据失效，请刷新");
            }
            // Gate：指定医师时号源须在其可接诊范围内（BR-REG-014）
            practitionerHealthcareServiceResolver.assertPractitionerAcceptsHealthcareService(
                request.getPractitionerId(), request.getServiceTypeId());
        }
        String registrationPractitionerDisplayName = null;
        if (registrationPractitioner != null) {
            registrationPractitionerDisplayName = registrationPractitioner.getName();
        }

        // 挂号员与患者快照：投影依赖事件携带展示字段，须在此拉齐主档后再组装命令。
        // Pre：操作人只解析一次，支付草稿 enterer 复用，避免重复会话读。
        Long registrarId = currentPractitionerResolver.requireOperatorId("REG-VAL-001", "当前登录用户不能为空");
        // Pre：仅「点选号源」传 slotId 才占号；纯表单手动挂号与排班库存解绑，禁止按科室策略自动占号
        boolean shouldAllocateWalkInSlot = false;
        if (request.getAppointmentId() == null
            && request.getSlotId() != null
            && outpatientBookingRegistrationExtension.isEnabled()) {
            shouldAllocateWalkInSlot = true;
        }
        PatientDetailQueryModel patientDetail = patientQueryService.loadPatientDetail(patientId);
        PatientCoreQueryModel patientCore = null;
        if (patientDetail != null) {
            patientCore = patientDetail.getPatient();
        }

        // 查询挂号收费定义，缺失则无法生成预结算草稿。
        ChargeItemDefinitionQueryCommand priceCommand = new ChargeItemDefinitionQueryCommand();
        priceCommand.setInstanceId(request.getServiceTypeId());
        ChargeItemDefinition saleLine = chargeItemDefinitionQueryService.findSaleLineForHealthcareServiceRegistration(priceCommand);
        if (saleLine == null) {
            throw new BusinessException("REG-BIZ-404", "挂号定价不存在");
        }
        // Asm：支付草稿金额与创建命令一口价同源（create 落库写此值，preSettle 不改价），省一次按 id 回读 ChargeItem。
        BigDecimal chargeItemAmount = saleLine.getPriceValue();
        if (chargeItemAmount == null) {
            chargeItemAmount = BigDecimal.ZERO;
        }
        // 先开立就诊并发布计划事件，保证读模型行存在且收费明细可绑定真实 encounterId；否则挂号收费创建事件的投影无法累加列表金额。
        RegisterOutpatientEncounterCommand encounterCommand = outpatientRegistrationMapper.toRegisterOutpatientEncounterCommand(
            request,
            organization,
            healthcareService,
            patientCore,
            registrarId,
            currentOperatorResolver.findOperatorUserNameOrNull(),
            occurredAt,
            registrationPractitionerDisplayName
        );
        Long encounterId = encounterDomainService.registerOutpatientEncounter(encounterCommand);
        if (encounterId == null) {
            throw new BusinessException("REG-BIZ-500", "创建就诊失败，无法生成支付草稿");
        }
        Long walkInSlotId = null;
        if (shouldAllocateWalkInSlot) {
            // Cmd：就诊锚点已齐，现场占号并写预约行（同事务；失败整单回滚）。
            walkInSlotId = this.allocateWalkInSlotOrThrow(
                request, patientId, registrarId, occurredAt.toLocalDate(), encounterId);
        }
        // Pre：医保挂号确认即 2201 写 map(11)，供事前事中复用；与 ybRegisterFee 解耦；自费/非医保跳过
        this.runInsuranceRegistrationIfNeeded(request, encounterId);
        // Cmd：人人开个人现金自费户；省/市医保或工伤另开 MEDICAL_INSURANCE（所选合同）；已存在不改历史 contract_no
        boolean openMedicalInsuranceAccount = this.needsParallelMedicalInsuranceAccount(contract);
        encounterChargeAccountOpener.ensureEncounterChargeAccounts(
            patientId,
            encounterId,
            contractBusNo,
            openMedicalInsuranceAccount,
            request.getInsutype(),
            registrarId);
        // Pre：挂号费挂账——勾选医保支付挂号费走医保户，否则走个人现金户；跨省强制自费
        boolean ybRegisterFee = this.resolveYbRegisterFeeForRequest(request);
        Account registrationFeeAccount =
            encounterChargeAccountOpener.requireRegistrationFeeAccount(encounterId, ybRegisterFee);
        // TODO(ADR-CMP-001 D12)：Campus 启用且 ArchiveQueryReader 命中学生档案时，幂等开立
        // AccountTypeCode.PUBLIC_EXPENSE_ACCOUNT；教职工挂号不开，收费选 gfChargeMode 时再处理。
        // 在就诊锚点与账户锚点齐备后创建挂号收费项并预结算，使 RegistrationChargeItemCreatedEvent 携带 encounterId/totalPrice。
        CreateChargeItemCommand createChargeItemCommand = outpatientRegistrationMapper.toCreateChargeItemCommand(
            request, saleLine, encounterId, occurredAt, registrationFeeAccount.getId(), organization.getId());
        createChargeItemCommand.setOperatorId(registrarId);
        Long chargeItemId = chargeItemDomainService.createRegistrationChargeItem(createChargeItemCommand);
        BatchChargeItemIdsCommand preSettleCommand = outpatientRegistrationMapper.toSingleChargeItemBatchCommand(chargeItemId);
        chargeItemDomainService.preSettleChargeItems(preSettleCommand);
        // 使用就诊主键创建支付草稿，避免 payment.encounter_id 为空触发数据库约束异常。
        List<PaymentDetailItemCommand> paymentDetails = new ArrayList<>();
        CreatePaymentDraftFromPreSettleCommand paymentDraftCommand = outpatientRegistrationMapper
            .toCreatePaymentDraftFromPreSettleCommand(
                request, encounterId, chargeItemId, chargeItemAmount, paymentDetails, contractBusNo);
        // 支付草稿经办人复用上方已解析的挂号员，避免二次 requireOperatorId。
        paymentDraftCommand.setEntererId(registrarId);
        Long paymentDraftId = paymentReconciliationDomainService.createPaymentDraftFromPreSettle(paymentDraftCommand);
        List<ChargeItemPreviewDto> previews = outpatientRegistrationMapper.toChargeItemPreviewDtoListBySaleLine(saleLine);
        Long responseAppointmentId = request.getAppointmentId();
        Long responseSlotId = walkInSlotId;
        if (responseAppointmentId != null && outpatientBookingQueryExtension.isEnabled()) {
            Optional<OutpatientBookingQueryExtensionOut> bookingSnapshot =
                outpatientBookingQueryExtension.loadOutpatientBookingById(
                    new OutpatientBookingLoadExtensionIn().setAppointmentId(responseAppointmentId));
            if (bookingSnapshot.isPresent()) {
                responseSlotId = bookingSnapshot.get().getSlotId();
            }
        }
        PreRegisterSettlementResponse response = outpatientRegistrationMapper.toPreRegisterSettlementResponse(
            encounterId,
            paymentDraftId,
            chargeItemAmount,
            previews,
            responseAppointmentId,
            responseSlotId
        );
        this.applyRegisterFeeInsurancePreSettleIfNeeded(
            request, paymentDraftId, chargeItemId, encounterId, registrationFeeAccount, response);
        return response;
    }

    /**
     * 医保挂号确认：确保平台 2201 建档（{@code med_type=11}），写 {@code yb_encounter_platform_map}。
     *
     * <p>自费/非医保合同/工伤不调；须已读卡有 {@code psnNo}。与是否结挂号费（{@code ybRegisterFee}）无关。</p>
     *
     * @param request     预挂号请求
     * @param encounterId 已创建的就诊 id
     */
    private void runInsuranceRegistrationIfNeeded(PreRegisterSettlementRequest request, Long encounterId) {
        if (request == null || encounterId == null) {
            return;
        }
        if (this.isWorkInjuryContract(request.getContractId())) {
            return;
        }
        if (!outpatientRegistrationInsuranceExtension.isEnabled()
            || request.getContractId() == null
            || !outpatientRegistrationInsuranceExtension.supportsThirdPartyContract(request.getContractId())) {
            return;
        }
        if (StringUtils.isBlank(request.getPsnNo())) {
            throw new BusinessException("REG-VAL-002", "医保挂号须先读卡获取人员编号");
        }
        // Pre：挂号建档强制普通门诊 11，禁止请求覆盖（慢病等在结算时再补 2201）
        OutpatientRegistrationPlatformExtensionIn command = new OutpatientRegistrationPlatformExtensionIn()
            .setEncounterId(encounterId)
            .setContractId(request.getContractId())
            .setPayerCertType(request.getYbMediumType())
            .setPayerCertNo(request.getYbCardInfo())
            .setInsuredPersonNo(request.getPsnNo())
            .setInsuplcAdmdvs(request.getInsuplcAdmdvs())
            .setInsutype(request.getInsutype())
            .setMedType(YbMedType.GENERAL_OUTPATIENT.getCode());
        outpatientRegistrationInsuranceExtension.ensurePlatformRegistration(command);
    }

    /**
     * 预结算在 {@code ybRegisterFee=true} 时走 Extension：resolveMdtrtId(11)→2203→2204→2206，并刷新支付草稿个人现金行。
     *
     * @param request              预结算请求（含 ybRegisterFee / 读卡字段）
     * @param paymentDraftId       支付草稿 id
     * @param chargeItemId         挂号 chargeItem id
     * @param encounterId          就诊 id
     * @param registrationAccount  挂号账户
     * @param response             预结算响应（回填医保分摊）
     * @throws BusinessException 工伤/未装配医保/缺 psnNo 等
     */
    private void applyRegisterFeeInsurancePreSettleIfNeeded(
        PreRegisterSettlementRequest request,
        Long paymentDraftId,
        Long chargeItemId,
        Long encounterId,
        Account registrationAccount,
        PreRegisterSettlementResponse response) {
        // Gate：工伤合同禁止挂号费走医保账户
        if (this.isWorkInjuryContract(request.getContractId())) {
            if (Boolean.TRUE.equals(request.getYbRegisterFee())) {
                throw new BusinessException("WI-005", "工伤挂号费须自费，不得走医保/工伤账户");
            }
            return;
        }
        // Gate：ybRegisterFee=false → 纯本地草稿，不写 map(11)
        if (!Boolean.TRUE.equals(request.getYbRegisterFee())) {
            return;
        }
        if (!chargeInsuranceExtension.isEnabled()) {
            throw new BusinessException("REG-VAL-003", "未装配医保结算模块，无法挂号费医保预结算");
        }
        if (request.getContractId() == null
            || !chargeInsuranceExtension.supportsThirdPartyContract(request.getContractId())) {
            throw new BusinessException("REG-VAL-003", "挂号费医保须选择医保费用性质");
        }
        if (StringUtils.isBlank(request.getPsnNo())) {
            throw new BusinessException("REG-VAL-002", "医保挂号须先读卡获取人员编号");
        }
        // Pre：挂号费传 med_type=11（普通门诊）；长春等院点不支持 12，与诊疗费共用 map
        String medType = YbMedType.GENERAL_OUTPATIENT.getCode();
        if (StringUtils.isNotBlank(request.getMedType())) {
            medType = request.getMedType().trim();
        }
        OutpatientChargePreSettleExtensionIn command = new OutpatientChargePreSettleExtensionIn();
        command.setEncounterId(encounterId);
        command.setChargeItemIds(List.of(chargeItemId));
        command.setContractId(request.getContractId());
        command.setPaymentDraftId(paymentDraftId);
        command.setPayerCertType(request.getYbMediumType());
        command.setPayerCertNo(request.getYbCardInfo());
        command.setInsuredPersonNo(request.getPsnNo());
        command.setInsuplcAdmdvs(request.getInsuplcAdmdvs());
        command.setInsutype(request.getInsutype());
        command.setMedType(medType);
        command.setAcctUsedFlag(this.resolveRegisterFeeAcctUsedFlag(request.getYbUseAcctPay()));
        // Cmd：统一预结算链（首启 2201 仅 map miss 时）
        OutpatientChargePreSettleExtensionOut ybResult = chargeInsuranceExtension.preSettle(command);
        BigDecimal psnCashPay = BigDecimal.ZERO;
        if (ybResult.getPsnCashPay() != null) {
            psnCashPay = ybResult.getPsnCashPay();
        }
        List<PaymentDetailItemCommand> draftLines = new ArrayList<>();
        PaymentDetailItemCommand draftLine = new PaymentDetailItemCommand();
        draftLine.setAccountId(registrationAccount.getId());
        draftLine.setAccountCode(registrationAccount.getBusNo());
        draftLine.setPayEnum(PaymentChannel.CASHIER.getCode());
        draftLine.setAmount(psnCashPay);
        draftLine.setChargeAmount(psnCashPay);
        draftLine.setResultEnum(PaymentStatus.DRAFT.getCode());
        draftLines.add(draftLine);
        paymentReconciliationDomainService.refreshPaymentDraftPreSettleAmounts(
            paymentDraftId, psnCashPay, draftLines);
        response.setInsurancePreSettled(Boolean.TRUE);
        response.setYbChrgBchno(ybResult.getChargeBatchNo());
        response.setYbMedfeeSumamt(ybResult.getMedfeeSumamt());
        response.setYbFundPaySumamt(ybResult.getFundPaySumamt());
        response.setYbAcctPay(ybResult.getAcctPay());
        response.setYbPsnCashPay(ybResult.getPsnCashPay());
        // Asm：起付线/公务员仅确认弹窗展示，不改 totalAmount / 支付草稿
        response.setYbActPayDedc(ybResult.getActPayDedc());
        response.setYbCvlservFlag(ybResult.getCvlservFlag());
        response.setYbCvlservPay(ybResult.getCvlservPay());
        response.setTotalAmount(psnCashPay);
    }

    /**
     * 执行挂号缴费并开具电子发票。
     *
     * @param request 缴费请求
     * @return 缴费结果
     * @throws BusinessException 缴费参数不完整、草稿不可结算、关联收费明细缺失或与草稿就诊/患者/金额不一致时抛出
     */
    public PayRegisterFeeResponse payRegisterFee(PayRegisterFeeRequest request) {
        // Gate：支付草稿与明细必填，缺失任一参数禁止进入结算。
        boolean missingRequiredParam = request.getPaymentDraftId() == null
            || request.getPaymentDetails() == null;
        if (missingRequiredParam) {
            throw new BusinessException("PAY-VAL-001", "结算参数不完整");
        }
        if (request.getPaymentDetails().isEmpty()) {
            request.setPaymentDetails(new ArrayList<>());
        }

        // Pre：读取预结算支付草稿；补开户时仍按双账户口径（个人现金自费 + 可选医保户）
        // 草稿不存在或状态异常时抛出业务异常
        PaymentReconciliationQueryModel paymentDraft = paymentReconciliationQueryService.mustGetPayment(request.getPaymentDraftId());
        this.ensureAccountsForPayRegisterFee(paymentDraft);
        // 第三步：复用预结算已创建的挂号收费明细，不再重复创建；支付头金额以草稿为准并与明细金额交叉校验。
        ChargeItem registrationChargeLine = this.loadRegistrationChargeItemForPayRegisterFee(paymentDraft);
        Long chargeItemId = registrationChargeLine.getId();
        // Pre：支付明细挂账与挂号费 ChargeItem.account_id 对齐（医保付挂号费时为 MEDICAL_INSURANCE）
        Account paymentAccount = this.resolvePaymentAccountForRegisterFee(registrationChargeLine, paymentDraft);
        for (PaymentDetailDto detail : request.getPaymentDetails()) {
            detail.setPayChannelCode(paymentChannelGate.requireUserSelectablePayChannelCode(
                detail.getPayChannelCode(),
                "PAY-VAL-001",
                "支付渠道不合法"));
            detail.setAccountId(paymentAccount.getId());
            detail.setAccountCode(paymentAccount.getBusNo());
        }
        List<PaymentRecDetailQueryModel> draftDetails =
            paymentReconciliationQueryService.listPaymentRecDetailsByPaymentId(request.getPaymentDraftId());
        BigDecimal expectedPayable = this.resolveRegisterFeeExpectedPayable(paymentDraft, registrationChargeLine);
        if (expectedPayable.compareTo(BigDecimal.ZERO) > 0 && request.getPaymentDetails().isEmpty()) {
            throw new BusinessException("PAY-VAL-001", "结算参数不完整");
        }
        BigDecimal actualPayable = this.sumRegisterFeeSubmitAmount(request);
        if (expectedPayable.compareTo(actualPayable) != 0) {
            throw new BusinessException("PAY-VAL-002", "支付金额与预结算不一致");
        }
        // Gate：扫码金额先校验；clientScanResult 在 2207 之后再校验（允许先医保后银联）。
        this.assertScanPayChannelAmountValid(request);
        List<OutpatientChargeConfirmExtensionOut> ybConfirmResults = List.of();
        try {
            ybConfirmResults = this.collectRegisterFeeInsuranceConfirmResults(request, paymentDraft);
            this.assertClientScanResultPresentForScanPay(request);
            SettleOutpatientPaymentCommand settleOutpatientPaymentCommand =
                outpatientRegistrationMapper.toSettleOutpatientPaymentCommand(request);
            settleOutpatientPaymentCommand.setYbSettlements(this.buildRegisterFeeYbSettlementItems(ybConfirmResults));
            this.mergeRegisterFeeInsurancePaymentDetails(
                settleOutpatientPaymentCommand,
                ybConfirmResults,
                draftDetails,
                registrationChargeLine.getTotalPrice());
            Long operatorId = currentPractitionerResolver.requireOperatorId("PAY-VAL-001", "当前登录用户不能为空");
            Long orgId = managedOrganizationIdsResolver.requireSessionOrganizationUnitId("PAY-VAL-001", "当前登录机构不能为空");
            thirdPartyPaymentSettleOrchestrator.applyScanBeforeSettle(
                request.getPaymentDraftId(),
                settleOutpatientPaymentCommand.getPayReceiptDetails(),
                request.getClientScanResult(),
                operatorId,
                orgId);
            // Gate：扫码后的终态勾稽；纯自费与医保混结均须明细合计=号费（BR-OPC-024 终态）
            if (!InsuranceSettlementCashAlignSupport.isDetailSumMatchedChargeTotal(
                settleOutpatientPaymentCommand.getPayReceiptDetails(),
                registrationChargeLine.getTotalPrice())) {
                throw new BusinessException(
                    ErrorCodeConstants.PAYMENT_RECONCILIATION_RULE_CONFLICT,
                    "挂号落账明细合计与收费项不一致");
            }
            Long paymentId = paymentReconciliationSettleOrchestration.settleOutpatientPayment(settleOutpatientPaymentCommand);
            BatchChargeItemIdsCommand settleItemsCommand = outpatientRegistrationMapper.toSingleChargeItemBatchCommand(chargeItemId);
            // Cmd：收费人/收费时间写入 enterer_*（与 payment.enterer_id / bill_date 同源，BR-OPC-016）
            settleItemsCommand.setOperatorId(operatorId);
            settleItemsCommand.setBillDate(settleOutpatientPaymentCommand.getBillDate());
            chargeItemDomainService.settleChargeItems(settleItemsCommand);
            // 缴费成功后将就诊状态从 PLANNED 迁移至 REGISTERED，与 EncounterStatus 生命周期对齐。
            encounterDomainService.completeOutpatientRegistration(paymentDraft.getEncounterId());

            if (request.getAppointmentId() != null && outpatientBookingRegistrationExtension.isEnabled()) {
                outpatientBookingRegistrationExtension.linkEncounter(request.getAppointmentId(), paymentDraft.getEncounterId());
                outpatientBookingRegistrationExtension.checkInAfterRegister(request.getAppointmentId(), paymentDraft.getEncounterId());
            }

            // 第四步：票面（挂号收费项合计）> 0 时创建电子发票草稿；真正零元挂号跳过。博思开具由 einvoice-boss 异步处理。
            BigDecimal invoiceFaceAmount = MonetaryAmountSupport.scaleSettle(registrationChargeLine.getTotalPrice());
            CreateDraftInvoiceCommand draftInvoiceCommand =
                outpatientRegistrationMapper.toCreateDraftInvoiceCommand(paymentDraft, paymentId, invoiceFaceAmount);
            Long invoiceId = invoiceAfterPaymentOrchestration.createDraftAndScheduleIssueIfPayable(draftInvoiceCommand);
            Invoice invoice = null;
            if (invoiceId != null) {
                invoice = invoiceQueryService.getInvoiceById(outpatientRegistrationMapper.toInvoiceQueryCommand(invoiceId));
            }

            // 第五步：回传最新支付信息与发票草稿信息，组装缴费结果。
            return outpatientRegistrationMapper.toPayRegisterFeeResponse(
                paymentReconciliationQueryService.mustGetPayment(paymentId), invoice);
        } catch (RuntimeException ex) {
            // 2207 已成功但本地落账失败：同事务内冲正平台结算，与门诊收费确认对齐。
            if (!ybConfirmResults.isEmpty()) {
                try {
                    outpatientChargeInsuranceCompensationOrchestration.compensateSettlementsByPayment(
                        request.getPaymentDraftId());
                } catch (RuntimeException compensationEx) {
                    compensationEx.addSuppressed(ex);
                    throw compensationEx;
                }
            }
            throw ex;
        }
    }

    /**
     * 分页查询门诊挂号记录。
     *
     * @param request 分页请求
     * @return 挂号分页结果
     * @throws BusinessException 查询参数不完整时抛出
     */
    public PageClinicRegistersResponse pageClinicRegisters(PageClinicRegistersRequest request) {
        // Gate：日期区间与分页窗口必填，避免无界扫表。
        if (request.getDateFrom() == null || request.getDateTo() == null) {
            throw new BusinessException("REG-VAL-001", "查询参数不完整");
        }
        pageWindowGate.requireNonNullPageWindow(request, "REG-VAL-001", "查询参数不完整");
        // Pre：查询读模型分页；未收费取消（ENTERED_IN_ERROR）不展示；退号完成（CANCELLED）仍展示。
        ClinicRegisterPageQuery query = outpatientRegistrationMapper.toClinicRegisterPageQuery(request);
        query.setExcludeStatusEnums(List.of(EncounterStatus.ENTERED_IN_ERROR.getCode()));
        Page<ClinicRegisterQueryModel> page = clinicRegistrationLoader.pageClinicRegister(query);
        // 读模型未存挂号费：按就诊批量拉取收费明细，用 BR-CHG-001 锚点（adm_healthcare_service + 号源类型）汇总 total_price 回填列表金额。
        Map<String, BigDecimal> registrationFeeByAnchor = buildRegistrationFeeTotalsForClinicRegisterPage(page.getContent());
        // 待挂号（PLANNED）行批量解析支付草稿主键，便于列表上直接发起结算完成挂号。
        List<Long> plannedEncounterIds = new ArrayList<>();
        for (ClinicRegisterQueryModel row : page.getContent()) {
            if (row.getEncounterId() == null) {
                continue;
            }
            if (EncounterStatus.PLANNED.getCode().equals(row.getStatusEnum())) {
                plannedEncounterIds.add(row.getEncounterId());
            }
        }
        Map<Long, Long> paymentDraftByEncounter =
            paymentReconciliationQueryService.findDraftPaymentIdsByEncounterIds(plannedEncounterIds);
        List<Long> settledEncounterIds = new ArrayList<>();
        for (ClinicRegisterQueryModel row : page.getContent()) {
            if (row.getEncounterId() == null) {
                continue;
            }
            if (!EncounterStatus.PLANNED.getCode().equals(row.getStatusEnum())) {
                settledEncounterIds.add(row.getEncounterId());
            }
        }
        Map<Long, Long> paymentIdByEncounter =
            this.buildRegistrationFeePaymentIdsForClinicRegisterPage(settledEncounterIds);
        List<ClinicRegisterItemDto> records =
            outpatientRegistrationMapper.toClinicRegisterItemDtoList(
                page, registrationFeeByAnchor, paymentDraftByEncounter, paymentIdByEncounter);
        this.enrichClinicRegisterListRecords(records, paymentDraftByEncounter);
        return outpatientRegistrationMapper.toPageClinicRegistersResponse(page, records);
    }

    /**
     * 取消未收费挂号（PLANNED）：作废支付草稿，按需 2205/2202(med_type=12)，就诊置 {@code ENTERED_IN_ERROR}。
     *
     * <p>前端入口文案为「取消」。与退号（{@code CANCELLED}）分流：本方法仅处理未收费挂单。
     * 已缴费挂号须走 {@code cancelWithRefund}（退号），不可调用本方法。</p>
     *
     * @param request 含 encounterId、可选 cancelReason
     * @return 取消后状态与是否曾撤平台挂号
     */
    public CancelPlannedRegistrationResponse cancelPlannedRegistration(CancelPlannedRegistrationRequest request) {
        // Gate：须指定待取消的未收费挂号就诊
        if (request.getEncounterId() == null) {
            throw new BusinessException("CANCEL-VAL-001", "请先选择挂号记录");
        }
        // Pre：须为门诊 AMB 且 PLANNED，已登记退号走 cancelWithRefund
        EncounterDetailQueryModel encounter = encounterQueryService.mustGetEncounterOfClass(
            request.getEncounterId(),
            EncounterClass.AMB.getCode(),
            ErrorCodeConstants.ENCOUNTER_INVALID_STATUS,
            "当前就诊不是门诊类别，无法取消挂号");
        if (!EncounterStatus.PLANNED.getCode().equals(encounter.getStatusEnum())) {
            throw new BusinessException("CANCEL-BIZ-409", "仅待挂号（未收费）状态可取消挂号");
        }
        Long paymentDraftId = paymentReconciliationQueryService
            .findDraftPaymentIdsByEncounterIds(List.of(request.getEncounterId()))
            .get(request.getEncounterId());
        // Gate：已 SUCCESS 支付、或半成功（银联已扣 / 2207 已确认）禁止「未收费取消」
        this.assertPlannedRegistrationVoidable(request.getEncounterId(), paymentDraftId);
        String cancelReason = request.getCancelReason();
        if (cancelReason == null || cancelReason.isBlank()) {
            cancelReason = "窗口取消挂号";
        }
        Account registrationAccount = accountQueryService.getRegistrationAccount(
            encounter.getPatientId(), request.getEncounterId());
        Long contractId = this.resolveContractIdFromAccount(registrationAccount);
        boolean insuranceRevoked = false;
        // Cmd：有活跃预结算批次 → 2205 撤未结算明细
        if (paymentDraftId != null && chargeInsuranceExtension.isEnabled()) {
            List<OutpatientChargePreSettleQueryExtensionOut> active =
                chargeInsuranceExtension.listActivePreSettlesByPaymentDraft(
                new OutpatientChargePreSettleListExtensionIn().setPaymentDraftId(paymentDraftId));
            for (OutpatientChargePreSettleQueryExtensionOut row : active) {
                if (row == null || StringUtils.isBlank(row.getChargeBatchNo())) {
                    continue;
                }
                OutpatientChargeRevokeFeeDetailExtensionIn revokeCommand = new OutpatientChargeRevokeFeeDetailExtensionIn();
                revokeCommand.setChargeBatchNo(row.getChargeBatchNo().trim());
                revokeCommand.setPaymentDraftId(paymentDraftId);
                chargeInsuranceExtension.revokeFeeDetails(revokeCommand);
                insuranceRevoked = true;
            }
        }
        // Cmd：撤挂号费平台 map（11 现行 + 12 历史残留）；无 map 则 Extension 内幂等跳过
        if (outpatientRegistrationInsuranceExtension.isEnabled()
            && contractId != null
            && outpatientRegistrationInsuranceExtension.supportsThirdPartyContract(contractId)) {
            if (this.revokeRegisterFeePlatformMaps(request.getEncounterId(), contractId)) {
                insuranceRevoked = true;
            }
        }
        // Cmd：作废本地支付草稿并取消 PLANNED 就诊
        if (paymentDraftId != null) {
            AbortPreSettlementBeforePaymentCommand abortCommand = new AbortPreSettlementBeforePaymentCommand();
            abortCommand.setPaymentId(paymentDraftId);
            paymentReconciliationDomainService.abortPreSettlementBeforePayment(abortCommand);
        }
        encounterDomainService.cancelPlannedOutpatientRegistration(request.getEncounterId(), cancelReason);
        if (outpatientBookingRegistrationExtension.isEnabled()) {
            // Cmd：撤销待挂号同样回补现场占号余量，避免预结算占号后废弃泄漏。
            Long operatorId = currentPractitionerResolver.requireOperatorId("CANCEL-VAL-001", "当前登录用户不能为空");
            OutpatientRegistrationCancelExtensionIn bookingCancel = new OutpatientRegistrationCancelExtensionIn();
            bookingCancel.setCancelReason(cancelReason);
            bookingCancel.setOperatorId(operatorId);
            outpatientBookingRegistrationExtension.onRegistrationCancelled(request.getEncounterId(), bookingCancel);
        }
        // Asm：返回 ENTERED_IN_ERROR 与是否触达平台撤销
        return new CancelPlannedRegistrationResponse()
            .setEncounterStatus(EncounterStatus.ENTERED_IN_ERROR.getCode())
            .setInsurancePlatformRevoked(insuranceRevoked);
    }

    /**
     * 「取消挂号」前置：本就诊不得已有成功支付，且不得处于半成功（银联已扣 / 医保 2207 已确认）。
     *
     * <p>半成功时支付单仍为 DRAFT，仅查 SUCCESS 会漏拦；须同时查 BPC SUCCESS 与 CONFIRMED 回执。</p>
     *
     * @param encounterId    待取消的未收费就诊
     * @param paymentDraftId 支付草稿主键；可空（无草稿则只校验成功支付）
     */
    private void assertPlannedRegistrationVoidable(Long encounterId, Long paymentDraftId) {
        List<PaymentReconciliationQueryModel> successPayments =
            paymentReconciliationQueryService.findPaySuccessOrRefundingByEncounter(encounterId);
        // Gate：已产生正式收费（成功/退费中支付）→ 应走退号而非取消挂号
        if (successPayments != null && !successPayments.isEmpty()) {
            throw new BusinessException("CANCEL-BIZ-409", "已产生正式费用，不能取消挂号，请使用退号");
        }
        // Gate：无草稿则无半成功锚点
        if (paymentDraftId == null) {
            return;
        }
        // Gate：银联已扣款（BPC SUCCESS）但院内未落账 → 须补结算或退货，禁止当未收费取消
        // 口径与门诊收费 BR-OPC-025 / PaymentDraftHalfSuccessAbortGate 一致（同码 CANCEL-BIZ-413）
        if (thirdPartyPaymentExtension.isEnabled()
            && thirdPartyPaymentExtension.hasSuccessfulConsumerScanOrder(paymentDraftId)) {
            throw new BusinessException(
                OutpatientRegistrationConstants.CANCEL_REASON_CODE_HALF_SUCCESS,
                OutpatientRegistrationConstants.CANCEL_MESSAGE_HALF_SUCCESS);
        }
        // Gate：扫码前已 2207 CONFIRMED、预结算已 SETTLED → 同样禁止取消草稿
        if (this.hasConfirmedRegisterFeeInsuranceSettlement(paymentDraftId)) {
            throw new BusinessException(
                OutpatientRegistrationConstants.CANCEL_REASON_CODE_HALF_SUCCESS,
                OutpatientRegistrationConstants.CANCEL_MESSAGE_HALF_SUCCESS);
        }
    }

    /**
     * 已结挂号费退号前：先 2208 撤结算，再 2202(med_type=12) 撤平台挂号（§3.6 必做）。
     *
     * @param encounterId 就诊 id
     * @param payments    成功/退费中支付单（可空）
     */
    private void runRegisterFeeInsuranceRevokeBeforeCancel(
        Long encounterId,
        List<PaymentReconciliationQueryModel> payments) {
        if (!chargeInsuranceExtension.isEnabled()) {
            return;
        }
        if (payments != null) {
            for (PaymentReconciliationQueryModel payment : payments) {
                if (payment == null || payment.getId() == null) {
                    continue;
                }
                List<OutpatientChargeSettlementRecordExtensionOut> records =
                    chargeInsuranceExtension.listSettlementsByPayment(payment.getId());
                if (records == null || records.isEmpty()) {
                    continue;
                }
                for (OutpatientChargeSettlementRecordExtensionOut record : records) {
                    if (record == null) {
                        continue;
                    }
                    // Skip：本地已 REVOKED 不再打 2208（重试退号时 list 仍含已撤行）
                    if (PaymentYbSettlementStatus.REVOKED.equals(record.getStatus())) {
                        continue;
                    }
                    OutpatientChargeRevokeSettleExtensionIn revokeSettleCommand =
                        new OutpatientChargeRevokeSettleExtensionIn();
                    revokeSettleCommand.setSettlementRecordId(record.getSettlementRecordId());
                    revokeSettleCommand.setPaymentId(payment.getId());
                    revokeSettleCommand.setPreSettleId(record.getPreSettleId());
                    revokeSettleCommand.setSettlementId(record.getSettlementId());
                    try {
                        // Cmd：2208 撤销原挂号费正式结算
                        chargeInsuranceExtension.revokeSettle(revokeSettleCommand);
                    } catch (RuntimeException ex) {
                        String detail = ex.getMessage();
                        if (detail == null || detail.isBlank()) {
                            detail = "平台返回失败";
                        }
                        throw new BusinessException(
                            "INS-013",
                            "医保结算撤销失败（2208）：" + detail + "；请勿重复退号，联系医保办处理");
                    }
                }
            }
        }
        EncounterDetailQueryModel encounter = encounterQueryService.mustGetEncounterOfClass(
            encounterId,
            EncounterClass.AMB.getCode(),
            ErrorCodeConstants.ENCOUNTER_INVALID_STATUS,
            "当前就诊不是门诊类别，无法退号");
        Account registrationAccount = accountQueryService.getRegistrationAccount(
            encounter.getPatientId(), encounterId);
        Long contractId = this.resolveContractIdFromAccount(registrationAccount);
        // Gate：非医保合同或未装配登记 Extension → 跳过 2202
        if (!outpatientRegistrationInsuranceExtension.isEnabled()
            || contractId == null
            || !outpatientRegistrationInsuranceExtension.supportsThirdPartyContract(contractId)) {
            return;
        }
        try {
            // Cmd：2208 之后必 2202；11 现行与 12 历史残留都撤；无 map 则跳过
            this.revokeRegisterFeePlatformMaps(encounterId, contractId);
        } catch (RuntimeException ex) {
            throw new BusinessException(
                "INS-013",
                "医保挂号撤销失败（2202），本地退号已中止，请联系医保办处理");
        }
    }

    /**
     * 撤销本就诊挂号费相关平台 map：普通门诊 11（现行）与门诊挂号 12（改码前残留）。
     *
     * @param encounterId 就诊 id
     * @param contractId  费用性质 id
     * @return 任一 map 实际触达平台撤销时 true
     */
    private boolean revokeRegisterFeePlatformMaps(Long encounterId, Long contractId) {
        boolean revoked = false;
        for (String medType : List.of(
            YbMedType.GENERAL_OUTPATIENT.getCode(),
            YbMedType.OUTPATIENT_REGISTRATION.getCode())) {
            OutpatientRegistrationPlatformExtensionIn revokeCommand =
                new OutpatientRegistrationPlatformExtensionIn();
            revokeCommand.setEncounterId(encounterId);
            revokeCommand.setContractId(contractId);
            revokeCommand.setMedType(medType);
            OutpatientRegistrationPlatformExtensionOut revokeOut =
                outpatientRegistrationInsuranceExtension.revokePlatformRegistration(revokeCommand);
            if (revokeOut != null && StringUtils.isNotBlank(revokeOut.getEncounterPlatformId())) {
                revoked = true;
            }
        }
        return revoked;
    }

    /**
     * 刷新挂号费医保预结算：撤销原 2206 后，用最新就诊凭证重算（电子凭证过期场景）。
     *
     * @param request 含 paymentDraftId 与新读码字段；psnNo/险种等可缺省并从原快照复用
     * @return 刷新后的医保分摊与个人现金应付
     * @throws BusinessException 草稿非法、非 PLANNED、缺凭证或 Extension 未启用时
     */
    public RefreshRegisterFeeInsurancePreSettleResponse refreshRegisterFeeInsurancePreSettle(
        RefreshRegisterFeeInsurancePreSettleRequest request) {
        // Gate：支付草稿与新就诊凭证必填，且须已装配医保 Extension
        if (request.getPaymentDraftId() == null) {
            throw new BusinessException("PAY-VAL-001", "结算参数不完整");
        }
        if (StringUtils.isBlank(request.getYbCardInfo())) {
            throw new BusinessException("REG-VAL-002", "刷新预结算须重新读卡或展码获取最新凭证");
        }
        if (!chargeInsuranceExtension.isEnabled()) {
            throw new BusinessException("REG-VAL-003", "未装配医保结算模块，无法刷新预结算");
        }
        PaymentReconciliationQueryModel paymentDraft =
            paymentReconciliationQueryService.mustGetPayment(request.getPaymentDraftId());
        ChargeItem registrationChargeLine = this.loadRegistrationChargeItemForPayRegisterFee(paymentDraft);
        EncounterDetailQueryModel encounter = encounterQueryService.mustGetEncounterOfClass(
            paymentDraft.getEncounterId(),
            EncounterClass.AMB.getCode(),
            ErrorCodeConstants.ENCOUNTER_INVALID_STATUS,
            "当前就诊不是门诊类别，无法刷新预结算");
        if (!EncounterStatus.PLANNED.getCode().equals(encounter.getStatusEnum())) {
            throw new BusinessException("PAY-BIZ-409", "仅待挂号可刷新医保预结算");
        }
        Account registrationAccount = accountQueryService.getRegistrationAccount(
            encounter.getPatientId(), encounter.getId());
        Long contractId = this.resolveContractIdFromAccount(registrationAccount);
        if (contractId == null || !chargeInsuranceExtension.supportsThirdPartyContract(contractId)) {
            throw new BusinessException("REG-VAL-003", "非医保挂号费，无需刷新预结算");
        }
        // Pre：撤销 ACTIVE 2206 快照，并从上一批快照复用 psnNo/险种等（仅凭证必须更新）
        List<OutpatientChargePreSettleQueryExtensionOut> active =
            chargeInsuranceExtension.listActivePreSettlesByPaymentDraft(
            new OutpatientChargePreSettleListExtensionIn().setPaymentDraftId(request.getPaymentDraftId()));
        OutpatientChargePreSettleQueryExtensionOut previous = null;
        if (!active.isEmpty()) {
            previous = active.get(0);
        }
        for (OutpatientChargePreSettleQueryExtensionOut row : active) {
            if (row == null || StringUtils.isBlank(row.getChargeBatchNo())) {
                continue;
            }
            OutpatientChargeRevokeFeeDetailExtensionIn revokeCommand = new OutpatientChargeRevokeFeeDetailExtensionIn();
            revokeCommand.setChargeBatchNo(row.getChargeBatchNo().trim());
            revokeCommand.setPaymentDraftId(request.getPaymentDraftId());
            chargeInsuranceExtension.revokeFeeDetails(revokeCommand);
        }
        String psnNo = null;
        if (StringUtils.isNotBlank(request.getPsnNo())) {
            psnNo = request.getPsnNo().trim();
        } else if (previous != null) {
            psnNo = previous.getInsuredPersonNo();
        }
        if (StringUtils.isBlank(psnNo)) {
            throw new BusinessException("REG-VAL-002", "刷新预结算缺少人员编号，请重新读卡");
        }
        String insuplcAdmdvs = null;
        if (StringUtils.isNotBlank(request.getInsuplcAdmdvs())) {
            insuplcAdmdvs = request.getInsuplcAdmdvs().trim();
        } else if (previous != null) {
            insuplcAdmdvs = previous.getInsuplcAdmdvs();
        }
        String insutype = null;
        if (StringUtils.isNotBlank(request.getInsutype())) {
            insutype = request.getInsutype().trim();
        } else if (previous != null) {
            insutype = previous.getInsutype();
        }
        String payerCertType = null;
        if (StringUtils.isNotBlank(request.getYbMediumType())) {
            payerCertType = request.getYbMediumType().trim();
        } else if (previous != null) {
            payerCertType = previous.getPayerCertType();
        }
        Boolean ybUseAcctPay = request.getYbUseAcctPay();
        if (ybUseAcctPay == null && previous != null && StringUtils.isNotBlank(previous.getAcctUsedFlag())) {
            ybUseAcctPay = !"0".equals(previous.getAcctUsedFlag().trim());
        }
        // Pre：刷新挂号费预结算默认 med_type=11；请求显式传入时仍允许覆盖（运维/联调）
        String medType = YbMedType.GENERAL_OUTPATIENT.getCode();
        if (StringUtils.isNotBlank(request.getMedType())) {
            medType = request.getMedType().trim();
        }
        // Cmd：Extension 2203→2204→2206 重算并刷新支付草稿个人现金行
        OutpatientChargePreSettleExtensionIn command = new OutpatientChargePreSettleExtensionIn();
        command.setEncounterId(encounter.getId());
        command.setChargeItemIds(List.of(registrationChargeLine.getId()));
        command.setContractId(contractId);
        command.setPaymentDraftId(request.getPaymentDraftId());
        command.setPayerCertType(payerCertType);
        command.setPayerCertNo(request.getYbCardInfo());
        command.setInsuredPersonNo(psnNo);
        command.setInsuplcAdmdvs(insuplcAdmdvs);
        command.setInsutype(insutype);
        command.setMedType(medType);
        command.setAcctUsedFlag(this.resolveRegisterFeeAcctUsedFlag(ybUseAcctPay));
        OutpatientChargePreSettleExtensionOut ybResult = chargeInsuranceExtension.preSettle(command);
        BigDecimal psnCashPay = BigDecimal.ZERO;
        if (ybResult.getPsnCashPay() != null) {
            psnCashPay = ybResult.getPsnCashPay();
        }
        List<PaymentDetailItemCommand> draftLines = new ArrayList<>();
        PaymentDetailItemCommand draftLine = new PaymentDetailItemCommand();
        draftLine.setAccountId(registrationAccount.getId());
        draftLine.setAccountCode(registrationAccount.getBusNo());
        draftLine.setPayEnum(PaymentChannel.CASHIER.getCode());
        draftLine.setAmount(psnCashPay);
        draftLine.setChargeAmount(psnCashPay);
        draftLine.setResultEnum(PaymentStatus.DRAFT.getCode());
        draftLines.add(draftLine);
        paymentReconciliationDomainService.refreshPaymentDraftPreSettleAmounts(
            request.getPaymentDraftId(), psnCashPay, draftLines);
        // Asm：返回刷新后分摊供前端更新确认弹窗（起付线/公务员仅展示透传）
        return new RefreshRegisterFeeInsurancePreSettleResponse()
            .setPaymentDraftId(request.getPaymentDraftId())
            .setInsurancePreSettled(Boolean.TRUE)
            .setYbChrgBchno(ybResult.getChargeBatchNo())
            .setYbMedfeeSumamt(ybResult.getMedfeeSumamt())
            .setYbFundPaySumamt(ybResult.getFundPaySumamt())
            .setYbAcctPay(ybResult.getAcctPay())
            .setYbPsnCashPay(ybResult.getPsnCashPay())
            .setYbActPayDedc(ybResult.getActPayDedc())
            .setYbCvlservFlag(ybResult.getCvlservFlag())
            .setYbCvlservPay(ybResult.getCvlservPay())
            .setTotalAmount(psnCashPay);
    }

    /**
     * 当日挂号统计。
     *
     * <p>不含待收费 {@code PLANNED}、不含未收费取消 {@code ENTERED_IN_ERROR}；
     * 「已退」仅计退号完成 {@code CANCELLED}；「有效」= 当日挂号 − 已退。</p>
     *
     * @param request 统计日期，默认当天
     * @return 总数、已退数、有效数
     */
    public GetTodayRegisterSummaryResponse getTodayRegisterSummary(GetTodayRegisterSummaryRequest request) {
        // Pre：统计日缺省为当天，支持窗口切换历史日汇总。
        LocalDate date;
        if (request != null && request.getDate() != null) {
            date = request.getDate();
        } else {
            date = LocalDate.now();
        }
        LocalDateTime dayStart = date.atStartOfDay();
        LocalDateTime dayEnd = date.atTime(23, 59, 59);

        // 当日挂号：排除待收费与未收费取消，避免冲高「有效」
        ClinicRegisterPageQuery totalQuery = new ClinicRegisterPageQuery();
        totalQuery.setStartTime(dayStart);
        totalQuery.setEndTime(dayEnd);
        totalQuery.setExcludeStatusEnums(List.of(
            EncounterStatus.PLANNED.getCode(),
            EncounterStatus.ENTERED_IN_ERROR.getCode()));

        // 已退：仅退号完成（CANCELLED），与未收费取消分流
        ClinicRegisterPageQuery cancelledQuery = new ClinicRegisterPageQuery();
        cancelledQuery.setStartTime(dayStart);
        cancelledQuery.setEndTime(dayEnd);
        cancelledQuery.setStatusEnum(EncounterStatus.CANCELLED.getCode());

        long totalCount = clinicRegistrationLoader.countClinicRegister(totalQuery);
        long cancelledCount = clinicRegistrationLoader.countClinicRegister(cancelledQuery);

        return new GetTodayRegisterSummaryResponse()
            .setDate(date)
            .setTotalCount(totalCount)
            .setCancelledCount(cancelledCount)
            .setActiveCount(Math.max(0L, totalCount - cancelledCount));
    }

    /**
     * 校验当前挂号记录是否允许退号。
     *
     * <p>先执行 BR-REG-015 收费全收口门禁（非挂号锚点无待收/已收/退费中），再校验就诊可退状态（BR-REG-008）。
     * 已接诊/暂离/完诊须先经医生取消接诊（与账单无关）；退药/退耗由门诊退费 IF-REF-04 在资金退费前收口，本接口不再查发放态。</p>
     *
     * @param request 退号校验请求
     * @return 退号可行性结果
     * @throws BusinessException 未传就诊 ID 时抛出
     */
    public ValidateCancelableResponse validateCancelable(ValidateCancelableRequest request) {
        // Gate：退号校验必须明确就诊记录。
        if (request.getEncounterId() == null) {
            throw new BusinessException("CANCEL-VAL-001", "请先选择挂号记录");
        }
        // Pre：加载退号判定所需的就诊主档。
        EncounterDetailQueryModel encounter = encounterQueryService.mustGetEncounterOfClass(
            request.getEncounterId(),
            EncounterClass.AMB.getCode(),
            ErrorCodeConstants.ENCOUNTER_INVALID_STATUS,
            "当前就诊不是门诊类别，无法退号");
        // Gate：非挂号锚点账单须无待收/已收/退费中（BR-REG-015；药耗已由 IF-REF-04 收口）。
        OutpatientRegistrationCancelBlockResult clinicalBillingBlock =
            outpatientRegistrationCancelGate.evaluateClinicalBillingBlock(request.getEncounterId());
        if (clinicalBillingBlock.isBlocked()) {
            return outpatientRegistrationMapper.toValidateCancelableResponse(
                false,
                clinicalBillingBlock.getReasonCode(),
                clinicalBillingBlock.getReasonText());
        }
        // 按就诊状态判断是否允许退号（与 Encounter.assertCancelable 门诊口径一致：仅未接诊的已挂号/已分诊）。
        EncounterStatus encounterStatus = EnumUtil.fromCode(EncounterStatus.class, encounter.getStatusEnum());
        boolean cancelable = encounterStatus != null
            && (EncounterStatus.REGISTERED.equals(encounterStatus)
                || EncounterStatus.TRIAGED.equals(encounterStatus));
        String reasonCode;
        String reasonText;
        if (cancelable) {
            reasonCode = OutpatientRegistrationConstants.CANCEL_REASON_CODE_OK;
            reasonText = "允许退号";
        } else {
            // 已接诊/暂离/完诊：引导患者回医生站取消接诊（医生同意后方可退号）。
            if (EncounterStatus.IN_PROGRESS.equals(encounterStatus)
                || EncounterStatus.ON_HOLD.equals(encounterStatus)
                || EncounterStatus.DISCHARGED.equals(encounterStatus)) {
                reasonCode = OutpatientRegistrationConstants.CANCEL_REASON_CODE_NEED_CANCEL_RECEPTION;
                reasonText = OutpatientRegistrationConstants.CANCEL_MESSAGE_NEED_CANCEL_RECEPTION;
            } else if (EncounterStatus.PLANNED.equals(encounterStatus)) {
                reasonCode = "CANCEL-BIZ-409";
                reasonText = "待挂号状态不可退号";
            } else {
                reasonCode = "CANCEL-BIZ-409";
                reasonText = "当前状态不可退号";
            }
        }
        return outpatientRegistrationMapper.toValidateCancelableResponse(cancelable, reasonCode, reasonText);
    }

    /**
     * 执行退号并联动退费与冲红。
     *
     * <p>若挂号费已医保正式结算：本地退费前必先 【2208→2202(med_type=12)】。</p>
     * <p>已付挂号费时逆向单须<strong>原路返回</strong>拷贝原支付 {@code contract_no} 与渠道明细
     * （含统筹/个账/自付），对齐 {@code BR-REF-011}；禁止落空明细导致日结支付方式缺失。</p>
     * <p><strong>零元号例外</strong>：正式结算应付为 0 时可不落渠道明细（对齐 IF-REG-07 / BR-REG-017），
     * 退号允许无明细整单冲退，仍创建逆向单并回退收费项。</p>
     *
     * @param request 退号退费请求
     * @return 退号退费结果
     * @throws BusinessException 参数不完整、不满足退号条件、非零元却无原支付渠道明细或医保撤销失败时抛出
     */
    public CancelWithRefundResponse cancelWithRefund(CancelWithRefundRequest request) {
        // Gate：退号退费必须具备最小参数集（含幂等 refundRequestId）。
        if (request.getEncounterId() == null || request.getCancelReason() == null || request.getCancelReason().isBlank() || request.getRefundRequestId() == null || request.getRefundRequestId().isBlank()) {
            throw new BusinessException("REFUND-VAL-001", "退号参数不完整");
        }
        // Pre：先执行可退号校验，不通过则终止退号退费流程；异常文案与校验接口一致（如待挂号不可退号）。
        ValidateCancelableResponse validateResult = validateCancelable(new ValidateCancelableRequest().setEncounterId(request.getEncounterId()));
        if (!Boolean.TRUE.equals(validateResult.getCancelable())) {
            String refundBlockMsg = validateResult.getReasonText();
            if (refundBlockMsg == null || refundBlockMsg.isBlank()) {
                refundBlockMsg = "当前状态不可退号退费";
            }
            throw new BusinessException("REFUND-BIZ-403", refundBlockMsg);
        }
        List<PaymentReconciliationQueryModel> payments = paymentReconciliationQueryService.findPaySuccessOrRefundingByEncounter(request.getEncounterId());
        List<Long> paymentIds = new ArrayList<>();
        for (PaymentReconciliationQueryModel payment : payments) {
            paymentIds.add(payment.getId());
        }
        // Pre/Cmd：已医保结挂号费 → 必须先 2208，再 2202(med_type=12)，禁止只本地退
        this.runRegisterFeeInsuranceRevokeBeforeCancel(request.getEncounterId(), payments);
        // 执行就诊退号动作。
        CancelOutpatientRegistrationCommand cancelCommand = outpatientRegistrationMapper.toCancelOutpatientRegistrationCommand(request);
        encounterDomainService.cancelOutpatientRegistration(cancelCommand);
        if (outpatientBookingRegistrationExtension.isEnabled()) {
            // Cmd：通知 booking 按院点策略处置预约（默认 RELEASE_SLOT_ONLY：回补余量并软删预约行，释放同日防重键）。
            Long operatorId = currentPractitionerResolver.requireOperatorId("REFUND-VAL-001", "当前登录用户不能为空");
            OutpatientRegistrationCancelExtensionIn cancelReason = new OutpatientRegistrationCancelExtensionIn();
            cancelReason.setCancelReason(request.getCancelReason());
            cancelReason.setOperatorId(operatorId);
            outpatientBookingRegistrationExtension.onRegistrationCancelled(request.getEncounterId(), cancelReason);
        }
        // 存在支付记录时，创建逆向单并完成退费与收费项回退；原收费单保持 SUCCESS。
        PaymentReconciliationQueryModel refundPayment = null;
        if (!paymentIds.isEmpty()) {
            Long operatorId = currentPractitionerResolver.requireOperatorId("REFUND-VAL-001", "当前登录用户不能为空");
            PaymentReconciliationQueryModel originalPayment =
                this.requireRegistrationFeePaymentForCancel(request.getEncounterId());
            thirdPartyPaymentRefundOrchestrator.applyRefundBeforeLocalRefund(
                originalPayment.getId(),
                request.getClientRefundResult(),
                operatorId);
            // Pre：整单原路返回——读原支付渠道明细（含统筹/个账/自付），对齐 IF-REF-05 / BR-REF-011
            List<PaymentRecDetailQueryModel> originalPaymentDetails =
                paymentReconciliationQueryService.listPaymentRecDetailsByPaymentId(originalPayment.getId());
            List<PaymentDetailItemCommand> refundPaymentDetails =
                outpatientRegistrationMapper.toRegistrationCancelRefundPaymentDetails(originalPaymentDetails);
            // Gate：非零元无渠道明细则无法原路退；零元号正式结算可不落明细，允许空明细逆向单整单冲退
            if (refundPaymentDetails.isEmpty() && !this.isZeroYuanRegistrationPayment(originalPayment)) {
                throw new BusinessException(
                    ErrorCodeConstants.PAYMENT_RECONCILIATION_INVALID_ARGUMENT,
                    "原挂号支付无渠道明细，无法退号退费");
            }
            RequestOutpatientRefundCommand requestRefundCommand =
                outpatientRegistrationMapper.toRequestOutpatientRefundCommand(
                    request, originalPayment, refundPaymentDetails);
            Long originalPaymentId = originalPayment.getId();
            Long reversePaymentId = null;
            try {
                reversePaymentId = paymentReconciliationDomainService.requestOutpatientRefund(requestRefundCommand);
                // Cmd：退号整单全退专用收口（非门诊账单半退编排）；仅逆向单 REFUNDED 并发布一次退费完成事件。
                paymentReconciliationDomainService.completeRegistrationCancelRefund(
                    outpatientRegistrationMapper.toCompleteRegistrationCancelRefundCommand(originalPaymentId, reversePaymentId));
                BatchChargeItemIdsCommand refundItemsCommand = outpatientRegistrationMapper.toBatchChargeItemIdsCommandFromEncounter(request.getEncounterId(), chargeItemQueryService);
                // Cmd：退号退费覆盖收费人/收费时间（enterer_*）
                refundItemsCommand.setOperatorId(operatorId);
                refundItemsCommand.setBillDate(LocalDateTime.now());
                chargeItemDomainService.returnRegistrationRefundChargeItems(refundItemsCommand);
            } catch (BusinessException businessException) {
                paymentReconciliationDomainService.abortOutpatientRefundAttempt(reversePaymentId);
                throw businessException;
            } catch (RuntimeException runtimeException) {
                paymentReconciliationDomainService.abortOutpatientRefundAttempt(reversePaymentId);
                throw runtimeException;
            }
            refundPayment = paymentReconciliationQueryService.mustGetPayment(reversePaymentId);
        }
        // 若存在票据：DRAFT 仅本地作废（支付后立即退号常见）；ISSUED 冲红并调度博思。
        Invoice redInvoice = null;
        if (!payments.isEmpty()) {
            InvoiceQueryCommand latestInvoiceQuery = new InvoiceQueryCommand();
            latestInvoiceQuery.setPaymentId(payments.get(0).getId());
            Invoice latestInvoice = invoiceQueryService.getInvoiceByPaymentId(latestInvoiceQuery);
            if (latestInvoice != null && !InvoiceStatus.CANCELLED.sameAs(latestInvoice.getStatusEnum())) {
                CancelInvoiceCommand cancelInvoiceCommand = outpatientRegistrationMapper.toCancelInvoiceCommand(
                    latestInvoice.getId(), request.getCancelReason());
                if (InvoiceStatus.DRAFT.sameAs(latestInvoice.getStatusEnum())) {
                    invoiceDomainService.voidDraftInvoice(cancelInvoiceCommand);
                    bossElectronicInvoiceAfterCommitOrchestration.afterInvoiceCancelled(
                        latestInvoice.getId(), request.getCancelReason());
                } else if (InvoiceStatus.ISSUED.sameAs(latestInvoice.getStatusEnum())) {
                    invoiceDomainService.writeoffInvoiceAndCreateRedRecord(cancelInvoiceCommand);
                    bossElectronicInvoiceAfterCommitOrchestration.afterInvoiceCancelled(
                        latestInvoice.getId(), request.getCancelReason());
                    redInvoice = invoiceQueryService.getInvoiceById(
                        outpatientRegistrationMapper.toInvoiceQueryCommand(latestInvoice.getId()));
                }
            }
        }
        // 返回最新就诊、退费、红票状态供前端展示。
        EncounterDetailQueryModel encounter = encounterQueryService.mustGetEncounterOfClass(
            request.getEncounterId(),
            EncounterClass.AMB.getCode(),
            ErrorCodeConstants.ENCOUNTER_INVALID_STATUS,
            "当前就诊不是门诊类别，无法退号");
        return outpatientRegistrationMapper.toCancelWithRefundResponse(encounter, refundPayment, redInvoice);
    }

    /**
     * 判断原挂号支付是否为零元单（正式结算允许不落渠道明细的场景，供退号空明细放行）。
     *
     * <p>以结算刻度下的 {@code displayAmount}/{@code tenderedAmount} 为准；任一大于 0 则非零元。</p>
     *
     * @param originalPayment 原挂号支付读模型
     * @return 两金额均为 null 或不大于 0 时 {@code true}
     */
    private boolean isZeroYuanRegistrationPayment(PaymentReconciliationQueryModel originalPayment) {
        if (originalPayment == null) {
            return false;
        }
        BigDecimal displayAmount = originalPayment.getDisplayAmount();
        if (displayAmount == null) {
            displayAmount = BigDecimal.ZERO;
        }
        BigDecimal tenderedAmount = originalPayment.getTenderedAmount();
        if (tenderedAmount == null) {
            tenderedAmount = BigDecimal.ZERO;
        }
        BigDecimal displayScaled = MonetaryAmountSupport.scaleSettle(displayAmount);
        BigDecimal tenderedScaled = MonetaryAmountSupport.scaleSettle(tenderedAmount);
        return displayScaled.compareTo(BigDecimal.ZERO) <= 0
            && tenderedScaled.compareTo(BigDecimal.ZERO) <= 0;
    }

    /**
     * 补打挂号单。
     *
     * @param request 补打请求
     * @return 补打结果
     * @throws BusinessException encounterId 与 slipNo 均缺失时抛出
     */
    public PrintRegisterTicketResponse printRegisterTicket(PrintRegisterTicketRequest request) {
        // Gate：挂号单补打至少需要 encounterId 或 slipNo 之一。
        if ((request.getEncounterId() == null) && (request.getSlipNo() == null || request.getSlipNo().isBlank())) {
            throw new BusinessException("REG-VAL-001", "encounterId 和 slipNo 至少传一个");
        }
        // Pre：优先按 encounterId 查询挂号单。
        RegistrationSlipQueryModel slipReadModel = null;
        if (request.getEncounterId() != null) {
            slipReadModel = clinicRegistrationLoader.getRegistrationSlipByEncounterId(request.getEncounterId()).orElse(null);
        }
        // 若 encounterId 未命中，再按 slipNo 查询最近一条挂号单。
        if (slipReadModel == null && request.getSlipNo() != null && !request.getSlipNo().isBlank()) {
            RegistrationSlipPageQuery query = new RegistrationSlipPageQuery();
            query.setSlipNo(request.getSlipNo());
            query.setPageNo(1);
            query.setPageSize(1);
            Page<RegistrationSlipQueryModel> page = clinicRegistrationLoader.pageRegistrationSlip(query);
            if (!page.isEmpty()) {
                slipReadModel = page.getContent().get(0);
            }
        }
        if (slipReadModel == null) {
            throw new BusinessException("REG-BIZ-404", "未找到挂号单");
        }
        RegistrationSlipDto slip = outpatientRegistrationMapper.toRegistrationSlipDto(slipReadModel);
        registrationReceiptPrintAssembler.enrichPrintFields(slip, slipReadModel, request.getPaymentId());
        int printCount = 0;
        if (slip.getPrintCount() != null) {
            printCount = slip.getPrintCount();
        }
        return new PrintRegisterTicketResponse().setSlip(slip).setPrintCount(printCount + 1);
    }

    /**
     * 从挂号支付草稿解析并加载唯一挂号收费明细，供正式结算复用（禁止再次 {@code createRegistrationChargeItem}）。
     *
     * <p>自费时草稿 {@code displayAmount} 须等于 {@code ChargeItem.totalPrice}；
     * 有 ACTIVE 2206 时 {@code displayAmount} 为个人现金应付，允许与原价不一致。</p>
     *
     * @param paymentDraft 预结算产生的支付草稿读模型
     * @return 已存在且处于可结算门禁的收费明细实体
     * @throws BusinessException 草稿状态非法、关联串非法、明细缺失或与草稿就诊/患者/金额不一致时抛出
     */
    private ChargeItem loadRegistrationChargeItemForPayRegisterFee(PaymentReconciliationQueryModel paymentDraft) {
        // 仅允许对仍处于草稿态的支付单发起挂号正式结算，阻断重复收款。
        if (paymentDraft.getStatusEnum() == null) {
            throw new BusinessException("PAY-BIZ-409", "当前支付草稿状态不可结算，请重新预结算");
        }
        if (!PaymentStatus.DRAFT.equals(paymentDraft.getStatusEnum())) {
            throw new BusinessException("PAY-BIZ-409", "当前支付草稿状态不可结算，请重新预结算");
        }
        String rawChargeItemIds = paymentDraft.getChargeItemIds();
        if (rawChargeItemIds == null || rawChargeItemIds.isBlank()) {
            throw new BusinessException("PAY-BIZ-404", "支付草稿缺少关联收费明细");
        }
        List<Long> parsedChargeItemIds = new ArrayList<>();
        String[] idSegments = rawChargeItemIds.split(",");
        for (String idSegment : idSegments) {
            if (idSegment == null || idSegment.isBlank()) {
                continue;
            }
            String trimmedId = idSegment.trim();
            try {
                parsedChargeItemIds.add(Long.parseLong(trimmedId));
            } catch (NumberFormatException ex) {
                log.warn("支付草稿 charge_item_ids 段非法: {}", trimmedId, ex);
                throw new BusinessException("PAY-BIZ-404", "支付草稿关联收费明细标识不合法");
            }
        }
        if (parsedChargeItemIds.isEmpty()) {
            throw new BusinessException("PAY-BIZ-404", "支付草稿缺少关联收费明细");
        }
        if (parsedChargeItemIds.size() != 1) {
            throw new BusinessException("PAY-BIZ-409", "挂号草稿仅支持单条收费明细结算");
        }
        Long chargeItemPrimaryKey = parsedChargeItemIds.get(0);
        ChargeItemQueryCommand loadByIdCmd = new ChargeItemQueryCommand();
        loadByIdCmd.setIds(List.of(chargeItemPrimaryKey));
        List<ChargeItem> loadedChargeLines = chargeItemQueryService.loadChargeItemsByIds(loadByIdCmd);
        if (loadedChargeLines == null || loadedChargeLines.isEmpty()) {
            throw new BusinessException("CHARGE_ITEM_002", "关联收费明细不存在或已失效");
        }
        ChargeItem registrationLine = loadedChargeLines.get(0);
        // 就诊与患者必须与草稿一致，防止串单结算。
        if (paymentDraft.getEncounterId() == null || registrationLine.getEncounterId() == null) {
            throw new BusinessException("PAY-BIZ-409", "收费明细与支付草稿就诊不一致，请重新预结算");
        }
        if (!paymentDraft.getEncounterId().equals(registrationLine.getEncounterId())) {
            throw new BusinessException("PAY-BIZ-409", "收费明细与支付草稿就诊不一致，请重新预结算");
        }
        if (paymentDraft.getPatientId() == null || registrationLine.getPatientId() == null) {
            throw new BusinessException("PAY-BIZ-409", "收费明细与支付草稿患者不一致，请重新预结算");
        }
        if (!paymentDraft.getPatientId().equals(registrationLine.getPatientId())) {
            throw new BusinessException("PAY-BIZ-409", "收费明细与支付草稿患者不一致，请重新预结算");
        }
        if (registrationLine.getStatusEnum() == null) {
            throw new BusinessException("CHARGE_ITEM_003", "收费明细状态不可结算");
        }
        if (!ChargeItemStatus.PLANNED.getCode().equals(registrationLine.getStatusEnum())) {
            throw new BusinessException("CHARGE_ITEM_003", "收费明细状态不可结算");
        }
        BigDecimal draftDisplayAmount = paymentDraft.getDisplayAmount();
        if (draftDisplayAmount == null) {
            throw new BusinessException("PAY-BIZ-404", "支付草稿展示金额缺失");
        }
        BigDecimal linePersistedAmount = registrationLine.getTotalPrice();
        if (linePersistedAmount == null) {
            linePersistedAmount = BigDecimal.ZERO;
        }
        // 自费：草稿 displayAmount 须与 ChargeItem.totalPrice 一致。
        // 医保现金段：ACTIVE 2206 或已 CONFIRMED 2207（扫码前结算后预结算已 SETTLED）时允许 displayAmount≠原价。
        if (!this.isRegisterFeeInsuranceCashSegmentPayable(paymentDraft.getId())) {
            // 无医保现金段口径：草稿金额须等于挂号费原价，防止自费草稿被改脏后落账
            if (draftDisplayAmount.compareTo(linePersistedAmount) != 0) {
                throw new BusinessException("PAY-BIZ-409", "支付草稿金额与收费明细不一致，请重新预结算");
            }
        }
        return registrationLine;
    }

    /**
     * 点选号源占号：按请求 {@code slotId} 占用当日 Slot 并绑定就诊；无可用号源时阻断预结算。
     *
     * <p>仅 IF-REG-06 在请求显式携带 {@code slotId} 时调用；纯表单手动挂号不进入本方法。</p>
     *
     * @param request 预挂号请求（须含 slotId）
     * @param patientId 患者主键
     * @param operatorId 操作人
     * @param registrationDate 挂号自然日
     * @param encounterId 已开立就诊主键（释号锚点）
     * @return 占用的 slot 主键
     * @throws BusinessException BKG-REG-404 号源不可用或主数据缺失
     */
    private Long allocateWalkInSlotOrThrow(
        PreRegisterSettlementRequest request,
        Long patientId,
        Long operatorId,
        LocalDate registrationDate,
        Long encounterId) {
        // Gate：点选占号须科室/号源类型/就诊锚点与 slotId 齐全
        if (request.getOrganizationId() == null
            || request.getServiceTypeId() == null
            || encounterId == null
            || request.getSlotId() == null) {
            throw new BusinessException("BKG-REG-404", "当日无可用号源，请重新选择号源或联系排班员");
        }
        OutpatientWalkInSlotAllocateExtensionIn command = new OutpatientWalkInSlotAllocateExtensionIn();
        command.setOrganizationId(request.getOrganizationId());
        command.setPractitionerId(request.getPractitionerId());
        command.setHealthcareServiceId(request.getServiceTypeId());
        command.setRegistrationDate(registrationDate);
        command.setPatientId(patientId);
        command.setOperatorId(operatorId);
        command.setSlotId(request.getSlotId());
        command.setEncounterId(encounterId);
        Long slotId = outpatientBookingRegistrationExtension.allocateWalkInSlot(command);
        if (slotId == null) {
            throw new BusinessException("BKG-REG-404", "当日无可用号源，请重新选择号源或联系排班员");
        }
        return slotId;
    }

    /**
     * IF-REG-13 预约转挂号 Gate：校验预约事实与三要素，并复核医师岗位。
     */
    private void runBookingRegistrationGate(PreRegisterSettlementRequest request) {
        OutpatientBookingRegistrationValidateExtensionIn validateCommand = new OutpatientBookingRegistrationValidateExtensionIn();
        validateCommand.setAppointmentId(request.getAppointmentId());
        validateCommand.setOrganizationId(request.getOrganizationId());
        validateCommand.setPractitionerId(request.getPractitionerId());
        validateCommand.setHealthcareServiceId(request.getServiceTypeId());
        validateCommand.setPatientId(request.getPatientId());
        outpatientBookingQueryExtension.validateForRegistration(validateCommand);

        if (request.getPractitionerId() != null && request.getOrganizationId() != null) {
            List<Practitioner> activePractitioners = practitionerRoleScopeLoader.listActivePractitionersForOutpatientRegistration(
                WhalePlatformRoleCodeConstants.ROLE_DOCTOR, request.getOrganizationId());
            boolean practitionerMatched = false;
            for (Practitioner practitioner : activePractitioners) {
                if (practitioner != null && Objects.equals(practitioner.getId(), request.getPractitionerId())) {
                    practitionerMatched = true;
                    break;
                }
            }
            if (!practitionerMatched) {
                throw new BusinessException("BKG-REG-403", "医师在该科室无有效岗位");
            }
        }
    }

    /**
     * 是否需并行开立 {@code MEDICAL_INSURANCE}（省/市医保或工伤费用性质）。
     *
     * @param contract 挂号所选合同
     * @return 需开医保户时 true
     */
    private boolean needsParallelMedicalInsuranceAccount(ContractDetailQueryModel contract) {
        if (contract == null) {
            return false;
        }
        // 工伤：诊间账单可挂医保户；挂号费仍强制自费（ybRegisterFee 门禁另判）
        if (contract.getId() != null && this.isWorkInjuryContract(contract.getId())) {
            return true;
        }
        return this.isInsuranceContract(contract);
    }

    /**
     * 正式缴费补开户：按支付草稿合同判断是否双账户。
     *
     * @param paymentDraft 支付草稿
     */
    private void ensureAccountsForPayRegisterFee(PaymentReconciliationQueryModel paymentDraft) {
        if (paymentDraft == null || paymentDraft.getPatientId() == null || paymentDraft.getEncounterId() == null) {
            throw new BusinessException("PAY-BIZ-404", "支付草稿缺少患者或就诊，无法开立账户");
        }
        String draftContractNo = null;
        if (StringUtils.isNotBlank(paymentDraft.getContractNo())) {
            draftContractNo = paymentDraft.getContractNo().trim();
        }
        boolean openMedicalInsurance = false;
        if (draftContractNo != null) {
            ContractDetailQueryModel draftContract =
                contractQueryService.findOptionalContractEntityByBusNo(draftContractNo)
                    .map(this::toContractDetailForAccountOpen)
                    .orElse(null);
            if (draftContract != null) {
                // Pre：按合同分类/ybFlag；工伤合同另经 Extension 判定
                openMedicalInsurance = encounterChargeAccountOpener.needsParallelMedicalInsuranceAccount(
                    draftContract.getYbFlag(), draftContract.getCategoryEnum());
                if (!openMedicalInsurance && draftContract.getId() != null) {
                    openMedicalInsurance = this.isWorkInjuryContract(draftContract.getId());
                }
            }
        }
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.PRACTITIONER_INVALID_ARGUMENT,
            "当前会话操作人不能为空");
        encounterChargeAccountOpener.ensureEncounterChargeAccounts(
            paymentDraft.getPatientId(),
            paymentDraft.getEncounterId(),
            draftContractNo,
            openMedicalInsurance,
            operatorId);
    }

    /**
     * 解析挂号费支付明细挂账账户：优先收费行 accountId，回落个人现金户。
     *
     * @param registrationChargeLine 挂号收费行
     * @param paymentDraft           支付草稿（回落用）
     * @return 非空账户
     */
    private Account resolvePaymentAccountForRegisterFee(
        ChargeItem registrationChargeLine,
        PaymentReconciliationQueryModel paymentDraft
    ) {
        if (registrationChargeLine != null && registrationChargeLine.getAccountId() != null) {
            Account byCharge = accountQueryService.mapAccountsByIds(
                List.of(registrationChargeLine.getAccountId())).get(registrationChargeLine.getAccountId());
            if (byCharge != null && byCharge.getId() != null) {
                return byCharge;
            }
        }
        Account personalCash = accountQueryService.getRegistrationAccount(
            paymentDraft.getPatientId(), paymentDraft.getEncounterId());
        if (personalCash == null || personalCash.getId() == null) {
            throw new BusinessException("PAY-BIZ-404", "未找到可用结算账户");
        }
        return personalCash;
    }

    /**
     * 合同实体转开户判定用详情（仅 id/busNo/category）。
     *
     * @param entity 合同实体
     * @return 详情模型；entity 空时 null
     */
    private ContractDetailQueryModel toContractDetailForAccountOpen(
        com.openhis.health.domain.financial.contract.entity.Contract entity
    ) {
        if (entity == null) {
            return null;
        }
        ContractDetailQueryModel detail = new ContractDetailQueryModel();
        detail.setId(entity.getId());
        detail.setBusNo(entity.getBusNo());
        detail.setCategoryEnum(entity.getCategoryEnum());
        detail.setYbFlag(entity.getYbFlag());
        return detail;
    }

    /**
     * 合同是否为医保挂号费用性质（排除工伤；优先 Extension 支持判定）。
     *
     * @param contract 合同详情
     * @return 医保合同为 true
     */
    private boolean isInsuranceContract(ContractDetailQueryModel contract) {
        if (contract == null) {
            return false;
        }
        if (contract.getId() != null && this.isWorkInjuryContract(contract.getId())) {
            return false;
        }
        if (contract.getId() != null && outpatientRegistrationInsuranceExtension.supportsThirdPartyContract(contract.getId())) {
            return true;
        }
        return InsuranceContractCategorySupport.isInsuranceCategoryEnum(contract.getCategoryEnum());
    }

    /**
     * 合同 id 是否为工伤模块支持的工伤账户。
     *
     * @param contractId 合同主键
     * @return 工伤合同时为 true
     */
    private boolean isWorkInjuryContract(Long contractId) {
        return workInjuryModuleExtension.isEnabled()
            && workInjuryModuleExtension.supportsContract(contractId);
    }

    /** 吉林省级医保区划前缀（与定稿「跨省=前两位≠22」一致）。 */
    private static final String JILIN_INSUPLC_PROVINCE_PREFIX = "22";

    /**
     * 参保地是否跨省（相对吉林）。空或过短无法判定时返回 false。
     *
     * @param insuplcAdmdvs 参保地医保区划
     * @return true=跨省
     */
    private boolean isCrossProvinceInsuplc(String insuplcAdmdvs) {
        if (StringUtils.isBlank(insuplcAdmdvs)) {
            return false;
        }
        String digits = insuplcAdmdvs.replaceAll("\\D", "");
        if (digits.length() < 2) {
            return false;
        }
        return !digits.startsWith(JILIN_INSUPLC_PROVINCE_PREFIX);
    }

    /**
     * 解析挂号费是否走医保：跨省参保强制自费并回写请求，防止前端绕过。
     *
     * @param request 预结算请求
     * @return 实际是否医保挂号费
     */
    private boolean resolveYbRegisterFeeForRequest(PreRegisterSettlementRequest request) {
        boolean ybRegisterFee = Boolean.TRUE.equals(request.getYbRegisterFee());
        if (!ybRegisterFee) {
            return false;
        }
        if (this.isCrossProvinceInsuplc(request.getInsuplcAdmdvs())) {
            log.info(
                "跨省参保强制挂号费自费 psnNo={} insuplc={}",
                request.getPsnNo(),
                request.getInsuplcAdmdvs());
            request.setYbRegisterFee(Boolean.FALSE);
            return false;
        }
        return true;
    }

    /**
     * 合同是否为公费/学生等公付类别（InsuranceContractCategorySupport）。
     *
     * @param contract 合同详情
     * @return 公付类别为 true
     */
    private boolean isPublicChargeContract(ContractDetailQueryModel contract) {
        return contract != null
            && InsuranceContractCategorySupport.isPublicChargeCategoryEnum(contract.getCategoryEnum());
    }

    /**
     * 组装学生公费挂号待遇预览（学籍档案 + 可选 eligibility 校验）。
     *
     * @param request  待遇预览请求
     * @param response 待填充响应
     * @return 填充后的响应
     * @throws BusinessException 未选患者
     */
    private LoadRegisterBenefitPreviewResponse buildStudentBenefitPreview(
        LoadRegisterBenefitPreviewRequest request,
        LoadRegisterBenefitPreviewResponse response) {
        if (request.getPatientId() == null) {
            throw new BusinessException("REG-VAL-001", "请先选择患者");
        }
        response.setBenefitKind("STUDENT");
        response.setStudentPreview(buildStudentProfilePreview(request));
        return response;
    }

    /**
     * 加载患者基本信息并映射为挂号待遇预览 DTO（学籍已迁出 campus 模块，studentRecords 为空）。
     *
     * @param request 待遇预览请求（含 patientId）
     * @return 学生档案预览
     */
    private StudentRegisterBenefitPreviewDto buildStudentProfilePreview(LoadRegisterBenefitPreviewRequest request) {
        PatientDetailQueryModel detail = patientQueryService.loadPatientDetail(request.getPatientId());
        StudentRegisterBenefitPreviewDto dto = new StudentRegisterBenefitPreviewDto();
        if (detail != null && detail.getPatient() != null) {
            dto.setPatientId(detail.getPatient().getId());
            dto.setPatientName(detail.getPatient().getName());
            dto.setIdCard(detail.getPatient().getIdCard());
            dto.setPhone(detail.getPatient().getPhone());
        }
        return dto;
    }

    /**
     * 将合同匹配评估结果附加到待遇预览响应（非空时）。
     *
     * @param response   待遇预览响应
     * @param preview    医保读卡预览
     * @param contractId 所选合同 id
     */
    private void attachContractMatchToResponse(
        LoadRegisterBenefitPreviewResponse response,
        YbRegisterBenefitPreviewDto preview,
        Long contractId) {
        RegisterContractMatchDto match = buildContractMatch(preview, contractId);
        if (match != null) {
            response.setContractMatch(match);
        }
    }

    /**
     * 基于参保地、合同与待遇地评估挂号合同匹配度。
     *
     * @param preview    医保人员预览
     * @param contractId 合同 id
     * @return 匹配 DTO；缺参时 null
     */
    private RegisterContractMatchDto buildContractMatch(
        YbRegisterBenefitPreviewDto preview,
        Long contractId) {
        if (preview == null || contractId == null) {
            return null;
        }
        String insuplc = preview.getInsuplcAdmdvs();
        if (StringUtils.isBlank(insuplc)
            && preview.getInsuinfoList() != null
            && !preview.getInsuinfoList().isEmpty()) {
            insuplc = preview.getInsuinfoList().get(0).getInsuplcAdmdvs();
        }
        String admFallback = contractQueryService.findOptionalContractEntityById(contractId)
            .map(Contract::getAdmVs)
            .orElse(null);
        String treatment = registerContractMatchResolver.resolveTreatmentAreaCode(contractId, admFallback);
        return registerContractMatchResolver.evaluate(
            insuplc,
            contractId,
            treatment,
            contractQueryService.listAllContracts());
    }

    /**
     * 组装医保挂号待遇预览：读卡/人员编号 + 2001/5302A/5206 等扩展查询。
     *
     * @param request  待遇预览请求
     * @param response 待填充响应
     * @return 填充后的响应
     */
    private LoadRegisterBenefitPreviewResponse buildInsuranceBenefitPreview(
        LoadRegisterBenefitPreviewRequest request,
        LoadRegisterBenefitPreviewResponse response) {
        YbRegisterBenefitPreviewDto preview = fetchInsurancePreview(request);
        // 1101 异地切省通道后，后续待遇/备案/累计与合同匹配均按实际通道合同评估
        Long effectiveContractId = resolveEffectiveInsuranceContractId(request.getContractId(), preview);
        attachTreatmentCheckIfNeeded(
            preview,
            effectiveContractId,
            preview.getInsuplcAdmdvs(),
            request.getMedType(),
            request.getRunTreatmentCheck() == null || Boolean.TRUE.equals(request.getRunTreatmentCheck()));
        attachOpspFilingIfNeeded(
            preview,
            effectiveContractId,
            preview.getInsuplcAdmdvs(),
            request.getRunOpspFilingQuery() == null || Boolean.TRUE.equals(request.getRunOpspFilingQuery()));
        attachAreaHintIfNeeded(preview, effectiveContractId, preview.getInsuplcAdmdvs());
        attachCumulativeQueryIfNeeded(
            preview,
            effectiveContractId,
            preview.getInsuplcAdmdvs(),
            request.getRunCumulativeQuery() == null || Boolean.TRUE.equals(request.getRunCumulativeQuery()));
        response.setBenefitKind("INSURANCE");
        response.setYbPreview(preview);
        attachContractMatchToResponse(response, preview, effectiveContractId);
        return response;
    }

    /**
     * 读卡 1101 若已切省通道，优先使用返回的 resolvedContractId，避免后续交易仍走市医保。
     *
     * @param requestedContractId 请求入参合同
     * @param preview             人员预览（可含 resolvedContractId）
     * @return 有效合同 id
     */
    private static Long resolveEffectiveInsuranceContractId(
        Long requestedContractId,
        YbRegisterBenefitPreviewDto preview) {
        if (preview == null || StringUtils.isBlank(preview.getResolvedContractId())) {
            return requestedContractId;
        }
        try {
            return Long.valueOf(preview.getResolvedContractId().trim());
        } catch (NumberFormatException ignored) {
            return requestedContractId;
        }
    }

    /**
     * 获取医保人员预览：优先读卡 queryPerson，否则 psnNo+insutype 最小构造。
     *
     * @param request 待遇预览请求
     * @return 医保预览 DTO
     * @throws BusinessException 未装配医保或缺读卡/人员编号
     */
    private YbRegisterBenefitPreviewDto fetchInsurancePreview(LoadRegisterBenefitPreviewRequest request) {
        if (StringUtils.isNotBlank(request.getYbMediumType()) && StringUtils.isNotBlank(request.getYbCardInfo())) {
            if (!registerBenefitQueryExtension.isEnabled()) {
                throw new BusinessException("REG-VAL-003", "未装配医保模块，无法读卡");
            }
            InsurancePersonQueryExtensionIn personCommand = new InsurancePersonQueryExtensionIn();
            personCommand.setContractId(request.getContractId());
            personCommand.setEncounterCredentialType(request.getYbMediumType().trim());
            personCommand.setEncounterCredentialNo(request.getYbCardInfo().trim());
            personCommand.setCardSerialNo(StringUtils.trimToNull(request.getYbCardSn()));
            personCommand.setCertificateNo(StringUtils.trimToNull(request.getYbCertno()));
            personCommand.setPersonCertificateType("01");
            personCommand.setPersonName(StringUtils.trimToNull(request.getYbPsnName()));
            personCommand.setInsurancePoolAreaCode(request.getInsuplcAdmdvs());
            return toYbRegisterBenefitPreview(
                registerBenefitQueryExtension.queryPerson(personCommand),
                StringUtils.isNotBlank(request.getInsutype()) ? request.getInsutype() : null);
        }
        if (StringUtils.isNotBlank(request.getPsnNo()) && StringUtils.isNotBlank(request.getInsutype())) {
            YbRegisterBenefitPreviewDto preview = new YbRegisterBenefitPreviewDto();
            preview.setPsnNo(request.getPsnNo().trim());
            preview.setInsutype(request.getInsutype().trim());
            preview.setInsuplcAdmdvs(request.getInsuplcAdmdvs());
            return preview;
        }
        throw new BusinessException("REG-VAL-002", "请先读卡或提供人员编号与险种");
    }

    /**
     * 附加就医地/参保地划区提示（Extension 解析待遇地失败时仅 warn）。
     *
     * @param preview       医保预览
     * @param contractId    合同 id
     * @param insuplcAdmdvs 参保地统筹区
     */
    private void attachAreaHintIfNeeded(
        YbRegisterBenefitPreviewDto preview,
        Long contractId,
        String insuplcAdmdvs) {
        if (preview == null || !registerBenefitQueryExtension.isEnabled()) {
            return;
        }
        String admdvs = StringUtils.isNotBlank(insuplcAdmdvs) ? insuplcAdmdvs : preview.getInsuplcAdmdvs();
        if (StringUtils.isBlank(admdvs) || contractId == null) {
            return;
        }
        try {
            String mdtrtareaAdmdvs = registerBenefitQueryExtension.resolveTreatmentAreaCode(contractId);
            preview.setAreaHint(RegisterBenefitAreaHintHelper.build(admdvs, mdtrtareaAdmdvs));
        } catch (RuntimeException ex) {
            log.warn("就医地区划解析失败 contractId={}: {}", contractId, ex.getMessage());
        }
    }

    /**
     * 可选执行 5206 人员累计查询并写入预览（失败仅 warn）。
     *
     * @param preview            医保预览
     * @param contractId         合同 id
     * @param insuplcAdmdvs      参保地
     * @param runCumulativeQuery 是否执行查询
     */
    private void attachCumulativeQueryIfNeeded(
        YbRegisterBenefitPreviewDto preview,
        Long contractId,
        String insuplcAdmdvs,
        boolean runCumulativeQuery) {
        if (!runCumulativeQuery || !registerBenefitQueryExtension.isEnabled() || preview == null) {
            return;
        }
        if (StringUtils.isBlank(preview.getPsnNo()) || contractId == null) {
            return;
        }
        try {
            InsuranceCumulativeQueryExtensionIn queryCommand = new InsuranceCumulativeQueryExtensionIn();
            queryCommand.setContractId(contractId);
            queryCommand.setPsnNo(preview.getPsnNo());
            queryCommand.setInsurancePoolAreaCode(
                StringUtils.isNotBlank(insuplcAdmdvs) ? insuplcAdmdvs : preview.getInsuplcAdmdvs());
            queryCommand.setCumYm(YearMonth.now().format(GeneralSharedConstants.YEAR_MONTH_FORMATTER));
            appendCumulativePreview(preview, registerBenefitQueryExtension.queryCumulative(queryCommand));
        } catch (RuntimeException ex) {
            log.warn("5206 人员累计查询失败 contractId={} psnNo={}: {}", contractId, preview.getPsnNo(), ex.getMessage());
        }
    }

    /**
     * 5206 累计查询结果 → 预览 cumulativeInfoList。
     *
     * @param preview  医保预览 DTO
     * @param response 5206 查询结果
     */
    private void appendCumulativePreview(YbRegisterBenefitPreviewDto preview, InsuranceCumulativeQueryExtensionOut response) {
        preview.getCumulativeInfoList().clear();
        if (response == null || response.getCumulativeLines() == null) {
            return;
        }
        for (InsuranceCumulativeLineSnapshot row : response.getCumulativeLines()) {
            YbCumulativeInfoPreviewDto item = new YbCumulativeInfoPreviewDto();
            item.setInsutype(row.getInsutype());
            item.setYear(row.getYear());
            item.setCumYm(row.getCumYm());
            item.setCumTypeCode(row.getCumTypeCode());
            item.setCum(row.getCum() != null ? row.getCum().toPlainString() : null);
            preview.getCumulativeInfoList().add(item);
        }
    }

    /**
     * 可选执行慢特病备案查询并写入预览（5301 主查；失败仅 warn）。
     *
     * @param preview            医保预览
     * @param contractId         合同 id
     * @param insuplcAdmdvs      参保地
     * @param runOpspFilingQuery 是否执行查询
     */
    private void attachOpspFilingIfNeeded(
        YbRegisterBenefitPreviewDto preview,
        Long contractId,
        String insuplcAdmdvs,
        boolean runOpspFilingQuery) {
        if (!runOpspFilingQuery || !registerBenefitQueryExtension.isEnabled() || preview == null) {
            return;
        }
        if (StringUtils.isBlank(preview.getPsnNo())) {
            return;
        }
        try {
            InsuranceOpspFilingQueryExtensionIn queryCommand = new InsuranceOpspFilingQueryExtensionIn();
            queryCommand.setContractId(contractId);
            queryCommand.setPsnNo(preview.getPsnNo());
            queryCommand.setInsurancePoolAreaCode(
                StringUtils.isNotBlank(insuplcAdmdvs) ? insuplcAdmdvs : preview.getInsuplcAdmdvs());
            appendOpspFilingPreview(preview, registerBenefitQueryExtension.queryOpspFiling(queryCommand));
        } catch (RuntimeException ex) {
            log.warn("慢特病备案查询失败 contractId={} psnNo={}: {}", contractId, preview.getPsnNo(), ex.getMessage());
        }
    }

    /**
     * 备案查询结果 → 预览 opspFilingList。
     *
     * @param preview  医保预览 DTO
     * @param response 备案查询结果
     */
    private void appendOpspFilingPreview(YbRegisterBenefitPreviewDto preview, InsuranceOpspFilingQueryExtensionOut response) {
        preview.getOpspFilingList().clear();
        if (response == null || response.getFilingLines() == null) {
            return;
        }
        for (InsuranceOpspFilingLineSnapshot row : response.getFilingLines()) {
            YbOpspFilingPreviewDto item = new YbOpspFilingPreviewDto();
            item.setOpspDiseCode(row.getOpspDiseCode());
            item.setOpspDiseName(row.getOpspDiseName());
            item.setBegndate(row.getBegndate());
            item.setEnddate(row.getEnddate());
            preview.getOpspFilingList().add(item);
        }
    }

    /**
     * 可选执行 2001 待遇检查并写入预览。
     *
     * <p>跨省失败：只 warn，写入「暂不可查」提示且不弹错；省内失败：写入提示并标记
     * {@code treatmentCheckAlert=true} 供前端警告。慢特病备案查询与本方法解耦。</p>
     *
     * @param preview           医保预览
     * @param contractId        合同 id
     * @param insuplcAdmdvs     参保地
     * @param medType           医疗类别
     * @param runTreatmentCheck 是否执行检查
     */
    private void attachTreatmentCheckIfNeeded(
        YbRegisterBenefitPreviewDto preview,
        Long contractId,
        String insuplcAdmdvs,
        String medType,
        boolean runTreatmentCheck) {
        if (!runTreatmentCheck || !registerBenefitQueryExtension.isEnabled() || preview == null) {
            return;
        }
        if (StringUtils.isBlank(preview.getPsnNo()) || StringUtils.isBlank(preview.getInsutype())) {
            return;
        }
        String admdvs = insuplcAdmdvs;
        // Pre：请求未带参保地时回落预览参保地
        if (StringUtils.isBlank(admdvs)) {
            admdvs = preview.getInsuplcAdmdvs();
        }
        try {
            InsuranceTreatmentCheckExtensionIn checkCommand = new InsuranceTreatmentCheckExtensionIn();
            checkCommand.setContractId(contractId);
            checkCommand.setPsnNo(preview.getPsnNo());
            checkCommand.setInsutype(preview.getInsutype());
            checkCommand.setInsurancePoolAreaCode(admdvs);
            String checkMedType = YbMedType.GENERAL_OUTPATIENT.getCode();
            if (StringUtils.isNotBlank(medType)) {
                checkMedType = medType.trim();
            }
            checkCommand.setMedType(checkMedType);
            appendTreatmentPreview(preview, registerBenefitQueryExtension.checkTreatment(checkCommand));
            preview.setTreatmentCheckHint(null);
            preview.setTreatmentCheckAlert(Boolean.FALSE);
        } catch (RuntimeException ex) {
            boolean crossProvince = this.isCrossProvinceTreatmentArea(admdvs, contractId);
            // 跨省：平台常拒查省外待遇，静默并提示以备案/结算为准
            if (crossProvince) {
                log.warn(
                    "2001 跨省待遇检查失败（静默） contractId={} psnNo={}: {}",
                    contractId,
                    preview.getPsnNo(),
                    ex.getMessage());
                preview.setTreatmentCheckHint("省外参保人待遇暂不可查，请以慢特病备案与结算结果为准");
                preview.setTreatmentCheckAlert(Boolean.FALSE);
                return;
            }
            // 省内：保留可感知提示，供前端弹警告（不阻断慢特病列表）
            log.warn("2001 待遇检查失败 contractId={} psnNo={}: {}", contractId, preview.getPsnNo(), ex.getMessage());
            String detail = StringUtils.trimToNull(ex.getMessage());
            if (detail == null) {
                preview.setTreatmentCheckHint("待遇检查失败，请稍后重试或联系信息科");
            } else {
                preview.setTreatmentCheckHint("待遇检查失败：" + detail);
            }
            preview.setTreatmentCheckAlert(Boolean.TRUE);
        }
    }

    /**
     * 判断参保地相对当前合同就医地是否跨省。
     *
     * @param insuplcAdmdvs 参保地区划
     * @param contractId    合同 id（解析就医地）
     * @return true=跨省
     */
    private boolean isCrossProvinceTreatmentArea(String insuplcAdmdvs, Long contractId) {
        if (contractId == null || StringUtils.isBlank(insuplcAdmdvs)) {
            return false;
        }
        try {
            String mdtrtareaAdmdvs = registerBenefitQueryExtension.resolveTreatmentAreaCode(contractId);
            YbRegisterBenefitAreaHintDto hint =
                RegisterBenefitAreaHintHelper.build(insuplcAdmdvs, mdtrtareaAdmdvs);
            return RegisterBenefitAreaHintHelper.CROSS_PROVINCE.equals(hint.getAreaKind());
        } catch (RuntimeException ex) {
            log.warn("跨省判断失败 contractId={}: {}", contractId, ex.getMessage());
            return false;
        }
    }

    /**
     * 2001 待遇检查结果 → 预览 treatmentInfoList 与 treatmentBlocked 标记。
     *
     * @param preview  医保预览 DTO
     * @param response 2001 检查结果
     */
    private void appendTreatmentPreview(YbRegisterBenefitPreviewDto preview, InsuranceTreatmentCheckExtensionOut response) {
        preview.getTreatmentInfoList().clear();
        boolean blocked = false;
        if (response == null || response.getTreatmentLines() == null) {
            preview.setTreatmentBlocked(Boolean.FALSE);
            return;
        }
        for (InsuranceTreatmentLineSnapshot row : response.getTreatmentLines()) {
            YbTreatmentInfoPreviewDto item = new YbTreatmentInfoPreviewDto();
            item.setPsnNo(row.getPsnNo());
            item.setTrtChkType(row.getTrtChkType());
            item.setFundPayType(row.getFundPayType());
            item.setTrtEnjymntFlag(row.getTrtEnjymntFlag());
            item.setBegndate(row.getBegndate());
            item.setEnddate(row.getEnddate());
            item.setTrtChkRslt(row.getTrtChkRslt());
            preview.getTreatmentInfoList().add(item);
            String flag = StringUtils.trimToEmpty(row.getTrtEnjymntFlag());
            if (StringUtils.isNotBlank(flag) && !"1".equals(flag)) {
                blocked = true;
            }
        }
        preview.setTreatmentBlocked(blocked);
    }

    /**
     * Extension 人员查询结果 → 挂号待遇预览 DTO（含险种列表与 preferredInsutype 回落）。
     *
     * @param person            人员查询结果
     * @param preferredInsutype 优先险种 code
     * @return 预览 DTO
     */
    private YbRegisterBenefitPreviewDto toYbRegisterBenefitPreview(
        InsurancePersonQueryExtensionOut person,
        String preferredInsutype) {
        YbRegisterBenefitPreviewDto preview = new YbRegisterBenefitPreviewDto();
        preview.setPsnNo(person.getPsnNo());
        preview.setPsnName(person.getPsnName());
        preview.setCertno(person.getCertno());
        preview.setGend(person.getGend());
        preview.setBrdy(person.getBrdy());
        preview.setInsuplcAdmdvs(person.getInsuplcAdmdvs());
        preview.setInsutype(person.getInsutype());
        preview.setBalc(person.getBalc());
        preview.setResolvedContractBusNo(person.getResolvedContractBusNo());
        if (person.getResolvedContractId() != null) {
            preview.setResolvedContractId(String.valueOf(person.getResolvedContractId()));
        }
        if (person.getCoverageLines() != null) {
            for (InsuranceCoverageLineSnapshot row : person.getCoverageLines()) {
                YbInsuInfoPreviewDto item = new YbInsuInfoPreviewDto();
                item.setInsuplcAdmdvs(row.getInsuplcAdmdvs());
                item.setInsutype(row.getInsutype());
                item.setPsnType(row.getPsnType());
                item.setPsnInsuStas(row.getPsnInsuStas());
                item.setPsnInsuDate(row.getPsnInsuDate());
                item.setCvlservFlag(row.getCvlservFlag());
                item.setEmpName(row.getEmpName());
                item.setBalc(row.getBalc());
                preview.getInsuinfoList().add(item);
            }
        }
        if (StringUtils.isNotBlank(preferredInsutype) && person.getCoverageLines() != null) {
            for (InsuranceCoverageLineSnapshot row : person.getCoverageLines()) {
                if (preferredInsutype.equals(row.getInsutype())) {
                    preview.setInsutype(row.getInsutype());
                    preview.setInsuplcAdmdvs(row.getInsuplcAdmdvs());
                    preview.setBalc(row.getBalc());
                    break;
                }
            }
        }
        return preview;
    }

    /**
     * 在预结算前解析患者主档：有患者ID直接复用，否则按身份证号查重后复用或建档。
     *
     * <p>复用既有患者（直选 ID 或身份证命中）时：</p>
     * <ul>
     *   <li>表单非空联系电话与主档不一致 → {@link PatientDomainService#updatePatientPhone}（BR-REG-018）</li>
     *   <li>读卡/表单非空地址全文与主档不一致 → {@link PatientDomainService#updatePatientAddress}（BR-REG-019）</li>
     * </ul>
     *
     * @param request 预结算请求
     * @return 可用患者ID
     * @throws BusinessException 新建患者时姓名/性别未补齐，或当前登录机构不可用时抛出
     */
    private Long resolvePatientIdForPreRegister(PreRegisterSettlementRequest request) {
        // 已选中既有患者时，直接复用前端指定患者，并按需回写电话与地址。
        if (request.getPatientId() != null) {
            this.syncPatientPhoneFromPreRegisterIfNeeded(request.getPatientId(), request.getPhone());
            this.syncPatientAddressFromPreRegisterIfNeeded(request.getPatientId(), request.getAddress());
            return request.getPatientId();
        }
        // 未传身份证号时跳过同人识别，直接按最小建档字段走后续建档流程。
        if (request.getIdCardNo() != null && !request.getIdCardNo().isBlank()) {
            // 按身份证精确命中既有患者，命中后复用该患者并回写电话与地址。
            PatientSimpleQueryModel matchedPatient = patientQueryService.findPatientByIdCard(request.getIdCardNo());
            if (matchedPatient != null) {
                this.syncPatientPhoneFromPreRegisterIfNeeded(matchedPatient.getId(), request.getPhone());
                this.syncPatientAddressFromPreRegisterIfNeeded(matchedPatient.getId(), request.getAddress());
                return matchedPatient.getId();
            }
        }
        // 身份证未命中既有患者时，要求最小建档字段后新建患者主档。
        if (request.getName() == null || request.getName().isBlank() || request.getGender() == null || request.getGender().isBlank()) {
            throw new BusinessException("REG-PAT-VAL-001", "请补齐患者信息");
        }
        // 新建患者主档时，机构归属强制取当前会话机构，不使用前端挂号科室入参。
        Long patientOrganizationId = managedOrganizationIdsResolver.requireSessionOrganizationUnitId(
            "REG-VAL-001", "当前登录机构不能为空");
        RegisterPatientCommand command = outpatientRegistrationMapper.toRegisterPatientCommand(request);
        // RegisterPatientCommand 同时支持 item（单条）与 items（批量）两种形态：
        // 这里统一覆盖 organizationId，确保“患者建档机构”始终取当前登录机构，而非前端挂号科室入参。
        if (command.getItem() != null) {
            command.getItem().setOrganizationId(patientOrganizationId);
        }
        if (command.getItems() != null) {
            for (RegisterPatientCommand.RegisterPatientItemCommand item : command.getItems()) {
                item.setOrganizationId(patientOrganizationId);
            }
        }
        return patientDomainService.registerPatient(command);
    }

    /**
     * 复用既有患者时，将挂号表单非空联系电话回写主档（与主档相同则跳过）。
     *
     * <p>挂号列表「联系电话」取自 {@code Patient.phone}；仅新建分支会在建档命令写入 phone，
     * 复用分支须显式回写，否则表单填写的电话不会出现在历史就诊列表。</p>
     *
     * @param patientId 既有患者主键
     * @param formPhone 挂号表单联系电话；空则不改主档
     */
    private void syncPatientPhoneFromPreRegisterIfNeeded(Long patientId, String formPhone) {
        // Pre：表单未填电话则不改主档，避免空串覆盖既有号码。
        if (formPhone == null || formPhone.isBlank()) {
            return;
        }
        String trimmedPhone = formPhone.trim();
        PatientDetailQueryModel detail = patientQueryService.loadPatientDetail(patientId);
        // Pre：主档不存在时跳过回写，后续开立就诊再暴露错误。
        if (detail == null || detail.getPatient() == null) {
            return;
        }
        String currentPhone = detail.getPatient().getPhone();
        // Pre：主档已是同一号码则跳过写库与领域事件。
        if (Objects.equals(trimmedPhone, currentPhone)) {
            return;
        }
        // Cmd：回写主档 phone，供挂号列表 contactPhone 回显。
        UpdatePatientPhoneCommand command =
            outpatientRegistrationMapper.toUpdatePatientPhoneCommand(patientId, trimmedPhone);
        patientDomainService.updatePatientPhone(command);
    }

    /**
     * 复用既有患者时，将读卡/表单非空地址全文回写主档（与主档相同则跳过）。
     *
     * <p>仅写 {@code Patient.address}（全文）；空串不覆盖。患者管理「详细地址」读该字段。</p>
     *
     * @param patientId 既有患者主键
     * @param formAddress 读卡或表单地址全文；空则不改主档
     */
    private void syncPatientAddressFromPreRegisterIfNeeded(Long patientId, String formAddress) {
        // Pre：未带地址则不改主档，避免空串覆盖既有住址。
        if (formAddress == null || formAddress.isBlank()) {
            return;
        }
        String trimmedAddress = formAddress.trim();
        PatientDetailQueryModel detail = patientQueryService.loadPatientDetail(patientId);
        // Pre：主档不存在时跳过回写，后续开立就诊再暴露错误。
        if (detail == null || detail.getPatient() == null) {
            return;
        }
        String currentAddress = detail.getPatient().getAddress();
        // Pre：主档已是同一全文则跳过写库与领域事件。
        if (Objects.equals(trimmedAddress, currentAddress)) {
            return;
        }
        // Cmd：回写主档地址全文，供患者管理「详细地址」回显（BR-REG-019）。
        UpdatePatientAddressCommand command =
            outpatientRegistrationMapper.toUpdatePatientAddressCommand(patientId, trimmedAddress);
        patientDomainService.updatePatientAddress(command);
    }

    /**
     * 批量汇总挂号列表页各行挂号费（adm_healthcare_service 锚点 total_price 代数和）。
     *
     * @param rows 挂号列表读模型
     * @return encounter+service 复合键 → 挂号费合计
     */
    private Map<String, BigDecimal> buildRegistrationFeeTotalsForClinicRegisterPage(List<ClinicRegisterQueryModel> rows) {
        Map<String, BigDecimal> totalsByAnchor = new HashMap<>();
        if (rows == null || rows.isEmpty()) {
            return totalsByAnchor;
        }
        // 收集本页就诊主键并去重，再批量拉明细（简单 filter/map/collect，避免逐行打库）。
        LinkedHashSet<Long> encounterIdsOnPage = rows.stream()
            .map(ClinicRegisterQueryModel::getEncounterId)
            .filter(Objects::nonNull)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        if (encounterIdsOnPage.isEmpty()) {
            return totalsByAnchor;
        }
        ChargeItemQueryCommand batchCmd = new ChargeItemQueryCommand();
        batchCmd.setEncounterIds(new ArrayList<>(encounterIdsOnPage));
        List<ChargeItem> chargeLines = chargeItemQueryService.listChargeItemsByEncounterIds(batchCmd);
        String healthcareServiceSlot = TableNameConstants.ADM_HEALTHCARE_SERVICE;
        chargeLines.stream()
            .filter(Objects::nonNull)
            .filter(line -> healthcareServiceSlot.equals(line.getServiceTable()))
            .forEach(line -> {
                // 与预结算写入一致：号源类型挂在 adm_healthcare_service 锚点；复合键须与 Mapper 列表行查表字符串一致。
                Long eid = line.getEncounterId();
                Long sid = line.getServiceId();
                if (eid == null || sid == null) {
                    return;
                }
                String sep = "\u0001";
                String anchorKey = eid + sep + healthcareServiceSlot + sep + sid;
                BigDecimal lineTotal = line.getTotalPrice();
                if (lineTotal == null) {
                    return;
                }
                totalsByAnchor.merge(anchorKey, lineTotal, BigDecimal::add);
            });
        return totalsByAnchor;
    }

    /**
     * 按就诊解析挂号费收费项主键（{@code service_table = adm_healthcare_service}）。
     *
     * @param encounterIds 就诊主键
     * @return 就诊 → 挂号费 chargeItem 主键集合
     */
    private Map<Long, LinkedHashSet<Long>> resolveRegistrationFeeChargeItemIdsByEncounter(
        Collection<Long> encounterIds) {
        Map<Long, LinkedHashSet<Long>> result = new HashMap<>();
        if (encounterIds == null || encounterIds.isEmpty()) {
            return result;
        }
        List<Long> idList = new ArrayList<>();
        for (Long encounterId : encounterIds) {
            if (encounterId != null) {
                idList.add(encounterId);
            }
        }
        if (idList.isEmpty()) {
            return result;
        }
        ChargeItemQueryCommand batchCmd = new ChargeItemQueryCommand();
        batchCmd.setEncounterIds(idList);
        List<ChargeItem> chargeLines = chargeItemQueryService.listChargeItemsByEncounterIds(batchCmd);
        if (chargeLines == null || chargeLines.isEmpty()) {
            return result;
        }
        String healthcareServiceSlot = TableNameConstants.ADM_HEALTHCARE_SERVICE;
        for (ChargeItem line : chargeLines) {
            if (line == null || line.getId() == null || line.getEncounterId() == null) {
                continue;
            }
            if (!healthcareServiceSlot.equals(line.getServiceTable())) {
                continue;
            }
            result.computeIfAbsent(line.getEncounterId(), key -> new LinkedHashSet<>()).add(line.getId());
        }
        return result;
    }

    /**
     * 挂号列表：按挂号费 chargeItem 锚定对应 SUCCESS 支付流水（供退号银联退货/补打挂号小票）。
     *
     * @param encounterIds 已挂号就诊主键
     * @return 就诊 → 挂号支付流水主键
     */
    private Map<Long, Long> buildRegistrationFeePaymentIdsForClinicRegisterPage(List<Long> encounterIds) {
        Map<Long, LinkedHashSet<Long>> chargeItemIdsByEncounter =
            this.resolveRegistrationFeeChargeItemIdsByEncounter(encounterIds);
        return paymentReconciliationQueryService.findSuccessPaymentIdsOverlappingChargeItemsByEncounter(
            chargeItemIdsByEncounter);
    }

    /**
     * 退号退费：选出挂载挂号费 chargeItem 的正向支付；找不到则业务失败。
     *
     * @param encounterId 就诊主键
     * @return 挂号费支付读模型
     */
    private PaymentReconciliationQueryModel requireRegistrationFeePaymentForCancel(Long encounterId) {
        Map<Long, LinkedHashSet<Long>> chargeItemIdsByEncounter =
            this.resolveRegistrationFeeChargeItemIdsByEncounter(List.of(encounterId));
        LinkedHashSet<Long> registrationChargeItemIds = chargeItemIdsByEncounter.get(encounterId);
        if (registrationChargeItemIds == null || registrationChargeItemIds.isEmpty()) {
            throw new BusinessException(
                ErrorCodeConstants.PAYMENT_RECONCILIATION_INVALID_ARGUMENT,
                "未找到挂号费收费项，无法退号退费");
        }
        List<PaymentReconciliationQueryModel> hit =
            paymentReconciliationQueryService.findPaySuccessOrRefundingOverlappingChargeItems(
                encounterId,
                registrationChargeItemIds);
        if (!hit.isEmpty() && hit.get(0) != null) {
            return hit.get(0);
        }
        throw new BusinessException(
            ErrorCodeConstants.PAYMENT_RECONCILIATION_INVALID_ARGUMENT,
            "未找到挂号费对应的支付流水，无法退号退费");
    }

    /**
     * 是否存在挂号支付草稿绑定的 ACTIVE 2206 预结算（有则应付取个人现金，而非原价）。
     *
     * @param paymentDraftId 支付草稿主键
     * @return true 表示已有有效医保预结算
     */
    private boolean hasActiveRegisterFeeInsurancePreSettle(Long paymentDraftId) {
        if (!chargeInsuranceExtension.isEnabled() || paymentDraftId == null) {
            return false;
        }
        return !chargeInsuranceExtension.listActivePreSettlesByPaymentDraft(
            new OutpatientChargePreSettleListExtensionIn().setPaymentDraftId(paymentDraftId)).isEmpty();
    }

    /**
     * 草稿上是否已有 CONFIRMED 的挂号费 2207 回执（扫码前已正式结算场景）。
     *
     * <p>此时 {@code yb_clinic_pre_settle} 已为 SETTLED，不再出现在 ACTIVE 列表；
     * 落账仍须按草稿 {@code displayAmount}（个人现金段）校验，与门诊收费补收口径一致。</p>
     *
     * @param paymentDraftId 支付草稿主键
     * @return 存在未撤销的 CONFIRMED 回执时为 true
     */
    private boolean hasConfirmedRegisterFeeInsuranceSettlement(Long paymentDraftId) {
        if (!chargeInsuranceExtension.isEnabled() || paymentDraftId == null) {
            return false;
        }
        List<OutpatientChargeSettlementRecordExtensionOut> records =
            chargeInsuranceExtension.listSettlementsByPayment(paymentDraftId);
        // 无回执则非半成功医保侧
        if (records == null || records.isEmpty()) {
            return false;
        }
        for (OutpatientChargeSettlementRecordExtensionOut row : records) {
            // Skip：空行
            if (row == null) {
                continue;
            }
            // CONFIRMED：平台已正式结算，本地可能尚未 payRegisterFee 落成
            if ("CONFIRMED".equalsIgnoreCase(StringUtils.trimToEmpty(row.getStatus()))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 挂号费是否应按草稿 displayAmount（个人现金段）作为应付口径。
     *
     * <p>含：ACTIVE 2206；以及扫码前已 2207 导致预结算 SETTLED、但仍挂 CONFIRMED 回执的半成功路径。</p>
     *
     * @param paymentDraftId 支付草稿主键
     * @return true 时应付取 displayAmount，禁止回落 chargeItem 全额原价
     */
    private boolean isRegisterFeeInsuranceCashSegmentPayable(Long paymentDraftId) {
        // ACTIVE 2206：预结算尚未正式确认
        if (this.hasActiveRegisterFeeInsurancePreSettle(paymentDraftId)) {
            return true;
        }
        // CONFIRMED 2207：扫码前已结算，预结算已 SETTLED
        return this.hasConfirmedRegisterFeeInsuranceSettlement(paymentDraftId);
    }

    /**
     * 解析挂号正式结算应付：优先 2207/2206 Σ psn_cash；否则 chargeItem 总价。
     *
     * @param paymentDraft           支付草稿
     * @param registrationChargeLine 挂号 chargeItem
     * @return 应付金额
     */
    private BigDecimal resolveRegisterFeeExpectedPayable(
        PaymentReconciliationQueryModel paymentDraft,
        ChargeItem registrationChargeLine) {
        BigDecimal chargeTotal = registrationChargeLine.getTotalPrice();
        if (chargeTotal == null) {
            chargeTotal = BigDecimal.ZERO;
        }
        if (paymentDraft.getId() != null && chargeInsuranceExtension.isEnabled()) {
            BigDecimal fromConfirmed =
                this.resolveRegisterFeePatientCashFromConfirmed(paymentDraft.getId(), chargeTotal);
            if (fromConfirmed != null) {
                return fromConfirmed;
            }
            BigDecimal fromActive =
                this.resolveRegisterFeePatientCashFromActivePreSettle(paymentDraft.getId(), chargeTotal);
            if (fromActive != null) {
                return fromActive;
            }
        }
        return chargeTotal;
    }

    /**
     * CONFIRMED 2207 挂号权威现金。
     */
    private BigDecimal resolveRegisterFeePatientCashFromConfirmed(Long paymentDraftId, BigDecimal chargeTotal) {
        List<OutpatientChargeSettlementRecordExtensionOut> records =
            chargeInsuranceExtension.listSettlementsByPayment(paymentDraftId);
        if (records == null || records.isEmpty()) {
            return null;
        }
        BigDecimal sumPsnCash = BigDecimal.ZERO;
        BigDecimal sumMedfee = BigDecimal.ZERO;
        boolean any = false;
        for (OutpatientChargeSettlementRecordExtensionOut row : records) {
            if (row == null || !"CONFIRMED".equalsIgnoreCase(StringUtils.trimToEmpty(row.getStatus()))) {
                continue;
            }
            any = true;
            if (row.getPsnCashPay() != null) {
                sumPsnCash = sumPsnCash.add(row.getPsnCashPay());
            }
            if (row.getMedfeeSumamt() != null) {
                sumMedfee = sumMedfee.add(row.getMedfeeSumamt());
            }
        }
        if (!any) {
            return null;
        }
        return InsuranceSettlementCashAlignSupport.resolveTargetPatientCash(sumPsnCash, sumMedfee, chargeTotal);
    }

    /**
     * ACTIVE 2206 挂号权威现金。
     */
    private BigDecimal resolveRegisterFeePatientCashFromActivePreSettle(Long paymentDraftId, BigDecimal chargeTotal) {
        List<OutpatientChargePreSettleQueryExtensionOut> active =
            chargeInsuranceExtension.listActivePreSettlesByPaymentDraft(
                new OutpatientChargePreSettleListExtensionIn().setPaymentDraftId(paymentDraftId));
        if (active == null || active.isEmpty()) {
            return null;
        }
        BigDecimal sumPsnCash = BigDecimal.ZERO;
        BigDecimal sumMedfee = BigDecimal.ZERO;
        for (OutpatientChargePreSettleQueryExtensionOut row : active) {
            if (row == null) {
                continue;
            }
            if (row.getPsnCashPay() != null) {
                sumPsnCash = sumPsnCash.add(row.getPsnCashPay());
            }
            if (row.getMedfeeSumamt() != null) {
                sumMedfee = sumMedfee.add(row.getMedfeeSumamt());
            }
        }
        return InsuranceSettlementCashAlignSupport.resolveTargetPatientCash(sumPsnCash, sumMedfee, chargeTotal);
    }

    /**
     * 汇总挂号缴费请求各支付渠道提交金额。
     *
     * @param request 缴费请求
     * @return 提交金额合计
     */
    private BigDecimal sumRegisterFeeSubmitAmount(PayRegisterFeeRequest request) {
        BigDecimal total = BigDecimal.ZERO;
        for (PaymentDetailDto detail : request.getPaymentDetails()) {
            if (detail.getPayAmount() != null) {
                total = total.add(detail.getPayAmount());
            }
        }
        return total;
    }

    /**
     * 挂号扫码渠道金额门禁：含 {@code SCAN_PAY} 时须金额 &gt; 0。
     *
     * @param request 挂号缴费请求
     */
    private void assertScanPayChannelAmountValid(PayRegisterFeeRequest request) {
        PaymentDetailDto scanLine = this.findScanPayDetailLine(request);
        if (scanLine == null) {
            return;
        }
        if (scanLine.getPayAmount() == null || scanLine.getPayAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("PAY-VAL-001", "零现金应付禁止使用扫码支付渠道，请重新预结算后缴费");
        }
    }

    /**
     * 挂号本地落账前扫码回执门禁：含 {@code SCAN_PAY} 时须已提交 {@code clientScanResult}。
     *
     * @param request 挂号缴费请求
     */
    private void assertClientScanResultPresentForScanPay(PayRegisterFeeRequest request) {
        PaymentDetailDto scanLine = this.findScanPayDetailLine(request);
        if (scanLine == null) {
            return;
        }
        if (request.getClientScanResult() == null) {
            throw new BusinessException("PAY-VAL-001", "扫码支付须提交 clientScanResult");
        }
    }

    /**
     * 从挂号缴费明细定位扫码支付行。
     *
     * @param request 挂号缴费请求
     * @return SCAN_PAY 行；无则 null
     */
    private PaymentDetailDto findScanPayDetailLine(PayRegisterFeeRequest request) {
        if (request.getPaymentDetails() == null || request.getPaymentDetails().isEmpty()) {
            return null;
        }
        for (PaymentDetailDto detail : request.getPaymentDetails()) {
            if (detail == null) {
                continue;
            }
            if (PaymentChannel.SCAN_PAY.getCode().equals(StringUtils.trimToEmpty(detail.getPayChannelCode()))) {
                return detail;
            }
        }
        return null;
    }

    /**
     * 挂号正式结算前逐 ACTIVE 2206 批次调 Extension 2207；已确认则复用（部分失败补偿）。
     *
     * <p><b>防单边</b>：扫码前已独立 2207 时预结算离开 ACTIVE，须从 CONFIRMED 回执解析批次并复用，
     * 禁止空 ACTIVE 直接当「无医保」导致只落现金、漏写统筹/个账与 {@code fin_payment_yb_settlement}。</p>
     *
     * @param request      缴费请求
     * @param paymentDraft 支付草稿读模型
     * @return 2207 确认结果列表
     * @throws BusinessException 医保结算失败或凭证过期
     */
    private List<OutpatientChargeConfirmExtensionOut> collectRegisterFeeInsuranceConfirmResults(
        PayRegisterFeeRequest request,
        PaymentReconciliationQueryModel paymentDraft) {
        if (!chargeInsuranceExtension.isEnabled() || request.getPaymentDraftId() == null) {
            return List.of();
        }
        List<String> chrgBchnos = this.resolveRegisterFeeConfirmChrgBchnos(request.getPaymentDraftId());
        // Pre：扫码前已独立 2207 时复用 CONFIRMED 回执。
        List<OutpatientChargeConfirmExtensionOut> reused =
            this.tryReuseRegisterConfirmedInsuranceResults(request.getPaymentDraftId(), chrgBchnos);
        if (!reused.isEmpty()) {
            // 请求批次可能是子集：复用后仍须并入草稿上其它已 CONFIRMED 回执
            return this.mergeRegisterConfirmedInsuranceResults(request.getPaymentDraftId(), reused);
        }
        // Pre：批次解析失败但草稿上仍有 CONFIRMED → 全量复用，禁止再当「无医保」只落现金
        if (chrgBchnos.isEmpty()) {
            return this.tryReuseAllRegisterConfirmedInsuranceResults(request.getPaymentDraftId());
        }
        List<OutpatientChargeConfirmExtensionOut> results = new ArrayList<>();
        int succeededBatches = 0;
        try {
            for (String chrgBchno : chrgBchnos) {
                OutpatientChargeConfirmExtensionIn command = new OutpatientChargeConfirmExtensionIn();
                command.setPaymentDraftId(request.getPaymentDraftId());
                command.setChargeBatchNo(chrgBchno);
                command.setEncounterId(paymentDraft.getEncounterId());
                results.add(chargeInsuranceExtension.confirmBeforeLocalSettle(command));
                succeededBatches += 1;
            }
        } catch (RuntimeException ex) {
            if (succeededBatches > 0) {
                outpatientChargeInsuranceCompensationOrchestration.compensateSettlementsByPayment(request.getPaymentDraftId());
            }
            if (this.isInsuranceCredentialExpired(ex)) {
                throw new BusinessException(
                    "INS-015",
                    "医保电子凭证已失效，请重新展码并刷新预结算后再缴费");
            }
            throw new BusinessException("INS-013", "挂号费医保结算失败，请勿重复收费，联系医保办处理");
        }
        // Pre：本请求 2207 成功后仍兜底合并草稿上其它 CONFIRMED，避免部分批次漏 merge
        return this.mergeRegisterConfirmedInsuranceResults(request.getPaymentDraftId(), results);
    }

    /**
     * 解析挂号费待确认/复用批次：优先 ACTIVE 预结算；为空则回落 CONFIRMED 回执批次（先 2207 后缴费）。
     *
     * @param paymentDraftId 支付草稿主键
     * @return chrg_bchno 列表（去重保序）
     */
    private List<String> resolveRegisterFeeConfirmChrgBchnos(Long paymentDraftId) {
        if (paymentDraftId == null) {
            return List.of();
        }
        LinkedHashSet<String> batches = new LinkedHashSet<>();
        List<OutpatientChargePreSettleQueryExtensionOut> active =
            chargeInsuranceExtension.listActivePreSettlesByPaymentDraft(
                new OutpatientChargePreSettleListExtensionIn().setPaymentDraftId(paymentDraftId));
        if (active != null) {
            for (OutpatientChargePreSettleQueryExtensionOut row : active) {
                if (row == null || StringUtils.isBlank(row.getChargeBatchNo())) {
                    continue;
                }
                batches.add(row.getChargeBatchNo().trim());
            }
        }
        if (!batches.isEmpty()) {
            return new ArrayList<>(batches);
        }
        // Pre：ACTIVE 已空（扫码前 2207 已把预结算置 SETTLED）→ 用 CONFIRMED 回执批次复用落账
        return this.listRegisterConfirmedChargeBatchNos(paymentDraftId);
    }

    /**
     * 草稿上全部 CONFIRMED 回执的收费批次号（去重保序）。
     *
     * @param paymentDraftId 支付草稿主键
     * @return chrg_bchno 列表
     */
    private List<String> listRegisterConfirmedChargeBatchNos(Long paymentDraftId) {
        LinkedHashSet<String> batches = new LinkedHashSet<>();
        for (OutpatientChargeSettlementRecordExtensionOut row :
            this.listRegisterConfirmedSettlementRecords(paymentDraftId)) {
            if (StringUtils.isBlank(row.getChargeBatchNo())) {
                continue;
            }
            batches.add(row.getChargeBatchNo().trim());
        }
        return new ArrayList<>(batches);
    }

    /**
     * 草稿上 CONFIRMED 且含 setl_id 的结算回执摘要。
     *
     * @param paymentDraftId 支付草稿主键
     * @return 回执列表；无则空列表
     */
    private List<OutpatientChargeSettlementRecordExtensionOut> listRegisterConfirmedSettlementRecords(
        Long paymentDraftId) {
        if (paymentDraftId == null) {
            return List.of();
        }
        List<OutpatientChargeSettlementRecordExtensionOut> records =
            chargeInsuranceExtension.listSettlementsByPayment(paymentDraftId);
        if (records == null || records.isEmpty()) {
            return List.of();
        }
        List<OutpatientChargeSettlementRecordExtensionOut> confirmed = new ArrayList<>();
        for (OutpatientChargeSettlementRecordExtensionOut row : records) {
            if (row == null || StringUtils.isBlank(row.getSettlementId())) {
                continue;
            }
            if (!"CONFIRMED".equalsIgnoreCase(StringUtils.trimToEmpty(row.getStatus()))) {
                continue;
            }
            confirmed.add(row);
        }
        return confirmed;
    }

    /**
     * 挂号：草稿上已有覆盖全部批次的 CONFIRMED 回执时映射为确认结果。
     *
     * @param paymentDraftId 支付草稿
     * @param chrgBchnos     目标批次；空则回落全量 CONFIRMED
     * @return 可复用结果；否则空
     */
    private List<OutpatientChargeConfirmExtensionOut> tryReuseRegisterConfirmedInsuranceResults(
        Long paymentDraftId,
        List<String> chrgBchnos) {
        if (chrgBchnos == null || chrgBchnos.isEmpty()) {
            return List.of();
        }
        Map<String, OutpatientChargeSettlementRecordExtensionOut> confirmedByBatch = new HashMap<>();
        for (OutpatientChargeSettlementRecordExtensionOut row :
            this.listRegisterConfirmedSettlementRecords(paymentDraftId)) {
            if (StringUtils.isBlank(row.getChargeBatchNo())) {
                continue;
            }
            confirmedByBatch.put(row.getChargeBatchNo().trim(), row);
        }
        List<OutpatientChargeConfirmExtensionOut> reused = new ArrayList<>();
        for (String chrgBchno : chrgBchnos) {
            OutpatientChargeSettlementRecordExtensionOut hit = confirmedByBatch.get(chrgBchno);
            if (hit == null) {
                return List.of();
            }
            reused.add(this.toRegisterConfirmOutFromSettlementRecord(hit));
        }
        return reused;
    }

    /**
     * 批次未知时：草稿上全部 CONFIRMED 回执直接复用为确认结果（防单边最后兜底）。
     *
     * @param paymentDraftId 支付草稿主键
     * @return 确认结果；无 CONFIRMED 时为空
     */
    private List<OutpatientChargeConfirmExtensionOut> tryReuseAllRegisterConfirmedInsuranceResults(
        Long paymentDraftId) {
        List<OutpatientChargeConfirmExtensionOut> reused = new ArrayList<>();
        for (OutpatientChargeSettlementRecordExtensionOut row :
            this.listRegisterConfirmedSettlementRecords(paymentDraftId)) {
            reused.add(this.toRegisterConfirmOutFromSettlementRecord(row));
        }
        return reused;
    }

    /**
     * 将草稿上尚未出现在 results 中的 CONFIRMED 回执并入确认结果（按 setl_id 去重）。
     *
     * @param paymentDraftId 支付草稿主键
     * @param results        本请求已收集的确认结果
     * @return 合并后列表
     */
    private List<OutpatientChargeConfirmExtensionOut> mergeRegisterConfirmedInsuranceResults(
        Long paymentDraftId,
        List<OutpatientChargeConfirmExtensionOut> results) {
        List<OutpatientChargeConfirmExtensionOut> merged = new ArrayList<>();
        LinkedHashSet<String> seenSetlIds = new LinkedHashSet<>();
        if (results != null) {
            for (OutpatientChargeConfirmExtensionOut row : results) {
                if (row == null || StringUtils.isBlank(row.getSettlementId())) {
                    continue;
                }
                String setlId = row.getSettlementId().trim();
                if (!seenSetlIds.add(setlId)) {
                    continue;
                }
                merged.add(row);
            }
        }
        for (OutpatientChargeSettlementRecordExtensionOut row :
            this.listRegisterConfirmedSettlementRecords(paymentDraftId)) {
            String setlId = row.getSettlementId().trim();
            if (!seenSetlIds.add(setlId)) {
                continue;
            }
            merged.add(this.toRegisterConfirmOutFromSettlementRecord(row));
        }
        return merged;
    }

    /**
     * 结算回执摘要 → 挂号正式确认结果（须含 accountId，供 merge 统筹/个账）。
     *
     * @param row 回执摘要
     * @return 确认结果
     */
    private OutpatientChargeConfirmExtensionOut toRegisterConfirmOutFromSettlementRecord(
        OutpatientChargeSettlementRecordExtensionOut row) {
        return new OutpatientChargeConfirmExtensionOut()
            .setSettlementId(row.getSettlementId())
            .setChargeBatchNo(row.getChargeBatchNo())
            .setSettlementRecordId(row.getSettlementRecordId())
            .setAccountId(row.getAccountId())
            .setMdtrtId(row.getEncounterPlatformId())
            .setMedfeeSumamt(row.getMedfeeSumamt())
            .setFundPaySumamt(row.getFundPaySumamt())
            .setAcctPay(row.getAcctPay())
            .setPsnCashPay(row.getPsnCashPay());
    }

    /**
     * 2207 结果 → fin_payment_yb_settlement 写入项（挂号费专用）。
     *
     * @param results 2207 确认结果
     * @return 医保结算项命令列表
     */
    private List<PaymentYbSettlementItemCommand> buildRegisterFeeYbSettlementItems(
        List<OutpatientChargeConfirmExtensionOut> results) {
        if (results == null || results.isEmpty()) {
            return List.of();
        }
        List<PaymentYbSettlementItemCommand> items = new ArrayList<>();
        for (OutpatientChargeConfirmExtensionOut row : results) {
            if (row == null || StringUtils.isBlank(row.getSettlementId())) {
                continue;
            }
            PaymentYbSettlementItemCommand item = new PaymentYbSettlementItemCommand();
            item.setSetlId(row.getSettlementId().trim());
            if (StringUtils.isNotBlank(row.getChargeBatchNo())) {
                item.setChrgBchno(row.getChargeBatchNo().trim());
            }
            if (StringUtils.isNotBlank(row.getMdtrtId())) {
                item.setMdtrtId(row.getMdtrtId().trim());
            }
            item.setSettlementRecordId(row.getSettlementRecordId());
            item.setAccountId(row.getAccountId());
            item.setStatus(PaymentYbSettlementStatus.CONFIRMED);
            items.add(item);
        }
        return items;
    }

    /**
     * 将 2207 统筹/个账分项合并进 SettleOutpatientPaymentCommand，并按 Σ psn_cash 对齐患者自付与头表。
     *
     * @param settleCommand     结算命令
     * @param ybConfirmResults  2207 结果
     * @param draftDetails      草稿自付明细（取 accountCode / 回落 accountId）
     * @param chargeItemTotal   挂号收费项总价
     */
    private void mergeRegisterFeeInsurancePaymentDetails(
        SettleOutpatientPaymentCommand settleCommand,
        List<OutpatientChargeConfirmExtensionOut> ybConfirmResults,
        List<PaymentRecDetailQueryModel> draftDetails,
        BigDecimal chargeItemTotal) {
        if (ybConfirmResults == null || ybConfirmResults.isEmpty()) {
            return;
        }
        Map<Long, String> accountCodeById = new HashMap<>();
        Long fallbackAccountId = null;
        for (PaymentRecDetailQueryModel draftRow : draftDetails) {
            if (draftRow == null) {
                continue;
            }
            if (draftRow.getAccountId() != null && StringUtils.isNotBlank(draftRow.getAccountCode())) {
                accountCodeById.put(draftRow.getAccountId(), draftRow.getAccountCode().trim());
            }
            if (fallbackAccountId == null && draftRow.getAccountId() != null) {
                fallbackAccountId = draftRow.getAccountId();
            }
        }
        List<PaymentDetailItemCommand> insuranceLines = new ArrayList<>();
        LocalDateTime billDate = settleCommand.getBillDate() != null
            ? settleCommand.getBillDate()
            : LocalDateTime.now();
        BigDecimal sumPsnCash = BigDecimal.ZERO;
        BigDecimal sumMedfee = BigDecimal.ZERO;
        for (OutpatientChargeConfirmExtensionOut ybResult : ybConfirmResults) {
            if (ybResult == null) {
                continue;
            }
            if (ybResult.getPsnCashPay() != null) {
                sumPsnCash = sumPsnCash.add(ybResult.getPsnCashPay());
            }
            if (ybResult.getMedfeeSumamt() != null) {
                sumMedfee = sumMedfee.add(ybResult.getMedfeeSumamt());
            }
            Long accountId = ybResult.getAccountId();
            if (accountId == null) {
                accountId = fallbackAccountId;
            }
            String accountCode = accountCodeById.get(accountId);
            String setlId = ybResult.getSettlementId();
            this.appendRegisterFeeInsurancePaymentLine(
                insuranceLines, PaymentChannel.INSURANCE_FUND.getCode(),
                ybResult.getFundPaySumamt(), accountId, accountCode, setlId, billDate);
            this.appendRegisterFeeInsurancePaymentLine(
                insuranceLines, PaymentChannel.INSURANCE_ACCT.getCode(),
                ybResult.getAcctPay(), accountId, accountCode, setlId, billDate);
        }
        List<PaymentDetailItemCommand> merged = new ArrayList<>(insuranceLines);
        if (settleCommand.getPayReceiptDetails() != null) {
            merged.addAll(settleCommand.getPayReceiptDetails());
        }
        BigDecimal targetPatientCash = InsuranceSettlementCashAlignSupport.resolveTargetPatientCash(
            sumPsnCash, sumMedfee, chargeItemTotal);
        String fallbackAccountCode = null;
        if (fallbackAccountId != null) {
            fallbackAccountCode = accountCodeById.get(fallbackAccountId);
        }
        InsuranceSettlementCashAlignSupport.alignPatientPayLines(
            merged,
            targetPatientCash,
            fallbackAccountId,
            fallbackAccountCode,
            billDate,
            settleCommand.getOutcomeEnum());
        // Gate：挂号医保落账与门诊收费同口径——自付=权威现金，全明细=收费项合计
        this.assertRegisterInsuranceSettleDetailAligned(
            merged, targetPatientCash, chargeItemTotal);
        settleCommand.setPayReceiptDetails(merged);
        settleCommand.setDisplayAmount(targetPatientCash);
        settleCommand.setTenderedAmount(targetPatientCash);
    }

    /**
     * 挂号医保落账勾稽（与门诊收费 {@code assertInsuranceSettleDetailAligned} 同精神）。
     *
     * @param merged            已对齐明细
     * @param targetPatientCash 权威个人现金
     * @param chargeItemTotal   挂号收费项合计
     * @throws BusinessException 勾稽失败
     */
    private void assertRegisterInsuranceSettleDetailAligned(
        List<PaymentDetailItemCommand> merged,
        BigDecimal targetPatientCash,
        BigDecimal chargeItemTotal) {
        BigDecimal patientSum = InsuranceSettlementCashAlignSupport.sumPatientLineAmounts(merged);
        BigDecimal targetCash;
        if (targetPatientCash == null) {
            targetCash = MonetaryAmountSupport.scaleSettle(BigDecimal.ZERO);
        } else {
            targetCash = MonetaryAmountSupport.scaleSettle(targetPatientCash);
        }
        // 自付渠道合计须等于权威现金，否则号费支付明细会污染日结渠道
        if (patientSum.compareTo(targetCash) != 0) {
            throw new BusinessException(
                ErrorCodeConstants.PAYMENT_RECONCILIATION_RULE_CONFLICT,
                "挂号医保落账自付渠道合计与权威个人现金不一致");
        }
        // 全明细合计须等于收费项合计
        if (!InsuranceSettlementCashAlignSupport.isDetailSumMatchedChargeTotal(merged, chargeItemTotal)) {
            throw new BusinessException(
                ErrorCodeConstants.PAYMENT_RECONCILIATION_RULE_CONFLICT,
                "挂号医保落账明细合计与收费项不一致");
        }
    }

    /**
     * 追加单条医保支付子明细（金额≤0 跳过；缺 accountId 抛 PAY-VAL-002）。
     *
     * @param target      目标明细列表
     * @param payEnum     支付渠道 code
     * @param amount      金额
     * @param accountId   账户 id
     * @param accountCode 账户 busNo
     * @param setlId      医保 setl_id
     * @param billDate    交易时间
     * @throws BusinessException 缺 accountId
     */
    private void appendRegisterFeeInsurancePaymentLine(
        List<PaymentDetailItemCommand> target,
        String payEnum,
        BigDecimal amount,
        Long accountId,
        String accountCode,
        String setlId,
        LocalDateTime billDate) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        if (accountId == null) {
            throw new BusinessException("PAY-VAL-002", "医保结算明细缺少账户标识");
        }
        PaymentDetailItemCommand line = new PaymentDetailItemCommand();
        line.setPayEnum(payEnum);
        line.setAccountId(accountId);
        line.setAccountCode(accountCode);
        line.setAmount(amount);
        line.setChargeAmount(amount);
        line.setPayTransNo(setlId);
        line.setPayTransDate(billDate);
        line.setResultEnum(PaymentStatus.SUCCESS.getCode());
        target.add(line);
    }

    /**
     * 挂号列表行回补合同名称、收费人、医保平台快照与参保地字典展示名。
     *
     * <p>费用性质列取就诊<strong>优先账户合同</strong>（有医保户优先医保合同名），与医生站卡片/挂号明细报表身份口径一致；
     * 不跟挂号费账单挂账账户或支付渠道。</p>
     *
     * @param records                 列表 DTO
     * @param paymentDraftByEncounter 待挂号 encounter → 支付草稿 id
     */
    private void enrichClinicRegisterListRecords(
        List<ClinicRegisterItemDto> records,
        Map<Long, Long> paymentDraftByEncounter) {
        if (records == null || records.isEmpty()) {
            return;
        }
        List<Long> encounterIds = new ArrayList<>();
        List<Long> successPaymentIds = new ArrayList<>();
        for (ClinicRegisterItemDto row : records) {
            if (row == null) {
                continue;
            }
            if (row.getEncounterId() != null) {
                encounterIds.add(row.getEncounterId());
            }
            // 已缴费行才有成功支付流水，用于回显收费人
            if (row.getPaymentId() != null) {
                successPaymentIds.add(row.getPaymentId());
            }
        }
        if (encounterIds.isEmpty()) {
            return;
        }
        // Pre：成功支付 enterer_id → 医务人员姓名（与退费/费用明细收费人回显同源）
        Map<Long, Long> entererIdByPaymentId =
            paymentReconciliationQueryService.mapEntererIdByPaymentIds(successPaymentIds);
        LinkedHashSet<Long> entererIds = new LinkedHashSet<>();
        for (Long entererId : entererIdByPaymentId.values()) {
            if (entererId != null) {
                entererIds.add(entererId);
            }
        }
        Map<Long, String> cashierNameByEntererId =
            practitionerQueryService.mapDisplayNameByEntererIds(entererIds);
        // Pre：费用性质 = 挂号身份（优先医保账户），非个人现金户/账单挂账
        Map<Long, String> contractNameByEncounter =
            encounterContractDisplayAssembler.mapContractDisplayByEncounterIds(encounterIds);
        Map<Long, OutpatientRegistrationPlatformQueryExtensionOut> platformByEncounter = new HashMap<>();
        if (outpatientRegistrationInsuranceExtension.isEnabled()) {
            for (OutpatientRegistrationPlatformQueryExtensionOut snap :
                outpatientRegistrationInsuranceExtension.findPlatformSnapshots(
                    new OutpatientRegistrationPlatformQueryExtensionIn()
                        .setEncounterIds(encounterIds)
                        .setPaymentDraftIdByEncounter(paymentDraftByEncounter))) {
                if (snap != null && snap.getEncounterId() != null) {
                    platformByEncounter.put(snap.getEncounterId(), snap);
                }
            }
        }
        for (ClinicRegisterItemDto row : records) {
            if (row == null || row.getEncounterId() == null) {
                continue;
            }
            String contractName = contractNameByEncounter.get(row.getEncounterId());
            if (StringUtils.isNotBlank(contractName)) {
                row.setContractName(contractName);
            }
            if (row.getPaymentId() != null) {
                Long entererId = entererIdByPaymentId.get(row.getPaymentId());
                if (entererId != null) {
                    row.setCashierName(cashierNameByEntererId.get(entererId));
                }
            }
            // 退号完成：库内 CANCELLED，窗口文案统一「退号」，不展示「已取消」
            if (EncounterStatus.CANCELLED.getCode().equals(row.getStatusEnum())) {
                row.setStatusText(OutpatientRegistrationConstants.CLINIC_REGISTER_STATUS_TEXT_REFUNDED);
            }
            OutpatientRegistrationPlatformQueryExtensionOut snap = platformByEncounter.get(row.getEncounterId());
            if (snap != null) {
                row.setInsuplcAdmdvs(snap.getInsuplcAdmdvs());
                row.setInsurancePlatformRegistered(snap.getPlatformRegistered());
                row.setInsurancePreSettled(snap.getInsurancePreSettled());
                row.setYbFundPaySumamt(snap.getYbFundPaySumamt());
                row.setYbAcctPay(snap.getYbAcctPay());
                row.setYbPsnCashPay(snap.getYbPsnCashPay());
            }
        }
        if (platformDictDisplayExtension.isEnabled()) {
            List<String> admdvsCodes = new ArrayList<>();
            for (ClinicRegisterItemDto row : records) {
                if (row != null && StringUtils.isNotBlank(row.getInsuplcAdmdvs())) {
                    admdvsCodes.add(row.getInsuplcAdmdvs().trim());
                }
            }
            if (!admdvsCodes.isEmpty()) {
                Map<String, String> labelByCode =
                    platformDictDisplayExtension.lookupInsuplcAdmdvsLabels(admdvsCodes);
                for (ClinicRegisterItemDto row : records) {
                    if (row == null || StringUtils.isBlank(row.getInsuplcAdmdvs())) {
                        continue;
                    }
                    String label = labelByCode.get(row.getInsuplcAdmdvs().trim());
                    if (StringUtils.isNotBlank(label)) {
                        row.setInsuplcAdmdvsName(label.trim());
                    }
                }
            }
        }
    }

    /**
     * 由挂号账户 contractNo 解析 fin_contract 主键。
     *
     * @param account 挂号账户
     * @return 合同 id；无 contractNo 时 null
     */
    private Long resolveContractIdFromAccount(Account account) {
        if (account == null || StringUtils.isBlank(account.getContractNo())) {
            return null;
        }
        return contractQueryService.findOptionalContractEntityByBusNo(account.getContractNo().trim())
            .map(Contract::getId)
            .orElse(null);
    }

    /**
     * 挂号费医保预结算个账使用标志：false→{@code "0"}，否则 {@code "1"}。
     *
     * @param ybUseAcctPay 是否使用个账
     * @return acct_used_flag 传平台值
     */
    private String resolveRegisterFeeAcctUsedFlag(Boolean ybUseAcctPay) {
        if (Boolean.FALSE.equals(ybUseAcctPay)) {
            return "0";
        }
        return "1";
    }

    /**
     * 判断异常链是否表明医保就诊凭证（含电子凭证 ecToken）已失效，需重新展码并刷新预结算。
     *
     * @param ex 2207 或平台调用抛出的异常
     * @return 凭证过期类文案时为 true
     */
    private boolean isInsuranceCredentialExpired(Throwable ex) {
        Throwable cursor = ex;
        while (cursor != null) {
            String msg = cursor.getMessage();
            if (StringUtils.isNotBlank(msg)) {
                String normalized = msg.trim();
                if (normalized.contains("凭证已失效")
                    || normalized.contains("凭证失效")
                    || normalized.contains("凭证过期")
                    || (normalized.contains("电子凭证")
                        && (normalized.contains("失效")
                            || normalized.contains("过期")
                            || normalized.contains("无效")))
                    || normalized.contains("重新展码")
                    || normalized.contains("重新获取")
                    || normalized.contains("ecToken")
                    || normalized.contains("mdtrt_cert")) {
                    return true;
                }
            }
            cursor = cursor.getCause();
        }
        return false;
    }
}
