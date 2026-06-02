package com.openhis.health.application.outpatient.financial.registration.service;

import com.openhis.health.application.common.validator.CurrentOperatorResolver;
import com.openhis.health.application.common.validator.PageWindowGate;
import com.openhis.health.application.common.validator.PaymentChannelGate;
import com.openhis.health.application.outpatient.financial.registration.dto.PaymentDetailDto;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.application.outpatient.financial.registration.dto.ChargeItemPreviewDto;
import com.openhis.health.application.outpatient.financial.registration.dto.ClinicRegisterItemDto;
import com.openhis.health.application.outpatient.financial.registration.dto.PatientItemDto;
import com.openhis.health.application.outpatient.financial.registration.dto.RegistrationSlipDto;
import com.openhis.health.application.outpatient.financial.registration.mapper.OutpatientRegistrationMapper;
import com.openhis.health.application.outpatient.financial.registration.request.CancelWithRefundRequest;
import com.openhis.health.application.outpatient.financial.registration.request.ListDepartmentsRequest;
import com.openhis.health.application.outpatient.financial.registration.request.ListPractitionersRequest;
import com.openhis.health.application.outpatient.financial.registration.request.LoadRegisterFormInitRequest;
import com.openhis.health.application.outpatient.financial.registration.request.PageClinicRegistersRequest;
import com.openhis.health.application.outpatient.financial.registration.request.PagePatientsForSelectRequest;
import com.openhis.health.application.outpatient.financial.registration.request.PayRegisterFeeRequest;
import com.openhis.health.application.outpatient.financial.registration.request.PreRegisterSettlementRequest;
import com.openhis.health.application.outpatient.financial.registration.request.PrintRegisterTicketRequest;
import com.openhis.health.application.outpatient.financial.registration.request.ValidateCancelableRequest;
import com.openhis.health.application.outpatient.financial.registration.response.CancelWithRefundResponse;
import com.openhis.health.application.outpatient.financial.registration.response.ListDepartmentsResponse;
import com.openhis.health.application.outpatient.financial.registration.response.ListPractitionersResponse;
import com.openhis.health.application.outpatient.financial.registration.response.LoadRegisterFormInitResponse;
import com.openhis.health.application.outpatient.financial.registration.response.PageClinicRegistersResponse;
import com.openhis.health.application.outpatient.financial.registration.response.PagePatientsForSelectResponse;
import com.openhis.health.application.outpatient.financial.registration.response.PayRegisterFeeResponse;
import com.openhis.health.application.outpatient.financial.registration.response.PreRegisterSettlementResponse;
import com.openhis.health.application.outpatient.financial.registration.response.PrintRegisterTicketResponse;
import com.openhis.health.application.outpatient.financial.registration.response.ValidateCancelableResponse;
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
import com.openhis.health.domain.administration.encounter.queries.OutpatientDuplicateCheckQuery;
import com.openhis.health.domain.administration.encounter.queries.model.EncounterDetailQueryModel;
import com.openhis.health.domain.administration.encounter.queries.model.OutpatientDuplicateCheckQueryModel;
import com.openhis.health.domain.administration.encounter.service.EncounterDomainService;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.application.querysupport.encounter.EncounterContextFacade;
import com.openhis.health.domain.administration.healthcareservice.entity.HealthcareService;
import com.openhis.health.domain.administration.healthcareservice.service.HealthcareServiceQueryService;
import com.openhis.health.domain.administration.invoice.command.CancelInvoiceCommand;
import com.openhis.health.domain.administration.invoice.command.CreateDraftInvoiceCommand;
import com.openhis.health.domain.administration.invoice.command.IssueElectronicInvoiceCommand;
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
import com.openhis.health.domain.administration.patient.queries.model.PatientSimpleQueryModel;
import com.openhis.health.domain.administration.patient.service.PatientDomainService;
import com.openhis.health.domain.administration.patient.service.PatientQueryService;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.administration.practitioner.service.PractitionerQueryService;
import com.openhis.health.domain.financial.contract.queries.model.ContractDetailQueryModel;
import com.openhis.health.domain.financial.contract.queries.model.ContractListItemQueryModel;
import com.openhis.health.domain.financial.contract.service.ContractQueryService;
import com.openhis.health.domain.financial.paymentreconciliation.command.CreatePaymentDraftFromPreSettleCommand;
import com.openhis.health.domain.financial.paymentreconciliation.command.EnterRefundProcessingWindowCommand;
import com.openhis.health.domain.financial.paymentreconciliation.command.PaymentDetailItemCommand;
import com.openhis.health.domain.financial.paymentreconciliation.command.RequestOutpatientRefundCommand;
import com.openhis.health.domain.financial.paymentreconciliation.command.SettleOutpatientPaymentCommand;
import com.openhis.health.domain.financial.paymentreconciliation.queries.model.PaymentReconciliationQueryModel;
import com.openhis.health.domain.financial.paymentreconciliation.service.PaymentReconciliationDomainService;
import com.openhis.health.domain.financial.paymentreconciliation.service.PaymentReconciliationQueryService;
import com.openhis.health.domain.administration.encounter.queries.model.ClinicRegisterQueryModel;
import com.openhis.health.domain.administration.encounter.queries.ClinicRegisterPageQuery;
import com.openhis.health.domain.administration.encounter.queries.model.RegistrationSlipQueryModel;
import com.openhis.health.domain.administration.encounter.queries.RegistrationSlipPageQuery;
import com.openhis.health.domain.share.enums.AdministrativeGender;
import com.openhis.health.domain.share.enums.ChargeItemStatus;
import com.openhis.health.domain.share.enums.EncounterClass;
import com.openhis.health.domain.share.enums.EncounterStatus;
import com.openhis.health.domain.share.enums.PaymentStatus;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.health.domain.terminology.service.CodeSystemQueryService;
import com.openhis.whale.core.application.dto.OptionDto;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.infrastructure.security.ICurrentUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    /** 当前登录用户上下文（机构、展示名等，非操作人门控）。 */
    private final ICurrentUser currentUser;
    /** 登录操作人门控（B.6.1）。 */
    private final CurrentOperatorResolver currentOperatorResolver;
    /** 分页窗口入站门控（B.10.11）。 */
    private final PageWindowGate pageWindowGate;

    /** 支付渠道入站门控。 */
    private final PaymentChannelGate paymentChannelGate;

    /**
     * 加载挂号表单初始化数据。
     *
     * @param request 初始化请求
     * @return 初始化结果
     */
    public LoadRegisterFormInitResponse loadRegisterFormInit(LoadRegisterFormInitRequest request) {
        // 加载当前门诊挂号可选的科室、医生、费用性质和号源类型。
        List<Organization> organizations = organizationQueryService.listOrganizationsForOutpatientRegistration();
        List<Practitioner> practitioners = practitionerQueryService.listPractitionersForOutpatientRegistration(request.getOrganizationId());
        Page<ContractListItemQueryModel> contractPage = contractQueryService.listContracts(request.getOrganizationId(), Pageable.unpaged());
        List<ContractListItemQueryModel> contracts = contractPage.getContent();
        List<HealthcareService> healthcareServices = healthcareServiceQueryService.listServicesForRegistration();
        // 性别下拉与患者建档页一致：AdministrativeGender → OptionDto。
        List<OptionDto> genderOptions = codeSystemQueryService.getOptionListByEnum(AdministrativeGender.class);
        // 统一转换为前端下拉结构并返回。
        return outpatientRegistrationMapper.toLoadRegisterFormInitResponse(
            outpatientRegistrationMapper.toOrganizationOptionList(organizations),
            outpatientRegistrationMapper.toPractitionerOptionList(practitioners),
            outpatientRegistrationMapper.toContractOptionList(contracts),
            outpatientRegistrationMapper.toHealthcareServiceOptionList(healthcareServices),
            genderOptions
        );
    }

    /**
     * 分页检索可选患者。
     *
     * @param request 分页请求
     * @return 分页结果
     * @throws BusinessException 页码或页大小非法时抛出
     */
    public PagePatientsForSelectResponse pagePatientsForSelect(PagePatientsForSelectRequest request) {
        pageWindowGate.requirePositivePageWindow(request, "REG-PAT-VAL-001", "分页参数不合法");
        // 组装领域分页查询命令。
        PatientPagedQuery query = new PatientPagedQuery();
        query.setName(request.getKeyword());
        query.setPageNo(request.getPageNo());
        query.setPageSize(request.getPageSize());
        // 查询患者分页并映射返回。
        Page<PatientPageItemQueryModel> page = patientQueryService.searchPatientsPaged(query);
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
        // 按科室加载可挂号医生并映射输出。
        List<Practitioner> practitioners = practitionerQueryService.listPractitionersForOutpatientRegistration(request.getOrganizationId());
        return outpatientRegistrationMapper.toListPractitionersResponse(outpatientRegistrationMapper.toPractitionerOptionList(practitioners));
    }

    /**
     * 执行挂号预结算并生成支付草稿。
     *
     * @param request 预结算请求
     * @return 预结算结果
     * @throws BusinessException 挂号要素不完整、重复挂号、主数据失效或定价不存在时抛出
     */
    public PreRegisterSettlementResponse preRegisterSettlement(PreRegisterSettlementRequest request) {
        // 门控挂号关键要素，避免后续编排出现空主键。
        if (request.getContractId() == null || request.getOrganizationId() == null || request.getServiceTypeId() == null) {
            throw new BusinessException("REG-VAL-001", "请补齐挂号要素");
        }
        // 预结算前先收敛患者主档：优先使用已选患者，其次按身份证命中既有患者，未命中则新建患者。
        Long patientId = resolvePatientIdForPreRegister(request);
        request.setPatientId(patientId);
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
        Long registrarId = currentOperatorResolver.requireOperatorId("REG-VAL-001", "当前登录用户不能为空");
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
            currentUser.getCurrentUserName(),
            occurredAt,
            registrationPractitionerDisplayName
        );
        Long encounterId = encounterDomainService.registerOutpatientEncounter(encounterCommand);
        if (encounterId == null) {
            throw new BusinessException("REG-BIZ-500", "创建就诊失败，无法生成支付草稿");
        }
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
            currentOperatorResolver.requireOperatorId("REG-VAL-001", "当前登录用户不能为空"));
        Long paymentDraftId = paymentReconciliationDomainService.createPaymentDraftFromPreSettle(paymentDraftCommand);
        List<ChargeItemPreviewDto> previews = outpatientRegistrationMapper.toChargeItemPreviewDtoListBySaleLine(saleLine);
        // 返回预结算结果，供前端确认收费项和金额。
        return outpatientRegistrationMapper.toPreRegisterSettlementResponse(
            encounterId,
            paymentDraftId,
            chargeItemAmount,
            previews
        );
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
            || request.getPaymentDetails() == null
            || request.getPaymentDetails().isEmpty();
        if (missingRequiredParam) {
            throw new BusinessException("PAY-VAL-001", "结算参数不完整");
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
        SettleOutpatientPaymentCommand settleOutpatientPaymentCommand = outpatientRegistrationMapper.toSettleOutpatientPaymentCommand(request);
        Long paymentId = paymentReconciliationDomainService.settleOutpatientPayment(settleOutpatientPaymentCommand);
        BatchChargeItemIdsCommand settleItemsCommand = outpatientRegistrationMapper.toSingleChargeItemBatchCommand(chargeItemId);
        chargeItemDomainService.settleChargeItems(settleItemsCommand);
        // 缴费成功后将就诊状态从 PLANNED 迁移至 REGISTERED，与 EncounterStatus 生命周期对齐。
        encounterDomainService.completeOutpatientRegistration(paymentDraft.getEncounterId());

        // 第四步：支付完成后创建电子发票草稿、开具发票并回查最新票据结果。
        CreateDraftInvoiceCommand draftInvoiceCommand = outpatientRegistrationMapper.toCreateDraftInvoiceCommand(paymentDraft, paymentId);
        Long invoiceId = invoiceDomainService.createDraftInvoiceAfterPayment(draftInvoiceCommand);
        IssueElectronicInvoiceCommand issueElectronicInvoiceCommand = outpatientRegistrationMapper.toIssueElectronicInvoiceCommand(invoiceId);
        invoiceDomainService.issueElectronicInvoice(issueElectronicInvoiceCommand);
        Invoice invoice = invoiceQueryService.getInvoiceById(outpatientRegistrationMapper.toInvoiceQueryCommand(invoiceId));

        // 第五步：回传最新支付信息与发票信息，组装缴费结果。
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
        List<ClinicRegisterItemDto> records =
            outpatientRegistrationMapper.toClinicRegisterItemDtoList(page, registrationFeeByAnchor, paymentDraftByEncounter);
        return outpatientRegistrationMapper.toPageClinicRegistersResponse(page, records);
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
        // 按就诊状态判断是否允许退号（使用枚举，避免字符串硬编码）；待挂号（PLANNED）与终态一律不可退号。
        EncounterStatus encounterStatus = EnumUtil.fromCode(EncounterStatus.class, encounter.getStatusEnum());
        boolean cancelable = encounterStatus != null
            && !EncounterStatus.PLANNED.equals(encounterStatus)
            && !EncounterStatus.DISCHARGED.equals(encounterStatus)
            && !EncounterStatus.CANCELLED.equals(encounterStatus);
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
        List<PaymentReconciliationQueryModel> payments = paymentReconciliationQueryService.findPaySuccessOrRefundingByEncounter(request.getEncounterId());
        List<Long> paymentIds = new ArrayList<>();
        for (PaymentReconciliationQueryModel payment : payments) {
            paymentIds.add(payment.getId());
        }
        // 存在支付记录时，进入退费窗口并完成退费和收费项回退。
        PaymentReconciliationQueryModel refundPayment = null;
        if (!paymentIds.isEmpty()) {
            EnterRefundProcessingWindowCommand refundWindowCommand = outpatientRegistrationMapper.toEnterRefundProcessingWindowCommand(paymentIds);
            paymentReconciliationDomainService.enterRefundProcessingWindow(refundWindowCommand);
            RequestOutpatientRefundCommand requestRefundCommand = outpatientRegistrationMapper.toRequestOutpatientRefundCommand(request, payments.get(0));
            Long originalPaymentId = payments.get(0).getId();
            Long refundPaymentId = paymentReconciliationDomainService.requestOutpatientRefund(requestRefundCommand);
            // Cmd：退号整单全退专用收口（非门诊账单半退编排）；原单+逆向单一次落库并仅发布一次退费完成事件。
            paymentReconciliationDomainService.completeRegistrationCancelRefund(
                outpatientRegistrationMapper.toCompleteRegistrationCancelRefundCommand(originalPaymentId, refundPaymentId));
            refundPayment = paymentReconciliationQueryService.mustGetPayment(refundPaymentId);
            BatchChargeItemIdsCommand refundItemsCommand = outpatientRegistrationMapper.toBatchChargeItemIdsCommandFromEncounter(request.getEncounterId(), chargeItemQueryService);
            chargeItemDomainService.returnRegistrationRefundChargeItems(refundItemsCommand);
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
        // 返回补打结果并累加打印次数。
        RegistrationSlipDto slip = outpatientRegistrationMapper.toRegistrationSlipDto(slipReadModel);
        int printCount = 0;
        if (slip != null && slip.getPrintCount() != null) {
            printCount = slip.getPrintCount();
        }
        return outpatientRegistrationMapper.toPrintRegisterTicketResponse(slipReadModel).setPrintCount(printCount + 1);
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
        // 新建患者主档时，机构归属强制取当前登录机构，不使用前端挂号科室入参。
        Long patientOrganizationId = currentUser.getCurrentOrganizationUnitId();
        if (patientOrganizationId == null) {
            throw new BusinessException("REG-VAL-001", "当前登录机构不能为空");
        }
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
     * 为本页挂号行构建「挂号费锚点 → 收费总价」索引：就诊维度一次 IN 查询，内存按号源锚点汇总 {@code total_price}。
     *
     * @param rows 当前页读模型行
     * @return 锚点键到金额的映射（无明细时为空映射）
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
}
