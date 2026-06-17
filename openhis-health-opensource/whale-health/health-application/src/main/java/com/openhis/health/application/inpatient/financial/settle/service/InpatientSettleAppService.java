package com.openhis.health.application.inpatient.financial.settle.service;

import com.openhis.health.module.common.assembler.financial.PaymentChannelDisplayAssembler;
import com.openhis.health.module.common.assembler.inpatient.InpatientDisplayAccountBalanceAssembler;
import com.openhis.health.module.common.assembler.inpatient.InpatientInfoQueueDisplayAssembler;
import com.openhis.health.module.common.pagination.PageQueryBinder;
import com.openhis.health.module.common.validator.CurrentOperatorResolver;
import com.openhis.health.module.common.validator.PageWindowGate;
import com.openhis.health.module.common.validator.PatientEncounterConsistencyGate;
import com.openhis.health.module.common.validator.PaymentChannelGate;
import com.openhis.health.application.inpatient.financial.prepay.mapper.InpatientPrepayMapper;
import com.openhis.health.application.inpatient.financial.settle.dto.InpatientSettlePaymentDetailDto;
import com.openhis.health.application.inpatient.financial.settle.dto.InpatientSettleQueueItemDto;
import com.openhis.health.application.inpatient.financial.settle.mapper.InpatientSettleMapper;
import com.openhis.health.application.inpatient.financial.settle.mapper.InpatientSettleReadBridgeMapper;
import com.openhis.health.application.inpatient.financial.settle.request.CancelInpatientSettleRequest;
import com.openhis.health.application.inpatient.financial.settle.request.ConfirmInpatientSettleRequest;
import com.openhis.health.application.inpatient.financial.settle.request.LoadInpatientSettlePageInitRequest;
import com.openhis.health.application.inpatient.financial.settle.request.LoadInpatientSettleSummaryRequest;
import com.openhis.health.application.inpatient.financial.settle.request.LoadSettledInpatientPaymentViewRequest;
import com.openhis.health.application.inpatient.financial.settle.request.PageInpatientSettleQueueRequest;
import com.openhis.health.application.inpatient.financial.settle.request.PreSettleInpatientChargesRequest;
import com.openhis.health.application.inpatient.financial.settle.request.QueryInpatientSettleChargesRequest;
import com.openhis.health.application.inpatient.financial.settle.response.CancelInpatientSettleResponse;
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
import com.openhis.health.domain.financial.paymentreconciliation.command.AbortPreSettlementBeforePaymentCommand;
import com.openhis.health.domain.financial.paymentreconciliation.command.CancelInpatientSettleAsRefundCommand;
import com.openhis.health.domain.financial.paymentreconciliation.command.CompleteInpatientSettleCancelRefundCommand;
import com.openhis.health.domain.financial.paymentreconciliation.command.CreatePaymentDraftFromPreSettleCommand;
import com.openhis.health.domain.financial.paymentreconciliation.command.PaymentDetailItemCommand;
import com.openhis.health.domain.financial.paymentreconciliation.command.RefundAdvanceDepositCommand;
import com.openhis.health.domain.financial.paymentreconciliation.command.SettleInpatientPaymentCommand;
import com.openhis.health.domain.financial.paymentreconciliation.command.UpdatePaymentStatusWithSettleIdsCommand;
import com.openhis.health.domain.financial.paymentreconciliation.queries.model.PaymentRecDetailQueryModel;
import com.openhis.health.domain.financial.paymentreconciliation.queries.model.PaymentReconciliationQueryModel;
import com.openhis.health.domain.financial.paymentreconciliation.service.PaymentReconciliationDomainService;
import com.openhis.health.domain.financial.paymentreconciliation.service.PaymentReconciliationQueryService;
import com.openhis.health.domain.financial.contract.entity.Contract;
import com.openhis.health.domain.administration.encounter.queries.InpatientEncounterPageQuery;
import com.openhis.health.domain.administration.encounter.queries.model.InpatientEncounterContextQueryModel;
import com.openhis.health.module.querysupport.product.ProductDefinitionBatchLoader;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.application.insurance.config.InsuranceCoordinationProperties;
import com.openhis.health.application.insurance.inpatient.InsuranceInpatientSettlementAppService;
import com.openhis.health.application.insurance.inpatient.request.StartIpInsuranceSettleRequest;
import com.openhis.health.application.insurance.inpatient.response.StartIpInsuranceSettleResponse;
import com.openhis.health.domain.share.constants.InsuranceCoordinationConstants;
import com.openhis.health.domain.share.constants.InpatientSettleQueueTab;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.ChargeItemStatus;
import com.openhis.health.domain.share.enums.EncounterClass;
import com.openhis.health.domain.share.enums.EncounterStatus;
import com.openhis.health.domain.share.enums.InvoiceStatus;
import com.openhis.health.domain.share.enums.PaymentChannel;
import com.openhis.health.domain.share.enums.PaymentKind;
import com.openhis.health.domain.share.constants.InpatientSettleConstants;
import com.openhis.health.domain.share.enums.PaymentStatus;
import com.openhis.whale.core.application.dto.OptionDto;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.chs.jilin.port.InpatientInsuranceSettlementPort;
import com.openhis.whale.chs.jilin.port.model.IpInsuranceConfirmCommand;
import com.openhis.whale.chs.jilin.port.model.IpInsuranceConfirmResult;
import com.openhis.whale.chs.jilin.port.model.IpInsuranceEncounterCommand;
import com.openhis.whale.chs.jilin.port.model.IpInsurancePreSettleCommand;
import com.openhis.whale.chs.jilin.port.model.IpInsurancePreSettleResult;
import com.openhis.whale.chs.jilin.port.model.IpInsuranceRevokeFeeDetailCommand;
import com.openhis.whale.chs.jilin.port.model.IpInsuranceRevokeSettleCommand;
import com.openhis.whale.chs.jilin.port.model.IpInsuranceRevokeSettleResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 住院结算应用服务（AS-IP-FIN-003，IF-IP-STL-01～07）。
 *
 * <p>职责边界：</p>
 * <ul>
 *   <li>Gate/Pre：结算队列、就诊 IMP 门禁、待结算费用与草稿/金额一致性校验。</li>
 *   <li>Cmd：预结算、正式结算、发票草稿与出院财务全结（顺序与 AS-SDD §3 一致）。</li>
 *   <li>Asm：经 {@link InpatientSettleMapper} 装配队列/摘要/费用/结算响应。</li>
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
    private final CurrentOperatorResolver currentOperatorResolver;

    /** 分页窗口入站门控（B.10.11）。 */
    private final PageWindowGate pageWindowGate;

    /** 支付渠道入站门控。 */
    private final PaymentChannelGate paymentChannelGate;

    /** 患者与就诊一致性门控。 */
    private final PatientEncounterConsistencyGate patientEncounterConsistencyGate;

    /** 就诊读侧（队列、门禁与上下文）。 */
    private final EncounterQueryService encounterQueryService;
    private final EncounterContextFacade encounterContextFacade;

    /** 就诊写侧（出院财务全结）。 */
    private final EncounterDomainService encounterDomainService;

    /** 就诊账户读侧。 */
    private final AccountQueryService accountQueryService;

    /** 就诊账户写侧（出院多退少补退预交金）。 */
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

    private final ProductDefinitionBatchLoader productDefinitionBatchLoader;

    /** 结算读路径（IF-IP-STL-03/04/07）。 */
    private final InpatientSettleReadLoader inpatientSettleReadLoader;

    /** 结算读路径 Request/Response 桥接。 */
    private final InpatientSettleReadBridgeMapper inpatientSettleReadBridgeMapper;

    /** 操作员展示名。 */
    private final PractitionerQueryService practitionerQueryService;

    /** 住院结算 DTO/Command 映射。 */
    private final InpatientSettleMapper inpatientSettleMapper;

    /** 住院医保结算 Port（模块未启用时为 null）。 */
    @Autowired(required = false)
    private InpatientInsuranceSettlementPort inpatientInsuranceSettlementPort;

    @Autowired(required = false)
    private InsuranceInpatientSettlementAppService insuranceInpatientSettlementAppService;

    /** 预交金 Command 复用（出院退预交金结算收口）。 */
    private final InpatientPrepayMapper inpatientPrepayMapper;

    /** 住院队列展示回填。 */
    private final InpatientInfoQueueDisplayAssembler inpatientInfoQueueDisplayAssembler;

    /** 支付渠道展示名。 */
    private final PaymentChannelDisplayAssembler paymentChannelDisplayAssembler;

    /** 医保协同单配置。 */
    private final InsuranceCoordinationProperties insuranceCoordinationProperties;

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
            List<OptionDto> paymentChannels = new ArrayList<>();
            long channelSort = 1L;
            for (PaymentChannel channel : PaymentChannel.values()) {
                if (!channel.isUserSelectable()) {
                    continue;
                }
                paymentChannels.add(new OptionDto(channelSort, channel.getCode(), channel.getDisplay()));
                channelSort = channelSort + 1L;
            }
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
            Map<Long, BigDecimal> chargedOccupancyByEncounterId =
                chargeItemQueryService.mapInpatientChargedOccupancyTotalPriceByEncounterIds(encounterIds);
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
            // Asm：逐行装配队列卡片（含展示余额 = 预交金净值 − 待结算/已结算占用）。
            List<InpatientSettleQueueItemDto> content = new ArrayList<>();
            for (InpatientEncounterContextQueryModel row : page.getContent()) {
                Account account = null;
                InpatientInfoQueueDisplayAssembler.InpatientQueueDisplayOverlay rowOverlay = null;
                BigDecimal pendingSettlement = null;
                BigDecimal chargedOccupancy = null;
                LocalDateTime lastSettleTime = null;
                if (row != null && row.getEncounterId() != null) {
                    account = accountByEncounterId.get(row.getEncounterId());
                    rowOverlay = displayOverlay.get(row.getEncounterId());
                    pendingSettlement = pendingByEncounterId.get(row.getEncounterId());
                    chargedOccupancy = chargedOccupancyByEncounterId.get(row.getEncounterId());
                    lastSettleTime = lastSettleTimeByEncounterId.get(row.getEncounterId());
                }
                BigDecimal displayBalance = InpatientDisplayAccountBalanceAssembler.computeDisplayAccountBalance(
                    account, chargedOccupancy);
                InpatientSettleQueueItemDto item = inpatientSettleMapper.toInpatientSettleQueueItemDto(
                    row, account, rowOverlay, pendingSettlement, lastSettleTime, displayBalance);
                if (item != null && account != null && StringUtils.isNotBlank(account.getContractNo())) {
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
        return inpatientSettleReadBridgeMapper.toSummaryResponse(
            inpatientSettleReadLoader.loadInpatientSettleSummary(
                inpatientSettleReadBridgeMapper.toSummaryQuery(request)));
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
        return inpatientSettleReadBridgeMapper.toChargesResponse(
            inpatientSettleReadLoader.queryInpatientSettleCharges(
                inpatientSettleReadBridgeMapper.toChargesQuery(request)));
    }

    /**
     * IF-IP-STL-05：住院预结算。
     *
     * @param request 就诊、患者与所选待结算明细
     * @return 支付草稿 ID 与应收合计
     * @throws BusinessException 参数/状态/草稿冲突时抛出 EX-IP-STL-001/102/105
     */
    public PreSettleInpatientChargesResponse preSettleInpatientCharges(PreSettleInpatientChargesRequest request) {
        // Gate：就诊、患者与所选收费明细均须非空，否则无法形成有效预结算草稿。
        if (request == null
            || request.getEncounterId() == null
            || request.getPatientId() == null
            || request.getChargeItemIds() == null
            || request.getChargeItemIds().isEmpty()) {
            throw new BusinessException(
                InpatientSettleConstants.EX_IP_STL_001,
                "请选择待结算费用");
        }
        Long operatorId = currentOperatorResolver.requireOperatorId(
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
        // Pre：加载所选行并校验归属就诊且均为待结算态（BR-IP-STL-002）。
        ChargeItemQueryCommand loadCmd = new ChargeItemQueryCommand();
        loadCmd.setIds(request.getChargeItemIds());
        List<ChargeItem> chargeItems = chargeItemQueryService.loadChargeItemsByIds(loadCmd);
        this.assertChargeItemsForInpatientSettle(chargeItems, request.getChargeItemIds(), request.getEncounterId());
        BigDecimal receivableTotal = this.sumChargeItemAmount(chargeItems);
        if (receivableTotal.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(
                InpatientSettleConstants.EX_IP_STL_001,
                "请选择待结算费用");
        }
        Set<Long> allPendingIds = this.loadAllPendingChargeItemIds(request.getEncounterId());
        Set<Long> selectedIds = new HashSet<>(request.getChargeItemIds());
        String settleMode = this.resolveSettleMode(
            encounter.getStatusEnum(),
            selectedIds,
            allPendingIds,
            request.getSettleMode());
        // Pre：找出同就诊下未完成的 INPATIENT_CLINIC 草稿，后续逐条作废（BR-IP-STL-010）。
        List<PaymentReconciliationQueryModel> encounterPayments =
            paymentReconciliationQueryService.findPaymentsByEncounter(request.getEncounterId());
        List<PaymentReconciliationQueryModel> draftPayments = new ArrayList<>();
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
                inpatientSettleMapper.toBatchChargeItemIdsCommand(request.getChargeItemIds(), operatorId);
            // Cmd：收费项预结算校验与事件（状态仍 PENDING_SETTLEMENT）。
            chargeItemDomainService.preSettleInpatientChargeItems(preSettleCmd);
            Account account = accountQueryService.getRegistrationAccount(
                request.getPatientId(),
                request.getEncounterId());
            String contractNo = null;
            if (account != null) {
                contractNo = account.getContractNo();
            }
            List<PaymentDetailItemCommand> draftDetailCommands = new ArrayList<>();
            PaymentDetailItemCommand placeholder = new PaymentDetailItemCommand();
            placeholder.setPayEnum(PaymentChannel.CASHIER.getCode());
            placeholder.setAmount(receivableTotal);
            placeholder.setChargeAmount(receivableTotal);
            placeholder.setResultEnum(PaymentStatus.DRAFT.getCode());
            if (account != null) {
                placeholder.setAccountId(account.getId());
            }
            draftDetailCommands.add(placeholder);
            CreatePaymentDraftFromPreSettleCommand paymentDraftCommand =
                inpatientSettleMapper.toCreatePaymentDraftFromPreSettleCommand(
                    request,
                    receivableTotal,
                    Collections.emptyList(),
                    operatorId,
                    this.joinIds(request.getChargeItemIds()),
                    contractNo);
            paymentDraftCommand.setPaymentDetails(draftDetailCommands);
            // Cmd：落 INPATIENT_CLINIC 支付草稿（DRAFT）及占位明细，供 IF-IP-STL-06 正式结算消费。
            Long paymentDraftId =
                paymentReconciliationDomainService.createPaymentDraftFromPreSettle(paymentDraftCommand);
            PreSettleInpatientChargesResponse response =
                inpatientSettleMapper.toPreSettleInpatientChargesResponse(paymentDraftId, receivableTotal);
            response.setSettleMode(settleMode);
            this.applyInsurancePreSettleIfNeeded(request, paymentDraftId, response, chargeItems, settleMode, account);
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
            || request.getChargeItemIds().isEmpty()
            || request.getPaymentDetails() == null
            || request.getPaymentDetails().isEmpty()) {
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
        Long operatorId = currentOperatorResolver.requireOperatorId(
            InpatientSettleConstants.EX_IP_STL_106,
            "重新预结算并核对");
        // Pre：草稿须为 INPATIENT_CLINIC + DRAFT，且就诊锚点与本次提交一致。
        PaymentReconciliationQueryModel payment =
            paymentReconciliationQueryService.mustGetPayment(request.getPaymentDraftId());
        if (payment.getKindEnum() != PaymentKind.INPATIENT_CLINIC
            || payment.getStatusEnum() != PaymentStatus.DRAFT) {
            throw new BusinessException(
                InpatientSettleConstants.EX_IP_STL_106,
                "支付草稿已失效，请重新预结算");
        }
        if (!request.getEncounterId().equals(payment.getEncounterId())) {
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
        // Pre：拟结项须仍属本就诊且为待结算态，禁止重复结算已结行。
        ChargeItemQueryCommand loadCmd = new ChargeItemQueryCommand();
        loadCmd.setIds(request.getChargeItemIds());
        List<ChargeItem> chargeItems = chargeItemQueryService.loadChargeItemsByIds(loadCmd);
        this.assertChargeItemsForInpatientSettle(chargeItems, request.getChargeItemIds(), request.getEncounterId());
        // Pre：与历史成功/退款中流水挂载 chargeItem 求交，命中则拒绝（BR-IP-STL-007）。
        List<PaymentReconciliationQueryModel> overlapping =
            paymentReconciliationQueryService.findPaySuccessOrRefundingOverlappingChargeItems(
                request.getEncounterId(),
                request.getChargeItemIds());
        if (overlapping != null && !overlapping.isEmpty()) {
            throw new BusinessException(
                InpatientSettleConstants.EX_IP_STL_106,
                "所选费用已存在成功结算流水");
        }
        BigDecimal expectedTotal = this.resolveExpectedPayableAmount(payment, chargeItems);
        BigDecimal submitTotal = this.sumPaymentDetailAmount(request.getPaymentDetails());
        // Pre：渠道实付合计须与应收/displayAmount 一致（BR-IP-STL-006）。
        if (expectedTotal.setScale(InpatientSettleConstants.SETTLE_AMOUNT_COMPARE_SCALE, RoundingMode.HALF_UP)
            .compareTo(submitTotal.setScale(InpatientSettleConstants.SETTLE_AMOUNT_COMPARE_SCALE, RoundingMode.HALF_UP))
            != 0) {
            throw new BusinessException(
                InpatientSettleConstants.EX_IP_STL_106,
                "渠道实付合计与应收不一致");
        }
        // Pre：加载就诊下全部待结算行（排除挂号锚点），用于出院全结/中途结算判定。
        Set<Long> allPendingIds = this.loadAllPendingChargeItemIds(request.getEncounterId());
        Set<Long> selectedIds = new HashSet<>(request.getChargeItemIds());
        String insuranceSettleMode = this.resolveInsuranceSettleMode(payment.getId(), request.getYbPreSettleId());
        String settleMode = insuranceSettleMode != null
            ? insuranceSettleMode
            : this.resolveSettleMode(encounter.getStatusEnum(), selectedIds, allPendingIds, null);
        boolean dischargeFullSettle =
            InpatientSettleConstants.SETTLE_MODE_DISCHARGE_FULL.equals(settleMode);
        patientEncounterConsistencyGate.requireRequestPatientMatchesEncounter(
            request.getPatientId(),
            encounter.getPatientId(),
            InpatientSettleConstants.EX_IP_STL_106,
            "患者标识与就诊不一致");
        // Pre：本批结算推进为 SETTLED 前的已结算占用，供出院多退计算（避免同事务内 SUM 未 flush 漏计本批）。
        BigDecimal settledBeforeCurrentBatch =
            chargeItemQueryService.sumSettledTotalPriceByEncounterId(request.getEncounterId());
        ConfirmInpatientSettleResponse asyncResponse = this.tryConfirmInpatientSettleAsync(
            request,
            operatorId,
            payment,
            dischargeFullSettle,
            settledBeforeCurrentBatch);
        if (asyncResponse != null) {
            return asyncResponse;
        }
        String ybSetlId = null;
        try {
            ybSetlId = this.runInsuranceConfirmIfNeeded(request);
            SettleInpatientPaymentCommand settleCommand =
                inpatientSettleMapper.toSettleInpatientPaymentCommand(request, operatorId);
            // Cmd ①：支付草稿推进为 SUCCESS。
            Long paymentId = paymentReconciliationDomainService.settleInpatientPayment(settleCommand);
            BatchChargeItemIdsCommand settleChargeCmd =
                inpatientSettleMapper.toBatchChargeItemIdsCommand(request.getChargeItemIds(), operatorId);
            // Cmd ②：选中收费项推进为 SETTLED。
            chargeItemDomainService.settleInpatientChargeItems(settleChargeCmd);
            PaymentReconciliationQueryModel settledPayment =
                paymentReconciliationQueryService.mustGetPayment(paymentId);
            CreateDraftInvoiceCommand invoiceCommand =
                inpatientSettleMapper.toCreateDraftInvoiceCommand(settledPayment, paymentId);
            invoiceCommand.setOperatorId(operatorId);
            // Cmd ③：创建发票草稿（BR-IP-STL-008）。
            Long invoiceDraftId = invoiceDomainService.createDraftInvoiceAfterPayment(invoiceCommand);
            Long advanceRefundPaymentId = null;
            if (dischargeFullSettle) {
                // Cmd ④：出院全结多退——仅退「预交金净值 − 已结算占用」盈余，非全额预交金（BR-IP-STL-014）。
                advanceRefundPaymentId = this.refundDischargeAdvanceDepositSurplus(
                    request.getEncounterId(),
                    request.getPatientId(),
                    operatorId,
                    settledBeforeCurrentBatch,
                    this.sumChargeItemAmount(chargeItems));
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
                paymentId, invoiceDraftId, dischargeFullSettle, advanceRefundPaymentId, ybSetlId);
        } catch (BusinessException ex) {
            throw ex;
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
        // Pre：读路径加载已结算支付单快照与关联费用明细。
        return inpatientSettleReadBridgeMapper.toPaymentViewResponse(
            inpatientSettleReadLoader.loadSettledInpatientPaymentView(
                inpatientSettleReadBridgeMapper.toPaymentViewQuery(request)));
    }

    /**
     * 取消住院结算（CHS Wave 2 扩展；AS-IP-FIN-003 非目标外挂）。
     *
     * @param request 取消请求
     * @return 逆向退款支付单与医保撤销结果
     * @throws BusinessException 原单状态非法、患者就诊不一致或已存在退款单时抛出 EX-IP-STL-106
     */
    public CancelInpatientSettleResponse cancelInpatientSettle(CancelInpatientSettleRequest request) {
        // Gate：取消结算须已登录且原单/患者/就诊一致。
        Long operatorId = currentOperatorResolver.requireOperatorId(
            InpatientSettleConstants.EX_IP_STL_106,
            "请先登录");
        // Pre：加载原成功结算单，不存在或非成功态则拒绝取消。
        PaymentReconciliationQueryModel originalPayment =
            paymentReconciliationQueryService.mustGetPayment(request.getPaymentId());
        if (!PaymentStatus.SUCCESS.equals(originalPayment.getStatusEnum())) {
            throw new BusinessException(
                InpatientSettleConstants.EX_IP_STL_106,
                "仅成功结算单允许取消");
        }
        if (!PaymentKind.INPATIENT_CLINIC.equals(originalPayment.getKindEnum())) {
            throw new BusinessException(
                InpatientSettleConstants.EX_IP_STL_106,
                "支付单非住院结算类型");
        }
        if (!request.getEncounterId().equals(originalPayment.getEncounterId())
            || !request.getPatientId().equals(originalPayment.getPatientId())) {
            throw new BusinessException(
                InpatientSettleConstants.EX_IP_STL_106,
                "患者或就诊与支付单不一致");
        }
        EncounterDetailQueryModel encounter = encounterQueryService.mustGetEncounterOfClass(
            request.getEncounterId(),
            EncounterClass.IMP.getCode(),
            InpatientSettleConstants.EX_IP_STL_106,
            "就诊记录不是住院类别，不能取消结算");
        patientEncounterConsistencyGate.requireRequestPatientMatchesEncounter(
            request.getPatientId(),
            encounter.getPatientId(),
            InpatientSettleConstants.EX_IP_STL_106,
            "患者标识与就诊不一致");
        List<Long> chargeItemIds = this.parseIds(originalPayment.getChargeItemIds());
        // Pre：医保撤销须在本地逆向落账之前完成，避免平台与 HIS 状态分叉。
        InsuranceRevokeSnapshot insuranceRevoke =
            this.runInsuranceRevokeBeforeCancel(
                request.getPaymentId(),
                request.getEncounterId(),
                request.getPatientId(),
                chargeItemIds);
        List<PaymentRecDetailQueryModel> paymentDetails =
            paymentReconciliationQueryService.listPaymentRecDetailsByPaymentId(request.getPaymentId());
        CancelInpatientSettleAsRefundCommand cancelCommand =
            inpatientSettleMapper.toCancelInpatientSettleAsRefundCommand(
                originalPayment, paymentDetails, operatorId);
        try {
            // Cmd：生成逆向退款支付单（REFUND_PROCESSING）。
            Long refundPaymentId = paymentReconciliationDomainService.cancelInpatientSettleAsRefund(cancelCommand);
            if (!chargeItemIds.isEmpty()) {
                BatchChargeItemIdsCommand revertChargeCmd =
                    inpatientSettleMapper.toBatchChargeItemIdsCommand(chargeItemIds, operatorId);
                // Cmd：本批费用 SETTLED → PENDING_SETTLEMENT，恢复可再次结算。
                chargeItemDomainService.revertInpatientSettledChargeItems(revertChargeCmd);
            }
            boolean dischargeFullRevert =
                InpatientSettleConstants.SETTLE_MODE_DISCHARGE_FULL.equals(insuranceRevoke.settleMode);
            if (!dischargeFullRevert
                && EncounterStatus.COMPLETED.getCode().equals(encounter.getStatusEnum())) {
                dischargeFullRevert = true;
            }
            if (dischargeFullRevert) {
                // Cmd：出院全结取消时就诊 COMPLETED → PENDING。
                encounterDomainService.revertInpatientFinancialSettlement(request.getEncounterId());
                // Cmd：若同批存在出院多退预交金，回补账户并作废对应预交金流水。
                this.revertDischargeAdvanceDepositOnCancel(
                    request.getEncounterId(),
                    request.getPatientId(),
                    request.getPaymentId(),
                    originalPayment.getBillDate(),
                    operatorId);
            }
            // Cmd：作废草稿或冲红已开具票据，与 confirm 时 createDraftInvoiceAfterPayment 对称。
            this.voidInvoiceLinkedToPaymentOnCancel(request.getPaymentId(), operatorId);
            CompleteInpatientSettleCancelRefundCommand completeCommand =
                inpatientSettleMapper.toCompleteInpatientSettleCancelRefundCommand(
                    request.getPaymentId(),
                    refundPaymentId,
                    insuranceRevoke.ybSetlId);
            // Cmd：原单与逆向单收口为 REFUNDED，并发布住院结算取消事件。
            paymentReconciliationDomainService.completeInpatientSettleCancelRefund(completeCommand);
            return inpatientSettleMapper.toCancelInpatientSettleResponse(
                refundPaymentId,
                insuranceRevoke.insuranceRevoked,
                insuranceRevoke.ybSetlId);
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new BusinessException(
                InpatientSettleConstants.EX_IP_STL_109,
                "取消住院结算失败，请以流水为准重试");
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
        for (Long requestedId : requestedIds) {
            ChargeItem chargeItem = loadedById.get(requestedId);
            if (chargeItem == null) {
                throw new BusinessException(
                    InpatientSettleConstants.EX_IP_STL_001,
                    "请选择待结算费用");
            }
            if (!encounterId.equals(chargeItem.getEncounterId())) {
                throw new BusinessException(
                    InpatientSettleConstants.EX_IP_STL_001,
                    "所选费用不属于当前就诊");
            }
            if (!ChargeItemStatus.PENDING_SETTLEMENT.getCode().equals(chargeItem.getStatusEnum())) {
                throw new BusinessException(
                    InpatientSettleConstants.EX_IP_STL_001,
                    "所选费用含非待结算项");
            }
        }
    }

    /**
     * 出院全结多退：预交金净值大于已结算占用时，落退预交金流水并扣减账户余额。
     *
     * <p>退费额 = 预交金净值 −（结算前已结占用 + 本批刚结占用）；只退盈余，不退全额预交金。
     * 例：预交 1000、本批费用 50 → 退 950，账户净值 50，展示余额 50−50=0。</p>
     *
     * @param encounterId                 就诊 ID
     * @param patientId                   患者 ID
     * @param operatorId                  操作员 ID
     * @param settledBeforeCurrentBatch   本批 {@code settleInpatientChargeItems} 执行前已结算占用合计
     * @param currentBatchSettledTotal    本批刚推进为 {@code SETTLED} 的费用合计
     * @return 退预交金支付单 ID；无需退费时 null
     */
    private Long refundDischargeAdvanceDepositSurplus(
        Long encounterId,
        Long patientId,
        Long operatorId,
        BigDecimal settledBeforeCurrentBatch,
        BigDecimal currentBatchSettledTotal) {
        Account account = accountQueryService.getRegistrationAccount(patientId, encounterId);
        if (account == null || account.getId() == null) {
            throw new BusinessException(
                InpatientSettleConstants.EX_IP_STL_103,
                "请先完成住院登记");
        }
        BigDecimal prepayNet = InpatientDisplayAccountBalanceAssembler.resolvePersonalCashPrepayNet(account);
        BigDecimal settledOccupancy = settledBeforeCurrentBatch;
        if (settledOccupancy == null) {
            settledOccupancy = BigDecimal.ZERO;
        }
        if (currentBatchSettledTotal != null) {
            settledOccupancy = settledOccupancy.add(currentBatchSettledTotal);
        }
        // 多退少补之「多退」：仅退还预交金净值超出全部已结算占用的部分。
        BigDecimal refundAmount = prepayNet.subtract(settledOccupancy)
            .setScale(InpatientSettleConstants.SETTLE_AMOUNT_COMPARE_SCALE, RoundingMode.HALF_UP);
        if (refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
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

    private String resolveSettleMode(
        String encounterStatus,
        Set<Long> selectedChargeItemIds,
        Set<Long> allPendingChargeItemIds,
        String requestSettleMode) {
        boolean fullSelection = selectedChargeItemIds.equals(allPendingChargeItemIds);
        if (InpatientSettleConstants.SETTLE_MODE_MID.equals(requestSettleMode)) {
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
            if (!fullSelection) {
                throw new BusinessException(
                    InpatientSettleConstants.INS_IP_001,
                    "请全选待结算费用或改为中途结算");
            }
            return InpatientSettleConstants.SETTLE_MODE_DISCHARGE_FULL;
        }
        if (fullSelection) {
            throw new BusinessException(
                InpatientSettleConstants.EX_IP_STL_108,
                "请先完成护士出院或改为中途结算");
        }
        return InpatientSettleConstants.SETTLE_MODE_MID;
    }

    private void applyInsurancePreSettleIfNeeded(
        PreSettleInpatientChargesRequest request,
        Long paymentDraftId,
        PreSettleInpatientChargesResponse response,
        List<ChargeItem> chargeItems,
        String settleMode,
        Account account) {
        if (inpatientInsuranceSettlementPort == null) {
            response.setSettlementComposition("SELF_PAY_ONLY");
            return;
        }
        Long insuranceContractId = this.resolveInsuranceContractId(request, account);
        if (insuranceContractId == null) {
            response.setSettlementComposition("SELF_PAY_ONLY");
            return;
        }
        if (!inpatientInsuranceSettlementPort.isInsuranceContract(insuranceContractId)) {
            response.setSettlementComposition("SELF_PAY_ONLY");
            return;
        }
        if (StringUtils.isBlank(request.getPsnNo())) {
            throw new BusinessException(
                InpatientSettleConstants.EX_IP_STL_001,
                "医保预结算须先读卡获取人员编号");
        }
        IpInsurancePreSettleCommand command = new IpInsurancePreSettleCommand();
        command.setEncounterId(request.getEncounterId());
        command.setChargeItemIds(request.getChargeItemIds());
        command.setContractId(insuranceContractId);
        command.setPaymentDraftId(paymentDraftId);
        command.setMdtrtCertType(request.getYbMediumType());
        command.setMdtrtCertNo(request.getYbCardInfo());
        command.setPsnNo(request.getPsnNo());
        command.setInsuplcAdmdvs(request.getInsuplcAdmdvs());
        command.setInsutype(request.getInsutype());
        command.setSettleMode(settleMode);
        command.setAcctUsedFlag(request.getAcctUsedFlag());
        command.setPsnSetlway(request.getPsnSetlway());
        IpInsurancePreSettleResult ybResult = inpatientInsuranceSettlementPort.preSettle(command);
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
        List<PaymentDetailItemCommand> draftLines = new ArrayList<>();
        PaymentDetailItemCommand placeholder = new PaymentDetailItemCommand();
        placeholder.setPayEnum(PaymentChannel.CASHIER.getCode());
        BigDecimal displayAmount = response.getDisplayAmount() != null
            ? response.getDisplayAmount()
            : response.getReceivableTotal();
        placeholder.setAmount(displayAmount);
        placeholder.setChargeAmount(displayAmount);
        placeholder.setResultEnum(PaymentStatus.DRAFT.getCode());
        if (account != null) {
            placeholder.setAccountId(account.getId());
        }
        draftLines.add(placeholder);
        paymentReconciliationDomainService.refreshPaymentDraftPreSettleAmounts(
            paymentDraftId,
            displayAmount,
            draftLines);
        if (response.getDisplayAmount() != null) {
            response.setPreviewText("患者实付：" + response.getDisplayAmount() + " 元");
        }
    }

    private Long resolveInsuranceContractId(PreSettleInpatientChargesRequest request, Account account) {
        if (request.getContractId() != null) {
            return request.getContractId();
        }
        if (account == null || StringUtils.isBlank(account.getContractNo())) {
            return null;
        }
        Contract contract = contractQueryService
            .findOptionalContractEntityByBusNo(account.getContractNo().trim())
            .orElse(null);
        if (contract == null) {
            return null;
        }
        if (inpatientInsuranceSettlementPort.isInsuranceContract(contract.getId())) {
            return contract.getId();
        }
        return null;
    }

    private ConfirmInpatientSettleResponse tryConfirmInpatientSettleAsync(
        ConfirmInpatientSettleRequest request,
        Long operatorId,
        PaymentReconciliationQueryModel payment,
        boolean dischargeFullSettle,
        BigDecimal settledBeforeCurrentBatch) {
        if (!insuranceCoordinationProperties.isIpSettleAsyncEnabled()) {
            return null;
        }
        if (insuranceInpatientSettlementAppService == null || inpatientInsuranceSettlementPort == null) {
            return null;
        }
        List<IpInsurancePreSettleResult> active =
            inpatientInsuranceSettlementPort.listActivePreSettlesByPaymentDraft(request.getPaymentDraftId());
        if (active.isEmpty()) {
            return null;
        }
        IpInsurancePreSettleResult target = active.get(active.size() - 1);
        if (request.getYbPreSettleId() != null) {
            for (IpInsurancePreSettleResult row : active) {
                if (request.getYbPreSettleId().equals(row.getPreSettleId())) {
                    target = row;
                    break;
                }
            }
        }
        Long contractId = target.getContractId();
        if (contractId == null && StringUtils.isNotBlank(payment.getContractNo())) {
            contractId = contractQueryService.mapContractIdByBusNoIn(
                Collections.singleton(payment.getContractNo().trim())).get(payment.getContractNo().trim());
        }
        if (contractId == null) {
            return null;
        }
        Long accountId = payment.getAccountId();
        if (accountId == null) {
            Account account = accountQueryService.getRegistrationAccount(request.getPatientId(), request.getEncounterId());
            if (account != null) {
                accountId = account.getId();
            }
        }
        if (accountId == null) {
            return null;
        }
        StartIpInsuranceSettleRequest startRequest = new StartIpInsuranceSettleRequest();
        startRequest.setPaymentDraftId(request.getPaymentDraftId());
        startRequest.setContractId(contractId);
        startRequest.setAccountId(accountId);
        startRequest.setEncounterId(request.getEncounterId());
        startRequest.setPatientId(request.getPatientId());
        startRequest.setPreSettleId(target.getPreSettleId());
        startRequest.setYbPreSettleId(request.getYbPreSettleId());
        startRequest.setChargeItemIds(request.getChargeItemIds());
        startRequest.setDischargeFullSettle(dischargeFullSettle);
        startRequest.setSettledBeforeCurrentBatch(settledBeforeCurrentBatch);
        startRequest.setBillDate(request.getBillDate());
        startRequest.setOperatorId(operatorId);
        StartIpInsuranceSettleResponse startResponse =
            insuranceInpatientSettlementAppService.startInpatientSettle(startRequest);
        ConfirmInpatientSettleResponse response = new ConfirmInpatientSettleResponse();
        response.setPaymentId(startResponse.getPaymentId());
        response.setSettlementMode(startResponse.getSettlementMode());
        response.setPaymentStatus(startResponse.getPaymentStatus());
        response.setCoordinationIds(startResponse.getCoordinationIds());
        response.setDischargeFullSettle(dischargeFullSettle);
        response.setInsuranceSettled(Boolean.FALSE);
        return response;
    }

    private String runInsuranceConfirmIfNeeded(ConfirmInpatientSettleRequest request) {
        if (inpatientInsuranceSettlementPort == null) {
            return null;
        }
        List<IpInsurancePreSettleResult> active =
            inpatientInsuranceSettlementPort.listActivePreSettlesByPaymentDraft(request.getPaymentDraftId());
        if (active.isEmpty()) {
            return null;
        }
        IpInsurancePreSettleResult target = active.get(active.size() - 1);
        if (request.getYbPreSettleId() != null) {
            for (IpInsurancePreSettleResult row : active) {
                if (request.getYbPreSettleId().equals(row.getPreSettleId())) {
                    target = row;
                    break;
                }
            }
        }
        IpInsuranceConfirmCommand command = new IpInsuranceConfirmCommand();
        command.setPaymentDraftId(request.getPaymentDraftId());
        command.setPreSettleId(target.getPreSettleId());
        command.setEncounterId(request.getEncounterId());
        IpInsuranceConfirmResult ybResult = inpatientInsuranceSettlementPort.confirmBeforeLocalSettle(command);
        return ybResult.getSetlId();
    }

    private String resolveInsuranceSettleMode(Long paymentDraftId, Long ybPreSettleId) {
        if (inpatientInsuranceSettlementPort == null || paymentDraftId == null) {
            return null;
        }
        List<IpInsurancePreSettleResult> active =
            inpatientInsuranceSettlementPort.listActivePreSettlesByPaymentDraft(paymentDraftId);
        if (active.isEmpty()) {
            return null;
        }
        if (ybPreSettleId != null) {
            for (IpInsurancePreSettleResult row : active) {
                if (ybPreSettleId.equals(row.getPreSettleId())) {
                    return row.getSettleMode();
                }
            }
        }
        return active.get(active.size() - 1).getSettleMode();
    }

    /**
     * 取消住院结算前撤销医保：2305 →（出院全结时）2405 → 本批 2302。
     *
     * @param paymentId     成功支付单 ID
     * @param encounterId   就诊 ID
     * @param patientId     患者 ID
     * @param chargeItemIds 本批结算费用明细 ID
     * @return 医保撤销快照
     */
    private InsuranceRevokeSnapshot runInsuranceRevokeBeforeCancel(
        Long paymentId,
        Long encounterId,
        Long patientId,
        List<Long> chargeItemIds) {
        InsuranceRevokeSnapshot snapshot = new InsuranceRevokeSnapshot();
        snapshot.insuranceRevoked = false;
        snapshot.ybSetlId = null;
        snapshot.settleMode = null;
        if (inpatientInsuranceSettlementPort == null) {
            return snapshot;
        }
        IpInsurancePreSettleResult preSettle =
            inpatientInsuranceSettlementPort.findSettledPreSettleByPayment(paymentId);
        if (preSettle == null) {
            return snapshot;
        }
        snapshot.settleMode = preSettle.getSettleMode();
        IpInsuranceRevokeSettleCommand revokeSettleCommand = new IpInsuranceRevokeSettleCommand();
        revokeSettleCommand.setPaymentId(paymentId);
        revokeSettleCommand.setPreSettleId(preSettle.getPreSettleId());
        revokeSettleCommand.setSetlId(preSettle.getSetlId());
        // Cmd ①：2305 撤销医保正式结算。
        IpInsuranceRevokeSettleResult revokeSettleResult =
            inpatientInsuranceSettlementPort.revokeSettle(revokeSettleCommand);
        snapshot.insuranceRevoked = true;
        snapshot.ybSetlId = revokeSettleResult.getSetlId();
        Long contractId = preSettle.getContractId();
        if (contractId == null) {
            Account account = accountQueryService.getRegistrationAccount(patientId, encounterId);
            contractId = this.resolveInsuranceContractIdFromAccount(account);
        }
        if (InpatientSettleConstants.SETTLE_MODE_DISCHARGE_FULL.equals(preSettle.getSettleMode())
            && contractId != null) {
            IpInsuranceEncounterCommand dischargeCommand = new IpInsuranceEncounterCommand();
            dischargeCommand.setEncounterId(encounterId);
            dischargeCommand.setContractId(contractId);
            // Cmd ②：出院全结路径须同步撤销 2402（2405）。
            inpatientInsuranceSettlementPort.revokeDischarge(dischargeCommand);
        }
        if (contractId != null && chargeItemIds != null && !chargeItemIds.isEmpty()) {
            IpInsuranceRevokeFeeDetailCommand feeCommand = new IpInsuranceRevokeFeeDetailCommand();
            feeCommand.setEncounterId(encounterId);
            feeCommand.setContractId(contractId);
            feeCommand.setChargeItemIds(chargeItemIds);
            // Cmd ③：撤销本批已传费用明细（2302），供后续重新结算。
            inpatientInsuranceSettlementPort.revokeFeeDetails(feeCommand);
        }
        return snapshot;
    }

    /**
     * 登记账户合同 → 医保合同 ID。
     *
     * @param account 登记账户
     * @return 医保合同 ID；非医保时为 null
     */
    private Long resolveInsuranceContractIdFromAccount(Account account) {
        if (account == null || StringUtils.isBlank(account.getContractNo())) {
            return null;
        }
        Contract contract = contractQueryService
            .findOptionalContractEntityByBusNo(account.getContractNo().trim())
            .orElse(null);
        if (contract == null) {
            return null;
        }
        if (inpatientInsuranceSettlementPort.isInsuranceContract(contract.getId())) {
            return contract.getId();
        }
        return null;
    }

    /**
     * 取消出院全结时回补多退预交金：恢复账户余额并将同批预交金退现流水标为已退款。
     *
     * @param encounterId         就诊 ID
     * @param patientId           患者 ID
     * @param settlementPaymentId 被取消的结算支付单 ID
     * @param settlementBillDate  原结算账单日
     * @param operatorId          操作员 ID
     */
    private void revertDischargeAdvanceDepositOnCancel(
        Long encounterId,
        Long patientId,
        Long settlementPaymentId,
        LocalDateTime settlementBillDate,
        Long operatorId) {
        List<PaymentReconciliationQueryModel> payments =
            paymentReconciliationQueryService.findPaySuccessOrRefundingByEncounter(encounterId);
        PaymentReconciliationQueryModel advanceRefundPayment = null;
        for (PaymentReconciliationQueryModel payment : payments) {
            if (!PaymentKind.HOSPITAL_DEPOSIT.equals(payment.getKindEnum())) {
                continue;
            }
            if (settlementPaymentId.equals(payment.getId())) {
                continue;
            }
            if (payment.getDisplayAmount() == null
                || payment.getDisplayAmount().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            if (settlementBillDate != null && payment.getBillDate() != null) {
                if (!settlementBillDate.equals(payment.getBillDate())) {
                    continue;
                }
            }
            advanceRefundPayment = payment;
            break;
        }
        if (advanceRefundPayment == null) {
            return;
        }
        AdjustAdvanceDepositBalanceCommand balanceCmd = new AdjustAdvanceDepositBalanceCommand();
        balanceCmd.setPatientId(patientId);
        balanceCmd.setEncounterId(encounterId);
        balanceCmd.setBalanceDelta(advanceRefundPayment.getDisplayAmount());
        balanceCmd.setOperatorId(operatorId);
        // Cmd：取消结算后把多退预交金加回登记账户。
        accountDomainService.adjustAdvanceDepositBalance(balanceCmd);
        UpdatePaymentStatusWithSettleIdsCommand statusCmd = new UpdatePaymentStatusWithSettleIdsCommand();
        statusCmd.setPaymentId(advanceRefundPayment.getId());
        statusCmd.setStatusEnum(PaymentStatus.REFUNDED);
        // Cmd：同批预交金退现流水标为已退款，避免重复冲正。
        paymentReconciliationDomainService.updatePaymentStatusWithSettleIds(statusCmd);
    }

    /**
     * 取消住院结算时作废/冲红原单关联票据：DRAFT → voidDraftInvoice；ISSUED → writeoff。
     *
     * @param paymentId  被取消的结算支付单 ID
     * @param operatorId 操作员 ID
     */
    private void voidInvoiceLinkedToPaymentOnCancel(Long paymentId, Long operatorId) {
        Invoice invoice = invoiceQueryService.getInvoiceByPaymentId(
            inpatientSettleMapper.toInvoiceQueryByPaymentId(paymentId));
        if (invoice == null || invoice.getId() == null) {
            return;
        }
        if (InvoiceStatus.CANCELLED.sameAs(invoice.getStatusEnum())) {
            return;
        }
        CancelInvoiceCommand cancelCommand = inpatientSettleMapper.toCancelInvoiceCommand(
            invoice.getId(),
            InpatientSettleConstants.INVOICE_CANCEL_REASON_INPATIENT_SETTLE,
            operatorId);
        if (InvoiceStatus.DRAFT.sameAs(invoice.getStatusEnum())) {
            invoiceDomainService.voidDraftInvoice(cancelCommand);
            return;
        }
        if (InvoiceStatus.ISSUED.sameAs(invoice.getStatusEnum())) {
            invoiceDomainService.writeoffInvoiceAndCreateRedRecord(cancelCommand);
        }
    }

    private BigDecimal resolveExpectedPayableAmount(
        PaymentReconciliationQueryModel payment,
        List<ChargeItem> chargeItems) {
        if (payment.getDisplayAmount() != null
            && inpatientInsuranceSettlementPort != null
            && payment.getId() != null
            && !inpatientInsuranceSettlementPort.listActivePreSettlesByPaymentDraft(payment.getId()).isEmpty()) {
            return payment.getDisplayAmount();
        }
        return this.sumChargeItemAmount(chargeItems);
    }

    /** 取消住院结算时医保撤销结果快照（应用层内部）。 */
    private static final class InsuranceRevokeSnapshot {
        private boolean insuranceRevoked;
        private String ybSetlId;
        private String settleMode;
    }
}
