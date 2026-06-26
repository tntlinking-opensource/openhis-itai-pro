package com.openhis.health.application.inpatient.financial.prepay.service;

import com.openhis.health.module.common.pagination.PageQueryBinder;
import com.openhis.health.module.common.validator.CurrentPractitionerResolver;
import com.openhis.health.module.common.validator.PageWindowGate;
import com.openhis.health.module.common.assembler.financial.PaymentChannelOptionProvider;
import com.openhis.health.module.common.validator.PaymentChannelGate;
import com.openhis.health.module.common.support.ThirdPartyPaymentSettleSupport;
import com.openhis.health.module.common.support.ThirdPartyPaymentRefundSupport;
import com.openhis.health.module.common.validator.PatientEncounterConsistencyGate;
import com.openhis.health.application.inpatient.financial.prepay.dto.PrepayPaymentDetailDto;
import com.openhis.health.application.inpatient.financial.prepay.dto.PrepayPaymentRecordDto;
import com.openhis.health.application.inpatient.financial.prepay.dto.PrepayQueueItemDto;
import com.openhis.health.application.inpatient.financial.prepay.mapper.InpatientPrepayMapper;
import com.openhis.health.module.common.assembler.financial.PaymentChannelDisplayAssembler;
import com.openhis.health.module.common.assembler.inpatient.InpatientAccountBalanceDisplayDtoApplier;
import com.openhis.health.module.common.assembler.inpatient.InpatientAccountBalanceDisplayAssembler;
import com.openhis.health.module.common.assembler.inpatient.InpatientAccountBalanceDisplayResult;
import com.openhis.health.module.common.assembler.inpatient.InpatientDisplayAccountBalanceAssembler;
import com.openhis.health.module.common.assembler.inpatient.InpatientInfoQueueDisplayAssembler;
import com.openhis.health.application.inpatient.financial.prepay.request.CollectPrepayAdvanceDepositRequest;
import com.openhis.health.application.inpatient.financial.prepay.request.LoadInpatientPrepayPageInitRequest;
import com.openhis.health.application.inpatient.financial.prepay.request.ListPrepayPaymentRecordsRequest;
import com.openhis.health.application.inpatient.financial.prepay.request.PagePrepayPatientQueueRequest;
import com.openhis.health.application.inpatient.financial.prepay.request.RefundPrepayAdvanceDepositRequest;
import com.openhis.health.application.inpatient.financial.prepay.response.CollectPrepayAdvanceDepositResponse;
import com.openhis.health.application.inpatient.financial.prepay.response.LoadInpatientPrepayPageInitResponse;
import com.openhis.health.application.inpatient.financial.prepay.response.ListPrepayPaymentRecordsResponse;
import com.openhis.health.application.inpatient.financial.prepay.response.PagePrepayPatientQueueResponse;
import com.openhis.health.application.inpatient.financial.prepay.response.PreparePrepayAdvanceDepositResponse;
import com.openhis.health.application.inpatient.financial.prepay.response.RefundPrepayAdvanceDepositResponse;
import com.openhis.health.domain.administration.account.command.AdjustAdvanceDepositBalanceCommand;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.openhis.health.domain.administration.account.entity.Account;
import com.openhis.health.domain.administration.account.service.AccountDomainService;
import com.openhis.health.domain.administration.account.service.AccountQueryService;
import com.openhis.health.domain.administration.encounter.queries.model.EncounterDetailQueryModel;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.module.querysupport.encounter.EncounterContextFacade;
import com.openhis.health.domain.administration.practitioner.service.PractitionerQueryService;
import com.openhis.health.domain.financial.paymentreconciliation.command.CollectAdvanceDepositCommand;
import com.openhis.health.domain.financial.paymentreconciliation.command.RefundAdvanceDepositCommand;
import com.openhis.health.domain.financial.paymentreconciliation.command.SettleInpatientPaymentCommand;
import com.openhis.health.domain.financial.paymentreconciliation.queries.model.PaymentReconciliationQueryModel;
import com.openhis.health.domain.financial.paymentreconciliation.service.PaymentReconciliationDomainService;
import com.openhis.health.domain.financial.paymentreconciliation.service.PaymentReconciliationQueryService;
import com.openhis.health.domain.administration.encounter.queries.InpatientEncounterPageQuery;
import com.openhis.health.domain.administration.encounter.queries.model.InpatientEncounterContextQueryModel;
import com.openhis.health.domain.share.constants.InpatientPrepayConstants;
import com.openhis.health.domain.share.enums.EncounterClass;
import com.openhis.health.domain.share.enums.EncounterStatus;
import com.openhis.health.domain.share.enums.PaymentKind;
import com.openhis.health.domain.share.enums.PaymentStatus;
import com.openhis.whale.core.application.dto.OptionDto;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 住院预交金应用服务（AS-IP-FIN-002，IF-IP-PREP-01～04）。
 *
 * <p>职责边界：</p>
 * <ul>
 *   <li>Gate/Pre：在院队列分页、就诊 IMP 门禁、可收退状态、账户与支付明细合计。</li>
 *   <li>Cmd：预交金收/退草稿、住院支付结算、账户余额调整（顺序与 AS-SDD §3.4/§3.5 一致）。</li>
 *   <li>Asm：经 {@link InpatientPrepayMapper} 装配队列/流水/收退响应。</li>
 * </ul>
 *
 * <p>接入：{@code AppServiceControllerRegistrar} →
 * {@code POST /api/app/inpatientPrepay/{methodName}}；不另写 RestController。</p>
 *
 * <p>写路径事务由 {@link com.openhis.whale.core.infrastructure.transaction.AppServiceAspect} 统一管理 UoW。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InpatientPrepayAppService {

    /** 写路径操作人门控（B.6.1）。 */
    private final CurrentPractitionerResolver currentPractitionerResolver;

    /** 分页窗口入站门控（B.10.11）。 */
    private final PageWindowGate pageWindowGate;

    /** 支付渠道入站门控。 */
    private final PaymentChannelGate paymentChannelGate;

    /** 收银支付渠道下拉（477 ∩ Gate）。 */
    private final PaymentChannelOptionProvider paymentChannelOptionProvider;

    private final ThirdPartyPaymentSettleSupport thirdPartyPaymentSettleSupport;

    private final ThirdPartyPaymentRefundSupport thirdPartyPaymentRefundSupport;

    /** 就诊读侧（队列与门禁）。 */
    private final EncounterQueryService encounterQueryService;
    private final EncounterContextFacade encounterContextFacade;

    /** 就诊账户读侧。 */
    private final AccountQueryService accountQueryService;

    /** 就诊账户写侧（结算后余额）。 */
    private final AccountDomainService accountDomainService;

    /** 支付流水读侧。 */
    private final PaymentReconciliationQueryService paymentReconciliationQueryService;

    /** 支付流水写侧（收退与结算）。 */
    private final PaymentReconciliationDomainService paymentReconciliationDomainService;

    /** 操作员展示名回落。 */
    private final PractitionerQueryService practitionerQueryService;

    /** 预交金 DTO/Command 映射。 */
    private final InpatientPrepayMapper inpatientPrepayMapper;

    /** 住院队列展示回填。 */
    private final InpatientInfoQueueDisplayAssembler inpatientInfoQueueDisplayAssembler;

    /** 支付渠道展示名解析。 */
    private final PaymentChannelDisplayAssembler paymentChannelDisplayAssembler;

    /** 请求患者与就诊患者一致性门控（B.6.1）。 */
    private final PatientEncounterConsistencyGate patientEncounterConsistencyGate;

    /** 收费明细读侧（待结算合计 → 展示余额）。 */
    private final ChargeItemQueryService chargeItemQueryService;

    /**
     * IF-IP-PREP-00：预交金页初始化。
     *
     * @param request 占位请求
     * @return 用户可选支付渠道下拉
     * @throws BusinessException 初始化失败时抛出 EX-IP-PREP-101
     */
    public LoadInpatientPrepayPageInitResponse loadInpatientPrepayPageInit(LoadInpatientPrepayPageInitRequest request) {
        try {
            // Pre：用户可选支付渠道与住院结算/门诊收费口径对齐（ADR-PAY-001）。
            List<OptionDto> paymentChannels = paymentChannelOptionProvider.loadUserSelectableOptions();
            // Asm：Mapper 装配支付渠道下拉。
            return inpatientPrepayMapper.toLoadInpatientPrepayPageInitResponse(paymentChannels);
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new BusinessException(
                InpatientPrepayConstants.EX_IP_PREP_101,
                "初始化失败，请稍后重试");
        }
    }

    /**
     * IF-IP-PREP-01：在院患者队列分页查询。
     *
     * @param request 关键字、入院日与分页
     * @return 可收退预交金患者卡片分页
     * @throws BusinessException 分页非法或查询失败时抛出 EX-IP-PREP-101
     */
    public PagePrepayPatientQueueResponse pagePrepayPatientQueue(PagePrepayPatientQueueRequest request) {
        try {
            pageWindowGate.requirePositivePageWindow(
                request,
                InpatientPrepayConstants.EX_IP_PREP_101,
                "分页参数不合法，请检查页码与每页条数");
            // Pre：装配住院上下文分页入参（清床前可见，含 COMPLETED 待清床；收退门禁见 requirePrepayEligibleEncounterStatus）。
            InpatientEncounterPageQuery query = inpatientPrepayMapper.toInpatientEncounterPageQuery(request);
            PageQueryBinder.bind(request, query);
            Page<InpatientEncounterContextQueryModel> page =
                encounterContextFacade.pageInpatientPrepayQueue(query);
            // Pre：批量加载当前页就诊的个人现金户，避免 N+1。
            Set<Long> encounterIds = new LinkedHashSet<>();
            for (InpatientEncounterContextQueryModel row : page.getContent()) {
                if (row.getEncounterId() != null) {
                    encounterIds.add(row.getEncounterId());
                }
            }
            Map<Long, Account> accountByEncounterId =
                accountQueryService.mapRegistrationAccountsByEncounterIds(encounterIds);
            Map<Long, String> contractNoByEncounterId = new HashMap<>();
            for (Map.Entry<Long, Account> entry : accountByEncounterId.entrySet()) {
                if (entry.getValue() != null && entry.getValue().getContractNo() != null) {
                    contractNoByEncounterId.put(entry.getKey(), entry.getValue().getContractNo());
                }
            }
            // Pre：上下文 patient_id 缺失时从就诊主档回填，避免卡片/门控误判 EX-IP-PREP-102。
            Map<Long, Long> patientIdByEncounterId =
                encounterQueryService.mapPatientIdByEncounterIds(encounterIds);
            for (InpatientEncounterContextQueryModel row : page.getContent()) {
                if (row == null || row.getEncounterId() == null || row.getPatientId() != null) {
                    continue;
                }
                Long patientId = patientIdByEncounterId.get(row.getEncounterId());
                if (patientId != null) {
                    row.setPatientId(patientId);
                }
            }
            Map<Long, InpatientInfoQueueDisplayAssembler.InpatientQueueDisplayOverlay> displayOverlay =
                inpatientInfoQueueDisplayAssembler.buildOverlay(page.getContent(), contractNoByEncounterId);
            Map<Long, BigDecimal> pendingByEncounterId =
                chargeItemQueryService.mapPendingSettlementTotalPriceByEncounterIds(encounterIds);
            Map<Long, BigDecimal> settledByEncounterId =
                chargeItemQueryService.mapSettledTotalPriceByEncounterIds(encounterIds);
            Set<Long> reversedPendingResettleEncounterIds =
                paymentReconciliationQueryService.filterReversedPendingResettleEncounterIds(encounterIds);
            List<PrepayQueueItemDto> content = new ArrayList<>();
            for (InpatientEncounterContextQueryModel row : page.getContent()) {
                Account account = null;
                InpatientInfoQueueDisplayAssembler.InpatientQueueDisplayOverlay rowOverlay = null;
                if (row.getEncounterId() != null) {
                    account = accountByEncounterId.get(row.getEncounterId());
                    rowOverlay = displayOverlay.get(row.getEncounterId());
                }
                BigDecimal pending = BigDecimal.ZERO;
                BigDecimal settled = BigDecimal.ZERO;
                boolean reversedPendingResettle = false;
                if (row != null && row.getEncounterId() != null) {
                    BigDecimal pendingTotal = pendingByEncounterId.get(row.getEncounterId());
                    if (pendingTotal != null) {
                        pending = pendingTotal;
                    }
                    BigDecimal settledTotal = settledByEncounterId.get(row.getEncounterId());
                    if (settledTotal != null) {
                        settled = settledTotal;
                    }
                    if (reversedPendingResettleEncounterIds.contains(row.getEncounterId())) {
                        reversedPendingResettle = true;
                    }
                }
                PrepayQueueItemDto item = inpatientPrepayMapper.toPrepayQueueItemDto(
                    row, account, rowOverlay);
                InpatientAccountBalanceDisplayResult accountBalanceDisplay =
                    InpatientAccountBalanceDisplayAssembler.assemble(
                        account, pending, settled, reversedPendingResettle);
                InpatientAccountBalanceDisplayDtoApplier.apply(item, accountBalanceDisplay);
                content.add(item);
            }
            return inpatientPrepayMapper.toPagePrepayPatientQueueResponse(content, page);
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new BusinessException(
                InpatientPrepayConstants.EX_IP_PREP_101,
                "队列查询失败，请缩小条件或稍后重试");
        }
    }

    /**
     * IF-IP-PREP-02：预交金流水查询。
     *
     * @param request 就诊 ID（可空）
     * @return 住院存款流水列表
     * @throws BusinessException 就诊非住院时抛出 EX-IP-PREP-102
     */
    public ListPrepayPaymentRecordsResponse listPrepayPaymentRecords(ListPrepayPaymentRecordsRequest request) {
        ListPrepayPaymentRecordsResponse response = new ListPrepayPaymentRecordsResponse();
        if (request == null || request.getEncounterId() == null) {
            response.setRecords(Collections.emptyList());
            return response;
        }
        // Pre：须为住院类就诊，否则无法在本页展示预交金流水（含 COMPLETED 待清床只读查阅）。
        EncounterDetailQueryModel encounter = encounterQueryService.mustGetEncounterOfClass(
            request.getEncounterId(),
            EncounterClass.IMP.getCode(),
            InpatientPrepayConstants.EX_IP_PREP_102,
            "就诊非住院或不存在，请刷新并重新选择患者");
        // Pre：按就诊加载全部支付头，再在应用层过滤住院存款（BR-IP-PREP-008）。
        List<PaymentReconciliationQueryModel> allPayments =
            paymentReconciliationQueryService.findPaymentsByEncounter(request.getEncounterId());
        List<PaymentReconciliationQueryModel> hospitalDeposits = new ArrayList<>();
        for (PaymentReconciliationQueryModel payment : allPayments) {
            if (payment == null) {
                continue;
            }
            if (this.isHospitalDepositPayment(payment)) {
                hospitalDeposits.add(payment);
            }
        }
        // Pre：请求携带 patientId 时须与就诊主档一致；未携带时仅按 encounterId 只读展示（IF-IP-PREP-02）。
        if (request.getPatientId() != null) {
            patientEncounterConsistencyGate.requireRequestPatientMatchesEncounter(
                request.getPatientId(),
                encounter.getPatientId(),
                InpatientPrepayConstants.EX_IP_PREP_102,
                "就诊非住院或不存在，请刷新并重新选择患者");
        }
        this.sortPaymentsNewestFirst(hospitalDeposits);
        Map<Long, String> operatorNameById = practitionerQueryService.mapDisplayNameByEntererIds(
            this.collectEntererIds(hospitalDeposits));
        Map<Long, String> payMethodTextByPaymentId = this.loadPayMethodTextIndex(hospitalDeposits);
        List<PrepayPaymentRecordDto> records =
            inpatientPrepayMapper.toPrepayPaymentRecordDtoList(
                hospitalDeposits,
                operatorNameById,
                payMethodTextByPaymentId);
        response.setRecords(records);
        return response;
    }

    /**
     * IF-IP-PREP-03a：预交金扫码预下单（仅落 DRAFT，供 Win 被扫与 prepareConsumerScan）。
     *
     * @param request 与正式收取相同的就诊、金额与支付明细
     * @return 预交金支付草稿 ID
     */
    public PreparePrepayAdvanceDepositResponse preparePrepayAdvanceDeposit(
        CollectPrepayAdvanceDepositRequest request) {
        // Gate/Pre：与收取共用校验（就诊、账户、明细合计）。
        CollectPrepayAdvanceDepositContext ctx = this.buildCollectContext(request);
        // Cmd：仅落 HOSPITAL_DEPOSIT DRAFT，不结算、不调账。
        Long paymentId = paymentReconciliationDomainService.collectAdvanceDeposit(ctx.collectCommand());
        // Asm：返回草稿 ID 供前端 prepareConsumerScan。
        return new PreparePrepayAdvanceDepositResponse().setPaymentDraftId(paymentId);
    }

    /**
     * IF-IP-PREP-03：收取预交金（续收）。
     *
     * @param request 就诊、金额与支付明细
     * @return 支付单 ID 与结算后余额
     * @throws BusinessException 参数/状态/账户/收款失败时抛出 EX-IP-PREP-001～008、004
     */
    public CollectPrepayAdvanceDepositResponse collectPrepayAdvanceDeposit(
        CollectPrepayAdvanceDepositRequest request) {
        CollectPrepayAdvanceDepositContext ctx = this.buildCollectContext(request);
        try {
            Long paymentId = ctx.existingDraftId() != null
                ? ctx.existingDraftId()
                : paymentReconciliationDomainService.collectAdvanceDeposit(ctx.collectCommand());
            thirdPartyPaymentSettleSupport.applyScanBeforeSettle(
                paymentId,
                ctx.collectCommand().getPaymentDetails(),
                request.getClientScanResult(),
                ctx.operatorId(),
                null);
            paymentReconciliationDomainService.refreshPaymentDraftPreSettleAmounts(
                paymentId,
                ctx.collectCommand().getDisplayAmount(),
                ctx.collectCommand().getPaymentDetails());
            SettleInpatientPaymentCommand settleCommand =
                inpatientPrepayMapper.toSettleInpatientPaymentCommand(paymentId);
            paymentReconciliationDomainService.settleInpatientPayment(settleCommand);
            AdjustAdvanceDepositBalanceCommand balanceCmd =
                inpatientPrepayMapper.toCollectBalanceAdjustCommand(
                    request.getPatientId(),
                    request.getEncounterId(),
                    request.getAmount(),
                    ctx.operatorId());
            accountDomainService.adjustAdvanceDepositBalance(balanceCmd);
            BigDecimal displayBalance =
                this.loadDisplayAccountBalance(request.getPatientId(), request.getEncounterId());
            return inpatientPrepayMapper.toCollectPrepayAdvanceDepositResponse(paymentId, displayBalance);
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new BusinessException(
                InpatientPrepayConstants.EX_IP_PREP_004,
                "收取或结算失败，请以流水为准并稍后重试");
        }
    }

    private CollectPrepayAdvanceDepositContext buildCollectContext(CollectPrepayAdvanceDepositRequest request) {
        this.validateCollectGate(request);
        Long operatorId = this.resolveOperatorId(request.getOperatorId());
        EncounterDetailQueryModel encounter = encounterQueryService.mustGetEncounterOfClass(
            request.getEncounterId(),
            EncounterClass.IMP.getCode(),
            InpatientPrepayConstants.EX_IP_PREP_102,
            "就诊非住院或不存在，请刷新并重新选择患者");
        this.requirePrepayEligibleEncounterStatus(encounter.getStatusEnum());
        patientEncounterConsistencyGate.requireRequestPatientMatchesEncounter(
            request.getPatientId(),
            encounter.getPatientId(),
            InpatientPrepayConstants.EX_IP_PREP_001,
            "请选择患者并输入正确金额");
        Account account = accountQueryService.getRegistrationAccount(
            request.getPatientId(),
            request.getEncounterId());
        if (account == null || account.getId() == null) {
            throw new BusinessException(
                InpatientPrepayConstants.EX_IP_PREP_002,
                "请先完成住院登记");
        }
        if (request.getPaymentDraftId() != null) {
            this.requirePrepayCollectDraft(
                request.getPaymentDraftId(),
                request.getEncounterId(),
                request.getAmount());
        }
        CollectAdvanceDepositCommand collectCommand =
            inpatientPrepayMapper.toCollectAdvanceDepositCommand(
                request,
                account.getId(),
                operatorId);
        return new CollectPrepayAdvanceDepositContext(
            operatorId,
            request.getPaymentDraftId(),
            collectCommand);
    }

    private void requirePrepayCollectDraft(Long paymentDraftId, Long encounterId, BigDecimal amount) {
        PaymentReconciliationQueryModel draft = paymentReconciliationQueryService.mustGetPayment(paymentDraftId);
        if (draft.getKindEnum() != PaymentKind.HOSPITAL_DEPOSIT
            || draft.getStatusEnum() != PaymentStatus.DRAFT) {
            throw new BusinessException(
                InpatientPrepayConstants.EX_IP_PREP_004,
                "预交金支付草稿已失效，请重新扫码");
        }
        if (!encounterId.equals(draft.getEncounterId())) {
            throw new BusinessException(
                InpatientPrepayConstants.EX_IP_PREP_004,
                "预交金草稿与就诊不一致");
        }
        if (amount != null && draft.getDisplayAmount() != null
            && amount.compareTo(draft.getDisplayAmount()) != 0) {
            throw new BusinessException(
                InpatientPrepayConstants.EX_IP_PREP_001,
                "收取金额与预下单不一致");
        }
    }

    private record CollectPrepayAdvanceDepositContext(
        Long operatorId,
        Long existingDraftId,
        CollectAdvanceDepositCommand collectCommand) {
    }

    /**
     * IF-IP-PREP-04：退预交金。
     *
     * @param request 就诊、退费金额与退现明细
     * @return 退款支付单 ID 与结算后余额
     * @throws BusinessException 参数/状态/账户/退款失败时抛出 EX-IP-PREP-001～008、005、004
     */
    public RefundPrepayAdvanceDepositResponse refundPrepayAdvanceDeposit(
        RefundPrepayAdvanceDepositRequest request) {
        // Gate：患者与退费主金额合法。
        this.validateRefundGate(request);
        Long operatorId = this.resolveOperatorId(request.getOperatorId());
        // Pre：住院就诊存在且处于可收退状态。
        EncounterDetailQueryModel encounter = encounterQueryService.mustGetEncounterOfClass(
            request.getEncounterId(),
            EncounterClass.IMP.getCode(),
            InpatientPrepayConstants.EX_IP_PREP_102,
            "就诊非住院或不存在，请刷新并重新选择患者");
        this.requirePrepayEligibleEncounterStatus(encounter.getStatusEnum());
        // Pre：卡片患者须与就诊主档一致，防止退错人。
        patientEncounterConsistencyGate.requireRequestPatientMatchesEncounter(
            request.getPatientId(),
            encounter.getPatientId(),
            InpatientPrepayConstants.EX_IP_PREP_001,
            "请选择患者并输入正确金额");
        // Pre：登记个人现金户须已开立。
        Account account = accountQueryService.getRegistrationAccount(
            request.getPatientId(),
            request.getEncounterId());
        if (account == null || account.getId() == null) {
            throw new BusinessException(
                InpatientPrepayConstants.EX_IP_PREP_002,
                "请先完成住院登记");
        }
        BigDecimal refundableNet = InpatientDisplayAccountBalanceAssembler.resolvePersonalCashPrepayNet(account);
        // Pre：退费不得超过预交金收退净值（Account.balance），与展示余额口径分离。
        if (request.getRefundAmount().compareTo(refundableNet) > 0) {
            throw new BusinessException(
                InpatientPrepayConstants.EX_IP_PREP_005,
                "退费金额大于可退预交金，请减小退费金额");
        }
        RefundAdvanceDepositCommand refundCommand =
            inpatientPrepayMapper.toRefundAdvanceDepositCommand(
                request,
                account.getId(),
                operatorId);
        try {
            // Pre：扫码原路退须先 Win 退货并落 BPC 逆向单，失败则不进入本地退预交金。
            if (request.getClientRefundResult() != null) {
                if (request.getOriginalPaymentId() == null) {
                    throw new BusinessException(
                        InpatientPrepayConstants.EX_IP_PREP_001,
                        "扫码原路退须指定原支付单");
                }
                thirdPartyPaymentRefundSupport.applyRefundBeforeLocalRefund(
                    request.getOriginalPaymentId(),
                    request.getClientRefundResult(),
                    operatorId);
            }
            // Cmd：落预交金退款 DRAFT（无 relation_id）。
            Long paymentId = paymentReconciliationDomainService.refundAdvanceDeposit(refundCommand);
            // Cmd：结算为 SUCCESS。
            SettleInpatientPaymentCommand settleCommand =
                inpatientPrepayMapper.toSettleInpatientPaymentCommand(paymentId);
            paymentReconciliationDomainService.settleInpatientPayment(settleCommand);
            // Cmd：个人现金户余额按退费金额扣减。
            AdjustAdvanceDepositBalanceCommand balanceCmd =
                inpatientPrepayMapper.toRefundBalanceAdjustCommand(
                    request.getPatientId(),
                    request.getEncounterId(),
                    request.getRefundAmount(),
                    operatorId);
            accountDomainService.adjustAdvanceDepositBalance(balanceCmd);
            BigDecimal displayBalance =
                this.loadDisplayAccountBalance(request.getPatientId(), request.getEncounterId());
            return inpatientPrepayMapper.toRefundPrepayAdvanceDepositResponse(paymentId, displayBalance);
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new BusinessException(
                InpatientPrepayConstants.EX_IP_PREP_004,
                "退款或结算失败，请以流水为准并稍后重试");
        }
    }

    /**
     * 收取 Gate：主键、主金额与支付明细合计（BR-IP-PREP-003、010）。
     *
     * @param request 收取请求
     * @throws BusinessException 参数或明细不合法
     */
    private void validateCollectGate(CollectPrepayAdvanceDepositRequest request) {
        if (request == null
            || request.getEncounterId() == null
            || request.getPatientId() == null
            || request.getAmount() == null
            || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(
                InpatientPrepayConstants.EX_IP_PREP_001,
                "请选择患者并输入正确金额");
        }
        this.validatePaymentDetailsAgainstMainAmount(
            request.getPaymentDetails(),
            request.getAmount());
    }

    /**
     * 退费 Gate：主键、退费金额与支付明细合计。
     *
     * @param request 退费请求
     * @throws BusinessException 参数或明细不合法
     */
    private void validateRefundGate(RefundPrepayAdvanceDepositRequest request) {
        if (request == null
            || request.getEncounterId() == null
            || request.getPatientId() == null
            || request.getRefundAmount() == null
            || request.getRefundAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(
                InpatientPrepayConstants.EX_IP_PREP_001,
                "请选择患者并输入正确金额");
        }
        this.validatePaymentDetailsAgainstMainAmount(
            request.getPaymentDetails(),
            request.getRefundAmount());
    }

    /**
     * 支付明细非空、各行金额为正且合计等于主金额（§2.6）。
     *
     * @param details    支付明细
     * @param mainAmount 主金额
     * @throws BusinessException 明细缺失或合计不等
     */
    private void validatePaymentDetailsAgainstMainAmount(
        List<PrepayPaymentDetailDto> details,
        BigDecimal mainAmount) {
        if (details == null || details.isEmpty()) {
            throw new BusinessException(
                InpatientPrepayConstants.EX_IP_PREP_008,
                "补全支付方式后再提交");
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (PrepayPaymentDetailDto row : details) {
            if (row == null || row.getAmount() == null) {
                throw new BusinessException(
                    InpatientPrepayConstants.EX_IP_PREP_007,
                    "支付明细合计与主金额不一致，请调整明细分项或主金额");
            }
            if (row.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException(
                    InpatientPrepayConstants.EX_IP_PREP_007,
                    "支付明细合计与主金额不一致，请调整明细分项或主金额");
            }
            row.setPayChannelCode(paymentChannelGate.requireUserSelectablePayChannelCode(
                row.getPayChannelCode(),
                InpatientPrepayConstants.EX_IP_PREP_001,
                "支付方式不合法"));
            sum = sum.add(row.getAmount());
        }
        BigDecimal normalizedSum = sum.setScale(
            InpatientPrepayConstants.PREPAY_AMOUNT_COMPARE_SCALE,
            RoundingMode.HALF_UP);
        BigDecimal normalizedMain = mainAmount.setScale(
            InpatientPrepayConstants.PREPAY_AMOUNT_COMPARE_SCALE,
            RoundingMode.HALF_UP);
        if (normalizedSum.compareTo(normalizedMain) != 0) {
            throw new BusinessException(
                InpatientPrepayConstants.EX_IP_PREP_007,
                "支付明细合计与主金额不一致，请调整明细分项或主金额");
        }
    }

    /**
     * 就诊状态须属于可收退预交金集合（BR-IP-PREP-001）。
     *
     * @param statusEnum 就诊状态 code
     * @throws BusinessException 状态不允许时抛出 EX-IP-PREP-003
     */
    private void requirePrepayEligibleEncounterStatus(String statusEnum) {
        if (this.isPrepayEligibleStatus(statusEnum)) {
            return;
        }
        throw new BusinessException(
            InpatientPrepayConstants.EX_IP_PREP_003,
            "当前就诊状态不允许办理预交金收退");
    }

    /**
     * 判断就诊状态是否允许收退预交金。
     *
     * @param statusEnum 状态 code
     * @return 允许为 true
     */
    private boolean isPrepayEligibleStatus(String statusEnum) {
        if (statusEnum == null) {
            return false;
        }
        if (EncounterStatus.REGISTERED.getCode().equals(statusEnum)) {
            return true;
        }
        if (EncounterStatus.IN_PROGRESS.getCode().equals(statusEnum)) {
            return true;
        }
        if (EncounterStatus.ON_HOLD.getCode().equals(statusEnum)) {
            return true;
        }
        if (EncounterStatus.PENDING.getCode().equals(statusEnum)) {
            return true;
        }
        return false;
    }

    /**
     * 解析操作员：请求优先，否则当前登录用户。
     *
     * @param operatorIdFromRequest 请求中的操作员
     * @return 操作员 ID
     * @throws BusinessException 无法解析时抛出
     */
    private Long resolveOperatorId(Long operatorIdFromRequest) {
        Long operatorId = operatorIdFromRequest;
        if (operatorId == null) {
            operatorId = currentPractitionerResolver.requireOperatorId(
                InpatientPrepayConstants.EX_IP_PREP_001,
                "请选择患者并输入正确金额");
        }
        return operatorId;
    }

    /**
     * 加载患者栏展示余额：库内账户余额 − 待结算。
     *
     * @param patientId   患者 ID
     * @param encounterId 就诊 ID
     * @return 展示可用余额
     */
    private BigDecimal loadDisplayAccountBalance(Long patientId, Long encounterId) {
        Account account = accountQueryService.getRegistrationAccount(patientId, encounterId);
        BigDecimal pending = BigDecimal.ZERO;
        if (encounterId != null) {
            pending = chargeItemQueryService.sumPendingSettlementTotalPriceByEncounterId(encounterId);
        }
        return InpatientDisplayAccountBalanceAssembler.computeDisplayAccountBalance(account, pending);
    }

    /**
     * 是否住院预交金（HOSPITAL_DEPOSIT / 历史 PRE_DEPOSIT 同义）支付头。
     *
     * @param payment 支付读模型
     * @return 属于预交金流水时为 true
     */
    private boolean isHospitalDepositPayment(PaymentReconciliationQueryModel payment) {
        if (payment == null) {
            return false;
        }
        PaymentKind kind = payment.getKindEnum();
        if (kind == null) {
            return false;
        }
        return kind.allowsEmptyChargeItemIds();
    }

    /**
     * 支付流水按 bill_date、paymentId 降序排序（IF-IP-PREP-02）。
     *
     * @param payments 待排序列表
     */
    private void sortPaymentsNewestFirst(List<PaymentReconciliationQueryModel> payments) {
        if (payments == null || payments.size() <= 1) {
            return;
        }
        payments.sort(
            Comparator.comparing(
                PaymentReconciliationQueryModel::getBillDate,
                Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(
                    PaymentReconciliationQueryModel::getId,
                    Comparator.nullsLast(Comparator.reverseOrder())));
    }

    /**
     * 收集支付头经办人 ID 集合。
     *
     * @param payments 支付头列表
     * @return 去重后的 entererId
     */
    private LinkedHashSet<Long> collectEntererIds(List<PaymentReconciliationQueryModel> payments) {
        LinkedHashSet<Long> entererIds = new LinkedHashSet<>();
        if (payments == null) {
            return entererIds;
        }
        for (PaymentReconciliationQueryModel payment : payments) {
            if (payment != null && payment.getEntererId() != null) {
                entererIds.add(payment.getEntererId());
            }
        }
        return entererIds;
    }

    /**
     * 批量解析流水支付方式展示（明细首条 pay_enum → 中文渠道名）。
     *
     * @param payments 住院存款支付头列表
     * @return paymentId → 支付方式展示
     */
    private Map<Long, String> loadPayMethodTextIndex(List<PaymentReconciliationQueryModel> payments) {
        Map<Long, String> textByPaymentId = new HashMap<>();
        if (payments == null || payments.isEmpty()) {
            return textByPaymentId;
        }
        LinkedHashSet<Long> paymentIds = new LinkedHashSet<>();
        for (PaymentReconciliationQueryModel payment : payments) {
            if (payment != null && payment.getId() != null) {
                paymentIds.add(payment.getId());
            }
        }
        Map<Long, String> payEnumByPaymentId =
            paymentReconciliationQueryService.mapFirstPayEnumByPaymentIds(paymentIds);
        for (Map.Entry<Long, String> entry : payEnumByPaymentId.entrySet()) {
            String display = paymentChannelDisplayAssembler.resolvePayChannelDisplay(entry.getValue(), null);
            textByPaymentId.put(entry.getKey(), display);
        }
        return textByPaymentId;
    }
}
