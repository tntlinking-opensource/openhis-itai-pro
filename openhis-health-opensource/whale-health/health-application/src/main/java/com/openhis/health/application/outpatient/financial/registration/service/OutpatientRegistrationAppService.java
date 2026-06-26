package com.openhis.health.application.outpatient.financial.registration.service;

import com.openhis.health.application.outpatient.financial.registration.dto.*;
import com.openhis.health.application.outpatient.financial.registration.support.RegisterBenefitAreaHintSupport;
import com.openhis.health.application.outpatient.financial.registration.support.RegisterContractMatchSupport;
import com.openhis.health.application.outpatient.financial.registration.support.RegistrationReceiptPrintAssembler;
import com.openhis.health.application.outpatient.financial.registration.mapper.OutpatientRegistrationMapper;
import com.openhis.health.application.outpatient.financial.registration.request.*;
import com.openhis.health.application.outpatient.financial.registration.response.*;
import com.openhis.health.domain.administration.account.command.OpenEncounterAccountsCommand;
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
import com.openhis.health.domain.administration.patient.queries.PatientPagedQuery;
import com.openhis.health.domain.administration.patient.queries.model.PatientCoreQueryModel;
import com.openhis.health.domain.administration.patient.queries.model.PatientDetailQueryModel;
import com.openhis.health.domain.administration.patient.queries.model.PatientPageItemQueryModel;
import com.openhis.health.domain.administration.patient.queries.model.PatientStudentProfileQueryModel;
import com.openhis.health.domain.administration.patient.queries.model.PatientStudentQueryModel;
import com.openhis.health.domain.administration.patient.queries.model.StudentPublicExpenseEligibilityQueryModel;
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
import com.openhis.health.domain.financial.paymentreconciliation.command.CompleteRegistrationCancelRefundCommand;
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
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.constants.WhalePlatformRoleCodeConstants;
import com.openhis.health.domain.share.enums.*;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.health.domain.share.utils.InsuranceContractCategorySupport;
import com.openhis.health.domain.terminology.service.CodeSystemQueryService;
import com.openhis.health.module.common.validator.CurrentManagedOrganizationIdsResolver;
import com.openhis.health.module.common.validator.CurrentPractitionerResolver;
import com.openhis.whale.core.application.session.CurrentOperatorResolver;
import com.openhis.health.module.common.validator.PageWindowGate;
import com.openhis.health.module.common.assembler.financial.PaymentChannelOptionProvider;
import com.openhis.health.module.common.validator.PaymentChannelGate;
import com.openhis.health.module.common.support.ThirdPartyPaymentRefundSupport;
import com.openhis.health.module.common.support.ThirdPartyPaymentSettleSupport;
import com.openhis.health.module.querysupport.administration.PractitionerRoleScopeLoader;
import com.openhis.health.module.querysupport.encounter.EncounterContextFacade;
import com.openhis.health.integration.support.insurance.outpatient.OutpatientInsuranceSettlementCompensation;
import com.openhis.health.integration.support.einvoice.BossElectronicInvoiceIntegrationSupport;
import com.openhis.health.integration.insurance.outpatient.OutpatientChargeInsuranceExtension;
import com.openhis.health.integration.insurance.outpatient.OutpatientRegistrationInsuranceExtension;
import com.openhis.health.integration.insurance.platformdict.InsurancePlatformDictDisplayExtension;
import com.openhis.health.integration.insurance.outpatient.command.OutpatientChargeConfirmCommand;
import com.openhis.health.integration.insurance.outpatient.command.OutpatientChargePreSettleCommand;
import com.openhis.health.integration.insurance.outpatient.command.OutpatientChargeRevokeFeeDetailCommand;
import com.openhis.health.integration.insurance.outpatient.command.OutpatientRegistrationPlatformCommand;
import com.openhis.health.integration.insurance.outpatient.result.OutpatientRegistrationPlatformSnapshot;
import com.openhis.health.integration.insurance.outpatient.result.OutpatientChargeConfirmResult;
import com.openhis.health.integration.insurance.outpatient.result.OutpatientChargePreSettleResult;
import com.openhis.health.integration.insurance.outpatient.result.OutpatientChargePreSettleSnapshot;
import com.openhis.health.integration.insurance.benefit.InsuranceRegisterBenefitQueryExtension;
import com.openhis.health.integration.insurance.benefit.command.InsuranceCumulativeQueryCommand;
import com.openhis.health.integration.insurance.benefit.command.InsuranceOpspFilingQueryCommand;
import com.openhis.health.integration.insurance.benefit.command.InsurancePersonQueryCommand;
import com.openhis.health.integration.insurance.benefit.command.InsuranceTreatmentCheckCommand;
import com.openhis.health.integration.insurance.benefit.result.InsuranceCumulativeLineSnapshot;
import com.openhis.health.integration.insurance.benefit.result.InsuranceCumulativeQueryResult;
import com.openhis.health.integration.insurance.benefit.result.InsuranceCoverageLineSnapshot;
import com.openhis.health.integration.insurance.benefit.result.InsuranceOpspFilingLineSnapshot;
import com.openhis.health.integration.insurance.benefit.result.InsuranceOpspFilingQueryResult;
import com.openhis.health.integration.insurance.benefit.result.InsurancePersonQueryResult;
import com.openhis.health.integration.insurance.benefit.result.InsuranceTreatmentCheckResult;
import com.openhis.health.integration.insurance.benefit.result.InsuranceTreatmentLineSnapshot;
import com.openhis.health.integration.booking.command.OutpatientBookingLookupCommand;
import com.openhis.health.integration.booking.command.OutpatientBookingRegistrationValidateCommand;
import com.openhis.health.integration.booking.command.OutpatientRegistrationCancelCommand;
import com.openhis.health.integration.booking.command.OutpatientWalkInSlotAllocateCommand;
import com.openhis.health.integration.booking.outpatient.OutpatientBookingQueryExtension;
import com.openhis.health.integration.booking.outpatient.OutpatientBookingRegistrationExtension;
import com.openhis.health.integration.booking.result.BookableServiceOptionSnapshot;
import com.openhis.health.integration.booking.result.BookableSlotHintSnapshot;
import com.openhis.health.integration.booking.result.OutpatientBookingSnapshot;
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
 *   <li>Cmd：就诊开立（PLANNED 态）、账户开立（个人现金账户）、挂号费收费项创建、支付草稿生成、
 *          正式结算后状态迁移（REGISTERED 态）、发票开具。</li>
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
    /** 费用性质查询服务。 */
    private final ContractQueryService contractQueryService;
    /** 诊疗服务查询服务。 */
    private final HealthcareServiceQueryService healthcareServiceQueryService;
    /** 就诊写侧领域服务。 */
    private final EncounterDomainService encounterDomainService;
    /** 就诊读侧查询服务。 */
    private final EncounterQueryService encounterQueryService;
    private final EncounterContextFacade encounterContextFacade;
    /** 账户写侧领域服务。 */
    private final AccountDomainService accountDomainService;
    /** 账户读侧查询服务。 */
    private final AccountQueryService accountQueryService;
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

    private final ThirdPartyPaymentSettleSupport thirdPartyPaymentSettleSupport;

    private final ThirdPartyPaymentRefundSupport thirdPartyPaymentRefundSupport;

    /** 门诊挂号第三方平台登记扩展。 */
    private final OutpatientRegistrationInsuranceExtension outpatientRegistrationInsuranceExtension;

    /** 1901 平台字典展示（参保地 label）。 */
    private final InsurancePlatformDictDisplayExtension platformDictDisplayExtension;

    /** 门诊收费第三方结算扩展（挂号费医保 2203→2207）。 */
    private final OutpatientChargeInsuranceExtension chargeInsuranceExtension;

    /** 门诊挂号待遇第三方查询扩展（1101/2001/5301/5206）。 */
    private final InsuranceRegisterBenefitQueryExtension registerBenefitQueryExtension;

    /** 参保地与费用性质医保通道匹配。 */
    private final RegisterContractMatchSupport registerContractMatchSupport;

    private final OutpatientInsuranceSettlementCompensation outpatientInsuranceSettlementCompensation;

    /** 门诊挂号预约只读 Extension（IF-REG-12～14）。 */
    private final OutpatientBookingQueryExtension outpatientBookingQueryExtension;

    /** 门诊挂号预约写侧衔接 Extension（IF-REG-07/10、现场占号）。 */
    private final OutpatientBookingRegistrationExtension outpatientBookingRegistrationExtension;

    private final BossElectronicInvoiceIntegrationSupport bossElectronicInvoiceIntegrationSupport;

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
        healthcareServices = this.narrowHealthcareServicesForBooking(
            healthcareServices, request.getOrganizationId(), request.getPractitionerId(), request.getRegistrationDate());
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
            String admVsFallback = contractQueryService.findOptionalContractEntityById(contract.getId())
                .map(Contract::getAdmVs)
                .orElse(null);
            Boolean ybFlag = contractQueryService.findOptionalContractEntityById(contract.getId())
                .map(Contract::getYbFlag)
                .orElse(null);
            ContractInsuranceProfileDto profile = new ContractInsuranceProfileDto()
                .setContractId(String.valueOf(contract.getId()))
                .setCategoryCode(contract.getCategoryEnum())
                .setBusNo(contract.getBusNo())
                .setTreatmentAreaCode(
                    registerContractMatchSupport.resolveTreatmentAreaCode(contract.getId(), admVsFallback))
                .setYbFlag(ybFlag);
            contractInsuranceProfileById.put(String.valueOf(contract.getId()), profile);
        }
        response.setContractInsuranceProfileById(contractInsuranceProfileById);
        response.setPaymentChannelOptions(paymentChannelOptionProvider.loadUserSelectableOptions());
        return response;
    }

    /**
     * 加载挂号待遇预览（医保 1101 / 学生公费学籍）。
     */
    public LoadRegisterBenefitPreviewResponse loadRegisterBenefitPreview(LoadRegisterBenefitPreviewRequest request) {
        if (request.getContractId() == null) {
            throw new BusinessException("REG-VAL-001", "费用性质不能为空");
        }
        ContractDetailQueryModel contract = contractQueryService.findContractById(request.getContractId());
        if (contract == null) {
            throw new BusinessException("REG-BIZ-404", "主数据失效，请刷新");
        }
        LoadRegisterBenefitPreviewResponse response = new LoadRegisterBenefitPreviewResponse();
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
        // 读取可挂号科室并映射为下拉结构。
        List<Organization> organizations = organizationQueryService.listOrganizationsForOutpatientRegistration();
        return outpatientRegistrationMapper.toListDepartmentsResponse(outpatientRegistrationMapper.toOrganizationOptionList(organizations));
    }

    /**
     * 查询科室下可挂号医生列表。
     *
     * @param request 医生查询请求
     * @return 医生下拉结果
     */
    public ListPractitionersResponse listPractitioners(ListPractitionersRequest request) {
        List<Practitioner> practitioners = practitionerRoleScopeLoader.listActivePractitionersForOutpatientRegistration(
            WhalePlatformRoleCodeConstants.ROLE_DOCTOR, request.getOrganizationId());
        return outpatientRegistrationMapper.toListPractitionersResponse(outpatientRegistrationMapper.toPractitionerOptionList(practitioners));
    }

    /**
     * IF-REG-12：挂号台加载有效门诊预约。
     */
    public LoadAppointmentForRegisterResponse loadAppointmentForRegister(LoadAppointmentForRegisterRequest request) {
        if (!outpatientBookingQueryExtension.isEnabled()) {
            throw new BusinessException("BKG-REG-404", "预约模块未启用");
        }
        if (request == null) {
            throw new BusinessException("REG-VAL-001", "查询参数不完整");
        }
        boolean hasBusNo = StringUtils.isNotBlank(request.getAppointmentBusNo());
        if (!hasBusNo && request.getPatientId() == null) {
            throw new BusinessException("REG-VAL-001", "patientId 与 appointmentBusNo 至少传一个");
        }
        OutpatientBookingLookupCommand lookupQuery = new OutpatientBookingLookupCommand();
        lookupQuery.setPatientId(request.getPatientId());
        lookupQuery.setAppointmentBusNo(request.getAppointmentBusNo());
        LocalDate registrationDate;
        if (request.getRegistrationDate() != null) {
            registrationDate = request.getRegistrationDate();
        } else {
            registrationDate = LocalDate.now();
        }
        lookupQuery.setRegistrationDate(registrationDate);
        OutpatientBookingSnapshot snapshot = outpatientBookingQueryExtension.findActiveOutpatientBooking(lookupQuery)
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
        List<BookableSlotHintSnapshot> hints = outpatientBookingQueryExtension.listBookableSlots(
            request.getOrganizationId(),
            request.getPractitionerId(),
            healthcareServiceId,
            registrationDate);
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
        // Gate：门控挂号关键要素，避免后续编排出现空主键。
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
        // 同患者、同目标科室、当日：凡有效就诊（含诊毕）均占名额，退号后释放（PLANNED 未交费不占名额）。
        // 挂号防重检查：当日同科室已挂号则阻断重复挂号
        OutpatientDuplicateCheckQuery duplicateCheckQuery = new OutpatientDuplicateCheckQuery();
        duplicateCheckQuery.setPatientId(patientId);
        duplicateCheckQuery.setOrganizationId(request.getOrganizationId());
        duplicateCheckQuery.setRegistrationDate(occurredAt.toLocalDate());
        OutpatientDuplicateCheckQueryModel duplicateCheckQueryModel = encounterQueryService.checkOutpatientDuplicateRegistration(duplicateCheckQuery);
        if (Boolean.TRUE.equals(duplicateCheckQueryModel.getDuplicated())) {
            throw new BusinessException("REG-BIZ-409", "本科室当日已有门诊挂号记录（含诊毕），须退号后方可再挂");
        }
        // 装载结算所需主数据，主数据缺失时拒绝继续。
        ContractDetailQueryModel contract = contractQueryService.findContractById(request.getContractId());
        Organization organization = organizationQueryService.loadOrganizationByIdForDisplay(request.getOrganizationId());
        HealthcareService healthcareService = healthcareServiceQueryService.loadHealthcareServiceById(request.getServiceTypeId());
        if (contract == null || organization == null || healthcareService == null) {
            throw new BusinessException("REG-BIZ-404", "主数据失效，请刷新");
        }
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
        }
        String registrationPractitionerDisplayName = null;
        if (registrationPractitioner != null) {
            registrationPractitionerDisplayName = registrationPractitioner.getName();
        }

        // 挂号员与患者快照：投影依赖事件携带展示字段，须在此拉齐主档后再组装命令。
        Long registrarId = currentPractitionerResolver.requireOperatorId("REG-VAL-001", "当前登录用户不能为空");
        Long walkInSlotId = null;
        if (request.getAppointmentId() == null && outpatientBookingRegistrationExtension.isEnabled()) {
            walkInSlotId = this.tryAllocateWalkInSlot(request, patientId, registrarId, occurredAt.toLocalDate());
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
        this.runInsuranceRegistrationIfNeeded(request, encounterId);
        // 先开立默认个人现金账户（AccountTypeCode.PERSONAL_CASH_ACCOUNT），挂号费与后续医嘱费用草稿同锚；已存在则幂等跳过。
        Account registrationAccount = accountQueryService.getRegistrationAccount(patientId, encounterId);
        if (registrationAccount == null) {
            OpenEncounterAccountsCommand openAccountCommand =
                outpatientRegistrationMapper.toOpenEncounterAccountsCommandForPreRegister(
                    request, encounterId, contractBusNo);
            accountDomainService.openEncounterAccounts(openAccountCommand);
            registrationAccount = accountQueryService.getRegistrationAccount(patientId, encounterId);
        } else if (registrationAccount.getId() != null) {
            accountDomainService.backfillContractNoIfMissing(registrationAccount.getId(), contractBusNo);
            registrationAccount = accountQueryService.getRegistrationAccount(patientId, encounterId);
        }
        if (registrationAccount == null || registrationAccount.getId() == null) {
            throw new BusinessException("REG-BIZ-500", "开立就诊自费账户失败，无法生成收费明细");
        }
        // 在就诊锚点与账户锚点齐备后创建挂号收费项并预结算，使 RegistrationChargeItemCreatedEvent 携带 encounterId/totalPrice。
        CreateChargeItemCommand createChargeItemCommand = outpatientRegistrationMapper.toCreateChargeItemCommand(
            request, saleLine, encounterId, occurredAt, registrationAccount.getId(), organization.getId());
        createChargeItemCommand.setOperatorId(registrarId);
        Long chargeItemId = chargeItemDomainService.createRegistrationChargeItem(createChargeItemCommand);
        BatchChargeItemIdsCommand preSettleCommand = outpatientRegistrationMapper.toSingleChargeItemBatchCommand(chargeItemId);
        chargeItemDomainService.preSettleChargeItems(preSettleCommand);
        // 以落库后的 ChargeItem.totalPrice 为支付草稿与响应总额的唯一口径，避免与定价定义二次取值不一致。
        ChargeItemQueryCommand reloadChargeCmd = new ChargeItemQueryCommand();
        reloadChargeCmd.setIds(List.of(chargeItemId));
        List<ChargeItem> persistedChargeLines = chargeItemQueryService.loadChargeItemsByIds(reloadChargeCmd);
        if (persistedChargeLines == null || persistedChargeLines.isEmpty()) {
            throw new BusinessException("CHARGE_ITEM_002", "挂号收费明细落库后读取失败，请重试预结算");
        }
        ChargeItem persistedChargeLine = persistedChargeLines.get(0);
        BigDecimal chargeItemAmount = persistedChargeLine.getTotalPrice();
        if (chargeItemAmount == null) {
            chargeItemAmount = BigDecimal.ZERO;
        }
        // 使用就诊主键创建支付草稿，避免 payment.encounter_id 为空触发数据库约束异常。
        List<PaymentDetailItemCommand> paymentDetails = new ArrayList<>();
        CreatePaymentDraftFromPreSettleCommand paymentDraftCommand = outpatientRegistrationMapper
            .toCreatePaymentDraftFromPreSettleCommand(
                request, encounterId, chargeItemId, chargeItemAmount, paymentDetails, contractBusNo);
        // 支付草稿经办人取当前登录用户，避免 enterer_id 为空。
        paymentDraftCommand.setEntererId(
            currentPractitionerResolver.requireOperatorId("REG-VAL-001", "当前登录用户不能为空"));
        Long paymentDraftId = paymentReconciliationDomainService.createPaymentDraftFromPreSettle(paymentDraftCommand);
        List<ChargeItemPreviewDto> previews = outpatientRegistrationMapper.toChargeItemPreviewDtoListBySaleLine(saleLine);
        Long responseAppointmentId = request.getAppointmentId();
        Long responseSlotId = walkInSlotId;
        if (responseAppointmentId != null && outpatientBookingQueryExtension.isEnabled()) {
            Optional<OutpatientBookingSnapshot> bookingSnapshot =
                outpatientBookingQueryExtension.loadOutpatientBookingById(responseAppointmentId);
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
            request, paymentDraftId, chargeItemId, encounterId, registrationAccount, response);
        return response;
    }

    /** 预挂号阶段：医保费用性质时调 Extension 2201 平台登记。 */
    private void runInsuranceRegistrationIfNeeded(PreRegisterSettlementRequest request, Long encounterId) {
        if (!outpatientRegistrationInsuranceExtension.isEnabled()) {
            return;
        }
        if (request.getContractId() == null
            || !outpatientRegistrationInsuranceExtension.supportsThirdPartyContract(request.getContractId())) {
            return;
        }
        if (StringUtils.isBlank(request.getPsnNo())) {
            throw new BusinessException("REG-VAL-002", "医保挂号须先读卡获取人员编号");
        }
        OutpatientRegistrationPlatformCommand command = new OutpatientRegistrationPlatformCommand();
        command.setEncounterId(encounterId);
        command.setContractId(request.getContractId());
        command.setPayerCertType(request.getYbMediumType());
        command.setPayerCertNo(request.getYbCardInfo());
        command.setInsuredPersonNo(request.getPsnNo());
        command.setInsuplcAdmdvs(request.getInsuplcAdmdvs());
        command.setInsutype(request.getInsutype());
        command.setMedType(request.getMedType());
        // Cmd：Extension → 2201（与挂号费 2206 分离）
        outpatientRegistrationInsuranceExtension.ensurePlatformRegistration(command);
    }

    private void applyRegisterFeeInsurancePreSettleIfNeeded(
        PreRegisterSettlementRequest request,
        Long paymentDraftId,
        Long chargeItemId,
        Long encounterId,
        Account registrationAccount,
        PreRegisterSettlementResponse response) {
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
        String medType = YbMedType.GENERAL_OUTPATIENT.getCode();
        OutpatientChargePreSettleCommand command = new OutpatientChargePreSettleCommand();
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
        // Cmd：挂号费走 Extension 2204→2206（非 RegistrationExtension）
        OutpatientChargePreSettleResult ybResult = chargeInsuranceExtension.preSettle(command);
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
        // 第一步：门控缴费关键参数，缺失任一参数均禁止进入结算流程。
        boolean missingRequiredParam = request.getPaymentDraftId() == null
            || request.getPaymentDetails() == null;
        if (missingRequiredParam) {
            throw new BusinessException("PAY-VAL-001", "结算参数不完整");
        }
        if (request.getPaymentDetails().isEmpty()) {
            request.setPaymentDetails(new ArrayList<>());
        }

        // 第二步：读取预结算支付草稿，并确保就诊账户已开立（预结算已开户时幂等跳过）。
        // 草稿不存在或状态异常时抛出业务异常
        PaymentReconciliationQueryModel paymentDraft = paymentReconciliationQueryService.mustGetPayment(request.getPaymentDraftId());
        Account paymentAccount = accountQueryService.getRegistrationAccount(paymentDraft.getPatientId(), paymentDraft.getEncounterId());
        if (paymentAccount == null) {
            OpenEncounterAccountsCommand accountCommand = outpatientRegistrationMapper.toOpenEncounterAccountsCommand(paymentDraft);
            accountDomainService.openEncounterAccounts(accountCommand);
            paymentAccount = accountQueryService.getRegistrationAccount(paymentDraft.getPatientId(), paymentDraft.getEncounterId());
        } else if (paymentAccount.getId() != null && StringUtils.isNotBlank(paymentDraft.getContractNo())) {
            accountDomainService.backfillContractNoIfMissing(paymentAccount.getId(), paymentDraft.getContractNo());
            paymentAccount = accountQueryService.getRegistrationAccount(paymentDraft.getPatientId(), paymentDraft.getEncounterId());
        }
        if (paymentAccount == null || paymentAccount.getId() == null) {
            throw new BusinessException("PAY-BIZ-404", "未找到可用结算账户");
        }
        for (PaymentDetailDto detail : request.getPaymentDetails()) {
            detail.setPayChannelCode(paymentChannelGate.requireUserSelectablePayChannelCode(
                detail.getPayChannelCode(),
                "PAY-VAL-001",
                "支付渠道不合法"));
            detail.setAccountId(paymentAccount.getId());
            detail.setAccountCode(paymentAccount.getBusNo());
        }

        // 第三步：复用预结算已创建的挂号收费明细，不再重复创建；支付头金额以草稿为准并与明细金额交叉校验。
        ChargeItem registrationChargeLine = this.loadRegistrationChargeItemForPayRegisterFee(paymentDraft);
        Long chargeItemId = registrationChargeLine.getId();
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
        List<OutpatientChargeConfirmResult> ybConfirmResults =
            this.collectRegisterFeeInsuranceConfirmResults(request, paymentDraft);
        SettleOutpatientPaymentCommand settleOutpatientPaymentCommand =
            outpatientRegistrationMapper.toSettleOutpatientPaymentCommand(request);
        settleOutpatientPaymentCommand.setYbSettlements(this.buildRegisterFeeYbSettlementItems(ybConfirmResults));
        this.mergeRegisterFeeInsurancePaymentDetails(
            settleOutpatientPaymentCommand, ybConfirmResults, draftDetails);
        Long operatorId = currentPractitionerResolver.requireOperatorId("PAY-VAL-001", "当前登录用户不能为空");
        Long orgId = managedOrganizationIdsResolver.requireSessionOrganizationUnitId("PAY-VAL-001", "当前登录机构不能为空");
        thirdPartyPaymentSettleSupport.applyScanBeforeSettle(
            request.getPaymentDraftId(),
            settleOutpatientPaymentCommand.getPayReceiptDetails(),
            request.getClientScanResult(),
            operatorId,
            orgId);
        Long paymentId = paymentReconciliationDomainService.settleOutpatientPayment(settleOutpatientPaymentCommand);
        BatchChargeItemIdsCommand settleItemsCommand = outpatientRegistrationMapper.toSingleChargeItemBatchCommand(chargeItemId);
        // Cmd：挂号缴费与门诊收银共用 settleChargeItems，须写入收款人供明细列表回显（BR-OPC-016）。
        settleItemsCommand.setOperatorId(operatorId);
        chargeItemDomainService.settleChargeItems(settleItemsCommand);
        // 缴费成功后将就诊状态从 PLANNED 迁移至 REGISTERED，与 EncounterStatus 生命周期对齐。
        encounterDomainService.completeOutpatientRegistration(paymentDraft.getEncounterId());

        if (request.getAppointmentId() != null && outpatientBookingRegistrationExtension.isEnabled()) {
            outpatientBookingRegistrationExtension.linkEncounter(request.getAppointmentId(), paymentDraft.getEncounterId());
            outpatientBookingRegistrationExtension.checkInAfterRegister(request.getAppointmentId(), paymentDraft.getEncounterId());
        }

        // 第四步：支付完成后仅创建电子发票草稿；博思开具由 einvoice-boss 异步处理，不阻塞收费窗。
        CreateDraftInvoiceCommand draftInvoiceCommand = outpatientRegistrationMapper.toCreateDraftInvoiceCommand(paymentDraft, paymentId);
        Long invoiceId = invoiceDomainService.createDraftInvoiceAfterPayment(draftInvoiceCommand);
        bossElectronicInvoiceIntegrationSupport.afterDraftCreated(invoiceId);
        Invoice invoice = invoiceQueryService.getInvoiceById(outpatientRegistrationMapper.toInvoiceQueryCommand(invoiceId));

        // 第五步：回传最新支付信息与发票草稿信息，组装缴费结果。
        return outpatientRegistrationMapper.toPayRegisterFeeResponse(paymentReconciliationQueryService.mustGetPayment(paymentId), invoice);
    }

    /**
     * 分页查询门诊挂号记录。
     *
     * @param request 分页请求
     * @return 挂号分页结果
     * @throws BusinessException 查询参数不完整时抛出
     */
    public PageClinicRegistersResponse pageClinicRegisters(PageClinicRegistersRequest request) {
        if (request.getDateFrom() == null || request.getDateTo() == null) {
            throw new BusinessException("REG-VAL-001", "查询参数不完整");
        }
        pageWindowGate.requireNonNullPageWindow(request, "REG-VAL-001", "查询参数不完整");
        // 查询读模型分页并映射为响应记录。
        ClinicRegisterPageQuery query = outpatientRegistrationMapper.toClinicRegisterPageQuery(request);
        Page<ClinicRegisterQueryModel> page = encounterContextFacade.pageClinicRegister(query);
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
            paymentReconciliationQueryService.findLatestSuccessPaymentIdsByEncounterIds(settledEncounterIds);
        List<ClinicRegisterItemDto> records =
            outpatientRegistrationMapper.toClinicRegisterItemDtoList(
                page, registrationFeeByAnchor, paymentDraftByEncounter, paymentIdByEncounter);
        this.enrichClinicRegisterListRecords(records, paymentDraftByEncounter);
        return outpatientRegistrationMapper.toPageClinicRegistersResponse(page, records);
    }

    /**
     * 撤销待挂号（PLANNED）：作废支付草稿，必要时撤销医保 2206/2202，并将就诊置为 CANCELLED。
     */
    public CancelPlannedRegistrationResponse cancelPlannedRegistration(CancelPlannedRegistrationRequest request) {
        if (request.getEncounterId() == null) {
            throw new BusinessException("CANCEL-VAL-001", "请先选择挂号记录");
        }
        EncounterDetailQueryModel encounter = encounterQueryService.mustGetEncounterOfClass(
            request.getEncounterId(),
            EncounterClass.AMB.getCode(),
            ErrorCodeConstants.ENCOUNTER_INVALID_STATUS,
            "当前就诊不是门诊类别，无法撤销");
        if (!EncounterStatus.PLANNED.getCode().equals(encounter.getStatusEnum())) {
            throw new BusinessException("CANCEL-BIZ-409", "仅待挂号状态可撤销");
        }
        String cancelReason = request.getCancelReason();
        if (cancelReason == null || cancelReason.isBlank()) {
            cancelReason = "窗口撤销待挂号";
        }
        Long paymentDraftId = paymentReconciliationQueryService
            .findDraftPaymentIdsByEncounterIds(List.of(request.getEncounterId()))
            .get(request.getEncounterId());
        Account registrationAccount = accountQueryService.getRegistrationAccount(
            encounter.getPatientId(), request.getEncounterId());
        Long contractId = this.resolveContractIdFromAccount(registrationAccount);
        boolean insuranceRevoked = false;
        if (paymentDraftId != null && chargeInsuranceExtension.isEnabled()) {
            List<OutpatientChargePreSettleSnapshot> active =
                chargeInsuranceExtension.listActivePreSettlesByPaymentDraft(paymentDraftId);
            for (OutpatientChargePreSettleSnapshot row : active) {
                if (row == null || StringUtils.isBlank(row.getChargeBatchNo())) {
                    continue;
                }
                OutpatientChargeRevokeFeeDetailCommand revokeCommand = new OutpatientChargeRevokeFeeDetailCommand();
                revokeCommand.setChargeBatchNo(row.getChargeBatchNo().trim());
                revokeCommand.setPaymentDraftId(paymentDraftId);
                chargeInsuranceExtension.revokeFeeDetails(revokeCommand);
            }
        }
        if (outpatientRegistrationInsuranceExtension.isEnabled()
            && contractId != null
            && outpatientRegistrationInsuranceExtension.supportsThirdPartyContract(contractId)) {
            OutpatientRegistrationPlatformCommand revokeCommand = new OutpatientRegistrationPlatformCommand();
            revokeCommand.setEncounterId(request.getEncounterId());
            revokeCommand.setContractId(contractId);
            outpatientRegistrationInsuranceExtension.revokePlatformRegistration(revokeCommand);
            insuranceRevoked = true;
        }
        if (paymentDraftId != null) {
            AbortPreSettlementBeforePaymentCommand abortCommand = new AbortPreSettlementBeforePaymentCommand();
            abortCommand.setPaymentId(paymentDraftId);
            paymentReconciliationDomainService.abortPreSettlementBeforePayment(abortCommand);
        }
        encounterDomainService.cancelPlannedOutpatientRegistration(request.getEncounterId(), cancelReason);
        return new CancelPlannedRegistrationResponse()
            .setEncounterStatus(EncounterStatus.CANCELLED.getCode())
            .setInsurancePlatformRevoked(insuranceRevoked);
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
        List<OutpatientChargePreSettleSnapshot> active =
            chargeInsuranceExtension.listActivePreSettlesByPaymentDraft(request.getPaymentDraftId());
        OutpatientChargePreSettleSnapshot previous = null;
        if (!active.isEmpty()) {
            previous = active.get(0);
        }
        for (OutpatientChargePreSettleSnapshot row : active) {
            if (row == null || StringUtils.isBlank(row.getChargeBatchNo())) {
                continue;
            }
            OutpatientChargeRevokeFeeDetailCommand revokeCommand = new OutpatientChargeRevokeFeeDetailCommand();
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
        String medType = YbMedType.GENERAL_OUTPATIENT.getCode();
        if (StringUtils.isNotBlank(request.getMedType())) {
            medType = request.getMedType().trim();
        }
        // Cmd：Extension 2204→2206 重算并刷新支付草稿个人现金行
        OutpatientChargePreSettleCommand command = new OutpatientChargePreSettleCommand();
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
        OutpatientChargePreSettleResult ybResult = chargeInsuranceExtension.preSettle(command);
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
        // Asm：返回刷新后分摊供前端更新确认弹窗
        return new RefreshRegisterFeeInsurancePreSettleResponse()
            .setPaymentDraftId(request.getPaymentDraftId())
            .setInsurancePreSettled(Boolean.TRUE)
            .setYbChrgBchno(ybResult.getChargeBatchNo())
            .setYbMedfeeSumamt(ybResult.getMedfeeSumamt())
            .setYbFundPaySumamt(ybResult.getFundPaySumamt())
            .setYbAcctPay(ybResult.getAcctPay())
            .setYbPsnCashPay(ybResult.getPsnCashPay())
            .setTotalAmount(psnCashPay);
    }

    /**
     * 当日挂号统计（不含待收费 PLANNED）。
     *
     * @param request 统计日期，默认当天
     * @return 总数、已退数、有效数
     */
    public GetTodayRegisterSummaryResponse getTodayRegisterSummary(GetTodayRegisterSummaryRequest request) {
        LocalDate date = request != null && request.getDate() != null ? request.getDate() : LocalDate.now();
        LocalDateTime dayStart = date.atStartOfDay();
        LocalDateTime dayEnd = date.atTime(23, 59, 59);

        ClinicRegisterPageQuery totalQuery = new ClinicRegisterPageQuery();
        totalQuery.setStartTime(dayStart);
        totalQuery.setEndTime(dayEnd);
        totalQuery.setExcludeStatusEnums(List.of(EncounterStatus.PLANNED.getCode()));

        ClinicRegisterPageQuery cancelledQuery = new ClinicRegisterPageQuery();
        cancelledQuery.setStartTime(dayStart);
        cancelledQuery.setEndTime(dayEnd);
        cancelledQuery.setStatusEnum(EncounterStatus.CANCELLED.getCode());

        long totalCount = encounterContextFacade.countClinicRegister(totalQuery);
        long cancelledCount = encounterContextFacade.countClinicRegister(cancelledQuery);

        return new GetTodayRegisterSummaryResponse()
            .setDate(date)
            .setTotalCount(totalCount)
            .setCancelledCount(cancelledCount)
            .setActiveCount(Math.max(0L, totalCount - cancelledCount));
    }

    /**
     * 校验当前挂号记录是否允许退号。
     *
     * @param request 退号校验请求
     * @return 退号可行性结果
     * @throws BusinessException 未传就诊 ID 时抛出
     */
    public ValidateCancelableResponse validateCancelable(ValidateCancelableRequest request) {
        // 退号校验必须明确就诊记录。
        if (request.getEncounterId() == null) {
            throw new BusinessException("CANCEL-VAL-001", "请先选择挂号记录");
        }
        // 加载退号判定所需的就诊、支付、收费、票据信息。
        EncounterDetailQueryModel encounter = encounterQueryService.mustGetEncounterOfClass(
            request.getEncounterId(),
            EncounterClass.AMB.getCode(),
            ErrorCodeConstants.ENCOUNTER_INVALID_STATUS,
            "当前就诊不是门诊类别，无法退号");
        List<PaymentReconciliationQueryModel> payments = paymentReconciliationQueryService.findPaySuccessOrRefundingByEncounter(request.getEncounterId());
        ChargeItemQueryCommand chargeItemQueryCommand = new ChargeItemQueryCommand();
        chargeItemQueryCommand.setEncounterId(request.getEncounterId());
        List<ChargeItem> chargeItems = chargeItemQueryService.listChargeItemsByEncounterId(chargeItemQueryCommand);
        Invoice latestInvoice = null;
        if (!payments.isEmpty()) {
            InvoiceQueryCommand queryCommand = new InvoiceQueryCommand();
            queryCommand.setPaymentId(payments.get(0).getId());
            latestInvoice = invoiceQueryService.getLatestIssuedInvoiceByPaymentId(queryCommand);
        }
        // 按就诊状态判断是否允许退号（与 Encounter.assertCancelable 门诊口径一致）。
        EncounterStatus encounterStatus = EnumUtil.fromCode(EncounterStatus.class, encounter.getStatusEnum());
        boolean cancelable = encounterStatus != null
            && (EncounterStatus.REGISTERED.equals(encounterStatus)
                || EncounterStatus.TRIAGED.equals(encounterStatus)
                || EncounterStatus.IN_PROGRESS.equals(encounterStatus)
                || EncounterStatus.DISCHARGED.equals(encounterStatus));
        String reasonCode;
        String reasonText;
        if (cancelable) {
            reasonCode = "OK";
            reasonText = "允许退号";
        } else {
            reasonCode = "CANCEL-BIZ-409";
            if (EncounterStatus.PLANNED.equals(encounterStatus)) {
                reasonText = "待挂号状态不可退号";
            } else {
                reasonText = "当前状态不可退号";
            }
        }
        // 对特殊场景给出更具体提示文案。
        if (latestInvoice != null && !cancelable) {
            reasonText = "已开票且状态不可退号";
        }
        if (!chargeItems.isEmpty() && payments.isEmpty()) {
            reasonCode = "CANCEL-BIZ-409";
        }
        return outpatientRegistrationMapper.toValidateCancelableResponse(cancelable, reasonCode, reasonText);
    }

    /**
     * 执行退号并联动退费与冲红。
     *
     * @param request 退号退费请求
     * @return 退号退费结果
     * @throws BusinessException 参数不完整或不满足退号条件时抛出
     */
    public CancelWithRefundResponse cancelWithRefund(CancelWithRefundRequest request) {
        // 退号退费必须具备最小参数集。
        if (request.getEncounterId() == null || request.getCancelReason() == null || request.getCancelReason().isBlank() || request.getRefundRequestId() == null || request.getRefundRequestId().isBlank()) {
            throw new BusinessException("REFUND-VAL-001", "退号参数不完整");
        }
        // 先执行可退号校验，不通过则终止退号退费流程；异常文案与校验接口一致（如待挂号不可退号）。
        ValidateCancelableResponse validateResult = validateCancelable(new ValidateCancelableRequest().setEncounterId(request.getEncounterId()));
        if (!Boolean.TRUE.equals(validateResult.getCancelable())) {
            String refundBlockMsg = validateResult.getReasonText();
            if (refundBlockMsg == null || refundBlockMsg.isBlank()) {
                refundBlockMsg = "当前状态不可退号退费";
            }
            throw new BusinessException("REFUND-BIZ-403", refundBlockMsg);
        }
        // 执行就诊退号动作。
        CancelOutpatientRegistrationCommand cancelCommand = outpatientRegistrationMapper.toCancelOutpatientRegistrationCommand(request);
        encounterDomainService.cancelOutpatientRegistration(cancelCommand);
        if (outpatientBookingRegistrationExtension.isEnabled()) {
            OutpatientRegistrationCancelCommand cancelReason = new OutpatientRegistrationCancelCommand();
            cancelReason.setCancelReason(request.getCancelReason());
            outpatientBookingRegistrationExtension.onRegistrationCancelled(request.getEncounterId(), cancelReason);
        }
        List<PaymentReconciliationQueryModel> payments = paymentReconciliationQueryService.findPaySuccessOrRefundingByEncounter(request.getEncounterId());
        List<Long> paymentIds = new ArrayList<>();
        for (PaymentReconciliationQueryModel payment : payments) {
            paymentIds.add(payment.getId());
        }
        // 存在支付记录时，创建逆向单并完成退费与收费项回退；原收费单保持 SUCCESS。
        PaymentReconciliationQueryModel refundPayment = null;
        if (!paymentIds.isEmpty()) {
            Long operatorId = currentPractitionerResolver.requireOperatorId("REFUND-VAL-001", "当前登录用户不能为空");
            PaymentReconciliationQueryModel originalPayment = payments.get(0);
            thirdPartyPaymentRefundSupport.applyRefundBeforeLocalRefund(
                originalPayment.getId(),
                request.getClientRefundResult(),
                operatorId);
            RequestOutpatientRefundCommand requestRefundCommand = outpatientRegistrationMapper.toRequestOutpatientRefundCommand(request, originalPayment);
            Long originalPaymentId = originalPayment.getId();
            Long reversePaymentId = null;
            try {
                reversePaymentId = paymentReconciliationDomainService.requestOutpatientRefund(requestRefundCommand);
                // Cmd：退号整单全退专用收口（非门诊账单半退编排）；仅逆向单 REFUNDED 并发布一次退费完成事件。
                paymentReconciliationDomainService.completeRegistrationCancelRefund(
                    outpatientRegistrationMapper.toCompleteRegistrationCancelRefundCommand(originalPaymentId, reversePaymentId));
                BatchChargeItemIdsCommand refundItemsCommand = outpatientRegistrationMapper.toBatchChargeItemIdsCommandFromEncounter(request.getEncounterId(), chargeItemQueryService);
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
        // 若存在已开票记录，则同步冲红。
        Invoice redInvoice = null;
        if (!payments.isEmpty()) {
            InvoiceQueryCommand latestInvoiceQuery = new InvoiceQueryCommand();
            latestInvoiceQuery.setPaymentId(payments.get(0).getId());
            Invoice latestInvoice = invoiceQueryService.getLatestIssuedInvoiceByPaymentId(latestInvoiceQuery);
            if (latestInvoice != null) {
                CancelInvoiceCommand cancelInvoiceCommand = outpatientRegistrationMapper.toCancelInvoiceCommand(latestInvoice.getId(), request.getCancelReason());
                invoiceDomainService.writeoffInvoiceAndCreateRedRecord(cancelInvoiceCommand);
                bossElectronicInvoiceIntegrationSupport.afterInvoiceCancelled(
                    latestInvoice.getId(), request.getCancelReason());
                redInvoice = invoiceQueryService.getInvoiceById(outpatientRegistrationMapper.toInvoiceQueryCommand(latestInvoice.getId()));
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
     * 补打挂号单。
     *
     * @param request 补打请求
     * @return 补打结果
     * @throws BusinessException encounterId 与 slipNo 均缺失时抛出
     */
    public PrintRegisterTicketResponse printRegisterTicket(PrintRegisterTicketRequest request) {
        // 挂号单补打至少需要 encounterId 或 slipNo 之一。
        if ((request.getEncounterId() == null) && (request.getSlipNo() == null || request.getSlipNo().isBlank())) {
            throw new BusinessException("REG-VAL-001", "encounterId 和 slipNo 至少传一个");
        }
        // 优先按 encounterId 查询挂号单。
        RegistrationSlipQueryModel slipReadModel = null;
        if (request.getEncounterId() != null) {
            slipReadModel = encounterContextFacade.getRegistrationSlipByEncounterId(request.getEncounterId()).orElse(null);
        }
        // 若 encounterId 未命中，再按 slipNo 查询最近一条挂号单。
        if (slipReadModel == null && request.getSlipNo() != null && !request.getSlipNo().isBlank()) {
            RegistrationSlipPageQuery query = new RegistrationSlipPageQuery();
            query.setSlipNo(request.getSlipNo());
            query.setPageNo(1);
            query.setPageSize(1);
            Page<RegistrationSlipQueryModel> page = encounterContextFacade.pageRegistrationSlip(query);
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
        if (draftDisplayAmount.compareTo(linePersistedAmount) != 0) {
            throw new BusinessException("PAY-BIZ-409", "支付草稿金额与收费明细不一致，请重新预结算");
        }
        return registrationLine;
    }

    /**
     * IF-REG-01：岗位号源与当日放号求交，收窄号源类型下拉。
     */
    private List<HealthcareService> narrowHealthcareServicesForBooking(
        List<HealthcareService> services,
        Long organizationId,
        Long practitionerId,
        LocalDate registrationDate) {
        if (!outpatientBookingQueryExtension.isEnabled() || organizationId == null || services == null || services.isEmpty()) {
            return services;
        }
        LocalDate date;
        if (registrationDate != null) {
            date = registrationDate;
        } else {
            date = LocalDate.now();
        }
        List<BookableServiceOptionSnapshot> bookable = outpatientBookingQueryExtension.listBookableServiceTypes(
            organizationId, practitionerId, date);
        if (bookable == null || bookable.isEmpty()) {
            return services;
        }
        Set<Long> allowedIds = new HashSet<>();
        for (BookableServiceOptionSnapshot option : bookable) {
            if (option != null && option.getHealthcareServiceId() != null) {
                allowedIds.add(option.getHealthcareServiceId());
            }
        }
        List<HealthcareService> narrowed = new ArrayList<>();
        for (HealthcareService service : services) {
            if (service != null && service.getId() != null && allowedIds.contains(service.getId())) {
                narrowed.add(service);
            }
        }
        if (narrowed.isEmpty()) {
            return services;
        }
        return narrowed;
    }

    /**
     * 模式 C：现场挂号尝试占当日空闲号源（失败则降级不占号）。
     */
    private Long tryAllocateWalkInSlot(
        PreRegisterSettlementRequest request,
        Long patientId,
        Long operatorId,
        LocalDate registrationDate) {
        if (request.getOrganizationId() == null || request.getServiceTypeId() == null) {
            return null;
        }
        OutpatientWalkInSlotAllocateCommand command = new OutpatientWalkInSlotAllocateCommand();
        command.setOrganizationId(request.getOrganizationId());
        command.setPractitionerId(request.getPractitionerId());
        command.setHealthcareServiceId(request.getServiceTypeId());
        command.setRegistrationDate(registrationDate);
        command.setPatientId(patientId);
        command.setOperatorId(operatorId);
        command.setSlotId(request.getSlotId());
        return outpatientBookingRegistrationExtension.allocateWalkInSlot(command);
    }

    /**
     * IF-REG-13 预约转挂号 Gate：校验预约事实与三要素，并复核医师岗位。
     */
    private void runBookingRegistrationGate(PreRegisterSettlementRequest request) {
        OutpatientBookingRegistrationValidateCommand validateCommand = new OutpatientBookingRegistrationValidateCommand();
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

    private boolean isInsuranceContract(ContractDetailQueryModel contract) {
        if (contract == null) {
            return false;
        }
        if (contract.getId() != null && outpatientRegistrationInsuranceExtension.supportsThirdPartyContract(contract.getId())) {
            return true;
        }
        return isInsuranceCategory(contract.getCategoryEnum());
    }

    private boolean isPublicChargeContract(ContractDetailQueryModel contract) {
        return contract != null
            && InsuranceContractCategorySupport.isPublicChargeCategoryEnum(contract.getCategoryEnum());
    }

    private boolean isInsuranceCategory(String categoryEnum) {
        return InsuranceContractCategorySupport.isInsuranceCategoryEnum(categoryEnum);
    }

    private LoadRegisterBenefitPreviewResponse buildStudentBenefitPreview(
        LoadRegisterBenefitPreviewRequest request,
        LoadRegisterBenefitPreviewResponse response) {
        if (request.getPatientId() == null) {
            throw new BusinessException("REG-VAL-001", "请先选择患者");
        }
        response.setBenefitKind("STUDENT");
        response.setStudentPreview(buildStudentProfilePreview(request));
        if (request.getStudentId() != null) {
            StudentPublicExpenseEligibilityQueryModel eligibility =
                patientQueryService.checkStudentPublicExpenseEligibility(request.getPatientId(), request.getStudentId());
            response.setStudentEligibility(StudentEligibilityPreviewDto.from(eligibility));
        }
        return response;
    }

    private StudentRegisterBenefitPreviewDto buildStudentProfilePreview(LoadRegisterBenefitPreviewRequest request) {
        PatientStudentProfileQueryModel profile =
            patientQueryService.loadPatientStudentProfile(request.getPatientId());
        StudentRegisterBenefitPreviewDto dto = new StudentRegisterBenefitPreviewDto();
        dto.setPatientId(profile.getPatientId());
        dto.setPatientName(profile.getName());
        dto.setIdCard(profile.getIdCard());
        dto.setPhone(profile.getPhone());
        if (profile.getStudentRecords() != null) {
            for (PatientStudentQueryModel row : profile.getStudentRecords()) {
                StudentRecordPreviewDto item = new StudentRecordPreviewDto();
                item.setStudentId(row.getId());
                item.setName(row.getName());
                item.setStudentNo(row.getStudentNo());
                item.setCollege(row.getCollege());
                item.setMajor(row.getMajor());
                item.setGrade(row.getGrade());
                item.setSchoolStatus(row.getSchoolStatus());
                item.setPhysicalExamResult(row.getPhysicalExamResult());
                if (row.getLeaveTime() != null) {
                    item.setLeaveTimeText(row.getLeaveTime().toString());
                }
                dto.getStudentRecords().add(item);
            }
        }
        return dto;
    }

    private void attachContractMatchToResponse(
        LoadRegisterBenefitPreviewResponse response,
        YbRegisterBenefitPreviewDto preview,
        Long contractId) {
        RegisterContractMatchDto match = buildContractMatch(preview, contractId);
        if (match != null) {
            response.setContractMatch(match);
        }
    }

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
        String treatment = registerContractMatchSupport.resolveTreatmentAreaCode(contractId, admFallback);
        return registerContractMatchSupport.evaluate(
            insuplc,
            contractId,
            treatment,
            contractQueryService.listAllContracts());
    }

    private LoadRegisterBenefitPreviewResponse buildInsuranceBenefitPreview(
        LoadRegisterBenefitPreviewRequest request,
        LoadRegisterBenefitPreviewResponse response) {
        YbRegisterBenefitPreviewDto preview = fetchInsurancePreview(request);
        attachTreatmentCheckIfNeeded(
            preview,
            request.getContractId(),
            preview.getInsuplcAdmdvs(),
            request.getMedType(),
            shouldRunTreatmentCheck(request.getRunTreatmentCheck()));
        attachOpspFilingIfNeeded(
            preview,
            request.getContractId(),
            preview.getInsuplcAdmdvs(),
            shouldRunOpspFilingQuery(request.getRunOpspFilingQuery()));
        attachAreaHintIfNeeded(preview, request.getContractId(), preview.getInsuplcAdmdvs());
        attachCumulativeQueryIfNeeded(
            preview,
            request.getContractId(),
            preview.getInsuplcAdmdvs(),
            shouldRunCumulativeQuery(request.getRunCumulativeQuery()));
        response.setBenefitKind("INSURANCE");
        response.setYbPreview(preview);
        attachContractMatchToResponse(response, preview, request.getContractId());
        return response;
    }

    private YbRegisterBenefitPreviewDto fetchInsurancePreview(LoadRegisterBenefitPreviewRequest request) {
        if (StringUtils.isNotBlank(request.getYbMediumType()) && StringUtils.isNotBlank(request.getYbCardInfo())) {
            if (!registerBenefitQueryExtension.isEnabled()) {
                throw new BusinessException("REG-VAL-003", "未装配医保模块，无法读卡");
            }
            InsurancePersonQueryCommand personCommand = new InsurancePersonQueryCommand();
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

    private boolean shouldRunTreatmentCheck(Boolean runTreatmentCheck) {
        return runTreatmentCheck == null || Boolean.TRUE.equals(runTreatmentCheck);
    }

    private boolean shouldRunOpspFilingQuery(Boolean runOpspFilingQuery) {
        return runOpspFilingQuery == null || Boolean.TRUE.equals(runOpspFilingQuery);
    }

    private boolean shouldRunCumulativeQuery(Boolean runCumulativeQuery) {
        return runCumulativeQuery == null || Boolean.TRUE.equals(runCumulativeQuery);
    }

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
            preview.setAreaHint(RegisterBenefitAreaHintSupport.build(admdvs, mdtrtareaAdmdvs));
        } catch (RuntimeException ex) {
            log.warn("就医地区划解析失败 contractId={}: {}", contractId, ex.getMessage());
        }
    }

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
            InsuranceCumulativeQueryCommand queryCommand = new InsuranceCumulativeQueryCommand();
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

    private void appendCumulativePreview(YbRegisterBenefitPreviewDto preview, InsuranceCumulativeQueryResult response) {
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
            InsuranceOpspFilingQueryCommand queryCommand = new InsuranceOpspFilingQueryCommand();
            queryCommand.setContractId(contractId);
            queryCommand.setPsnNo(preview.getPsnNo());
            queryCommand.setInsurancePoolAreaCode(
                StringUtils.isNotBlank(insuplcAdmdvs) ? insuplcAdmdvs : preview.getInsuplcAdmdvs());
            appendOpspFilingPreview(preview, registerBenefitQueryExtension.queryOpspFiling(queryCommand));
        } catch (RuntimeException ex) {
            log.warn("5301 慢特病备案查询失败 contractId={} psnNo={}: {}", contractId, preview.getPsnNo(), ex.getMessage());
        }
    }

    private void appendOpspFilingPreview(YbRegisterBenefitPreviewDto preview, InsuranceOpspFilingQueryResult response) {
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
        try {
            InsuranceTreatmentCheckCommand checkCommand = new InsuranceTreatmentCheckCommand();
            checkCommand.setContractId(contractId);
            checkCommand.setPsnNo(preview.getPsnNo());
            checkCommand.setInsutype(preview.getInsutype());
            String admdvs = StringUtils.isNotBlank(insuplcAdmdvs) ? insuplcAdmdvs : preview.getInsuplcAdmdvs();
            checkCommand.setInsurancePoolAreaCode(admdvs);
            checkCommand.setMedType(
                StringUtils.isNotBlank(medType) ? medType.trim() : YbMedType.GENERAL_OUTPATIENT.getCode());
            appendTreatmentPreview(preview, registerBenefitQueryExtension.checkTreatment(checkCommand));
        } catch (RuntimeException ex) {
            log.warn("2001 待遇检查失败 contractId={} psnNo={}: {}", contractId, preview.getPsnNo(), ex.getMessage());
        }
    }

    private void appendTreatmentPreview(YbRegisterBenefitPreviewDto preview, InsuranceTreatmentCheckResult response) {
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

    private YbRegisterBenefitPreviewDto toYbRegisterBenefitPreview(
        InsurancePersonQueryResult person,
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
     * @param request 预结算请求
     * @return 可用患者ID
     * @throws BusinessException 新建患者时姓名/性别未补齐，或当前登录机构不可用时抛出
     */
    private Long resolvePatientIdForPreRegister(PreRegisterSettlementRequest request) {
        // 已选中既有患者时，直接复用前端指定患者。
        if (request.getPatientId() != null) {
            return request.getPatientId();
        }
        // 未传身份证号时跳过同人识别，直接按最小建档字段走后续建档流程。
        if (request.getIdCardNo() != null && !request.getIdCardNo().isBlank()) {
            // 按身份证精确命中既有患者，命中后复用该患者继续挂号。
            PatientSimpleQueryModel matchedPatient = patientQueryService.findPatientByIdCard(request.getIdCardNo());
            if (matchedPatient != null) {
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

    private BigDecimal resolveRegisterFeeExpectedPayable(
        PaymentReconciliationQueryModel paymentDraft,
        ChargeItem registrationChargeLine) {
        if (paymentDraft.getDisplayAmount() != null
            && chargeInsuranceExtension.isEnabled()
            && paymentDraft.getId() != null
            && !chargeInsuranceExtension.listActivePreSettlesByPaymentDraft(paymentDraft.getId()).isEmpty()) {
            return paymentDraft.getDisplayAmount();
        }
        BigDecimal amount = registrationChargeLine.getTotalPrice();
        return amount != null ? amount : BigDecimal.ZERO;
    }

    private BigDecimal sumRegisterFeeSubmitAmount(PayRegisterFeeRequest request) {
        BigDecimal total = BigDecimal.ZERO;
        for (PaymentDetailDto detail : request.getPaymentDetails()) {
            if (detail.getPayAmount() != null) {
                total = total.add(detail.getPayAmount());
            }
        }
        return total;
    }

    private List<OutpatientChargeConfirmResult> collectRegisterFeeInsuranceConfirmResults(
        PayRegisterFeeRequest request,
        PaymentReconciliationQueryModel paymentDraft) {
        if (!chargeInsuranceExtension.isEnabled() || request.getPaymentDraftId() == null) {
            return List.of();
        }
        List<OutpatientChargePreSettleSnapshot> active =
            chargeInsuranceExtension.listActivePreSettlesByPaymentDraft(request.getPaymentDraftId());
        if (active.isEmpty()) {
            return List.of();
        }
        List<OutpatientChargeConfirmResult> results = new ArrayList<>();
        int succeededBatches = 0;
        try {
            for (OutpatientChargePreSettleSnapshot row : active) {
                if (row == null || StringUtils.isBlank(row.getChargeBatchNo())) {
                    continue;
                }
                OutpatientChargeConfirmCommand command = new OutpatientChargeConfirmCommand();
                command.setPaymentDraftId(request.getPaymentDraftId());
                command.setChargeBatchNo(row.getChargeBatchNo().trim());
                command.setEncounterId(paymentDraft.getEncounterId());
                results.add(chargeInsuranceExtension.confirmBeforeLocalSettle(command));
                succeededBatches += 1;
            }
        } catch (RuntimeException ex) {
            if (succeededBatches > 0) {
                outpatientInsuranceSettlementCompensation.compensateSettlementsByPayment(request.getPaymentDraftId());
            }
            if (this.isInsuranceCredentialExpired(ex)) {
                throw new BusinessException(
                    "INS-015",
                    "医保电子凭证已失效，请重新展码并刷新预结算后再缴费");
            }
            throw new BusinessException("INS-013", "挂号费医保结算失败，请勿重复收费，联系医保办处理");
        }
        return results;
    }

    private List<PaymentYbSettlementItemCommand> buildRegisterFeeYbSettlementItems(
        List<OutpatientChargeConfirmResult> results) {
        if (results == null || results.isEmpty()) {
            return List.of();
        }
        List<PaymentYbSettlementItemCommand> items = new ArrayList<>();
        for (OutpatientChargeConfirmResult row : results) {
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

    private void mergeRegisterFeeInsurancePaymentDetails(
        SettleOutpatientPaymentCommand settleCommand,
        List<OutpatientChargeConfirmResult> ybConfirmResults,
        List<PaymentRecDetailQueryModel> draftDetails) {
        if (ybConfirmResults == null || ybConfirmResults.isEmpty()) {
            return;
        }
        Map<Long, String> accountCodeById = new HashMap<>();
        for (PaymentRecDetailQueryModel draftRow : draftDetails) {
            if (draftRow.getAccountId() != null && StringUtils.isNotBlank(draftRow.getAccountCode())) {
                accountCodeById.put(draftRow.getAccountId(), draftRow.getAccountCode().trim());
            }
        }
        List<PaymentDetailItemCommand> insuranceLines = new ArrayList<>();
        LocalDateTime billDate = settleCommand.getBillDate() != null
            ? settleCommand.getBillDate()
            : LocalDateTime.now();
        for (OutpatientChargeConfirmResult ybResult : ybConfirmResults) {
            if (ybResult == null) {
                continue;
            }
            Long accountId = ybResult.getAccountId();
            String accountCode = accountCodeById.get(accountId);
            String setlId = ybResult.getSettlementId();
            this.appendRegisterFeeInsurancePaymentLine(
                insuranceLines, PaymentChannel.INSURANCE_FUND.getCode(),
                ybResult.getFundPaySumamt(), accountId, accountCode, setlId, billDate);
            this.appendRegisterFeeInsurancePaymentLine(
                insuranceLines, PaymentChannel.INSURANCE_ACCT.getCode(),
                ybResult.getAcctPay(), accountId, accountCode, setlId, billDate);
        }
        if (insuranceLines.isEmpty()) {
            return;
        }
        List<PaymentDetailItemCommand> merged = new ArrayList<>(insuranceLines);
        if (settleCommand.getPayReceiptDetails() != null) {
            merged.addAll(settleCommand.getPayReceiptDetails());
        }
        settleCommand.setPayReceiptDetails(merged);
    }

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

    private void enrichClinicRegisterListRecords(
        List<ClinicRegisterItemDto> records,
        Map<Long, Long> paymentDraftByEncounter) {
        if (records == null || records.isEmpty()) {
            return;
        }
        List<Long> encounterIds = new ArrayList<>();
        for (ClinicRegisterItemDto row : records) {
            if (row != null && row.getEncounterId() != null) {
                encounterIds.add(row.getEncounterId());
            }
        }
        if (encounterIds.isEmpty()) {
            return;
        }
        Map<Long, Account> accountByEncounter = accountQueryService.mapRegistrationAccountsByEncounterIds(encounterIds);
        Map<String, String> contractNameByBusNo = new HashMap<>();
        for (Account account : accountByEncounter.values()) {
            if (account == null || StringUtils.isBlank(account.getContractNo())) {
                continue;
            }
            String busNo = account.getContractNo().trim();
            if (contractNameByBusNo.containsKey(busNo)) {
                continue;
            }
            contractQueryService.findOptionalContractEntityByBusNo(busNo)
                .ifPresent(contract -> contractNameByBusNo.put(busNo, contract.getName()));
        }
        Map<Long, OutpatientRegistrationPlatformSnapshot> platformByEncounter = new HashMap<>();
        if (outpatientRegistrationInsuranceExtension.isEnabled()) {
            for (OutpatientRegistrationPlatformSnapshot snap :
                outpatientRegistrationInsuranceExtension.findPlatformSnapshots(encounterIds, paymentDraftByEncounter)) {
                if (snap != null && snap.getEncounterId() != null) {
                    platformByEncounter.put(snap.getEncounterId(), snap);
                }
            }
        }
        for (ClinicRegisterItemDto row : records) {
            if (row == null || row.getEncounterId() == null) {
                continue;
            }
            Account account = accountByEncounter.get(row.getEncounterId());
            if (account != null && StringUtils.isNotBlank(account.getContractNo())) {
                row.setContractName(contractNameByBusNo.get(account.getContractNo().trim()));
            }
            OutpatientRegistrationPlatformSnapshot snap = platformByEncounter.get(row.getEncounterId());
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

    private Long resolveContractIdFromAccount(Account account) {
        if (account == null || StringUtils.isBlank(account.getContractNo())) {
            return null;
        }
        return contractQueryService.findOptionalContractEntityByBusNo(account.getContractNo().trim())
            .map(Contract::getId)
            .orElse(null);
    }

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
