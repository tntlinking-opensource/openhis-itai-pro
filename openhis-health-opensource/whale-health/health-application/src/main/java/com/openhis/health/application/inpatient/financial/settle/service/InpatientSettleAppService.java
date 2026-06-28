package com.openhis.health.application.inpatient.financial.settle.service;

import com.openhis.health.module.common.assembler.financial.PaymentChannelDisplayAssembler;
import com.openhis.health.module.common.assembler.inpatient.InpatientAccountBalanceDisplayDtoApplier;
import com.openhis.health.module.common.assembler.inpatient.InpatientAccountBalanceDisplayAssembler;
import com.openhis.health.module.common.assembler.inpatient.InpatientAccountBalanceDisplayResult;
import com.openhis.health.module.common.assembler.inpatient.InpatientDisplayAccountBalanceAssembler;
import com.openhis.health.module.common.assembler.inpatient.InpatientInfoQueueDisplayAssembler;
import com.openhis.health.module.common.pagination.PageQueryBinder;
import com.openhis.health.module.common.validator.CurrentPractitionerResolver;
import com.openhis.health.module.common.validator.PageWindowGate;
import com.openhis.health.module.common.validator.PatientEncounterConsistencyGate;
import com.openhis.health.module.common.assembler.financial.PaymentChannelOptionProvider;
import com.openhis.health.module.common.validator.PaymentChannelGate;
import com.openhis.health.module.common.support.ThirdPartyPaymentSettleSupport;
import com.openhis.health.application.inpatient.financial.prepay.mapper.InpatientPrepayMapper;
import com.openhis.health.application.inpatient.financial.settle.dto.InpatientSettlePaymentDetailDto;
import com.openhis.health.application.inpatient.financial.settle.dto.InpatientSettleQueueItemDto;
import com.openhis.health.application.inpatient.financial.settle.mapper.InpatientSettleMapper;
import com.openhis.health.application.inpatient.financial.settle.mapper.InpatientSettleReadMapper;
import com.openhis.health.application.inpatient.financial.settle.request.ReverseInpatientSettleRequest;
import com.openhis.health.application.inpatient.financial.settle.request.ConfirmInpatientSettleRequest;
import com.openhis.health.application.inpatient.financial.settle.request.LoadInpatientSettlePageInitRequest;
import com.openhis.health.application.inpatient.financial.settle.request.LoadInpatientSettleSummaryRequest;
import com.openhis.health.application.inpatient.financial.settle.request.LoadSettledInpatientPaymentViewRequest;
import com.openhis.health.application.inpatient.financial.settle.request.PageInpatientSettleQueueRequest;
import com.openhis.health.application.inpatient.financial.settle.request.PreSettleInpatientChargesRequest;
import com.openhis.health.application.inpatient.financial.settle.request.QueryInpatientSettleChargesRequest;
import com.openhis.health.application.inpatient.financial.settle.response.ReverseInpatientSettleResponse;
import com.openhis.health.application.inpatient.financial.settle.response.ConfirmInpatientSettleResponse;
import com.openhis.health.application.inpatient.financial.settle.response.LoadInpatientSettlePageInitResponse;
import com.openhis.health.application.inpatient.financial.settle.response.LoadInpatientSettleSummaryResponse;
import com.openhis.health.application.inpatient.financial.settle.response.LoadSettledInpatientPaymentViewResponse;
import com.openhis.health.application.inpatient.financial.settle.response.PageInpatientSettleQueueResponse;
import com.openhis.health.application.inpatient.financial.settle.response.PreSettleInpatientChargesResponse;
import com.openhis.health.application.inpatient.financial.settle.response.QueryInpatientSettleChargesResponse;
import com.openhis.health.domain.administration.account.command.AdjustAdvanceDepositBalanceCommand;
import com.openhis.health.domain.administration.account.entity.Account;
import com.openhis.health.domain.administration.account.service.AccountDomainService;
import com.openhis.health.domain.administration.account.service.AccountQueryService;
import com.openhis.health.domain.administration.chargeitem.command.BatchChargeItemIdsCommand;
import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitem.query.ChargeItemQueryCommand;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemDomainService;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.openhis.health.domain.administration.encounter.command.CompleteInpatientFinancialSettlementCommand;
import com.openhis.health.domain.administration.encounter.queries.model.EncounterDetailQueryModel;
import com.openhis.health.domain.administration.encounter.service.EncounterDomainService;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.module.querysupport.encounter.EncounterContextFacade;
import com.openhis.health.module.querysupport.financial.InpatientSettleReadLoader;
import com.openhis.health.domain.administration.invoice.command.CancelInvoiceCommand;
import com.openhis.health.domain.administration.invoice.command.CreateDraftInvoiceCommand;
import com.openhis.health.domain.administration.invoice.entity.Invoice;
import com.openhis.health.domain.administration.invoice.query.InvoiceQueryCommand;
import com.openhis.health.domain.administration.invoice.service.InvoiceDomainService;
import com.openhis.health.domain.administration.invoice.service.InvoiceQueryService;
import com.openhis.health.domain.administration.practitioner.service.PractitionerQueryService;
import com.openhis.health.domain.financial.contract.service.ContractQueryService;
import com.openhis.health.domain.financial.contract.queries.model.ContractDetailQueryModel;
import com.openhis.health.domain.financial.paymentreconciliation.command.AbortPreSettlementBeforePaymentCommand;
import com.openhis.health.domain.financial.paymentreconciliation.command.CancelInpatientSettleAsRefundCommand;
import com.openhis.health.domain.financial.paymentreconciliation.command.CollectAdvanceDepositCommand;
import com.openhis.health.domain.financial.paymentreconciliation.command.CompleteInpatientSettleCancelRefundCommand;
import com.openhis.health.domain.financial.paymentreconciliation.command.CreatePaymentDraftFromPreSettleCommand;
import com.openhis.health.domain.financial.paymentreconciliation.command.PaymentDetailItemCommand;
import com.openhis.health.domain.financial.paymentreconciliation.command.PaymentYbSettlementItemCommand;
import com.openhis.health.domain.financial.paymentreconciliation.command.RefundAdvanceDepositCommand;
import com.openhis.health.domain.financial.paymentreconciliation.command.SettleInpatientPaymentCommand;
import com.openhis.health.domain.financial.paymentreconciliation.PaymentYbSettlementStatus;
import com.openhis.health.domain.financial.paymentreconciliation.queries.model.PaymentRecDetailQueryModel;
import com.openhis.health.domain.financial.paymentreconciliation.queries.model.PaymentReconciliationQueryModel;
import com.openhis.health.domain.financial.paymentreconciliation.service.PaymentReconciliationDomainService;
import com.openhis.health.domain.financial.paymentreconciliation.service.PaymentReconciliationQueryService;
import com.openhis.health.domain.financial.contract.entity.Contract;
import com.openhis.health.domain.administration.encounter.queries.InpatientEncounterPageQuery;
import com.openhis.health.domain.administration.encounter.queries.model.InpatientEncounterContextQueryModel;
import com.openhis.health.module.querysupport.product.ProductDefinitionBatchLoader;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.InpatientSettleQueueTab;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.ChargeItemStatus;
import com.openhis.health.domain.share.enums.EncounterClass;
import com.openhis.health.domain.share.enums.EncounterStatus;
import com.openhis.health.domain.share.enums.InvoiceStatus;
import com.openhis.health.domain.share.enums.PaymentChannel;
import com.openhis.health.domain.share.enums.PaymentKind;
import com.openhis.health.domain.share.enums.PaymentStatus;
import com.openhis.health.domain.share.constants.InpatientSettleConstants;
import com.openhis.health.domain.workflow.servicerequest.command.RejectServiceRequestToDraftCommand;
import com.openhis.health.domain.workflow.servicerequest.service.ServiceRequestDomainService;
import com.openhis.health.module.querysupport.financial.InpatientReverseSettlePreSupport;
import com.openhis.whale.core.application.dto.OptionDto;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.health.integration.insurance.inpatient.InpatientChargeInsuranceExtension;
import com.openhis.health.integration.support.einvoice.BossElectronicInvoiceIntegrationSupport;
import com.openhis.health.integration.insurance.inpatient.command.InpatientChargeConfirmCommand;
import com.openhis.health.integration.insurance.inpatient.command.InpatientChargePreSettleCommand;
import com.openhis.health.integration.insurance.inpatient.result.InpatientChargeConfirmResult;
import com.openhis.health.integration.insurance.inpatient.result.InpatientChargePreSettleResult;
import com.openhis.health.integration.payment.dto.ClientScanResultDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 住院结算与反结应用服务（AS-IP-FIN-003 IF-IP-STL-01～07；AS-IP-FIN-004 IF-IP-RVS-01）。
 *
 * <p>职责边界：</p>
 * <ul>
 *   <li>Gate/Pre：结算队列、就诊 IMP 门禁、待结算费用与草稿/金额一致性校验。</li>
 *   <li>Cmd：预结算、正式结算、住院反结、发票草稿与出院财务全结（顺序与 AS-SDD §3 一致）。</li>
 *   <li>Asm：经 {@link InpatientSettleMapper} 装配队列/摘要/费用/结算/反结响应。</li>
 * </ul>
 *
 * <p>接入：{@code AppServiceControllerRegistrar} →
 * {@code POST /api/app/inpatientSettle/{methodName}}；不另写 RestController。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InpatientSettleAppService {

    /** 写路径操作人门控（B.6.1）。 */
    private final CurrentPractitionerResolver currentPractitionerResolver;

    /** 写路径校对人门控（撤销出院嘱，BR-IP-RVS-007）。 */

    /** 分页窗口入站门控（B.10.11）。 */
    private final PageWindowGate pageWindowGate;

    /** 支付渠道入站门控。 */
    private final PaymentChannelGate paymentChannelGate;

    private final ThirdPartyPaymentSettleSupport thirdPartyPaymentSettleSupport;

    private final PaymentChannelOptionProvider paymentChannelOptionProvider;

    /** 患者与就诊一致性门控。 */
    private final PatientEncounterConsistencyGate patientEncounterConsistencyGate;

    /** 就诊读侧（队列、门禁与上下文）。 */
    private final EncounterQueryService encounterQueryService;

    /** 住院就诊上下文门面（结算队列分页读模型）。 */
    private final EncounterContextFacade encounterContextFacade;

    /** 就诊写侧（出院财务全结）。 */
    private final EncounterDomainService encounterDomainService;

    /** 就诊账户读侧。 */
    private final AccountQueryService accountQueryService;

    /** 就诊账户写侧（出院多退少补收退预交金）。 */
    private final AccountDomainService accountDomainService;

    /** 收费明细读侧。 */
    private final ChargeItemQueryService chargeItemQueryService;

    /** 收费明细写侧（预结算/正式结算）。 */
    private final ChargeItemDomainService chargeItemDomainService;

    /** 支付流水读侧。 */
    private final PaymentReconciliationQueryService paymentReconciliationQueryService;

    /** 支付流水写侧。 */
    private final PaymentReconciliationDomainService paymentReconciliationDomainService;

    /** 发票写侧。 */
    private final InvoiceDomainService invoiceDomainService;

    /** 发票读侧。 */
    private final InvoiceQueryService invoiceQueryService;

    /** 合同名称读侧。 */
    private final ContractQueryService contractQueryService;

    /** 产品定义批量加载（费用明细展示名）。 */
    private final ProductDefinitionBatchLoader productDefinitionBatchLoader;

    /** 结算读路径（IF-IP-STL-03/04/07）。 */
    private final InpatientSettleReadLoader inpatientSettleReadLoader;

    /** 结算读路径 Request/Response 桥接。 */
    private final InpatientSettleReadMapper inpatientSettleReadMapper;

    /** 操作员展示名。 */
    private final PractitionerQueryService practitionerQueryService;

    /** 住院结算 DTO/Command 映射。 */
    private final InpatientSettleMapper inpatientSettleMapper;

    /** 住院第三方结算扩展（未装 Module 时为 NoOp）。 */
    private final InpatientChargeInsuranceExtension inpatientChargeInsuranceExtension;

    private final BossElectronicInvoiceIntegrationSupport bossElectronicInvoiceIntegrationSupport;

    /** 预交金 Command 复用（出院退预交金结算收口）。 */
    private final InpatientPrepayMapper inpatientPrepayMapper;

    /** 住院队列展示回填。 */
    private final InpatientInfoQueueDisplayAssembler inpatientInfoQueueDisplayAssembler;

    /** 支付渠道展示名。 */
    private final PaymentChannelDisplayAssembler paymentChannelDisplayAssembler;

    /** 出院类诊疗写侧（反结撤销出院嘱 Cmd）。 */
    private final ServiceRequestDomainService serviceRequestDomainService;

    /** 反结 Pre 读侧与校验（module.querysupport，与护士站共用口径）。 */
    private final InpatientReverseSettlePreSupport inpatientReverseSettlePreSupport;

    /**
     * IF-IP-STL-01：结算页初始化。
     *
     * @param request 占位请求
     * @return 页签、呈现模式与支付渠道下拉
     * @throws BusinessException 初始化失败时抛出 EX-IP-STL-101
     */
    public LoadInpatientSettlePageInitResponse loadInpatientSettlePageInit(LoadInpatientSettlePageInitRequest request) {
        try {
            // Pre：装配队列页签、费用呈现模式与用户可选支付渠道下拉。
            List<OptionDto> queueTabs = new ArrayList<>();
            queueTabs.add(new OptionDto(1L, InpatientSettleQueueTab.PENDING_SETTLE, "有待结算"));
            queueTabs.add(new OptionDto(2L, InpatientSettleQueueTab.SETTLED, "已结算"));
            List<OptionDto> presentationModes = new ArrayList<>();
            presentationModes.add(new OptionDto(
                1L, InpatientSettleConstants.PRESENTATION_MODE_DETAIL, "明细清单"));
            presentationModes.add(new OptionDto(
                2L, InpatientSettleConstants.PRESENTATION_MODE_SUMMARY, "大类汇总"));
            // Pre：用户可选支付渠道与预交金/门诊收费口径对齐。
            List<OptionDto> paymentChannels = paymentChannelOptionProvider.loadUserSelectableOptions();
            // Asm：Mapper 装配页签、呈现模式与渠道下拉。
            return inpatientSettleMapper.toLoadInpatientSettlePageInitResponse(
                queueTabs, presentationModes, paymentChannels);
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new BusinessException(
                InpatientSettleConstants.EX_IP_STL_101,
                "初始化失败，请稍后重试");
        }
    }

    /**
     * IF-IP-STL-02：结算患者队列分页查询。
     *
     * @param request 关键字、入院日、页签与分页
     * @return 结算队列卡片分页
     * @throws BusinessException 分页非法或查询失败时抛出 EX-IP-STL-101
     */
    public PageInpatientSettleQueueResponse pageInpatientSettleQueue(PageInpatientSettleQueueRequest request) {
        try {
            // Gate：分页窗口须为正，否则无法安全翻页查询结算队列。
            pageWindowGate.requirePositivePageWindow(
                request,
                InpatientSettleConstants.EX_IP_STL_101,
                "分页参数不合法，请检查页码与每页条数");
            // Pre：装配结算队列分页入参（settleQueue=true）。
            InpatientEncounterPageQuery query = inpatientSettleMapper.toInpatientEncounterPageQuery(request);
            PageQueryBinder.bind(request, query);
            Page<InpatientEncounterContextQueryModel> page =
                encounterContextFacade.pageInpatientSettleQueue(query);
            Set<Long> encounterIds = new LinkedHashSet<>();
            // 遍历分页结果，收集就诊 ID 供后续批量 Pre 加载。
            for (InpatientEncounterContextQueryModel row : page.getContent()) {
                if (row != null && row.getEncounterId() != null) {
                    encounterIds.add(row.getEncounterId());
                }
            }
            // Pre：上下文 patient_id 为空时从就诊主档回填。
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
            // Pre：批量加载登记账户、待结算合计、合同展示名与队列展示覆盖层。
            Map<Long, Account> accountByEncounterId =
                accountQueryService.mapRegistrationAccountsByEncounterIds(encounterIds);
            Map<Long, String> contractNoByEncounterId = new HashMap<>();
            for (Map.Entry<Long, Account> entry : accountByEncounterId.entrySet()) {
                if (entry.getValue() != null && entry.getValue().getContractNo() != null) {
                    contractNoByEncounterId.put(entry.getKey(), entry.getValue().getContractNo());
                }
            }
            Map<Long, BigDecimal> pendingByEncounterId =
                chargeItemQueryService.mapPendingSettlementTotalPriceByEncounterIds(encounterIds);
            Map<Long, BigDecimal> settledByEncounterId =
                chargeItemQueryService.mapSettledTotalPriceByEncounterIds(encounterIds);
            Set<String> contractNos = new HashSet<>();
            for (String contractNo : contractNoByEncounterId.values()) {
                if (StringUtils.isNotBlank(contractNo)) {
                    contractNos.add(contractNo.trim());
                }
            }
            Map<String, String> contractNameByBusNo =
                contractQueryService.mapContractNameByBusNoIn(contractNos);
            Map<String, Long> contractIdByBusNo =
                contractQueryService.mapContractIdByBusNoIn(contractNos);
            Map<Long, InpatientInfoQueueDisplayAssembler.InpatientQueueDisplayOverlay> displayOverlay =
                inpatientInfoQueueDisplayAssembler.buildOverlay(page.getContent(), contractNoByEncounterId);
            // Pre：将合同展示名回填至队列覆盖层。
            for (Map.Entry<Long, InpatientInfoQueueDisplayAssembler.InpatientQueueDisplayOverlay> entry
                : displayOverlay.entrySet()) {
                InpatientInfoQueueDisplayAssembler.InpatientQueueDisplayOverlay overlay = entry.getValue();
                if (overlay == null) {
                    continue;
                }
                Account account = accountByEncounterId.get(entry.getKey());
                if (account != null && StringUtils.isNotBlank(account.getContractNo())) {
                    String contractDisplay = contractNameByBusNo.get(account.getContractNo().trim());
                    if (StringUtils.isNotBlank(contractDisplay)) {
                        overlay.setContractDisplay(contractDisplay);
                    }
                }
            }
            String tabCode = query.getSettleQueueTab();
            Map<Long, LocalDateTime> lastSettleTimeByEncounterId = new HashMap<>();
            if (InpatientSettleQueueTab.SETTLED.equals(tabCode)) {
                // Pre：已结算页签需展示最近成功住院临床结算时间。
                for (Long encounterId : encounterIds) {
                    List<PaymentReconciliationQueryModel> payments =
                        paymentReconciliationQueryService.findPaymentsByEncounter(encounterId);
                    LocalDateTime maxBillDate = null;
                    // 遍历就诊支付流水，取 INPATIENT_CLINIC + SUCCESS 的最大 bill_date。
                    for (PaymentReconciliationQueryModel payment : payments) {
                        if (payment == null) {
                            continue;
                        }
                        if (payment.getKindEnum() != PaymentKind.INPATIENT_CLINIC) {
                            continue;
                        }
                        if (payment.getStatusEnum() != PaymentStatus.SUCCESS) {
                            continue;
                        }
                        LocalDateTime billDate = payment.getBillDate();
                        if (billDate == null) {
                            continue;
                        }
                        if (maxBillDate == null || billDate.isAfter(maxBillDate)) {
                            maxBillDate = billDate;
                        }
                    }
                    if (maxBillDate != null) {
                        lastSettleTimeByEncounterId.put(encounterId, maxBillDate);
                    }
                }
            }
            Set<Long> reversedPendingResettleEncounterIds =
                paymentReconciliationQueryService.filterReversedPendingResettleEncounterIds(encounterIds);
            // Asm：逐行装配队列卡片（账户余额展示态 + 文案）。
            List<InpatientSettleQueueItemDto> content = new ArrayList<>();
            for (InpatientEncounterContextQueryModel row : page.getContent()) {
                Account account = null;
                InpatientInfoQueueDisplayAssembler.InpatientQueueDisplayOverlay rowOverlay = null;
                BigDecimal pendingSettlement = null;
                BigDecimal settledTotal = null;
                LocalDateTime lastSettleTime = null;
                if (row != null && row.getEncounterId() != null) {
                    account = accountByEncounterId.get(row.getEncounterId());
                    rowOverlay = displayOverlay.get(row.getEncounterId());
                    pendingSettlement = pendingByEncounterId.get(row.getEncounterId());
                    settledTotal = settledByEncounterId.get(row.getEncounterId());
                    lastSettleTime = lastSettleTimeByEncounterId.get(row.getEncounterId());
                }
                boolean reversedPendingResettle = false;
                if (row != null
                    && row.getEncounterId() != null
                    && reversedPendingResettleEncounterIds.contains(row.getEncounterId())) {
                    reversedPendingResettle = true;
                }
                BigDecimal pendingTotal = BigDecimal.ZERO;
                if (pendingSettlement != null) {
                    pendingTotal = pendingSettlement;
                }
                BigDecimal settledSum = BigDecimal.ZERO;
                if (settledTotal != null) {
                    settledSum = settledTotal;
                }
                InpatientAccountBalanceDisplayResult accountBalanceDisplay =
                    InpatientAccountBalanceDisplayAssembler.assemble(
                        account,
                        pendingTotal,
                        settledSum,
                        reversedPendingResettle);
                InpatientSettleQueueItemDto item = inpatientSettleMapper.toInpatientSettleQueueItemDto(
                    row, account, rowOverlay, pendingSettlement, lastSettleTime);
                InpatientAccountBalanceDisplayDtoApplier.apply(item, accountBalanceDisplay);
                if (item != null && account != null && StringUtils.isNotBlank(account.getContractNo())) {
                    // Asm：回填合同主键供前端跳转合同详情。
                    Long contractId = contractIdByBusNo.get(account.getContractNo().trim());
                    if (contractId != null) {
                        item.setContractId(contractId);
                    }
                }
                content.add(item);
            }
            return inpatientSettleMapper.toPageInpatientSettleQueueResponse(content, page);
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new BusinessException(
                InpatientSettleConstants.EX_IP_STL_101,
                "队列查询失败，请缩小条件或稍后重试");
        }
    }

    /**
     * IF-IP-STL-03：结算摘要（待结算患者）。
     *
     * @param request 就诊、患者与可选勾选明细
     * @return 待结算合计、选中合计与展示余额
     * @throws BusinessException 就诊/账户/状态非法时抛出 EX-IP-STL-102/103
     */
    public LoadInpatientSettleSummaryResponse loadInpatientSettleSummary(LoadInpatientSettleSummaryRequest request) {
        // Pre：读路径加载待结算摘要（合计、选中合计、展示余额）。
        // Asm：Mapper 转 Response。
        return inpatientSettleReadMapper.toSummaryResponse(
            inpatientSettleReadLoader.loadInpatientSettleSummary(
                inpatientSettleReadMapper.toSummaryQuery(request)));
    }

    /**
     * IF-IP-STL-04：结算专用费用查询。
     *
     * @param request 就诊、呈现模式与业务日区间
     * @return 待结算明细或大类汇总
     * @throws BusinessException 就诊无效或展示数据不完整时抛出 EX-IP-STL-102/104
     */
    public QueryInpatientSettleChargesResponse queryInpatientSettleCharges(QueryInpatientSettleChargesRequest request) {
        // Pre：读路径按呈现模式查询待结算费用明细或大类汇总。
        // Asm：Mapper 转 Response。
        return inpatientSettleReadMapper.toChargesResponse(
            inpatientSettleReadLoader.queryInpatientSettleCharges(
                inpatientSettleReadMapper.toChargesQuery(request)));
    }

    /**
     * IF-IP-STL-05：住院预结算。
     *
     * @param request 就诊、患者与所选待结算明细
     * @return 支付草稿 ID 与应收合计
     * @throws BusinessException 参数/状态/草稿冲突时抛出 EX-IP-STL-001/102/105
     */
    public PreSettleInpatientChargesResponse preSettleInpatientCharges(PreSettleInpatientChargesRequest request) {
        // Gate：就诊、患者与 chargeItemIds 列表（可为空数组）均须齐备。
        if (request == null
            || request.getEncounterId() == null
            || request.getPatientId() == null
            || request.getChargeItemIds() == null) {
            throw new BusinessException(
                InpatientSettleConstants.EX_IP_STL_001,
                "请选择待结算费用");
        }
        // Gate：写路径须已登录操作员。
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            InpatientSettleConstants.EX_IP_STL_001,
            "请选择待结算费用");
        EncounterDetailQueryModel encounter = encounterQueryService.mustGetEncounterOfClass(
            request.getEncounterId(),
            EncounterClass.IMP.getCode(),
            InpatientSettleConstants.EX_IP_STL_102,
            "重新选择患者");
        // Pre：终态/取消/完诊就诊不允许再发起住院临床结算预结算。
        if (EncounterStatus.COMPLETED.getCode().equals(encounter.getStatusEnum())
            || EncounterStatus.CANCELLED.getCode().equals(encounter.getStatusEnum())
            || EncounterStatus.DISCHARGED.getCode().equals(encounter.getStatusEnum())) {
            throw new BusinessException(
                InpatientSettleConstants.EX_IP_STL_102,
                "当前就诊状态不允许结算");
        }
        patientEncounterConsistencyGate.requireRequestPatientMatchesEncounter(
            request.getPatientId(),
            encounter.getPatientId(),
            InpatientSettleConstants.EX_IP_STL_001,
            "请选择待结算费用");
        Set<Long> allPendingIds = this.loadAllPendingChargeItemIds(request.getEncounterId());
        List<Long> requestedChargeItemIds = request.getChargeItemIds();
        boolean zeroAmountDischargeFull = requestedChargeItemIds.isEmpty();
        if (zeroAmountDischargeFull) {
            // Pre：无费用时仅允许 PENDING 态零元出院全结。
            if (!EncounterStatus.PENDING.getCode().equals(encounter.getStatusEnum())
                || !allPendingIds.isEmpty()) {
                throw new BusinessException(
                    InpatientSettleConstants.EX_IP_STL_001,
                    "请选择待结算费用");
            }
        }
        List<ChargeItem> chargeItems = Collections.emptyList();
        BigDecimal receivableTotal = BigDecimal.ZERO;
        if (!zeroAmountDischargeFull) {
            // Pre：加载所选行并校验归属就诊且均为待结算态（BR-IP-STL-002）。
            ChargeItemQueryCommand loadCmd = new ChargeItemQueryCommand();
            loadCmd.setIds(requestedChargeItemIds);
            chargeItems = chargeItemQueryService.loadChargeItemsByIds(loadCmd);
            this.assertChargeItemsForInpatientSettle(
                chargeItems, requestedChargeItemIds, request.getEncounterId());
            receivableTotal = this.sumChargeItemAmount(chargeItems);
            if (receivableTotal.compareTo(BigDecimal.ZERO) <= 0) {
                // Pre：有明细时应收须为正；零元全结走空列表分支。
                throw new BusinessException(
                    InpatientSettleConstants.EX_IP_STL_001,
                    "请选择待结算费用");
            }
        }
        Set<Long> selectedIds = new HashSet<>(requestedChargeItemIds);
        String settleMode = this.resolveSettleMode(
            encounter.getStatusEnum(),
            selectedIds,
            allPendingIds,
            request.getSettleMode());
        // Pre：找出同就诊下未完成的 INPATIENT_CLINIC 草稿，后续逐条作废（BR-IP-STL-010）。
        List<PaymentReconciliationQueryModel> encounterPayments =
            paymentReconciliationQueryService.findPaymentsByEncounter(request.getEncounterId());
        List<PaymentReconciliationQueryModel> draftPayments = new ArrayList<>();
        // 遍历同就诊流水，筛出未完成的 INPATIENT_CLINIC 草稿待作废。
        for (PaymentReconciliationQueryModel payment : encounterPayments) {
            if (payment == null) {
                continue;
            }
            if (payment.getKindEnum() != PaymentKind.INPATIENT_CLINIC) {
                continue;
            }
            if (payment.getStatusEnum() != PaymentStatus.DRAFT) {
                continue;
            }
            draftPayments.add(payment);
        }
        try {
            // Cmd：作废旧 INPATIENT_CLINIC 草稿，避免并发预结算残留（BR-IP-STL-010）。
            for (PaymentReconciliationQueryModel draft : draftPayments) {
                AbortPreSettlementBeforePaymentCommand abortCmd = new AbortPreSettlementBeforePaymentCommand();
                abortCmd.setPaymentId(draft.getId());
                paymentReconciliationDomainService.abortPreSettlementBeforePayment(abortCmd);
            }
            BatchChargeItemIdsCommand preSettleCmd =
                inpatientSettleMapper.toBatchChargeItemIdsCommand(requestedChargeItemIds, operatorId);
            if (!zeroAmountDischargeFull) {
                // Cmd：收费项预结算校验与事件（状态仍 PENDING_SETTLEMENT）。
                chargeItemDomainService.preSettleInpatientChargeItems(preSettleCmd);
            }
            Account registrationAccount = accountQueryService.getRegistrationAccount(
                request.getPatientId(),
                request.getEncounterId());
            ContractDetailQueryModel contractFromRequest = null;
            if (request.getContractId() != null) {
                contractFromRequest = contractQueryService.findContractById(request.getContractId());
                // 显式传入合同主键但未命中主数据时，直接阻断预结算，避免生成无法追溯合同号的支付草稿。
                if (contractFromRequest == null) {
                    throw new BusinessException(
                        InpatientSettleConstants.EX_IP_STL_001,
                        "费用性质合同不存在");
                }
            }
            // BR-IP-STL-016：从收费项记账账户聚合 contract_no（非预交金登记户）。
            String contractNo = this.resolvePaymentContractBusNoForPreSettle(
                contractFromRequest,
                chargeItems,
                request.getEncounterId());
            // 占位明细按收费项记账账户汇总（自费/医保分桶），禁止一律挂登记个人现金户。
            List<PaymentDetailItemCommand> draftDetailCommands =
                this.buildInpatientPreSettlePaymentDetailDraftLines(request.getEncounterId(), chargeItems);
            CreatePaymentDraftFromPreSettleCommand paymentDraftCommand =
                inpatientSettleMapper.toCreatePaymentDraftFromPreSettleCommand(
                    request,
                    receivableTotal,
                    Collections.emptyList(),
                    operatorId,
                    this.joinIds(requestedChargeItemIds),
                    contractNo);
            paymentDraftCommand.setPaymentDetails(draftDetailCommands);
            // Cmd：落 INPATIENT_CLINIC 支付草稿（DRAFT）及占位明细，供 IF-IP-STL-06 正式结算消费。
            Long paymentDraftId =
                paymentReconciliationDomainService.createPaymentDraftFromPreSettle(paymentDraftCommand);
            PreSettleInpatientChargesResponse response =
                inpatientSettleMapper.toPreSettleInpatientChargesResponse(paymentDraftId, receivableTotal);
            response.setSettleMode(settleMode);
            // Cmd：医保扩展开启时走第三方预结算并刷新草稿 displayAmount。
            this.applyInsurancePreSettleIfNeeded(
                request,
                paymentDraftId,
                response,
                chargeItems,
                settleMode);
            if (!Boolean.TRUE.equals(response.getInsurancePreSettled())) {
                this.applySelfPayChannelPayableOnPreSettle(
                    request.getEncounterId(),
                    receivableTotal,
                    registrationAccount,
                    response);
            }
            return response;
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new BusinessException(
                InpatientSettleConstants.EX_IP_STL_105,
                "重新预结算或作废草稿后再试");
        }
    }

    /**
     * IF-IP-STL-06：确认住院结算。
     *
     * @param request 草稿、收费明细与渠道实付明细
     * @return 支付 ID、发票草稿与是否出院全结
     * @throws BusinessException 草稿/金额/全结门禁失败时抛出 EX-IP-STL-106～109
     */
    public ConfirmInpatientSettleResponse confirmInpatientSettle(ConfirmInpatientSettleRequest request) {
        // Gate：草稿、收费项集合与渠道明细均须齐备，否则无法进入正式结算写路径。
        if (request == null
            || request.getEncounterId() == null
            || request.getPatientId() == null
            || request.getPaymentDraftId() == null
            || request.getChargeItemIds() == null
            || request.getPaymentDetails() == null) {
            throw new BusinessException(
                InpatientSettleConstants.EX_IP_STL_106,
                "重新预结算并核对");
        }
        // Gate：逐条校验用户可选支付渠道 code，防止非法渠道进入结算命令。
        for (InpatientSettlePaymentDetailDto detail : request.getPaymentDetails()) {
            if (detail == null) {
                continue;
            }
            detail.setPayChannelCode(paymentChannelGate.requireUserSelectablePayChannelCode(
                detail.getPayChannelCode(),
                InpatientSettleConstants.EX_IP_STL_106,
                "支付方式不合法"));
        }
        // Gate：写路径须已登录操作员。
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            InpatientSettleConstants.EX_IP_STL_106,
            "重新预结算并核对");
        // Pre：草稿须为 INPATIENT_CLINIC + DRAFT，且就诊锚点与本次提交一致。
        PaymentReconciliationQueryModel payment =
            paymentReconciliationQueryService.mustGetPayment(request.getPaymentDraftId());
        if (payment.getKindEnum() != PaymentKind.INPATIENT_CLINIC
            || payment.getStatusEnum() != PaymentStatus.DRAFT) {
            // Pre：草稿须为住院临床 DRAFT，否则已失效或被其他流程消费。
            throw new BusinessException(
                InpatientSettleConstants.EX_IP_STL_106,
                "支付草稿已失效，请重新预结算");
        }
        if (!request.getEncounterId().equals(payment.getEncounterId())) {
            // Pre：草稿就诊锚点须与本次提交一致，防止跨就诊结算。
            throw new BusinessException(
                InpatientSettleConstants.EX_IP_STL_106,
                "支付草稿与就诊不一致");
        }
        // Pre：草稿从表须非空，否则无法与实收明细交叉校验（BR-IP-STL-005/006）。
        if (paymentReconciliationQueryService.listPaymentRecDetailsByPaymentId(request.getPaymentDraftId()).isEmpty()) {
            throw new BusinessException(
                InpatientSettleConstants.EX_IP_STL_106,
                "支付草稿明细为空，请重新预结算");
        }
        // Pre：请求 chargeItemIds 须与草稿头绑定集合完全一致（BR-IP-STL-005）。
        if (!this.sameChargeItemSet(payment.getChargeItemIds(), request.getChargeItemIds())) {
            throw new BusinessException(
                InpatientSettleConstants.EX_IP_STL_106,
                "收费项与草稿不一致");
        }
        EncounterDetailQueryModel encounter = encounterQueryService.mustGetEncounterOfClass(
            request.getEncounterId(),
            EncounterClass.IMP.getCode(),
            InpatientSettleConstants.EX_IP_STL_106,
            "重新预结算并核对");
        // Pre：拟结项须仍属本就诊且为待结算态，禁止重复结算已结行；零元出院全结允许空列表。
        List<ChargeItem> chargeItems = Collections.emptyList();
        if (!request.getChargeItemIds().isEmpty()) {
            ChargeItemQueryCommand loadCmd = new ChargeItemQueryCommand();
            loadCmd.setIds(request.getChargeItemIds());
            chargeItems = chargeItemQueryService.loadChargeItemsByIds(loadCmd);
            this.assertChargeItemsForInpatientSettle(chargeItems, request.getChargeItemIds(), request.getEncounterId());
        }
        // Pre：与历史成功/退款中流水挂载 chargeItem 求交，命中则拒绝（BR-IP-STL-007）。
        List<PaymentReconciliationQueryModel> overlapping =
            paymentReconciliationQueryService.findPaySuccessOrRefundingOverlappingChargeItems(
                request.getEncounterId(),
                request.getChargeItemIds());
        if (overlapping != null && !overlapping.isEmpty()) {
            // Pre：所选费用已与历史 SUCCESS/REFUNDING 流水绑定，禁止重复结算。
            throw new BusinessException(
                InpatientSettleConstants.EX_IP_STL_106,
                "所选费用已存在成功结算流水");
        }
        BigDecimal expectedChannelPayable = this.resolveExpectedChannelPayable(
            request,
            payment,
            chargeItems);
        BigDecimal submitTotal = this.sumPaymentDetailAmount(request.getPaymentDetails());
        // Pre：渠道实付合计须与少补/医保现金自付一致（自费预交金充足时为 0）。
        if (expectedChannelPayable.setScale(InpatientSettleConstants.SETTLE_AMOUNT_COMPARE_SCALE, RoundingMode.HALF_UP)
            .compareTo(submitTotal.setScale(InpatientSettleConstants.SETTLE_AMOUNT_COMPARE_SCALE, RoundingMode.HALF_UP))
            != 0) {
            throw new BusinessException(
                InpatientSettleConstants.EX_IP_STL_106,
                "渠道实付合计与应收不一致");
        }
        if (expectedChannelPayable.compareTo(BigDecimal.ZERO) > 0 && submitTotal.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(
                InpatientSettleConstants.EX_IP_STL_106,
                "请录入支付明细");
        }
        // Pre：加载就诊下全部待结算行（排除挂号锚点），用于出院全结/中途结算判定。
        Set<Long> allPendingIds = this.loadAllPendingChargeItemIds(request.getEncounterId());
        Set<Long> selectedIds = new HashSet<>(request.getChargeItemIds());
        String insuranceSettleMode = this.resolveInsuranceSettleMode(payment.getId(), request.getYbPreSettleId());
        String settleMode;
        if (insuranceSettleMode != null) {
            settleMode = insuranceSettleMode;
        } else {
            settleMode = this.resolveSettleMode(encounter.getStatusEnum(), selectedIds, allPendingIds, null);
        }
        boolean dischargeFullSettle =
            InpatientSettleConstants.SETTLE_MODE_DISCHARGE_FULL.equals(settleMode);
        patientEncounterConsistencyGate.requireRequestPatientMatchesEncounter(
            request.getPatientId(),
            encounter.getPatientId(),
            InpatientSettleConstants.EX_IP_STL_106,
            "患者标识与就诊不一致");
        BigDecimal receivableTotal = this.sumChargeItemAmount(chargeItems);
        boolean insuranceSettleDraft = this.isInsuranceSettlePaymentDraft(payment.getId());
        // 兼容历史脏草稿：正式结算前若 payment.contract_no 仍为空，从收费项记账账户补写（BR-IP-STL-016）。
        if (StringUtils.isBlank(payment.getContractNo()) && !request.getChargeItemIds().isEmpty()) {
            String contractBusNo = this.resolvePaymentContractBusNoForPreSettle(
                null,
                chargeItems,
                request.getEncounterId());
            paymentReconciliationDomainService.backfillPaymentContractNoIfBlank(
                request.getPaymentDraftId(),
                contractBusNo);
        }
        InpatientChargeConfirmResult ybConfirmResult = null;
        try {
            // Cmd：医保扩展开启时先 confirm 2304，再推进本地支付 SUCCESS。
            ybConfirmResult = this.runInsuranceConfirmIfNeeded(request);
            SettleInpatientPaymentCommand settleCommand =
                inpatientSettleMapper.toSettleInpatientPaymentCommand(request, operatorId);
            if (ybConfirmResult != null) {
                settleCommand.setYbSettlements(this.buildInpatientYbSettlementItems(ybConfirmResult));
            }
            if (!insuranceSettleDraft && expectedChannelPayable.compareTo(BigDecimal.ZERO) > 0) {
                // Cmd ⓪：少补——自动落收预交金流水并增账户（与多退对称，用户无感）。
                this.collectSettleSupplementAdvanceDeposit(
                    request,
                    operatorId,
                    expectedChannelPayable,
                    settleCommand.getBillDate(),
                    request.getClientScanResult());
            }
            if (!insuranceSettleDraft) {
                // Pre：刷新住院结算草稿为预交金冲抵占位明细（按收费项记账账户分桶，非登记户）。
                this.refreshInpatientSettleDraftForPrepayOffset(
                    request.getPaymentDraftId(),
                    request.getEncounterId(),
                    chargeItems,
                    receivableTotal);
            }
            // Cmd ①：支付草稿推进为 SUCCESS。
            Long paymentId = paymentReconciliationDomainService.settleInpatientPayment(settleCommand);
            BatchChargeItemIdsCommand settleChargeCmd =
                inpatientSettleMapper.toBatchChargeItemIdsCommand(request.getChargeItemIds(), operatorId);
            if (!request.getChargeItemIds().isEmpty()) {
                // Cmd ②：选中收费项推进为 SETTLED。
                chargeItemDomainService.settleInpatientChargeItems(settleChargeCmd);
            }
            if (receivableTotal.compareTo(BigDecimal.ZERO) > 0) {
                // Cmd ②b：本批已结费用扣减账户余额（已结算额不再参与展示侧重复扣减）。
                AdjustAdvanceDepositBalanceCommand deductBalanceCmd =
                    inpatientPrepayMapper.toRefundBalanceAdjustCommand(
                        request.getPatientId(),
                        request.getEncounterId(),
                        receivableTotal,
                        operatorId);
                accountDomainService.adjustAdvanceDepositBalance(deductBalanceCmd);
            }
            PaymentReconciliationQueryModel settledPayment =
                paymentReconciliationQueryService.mustGetPayment(paymentId);
            CreateDraftInvoiceCommand invoiceCommand =
                inpatientSettleMapper.toCreateDraftInvoiceCommand(settledPayment, paymentId);
            invoiceCommand.setOperatorId(operatorId);
            // Cmd ③：创建发票草稿（BR-IP-STL-008）。
            Long invoiceDraftId = invoiceDomainService.createDraftInvoiceAfterPayment(invoiceCommand);
            bossElectronicInvoiceIntegrationSupport.afterDraftCreated(invoiceDraftId);
            Long advanceRefundPaymentId = null;
            if (dischargeFullSettle) {
                // Cmd ④：出院全结多退——退库内账户余额盈余（扣本批已结后）。
                advanceRefundPaymentId = this.refundDischargeAdvanceDepositSurplus(
                    request.getEncounterId(),
                    request.getPatientId(),
                    operatorId,
                    settleCommand.getBillDate());
                LocalDateTime settlementTime = settleCommand.getBillDate();
                CompleteInpatientFinancialSettlementCommand completeCmd =
                    inpatientSettleMapper.toCompleteInpatientFinancialSettlementCommand(
                        request.getEncounterId(),
                        settlementTime,
                        operatorId);
                // Cmd ⑤：出院全结时就诊 PENDING→COMPLETED（条件执行）。
                encounterDomainService.completeInpatientFinancialSettlement(completeCmd);
            }
            return inpatientSettleMapper.toConfirmInpatientSettleResponse(
                paymentId,
                invoiceDraftId,
                dischargeFullSettle,
                advanceRefundPaymentId,
                ybConfirmResult == null ? null : ybConfirmResult.getSettlementId());
        } catch (BusinessException ex) {
            if (ErrorCodeConstants.ENCOUNTER_INVALID_STATUS.equals(ex.getErrorCode())) {
                throw new BusinessException(
                    InpatientSettleConstants.EX_IP_STL_109,
                    "就诊状态已变更，请刷新后重试");
            }
            throw ex;
        } catch (OptimisticLockingFailureException ex) {
            throw new BusinessException(
                InpatientSettleConstants.EX_IP_STL_109,
                "支付结算失败，请以流水为准重试");
        } catch (RuntimeException ex) {
            throw new BusinessException(
                InpatientSettleConstants.EX_IP_STL_109,
                "支付结算失败，请以流水为准重试");
        }
    }

    /**
     * IF-IP-STL-07：已结算单查阅。
     *
     * @param request 就诊、患者与可选指定结算单
     * @return 结算单快照与费用明细
     * @throws BusinessException 无结算记录时抛出 EX-IP-STL-110
     */
    public LoadSettledInpatientPaymentViewResponse loadSettledInpatientPaymentView(
        LoadSettledInpatientPaymentViewRequest request) {
        // Pre：读路径加载已结算支付单快照与关联费用明细（含历史 NET / reverseEnabled）。
        // Asm：Mapper 转 Response。
        return inpatientSettleReadMapper.toPaymentViewResponse(
            inpatientSettleReadLoader.loadSettledInpatientPaymentView(
                inpatientSettleReadMapper.toPaymentViewQuery(request)));
    }

    /**
     * IF-IP-RVS-01：住院反结（收银台已结算 Tab）。
     *
     * @param request 正向成功支付单与三主键
     * @return 逆向反结支付单 ID 与成功标识
     * @throws BusinessException EX-IP-RVS-001～005
     */
    public ReverseInpatientSettleResponse reverseInpatientSettle(ReverseInpatientSettleRequest request) {
        // Gate：收银台入口禁止 DISCHARGED 本页反结（BR-IP-RVS-008）；召回内嵌走 RecallReverseSettleSupport。
        return this.executeReverseInpatientSettle(request, false);
    }

    /**
     * 住院反结编排序列内核（IF-IP-RVS-01）。
     *
     * @param request         反结请求
     * @param allowDischarged 为 true 时跳过 DISCHARGED 本页拒绝（保留扩展；召回内嵌走 {@code RecallReverseSettleSupport}）
     * @return 反结响应
     * @throws BusinessException EX-IP-RVS-001～005
     */
    private ReverseInpatientSettleResponse executeReverseInpatientSettle(
        ReverseInpatientSettleRequest request,
        boolean allowDischarged) {
        // Gate：反结须已登录操作员与校对人（撤销出院嘱）。
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            InpatientSettleConstants.EX_IP_RVS_001,
            "请先登录");
        Long performerCheckId = currentPractitionerResolver.requirePractitionerId(
            InpatientSettleConstants.EX_IP_RVS_001,
            "请先登录");
        LocalDateTime checkTime = LocalDateTime.now();
        // Pre：加载原正向成功结算单，非 SUCCESS 或非住院结算类型则拒绝。
        PaymentReconciliationQueryModel originalPayment =
            paymentReconciliationQueryService.mustGetPayment(request.getPaymentId());
        if (!PaymentStatus.SUCCESS.equals(originalPayment.getStatusEnum())) {
            // Pre：仅正向 SUCCESS 单可反结，REFUNDED 原单须走历史展示不可写。
            throw new BusinessException(
                InpatientSettleConstants.EX_IP_RVS_001,
                "请刷新并重新选择结算单");
        }
        if (!PaymentKind.INPATIENT_CLINIC.equals(originalPayment.getKindEnum())) {
            // Pre：须为住院临床出院全结单。
            throw new BusinessException(
                InpatientSettleConstants.EX_IP_RVS_001,
                "请刷新并重新选择结算单");
        }
        if (originalPayment.getRelationId() != null) {
            // Pre：逆向单本身不可作为反结原单。
            throw new BusinessException(
                InpatientSettleConstants.EX_IP_RVS_001,
                "请刷新并重新选择结算单");
        }
        if (!request.getEncounterId().equals(originalPayment.getEncounterId())
            || !request.getPatientId().equals(originalPayment.getPatientId())) {
            // Pre：支付单须与请求就诊/患者一致。
            throw new BusinessException(
                InpatientSettleConstants.EX_IP_RVS_001,
                "请刷新并重新选择结算单");
        }
        EncounterDetailQueryModel encounter = encounterQueryService.mustGetEncounterOfClass(
            request.getEncounterId(),
            EncounterClass.IMP.getCode(),
            InpatientSettleConstants.EX_IP_RVS_001,
            "就诊记录不是住院类别，不能反结");
        // Pre：已清床离院患者禁止收银台直接反结，须走护士站召回内嵌。
        if (!allowDischarged
            && EncounterStatus.DISCHARGED.getCode().equals(encounter.getStatusEnum())) {
            throw new BusinessException(
                InpatientSettleConstants.EX_IP_RVS_003,
                "已清床患者请至护士站办理召回");
        }
        patientEncounterConsistencyGate.requireRequestPatientMatchesEncounter(
            request.getPatientId(),
            encounter.getPatientId(),
            InpatientSettleConstants.EX_IP_RVS_001,
            "患者标识与就诊不一致");
        // Pre：一笔结算仅允许一次反结，已存在逆向关联则拒绝。
        if (Boolean.TRUE.equals(
            paymentReconciliationQueryService.hasRefundByRelationId(request.getPaymentId()).getExists())) {
            throw new BusinessException(
                InpatientSettleConstants.EX_IP_RVS_001,
                "请刷新并重新选择结算单");
        }
        List<Long> paymentBoundChargeItemIds =
            inpatientReverseSettlePreSupport.parseBoundChargeItemIds(originalPayment.getChargeItemIds());
        ChargeItemQueryCommand loadCmd = new ChargeItemQueryCommand();
        loadCmd.setIds(paymentBoundChargeItemIds);
        List<ChargeItem> paymentBoundChargeItems = chargeItemQueryService.loadChargeItemsByIds(loadCmd);
        // Pre：原单绑定费用须均为 SETTLED，否则数据已变化。
        inpatientReverseSettlePreSupport.assertAllChargeItemsSettledForReverse(
            paymentBoundChargeItems, paymentBoundChargeItemIds, request.getEncounterId());
        // Pre：反结回退本就诊全部已结算行（本期无中途结算；避免绑定串遗漏导致余额虚高）。
        List<Long> chargeItemIdsToRevert = chargeItemQueryService.listChargeItemIdsByEncounterIdAndStatusEnum(
            request.getEncounterId(), ChargeItemStatus.SETTLED.getCode());
        if (chargeItemIdsToRevert.isEmpty() && !paymentBoundChargeItemIds.isEmpty()) {
            chargeItemIdsToRevert = paymentBoundChargeItemIds;
        }
        // Pre：本期无中途结算，须存在可退回或已为草稿的医生开立出院嘱（category=34）。
        Long dischargeServiceRequestId =
            inpatientReverseSettlePreSupport.resolveRejectableDischargeServiceRequestId(request.getEncounterId());
        if (dischargeServiceRequestId == null
            && !inpatientReverseSettlePreSupport.hasDoctorDischargeRequestInDraft(request.getEncounterId())) {
            // Pre：既无可退回出院嘱、也无 DRAFT 出院嘱，拒绝反结。
            throw new BusinessException(
                InpatientSettleConstants.EX_IP_RVS_004,
                "未找到可撤回的出院医嘱");
        }
        List<PaymentRecDetailQueryModel> paymentDetails =
            paymentReconciliationQueryService.listPaymentRecDetailsByPaymentId(request.getPaymentId());
        CancelInpatientSettleAsRefundCommand cancelCommand =
            inpatientSettleMapper.toCancelInpatientSettleAsRefundCommand(
                originalPayment, paymentDetails, operatorId);
        try {
            // Cmd：新建逆向反结支付单（REFUND_PROCESSING）。
            Long refundPaymentId = paymentReconciliationDomainService.cancelInpatientSettleAsRefund(cancelCommand);
            if (!chargeItemIdsToRevert.isEmpty()) {
                BatchChargeItemIdsCommand revertChargeCmd =
                    inpatientSettleMapper.toBatchChargeItemIdsCommand(chargeItemIdsToRevert, operatorId);
                // Cmd：本就诊已结算费用 SETTLED → PENDING_SETTLEMENT，恢复可再次结算。
                chargeItemDomainService.revertInpatientSettledChargeItems(revertChargeCmd);
            }
            // 出院结算（kind_enum=INPATIENT_CLINIC）反结不回冲账户余额；中途结算反结上线后按支付大类回冲本批已结扣款。
            this.creditAccountBalanceOnMidSettleReverseIfNeeded(originalPayment, chargeItemIdsToRevert, operatorId);
            // Cmd：仅 COMPLETED 未清床时回退就诊财务态 PENDING；DISCHARGED 由召回路径处理。
            if (EncounterStatus.COMPLETED.getCode().equals(encounter.getStatusEnum())) {
                encounterDomainService.revertInpatientFinancialSettlement(request.getEncounterId());
            }
            // Cmd：作废草稿或冲红已开具票据。
            this.voidInvoiceLinkedToPaymentOnCancel(request.getPaymentId(), operatorId);
            if (dischargeServiceRequestId != null) {
                RejectServiceRequestToDraftCommand rejectCommand =
                    inpatientSettleMapper.toRejectDischargeServiceRequestToDraftCommand(
                        dischargeServiceRequestId, operatorId, performerCheckId, checkTime);
                // Cmd：出院类诊疗退回草稿；已为 DRAFT 时幂等跳过。
                serviceRequestDomainService.rejectServiceRequestToDraft(rejectCommand);
            }
            CompleteInpatientSettleCancelRefundCommand completeCommand =
                inpatientSettleMapper.toCompleteInpatientSettleCancelRefundCommand(
                    request.getPaymentId(),
                    refundPaymentId,
                    null);
            // Cmd：原单与逆向单收口 REFUNDED，发布住院结算取消事件。
            paymentReconciliationDomainService.completeInpatientSettleCancelRefund(completeCommand);
            // Asm：返回逆向单 ID 与成功标识。
            return inpatientSettleMapper.toReverseInpatientSettleResponse(refundPaymentId);
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new BusinessException(
                InpatientSettleConstants.EX_IP_RVS_005,
                "反结失败，请以流水为准后重试");
        }
    }

    /**
     * 校验收费项归属就诊且均为待结算态。
     *
     * @param chargeItems     已加载收费项
     * @param requestedIds    请求 ID 列表
     * @param encounterId     就诊 ID
     * @throws BusinessException 缺失或非待结算/非本就诊
     */
    private void assertChargeItemsForInpatientSettle(
        List<ChargeItem> chargeItems,
        List<Long> requestedIds,
        Long encounterId) {
        Map<Long, ChargeItem> loadedById = new HashMap<>();
        if (chargeItems != null) {
            for (ChargeItem chargeItem : chargeItems) {
                if (chargeItem != null && chargeItem.getId() != null) {
                    loadedById.put(chargeItem.getId(), chargeItem);
                }
            }
        }
        // 逐条校验请求 ID 在库中存在、归属本就诊且为 PENDING_SETTLEMENT。
        for (Long requestedId : requestedIds) {
            ChargeItem chargeItem = loadedById.get(requestedId);
            if (chargeItem == null) {
                // Pre：请求 ID 在库中缺失，禁止部分结算。
                throw new BusinessException(
                    InpatientSettleConstants.EX_IP_STL_001,
                    "请选择待结算费用");
            }
            if (!encounterId.equals(chargeItem.getEncounterId())) {
                // Pre：费用须归属当前就诊。
                throw new BusinessException(
                    InpatientSettleConstants.EX_IP_STL_001,
                    "所选费用不属于当前就诊");
            }
            if (!ChargeItemStatus.PENDING_SETTLEMENT.getCode().equals(chargeItem.getStatusEnum())) {
                // Pre：仅待结算态可进入预结算/正式结算。
                throw new BusinessException(
                    InpatientSettleConstants.EX_IP_STL_001,
                    "所选费用含非待结算项");
            }
        }
    }

    /**
     * 出院全结多退：库内账户余额仍有盈余时，落退预交金流水并扣减账户余额至 0。
     *
     * <p>调用前须已完成本批费用 {@code SETTLED} 与账户余额扣减；退费额 = 扣减后 {@code adm_account.balance}。</p>
     *
     * @param encounterId         就诊 ID
     * @param patientId           患者 ID
     * @param operatorId          操作员 ID
     * @param settlementBillDate  结算账单时间
     * @return 退预交金支付单 ID；无需退费时 null
     */
    private Long refundDischargeAdvanceDepositSurplus(
        Long encounterId,
        Long patientId,
        Long operatorId,
        LocalDateTime settlementBillDate) {
        Account account = accountQueryService.getRegistrationAccount(patientId, encounterId);
        if (account == null || account.getId() == null) {
            // Pre：出院全结多退须已有登记账户。
            throw new BusinessException(
                InpatientSettleConstants.EX_IP_STL_103,
                "请先完成住院登记");
        }
        BigDecimal refundAmount = InpatientDisplayAccountBalanceAssembler.resolveAccountBalance(account)
            .setScale(InpatientSettleConstants.SETTLE_AMOUNT_COMPARE_SCALE, RoundingMode.HALF_UP);
        if (refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
            // 扣本批已结后账户无盈余，无需退预交金。
            return null;
        }
        RefundAdvanceDepositCommand refundCommand = inpatientSettleMapper.toDischargeDepositRefundCommand(
            encounterId,
            patientId,
            account.getId(),
            refundAmount,
            operatorId);
        // Cmd：落退预交金 DRAFT（HOSPITAL_DEPOSIT / UN_PAY），与预交金工作台口径一致。
        Long refundPaymentId = paymentReconciliationDomainService.refundAdvanceDeposit(refundCommand);
        SettleInpatientPaymentCommand refundSettleCommand =
            inpatientPrepayMapper.toSettleInpatientPaymentCommand(refundPaymentId);
        if (settlementBillDate != null) {
            refundSettleCommand.setBillDate(settlementBillDate);
        }
        paymentReconciliationDomainService.settleInpatientPayment(refundSettleCommand);
        AdjustAdvanceDepositBalanceCommand balanceCmd = inpatientPrepayMapper.toRefundBalanceAdjustCommand(
            patientId,
            encounterId,
            refundAmount,
            operatorId);
        accountDomainService.adjustAdvanceDepositBalance(balanceCmd);
        return refundPaymentId;
    }

    /**
     * 中途结算反结时回冲账户余额；出院全结（{@link PaymentKind#INPATIENT_CLINIC}）反结不回冲。
     *
     * <p>本期仅有出院结算大类；中途结算支付大类上线后在本方法内按 {@code kind_enum} 分支回冲。</p>
     *
     * @param originalPayment        正向结算支付单
     * @param revertedChargeItemIds  回退为待结算的收费项 ID
     * @param operatorId             操作员 ID
     */
    private void creditAccountBalanceOnMidSettleReverseIfNeeded(
        PaymentReconciliationQueryModel originalPayment,
        List<Long> revertedChargeItemIds,
        Long operatorId) {
        if (originalPayment == null) {
            return;
        }
        if (PaymentKind.INPATIENT_CLINIC.equals(originalPayment.getKindEnum())) {
            // 出院全结反结：多退/少补已发生，库内余额保持 0 或欠费展示，待再结时由待结算体现。
            return;
        }
        if (revertedChargeItemIds == null || revertedChargeItemIds.isEmpty()) {
            return;
        }
        if (originalPayment.getPatientId() == null || originalPayment.getEncounterId() == null) {
            return;
        }
        ChargeItemQueryCommand loadCmd = new ChargeItemQueryCommand();
        loadCmd.setIds(revertedChargeItemIds);
        List<ChargeItem> revertedItems = chargeItemQueryService.loadChargeItemsByIds(loadCmd);
        BigDecimal revertedTotal = this.sumChargeItemAmount(revertedItems);
        if (revertedTotal.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        AdjustAdvanceDepositBalanceCommand creditCmd = inpatientPrepayMapper.toCollectBalanceAdjustCommand(
            originalPayment.getPatientId(),
            originalPayment.getEncounterId(),
            revertedTotal,
            operatorId);
        accountDomainService.adjustAdvanceDepositBalance(creditCmd);
    }

    /**
     * 收费项 total_price 求和。
     *
     * @param chargeItems 收费项列表
     * @return 合计金额
     */
    private BigDecimal sumChargeItemAmount(List<ChargeItem> chargeItems) {
        BigDecimal sum = BigDecimal.ZERO;
        if (chargeItems == null) {
            return sum;
        }
        // 累加各收费项 total_price 作为应收基准。
        for (ChargeItem chargeItem : chargeItems) {
            if (chargeItem == null || chargeItem.getTotalPrice() == null) {
                continue;
            }
            sum = sum.add(chargeItem.getTotalPrice());
        }
        return sum;
    }

    /**
     * 支付明细金额求和。
     *
     * @param details 支付明细
     * @return 合计
     */
    private BigDecimal sumPaymentDetailAmount(List<InpatientSettlePaymentDetailDto> details) {
        BigDecimal sum = BigDecimal.ZERO;
        if (details == null) {
            return sum;
        }
        // 累加渠道实付明细 amount 供与应收交叉校验。
        for (InpatientSettlePaymentDetailDto detail : details) {
            if (detail == null || detail.getAmount() == null) {
                continue;
            }
            sum = sum.add(detail.getAmount());
        }
        return sum;
    }

    /**
     * 收费项 ID 列表 → 逗号串。
     *
     * @param ids ID 列表
     * @return 逗号分隔串
     */
    private String joinIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return GeneralSharedConstants.EMPTY_STRING;
        }
        StringBuilder builder = new StringBuilder();
        for (Long id : ids) {
            if (id == null) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(',');
            }
            builder.append(id);
        }
        return builder.toString();
    }

    /**
     * 逗号串 → 收费项 ID 列表。
     *
     * @param raw 持久化串
     * @return ID 列表
     */
    private List<Long> parseIds(String raw) {
        List<Long> ids = new ArrayList<>();
        if (StringUtils.isBlank(raw)) {
            return ids;
        }
        String[] parts = raw.split(",");
        for (String part : parts) {
            if (StringUtils.isBlank(part)) {
                continue;
            }
            ids.add(Long.valueOf(part.trim()));
        }
        return ids;
    }

    /**
     * 草稿绑定收费项与请求集合是否一致。
     *
     * @param draftIds   草稿侧逗号串
     * @param requestIds 请求 ID 列表
     * @return true 表示一致
     */
    private boolean sameChargeItemSet(String draftIds, List<Long> requestIds) {
        List<Long> parsed = this.parseIds(draftIds);
        if (requestIds == null || parsed.size() != requestIds.size()) {
            return false;
        }
        Map<Long, Boolean> requestIdMap = new HashMap<>();
        for (Long requestId : requestIds) {
            requestIdMap.put(requestId, Boolean.TRUE);
        }
        for (Long parsedId : parsed) {
            if (!requestIdMap.containsKey(parsedId)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 加载就诊下全部待结算收费项 ID（排除挂号锚点 service_table）。
     *
     * @param encounterId 就诊 ID
     * @return 待结算 ID 集合；无则空集
     */
    private Set<Long> loadAllPendingChargeItemIds(Long encounterId) {
        ChargeItemQueryCommand pendingQuery = new ChargeItemQueryCommand();
        pendingQuery.setEncounterId(encounterId);
        List<String> pendingStatusIn = new ArrayList<>();
        pendingStatusIn.add(ChargeItemStatus.PENDING_SETTLEMENT.getCode());
        pendingQuery.setStatusEnumIn(pendingStatusIn);
        pendingQuery.setExcludedServiceTable(TableNameConstants.ADM_HEALTHCARE_SERVICE);
        List<ChargeItem> allPending = chargeItemQueryService.listChargeItemsByEncounterId(pendingQuery);
        Set<Long> allPendingIds = new HashSet<>();
        for (ChargeItem pendingItem : allPending) {
            if (pendingItem != null && pendingItem.getId() != null) {
                allPendingIds.add(pendingItem.getId());
            }
        }
        return allPendingIds;
    }

    /**
     * 判定本次结算模式：出院全结 / 中途结算（BR-IP-STL-011～014）。
     *
     * @param encounterStatus         就诊状态
     * @param selectedChargeItemIds   用户选中 ID
     * @param allPendingChargeItemIds 就诊全部待结算 ID
     * @param requestSettleMode       请求显式指定模式（可 null）
     * @return {@link InpatientSettleConstants#SETTLE_MODE_DISCHARGE_FULL} 或 {@code MID}
     * @throws BusinessException 全选/部分选与就诊态冲突时
     */
    private String resolveSettleMode(
        String encounterStatus,
        Set<Long> selectedChargeItemIds,
        Set<Long> allPendingChargeItemIds,
        String requestSettleMode) {
        boolean fullSelection = selectedChargeItemIds.equals(allPendingChargeItemIds);
        if (InpatientSettleConstants.SETTLE_MODE_MID.equals(requestSettleMode)) {
            // 显式中途结算：PENDING 态全选则拒绝（应走出院全结）。
            if (fullSelection && EncounterStatus.PENDING.getCode().equals(encounterStatus)) {
                throw new BusinessException(
                    InpatientSettleConstants.INS_IP_016,
                    "已全选待结算费用，请使用出院全结");
            }
            if (selectedChargeItemIds.isEmpty()) {
                throw new BusinessException(
                    InpatientSettleConstants.EX_IP_STL_001,
                    "请选择待结算费用");
            }
            return InpatientSettleConstants.SETTLE_MODE_MID;
        }
        if (EncounterStatus.PENDING.getCode().equals(encounterStatus)) {
            // PENDING 态须全选待结算，否则只能中途结算。
            if (!fullSelection) {
                throw new BusinessException(
                    InpatientSettleConstants.INS_IP_001,
                    "请全选待结算费用或改为中途结算");
            }
            return InpatientSettleConstants.SETTLE_MODE_DISCHARGE_FULL;
        }
        // IN_PROGRESS/COMPLETED 等：全选须先护士出院；部分选为中途结算。
        if (fullSelection) {
            throw new BusinessException(
                InpatientSettleConstants.EX_IP_STL_108,
                "请先完成护士出院或改为中途结算");
        }
        return InpatientSettleConstants.SETTLE_MODE_MID;
    }

    /**
     * 医保第三方预结算（2303）：刷新草稿 displayAmount 与结算组成字段。
     *
     * @param request        预结算请求（含读卡/合同信息）
     * @param paymentDraftId 本地支付草稿 ID
     * @param response       待回填的预结算响应
     * @param chargeItems    所选收费项
     * @param settleMode     出院全结/中途
     */
    private void applyInsurancePreSettleIfNeeded(
        PreSettleInpatientChargesRequest request,
        Long paymentDraftId,
        PreSettleInpatientChargesResponse response,
        List<ChargeItem> chargeItems,
        String settleMode) {
        if (!inpatientChargeInsuranceExtension.isEnabled()) {
            // 未启用医保扩展：纯自费口径。
            response.setSettlementComposition("SELF_PAY_ONLY");
            return;
        }
        Long insuranceContractId =
            this.resolveInsuranceContractId(request, chargeItems, request.getEncounterId());
        if (insuranceContractId == null) {
            // 无医保合同：按自费处理。
            response.setSettlementComposition("SELF_PAY_ONLY");
            return;
        }
        if (!inpatientChargeInsuranceExtension.supportsThirdPartyContract(insuranceContractId)) {
            // 合同非第三方医保：按自费处理。
            response.setSettlementComposition("SELF_PAY_ONLY");
            return;
        }
        if (StringUtils.isBlank(request.getPsnNo())) {
            // Gate：医保预结算须先读卡获取人员编号。
            throw new BusinessException(
                InpatientSettleConstants.EX_IP_STL_001,
                "医保预结算须先读卡获取人员编号");
        }
        InpatientChargePreSettleCommand command = new InpatientChargePreSettleCommand();
        command.setEncounterId(request.getEncounterId());
        command.setChargeItemIds(request.getChargeItemIds());
        command.setContractId(insuranceContractId);
        command.setPaymentDraftId(paymentDraftId);
        command.setPayerCertType(request.getYbMediumType());
        command.setPayerCertNo(request.getYbCardInfo());
        command.setInsuredPersonNo(request.getPsnNo());
        command.setInsuplcAdmdvs(request.getInsuplcAdmdvs());
        command.setInsutype(request.getInsutype());
        command.setSettleMode(settleMode);
        command.setAcctUsedFlag(request.getAcctUsedFlag());
        command.setPsnSetlway(request.getPsnSetlway());
        // Cmd：调用医保 2303 预结算，获取基金/个账/现金分摊。
        InpatientChargePreSettleResult ybResult = inpatientChargeInsuranceExtension.preSettle(command);
        response.setInsurancePreSettled(Boolean.TRUE);
        response.setSettlementComposition("INSURANCE_ONLY");
        response.setYbPreSettleId(ybResult.getPreSettleId());
        response.setYbMedfeeSumamt(ybResult.getMedfeeSumamt());
        response.setYbFundPaySumamt(ybResult.getFundPaySumamt());
        response.setYbAcctPay(ybResult.getAcctPay());
        response.setYbPsnCashPay(ybResult.getPsnCashPay());
        if (ybResult.getPsnCashPay() != null) {
            response.setDisplayAmount(ybResult.getPsnCashPay());
        }
        BigDecimal displayAmount;
        if (response.getDisplayAmount() != null) {
            displayAmount = response.getDisplayAmount();
        } else {
            displayAmount = response.getReceivableTotal();
        }
        List<PaymentDetailItemCommand> draftLines = this.buildInpatientInsuranceCashPayDraftLines(
            request.getEncounterId(),
            chargeItems,
            displayAmount);
        // Cmd：用医保现金自付额刷新本地支付草稿 displayAmount 与占位明细（账户锚定收费项记账户）。
        paymentReconciliationDomainService.refreshPaymentDraftPreSettleAmounts(
            paymentDraftId,
            displayAmount,
            draftLines);
        // 纯医保预结算：补写医保合同 bus_no，供日结费用性质筛选。
        paymentReconciliationDomainService.backfillPaymentContractNoIfBlank(
            paymentDraftId,
            this.resolveContractBusNoById(insuranceContractId));
        if (response.getDisplayAmount() != null) {
            response.setPreviewText("患者实付：" + response.getDisplayAmount() + " 元");
        }
    }

    /**
     * 按收费项记账账户汇总预结算占位明细（自费/医保分桶，对齐门诊 IF-OPC-06）。
     *
     * @param encounterId 就诊主键
     * @param chargeItems 本次预结算收费项
     * @return 非空占位明细列表
     */
    private List<PaymentDetailItemCommand> buildInpatientPreSettlePaymentDetailDraftLines(
        Long encounterId,
        List<ChargeItem> chargeItems) {
        Long defaultSelfPayAccountId = accountQueryService.getSelfPayAccount(encounterId);
        Map<Long, BigDecimal> amountByAccountId = new LinkedHashMap<>();
        for (ChargeItem chargeLine : chargeItems) {
            if (chargeLine == null) {
                continue;
            }
            Long accountId = chargeLine.getAccountId();
            if (accountId == null) {
                accountId = defaultSelfPayAccountId;
            }
            if (accountId == null) {
                throw new BusinessException(
                    InpatientSettleConstants.EX_IP_STL_001,
                    "收费项未绑定结算账户且就诊未开立默认自费账户，无法生成支付草稿");
            }
            BigDecimal linePrice = chargeLine.getTotalPrice();
            if (linePrice == null) {
                linePrice = BigDecimal.ZERO;
            }
            BigDecimal prior = amountByAccountId.get(accountId);
            if (prior == null) {
                amountByAccountId.put(accountId, linePrice);
            } else {
                amountByAccountId.put(accountId, prior.add(linePrice));
            }
        }
        List<Account> encounterAccounts = accountQueryService.getAccountListByEncounter(encounterId);
        Map<Long, Account> accountById = new HashMap<>();
        for (Account accountRow : encounterAccounts) {
            accountById.put(accountRow.getId(), accountRow);
        }
        List<PaymentDetailItemCommand> draftLines = new ArrayList<>();
        for (Map.Entry<Long, BigDecimal> entry : amountByAccountId.entrySet()) {
            Long accountKey = entry.getKey();
            BigDecimal draftLineAmount = entry.getValue();
            PaymentDetailItemCommand draftLine = new PaymentDetailItemCommand();
            draftLine.setAccountId(accountKey);
            Account matchedAccount = accountById.get(accountKey);
            if (matchedAccount != null) {
                draftLine.setAccountCode(matchedAccount.getBusNo());
            }
            draftLine.setPayEnum(PaymentChannel.CASHIER.getCode());
            draftLine.setAmount(draftLineAmount);
            draftLine.setChargeAmount(draftLineAmount);
            draftLine.setResultEnum(PaymentStatus.DRAFT.getCode());
            draftLines.add(draftLine);
        }
        return draftLines;
    }

    /**
     * 医保预结算后占位明细：患者现金自付挂主记账户（来自收费项，非预交金登记户）。
     *
     * @param encounterId   就诊主键
     * @param chargeItems   所选收费项
     * @param cashPayAmount 医保 2303 返回的个人现金自付
     * @return 占位明细
     */
    private List<PaymentDetailItemCommand> buildInpatientInsuranceCashPayDraftLines(
        Long encounterId,
        List<ChargeItem> chargeItems,
        BigDecimal cashPayAmount) {
        List<PaymentDetailItemCommand> baseLines =
            this.buildInpatientPreSettlePaymentDetailDraftLines(encounterId, chargeItems);
        if (baseLines.isEmpty()) {
            return baseLines;
        }
        BigDecimal cashPay = cashPayAmount;
        if (cashPay == null) {
            cashPay = BigDecimal.ZERO;
        }
        Long primaryAccountId = this.resolvePrimaryBillingAccountId(encounterId, chargeItems);
        List<PaymentDetailItemCommand> draftLines = new ArrayList<>();
        boolean cashAssigned = false;
        for (PaymentDetailItemCommand line : baseLines) {
            if (line == null) {
                continue;
            }
            PaymentDetailItemCommand draftLine = new PaymentDetailItemCommand();
            draftLine.setAccountId(line.getAccountId());
            draftLine.setAccountCode(line.getAccountCode());
            draftLine.setPayEnum(PaymentChannel.CASHIER.getCode());
            draftLine.setResultEnum(PaymentStatus.DRAFT.getCode());
            draftLine.setChargeAmount(line.getChargeAmount());
            if (!cashAssigned && primaryAccountId.equals(line.getAccountId())) {
                draftLine.setAmount(cashPay);
                cashAssigned = true;
            } else {
                draftLine.setAmount(BigDecimal.ZERO);
            }
            draftLines.add(draftLine);
        }
        if (!cashAssigned) {
            draftLines.get(0).setAmount(cashPay);
        }
        return draftLines;
    }

    /**
     * 解析支付草稿 contract_no：请求显式合同优先，否则从收费项记账账户聚合（BR-IP-STL-016）。
     *
     * <p>多账户且 contract_no 不一致时返回 null（自付与医保同时付费，见 DM-FIN-002）。</p>
     *
     * @param contractFromRequest 请求显式指定的费用性质合同，可空
     * @param chargeItems         本次预结算/结算收费项
     * @param encounterId         就诊主键
     * @return fin_contract.bus_no，无法唯一确定时 null
     */
    private String resolvePaymentContractBusNoForPreSettle(
        ContractDetailQueryModel contractFromRequest,
        List<ChargeItem> chargeItems,
        Long encounterId) {
        if (contractFromRequest != null && StringUtils.isNotBlank(contractFromRequest.getBusNo())) {
            return contractFromRequest.getBusNo().trim();
        }
        Long defaultSelfPayAccountId = accountQueryService.getSelfPayAccount(encounterId);
        LinkedHashSet<Long> accountIds = new LinkedHashSet<>();
        if (defaultSelfPayAccountId != null) {
            accountIds.add(defaultSelfPayAccountId);
        }
        Long medicalInsuranceAccountId = accountQueryService.getMedicalInsuranceAccount(encounterId);
        if (medicalInsuranceAccountId != null) {
            accountIds.add(medicalInsuranceAccountId);
        }
        for (ChargeItem item : chargeItems) {
            if (item != null && item.getAccountId() != null) {
                accountIds.add(item.getAccountId());
            }
        }
        Map<Long, Account> accountById = accountQueryService.mapAccountsByIds(accountIds);
        LinkedHashSet<String> contractBusNos = new LinkedHashSet<>();
        for (ChargeItem item : chargeItems) {
            if (item == null) {
                continue;
            }
            Long accountId = item.getAccountId();
            if (accountId == null) {
                accountId = defaultSelfPayAccountId;
            }
            if (accountId == null) {
                continue;
            }
            Account account = accountById.get(accountId);
            if (account != null && StringUtils.isNotBlank(account.getContractNo())) {
                contractBusNos.add(account.getContractNo().trim());
            }
        }
        if (contractBusNos.size() == 1) {
            return contractBusNos.iterator().next();
        }
        if (contractBusNos.isEmpty()) {
            return null;
        }
        Long primaryAccountId = this.resolvePrimaryBillingAccountId(encounterId, chargeItems);
        if (primaryAccountId != null) {
            Account primaryAccount = accountById.get(primaryAccountId);
            if (primaryAccount != null && StringUtils.isNotBlank(primaryAccount.getContractNo())) {
                return primaryAccount.getContractNo().trim();
            }
        }
        return null;
    }

    private Long resolvePrimaryBillingAccountId(Long encounterId, List<ChargeItem> chargeItems) {
        for (ChargeItem item : chargeItems) {
            if (item != null && item.getAccountId() != null) {
                return item.getAccountId();
            }
        }
        Long medicalInsuranceAccountId = accountQueryService.getMedicalInsuranceAccount(encounterId);
        if (medicalInsuranceAccountId != null) {
            return medicalInsuranceAccountId;
        }
        return accountQueryService.getSelfPayAccount(encounterId);
    }

    private String resolveContractBusNoById(Long contractId) {
        if (contractId == null) {
            return null;
        }
        ContractDetailQueryModel contract = contractQueryService.findContractById(contractId);
        if (contract == null || StringUtils.isBlank(contract.getBusNo())) {
            return null;
        }
        return contract.getBusNo().trim();
    }

    /**
     * 解析本次预结算使用的医保合同 ID（请求优先，否则扫描收费项记账账户）。
     *
     * @param request     预结算请求
     * @param chargeItems 所选收费项
     * @param encounterId 就诊主键
     * @return 医保合同 ID；非医保时为 null
     */
    private Long resolveInsuranceContractId(
        PreSettleInpatientChargesRequest request,
        List<ChargeItem> chargeItems,
        Long encounterId) {
        if (request.getContractId() != null) {
            return request.getContractId();
        }
        LinkedHashSet<Long> accountIds = new LinkedHashSet<>();
        for (ChargeItem item : chargeItems) {
            if (item != null && item.getAccountId() != null) {
                accountIds.add(item.getAccountId());
            }
        }
        Long defaultSelfPayAccountId = accountQueryService.getSelfPayAccount(encounterId);
        if (defaultSelfPayAccountId != null) {
            accountIds.add(defaultSelfPayAccountId);
        }
        Long medicalInsuranceAccountId = accountQueryService.getMedicalInsuranceAccount(encounterId);
        if (medicalInsuranceAccountId != null) {
            accountIds.add(medicalInsuranceAccountId);
        }
        Map<Long, Account> accountById = accountQueryService.mapAccountsByIds(accountIds);
        for (ChargeItem item : chargeItems) {
            if (item == null) {
                continue;
            }
            Long accountId = item.getAccountId();
            if (accountId == null) {
                accountId = defaultSelfPayAccountId;
            }
            Account account = accountById.get(accountId);
            if (account == null || StringUtils.isBlank(account.getContractNo())) {
                continue;
            }
            Contract contract = contractQueryService
                .findOptionalContractEntityByBusNo(account.getContractNo().trim())
                .orElse(null);
            if (contract == null) {
                continue;
            }
            if (inpatientChargeInsuranceExtension.supportsThirdPartyContract(contract.getId())) {
                return contract.getId();
            }
        }
        return null;
    }

    /**
     * 正式结算前医保 confirm（2304）：返回医保结算 ID 供本地支付单关联。
     *
     * @param request 确认结算请求
     * @return 医保 setlId；非医保路径为 null
     */
    private InpatientChargeConfirmResult runInsuranceConfirmIfNeeded(ConfirmInpatientSettleRequest request) {
        if (!inpatientChargeInsuranceExtension.isEnabled()) {
            return null;
        }
        List<InpatientChargePreSettleResult> active =
            inpatientChargeInsuranceExtension.listActivePreSettlesByPaymentDraft(request.getPaymentDraftId());
        if (active.isEmpty()) {
            return null;
        }
        InpatientChargePreSettleResult target = active.get(active.size() - 1);
        if (request.getYbPreSettleId() != null) {
            for (InpatientChargePreSettleResult row : active) {
                if (request.getYbPreSettleId().equals(row.getPreSettleId())) {
                    target = row;
                    break;
                }
            }
        }
        InpatientChargeConfirmCommand command = new InpatientChargeConfirmCommand();
        command.setPaymentDraftId(request.getPaymentDraftId());
        command.setPreSettleId(target.getPreSettleId());
        command.setEncounterId(request.getEncounterId());
        // Cmd：Extension → 2304 正式结算，先于本地 payment 落账
        InpatientChargeConfirmResult ybResult = inpatientChargeInsuranceExtension.confirmBeforeLocalSettle(command);
        return ybResult;
    }

    private List<PaymentYbSettlementItemCommand> buildInpatientYbSettlementItems(
        InpatientChargeConfirmResult ybResult) {
        if (ybResult == null || StringUtils.isBlank(ybResult.getSettlementId())) {
            return List.of();
        }
        PaymentYbSettlementItemCommand item = new PaymentYbSettlementItemCommand();
        item.setSetlId(ybResult.getSettlementId().trim());
        if (StringUtils.isNotBlank(ybResult.getChargeBatchNo())) {
            item.setChrgBchno(ybResult.getChargeBatchNo().trim());
        }
        if (StringUtils.isNotBlank(ybResult.getEncounterPlatformId())) {
            item.setMdtrtId(ybResult.getEncounterPlatformId().trim());
        }
        item.setSettlementRecordId(ybResult.getSettlementRecordId());
        item.setStatus(PaymentYbSettlementStatus.CONFIRMED);
        return List.of(item);
    }

    /**
     * 从草稿关联的医保预结算快照解析 settleMode（出院全结/中途）。
     *
     * @param paymentDraftId 支付草稿 ID
     * @param ybPreSettleId  请求指定的预结算 ID（可 null）
     * @return settleMode；无医保预结算时为 null
     */
    private String resolveInsuranceSettleMode(Long paymentDraftId, Long ybPreSettleId) {
        if (!inpatientChargeInsuranceExtension.isEnabled() || paymentDraftId == null) {
            return null;
        }
        List<InpatientChargePreSettleResult> active =
            inpatientChargeInsuranceExtension.listActivePreSettlesByPaymentDraft(paymentDraftId);
        if (active.isEmpty()) {
            return null;
        }
        if (ybPreSettleId != null) {
            for (InpatientChargePreSettleResult row : active) {
                if (ybPreSettleId.equals(row.getPreSettleId())) {
                    return row.getSettleMode();
                }
            }
        }
        return active.get(active.size() - 1).getSettleMode();
    }

    /**
     * 住院反结时作废/冲红原单关联票据：DRAFT → voidDraftInvoice；ISSUED → writeoff。
     *
     * @param paymentId  被反结的原正向结算支付单 ID
     * @param operatorId 操作员 ID
     */
    private void voidInvoiceLinkedToPaymentOnCancel(Long paymentId, Long operatorId) {
        Invoice invoice = invoiceQueryService.getInvoiceByPaymentId(
            inpatientSettleMapper.toInvoiceQueryByPaymentId(paymentId));
        if (invoice == null || invoice.getId() == null) {
            // 无关联发票则跳过。
            return;
        }
        if (InvoiceStatus.CANCELLED.sameAs(invoice.getStatusEnum())) {
            // 已作废/冲红则幂等跳过。
            return;
        }
        CancelInvoiceCommand cancelCommand = inpatientSettleMapper.toCancelInvoiceCommand(
            invoice.getId(),
            InpatientSettleConstants.INVOICE_CANCEL_REASON_INPATIENT_SETTLE,
            operatorId);
        if (InvoiceStatus.DRAFT.sameAs(invoice.getStatusEnum())) {
            invoiceDomainService.voidDraftInvoice(cancelCommand);
            bossElectronicInvoiceIntegrationSupport.afterInvoiceCancelled(invoice.getId(), cancelCommand.getReason());
            return;
        }
        if (InvoiceStatus.ISSUED.sameAs(invoice.getStatusEnum())) {
            invoiceDomainService.writeoffInvoiceAndCreateRedRecord(cancelCommand);
            bossElectronicInvoiceIntegrationSupport.afterInvoiceCancelled(invoice.getId(), cancelCommand.getReason());
        }
    }

    /**
     * 解析正式结算渠道实付：医保预结算用草稿 displayAmount；自费用少补金额（预交金充足时为 0）。
     *
     * @param request     确认结算请求
     * @param payment     支付草稿
     * @param chargeItems 本批收费项
     * @return 与用户 paymentDetails 交叉校验的期望渠道实付
     */
    private BigDecimal resolveExpectedChannelPayable(
        ConfirmInpatientSettleRequest request,
        PaymentReconciliationQueryModel payment,
        List<ChargeItem> chargeItems) {
        if (this.isInsuranceSettlePaymentDraft(payment.getId())) {
            if (payment.getDisplayAmount() != null) {
                return payment.getDisplayAmount();
            }
            return this.sumChargeItemAmount(chargeItems);
        }
        Account account = accountQueryService.getRegistrationAccount(
            request.getPatientId(),
            request.getEncounterId());
        BigDecimal accountBalance = BigDecimal.ZERO;
        if (account != null) {
            accountBalance = InpatientDisplayAccountBalanceAssembler.resolveAccountBalance(account);
        }
        return this.calculateSettleSupplementAmount(
            accountBalance,
            this.sumChargeItemAmount(chargeItems));
    }

    /**
     * 是否医保预结算后的住院结算草稿。
     *
     * @param paymentDraftId 支付草稿 ID
     * @return 存在 ACTIVE 医保预结算快照时为 true
     */
    private boolean isInsuranceSettlePaymentDraft(Long paymentDraftId) {
        return inpatientChargeInsuranceExtension.isEnabled()
            && paymentDraftId != null
            && !inpatientChargeInsuranceExtension.listActivePreSettlesByPaymentDraft(paymentDraftId).isEmpty();
    }

    /**
     * 少补金额：本批费用超出库内账户余额的部分；无少补时 0。
     *
     * @param accountBalance    结算前库内账户余额（已含历史结算扣减）
     * @param currentBatchTotal 本批费用合计
     * @return 须由渠道实收（并落收预交金）的金额
     */
    private BigDecimal calculateSettleSupplementAmount(
        BigDecimal accountBalance,
        BigDecimal currentBatchTotal) {
        BigDecimal balance = BigDecimal.ZERO;
        if (accountBalance != null) {
            balance = accountBalance;
        }
        BigDecimal batch = BigDecimal.ZERO;
        if (currentBatchTotal != null) {
            batch = currentBatchTotal;
        }
        BigDecimal supplement = batch.subtract(balance)
            .setScale(InpatientSettleConstants.SETTLE_AMOUNT_COMPARE_SCALE, RoundingMode.HALF_UP);
        if (supplement.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return supplement;
    }

    /**
     * 自费预结算响应回填渠道实付（少补金额；预交金充足时为 0）。
     *
     * @param encounterId     就诊 ID
     * @param receivableTotal 本批应收合计
     * @param account         登记账户
     * @param response        预结算响应
     */
    private void applySelfPayChannelPayableOnPreSettle(
        Long encounterId,
        BigDecimal receivableTotal,
        Account account,
        PreSettleInpatientChargesResponse response) {
        BigDecimal accountBalance = BigDecimal.ZERO;
        if (account != null) {
            accountBalance = InpatientDisplayAccountBalanceAssembler.resolveAccountBalance(account);
        }
        BigDecimal channelPayable = this.calculateSettleSupplementAmount(
            accountBalance,
            receivableTotal);
        response.setDisplayAmount(channelPayable);
        if (channelPayable.compareTo(BigDecimal.ZERO) > 0) {
            response.setPreviewText("患者实付：" + channelPayable + " 元");
            return;
        }
        if (receivableTotal != null && receivableTotal.compareTo(BigDecimal.ZERO) > 0) {
            response.setPreviewText("预交金冲抵，应收合计：" + receivableTotal + " 元");
        }
    }

    /**
     * 结算少补：自动收取预交金（HOSPITAL_DEPOSIT PAY），与用户渠道实付对称于出院多退。
     *
     * @param request          确认结算请求
     * @param operatorId       操作员 ID
     * @param supplementAmount 少补金额
     * @param billDate         结算时间
     * @return 收预交金支付单 ID
     */
    private Long collectSettleSupplementAdvanceDeposit(
        ConfirmInpatientSettleRequest request,
        Long operatorId,
        BigDecimal supplementAmount,
        LocalDateTime billDate,
        ClientScanResultDto clientScanResult) {
        Account account = accountQueryService.getRegistrationAccount(
            request.getPatientId(),
            request.getEncounterId());
        if (account == null || account.getId() == null) {
            throw new BusinessException(
                InpatientSettleConstants.EX_IP_STL_103,
                "请先完成住院登记");
        }
        CollectAdvanceDepositCommand collectCommand = inpatientSettleMapper.toSettleSupplementCollectCommand(
            request.getEncounterId(),
            request.getPatientId(),
            account.getId(),
            supplementAmount,
            request.getPaymentDetails(),
            operatorId);
        Long collectPaymentId = paymentReconciliationDomainService.collectAdvanceDeposit(collectCommand);
        thirdPartyPaymentSettleSupport.applyScanBeforeSettle(
            collectPaymentId,
            collectCommand.getPaymentDetails(),
            clientScanResult,
            operatorId,
            null);
        paymentReconciliationDomainService.refreshPaymentDraftPreSettleAmounts(
            collectPaymentId,
            collectCommand.getDisplayAmount(),
            collectCommand.getPaymentDetails());
        SettleInpatientPaymentCommand collectSettleCommand =
            inpatientPrepayMapper.toSettleInpatientPaymentCommand(collectPaymentId);
        if (billDate != null) {
            collectSettleCommand.setBillDate(billDate);
        }
        paymentReconciliationDomainService.settleInpatientPayment(collectSettleCommand);
        AdjustAdvanceDepositBalanceCommand balanceCmd = inpatientPrepayMapper.toCollectBalanceAdjustCommand(
            request.getPatientId(),
            request.getEncounterId(),
            supplementAmount,
            operatorId);
        accountDomainService.adjustAdvanceDepositBalance(balanceCmd);
        return collectPaymentId;
    }

    /**
     * 正式结算前刷新住院结算草稿：全额应收 + 预交金冲抵占位明细（按记账账户分桶）。
     *
     * @param paymentDraftId  支付草稿 ID
     * @param encounterId     就诊 ID
     * @param chargeItems     本批收费项
     * @param receivableTotal 本批应收合计
     */
    private void refreshInpatientSettleDraftForPrepayOffset(
        Long paymentDraftId,
        Long encounterId,
        List<ChargeItem> chargeItems,
        BigDecimal receivableTotal) {
        if (paymentDraftId == null || receivableTotal == null || chargeItems.isEmpty()) {
            return;
        }
        List<PaymentDetailItemCommand> draftLines =
            this.buildInpatientPreSettlePaymentDetailDraftLines(encounterId, chargeItems);
        paymentReconciliationDomainService.refreshPaymentDraftPreSettleAmounts(
            paymentDraftId,
            receivableTotal,
            draftLines);
    }
}
