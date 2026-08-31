/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.financial.refund.service;

import com.openhis.health.application.shared.pagination.PageQueryBinder;
import com.openhis.health.application.shared.pagination.PagedResultBuilder;
import com.openhis.health.application.shared.validator.CurrentPractitionerResolver;
import com.openhis.health.application.shared.financial.OutpatientTcmDecoctionChargeItemRecognizer;
import com.openhis.health.application.shared.financial.PaymentChannelOptionProvider;
import com.openhis.health.application.shared.validator.PaymentChannelGate;
import com.openhis.health.application.outpatient.financial.refund.support.PaymentChannelRefundOrchestrator;
import com.openhis.health.application.shared.payment.ThirdPartyPaymentRefundOrchestrator;
import com.openhis.health.application.outpatient.financial.charge.dto.PaymentDetailSubmitDto;
import com.openhis.health.application.outpatient.financial.charge.request.ConfirmOutpatientChargeSettlementRequest;
import com.openhis.health.application.outpatient.financial.charge.request.PreSettleOutpatientChargesRequest;
import com.openhis.health.application.outpatient.financial.charge.response.PreSettleOutpatientChargesResponse;
import com.openhis.health.application.outpatient.financial.charge.service.OutpatientChargeAppService;
import com.openhis.health.domain.share.constants.OutpatientRefundConstants;
import com.openhis.health.domain.share.constants.OutpatientRefundQueueTab;
import com.openhis.health.application.outpatient.financial.refund.dto.OutpatientRefundLineItemDto;
import com.openhis.health.application.outpatient.financial.refund.dto.OutpatientRefundPaymentGroupDto;
import com.openhis.health.application.outpatient.financial.refund.dto.OutpatientRefundQueueRowDto;
import com.openhis.health.application.outpatient.financial.refund.dto.RefundPaymentDetailSubmitDto;
import com.openhis.health.application.outpatient.financial.refund.mapper.OutpatientRefundMapper;
import com.openhis.health.application.outpatient.financial.refund.request.ExecuteOutpatientBillRefundByPaymentRequest;
import com.openhis.health.application.outpatient.financial.refund.request.ListOriginalPayChannelCodesRequest;
import com.openhis.health.application.outpatient.financial.refund.request.LoadOutpatientRefundExecutionDetailRequest;
import com.openhis.health.application.outpatient.financial.refund.request.LoadOutpatientRefundPageInitRequest;
import com.openhis.health.application.outpatient.financial.refund.request.PageOutpatientRefundQueueRequest;
import com.openhis.health.application.outpatient.financial.refund.request.SwitchPreservedChargeItemsToInsuranceRequest;
import com.openhis.health.application.outpatient.financial.refund.request.SwitchPreservedChargeItemsToSelfPayRequest;
import com.openhis.health.application.outpatient.financial.refund.request.VerifyRefundableBeforeExecutionRequest;
import com.openhis.health.application.outpatient.financial.refund.response.ExecuteOutpatientBillRefundByPaymentResponse;
import com.openhis.health.application.outpatient.financial.refund.response.LoadOutpatientRefundExecutionDetailResponse;
import com.openhis.health.application.outpatient.financial.refund.response.LoadOutpatientRefundPageInitResponse;
import com.openhis.health.application.outpatient.financial.refund.response.PageOutpatientRefundQueueResponse;
import com.openhis.health.application.outpatient.financial.refund.response.SwitchPreservedChargeItemsAccountResponse;
import com.openhis.health.application.outpatient.financial.refund.response.VerifyRefundableBeforeExecutionResponse;
import com.openhis.health.domain.administration.chargeitem.command.BatchChargeItemIdsCommand;
import com.openhis.health.domain.administration.chargeitem.command.SwitchChargeItemAccountCommand;
import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitem.query.ChargeItemQueryCommand;
import com.openhis.health.domain.administration.chargeitem.query.OutpatientChargeQueueEncounterQuery;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemDomainService;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.openhis.health.domain.administration.chargeitemdefinition.entity.ChargeItemDefinition;
import com.openhis.health.domain.administration.chargeitemdefinition.service.ChargeItemDefinitionQueryService;
import com.openhis.health.domain.administration.account.service.AccountQueryService;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.application.shared.encounter.EncounterContextReader;
import com.openhis.health.application.outpatient.financial.charge.support.OutpatientChargeEncounterQueueLoader;
import com.openhis.health.domain.administration.practitioner.service.PractitionerQueryService;
import com.openhis.health.domain.administration.account.entity.Account;
import com.openhis.health.domain.financial.paymentreconciliation.command.CompleteOutpatientRefundCommand;
import com.openhis.health.domain.financial.paymentreconciliation.command.PaymentDetailItemCommand;
import com.openhis.health.domain.financial.paymentreconciliation.command.RequestOutpatientRefundCommand;
import com.openhis.health.domain.financial.paymentreconciliation.queries.model.ChargeItemIdsCollectionQueryModel;
import com.openhis.health.domain.financial.paymentreconciliation.queries.model.PaymentRecDetailQueryModel;
import com.openhis.health.domain.financial.paymentreconciliation.queries.model.PaymentReconciliationQueryModel;
import com.openhis.health.domain.financial.paymentreconciliation.service.PaymentReconciliationDomainService;
import com.openhis.health.domain.financial.paymentreconciliation.service.PaymentReconciliationQueryService;
import com.openhis.health.domain.financial.paymentreconciliation.support.PaymentYbSettlementSupport;
import com.openhis.health.domain.medication.medicationrequest.entity.MedicationRequest;
import com.openhis.health.domain.medication.medicationrequest.service.MedicationRequestQueryService;
import com.openhis.health.domain.administration.encounter.queries.model.OutpatientEncounterContextQueryModel;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.application.shared.product.ProductDefinitionDisplayTerminologyAssembler;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.domain.share.utils.MonetaryAmountSupport;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.OutpatientChargeConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.administration.invoice.command.CancelInvoiceCommand;
import com.openhis.health.domain.administration.invoice.entity.Invoice;
import com.openhis.health.domain.administration.invoice.query.InvoiceQueryCommand;
import com.openhis.health.domain.administration.invoice.service.InvoiceDomainService;
import com.openhis.health.domain.administration.invoice.service.InvoiceQueryService;
import com.openhis.health.domain.share.enums.ChargeItemStatus;
import com.openhis.health.domain.share.enums.InvoiceStatus;
import com.openhis.health.infrastructure.integration.einvoice.orchestration.BossElectronicInvoiceAfterCommitOrchestration;
import com.openhis.health.domain.share.enums.PaymentChannel;
import com.openhis.health.domain.share.enums.PaymentStatus;
import com.openhis.health.domain.share.enums.RequestStatus;
import com.openhis.health.domain.share.utils.GatewayIdParseUtil;
import com.openhis.health.domain.terminology.service.CodeSystemQueryService;
import com.openhis.health.domain.workflow.devicerequest.entity.DeviceRequest;
import com.openhis.health.domain.workflow.devicerequest.service.DeviceRequestQueryService;
import com.openhis.whale.core.application.dto.OptionDto;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.health.infrastructure.integration.insurance.outpatient.OutpatientChargeInsuranceExtension;
import com.openhis.health.infrastructure.integration.insurance.outpatient.ExtensionIn.OutpatientChargeRevokeSettleExtensionIn;
import com.openhis.health.infrastructure.integration.insurance.outpatient.ExtensionOut.OutpatientChargeSettlementRecordExtensionOut;
import com.openhis.health.infrastructure.integration.workinjury.WorkInjuryOutpatientSettlementExtension;
import com.openhis.health.infrastructure.integration.workinjury.ExtensionOut.WorkInjurySettlementRecordExtensionOut;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 门诊退费应用服务（AS-OP-FIN-003，IF-REF-01～06）。
 *
 * <p>职责边界：</p>
 * <ul>
 *   <li>队列与执行明细只读编排，写侧经支付域与收费明细域收口。</li>
 *   <li>半退保留行再结算委托 {@link OutpatientChargeAppService}，不重复 IF-OPC-06/07 编排。</li>
 * </ul>
 *
 * <p>接入说明：由 AppServiceControllerRegistrar 注册为
 * POST /api/app/outpatientRefund/{methodName}；不另写 RestController。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutpatientRefundAppService {

    /** 写路径操作人门控（B.6.1）。 */
    private final CurrentPractitionerResolver currentPractitionerResolver;

    /** 支付渠道入站门控。 */
    private final PaymentChannelGate paymentChannelGate;

    /** 收银支付渠道下拉（477 ∩ Gate）。 */
    private final PaymentChannelOptionProvider paymentChannelOptionProvider;

    private final ThirdPartyPaymentRefundOrchestrator thirdPartyPaymentRefundOrchestrator;

    /** 原支付渠道读侧（退费前是否可能银联原路退）。 */
    private final PaymentChannelRefundOrchestrator paymentChannelRefundOrchestrator;

    /** 术语/枚举下拉。 */
    private final CodeSystemQueryService codeSystemQueryService;

    /** 就诊存在性门禁。 */
    private final EncounterQueryService encounterQueryService;
    private final EncounterContextReader EncounterContextReader;

    /** 收费明细读侧。 */
    private final ChargeItemQueryService chargeItemQueryService;
    private final OutpatientChargeEncounterQueueLoader outpatientChargeEncounterQueueLoader;

    /** 收费明细写侧（收口与转账户）。 */
    private final ChargeItemDomainService chargeItemDomainService;

    /** 支付流水读侧。 */
    private final PaymentReconciliationQueryService paymentReconciliationQueryService;

    /** 支付流水写侧（退费链）。 */
    private final PaymentReconciliationDomainService paymentReconciliationDomainService;

    /** 药品申请读侧（IF-REF-04）。 */
    private final MedicationRequestQueryService medicationRequestQueryService;

    /** 耗材申请读侧（IF-REF-04）。 */
    private final DeviceRequestQueryService deviceRequestQueryService;

    /** 账户解析（IF-REF-06）。 */
    private final AccountQueryService accountQueryService;

    /** 门诊代煎费识别：半退保留行转账户时排除，保持自费费用性质。 */
    private final OutpatientTcmDecoctionChargeItemRecognizer outpatientTcmDecoctionChargeItemRecognizer;

    /** 定价定义名称回落。 */
    private final ChargeItemDefinitionQueryService chargeItemDefinitionQueryService;

    /** 产品定义名称回落。 */
    private final ProductDefinitionDisplayTerminologyAssembler productDefinitionDisplayTerminologyAssembler;

    /** 原支付收款人（enterer_id）姓名回落。 */
    private final PractitionerQueryService practitionerQueryService;

    /** 半退保留行预结算与正式结算。 */
    private final OutpatientChargeAppService outpatientChargeAppService;

    /** 票据写侧（退费后 DRAFT 作废 / ISSUED 冲红）。 */
    private final InvoiceDomainService invoiceDomainService;

    /** 票据读侧。 */
    private final InvoiceQueryService invoiceQueryService;

    /** 博思冲红 afterCommit 编排。 */
    private final BossElectronicInvoiceAfterCommitOrchestration bossElectronicInvoiceAfterCommitOrchestration;

    /** 门诊第三方结算扩展（未装 Module 时为 NoOp）。 */
    private final OutpatientChargeInsuranceExtension chargeInsuranceExtension;

    /** 工伤门诊结算扩展（2208 退费 invoke）。 */
    private final WorkInjuryOutpatientSettlementExtension workInjurySettleExtension;

    /** Request/Response 与 Command 装配。 */
    private final OutpatientRefundMapper outpatientRefundMapper;

    /**
     * IF-REF-01：加载退费页初始化数据。
     *
     * @param request 初始化请求
     * @return 收费项状态筛选项与支付渠道下拉（退费窗语义子集）
     * @throws BusinessException 装配失败时抛出 EX-REF-101
     */
    public LoadOutpatientRefundPageInitResponse loadOutpatientRefundPageInit(LoadOutpatientRefundPageInitRequest request) {
        try {
            // Pre：退费窗仅暴露退费中/已退费等过程态，不提供收银 PLANNED/SETTLED 口径选项。
            List<OptionDto> allOptions = codeSystemQueryService.getOptionListByEnum(ChargeItemStatus.class);
            List<OptionDto> filtered = this.filterRefundPageInitOptions(allOptions);
            // Pre：用户可选支付渠道与门诊收费/挂号口径对齐（ADR-PAY-001）。
            List<OptionDto> paymentChannels = paymentChannelOptionProvider.loadUserSelectableOptions();
            // Asm：装配初始化下拉并返回。
            return outpatientRefundMapper.toLoadOutpatientRefundPageInitResponse(filtered, paymentChannels);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(
                ErrorCodeConstants.ENCOUNTER_RULE_CONFLICT,
                this.buildMessage(OutpatientRefundConstants.MESSAGE_CODE_INIT_LOAD_FAILED, "初始化数据不可用"));
        }
    }

    /**
     * IF-REF-02：分页查询待退费患者队列。
     *
     * @param request 分页与页签请求
     * @return 分页队列
     * @throws BusinessException 查询失败时抛出 EX-REF-102
     */
    public PageOutpatientRefundQueueResponse pageOutpatientRefundQueue(PageOutpatientRefundQueueRequest request) {
        try {
            // Gate：分页参数与页签类型必须合法。
            this.validateQueueRequest(request);
            // Pre：按页签收敛 REFUNDING/REFUNDED 收费事实分页就诊，再回补门诊信息并打退费标签。
            OutpatientChargeQueueEncounterQuery queueEncounterQuery = new OutpatientChargeQueueEncounterQuery();
            PageQueryBinder.bind(request, queueEncounterQuery);
            queueEncounterQuery.setExcludedServiceTable(TableNameConstants.ADM_HEALTHCARE_SERVICE);
            Page<Long> encounterPage =
                this.outpatientChargeEncounterQueueLoader.pageEncounterIdsForOutpatientRefundQueue(
                    queueEncounterQuery, request.getQueueTabCode());
            List<OutpatientEncounterContextQueryModel> infos =
                EncounterContextReader.listOutpatientContextsByEncounterIdsOrdered(encounterPage.getContent());
            Page<OutpatientEncounterContextQueryModel> pageShell =
                new PageImpl<>(infos, encounterPage.getPageable(), encounterPage.getTotalElements());
            Map<Long, String> flags = this.resolveQueueFlags(infos);
            List<OutpatientRefundQueueRowDto> records = outpatientRefundMapper.toOutpatientRefundQueueRowDtoList(infos, flags);
            List<OutpatientRefundQueueRowDto> filtered = new ArrayList<>();
            for (OutpatientRefundQueueRowDto record : records) {
                // 全部页签：直接透传所有队列行。
                if (OutpatientRefundQueueTab.ALL.equals(request.getQueueTabCode())) {
                    filtered.add(record);
                    continue;
                }
                // 已退页签：DB 已按存在 REFUNDED 收敛；半退后仍有拟退时也保留，不再按标签二次剔除。
                if (OutpatientRefundQueueTab.SETTLED.equals(request.getQueueTabCode())) {
                    record.setQueueLabelCode(OutpatientRefundQueueTab.SETTLED);
                    filtered.add(record);
                    continue;
                }
                // 待退页签：仅保留仍有拟退（标签为 PENDING）的行。
                if (request.getQueueTabCode().equals(record.getQueueLabelCode())) {
                    filtered.add(record);
                }
            }
            // Asm：封装分页响应。
            return this.toQueuePageResponse(pageShell, filtered);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(
                ErrorCodeConstants.ENCOUNTER_RULE_CONFLICT,
                this.buildMessage(OutpatientRefundConstants.MESSAGE_CODE_QUEUE_QUERY_FAILED, "队列查询失败"));
        }
    }

    /**
     * IF-REF-03：加载就诊退费执行明细（待执行拟退 / 已退费终态 / 合并）。
     *
     * @param request 就诊主键与展示口径
     * @return 按原支付分组的明细行
     * @throws BusinessException 待执行口径下无可退费时抛出 EX-REF-103
     */
    public LoadOutpatientRefundExecutionDetailResponse loadOutpatientRefundExecutionDetail(
        LoadOutpatientRefundExecutionDetailRequest request) {
        try {
            // Gate：就诊主键须非空且就诊须存在。
            this.validateEncounterId(request.getEncounterId());
            encounterQueryService.mustGetEncounter(request.getEncounterId());
            // Pre：按展示口径组装按原支付分组的退费明细。
            String detailViewCode = this.resolveRefundDetailViewCode(request.getDetailViewCode());
            List<OutpatientRefundPaymentGroupDto> groups =
                this.buildRefundPaymentGroupsByView(request.getEncounterId(), detailViewCode);
            // Gate：待执行口径下无 REFUNDING 行则拒绝，避免收费窗误操作。
            if (groups.isEmpty() && OutpatientRefundQueueTab.PENDING.equals(detailViewCode)) {
                throw new BusinessException(
                    ErrorCodeConstants.CHARGE_ITEM_NOT_FOUND,
                    this.buildMessage(OutpatientRefundConstants.MESSAGE_CODE_EXECUTION_DETAIL_INVALID, "当前就诊无可执行退费"));
            }
            // Asm：返回按支付分组的执行明细。
            return outpatientRefundMapper.toLoadOutpatientRefundExecutionDetailResponse(request.getEncounterId(), groups);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(
                ErrorCodeConstants.ENCOUNTER_RULE_CONFLICT,
                this.buildMessage(OutpatientRefundConstants.MESSAGE_CODE_EXECUTION_DETAIL_INVALID, "明细加载失败"));
        }
    }

    /**
     * IF-REF-04：执行前可退门禁。
     *
     * @param request 就诊与拟退行
     * @return 是否允许继续
     */
    public VerifyRefundableBeforeExecutionResponse verifyRefundableBeforeExecution(
        VerifyRefundableBeforeExecutionRequest request) {
        // Gate：就诊与拟退收费明细须非空且归属一致。
        this.validateEncounterId(request.getEncounterId());
        GatewayIdParseUtil.requireNonEmptyLongIds(request.getRefundingChargeItemIds(), "拟退收费明细不能为空");
        encounterQueryService.mustGetEncounter(request.getEncounterId());
        // Pre：加载拟退行并校验归属就诊。
        ChargeItemQueryCommand loadCmd = new ChargeItemQueryCommand();
        loadCmd.setIds(request.getRefundingChargeItemIds());
        List<ChargeItem> rows = chargeItemQueryService.loadChargeItemsByIds(loadCmd);
        this.assertChargeItemsBelongEncounter(rows, request.getEncounterId(), OutpatientRefundConstants.MESSAGE_CODE_CHARGE_ITEM_STATUS_CHANGED);
        // Gate：退药/退耗未完成则返回拒绝码。
        String rejectCode = this.runRefundableGate(rows);
        if (rejectCode != null) {
            // Asm：门禁不通过，返回拒绝码供前端展示。
            return outpatientRefundMapper.toVerifyRefundableBeforeExecutionResponse(false, rejectCode);
        }
        // Asm：门禁通过。
        return outpatientRefundMapper.toVerifyRefundableBeforeExecutionResponse(true, null);
    }

    /**
     * 按原支付主单查询落库渠道码（退费前判定是否可能银联原路退）。
     *
     * @param request 原支付主单主键
     * @return {@code fin_payment_rec_detail.pay_enum} 去重列表；无明细时返回空表
     */
    public List<String> listOriginalPayChannelCodes(ListOriginalPayChannelCodesRequest request) {
        if (request == null || request.getPaymentId() == null) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "paymentId 不能为空");
        }
        return paymentChannelRefundOrchestrator.listDistinctPayChannelCodes(request.getPaymentId());
    }

    /**
     * IF-REF-05：按原支付主单执行门诊账单退费。
     *
     * @param request 单笔支付退费请求
     * @return 执行结果
     * @throws BusinessException 支付/行状态/渠道金额/再结算失败等
     */
    public ExecuteOutpatientBillRefundByPaymentResponse executeOutpatientBillRefundByPayment(
        ExecuteOutpatientBillRefundByPaymentRequest request) {
        // Gate：退费请求参数、渠道与金额须完整合法。
        this.validateExecuteRefundRequest(request);
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.PRACTITIONER_INVALID_ARGUMENT,
            "当前会话操作人不能为空");
        encounterQueryService.mustGetEncounter(request.getEncounterId());
        // Pre：加载原支付单并校验可退状态与就诊一致。
        PaymentReconciliationQueryModel originalPayment =
            paymentReconciliationQueryService.mustGetPayment(request.getOriginalPaymentId());
        if (!request.getEncounterId().equals(originalPayment.getEncounterId())) {
            throw new BusinessException(
                ErrorCodeConstants.PAYMENT_RECONCILIATION_RULE_CONFLICT,
                this.buildMessage(OutpatientRefundConstants.MESSAGE_CODE_PAYMENT_NOT_REFUNDABLE, "支付与就诊不一致"));
        }
        this.assertOriginalPaymentRefundable(originalPayment);
        ChargeItemQueryCommand loadCmd = new ChargeItemQueryCommand();
        loadCmd.setIds(request.getRefundingChargeItemIds());
        List<ChargeItem> refundingRows = chargeItemQueryService.loadChargeItemsByIds(loadCmd);
        this.assertRefundingChargeItems(refundingRows, request.getRefundingChargeItemIds(), request.getEncounterId());
        // Gate：同一原支付须一次执行退清，禁止分次退费（BR-REF-021）。
        this.assertNoSplitRefundOnPaymentExecute(
            request.getEncounterId(),
            request.getOriginalPaymentId(),
            request.getRefundingChargeItemIds());
        // Gate：退药/退耗门禁与实退金额须等于应退合计。
        String gateReject = this.runRefundableGate(refundingRows);
        if (gateReject != null) {
            if (OutpatientRefundConstants.MESSAGE_CODE_MEDICATION_RETURN_PENDING.equals(gateReject)) {
                throw new BusinessException(ErrorCodeConstants.CHARGE_ITEM_RULE_CONFLICT, this.buildMessage(gateReject, "请先完成退药后再退费"));
            }
            throw new BusinessException(ErrorCodeConstants.CHARGE_ITEM_RULE_CONFLICT, this.buildMessage(gateReject, "请先完成退耗材后再退费"));
        }
        List<PaymentRecDetailQueryModel> originalPaymentDetails =
            paymentReconciliationQueryService.listPaymentRecDetailsByPaymentId(request.getOriginalPaymentId());
        // Gate：原单明细须与收费项合计勾稽，禁止把少记/多记自付复制到逆向单（BR-REF-024）
        this.assertOriginalPaymentDetailsBalancedWithCharges(originalPayment, originalPaymentDetails);
        // 原路返回：实退分桶一律取自原支付明细（含医保统筹/个账）；忽略前端自选支付方式。
        // 产品半退时资金侧仍对该 paymentId 整单冲退（AS-OP-FIN-003 §1.3），渠道合计取原支付全额。
        List<RefundPaymentDetailSubmitDto> effectiveRefundPaymentDetails =
            this.buildFullOriginalRefundPaymentDetails(originalPaymentDetails);
        // 渠道实退合计（含医保统筹/个账）；与主单患者应付可能不一致（医保全报 display=0）
        BigDecimal channelRefundTotal = this.sumRefundPaymentDetails(effectiveRefundPaymentDetails);
        BigDecimal expectedRefund = channelRefundTotal;
        BigDecimal actualRefund = channelRefundTotal;
        // Gate：应退与渠道实退须在结算刻度一致（BR-MON-001）
        if (!MonetaryAmountSupport.equalsAtSettleScale(expectedRefund, actualRefund)) {
            throw new BusinessException(
                ErrorCodeConstants.PAYMENT_RECONCILIATION_RULE_CONFLICT,
                this.buildMessage(OutpatientRefundConstants.MESSAGE_CODE_REFUND_AMOUNT_MISMATCH, "实退合计与应退不符"));
        }
        // Pre：工伤/医保撤销须在本地逆向落账之前完成。
        boolean hadInsuranceSettlement =
            chargeInsuranceExtension.isEnabled()
                && !chargeInsuranceExtension.listSettlementsByPayment(request.getOriginalPaymentId()).isEmpty();
        this.runWorkInjuryRevokeBeforeRefund(request.getOriginalPaymentId());
        this.runInsuranceRevokeBeforeRefund(
            request.getOriginalPaymentId(),
            originalPayment,
            operatorId,
            request.getEncounterId());
        LocalDateTime billDate = this.resolveBillDate(request.getBillDate());
        String outcomeEnum = this.resolvePaymentOutcomeEnum(originalPayment);
        List<Long> paymentIdList = new ArrayList<>();
        paymentIdList.add(request.getOriginalPaymentId());
        try {
            thirdPartyPaymentRefundOrchestrator.applyRefundBeforeLocalRefund(
                request.getOriginalPaymentId(),
                request.getClientRefundResult(),
                operatorId);
        } catch (RuntimeException refundEx) {
            throw this.wrapLocalRefundFailureAfterExternalReverse(refundEx, hadInsuranceSettlement);
        }
        // 医保已撤或银联已退货：后续本地失败须提示强制重试，禁止当「未退费」再收一次。
        boolean externalReverseCompleted =
            hadInsuranceSettlement || request.getClientRefundResult() != null;
        List<PaymentDetailItemCommand> refundPaymentDetailCommands =
            outpatientRefundMapper.toPaymentDetailItemCommands(effectiveRefundPaymentDetails);
        // 明细终态：禁止落 DRAFT；原明细缺失时回落本笔 outcome
        this.fillRefundDetailResultEnum(refundPaymentDetailCommands, outcomeEnum);
        // Gate：退费渠道拆分须与原支付明细一致（原路返回，BR-REF-011）。
        this.assertRefundPaymentDetailsMatchOriginalPayment(effectiveRefundPaymentDetails, originalPaymentDetails);
        // Pre：前端仅提交渠道与实退额；落库前须写回 accountId，避免领域明细自检「支付明细账户标识不能为空」。
        this.attachOriginalPaymentAccountsToRefundPaymentDetails(
            refundPaymentDetailCommands, originalPaymentDetails, request.getEncounterId(), refundingRows);
        RequestOutpatientRefundCommand refundCommand = outpatientRefundMapper.toRequestOutpatientRefundCommand(
            request,
            originalPayment,
            operatorId,
            this.joinIds(request.getRefundingChargeItemIds()),
            expectedRefund,
            refundPaymentDetailCommands);
        Long reversePaymentId = null;
        try {
            // Cmd：创建逆向单（REFUND_PROCESSING + relation_id）；原收费单保持 SUCCESS
            reversePaymentId = paymentReconciliationDomainService.requestOutpatientRefund(refundCommand);
            CompleteOutpatientRefundCommand completeCommand =
                outpatientRefundMapper.toCompleteOutpatientRefundCommand(reversePaymentId, outcomeEnum, billDate);
            // 逆向桥表：拷贝原单 setl 为 REVOKED，与 2208 后原单同态，供日结行级过滤
            PaymentReconciliationQueryModel originalForYb =
                paymentReconciliationQueryService.mustGetPayment(request.getOriginalPaymentId());
            completeCommand.setYbSettlements(
                PaymentYbSettlementSupport.copyAsRevoked(originalForYb.getYbSettlements()));
            // Cmd：逆向单收口 REFUNDED 并发布唯一退费完成事件
            paymentReconciliationDomainService.completeOutpatientRefund(completeCommand);
            List<Long> snapshotIds = this.resolvePaymentSnapshotChargeItemIds(request.getOriginalPaymentId());
            List<Long> completionIds = chargeItemQueryService.resolveChargeItemIdsForOutpatientRefundBillCompletion(
                request.getEncounterId(), snapshotIds);
            BatchChargeItemIdsCommand billRefundCommand =
                outpatientRefundMapper.toBatchChargeItemIdsCommand(completionIds, operatorId);
            // Cmd：退费完成覆盖 ChargeItem 收费人/收费时间（与逆向 payment 同源）
            billRefundCommand.setBillDate(billDate);
            chargeItemDomainService.completeOutpatientBillRefund(billRefundCommand);
            // 资金退费成功后收口原支付关联票据：DRAFT 本地作废；ISSUED 冲红（半退再结算会另开新票）。
            this.voidOrWriteOffInvoiceLinkedToPayment(
                request.getOriginalPaymentId(),
                operatorId,
                "门诊退费");
            boolean halfRefundExecuted = false;
            if (request.getPreservedNewChargeItemIds() != null && !request.getPreservedNewChargeItemIds().isEmpty()) {
                halfRefundExecuted = true;
                if (outcomeEnum == null) {
                    throw new BusinessException(
                        ErrorCodeConstants.PAYMENT_RECONCILIATION_RULE_CONFLICT,
                        this.buildMessage(
                            OutpatientRefundConstants.MESSAGE_CODE_HALF_REFUND_SETTLE_FAILED,
                            "原支付结果码缺失，无法半退再结算"));
                }
                // Cmd：半退保留行当场预结算并正式结算（委托收费域 IF-OPC-06/07）。
                this.preSettleAndConfirmPreservedLines(request, billDate, outcomeEnum, originalPaymentDetails);
            }
            log.info(
                "outpatient refund executed encounterId={} originalPaymentId={} reversePaymentId={} refundingCount={}",
                request.getEncounterId(),
                request.getOriginalPaymentId(),
                reversePaymentId,
                request.getRefundingChargeItemIds().size());
            // Asm：返回逆向支付单 ID 与半退标志。
            return outpatientRefundMapper.toExecuteOutpatientBillRefundByPaymentResponse(
                reversePaymentId, request.getOriginalPaymentId(), halfRefundExecuted);
        } catch (BusinessException businessException) {
            this.rollbackOutpatientRefundAttempt(reversePaymentId);
            throw this.wrapLocalRefundFailureAfterExternalReverse(businessException, externalReverseCompleted);
        } catch (RuntimeException runtimeException) {
            this.rollbackOutpatientRefundAttempt(reversePaymentId);
            throw this.wrapLocalRefundFailureAfterExternalReverse(runtimeException, externalReverseCompleted);
        }
    }

    /**
     * 第三方已撤销后本地落账失败：改写提示，要求立即重试退费、禁止再收一次。
     *
     * @param cause                    原异常
     * @param externalReverseCompleted 医保/银联撤销是否已完成
     * @return 包装后的业务异常，或原异常（未完成外部撤销时）
     */
    private RuntimeException wrapLocalRefundFailureAfterExternalReverse(
            RuntimeException cause, boolean externalReverseCompleted) {
        if (!externalReverseCompleted || cause == null) {
            return cause;
        }
        String detail = cause.getMessage();
        if (detail == null || detail.isBlank()) {
            detail = "院内落账失败";
        }
        String message = "第三方（医保/银联）已撤销成功，但院内退费未完成，请立即重试退费，切勿再次收费。原因：" + detail;
        if (cause instanceof BusinessException businessException) {
            String code = businessException.getErrorCode();
            if (code == null || code.isBlank()) {
                code = "REF-013";
            }
            BusinessException wrapped = new BusinessException(code, message);
            wrapped.addSuppressed(cause);
            return wrapped;
        }
        BusinessException wrapped = new BusinessException("REF-013", message);
        wrapped.addSuppressed(cause);
        return wrapped;
    }

    /**
     * 本地退费编排失败时回滚已创建的逆向单，清空 relation_id 以便重试。
     *
     * @param reversePaymentId 逆向支付主键；未创建时可 {@code null}
     */
    private void rollbackOutpatientRefundAttempt(Long reversePaymentId) {
        if (reversePaymentId == null) {
            return;
        }
        paymentReconciliationDomainService.abortOutpatientRefundAttempt(reversePaymentId);
    }

    /**
     * 门诊退费后作废/冲红原支付关联票据：DRAFT → voidDraftInvoice；ISSUED → writeoff。
     *
     * @param paymentId  被退费的原正向支付单 ID
     * @param operatorId 操作员 ID
     * @param reason     作废/冲红原因
     */
    private void voidOrWriteOffInvoiceLinkedToPayment(Long paymentId, Long operatorId, String reason) {
        InvoiceQueryCommand query = new InvoiceQueryCommand();
        query.setPaymentId(paymentId);
        Invoice invoice = invoiceQueryService.getInvoiceByPaymentId(query);
        if (invoice == null || invoice.getId() == null) {
            return;
        }
        if (InvoiceStatus.CANCELLED.sameAs(invoice.getStatusEnum())) {
            return;
        }
        CancelInvoiceCommand cancelCommand = new CancelInvoiceCommand();
        cancelCommand.setInvoiceId(invoice.getId());
        cancelCommand.setReason(reason);
        cancelCommand.setOperatorId(operatorId);
        if (InvoiceStatus.DRAFT.sameAs(invoice.getStatusEnum())) {
            invoiceDomainService.voidDraftInvoice(cancelCommand);
            bossElectronicInvoiceAfterCommitOrchestration.afterInvoiceCancelled(invoice.getId(), reason);
            return;
        }
        if (InvoiceStatus.ISSUED.sameAs(invoice.getStatusEnum())) {
            invoiceDomainService.writeoffInvoiceAndCreateRedRecord(cancelCommand);
            bossElectronicInvoiceAfterCommitOrchestration.afterInvoiceCancelled(invoice.getId(), reason);
        }
    }

    /**
     * 退费前校验工伤 2208 已完成（invoke 链由前端 Win 执行）。
     */
    private void runWorkInjuryRevokeBeforeRefund(Long originalPaymentId) {
        if (originalPaymentId == null) {
            return;
        }
        List<WorkInjurySettlementRecordExtensionOut> records =
                workInjurySettleExtension.listSettlementsByPayment(originalPaymentId);
        if (records.isEmpty()) {
            return;
        }
        for (WorkInjurySettlementRecordExtensionOut record : records) {
            if (record == null) {
                continue;
            }
            if (!"REVOKED".equals(record.getStatus())) {
                throw new BusinessException(
                        "WI-008",
                        "工伤结算尚未撤销（2208），请先完成 Win invoke 撤销后再退费");
            }
        }
    }

    /**
     * 退费前撤销医保正式结算（2208）；混合结算按回执逐条撤销。
     */
    private void runInsuranceRevokeBeforeRefund(
        Long originalPaymentId,
        PaymentReconciliationQueryModel originalPayment,
        Long operatorId,
        Long encounterId) {
        if (!chargeInsuranceExtension.isEnabled() || originalPaymentId == null) {
            return;
        }
        List<OutpatientChargeSettlementRecordExtensionOut> records =
            chargeInsuranceExtension.listSettlementsByPayment(originalPaymentId);
        if (records.isEmpty()) {
            return;
        }
        for (OutpatientChargeSettlementRecordExtensionOut record : records) {
            if (record == null) {
                continue;
            }
            // Skip：本地已 REVOKED 不再打 2208（半退/重试退费时 list 仍含已撤行）
            if ("REVOKED".equals(record.getStatus())) {
                continue;
            }
            OutpatientChargeRevokeSettleExtensionIn command = new OutpatientChargeRevokeSettleExtensionIn();
            command.setSettlementRecordId(record.getSettlementRecordId());
            command.setPaymentId(originalPaymentId);
            command.setPreSettleId(record.getPreSettleId());
            command.setSettlementId(record.getSettlementId());
            try {
                // Cmd：退费前 Extension 2208 撤销原正式结算
                chargeInsuranceExtension.revokeSettle(command);
            } catch (RuntimeException ex) {
                String detail = ex.getMessage();
                if (detail == null || detail.isBlank()) {
                    detail = "平台返回失败";
                }
                throw new BusinessException(
                    "INS-013",
                    "医保结算撤销失败（2208）：" + detail + "；请勿重复退费，联系医保办处理");
            }
        }
    }

    /**
     * IF-REF-06：半退保留行转医保。
     *
     * @param request 就诊主键
     * @return 切换结果
     */
    public SwitchPreservedChargeItemsAccountResponse switchPreservedChargeItemsToInsurance(
        SwitchPreservedChargeItemsToInsuranceRequest request) {
        // Cmd：半退保留 PLANNED 行批量切换至医保账户。
        return this.switchPreservedAccount(request.getEncounterId(), true);
    }

    /**
     * IF-REF-06：半退保留行转自费。
     *
     * @param request 就诊主键
     * @return 切换结果
     */
    public SwitchPreservedChargeItemsAccountResponse switchPreservedChargeItemsToSelfPay(
        SwitchPreservedChargeItemsToSelfPayRequest request) {
        // Cmd：半退保留 PLANNED 行批量切换至自费账户。
        return this.switchPreservedAccount(request.getEncounterId(), false);
    }

    /**
     * 半退保留 PLANNED 行批量切换记账账户。
     *
     * @param encounterId 就诊主键
     * @param insurance true=医保；false=自费
     * @return 切换响应
     */
    private SwitchPreservedChargeItemsAccountResponse switchPreservedAccount(Long encounterId, boolean insurance) {
        // Gate：就诊须存在。
        this.validateEncounterId(encounterId);
        encounterQueryService.mustGetEncounter(encounterId);
        // Gate：须双账户并存；纯自费挂号不可在半退场景互转记账
        if (!accountQueryService.hasDualChargeAccounts(encounterId)) {
            throw new BusinessException(
                ErrorCodeConstants.ACCOUNT_NOT_FOUND,
                this.buildMessage(
                    OutpatientRefundConstants.MESSAGE_CODE_DUAL_ACCOUNT_REQUIRED,
                    "当前就诊为自费挂号，不支持转医保/转自费"));
        }
        // Pre：列举半退保留 PLANNED 行。
        List<ChargeItem> preservedRows = this.listPreservedPlannedChargeItems(encounterId);
        if (preservedRows.isEmpty()) {
            throw new BusinessException(
                ErrorCodeConstants.CHARGE_ITEM_RULE_CONFLICT,
                this.buildMessage(OutpatientRefundConstants.MESSAGE_CODE_NO_PRESERVED_LINES, "当前就诊无保留待再结算项"));
        }
        Long targetAccountId;
        // Pre：解析目标记账账户（医保或自费）。
        if (insurance) {
            targetAccountId = accountQueryService.getMedicalInsuranceAccount(encounterId);
        } else {
            targetAccountId = accountQueryService.getSelfPayAccount(encounterId);
        }
        if (targetAccountId == null) {
            throw new BusinessException(
                ErrorCodeConstants.ACCOUNT_NOT_FOUND,
                this.buildMessage(OutpatientRefundConstants.MESSAGE_CODE_ACCOUNT_NOT_FOUND, "未找到目标账户"));
        }
        // Cmd：批量切换收费项账户，不触发支付链；代煎行强制自费，不参与切换。
        SwitchChargeItemAccountCommand command = new SwitchChargeItemAccountCommand();
        command.setOperatorId(currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.PRACTITIONER_INVALID_ARGUMENT,
            "当前会话操作人不能为空"));
        List<SwitchChargeItemAccountCommand.Item> items = new ArrayList<>();
        for (ChargeItem row : preservedRows) {
            // 代煎挂个人现金自费户，半退保留行转账户时跳过（BR-REF-010）
            if (outpatientTcmDecoctionChargeItemRecognizer.isDecoctionChargeItem(row)) {
                continue;
            }
            SwitchChargeItemAccountCommand.Item item = new SwitchChargeItemAccountCommand.Item();
            item.setChargeItemId(row.getId());
            item.setAccountId(targetAccountId);
            items.add(item);
        }
        // 仅代煎保留行时无目标可切，幂等成功
        if (items.isEmpty()) {
            return outpatientRefundMapper.toSwitchPreservedChargeItemsAccountResponse(true);
        }
        command.setItems(items);
        chargeItemDomainService.switchChargeItemAccount(command);
        // Asm：返回切换成功标志。
        return outpatientRefundMapper.toSwitchPreservedChargeItemsAccountResponse(true);
    }

    /**
     * 解析 IF-REF-03 明细展示口径（空则待执行拟退）。
     *
     * @param raw 请求入参
     * @return {@link OutpatientRefundQueueTab} 字面量
     */
    private String resolveRefundDetailViewCode(String raw) {
        if (StringUtils.isEmpty(raw)) {
            return OutpatientRefundQueueTab.PENDING;
        }
        String trimmed = raw.trim().toUpperCase();
        if (OutpatientRefundQueueTab.SETTLED.equals(trimmed)) {
            return OutpatientRefundQueueTab.SETTLED;
        }
        if (OutpatientRefundQueueTab.ALL.equals(trimmed)) {
            return OutpatientRefundQueueTab.ALL;
        }
        return OutpatientRefundQueueTab.PENDING;
    }

    /**
     * 按展示口径组装支付分组明细。
     *
     * @param encounterId     就诊主键
     * @param detailViewCode  PENDING / SETTLED / ALL
     * @return 分组列表
     */
    private List<OutpatientRefundPaymentGroupDto> buildRefundPaymentGroupsByView(Long encounterId, String detailViewCode) {
        if (OutpatientRefundQueueTab.SETTLED.equals(detailViewCode)) {
            return this.buildCompletedRefundPaymentGroups(encounterId);
        }
        if (OutpatientRefundQueueTab.ALL.equals(detailViewCode)) {
            return this.mergeRefundPaymentGroups(
                this.buildExecutionPaymentGroups(encounterId),
                this.buildCompletedRefundPaymentGroups(encounterId));
        }
        return this.buildExecutionPaymentGroups(encounterId);
    }

    /**
     * 合并待执行与已退费分组（同一原支付优先保留待执行分组）。
     *
     * @param pendingGroups   待执行分组
     * @param completedGroups 已退费分组
     * @return 合并结果
     */
    private List<OutpatientRefundPaymentGroupDto> mergeRefundPaymentGroups(
        List<OutpatientRefundPaymentGroupDto> pendingGroups,
        List<OutpatientRefundPaymentGroupDto> completedGroups
    ) {
        Map<Long, OutpatientRefundPaymentGroupDto> merged = new HashMap<>();
        if (pendingGroups != null) {
            for (OutpatientRefundPaymentGroupDto group : pendingGroups) {
                if (group != null && group.getPaymentId() != null) {
                    merged.put(group.getPaymentId(), group);
                }
            }
        }
        if (completedGroups != null) {
            for (OutpatientRefundPaymentGroupDto group : completedGroups) {
                if (group == null || group.getPaymentId() == null) {
                    continue;
                }
                if (!merged.containsKey(group.getPaymentId())) {
                    merged.put(group.getPaymentId(), group);
                }
            }
        }
        return new ArrayList<>(merged.values());
    }

    /**
     * 判断是否为门诊账单逆向退款支付单（{@code relation_id} 指向原支付主单）。
     *
     * <p>IF-REF-03 按<strong>原支付</strong>分组展示；逆向单与源单挂载同一批收费项快照，
     * 若并入分组会导致「原支付单号 + 已退明细」成对重复（PMR-xxxx 原单 + PMR-xxxx+1 逆向单）。</p>
     *
     * @param payment 支付读模型
     * @return true 表示逆向退款单，读侧分组应排除
     */
    private boolean isOutpatientRefundReversePayment(PaymentReconciliationQueryModel payment) {
        return payment != null && payment.getRelationId() != null;
    }

    /**
     * 构建 IF-REF-03 待执行支付分组（拟退 REFUNDING + 半退保留 PLANNED）。
     *
     * @param encounterId 就诊主键
     * @return 分组列表
     */
    private List<OutpatientRefundPaymentGroupDto> buildExecutionPaymentGroups(Long encounterId) {
        List<PaymentReconciliationQueryModel> payments = paymentReconciliationQueryService.findPaymentsByEncounter(encounterId);
        List<PaymentReconciliationQueryModel> refundablePayments = new ArrayList<>();
        for (PaymentReconciliationQueryModel payment : payments) {
            if (payment.getStatusEnum() == null) {
                continue;
            }
            // 逆向退款单不是「原支付」分组锚点，排除以免与源单重复挂载同一批收费项。
            if (this.isOutpatientRefundReversePayment(payment)) {
                continue;
            }
            // 待执行退费：原支付须仍为成功态；PARTIAL_REFUNDED 表示已分次退过，禁止再次受理（BR-REF-021）。
            if (PaymentStatus.SUCCESS.equals(payment.getStatusEnum())) {
                refundablePayments.add(payment);
            }
        }
        if (refundablePayments.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> paymentIds = new ArrayList<>();
        for (PaymentReconciliationQueryModel payment : refundablePayments) {
            paymentIds.add(payment.getId());
        }
        List<ChargeItemIdsCollectionQueryModel> snapshotCollections =
            paymentReconciliationQueryService.getChargeItemIdsByPayments(paymentIds);
        Map<Long, List<Long>> chargeIdsByPayment = new HashMap<>();
        for (ChargeItemIdsCollectionQueryModel collection : snapshotCollections) {
            chargeIdsByPayment.put(collection.getPaymentId(), collection.getChargeItemIds());
        }
        LinkedHashSet<Long> allChargeIds = new LinkedHashSet<>();
        for (List<Long> ids : chargeIdsByPayment.values()) {
            if (ids != null) {
                allChargeIds.addAll(ids);
            }
        }
        ChargeItemQueryCommand bulkLoad = new ChargeItemQueryCommand();
        bulkLoad.setIds(new ArrayList<>(allChargeIds));
        List<ChargeItem> snapshotRows = chargeItemQueryService.loadChargeItemsByIds(bulkLoad);
        Map<Long, ChargeItem> chargeById = new HashMap<>();
        for (ChargeItem row : snapshotRows) {
            chargeById.put(row.getId(), row);
        }
        Map<Long, ChargeItemDefinition> definitionMap = this.buildChargeDefinitionMapByDefinitionId(snapshotRows);
        Map<String, ProductDefinitionDisplayDto> productMap = this.buildProductDefinitionMap(snapshotRows, definitionMap);
        Map<Long, String> collectorNameByEntererId = this.buildCollectorDisplayNameByEntererId(refundablePayments);
        List<OutpatientRefundPaymentGroupDto> groups = new ArrayList<>();
        for (PaymentReconciliationQueryModel payment : refundablePayments) {
            List<Long> paymentChargeIds = chargeIdsByPayment.get(payment.getId());
            if (paymentChargeIds == null || paymentChargeIds.isEmpty()) {
                continue;
            }
            List<ChargeItem> paymentRows = new ArrayList<>();
            for (Long chargeId : paymentChargeIds) {
                ChargeItem row = chargeById.get(chargeId);
                if (row != null) {
                    paymentRows.add(row);
                }
            }
            paymentRows = chargeItemQueryService.filterOutSupersededSettledLines(encounterId, paymentRows);
            List<ChargeItem> refundingLines = new ArrayList<>();
            List<ChargeItem> settledForPreserved = new ArrayList<>();
            for (ChargeItem row : paymentRows) {
                if (ChargeItemStatus.REFUNDING.getCode().equals(row.getStatusEnum())) {
                    refundingLines.add(row);
                }
                if (ChargeItemStatus.SETTLED.getCode().equals(row.getStatusEnum())) {
                    settledForPreserved.add(row);
                }
            }
            if (refundingLines.isEmpty()) {
                continue;
            }
            LinkedHashSet<Long> sourceIds = new LinkedHashSet<>();
            for (ChargeItem settled : settledForPreserved) {
                sourceIds.add(settled.getId());
            }
            Map<Long, Long> preservedMap =
                chargeItemQueryService.mapPreservedNewChargeItemIdBySourceId(encounterId, sourceIds);
            LinkedHashSet<Long> missingPreservedIds = new LinkedHashSet<>();
            for (Long newId : preservedMap.values()) {
                if (newId != null && !chargeById.containsKey(newId)) {
                    missingPreservedIds.add(newId);
                }
            }
            if (!missingPreservedIds.isEmpty()) {
                ChargeItemQueryCommand preservedBulkLoad = new ChargeItemQueryCommand();
                preservedBulkLoad.setIds(new ArrayList<>(missingPreservedIds));
                List<ChargeItem> preservedBulkRows = chargeItemQueryService.loadChargeItemsByIds(preservedBulkLoad);
                for (ChargeItem preservedRow : preservedBulkRows) {
                    chargeById.put(preservedRow.getId(), preservedRow);
                }
            }
            List<ChargeItem> preservedLines = new ArrayList<>();
            for (Long newId : preservedMap.values()) {
                ChargeItem preserved = chargeById.get(newId);
                if (preserved != null && ChargeItemStatus.PLANNED.getCode().equals(preserved.getStatusEnum())) {
                    preservedLines.add(preserved);
                }
            }
            boolean halfRefund = !preservedLines.isEmpty();
            List<OutpatientRefundLineItemDto> refundingDtos = new ArrayList<>();
            for (ChargeItem row : refundingLines) {
                String label = this.resolveLineDisplayLabel(row, definitionMap, productMap);
                refundingDtos.add(outpatientRefundMapper.toOutpatientRefundLineItemDto(row, payment.getId(), label));
            }
            List<OutpatientRefundLineItemDto> preservedDtos = new ArrayList<>();
            for (ChargeItem row : preservedLines) {
                String label = this.resolveLineDisplayLabel(row, definitionMap, productMap);
                preservedDtos.add(outpatientRefundMapper.toOutpatientRefundLineItemDto(row, payment.getId(), label));
            }
            BigDecimal refundableAmount = this.sumChargeItemAmount(refundingLines);
            String collectorName = null;
            if (payment.getEntererId() != null) {
                collectorName = collectorNameByEntererId.get(payment.getEntererId());
            }
            groups.add(outpatientRefundMapper.toOutpatientRefundPaymentGroupDto(
                payment, refundingDtos, preservedDtos, refundableAmount, halfRefund, collectorName));
        }
        this.enrichPaymentGroupsWithOriginalPayChannelCodes(groups);
        return groups;
    }

    /**
     * 构建 IF-REF-03 已退费支付分组（终态 REFUNDED 只读查询）。
     *
     * @param encounterId 就诊主键
     * @return 分组列表；无已退明细时返回空表
     */
    private List<OutpatientRefundPaymentGroupDto> buildCompletedRefundPaymentGroups(Long encounterId) {
        List<PaymentReconciliationQueryModel> payments =
            paymentReconciliationQueryService.findPaymentsByEncounter(encounterId);
        List<PaymentReconciliationQueryModel> completedPayments = new ArrayList<>();
        for (PaymentReconciliationQueryModel payment : payments) {
            if (payment.getStatusEnum() == null) {
                continue;
            }
            // 逆向退款单与源单共享 charge_item_ids 快照，只展示原支付分组（AS-OP-FIN-003 §3.4）。
            if (this.isOutpatientRefundReversePayment(payment)) {
                continue;
            }
            // 已退费查询：门诊账单退费完成后原单保持 SUCCESS（DM-FIN-002），以快照内 REFUNDED 收费行判定；
            // 兼容历史/退号等原单 status 已为 REFUNDED 或 PARTIAL_REFUNDED 的场景。
            if (PaymentStatus.SUCCESS.equals(payment.getStatusEnum())
                || PaymentStatus.REFUNDED.equals(payment.getStatusEnum())
                || PaymentStatus.PARTIAL_REFUNDED.equals(payment.getStatusEnum())) {
                completedPayments.add(payment);
            }
        }
        if (completedPayments.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> paymentIds = new ArrayList<>();
        for (PaymentReconciliationQueryModel payment : completedPayments) {
            paymentIds.add(payment.getId());
        }
        List<ChargeItemIdsCollectionQueryModel> snapshotCollections =
            paymentReconciliationQueryService.getChargeItemIdsByPayments(paymentIds);
        Map<Long, List<Long>> chargeIdsByPayment = new HashMap<>();
        for (ChargeItemIdsCollectionQueryModel collection : snapshotCollections) {
            chargeIdsByPayment.put(collection.getPaymentId(), collection.getChargeItemIds());
        }
        LinkedHashSet<Long> allChargeIds = new LinkedHashSet<>();
        for (List<Long> ids : chargeIdsByPayment.values()) {
            if (ids != null) {
                allChargeIds.addAll(ids);
            }
        }
        if (allChargeIds.isEmpty()) {
            return Collections.emptyList();
        }
        ChargeItemQueryCommand bulkLoad = new ChargeItemQueryCommand();
        bulkLoad.setIds(new ArrayList<>(allChargeIds));
        List<ChargeItem> snapshotRows = chargeItemQueryService.loadChargeItemsByIds(bulkLoad);
        Map<Long, ChargeItem> chargeById = new HashMap<>();
        for (ChargeItem row : snapshotRows) {
            chargeById.put(row.getId(), row);
        }
        Map<Long, ChargeItemDefinition> definitionMap = this.buildChargeDefinitionMapByDefinitionId(snapshotRows);
        Map<String, ProductDefinitionDisplayDto> productMap = this.buildProductDefinitionMap(snapshotRows, definitionMap);
        Map<Long, String> collectorNameByEntererId = this.buildCollectorDisplayNameByEntererId(completedPayments);
        List<OutpatientRefundPaymentGroupDto> groups = new ArrayList<>();
        for (PaymentReconciliationQueryModel payment : completedPayments) {
            List<Long> paymentChargeIds = chargeIdsByPayment.get(payment.getId());
            if (paymentChargeIds == null || paymentChargeIds.isEmpty()) {
                continue;
            }
            List<ChargeItem> paymentRows = new ArrayList<>();
            for (Long chargeId : paymentChargeIds) {
                ChargeItem row = chargeById.get(chargeId);
                if (row != null) {
                    paymentRows.add(row);
                }
            }
            paymentRows = chargeItemQueryService.filterOutSupersededSettledLines(encounterId, paymentRows);
            List<ChargeItem> refundedLines = new ArrayList<>();
            for (ChargeItem row : paymentRows) {
                if (ChargeItemStatus.REFUNDED.getCode().equals(row.getStatusEnum())) {
                    refundedLines.add(row);
                }
            }
            if (refundedLines.isEmpty()) {
                continue;
            }
            List<OutpatientRefundLineItemDto> refundedDtos = new ArrayList<>();
            for (ChargeItem row : refundedLines) {
                String label = this.resolveLineDisplayLabel(row, definitionMap, productMap);
                refundedDtos.add(outpatientRefundMapper.toOutpatientRefundLineItemDto(row, payment.getId(), label));
            }
            BigDecimal refundedAmount = this.sumChargeItemAmount(refundedLines);
            if (refundedAmount.compareTo(BigDecimal.ZERO) < 0) {
                refundedAmount = refundedAmount.abs();
            }
            String collectorName = null;
            if (payment.getEntererId() != null) {
                collectorName = collectorNameByEntererId.get(payment.getEntererId());
            }
            groups.add(outpatientRefundMapper.toOutpatientRefundPaymentGroupDto(
                payment, refundedDtos, Collections.emptyList(), refundedAmount, false, collectorName));
        }
        this.enrichPaymentGroupsWithOriginalPayChannelCodes(groups);
        return groups;
    }

    /**
     * 为支付分组补充原支付落库渠道码与分桶金额（供前端原路退判定与费用明细展示）。
     *
     * @param groups 按原支付分组列表
     */
    private void enrichPaymentGroupsWithOriginalPayChannelCodes(List<OutpatientRefundPaymentGroupDto> groups) {
        if (groups == null || groups.isEmpty()) {
            return;
        }
        List<Long> paymentIds = new ArrayList<>();
        for (OutpatientRefundPaymentGroupDto group : groups) {
            if (group.getPaymentId() != null) {
                paymentIds.add(group.getPaymentId());
            }
        }
        if (paymentIds.isEmpty()) {
            return;
        }
        Map<Long, List<String>> codesByPaymentId =
            paymentChannelRefundOrchestrator.mapDistinctPayChannelCodesByPaymentIds(paymentIds);
        for (OutpatientRefundPaymentGroupDto group : groups) {
            if (group.getPaymentId() == null) {
                continue;
            }
            group.setOriginalPayChannelCodes(
                codesByPaymentId.getOrDefault(group.getPaymentId(), Collections.emptyList()));
            List<PaymentRecDetailQueryModel> details =
                paymentReconciliationQueryService.listPaymentRecDetailsByPaymentId(group.getPaymentId());
            BigDecimal fundPay = BigDecimal.ZERO;
            BigDecimal acctPay = BigDecimal.ZERO;
            BigDecimal selfPay = BigDecimal.ZERO;
            if (details != null) {
                for (PaymentRecDetailQueryModel row : details) {
                    if (row == null || StringUtils.isEmpty(row.getPayEnum()) || row.getAmount() == null) {
                        continue;
                    }
                    String channelCode = row.getPayEnum().trim();
                    if (PaymentChannel.INSURANCE_FUND.getCode().equals(channelCode)) {
                        fundPay = MonetaryAmountSupport.sumThenSettle(List.of(fundPay, row.getAmount()));
                    } else if (PaymentChannel.INSURANCE_ACCT.getCode().equals(channelCode)) {
                        acctPay = MonetaryAmountSupport.sumThenSettle(List.of(acctPay, row.getAmount()));
                    } else {
                        PaymentChannel channel = PaymentChannel.fromCode(channelCode);
                        if (channel != null && channel.isUserSelectable() && !channel.isUiOnly()) {
                            selfPay = MonetaryAmountSupport.sumThenSettle(List.of(selfPay, row.getAmount()));
                        }
                    }
                }
            }
            group.setYbFundPaySumamt(fundPay);
            group.setYbAcctPay(acctPay);
            group.setSelfPayAmount(selfPay);
        }
    }

    /**
     * 批量解析原支付收款人展示名：enterer_id（系统用户）→ 医务人员姓名。
     *
     * @param payments 可退原支付列表
     * @return enterer 用户主键 → 展示名（无法关联时不放入键）
     */
    private Map<Long, String> buildCollectorDisplayNameByEntererId(Collection<PaymentReconciliationQueryModel> payments) {
        LinkedHashSet<Long> entererIds = new LinkedHashSet<>();
        for (PaymentReconciliationQueryModel payment : payments) {
            if (payment.getEntererId() != null) {
                entererIds.add(payment.getEntererId());
            }
        }
        if (entererIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, String> nameByEntererId = practitionerQueryService.mapDisplayNameByEntererIds(entererIds);
        return nameByEntererId;
    }

    /**
     * 解析原支付结果码：优先沿用原单；空串时回落为空由领域层校验（半退再结算须非空）。
     *
     * @param payment 原支付读模型
     * @return 持久化 outcome code
     */
    private String resolvePaymentOutcomeEnum(PaymentReconciliationQueryModel payment) {
        if (payment == null || payment.getOutcomeEnum() == null) {
            return null;
        }
        String trimmed = payment.getOutcomeEnum().trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed;
    }

    /**
     * 半退保留行预结算并正式结算（委托门诊收费）。
     *
     * @param request 退费执行请求
     * @param billDate 账单日
     * @param outcomeEnum 支付结果码
     * @param originalPaymentDetails 原支付自付明细（再结算渠道分桶对齐原路）
     */
    private void preSettleAndConfirmPreservedLines(
        ExecuteOutpatientBillRefundByPaymentRequest request,
        LocalDateTime billDate,
        String outcomeEnum,
        List<PaymentRecDetailQueryModel> originalPaymentDetails) {
        // 半退：保留行再结算委托收费域 IF-OPC-06/07，避免本服务重复支付编排。
        PreSettleOutpatientChargesRequest preRequest = new PreSettleOutpatientChargesRequest();
        preRequest.setEncounterId(request.getEncounterId());
        preRequest.setChargeItemIds(request.getPreservedNewChargeItemIds());
        PreSettleOutpatientChargesResponse preResponse = outpatientChargeAppService.preSettleOutpatientCharges(preRequest);
        ConfirmOutpatientChargeSettlementRequest confirmRequest = new ConfirmOutpatientChargeSettlementRequest();
        confirmRequest.setPaymentDraftId(preResponse.getPaymentDraftId());
        confirmRequest.setEncounterId(request.getEncounterId());
        confirmRequest.setChargeItemIds(request.getPreservedNewChargeItemIds());
        confirmRequest.setBillDate(billDate);
        confirmRequest.setOutcomeEnum(outcomeEnum);
        // 再结算实付须对齐预结算应付，并按原支付渠道比例分桶（BR-REF-007 / BR-REF-011）。
        List<PaymentDetailSubmitDto> paymentDetails =
            this.buildPreservedResettlePaymentDetails(preResponse.getDisplayAmount(), originalPaymentDetails);
        confirmRequest.setPaymentDetails(paymentDetails);
        outpatientChargeAppService.confirmOutpatientChargeSettlement(confirmRequest);
    }

    /**
     * 按原支付明细装配整单冲退渠道实退（产品半退 UN_PAY 须全额原路返回）。
     *
     * @param originalPaymentDetails 原支付自付明细
     * @return 各渠道实退额
     */
    private List<RefundPaymentDetailSubmitDto> buildFullOriginalRefundPaymentDetails(
        List<PaymentRecDetailQueryModel> originalPaymentDetails) {
        if (originalPaymentDetails == null || originalPaymentDetails.isEmpty()) {
            throw new BusinessException(
                ErrorCodeConstants.PAYMENT_RECONCILIATION_INVALID_ARGUMENT,
                this.buildMessage(
                    OutpatientRefundConstants.MESSAGE_CODE_REFUND_CHANNEL_MUST_ORIGINAL,
                    "原支付无渠道明细，无法整单冲退"));
        }
        List<RefundPaymentDetailSubmitDto> refundDetails = new ArrayList<>();
        // 逐行拷贝（含 pay_trans_no），禁止按渠道聚合丢 setl_id
        for (PaymentRecDetailQueryModel row : originalPaymentDetails) {
            if (row == null || StringUtils.isEmpty(row.getPayEnum()) || row.getAmount() == null) {
                continue;
            }
            String channelCode = paymentChannelGate.requireKnownPersistedPayChannelCode(
                row.getPayEnum(),
                ErrorCodeConstants.PAYMENT_RECONCILIATION_INVALID_ARGUMENT,
                "原支付渠道不合法");
            RefundPaymentDetailSubmitDto detail = new RefundPaymentDetailSubmitDto();
            detail.setPayChannelCode(channelCode);
            detail.setAmount(row.getAmount());
            if (StringUtils.isNotEmpty(row.getPayTransNo())) {
                detail.setPayTransNo(row.getPayTransNo().trim());
            }
            detail.setPayTransText(row.getPayTransText());
            detail.setPayTransDate(row.getPayTransDate());
            if (StringUtils.isNotEmpty(row.getResultEnum())) {
                detail.setResultEnum(row.getResultEnum().trim());
            }
            detail.setAccountId(row.getAccountId());
            detail.setAccountCode(row.getAccountCode());
            refundDetails.add(detail);
        }
        if (refundDetails.isEmpty()) {
            throw new BusinessException(
                ErrorCodeConstants.PAYMENT_RECONCILIATION_INVALID_ARGUMENT,
                this.buildMessage(
                    OutpatientRefundConstants.MESSAGE_CODE_REFUND_CHANNEL_MUST_ORIGINAL,
                    "原支付无渠道明细，无法整单冲退"));
        }
        return refundDetails;
    }

    /**
     * 逆向明细 result_enum 收口为成功/已退语义；空或 DRAFT 时回落本笔 outcome。
     *
     * @param details     待落库逆向明细命令
     * @param outcomeEnum 本笔退费结果码
     */
    private void fillRefundDetailResultEnum(List<PaymentDetailItemCommand> details, String outcomeEnum) {
        if (details == null || details.isEmpty()) {
            return;
        }
        for (PaymentDetailItemCommand detail : details) {
            if (detail == null) {
                continue;
            }
            String resultEnum = detail.getResultEnum();
            if (StringUtils.isEmpty(resultEnum)
                || "DRAFT".equalsIgnoreCase(resultEnum.trim())) {
                detail.setResultEnum(outcomeEnum);
            }
        }
    }

    /**
     * 半退保留行再结算：按原支付渠道比例将预结算应付拆分为正式结算渠道实付。
     *
     * @param payableAmount 预结算现金应付
     * @param originalPaymentDetails 原支付自付明细
     * @return 正式结算渠道实付
     */
    private List<PaymentDetailSubmitDto> buildPreservedResettlePaymentDetails(
        BigDecimal payableAmount,
        List<PaymentRecDetailQueryModel> originalPaymentDetails) {
        Map<String, BigDecimal> originalByChannel = this.aggregatePaymentAmountByChannel(originalPaymentDetails);
        if (originalByChannel.isEmpty()) {
            throw new BusinessException(
                ErrorCodeConstants.PAYMENT_RECONCILIATION_INVALID_ARGUMENT,
                this.buildMessage(
                    OutpatientRefundConstants.MESSAGE_CODE_REFUND_CHANNEL_MUST_ORIGINAL,
                    "原支付无渠道明细，无法半退再结算"));
        }
        LinkedHashSet<String> channelOrder = new LinkedHashSet<>();
        if (originalPaymentDetails != null) {
            for (PaymentRecDetailQueryModel row : originalPaymentDetails) {
                if (row == null || StringUtils.isEmpty(row.getPayEnum()) || row.getAmount() == null) {
                    continue;
                }
                String channelCode = paymentChannelGate.requireKnownPersistedPayChannelCode(
                    row.getPayEnum(),
                    ErrorCodeConstants.PAYMENT_RECONCILIATION_INVALID_ARGUMENT,
                    "原支付渠道不合法");
                channelOrder.add(channelCode);
            }
        }
        List<String> channels = new ArrayList<>(channelOrder);
        List<BigDecimal> originalAmounts = new ArrayList<>();
        for (String channel : channels) {
            originalAmounts.add(originalByChannel.get(channel));
        }
        BigDecimal originalTotal = MonetaryAmountSupport.sumThenSettle(originalAmounts);
        BigDecimal payable = MonetaryAmountSupport.scaleSettle(payableAmount);
        List<PaymentDetailSubmitDto> paymentDetails = new ArrayList<>();
        BigDecimal allocatedSum = BigDecimal.ZERO;
        for (int index = 0; index < channels.size(); index++) {
            String channel = channels.get(index);
            BigDecimal scaledAmount;
            if (index == channels.size() - 1) {
                scaledAmount = MonetaryAmountSupport.scaleSettle(payable.subtract(allocatedSum));
            } else {
                BigDecimal proportion = MonetaryAmountSupport.proportionAtCalcScale(
                    originalByChannel.get(channel),
                    payable,
                    originalTotal);
                scaledAmount = MonetaryAmountSupport.scaleSettle(proportion);
                allocatedSum = allocatedSum.add(scaledAmount);
            }
            PaymentDetailSubmitDto detail = new PaymentDetailSubmitDto();
            detail.setPayChannelCode(channel);
            detail.setAmount(scaledAmount);
            paymentDetails.add(detail);
        }
        return paymentDetails;
    }

    /**
     * IF-REF-04 门禁：退药/退耗未完成则返回拒绝码。
     *
     * @param refundingRows 拟退收费行
     * @return 拒绝码；通过时 null
     */
    private String runRefundableGate(List<ChargeItem> refundingRows) {
        // 从拟退行收集关联的退药/退耗服务申请锚点，批量查询避免逐条 I/O。
        LinkedHashSet<Long> medicineIds = new LinkedHashSet<>();
        LinkedHashSet<Long> deviceIds = new LinkedHashSet<>();
        for (ChargeItem row : refundingRows) {
            if (TableNameConstants.MED_MEDICATION_REQUEST.equals(row.getServiceTable()) && row.getServiceId() != null) {
                medicineIds.add(row.getServiceId());
            }
            if (TableNameConstants.WKF_DEVICE_REQUEST.equals(row.getServiceTable()) && row.getServiceId() != null) {
                deviceIds.add(row.getServiceId());
            }
        }
        if (!medicineIds.isEmpty()) {
            List<MedicationRequest> refundMedicineRequests = medicationRequestQueryService.listByRefundMedicineIdIn(medicineIds);
            for (MedicationRequest refundRequest : refundMedicineRequests) {
                // 退药负向单未达 COMPLETED 则阻塞资金退费（CANCELLED=待退，对齐 openhis）。
                if (this.isPendingRefundNegativeRequest(refundRequest.getStatusEnum())) {
                    return OutpatientRefundConstants.MESSAGE_CODE_MEDICATION_RETURN_PENDING;
                }
            }
        }
        if (!deviceIds.isEmpty()) {
            List<DeviceRequest> refundDeviceRequests = deviceRequestQueryService.listByRefundDeviceIdIn(deviceIds);
            for (DeviceRequest refundRequest : refundDeviceRequests) {
                // 退耗负向单未达 COMPLETED 则阻塞资金退费。
                if (this.isPendingRefundNegativeRequest(refundRequest.getStatusEnum())) {
                    return OutpatientRefundConstants.MESSAGE_CODE_DEVICE_RETURN_PENDING;
                }
            }
        }
        return null;
    }

    /**
     * 判断负向退药/退耗申请是否仍处于待完结态（仅 COMPLETED 放行资金退费）。
     *
     * @param statusCode 负向申请持久化状态 code
     * @return true=仍阻塞资金退费
     */
    private boolean isPendingRefundNegativeRequest(String statusCode) {
        if (statusCode == null) {
            return true;
        }
        if (RequestStatus.COMPLETED.getCode().equals(statusCode)) {
            return false;
        }
        return true;
    }

    /**
     * 列举半退保留再生 PLANNED 行（refund_id 非空）。
     *
     * @param encounterId 就诊主键
     * @return 保留行列表
     */
    private List<ChargeItem> listPreservedPlannedChargeItems(Long encounterId) {
        ChargeItemQueryCommand queryCommand = new ChargeItemQueryCommand();
        queryCommand.setEncounterId(encounterId);
        List<ChargeItem> allRows = chargeItemQueryService.listChargeItemsByEncounterId(queryCommand);
        List<ChargeItem> preserved = new ArrayList<>();
        for (ChargeItem row : allRows) {
            if (!ChargeItemStatus.PLANNED.getCode().equals(row.getStatusEnum())) {
                continue;
            }
            if (row.getRefundId() == null) {
                continue;
            }
            preserved.add(row);
        }
        return preserved;
    }

    /**
     * 解析支付快照挂载的收费明细主键。
     *
     * @param paymentId 支付主键
     * @return 主键列表
     */
    private List<Long> resolvePaymentSnapshotChargeItemIds(Long paymentId) {
        List<ChargeItemIdsCollectionQueryModel> collections =
            paymentReconciliationQueryService.getChargeItemIdsByPayments(List.of(paymentId));
        if (collections.isEmpty() || collections.get(0).getChargeItemIds() == null) {
            return Collections.emptyList();
        }
        return collections.get(0).getChargeItemIds();
    }

    /**
     * IF-REF-05 Gate：同一原支付须一次执行退清，禁止分次退费（半退=一次收退两次，BR-REF-021）。
     *
     * @param encounterId            就诊主键
     * @param paymentId              原支付主键
     * @param refundingChargeItemIds 本次拟退收费明细主键
     * @throws BusinessException 未覆盖该支付下全部 {@code REFUNDING} 行时抛出 EX-REF-305
     */
    private void assertNoSplitRefundOnPaymentExecute(
        Long encounterId,
        Long paymentId,
        List<Long> refundingChargeItemIds) {
        List<Long> snapshotIds = this.resolvePaymentSnapshotChargeItemIds(paymentId);
        if (snapshotIds.isEmpty()) {
            return;
        }
        ChargeItemQueryCommand loadCmd = new ChargeItemQueryCommand();
        loadCmd.setIds(snapshotIds);
        List<ChargeItem> snapshotRows = chargeItemQueryService.loadChargeItemsByIds(loadCmd);
        snapshotRows = chargeItemQueryService.filterOutSupersededSettledLines(encounterId, snapshotRows);
        LinkedHashSet<Long> refundingOnPayment = new LinkedHashSet<>();
        for (ChargeItem row : snapshotRows) {
            if (ChargeItemStatus.REFUNDING.getCode().equals(row.getStatusEnum())) {
                refundingOnPayment.add(row.getId());
            }
        }
        if (refundingOnPayment.isEmpty()) {
            return;
        }
        LinkedHashSet<Long> submittedRefundingIds = new LinkedHashSet<>();
        if (refundingChargeItemIds != null) {
            for (Long id : refundingChargeItemIds) {
                if (id != null) {
                    submittedRefundingIds.add(id);
                }
            }
        }
        // Gate：该支付下所有 REFUNDING 行须在同一次 IF-REF-05 中执行，不得分两次退。
        if (!submittedRefundingIds.equals(refundingOnPayment)) {
            throw new BusinessException(
                ErrorCodeConstants.CHARGE_ITEM_RULE_CONFLICT,
                this.buildMessage(
                    OutpatientRefundConstants.MESSAGE_CODE_HALF_REFUND_NOT_SUPPORTED,
                    "同一笔支付须一次执行退清，不支持分次退费"));
        }
    }

    /**
     * IF-REF-05 Gate：退费渠道拆分须与原支付明细一致（原路返回，BR-REF-011）。
     *
     * @param refundDetails    请求体渠道实退明细
     * @param originalDetails  原支付明细读模型
     * @throws BusinessException 渠道集合或分桶金额不一致时抛出 EX-REF-306
     */
    private void assertRefundPaymentDetailsMatchOriginalPayment(
        List<RefundPaymentDetailSubmitDto> refundDetails,
        List<PaymentRecDetailQueryModel> originalDetails) {
        Map<String, BigDecimal> originalByChannel = this.aggregatePaymentAmountByChannel(originalDetails);
        Map<String, BigDecimal> refundByChannel = this.aggregateRefundAmountByChannel(refundDetails);
        if (originalByChannel.isEmpty()) {
            throw new BusinessException(
                ErrorCodeConstants.PAYMENT_RECONCILIATION_INVALID_ARGUMENT,
                this.buildMessage(
                    OutpatientRefundConstants.MESSAGE_CODE_REFUND_CHANNEL_MUST_ORIGINAL,
                    "原支付无渠道明细，无法原路退"));
        }
        if (!originalByChannel.keySet().equals(refundByChannel.keySet())) {
            throw new BusinessException(
                ErrorCodeConstants.PAYMENT_RECONCILIATION_RULE_CONFLICT,
                this.buildMessage(
                    OutpatientRefundConstants.MESSAGE_CODE_REFUND_CHANNEL_MUST_ORIGINAL,
                    "退费渠道须与原支付一致"));
        }
        for (Map.Entry<String, BigDecimal> entry : originalByChannel.entrySet()) {
            BigDecimal refundAmount = refundByChannel.get(entry.getKey());
            if (!MonetaryAmountSupport.equalsAtSettleScale(entry.getValue(), refundAmount)) {
                throw new BusinessException(
                    ErrorCodeConstants.PAYMENT_RECONCILIATION_RULE_CONFLICT,
                    this.buildMessage(
                        OutpatientRefundConstants.MESSAGE_CODE_REFUND_CHANNEL_MUST_ORIGINAL,
                        "各渠道实退额须与原支付分桶一致"));
            }
        }
    }

    /**
     * IF-REF-05 Gate：原支付渠道明细合计须等于挂载收费项合计（BR-REF-024）。
     *
     * <p>历史脏例：多笔 2207 后现金只落了首笔 psn_cash，明细合计&lt;收费项；原路退会把缺口复制到逆向单。</p>
     *
     * @param originalPayment         原支付读模型
     * @param originalPaymentDetails  原支付明细
     * @throws BusinessException 勾稽失败 → EX-REF-307
     */
    private void assertOriginalPaymentDetailsBalancedWithCharges(
        PaymentReconciliationQueryModel originalPayment,
        List<PaymentRecDetailQueryModel> originalPaymentDetails) {
        if (originalPayment == null) {
            return;
        }
        List<Long> chargeItemIds = this.parseChargeItemIds(originalPayment.getChargeItemIds());
        // 无挂载收费项（预交金等）不走本勾稽
        if (chargeItemIds.isEmpty()) {
            return;
        }
        ChargeItemQueryCommand loadCmd = new ChargeItemQueryCommand();
        loadCmd.setIds(chargeItemIds);
        List<ChargeItem> chargeItems = chargeItemQueryService.loadChargeItemsByIds(loadCmd);
        BigDecimal chargeTotal = BigDecimal.ZERO;
        if (chargeItems != null) {
            for (ChargeItem item : chargeItems) {
                if (item == null || item.getTotalPrice() == null) {
                    continue;
                }
                chargeTotal = chargeTotal.add(item.getTotalPrice());
            }
        }
        chargeTotal = MonetaryAmountSupport.scaleSettle(chargeTotal);
        BigDecimal detailTotal = BigDecimal.ZERO;
        if (originalPaymentDetails != null) {
            for (PaymentRecDetailQueryModel detail : originalPaymentDetails) {
                if (detail == null || detail.getAmount() == null) {
                    continue;
                }
                BigDecimal line = detail.getAmount();
                if (detail.getReturnAmount() != null) {
                    line = line.subtract(detail.getReturnAmount());
                }
                detailTotal = detailTotal.add(line);
            }
        }
        detailTotal = MonetaryAmountSupport.scaleSettle(detailTotal);
        // 明细合计须等于收费项：与收费落账 BR-OPC-024 同精神，挡住脏原单被原路复制
        if (!MonetaryAmountSupport.equalsAtSettleScale(detailTotal, chargeTotal)) {
            throw new BusinessException(
                ErrorCodeConstants.PAYMENT_RECONCILIATION_RULE_CONFLICT,
                this.buildMessage(
                    OutpatientRefundConstants.MESSAGE_CODE_REFUND_ORIGINAL_DETAIL_UNBALANCED,
                    "原支付渠道明细合计与收费项不一致，请先修正原单后再退费"));
        }
    }

    /**
     * 解析支付头挂载的收费明细 id 串。
     *
     * @param rawChargeItemIds 逗号分隔 id
     * @return 去重后的主键列表；空串返回空列表
     */
    private List<Long> parseChargeItemIds(String rawChargeItemIds) {
        List<Long> ids = new ArrayList<>();
        if (StringUtils.isEmpty(rawChargeItemIds)) {
            return ids;
        }
        String[] parts = rawChargeItemIds.split(",");
        LinkedHashSet<Long> unique = new LinkedHashSet<>();
        for (String part : parts) {
            if (StringUtils.isEmpty(part)) {
                continue;
            }
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                unique.add(Long.valueOf(trimmed));
            } catch (NumberFormatException ignored) {
                // 脏串跳过，后续空列表会跳过勾稽或由其它 Gate 拦截
            }
        }
        ids.addAll(unique);
        return ids;
    }

    /**
     * 按渠道码汇总原支付明细发生额（结算刻度）。
     *
     * @param originalDetails 原支付明细
     * @return 渠道码 → 金额
     */
    private Map<String, BigDecimal> aggregatePaymentAmountByChannel(List<PaymentRecDetailQueryModel> originalDetails) {
        Map<String, BigDecimal> buckets = new HashMap<>();
        if (originalDetails == null) {
            return buckets;
        }
        for (PaymentRecDetailQueryModel row : originalDetails) {
            if (row == null || StringUtils.isEmpty(row.getPayEnum()) || row.getAmount() == null) {
                continue;
            }
            String channelCode = paymentChannelGate.requireKnownPersistedPayChannelCode(
                row.getPayEnum(),
                ErrorCodeConstants.PAYMENT_RECONCILIATION_INVALID_ARGUMENT,
                "原支付渠道不合法");
            BigDecimal existing = buckets.get(channelCode);
            if (existing == null) {
                buckets.put(channelCode, row.getAmount());
            } else {
                List<BigDecimal> pair = new ArrayList<>(2);
                pair.add(existing);
                pair.add(row.getAmount());
                buckets.put(channelCode, MonetaryAmountSupport.sumThenSettle(pair));
            }
        }
        return buckets;
    }

    /**
     * 按渠道码汇总退费请求体实退额（结算刻度）。
     *
     * @param refundDetails 退费渠道明细
     * @return 渠道码 → 金额
     */
    private Map<String, BigDecimal> aggregateRefundAmountByChannel(List<RefundPaymentDetailSubmitDto> refundDetails) {
        Map<String, BigDecimal> buckets = new HashMap<>();
        if (refundDetails == null) {
            return buckets;
        }
        for (RefundPaymentDetailSubmitDto row : refundDetails) {
            if (row == null || StringUtils.isEmpty(row.getPayChannelCode()) || row.getAmount() == null) {
                continue;
            }
            String channelCode = paymentChannelGate.requireKnownPersistedPayChannelCode(
                row.getPayChannelCode(),
                ErrorCodeConstants.PAYMENT_RECONCILIATION_INVALID_ARGUMENT,
                "退款支付渠道不合法");
            BigDecimal existing = buckets.get(channelCode);
            if (existing == null) {
                buckets.put(channelCode, row.getAmount());
            } else {
                List<BigDecimal> pair = new ArrayList<>(2);
                pair.add(existing);
                pair.add(row.getAmount());
                buckets.put(channelCode, MonetaryAmountSupport.sumThenSettle(pair));
            }
        }
        return buckets;
    }

    /**
     * 校验原支付可受理退费。
     *
     * @param payment 原支付读模型
     */
    private void assertOriginalPaymentRefundable(PaymentReconciliationQueryModel payment) {
        if (payment.getStatusEnum() == null) {
            throw new BusinessException(
                ErrorCodeConstants.PAYMENT_RECONCILIATION_RULE_CONFLICT,
                this.buildMessage(OutpatientRefundConstants.MESSAGE_CODE_PAYMENT_NOT_REFUNDABLE, "支付状态已变化"));
        }
        if (PaymentStatus.REFUNDED.equals(payment.getStatusEnum())) {
            throw new BusinessException(
                ErrorCodeConstants.PAYMENT_RECONCILIATION_RULE_CONFLICT,
                this.buildMessage(OutpatientRefundConstants.MESSAGE_CODE_PAYMENT_NOT_REFUNDABLE, "支付已退款"));
        }
        // Gate：已部分退费的原支付禁止再次受理（分次退 / PARTIAL_REFUNDED 语义，BR-REF-021）。
        if (PaymentStatus.PARTIAL_REFUNDED.equals(payment.getStatusEnum())) {
            throw new BusinessException(
                ErrorCodeConstants.PAYMENT_RECONCILIATION_RULE_CONFLICT,
                this.buildMessage(
                    OutpatientRefundConstants.MESSAGE_CODE_HALF_REFUND_NOT_SUPPORTED,
                    "该笔支付已部分退费，不支持再次退费"));
        }
        if (!PaymentStatus.SUCCESS.equals(payment.getStatusEnum())) {
            throw new BusinessException(
                ErrorCodeConstants.PAYMENT_RECONCILIATION_RULE_CONFLICT,
                this.buildMessage(OutpatientRefundConstants.MESSAGE_CODE_PAYMENT_NOT_REFUNDABLE, "支付状态不可退"));
        }
    }

    /**
     * 校验拟退行均为 REFUNDING 且归属就诊。
     *
     * @param rows 已加载行
     * @param requestedIds 请求 id
     * @param encounterId 就诊主键
     */
    private void assertRefundingChargeItems(List<ChargeItem> rows, List<Long> requestedIds, Long encounterId) {
        this.assertChargeItemsLoaded(requestedIds, rows, OutpatientRefundConstants.MESSAGE_CODE_CHARGE_ITEM_STATUS_CHANGED);
        this.assertChargeItemsBelongEncounter(rows, encounterId, OutpatientRefundConstants.MESSAGE_CODE_CHARGE_ITEM_STATUS_CHANGED);
        for (ChargeItem row : rows) {
            if (!ChargeItemStatus.REFUNDING.getCode().equals(row.getStatusEnum())) {
                throw new BusinessException(
                    ErrorCodeConstants.CHARGE_ITEM_INVALID_STATUS,
                    this.buildMessage(OutpatientRefundConstants.MESSAGE_CODE_CHARGE_ITEM_STATUS_CHANGED, "收费明细状态已变化"));
            }
        }
    }

    /**
     * 过滤退费页初始化下拉：仅 REFUNDING / REFUNDED。
     *
     * @param allOptions 全量枚举选项
     * @return 子集
     */
    private List<OptionDto> filterRefundPageInitOptions(List<OptionDto> allOptions) {
        List<OptionDto> filtered = new ArrayList<>();
        if (allOptions == null) {
            return filtered;
        }
        for (OptionDto option : allOptions) {
            if (option == null || option.getValue() == null) {
                continue;
            }
            if (ChargeItemStatus.REFUNDING.getCode().equals(option.getValue())
                || ChargeItemStatus.REFUNDED.getCode().equals(option.getValue())) {
                filtered.add(option);
            }
        }
        return filtered;
    }

    /**
     * 为队列行解析退费页签标签（BR-REF-002：拟退明细 vs 已退终态）。
     *
     * @param records 门诊信息行
     * @return encounterId → 标签
     */
    private Map<Long, String> resolveQueueFlags(List<OutpatientEncounterContextQueryModel> records) {
        Map<Long, String> flags = new HashMap<>();
        if (records == null || records.isEmpty()) {
            return flags;
        }
        List<Long> encounterIds = new ArrayList<>();
        for (OutpatientEncounterContextQueryModel record : records) {
            if (record != null && record.getEncounterId() != null) {
                encounterIds.add(record.getEncounterId());
            }
        }
        List<ChargeItem> refundRelatedRows =
            chargeItemQueryService.listRefundRelatedChargeItemsByEncounterIds(encounterIds);
        Map<Long, Boolean> hasRefundingByEncounterId = new HashMap<>();
        Map<Long, Boolean> hasRefundedByEncounterId = new HashMap<>();
        for (ChargeItem row : refundRelatedRows) {
            if (row == null || row.getEncounterId() == null) {
                continue;
            }
            Long encounterId = row.getEncounterId();
            if (ChargeItemStatus.REFUNDING.getCode().equals(row.getStatusEnum())) {
                hasRefundingByEncounterId.put(encounterId, Boolean.TRUE);
            }
            if (ChargeItemStatus.REFUNDED.getCode().equals(row.getStatusEnum())) {
                hasRefundedByEncounterId.put(encounterId, Boolean.TRUE);
            }
        }
        for (OutpatientEncounterContextQueryModel record : records) {
            if (record == null || record.getEncounterId() == null) {
                continue;
            }
            Long encounterId = record.getEncounterId();
            // 仍存在拟退明细 → 待退费（可与已退并存，已退页签另按 REFUNDED 事实展示）。
            // 无拟退且存在已退明细 → 已退费。
            if (Boolean.TRUE.equals(hasRefundingByEncounterId.get(encounterId))) {
                flags.put(encounterId, OutpatientRefundQueueTab.PENDING);
                continue;
            }
            if (Boolean.TRUE.equals(hasRefundedByEncounterId.get(encounterId))) {
                flags.put(encounterId, OutpatientRefundQueueTab.SETTLED);
                continue;
            }
            flags.put(encounterId, OutpatientRefundQueueTab.PENDING);
        }
        return flags;
    }

    /**
     * 批量加载定价定义映射。
     *
     * @param chargeItems 收费行
     * @return 定义 id → 定义
     */
    private Map<Long, ChargeItemDefinition> buildChargeDefinitionMapByDefinitionId(List<ChargeItem> chargeItems) {
        LinkedHashSet<Long> definitionIds = new LinkedHashSet<>();
        for (ChargeItem chargeItem : chargeItems) {
            if (chargeItem.getDefinitionId() != null) {
                definitionIds.add(chargeItem.getDefinitionId());
            }
        }
        if (definitionIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return chargeItemDefinitionQueryService.findAllDefinitionsByIdIn(definitionIds);
    }

    /**
     * 按收费明细批量加载产品定义读模型（与 IF-OPC-03 回落口径一致）。
     *
     * @param chargeItems 收费行
     * @param definitionMap 定价映射
     * @return 复合键 → 读模型
     */
    private Map<String, ProductDefinitionDisplayDto> buildProductDefinitionMap(
        List<ChargeItem> chargeItems,
        Map<Long, ChargeItemDefinition> definitionMap) {
        List<ProductDefinitionBatchLookup> lookups = new ArrayList<>();
        for (ChargeItem row : chargeItems) {
            ChargeItemDefinition definitionRow = null;
            if (definitionMap != null && row.getDefinitionId() != null) {
                definitionRow = definitionMap.get(row.getDefinitionId());
            }
            ProductDefinitionBatchLookup lookup = this.resolveCatalogLookupForChargeItem(row, definitionRow);
            if (lookup != null) {
                lookups.add(lookup);
            }
        }
        if (lookups.isEmpty()) {
            return Collections.emptyMap();
        }
        return productDefinitionDisplayTerminologyAssembler.loadAsMap(lookups);
    }

    /**
     * 解析单条收费明细对应的目录批量查询键。
     *
     * @param row 收费明细
     * @param definition 定价定义
     * @return 查询键或 null
     */
    private ProductDefinitionBatchLookup resolveCatalogLookupForChargeItem(ChargeItem row, ChargeItemDefinition definition) {
        if (row.getProductId() != null && StringUtils.isNotEmpty(row.getProductTable())) {
            ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
            lookup.setProductDefinitionId(row.getProductId());
            lookup.setProductTableName(row.getProductTable().trim());
            return lookup;
        }
        if (definition != null && definition.getInstanceId() != null && StringUtils.isNotEmpty(definition.getInstanceTable())) {
            ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
            lookup.setProductDefinitionId(definition.getInstanceId());
            lookup.setProductTableName(definition.getInstanceTable().trim());
            return lookup;
        }
        return null;
    }

    /**
     * 从批量目录映射解析产品读模型行。
     *
     * @param row 收费明细
     * @param productMap 目录映射
     * @param definition 定价定义
     * @return 产品读模型或 null
     */
    private ProductDefinitionDisplayDto resolveProductForChargeItem(
        ChargeItem row,
        Map<String, ProductDefinitionDisplayDto> productMap,
        ChargeItemDefinition definition) {
        if (productMap == null || productMap.isEmpty()) {
            return null;
        }
        ProductDefinitionBatchLookup anchor = this.resolveCatalogLookupForChargeItem(row, definition);
        if (anchor == null) {
            return null;
        }
        String key = ProductDefinitionKeyUtil.buildCompositeKey(
            anchor.getProductTableName(),
            anchor.getProductDefinitionId());
        if (key == null) {
            return null;
        }
        return productMap.get(key);
    }

    /**
     * 解析单行展示名称（与 IF-OPC-03 回落口径一致）。
     *
     * @param row 收费行
     * @param definitionMap 定价映射
     * @param productMap 产品映射
     * @return 展示名
     */
    private String resolveLineDisplayLabel(
        ChargeItem row,
        Map<Long, ChargeItemDefinition> definitionMap,
        Map<String, ProductDefinitionDisplayDto> productMap) {
        ChargeItemDefinition definition = null;
        if (definitionMap != null && row.getDefinitionId() != null) {
            definition = definitionMap.get(row.getDefinitionId());
        }
        ProductDefinitionDisplayDto product = this.resolveProductForChargeItem(row, productMap, definition);
        if (product != null && StringUtils.isNotEmpty(product.getProductName())) {
            return product.getProductName();
        }
        if (definition != null && StringUtils.isNotEmpty(definition.getChargeName())) {
            return definition.getChargeName();
        }
        if (StringUtils.isNotEmpty(row.getBusNo())) {
            return row.getBusNo();
        }
        return OutpatientChargeConstants.PENDING_CHARGE_ITEM_DISPLAY_FALLBACK;
    }

    /**
     * 汇总拟退/已退收费行金额（先 6 位加总再 settle，供应退展示与支付校验）。
     *
     * @param rows 收费行
     * @return 结算刻度合计
     */
    private BigDecimal sumChargeItemAmount(List<ChargeItem> rows) {
        List<BigDecimal> amounts = new ArrayList<>();
        if (rows != null) {
            for (ChargeItem row : rows) {
                if (row != null && row.getTotalPrice() != null) {
                    amounts.add(row.getTotalPrice());
                }
            }
        }
        return MonetaryAmountSupport.sumThenSettle(amounts);
    }

    /**
     * 将原支付明细上的账户写回逆向退款明细，满足 {@code fin_payment_rec_detail.account_id} 与领域自检。
     *
     * <p>IF-REF-05 请求体仅带渠道与实退金额；须在应用层沿用原支付单已落库的账户分桶，
     * 与 IF-OPC-07 {@link OutpatientChargeAppService} 对草稿明细的回填语义一致。</p>
     *
     * @param refundLines 逆向单支付明细命令
     * @param originalDetails 原支付单明细读模型
     * @param encounterId 就诊主键（账户回落）
     * @param refundingRows 拟退收费行（单账户回落）
     */
    private void attachOriginalPaymentAccountsToRefundPaymentDetails(
        List<PaymentDetailItemCommand> refundLines,
        List<PaymentRecDetailQueryModel> originalDetails,
        Long encounterId,
        List<ChargeItem> refundingRows) {
        if (refundLines == null || refundLines.isEmpty()) {
            return;
        }
        if (!this.hasRefundLineMissingAccountId(refundLines)) {
            return;
        }
        if (originalDetails != null && !originalDetails.isEmpty()) {
            // 原支付仅一条明细：全渠道逆向行共用同一结算账户（与 IF-OPC-07 单账户草稿一致）。
            if (originalDetails.size() == 1) {
                PaymentRecDetailQueryModel originalRow = originalDetails.get(0);
                if (originalRow.getAccountId() != null) {
                    for (PaymentDetailItemCommand line : refundLines) {
                        if (line == null) {
                            continue;
                        }
                        if (line.getAccountId() == null) {
                            line.setAccountId(originalRow.getAccountId());
                            line.setAccountCode(originalRow.getAccountCode());
                        }
                    }
                    return;
                }
            }
            // 多账户原支付：按渠道码对齐原单分桶，保证医保/自费逆向明细记账口径一致。
            Map<String, PaymentRecDetailQueryModel> originalByPayEnum = new HashMap<>();
            for (PaymentRecDetailQueryModel originalRow : originalDetails) {
                if (originalRow != null && StringUtils.isNotEmpty(originalRow.getPayEnum())) {
                    originalByPayEnum.put(originalRow.getPayEnum(), originalRow);
                }
            }
            for (PaymentDetailItemCommand line : refundLines) {
                if (line == null || line.getAccountId() != null) {
                    continue;
                }
                PaymentRecDetailQueryModel matched = originalByPayEnum.get(line.getPayEnum());
                if (matched == null || matched.getAccountId() == null) {
                    continue;
                }
                line.setAccountId(matched.getAccountId());
                line.setAccountCode(matched.getAccountCode());
            }
            if (!this.hasRefundLineMissingAccountId(refundLines)) {
                return;
            }
        }
        // 原单无可用账户快照时，从拟退收费行推断唯一账户；仍无法确定则拒绝生成逆向单。
        Long fallbackAccountId = this.resolveSingleRefundAccountId(encounterId, refundingRows);
        if (fallbackAccountId == null) {
            throw new BusinessException(
                ErrorCodeConstants.PAYMENT_RECONCILIATION_INVALID_ARGUMENT,
                this.buildMessage(
                    OutpatientRefundConstants.MESSAGE_CODE_ACCOUNT_NOT_FOUND,
                    "无法从原支付明细或拟退收费项解析结算账户"));
        }
        String fallbackAccountCode = this.resolveAccountBusNo(encounterId, fallbackAccountId);
        for (PaymentDetailItemCommand line : refundLines) {
            if (line == null || line.getAccountId() != null) {
                continue;
            }
            line.setAccountId(fallbackAccountId);
            line.setAccountCode(fallbackAccountCode);
        }
    }

    /**
     * 判断逆向支付明细是否仍存在未回填的结算账户。
     *
     * @param refundLines 逆向单支付明细命令
     * @return 任一行 accountId 为空则 true
     */
    private boolean hasRefundLineMissingAccountId(List<PaymentDetailItemCommand> refundLines) {
        if (refundLines == null) {
            return false;
        }
        for (PaymentDetailItemCommand line : refundLines) {
            if (line != null && line.getAccountId() == null) {
                return true;
            }
        }
        return false;
    }

    /**
     * 按就诊账户列表解析账户业务号，用于逆向明细 accountCode 展示与落库。
     *
     * @param encounterId 就诊主键
     * @param accountId 结算账户主键
     * @return 账户 busNo；未命中则 null
     */
    private String resolveAccountBusNo(Long encounterId, Long accountId) {
        if (accountId == null) {
            return null;
        }
        List<Account> encounterAccounts = accountQueryService.getAccountListByEncounter(encounterId);
        for (Account accountRow : encounterAccounts) {
            if (accountId.equals(accountRow.getId())) {
                return accountRow.getBusNo();
            }
        }
        return null;
    }

    /**
     * 从拟退收费行解析唯一结算账户；多账户并存时返回 null，由调用方走原支付明细分桶匹配。
     *
     * @param encounterId 就诊主键
     * @param refundingRows 拟退收费行
     * @return 唯一账户主键；无法唯一确定时 null
     */
    private Long resolveSingleRefundAccountId(Long encounterId, List<ChargeItem> refundingRows) {
        Long defaultSelfPayAccountId = accountQueryService.getSelfPayAccount(encounterId);
        LinkedHashSet<Long> accountIds = new LinkedHashSet<>();
        if (refundingRows != null) {
            for (ChargeItem row : refundingRows) {
                Long accountId = row.getAccountId();
                // 拟退行未锚定账户时回落默认自费账户，与门诊收费预结算口径一致。
                if (accountId == null) {
                    accountId = defaultSelfPayAccountId;
                }
                if (accountId != null) {
                    accountIds.add(accountId);
                }
            }
        }
        if (accountIds.size() == 1) {
            return accountIds.iterator().next();
        }
        // 多账户并存时不强行猜测，交由原支付明细分渠道匹配（含医保户+代煎自费合同户）。
        return null;
    }

    /**
     * 汇总渠道实退金额（结算刻度 2 位，**BR-MON-001**）。
     *
     * @param details 渠道明细
     * @return 结算刻度合计
     */
    private BigDecimal sumRefundPaymentDetails(List<RefundPaymentDetailSubmitDto> details) {
        List<BigDecimal> amounts = new ArrayList<>();
        if (details != null) {
            for (RefundPaymentDetailSubmitDto detail : details) {
                if (detail != null && detail.getAmount() != null) {
                    amounts.add(detail.getAmount());
                }
            }
        }
        return MonetaryAmountSupport.sumThenSettle(amounts);
    }

    /**
     * 解析账单日。
     *
     * @param billDateText 请求文本
     * @return 账单时刻
     */
    private LocalDateTime resolveBillDate(String billDateText) {
        if (billDateText == null || billDateText.isBlank()) {
            return LocalDateTime.now();
        }
        String trimmed = billDateText.trim();
        try {
            return LocalDateTime.parse(trimmed, GeneralSharedConstants.DATE_TIME_FORMATTER);
        } catch (DateTimeParseException ex) {
            try {
                return LocalDateTime.parse(trimmed, GeneralSharedConstants.ISO_LOCAL_DATE_TIME_FORMATTER);
            } catch (DateTimeParseException ignored) {
                return LocalDateTime.now();
            }
        }
    }

    /**
     * 拼接收费明细 id。
     *
     * @param ids 主键列表
     * @return 逗号串
     */
    private String joinIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return GeneralSharedConstants.EMPTY_STRING;
        }
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < ids.size(); index++) {
            if (index > 0) {
                builder.append(",");
            }
            builder.append(ids.get(index));
        }
        return builder.toString();
    }

    /**
     * 队列页签过滤后的分页响应（与 {@link OutpatientChargeAppService#toQueuePageResponse} 同口径）。
     *
     * @param page    就诊分页壳
     * @param records 当前页过滤后的行
     * @return 分页响应
     */
    private PageOutpatientRefundQueueResponse toQueuePageResponse(
        Page<OutpatientEncounterContextQueryModel> page,
        List<OutpatientRefundQueueRowDto> records) {
        return PagedResultBuilder.buildList(new PageOutpatientRefundQueueResponse(), records);
    }

    /**
     * 校验队列请求。
     *
     * @param request 分页请求
     */
    private void validateQueueRequest(PageOutpatientRefundQueueRequest request) {
        if (request == null || request.getQueueTabCode() == null || request.getQueueTabCode().isBlank()) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "queueTabCode 不能为空");
        }
        if (!OutpatientRefundQueueTab.ALL.equals(request.getQueueTabCode())
            && !OutpatientRefundQueueTab.PENDING.equals(request.getQueueTabCode())
            && !OutpatientRefundQueueTab.SETTLED.equals(request.getQueueTabCode())) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "queueTabCode 非法");
        }
    }

    /**
     * 校验执行退费请求。
     *
     * @param request 执行请求
     */
    private void validateExecuteRefundRequest(ExecuteOutpatientBillRefundByPaymentRequest request) {
        if (request == null || request.getEncounterId() == null || request.getOriginalPaymentId() == null) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "退费参数不完整");
        }
        GatewayIdParseUtil.requireNonEmptyLongIds(request.getRefundingChargeItemIds(), "拟退收费明细不能为空");
        // 渠道实退由服务端按原支付明细原路返回装配；前端可不传 refundPaymentDetails。
    }

    /**
     * 校验就诊主键非空。
     *
     * @param encounterId 就诊主键
     */
    private void validateEncounterId(Long encounterId) {
        if (encounterId == null) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "encounterId 不能为空");
        }
    }

    /**
     * 校验收费项全部命中。
     *
     * @param requestedIds 请求 id
     * @param loadedRows 已加载行
     * @param businessCode 业务码
     */
    private void assertChargeItemsLoaded(List<Long> requestedIds, List<ChargeItem> loadedRows, String businessCode) {
        if (loadedRows == null || loadedRows.isEmpty()) {
            throw new BusinessException(
                ErrorCodeConstants.CHARGE_ITEM_NOT_FOUND,
                this.buildMessage(businessCode, "收费明细不存在"));
        }
        LinkedHashSet<Long> loadedIds = new LinkedHashSet<>();
        for (ChargeItem row : loadedRows) {
            loadedIds.add(row.getId());
        }
        for (Long requestedId : requestedIds) {
            if (!loadedIds.contains(requestedId)) {
                throw new BusinessException(
                    ErrorCodeConstants.CHARGE_ITEM_NOT_FOUND,
                    this.buildMessage(businessCode, "部分收费明细不存在"));
            }
        }
    }

    /**
     * 校验收费项归属就诊。
     *
     * @param rows 收费行
     * @param encounterId 就诊主键
     * @param businessCode 业务码
     */
    private void assertChargeItemsBelongEncounter(List<ChargeItem> rows, Long encounterId, String businessCode) {
        for (ChargeItem row : rows) {
            if (!encounterId.equals(row.getEncounterId())) {
                throw new BusinessException(
                    ErrorCodeConstants.CHARGE_ITEM_RULE_CONFLICT,
                    this.buildMessage(businessCode, "收费项不属于当前就诊"));
            }
        }
    }

    /**
     * 组装带业务码前缀的消息。
     *
     * @param businessCode 业务码
     * @param message 文案
     * @return 组合消息
     */
    private String buildMessage(String businessCode, String message) {
        return businessCode + ": " + message;
    }
}
